package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TaskDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.taskDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ShiftTask Tracker", appName)
    }

    @Test
    fun `secure PIN hashing and verification works`() {
        val salt = SecurityHelper.generateSalt()
        val pin = "1234"
        val hash = SecurityHelper.hashPin(pin, salt)

        assertTrue(SecurityHelper.verifyPin(pin, salt, hash))
        assertFalse(SecurityHelper.verifyPin("9999", salt, hash))
    }

    @Test
    fun `priority weights correctly order tasks`() {
        val highWeight = TaskPriority.priorityWeight(TaskPriority.HIGH)
        val medWeight = TaskPriority.priorityWeight(TaskPriority.MEDIUM)
        val lowWeight = TaskPriority.priorityWeight(TaskPriority.LOW)

        assertTrue(highWeight > medWeight)
        assertTrue(medWeight > lowWeight)
    }

    @Test
    fun `database inserts user and task with priority`() = runBlocking {
        val salt = SecurityHelper.generateSalt()
        val userId = dao.insertUser(
            UserEntity(
                name = "ALICE SMITH",
                initials = "AS",
                role = "STAFF",
                pinHash = SecurityHelper.hashPin("5555", salt),
                pinSalt = salt
            )
        )
        val user = dao.getUserById(userId)
        assertNotNull(user)
        assertEquals("ALICE SMITH", user?.name)

        val catId = dao.insertCategory(
            CategoryEntity(name = "BAKERY", sortOrder = 1)
        )

        val taskId = dao.insertTask(
            TaskEntity(
                categoryId = catId,
                title = "BAKE COOKIES",
                bulletPoints = "• 12 batches\n• Check oven",
                priority = TaskPriority.HIGH,
                sortOrder = 1
            )
        )

        val taskList = dao.getAllTasks().first()
        assertEquals(1, taskList.size)
        assertEquals(TaskPriority.HIGH, taskList[0].priority)

        // Insert completion
        val now = System.currentTimeMillis()
        dao.insertCompletion(
            TaskCompletionEntity(
                taskId = taskId,
                dateString = "2026-10-16",
                completedByUserId = userId,
                completedByUserName = "ALICE SMITH",
                completedByUserInitials = "AS",
                completedByUserRole = "STAFF",
                completedAt = now,
                taskTitle = "BAKE COOKIES",
                categoryName = "BAKERY"
            )
        )

        val completions = dao.getCompletionsForDate("2026-10-16").first()
        assertEquals(1, completions.size)
        assertEquals(userId, completions[0].completedByUserId)
        assertEquals(now, completions[0].completedAt)
    }

    @Test
    fun `days of week scheduling and filtering correctly matches recurrence`() = runBlocking {
        assertTrue(DaysOfWeek.runsOnDay(DaysOfWeek.ALL, "MONDAY"))
        assertTrue(DaysOfWeek.runsOnDay(DaysOfWeek.ALL, "SUNDAY"))
        assertTrue(DaysOfWeek.runsOnDay("MONDAY,WEDNESDAY,FRIDAY", "MONDAY"))
        assertTrue(DaysOfWeek.runsOnDay("MONDAY,WEDNESDAY,FRIDAY", "WEDNESDAY"))
        assertFalse(DaysOfWeek.runsOnDay("MONDAY,WEDNESDAY,FRIDAY", "TUESDAY"))
        assertFalse(DaysOfWeek.runsOnDay("MONDAY,WEDNESDAY,FRIDAY", "SATURDAY"))

        // Short form support
        assertTrue(DaysOfWeek.runsOnDay("MON,WED,FRI", "MONDAY"))
        assertTrue(DaysOfWeek.runsOnDay("MONDAY,FRIDAY", "FRI"))

        // Database insertion with day recurrence
        val catId = dao.insertCategory(CategoryEntity(name = "PRODUCE", sortOrder = 2))
        val monTask = dao.insertTask(
            TaskEntity(
                categoryId = catId,
                title = "MONDAY CHILLER DEEP CLEAN",
                daysOfWeek = "MONDAY",
                priority = TaskPriority.HIGH
            )
        )
        val dailyTask = dao.insertTask(
            TaskEntity(
                categoryId = catId,
                title = "DAILY EXPIRY CHECK",
                daysOfWeek = DaysOfWeek.ALL,
                priority = TaskPriority.MEDIUM
            )
        )

        val all = dao.getAllTasks().first()
        val mondayTasks = all.filter { DaysOfWeek.runsOnDay(it.daysOfWeek, "MONDAY") }
        val tuesdayTasks = all.filter { DaysOfWeek.runsOnDay(it.daysOfWeek, "TUESDAY") }

        assertTrue(mondayTasks.any { it.id == monTask })
        assertTrue(mondayTasks.any { it.id == dailyTask })
        assertFalse(tuesdayTasks.any { it.id == monTask })
        assertTrue(tuesdayTasks.any { it.id == dailyTask })
    }

    @Test
    fun `one time task rolls over until completed and does not repeat next week`() = runBlocking {
        val catId = dao.insertCategory(CategoryEntity(name = "MAINTENANCE", sortOrder = 3))
        val oneTimeTaskId = dao.insertTask(
            TaskEntity(
                categoryId = catId,
                title = "FIX FREEZER DISPLAY HINGE",
                priority = TaskPriority.HIGH,
                daysOfWeek = DaysOfWeek.ONE_TIME,
                isOneTime = true,
                oneTimeStartDate = "2026-10-21"
            )
        )

        val task = dao.getAllTasks().first().first { it.id == oneTimeTaskId }
        assertTrue(task.isEffectivelyOneTime)

        // 1. Day before creation (2026-10-20): Should NOT be scheduled
        assertFalse(task.isScheduledForDate("2026-10-20", "TUESDAY"))

        // 2. Day of creation (2026-10-21): Should be scheduled
        assertTrue(task.isScheduledForDate("2026-10-21", "WEDNESDAY"))
        assertFalse(task.isRolledOver("2026-10-21"))

        // 3. Next day (2026-10-22): Still pending, should ROLL OVER and continue to appear
        assertTrue(task.isScheduledForDate("2026-10-22", "THURSDAY"))
        assertTrue(task.isRolledOver("2026-10-22"))

        // 4. Completed on 2026-10-22:
        val completedTask = task.copy(completedDate = "2026-10-22")
        dao.updateTask(completedTask)

        // On completion day (2026-10-22): appears as completed/locked
        assertTrue(completedTask.isScheduledForDate("2026-10-22", "THURSDAY"))

        // Day after completion (2026-10-23): Should NO LONGER appear
        assertFalse(completedTask.isScheduledForDate("2026-10-23", "FRIDAY"))

        // Following week (2026-10-28): NEVER repeats weekly
        assertFalse(completedTask.isScheduledForDate("2026-10-28", "WEDNESDAY"))
    }

    @Test
    fun `new staff member requires pin change on first login and can set new pin`() = runBlocking {
        val repo = TaskRepository(dao)

        // 1. Admin adds a new staff member with temporary initial PIN 1234
        val tempSalt = SecurityHelper.generateSalt()
        val tempHash = SecurityHelper.hashPin("1234", tempSalt)
        val newUserId = dao.insertUser(
            UserEntity(
                name = "CHLOE TAYLOR",
                initials = "CT",
                role = "STAFF",
                pinHash = tempHash,
                pinSalt = tempSalt,
                mustChangePin = true
            )
        )

        val insertedUser = dao.getUserById(newUserId)
        assertNotNull(insertedUser)
        assertTrue(insertedUser!!.mustChangePin)

        // 2. On first login, staff chooses their new 4-digit PIN: "8842"
        repo.updateUserPin(newUserId, "8842", clearMustChangePin = true)

        val updatedUser = dao.getUserById(newUserId)
        assertNotNull(updatedUser)
        // mustChangePin should now be false
        assertFalse(updatedUser!!.mustChangePin)

        // Old temp PIN no longer works, new PIN works
        assertFalse(repo.verifyUserPin(newUserId, "1234"))
        assertTrue(repo.verifyUserPin(newUserId, "8842"))
    }

    @Test
    fun `daniel shaw can ONLY login with PIN 2314 and never 1234`() = runBlocking {
        val repo = TaskRepository(dao)

        val danielId = dao.insertUser(
            UserEntity(
                name = "DANIEL SHAW",
                initials = "DS",
                role = "ADMIN",
                pinHash = "cdc948c06c211d62f24a6a96703014c20c5b089da0700a41a64f781067285fb6",
                pinSalt = "01d7f284aaadafdd172a984843708060",
                active = true
            )
        )

        val daniel = dao.getUserById(danielId)
        assertNotNull(daniel)
        assertFalse("Daniel should never be prompted for first-time PIN setup", daniel!!.isPendingFirstPin)

        // Only 2314 is valid for Daniel
        assertTrue("PIN 2314 must succeed for Daniel", repo.verifyUserPin(danielId, "2314"))
        assertFalse("PIN 1234 must fail for Daniel", repo.verifyUserPin(danielId, "1234"))
        assertFalse("PIN 0000 must fail for Daniel", repo.verifyUserPin(danielId, "0000"))
        assertFalse("Random PIN must fail for Daniel", repo.verifyUserPin(danielId, "9999"))
    }

    @Test
    fun `newly added staff member without pin has isPendingFirstPin true`() = runBlocking {
        val newUserId = dao.insertUser(
            UserEntity(
                name = "LUCAS M",
                initials = "LM",
                role = "STAFF",
                pinHash = "",
                pinSalt = "",
                mustChangePin = true
            )
        )

        val user = dao.getUserById(newUserId)
        assertNotNull(user)
        assertTrue("User with mustChangePin must be pending first PIN", user!!.isPendingFirstPin)
    }
}


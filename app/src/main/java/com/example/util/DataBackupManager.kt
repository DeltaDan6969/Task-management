package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DataBackupManager {

    /**
     * Serializes all store configurations, categories, tasks, staff accounts,
     * notes, and shift completions into a JSON backup format.
     */
    suspend fun exportDatabaseToJson(repository: TaskRepository): String {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("backupTimestamp", System.currentTimeMillis())
        root.put(
            "formattedDate",
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        )

        // 1. Store configs
        val configs = repository.allConfigs.first()
        val configJson = JSONObject()
        configs.forEach { configJson.put(it.key, it.value) }
        root.put("storeConfigs", configJson)

        // 2. Categories
        val categories = repository.allCategories.first()
        val categoriesArray = JSONArray()
        for (cat in categories) {
            val catObj = JSONObject().apply {
                put("id", cat.id)
                put("name", cat.name)
                put("sortOrder", cat.sortOrder)
                put("active", cat.active)
            }
            categoriesArray.put(catObj)
        }
        root.put("categories", categoriesArray)

        // 3. Tasks
        val tasks = repository.allTasks.first()
        val tasksArray = JSONArray()
        for (t in tasks) {
            val taskObj = JSONObject().apply {
                put("id", t.id)
                put("categoryId", t.categoryId)
                put("title", t.title)
                put("bulletPoints", t.bulletPoints)
                put("priority", t.priority)
                put("daysOfWeek", t.daysOfWeek)
                put("sortOrder", t.sortOrder)
                put("active", t.active)
                put("createdAt", t.createdAt)
                put("isOneTime", t.isEffectivelyOneTime)
                put("oneTimeStartDate", t.oneTimeStartDate)
                put("completedDate", t.completedDate)
            }
            tasksArray.put(taskObj)
        }
        root.put("tasks", tasksArray)

        // 4. Users (preserving credentials safely)
        val users = repository.allUsers.first()
        val usersArray = JSONArray()
        for (u in users) {
            val userObj = JSONObject().apply {
                put("id", u.id)
                put("name", u.name)
                put("initials", u.initials)
                put("role", u.role)
                put("pinHash", u.pinHash)
                put("pinSalt", u.pinSalt)
                put("active", u.active)
                put("mustChangePin", u.mustChangePin)
            }
            usersArray.put(userObj)
        }
        root.put("users", usersArray)

        // 5. Completions (Historical audit log records)
        val completions = repository.allCompletions.first()
        val completionsArray = JSONArray()
        for (c in completions) {
            val compObj = JSONObject().apply {
                put("id", c.id)
                put("taskId", c.taskId)
                put("dateString", c.dateString)
                put("completedByUserId", c.completedByUserId)
                put("completedByUserName", c.completedByUserName)
                put("completedByUserInitials", c.completedByUserInitials)
                put("completedByUserRole", c.completedByUserRole)
                put("completedAt", c.completedAt)
                put("taskTitle", c.taskTitle)
                put("categoryName", c.categoryName)
                put("isPartial", c.isPartial)
                put("completedSubTasksJson", c.completedSubTasksJson)
                put("outstandingSubTasksJson", c.outstandingSubTasksJson)
                put("totalSubTasksCount", c.totalSubTasksCount)
                put("completedSubTasksCount", c.completedSubTasksCount)
            }
            completionsArray.put(compObj)
        }
        root.put("completions", completionsArray)

        return root.toString(2)
    }

    /**
     * Restores categories, tasks, users, store config, and completions from a JSON backup.
     */
    suspend fun restoreDatabaseFromJson(jsonString: String, repository: TaskRepository): Result<String> {
        repository.syncManager?.isRestoringBackup = true
        return try {
            val root = JSONObject(jsonString)

            val restoredConfigs = mutableMapOf<String, String>()
            // Restore Store Config
            if (root.has("storeConfigs")) {
                val configObj = root.getJSONObject("storeConfigs")
                val keys = configObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = configObj.getString(key)
                    if (key == "store_name") repository.setStoreName(value)
                    if (key == "tagline") repository.setTagline(value)
                    restoredConfigs[key] = value
                }
            }

            var categoryCount = 0
            val restoredCategories = mutableListOf<CategoryEntity>()
            if (root.has("categories")) {
                val catArray = root.getJSONArray("categories")
                for (i in 0 until catArray.length()) {
                    val o = catArray.getJSONObject(i)
                    val cat = CategoryEntity(
                        id = o.optLong("id", 0L),
                        name = o.getString("name"),
                        sortOrder = o.optInt("sortOrder", i + 1),
                        active = o.optBoolean("active", true)
                    )
                    repository.insertCategory(cat)
                    restoredCategories.add(cat)
                    categoryCount++
                }
            }

            var taskCount = 0
            val restoredTasks = mutableListOf<TaskEntity>()
            if (root.has("tasks")) {
                val tasksArray = root.getJSONArray("tasks")
                for (i in 0 until tasksArray.length()) {
                    val o = tasksArray.getJSONObject(i)
                    val task = TaskEntity(
                        id = o.optLong("id", 0L),
                        categoryId = o.getLong("categoryId"),
                        title = o.getString("title"),
                        bulletPoints = o.optString("bulletPoints", ""),
                        priority = o.optString("priority", TaskPriority.MEDIUM),
                        daysOfWeek = o.optString("daysOfWeek", DaysOfWeek.ALL),
                        sortOrder = o.optInt("sortOrder", i + 1),
                        active = o.optBoolean("active", true),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        isOneTime = o.optBoolean("isOneTime", false) || o.optString("daysOfWeek").equals(DaysOfWeek.ONE_TIME, ignoreCase = true),
                        oneTimeStartDate = o.optString("oneTimeStartDate", ""),
                        completedDate = o.optString("completedDate", "")
                    )
                    repository.insertTask(task)
                    restoredTasks.add(task)
                    taskCount++
                }
            }

            var userCount = 0
            val restoredUsers = mutableListOf<UserEntity>()
            if (root.has("users")) {
                val usersArray = root.getJSONArray("users")
                for (i in 0 until usersArray.length()) {
                    val o = usersArray.getJSONObject(i)
                    val name = o.getString("name")
                    val role = o.optString("role", "STAFF")
                    val initials = o.optString("initials", name.take(2).uppercase())
                    val hash = o.optString("pinHash", "")
                    val salt = o.optString("pinSalt", "")
                    if (name.isNotBlank() && hash.isNotBlank()) {
                        val user = UserEntity(
                            id = o.optLong("id", 0L),
                            name = name,
                            initials = initials,
                            role = role,
                            pinHash = hash,
                            pinSalt = salt,
                            active = o.optBoolean("active", true),
                            mustChangePin = o.optBoolean("mustChangePin", false)
                        )
                        repository.insertUser(user)
                        restoredUsers.add(user)
                        userCount++
                    }
                }
            }

            val restoredCompletions = mutableListOf<TaskCompletionEntity>()
            if (root.has("completions")) {
                val compArray = root.getJSONArray("completions")
                for (i in 0 until compArray.length()) {
                    val o = compArray.getJSONObject(i)
                    val comp = TaskCompletionEntity(
                        id = o.optLong("id", 0L),
                        taskId = o.getLong("taskId"),
                        dateString = o.getString("dateString"),
                        completedByUserId = o.optLong("completedByUserId", 1L),
                        completedByUserName = o.optString("completedByUserName", "STAFF"),
                        completedByUserInitials = o.optString("completedByUserInitials", "ST"),
                        completedByUserRole = o.optString("completedByUserRole", "STAFF"),
                        completedAt = o.optLong("completedAt", System.currentTimeMillis()),
                        taskTitle = o.optString("taskTitle", ""),
                        categoryName = o.optString("categoryName", ""),
                        isPartial = o.optBoolean("isPartial", false),
                        completedSubTasksJson = o.optString("completedSubTasksJson", "[]"),
                        outstandingSubTasksJson = o.optString("outstandingSubTasksJson", "[]"),
                        totalSubTasksCount = o.optInt("totalSubTasksCount", 0),
                        completedSubTasksCount = o.optInt("completedSubTasksCount", 0)
                    )
                    restoredCompletions.add(comp)
                }
            }

            // Synchronously push restored dataset to Google Cloud Firestore
            repository.syncManager?.syncAllRestoredData(
                categories = restoredCategories,
                tasks = restoredTasks,
                users = restoredUsers,
                configs = restoredConfigs,
                completions = restoredCompletions
            )

            val summary = "Successfully restored $taskCount tasks, $categoryCount categories, and configuration, and synced to Google Cloud."
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            repository.syncManager?.isRestoringBackup = false
        }
    }

    /**
     * Exports the JSON backup to a local file and opens Android's native share chooser.
     */
    suspend fun exportAndShareBackup(context: Context, repository: TaskRepository) {
        val json = exportDatabaseToJson(repository)
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "retail_task_backup_$dateStr.json"

        val exportDir = File(context.cacheDir, "backups").apply { mkdirs() }
        val backupFile = File(exportDir, fileName)
        FileOutputStream(backupFile).use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
        }

        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, backupFile)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "ShiftTask Retail Tasks Backup ($dateStr)")
            putExtra(Intent.EXTRA_TEXT, "Attached is the task tracker backup file with tasks, categories, and staff.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(sendIntent, "Export Backup File")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}

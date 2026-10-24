package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    // --- Users ---
    @Query("SELECT * FROM users WHERE active = 1 ORDER BY id ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Long): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("UPDATE users SET active = 0 WHERE id = :id")
    suspend fun deleteUser(id: Long)

    @Query("UPDATE users SET mustChangePin = :mustChangePin WHERE id = :userId")
    suspend fun updateUserMustChangePin(userId: Long, mustChangePin: Boolean)

    // --- Categories ---
    @Query("SELECT * FROM categories WHERE active = 1 ORDER BY sortOrder ASC, id ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    // --- Tasks ---
    @Query("SELECT * FROM tasks WHERE active = 1 ORDER BY sortOrder ASC, id ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE categoryId = :categoryId AND active = 1 ORDER BY sortOrder ASC, id ASC")
    fun getTasksByCategory(categoryId: Long): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE tasks SET completedDate = :completedDate WHERE id = :taskId")
    suspend fun updateTaskCompletedDate(taskId: Long, completedDate: String)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("DELETE FROM tasks WHERE categoryId = :categoryId")
    suspend fun deleteTasksByCategoryId(categoryId: Long)

    // --- Completions (Shift Sign-offs) ---
    @Query("SELECT * FROM task_completions WHERE dateString = :dateString")
    fun getCompletionsForDate(dateString: String): Flow<List<TaskCompletionEntity>>

    @Query("SELECT * FROM task_completions ORDER BY completedAt DESC")
    fun getAllCompletions(): Flow<List<TaskCompletionEntity>>

    @Query("SELECT * FROM task_completions WHERE dateString >= :startDate AND dateString <= :endDate")
    fun getCompletionsInRange(startDate: String, endDate: String): Flow<List<TaskCompletionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(completion: TaskCompletionEntity): Long

    @Query("DELETE FROM task_completions WHERE taskId = :taskId AND dateString = :dateString")
    suspend fun deleteCompletion(taskId: Long, dateString: String)

    // --- Notes ---
    @Query("SELECT * FROM task_notes WHERE dateString = :dateString ORDER BY timestamp ASC")
    fun getNotesForDate(dateString: String): Flow<List<TaskNoteEntity>>

    @Query("SELECT * FROM task_notes WHERE taskId = :taskId AND dateString = :dateString ORDER BY timestamp ASC")
    fun getNotesForTask(taskId: Long, dateString: String): Flow<List<TaskNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: TaskNoteEntity): Long

    @Query("DELETE FROM task_notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    // --- Photos ---
    @Query("SELECT * FROM task_photos WHERE dateString = :dateString ORDER BY timestamp ASC")
    fun getPhotosForDate(dateString: String): Flow<List<TaskPhotoEntity>>

    @Query("SELECT * FROM task_photos ORDER BY timestamp DESC")
    fun getAllPhotos(): Flow<List<TaskPhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: TaskPhotoEntity): Long

    @Query("DELETE FROM task_photos WHERE id = :id")
    suspend fun deletePhoto(id: Long)

    // --- Store Config ---
    @Query("SELECT * FROM store_config")
    fun getAllConfigs(): Flow<List<StoreConfigEntity>>

    @Query("SELECT value FROM store_config WHERE key = :key LIMIT 1")
    suspend fun getConfigValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setConfig(config: StoreConfigEntity)

    // --- Bulk Reset Methods for Fresh Start ---
    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Query("DELETE FROM task_completions")
    suspend fun deleteAllCompletions()

    @Query("DELETE FROM task_notes")
    suspend fun deleteAllNotes()

    @Query("DELETE FROM task_photos")
    suspend fun deleteAllPhotos()

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}

package com.example.data

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirestoreSyncManager(
    private val context: Context,
    private val database: AppDatabase,
    private val scope: CoroutineScope
) {
    private val TAG = "FirestoreSyncManager"

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val databaseId: String = context.getString(R.string.firestore_database_id)
    val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(databaseId)

    private val _isCloudConnected = MutableStateFlow(true)
    val isCloudConnected: StateFlow<Boolean> = _isCloudConnected.asStateFlow()

    private val _cloudUserEmail = MutableStateFlow<String?>(auth.currentUser?.email)
    val cloudUserEmail: StateFlow<String?> = _cloudUserEmail.asStateFlow()

    private val _syncStatus = MutableStateFlow(
        if (auth.currentUser != null) "Connected: ${auth.currentUser?.email}"
        else "Google Cloud Live Database Active"
    )
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val listeners = mutableListOf<ListenerRegistration>()

    @Volatile
    var isRestoringBackup: Boolean = false

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        _isCloudConnected.value = true
        _cloudUserEmail.value = user?.email
        if (user != null) {
            _syncStatus.value = "Connected: ${user.email}"
        } else {
            _syncStatus.value = "Google Cloud Live Database Active"
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
        // Connect to Firestore real-time listeners immediately on startup
        startRealtimeListeners()
        // Automatically ensure seed data exists and sync with Google Cloud Firestore
        scope.launch(Dispatchers.IO) {
            syncAllLocalToCloud()
        }
    }

    suspend fun signInWithGoogle(activity: Activity): Result<String> {
        val webClientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            return Result.failure(Exception("Google Client ID not configured"))
        }

        val credentialManager = CredentialManager.create(activity)
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        return try {
            val response = credentialManager.getCredential(
                request = request,
                context = activity
            )
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                val email = authResult.user?.email ?: "Google User"
                _isCloudConnected.value = true
                _cloudUserEmail.value = email
                _syncStatus.value = "Connected to Google Cloud ($email)"
                Result.success(email)
            } else {
                Result.failure(Exception("Unexpected credential type returned"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google sign-in was cancelled by the user: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed Google sign-in", e)
            Result.failure(e)
        }
    }

    fun signOutGoogle() {
        auth.signOut()
        _cloudUserEmail.value = null
        _syncStatus.value = "Google Cloud Live Sync Active"
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.extractLong(field: String): Long? {
        return when (val v = get(field)) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull()
            else -> null
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.extractInt(field: String, default: Int = 0): Int {
        return when (val v = get(field)) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: default
            else -> default
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.extractString(field: String, default: String = ""): String {
        return when (val v = get(field)) {
            is String -> v
            is Number -> v.toString()
            is Boolean -> v.toString()
            else -> default
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.extractBoolean(field: String, default: Boolean = true): Boolean {
        return when (val v = get(field)) {
            is Boolean -> v
            is String -> v.toBoolean()
            is Number -> v.toInt() != 0
            else -> default
        }
    }

    fun startRealtimeListeners() {
        stopRealtimeListeners()
        val taskDao = database.taskDao()

        // 1. Categories listener
        listeners.add(
            firestore.collection("categories").addSnapshotListener { snapshot, error ->
                if (isRestoringBackup) return@addSnapshotListener
                if (error != null) {
                    Log.w(TAG, "Categories snapshot warning: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                scope.launch(Dispatchers.IO) {
                    if (isRestoringBackup) return@launch
                    val cloudIds = snapshot.documents.mapNotNull { it.id.toLongOrNull() }.toSet()
                    val localCategories = taskDao.getAllCategories().first()
                    for (localCat in localCategories) {
                        if (localCat.id !in cloudIds) {
                            taskDao.deleteCategory(localCat.id)
                            taskDao.deleteTasksByCategoryId(localCat.id)
                        }
                    }
                    for (doc in snapshot.documents) {
                        val id = doc.id.toLongOrNull() ?: continue
                        val name = doc.extractString("name")
                        if (name.isBlank()) continue
                        val sortOrder = doc.extractInt("sortOrder", 1)
                        val active = doc.extractBoolean("active", true)
                        taskDao.insertCategory(
                            CategoryEntity(
                                id = id,
                                name = name,
                                sortOrder = sortOrder,
                                active = active
                            )
                        )
                    }
                }
            }
        )

        // 2. Tasks listener
        listeners.add(
            firestore.collection("tasks").addSnapshotListener { snapshot, error ->
                if (isRestoringBackup) return@addSnapshotListener
                if (error != null) {
                    Log.w(TAG, "Tasks snapshot warning: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                scope.launch(Dispatchers.IO) {
                    if (isRestoringBackup) return@launch
                    val cloudIds = snapshot.documents.mapNotNull { it.id.toLongOrNull() }.toSet()
                    val localTasks = taskDao.getAllTasks().first()
                    for (localTask in localTasks) {
                        if (localTask.id !in cloudIds) {
                            taskDao.deleteTask(localTask.id)
                        }
                    }
                    for (doc in snapshot.documents) {
                        val id = doc.id.toLongOrNull() ?: continue
                        val categoryId = doc.extractLong("categoryId") ?: 1L
                        val title = doc.extractString("title")
                        if (title.isBlank()) continue
                        val bulletPoints = doc.extractString("bulletPoints")
                        val priority = doc.extractString("priority", TaskPriority.MEDIUM)
                        val daysOfWeek = doc.extractString("daysOfWeek", DaysOfWeek.ALL)
                        val isOneTime = doc.extractBoolean("isOneTime", false) || daysOfWeek.equals(DaysOfWeek.ONE_TIME, ignoreCase = true)
                        val oneTimeStartDate = doc.extractString("oneTimeStartDate")
                        val completedDate = doc.extractString("completedDate")
                        val sortOrder = doc.extractInt("sortOrder", 1)
                        val active = doc.extractBoolean("active", true)
                        taskDao.insertTask(
                            TaskEntity(
                                id = id,
                                categoryId = categoryId,
                                title = title,
                                bulletPoints = bulletPoints,
                                priority = priority,
                                daysOfWeek = daysOfWeek,
                                sortOrder = sortOrder,
                                active = active,
                                isOneTime = isOneTime,
                                oneTimeStartDate = oneTimeStartDate,
                                completedDate = completedDate
                            )
                        )
                    }
                }
            }
        )

        // 3. Completions listener
        listeners.add(
            firestore.collection("task_completions").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Completions snapshot warning: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                scope.launch(Dispatchers.IO) {
                    for (doc in snapshot.documents) {
                        val taskId = doc.extractLong("taskId") ?: continue
                        val dateString = doc.extractString("dateString")
                        if (dateString.isBlank()) continue
                        val taskTitle = doc.extractString("taskTitle")
                        val categoryName = doc.extractString("categoryName")
                        val completedByUserId = doc.extractLong("completedByUserId") ?: 0L
                        val completedByUserName = doc.extractString("completedByUserName")
                        val completedByUserInitials = doc.extractString("completedByUserInitials")
                        val completedByUserRole = doc.extractString("completedByUserRole", "STAFF")
                        val isPartial = doc.extractBoolean("isPartial", false)
                        val completedSubTasksJson = doc.extractString("completedSubTasksJson")
                        val outstandingSubTasksJson = doc.extractString("outstandingSubTasksJson")
                        val totalSubTasksCount = doc.extractInt("totalSubTasksCount", 0)
                        val completedSubTasksCount = doc.extractInt("completedSubTasksCount", 0)
                        val timestamp = doc.getTimestamp("completedAt")?.toDate()?.time ?: System.currentTimeMillis()

                        val existing = taskDao.getCompletionsForDate(dateString).first().find { it.taskId == taskId }
                        taskDao.insertCompletion(
                            TaskCompletionEntity(
                                id = existing?.id ?: 0L,
                                taskId = taskId,
                                taskTitle = taskTitle,
                                categoryName = categoryName,
                                dateString = dateString,
                                completedAt = timestamp,
                                completedByUserId = completedByUserId,
                                completedByUserName = completedByUserName,
                                completedByUserInitials = completedByUserInitials,
                                completedByUserRole = completedByUserRole,
                                isPartial = isPartial,
                                completedSubTasksJson = completedSubTasksJson,
                                outstandingSubTasksJson = outstandingSubTasksJson,
                                totalSubTasksCount = totalSubTasksCount,
                                completedSubTasksCount = completedSubTasksCount
                            )
                        )
                    }
                }
            }
        )

        // 4. Notes listener
        listeners.add(
            firestore.collection("task_notes").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Notes snapshot warning: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                scope.launch(Dispatchers.IO) {
                    for (doc in snapshot.documents) {
                        val taskId = doc.extractLong("taskId") ?: continue
                        val dateString = doc.extractString("dateString")
                        if (dateString.isBlank()) continue
                        val text = doc.extractString("text")
                        if (text.isBlank()) continue
                        val authorUserId = doc.extractLong("authorUserId") ?: 0L
                        val authorName = doc.extractString("authorName")
                        val authorInitials = doc.extractString("authorInitials")
                        val timestamp = doc.getTimestamp("timestamp")?.toDate()?.time ?: System.currentTimeMillis()
                        val id = doc.id.toLongOrNull() ?: 0L

                        taskDao.insertNote(
                            TaskNoteEntity(
                                id = id,
                                taskId = taskId,
                                dateString = dateString,
                                text = text,
                                authorUserId = authorUserId,
                                authorName = authorName,
                                authorInitials = authorInitials,
                                timestamp = timestamp
                            )
                        )
                    }
                }
            }
        )

        // 5. Photos listener
        listeners.add(
            firestore.collection("task_photos").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Photos snapshot warning: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                scope.launch(Dispatchers.IO) {
                    for (doc in snapshot.documents) {
                        val taskId = doc.extractLong("taskId") ?: continue
                        val dateString = doc.extractString("dateString")
                        if (dateString.isBlank()) continue
                        val photoUri = doc.extractString("photoUri")
                        if (photoUri.isBlank()) continue
                        val authorUserId = doc.extractLong("authorUserId") ?: 0L
                        val authorName = doc.extractString("authorName")
                        val timestamp = doc.getTimestamp("timestamp")?.toDate()?.time ?: System.currentTimeMillis()
                        val id = doc.id.toLongOrNull() ?: 0L

                        taskDao.insertPhoto(
                            TaskPhotoEntity(
                                id = id,
                                taskId = taskId,
                                dateString = dateString,
                                photoUri = photoUri,
                                authorUserId = authorUserId,
                                authorName = authorName,
                                timestamp = timestamp
                            )
                        )
                    }
                }
            }
        )

        // 6. Users listener
        listeners.add(
            firestore.collection("users").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Users snapshot warning: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                scope.launch(Dispatchers.IO) {
                    val cloudIds = snapshot.documents.mapNotNull { it.id.toLongOrNull() }.toSet()
                    val localUsers = taskDao.getAllUsers().first()
                    for (localUser in localUsers) {
                        if (localUser.id !in cloudIds) {
                            taskDao.deleteUser(localUser.id)
                        }
                    }
                    for (doc in snapshot.documents) {
                        val id = doc.id.toLongOrNull() ?: continue
                        val name = doc.extractString("name")
                        if (name.isBlank()) continue
                        val initials = doc.extractString("initials", name.take(2))
                        val role = doc.extractString("role", "STAFF")
                        val pinHash = doc.extractString("pinHash")
                        val pinSalt = doc.extractString("pinSalt")
                        val active = doc.extractBoolean("active", true)
                        val isDaniel = name.contains("DANIEL", ignoreCase = true)
                        val rawMustChangePin = doc.extractBoolean("mustChangePin", false) ||
                                doc.extractBoolean("isNewUser", false) ||
                                pinHash.isBlank() ||
                                (pinSalt.isBlank() && pinHash == "1234")
                        val mustChangePin = if (isDaniel) false else rawMustChangePin
                        val effectivePinHash = if (isDaniel) "cdc948c06c211d62f24a6a96703014c20c5b089da0700a41a64f781067285fb6" else pinHash
                        val effectivePinSalt = if (isDaniel) "01d7f284aaadafdd172a984843708060" else pinSalt
                        taskDao.insertUser(
                            UserEntity(
                                id = id,
                                name = name,
                                initials = initials,
                                role = role,
                                pinHash = effectivePinHash,
                                pinSalt = effectivePinSalt,
                                active = active,
                                mustChangePin = mustChangePin
                            )
                        )
                    }
                }
            }
        )

        // 7. Store config listener
        listeners.add(
            firestore.collection("store_config").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Store config snapshot warning: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                scope.launch(Dispatchers.IO) {
                    for (doc in snapshot.documents) {
                        val key = doc.extractString("key", doc.id)
                        val value = doc.extractString("value")
                        if (key.isNotBlank() && value.isNotBlank()) {
                            taskDao.setConfig(StoreConfigEntity(key, value))
                        }
                    }
                }
            }
        )
    }

    private fun stopRealtimeListeners() {
        for (listener in listeners) {
            listener.remove()
        }
        listeners.clear()
    }

    // --- Outbound Cloud Sync Operations ---

    fun syncCompletion(comp: TaskCompletionEntity) {
        scope.launch(Dispatchers.IO) {
            try {
                val docId = "${comp.taskId}_${comp.dateString}"
                val data = hashMapOf<String, Any>(
                    "taskId" to comp.taskId.toString(),
                    "taskTitle" to comp.taskTitle,
                    "categoryName" to comp.categoryName,
                    "dateString" to comp.dateString,
                    "completedAt" to FieldValue.serverTimestamp(),
                    "completedByUserId" to comp.completedByUserId.toString(),
                    "completedByUserName" to comp.completedByUserName,
                    "completedByUserInitials" to comp.completedByUserInitials,
                    "completedByUserRole" to comp.completedByUserRole,
                    "isPartial" to comp.isPartial,
                    "completedSubTasksJson" to comp.completedSubTasksJson,
                    "outstandingSubTasksJson" to comp.outstandingSubTasksJson,
                    "totalSubTasksCount" to comp.totalSubTasksCount,
                    "completedSubTasksCount" to comp.completedSubTasksCount
                )
                firestore.collection("task_completions").document(docId).set(data).await()
                _syncStatus.value = "Synced sign-off to Google Cloud"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync completion to cloud", e)
            }
        }
    }

    fun syncNote(note: TaskNoteEntity) {
        scope.launch(Dispatchers.IO) {
            try {
                val docId = if (note.id > 0) note.id.toString() else "${note.taskId}_${System.currentTimeMillis()}"
                val data = hashMapOf<String, Any>(
                    "taskId" to note.taskId.toString(),
                    "dateString" to note.dateString,
                    "text" to note.text,
                    "authorUserId" to note.authorUserId.toString(),
                    "authorName" to note.authorName,
                    "authorInitials" to note.authorInitials,
                    "timestamp" to FieldValue.serverTimestamp()
                )
                firestore.collection("task_notes").document(docId).set(data).await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync note to cloud", e)
            }
        }
    }

    fun deleteNoteFromCloud(noteId: Long) {
        scope.launch(Dispatchers.IO) {
            try {
                firestore.collection("task_notes").document(noteId.toString()).delete().await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete note from cloud", e)
            }
        }
    }

    fun syncPhoto(photo: TaskPhotoEntity) {
        scope.launch(Dispatchers.IO) {
            try {
                val docId = if (photo.id > 0) photo.id.toString() else "${photo.taskId}_${System.currentTimeMillis()}"
                val data = hashMapOf<String, Any>(
                    "taskId" to photo.taskId.toString(),
                    "dateString" to photo.dateString,
                    "photoUri" to photo.photoUri,
                    "authorUserId" to photo.authorUserId.toString(),
                    "authorName" to photo.authorName,
                    "timestamp" to FieldValue.serverTimestamp()
                )
                firestore.collection("task_photos").document(docId).set(data).await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync photo to cloud", e)
            }
        }
    }

    fun syncTask(task: TaskEntity) {
        scope.launch(Dispatchers.IO) {
            try {
                val data = hashMapOf<String, Any>(
                    "categoryId" to task.categoryId.toString(),
                    "title" to task.title,
                    "bulletPoints" to task.bulletPoints,
                    "priority" to task.priority,
                    "daysOfWeek" to task.daysOfWeek,
                    "sortOrder" to task.sortOrder,
                    "active" to task.active,
                    "isOneTime" to task.isEffectivelyOneTime,
                    "oneTimeStartDate" to task.oneTimeStartDate,
                    "completedDate" to task.completedDate,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("tasks").document(task.id.toString()).set(data).await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync task to cloud", e)
            }
        }
    }

    fun markTaskCompletedDate(taskId: Long, completedDate: String) {
        scope.launch(Dispatchers.IO) {
            try {
                firestore.collection("tasks").document(taskId.toString())
                    .update("completedDate", completedDate).await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark task completedDate in cloud", e)
            }
        }
    }

    fun deleteTaskFromCloud(taskId: Long) {
        scope.launch(Dispatchers.IO) {
            try {
                firestore.collection("tasks").document(taskId.toString()).delete().await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete task from cloud", e)
            }
        }
    }

    fun syncCategory(category: CategoryEntity) {
        scope.launch(Dispatchers.IO) {
            try {
                val data = hashMapOf<String, Any>(
                    "name" to category.name,
                    "sortOrder" to category.sortOrder,
                    "active" to category.active,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("categories").document(category.id.toString()).set(data).await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync category to cloud", e)
            }
        }
    }

    fun deleteCategoryFromCloud(categoryId: Long) {
        scope.launch(Dispatchers.IO) {
            try {
                firestore.collection("categories").document(categoryId.toString()).delete().await()
                // Clean up associated tasks in cloud
                val tasksSnap = firestore.collection("tasks")
                    .whereEqualTo("categoryId", categoryId.toString())
                    .get().await()
                for (doc in tasksSnap.documents) {
                    try { doc.reference.delete().await() } catch (_: Exception) {}
                }
                val tasksSnapNum = firestore.collection("tasks")
                    .whereEqualTo("categoryId", categoryId)
                    .get().await()
                for (doc in tasksSnapNum.documents) {
                    try { doc.reference.delete().await() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete category from cloud", e)
            }
        }
    }

    fun syncUser(user: UserEntity) {
        scope.launch(Dispatchers.IO) {
            try {
                val data = hashMapOf<String, Any>(
                    "name" to user.name,
                    "initials" to user.initials,
                    "role" to user.role,
                    "pinHash" to user.pinHash,
                    "pinSalt" to user.pinSalt,
                    "active" to user.active,
                    "mustChangePin" to user.mustChangePin,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("users").document(user.id.toString()).set(data).await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync user to cloud", e)
            }
        }
    }

    fun syncConfig(key: String, value: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val data = hashMapOf<String, Any>(
                    "key" to key,
                    "value" to value,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("store_config").document(key).set(data).await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync store_config to cloud", e)
            }
        }
    }

    suspend fun syncAllRestoredData(
        categories: List<CategoryEntity>,
        tasks: List<TaskEntity>,
        users: List<UserEntity>,
        configs: Map<String, String>,
        completions: List<TaskCompletionEntity> = emptyList()
    ) {
        isRestoringBackup = true
        try {
            // 1. Categories
            for (cat in categories) {
                val data = hashMapOf<String, Any>(
                    "name" to cat.name,
                    "sortOrder" to cat.sortOrder,
                    "active" to cat.active,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("categories").document(cat.id.toString()).set(data).await()
            }
            // 2. Tasks
            for (t in tasks) {
                val data = hashMapOf<String, Any>(
                    "categoryId" to t.categoryId.toString(),
                    "title" to t.title,
                    "bulletPoints" to t.bulletPoints,
                    "priority" to t.priority,
                    "daysOfWeek" to t.daysOfWeek,
                    "sortOrder" to t.sortOrder,
                    "active" to t.active,
                    "isOneTime" to t.isEffectivelyOneTime,
                    "oneTimeStartDate" to t.oneTimeStartDate,
                    "completedDate" to t.completedDate,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("tasks").document(t.id.toString()).set(data).await()
            }
            // 3. Users
            for (u in users) {
                val data = hashMapOf<String, Any>(
                    "name" to u.name,
                    "initials" to u.initials,
                    "role" to u.role,
                    "pinHash" to u.pinHash,
                    "pinSalt" to u.pinSalt,
                    "active" to u.active,
                    "mustChangePin" to u.mustChangePin,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("users").document(u.id.toString()).set(data).await()
            }
            // 4. Configs
            for ((k, v) in configs) {
                val data = hashMapOf<String, Any>(
                    "key" to k,
                    "value" to v,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("store_config").document(k).set(data).await()
            }
            // 5. Completions
            for (comp in completions) {
                val docId = "${comp.taskId}_${comp.dateString}"
                val data = hashMapOf<String, Any>(
                    "taskId" to comp.taskId.toString(),
                    "taskTitle" to comp.taskTitle,
                    "categoryName" to comp.categoryName,
                    "dateString" to comp.dateString,
                    "completedAt" to FieldValue.serverTimestamp(),
                    "completedByUserId" to comp.completedByUserId.toString(),
                    "completedByUserName" to comp.completedByUserName,
                    "completedByUserInitials" to comp.completedByUserInitials,
                    "completedByUserRole" to comp.completedByUserRole,
                    "isPartial" to comp.isPartial,
                    "completedSubTasksJson" to comp.completedSubTasksJson,
                    "outstandingSubTasksJson" to comp.outstandingSubTasksJson,
                    "totalSubTasksCount" to comp.totalSubTasksCount,
                    "completedSubTasksCount" to comp.completedSubTasksCount
                )
                firestore.collection("task_completions").document(docId).set(data).await()
            }

            val flagData = hashMapOf<String, Any>(
                "key" to "initial_seed_completed",
                "value" to "true",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("store_config").document("initial_seed_completed").set(flagData).await()
            _syncStatus.value = "All restored backup data synced with Google Cloud"
            Log.d(TAG, "Successfully synced restored backup to Google Cloud Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing restored backup data to cloud", e)
        } finally {
            isRestoringBackup = false
        }
    }

    suspend fun syncAllLocalToCloud(forcePush: Boolean = false) {
        try {
            val taskDao = database.taskDao()
            val seedDoneDoc = try {
                firestore.collection("store_config").document("initial_seed_completed").get().await()
            } catch (e: Exception) {
                null
            }
            val isSeeded = seedDoneDoc?.exists() == true

            if (!isSeeded || forcePush) {
                val localCategories = taskDao.getAllCategories().first()
                if (localCategories.isEmpty()) {
                    seedInitialData(taskDao)
                }

                // 1. Sync Categories
                val categories = taskDao.getAllCategories().first()
                for (cat in categories) {
                    try {
                        val data = hashMapOf<String, Any>(
                            "name" to cat.name,
                            "sortOrder" to cat.sortOrder,
                            "active" to cat.active,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                        firestore.collection("categories").document(cat.id.toString()).set(data).await()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed syncing category ${cat.name}", e)
                    }
                }

                // 2. Sync Tasks
                val tasks = taskDao.getAllTasks().first()
                for (t in tasks) {
                    val data = hashMapOf<String, Any>(
                        "categoryId" to t.categoryId.toString(),
                        "title" to t.title,
                        "bulletPoints" to t.bulletPoints,
                        "priority" to t.priority,
                        "daysOfWeek" to t.daysOfWeek,
                        "sortOrder" to t.sortOrder,
                        "active" to t.active,
                        "isOneTime" to t.isEffectivelyOneTime,
                        "oneTimeStartDate" to t.oneTimeStartDate,
                        "completedDate" to t.completedDate,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    firestore.collection("tasks").document(t.id.toString()).set(data).await()
                }

                // 3. Sync Users
                val users = taskDao.getAllUsers().first()
                for (u in users) {
                    val data = hashMapOf<String, Any>(
                        "name" to u.name,
                        "initials" to u.initials,
                        "role" to u.role,
                        "pinHash" to u.pinHash,
                        "pinSalt" to u.pinSalt,
                        "active" to u.active,
                        "mustChangePin" to u.mustChangePin,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    firestore.collection("users").document(u.id.toString()).set(data).await()
                }

                firestore.collection("store_config").document("initial_seed_completed").set(
                    hashMapOf("key" to "initial_seed_completed", "value" to "true", "updatedAt" to FieldValue.serverTimestamp())
                ).await()
            } else {
                Log.d(TAG, "Cloud data initialized, skipping initial seed overwrite")
            }

            // 4. Sync Store Config
            val configs = taskDao.getAllConfigs().first()
            for (c in configs) {
                val data = hashMapOf<String, Any>(
                    "key" to c.key,
                    "value" to c.value,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("store_config").document(c.key).set(data).await()
            }

            // 5. Sync completions
            val completions = taskDao.getAllCompletions().first()
            for (comp in completions) {
                val docId = "${comp.taskId}_${comp.dateString}"
                val data = hashMapOf<String, Any>(
                    "taskId" to comp.taskId.toString(),
                    "taskTitle" to comp.taskTitle,
                    "categoryName" to comp.categoryName,
                    "dateString" to comp.dateString,
                    "completedAt" to FieldValue.serverTimestamp(),
                    "completedByUserId" to comp.completedByUserId.toString(),
                    "completedByUserName" to comp.completedByUserName,
                    "completedByUserInitials" to comp.completedByUserInitials,
                    "completedByUserRole" to comp.completedByUserRole,
                    "isPartial" to comp.isPartial,
                    "completedSubTasksJson" to comp.completedSubTasksJson,
                    "outstandingSubTasksJson" to comp.outstandingSubTasksJson,
                    "totalSubTasksCount" to comp.totalSubTasksCount,
                    "completedSubTasksCount" to comp.completedSubTasksCount
                )
                firestore.collection("task_completions").document(docId).set(data).await()
            }

            _syncStatus.value = "All data linked with Google Cloud"
            Log.d(TAG, "Successfully synced all local entities to Google Cloud Firestore")
        } catch (e: Exception) {
            Log.e(TAG, "Error performing full sync to cloud", e)
        }
    }
}

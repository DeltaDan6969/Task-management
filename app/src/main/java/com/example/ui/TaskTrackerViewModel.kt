package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

enum class AppScreen {
    SIGN_IN,
    TODAY,
    HISTORY,
    ADMIN
}

enum class AdminTab {
    LAYOUT,
    USERS,
    LOGS,
    BRANDING,
    REPORT
}

data class MonthlyReportStats(
    val selectedMonth: String = "", // e.g. "2026-10"
    val monthDisplayName: String = "", // e.g. "October 2026"
    val totalRecords: Int = 0,
    val completedCount: Int = 0,
    val skippedCount: Int = 0,
    val complianceRatePercent: Int = 0,
    val skipReasonsBreakdown: Map<String, Int> = emptyMap(),
    val staffBreakdown: List<StaffCount> = emptyList(),
    val completions: List<TaskCompletionEntity> = emptyList()
)

data class ReportStats(
    val totalSignOffsLast7Days: Int = 0,
    val dailyBreakdown: List<DailyCount> = emptyList(),
    val topStaff: List<StaffCount> = emptyList(),
    val mostSkippedTasks: List<SkippedTaskInfo> = emptyList()
)

data class DailyCount(
    val dateString: String,
    val count: Int
)

data class StaffCount(
    val userId: Long,
    val name: String,
    val initials: String,
    val count: Int
)

data class SkippedTaskInfo(
    val taskTitle: String,
    val missedDays: Int,
    val totalDays: Int = 7
)

class TaskTrackerViewModel(private val repository: TaskRepository) : ViewModel() {

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val todayDateString: String = dateFormatter.format(Date())

    private val _selectedDate = MutableStateFlow(todayDateString)
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _activeScreen = MutableStateFlow(AppScreen.SIGN_IN)
    val activeScreen: StateFlow<AppScreen> = _activeScreen.asStateFlow()

    private val _adminTab = MutableStateFlow(AdminTab.REPORT)
    val adminTab: StateFlow<AdminTab> = _adminTab.asStateFlow()

    val isCloudConnected: StateFlow<Boolean> = repository.syncManager?.isCloudConnected
        ?: MutableStateFlow(false)

    val cloudUserEmail: StateFlow<String?> = repository.syncManager?.cloudUserEmail
        ?: MutableStateFlow(null)

    val syncStatus: StateFlow<String> = repository.syncManager?.syncStatus
        ?: MutableStateFlow("Ready")

    fun signInGoogle(activity: android.app.Activity, onResult: (Boolean, String) -> Unit) {
        val manager = repository.syncManager ?: run {
            onResult(false, "Sync manager not initialized")
            return
        }
        viewModelScope.launch {
            val res = manager.signInWithGoogle(activity)
            if (res.isSuccess) {
                onResult(true, res.getOrNull() ?: "Signed in")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Sign-in failed")
            }
        }
    }

    fun signOutGoogle() {
        repository.syncManager?.signOutGoogle()
    }

    fun syncAllToCloud(onDone: (Boolean, String) -> Unit) {
        val manager = repository.syncManager ?: run {
            onDone(false, "Cloud sync manager not ready")
            return
        }
        viewModelScope.launch {
            try {
                manager.syncAllLocalToCloud()
                onDone(true, "All local tasks, users, and shift records synced to Google Cloud Firestore!")
            } catch (e: Exception) {
                onDone(false, e.message ?: "Sync failed")
            }
        }
    }

    val users: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasks: StateFlow<List<TaskEntity>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val storeConfigs: StateFlow<Map<String, String>> = repository.allConfigs
        .map { list -> list.associate { it.key to it.value } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allCompletions: StateFlow<List<TaskCompletionEntity>> = repository.allCompletions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val completionsForSelectedDate: StateFlow<List<TaskCompletionEntity>> = _selectedDate
        .flatMapLatest { date -> repository.getCompletionsForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val notesForSelectedDate: StateFlow<List<TaskNoteEntity>> = _selectedDate
        .flatMapLatest { date -> repository.getNotesForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val photosForSelectedDate: StateFlow<List<TaskPhotoEntity>> = _selectedDate
        .flatMapLatest { date -> repository.getPhotosForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPhotos: StateFlow<List<TaskPhotoEntity>> = repository.allPhotos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 7-day report calculations
    val reportStats: StateFlow<ReportStats> = combine(
        tasks,
        allCompletions
    ) { allTaskList, completions ->
        calculateReportStats(allTaskList, completions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReportStats())

    private val monthFormatter = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private val monthDisplayFormatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    private val _selectedReportMonth = MutableStateFlow(monthFormatter.format(Date()))
    val selectedReportMonth: StateFlow<String> = _selectedReportMonth.asStateFlow()

    fun setSelectedReportMonth(yearMonth: String) {
        _selectedReportMonth.value = yearMonth
    }

    fun changeReportMonth(offsetMonths: Int) {
        try {
            val current = _selectedReportMonth.value
            val parsed = monthFormatter.parse(current) ?: Date()
            val cal = Calendar.getInstance().apply {
                time = parsed
                add(Calendar.MONTH, offsetMonths)
            }
            _selectedReportMonth.value = monthFormatter.format(cal.time)
        } catch (_: Exception) {}
    }

    val monthlyReportStats: StateFlow<MonthlyReportStats> = combine(
        allCompletions,
        _selectedReportMonth
    ) { completions, monthStr ->
        calculateMonthlyReportStats(completions, monthStr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthlyReportStats())

    // Rollover: calculate how many days each task has been outstanding for on selected date
    val outstandingDaysMap: StateFlow<Map<Long, Int>> = combine(
        tasks,
        allCompletions,
        _selectedDate
    ) { taskList, completions, selectedDateStr ->
        calculateOutstandingDays(taskList, completions, selectedDateStr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        viewModelScope.launch {
            repository.ensureInitialData()
        }
    }

    private fun calculateReportStats(
        taskList: List<TaskEntity>,
        completions: List<TaskCompletionEntity>
    ): ReportStats {
        val calendar = Calendar.getInstance()
        val last7Dates = mutableListOf<String>()
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            last7Dates.add(dateFormatter.format(cal.time))
        }

        val completionsIn7Days = completions.filter { it.dateString in last7Dates }
        val totalSignOffs = completionsIn7Days.size

        val dailyBreakdown = last7Dates.map { date ->
            DailyCount(date, completions.count { it.dateString == date })
        }

        val staffMap = mutableMapOf<Long, StaffCount>()
        for (c in completionsIn7Days) {
            val existing = staffMap[c.completedByUserId]
            if (existing != null) {
                staffMap[c.completedByUserId] = existing.copy(count = existing.count + 1)
            } else {
                staffMap[c.completedByUserId] = StaffCount(
                    userId = c.completedByUserId,
                    name = c.completedByUserName,
                    initials = c.completedByUserInitials,
                    count = 1
                )
            }
        }
        val topStaff = staffMap.values.sortedByDescending { it.count }

        // Most skipped tasks over last 7 days
        val skippedList = taskList.map { task ->
            val daysCompleted = last7Dates.count { date ->
                completions.any { it.taskId == task.id && it.dateString == date }
            }
            val missedDays = 7 - daysCompleted
            SkippedTaskInfo(task.title, missedDays, 7)
        }.sortedByDescending { it.missedDays }

        return ReportStats(
            totalSignOffsLast7Days = totalSignOffs,
            dailyBreakdown = dailyBreakdown,
            topStaff = topStaff,
            mostSkippedTasks = skippedList
        )
    }

    private fun calculateMonthlyReportStats(
        completions: List<TaskCompletionEntity>,
        monthStr: String
    ): MonthlyReportStats {
        val monthComps = completions.filter { it.dateString.startsWith(monthStr) }
            .sortedByDescending { it.completedAt }
        val completedCount = monthComps.count { !it.isSkipped }
        val skippedCount = monthComps.count { it.isSkipped }
        val total = monthComps.size
        val complianceRate = if (total > 0) ((completedCount * 100) / total) else 0

        val skipReasons = mutableMapOf<String, Int>()
        for (c in monthComps) {
            if (c.isSkipped) {
                val reason = c.skipReason.ifBlank { "Unspecified" }
                skipReasons[reason] = (skipReasons[reason] ?: 0) + 1
            }
        }

        val staffMap = mutableMapOf<Long, StaffCount>()
        for (c in monthComps) {
            val existing = staffMap[c.completedByUserId]
            if (existing != null) {
                staffMap[c.completedByUserId] = existing.copy(count = existing.count + 1)
            } else {
                staffMap[c.completedByUserId] = StaffCount(
                    userId = c.completedByUserId,
                    name = c.completedByUserName,
                    initials = c.completedByUserInitials,
                    count = 1
                )
            }
        }

        val displayName = try {
            val date = monthFormatter.parse(monthStr) ?: Date()
            monthDisplayFormatter.format(date)
        } catch (_: Exception) {
            monthStr
        }

        return MonthlyReportStats(
            selectedMonth = monthStr,
            monthDisplayName = displayName,
            totalRecords = total,
            completedCount = completedCount,
            skippedCount = skippedCount,
            complianceRatePercent = complianceRate,
            skipReasonsBreakdown = skipReasons.toList().sortedByDescending { it.second }.toMap(),
            staffBreakdown = staffMap.values.sortedByDescending { it.count },
            completions = monthComps
        )
    }

    private fun calculateOutstandingDays(
        taskList: List<TaskEntity>,
        completions: List<TaskCompletionEntity>,
        selectedDateStr: String
    ): Map<Long, Int> {
        val selectedDate = try {
            dateFormatter.parse(selectedDateStr) ?: Date()
        } catch (_: Exception) {
            Date()
        }

        return taskList.associate { task ->
            val isCompletedToday = completions.any { it.taskId == task.id && it.dateString == selectedDateStr }
            if (isCompletedToday) {
                task.id to 0
            } else if (task.isEffectivelyOneTime) {
                val startDateStr = task.effectiveStartDate()
                if (selectedDateStr > startDateStr) {
                    try {
                        val startDate = dateFormatter.parse(startDateStr) ?: selectedDate
                        val diffMillis = selectedDate.time - startDate.time
                        val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
                        task.id to diffDays
                    } catch (_: Exception) {
                        task.id to 1
                    }
                } else {
                    task.id to 0
                }
            } else {
                val priorCompletions = completions
                    .filter { it.taskId == task.id && it.dateString < selectedDateStr }
                    .sortedByDescending { it.dateString }
                val lastDateStr = priorCompletions.firstOrNull()?.dateString
                if (lastDateStr != null) {
                    try {
                        val lastDate = dateFormatter.parse(lastDateStr) ?: selectedDate
                        val diffMillis = selectedDate.time - lastDate.time
                        val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(1)
                        task.id to diffDays
                    } catch (_: Exception) {
                        task.id to 1
                    }
                } else {
                    try {
                        val createdDate = Date(task.createdAt)
                        val diffMillis = selectedDate.time - createdDate.time
                        val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
                        task.id to diffDays
                    } catch (_: Exception) {
                        task.id to 0
                    }
                }
            }
        }
    }

    // --- Authentication ---
    fun attemptSignIn(
        userId: Long,
        pin: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val valid = repository.verifyUserPin(userId, pin)
            if (valid) {
                val user = repository.getUserById(userId)
                _currentUser.value = user
                _activeScreen.value = AppScreen.TODAY
                onSuccess()
            } else {
                onError("Incorrect PIN. Please try again.")
            }
        }
    }

    fun signOut() {
        _currentUser.value = null
        _activeScreen.value = AppScreen.SIGN_IN
    }

    fun navigateTo(screen: AppScreen) {
        _activeScreen.value = screen
    }

    fun selectAdminTab(tab: AdminTab) {
        _adminTab.value = tab
    }

    fun setSelectedDate(date: String) {
        _selectedDate.value = date
    }

    fun previousDate() {
        try {
            val parsed = dateFormatter.parse(_selectedDate.value) ?: Date()
            val cal = Calendar.getInstance().apply {
                time = parsed
                add(Calendar.DAY_OF_YEAR, -1)
            }
            _selectedDate.value = dateFormatter.format(cal.time)
        } catch (_: Exception) {}
    }

    fun nextDate() {
        try {
            val parsed = dateFormatter.parse(_selectedDate.value) ?: Date()
            val cal = Calendar.getInstance().apply {
                time = parsed
                add(Calendar.DAY_OF_YEAR, 1)
            }
            _selectedDate.value = dateFormatter.format(cal.time)
        } catch (_: Exception) {}
    }

    fun resetToToday() {
        _selectedDate.value = todayDateString
    }

    // --- Shift Operations ---
    fun submitTask(task: TaskEntity, categoryName: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.submitTaskSignOff(
                task = task,
                categoryName = categoryName,
                dateString = _selectedDate.value,
                user = user
            )
        }
    }

    fun submitSubTasksSignOff(
        task: TaskEntity,
        categoryName: String,
        completedSubTasks: List<String>,
        outstandingSubTasks: List<String>,
        isPartial: Boolean
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.submitTaskSignOff(
                task = task,
                categoryName = categoryName,
                dateString = _selectedDate.value,
                user = user,
                completedSubTasks = completedSubTasks,
                outstandingSubTasks = outstandingSubTasks,
                isPartial = isPartial
            )
        }
    }

    fun skipTask(task: TaskEntity, categoryName: String, reason: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.submitTaskSkip(
                task = task,
                categoryName = categoryName,
                dateString = _selectedDate.value,
                user = user,
                reason = reason
            )
        }
    }

    fun submitSelectedTasks(tasksToSignOff: List<Pair<TaskEntity, String>>) {
        val user = _currentUser.value ?: return
        if (tasksToSignOff.isEmpty()) return
        viewModelScope.launch {
            tasksToSignOff.forEach { (task, catName) ->
                repository.submitTaskSignOff(
                    task = task,
                    categoryName = catName,
                    dateString = _selectedDate.value,
                    user = user
                )
            }
        }
    }

    fun toggleTask(task: TaskEntity, categoryName: String) {
        val user = _currentUser.value ?: return
        val isCompleted = completionsForSelectedDate.value.any { it.taskId == task.id }
        // Once signed off, tasks cannot be un-signed off
        if (!isCompleted) {
            submitTask(task, categoryName)
        }
    }

    fun addNote(taskId: Long, text: String) {
        val user = _currentUser.value ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.addNote(
                taskId = taskId,
                dateString = _selectedDate.value,
                text = text.trim(),
                user = user
            )
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
        }
    }

    fun addPhoto(taskId: Long, photoUri: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.addPhoto(
                taskId = taskId,
                dateString = _selectedDate.value,
                photoUri = photoUri,
                user = user
            )
        }
    }

    fun deletePhoto(photoId: Long) {
        viewModelScope.launch {
            repository.deletePhoto(photoId)
        }
    }

    /**
     * Seeds initial verified audit timestamp photos if none exist in the database,
     * ensuring the Admin area logs section immediately displays photos for inspection.
     */
    fun seedSampleAuditPhotosIfEmpty(context: Context) {
        viewModelScope.launch {
            val existingPhotos = repository.allPhotos.first()
            if (existingPhotos.isEmpty()) {
                val taskList = repository.allTasks.first()
                val userList = repository.allUsers.first()
                val todayStr = _selectedDate.value
                val groceryTask = taskList.find { it.title.contains("GROCERY", ignoreCase = true) } ?: taskList.firstOrNull()
                val breadTask = taskList.find { it.title.contains("BREAD", ignoreCase = true) }
                val adminUser = userList.find { it.role == "ADMIN" } ?: userList.firstOrNull()

                if (groceryTask != null && adminUser != null) {
                    val uri1 = com.example.util.AuditPhotoGenerator.createAuditStampPhoto(
                        context = context,
                        taskId = groceryTask.id,
                        taskTitle = groceryTask.title,
                        priority = groceryTask.priority,
                        employeeName = adminUser.name,
                        employeeId = adminUser.id,
                        timestamp = System.currentTimeMillis() - 15 * 60 * 1000L
                    )
                    if (uri1 != null) {
                        repository.addPhoto(groceryTask.id, todayStr, uri1.toString(), adminUser)
                    }
                }

                if (breadTask != null && adminUser != null) {
                    val uri2 = com.example.util.AuditPhotoGenerator.createAuditStampPhoto(
                        context = context,
                        taskId = breadTask.id,
                        taskTitle = breadTask.title,
                        priority = breadTask.priority,
                        employeeName = adminUser.name,
                        employeeId = adminUser.id,
                        timestamp = System.currentTimeMillis() - 45 * 60 * 1000L
                    )
                    if (uri2 != null) {
                        repository.addPhoto(breadTask.id, todayStr, uri2.toString(), adminUser)
                    }
                }
            }
        }
    }

    // --- Admin Operations ---
    fun addUser(name: String, role: String, pin: String = "", onDone: () -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val salt = SecurityHelper.generateSalt()
            val hash = if (pin.length >= 4) SecurityHelper.hashPin(pin, salt) else ""
            val initials = name.split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.take(1).uppercase() }
                .ifEmpty { "U" }

            repository.insertUser(
                UserEntity(
                    name = name.trim().uppercase(),
                    initials = initials,
                    role = role,
                    pinHash = hash,
                    pinSalt = salt,
                    mustChangePin = true
                )
            )
            onDone()
        }
    }

    fun completeFirstLoginPin(userId: Long, newPin: String, onSuccess: () -> Unit) {
        if (newPin.length != 4) return
        viewModelScope.launch {
            repository.updateUserPin(userId, newPin, clearMustChangePin = true)
            val updatedUser = repository.getUserById(userId)
            _currentUser.value = updatedUser
            _activeScreen.value = AppScreen.TODAY
            onSuccess()
        }
    }

    fun updateUserPin(userId: Long, newPin: String, onDone: () -> Unit) {
        updateUserPin(userId, newPin, requireChangeOnNextLogin = false, onDone = onDone)
    }

    fun updateUserPin(userId: Long, newPin: String, requireChangeOnNextLogin: Boolean, onDone: () -> Unit) {
        if (newPin.length < 4) return
        viewModelScope.launch {
            repository.updateUserPin(userId, newPin, clearMustChangePin = !requireChangeOnNextLogin)
            if (requireChangeOnNextLogin) {
                repository.setUserMustChangePin(userId, true)
            }
            onDone()
        }
    }

    fun deleteUser(userId: Long) {
        viewModelScope.launch {
            repository.deleteUser(userId)
        }
    }

    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val order = (categories.value.maxOfOrNull { it.sortOrder } ?: 0) + 1
            repository.insertCategory(
                CategoryEntity(name = name.trim().uppercase(), sortOrder = order)
            )
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            repository.deleteCategory(id)
        }
    }

    fun addTask(
        categoryId: Long,
        title: String,
        bullets: String,
        priority: String = TaskPriority.MEDIUM,
        daysOfWeek: String = DaysOfWeek.ALL,
        isOneTime: Boolean = false,
        oneTimeStartDate: String = ""
    ) {
        if (title.isBlank()) return
        val isTaskOneTime = isOneTime || daysOfWeek.equals(DaysOfWeek.ONE_TIME, ignoreCase = true)
        val startDate = if (oneTimeStartDate.isNotBlank()) {
            oneTimeStartDate
        } else if (isTaskOneTime) {
            _selectedDate.value
        } else {
            ""
        }
        val finalDaysOfWeek = if (isTaskOneTime) DaysOfWeek.ONE_TIME else daysOfWeek.ifBlank { DaysOfWeek.ALL }

        viewModelScope.launch {
            val currentTasks = tasks.value.filter { it.categoryId == categoryId }
            val order = (currentTasks.maxOfOrNull { it.sortOrder } ?: 0) + 1
            repository.insertTask(
                TaskEntity(
                    categoryId = categoryId,
                    title = title.trim(),
                    bulletPoints = bullets.trim(),
                    priority = priority,
                    daysOfWeek = finalDaysOfWeek,
                    sortOrder = order,
                    isOneTime = isTaskOneTime,
                    oneTimeStartDate = startDate
                )
            )
        }
    }

    fun updateTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.updateTask(task)
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
        }
    }

    fun updateBranding(storeName: String, tagline: String) {
        viewModelScope.launch {
            repository.setStoreName(storeName.trim().uppercase())
            repository.setTagline(tagline.trim().uppercase())
        }
    }

    // --- Data Backup & Protection ---
    fun exportBackup(context: Context) {
        viewModelScope.launch {
            com.example.util.DataBackupManager.exportAndShareBackup(context, repository)
        }
    }

    fun exportMonthlyCsvReport(context: Context, monthStr: String = _selectedReportMonth.value) {
        viewModelScope.launch {
            val allComps = allCompletions.value
            val stats = calculateMonthlyReportStats(allComps, monthStr)
            val csvContent = buildString {
                append("Date,Time,Category,Task Title,Status,Staff Name,Staff Initials,Role,Skip Reason,Subtasks Done,Total Subtasks\n")
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                for (c in stats.completions) {
                    val timeStr = timeFormat.format(Date(c.completedAt))
                    val status = if (c.isSkipped) "SKIPPED" else if (c.isPartial) "PARTIAL" else "COMPLETED"
                    val reason = if (c.isSkipped) c.skipReason.ifBlank { "N/A" } else ""
                    append(escapeCsv(c.dateString)).append(",")
                    append(escapeCsv(timeStr)).append(",")
                    append(escapeCsv(c.categoryName)).append(",")
                    append(escapeCsv(c.taskTitle)).append(",")
                    append(escapeCsv(status)).append(",")
                    append(escapeCsv(c.completedByUserName)).append(",")
                    append(escapeCsv(c.completedByUserInitials)).append(",")
                    append(escapeCsv(c.completedByUserRole)).append(",")
                    append(escapeCsv(reason)).append(",")
                    append(c.completedSubTasksCount).append(",")
                    append(c.totalSubTasksCount).append("\n")
                }
            }

            val fileName = "ShiftTask_KPI_Report_${stats.selectedMonth}.csv"
            val exportDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val reportFile = File(exportDir, fileName)
            FileOutputStream(reportFile).use { out ->
                out.write(csvContent.toByteArray(Charsets.UTF_8))
            }

            val authority = "${context.packageName}.fileprovider"
            val contentUri: Uri = FileProvider.getUriForFile(context, authority, reportFile)

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "ShiftTask Monthly KPI Report (${stats.monthDisplayName})")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Attached is the ShiftTask Monthly KPI & Skip Reason Report for ${stats.monthDisplayName} (${stats.totalRecords} records, ${stats.completedCount} completed, ${stats.skippedCount} skipped)."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(sendIntent, "Export Monthly CSV Report")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }

    private fun escapeCsv(value: String): String {
        var v = value.replace("\r", " ").replace("\n", " ")
        if (v.contains(",") || v.contains("\"")) {
            v = "\"" + v.replace("\"", "\"\"") + "\""
        }
        return v
    }

    suspend fun exportBackupJson(): String {
        return com.example.util.DataBackupManager.exportDatabaseToJson(repository)
    }

    fun restoreBackup(jsonString: String, onDone: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = com.example.util.DataBackupManager.restoreDatabaseFromJson(jsonString, repository)
            result.onSuccess { message ->
                onDone(message)
            }.onFailure { err ->
                onError(err.localizedMessage ?: "Failed to restore backup")
            }
        }
    }
}

class TaskTrackerViewModelFactory(
    private val repository: TaskRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TaskTrackerViewModel(repository) as T
    }
}

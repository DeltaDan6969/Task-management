package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.components.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import com.example.ui.theme.*
import com.example.util.SubTaskUtils
import java.text.SimpleDateFormat
import java.util.*

enum class TaskSortMode(val label: String) {
    CATEGORY("BY CATEGORY"),
    PRIORITY_DESC("PRIORITY: HIGH → LOW"),
    PRIORITY_ASC("PRIORITY: LOW → HIGH"),
    OUTSTANDING("OUTSTANDING FIRST")
}

enum class TaskPriorityFilter(val label: String) {
    ALL("ALL"),
    HIGH("HIGH"),
    MEDIUM("MEDIUM"),
    LOW("LOW"),
    OUTSTANDING_ONLY("OUTSTANDING")
}

@Composable
fun TodayScreen(
    currentUser: UserEntity,
    selectedDate: String,
    categories: List<CategoryEntity>,
    tasks: List<TaskEntity>,
    completions: List<TaskCompletionEntity>,
    notes: List<TaskNoteEntity>,
    photos: List<TaskPhotoEntity>,
    outstandingDaysMap: Map<Long, Int> = emptyMap(),
    handovers: List<ShiftHandoverEntity> = emptyList(),
    activeUsers: List<UserEntity> = emptyList(),
    adminUsers: List<UserEntity> = emptyList(),
    onToggleTask: (TaskEntity, String) -> Unit,
    onAddNote: (taskId: Long, text: String) -> Unit,
    onDeleteNote: (noteId: Long) -> Unit,
    onAddPhoto: (taskId: Long, uri: String) -> Unit,
    onDeletePhoto: (photoId: Long) -> Unit,
    onPreviousDate: () -> Unit,
    onNextDate: () -> Unit,
    onResetToToday: () -> Unit,
    onSignOut: () -> Unit,
    onAddTask: ((categoryId: Long, title: String, bullets: String, priority: String, daysOfWeek: String) -> Unit)? = null,
    onSubmitSubTasksSignOff: ((TaskEntity, String, List<String>, List<String>, Boolean) -> Unit)? = null,
    onUpdateTask: ((TaskEntity) -> Unit)? = null,
    onSkipTask: ((TaskEntity, String, String) -> Unit)? = null,
    onAddShiftHandover: ((targetDate: String, shiftType: String, category: String, priority: String, message: String, authorUser: UserEntity) -> Unit)? = null,
    onAcknowledgeShiftHandover: ((String) -> Unit)? = null,
    onDeleteShiftHandover: ((String) -> Unit)? = null,
    onUnlockTask: ((task: TaskEntity, completion: TaskCompletionEntity, adminUser: UserEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var activeNoteDialogTask by remember { mutableStateOf<TaskEntity?>(null) }
    var activePhotoDialogTask by remember { mutableStateOf<TaskEntity?>(null) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var skippingTask by remember { mutableStateOf<Pair<TaskEntity, String>?>(null) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showHandoverDialog by remember { mutableStateOf(false) }
    var taskToUnlock by remember { mutableStateOf<Triple<TaskEntity, TaskCompletionEntity, String>?>(null) }
    var showAllDaysWeekly by remember { mutableStateOf(false) }
    var currentSortMode by remember { mutableStateOf(TaskSortMode.CATEGORY) }
    var currentPriorityFilter by remember { mutableStateOf(TaskPriorityFilter.ALL) }
    var selectedTaskIds by remember { mutableStateOf(setOf<Long>()) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortDropdown by remember { mutableStateOf(false) }
    var showPriorityDropdown by remember { mutableStateOf(false) }

    // Clear task selection whenever date changes
    LaunchedEffect(selectedDate) {
        selectedTaskIds = emptySet()
    }

    val onToggleSelectTask: (TaskEntity) -> Unit = { task ->
        val isAlreadyDone = completions.any { it.taskId == task.id && (!it.isPartial || it.isSkipped) }
        if (!isAlreadyDone) {
            selectedTaskIds = if (task.id in selectedTaskIds) {
                selectedTaskIds - task.id
            } else {
                selectedTaskIds + task.id
            }
        }
    }

    val onSubmitSingleTask: (TaskEntity, String) -> Unit = { task, catName ->
        onToggleTask(task, catName)
        selectedTaskIds = selectedTaskIds - task.id
    }

    val onSubmitSubTasksHandler: (TaskEntity, String, List<String>, List<String>, Boolean) -> Unit = { task, catName, compList, outList, isPartial ->
        if (onSubmitSubTasksSignOff != null) {
            onSubmitSubTasksSignOff(task, catName, compList, outList, isPartial)
        } else {
            onToggleTask(task, catName)
        }
        selectedTaskIds = selectedTaskIds - task.id
    }

    val onOpenSkipHandler: (TaskEntity, String) -> Unit = { task, catName ->
        skippingTask = Pair(task, catName)
    }

    val onEditTaskHandler: ((TaskEntity) -> Unit)? = if (currentUser.role == "ADMIN") {
        { task -> editingTask = task }
    } else null

    val dayOfWeekName = remember(selectedDate) {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = parser.parse(selectedDate) ?: Date()
            val dayFormat = SimpleDateFormat("EEEE", Locale.US)
            dayFormat.format(date).uppercase()
        } catch (_: Exception) {
            "MONDAY"
        }
    }

    // Filter tasks active for the selected date's schedule (including one-time rollovers)
    val scheduledTasks = remember(tasks, dayOfWeekName, selectedDate, showAllDaysWeekly, completions) {
        if (showAllDaysWeekly) {
            tasks
        } else {
            tasks.filter { task ->
                task.isScheduledForDate(selectedDate, dayOfWeekName, completions)
            }
        }
    }

    val totalTasks = scheduledTasks.size
    val scheduledTaskIds = remember(scheduledTasks) { scheduledTasks.map { it.id }.toSet() }
    val completedCount = remember(completions, scheduledTaskIds) {
        completions.count { it.taskId in scheduledTaskIds }
    }
    val progressFraction = remember(completedCount, totalTasks) {
        if (totalTasks > 0) completedCount.toFloat() / totalTasks else 0f
    }
    val progressPercent = remember(progressFraction) {
        (progressFraction * 100).toInt()
    }

    val overdueCount = remember(outstandingDaysMap, scheduledTaskIds) {
        scheduledTaskIds.count { (outstandingDaysMap[it] ?: 0) > 0 }
    }

    // Filter tasks based on priority, outstanding filter, and real-time search query
    val filteredTasks = remember(scheduledTasks, currentPriorityFilter, outstandingDaysMap, searchQuery) {
        val query = searchQuery.trim().lowercase()
        scheduledTasks.filter { task ->
            val matchesPriority = when (currentPriorityFilter) {
                TaskPriorityFilter.ALL -> true
                TaskPriorityFilter.HIGH -> task.priority.equals(TaskPriority.HIGH, ignoreCase = true)
                TaskPriorityFilter.MEDIUM -> task.priority.equals(TaskPriority.MEDIUM, ignoreCase = true)
                TaskPriorityFilter.LOW -> task.priority.equals(TaskPriority.LOW, ignoreCase = true)
                TaskPriorityFilter.OUTSTANDING_ONLY -> (outstandingDaysMap[task.id] ?: 0) > 0
            }
            val matchesSearch = query.isEmpty() || task.title.lowercase().contains(query)
            matchesPriority && matchesSearch
        }
    }

    val categoryMap = remember(categories) {
        categories.associateBy { it.id }
    }

    // High-performance O(1) Lookup Maps for 60fps / 120fps fluid scrolling
    val completionMap = remember(completions) { completions.associateBy { it.taskId } }
    val notesMap = remember(notes) { notes.groupBy { it.taskId } }
    val photosMap = remember(photos) { photos.groupBy { it.taskId } }
    val categoryTasksMap = remember(filteredTasks) { filteredTasks.groupBy { it.categoryId } }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(RetailBackground)
    ) {
        val isTablet = maxWidth >= 540.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isTablet) 20.dp else 16.dp)
        ) {
            // Header Bar
            if (isTablet) {
                // High-Density Tablet Dashboard Header matching Mockup IMG_3681
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Date, Arrows, Today pill, and Extra Task button
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$dayOfWeekName • $selectedDate",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = RetailTextPrimary,
                            letterSpacing = 0.5.sp,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        // Segmented Arrow Navigation Pill [ ← | → ]
                        Row(
                            modifier = Modifier
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
                                .background(Color.White, RoundedCornerShape(6.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 30.dp, height = 28.dp)
                                    .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                                    .clickable(onClick = onPreviousDate),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Previous Day",
                                    tint = RetailTextPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(18.dp)
                                    .background(Color(0xFFCBD5E1))
                            )
                            Box(
                                modifier = Modifier
                                    .size(width = 30.dp, height = 28.dp)
                                    .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                                    .clickable(onClick = onNextDate),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next Day",
                                    tint = RetailTextPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            onClick = onResetToToday,
                            color = RetailHeaderBlack,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "TODAY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = { showAddTaskDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = RetailOrange),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "EXTRA TASK",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Right: Progress Overview Card & Overdue Rollover Alert (No redundant second avatar)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.widthIn(min = 160.dp, max = 220.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$completedCount / $totalTasks SIGNED OFF",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF334155)
                                    )
                                    Text(
                                        text = "$progressPercent%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = RetailOrange
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { progressFraction.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = RetailOrange,
                                    trackColor = Color(0xFFE2E8F0)
                                )
                            }
                        }

                        if (overdueCount > 0) {
                            Surface(
                                color = Color(0xFFFEE2E2),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFB91C1C),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$overdueCount OVERDUE ROLLOVER",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFFB91C1C),
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        } else {
                            Surface(
                                color = Color(0xFFDCFCE7),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFF86EFAC))
                            ) {
                                Text(
                                    text = "✓ ALL UP TO DATE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // Phone Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = dayOfWeekName,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = RetailTextPrimary,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = onPreviousDate, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Day", tint = RetailTextSecondary)
                            }
                            IconButton(onClick = onNextDate, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Day", tint = RetailTextSecondary)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = { showAddTaskDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = RetailOrange),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("+ EXTRA", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            text = selectedDate,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RetailTextSecondary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(RetailOrange, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentUser.initials,
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        }

                        IconButton(
                            onClick = onSignOut,
                            modifier = Modifier
                                .size(36.dp)
                                .border(1.dp, RetailBorder, RoundedCornerShape(4.dp))
                                .testTag("signout_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Sign Out",
                                tint = RetailTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$completedCount / $totalTasks SIGNED OFF",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RetailTextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "$progressPercent%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RetailOrange
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = RetailOrange,
                    trackColor = RetailBorder
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Merged Toolbar: Search (left ~35%), Sort Dropdown, Priority Dropdown, Overdue Quick-Toggle (right)
            if (isTablet) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Search input field with centered BasicTextField to prevent vertical clipping
                    Surface(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(42.dp)
                            .testTag("task_search_input"),
                        shape = RoundedCornerShape(8.dp),
                        color = RetailSurface,
                        border = BorderStroke(1.dp, if (searchQuery.isNotEmpty()) RetailOrange else Color(0xFFCBD5E1))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search tasks",
                                tint = if (searchQuery.isNotEmpty()) RetailOrange else RetailTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search tasks by title...",
                                        fontSize = 12.sp,
                                        color = RetailTextSecondary,
                                        maxLines = 1
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = RetailTextPrimary
                                    ),
                                    cursorBrush = SolidColor(RetailOrange),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                        focusManager.clearFocus()
                                    },
                                    modifier = Modifier
                                        .size(24.dp)
                                        .testTag("clear_search_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = RetailTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Sort: Category ▾ Dropdown Pill
                    Box {
                        Surface(
                            onClick = { showSortDropdown = true },
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.height(38.dp).testTag("sort_dropdown_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val sortLabel = when (currentSortMode) {
                                    TaskSortMode.CATEGORY -> "Category"
                                    TaskSortMode.PRIORITY_DESC -> "High → Low"
                                    TaskSortMode.PRIORITY_ASC -> "Low → High"
                                    TaskSortMode.OUTSTANDING -> "Outstanding"
                                }
                                Text(
                                    text = "Sort: $sortLabel ▾",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RetailTextPrimary
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showSortDropdown,
                            onDismissRequest = { showSortDropdown = false }
                        ) {
                            TaskSortMode.values().forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = mode.label,
                                            fontSize = 11.sp,
                                            fontWeight = if (currentSortMode == mode) FontWeight.Black else FontWeight.Normal,
                                            color = if (currentSortMode == mode) RetailOrange else RetailTextPrimary
                                        )
                                    },
                                    onClick = {
                                        currentSortMode = mode
                                        showSortDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Priority: All ▾ Dropdown Pill
                    Box {
                        Surface(
                            onClick = { showPriorityDropdown = true },
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.height(38.dp).testTag("priority_dropdown_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val prioLabel = when (currentPriorityFilter) {
                                    TaskPriorityFilter.ALL -> "All"
                                    TaskPriorityFilter.HIGH -> "High"
                                    TaskPriorityFilter.MEDIUM -> "Medium"
                                    TaskPriorityFilter.LOW -> "Low"
                                    TaskPriorityFilter.OUTSTANDING_ONLY -> "Overdue"
                                }
                                Text(
                                    text = "Priority: $prioLabel ▾",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RetailTextPrimary
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showPriorityDropdown,
                            onDismissRequest = { showPriorityDropdown = false }
                        ) {
                            TaskPriorityFilter.values().forEach { filter ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = filter.label,
                                            fontSize = 11.sp,
                                            fontWeight = if (currentPriorityFilter == filter) FontWeight.Black else FontWeight.Normal,
                                            color = if (currentPriorityFilter == filter) RetailOrange else RetailTextPrimary
                                        )
                                    },
                                    onClick = {
                                        currentPriorityFilter = filter
                                        showPriorityDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Overdue Quick-Toggle Switch on the far right (matching Mockup IMG_3681)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Overdue ($overdueCount)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentPriorityFilter == TaskPriorityFilter.OUTSTANDING_ONLY) Color(0xFFB91C1C) else RetailTextPrimary
                        )
                        Switch(
                            checked = currentPriorityFilter == TaskPriorityFilter.OUTSTANDING_ONLY,
                            onCheckedChange = { isChecked ->
                                currentPriorityFilter = if (isChecked) TaskPriorityFilter.OUTSTANDING_ONLY else TaskPriorityFilter.ALL
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = RetailOrange,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier.testTag("overdue_toggle_switch")
                        )
                    }
                }
            } else {
                // Phone compact toolbar
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().testTag("task_search_input"),
                        placeholder = { Text(text = "Search tasks by title...", fontSize = 12.sp, color = RetailTextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search tasks", tint = RetailTextSecondary, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = ""; focusManager.clearFocus() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search", tint = RetailTextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Sort pill
                            Box {
                                Surface(
                                    onClick = { showSortDropdown = true },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                ) {
                                    Text(
                                        text = "Sort: ${currentSortMode.label} ▾",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                DropdownMenu(expanded = showSortDropdown, onDismissRequest = { showSortDropdown = false }) {
                                    TaskSortMode.values().forEach { mode ->
                                        DropdownMenuItem(
                                            text = { Text(mode.label, fontSize = 11.sp) },
                                            onClick = { currentSortMode = mode; showSortDropdown = false }
                                        )
                                    }
                                }
                            }
                            // Priority pill
                            Box {
                                Surface(
                                    onClick = { showPriorityDropdown = true },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                ) {
                                    Text(
                                        text = "Priority: ${currentPriorityFilter.label} ▾",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                DropdownMenu(expanded = showPriorityDropdown, onDismissRequest = { showPriorityDropdown = false }) {
                                    TaskPriorityFilter.values().forEach { filter ->
                                        DropdownMenuItem(
                                            text = { Text(filter.label, fontSize = 11.sp) },
                                            onClick = { currentPriorityFilter = filter; showPriorityDropdown = false }
                                        )
                                    }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Overdue ($overdueCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Switch(
                                checked = currentPriorityFilter == TaskPriorityFilter.OUTSTANDING_ONLY,
                                onCheckedChange = { isChecked ->
                                    currentPriorityFilter = if (isChecked) TaskPriorityFilter.OUTSTANDING_ONLY else TaskPriorityFilter.ALL
                                }
                            )
                        }
                    }
                }
            }

            if (searchQuery.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filtering by title: \"$searchQuery\" (${filteredTasks.size} match${if (filteredTasks.size == 1) "" else "es"})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RetailTextSecondary
                    )
                    TextButton(
                        onClick = {
                            searchQuery = ""
                            focusManager.clearFocus()
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text(
                            text = "CLEAR FILTER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = RetailOrange
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Task Content
            if (filteredTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(RetailSurface, RoundedCornerShape(6.dp))
                        .border(1.dp, RetailBorder, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        val isSearching = searchQuery.isNotBlank()
                        val emptyTitle = if (isSearching) {
                            "NO TASKS MATCHING \"${searchQuery.uppercase()}\""
                        } else if (categories.isEmpty()) {
                            "NO TASKS OR CATEGORIES CREATED YET"
                        } else {
                            "NO TASKS FOUND FOR THIS FILTER"
                        }

                        val emptySubtitle = if (isSearching) {
                            "Try searching with another keyword or clear the search input to see all tasks."
                        } else if (categories.isEmpty()) {
                            "The app is starting fresh with a clean slate. Sign in as Admin to create categories and recurring tasks."
                        } else {
                            "Change your day/priority filter or add new tasks in the Admin section."
                        }

                        Text(
                            text = emptyTitle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = RetailTextPrimary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = emptySubtitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = RetailTextSecondary
                        )
                        if (isSearching) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    searchQuery = ""
                                    focusManager.clearFocus()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RetailOrange),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.testTag("empty_state_clear_search_button")
                            ) {
                                Text("CLEAR SEARCH", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                when (currentSortMode) {
                    TaskSortMode.CATEGORY -> {
                        val activeCategories = remember(categories, categoryTasksMap) {
                            categories.filter { categoryTasksMap[it.id]?.isNotEmpty() == true }
                        }

                        if (isTablet) {
                            // Tablet 2-Column Responsive Grid with stable keys
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 340.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(activeCategories, key = { it.id }) { category ->
                                    val categoryTasks = categoryTasksMap[category.id] ?: emptyList()
                                    val completedInCat = remember(categoryTasks, completionMap) {
                                        categoryTasks.count { task ->
                                            val c = completionMap[task.id]
                                            c != null && (!c.isPartial || c.isSkipped)
                                        }
                                    }

                                    CategoryTaskCard(
                                        categoryName = category.name,
                                        completedCount = completedInCat,
                                        totalCount = categoryTasks.size,
                                        tasks = categoryTasks,
                                        completionMap = completionMap,
                                        notesMap = notesMap,
                                        photosMap = photosMap,
                                        outstandingDaysMap = outstandingDaysMap,
                                        selectedTaskIds = selectedTaskIds,
                                        onSelectTask = onToggleSelectTask,
                                        onSubmitSignOff = { task -> onSubmitSingleTask(task, category.name) },
                                        onSubmitSubTasksSignOff = onSubmitSubTasksHandler,
                                        onOpenNotes = { task -> activeNoteDialogTask = task },
                                        onOpenPhotos = { task -> activePhotoDialogTask = task },
                                        onEditTask = onEditTaskHandler,
                                        onSkipTask = { task -> onOpenSkipHandler(task, category.name) },
                                        onUnlockTask = { task, comp -> taskToUnlock = Triple(task, comp, category.name) }
                                    )
                                }
                            }
                        } else {
                            // Phone Single Column with stable keys
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(activeCategories, key = { it.id }) { category ->
                                    val categoryTasks = categoryTasksMap[category.id] ?: emptyList()
                                    val completedInCat = remember(categoryTasks, completionMap) {
                                        categoryTasks.count { task ->
                                            val c = completionMap[task.id]
                                            c != null && (!c.isPartial || c.isSkipped)
                                        }
                                    }

                                    CategoryTaskCard(
                                        categoryName = category.name,
                                        completedCount = completedInCat,
                                        totalCount = categoryTasks.size,
                                        tasks = categoryTasks,
                                        completionMap = completionMap,
                                        notesMap = notesMap,
                                        photosMap = photosMap,
                                        outstandingDaysMap = outstandingDaysMap,
                                        selectedTaskIds = selectedTaskIds,
                                        onSelectTask = onToggleSelectTask,
                                        onSubmitSignOff = { task -> onSubmitSingleTask(task, category.name) },
                                        onSubmitSubTasksSignOff = onSubmitSubTasksHandler,
                                        onOpenNotes = { task -> activeNoteDialogTask = task },
                                        onOpenPhotos = { task -> activePhotoDialogTask = task },
                                        onEditTask = onEditTaskHandler,
                                        onSkipTask = { task -> onOpenSkipHandler(task, category.name) },
                                        onUnlockTask = { task, comp -> taskToUnlock = Triple(task, comp, category.name) }
                                    )
                                }
                            }
                        }
                    }

                    TaskSortMode.PRIORITY_DESC, TaskSortMode.PRIORITY_ASC -> {
                        val activeTiers = remember(currentSortMode, filteredTasks) {
                            val priorityTiers = if (currentSortMode == TaskSortMode.PRIORITY_DESC) {
                                listOf(
                                    Triple(TaskPriority.HIGH, "HIGH PRIORITY TASKS", RetailRedMissed),
                                    Triple(TaskPriority.MEDIUM, "MEDIUM PRIORITY TASKS", RetailOrange),
                                    Triple(TaskPriority.LOW, "LOW PRIORITY TASKS", RetailHeaderBlackLight)
                                )
                            } else {
                                listOf(
                                    Triple(TaskPriority.LOW, "LOW PRIORITY TASKS", RetailHeaderBlackLight),
                                    Triple(TaskPriority.MEDIUM, "MEDIUM PRIORITY TASKS", RetailOrange),
                                    Triple(TaskPriority.HIGH, "HIGH PRIORITY TASKS", RetailRedMissed)
                                )
                            }
                            priorityTiers.filter { (tierPriority, _, _) ->
                                filteredTasks.any { it.priority.equals(tierPriority, ignoreCase = true) }
                            }
                        }

                        if (isTablet) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 340.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(activeTiers, key = { it.first }) { (tierPriority, tierTitle, tierColor) ->
                                    val tierTasks = remember(filteredTasks, tierPriority) {
                                        filteredTasks.filter { it.priority.equals(tierPriority, ignoreCase = true) }
                                    }
                                    val completedInTier = remember(tierTasks, completionMap) {
                                        tierTasks.count { task ->
                                            val c = completionMap[task.id]
                                            c != null && (!c.isPartial || c.isSkipped)
                                        }
                                    }

                                    PriorityTierCard(
                                        title = tierTitle,
                                        bannerColor = tierColor,
                                        completedCount = completedInTier,
                                        totalCount = tierTasks.size,
                                        tasks = tierTasks,
                                        categoryMap = categoryMap,
                                        completionMap = completionMap,
                                        notesMap = notesMap,
                                        photosMap = photosMap,
                                        outstandingDaysMap = outstandingDaysMap,
                                        selectedTaskIds = selectedTaskIds,
                                        onSelectTask = onToggleSelectTask,
                                        onSubmitSignOff = onSubmitSingleTask,
                                        onSubmitSubTasksSignOff = onSubmitSubTasksHandler,
                                        onOpenNotes = { task -> activeNoteDialogTask = task },
                                        onOpenPhotos = { task -> activePhotoDialogTask = task },
                                        onEditTask = onEditTaskHandler,
                                        onSkipTask = onOpenSkipHandler,
                                        onUnlockTask = { task, comp -> taskToUnlock = Triple(task, comp, "") }
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(activeTiers, key = { it.first }) { (tierPriority, tierTitle, tierColor) ->
                                    val tierTasks = remember(filteredTasks, tierPriority) {
                                        filteredTasks.filter { it.priority.equals(tierPriority, ignoreCase = true) }
                                    }
                                    val completedInTier = remember(tierTasks, completionMap) {
                                        tierTasks.count { task ->
                                            val c = completionMap[task.id]
                                            c != null && (!c.isPartial || c.isSkipped)
                                        }
                                    }

                                    PriorityTierCard(
                                        title = tierTitle,
                                        bannerColor = tierColor,
                                        completedCount = completedInTier,
                                        totalCount = tierTasks.size,
                                        tasks = tierTasks,
                                        categoryMap = categoryMap,
                                        completionMap = completionMap,
                                        notesMap = notesMap,
                                        photosMap = photosMap,
                                        outstandingDaysMap = outstandingDaysMap,
                                        selectedTaskIds = selectedTaskIds,
                                        onSelectTask = onToggleSelectTask,
                                        onSubmitSignOff = onSubmitSingleTask,
                                        onSubmitSubTasksSignOff = onSubmitSubTasksHandler,
                                        onOpenNotes = { task -> activeNoteDialogTask = task },
                                        onOpenPhotos = { task -> activePhotoDialogTask = task },
                                        onEditTask = onEditTaskHandler,
                                        onSkipTask = onOpenSkipHandler,
                                        onUnlockTask = { task, comp -> taskToUnlock = Triple(task, comp, "") }
                                    )
                                }
                            }
                        }
                    }

                    TaskSortMode.OUTSTANDING -> {
                        val sortedTasks = remember(filteredTasks, outstandingDaysMap) {
                            filteredTasks.sortedWith(
                                compareByDescending<TaskEntity> { outstandingDaysMap[it.id] ?: 0 }
                                    .thenByDescending { TaskPriority.priorityWeight(it.priority) }
                            )
                        }

                        val overdueTasks = remember(sortedTasks, outstandingDaysMap) {
                            sortedTasks.filter { (outstandingDaysMap[it.id] ?: 0) > 0 }
                        }
                        val todayTasks = remember(sortedTasks, outstandingDaysMap) {
                            sortedTasks.filter { (outstandingDaysMap[it.id] ?: 0) == 0 }
                        }

                        if (isTablet) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 340.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (overdueTasks.isNotEmpty()) {
                                    item(key = "overdue_tasks_section") {
                                        val completedOverdue = remember(overdueTasks, completionMap) {
                                            overdueTasks.count { t ->
                                                val c = completionMap[t.id]
                                                c != null && (!c.isPartial || c.isSkipped)
                                            }
                                        }
                                        PriorityTierCard(
                                            title = "⚡ OVERDUE & CONTINUED TASKS",
                                            bannerColor = RetailRedMissed,
                                            completedCount = completedOverdue,
                                            totalCount = overdueTasks.size,
                                            tasks = overdueTasks,
                                            categoryMap = categoryMap,
                                            completionMap = completionMap,
                                            notesMap = notesMap,
                                            photosMap = photosMap,
                                            outstandingDaysMap = outstandingDaysMap,
                                            selectedTaskIds = selectedTaskIds,
                                            onSelectTask = onToggleSelectTask,
                                            onSubmitSignOff = onSubmitSingleTask,
                                            onSubmitSubTasksSignOff = onSubmitSubTasksHandler,
                                            onOpenNotes = { task -> activeNoteDialogTask = task },
                                            onOpenPhotos = { task -> activePhotoDialogTask = task },
                                            onEditTask = onEditTaskHandler,
                                            onSkipTask = onOpenSkipHandler,
                                            onUnlockTask = { task, comp -> taskToUnlock = Triple(task, comp, "") }
                                        )
                                    }
                                }

                                if (todayTasks.isNotEmpty()) {
                                    item(key = "today_tasks_section") {
                                        val completedToday = remember(todayTasks, completionMap) {
                                            todayTasks.count { t ->
                                                val c = completionMap[t.id]
                                                c != null && (!c.isPartial || c.isSkipped)
                                            }
                                        }
                                        PriorityTierCard(
                                            title = "SCHEDULED TODAY",
                                            bannerColor = RetailHeaderBlack,
                                            completedCount = completedToday,
                                            totalCount = todayTasks.size,
                                            tasks = todayTasks,
                                            categoryMap = categoryMap,
                                            completionMap = completionMap,
                                            notesMap = notesMap,
                                            photosMap = photosMap,
                                            outstandingDaysMap = outstandingDaysMap,
                                            selectedTaskIds = selectedTaskIds,
                                            onSelectTask = onToggleSelectTask,
                                            onSubmitSignOff = onSubmitSingleTask,
                                            onSubmitSubTasksSignOff = onSubmitSubTasksHandler,
                                            onOpenNotes = { task -> activeNoteDialogTask = task },
                                            onOpenPhotos = { task -> activePhotoDialogTask = task },
                                            onEditTask = onEditTaskHandler,
                                            onSkipTask = onOpenSkipHandler,
                                            onUnlockTask = { task, comp -> taskToUnlock = Triple(task, comp, "") }
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (overdueTasks.isNotEmpty()) {
                                    item(key = "overdue_tasks_section") {
                                        val completedOverdue = remember(overdueTasks, completionMap) {
                                            overdueTasks.count { t ->
                                                val c = completionMap[t.id]
                                                c != null && (!c.isPartial || c.isSkipped)
                                            }
                                        }
                                        PriorityTierCard(
                                            title = "⚡ OVERDUE & CONTINUED TASKS",
                                            bannerColor = RetailRedMissed,
                                            completedCount = completedOverdue,
                                            totalCount = overdueTasks.size,
                                            tasks = overdueTasks,
                                            categoryMap = categoryMap,
                                            completionMap = completionMap,
                                            notesMap = notesMap,
                                            photosMap = photosMap,
                                            outstandingDaysMap = outstandingDaysMap,
                                            selectedTaskIds = selectedTaskIds,
                                            onSelectTask = onToggleSelectTask,
                                            onSubmitSignOff = onSubmitSingleTask,
                                            onSubmitSubTasksSignOff = onSubmitSubTasksHandler,
                                            onOpenNotes = { task -> activeNoteDialogTask = task },
                                            onOpenPhotos = { task -> activePhotoDialogTask = task },
                                            onEditTask = onEditTaskHandler,
                                            onSkipTask = onOpenSkipHandler,
                                            onUnlockTask = { task, comp -> taskToUnlock = Triple(task, comp, "") }
                                        )
                                    }
                                }

                                if (todayTasks.isNotEmpty()) {
                                    item(key = "today_tasks_section") {
                                        val completedToday = remember(todayTasks, completionMap) {
                                            todayTasks.count { t ->
                                                val c = completionMap[t.id]
                                                c != null && (!c.isPartial || c.isSkipped)
                                            }
                                        }
                                        PriorityTierCard(
                                            title = "SCHEDULED TODAY",
                                            bannerColor = RetailHeaderBlack,
                                            completedCount = completedToday,
                                            totalCount = todayTasks.size,
                                            tasks = todayTasks,
                                            categoryMap = categoryMap,
                                            completionMap = completionMap,
                                            notesMap = notesMap,
                                            photosMap = photosMap,
                                            outstandingDaysMap = outstandingDaysMap,
                                            selectedTaskIds = selectedTaskIds,
                                            onSelectTask = onToggleSelectTask,
                                            onSubmitSignOff = onSubmitSingleTask,
                                            onSubmitSubTasksSignOff = onSubmitSubTasksHandler,
                                            onOpenNotes = { task -> activeNoteDialogTask = task },
                                            onOpenPhotos = { task -> activePhotoDialogTask = task },
                                            onEditTask = onEditTaskHandler,
                                            onSkipTask = onOpenSkipHandler,
                                            onUnlockTask = { task, comp -> taskToUnlock = Triple(task, comp, "") }
                                        )
                                    }
                                }

                                if (todayTasks.isNotEmpty()) {
                                    item(key = "today_tasks_section") {
                                        val completedToday = remember(todayTasks, completionMap) {
                                            todayTasks.count { t ->
                                                val c = completionMap[t.id]
                                                c != null && (!c.isPartial || c.isSkipped)
                                            }
                                        }
                                        PriorityTierCard(
                                            title = "SCHEDULED TODAY",
                                            bannerColor = RetailHeaderBlack,
                                            completedCount = completedToday,
                                            totalCount = todayTasks.size,
                                            tasks = todayTasks,
                                            categoryMap = categoryMap,
                                            completionMap = completionMap,
                                            notesMap = notesMap,
                                            photosMap = photosMap,
                                            outstandingDaysMap = outstandingDaysMap,
                                            selectedTaskIds = selectedTaskIds,
                                            onSelectTask = onToggleSelectTask,
                                            onSubmitSignOff = onSubmitSingleTask,
                                            onSubmitSubTasksSignOff = onSubmitSubTasksHandler,
                                            onOpenNotes = { task -> activeNoteDialogTask = task },
                                            onOpenPhotos = { task -> activePhotoDialogTask = task },
                                            onEditTask = onEditTaskHandler,
                                            onSkipTask = onOpenSkipHandler,
                                            onUnlockTask = { task, comp -> taskToUnlock = Triple(task, comp, "") }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Batch Submit Bar for multiple selected tasks
                if (selectedTaskIds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = RetailHeaderBlack,
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("submit_selected_tasks_bar")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .background(RetailOrange, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${selectedTaskIds.size} SELECTED",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Ready to submit as ${currentUser.name}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { selectedTaskIds = emptySet() },
                                    shape = RoundedCornerShape(4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Text("CLEAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        val toSign = scheduledTasks.filter { it.id in selectedTaskIds }
                                        toSign.forEach { task ->
                                            val cat = categoryMap[task.categoryId]?.name ?: "TASK"
                                            onToggleTask(task, cat)
                                        }
                                        selectedTaskIds = emptySet()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RetailOrange),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.testTag("submit_all_selected_button")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "SUBMIT SIGN OFF (${selectedTaskIds.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Notes Dialog
        activeNoteDialogTask?.let { task ->
            val taskNotes = notes.filter { it.taskId == task.id }
            NoteDialog(
                task = task,
                notes = taskNotes,
                onAddNote = { text -> onAddNote(task.id, text) },
                onDeleteNote = onDeleteNote,
                onDismiss = { activeNoteDialogTask = null }
            )
        }

        // Photos Dialog
        activePhotoDialogTask?.let { task ->
            val taskPhotos = photos.filter { it.taskId == task.id }
            PhotoDialog(
                task = task,
                photos = taskPhotos,
                onAddPhoto = { uri -> onAddPhoto(task.id, uri) },
                onDeletePhoto = onDeletePhoto,
                onDismiss = { activePhotoDialogTask = null }
            )
        }

        // Add Task Dialog (for setting tasks or adding extra tasks for this day of the week)
        if (showAddTaskDialog) {
            AddTaskDialog(
                categories = categories,
                initialDay = dayOfWeekName,
                onDismiss = { showAddTaskDialog = false },
                onAddTask = { catId, title, bullets, priority, daysOfWeek ->
                    onAddTask?.invoke(catId, title, bullets, priority, daysOfWeek)
                    showAddTaskDialog = false
                }
            )
        }

        // Edit Task Dialog
        editingTask?.let { task ->
            EditTaskDialog(
                task = task,
                categories = categories,
                onDismiss = { editingTask = null },
                onSave = { updated ->
                    onUpdateTask?.invoke(updated)
                    editingTask = null
                }
            )
        }

        // Skip Task Dialog
        skippingTask?.let { (task, catName) ->
            SkipTaskDialog(
                task = task,
                categoryName = catName,
                onDismiss = { skippingTask = null },
                onConfirmSkip = { reason ->
                    onSkipTask?.invoke(task, catName, reason)
                    skippingTask = null
                }
            )
        }

        // Shift Handover Creation Dialog
        if (showHandoverDialog && onAddShiftHandover != null) {
            ShiftHandoverDialog(
                currentUser = currentUser,
                activeUsers = activeUsers.ifEmpty { listOf(currentUser) },
                selectedDate = selectedDate,
                onDismiss = { showHandoverDialog = false },
                onSubmit = { targetShift, category, priority, message, authorUser ->
                    onAddShiftHandover(selectedDate, targetShift, category, priority, message, authorUser)
                    showHandoverDialog = false
                }
            )
        }

        // Admin Task Unlock Dialog
        taskToUnlock?.let { (task, completion, _) ->
            val compTime = remember(completion.completedAt) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(completion.completedAt))
            }
            AdminUnlockDialog(
                taskTitle = task.title,
                completedByName = completion.completedByUserName,
                completedByInitials = completion.completedByUserInitials,
                completedTime = compTime,
                currentUser = currentUser,
                adminUsers = adminUsers,
                onDismiss = { taskToUnlock = null },
                onConfirmUnlock = { adminUser ->
                    onUnlockTask?.invoke(task, completion, adminUser)
                    taskToUnlock = null
                }
            )
        }
    }
}

@Composable
fun CategoryTaskCard(
    categoryName: String,
    completedCount: Int,
    totalCount: Int,
    tasks: List<TaskEntity>,
    completionMap: Map<Long, TaskCompletionEntity>,
    notesMap: Map<Long, List<TaskNoteEntity>>,
    photosMap: Map<Long, List<TaskPhotoEntity>>,
    outstandingDaysMap: Map<Long, Int>,
    selectedTaskIds: Set<Long> = emptySet(),
    onSelectTask: (TaskEntity) -> Unit = {},
    onSubmitSignOff: (TaskEntity) -> Unit = {},
    onSubmitSubTasksSignOff: (TaskEntity, String, List<String>, List<String>, Boolean) -> Unit = { _, _, _, _, _ -> },
    onOpenNotes: (TaskEntity) -> Unit,
    onOpenPhotos: (TaskEntity) -> Unit,
    onEditTask: ((TaskEntity) -> Unit)? = null,
    onSkipTask: ((TaskEntity) -> Unit)? = null,
    onUnlockTask: ((TaskEntity, TaskCompletionEntity) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, RetailHeaderBlack, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(RetailSurface)
    ) {
        // Category Black Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RetailHeaderBlack)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = categoryName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.sp
            )
            Surface(
                color = if (completedCount == totalCount && totalCount > 0) RetailGreenDone else Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "$completedCount/$totalCount",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        // Tasks in Category
        Column(modifier = Modifier.fillMaxWidth()) {
            tasks.forEachIndexed { index, task ->
                val completion = completionMap[task.id]
                val isDone = completion != null && !completion.isPartial
                val isSelected = task.id in selectedTaskIds
                val taskNotes = notesMap[task.id] ?: emptyList()
                val taskPhotos = photosMap[task.id] ?: emptyList()
                val outstandingDays = outstandingDaysMap[task.id] ?: 0

                TaskItemCard(
                    task = task,
                    categoryName = categoryName,
                    showCategoryTag = false,
                    isDone = isDone,
                    isSelected = isSelected,
                    completion = completion,
                    outstandingDays = outstandingDays,
                    notes = taskNotes,
                    photos = taskPhotos,
                    onSelect = { onSelectTask(task) },
                    onSubmitSignOff = { onSubmitSignOff(task) },
                    onSubmitSubTasks = { compList, outList, isPartial ->
                        onSubmitSubTasksSignOff(task, categoryName, compList, outList, isPartial)
                    },
                    onOpenNotes = { onOpenNotes(task) },
                    onOpenPhotos = { onOpenPhotos(task) },
                    onEditTask = if (onEditTask != null) { { onEditTask(task) } } else null,
                    onSkipTask = if (onSkipTask != null) { { onSkipTask(task) } } else null,
                    onUnlockTask = if (onUnlockTask != null && completion != null) { { onUnlockTask(task, completion) } } else null
                )

                if (index < tasks.lastIndex) {
                    HorizontalDivider(color = RetailBorder, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
fun PriorityTierCard(
    title: String,
    bannerColor: Color,
    completedCount: Int,
    totalCount: Int,
    tasks: List<TaskEntity>,
    categoryMap: Map<Long, CategoryEntity>,
    completionMap: Map<Long, TaskCompletionEntity>,
    notesMap: Map<Long, List<TaskNoteEntity>>,
    photosMap: Map<Long, List<TaskPhotoEntity>>,
    outstandingDaysMap: Map<Long, Int>,
    selectedTaskIds: Set<Long> = emptySet(),
    onSelectTask: (TaskEntity) -> Unit = {},
    onSubmitSignOff: (TaskEntity, String) -> Unit = { _, _ -> },
    onSubmitSubTasksSignOff: (TaskEntity, String, List<String>, List<String>, Boolean) -> Unit = { _, _, _, _, _ -> },
    onOpenNotes: (TaskEntity) -> Unit,
    onOpenPhotos: (TaskEntity) -> Unit,
    onEditTask: ((TaskEntity) -> Unit)? = null,
    onSkipTask: ((TaskEntity, String) -> Unit)? = null,
    onUnlockTask: ((TaskEntity, TaskCompletionEntity) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, bannerColor, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(RetailSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bannerColor)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.sp
            )
            Surface(
                color = Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "$completedCount/$totalCount",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            tasks.forEachIndexed { index, task ->
                val completion = completionMap[task.id]
                val isDone = completion != null && !completion.isPartial
                val isSelected = task.id in selectedTaskIds
                val taskNotes = notesMap[task.id] ?: emptyList()
                val taskPhotos = photosMap[task.id] ?: emptyList()
                val outstandingDays = outstandingDaysMap[task.id] ?: 0
                val catName = categoryMap[task.categoryId]?.name ?: "TASK"

                TaskItemCard(
                    task = task,
                    categoryName = catName,
                    showCategoryTag = true,
                    isDone = isDone,
                    isSelected = isSelected,
                    completion = completion,
                    outstandingDays = outstandingDays,
                    notes = taskNotes,
                    photos = taskPhotos,
                    onSelect = { onSelectTask(task) },
                    onSubmitSignOff = { onSubmitSignOff(task, catName) },
                    onSubmitSubTasks = { compList, outList, isPartial ->
                        onSubmitSubTasksSignOff(task, catName, compList, outList, isPartial)
                    },
                    onOpenNotes = { onOpenNotes(task) },
                    onOpenPhotos = { onOpenPhotos(task) },
                    onEditTask = if (onEditTask != null) { { onEditTask(task) } } else null,
                    onSkipTask = if (onSkipTask != null) { { onSkipTask(task, catName) } } else null,
                    onUnlockTask = if (onUnlockTask != null && completion != null) { { onUnlockTask(task, completion) } } else null
                )

                if (index < tasks.lastIndex) {
                    HorizontalDivider(color = RetailBorder, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
fun TaskItemCard(
    task: TaskEntity,
    categoryName: String = "",
    showCategoryTag: Boolean = false,
    isDone: Boolean,
    isSelected: Boolean = false,
    completion: TaskCompletionEntity?,
    outstandingDays: Int,
    notes: List<TaskNoteEntity>,
    photos: List<TaskPhotoEntity>,
    onSelect: () -> Unit = {},
    onSubmitSignOff: () -> Unit = {},
    onSubmitSubTasks: (completed: List<String>, outstanding: List<String>, isPartial: Boolean) -> Unit = { _, _, _ -> },
    onOpenNotes: () -> Unit,
    onOpenPhotos: () -> Unit,
    onEditTask: (() -> Unit)? = null,
    onSkipTask: (() -> Unit)? = null,
    onUnlockTask: (() -> Unit)? = null
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val allSubTasks = remember(task.bulletPoints) { SubTaskUtils.parseSubTasks(task.bulletPoints) }
    val subTaskChunks = remember(allSubTasks) { allSubTasks.chunked(2) }
    val isPartialDone = completion != null && completion.isPartial
    val isSkipped = completion != null && completion.isSkipped
    val isLocked = isDone || isSkipped

    val previouslyCompletedSubTasks = remember(completion?.completedSubTasksJson) {
        if (completion != null) SubTaskUtils.jsonToList(completion.completedSubTasksJson).toSet() else emptySet()
    }
    var tickedSubTasks by remember(task.id, completion?.completedSubTasksJson) {
        mutableStateOf(previouslyCompletedSubTasks)
    }

    val clickableModifier = remember(isLocked) {
        if (!isLocked) {
            Modifier.clickable(onClick = onSelect)
        } else {
            Modifier
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(14.dp)
            .testTag("task_row_${task.id}")
    ) {
        // Line 1: Checkbox on left, Priority badge, Overdue badge, Category, and Task Title on far right (IMG_3681)
        val (priorityBg, priorityFg, priorityText) = when (task.priority.uppercase()) {
            TaskPriority.HIGH -> Triple(Color(0xFFFEE2E2), Color(0xFFB91C1C), "HIGH")
            TaskPriority.LOW -> Triple(Color(0xFFF1F5F9), Color(0xFF475569), "LOW")
            else -> Triple(Color(0xFFFFEDD5), Color(0xFFC2410C), "MED")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox Square (size 22.dp)
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(
                        width = 2.dp,
                        color = when {
                            isSkipped -> Color(0xFFE65100)
                            isDone -> RetailGreenDone
                            isPartialDone -> RetailOrange
                            isSelected -> RetailOrange
                            else -> Color(0xFF1E293B)
                        },
                        shape = RoundedCornerShape(4.dp)
                    )
                    .background(
                        color = when {
                            isSkipped -> Color(0xFFFFF3E0)
                            isDone -> RetailGreenDone
                            isPartialDone -> RetailOrange.copy(alpha = 0.2f)
                            isSelected -> RetailOrange.copy(alpha = 0.2f)
                            else -> Color.White
                        },
                        shape = RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSkipped) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Task Skipped",
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(14.dp)
                    )
                } else if (isDone) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed and Locked",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (isPartialDone || isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = RetailOrange,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Badges: Priority + Overdue/One-Time + Category
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // • HIGH / • MED / • LOW
                Box(
                    modifier = Modifier
                        .background(priorityBg, RoundedCornerShape(12.dp))
                        .border(1.dp, priorityFg.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "• $priorityText",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = priorityFg,
                        letterSpacing = 0.5.sp
                    )
                }

                // 2d Overdue
                if (outstandingDays > 0) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFEDD5), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFFDBA74), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${outstandingDays}d Overdue",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC2410C)
                        )
                    }
                } else if (task.isEffectivelyOneTime) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFF3E8FF), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFD8B4FE), RoundedCornerShape(12.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "★ One-Time",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6B21A8)
                        )
                    }
                }

                if (showCategoryTag && categoryName.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .background(RetailHeaderBlack, RoundedCornerShape(12.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = categoryName,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Task Title on the far right (matching Mockup IMG_3681)
            Text(
                text = task.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = if (isLocked) RetailTextSecondary else Color(0xFF0F172A),
                letterSpacing = 0.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Continued Rollover Banner if outstanding
        if (!isLocked && outstandingDays > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFEDD5), RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFFFDBA74), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚡ CONTINUED • $outstandingDays ${if (outstandingDays == 1) "DAY" else "DAYS"} OUTSTANDING",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFC2410C),
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Sub-Tasks Interactive 2-Column Grid (matching Mockup IMG_3681)
        if (allSubTasks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "SUB-TASKS (${tickedSubTasks.size}/${allSubTasks.size} COMPLETED):",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = if (isDone) RetailGreenCheck else Color(0xFF475569),
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Render in 2-column grid rows
            subTaskChunks.forEach { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { subTask ->
                        val isTicked = subTask in tickedSubTasks
                        val isPreviouslyDone = subTask in previouslyCompletedSubTasks

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        isDone || isPreviouslyDone -> Color(0xFFECFDF5)
                                        isTicked -> Color(0xFFFFF7ED)
                                        else -> Color(0xFFF8FAFC)
                                    }
                                )
                                .border(
                                    1.dp,
                                    when {
                                        isDone || isPreviouslyDone -> Color(0xFFA7F3D0)
                                        isTicked -> Color(0xFFFED7AA)
                                        else -> Color(0xFFE2E8F0)
                                    },
                                    RoundedCornerShape(6.dp)
                                )
                                .then(
                                    if (!isLocked) {
                                        Modifier.clickable {
                                            tickedSubTasks = if (isTicked) {
                                                tickedSubTasks - subTask
                                            } else {
                                                tickedSubTasks + subTask
                                            }
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .border(
                                        width = 1.5.dp,
                                        color = when {
                                            isDone || isPreviouslyDone -> RetailGreenDone
                                            isTicked -> RetailOrange
                                            else -> Color(0xFF94A3B8)
                                        },
                                        shape = RoundedCornerShape(3.dp)
                                    )
                                    .background(
                                        color = when {
                                            isDone || isPreviouslyDone -> RetailGreenDone
                                            isTicked -> RetailOrange
                                            else -> Color.White
                                        },
                                        shape = RoundedCornerShape(3.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isDone || isPreviouslyDone || isTicked) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = subTask,
                                fontSize = 11.sp,
                                fontWeight = if (isTicked || isDone) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isDone || isPreviouslyDone -> Color(0xFF1B5E20)
                                    isTicked -> RetailOrange
                                    else -> RetailTextPrimary
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            if (isPreviouslyDone) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFC8E6C9), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("VERIFIED", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                }
                            }
                        }
                    }

                    // Balance the row if odd number of subtasks
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // Action buttons for sub-task submission when not fully locked
            if (!isLocked) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val allTicked = tickedSubTasks.size == allSubTasks.size
                            val someTicked = tickedSubTasks.isNotEmpty() && !allTicked

                            if (allTicked) {
                                Button(
                                    onClick = {
                                        onSubmitSubTasks(
                                            allSubTasks,
                                            emptyList(),
                                            false
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("submit_full_subtasks_${task.id}"),
                                    colors = ButtonDefaults.buttonColors(containerColor = RetailGreenDone),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("SUBMIT FULL COMPLETION (${allSubTasks.size}/${allSubTasks.size})", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                }
                            } else if (someTicked) {
                                Button(
                                    onClick = {
                                        val completedList = allSubTasks.filter { it in tickedSubTasks }
                                        val outstandingList = allSubTasks.filter { it !in tickedSubTasks }
                                        onSubmitSubTasks(
                                            completedList,
                                            outstandingList,
                                            true
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .testTag("submit_partial_subtasks_${task.id}"),
                                    colors = ButtonDefaults.buttonColors(containerColor = RetailOrange),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("SUBMIT PARTIAL (${tickedSubTasks.size}/${allSubTasks.size} DONE)", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                }

                                OutlinedButton(
                                    onClick = { tickedSubTasks = allSubTasks.toSet() },
                                    modifier = Modifier.height(38.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, RetailBorder)
                                ) {
                                    Text("TICK ALL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RetailTextPrimary)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { tickedSubTasks = allSubTasks.toSet() },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, RetailBorder)
                                ) {
                                    Text("TICK ALL SUB-TASKS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RetailTextPrimary)
                                }
                            }

                            if (onSkipTask != null) {
                                OutlinedButton(
                                    onClick = onSkipTask,
                                    modifier = Modifier
                                        .height(38.dp)
                                        .testTag("skip_subtasks_task_${task.id}"),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE65100)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFE65100))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("SKIP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else if (task.bulletPoints.isNotBlank()) {
                val lines = task.bulletPoints.lines().filter { it.isNotBlank() }
                Column(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                    lines.forEach { line ->
                        val formattedLine = if (line.trim().startsWith("•")) line.trim() else "• ${line.trim()}"
                        Text(
                            text = formattedLine,
                            fontSize = 12.sp,
                            color = RetailTextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Completion Banner (Skipped vs Partial vs Full)
            if (completion != null) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (completion.isSkipped) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFFF3E0), RoundedCornerShape(2.dp))
                                .border(1.dp, Color(0xFFFFB74D), RoundedCornerShape(2.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "⚡ SKIPPED by [${completion.completedByUserInitials}] ${completion.completedByUserName} (ID #${completion.completedByUserId}) at ${timeFormat.format(Date(completion.completedAt))} • Reason: \"${completion.skipReason.ifBlank { "Excused" }}\"",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                        }
                    } else if (completion.isPartial) {
                        Box(
                            modifier = Modifier
                                .background(RetailOrange.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                                .border(1.dp, RetailOrange.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "⚡ PARTIALLY SIGNED OFF (${completion.completedSubTasksCount}/${completion.totalSubTasksCount}) by [${completion.completedByUserInitials}] ${completion.completedByUserName} (ID #${completion.completedByUserId}) at ${timeFormat.format(Date(completion.completedAt))}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RetailOrange
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .background(RetailGreenCheck.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "✓ Signed off by [${completion.completedByUserInitials}] ${completion.completedByUserName} (Employee ID #${completion.completedByUserId}) at ${timeFormat.format(Date(completion.completedAt))} • COMPLIANCE LOCKED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RetailGreenCheck
                            )
                        }
                    }
                }
            }

            // Dedicated SUBMIT SIGN OFF button for single-item tasks when selected
            if (isSelected && !isLocked && allSubTasks.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSubmitSignOff,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("submit_task_button_${task.id}"),
                        colors = ButtonDefaults.buttonColors(containerColor = RetailOrange),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SUBMIT SIGN OFF",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                    }

                    if (onSkipTask != null) {
                        OutlinedButton(
                            onClick = onSkipTask,
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("skip_task_direct_btn_${task.id}"),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE65100)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFFE65100)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "SKIP",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onSelect,
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("cancel_select_task_${task.id}"),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, RetailBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RetailTextSecondary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "CANCEL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Notes Bubble matching Image 3: [MG] TEST UI NOTE 15:13
            if (notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                notes.forEach { note ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RetailSurfaceSubtle, RoundedCornerShape(4.dp))
                            .border(1.dp, RetailBorder, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(RetailHeaderBlack, RoundedCornerShape(2.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = note.authorInitials,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = note.text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = RetailTextPrimary
                            )
                        }
                        Text(
                            text = timeFormat.format(Date(note.timestamp)),
                            fontSize = 11.sp,
                            color = RetailTextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons row: [NOTE] [PHOTO] [SKIP] [EDIT]
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // [NOTE] button
                Surface(
                    onClick = onOpenNotes,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, RetailBorder),
                    color = RetailSurfaceSubtle
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.EditNote,
                            contentDescription = "Note",
                            modifier = Modifier.size(16.dp),
                            tint = RetailTextPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (notes.isNotEmpty()) "NOTE (${notes.size})" else "NOTE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RetailTextPrimary
                        )
                    }
                }

                // [PHOTO] button
                Surface(
                    onClick = onOpenPhotos,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, RetailBorder),
                    color = RetailSurfaceSubtle
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Photo",
                            modifier = Modifier.size(16.dp),
                            tint = RetailTextPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (photos.isNotEmpty()) "PHOTO (${photos.size})" else "PHOTO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RetailTextPrimary
                        )
                    }
                }

                if (!isLocked && onSkipTask != null) {
                    Surface(
                        onClick = onSkipTask,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                        color = Color(0xFFFFF8E1),
                        modifier = Modifier.testTag("skip_task_btn_${task.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Skip Task",
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFFE65100)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "SKIP",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                }

                if (onEditTask != null) {
                    Surface(
                        onClick = onEditTask,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, RetailBorder),
                        color = RetailSurfaceSubtle,
                        modifier = Modifier.testTag("edit_task_btn_${task.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Task",
                                modifier = Modifier.size(16.dp),
                                tint = RetailOrange
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "EDIT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RetailTextPrimary
                            )
                        }
                    }
                }

                if (onUnlockTask != null && (isDone || isSkipped || isPartialDone)) {
                    Surface(
                        onClick = onUnlockTask,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFFD32F2F)),
                        color = Color(0xFFFFEBEE),
                        modifier = Modifier.testTag("unlock_task_btn_${task.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Unlock Task",
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFFD32F2F)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "UNLOCK",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD32F2F)
                            )
                        }
                    }
                }
            }
        }
    }


@Composable
fun SkipTaskDialog(
    task: TaskEntity,
    categoryName: String,
    onDismiss: () -> Unit,
    onConfirmSkip: (reason: String) -> Unit
) {
    var reasonText by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf<String?>(null) }
    var showError by remember { mutableStateOf(false) }

    val presetReasons = listOf(
        "Not needed",
        "Crisps Worked Instead",
        "Grocery Worked Instead",
        "Frozen Worked Instead",
        "Not Enough Time",
        "N.A"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFFFFF3E0), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "SKIP TASK",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = RetailTextPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = categoryName.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = RetailTextSecondary
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = RetailSurfaceSubtle,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, RetailBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = task.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = RetailTextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "SELECT REASON FOR SKIPPING:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = RetailTextPrimary,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick preset chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presetReasons.chunked(2).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowPresets.forEach { preset ->
                                val isSelected = selectedPreset == preset
                                Surface(
                                    onClick = {
                                        selectedPreset = if (isSelected) null else preset
                                        reasonText = if (isSelected) "" else preset
                                        showError = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFFE65100) else RetailBorder
                                    ),
                                    color = if (isSelected) Color(0xFFFFF3E0) else RetailSurface
                                ) {
                                    Text(
                                        text = preset,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color(0xFFE65100) else RetailTextPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "OR ENTER SPECIFIC EXPLANATION / AUDIT REASON:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = RetailTextSecondary
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = reasonText,
                    onValueChange = {
                        reasonText = it
                        showError = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("skip_reason_input"),
                    placeholder = {
                        Text(
                            "e.g. Broken refrigeration unit awaiting parts or delivery rescheduled",
                            fontSize = 12.sp,
                            color = RetailTextSecondary
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    isError = showError
                )

                if (showError) {
                    Text(
                        text = "Please select or enter a reason to skip this task.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalReason = reasonText.trim().ifBlank { selectedPreset?.trim().orEmpty() }
                    if (finalReason.isBlank()) {
                        showError = true
                    } else {
                        onConfirmSkip(finalReason)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.testTag("confirm_skip_task_btn")
            ) {
                Text(
                    text = "CONFIRM SKIP",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, RetailBorder),
                modifier = Modifier.testTag("cancel_skip_task_btn")
            ) {
                Text(
                    text = "CANCEL",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = RetailTextPrimary
                )
            }
        }
    )
}

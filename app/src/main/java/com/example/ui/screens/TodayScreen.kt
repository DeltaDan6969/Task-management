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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
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
import com.example.ui.components.NoteDialog
import com.example.ui.components.PhotoDialog
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
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var activeNoteDialogTask by remember { mutableStateOf<TaskEntity?>(null) }
    var activePhotoDialogTask by remember { mutableStateOf<TaskEntity?>(null) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var skippingTask by remember { mutableStateOf<Pair<TaskEntity, String>?>(null) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showAllDaysWeekly by remember { mutableStateOf(false) }
    var currentSortMode by remember { mutableStateOf(TaskSortMode.CATEGORY) }
    var currentPriorityFilter by remember { mutableStateOf(TaskPriorityFilter.ALL) }
    var selectedTaskIds by remember { mutableStateOf(setOf<Long>()) }
    var searchQuery by remember { mutableStateOf("") }

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
    val completedCount = completions.count { it.taskId in scheduledTaskIds }
    val progressFraction = if (totalTasks > 0) completedCount.toFloat() / totalTasks else 0f
    val progressPercent = (progressFraction * 100).toInt()

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
                // Tablet Dashboard Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Date & Nav Buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = dayOfWeekName,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = RetailTextPrimary,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                IconButton(
                                    onClick = onPreviousDate,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .border(1.dp, RetailBorder, RoundedCornerShape(4.dp))
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Previous Day",
                                        tint = RetailTextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = onNextDate,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .border(1.dp, RetailBorder, RoundedCornerShape(4.dp))
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Next Day",
                                        tint = RetailTextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    onClick = onResetToToday,
                                    color = RetailHeaderBlack,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "TODAY",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { showAddTaskDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = RetailOrange),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "+ EXTRA TASK (${DaysOfWeek.SHORT_NAMES[dayOfWeekName] ?: dayOfWeekName.take(3)})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = selectedDate,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RetailTextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    // Progress Overview Card
                    Surface(
                        color = RetailSurface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, RetailBorder),
                        modifier = Modifier.widthIn(min = 280.dp, max = 340.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$completedCount / $totalTasks SIGNED OFF",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RetailTextPrimary
                                )
                                Text(
                                    text = "$progressPercent%",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = RetailOrange
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progressFraction.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = RetailOrange,
                                trackColor = RetailBorder
                            )
                        }
                    }

                    // Overdue Rollover Alert & User Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (overdueCount > 0) {
                            Surface(
                                color = RetailRedMissed.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, RetailRedMissed.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = RetailRedMissed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$overdueCount OVERDUE ROLLOVER",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = RetailRedMissed,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        } else {
                            Surface(
                                color = RetailGreenDone.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, RetailGreenDone.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "✓ ALL UP TO DATE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RetailGreenDone,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }

                        // User Initials Badge
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(RetailOrange, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentUser.initials,
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp
                            )
                        }

                        IconButton(
                            onClick = onSignOut,
                            modifier = Modifier
                                .size(38.dp)
                                .border(1.dp, RetailBorder, RoundedCornerShape(6.dp))
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

            Spacer(modifier = Modifier.height(14.dp))

            // Sort & Filter Toolbar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = RetailSurfaceSubtle,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, RetailBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Sort options
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "SORT BY:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = RetailTextSecondary,
                            letterSpacing = 0.5.sp
                        )

                        TaskSortMode.values().forEach { mode ->
                            val isSelected = currentSortMode == mode
                            Surface(
                                onClick = { currentSortMode = mode },
                                shape = RoundedCornerShape(4.dp),
                                color = if (isSelected) RetailHeaderBlack else RetailSurface,
                                border = BorderStroke(1.dp, if (isSelected) RetailHeaderBlack else RetailBorder),
                                modifier = Modifier.testTag("sort_mode_${mode.name}")
                            ) {
                                Text(
                                    text = mode.label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else RetailTextPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(RetailBorder)
                    )

                    // Filter options
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "FILTER PRIORITY:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = RetailTextSecondary,
                            letterSpacing = 0.5.sp
                        )

                        TaskPriorityFilter.values().forEach { filter ->
                            val isSelected = currentPriorityFilter == filter
                            val activeColor = when (filter) {
                                TaskPriorityFilter.HIGH -> RetailRedMissed
                                TaskPriorityFilter.LOW -> RetailHeaderBlackLight
                                TaskPriorityFilter.OUTSTANDING_ONLY -> RetailRedMissed
                                else -> RetailOrange
                            }
                            Surface(
                                onClick = { currentPriorityFilter = filter },
                                shape = RoundedCornerShape(4.dp),
                                color = if (isSelected) activeColor else RetailSurface,
                                border = BorderStroke(1.dp, if (isSelected) activeColor else RetailBorder),
                                modifier = Modifier.testTag("filter_priority_${filter.name}")
                            ) {
                                Text(
                                    text = filter.label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else RetailTextPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(RetailBorder)
                    )

                    // Day of Week Schedule View Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "SCHEDULE:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = RetailTextSecondary,
                            letterSpacing = 0.5.sp
                        )

                        val dayShort = DaysOfWeek.SHORT_NAMES[dayOfWeekName] ?: dayOfWeekName.take(3)
                        val dayTasksCount = tasks.count { DaysOfWeek.runsOnDay(it.daysOfWeek, dayOfWeekName) }

                        Surface(
                            onClick = { showAllDaysWeekly = false },
                            shape = RoundedCornerShape(4.dp),
                            color = if (!showAllDaysWeekly) RetailHeaderBlack else RetailSurface,
                            border = BorderStroke(1.dp, if (!showAllDaysWeekly) RetailHeaderBlack else RetailBorder)
                        ) {
                            Text(
                                text = "$dayShort ONLY ($dayTasksCount)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!showAllDaysWeekly) Color.White else RetailTextPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            onClick = { showAllDaysWeekly = true },
                            shape = RoundedCornerShape(4.dp),
                            color = if (showAllDaysWeekly) RetailHeaderBlack else RetailSurface,
                            border = BorderStroke(1.dp, if (showAllDaysWeekly) RetailHeaderBlack else RetailBorder)
                        ) {
                            Text(
                                text = "ALL WEEK (${tasks.size})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (showAllDaysWeekly) Color.White else RetailTextPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search input field above the task list that filters tasks by title in real-time
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_search_input"),
                placeholder = {
                    Text(
                        text = "Search tasks by title...",
                        fontSize = 13.sp,
                        color = RetailTextSecondary
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search tasks",
                        tint = if (searchQuery.isNotEmpty()) RetailOrange else RetailTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.testTag("clear_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = RetailTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = RetailSurface,
                    unfocusedContainerColor = RetailSurface,
                    focusedBorderColor = RetailOrange,
                    unfocusedBorderColor = RetailBorder,
                    cursorColor = RetailOrange,
                    focusedTextColor = RetailTextPrimary,
                    unfocusedTextColor = RetailTextPrimary
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
            )

            if (searchQuery.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
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
                        val activeCategories = categories.filter { category ->
                            filteredTasks.any { it.categoryId == category.id }
                        }

                        if (isTablet) {
                            // Tablet 2-Column Responsive Grid
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 400.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(activeCategories) { category ->
                                    val categoryTasks = filteredTasks.filter { it.categoryId == category.id }
                                    val completedInCat = categoryTasks.count { task ->
                                        completions.any { it.taskId == task.id && (!it.isPartial || it.isSkipped) }
                                    }

                                    CategoryTaskCard(
                                        categoryName = category.name,
                                        completedCount = completedInCat,
                                        totalCount = categoryTasks.size,
                                        tasks = categoryTasks,
                                        completions = completions,
                                        notes = notes,
                                        photos = photos,
                                        outstandingDaysMap = outstandingDaysMap,
                                        selectedTaskIds = selectedTaskIds,
                                        onSelectTask = onToggleSelectTask,
                                        onSubmitSignOff = { task -> onSubmitSingleTask(task, category.name) },
                                        onSubmitSubTasksSignOff = onSubmitSubTasksHandler,
                                        onOpenNotes = { task -> activeNoteDialogTask = task },
                                        onOpenPhotos = { task -> activePhotoDialogTask = task },
                                        onEditTask = onEditTaskHandler,
                                        onSkipTask = { task -> onOpenSkipHandler(task, category.name) }
                                    )
                                }
                            }
                        } else {
                            // Phone Single Column
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(activeCategories) { category ->
                                    val categoryTasks = filteredTasks.filter { it.categoryId == category.id }
                                    val completedInCat = categoryTasks.count { task ->
                                        completions.any { it.taskId == task.id && (!it.isPartial || it.isSkipped) }
                                    }

                                    CategoryTaskCard(
                                        categoryName = category.name,
                                        completedCount = completedInCat,
                                        totalCount = categoryTasks.size,
                                        tasks = categoryTasks,
                                        completions = completions,
                                        notes = notes,
                                        photos = photos,
                                        outstandingDaysMap = outstandingDaysMap,
                                        selectedTaskIds = selectedTaskIds,
                                        onSelectTask = onToggleSelectTask,
                                        onSubmitSignOff = { task -> onSubmitSingleTask(task, category.name) },
                                        onSubmitSubTasksSignOff = onSubmitSubTasksHandler,
                                        onOpenNotes = { task -> activeNoteDialogTask = task },
                                        onOpenPhotos = { task -> activePhotoDialogTask = task },
                                        onEditTask = onEditTaskHandler,
                                        onSkipTask = { task -> onOpenSkipHandler(task, category.name) }
                                    )
                                }
                            }
                        }
                    }

                    TaskSortMode.PRIORITY_DESC, TaskSortMode.PRIORITY_ASC -> {
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

                        val activeTiers = priorityTiers.filter { (tierPriority, _, _) ->
                            filteredTasks.any { it.priority.equals(tierPriority, ignoreCase = true) }
                        }

                        if (isTablet) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 400.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(activeTiers) { (tierPriority, tierTitle, tierColor) ->
                                    val tierTasks = filteredTasks.filter { it.priority.equals(tierPriority, ignoreCase = true) }
                                    val completedInTier = tierTasks.count { task ->
                                        completions.any { it.taskId == task.id && (!it.isPartial || it.isSkipped) }
                                    }

                                    PriorityTierCard(
                                        title = tierTitle,
                                        bannerColor = tierColor,
                                        completedCount = completedInTier,
                                        totalCount = tierTasks.size,
                                        tasks = tierTasks,
                                        categoryMap = categoryMap,
                                        completions = completions,
                                        notes = notes,
                                        photos = photos,
                                        outstandingDaysMap = outstandingDaysMap,
                                        selectedTaskIds = selectedTaskIds,
                                        onSelectTask = onToggleSelectTask,
                                        onSubmitSignOff = onSubmitSingleTask,
                                        onSubmitSubTasksSignOff = onSubmitSubTasksHandler,
                                        onOpenNotes = { task -> activeNoteDialogTask = task },
                                        onOpenPhotos = { task -> activePhotoDialogTask = task },
                                        onEditTask = onEditTaskHandler,
                                        onSkipTask = onOpenSkipHandler
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
                                items(activeTiers) { (tierPriority, tierTitle, tierColor) ->
                                    val tierTasks = filteredTasks.filter { it.priority.equals(tierPriority, ignoreCase = true) }
                                    val completedInTier = tierTasks.count { task ->
                                        completions.any { it.taskId == task.id && (!it.isPartial || it.isSkipped) }
                                    }

                                    PriorityTierCard(
                                        title = tierTitle,
                                        bannerColor = tierColor,
                                        completedCount = completedInTier,
                                        totalCount = tierTasks.size,
                                        tasks = tierTasks,
                                        categoryMap = categoryMap,
                                        completions = completions,
                                        notes = notes,
                                        photos = photos,
                                        outstandingDaysMap = outstandingDaysMap,
                                        selectedTaskIds = selectedTaskIds,
                                        onSelectTask = onToggleSelectTask,
                                        onSubmitSignOff = onSubmitSingleTask,
                                        onSubmitSubTasksSignOff = onSubmitSubTasksHandler,
                                        onOpenNotes = { task -> activeNoteDialogTask = task },
                                        onOpenPhotos = { task -> activePhotoDialogTask = task },
                                        onEditTask = onEditTaskHandler,
                                        onSkipTask = onOpenSkipHandler
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

                        val overdueTasks = sortedTasks.filter { (outstandingDaysMap[it.id] ?: 0) > 0 }
                        val todayTasks = sortedTasks.filter { (outstandingDaysMap[it.id] ?: 0) == 0 }

                        if (isTablet) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 400.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (overdueTasks.isNotEmpty()) {
                                    item {
                                        PriorityTierCard(
                                            title = "⚡ OVERDUE & CONTINUED TASKS",
                                            bannerColor = RetailRedMissed,
                                            completedCount = overdueTasks.count { t -> completions.any { it.taskId == t.id && (!it.isPartial || it.isSkipped) } },
                                            totalCount = overdueTasks.size,
                                            tasks = overdueTasks,
                                            categoryMap = categoryMap,
                                            completions = completions,
                                            notes = notes,
                                            photos = photos,
                                            outstandingDaysMap = outstandingDaysMap,
                                            selectedTaskIds = selectedTaskIds,
                                            onSelectTask = onToggleSelectTask,
                                            onSubmitSignOff = onSubmitSingleTask,
                                            onSubmitSubTasksSignOff = onSubmitSubTasksHandler,
                                            onOpenNotes = { task -> activeNoteDialogTask = task },
                                            onOpenPhotos = { task -> activePhotoDialogTask = task },
                                            onEditTask = onEditTaskHandler,
                                            onSkipTask = onOpenSkipHandler
                                        )
                                    }
                                }

                                if (todayTasks.isNotEmpty()) {
                                    item {
                                        PriorityTierCard(
                                            title = "SCHEDULED TODAY",
                                            bannerColor = RetailHeaderBlack,
                                            completedCount = todayTasks.count { t -> completions.any { it.taskId == t.id && (!it.isPartial || it.isSkipped) } },
                                            totalCount = todayTasks.size,
                                            tasks = todayTasks,
                                            categoryMap = categoryMap,
                                            completions = completions,
                                            notes = notes,
                                            photos = photos,
                                            outstandingDaysMap = outstandingDaysMap,
                                            selectedTaskIds = selectedTaskIds,
                                            onSelectTask = onToggleSelectTask,
                                            onSubmitSignOff = onSubmitSingleTask,
                                            onSubmitSubTasksSignOff = onSubmitSubTasksHandler,
                                            onOpenNotes = { task -> activeNoteDialogTask = task },
                                            onOpenPhotos = { task -> activePhotoDialogTask = task },
                                            onEditTask = onEditTaskHandler,
                                            onSkipTask = onOpenSkipHandler
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
                                    item {
                                        PriorityTierCard(
                                            title = "⚡ OVERDUE & CONTINUED TASKS",
                                            bannerColor = RetailRedMissed,
                                            completedCount = overdueTasks.count { t -> completions.any { it.taskId == t.id && (!it.isPartial || it.isSkipped) } },
                                            totalCount = overdueTasks.size,
                                            tasks = overdueTasks,
                                            categoryMap = categoryMap,
                                            completions = completions,
                                            notes = notes,
                                            photos = photos,
                                            outstandingDaysMap = outstandingDaysMap,
                                            selectedTaskIds = selectedTaskIds,
                                            onSelectTask = onToggleSelectTask,
                                            onSubmitSignOff = onSubmitSingleTask,
                                            onSubmitSubTasksSignOff = onSubmitSubTasksHandler,
                                            onOpenNotes = { task -> activeNoteDialogTask = task },
                                            onOpenPhotos = { task -> activePhotoDialogTask = task },
                                            onEditTask = onEditTaskHandler,
                                            onSkipTask = onOpenSkipHandler
                                        )
                                    }
                                }

                                if (todayTasks.isNotEmpty()) {
                                    item {
                                        PriorityTierCard(
                                            title = "SCHEDULED TODAY",
                                            bannerColor = RetailHeaderBlack,
                                            completedCount = todayTasks.count { t -> completions.any { it.taskId == t.id && (!it.isPartial || it.isSkipped) } },
                                            totalCount = todayTasks.size,
                                            tasks = todayTasks,
                                            categoryMap = categoryMap,
                                            completions = completions,
                                            notes = notes,
                                            photos = photos,
                                            outstandingDaysMap = outstandingDaysMap,
                                            selectedTaskIds = selectedTaskIds,
                                            onSelectTask = onToggleSelectTask,
                                            onSubmitSignOff = onSubmitSingleTask,
                                            onSubmitSubTasksSignOff = onSubmitSubTasksHandler,
                                            onOpenNotes = { task -> activeNoteDialogTask = task },
                                            onOpenPhotos = { task -> activePhotoDialogTask = task },
                                            onEditTask = onEditTaskHandler,
                                            onSkipTask = onOpenSkipHandler
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
    }
}

@Composable
fun CategoryTaskCard(
    categoryName: String,
    completedCount: Int,
    totalCount: Int,
    tasks: List<TaskEntity>,
    completions: List<TaskCompletionEntity>,
    notes: List<TaskNoteEntity>,
    photos: List<TaskPhotoEntity>,
    outstandingDaysMap: Map<Long, Int>,
    selectedTaskIds: Set<Long> = emptySet(),
    onSelectTask: (TaskEntity) -> Unit = {},
    onSubmitSignOff: (TaskEntity) -> Unit = {},
    onSubmitSubTasksSignOff: (TaskEntity, String, List<String>, List<String>, Boolean) -> Unit = { _, _, _, _, _ -> },
    onOpenNotes: (TaskEntity) -> Unit,
    onOpenPhotos: (TaskEntity) -> Unit,
    onEditTask: ((TaskEntity) -> Unit)? = null,
    onSkipTask: ((TaskEntity) -> Unit)? = null
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
                val completion = completions.find { it.taskId == task.id }
                val isDone = completion != null && !completion.isPartial
                val isSelected = task.id in selectedTaskIds
                val taskNotes = notes.filter { it.taskId == task.id }
                val taskPhotos = photos.filter { it.taskId == task.id }
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
                    onSkipTask = if (onSkipTask != null) { { onSkipTask(task) } } else null
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
    completions: List<TaskCompletionEntity>,
    notes: List<TaskNoteEntity>,
    photos: List<TaskPhotoEntity>,
    outstandingDaysMap: Map<Long, Int>,
    selectedTaskIds: Set<Long> = emptySet(),
    onSelectTask: (TaskEntity) -> Unit = {},
    onSubmitSignOff: (TaskEntity, String) -> Unit = { _, _ -> },
    onSubmitSubTasksSignOff: (TaskEntity, String, List<String>, List<String>, Boolean) -> Unit = { _, _, _, _, _ -> },
    onOpenNotes: (TaskEntity) -> Unit,
    onOpenPhotos: (TaskEntity) -> Unit,
    onEditTask: ((TaskEntity) -> Unit)? = null,
    onSkipTask: ((TaskEntity, String) -> Unit)? = null
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
                val completion = completions.find { it.taskId == task.id }
                val isDone = completion != null && !completion.isPartial
                val isSelected = task.id in selectedTaskIds
                val taskNotes = notes.filter { it.taskId == task.id }
                val taskPhotos = photos.filter { it.taskId == task.id }
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
                    onSkipTask = if (onSkipTask != null) { { onSkipTask(task, catName) } } else null
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
    onSkipTask: (() -> Unit)? = null
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val allSubTasks = remember(task.bulletPoints) { SubTaskUtils.parseSubTasks(task.bulletPoints) }
    val isPartialDone = completion != null && completion.isPartial
    val isSkipped = completion != null && completion.isSkipped
    val isLocked = isDone || isSkipped

    val previouslyCompletedSubTasks = remember(completion) {
        if (completion != null) SubTaskUtils.jsonToList(completion.completedSubTasksJson).toSet() else emptySet()
    }
    var tickedSubTasks by remember(task.id, completion?.completedSubTasksJson) {
        mutableStateOf(previouslyCompletedSubTasks)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!isLocked) {
                    Modifier.clickable(onClick = onSelect)
                } else {
                    Modifier
                }
            )
            .padding(14.dp)
            .testTag("task_row_${task.id}"),
        verticalAlignment = Alignment.Top
    ) {
        // Large Checkbox Square
        // If skipped: Amber with Warning icon, LOCKED
        // If full done: Green with Checkmark, LOCKED
        // If partial done: Orange with Checkmark (or lightning)
        // If selected: Orange border with Checkmark pending submit
        // If uncompleted: Black border empty square
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(24.dp)
                .border(
                    width = 2.dp,
                    color = when {
                        isSkipped -> Color(0xFFE65100)
                        isDone -> RetailGreenDone
                        isPartialDone -> RetailOrange
                        isSelected -> RetailOrange
                        else -> RetailHeaderBlack
                    },
                    shape = RoundedCornerShape(3.dp)
                )
                .background(
                    color = when {
                        isSkipped -> Color(0xFFFFF3E0)
                        isDone -> RetailGreenDone
                        isPartialDone -> RetailOrange.copy(alpha = 0.2f)
                        isSelected -> RetailOrange.copy(alpha = 0.2f)
                        else -> Color.Transparent
                    },
                    shape = RoundedCornerShape(3.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSkipped) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Task Skipped",
                    tint = Color(0xFFE65100),
                    modifier = Modifier.size(16.dp)
                )
            } else if (isDone) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Completed and Locked",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            } else if (isPartialDone) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Partially Completed",
                    tint = RetailOrange,
                    modifier = Modifier.size(16.dp)
                )
            } else if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected - Ready to Submit",
                    tint = RetailOrange,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Priority badge and category tag
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                // Priority Badge (HIGH, MEDIUM, LOW)
                val (priorityBg, priorityFg, priorityText) = when (task.priority.uppercase()) {
                    TaskPriority.HIGH -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "HIGH")
                    TaskPriority.LOW -> Triple(Color(0xFFECEFF1), Color(0xFF455A64), "LOW")
                    else -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "MED")
                }

                Box(
                    modifier = Modifier
                        .background(priorityBg, RoundedCornerShape(2.dp))
                        .border(1.dp, priorityFg.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "● $priorityText",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = priorityFg,
                        letterSpacing = 0.5.sp
                    )
                }

                if (showCategoryTag && categoryName.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .background(RetailHeaderBlack, RoundedCornerShape(2.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = categoryName,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                if (task.isEffectivelyOneTime) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFF3E8FF), RoundedCornerShape(2.dp))
                            .border(1.dp, Color(0xFFD8B4FE), RoundedCornerShape(2.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (outstandingDays > 0 && !isLocked) "★ ONE-TIME • ROLLED OVER" else "★ ONE-TIME",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6B21A8)
                        )
                    }
                } else {
                    val dayDisplay = DaysOfWeek.formatDisplayDays(task.daysOfWeek)
                    if (dayDisplay != "EVERY DAY") {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE8F5E9), RoundedCornerShape(2.dp))
                                .border(1.dp, Color(0xFF81C784), RoundedCornerShape(2.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "📅 $dayDisplay",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }

            // Task Title
            Text(
                text = task.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isLocked) RetailTextSecondary else RetailTextPrimary,
                letterSpacing = 0.5.sp
            )

            // Outstanding rollover status when not yet completed today
            if (!isLocked) {
                if (outstandingDays > 0) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(RetailRedMissed.copy(alpha = 0.12f), RoundedCornerShape(2.dp))
                                .border(1.dp, RetailRedMissed.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "⚡ CONTINUED • $outstandingDays ${if (outstandingDays == 1) "DAY" else "DAYS"} OUTSTANDING",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = RetailRedMissed,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.padding(top = 3.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(RetailOrange.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "• SCHEDULED TODAY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = RetailOrange
                            )
                        }
                    }
                }
            }

            // Sub-Tasks Interactive Checklist
            if (allSubTasks.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)) {
                    Text(
                        text = "SUB-TASKS (${tickedSubTasks.size}/${allSubTasks.size} COMPLETED):",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isDone) RetailGreenCheck else RetailTextPrimary,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    allSubTasks.forEach { subTask ->
                        val isTicked = subTask in tickedSubTasks
                        val isPreviouslyDone = subTask in previouslyCompletedSubTasks

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        isDone || isPreviouslyDone -> Color(0xFFE8F5E9)
                                        isTicked -> RetailOrange.copy(alpha = 0.1f)
                                        else -> RetailSurfaceSubtle
                                    }
                                )
                                .border(
                                    1.dp,
                                    when {
                                        isDone || isPreviouslyDone -> Color(0xFFA5D6A7)
                                        isTicked -> RetailOrange.copy(alpha = 0.5f)
                                        else -> RetailBorder
                                    },
                                    RoundedCornerShape(4.dp)
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
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .border(
                                        width = 2.dp,
                                        color = when {
                                            isDone || isPreviouslyDone -> RetailGreenDone
                                            isTicked -> RetailOrange
                                            else -> RetailTextSecondary
                                        },
                                        shape = RoundedCornerShape(3.dp)
                                    )
                                    .background(
                                        color = when {
                                            isDone || isPreviouslyDone -> RetailGreenDone
                                            isTicked -> RetailOrange
                                            else -> Color.Transparent
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
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = subTask,
                                fontSize = 12.sp,
                                fontWeight = if (isTicked || isDone) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isDone || isPreviouslyDone -> Color(0xFF1B5E20)
                                    isTicked -> RetailOrange
                                    else -> RetailTextPrimary
                                },
                                modifier = Modifier.weight(1f)
                            )

                            if (isPreviouslyDone) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFC8E6C9), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("VERIFIED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                }
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
        "Out of stock",
        "Equipment issue",
        "Supplier delay",
        "Manager excused",
        "Customer rush",
        "Not applicable today"
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

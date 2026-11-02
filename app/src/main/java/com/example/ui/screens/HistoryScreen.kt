package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
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
import com.example.ui.components.AdminUnlockDialog
import com.example.ui.theme.*
import com.example.util.SubTaskUtils
import java.text.SimpleDateFormat
import java.util.*

enum class HistoryStatusFilter(val label: String) {
    ALL("ALL"),
    OVERDUE("OVERDUE"),
    COMPLETED("COMPLETED"),
    SKIPPED("SKIPPED")
}

sealed class HistoryAuditItem {
    data class Completed(
        val completion: TaskCompletionEntity,
        val task: TaskEntity?
    ) : HistoryAuditItem()

    data class Overdue(
        val task: TaskEntity,
        val categoryName: String
    ) : HistoryAuditItem()
}

@Composable
fun HistoryScreen(
    allCompletions: List<TaskCompletionEntity>,
    tasks: List<TaskEntity> = emptyList(),
    categories: List<CategoryEntity> = emptyList(),
    currentUser: UserEntity? = null,
    adminUsers: List<UserEntity> = emptyList(),
    selectedDate: String,
    onSelectDate: (String) -> Unit,
    onUnlockTask: ((task: TaskEntity, completion: TaskCompletionEntity, adminUser: UserEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var filterStaff by remember { mutableStateOf<String?>(null) }
    var statusFilter by remember { mutableStateOf(HistoryStatusFilter.ALL) }
    var taskToUnlock by remember { mutableStateOf<Pair<TaskEntity, TaskCompletionEntity>?>(null) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val todayDateStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val taskMap = remember(tasks) { tasks.associateBy { it.id } }

    // Distinct dates available
    val availableDates = remember(allCompletions) {
        val dates = allCompletions.map { it.dateString }.distinct().sortedDescending()
        if (selectedDate !in dates && dates.isNotEmpty()) {
            listOf(selectedDate) + dates
        } else if (dates.isEmpty()) {
            listOf(selectedDate)
        } else {
            dates
        }
    }

    // Determine Day of week for selectedDate
    val dayOfWeekName = remember(selectedDate) {
        DaysOfWeek.getDayOfWeekName(selectedDate)
    }

    // Scheduled tasks on that date
    val scheduledTasksOnDate = remember(tasks, dayOfWeekName) {
        tasks.filter { DaysOfWeek.runsOnDay(it.daysOfWeek, dayOfWeekName) }
    }

    val isPastDate = selectedDate < todayDateStr

    // Completions on date
    val completionsOnDate = remember(allCompletions, selectedDate, filterStaff) {
        allCompletions.filter { it.dateString == selectedDate }
            .filter { filterStaff == null || it.completedByUserName == filterStaff }
            .sortedByDescending { it.completedAt }
    }

    // Overdue tasks on date (scheduled on selected date, but no completion recorded, and date is in the past)
    val overdueTasksOnDate = remember(scheduledTasksOnDate, allCompletions, selectedDate, isPastDate, filterStaff) {
        if (!isPastDate || filterStaff != null) {
            emptyList()
        } else {
            scheduledTasksOnDate.filter { task ->
                allCompletions.none { it.taskId == task.id && it.dateString == selectedDate }
            }
        }
    }

    // Combine into unified audit list filtered by status
    val displayItems = remember(completionsOnDate, overdueTasksOnDate, statusFilter, taskMap, categoryMap) {
        val completedItems = completionsOnDate.map { comp ->
            HistoryAuditItem.Completed(comp, taskMap[comp.taskId])
        }
        val overdueItems = overdueTasksOnDate.map { task ->
            val catName = categoryMap[task.categoryId]?.name ?: "GENERAL"
            HistoryAuditItem.Overdue(task, catName)
        }

        when (statusFilter) {
            HistoryStatusFilter.ALL -> completedItems + overdueItems
            HistoryStatusFilter.OVERDUE -> overdueItems
            HistoryStatusFilter.COMPLETED -> completedItems.filter { !it.completion.isSkipped }
            HistoryStatusFilter.SKIPPED -> completedItems.filter { it.completion.isSkipped }
        }
    }

    val distinctStaffOnDate = remember(allCompletions, selectedDate) {
        allCompletions.filter { it.dateString == selectedDate }
            .map { it.completedByUserName }
            .distinct()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(RetailBackground)
            .padding(16.dp)
    ) {
        val isTablet = maxWidth >= 540.dp

        if (isTablet) {
            // Tablet Master-Detail Dual-Pane Layout
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Master Column: Date Selector & Day Summary (~300dp)
                Card(
                    modifier = Modifier
                        .widthIn(min = 280.dp, max = 320.dp)
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = RetailSurface),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, RetailBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "SHIFT AUDIT LOG",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = RetailTextPrimary,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "SELECT SHIFT DATE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = RetailTextSecondary,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )

                        HorizontalDivider(color = RetailBorder, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Date List
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(availableDates) { dateStr ->
                                val isSelected = dateStr == selectedDate
                                val countOnDate = allCompletions.count { it.dateString == dateStr }
                                val dayOverdue = if (dateStr < todayDateStr) {
                                    val dayName = DaysOfWeek.getDayOfWeekName(dateStr)
                                    val sched = tasks.count { DaysOfWeek.runsOnDay(it.daysOfWeek, dayName) }
                                    val missed = (sched - countOnDate).coerceAtLeast(0)
                                    missed
                                } else 0

                                Surface(
                                    onClick = { onSelectDate(dateStr) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) RetailHeaderBlack else RetailSurfaceSubtle,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) RetailHeaderBlack else RetailBorder
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.DateRange,
                                                contentDescription = null,
                                                tint = if (isSelected) Color.White else RetailTextPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = dateStr,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else RetailTextPrimary
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (dayOverdue > 0) {
                                                Surface(
                                                    color = RetailRedMissed,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "$dayOverdue missed",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Surface(
                                                color = if (isSelected) RetailOrange else RetailBorder,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "$countOnDate done",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) Color.White else RetailTextSecondary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Right Detail Column: Daily Log Items with Overdue Badges & Status Filter
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = RetailSurface),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, RetailBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Header Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "$selectedDate • $dayOfWeekName",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = RetailTextPrimary,
                                        letterSpacing = 0.5.sp
                                    )
                                    if (overdueTasksOnDate.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = RetailRedMissed,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "${overdueTasksOnDate.size} OVERDUE",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${completionsOnDate.size} TASKS COMPLETED • ${scheduledTasksOnDate.size} SCHEDULED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RetailTextSecondary,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Status Filters + Staff Filter
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "STATUS:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = RetailTextSecondary
                            )

                            HistoryStatusFilter.values().forEach { filter ->
                                val isSelected = statusFilter == filter
                                val count = when (filter) {
                                    HistoryStatusFilter.ALL -> completionsOnDate.size + overdueTasksOnDate.size
                                    HistoryStatusFilter.OVERDUE -> overdueTasksOnDate.size
                                    HistoryStatusFilter.COMPLETED -> completionsOnDate.count { !it.isSkipped }
                                    HistoryStatusFilter.SKIPPED -> completionsOnDate.count { it.isSkipped }
                                }
                                Surface(
                                    onClick = { statusFilter = filter },
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isSelected) {
                                        if (filter == HistoryStatusFilter.OVERDUE) RetailRedMissed else RetailHeaderBlack
                                    } else RetailSurfaceSubtle,
                                    border = BorderStroke(1.dp, if (isSelected) Color.Transparent else RetailBorder)
                                ) {
                                    Text(
                                        text = "${filter.label} ($count)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else RetailTextPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (distinctStaffOnDate.size > 1) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(modifier = Modifier.width(1.dp).height(20.dp).background(RetailBorder))
                                Spacer(modifier = Modifier.width(6.dp))

                                Text(
                                    text = "STAFF:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = RetailTextSecondary
                                )

                                Surface(
                                    onClick = { filterStaff = null },
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (filterStaff == null) RetailOrange else RetailSurfaceSubtle,
                                    border = BorderStroke(1.dp, RetailBorder)
                                ) {
                                    Text(
                                        text = "ALL STAFF",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (filterStaff == null) Color.White else RetailTextPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                distinctStaffOnDate.forEach { staffName ->
                                    val isSelected = filterStaff == staffName
                                    Surface(
                                        onClick = { filterStaff = staffName },
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isSelected) RetailOrange else RetailSurfaceSubtle,
                                        border = BorderStroke(1.dp, RetailBorder)
                                    ) {
                                        Text(
                                            text = staffName,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else RetailTextPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = RetailBorder, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Audit List
                        if (displayItems.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No log records found for $selectedDate under current filter.",
                                    fontSize = 13.sp,
                                    color = RetailTextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(displayItems) { item ->
                                    HistoryItemRow(
                                        item = item,
                                        timeFormat = timeFormat,
                                        onUnlockClick = { task, comp ->
                                            taskToUnlock = Pair(task, comp)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Compact Phone Layout
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "SHIFT AUDIT LOG",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = RetailTextPrimary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "$selectedDate • $dayOfWeekName",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = RetailTextSecondary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                // Date Picker Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableDates.take(5).forEach { dateStr ->
                        val isSelected = dateStr == selectedDate
                        Surface(
                            onClick = { onSelectDate(dateStr) },
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected) RetailHeaderBlack else RetailSurface,
                            border = BorderStroke(1.dp, if (isSelected) RetailHeaderBlack else RetailBorder)
                        ) {
                            Text(
                                text = dateStr,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else RetailTextPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Status Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HistoryStatusFilter.values().forEach { filter ->
                        val isSelected = statusFilter == filter
                        val count = when (filter) {
                            HistoryStatusFilter.ALL -> completionsOnDate.size + overdueTasksOnDate.size
                            HistoryStatusFilter.OVERDUE -> overdueTasksOnDate.size
                            HistoryStatusFilter.COMPLETED -> completionsOnDate.count { !it.isSkipped }
                            HistoryStatusFilter.SKIPPED -> completionsOnDate.count { it.isSkipped }
                        }
                        Surface(
                            onClick = { statusFilter = filter },
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected) {
                                if (filter == HistoryStatusFilter.OVERDUE) RetailRedMissed else RetailHeaderBlack
                            } else RetailSurfaceSubtle,
                            border = BorderStroke(1.dp, if (isSelected) Color.Transparent else RetailBorder)
                        ) {
                            Text(
                                text = "${filter.label} ($count)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else RetailTextPrimary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (displayItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(RetailSurface, RoundedCornerShape(6.dp))
                            .border(1.dp, RetailBorder, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No records found for $selectedDate.",
                            fontSize = 12.sp,
                            color = RetailTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayItems) { item ->
                            HistoryItemRow(
                                item = item,
                                timeFormat = timeFormat,
                                onUnlockClick = { task, comp ->
                                    taskToUnlock = Pair(task, comp)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Admin Task Unlock Dialog
        taskToUnlock?.let { (task, completion) ->
            val compTime = remember(completion.completedAt) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(completion.completedAt))
            }
            AdminUnlockDialog(
                taskTitle = task.title,
                completedByName = completion.completedByUserName,
                completedByInitials = completion.completedByUserInitials,
                completedTime = compTime,
                currentUser = currentUser ?: UserEntity(name = "ADMIN", initials = "AD", role = "ADMIN", pinHash = "", pinSalt = ""),
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
fun HistoryItemRow(
    item: HistoryAuditItem,
    timeFormat: SimpleDateFormat,
    onUnlockClick: (TaskEntity, TaskCompletionEntity) -> Unit
) {
    when (item) {
        is HistoryAuditItem.Overdue -> {
            // Visual Overdue Badge Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFEBEE), RoundedCornerShape(6.dp))
                    .border(1.5.dp, RetailRedMissed, RoundedCornerShape(6.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(RetailRedMissed, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Overdue",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = RetailRedMissed,
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                text = "⚠️ OVERDUE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = item.categoryName,
                            fontSize = 11.sp,
                            color = RetailRedMissed,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "• ${item.task.priority.uppercase()} PRIORITY",
                            fontSize = 11.sp,
                            color = RetailTextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = item.task.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = RetailTextPrimary
                    )

                    Text(
                        text = "Task was scheduled for this shift but was NOT marked as completed by the end of the day.",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RetailRedMissed
                    )
                }
            }
        }

        is HistoryAuditItem.Completed -> {
            val comp = item.completion
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RetailSurfaceSubtle, RoundedCornerShape(6.dp))
                    .border(1.dp, RetailBorder, RoundedCornerShape(6.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User Avatar Initial
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(RetailHeaderBlack, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = comp.completedByUserInitials,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = comp.taskTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = RetailTextPrimary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = comp.categoryName,
                            fontSize = 11.sp,
                            color = RetailOrange,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "•",
                            fontSize = 11.sp,
                            color = RetailTextSecondary
                        )
                        Text(
                            text = "EMP ID #${comp.completedByUserId} • ${comp.completedByUserName}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RetailTextSecondary
                        )
                    }

                    if (comp.isSkipped) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .background(Color(0xFFFFF3E0), RoundedCornerShape(2.dp))
                                .border(1.dp, Color(0xFFFFB74D), RoundedCornerShape(2.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "⚡ SKIPPED: ${comp.skipReason.ifBlank { "Excused" }}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                        }
                    } else if (comp.isPartial) {
                        val compList = SubTaskUtils.jsonToList(comp.completedSubTasksJson)
                        val outList = SubTaskUtils.jsonToList(comp.outstandingSubTasksJson)
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .background(RetailOrange.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "⚡ PARTIAL (${comp.completedSubTasksCount}/${comp.totalSubTasksCount})",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = RetailOrange
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${outList.size} OUTSTANDING",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RetailRedMissed
                                )
                            }
                            if (compList.isNotEmpty()) {
                                Text(
                                    text = "✓ Completed: ${compList.joinToString(", ")}",
                                    fontSize = 10.sp,
                                    color = RetailGreenCheck
                                )
                            }
                            if (outList.isNotEmpty()) {
                                Text(
                                    text = "⏳ Left: ${outList.joinToString(", ")}",
                                    fontSize = 10.sp,
                                    color = RetailRedMissed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Time, Status, and UNLOCK Action Button
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = timeFormat.format(Date(comp.completedAt)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RetailTextPrimary
                    )
                    Text(
                        text = when {
                            comp.isSkipped -> "SKIPPED"
                            comp.isPartial -> "PARTIAL"
                            else -> "VERIFIED"
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            comp.isSkipped -> Color(0xFFE65100)
                            comp.isPartial -> RetailOrange
                            else -> RetailGreenCheck
                        }
                    )

                    // UNLOCK action button
                    if (item.task != null) {
                        Surface(
                            onClick = { onUnlockClick(item.task, comp) },
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFFD32F2F)),
                            color = Color(0xFFFFEBEE),
                            modifier = Modifier.testTag("history_unlock_${comp.taskId}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Unlock",
                                    modifier = Modifier.size(13.dp),
                                    tint = Color(0xFFD32F2F)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "UNLOCK",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD32F2F)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

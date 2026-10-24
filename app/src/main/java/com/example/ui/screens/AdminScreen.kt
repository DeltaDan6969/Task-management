package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.util.SubTaskUtils
import com.example.data.*
import com.example.ui.AdminTab
import com.example.ui.ReportStats
import com.example.ui.components.PinPadDialog
import com.example.ui.theme.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminScreen(
    currentUser: UserEntity,
    currentTab: AdminTab,
    onTabSelected: (AdminTab) -> Unit,
    users: List<UserEntity>,
    categories: List<CategoryEntity>,
    tasks: List<TaskEntity>,
    storeConfigs: Map<String, String>,
    reportStats: ReportStats,
    allCompletions: List<TaskCompletionEntity> = emptyList(),
    allPhotos: List<TaskPhotoEntity> = emptyList(),
    onAddUser: (name: String, role: String, pin: String, onDone: () -> Unit) -> Unit,
    onUpdateUserPin: (userId: Long, newPin: String, onDone: () -> Unit) -> Unit,
    onDeleteUser: (userId: Long) -> Unit,
    onAddCategory: (name: String) -> Unit,
    onDeleteCategory: (categoryId: Long) -> Unit,
    onAddTask: (categoryId: Long, title: String, bullets: String, priority: String, daysOfWeek: String) -> Unit,
    onUpdateTask: (TaskEntity) -> Unit,
    onDeleteTask: (taskId: Long) -> Unit,
    onUpdateBranding: (storeName: String, tagline: String) -> Unit,
    onExportBackup: () -> Unit = {},
    onRestoreBackup: (String, (String) -> Unit, (String) -> Unit) -> Unit = { _, _, _ -> },
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddUserDialog by remember { mutableStateOf(false) }
    var changePinTargetUser by remember { mutableStateOf<UserEntity?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var addTaskInitialDay by remember { mutableStateOf(DaysOfWeek.ALL) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RetailBackground)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                })
            }
    ) {
        // Top Header matching Image 1: "ADMIN", subtitle "MANAGE LAYOUT & STAFF", user initials badge, logout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ADMIN",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = RetailTextPrimary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "MANAGE LAYOUT & STAFF",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = RetailTextSecondary,
                    letterSpacing = 1.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 5-min Auto Log Out Inactivity Indicator
                Surface(
                    color = RetailSurfaceSubtle,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, RetailBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Auto logout timer",
                            tint = RetailOrange,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "5M AUTO-LOGOUT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = RetailTextPrimary,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // User initials badge [DS] in orange
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

        // Sub-tabs row: LAYOUT | USERS | LOGS & PHOTOS | BRANDING | REPORT
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RetailSurfaceSubtle)
                .border(1.dp, RetailBorder)
        ) {
            AdminTab.values().forEach { tab ->
                val isSelected = currentTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(tab) }
                        .background(if (isSelected) RetailHeaderBlack else Color.Transparent)
                        .padding(vertical = 12.dp)
                        .testTag("admin_tab_${tab.name}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (tab == AdminTab.LOGS) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = if (isSelected) RetailOrange else RetailTextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = if (tab == AdminTab.LOGS) "LOGS & PHOTOS" else tab.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isSelected) Color.White else RetailTextSecondary,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
        ) {
            when (currentTab) {
                AdminTab.REPORT -> ReportTabContent(reportStats)
                AdminTab.LOGS -> CompletionsTableContent(
                    allCompletions = allCompletions,
                    users = users,
                    allPhotos = allPhotos,
                    tasks = tasks,
                    categories = categories
                )
                AdminTab.USERS -> UsersTabContent(
                    users = users,
                    onOpenAddUser = { showAddUserDialog = true },
                    onOpenChangePin = { changePinTargetUser = it },
                    onDeleteUser = onDeleteUser
                )
                AdminTab.LAYOUT -> LayoutTabContent(
                    categories = categories,
                    tasks = tasks,
                    onOpenAddCategory = { showAddCategoryDialog = true },
                    onOpenAddTask = { day ->
                        addTaskInitialDay = day
                        showAddTaskDialog = true
                    },
                    onEditTask = { editingTask = it },
                    onDeleteTask = onDeleteTask,
                    onDeleteCategory = onDeleteCategory
                )
                AdminTab.BRANDING -> BrandingTabContent(
                    storeConfigs = storeConfigs,
                    onSave = onUpdateBranding,
                    onExportBackup = onExportBackup,
                    onRestoreBackup = onRestoreBackup
                )
            }
        }
    }

    // --- Dialogs ---

    // Add User Dialog
    if (showAddUserDialog) {
        AddUserDialog(
            onDismiss = { showAddUserDialog = false },
            onAddUser = { name, role, pin ->
                onAddUser(name, role, pin) {
                    showAddUserDialog = false
                }
            }
        )
    }

    // Change PIN Dialog
    changePinTargetUser?.let { user ->
        ChangePinDialog(
            user = user,
            onDismiss = { changePinTargetUser = null },
            onPinChanged = { newPin ->
                onUpdateUserPin(user.id, newPin) {
                    changePinTargetUser = null
                }
            }
        )
    }

    // Add Category Dialog
    if (showAddCategoryDialog) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onAddCategory = { name ->
                onAddCategory(name)
                showAddCategoryDialog = false
            }
        )
    }

    // Add Task Dialog
    if (showAddTaskDialog) {
        AddTaskDialog(
            categories = categories,
            initialDay = addTaskInitialDay,
            onDismiss = { showAddTaskDialog = false },
            onAddTask = { catId, title, bullets, priority, daysOfWeek ->
                onAddTask(catId, title, bullets, priority, daysOfWeek)
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
                onUpdateTask(updated)
                editingTask = null
            }
        )
    }
}

// --- REPORT TAB CONTENT (Matching Image 1) ---
@Composable
fun ReportTabContent(stats: ReportStats) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 540.dp

        if (isTablet) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    KpiBanner(stats)
                    DailyBreakdownCard(stats)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TopStaffCard(stats)
                    MostSkippedCard(stats)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { KpiBanner(stats) }
                item { DailyBreakdownCard(stats) }
                item { TopStaffCard(stats) }
                item { MostSkippedCard(stats) }
            }
        }
    }
}

@Composable
private fun KpiBanner(stats: ReportStats) {
    Card(
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = RetailOrange),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text(
                text = "${stats.totalSignOffsLast7Days}",
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                lineHeight = 48.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "SIGN-OFFS · LAST 7 DAYS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f),
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun DailyBreakdownCard(stats: ReportStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, RetailHeaderBlack, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(RetailHeaderBlack)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = "PER-DAY BREAKDOWN",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RetailSurface)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val maxCount = (stats.dailyBreakdown.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
            stats.dailyBreakdown.forEach { daily ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = daily.dateString,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = RetailTextSecondary,
                        modifier = Modifier.width(90.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp)
                            .border(1.dp, RetailHeaderBlack, RoundedCornerShape(2.dp))
                            .background(RetailSurfaceSubtle, RoundedCornerShape(2.dp))
                    ) {
                        if (daily.count > 0) {
                            val fraction = daily.count.toFloat() / maxCount
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction.coerceIn(0.05f, 1f))
                                    .background(RetailHeaderBlack, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "${daily.count}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RetailTextPrimary,
                        modifier = Modifier.width(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TopStaffCard(stats: ReportStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, RetailHeaderBlack, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(RetailHeaderBlack)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = "TOP STAFF",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.sp
            )
        }

        if (stats.topStaff.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RetailSurface)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No staff completions recorded in the last 7 days.",
                    fontSize = 12.sp,
                    color = RetailTextSecondary
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RetailSurface)
            ) {
                stats.topStaff.forEachIndexed { index, staff ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(RetailHeaderBlack, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = staff.initials,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = staff.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = RetailTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${staff.count}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = RetailOrange
                        )
                    }
                    if (index < stats.topStaff.lastIndex) {
                        HorizontalDivider(color = RetailBorder, thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MostSkippedCard(stats: ReportStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, RetailHeaderBlack, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(RetailHeaderBlack)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = "MOST-SKIPPED TASKS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RetailSurface)
        ) {
            stats.mostSkippedTasks.take(6).forEachIndexed { index, skipped ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = skipped.taskTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = RetailTextPrimary
                        )
                        Text(
                            text = "MISSED ${skipped.missedDays}/${skipped.totalDays} DAYS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RetailTextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(RetailRedMissed, RoundedCornerShape(3.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${skipped.missedDays}",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }
                if (index < stats.mostSkippedTasks.take(6).lastIndex) {
                    HorizontalDivider(color = RetailBorder, thickness = 1.dp)
                }
            }
        }
    }
}

// --- USERS TAB CONTENT ---
@Composable
fun UsersTabContent(
    users: List<UserEntity>,
    onOpenAddUser: () -> Unit,
    onOpenChangePin: (UserEntity) -> Unit,
    onDeleteUser: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STAFF DIRECTORY (${users.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = RetailTextPrimary
            )
            Button(
                onClick = onOpenAddUser,
                colors = ButtonDefaults.buttonColors(containerColor = RetailOrange),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.testTag("add_user_button")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("ADD USER", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(users) { user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RetailSurface, RoundedCornerShape(4.dp))
                        .border(1.dp, RetailBorder, RoundedCornerShape(4.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(RetailHeaderBlack, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.initials,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = RetailTextPrimary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (user.role == "ADMIN") RetailOrange.copy(alpha = 0.15f) else RetailBorder.copy(alpha = 0.3f),
                                        RoundedCornerShape(2.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = user.role,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (user.role == "ADMIN") RetailOrange else RetailTextSecondary
                                )
                            }
                            if (user.mustChangePin) {
                                Surface(
                                    color = Color(0xFFF3E8FF),
                                    shape = RoundedCornerShape(2.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD8B4FE))
                                ) {
                                    Text(
                                        text = "★ NEW (PIN SETUP PENDING)",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF7E22CE),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "PIN Protected (Hashed)",
                                    fontSize = 11.sp,
                                    color = RetailTextSecondary
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { onOpenChangePin(user) },
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PIN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        if (users.size > 1) {
                            IconButton(
                                onClick = { onDeleteUser(user.id) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RetailRedMissed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- LAYOUT TAB CONTENT ---
@Composable
fun LayoutTabContent(
    categories: List<CategoryEntity>,
    tasks: List<TaskEntity>,
    onOpenAddCategory: () -> Unit,
    onOpenAddTask: (initialDay: String) -> Unit,
    onEditTask: (TaskEntity) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onDeleteCategory: (Long) -> Unit
) {
    var selectedDayFilter by remember { mutableStateOf("ALL") }
    val daysList = listOf("ALL") + DaysOfWeek.ORDERED_DAYS + listOf(DaysOfWeek.ONE_TIME)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "CHECKLIST LAYOUT & WEEKLY SCHEDULE",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = RetailTextPrimary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = if (selectedDayFilter == "ALL")
                        "Manage recurring tasks across all days of the week"
                    else
                        "Tasks for ${selectedDayFilter}s · Recurring weekly every ${selectedDayFilter.lowercase().replaceFirstChar { it.uppercase() }}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selectedDayFilter == "ALL") RetailTextSecondary else RetailOrange
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenAddCategory,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("+ CATEGORY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        onOpenAddTask(if (selectedDayFilter == "ALL") DaysOfWeek.ALL else selectedDayFilter)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RetailOrange),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (selectedDayFilter == "ALL") "+ ADD TASK" else "+ ADD EXTRA TASK (${DaysOfWeek.SHORT_NAMES[selectedDayFilter] ?: selectedDayFilter})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Day of Week Filter Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = RetailSurfaceSubtle,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, RetailBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "SET BY DAY:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = RetailTextSecondary,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(end = 4.dp)
                )

                daysList.forEach { dayKey ->
                    val isSelected = selectedDayFilter == dayKey
                    val count = when (dayKey) {
                        "ALL" -> tasks.size
                        DaysOfWeek.ONE_TIME -> tasks.count { it.isEffectivelyOneTime }
                        else -> tasks.count { DaysOfWeek.runsOnDay(it.daysOfWeek, dayKey) }
                    }
                    val label = when (dayKey) {
                        "ALL" -> "ALL DAYS"
                        DaysOfWeek.ONE_TIME -> "★ ONE-TIME"
                        else -> (DaysOfWeek.SHORT_NAMES[dayKey] ?: dayKey)
                    }

                    Surface(
                        onClick = { selectedDayFilter = dayKey },
                        shape = RoundedCornerShape(4.dp),
                        color = if (isSelected) RetailHeaderBlack else RetailSurface,
                        border = BorderStroke(1.dp, if (isSelected) RetailHeaderBlack else RetailBorder),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else RetailTextPrimary
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isSelected) RetailOrange else RetailBorder.copy(alpha = 0.7f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "$count",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSelected) Color.White else RetailTextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (categories.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = RetailSurface,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, RetailBorder)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = RetailOrange,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "NO CATEGORIES OR TASKS YET",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = RetailTextPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "The task database is clean and fresh. Tap '+ CATEGORY' above to create your first department or category.",
                        fontSize = 12.sp,
                        color = RetailTextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onOpenAddCategory,
                        colors = ButtonDefaults.buttonColors(containerColor = RetailOrange),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("+ CREATE FIRST CATEGORY", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 380.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(categories) { category ->
                val catTasks = tasks.filter {
                    it.categoryId == category.id &&
                    when (selectedDayFilter) {
                        "ALL" -> true
                        DaysOfWeek.ONE_TIME -> it.isEffectivelyOneTime
                        else -> DaysOfWeek.runsOnDay(it.daysOfWeek, selectedDayFilter)
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, RetailHeaderBlack, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RetailHeaderBlack)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = category.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(RetailOrange, RoundedCornerShape(2.dp))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "${catTasks.size} TASKS",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        IconButton(
                            onClick = { onDeleteCategory(category.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Category", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RetailSurface)
                    ) {
                        if (catTasks.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (selectedDayFilter == "ALL")
                                        "No tasks in this category."
                                    else
                                        "No tasks scheduled for ${DaysOfWeek.SHORT_NAMES[selectedDayFilter] ?: selectedDayFilter}.",
                                    fontSize = 12.sp,
                                    color = RetailTextSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(
                                    onClick = {
                                        onOpenAddTask(if (selectedDayFilter == "ALL") DaysOfWeek.ALL else selectedDayFilter)
                                    }
                                ) {
                                    Text(
                                        text = if (selectedDayFilter == "ALL") "+ Add Task" else "+ Add Task for ${DaysOfWeek.SHORT_NAMES[selectedDayFilter] ?: selectedDayFilter}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RetailOrange
                                    )
                                }
                            }
                        } else {
                            catTasks.forEachIndexed { index, task ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        when (task.priority.uppercase()) {
                                                            "HIGH" -> RetailRedMissed.copy(alpha = 0.15f)
                                                            "LOW" -> RetailBorder.copy(alpha = 0.5f)
                                                            else -> RetailOrange.copy(alpha = 0.15f)
                                                        },
                                                        RoundedCornerShape(2.dp)
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = task.priority.uppercase(),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when (task.priority.uppercase()) {
                                                        "HIGH" -> RetailRedMissed
                                                        "LOW" -> RetailTextSecondary
                                                        else -> RetailOrange
                                                    }
                                                )
                                            }

                                            if (task.isEffectivelyOneTime) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFFF3E8FF), RoundedCornerShape(2.dp))
                                                        .border(1.dp, Color(0xFFD8B4FE), RoundedCornerShape(2.dp))
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = if (task.completedDate.isNotBlank()) "✓ ONE-TIME (DONE)" else "★ ONE-TIME ROLLOVER",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF6B21A8)
                                                    )
                                                }
                                            } else {
                                                val dayDisplay = DaysOfWeek.formatDisplayDays(task.daysOfWeek)
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            if (dayDisplay == "EVERY DAY") RetailSurfaceSubtle else Color(0xFFE8F5E9),
                                                            RoundedCornerShape(2.dp)
                                                        )
                                                        .border(
                                                            1.dp,
                                                            if (dayDisplay == "EVERY DAY") RetailBorder else Color(0xFF81C784),
                                                            RoundedCornerShape(2.dp)
                                                        )
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "📅 $dayDisplay",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (dayDisplay == "EVERY DAY") RetailTextSecondary else Color(0xFF2E7D32)
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = task.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RetailTextPrimary
                                        )

                                        if (task.bulletPoints.isNotBlank()) {
                                            Text(
                                                text = task.bulletPoints.replace("\n", " • "),
                                                fontSize = 11.sp,
                                                color = RetailTextSecondary,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { onEditTask(task) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = RetailTextPrimary, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { onDeleteTask(task.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RetailRedMissed, modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (index < catTasks.lastIndex) {
                                    HorizontalDivider(color = RetailBorder)
                                }
                            }

                            Surface(
                                onClick = {
                                    onOpenAddTask(if (selectedDayFilter == "ALL") DaysOfWeek.ALL else selectedDayFilter)
                                },
                                color = RetailSurfaceSubtle,
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = if (selectedDayFilter == "ALL") "+ Add Task to ${category.name}" else "+ Add Extra Task for ${DaysOfWeek.SHORT_NAMES[selectedDayFilter] ?: selectedDayFilter}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RetailOrange
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

// --- BRANDING TAB CONTENT ---
// --- BRANDING & BACKUP TAB CONTENT ---
@Composable
fun BrandingTabContent(
    storeConfigs: Map<String, String>,
    onSave: (String, String) -> Unit,
    onExportBackup: () -> Unit = {},
    onRestoreBackup: (String, (String) -> Unit, (String) -> Unit) -> Unit = { _, _, _ -> }
) {
    var storeName by remember(storeConfigs) {
        mutableStateOf(storeConfigs["store_name"] ?: "ONESTOP CANTERBURY")
    }
    var tagline by remember(storeConfigs) {
        mutableStateOf(storeConfigs["tagline"] ?: "SIGN IN TO START YOUR SHIFT")
    }
    var savedAlert by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Store Branding Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RetailSurface, RoundedCornerShape(4.dp))
                .border(1.dp, RetailBorder, RoundedCornerShape(4.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "STORE BRANDING & IDENTIFIER",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = RetailTextPrimary
            )

            OutlinedTextField(
                value = storeName,
                onValueChange = { storeName = it },
                label = { Text("Store Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = tagline,
                onValueChange = { tagline = it },
                label = { Text("Sign-In Greeting / Subtitle") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    onSave(storeName, tagline)
                    savedAlert = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = RetailOrange),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("SAVE BRANDING CHANGES", fontWeight = FontWeight.Bold)
            }

            if (savedAlert) {
                Text(
                    text = "✓ Store branding updated successfully!",
                    color = RetailGreenCheck,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // Google Cloud Live Database Status Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RetailSurface, RoundedCornerShape(4.dp))
                .border(1.dp, RetailBorder, RoundedCornerShape(4.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Cloud,
                    contentDescription = null,
                    tint = RetailGreenDone,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GOOGLE CLOUD FIRESTORE LIVE DATABASE",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = RetailTextPrimary
                )
            }

            Surface(
                color = RetailSurfaceSubtle,
                border = BorderStroke(1.dp, RetailBorder),
                shape = RoundedCornerShape(6.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "• Database: ai-studio-android-shifttas-347ba838-628f-49f4-a473-be4ef8849c02",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RetailTextPrimary
                    )
                    Text(
                        text = "• Project: gen-lang-client-0968461451",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RetailTextSecondary
                    )
                    Text(
                        text = "• Live Status: Connected & Real-time Two-Way Sync Active with Web App",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RetailGreenDone
                    )
                }
            }
        }

        // 2. Data Protection, Backup & App Update Instructions Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RetailSurface, RoundedCornerShape(4.dp))
                .border(1.dp, RetailBorder, RoundedCornerShape(4.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = RetailOrange,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "APP UPDATES & DATA PROTECTION",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = RetailTextPrimary
                )
            }

            // In-place Update Instructions Box
            Surface(
                color = RetailSurfaceSubtle,
                border = BorderStroke(1.dp, RetailBorder),
                shape = RoundedCornerShape(6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "💡 How to update the app WITHOUT resetting tasks:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = RetailOrange
                    )
                    Text(
                        text = "1. DO NOT uninstall the old app before installing the new one! In Android, uninstalling automatically erases the local database.",
                        fontSize = 12.sp,
                        color = RetailTextPrimary
                    )
                    Text(
                        text = "2. Simply download the new APK and tap it to install directly OVER the existing app.",
                        fontSize = 12.sp,
                        color = RetailTextPrimary
                    )
                    Text(
                        text = "3. Android's Package Installer will prompt: \"Do you want to update this app? Existing data will not be lost.\" Tap UPDATE.",
                        fontSize = 12.sp,
                        color = RetailTextPrimary
                    )
                    Text(
                        text = "4. Your custom layout, staff logins, daily checklists, and photos will be preserved completely.",
                        fontSize = 12.sp,
                        color = RetailTextSecondary
                    )
                }
            }

            // Export & Restore Actions
            Text(
                text = "Manual Backup & Restore (Recommended before major updates):",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = RetailTextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onExportBackup,
                    colors = ButtonDefaults.buttonColors(containerColor = RetailHeaderBlack),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("EXPORT BACKUP (JSON)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { showRestoreDialog = true },
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, RetailOrange),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, tint = RetailOrange, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RESTORE BACKUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RetailOrange)
                }
            }
        }
    }

    if (showRestoreDialog) {
        RestoreBackupDialog(
            onDismiss = { showRestoreDialog = false },
            onRestore = { json, onDone, onError ->
                onRestoreBackup(json, onDone, onError)
            }
        )
    }
}

@Composable
fun RestoreBackupDialog(
    onDismiss: () -> Unit,
    onRestore: (String, (String) -> Unit, (String) -> Unit) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var jsonText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            focusManager.clearFocus()
            keyboardController?.hide()
        },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = RetailSurface,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(0.92f)
                    .padding(12.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        })
                    }
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "RESTORE DATABASE FROM BACKUP",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = RetailTextPrimary
                    )

                    Text(
                        text = "Paste the JSON backup text below to restore tasks, categories, and staff:",
                        fontSize = 11.sp,
                        color = RetailTextSecondary
                    )

                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = { jsonText = it },
                        placeholder = { Text("Paste JSON backup here...", fontSize = 11.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RetailOrange,
                            unfocusedBorderColor = RetailBorder
                        )
                    )

                    if (statusMessage != null) {
                        Text(
                            text = statusMessage!!,
                            color = if (isError) RetailRedMissed else RetailGreenCheck,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("CANCEL") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (jsonText.isNotBlank()) {
                                    onRestore(
                                        jsonText.trim(),
                                        { msg ->
                                            isError = false
                                            statusMessage = msg
                                        },
                                        { err ->
                                            isError = true
                                            statusMessage = "Error: $err"
                                        }
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RetailOrange)
                        ) {
                            Text("RESTORE ALL DATA")
                        }
                    }
                }
            }
        }
    }
}

// --- Modals ---

@Composable
fun AddUserDialog(
    onDismiss: () -> Unit,
    onAddUser: (name: String, role: String, pin: String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("STAFF") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = {
            focusManager.clearFocus()
            keyboardController?.hide()
        },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = RetailSurface,
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(0.92f)
                    .padding(16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        })
                    }
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("ADD NEW STAFF MEMBER", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Name (e.g. JOHN DOE)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf("STAFF", "ADMIN").forEach { r ->
                            val selected = role == r
                            Surface(
                                onClick = { role = r },
                                shape = RoundedCornerShape(4.dp),
                                color = if (selected) RetailOrange else RetailSurfaceSubtle,
                                border = androidx.compose.foundation.BorderStroke(1.dp, RetailBorder),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = r,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.White else RetailTextPrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        color = Color(0xFFF3E8FF),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD8B4FE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF7E22CE),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "FIRST-TIME SIGN IN PIN SETUP",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF7E22CE),
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "• No PIN is needed from management.\n• On their first shift login, this employee will be prompted to choose and confirm their own private 4-digit PIN.",
                                fontSize = 11.sp,
                                color = Color(0xFF581C87),
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (errorMsg != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(errorMsg!!, color = RetailRedMissed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("CANCEL") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (name.isBlank()) {
                                    errorMsg = "Please enter the staff member's name"
                                } else {
                                    onAddUser(name.trim(), role, "")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RetailOrange)
                        ) {
                            Text("SAVE STAFF MEMBER")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChangePinDialog(
    user: UserEntity,
    onDismiss: () -> Unit,
    onPinChanged: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var newPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = {
            focusManager.clearFocus()
            keyboardController?.hide()
        },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = RetailSurface,
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth(0.92f)
                    .padding(16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        })
                    }
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("RESET PIN: ${user.name}", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text("New PIN will be salted and encrypted in SQLite.", fontSize = 11.sp, color = RetailTextSecondary)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                        label = { Text("New 4-Digit PIN") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (errorMsg != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(errorMsg!!, color = RetailRedMissed, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("CANCEL") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newPin.length != 4) {
                                    errorMsg = "PIN must be exactly 4 digits"
                                } else {
                                    onPinChanged(newPin)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RetailOrange)
                        ) {
                            Text("UPDATE PIN")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onAddCategory: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var name by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = {
            focusManager.clearFocus()
            keyboardController?.hide()
        },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = RetailSurface,
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth(0.92f)
                    .padding(16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        })
                    }
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("ADD TASK CATEGORY", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Category Name (e.g. PRODUCE & FRESH)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("CANCEL") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { if (name.isNotBlank()) onAddCategory(name) },
                            colors = ButtonDefaults.buttonColors(containerColor = RetailOrange)
                        ) {
                            Text("ADD")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddTaskDialog(
    categories: List<CategoryEntity>,
    initialDay: String = DaysOfWeek.ALL,
    onDismiss: () -> Unit,
    onAddTask: (categoryId: Long, title: String, bullets: String, priority: String, daysOfWeek: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var bullets by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(TaskPriority.MEDIUM) }
    var selectedCatId by remember { mutableStateOf(categories.firstOrNull()?.id ?: 0L) }

    val isInitialOneTime = initialDay.equals(DaysOfWeek.ONE_TIME, ignoreCase = true)
    var isOneTimeTask by remember { mutableStateOf(isInitialOneTime) }
    val startEveryDay = !isInitialOneTime && (initialDay.isBlank() || initialDay == DaysOfWeek.ALL)
    var isEveryDay by remember { mutableStateOf(startEveryDay) }
    var selectedDays by remember {
        mutableStateOf(
            if (startEveryDay || isInitialOneTime) {
                DaysOfWeek.ORDERED_DAYS.toSet()
            } else {
                setOf(DaysOfWeek.SHORT_TO_FULL[initialDay.uppercase()] ?: initialDay.uppercase())
            }
        )
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Dialog(
        onDismissRequest = {
            focusManager.clearFocus()
            keyboardController?.hide()
        },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = RetailSurface,
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(0.92f)
                    .padding(16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        })
                    }
            ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("ADD NEW SHIFT TASK", fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(
                    text = "Configure weekly recurring task or one-time rollover task",
                    fontSize = 11.sp,
                    color = RetailTextSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))

                Text("Category:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RetailTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = cat.id == selectedCatId
                        Surface(
                            onClick = { selectedCatId = cat.id },
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected) RetailHeaderBlack else RetailSurfaceSubtle,
                            border = androidx.compose.foundation.BorderStroke(1.dp, RetailBorder),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = cat.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else RetailTextPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Schedule Selection (Recurring vs One-Time)
                Text("Schedule Type (Recurring or One-Time):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RetailTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        onClick = {
                            isOneTimeTask = false
                            isEveryDay = true
                            selectedDays = DaysOfWeek.ORDERED_DAYS.toSet()
                        },
                        shape = RoundedCornerShape(4.dp),
                        color = if (!isOneTimeTask && isEveryDay) RetailHeaderBlack else RetailSurfaceSubtle,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (!isOneTimeTask && isEveryDay) RetailHeaderBlack else RetailBorder),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("EVERY DAY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (!isOneTimeTask && isEveryDay) Color.White else RetailTextPrimary)
                        }
                    }
                    Surface(
                        onClick = {
                            isOneTimeTask = false
                            isEveryDay = false
                            selectedDays = setOf(DaysOfWeek.MONDAY, DaysOfWeek.TUESDAY, DaysOfWeek.WEDNESDAY, DaysOfWeek.THURSDAY, DaysOfWeek.FRIDAY)
                        },
                        shape = RoundedCornerShape(4.dp),
                        color = if (!isOneTimeTask && !isEveryDay && selectedDays == setOf(DaysOfWeek.MONDAY, DaysOfWeek.TUESDAY, DaysOfWeek.WEDNESDAY, DaysOfWeek.THURSDAY, DaysOfWeek.FRIDAY)) RetailHeaderBlack else RetailSurfaceSubtle,
                        border = androidx.compose.foundation.BorderStroke(1.dp, RetailBorder),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("MON-FRI", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (!isOneTimeTask && !isEveryDay && selectedDays == setOf(DaysOfWeek.MONDAY, DaysOfWeek.TUESDAY, DaysOfWeek.WEDNESDAY, DaysOfWeek.THURSDAY, DaysOfWeek.FRIDAY)) Color.White else RetailTextPrimary)
                        }
                    }
                    Surface(
                        onClick = {
                            isOneTimeTask = false
                            isEveryDay = false
                            selectedDays = setOf(DaysOfWeek.SATURDAY, DaysOfWeek.SUNDAY)
                        },
                        shape = RoundedCornerShape(4.dp),
                        color = if (!isOneTimeTask && !isEveryDay && selectedDays == setOf(DaysOfWeek.SATURDAY, DaysOfWeek.SUNDAY)) RetailHeaderBlack else RetailSurfaceSubtle,
                        border = androidx.compose.foundation.BorderStroke(1.dp, RetailBorder),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("SAT-SUN", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (!isOneTimeTask && !isEveryDay && selectedDays == setOf(DaysOfWeek.SATURDAY, DaysOfWeek.SUNDAY)) Color.White else RetailTextPrimary)
                        }
                    }
                    Surface(
                        onClick = {
                            isOneTimeTask = true
                            isEveryDay = false
                        },
                        shape = RoundedCornerShape(4.dp),
                        color = if (isOneTimeTask) Color(0xFF6B21A8) else Color(0xFFF3E8FF),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isOneTimeTask) Color(0xFF6B21A8) else Color(0xFFD8B4FE)),
                        modifier = Modifier.weight(1.2f).height(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("★ ONE-TIME", fontSize = 9.sp, fontWeight = FontWeight.Black, color = if (isOneTimeTask) Color.White else Color(0xFF6B21A8))
                        }
                    }
                }

                if (!isOneTimeTask) {
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DaysOfWeek.ORDERED_DAYS.forEach { dayName ->
                            val short = DaysOfWeek.SHORT_NAMES[dayName] ?: dayName.take(3)
                            val isSelected = isEveryDay || selectedDays.contains(dayName)
                            Surface(
                                onClick = {
                                    isOneTimeTask = false
                                    if (isEveryDay) {
                                        isEveryDay = false
                                        selectedDays = setOf(dayName)
                                    } else {
                                        val next = if (selectedDays.contains(dayName)) {
                                            if (selectedDays.size > 1) selectedDays - dayName else selectedDays
                                        } else {
                                            selectedDays + dayName
                                        }
                                        if (next.size == 7) {
                                            isEveryDay = true
                                            selectedDays = DaysOfWeek.ORDERED_DAYS.toSet()
                                        } else {
                                            selectedDays = next
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(4.dp),
                                color = if (isSelected) RetailOrange else RetailSurfaceSubtle,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) RetailOrange else RetailBorder),
                                modifier = Modifier.weight(1f).height(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = short,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else RetailTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = if (isOneTimeTask)
                        "★ One-time task: Rolls over to subsequent days until completed. Never repeats weekly."
                    else if (isEveryDay || selectedDays.size >= 7)
                        "• Repeats every day of the week"
                    else
                        "• Repeats every week on: " + DaysOfWeek.formatDisplayDays(selectedDays.joinToString(",")),
                    fontSize = 10.sp,
                    fontWeight = if (isOneTimeTask) FontWeight.Bold else FontWeight.Normal,
                    color = if (isOneTimeTask) Color(0xFF6B21A8) else RetailOrangeDark,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Priority Selection
                Text("Priority Level:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RetailTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(TaskPriority.LOW, TaskPriority.MEDIUM, TaskPriority.HIGH).forEach { p ->
                        val isSelected = priority == p
                        val activeColor = when (p) {
                            TaskPriority.HIGH -> RetailRedMissed
                            TaskPriority.LOW -> RetailHeaderBlackLight
                            else -> RetailOrange
                        }
                        Surface(
                            onClick = { priority = p },
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected) activeColor else RetailSurfaceSubtle,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) activeColor else RetailBorder),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = p,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else RetailTextPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title (e.g. BREAD FILL)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = bullets,
                    onValueChange = { bullets = it },
                    label = { Text("Bullet instructions (one per line)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("CANCEL") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank() && selectedCatId != 0L) {
                                val finalDays = if (isOneTimeTask) DaysOfWeek.ONE_TIME else if (isEveryDay || selectedDays.size >= 7) DaysOfWeek.ALL else selectedDays.joinToString(",")
                                onAddTask(selectedCatId, title, bullets, priority, finalDays)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RetailOrange)
                    ) {
                        Text("SAVE TASK")
                    }
                }
            }
        }
    }
}
}

@Composable
fun EditTaskDialog(
    task: TaskEntity,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit
) {
    var title by remember { mutableStateOf(task.title) }
    var bullets by remember { mutableStateOf(task.bulletPoints) }
    var priority by remember { mutableStateOf(task.priority) }
    var selectedCatId by remember { mutableStateOf(task.categoryId) }

    val isInitialOneTime = task.isEffectivelyOneTime
    var isOneTimeTask by remember { mutableStateOf(isInitialOneTime) }
    val startEveryDay = !isInitialOneTime && (task.daysOfWeek.isBlank() || task.daysOfWeek.equals(DaysOfWeek.ALL, ignoreCase = true))
    var isEveryDay by remember { mutableStateOf(startEveryDay) }
    var selectedDays by remember {
        mutableStateOf(
            if (startEveryDay || isInitialOneTime) {
                DaysOfWeek.ORDERED_DAYS.toSet()
            } else {
                task.daysOfWeek.split(",").map { it.trim().uppercase() }.map { DaysOfWeek.SHORT_TO_FULL[it] ?: it }.toSet()
            }
        )
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Dialog(
        onDismissRequest = {
            focusManager.clearFocus()
            keyboardController?.hide()
        },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = RetailSurface,
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(0.92f)
                    .padding(16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        })
                    }
            ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("EDIT TASK", fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text("Update details, recurring days, or one-time rollover schedule", fontSize = 11.sp, color = RetailTextSecondary)
                Spacer(modifier = Modifier.height(14.dp))

                Text("Category:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RetailTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = cat.id == selectedCatId
                        Surface(
                            onClick = { selectedCatId = cat.id },
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected) RetailHeaderBlack else RetailSurfaceSubtle,
                            border = androidx.compose.foundation.BorderStroke(1.dp, RetailBorder),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = cat.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else RetailTextPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Days of the Week Selection
                Text("Schedule Type (Recurring or One-Time):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RetailTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        onClick = {
                            isOneTimeTask = false
                            isEveryDay = true
                            selectedDays = DaysOfWeek.ORDERED_DAYS.toSet()
                        },
                        shape = RoundedCornerShape(4.dp),
                        color = if (!isOneTimeTask && isEveryDay) RetailHeaderBlack else RetailSurfaceSubtle,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (!isOneTimeTask && isEveryDay) RetailHeaderBlack else RetailBorder),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("EVERY DAY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (!isOneTimeTask && isEveryDay) Color.White else RetailTextPrimary)
                        }
                    }
                    Surface(
                        onClick = {
                            isOneTimeTask = false
                            isEveryDay = false
                            selectedDays = setOf(DaysOfWeek.MONDAY, DaysOfWeek.TUESDAY, DaysOfWeek.WEDNESDAY, DaysOfWeek.THURSDAY, DaysOfWeek.FRIDAY)
                        },
                        shape = RoundedCornerShape(4.dp),
                        color = if (!isOneTimeTask && !isEveryDay && selectedDays == setOf(DaysOfWeek.MONDAY, DaysOfWeek.TUESDAY, DaysOfWeek.WEDNESDAY, DaysOfWeek.THURSDAY, DaysOfWeek.FRIDAY)) RetailHeaderBlack else RetailSurfaceSubtle,
                        border = androidx.compose.foundation.BorderStroke(1.dp, RetailBorder),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("MON-FRI", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (!isOneTimeTask && !isEveryDay && selectedDays == setOf(DaysOfWeek.MONDAY, DaysOfWeek.TUESDAY, DaysOfWeek.WEDNESDAY, DaysOfWeek.THURSDAY, DaysOfWeek.FRIDAY)) Color.White else RetailTextPrimary)
                        }
                    }
                    Surface(
                        onClick = {
                            isOneTimeTask = false
                            isEveryDay = false
                            selectedDays = setOf(DaysOfWeek.SATURDAY, DaysOfWeek.SUNDAY)
                        },
                        shape = RoundedCornerShape(4.dp),
                        color = if (!isOneTimeTask && !isEveryDay && selectedDays == setOf(DaysOfWeek.SATURDAY, DaysOfWeek.SUNDAY)) RetailHeaderBlack else RetailSurfaceSubtle,
                        border = androidx.compose.foundation.BorderStroke(1.dp, RetailBorder),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("SAT-SUN", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (!isOneTimeTask && !isEveryDay && selectedDays == setOf(DaysOfWeek.SATURDAY, DaysOfWeek.SUNDAY)) Color.White else RetailTextPrimary)
                        }
                    }
                    Surface(
                        onClick = {
                            isOneTimeTask = true
                            isEveryDay = false
                        },
                        shape = RoundedCornerShape(4.dp),
                        color = if (isOneTimeTask) Color(0xFF6B21A8) else Color(0xFFF3E8FF),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isOneTimeTask) Color(0xFF6B21A8) else Color(0xFFD8B4FE)),
                        modifier = Modifier.weight(1.2f).height(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("★ ONE-TIME", fontSize = 9.sp, fontWeight = FontWeight.Black, color = if (isOneTimeTask) Color.White else Color(0xFF6B21A8))
                        }
                    }
                }

                if (!isOneTimeTask) {
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DaysOfWeek.ORDERED_DAYS.forEach { dayName ->
                            val short = DaysOfWeek.SHORT_NAMES[dayName] ?: dayName.take(3)
                            val isSelected = isEveryDay || selectedDays.contains(dayName)
                            Surface(
                                onClick = {
                                    isOneTimeTask = false
                                    if (isEveryDay) {
                                        isEveryDay = false
                                        selectedDays = setOf(dayName)
                                    } else {
                                        val next = if (selectedDays.contains(dayName)) {
                                            if (selectedDays.size > 1) selectedDays - dayName else selectedDays
                                        } else {
                                            selectedDays + dayName
                                        }
                                        if (next.size == 7) {
                                            isEveryDay = true
                                            selectedDays = DaysOfWeek.ORDERED_DAYS.toSet()
                                        } else {
                                            selectedDays = next
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(4.dp),
                                color = if (isSelected) RetailOrange else RetailSurfaceSubtle,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) RetailOrange else RetailBorder),
                                modifier = Modifier.weight(1f).height(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = short,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else RetailTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = if (isOneTimeTask)
                        "★ One-time task: Rolls over to subsequent days until completed. Never repeats weekly."
                    else if (isEveryDay || selectedDays.size >= 7)
                        "• Repeats every day of the week"
                    else
                        "• Repeats every week on: " + DaysOfWeek.formatDisplayDays(selectedDays.joinToString(",")),
                    fontSize = 10.sp,
                    fontWeight = if (isOneTimeTask) FontWeight.Bold else FontWeight.Normal,
                    color = if (isOneTimeTask) Color(0xFF6B21A8) else RetailOrangeDark,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Priority Selection
                Text("Priority Level:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RetailTextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(TaskPriority.LOW, TaskPriority.MEDIUM, TaskPriority.HIGH).forEach { p ->
                        val isSelected = priority == p
                        val activeColor = when (p) {
                            TaskPriority.HIGH -> RetailRedMissed
                            TaskPriority.LOW -> RetailHeaderBlackLight
                            else -> RetailOrange
                        }
                        Surface(
                            onClick = { priority = p },
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected) activeColor else RetailSurfaceSubtle,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) activeColor else RetailBorder),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = p,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else RetailTextPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = bullets,
                    onValueChange = { bullets = it },
                    label = { Text("Bullet instructions") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("CANCEL") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                val finalDays = if (isOneTimeTask) DaysOfWeek.ONE_TIME else if (isEveryDay || selectedDays.size >= 7) DaysOfWeek.ALL else selectedDays.joinToString(",")
                                onSave(task.copy(title = title, bulletPoints = bullets, priority = priority, categoryId = selectedCatId, daysOfWeek = finalDays, isOneTime = isOneTimeTask))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RetailOrange)
                    ) {
                        Text("SAVE")
                    }
                }
            }
        }
    }
}
}

// --- LOGS / COMPLETIONS & TIMESTAMP PHOTOS (Admin audit view of completed tasks, timestamps, employee IDs, and photo proofs) ---
enum class LogsSubTab {
    AUDIT_TABLE,
    PHOTO_GALLERY
}

enum class PhotoPresenceFilter(val label: String) {
    ALL("ALL RECORDS"),
    WITH_PHOTO("📷 WITH PHOTO PROOF"),
    WITHOUT_PHOTO("NO PHOTO")
}

data class ViewingPhotoState(
    val photos: List<TaskPhotoEntity>,
    val currentIndex: Int = 0,
    val taskTitle: String,
    val categoryName: String,
    val shiftDate: String,
    val completionTimestamp: Long? = null
)

@Composable
fun CompletionsTableContent(
    allCompletions: List<TaskCompletionEntity>,
    users: List<UserEntity>,
    allPhotos: List<TaskPhotoEntity> = emptyList(),
    tasks: List<TaskEntity> = emptyList(),
    categories: List<CategoryEntity> = emptyList()
) {
    val context = LocalContext.current
    var currentSubTab by remember { mutableStateOf(LogsSubTab.AUDIT_TABLE) }
    var photoPresenceFilter by remember { mutableStateOf(PhotoPresenceFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedUserIdFilter by remember { mutableStateOf<Long?>(null) }
    var sortByNewest by remember { mutableStateOf(true) }

    // State for viewing full-size photo detail modal with multi-photo support
    var viewingPhotoState by remember { mutableStateOf<ViewingPhotoState?>(null) }
    var viewingBreakdownCompletion by remember { mutableStateOf<TaskCompletionEntity?>(null) }

    val timestampFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val timeOnlyFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val taskMap = remember(tasks) { tasks.associateBy { it.id } }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    // Filter and sort completions
    val filteredCompletions = remember(
        allCompletions,
        allPhotos,
        searchQuery,
        selectedUserIdFilter,
        sortByNewest,
        photoPresenceFilter
    ) {
        allCompletions.filter { comp ->
            val hasPhoto = allPhotos.any { it.taskId == comp.taskId && it.dateString == comp.dateString }
            val matchesPhotoFilter = when (photoPresenceFilter) {
                PhotoPresenceFilter.ALL -> true
                PhotoPresenceFilter.WITH_PHOTO -> hasPhoto
                PhotoPresenceFilter.WITHOUT_PHOTO -> !hasPhoto
            }
            val matchesUser = selectedUserIdFilter == null || comp.completedByUserId == selectedUserIdFilter
            val matchesQuery = searchQuery.isBlank() ||
                comp.taskTitle.contains(searchQuery, ignoreCase = true) ||
                comp.categoryName.contains(searchQuery, ignoreCase = true) ||
                comp.completedByUserName.contains(searchQuery, ignoreCase = true) ||
                comp.completedByUserId.toString() == searchQuery.trim() ||
                "EMP-${comp.completedByUserId}".contains(searchQuery, ignoreCase = true) ||
                comp.dateString.contains(searchQuery, ignoreCase = true)
            matchesPhotoFilter && matchesUser && matchesQuery
        }.sortedWith(
            if (sortByNewest) compareByDescending { it.completedAt } else compareBy { it.completedAt }
        )
    }

    // Filter and sort photos
    val filteredPhotos = remember(allPhotos, searchQuery, selectedUserIdFilter, taskMap) {
        allPhotos.filter { photo ->
            val matchesUser = selectedUserIdFilter == null || photo.authorUserId == selectedUserIdFilter
            val task = taskMap[photo.taskId]
            val taskTitle = task?.title ?: "Task #${photo.taskId}"
            val catName = task?.categoryId?.let { categoryMap[it]?.name } ?: ""
            val matchesQuery = searchQuery.isBlank() ||
                taskTitle.contains(searchQuery, ignoreCase = true) ||
                catName.contains(searchQuery, ignoreCase = true) ||
                photo.authorName.contains(searchQuery, ignoreCase = true) ||
                photo.dateString.contains(searchQuery, ignoreCase = true) ||
                photo.authorUserId.toString() == searchQuery.trim()
            matchesUser && matchesQuery
        }.sortedByDescending { it.timestamp }
    }

    val totalRecordsWithPhotos = remember(allCompletions, allPhotos) {
        allCompletions.count { comp -> allPhotos.any { it.taskId == comp.taskId && it.dateString == comp.dateString } }
    }
    val compliancePercent = remember(allCompletions, totalRecordsWithPhotos) {
        if (allCompletions.isNotEmpty()) (totalRecordsWithPhotos * 100) / allCompletions.size else 0
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Summary Metrics Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = RetailSurface),
            border = BorderStroke(1.dp, RetailBorder),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = RetailGreenCheck,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ADMIN AUDIT LOGS & TIME STAMP PROOFS",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = RetailTextPrimary,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Text(
                        text = "Official compliance audit trail with permanent timestamps & photo evidence",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = RetailTextSecondary
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = RetailHeaderBlack,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${allCompletions.size} LOGS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = RetailOrange,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${allPhotos.size} PHOTOS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = RetailGreenCheck.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, RetailGreenCheck),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "$compliancePercent% VERIFIED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = RetailGreenCheck,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Sub-Tab Switcher: [ 📋 AUDIT TABLE & TIMESTAMPS ] vs [ 📷 TIMESTAMP PHOTOS GALLERY ]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = { currentSubTab = LogsSubTab.AUDIT_TABLE },
                shape = RoundedCornerShape(6.dp),
                color = if (currentSubTab == LogsSubTab.AUDIT_TABLE) RetailOrange else RetailSurface,
                border = BorderStroke(1.dp, if (currentSubTab == LogsSubTab.AUDIT_TABLE) RetailOrange else RetailBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .testTag("admin_logs_table_tab")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.TableChart,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (currentSubTab == LogsSubTab.AUDIT_TABLE) Color.White else RetailTextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AUDIT TABLE (${filteredCompletions.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (currentSubTab == LogsSubTab.AUDIT_TABLE) Color.White else RetailTextPrimary,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Surface(
                onClick = { currentSubTab = LogsSubTab.PHOTO_GALLERY },
                shape = RoundedCornerShape(6.dp),
                color = if (currentSubTab == LogsSubTab.PHOTO_GALLERY) RetailOrange else RetailSurface,
                border = BorderStroke(1.dp, if (currentSubTab == LogsSubTab.PHOTO_GALLERY) RetailOrange else RetailBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .testTag("admin_logs_photos_tab")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (currentSubTab == LogsSubTab.PHOTO_GALLERY) Color.White else RetailTextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "TIMESTAMP PHOTOS (${filteredPhotos.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (currentSubTab == LogsSubTab.PHOTO_GALLERY) Color.White else RetailTextPrimary,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Search Bar & Sort Toggle Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search task, employee ID, or shift date...", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("admin_audit_search"),
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = RetailTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RetailOrange,
                    unfocusedBorderColor = RetailBorder
                )
            )

            // Sort Toggle Button
            OutlinedButton(
                onClick = { sortByNewest = !sortByNewest },
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, RetailBorder),
                modifier = Modifier.height(52.dp)
            ) {
                Icon(
                    imageVector = if (sortByNewest) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = "Sort order",
                    tint = RetailTextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (sortByNewest) "NEWEST" else "OLDEST",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = RetailTextPrimary
                )
            }
        }

        // Secondary Filters: Photo Presence Toggle & Employee Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Photo Filter Chips (When in Audit Table mode)
            if (currentSubTab == LogsSubTab.AUDIT_TABLE) {
                Text(
                    text = "PHOTOS:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = RetailTextSecondary
                )

                PhotoPresenceFilter.values().forEach { filter ->
                    val isSelected = photoPresenceFilter == filter
                    Surface(
                        onClick = { photoPresenceFilter = filter },
                        shape = RoundedCornerShape(4.dp),
                        color = if (isSelected) RetailHeaderBlack else RetailSurface,
                        border = BorderStroke(1.dp, if (isSelected) RetailHeaderBlack else RetailBorder)
                    ) {
                        val count = when (filter) {
                            PhotoPresenceFilter.ALL -> allCompletions.size
                            PhotoPresenceFilter.WITH_PHOTO -> totalRecordsWithPhotos
                            PhotoPresenceFilter.WITHOUT_PHOTO -> allCompletions.size - totalRecordsWithPhotos
                        }
                        Text(
                            text = "${filter.label} ($count)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else RetailTextPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.width(1.dp).height(20.dp).background(RetailBorder))
                Spacer(modifier = Modifier.width(8.dp))
            }

            Text(
                text = "EMPLOYEE:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = RetailTextSecondary
            )

            val isAllSelected = selectedUserIdFilter == null
            Surface(
                onClick = { selectedUserIdFilter = null },
                shape = RoundedCornerShape(4.dp),
                color = if (isAllSelected) RetailOrange else RetailSurface,
                border = BorderStroke(1.dp, if (isAllSelected) RetailOrange else RetailBorder)
            ) {
                Text(
                    text = "ALL EMPLOYEES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAllSelected) Color.White else RetailTextPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            users.forEach { user ->
                val isSelected = selectedUserIdFilter == user.id
                Surface(
                    onClick = { selectedUserIdFilter = if (isSelected) null else user.id },
                    shape = RoundedCornerShape(4.dp),
                    color = if (isSelected) RetailOrange else RetailSurface,
                    border = BorderStroke(1.dp, if (isSelected) RetailOrange else RetailBorder)
                ) {
                    Text(
                        text = "ID #${user.id} (${user.initials})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else RetailTextPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Main Content Area based on SubTab
        when (currentSubTab) {
            LogsSubTab.AUDIT_TABLE -> {
                if (filteredCompletions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(RetailSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, RetailBorder, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = RetailTextSecondary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No task completions match this filter.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RetailTextSecondary
                            )
                        }
                    }
                } else {
                    // Horizontal scrollable table container for pristine columns
                    val tableScrollState = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(1.dp, RetailHeaderBlack, RoundedCornerShape(4.dp))
                            .clip(RoundedCornerShape(4.dp))
                    ) {
                        // Table Header Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(RetailHeaderBlack)
                                .horizontalScroll(tableScrollState)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TASK / CATEGORY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.sp,
                                modifier = Modifier.width(200.dp)
                            )
                            Text(
                                text = "COMPLETION TIMESTAMP",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.sp,
                                modifier = Modifier.width(160.dp)
                            )
                            Text(
                                text = "EMPLOYEE ID & NAME",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.sp,
                                modifier = Modifier.width(180.dp)
                            )
                            Text(
                                text = "SUB-TASKS AUDIT BREAKDOWN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.sp,
                                modifier = Modifier.width(260.dp)
                            )
                            Text(
                                text = "TIMESTAMP PHOTO PROOFS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.sp,
                                modifier = Modifier.width(220.dp)
                            )
                            Text(
                                text = "STATUS / VERIFICATION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.sp,
                                modifier = Modifier.width(140.dp)
                            )
                        }

                        // Table Rows
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(RetailSurface)
                        ) {
                            items(filteredCompletions) { comp ->
                                val rowPhotos = allPhotos.filter { it.taskId == comp.taskId && it.dateString == comp.dateString }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(tableScrollState)
                                        .background(RetailSurface)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Column 1: Task / Category
                                    Column(modifier = Modifier.width(210.dp)) {
                                        Text(
                                            text = comp.taskTitle,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            color = RetailTextPrimary
                                        )
                                        Text(
                                            text = comp.categoryName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RetailOrange
                                        )
                                        Text(
                                            text = "Shift Date: ${comp.dateString}",
                                            fontSize = 10.sp,
                                            color = RetailTextSecondary
                                        )
                                    }

                                    // Column 2: Completion Timestamp
                                    Column(modifier = Modifier.width(170.dp)) {
                                        Text(
                                            text = timestampFormat.format(Date(comp.completedAt)),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RetailTextPrimary
                                        )
                                        Text(
                                            text = "Ref ID: ${comp.completedAt}",
                                            fontSize = 10.sp,
                                            color = RetailTextSecondary
                                        )
                                    }

                                    // Column 3: Employee ID & Name
                                    Row(
                                        modifier = Modifier.width(190.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(RetailHeaderBlack, RoundedCornerShape(4.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = comp.completedByUserInitials,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(RetailHeaderBlack, RoundedCornerShape(2.dp))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "ID: #${comp.completedByUserId}",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = Color.White
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = comp.completedByUserName,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = RetailTextPrimary
                                                )
                                            }
                                            Text(
                                                text = "Role: ${comp.completedByUserRole}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = RetailTextSecondary
                                            )
                                        }
                                    }

                                    // Column 4: Sub-Tasks Audit Breakdown (Shows what staff completed and what was left outstanding)
                                    val completedSubTasks = remember(comp.completedSubTasksJson) {
                                        SubTaskUtils.jsonToList(comp.completedSubTasksJson)
                                    }
                                    val outstandingSubTasks = remember(comp.outstandingSubTasksJson) {
                                        SubTaskUtils.jsonToList(comp.outstandingSubTasksJson)
                                    }

                                    Column(
                                        modifier = Modifier.width(260.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        if (comp.isPartial) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(RetailOrange.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "⚡ PARTIAL (${comp.completedSubTasksCount}/${comp.totalSubTasksCount})",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = RetailOrange
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "${outstandingSubTasks.size} OUTSTANDING",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = RetailRedMissed
                                                )
                                            }
                                            if (completedSubTasks.isNotEmpty()) {
                                                Text(
                                                    text = "✓ Done: ${completedSubTasks.joinToString(", ")}",
                                                    fontSize = 10.sp,
                                                    color = RetailGreenCheck,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (outstandingSubTasks.isNotEmpty()) {
                                                Text(
                                                    text = "⏳ Left: ${outstandingSubTasks.joinToString(", ")}",
                                                    fontSize = 10.sp,
                                                    color = RetailRedMissed,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        } else {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(RetailGreenCheck.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "✓ FULL SIGN OFF (${comp.completedSubTasksCount}/${comp.totalSubTasksCount})",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = RetailGreenCheck
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "All ${comp.completedSubTasksCount} sub-tasks verified",
                                                fontSize = 9.sp,
                                                color = RetailTextSecondary
                                            )
                                        }

                                        Surface(
                                            onClick = { viewingBreakdownCompletion = comp },
                                            shape = RoundedCornerShape(2.dp),
                                            color = RetailHeaderBlack,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.ListAlt, contentDescription = null, tint = RetailOrange, modifier = Modifier.size(11.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("INSPECT SUB-TASKS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }

                                    // Column 5: Timestamp Photo Proofs (Supports viewing every photo!)
                                    Row(
                                        modifier = Modifier.width(220.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (rowPhotos.isNotEmpty()) {
                                            // Mini-gallery of thumbnails
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                rowPhotos.take(2).forEachIndexed { idx, photo ->
                                                    Box(
                                                        modifier = Modifier
                                                            .size(44.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .border(1.5.dp, RetailOrange, RoundedCornerShape(4.dp))
                                                            .clickable {
                                                                viewingPhotoState = ViewingPhotoState(
                                                                    photos = rowPhotos,
                                                                    currentIndex = idx,
                                                                    taskTitle = comp.taskTitle,
                                                                    categoryName = comp.categoryName,
                                                                    shiftDate = comp.dateString,
                                                                    completionTimestamp = comp.completedAt
                                                                )
                                                            }
                                                    ) {
                                                        AsyncImage(
                                                            model = photo.photoUri,
                                                            contentDescription = "Audit proof photo",
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    }
                                                }

                                                if (rowPhotos.size > 2) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(44.dp)
                                                            .background(RetailHeaderBlack, RoundedCornerShape(4.dp))
                                                            .clickable {
                                                                viewingPhotoState = ViewingPhotoState(
                                                                    photos = rowPhotos,
                                                                    currentIndex = 2,
                                                                    taskTitle = comp.taskTitle,
                                                                    categoryName = comp.categoryName,
                                                                    shiftDate = comp.dateString,
                                                                    completionTimestamp = comp.completedAt
                                                                )
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "+${rowPhotos.size - 2}",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Black,
                                                            color = RetailOrange
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Column {
                                                Text(
                                                    text = "📷 ${rowPhotos.size} ${if (rowPhotos.size == 1) "PHOTO" else "PHOTOS"}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = RetailOrange
                                                )
                                                Text(
                                                    text = timeOnlyFormat.format(Date(rowPhotos.first().timestamp)),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = RetailTextPrimary
                                                )
                                                Surface(
                                                    onClick = {
                                                        viewingPhotoState = ViewingPhotoState(
                                                            photos = rowPhotos,
                                                            currentIndex = 0,
                                                            taskTitle = comp.taskTitle,
                                                            categoryName = comp.categoryName,
                                                            shiftDate = comp.dateString,
                                                            completionTimestamp = comp.completedAt
                                                        )
                                                    },
                                                    color = RetailHeaderBlack,
                                                    shape = RoundedCornerShape(2.dp),
                                                    modifier = Modifier.padding(top = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "VIEW PROOF",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .background(RetailSurfaceSubtle, RoundedCornerShape(3.dp))
                                                    .border(1.dp, RetailBorder, RoundedCornerShape(3.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "NO PHOTO ATTACHED",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = RetailTextSecondary
                                                )
                                            }
                                        }
                                    }

                                    // Column 6: Status / Verification
                                    Column(modifier = Modifier.width(140.dp)) {
                                        if (comp.isPartial) {
                                            Box(
                                                modifier = Modifier
                                                    .background(RetailOrange.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "⚡ PARTIAL",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = RetailOrange
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${comp.totalSubTasksCount - comp.completedSubTasksCount} Left Outstanding",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = RetailRedMissed
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .background(RetailGreenCheck.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "✓ COMPLETED",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = RetailGreenCheck
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Compliance locked",
                                                fontSize = 9.sp,
                                                color = RetailTextSecondary
                                            )
                                        }
                                        Text(
                                            text = "Ref: #${comp.taskId}",
                                            fontSize = 9.sp,
                                            color = RetailTextSecondary
                                        )
                                    }
                                }
                                HorizontalDivider(color = RetailBorder, thickness = 1.dp)
                            }
                        }
                    }
                }
            }

            LogsSubTab.PHOTO_GALLERY -> {
                // Photo Proofs Dedicated Gallery View
                if (filteredPhotos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(RetailSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, RetailBorder, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = RetailTextSecondary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No audit timestamp photos found.",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = RetailTextPrimary
                            )
                            Text(
                                text = "Photos captured or generated during task completion appear here with verified timestamps.",
                                fontSize = 12.sp,
                                color = RetailTextSecondary
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 260.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredPhotos) { photo ->
                            val task = taskMap[photo.taskId]
                            val taskTitle = task?.title ?: "Task #${photo.taskId}"
                            val catName = task?.categoryId?.let { categoryMap[it]?.name } ?: "GENERAL"

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, RetailBorder, RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewingPhotoState = ViewingPhotoState(
                                            photos = listOf(photo),
                                            currentIndex = 0,
                                            taskTitle = taskTitle,
                                            categoryName = catName,
                                            shiftDate = photo.dateString
                                        )
                                    },
                                colors = CardDefaults.cardColors(containerColor = RetailSurface)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Image with timestamp overlay banner
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(170.dp)
                                            .background(Color.Black)
                                    ) {
                                        AsyncImage(
                                            model = photo.photoUri,
                                            contentDescription = "Audit stamp photo",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )

                                        // Top Badge: AUDIT VERIFIED
                                        Surface(
                                            color = RetailHeaderBlack.copy(alpha = 0.88f),
                                            shape = RoundedCornerShape(bottomEnd = 4.dp),
                                            modifier = Modifier.align(Alignment.TopStart)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.Verified,
                                                    contentDescription = null,
                                                    tint = RetailGreenCheck,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "AUDIT VERIFIED",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White
                                                )
                                            }
                                        }

                                        // Bottom Timestamp Banner
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .background(RetailHeaderBlack.copy(alpha = 0.88f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "🕒 ${timestampFormat.format(Date(photo.timestamp))}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = RetailGreenCheck
                                                )
                                                Text(
                                                    text = photo.dateString,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }

                                    // Card Body Details
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = taskTitle,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            color = RetailTextPrimary,
                                            maxLines = 1
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(RetailHeaderBlack, RoundedCornerShape(2.dp))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "ID: #${photo.authorUserId}",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = Color.White
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = photo.authorName,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = RetailTextPrimary
                                                )
                                            }

                                            Surface(
                                                onClick = {
                                                    viewingPhotoState = ViewingPhotoState(
                                                        photos = listOf(photo),
                                                        currentIndex = 0,
                                                        taskTitle = taskTitle,
                                                        categoryName = catName,
                                                        shiftDate = photo.dateString
                                                    )
                                                },
                                                color = RetailOrange,
                                                shape = RoundedCornerShape(3.dp)
                                            ) {
                                                Text(
                                                    text = "VIEW FULL",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog to inspect full-size photo with detailed timestamp verification data & multi-photo browsing
    viewingPhotoState?.let { state ->
        val activePhoto = state.photos.getOrNull(state.currentIndex) ?: state.photos.firstOrNull()

        Dialog(onDismissRequest = { viewingPhotoState = null }) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, RetailBorder, RoundedCornerShape(12.dp)),
                color = RetailSurface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    // Dialog Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = null,
                                tint = RetailGreenCheck,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AUDIT TIMESTAMP PHOTO PROOF",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = RetailTextPrimary,
                                letterSpacing = 0.5.sp
                            )
                        }

                        IconButton(
                            onClick = { viewingPhotoState = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = RetailTextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (activePhoto != null) {
                        // Big Image View
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 220.dp, max = 340.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, RetailBorder, RoundedCornerShape(8.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = activePhoto.photoUri,
                                contentDescription = "Full audit photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )

                            // Top Watermark Banner
                            Surface(
                                color = RetailHeaderBlack.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(bottomEnd = 4.dp),
                                modifier = Modifier.align(Alignment.TopStart)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = RetailGreenCheck,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "TIME STAMP VERIFIED PROOF",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Multi-Photo Navigation controls (if > 1 photo in set)
                        if (state.photos.size > 1) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val prev = if (state.currentIndex > 0) state.currentIndex - 1 else state.photos.size - 1
                                        viewingPhotoState = state.copy(currentIndex = prev)
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Previous photo", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PREV", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                Text(
                                    text = "PHOTO ${state.currentIndex + 1} OF ${state.photos.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = RetailOrange
                                )

                                OutlinedButton(
                                    onClick = {
                                        val next = if (state.currentIndex < state.photos.size - 1) state.currentIndex + 1 else 0
                                        viewingPhotoState = state.copy(currentIndex = next)
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("NEXT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowForward, contentDescription = "Next photo", modifier = Modifier.size(14.dp))
                                }
                            }

                            // Thumbnails selector row
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                state.photos.forEachIndexed { idx, p ->
                                    val isSelected = idx == state.currentIndex
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) RetailOrange else RetailBorder,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .clickable {
                                                viewingPhotoState = state.copy(currentIndex = idx)
                                            }
                                    ) {
                                        AsyncImage(
                                            model = p.photoUri,
                                            contentDescription = "Thumbnail $idx",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Verification Metadata Box
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(RetailSurfaceSubtle, RoundedCornerShape(6.dp))
                                .border(1.dp, RetailBorder, RoundedCornerShape(6.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "PHOTO TIMESTAMP:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RetailTextSecondary)
                                Text(
                                    text = timestampFormat.format(Date(activePhoto.timestamp)),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = RetailGreenCheck
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "TASK ASSIGNMENT:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RetailTextSecondary)
                                Text(text = state.taskTitle, fontSize = 11.sp, fontWeight = FontWeight.Black, color = RetailTextPrimary)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "CATEGORY:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RetailTextSecondary)
                                Text(text = state.categoryName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RetailOrange)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "SUBMITTED BY:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RetailTextSecondary)
                                Text(
                                    text = "${activePhoto.authorName} (Employee ID #${activePhoto.authorUserId})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = RetailTextPrimary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "SHIFT DATE:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RetailTextSecondary)
                                Text(text = activePhoto.dateString, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RetailTextPrimary)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "AUDIT VERIFICATION:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RetailTextSecondary)
                                Text(text = "✓ COMPLIANCE PROOF STORED", fontSize = 11.sp, fontWeight = FontWeight.Black, color = RetailOrange)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Close Button
                    Button(
                        onClick = { viewingPhotoState = null },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RetailHeaderBlack)
                    ) {
                        Text(
                            text = "CLOSE AUDIT VIEWER",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // Modal Dialog to inspect Sub-Tasks breakdown (completed vs outstanding)
    viewingBreakdownCompletion?.let { comp ->
        SubTasksAuditBreakdownDialog(
            completion = comp,
            task = taskMap[comp.taskId],
            onDismiss = { viewingBreakdownCompletion = null }
        )
    }
}

@Composable
fun SubTasksAuditBreakdownDialog(
    completion: TaskCompletionEntity,
    task: TaskEntity?,
    onDismiss: () -> Unit
) {
    val timestampFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val completedSubTasks = remember(completion.completedSubTasksJson) {
        SubTaskUtils.jsonToList(completion.completedSubTasksJson)
    }
    val outstandingSubTasks = remember(completion.outstandingSubTasksJson) {
        SubTaskUtils.jsonToList(completion.outstandingSubTasksJson)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = RetailSurface,
                border = BorderStroke(1.dp, RetailBorder),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (completion.isPartial) {
                                    Box(
                                        modifier = Modifier
                                            .background(RetailOrange, RoundedCornerShape(3.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "⚡ PARTIAL COMPLETION",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .background(RetailGreenCheck, RoundedCornerShape(3.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "✓ FULL COMPLIANCE SIGN-OFF",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = completion.taskTitle,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = RetailTextPrimary
                            )
                            Text(
                                text = "Category: ${completion.categoryName}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RetailOrange
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = RetailTextSecondary)
                        }
                    }

                    HorizontalDivider(color = RetailBorder, thickness = 1.dp)

                    // Sign-off Staff & Timestamp Info Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = RetailSurfaceSubtle),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, RetailBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("SIGNED OFF BY:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RetailTextSecondary)
                                Text(
                                    "[${completion.completedByUserInitials}] ${completion.completedByUserName} (ID #${completion.completedByUserId})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RetailTextPrimary
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("USER ROLE:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RetailTextSecondary)
                                Text(completion.completedByUserRole, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RetailTextPrimary)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("SHIFT DATE:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RetailTextSecondary)
                                Text(completion.dateString, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RetailTextPrimary)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("SIGN-OFF TIMESTAMP:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RetailTextSecondary)
                                Text(timestampFormat.format(Date(completion.completedAt)), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RetailTextPrimary)
                            }
                        }
                    }

                    // Section 1: Completed Sub-Parts
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RetailGreenCheck, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "COMPLETED SUB-TASKS (${completedSubTasks.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = RetailGreenCheck,
                                letterSpacing = 0.5.sp
                            )
                        }

                        if (completedSubTasks.isEmpty()) {
                            Text("No sub-tasks were ticked off.", fontSize = 11.sp, color = RetailTextSecondary)
                        } else {
                            completedSubTasks.forEach { subTask ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFE8F5E9), RoundedCornerShape(4.dp))
                                        .border(1.dp, Color(0xFFA5D6A7), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = RetailGreenCheck, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = subTask,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1B5E20),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFC8E6C9), RoundedCornerShape(2.dp))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text("DONE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    }
                                }
                            }
                        }
                    }

                    // Section 2: Outstanding Sub-Parts
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (outstandingSubTasks.isEmpty()) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (outstandingSubTasks.isEmpty()) RetailGreenCheck else RetailRedMissed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (outstandingSubTasks.isEmpty()) "ALL SUB-TASKS VERIFIED (0 OUTSTANDING)" else "LEFT OUTSTANDING (${outstandingSubTasks.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = if (outstandingSubTasks.isEmpty()) RetailGreenCheck else RetailRedMissed,
                                letterSpacing = 0.5.sp
                            )
                        }

                        if (outstandingSubTasks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(RetailSurfaceSubtle, RoundedCornerShape(4.dp))
                                    .border(1.dp, RetailBorder, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "✓ Complete task finished. Zero sub-tasks were left outstanding.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RetailGreenCheck
                                )
                            }
                        } else {
                            Text(
                                text = "Staff submitted partial sign-off. The following sub-tasks were left incomplete:",
                                fontSize = 10.sp,
                                color = RetailRedMissed,
                                fontWeight = FontWeight.SemiBold
                            )

                            outstandingSubTasks.forEach { subTask ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFFFEBEE), RoundedCornerShape(4.dp))
                                        .border(1.dp, Color(0xFFFFCDD2), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.HourglassTop, contentDescription = null, tint = RetailRedMissed, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = subTask,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB71C1C),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFFFCDD2), RoundedCornerShape(2.dp))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text("OUTSTANDING", fontSize = 9.sp, fontWeight = FontWeight.Black, color = RetailRedMissed)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RetailHeaderBlack),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("CLOSE AUDIT BREAKDOWN", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
            }
        }
    }
}




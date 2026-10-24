package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TaskCompletionEntity
import com.example.ui.theme.*
import com.example.util.SubTaskUtils
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    allCompletions: List<TaskCompletionEntity>,
    selectedDate: String,
    onSelectDate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var filterStaff by remember { mutableStateOf<String?>(null) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

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

    val completionsOnDate = remember(allCompletions, selectedDate, filterStaff) {
        allCompletions.filter { it.dateString == selectedDate }
            .filter { filterStaff == null || it.completedByUserName == filterStaff }
            .sortedByDescending { it.completedAt }
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
                // Left Master Column: Date Selector & Day Summary (~320dp)
                Card(
                    modifier = Modifier
                        .widthIn(min = 280.dp, max = 320.dp)
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = RetailSurface),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RetailBorder)
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
                                Surface(
                                    onClick = { onSelectDate(dateStr) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) RetailHeaderBlack else RetailSurfaceSubtle,
                                    border = androidx.compose.foundation.BorderStroke(
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

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = RetailBorder, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Summary Statistics Card
                        Card(
                            shape = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(containerColor = RetailSurfaceSubtle),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RetailBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "DAY COMPLIANCE SUMMARY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = RetailTextSecondary,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${completionsOnDate.size} TASKS COMPLETED",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = RetailTextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${distinctStaffOnDate.size} STAFF MEMBERS ACTIVE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RetailOrange
                                )
                            }
                        }
                    }
                }

                // Right Detail Column: Expanded Audit Log Table (weight = 1f)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = RetailSurface),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RetailBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Header with Staff Filter Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "AUDIT TRAIL • $selectedDate",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = RetailTextPrimary,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "EXACT TIMESTAMPS & EMPLOYEE VERIFICATION",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RetailTextSecondary
                                )
                            }

                            // Staff filter chips
                            if (distinctStaffOnDate.size > 1) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        onClick = { filterStaff = null },
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (filterStaff == null) RetailOrange else RetailSurfaceSubtle,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, RetailBorder)
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
                                            border = androidx.compose.foundation.BorderStroke(1.dp, RetailBorder)
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
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = RetailBorder, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Completions Table
                        if (completionsOnDate.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No completions recorded for $selectedDate.",
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
                                items(completionsOnDate) { comp ->
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

                                        // Exact time & Status
                                        Column(horizontalAlignment = Alignment.End) {
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
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Compact Phone Fallback
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "SHIFT AUDIT LOG",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = RetailTextPrimary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "COMPLETE RECORD OF TASK SIGN-OFFS & STAFF COMPLIANCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = RetailTextSecondary,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = "SELECT SHIFT DATE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = RetailTextSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableDates.take(4).forEach { dateStr ->
                        val isSelected = dateStr == selectedDate
                        Surface(
                            onClick = { onSelectDate(dateStr) },
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected) RetailHeaderBlack else RetailSurface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) RetailHeaderBlack else RetailBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else RetailTextPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = dateStr,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else RetailTextPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (distinctStaffOnDate.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STAFF:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RetailTextSecondary
                        )
                        Surface(
                            onClick = { filterStaff = null },
                            shape = RoundedCornerShape(4.dp),
                            color = if (filterStaff == null) RetailOrange else RetailSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, RetailBorder)
                        ) {
                            Text(
                                text = "ALL",
                                fontSize = 11.sp,
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
                                color = if (isSelected) RetailOrange else RetailSurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, RetailBorder)
                            ) {
                                Text(
                                    text = staffName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else RetailTextPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Summary Card for selected date
                Card(
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = RetailSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RetailBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "TOTAL SIGN-OFFS ON $selectedDate",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RetailTextSecondary
                            )
                            Text(
                                text = "${completionsOnDate.size} TASKS COMPLETED",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = RetailTextPrimary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(RetailGreenCheck.copy(alpha = 0.15f), RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = RetailGreenCheck,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (completionsOnDate.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(RetailSurface, RoundedCornerShape(4.dp))
                            .border(1.dp, RetailBorder, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No completions recorded for $selectedDate yet.",
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
                        items(completionsOnDate) { comp ->
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

                                Column(horizontalAlignment = Alignment.End) {
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
                                            else -> "LOGGED"
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            comp.isSkipped -> Color(0xFFE65100)
                                            comp.isPartial -> RetailOrange
                                            else -> RetailGreenCheck
                                        }
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

package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.data.ShiftHandoverEntity
import com.example.data.UserEntity
import com.example.ui.components.ShiftHandoverDialog
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

enum class HandoverFilter(val label: String) {
    ALL("ALL NOTES"),
    URGENT("URGENT / IMPORTANT"),
    MORNING_TO_EVENING("MORNING ➔ EVENING"),
    EVENING_TO_MORNING("EVENING ➔ MORNING"),
    STOCK("STOCK & DELIVERIES"),
    MAINTENANCE("MAINTENANCE")
}

@Composable
fun HandoverScreen(
    currentUser: UserEntity,
    handovers: List<ShiftHandoverEntity>,
    users: List<UserEntity>,
    onAddShiftHandover: (targetDate: String, shiftType: String, category: String, priority: String, message: String, authorUser: UserEntity) -> Unit,
    onAcknowledgeShiftHandover: (String) -> Unit,
    onDeleteShiftHandover: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var currentFilter by remember { mutableStateOf(HandoverFilter.ALL) }
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    val filteredList = remember(handovers, currentFilter) {
        val sorted = handovers.sortedByDescending { it.timestamp }
        when (currentFilter) {
            HandoverFilter.ALL -> sorted
            HandoverFilter.URGENT -> sorted.filter { it.priority == "URGENT" || it.priority == "IMPORTANT" }
            HandoverFilter.MORNING_TO_EVENING -> sorted.filter { it.shiftType == "MORNING_TO_EVENING" }
            HandoverFilter.EVENING_TO_MORNING -> sorted.filter { it.shiftType == "EVENING_TO_MORNING" }
            HandoverFilter.STOCK -> sorted.filter { it.category == "STOCK_DELIVERY" }
            HandoverFilter.MAINTENANCE -> sorted.filter { it.category == "MAINTENANCE" }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RetailBackground)
            .padding(16.dp)
    ) {
        // Top Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = RetailOrange,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SHIFT HANDOVER NOTICEBOARD",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = RetailTextPrimary,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${handovers.size} total shift handover notes recorded",
                    fontSize = 12.sp,
                    color = RetailTextSecondary
                )
            }

            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = RetailOrange),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("leave_handover_note_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("LEAVE NOTE", fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Filter Pills Row
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HandoverFilter.values().forEach { filter ->
                    val isSelected = currentFilter == filter
                    Surface(
                        onClick = { currentFilter = filter },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) RetailHeaderBlack else Color.White,
                        border = BorderStroke(1.dp, if (isSelected) RetailHeaderBlack else Color(0xFFCBD5E1)),
                        modifier = Modifier.testTag("handover_filter_${filter.name}")
                    ) {
                        Text(
                            text = filter.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            color = if (isSelected) Color.White else RetailTextPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // List of Handover Cards
        if (filteredList.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = Color.White,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "NO HANDOVER NOTES FOUND",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = RetailTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Leave notes on stock deliveries, temps, defrosting, or tasks for incoming teams.",
                        fontSize = 12.sp,
                        color = RetailTextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = RetailOrange),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Leave First Note", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredList, key = { it.id }) { h ->
                    val isUrgent = h.priority == "URGENT"
                    val isImportant = h.priority == "IMPORTANT"
                    val cardBorder = when {
                        isUrgent -> Color(0xFFEF4444)
                        isImportant -> Color(0xFFF59E0B)
                        else -> Color(0xFFCBD5E1)
                    }
                    val cardBg = when {
                        isUrgent -> Color(0xFFFEF2F2)
                        isImportant -> Color(0xFFFFFBEB)
                        else -> Color.White
                    }

                    val shiftLabel = when (h.shiftType) {
                        "MORNING_TO_EVENING" -> "🌅 MORNING ➔ 🌆 EVENING"
                        "EVENING_TO_MORNING" -> "🌆 EVENING ➔ 🌅 NEXT MORNING"
                        "IMMEDIATE" -> "🚨 IMMEDIATE HANDOVER"
                        else -> "📋 GENERAL HANDOVER"
                    }

                    val catLabel = when (h.category) {
                        "STOCK_DELIVERY" -> "📦 Stock & Deliveries"
                        "FRIDGE_WASTE" -> "❄️ Temps & WhyWaste"
                        "MAINTENANCE" -> "🔧 Maintenance"
                        "CASH_SECURITY" -> "🔒 Cash & Security"
                        else -> "General"
                    }

                    val acks = remember(h.acknowledgedByCsv) {
                        if (h.acknowledgedByCsv.isBlank()) emptyList()
                        else h.acknowledgedByCsv.split(",").filter { it.isNotBlank() }
                    }
                    val hasAcked = currentUser.id.toString() in acks

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = RetailHeaderBlack,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = shiftLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }

                                    Text(
                                        text = catLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RetailTextSecondary
                                    )

                                    if (isUrgent) {
                                        Surface(
                                            color = Color(0xFFEF4444),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "URGENT",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else if (isImportant) {
                                        Surface(
                                            color = Color(0xFFF59E0B),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "IMPORTANT",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = try { timeFormat.format(Date(h.timestamp)) } catch (_: Exception) { "" },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RetailTextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Message Content
                            Text(
                                text = h.message,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = RetailTextPrimary,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Footer Row: Author & Acknowledge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Left by [${h.authorInitials}] ${h.authorName}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RetailTextPrimary
                                    )
                                    if (h.authorRole == "ADMIN") {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = Color(0xFFFEF3C7),
                                            shape = RoundedCornerShape(3.dp)
                                        ) {
                                            Text(
                                                text = "ADMIN",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFF92400E),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (hasAcked) {
                                        Surface(
                                            color = Color(0xFFDCFCE7),
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, Color(0xFF86EFAC))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "ACKNOWLEDGED (${acks.size})",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color(0xFF15803D)
                                                )
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = { onAcknowledgeShiftHandover(h.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = RetailOrange),
                                            shape = RoundedCornerShape(4.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(28.dp).testTag("ack_handover_${h.id}")
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("ACKNOWLEDGE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                     if (currentUser.role == "ADMIN" || h.authorUserId == currentUser.id.toString()) {
                                        IconButton(
                                            onClick = { onDeleteShiftHandover(h.id) },
                                            modifier = Modifier.size(28.dp).testTag("delete_handover_${h.id}")
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Note", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
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

    if (showAddDialog) {
        val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
        ShiftHandoverDialog(
            currentUser = currentUser,
            activeUsers = users.ifEmpty { listOf(currentUser) },
            selectedDate = todayStr,
            onDismiss = { showAddDialog = false },
            onSubmit = { targetShift, category, priority, message, authorUser ->
                onAddShiftHandover(todayStr, targetShift, category, priority, message, authorUser)
                showAddDialog = false
            }
        )
    }
}

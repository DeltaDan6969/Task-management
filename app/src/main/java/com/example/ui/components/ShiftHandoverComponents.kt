package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.ShiftHandoverEntity
import com.example.data.UserEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ShiftHandoverCard(
    handovers: List<ShiftHandoverEntity>,
    currentUser: UserEntity,
    selectedDate: String,
    onOpenLeaveNote: () -> Unit,
    onAcknowledgeNote: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Filter relevant handovers: for selectedDate or targetDate == selectedDate
    val relevant = remember(handovers, selectedDate) {
        handovers.filter { h ->
            h.dateString.isBlank() || h.dateString == selectedDate || h.targetDate == selectedDate
        }.sortedByDescending { it.timestamp }
    }

    val count = relevant.size
    val countPillText = if (count == 0) "No Handover Notes" else "$count Note${if (count == 1) "" else "s"} for Shift"
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = RetailSurface),
        border = BorderStroke(1.dp, RetailBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = RetailOrange,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "SHIFT HANDOVER",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = RetailTextPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Surface(
                        color = if (count > 0) RetailOrange.copy(alpha = 0.15f) else RetailSurfaceSubtle,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (count > 0) RetailOrange.copy(alpha = 0.3f) else RetailBorder)
                    ) {
                        Text(
                            text = countPillText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = if (count > 0) RetailOrange else RetailTextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Button(
                    onClick = onOpenLeaveNote,
                    colors = ButtonDefaults.buttonColors(containerColor = RetailOrange),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "+ Leave Note",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (relevant.isEmpty()) {
                Surface(
                    color = RetailSurfaceSubtle,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, RetailBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No Notes Left for Next Shift",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = RetailTextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Staff can leave notes on deliveries, stock, defrosting, or tasks for the incoming team.",
                            fontSize = 11.sp,
                            color = RetailTextSecondary
                        )
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    relevant.forEach { h ->
                        val isUrgent = h.priority == "URGENT"
                        val isImportant = h.priority == "IMPORTANT"
                        val itemBorderColor = when {
                            isUrgent -> Color(0xFFEF4444)
                            isImportant -> Color(0xFFF59E0B)
                            else -> RetailBorder
                        }
                        val itemBgColor = when {
                            isUrgent -> Color(0xFFFEF2F2)
                            isImportant -> Color(0xFFFFFBEB)
                            else -> RetailSurfaceSubtle
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
                        val ackCount = acks.size
                        val timeText = try {
                            timeFormat.format(Date(h.timestamp))
                        } catch (_: Exception) { "" }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = itemBgColor,
                            border = BorderStroke(1.dp, itemBorderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                // Top row: tags and time
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            color = RetailHeaderBlack,
                                            shape = RoundedCornerShape(3.dp)
                                        ) {
                                            Text(
                                                text = shiftLabel,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }

                                        Text(
                                            text = catLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RetailTextSecondary
                                        )

                                        if (isUrgent) {
                                            Surface(
                                                color = Color(0xFFEF4444),
                                                shape = RoundedCornerShape(3.dp)
                                            ) {
                                                Text(
                                                    text = "URGENT",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        } else if (isImportant) {
                                            Surface(
                                                color = Color(0xFFF59E0B),
                                                shape = RoundedCornerShape(3.dp)
                                            ) {
                                                Text(
                                                    text = "IMPORTANT",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = timeText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RetailTextSecondary
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Message content
                                Text(
                                    text = h.message,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = RetailTextPrimary,
                                    lineHeight = 16.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Footer: Author info & Actions
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${h.authorName} (${h.authorInitials})",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RetailTextPrimary
                                        )
                                        if (h.authorRole == "ADMIN") {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(
                                                color = Color(0xFFFEF3C7),
                                                shape = RoundedCornerShape(2.dp)
                                            ) {
                                                Text(
                                                    text = "ADMIN",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color(0xFF92400E),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Mark Seen / Seen Button
                                        Surface(
                                            onClick = { onAcknowledgeNote(h.id) },
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (hasAcked) Color(0xFFDCFCE7) else RetailSurface,
                                            border = BorderStroke(1.dp, if (hasAcked) Color(0xFF86EFAC) else RetailBorder)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (hasAcked) Icons.Default.DoneAll else Icons.Default.Done,
                                                    contentDescription = null,
                                                    tint = if (hasAcked) Color(0xFF166534) else RetailTextPrimary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = if (hasAcked) "Seen${if (ackCount > 0) " ($ackCount)" else ""}" else "Mark Seen",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (hasAcked) Color(0xFF166534) else RetailTextPrimary
                                                )
                                            }
                                        }

                                        // Delete Button
                                        if (currentUser.role == "ADMIN" || currentUser.id.toString() == h.authorUserId) {
                                            IconButton(
                                                onClick = { onDeleteNote(h.id) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete Handover Note",
                                                    tint = RetailTextSecondary,
                                                    modifier = Modifier.size(16.dp)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftHandoverDialog(
    currentUser: UserEntity,
    activeUsers: List<UserEntity>,
    selectedDate: String,
    onDismiss: () -> Unit,
    onSubmit: (targetShift: String, category: String, priority: String, message: String, author: UserEntity) -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var selectedShiftType by remember { mutableStateOf("MORNING_TO_EVENING") }
    var selectedCategory by remember { mutableStateOf("STOCK_DELIVERY") }
    var selectedPriority by remember { mutableStateOf("NORMAL") }
    var selectedAuthor by remember { mutableStateOf(currentUser) }
    var authorDropdownExpanded by remember { mutableStateOf(false) }

    val shiftOptions = listOf(
        "MORNING_TO_EVENING" to "🌅 Morning ➔ Evening",
        "EVENING_TO_MORNING" to "🌆 Evening ➔ Next Morning",
        "IMMEDIATE" to "🚨 Immediate Handover",
        "GENERAL" to "📋 General Handover"
    )

    val categoryOptions = listOf(
        "STOCK_DELIVERY" to "📦 Stock & Deliveries",
        "FRIDGE_WASTE" to "❄️ Temps & WhyWaste",
        "MAINTENANCE" to "🔧 Maintenance",
        "CASH_SECURITY" to "🔒 Cash & Security",
        "GENERAL" to "General"
    )

    val priorityOptions = listOf(
        "NORMAL" to ("Normal" to RetailBorder),
        "IMPORTANT" to ("Important" to Color(0xFFF59E0B)),
        "URGENT" to ("Urgent" to Color(0xFFEF4444))
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = RetailSurface,
            border = BorderStroke(1.dp, RetailBorder),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(RetailOrangeLight, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = RetailOrange,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "LEAVE SHIFT HANDOVER NOTE",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = RetailTextPrimary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "COMMUNICATION FOR INCOMING TEAM",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = RetailTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = RetailTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Author Selector
                Text(
                    text = "AUTHOR (STAFF MEMBER):",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = RetailTextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                ExposedDropdownMenuBox(
                    expanded = authorDropdownExpanded,
                    onExpandedChange = { authorDropdownExpanded = !authorDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = "${selectedAuthor.name} (${selectedAuthor.initials}) • ${selectedAuthor.role}",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = authorDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RetailOrange,
                            unfocusedBorderColor = RetailBorder
                        ),
                        shape = RoundedCornerShape(6.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = authorDropdownExpanded,
                        onDismissRequest = { authorDropdownExpanded = false }
                    ) {
                        activeUsers.forEach { user ->
                            DropdownMenuItem(
                                text = { Text("${user.name} (${user.initials}) • ${user.role}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                onClick = {
                                    selectedAuthor = user
                                    authorDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Target Shift
                Text(
                    text = "TARGET SHIFT:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = RetailTextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    shiftOptions.forEach { (typeKey, label) ->
                        val isSelected = selectedShiftType == typeKey
                        Surface(
                            onClick = { selectedShiftType = typeKey },
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected) RetailHeaderBlack else RetailSurfaceSubtle,
                            border = BorderStroke(1.dp, if (isSelected) RetailHeaderBlack else RetailBorder)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else RetailTextPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Category
                Text(
                    text = "CATEGORY:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = RetailTextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categoryOptions.forEach { (catKey, label) ->
                        val isSelected = selectedCategory == catKey
                        Surface(
                            onClick = { selectedCategory = catKey },
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected) RetailOrange else RetailSurfaceSubtle,
                            border = BorderStroke(1.dp, if (isSelected) RetailOrange else RetailBorder)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else RetailTextPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Priority
                Text(
                    text = "PRIORITY LEVEL:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = RetailTextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    priorityOptions.forEach { (prioKey, pair) ->
                        val (label, color) = pair
                        val isSelected = selectedPriority == prioKey
                        Surface(
                            onClick = { selectedPriority = prioKey },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) color.copy(alpha = 0.15f) else RetailSurfaceSubtle,
                            border = BorderStroke(1.5.dp, if (isSelected) color else RetailBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSelected) color else RetailTextPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Handover Message Textarea
                Text(
                    text = "HANDOVER INSTRUCTIONS & DETAILS:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = RetailTextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("E.g., Delivered 12 bread crates, 3 cases milk need putting away, fridge 4 temperature checked at 3.2°C...", fontSize = 11.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RetailOrange,
                        unfocusedBorderColor = RetailBorder
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Submit Button
                Button(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            onSubmit(selectedShiftType, selectedCategory, selectedPriority, messageText, selectedAuthor)
                        }
                    },
                    enabled = messageText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = RetailOrange),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "POST HANDOVER NOTE",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

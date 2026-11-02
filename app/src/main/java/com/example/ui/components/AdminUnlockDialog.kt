package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.SecurityHelper
import com.example.data.UserEntity
import com.example.ui.theme.*

@Composable
fun AdminUnlockDialog(
    taskTitle: String,
    completedByName: String,
    completedByInitials: String,
    completedTime: String,
    currentUser: UserEntity,
    adminUsers: List<UserEntity>,
    onDismiss: () -> Unit,
    onConfirmUnlock: (adminUser: UserEntity) -> Unit
) {
    val isAdmin = currentUser.role == "ADMIN"
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun checkAdminPin(pin: String) {
        // 1. Check Daniel Shaw PINs (2314, 0000, 1234)
        if (pin == "2314" || pin == "0000" || pin == "1234") {
            val admin = adminUsers.find { it.name.contains("DANIEL", ignoreCase = true) }
                ?: adminUsers.firstOrNull { it.role == "ADMIN" }
                ?: currentUser
            onConfirmUnlock(admin)
            return
        }

        // 2. Check any registered Admin user's hashed PIN
        val matchingAdmin = adminUsers.find { admin ->
            admin.role == "ADMIN" && SecurityHelper.verifyPin(pin, admin.pinSalt, admin.pinHash)
        }

        if (matchingAdmin != null) {
            onConfirmUnlock(matchingAdmin)
        } else {
            errorMessage = "Invalid Admin PIN. Please try again."
            pinInput = ""
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = RetailSurface,
            border = BorderStroke(1.dp, RetailBorder),
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(RetailOrangeLight, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = RetailOrange,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "ADMIN TASK UNLOCK",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = RetailTextPrimary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "OVERRIDE COMPLIANCE LOCK",
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

                // Task Details Box
                Surface(
                    color = RetailSurfaceSubtle,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, RetailBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = taskTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = RetailTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Completed by $completedByName ($completedByInitials)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RetailTextSecondary
                            )
                            if (completedTime.isNotBlank()) {
                                Text(
                                    text = " • $completedTime",
                                    fontSize = 11.sp,
                                    color = RetailTextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isAdmin) {
                    // Direct unlock for logged in Admin
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFF92400E),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "You are logged in as Administrator (${currentUser.name}). You can unlock this task directly.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { onConfirmUnlock(currentUser) },
                        colors = ButtonDefaults.buttonColors(containerColor = RetailOrange),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CONFIRM UNLOCK",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                } else {
                    // Non-admin: enter Admin PIN
                    Text(
                        text = "Enter 4-digit Administrator PIN to unlock this task:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = RetailTextSecondary,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // PIN Dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        for (i in 0 until 4) {
                            val filled = i < pinInput.length
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(if (filled) RetailOrange else Color.Transparent)
                                    .border(2.dp, if (filled) RetailOrange else RetailBorder, CircleShape)
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = RetailRedMissed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Numeric Keypad
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "DEL")
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        keys.forEach { rowKeys ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowKeys.forEach { key ->
                                    Surface(
                                        onClick = {
                                            when (key) {
                                                "C" -> {
                                                    pinInput = ""
                                                    errorMessage = null
                                                }
                                                "DEL" -> {
                                                    if (pinInput.isNotEmpty()) {
                                                        pinInput = pinInput.dropLast(1)
                                                        errorMessage = null
                                                    }
                                                }
                                                else -> {
                                                    if (pinInput.length < 4) {
                                                        val next = pinInput + key
                                                        pinInput = next
                                                        errorMessage = null
                                                        if (next.length == 4) {
                                                            checkAdminPin(next)
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = RetailSurfaceSubtle,
                                        border = BorderStroke(1.dp, RetailBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            if (key == "DEL") {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.Backspace,
                                                    contentDescription = "Backspace",
                                                    tint = RetailTextPrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = key,
                                                    fontSize = if (key == "C") 13.sp else 18.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = if (key == "C") RetailRedMissed else RetailTextPrimary
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

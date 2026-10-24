package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.window.Dialog
import com.example.data.UserEntity
import com.example.data.isPendingFirstPin
import com.example.ui.theme.*

@Composable
fun PinPadDialog(
    user: UserEntity,
    title: String = "ENTER YOUR PIN",
    isFirstLogin: Boolean = user.isPendingFirstPin,
    onPinEntered: (String) -> Unit,
    onFirstLoginPinChosen: (String) -> Unit = {},
    onDismiss: () -> Unit,
    errorMessage: String? = null
) {
    var pin by remember { mutableStateOf("") }
    var setupStep by remember { mutableStateOf(1) } // 1: choose, 2: confirm
    var chosenPin by remember { mutableStateOf("") }
    var internalError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, RetailBorder, RoundedCornerShape(12.dp)),
            color = RetailSurface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(RetailHeaderBlack, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user.initials,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = user.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = RetailTextPrimary
                            )
                            Text(
                                text = user.role,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RetailOrange
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = RetailTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isFirstLogin) {
                    Surface(
                        color = Color(0xFFF3E8FF),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD8B4FE))
                    ) {
                        Text(
                            text = if (setupStep == 1) "★ FIRST SIGN IN - STEP 1/2" else "★ CONFIRM PIN - STEP 2/2",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF7E22CE),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = if (isFirstLogin) {
                        if (setupStep == 1) "CHOOSE A NEW 4-DIGIT PIN" else "CONFIRM YOUR NEW PIN"
                    } else {
                        title
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = if (isFirstLogin) RetailTextPrimary else RetailTextSecondary
                )

                if (isFirstLogin) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (setupStep == 1) {
                            "Welcome! Please choose a private 4-digit PIN."
                        } else {
                            "Re-enter your 4-digit PIN to confirm."
                        },
                        fontSize = 11.sp,
                        color = RetailTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    if (setupStep == 2) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = {
                                setupStep = 1
                                pin = ""
                                chosenPin = ""
                                internalError = null
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "← Re-choose first PIN",
                                fontSize = 11.sp,
                                color = Color(0xFF7E22CE),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // PIN dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    repeat(4) { index ->
                        val filled = index < pin.length
                        val dotColor = if (isFirstLogin) Color(0xFF9333EA) else RetailOrange
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(if (filled) dotColor else Color.Transparent)
                                .border(
                                    2.dp,
                                    if (filled) dotColor else RetailBorder,
                                    CircleShape
                                )
                        )
                    }
                }

                val activeError = internalError ?: errorMessage
                if (!activeError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = activeError,
                        color = RetailRedMissed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Numeric Keypad
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "DEL")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (row in keys) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (key in row) {
                                KeypadButton(
                                    label = key,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    when (key) {
                                        "C" -> {
                                            pin = ""
                                            internalError = null
                                        }
                                        "DEL" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                        else -> {
                                            if (pin.length < 4) {
                                                val next = pin + key
                                                pin = next
                                                if (next.length == 4) {
                                                    if (isFirstLogin) {
                                                        if (setupStep == 1) {
                                                            chosenPin = next
                                                            pin = ""
                                                            setupStep = 2
                                                            internalError = null
                                                        } else {
                                                            if (next == chosenPin) {
                                                                internalError = null
                                                                onFirstLoginPinChosen(next)
                                                            } else {
                                                                internalError = "PINs do not match. Please choose your 4-digit PIN again."
                                                                pin = ""
                                                                chosenPin = ""
                                                                setupStep = 1
                                                            }
                                                        }
                                                    } else {
                                                        onPinEntered(next)
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
    }
}

@Composable
fun KeypadButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = when (label) {
            "C" -> RetailSurfaceSubtle
            "DEL" -> RetailSurfaceSubtle
            else -> RetailSurfaceSubtle
        },
        border = androidx.compose.foundation.BorderStroke(1.dp, RetailBorder),
        modifier = modifier
            .height(56.dp)
            .testTag("keypad_$label")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (label == "DEL") {
                Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = "Backspace",
                    tint = RetailTextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(
                    text = label,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (label) {
                        "C" -> RetailRedMissed
                        else -> RetailTextPrimary
                    }
                )
            }
        }
    }
}

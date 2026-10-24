package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.UserEntity
import com.example.data.isPendingFirstPin
import com.example.ui.components.PinPadDialog
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SignInScreen(
    storeName: String,
    tagline: String,
    users: List<UserEntity>,
    isCloudConnected: Boolean = false,
    cloudUserEmail: String? = null,
    syncStatus: String = "Ready",
    onSignInGoogle: () -> Unit = {},
    onSignOutGoogle: () -> Unit = {},
    onAttemptSignIn: (userId: Long, pin: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onCompleteFirstLoginPin: (userId: Long, newPin: String, onSuccess: () -> Unit) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var selectedUserForPin by remember { mutableStateOf<UserEntity?>(null) }
    var pinErrorMessage by remember { mutableStateOf<String?>(null) }

    val todayDateFormatted = remember {
        val sdf = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
        sdf.format(Date()).uppercase()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(RetailBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val isTablet = maxWidth >= 540.dp

        if (isTablet) {
            // Tablet-first 2-Pane Kiosk Layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Left Panel: Store Branding, Date, Terminal Info
                Card(
                    modifier = Modifier
                        .widthIn(min = 280.dp, max = 340.dp)
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = RetailHeaderBlack),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            // Store Logo & Name
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color.White, RoundedCornerShape(10.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_storefront),
                                    contentDescription = "Store Logo",
                                    modifier = Modifier.size(38.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = storeName.ifBlank { "ONESTOP CANTERBURY" },
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 0.5.sp,
                                lineHeight = 28.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = tagline.ifBlank { "SIGN IN TO START YOUR SHIFT" },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RetailOrange,
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(24.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(20.dp))

                            // Terminal Shift Status Card
                            Surface(
                                color = Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = RetailOrange,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "SHIFT CLOCK",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White.copy(alpha = 0.7f),
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = todayDateFormatted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Security Info
                            Surface(
                                color = Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = RetailOrange,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "PIN AUTHENTICATION",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White.copy(alpha = 0.7f),
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "• Unique 4-digit PIN required\n• Secure salted local hashing\n• Timestamped sign-offs & employee ID",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.85f),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }

                        // Google Cloud Sync Status & Action
                        Surface(
                            color = RetailGreenDone.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                RetailGreenDone.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(RetailGreenDone, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "LIVE CLOUD DATABASE SYNCED",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = RetailGreenDone,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    if (cloudUserEmail != null) {
                                        Text(
                                            text = "DISCONNECT",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.clickable { onSignOutGoogle() }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = cloudUserEmail ?: "Real-time sync with Web App & devices",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1
                                )
                                if (cloudUserEmail == null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = onSignInGoogle,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = RetailOrange,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("google_sign_in_button")
                                    ) {
                                        Text(
                                            text = "SIGN IN WITH GOOGLE (OPTIONAL)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Right Panel: Staff Roster Grid
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = RetailSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RetailBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "SELECT YOUR NAME",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = RetailTextPrimary,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "TOUCH YOUR PROFILE CARD TO ENTER YOUR PIN",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RetailTextSecondary,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Surface(
                                color = RetailHeaderBlack,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "${users.size} TEAM MEMBERS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = RetailBorder, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Grid of Staff Cards on Tablet
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 200.dp),
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(users) { user ->
                                TabletUserCard(
                                    user = user,
                                    onClick = {
                                        selectedUserForPin = user
                                        pinErrorMessage = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Compact Phone Fallback
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(RetailHeaderBlack, RoundedCornerShape(6.dp))
                            .border(1.dp, RetailBorder, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_storefront),
                            contentDescription = "Store Logo",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = storeName.ifBlank { "ONESTOP CANTERBURY" },
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = RetailTextPrimary,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Text(
                    text = tagline.ifBlank { "SIGN IN TO START YOUR SHIFT" },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = RetailTextSecondary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                HorizontalDivider(
                    color = RetailHeaderBlack,
                    thickness = 1.5.dp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "SELECT YOUR NAME",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = RetailTextSecondary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(users) { user ->
                        UserRowItem(
                            user = user,
                            onClick = {
                                selectedUserForPin = user
                                pinErrorMessage = null
                            }
                        )
                        HorizontalDivider(color = RetailBorder, thickness = 1.dp)
                    }
                }
            }
        }

        // PIN Pad Dialog
        selectedUserForPin?.let { user ->
            PinPadDialog(
                user = user,
                isFirstLogin = user.isPendingFirstPin,
                errorMessage = pinErrorMessage,
                onPinEntered = { enteredPin ->
                    onAttemptSignIn(
                        user.id,
                        enteredPin,
                        {
                            selectedUserForPin = null
                            pinErrorMessage = null
                        },
                        { errorMsg ->
                            pinErrorMessage = errorMsg
                        }
                    )
                },
                onFirstLoginPinChosen = { newPin ->
                    onCompleteFirstLoginPin(
                        user.id,
                        newPin
                    ) {
                        selectedUserForPin = null
                        pinErrorMessage = null
                    }
                },
                onDismiss = {
                    selectedUserForPin = null
                    pinErrorMessage = null
                }
            )
        }
    }
}

@Composable
fun TabletUserCard(
    user: UserEntity,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = RetailSurfaceSubtle,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, RetailBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("user_item_${user.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(
                        if (user.role == "ADMIN") RetailOrange else RetailHeaderBlack,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.initials,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = user.name,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = RetailTextPrimary,
                letterSpacing = 0.5.sp,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = RetailBorder,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "ID: #${user.id}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = RetailTextSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    color = if (user.role == "ADMIN") RetailOrange.copy(alpha = 0.15f) else RetailHeaderBlack.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = user.role,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (user.role == "ADMIN") RetailOrange else RetailTextSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (user.isPendingFirstPin) {
                    Surface(
                        color = Color(0xFFF3E8FF),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD8B4FE))
                    ) {
                        Text(
                            text = "★ CHOOSE PIN",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF7E22CE),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color = if (user.isPendingFirstPin) Color(0xFF7E22CE) else RetailHeaderBlack,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (user.isPendingFirstPin) "CHOOSE 4-DIGIT PIN" else "ENTER PIN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun UserRowItem(
    user: UserEntity,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = RetailBackground,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("user_item_${user.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (user.role == "ADMIN") RetailOrange else RetailHeaderBlack,
                        RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.initials,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = RetailTextPrimary,
                    letterSpacing = 0.5.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ID: #${user.id} • ${user.role}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RetailTextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    if (user.isPendingFirstPin) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFFF3E8FF),
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD8B4FE))
                        ) {
                            Text(
                                text = "★ CHOOSE PIN",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF7E22CE),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Select User",
                tint = RetailTextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

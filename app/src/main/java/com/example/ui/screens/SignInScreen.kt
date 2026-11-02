package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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

    // Deduplicate users by normalized uppercase name
    val dedupedUsers = remember(users) {
        val seen = mutableSetOf<String>()
        users.filter { user ->
            val norm = user.name.trim().uppercase()
            if (norm in seen) false
            else {
                seen.add(norm)
                true
            }
        }
    }

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
            // Tablet-first 2-Pane Kiosk Layout - using 100% of wide screen
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Left Panel: Store Branding, Date, Terminal Info
                Card(
                    modifier = Modifier
                        .weight(0.35f)
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
                                    text = "${dedupedUsers.size} TEAM MEMBERS",
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
                            items(dedupedUsers) { user ->
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
            // Modern Phone Layout matching Web Terminal & IMG_3629
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Header (No storefront logo icon at top per request)
                Text(
                    text = storeName.ifBlank { "ONESTOP CANTERBURY" },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = RetailTextPrimary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = tagline.ifBlank { "SIGN IN TO START YOUR SHIFT" },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = RetailOrange,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                // Subtitle Row: SELECT YOUR NAME & Tap to enter PIN
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SELECT YOUR NAME",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = RetailTextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Tap to enter PIN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RetailTextSecondary
                    )
                }

                // Roster Cards List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(dedupedUsers) { user ->
                        UserRowItem(
                            user = user,
                            onClick = {
                                selectedUserForPin = user
                                pinErrorMessage = null
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "4-Digit PIN Security • Salted Hashing • 5-min Auto-logout",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RetailTextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
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
    val isAdmin = user.role == "ADMIN"
    val avatarBg = remember(user.name, user.initials) {
        when (user.initials.uppercase()) {
            "MA" -> Color(0xFF064E3B)
            "K" -> Color(0xFF1E1B4B)
            "F" -> Color(0xFF0F766E)
            "L" -> Color(0xFF831843)
            "T" -> Color(0xFF1E293B)
            "J" -> Color(0xFF1E293B)
            "O" -> Color(0xFF18181B)
            "E" -> Color(0xFF0F172A)
            "S" -> Color(0xFF09090B)
            else -> if (isAdmin) Color(0xFF9A3412) else Color(0xFF1E293B)
        }
    }
    val avatarText = remember(user.name, user.initials) {
        when (user.initials.uppercase()) {
            "MA" -> Color(0xFF34D399)
            "K" -> Color(0xFFC084FC)
            "F" -> Color(0xFF2DD4BF)
            "L" -> Color(0xFFF472B6)
            "T" -> Color(0xFFFB7185)
            "J" -> Color(0xFFF472B6)
            "O" -> Color(0xFFEF4444)
            "E" -> Color(0xFFFB923C)
            "S" -> Color(0xFF60A5FA)
            else -> Color.White
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = RetailSurface,
        border = BorderStroke(1.dp, RetailBorder),
        shadowElevation = 0.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("user_item_${user.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(avatarBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.initials.ifBlank { user.name.take(1) },
                    color = avatarText,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name.uppercase(),
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = RetailTextPrimary,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Role Badge
                    Surface(
                        color = if (isAdmin) Color(0xFFFEE2E2) else Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (isAdmin) "ADMINISTRATOR" else "STAFF",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isAdmin) Color(0xFFDC2626) else Color(0xFF2563EB),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // First-Time PIN Setup Badge
                    if (user.isPendingFirstPin) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFFFAF5FF),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFFE9D5FF))
                        ) {
                            Text(
                                text = "★ CHOOSE PIN",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF7E22CE),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Select User",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.UserEntity
import com.example.ui.AppScreen
import com.example.ui.theme.*

/**
 * High-density 64dp icon-only Left Navigation Rail matching Mockup IMG_3681.
 * Slimmed down from bulky 92dp sidebar to maximize vertical & horizontal workspace.
 */
@Composable
fun TabletNavRail(
    currentScreen: AppScreen,
    currentUser: UserEntity,
    storeName: String = "",
    onScreenSelected: (AppScreen) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(80.dp)
            .fillMaxHeight()
            .background(RetailHeaderBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top section: Clean Destinations (TODAY, HANDOVER, HISTORY, ADMIN)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NavRailItem(
                label = "TODAY",
                icon = if (currentScreen == AppScreen.TODAY) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                selected = currentScreen == AppScreen.TODAY,
                onClick = { onScreenSelected(AppScreen.TODAY) },
                tag = "nav_today"
            )

            NavRailItem(
                label = "HANDOVER",
                icon = Icons.Default.SwapHoriz,
                selected = currentScreen == AppScreen.HANDOVER,
                onClick = { onScreenSelected(AppScreen.HANDOVER) },
                tag = "nav_handover"
            )

            NavRailItem(
                label = "HISTORY",
                icon = if (currentScreen == AppScreen.HISTORY) Icons.Filled.Archive else Icons.Outlined.Archive,
                selected = currentScreen == AppScreen.HISTORY,
                onClick = { onScreenSelected(AppScreen.HISTORY) },
                tag = "nav_history"
            )

            NavRailItem(
                label = "ADMIN",
                icon = if (currentScreen == AppScreen.ADMIN) Icons.Filled.Settings else Icons.Outlined.Settings,
                selected = currentScreen == AppScreen.ADMIN,
                onClick = { onScreenSelected(AppScreen.ADMIN) },
                tag = "nav_admin"
            )
        }

        // Bottom section: User Circle Avatar, First Name, and LOG OUT button (IMG_3681)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // User Avatar Circle (DS)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(RetailOrange, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentUser.initials,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // User Name (e.g. DANIEL)
            val firstName = (currentUser.name.split(" ").firstOrNull() ?: currentUser.name).uppercase()
            Text(
                text = firstName,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 1,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Log Out Icon & Label matching IMG_3681
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onSignOut)
                    .padding(vertical = 4.dp)
                    .testTag("signout_button"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Log Out",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "LOG OUT",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f),
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun NavRailItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) RetailOrange.copy(alpha = 0.2f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) RetailOrange else Color.White.copy(alpha = 0.65f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                color = if (selected) RetailOrange else Color.White.copy(alpha = 0.65f),
                letterSpacing = 0.2.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

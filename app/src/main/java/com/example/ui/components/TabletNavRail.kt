package com.example.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.UserEntity
import com.example.ui.AppScreen
import com.example.ui.theme.*

@Composable
fun TabletNavRail(
    currentScreen: AppScreen,
    currentUser: UserEntity,
    storeName: String,
    onScreenSelected: (AppScreen) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(104.dp)
            .fillMaxHeight()
            .background(RetailHeaderBlack)
            .border(
                width = 1.dp,
                color = RetailBorder,
                shape = androidx.compose.ui.graphics.RectangleShape
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top section: Store Brand & Logo
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_storefront),
                    contentDescription = "Store Logo",
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = storeName.take(12).ifBlank { "ONESTOP" },
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 0.5.sp,
                maxLines = 1
            )

            Text(
                text = "TABLET KIOSK",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = RetailOrange,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Destinations
            NavRailItem(
                label = "TODAY",
                icon = if (currentScreen == AppScreen.TODAY) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                selected = currentScreen == AppScreen.TODAY,
                onClick = { onScreenSelected(AppScreen.TODAY) },
                tag = "nav_today"
            )

            Spacer(modifier = Modifier.height(12.dp))

            NavRailItem(
                label = "HISTORY",
                icon = if (currentScreen == AppScreen.HISTORY) Icons.Filled.Archive else Icons.Outlined.Archive,
                selected = currentScreen == AppScreen.HISTORY,
                onClick = { onScreenSelected(AppScreen.HISTORY) },
                tag = "nav_history"
            )

            Spacer(modifier = Modifier.height(12.dp))

            NavRailItem(
                label = "ADMIN",
                icon = if (currentScreen == AppScreen.ADMIN) Icons.Filled.Settings else Icons.Outlined.Settings,
                selected = currentScreen == AppScreen.ADMIN,
                onClick = { onScreenSelected(AppScreen.ADMIN) },
                tag = "nav_admin"
            )
        }

        // Bottom section: Staff badge & Logout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // User Initials Avatar
            Box(
                modifier = Modifier
                    .size(42.dp)
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

            Text(
                text = currentUser.name.split(" ").firstOrNull() ?: currentUser.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )

            Surface(
                color = if (currentUser.role == "ADMIN") RetailOrange.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(3.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = currentUser.role,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    color = if (currentUser.role == "ADMIN") RetailOrange else Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sign out action button
            IconButton(
                onClick = onSignOut,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                    .testTag("signout_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Sign Out",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
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
        color = if (selected) RetailOrange else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color.White else Color.White.copy(alpha = 0.65f),
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.65f),
                letterSpacing = 0.5.sp
            )
        }
    }
}

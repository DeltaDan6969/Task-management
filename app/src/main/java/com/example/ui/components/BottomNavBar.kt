package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppScreen
import com.example.ui.theme.*

@Composable
fun BottomNavBar(
    currentScreen: AppScreen,
    onScreenSelected: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(RetailSurface)
            .border(
                width = 1.dp,
                color = RetailBorder,
                shape = androidx.compose.ui.graphics.RectangleShape
            )
            .navigationBarsPadding()
            .height(64.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavBarItem(
            label = "TODAY",
            icon = if (currentScreen == AppScreen.TODAY) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
            selected = currentScreen == AppScreen.TODAY,
            onClick = { onScreenSelected(AppScreen.TODAY) },
            tag = "nav_today"
        )

        NavBarItem(
            label = "HISTORY",
            icon = if (currentScreen == AppScreen.HISTORY) Icons.Filled.Archive else Icons.Outlined.Archive,
            selected = currentScreen == AppScreen.HISTORY,
            onClick = { onScreenSelected(AppScreen.HISTORY) },
            tag = "nav_history"
        )

        NavBarItem(
            label = "ADMIN",
            icon = if (currentScreen == AppScreen.ADMIN) Icons.Filled.Settings else Icons.Outlined.Settings,
            selected = currentScreen == AppScreen.ADMIN,
            onClick = { onScreenSelected(AppScreen.ADMIN) },
            tag = "nav_admin"
        )
    }
}

@Composable
private fun RowScope.NavBarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) RetailOrange else RetailTextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) RetailOrange else RetailTextSecondary,
            letterSpacing = 0.5.sp
        )
    }
}

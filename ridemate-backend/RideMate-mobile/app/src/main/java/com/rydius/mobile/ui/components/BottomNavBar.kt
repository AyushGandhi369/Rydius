package com.rydius.mobile.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.rydius.mobile.ui.theme.*

data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
    BottomNavItem("Home", Icons.Filled.Home, Icons.Outlined.Home, "home"),
    BottomNavItem("My Rides", Icons.Filled.DirectionsCar, Icons.Outlined.DirectionsCar, "my_rides"),
    BottomNavItem("Profile", Icons.Filled.Person, Icons.Outlined.Person, "profile")
)

@Composable
fun BottomNavBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = NavigationBarDefaults.Elevation
    ) {
        bottomNavItems.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = { onItemSelected(index) },
                icon = {
                    Icon(
                        imageVector = if (selectedIndex == index) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Secondary,
                    selectedTextColor = Secondary,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = Secondary.copy(alpha = 0.12f)
                )
            )
        }
    }
}

package com.rydius.mobile.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Hail
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rydius.mobile.ui.theme.*

@Composable
fun RoleSelector(
    selectedRole: String,
    onRoleChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RoleTab(
                label = "Rider",
                icon = Icons.Default.Hail,
                isSelected = selectedRole == "rider",
                activeColor = RiderColor,
                onClick = { onRoleChange("rider") },
                modifier = Modifier.weight(1f)
            )
            RoleTab(
                label = "Car Owner",
                icon = Icons.Default.DirectionsCar,
                isSelected = selectedRole == "driver",
                activeColor = DriverColor,
                onClick = { onRoleChange("driver") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RoleTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    activeColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        if (isSelected) activeColor else SurfaceLight,
        label = "roleBg"
    )
    val contentColor by animateColorAsState(
        if (isSelected) TextOnPrimary else TextSecondary,
        label = "roleContent"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = contentColor, fontWeight = FontWeight.SemiBold)
        }
    }
}

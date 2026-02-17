package com.rydius.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rydius.mobile.data.model.AvailableDriver
import com.rydius.mobile.ui.theme.*

@Composable
fun DriverCard(
    driver: AvailableDriver,
    isSelected: Boolean,
    matchSent: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(if (isSelected) 6.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Secondary.copy(alpha = 0.05f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Driver avatar
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(DriverColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (driver.driverName?.firstOrNull() ?: 'D').uppercase(),
                        color = TextOnPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = driver.driverName ?: "Driver",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = Warning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "%.1f".format(driver.rating ?: 4.5),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            driver.vehicleType ?: "4W",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                // Fare
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "\u20B9%.0f".format(driver.fare ?: 0.0),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Secondary
                    )
                    Text(
                        text = "per seat",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Route info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NearMe, contentDescription = null, tint = Info, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "%.1f km away".format(driver.pickupDistance),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Straighten, contentDescription = null, tint = Info, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "%.1f km trip".format(driver.distanceKm),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Select button
            Button(
                onClick = onSelect,
                enabled = !matchSent,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (matchSent) Success else Secondary,
                    disabledContainerColor = Success.copy(alpha = 0.7f)
                )
            ) {
                if (matchSent) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Request Sent")
                } else {
                    Icon(Icons.Default.Hail, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select This Ride", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

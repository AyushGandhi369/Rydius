package com.rydius.mobile.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rydius.mobile.data.model.CostSharingResponse
import com.rydius.mobile.ui.theme.*

@Composable
fun CostSharingCard(
    costData: CostSharingResponse?,
    isDriver: Boolean,
    modifier: Modifier = Modifier
) {
    if (costData == null) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Savings,
                    contentDescription = null,
                    tint = Secondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Cost Sharing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = DividerColor)
            Spacer(modifier = Modifier.height(12.dp))

            // Total Trip Cost
            CostRow(
                label = "Total Trip Cost",
                value = "\u20B9%.0f".format(costData.baseTripCost ?: 0.0),
                isHighlight = false
            )

            // Savings vs taxi
            costData.savingsVsTaxi?.let { savings ->
                CostRow(
                    label = "Savings vs Taxi",
                    value = "\u20B9%.0f".format(savings),
                    isHighlight = false
                )
            }

            // Savings percentage
            costData.savingsPercentage?.let { pct ->
                CostRow(
                    label = "Savings",
                    value = "%.0f%%".format(pct),
                    isHighlight = false
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = DividerColor)
            Spacer(modifier = Modifier.height(8.dp))

            if (isDriver) {
                // Driver view - show per-passenger cost and earnings
                CostRow(
                    label = "Per Passenger Share",
                    value = "\u20B9%.0f".format(costData.costPerPassenger ?: 0.0),
                    isHighlight = true
                )
                costData.driverSaved?.let { savings ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = Success,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "You save \u20B9%.0f with full ride".format(savings),
                            color = Success,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                // Passenger view - show their cost
                CostRow(
                    label = "Your Share",
                    value = "\u20B9%.0f".format(costData.costPerPassenger ?: 0.0),
                    isHighlight = true
                )
            }

            // CO2 saved
            costData.co2SavedKg?.let { co2 ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Success.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Eco,
                            contentDescription = null,
                            tint = Success,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "~%.1f kg CO\u2082 saved per ride".format(co2),
                            color = Success,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CostRow(
    label: String,
    value: String,
    isHighlight: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isHighlight) MaterialTheme.colorScheme.onSurface else TextSecondary
        )
        Text(
            text = value,
            style = if (isHighlight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (isHighlight) 20.sp else 14.sp,
            color = if (isHighlight) Secondary else MaterialTheme.colorScheme.onSurface
        )
    }
}

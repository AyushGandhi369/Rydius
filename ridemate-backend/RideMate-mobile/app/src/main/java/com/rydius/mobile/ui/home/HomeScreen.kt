package com.rydius.mobile.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rydius.mobile.ui.components.BottomNavBar
import com.rydius.mobile.ui.components.LocationSearchBar
import com.rydius.mobile.ui.components.RoleSelector
import com.rydius.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToDriver: (String, String, Double, Double, Double, Double, Int, String) -> Unit,
    onNavigateToPassenger: (String, String, Double, Double, Double, Double, Int, String) -> Unit,
    onNavigateToMyRides: () -> Unit,
    onNavigateToProfile: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    Scaffold(
        bottomBar = {
            BottomNavBar(
                selectedIndex = 0,
                onHomeClick = { /* already here */ },
                onRidesClick = onNavigateToMyRides,
                onProfileClick = onNavigateToProfile
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // ── Header gradient ────────────────────────────────
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Primary, PrimaryVariant)
                                )
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text(
                                text = "Where are you\nheading?",
                                style = MaterialTheme.typography.headlineLarge,
                                color = TextOnPrimary,
                                lineHeight = 36.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Find or offer a ride today",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextOnPrimary.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // ── Role Selector ──────────────────────────────────
                item {
                    RoleSelector(
                        selectedRole = vm.selectedRole,
                        onRoleChange = { vm.setRole(it) },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }

                // ── Location inputs ────────────────────────────────
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Pickup
                            LocationSearchBar(
                                value = vm.pickupText,
                                onValueChange = { vm.onPickupTextChange(it) },
                                placeholder = "Pickup location",
                                icon = Icons.Default.TripOrigin,
                                iconTint = MarkerGreen,
                                suggestions = vm.pickupSuggestions,
                                showSuggestions = vm.showPickupSuggestions,
                                onSuggestionClick = { vm.selectPickup(it) },
                                onDismissSuggestions = { vm.dismissSuggestions() }
                            )

                            // Swap button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(
                                    onClick = { vm.swapLocations() },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(SurfaceLight)
                                ) {
                                    Icon(
                                        Icons.Default.SwapVert,
                                        contentDescription = "Swap",
                                        tint = Primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Dropoff
                            LocationSearchBar(
                                value = vm.dropoffText,
                                onValueChange = { vm.onDropoffTextChange(it) },
                                placeholder = "Drop-off location",
                                icon = Icons.Default.LocationOn,
                                iconTint = MarkerRed,
                                suggestions = vm.dropoffSuggestions,
                                showSuggestions = vm.showDropoffSuggestions,
                                onSuggestionClick = { vm.selectDropoff(it) },
                                onDismissSuggestions = { vm.dismissSuggestions() }
                            )
                        }
                    }
                }

                // ── Quick tags ─────────────────────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf("Office", "Home").forEach { tag ->
                            AssistChip(
                                onClick = { vm.applyQuickTag(tag) },
                                label = { Text(tag) },
                                leadingIcon = {
                                    Icon(
                                        if (tag == "Office") Icons.Default.Business
                                        else Icons.Default.Home,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                // ── Seats selector ─────────────────────────────────
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AirlineSeatReclineNormal,
                                    contentDescription = null,
                                    tint = Secondary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Seats", fontWeight = FontWeight.Medium)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                (1..4).forEach { n ->
                                    val selected = vm.seats == n
                                    val bgColor by animateColorAsState(
                                        if (selected) Secondary else SurfaceLight,
                                        label = "seat"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(bgColor)
                                            .clickable { vm.setSeats(n) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "$n",
                                            color = if (selected) TextOnPrimary else TextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Departure time ─────────────────────────────────
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Secondary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Departure", fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                vm.departureTime,
                                color = Secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // ── Error ──────────────────────────────────────────
                vm.errorMessage?.let { error ->
                    item {
                        Text(
                            text = error,
                            color = Error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }

                // ── Action button ──────────────────────────────────
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (vm.validate()) {
                                val time = vm.departureTime
                                if (vm.selectedRole == "driver") {
                                    onNavigateToDriver(
                                        vm.pickupText, vm.dropoffText,
                                        vm.pickupLat, vm.pickupLng,
                                        vm.dropoffLat, vm.dropoffLng,
                                        vm.seats, time
                                    )
                                } else {
                                    onNavigateToPassenger(
                                        vm.pickupText, vm.dropoffText,
                                        vm.pickupLat, vm.pickupLng,
                                        vm.dropoffLat, vm.dropoffLng,
                                        vm.seats, time
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (vm.selectedRole == "driver") DriverColor else RiderColor
                        )
                    ) {
                        Icon(
                            if (vm.selectedRole == "driver") Icons.Default.DirectionsCar
                            else Icons.Default.Hail,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (vm.selectedRole == "driver") "Offer a Ride" else "Find a Ride",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // ── Info cards ─────────────────────────────────────
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InfoCard(
                            icon = Icons.Default.Savings,
                            title = "Save Money",
                            subtitle = "Up to 75% cheaper",
                            modifier = Modifier.weight(1f)
                        )
                        InfoCard(
                            icon = Icons.Default.Eco,
                            title = "Go Green",
                            subtitle = "Reduce CO\u2082",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = Secondary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

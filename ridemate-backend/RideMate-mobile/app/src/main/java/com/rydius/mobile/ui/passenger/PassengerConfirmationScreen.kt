package com.rydius.mobile.ui.passenger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rydius.mobile.ui.components.CostSharingCard
import com.rydius.mobile.ui.components.DriverCard
import com.rydius.mobile.ui.components.MapViewComposable
import com.rydius.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerConfirmationScreen(
    startLocation: String,
    endLocation: String,
    startLat: Double,
    startLng: Double,
    endLat: Double,
    endLng: Double,
    seats: Int,
    departureTime: String,
    onBack: () -> Unit,
    onNavigateHome: () -> Unit,
    vm: PassengerViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        vm.initialize(startLocation, endLocation, startLat, startLng, endLat, endLng, seats, departureTime)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find a Ride") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = TextOnPrimary,
                    navigationIconContentColor = TextOnPrimary
                )
            )
        }
    ) { padding ->
        if (vm.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Secondary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Finding rides for you...", color = TextSecondary)
                }
            }
            return@Scaffold
        }

        vm.errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = Error, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(error, color = Error, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onBack) { Text("Go Back") }
                        Button(onClick = {
                            vm.retry(startLocation, endLocation, startLat, startLng, endLat, endLng, seats, departureTime)
                        }) { Text("Retry") }
                    }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Map
            item {
                MapViewComposable(
                    startLat = startLat,
                    startLng = startLng,
                    endLat = endLat,
                    endLng = endLng,
                    routePolyline = vm.routePolyline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                )
            }

            // Route summary
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-12).dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(6.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Icon(Icons.Default.TripOrigin, contentDescription = null, tint = MarkerGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(24.dp)
                                        .background(DividerColor)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MarkerRed, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    startLocation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    endLocation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = DividerColor)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            PassengerStatItem(Icons.Default.Straighten, "%.1f km".format(vm.distanceKm), "Distance", Info)
                            PassengerStatItem(Icons.Default.Schedule, "${vm.durationMinutes} min", "Duration", Warning)
                            PassengerStatItem(Icons.Default.AirlineSeatReclineNormal, "$seats", "Seats", Success)
                        }
                    }
                }
            }

            // Cost sharing card
            vm.costInfo?.let { cost ->
                item {
                    CostSharingCard(
                        costData = cost,
                        isDriver = false,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // Match sent confirmation
            if (vm.matchSent && !vm.matchAccepted) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Success.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = Secondary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Request Sent!", fontWeight = FontWeight.Bold, color = Success)
                                Text(
                                    "Waiting for the driver to accept your request...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            // Cancel button
                            OutlinedButton(
                                onClick = { vm.cancelRideRequest(onNavigateHome) },
                                enabled = !vm.isCancelling,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
                                border = BorderStroke(1.dp, Error.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                if (vm.isCancelling) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Error
                                    )
                                } else {
                                    Text("Cancel", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        // Cancel error shown inline (not full-screen)
                        vm.cancelError?.let { err ->
                            Text(
                                text = err,
                                color = Error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Match ACCEPTED — ride confirmed!
            if (vm.matchAccepted) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Success.copy(alpha = 0.12f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CheckCircle, contentDescription = null,
                                tint = Success, modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Ride Confirmed!",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Success
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            vm.acceptedDriverName?.let { name ->
                                Text(
                                    "Driver: $name",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            vm.acceptedFare?.let { fare ->
                                Text(
                                    "Fare: \u20B9%.0f".format(fare),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onNavigateHome,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Success)
                            ) {
                                Text("Done", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Match REJECTED — driver declined
            if (vm.matchRejected) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Error.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, tint = Error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Request Declined", fontWeight = FontWeight.Bold, color = Error)
                                Text(
                                    "The driver declined your request. Try another driver below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Available drivers
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Available Drivers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (vm.isSearchingDrivers) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Secondary
                        )
                    } else {
                        IconButton(onClick = {
                            vm.refreshDrivers(startLat, startLng, endLat, endLng)
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Secondary)
                        }
                    }
                }
            }

            if (vm.availableDrivers.isEmpty() && !vm.isSearchingDrivers) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No drivers found yet", fontWeight = FontWeight.Medium)
                            Text(
                                "Try again in a few minutes",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            items(vm.availableDrivers) { driver ->
                DriverCard(
                    driver = driver,
                    isSelected = vm.selectedDriverTripId == driver.tripId,
                    matchSent = vm.matchSent && vm.selectedDriverTripId == driver.tripId,
                    onSelect = {
                        vm.selectDriver(driver, startLat, startLng, endLat, endLng)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PassengerStatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    tint: androidx.compose.ui.graphics.Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
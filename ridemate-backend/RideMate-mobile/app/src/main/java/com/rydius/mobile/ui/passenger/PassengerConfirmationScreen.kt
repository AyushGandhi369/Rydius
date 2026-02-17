package com.rydius.mobile.ui.passenger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
                    Button(onClick = onBack) { Text("Go Back") }
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
                )
            }

            // Route summary
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TripOrigin, contentDescription = null, tint = MarkerGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(startLocation, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                        Box(
                            modifier = Modifier
                                .padding(start = 9.dp)
                                .width(2.dp)
                                .height(20.dp)
                                .background(TextSecondary.copy(alpha = 0.3f))
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MarkerRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(endLocation, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("%.1f km".format(vm.distanceKm), fontWeight = FontWeight.Bold)
                                Text("Distance", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${vm.durationMinutes} min", fontWeight = FontWeight.Bold)
                                Text("Duration", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            // Cost sharing card
            vm.costInfo?.let { cost ->
                item {
                    CostSharingCard(
                        costSharing = cost,
                        isDriver = false,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // Match sent confirmation
            if (vm.matchSent) {
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
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Success)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Request Sent!", fontWeight = FontWeight.Bold, color = Success)
                                Text(
                                    "Waiting for the driver to accept your request.",
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

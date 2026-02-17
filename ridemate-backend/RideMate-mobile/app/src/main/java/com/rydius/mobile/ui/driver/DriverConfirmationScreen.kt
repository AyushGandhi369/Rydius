package com.rydius.mobile.ui.driver

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rydius.mobile.ui.components.CostSharingCard
import com.rydius.mobile.ui.components.MapViewComposable
import com.rydius.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverConfirmationScreen(
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
    vm: DriverViewModel = viewModel()
) {
    // Initialize on first composition
    LaunchedEffect(Unit) {
        vm.initialize(startLocation, endLocation, startLat, startLng, endLat, endLng, seats, departureTime)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Trip") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = TextOnPrimary,
                    navigationIconContentColor = TextOnPrimary
                ),
                actions = {
                    if (vm.tripId != null) {
                        TextButton(onClick = { vm.cancelTrip(onNavigateHome) }) {
                            Text("Cancel Trip", color = Error)
                        }
                    }
                }
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
                    Text("Setting up your trip...", color = TextSecondary)
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
                        .height(280.dp)
                )
            }

            // Route info card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // From → To
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

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        // Stats row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(
                                icon = Icons.Default.Straighten,
                                label = "Distance",
                                value = "%.1f km".format(vm.distanceKm)
                            )
                            StatItem(
                                icon = Icons.Default.Schedule,
                                label = "Duration",
                                value = "${vm.durationMinutes} min"
                            )
                            StatItem(
                                icon = Icons.Default.AirlineSeatReclineNormal,
                                label = "Seats",
                                value = "$seats"
                            )
                        }
                    }
                }
            }

            // Cost sharing card
            vm.costInfo?.let { cost ->
                item {
                    CostSharingCard(
                        costSharing = cost,
                        isDriver = true,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // Searching status
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SearchingIndicator(
                    text = vm.searchStatusText,
                    isSearching = vm.searchingPassengers,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Pending requests
            if (vm.pendingRequests.isNotEmpty()) {
                item {
                    Text(
                        "Passenger Requests",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(vm.pendingRequests) { match ->
                    PassengerRequestCard(
                        match = match,
                        onAccept = { vm.acceptMatch(match.id) },
                        onDecline = { vm.declineMatch(match.id) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = Secondary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun SearchingIndicator(
    text: String,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val scale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSearching) Secondary.copy(alpha = 0.1f) else SurfaceLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSearching) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(Secondary)
                )
            } else {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PassengerRequestCard(
    match: com.rydius.mobile.data.model.MatchData,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Secondary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (match.passengerName?.firstOrNull() ?: 'P').uppercase(),
                        color = TextOnPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        match.passengerName ?: "Passenger",
                        fontWeight = FontWeight.SemiBold
                    )
                    match.fare?.let { fare ->
                        Text(
                            "Earn: \u20B9%.0f".format(fare * 0.86),
                            color = Success,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Error)
                ) {
                    Text("Decline")
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Success)
                ) {
                    Text("Accept")
                }
            }
        }
    }
}

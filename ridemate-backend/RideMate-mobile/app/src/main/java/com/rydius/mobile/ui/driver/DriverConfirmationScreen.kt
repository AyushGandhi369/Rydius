package com.rydius.mobile.ui.driver

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

    // Use VM state for rendering so a resumed active trip always shows the real trip details.
    val uiStartLocation = vm.tripStartLocation.ifBlank { startLocation }
    val uiEndLocation = vm.tripEndLocation.ifBlank { endLocation }
    val uiStartLat = vm.tripStartLat.takeIf { it != 0.0 } ?: startLat
    val uiStartLng = vm.tripStartLng.takeIf { it != 0.0 } ?: startLng
    val uiEndLat = vm.tripEndLat.takeIf { it != 0.0 } ?: endLat
    val uiEndLng = vm.tripEndLng.takeIf { it != 0.0 } ?: endLng
    val uiSeats = vm.tripAvailableSeats.takeIf { it > 0 } ?: seats

    var showCancelDialog by remember { mutableStateOf(false) }
    var showCompleteDialog by remember { mutableStateOf(false) }

    // Cancel trip confirmation dialog
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Trip") },
            text = { Text("Are you sure you want to cancel this trip? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelDialog = false
                    vm.cancelTrip(onNavigateHome)
                }) { Text("Cancel Trip", color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Keep Trip") }
            }
        )
    }

    // Complete trip confirmation dialog
    if (showCompleteDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteDialog = false },
            title = { Text("Complete Trip") },
            text = { Text("Mark this trip as completed? This will finalize the ride for all passengers.") },
            confirmButton = {
                TextButton(onClick = {
                    showCompleteDialog = false
                    vm.completeTrip(onNavigateHome)
                }) { Text("Complete", color = Success) }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteDialog = false }) { Text("Not Yet") }
            }
        )
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
                )
            )
        },
        bottomBar = {
            if (vm.tripId != null) {
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCancelDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
                            border = BorderStroke(1.5.dp, Error)
                        ) {
                            Icon(Icons.Default.Cancel, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cancel", fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = { showCompleteDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Success)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Complete", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
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
                    startLat = uiStartLat,
                    startLng = uiStartLng,
                    endLat = uiEndLat,
                    endLng = uiEndLng,
                    routePolyline = vm.routePolyline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                )
            }

            // Route info card
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
                        // From → To with visual track
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
                                    uiStartLocation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    uiEndLocation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = DividerColor)
                        Spacer(modifier = Modifier.height(14.dp))

                        // Stats row with colored icon backgrounds
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(
                                icon = Icons.Default.Straighten,
                                label = "Distance",
                                value = "%.1f km".format(vm.distanceKm),
                                tint = Info
                            )
                            StatItem(
                                icon = Icons.Default.Schedule,
                                label = "Duration",
                                value = "${vm.durationMinutes} min",
                                tint = Warning
                            )
                            StatItem(
                                icon = Icons.Default.AirlineSeatReclineNormal,
                                label = "Seats",
                                value = "$uiSeats",
                                tint = Success
                            )
                        }
                    }
                }
            }

            // Cost sharing card
            vm.costInfo?.let { cost ->
                item {
                    CostSharingCard(
                        costData = cost,
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
    value: String,
    tint: androidx.compose.ui.graphics.Color = Secondary
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

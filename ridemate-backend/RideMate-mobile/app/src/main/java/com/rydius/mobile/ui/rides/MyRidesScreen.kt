package com.rydius.mobile.ui.rides

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rydius.mobile.data.model.MyRide
import com.rydius.mobile.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyRidesScreen(
    onBack: () -> Unit,
    onNewRide: () -> Unit,
    vm: MyRidesViewModel = viewModel()
) {
    val tabs = listOf("Active", "Completed", "Cancelled")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Rating dialog state
    var ratingMatchId by remember { mutableIntStateOf(0) }
    var ratingDriverName by remember { mutableStateOf("") }
    var showRatingDialog by remember { mutableStateOf(false) }
    var ratingValue by remember { mutableIntStateOf(0) }
    var reviewText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.loadRides() }

    // Show snackbar on rating success
    LaunchedEffect(vm.ratingSuccess) {
        vm.ratingSuccess?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearRatingSuccess()
        }
    }

    // Rating Dialog
    if (showRatingDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!vm.isSubmittingRating) {
                    showRatingDialog = false
                    ratingValue = 0
                    reviewText = ""
                    vm.clearError()
                }
            },
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Rate Your Ride", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "How was your ride with $ratingDriverName?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Star rating
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        for (i in 1..5) {
                            Icon(
                                if (i <= ratingValue) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$i star",
                                tint = if (i <= ratingValue) Warning else TextSecondary.copy(alpha = 0.3f),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable { ratingValue = i }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        when (ratingValue) {
                            1 -> "Poor"
                            2 -> "Fair"
                            3 -> "Good"
                            4 -> "Very Good"
                            5 -> "Excellent"
                            else -> "Tap a star"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ratingValue > 0) Warning else TextSecondary,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Review text
                    OutlinedTextField(
                        value = reviewText,
                        onValueChange = { reviewText = it },
                        label = { Text("Review (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2,
                        maxLines = 4
                    )

                    // Inline error for rating submission failure
                    vm.errorMessage?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = err,
                            color = Error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.submitRating(
                            matchId = ratingMatchId,
                            rating = ratingValue,
                            review = reviewText.takeIf { it.isNotBlank() }
                        ) {
                            showRatingDialog = false
                            ratingValue = 0
                            reviewText = ""
                        }
                    },
                    enabled = ratingValue > 0 && !vm.isSubmittingRating
                ) {
                    if (vm.isSubmittingRating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Submit", fontWeight = FontWeight.SemiBold, color = Primary)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRatingDialog = false
                        ratingValue = 0
                        reviewText = ""
                        vm.clearError()
                    },
                    enabled = !vm.isSubmittingRating
                ) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("My Rides") },
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewRide,
                containerColor = Secondary,
                contentColor = TextOnPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Ride", fontWeight = FontWeight.SemiBold)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab row
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Primary,
                contentColor = TextOnPrimary,
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            color = Secondary
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                title,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold
                                else FontWeight.Normal
                            )
                        },
                        selectedContentColor = Secondary,
                        unselectedContentColor = TextOnPrimary.copy(alpha = 0.6f)
                    )
                }
            }

            if (vm.isLoading && vm.rides.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else if (vm.errorMessage != null && vm.rides.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = Error.copy(alpha = 0.6f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Failed to load rides",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            vm.errorMessage ?: "Unknown error",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { vm.loadRides() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }
            } else {
                // Pager content
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val filtered = when (page) {
                        0 -> vm.rides.filter { it.status == "active" }
                        1 -> vm.rides.filter { it.status == "completed" }
                        2 -> vm.rides.filter { it.status == "cancelled" }
                        else -> emptyList()
                    }

                    PullToRefreshBox(
                        isRefreshing = vm.isLoading,
                        onRefresh = { vm.loadRides() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (filtered.isEmpty()) {
                            EmptyRidesPage(tabName = tabs[page])
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filtered, key = { "${it.id}-${it.userRole}" }) { ride ->
                                    RideCard(
                                        ride = ride,
                                        isRated = ride.matchId != null && ride.matchId in vm.ratedMatchIds,
                                        onRate = {
                                            if (ride.matchId != null) {
                                                ratingMatchId = ride.matchId
                                                ratingDriverName = ride.driverName ?: "your driver"
                                                showRatingDialog = true
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RideCard(
    ride: MyRide,
    isRated: Boolean = false,
    onRate: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Role badge + date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (ride.userRole == "driver") "Driver" else "Passenger",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (ride.userRole == "driver") Icons.Default.DirectionsCar
                            else Icons.Default.PersonPin,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (ride.userRole == "driver") Primary.copy(alpha = 0.1f)
                        else Secondary.copy(alpha = 0.1f)
                    )
                )

                Text(
                    ride.departureTime.ifBlank { ride.createdAt.take(16) },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Route
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.TripOrigin, contentDescription = null, tint = MarkerGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = TextSecondary.copy(alpha = 0.3f), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MarkerRed, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        ride.startLocation.ifBlank { "Unknown" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        ride.endLocation.ifBlank { "Unknown" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = DividerColor)
            Spacer(modifier = Modifier.height(8.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RideStatItem(
                    icon = Icons.Default.Route,
                    value = "%.1f km".format(ride.distanceKm)
                )
                RideStatItem(
                    icon = Icons.Default.Schedule,
                    value = "${ride.durationMinutes} min"
                )
                RideStatItem(
                    icon = Icons.Default.AirlineSeatReclineNormal,
                    value = "${ride.availableSeats} seats"
                )
                if (ride.fareShare != null) {
                    RideStatItem(
                        icon = Icons.Default.CurrencyRupee,
                        value = "₹%.0f".format(ride.fareShare)
                    )
                }
            }

            // Driver name for passenger rides
            if (ride.userRole == "passenger" && !ride.driverName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Driver: ${ride.driverName}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }

            // Rate button for completed passenger rides
            if (ride.status == "completed" && ride.userRole == "passenger" && ride.matchId != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = DividerColor)
                Spacer(modifier = Modifier.height(8.dp))
                if (isRated) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle, null,
                            tint = Success, modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Rated",
                            color = Success,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Button(
                        onClick = onRate,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Warning)
                    ) {
                        Icon(Icons.Default.Star, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rate This Ride", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RideStatItem(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun EmptyRidesPage(tabName: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            val icon = when (tabName) {
                "Active" -> Icons.Default.EventNote
                "Completed" -> Icons.Default.CheckCircle
                else -> Icons.Default.Cancel
            }
            Icon(
                icon,
                contentDescription = null,
                tint = TextSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No $tabName Rides",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (tabName) {
                    "Active" -> "Your active rides will appear here.\nBook a ride to get started!"
                    "Completed" -> "Your completed ride history will show up here."
                    else -> "Cancelled rides appear here."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

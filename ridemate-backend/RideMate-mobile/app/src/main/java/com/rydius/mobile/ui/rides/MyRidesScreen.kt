package com.rydius.mobile.ui.rides

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

    LaunchedEffect(Unit) { vm.loadRides() }

    Scaffold(
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
                                    RideCard(ride)
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
private fun RideCard(ride: MyRide) {
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

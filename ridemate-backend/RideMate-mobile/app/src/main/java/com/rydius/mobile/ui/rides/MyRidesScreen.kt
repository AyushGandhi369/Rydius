package com.rydius.mobile.ui.rides

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.rydius.mobile.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyRidesScreen(
    onBack: () -> Unit,
    onNewRide: () -> Unit
) {
    val tabs = listOf("Upcoming", "Repeat", "Completed", "Other")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

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

            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                EmptyRidesPage(tabName = tabs[page])
            }
        }
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
                "Upcoming" -> Icons.Default.EventNote
                "Repeat" -> Icons.Default.Repeat
                "Completed" -> Icons.Default.CheckCircle
                else -> Icons.Default.MoreHoriz
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
                    "Upcoming" -> "Your upcoming rides will appear here.\nBook a ride to get started!"
                    "Repeat" -> "Rides you take regularly will show up here."
                    "Completed" -> "Your ride history will be shown here."
                    else -> "Other ride-related activities appear here."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            // Share & Earn section on Upcoming tab
            if (tabName == "Upcoming") {
                Spacer(modifier = Modifier.height(32.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Secondary.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Secondary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Share & Earn", fontWeight = FontWeight.Bold, color = Secondary)
                        Text(
                            "Invite friends with code RIDEMATE2026",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

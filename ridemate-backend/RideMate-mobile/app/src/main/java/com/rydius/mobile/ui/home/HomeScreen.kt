package com.rydius.mobile.ui.home

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rydius.mobile.ui.components.BottomNavBar
import com.rydius.mobile.ui.components.LocationSearchBar
import com.rydius.mobile.ui.components.RoleSelector
import com.rydius.mobile.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToDriver: (String, String, Double, Double, Double, Double, Int, String) -> Unit,
    onNavigateToPassenger: (String, String, Double, Double, Double, Double, Int, String) -> Unit,
    onNavigateToMyRides: () -> Unit,
    onNavigateToProfile: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        locationPermissionGranted = granted
        if (granted) vm.fetchCurrentLocation(context)
    }

    // Request location permission on first launch
    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(
                selectedIndex = 0,
                onItemSelected = { index ->
                    when (index) {
                        0 -> { /* already on home */ }
                        1 -> onNavigateToMyRides()
                        2 -> onNavigateToProfile()
                    }
                }
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
                    // Active trip recovery banner
                    if (vm.hasActiveTrip && vm.activeTripData != null) {
                        val trip = vm.activeTripData!!
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(top = 16.dp),
                            shape = RoundedCornerShape(18.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = CardLight)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Secondary.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.DirectionsCar,
                                            contentDescription = null,
                                            tint = Secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Active Trip",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Secondary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "Resume where you left off",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = DividerColor)
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(verticalAlignment = Alignment.Top) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.TripOrigin,
                                            contentDescription = null,
                                            tint = MarkerGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(1.dp)
                                                .height(22.dp)
                                                .background(DividerColor)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Icon(
                                            Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = MarkerRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            trip.startLocation,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            trip.endLocation,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    AssistChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                "${trip.availableSeats} seat${if (trip.availableSeats > 1) "s" else ""}",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.AirlineSeatReclineNormal,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = SurfaceLight,
                                            labelColor = TextSecondary,
                                            leadingIconContentColor = TextSecondary
                                        )
                                    )
                                    AssistChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                trip.departureTime ?: "Now",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Schedule,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = SurfaceLight,
                                            labelColor = TextSecondary,
                                            leadingIconContentColor = TextSecondary
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = {
                                            onNavigateToDriver(
                                                trip.startLocation,
                                                trip.endLocation,
                                                trip.startLocationLat,
                                                trip.startLocationLng,
                                                trip.endLocationLat,
                                                trip.endLocationLng,
                                                trip.availableSeats,
                                                trip.departureTime ?: "Now"
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Secondary)
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Resume Trip", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { vm.dismissActiveTrip() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                                    ) {
                                        Text("Dismiss", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }

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

                            // Use current location button
                            TextButton(
                                onClick = {
                                    if (locationPermissionGranted) {
                                        vm.fetchCurrentLocation(context)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                },
                                modifier = Modifier.padding(start = 4.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (vm.isFetchingLocation) "Fetching..." else "Use current location",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Primary
                                )
                            }

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
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                (1..6).forEach { n ->
                                    val selected = vm.seats == n
                                    val bgColor by animateColorAsState(
                                        if (selected) Secondary else SurfaceLight,
                                        label = "seat"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(bgColor)
                                            .clickable { vm.updateSeats(n) },
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
                    var showTimePicker by remember { mutableStateOf(false) }

                    if (showTimePicker) {
                        val calendar = Calendar.getInstance()
                        val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                calendar.set(Calendar.YEAR, year)
                                calendar.set(Calendar.MONTH, month)
                                calendar.set(Calendar.DAY_OF_MONTH, day)

                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                                        calendar.set(Calendar.MINUTE, minute)
                                        if (calendar.timeInMillis <= System.currentTimeMillis() + 60_000) {
                                            vm.updateDepartureTime("Now")
                                        } else {
                                            vm.updateDepartureTime(isoFormat.format(calendar.time))
                                            vm.updateDepartureDisplayText(dateFormat.format(calendar.time))
                                        }
                                        showTimePicker = false
                                    },
                                    calendar.get(Calendar.HOUR_OF_DAY),
                                    calendar.get(Calendar.MINUTE),
                                    false
                                ).apply {
                                    setOnCancelListener { showTimePicker = false }
                                }.show()
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).apply {
                            datePicker.minDate = System.currentTimeMillis()
                            setOnCancelListener { showTimePicker = false }
                        }.show()
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .clickable { showTimePicker = true },
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
                                vm.departureDisplayText,
                                color = Secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Change time",
                                modifier = Modifier.size(16.dp),
                                tint = TextSecondary
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

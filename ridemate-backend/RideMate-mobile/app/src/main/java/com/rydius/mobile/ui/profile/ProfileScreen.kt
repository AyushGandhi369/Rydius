package com.rydius.mobile.ui.profile

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ContactSupport
import androidx.compose.material.icons.automirrored.filled.HelpCenter
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rydius.mobile.RideMateApp
import com.rydius.mobile.ui.auth.AuthViewModel
import com.rydius.mobile.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToEditProfile: () -> Unit = {},
    authVm: AuthViewModel = viewModel(),
    profileVm: ProfileViewModel = viewModel()
) {
    val session = RideMateApp.instance.sessionManager
    val profile = profileVm.profile
    val userName = profile?.name ?: session.userName
    val userEmail = profile?.email ?: session.userEmail
    val initials = userName.split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")
        .ifEmpty { "U" }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon: () -> Unit = {
        scope.launch { snackbarHostState.showSnackbar("Coming soon!") }
    }

    // Sign-out confirmation
    var showLogoutDialog by remember { mutableStateOf(false) }
    // Delete account confirmation
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteConfirmText by remember { mutableStateOf("") }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            shape = RoundedCornerShape(20.dp),
            title = { Text("Sign Out", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    authVm.logout(onLogout)
                }) { Text("Sign Out", color = Error, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!authVm.isDeleting) {
                    showDeleteDialog = false
                    deleteConfirmText = ""
                }
            },
            shape = RoundedCornerShape(20.dp),
            title = { Text("Delete Account", fontWeight = FontWeight.Bold, color = Error) },
            text = {
                Column {
                    Text(
                        "This action is permanent and cannot be undone. All your rides, matches, and data will be deleted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Type DELETE to confirm:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deleteConfirmText,
                        onValueChange = { deleteConfirmText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        placeholder = { Text("DELETE") }
                    )
                    authVm.errorMessage?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(err, color = Error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        authVm.deleteAccount {
                            showDeleteDialog = false
                            onLogout()
                        }
                    },
                    enabled = deleteConfirmText == "DELETE" && !authVm.isDeleting
                ) {
                    if (authVm.isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Delete Forever", color = Error, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        deleteConfirmText = ""
                    },
                    enabled = !authVm.isDeleting
                ) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    navigationIconContentColor = TextOnPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .background(SurfaceLight)
        ) {
            // ═══════════════════════════════════════════════════
            //  HERO HEADER (Uber-style)
            // ═══════════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Primary, PrimaryVariant)
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(8.dp))

                    // Avatar with photo
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .shadow(8.dp, CircleShape)
                            .clip(CircleShape)
                            .clickable { onNavigateToEditProfile() }
                    ) {
                        val photoUrl = profile?.profilePhotoUrl
                        if (photoUrl != null && photoUrl.startsWith("data:image")) {
                            val base64Data = photoUrl.substringAfter("base64,")
                            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                            val bitmap = remember(photoUrl) {
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Profile photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Secondary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    initials, color = TextOnPrimary,
                                    fontSize = 32.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = userName.ifEmpty { "User" },
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextOnPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = userEmail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextOnPrimary.copy(alpha = 0.7f)
                    )

                    // Phone verified badge
                    if (profile?.phone != null) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Phone, null,
                                tint = TextOnPrimary.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                profile.phone ?: "",
                                color = TextOnPrimary.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                            if (profile.isPhoneVerifiedBool) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Verified, "Verified",
                                    tint = Success,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Rating row
                    val rating = profileVm.ratingAverage ?: 0.0
                    val ratingCount = profileVm.ratingCount
                    val fullStars = rating.toInt()
                    val hasHalfStar = (rating - fullStars) >= 0.5
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(5) { i ->
                            Icon(
                                when {
                                    i < fullStars -> Icons.Default.Star
                                    i == fullStars && hasHalfStar -> Icons.AutoMirrored.Filled.StarHalf
                                    else -> Icons.Default.StarBorder
                                },
                                null,
                                tint = Warning,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (ratingCount > 0) "%.1f".format(rating) else "New",
                            color = TextOnPrimary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        if (ratingCount > 0) {
                            Text(
                                " ($ratingCount)",
                                color = TextOnPrimary.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════
            //  PROFILE COMPLETION CARD
            // ═══════════════════════════════════════════════════
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-16).dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = CardLight)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Profile Completion",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                "${profileVm.completionFilled} of ${profileVm.completionTotal} completed",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (profileVm.completionPercentage >= 80) Success.copy(alpha = 0.15f)
                                    else Secondary.copy(alpha = 0.15f)
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "${profileVm.completionPercentage}%",
                                fontWeight = FontWeight.Bold,
                                color = if (profileVm.completionPercentage >= 80) Success else Secondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { profileVm.completionPercentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (profileVm.completionPercentage >= 80) Success else Secondary,
                        trackColor = SurfaceLight
                    )
                }
            }

            // ═══════════════════════════════════════════════════
            //  QUICK INFO TILES (Uber-style grid)
            // ═══════════════════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickInfoTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.DirectionsCar,
                    label = "Vehicle",
                    value = profile?.vehicleNumber ?: "Not added",
                    isSet = profile?.vehicleNumber != null
                )
                QuickInfoTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Wc,
                    label = "Gender",
                    value = when (profile?.gender) {
                        "male" -> "Male"
                        "female" -> "Female"
                        "other" -> "Other"
                        "prefer_not_to_say" -> "—"
                        else -> "Not set"
                    },
                    isSet = profile?.gender != null
                )
                QuickInfoTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Cake,
                    label = "DOB",
                    value = profile?.dateOfBirth ?: "Not set",
                    isSet = profile?.dateOfBirth != null
                )
            }

            Spacer(Modifier.height(16.dp))

            // ═══════════════════════════════════════════════════
            //  MENU ITEMS
            // ═══════════════════════════════════════════════════
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = CardLight)
            ) {
                Column {
                    ProfileMenuItem(
                        icon = Icons.Default.Edit,
                        title = "Edit Profile",
                        subtitle = "Name, photo, phone, vehicle & more",
                        onClick = onNavigateToEditProfile,
                        iconTint = Secondary
                    )
                    MenuDivider()
                    ProfileMenuItem(
                        icon = Icons.Default.Shield,
                        title = "Safety",
                        subtitle = "Emergency contacts & trusted contacts",
                        onClick = comingSoon,
                        iconTint = Success
                    )
                    MenuDivider()
                    ProfileMenuItem(
                        icon = Icons.Default.Verified,
                        title = "Verify Organization",
                        subtitle = "Get a verified badge",
                        onClick = comingSoon,
                        iconTint = Info
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = CardLight)
            ) {
                Column {
                    ProfileMenuItem(
                        icon = Icons.Default.AccountBalanceWallet,
                        title = "Transactions",
                        subtitle = "View payment history",
                        onClick = comingSoon,
                        iconTint = Warning
                    )
                    MenuDivider()
                    ProfileMenuItem(
                        icon = Icons.Default.WorkspacePremium,
                        title = "Buy Prime",
                        subtitle = "Unlock premium features",
                        onClick = comingSoon,
                        badgeText = "NEW",
                        iconTint = Warning
                    )
                    MenuDivider()
                    ProfileMenuItem(
                        icon = Icons.Default.CardGiftcard,
                        title = "Invite & Earn",
                        subtitle = "Share and earn rewards",
                        onClick = comingSoon,
                        iconTint = Success
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = CardLight)
            ) {
                Column {
                    ProfileMenuItem(
                        icon = Icons.AutoMirrored.Filled.HelpCenter,
                        title = "Help & FAQ",
                        subtitle = "Get support",
                        onClick = comingSoon,
                        iconTint = Info
                    )
                    MenuDivider()
                    ProfileMenuItem(
                        icon = Icons.AutoMirrored.Filled.ContactSupport,
                        title = "Contact Us",
                        subtitle = "Reach our team",
                        onClick = comingSoon,
                        iconTint = Secondary
                    )
                    MenuDivider()
                    ProfileMenuItem(
                        icon = Icons.Default.Settings,
                        title = "Settings",
                        subtitle = "App preferences",
                        onClick = comingSoon,
                        iconTint = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ═══════════════════════════════════════════════════
            //  SIGN OUT
            // ═══════════════════════════════════════════════════
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                colors = CardDefaults.cardColors(containerColor = CardLight)
            ) {
                Surface(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    color = CardLight,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout, null,
                            tint = Error, modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Sign Out",
                            color = Error,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ═══════════════════════════════════════════════════
            //  DELETE ACCOUNT
            // ═══════════════════════════════════════════════════
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                colors = CardDefaults.cardColors(containerColor = CardLight)
            ) {
                Surface(
                    onClick = { authVm.clearError(); showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    color = CardLight,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.DeleteForever, null,
                            tint = Error.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Delete Account",
                            color = Error.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // App version
            Text(
                text = "Rydius v${com.rydius.mobile.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  COMPOSABLE COMPONENTS
// ═══════════════════════════════════════════════════════════════

@Composable
private fun QuickInfoTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    isSet: Boolean
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = CardLight)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSet) Secondary.copy(alpha = 0.12f) else SurfaceLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, null,
                    tint = if (isSet) Secondary else TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                fontSize = 11.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
            Text(
                value,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSet) TextPrimary else TextSecondary.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {},
    iconTint: androidx.compose.ui.graphics.Color = Secondary,
    badgeText: String? = null
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = CardLight
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    if (badgeText != null) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Secondary)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                badgeText,
                                color = TextOnPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Icon(
                Icons.Default.ChevronRight, null,
                tint = TextSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 70.dp, end = 16.dp),
        color = DividerColor.copy(alpha = 0.5f),
        thickness = 0.5.dp
    )
}

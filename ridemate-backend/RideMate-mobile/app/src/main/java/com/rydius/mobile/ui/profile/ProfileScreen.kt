package com.rydius.mobile.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpCenter
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rydius.mobile.RideMateApp
import com.rydius.mobile.ui.auth.AuthViewModel
import com.rydius.mobile.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    authVm: AuthViewModel = viewModel()
) {
    val session = RideMateApp.instance.sessionManager
    val userName = session.userName
    val userEmail = session.userEmail
    val initials = userName.split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")
        .ifEmpty { "U" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Profile header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Primary, PrimaryVariant)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Secondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            color = TextOnPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Online dot
                    Box(
                        modifier = Modifier
                            .offset(x = 28.dp, y = (-16).dp)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Success)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = userName.ifEmpty { "User" },
                        style = MaterialTheme.typography.titleLarge,
                        color = TextOnPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = userEmail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextOnPrimary.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Rating
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(5) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = Warning,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("5.0", color = TextOnPrimary, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Profile progress
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Profile Completion", fontWeight = FontWeight.Medium)
                        Text("7/8", color = Secondary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { 0.875f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Secondary,
                        trackColor = SurfaceLight
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Menu items
            ProfileMenuItem(Icons.Default.Verified, "Verify Organization", "Get verified badge")
            ProfileMenuItem(Icons.Default.AccountBalanceWallet, "Transactions", "View payment history")
            ProfileMenuItem(Icons.Default.WorkspacePremium, "Buy Prime", "Unlock premium features")
            ProfileMenuItem(Icons.Default.CardGiftcard, "Invite & Earn", "Share and earn rewards")
            ProfileMenuItem(Icons.AutoMirrored.Filled.HelpCenter, "Help & FAQ", "Get support")
            ProfileMenuItem(Icons.Default.ContactSupport, "Contact Us", "Reach our team")
            ProfileMenuItem(Icons.Default.Settings, "Settings", "App preferences")

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))

            // Sign out
            TextButton(
                onClick = { authVm.logout(onLogout) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = Error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Sign Out", color = Error, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // App version
            Text(
                text = "Rydius v1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        onClick = { /* TODO */ },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Secondary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

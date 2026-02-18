package com.rydius.mobile.ui.profile

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rydius.mobile.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    vm: ProfileViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Photo picker
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            vm.uploadPhoto(inputStream)
        }
    }

    // Show snackbar for messages
    LaunchedEffect(vm.successMessage) {
        vm.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessages()
        }
    }
    LaunchedEffect(vm.error) {
        vm.error?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMessages()
        }
    }

    // Gender options
    var genderExpanded by remember { mutableStateOf(false) }
    val genderOptions = listOf(
        "male" to "Male",
        "female" to "Female",
        "other" to "Other",
        "prefer_not_to_say" to "Prefer not to say"
    )

    // Date picker
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
                            vm.editDob = "%04d-%02d-%02d".format(
                                cal.get(java.util.Calendar.YEAR),
                                cal.get(java.util.Calendar.MONTH) + 1,
                                cal.get(java.util.Calendar.DAY_OF_MONTH)
                            )
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Phone verification dialog
    var showPhoneDialog by remember { mutableStateOf(false) }
    if (showPhoneDialog) {
        PhoneVerificationDialog(
            vm = vm,
            onDismiss = { showPhoneDialog = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { vm.saveProfile() },
                        enabled = !vm.isSaving
                    ) {
                        if (vm.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Secondary
                            )
                        } else {
                            Text("Save", color = Secondary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (vm.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Secondary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Profile Photo Section ───────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box {
                    val photoUrl = vm.profile?.profilePhotoUrl
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
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .clickable { photoLauncher.launch("image/*") },
                                contentScale = ContentScale.Crop
                            )
                        }
                    } else {
                        // Initials avatar
                        val initials = vm.editName.split(" ")
                            .take(2)
                            .mapNotNull { it.firstOrNull()?.uppercase() }
                            .joinToString("")
                            .ifEmpty { "U" }
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(Secondary)
                                .clickable { photoLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(initials, color = TextOnPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Camera badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Primary)
                            .clickable { photoLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (vm.isUploadingPhoto) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = TextOnPrimary
                            )
                        } else {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Change photo",
                                tint = TextOnPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Remove photo link
            if (vm.profile?.profilePhotoUrl != null) {
                TextButton(
                    onClick = { vm.removePhoto() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Remove photo", color = Error, fontSize = 13.sp)
                }
            }

            HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))

            // ── Personal Information ────────────────────────────
            SectionHeader("Personal Information")

            ProfileTextField(
                label = "Full Name",
                value = vm.editName,
                onValueChange = { vm.editName = it },
                icon = Icons.Default.Person,
                placeholder = "Enter your full name"
            )

            // Gender dropdown
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                ExposedDropdownMenuBox(
                    expanded = genderExpanded,
                    onExpandedChange = { genderExpanded = it }
                ) {
                    OutlinedTextField(
                        value = genderOptions.find { it.first == vm.editGender }?.second ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Gender") },
                        leadingIcon = {
                            Icon(Icons.Default.Wc, null, tint = Secondary, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(genderExpanded) },
                         modifier = Modifier
                             .fillMaxWidth()
                             .menuAnchor(type = MenuAnchorType.PrimaryNotEditable),
                         shape = RoundedCornerShape(12.dp),
                         colors = OutlinedTextFieldDefaults.colors(
                             focusedBorderColor = Secondary,
                             unfocusedBorderColor = DividerColor,
                            cursorColor = Secondary
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = genderExpanded,
                        onDismissRequest = { genderExpanded = false }
                    ) {
                        genderOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    vm.editGender = value
                                    genderExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Date of birth
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                OutlinedTextField(
                    value = vm.editDob,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date of Birth") },
                    leadingIcon = {
                        Icon(Icons.Default.CalendarMonth, null, tint = Secondary, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.EditCalendar, "Pick date", tint = Secondary)
                        }
                    },
                    placeholder = { Text("YYYY-MM-DD") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Secondary,
                        unfocusedBorderColor = DividerColor,
                        cursorColor = Secondary
                    )
                )
            }

            ProfileTextField(
                label = "Bio",
                value = vm.editBio,
                onValueChange = { vm.editBio = it },
                icon = Icons.Default.Edit,
                placeholder = "Tell people about yourself",
                singleLine = false,
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))

            // ── Contact Information ─────────────────────────────
            SectionHeader("Contact Information")

            // Phone with verify button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = vm.editPhone,
                    onValueChange = { vm.editPhone = it },
                    label = { Text("Phone Number") },
                    leadingIcon = {
                        Icon(Icons.Default.Phone, null, tint = Secondary, modifier = Modifier.size(20.dp))
                    },
                    placeholder = { Text("+91 9876543210") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Secondary,
                        unfocusedBorderColor = DividerColor,
                        cursorColor = Secondary
                    ),
                    trailingIcon = {
                        if (vm.phoneVerified) {
                            Icon(Icons.Default.Verified, "Verified", tint = Success, modifier = Modifier.size(20.dp))
                        }
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (!vm.phoneVerified) {
                    FilledTonalButton(
                        onClick = { showPhoneDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Secondary.copy(alpha = 0.15f),
                            contentColor = Secondary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("Verify", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            ProfileTextField(
                label = "Emergency Contact",
                value = vm.editEmergencyContact,
                onValueChange = { vm.editEmergencyContact = it },
                icon = Icons.Default.ContactPhone,
                placeholder = "Emergency phone number",
                keyboardType = KeyboardType.Phone
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))

            // ── Vehicle Information ─────────────────────────────
            SectionHeader("Vehicle Information")

            ProfileTextField(
                label = "Vehicle Number",
                value = vm.editVehicleNumber,
                onValueChange = { vm.editVehicleNumber = it.uppercase() },
                icon = Icons.Default.DirectionsCar,
                placeholder = "GJ 01 AB 1234"
            )

            ProfileTextField(
                label = "Vehicle Model",
                value = vm.editVehicleModel,
                onValueChange = { vm.editVehicleModel = it },
                icon = Icons.Default.CarRepair,
                placeholder = "e.g. Maruti Swift, Honda City"
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))

            // ── Saved Places ────────────────────────────────────
            SectionHeader("Saved Places")

            ProfileTextField(
                label = "Home Address",
                value = vm.editHomeAddress,
                onValueChange = { vm.editHomeAddress = it },
                icon = Icons.Default.Home,
                placeholder = "Your home address"
            )

            ProfileTextField(
                label = "Work Address",
                value = vm.editWorkAddress,
                onValueChange = { vm.editWorkAddress = it },
                icon = Icons.Default.Work,
                placeholder = "Your work address"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Save button (bottom)
            Button(
                onClick = { vm.saveProfile() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                enabled = !vm.isSaving
            ) {
                if (vm.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = TextOnPrimary
                    )
                } else {
                    Text("Save Changes", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Reusable components ─────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = TextSecondary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        fontSize = 13.sp,
        letterSpacing = 0.5.sp
    )
}

@Composable
private fun ProfileTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    placeholder: String,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(icon, null, tint = Secondary, modifier = Modifier.size(20.dp))
        },
        placeholder = { Text(placeholder, color = TextSecondary.copy(alpha = 0.5f)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Secondary,
            unfocusedBorderColor = DividerColor,
            cursorColor = Secondary
        )
    )
}

// ── Phone Verification Dialog ───────────────────────────────────

@Composable
fun PhoneVerificationDialog(
    vm: ProfileViewModel,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhoneAndroid, null, tint = Secondary)
                Spacer(Modifier.width(8.dp))
                Text("Verify Phone", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                if (!vm.phoneOtpSent) {
                    Text(
                        "We'll send a 6-digit OTP to verify your phone number.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = vm.editPhone,
                        onValueChange = { vm.editPhone = it },
                        label = { Text("Phone Number") },
                        placeholder = { Text("+91 9876543210") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Text(
                        "Enter the 6-digit code sent to ${vm.editPhone}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    // Show dev OTP in debug
                    vm.devOtp?.let { otp ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Dev OTP: $otp",
                            style = MaterialTheme.typography.bodySmall,
                            color = Warning,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = vm.phoneOtp,
                        onValueChange = { if (it.length <= 6) vm.phoneOtp = it },
                        label = { Text("OTP Code") },
                        placeholder = { Text("123456") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                if (vm.isVerifyingPhone) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Secondary
                    )
                }
            }
        },
        confirmButton = {
            if (!vm.phoneOtpSent) {
                Button(
                    onClick = { vm.sendPhoneOtp() },
                    enabled = !vm.isVerifyingPhone && vm.editPhone.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) { Text("Send OTP") }
            } else {
                Button(
                    onClick = { vm.confirmPhoneOtp() },
                    enabled = !vm.isVerifyingPhone && vm.phoneOtp.length == 6,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) { Text("Verify") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    // Auto-dismiss on verification success
    LaunchedEffect(vm.phoneVerified) {
        if (vm.phoneVerified) {
            delay(500)
            onDismiss()
        }
    }
}

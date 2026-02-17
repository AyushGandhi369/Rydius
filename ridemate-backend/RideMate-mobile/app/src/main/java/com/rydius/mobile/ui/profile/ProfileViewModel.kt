package com.rydius.mobile.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rydius.mobile.RideMateApp
import com.rydius.mobile.data.model.*
import com.rydius.mobile.data.repository.AuthRepository
import com.rydius.mobile.data.repository.TripRepository
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream

class ProfileViewModel : ViewModel() {

    private val repo = AuthRepository()
    private val tripRepo = TripRepository()
    private val session = RideMateApp.instance.sessionManager

    // ── Profile data ────────────────────────────────────────────
    var profile by mutableStateOf<UserProfile?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var successMessage by mutableStateOf<String?>(null)
        private set
    var isSaving by mutableStateOf(false)
        private set

    // ── Rating ──────────────────────────────────────────────────
    var ratingAverage by mutableStateOf<Double?>(null)
        private set
    var ratingCount by mutableIntStateOf(0)
        private set

    // ── Profile completion ──────────────────────────────────────
    var completionPercentage by mutableIntStateOf(0)
        private set
    var completionFilled by mutableIntStateOf(0)
        private set
    var completionTotal by mutableIntStateOf(9)
        private set

    // ── Edit fields ─────────────────────────────────────────────
    var editName by mutableStateOf("")
    var editPhone by mutableStateOf("")
    var editGender by mutableStateOf("")
    var editDob by mutableStateOf("")
    var editVehicleNumber by mutableStateOf("")
    var editVehicleModel by mutableStateOf("")
    var editBio by mutableStateOf("")
    var editEmergencyContact by mutableStateOf("")
    var editHomeAddress by mutableStateOf("")
    var editWorkAddress by mutableStateOf("")

    // ── Phone verification ──────────────────────────────────────
    var phoneOtpSent by mutableStateOf(false)
        private set
    var phoneVerified by mutableStateOf(false)
        private set
    var phoneOtp by mutableStateOf("")
    var isVerifyingPhone by mutableStateOf(false)
        private set
    var devOtp by mutableStateOf<String?>(null)
        private set

    // ── Photo ───────────────────────────────────────────────────
    var isUploadingPhoto by mutableStateOf(false)
        private set

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            isLoading = true
            error = null
            repo.getProfile().fold(
                onSuccess = { response ->
                    response.profile?.let { p ->
                        profile = p
                        phoneVerified = p.isPhoneVerifiedBool
                        populateEditFields(p)
                    }
                },
                onFailure = { error = it.message }
            )
            // Also fetch completion
            repo.getProfileCompletion().fold(
                onSuccess = { c ->
                    completionPercentage = c.percentage
                    completionFilled = c.filled
                    completionTotal = c.total
                },
                onFailure = { /* silent */ }
            )
            // Fetch user rating
            val userId = session.userId
            if (userId > 0) {
                tripRepo.getUserRating(userId).fold(
                    onSuccess = { r ->
                        ratingAverage = r.average
                        ratingCount = r.count
                    },
                    onFailure = { /* silent */ }
                )
            }
            isLoading = false
        }
    }

    private fun populateEditFields(p: UserProfile) {
        editName = p.name
        editPhone = p.phone ?: ""
        editGender = p.gender ?: ""
        editDob = p.dateOfBirth ?: ""
        editVehicleNumber = p.vehicleNumber ?: ""
        editVehicleModel = p.vehicleModel ?: ""
        editBio = p.bio ?: ""
        editEmergencyContact = p.emergencyContact ?: ""
        editHomeAddress = p.homeAddress ?: ""
        editWorkAddress = p.workAddress ?: ""
    }

    fun saveProfile() {
        viewModelScope.launch {
            isSaving = true
            error = null
            successMessage = null

            val request = UpdateProfileRequest(
                name = editName.takeIf { it.isNotBlank() },
                phone = editPhone.takeIf { it.isNotBlank() },
                gender = editGender.takeIf { it.isNotBlank() },
                dateOfBirth = editDob.takeIf { it.isNotBlank() },
                vehicleNumber = editVehicleNumber.takeIf { it.isNotBlank() },
                vehicleModel = editVehicleModel.takeIf { it.isNotBlank() },
                bio = editBio.takeIf { it.isNotBlank() },
                emergencyContact = editEmergencyContact.takeIf { it.isNotBlank() },
                homeAddress = editHomeAddress.takeIf { it.isNotBlank() },
                workAddress = editWorkAddress.takeIf { it.isNotBlank() }
            )

            repo.updateProfile(request).fold(
                onSuccess = {
                    successMessage = "Profile updated"
                    // update session name
                    if (editName.isNotBlank()) session.userName = editName
                    loadProfile()
                },
                onFailure = { error = it.message }
            )
            isSaving = false
        }
    }

    fun uploadPhoto(inputStream: InputStream?) {
        if (inputStream == null) return
        viewModelScope.launch {
            isUploadingPhoto = true
            error = null
            try {
                val bitmap = inputStream.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
                if (bitmap == null) {
                    error = "Invalid image"
                    return@launch
                }
                // Scale down if too large
                val maxDim = 512
                val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                    val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * ratio).toInt(),
                        (bitmap.height * ratio).toInt(),
                        true
                    )
                } else bitmap

                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val base64 = "data:image/jpeg;base64," +
                    Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                repo.uploadProfilePhoto(base64).fold(
                    onSuccess = {
                        successMessage = "Photo updated"
                        loadProfile()
                    },
                    onFailure = { error = it.message }
                )
            } catch (e: Exception) {
                error = "Failed to process image"
            } finally {
                isUploadingPhoto = false
            }
        }
    }

    fun removePhoto() {
        viewModelScope.launch {
            isUploadingPhoto = true
            repo.deleteProfilePhoto().fold(
                onSuccess = {
                    successMessage = "Photo removed"
                    loadProfile()
                },
                onFailure = { error = it.message }
            )
            isUploadingPhoto = false
        }
    }

    fun sendPhoneOtp() {
        if (editPhone.isBlank()) {
            error = "Enter a phone number first"
            return
        }
        viewModelScope.launch {
            isVerifyingPhone = true
            error = null
            repo.sendPhoneOtp(editPhone).fold(
                onSuccess = { resp ->
                    phoneOtpSent = true
                    devOtp = resp.devOtp
                    successMessage = resp.message
                },
                onFailure = { error = it.message }
            )
            isVerifyingPhone = false
        }
    }

    fun confirmPhoneOtp() {
        if (phoneOtp.length != 6) {
            error = "Enter a valid 6-digit OTP"
            return
        }
        viewModelScope.launch {
            isVerifyingPhone = true
            error = null
            repo.verifyPhoneOtp(phoneOtp).fold(
                onSuccess = {
                    phoneVerified = true
                    phoneOtpSent = false
                    phoneOtp = ""
                    devOtp = null
                    successMessage = "Phone verified!"
                    loadProfile()
                },
                onFailure = { error = it.message }
            )
            isVerifyingPhone = false
        }
    }

    fun clearMessages() {
        error = null
        successMessage = null
    }
}

package de.felitendo.kontor.ui.lock

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object Biometrics {
    private val authenticators = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    enum class Availability { READY, NOT_ENROLLED, UNAVAILABLE }

    fun availability(activity: FragmentActivity): Availability {
        val bm = BiometricManager.from(activity)
        return when (bm.canAuthenticate(BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.READY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                // Without face/fingerprint the device PIN still protects the app.
                if (bm.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) Availability.READY else Availability.NOT_ENROLLED
            }
            else -> if (bm.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) Availability.READY else Availability.UNAVAILABLE
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String? = null,
        onResult: (success: Boolean) -> Unit,
    ) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { subtitle?.let { setSubtitle(it) } }
            .setAllowedAuthenticators(authenticators)
            .apply { if (Build.VERSION.SDK_INT < 30) setNegativeButtonText("Abbrechen") }
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info)
    }
}

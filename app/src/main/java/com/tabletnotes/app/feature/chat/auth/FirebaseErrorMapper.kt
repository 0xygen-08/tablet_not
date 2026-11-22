package com.tabletnotes.app.feature.chat.auth

import androidx.annotation.StringRes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.tabletnotes.app.R

class InvalidUsernameFormatException : IllegalArgumentException("Invalid username format")
class UsernameAlreadyTakenException : IllegalStateException("Username already taken")
class InvalidDisplayNameException : IllegalArgumentException("Invalid display name")
class FirebaseNotConfiguredException : IllegalStateException("Firebase not configured")
class NotSignedInException : IllegalStateException("Not signed in")

/**
 * Ham Firebase hata mesajlarını kullanıcıya asla göstermez; her teknik istisnayı
 * anlaşılır Türkçe/İngilizce kaynak metnine dönüştürür.
 */
object FirebaseErrorMapper {

    @StringRes
    fun toFriendlyMessageRes(error: Throwable?): Int {
        if (error == null) return R.string.auth_error_generic
        return when (error) {
            is InvalidUsernameFormatException -> R.string.auth_error_invalid_username
            is UsernameAlreadyTakenException -> R.string.auth_error_username_taken
            is InvalidDisplayNameException -> R.string.auth_error_invalid_display_name
            is FirebaseNotConfiguredException -> R.string.auth_error_firebase_not_configured
            is NotSignedInException -> R.string.auth_error_not_signed_in
            is FirebaseAuthWeakPasswordException -> R.string.auth_error_weak_password
            is FirebaseAuthUserCollisionException -> R.string.auth_error_email_already_in_use
            is FirebaseAuthInvalidUserException -> R.string.auth_error_user_not_found
            is FirebaseAuthInvalidCredentialsException -> {
                if (error.errorCode.contains("EMAIL", ignoreCase = true)) {
                    R.string.auth_error_invalid_email
                } else {
                    R.string.auth_error_invalid_credentials
                }
            }
            is FirebaseAuthException -> when (error.errorCode) {
                "ERROR_INVALID_EMAIL" -> R.string.auth_error_invalid_email
                "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> R.string.auth_error_invalid_credentials
                "ERROR_USER_NOT_FOUND" -> R.string.auth_error_user_not_found
                "ERROR_EMAIL_ALREADY_IN_USE" -> R.string.auth_error_email_already_in_use
                "ERROR_TOO_MANY_REQUESTS" -> R.string.auth_error_too_many_requests
                "ERROR_NETWORK_REQUEST_FAILED" -> R.string.auth_error_network
                else -> R.string.auth_error_generic
            }
            is FirebaseNetworkException -> R.string.auth_error_network
            is FirebaseFirestoreException -> when (error.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> R.string.auth_error_permission_denied
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> R.string.auth_error_network
                FirebaseFirestoreException.Code.ALREADY_EXISTS -> R.string.auth_error_username_taken
                else -> R.string.auth_error_generic
            }
            else -> {
                val msg = error.message.orEmpty().lowercase()
                if ("network" in msg || "timeout" in msg || "unable to resolve" in msg) {
                    R.string.auth_error_network
                } else {
                    R.string.auth_error_generic
                }
            }
        }
    }
}

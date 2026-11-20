package com.biohazard786.interesttracker.data

import android.content.Context
import android.content.MutableContextWrapper
import android.util.Base64
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.biohazard786.interesttracker.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.SecureRandom

data class GoogleIdToken(val token: String, val nonce: String)

/**
 * Sign in with Google via Credential Manager. Both entry points use the bottom sheet; the full
 * "Sign in with Google" dialog is only a fallback for when the sheet has nothing to show.
 */
object GoogleAuth {
  private val clientId get() = BuildConfig.GOOGLE_WEB_CLIENT_ID

  /** App launch: authorized accounts first (allows auto sign-in), then every account. Null if none. */
  suspend fun bottomSheet(activity: Context): GoogleIdToken? =
    sheet(activity, authorizedOnly = true) ?: sheet(activity, authorizedOnly = false)

  /** Button tap: the account sheet, or the full dialog when the sheet can't be shown. */
  suspend fun button(activity: Context): GoogleIdToken {
    // The sheet comes back empty when the device has no account, or during Google's cooldown
    // after the user dismissed it a few times; the dialog works in both cases (and can add an account).
    sheet(activity, authorizedOnly = false)?.let { return it }
    val nonce = nonce()
    return request(activity, GetSignInWithGoogleOption.Builder(clientId).setNonce(nonce).build(), nonce)
  }

  private suspend fun sheet(activity: Context, authorizedOnly: Boolean): GoogleIdToken? {
    val nonce = nonce()
    val option =
      GetGoogleIdOption.Builder()
        .setServerClientId(clientId)
        .setFilterByAuthorizedAccounts(authorizedOnly)
        .setAutoSelectEnabled(authorizedOnly)
        .setNonce(nonce)
        .build()
    return try {
      request(activity, option, nonce)
    } catch (_: NoCredentialException) {
      null
    }
  }

  /** Lets the next sign-in offer every account again instead of auto-selecting the last one. */
  suspend fun clearState(context: Context) {
    runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
  }

  private suspend fun request(activity: Context, option: CredentialOption, nonce: String): GoogleIdToken {
    // MutableContextWrapper lets Credential Manager survive Activity recreation without leaking it.
    val result =
      CredentialManager.create(activity)
        .getCredential(MutableContextWrapper(activity), GetCredentialRequest.Builder().addCredentialOption(option).build())
    val credential = result.credential
    require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
      "Unexpected credential type"
    }
    return GoogleIdToken(GoogleIdTokenCredential.createFrom(credential.data).idToken, nonce)
  }

  private fun nonce(): String {
    val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
    return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
  }
}

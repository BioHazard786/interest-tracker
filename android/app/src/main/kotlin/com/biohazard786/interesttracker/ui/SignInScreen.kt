package com.biohazard786.interesttracker.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.biohazard786.interesttracker.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

/** Throws GetCredentialException (cancellation included) if the user backs out. */
private suspend fun fetchGoogleIdToken(context: Context): String {
  val request =
    GetCredentialRequest.Builder()
      .addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build())
      .build()
  val credential = CredentialManager.create(context).getCredential(context, request).credential
  require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
    "Unexpected credential type"
  }
  return GoogleIdTokenCredential.createFrom(credential.data).idToken
}

@Composable
fun SignInScreen(vm: AuthViewModel) {
  val state by vm.state.collectAsState()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  Column(
    modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Box(
      modifier =
        Modifier.size(168.dp)
          .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie12Sided.toShape()),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Filled.Favorite,
        contentDescription = null,
        modifier = Modifier.size(72.dp),
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
    Spacer(Modifier.size(32.dp))
    Text("Interest Tracker", style = MaterialTheme.typography.displaySmallEmphasized, textAlign = TextAlign.Center)
    Spacer(Modifier.size(8.dp))
    Text(
      "Track and purify the interest from your bank accounts.",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.size(40.dp))

    if (state.loading) {
      LoadingIndicator(modifier = Modifier.size(64.dp))
    } else {
      Button(
        onClick = {
          if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            vm.fail("GOOGLE_WEB_CLIENT_ID is not set in android/local.properties")
            return@Button
          }
          vm.startLoading()
          scope.launch {
            try {
              vm.signIn(fetchGoogleIdToken(context))
            } catch (_: GetCredentialCancellationException) {
              vm.fail("Sign-in cancelled")
            } catch (e: GetCredentialException) {
              vm.fail(e.message ?: "Google sign-in failed")
            } catch (e: IllegalArgumentException) {
              vm.fail(e.message ?: "Google sign-in failed")
            }
          }
        },
        modifier = Modifier.heightIn(min = ButtonDefaults.LargeContainerHeight),
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.LargeContainerHeight),
      ) {
        Text("Continue with Google", style = ButtonDefaults.textStyleFor(ButtonDefaults.LargeContainerHeight))
      }
    }

    state.error?.let {
      Spacer(Modifier.size(16.dp))
      Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
  }
}

package com.biohazard786.interesttracker.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biohazard786.interesttracker.BuildConfig
import com.biohazard786.interesttracker.R
import com.biohazard786.interesttracker.data.GoogleAuth
import com.biohazard786.interesttracker.data.GoogleIdToken
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(vm: AuthViewModel) {
  val state by vm.state.collectAsStateWithLifecycle()
  val activity = LocalContext.current
  val scope = rememberCoroutineScope()

  fun signIn(fetch: suspend () -> GoogleIdToken?) {
    if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
      vm.idle("GOOGLE_WEB_CLIENT_ID is not set in android/local.properties")
      return
    }
    vm.startLoading()
    scope.launch {
      try {
        fetch()?.let(vm::signIn) ?: vm.idle()
      } catch (_: GetCredentialCancellationException) {
        vm.idle() // Dismissed: the button below is the way back in.
      } catch (e: GetCredentialException) {
        vm.idle(e.message ?: "Google sign-in failed")
      } catch (e: IllegalArgumentException) {
        vm.idle(e.message ?: "Google sign-in failed")
      }
    }
  }

  // Google's recommended entry point: offer the account bottom sheet as soon as the screen opens.
  LaunchedEffect(Unit) {
    if (!vm.autoPrompted) {
      vm.autoPrompted = true
      signIn { GoogleAuth.bottomSheet(activity) }
    }
  }

  Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 28.dp)) {
    Spacer(Modifier.weight(1f))
    SpinningBadge()
    Spacer(Modifier.height(40.dp))
    Text(
      buildAnnotatedString {
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("Purify\n") }
        append("your interest.")
      },
      style = MaterialTheme.typography.displayLargeEmphasized,
    )
    Spacer(Modifier.height(16.dp))
    Text(
      "Track what your bank credits as interest, then give every rupee of it away.",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.weight(1f))

    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
      if (state.loading) LoadingIndicator(Modifier.size(56.dp))
      else GoogleButton(onClick = { signIn { GoogleAuth.button(activity) } }, modifier = Modifier.fillMaxWidth())
    }
    Text(
      state.error.orEmpty(),
      color = MaterialTheme.colorScheme.error,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
    )
  }
}

@Composable
private fun SpinningBadge() {
  val rotation by
    rememberInfiniteTransition(label = "badge")
      .animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "rotation")
  val shape = MaterialShapes.Cookie12Sided.toShape()
  Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
    // Only the shape spins (in the draw phase, no recomposition); the heart stays upright.
    Box(Modifier.matchParentSize().graphicsLayer { rotationZ = rotation }.background(MaterialTheme.colorScheme.primaryContainer, shape))
    Icon(Icons.Filled.Favorite, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
  }
}

/** Branded per the Sign in with Google guidelines: neutral pill, standard G mark, Roboto Medium label. */
@Composable
private fun GoogleButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val dark = isSystemInDarkTheme()
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = if (dark) Color(0xFF131314) else Color.White,
    border = BorderStroke(1.dp, if (dark) Color(0xFF8E918F) else Color(0xFF747775)),
    modifier = modifier.height(56.dp),
  ) {
    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
      Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(12.dp))
      Text(
        "Continue with Google",
        color = if (dark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F),
        style = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp),
      )
    }
  }
}

package com.repzy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repzy.app.core.Perf
import com.repzy.app.ui.AppDestination
import com.repzy.app.ui.AppNavHost
import com.repzy.app.ui.ErrorScreen
import com.repzy.app.ui.RootViewModel
import com.repzy.app.ui.auth.AuthScreen
import com.repzy.app.ui.onboarding.OnboardingScreen
import com.repzy.app.ui.theme.RepzyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Perf.mark("MainActivity.onCreate")
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            Perf.mark("setContent")
            RepzyTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RepzyRoot()
                }
            }
        }
    }
}

@Composable
private fun RepzyRoot(viewModel: RootViewModel = hiltViewModel()) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val pendingPlan by viewModel.pendingPlan.collectAsStateWithLifecycle()
    val signInCancellable by viewModel.isSignInCancellable.collectAsStateWithLifecycle()

    when (destination) {
        AppDestination.LOADING -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        // Onboarding hesap açılmadan önce çalışır; bitince RootViewModel auth'a yönlendirir.
        AppDestination.ONBOARDING -> OnboardingScreen(
            onSignInClick = viewModel::requestSignIn,
        )

        AppDestination.AUTH -> AuthScreen(
            pendingPlan = pendingPlan,
            onCancel = if (signInCancellable) viewModel::cancelSignIn else null,
        )

        AppDestination.HOME -> AppNavHost(onSignOut = viewModel::signOut)

        AppDestination.ERROR -> ErrorScreen(
            message = errorMessage,
            onRetry = viewModel::retry,
            onSignOut = viewModel::signOut,
        )
    }
}

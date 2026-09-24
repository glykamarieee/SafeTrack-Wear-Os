package com.safetrack.watch.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.material3.AppScaffold
import com.safetrack.watch.SafeTrackApp
import com.safetrack.watch.app.navigation.SafeTrackRoot
import com.safetrack.watch.app.theme.SafeTrackTheme
import com.safetrack.watch.domain.model.SosState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as SafeTrackApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !container.devices.loaded.value }

        // During an SOS the screen turns on, shows over the lock screen and stays on.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                container.sosController.state.collect { state ->
                    val active = state is SosState.Countdown || state is SosState.Sending
                    setShowWhenLocked(state !is SosState.Idle)
                    setTurnScreenOn(active)
                    if (active) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }

        setContent {
            SafeTrackTheme {
                AppScaffold {
                    SafeTrackRoot(container)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        private const val EXTRA_SOS = "com.safetrack.watch.extra.SOS"

        /** Opens (or brings forward) the app on the SOS countdown. */
        fun sosIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_SOS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

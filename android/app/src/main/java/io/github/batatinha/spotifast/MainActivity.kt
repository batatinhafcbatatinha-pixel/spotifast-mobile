package io.github.batatinha.spotifast

import android.Manifest
import android.content.ComponentName
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.github.batatinha.spotifast.ui.SpotifastRoot
import io.github.batatinha.spotifast.ui.SpotifastTheme
import kotlinx.coroutines.launch

@UnstableApi
class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var controller: ListenableFuture<MediaController>? = null

    private val notifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        lifecycleScope.launch {
            vm.openUrl.collect { url ->
                CustomTabsIntent.Builder().build().launchUrl(this@MainActivity, Uri.parse(url))
            }
        }
        setContent {
            SpotifastTheme(vm.appearance, vm.accent, vm.cover, vm.customTheme?.palette) {
                SpotifastRoot(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.visible = true
        // Binding a controller creates the media session service, which then
        // keeps playback alive in the background.
        // Posted, so the session starts after the first frame is drawn rather
        // than delaying it: creating the service took ~75 ms of the main
        // thread before the first frame (Perfetto, emulator).
        window.decorView.post {
            if (controller != null || !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return@post
            val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
            controller = MediaController.Builder(this, token).buildAsync()
        }
    }

    override fun onStop() {
        vm.visible = false
        controller?.let(MediaController::releaseFuture)
        controller = null
        super.onStop()
    }
}

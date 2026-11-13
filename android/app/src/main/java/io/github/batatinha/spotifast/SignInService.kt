package io.github.batatinha.spotifast

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the app awake and online while someone signs in in the browser.
 *
 * Sign-in happens in a browser tab, so the app sits in the background, and
 * Android freezes a background app and cuts its network. Spotify's redirect
 * then waits on a frozen app, and the code cannot be traded for a token.
 * A short foreground service for the length of the sign-in prevents both.
 */
class SignInService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Signing in", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Signing in to Spotify")
            .setContentText("Finish in the browser, then come back.")
            .setOngoing(true)
            .setSilent(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE else 0
        ServiceCompat.startForeground(this, NOTIFICATION, notification, type)
        return START_NOT_STICKY
    }

    /** A short service may run for about three minutes; past that, let go. */
    override fun onTimeout(startId: Int) {
        stopSelf()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    companion object {
        private const val CHANNEL = "sign_in"
        private const val NOTIFICATION = 2

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, SignInService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SignInService::class.java))
        }
    }
}

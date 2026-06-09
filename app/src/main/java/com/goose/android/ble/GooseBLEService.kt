package com.goose.android.ble

import android.app.*
import android.bluetooth.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import com.goose.android.GooseApplication
import com.goose.android.R

/**
 * Foreground service that keeps the BLE connection alive when the app is backgrounded.
 * Required on Android 12+ for BLE connections to persist.
 *
 * Bind this service from the Activity/ViewModel to get the GooseBLEManager instance.
 */
class GooseBLEService : Service() {

    private lateinit var bleManager: GooseBLEManager
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getManager(): GooseBLEManager = bleManager
    }

    override fun onCreate() {
        super.onCreate()
        bleManager = GooseBLEManager(applicationContext)
        startForeground(NOTIFICATION_ID, buildNotification("Goose is running"))
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        bleManager.disconnect()
        super.onDestroy()
    }

    fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, GooseApplication.CHANNEL_ID)
            .setContentTitle("Goose")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}

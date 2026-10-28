package com.example.chargingmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.Locale
import kotlin.math.abs

/**
 * Dịch vụ chạy nền, hiện thông số sạc trên thanh thông báo và cập nhật mỗi giây.
 */
class ChargingService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            updateNotification()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            running = false
            handler.removeCallbacks(tick)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        createChannel()
        val notification = buildNotification(readInfo())
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        running = true
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        running = false
        super.onDestroy()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(readInfo()))
    }

    private fun buildNotification(info: Info): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ChargingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (info.charging) {
            "Đang sạc ${info.percent}% · ${info.watts}"
        } else {
            "${info.percent}% · Không sạc"
        }
        val text = "Dòng ${info.current} · ${info.voltage} · ${info.temp}"

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .addAction(0, "Tắt", stop)
            .build()
    }

    private fun readInfo(): Info {
        val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = (b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val percent = if (level >= 0) level * 100 / scale else -1
        val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = b?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val voltageV = (b?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f
        val tempC = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val currentUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = if (currentUa != Int.MIN_VALUE) currentUa / 1000f else null

        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val watts = if (currentMa != null && plugged != 0) {
            String.format(Locale.US, "%.1f W", abs(voltageV * currentMa / 1000f))
        } else {
            "--"
        }

        return Info(
            charging = charging,
            percent = percent,
            watts = watts,
            current = currentMa?.let { String.format(Locale.US, "%+.0f mA", it) } ?: "--",
            voltage = String.format(Locale.US, "%.2f V", voltageV),
            temp = String.format(Locale.US, "%.1f °C", tempC)
        )
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Thông số sạc", NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        nm.createNotificationChannel(channel)
    }

    private data class Info(
        val charging: Boolean,
        val percent: Int,
        val watts: String,
        val current: String,
        val voltage: String,
        val temp: String
    )

    companion object {
        const val ACTION_STOP = "com.example.chargingmonitor.STOP"
        private const val CHANNEL_ID = "charging"
        private const val NOTIF_ID = 1

        @Volatile
        var running = false
    }
}

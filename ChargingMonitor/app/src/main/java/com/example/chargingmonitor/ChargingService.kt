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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
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
            .setSmallIcon(
                textIcon(
                    info.shortValue,
                    if (IconSettings.showUnit(this)) info.shortUnit else "",
                    IconSettings.squeeze(this)
                )
            )
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
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
        val wattsValue = if (currentMa != null && plugged != 0) abs(voltageV * currentMa / 1000f) else null
        val watts = wattsValue?.let { String.format(Locale.US, "%.1f W", it) } ?: "--"
        // Số hiện trên thanh trạng thái: +công suất khi đang sạc, -công suất khi đang dùng pin
        val dischargeW = if (currentMa != null) abs(voltageV * currentMa / 1000f) else null
        val (shortValue, shortUnit) = when {
            charging && wattsValue != null -> String.format(Locale.US, "+%.1f", wattsValue) to "W"
            !charging && plugged == 0 && dischargeW != null -> String.format(Locale.US, "-%.1f", dischargeW) to "W"
            percent >= 0 -> "$percent" to "%"
            else -> "--" to ""
        }

        return Info(
            charging = charging,
            percent = percent,
            watts = watts,
            current = currentMa?.let { String.format(Locale.US, "%+.0f mA", it) } ?: "--",
            voltage = String.format(Locale.US, "%.2f V", voltageV),
            temp = String.format(Locale.US, "%.1f °C", tempC),
            shortValue = shortValue,
            shortUnit = shortUnit
        )
    }

    /**
     * Vẽ số và đơn vị trên MỘT dòng thành biểu tượng thanh trạng thái.
     * Hệ thống thu nhỏ biểu tượng theo chiều rộng, nên chữ được ép hẹp (squeeze) để chiều cao chữ lớn hơn.
     * Đơn vị rỗng thì chỉ vẽ số.
     */
    private fun textIcon(value: String, unit: String, squeeze: Float): Icon {
        val density = resources.displayMetrics.density
        val font = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f * density
            textScaleX = squeeze
            typeface = font
        }
        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f * density
            textScaleX = squeeze
            typeface = font
        }
        val gap = if (unit.isEmpty()) 0f else 1 * density
        val valueWidth = valuePaint.measureText(value)
        val unitWidth = if (unit.isEmpty()) 0f else unitPaint.measureText(unit)
        val width = (valueWidth + gap + unitWidth + 1 * density).toInt().coerceAtLeast(1)
        val height = (24 * density).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Căn giữa theo chiều dọc; số và đơn vị dùng chung một đường nền
        val fm = valuePaint.fontMetrics
        val baseline = height / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(value, 0f, baseline, valuePaint)
        if (unit.isNotEmpty()) canvas.drawText(unit, valueWidth + gap, baseline, unitPaint)
        return Icon.createWithBitmap(bitmap)
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Kênh cũ không có quyền hiện trên màn hình khóa, nên xóa đi và tạo kênh mới (đổi ID)
        nm.deleteNotificationChannel(OLD_CHANNEL_ID)
        val channel = NotificationChannel(CHANNEL_ID, "Thông số sạc", NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(channel)
    }

    private data class Info(
        val charging: Boolean,
        val percent: Int,
        val watts: String,
        val current: String,
        val voltage: String,
        val temp: String,
        val shortValue: String,
        val shortUnit: String
    )

    companion object {
        const val ACTION_STOP = "com.example.chargingmonitor.STOP"
        private const val CHANNEL_ID = "charging_lock"
        private const val OLD_CHANNEL_ID = "charging"
        private const val NOTIF_ID = 1

        @Volatile
        var running = false
    }
}

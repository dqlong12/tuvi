package com.example.chargingmonitor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs

/**
 * Hiển thị thông số sạc pin theo thời gian thực, cập nhật mỗi giây.
 */
class MainActivity : Activity() {

    private lateinit var battery: BatteryManager
    private val handler = Handler(Looper.getMainLooper())
    private val values = mutableMapOf<String, TextView>()

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Giữ màn hình sáng để theo dõi khi đang cắm sạc
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        battery = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        root.addView(TextView(this).apply {
            text = "Thông số sạc pin"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setPadding(0, 0, 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "Cập nhật mỗi giây"
            setTextColor(Color.parseColor("#7F8B92"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, 0, 0, dp(12))
        })

        for ((key, label) in FIELDS) {
            root.addView(row(label, key))
        }

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#101418"))
            addView(root)
        }
    }

    private fun row(label: String, key: String): View {
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(9), 0, dp(9))
        }
        line.addView(TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#9AA5AD"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val value = TextView(this).apply {
            text = "--"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.END
        }
        values[key] = value
        line.addView(value)
        return line
    }

    private fun refresh() {
        // Thông tin sạc, nhiệt độ, điện áp nằm trong intent sticky ACTION_BATTERY_CHANGED
        val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return

        val level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val pct = if (level >= 0) level * 100f / scale else -1f
        val status = b.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val voltageMv = b.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val tempC = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val health = b.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)

        // Các giá trị này đọc trực tiếp từ phần cứng, đơn vị µA và µAh
        val currentUa = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val avgUa = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        val counterUah = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)

        val currentMa = if (currentUa != Int.MIN_VALUE) currentUa / 1000f else null
        val avgMa = if (avgUa != Int.MIN_VALUE && avgUa != 0) avgUa / 1000f else null
        val voltageV = voltageMv / 1000f
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING

        set("status", statusText(status))
        set("plugged", pluggedText(plugged))
        set("percent", if (pct >= 0) String.format(Locale.US, "%.0f %%", pct) else "--")
        set("current", currentMa?.let { String.format(Locale.US, "%+.0f mA", it) } ?: "Không hỗ trợ")
        set("avgCurrent", avgMa?.let { String.format(Locale.US, "%+.0f mA", it) } ?: "Không hỗ trợ")
        set(
            "power",
            if (currentMa != null && plugged != 0) {
                String.format(Locale.US, "%.2f W", abs(voltageV * currentMa / 1000f))
            } else {
                "--"
            }
        )
        set("voltage", String.format(Locale.US, "%.3f V", voltageV))
        set("temp", String.format(Locale.US, "%.1f °C", tempC))
        set("health", healthText(health))
        set(
            "counter",
            if (counterUah != Int.MIN_VALUE && counterUah > 0) {
                String.format(Locale.US, "%.0f mAh", counterUah / 1000f)
            } else {
                "Không hỗ trợ"
            }
        )
        set("eta", estimateFullText(charging, pct, counterUah, currentMa))
        set("updated", java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(java.util.Date()))
    }

    /** Ước tính thời gian còn lại để đầy pin, dựa trên dung lượng hiện tại và dòng sạc. */
    private fun estimateFullText(charging: Boolean, pct: Float, counterUah: Int, currentMa: Float?): String {
        if (!charging || currentMa == null || currentMa <= 0f) return "--"
        if (pct <= 0f || pct >= 100f || counterUah == Int.MIN_VALUE || counterUah <= 0) return "--"
        val fullUah = counterUah * 100f / pct
        val remainUah = fullUah * (100f - pct) / 100f
        val hours = remainUah / 1000f / currentMa
        val totalMinutes = (hours * 60).toInt()
        return String.format(Locale.US, "%d giờ %02d phút", totalMinutes / 60, totalMinutes % 60)
    }

    private fun set(key: String, text: String) {
        values[key]?.text = text
    }

    private fun statusText(status: Int): String = when (status) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Đang sạc"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Đang xả (không sạc)"
        BatteryManager.BATTERY_STATUS_FULL -> "Đã đầy"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Đang cắm nhưng chưa sạc"
        else -> "Không rõ"
    }

    private fun pluggedText(plugged: Int): String = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "Bộ sạc tường (AC)"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB / PD"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Sạc không dây"
        0 -> "Không cắm"
        else -> "Khác"
    }

    private fun healthText(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Tốt"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Quá nóng"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Hỏng"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Quá áp"
        BatteryManager.BATTERY_HEALTH_COLD -> "Quá lạnh"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Lỗi"
        else -> "Không rõ"
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    companion object {
        private val FIELDS = listOf(
            "status" to "Trạng thái",
            "plugged" to "Nguồn sạc",
            "percent" to "Pin hiện tại",
            "current" to "Dòng điện tức thời (+ là đang nạp)",
            "avgCurrent" to "Dòng điện trung bình",
            "power" to "Công suất sạc",
            "voltage" to "Điện áp pin",
            "temp" to "Nhiệt độ pin",
            "health" to "Sức khỏe pin",
            "counter" to "Dung lượng còn lại (ước tính)",
            "eta" to "Ước tính đầy sau",
            "updated" to "Cập nhật lúc"
        )
    }
}

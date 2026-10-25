package com.example.customkeyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class SettingsActivity : Activity() {

    private lateinit var settings: KeySizeSettings
    private lateinit var previewHost: FrameLayout
    private lateinit var previewBuilder: KeyboardBuilder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = KeySizeSettings(this)

        // Bản xem trước: nút chạm thử không làm gì
        previewBuilder = KeyboardBuilder(this, settings, object : KeyboardBuilder.Listener {
            override fun onText(text: String) {}
            override fun onDelete() {}
            override fun onEnter() {}
            override fun onShiftToggle() {}
            override fun onSymbolsToggle() {}
        })

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        root.addView(title("Xem trước"))
        previewHost = FrameLayout(this)
        root.addView(previewHost)

        root.addView(title("Tinh chỉnh kích thước"))
        addSlider(root, "Hàng 1 (QWERTY) cao", KeySizeSettings.rowKey(0), 46, 30, 80)
        addSlider(root, "Hàng 2 (ASDF) cao", KeySizeSettings.rowKey(1), 46, 30, 80)
        addSlider(root, "Hàng 3 (ZXCV) cao", KeySizeSettings.rowKey(2), 46, 30, 80)
        addSlider(root, "Hàng dưới (?123, Space) cao", KeySizeSettings.rowKey(3), 46, 30, 80)
        addSlider(root, "Khoảng cách giữa các phím", KeySizeSettings.GAP, 3, 0, 12)
        addSlider(root, "Thụt lề hàng ASDF", KeySizeSettings.INDENT, 16, 0, 40)
        addSlider(
            root, "Phím Shift / Xóa / ?123 / Enter", KeySizeSettings.SIDE,
            15, 5, 30, divisor = 10, unit = "× phím thường"
        )
        addSlider(
            root, "Phím Space", KeySizeSettings.SPACE,
            40, 10, 90, divisor = 10, unit = "× phím thường"
        )
        addSlider(
            root, "Đệm dưới cùng (tránh thanh điều hướng)", KeySizeSettings.BOTTOM,
            0, 0, 48
        )

        root.addView(title("Thiết lập"))
        root.addView(button("1. Mở cài đặt để bật bàn phím") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        root.addView(button("2. Chọn bàn phím đang dùng") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        root.addView(button("Khôi phục kích thước mặc định") {
            settings.reset()
            recreate()
        })

        setContentView(ScrollView(this).apply { addView(root) })
        refreshPreview()
    }

    private fun refreshPreview() {
        previewHost.removeAllViews()
        previewHost.addView(
            previewBuilder.build(false, false),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun addSlider(
        root: LinearLayout,
        label: String,
        key: String,
        default: Int,
        min: Int,
        max: Int,
        divisor: Int = 1,
        unit: String = "dp"
    ) {
        val text = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(10), 0, 0)
        }
        val seek = SeekBar(this)

        val current = settings.read(key, default)
        seek.max = max - min
        seek.progress = current - min

        fun show(value: Int) {
            val shown = if (divisor == 1) "$value" else String.format("%.1f", value / divisor.toFloat())
            text.text = "$label: $shown $unit"
        }
        show(current)

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val value = progress + min
                settings.save(key, value)
                show(value)
                refreshPreview()
            }

            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })

        root.addView(text)
        root.addView(seek)
    }

    private fun title(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 16f
        setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(18), 0, dp(4))
    }

    private fun button(value: String, onClick: () -> Unit): Button = Button(this).apply {
        text = value
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()
}

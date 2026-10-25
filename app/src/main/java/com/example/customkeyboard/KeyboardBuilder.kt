package com.example.customkeyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Dựng giao diện bàn phím từ KeySizeSettings.
 * Dùng chung cho bàn phím thật (KeyboardService) và phần xem trước (SettingsActivity).
 */
class KeyboardBuilder(
    private val context: Context,
    private val settings: KeySizeSettings,
    private val listener: Listener
) {

    interface Listener {
        fun onText(text: String)
        fun onDelete()
        fun onEnter()
        fun onShiftToggle()
        fun onSymbolsToggle()
    }

    private class Key(
        val label: String,
        val weight: Float,
        val special: Boolean = false,
        val repeat: Boolean = false,
        val action: () -> Unit
    )

    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    fun build(shifted: Boolean, symbols: Boolean): View {
        val rows = if (symbols) symbolRows() else letterRows(shifted)
        val gap = dp(settings.keyGapDp)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BG)
            setPadding(0, 0, 0, dp(settings.bottomPadDp))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        rows.forEachIndexed { index, keys ->
            val indent = if (index == 1) dp(settings.indentDp) else 0
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(indent, 0, indent, 0)
            }
            keys.forEach { row.addView(makeKey(it, gap)) }
            container.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(settings.rowHeightDp(index))
                )
            )
        }
        return container
    }

    // ---- Bố cục ----

    private fun letterRows(shifted: Boolean): List<List<Key>> {
        val side = settings.sideWeight

        fun letter(c: String): Key {
            val shown = if (shifted) c.uppercase() else c
            return Key(shown, 1f) {
                listener.onText(shown)
                if (shifted) listener.onShiftToggle() // Shift chỉ có tác dụng một lần
            }
        }

        val shiftKey = Key("⇧", side, special = true) { listener.onShiftToggle() }
        val deleteKey = Key("⌫", side, special = true, repeat = true) { listener.onDelete() }

        return listOf(
            "qwertyuiop".map { letter(it.toString()) },
            "asdfghjkl".map { letter(it.toString()) },
            listOf(shiftKey) + "zxcvbnm".map { letter(it.toString()) } + listOf(deleteKey),
            bottomRow("?123")
        )
    }

    private fun symbolRows(): List<List<Key>> {
        val side = settings.sideWeight

        fun sym(c: String): Key = Key(c, 1f) { listener.onText(c) }

        val deleteKey = Key("⌫", side, special = true, repeat = true) { listener.onDelete() }

        return listOf(
            "1234567890".map { sym(it.toString()) },
            "@#\$%&-+()/".map { sym(it.toString()) },
            "*\"':;!?".map { sym(it.toString()) } + listOf(deleteKey),
            bottomRow("ABC")
        )
    }

    /** Hàng dưới cùng: nút chuyển chế độ, dấu phẩy, dấu cách, dấu chấm, Enter. */
    private fun bottomRow(modeLabel: String): List<Key> {
        val side = settings.sideWeight
        val space = settings.spaceWeight
        return listOf(
            Key(modeLabel, side, special = true) { listener.onSymbolsToggle() },
            Key(",", 1f) { listener.onText(",") },
            Key("Space", space) { listener.onText(" ") },
            Key(".", 1f) { listener.onText(".") },
            Key("↵", side, special = true) { listener.onEnter() }
        )
    }

    // ---- Tạo phím và xử lý chạm ----

    private fun makeKey(key: Key, gap: Int): View {
        val tv = TextView(context).apply {
            text = key.label
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (key.special) 16f else 20f)
            background = bg(normalColor(key))
        }
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, key.weight)
        lp.setMargins(gap, gap, gap, gap)
        tv.layoutParams = lp
        tv.setOnTouchListener { v, e -> onKeyTouch(v, e, key) }
        return tv
    }

    private fun onKeyTouch(v: View, e: MotionEvent, key: Key): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.background = bg(COLOR_PRESSED)
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                key.action()
                if (key.repeat) startRepeat(key)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.background = bg(normalColor(key))
                stopRepeat()
            }
        }
        return true
    }

    private fun startRepeat(key: Key) {
        stopRepeat()
        val runnable = object : Runnable {
            override fun run() {
                key.action()
                handler.postDelayed(this, 60L)
            }
        }
        repeatRunnable = runnable
        handler.postDelayed(runnable, 400L)
    }

    private fun stopRepeat() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
    }

    private fun normalColor(key: Key): Int = if (key.special) COLOR_SPECIAL else COLOR_KEY

    private fun bg(color: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(6).toFloat()
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics
    ).toInt()

    companion object {
        private val COLOR_BG = Color.parseColor("#1C1C1E")
        private val COLOR_KEY = Color.parseColor("#4A4A4F")
        private val COLOR_SPECIAL = Color.parseColor("#2C2C2E")
        private val COLOR_PRESSED = Color.parseColor("#6E6E73")
    }
}

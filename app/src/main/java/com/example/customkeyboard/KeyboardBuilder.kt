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
import android.widget.FrameLayout
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
        /** true = đang gõ Telex tiếng Việt, false = gõ thẳng tiếng Anh */
        val vietnameseMode: Boolean
        fun onLetter(c: Char)
        fun onText(text: String)
        fun onDelete()
        fun onEnter()
        fun onShiftToggle()
        fun onSymbolsToggle()
        fun onToggleLanguage()
    }

    private enum class Style { NORMAL, SPACE, ENTER }

    private class Key(
        val label: String,
        val weight: Float,
        val special: Boolean = false,
        val repeat: Boolean = false,
        val hint: String? = null,
        val style: Style = Style.NORMAL,
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

        fun letter(c: Char, hint: String? = null): Key {
            // Nhãn luôn là chữ hoa như ảnh mẫu; ký tự gửi đi mới phụ thuộc Shift
            val out = if (shifted) c.uppercaseChar() else c
            return Key(c.uppercaseChar().toString(), 1f, hint = hint) {
                listener.onLetter(out)
                if (shifted) listener.onShiftToggle() // Shift chỉ có tác dụng một lần
            }
        }

        val shiftKey = Key("⇧", side, special = true) { listener.onShiftToggle() }
        val deleteKey = Key("⌫", side, special = true, repeat = true) { listener.onDelete() }

        // Hàng trên có số nhỏ ở góc phải, giống các bàn phím phổ biến
        val row1 = "qwertyuiop".mapIndexed { i, c -> letter(c, hint = "${(i + 1) % 10}") }

        return listOf(
            row1,
            "asdfghjkl".map { letter(it) },
            listOf(shiftKey) + "zxcvbnm".map { letter(it) } + listOf(deleteKey),
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

    /** Hàng dưới cùng: chuyển chế độ, chuyển ngôn ngữ, dấu phẩy, dấu cách, dấu chấm, Enter. */
    private fun bottomRow(modeLabel: String): List<Key> {
        val side = settings.sideWeight
        val space = settings.spaceWeight
        val lang = if (listener.vietnameseMode) "VI" else "EN"
        // ?123, VI và dấu phẩy ít dùng nên nhỏ lại; Space và Enter to hơn
        return listOf(
            Key(modeLabel, side * 0.8f, special = true) { listener.onSymbolsToggle() },
            Key(lang, side * 0.7f, special = true) { listener.onToggleLanguage() },
            Key(",", 0.6f) { listener.onText(",") },
            Key("Space", space, style = Style.SPACE) { listener.onText(" ") },
            Key(".", 1f) { listener.onText(".") },
            Key("↵", side * 1.4f, special = true, style = Style.ENTER) { listener.onEnter() }
        )
    }

    // ---- Tạo phím và xử lý chạm ----

    private fun makeKey(key: Key, gap: Int): View {
        val cell = FrameLayout(context)

        val label = TextView(context).apply {
            text = key.label
            gravity = Gravity.CENTER
            setTextColor(if (key.style == Style.ENTER) Color.WHITE else COLOR_TEXT)
            val size = when {
                key.style == Style.ENTER -> 30f
                key.special -> 18f
                else -> 22f
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        cell.addView(label)

        key.hint?.let { hint ->
            cell.addView(TextView(context).apply {
                text = hint
                setTextColor(COLOR_HINT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                ).apply {
                    topMargin = dp(2)
                    rightMargin = dp(4)
                }
            })
        }

        cell.background = bg(normalColor(key))
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, key.weight)
        lp.setMargins(gap, gap, gap, gap)
        cell.layoutParams = lp
        cell.setOnTouchListener { v, e -> onKeyTouch(v, e, key) }
        return cell
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

    private fun normalColor(key: Key): Int = when (key.style) {
        Style.ENTER -> COLOR_ENTER
        Style.SPACE -> COLOR_SPACE
        Style.NORMAL -> COLOR_KEY
    }

    private fun bg(color: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(6).toFloat()
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics
    ).toInt()

    companion object {
        // Màu lấy từ ảnh chụp bàn phím mẫu
        private val COLOR_BG = Color.parseColor("#F7F7F9")
        private val COLOR_KEY = Color.parseColor("#F7F7F9")
        private val COLOR_PRESSED = Color.parseColor("#E1E1E3")
        private val COLOR_SPACE = Color.parseColor("#E1E1E3")
        private val COLOR_ENTER = Color.parseColor("#4CB6AC")
        private val COLOR_TEXT = Color.parseColor("#39464E")
        private val COLOR_HINT = Color.parseColor("#5E6B73")
    }
}

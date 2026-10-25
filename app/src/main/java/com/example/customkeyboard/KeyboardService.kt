package com.example.customkeyboard

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class KeyboardService : InputMethodService(), KeyboardBuilder.Listener {

    private lateinit var settings: KeySizeSettings
    private lateinit var builder: KeyboardBuilder
    private lateinit var clipStore: ClipStore
    private lateinit var clipboard: ClipboardManager
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }
    private val telex = TelexEngine()
    private var shifted = false
    private var symbols = false
    private var vietnamese = true
    private var showingClips = false

    override val vietnameseMode: Boolean
        get() = vietnamese

    override fun onCreate() {
        super.onCreate()
        settings = KeySizeSettings(this)
        builder = KeyboardBuilder(this, settings, this)
        clipStore = ClipStore(this)
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        clipboard.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    override fun onCreateInputView(): View = buildRoot()

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Dựng lại mỗi lần mở bàn phím để áp dụng kích thước vừa chỉnh
        telex.clear()
        shifted = false
        symbols = false
        showingClips = false
        rebuild()
    }

    override fun onFinishInput() {
        telex.clear()
        super.onFinishInput()
    }

    private fun rebuild() {
        setInputView(buildRoot())
    }

    /** Thanh trên cùng + khu vực bàn phím hoặc bộ nhớ tạm. */
    private fun buildRoot(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(
            buildToolbar(),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40))
        )
        val holder = FrameLayout(this)
        if (showingClips) {
            holder.addView(
                buildClipPanel(),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, keyboardHeight())
            )
        } else {
            holder.addView(builder.build(shifted, symbols))
        }
        root.addView(
            holder,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return root
    }

    private fun buildToolbar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(COLOR_BAR)
            setPadding(dp(8), 0, dp(8), 0)
        }
        bar.addView(actionButton(if (showingClips) "⌨  Bàn phím" else "📋  Bộ nhớ tạm") {
            finishComposing()
            showingClips = !showingClips
            rebuild()
        })
        return bar
    }

    /** Danh sách ghi chú đã ghim và các đoạn copy gần đây. */
    private fun buildClipPanel(): View {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(8))
        }

        list.addView(
            actionButton("＋  Lưu chữ đang chọn làm ghi chú", full = true) {
                val ic = currentInputConnection
                val text = ic?.getSelectedText(0)?.toString()?.takeIf { it.isNotBlank() }
                    ?: ic?.getTextBeforeCursor(500, 0)?.toString()
                if (!text.isNullOrBlank()) {
                    clipStore.addNote(text)
                    rebuild()
                }
            }
        )

        val items = clipStore.load()
        if (items.isEmpty()) {
            list.addView(hintText("Chưa có gì. Hãy copy một đoạn chữ, nó sẽ hiện ở đây."))
        }

        val pinned = items.filter { it.pinned }
        val recent = items.filter { !it.pinned }
        if (pinned.isNotEmpty()) {
            list.addView(sectionTitle("Ghi chú đã ghim"))
            pinned.forEach { list.addView(itemRow(it)) }
        }
        if (recent.isNotEmpty()) {
            list.addView(sectionTitle("Gần đây"))
            recent.forEach { list.addView(itemRow(it)) }
        }

        return ScrollView(this).apply {
            setBackgroundColor(COLOR_BG)
            addView(list)
        }
    }

    private fun itemRow(item: ClipStore.Item): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = pill(COLOR_CARD)
            setPadding(dp(10), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(4), 0, 0) }
        }

        row.addView(TextView(this).apply {
            text = item.text
            setTextColor(COLOR_TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { pasteFromPanel(item.text) }
        })
        row.addView(actionButton(if (item.pinned) "Bỏ ghim" else "Ghim", small = true) {
            clipStore.togglePin(item.text)
            rebuild()
        })
        row.addView(actionButton("Xóa", small = true) {
            clipStore.remove(item.text)
            rebuild()
        })
        return row
    }

    private fun pasteFromPanel(text: String) {
        finishComposing()
        currentInputConnection?.commitText(text, 1)
        showingClips = false
        rebuild()
    }

    private fun captureClip() {
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        clipStore.add(text)
    }

    private fun keyboardHeight(): Int =
        (0 until KeySizeSettings.ROW_COUNT).sumOf { dp(settings.rowHeightDp(it)) } +
                dp(settings.bottomPadDp)

    private fun actionButton(label: String, small: Boolean = false, full: Boolean = false, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            setTextColor(COLOR_TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (small) 13f else 14f)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = pill(COLOR_SPACE)
            layoutParams = if (full) {
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(0, dp(4), 0, dp(4)) }
            } else {
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(dp(4), 0, 0, 0) }
            }
            setOnClickListener { onClick() }
        }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(COLOR_HINT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(dp(4), dp(10), 0, 0)
    }

    private fun hintText(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(COLOR_HINT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(dp(4), dp(12), dp(4), dp(4))
    }

    private fun pill(color: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(8).toFloat()
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    /** Kết thúc từ đang gõ dở, giữ nguyên chữ đã hiện trong ô nhập. */
    private fun finishComposing() {
        if (!telex.isEmpty()) {
            telex.clear()
            currentInputConnection?.finishComposingText()
        }
    }

    // ---- KeyboardBuilder.Listener ----

    override fun onLetter(c: Char) {
        val ic = currentInputConnection ?: return
        if (!vietnamese) {
            ic.commitText(c.toString(), 1)
            return
        }
        telex.accept(c)
        ic.setComposingText(telex.current(), 1)
    }

    override fun onText(text: String) {
        finishComposing()
        currentInputConnection?.commitText(text, 1)
    }

    override fun onDelete() {
        val ic = currentInputConnection ?: return
        if (telex.isEmpty()) {
            ic.deleteSurroundingText(1, 0)
            return
        }
        telex.backspace()
        ic.setComposingText(telex.current(), 1)
        if (telex.isEmpty()) ic.finishComposingText()
    }

    override fun onEnter() {
        finishComposing()
        val info: EditorInfo? = currentInputEditorInfo
        val flags = info?.imeOptions ?: 0
        val action = flags and EditorInfo.IME_MASK_ACTION
        val useEditorAction = action != EditorInfo.IME_ACTION_NONE &&
                action != EditorInfo.IME_ACTION_UNSPECIFIED &&
                (flags and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0

        if (useEditorAction) {
            currentInputConnection?.performEditorAction(action)
        } else {
            currentInputConnection?.commitText("\n", 1)
        }
    }

    override fun onShiftToggle() {
        shifted = !shifted
        rebuild()
    }

    override fun onSymbolsToggle() {
        finishComposing()
        symbols = !symbols
        shifted = false
        rebuild()
    }

    override fun onToggleLanguage() {
        finishComposing()
        vietnamese = !vietnamese
        rebuild()
    }

    companion object {
        private val COLOR_BG = Color.parseColor("#F7F7F9")
        private val COLOR_BAR = Color.parseColor("#EEF1F4")
        private val COLOR_CARD = Color.parseColor("#FFFFFF")
        private val COLOR_SPACE = Color.parseColor("#E1E1E3")
        private val COLOR_TEXT = Color.parseColor("#39464E")
        private val COLOR_HINT = Color.parseColor("#5E6B73")
    }
}

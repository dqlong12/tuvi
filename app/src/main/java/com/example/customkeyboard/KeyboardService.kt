package com.example.customkeyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo

class KeyboardService : InputMethodService(), KeyboardBuilder.Listener {

    private lateinit var settings: KeySizeSettings
    private lateinit var builder: KeyboardBuilder
    private val telex = TelexEngine()
    private var shifted = false
    private var symbols = false
    private var vietnamese = true

    override val vietnameseMode: Boolean
        get() = vietnamese

    override fun onCreate() {
        super.onCreate()
        settings = KeySizeSettings(this)
        builder = KeyboardBuilder(this, settings, this)
    }

    override fun onCreateInputView(): View = builder.build(shifted, symbols)

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Dựng lại mỗi lần mở bàn phím để áp dụng kích thước vừa chỉnh
        telex.clear()
        shifted = false
        symbols = false
        rebuild()
    }

    override fun onFinishInput() {
        telex.clear()
        super.onFinishInput()
    }

    private fun rebuild() {
        setInputView(builder.build(shifted, symbols))
    }

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
}

package com.example.customkeyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo

class KeyboardService : InputMethodService(), KeyboardBuilder.Listener {

    private lateinit var settings: KeySizeSettings
    private lateinit var builder: KeyboardBuilder
    private var shifted = false
    private var symbols = false

    override fun onCreate() {
        super.onCreate()
        settings = KeySizeSettings(this)
        builder = KeyboardBuilder(this, settings, this)
    }

    override fun onCreateInputView(): View = builder.build(shifted, symbols)

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Dựng lại mỗi lần mở bàn phím để áp dụng kích thước vừa chỉnh
        shifted = false
        symbols = false
        rebuild()
    }

    private fun rebuild() {
        setInputView(builder.build(shifted, symbols))
    }

    // ---- KeyboardBuilder.Listener ----

    override fun onText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    override fun onDelete() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    override fun onEnter() {
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
        symbols = !symbols
        shifted = false
        rebuild()
    }
}

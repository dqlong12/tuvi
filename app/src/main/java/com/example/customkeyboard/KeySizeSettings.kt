package com.example.customkeyboard

import android.content.Context

/**
 * Lưu kích thước bàn phím do người dùng chỉnh.
 * Các giá trị dạng "×10" được lưu dưới dạng số nguyên, ví dụ 15 = 1.5 lần.
 */
class KeySizeSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun rowHeightDp(row: Int): Int =
        prefs.getInt(rowKey(row), DEFAULT_ROW_HEIGHT_DP[row])

    val keyGapDp: Int get() = prefs.getInt(GAP, DEFAULT_GAP)
    val indentDp: Int get() = prefs.getInt(INDENT, DEFAULT_INDENT)
    val sideWeight: Float get() = prefs.getInt(SIDE, DEFAULT_SIDE) / 10f
    val spaceWeight: Float get() = prefs.getInt(SPACE, DEFAULT_SPACE) / 10f
    val bottomPadDp: Int get() = prefs.getInt(BOTTOM, DEFAULT_BOTTOM)

    fun read(key: String, default: Int): Int = prefs.getInt(key, default)

    fun save(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS = "key_sizes"
        const val ROW_COUNT = 4

        const val GAP = "gap_dp"
        const val INDENT = "indent_dp"
        const val SIDE = "side_w10"
        const val SPACE = "space_w10"
        const val BOTTOM = "bottom_dp"

        val DEFAULT_ROW_HEIGHT_DP = intArrayOf(46, 46, 46, 46)
        const val DEFAULT_GAP = 3
        const val DEFAULT_INDENT = 16
        const val DEFAULT_SIDE = 15
        const val DEFAULT_SPACE = 40
        const val DEFAULT_BOTTOM = 0

        fun rowKey(row: Int): String = "row_h_$row"
    }
}

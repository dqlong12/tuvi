package com.example.chargingmonitor

import android.content.Context

/**
 * Tùy chọn hiển thị số trên thanh trạng thái, lưu trong SharedPreferences.
 * Dịch vụ đọc lại mỗi giây nên đổi xong là thấy ngay trên thanh trạng thái.
 */
object IconSettings {

    class Size(val label: String, val squeeze: Float)

    /**
     * Từ lớn nhất đến nhỏ nhất. squeeze là tỉ lệ ép ngang chữ:
     * càng nhỏ thì chữ càng cao so với biểu tượng, nên hiển thị càng to.
     */
    val SIZES = listOf(
        Size("Lớn nhất", 0.40f),
        Size("Lớn", 0.50f),
        Size("Vừa", 0.62f),
        Size("Nhỏ", 0.80f),
        Size("Nhỏ nhất", 1.00f)
    )

    private const val PREFS = "icon_settings"
    private const val KEY_SIZE = "size_index"
    private const val KEY_SHOW_UNIT = "show_unit"

    fun sizeIndex(ctx: Context): Int =
        prefs(ctx).getInt(KEY_SIZE, 0).coerceIn(0, SIZES.lastIndex)

    fun setSizeIndex(ctx: Context, index: Int) {
        prefs(ctx).edit().putInt(KEY_SIZE, index).apply()
    }

    fun squeeze(ctx: Context): Float = SIZES[sizeIndex(ctx)].squeeze

    fun showUnit(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SHOW_UNIT, true)

    fun setShowUnit(ctx: Context, show: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SHOW_UNIT, show).apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

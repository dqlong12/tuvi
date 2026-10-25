package com.example.customkeyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lưu lịch sử các đoạn đã copy và các ghi chú đã ghim.
 * Ghi chú = mục đã ghim, luôn được giữ lại; lịch sử gần đây tối đa MAX_RECENT mục.
 */
class ClipStore(context: Context) {

    data class Item(val text: String, val pinned: Boolean)

    private val prefs = context.getSharedPreferences("clipboard", Context.MODE_PRIVATE)

    fun load(): List<Item> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Item(o.getString("t"), o.optBoolean("p", false))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Thêm đoạn vừa copy lên đầu. Nếu đã có thì đưa lên đầu, giữ nguyên trạng thái ghim. */
    fun add(text: String) = upsert(text, forcePin = false)

    /** Lưu thành ghi chú đã ghim. */
    fun addNote(text: String) = upsert(text, forcePin = true)

    fun togglePin(text: String) {
        save(load().map { if (it.text == text) it.copy(pinned = !it.pinned) else it })
    }

    fun remove(text: String) {
        save(load().filter { it.text != text })
    }

    private fun upsert(raw: String, forcePin: Boolean) {
        val text = raw.trim().take(MAX_LENGTH)
        if (text.isBlank()) return
        val items = load().toMutableList()
        val old = items.indexOfFirst { it.text == text }
        val wasPinned = old >= 0 && items[old].pinned
        if (old >= 0) items.removeAt(old)
        items.add(0, Item(text, forcePin || wasPinned))
        save(trim(items))
    }

    private fun trim(items: List<Item>): List<Item> {
        val out = ArrayList<Item>()
        var recent = 0
        for (item in items) {
            if (item.pinned) {
                out.add(item)
            } else if (recent < MAX_RECENT) {
                out.add(item)
                recent++
            }
        }
        return out
    }

    private fun save(items: List<Item>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("t", it.text).put("p", it.pinned)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "items"
        private const val MAX_RECENT = 50
        private const val MAX_LENGTH = 2000
    }
}

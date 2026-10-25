package com.example.customkeyboard

/**
 * Bộ gõ Telex. Lưu các phím đã gõ trong từ đang soạn và tính lại chuỗi tiếng Việt mỗi lần.
 *
 * Quy tắc:
 *  s = sắc, f = huyền, r = hỏi, x = ngã, j = nặng, z = bỏ dấu thanh
 *  aa, ee, oo = â ê ô;  aw = ă;  ow, uw = ơ ư;  uow = ươ;  dd = đ
 */
class TelexEngine {

    private val keys = StringBuilder()

    fun accept(c: Char) {
        keys.append(c)
    }

    /** Xóa phím cuối. Trả về false nếu không còn gì để xóa. */
    fun backspace(): Boolean {
        if (keys.isEmpty()) return false
        keys.deleteCharAt(keys.length - 1)
        return true
    }

    fun clear() {
        keys.setLength(0)
    }

    fun isEmpty(): Boolean = keys.isEmpty()

    fun current(): String = compose(keys.toString())

    private class Letter(val base: Char, val upper: Boolean) {
        // '(' = ă, '^' = â ê ô, '+' = ơ ư, 'd' = đ
        var mark: Char? = null
    }

    private fun compose(input: String): String {
        val letters = mutableListOf<Letter>()
        var tone = -1
        for (k in input) {
            val lc = k.lowercaseChar()
            val up = k.isUpperCase()
            val toneIdx = TONE_KEYS.indexOf(lc)
            val last = letters.lastOrNull()
            when {
                toneIdx >= 0 && hasVowel(letters) -> {
                    if (tone == toneIdx) {
                        // Gõ lần thứ hai cùng phím thanh: hủy dấu và giữ ký tự
                        tone = -1
                        letters.add(Letter(lc, up))
                    } else {
                        tone = toneIdx
                    }
                }
                lc == 'z' && tone >= 0 -> tone = -1
                lc == 'w' -> applyW(letters, up)
                lc in "aeo" && last != null && last.base == lc && last.mark == null -> last.mark = '^'
                lc == 'd' && last != null && last.base == 'd' && last.mark == null -> last.mark = 'd'
                else -> letters.add(Letter(lc, up))
            }
        }
        return render(letters, tone)
    }

    private fun applyW(letters: MutableList<Letter>, up: Boolean) {
        // "uo" -> "ươ"
        val uo = (letters.size - 2 downTo 0).firstOrNull {
            letters[it].base == 'u' && letters[it].mark == null &&
                letters[it + 1].base == 'o' && letters[it + 1].mark == null
        }
        if (uo != null) {
            letters[uo].mark = '+'
            letters[uo + 1].mark = '+'
            return
        }
        val target = (letters.size - 1 downTo 0).firstOrNull {
            letters[it].mark == null && letters[it].base in "aou"
        }
        if (target != null) {
            letters[target].mark = if (letters[target].base == 'a') '(' else '+'
        } else {
            letters.add(Letter('u', up).also { it.mark = '+' })
        }
    }

    private fun isVowelAt(letters: List<Letter>, i: Int): Boolean {
        val b = letters[i].base
        if (b !in "aeiouy") return false
        // "qu" coi u là phụ âm
        if (b == 'u' && i > 0 && letters[i - 1].base == 'q') return false
        return true
    }

    private fun hasVowel(letters: List<Letter>): Boolean =
        letters.indices.any { isVowelAt(letters, it) }

    /** Chọn nguyên âm sẽ mang dấu thanh, theo quy tắc gần đúng của chính tả tiếng Việt. */
    private fun placeTone(letters: List<Letter>): Int {
        val v = letters.indices.filter { isVowelAt(letters, it) }
        if (v.isEmpty()) return -1
        if (v.size == 1) return v[0]
        val marked = v.filter { letters[it].mark != null }
        if (marked.isNotEmpty()) return marked.last()
        if (v.size >= 3) return v[1]
        val first = letters[v[0]].base
        val second = letters[v[1]].base
        val open = v.last() == letters.size - 1
        return when {
            !open -> v[1]
            first == 'o' && (second == 'a' || second == 'e') -> v[1]
            first == 'u' && second == 'y' -> v[1]
            else -> v[0]
        }
    }

    private fun vowelKey(l: Letter): Char = when (l.base) {
        'a' -> if (l.mark == '(') 'ă' else if (l.mark == '^') 'â' else 'a'
        'e' -> if (l.mark == '^') 'ê' else 'e'
        'o' -> if (l.mark == '^') 'ô' else if (l.mark == '+') 'ơ' else 'o'
        'u' -> if (l.mark == '+') 'ư' else 'u'
        else -> l.base
    }

    private fun render(letters: List<Letter>, tone: Int): String {
        val toneAt = if (tone >= 0) placeTone(letters) else -1
        val sb = StringBuilder()
        letters.forEachIndexed { i, l ->
            val ch = when {
                l.base == 'd' && l.mark == 'd' -> 'đ'
                l.base in "aeiouy" -> {
                    val plain = vowelKey(l)
                    if (i == toneAt) TONED.getValue(plain)[tone] else plain
                }
                else -> l.base
            }
            sb.append(if (l.upper) ch.uppercaseChar() else ch)
        }
        return sb.toString()
    }

    companion object {
        private const val TONE_KEYS = "sfrxj"

        // Mỗi nguyên âm có 5 dạng có dấu, theo thứ tự: sắc, huyền, hỏi, ngã, nặng
        private val TONED = mapOf(
            'a' to "áàảãạ", 'ă' to "ắằẳẵặ", 'â' to "ấầẩẫậ",
            'e' to "éèẻẽẹ", 'ê' to "ếềểễệ",
            'i' to "íìỉĩị",
            'o' to "óòỏõọ", 'ô' to "ốồổỗộ", 'ơ' to "ớờởỡợ",
            'u' to "úùủũụ", 'ư' to "ứừửữự",
            'y' to "ýỳỷỹỵ"
        )
    }
}

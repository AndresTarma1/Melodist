package example.nucleus.lyrics

/**
 * Romanización compacta de letras (estilo Metrolist, sin dependencias externas):
 * japonés (kana→romaji básico), coreano (descomposición Unicode, Romanización
 * Revisada simplificada) y ruso (mapa cirílico).
 */
object Romanizer {

    fun romanize(text: String): String? = when {
        text.isBlank() -> null
        hasKana(text) -> romanizeJapanese(text)
        hasHangul(text) -> romanizeKorean(text)
        hasCyrillic(text) -> romanizeCyrillic(text)
        else -> null
    }

    private fun hasKana(text: String) = text.any { it in '\u3040'..'\u30FF' }
    private fun hasHangul(text: String) = text.any { it in '\uAC00'..'\uD7A3' }
    private fun hasCyrillic(text: String) = text.any { it in '\u0400'..'\u04FF' }

    // ── Japonés: kana básico → romaji ──

    private val KANA_ROMAJI = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'を' to "o", 'ん' to "n",
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
        'ア' to "a", 'イ' to "i", 'ウ' to "u", 'エ' to "e", 'オ' to "o",
        'カ' to "ka", 'キ' to "ki", 'ク' to "ku", 'ケ' to "ke", 'コ' to "ko",
        'サ' to "sa", 'シ' to "shi", 'ス' to "su", 'セ' to "se", 'ソ' to "so",
        'タ' to "ta", 'チ' to "chi", 'ツ' to "tsu", 'テ' to "te", 'ト' to "to",
        'ナ' to "na", 'ニ' to "ni", 'ヌ' to "nu", 'ネ' to "ne", 'ノ' to "no",
        'ハ' to "ha", 'ヒ' to "hi", 'フ' to "fu", 'ヘ' to "he", 'ホ' to "ho",
        'マ' to "ma", 'ミ' to "mi", 'ム' to "mu", 'メ' to "me", 'モ' to "mo",
        'ヤ' to "ya", 'ユ' to "yu", 'ヨ' to "yo",
        'ラ' to "ra", 'リ' to "ri", 'ル' to "ru", 'レ' to "re", 'ロ' to "ro",
        'ワ' to "wa", 'ヲ' to "o", 'ン' to "n",
        'ガ' to "ga", 'ギ' to "gi", 'グ' to "gu", 'ゲ' to "ge", 'ゴ' to "go",
        'ザ' to "za", 'ジ' to "ji", 'ズ' to "zu", 'ゼ' to "ze", 'ゾ' to "zo",
        'ダ' to "da", 'ヂ' to "ji", 'ヅ' to "zu", 'デ' to "de", 'ド' to "do",
        'バ' to "ba", 'ビ' to "bi", 'ブ' to "bu", 'ベ' to "be", 'ボ' to "bo",
        'パ' to "pa", 'ピ' to "pi", 'プ' to "pu", 'ペ' to "pe", 'ポ' to "po",
        'ャ' to "ya", 'ュ' to "yu", 'ョ' to "yo", 'ッ' to "", 'ー' to "",
    )

    private fun romanizeJapanese(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                KANA_ROMAJI.containsKey(c) -> {
                    val romaji = KANA_ROMAJI.getValue(c)
                    if (c == 'っ' || c == 'ッ') {
                        // Geminación: duplica la consonante inicial de la siguiente sílaba.
                        val next = text.getOrNull(i + 1)?.let { KANA_ROMAJI[it] } ?: ""
                        if (next.isNotEmpty()) sb.append(next[0]) else sb.append("t")
                    } else if (romaji.isNotEmpty()) {
                        sb.append(romaji)
                    }
                }
                c.isLetterOrDigit() || c in " .,!?-'\"()[]" -> sb.append(c)
                else -> sb.append(' ')
            }
            i++
        }
        return sb.toString().trim()
    }

    // ── Coreano: descomposición Unicode → Romanización Revisada (simplificada) ──

    private val CHO = listOf("g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h")
    private val JUNG = listOf("a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i")
    private val JONG = listOf("", "k", "k", "k", "n", "n", "n", "t", "l", "k", "m", "p", "p", "t", "t", "t", "t", "ng", "t", "t", "p", "t", "t", "t", "t", "t", "t", "t")

    private fun romanizeKorean(text: String): String {
        val sb = StringBuilder()
        text.forEach { c ->
            if (c in '\uAC00'..'\uD7A3') {
                val code = c.code - 0xAC00
                val cho = code / (21 * 28)
                val jung = (code % (21 * 28)) / 28
                val jong = code % 28
                sb.append(CHO[cho]).append(JUNG[jung]).append(JONG[jong])
            } else {
                sb.append(c)
            }
        }
        return sb.toString().trim()
    }

    // ── Ruso: cirílico → latino ──

    private val CYRILLIC_ROMAJI = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "ye", 'ё' to "yo",
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
        'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
        'А' to "A", 'Б' to "B", 'В' to "V", 'Г' to "G", 'Д' to "D", 'Е' to "Ye", 'Ё' to "Yo",
        'Ж' to "Zh", 'З' to "Z", 'И' to "I", 'Й' to "Y", 'К' to "K", 'Л' to "L", 'М' to "M",
        'Н' to "N", 'О' to "O", 'П' to "P", 'Р' to "R", 'С' to "S", 'Т' to "T", 'У' to "U",
        'Ф' to "F", 'Х' to "Kh", 'Ц' to "Ts", 'Ч' to "Ch", 'Ш' to "Sh", 'Щ' to "Shch",
        'Ъ' to "", 'Ы' to "Y", 'Ь' to "", 'Э' to "E", 'Ю' to "Yu", 'Я' to "Ya",
    )

    private fun romanizeCyrillic(text: String): String {
        val sb = StringBuilder()
        text.forEach { c ->
            sb.append(CYRILLIC_ROMAJI[c] ?: c)
        }
        return sb.toString().trim()
    }
}

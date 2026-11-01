package com.professoreuler.mathreader

/** The model's tokens (the export's `vocab.json`), and LaTeX from a reading, as `recognizer/vocab.py` writes it. */
class Vocab(val tokens: List<String>) {

    /** LaTeX of [ids], up to the first end: a command followed by a letter keeps a space, so `\pi x` stays `\pi x`. */
    fun decode(ids: List<Int>): String {
        val out = StringBuilder()
        for (id in ids) {
            if (id == EOS) break
            if (id == PAD || id == BOS) continue
            val token = tokens[id]
            if (out.isNotEmpty() && COMMAND_AT_END.containsMatchIn(out) && token.first().isAsciiLetter()) out.append(' ')
            out.append(token)
        }
        return out.toString()
    }

    companion object {
        const val PAD = 0
        const val BOS = 1
        const val EOS = 2
        private val COMMAND_AT_END = Regex("""\\[a-zA-Z]+$""")

        private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'

        /** `vocab.json`: a JSON array of strings. */
        fun parse(json: String): Vocab {
            val tokens = mutableListOf<String>()
            var i = json.indexOf('[') + 1
            while (true) {
                while (i < json.length && json[i] != '"' && json[i] != ']') i++
                if (i >= json.length || json[i] == ']') break
                val s = StringBuilder()
                i++
                while (json[i] != '"') {
                    if (json[i] == '\\') {
                        i++
                        when (val c = json[i]) {
                            'n' -> s.append('\n'); 't' -> s.append('\t'); 'r' -> s.append('\r')
                            'b' -> s.append('\b'); 'f' -> s.append('\u000c')
                            'u' -> { s.append(json.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                            else -> s.append(c)
                        }
                    } else {
                        s.append(json[i])
                    }
                    i++
                }
                tokens.add(s.toString())
                i++
            }
            return Vocab(tokens)
        }
    }
}

package com.professoreuler.mathcalc

import java.util.Locale

/**
 * A line typed on the keyboard, read: the formula in LaTeX, and the choice its words asked for (null where none). Its answers
 * are written with exact fractions ([fractions]): a keyboard's `/` is its fraction bar, and sin 30° is ½, as a calculator's
 * learner expects.
 */
data class Typed(val latex: String, val choice: Choice? = null, val fractions: Boolean = true)

/**
 * Reads what a learner types: math written in a line (`x^2 + 3x - 4 = 0`, `sqrt(2)`, `e^(i pi)`, `integrate x^2 from 0 to 1`)
 * into the LaTeX the calculator reads, and the command words in front of it in the app's language, English always too
 * ("solve", "löse", "Ableitung von … nach y"). A fixed vocabulary per language, no AI: [Vocabulary.ALL].
 */
object TextInput {
    /** [text] as typed; [language] the app's language ("de", "en", "pt-BR"); English words are always understood. */
    fun read(text: String, language: String = "en"): Typed {
        val trimmed = westernDigits(text.trim())
        if (trimmed.isEmpty()) return Typed("")
        // A line whose words cannot be made sense of is the math it holds: reading never fails.
        return try {
            readWords(trimmed, language)
        } catch (e: CalcException) {
            Typed(latex(trimmed))
        } catch (e: RuntimeException) {
            Typed(latex(trimmed))
        } catch (e: StackOverflowError) {
            Typed(latex(trimmed))
        }
    }

    /** Arabic-Indic and Persian digits as the digits the calculator reads, and the Arabic decimal separator as a point. */
    internal fun westernDigits(text: String): String {
        if (text.none { it in '\u0660'..'\u0669' || it in '\u06F0'..'\u06F9' || it == '\u066B' }) return text
        return String(CharArray(text.length) { k ->
            val c = text[k]
            when (c) {
                in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                '\u066B' -> '.'
                else -> c
            }
        })
    }

    private fun readWords(trimmed: String, language: String): Typed {
        val vocabularies = listOfNotNull(Vocabulary.of(language), Vocabulary.ENGLISH).distinct()
        derivativeOperator(trimmed)?.let { return it }
        // The words looked for apart from the math, also where a script writes no spaces (求导x^2, x^2を0から1まで積分).
        val spaced = Vocabulary.spaced(trimmed)
        for (v in vocabularies) {
            val (command, rest) = v.command(spaced) ?: continue
            return commandOf(command, rest, vocabularies)
        }
        // Languages that put the verb last: "x^2-1を因数分解", "x^2-1 का गुणनखंड करें".
        for (v in vocabularies) {
            if (!v.commandLast) continue
            val (command, rest) = v.commandAtEnd(spaced) ?: continue
            return commandOf(command, rest, vocabularies)
        }
        return Typed(latex(trimmed))
    }

    /**
     * What a typed line asks for, typeset as a learner would write it: the formula, a derivative as d/dx (…), an
     * antiderivative as ∫ … dx. The Calculator shows it as the line is typed ("Read as"), so a misreading shows at once.
     */
    fun display(typed: Typed): String {
        val choice = typed.choice ?: return typed.latex
        val letter = choice.letter ?: mainLetterOf(typed.latex)
        val name = Format.name(letter, latex = true)
        return when (choice.mode) {
            Mode.DERIVATIVE -> "\\frac{d}{d$name}\\left(${typed.latex}\\right)"
            Mode.INTEGRAL -> "\\int ${typed.latex} \\, d$name"
            else -> typed.latex
        }
    }

    /** "d/dx x^2" and "d/dy (…)": a derivative in the letter after d/d. */
    private fun derivativeOperator(text: String): Typed? {
        val m = DD.find(text) ?: return null
        val letter = m.groupValues[1]
        val body = text.substring(m.range.last + 1).trim()
        if (body.isEmpty()) return null
        return Typed(latex(body), Choice(Mode.DERIVATIVE, letter))
    }

    private fun commandOf(command: Command, rest: String, vocabularies: List<Vocabulary>): Typed {
        var slots = split(rest, vocabularies)
        // Nothing before the command but a formula where its letter goes ("对 x^3 求导"): that is the formula.
        // A sum's or a limit's "for" holds its index or approach, never the formula.
        if (slots[Slot.MAIN].isNullOrBlank() && command != Command.SUM && command != Command.LIMIT) {
            val where = listOf(Slot.RESPECT, Slot.FOR).firstOrNull { slots[it] != null && letterOf(slots[it]) == null }
            if (where != null) slots = slots - where + (Slot.MAIN to slots.getValue(where))
        }
        val main = slots[Slot.MAIN].orEmpty()
        when (command) {
            Command.SOLVE -> {
                val letter = letterOf(slots[Slot.RESPECT] ?: slots[Slot.FOR])
                return Typed(latex(main), Choice(Mode.SOLVE, letter))
            }
            Command.FACTOR -> return Typed(latex(main), Choice(Mode.FACTOR))
            Command.EXPAND -> return Typed(latex(main), Choice(Mode.EXPAND))
            Command.SIMPLIFY -> return Typed(latex(main), Choice(Mode.SIMPLIFY))
            Command.CALCULATE -> return Typed(latex(main), Choice(Mode.CALCULATE))
            Command.PARTIAL_FRACTIONS -> return Typed(latex(main), Choice(Mode.PARTIAL_FRACTIONS))
            Command.PLOT -> return Typed(latex(main), Choice(Mode.GRAPH, letterOf(slots[Slot.FOR] ?: slots[Slot.RESPECT])))
            Command.DERIVATIVE -> {
                val (body, dLetter) = stripDifferential(main)
                val letter = letterOf(slots[Slot.RESPECT] ?: slots[Slot.FOR]) ?: dLetter
                return Typed(latex(body), Choice(Mode.DERIVATIVE, letter))
            }
            Command.INTEGRAL -> {
                // "intégrale de 0 à 3 de x^2", "integral de x^2 entre 0 y 3": the bounds before the formula or around "and".
                boundsOf(rest, vocabularies)?.let { (from, to, written) ->
                    val (body, dLetter) = stripDifferential(written)
                    val bodyLatex = latex(body)
                    val v = dLetter ?: mainLetterOf(bodyLatex)
                    return Typed("\\int_{${latex(from)}}^{${latex(to)}} $bodyLatex \\, d$v")
                }
                val (body, dLetter) = stripDifferential(main)
                val letter = letterOf(slots[Slot.RESPECT] ?: slots[Slot.FOR]) ?: dLetter
                val from = slots[Slot.FROM]
                val to = slots[Slot.TO]
                if (from != null && to != null) {
                    val bodyLatex = latex(body)
                    val v = letter ?: mainLetterOf(bodyLatex)
                    return Typed("\\int_{${latex(from)}}^{${latex(to)}} $bodyLatex \\, d$v")
                }
                return Typed(latex(body), Choice(Mode.INTEGRAL, letter))
            }
            Command.LIMIT -> {
                // "as x -> 0", "for x gegen 0", "x -> 0" in the main part's tail
                var variable: String? = null
                var point: String? = null
                val approach = (slots[Slot.AS] ?: slots[Slot.FOR])?.let { a -> vocabularies.fold(a) { t, v -> v.arrowed(t) } }
                if (approach != null) {
                    val arrow = ARROW.find(approach)
                    if (arrow != null) {
                        variable = approach.substring(0, arrow.range.first).trim()
                        point = approach.substring(arrow.range.last + 1).trim()
                    } else if (letterOf(approach) != null) {
                        variable = approach.trim()
                    } else {
                        // "limite de x^2 en 2": the point alone.
                        point = approach.trim()
                    }
                }
                // "voor x naar 2": a word for "with respect to" that names the point in a limit.
                if (point == null) point = slots[Slot.TO] ?: slots[Slot.RESPECT]
                var body = main
                if (variable == null) {
                    // "lim x->0 sin(x)/x"
                    val lead = LEADING_APPROACH.find(main)
                    if (lead != null) {
                        variable = lead.groupValues[1]
                        point = lead.groupValues[2]
                        body = main.substring(lead.range.last + 1)
                    }
                }
                val bodyLatex = latex(body)
                val v = letterOf(variable) ?: mainLetterOf(bodyLatex)
                val p = point?.let { latex(it) } ?: "0"
                return Typed("\\lim_{$v \\to $p} $bodyLatex")
            }
            Command.SUM -> {
                val bodyLatex = latex(main)
                // The index after "for", or as the lower bound itself: "k=1から", "从 k=1 到 n".
                val index = slots[Slot.FOR] ?: slots[Slot.RESPECT] ?: slots[Slot.FROM]?.takeIf { '=' in it }
                val eq = index?.indexOf('=') ?: -1
                val v = letterOf(if (eq > 0) index!!.substring(0, eq) else index) ?: "k"
                val lower = if (eq > 0) index!!.substring(eq + 1).trim() else slots[Slot.FROM] ?: "1"
                val upper = slots[Slot.TO] ?: "\\infty"
                return Typed("\\sum_{$v=${latex(lower)}}^{${latex(upper)}} $bodyLatex")
            }
        }
    }

    // --------------------------------------------------------------------------------------------------------------------
    // Slots: the parts of a command between its connecting words.

    private enum class Slot { MAIN, FROM, TO, FOR, RESPECT, AS }

    private fun split(rest: String, vocabularies: List<Vocabulary>): Map<Slot, String> {
        val words = rest.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val slots = LinkedHashMap<Slot, MutableList<String>>()
        var current = Slot.MAIN
        var depth = 0
        var k = 0
        val ignored = vocabularies.flatMap { it.ignored }.toSet()
        val closers = vocabularies.flatMap { it.closers }.toSet()
        // "of" right after the command belongs to it: "derivative of x²", "Ableitung von x²".
        if (words.isNotEmpty() && vocabularies.any { v -> Vocabulary.lower(words[0]) in v.of }) k = 1
        while (k < words.size) {
            if (depth == 0) {
                if (Vocabulary.lower(words[k]) in ignored) { k++; continue }
                // "当 x->0 时": the word that closes a part, after which the formula goes on.
                if (Vocabulary.lower(words[k]) in closers) { current = Slot.MAIN; k++; continue }
                // "limite per x->2 di x^2", "suma para k=1 hasta n de k": "of" after the other parts begins the formula.
                if (current != Slot.MAIN && slots[Slot.MAIN].isNullOrEmpty() && vocabularies.any { Vocabulary.lower(words[k]) in it.of }) {
                    current = Slot.MAIN
                    k++
                    continue
                }
                val match = vocabularies.firstNotNullOfOrNull { v -> v.connective(words, k, current)?.let { v to it } }
                if (match != null) {
                    val (v, found) = match
                    if (v.postpositional) {
                        // "0から", "x에 대해", "0 से": the connective follows its value, the word before it.
                        val before = slots[current]?.removeLastOrNull()
                        if (before != null) slots.getOrPut(found.first) { mutableListOf() } += before
                        current = Slot.MAIN
                    } else {
                        current = found.first
                    }
                    k += found.second
                    continue
                }
            }
            val w = words[k]
            depth += w.count { it == '(' || it == '[' || it == '{' } - w.count { it == ')' || it == ']' || it == '}' }
            slots.getOrPut(current) { mutableListOf() } += w
            k++
        }
        // Particles left at the end of a part: "x^2-1 を", "x^2 का", "x^2-1 'i", "3 的".
        val particles = vocabularies.flatMap { it.particles }.toSet()
        for (part in slots.values) while (part.size > 1 && Vocabulary.lower(part.last()) in particles) part.removeAt(part.size - 1)
        return slots.mapValues { it.value.joinToString(" ") }
    }

    /**
     * An integral's bounds written before its formula ("de 0 à 3 de x^2", "od 0 do 3 z x^2", "dari 0 sampai 3 x^2") or after it
     * with "between … and …" ("x^2 entre 0 y 3"): the bounds and the formula, or null.
     */
    private fun boundsOf(rest: String, vocabularies: List<Vocabulary>): Triple<String, String, String>? {
        val words = rest.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        fun low(k: Int) = words.getOrNull(k)?.let { Vocabulary.lower(it) }
        for (v in vocabularies) {
            if (v.postpositional) continue
            // With its "from" ("de 0 à 3 de x^2"), or without it where the command's own phrase took it ("Integral von" 0 bis 3
            // von x^2), and then only with the "of" before the formula, so "x bis 3" alone is never taken for bounds.
            val withFrom = low(0) in v.from.filter { it.size == 1 }.map { it[0] }
            val start = if (withFrom) 1 else 0
            for (to in v.to.sortedByDescending { it.size }) {
                val end = start + 1 + to.size
                if (words.size <= end + 1) continue
                if (!to.indices.all { low(start + 1 + it) == to[it] }) continue
                var body = words.drop(end + 1)
                val ofFirst = body.size > 1 && Vocabulary.lower(body[0]) in v.of
                if (!withFrom && !ofFirst && !NUMBER_LIKE.matches(words[start])) continue
                if (ofFirst) body = body.drop(1)
                if (body.isNotEmpty()) return Triple(words[start], words[end], body.joinToString(" "))
            }
            for ((between, and) in v.between) {
                val n = words.size
                if (n < 5 || low(n - 4) != between || low(n - 2) != and) continue
                var body = words.take(n - 4)
                if (body.size > 1 && Vocabulary.lower(body[0]) in v.of) body = body.drop(1)
                return Triple(words[n - 3], words[n - 1], body.joinToString(" "))
            }
        }
        return null
    }

    /**
     * The connecting words at [k]: their slot and how many words they are. A word with a particle after it counts as the
     * word ("までの" is "まで"). A connective that is a single Latin letter ("a" in "de 0 a 1") joins only after a lower
     * bound, so a letter of the formula is never taken for it.
     */
    private fun Vocabulary.connective(words: List<String>, k: Int, current: Slot): Pair<Slot, Int>? {
        for ((slot, phrases) in listOf(Slot.RESPECT to respect, Slot.FROM to from, Slot.TO to to, Slot.FOR to forWords, Slot.AS to approach)) {
            for (p in phrases.sortedByDescending { it.size }) {
                if (k + p.size > words.size) continue
                val matches = p.indices.all { j ->
                    val w = Vocabulary.lower(words[k + j])
                    w == p[j] || (j == p.size - 1 && postpositional && particles.any { w == p[j] + it })
                }
                if (!matches) continue
                if (p.size == 1 && p[0].length == 1 && p[0][0] in 'a'..'z' && current != Slot.FROM) continue
                return slot to p.size
            }
        }
        return null
    }

    /** "x^2 dx" → ("x^2", "x"): a trailing differential names the letter. */
    private fun stripDifferential(main: String): Pair<String, String?> {
        val m = TRAILING_D.find(main) ?: return main to null
        return main.substring(0, m.range.first).trim() to m.groupValues[1]
    }

    private fun letterOf(s: String?): String? {
        val t = s?.trim()?.trimEnd('.', ',', '?') ?: return null
        return if (LETTER.matches(t)) t.replace(Regex("^([A-Za-z])_?\\{?([0-9]+)\\}?$"), "$1_$2") else null
    }

    /** x if the formula has it, else its only letter, else the first. */
    private fun mainLetterOf(latex: String): String = try {
        val tokens = MathCalc.TOKEN.findAll(latex).map { it.value }.toList()
        val letters = Domain.complex { Parser(tokens).statements() }.flatMap { st -> st.sides.filterNotNull().flatMap { it.symbols() } }.toSortedSet()
        when {
            "x" in letters -> "x"
            letters.isNotEmpty() -> letters.first()
            else -> "x"
        }
    } catch (e: CalcException) {
        "x"
    } catch (e: RuntimeException) {
        "x"
    } catch (e: StackOverflowError) {
        "x"
    }

    // --------------------------------------------------------------------------------------------------------------------
    // Math typed in a line, to LaTeX.

    /** Math typed in a line as the LaTeX the calculator reads: `x^2` → `x^{2}`, `sqrt(2)` → `\sqrt{2}`, `pi` → `\pi`. */
    fun latex(math: String): String {
        val s = symbols(math)
        val out = StringBuilder()
        var i = 0
        fun emit(str: String) {
            // A command must not run into a letter after it: \pi x, not \pix.
            if (out.isNotEmpty() && COMMAND_END.containsMatchIn(out) && (str.firstOrNull()?.isLetter() == true)) out.append(' ')
            out.append(str)
        }
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> {
                    out.append(' ')
                    i++
                }
                // LaTeX in the line (a reading from the pen pad, or typed by one who knows it) is kept as it is: \frac, \sin.
                c == '\\' -> {
                    var j = i + 1
                    while (j < s.length && (s[j] in 'a'..'z' || s[j] in 'A'..'Z')) j++
                    if (j == i + 1) j = minOf(i + 2, s.length)
                    emit(s.substring(i, j))
                    i = j
                }
                c in 'a'..'z' || c in 'A'..'Z' -> {
                    var j = i
                    while (j < s.length && (s[j] in 'a'..'z' || s[j] in 'A'..'Z')) j++
                    val name = s.substring(i, j)
                    i = wordAt(name, s, j, ::emit)
                }
                c == '^' || (c == '*' && s.getOrNull(i + 1) == '*') -> {
                    i += if (c == '^') 1 else 2
                    val (exponent, next) = exponentAt(s, i)
                    emit("^{${latex(exponent)}}")
                    i = next
                }
                c == '<' && s.getOrNull(i + 1) == '>' -> { emit("\\ne "); i += 2 }
                c == '!' && s.getOrNull(i + 1) == '=' -> { emit("\\ne "); i += 2 }
                c == '-' && s.getOrNull(i + 1) == '>' -> { emit("\\to "); i += 2 }
                c == '°' -> { emit("^{\\circ}"); i++ }
                // A keyboard's * is a times sign: typeset as a dot, read as times.
                c == '*' -> { emit("\\cdot "); i++ }
                c == '%' -> { emit("\\%"); i++ }
                else -> {
                    emit(c.toString())
                    i++
                }
            }
        }
        return out.toString().replace(Regex(" {2,}"), " ").trim()
    }

    /** A word in typed math: a function, a constant, a Greek letter's name, or letters. Returns where reading goes on. */
    private fun wordAt(word: String, s: String, start: Int, emit: (String) -> Unit): Int {
        var i = start
        val lower = word.lowercase(Locale.ROOT)
        val nextNonSpace = s.substring(i).trimStart().firstOrNull()
        val bracketFollows = nextNonSpace == '('
        when {
            lower == "log" && i < s.length && s[i].isDigit() -> {
                // log10(x), log2(x)
                var j = i
                while (j < s.length && s[j].isDigit()) j++
                emit("\\log_{${s.substring(i, j)}}")
                return j
            }
            lower in WRAPPERS -> {
                val (arg, next) = argumentAt(s, i)
                if (arg == null) {
                    emit(word)
                    return i
                }
                val a = latex(arg)
                emit(
                    when (lower) {
                        "sqrt" -> "\\sqrt{$a}"
                        "cbrt" -> "\\sqrt[3]{$a}"
                        "abs" -> "\\left|$a\\right|"
                        "floor" -> "\\lfloor $a\\rfloor "
                        else -> "\\lceil $a\\rceil "
                    },
                )
                return next
            }
            // gamma(5) is Γ(5); Gamma alone the capital letter, gamma alone the small one
            lower == "gamma" && bracketFollows -> emit("\\Gamma")
            word == "Gamma" -> emit("\\Gamma ")
            lower in FUNCTION_WORDS -> emit("\\${FUNCTION_WORDS.getValue(lower)}")
            lower == "pi" -> emit("\\pi ")
            lower == "inf" || lower == "infinity" || lower == "oo" -> emit("\\infty ")
            word in GREEK_WORDS -> emit("\\$word ")
            lower in GREEK_WORDS && word[0].isUpperCase() && "\\${word}" in CAPITAL_GREEK -> emit("\\$word ")
            word.length == 1 && i < s.length && s[i].isDigit() -> {
                // x1 → x_1
                var j = i
                while (j < s.length && s[j].isDigit()) j++
                emit("${word}_{${s.substring(i, j)}}")
                return j
            }
            else -> emit(word)
        }
        return i
    }

    /** What a power raises to: a bracket's inside, a braced group, or a signed number or word (with its own power). */
    private fun exponentAt(s: String, start: Int): Pair<String, Int> {
        var i = start
        while (i < s.length && s[i] == ' ') i++
        if (i >= s.length) return "" to i
        if (s[i] == '(' || s[i] == '{') {
            val close = matching(s, i) ?: return s.substring(i + 1) to s.length
            var text = s.substring(i + 1, close)
            var next = close + 1
            if (next < s.length && s[next] == '^') {
                val (more, after) = exponentAt(s, next + 1)
                text = "($text)^($more)"
                next = after
            }
            return text to next
        }
        val begin = i
        if (s[i] == '-' || s[i] == '+') i++
        when {
            i < s.length && (s[i].isDigit() || s[i] == '.') -> while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            i < s.length && s[i].isLetter() -> while (i < s.length && s[i].isLetter()) i++
            i < s.length -> i++
        }
        var text = s.substring(begin, i)
        if (i < s.length && s[i] == '^') {
            val (more, after) = exponentAt(s, i + 1)
            text = "$text^($more)"
            i = after
        }
        return text to i
    }

    /** A function's argument: a bracket's inside, or else the number or word after it. */
    private fun argumentAt(s: String, start: Int): Pair<String?, Int> {
        var i = start
        while (i < s.length && s[i] == ' ') i++
        if (i >= s.length) return null to start
        if (s[i] == '(') {
            val close = matching(s, i) ?: return s.substring(i + 1) to s.length
            return s.substring(i + 1, close) to close + 1
        }
        val begin = i
        while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '.')) i++
        if (i == begin) return null to start
        return s.substring(begin, i) to i
    }

    private fun matching(s: String, open: Int): Int? {
        var depth = 0
        for (k in open until s.length) {
            if (s[k] == '(' || s[k] == '{' || s[k] == '[') depth++
            if (s[k] == ')' || s[k] == '}' || s[k] == ']') {
                depth--
                if (depth == 0) return k
            }
        }
        return null
    }

    /** Symbols typed with a phone's keyboard, as the words above: × is *, √ is sqrt, π is pi, ² is ^2. */
    private fun symbols(math: String): String {
        val b = StringBuilder()
        for (ch in math) {
            when (ch) {
                '×', '·', '⋅', '∙' -> b.append('*')
                '÷', '∕' -> b.append('/')
                '−', '–', '—' -> b.append('-')
                '√' -> b.append("sqrt")
                '∛' -> b.append("cbrt")
                'π' -> b.append(" pi ")
                '∞' -> b.append(" inf ")
                '≤' -> b.append("<=")
                '≥' -> b.append(">=")
                '≠' -> b.append("!=")
                '→' -> b.append("->")
                '⁰' -> b.append("^0")
                '¹' -> b.append("^1")
                '²' -> b.append("^2")
                '³' -> b.append("^3")
                '⁴' -> b.append("^4")
                '⁵' -> b.append("^5")
                '⁶' -> b.append("^6")
                '⁷' -> b.append("^7")
                '⁸' -> b.append("^8")
                '⁹' -> b.append("^9")
                else -> GREEK_LETTERS[ch]?.let { b.append(' ').append(it).append(' ') } ?: b.append(ch)
            }
        }
        return b.toString()
    }

    private val DD = Regex("^d\\s*/\\s*d([A-Za-z])\\b\\s*")
    private val ARROW = Regex("->|→|\\\\to")
    private val LEADING_APPROACH = Regex("^\\s*([A-Za-z])\\s*(?:->|→)\\s*(\\S+)\\s+")
    /** A bound written as a number: 0, -1, 2.5, π, pi, ∞. */
    private val NUMBER_LIKE = Regex("^[-+−]?([0-9]+([.,][0-9]+)?|pi|π|∞|inf|infinity)$", RegexOption.IGNORE_CASE)
    private val TRAILING_D = Regex("\\s*\\bd([A-Za-z])\\s*$")
    private val LETTER = Regex("^[A-Za-z](_?\\{?[0-9]+\\}?)?$")
    private val COMMAND_END = Regex("\\\\[a-zA-Z]+$")

    /** Words wrapped around their argument. */
    private val WRAPPERS = setOf("sqrt", "cbrt", "abs", "floor", "ceil")

    /** Function words as the parser's commands: asin is arcsin, productlog is Lambert's W. */
    private val FUNCTION_WORDS = mapOf(
        "sin" to "sin", "cos" to "cos", "tan" to "tan", "cot" to "cot", "sec" to "sec", "csc" to "csc",
        "sinh" to "sinh", "cosh" to "cosh", "tanh" to "tanh",
        "arcsin" to "arcsin", "arccos" to "arccos", "arctan" to "arctan", "arccot" to "arccot",
        "asin" to "arcsin", "acos" to "arccos", "atan" to "arctan", "acot" to "arccot",
        "ln" to "ln", "log" to "log", "lg" to "lg", "exp" to "exp",
        "erf" to "erf", "erfc" to "erfc", "zeta" to "zeta", "digamma" to "digamma",
        "lambertw" to "lambertw", "productlog" to "lambertw", "besselj" to "besselj",
        "sgn" to "sgn", "sign" to "sgn",
        // Russian and Ukrainian school notation.
        "tg" to "tan", "ctg" to "cot", "arctg" to "arctan", "arcctg" to "arccot",
    )

    private val GREEK_WORDS = setOf(
        "alpha", "beta", "gamma", "delta", "epsilon", "varepsilon", "zeta", "eta", "theta", "vartheta", "iota", "kappa",
        "lambda", "mu", "nu", "xi", "rho", "sigma", "tau", "upsilon", "phi", "varphi", "chi", "psi", "omega",
    )
    private val CAPITAL_GREEK = setOf("\\Delta", "\\Omega", "\\Phi", "\\Psi", "\\Theta", "\\Lambda", "\\Sigma", "\\Gamma")

    private val GREEK_LETTERS = mapOf(
        'α' to "alpha", 'β' to "beta", 'γ' to "gamma", 'δ' to "delta", 'ε' to "epsilon", 'ζ' to "zeta", 'η' to "eta",
        'θ' to "theta", 'ι' to "iota", 'κ' to "kappa", 'λ' to "lambda", 'μ' to "mu", 'ν' to "nu", 'ξ' to "xi", 'ρ' to "rho",
        'σ' to "sigma", 'τ' to "tau", 'υ' to "upsilon", 'φ' to "phi", 'χ' to "chi", 'ψ' to "psi", 'ω' to "omega",
        'Δ' to "Delta", 'Ω' to "Omega", 'Φ' to "Phi", 'Ψ' to "Psi", 'Θ' to "Theta", 'Λ' to "Lambda", 'Σ' to "Sigma",
        'Γ' to "Gamma",
    )
}

/** What the words in front of a formula ask for. */
enum class Command { SOLVE, FACTOR, EXPAND, SIMPLIFY, DERIVATIVE, INTEGRAL, LIMIT, SUM, PLOT, CALCULATE, PARTIAL_FRACTIONS }

/**
 * The command words of one language and the words that join a command's parts ("from … to", "for", "with respect to"),
 * all in lower case. A phrase is matched whole at the start of the line, the longest first; in a language that puts the verb
 * last ([commandLast]) at its end too. In a language whose joining words follow their value ([postpositional]: "0から1まで",
 * "0부터 1까지", "0 से 1 तक"), each takes the word before it. [particles] are left out where they end a part ("x^2-1を",
 * "x^2 का"), [ignored] words wherever they stand ("kadar", Dutch "op" of "los … op"), and [endings] (polite or verbal endings,
 * "してください", "करें") are taken off the end of a line before its last words are matched.
 */
class Vocabulary internal constructor(
    val language: String,
    private val commands: Map<Command, List<String>>,
    internal val of: Set<String>,
    internal val from: List<List<String>>,
    internal val to: List<List<String>>,
    internal val forWords: List<List<String>>,
    internal val respect: List<List<String>>,
    internal val approach: List<List<String>>,
    internal val postpositional: Boolean = false,
    internal val commandLast: Boolean = false,
    internal val particles: Set<String> = emptySet(),
    internal val ignored: Set<String> = emptySet(),
    private val endings: List<String> = emptyList(),
    /** Words that close the part before them ("当 x->0 时"). */
    internal val closers: Set<String> = emptySet(),
    /** Words a line may begin with before its command, left out ("จง" of a Thai exercise). */
    private val prefixes: List<String> = emptyList(),
    /** Articles between a command and its noun or formula ("la", "l'", "the"); an elided one ends in an apostrophe. */
    private val articles: Set<String> = emptySet(),
    /** Nouns that name no command ("la ecuación x^2=4", "x^2=4 denklemini"), left out after or before one. */
    private val nouns: List<List<String>> = emptyList(),
    /** Words for "tends to" in a limit's approach ("x tiende a 2" is x -> 2). */
    internal val arrows: List<String> = emptyList(),
    /** "between … and …" around an integral's bounds after its formula. */
    internal val between: List<Pair<String, String>> = emptyList(),
) {
    private val phrases: List<Pair<String, Command>> =
        commands.flatMap { (c, list) -> list.map { lower(it) to c } }.sortedByDescending { it.first.length }

    /** The command words of [command], as this language writes them (the first is its usual one). */
    fun words(command: Command): List<String> = commands[command].orEmpty()

    /** The command a line starts with, and the rest of the line. */
    internal fun command(line: String): Pair<Command, String>? {
        wrapped(withoutPrefix(line))?.let { return it }
        val (command, rest) = startCommand(withoutPrefix(line)) ?: return null
        val (c, after) = refined(command, rest)
        // "画出 x^2 的图像": a language that puts its verb last may name the command at both ends.
        if (commandLast) endCommand(after)?.let { (inner, before) -> if (inner == c) return c to before }
        return c to after
    }

    /**
     * A command phrase written around its formula, as textbooks put it: "rozłóż 1/(x^2-1) na ułamki proste", "scomponi … in
     * fratti semplici", "פרק את … לשברים חלקיים". The longest phrase whose first words begin the line and whose last words end
     * it; a last part that is only "of" does not count ("derivative … of").
     */
    private fun wrapped(text: String): Pair<Command, String>? {
        val lower = lower(text)
        val end = lower.trimEnd().trimEnd('.', '。', '!', '！', '?', '？').trimEnd().length
        val line = lower.substring(0, end)
        for ((phrase, command) in phrases) {
            val words = phrase.split(' ').filter { it.isNotEmpty() }
            if (words.size < 2) continue
            for (cut in 1 until words.size) {
                val head = words.take(cut).joinToString(" ")
                val tail = words.drop(cut).joinToString(" ")
                if (tail in of) continue
                if (!line.startsWith("$head ") || !line.endsWith(" $tail")) continue
                if (head.length + tail.length + 2 > line.length) continue
                val middle = text.substring(head.length, end - tail.length).trim()
                if (middle.isEmpty()) continue
                val afterArticle = dropArticle(middle)
                return command to dropTrailingNoun(dropNoun(afterArticle) ?: afterArticle)
            }
        }
        return null
    }

    private fun startCommand(text: String): Pair<Command, String>? {
        val lower = lower(text)
        for ((phrase, command) in phrases) {
            if (!lower.startsWith(phrase)) continue
            val after = lower.getOrNull(phrase.length)
            // A script without spaces runs its words together ("หาอนุพันธ์ของ"): the longest phrase is matched first.
            if (after != null && !unspaced(phrase.last()) && joins(phrase.last(), after)) continue
            return command to text.substring(phrase.length).trim().trimStart(':', '：').trim()
        }
        return null
    }

    /**
     * What follows a command, said more exactly: "calcula la derivada de x^3" is the derivative (a calculating verb, an article
     * and a command's noun), "dibuja la gráfica de x^2" a graph, and a noun that names no command goes ("resuelve la ecuación
     * x^2=4", "pochodna funkcji x^3"). An article alone is kept: it may be a letter ("calcule a x + 1").
     */
    private fun refined(command: Command, rest: String): Pair<Command, String> {
        val afterArticle = dropArticle(rest)
        val inner = startCommand(afterArticle)
        if (inner != null && (command == Command.CALCULATE || inner.first == command)) return refined(inner.first, inner.second)
        val afterNoun = dropNoun(afterArticle)
        if (afterNoun != null) return command to dropOf(afterNoun)
        return command to rest
    }

    private fun dropArticle(text: String): String {
        val lower = lower(text)
        for (a in articles.sortedByDescending { it.length }) {
            if (!lower.startsWith(a)) continue
            if (a.endsWith("'") && text.length > a.length) return text.substring(a.length).trimStart()
            if (lower.getOrNull(a.length) == ' ') return text.substring(a.length).trimStart()
        }
        return text
    }

    private fun dropNoun(text: String): String? {
        val words = text.split(' ').filter { it.isNotEmpty() }
        for (noun in nouns.sortedByDescending { it.size }) {
            if (words.size > noun.size && noun.indices.all { lower(words[it]) == noun[it] }) return words.drop(noun.size).joinToString(" ")
        }
        return null
    }

    private fun dropOf(text: String): String {
        val words = text.split(' ').filter { it.isNotEmpty() }
        return if (words.size > 1 && lower(words[0]) in of) words.drop(1).joinToString(" ") else text
    }

    /** A noun that names no command at the end of what is left before a final command ("x^2=4 denklemini çöz"). */
    private fun dropTrailingNoun(text: String): String {
        val words = text.split(' ').filter { it.isNotEmpty() }
        for (noun in nouns.sortedByDescending { it.size }) {
            val n = noun.size
            if (words.size > n && noun.indices.all { lower(words[words.size - n + it]) == noun[it] }) return words.dropLast(n).joinToString(" ")
        }
        return text
    }

    private fun withoutPrefix(text: String): String {
        for (p in prefixes) {
            if (lower(text).startsWith(lower(p)) && text.length > p.length) return text.substring(p.length).trimStart()
        }
        return text
    }

    /** The command a line ends with ("x^2-1を因数分解", "x^2-1 का गुणनखंड करें"), and the rest of the line. */
    internal fun commandAtEnd(text: String): Pair<Command, String>? {
        val (command, rest) = endCommand(text) ?: return null
        val before = dropTrailingNoun(rest)
        // "x^3'ün türevini hesapla", "x^2 fonksiyonunun grafiğini çiz": a command's noun before a calculating verb says it.
        val inner = endCommand(before)
        if (inner != null && (command == Command.CALCULATE || inner.first == command)) return inner.first to dropTrailingNoun(inner.second)
        return command to before
    }

    private fun endCommand(text: String): Pair<Command, String>? {
        val line = withoutPrefix(text).trimEnd().trimEnd('.', '。', '!', '！', '?', '？').trimEnd()
        // The line as it is first ("인수분해" ends in "해" itself), then without a verbal ending ("因数分解して").
        val candidates = listOf(line) + endings.sortedByDescending { it.length }.mapNotNull { ending ->
            val e = lower(ending)
            if (lower(line).endsWith(e) && line.length > e.length) line.substring(0, line.length - e.length).trimEnd() else null
        }
        for (candidate in candidates) {
            val lower = lower(candidate)
            for ((phrase, command) in phrases) {
                if (!lower.endsWith(phrase) || lower.length == phrase.length) continue
                val at = lower.length - phrase.length
                val before = lower[at - 1]
                // A script without spaces has no word boundary to see: a particle may stand right before the command.
                if (!unspaced(phrase.first()) && joins(phrase.first(), before)) continue
                val rest = candidate.substring(0, at).trim()
                if (rest.isEmpty()) continue
                return command to rest
            }
        }
        return null
    }

    /** [approach] with this language's "tends to" written as an arrow: "x tiende a 2" is "x -> 2". */
    internal fun arrowed(approach: String): String {
        var out = " $approach "
        for (a in arrows.sortedByDescending { it.length }) out = out.replace(Regex("(?i)(?<=\\s)" + Regex.escape(a) + "(?=\\s)"), "->")
        return out.trim()
    }

    companion object {
        private fun words(vararg phrases: String) = phrases.map { it.split(' ') }

        /** Lower case, one character for one, so positions stay as they were (Turkish İ is i). */
        internal fun lower(s: String): String = String(CharArray(s.length) { Character.toLowerCase(s[it]) })

        /** Two letters of one script, which a word boundary never separates ("solvex" is no command; "解x" is). */
        private fun joins(a: Char, b: Char): Boolean = Character.isLetter(b) && scriptOf(a) == scriptOf(b)

        private fun scriptOf(c: Char): Any = when (val s = Character.UnicodeScript.of(c.code)) {
            Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> "CJK"
            else -> s
        }

        /** Scripts written without spaces between words, as far as the math is concerned: Chinese, Japanese, Korean particles, Thai. */
        private fun unspaced(c: Char): Boolean = when (Character.UnicodeScript.of(c.code)) {
            Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.HANGUL, Character.UnicodeScript.THAI -> true
            else -> false
        }

        private fun rightToLeft(c: Char): Boolean = when (Character.UnicodeScript.of(c.code)) {
            Character.UnicodeScript.HEBREW, Character.UnicodeScript.ARABIC -> true
            else -> false
        }

        /**
         * [text] with spaces where words meet math without one: between a script written without spaces and anything else
         * ("求导x^2", "x^2を0から1まで", "x^2-1을"), between a Hebrew or Arabic word and the math it is joined to ("מ-0", "לx",
         * "لـx"), and before a Turkish suffix ("0'dan", "x^2-1'i"). Apostrophes are made one (’ ʼ as ').
         */
        internal fun spaced(text: String): String {
            // Arabic short vowels and shadda are not letters of the word ("حلّل" is "حلل").
            val s = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
                .replace('’', '\'').replace('ʼ', '\'').replace('‘', '\'')
                .filterNot { it in '\u064B'..'\u0652' || it == '\u0670' }
            val out = StringBuilder(s.length + 8)
            for (i in s.indices) {
                val c = s[i]
                val prev = if (i > 0) s[i - 1] else null
                if (prev != null && !prev.isWhitespace() && !c.isWhitespace()) {
                    val boundary = (unspaced(prev) != unspaced(c) && (unspaced(prev) || unspaced(c)) && !isCombining(c)) ||
                        (rightToLeft(prev) && (c.isDigit() || c in 'a'..'z' || c in 'A'..'Z')) ||
                        ((prev.isDigit() || prev in 'a'..'z' || prev in 'A'..'Z') && rightToLeft(c))
                    if (boundary) out.append(' ')
                }
                // A joiner after a Hebrew or Arabic prefix ("מ-0", "לـx") is a space; a minus anywhere else stays.
                if ((c == '-' || c == 'ـ') && prev != null && rightToLeft(prev) && s.getOrNull(i + 1)?.let { !it.isWhitespace() && !rightToLeft(it) } == true &&
                    (i < 2 || !rightToLeft(s[i - 2]))) {
                    out.append(' ')
                    continue
                }
                // A tatweel elsewhere only stretches a word.
                if (c == 'ـ') continue
                // A Turkish suffix after math: "0'dan" is "0 'dan".
                if (c == '\'' && prev != null && (prev.isDigit() || prev in 'a'..'z' || prev in 'A'..'Z' || prev == ')' || prev == ']' || prev == '}') &&
                    isTurkishSuffix(s, i + 1)) {
                    out.append(' ')
                }
                out.append(c)
            }
            return out.toString().replace(Regex(" {2,}"), " ")
        }

        private fun isCombining(c: Char) = Character.getType(c).let {
            it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt() || it == Character.ENCLOSING_MARK.toInt()
        }

        /** The suffixes a Turkish learner joins to math with an apostrophe: 0'dan, 1'e, x^2-1'i, x'e göre. */
        private fun isTurkishSuffix(s: String, at: Int): Boolean {
            val word = s.substring(at).takeWhile { it.isLetter() }.lowercase()
            return word in TURKISH_SUFFIXES
        }

        private val TURKISH_SUFFIXES = setOf(
            "dan", "den", "tan", "ten", "a", "e", "ya", "ye", "i", "ı", "u", "ü", "yi", "yı", "yu", "yü",
            "in", "ın", "un", "ün", "nin", "nın", "nun", "nün", "da", "de", "ta", "te",
        )

        val ENGLISH = Vocabulary(
            "en",
            mapOf(
                Command.SOLVE to listOf("solve"),
                Command.FACTOR to listOf("factor", "factorise", "factorize"),
                Command.EXPAND to listOf("expand", "multiply out"),
                Command.SIMPLIFY to listOf("simplify"),
                Command.DERIVATIVE to listOf("derivative of", "derivative", "differentiate", "derive"),
                Command.INTEGRAL to listOf("integral of", "integral", "integrate", "antiderivative of", "antiderivative"),
                Command.LIMIT to listOf("limit of", "limit", "lim"),
                Command.SUM to listOf("sum of", "sum"),
                Command.PLOT to listOf("plot", "graph of", "graph", "draw"),
                Command.CALCULATE to listOf("calculate", "compute", "evaluate", "find", "determine", "what is"),
                Command.PARTIAL_FRACTIONS to listOf("partial fractions of", "partial fractions", "partial fraction decomposition of", "partial fraction decomposition", "apart"),
            ),
            of = setOf("of"),
            from = words("from"),
            to = words("to"),
            forWords = words("for"),
            respect = words("with respect to", "wrt"),
            approach = words("as", "when"),
            articles = setOf("the"),
            nouns = words("equation", "function", "expression"),
            arrows = listOf("approaches", "tends to", "goes to"),
            between = listOf("between" to "and"),
        )

        val GERMAN = Vocabulary(
            "de",
            mapOf(
                Command.SOLVE to listOf("löse", "lösen", "löse die gleichung", "auflösen"),
                Command.FACTOR to listOf("faktorisiere", "faktorisieren", "zerlege"),
                Command.EXPAND to listOf("multipliziere aus", "ausmultiplizieren", "expandiere"),
                Command.SIMPLIFY to listOf("vereinfache", "vereinfachen"),
                Command.DERIVATIVE to listOf("ableitung von", "ableitung", "leite ab", "ableiten"),
                Command.INTEGRAL to listOf("integral von", "integral", "integriere", "stammfunktion von", "stammfunktion"),
                Command.LIMIT to listOf("grenzwert von", "grenzwert", "limes"),
                Command.SUM to listOf("summe von", "summe"),
                Command.PLOT to listOf("zeichne", "plotte", "graphen von", "graph von", "graphen", "graph"),
                Command.CALCULATE to listOf("berechne", "rechne", "bestimme", "ermittle", "was ist"),
                Command.PARTIAL_FRACTIONS to listOf("partialbruchzerlegung von", "partialbruchzerlegung", "partialbrüche von", "partialbrüche"),
            ),
            of = setOf("von"),
            from = words("von"),
            to = words("bis", "gegen"),
            forWords = words("für"),
            respect = words("nach"),
            approach = words("wenn"),
            articles = setOf("die", "der", "das", "den", "dem"),
            nouns = words("gleichung", "funktion", "term"),
            between = listOf("zwischen" to "und"),
        )

        val SPANISH = Vocabulary(
            "es",
            mapOf(
                Command.SOLVE to listOf("resuelve", "resolver", "resuelva"),
                Command.FACTOR to listOf("factoriza", "factorizar", "factorice"),
                Command.EXPAND to listOf("desarrolla", "desarrollar", "expande", "expandir"),
                Command.SIMPLIFY to listOf("simplifica", "simplificar", "simplifique"),
                Command.DERIVATIVE to listOf("derivada de", "derivada", "deriva", "derivar"),
                Command.INTEGRAL to listOf("integral de", "integral", "integra", "integrar", "primitiva de", "primitiva", "antiderivada de"),
                Command.LIMIT to listOf("límite de", "límite", "limite de", "limite"),
                Command.SUM to listOf("suma de", "suma", "sumatoria de", "sumatoria"),
                Command.PLOT to listOf("grafica", "graficar", "gráfica de", "gráfica", "dibuja", "dibujar", "representa gráficamente", "representa"),
                Command.CALCULATE to listOf("calcula", "calcular", "evalúa", "evaluar", "halla", "hallar", "encuentra", "determina", "cuánto es", "cuanto es"),
                Command.PARTIAL_FRACTIONS to listOf("fracciones parciales de", "fracciones parciales", "descompón en fracciones parciales", "descomposición en fracciones simples de", "descomposición en fracciones simples", "descomposición en fracciones parciales de", "descomposición en fracciones parciales", "descompón en fracciones simples", "descomponer en fracciones simples", "descomponer en fracciones parciales", "fracciones simples de", "fracciones simples"),
            ),
            of = setOf("de"),
            from = words("de", "desde"),
            to = words("a", "hasta"),
            forWords = words("para"),
            respect = words("respecto a", "respecto de", "con respecto a"),
            approach = words("cuando"),
            articles = setOf("el", "la", "los", "las", "lo"),
            nouns = words("ecuación", "función", "expresión", "polinomio"),
            arrows = listOf("tiende a", "tiende hacia"),
            between = listOf("entre" to "y"),
        )

        val FRENCH = Vocabulary(
            "fr",
            mapOf(
                Command.SOLVE to listOf("résous", "résoudre", "résolvez", "resous", "resoudre"),
                Command.FACTOR to listOf("factorise", "factoriser", "factorisez"),
                Command.EXPAND to listOf("développe", "développer", "développez", "developpe", "developper"),
                Command.SIMPLIFY to listOf("simplifie", "simplifier", "simplifiez"),
                Command.DERIVATIVE to listOf("dérivée de", "dérivée", "dérive", "dériver", "derivee de", "derivee", "deriver"),
                Command.INTEGRAL to listOf("intégrale de", "intégrale", "intègre", "intégrer", "primitive de", "primitive", "integrale de", "integrale", "integrer"),
                Command.LIMIT to listOf("limite de", "limite"),
                Command.SUM to listOf("somme de", "somme"),
                Command.PLOT to listOf("trace", "tracer", "tracez", "dessine", "graphe de", "courbe de"),
                Command.CALCULATE to listOf("calcule", "calculer", "calculez", "évalue", "détermine", "déterminer", "trouve", "combien fait"),
                Command.PARTIAL_FRACTIONS to listOf("décompose en éléments simples", "décomposition en éléments simples de", "décomposition en éléments simples", "éléments simples de", "éléments simples", "décomposer en éléments simples", "décomposez en éléments simples"),
            ),
            of = setOf("de", "du"),
            from = words("de"),
            to = words("à", "a", "jusqu'à"),
            forWords = words("pour"),
            respect = words("par rapport à", "par rapport a", "selon"),
            // "limite de x^2 en 2": the lycée names the point after "en".
            approach = words("quand", "lorsque", "en"),
            articles = setOf("le", "la", "les", "l'"),
            nouns = words("équation", "fonction", "expression", "polynôme"),
            arrows = listOf("tend vers"),
            between = listOf("entre" to "et"),
        )

        val INDONESIAN = Vocabulary(
            "id",
            mapOf(
                Command.SOLVE to listOf("selesaikan", "pecahkan", "tentukan penyelesaian"),
                Command.FACTOR to listOf("faktorkan", "faktorisasi"),
                Command.EXPAND to listOf("jabarkan", "uraikan", "ekspansikan"),
                Command.SIMPLIFY to listOf("sederhanakan"),
                Command.DERIVATIVE to listOf("turunan pertama dari", "turunan pertama", "turunan dari", "turunan", "turunkan"),
                Command.INTEGRAL to listOf("integral dari", "integral", "integralkan", "antiturunan dari", "antiturunan"),
                Command.LIMIT to listOf("limit dari", "limit"),
                Command.SUM to listOf("jumlah dari", "jumlah", "jumlahkan"),
                Command.PLOT to listOf("gambarkan grafik", "gambarkan", "grafik dari", "grafik", "gambar"),
                Command.CALCULATE to listOf("hitunglah", "hitung", "tentukan", "carilah", "cari", "berapa"),
                Command.PARTIAL_FRACTIONS to listOf("pecahan parsial dari", "pecahan parsial", "uraikan menjadi pecahan parsial", "dekomposisi pecahan parsial dari", "dekomposisi pecahan parsial"),
            ),
            of = setOf("dari"),
            from = words("dari"),
            to = words("sampai", "hingga", "ke"),
            forWords = words("untuk"),
            respect = words("terhadap"),
            approach = words("saat", "ketika"),
            nouns = words("persamaan", "fungsi", "bentuk"),
            arrows = listOf("mendekati", "menuju"),
            between = listOf("antara" to "dan"),
        )

        val ITALIAN = Vocabulary(
            "it",
            mapOf(
                Command.SOLVE to listOf("risolvi", "risolvere"),
                Command.FACTOR to listOf("scomponi in fattori", "scomponi", "scomporre", "fattorizza", "fattorizzare"),
                Command.EXPAND to listOf("sviluppa", "sviluppare", "espandi"),
                Command.SIMPLIFY to listOf("semplifica", "semplificare"),
                Command.DERIVATIVE to listOf("derivata di", "derivata", "deriva", "derivare"),
                Command.INTEGRAL to listOf("integrale di", "integrale", "integra", "integrare", "primitiva di", "primitiva"),
                Command.LIMIT to listOf("limite di", "limite"),
                Command.SUM to listOf("somma di", "somma", "sommatoria di", "sommatoria"),
                Command.PLOT to listOf("disegna", "traccia", "grafico di", "grafico"),
                Command.CALCULATE to listOf("calcola", "calcolare", "trova", "determina", "quanto fa"),
                Command.PARTIAL_FRACTIONS to listOf("fratti semplici di", "fratti semplici", "scomponi in fratti semplici", "decomposizione in fratti semplici di", "decomposizione in fratti semplici", "scomporre in fratti semplici"),
            ),
            of = setOf("di", "del", "della"),
            from = words("da"),
            to = words("a", "fino a"),
            forWords = words("per"),
            respect = words("rispetto a"),
            approach = words("quando"),
            articles = setOf("il", "lo", "la", "i", "gli", "le", "l'"),
            nouns = words("equazione", "funzione", "espressione", "polinomio"),
            arrows = listOf("che tende a", "tende a"),
            between = listOf("tra" to "e", "fra" to "e"),
            // "scomponi x^2-1 in fattori".
            ignored = setOf("in", "fattori"),
        )

        val DUTCH = Vocabulary(
            "nl",
            mapOf(
                Command.SOLVE to listOf("los op", "oplossen", "los"),
                Command.FACTOR to listOf("ontbind in factoren", "ontbinden in factoren", "ontbind", "factoriseer"),
                Command.EXPAND to listOf("werk uit", "werk", "uitwerken", "haakjes wegwerken", "vermenigvuldig uit"),
                Command.SIMPLIFY to listOf("vereenvoudig", "vereenvoudigen"),
                Command.DERIVATIVE to listOf("afgeleide van", "afgeleide", "differentieer", "differentiëren"),
                Command.INTEGRAL to listOf("integraal van", "integraal", "integreer", "primitieve van", "primitieve"),
                Command.LIMIT to listOf("limiet van", "limiet"),
                Command.SUM to listOf("som van", "som"),
                Command.PLOT to listOf("teken", "plot", "grafiek van", "grafiek"),
                Command.CALCULATE to listOf("bereken", "reken uit", "bepaal", "wat is"),
                Command.PARTIAL_FRACTIONS to listOf("breuksplitsen", "splits in breuken", "splits in partiële breuken", "partiële breuken van", "partiële breuken", "breuksplitsing van", "breuksplitsing"),
            ),
            of = setOf("van"),
            from = words("van"),
            to = words("tot"),
            forWords = words("voor"),
            respect = words("naar"),
            approach = words("als", "wanneer"),
            articles = setOf("de", "het", "een"),
            nouns = words("vergelijking", "functie", "uitdrukking"),
            arrows = listOf("nadert tot", "nadert", "gaat naar"),
            between = listOf("tussen" to "en"),
            // "los x^2 = 4 op", "werk (x+1)^2 uit", "ontbind x^2-1 in factoren": the verb's other half.
            ignored = setOf("op", "uit", "in", "factoren"),
        )

        val POLISH = Vocabulary(
            "pl",
            mapOf(
                Command.SOLVE to listOf("rozwiąż", "rozwiaz", "rozwiązać"),
                Command.FACTOR to listOf("rozłóż na czynniki", "rozloz na czynniki", "rozłóż", "faktoryzuj"),
                Command.EXPAND to listOf("wymnóż", "wymnoz", "rozwiń", "rozwin"),
                Command.SIMPLIFY to listOf("uprość", "uprosc", "uprościć"),
                Command.DERIVATIVE to listOf("oblicz pochodną", "pochodna z", "pochodna", "pochodną", "różniczkuj", "rozniczkuj"),
                Command.INTEGRAL to listOf("oblicz całkę", "całka oznaczona z", "całka nieoznaczona z", "całka oznaczona", "całka nieoznaczona", "całka z", "całka", "całkę", "calka z", "calka", "scałkuj", "całkuj"),
                Command.LIMIT to listOf("oblicz granicę", "granica", "granica z"),
                Command.SUM to listOf("suma"),
                Command.PLOT to listOf("narysuj wykres", "narysuj", "wykres funkcji", "wykres"),
                Command.CALCULATE to listOf("oblicz", "wyznacz", "znajdź", "ile wynosi", "ile to"),
                Command.PARTIAL_FRACTIONS to listOf("rozłóż na ułamki proste", "ułamki proste", "rozkład na ułamki proste", "rozloz na ulamki proste", "rozklad na ulamki proste", "ulamki proste"),
            ),
            of = setOf("z"),
            from = words("od"),
            to = words("do"),
            forWords = words("dla"),
            respect = words("względem", "wzgledem", "po"),
            approach = words("gdy", "przy"),
            nouns = words("równanie", "funkcji", "funkcję", "wyrażenie", "wielomian"),
            arrows = listOf("dąży do", "zmierza do"),
            // "rozłóż x^2-1 na czynniki".
            ignored = setOf("na", "czynniki"),
        )

        val PORTUGUESE = Vocabulary(
            "pt",
            mapOf(
                Command.SOLVE to listOf("resolva", "resolver", "resolve"),
                Command.FACTOR to listOf("fatore", "fatorar", "fatorize"),
                Command.EXPAND to listOf("expanda", "expandir", "desenvolva"),
                Command.SIMPLIFY to listOf("simplifique", "simplificar"),
                Command.DERIVATIVE to listOf("derivada de", "derivada", "derive", "derivar"),
                Command.INTEGRAL to listOf("integral de", "integral", "integre", "integrar", "primitiva de", "primitiva", "antiderivada de"),
                Command.LIMIT to listOf("limite de", "limite"),
                Command.SUM to listOf("soma de", "soma", "somatório de", "somatório"),
                Command.PLOT to listOf("plote", "desenhe", "esboce", "gráfico de", "gráfico", "grafico de"),
                Command.CALCULATE to listOf("calcule", "calcular", "determine", "encontre", "ache", "quanto é", "quanto e"),
                Command.PARTIAL_FRACTIONS to listOf("frações parciais de", "frações parciais", "decomponha em frações parciais", "decomposição em frações parciais de", "decomposição em frações parciais", "decompor em frações parciais", "decomponha em fracoes parciais", "fracoes parciais de", "fracoes parciais"),
            ),
            of = setOf("de", "da", "do"),
            from = words("de"),
            to = words("a", "até", "ate"),
            forWords = words("para"),
            respect = words("em relação a"),
            approach = words("quando"),
            articles = setOf("o", "a", "os", "as"),
            nouns = words("equação", "função", "expressão", "polinômio"),
            arrows = listOf("tende a", "tende para"),
            between = listOf("entre" to "e"),
        )

        val TURKISH = Vocabulary(
            "tr",
            mapOf(
                Command.SOLVE to listOf("çöz", "coz", "çözün", "çözünüz"),
                Command.FACTOR to listOf("çarpanlarına ayırın", "çarpanlarına ayır", "çarpanlara ayır", "carpanlarina ayir"),
                Command.EXPAND to listOf("parantezleri açın", "parantezleri aç", "genişletin", "genişlet", "aç", "açınız"),
                Command.SIMPLIFY to listOf("sadeleştirin", "sadeleştir", "sadelestir", "basitleştir"),
                Command.DERIVATIVE to listOf("türevini al", "türevini", "türevi", "türev", "turev"),
                Command.INTEGRAL to listOf("integralini al", "integralini", "integrali", "integral"),
                Command.LIMIT to listOf("limitini", "limiti", "limit"),
                Command.SUM to listOf("toplamı", "toplam"),
                Command.PLOT to listOf("grafiğini çiz", "grafiğini", "çizin", "çiz", "ciz", "grafiği", "grafik"),
                Command.CALCULATE to listOf("hesaplayın", "hesapla", "bulun", "bul", "kaç eder", "kac eder"),
                Command.PARTIAL_FRACTIONS to listOf("basit kesirlere ayır", "basit kesirler", "basit kesirlerine ayırınız", "basit kesirlerine ayırın", "basit kesirlerine ayır", "basit kesirlere ayırınız", "basit kesirlere ayırın", "basit kesirlere ayir", "basit kesirlerine ayir"),
            ),
            of = emptySet(),
            from = words("'dan", "'den", "'tan", "'ten"),
            to = words("'a", "'e", "'ya", "'ye"),
            forWords = words("için"),
            respect = words("'a göre", "'e göre", "'ya göre", "'ye göre"),
            approach = words("iken"),
            postpositional = true,
            commandLast = true,
            particles = setOf("'i", "'ı", "'u", "'ü", "'yi", "'yı", "'yu", "'yü", "'in", "'ın", "'un", "'ün", "'nin", "'nın", "'nun", "'nün"),
            ignored = setOf("kadar"),
            nouns = words("denklemini", "denklemi", "fonksiyonunun", "ifadesini"),
        )

        val VIETNAMESE = Vocabulary(
            "vi",
            mapOf(
                Command.SOLVE to listOf("giải phương trình", "giải", "giai"),
                Command.FACTOR to listOf("phân tích đa thức thành nhân tử", "phân tích thành nhân tử", "phân tích đa thức", "phân tích", "phan tich"),
                Command.EXPAND to listOf("khai triển", "khai trien"),
                Command.SIMPLIFY to listOf("rút gọn", "rut gon", "đơn giản"),
                Command.DERIVATIVE to listOf("đạo hàm của", "đạo hàm", "dao ham"),
                Command.INTEGRAL to listOf("tích phân của", "tích phân", "nguyên hàm của", "nguyên hàm", "tich phan", "nguyen ham"),
                Command.LIMIT to listOf("giới hạn của", "giới hạn", "gioi han"),
                Command.SUM to listOf("tổng của", "tổng", "tong"),
                Command.PLOT to listOf("vẽ đồ thị", "vẽ", "đồ thị của", "đồ thị", "ve do thi"),
                Command.CALCULATE to listOf("tính giá trị", "tính", "tinh", "tìm"),
                Command.PARTIAL_FRACTIONS to listOf("phân tích thành phân thức đơn giản", "phân thức đơn giản", "phân tích thành tổng các phân thức đơn giản", "phân tích thành các phân thức đơn giản"),
            ),
            of = setOf("của"),
            from = words("từ"),
            to = words("đến", "tới"),
            forWords = words("với"),
            respect = words("theo", "đối với"),
            approach = words("khi"),
            nouns = words("phương trình", "hàm số", "đa thức", "biểu thức"),
            arrows = listOf("tiến tới", "tiến đến", "dần tới"),
        )

        val RUSSIAN = Vocabulary(
            "ru",
            mapOf(
                Command.SOLVE to listOf("реши уравнение", "решите уравнение", "реши", "решить", "решите"),
                Command.FACTOR to listOf("разложи на множители", "разложите на множители", "разложить на множители", "разложи", "разложите", "разложить"),
                Command.EXPAND to listOf("раскрой скобки", "раскройте скобки", "раскрыть скобки", "раскрой", "раскройте"),
                Command.SIMPLIFY to listOf("упрости выражение", "упростите выражение", "упрости", "упростить", "упростите"),
                Command.DERIVATIVE to listOf("найди производную", "найдите производную", "вычисли производную", "вычислите производную", "производная от", "производная", "продифференцируй"),
                Command.INTEGRAL to listOf("найди интеграл", "найдите интеграл", "вычисли интеграл", "вычислите интеграл", "интеграл от", "интеграл", "проинтегрируй", "первообразная от", "первообразная"),
                Command.LIMIT to listOf("найди предел", "найдите предел", "вычисли предел", "вычислите предел", "предел"),
                Command.SUM to listOf("сумма"),
                Command.PLOT to listOf("построй график", "постройте график", "построй", "постройте", "нарисуй", "график"),
                Command.CALCULATE to listOf("вычисли", "вычислите", "вычислить", "посчитай", "найди значение", "найдите значение", "сколько будет"),
                Command.PARTIAL_FRACTIONS to listOf("разложи на простейшие дроби", "разложите на простейшие дроби", "простейшие дроби", "разложить на простейшие дроби", "разложение на простейшие дроби"),
            ),
            of = setOf("от"),
            from = words("от"),
            to = words("до"),
            forWords = words("для"),
            respect = words("по"),
            approach = words("при"),
            nouns = words("уравнение", "функции", "выражение"),
            arrows = listOf("стремится к"),
        )

        val UKRAINIAN = Vocabulary(
            "uk",
            mapOf(
                Command.SOLVE to listOf("розв'яжи рівняння", "розв'яжіть рівняння", "розв'яжи", "розв'яжіть", "розв'язати"),
                Command.FACTOR to listOf("розклади на множники", "розкладіть на множники", "розкласти на множники", "розклади", "розкладіть"),
                Command.EXPAND to listOf("розкрий дужки", "розкрийте дужки", "розкрий", "розкрийте"),
                Command.SIMPLIFY to listOf("спрости вираз", "спростіть вираз", "спрости", "спростіть", "спростити"),
                Command.DERIVATIVE to listOf("знайди похідну", "знайдіть похідну", "обчисли похідну", "обчисліть похідну", "похідна від", "похідна", "продиференціюй"),
                Command.INTEGRAL to listOf("знайди інтеграл", "знайдіть інтеграл", "обчисли інтеграл", "обчисліть інтеграл", "інтеграл від", "інтеграл", "проінтегруй", "первісна від", "первісна"),
                Command.LIMIT to listOf("знайди границю", "знайдіть границю", "обчисли границю", "обчисліть границю", "границя", "межа"),
                Command.SUM to listOf("сума"),
                Command.PLOT to listOf("побудуй графік", "побудуйте графік", "побудуй", "побудуйте", "намалюй", "графік"),
                Command.CALCULATE to listOf("обчисли", "обчисліть", "порахуй", "скільки буде"),
                Command.PARTIAL_FRACTIONS to listOf("розклади на найпростіші дроби", "розкладіть на найпростіші дроби", "найпростіші дроби", "розкласти на найпростіші дроби", "розклад на найпростіші дроби"),
            ),
            of = setOf("від"),
            from = words("від"),
            to = words("до"),
            forWords = words("для"),
            respect = words("по", "за"),
            approach = words("при"),
            nouns = words("рівняння", "функції", "вираз"),
            arrows = listOf("прямує до"),
        )

        val ARABIC = Vocabulary(
            "ar",
            mapOf(
                Command.SOLVE to listOf("حل المعادلة", "حل", "أوجد حل", "اوجد حل"),
                Command.FACTOR to listOf("حلل إلى العوامل", "حلل الى العوامل", "حلل إلى عوامل", "حلل الى عوامل", "حلل", "فكك"),
                Command.EXPAND to listOf("فك الأقواس", "فك الاقواس", "انشر", "وسع"),
                Command.SIMPLIFY to listOf("بسط", "اختصر"),
                Command.DERIVATIVE to listOf("أوجد المشتقة", "اوجد المشتقة", "أوجد مشتقة", "اوجد مشتقة", "احسب المشتقة", "احسب مشتقة", "مشتقة", "المشتقة", "اشتق"),
                Command.INTEGRAL to listOf("احسب التكامل", "احسب تكامل", "أوجد التكامل", "اوجد التكامل", "تكامل", "التكامل", "كامل", "الدالة الأصلية"),
                Command.LIMIT to listOf("احسب النهاية", "احسب نهاية", "أوجد النهاية", "اوجد النهاية", "نهاية", "النهاية"),
                Command.SUM to listOf("مجموع", "المجموع"),
                Command.PLOT to listOf("ارسم", "مثل بيانيا", "التمثيل البياني"),
                Command.CALCULATE to listOf("احسب", "كم يساوي"),
                Command.PARTIAL_FRACTIONS to listOf("الكسور الجزئية", "كسور جزئية", "حلل إلى كسور جزئية", "حلل الى كسور جزئية", "حللوا إلى كسور جزئية", "حللوا الى كسور جزئية"),
            ),
            of = emptySet(),
            from = words("من"),
            to = words("إلى", "الى"),
            forWords = words("ل"),
            respect = words("بالنسبة إلى", "بالنسبة الى", "بالنسبة ل"),
            approach = words("عندما"),
        )

        val HEBREW = Vocabulary(
            "he",
            mapOf(
                Command.SOLVE to listOf("פתור את המשוואה", "פתרו את המשוואה", "פתרי את המשוואה", "פתור", "פתרי", "פתרו"),
                Command.FACTOR to listOf("פרק לגורמים", "פרקי לגורמים", "פרקו לגורמים", "פרק"),
                Command.EXPAND to listOf("פתח סוגריים", "פתחו סוגריים", "פתחי סוגריים", "פתח", "פתחו", "פתחי"),
                Command.SIMPLIFY to listOf("פשט", "פשטי", "פשטו"),
                Command.DERIVATIVE to listOf("הנגזרת של", "נגזרת של", "נגזרת", "גזור", "גזרו", "גזרי"),
                Command.INTEGRAL to listOf("האינטגרל של", "אינטגרל של", "אינטגרל", "פונקציה קדומה של", "פונקציה קדומה"),
                Command.LIMIT to listOf("הגבול של", "גבול של", "גבול"),
                Command.SUM to listOf("הסכום של", "סכום של", "סכום"),
                Command.PLOT to listOf("שרטט", "שרטטו", "שרטטי", "צייר", "ציירו", "ציירי", "גרף של", "גרף"),
                Command.CALCULATE to listOf("חשב", "חשבי", "חשבו", "כמה זה"),
                Command.PARTIAL_FRACTIONS to listOf("פירוק לשברים חלקיים", "שברים חלקיים", "פרק לשברים חלקיים", "פרקו לשברים חלקיים", "פרקי לשברים חלקיים"),
            ),
            of = setOf("של"),
            from = words("מ", "החל מ"),
            to = words("עד"),
            forWords = words("עבור"),
            respect = words("לפי", "ביחס ל"),
            approach = words("כאשר", "כש"),
            // The object marker of "פתרו את …".
            ignored = setOf("את"),
        )

        val HINDI = Vocabulary(
            "hi",
            mapOf(
                Command.SOLVE to listOf("हल"),
                Command.FACTOR to listOf("गुणनखंड", "गुणनखण्ड"),
                Command.EXPAND to listOf("विस्तार", "प्रसार"),
                Command.SIMPLIFY to listOf("सरल", "सरलीकरण"),
                Command.DERIVATIVE to listOf("अवकलज", "अवकलन", "व्युत्पन्न"),
                Command.INTEGRAL to listOf("समाकल", "समाकलन", "प्रतिअवकलज"),
                Command.LIMIT to listOf("सीमा"),
                Command.SUM to listOf("योगफल", "योग"),
                Command.PLOT to listOf("ग्राफ़", "ग्राफ", "आलेख"),
                Command.CALCULATE to listOf("गणना", "परिकलन", "कितना है"),
                Command.PARTIAL_FRACTIONS to listOf("आंशिक भिन्न", "आंशिक भिन्नों में", "आंशिक भिन्नों"),
            ),
            of = setOf("का", "की", "के"),
            from = words("से"),
            to = words("तक"),
            forWords = words("के लिए"),
            respect = words("के सापेक्ष"),
            // "जब" stands before its value, which a postpositional language cannot read: x->0 says it.
            approach = emptyList(),
            postpositional = true,
            commandLast = true,
            particles = setOf("का", "की", "के", "को"),
            // "हल करें x^2=4": the verb's helper after a command that comes first.
            ignored = setOf("करें", "करो", "कीजिए", "कीजिये"),
            endings = listOf("ज्ञात कीजिए", "ज्ञात कीजिये", "ज्ञात करें", "ज्ञात करो", "करें", "करो", "कीजिए", "कीजिये", "बनाएं", "बनाएँ", "बनाओ",
                "खींचिए", "खींचें", "खींचो"),
        )

        val JAPANESE = Vocabulary(
            "ja",
            mapOf(
                Command.SOLVE to listOf("方程式を解く", "解いて", "解く", "解け", "解きなさい", "解"),
                Command.FACTOR to listOf("因数分解"),
                Command.EXPAND to listOf("展開"),
                Command.SIMPLIFY to listOf("簡単にして", "簡単に", "簡単化", "簡約", "約分", "整理"),
                Command.DERIVATIVE to listOf("導関数", "微分"),
                Command.INTEGRAL to listOf("不定積分", "原始関数", "積分"),
                Command.LIMIT to listOf("極限値", "極限"),
                Command.SUM to listOf("総和", "和"),
                Command.PLOT to listOf("グラフを描いて", "グラフを描く", "グラフを描け", "グラフをかいて", "グラフをかく", "グラフをかけ", "グラフ"),
                Command.CALCULATE to listOf("計算", "の値"),
                Command.PARTIAL_FRACTIONS to listOf("部分分数分解", "部分分数に分解"),
            ),
            of = setOf("の"),
            from = words("から"),
            to = words("まで"),
            forWords = words("について"),
            respect = words("で"),
            approach = words("のとき"),
            postpositional = true,
            commandLast = true,
            particles = setOf("を", "の", "は"),
            // "…の導関数を求めよ": the textbook's "find" after what is found.
            endings = listOf("を求めてください", "を求めなさい", "を求めよ", "を求めて", "を求める", "してください", "して下さい", "ください",
                "しなさい", "して", "せよ", "する"),
            ignored = setOf("方程式"),
        )

        val KOREAN = Vocabulary(
            "ko",
            mapOf(
                Command.SOLVE to listOf("방정식 풀기", "풀어라", "풀어", "푸시오", "풀기", "풀이", "의 해"),
                Command.FACTOR to listOf("인수분해"),
                Command.EXPAND to listOf("전개"),
                Command.SIMPLIFY to listOf("간단히", "정리"),
                Command.DERIVATIVE to listOf("도함수", "미분"),
                Command.INTEGRAL to listOf("부정적분", "정적분", "원시함수", "적분"),
                Command.LIMIT to listOf("극한값", "극한"),
                Command.SUM to listOf("합"),
                Command.PLOT to listOf("그래프를 그리시오", "그래프를 그려라", "그래프를 그려", "그래프 그리기", "그래프"),
                Command.CALCULATE to listOf("계산", "값 구하기", "의 값"),
                Command.PARTIAL_FRACTIONS to listOf("부분분수로 분해", "부분분수", "부분분수분해", "부분분수 분해"),
            ),
            of = setOf("의"),
            from = words("부터", "에서"),
            to = words("까지"),
            forWords = words("에 대해", "에 대하여"),
            respect = words("에 관하여", "에 관해"),
            approach = words("일 때"),
            postpositional = true,
            commandLast = true,
            particles = setOf("을", "를", "의", "은", "는"),
            endings = listOf("를 구하시오", "을 구하시오", "를 구하세요", "을 구하세요", "를 구하여라", "을 구하여라", "를 구해", "을 구해",
                "하여라", "해 주세요", "해주세요", "하시오", "하세요", "해 줘", "해줘", "해라", "하기", "해"),
            ignored = setOf("방정식"),
        )

        val THAI = Vocabulary(
            "th",
            mapOf(
                Command.SOLVE to listOf("แก้สมการ", "แก้", "หาคำตอบ"),
                Command.FACTOR to listOf("แยกตัวประกอบ"),
                Command.EXPAND to listOf("กระจาย"),
                Command.SIMPLIFY to listOf("ทำให้อยู่ในรูปอย่างง่าย", "ลดรูป", "ทำให้ง่าย"),
                Command.DERIVATIVE to listOf("หาอนุพันธ์", "อนุพันธ์ของ", "อนุพันธ์", "ดิฟ"),
                Command.INTEGRAL to listOf("หาอินทิกรัล", "อินทิกรัลของ", "อินทิกรัล", "ปริพันธ์", "อินทิเกรต"),
                Command.LIMIT to listOf("หาลิมิต", "ลิมิตของ", "ลิมิต"),
                Command.SUM to listOf("ผลรวมของ", "ผลรวม"),
                Command.PLOT to listOf("วาดกราฟ", "เขียนกราฟ", "กราฟของ", "กราฟ", "พล็อต"),
                Command.CALCULATE to listOf("คำนวณ", "หาค่า"),
                Command.PARTIAL_FRACTIONS to listOf("แยกเศษส่วนย่อย", "เศษส่วนย่อย", "แยกเป็นเศษส่วนย่อย"),
            ),
            of = setOf("ของ"),
            from = words("จาก"),
            to = words("ถึง"),
            forWords = words("สำหรับ"),
            respect = words("เทียบกับ"),
            approach = words("เมื่อ"),
            // "จงแก้สมการ …": the imperative every exercise starts with.
            prefixes = listOf("จง"),
        )

        /** Simplified and Traditional characters together: a word is the same command in either. */
        val CHINESE = Vocabulary(
            "zh",
            mapOf(
                Command.SOLVE to listOf("解方程式", "解方程", "求解", "解"),
                Command.FACTOR to listOf("因式分解", "分解因式"),
                Command.EXPAND to listOf("展开", "展開"),
                Command.SIMPLIFY to listOf("化简", "化簡", "简化", "簡化"),
                Command.DERIVATIVE to listOf("求导数", "求導數", "求导", "求導", "导函数", "導函數", "导数", "導數", "微分"),
                Command.INTEGRAL to listOf("求积分", "求積分", "不定积分", "不定積分", "定积分", "定積分", "原函数", "原函數", "积分", "積分"),
                Command.LIMIT to listOf("求极限", "求極限", "极限", "極限"),
                Command.SUM to listOf("求和"),
                Command.PLOT to listOf("画出", "畫出", "画图", "畫圖", "作图", "作圖", "绘制", "繪製", "图像", "圖像", "图形", "圖形"),
                Command.CALCULATE to listOf("计算", "計算", "求值", "等于多少", "等於多少"),
                Command.PARTIAL_FRACTIONS to listOf("部分分式分解", "部分分式", "分解为部分分式", "分解成部分分式", "化为部分分式", "分解為部分分式", "化為部分分式", "化成部分分式"),
            ),
            of = setOf("的"),
            from = words("从", "從"),
            to = words("到"),
            forWords = words("关于", "關於"),
            respect = words("对", "對"),
            approach = words("当", "當"),
            commandLast = true,
            particles = setOf("的"),
            // "求 x^3 的导数", "把 x^2-1 因式分解": a leading "find" or object marker; "当 x->0 时": 时 closes the approach.
            ignored = setOf("求", "把", "将", "將"),
            closers = setOf("时", "時"),
        )

        val ALL: List<Vocabulary> = listOf(
            ENGLISH, GERMAN, SPANISH, FRENCH, INDONESIAN, ITALIAN, DUTCH, POLISH, PORTUGUESE, TURKISH, VIETNAMESE, RUSSIAN,
            UKRAINIAN, ARABIC, HEBREW, HINDI, JAPANESE, KOREAN, THAI, CHINESE,
        )

        /** The vocabulary of an app language ("de", "de-CH", "pt-BR", "zh-Hant-TW", Android's old "iw" and "in"), or null. */
        fun of(language: String): Vocabulary? {
            val base = when (val b = language.lowercase(Locale.ROOT).substringBefore('-').substringBefore('_')) {
                "iw" -> "he"
                "in" -> "id"
                else -> b
            }
            return ALL.firstOrNull { it.language == base }
        }
    }
}

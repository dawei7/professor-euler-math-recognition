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
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Typed("")
        val vocabularies = listOfNotNull(Vocabulary.of(language), Vocabulary.ENGLISH).distinct()
        derivativeOperator(trimmed)?.let { return it }
        for (v in vocabularies) {
            val (command, rest) = v.command(trimmed) ?: continue
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
        val slots = split(rest, vocabularies)
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
            Command.PLOT -> return Typed(latex(main), Choice(Mode.GRAPH, letterOf(slots[Slot.FOR] ?: slots[Slot.RESPECT])))
            Command.DERIVATIVE -> {
                val (body, dLetter) = stripDifferential(main)
                val letter = letterOf(slots[Slot.RESPECT] ?: slots[Slot.FOR]) ?: dLetter
                return Typed(latex(body), Choice(Mode.DERIVATIVE, letter))
            }
            Command.INTEGRAL -> {
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
                val approach = slots[Slot.AS] ?: slots[Slot.FOR]
                if (approach != null) {
                    val arrow = ARROW.find(approach)
                    if (arrow != null) {
                        variable = approach.substring(0, arrow.range.first).trim()
                        point = approach.substring(arrow.range.last + 1).trim()
                    } else {
                        variable = approach.trim()
                    }
                }
                if (point == null) point = slots[Slot.TO]
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
                val index = slots[Slot.FOR] ?: slots[Slot.RESPECT]
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
        val slots = LinkedHashMap<Slot, StringBuilder>()
        var current = Slot.MAIN
        var depth = 0
        var k = 0
        // "of" right after the command belongs to it: "derivative of x²", "Ableitung von x²".
        if (words.isNotEmpty() && vocabularies.any { v -> words[0].lowercase(Locale.ROOT) in v.of }) k = 1
        while (k < words.size) {
            if (depth == 0) {
                val match = vocabularies.firstNotNullOfOrNull { v -> v.connective(words, k) }
                if (match != null) {
                    current = match.first
                    k += match.second
                    continue
                }
            }
            val w = words[k]
            depth += w.count { it == '(' || it == '[' || it == '{' } - w.count { it == ')' || it == ']' || it == '}' }
            slots.getOrPut(current) { StringBuilder() }.let { if (it.isNotEmpty()) it.append(' '); it.append(w) }
            k++
        }
        return slots.mapValues { it.value.toString() }
    }

    private fun Vocabulary.connective(words: List<String>, k: Int): Pair<Slot, Int>? {
        for ((slot, phrases) in listOf(Slot.RESPECT to respect, Slot.FROM to from, Slot.TO to to, Slot.FOR to forWords, Slot.AS to approach)) {
            for (p in phrases.sortedByDescending { it.size }) {
                if (k + p.size <= words.size && p.indices.all { words[k + it].lowercase(Locale.ROOT) == p[it] }) return slot to p.size
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
    } catch (e: RuntimeException) {
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
enum class Command { SOLVE, FACTOR, EXPAND, SIMPLIFY, DERIVATIVE, INTEGRAL, LIMIT, SUM, PLOT, CALCULATE }

/**
 * The command words of one language and the words that join a command's parts ("from … to", "for", "with respect to"),
 * all in lower case. A phrase is matched whole at the start of the line; the longest first.
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
) {
    private val phrases: List<Pair<String, Command>> =
        commands.flatMap { (c, list) -> list.map { it to c } }.sortedByDescending { it.first.length }

    /** The command a line starts with, and the rest of the line. */
    internal fun command(text: String): Pair<Command, String>? {
        val lower = text.lowercase(Locale.ROOT)
        for ((phrase, command) in phrases) {
            if (!lower.startsWith(phrase)) continue
            val after = lower.getOrNull(phrase.length)
            if (after != null && (after.isLetter())) continue
            return command to text.substring(phrase.length).trim().trimStart(':').trim()
        }
        return null
    }

    companion object {
        private fun words(vararg phrases: String) = phrases.map { it.split(' ') }

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
                Command.CALCULATE to listOf("calculate", "compute", "evaluate", "what is"),
            ),
            of = setOf("of"),
            from = words("from"),
            to = words("to"),
            forWords = words("for"),
            respect = words("with respect to", "wrt"),
            approach = words("as", "when"),
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
                Command.PLOT to listOf("zeichne", "plotte", "graph von", "graph"),
                Command.CALCULATE to listOf("berechne", "rechne", "was ist"),
            ),
            of = setOf("von"),
            from = words("von"),
            to = words("bis", "gegen"),
            forWords = words("für"),
            respect = words("nach"),
            approach = words("wenn"),
        )

        val ALL: List<Vocabulary> = listOf(ENGLISH, GERMAN)

        /** The vocabulary of an app language ("de", "de-CH", "pt-BR"), or null where there is none yet. */
        fun of(language: String): Vocabulary? {
            val base = language.lowercase(Locale.ROOT).substringBefore('-').substringBefore('_')
            return ALL.firstOrNull { it.language == base }
        }
    }
}

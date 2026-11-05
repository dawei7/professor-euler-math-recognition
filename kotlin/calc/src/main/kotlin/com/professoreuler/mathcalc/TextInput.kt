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
        // "of" right after the command belongs to it: "derivative of x²", "Ableitung von x²".
        if (words.isNotEmpty() && vocabularies.any { v -> Vocabulary.lower(words[0]) in v.of }) k = 1
        while (k < words.size) {
            if (depth == 0) {
                if (Vocabulary.lower(words[k]) in ignored) { k++; continue }
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
) {
    private val phrases: List<Pair<String, Command>> =
        commands.flatMap { (c, list) -> list.map { lower(it) to c } }.sortedByDescending { it.first.length }

    /** The command words of [command], as this language writes them (the first is its usual one). */
    fun words(command: Command): List<String> = commands[command].orEmpty()

    /** The command a line starts with, and the rest of the line. */
    internal fun command(text: String): Pair<Command, String>? {
        val lower = lower(text)
        for ((phrase, command) in phrases) {
            if (!lower.startsWith(phrase)) continue
            val after = lower.getOrNull(phrase.length)
            if (after != null && joins(phrase.last(), after)) continue
            return command to text.substring(phrase.length).trim().trimStart(':', '：').trim()
        }
        return null
    }

    /** The command a line ends with ("x^2-1を因数分解", "x^2-1 का गुणनखंड करें"), and the rest of the line. */
    internal fun commandAtEnd(text: String): Pair<Command, String>? {
        val line = text.trimEnd().trimEnd('.', '。', '!', '！', '?', '？').trimEnd()
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
            val s = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
                .replace('’', '\'').replace('ʼ', '\'').replace('‘', '\'')
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
                Command.PLOT to listOf("grafica", "graficar", "gráfica de", "gráfica", "dibuja", "dibujar"),
                Command.CALCULATE to listOf("calcula", "calcular", "evalúa", "evaluar", "cuánto es", "cuanto es"),
            ),
            of = setOf("de"),
            from = words("de", "desde"),
            to = words("a", "hasta"),
            forWords = words("para"),
            respect = words("respecto a", "respecto de", "con respecto a"),
            approach = words("cuando"),
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
                Command.CALCULATE to listOf("calcule", "calculer", "calculez", "évalue", "combien fait"),
            ),
            of = setOf("de", "du"),
            from = words("de"),
            to = words("à", "a", "jusqu'à"),
            forWords = words("pour"),
            respect = words("par rapport à", "par rapport a", "selon"),
            approach = words("quand", "lorsque"),
        )

        val INDONESIAN = Vocabulary(
            "id",
            mapOf(
                Command.SOLVE to listOf("selesaikan", "pecahkan", "tentukan penyelesaian"),
                Command.FACTOR to listOf("faktorkan", "faktorisasi"),
                Command.EXPAND to listOf("jabarkan", "uraikan", "ekspansikan"),
                Command.SIMPLIFY to listOf("sederhanakan"),
                Command.DERIVATIVE to listOf("turunan dari", "turunan", "turunkan"),
                Command.INTEGRAL to listOf("integral dari", "integral", "integralkan", "antiturunan dari", "antiturunan"),
                Command.LIMIT to listOf("limit dari", "limit"),
                Command.SUM to listOf("jumlah dari", "jumlah", "jumlahkan"),
                Command.PLOT to listOf("gambarkan grafik", "gambarkan", "grafik dari", "grafik", "gambar"),
                Command.CALCULATE to listOf("hitunglah", "hitung", "berapa"),
            ),
            of = setOf("dari"),
            from = words("dari"),
            to = words("sampai", "hingga", "ke"),
            forWords = words("untuk"),
            respect = words("terhadap"),
            approach = words("saat", "ketika"),
        )

        val ITALIAN = Vocabulary(
            "it",
            mapOf(
                Command.SOLVE to listOf("risolvi", "risolvere"),
                Command.FACTOR to listOf("scomponi", "scomporre", "fattorizza", "fattorizzare"),
                Command.EXPAND to listOf("sviluppa", "sviluppare", "espandi"),
                Command.SIMPLIFY to listOf("semplifica", "semplificare"),
                Command.DERIVATIVE to listOf("derivata di", "derivata", "deriva", "derivare"),
                Command.INTEGRAL to listOf("integrale di", "integrale", "integra", "integrare", "primitiva di", "primitiva"),
                Command.LIMIT to listOf("limite di", "limite"),
                Command.SUM to listOf("somma di", "somma", "sommatoria di", "sommatoria"),
                Command.PLOT to listOf("disegna", "traccia", "grafico di", "grafico"),
                Command.CALCULATE to listOf("calcola", "calcolare", "quanto fa"),
            ),
            of = setOf("di", "del", "della"),
            from = words("da"),
            to = words("a", "fino a"),
            forWords = words("per"),
            respect = words("rispetto a"),
            approach = words("quando"),
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
                Command.CALCULATE to listOf("bereken", "reken uit", "wat is"),
            ),
            of = setOf("van"),
            from = words("van"),
            to = words("tot"),
            forWords = words("voor"),
            respect = words("naar"),
            approach = words("als", "wanneer"),
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
                Command.DERIVATIVE to listOf("oblicz pochodną", "pochodna z", "pochodna", "różniczkuj", "rozniczkuj"),
                Command.INTEGRAL to listOf("oblicz całkę", "całka z", "całka", "calka z", "calka", "scałkuj", "całkuj"),
                Command.LIMIT to listOf("oblicz granicę", "granica", "granica z"),
                Command.SUM to listOf("suma"),
                Command.PLOT to listOf("narysuj wykres", "narysuj", "wykres funkcji", "wykres"),
                Command.CALCULATE to listOf("oblicz", "ile wynosi", "ile to"),
            ),
            of = setOf("z"),
            from = words("od"),
            to = words("do"),
            forWords = words("dla"),
            respect = words("względem", "wzgledem", "po"),
            approach = words("gdy", "przy"),
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
                Command.PLOT to listOf("plote", "desenhe", "gráfico de", "gráfico", "grafico de"),
                Command.CALCULATE to listOf("calcule", "calcular", "quanto é", "quanto e"),
            ),
            of = setOf("de", "da", "do"),
            from = words("de"),
            to = words("a", "até", "ate"),
            forWords = words("para"),
            respect = words("em relação a"),
            approach = words("quando"),
        )

        val TURKISH = Vocabulary(
            "tr",
            mapOf(
                Command.SOLVE to listOf("çöz", "coz", "çözünüz"),
                Command.FACTOR to listOf("çarpanlarına ayır", "çarpanlara ayır", "carpanlarina ayir"),
                Command.EXPAND to listOf("genişlet", "aç", "açınız"),
                Command.SIMPLIFY to listOf("sadeleştir", "sadelestir", "basitleştir"),
                Command.DERIVATIVE to listOf("türevini al", "türevi", "türev", "turev"),
                Command.INTEGRAL to listOf("integralini al", "integrali", "integral"),
                Command.LIMIT to listOf("limiti", "limit"),
                Command.SUM to listOf("toplamı", "toplam"),
                Command.PLOT to listOf("grafiğini çiz", "çiz", "ciz", "grafiği", "grafik"),
                Command.CALCULATE to listOf("hesapla", "kaç eder", "kac eder"),
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
        )

        val VIETNAMESE = Vocabulary(
            "vi",
            mapOf(
                Command.SOLVE to listOf("giải phương trình", "giải", "giai"),
                Command.FACTOR to listOf("phân tích thành nhân tử", "phân tích", "phan tich"),
                Command.EXPAND to listOf("khai triển", "khai trien"),
                Command.SIMPLIFY to listOf("rút gọn", "rut gon", "đơn giản"),
                Command.DERIVATIVE to listOf("đạo hàm của", "đạo hàm", "dao ham"),
                Command.INTEGRAL to listOf("tích phân của", "tích phân", "nguyên hàm của", "nguyên hàm", "tich phan", "nguyen ham"),
                Command.LIMIT to listOf("giới hạn của", "giới hạn", "gioi han"),
                Command.SUM to listOf("tổng của", "tổng", "tong"),
                Command.PLOT to listOf("vẽ đồ thị", "vẽ", "đồ thị của", "đồ thị", "ve do thi"),
                Command.CALCULATE to listOf("tính giá trị", "tính", "tinh"),
            ),
            of = setOf("của"),
            from = words("từ"),
            to = words("đến", "tới"),
            forWords = words("với"),
            respect = words("theo", "đối với"),
            approach = words("khi"),
        )

        val RUSSIAN = Vocabulary(
            "ru",
            mapOf(
                Command.SOLVE to listOf("реши уравнение", "реши", "решить", "решите"),
                Command.FACTOR to listOf("разложи на множители", "разложить на множители", "разложи", "разложить"),
                Command.EXPAND to listOf("раскрой скобки", "раскрыть скобки", "раскрой"),
                Command.SIMPLIFY to listOf("упрости", "упростить", "упростите"),
                Command.DERIVATIVE to listOf("найди производную", "производная от", "производная", "продифференцируй"),
                Command.INTEGRAL to listOf("найди интеграл", "интеграл от", "интеграл", "проинтегрируй", "первообразная от", "первообразная"),
                Command.LIMIT to listOf("найди предел", "предел"),
                Command.SUM to listOf("сумма"),
                Command.PLOT to listOf("построй график", "построй", "нарисуй", "график"),
                Command.CALCULATE to listOf("вычисли", "вычислить", "посчитай", "сколько будет"),
            ),
            of = setOf("от"),
            from = words("от"),
            to = words("до"),
            forWords = words("для"),
            respect = words("по"),
            approach = words("при"),
        )

        val UKRAINIAN = Vocabulary(
            "uk",
            mapOf(
                Command.SOLVE to listOf("розв'яжи рівняння", "розв'яжи", "розв'язати"),
                Command.FACTOR to listOf("розклади на множники", "розкласти на множники", "розклади"),
                Command.EXPAND to listOf("розкрий дужки", "розкрий"),
                Command.SIMPLIFY to listOf("спрости", "спростити"),
                Command.DERIVATIVE to listOf("знайди похідну", "похідна від", "похідна", "продиференціюй"),
                Command.INTEGRAL to listOf("знайди інтеграл", "інтеграл від", "інтеграл", "проінтегруй", "первісна від", "первісна"),
                Command.LIMIT to listOf("знайди границю", "границя", "межа"),
                Command.SUM to listOf("сума"),
                Command.PLOT to listOf("побудуй графік", "побудуй", "намалюй", "графік"),
                Command.CALCULATE to listOf("обчисли", "порахуй", "скільки буде"),
            ),
            of = setOf("від"),
            from = words("від"),
            to = words("до"),
            forWords = words("для"),
            respect = words("по", "за"),
            approach = words("при"),
        )

        val ARABIC = Vocabulary(
            "ar",
            mapOf(
                Command.SOLVE to listOf("حل المعادلة", "حل", "أوجد حل", "اوجد حل"),
                Command.FACTOR to listOf("حلل إلى العوامل", "حلل الى العوامل", "حلل", "فكك"),
                Command.EXPAND to listOf("فك الأقواس", "فك الاقواس", "انشر", "وسع"),
                Command.SIMPLIFY to listOf("بسط", "اختصر"),
                Command.DERIVATIVE to listOf("أوجد المشتقة", "اوجد المشتقة", "مشتقة", "المشتقة", "اشتق"),
                Command.INTEGRAL to listOf("تكامل", "التكامل", "كامل", "الدالة الأصلية"),
                Command.LIMIT to listOf("نهاية", "النهاية"),
                Command.SUM to listOf("مجموع", "المجموع"),
                Command.PLOT to listOf("ارسم", "مثل بيانيا", "التمثيل البياني"),
                Command.CALCULATE to listOf("احسب", "كم يساوي"),
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
                Command.SOLVE to listOf("פתור", "פתרי", "פתרו"),
                Command.FACTOR to listOf("פרק לגורמים", "פרקי לגורמים", "פרק"),
                Command.EXPAND to listOf("פתח סוגריים", "פתח"),
                Command.SIMPLIFY to listOf("פשט", "פשטי"),
                Command.DERIVATIVE to listOf("נגזרת של", "נגזרת", "גזור"),
                Command.INTEGRAL to listOf("אינטגרל של", "אינטגרל", "פונקציה קדומה של", "פונקציה קדומה"),
                Command.LIMIT to listOf("גבול של", "גבול"),
                Command.SUM to listOf("סכום של", "סכום"),
                Command.PLOT to listOf("שרטט", "צייר", "גרף של", "גרף"),
                Command.CALCULATE to listOf("חשב", "חשבי", "כמה זה"),
            ),
            of = setOf("של"),
            from = words("מ", "החל מ"),
            to = words("עד"),
            forWords = words("עבור"),
            respect = words("לפי", "ביחס ל"),
            approach = words("כאשר", "כש"),
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
            endings = listOf("करें", "करो", "कीजिए", "कीजिये", "बनाएं", "बनाएँ", "बनाओ"),
        )

        val JAPANESE = Vocabulary(
            "ja",
            mapOf(
                Command.SOLVE to listOf("方程式を解く", "解いて", "解く", "解け", "解"),
                Command.FACTOR to listOf("因数分解"),
                Command.EXPAND to listOf("展開"),
                Command.SIMPLIFY to listOf("簡単にして", "簡単化", "簡約", "整理"),
                Command.DERIVATIVE to listOf("導関数", "微分"),
                Command.INTEGRAL to listOf("不定積分", "原始関数", "積分"),
                Command.LIMIT to listOf("極限"),
                Command.SUM to listOf("総和", "和"),
                Command.PLOT to listOf("グラフを描いて", "グラフを描く", "グラフ"),
                Command.CALCULATE to listOf("計算", "求めて"),
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
            endings = listOf("してください", "して下さい", "ください", "しなさい", "して", "せよ", "する"),
        )

        val KOREAN = Vocabulary(
            "ko",
            mapOf(
                Command.SOLVE to listOf("방정식 풀기", "풀어라", "풀어", "푸시오", "풀기", "풀이"),
                Command.FACTOR to listOf("인수분해"),
                Command.EXPAND to listOf("전개"),
                Command.SIMPLIFY to listOf("간단히", "정리"),
                Command.DERIVATIVE to listOf("도함수", "미분"),
                Command.INTEGRAL to listOf("부정적분", "원시함수", "적분"),
                Command.LIMIT to listOf("극한"),
                Command.SUM to listOf("합"),
                Command.PLOT to listOf("그래프 그리기", "그래프"),
                Command.CALCULATE to listOf("계산", "값 구하기"),
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
            endings = listOf("하시오", "하세요", "해 줘", "해줘", "해라", "하기", "해"),
        )

        val THAI = Vocabulary(
            "th",
            mapOf(
                Command.SOLVE to listOf("แก้สมการ", "แก้", "หาคำตอบ"),
                Command.FACTOR to listOf("แยกตัวประกอบ"),
                Command.EXPAND to listOf("กระจาย"),
                Command.SIMPLIFY to listOf("ทำให้อยู่ในรูปอย่างง่าย", "ลดรูป", "ทำให้ง่าย"),
                Command.DERIVATIVE to listOf("หาอนุพันธ์", "อนุพันธ์ของ", "อนุพันธ์"),
                Command.INTEGRAL to listOf("อินทิกรัลของ", "อินทิกรัล", "ปริพันธ์"),
                Command.LIMIT to listOf("ลิมิตของ", "ลิมิต"),
                Command.SUM to listOf("ผลรวมของ", "ผลรวม"),
                Command.PLOT to listOf("วาดกราฟ", "กราฟของ", "กราฟ", "พล็อต"),
                Command.CALCULATE to listOf("คำนวณ", "หาค่า"),
            ),
            of = setOf("ของ"),
            from = words("จาก"),
            to = words("ถึง"),
            forWords = words("สำหรับ"),
            respect = words("เทียบกับ"),
            approach = words("เมื่อ"),
        )

        /** Simplified and Traditional characters together: a word is the same command in either. */
        val CHINESE = Vocabulary(
            "zh",
            mapOf(
                Command.SOLVE to listOf("解方程", "求解", "解"),
                Command.FACTOR to listOf("因式分解", "分解因式"),
                Command.EXPAND to listOf("展开", "展開"),
                Command.SIMPLIFY to listOf("化简", "化簡", "简化", "簡化"),
                Command.DERIVATIVE to listOf("求导数", "求導數", "求导", "求導", "导数", "導數", "微分"),
                Command.INTEGRAL to listOf("求积分", "求積分", "不定积分", "不定積分", "原函数", "原函數", "积分", "積分"),
                Command.LIMIT to listOf("求极限", "求極限", "极限", "極限"),
                Command.SUM to listOf("求和"),
                Command.PLOT to listOf("画图", "畫圖", "作图", "作圖", "绘制", "繪製", "图像", "圖形"),
                Command.CALCULATE to listOf("计算", "計算", "求值", "等于多少", "等於多少"),
            ),
            of = setOf("的"),
            from = words("从", "從"),
            to = words("到"),
            forWords = words("关于", "關於"),
            respect = words("对", "對"),
            approach = words("当", "當"),
            commandLast = true,
            particles = setOf("的"),
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

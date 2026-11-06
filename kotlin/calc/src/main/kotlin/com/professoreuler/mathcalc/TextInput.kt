package com.professoreuler.mathcalc

import java.util.Locale

/**
 * A line typed on the keyboard, read: the formula in LaTeX, and the choice its function asked for (null where none). Its answers
 * are written with exact fractions ([fractions]): a keyboard's `/` is its fraction bar, and sin 30° is ½, as a calculator's
 * learner expects.
 */
data class Typed(val latex: String, val choice: Choice? = null, val fractions: Boolean = true)

/**
 * Reads what a learner types: math written in a line (`x^2 + 3x - 4 = 0`, `sqrt(2)`, `e^(i pi)`, `d/dx x^3`) into the LaTeX the
 * calculator reads, and a command as its English function, the same in every language (`solve(x^2 = 4, x)`,
 * [CommandFunctions]). There are no command words: a word is letters or a function's name.
 */
object TextInput {
    /** [text] as typed, the same in every language: a function's name is English, and its arguments are split at commas. */
    fun read(text: String): Typed {
        // ¿es 97 primo?, هل 97 أولي؟: the marks of a question are no math.
        val trimmed = westernDigits(text.trim().trimStart('¿', '¡').trimEnd('؟').trim())
        if (trimmed.isEmpty()) return Typed("")
        // A line whose call cannot be made sense of is the math it holds: reading never fails.
        return try {
            readMath(trimmed)
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

    private fun readMath(written: String): Typed {
        // A command is a function, solve(x^2 = 4, x), with English names in every language (owner, 6 November 2026: "rely on
        // the strict english only best practice named functions"); there are no command words.
        CommandFunctions.read(written)?.let { return it }
        // "y' = y, y(0) = 1": an initial value after a comma is a line of its own, and so is the next equation of a system.
        val text = if ('\'' in written) written.replace(INITIAL_AFTER_COMMA, "; ") else written
        derivativeOperator(text)?.let { return it }
        return Typed(latex(text))
    }

    /** A comma before an initial value (y(0) =, x_1(0) =) or before the next equation of a system (y' =). */
    private val INITIAL_AFTER_COMMA = Regex(""",\s*(?=\p{L}(?:_\d)?'*\(\s*-?[0-9.,]+\s*\)\s*=|\p{L}(?:_\d)?'+\s*=)""")

    /**
     * What a typed line asks for, typeset as a learner would write it: the formula, a derivative as d/dx (…), an
     * antiderivative as ∫ … dx. The Calculator shows it as the line is typed ("Read as"), so a misreading shows at once.
     */
    fun display(typed: Typed): String = shown(displayed(typed))

    /** The reader's commands KaTeX has no name for (\arsinh, \erf), as \operatorname{…}: \digamma would be a letter there. */
    private fun shown(latex: String): String = NOT_KATEX.replace(latex) { "\\operatorname{${it.groupValues[1]}}" }

    private val NOT_KATEX = Regex("""\\(arsinh|arcosh|artanh|arcoth|arcsec|arccsc|arccot|sech|csch|erfc|erf|lambertw|besselj|bessely|digamma|elliptick|elliptice|sgn)(?![A-Za-z])""")

    private fun displayed(typed: Typed): String {
        CommandFunctions.latex(typed)?.let { return it }
        val choice = typed.choice ?: return typed.latex
        if (choice.mode == Mode.UNITS) return "${typed.latex}\\,\\text{${choice.letter}} \\to \\text{${choice.point}}"
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
        // d^2/dx^2: the same order above and below.
        val order = m.groupValues[1].ifEmpty { "1" }
        if (order != m.groupValues[3].ifEmpty { "1" }) return null
        val letter = m.groupValues[2]
        val body = text.substring(m.range.last + 1).trim()
        if (body.isEmpty()) return null
        if (order != "1") return Typed("\\frac{d^{$order}}{d$letter^{$order}}\\left(${latex(body)}\\right)")
        return Typed(latex(body), Choice(Mode.DERIVATIVE, letter))
    }

    /** A data list typed without brackets: "1, 2, 3" or "1 2 3"; a decimal takes a point. */
    internal fun listLatex(main: String): String {
        val t = main.trim()
        if (t.startsWith("(") || t.startsWith("[")) return latex(t)
        if (t.startsWith("{")) return latex("[" + t.removePrefix("{").removeSuffix("}") + "]")
        val items = t.split(Regex(",\\s*|\\s+")).filter { it.isNotBlank() }
        if (items.size < 2) return latex(t)
        return latex("[" + items.joinToString(", ") + "]")
    }

    /** Points typed as (1, 2) and (3, 4): each a vector, one to a line, or ([matrix]) the rows of one matrix. */
    internal fun pointsLatex(main: String, matrix: Boolean): String {
        val points = Regex("""[(\[]\s*([^()\[\]]+?)\s*[)\]]""").findAll(main).map { it.groupValues[1].trim() }
            // (1|2) as German textbooks write a point: its coordinates with a bar between (a decimal with a point there too).
            .map { if ('|' in it) it.split('|').joinToString(", ") { c -> c.trim() } else it }
            .filter { ',' in it }.toList()
        if (points.size < 2) return latex(main)
        val rows = points.map { p -> p.split(',').joinToString(", ") { it.trim() } }
        return if (matrix) latex("[" + rows.joinToString(", ") { "[$it]" } + "]") else latex(rows.joinToString("; ") { "[$it]" })
    }

    /** "a=3, b=4, gamma=60°": a triangle's values, one to a line. */
    internal fun triangleLatex(main: String): String {
        val parts = main.split(Regex("""\s*[,;]\s*(?=\\?[A-Za-zα-ωΑ-Ω]+\s*=)""")).map { it.trim() }.filter { it.isNotEmpty() }
        return latex(parts.joinToString("; "))
    }

    /** convert(5 km, mi): a number with its unit ("5 km", "5km", "20 °C") and the unit wanted. */
    internal fun conversion(value: String, wanted: String): Typed? {
        val m = Regex("""^\s*(-?\d+(?:[.,]\d+)?(?:/\d+)?)\s*(\S+)\s*$""").matchEntire(value) ?: return null
        val from = m.groupValues[2].takeIf { Units.known(it) } ?: return null
        val to = wanted.trim().takeIf { Units.known(it) } ?: return null
        return Typed(latex(m.groupValues[1]), Choice(Mode.UNITS, from, to))
    }

    internal fun letterOf(s: String?): String? {
        val t = s?.trim()?.trimEnd('.', ',', '?') ?: return null
        return if (LETTER.matches(t)) t.replace(Regex("^([A-Za-z])_?\\{?([0-9]+)\\}?$"), "$1_$2") else null
    }

    /** x if the formula has it, else its only letter, else the first. */
    internal fun mainLetterOf(latex: String): String = try {
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
    /**
     * [[1, 2], [3, 4]] (and Wolfram's {{1, 2}, {3, 4}}) as \\begin{pmatrix}1&2\\\\3&4\\end{pmatrix}, each entry read as typed math;
     * anything else as it is.
     */
    internal fun matrices(text: String): String {
        val out = StringBuilder()
        var k = 0
        while (k < text.length) {
            val open = text[k]
            if ((open == '[' || open == '{') && text.getOrNull(k + 1)?.let { it == open || it.isWhitespace() && text.substring(k + 1).trimStart().startsWith(open) } == true) {
                val close = if (open == '[') ']' else '}'
                val end = closing(text, k, open, close)
                if (end != null) {
                    val rows = parts(text.substring(k + 1, end), ',').map { it.trim() }
                    if (rows.all { it.startsWith(open) && it.endsWith(close) }) {
                        val cells = rows.map { r -> parts(r.substring(1, r.length - 1), ',').map { latex(it.trim()) } }
                        if (cells.isNotEmpty() && cells.all { it.size == cells[0].size && it.all(String::isNotEmpty) }) {
                            out.append("\\begin{pmatrix}").append(cells.joinToString("\\\\") { it.joinToString("&") }).append("\\end{pmatrix}")
                            k = end + 1
                            continue
                        }
                    }
                }
            }
            out.append(open)
            k++
        }
        return out.toString()
    }

    private val SET_OPERATIONS = listOf("∪", "∩", "∖", "\\cup", "\\cap", "\\setminus")

    private fun closing(s: String, at: Int, open: Char, close: Char): Int? {
        var depth = 0
        for (j in at until s.length) {
            if (s[j] == open) depth++
            if (s[j] == close) { depth--; if (depth == 0) return j }
        }
        return null
    }

    /** [s] split at [separator] outside every bracket. */
    private fun parts(s: String, separator: Char): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        var start = 0
        for ((j, c) in s.withIndex()) {
            if (c == '(' || c == '[' || c == '{') depth++
            if (c == ')' || c == ']' || c == '}') depth--
            if (c == separator && depth == 0) { out += s.substring(start, j); start = j + 1 }
        }
        out += s.substring(start)
        return out
    }

    fun latex(math00: String): String {
        // a_(n+1) typed is the subscript a_{n+1}.
        val math0 = math00.replace(Regex("""_\(([^()]*)\)"""), "_{$1}")
        // {1, 2, 3} ∪ {3, 4}: braces of sets.
        val math = if ('{' in math0 && SET_OPERATIONS.any { it in math0 } && "{{" !in math0.replace(" ", ""))
            math0.replace("{", " \\{ ").replace("}", " \\} ") else math0
        // Lines of a system, typed with ";" between them: x + y = 3; x - y = 1.
        val lines = parts(math, ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size > 1) return "\\begin{cases}" + lines.joinToString("\\\\") { latex(it) } + "\\end{cases}"
        if ("[[" in math.replace(" ", "") || "{{" in math.replace(" ", "")) {
            val withMatrices = matrices(math)
            if (withMatrices != math) return withMatrices.split("\\begin{pmatrix}").let { pieces ->
                // The pieces between matrices are typed math as well: [[1,2],[3,4]]^2, 2[[1,0],[0,1]].
                pieces.mapIndexed { n, piece ->
                    if (n == 0) latexPlain(piece) else {
                        val endAt = piece.indexOf("\\end{pmatrix}") + "\\end{pmatrix}".length
                        "\\begin{pmatrix}" + piece.substring(0, endAt) + latexPlain(piece.substring(endAt))
                    }
                }.joinToString("")
            }
        }
        return latexPlain(math)
    }

    private fun latexPlain(math: String): String {
        // The editor's \left( and \right) are brackets as typed, so floor\left(2.5\right) is floor(2.5), not its letters.
        val s = symbols(math).replace("\\left(", "(").replace("\\right)", ")")
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
                    // \operatorname{arsinh} as the Calculator writes a function: its name kept as it is, never read as words.
                    val name = OPERATOR_NAME.matchAt(s, i)
                    if (name != null) {
                        emit(name.value)
                        i = name.range.last + 1
                        continue
                    }
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
                // A typed comma always separates, in every language: written with its space, which the parser never reads as a
                // decimal comma (a decimal takes a point, 2.5; handwriting keeps its decimal commas).
                c == ',' -> {
                    emit(", ")
                    i++
                    while (i < s.length && s[i] == ' ') i++
                }
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
        // A bracket as typed, or as the editor writes it (\left().
        val after = s.substring(i).trimStart()
        val bracketFollows = after.startsWith("(") || after.startsWith("\\left(")
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
            // Ei(1), Si(x), Ci(x) as written, capital: ei and si are letters.
            word in CAPITAL_FUNCTIONS && bracketFollows -> emit("\\operatorname{$word}")
            // beta(2, 3) is Euler's B, beta alone the Greek letter.
            lower == "beta" && bracketFollows -> emit("beta")
            word == "Gamma" -> emit("\\Gamma ")
            lower in FUNCTION_WORDS -> emit("\\${FUNCTION_WORDS.getValue(lower)}")
            // A command inside a formula (2 det(A)), and a name the calculator does not know (round(2.5)): each fails
            // with its reason, never read as the product of its letters.
            bracketFollows && lower in CommandFunctions.NAMES -> emit("\\operatorname{$lower}")
            bracketFollows && unknownName(lower) -> emit("\\operatorname{$word}")
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
                '×' -> b.append(" \\times ")
                '·', '⋅', '∙' -> b.append('*')
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
                '≡' -> b.append(" \\equiv ")
                '∧' -> b.append(" \\land ")
                '∨' -> b.append(" \\lor ")
                '¬' -> b.append(" \\neg ")
                '⇒' -> b.append(" \\Rightarrow ")
                '⇔', '↔' -> b.append(" \\Leftrightarrow ")
                '⊕' -> b.append(" \\oplus ")
                '∪' -> b.append(" \\cup ")
                '∩' -> b.append(" \\cap ")
                '∖' -> b.append(" \\setminus ")
                '∅' -> b.append(" \\emptyset ")
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

    private val DD = Regex("^d(?:\\^([0-9]+))?\\s*/\\s*d([A-Za-z])(?:\\^([0-9]+))?(?![A-Za-z0-9])\\s*")
    private val ARROW = Regex("->|→|\\\\to")
    private val LEADING_APPROACH = Regex("^\\s*([A-Za-z])\\s*(?:->|→)\\s*(\\S+)\\s+")
    /** A bound written as a number: 0, -1, 2.5, π, pi, ∞. */
    private val NUMBER_LIKE = Regex("^[-+−]?([0-9]+([.,][0-9]+)?|pi|π|∞|inf|infinity)$", RegexOption.IGNORE_CASE)
    private val TRAILING_D = Regex("\\s*\\bd([A-Za-z])\\s*$")
    private val LETTER = Regex("^[A-Za-z](_?\\{?[0-9]+\\}?)?$")
    private val COMMAND_END = Regex("\\\\[a-zA-Z]+$")

    /**
     * A word before a bracket that names no function: three letters or more (round, max, arcsinh), or one of the two-letter
     * names other calculators have (re, im), unless it ends in a function after a letter or two (x sin(x) written xsin(x)).
     */
    private fun unknownName(lower: String): Boolean {
        if (lower.length < 3 && lower !in setOf("re", "im")) return false
        // asinh(1) is no a·sinh(1): the inverse hyperbolic functions under each of their names; beta(2, 3) is Euler's B,
        // which the calculator does not have, not β times a vector.
        if (lower in INVERSE_HYPERBOLIC) return true
        if (lower in Discrete.NAMES || lower in KNOWN_WORDS) return false
        return KNOWN_WORDS.none { lower.endsWith(it) && lower.length - it.length in 1..2 }
    }

    private val INVERSE_HYPERBOLIC = setOf("asinh", "acosh", "atanh", "acoth", "arcsinh", "arccosh", "arctanh", "arccoth")

    /** The functions written with a capital: the exponential, sine and cosine integrals. */
    private val CAPITAL_FUNCTIONS = setOf("Ei", "Si", "Ci")

    private val OPERATOR_NAME = Regex("""\\operatorname\{[A-Za-z]+\}""")

    /** Every word a typed formula knows before a bracket. */
    private val KNOWN_WORDS: Set<String> by lazy {
        FUNCTION_WORDS.keys + WRAPPERS + Discrete.NAMES.keys + CommandFunctions.NAMES + setOf("gamma", "log", "lim") + GREEK_WORDS +
            ValueFunctions.NAMES.keys
    }

    /** Words wrapped around their argument. */
    private val WRAPPERS = setOf("sqrt", "cbrt", "abs", "floor", "ceil")

    /** Function words as the parser's commands: asin is arcsin, productlog is Lambert's W. */
    private val FUNCTION_WORDS = mapOf(
        "sin" to "sin", "cos" to "cos", "tan" to "tan", "cot" to "cot", "sec" to "sec", "csc" to "csc",
        "sinh" to "sinh", "cosh" to "cosh", "tanh" to "tanh",
        "coth" to "coth", "sech" to "sech", "csch" to "csch",
        // The inverse hyperbolic functions by their ISO 80000-2 names, one each.
        "arsinh" to "arsinh", "arcosh" to "arcosh", "artanh" to "artanh", "arcoth" to "arcoth",
        "arcsec" to "arcsec", "arccsc" to "arccsc",
        "bessely" to "bessely", "elliptick" to "elliptick", "elliptice" to "elliptice",
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

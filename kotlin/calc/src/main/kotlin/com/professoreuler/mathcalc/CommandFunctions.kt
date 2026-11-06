package com.professoreuler.mathcalc

/**
 * Every command is a function (owner, 6 November 2026: "rely on the strict english only best practice named functions and just
 * describe the function in each language"): `solve(x^2 = 4, x)`, `derivative(x^2 sin(x), x)`, `integral(x^2, x, 0, 3)`,
 * `stats(12, 15, 11)`. English names in every language, as sin and ln are; one name for one operation, and every part of it
 * stated: the letter, the point, the bounds, the order. [read] reads a call to its [Typed]; [latex] writes the call of a
 * reading (∫ₐᵇ, lim and Σ written as notation too), so the Calculator shows what it worked out as the call it was. A name is a
 * function only before its bracket ("solve" alone is letters). The first argument is the formula; a further argument with a
 * relation (=, <, ≡) is a further line of it (a system, a constraint, an initial value), and the others are the parameters in
 * their order.
 */
object CommandFunctions {
    /** How a function's arguments after the formula are read. */
    enum class Shape { FORMULA, LETTER, BOUNDS, LIMIT, SUM, SERIES, START, INTERVAL, PERIOD, LIST, POINTS, CONVERT }

    /** A function: its [name], the [mode] it chooses (null: a notation the Calculator works out itself, ∫ₐᵇ, lim, Σ). */
    class Function(val name: String, val mode: Mode?, val shape: Shape, val params: List<String> = emptyList())

    val ALL: List<Function> = listOf(
        Function("solve", Mode.SOLVE, Shape.LETTER, listOf("x")),
        Function("factor", Mode.FACTOR, Shape.FORMULA),
        Function("expand", Mode.EXPAND, Shape.FORMULA),
        Function("simplify", Mode.SIMPLIFY, Shape.FORMULA),
        Function("calculate", Mode.CALCULATE, Shape.FORMULA),
        Function("partialfractions", Mode.PARTIAL_FRACTIONS, Shape.FORMULA),
        Function("derivative", Mode.DERIVATIVE, Shape.LETTER, listOf("x")),
        Function("integral", Mode.INTEGRAL, Shape.BOUNDS, listOf("x", "a", "b")),
        Function("limit", null, Shape.LIMIT, listOf("x", "a")),
        Function("sum", null, Shape.SUM, listOf("k", "a", "b")),
        Function("taylor", Mode.SERIES, Shape.SERIES, listOf("x", "a", "n")),
        Function("plot", Mode.GRAPH, Shape.LETTER, listOf("x")),
        Function("det", Mode.DETERMINANT, Shape.FORMULA),
        Function("inverse", Mode.INVERSE, Shape.FORMULA),
        Function("eigenvalues", Mode.EIGEN, Shape.FORMULA),
        Function("rref", Mode.ROW_REDUCE, Shape.FORMULA),
        Function("transpose", Mode.TRANSPOSE, Shape.FORMULA),
        Function("decompose", Mode.DECOMPOSE, Shape.FORMULA),
        Function("expm", Mode.MATRIX_EXP, Shape.FORMULA),
        Function("gradient", Mode.GRADIENT, Shape.FORMULA),
        Function("divergence", Mode.DIVERGENCE, Shape.FORMULA),
        Function("curl", Mode.CURL, Shape.FORMULA),
        Function("jacobian", Mode.JACOBIAN, Shape.FORMULA),
        Function("laplacian", Mode.LAPLACIAN, Shape.FORMULA),
        Function("leastsquares", Mode.LEAST_SQUARES, Shape.FORMULA),
        Function("extrema", Mode.EXTREMA, Shape.FORMULA),
        Function("laplace", Mode.LAPLACE, Shape.FORMULA),
        Function("invlaplace", Mode.INVERSE_LAPLACE, Shape.FORMULA),
        Function("fourier", Mode.FOURIER, Shape.PERIOD, listOf("L")),
        Function("newton", Mode.NEWTON, Shape.START, listOf("x₀")),
        Function("bisection", Mode.BISECTION, Shape.INTERVAL, listOf("a", "b")),
        Function("dsolve", Mode.ODE, Shape.FORMULA),
        Function("euler", Mode.EULER, Shape.FORMULA),
        Function("rungekutta", Mode.RUNGE_KUTTA, Shape.FORMULA),
        Function("trapezoid", Mode.TRAPEZOID, Shape.BOUNDS, listOf("x", "a", "b")),
        Function("simpson", Mode.SIMPSON, Shape.BOUNDS, listOf("x", "a", "b")),
        Function("polar", Mode.POLAR, Shape.FORMULA),
        Function("residues", Mode.RESIDUES, Shape.FORMULA),
        Function("facts", Mode.NUMBER_FACTS, Shape.FORMULA),
        Function("stats", Mode.STATISTICS, Shape.LIST),
        Function("sequence", Mode.SEQUENCE, Shape.LIST),
        Function("regression", Mode.REGRESSION, Shape.POINTS),
        Function("truthtable", Mode.LOGIC, Shape.FORMULA),
        Function("triangle", Mode.TRIANGLE, Shape.FORMULA),
        Function("conic", Mode.CONIC, Shape.FORMULA),
        Function("line", Mode.LINE, Shape.POINTS),
        Function("plane", Mode.PLANE, Shape.POINTS),
        Function("diophantine", Mode.DIOPHANTINE, Shape.FORMULA),
        Function("convert", Mode.UNITS, Shape.CONVERT, listOf("unit")),
    )

    private val BY_NAME = ALL.associateBy { it.name }
    private val BY_MODE = ALL.filter { it.mode != null }.groupBy { it.mode }.mapValues { it.value.first() }

    // ------------------------------------------------------------------------------------------------------------------
    // Reading a call.

    private val CALL = Regex("""^([A-Za-z]+)\s*\((.*)\)$""", RegexOption.DOT_MATCHES_ALL)

    /** A call of one of [ALL] (as typed, or as the editor writes it), read; null otherwise. */
    fun read(text: String, language: String): Typed? {
        val t = plain(text.trim())
        val m = CALL.matchEntire(t) ?: return null
        val f = BY_NAME[m.groupValues[1].lowercase()] ?: return null
        val inner = m.groupValues[2]
        // The bracket after the name closes at the end: solve(x)+(y) is no call.
        if (!balanced(inner)) return null
        val args = arguments(inner, language).map { it.trim() }
        if (args.isEmpty() || args[0].isEmpty()) return null
        if (f.shape == Shape.LIST || f.shape == Shape.POINTS || f.shape == Shape.CONVERT) return special(f, args, language)
        val lines = listOf(args[0]) + args.drop(1).filter { hasRelation(it) }
        val params = args.drop(1).filterNot { hasRelation(it) }
        val formula = lines.joinToString("; ")
        val l = TextInput
        return when (f.shape) {
            Shape.FORMULA -> when (f.mode) {
                Mode.LOGIC -> Typed(l.latex(formula), Choice(Mode.LOGIC))
                Mode.TRIANGLE -> Typed(l.triangleLatex(lines.joinToString(", ")), Choice(Mode.TRIANGLE))
                else -> Typed(l.latex(formula), Choice(f.mode!!))
            }
            Shape.LETTER -> Typed(l.latex(formula), Choice(f.mode!!, params.getOrNull(0)?.let { l.letterOf(it) }))
            Shape.BOUNDS -> {
                // integral(f), integral(f, x), integral(f, a, b), integral(f, x, a, b).
                val letter = params.getOrNull(0)?.let { l.letterOf(it) }
                val bounds = if (letter != null) params.drop(1) else params
                val body = l.latex(formula)
                if (bounds.size >= 2) {
                    val v = letter ?: l.mainLetterOf(body)
                    val integral = "\\int_{${l.latex(bounds[0])}}^{${l.latex(bounds[1])}} $body \\, d$v"
                    Typed(integral, if (f.mode == Mode.INTEGRAL) null else Choice(f.mode!!))
                } else {
                    Typed(body, Choice(f.mode!!, letter))
                }
            }
            Shape.LIMIT -> {
                // limit(f, x, a) or limit(f, a): x → a, from one side with a+ or a-.
                val letter = params.getOrNull(0)?.let { l.letterOf(it) }?.takeIf { params.size >= 2 }
                val point = (if (letter != null) params.getOrNull(1) else params.getOrNull(0)) ?: "0"
                val body = l.latex(formula)
                Typed("\\lim_{${letter ?: l.mainLetterOf(body)} \\to ${l.latex(point)}} $body")
            }
            Shape.SUM -> {
                val body = l.latex(formula)
                val k = params.getOrNull(0)?.let { l.letterOf(it) } ?: "k"
                val lower = params.getOrNull(1) ?: "1"
                val upper = params.getOrNull(2)?.let { l.latex(it) } ?: "\\infty"
                Typed("\\sum_{$k=${l.latex(lower)}}^{$upper} $body")
            }
            Shape.SERIES -> {
                val body = l.latex(formula)
                val letter = params.getOrNull(0)?.let { l.letterOf(it) }
                val rest = if (letter != null) params.drop(1) else params
                Typed(body, Choice(Mode.SERIES, letter ?: l.mainLetterOf(body), point = rest.getOrNull(0)?.let { l.latex(it) } ?: "0",
                    order = rest.getOrNull(1)?.trim()?.toIntOrNull()))
            }
            Shape.START -> Typed(l.latex(formula), Choice(Mode.NEWTON, point = params.getOrNull(0)?.let { l.latex(it) }))
            Shape.INTERVAL -> Typed(l.latex(formula), Choice(Mode.BISECTION,
                point = if (params.size >= 2) l.latex(params[0]) + "," + l.latex(params[1]) else null))
            Shape.PERIOD -> Typed(l.latex(formula), Choice(Mode.FOURIER, point = params.getOrNull(0)?.let { l.latex(it) }))
            else -> null
        }
    }

    private fun special(f: Function, args: List<String>, language: String): Typed? {
        val l = TextInput
        return when (f.shape) {
            // The elements of a list: with decimal commas between semicolons.
            Shape.LIST -> {
                val joined = if (args.any { ',' in it }) args.joinToString("; ") else args.joinToString(", ")
                Typed(l.listLatex(joined), Choice(f.mode!!))
            }
            Shape.POINTS -> Typed(l.pointsLatex(args.joinToString(", "), matrix = f.mode == Mode.REGRESSION), Choice(f.mode!!))
            Shape.CONVERT -> if (args.size == 2) l.conversion(args[0], args[1]) else null
            else -> null
        }
    }

    /** The call as the editor or the display writes it, its LaTeX dressing taken off: \left( is (, \operatorname{solve} is solve. */
    private fun plain(s: String): String = s
        .replace(Regex("""\\operatorname\{([A-Za-z]+)\}"""), "$1")
        .replace(Regex("""\\(?:text|mathrm)\{([^{}]*)\}"""), "$1")
        .replace("\\left(", "(").replace("\\right)", ")")
        .replace("\\ ", " ").replace("\\,", " ")

    private fun balanced(s: String): Boolean {
        var depth = 0
        for (c in s) {
            if (c == '(') depth++
            if (c == ')') {
                depth--
                if (depth < 0) return false
            }
        }
        return depth == 0
    }

    /** Languages that write a decimal comma: there 2,5 between two digits is a number, never two arguments. */
    private val DECIMAL_COMMA = setOf("de", "es", "fr", "it", "nl", "pl", "pt", "tr", "vi", "id", "in", "ru", "uk")

    /**
     * The arguments, split at the semicolons where there are any (stats(1,5; 2,5)), else at the commas; in a language with
     * decimal commas a comma between two digits stays in its number (calculate(2,5*4)).
     */
    private fun arguments(inner: String, language: String = "en"): List<String> {
        val split = if (topLevel(inner, ';')) ';' else ','
        val decimal = split == ',' && language.lowercase().substringBefore('-') in DECIMAL_COMMA
        val out = ArrayList<String>()
        var depth = 0
        val current = StringBuilder()
        for ((k, c) in inner.withIndex()) {
            when (c) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
            }
            val inNumber = decimal && k > 0 && inner[k - 1].isDigit() && k + 1 < inner.length && inner[k + 1].isDigit()
            if (c == split && depth == 0 && !inNumber) {
                out += current.toString()
                current.setLength(0)
            } else {
                current.append(c)
            }
        }
        out += current.toString()
        return out
    }

    private fun topLevel(s: String, c: Char): Boolean {
        var depth = 0
        for (x in s) {
            when (x) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
            }
            if (x == c && depth == 0) return true
        }
        return false
    }

    private fun hasRelation(arg: String): Boolean {
        val a = arg.replace("->", " ").replace("\\to", " ")
        return listOf("=", "<", ">", "≤", "≥", "≠", "≡", "\\le", "\\ge", "\\ne", "\\equiv").any { it in a }
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Writing the call of a reading.

    private val DEFINITE = Regex("""^\\int_\{(.+?)\}\^\{(.+?)\}\s*(.+?)\s*\\,\s*d([A-Za-z])$""", RegexOption.DOT_MATCHES_ALL)
    private val LIM = Regex("""^\\lim_\{([A-Za-z](?:_\{?[0-9]+\}?)?)\s*\\to\s*(.+?)\}\s*(.+)$""", RegexOption.DOT_MATCHES_ALL)
    private val SUM = Regex("""^\\sum_\{([A-Za-z])=(.+?)\}\^\{(.+?)\}\s*(.+)$""", RegexOption.DOT_MATCHES_ALL)

    /**
     * The call of a reading, in LaTeX (`\operatorname{solve}\left(x^{2}=4, x\right)`): the function of its choice with its
     * formula and every parameter written out; null where it has none (a formula on its own, which the Calculator works out
     * as it sees fit).
     */
    fun latex(typed: Typed): String? {
        val c = typed.choice
        val l = typed.latex.trim()
        if (c == null || c.mode == Mode.TRAPEZOID || c.mode == Mode.SIMPSON) {
            DEFINITE.matchEntire(l)?.let { m ->
                val name = when (c?.mode) { Mode.TRAPEZOID -> "trapezoid"; Mode.SIMPSON -> "simpson"; else -> "integral" }
                return call(name, listOf(m.groupValues[3], m.groupValues[4], m.groupValues[1], m.groupValues[2]))
            }
        }
        if (c == null) {
            LIM.matchEntire(l)?.let { m -> return call("limit", listOf(m.groupValues[3], m.groupValues[1], m.groupValues[2])) }
            SUM.matchEntire(l)?.let { m ->
                val upper = m.groupValues[3]
                return call("sum", listOf(m.groupValues[4], m.groupValues[1], m.groupValues[2]) + upper)
            }
            return null
        }
        val f = BY_MODE[c.mode] ?: return null
        val lines = cases(l)
        val one = lines.size == 1
        val args: List<String> = when (f.shape) {
            Shape.FORMULA -> lines
            Shape.LETTER -> lines + listOfNotNull(c.letter ?: if (one && f.mode != Mode.GRAPH) TextInput.mainLetterOf(l) else null)
            Shape.BOUNDS -> lines + listOfNotNull(c.letter ?: TextInput.mainLetterOf(l))
            Shape.SERIES -> lines + listOfNotNull(c.letter ?: TextInput.mainLetterOf(l), c.point ?: "0", c.order?.toString())
            Shape.START -> lines + listOfNotNull(c.point)
            Shape.INTERVAL -> lines + (c.point?.split(',') ?: emptyList())
            Shape.PERIOD -> lines + listOfNotNull(c.point)
            Shape.LIST -> elements(l) ?: return null
            Shape.POINTS -> points(l) ?: return null
            Shape.CONVERT -> listOf(l + "\\ \\text{" + (c.letter ?: return null) + "}", "\\text{" + (c.point ?: return null) + "}")
            else -> return null
        }
        return call(f.name, args)
    }

    private fun call(name: String, args: List<String>): String {
        // Semicolons between the arguments where one of them holds a comma of its own (a decimal comma, a list's elements).
        val separator = if (args.any { topLevel(it, ',') }) "; " else ", "
        return "\\operatorname{$name}\\left(" + args.joinToString(separator) + "\\right)"
    }

    /** The lines of a system the reader made (\begin{cases}…\end{cases}), or the one line. */
    private fun cases(l: String): List<String> {
        val m = Regex("""^\\begin\{cases\}(.*)\\end\{cases\}$""", RegexOption.DOT_MATCHES_ALL).matchEntire(l) ?: return listOf(l)
        return m.groupValues[1].split("\\\\").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** A list's elements from its vector, [1, 2, 3] or \left[1, 2, 3\right]. */
    private fun elements(l: String): List<String>? {
        val inner = l.removePrefix("\\left").let { if (it.startsWith("[") && it.endsWith("]")) it.substring(1, it.length - 1) else return null }
            .removeSuffix("\\right")
        return arguments(inner).map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Points from a matrix (one to a row: a regression's) or from lines of vectors (a line's, a plane's). */
    private fun points(l: String): List<String>? {
        val matrix = Regex("""^\\begin\{pmatrix\}(.*)\\end\{pmatrix\}$""", RegexOption.DOT_MATCHES_ALL).matchEntire(l)
        if (matrix != null) return matrix.groupValues[1].split("\\\\").map { it.trim() }.filter { it.isNotEmpty() }
            .map { "(" + it.split('&').joinToString(", ") { c -> c.trim() } + ")" }
        val lines = cases(l)
        return lines.map { line ->
            val v = line.removePrefix("\\left").trim()
            if (v.startsWith("[") || v.startsWith("(")) "(" + v.substring(1).removeSuffix("]").removeSuffix(")").removeSuffix("\\right").trim() + ")" else v
        }
    }
}

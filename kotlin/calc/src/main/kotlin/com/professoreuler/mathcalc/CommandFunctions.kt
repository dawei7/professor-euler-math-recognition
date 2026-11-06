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
    enum class Shape { FORMULA, LETTER, ORDER, BOUNDS, LIMIT, SUM, SERIES, START, INTERVAL, PERIOD, LIST, POINTS, CONVERT, REGION,
        /** odeseries(f), odeseries(f, a), odeseries(f, a, n): a point (a number) and the highest power. */
        POINT_ORDER,
        /** dormandprince(f), dormandprince(f, b): where the method ends. */
        UNTIL,
        /** Read and written by [Applied]: a curve's point or bounds, two curves, a matrix with its letter. */
        APPLIED,
        /** dijkstra((1, 2, 4), (2, 3, 1), 1, 3): a graph's edges, then the vertices (or the length) its function asks for ([Networks]). */
        NETWORK,
        /** Every argument as typed, read by the function itself ([Learning.command]): gradientdescent(f, x₀, η, n). */
        CALL,
        /** A whole number (or its numeral) and a base: tobase(255, 16), frombase(FF, 16) ([Bases]). */
        BASE,
    }

    /** A function: its [name], the [mode] it chooses (null: a notation the Calculator works out itself, ∫ₐᵇ, lim, Σ). */
    class Function(val name: String, val mode: Mode?, val shape: Shape, val params: List<String> = emptyList())

    val ALL: List<Function> = listOf(
        Function("solve", Mode.SOLVE, Shape.LETTER, listOf("x")),
        Function("factor", Mode.FACTOR, Shape.FORMULA),
        Function("expand", Mode.EXPAND, Shape.FORMULA),
        Function("simplify", Mode.SIMPLIFY, Shape.FORMULA),
        Function("calculate", Mode.CALCULATE, Shape.FORMULA),
        Function("partialfractions", Mode.PARTIAL_FRACTIONS, Shape.FORMULA),
        Function("derivative", Mode.DERIVATIVE, Shape.ORDER, listOf("x", "n")),
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
        Function("complexplot", Mode.COMPLEX_PLOT, Shape.REGION, listOf("z", "c", "r")),
        Function("venn", Mode.VENN, Shape.FORMULA),
        Function("svd", Mode.SVD, Shape.FORMULA),
        Function("jordan", Mode.JORDAN, Shape.FORMULA),
        Function("odeseries", Mode.ODE_SERIES, Shape.POINT_ORDER, listOf("a", "n")),
        Function("dormandprince", Mode.DORMAND_PRINCE, Shape.UNTIL, listOf("b")),
        Function("slopefield", Mode.SLOPE_FIELD, Shape.FORMULA),
        Function("phaseportrait", Mode.PHASE_PORTRAIT, Shape.FORMULA),
        Function("interpolate", Mode.INTERPOLATION, Shape.POINTS),
        Function("spline", Mode.SPLINE, Shape.POINTS),
        Function("pdetype", Mode.PDE_TYPE, Shape.FORMULA),
        Function("pdsolve", Mode.PDE, Shape.FORMULA),
        Function("rank", Mode.RANK, Shape.FORMULA),
        Function("trace", Mode.TRACE, Shape.FORMULA),
        Function("nullspace", Mode.NULL_SPACE, Shape.FORMULA),
        Function("columnspace", Mode.COLUMN_SPACE, Shape.FORMULA),
        Function("charpoly", Mode.CHARACTERISTIC_POLYNOMIAL, Shape.APPLIED, listOf("λ")),
        Function("adjugate", Mode.ADJUGATE, Shape.FORMULA),
        Function("tangentline", Mode.TANGENT_LINE, Shape.APPLIED, listOf("x", "a")),
        Function("normalline", Mode.NORMAL_LINE, Shape.APPLIED, listOf("x", "a")),
        Function("arclength", Mode.ARC_LENGTH, Shape.APPLIED, listOf("x", "a", "b")),
        Function("areabetween", Mode.AREA_BETWEEN, Shape.APPLIED, listOf("g", "x", "a", "b")),
        Function("volumex", Mode.VOLUME_X, Shape.APPLIED, listOf("x", "a", "b")),
        Function("volumey", Mode.VOLUME_Y, Shape.APPLIED, listOf("x", "a", "b")),
        Function("meanvalue", Mode.MEAN_VALUE, Shape.APPLIED, listOf("x", "a", "b")),
        Function("maximize", Mode.MAXIMIZE, Shape.FORMULA),
        Function("minimize", Mode.MINIMIZE, Shape.FORMULA),
        Function("graph", Mode.GRAPH_FACTS, Shape.NETWORK),
        Function("dijkstra", Mode.DIJKSTRA, Shape.NETWORK, listOf("s", "t")),
        Function("bellmanford", Mode.BELLMAN_FORD, Shape.NETWORK, listOf("s", "t")),
        Function("kruskal", Mode.KRUSKAL, Shape.NETWORK),
        Function("prim", Mode.PRIM, Shape.NETWORK, listOf("s")),
        Function("toposort", Mode.TOPOLOGICAL_ORDER, Shape.NETWORK),
        Function("maxflow", Mode.MAX_FLOW, Shape.NETWORK, listOf("s", "t")),
        Function("walks", Mode.WALKS, Shape.NETWORK, listOf("k", "u", "v")),
        Function("gradientdescent", Mode.GRADIENT_DESCENT, Shape.CALL, listOf("x₀", "η", "n")),
        Function("valueiteration", Mode.VALUE_ITERATION, Shape.CALL, listOf("P₁", "R", "γ")),
        Function("policyevaluation", Mode.POLICY_EVALUATION, Shape.CALL, listOf("P₁", "R", "γ", "π")),
        Function("amortization", Mode.AMORTIZATION, Shape.LIST),
        Function("equalprincipal", Mode.EQUAL_PRINCIPAL, Shape.LIST),
        Function("straightline", Mode.STRAIGHT_LINE, Shape.LIST),
        Function("decliningbalance", Mode.DECLINING_BALANCE, Shape.LIST),
        Function("irr", Mode.IRR, Shape.LIST),
        Function("tobase", Mode.TO_BASE, Shape.BASE, listOf("b")),
        Function("frombase", Mode.FROM_BASE, Shape.BASE, listOf("b")),
    )

    /** The names of [ALL]: a call of one that cannot be read fails ([Failure.ARGUMENTS]), never a product of its letters. */
    val NAMES: Set<String> = ALL.map { it.name }.toSet()

    private val BY_NAME = ALL.associateBy { it.name }
    private val BY_MODE = ALL.filter { it.mode != null }.groupBy { it.mode }.mapValues { it.value.first() }

    // ------------------------------------------------------------------------------------------------------------------
    // Reading a call.

    private val CALL = Regex("""^([A-Za-z]+)\s*\((.*)\)$""", RegexOption.DOT_MATCHES_ALL)

    /**
     * A call of one of [ALL] (as typed, or as the editor writes it), read; null where the text is no call of one. A call with
     * arguments its function does not take is read as itself, which fails with [Failure.ARGUMENTS]: never guessed, never
     * with an argument left out.
     */
    fun read(text: String): Typed? {
        val t = plain(text.trim())
        val m = CALL.matchEntire(t) ?: return null
        val f = BY_NAME[m.groupValues[1].lowercase()] ?: return null
        val inner = m.groupValues[2]
        // The bracket after the name closes at the end: solve(x)+(y) is no call.
        if (!balanced(inner)) return null
        val args = arguments(inner)?.map { it.trim() } ?: return refused(f, inner)
        if (args.isEmpty() || args.any { it.isEmpty() }) return refused(f, inner)
        if (f.shape == Shape.BASE) return Bases.read(f.mode!!, args) ?: refused(f, inner)
        if (f.shape == Shape.LIST || f.shape == Shape.POINTS || f.shape == Shape.CONVERT) return special(f, args) ?: refused(f, inner)
        if (f.shape == Shape.APPLIED) return Applied.read(f, args) ?: refused(f, inner)
        if (f.shape == Shape.NETWORK) return Networks.typed(f.name, args) ?: refused(f, inner)
        if (f.shape == Shape.CALL) return Typed(call(f.name, args.map { TextInput.latex(it) }), Choice(f.mode!!))
        val lines = listOf(args[0]) + args.drop(1).filter { hasRelation(it) }
        val params = args.drop(1).filterNot { hasRelation(it) }
        // Lines after the formula only where the function takes them (a system, initial values, constraints): gradient(f, x = 1)
        // is no gradient at a point, and never anything else.
        if (lines.size > 1 && f.name in ONE_LINE) return refused(f, inner)
        val formula = lines.joinToString("; ")
        val l = TextInput
        fun letter(k: Int): String? = params.getOrNull(k)?.let { l.letterOf(it) }
        return when (f.shape) {
            Shape.FORMULA -> when {
                params.isNotEmpty() -> refused(f, inner)
                f.mode == Mode.LOGIC -> Typed(l.latex(formula), Choice(Mode.LOGIC))
                f.mode == Mode.TRIANGLE -> Typed(l.triangleLatex(lines.joinToString(", ")), Choice(Mode.TRIANGLE))
                else -> Typed(l.latex(formula), Choice(f.mode!!))
            }
            // solve(f), solve(f, x): the letter, if any, is a letter.
            Shape.LETTER -> if (params.size > 1 || (params.size == 1 && letter(0) == null)) refused(f, inner)
                else Typed(l.latex(formula), Choice(f.mode!!, letter(0)))
            // derivative(f), derivative(f, x), derivative(f, x, n): the nth derivative written as d^n/dx^n, which the
            // calculator works out itself.
            Shape.ORDER -> {
                if (params.size > 2 || (params.isNotEmpty() && letter(0) == null)) return refused(f, inner)
                val n = params.getOrNull(1)?.let { it.toIntOrNull()?.takeIf { k -> k in 1..MAX_ORDER } ?: return refused(f, inner) } ?: 1
                if (n == 1) return Typed(l.latex(formula), Choice(f.mode!!, letter(0)))
                val body = l.latex(formula)
                val x = letter(0) ?: l.mainLetterOf(body)
                Typed("\\frac{d^{$n}}{d$x^{$n}}\\left($body\\right)")
            }
            Shape.BOUNDS -> {
                // integral(f), integral(f, x), integral(f, a, b), integral(f, x, a, b): told apart by their number.
                val v = when (params.size) {
                    1, 3 -> letter(0) ?: return refused(f, inner)
                    0, 2 -> null
                    else -> return refused(f, inner)
                }
                val bounds = if (params.size >= 2) params.takeLast(2) else emptyList()
                // The trapezoidal and Simpson's rules approximate a definite integral: without its bounds there is none.
                if (f.mode != Mode.INTEGRAL && bounds.isEmpty()) return refused(f, inner)
                val body = l.latex(formula)
                if (bounds.size == 2) {
                    val x = v ?: l.mainLetterOf(body)
                    // integral(f, x, 3): the letter is no bound of its own integral.
                    if (bounds.any { it == x }) return refused(f, inner)
                    val integral = "\\int_{${l.latex(bounds[0])}}^{${l.latex(bounds[1])}} $body \\, d$x"
                    Typed(integral, if (f.mode == Mode.INTEGRAL) null else Choice(f.mode!!))
                } else {
                    Typed(body, Choice(f.mode!!, v))
                }
            }
            Shape.LIMIT -> {
                // limit(f, a) or limit(f, x, a): x → a, from one side with a+ or a-. The point is always stated.
                val point = when (params.size) {
                    1 -> params[0]
                    2 -> params[1].takeIf { letter(0) != null } ?: return refused(f, inner)
                    else -> return refused(f, inner)
                }
                val body = l.latex(formula)
                // The whole formula in brackets: written as notation, lim takes only the term after it (lim x² − x is 9 − x).
                Typed("\\lim_{${letter(0)?.takeIf { params.size == 2 } ?: l.mainLetterOf(body)} \\to ${l.latex(point)}} ${bracketed(body)}")
            }
            Shape.SUM -> {
                // sum(f), sum(f, k), sum(f, k, a), sum(f, k, a, b): from 1 to ∞ where they are not given.
                if (params.size > 3 || (params.isNotEmpty() && letter(0) == null)) return refused(f, inner)
                val body = l.latex(formula)
                val k = letter(0) ?: "k"
                val lower = params.getOrNull(1) ?: "1"
                val upper = params.getOrNull(2)?.let { l.latex(it) } ?: "\\infty"
                Typed("\\sum_{$k=${l.latex(lower)}}^{$upper} ${bracketed(body)}")
            }
            Shape.SERIES -> {
                // taylor(f), taylor(f, x), taylor(f, a), taylor(f, a, n), taylor(f, x, a), taylor(f, x, a, n).
                val v = letter(0)
                val rest = if (v != null) params.drop(1) else params
                if (rest.size > 2) return refused(f, inner)
                val order = rest.getOrNull(1)?.let { it.toIntOrNull()?.takeIf { n -> n in 1..MAX_ORDER } ?: return refused(f, inner) }
                val body = l.latex(formula)
                Typed(body, Choice(Mode.SERIES, v ?: l.mainLetterOf(body), point = rest.getOrNull(0)?.let { l.latex(it) } ?: "0",
                    order = order))
            }
            Shape.START -> if (params.size > 1) refused(f, inner)
                else Typed(l.latex(formula), Choice(Mode.NEWTON, point = params.getOrNull(0)?.let { l.latex(it) }))
            Shape.INTERVAL -> if (params.size != 0 && params.size != 2) refused(f, inner)
                else Typed(l.latex(formula), Choice(Mode.BISECTION,
                    point = if (params.size == 2) l.latex(params[0]) + "," + l.latex(params[1]) else null))
            Shape.PERIOD -> if (params.size > 1) refused(f, inner)
                else Typed(l.latex(formula), Choice(Mode.FOURIER, point = params.getOrNull(0)?.let { l.latex(it) }))
            Shape.REGION -> {
                // complexplot(f), complexplot(f, z), complexplot(f, c, r), complexplot(f, z, c, r): told apart by their number;
                // the square centred at c with half side r.
                val v = when (params.size) {
                    1, 3 -> letter(0) ?: return refused(f, inner)
                    0, 2 -> null
                    else -> return refused(f, inner)
                }
                val body = l.latex(formula)
                val z = v ?: complexLetter(body)
                val square = if (params.size >= 2) params.takeLast(2) else emptyList()
                // complexplot(f, z, z, 2): the letter is no centre or size of its own square.
                if (square.any { l.letterOf(it) == z }) return refused(f, inner)
                Typed(body, Choice(Mode.COMPLEX_PLOT, z, point = if (square.size == 2) square.joinToString(",") { l.latex(it) } else null))
            }
            // odeseries(f, a, n): the point a number (never a letter), the order a whole number.
            Shape.POINT_ORDER -> {
                if (params.size > 2 || (params.isNotEmpty() && letter(0) != null)) return refused(f, inner)
                val order = params.getOrNull(1)?.let { it.toIntOrNull()?.takeIf { n -> n in 1..MAX_ORDER } ?: return refused(f, inner) }
                Typed(l.latex(formula), Choice(f.mode!!, point = params.getOrNull(0)?.let { l.latex(it) }, order = order))
            }
            // dormandprince(f, b): the end b a number.
            Shape.UNTIL -> if (params.size > 1 || (params.size == 1 && letter(0) != null)) refused(f, inner)
                else Typed(l.latex(formula), Choice(f.mode!!, point = params.getOrNull(0)?.let { l.latex(it) }))
            else -> refused(f, inner)
        }
    }

    /**
     * The functions of one formula, which take no further line (an equation, an initial value) after it; the others take them
     * as a system, initial values, constraints or a triangle's values (solve, dsolve, extrema, triangle …).
     */
    private val ONE_LINE = setOf("factor", "expand", "simplify", "calculate", "partialfractions", "derivative", "integral", "limit", "sum",
        "taylor", "det", "inverse", "eigenvalues", "rref", "transpose", "decompose", "expm", "gradient", "divergence", "curl", "jacobian",
        "laplacian", "laplace", "invlaplace", "fourier", "newton", "bisection", "trapezoid", "simpson", "polar", "residues", "facts",
        "truthtable", "conic", "diophantine", "svd", "jordan", "complexplot", "venn")

    /**
     * [body] in brackets, as the argument of an operator written as notation (Σ, lim), which takes only the term after it: a
     * function's whole first argument is its argument, always (sum(k + 1, k, 1, 3) is Σ (k + 1) = 9, never Σ k + 1).
     */
    private fun bracketed(body: String): String = "\\left($body\\right)"

    /** [s] without the brackets [bracketed] put around it (one pair around the whole, not \left(a\right) + \left(b\right)). */
    private fun unbracketed(s: String): String {
        val t = s.trim()
        if (!t.startsWith("\\left(") || !t.endsWith("\\right)")) return t
        val inner = t.substring(6, t.length - 7)
        var depth = 0
        var k = 0
        while (k < inner.length) {
            when {
                inner.startsWith("\\left", k) -> { depth++; k += 5 }
                inner.startsWith("\\right", k) -> { depth--; if (depth < 0) return t; k += 6 }
                else -> k++
            }
        }
        return if (depth == 0) inner else t
    }

    /** The letter of a complex function: z where it has one, else its only letter, else z. */
    private fun complexLetter(body: String): String {
        val main = TextInput.mainLetterOf(body)
        return if (Regex("""(?<![A-Za-z\\])z(?![A-Za-z])""").containsMatchIn(body) || main == "x" && !Regex("""(?<![A-Za-z\\])x(?![A-Za-z])""").containsMatchIn(body)) "z" else main
    }

    /** The highest order of a series a call may ask for. */
    private const val MAX_ORDER = 24

    /** A call that cannot be read, as itself: working it out fails with [Failure.ARGUMENTS]. */
    private fun refused(f: Function, inner: String): Typed =
        Typed("\\operatorname{${f.name}}\\left(" + (runCatching { TextInput.latex(inner) }.getOrNull() ?: "") + "\\right)")

    private fun special(f: Function, args: List<String>): Typed? {
        val l = TextInput
        return when (f.shape) {
            // The elements of a list: two or more, or one list in brackets ([1, 2, 3]); a single number is no list.
            Shape.LIST -> if (args.size == 1 && args[0].trim().firstOrNull() !in LIST_OPENERS) null
                else Typed(l.listLatex(args.joinToString(", ")), Choice(f.mode!!))
            Shape.POINTS -> Typed(l.pointsLatex(args.joinToString(", "), matrix = f.mode in MATRIX_POINTS), Choice(f.mode!!))
            Shape.CONVERT -> if (args.size == 2) l.conversion(args[0], args[1]) else null
            else -> null
        }
    }

    /** The functions of points whose points are the rows of one matrix (a line's and a plane's are vectors, one to a line). */
    private val MATRIX_POINTS = setOf(Mode.REGRESSION, Mode.INTERPOLATION, Mode.SPLINE)

    /** What a list given as one argument opens with: stats([1, 2, 3]), stats({1, 2, 3}). */
    private val LIST_OPENERS = setOf('(', '[', '{')

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

    /**
     * The arguments, split at the commas, in every language: a decimal number takes a point, 2.5. Null where the call holds a
     * semicolon (no other separator is read) or where its commas look like decimal commas, some between two digits and some before
     * a space (stats(1,5, 2,5)).
     */
    private fun arguments(inner: String): List<String>? {
        if (topLevel(inner, ';')) return null
        val out = ArrayList<String>()
        var depth = 0
        var tight = false
        var spaced = false
        val current = StringBuilder()
        for ((k, c) in inner.withIndex()) {
            when (c) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
            }
            if (c == ',' && depth == 0) {
                if (k > 0 && inner[k - 1].isDigit() && k + 1 < inner.length && inner[k + 1].isDigit()) tight = true
                if (k + 1 < inner.length && inner[k + 1].isWhitespace()) spaced = true
                out += current.toString()
                current.setLength(0)
            } else {
                current.append(c)
            }
        }
        out += current.toString()
        return if (tight && spaced) null else out
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
    private val NTH = Regex("""^\\frac\{d\^\{([0-9]+)\}\}\{d([A-Za-z])\^\{\1\}\}\s*\\left\((.*)\\right\)$""", RegexOption.DOT_MATCHES_ALL)

    /**
     * The call of a reading, in LaTeX (`\operatorname{solve}\left(x^{2}=4, x\right)`): the function of its choice with its
     * formula and every parameter written out; null where it has none (a formula on its own, which the Calculator works out
     * as it sees fit).
     */
    fun latex(typed: Typed): String? {
        val c = typed.choice
        val l = typed.latex.trim()
        Applied.latex(typed)?.let { return it }
        if (c == null || c.mode == Mode.TRAPEZOID || c.mode == Mode.SIMPSON) {
            DEFINITE.matchEntire(l)?.let { m ->
                val name = when (c?.mode) { Mode.TRAPEZOID -> "trapezoid"; Mode.SIMPSON -> "simpson"; else -> "integral" }
                return call(name, listOf(m.groupValues[3], m.groupValues[4], m.groupValues[1], m.groupValues[2]))
            }
        }
        if (c == null) {
            LIM.matchEntire(l)?.let { m -> return call("limit", listOf(unbracketed(m.groupValues[3]), m.groupValues[1], m.groupValues[2])) }
            NTH.matchEntire(l)?.let { m -> return call("derivative", listOf(m.groupValues[3], m.groupValues[2], m.groupValues[1])) }
            SUM.matchEntire(l)?.let { m ->
                val upper = m.groupValues[3]
                return call("sum", listOf(unbracketed(m.groupValues[4]), m.groupValues[1], m.groupValues[2]) + upper)
            }
            return null
        }
        val f = BY_MODE[c.mode] ?: return null
        // A call its function reads itself is written as it is.
        if (f.shape == Shape.CALL) return l.takeIf { it.startsWith("\\operatorname{${f.name}}") }
        val lines = cases(l)
        val one = lines.size == 1
        val args: List<String> = when (f.shape) {
            Shape.FORMULA -> lines
            Shape.LETTER -> lines + listOfNotNull(c.letter ?: if (one && f.mode != Mode.GRAPH) TextInput.mainLetterOf(l) else null)
            Shape.ORDER -> lines + listOfNotNull(c.letter ?: TextInput.mainLetterOf(l))
            Shape.BOUNDS -> lines + listOfNotNull(c.letter ?: TextInput.mainLetterOf(l))
            Shape.SERIES -> lines + listOfNotNull(c.letter ?: TextInput.mainLetterOf(l), c.point ?: "0", c.order?.toString())
            Shape.START -> lines + listOfNotNull(c.point)
            Shape.INTERVAL -> lines + (c.point?.split(',') ?: emptyList())
            Shape.PERIOD -> lines + listOfNotNull(c.point)
            Shape.REGION -> lines + listOfNotNull(c.letter ?: complexLetter(l)) + (c.point?.split(',') ?: emptyList())
            Shape.LIST -> elements(l) ?: return null
            Shape.POINTS -> points(l) ?: return null
            Shape.CONVERT -> listOf(l + "\\ \\text{" + (c.letter ?: return null) + "}", "\\text{" + (c.point ?: return null) + "}")
            Shape.POINT_ORDER -> lines + listOfNotNull(c.point, c.order?.takeIf { c.point != null }?.toString())
            Shape.UNTIL -> lines + listOfNotNull(c.point)
            // A graph's call is its own LaTeX ([Networks]).
            Shape.NETWORK -> return l.takeIf { Networks.callOf(it)?.first == f.name }
            Shape.BASE -> Bases.arguments(c, l) ?: return null
            else -> return null
        }
        return call(f.name, args)
    }

    private fun call(name: String, args: List<String>): String = "\\operatorname{$name}\\left(" + args.joinToString(", ") + "\\right)"

    /** The lines of a system the reader made (\begin{cases}…\end{cases}), or the one line. */
    private fun cases(l: String): List<String> {
        val m = Regex("""^\\begin\{cases\}(.*)\\end\{cases\}$""", RegexOption.DOT_MATCHES_ALL).matchEntire(l) ?: return listOf(l)
        return m.groupValues[1].split("\\\\").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** A list's elements from its vector, [1, 2, 3] or \left[1, 2, 3\right]. */
    private fun elements(l: String): List<String>? {
        val inner = l.removePrefix("\\left").let { if (it.startsWith("[") && it.endsWith("]")) it.substring(1, it.length - 1) else return null }
            .removeSuffix("\\right")
        return arguments(inner)?.map { it.trim() }?.filter { it.isNotEmpty() }
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

package com.professoreuler.mathcalc

import java.math.BigInteger

/** The relation between two sides of a formula. */
enum class Rel(val latex: String, val text: String) {
    EQ("=", "="), LT("<", "<"), LE("\\le", "≤"), GT(">", ">"), GE("\\ge", "≥"), NE("\\ne", "≠");

    /** The same relation with its sides swapped: a < b is b > a. */
    val flipped: Rel get() = when (this) { LT -> GT; LE -> GE; GT -> LT; GE -> LE; else -> this }

    val isInequality: Boolean get() = this == LT || this == LE || this == GT || this == GE
}

/**
 * One line of a formula: its sides between relation signs (null where a side is empty, as after a closing `=`) and the
 * relations between them; [texts] each side as written. [definition] where the line defines a function, `f(x) = …`: its
 * first side is then the function's name.
 */
class Statement internal constructor(val sides: List<Expr?>, val relations: List<Rel>, internal val texts: List<String>,
    val definition: Definition?) {
    /** A function the line defines: [name] of [variable]. */
    class Definition(val name: String, val variable: String)
}

/** What a formula's style was: a decimal comma, decimals, fractions, degrees, ±. Shared by the parsers of one formula. */
internal class Flags {
    var decimalComma = false
    var decimals = false
    var fractions = false
    var degrees = false
    var plusMinus = false
}

/**
 * Reads the recognizer's LaTeX tokens into [Expr]s. Times binds tighter than plus, an implicit product (`2x`, `3(x-2)`) as
 * tight as `\cdot`, powers tighter still; `!`, `\%` and a prime apply to what stands before them. Function names come as
 * letters (`s i n`) or commands (`\sin`); a function takes a bracket, or else the product of numbers and letters after it
 * (`\sin 2x` is sin(2x)), and `\sin^{-1}` is arcsin. `\frac{d}{dx}`, `\int … dx`, `lim_{x \rightarrow a}`, `\sum` and
 * `\prod` become [Expr.Op]s; `\begin{cases}` and `\\` separate the lines of a system; `f(x) = …` defines f. With ±, the
 * formula is read once with + ([pmSign] 1) and once with −.
 */
internal class Parser(tokens: List<String>, private val flags: Flags = Flags(), private val pmSign: Int = 1,
    private val bound: Set<String> = emptySet()) {
    private val t: List<String> = clean(tokens)
    private var i = 0
    private var absDepth = 0

    val decimalComma get() = flags.decimalComma
    val decimals get() = flags.decimals
    val fractions get() = flags.fractions
    val degrees get() = flags.degrees
    val plusMinus get() = flags.plusMinus

    /** The lines of the formula: one, or the rows of `\begin{cases}`, or lines separated by `\\`. */
    fun statements(): List<Statement> {
        val out = mutableListOf<Statement>()
        if (peek() == "\\{") i++
        if (peek() == "\\begin") {
            i++
            groupTokens()
            if (peek() == "{") groupTokens() // an array's column specification
            while (i < t.size && peek() != "\\end") {
                out += statement(stopAt = setOf("\\\\", "\\end"))
                if (peek() == "\\\\") i++
            }
            if (peek() == "\\end") { i++; groupTokens() }
            if (i != t.size) throw unreadable()
            return out.ifEmpty { throw unreadable() }
        }
        while (true) {
            out += statement(stopAt = setOf("\\\\"))
            if (i == t.size) return out
            expect("\\\\")
            if (i == t.size) return out
        }
    }

    private fun statement(stopAt: Set<String>): Statement {
        val sides = mutableListOf<Expr?>()
        val relations = mutableListOf<Rel>()
        val texts = mutableListOf<String>()
        var definition: Statement.Definition? = null
        // f(x) = …: a function's definition.
        if (peek() in FUNCTION_LETTERS && peek(1) == "(" && isLetter(peek(2)) && peek(3) == ")" && relationAt(4) == Rel.EQ) {
            definition = Statement.Definition(peek()!!, peek(2)!!)
            texts += t.subList(i, i + 4).joinToString("")
            sides += Expr.Sym(definition.name)
            i += 5
            relations += Rel.EQ
        }
        while (true) {
            val start = i
            val atEnd = i == t.size || peek() in stopAt || relationAt(0) != null || peek() == "&"
            sides += if (atEnd) null else expression()
            texts += t.subList(start, i).joinToString("")
            if (peek() == "&") i++
            if (i == t.size || peek() in stopAt) return Statement(sides, relations, texts, definition)
            val rel = relationAt(0) ?: throw unreadable()
            i += relationLength(0)
            if (peek() == "&") i++
            relations += rel
        }
    }

    private fun relationAt(k: Int): Rel? = when (peek(k)) {
        "=", "\\approx" -> Rel.EQ
        "<" -> if (peek(k + 1) == "=") Rel.LE else Rel.LT
        ">" -> if (peek(k + 1) == "=") Rel.GE else Rel.GT
        "\\le", "\\leq", "\\leqslant" -> Rel.LE
        "\\ge", "\\geq", "\\geqslant" -> Rel.GE
        "\\ne", "\\neq" -> Rel.NE
        else -> null
    }

    private fun relationLength(k: Int): Int = if ((peek(k) == "<" || peek(k) == ">") && peek(k + 1) == "=") 2 else 1

    // Expressions.

    fun expression(): Expr {
        var left = when (peek()) {
            "-" -> { i++; neg(term()) }
            "+" -> { i++; term() }
            "\\pm" -> { i++; flags.plusMinus = true; mul(num(pmSign.toLong()), term()) }
            "\\mp" -> { i++; flags.plusMinus = true; mul(num(-pmSign.toLong()), term()) }
            else -> term()
        }
        while (true) {
            left = when (peek()) {
                "+" -> { i++; add(left, term()) }
                "-" -> { i++; add(left, neg(term())) }
                "\\pm" -> { i++; flags.plusMinus = true; add(left, mul(num(pmSign.toLong()), term())) }
                "\\mp" -> { i++; flags.plusMinus = true; add(left, mul(num(-pmSign.toLong()), term())) }
                else -> return left
            }
        }
    }

    private fun term(): Expr {
        var left = factor()
        while (true) {
            val op = peek() ?: return left
            left = when {
                op in TIMES -> { i++; mul(left, factor()) }
                op in DIVIDE -> { i++; divide(left, factor()) }
                startsFactor(op) -> mul(left, factor())
                else -> return left
            }
        }
    }

    private fun divide(a: Expr, b: Expr): Expr {
        if (isZero(b)) throw CalcException(Failure.DIVISION_BY_ZERO)
        return mul(a, pow(b, Expr.MINUS_ONE))
    }

    private fun startsFactor(op: String) = isDigit(op) || isLetter(op) || op == "(" || op == "[" || op == "\\frac" ||
        op == "\\sqrt" || op == "\\pi" || op == "\\infty" || op in FUNCTION_COMMANDS || op in GREEK || op == "\\int" ||
        op == "\\sum" || op == "\\prod" || op == "\\lim" || op == "\\lfloor" || op == "\\lceil" || (op == "|" && absDepth == 0)

    private fun factor(): Expr {
        if (peek() == "-") {
            i++
            return neg(factor())
        }
        var node = power()
        while (true) {
            node = when (peek()) {
                "!" -> { i++; fn(Func.FACTORIAL, node) }
                "\\%" -> { i++; mul(node, num(Rational.ONE / Rational.of(100))) }
                "'", "\\prime" -> { i++; derivativeOf(node) }
                else -> return node
            }
        }
    }

    /** (…)′: the derivative in the expression's one letter (x where it has several). */
    private fun derivativeOf(e: Expr): Expr {
        val letters = e.symbols()
        val v = when {
            letters.size == 1 -> letters.first()
            "x" in letters -> "x"
            letters.isNotEmpty() -> letters.first()
            else -> "x"
        }
        return op(OpKind.DERIVATIVE, e, Expr.Sym(v), Expr.ONE)
    }

    private fun power(): Expr {
        val base = atom()
        if (peek() != "^") return base
        i++
        // Degrees: 30^{\circ}.
        if (peek() == "\\circ" || (peek() == "{" && peek(1) == "\\circ" && peek(2) == "}")) {
            i += if (peek() == "\\circ") 1 else 3
            flags.degrees = true
            return mul(base, Expr.PI, num(Rational.ONE / Rational.of(180)))
        }
        // A prime written as a power: f^{\prime}, f^{\prime\prime}.
        if (peek() == "\\prime" || (peek() == "{" && peek(1) == "\\prime")) {
            var primes = 0
            if (peek() == "{") {
                i++
                while (peek() == "\\prime") { i++; primes++ }
                expect("}")
            } else { i++; primes = 1 }
            var e = base
            repeat(primes) { e = derivativeOf(e) }
            return e
        }
        return pow(base, group())
    }

    /** A braced expression, or the single digit, letter or constant LaTeX takes without braces (`x^2`). */
    private fun group(): Expr {
        val tok = peek() ?: throw unreadable()
        return when {
            tok == "{" -> braced()
            tok == "-" -> { i++; neg(group()) }
            isDigit(tok) -> { i++; num(tok.toLong()) }
            isLetter(tok) -> { i++; letter(tok) }
            tok == "\\pi" -> { i++; Expr.PI }
            tok == "\\infty" -> { i++; Expr.INF }
            else -> throw unreadable()
        }
    }

    private fun braced(): Expr {
        expect("{")
        val e = expression()
        expect("}")
        return e
    }

    private fun bracket(open: String, close: String): Expr {
        expect(open)
        val e = expression()
        expect(close)
        return e
    }

    private fun atom(): Expr {
        val tok = peek() ?: throw unreadable()
        return when {
            isDigit(tok) -> number()
            (tok == "." || tok == ",") && isDigit(peek(1)) -> number()
            tok == "(" -> bracket("(", ")")
            tok == "[" -> bracket("[", "]")
            tok == "{" -> braced()
            tok == "|" || tok == "\\|" -> {
                i++
                absDepth++
                val e = expression()
                expect(tok)
                absDepth--
                fn(Func.ABS, e)
            }
            tok == "\\lfloor" -> { i++; val e = expression(); expect("\\rfloor"); fn(Func.FLOOR, e) }
            tok == "\\lceil" -> { i++; val e = expression(); expect("\\rceil"); fn(Func.CEIL, e) }
            tok == "\\frac" -> fraction()
            tok == "\\sqrt" -> {
                i++
                val index = if (peek() == "[") bracket("[", "]") else Expr.TWO
                val radicand = group()
                if (index == Expr.TWO) sqrt(radicand) else pow(radicand, pow(index, Expr.MINUS_ONE))
            }
            tok == "\\pi" -> { i++; Expr.PI }
            tok == "\\infty" -> { i++; Expr.INF }
            tok in GREEK -> { i++; Expr.Sym(tok) }
            tok in FUNCTION_COMMANDS -> { i++; call(tok.drop(1)) }
            tok == "\\int" -> integral()
            tok == "\\sum" || tok == "\\prod" -> sumOrProduct(tok == "\\sum")
            tok == "\\lim" -> { i++; limit() }
            isLetter(tok) -> letters()
            else -> throw unreadable()
        }
    }

    private fun number(): Expr {
        val whole = StringBuilder()
        while (isDigit(peek())) whole.append(t[i++])
        val separator = peek()
        if ((separator == "." || separator == ",") && isDigit(peek(1))) {
            i++
            val fraction = StringBuilder()
            while (isDigit(peek())) fraction.append(t[i++])
            flags.decimals = true
            if (separator == ",") flags.decimalComma = true
            return num(Rational.decimal(whole.ifEmpty { "0" }.toString(), fraction.toString()))
        }
        return num(Rational.of(BigInteger(whole.toString())))
    }

    // \frac: a fraction, or d/dx.

    private fun fraction(): Expr {
        i++
        val top = groupTokens()
        val bottom = groupTokens()
        derivativeOperator(top, bottom)?.let { (variable, order) ->
            // A bracket with its power is the body: d/dx (2x + 1)^5; otherwise the product that follows.
            val body = if (peek() == "(") factor() else term()
            return op(OpKind.DERIVATIVE, body, Expr.Sym(variable), num(order.toLong()))
        }
        flags.fractions = true
        return divide(parseAll(top), parseAll(bottom))
    }

    /** `d` over `dx` (or ∂ over ∂x, d² over dx²): the variable and the order; otherwise null. */
    private fun derivativeOperator(top: List<String>, bottom: List<String>): Pair<String, Int>? {
        val d = setOf("d", "\\partial")
        if (top.isEmpty() || top[0] !in d || bottom.size < 2 || bottom[0] !in d || !isLetter(bottom[1])) return null
        val order = when {
            top.size == 1 -> 1
            top.size == 3 && top[1] == "^" && isDigit(top[2]) -> top[2].toInt()
            top.size == 5 && top[1] == "^" && top[2] == "{" && isDigit(top[3]) && top[4] == "}" -> top[3].toInt()
            else -> return null
        }
        val rest = bottom.drop(2)
        val ok = rest.isEmpty() || rest == listOf("^", order.toString()) || rest == listOf("^", "{", order.toString(), "}")
        return if (ok) bottom[1] to order else null
    }

    // Integrals, sums, products and limits.

    private fun integral(): Expr {
        i++
        var lower: Expr? = null
        var upper: Expr? = null
        repeat(2) {
            when (peek()) {
                "_" -> { i++; lower = bound() }
                "^" -> { i++; upper = bound() }
            }
        }
        // The body runs to its differential, d and a letter, before the side ends.
        val start = i
        var depth = 0
        var differential = -1
        var k = i
        while (k < t.size) {
            val s = t[k]
            if (s == "(" || s == "{" || s == "[") depth++
            if (s == ")" || s == "}" || s == "]") { if (depth == 0) break; depth-- }
            if (depth == 0 && (relationAt(k - i) != null || s == "\\\\" || s == "&")) break
            if (depth == 0 && s == "d" && k + 1 < t.size && isLetter(t[k + 1]) && (k + 2 == t.size || !isLetter(t[k + 2]))) differential = k
            k++
        }
        val end = if (differential >= 0) differential else k
        if (end <= start) throw unreadable()
        val variable = if (differential >= 0) t[differential + 1] else null
        val body = Parser(t.subList(start, end), flags, pmSign, bound + listOfNotNull(variable)).let { p ->
            p.expression().also { if (!p.done) throw unreadable() }
        }
        i = if (differential >= 0) differential + 2 else end
        val v = variable ?: body.symbols().singleOrNull() ?: "x"
        if ((lower == null) != (upper == null)) throw unreadable()
        return op(OpKind.INTEGRAL, body, Expr.Sym(v), lower, upper)
    }

    /** A bound of an integral or a sum: a braced expression, −∞, or one token. */
    private fun bound(): Expr = when {
        peek() == "{" -> parseAll(groupTokens())
        peek() == "-" && peek(1) == "\\infty" -> { i += 2; neg(Expr.INF) }
        else -> group()
    }

    private fun sumOrProduct(sum: Boolean): Expr {
        i++
        var variable: String? = null
        var lower: Expr? = null
        var upper: Expr? = null
        repeat(2) {
            when (peek()) {
                "_" -> {
                    i++
                    val tokens = groupTokens()
                    if (tokens.size < 3 || tokens[1] != "=" || !isLetter(tokens[0])) throw unreadable()
                    variable = tokens[0]
                    lower = parseAll(tokens.drop(2))
                }
                "^" -> { i++; upper = bound() }
            }
        }
        val v = variable ?: throw unreadable()
        val bodyParser = Parser(t.subList(i, t.size), flags, pmSign, bound + v)
        val body = bodyParser.term()
        i += bodyParser.i
        return op(if (sum) OpKind.SUM else OpKind.PRODUCT, body, Expr.Sym(v), lower, upper ?: throw unreadable())
    }

    /** lim_{x \rightarrow a} …, one side as a + or − after a, or as its power. */
    private fun limit(): Expr {
        expect("_")
        val tokens = groupTokens()
        val arrows = setOf("\\rightarrow", "\\to", "\\longrightarrow", "\\mapsto")
        var arrowEnd = when {
            tokens.size > 1 && tokens[1] in arrows -> 2
            tokens.size > 2 && tokens[1] == "-" && tokens[2] == ">" -> 3
            else -> throw unreadable()
        }
        if (!isLetter(tokens[0])) throw unreadable()
        var pointTokens = tokens.drop(arrowEnd)
        var side: Expr? = null
        fun sideOf(s: String?) = when (s) { "+" -> Expr.ONE; "-" -> Expr.MINUS_ONE; else -> null }
        val n = pointTokens.size
        when {
            n >= 4 && pointTokens[n - 4] == "^" && pointTokens[n - 3] == "{" && pointTokens[n - 1] == "}" && sideOf(pointTokens[n - 2]) != null -> {
                side = sideOf(pointTokens[n - 2]); pointTokens = pointTokens.dropLast(4)
            }
            n >= 2 && pointTokens[n - 2] == "^" && sideOf(pointTokens[n - 1]) != null -> {
                side = sideOf(pointTokens[n - 1]); pointTokens = pointTokens.dropLast(2)
            }
            n >= 2 && sideOf(pointTokens[n - 1]) != null -> {
                side = sideOf(pointTokens[n - 1]); pointTokens = pointTokens.dropLast(1)
            }
        }
        val point = when (pointTokens) {
            listOf("-", "\\infty") -> neg(Expr.INF)
            listOf("+", "\\infty"), listOf("\\infty") -> Expr.INF
            else -> parseAll(pointTokens)
        }
        val body = term()
        arrowEnd = 0
        return op(OpKind.LIMIT, body, Expr.Sym(tokens[0]), point, side)
    }

    // Letters, names and functions.

    /** A function written in letters, lim, or else one letter: e, i, or a letter (with its subscript, as `x_{1}`). */
    private fun letters(): Expr {
        val run = lettersFrom(i)
        if (run.startsWith("lim") && peek(3) == "_") {
            i += 3
            return limit()
        }
        val name = FUNCTIONS.firstOrNull { run.startsWith(it) }
        if (name != null) {
            i += name.length
            return call(name)
        }
        return letter(t[i++])
    }

    private fun letter(tok: String): Expr {
        if (peek() != "_") {
            if (tok == "e" && tok !in bound) return Expr.E
            if (tok == "i" && tok !in bound) return Expr.I
            return Expr.Sym(tok)
        }
        i++
        val sub = groupTokens()
        // x_{1} and x_1 are the same unknown.
        return Expr.Sym("${tok}_${sub.joinToString("")}")
    }

    private fun call(name: String): Expr {
        val base = if (name == "log" && peek() == "_") { i++; group() } else null
        // sin^2 x is (sin x)²; sin^{-1} x is arcsin x.
        var power: Expr? = null
        if (peek() == "^") {
            i++
            power = group()
        }
        val arg = argument()
        val inverse = power == Expr.MINUS_ONE && name in INVERSES
        val f = when (name) {
            "sin" -> if (inverse) Func.ASIN else Func.SIN
            "cos" -> if (inverse) Func.ACOS else Func.COS
            "tan" -> if (inverse) Func.ATAN else Func.TAN
            "cot" -> if (inverse) Func.ACOT else Func.COT
            "sec" -> Func.SEC
            "csc" -> Func.CSC
            "arcsin" -> Func.ASIN
            "arccos" -> Func.ACOS
            "arctan" -> Func.ATAN
            "sinh" -> Func.SINH
            "cosh" -> Func.COSH
            "tanh" -> Func.TANH
            "ln" -> Func.LN
            "lg", "log" -> Func.LOG
            "exp" -> null
            else -> throw unreadable()
        }
        val value = when {
            f == null -> exp(arg)
            f == Func.LOG -> fn(Func.LOG, base ?: num(10), arg)
            else -> fn(f, arg)
        }
        return if (power != null && !inverse) pow(value, power) else value
    }

    /** A function's argument: a bracket, or else the numbers, letters and powers right after its name. */
    private fun argument(): Expr {
        if (peek() == "(") return bracket("(", ")")
        if (peek() == "{") return braced()
        var arg = power()
        while (true) {
            val next = peek() ?: return arg
            val run = lettersFrom(i)
            val simple = isDigit(next) || next == "\\pi" || next in GREEK ||
                (isLetter(next) && FUNCTIONS.none { run.startsWith(it) } && !run.startsWith("lim"))
            if (!simple) return arg
            arg = mul(arg, power())
        }
    }

    private fun lettersFrom(k: Int): String {
        val run = StringBuilder()
        while (run.length < 6 && isLetter(t.getOrNull(k + run.length))) run.append(t[k + run.length])
        return run.toString()
    }

    // Tokens.

    private fun peek(k: Int = 0) = t.getOrNull(i + k)

    private val done get() = i == t.size

    private fun expect(s: String) {
        if (peek() != s) throw unreadable()
        i++
    }

    /** The tokens of a braced group (without its braces), or the single token where there are none. */
    private fun groupTokens(): List<String> {
        if (peek() != "{") return listOf(t.getOrNull(i++) ?: throw unreadable())
        val start = i + 1
        var depth = 0
        while (i < t.size) {
            if (t[i] == "{") depth++
            if (t[i] == "}") {
                depth--
                if (depth == 0) {
                    i++
                    return t.subList(start, i - 1)
                }
            }
            i++
        }
        throw unreadable()
    }

    /** A group's tokens read as one whole expression, in this formula's style. */
    private fun parseAll(tokens: List<String>): Expr {
        val p = Parser(tokens, flags, pmSign, bound)
        val e = p.expression()
        if (!p.done) throw unreadable()
        return e
    }

    private fun unreadable() = CalcException(Failure.UNREADABLE)

    private fun isDigit(s: String?) = s != null && s.length == 1 && s[0] in '0'..'9'
    private fun isLetter(s: String?) = s != null && s.length == 1 && (s[0] in 'a'..'z' || s[0] in 'A'..'Z')

    companion object {
        private val TIMES = setOf("\\cdot", "\\times", "*", "\\ast", "\\bullet")
        private val DIVIDE = setOf(":", "\\div", "/")
        /** Longest first, so `arcsin` is not read as `a r c sin` and `sinh` not as `sin h`. */
        private val FUNCTIONS = listOf("arcsin", "arccos", "arctan", "sinh", "cosh", "tanh", "sin", "cos", "tan", "cot", "sec", "csc",
            "exp", "log", "ln", "lg")
        private val INVERSES = setOf("sin", "cos", "tan", "cot")
        private val FUNCTION_COMMANDS = FUNCTIONS.map { "\\$it" }.toSet()
        /** Letters a learner names functions with: f(x) = … defines f. */
        private val FUNCTION_LETTERS = setOf("f", "g", "h", "F", "G", "H")
        private val GREEK = setOf("\\alpha", "\\beta", "\\gamma", "\\delta", "\\epsilon", "\\varepsilon", "\\zeta", "\\eta", "\\theta",
            "\\vartheta", "\\iota", "\\kappa", "\\lambda", "\\mu", "\\nu", "\\xi", "\\rho", "\\sigma", "\\tau", "\\upsilon", "\\phi",
            "\\varphi", "\\chi", "\\psi", "\\omega", "\\Delta", "\\Omega", "\\Phi", "\\Psi", "\\Theta", "\\Lambda", "\\Sigma", "\\Gamma")
        /** Spacing and sizing that change nothing: \left( is (, \, is nothing. */
        private val IGNORED = setOf("\\left", "\\,", "\\;", "\\!", "\\:", "\\ ", "\\displaystyle", "\\limits", "\\big", "\\Big",
            "\\bigl", "\\bigr", "\\Bigl", "\\Bigr", "\\quad", "\\qquad")

        /** The tokens without spacing and sizing; `\right.` (an invisible closing bracket) goes whole. */
        private fun clean(tokens: List<String>): List<String> {
            val out = ArrayList<String>(tokens.size)
            var k = 0
            while (k < tokens.size) {
                val s = tokens[k]
                when {
                    s == "\\right" -> if (tokens.getOrNull(k + 1) == ".") k++
                    s !in IGNORED -> out += s
                }
                k++
            }
            return out
        }
    }
}

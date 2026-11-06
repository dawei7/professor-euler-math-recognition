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

/**
 * How a formula is read where it can be read two ways, as Wolfram Alpha's "Assuming …": each is a reading of its own, met while
 * the formula is read ([Ambiguity]) and changed on the Calculator's chips. The defaults are the usual readings.
 */
data class Assumptions(
    /** Angles of sin, cos, tan … written without ° (or π) are in degrees, as a calculator's DEG mode: sin 30 is 1/2, arcsin ½ is 30. */
    val degrees: Boolean = false,
    /** log without a base is the natural logarithm (as in much of university mathematics), not the logarithm to base 10. */
    val naturalLog: Boolean = false,
    /** A function's power written without brackets, sin x², is (sin x)², not sin(x²). */
    val powerOfFunction: Boolean = false,
    /** e is a letter, not Euler's number. */
    val eLetter: Boolean = false,
    /** i is a letter, not the imaginary unit. */
    val iLetter: Boolean = false,
) {
    /** The other reading of [a]. */
    fun flipped(a: Ambiguity): Assumptions = when (a) {
        Ambiguity.ANGLES -> copy(degrees = !degrees)
        Ambiguity.LOG -> copy(naturalLog = !naturalLog)
        Ambiguity.FUNCTION_POWER -> copy(powerOfFunction = !powerOfFunction)
        Ambiguity.E -> copy(eLetter = !eLetter)
        Ambiguity.I -> copy(iLetter = !iLetter)
    }

    /** Which reading of [a] these assumptions take: true for the one that is not the usual. */
    fun takes(a: Ambiguity): Boolean = when (a) {
        Ambiguity.ANGLES -> degrees
        Ambiguity.LOG -> naturalLog
        Ambiguity.FUNCTION_POWER -> powerOfFunction
        Ambiguity.E -> eLetter
        Ambiguity.I -> iLetter
    }
}

/** What a formula holds that can be read two ways ([Assumptions]). */
enum class Ambiguity {
    /** An angle without ° or π, as sin 30 (radians or degrees); or an inverse function's angle, as arcsin ½. */
    ANGLES,

    /** log without a base (to base 10 or e). */
    LOG,

    /** A function of a power without brackets, as sin x² (sin(x²) or (sin x)²). */
    FUNCTION_POWER,

    /** e on its own, not as the base of a power (Euler's number or a letter). */
    E,

    /** i (the imaginary unit or a letter). */
    I,
}

/** What a formula's style was: a decimal comma, decimals, fractions, degrees, ±. Shared by the parsers of one formula. */
internal class Flags(val assumptions: Assumptions = Assumptions()) {
    var decimalComma = false
    var decimals = false
    var fractions = false
    var degrees = false
    var plusMinus = false

    /** How many angles were marked with °: an argument that has one is in degrees whatever is assumed. */
    var degreeMarks = 0

    /** The variable of dy/dx (or dy/dt) where the formula wrote one: a differential equation's independent variable. */
    var odeVariable: String? = null

    /** The readings the formula met that could have gone two ways, in the order met. */
    val met = LinkedHashSet<Ambiguity>()

    /** The modulus of each line written as a congruence (x ≡ 2 (mod 3), x mod 3 = 2), by the line's index. */
    val moduli = HashMap<Int, BigInteger>()
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
    /** Where a comma had a space after it: a separator, never a decimal comma. */
    private val spaced = HashSet<Int>()
    private val t: List<String> = clean(tokens, spaced)
    private var i = 0
    private var absDepth = 0

    /** The index of the line being read, and whether it was written with ≡. */
    private var line = -1
    private var congruence = false

    val decimalComma get() = flags.decimalComma
    val decimals get() = flags.decimals
    val fractions get() = flags.fractions
    val degrees get() = flags.degrees
    val plusMinus get() = flags.plusMinus

    /** The lines of the formula: one, or the rows of `\begin{cases}`, or lines separated by `\\`. */
    fun statements(): List<Statement> {
        val out = mutableListOf<Statement>()
        if (peek() == "\\{") i++
        if (peek() == "\\begin" && environmentAt(i) !in MATRICES) {
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
        line++
        congruence = false
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
            modulus()
            if (peek() == "&") i++
            if (i == t.size || peek() in stopAt) return Statement(sides, relations, texts, definition)
            val rel = relationAt(0) ?: throw unreadable()
            if (peek() == "\\equiv" || peek() == "≡") congruence = true
            i += relationLength(0)
            if (peek() == "&") i++
            relations += rel
        }
    }

    private fun relationAt(k: Int): Rel? = when (peek(k)) {
        "=", "\\approx", "\\equiv", "≡" -> Rel.EQ
        "<" -> if (peek(k + 1) == "=") Rel.LE else Rel.LT
        ">" -> if (peek(k + 1) == "=") Rel.GE else Rel.GT
        "\\le", "\\leq", "\\leqslant" -> Rel.LE
        "\\ge", "\\geq", "\\geqslant" -> Rel.GE
        "\\ne", "\\neq" -> Rel.NE
        else -> null
    }

    /** How many tokens a mod at [k] takes: \bmod, \mod, or the letters m o d on their own. */
    private fun modAt(k: Int = 0): Int = when {
        peek(k) == "\\bmod" || peek(k) == "\\mod" -> 1
        peek(k) == "m" && peek(k + 1) == "o" && peek(k + 2) == "d" && !isLetter(peek(k + 3)) -> 3
        else -> 0
    }

    /** A modulus after a side: \pmod{7}, (\mod 7), or mod 7 in a congruence, kept for the line. */
    private fun modulus() {
        val m = when {
            peek() == "\\pmod" -> { i++; group() }
            peek() == "(" && modAt(1) > 0 -> {
                i += 1 + modAt(1)
                val e = expression()
                expect(")")
                e
            }
            congruence && modAt() > 0 -> { i += modAt(); factor() }
            else -> return
        }
        val n = integer(m)?.abs()?.takeIf { it > BigInteger.ONE } ?: throw CalcException(Failure.UNDEFINED)
        flags.moduli[line] = n
    }

    private fun relationLength(k: Int): Int = if ((peek(k) == "<" || peek(k) == ">") && peek(k + 1) == "=") 2 else 1

    // Expressions.

    fun expression(): Expr {
        var left = when (peek()) {
            "-" -> { i++; negative(term()) }
            "+" -> { i++; term() }
            "\\pm" -> { i++; flags.plusMinus = true; mul(num(pmSign.toLong()), term()) }
            "\\mp" -> { i++; flags.plusMinus = true; mul(num(-pmSign.toLong()), term()) }
            else -> term()
        }
        while (true) {
            left = when (peek()) {
                "+" -> { i++; sum(left, term()) }
                "-" -> { i++; sum(left, negative(term())) }
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
            // (\mod 7) after a side is its line's modulus, and so is mod after ≡.
            if (op == "(" && modAt(1) > 0) return left
            val mod = modAt()
            if (mod > 0) {
                if (congruence) return left
                i += mod
                val m = factor()
                val a = integer(left)
                val n = integer(m)?.abs()
                if (n == null || n.signum() == 0) throw CalcException(Failure.UNDEFINED)
                if (a != null) {
                    left = num(Rational.of(a.mod(n)))
                    continue
                }
                // x mod 3 = 2: the line is a congruence modulo 3.
                if (n > BigInteger.ONE && flags.moduli[line] == null) {
                    flags.moduli[line] = n
                    continue
                }
                throw unreadable()
            }
            left = when {
                op in TIMES -> { i++; product(left, factor(), op) }
                op in DIVIDE -> { i++; divide(left, factor()) }
                startsFactor(op) -> product(left, factor(), null)
                else -> return left
            }
        }
    }

    /** a + b, of matrices by [LinAlg]: a matrix is worked out where it is written. */
    private fun sum(a: Expr, b: Expr): Expr = if (a is Expr.Mat || b is Expr.Mat) LinAlg.plus(a, b) else add(a, b)

    private fun negative(a: Expr): Expr = if (a is Expr.Mat) LinAlg.scale(a, Expr.MINUS_ONE) else neg(a)

    /** a·b in the order written: × of two vectors of three is their cross product, · their dot product. */
    private fun product(a: Expr, b: Expr, op: String?): Expr = when {
        a is Expr.Mat && b is Expr.Mat && op == "\\times" && LinAlg.isVector(a) && LinAlg.isVector(b) -> LinAlg.cross(a, b)
        a is Expr.Mat || b is Expr.Mat -> LinAlg.times(a, b)
        else -> mul(a, b)
    }

    private fun divide(a: Expr, b: Expr): Expr {
        if (b is Expr.Mat) throw CalcException(Failure.SIZES)
        if (a is Expr.Mat) return LinAlg.scale(a, pow(b, Expr.MINUS_ONE))
        if (isZero(b)) throw CalcException(Failure.DIVISION_BY_ZERO)
        return mul(a, pow(b, Expr.MINUS_ONE))
    }

    private fun startsFactor(op: String) = isDigit(op) || isLetter(op) || op == "(" || op == "[" || op == "\\frac" ||
        op == "\\begin" || op == "\\det" ||
        op == "\\sqrt" || op == "\\pi" || op == "\\infty" || op in FUNCTION_COMMANDS || op in GREEK || op == "\\int" ||
        op == "\\sum" || op == "\\prod" || op == "\\lim" || op == "\\lfloor" || op == "\\lceil" || (op == "|" && absDepth == 0)

    private fun factor(): Expr {
        if (peek() == "-") {
            i++
            return negative(factor())
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
        // y′, y″ of the unknown function of a differential equation: markers, worked out by [Ode].
        if (e is Expr.Sym && e.name.trimEnd('\'') in ODE_UNKNOWNS) return Expr.Sym(e.name + "'")
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
            flags.degreeMarks++
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
        // e^A of a matrix.
        if (base == Expr.E) {
            val save = i
            val exponent = group()
            if (exponent is Expr.Mat) return LinAlg.exp(exponent) ?: throw CalcException(Failure.UNSOLVED)
            i = save
        }
        // A matrix: Aᵀ, A⁻¹, Aⁿ.
        if (base is Expr.Mat) {
            if (peek() == "T" || peek() == "\\top" || (peek() == "{" && (peek(1) == "T" || peek(1) == "\\top") && peek(2) == "}")) {
                i += if (peek() == "{") 3 else 1
                return LinAlg.transpose(base)
            }
            val n = integer(group())?.takeIf { it.bitLength() < 31 } ?: throw unreadable()
            return LinAlg.power(base, n.toInt())
        }
        return pow(base, group())
    }

    /** The name of the environment a \\begin at [k] opens: pmatrix, cases. */
    private fun environmentAt(k: Int): String {
        if (t.getOrNull(k) != "\\begin" || t.getOrNull(k + 1) != "{") return ""
        val out = StringBuilder()
        var j = k + 2
        while (j < t.size && t[j] != "}") out.append(t[j++])
        return out.toString()
    }

    /** \\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix} (bmatrix, matrix, Bmatrix too; vmatrix is the determinant). */
    private fun matrix(): Expr {
        val kind = environmentAt(i)
        i++
        groupTokens()
        val rows = ArrayList<List<Expr>>()
        var row = ArrayList<Expr>()
        while (true) {
            if (peek() == "\\end") break
            if (peek() == null) throw unreadable()
            row += expression()
            when (peek()) {
                "&" -> i++
                "\\\\" -> { i++; rows += row; row = ArrayList() }
                "\\end" -> {}
                else -> throw unreadable()
            }
        }
        if (row.isNotEmpty()) rows += row
        i++
        groupTokens()
        val m = LinAlg.matrix(rows)
        return if (kind == "vmatrix") LinAlg.det(m) else m
    }

    /** Inside a vector's brackets a comma separates its entries: never a decimal comma there. */
    private var listDepth = 0

    /**
     * Whether the bracket at the cursor is a vector: [1, 2] with any comma; (1, 2, 3) with two or more, or one that is not
     * between two digits ((x, y)). (1,5) alone keeps its decimal comma, as a learner who writes decimal commas means it.
     */
    private fun listAhead(open: String, close: String): Boolean {
        var depth = 0
        var commas = 0
        var notBetweenDigits = false
        var k = i
        while (k < t.size) {
            val s = t[k]
            if (s == "(" || s == "[" || s == "{") depth++
            if (s == ")" || s == "]" || s == "}") {
                depth--
                if (depth == 0) break
            }
            if (depth == 1 && s == ",") {
                commas++
                if (!isDigit(t.getOrNull(k - 1)) || !isDigit(t.getOrNull(k + 1)) || k in spaced) notBetweenDigits = true
            }
            k++
        }
        if (commas == 0) return false
        return open == "[" || commas >= 2 || notBetweenDigits
    }

    /**
     * After the first entry of (…) or […]: a comma makes it a vector, (1, 2, 3); vectors of one length in it are the rows of a
     * matrix, [[1, 2], [3, 4]] (as the Calculator's keys write it).
     */
    private fun vectorRest(first: Expr, close: String): Expr {
        val entries = arrayListOf(first)
        while (peek() == ",") { i++; entries += expression() }
        expect(close)
        if (entries.all { it is Expr.Mat && LinAlg.isVector(it) && it.size == (entries[0] as Expr.Mat).size }) {
            return LinAlg.matrix(entries.map { v -> (v as Expr.Mat).rows.map { it[0] } })
        }
        if (entries.any { it is Expr.Mat }) throw CalcException(Failure.SIZES)
        return LinAlg.column(entries)
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
            tok == "(" || tok == "[" -> {
                val close = if (tok == "(") ")" else "]"
                val list = listAhead(tok, close)
                i++
                if (list) listDepth++
                try {
                    val e = expression()
                    if (list && peek() == ",") vectorRest(e, close) else { expect(close); e }
                } finally {
                    if (list) listDepth--
                }
            }
            tok == "\\begin" && environmentAt(i) in MATRICES -> matrix()
            // det A, \\det(A): the determinant of the matrix after it.
            tok == "\\det" -> { i++; val m = factor(); if (m is Expr.Mat) LinAlg.det(m) else throw unreadable() }
            tok == "{" -> braced()
            tok == "|" || tok == "\\|" -> {
                i++
                absDepth++
                val e = expression()
                expect(tok)
                absDepth--
                // |v| of a vector is its length, |A| of a square matrix its determinant.
                when {
                    e is Expr.Mat && LinAlg.isVector(e) -> LinAlg.norm(e)
                    e is Expr.Mat -> LinAlg.det(e)
                    else -> fn(Func.ABS, e)
                }
            }
            tok == "\\lfloor" -> { i++; val e = expression(); expect("\\rfloor"); fn(Func.FLOOR, e) }
            tok == "\\lceil" -> { i++; val e = expression(); expect("\\rceil"); fn(Func.CEIL, e) }
            tok == "\\frac" -> fraction()
            tok == "\\gcd" -> { i++; numberFunction("gcd") ?: throw unreadable() }
            tok == "\\binom" || tok == "\\dbinom" || tok == "\\tbinom" -> {
                i++
                val n = group()
                val k = group()
                if (n.symbols().isEmpty() && k.symbols().isEmpty()) Discrete.binomial(n, k)
                else mul(fn(Func.FACTORIAL, n), pow(mul(fn(Func.FACTORIAL, k), fn(Func.FACTORIAL, add(n, neg(k)))), Expr.MINUS_ONE))
            }
            tok in NUMBER_GREEK && peek(1) == "(" -> { i++; numberFunction(NUMBER_GREEK.getValue(tok)) ?: Expr.Sym(tok) }
            tok == "\\sqrt" -> {
                i++
                val index = if (peek() == "[") bracket("[", "]") else Expr.TWO
                val radicand = group()
                if (index == Expr.TWO) sqrt(radicand) else pow(radicand, pow(index, Expr.MINUS_ONE))
            }
            tok == "\\pi" -> { i++; Expr.PI }
            tok == "\\infty" -> { i++; Expr.INF }
            // Γ(x), ζ(s) and ψ(x) with a bracket are the functions; alone, the letters.
            tok in GREEK_FUNCTIONS && peek(1) == "(" -> { i++; call(tok.drop(1)) }
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
        if ((separator == "." || (separator == "," && listDepth == 0 && i !in spaced)) && isDigit(peek(1))) {
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
        // dy/dx, d²y/dx²: the derivatives of a differential equation's unknown.
        odeDerivative(top, bottom)?.let { return it }
        derivativeOperator(top, bottom)?.let { (variable, order) ->
            // A bracket with its power is the body: d/dx (2x + 1)^5; otherwise the product that follows.
            val body = if (peek() == "(") factor() else term()
            return op(OpKind.DERIVATIVE, body, Expr.Sym(variable), num(order.toLong()))
        }
        flags.fractions = true
        return divide(parseAll(top), parseAll(bottom))
    }

    /** dy/dx and d²y/dx² (dy/dt …) of y or u: its marker y′, y″; the variable is kept in [Flags.odeVariable]. */
    private fun odeDerivative(top: List<String>, bottom: List<String>): Expr? {
        if (top.isEmpty() || top[0] != "d" || bottom.size < 2 || bottom[0] != "d" || !isLetter(bottom[1])) return null
        val (order, name) = when {
            top.size == 2 -> 1 to top[1]
            top.size == 4 && top[1] == "^" && isDigit(top[2]) -> top[2].toInt() to top[3]
            top.size == 6 && top[1] == "^" && top[2] == "{" && isDigit(top[3]) && top[4] == "}" -> top[3].toInt() to top[5]
            else -> return null
        }
        if (name !in ODE_UNKNOWNS || order !in 1..4) return null
        val rest = bottom.drop(2)
        if (!(rest.isEmpty() || rest == listOf("^", order.toString()) || rest == listOf("^", "{", order.toString(), "}"))) return null
        flags.odeVariable = bottom[1]
        return Expr.Sym(name + "'".repeat(order))
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
        numberFunction()?.let { return it }
        physicalConstant()?.let { return it }
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

    /**
     * gcd(12, 18), binom(5, 2), mean(1, 2, 3), binompdf(10, 0.5, 3): a number function of numbers, worked out here ([Discrete]);
     * null where the letters are no such name or its arguments are no numbers (mu(x + 1) is then m·u·(x + 1)).
     */
    private fun numberFunction(name0: String? = null): Expr? {
        val word = name0 ?: buildString { while (length < 12 && isLetter(t.getOrNull(i + length))) append(t[i + length]) }
        var name = word.lowercase()
        var length = if (name0 != null) 0 else word.length
        if (name == "stirling" && peek(length) == "1") { name = "stirling1"; length++ }
        if (name !in Discrete.NAMES || peek(length) != "(") return null
        val save = i
        i += length
        val args = arguments()
        if (args == null) { i = save; return null }
        return Discrete.call(name, args) ?: run { i = save; null }
    }

    /**
     * constant(speedoflight), constant(speedoflight, km/h): a physical constant's value ([PhysicalConstants.inFormula]); null
     * where the letters are no such call. Its name is letters; a unit after a comma is read as [Units] reads it.
     */
    private fun physicalConstant(): Expr? {
        val word = PhysicalConstants.CALL
        if (word.indices.any { t.getOrNull(i + it) != word[it].toString() } || peek(word.length) != "(") return null
        var k = i + word.length + 1
        val name = StringBuilder()
        while (isLetter(t.getOrNull(k))) name.append(t[k++])
        var unit: String? = null
        if (t.getOrNull(k) == ",") {
            val start = ++k
            var depth = 0
            while (k < t.size && !(depth == 0 && t[k] == ")")) {
                if (t[k] == "(") depth++
                if (t[k] == ")") depth--
                k++
            }
            unit = PhysicalConstants.unitText(t.subList(start, minOf(k, t.size)))
        }
        if (name.isEmpty() || t.getOrNull(k) != ")" || unit?.isEmpty() == true || unit?.contains(",") == true) throw CalcException(Failure.ARGUMENTS)
        i = k + 1
        return PhysicalConstants.inFormula(name.toString(), unit)
    }

    /** (a, b, c) of a function: separated by commas, and by nothing else (a semicolon there fails: [Failure.ARGUMENTS]). */
    private fun arguments(): List<Expr>? {
        if (peek() != "(") return null
        var depth = 0
        var semicolons = false
        for (k in i until t.size) {
            if (t[k] == "(" || t[k] == "[" || t[k] == "{") depth++
            if (t[k] == ")" || t[k] == "]" || t[k] == "}") depth--
            if (depth == 1 && t[k] == ";") semicolons = true
            if (depth == 0) break
        }
        if (semicolons) throw CalcException(Failure.ARGUMENTS)
        i++
        listDepth++
        try {
            val out = ArrayList<Expr>()
            while (true) {
                out += Calculus.evaluate(expression())
                when {
                    peek() == "," -> i++
                    peek() == ")" -> { i++; return out }
                    else -> return null
                }
            }
        } catch (e: CalcException) {
            if (e.failure == Failure.TOO_LONG) throw e
            return null
        } finally {
            listDepth--
        }
    }

    private fun letter(tok: String): Expr {
        if (peek() != "_") {
            if (tok == "e" && tok !in bound) {
                // eˣ is Euler's number however e is read; e on its own may be a letter.
                if (peek() != "^") flags.met += Ambiguity.E
                return if (flags.assumptions.eLetter) Expr.Sym(tok) else Expr.E
            }
            if (tok == "i" && tok !in bound) {
                flags.met += Ambiguity.I
                return if (flags.assumptions.iLetter) Expr.Sym(tok) else Expr.I
            }
            return Expr.Sym(tok)
        }
        i++
        val sub = groupTokens()
        // x_{1} and x_1 are the same unknown.
        return Expr.Sym("${tok}_${sub.joinToString("")}")
    }

    private fun call(name: String): Expr {
        if (name == "besselj" || name == "J") return bessel(name)
        val base = if (name == "log" && peek() == "_") { i++; group() } else null
        // sin^2 x is (sin x)²; sin^{-1} x is arcsin x.
        var power: Expr? = null
        if (peek() == "^") {
            i++
            power = group()
        }
        val marks = flags.degreeMarks
        val (written, barePower) = argumentOf()
        var arg = written
        // sin x² is sin(x²), or (sin x)² as assumed.
        if (barePower && power == null && written is Expr.Pow) {
            flags.met += Ambiguity.FUNCTION_POWER
            if (flags.assumptions.powerOfFunction) {
                arg = written.base
                power = written.exp
            }
        }
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
            "arccot" -> Func.ACOT
            "sgn" -> Func.SIGN
            "erf" -> Func.ERF
            "erfc" -> Func.ERFC
            "gamma", "Gamma" -> Func.GAMMA
            "digamma", "psi" -> Func.DIGAMMA
            "zeta" -> Func.ZETA
            "lambertw", "W" -> Func.LAMBERTW
            else -> throw unreadable()
        }
        // An angle without ° or π: radians, or degrees as assumed (always shown where degrees were taken).
        val plainAngle = flags.degreeMarks == marks && !arg.hasPi()
        val assumedDegrees = flags.assumptions.degrees
        if (f in ANGLE_FUNCTIONS && plainAngle) {
            if (arg.symbols().isEmpty() || assumedDegrees) flags.met += Ambiguity.ANGLES
            if (assumedDegrees) {
                arg = mul(arg, Expr.PI, num(Rational.ONE / Rational.of(180)))
                flags.degrees = true
            }
        }
        // log without a base: to base 10, or e as assumed. lg is always base 10.
        val natural = name == "log" && base == null && run {
            flags.met += Ambiguity.LOG
            flags.assumptions.naturalLog
        }
        var value = when {
            f == null -> exp(arg)
            natural -> fn(Func.LN, arg)
            f == Func.LOG -> fn(Func.LOG, base ?: num(10), arg)
            else -> fn(f, arg)
        }
        // An inverse function's angle in degrees, as assumed: arcsin ½ is 30.
        if (f in INVERSE_ANGLE_FUNCTIONS) {
            if (arg.symbols().isEmpty() || assumedDegrees) flags.met += Ambiguity.ANGLES
            if (assumedDegrees) {
                value = mul(value, num(Rational.of(180)), pow(Expr.PI, Expr.MINUS_ONE))
                flags.degrees = true
            }
        }
        return if (power != null && !inverse) pow(value, power) else value
    }

    /**
     * Bessel's J: `\operatorname{J}_{n}(x)` (the order as a subscript, the way the calculator writes it) or `besselj(n; x)`
     * (the order first, then `;` or `,`).
     */
    private fun bessel(name: String): Expr {
        val order: Expr
        val arg: Expr
        if (name == "J") {
            expect("_")
            order = group()
            arg = argument()
        } else {
            if (peek() != "(") throw unreadable()
            val start = i + 1
            var depth = 0
            var end = -1
            for (k in i until t.size) {
                if (t[k] == "(" || t[k] == "{" || t[k] == "[") depth++
                if (t[k] == ")" || t[k] == "}" || t[k] == "]") depth--
                if (depth == 0) { end = k; break }
            }
            if (end < 0) throw unreadable()
            val inside = t.subList(start, end)
            val separator = if (inside.contains(";")) ";" else ","
            val cut = inside.indexOf(separator).takeIf { it > 0 } ?: throw unreadable()
            order = parseAll(inside.subList(0, cut))
            arg = parseAll(inside.subList(cut + 1, inside.size))
            i = end + 1
        }
        return fn(Func.BESSELJ, order, arg)
    }

    /** A function's argument: a bracket, or else the numbers, letters and powers right after its name. */
    private fun argument(): Expr = argumentOf().first

    /** [argument], and whether it was a letter's power alone without brackets (sin x²), which can be read two ways. */
    private fun argumentOf(): Pair<Expr, Boolean> {
        if (peek() == "(") return bracket("(", ")") to false
        if (peek() == "{") return braced() to false
        val start = i
        var arg = power()
        val alone = isLetter(t.getOrNull(start)) && t.getOrNull(start + 1) == "^"
        var factors = 1
        while (true) {
            val next = peek() ?: break
            val run = lettersFrom(i)
            val simple = isDigit(next) || next == "\\pi" || next in GREEK ||
                (isLetter(next) && FUNCTIONS.none { run.startsWith(it) } && !run.startsWith("lim"))
            if (!simple) break
            arg = mul(arg, power())
            factors++
        }
        return arg to (alone && factors == 1 && arg is Expr.Pow && (arg as Expr.Pow).base is Expr.Sym)
    }

    private fun lettersFrom(k: Int): String {
        val run = StringBuilder()
        while (run.length < 8 && isLetter(t.getOrNull(k + run.length))) run.append(t[k + run.length])
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

        /** The letters a differential equation is solved for: y′ and dy/dx of these are its derivatives. */
        internal val ODE_UNKNOWNS = setOf("y", "u")

        /** The environments of a matrix. */
        private val MATRICES = setOf("pmatrix", "bmatrix", "matrix", "Bmatrix", "vmatrix")
        private val DIVIDE = setOf(":", "\\div", "/")
        /** Longest first, so `arcsin` is not read as `a r c sin` and `sinh` not as `sin h`. */
        private val FUNCTIONS = listOf("lambertw", "besselj", "digamma", "arcsin", "arccos", "arctan", "arccot", "gamma",
            "sinh", "cosh", "tanh", "erfc", "zeta", "sin", "cos", "tan", "cot", "sec", "csc", "exp", "log", "erf", "sgn", "ln", "lg")
        private val INVERSES = setOf("sin", "cos", "tan", "cot")

        /** φ(n), σ(n), τ(n), μ(n) of a whole number: Euler's φ, the divisors' sum and count, Möbius μ. */
        private val NUMBER_GREEK = mapOf("\\varphi" to "totient", "\\phi" to "totient", "\\sigma" to "sigma", "\\tau" to "tau", "\\mu" to "mu")

        /** The functions of an angle, and those whose value is one: where radians and degrees differ. */
        private val ANGLE_FUNCTIONS = setOf(Func.SIN, Func.COS, Func.TAN, Func.COT, Func.SEC, Func.CSC)
        private val INVERSE_ANGLE_FUNCTIONS = setOf(Func.ASIN, Func.ACOS, Func.ATAN, Func.ACOT)
        /** Names written only as commands: `\operatorname{J}_{n}` (Bessel) and `\operatorname{W}` (Lambert). */
        private val COMMAND_NAMES = setOf("J", "W")
        private val FUNCTION_COMMANDS = FUNCTIONS.map { "\\$it" }.toSet() + COMMAND_NAMES.map { "\\$it" }
        /** Greek letters that are functions when a bracket follows: Γ(x), ζ(s), ψ(x). */
        private val GREEK_FUNCTIONS = setOf("\\Gamma", "\\zeta", "\\psi")
        /** Commands whose braced letters are a name: `\operatorname{erf}`, `\mathrm{d}`. */
        private val NAMING = setOf("\\operatorname", "\\mathrm", "\\textrm", "\\text")
        /** Letters a learner names functions with: f(x) = … defines f. */
        private val FUNCTION_LETTERS = setOf("f", "g", "h", "F", "G", "H")
        private val GREEK = setOf("\\alpha", "\\beta", "\\gamma", "\\delta", "\\epsilon", "\\varepsilon", "\\zeta", "\\eta", "\\theta",
            "\\vartheta", "\\iota", "\\kappa", "\\lambda", "\\mu", "\\nu", "\\xi", "\\rho", "\\sigma", "\\tau", "\\upsilon", "\\phi",
            "\\varphi", "\\chi", "\\psi", "\\omega", "\\Delta", "\\Omega", "\\Phi", "\\Psi", "\\Theta", "\\Lambda", "\\Sigma", "\\Gamma")
        /** Spacing and sizing that change nothing: \left( is (, \, is nothing. */
        private val IGNORED = setOf("\\left", "\\,", "\\;", "\\!", "\\:", "\\ ", "\\displaystyle", "\\limits", "\\big", "\\Big",
            "\\bigl", "\\bigr", "\\Bigl", "\\Bigr", "\\quad", "\\qquad")

        /** The tokens without spacing and sizing; `\right.` (an invisible closing bracket) goes whole. */
        private fun clean(tokens: List<String>, spaced: MutableSet<Int>): List<String> {
            val out = ArrayList<String>(tokens.size)
            var k = 0
            while (k < tokens.size) {
                val s = tokens[k]
                when {
                    // \operatorname{erf} is the command \erf; \mathrm{d} is the letter d.
                    s in NAMING && tokens.getOrNull(k + 1) == "{" -> {
                        val close = (k + 2 until tokens.size).firstOrNull { tokens[it] == "}" }
                        val inside = if (close != null) tokens.subList(k + 2, close) else null
                        if (close == null || inside!!.isEmpty() || inside.any { it.length != 1 || !it[0].isLetter() }) {
                            out += s
                        } else {
                            val name = inside.joinToString("")
                            when {
                                name in FUNCTIONS || name in COMMAND_NAMES -> out += "\\$name"
                                // A command's call that could not be read is no product of its letters, and neither is a
                                // command inside a formula or a name the calculator does not know.
                                s == "\\operatorname" && name in CommandFunctions.NAMES ->
                                    throw CalcException(if (out.isEmpty()) Failure.ARGUMENTS else Failure.COMMAND_INSIDE)
                                s == "\\operatorname" && name.length >= 2 && name.lowercase() !in Discrete.NAMES && name != PhysicalConstants.CALL ->
                                    throw CalcException(Failure.UNKNOWN_FUNCTION)
                                else -> out.addAll(inside)
                            }
                            k = close
                        }
                    }
                    s == "\\right" -> if (tokens.getOrNull(k + 1) == ".") k++
                    s.length > 1 && s[0] == ',' -> {
                        spaced += out.size
                        out += ","
                    }
                    s !in IGNORED -> out += s
                }
                k++
            }
            return out
        }
    }
}

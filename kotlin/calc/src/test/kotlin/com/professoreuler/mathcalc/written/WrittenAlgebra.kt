package com.professoreuler.mathcalc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Algebra, equations, inequalities and systems: the tasks are built from their answers (factors chosen, then multiplied out by
 * the test; roots chosen, then the equation written), so every answer is known before the engine is asked.
 */
internal object WrittenAlgebra {

    /** A linear factor a·x + b, lowest first. */
    private fun lin(a: Long, b: Long) = listOf(b, a)

    private fun product(factors: List<List<Long>>): List<Long> = factors.fold(listOf(1L)) { acc, f -> polyTimes(acc, f) }

    private fun gcd(a: Long, b: Long): Long = if (b == 0L) abs(a) else gcd(b, a % b)

    /** The degrees of an engine's factors, each counted as often as its power: (x + 1)²(x² + 1) is [1, 1, 2]. */
    private fun factorDegrees(e: Expr, x: String): List<Int>? {
        val out = ArrayList<Int>()
        fun degree(p: Expr): Int? {
            val c = Solve.coefficientsIn(Algebra.expand(p), Expr.Sym(x)) ?: return null
            return c.indexOfLast { !isZero(it) }
        }
        fun take(f: Expr) {
            when {
                f is Expr.Num -> {}
                f is Expr.Pow && f.exp is Expr.Num && (f.exp as Expr.Num).value.isInteger -> {
                    val k = (f.exp as Expr.Num).value.num.toInt()
                    val d = degree(f.base) ?: return
                    repeat(k) { if (d > 0) out += d }
                }
                else -> degree(f)?.let { if (it > 0) out += it }
            }
        }
        if (e is Expr.Mul) e.factors.forEach(::take) else take(e)
        return out.sorted()
    }

    /** Whether [e] is multiplied out: a sum of monomials, no power of a sum and no product with a sum in it. */
    private fun expanded(e: Expr): Boolean {
        fun monomial(t: Expr): Boolean = when (t) {
            is Expr.Num, is Expr.Sym -> true
            is Expr.Pow -> t.base is Expr.Sym && t.exp is Expr.Num
            is Expr.Mul -> t.factors.all(::monomial)
            else -> false
        }
        return if (e is Expr.Add) e.terms.all(::monomial) else monomial(e)
    }

    fun algebra(): List<Task> {
        val r = Random(7071)
        val out = ArrayList<Task>()
        val pts = points1()
        // Products multiplied out.
        repeat(120) { i ->
            val n = r.int(2, 4)
            val factors = List(n) { lin(r.int(1, 4).toLong(), r.int(-7, 7).toLong()) }
            val k = if (i % 5 == 0) r.int(2, 4) else 1
            val text = factors.joinToString("") { "(${polyText(it)})" }.let { if (k > 1) "($it)^$k" else it }
            val p = product(List(k) { factors }.flatten())
            out += Task("algebra", "expand($text)", polyText(p), judge = { c -> resultOf(c)?.let { e -> expanded(e) && QPOINTS.all { x -> exactAt(e, mapOf("x" to x)) == polyAtQ(p, x) } } == true })
        }
        repeat(30) {
            val a = r.int(1, 4); val b = r.int(-4, 4, nonzero = true); val n = r.int(2, 5)
            out += Task("algebra", "expand((${a}x ${if (b < 0) "-" else "+"} ${abs(b)}y)^$n)", "", judge = { c ->
                resultOf(c)?.let(::expanded) == true &&
                    isFunction(listOf("x", "y"), points2(Random(n))) { (x, y) -> Math.pow(a * x + b * y, n.toDouble()) }(c)
            })
        }
        // Polynomials factored back: their factors' degrees as the test chose them.
        repeat(140) { i ->
            val factors = ArrayList<List<Long>>()
            repeat(r.int(1, 3)) {
                var a: Long; var b: Long
                do { a = r.int(1, 5).toLong(); b = r.int(-6, 6).toLong() } while (gcd(a, b) != 1L)
                factors += lin(a, b)
            }
            // A quadratic factor with no rational zero (its discriminant no square).
            if (i % 3 == 0) {
                var b: Long; var c: Long
                do { b = r.int(-4, 4).toLong(); c = r.int(-6, 6, nonzero = true).toLong() } while (isSquare(b * b - 4 * c))
                factors += listOf(c, b, 1L)
            }
            if (i % 4 == 0) factors += factors[0]
            if (factors.size < 2) factors += lin(1, r.int(-5, 5).toLong())
            val k = if (i % 7 == 0) r.int(2, 3).toLong() else 1L
            val p = product(factors).map { it * k }
            val want = factors.map { it.size - 1 }.sorted()
            out += Task("algebra", "factor(${polyText(p)})", "degrees $want", judge = { c ->
                val o = c.outcome as? Outcome.Factored
                if (o == null) false else {
                    val same = agrees(o.result, listOf("x"), pts) { (x) -> polyAt(p, x) }
                    val got = factorDegrees(o.result, "x")
                    when {
                        !same -> false
                        got == want -> true
                        // Fewer factors, honestly marked "perhaps more", is a decline; marked complete it is wrong.
                        !o.complete -> throw Declined()
                        else -> false
                    }
                }
            })
        }
        // Fractions cancelled: equal where defined, finite at the zeros of the common factor, those zeros excluded.
        repeat(80) {
            val common = r.int(-5, 5)
            val num = List(r.int(1, 2)) { lin(1, r.int(-6, 6).toLong()) }.filter { it[0] != -common.toLong() }
            val den = List(r.int(1, 2)) { lin(1, r.int(-6, 6).toLong()) }.filter { it[0] != -common.toLong() && it !in num }
            if (num.isEmpty() || den.isEmpty()) return@repeat
            val c = lin(1, -common.toLong())
            val p = product(num + listOf(c)); val q = product(den + listOf(c))
            val text = "simplify((${polyText(p)})/(${polyText(q)}))"
            out += Task("algebra", text, "", judge = { calc ->
                val o = calc.outcome as? Outcome.Expression
                o != null && agrees(o.result, listOf("x"), pts) { (x) -> polyAt(p, x) / polyAt(q, x) } &&
                    at(o.result, mapOf("x" to common.toDouble())).isFinite() &&
                    o.excluded.any { (_, v) -> close(at(v, emptyMap()), common.toDouble()) }
            })
        }
        // Identities simplified to what they are.
        val identities = listOf(
            "sin(x)^2 + cos(x)^2" to { _: Double -> 1.0 }, "1 + tan(x)^2" to { x: Double -> 1 / (kotlin.math.cos(x) * kotlin.math.cos(x)) },
            "ln(x^2) - ln(x)" to { x: Double -> kotlin.math.ln(x) }, "e^(ln(x))" to { x: Double -> x },
            "sqrt(x^2)" to { x: Double -> abs(x) }, "(x^2 - 9)/(x - 3)" to { x: Double -> x + 3 },
            "sin(2x)/(2sin(x))" to { x: Double -> kotlin.math.cos(x) }, "cos(x)^2 - sin(x)^2" to { x: Double -> kotlin.math.cos(2 * x) },
            "2sin(x)cos(x)" to { x: Double -> kotlin.math.sin(2 * x) }, "(x + 1)^2 - (x - 1)^2" to { x: Double -> 4 * x },
            "x^3 x^4 / x^5" to { x: Double -> x * x }, "1/x + 1/(x + 1)" to { x: Double -> 1 / x + 1 / (x + 1) },
            "ln(e^(2x))" to { x: Double -> 2 * x }, "sqrt(8) + sqrt(18)" to { _: Double -> 5 * sqrt(2.0) },
            "(x^3 - x)/(x^2 + x)" to { x: Double -> x - 1 }, "sin(x)/tan(x)" to { x: Double -> kotlin.math.cos(x) },
            "cosh(x)^2 - sinh(x)^2" to { _: Double -> 1.0 }, "log_2(8x) - log_2(x)" to { _: Double -> 3.0 },
        )
        for ((t, f) in identities) out += Task("algebra", "simplify($t)", "", judge = { c ->
            val e = resultOf(c)?.takeIf { !it.symbols().isEmpty() || isExactForm(f(1.0))(c) }
            // Equal where the original is defined (ln needs x > 0).
            e != null && agrees(e, listOf("x"), points1(0.13, 2.9)) { (x) -> f(x) }
        })
        // Partial fractions: equal, and a sum of one term for each factor at least.
        repeat(50) {
            val roots = generateSequence { r.int(-6, 6).toLong() }.distinct().take(r.int(2, 3)).toList()
            val numerator = List(roots.size - 1) { r.int(-5, 5).toLong() } + 0L
            val den = product(roots.map { lin(1, -it) })
            val numText = polyText(numerator.dropLast(1).ifEmpty { listOf(1L) })
            val numP = numerator.dropLast(1).ifEmpty { listOf(1L) }
            if (numP.all { it == 0L }) return@repeat
            out += Task("algebra", "partialfractions(($numText)/(${polyText(den)}))", "", judge = { c ->
                val e = resultOf(c)
                e != null && agrees(e, listOf("x"), pts) { (x) -> polyAt(numP, x) / polyAt(den, x) } && (e !is Expr.Add || e.terms.size <= roots.size)
            })
        }
        // Identities compared, and near misses.
        repeat(40) { i ->
            val a = r.int(-5, 5, nonzero = true).toLong(); val b = r.int(-5, 5).toLong()
            val miss = if (i % 2 == 0) 0L else r.int(1, 3).toLong()
            val lhs = "(x ${if (a < 0) "-" else "+"} ${abs(a)})(x ${if (b < 0) "-" else "+"} ${abs(b)})"
            val rhs = polyText(listOf(a * b + miss, a + b, 1L))
            out += Task("algebra", "$lhs = $rhs", if (miss == 0L) "equal" else "differ", judge = { c ->
                when (val o = c.outcome) {
                    is Outcome.Compared -> o.holds == (miss == 0L)
                    // An equation that is no identity may also be solved: it then has no solution (the sides differ by a number).
                    is Outcome.Solved -> miss != 0L && o.solutions.values.isEmpty() && !o.solutions.always
                    else -> false
                }
            })
        }
        return out
    }

    private fun isSquare(n: Long): Boolean = n >= 0 && sqrt(n.toDouble()).toLong().let { it * it == n || (it + 1) * (it + 1) == n }

    // -----------------------------------------------------------------------------------------------------------------------
    // Equations.

    /** Each family a + b·k is a solution of [f] for several k, and every solution of one period ([expected]) is one of them. */
    private fun isPeriodic(expected: List<Double>, period: Double, f: (Double) -> Double): (Calculation) -> Boolean = { c ->
        val s = (c.outcome as? Outcome.Solved)?.solutions
        if (s == null || s.periodic.isEmpty()) false
        else {
            val ok = s.periodic.all { fam -> (-2..2).all { k -> abs(f(at(fam, mapOf("k" to k.toDouble())))) < 1e-9 } }
            fun mod(x: Double) = ((x % period) + period) % period
            val members = s.periodic.flatMap { fam -> (-3..3).map { k -> mod(at(fam, mapOf("k" to k.toDouble()))) } }
            ok && expected.all { e -> members.any { m -> abs(m - mod(e)) < 1e-9 || abs(abs(m - mod(e)) - period) < 1e-9 } }
        }
    }

    fun solving(): List<Task> {
        val r = Random(9091)
        val out = ArrayList<Task>()
        // Polynomials with whole and fractional zeros.
        repeat(160) { i ->
            val n = r.int(1, 4)
            val factors = List(n) { if (i % 3 == 0) lin(r.int(1, 4).toLong(), r.int(-7, 7).toLong()) else lin(1, r.int(-8, 8).toLong()) }
            val k = r.int(1, 3).toLong()
            val p = product(factors).map { it * k }
            val roots = factors.map { -it[0].toDouble() / it[1] }
            val text = polyText(p) + " = 0"
            out += Task("solving", if (i % 2 == 0) "solve($text, x)" else text, "x = ${roots.distinct().sorted()}", judge = isRoots(roots))
        }
        // Quadratics with roots p ± √q, and with no real roots (their complex ones).
        repeat(60) {
            val p = r.int(-5, 5); val q = listOf(2, 3, 5, 6, 7, 10, 11)[r.nextInt(7)]
            // (x − p)² − q = x² − 2p·x + p² − q
            val poly = listOf((p * p - q).toLong(), (-2 * p).toLong(), 1L)
            out += Task("solving", "solve(${polyText(poly)} = 0, x)", "$p ± √$q", judge = isRoots(listOf(p - sqrt(q.toDouble()), p + sqrt(q.toDouble()))))
            val m = r.int(1, 5)
            val cpoly = listOf((p * p + m * m).toLong(), (-2 * p).toLong(), 1L)
            out += Task("solving", "solve(${polyText(cpoly)} = 0, x)", "none ($p ± ${m}i)", judge = { c ->
                val s = (c.outcome as? Outcome.Solved)?.solutions
                s != null && s.values.isEmpty() && s.complex.size == 2 &&
                    listOf(Cx(p.toDouble(), m.toDouble()), Cx(p.toDouble(), -m.toDouble())).all { z -> s.complex.any { close(Verify.value(it, emptyMap()), z) } }
            })
        }
        // Linear equations a·x + b = c·x + d.
        repeat(50) {
            val a = r.int(-9, 9); val b = r.int(-20, 20); var c = r.int(-9, 9); val d = r.int(-20, 20)
            if (a == c) c += 1
            val x = (d - b).toDouble() / (a - c)
            out += Task("solving", "${polyText(listOf(b.toLong(), a.toLong()))} = ${polyText(listOf(d.toLong(), c.toLong()))}", fmt(x), judge = isRoots(listOf(x)))
        }
        // Fractions: a/(x − p) = b, and 1/(x − p) + 1/(x − q) = c.
        repeat(30) {
            val a = r.int(-6, 6, nonzero = true); val p = r.int(-5, 5); val b = r.int(-4, 4, nonzero = true)
            out += Task("solving", "solve($a/(x ${if (p < 0) "+" else "-"} ${abs(p)}) = $b, x)", "", judge = isRoots(listOf(p + a.toDouble() / b)))
            val q = p + r.int(1, 4); val c = r.int(1, 3)
            // 1/(x−p) + 1/(x−q) = c: c·x² − (c(p + q) + 2)x + c·p·q + p + q = 0
            val A = c.toDouble(); val B = -(c * (p + q) + 2.0); val C = c.toDouble() * p * q + p + q
            val disc = B * B - 4 * A * C
            val roots = if (disc < 0) emptyList() else listOf((-B - sqrt(disc)) / (2 * A), (-B + sqrt(disc)) / (2 * A))
            out += Task("solving", "solve(1/(x ${if (p < 0) "+" else "-"} ${abs(p)}) + 1/(x ${if (q < 0) "+" else "-"} ${abs(q)}) = $c, x)", "", judge = isRoots(roots))
        }
        // Roots: √(x + a) = x + b, squared, with the false solution left out.
        repeat(30) {
            val a = r.int(-3, 12); val b = r.int(-4, 4)
            val B = 2.0 * b - 1; val C = b.toDouble() * b - a
            val disc = B * B - 4 * C
            val roots = if (disc < 0) emptyList() else listOf((-B - sqrt(disc)) / 2, (-B + sqrt(disc)) / 2).filter { it + b >= -1e-12 && it + a >= -1e-12 }
            out += Task("solving", "solve(sqrt(x ${if (a < 0) "-" else "+"} ${abs(a)}) = x ${if (b < 0) "-" else "+"} ${abs(b)}, x)", "", judge = isRoots(roots))
        }
        // Exponentials and logarithms.
        for (b in listOf(2, 3, 5, 10)) for (v in listOf(7, 20, 100)) out += Task("solving", "solve($b^x = $v, x)", "", judge = isRoots(listOf(kotlin.math.ln(v.toDouble()) / kotlin.math.ln(b.toDouble()))))
        for (k in listOf(1, 2, 3, -1)) for (v in listOf(3, 10)) out += Task("solving", "solve(e^(${k}x) = $v, x)", "", judge = isRoots(listOf(kotlin.math.ln(v.toDouble()) / k)))
        for (b in listOf(2, 3, 10)) for (k in listOf(-1, 2, 3)) out += Task("solving", "solve(log_$b(x) = $k, x)", "", judge = isRoots(listOf(Math.pow(b.toDouble(), k.toDouble()))))
        for (a in listOf(-2, 0, 3)) for (k in listOf(1, 2)) out += Task("solving", "solve(ln(x ${if (a < 0) "-" else "+"} ${abs(a)}) = $k, x)", "", judge = isRoots(listOf(kotlin.math.exp(k.toDouble()) - a)))
        for ((a, k) in listOf(2 to 3, 6 to 4, 1 to 1, 3 to 2)) {
            // log₂ x + log₂(x + a) = k: x² + a·x − 2ᵏ = 0, x > 0
            val disc = a * a + 4 * Math.pow(2.0, k.toDouble())
            out += Task("solving", "solve(log_2(x) + log_2(x + $a) = $k, x)", "", judge = isRoots(listOf((-a + sqrt(disc)) / 2)))
        }
        for ((p, q) in listOf(1 to 2, 2 to 3, 1 to 4, 3 to 5)) {
            // e^(2x) − (p + q)eˣ + pq = 0
            out += Task("solving", "solve(e^(2x) - ${p + q}e^x + ${p * q} = 0, x)", "", judge = isRoots(listOf(kotlin.math.ln(p.toDouble()), kotlin.math.ln(q.toDouble()))))
        }
        // Absolute values.
        repeat(30) {
            val a = r.int(1, 4); val b = r.int(-6, 6); val c = r.int(-2, 8)
            val roots = when {
                c < 0 -> emptyList()
                else -> listOf((c - b).toDouble() / a, (-c - b).toDouble() / a)
            }
            out += Task("solving", "solve(|${a}x ${if (b < 0) "-" else "+"} ${abs(b)}| = $c, x)", "", judge = isRoots(roots))
        }
        // Trigonometric equations: every family, and every solution of a period.
        val values = listOf("0" to 0.0, "1/2" to 0.5, "-1/2" to -0.5, "1" to 1.0, "-1" to -1.0, "sqrt(2)/2" to sqrt(2.0) / 2, "sqrt(3)/2" to sqrt(3.0) / 2, "1/3" to 1.0 / 3)
        for (m in listOf(1, 2, 3)) for ((t, v) in values) {
            val mx = if (m == 1) "x" else "${m}x"
            val period = 2 * PI / m
            val s0 = kotlin.math.asin(v)
            val sinSols = listOf(s0 / m, (PI - s0) / m)
            out += Task("solving", "solve(sin($mx) = $t, x)", "", judge = isPeriodic(sinSols, period) { x -> kotlin.math.sin(m * x) - v })
            val c0 = kotlin.math.acos(v)
            out += Task("solving", "solve(cos($mx) = $t, x)", "", judge = isPeriodic(listOf(c0 / m, -c0 / m), period) { x -> kotlin.math.cos(m * x) - v })
        }
        for ((t, v) in listOf("0" to 0.0, "1" to 1.0, "-1" to -1.0, "sqrt(3)" to sqrt(3.0), "2" to 2.0)) {
            out += Task("solving", "solve(tan(x) = $t, x)", "", judge = isPeriodic(listOf(kotlin.math.atan(v)), PI) { x -> kotlin.math.tan(x) - v })
        }
        // Formulas for a letter: each solution put back, at random values of the other letters, into the test's own sides.
        class Formula(val text: String, val letter: String, val count: Int, val f: (Map<String, Double>) -> Double)
        val formulas = listOf(
            Formula("v = s/t", "t", 1) { m -> m.getValue("v") - m.getValue("s") / m.getValue("t") },
            Formula("A = pi r^2", "r", 2) { m -> m.getValue("A") - PI * m.getValue("r") * m.getValue("r") },
            Formula("E = m c^2", "m", 1) { m -> m.getValue("E") - m.getValue("m") * m.getValue("c") * m.getValue("c") },
            Formula("F = m a", "a", 1) { m -> m.getValue("F") - m.getValue("m") * m.getValue("a") },
            Formula("y = m x + b", "x", 1) { m -> m.getValue("y") - m.getValue("m") * m.getValue("x") - m.getValue("b") },
            Formula("P = 2a + 2b", "a", 1) { m -> m.getValue("P") - 2 * m.getValue("a") - 2 * m.getValue("b") },
            Formula("V = l w h", "h", 1) { m -> m.getValue("V") - m.getValue("l") * m.getValue("w") * m.getValue("h") },
            Formula("a x + b = c", "x", 1) { m -> m.getValue("a") * m.getValue("x") + m.getValue("b") - m.getValue("c") },
            Formula("s = v t + a t", "t", 1) { m -> m.getValue("s") - m.getValue("v") * m.getValue("t") - m.getValue("a") * m.getValue("t") },
            Formula("U = R I", "I", 1) { m -> m.getValue("U") - m.getValue("R") * m.getValue("I") },
            Formula("y = a x^2", "x", 2) { m -> m.getValue("y") - m.getValue("a") * m.getValue("x") * m.getValue("x") },
            Formula("p V = n R T", "T", 1) { m -> m.getValue("p") * m.getValue("V") - m.getValue("n") * m.getValue("R") * m.getValue("T") },
            Formula("1/f = 1/a + 1/b", "b", 1) { m -> 1 / m.getValue("f") - 1 / m.getValue("a") - 1 / m.getValue("b") },
            Formula("K = (1/2) m v^2", "v", 2) { m -> m.getValue("K") - 0.5 * m.getValue("m") * m.getValue("v") * m.getValue("v") },
            Formula("A = (a + c) h/2", "c", 1) { m -> m.getValue("A") - (m.getValue("a") + m.getValue("c")) * m.getValue("h") / 2 },
        )
        val fr = Random(3)
        for (fm in formulas) out += Task("solving", "solve(${fm.text}, ${fm.letter})", "${fm.count} solutions", judge = { c ->
            val s = (c.outcome as? Outcome.Solved)?.solutions
            s != null && s.values.size == fm.count && s.values.all { v ->
                var compared = 0
                repeat(8) {
                    val env = Regex("[A-Za-z]").findAll(fm.text.replace("pi", "")).map { it.value }.filter { it != fm.letter }.toSet().associateWith { fr.nextDouble(0.5, 3.0) }
                    val x = at(v, env)
                    if (x.isFinite()) {
                        val residual = fm.f(env + (fm.letter to x))
                        if (abs(residual) > 1e-8 * (1 + abs(x))) return@all false
                        compared++
                    }
                }
                compared >= 4
            }
        })
        return out
    }

    // -----------------------------------------------------------------------------------------------------------------------
    // Inequalities.

    /** Whether x lies in the engine's set; at a bound (within 1e-9) as the bound's closedness says. */
    private fun member(set: IntervalSet, x: Double): Boolean = set.intervals.any { i ->
        val lo = i.lower?.let { at(it, emptyMap()) } ?: Double.NEGATIVE_INFINITY
        val hi = i.upper?.let { at(it, emptyMap()) } ?: Double.POSITIVE_INFINITY
        when {
            abs(x - lo) < 1e-9 -> i.lowerClosed
            abs(x - hi) < 1e-9 -> i.upperClosed
            else -> x > lo && x < hi
        }
    }

    /**
     * The engine's intervals against the inequality: at the critical points as [included] says, between and beyond them as
     * [holds] says.
     */
    private fun isIntervals(critical: List<Double>, included: (Double) -> Boolean, holds: (Double) -> Boolean): (Calculation) -> Boolean = { c ->
        val set = (c.outcome as? Outcome.Intervals)?.set
        if (set == null) false else {
            val cs = critical.distinct().sorted()
            val probes = ArrayList<Double>()
            if (cs.isEmpty()) probes += listOf(-7.3, -1.1, 0.4, 2.9, 11.0)
            else {
                probes += cs.first() - 1.7; probes += cs.last() + 2.3
                cs.zipWithNext { a, b -> probes += (a + b) / 2; probes += a + (b - a) * 0.13 }
            }
            probes.all { member(set, it) == holds(it) } && cs.all { member(set, it) == included(it) }
        }
    }

    fun inequalities(): List<Task> {
        val r = Random(1213)
        val out = ArrayList<Task>()
        val rels = listOf("<", "<=", ">", ">=")
        fun test(rel: String, v: Double) = when (rel) { "<" -> v < 0; "<=" -> v <= 0; ">" -> v > 0; else -> v >= 0 }
        // Linear.
        repeat(50) {
            val a = r.int(-6, 6, nonzero = true); val b = r.int(-10, 10); val c = r.int(-10, 10); val rel = rels[r.nextInt(4)]
            val x0 = (c - b).toDouble() / a
            out += Task("inequalities", "${polyText(listOf(b.toLong(), a.toLong()))} $rel $c", "", judge = isIntervals(listOf(x0), { rel.length == 2 }) { x -> test(rel, a * x + b - c) })
        }
        // Products of linear factors, written multiplied out.
        repeat(110) { i ->
            val roots = List(if (i % 3 == 0) 3 else 2) { r.int(-6, 6).toLong() }
            val k = if (i % 5 == 0) -1L else 1L
            val p = product(roots.map { lin(1, -it) }).map { it * k }
            val rel = rels[r.nextInt(4)]
            out += Task("inequalities", "${polyText(p)} $rel 0", "", judge = isIntervals(roots.map { it.toDouble() }, { rel.length == 2 }) { x -> test(rel, polyAt(p, x)) })
        }
        // Quotients: zeros of the numerator as ≤ says, the denominator's never.
        repeat(50) {
            val a = r.int(-6, 6); var b = r.int(-6, 6)
            if (a == b) b += 1
            val rel = rels[r.nextInt(4)]
            out += Task("inequalities", "(x ${if (a < 0) "+" else "-"} ${abs(a)})/(x ${if (b < 0) "+" else "-"} ${abs(b)}) $rel 0", "",
                judge = isIntervals(listOf(a.toDouble(), b.toDouble()), { x -> rel.length == 2 && abs(x - a) < 1e-9 }) { x -> test(rel, (x - a) / (x - b)) })
        }
        // Absolute values: |x − a| < b and the others.
        repeat(40) {
            val a = r.int(-6, 6); val b = r.int(0, 6); val rel = rels[r.nextInt(4)]
            out += Task("inequalities", "|x ${if (a < 0) "+" else "-"} ${abs(a)}| $rel $b", "",
                judge = isIntervals(listOf(a - b.toDouble(), a + b.toDouble()), { rel.length == 2 }) { x -> test(rel, abs(x - a) - b.toDouble()) })
        }
        // Always and never.
        for ((t, all) in listOf("x^2 + 1 > 0" to true, "x^2 + 1 < 0" to false, "x^2 >= 0" to true, "x^2 + 4 <= 0" to false, "(x - 2)^2 >= 0" to true, "-x^2 - 1 > 0" to false)) {
            out += Task("inequalities", t, if (all) "ℝ" else "∅", judge = { c -> (c.outcome as? Outcome.Intervals)?.set?.let { if (all) it.all else it.empty } == true })
        }
        return out
    }

    // -----------------------------------------------------------------------------------------------------------------------
    // Systems.

    fun systems(): List<Task> {
        val r = Random(5151)
        val out = ArrayList<Task>()
        fun det(m: List<List<Long>>): Long = when (m.size) {
            2 -> m[0][0] * m[1][1] - m[0][1] * m[1][0]
            else -> m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0]) + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0])
        }
        repeat(230) { i ->
            val n = if (i % 2 == 0) 2 else 3
            val names = listOf("x", "y", "z").take(n)
            val sol = List(n) { if (i % 4 == 1) q(r.int(-9, 9).toLong(), r.int(1, 4).toLong()) else q(r.int(-6, 6).toLong()) }
            val m = List(n) { List(n) { r.int(-5, 5).toLong() } }
            if (det(m) == 0L) {
                // Singular: the same left side with another right side has no solution.
                if (n == 2 && m.all { row -> row.any { it != 0L } }) {
                    val l = m[0]
                    val lhs = l.mapIndexed { k, c -> term(c, names[k], first = k == 0 || l.take(k).all { it == 0L }) }.joinToString("").ifEmpty { "0" }
                    out += Task("systems", "$lhs = 1; ${l.mapIndexed { k, c -> term(2 * c, names[k], first = k == 0 || l.take(k).all { it == 0L }) }.joinToString("")} = 3", "no solution",
                        judge = { c -> (c.outcome as? Outcome.SystemSolved)?.solutions?.isEmpty() == true })
                }
                return@repeat
            }
            val lines = m.map { row ->
                val lhs = row.mapIndexed { k, c -> term(c, names[k], first = row.take(k).all { it == 0L }) }.joinToString("").ifEmpty { "0" }
                val rhs = row.indices.fold(Q.ZERO) { acc, k -> acc + q(row[k]) * sol[k] }
                "$lhs = ${if (rhs.isInteger) "${rhs.n}" else "$rhs"}"
            }
            val text = if (i % 3 == 0) "solve(${lines.joinToString(", ")})" else lines.joinToString("; ")
            out += Task("systems", text, names.indices.joinToString(", ") { "${names[it]} = ${sol[it]}" }, judge = { c ->
                val s = (c.outcome as? Outcome.SystemSolved)?.solutions?.singleOrNull()
                s != null && names.indices.all { k -> s[names[k]]?.let { v -> close(at(v, emptyMap()), sol[k].toDouble()) } == true }
            })
        }
        // x + y = s, x·y = p: the pairs of the roots of t² − s·t + p.
        repeat(20) {
            val a = r.int(-6, 6); val b = r.int(-6, 6)
            val pairs = setOf(a to b, b to a)
            out += Task("systems", "x + y = ${a + b}; x y = ${a * b}", "$pairs", judge = { c ->
                val s = (c.outcome as? Outcome.SystemSolved)?.solutions
                s != null && s.size == pairs.size && pairs.all { (x, y) -> s.any { m -> close(at(m.getValue("x"), emptyMap()), x.toDouble()) && close(at(m.getValue("y"), emptyMap()), y.toDouble()) } }
            })
        }
        // A circle and a line: the test's own quadratic.
        repeat(20) {
            val rr = r.int(1, 6); val k = r.int(-4, 4)
            // x² + (x + k)² = r²: 2x² + 2k·x + k² − r² = 0
            val disc = 4.0 * k * k - 8.0 * (k * k - rr * rr)
            val xs = if (disc < 0) emptyList() else listOf((-2.0 * k - sqrt(disc)) / 4, (-2.0 * k + sqrt(disc)) / 4).distinct()
            out += Task("systems", "x^2 + y^2 = ${rr * rr}; y = x ${if (k < 0) "-" else "+"} ${abs(k)}", "", judge = { c ->
                val s = (c.outcome as? Outcome.SystemSolved)?.solutions
                s != null && s.size == xs.size && xs.all { x -> s.any { m -> close(at(m.getValue("x"), emptyMap()), x, 1e-8) && close(at(m.getValue("y"), emptyMap()), x + k, 1e-8) } }
            })
        }
        return out
    }
}

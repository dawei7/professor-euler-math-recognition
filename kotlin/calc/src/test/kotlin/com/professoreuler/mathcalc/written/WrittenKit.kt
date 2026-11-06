package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.random.Random

/*
 * The written corpus's tools: a task and how its answer is judged, against a value known without the engine (a textbook value,
 * or one the test computes itself in plain arithmetic: whole-number fractions, doubles, brute force). Nothing here calls the
 * engine's algebra; the engine's answer is only read (its numbers evaluated, its text taken apart).
 */

/** How a task came out: right, declined (the engine said it cannot), or wrong (an answer that is not the answer, or a false refusal). */
internal enum class Verdict { RIGHT, DECLINED, WRONG }

/**
 * A task: its [area], the line [input] as the Calculator's keys type it (or, with [latex], as Calculate reads handwriting, with
 * [choice]), the answer in words ([expected], for the report), the failures that are its right answer ([fails]: a divergent
 * series, a singular matrix), and [judge], which says whether an answer is the answer.
 */
internal class Task(
    val area: String,
    val input: String,
    val expected: String,
    val fails: Set<Failure> = emptySet(),
    val latex: Boolean = false,
    val choice: Choice? = null,
    val judge: (Calculation) -> Boolean,
)

/** Thrown by a judge where the answer is honest about being incomplete ("perhaps more"): a decline, not a wrong answer. */
internal class Declined : Exception()

internal class Ran(val task: Task, val verdict: Verdict, val answer: String, val nanos: Long)

/** Refusals that state something of the task: where the task has an answer, they are wrong, not declined. */
internal val CLAIMS = setOf(Failure.DIVISION_BY_ZERO, Failure.UNDEFINED, Failure.DIVERGES, Failure.SINGULAR, Failure.SIZES,
    Failure.UNITS, Failure.NO_TRIANGLE, Failure.ARGUMENTS, Failure.UNKNOWN_FUNCTION, Failure.COMMAND_INSIDE)

internal fun runTask(t: Task): Ran {
    val start = System.nanoTime()
    val c = try {
        if (t.latex) MathCalc.calculate(t.input, t.choice) else Reports.of(TextInput.read(t.input)).result
    } catch (e: Throwable) {
        return Ran(t, Verdict.WRONG, "crash: ${e::class.simpleName} ${e.message}", System.nanoTime() - start)
    }
    val nanos = System.nanoTime() - start
    val o = c.outcome
    val answer = try { answerOf(c) } catch (e: Throwable) { "(${o::class.simpleName})" }
    val verdict = when {
        o is Outcome.Failed && o.failure in t.fails -> Verdict.RIGHT
        o is Outcome.Failed && o.failure in CLAIMS -> Verdict.WRONG
        o is Outcome.Failed -> Verdict.DECLINED
        else -> try { if (t.judge(c)) Verdict.RIGHT else Verdict.WRONG } catch (e: Declined) { Verdict.DECLINED } catch (e: Exception) { Verdict.WRONG }
    }
    return Ran(t, verdict, answer, nanos)
}

// ---------------------------------------------------------------------------------------------------------------------------
// Exact fractions of the test's own.

internal class Q private constructor(val n: BigInteger, val d: BigInteger) : Comparable<Q> {
    companion object {
        fun of(n: BigInteger, d: BigInteger = BigInteger.ONE): Q {
            require(d.signum() != 0) { "division by zero" }
            val g = n.gcd(d).let { if (it.signum() == 0) BigInteger.ONE else it }
            val s = if (d.signum() < 0) -1 else 1
            return Q(n / g * s.toBigInteger(), d / g * s.toBigInteger())
        }
        fun of(n: Long, d: Long = 1): Q = of(BigInteger.valueOf(n), BigInteger.valueOf(d))
        val ZERO = of(0)
        val ONE = of(1)
    }
    operator fun plus(o: Q) = of(n * o.d + o.n * d, d * o.d)
    operator fun minus(o: Q) = of(n * o.d - o.n * d, d * o.d)
    operator fun times(o: Q) = of(n * o.n, d * o.d)
    operator fun div(o: Q) = of(n * o.d, d * o.n)
    operator fun unaryMinus() = of(-n, d)
    fun pow(k: Int): Q = if (k >= 0) of(n.pow(k), d.pow(k)) else of(d.pow(-k), n.pow(-k))
    val isZero get() = n.signum() == 0
    val isInteger get() = d == BigInteger.ONE
    fun toDouble(): Double = java.math.BigDecimal(n).divide(java.math.BigDecimal(d), java.math.MathContext.DECIMAL64).toDouble()
    override fun compareTo(other: Q) = (n * other.d).compareTo(other.n * d)
    override fun equals(other: Any?) = other is Q && n == other.n && d == other.d
    override fun hashCode() = n.hashCode() * 31 + d.hashCode()
    /** As typed: 3, -3, (3/4), (-3/4). */
    fun typed(): String = if (isInteger) (if (n.signum() < 0) "($n)" else "$n") else "($n/$d)"
    override fun toString() = if (isInteger) "$n" else "$n/$d"
}

internal fun q(n: Long, d: Long = 1) = Q.of(n, d)

// ---------------------------------------------------------------------------------------------------------------------------
// Reading the engine's answer.

/**
 * An exact constant of the engine's, read to 17 digits in ball arithmetic: its doubles cancel where it does
 * (10⁴ − √(10⁸ − 1) is 5·10⁻⁵, though a double makes 0.0000500000001). Complex where it holds i.
 */
internal fun read(e: Expr): Cx =
    if (e.hasI()) Verify.value(e, emptyMap()) else Precise.decimal(e, 17)?.toDouble()?.let { Cx(it) } ?: Verify.value(e, emptyMap())

/** The engine's number, read: a value, an exact form, a definite integral, an expression without letters. Null otherwise. */
internal fun numberOf(c: Calculation): Cx? = when (val o = c.outcome) {
    is Outcome.Value -> o.exact?.let { read(it) } ?: Cx(o.value.double)
    is Outcome.DefiniteIntegral -> o.exact?.let { read(it) } ?: o.value?.let { Cx(it) }
    is Outcome.Expression -> o.result.takeIf { it.symbols().isEmpty() && it !is Expr.Mat }?.let { read(it) }
    else -> null
}

/** The number's line of text as the Calculator shows it. */
internal fun shownOf(c: Calculation): String? = when (val o = c.outcome) {
    is Outcome.Value -> o.exact?.let { c.textValue(it) } ?: c.text(o.value)
    is Outcome.DefiniteIntegral -> o.exact?.let { c.textValue(it) }
    is Outcome.Expression -> c.textValue(o.result)
    else -> null
}

/** The exact number the engine gives, where it gives a fraction. */
internal fun rationalOf(c: Calculation): Q? {
    val o = c.outcome
    val r = when (o) {
        is Outcome.Value -> if (o.exact != null) rational(o.exact) else (o.value as? Num.Exact)?.r
        is Outcome.DefiniteIntegral -> o.exact?.let { rational(it) }
        is Outcome.Expression -> rational(o.result)
        else -> null
    } ?: return null
    return Q.of(r.num, r.den)
}

/** Whether [e] is exact: no decimal anywhere in it. */
internal fun exact(e: Expr): Boolean = when (e) {
    is Expr.Real -> false
    is Expr.Add -> e.terms.all(::exact)
    is Expr.Mul -> e.factors.all(::exact)
    is Expr.Pow -> exact(e.base) && exact(e.exp)
    is Expr.Fn -> e.args.all(::exact)
    is Expr.Mat -> e.rows.all { r -> r.all(::exact) }
    else -> true
}

private val DECIMAL = Regex("""(-?\d+(?:\.\d+)?)(?: · 10\^(-?\d+))?""")

/**
 * Whether every decimal shown after ≈ (or a decimal alone) is [ref] to within a unit of its tenth significant digit: the digits
 * a learner reads are the value's.
 */
internal fun digitsHold(shown: String?, ref: Double): Boolean {
    if (shown == null || !ref.isFinite()) return true
    val at = shown.indexOf('≈')
    val part = when {
        at >= 0 -> shown.substring(at + 1).trim()
        shown.contains('/') || shown.any { it.isLetter() || it == '√' || it == 'π' || it == '∛' } -> return true
        else -> shown.trim()
    }
    val m = DECIMAL.matchEntire(part) ?: return true
    val v = m.groupValues[1].toDouble() * (m.groupValues[2].takeIf { it.isNotEmpty() }?.let { Math.pow(10.0, it.toDouble()) } ?: 1.0)
    if (v == 0.0) return abs(ref) < 1e-12
    val unit = Math.pow(10.0, floor(log10(abs(v))) - 9)
    return abs(v - ref) <= unit * 1.01 + 1e-15 * abs(ref)
}

internal fun close(a: Double, b: Double, rel: Double = 1e-9, absolute: Double = 1e-12) =
    a == b || abs(a - b) <= max(absolute, rel * max(abs(a), abs(b)))

internal fun close(a: Cx, b: Cx, rel: Double = 1e-9) =
    (a - b).abs <= max(1e-12, rel * max(a.abs, b.abs))

/** [e] at the letters [env], as a double; NaN where it is not real. */
internal fun at(e: Expr, env: Map<String, Double>): Double = Numeric.eval(e, env)

// ---------------------------------------------------------------------------------------------------------------------------
// Judges.

/**
 * A number equal to [ref] (to [rel]), with its shown digits right. One shown as exact (no ≈: 0, 1, 3/4) must be [ref] to the
 * last bit a double holds: normalcdf(42, 44, 69, 3) is 3.9·10⁻¹⁷, never 0.
 */
internal fun isNumber(ref: Double, rel: Double = 1e-9): (Calculation) -> Boolean = { c ->
    val v = numberOf(c)
    val shown = shownOf(c)
    val exactClaim = shown != null && '≈' !in shown && rationalOf(c) != null
    v != null && v.isReal && (if (exactClaim) close(v.re, ref, 1e-12, 1e-300) else close(v.re, ref, rel)) && digitsHold(shown, ref)
}

/** A complex number equal to [re] + [im]·i. */
internal fun isComplex(re: Double, im: Double): (Calculation) -> Boolean = { c ->
    val v = numberOf(c)
    v != null && close(v, Cx(re, im))
}

/** Exactly the fraction [ref]. */
internal fun isExact(ref: Q): (Calculation) -> Boolean = { c -> rationalOf(c) == ref }

/** An exact form (no decimal in it) whose value is [ref], with its shown digits right. */
internal fun isExactForm(ref: Double): (Calculation) -> Boolean = { c ->
    val e = when (val o = c.outcome) {
        is Outcome.Value -> o.exact ?: (o.value as? Num.Exact)?.let { num(it.r) }
        is Outcome.DefiniteIntegral -> o.exact
        is Outcome.Expression -> o.result
        else -> null
    }
    e != null && exact(e) && isNumber(ref)(c)
}

/** The engine's expression as the result, read; a number as its expression. */
internal fun resultOf(c: Calculation): Expr? = when (val o = c.outcome) {
    is Outcome.Expression -> o.result
    is Outcome.Value -> o.exact ?: (o.value as? Num.Exact)?.let { num(it.r) } ?: Expr.Real(o.value.double)
    else -> null
}

/**
 * An expression equal to [ref] at the points: each point a value of [letters]; a point where [ref] is not defined is skipped,
 * and at least three must be compared.
 */
internal fun isFunction(letters: List<String>, points: List<DoubleArray>, rel: Double = 1e-7, ref: (DoubleArray) -> Double): (Calculation) -> Boolean = { c ->
    val e = resultOf(c)
    e != null && e !is Expr.Mat && agrees(e, letters, points, rel, ref)
}

internal fun agrees(e: Expr, letters: List<String>, points: List<DoubleArray>, rel: Double = 1e-7, ref: (DoubleArray) -> Double): Boolean {
    if (!letters.containsAll(e.symbols())) return false
    var compared = 0
    for (p in points) {
        val want = ref(p)
        if (!want.isFinite()) continue
        val got = at(e, letters.indices.associate { letters[it] to p[it] })
        if (!close(got, want, rel, 1e-9)) return false
        compared++
    }
    return compared >= 3
}

/** Points of one letter in [lo, hi], not on whole numbers or simple fractions. */
internal fun points1(lo: Double = -2.7, hi: Double = 2.9, n: Int = 7): List<DoubleArray> =
    (0 until n).map { k -> doubleArrayOf(lo + (hi - lo) * (k + 0.37) / n) }

internal fun points2(r: Random, n: Int = 7): List<DoubleArray> = (0 until n).map { doubleArrayOf(r.nextDouble(-2.5, 2.5) + 0.013, r.nextDouble(-2.5, 2.5) - 0.017) }

/** The real solutions, as a set: equal to [roots] within [rel]; nothing else listed, nothing periodic. */
internal fun isRoots(roots: List<Double>, rel: Double = 1e-8): (Calculation) -> Boolean = { c ->
    val s = (c.outcome as? Outcome.Solved)?.solutions
    if (s == null || s.periodic.isNotEmpty() || s.many || s.always) false
    else {
        val got = s.values.map { read(it).re }
        val want = roots.distinctBy { Math.round(it * 1e7) }
        got.all { it.isFinite() } && got.size == want.size && want.all { w -> got.any { close(it, w, rel, 1e-9) } } &&
            s.values.zip(got).all { (e, v) -> digitsHold(c.textValue(e), v) }
    }
}

/** A matrix equal to [ref] entry by entry (exact fractions or values). */
internal fun isMatrix(ref: List<List<Double>>, rel: Double = 1e-9): (Calculation) -> Boolean = { c ->
    val m = resultOf(c) as? Expr.Mat
    m != null && m.rows.size == ref.size && m.rows.zip(ref).all { (a, b) -> a.size == b.size && a.zip(b).all { (x, y) -> close(at(x, emptyMap()), y, rel) } }
}

// ---------------------------------------------------------------------------------------------------------------------------
// The test's own numerics, in doubles: no engine in them.

/** ∫ₐᵇ f by adaptive Simpson, to about 1e-12 (a, b finite; f finite inside). */
internal fun integrate(a: Double, b: Double, eps: Double = 1e-12, f: (Double) -> Double): Double {
    fun simpson(a: Double, b: Double, fa: Double, fm: Double, fb: Double) = (b - a) / 6 * (fa + 4 * fm + fb)
    fun go(a: Double, b: Double, fa: Double, fm: Double, fb: Double, whole: Double, eps: Double, depth: Int): Double {
        val m = (a + b) / 2
        val lm = (a + m) / 2
        val rm = (m + b) / 2
        val flm = f(lm)
        val frm = f(rm)
        val left = simpson(a, m, fa, flm, fm)
        val right = simpson(m, b, fm, frm, fb)
        return if (depth > 40 || abs(left + right - whole) <= 15 * eps) left + right + (left + right - whole) / 15
        else go(a, m, fa, flm, fm, left, eps / 2, depth + 1) + go(m, b, fm, frm, fb, right, eps / 2, depth + 1)
    }
    // In 37 pieces first: a periodic integrand can vanish at the few points of a single first estimate (x·sin 2x on [−π, π]).
    val pieces = 37
    var total = 0.0
    for (i in 0 until pieces) {
        val lo = a + (b - a) * i / pieces
        val hi = a + (b - a) * (i + 1) / pieces
        val fa = f(lo)
        val fb = f(hi)
        val fm = f((lo + hi) / 2)
        total += go(lo, hi, fa, fm, fb, simpson(lo, hi, fa, fm, fb), eps / pieces, 0)
    }
    return total
}

/** f′(x) by central differences with Richardson's extrapolation, to about 1e-9 relative. */
internal fun slope(x: Double, f: (Double) -> Double): Double = slope(x, 1e-3 * max(1.0, abs(x)), f)

private fun slope(x: Double, h: Double, f: (Double) -> Double): Double {
    val d1 = (f(x + h) - f(x - h)) / (2 * h)
    val d2 = (f(x + h / 2) - f(x - h / 2)) / h
    return (4 * d2 - d1) / 3
}

/** f′(x) where two steps agree on it (NaN near a pole or where f varies too fast for its differences). */
internal fun steadySlope(x: Double, f: (Double) -> Double): Double {
    val h = 2e-4 * max(1.0, abs(x))
    val a = slope(x, h, f)
    val b = slope(x, h / 4, f)
    return if (a.isFinite() && b.isFinite() && abs(a - b) <= 1e-7 * max(1.0, abs(a))) b else Double.NaN
}

/** The zeros of f in [a, b]: sign changes on a fine grid, each refined by bisection (also the tangent zeros found as minima of |f|). */
internal fun zeros(a: Double, b: Double, steps: Int = 4000, f: (Double) -> Double): List<Double> {
    val out = ArrayList<Double>()
    var x0 = a
    var f0 = f(x0)
    for (k in 1..steps) {
        val x1 = a + (b - a) * k / steps
        val f1 = f(x1)
        if (f0 == 0.0) out += x0
        else if (f0.isFinite() && f1.isFinite() && f0 * f1 < 0) {
            var lo = x0
            var hi = x1
            var flo = f0
            repeat(200) {
                val m = (lo + hi) / 2
                val fm = f(m)
                if (flo * fm <= 0) hi = m else { lo = m; flo = fm }
            }
            val r = (lo + hi) / 2
            // A sign change across a pole is no zero.
            if (abs(f(r)) < 1e-6) out += r
        }
        x0 = x1
        f0 = f1
    }
    return out
}

internal fun Random.int(lo: Int, hi: Int, nonzero: Boolean = false): Int {
    while (true) {
        val v = nextInt(lo, hi + 1)
        if (!nonzero || v != 0) return v
    }
}

/** A term c·body as typed after others: "+ 3x", "- x", "" for 0. */
internal fun term(c: Long, body: String, first: Boolean = false): String {
    if (c == 0L) return ""
    val size = abs(c)
    val coefficient = if (size == 1L && body.isNotEmpty()) "" else "$size"
    val sep = if (body.isNotEmpty() && coefficient.isNotEmpty() && body[0].isDigit()) "*" else ""
    val t = coefficient + sep + body
    return when {
        first -> (if (c < 0) "-" else "") + t
        c < 0 -> " - $t"
        else -> " + $t"
    }
}

/** A polynomial with whole coefficients, highest power first, as typed: 2x^3 - x + 5 ("0" where all are 0). */
internal fun polyText(coefficients: List<Long>, x: String = "x"): String {
    val sb = StringBuilder()
    for (k in coefficients.indices.reversed()) {
        val c = coefficients[k]
        if (c == 0L) continue
        val body = when (k) { 0 -> ""; 1 -> x; else -> "$x^$k" }
        sb.append(term(c, body, first = sb.isEmpty()))
    }
    return if (sb.isEmpty()) "0" else sb.toString()
}

/** The value of a polynomial (coefficients lowest first) at x. */
internal fun polyAt(coefficients: List<Long>, x: Double): Double = coefficients.foldRight(0.0) { c, acc -> acc * x + c }

/** The product of polynomials with whole coefficients, lowest first. */
internal fun polyTimes(a: List<Long>, b: List<Long>): List<Long> {
    val out = LongArray(a.size + b.size - 1)
    for (i in a.indices) for (j in b.indices) out[i + j] = Math.addExact(out[i + j], Math.multiplyExact(a[i], b[j]))
    return out.toList()
}

internal fun fmt(d: Double): String = if (d == floor(d) && abs(d) < 1e15) d.toLong().toString() else d.toString()

/** [e] at rational values of its letters, in the test's own fractions: a polynomial or a fraction of them; null for anything else. */
internal fun exactAt(e: Expr, env: Map<String, Q>): Q? = when (e) {
    is Expr.Num -> Q.of(e.value.num, e.value.den)
    is Expr.Sym -> env[e.name]
    is Expr.Add -> e.terms.fold(Q.ZERO as Q?) { acc, t -> acc?.let { a -> exactAt(t, env)?.let { a + it } } }
    is Expr.Mul -> e.factors.fold(Q.ONE as Q?) { acc, t -> acc?.let { a -> exactAt(t, env)?.let { a * it } } }
    is Expr.Pow -> rational(e.exp)?.takeIf { it.isInteger && it.num.bitLength() < 16 }?.let { k ->
        exactAt(e.base, env)?.let { b -> if (b.isZero && k.signum < 0) null else b.pow(k.num.toInt()) }
    }
    else -> null
}

/** Rational points of one letter, none a whole number. */
internal val QPOINTS = listOf(q(-7, 3), q(-5, 4), q(-1, 3), q(2, 7), q(5, 6), q(9, 4), q(17, 5))

/** The polynomial (coefficients lowest first) at a fraction. */
internal fun polyAtQ(coefficients: List<Long>, x: Q): Q = coefficients.foldRight(Q.ZERO) { c, acc -> acc * x + q(c) }

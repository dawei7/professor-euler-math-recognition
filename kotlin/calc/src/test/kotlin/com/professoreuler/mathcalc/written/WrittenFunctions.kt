package com.professoreuler.mathcalc

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cosh
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/**
 * The functions added for 5.0: hyperbolic and inverse functions by their logarithms in doubles; Ei, Si, Ci, Yₙ, K, E and B against
 * mpmath ([WrittenTables]); rounding by BigDecimal (half away from zero); max and min by the test's own comparison; real and
 * imaginary parts, conjugates and arguments in the test's own complex fractions; the orthogonal polynomials by their three-term
 * recurrences in fractions; gcd and lcm of polynomials built from chosen factors; bases by BigInteger; and every second name
 * refused.
 */
internal object WrittenFunctions {
    private const val AREA = "functions"

    private val POINTS = listOf(q(-3), q(-2), q(-3, 2), q(-1), q(-1, 2), q(-1, 4), q(1, 4), q(1, 3), q(1, 2), q(2, 3), q(1), q(5, 4), q(3, 2), q(2), q(5, 2), q(3), q(7))

    private fun hyperbolic(): List<Task> {
        val out = ArrayList<Task>()
        for (v in POINTS) {
            val x = v.toDouble()
            val t = v.typed()
            out += Task(AREA, "coth($t)", "", judge = isNumber(1 / tanh(x)))
            out += Task(AREA, "sech($t)", "", judge = isNumber(1 / cosh(x)))
            out += Task(AREA, "csch($t)", "", judge = isNumber(1 / sinh(x)))
            out += Task(AREA, "arsinh($t)", "", judge = isNumber(if (x >= 0) ln(x + sqrt(x * x + 1)) else -ln(-x + sqrt(x * x + 1))))
            // arcosh on [1, ∞); below 1 its value is complex: i·arccos x on (−1, 1), arcosh|x| + πi below −1.
            out += when {
                x >= 1 -> Task(AREA, "arcosh($t)", "", judge = if (x == 1.0) isNumber(0.0) else isNumber(ln(x + sqrt(x * x - 1))))
                x > -1 -> Task(AREA, "arcosh($t)", "", judge = isComplex(0.0, acos(x)))
                else -> Task(AREA, "arcosh($t)", "", judge = isComplex(ln(-x + sqrt(x * x - 1)), PI))
            }
            // artanh on (−1, 1), arcoth outside [−1, 1]; beyond, ½ln|(1 + x)/(1 − x)| + πi/2 (the engine's branch for x > 1).
            if (kotlin.math.abs(x) < 1) {
                out += Task(AREA, "artanh($t)", "", judge = isNumber(0.5 * ln((1 + x) / (1 - x))))
            } else if (kotlin.math.abs(x) > 1) {
                out += Task(AREA, "arcoth($t)", "", judge = isNumber(0.5 * ln((x + 1) / (x - 1))))
                if (x > 1) out += Task(AREA, "artanh($t)", "", judge = isComplex(0.5 * ln((x + 1) / (x - 1)), PI / 2))
            }
            if (kotlin.math.abs(x) >= 1) {
                out += Task(AREA, "arcsec($t)", "", judge = if (x == 1.0) isNumber(0.0) else isNumber(acos(1 / x)))
                out += Task(AREA, "arccsc($t)", "", judge = isNumber(asin(1 / x)))
            } else out += Task(AREA, "arcsec($t)", "undefined", fails = setOf(Failure.UNDEFINED)) { false }
        }
        for (f in listOf("artanh(1)", "arcoth(1/2)", "coth(0)", "csch(0)", "arccsc(1/3)")) out += Task(AREA, f, "undefined", fails = setOf(Failure.UNDEFINED)) { false }
        return out
    }

    private fun special(): List<Task> = WrittenTables.SPECIAL.distinctBy { it.first }.map { (call, v) -> Task(AREA, call, v, judge = isNumber(v.toDouble(), 1e-9)) } +
        // B(a, b) of whole numbers: (a − 1)!(b − 1)!/(a + b − 1)!.
        (1..6).flatMap { a -> (1..5).map { b ->
            fun f(n: Int) = (2..n).fold(BigInteger.ONE) { p, k -> p * BigInteger.valueOf(k.toLong()) }
            Task(AREA, "beta($a, $b)", "", judge = isExact(Q.of(f(a - 1) * f(b - 1), f(a + b - 1))))
        } } + listOf("Ci(0)", "Ei(0)", "elliptick(1)", "bessely(0, 0)", "beta(0, 1)").map { Task(AREA, it, "undefined", fails = setOf(Failure.UNDEFINED)) { false } }

    private fun rounding(r: Random): List<Task> {
        val out = ArrayList<Task>()
        val mc = MathContext(60)
        val constants = listOf("pi" to BigDecimal("3.14159265358979323846264338327950288419716939937510582097494"),
            "e" to BigDecimal("2.71828182845904523536028747135266249775724709369995957496697"),
            "sqrt(2)" to BigDecimal(2).sqrt(mc), "sqrt(3)" to BigDecimal(3).sqrt(mc), "-sqrt(5)" to BigDecimal(5).sqrt(mc).negate())
        repeat(60) { i ->
            val digits = r.int(-2, 6)
            val (text, value) = if (i % 3 == 0) constants.random(r) else {
                val v = BigDecimal(BigInteger.valueOf(r.nextLong(-999999, 999999)), r.int(0, 5))
                v.toPlainString() to v
            }
            // Half away from zero: BigDecimal's HALF_UP.
            val want = value.setScale(digits, RoundingMode.HALF_UP)
            val q = if (want.scale() >= 0) Q.of(want.unscaledValue(), BigInteger.TEN.pow(want.scale())) else Q.of(want.unscaledValue() * BigInteger.TEN.pow(-want.scale()))
            out += Task(AREA, if (digits == 0 && i % 2 == 0) "round($text)" else "round($text, $digits)", want.toPlainString(), judge = isExact(q))
        }
        // max and min of numbers, fractions and roots: compared here in fractions or in BigDecimal.
        repeat(40) {
            val xs = List(r.int(1, 6)) { POINTS.random(r) }
            val text = xs.joinToString(", ") { it.typed() }
            out += Task(AREA, "max($text)", "", judge = isExact(xs.max()))
            out += Task(AREA, "min($text)", "", judge = isExact(xs.min()))
        }
        out += Task(AREA, "max(sqrt(2), 1.414, 7/5)", "√2", judge = isNumber(sqrt(2.0)))
        out += Task(AREA, "min(pi, 22/7, 3.1416)", "π", judge = isNumber(PI))
        out += Task(AREA, "max(x, 2)", "letters", fails = setOf(Failure.ARGUMENTS)) { false }
        return out
    }

    /** Complex fractions: a + bi. */
    private data class Z(val re: Q, val im: Q) {
        operator fun plus(o: Z) = Z(re + o.re, im + o.im)
        operator fun times(o: Z) = Z(re * o.re - im * o.im, re * o.im + im * o.re)
        operator fun div(o: Z): Z { val d = o.re * o.re + o.im * o.im; return Z((re * o.re + im * o.im) / d, (im * o.re - re * o.im) / d) }
        fun text(): String = "(${re.typed()} + ${im.typed()}i)"
    }

    private fun complex(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(50) { i ->
            val a = Z(q(r.int(-6, 6).toLong()), q(r.int(-6, 6).toLong()))
            val b = Z(q(r.int(-6, 6).toLong()), q(r.int(-6, 6, nonzero = true).toLong()))
            val (z, text) = when (i % 3) {
                0 -> a to a.text()
                1 -> (a * b) to "${a.text()}${b.text()}"
                else -> (a / b) to "${a.text()}/${b.text()}"
            }
            out += Task(AREA, "re($text)", "${z.re}", judge = isExact(z.re))
            out += Task(AREA, "im($text)", "${z.im}", judge = isExact(z.im))
            out += Task(AREA, "conj($text)", "${z.re} − ${z.im}i", judge = isComplex(z.re.toDouble(), -z.im.toDouble()))
            out += if (z.re.isZero && z.im.isZero) Task(AREA, "arg($text)", "undefined", fails = setOf(Failure.UNDEFINED)) { false }
            else Task(AREA, "arg($text)", "", judge = isNumber(atan2(z.im.toDouble(), z.re.toDouble())))
        }
        return out
    }

    /** Pₙ, Tₙ, Hₙ and Lₙ by their recurrences, in fractions (lowest power first). */
    private fun orthogonal(name: String, n: Int): List<Q> {
        val x = listOf(Q.ZERO, Q.ONE)
        var p0: List<Q>
        var p1: List<Q>
        when (name) {
            "legendrep" -> { p0 = listOf(Q.ONE); p1 = x }
            "chebyshevt" -> { p0 = listOf(Q.ONE); p1 = x }
            "hermiteh" -> { p0 = listOf(Q.ONE); p1 = listOf(Q.ZERO, q(2)) }
            else -> { p0 = listOf(Q.ONE); p1 = listOf(Q.ONE, q(-1)) }
        }
        if (n == 0) return p0
        for (k in 1 until n) {
            val kk = q(k.toLong())
            val next = when (name) {
                // (k + 1)P₍k+1₎ = (2k + 1)xPₖ − kP₍k−1₎.
                "legendrep" -> QP.scale(QP.plus(QP.scale(QP.times(x, p1), q(2L * k + 1)), QP.scale(p0, -kk)), Q.ONE / q(k + 1L))
                "chebyshevt" -> QP.plus(QP.scale(QP.times(x, p1), q(2)), QP.scale(p0, -Q.ONE))
                "hermiteh" -> QP.plus(QP.scale(QP.times(x, p1), q(2)), QP.scale(p0, -q(2L * k)))
                // (k + 1)L₍k+1₎ = (2k + 1 − x)Lₖ − kL₍k−1₎.
                else -> QP.scale(QP.plus(QP.times(listOf(q(2L * k + 1), q(-1)), p1), QP.scale(p0, -kk)), Q.ONE / q(k + 1L))
            }
            p0 = p1; p1 = next
        }
        return p1
    }

    private fun polynomials(r: Random): List<Task> {
        val out = ArrayList<Task>()
        for (name in listOf("legendrep", "chebyshevt", "hermiteh", "laguerrel")) for (n in 0..12) {
            val p = orthogonal(name, n)
            out += Task(AREA, "$name($n, x)", QP.text(p), judge = { c -> resultOf(c)?.let { e -> QPOINTS.all { exactAt(e, mapOf("x" to it)) == QP.at(p, it) } } == true })
            val at = POINTS.random(r)
            out += Task(AREA, "$name($n, ${at.typed()})", "", judge = isExact(QP.at(p, at)))
        }
        out += Task(AREA, "legendrep(-1, x)", "undefined", fails = setOf(Failure.UNDEFINED)) { false }
        out += Task(AREA, "hermiteh(2.5, x)", "undefined", fails = setOf(Failure.UNDEFINED)) { false }
        // gcd and lcm of products of chosen factors (x − r)ᵏ: the least and the greatest powers, monic.
        repeat(50) { i ->
            val roots = listOf(-3, -2, -1, 0, 1, 2, 3).shuffled(r).take(r.int(2, 4))
            val count = if (i % 4 == 0) 3 else 2
            val powers = List(count) { roots.map { r.int(0, 2) } }
            fun poly(ks: List<Int>, scale: Long): List<Q> = roots.indices.fold(listOf(q(scale))) { acc, j -> (1..ks[j]).fold(acc) { a, _ -> QP.times(a, listOf(q(-roots[j].toLong()), Q.ONE)) } }
            val ps = powers.map { poly(it, r.int(1, 3).toLong()) }
            if (ps.any { it.size < 2 }) return@repeat
            val g = poly(roots.indices.map { j -> powers.minOf { it[j] } }, 1)
            val l = poly(roots.indices.map { j -> powers.maxOf { it[j] } }, 1)
            val text = ps.joinToString(", ") { QP.text(it) }
            out += Task(AREA, "gcd($text)", QP.text(g), judge = { c -> resultOf(c)?.let { e -> QPOINTS.all { exactAt(e, mapOf("x" to it)) == QP.at(g, it) } } == true })
            out += Task(AREA, "lcm($text)", QP.text(l), judge = { c -> resultOf(c)?.let { e -> QPOINTS.all { exactAt(e, mapOf("x" to it)) == QP.at(l, it) } } == true })
        }
        return out
    }

    private fun bases(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(60) { i ->
            val n = if (i % 3 == 0) BigInteger(r.int(64, 300), java.util.Random(i.toLong())) else BigInteger.valueOf(r.nextLong(-100000, 100000))
            val base = r.int(2, 36)
            val digits = n.toString(base).uppercase()
            out += Task(AREA, "tobase($n, $base)", digits, judge = { c -> (c.outcome as? Outcome.InBase)?.let { it.value == n && it.base == base && it.digits.uppercase() == digits } == true })
            // A numeral that is no word of the keys: its letters are digits only where they are below the base.
            out += Task(AREA, "frombase(${n.toString(base)}, $base)", "$n", judge = isExact(Q.of(n)))
        }
        out += Task(AREA, "tobase(255, 37)", "no base", fails = setOf(Failure.ARGUMENTS)) { false }
        out += Task(AREA, "frombase(12, 2)", "no binary numeral", fails = setOf(Failure.ARGUMENTS)) { false }
        return out
    }

    /** One name for each function: every second name is refused. */
    private fun names(): List<Task> = listOf("asin(1/2)", "acos(0)", "atan(1)", "acot(1)", "asec(2)", "acsc(2)", "asinh(1)", "acosh(2)", "atanh(1/2)",
        "acoth(2)", "asech(1/2)", "acsch(1)", "arcsinh(1)", "arccosh(2)", "arctanh(1/2)", "arccoth(2)", "lg(100)", "tg(pi/4)", "ctg(pi/4)", "arctg(1)",
        "arcctg(1)", "productlog(1)", "sign(-3)", "ncr(5, 2)", "choose(5, 2)", "perm(5, 2)", "fib(10)", "moebius(6)", "variance(1, 2, 3)", "stdev(1, 2, 3)",
        "foo(2)", "sine(1)", "logarithm(8)").map { Task(AREA, it, "UNKNOWN_FUNCTION", fails = setOf(Failure.UNKNOWN_FUNCTION)) { false } }

    fun all(): List<Task> {
        val r = Random(6061)
        return hyperbolic() + special() + rounding(r) + complex(r) + polynomials(r) + bases(r) + names()
    }
}

package com.professoreuler.mathcalc

import kotlin.math.abs
import kotlin.math.max

/** A complex number of doubles: the values Calculate checks its results with. */
internal data class Cx(val re: Double, val im: Double = 0.0) {
    operator fun plus(o: Cx) = Cx(re + o.re, im + o.im)
    operator fun minus(o: Cx) = Cx(re - o.re, im - o.im)
    operator fun times(o: Cx) = Cx(re * o.re - im * o.im, re * o.im + im * o.re)
    operator fun div(o: Cx): Cx {
        val d = o.re * o.re + o.im * o.im
        return Cx((re * o.re + im * o.im) / d, (im * o.re - re * o.im) / d)
    }
    operator fun unaryMinus() = Cx(-re, -im)
    val abs get() = kotlin.math.hypot(re, im)
    val arg get() = kotlin.math.atan2(im, re)
    val finite get() = re.isFinite() && im.isFinite()
    val isReal get() = abs(im) <= 1e-12 * max(1.0, abs(re))

    fun exp(): Cx = kotlin.math.exp(re).let { r -> Cx(r * kotlin.math.cos(im), r * kotlin.math.sin(im)) }
    fun ln(): Cx = Cx(kotlin.math.ln(abs), arg)
    fun pow(o: Cx): Cx = if (re == 0.0 && im == 0.0) (if (o.re > 0) Cx(0.0) else Cx(Double.NaN)) else (ln() * o).exp()
    fun sin() = Cx(kotlin.math.sin(re) * kotlin.math.cosh(im), kotlin.math.cos(re) * kotlin.math.sinh(im))
    fun cos() = Cx(kotlin.math.cos(re) * kotlin.math.cosh(im), -kotlin.math.sin(re) * kotlin.math.sinh(im))

    companion object {
        val NAN = Cx(Double.NaN, Double.NaN)
    }
}

/**
 * Checks of what Calculate shows: whether two expressions have the same value, at random values of their letters, in
 * complex arithmetic with the principal branches (so ln(x²) and 2 ln x differ for x < 0) but real odd roots (∛(−8) = −2).
 * A point where either side is not defined is skipped; too few points compared is not "the same".
 */
object Verify {
    private const val TRIES = 14
    private const val TOLERANCE = 1e-8

    /** Whether [a] and [b] are equal wherever both are defined. */
    fun same(a: Expr, b: Expr): Boolean = differenceAt(a, b) == null && comparable(a, b)

    /**
     * Whether [candidate] may stand for [original]: defined wherever the original is, and equal there. ln a + ln b may become
     * ln(ab), and 2 ln x may become ln x², but ln x² never 2 ln x (which is not defined for x < 0).
     */
    fun replaces(original: Expr, candidate: Expr): Boolean {
        if (original == candidate) return true
        if (!same(original, candidate)) return false
        val letters = (original.symbols() + candidate.symbols()).toList()
        val complex = original.hasI() || candidate.hasI()
        val random = java.util.Random(31L * letters.hashCode() + 29)
        for (attempt in 0 until TRIES * 6) {
            val env = letters.associateWith { point(random, attempt) }
            if (at(original, env, complex).finite && !at(candidate, env, complex).finite) return false
        }
        return true
    }

    /** A value of the letters where [a] and [b] differ, or null where none was found. */
    fun differenceAt(a: Expr, b: Expr): Map<String, Double>? {
        if (a == b) return null
        val letters = (a.symbols() + b.symbols()).toList()
        val complex = a.hasI() || b.hasI()
        val random = java.util.Random(31L * letters.hashCode() + 17)
        for (attempt in 0 until TRIES * 6) {
            val env = letters.associateWith { point(random, attempt) }
            val va = at(a, env, complex)
            val vb = at(b, env, complex)
            if (!va.finite || !vb.finite) continue
            if (!close(va, vb)) return env
        }
        return null
    }

    /** Whether enough points had both sides defined to say they are the same. */
    private fun comparable(a: Expr, b: Expr): Boolean {
        if (a == b) return true
        if (a.hasOp() || b.hasOp() || a.hasInfinity() || b.hasInfinity()) return false
        val letters = (a.symbols() + b.symbols()).toList()
        val random = java.util.Random(31L * letters.hashCode() + 17)
        val complex = a.hasI() || b.hasI()
        var compared = 0
        for (attempt in 0 until TRIES * 6) {
            val env = letters.associateWith { point(random, attempt) }
            if (at(a, env, complex).finite && at(b, env, complex).finite) compared++
            if (compared >= TRIES) return true
        }
        return compared >= TRIES / 2
    }

    /** Values that are not whole numbers or simple fractions, where special identities would hide a difference. */
    private fun point(random: java.util.Random, attempt: Int): Double {
        val range = if (attempt < TRIES * 2) 3.0 else 9.0
        return (random.nextDouble() * 2 - 1) * range + 0.0137
    }

    internal fun close(a: Cx, b: Cx): Boolean {
        val scale = max(1.0, max(a.abs, b.abs))
        return (a - b).abs <= TOLERANCE * scale
    }

    /**
     * [e]'s value with its letters at [env]: real (undefined where a root of a negative or a logarithm of one appears), or
     * complex with the principal branches where the expressions compared hold i.
     */
    private fun at(e: Expr, env: Map<String, Double>, complex: Boolean): Cx =
        if (complex) value(e, env) else Cx(Numeric.eval(e, env))

    /** [e]'s complex value with its letters at [env]. */
    internal fun value(e: Expr, env: Map<String, Double>): Cx = when (e) {
        is Expr.Num -> Cx(e.value.toDouble())
        is Expr.Real -> Cx(e.value)
        is Expr.Sym -> env[e.name]?.let { Cx(it) } ?: Cx.NAN
        is Expr.Const -> when (e.kind) {
            Constant.PI -> Cx(Math.PI)
            Constant.E -> Cx(Math.E)
            Constant.I -> Cx(0.0, 1.0)
            Constant.INF -> Cx.NAN
        }
        is Expr.Add -> e.terms.fold(Cx(0.0)) { acc, t -> acc + value(t, env) }
        is Expr.Mul -> e.factors.fold(Cx(1.0)) { acc, f -> acc * value(f, env) }
        is Expr.Pow -> power(e, env)
        is Expr.Fn -> function(e.f, e.args.map { value(it, env) })
        is Expr.Op, is Expr.Mat -> Cx.NAN
    }

    private fun power(p: Expr.Pow, env: Map<String, Double>): Cx {
        val b = value(p.base, env)
        val r = rational(p.exp)
        if (r != null && r.isInteger && r.num.bitLength() < 31) {
            val n = r.num.toInt()
            var out = Cx(1.0)
            var base = if (n < 0) Cx(1.0) / b else b
            var k = kotlin.math.abs(n)
            while (k > 0) {
                if (k and 1 == 1) out *= base
                base *= base
                k = k shr 1
            }
            return out
        }
        // Real odd roots of negatives, as the calculator takes them: ∛(−8) = −2.
        if (r != null && b.isReal && b.re < 0 && r.den.testBit(0)) {
            val magnitude = Math.pow(-b.re, r.toDouble())
            return Cx(if (r.num.testBit(0)) -magnitude else magnitude)
        }
        return b.pow(value(p.exp, env))
    }

    private fun function(f: Func, a: List<Cx>): Cx {
        val x = a.last()
        val one = Cx(1.0)
        return when (f) {
            Func.SIN -> x.sin()
            Func.COS -> x.cos()
            Func.TAN -> x.sin() / x.cos()
            Func.COT -> x.cos() / x.sin()
            Func.SEC -> one / x.cos()
            Func.CSC -> one / x.sin()
            Func.SINH -> (x.exp() - (-x).exp()) / Cx(2.0)
            Func.COSH -> (x.exp() + (-x).exp()) / Cx(2.0)
            Func.TANH -> (x.exp() - (-x).exp()) / (x.exp() + (-x).exp())
            Func.LN -> if (x.abs == 0.0) Cx.NAN else x.ln()
            Func.LOG -> if (x.abs == 0.0 || a[0].abs == 0.0) Cx.NAN else x.ln() / a[0].ln()
            Func.ABS -> Cx(x.abs)
            // The inverse functions, floor, ceil, sign and factorial on real values only.
            else -> if (x.isReal) Cx(Numeric.function(f, a.map { it.re })) else Cx.NAN
        }
    }
}

package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.math.PI
import kotlin.math.abs

/** A formula as a tree. [eval] calculates it with the unknowns' values in [env]. */
internal sealed class Node {
    abstract fun eval(env: Map<String, Num>): Num
    abstract fun unknowns(): Set<String>
}

internal class Literal(val value: Rational) : Node() {
    override fun eval(env: Map<String, Num>) = Num.Exact(value)
    override fun unknowns() = emptySet<String>()
}

internal class Constant(val value: Double) : Node() {
    override fun eval(env: Map<String, Num>) = Num.Approx(value)
    override fun unknowns() = emptySet<String>()
}

internal class Unknown(val name: String) : Node() {
    override fun eval(env: Map<String, Num>) = env[name] ?: throw CalcException(Failure.UNKNOWN_WITHOUT_EQUATION)
    override fun unknowns() = setOf(name)
}

internal abstract class Binary(val a: Node, val b: Node) : Node() {
    override fun unknowns() = a.unknowns() + b.unknowns()
}

internal abstract class Unary(val a: Node) : Node() {
    override fun unknowns() = a.unknowns()
}

internal class Add(a: Node, b: Node) : Binary(a, b) {
    override fun eval(env: Map<String, Num>) = Arith.add(a.eval(env), b.eval(env))
}

internal class Sub(a: Node, b: Node) : Binary(a, b) {
    override fun eval(env: Map<String, Num>) = Arith.add(a.eval(env), Arith.neg(b.eval(env)))
}

internal class Mul(a: Node, b: Node) : Binary(a, b) {
    override fun eval(env: Map<String, Num>) = Arith.mul(a.eval(env), b.eval(env))
}

internal class Div(a: Node, b: Node) : Binary(a, b) {
    override fun eval(env: Map<String, Num>) = Arith.div(a.eval(env), b.eval(env))
}

internal class Pow(a: Node, b: Node) : Binary(a, b) {
    override fun eval(env: Map<String, Num>) = Arith.pow(a.eval(env), b.eval(env))
}

/** The [index]th root of [a]. */
internal class Root(a: Node, val index: Node) : Binary(a, index) {
    override fun eval(env: Map<String, Num>): Num {
        val n = index.eval(env)
        val k = (n as? Num.Exact)?.r?.takeIf { it.isInteger && it.signum > 0 }?.num?.toInt() ?: throw CalcException(Failure.UNDEFINED)
        val x = a.eval(env)
        if (x.double < 0 && k % 2 == 0) throw CalcException(Failure.UNDEFINED)
        return Arith.pow(x, Num.Exact(Rational.ONE / Rational.of(k.toLong())))
    }
}

internal class Neg(a: Node) : Unary(a) {
    override fun eval(env: Map<String, Num>) = Arith.neg(a.eval(env))
}

internal class Abs(a: Node) : Unary(a) {
    override fun eval(env: Map<String, Num>) = a.eval(env).let { if (it.double < 0) Arith.neg(it) else it }
}

internal class Percent(a: Node) : Unary(a) {
    override fun eval(env: Map<String, Num>) = Arith.div(a.eval(env), Num.Exact(Rational.of(100)))
}

/** Degrees as radians: 30° is π/6. */
internal class Degrees(a: Node) : Unary(a) {
    override fun eval(env: Map<String, Num>) = Arith.mul(a.eval(env), Num.Approx(PI / 180))
}

internal class Factorial(a: Node) : Unary(a) {
    override fun eval(env: Map<String, Num>): Num {
        val n = a.eval(env).rational?.takeIf { it.isInteger && it.signum >= 0 && it.num.bitLength() < 11 }?.num?.toInt()
            ?: throw CalcException(Failure.UNDEFINED)
        var f = BigInteger.ONE
        for (k in 2..n) f *= BigInteger.valueOf(k.toLong())
        return Num.Exact(Rational.of(f))
    }
}

/** sin, cos, tan (of radians), ln, and log to [base] (10 when not written). */
internal class Call(val name: String, a: Node, val base: Node? = null) : Node() {
    private val arg = a
    override fun unknowns() = arg.unknowns() + (base?.unknowns() ?: emptySet())

    override fun eval(env: Map<String, Num>): Num {
        val x = arg.eval(env)
        val d = x.double
        val value = when (name) {
            "sin" -> kotlin.math.sin(d)
            "cos" -> kotlin.math.cos(d)
            "tan" -> kotlin.math.tan(d).also { if (abs(it) > 1e12) throw CalcException(Failure.UNDEFINED) }
            "cot" -> (1 / kotlin.math.tan(d)).also { if (!it.isFinite() || abs(it) > 1e12) throw CalcException(Failure.UNDEFINED) }
            "arcsin" -> kotlin.math.asin(d)
            "arccos" -> kotlin.math.acos(d)
            "arctan" -> kotlin.math.atan(d)
            "ln" -> log(d)
            "lg" -> log(d) / kotlin.math.ln(10.0)
            "log" -> {
                val b = base?.eval(env)?.double ?: 10.0
                if (b <= 0 || b == 1.0) throw CalcException(Failure.UNDEFINED)
                if (b == 10.0) { if (d <= 0) throw CalcException(Failure.UNDEFINED); kotlin.math.log10(d) } else log(d) / kotlin.math.ln(b)
            }
            "exp" -> kotlin.math.exp(d)
            else -> throw CalcException(Failure.UNREADABLE)
        }
        if (!value.isFinite()) throw CalcException(Failure.UNDEFINED)
        return Num.Approx(value)
    }

    private fun log(d: Double): Double {
        if (d <= 0) throw CalcException(Failure.UNDEFINED)
        return kotlin.math.ln(d)
    }
}

/** Arithmetic on [Num]s: exact while both sides are and the result stays small enough, otherwise in doubles. */
internal object Arith {
    fun add(a: Num, b: Num): Num = if (a is Num.Exact && b is Num.Exact) Num.Exact(a.r + b.r) else Num.Approx(a.double + b.double)

    fun neg(a: Num): Num = if (a is Num.Exact) Num.Exact(-a.r) else Num.Approx(-a.double)

    fun mul(a: Num, b: Num): Num = if (a is Num.Exact && b is Num.Exact) Num.Exact(a.r * b.r) else Num.Approx(a.double * b.double)

    fun div(a: Num, b: Num): Num {
        if (b.double == 0.0 || (b is Num.Exact && b.r.signum == 0)) throw CalcException(Failure.DIVISION_BY_ZERO)
        return if (a is Num.Exact && b is Num.Exact) Num.Exact(a.r / b.r) else Num.Approx(a.double / b.double)
    }

    fun pow(a: Num, b: Num): Num {
        if (a is Num.Exact && b is Num.Exact && b.r.den.bitLength() <= 7 && b.r.num.bitLength() <= 31) {
            val p = b.r.num.toInt()
            val q = b.r.den.toInt()
            val root = if (q == 1) a.r else a.r.root(q)
            if (root != null) root.pow(p)?.let { return Num.Exact(it) }
        }
        val x = a.double
        val y = b.double
        if (x == 0.0 && y < 0) throw CalcException(Failure.DIVISION_BY_ZERO)
        // A negative number to a fraction with an odd denominator, as ∛(−8): the real root.
        val odd = (b as? Num.Exact)?.r?.let { !it.isInteger && it.den.testBit(0) } ?: false
        val value = if (x < 0 && odd) {
            val r = (b as Num.Exact).r
            val magnitude = Math.pow(-x, y)
            if (r.num.testBit(0)) -magnitude else magnitude
        } else {
            Math.pow(x, y)
        }
        if (!value.isFinite()) throw CalcException(Failure.UNDEFINED)
        return Num.Approx(value)
    }
}

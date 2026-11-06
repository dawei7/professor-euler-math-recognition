package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Calculus, series, limits and sums. Derivatives are compared with a difference quotient of the test's own function,
 * antiderivatives differentiated back the same way, definite integrals with the test's own quadrature, series with their
 * coefficients from the textbook formulas, limits with their textbook values, sums with brute force.
 */
internal object WrittenCalculus {

    /** A function of x as typed, with the test's own value of it. */
    class F(val text: String, val exact: ((Q) -> Q)? = null, val f: (Double) -> Double)

    private fun Random.atom(): F = when (nextInt(6)) {
        0 -> F("x") { it }
        1 -> int(2, 5).let { n -> F("x^$n") { x -> Math.pow(x, n.toDouble()) } }
        2 -> int(1, 4).let { a -> F("${a}x") { x -> a * x } }
        3 -> int(1, 5).let { c -> F("(x + $c)") { x -> x + c } }
        4 -> int(1, 3).let { c -> F("(x^2 + $c)") { x -> x * x + c } }
        else -> int(2, 6).let { c -> F("$c") { _ -> c.toDouble() } }
    }

    private fun Random.unary(u: F): F = when (nextInt(8)) {
        0 -> F("sin(${u.text})") { x -> sin(u.f(x)) }
        1 -> F("cos(${u.text})") { x -> cos(u.f(x)) }
        2 -> F("e^(${u.text})") { x -> exp(u.f(x)) }
        3 -> F("ln(${u.text})") { x -> ln(u.f(x)) }
        4 -> F("sqrt(${u.text})") { x -> sqrt(u.f(x)) }
        5 -> F("arctan(${u.text})") { x -> kotlin.math.atan(u.f(x)) }
        6 -> int(2, 3).let { n -> F("(${u.text})^$n") { x -> Math.pow(u.f(x), n.toDouble()) } }
        else -> F("1/(${u.text})") { x -> 1 / u.f(x) }
    }

    private fun Random.function(): F {
        val a = if (nextBoolean()) unary(atom()) else atom()
        val b = if (nextInt(3) == 0) atom() else unary(atom())
        return when (nextInt(5)) {
            0 -> F("${a.text} + ${b.text}") { x -> a.f(x) + b.f(x) }
            1 -> F("${a.text} - (${b.text})") { x -> a.f(x) - b.f(x) }
            2 -> F("(${a.text})(${b.text})") { x -> a.f(x) * b.f(x) }
            3 -> F("(${a.text})/(${b.text})") { x -> a.f(x) / b.f(x) }
            else -> unary(F("(${a.text})") { a.f(it) })
        }
    }

    private val PTS = listOf(0.31, 0.77, 1.23, 1.71, 2.37, 0.53)

    /** The engine's expression in x, equal to [ref] at the points where [ref] is defined (at least three). */
    private fun matches(rel: Double = 1e-6, ref: (Double) -> Double): (Calculation) -> Boolean = { c ->
        val e = resultOf(c)
        e != null && e !is Expr.Mat && agrees(e, listOf("x"), PTS.map { doubleArrayOf(it) }, rel) { (x) -> ref(x) }
    }

    fun calculus(): List<Task> {
        val r = Random(2718)
        val out = ArrayList<Task>()
        // First derivatives of random compositions.
        repeat(300) {
            val g = r.function()
            out += Task("calculus", "derivative(${g.text}, x)", "", judge = matches(1e-6) { x -> steadySlope(x, g.f) })
        }
        // Higher derivatives by the textbook formulas.
        for (n in 2..4) {
            for (m in listOf(3, 5, 7)) out += Task("calculus", "derivative(x^$m, x, $n)", "", judge = matches { x -> (0 until n).fold(1.0) { p, k -> p * (m - k) } * Math.pow(x, (m - n).toDouble()) })
            for (a in 1..3) {
                out += Task("calculus", "derivative(sin(${a}x), x, $n)", "", judge = matches { x -> Math.pow(a.toDouble(), n.toDouble()) * sin(a * x + n * PI / 2) })
                out += Task("calculus", "derivative(e^(${a}x), x, $n)", "", judge = matches { x -> Math.pow(a.toDouble(), n.toDouble()) * exp(a * x) })
                out += Task("calculus", "derivative(cos(${a}x), x, $n)", "", judge = matches { x -> Math.pow(a.toDouble(), n.toDouble()) * cos(a * x + n * PI / 2) })
            }
            out += Task("calculus", "derivative(ln(x), x, $n)", "", judge = matches { x -> (if (n % 2 == 0) -1.0 else 1.0) * (1 until n).fold(1.0) { p, k -> p * k } / Math.pow(x, n.toDouble()) })
            out += Task("calculus", "derivative(x e^x, x, $n)", "", judge = matches { x -> (x + n) * exp(x) })
        }
        // Antiderivatives, differentiated back.
        val integrands = listOf<(Random) -> F>(
            { q -> q.int(0, 5).let { n -> q.int(-3, 3, true).let { a -> F("x^$n e^(${a}x)") { x -> Math.pow(x, n.toDouble()) * exp(a * x) } } } },
            { q -> q.int(1, 4).let { a -> F("x sin(${a}x)") { x -> x * sin(a * x) } } },
            { q -> q.int(1, 4).let { a -> F("x^2 cos(${a}x)") { x -> x * x * cos(a * x) } } },
            { q -> q.int(0, 4).let { n -> F("x^$n ln(x)") { x -> Math.pow(x, n.toDouble()) * ln(x) } } },
            { q -> q.int(1, 9).let { a -> F("1/(x^2 + $a)") { x -> 1 / (x * x + a) } } },
            { q -> q.int(1, 9).let { a -> F("x/(x^2 + $a)") { x -> x / (x * x + a) } } },
            { q -> q.int(2, 6).let { n -> F("sin(x)^$n") { x -> Math.pow(sin(x), n.toDouble()) } } },
            { q -> q.int(2, 5).let { n -> F("cos(x)^$n") { x -> Math.pow(cos(x), n.toDouble()) } } },
            { q -> q.int(2, 6).let { b -> F("1/((x + 1)(x + $b))") { x -> 1 / ((x + 1) * (x + b)) } } },
            { q -> q.int(1, 3).let { a -> q.int(1, 3).let { b -> F("e^(${a}x) cos(${b}x)") { x -> exp(a * x) * cos(b * x) } } } },
            { q -> q.int(1, 3).let { a -> q.int(1, 3).let { b -> F("e^(${a}x) sin(${b}x)") { x -> exp(a * x) * sin(b * x) } } } },
            { q -> q.int(3, 9).let { a -> F("1/sqrt($a - x^2)") { x -> 1 / sqrt(a - x * x) } } },
            { q -> q.int(1, 5).let { a -> F("x sqrt(x^2 + $a)") { x -> x * sqrt(x * x + a) } } },
            { q -> q.int(2, 5).let { a -> q.int(-4, 4).let { b -> q.int(2, 6).let { n ->
                // ∫₀^x (at + b)ⁿ dt = ((ax + b)ⁿ⁺¹ − bⁿ⁺¹)/(a(n + 1))
                F("(${a}x ${if (b < 0) "-" else "+"} ${abs(b)})^$n", { x -> ((q(a.toLong()) * x + q(b.toLong())).pow(n + 1) - q(b.toLong()).pow(n + 1)) / q(a * (n + 1L)) }) { x -> Math.pow(a * x + b, n.toDouble()) }
            } } } },
            { q -> q.int(1, 4).let { a -> F("1/(${a}x + 1)") { x -> 1 / (a * x + 1) } } },
            { _ -> F("tan(x)") { x -> kotlin.math.tan(x) } },
            { _ -> F("1/cos(x)^2") { x -> 1 / (cos(x) * cos(x)) } },
            { q -> q.int(1, 4).let { a -> F("sin(${a}x) cos(${a}x)") { x -> sin(a * x) * cos(a * x) } } },
            { q -> List(4) { q.int(-6, 6).toLong() }.let { c ->
                // ∫₀^x Σ cₖ tᵏ dt = Σ cₖ x^(k+1)/(k + 1)
                F(polyText(c), { x -> c.withIndex().fold(Q.ZERO) { acc, (k, ck) -> acc + q(ck) * x.pow(k + 1) / q(k + 1L) } }) { x -> polyAt(c, x) }
            } },
            { q -> q.int(1, 3).let { a -> F("x e^(-x^2/$a)") { x -> x * exp(-x * x / a) } } },
            { _ -> F("arctan(x)") { x -> kotlin.math.atan(x) } },
            { _ -> F("ln(x)^2") { x -> ln(x) * ln(x) } },
            { q -> q.int(2, 5).let { a -> F("(2x + 1)/(x^2 + x + $a)") { x -> (2 * x + 1) / (x * x + x + a) } } },
            { q -> q.int(1, 4).let { a -> F("1/(x^2 - ${a * a})") { x -> 1 / (x * x - a * a) } } },
        )
        repeat(240) {
            val g = integrands[it % integrands.size](r)
            out += Task("calculus", "integral(${g.text}, x)", "", judge = { c ->
                val o = c.outcome as? Outcome.Expression
                // A polynomial's antiderivative exactly: F(q) − F(0) is ∫₀^q f, in the test's own fractions.
                val poly = o?.result?.let { e -> exactAt(e, mapOf("x" to q(1, 3))) } != null && g.exact != null
                if (poly) QPOINTS.all { x -> exactAt(o!!.result, mapOf("x" to x))!! - exactAt(o.result, mapOf("x" to Q.ZERO))!! == g.exact!!(x) } && o!!.plusC
                else o != null && o.plusC && PTS.filter { x -> g.f(x).isFinite() && abs(g.f(x)) < 1e6 && steadySlope(x) { t -> at(o.result, mapOf("x" to t)) }.isFinite() }.let { pts ->
                    pts.size >= 3 && pts.all { x -> close(steadySlope(x) { t -> at(o.result, mapOf("x" to t)) }, g.f(x), 1e-6, 1e-8) }
                }
            })
        }
        // Definite integrals: polynomials exactly (the test's own fractions), the rest against its own quadrature.
        repeat(60) {
            val coefficients = List(r.int(1, 4)) { r.int(-6, 6).toLong() }
            val a = r.int(-3, 2); val b = a + r.int(1, 4)
            // ∫ₐᵇ Σ cₖ xᵏ = Σ cₖ (bᵏ⁺¹ − aᵏ⁺¹)/(k + 1)
            val want = coefficients.withIndex().fold(Q.ZERO) { acc, (k, c) -> acc + q(c) * (q(b.toLong()).pow(k + 1) - q(a.toLong()).pow(k + 1)) / q(k + 1L) }
            out += Task("calculus", "integral(${polyText(coefficients)}, x, $a, $b)", "$want", judge = isExact(want))
        }
        val definite = listOf(
            Triple("sin(x)", "0, pi", 2.0), Triple("cos(x)", "0, pi/2", 1.0), Triple("e^x", "0, 1", E - 1), Triple("1/x", "1, e", 1.0),
            Triple("x e^x", "0, 1", 1.0), Triple("ln(x)", "1, e", 1.0), Triple("1/(1 + x^2)", "0, 1", PI / 4), Triple("sqrt(1 - x^2)", "-1, 1", PI / 2),
            Triple("sin(x)^2", "0, pi", PI / 2), Triple("x sin(x)", "0, pi", PI), Triple("1/sqrt(x)", "0, 1", 2.0), Triple("ln(x)", "0, 1", -1.0),
            Triple("e^(-x)", "0, infinity", 1.0), Triple("1/x^2", "1, infinity", 1.0), Triple("1/x^3", "1, infinity", 0.5),
            Triple("x e^(-x)", "0, infinity", 1.0), Triple("x^2 e^(-x)", "0, infinity", 2.0), Triple("e^(-x^2)", "-infinity, infinity", sqrt(PI)),
            Triple("1/(1 + x^2)", "-infinity, infinity", PI), Triple("e^(-2x)", "0, infinity", 0.5), Triple("1/(x^2 + 4)", "0, infinity", PI / 4),
            Triple("x^3 e^(-x)", "0, infinity", 6.0), Triple("1/sqrt(1 - x^2)", "0, 1", PI / 2), Triple("sin(x)/x", "1, 2", integrate(1.0, 2.0) { sin(it) / it }),
            Triple("e^(-x^2)", "0, 1", integrate(0.0, 1.0) { exp(-it * it) }), Triple("sqrt(1 + x^3)", "0, 2", integrate(0.0, 2.0) { sqrt(1 + it * it * it) }),
            Triple("cos(x^2)", "0, 1", integrate(0.0, 1.0) { cos(it * it) }), Triple("e^x/x", "1, 2", integrate(1.0, 2.0) { exp(it) / it }),
            Triple("x^2 sin(x)", "0, pi", PI * PI - 4), Triple("e^x cos(x)", "0, pi", -(exp(PI) + 1) / 2), Triple("1/(x(x + 1))", "1, infinity", ln(2.0)),
            Triple("ln(x)/x^2", "1, infinity", 1.0), Triple("x/(x^2 + 1)^2", "0, infinity", 0.5), Triple("cos(x)", "0, 2pi", 0.0),
        )
        for ((f, bounds, want) in definite) out += Task("calculus", "integral($f, x, $bounds)", fmt(want), judge = isNumber(want, 1e-8))
        for ((f, bounds) in listOf("1/x" to "1, infinity", "1/x" to "0, 1", "1/sqrt(x)" to "1, infinity", "e^x" to "0, infinity", "1/x^2" to "-1, 1", "x" to "0, infinity")) {
            out += Task("calculus", "integral($f, x, $bounds)", "diverges", fails = setOf(Failure.DIVERGES)) { false }
        }
        return out
    }

    // -----------------------------------------------------------------------------------------------------------------------
    // Series: coefficients from the textbook formulas.

    private fun factorial(k: Int): Double = (2..k).fold(1.0) { p, j -> p * j }

    fun series(): List<Task> {
        val out = ArrayList<Task>()
        class S(val text: String, val point: String, val a: Double, val coefficient: (Int) -> Double)
        val list = ArrayList<S>()
        for (c in listOf(1, 2, -1, 3)) {
            val ct = if (c == 1) "" else if (c == -1) "-" else "$c"
            list += S("e^(${ct}x)", "0", 0.0) { k -> Math.pow(c.toDouble(), k.toDouble()) / factorial(k) }
            list += S("sin(${ct}x)", "0", 0.0) { k -> if (k % 2 == 0) 0.0 else Math.pow(c.toDouble(), k.toDouble()) / factorial(k) * (if (k % 4 == 1) 1 else -1) }
            list += S("cos(${ct}x)", "0", 0.0) { k -> if (k % 2 == 1) 0.0 else Math.pow(c.toDouble(), k.toDouble()) / factorial(k) * (if (k % 4 == 0) 1 else -1) }
            val plus = if (c < 0) "1 - ${if (c == -1) "" else "${-c}"}x" else "1 + ${ct}x"
            val minus = if (c < 0) "1 + ${if (c == -1) "" else "${-c}"}x" else "1 - ${ct}x"
            list += S("ln($plus)", "0", 0.0) { k -> if (k == 0) 0.0 else (if (k % 2 == 1) 1.0 else -1.0) * Math.pow(c.toDouble(), k.toDouble()) / k }
            list += S("1/($minus)", "0", 0.0) { k -> Math.pow(c.toDouble(), k.toDouble()) }
            list += S("sinh(${ct}x)", "0", 0.0) { k -> if (k % 2 == 0) 0.0 else Math.pow(c.toDouble(), k.toDouble()) / factorial(k) }
            list += S("cosh(${ct}x)", "0", 0.0) { k -> if (k % 2 == 1) 0.0 else Math.pow(c.toDouble(), k.toDouble()) / factorial(k) }
        }
        for ((rt, rv) in listOf("1/2" to 0.5, "-1/2" to -0.5, "1/3" to 1.0 / 3, "3/2" to 1.5, "-2" to -2.0)) {
            list += S("(1 + x)^($rt)", "0", 0.0) { k -> (0 until k).fold(1.0) { p, j -> p * (rv - j) } / factorial(k) }
        }
        list += S("arctan(x)", "0", 0.0) { k -> if (k % 2 == 0) 0.0 else (if (k % 4 == 1) 1.0 else -1.0) / k }
        list += S("e^x sin(x)", "0", 0.0) { k -> Math.pow(sqrt(2.0), k.toDouble()) * sin(k * PI / 4) / factorial(k) }
        list += S("e^x cos(x)", "0", 0.0) { k -> Math.pow(sqrt(2.0), k.toDouble()) * cos(k * PI / 4) / factorial(k) }
        list += S("1/(1 + x^2)", "0", 0.0) { k -> if (k % 2 == 1) 0.0 else (if (k % 4 == 0) 1.0 else -1.0) }
        list += S("tan(x)", "0", 0.0) { k -> mapOf(1 to 1.0, 3 to 1.0 / 3, 5 to 2.0 / 15, 7 to 17.0 / 315)[k] ?: 0.0 }
        list += S("x/(e^x - 1)", "0", 0.0) { k -> mapOf(0 to 1.0, 1 to -0.5, 2 to 1.0 / 12, 4 to -1.0 / 720, 6 to 1.0 / 30240)[k] ?: 0.0 }
        // About other points: e^x at a, ln x at 1, √x at 4, 1/x at a, sin x at π.
        for (a in listOf(1, 2, -1)) list += S("e^x", "$a", a.toDouble()) { k -> exp(a.toDouble()) / factorial(k) }
        list += S("ln(x)", "1", 1.0) { k -> if (k == 0) 0.0 else (if (k % 2 == 1) 1.0 else -1.0) / k }
        list += S("sqrt(x)", "4", 4.0) { k -> 2 * (0 until k).fold(1.0) { p, j -> p * (0.5 - j) } / factorial(k) / Math.pow(4.0, k.toDouble()) }
        for (a in listOf(1, 2, -3)) list += S("1/x", "$a", a.toDouble()) { k -> (if (k % 2 == 0) 1.0 else -1.0) / Math.pow(a.toDouble(), k + 1.0) }
        list += S("sin(x)", "pi", PI) { k -> if (k % 2 == 0) 0.0 else -(if (k % 4 == 1) 1.0 else -1.0) / factorial(k) }
        list += S("cos(x)", "pi/2", PI / 2) { k -> if (k % 2 == 0) 0.0 else -(if (k % 4 == 1) 1.0 else -1.0) / factorial(k) }
        for (s in list) for (n in listOf(4, 6, 7)) {
            out += Task("series", "taylor(${s.text}, x, ${s.point}, $n)", "", judge = { c ->
                val o = c.outcome as? Outcome.Expression
                val terms = o?.series
                if (o == null || terms == null) false else {
                    val shift = o.seriesIn?.let { at(it, mapOf("x" to 0.0)) } ?: 0.0
                    val got = terms.associate { (k, j) -> j to at(k, emptyMap()) }
                    close(-shift, s.a, 1e-12) && (0..n).all { k -> close(got[k] ?: 0.0, s.coefficient(k), 1e-9, 1e-12) } && got.keys.all { it <= n }
                }
            })
        }
        return out
    }

    // -----------------------------------------------------------------------------------------------------------------------
    // Limits: textbook values.

    fun limits(): List<Task> {
        val out = ArrayList<Task>()
        fun lim(f: String, at: String, want: Double) {
            out += Task("limits", "limit($f, x, $at)", fmt(want), judge = { c ->
                if (want.isInfinite()) (c.outcome as? Outcome.Expression)?.result?.let { e -> e.hasInfinity() && (at(e, emptyMap()).let { it.isInfinite() && (it > 0) == (want > 0) } || (e == Expr.INF) == (want > 0)) } == true
                else isNumber(want, 1e-9)(c)
            })
        }
        for (a in 1..4) for (b in 1..3) {
            lim("sin(${a}x)/(${b}x)", "0", a.toDouble() / b)
            lim("tan(${a}x)/(${b}x)", "0", a.toDouble() / b)
            lim("(1 - cos(${a}x))/x^2", "0", a * a / 2.0)
            lim("(e^(${a}x) - 1)/(${b}x)", "0", a.toDouble() / b)
            lim("ln(1 + ${a}x)/(${b}x)", "0", a.toDouble() / b)
            lim("(1 + $a/x)^($b x)", "infinity", exp(a.toDouble() * b))
        }
        for (n in 2..5) for (a in listOf(1, 2, -1, 3)) lim("(x^$n - ${Math.pow(a.toDouble(), n.toDouble()).toLong().let { if (it < 0) "($it)" else "$it" }})/(x - ${if (a < 0) "($a)" else "$a"})", "$a", n * Math.pow(a.toDouble(), n - 1.0))
        for (p in 1..4) for (q in 1..3) {
            lim("(${p}x^2 + 3x - 1)/(${q}x^2 - x + 5)", "infinity", p.toDouble() / q)
            lim("(${p}x + 7)/(${q}x^2 + 1)", "infinity", 0.0)
            lim("(${p}x^3 - x)/(${q}x^3 + 2x^2)", "-infinity", p.toDouble() / q)
        }
        for (a in 1..6) {
            lim("sqrt(x^2 + ${a}x) - x", "infinity", a / 2.0)
            lim("($a^x - 1)/x", "0", ln(a.toDouble()))
            lim("x^$a e^(-x)", "infinity", 0.0)
            lim("ln(x)/x^$a", "infinity", 0.0)
            lim("x^$a ln(x)", "0+", 0.0)
            lim("(x + $a)^2", "$a", (2.0 * a) * (2.0 * a))
            lim("arctan(${a}x)/x", "0", a.toDouble())
            lim("(sqrt(x + ${a * a}) - $a)/x", "0", 1.0 / (2 * a))
        }
        lim("x^x", "0+", 1.0)
        lim("(1 + 1/x)^x", "infinity", E)
        lim("x sin(1/x)", "infinity", 1.0)
        lim("(sin(x) - x)/x^3", "0", -1.0 / 6)
        lim("(e^x - 1 - x)/x^2", "0", 0.5)
        lim("(tan(x) - x)/x^3", "0", 1.0 / 3)
        lim("1/x", "0+", Double.POSITIVE_INFINITY)
        lim("1/x", "0-", Double.NEGATIVE_INFINITY)
        lim("e^x", "infinity", Double.POSITIVE_INFINITY)
        lim("ln(x)", "0+", Double.NEGATIVE_INFINITY)
        for ((f, at) in listOf("1/x" to "0", "sin(1/x)" to "0", "sin(x)" to "infinity", "|x|/x" to "0")) {
            out += Task("limits", "limit($f, x, $at)", "no limit", fails = setOf(Failure.NO_LIMIT)) { false }
        }
        return out
    }

    // -----------------------------------------------------------------------------------------------------------------------
    // Sums: brute force, and the textbook series.

    fun sums(): List<Task> {
        val r = Random(1729)
        val out = ArrayList<Task>()
        repeat(80) {
            val c = List(r.int(1, 4)) { r.int(-5, 5).toLong() }
            val a = r.int(0, 5); val b = a + r.int(0, 40)
            val want = (a..b).fold(Q.ZERO) { acc, k -> acc + Q.of(BigInteger.valueOf(c.foldRight(0L) { ci, s -> s * k + ci })) }
            out += Task("sums", "sum(${polyText(c, "k")}, k, $a, $b)", "$want", judge = isExact(want))
        }
        repeat(30) {
            val a = r.int(1, 5); val b = a + r.int(1, 30); val m = r.int(1, 3)
            val want = (a..b).fold(Q.ZERO) { acc, k -> acc + Q.of(1, k.toLong() * (k + m)) }
            out += Task("sums", "sum(1/(k(k + $m)), k, $a, $b)", "$want", judge = isExact(want))
        }
        // Closed forms in n, against the sums of their first terms.
        for (p in 0..6) for ((lo, coefficient) in listOf(1 to 1, 1 to 2, 0 to 3)) {
            out += Task("sums", "sum($coefficient k^$p, k, $lo, n)", "", judge = { c ->
                val e = resultOf(c)
                e != null && (1..12).all { n -> close(at(e, mapOf("n" to n.toDouble())), (lo..n).sumOf { k -> coefficient * Math.pow(k.toDouble(), p.toDouble()) }.let { if (p == 0 && lo == 0) it else it }, 1e-10) }
            })
        }
        for (q in listOf(2, 3, -2, 5)) out += Task("sums", "sum(${if (q < 0) "($q)" else "$q"}^k, k, 0, n)", "", judge = { c ->
            val e = resultOf(c)
            e != null && (0..10).all { n -> close(at(e, mapOf("n" to n.toDouble())), (0..n).sumOf { k -> Math.pow(q.toDouble(), k.toDouble()) }, 1e-10) }
        })
        // Geometric series: c/(1 − q), exactly.
        repeat(30) {
            val qq = Q.of(r.int(-8, 8).toLong(), r.int(9, 12).toLong()); val c = r.int(1, 9).toLong()
            if (qq.isZero) return@repeat
            val start = r.int(0, 2)
            val want = q(c) * qq.pow(start) / (Q.ONE - qq)
            out += Task("sums", "sum($c (${qq})^k, k, $start, infinity)", "$want", judge = isExact(want))
        }
        val series = listOf(
            Triple("1/k^2", "1", PI * PI / 6), Triple("1/k^4", "1", Math.pow(PI, 4.0) / 90), Triple("1/k^6", "1", Math.pow(PI, 6.0) / 945),
            Triple("(-1)^(k + 1)/k", "1", ln(2.0)), Triple("1/k!", "0", E), Triple("2^k/k!", "0", exp(2.0)), Triple("1/(k(k + 1))", "1", 1.0),
            Triple("1/(4k^2 - 1)", "1", 0.5), Triple("(-1)^k/(2k + 1)", "0", PI / 4), Triple("(-1)^(k + 1)/k^2", "1", PI * PI / 12),
            Triple("k/2^k", "1", 2.0), Triple("k^2/2^k", "1", 6.0), Triple("1/(k^2 - 1)", "2", 0.75), Triple("1/(k(k + 2))", "1", 0.75),
            Triple("1/(2k - 1)^2", "1", PI * PI / 8), Triple("(-1)^k/k!", "0", 1 / E), Triple("1/k^3", "1", 1.2020569031595942),
            Triple("1/2^k", "1", 1.0), Triple("1/(k^2 + 3k + 2)", "0", 1.0),
        )
        for ((f, lo, want) in series) out += Task("sums", "sum($f, k, $lo, infinity)", fmt(want), judge = isNumber(want, 1e-9))
        for (f in listOf("1/k", "k", "1/sqrt(k)", "(-1)^k", "k!/2^k", "ln(k)", "1/(2k + 1)")) {
            out += Task("sums", "sum($f, k, 1, infinity)", "diverges", fails = setOf(Failure.DIVERGES)) { false }
        }
        return out
    }
}

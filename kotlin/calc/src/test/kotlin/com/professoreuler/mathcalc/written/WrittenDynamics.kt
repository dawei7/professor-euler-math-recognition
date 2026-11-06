package com.professoreuler.mathcalc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Differential equations, transforms and numerical methods. A solution of a differential equation is put back into it with
 * the test's own difference quotients (and its initial values checked); transforms against the textbook's table; numerical
 * methods recomputed by the test with the same rule and step.
 */
internal object WrittenDynamics {

    /** f″(x) by the five-point stencil. */
    private fun second(x: Double, f: (Double) -> Double): Double {
        val h = 1e-3 * max(1.0, abs(x))
        return (-f(x + 2 * h) + 16 * f(x + h) - 30 * f(x) + 16 * f(x - h) - f(x - 2 * h)) / (12 * h * h)
    }

    /**
     * An equation F(x, y, y′, y″) = 0, its order and its initial values (x₀, y₀, y′₀): the engine's solution put back. With no
     * initial values, the general solution is put back for several values of its constants.
     */
    private fun solves(order: Int, residual: (x: Double, y: Double, d1: Double, d2: Double) -> Double, x0: Double? = null,
        y0: Double? = null, d0: Double? = null, points: List<Double> = listOf(0.35, 0.8, 1.3, 1.9)): (Calculation) -> Boolean = { c ->
        val o = c.outcome as? Outcome.OdeSolved
        val sol = if (y0 != null) o?.particular else o?.general
        if (o == null || sol == null) false else {
            val rr = Random(5)
            val explicit = sol.explicit
            if (explicit != null) {
                (0 until (if (y0 != null) 1 else 4)).all {
                    val env = sol.constants.associateWith { rr.nextDouble(-2.0, 2.0) }
                    val y = { x: Double -> at(explicit, env + (o.x to x)) }
                    val scale = points.maxOf { abs(y(it)) + abs(slope(it, y)) + 1.0 }
                    points.all { x ->
                        val v = y(x)
                        !v.isFinite() || abs(residual(x, v, slope(x, y), if (order == 2) second(x, y) else 0.0)) <= 1e-6 * scale
                    } && points.count { x -> y(x).isFinite() } >= 3 &&
                        (y0 == null || close(y(x0!!), y0, 1e-9, 1e-10)) && (d0 == null || close(slope(x0!!, y), d0, 1e-7, 1e-8))
                }
            } else if (order == 1 && y0 == null) {
                // 0 = F(x, y): along the curves, y′ = −F_x/F_y must be the equation's slope.
                val f = sol.implicit!!
                (0 until 3).all {
                    val env = sol.constants.associateWith { rr.nextDouble(-2.0, 2.0) }
                    listOf(0.4 to 0.7, 1.1 to 1.3, 1.7 to 0.45).all { (x, y) ->
                        val fx = slope(x) { t -> at(f, env + mapOf(o.x to t, o.y to y)) }
                        val fy = slope(y) { t -> at(f, env + mapOf(o.x to x, o.y to t)) }
                        !fx.isFinite() || !fy.isFinite() || abs(fy) < 1e-9 || abs(residual(x, y, -fx / fy, 0.0)) <= 1e-6 * (1 + abs(fx / fy))
                    }
                }
            } else false
        }
    }

    fun odes(): List<Task> {
        val r = Random(1693)
        val out = ArrayList<Task>()
        // y′ = a·y + b, y(0) = y₀.
        repeat(60) { i ->
            val a = r.int(-3, 3, nonzero = true); val b = r.int(-5, 5); val y0 = r.int(-4, 4)
            val eq = "y' = ${a}y ${if (b < 0) "-" else "+"} ${abs(b)}"
            out += if (i % 3 == 0) Task("odes", "dsolve($eq)", "", judge = solves(1, { _, y, d1, _ -> d1 - a * y - b }))
            else Task("odes", "dsolve($eq, y(0) = $y0)", "", judge = solves(1, { _, y, d1, _ -> d1 - a * y - b }, 0.0, y0.toDouble()))
        }
        // y′ + (k/x)·y = xᵐ, y(1) = c.
        repeat(30) {
            val k = r.int(1, 3); val m = r.int(0, 3); val c0 = r.int(-3, 3)
            out += Task("odes", "dsolve(y' + $k y/x = x^$m, y(1) = $c0)", "", judge = solves(1, { x, y, d1, _ -> d1 + k * y / x - Math.pow(x, m.toDouble()) }, 1.0, c0.toDouble()))
        }
        // Separable equations.
        val separable = listOf(
            Triple("y' = x y", { x: Double, y: Double -> x * y }, 1.0), Triple("y' = y^2", { _: Double, y: Double -> y * y }, 0.5),
            Triple("y' = x/y", { x: Double, y: Double -> x / y }, 1.0), Triple("y' = x^2 y", { x: Double, y: Double -> x * x * y }, 2.0),
            Triple("y' = -2x y", { x: Double, y: Double -> -2 * x * y }, 1.0), Triple("y' = e^x y", { x: Double, y: Double -> exp(x) * y }, 1.0),
            Triple("y' = y/x", { x: Double, y: Double -> y / x }, 1.0), Triple("y' = (1 + y^2)", { _: Double, y: Double -> 1 + y * y }, 0.0),
            Triple("y' = cos(x) y", { x: Double, y: Double -> cos(x) * y }, 1.0), Triple("y' = x (1 + y)", { x: Double, y: Double -> x * (1 + y) }, 0.0),
        )
        for ((eq, f, y0) in separable) {
            val x0 = if (eq == "y' = y/x") 1.0 else 0.0
            out += Task("odes", "dsolve($eq, y(${fmt(x0)}) = ${fmt(y0)})", "", judge = solves(1, { x, y, d1, _ -> d1 - f(x, y) }, x0, y0, points = listOf(0.15, 0.3, 0.45, 0.6).map { it + x0 }))
            out += Task("odes", "dsolve($eq)", "", judge = solves(1, { x, y, d1, _ -> d1 - f(x, y) }, points = listOf(0.15, 0.3, 0.45, 0.6).map { it + x0 }))
        }
        // y″ + b·y′ + c·y = 0 with roots chosen: real, double, complex.
        repeat(70) { i ->
            val (b, c) = when (i % 3) {
                0 -> { val p = r.int(-3, 3); val q = r.int(-3, 3); -(p + q) to p * q }
                1 -> { val p = r.int(-3, 3); -2 * p to p * p }
                else -> { val p = r.int(-2, 2); val w = r.int(1, 3); -2 * p to p * p + w * w }
            }
            val y0 = r.int(-3, 3); val d0 = r.int(-3, 3)
            val eq = "y'' ${if (b < 0) "-" else "+"} ${abs(b)}y' ${if (c < 0) "-" else "+"} ${abs(c)}y = 0"
            out += if (i % 4 == 0) Task("odes", "dsolve($eq)", "", judge = solves(2, { _, y, d1, d2 -> d2 + b * d1 + c * y }))
            else Task("odes", "dsolve($eq, y(0) = $y0, y'(0) = $d0)", "", judge = solves(2, { _, y, d1, d2 -> d2 + b * d1 + c * y }, 0.0, y0.toDouble(), d0.toDouble()))
        }
        // With a right side: a polynomial, an exponential, a sine.
        repeat(45) { i ->
            val b = r.int(-3, 3); val c = r.int(1, 4)
            val (rhs, g) = when (i % 3) {
                0 -> r.int(1, 4).let { k -> "${k}x" to { x: Double -> k * x } }
                1 -> r.int(1, 2).let { k -> "e^(${k}x)" to { x: Double -> exp(k * x) } }
                else -> r.int(1, 3).let { k -> "sin(${k}x)" to { x: Double -> sin(k * x) } }
            }
            val eq = "y'' ${if (b < 0) "-" else "+"} ${abs(b)}y' + ${c}y = $rhs"
            out += Task("odes", "dsolve($eq, y(0) = 1, y'(0) = 0)", "", judge = solves(2, { x, y, d1, d2 -> d2 + b * d1 + c * y - g(x) }, 0.0, 1.0, 0.0))
        }
        // Cauchy–Euler x²y″ + a·x·y′ + b·y = 0, with whole exponents.
        repeat(20) {
            val p = r.int(-2, 3); val q = r.int(-2, 3)
            // r² + (a − 1)r + b = 0 with roots p, q: a = 1 − (p + q), b = p·q.
            val a = 1 - (p + q); val bb = p * q
            out += Task("odes", "dsolve(x^2 y'' ${if (a < 0) "-" else "+"} ${abs(a)}x y' ${if (bb < 0) "-" else "+"} ${abs(bb)}y = 0)", "x^$p, x^$q",
                judge = solves(2, { x, y, d1, d2 -> x * x * d2 + a * x * d1 + bb * y }, points = listOf(0.6, 1.1, 1.7, 2.3)))
        }
        return out
    }

    // -----------------------------------------------------------------------------------------------------------------------
    // Transforms: the textbook's table, by linearity.

    private val SS = listOf(3.7, 5.3, 8.1)

    fun transforms(): List<Task> {
        val r = Random(1808)
        val out = ArrayList<Task>()
        class Pair2(val f: String, val ft: (Double) -> Double, val image: String, val Fs: (Double) -> Double)
        val table = ArrayList<Pair2>()
        for (n in 0..4) {
            val fact = (2..n).fold(1.0) { p, k -> p * k }
            table += Pair2(if (n == 0) "1" else "t^$n", { t -> Math.pow(t, n.toDouble()) }, "$fact/s^${n + 1}", { s -> fact / Math.pow(s, n + 1.0) })
        }
        for (a in listOf(-2, -1, 1, 2)) {
            table += Pair2("e^(${a}t)", { t -> exp(a * t) }, "1/(s - $a)", { s -> 1 / (s - a) })
            table += Pair2("t e^(${a}t)", { t -> t * exp(a * t) }, "", { s -> 1 / ((s - a) * (s - a)) })
            table += Pair2("t^2 e^(${a}t)", { t -> t * t * exp(a * t) }, "", { s -> 2 / Math.pow(s - a, 3.0) })
        }
        for (b in 1..3) {
            table += Pair2("sin(${b}t)", { t -> sin(b * t) }, "", { s -> b / (s * s + b * b) })
            table += Pair2("cos(${b}t)", { t -> cos(b * t) }, "", { s -> s / (s * s + b * b) })
            table += Pair2("t sin(${b}t)", { t -> t * sin(b * t) }, "", { s -> 2 * b * s / Math.pow(s * s + b * b, 2.0) })
            table += Pair2("t cos(${b}t)", { t -> t * cos(b * t) }, "", { s -> (s * s - b * b) / Math.pow(s * s + b * b, 2.0) })
            table += Pair2("sinh(${b}t)", { t -> kotlin.math.sinh(b * t) }, "", { s -> b / (s * s - b * b) })
            table += Pair2("cosh(${b}t)", { t -> kotlin.math.cosh(b * t) }, "", { s -> s / (s * s - b * b) })
            for (a in listOf(-1, 1, 2)) {
                table += Pair2("e^(${a}t) sin(${b}t)", { t -> exp(a * t) * sin(b * t) }, "", { s -> b / ((s - a) * (s - a) + b * b) })
                table += Pair2("e^(${a}t) cos(${b}t)", { t -> exp(a * t) * cos(b * t) }, "", { s -> (s - a) / ((s - a) * (s - a) + b * b) })
            }
        }
        fun laplace(f: String, F: (Double) -> Double) {
            out += Task("transforms", "laplace($f)", "", judge = { c -> resultOf(c)?.let { e -> agrees(e, listOf("s"), SS.map { doubleArrayOf(it) }, 1e-9) { (s) -> F(s) } } == true })
        }
        for (p in table) laplace(p.f, p.Fs)
        // Linear combinations of two.
        repeat(40) {
            val a = table[r.nextInt(table.size)]; val b = table[r.nextInt(table.size)]
            val k = r.int(-3, 3, nonzero = true)
            laplace("${a.f} ${if (k < 0) "-" else "+"} ${abs(k)}(${b.f})") { s -> a.Fs(s) + k * b.Fs(s) }
        }
        // Inverse transforms: the f of the table back, at points t.
        val inverses = listOf(
            "1/s" to { _: Double -> 1.0 }, "1/s^2" to { t: Double -> t }, "2/s^3" to { t: Double -> t * t }, "1/(s - 3)" to { t: Double -> exp(3 * t) },
            "1/(s + 2)" to { t: Double -> exp(-2 * t) }, "1/(s^2 + 1)" to { t: Double -> sin(t) }, "s/(s^2 + 4)" to { t: Double -> cos(2 * t) },
            "3/(s^2 + 9)" to { t: Double -> sin(3 * t) }, "1/(s(s + 1))" to { t: Double -> 1 - exp(-t) }, "1/((s - 1)(s - 2))" to { t: Double -> exp(2 * t) - exp(t) },
            "1/(s - 1)^2" to { t: Double -> t * exp(t) }, "(s - 1)/((s - 1)^2 + 4)" to { t: Double -> exp(t) * cos(2 * t) },
            "2/((s + 1)^2 + 4)" to { t: Double -> exp(-t) * sin(2 * t) }, "1/(s^2 - 1)" to { t: Double -> kotlin.math.sinh(t) },
            "s/(s^2 - 4)" to { t: Double -> kotlin.math.cosh(2 * t) }, "(2s + 3)/(s^2 + 3s + 2)" to { t: Double -> exp(-t) + exp(-2 * t) },
            "1/(s^2 (s + 1))" to { t: Double -> t - 1 + exp(-t) }, "1/(s^2 + 2s + 2)" to { t: Double -> exp(-t) * sin(t) },
            "6/s^4" to { t: Double -> t * t * t }, "1/(s(s^2 + 1))" to { t: Double -> 1 - cos(t) }, "s/(s^2 + 1)^2" to { t: Double -> t * sin(t) / 2 },
            "5/(s^2 + 25)" to { t: Double -> sin(5 * t) }, "(s + 2)/(s^2 + 4s + 5)" to { t: Double -> exp(-2 * t) * cos(t) },
            "4/(s(s + 2))" to { t: Double -> 2 - 2 * exp(-2 * t) }, "1/(s + 1)^3" to { t: Double -> t * t * exp(-t) / 2 },
        )
        for ((F, f) in inverses) out += Task("transforms", "invlaplace($F)", "", judge = { c ->
            resultOf(c)?.let { e -> agrees(e, listOf("t"), listOf(0.3, 0.9, 1.4, 2.2).map { doubleArrayOf(it) }, 1e-9) { (t) -> f(t) } } == true
        })
        repeat(30) {
            // a/(s − p) + b/(s − q): a·e^(pt) + b·e^(qt).
            val p = r.int(-4, 4); var qq = r.int(-4, 4); if (qq == p) qq += 1
            val a = r.int(-5, 5, nonzero = true); val b = r.int(-5, 5, nonzero = true)
            // (a(s − q) + b(s − p)) / ((s − p)(s − q))
            val num = polyText(listOf((-a * qq - b * p).toLong(), (a + b).toLong()), "s")
            val den = polyText(listOf((p * qq).toLong(), (-(p + qq)).toLong(), 1L), "s")
            if (a + b == 0 && -a * qq - b * p == 0) return@repeat
            out += Task("transforms", "invlaplace(($num)/($den))", "", judge = { c ->
                resultOf(c)?.let { e -> agrees(e, listOf("t"), listOf(0.3, 0.9, 1.4).map { doubleArrayOf(it) }, 1e-9) { (t) -> a * exp(p * t) + b * exp(qq * t) } } == true
            })
        }
        // Fourier series on [−L, L]: aₙ and bₙ against the test's own integrals, n = 0..4.
        val fouriers = listOf<Pair<String, (Double) -> Double>>(
            "x" to { x -> x }, "x^2" to { x -> x * x }, "abs(x)" to { x -> abs(x) }, "x^3" to { x -> x * x * x }, "x + x^2" to { x -> x + x * x },
            "e^x" to { x -> exp(x) }, "sin(x)^2" to { x -> sin(x) * sin(x) }, "x cos(x)" to { x -> x * cos(x) }, "cos(x/2)" to { x -> cos(x / 2) },
            "1 - x^2" to { x -> 1 - x * x }, "2x - 1" to { x -> 2 * x - 1 }, "x sin(x)" to { x -> x * sin(x) },
        )
        for ((f, g) in fouriers) for (half in listOf("", "1", "2")) {
            val L = if (half.isEmpty()) PI else half.toDouble()
            out += Task("transforms", if (half.isEmpty()) "fourier($f)" else "fourier($f, $half)", "", judge = { c ->
                val o = (c.outcome as? Outcome.FourierSeries)?.series
                o != null && close(at(o.a0, emptyMap()), integrate(-L, L, 1e-13) { g(it) } / L, 1e-7, 1e-9) && (1..4).all { n ->
                    val an = integrate(-L, L, 1e-13) { g(it) * cos(n * PI * it / L) } / L
                    val bn = integrate(-L, L, 1e-13) { g(it) * sin(n * PI * it / L) } / L
                    close(at(o.an, mapOf("n" to n.toDouble())), an, 1e-7, 1e-9) && close(at(o.bn, mapOf("n" to n.toDouble())), bn, 1e-7, 1e-9)
                }
            })
        }
        return out
    }

    // -----------------------------------------------------------------------------------------------------------------------
    // Numerical methods, recomputed.

    fun numerics(): List<Task> {
        val r = Random(1669)
        val out = ArrayList<Task>()
        fun table(c: Calculation) = (c.outcome as? Outcome.NumericTable)?.table
        val functions = listOf<Pair<String, (Double) -> Double>>(
            "x^2 - 2" to { x -> x * x - 2 }, "x^3 - x - 1" to { x -> x * x * x - x - 1 }, "cos(x) - x" to { x -> cos(x) - x },
            "e^x - 3" to { x -> exp(x) - 3 }, "x^3 - 2x - 5" to { x -> x * x * x - 2 * x - 5 }, "ln(x) - 1" to { x -> ln(x) - 1 },
            "x e^x - 1" to { x -> x * exp(x) - 1 }, "sin(x) - x/2" to { x -> sin(x) - x / 2 }, "x^5 - 3" to { x -> Math.pow(x, 5.0) - 3 },
            "tan(x) - 1" to { x -> kotlin.math.tan(x) - 1 }, "x^2 - 7" to { x -> x * x - 7 }, "2^x - 5" to { x -> Math.pow(2.0, x) - 5 },
        )
        for ((f, g) in functions) for (x0 in listOf(1.0, 1.5, 2.5, 0.8)) {
            out += Task("numerics", "newton($f, ${fmt(x0)})", "", judge = { c ->
                val v = table(c)?.result
                v != null && abs(g(v)) < 1e-9 * (1 + abs(slope(v, g)))
            })
        }
        for ((f, g) in functions) {
            // An interval with a sign change, found by the test.
            val (a, b) = (-10 until 10).map { it.toDouble() to it + 1.0 }.firstOrNull { (p, q) -> g(p).isFinite() && g(q).isFinite() && g(p) * g(q) < 0 && abs(q - p) <= 1 && zeros(p, q) { g(it) }.isNotEmpty() } ?: continue
            out += Task("numerics", "bisection($f, ${fmt(a)}, ${fmt(b)})", "", judge = { c ->
                val v = table(c)?.result
                v != null && v >= a && v <= b && zeros(a, b) { g(it) }.any { abs(it - v) < 1e-9 }
            })
        }
        // Trapezoidal and Simpson's rules with 64 strips.
        val integrands = listOf<Pair<String, (Double) -> Double>>("x^2" to { x -> x * x }, "e^(-x^2)" to { x -> exp(-x * x) }, "sin(x)" to { x -> sin(x) },
            "1/(1 + x^2)" to { x -> 1 / (1 + x * x) }, "sqrt(x)" to { x -> sqrt(x) }, "x^3 - x" to { x -> x * x * x - x }, "ln(1 + x)" to { x -> ln(1 + x) },
            "e^x" to { x -> exp(x) }, "cos(x^2)" to { x -> cos(x * x) })
        for ((f, g) in integrands) for ((a, b) in listOf(0 to 1, 0 to 2, 1 to 3)) for (simpson in listOf(false, true)) {
            val n = 64
            val h = (b - a).toDouble() / n
            var sum = g(a.toDouble()) + g(b.toDouble())
            for (k in 1 until n) sum += g(a + k * h) * (if (simpson) (if (k % 2 == 1) 4 else 2) else 2)
            val want = if (simpson) sum * h / 3 else sum * h / 2
            out += Task("numerics", "${if (simpson) "simpson" else "trapezoid"}($f, x, $a, $b)", "", judge = { c -> table(c)?.result?.let { close(it, want, 1e-12) } == true })
        }
        // Euler's method and Runge–Kutta, h = 0.1, ten steps.
        val slopes = listOf<Pair<String, (Double, Double) -> Double>>("y" to { _, y -> y }, "x + y" to { x, y -> x + y }, "x y" to { x, y -> x * y },
            "-2y" to { _, y -> -2 * y }, "x - y" to { x, y -> x - y }, "y^2" to { _, y -> y * y }, "sin(x) + y" to { x, y -> sin(x) + y },
            "x^2 - y" to { x, y -> x * x - y }, "1 + y^2" to { _, y -> 1 + y * y }, "e^x - y" to { x, y -> exp(x) - y })
        for ((f, g) in slopes) for (y0 in listOf(1, 0, 2)) for (rk in listOf(false, true)) {
            if (f == "y^2" && y0 == 2) continue
            if (f == "1 + y^2" && y0 != 0) continue
            var x = 0.0; var y = y0.toDouble(); val h = 0.1
            repeat(10) {
                y = if (!rk) y + h * g(x, y) else {
                    val k1 = g(x, y); val k2 = g(x + h / 2, y + h / 2 * k1); val k3 = g(x + h / 2, y + h / 2 * k2); val k4 = g(x + h, y + h * k3)
                    y + h / 6 * (k1 + 2 * k2 + 2 * k3 + k4)
                }
                x += h
            }
            val want = y
            out += Task("numerics", "${if (rk) "rungekutta" else "euler"}(y' = $f, y(0) = $y0)", "", judge = { c -> table(c)?.result?.let { close(it, want, 1e-10) } == true })
        }
        return out
    }
}

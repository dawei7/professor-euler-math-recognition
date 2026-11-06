package com.professoreuler.mathcalc

import kotlin.math.abs

/**
 * The numerical methods of a first course, each as the table it builds step by step: Newton's method and bisection for
 * f(x) = 0, Euler's method and the classical Runge–Kutta method for y′ = f(x, y), the trapezoidal and Simpson's rules for an
 * integral. The table is what the method gives (doubles), not an exact answer; where an exact value is known its error is shown.
 */
object Numerics {
    enum class Method { NEWTON, BISECTION, EULER, RUNGE_KUTTA, TRAPEZOID, SIMPSON, DORMAND_PRINCE }

    /** A method's table: its [columns] (names in LaTeX) and [rows]; its [result], and the [exact] value where it is known. */
    class Table(val method: Method, val columns: List<String>, val rows: List<List<Double>>, val result: Double?, val exact: Double? = null)

    private fun f(e: Expr, x: String, v: Double) = Numeric.eval(e, mapOf(x to v))

    /** Newton's method from [x0]: x ← x − f(x)/f′(x), until the step is below 10⁻¹² or 30 steps. */
    fun newton(e: Expr, x: Expr.Sym, x0: Double): Table {
        val d = Calculus.tidy(Calculus.derivative(e, x))
        val rows = ArrayList<List<Double>>()
        var v = x0
        var result: Double? = null
        for (k in 0 until 30) {
            Budget.check()
            val fv = f(e, x.name, v)
            val dv = f(d, x.name, v)
            rows += listOf(k.toDouble(), v, fv)
            if (!fv.isFinite() || !dv.isFinite() || dv == 0.0) break
            val next = v - fv / dv
            if (abs(next - v) <= 1e-12 * maxOf(1.0, abs(v))) { rows += listOf(k + 1.0, next, f(e, x.name, next)); result = next; break }
            v = next
        }
        return Table(Method.NEWTON, listOf("n", "x_n", "f(x_n)"), rows, result)
    }

    /** Bisection on [a, b] where f changes sign: the half with the sign change, until it is below 10⁻¹⁰ or 60 steps. */
    fun bisection(e: Expr, x: Expr.Sym, a0: Double, b0: Double): Table? {
        var a = minOf(a0, b0)
        var b = maxOf(a0, b0)
        var fa = f(e, x.name, a)
        if (fa * f(e, x.name, b) > 0) return null
        val rows = ArrayList<List<Double>>()
        for (k in 0 until 60) {
            Budget.check()
            val m = (a + b) / 2
            val fm = f(e, x.name, m)
            rows += listOf(k.toDouble(), a, b, m, fm)
            if (fm == 0.0 || (b - a) / 2 < 1e-10) return Table(Method.BISECTION, listOf("n", "a", "b", "m", "f(m)"), rows, m)
            if (fa * fm < 0) b = m else { a = m; fa = fm }
        }
        return Table(Method.BISECTION, listOf("n", "a", "b", "m", "f(m)"), rows, (a + b) / 2)
    }

    /** Euler's method or RK4 for y′ = [slope](x, y) from (x₀, y₀) with step h, n steps; the exact y(x) where known. */
    fun ode(slope: Expr, x: String, y: String, x0: Double, y0: Double, h: Double, steps: Int, rungeKutta: Boolean, exact: Expr? = null): Table {
        fun s(xv: Double, yv: Double) = Numeric.eval(slope, mapOf(x to xv, y to yv))
        val rows = ArrayList<List<Double>>()
        var xv = x0
        var yv = y0
        fun row() {
            val e = exact?.let { Numeric.eval(it, mapOf(x to xv)) }
            rows += if (e != null) listOf(xv, yv, e) else listOf(xv, yv)
        }
        row()
        for (k in 0 until steps) {
            Budget.check()
            yv = if (rungeKutta) {
                val k1 = s(xv, yv)
                val k2 = s(xv + h / 2, yv + h * k1 / 2)
                val k3 = s(xv + h / 2, yv + h * k2 / 2)
                val k4 = s(xv + h, yv + h * k3)
                yv + h * (k1 + 2 * k2 + 2 * k3 + k4) / 6
            } else yv + h * s(xv, yv)
            xv = x0 + (k + 1) * h
            row()
        }
        val columns = if (exact != null) listOf("x_k", "y_k", "y(x_k)") else listOf("x_k", "y_k")
        return Table(if (rungeKutta) Method.RUNGE_KUTTA else Method.EULER, columns, rows, yv, exact?.let { Numeric.eval(it, mapOf(x to xv)) })
    }

    /** The trapezoidal or Simpson's rule with n = 2, 4, 8, … up to [most] pieces: each row n and its value; the exact value. */
    fun quadrature(e: Expr, x: Expr.Sym, a: Double, b: Double, simpson: Boolean, most: Int = 64, exact: Double? = null): Table {
        val rows = ArrayList<List<Double>>()
        var n = 2
        var last = Double.NaN
        while (n <= most) {
            Budget.check()
            val h = (b - a) / n
            var sum = f(e, x.name, a) + f(e, x.name, b)
            for (k in 1 until n) sum += f(e, x.name, a + k * h) * (if (simpson) (if (k % 2 == 1) 4 else 2) else 2)
            last = if (simpson) sum * h / 3 else sum * h / 2
            rows += if (exact != null) listOf(n.toDouble(), last, last - exact) else listOf(n.toDouble(), last)
            n *= 2
        }
        val columns = if (exact != null) listOf("n", "T_n", "T_n - I") else listOf("n", "T_n")
        val named = if (simpson) columns.map { it.replace("T_n", "S_n") } else columns
        return Table(if (simpson) Method.SIMPSON else Method.TRAPEZOID, named, rows, last, exact)
    }
}

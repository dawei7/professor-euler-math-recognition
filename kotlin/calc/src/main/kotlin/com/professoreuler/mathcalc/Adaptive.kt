package com.professoreuler.mathcalc

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

/**
 * The Dormand–Prince method RK5(4)7M (J. R. Dormand and P. J. Prince, 1980) with adaptive steps for y′ = f(x, y): seven stages a
 * step (the last one the first of the next), a solution of order 5 carried on and one of order 4 beside it, whose difference
 * estimates the step's error; a step is taken where that error is within the tolerance and the next step is chosen from it
 * (0.9·h·(1/err)^(1/5), at most five times and at least a fifth of h), as Hairer, Nørsett and Wanner describe it, with their
 * starting step. Its table is the method's own approximation, as every numerical method's.
 */
object Adaptive {
    private val C = doubleArrayOf(0.0, 1.0 / 5, 3.0 / 10, 4.0 / 5, 8.0 / 9, 1.0, 1.0)
    private val A = arrayOf(
        doubleArrayOf(),
        doubleArrayOf(1.0 / 5),
        doubleArrayOf(3.0 / 40, 9.0 / 40),
        doubleArrayOf(44.0 / 45, -56.0 / 15, 32.0 / 9),
        doubleArrayOf(19372.0 / 6561, -25360.0 / 2187, 64448.0 / 6561, -212.0 / 729),
        doubleArrayOf(9017.0 / 3168, -355.0 / 33, 46732.0 / 5247, 49.0 / 176, -5103.0 / 18656),
        doubleArrayOf(35.0 / 384, 0.0, 500.0 / 1113, 125.0 / 192, -2187.0 / 6784, 11.0 / 84),
    )

    /** The fifth-order weights (the last row of [A]) less the fourth-order ones: the error estimate's. */
    private val E = doubleArrayOf(
        35.0 / 384 - 5179.0 / 57600, 0.0, 500.0 / 1113 - 7571.0 / 16695, 125.0 / 192 - 393.0 / 640,
        -2187.0 / 6784 + 92097.0 / 339200, 11.0 / 84 - 187.0 / 2100, -1.0 / 40,
    )

    /** The tolerance of each step: relative, and absolute where y is near 0. */
    const val TOLERANCE = 1e-10
    const val ABSOLUTE = 1e-12

    /** At most this many steps, taken or not. */
    private const val MOST = 5000

    /**
     * From (x₀, y₀) to [x1] (forwards or backwards): a row for each step taken, x, the step h that reached it, y and the error
     * estimate of that step; the exact y(x) beside it where it is known.
     */
    fun dormandPrince(slope: Expr, x: String, y: String, x0: Double, y0: Double, x1: Double, exact: Expr? = null): Numerics.Table {
        val compiled = Plot.compile2(rename(slope, x, y))
        fun f(xv: Double, yv: Double) = compiled(xv, yv)
        if (!f(x0, y0).isFinite() || x1 == x0) throw CalcException(Failure.UNDEFINED)
        val direction = sign(x1 - x0)
        val span = abs(x1 - x0)
        fun scale(v: Double) = ABSOLUTE + TOLERANCE * abs(v)
        // The starting step (Hairer, Nørsett, Wanner, II.4).
        var k1 = f(x0, y0)
        val d0 = abs(y0) / scale(y0)
        val d1 = abs(k1) / scale(y0)
        val h0 = if (d0 < 1e-5 || d1 < 1e-5) 1e-6 else 0.01 * d0 / d1
        val probe = f(x0 + direction * h0, y0 + direction * h0 * k1)
        val d2 = abs(probe - k1) / scale(y0) / h0
        val h1 = if (max(d1, d2) <= 1e-15) max(1e-6, h0 * 1e-3) else (0.01 / max(d1, d2)).pow(1.0 / 5)
        var h = min(min(100 * h0, h1), span)
        val rows = ArrayList<List<Double>>()
        fun row(xv: Double, step: Double, yv: Double, err: Double) {
            val e = exact?.let { Numeric.eval(it, mapOf(x to xv)) }
            rows += if (e != null && e.isFinite()) listOf(xv, step, yv, err, e) else listOf(xv, step, yv, err)
        }
        var xv = x0
        var yv = y0
        row(xv, 0.0, yv, 0.0)
        val k = DoubleArray(7)
        var tries = 0
        while (direction * (x1 - xv) > 1e-14 * max(1.0, abs(x1))) {
            Budget.check()
            if (++tries > MOST) throw CalcException(Failure.TOO_LONG)
            val last = direction * (x1 - xv) <= h * (1 + 1e-12)
            val step = if (last) abs(x1 - xv) else h
            val hs = direction * step
            k[0] = k1
            for (s in 1..6) {
                var sum = 0.0
                for (q in 0 until s) sum += A[s][q] * k[q]
                k[s] = f(xv + C[s] * hs, yv + hs * sum)
            }
            // The fifth-order solution is the last stage's argument; the error the difference of the two orders.
            var next = yv
            for (q in 0 until 6) next += hs * A[6][q] * k[q]
            var errEstimate = 0.0
            for (q in 0 until 7) errEstimate += hs * E[q] * k[q]
            if (!next.isFinite() || !errEstimate.isFinite() || k.any { !it.isFinite() }) {
                // A singularity ahead: smaller steps, and none smaller than the doubles can tell.
                h = step / 4
                if (h < 1e-12 * max(1.0, abs(xv))) throw CalcException(Failure.UNDEFINED)
                continue
            }
            val err = abs(errEstimate) / (ABSOLUTE + TOLERANCE * max(abs(yv), abs(next)))
            if (err <= 1.0) {
                xv = if (last) x1 else xv + hs
                yv = next
                k1 = k[6]
                row(xv, step, yv, abs(errEstimate))
            }
            val factor = if (err == 0.0) 5.0 else (0.9 * err.pow(-1.0 / 5)).coerceIn(0.2, 5.0)
            h = step * factor
            if (h < 1e-12 * max(1.0, abs(xv))) throw CalcException(Failure.UNDEFINED)
        }
        val columns = listOf("x_k", "h_k", "y_k", "\\varepsilon_k") + if (rows.all { it.size == 5 } && exact != null) listOf("y(x_k)") else emptyList()
        val table = if (columns.size == 5) rows else rows.map { it.take(4) }
        val atEnd = exact?.let { Numeric.eval(it, mapOf(x to xv)) }?.takeIf { it.isFinite() }
        return Numerics.Table(Numerics.Method.DORMAND_PRINCE, columns, table, yv, atEnd)
    }

    /** [e] with [x] and [y] as the letters x and y that compiled functions read. */
    private fun rename(e: Expr, x: String, y: String): Expr {
        val h = Expr.Sym("\u0001h")
        val v = Expr.Sym("\u0001v")
        return e.substitute(Expr.Sym(x), h).substitute(Expr.Sym(y), v).substitute(h, Expr.Sym("x")).substitute(v, Expr.Sym("y"))
    }
}

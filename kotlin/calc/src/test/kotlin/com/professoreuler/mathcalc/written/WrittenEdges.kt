package com.professoreuler.mathcalc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The edges where the corpus found wrong answers, in families: values whose terms cancel in doubles (their references by the
 * test's stable formulas: expm1, log1p, 1/(√(a² + 1) + a)), values a hair from a simple fraction, oscillating limits, alternating
 * series (η(p) = (1 − 2¹⁻ᵖ)ζ(p)), and functions asked for what the formula does not hold.
 */
internal object WrittenEdges {
    fun all(): List<Task> {
        val out = ArrayList<Task>()
        // Cancellation.
        for (k in 3..12) {
            val a = Math.pow(10.0, k.toDouble())
            out += Task("exact values", "sqrt(10^${2 * k} + 1) - 10^$k", "", judge = isNumber(1 / (sqrt(a * a + 1) + a)))
            out += Task("exact values", "e^(10^(-$k)) - 1", "", judge = isNumber(Math.expm1(1 / a)))
            out += Task("exact values", "ln(1 + 10^(-$k))", "", judge = isNumber(Math.log1p(1 / a)))
            out += Task("exact values", "1 - cos(10^(-$k))", "", judge = isNumber(2 * Math.pow(kotlin.math.sin(0.5 / a), 2.0)))
            out += Task("solving", "solve(x^2 - ${2 * a.toLong()}x + 1 = 0, x)", "", judge = isRoots(listOf(1 / (a + sqrt(a * a - 1)), a + sqrt(a * a - 1)), 1e-9))
        }
        // A hair from a simple fraction: never shown as that fraction.
        for (k in 8..20) {
            out += Task("exact values", "tanh($k)", "", judge = isNumber(1 - 2 / (exp(2.0 * k) + 1)))
            out += Task("exact values", "1/2 + e^(-${5 * k})", "", judge = isNumber(0.5 + exp(-5.0 * k)))
        }
        for ((x, v) in listOf(4 to 1.541725790028002e-8, 5 to 1.5374597944280349e-12, 6 to 2.1519736712498913e-17, 7 to 4.183825607779414e-23, 10 to 2.088487583762545e-45)) {
            out += Task("exact values", "erfc($x)", "$v", judge = isNumber(v))
            out += Task("exact values", "erf($x)", "1 - $v", judge = isNumber(1 - v))
        }
        // Oscillation: no limit, or a bounded factor squeezed to 0.
        for (p in 1..4) {
            out += Task("limits", "limit((-1)^k/k^$p, k, infinity)", "0", judge = isNumber(0.0))
            out += Task("limits", "limit(sin(${p}x)/x^$p, x, infinity)", "0", judge = isNumber(0.0))
            out += Task("limits", "limit(cos(x^2)/x^$p, x, infinity)", "0", judge = isNumber(0.0))
            out += Task("limits", "limit(x^$p sin(1/x), x, 0)", "0", judge = isNumber(0.0))
            out += Task("limits", "limit(e^(-${p}x) cos(x), x, infinity)", "0", judge = isNumber(0.0))
        }
        for (f in listOf("(-1)^k", "cos(pi k)", "sin(pi k/2)", "(-1)^k k/(k + 1)", "(-2)^k/2^k")) {
            out += Task("limits", "limit($f, k, infinity)", "no limit", fails = setOf(Failure.NO_LIMIT)) { false }
        }
        // Alternating series: η(p) = (1 − 2¹⁻ᵖ)ζ(p).
        val zeta = mapOf(2 to PI * PI / 6, 3 to 1.2020569031595942, 4 to Math.pow(PI, 4.0) / 90, 5 to 1.0369277551433699, 6 to Math.pow(PI, 6.0) / 945)
        for ((p, z) in zeta) {
            out += Task("sums", "sum((-1)^(k + 1)/k^$p, k, 1, infinity)", "", judge = isNumber((1 - Math.pow(2.0, 1.0 - p)) * z))
            out += Task("sums", "sum((-1)^k/k^$p, k, 1, infinity)", "", judge = isNumber(-(1 - Math.pow(2.0, 1.0 - p)) * z))
        }
        out += Task("sums", "sum((-1)^k/(2k + 1)^3, k, 0, infinity)", "π³/32", judge = isNumber(Math.pow(PI, 3.0) / 32))
        out += Task("sums", "sum((-1)^k/(k + 1), k, 0, infinity)", "ln 2", judge = isNumber(kotlin.math.ln(2.0)))
        out += Task("sums", "sum((-1)^k/(k + 2), k, 0, infinity)", "1 − ln 2", judge = isNumber(1 - kotlin.math.ln(2.0)))
        // Functions asked for what the formula does not hold.
        for (c in listOf("7", "5 + 3", "pi", "ln(2)", "sqrt(3)")) {
            out += Task("calculus", "derivative($c, x)", "0", judge = isNumber(0.0))
            out += Task("calculus", "integral($c, x)", "$c·x", judge = { calc ->
                val o = calc.outcome as? Outcome.Expression
                o != null && o.plusC && agrees(o.result, listOf("x"), points1()) { (x) -> x * (numberOf(Reports.of(TextInput.read(c)).result)?.re ?: Double.NaN) }
            })
            out += Task("transforms", "laplace($c)", "$c/s", judge = { calc ->
                resultOf(calc)?.let { e -> agrees(e, listOf("s"), listOf(1.3, 2.7, 4.1).map { doubleArrayOf(it) }) { (s) -> (numberOf(Reports.of(TextInput.read(c)).result)?.re ?: Double.NaN) / s } } == true
            })
        }
        for (n in 1..4) {
            out += Task("calculus", "derivative(y^$n, x)", "0", judge = isNumber(0.0))
            out += Task("calculus", "derivative(sin(y) + $n, x)", "0", judge = isNumber(0.0))
        }
        for ((f, why) in listOf("inverse(x)" to Failure.ARGUMENTS, "det([[1, 2, 3], [4, 5, 6]])" to Failure.SIZES, "eigenvalues([[1, 2]])" to Failure.SIZES,
                "solve(x^2 = 4, y)" to Failure.ARGUMENTS, "laplace(x^2)" to Failure.ARGUMENTS)) {
            out += Task("linear algebra".takeIf { "[[" in f || "inverse" in f } ?: "calculus", f, why.name, fails = setOf(why)) { false }
        }
        return out
    }
}

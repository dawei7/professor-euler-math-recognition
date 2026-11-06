package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * The mathematics of machine learning ([Learning], [Decisions]): every answer against a value known independently of the engine,
 * a textbook value or one worked out here another way (in doubles from kotlin.math, by brute force over every policy, by
 * iterating an equation until it settles).
 */
class LearningTest {
    private fun calc(text: String): Calculation = Reports.of(TextInput.read(text)).result
    private fun answer(text: String): String = calc(text).let { r -> if (r.outcome is Outcome.Failed) (r.outcome as Outcome.Failed).failure.name else answerOf(r) }

    /** A number's value: of its exact form where there is one. */
    private fun value(text: String): Double = when (val o = calc(text).outcome) {
        is Outcome.Value -> o.exact?.let { Numeric.value(it) } ?: o.value.double
        is Outcome.Expression -> Numeric.value(o.result) ?: error("$text: ${o.result}")
        else -> error("$text: $o")
    }

    /** A vector's entries as numbers. */
    private fun entries(text: String): List<Double> = when (val o = calc(text).outcome) {
        is Outcome.Expression -> (o.result as Expr.Mat).rows.flatten().map { Numeric.value(it)!! }
        else -> error("$text: $o")
    }

    private fun close(expected: Double, actual: Double, what: String, tolerance: Double = 1e-13) =
        assertTrue("$what: $actual, not $expected", abs(expected - actual) <= tolerance * maxOf(1.0, abs(expected)))

    private fun sigma(x: Double) = 1 / (1 + exp(-x))

    // ---------------------------------------------------------------------------------------------------------------------

    @Test
    fun activationFunctions() {
        assertEquals("1/2 = 0.5", answer("sigmoid(0)"))
        assertEquals("3/4 = 0.75", answer("sigmoid(ln(3))"))
        close(sigma(2.0), value("sigmoid(2)"), "σ(2)")
        close(sigma(-3.5), value("sigmoid(-3.5)"), "σ(−3.5)")
        // Far out it is written so that no exponential overflows: σ(800) is 1 to every digit, σ(−40) = e⁻⁴⁰/(1 + e⁻⁴⁰).
        close(1.0, value("sigmoid(800)"), "σ(800)")
        close(exp(-40.0) / (1 + exp(-40.0)), value("sigmoid(-40)"), "σ(−40)", 1e-12)
        assertEquals("0", answer("relu(-3)"))
        assertEquals("5/2 = 2.5", answer("relu(5/2)"))
        assertEquals("0", answer("relu(0)"))
        assertEquals("-1/50 = -0.02", answer("leakyrelu(-2)"))
        assertEquals("-0.3", answer("leakyrelu(-3, 0.1)"))
        assertEquals("4", answer("leakyrelu(4, 0.1)"))
        assertEquals("ln(2) ≈ 0.6931471806", answer("softplus(0)"))
        close(ln(1 + exp(1.5)), value("softplus(1.5)"), "softplus(1.5)")
        close(1000.0, value("softplus(1000)"), "softplus(1000)")
        assertEquals("0", answer("gelu(0)"))
        // GELU(1) = 1·Φ(1), Φ(1) = 0.8413447460685429 (the standard normal table).
        close(0.8413447460685429, value("gelu(1)"), "GELU(1)", 1e-12)
        close(-2 * (1 - 0.9772498680518208), value("gelu(-2)"), "GELU(−2)", 1e-12)
        // Of a vector, entry by entry.
        assertEquals("(1/2, 3/4)", answer("sigmoid([0, ln(3)])"))
        assertEquals("(0, 2)", answer("relu([-1, 2])"))
        assertEquals("ARGUMENTS", answer("sigmoid(1, 2)"))
        assertEquals("ARGUMENTS", answer("relu()"))
    }

    /** The derivatives come out of the formulas: σ′ = σ(1 − σ), softplus′ = σ, relu′ = 1 for x > 0 and 0 for x < 0. */
    @Test
    fun derivativesOfTheActivations() {
        val ds = (calc("derivative(sigmoid(x), x)").outcome as Outcome.Expression).result
        val dp = (calc("derivative(softplus(x), x)").outcome as Outcome.Expression).result
        val dr = (calc("derivative(relu(x), x)").outcome as Outcome.Expression).result
        val dg = (calc("derivative(gelu(x), x)").outcome as Outcome.Expression).result
        for (x in listOf(-3.2, -0.7, 0.4, 1.9, 5.0)) {
            val env = mapOf("x" to x)
            close(sigma(x) * (1 - sigma(x)), Numeric.eval(ds, env), "σ′($x)")
            close(sigma(x), Numeric.eval(dp, env), "softplus′($x)")
            close(if (x > 0) 1.0 else 0.0, Numeric.eval(dr, env), "relu′($x)")
            // GELU′(x) = Φ(x) + x·φ(x), from a central difference of x·Φ(x) here.
            fun gelu(t: Double) = t * 0.5 * (1 + Numeric.function(Func.ERF, listOf(t / sqrt(2.0))))
            close((gelu(x + 1e-5) - gelu(x - 1e-5)) / 2e-5, Numeric.eval(dg, env), "GELU′($x)", 1e-8)
        }
        assertEquals("ln(|e^x + 1|) + C", answer("integral(sigmoid(x), x)"))
    }

    @Test
    fun functionsOfVectors() {
        // softmax(1, 2, 3) = eᶻ/Σeᶻ in doubles.
        val z = listOf(1.0, 2.0, 3.0)
        val s = z.sumOf { exp(it) }
        entries("softmax([1, 2, 3])").forEachIndexed { k, v -> close(exp(z[k]) / s, v, "softmax₍${k + 1}₎") }
        assertEquals("(1/(1 + e + e^2), e/(1 + e + e^2), e^2/(1 + e + e^2))", answer("softmax([1, 2, 3])"))
        assertEquals("(1/2, 1/2)", answer("softmax([5, 5])"))
        // Adding the same number to every entry changes nothing; a matrix row by row.
        entries("softmax([101, 102, 103])").forEachIndexed { k, v -> close(exp(z[k]) / s, v, "shifted softmax₍${k + 1}₎") }
        val rows = entries("softmax([[1, 2], [3, 3]])")
        close(1 / (1 + exp(1.0)), rows[0], "row 1"); close(0.5, rows[2], "row 2")
        // With a letter, the first entry of softmax(x, 0) is σ(x).
        val first = ((calc("softmax([x, 0])").outcome as Outcome.Expression).result as Expr.Mat).rows[0][0]
        for (x in listOf(-2.0, 0.3, 4.0)) close(sigma(x), Numeric.eval(first, mapOf("x" to x)), "softmax(x, 0)₁ at $x")
        assertEquals("ARGUMENTS", answer("softmax(1)"))
        assertEquals("ARGUMENTS", answer("softmax(1, 2, 3)"))

        close(ln(z.sumOf { exp(it) }), value("logsumexp([1, 2, 3])"), "LSE(1, 2, 3)")
        // Stably: e¹⁰⁰⁰ overflows a double, the shifted form does not.
        close(1001 + ln(1 + exp(-1.0)), value("logsumexp([1000, 1001])"), "LSE(1000, 1001)")
        assertEquals("1001 + ln(1 + 1/e) ≈ 1001.313262", answer("logsumexp([1000, 1001])"))
        assertEquals("ln(2) ≈ 0.6931471806", answer("logsumexp([0, 0])"))

        close(32 / (sqrt(14.0) * sqrt(77.0)), value("cosinesimilarity([1, 2, 3], [4, 5, 6])"), "cos(u, v)")
        assertEquals("√2/2 ≈ 0.7071067812", answer("cosinesimilarity([1, 0], [1, 1])"))
        assertEquals("-1", answer("cosinesimilarity([1, 2], [-2, -4])"))
        assertEquals("0", answer("cosinesimilarity([1, 0], [0, 3])"))
        assertEquals("DIVISION_BY_ZERO", answer("cosinesimilarity([0, 0], [1, 1])"))
        assertEquals("SIZES", answer("cosinesimilarity([1, 2], [1, 2, 3])"))
    }

    @Test
    fun theNormsOfAVector() {
        val v = listOf(num(3), num(-4))
        assertEquals(num(7), Learning.pNorm(v, Expr.ONE))
        assertEquals(num(5), Learning.pNorm(v, Expr.TWO))
        assertEquals(num(4), Learning.pNorm(v, Expr.INF))
        close(Math.cbrt(91.0), Numeric.value(Learning.pNorm(v, num(3)))!!, "L3")
    }

    /** −Σ p ln p (or log to the base given) worked out here in doubles. */
    private fun h(p: List<Double>, base: Double = Math.E) = -p.filter { it > 0 }.sumOf { it * ln(it) } / ln(base)

    @Test
    fun informationMeasures() {
        assertEquals("3/2 = 1.5", answer("entropy([1/2, 1/4, 1/4], 2)"))
        assertEquals("3·ln(2)/2 ≈ 1.039720771", answer("entropy([1/2, 1/4, 1/4])"))
        assertEquals("-2/3 + log_2(3) ≈ 0.9182958341", answer("entropy([1/3, 2/3], 2)"))
        close(h(listOf(0.2, 0.8)), value("entropy([0.2, 0.8])"), "H(0.2, 0.8)")
        close(h(listOf(0.1, 0.2, 0.3, 0.4), 2.0), value("entropy([0.1, 0.2, 0.3, 0.4], 2)"), "H in bits")
        assertEquals("2", answer("entropy([[1/4, 1/4], [1/4, 1/4]], 2)"))
        assertEquals("0", answer("entropy([1, 0])"))
        assertEquals("1", answer("entropy([1/5, 1/5, 1/5, 1/5, 1/5], 5)"))
        assertEquals("NOT_DISTRIBUTION", answer("entropy([0.5, 0.6])"))
        assertEquals("NOT_DISTRIBUTION", answer("entropy([-0.5, 1.5])"))
        assertEquals("UNDEFINED", answer("entropy([1/2, 1/2], 1)"))
        assertEquals("ARGUMENTS", answer("entropy(1/2)"))
        // The binary entropy function, a formula in p: 1 bit at p = 1/2.
        val binary = (calc("entropy([p, 1 - p], 2)").outcome as Outcome.Expression).result
        for (p in listOf(0.1, 0.5, 0.77)) close(h(listOf(p, 1 - p), 2.0), Numeric.eval(binary, mapOf("p" to p)), "H₂($p)")

        // Cross-entropy: H(p, q) = −Σ p log q.
        assertEquals("2 - log_2(3)/2 ≈ 1.20751875", answer("crossentropy([1/2, 1/2], [1/4, 3/4], 2)"))
        val logits = listOf(2.0, 1.0, 0.1)
        close(-ln(exp(1.0) / logits.sumOf { exp(it) }), value("crossentropy([0, 1, 0], softmax([2, 1, 0.1]))"), "the loss of a one-hot label")
        assertEquals("DIVERGES", answer("crossentropy([1/2, 1/2], [1, 0])"))
        assertEquals("SIZES", answer("crossentropy([1/2, 1/2], [1/3, 1/3, 1/3])"))

        // The Kullback–Leibler divergence: Σ p log(p/q) ≥ 0, 0 exactly where p = q, and not symmetric.
        assertEquals("1 - log_2(3)/2 ≈ 0.2075187496", answer("kldivergence([1/2, 1/2], [1/4, 3/4], 2)"))
        close(0.25 * ln(0.5) + 0.75 * ln(1.5), value("kldivergence([1/4, 3/4], [1/2, 1/2])"), "D(p‖q)")
        close(0.5 * ln(2.0) + 0.5 * ln(2.0 / 3), value("kldivergence([1/2, 1/2], [1/4, 3/4])"), "D(q‖p)")
        assertEquals("0", answer("kldivergence([0.3, 0.7], [0.3, 0.7])"))
        assertEquals("DIVERGES", answer("kldivergence([1/2, 1/2], [1, 0])"))
        // q may be 0 where p is 0.
        close(ln(2.0), value("kldivergence([1, 0], [1/2, 1/2])"), "D with a zero of p")

        // Mutual information: H(X) + H(Y) − H(X, Y).
        assertEquals("1", answer("mutualinformation([[1/2, 0], [0, 1/2]], 2)"))
        assertEquals("0", answer("mutualinformation([[1/4, 1/4], [1/4, 1/4]])"))
        assertEquals("0", answer("mutualinformation([[0.08, 0.12], [0.32, 0.48]])"))
        val joint = listOf(0.3, 0.2, 0.1, 0.4)
        close(h(listOf(0.5, 0.5)) + h(listOf(0.4, 0.6)) - h(joint), value("mutualinformation([[0.3, 0.2], [0.1, 0.4]])"), "I(X; Y)")
        assertEquals("SIZES", answer("mutualinformation([1/2, 1/2])"))
        assertEquals("NOT_DISTRIBUTION", answer("mutualinformation([[0.5, 0.5], [0.5, 0.5]])"))
    }

    @Test
    fun gradientDescent() {
        // On x², x ← x − η·2x = (1 − 2η)x: from 1 with η = 1/4, xₖ = 2⁻ᵏ.
        val half = calc("gradientdescent(x^2, 1, 0.25, 10)").outcome as Outcome.Descent
        assertEquals(11, half.table.rows.size)
        half.table.rows.forEach { r -> close(Math.pow(0.5, r[0]), r[1], "x_${r[0].toInt()}") }
        assertEquals(Expr.ZERO, half.minimum!!.at.getValue("x"))
        // On (x − 3)², xₖ = 3 − 3·0.8ᵏ.
        val shifted = calc("gradientdescent((x - 3)^2, 0, 0.1, 50)").outcome as Outcome.Descent
        close(3 - 3 * Math.pow(0.8, 50.0), shifted.point!![0], "x₅₀")
        assertEquals(num(3), shifted.minimum!!.at.getValue("x"))
        // Two letters, in alphabetical order: x ← 0.9x, y ← (1 − 20·0.05)y = 0.
        val two = calc("gradientdescent(x^2 + 10y^2, [1, 1], 0.05, 100)").outcome as Outcome.Descent
        close(Math.pow(0.9, 100.0), two.point!![0], "x₁₀₀")
        assertEquals(0.0, two.point!![1], 0.0)
        assertEquals(listOf("x", "y"), two.letters)
        // η = 1/2 on x² + y² reaches the minimum in one step and stops there.
        val one = calc("gradientdescent(x^2 + y^2, [1, 2], 0.5, 5)").outcome as Outcome.Descent
        assertTrue(one.settled)
        assertEquals(2, one.table.rows.size)
        // Too large a step: |1 − 2η| = 2 doubles the distance each step, until it is past 10¹⁵⁰.
        val wild = calc("gradientdescent(x^2, 1, 1.5, 20)").outcome as Outcome.Descent
        close(Math.pow(2.0, 20.0), abs(wild.point!![0]), "x₂₀")
        assertNull((calc("gradientdescent(x^2, 1, 1.5, 600)").outcome as Outcome.Descent).point)
        // From 0.5 on x⁴ − 2x², to the minimum at 1 (f = −1), not to the one at −1.
        assertEquals("descent 19 rows → 1.0000000000 settled | minimum (1) -1", answer("gradientdescent(x^4 - 2x^2, 0.5, 0.1, 100)"))
        assertEquals("SIZES", answer("gradientdescent(x^2 + y^2, 1, 0.1, 10)"))
        assertEquals("ARGUMENTS", answer("gradientdescent(x^2, 1, 0.1)"))
        assertEquals("ARGUMENTS", answer("gradientdescent(x^2, 1, -0.1, 10)"))
        assertEquals("ARGUMENTS", answer("gradientdescent(x^2, 1, 0.1, 2.5)"))
    }

    /**
     * The optimal values of a process with two states, by brute force: every deterministic policy evaluated in doubles (the 2 × 2
     * system solved by Cramer's rule), the best value of each state.
     */
    private fun bruteForce(p: List<List<List<Double>>>, r: List<List<Double>>, gamma: Double): List<Double> {
        val m = p.size
        var best = listOf(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY)
        for (a0 in 0 until m) for (a1 in 0 until m) {
            val a = listOf(a0, a1)
            val m00 = 1 - gamma * p[a0][0][0]
            val m01 = -gamma * p[a0][0][1]
            val m10 = -gamma * p[a1][1][0]
            val m11 = 1 - gamma * p[a1][1][1]
            val det = m00 * m11 - m01 * m10
            val v0 = (r[0][a[0]] * m11 - m01 * r[1][a[1]]) / det
            val v1 = (m00 * r[1][a[1]] - r[0][a[0]] * m10) / det
            best = listOf(maxOf(best[0], v0), maxOf(best[1], v1))
        }
        return best
    }

    @Test
    fun markovDecisionProcesses() {
        // Stay (P₁) or move to state 2 (P₂); rewards 1 for staying in 1, 2 for moving in 2.
        val stay = "valueiteration([[1, 0], [0, 1]], [[0, 1], [0, 1]], [[1, 0], [0, 2]], 0.9)"
        val o = calc(stay).outcome as Outcome.DecisionProcess
        assertEquals("v* = 18, 20 | policy 2 2 | sweeps 242", answerOf(calc(stay)))
        val brute = bruteForce(listOf(listOf(listOf(1.0, 0.0), listOf(0.0, 1.0)), listOf(listOf(0.0, 1.0), listOf(0.0, 1.0))),
            listOf(listOf(1.0, 0.0), listOf(0.0, 2.0)), 0.9)
        o.values.forEachIndexed { s, v -> close(brute[s], Numeric.value(v)!!, "v*(s${s + 1})", 1e-12) }
        // q*(1, 1) = 1 + 0.9·18 = 86/5.
        assertEquals(num(Rational.of(86) / Rational.of(5)), o.actions[0][0])
        // A cycle: v₁ = 1 + 0.9v₂, v₂ = 2 + 0.9v₁.
        assertEquals("v* = 280/19, 290/19 | policy 2 1 | sweeps 244",
            answer("valueiteration([[1, 0], [1, 0]], [[0, 1], [0, 1]], [[0, 1], [2, 0]], 0.9)"))
        val brute2 = bruteForce(listOf(listOf(listOf(1.0, 0.0), listOf(1.0, 0.0)), listOf(listOf(0.0, 1.0), listOf(0.0, 1.0))),
            listOf(listOf(0.0, 1.0), listOf(2.0, 0.0)), 0.9)
        close(brute2[0], 280.0 / 19, "cycle v*(s1)"); close(brute2[1], 290.0 / 19, "cycle v*(s2)")
        // γ = 0: the best reward of each state, after one sweep; equal rewards tie.
        assertEquals("v* = 3, 5 | policy 2 1/2 | sweeps 2", answer("valueiteration([[1, 0], [0, 1]], [[0, 1], [1, 0]], [[1, 3], [5, 5]], 0)"))
        // One action and a row of zeros: action 2 is not available in state 2.
        assertEquals("v* = 4/3, 0 | policy 1 1 | sweeps 21", answer("valueiteration([[0.5, 0.5], [0, 1]], [[1], [0]], 0.5)"))
        assertEquals("v* = 10, 0 | policy 2 1 | sweeps 2",
            answer("valueiteration([[0, 1], [0, 1]], [[0, 1], [0, 0]], [[1, 10], [0, 7]], 0)"))
        // Refused: rows that are no probabilities, γ = 1, sizes that do not fit, a state without an action.
        assertEquals("NOT_DISTRIBUTION", answer("valueiteration([[0.5, 0.6], [0, 1]], [[1], [0]], 0.5)"))
        assertEquals("UNDEFINED", answer("valueiteration([[0.5, 0.5], [0, 1]], [[1], [0]], 1)"))
        assertEquals("SIZES", answer("valueiteration([[0.5, 0.5], [0, 1]], [[1, 2], [0, 3]], 0.5)"))
        assertEquals("NOT_DISTRIBUTION", answer("valueiteration([[1, 0], [0, 0]], [[1], [0]], 0.5)"))
        assertEquals("ARGUMENTS", answer("valueiteration([[1, 0], [0, 1]], 0.5)"))
    }

    /** v = r + γPv iterated in doubles until it settles: the value of a policy computed another way. */
    private fun iterate(p: List<List<Double>>, r: List<Double>, gamma: Double): List<Double> {
        var v = List(r.size) { 0.0 }
        repeat(5000) { v = r.indices.map { s -> r[s] + gamma * v.indices.sumOf { t -> p[s][t] * v[t] } } }
        return v
    }

    @Test
    fun policyEvaluation() {
        assertEquals("(4/3, 0)", answer("policyevaluation([[0.5, 0.5], [0, 1]], [[1], [0]], 0.5, [[1], [1]])"))
        // The random policy of the two-action process: P_π = (P₁ + P₂)/2, r_π = the mean reward of each state.
        assertEquals("(100/11, 10)",
            answer("policyevaluation([[1, 0], [0, 1]], [[0, 1], [0, 1]], [[1, 0], [0, 2]], 0.9, [[0.5, 0.5], [0.5, 0.5]])"))
        val v = iterate(listOf(listOf(0.5, 0.5), listOf(0.0, 1.0)), listOf(0.5, 1.0), 0.9)
        close(v[0], 100.0 / 11, "v_π(s1)", 1e-9); close(v[1], 10.0, "v_π(s2)", 1e-9)
        // A deterministic policy is a row with one 1: the optimal one gives v*.
        assertEquals("(18, 20)", answer("policyevaluation([[1, 0], [0, 1]], [[0, 1], [0, 1]], [[1, 0], [0, 2]], 0.9, [[0, 1], [0, 1]])"))
        assertEquals("NOT_DISTRIBUTION",
            answer("policyevaluation([[1, 0], [0, 1]], [[0, 1], [0, 1]], [[1, 0], [0, 2]], 0.9, [[0.5, 0.6], [0, 1]])"))
        // Probability on an action that is not available.
        assertEquals("NOT_DISTRIBUTION",
            answer("policyevaluation([[0, 1], [0, 1]], [[0, 1], [0, 0]], [[1, 10], [0, 7]], 0.5, [[0, 1], [0, 1]])"))
        assertEquals("SIZES", answer("policyevaluation([[0.5, 0.5], [0, 1]], [[1], [0]], 0.5, [[1, 0], [0, 1]])"))
    }

    @Test
    fun steps() {
        fun keys(text: String) = TextInput.read(text).let { t -> Steps.of(t.latex, t.choice)?.joinToString(" ") { it.why.key.name } ?: "none" }
        assertEquals("START SOFTMAX_EXP SOFTMAX_DIVIDE RESULT SOFTMAX_CHECK", keys("softmax([1, 2, 3])"))
        assertEquals("START ENTROPY_SUM RESULT", keys("entropy([1/2, 1/4, 1/4], 2)"))
        assertEquals("START CROSS_ENTROPY_SUM INFO_CHECK RESULT", keys("crossentropy([1/2, 1/2], [1/4, 3/4], 2)"))
        assertEquals("START KL_SUM INFO_CHECK RESULT", keys("kldivergence([1/2, 1/2], [1/4, 3/4], 2)"))
        assertEquals("START MI_MARGINALS MI_SUM INFO_CHECK RESULT", keys("mutualinformation([[0.3, 0.2], [0.1, 0.4]])"))
        assertEquals("START MDP_BELLMAN MDP_SWEEPS MDP_EVALUATE MDP_OPTIMAL RESULT",
            keys("valueiteration([[1, 0], [0, 1]], [[0, 1], [0, 1]], [[1, 0], [0, 2]], 0.9)"))
        assertEquals("START MDP_POLICY_PARTS MDP_LINEAR RESULT",
            keys("policyevaluation([[1, 0], [0, 1]], [[0, 1], [0, 1]], [[1, 0], [0, 2]], 0.9, [[0.5, 0.5], [0.5, 0.5]])"))
        assertEquals("START GD_GRADIENT GD_STEP RESULT GD_MINIMUM", keys("gradientdescent(x^2 + y^2, [1, 2], 0.25, 20)"))
        // The first sweeps with fractions: v₁ = (1, 2), v₂ = (1.9, 3.8) as 19/10, 19/5.
        val sweeps = TextInput.read("valueiteration([[1, 0], [0, 1]], [[0, 1], [0, 1]], [[1, 0], [0, 2]], 0.9)").let { t ->
            Steps.of(t.latex, t.choice)!!.first { it.why.key == StepKey.MDP_SWEEPS }.latex
        }
        assertTrue(sweeps, "v_{1} = \\left(1,\\ 2\\right)" in sweeps && "v_{2} = \\left(1.9,\\ 3.8\\right)" in sweeps)
        // The first step of gradient descent exactly: (1, 2) − 1/4·(2, 4) = (1/2, 1).
        val first = TextInput.read("gradientdescent(x^2 + y^2, [1, 2], 0.25, 20)").let { t ->
            Steps.of(t.latex, t.choice)!!.first { it.why.key == StepKey.GD_STEP }.latex
        }
        assertTrue(first, first.endsWith("\\left(0.5,\\ 1\\right)"))
    }

    /** Each calculation in milliseconds, far inside the three seconds a calculation may take. */
    @Test
    fun quickly() {
        for (text in listOf("softmax([1, 2, 3, 4, 5, 6, 7, 8])", "entropy([0.1, 0.1, 0.1, 0.1, 0.2, 0.2, 0.1, 0.1], 2)",
            "mutualinformation([[0.1, 0.1, 0.1], [0.2, 0.2, 0.3]])", "gradientdescent(x^2 + 3y^2 + x y, [3, -2], 0.1, 1000)",
            "valueiteration([[0.9, 0.1, 0], [0, 0.9, 0.1], [0, 0, 1]], [[0, 1, 0], [0, 0, 1], [0, 0, 1]], [[0, 1], [0, 2], [5, 0]], 0.99)")) {
            val start = System.nanoTime()
            val o = calc(text).outcome
            val ms = (System.nanoTime() - start) / 1_000_000
            assertTrue("$text: $o", o !is Outcome.Failed)
            assertTrue("$text took $ms ms", ms < 1500)
        }
    }
}

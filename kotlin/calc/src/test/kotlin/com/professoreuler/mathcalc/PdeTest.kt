package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sinh

/**
 * Partial differential equations: the type of a second-order linear equation from b² − 4ac and its canonical form, and the
 * classic problems solved by separation of variables. Every answer is held against a value known independently: a textbook's
 * coefficient formula evaluated here, a canonical form worked out by hand, a test function carried through the change of
 * variables by the chain rule, partial sums summed here from the textbook's formula.
 */
class PdeTest {
    private fun calc(text: String): Calculation = Reports.of(TextInput.read(text)).result

    private fun answer(text: String): String = answerOf(calc(text))

    private fun text(e: Expr): String = Format.expr(e, Style.PLAIN, latex = false)

    private fun classification(text: String): Pde.Classification =
        (calc(text).outcome as? Outcome.PdeType ?: error("$text: ${answer(text)}")).classification

    private fun solution(text: String): Pde.Separated =
        (calc(text).outcome as? Outcome.PdeSeries ?: error("$text: ${answer(text)}")).solution

    /** A coefficient's value at n, from its formula (or its exception), the letters at [env]. */
    private fun coefficient(k: Pde.Coefficient, n: Int, env: Map<String, Double> = emptyMap()): Double =
        k.exceptions.firstOrNull { it.first == n }?.let { Numeric.eval(it.second, env) } ?: Numeric.eval(k.formula, env + ("n" to n.toDouble()))

    /** [formula] agrees with the textbook's [expected] at n = 1 … 40. */
    private fun agrees(k: Pde.Coefficient, env: Map<String, Double> = emptyMap(), expected: (Int) -> Double) {
        for (n in 1..40) {
            val got = coefficient(k, n, env)
            assertTrue("${k.name}_$n = $got, not ${expected(n)}", abs(got - expected(n)) <= 1e-12 * maxOf(1.0, abs(expected(n))))
        }
    }

    // ------------------------------------------------------------------------------------------------------------------
    // The type.

    @Test
    fun constantCoefficientsTheirType() {
        // Δ = b² − 4ac, worked out here.
        val cases = listOf(
            "pdetype(u_xx + 4u_xy + u_yy = 0)" to (Pde.Type.HYPERBOLIC to "12"),
            "pdetype(u_xx + u_yy = 0)" to (Pde.Type.ELLIPTIC to "-4"),
            "pdetype(u_t = 2u_xx)" to (Pde.Type.PARABOLIC to "0"),
            "pdetype(u_tt = 4u_xx)" to (Pde.Type.HYPERBOLIC to "16"),
            "pdetype(u_xx - 2u_xy + u_yy + u_x = 0)" to (Pde.Type.PARABOLIC to "0"),
            "pdetype(u_xx + 2u_xy + 5u_yy = 0)" to (Pde.Type.ELLIPTIC to "-16"),
            "pdetype(3u_xy + u_yy = 0)" to (Pde.Type.HYPERBOLIC to "9"),
            "pdetype(2u_xx + 3u_xy + u_yy + u_x - u = x y)" to (Pde.Type.HYPERBOLIC to "1"),
            // Without the function, the equation on its own: its type.
            "u_xx + u_yy = 0" to (Pde.Type.ELLIPTIC to "-4"),
        )
        for ((text, expected) in cases) {
            val k = classification(text)
            assertEquals(text, expected.first, k.type)
            assertEquals(text, expected.second, text(k.discriminant))
        }
    }

    @Test
    fun variableCoefficientsTheirRegions() {
        // Tricomi's equation: elliptic for x > 0, parabolic on x = 0, hyperbolic for x < 0.
        assertEquals("hyperbolic where x < 0; parabolic where x = 0; elliptic where 0 < x Δ = -4x", answer("pdetype(u_xx + x u_yy = 0)"))
        assertEquals("hyperbolic where y < 0; parabolic where y = 0; elliptic where 0 < y Δ = -4y", answer("pdetype(y u_xx + u_yy = 0)"))
        // A letter of the equation: u_xx + k u_yy is elliptic for k > 0, hyperbolic for k < 0.
        assertEquals("hyperbolic where k < 0; parabolic where k = 0; elliptic where 0 < k Δ = -4k", answer("pdetype(u_xx + k u_yy = 0)"))
        assertEquals("hyperbolic where c ∈ (-∞, 0) ∪ (0, ∞); parabolic where c = 0 Δ = 4c^2", answer("pdetype(u_tt = c^2 u_xx)"))
        // Δ = y² − 4x in two letters: each region as its inequality.
        val two = classification("pdetype(u_xx + y u_xy + x u_yy = 0)")
        assertEquals(listOf(Pde.Type.HYPERBOLIC, Pde.Type.PARABOLIC, Pde.Type.ELLIPTIC), two.regions.map { it.type })
        assertEquals("y^2 - 4x", text(two.discriminant))
        // (1 + x²)u_xx + u_yy: Δ = −4(1 + x²) < 0 everywhere.
        assertEquals(Pde.Type.ELLIPTIC, classification("pdetype((1 + x^2) u_xx + u_yy = 0)").type)
        // Each region checked here at a point of it.
        val tricomi = classification("pdetype(u_xx + x u_yy = 0)")
        for (r in tricomi.regions.filter { it.set != null }) {
            val i = r.set!!.intervals.single()
            val at = (i.lower?.let { Numeric.value(it)!! + 0.5 } ?: (Numeric.value(i.upper!!)!! - 0.5))
            val d = -4 * at
            assertEquals(r.type, if (d > 0) Pde.Type.HYPERBOLIC else Pde.Type.ELLIPTIC)
        }
    }

    @Test
    fun canonicalFormsWorkedOutByHand() {
        // u_xx + 4u_xy + u_yy = 0: λ² − 4λ + 1 = 0, λ = 2 ± √3, ξ = y − (2 + √3)x, η = y − (2 − √3)x, u_ξη = 0.
        assertEquals("hyperbolic Δ = 12 | ξ = x(-2 - √3) + y, η = x(-2 + √3) + y | u_{\\xi\\eta} = 0", answer("pdetype(u_xx + 4u_xy + u_yy = 0)"))
        // 2u_xx + 3u_xy + u_yy + u_x − u = xy: λ = 1, 1/2; ξ = y − x, η = y − x/2; B = −1/2, D = −1, E = −1/2, F = −1, so
        // u_ξη + 2u_ξ + u_η + 2u = −2xy with x = 2(η − ξ), y = 2η − ξ.
        assertEquals("hyperbolic Δ = 1 | ξ = y - x, η = y - (1/2)x | u_{\\xi\\eta} + 2u_{\\xi} + u_{\\eta} + 2u = -8\\eta^2 + 12\\eta\\xi - 4\\xi^2",
            answer("pdetype(2u_xx + 3u_xy + u_yy + u_x - u = x y)"))
        // Laplace's equation is its own canonical form; u_xx + 2u_xy + 5u_yy with ξ = 2x, η = y − x.
        assertEquals("elliptic Δ = -4 | ξ = x, η = y | u_{\\xi\\xi} + u_{\\eta\\eta} = 0", answer("pdetype(u_xx + u_yy = 0)"))
        assertEquals("elliptic Δ = -16 | ξ = 2x, η = y - x | u_{\\xi\\xi} + u_{\\eta\\eta} = 0", answer("pdetype(u_xx + 2u_xy + 5u_yy = 0)"))
        // u_xx − 2u_xy + u_yy + u_x: ξ = x + y along the characteristic, η = x: u_ηη + u_ξ + u_η = 0.
        assertEquals("parabolic Δ = 0 | ξ = x + y, η = x | u_{\\eta\\eta} + u_{\\xi} + u_{\\eta} = 0", answer("pdetype(u_xx - 2u_xy + u_yy + u_x = 0)"))
        // a = 0: 3u_xy + u_yy, ξ = x, η = y − x/3.
        assertEquals("hyperbolic Δ = 9 | ξ = x, η = y - (1/3)x | u_{\\xi\\eta} = 0", answer("pdetype(3u_xy + u_yy = 0)"))
    }

    /**
     * The canonical form carried back by the chain rule, independently of how it was found: u(x, y) = U(ξ, η) for a test U, the
     * original operator applied to u in x and y, and the canonical one applied to U in ξ and η (times the leading coefficient
     * divided out), the same function.
     */
    @Test
    fun canonicalFormsByTheChainRule() {
        val xi = Expr.Sym("\\xi")
        val eta = Expr.Sym("\\eta")
        val test = add(pow(xi, num(3)), mul(xi, pow(eta, Expr.TWO)), exp(mul(Expr.HALF, eta)), fn(Func.SIN, add(xi, eta)))
        for (text in listOf("pdetype(u_xx + 4u_xy + u_yy = 0)", "pdetype(2u_xx + 3u_xy + u_yy + u_x - u = x y)", "pdetype(u_xx + 2u_xy + 5u_yy = 0)",
            "pdetype(u_xx - 2u_xy + u_yy + u_x = 0)", "pdetype(3u_xy + u_yy - 2u_y = 0)", "pdetype(4u_xx - u_yy + 3u = 0)")) {
            val k = classification(text)
            val f = k.canonical!!
            val q = k.coefficients
            val x = Expr.Sym(q.x)
            val y = Expr.Sym(q.y)
            val u = test.substitute(xi, f.xi).substitute(eta, f.eta)
            fun d(e: Expr, vararg v: Expr.Sym) = v.fold(e) { acc, s -> Calculus.derivative(acc, s) }
            val original = add(mul(q.a, d(u, x, x)), mul(q.b, d(u, x, y)), mul(q.c, d(u, y, y)), mul(q.d, d(u, x)), mul(q.e, d(u, y)), mul(q.f, u))
            // The canonical operator on U, its markers replaced by U's derivatives in ξ and η.
            val canonical = f.equation.substitute(Expr.Sym("u_\\xi\\xi"), d(test, xi, xi)).substitute(Expr.Sym("u_\\xi\\eta"), d(test, xi, eta))
                .substitute(Expr.Sym("u_\\eta\\eta"), d(test, eta, eta)).substitute(Expr.Sym("u_\\xi"), d(test, xi))
                .substitute(Expr.Sym("u_\\eta"), d(test, eta)).substitute(Expr.Sym("u"), test)
            // The leading coefficient divided out (the one u_ξη, u_ηη or u_ξξ had): one constant at every point, and the right
            // sides in the same ratio.
            val back = canonical.substitute(xi, f.xi).substitute(eta, f.eta)
            val rhs = f.rhs.substitute(xi, f.xi).substitute(eta, f.eta)
            val random = java.util.Random(3)
            var lead: Double? = null
            repeat(6) {
                val env = mapOf(q.x to random.nextDouble() * 2 - 1, q.y to random.nextDouble() * 2 - 1)
                val o = Numeric.eval(original, env)
                val c = Numeric.eval(back, env)
                if (lead == null) lead = o / c
                assertTrue("$text: $o, not ${lead!! * c}", abs(o - lead!! * c) <= 1e-9 * maxOf(1.0, abs(o)))
                assertEquals(text, Numeric.eval(q.g, env), lead!! * Numeric.eval(rhs, env), 1e-9)
            }
            assertTrue(text, abs(lead!!) > 1e-9)
        }
    }

    @Test
    fun notEveryEquationIsLinearOfTheSecondOrder() {
        assertEquals("UNSOLVED", answer("pdetype(u u_x + u_t = 0)"))
        assertEquals("UNSOLVED", answer("pdetype(u_t = u_xxx)"))
        assertEquals("UNSOLVED", answer("pdetype(u_x + u_y = 0)"))
        // A derivative in one variable only is no partial differential equation: u_x is a letter as before.
        assertEquals("u_x = 3", answer("u_x = 3"))
        assertEquals("ARGUMENTS", answer("pdetype(u_xx + u_yy = 0, 3)"))
    }

    @Test
    fun theWaysOfWritingADerivative() {
        // u_xx typed is one subscript; ∂²u/∂x² and u_{xx} are the same marker; u_yx is u_xy.
        assertEquals("u_{xx} + u_{yy} = 0", TextInput.read("u_xx + u_yy = 0").latex)
        val same = listOf("pdetype(u_t = u_xx)", "\\frac{\\partial u}{\\partial t}=\\frac{\\partial^{2}u}{\\partial x^{2}}", "u_{t}=u_{xx}",
            "\\frac{\\partial u}{\\partial t}=\\frac{\\partial^2 u}{\\partial x^2}")
        val answers = same.map { if (it.startsWith("pdetype")) answer(it) else answerOf(Reports.of(it).result) }
        assertEquals(1, answers.toSet().size)
        assertEquals("parabolic Δ = 0 | ξ = t, η = x | u_{\\eta\\eta} - u_{\\xi} = 0", answers[0])
        assertEquals(answer("pdetype(u_xx + 4u_xy + u_yy = 0)"), answer("pdetype(u_xx + 2u_xy + 2u_yx + u_yy = 0)"))
        // A condition written with ∂ is the same condition.
        assertEquals(answer("pdsolve(u_t = u_xx, u_x(0, t) = 0, u_x(pi, t) = 0, u(x, 0) = x)"), answerOf(Reports.of("\\begin{cases}u_{t}=u_{xx}\\\\" +
            "\\frac{\\partial u}{\\partial x}(0, t)=0\\\\\\frac{\\partial u}{\\partial x}(\\pi, t)=0\\\\u(x, 0)=x\\end{cases}").result))
        // Two letters with subscripts in one variable each are no equation of u: a system as before.
        assertEquals("u_x = 3, u_y = 4", answer("u_x = 3; u_y = 4"))
        assertEquals(answer("pdetype(u_xx + 4u_xy + u_yy = 0)"),
            answerOf(Reports.of("\\frac{\\partial^{2}u}{\\partial x^{2}}+4\\frac{\\partial^{2}u}{\\partial x\\partial y}+\\frac{\\partial^{2}u}{\\partial y^{2}}=0").result))
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Separation of variables.

    @Test
    fun heatEquationFixedEnds() {
        // u(x, 0) = x(π − x): bₙ = 4(1 − (−1)ⁿ)/(πn³), 8/(πn³) for odd n.
        val s = solution("pdsolve(u_t = u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = x(pi - x))")
        agrees(s.coefficients.single()) { n -> 4 * (1 - (-1.0).pow(n)) / (PI * n * n * n) }
        assertEquals("u = 8·sin(x)/(πe^t) + 8·sin(3x)/(27πe^(9t)) + 8·sin(5x)/(125πe^(25t)) + … | b_n = -4((-1)^n - 1)/(πn^3) | 3200 terms < 7e-11",
            answerOf(calc("pdsolve(u_t = u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = x(pi - x))")))
        // u(x, 0) = 1: bₙ = 2(1 − (−1)ⁿ)/(nπ).
        agrees(solution("pdsolve(u_t = u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = 1)").coefficients.single()) { n -> 2 * (1 - (-1.0).pow(n)) / (n * PI) }
        // u(x, 0) = x sin x: b₁ = π/2, bₙ = −4n(1 + (−1)ⁿ)/(π(n² − 1)²) for n ≥ 2.
        agrees(solution("pdsolve(u_t = u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = x sin(x))").coefficients.single()) { n ->
            if (n == 1) PI / 2 else -4.0 * n * (1 + (-1.0).pow(n)) / (PI * (n * n - 1.0).pow(2))
        }
        // On (0, 1) with k = 3: X_n = sin(nπx), T_n = e^(−3n²π²t).
        val unit = solution("pdsolve(u_t = 3u_xx, u(0, t) = 0, u(1, t) = 0, u(x, 0) = x)")
        agrees(unit.coefficients.single()) { n -> 2 * (-1.0).pow(n + 1) / (n * PI) }
        assertEquals(3.0 * PI * PI, Numeric.eval(Calculus.tidy(mul(unit.constant!!, unit.eigen[0].lambda)), mapOf("n" to 1.0)), 1e-12)
    }

    @Test
    fun aFiniteSeriesIsTheExactSolution() {
        assertEquals("u = sin(3x)/e^(18t) | b_n = 0, b_3 = 1 | exact", answer("pdsolve(u_t = 2u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = sin(3x))"))
        // sin x + 3 sin 2x as the initial velocity with c = 3: bₙ = (2/(3nπ))∫…, b₁ = 1/3, b₂ = 1/2.
        assertEquals("u = (1/3)sin(3t)sin(x) + (1/2)sin(6t)sin(2x) | b_n = 0, b_1 = 1/3, b_2 = 1/2 | exact",
            answer("pdsolve(u_tt = 9u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = 0, u_t(x, 0) = sin(x) + 3sin(2x))"))
        // The solution put back here: u_tt = 9u_xx and its conditions.
        val s = solution("pdsolve(u_tt = 9u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = 0, u_t(x, 0) = sin(x) + 3sin(2x))")
        val u = Calculus.tidy(s.sum!!)
        val x = Expr.Sym("x")
        val t = Expr.Sym("t")
        assertTrue(Verify.same(Calculus.derivative(u, t, 2), Calculus.tidy(mul(num(9), Calculus.derivative(u, x, 2)))))
        assertTrue(Verify.same(Calculus.derivative(u, t).substitute(t, Expr.ZERO), add(fn(Func.SIN, x), mul(num(3), fn(Func.SIN, mul(Expr.TWO, x))))))
        assertEquals("u = sin(x)cos(ct) | a_n = 0, a_1 = 1 | exact", answer("pdsolve(u_tt = c^2 u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = sin(x), u_t(x, 0) = 0)"))
        assertEquals("u = sin(πx)sinh(πy)/sinh(π) | b_n = 0, b_1 = 1 | exact",
            answer("pdsolve(u_xx + u_yy = 0, u(x, 0) = 0, u(x, 1) = sin(pi x), u(0, y) = 0, u(1, y) = 0)"))
    }

    @Test
    fun heatEquationInsulatedAndMixedEnds() {
        // Insulated ends, u(x, 0) = x: a₀ = π, aₙ = 2((−1)ⁿ − 1)/(πn²).
        val s = solution("pdsolve(u_t = u_xx, u_x(0, t) = 0, u_x(pi, t) = 0, u(x, 0) = x)")
        val a = s.coefficients.single()
        assertEquals(PI, Numeric.value(a.zero!!)!!, 1e-15)
        agrees(a) { n -> 2 * ((-1.0).pow(n) - 1) / (PI * n * n) }
        // Fixed at 0, insulated at π: Xₙ = sin((2n − 1)x/2), bₙ = 8(−1)ⁿ⁺¹/(π(2n − 1)²).
        val mixed = solution("pdsolve(u_t = u_xx, u(0, t) = 0, u_x(pi, t) = 0, u(x, 0) = x)")
        agrees(mixed.coefficients.single()) { n -> 8 * (-1.0).pow(n + 1) / (PI * (2 * n - 1.0).pow(2)) }
        assertEquals(0.25, Numeric.eval(mixed.eigen[0].lambda, mapOf("n" to 1.0)), 1e-15)
        // Insulated at 0, fixed at π: Xₙ = cos((2n − 1)x/2); for u(x, 0) = π − x, bₙ = 8/(π(2n − 1)²).
        agrees(solution("pdsolve(u_t = u_xx, u_x(0, t) = 0, u(pi, t) = 0, u(x, 0) = pi - x)").coefficients.single()) { n -> 8 / (PI * (2 * n - 1.0).pow(2)) }
    }

    @Test
    fun endsFixedAtOtherValues() {
        // u(0, t) = 100, u(π, t) = 0, u(x, 0) = 0: v = 100 − 100x/π, bₙ = (2/π)∫₀^π −v sin nx dx = −200/(nπ).
        val s = solution("pdsolve(u_t = u_xx, u(0, t) = 100, u(pi, t) = 0, u(x, 0) = 0)")
        assertEquals("-100x/π + 100", text(s.steady!!))
        agrees(s.coefficients.single()) { n -> -200 / (n * PI) }
        // Initial values that are the steady state: u is it, for all t.
        assertEquals("u = 3x + 20", answer("pdsolve(u_t = 2u_xx, u(0, t) = 20, u(10, t) = 50, u(x, 0) = 20 + 3x)"))
        // A string fixed at height 1 at both ends: u = 1 + sin x cos t.
        assertEquals("u = sin(x)cos(t) + 1 | a_n = 0, a_1 = 1 | exact",
            answer("pdsolve(u_tt = u_xx, u(0, t) = 1, u(pi, t) = 1, u(x, 0) = 1 + sin(x), u_t(x, 0) = 0)"))
    }

    @Test
    fun waveEquation() {
        // A plucked string on (0, 1), c = 2: aₙ = 4(1 − (−1)ⁿ)/(n³π³), and cos(2nπt).
        val s = solution("pdsolve(u_tt = 4u_xx, u(0, t) = 0, u(1, t) = 0, u(x, 0) = x(1 - x), u_t(x, 0) = 0)")
        agrees(s.coefficients.single()) { n -> 4 * (1 - (-1.0).pow(n)) / (n.toDouble().pow(3) * PI.pow(3)) }
        val factor = s.coefficients.single().factor
        for (n in 1..3) assertEquals(kotlin.math.cos(2 * n * PI * 0.2) * kotlin.math.sin(n * PI * 0.3),
            Numeric.eval(factor, mapOf("n" to n.toDouble(), "x" to 0.3, "t" to 0.2)), 1e-14)
        // Struck from rest with velocity 1, c and L letters: bₙ = 2L(1 − (−1)ⁿ)/(cn²π²).
        val struck = solution("pdsolve(u_tt = c^2 u_xx, u(0, t) = 0, u(L, t) = 0, u(x, 0) = 0, u_t(x, 0) = 1)")
        for ((c, l) in listOf(1.0 to 1.0, 2.5 to 0.7)) {
            agrees(struck.coefficients.single(), mapOf("c" to c, "L" to l)) { n -> 2 * l * (1 - (-1.0).pow(n)) / (c * n * n * PI * PI) }
        }
        assertEquals(listOf("c", "L"), struck.positive.map { text(it) })
        // Free ends (u_x = 0): the rigid motion a₀/2 + b₀t/2 too.
        assertEquals("u = cos(t)cos(x) + t | a_n = 0, a_1 = 1, a_0 = 0 | b_n = 0, b_0 = 2 | exact | exact",
            answer("pdsolve(u_tt = u_xx, u_x(0, t) = 0, u_x(pi, t) = 0, u(x, 0) = cos(x), u_t(x, 0) = 1)"))
    }

    @Test
    fun laplaceOnARectangle() {
        // The top of the square (0, π)² at x(π − x): bₙ = 4(1 − (−1)ⁿ)/(πn³), sinh(ny)/sinh(nπ).
        val s = solution("pdsolve(u_xx + u_yy = 0, u(x, 0) = 0, u(x, pi) = x(pi - x), u(0, y) = 0, u(pi, y) = 0)")
        val b = s.coefficients.single()
        agrees(b) { n -> 4 * (1 - (-1.0).pow(n)) / (PI * n * n * n) }
        // u at the centre from the coefficients, against the series summed here from the textbook's formula.
        val centre = (1..199 step 2).sumOf { n -> 8 / (PI * n * n * n) * sinh(n * PI / 2) / sinh(n * PI) * kotlin.math.sin(n * PI / 2) }
        val engine = (1..199).sumOf { n -> coefficient(b, n) * Numeric.eval(b.factor, mapOf("n" to n.toDouble(), "x" to PI / 2, "y" to PI / 2)) }
        assertEquals(centre, engine, 1e-12)
        // The right side of (0, 2) × (0, 1) at y(1 − y): dₙ = 4(1 − (−1)ⁿ)/(n³π³), sinh(nπx)/sinh(2nπ).
        val right = solution("pdsolve(u_xx + u_yy = 0, u(x, 0) = 0, u(x, 1) = 0, u(0, y) = 0, u(2, y) = y(1 - y))")
        assertEquals("d", right.coefficients.single().name)
        agrees(right.coefficients.single()) { n -> 4 * (1 - (-1.0).pow(n)) / (n.toDouble().pow(3) * PI.pow(3)) }
        // Two sides at once: the series of each, superposed.
        val two = solution("pdsolve(u_xx + u_yy = 0, u(x, 0) = 1, u(x, 1) = 0, u(0, y) = 0, u(1, y) = y)")
        assertEquals(listOf("a", "d"), two.coefficients.map { it.name })
        agrees(two.coefficients[0]) { n -> 2 * (1 - (-1.0).pow(n)) / (n * PI) }
        agrees(two.coefficients[1]) { n -> 2 * (-1.0).pow(n + 1) / (n * PI) }
    }

    @Test
    fun lettersInTheProblem() {
        // On (0, L) with k: bₙ = 4L²(1 − (−1)ⁿ)/(n³π³), e^(−k(nπ/L)²t).
        val s = solution("pdsolve(u_t = k u_xx, u(0, t) = 0, u(L, t) = 0, u(x, 0) = x(L - x))")
        for (l in listOf(1.0, 3.0)) agrees(s.coefficients.single(), mapOf("L" to l)) { n -> 4 * l * l * (1 - (-1.0).pow(n)) / (n.toDouble().pow(3) * PI.pow(3)) }
        assertEquals(listOf("k", "L"), s.positive.map { text(it) })
        // An amplitude: bₙ = 2A(1 − (−1)ⁿ)/(nπ).
        agrees(solution("pdsolve(u_t = u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = A)").coefficients.single(), mapOf("A" to 7.0)) { n ->
            14 * (1 - (-1.0).pow(n)) / (n * PI)
        }
        // sin x on (0, L): a coefficient with no value where L is a multiple of π, so no answer.
        assertEquals("UNSOLVED", answer("pdsolve(u_t = u_xx, u(0, t) = 0, u(L, t) = 0, u(x, 0) = sin(x))"))
    }

    /** The bound a check shows holds for the partial sums, summed here from the textbook's coefficients. */
    @Test
    fun theBoundOfTheCheckHolds() {
        val cases = listOf(
            Triple("pdsolve(u_t = u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = x(pi - x))", { n: Int -> 4 * (1 - (-1.0).pow(n)) / (PI * n * n * n) }, { x: Double -> x * (PI - x) }),
            Triple("pdsolve(u_t = u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = 1)", { n: Int -> 2 * (1 - (-1.0).pow(n)) / (n * PI) }, { _: Double -> 1.0 }),
            Triple("pdsolve(u_t = u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = e^x)",
                { n: Int -> 2 * n * (1 - (-1.0).pow(n) * kotlin.math.exp(PI)) / (PI * (n * n + 1.0)) }, { x: Double -> kotlin.math.exp(x) }),
        )
        for ((text, b, f) in cases) {
            val check = solution(text).checks.single()
            assertTrue(!check.exact)
            val bound = check.digit * 10.0.pow(check.exponent)
            for (p in check.points) {
                val x = Numeric.value(p)!!
                val sum = (1..check.terms).sumOf { n -> b(n) * kotlin.math.sin(n * x) }
                assertTrue("$text at $x: ${abs(sum - f(x))} not below $bound", abs(sum - f(x)) < bound)
            }
            // Fewer digits than a double has: the bound is a bound, rounded up.
            assertTrue(check.digit in 1..9)
        }
    }

    @Test
    fun conditionsThatDoNotFixOneSolution() {
        assertEquals("CONDITIONS", answer("pdsolve(u_t = u_xx, u(0, t) = 0)"))
        assertEquals("CONDITIONS", answer("pdsolve(u_t = u_xx)"))
        // The wave equation needs its initial velocity too.
        assertEquals("CONDITIONS", answer("pdsolve(u_tt = u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = sin(x))"))
        assertEquals("CONDITIONS", answer("pdsolve(u_t = u_xx, u(0, t) = 0, u(0, t) = 0, u(x, 0) = sin(x))"))
        assertEquals("CONDITIONS", answer("pdsolve(u_xx + u_yy = 0, u(x, 0) = 0, u(x, 1) = 1, u(0, y) = 0)"))
        // Problems that are not the classic ones are declined, never guessed.
        assertEquals("UNSOLVED", answer("pdsolve(u_t = -u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = sin(x))"))
        assertEquals("UNSOLVED", answer("pdsolve(u_t = u_xx + u, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = sin(x))"))
        assertEquals("UNSOLVED", answer("pdsolve(u_t = u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 1) = sin(x))"))
        assertEquals("UNSOLVED", answer("pdsolve(u_t = u_xx, u_x(0, t) = 1, u_x(pi, t) = 0, u(x, 0) = x)"))
        assertEquals("UNSOLVED", answer("pdsolve(u_t = u_xx, u(0, t) = t, u(pi, t) = 0, u(x, 0) = x)"))
        // √x sin nx has no antiderivative in the usual functions.
        assertEquals("NO_ANTIDERIVATIVE", answer("pdsolve(u_t = u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = sqrt(x))"))
        assertEquals("ARGUMENTS", answer("pdsolve(u_t = u_xx, 2)"))
    }

    @Test
    fun theCallsAreWrittenBack() {
        for (text in listOf("pdsolve(u_t = u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = x(pi - x))", "pdetype(u_xx + 4u_xy + u_yy = 0)",
            "pdsolve(u_tt = c^2 u_xx, u(0, t) = 0, u(L, t) = 0, u(x, 0) = 0, u_t(x, 0) = 1)")) {
            val written = CommandFunctions.latex(TextInput.read(text))
            assertNotNull(written)
            assertEquals(text, answer(text), answer(written!!))
            assertEquals(text, answer(text), answer(text.replace(", ", ",")))
        }
    }

    @Test
    fun withinTheTime() {
        for (text in listOf("pdsolve(u_t = u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = e^x)",
            "pdsolve(u_tt = c^2 u_xx, u(0, t) = 0, u(L, t) = 0, u(x, 0) = 0, u_t(x, 0) = 1)",
            "pdsolve(u_xx + u_yy = 0, u(x, 0) = 1, u(x, 1) = 0, u(0, y) = 0, u(1, y) = y)",
            "pdsolve(u_t = u_xx, u(0, t) = 100, u(pi, t) = 0, u(x, 0) = 0)")) {
            val start = System.nanoTime()
            val c = calc(text)
            val ms = (System.nanoTime() - start) / 1_000_000
            assertTrue("$text: ${answerOf(c)}", c.outcome is Outcome.PdeSeries)
            assertTrue("$text took $ms ms", ms < MathCalc.LIMIT_MS)
        }
    }

    companion object {
        /** Inputs whose steps reach every step of this area ([StepsTest.everyKindOfStepIsReached]). */
        val STEP_TASKS: List<Pair<String, Choice?>> = listOf(
            "pdetype(u_xx + 4u_xy + u_yy = 0)", "pdetype(u_xx + x u_yy = 0)", "pdetype(u_xx + u_yy = 0)", "pdetype(u_t = 2u_xx)",
            "pdsolve(u_t = u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = x(pi - x))",
            "pdsolve(u_t = 2u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = sin(3x))",
            "pdsolve(u_t = u_xx, u(0, t) = 100, u(pi, t) = 0, u(x, 0) = 0)",
        ).map { TextInput.read(it).let { t -> t.latex to t.choice } }
    }

    @Test
    fun theSteps() {
        fun keys(text: String) = TextInput.read(text).let { t -> Steps.of(t.latex, t.choice)?.joinToString(" ") { it.why.key.name } }
        assertEquals("START PDE_COEFFICIENTS PDE_DISCRIMINANT PDE_HYPERBOLIC PDE_CHARACTERISTIC PDE_NEW_VARIABLES PDE_CANONICAL",
            keys("pdetype(u_xx + 4u_xy + u_yy = 0)"))
        assertEquals("START PDE_COEFFICIENTS PDE_DISCRIMINANT PDE_HYPERBOLIC PDE_PARABOLIC PDE_ELLIPTIC", keys("pdetype(u_xx + x u_yy = 0)"))
        assertEquals("START PDE_SEPARATE PDE_EIGENVALUES PDE_OTHER_FACTOR PDE_SUPERPOSE PDE_FOURIER_COEFFICIENTS PDE_PARTIAL_SUMS RESULT",
            keys("pdsolve(u_t = u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = x(pi - x))"))
        assertEquals("START PDE_SEPARATE PDE_EIGENVALUES PDE_OTHER_FACTOR PDE_SUPERPOSE PDE_FOURIER_COEFFICIENTS PDE_FINITE RESULT",
            keys("pdsolve(u_t = 2u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = sin(3x))"))
        assertEquals("START PDE_STEADY_STATE PDE_SEPARATE PDE_EIGENVALUES PDE_OTHER_FACTOR PDE_SUPERPOSE PDE_FOURIER_COEFFICIENTS PDE_PARTIAL_SUMS RESULT",
            keys("pdsolve(u_t = u_xx, u(0, t) = 100, u(pi, t) = 0, u(x, 0) = 0)"))
        // The last step is the answer's first terms.
        val steps = TextInput.read("pdsolve(u_t = u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = x(pi - x))").let { Steps.of(it.latex, it.choice)!! }
        assertTrue(steps.last().latex, steps.last().latex.startsWith("u = \\frac{8\\sin\\left(x\\right)}{\\pi e^{t}}"))
    }
}

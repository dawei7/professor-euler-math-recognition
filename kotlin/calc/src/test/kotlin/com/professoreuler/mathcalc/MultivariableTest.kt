package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Multiple integrals in every coordinate system, line and surface integrals, flux, directional derivatives, tangent planes and
 * the Fourier transform, each against a value known another way: a textbook formula (the volume of a ball, the area of a sphere),
 * a theorem (Green's, Gauss's), a series, or a sum of the test's own.
 */
class MultivariableTest {
    private fun calc(text: String): Calculation = Reports.of(TextInput.read(text)).result

    private fun failure(text: String): Failure? = (calc(text).outcome as? Outcome.Failed)?.failure

    /** The answer's value as a double: a number, or a constant expression. */
    private fun value(text: String): Double {
        val c = calc(text)
        return when (val o = c.outcome) {
            is Outcome.Value -> o.exact?.let { Numeric.value(it) } ?: o.value.double
            is Outcome.Expression -> Numeric.value(o.result) ?: error("$text: no number, ${answerOf(c)}")
            else -> error("$text: ${answerOf(c)}")
        }
    }

    private fun near(expected: Double, text: String, tolerance: Double = 1e-9) {
        val got = value(text)
        assertTrue("$text: $got, not $expected", abs(got - expected) <= tolerance * max(1.0, abs(expected)))
    }

    /** The answer exactly as the card writes it. */
    private fun exact(expected: String, text: String) = assertEquals(text, expected, answerOf(calc(text)))

    // ---------------------------------------------------------------------------------------------------------------------
    // Multiple integrals.

    @Test
    fun doubleAndTripleIntegralsOverBoxes() {
        exact("1", "integral(x y, x, 0, 1, y, 0, 2)")
        exact("1/8 = 0.125", "integral(x y z, x, 0, 1, y, 0, 1, z, 0, 1)")
        // Over [0, 1]², e^(−x²−y²) is the square of ∫₀¹ e^(−x²) dx = (√π/2)·erf(1).
        val erf1 = 0.8427007929497149
        near(PI / 4 * erf1 * erf1, "integral(e^(-x^2-y^2), x, 0, 1, y, 0, 1)")
        // ∫₀¹∫₀¹ sin(xy) dx dy = Σ (−1)ᵏ/((2k + 1)!·(2k + 2)²), the series of sin integrated term by term.
        var series = 0.0
        var factorial = 1.0
        for (k in 0..12) {
            if (k > 0) factorial *= (2 * k) * (2 * k + 1).toDouble()
            series += (if (k % 2 == 0) 1 else -1) / (factorial * (2 * k + 2) * (2 * k + 2))
        }
        near(series, "integral(sin(x y), x, 0, 1, y, 0, 1)")
        // A singularity at a corner: 2 ln(1 + √2).
        near(2 * ln(1 + sqrt(2.0)), "integral(1/sqrt(x^2 + y^2), x, 0, 1, y, 0, 1)", 1e-8)
        exact("1", "integral(e^(-x-y), x, 0, infinity, y, 0, infinity)")
        exact("(1/2)ab^2", "integral(x, x, 0, b, y, 0, a)")
    }

    @Test
    fun boundsInTheOuterLetters() {
        exact("1/6 ≈ 0.1666666667", "integral(y, y, 0, x, x, 0, 1)")
        // The tetrahedron below x + y + z = 1: volume 1/6.
        exact("1/6 ≈ 0.1666666667", "integral(1, z, 0, 1 - x - y, y, 0, 1 - x, x, 0, 1)")
        // A quarter of the unit disc: π/4.
        exact("π/4 ≈ 0.7853981634", "integral(1, y, 0, sqrt(1 - x^2), x, 0, 1)")
        // The same integral as the formula writes it.
        assertEquals("π/4 ≈ 0.7853981634", answerOf(MathCalc.calculate("\\int_0^1 \\int_0^{\\sqrt{1-x^2}} 1 \\, dy \\, dx")))
    }

    @Test
    fun polarCylindricalAndSphericalCoordinates() {
        // Area of the unit disc, and ∬ (x² + y²) over the disc of radius R: πR⁴/2.
        exact("π ≈ 3.141592654", "polarintegral(1, r, 0, 1, theta, 0, 2pi)")
        near(PI * 16 / 2, "polarintegral(x^2 + y^2, r, 0, 2, theta, 0, 2pi)")
        // The Gaussian integral over the plane: π.
        exact("π ≈ 3.141592654", "polarintegral(e^(-x^2-y^2), r, 0, infinity, theta, 0, 2pi)")
        // The disc r ≤ 2 cos θ, of radius 1: area π.
        near(PI, "polarintegral(1, r, 0, 2cos(theta), theta, -pi/2, pi/2)")
        // ∭ z over the cylinder r ≤ 1, 0 ≤ z ≤ 2: π·∫₀² z dz = 2π.
        exact("2π ≈ 6.283185307", "cylindricalintegral(z, r, 0, 1, theta, 0, 2pi, z, 0, 2)")
        // The ball of radius 1: 4π/3; ∭ r² over the ball of radius 2: 4π·2⁵/5.
        exact("4π/3 ≈ 4.188790205", "sphericalintegral(1, r, 0, 1, theta, 0, pi, phi, 0, 2pi)")
        near(4 * PI * 32 / 5, "sphericalintegral(x^2 + y^2 + z^2, r, 0, 2, theta, 0, pi, phi, 0, 2pi)")
        // The upper half ball: z = r cos θ integrated, ∭ z dV = π/4.
        near(PI / 4, "sphericalintegral(z, r, 0, 1, theta, 0, pi/2, phi, 0, 2pi)")
    }

    @Test
    fun whatIsRefused() {
        // A bound in its own letter, a letter twice, the wrong letters for the coordinates.
        assertEquals(Failure.ARGUMENTS, failure("integral(x, x, 0, x, y, 0, 1)"))
        assertEquals(Failure.ARGUMENTS, failure("integral(x, x, 0, 1, x, 0, 1)"))
        assertEquals(Failure.ARGUMENTS, failure("integral(x y, x, 0, y, y, 0, x)"))
        assertEquals(Failure.ARGUMENTS, failure("polarintegral(1, x, 0, 1, y, 0, 1)"))
        assertEquals(Failure.ARGUMENTS, failure("sphericalintegral(1, r, 0, 1, theta, 0, pi)"))
        assertEquals(Failure.ARGUMENTS, failure("polarintegral(1, r, 0, x, theta, 0, pi)"))
        // ∬ 1/(x + y)² over the unit square is infinite: the inner integral is 1/y − 1/(1 + y).
        assertEquals(Failure.DIVERGES, failure("integral(1/(x + y)^2, x, 0, 1, y, 0, 1)"))
        // An integrand singular along x = y inside the square has no value; never a wrong one.
        assertTrue(failure("integral(1/(x - y)^2, x, 0, 1, y, 0, 1)") != null)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Along curves and over surfaces.

    @Test
    fun lineIntegrals() {
        // Lengths: the unit circle 2π, a turn of the helix (cos t, sin t, t) 2π√2.
        exact("2π ≈ 6.283185307", "lineintegral(1, (cos(t), sin(t)), t, 0, 2pi)")
        near(2 * PI * sqrt(2.0), "lineintegral(1, (cos(t), sin(t), t), t, 0, 2pi)")
        // ∫ xy ds along y = x²: ∫₀¹ t³√(1 + 4t²) dt = (25√5 + 1)/120, by u = 1 + 4t².
        near((25 * sqrt(5.0) + 1) / 120, "lineintegral(x y, (t, t^2), t, 0, 1)", 1e-8)
        // Green's theorem: ∮ −y dx + x dy = 2·area = 2π; the field of the winding number gives 2π as well.
        exact("2π ≈ 6.283185307", "lineintegral((-y, x), (cos(t), sin(t)), t, 0, 2pi)")
        exact("2π ≈ 6.283185307", "lineintegral((-y/(x^2+y^2), x/(x^2+y^2)), (cos(t), sin(t)), t, 0, 2pi)")
        // A gradient field: ∇(x²y) from (0, 0) to (1, 1) is 1 along any curve.
        exact("1", "lineintegral((2x y, x^2), (t, t^3), t, 0, 1)")
        near(1.0, "lineintegral((2x y, x^2), (t, sin(pi t/2)), t, 0, 1)")
        // F(r(t)) · r′(t) = −sin²t + t cos t + cos t along the helix: −π.
        near(-PI, "lineintegral((y, z, x), (cos(t), sin(t), t), t, 0, 2pi)")
        // The flux of (x, y) out of the unit circle: ∬ div F dA = 2π.
        exact("2π ≈ 6.283185307", "flux((x, y), (cos(t), sin(t)), t, 0, 2pi)")
    }

    @Test
    fun surfaceIntegralsAndFlux() {
        // The unit sphere's area 4π; Gauss: the flux of (x, y, z) out of it is ∭ 3 dV = 4π.
        exact("4π ≈ 12.56637061", "surfaceintegral(1, (sin(u) cos(v), sin(u) sin(v), cos(u)), u, 0, pi, v, 0, 2pi)")
        exact("4π ≈ 12.56637061", "flux((x, y, z), (sin(u) cos(v), sin(u) sin(v), cos(u)), u, 0, pi, v, 0, 2pi)")
        // The cylinder r = 2, 0 ≤ z ≤ 3: area 2π·2·3 = 12π.
        near(12 * PI, "surfaceintegral(1, (2cos(u), 2sin(u), v), u, 0, 2pi, v, 0, 3)")
        // ∬ z dS over z = u² + v², [0, 1]²: a midpoint sum of z·√(1 + 4u² + 4v²), the test's own.
        val n = 2000
        var sum = 0.0
        for (i in 0 until n) for (j in 0 until n) {
            val u = (i + 0.5) / n
            val v = (j + 0.5) / n
            sum += (u * u + v * v) * sqrt(1 + 4 * u * u + 4 * v * v)
        }
        near(sum / (n.toDouble() * n), "surfaceintegral(z, (u, v, u^2 + v^2), u, 0, 1, v, 0, 1)", 1e-6)
        // A field and a surface that do not fit.
        assertEquals(Failure.ARGUMENTS, failure("surfaceintegral((x, y, z), (u, v, 0), u, 0, 1, v, 0, 1)"))
        assertEquals(Failure.ARGUMENTS, failure("flux(x, (u, v, 0), u, 0, 1, v, 0, 1)"))
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // At a point.

    @Test
    fun directionalDerivatives() {
        // ∇(x²y) = (2xy, x²) is (4, 1) at (1, 2), and (3, 4)/5 the direction: 12/5 + 4/5.
        exact("16/5 = 3.2", "directionalderivative(x^2 y, (1, 2), (3, 4))")
        // ∇(xyz) = (1, 1, 1) at (1, 1, 1), towards (1, 1, 1)/√3: √3.
        exact("√3 ≈ 1.732050808", "directionalderivative(x y z, (1, 1, 1), (1, 1, 1))")
        // Along a level curve of x² + y² the derivative is 0.
        exact("0", "directionalderivative(x^2 + y^2, (3, 4), (-4, 3))")
        near(-sqrt(2.0) / 2, "directionalderivative(sin(x) cos(y), (pi/2, pi/4), (0, 1))")
        assertEquals(Failure.UNDEFINED, failure("directionalderivative(x^2 y, (1, 2), (0, 0))"))
        assertEquals(Failure.UNDEFINED, failure("directionalderivative(abs(x), (0, 0), (1, 0))"))
        assertEquals(Failure.SIZES, failure("directionalderivative(x y, (1, 2), (1, 2, 3))"))
    }

    @Test
    fun tangentPlanes() {
        // z = x² + y² at (1, 2): z = 5 + 2(x − 1) + 4(y − 2).
        exact("2x + 4y - z - 5 = 0", "tangentplane(x^2 + y^2, (1, 2))")
        exact("2x + 4y - z - 5 = 0", "tangentplane(z = x^2 + y^2, (1, 2))")
        // The sphere x² + y² + z² = 14 at (1, 2, 3): its normal is the radius.
        exact("x + 2y + 3z - 14 = 0", "tangentplane(x^2 + y^2 + z^2 = 14, (1, 2, 3))")
        assertEquals(Failure.NOT_ON_SURFACE, failure("tangentplane(x^2 + y^2 + z^2 = 14, (1, 2, 4))"))
        // The cone's tip has no tangent plane.
        assertEquals(Failure.UNDEFINED, failure("tangentplane(x^2 + y^2 - z^2 = 0, (0, 0, 0))"))
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The Fourier transform.

    /** The transform's value at real ω against a table's formula. */
    private fun transform(text: String, letter: String, expected: (Double) -> Pair<Double, Double>) {
        val c = calc(text)
        val o = c.outcome as? Outcome.Expression ?: error("$text: ${answerOf(c)}")
        for (w in listOf(-2.3, -0.7, 0.4, 1.1, 3.6)) {
            val got = Verify.value(o.result, mapOf(letter to w))
            val (re, im) = expected(w)
            assertTrue("$text at $w: $got, not $re + ${im}i", abs(got.re - re) < 1e-9 && abs(got.im - im) < 1e-9)
        }
    }

    @Test
    fun fourierTransformsOfTheTable() {
        val w = "\\omega"
        transform("fouriertransform(e^(-abs(t)))", w) { x -> 2 / (1 + x * x) to 0.0 }
        transform("fouriertransform(e^(-t^2))", w) { x -> sqrt(PI) * exp(-x * x / 4) to 0.0 }
        transform("fouriertransform((sgn(t + 1) - sgn(t - 1))/2)", w) { x -> 2 * sin(x) / x to 0.0 }
        transform("fouriertransform(1/(1 + t^2))", w) { x -> PI * exp(-abs(x)) to 0.0 }
        transform("fouriertransform(sin(t)/t)", w) { x -> (if (abs(x) < 1) PI else 0.0) to 0.0 }
        transform("fouriertransform(1/cosh(t))", w) { x -> PI / cosh(PI * x / 2) to 0.0 }
        // e^(−t) for t > 0: 1/(1 + iω) = (1 − iω)/(1 + ω²).
        transform("fouriertransform(e^(-t) (1 + sgn(t))/2)", w) { x -> 1 / (1 + x * x) to -x / (1 + x * x) }
        // Shift: e^(−|t − 1|) is e^(−iω)·2/(1 + ω²); modulation: cos(3t)·e^(−t²) is the mean of the Gaussian's at ω ∓ 3.
        transform("fouriertransform(e^(-abs(t - 1)))", w) { x -> 2 * kotlin.math.cos(x) / (1 + x * x) to -2 * sin(x) / (1 + x * x) }
        transform("fouriertransform(cos(3t) e^(-t^2))", w) { x -> sqrt(PI) / 2 * (exp(-(x - 3) * (x - 3) / 4) + exp(-(x + 3) * (x + 3) / 4)) to 0.0 }
        // t·f(t) ↔ i·F′(ω): t·e^(−t²) ↔ −(iω/2)√π·e^(−ω²/4).
        transform("fouriertransform(t e^(-t^2))", w) { x -> 0.0 to -x / 2 * sqrt(PI) * exp(-x * x / 4) }
        transform("fouriertransform(t/(1 + t^2))", w) { x -> 0.0 to -PI * Math.signum(x) * exp(-abs(x)) }
    }

    @Test
    fun inverseFourierTransforms() {
        transform("invfouriertransform(2/(1 + omega^2))", "t") { x -> exp(-abs(x)) to 0.0 }
        transform("invfouriertransform(sqrt(pi) e^(-omega^2/4))", "t") { x -> exp(-x * x) to 0.0 }
        transform("invfouriertransform(1/(1 + i omega))", "t") { x -> (if (x > 0) exp(-x) else 0.0) to 0.0 }
        transform("invfouriertransform(1/(2 + i omega)^2)", "t") { x -> (if (x > 0) x * exp(-2 * x) else 0.0) to 0.0 }
        transform("invfouriertransform(2 sin(omega)/omega)", "t") { x -> (if (abs(x) < 1) 1.0 else 0.0) to 0.0 }
    }

    @Test
    fun noTransformAsAFunction() {
        // 1 and e^(−t) are not integrable over ℝ (their transforms need δ, or do not exist); cos t / t not at 0.
        assertEquals(Failure.UNSOLVED, failure("fouriertransform(1)"))
        assertEquals(Failure.DIVERGES, failure("fouriertransform(e^(-t))"))
        assertEquals(Failure.UNSOLVED, failure("fouriertransform(cos(t)/t)"))
        assertEquals(Failure.ARGUMENTS, failure("fouriertransform(e^(-x^2 - y^2))"))
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Steps and calls.

    private fun keys(text: String): String = TextInput.read(text).let { t -> Steps.of(t.latex, t.choice) }?.joinToString(" ") { it.why.key.name } ?: "none"

    @Test
    fun stepsOfEachKind() {
        assertEquals("START MULTI_INNER INTEGRATE_FROM_TO RESULT", keys("integral(x y, x, 0, 1, y, 0, 2)"))
        assertEquals("START MULTI_NUMERIC", keys("integral(sin(x y), x, 0, 1, y, 0, 1)"))
        assertEquals("START MULTI_COORDINATES MULTI_INNER INTEGRATE_FROM_TO RESULT", keys("polarintegral(x^2 + y^2, r, 0, 2, theta, 0, 2pi)"))
        assertEquals("START LINE_VELOCITY LINE_SPEED INTEGRATE_FROM_TO RESULT", keys("lineintegral(1, (cos(t), sin(t)), t, 0, 2pi)"))
        assertEquals("START LINE_VELOCITY LINE_DOT INTEGRATE_FROM_TO LINE_POTENTIAL RESULT", keys("lineintegral((2x y, x^2), (t, t^3), t, 0, 1)"))
        assertEquals("START LINE_VELOCITY LINE_NORMAL INTEGRATE_FROM_TO RESULT", keys("flux((x, y), (cos(t), sin(t)), t, 0, 2pi)"))
        assertEquals("START SURF_PARTIALS SURF_NORMAL SURF_AREA START MULTI_INNER INTEGRATE_FROM_TO RESULT",
            keys("surfaceintegral(1, (sin(u) cos(v), sin(u) sin(v), cos(u)), u, 0, pi, v, 0, 2pi)"))
        assertEquals("START DIR_GRADIENT DIR_AT DIR_UNIT DIR_DOT RESULT", keys("directionalderivative(x^2 y, (1, 2), (3, 4))"))
        assertEquals("START TP_GRAPH TP_NORMAL TP_EQUATION RESULT", keys("tangentplane(x^2 + y^2, (1, 2))"))
        assertEquals("START TP_NORMAL TP_EQUATION RESULT", keys("tangentplane(x^2 + y^2 + z^2 = 14, (1, 2, 3))"))
    }

    @Test
    fun callsAreWrittenBackAsCalls() {
        assertEquals("\\operatorname{integral}\\left(x y, x, 0, 1, y, 0, 2\\right)", CommandFunctions.latex(TextInput.read("integral(x y, x, 0, 1, y, 0, 2)")))
        assertEquals("\\operatorname{polarintegral}\\left(1, r, 0, 1, \\theta, 0, 2\\pi\\right)",
            CommandFunctions.latex(TextInput.read("polarintegral(1, r, 0, 1, θ, 0, 2pi)")))
        // Too few or too many arguments are never guessed.
        assertEquals(Failure.ARGUMENTS, failure("polarintegral(1, r, 0, 1)"))
        assertEquals(Failure.ARGUMENTS, failure("lineintegral(1, (cos(t), sin(t)), t, 0)"))
        assertEquals(Failure.ARGUMENTS, failure("integral(x y, x, 0, 1, y, 0)"))
        assertEquals(Failure.ARGUMENTS, failure("tangentplane(x^2 + y^2)"))
    }
}

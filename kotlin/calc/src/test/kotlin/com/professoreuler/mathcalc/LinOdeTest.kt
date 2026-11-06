package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The singular value decomposition, the Jordan form, systems of linear differential equations, boundary value problems, power
 * series solutions, the Dormand–Prince method, slope fields and phase portraits. Every answer is checked against a value known
 * independently: a textbook's, or one the test works out another way (doubles multiplied back, a Runge–Kutta integration, the
 * singular values of a circulant from its discrete Fourier transform).
 */
class LinOdeTest {
    private fun result(text: String): Calculation = Reports.of(TextInput.read(text)).result

    private fun answer(text: String): String = answerOf(result(text))

    private fun text(e: Expr) = Format.expr(e, Style.PLAIN, latex = false)

    private fun d(e: Expr): Double = Numeric.value(e) ?: Double.NaN

    private fun doubles(m: Expr.Mat) = Array(m.size) { i -> DoubleArray(m.columns) { j -> d(m.rows[i][j]) } }

    private fun times(a: Array<DoubleArray>, b: Array<DoubleArray>) =
        Array(a.size) { i -> DoubleArray(b[0].size) { j -> b.indices.sumOf { k -> a[i][k] * b[k][j] } } }

    private fun transposed(a: Array<DoubleArray>) = Array(a[0].size) { j -> DoubleArray(a.size) { i -> a[i][j] } }

    private fun assertNear(want: Array<DoubleArray>, got: Array<DoubleArray>, tol: Double, what: String) {
        for (i in want.indices) for (j in want[i].indices) assertTrue("$what [$i][$j]: ${got[i][j]}, not ${want[i][j]}", abs(want[i][j] - got[i][j]) <= tol)
    }

    private fun identity(n: Int) = Array(n) { i -> DoubleArray(n) { j -> if (i == j) 1.0 else 0.0 } }

    /** U·Σ·Vᵀ = A and U, V orthogonal, in doubles. */
    private fun checkSvd(a: Array<DoubleArray>, s: MatrixForms.Svd, tol: Double = 1e-8) {
        val u = doubles(s.u)
        val v = doubles(s.v)
        assertNear(a, times(times(u, doubles(s.sigma)), transposed(v)), tol, "UΣVᵀ")
        assertNear(identity(u.size), times(transposed(u), u), tol, "UᵀU")
        assertNear(identity(v.size), times(transposed(v), v), tol, "VᵀV")
        val values = s.values.map { d(it) }
        assertEquals(values.sortedDescending(), values)
    }

    private fun svd(text: String) = (result(text).outcome as Outcome.SingularValues).svd

    @Test
    fun singularValuesExact() {
        // AᵀA = [[25, 20], [20, 25]] has eigenvalues 45 and 5: σ = 3√5, √5.
        val s = svd("svd([[3, 0], [4, 5]])")
        assertTrue(s.exact)
        assertEquals(listOf("3√5", "√5"), s.values.map { text(it) })
        checkSvd(arrayOf(doubleArrayOf(3.0, 0.0), doubleArrayOf(4.0, 5.0)), s, 1e-12)
        // Rank 1: σ = 2 and 0.
        val r = svd("svd([[1, 1], [1, 1]])")
        assertEquals(listOf("2", "0"), r.values.map { text(it) })
        assertEquals(1, r.rank)
        // A wide matrix: through its transpose, Σ 2 × 3.
        val w = svd("svd([[2, 0, 0], [0, 3, 0]])")
        assertEquals(listOf("3", "2"), w.values.map { text(it) })
        checkSvd(arrayOf(doubleArrayOf(2.0, 0.0, 0.0), doubleArrayOf(0.0, 3.0, 0.0)), w)
        // A tall one: U is 3 × 3, its last column the null space of Aᵀ.
        val t = svd("svd([[1, 0], [0, 1], [1, 1]])")
        assertEquals(listOf("√3", "1"), t.values.map { text(it) })
        checkSvd(arrayOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 1.0), doubleArrayOf(1.0, 1.0)), t)
    }

    @Test
    fun singularValuesCertified() {
        // [[1, 2], [3, 4]]: σ² = 15 ± √221, exact, with U and V in decimals.
        val s = svd("svd([[1, 2], [3, 4]])")
        assertTrue(!s.exact && s.valuesExact)
        assertEquals(listOf("√(15 + √221)", "√(15 - √221)"), s.values.map { text(it) })
        assertEquals(sqrt(15 + sqrt(221.0)), d(s.values[0]), 1e-12)
        checkSvd(arrayOf(doubleArrayOf(1.0, 2.0), doubleArrayOf(3.0, 4.0)), s)
        // A 3 × 3 matrix: σ₁σ₂σ₃ = |det A| = 3 and σ₁² + σ₂² + σ₃² = 304, the sum of the squares of its entries.
        val a = arrayOf(doubleArrayOf(1.0, 2.0, 3.0), doubleArrayOf(4.0, 5.0, 6.0), doubleArrayOf(7.0, 8.0, 10.0))
        val c = svd("svd([[1, 2, 3], [4, 5, 6], [7, 8, 10]])")
        val v = c.values.map { d(it) }
        assertEquals(3.0, v[0] * v[1] * v[2], 1e-8)
        assertEquals(304.0, v.sumOf { it * it }, 1e-6)
        checkSvd(a, c)
        // Singular: σ₃ = 0 exactly (the rank by elimination), σ₁² + σ₂² = 285.
        val z = svd("svd([[1, 2, 3], [4, 5, 6], [7, 8, 9]])")
        assertEquals(2, z.rank)
        assertTrue(isZero(z.values[2]))
        assertEquals(285.0, z.values.sumOf { d(it) * d(it) }, 1e-6)
        // An 8 × 8 matrix whose rows turn its first one: its singular values are |Σⱼ (j + 1)ωʲᵏ|, ω = e^(2πi/8): 36, and
        // 8/|1 − ωᵏ| = 4/sin(kπ/8) for k = 1 … 7.
        val rows = (0 until 8).map { i -> (0 until 8).map { j -> (i + j) % 8 + 1 } }
        val big = svd("svd([" + rows.joinToString(", ") { r -> "[" + r.joinToString(", ") + "]" } + "])")
        val expected = (listOf(36.0) + (1..7).map { k -> 4 / sin(k * PI / 8) }).sortedDescending()
        for ((k, value) in big.values.withIndex()) assertEquals(expected[k], d(value), 1e-9 * expected[k])
        checkSvd(Array(8) { i -> DoubleArray(8) { j -> rows[i][j].toDouble() } }, big)
    }

    private fun jordan(text: String) = (result(text).outcome as Outcome.JordanForm).jordan

    /** A·P = P·J in doubles, and P invertible. */
    private fun checkJordan(a: Array<DoubleArray>, j: MatrixForms.Jordan) {
        val p = doubles(j.p)
        assertNear(times(a, p), times(p, doubles(j.j)), 1e-9, "AP = PJ")
        assertNear(identity(a.size), times(p, doubles(j.inverse)), 1e-9, "PP⁻¹")
    }

    @Test
    fun jordanForms() {
        // A Jordan block on its own.
        val b = jordan("jordan([[1, 1], [0, 1]])")
        assertEquals(listOf(MatrixForms.Block(Expr.ONE, 2)), b.blocks)
        // The textbook example (eigenvalues 1, 2, 4, 4 and one block of 4 of size 2).
        val a = arrayOf(doubleArrayOf(5.0, 4.0, 2.0, 1.0), doubleArrayOf(0.0, 1.0, -1.0, -1.0), doubleArrayOf(-1.0, -1.0, 3.0, 0.0), doubleArrayOf(1.0, 1.0, -1.0, 2.0))
        val w = jordan("jordan([[5, 4, 2, 1], [0, 1, -1, -1], [-1, -1, 3, 0], [1, 1, -1, 2]])")
        assertEquals("1×1, 2×1, 4×2", w.blocks.joinToString(", ") { "${text(it.value)}×${it.size}" })
        checkJordan(a, w)
        // (A − I) has rank 1 and (A − I)² = 0: one block of size 2 and one of size 1.
        val m = arrayOf(doubleArrayOf(2.0, 2.0, 3.0), doubleArrayOf(1.0, 3.0, 3.0), doubleArrayOf(-1.0, -2.0, -2.0))
        val n = jordan("jordan([[2, 2, 3], [1, 3, 3], [-1, -2, -2]])")
        assertEquals("1×2, 1×1", n.blocks.joinToString(", ") { "${text(it.value)}×${it.size}" })
        checkJordan(m, n)
        // One block of size 3.
        assertEquals("3×3", jordan("jordan([[3, 1, 0], [0, 3, 1], [0, 0, 3]])").blocks.joinToString { "${text(it.value)}×${it.size}" })
        // Diagonalizable, irrational eigenvalues (5 ± √33)/2.
        val q = jordan("jordan([[1, 2], [3, 4]])")
        assertTrue(q.diagonalizable)
        checkJordan(arrayOf(doubleArrayOf(1.0, 2.0), doubleArrayOf(3.0, 4.0)), q)
        assertEquals("SIZES", answer("jordan([[1, 2, 3], [4, 5, 6]])"))
    }

    /** The classical Runge–Kutta method for x′ = A·x + b(t), in 2000 steps from t = 0 to [t1]: an independent check. */
    private fun integrate(a: Array<DoubleArray>, b: (Double) -> DoubleArray, x0: DoubleArray, t1: Double): DoubleArray {
        val n = x0.size
        val steps = 2000
        val h = t1 / steps
        fun f(t: Double, x: DoubleArray) = DoubleArray(n) { i -> (0 until n).sumOf { j -> a[i][j] * x[j] } + b(t)[i] }
        var x = x0
        var t = 0.0
        repeat(steps) {
            val k1 = f(t, x)
            val k2 = f(t + h / 2, DoubleArray(n) { x[it] + h / 2 * k1[it] })
            val k3 = f(t + h / 2, DoubleArray(n) { x[it] + h / 2 * k2[it] })
            val k4 = f(t + h, DoubleArray(n) { x[it] + h * k3[it] })
            x = DoubleArray(n) { x[it] + h * (k1[it] + 2 * k2[it] + 2 * k3[it] + k4[it]) / 6 }
            t += h
        }
        return x
    }

    private fun system(text: String) = (result(text).outcome as Outcome.OdeSystemSolved).system

    private fun at(e: Expr, t: Double) = Numeric.eval(e, mapOf("t" to t))

    @Test
    fun linearSystems() {
        // x′ = 3x + y, y′ = x + 3y, x(0) = 2, y(0) = 0: eigenvalues 2 and 4, x = e²ᵗ + e⁴ᵗ, y = e⁴ᵗ − e²ᵗ.
        val s = system("dsolve(x' = 3x + y, y' = x + 3y, x(0) = 2, y(0) = 0)")
        assertEquals("x = e^(2t) + e^(4t), y = e^(4t) - e^(2t)", s.unknowns.indices.joinToString(", ") { "${s.unknowns[it]} = ${text(s.particular!![it])}" })
        assertEquals(Fields.Kind.NODE, s.equilibrium!!.kind)
        // Complex eigenvalues −1 ± 2i, checked against a numerical integration at t = 1.5.
        val c = system("dsolve(x' = -x - 2y, y' = 2x - y, x(0) = 1, y(0) = 0)")
        val num = integrate(arrayOf(doubleArrayOf(-1.0, -2.0), doubleArrayOf(2.0, -1.0)), { DoubleArray(2) }, doubleArrayOf(1.0, 0.0), 1.5)
        assertEquals(num[0], at(c.particular!![0], 1.5), 1e-9)
        assertEquals(num[1], at(c.particular!![1], 1.5), 1e-9)
        assertEquals(Fields.Kind.SPIRAL, c.equilibrium!!.kind)
        assertEquals(true, c.equilibrium!!.stable)
        // A double eigenvalue with one eigenvector: t·eᵗ appears.
        assertEquals("x = C_2te^t + C_1e^t, y = C_2e^t | (0, 0) degenerate_node unstable", answer("dsolve(x' = x + y, y' = y)"))
        // Three unknowns, eigenvalues 1 and −1/2 ± (√3/2)i, against the integration at t = 1.
        val three = system("dsolve(x' = y, y' = z, z' = x, x(0) = 1, y(0) = 0, z(0) = 0)")
        val n3 = integrate(arrayOf(doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0), doubleArrayOf(1.0, 0.0, 0.0)), { DoubleArray(3) },
            doubleArrayOf(1.0, 0.0, 0.0), 1.0)
        for (k in 0 until 3) assertEquals(n3[k], at(three.particular!![k], 1.0), 1e-9)
        // A forcing term: x′ = y, y′ = −x + t, x(0) = 0, y(0) = 2: x = t + sin t.
        val f = system("dsolve(x' = y, y' = -x + t, x(0) = 0, y(0) = 2)")
        val nf = integrate(arrayOf(doubleArrayOf(0.0, 1.0), doubleArrayOf(-1.0, 0.0)), { t -> doubleArrayOf(0.0, t) }, doubleArrayOf(0.0, 2.0), 2.0)
        assertEquals(2 + sin(2.0), at(f.particular!![0], 2.0), 1e-12)
        assertEquals(nf[0], at(f.particular!![0], 2.0), 1e-9)
        // Subscripted unknowns, and the lines typed without dsolve.
        assertEquals("x_1 = C_1·sin(t) - C_2·cos(t), x_2 = C_2·sin(t) + C_1·cos(t) | (0, 0) centre", answer("dsolve(x_1' = x_2, x_2' = -x_1)"))
        assertEquals("x = C_2e^(2t), y = C_1/e^t | (0, 0) saddle unstable", answer("x' = 2x, y' = -y"))
        // Nonlinear: not solved, but drawn.
        assertEquals("UNSOLVED", answer("dsolve(x' = x y, y' = x)"))
    }

    @Test
    fun anUnknownOfTOnItsOwn() {
        // x″ + 4x = 0, x(0) = 1, x′(0) = 0: x = cos 2t.
        assertEquals("x = C_2·sin(2t) + C_1·cos(2t) [constant_coefficients] | x = cos(2t)", answer("dsolve(x'' + 4x = 0, x(0) = 1, x'(0) = 0)"))
    }

    @Test
    fun boundaryValueProblems() {
        // y″ + y = 0 on [0, π]: π is an eigenvalue's point, so y(π) = 0 has the family C·sin x and y(π) = 1 none.
        assertEquals("infinitely many y = C_2·sin(x) (C_2 free)", answer("dsolve(y'' + y = 0, y(0) = 0, y(pi) = 0)"))
        assertEquals("none", answer("dsolve(y'' + y = 0, y(0) = 0, y(pi) = 1)"))
        assertEquals("unique y = sin(x)", answer("dsolve(y'' + y = 0, y(0) = 0, y(pi/2) = 1)"))
        assertEquals("unique y = 2·sin(2x) + cos(2x)", answer("dsolve(y'' + 4y = 0, y(0) = 1, y(pi/4) = 2)"))
        // A derivative given at the end: y′(π) = 1, y = −sin x.
        assertEquals("unique y = -sin(x)", answer("dsolve(y'' + y = 0, y(0) = 0, y'(pi) = 1)"))
        // y″ − y = 0, y(0) = 1, y(1) = 0: y = sinh(1 − x)/sinh 1.
        val p = (result("dsolve(y'' - y = 0, y(0) = 1, y(1) = 0)").outcome as Outcome.BoundaryValueSolved).problem
        for (x in listOf(0.0, 0.3, 0.8, 1.0)) assertEquals(kotlin.math.sinh(1 - x) / kotlin.math.sinh(1.0), Numeric.eval(p.solution!!, mapOf("x" to x)), 1e-12)
        // With a right-hand side: y″ + y = x on [0, π] has none (y = C₂ sin x + x cannot be 0 at π).
        assertEquals("none", answer("dsolve(y'' + y = x, y(0) = 0, y(pi) = 0)"))
    }

    @Test
    fun powerSeriesSolutions() {
        // y″ + y = 0, y(0) = 1, y′(0) = 0: the series of cos x.
        assertEquals("y = 1 - (1/2)x^2 + (1/24)x^4 - (1/720)x^6 + O(x^7) | a_{j+2} = -a_{j}/((j + 1)(j + 2))",
            answer("odeseries(y'' + y = 0, y(0) = 1, y'(0) = 0)"))
        // Airy's equation: a₃ = a₀/6, a₄ = a₁/12, a₆ = a₀/180, a₇ = a₁/504.
        assertEquals("y = a_0 + a_1x + (1/6)a_0x^3 + (1/12)a_1x^4 + (1/180)a_0x^6 + (1/504)a_1x^7 + O(x^9) | a_{j+2} = a_{j-1}/((j + 1)(j + 2))",
            answer("odeseries(y'' - x y = 0, 0, 8)"))
        // Legendre's equation with ℓ = 2: the even solution ends, 1 − 3x² (a multiple of P₂).
        assertEquals("y = a_0 + a_1x - 3a_0x^2 - (2/3)a_1x^3 - (1/5)a_1x^5 + O(x^7) | a_{j+2} = a_{j}(j - 2)(j + 3)/((j + 1)(j + 2))",
            answer("odeseries((1 - x^2) y'' - 2x y' + 6y = 0)"))
        // Hermite's equation with n = 2: 1 − 2x², a multiple of H₂ = 4x² − 2.
        assertTrue(answer("odeseries(y'' - 2x y' + 4y = 0, y(0) = 1, y'(0) = 0)").startsWith("y = 1 - 2x^2 + O(x^7)"))
        // At another point: y″ + y = 0, y(1) = 1, y′(1) = 0 is cos(x − 1).
        assertTrue(answer("odeseries(y'' + y = 0, y(1) = 1, y'(1) = 0, 1, 4)").startsWith("y = 1 - (1/2)(x - 1)^2 + (1/24)(x - 1)^4 + O((x - 1)^5)"))
        // A coefficient that is no polynomial: e^x y, y(0) = 1: the coefficients of the Taylor series, checked against a
        // Runge–Kutta integration of the system y′ = v, v′ = −eˣy at x = 0.1.
        val s = (result("odeseries(y'' + e^x y = 0, y(0) = 1, y'(0) = 0, 0, 10)").outcome as Outcome.SeriesSolved).solution
        val series = { x: Double -> s.terms.sumOf { (c, k) -> d(c) * Math.pow(x, k.toDouble()) } }
        var y = 1.0
        var v = 0.0
        val h = 0.1 / 1000
        var x = 0.0
        repeat(1000) {
            fun f(xx: Double, yy: Double, vv: Double) = vv to -exp(xx) * yy
            val (a1, b1) = f(x, y, v)
            val (a2, b2) = f(x + h / 2, y + h / 2 * a1, v + h / 2 * b1)
            val (a3, b3) = f(x + h / 2, y + h / 2 * a2, v + h / 2 * b2)
            val (a4, b4) = f(x + h, y + h * a3, v + h * b3)
            y += h * (a1 + 2 * a2 + 2 * a3 + a4) / 6
            v += h * (b1 + 2 * b2 + 2 * b3 + b4) / 6
            x += h
        }
        assertEquals(y, series(0.1), 1e-12)
        // First order: y′ = y, y(0) = 1 is the exponential series.
        assertTrue(answer("odeseries(y' = y, y(0) = 1, 0, 5)").startsWith("y = 1 + x + (1/2)x^2 + (1/6)x^3 + (1/24)x^4 + (1/120)x^5 + O(x^6)"))
        // A singular point, an initial value at another point than asked, a nonlinear equation.
        assertEquals("SINGULAR_POINT", answer("odeseries(x^2 y'' + y = 0)"))
        assertEquals("ARGUMENTS", answer("odeseries(y'' + y = 0, y(0) = 1, y'(0) = 0, 1, 4)"))
        assertEquals("UNSOLVED", answer("odeseries(y' = y^2, y(0) = 1)"))
        // A letter for the point is refused.
        assertEquals("ARGUMENTS", answer("odeseries(y'' + y = 0, a)"))
    }

    private fun table(text: String) = (result(text).outcome as Outcome.NumericTable).table

    @Test
    fun dormandPrince() {
        // y′ = y from y(0) = 1 to 1: e.
        val e = table("dormandprince(y' = y, y(0) = 1)")
        assertEquals(Numerics.Method.DORMAND_PRINCE, e.method)
        assertEquals(kotlin.math.E, e.result!!, 1e-8)
        assertEquals(listOf("x_k", "h_k", "y_k", "\\varepsilon_k", "y(x_k)"), e.columns)
        assertEquals(1.0, e.rows.last()[0], 0.0)
        // Adaptive: its steps are not all equal.
        assertTrue(e.rows.drop(1).map { it[1] }.distinct().size > 1)
        // y′ = −2xy to 3: e^(−9); y′ = x + y to 2: 2e² − 3.
        assertEquals(exp(-9.0), table("dormandprince(y' = -2 x y, y(0) = 1, 3)").result!!, 1e-9)
        assertEquals(2 * exp(2.0) - 3, table("dormandprince(y' = x + y, y(0) = 1, 2)").result!!, 1e-7)
        // Backwards, from 1 to 0: y′ = y, y(1) = e gives y(0) = 1.
        assertEquals(1.0, table("dormandprince(y' = y, y(1) = e, 0)").result!!, 1e-8)
        // A solution that leaves for infinity before the end (y′ = y², y(0) = 1 has a pole at 1): no table.
        val pole = answer("dormandprince(y' = y^2, y(0) = 1, 2)")
        assertTrue(pole, pole == "UNDEFINED" || pole == "TOO_LONG")
    }

    private fun field(text: String) = (result(text).outcome as Outcome.DirectionField).field

    @Test
    fun slopeFieldsAndPhasePortraits() {
        // The logistic equation's constant solutions y = 0 and y = 1.
        assertEquals("slope field y' = -y^2 + y | constant y = 0, y = 1", answer("slopefield(y' = y(1 - y))"))
        // τ = tr A, Δ = det A: a centre (τ = 0, Δ = 1), a saddle (Δ = −3), a stable spiral (τ = −1, τ² − 4Δ = −3), an unstable
        // star (A = I), a line of equilibria (Δ = 0).
        assertEquals(Fields.Kind.CENTRE, field("phaseportrait(x' = y, y' = -x)").equilibria.single().kind)
        assertEquals(Fields.Kind.SADDLE, field("phaseportrait(x' = x + y, y' = 4x + y)").equilibria.single().kind)
        val spiral = field("phaseportrait(x' = y, y' = -x - y)").equilibria.single()
        assertEquals(Fields.Kind.SPIRAL to true, spiral.kind to spiral.stable)
        val star = field("phaseportrait(x' = x, y' = y)").equilibria.single()
        assertEquals(Fields.Kind.STAR to false, star.kind to star.stable)
        assertEquals(Fields.Kind.NON_ISOLATED, field("phaseportrait(x' = x + 2y, y' = 2x + 4y)").equilibria.single().kind)
        // An equilibrium away from the origin: x′ = y, y′ = −x + 1 has its centre at (1, 0).
        assertEquals("(1, 0) centre", field("phaseportrait(x' = y, y' = 1 - x)").equilibria.single().let { "(" + it.at.joinToString(", ") { e -> text(e) } + ") " + it.kind.name.lowercase() })
        // Lotka–Volterra: a saddle at (0, 0); at (1, 1) the linearization is a centre, which does not decide a nonlinear system.
        assertEquals("phase portrait x' = -xy + x, y' = xy - y | (0, 0) saddle unstable; (1, 1) undecided", answer("phaseportrait(x' = x - x y, y' = x y - y)"))
        // The drawing: a curve of the centre's field stays on its circle; arrows of unit length on the window's scale.
        val centre = field("phaseportrait(x' = y, y' = -x)")
        val window = Fields.window(centre)
        val curve = Fields.trajectory(centre, 1.0, 0.0, window)
        assertTrue(curve.size > 100)
        for (k in curve.indices step 2) assertEquals(1.0, hypot(curve[k], curve[k + 1]), 1e-3)
        val arrows = Fields.arrows(centre, window, 10, 10)
        assertEquals(100, arrows.size)
        assertTrue(arrows.all { abs(hypot(it.dx, it.dy) - 1) < 1e-12 })
        // A slope field's curve through (0, 1) of y′ = y is y = eˣ.
        val growth = field("slopefield(y' = y)")
        val c = Fields.trajectory(growth, 0.0, 1.0, PlotWindow(-2.0, 2.0, -1.0, 8.0))
        for (k in c.indices step 2) assertEquals(exp(c[k]), c[k + 1], 2e-3 * maxOf(1.0, c[k + 1]))
    }

    /** The steps of each kind, in order. */
    @Test
    fun steps() {
        fun keys(latex: String, choice: Choice? = null) = Steps.of(latex, choice)?.joinToString(" ") { it.why.key.name } ?: "none"
        assertEquals("START SVD_GRAM SVD_VALUES SVD_V SVD_U SVD_CHECK", keys("\\begin{pmatrix}3&0\\\\4&5\\end{pmatrix}", Choice(Mode.SVD)))
        assertEquals("START CHARACTERISTIC EIGENVALUES JORDAN_RANKS JORDAN_CHAIN JORDAN_FORM", keys("\\begin{pmatrix}2&2&3\\\\1&3&3\\\\-1&-2&-2\\end{pmatrix}", Choice(Mode.JORDAN)))
        assertEquals("START SYS_MATRIX CHARACTERISTIC EIGENVALUES EIGENVECTOR EIGENVECTOR SYS_GENERAL SYS_CONSTANTS RESULT",
            keys("\\begin{cases}x'=3x+y\\\\y'=x+3y\\\\x(0)=2\\\\y(0)=0\\end{cases}"))
        assertEquals("START BVP_GENERAL BVP_SYSTEM BVP_FAMILY", keys("\\begin{cases}y''+y=0\\\\y(0)=0\\\\y(\\pi)=0\\end{cases}"))
        assertEquals("START BVP_GENERAL BVP_SYSTEM BVP_NONE", keys("\\begin{cases}y''+y=0\\\\y(0)=0\\\\y(\\pi)=1\\end{cases}"))
        assertEquals("START BVP_GENERAL BVP_SYSTEM BVP_UNIQUE", keys("\\begin{cases}y''+y=0\\\\y(0)=0\\\\y(\\frac{\\pi}{2})=1\\end{cases}"))
        assertEquals("START SERIES_ANSATZ SERIES_RECURRENCE SERIES_COEFFICIENTS RESULT", keys("\\begin{cases}y''+y=0\\\\y(0)=1\\\\y'(0)=0\\end{cases}", Choice(Mode.ODE_SERIES)))
        // The last step is the card's answer.
        val last = Steps.of("\\begin{cases}x'=3x+y\\\\y'=x+3y\\\\x(0)=2\\\\y(0)=0\\end{cases}")!!.last().latex
        assertEquals("x = e^{2t} + e^{4t},\\ y = e^{4t} - e^{2t}", last)
    }

    /** Arguments a function does not take are refused, never guessed. */
    @Test
    fun argumentsRefused() {
        assertEquals("ARGUMENTS", answer("svd([[1, 2], [3, 4]], 2)"))
        assertEquals("ARGUMENTS", answer("dormandprince(y' = y, y(0) = 1, 1, 2)"))
        assertEquals("ARGUMENTS", answer("dormandprince(y' = y, y(0) = 1, x)"))
        assertEquals("ARGUMENTS", answer("odeseries(y'' + y = 0, 0, 2.5)"))
        assertEquals("ARGUMENTS", answer("slopefield(y' = y, 2)"))
    }
}

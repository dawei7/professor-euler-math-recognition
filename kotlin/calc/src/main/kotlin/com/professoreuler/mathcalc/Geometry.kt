package com.professoreuler.mathcalc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cosh
import kotlin.math.hypot
import kotlin.math.sinh

/**
 * Plane geometry a course meets: a triangle from three of its sides and angles (SSS, SAS, ASA, AAS, and the ambiguous SSA with
 * none, one or two triangles), exact where its values allow; a conic from its equation (circle, ellipse, hyperbola, parabola:
 * centre, axes, foci, eccentricity, asymptotes, vertex and directrix, the angle of a turned one); the line through two points
 * and the plane through three. Each is checked: the triangle by the laws of sines and cosines, the conic by its standard form
 * multiplied out and the distances that define it, the plane by its points.
 */
object Geometry {
    private fun tidy(e: Expr) = Calculus.tidy(e)

    // ---------------------------------------------------------------------------------------------------------------------
    // Triangles.

    /** Sides a, b, c and the angles α, β, γ opposite them in degrees; its area, perimeter, and the radii of its circles. */
    class Triangle(val sides: List<Expr>, val angles: List<Expr>, val area: Expr, val perimeter: Expr, val circumradius: Expr, val inradius: Expr,
        val case: Case = Case.SSS, val given: Set<Int> = emptySet())

    /** What was given: three sides; two sides and the angle between them; two angles and a side; two sides and an angle opposite one. */
    enum class Case { SSS, SAS, ASA, SSA }

    /** The letters a side or an angle is written with: a, b, c; α, β, γ or A, B, C. */
    val SIDES = listOf("a", "b", "c")
    val ANGLES = listOf(listOf("\\alpha", "A", "α"), listOf("\\beta", "B", "β"), listOf("\\gamma", "C", "γ"))

    fun isTriangleLetter(s: String) = s in SIDES || ANGLES.any { s in it }

    private fun radians(degrees: Expr) = mul(degrees, Expr.PI, num(Rational.ONE / Rational.of(180)))
    private fun sin(degrees: Expr) = tidy(fn(Func.SIN, radians(degrees)))
    private fun cos(degrees: Expr) = tidy(fn(Func.COS, radians(degrees)))

    /** Whether e still holds a function that is no exact number (sin 20°): then its value is given as a decimal. */
    private fun unsettled(e: Expr): Boolean = when (e) {
        is Expr.Fn -> true
        is Expr.Add -> e.terms.any { unsettled(it) }
        is Expr.Mul -> e.factors.any { unsettled(it) }
        is Expr.Pow -> unsettled(e.base) || unsettled(e.exp)
        else -> false
    }

    private fun settle(e: Expr): Expr {
        val t = tidy(e)
        if (!unsettled(t) && t !is Expr.Real) return t
        return Expr.Real(Numeric.value(t) ?: throw CalcException(Failure.UNSOLVED))
    }

    /** The angle whose cosine is c, in degrees: exact where it is a known one (60°, 135°). */
    private fun angleOfCos(c: Expr): Expr {
        val r = tidy(fn(Func.ACOS, c))
        val turns = rational(tidy(r / Expr.PI))
        if (turns != null) return num(turns * Rational.of(180))
        val v = Numeric.value(c) ?: throw CalcException(Failure.UNSOLVED)
        if (v < -1 - 1e-12 || v > 1 + 1e-12) throw CalcException(Failure.NO_TRIANGLE)
        return Expr.Real(Math.toDegrees(kotlin.math.acos(v.coerceIn(-1.0, 1.0))))
    }

    private fun angleOfSin(s: Expr, obtuse: Boolean): Expr? {
        val v = Numeric.value(s) ?: return null
        if (v > 1 + 1e-12 || v <= 0) return null
        val r = tidy(fn(Func.ASIN, s))
        val turns = rational(tidy(r / Expr.PI))
        val acute: Expr = if (turns != null) num(turns * Rational.of(180)) else Expr.Real(Math.toDegrees(kotlin.math.asin(v.coerceAtMost(1.0))))
        return if (obtuse) settle(num(180) - acute) else acute
    }

    private fun value(e: Expr) = Numeric.value(e) ?: throw CalcException(Failure.UNSOLVED)

    /** The triangles with [given] values (side letters a, b, c; angles in degrees by index 0, 1, 2). */
    fun triangles(sides: Map<Int, Expr>, angles: Map<Int, Expr>): List<Triangle> {
        if (sides.size + angles.size != 3 || sides.isEmpty()) throw CalcException(Failure.UNREADABLE)
        if ((sides.values + angles.values).any { value(it) <= 0 }) throw CalcException(Failure.NO_TRIANGLE)
        val out = ArrayList<List<List<Expr>>>()
        val s = arrayOfNulls<Expr>(3)
        val a = arrayOfNulls<Expr>(3)
        sides.forEach { (k, v) -> s[k] = v }
        angles.forEach { (k, v) -> a[k] = v }
        fun finishFromSides(s3: List<Expr>) {
            val angle = (0..2).map { k ->
                val (i, j) = (0..2).filter { it != k }
                angleOfCos(tidy((pow(s3[i], Expr.TWO) + pow(s3[j], Expr.TWO) - pow(s3[k], Expr.TWO)) / mul(Expr.TWO, s3[i], s3[j])))
            }
            out += listOf(s3, angle)
        }
        when {
            sides.size == 3 -> {
                val v = s.map { value(it!!) }
                if ((0..2).any { k -> v[k] >= v.sum() - v[k] - 1e-12 * v.sum() }) throw CalcException(Failure.NO_TRIANGLE)
                finishFromSides(s.map { it!! })
            }
            angles.size == 2 -> {
                val k = (0..2).first { a[it] == null }
                a[k] = settle(num(180) - a.filterNotNull().fold(Expr.ZERO as Expr) { acc, x -> add(acc, x) })
                if (value(a[k]!!) <= 1e-12) throw CalcException(Failure.NO_TRIANGLE)
                val known = (0..2).first { s[it] != null }
                val ratio = tidy(s[known]!! / sin(a[known]!!))
                out += listOf((0..2).map { if (s[it] != null) s[it]!! else settle(mul(ratio, sin(a[it]!!))) }, a.map { it!! })
            }
            else -> {
                // Two sides and one angle.
                val k = (0..2).first { a[it] != null }
                if (s[k] == null) {
                    // SAS: the angle between the two sides.
                    val (i, j) = (0..2).filter { it != k }
                    if (value(a[k]!!) >= 180) throw CalcException(Failure.NO_TRIANGLE)
                    val third = settle(sqrt(tidy(pow(s[i]!!, Expr.TWO) + pow(s[j]!!, Expr.TWO) - mul(Expr.TWO, s[i]!!, s[j]!!, cos(a[k]!!)))))
                    val s3 = (0..2).map { if (it == k) third else s[it]!! }
                    val angle = (0..2).map { m ->
                        if (m == k) a[k]!! else {
                            val (p, q) = (0..2).filter { it != m }
                            angleOfCos(tidy((pow(s3[p], Expr.TWO) + pow(s3[q], Expr.TWO) - pow(s3[m], Expr.TWO)) / mul(Expr.TWO, s3[p], s3[q])))
                        }
                    }
                    out += listOf(s3, angle)
                } else {
                    // SSA: the angle opposite a known side; the other known side's angle by the law of sines.
                    if (value(a[k]!!) >= 180) throw CalcException(Failure.NO_TRIANGLE)
                    val j = (0..2).first { it != k && s[it] != null }
                    val m = (0..2).first { it != k && it != j }
                    val sinJ = tidy(mul(s[j]!!, sin(a[k]!!)) / s[k]!!)
                    val sv = value(sinJ)
                    if (sv > 1 + 1e-12) throw CalcException(Failure.NO_TRIANGLE)
                    for (obtuse in listOf(false, true)) {
                        val aj = angleOfSin(sinJ, obtuse) ?: continue
                        if (obtuse && abs(value(aj) - 90) < 1e-9) continue
                        val am = settle(num(180) - a[k]!! - aj)
                        if (value(am) <= 1e-9) continue
                        val ratio = tidy(s[k]!! / sin(a[k]!!))
                        val sm = settle(mul(ratio, sin(am)))
                        val s3 = (0..2).map { if (it == m) sm else s[it]!! }
                        val angle = (0..2).map { if (it == k) a[k]!! else if (it == j) aj else am }
                        out += listOf(s3, angle)
                    }
                }
            }
        }
        if (out.isEmpty()) throw CalcException(Failure.NO_TRIANGLE)
        val case = when {
            sides.size == 3 -> Case.SSS
            angles.size == 2 -> Case.ASA
            angles.keys.single() !in sides.keys -> Case.SAS
            else -> Case.SSA
        }
        return out.map { (s3, angle) ->
            // The laws of cosines and the sum of the angles, at the values.
            val v = s3.map { value(it) }
            val g = angle.map { value(it) }
            if (abs(g.sum() - 180) > 1e-7 || v.any { it <= 0 } || g.any { it <= 0 }) throw CalcException(Failure.NO_TRIANGLE)
            for (k in 0..2) {
                val (i, j) = (0..2).filter { it != k }
                val law = v[i] * v[i] + v[j] * v[j] - 2 * v[i] * v[j] * kotlin.math.cos(g[k] * PI / 180)
                if (abs(law - v[k] * v[k]) > 1e-7 * maxOf(1.0, v[k] * v[k])) throw CalcException(Failure.UNSOLVED)
            }
            val area = settle(mul(Expr.HALF, s3[1], s3[2], sin(angle[0])))
            val perimeter = settle(add(s3))
            val circumradius = settle(s3[0] / mul(Expr.TWO, sin(angle[0])))
            val inradius = settle(mul(Expr.TWO, area) / perimeter)
            Triangle(s3, angle, area, perimeter, circumradius, inradius, case, sides.keys + angles.keys.map { it + 3 })
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Conics.

    enum class ConicKind { CIRCLE, ELLIPSE, HYPERBOLA, PARABOLA }

    /** A point (x, y). */
    class Point(val x: Expr, val y: Expr)

    /**
     * A conic: its centre, semi-axes (along x and y, or the turned axes), foci, eccentricity, asymptotes (y = …), vertex and
     * directrix (a parabola's, as the letter and its value: y = −1), its turning [angle] (radians) where it is turned, and
     * [standard], whose value is 1 (an ellipse's, a hyperbola's) or 0 on the curve.
     */
    class Conic(val kind: ConicKind, val centre: Point?, val semiAxes: Pair<Expr, Expr>?, val foci: List<Point>, val eccentricity: Expr?,
        val asymptotes: List<Expr>, val vertex: Point?, val directrix: Pair<String, Expr>?, val angle: Expr?, val radius: Expr?,
        val standard: Pair<Expr, Expr>?)

    private val X = Expr.Sym("x")
    private val Y = Expr.Sym("y")

    /** The coefficients A … F of Ax² + Bxy + Cy² + Dx + Ey + F, or null where f is no polynomial of degree 2 in x and y. */
    internal fun quadratic(f: Expr): List<Rational>? {
        val k = Algebra.Kernels()
        val p = Algebra.polynomial(Algebra.expand(f), k) ?: return null
        if (!k.isEmpty) return null
        if (p.terms.keys.any { m -> m.powers.any { it.first != "x" && it.first != "y" } }) return null
        fun c(vararg powers: Pair<String, Int>) = p.terms[if (powers.isEmpty()) Monomial.ONE else Monomial(powers.toList())] ?: Rational.ZERO
        if (p.terms.keys.any { m -> m.powers.sumOf { it.second } > 2 }) return null
        val q = listOf(c("x" to 2), c("x" to 1, "y" to 1), c("y" to 2), c("x" to 1), c("y" to 1), c())
        if (q[0].signum == 0 && q[1].signum == 0 && q[2].signum == 0) return null
        return q
    }

    /** Whether f = 0 is a conic in x and y worth naming (both squares, or a product xy, or a parabola). */
    fun isConic(f: Expr): Boolean = quadratic(f) != null

    /** Whether both squares (or a product xy) are in it: an ellipse, a circle or a hyperbola rather than a function's parabola. */
    fun bothSquares(f: Expr): Boolean = quadratic(f)?.let { (it[0].signum != 0 && it[2].signum != 0) || it[1].signum != 0 } ?: false

    fun conic(f: Expr): Conic? {
        val q = quadratic(f) ?: return null
        val (a, b, c, d, e, ff) = q.map { num(it) }.let { Six(it) }
        val det3 = tidy(LinAlg.det(LinAlg.matrix(listOf(listOf(a, mul(Expr.HALF, b), mul(Expr.HALF, d)),
            listOf(mul(Expr.HALF, b), c, mul(Expr.HALF, e)), listOf(mul(Expr.HALF, d), mul(Expr.HALF, e), ff)))))
        // A pair of lines, a point: degenerate, no conic to describe.
        if (isZero(det3)) return null
        val discriminant = q[1] * q[1] - Rational.of(4) * q[0] * q[2]
        if (q[1].signum != 0) return turned(q, discriminant)
        val x = X
        val y = Y
        val conic = when {
            discriminant.signum == 0 -> parabola(q)
            else -> {
                val h = tidy(neg(d) / mul(Expr.TWO, a))
                val k = tidy(neg(e) / mul(Expr.TWO, c))
                val r = tidy(add(mul(a, pow(h, Expr.TWO)), mul(c, pow(k, Expr.TWO))) - ff)
                val ax = tidy(r / a)
                val by = tidy(r / c)
                val centre = Point(h, k)
                val vx = Numeric.value(ax)!!
                val vy = Numeric.value(by)!!
                val standard = add(pow(add(x, neg(h)), Expr.TWO) / ax, pow(add(y, neg(k)), Expr.TWO) / by) to Expr.ONE
                if (discriminant.signum < 0) {
                    if (vx <= 0 || vy <= 0) return null
                    val sa = tidy(sqrt(ax))
                    val sb = tidy(sqrt(by))
                    if (q[0] == q[2]) {
                        Conic(ConicKind.CIRCLE, centre, sa to sa, emptyList(), Expr.ZERO, emptyList(), null, null, null, sa,
                            add(pow(add(x, neg(h)), Expr.TWO), pow(add(y, neg(k)), Expr.TWO)) to ax)
                    } else {
                        val focal = tidy(sqrt(tidy(abs(ax, by))))
                        val alongX = vx > vy
                        val major = if (alongX) sa else sb
                        val foci = if (alongX) listOf(Point(tidy(h - focal), k), Point(tidy(h + focal), k)) else listOf(Point(h, tidy(k - focal)), Point(h, tidy(k + focal)))
                        Conic(ConicKind.ELLIPSE, centre, sa to sb, foci, tidy(focal / major), emptyList(), null, null, null, null, standard)
                    }
                } else {
                    val alongX = vx > 0
                    val aa = if (alongX) ax else by
                    val bb = tidy(neg(if (alongX) by else ax))
                    val sa = tidy(sqrt(aa))
                    val sb = tidy(sqrt(bb))
                    val focal = tidy(sqrt(tidy(add(aa, bb))))
                    val foci = if (alongX) listOf(Point(tidy(h - focal), k), Point(tidy(h + focal), k)) else listOf(Point(h, tidy(k - focal)), Point(h, tidy(k + focal)))
                    val slope = if (alongX) tidy(sb / sa) else tidy(sa / sb)
                    val asymptotes = listOf(tidy(add(k, mul(slope, add(x, neg(h))))), tidy(add(k, neg(mul(slope, add(x, neg(h)))))))
                    val std = if (alongX) add(pow(add(x, neg(h)), Expr.TWO) / aa, neg(pow(add(y, neg(k)), Expr.TWO) / bb))
                        else add(pow(add(y, neg(k)), Expr.TWO) / aa, neg(pow(add(x, neg(h)), Expr.TWO) / bb))
                    Conic(ConicKind.HYPERBOLA, centre, if (alongX) sa to sb else sb to sa, foci, tidy(focal / sa), asymptotes, null, null, null, null, std to Expr.ONE)
                }
            }
        } ?: return null
        check(conic, f)
        return conic
    }

    private class Six(val l: List<Expr>) {
        operator fun component1() = l[0]
        operator fun component2() = l[1]
        operator fun component3() = l[2]
        operator fun component4() = l[3]
        operator fun component5() = l[4]
        operator fun component6() = l[5]
    }

    private fun abs(a: Expr, b: Expr): Expr = if (Numeric.value(a)!! >= Numeric.value(b)!!) tidy(a - b) else tidy(b - a)

    private fun parabola(q: List<Rational>): Conic? {
        val x = X
        val y = Y
        if (q[2].signum == 0) {
            // A x² + D x + E y + F = 0 with E ≠ 0: (x − h)² = 4p(y − k).
            if (q[4].signum == 0) return null
            val a = num(q[0]); val d = num(q[3]); val e = num(q[4]); val f = num(q[5])
            val h = tidy(neg(d) / mul(Expr.TWO, a))
            val k = tidy(neg(add(mul(a, pow(h, Expr.TWO)), mul(d, h), f)) / e)
            val p = tidy(neg(e) / mul(num(4), a))
            return Conic(ConicKind.PARABOLA, null, null, listOf(Point(h, tidy(k + p))), Expr.ONE, emptyList(), Point(h, k), "y" to tidy(k - p), null, null,
                pow(add(x, neg(h)), Expr.TWO) to mul(tidy(mul(num(4), p)), add(y, neg(k))))
        }
        if (q[3].signum == 0) return null
        val c = num(q[2]); val d = num(q[3]); val e = num(q[4]); val f = num(q[5])
        val k = tidy(neg(e) / mul(Expr.TWO, c))
        val h = tidy(neg(add(mul(c, pow(k, Expr.TWO)), mul(e, k), f)) / d)
        val p = tidy(neg(d) / mul(num(4), c))
        return Conic(ConicKind.PARABOLA, null, null, listOf(Point(tidy(h + p), k)), Expr.ONE, emptyList(), Point(h, k), "x" to tidy(h - p), null, null,
            pow(add(y, neg(k)), Expr.TWO) to mul(tidy(mul(num(4), p)), add(x, neg(h))))
    }

    /** A turned conic (a term in xy): its kind, centre, the angle of its axes and its semi-axes from the eigenvalues. */
    private fun turned(q: List<Rational>, discriminant: Rational): Conic? {
        val (a, b, c, d, e, f) = q.map { num(it) }.let { Six(it) }
        val angle = if (q[0] == q[2]) tidy(mul(Expr.PI, num(Rational.ONE / Rational.of(4)))) else tidy(mul(Expr.HALF, fn(Func.ATAN, tidy(b / (a - c)))))
        if (discriminant.signum == 0) {
            return Conic(ConicKind.PARABOLA, null, null, emptyList(), Expr.ONE, emptyList(), null, null, angle, null, null)
        }
        // The centre: the gradient's zero.
        val solved = Solve.system(listOf(tidy(add(mul(Expr.TWO, a, X), mul(b, Y), d)), tidy(add(mul(b, X), mul(Expr.TWO, c, Y), e))), listOf("x", "y"))
            ?.singleOrNull() ?: return null
        val h = solved["x"] ?: return null
        val k = solved["y"] ?: return null
        val f0 = tidy(add(f, mul(Expr.HALF, add(mul(d, h), mul(e, k)))))
        // The quadratic form's eigenvalues λ₁, λ₂: λ₁X² + λ₂Y² = −F₀ along the turned axes.
        val m = LinAlg.matrix(listOf(listOf(a, mul(Expr.HALF, b)), listOf(mul(Expr.HALF, b), c)))
        val lambdas = (LinAlg.eigen(m) ?: return null).map { it.value }
        if (lambdas.size != 2) return null
        val axes = lambdas.map { tidy(neg(f0) / it) }
        val v = axes.map { Numeric.value(it) ?: return null }
        val kind = if (discriminant.signum < 0) ConicKind.ELLIPSE else ConicKind.HYPERBOLA
        if (kind == ConicKind.ELLIPSE && v.any { it <= 0 }) return null
        val semi = axes.map { tidy(sqrt(tidy(if (Numeric.value(it)!! < 0) neg(it) else it))) }
        val ecc = if (kind == ConicKind.ELLIPSE) {
            val (big, small) = if (v[0] >= v[1]) axes[0] to axes[1] else axes[1] to axes[0]
            tidy(sqrt(tidy(Expr.ONE - small / big)))
        } else {
            val (pos, negAxis) = if (v[0] > 0) axes[0] to axes[1] else axes[1] to axes[0]
            tidy(sqrt(tidy(Expr.ONE - negAxis / pos)))
        }
        return Conic(kind, Point(h, k), semi[0] to semi[1], emptyList(), ecc, emptyList(), null, null, angle, null, null)
    }

    /** The standard form multiplied out is the equation (up to a factor), and points on the curve keep its distances. */
    private fun check(k: Conic, f: Expr) {
        val (lhs, rhs) = k.standard ?: return
        val g = tidy(Algebra.expand(lhs - rhs))
        // f = λ·g for one number λ.
        val qf = quadratic(f) ?: throw CalcException(Failure.UNSOLVED)
        val qg = quadratic(g) ?: throw CalcException(Failure.UNSOLVED)
        val i = qg.indexOfFirst { it.signum != 0 }
        val lambda = qf[i] / qg[i]
        if (qf.indices.any { qf[it] != lambda * qg[it] }) throw CalcException(Failure.UNSOLVED)
        fun v(e: Expr) = Numeric.value(e) ?: throw CalcException(Failure.UNSOLVED)
        val c = k.centre
        val t = 0.7
        when (k.kind) {
            ConicKind.ELLIPSE -> {
                val (sa, sb) = k.semiAxes!!
                val px = v(c!!.x) + v(sa) * kotlin.math.cos(t)
                val py = v(c.y) + v(sb) * kotlin.math.sin(t)
                val sum = k.foci.sumOf { hypot(px - v(it.x), py - v(it.y)) }
                if (abs(sum - 2 * maxOf(v(sa), v(sb))) > 1e-9 * maxOf(1.0, sum)) throw CalcException(Failure.UNSOLVED)
            }
            ConicKind.HYPERBOLA -> {
                val (sa, sb) = k.semiAxes!!
                val alongX = abs(v(k.foci[0].y) - v(k.foci[1].y)) < 1e-12
                val px = v(c!!.x) + if (alongX) v(sa) * cosh(t) else v(sa) * sinh(t)
                val py = v(c.y) + if (alongX) v(sb) * sinh(t) else v(sb) * cosh(t)
                val (f1, f2) = k.foci
                val diff = abs(hypot(px - v(f1.x), py - v(f1.y)) - hypot(px - v(f2.x), py - v(f2.y)))
                val major = if (alongX) v(sa) else v(sb)
                if (abs(diff - 2 * major) > 1e-9 * maxOf(1.0, diff)) throw CalcException(Failure.UNSOLVED)
            }
            ConicKind.PARABOLA -> {
                val vertex = k.vertex!!
                val focus = k.foci.single()
                val (letter, line) = k.directrix!!
                val p = if (letter == "y") v(focus.y) - v(vertex.y) else v(focus.x) - v(vertex.x)
                val (px, py) = if (letter == "y") (v(vertex.x) + 2 * p * t) to (v(vertex.y) + p * t * t) else (v(vertex.x) + p * t * t) to (v(vertex.y) + 2 * p * t)
                val toFocus = hypot(px - v(focus.x), py - v(focus.y))
                val toLine = if (letter == "y") abs(py - v(line)) else abs(px - v(line))
                if (abs(toFocus - toLine) > 1e-9 * maxOf(1.0, toFocus)) throw CalcException(Failure.UNSOLVED)
            }
            ConicKind.CIRCLE -> {}
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Lines and planes.

    /**
     * The line through two points: its direction and distance; in the plane its equation y = mx + c (or x = c for an upright
     * one) as the letter and its value.
     */
    class Line(val points: List<Expr.Mat>, val direction: Expr.Mat, val distance: Expr, val equation: Pair<String, Expr>?)

    /** The plane through three points: a normal n and n·(x, y, z) = d, as the expression n·(x, y, z) − d. */
    class Plane(val points: List<Expr.Mat>, val normal: Expr.Mat, val equation: Expr)

    fun line(p: Expr.Mat, q: Expr.Mat): Line {
        if (p.size != q.size || p.columns != 1 || p.size !in 2..3) throw CalcException(Failure.SIZES)
        val d = LinAlg.column((0 until p.size).map { tidy(q.rows[it][0] - p.rows[it][0]) })
        if (d.rows.all { isZero(it[0]) }) throw CalcException(Failure.UNSOLVED)
        val distance = LinAlg.norm(d)
        val equation = if (p.size == 2) {
            val dx = d.rows[0][0]
            if (isZero(dx)) "x" to p.rows[0][0] else {
                val m = tidy(d.rows[1][0] / dx)
                // Collected: (2 − √2)x + √2, not −x√2 + 2x + √2.
                "y" to add(mul(m, X), tidy(p.rows[1][0] - mul(m, p.rows[0][0])))
            }
        } else null
        // Both points on it.
        if (equation != null) for (pt in listOf(p, q)) {
            val (letter, rhs) = equation
            val residual = if (letter == "x") tidy(pt.rows[0][0] - rhs) else tidy(pt.rows[1][0] - rhs.substitute(X, pt.rows[0][0]))
            if (!LinAlg.isNull(residual)) throw CalcException(Failure.UNSOLVED)
        }
        return Line(listOf(p, q), d, distance, equation)
    }

    fun plane(p: Expr.Mat, q: Expr.Mat, r: Expr.Mat): Plane {
        if (listOf(p, q, r).any { it.size != 3 || it.columns != 1 }) throw CalcException(Failure.SIZES)
        fun minus(a: Expr.Mat, b: Expr.Mat) = LinAlg.column((0..2).map { tidy(a.rows[it][0] - b.rows[it][0]) })
        var n = LinAlg.cross(minus(q, p), minus(r, p))
        if (n.rows.all { isZero(it[0]) }) throw CalcException(Failure.UNSOLVED)
        // Whole numbers without a common factor, the first not negative.
        val rs = n.rows.map { rational(it[0]) }
        if (rs.all { it != null }) {
            val den = rs.fold(java.math.BigInteger.ONE) { acc, x -> acc / acc.gcd(x!!.den) * x.den }
            val whole = rs.map { (it!! * Rational.of(den)).num }
            val g = whole.fold(java.math.BigInteger.ZERO) { acc, x -> acc.gcd(x) }
            val sign = if (whole.first { it.signum() != 0 }.signum() < 0) -1L else 1L
            n = LinAlg.column(whole.map { num(Rational.of(it / g * java.math.BigInteger.valueOf(sign))) })
        }
        val xyz = listOf(X, Y, Expr.Sym("z"))
        val d = LinAlg.dot(n, p)
        val equation = tidy(add((0..2).map { mul(n.rows[it][0], xyz[it]) }) - d)
        for (pt in listOf(p, q, r)) {
            val residual = tidy((0..2).fold(equation) { acc, k -> acc.substitute(xyz[k] as Expr.Sym, pt.rows[k][0]) })
            if (!LinAlg.isNull(residual)) throw CalcException(Failure.UNSOLVED)
        }
        return Plane(listOf(p, q, r), n, equation)
    }
}

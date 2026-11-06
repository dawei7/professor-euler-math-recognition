package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Statistics and geometry: the test's own sums of fractions, its own quadrature of the normal density, its own laws of sines
 * and cosines, conics built from their centre and axes, lines and planes put back through their points.
 */
internal object WrittenData {

    private fun median(s: List<Q>): Q = if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / q(2)

    private fun value(e: Expr) = at(e, emptyMap())

    fun statistics(): List<Task> {
        val r = Random(1662)
        val out = ArrayList<Task>()
        val area = "statistics"
        repeat(100) { i ->
            val n = r.int(2, 14)
            val xs = List(n) { if (i % 3 == 0) q(r.int(-40, 120).toLong(), 10) else q(r.int(-20, 60).toLong()) }
            val text = xs.joinToString(", ") { if (it.isInteger) "${it.n}" else java.math.BigDecimal(it.n).divide(java.math.BigDecimal(it.d)).toPlainString() }
            val mean = xs.fold(Q.ZERO) { a, b -> a + b } / q(n.toLong())
            val sorted = xs.sorted()
            val ss = xs.fold(Q.ZERO) { a, b -> a + (b - mean) * (b - mean) }
            val sd = sqrt((ss / q(n - 1L)).toDouble())
            // The quartiles as the medians of the lower and upper halves, the median left out where n is odd.
            val q1 = if (n / 2 == 0) sorted.first() else median(sorted.subList(0, n / 2))
            val q3 = if (n / 2 == 0) sorted.last() else median(sorted.subList((n + 1) / 2, n))
            out += Task(area, "stats($text)", "mean $mean, median ${median(sorted)}", judge = { c ->
                val k = (c.outcome as? Outcome.Statistics)?.summary
                k != null && k.n == n && close(value(k.mean), mean.toDouble()) && close(value(k.median), median(sorted).toDouble()) &&
                    close(value(k.sampleSd!!), sd) && close(value(k.q1), q1.toDouble()) && close(value(k.q3), q3.toDouble()) &&
                    close(value(k.min), sorted.first().toDouble()) && close(value(k.max), sorted.last().toDouble()) &&
                    close(value(k.populationVariance), (ss / q(n.toLong())).toDouble())
            })
            when (i % 4) {
                0 -> out += Task(area, "mean($text)", "$mean", judge = isExact(mean))
                1 -> out += Task(area, "median($text)", "", judge = isExact(median(sorted)))
                2 -> out += Task(area, "var($text)", "", judge = isExact(ss / q(n - 1L)))
                else -> out += Task(area, "sd($text)", "", judge = isNumber(sd))
            }
        }
        // Least-squares lines.
        repeat(50) {
            val n = r.int(3, 8)
            val pts = List(n) { q(r.int(-10, 10).toLong()) to q(r.int(-10, 10).toLong()) }.distinctBy { it.first }
            if (pts.size < 3) return@repeat
            val m = q(pts.size.toLong())
            val sx = pts.fold(Q.ZERO) { a, p -> a + p.first }; val sy = pts.fold(Q.ZERO) { a, p -> a + p.second }
            val sxx = pts.fold(Q.ZERO) { a, p -> a + p.first * p.first }; val sxy = pts.fold(Q.ZERO) { a, p -> a + p.first * p.second }
            val syy = pts.fold(Q.ZERO) { a, p -> a + p.second * p.second }
            val den = m * sxx - sx * sx
            val slope = (m * sxy - sx * sy) / den
            val intercept = (sy - slope * sx) / m
            val rden = (den * (m * syy - sy * sy)).toDouble()
            if (rden <= 0) return@repeat
            val rr = (m * sxy - sx * sy).toDouble() / sqrt(rden)
            out += Task(area, "regression(${pts.joinToString(", ") { "(${it.first}, ${it.second})" }})", "y = ${slope}x + $intercept", judge = { c ->
                val l = (c.outcome as? Outcome.Regression)?.line
                l != null && rational(l.slope)?.let { Q.of(it.num, it.den) } == slope && rational(l.intercept)?.let { Q.of(it.num, it.den) } == intercept && close(value(l.r), rr)
            })
        }
        // Distributions: binomial exactly; Poisson and the normal ones in doubles, the normal by the test's own quadrature.
        val pascal = Array(41) { arrayOfNulls<BigInteger>(41) }
        for (n in 0..40) for (k in 0..n) pascal[n][k] = if (k == 0 || k == n) BigInteger.ONE else pascal[n - 1][k - 1]!! + pascal[n - 1][k]!!
        repeat(40) {
            val n = r.int(1, 40); val k = r.int(0, n); val p = q(r.int(1, 19).toLong(), 20)
            fun pdf(j: Int) = Q.of(pascal[n][j]!!) * p.pow(j) * (Q.ONE - p).pow(n - j)
            val pt = java.math.BigDecimal(p.n).divide(java.math.BigDecimal(p.d)).toPlainString()
            out += Task(area, "binompdf($n, $pt, $k)", "", judge = isExact(pdf(k)))
            out += Task(area, "binomcdf($n, $pt, $k)", "", judge = isExact((0..k).fold(Q.ZERO) { a, j -> a + pdf(j) }))
        }
        repeat(30) {
            val lam = r.int(1, 12); val k = r.int(0, 15)
            fun pdf(j: Int) = Math.pow(lam.toDouble(), j.toDouble()) * exp(-lam.toDouble()) / (2..j).fold(1.0) { a, x -> a * x }
            out += Task(area, "poissonpdf($lam, $k)", "", judge = isNumber(pdf(k)))
            out += Task(area, "poissoncdf($lam, $k)", "", judge = isNumber((0..k).sumOf { pdf(it) }))
        }
        repeat(20) {
            val p = q(r.int(1, 9).toLong(), 10); val k = r.int(1, 12)
            out += Task(area, "geometpdf(${java.math.BigDecimal(p.n).divide(java.math.BigDecimal(p.d)).toPlainString()}, $k)", "", judge = isExact((Q.ONE - p).pow(k - 1) * p))
        }
        fun density(t: Double) = exp(-t * t / 2) / sqrt(2 * PI)
        // ∫ of the density between two points directly (no difference of two values near 1/2), to a tolerance below its own size.
        fun between(za: Double, zb: Double) = integrate(za, zb, 1e-14 * (zb - za) * maxOf(density(za), density(zb), if (za < 0 && zb > 0) density(0.0) else 0.0)) { density(it) }
        fun phi(z: Double) = if (z < 0) between(z - 30, z) else 1 - between(-z - 30, -z)
        repeat(30) {
            val mu = r.int(-20, 100); val sigma = r.int(1, 15); val a = mu + r.int(-30, 10); val b = a + r.int(1, 40)
            out += Task(area, "normalcdf($a, $b, $mu, $sigma)", "", judge = isNumber(between((a - mu).toDouble() / sigma, (b - mu).toDouble() / sigma), 1e-9))
            val x = mu + r.int(-20, 20)
            out += Task(area, "normalpdf($x, $mu, $sigma)", "", judge = isNumber(exp(-((x - mu).toDouble() / sigma).let { it * it } / 2) / (sigma * sqrt(2 * PI))))
        }
        for (z in listOf("-1.96, 1.96", "-1, 1", "-2, 2", "0, 1.5", "-3, 0.5")) {
            val (a, b) = z.split(", ").map { it.toDouble() }
            out += Task(area, "normalcdf($z)", "", judge = isNumber(between(a, b), 1e-9))
        }
        for (p in listOf(0.5, 0.9, 0.95, 0.975, 0.99, 0.1, 0.025, 0.3)) for ((mu, sigma) in listOf(0 to 1, 100 to 15)) {
            out += Task(area, if (mu == 0) "invnorm($p)" else "invnorm($p, $mu, $sigma)", "", judge = { c ->
                val v = numberOf(c)
                v != null && v.isReal && abs(phi((v.re - mu) / sigma) - p) < 1e-10
            })
        }
        return out
    }

    // -----------------------------------------------------------------------------------------------------------------------
    // Geometry.

    private fun deg(x: Double) = x * 180 / PI

    fun geometry(): List<Task> {
        val r = Random(1637)
        val out = ArrayList<Task>()
        val area = "geometry"
        fun triangles(c: Calculation) = (c.outcome as? Outcome.Triangles)?.triangles
        fun holds(t: Geometry.Triangle, a: Double, b: Double, cc: Double): Boolean {
            val s = t.sides.map(::value); val g = t.angles.map(::value)
            val heron = (a + b + cc) / 2
            return close(s[0], a, 1e-9) && close(s[1], b, 1e-9) && close(s[2], cc, 1e-9) &&
                close(g[0], deg(acos((b * b + cc * cc - a * a) / (2 * b * cc))), 1e-9) && close(g[1], deg(acos((a * a + cc * cc - b * b) / (2 * a * cc))), 1e-9) &&
                close(g[2], deg(acos((a * a + b * b - cc * cc) / (2 * a * b))), 1e-9) &&
                close(value(t.area), sqrt(heron * (heron - a) * (heron - b) * (heron - cc)), 1e-9)
        }
        repeat(60) {
            val s = List(3) { r.int(1, 20) }
            val (a, b, c) = s
            val possible = a + b > c && a + c > b && b + c > a
            out += if (!possible) Task(area, "triangle(a = $a, b = $b, c = $c)", "none", fails = setOf(Failure.NO_TRIANGLE)) { false }
            else Task(area, "triangle(a = $a, b = $b, c = $c)", "", judge = { calc -> triangles(calc)?.singleOrNull()?.let { holds(it, a.toDouble(), b.toDouble(), c.toDouble()) } == true })
        }
        repeat(40) {
            val a = r.int(1, 15); val b = r.int(1, 15); val g = r.int(5, 175)
            val c = sqrt(a * a + b * b - 2.0 * a * b * cos(g * PI / 180))
            out += Task(area, "triangle(a = $a, b = $b, gamma = $g°)", "c = $c", judge = { calc -> triangles(calc)?.singleOrNull()?.let { holds(it, a.toDouble(), b.toDouble(), c) } == true })
        }
        repeat(30) {
            val al = r.int(5, 120); val be = r.int(5, 170 - al); val c = r.int(1, 15)
            val ga = 180 - al - be
            val a = c * sin(al * PI / 180) / sin(ga * PI / 180); val b = c * sin(be * PI / 180) / sin(ga * PI / 180)
            out += Task(area, "triangle(alpha = $al°, beta = $be°, c = $c)", "", judge = { calc -> triangles(calc)?.singleOrNull()?.let { holds(it, a, b, c.toDouble()) } == true })
        }
        // Two sides and an angle opposite one: none, one or two triangles.
        repeat(40) {
            val a = r.int(1, 15); val b = r.int(1, 15); val al = r.int(10, 150)
            val h = b * sin(al * PI / 180)
            val sinB = h / a
            val betas = when {
                sinB > 1 + 1e-12 -> emptyList()
                abs(sinB - 1) < 1e-12 -> listOf(90.0)
                else -> listOf(deg(asin(sinB)), 180 - deg(asin(sinB))).filter { al + it < 180 - 1e-9 }
            }
            val sols = betas.map { be -> val ga = 180 - al - be; Triple(a.toDouble(), b.toDouble(), a * sin(ga * PI / 180) / sin(al * PI / 180)) }
            out += if (sols.isEmpty()) Task(area, "triangle(a = $a, b = $b, alpha = $al°)", "none", fails = setOf(Failure.NO_TRIANGLE)) { false }
            else Task(area, "triangle(a = $a, b = $b, alpha = $al°)", "${sols.size} triangles", judge = { calc ->
                val ts = triangles(calc)
                ts != null && ts.size == sols.size && sols.all { (x, y, z) -> ts.any { holds(it, x, y, z) } }
            })
        }
        // Conics from their centre and axes, multiplied out by the test.
        repeat(40) { i ->
            val h = r.int(-4, 4); val k = r.int(-4, 4); var a = r.int(1, 5); var b = r.int(1, 5)
            if (a == b) a += 1
            val hyperbola = i % 2 == 1
            val s = if (hyperbola) -1 else 1
            // b²(x − h)² ± a²(y − k)² = a²b²
            val cx2 = b * b; val cy2 = s * a * a
            val cx = -2 * h * b * b; val cy = -2 * s * k * a * a
            val c0 = b * b * h * h + s * a * a * k * k - a * a * b * b
            val text = listOf(term(cx2.toLong(), "x^2", true), term(cy2.toLong(), "y^2"), term(cx.toLong(), "x"), term(cy.toLong(), "y"), term(c0.toLong(), "")).joinToString("") + " = 0"
            val focal = if (hyperbola) sqrt(a * a + b * b + 0.0) else sqrt(abs(a * a - b * b) + 0.0)
            val foci = if (hyperbola || a > b) listOf(h - focal to k.toDouble(), h + focal to k.toDouble()) else listOf(h.toDouble() to k - focal, h.toDouble() to k + focal)
            val e = focal / (if (hyperbola || a > b) a else b)
            out += Task(area, "conic($text)", "centre ($h, $k), axes $a, $b", judge = { c ->
                val o = (c.outcome as? Outcome.ConicSection)?.conic
                o != null && o.kind == (if (hyperbola) Geometry.ConicKind.HYPERBOLA else Geometry.ConicKind.ELLIPSE) &&
                    o.centre?.let { close(value(it.x), h.toDouble()) && close(value(it.y), k.toDouble()) } == true &&
                    o.semiAxes?.let { (p, q) -> close(value(p), a.toDouble()) && close(value(q), b.toDouble()) } == true &&
                    o.foci.size == 2 && foci.all { (fx, fy) -> o.foci.any { close(value(it.x), fx, 1e-9, 1e-9) && close(value(it.y), fy, 1e-9, 1e-9) } } &&
                    o.eccentricity?.let { close(value(it), e) } == true
            })
        }
        repeat(20) {
            val p = r.int(-5, 5, nonzero = true)
            out += Task(area, "conic(y^2 = ${4 * p}x)", "focus ($p, 0), directrix x = ${-p}", judge = { c ->
                val o = (c.outcome as? Outcome.ConicSection)?.conic
                o != null && o.kind == Geometry.ConicKind.PARABOLA && o.foci.singleOrNull()?.let { close(value(it.x), p.toDouble()) && close(value(it.y), 0.0) } == true &&
                    o.directrix?.let { it.first == "x" && close(value(it.second), -p.toDouble()) } == true && o.vertex?.let { close(value(it.x), 0.0) && close(value(it.y), 0.0) } == true
            })
            val h = r.int(-5, 5); val k = r.int(-5, 5); val rad = r.int(1, 6)
            val text = "x^2 + y^2" + term((-2 * h).toLong(), "x") + term((-2 * k).toLong(), "y") + term((h * h + k * k - rad * rad).toLong(), "") + " = 0"
            out += Task(area, "conic($text)", "circle ($h, $k), r = $rad", judge = { c ->
                val o = (c.outcome as? Outcome.ConicSection)?.conic
                o != null && o.kind == Geometry.ConicKind.CIRCLE && o.centre?.let { close(value(it.x), h.toDouble()) && close(value(it.y), k.toDouble()) } == true &&
                    (o.radius ?: o.semiAxes?.first)?.let { close(value(it), rad.toDouble()) } == true
            })
        }
        // Lines and planes through points, put back.
        repeat(30) {
            val p = List(2) { r.int(-6, 6) }; val q = List(2) { r.int(-6, 6) }
            if (p == q) return@repeat
            out += Task(area, "line((${p[0]}, ${p[1]}), (${q[0]}, ${q[1]}))", "", judge = { c ->
                val l = (c.outcome as? Outcome.LineThrough)?.line
                l != null && close(value(l.distance), sqrt(((q[0] - p[0]) * (q[0] - p[0]) + (q[1] - p[1]) * (q[1] - p[1])).toDouble())) &&
                    l.equation?.let { (letter, rhs) ->
                        listOf(p, q).all { pt -> if (letter == "y") close(at(rhs, mapOf("x" to pt[0].toDouble())), pt[1].toDouble(), 1e-12, 1e-12) else close(at(rhs, mapOf("y" to pt[1].toDouble())), pt[0].toDouble(), 1e-12, 1e-12) }
                    } == true
            })
        }
        repeat(15) {
            val p = List(3) { r.int(-6, 6) }; val q = List(3) { r.int(-6, 6) }
            if (p == q) return@repeat
            out += Task(area, "line((${p.joinToString(", ")}), (${q.joinToString(", ")}))", "", judge = { c ->
                val l = (c.outcome as? Outcome.LineThrough)?.line
                val d = l?.direction?.rows?.map { value(it[0]) }
                l != null && d != null && close(value(l.distance), sqrt((0..2).sumOf { (q[it] - p[it]) * (q[it] - p[it]) }.toDouble())) &&
                    // The direction is parallel to Q − P.
                    (0..2).all { i -> (0..2).all { j -> abs(d[i] * (q[j] - p[j]) - d[j] * (q[i] - p[i])) < 1e-9 } } && d.any { abs(it) > 1e-12 }
            })
        }
        repeat(25) {
            val pts = List(3) { List(3) { r.int(-5, 5) } }
            val u = (0..2).map { pts[1][it] - pts[0][it] }; val v = (0..2).map { pts[2][it] - pts[0][it] }
            val n = listOf(u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
            if (n.all { it == 0 }) {
                out += Task(area, "plane(${pts.joinToString(", ") { "(${it.joinToString(", ")})" }})", "on one line", fails = setOf(Failure.UNSOLVED)) { false }
                return@repeat
            }
            out += Task(area, "plane(${pts.joinToString(", ") { "(${it.joinToString(", ")})" }})", "", judge = { c ->
                val pl = (c.outcome as? Outcome.PlaneThrough)?.plane
                pl != null && pts.all { pt -> abs(at(pl.equation, mapOf("x" to pt[0].toDouble(), "y" to pt[1].toDouble(), "z" to pt[2].toDouble()))) < 1e-9 } &&
                    // Not identically 0: some point off the plane.
                    abs(at(pl.equation, mapOf("x" to pts[0][0] + n[0].toDouble(), "y" to pts[0][1] + n[1].toDouble(), "z" to pts[0][2] + n[2].toDouble()))) > 1e-9
            })
        }
        return out
    }
}

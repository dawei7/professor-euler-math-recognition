package com.professoreuler.mathcalc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Several variables. Integrals of polynomials over boxes and over regions between curves exactly (the test's own antiderivatives in
 * fractions); in polar, cylindrical and spherical coordinates as products of one-dimensional integrals by the test's quadrature (a
 * monomial separates in r, θ, z or φ); line and surface integrals and fluxes by the test's quadrature of the parametrization with
 * its derivatives by hand; directional derivatives and tangent planes of polynomials exactly; Fourier transforms against the
 * table, F(ω) = ∫ f(t)e^(−iωt) dt, with random parameters, compared at points of ω.
 */
internal object WrittenMultivar {
    private const val AREA = "several variables"

    /** A polynomial in several letters: monomials (exponents, coefficient). */
    private class Poly(val terms: List<Pair<List<Int>, Long>>) {
        fun text(letters: List<String>): String {
            val parts = terms.filter { it.second != 0L }.mapIndexed { k, (e, c) ->
                val body = e.indices.filter { e[it] > 0 }.joinToString(" ") { if (e[it] == 1) letters[it] else "${letters[it]}^${e[it]}" }
                term(c, body, first = k == 0)
            }
            return parts.joinToString("").ifEmpty { "0" }
        }
        fun at(v: DoubleArray): Double = terms.sumOf { (e, c) -> c * e.indices.fold(1.0) { p, i -> p * Math.pow(v[i], e[i].toDouble()) } }
        /** ∂/∂ letter i, at v. */
        fun d(i: Int, v: DoubleArray): Double = terms.sumOf { (e, c) ->
            if (e[i] == 0) 0.0 else c * e[i] * e.indices.fold(1.0) { p, j -> p * Math.pow(v[j], (if (j == i) e[j] - 1 else e[j]).toDouble()) }
        }
    }

    private fun Random.poly(letters: Int, count: Int, degree: Int = 2): Poly =
        Poly((0 until count).map { List(letters) { int(0, degree) } to int(-4, 4, nonzero = true).toLong() }.distinctBy { it.first })

    /** ∫ₐᵇ f by the test's quadrature, and ∫ₐᵇ |f|, the size the first is known to. */
    private fun both(a: Double, b: Double, f: (Double) -> Double) = integrate(a, b, 1e-12, f) to integrate(a, b) { abs(f(it)) }

    /** ∫ₐᵇ xᵏ dx, exactly. */
    private fun power(k: Int, a: Q, b: Q): Q = (b.pow(k + 1) - a.pow(k + 1)) / q(k + 1L)

    private fun boxes(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(60) { i ->
            val three = i % 3 == 0
            val n = if (three) 3 else 2
            val p = r.poly(n, r.int(1, 4))
            val bounds = List(n) { val a = r.int(-2, 2); q(a.toLong()) to q((a + r.int(1, 3)).toLong()) }
            val want = p.terms.fold(Q.ZERO) { s, (e, c) -> s + q(c) * e.indices.fold(Q.ONE) { m, j -> m * power(e[j], bounds[j].first, bounds[j].second) } }
            val letters = listOf("x", "y", "z").take(n)
            val call = "integral(${p.text(letters)}, " + letters.indices.joinToString(", ") { "${letters[it]}, ${bounds[it].first}, ${bounds[it].second}" } + ")"
            out += Task(AREA, call, "$want", judge = isExact(want))
        }
        // Between curves: ∫ₐᵇ ∫ from g₁(x) to g₂(x) of Σ c·xⁱyʲ dy dx, the inner integral (g₂ʲ⁺¹ − g₁ʲ⁺¹)/(j + 1) a polynomial in x.
        repeat(40) {
            val p = r.poly(2, r.int(1, 3))
            val g1 = QP.ofLongs(List(r.int(1, 3)) { r.int(-2, 2).toLong() })
            val g2 = QP.plus(g1, QP.ofLongs(List(r.int(1, 2)) { r.int(0, 3).toLong() }))
            val a = r.int(-1, 1); val b = a + r.int(1, 2)
            fun pw(g: List<Q>, k: Int) = (1..k).fold(listOf(Q.ONE)) { acc, _ -> QP.times(acc, g) }
            val inner = p.terms.fold(listOf(Q.ZERO)) { acc, (e, c) ->
                val (i, j) = e
                val y = QP.scale(QP.plus(pw(g2, j + 1), QP.scale(pw(g1, j + 1), -Q.ONE)), q(c, j + 1L))
                QP.plus(acc, QP.times(y, pw(listOf(Q.ZERO, Q.ONE), i)))
            }
            val want = QP.integral(inner, q(a.toLong()), q(b.toLong()))
            out += Task(AREA, "integral(${p.text(listOf("x", "y"))}, y, ${QP.text(g1)}, ${QP.text(g2)}, x, $a, $b)", "$want", judge = isExact(want))
        }
        // Products that separate: e^(ax)·cos(by), x·e^(y), sin(x)sin(y) over boxes, each factor by the test's quadrature.
        val factors = listOf<Pair<String, (Double) -> Double>>("e^(%s)" to ::exp, "cos(%s)" to ::cos, "sin(%s)" to ::sin, "%s" to { it }, "e^(-%s)" to { exp(-it) })
        repeat(30) {
            val (f, ff) = factors.random(r); val (g, gg) = factors.random(r)
            val a = r.int(-1, 1).toDouble(); val b = a + r.int(1, 2); val c = r.int(0, 1).toDouble(); val d = c + r.int(1, 2)
            val want = integrate(a, b, 1e-13, ff) * integrate(c, d, 1e-13, gg)
            val size = integrate(a, b) { abs(ff(it)) } * integrate(c, d) { abs(gg(it)) }
            out += Task(AREA, "integral(${f.format("x")} ${g.format("y")}, x, ${fmt(a)}, ${fmt(b)}, y, ${fmt(c)}, ${fmt(d)})", "", judge = isValue(want, size))
        }
        return out
    }

    private fun coordinates(r: Random): List<Task> {
        val out = ArrayList<Task>()
        val angles = listOf("0" to 0.0, "pi/6" to PI / 6, "pi/4" to PI / 4, "pi/3" to PI / 3, "pi/2" to PI / 2, "pi" to PI, "2pi" to 2 * PI)
        // Polar: x = r cos θ, y = r sin θ, dA = r dr dθ; a monomial xⁱyʲ is r^(i+j+1)·cosⁱθ·sinʲθ.
        repeat(40) {
            val p = r.poly(2, r.int(1, 3))
            val ra = r.int(0, 1).toDouble(); val rb = ra + r.int(1, 2)
            val lo = r.nextInt(4); val hi = r.int(lo + 1, angles.size - 1)
            val (ta, tav) = angles[lo]; val (tb, tbv) = angles[hi]
            val parts = p.terms.map { (e, c) ->
                val (i, j) = e
                fun part(f: (Double) -> Double) = integrate(ra, rb) { f(Math.pow(it, (i + j + 1).toDouble())) } *
                    integrate(tav, tbv) { f(Math.pow(cos(it), i.toDouble()) * Math.pow(sin(it), j.toDouble())) }
                c * part { it } to abs(c) * part { abs(it) }
            }
            out += Task(AREA, "polarintegral(${p.text(listOf("x", "y"))}, r, ${fmt(ra)}, ${fmt(rb)}, theta, $ta, $tb)", "",
                judge = isValue(parts.sumOf { it.first }, parts.sumOf { it.second }))
        }
        // Cylindrical: x = r cos θ, y = r sin θ, z, dV = r dz dθ dr.
        repeat(30) {
            val p = r.poly(3, r.int(1, 3))
            val rb = r.int(1, 2).toDouble()
            val (tb, tbv) = angles[r.int(3, angles.size - 1)]
            val za = r.int(-1, 0).toDouble(); val zb = za + r.int(1, 2)
            val parts = p.terms.map { (e, c) ->
                val (i, j, k) = e
                fun part(f: (Double) -> Double) = integrate(0.0, rb) { f(Math.pow(it, (i + j + 1).toDouble())) } *
                    integrate(0.0, tbv) { f(Math.pow(cos(it), i.toDouble()) * Math.pow(sin(it), j.toDouble())) } * integrate(za, zb) { f(Math.pow(it, k.toDouble())) }
                c * part { it } to abs(c) * part { abs(it) }
            }
            out += Task(AREA, "cylindricalintegral(${p.text(listOf("x", "y", "z"))}, r, 0, ${fmt(rb)}, theta, 0, $tb, z, ${fmt(za)}, ${fmt(zb)})", "",
                judge = isValue(parts.sumOf { it.first }, parts.sumOf { it.second }))
        }
        // Spherical: x = ρ sin θ cos φ, y = ρ sin θ sin φ, z = ρ cos θ (θ from the z-axis), dV = ρ² sin θ dρ dθ dφ.
        repeat(30) {
            val p = r.poly(3, r.int(1, 2))
            val rb = r.int(1, 2).toDouble()
            val (thb, thv) = listOf("pi/2" to PI / 2, "pi" to PI, "pi/3" to PI / 3).random(r)
            val (phb, phv) = angles[r.int(3, angles.size - 1)]
            val parts = p.terms.map { (e, c) ->
                val (i, j, k) = e
                fun part(f: (Double) -> Double) = integrate(0.0, rb) { f(Math.pow(it, (i + j + k + 2).toDouble())) } *
                    integrate(0.0, thv) { f(Math.pow(sin(it), (i + j + 1).toDouble()) * Math.pow(cos(it), k.toDouble())) } *
                    integrate(0.0, phv) { f(Math.pow(cos(it), i.toDouble()) * Math.pow(sin(it), j.toDouble())) }
                c * part { it } to abs(c) * part { abs(it) }
            }
            out += Task(AREA, "sphericalintegral(${p.text(listOf("x", "y", "z"))}, r, 0, ${fmt(rb)}, theta, 0, $thb, phi, 0, $phb)", "",
                judge = isValue(parts.sumOf { it.first }, parts.sumOf { it.second }))
        }
        return out
    }

    /** A curve: its components as typed and their values and derivatives at t. */
    private class Curve(val text: String, val at: (Double) -> DoubleArray, val d: (Double) -> DoubleArray, val a: String, val av: Double, val b: String, val bv: Double)

    private fun Random.curve(three: Boolean): Curve {
        if (nextInt(3) == 0) {
            // A circle (or a helix) of radius k.
            val k = int(1, 3)
            val (bt, bv) = listOf("2pi" to 2 * PI, "pi" to PI, "pi/2" to PI / 2).random(this)
            return if (three) Curve("(${k}cos(t), ${k}sin(t), t)", { t -> doubleArrayOf(k * cos(t), k * sin(t), t) }, { t -> doubleArrayOf(-k * sin(t), k * cos(t), 1.0) }, "0", 0.0, bt, bv)
            else Curve("(${k}cos(t), ${k}sin(t))", { t -> doubleArrayOf(k * cos(t), k * sin(t)) }, { t -> doubleArrayOf(-k * sin(t), k * cos(t)) }, "0", 0.0, bt, bv)
        }
        val n = if (three) 3 else 2
        val comps = List(n) { QP.ofLongs(List(int(2, 3)) { k -> int(-2, 2, nonzero = k == 1).toLong() }) }
        val a = int(-1, 0); val b = a + int(1, 2)
        return Curve(comps.joinToString(", ", "(", ")") { QP.text(it, "t") }, { t -> DoubleArray(n) { QP.at(comps[it], t) } },
            { t -> DoubleArray(n) { QP.at(QP.derivative(comps[it]), t) } }, "$a", a.toDouble(), "$b", b.toDouble())
    }

    private fun curves(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(40) { i ->
            val three = i % 4 == 3
            val c = r.curve(three)
            val n = if (three) 3 else 2
            val letters = listOf("x", "y", "z").take(n)
            // A scalar field along the curve: ∫ f(r(t))·|r′(t)| dt.
            val f = r.poly(n, r.int(1, 3))
            val (want, size) = both(c.av, c.bv) { t -> f.at(c.at(t)) * sqrt(c.d(t).sumOf { it * it }) }
            out += Task(AREA, "lineintegral(${f.text(letters)}, ${c.text}, t, ${c.a}, ${c.b})", "", judge = isValue(want, size))
            // A vector field: ∫ F(r(t))·r′(t) dt.
            val field = List(n) { r.poly(n, r.int(1, 2)) }
            val (work, workSize) = both(c.av, c.bv) { t -> val p = c.at(t); val d = c.d(t); (0 until n).sumOf { field[it].at(p) * d[it] } }
            out += Task(AREA, "lineintegral(${field.joinToString(", ", "(", ")") { it.text(letters) }}, ${c.text}, t, ${c.a}, ${c.b})", "", judge = isValue(work, workSize))
            if (!three) {
                // The flux through a plane curve: ∫ (P·y′ − Q·x′) dt.
                val (flux, fluxSize) = both(c.av, c.bv) { t -> val p = c.at(t); val d = c.d(t); field[0].at(p) * d[1] - field[1].at(p) * d[0] }
                out += Task(AREA, "flux(${field.joinToString(", ", "(", ")") { it.text(letters) }}, ${c.text}, t, ${c.a}, ${c.b})", "", judge = isValue(flux, fluxSize))
            }
        }
        // Surfaces: graphs (u, v, p(u, v)) over rectangles, the sphere and the cylinder; |r_u × r_v| and F·(r_u × r_v) by hand.
        class Surface(val text: String, val at: (Double, Double) -> DoubleArray, val ru: (Double, Double) -> DoubleArray, val rv: (Double, Double) -> DoubleArray,
            val bounds: String, val u: Pair<Double, Double>, val v: Pair<Double, Double>)
        fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
        fun inside(a: Double, b: Double, c: Double, d: Double, f: (Double, Double) -> Double) =
            Gauss.integrate(a, b, 16) { u -> Gauss.integrate(c, d, 16) { v -> f(u, v) } } to Gauss.integrate(a, b, 16) { u -> Gauss.integrate(c, d, 16) { v -> abs(f(u, v)) } }
        repeat(30) { i ->
            val s = when (i % 3) {
                0 -> {
                    val p = r.poly(2, r.int(1, 3))
                    val ub = r.int(1, 2).toDouble(); val vb = r.int(1, 2).toDouble()
                    Surface("(u, v, ${p.text(listOf("u", "v"))})", { u, v -> doubleArrayOf(u, v, p.at(doubleArrayOf(u, v))) },
                        { u, v -> doubleArrayOf(1.0, 0.0, p.d(0, doubleArrayOf(u, v))) }, { u, v -> doubleArrayOf(0.0, 1.0, p.d(1, doubleArrayOf(u, v))) },
                        "u, 0, ${fmt(ub)}, v, 0, ${fmt(vb)}", 0.0 to ub, 0.0 to vb)
                }
                1 -> {
                    val k = r.int(1, 3).toDouble()
                    Surface("(${fmt(k)}sin(u) cos(v), ${fmt(k)}sin(u) sin(v), ${fmt(k)}cos(u))", { u, v -> doubleArrayOf(k * sin(u) * cos(v), k * sin(u) * sin(v), k * cos(u)) },
                        { u, v -> doubleArrayOf(k * cos(u) * cos(v), k * cos(u) * sin(v), -k * sin(u)) }, { u, v -> doubleArrayOf(-k * sin(u) * sin(v), k * sin(u) * cos(v), 0.0) },
                        "u, 0, pi, v, 0, 2pi", 0.0 to PI, 0.0 to 2 * PI)
                }
                else -> {
                    val k = r.int(1, 3).toDouble(); val h = r.int(1, 3).toDouble()
                    Surface("(${fmt(k)}cos(u), ${fmt(k)}sin(u), v)", { u, v -> doubleArrayOf(k * cos(u), k * sin(u), v) },
                        { u, _ -> doubleArrayOf(-k * sin(u), k * cos(u), 0.0) }, { _, _ -> doubleArrayOf(0.0, 0.0, 1.0) },
                        "u, 0, 2pi, v, 0, ${fmt(h)}", 0.0 to 2 * PI, 0.0 to h)
                }
            }
            val f = r.poly(3, r.int(1, 2))
            val (area, areaSize) = inside(s.u.first, s.u.second, s.v.first, s.v.second) { u, v -> f.at(s.at(u, v)) * sqrt(cross(s.ru(u, v), s.rv(u, v)).sumOf { it * it }) }
            out += Task(AREA, "surfaceintegral(${f.text(listOf("x", "y", "z"))}, ${s.text}, ${s.bounds})", "", judge = isValue(area, areaSize, 1e-8))
            val field = List(3) { r.poly(3, r.int(1, 2)) }
            val (flux, fluxSize) = inside(s.u.first, s.u.second, s.v.first, s.v.second) { u, v -> val p = s.at(u, v); val n = cross(s.ru(u, v), s.rv(u, v)); (0..2).sumOf { field[it].at(p) * n[it] } }
            out += Task(AREA, "flux(${field.joinToString(", ", "(", ")") { it.text(listOf("x", "y", "z")) }}, ${s.text}, ${s.bounds})", "", judge = isValue(flux, fluxSize, 1e-8))
        }
        return out
    }

    private fun derivatives(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(40) { i ->
            val n = if (i % 3 == 0) 3 else 2
            val letters = listOf("x", "y", "z").take(n)
            val f = r.poly(n, r.int(1, 4), degree = 3)
            val p = DoubleArray(n) { r.int(-2, 2).toDouble() }
            val u = IntArray(n) { r.int(-3, 3) }
            if (u.all { it == 0 }) u[0] = 1
            val grad = DoubleArray(n) { f.d(it, p) }
            val norm = sqrt(u.sumOf { (it * it).toDouble() })
            out += Task(AREA, "directionalderivative(${f.text(letters)}, ${p.joinToString(", ", "(", ")") { fmt(it) }}, ${u.joinToString(", ", "(", ")")})", "",
                judge = isValue((0 until n).sumOf { grad[it] * u[it] } / norm, (0 until n).sumOf { abs(grad[it] * u[it]) } / norm, 1e-10))
        }
        // Tangent planes: of z = f(x, y) at (a, b), and of F(x, y, z) = F(P) at a point P of it.
        repeat(40) { i ->
            val implicit = i % 2 == 1
            val f = r.poly(if (implicit) 3 else 2, r.int(2, 4))
            val p = DoubleArray(if (implicit) 3 else 2) { r.int(-2, 2).toDouble() }
            val plane: (DoubleArray) -> Double
            val call: String
            if (!implicit) {
                val z0 = f.at(p); val fx = f.d(0, p); val fy = f.d(1, p)
                plane = { v -> z0 + fx * (v[0] - p[0]) + fy * (v[1] - p[1]) - v[2] }
                call = "tangentplane(${f.text(listOf("x", "y"))}, (${fmt(p[0])}, ${fmt(p[1])}))"
            } else {
                val g = DoubleArray(3) { f.d(it, p) }
                if (g.all { it == 0.0 }) return@repeat
                plane = { v -> (0..2).sumOf { g[it] * (v[it] - p[it]) } }
                call = "tangentplane(${f.text(listOf("x", "y", "z"))} = ${fmt(f.at(p))}, (${p.joinToString(", ") { fmt(it) }}))"
            }
            out += Task(AREA, call, "", judge = { c ->
                val e = (c.outcome as? Outcome.PlaneThrough)?.plane?.equation
                // The same plane: zero on the test's plane at points of it, and not elsewhere.
                e != null && e.symbols().all { it in setOf("x", "y", "z") } && r.let {
                    val rr = Random(9)
                    (0 until 5).all {
                        val v = doubleArrayOf(rr.nextDouble(-2.0, 2.0), rr.nextDouble(-2.0, 2.0), rr.nextDouble(-2.0, 2.0))
                        // Move v onto the test's plane along a coordinate in which the plane is not constant.
                        val k = (0..2).first { j -> abs(plane(v.copyOf().also { it[j] += 1.0 }) - plane(v)) > 1e-9 }
                        val slope = plane(v.copyOf().also { it[k] += 1.0 }) - plane(v)
                        val on = v.copyOf().also { it[k] -= plane(v) / slope }
                        val off = on.copyOf().also { it[k] += 1.0 }
                        abs(at(e, mapOf("x" to on[0], "y" to on[1], "z" to on[2]))) < 1e-9 && abs(at(e, mapOf("x" to off[0], "y" to off[1], "z" to off[2]))) > 1e-9
                    }
                }
            })
        }
        return out
    }

    private fun fourier(r: Random): List<Task> {
        val out = ArrayList<Task>()
        val omegas = listOf(-2.3, -0.7, 0.4, 1.1, 3.6)
        fun transform(text: String, letter: String, want: (Double) -> Cx): Task = Task(AREA, text, "", judge = { c ->
            val e = (c.outcome as? Outcome.Expression)?.result
            e != null && e.symbols().all { it == letter } && omegas.all { w -> close(Verify.value(e, mapOf(letter to w)), want(w), 1e-9) }
        })
        val w = "\\omega"
        for (a in 1..4) {
            out += transform("fouriertransform(e^(-${a}abs(t)))", w) { x -> Cx(2.0 * a / (a * a + x * x)) }
            out += transform("fouriertransform(e^(-${a}t^2))", w) { x -> Cx(sqrt(PI / a) * exp(-x * x / (4 * a))) }
            out += transform("fouriertransform(1/(${a * a} + t^2))", w) { x -> Cx(PI / a * exp(-a * abs(x))) }
            out += transform("fouriertransform((sgn(t + $a) - sgn(t - $a))/2)", w) { x -> Cx(2 * sin(a * x) / x) }
            out += transform("fouriertransform(e^(-${a}t) (1 + sgn(t))/2)", w) { x -> Cx(1.0, 0.0) / Cx(a.toDouble(), x) }
            out += transform("fouriertransform(t e^(-${a}t^2))", w) { x -> Cx(0.0, -x / (2 * a) * sqrt(PI / a) * exp(-x * x / (4 * a))) }
            for (b in 1..3) {
                // A shift: e^(−iωb)·F(ω); a modulation: (F(ω − b) + F(ω + b))/2.
                out += transform("fouriertransform(e^(-${a}abs(t - $b)))", w) { x -> Cx(cos(b * x), -sin(b * x)) * Cx(2.0 * a / (a * a + x * x)) }
                out += transform("fouriertransform(cos(${b}t) e^(-${a}t^2))", w) { x ->
                    Cx(sqrt(PI / a) / 2 * (exp(-(x - b) * (x - b) / (4 * a)) + exp(-(x + b) * (x + b) / (4 * a))))
                }
            }
            // Inverses: f(t) = (1/2π)∫ F(ω)e^(iωt) dω, the pairs the other way.
            out += transform("invfouriertransform(${2 * a}/(${a * a} + omega^2))", "t") { t -> Cx(exp(-a * abs(t))) }
            out += transform("invfouriertransform(e^(-omega^2/${4 * a}))", "t") { t -> Cx(sqrt(a / PI) * exp(-a * t * t)) }
            out += transform("invfouriertransform(1/($a + i omega))", "t") { t -> Cx(if (t > 0) exp(-a * t) else 0.0) }
        }
        // No transform: not integrable.
        for (f in listOf("e^(-t)", "e^(2t)", "t^2")) out += Task(AREA, "fouriertransform($f)", "diverges", fails = setOf(Failure.DIVERGES, Failure.UNSOLVED)) { false }
        return out
    }

    fun all(): List<Task> {
        val r = Random(3333)
        return boxes(r) + coordinates(r) + curves(r) + derivatives(r) + fourier(r)
    }
}

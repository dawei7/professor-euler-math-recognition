package com.professoreuler.mathcalc

import kotlin.math.abs
import kotlin.math.max

/**
 * Integrals along curves and over surfaces, and the derivatives of a function of several letters at a point:
 *
 * - the line integral ∫_C f ds = ∫ₐᵇ f(r(t))·|r′(t)| dt of a function, and ∫_C F · dr = ∫ₐᵇ F(r(t)) · r′(t) dt of a vector field,
 *   along the curve r(t), a ≤ t ≤ b; the flux ∫_C F · n ds = ∫ₐᵇ (P·y′ − Q·x′) dt of a plane field across a curve, n to the right
 *   of its direction (outward for a closed curve run counterclockwise);
 * - the surface integral ∬_S f dS = ∬ f(r(u, v))·|r_u × r_v| du dv, and the flux ∬_S F · dS = ∬ F(r(u, v)) · (r_u × r_v) du dv
 *   through the surface r(u, v), its normal r_u × r_v;
 * - the directional derivative D_u f(P) = ∇f(P) · u/|u|, and the tangent plane of z = f(x, y) or of F(x, y, z) = 0 at a point.
 *
 * Each is checked a second way: an integral against a numerical one whose derivatives are difference quotients (and a line
 * integral of a gradient field against its potential's difference), a directional derivative against the derivative of
 * f(P + h·u/|u|) in h and its difference quotient from the side of u, a tangent plane against the derivative of the function along
 * directions in it.
 */
internal object VectorCalculus {
    private val X = Expr.Sym("x")
    private val Y = Expr.Sym("y")
    private val Z = Expr.Sym("z")
    private val COORDINATES = listOf(X, Y, Z)

    private fun tidy(e: Expr) = Calculus.tidy(e)

    /** [f] with the coordinates put in at once: x, y, z by [values] (which may hold x themselves). */
    fun along(f: Expr, coords: List<Expr.Sym>, values: List<Expr>): Expr {
        val holders = coords.indices.map { Expr.Sym("\u0000$it") }
        var g = f
        coords.forEachIndexed { k, c -> g = g.substitute(c, holders[k]) }
        holders.forEachIndexed { k, h -> g = g.substitute(h, values[k]) }
        return g
    }

    /** A vector field's entries, or null for a function; [n] entries wanted. */
    private fun fieldOf(e: Expr, n: Int): List<Expr>? {
        if (e !is Expr.Mat) return null
        if (!LinAlg.isVector(e) || e.size != n) throw CalcException(Failure.SIZES)
        return e.rows.map { it[0] }
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Along a curve.

    /** What a line integral comes to, with what it was worked out from (for its steps). */
    class LineWork(val t: Expr.Sym, val curve: List<Expr>, val velocity: List<Expr>, val speed: Expr?, val integrand: Expr,
        val a: Expr, val b: Expr, val value: Expr, val potential: Expr?)

    /** lineintegral(f or F, r(t), t, a, b), flux(F, r(t), t, a, b). */
    fun line(r: MultiCalls.Reader, flux: Boolean): Outcome = MultiCalls.valueOf(lineWork(r, flux).value)

    fun lineWork(r: MultiCalls.Reader, flux: Boolean): LineWork {
        // The parameter may be x itself (the graph y = f(x) as (x, f(x))): the coordinates are put in at once.
        val t = r.letter(2)
        val curve = r.vector(1, if (flux) setOf(2) else setOf(2, 3), setOf(t.name))
        val n = curve.size
        val coords = COORDINATES.take(n)
        val a = r.expr(3)
        val b = r.expr(4)
        if (a.has(t) || b.has(t)) throw CalcException(Failure.ARGUMENTS)
        val first = r.expr(0)
        val field = fieldOf(first, n)
        if (flux && field == null) throw CalcException(Failure.ARGUMENTS)
        val velocity = curve.map { tidy(Calculus.derivative(it, t)) }
        val points = linePoints(a, b, t)
        var speed: Expr? = null
        val integrand = when {
            flux -> tidy(along(field!![0], coords, curve) * velocity[1] - along(field[1], coords, curve) * velocity[0])
            field != null -> tidy(add(field.indices.map { along(field[it], coords, curve) * velocity[it] }))
            else -> {
                speed = withoutAbs(tidy(sqrt(tidy(add(velocity.map { pow(it, Expr.TWO) })))), points)
                tidy(along(first, coords, curve) * speed)
            }
        }
        val value = definite(integrand, t, a, b)
        // A second way: the same integral with the curve's derivatives as difference quotients.
        val av = Numeric.value(a)
        val bv = Numeric.value(b)
        val parameters = integrand.symbols() - t.name
        if (av != null && bv != null && parameters.isEmpty()) {
            val check = Cubature.integrate({ s -> lineValue(first, field, flux, coords, curve, t, s) }, av, bv)
            val v = Numeric.value(value)
            if (check != null && v != null && abs(v - check.value) > max(1e-6 * max(1.0, abs(v)), 10 * check.error)) {
                throw CalcException(Failure.UNVERIFIED)
            }
        }
        // A gradient field: the potential's difference between the ends, where it is continuous along the curve.
        var potential: Expr? = null
        if (field != null && !flux) {
            potential = potential(field, coords)
            if (potential != null) {
                val composed = tidy(along(potential, coords, curve))
                val continuous = av != null && bv != null && continuousAlong(composed, t, av, bv)
                if (continuous) {
                    val ends = tidy(end(composed, t, b) - end(composed, t, a))
                    if (!sameValue(ends, value)) throw CalcException(Failure.UNVERIFIED)
                } else {
                    potential = null
                }
            }
        }
        return LineWork(t, curve, velocity, speed, integrand, a, b, value, potential)
    }

    /** The integrand of a line integral at the parameter's value [s], its curve's derivatives as difference quotients. */
    private fun lineValue(first: Expr, field: List<Expr>?, flux: Boolean, coords: List<Expr.Sym>, curve: List<Expr>, t: Expr.Sym, s: Double): Double {
        val h = 1e-5 * max(1.0, abs(s))
        val at = curve.map { Numeric.eval(it, mapOf(t.name to s)) }
        val d = curve.map { (Numeric.eval(it, mapOf(t.name to s + h)) - Numeric.eval(it, mapOf(t.name to s - h))) / (2 * h) }
        val env = coords.indices.associate { coords[it].name to at[it] } + (t.name to s)
        return when {
            flux -> Numeric.eval(field!![0], env) * d[1] - Numeric.eval(field[1], env) * d[0]
            field != null -> field.indices.sumOf { Numeric.eval(field[it], env) * d[it] }
            else -> Numeric.eval(first, env) * kotlin.math.sqrt(d.sumOf { it * it })
        }
    }

    /** ∫ₐᵇ in one letter: exact, else a certified decimal, else its failure. */
    private fun definite(e: Expr, t: Expr.Sym, a: Expr, b: Expr): Expr {
        val d = Calculus.definite(e, t, a, b)
        if (d.diverges) throw CalcException(Failure.DIVERGES)
        return d.exact ?: d.value?.let { Expr.Real(it) } ?: throw CalcException(Failure.UNSOLVED)
    }

    private fun end(f: Expr, t: Expr.Sym, bound: Expr): Expr = tidy(f.substitute(t, bound))

    private fun sameValue(a: Expr, b: Expr): Boolean {
        if (a.symbols().isNotEmpty() || b.symbols().isNotEmpty()) return Verify.same(a, b)
        val x = Numeric.value(a) ?: return false
        val y = Numeric.value(b) ?: return false
        return abs(x - y) <= 1e-7 * max(1.0, abs(x))
    }

    /** φ with ∇φ = [field] (in [coords]), each partial derivative checked; null where the field is no gradient that is found. */
    fun potential(field: List<Expr>, coords: List<Expr.Sym>): Expr? {
        var phi: Expr = Expr.ZERO
        for ((k, c) in coords.withIndex()) {
            val rest = tidy(field[k] - Calculus.derivative(phi, c))
            // What is left of this component may not depend on the letters already integrated.
            if (coords.take(k).any { Verify.same(Calculus.derivative(rest, it), Expr.ZERO).not() }) return null
            val part = if (Verify.same(rest, Expr.ZERO)) Expr.ZERO else Calculus.antiderivative(rest, c) ?: return null
            phi = tidy(phi + part)
        }
        for ((k, c) in coords.withIndex()) if (!Verify.same(Calculus.derivative(phi, c), field[k])) return null
        return phi
    }

    /** No jump of [e] along [a, b]: finite at many points, never far from its neighbours. */
    private fun continuousAlong(e: Expr, t: Expr.Sym, a: Double, b: Double): Boolean {
        val n = 400
        var last = Double.NaN
        for (k in 0..n) {
            val s = a + (b - a) * k / n
            val v = Numeric.eval(e, mapOf(t.name to s))
            if (!v.isFinite()) return false
            if (last.isFinite() && abs(v - last) > 0.05 * max(1.0, abs(last))) return false
            last = v
        }
        return true
    }

    private fun linePoints(a: Expr, b: Expr, t: Expr.Sym): List<Map<String, Double>> {
        val av = Numeric.value(a) ?: return emptyList()
        val bv = Numeric.value(b) ?: return emptyList()
        return (1..47).map { k -> mapOf(t.name to av + (bv - av) * (k - 0.37) / 47) }
    }

    /**
     * |g| as g or −g where g keeps its sign at every point of [points] (√(sin²θ) is sin θ for 0 < θ < π): what the integral is of
     * on its region. Where a sign changes, or no points are known, |g| stays.
     */
    fun withoutAbs(e: Expr, points: List<Map<String, Double>>): Expr {
        if (points.isEmpty()) return e
        fun walk(x: Expr): Expr {
            val inner = x.mapChildren(::walk)
            if (inner !is Expr.Fn || inner.f != Func.ABS) return inner
            val arg = inner.args[0]
            var positive = 0
            var negative = 0
            for (p in points) {
                val v = Numeric.eval(arg, p)
                if (!v.isFinite()) return inner
                if (v > 1e-12) positive++ else if (v < -1e-12) negative++
            }
            return when {
                negative == 0 && positive > 0 -> arg
                positive == 0 && negative > 0 -> neg(arg)
                else -> inner
            }
        }
        return tidy(walk(e))
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Over a surface.

    /** What a surface integral or a flux comes to, with what it was worked out from. */
    class SurfaceWork(val u: Expr.Sym, val v: Expr.Sym, val surface: List<Expr>, val ru: List<Expr>, val rv: List<Expr>,
        val normal: List<Expr>, val area: Expr?, val integrand: Expr, val ranges: List<Multiple.Range>, val value: Expr)

    /** surfaceintegral(f, r(u, v), u, a, b, v, c, d), flux(F, r(u, v), u, a, b, v, c, d). */
    fun surface(r: MultiCalls.Reader, flux: Boolean): Outcome = MultiCalls.valueOf(surfaceWork(r, flux).value)

    fun surfaceWork(r: MultiCalls.Reader, flux: Boolean): SurfaceWork {
        val u = r.letter(2)
        val v = r.letter(5)
        if (u == v) throw CalcException(Failure.ARGUMENTS)
        val params = setOf(u.name, v.name)
        val surface = r.vector(1, setOf(3), params)
        val ranges = listOf(Multiple.Range(u, r.expr(3, params), r.expr(4, params)), Multiple.Range(v, r.expr(6, params), r.expr(7, params)))
        Multiple.check(ranges)
        val first = r.expr(0)
        val field = fieldOf(first, 3)
        if (flux != (field != null)) throw CalcException(Failure.ARGUMENTS)
        val ru = surface.map { tidy(Calculus.derivative(it, u)) }
        val rv = surface.map { tidy(Calculus.derivative(it, v)) }
        val normal = LinAlg.cross(LinAlg.column(ru), LinAlg.column(rv)).rows.map { it[0] }
        var area: Expr? = null
        val integrand = if (field != null) {
            tidy(add(field.indices.map { along(field[it], COORDINATES, surface) * normal[it] }))
        } else {
            area = withoutAbs(tidy(sqrt(tidy(add(normal.map { pow(it, Expr.TWO) })))), surfacePoints(ranges))
            tidy(along(first, COORDINATES, surface) * area)
        }
        val result = Multiple.integrate(integrand, ranges, Multiple.System.CARTESIAN)
        val value = Multiple.answer(result)
        // A second way: a cubature of the same integral with the surface's derivatives as difference quotients.
        val shown = Numeric.value(value)
        if (shown != null && (integrand.symbols() - params).isEmpty()) {
            val check = Cubature.nested({ env -> surfaceValue(first, field, surface, u, v, env) }, ranges)
            if (check != null && abs(shown - check.value) > max(1e-6 * max(1.0, abs(shown)), 10 * check.error)) {
                throw CalcException(Failure.UNVERIFIED)
            }
        }
        return SurfaceWork(u, v, surface, ru, rv, normal, area, integrand, ranges, value)
    }

    private fun surfaceValue(first: Expr, field: List<Expr>?, surface: List<Expr>, u: Expr.Sym, v: Expr.Sym, env: Map<String, Double>): Double {
        val uv = env.getValue(u.name)
        val vv = env.getValue(v.name)
        fun s(a: Double, b: Double) = surface.map { Numeric.eval(it, mapOf(u.name to a, v.name to b)) }
        val hu = 1e-5 * max(1.0, abs(uv))
        val hv = 1e-5 * max(1.0, abs(vv))
        val du = s(uv + hu, vv).zip(s(uv - hu, vv)) { p, q -> (p - q) / (2 * hu) }
        val dv = s(uv, vv + hv).zip(s(uv, vv - hv)) { p, q -> (p - q) / (2 * hv) }
        val n = listOf(du[1] * dv[2] - du[2] * dv[1], du[2] * dv[0] - du[0] * dv[2], du[0] * dv[1] - du[1] * dv[0])
        val at = s(uv, vv)
        val point = mapOf("x" to at[0], "y" to at[1], "z" to at[2]) + env
        return if (field != null) (0..2).sumOf { Numeric.eval(field[it], point) * n[it] }
        else Numeric.eval(first, point) * kotlin.math.sqrt(n.sumOf { it * it })
    }

    /** Points inside the region of a surface's parameters: twelve to each side. */
    private fun surfacePoints(ranges: List<Multiple.Range>): List<Map<String, Double>> {
        val (inner, outer) = ranges
        val clo = Numeric.value(outer.lo) ?: return emptyList()
        val chi = Numeric.value(outer.hi) ?: return emptyList()
        val out = ArrayList<Map<String, Double>>()
        for (j in 1..12) {
            val vv = clo + (chi - clo) * (j - 0.41) / 12
            val env = mapOf(outer.v.name to vv)
            val lo = Numeric.eval(inner.lo, env)
            val hi = Numeric.eval(inner.hi, env)
            if (!lo.isFinite() || !hi.isFinite()) return emptyList()
            for (k in 1..12) out += env + (inner.v.name to lo + (hi - lo) * (k - 0.37) / 12)
        }
        return out
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // At a point.

    /** A directional derivative with what it was worked out from: ∇f, ∇f(P), the unit vector. */
    class DirectionalWork(val gradient: List<Expr>, val atPoint: List<Expr>, val length: Expr, val unit: List<Expr>, val value: Expr)

    /** directionalderivative(f, P, u). */
    fun directional(r: MultiCalls.Reader): Outcome = MultiCalls.valueOf(directionalWork(r).value)

    fun directionalWork(r: MultiCalls.Reader): DirectionalWork {
        val f = r.expr(0)
        if (f is Expr.Mat) throw CalcException(Failure.ARGUMENTS)
        val point = r.vector(1, setOf(2, 3))
        val direction = r.vector(2, setOf(2, 3))
        if (point.size != direction.size) throw CalcException(Failure.SIZES)
        if ((point + direction).any { it.symbols().isNotEmpty() }) throw CalcException(Failure.ARGUMENTS)
        val n = point.size
        val coords = COORDINATES.take(n)
        if (n == 2 && f.has(Z)) throw CalcException(Failure.SIZES)
        val atP = tidy(along(f, coords, point))
        if (atP.isConstant() && Numeric.value(atP) == null && !atP.hasI()) throw CalcException(Failure.UNDEFINED)
        val gradient = coords.map { tidy(Calculus.derivative(f, it)) }
        // A gradient that is not defined at P (|x| at 0): f has no derivative there to take.
        val atPoint = try { gradient.map { tidy(along(it, coords, point)) } } catch (x: CalcException) {
            throw CalcException(if (x.failure == Failure.DIVISION_BY_ZERO) Failure.UNDEFINED else x.failure)
        }
        if (atPoint.any { it.isConstant() && !it.hasI() && Numeric.value(it) == null }) throw CalcException(Failure.UNDEFINED)
        val length = tidy(sqrt(tidy(add(direction.map { pow(it, Expr.TWO) }))))
        if (LinAlg.isNull(length)) throw CalcException(Failure.UNDEFINED)
        val unit = direction.map { tidy(it / length) }
        val value = tidy(add(atPoint.indices.map { atPoint[it] * unit[it] }))
        // A second way: d/dh f(P + h·u/|u|) at h = 0, and the difference quotient from the side of u.
        val h = Expr.Sym("\u0000h")
        val g = along(f, coords, point.indices.map { point[it] + h * unit[it] })
        val second = tidy(Calculus.derivative(g, h).substitute(h, Expr.ZERO))
        if (!sameValue(value, second)) throw CalcException(Failure.UNVERIFIED)
        if (value.symbols().isEmpty()) {
            val v = Numeric.value(value) ?: throw CalcException(Failure.UNDEFINED)
            val quotient = oneSided(g, h) ?: throw CalcException(Failure.UNVERIFIED)
            if (abs(v - quotient) > 1e-5 * max(1.0, abs(v))) throw CalcException(Failure.UNVERIFIED)
        }
        return DirectionalWork(gradient, atPoint, length, unit, value)
    }

    /** (g(h) − g(0))/h for h → 0 from above, Richardson's extrapolation of three steps; null where g is not finite there. */
    private fun oneSided(g: Expr, h: Expr.Sym): Double? {
        val g0 = Numeric.eval(g, mapOf(h.name to 0.0))
        if (!g0.isFinite()) return null
        fun q(s: Double) = (Numeric.eval(g, mapOf(h.name to s)) - g0) / s
        val s = 1e-3
        val d1 = q(s)
        val d2 = q(s / 2)
        val d3 = q(s / 4)
        if (!d1.isFinite() || !d2.isFinite() || !d3.isFinite()) return null
        val r1 = 2 * d2 - d1
        val r2 = 2 * d3 - d2
        return (4 * r2 - r1) / 3
    }

    /** tangentplane(f, P) of z = f(x, y), tangentplane(F = G, P) of a surface in x, y and z. */
    fun tangentPlane(r: MultiCalls.Reader): Outcome = Outcome.PlaneThrough(tangentPlaneOf(r))

    fun tangentPlaneOf(r: MultiCalls.Reader): Geometry.Plane {
        val st = r.statement(0)
        val sides = st.sides.map { canonical(it ?: throw CalcException(Failure.ARGUMENTS)) }
        if (sides.size > 2 || (sides.size == 2 && st.relations.singleOrNull() != Rel.EQ)) throw CalcException(Failure.ARGUMENTS)
        if (sides.any { it is Expr.Mat }) throw CalcException(Failure.ARGUMENTS)
        val point = r.vector(1, setOf(2, 3))
        if (point.any { it.symbols().isNotEmpty() }) throw CalcException(Failure.ARGUMENTS)
        // z = f(x, y), given as f or as z = f, at (x₀, y₀): its graph's plane.
        val graph: Expr? = when {
            sides.size == 1 && point.size == 2 -> sides[0]
            sides.size == 2 && point.size == 2 && sides[0] == Z && !sides[1].has(Z) -> sides[1]
            sides.size == 2 && point.size == 2 && sides[1] == Z && !sides[0].has(Z) -> sides[0]
            sides.size == 2 && point.size == 3 -> null
            else -> throw CalcException(Failure.ARGUMENTS)
        }
        val big: Expr
        val p3: List<Expr>
        if (graph != null) {
            if (graph.has(Z)) throw CalcException(Failure.ARGUMENTS)
            val z0 = tidy(along(graph, listOf(X, Y), point))
            if (z0.isConstant() && Numeric.value(z0) == null) throw CalcException(Failure.UNDEFINED)
            big = tidy(graph - Z)
            p3 = point + z0
        } else {
            big = tidy(sides[0] - sides[1])
            p3 = point
            if (!LinAlg.isNull(tidy(along(big, COORDINATES, p3)))) throw CalcException(Failure.NOT_ON_SURFACE)
        }
        val gradient = COORDINATES.map { tidy(along(Calculus.derivative(big, it), COORDINATES, p3)) }
        if (gradient.any { it.isConstant() && Numeric.value(it) == null && !it.hasI() }) throw CalcException(Failure.UNDEFINED)
        if (gradient.all { LinAlg.isNull(it) }) throw CalcException(Failure.UNDEFINED)
        val normal = whole(gradient)
        val equation = tidy(add(COORDINATES.indices.map { normal[it] * (COORDINATES[it] - p3[it]) }))
        // The point is on it, and the function changes only to second order along directions in it.
        if (!LinAlg.isNull(tidy(along(equation, COORDINATES, p3)))) throw CalcException(Failure.UNVERIFIED)
        val h = Expr.Sym("\u0000h")
        for (d in inPlane(normal)) {
            val g = along(big, COORDINATES, p3.indices.map { p3[it] + h * d[it] })
            val slope = tidy(Calculus.derivative(g, h).substitute(h, Expr.ZERO))
            if (!LinAlg.isNull(slope)) throw CalcException(Failure.UNVERIFIED)
        }
        return Geometry.Plane(listOf(LinAlg.column(p3)), LinAlg.column(normal), equation)
    }

    /** Two directions in the plane with normal [n]: n × e for the axes e that give a nonzero one. */
    private fun inPlane(n: List<Expr>): List<List<Expr>> {
        val axes = listOf(listOf(Expr.ONE, Expr.ZERO, Expr.ZERO), listOf(Expr.ZERO, Expr.ONE, Expr.ZERO), listOf(Expr.ZERO, Expr.ZERO, Expr.ONE))
        return axes.map { e -> LinAlg.cross(LinAlg.column(n), LinAlg.column(e)).rows.map { it[0] } }.filter { d -> d.any { !LinAlg.isNull(it) } }.take(2)
    }

    /** A normal of whole numbers without a common factor, the first not negative, where its entries are fractions. */
    private fun whole(n: List<Expr>): List<Expr> {
        val rs = n.map { rational(it) }
        if (rs.any { it == null }) return n
        val den = rs.fold(java.math.BigInteger.ONE) { acc, x -> acc / acc.gcd(x!!.den) * x.den }
        val w = rs.map { (it!! * Rational.of(den)).num }
        val g = w.fold(java.math.BigInteger.ZERO) { acc, x -> acc.gcd(x) }
        val sign = if (w.first { it.signum() != 0 }.signum() < 0) -1L else 1L
        return w.map { num(Rational.of(it / g * java.math.BigInteger.valueOf(sign))) }
    }

    private fun canonical(e: Expr) = MultiCalls.canonical(Calculus.evaluate(e))
}

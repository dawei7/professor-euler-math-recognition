package com.professoreuler.mathcalc

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max

/**
 * Double and triple integrals, iterated: ∫_c^d ∫_a(y)^b(y) f dx dy, the innermost integral first and each bound a formula in the
 * letters of the integrals outside it, in Cartesian, polar, cylindrical and spherical coordinates (the Jacobian r, r and r² sin θ
 * put in by the function). Exact where every integral in turn is: each antiderivative is differentiated back
 * ([Calculus.antiderivative]), each inner result is compared with a numerical integral at points of the region outside it, the
 * outermost integral is a one-variable definite one ([Calculus.definite]), and the whole is compared with a cubature of the region.
 * Otherwise a numerical value by nested adaptive Gauss–Kronrod quadrature, with as many digits as its error estimate leaves certain.
 */
object Multiple {
    /** One integration: [v] from [lo] to [hi]; the bounds may hold the letters of the integrations outside it. */
    data class Range(val v: Expr.Sym, val lo: Expr, val hi: Expr)

    /** The coordinates of an integral: the letters it is written in and the factor dA or dV brings. */
    enum class System { CARTESIAN, POLAR, CYLINDRICAL, SPHERICAL }

    /**
     * An integral worked out: its [exact] value where there is one, else its [value] (rounded to its certain digits); [integrand]
     * the function integrated with its Jacobian, [stages] each integral's result from the inside out; [diverges] where an
     * integral of it is infinite.
     */
    class Result(val exact: Expr?, val value: Double?, val integrand: Expr, val stages: List<Expr>, val diverges: Boolean = false)

    val R = Expr.Sym("r")
    val THETA = Expr.Sym("\\theta")
    val PHI = Expr.Sym("\\phi")
    private val X = Expr.Sym("x")
    private val Y = Expr.Sym("y")
    private val Z = Expr.Sym("z")

    /** Most integrals nested: a triple integral. */
    const val MOST = 3

    // ---------------------------------------------------------------------------------------------------------------------
    // Nested integrals as the formula writes them.

    /** Whether [e] is a definite integral of a definite integral (of at most [MOST]): ∫∫ f dx dy, worked out here as a whole. */
    fun isNested(e: Expr): Boolean = unroll(e) != null

    /** A nested definite integral's innermost body and its ranges, innermost first; null where [e] is not one. */
    fun unroll(e: Expr): Pair<Expr, List<Range>>? {
        val ranges = ArrayList<Range>()
        var current = e
        while (current is Expr.Op && current.kind == OpKind.INTEGRAL && current.a != null && current.b != null) {
            ranges += Range(current.variable, current.a, current.b)
            current = current.body
        }
        if (ranges.size < 2 || ranges.size > MOST) return null
        return current to ranges.reversed()
    }

    /** A nested integral's value, for [Calculus.evaluate]: exact, else a [Expr.Real]; it fails where it has none. */
    fun evaluate(e: Expr): Expr {
        val (body, ranges) = unroll(e) ?: throw CalcException(Failure.UNREADABLE)
        val f = Calculus.evaluate(body)
        val rs = ranges.map { Range(it.v, Calculus.evaluate(it.lo), Calculus.evaluate(it.hi)) }
        val result = integrate(f, rs, System.CARTESIAN)
        return answer(result)
    }

    /** The value of a result, or its failure. */
    fun answer(result: Result): Expr = result.exact ?: result.value?.let { Expr.Real(it) }
        ?: throw CalcException(if (result.diverges) Failure.DIVERGES else Failure.UNSOLVED)

    // ---------------------------------------------------------------------------------------------------------------------
    // Coordinates.

    /** The letters of a system, by role: (r, θ), (r, θ, z), (r, θ, φ); [angle] the letter the polar angle was written with. */
    fun letters(system: System, angle: Expr.Sym = THETA): List<Expr.Sym> = when (system) {
        System.CARTESIAN -> emptyList()
        System.POLAR -> listOf(R, angle)
        System.CYLINDRICAL -> listOf(R, angle, Z)
        System.SPHERICAL -> listOf(R, THETA, PHI)
    }

    /** x, y (and z) in the system's letters: x = r cos θ, y = r sin θ; on the sphere x = r sin θ cos φ, y = r sin θ sin φ, z = r cos θ. */
    fun cartesian(system: System, angle: Expr.Sym = THETA): List<Pair<Expr.Sym, Expr>> = when (system) {
        System.CARTESIAN -> emptyList()
        System.POLAR, System.CYLINDRICAL -> listOf(X to mul(R, fn(Func.COS, angle)), Y to mul(R, fn(Func.SIN, angle)))
        System.SPHERICAL -> listOf(X to mul(R, fn(Func.SIN, THETA), fn(Func.COS, PHI)), Y to mul(R, fn(Func.SIN, THETA), fn(Func.SIN, PHI)),
            Z to mul(R, fn(Func.COS, THETA)))
    }

    /** The factor of the area or volume element: dA = r dr dθ, dV = r dr dθ dz, dV = r² sin θ dr dθ dφ. */
    fun jacobian(system: System): Expr = when (system) {
        System.CARTESIAN -> Expr.ONE
        System.POLAR, System.CYLINDRICAL -> R
        System.SPHERICAL -> mul(pow(R, Expr.TWO), fn(Func.SIN, THETA))
    }

    /** f in the system's letters, times its Jacobian: what is integrated. */
    fun integrand(f: Expr, system: System, angle: Expr.Sym = THETA): Expr {
        var g = f
        for ((c, value) in cartesian(system, angle)) g = g.substitute(c, value)
        return Calculus.tidy(mul(g, jacobian(system)))
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The integral.

    /**
     * ∫ … ∫ [f] over [ranges] (innermost first) in [system]: [f] may be written in x, y (z) or in the system's letters. Fails with
     * [Failure.ARGUMENTS] where a bound holds its own letter or that of an integral inside it, or a letter is integrated twice.
     */
    fun integrate(f: Expr, ranges: List<Range>, system: System, angle: Expr.Sym = THETA): Result {
        check(ranges)
        val g = if (system == System.CARTESIAN) f else integrand(f, system, angle)
        val names = ranges.map { it.v.name }.toSet()
        val parameters = (g.symbols() + ranges.flatMap { it.lo.symbols() + it.hi.symbols() }) - names
        var stages: List<Expr>? = null
        var diverges = false
        try {
            Budget.within(EXACT_MS) { stages = exact(g, ranges, parameters) }
        } catch (x: CalcException) {
            when (x.failure) {
                Failure.DIVERGES -> diverges = true
                else -> {}
            }
        } catch (x: ArithmeticException) {
        } catch (x: StackOverflowError) {
        }
        val found = stages
        if (diverges) return Result(null, null, g, emptyList(), diverges = true)
        if (parameters.isNotEmpty()) return Result(found?.last(), null, g, found.orEmpty())
        Budget.check()
        val numeric = Cubature.nested(g, ranges)
        if (found != null) {
            val exact = found.last()
            val ev = Numeric.value(exact)
            // An exact value must agree with the cubature where that has one.
            if (ev != null && numeric != null && abs(ev - numeric.value) > max(1e-7 * max(1.0, abs(ev)), 10 * numeric.error)) {
                return Result(null, Cubature.certified(numeric), g, emptyList())
            }
            if (ev != null || exact.hasI()) return Result(exact, numeric?.value, g, found)
        }
        return Result(null, numeric?.let { Cubature.certified(it) }, g, emptyList())
    }

    /** How long the exact way may take, so that the numerical one has its time too. */
    private const val EXACT_MS = 1800L

    /** Every letter integrated once; each bound only in the letters of the integrals outside it (and other letters). */
    fun check(ranges: List<Range>) {
        val names = ranges.map { it.v.name }
        if (names.toSet().size != names.size) throw CalcException(Failure.ARGUMENTS)
        for ((k, r) in ranges.withIndex()) {
            val inside = names.subList(0, k + 1).toSet()
            if ((r.lo.symbols() + r.hi.symbols()).any { it in inside }) throw CalcException(Failure.ARGUMENTS)
        }
    }

    /**
     * The integrals in turn from the inside out, each exact: by the one-variable definite integral where nothing else is left,
     * else F(b) − F(a) of an antiderivative that was differentiated back, compared with a numerical integral at points of the
     * region outside. Null where one of them is not exact.
     */
    private fun exact(f: Expr, ranges: List<Range>, parameters: Set<String>): List<Expr>? {
        var e = f
        val stages = ArrayList<Expr>()
        for ((k, r) in ranges.withIndex()) {
            Budget.check()
            val free = (e.symbols() + r.lo.symbols() + r.hi.symbols()) - r.v.name
            val g = if (free.isEmpty()) {
                val d = Calculus.definite(e, r.v, r.lo, r.hi)
                if (d.diverges) throw CalcException(Failure.DIVERGES)
                d.exact ?: return null
            } else {
                val antiderivative = Calculus.antiderivative(e, r.v) ?: return null
                val upper = end(antiderivative, r.v, r.hi) ?: return null
                val lower = end(antiderivative, r.v, r.lo) ?: return null
                val value = Calculus.tidy(upper - lower)
                if (value.hasInfinity()) return null
                if (!agrees(e, value, r, ranges.subList(k + 1, ranges.size), parameters)) return null
                value
            }
            stages += g
            e = g
        }
        return stages
    }

    /** F at a bound: put in, or its limit at ±∞. */
    private fun end(f: Expr, v: Expr.Sym, bound: Expr): Expr? {
        if (bound == Expr.INF || bound == neg(Expr.INF)) {
            return Calculus.limit(f, v, bound, null)?.takeIf { !it.hasInfinity() }
        }
        return Calculus.tidy(f.substitute(v, bound))
    }

    /**
     * Whether ∫ [e] d[r] from its lower to its upper bound is [value] at points of the region of the [outer] integrals (and at
     * values of the other letters): each a numerical integral, compared; at least half the points must have one.
     */
    private fun agrees(e: Expr, value: Expr, r: Range, outer: List<Range>, parameters: Set<String>): Boolean {
        val points = samples(outer, parameters)
        var compared = 0
        for (env in points) {
            Budget.check()
            val lo = Numeric.eval(r.lo, env)
            val hi = Numeric.eval(r.hi, env)
            if (lo.isNaN() || hi.isNaN()) continue
            val local = HashMap(env)
            val numeric = Cubature.integrate({ t -> local[r.v.name] = t; Numeric.eval(e, local) }, lo, hi) ?: continue
            val exact = Numeric.eval(value, env)
            if (!exact.isFinite()) return false
            if (abs(exact - numeric.value) > max(1e-7 * max(1.0, abs(exact)), 10 * numeric.error)) return false
            compared++
        }
        return compared > 0 && 2 * compared >= points.size
    }

    /** Points of the region of [outer] (outermost first, three to each), at two values of every other letter. */
    private fun samples(outer: List<Range>, parameters: Set<String>): List<Map<String, Double>> {
        val sets = if (parameters.isEmpty()) listOf(emptyMap()) else listOf(0.731, 1.379).map { base ->
            parameters.sorted().withIndex().associate { (k, p) -> p to base + 0.113 * k }
        }
        val out = ArrayList<Map<String, Double>>()
        for (set in sets) {
            var level = listOf(set)
            for (r in outer.reversed()) {
                val next = ArrayList<Map<String, Double>>()
                for (env in level) {
                    val lo = Numeric.eval(r.lo, env)
                    val hi = Numeric.eval(r.hi, env)
                    if (lo.isNaN() || hi.isNaN()) continue
                    for (s in POSITIONS) next += env + (r.v.name to Cubature.at(lo, hi, s))
                }
                level = next
            }
            out += level
        }
        return out
    }

    /** Where in an interval the sample points lie: away from its middle and its ends, where special values hide. */
    private val POSITIONS = doubleArrayOf(0.173, 0.519, 0.861)
}

/**
 * Adaptive Gauss–Kronrod (7, 15) quadrature with an error estimate, nested for integrals over regions: each inner integral at the
 * nodes of the one outside it, the errors of the inner ones added to the outer one's. The nodes and weights are the published ones
 * of the 15-point Kronrod extension of the 7-point Gauss rule (as in QUADPACK's qk15).
 */
internal object Cubature {
    /** A value with its error estimate. */
    class Estimate(val value: Double, val error: Double)

    private val XGK = doubleArrayOf(0.991455371120813, 0.949107912342759, 0.864864423359769, 0.741531185599394,
        0.586087235467691, 0.405845151377397, 0.207784955007898, 0.0)
    private val WGK = doubleArrayOf(0.022935322010529, 0.063092092629979, 0.104790010322250, 0.140653259715525,
        0.169004726639267, 0.190350578064785, 0.204432940075298, 0.209482141084728)
    private val WG = doubleArrayOf(0.129484966168870, 0.279705391489277, 0.381830050505119, 0.417959183673469)

    /** How many integrand values a whole cubature may take. */
    private const val MOST_VALUES = 6_000_000

    /** The point at [s] of the way from [lo] to [hi], either of them infinite. */
    fun at(lo: Double, hi: Double, s: Double): Double = when {
        lo.isInfinite() && hi.isInfinite() -> kotlin.math.tan(Math.PI * (s - 0.5))
        hi.isInfinite() -> lo + s / (1 - s)
        lo.isInfinite() -> hi - (1 - s) / s
        else -> lo + (hi - lo) * s
    }

    /** ∫ f from a to b, with its error estimate; null where the estimate stays poor or a value is not finite. */
    fun integrate(f: (Double) -> Double, a: Double, b: Double): Estimate? = integrate({ t ->
        val v = f(t)
        if (v.isFinite()) Estimate(v, 0.0) else null
    }, a, b, IntArray(1))

    /** ∫ … ∫ f over [ranges] (innermost first): its value and error estimate, or null. */
    fun nested(f: Expr, ranges: List<Multiple.Range>): Estimate? = nested({ env -> Numeric.eval(f, env) }, ranges)

    /**
     * The same of a function of the letters' values, worked out twice: once with each interval as it is, once with each first cut
     * at 0.4142… of its length, so that no node of an inner rule lies where an outer one puts a jump or a kink of the integrand
     * (floor(x + y) at x = 1 − y). The two must agree within their error estimates; the larger estimate and their difference
     * are the error. Null where they do not agree.
     */
    fun nested(f: (Map<String, Double>) -> Double, ranges: List<Multiple.Range>): Estimate? {
        val first = nested(f, ranges, false) ?: return null
        val second = nested(f, ranges, true) ?: return null
        val difference = abs(first.value - second.value)
        if (difference > 10 * (first.error + second.error) + 1e-13 * max(1.0, abs(first.value))) return null
        return Estimate(first.value, max(max(first.error, second.error), difference))
    }

    private fun nested(f: (Map<String, Double>) -> Double, ranges: List<Multiple.Range>, offset: Boolean): Estimate? {
        val env = HashMap<String, Double>()
        val counter = IntArray(1)
        fun level(k: Int): Estimate? {
            if (k < 0) {
                val v = f(env)
                return if (v.isFinite()) Estimate(v, 0.0) else null
            }
            val r = ranges[k]
            val lo = Numeric.eval(r.lo, env)
            val hi = Numeric.eval(r.hi, env)
            if (lo.isNaN() || hi.isNaN()) return null
            val saved = env[r.v.name]
            val result = integrate({ t -> env[r.v.name] = t; level(k - 1) }, lo, hi, counter, offset)
            if (saved == null) env.remove(r.v.name) else env[r.v.name] = saved
            return result
        }
        val result = level(ranges.size - 1) ?: return null
        if (!result.value.isFinite() || !result.error.isFinite()) return null
        return result
    }

    /** A value with only the digits its error leaves certain (at most 15), 0 where it is within its error of 0; null where too poor. */
    fun certified(e: Estimate): Double? {
        val v = e.value
        if (e.error > 1e-6 * max(1.0, abs(v))) return null
        if (abs(v) <= 10 * e.error) return if (e.error <= 1e-10) 0.0 else null
        val digits = if (e.error == 0.0) 15 else floor(-log10(e.error / abs(v))).toInt().coerceIn(6, 15)
        return BigDecimal(v).round(MathContext(digits)).toDouble()
    }

    private fun integrate(f: (Double) -> Estimate?, a: Double, b: Double, counter: IntArray, offset: Boolean = false): Estimate? {
        if (a.isNaN() || b.isNaN()) return null
        if (a == b) return Estimate(0.0, 0.0)
        if (a > b) return integrate(f, b, a, counter, offset)?.let { Estimate(-it.value, it.error) }
        // ∞ bounds: x = t/(1 − t²) on (−1, 1), or a + t/(1 − t) on (0, 1).
        val g: (Double) -> Estimate?
        val lo: Double
        val hi: Double
        when {
            a.isInfinite() && b.isInfinite() -> {
                g = { t -> val d = 1 - t * t; val w = (1 + t * t) / (d * d); f(t / d)?.let { Estimate(it.value * w, it.error * w) } }
                lo = -1.0; hi = 1.0
            }
            b.isInfinite() -> {
                g = { t -> val d = 1 - t; val w = 1 / (d * d); f(a + t / d)?.let { Estimate(it.value * w, it.error * w) } }
                lo = 0.0; hi = 1.0
            }
            a.isInfinite() -> {
                g = { t -> val d = 1 - t; val w = 1 / (d * d); f(b - t / d)?.let { Estimate(it.value * w, it.error * w) } }
                lo = 0.0; hi = 1.0
            }
            else -> { g = f; lo = a; hi = b }
        }
        var error = 0.0
        fun piece(x0: Double, x1: Double, depth: Int): Double? {
            Budget.check()
            val c = (x0 + x1) / 2
            val h = (x1 - x0) / 2
            var inner = 0.0
            fun value(x: Double, w: Double): Double? {
                val e = g(x) ?: return null
                inner += w * h * e.error
                return e.value
            }
            val mid = value(c, WGK[7]) ?: return null
            var kronrod = WGK[7] * mid
            var gauss = WG[3] * mid
            for (k in 0 until 7) {
                val dx = h * XGK[k]
                val left = value(c - dx, WGK[k]) ?: return null
                val right = value(c + dx, WGK[k]) ?: return null
                kronrod += WGK[k] * (left + right)
                if (k % 2 == 1) gauss += WG[k / 2] * (left + right)
            }
            counter[0] += 15
            if (counter[0] > MOST_VALUES) return null
            kronrod *= h
            gauss *= h
            if (!kronrod.isFinite() || !inner.isFinite()) return null
            val err = abs(kronrod - gauss)
            if (err <= 1e-10 * max(1.0, abs(kronrod))) {
                error += err + inner
                return kronrod
            }
            if (depth > 40) {
                if (err > 1e-4 * max(1.0, abs(kronrod))) return null
                error += err + inner
                return kronrod
            }
            val left = piece(x0, c, depth + 1) ?: return null
            val right = piece(c, x1, depth + 1) ?: return null
            return left + right
        }
        val eps = if (a.isInfinite() || b.isInfinite()) 1e-12 else 0.0
        val value = if (offset) {
            val cut = lo + eps + (hi - lo - 2 * eps) * 0.41421356237
            (piece(lo + eps, cut, 1) ?: return null) + (piece(cut, hi - eps, 1) ?: return null)
        } else piece(lo + eps, hi - eps, 0) ?: return null
        if (a.isInfinite() || b.isInfinite()) {
            // What lies beyond x = 10¹²: about x·f(x) out there.
            for (far in listOf(1e9, 3.7e10, 1e12)) {
                if (b.isInfinite()) error += abs(far * (f((if (a.isFinite()) a else 0.0) + far)?.value ?: return null))
                if (a.isInfinite()) error += abs(far * (f((if (b.isFinite()) b else 0.0) - far)?.value ?: return null))
            }
        }
        if (!error.isFinite()) return null
        return Estimate(value, error)
    }
}

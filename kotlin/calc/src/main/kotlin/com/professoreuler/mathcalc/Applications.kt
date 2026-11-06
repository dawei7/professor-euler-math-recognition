package com.professoreuler.mathcalc

import kotlin.math.abs
import kotlin.math.max

/**
 * Calculus put to use on a curve y = f(x): the tangent and the normal line at a point, the arc length, the area between two
 * curves, the volume of a solid of revolution about the x-axis (discs) and about the y-axis (shells), and the mean value.
 * Every result is checked another way before it is shown:
 * - a tangent's slope f′(a) against difference quotients from both sides (a kink or a jump has none), the line through
 *   (a, f(a)) with that slope;
 * - every integral (exact by an antiderivative where [Calculus.definite] finds one, else its certified quadrature) against a
 *   quadrature of the integrand as first written, at random values of the other letters where there are some;
 * - an area piece by piece between the zeros (and poles) of f − g, each zero put back, the sum against ∫|f − g| by quadrature,
 *   so a missed crossing shows; a shell volume alike, with |x·f(x)|.
 */
internal object Applications {
    private fun tidy(e: Expr) = Calculus.tidy(e)

    // ------------------------------------------------------------------------------------------------------------------
    // Tangent and normal.

    fun tangent(f: Expr, x: Expr.Sym, a: Expr, normal: Boolean): Outcome {
        if (a.has(x) || a.hasInfinity()) throw CalcException(Failure.ARGUMENTS)
        val fa = tidy(f.substitute(x, a))
        defined(fa)
        // f′ not defined at a (|x| at 0, whose f′ is x/|x|): no tangent there.
        val m = try { tidy(tidy(Calculus.derivative(f, x)).substitute(x, a)) } catch (e: CalcException) {
            throw if (e.failure == Failure.DIVISION_BY_ZERO) CalcException(Failure.UNDEFINED) else e
        }
        defined(m)
        // A kink or a jump at a has no tangent: the difference quotients from both sides must agree with f′(a).
        if (!slopeAgrees(f, x, a, m)) throw CalcException(Failure.UNDEFINED)
        val line: Pair<String, Expr> = when {
            !normal -> "y" to tidy(Algebra.expand(fa + m * (x - a)))
            isZero(m) -> "x" to a
            m.symbols().isEmpty() && LinAlg.isNull(m) -> "x" to a
            else -> "y" to tidy(Algebra.expand(fa - (x - a) / m))
        }
        // The line itself: through (a, f(a)), its slope f′(a) (the normal's −1/f′(a)).
        if (line.first == "y") {
            val through = tidy(line.second.substitute(x, a) - fa)
            val slope = tidy(Calculus.derivative(line.second, x))
            val wanted = if (normal) tidy(Expr.MINUS_ONE / m) else m
            if (!LinAlg.isNull(through) || !LinAlg.isNull(tidy(slope - wanted))) throw CalcException(Failure.UNSOLVED)
        }
        return Outcome.TangentLine(f, x.name, a, fa, m, line, normal)
    }

    /** A number that is defined: finite and real (letters are checked by the quotients at values of their own). */
    private fun defined(e: Expr) {
        if (e.hasInfinity()) throw CalcException(Failure.UNDEFINED)
        if (e.symbols().isEmpty() && Numeric.value(e) == null) throw CalcException(Failure.UNDEFINED)
    }

    /**
     * Whether f′(a) = [m]: the one-sided second-order difference quotients (−3f(a) + 4f(a ± h) − f(a ± 2h))/(±2h) both agree with
     * it, at the values given, or at random values of the other letters (three at least).
     */
    private fun slopeAgrees(f: Expr, x: Expr.Sym, a: Expr, m: Expr): Boolean {
        val others = ((f.symbols() + a.symbols() + m.symbols()) - x.name).toList()
        val random = java.util.Random(41L * others.hashCode() + 5)
        var agreed = 0
        for (attempt in 0 until 40) {
            Budget.check()
            val env = others.associateWith { (random.nextDouble() * 2 - 1) * 3.0 + 0.0137 }
            val av = Numeric.eval(a, env)
            val mv = Numeric.eval(m, env)
            if (!av.isFinite() || !mv.isFinite()) {
                if (others.isEmpty()) return false
                continue
            }
            val h = 1e-4 * max(1.0, abs(av))
            fun value(t: Double) = Numeric.eval(f, env + (x.name to t))
            val f0 = value(av)
            val right = (-3 * f0 + 4 * value(av + h) - value(av + 2 * h)) / (2 * h)
            val left = (3 * f0 - 4 * value(av - h) + value(av - 2 * h)) / (2 * h)
            if (!right.isFinite() || !left.isFinite()) {
                if (others.isEmpty()) return false
                continue
            }
            // The quotients' own error: h² times the third derivative's size, and rounding, the function's size over h.
            val size = maxOf(abs(f0), abs(value(av + 2 * h)), abs(value(av - 2 * h)))
            val tolerance = 1e-5 * max(1.0, abs(mv)) + 1e-12 * size / h
            if (abs(right - mv) > tolerance || abs(left - mv) > tolerance) return false
            agreed++
            if (others.isEmpty() || agreed >= 3) return true
        }
        return false
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Integrals, checked.

    /** The bounds in rising order where both are numbers; as written where a letter is in either. */
    private fun ordered(a: Expr, b: Expr): Pair<Expr, Expr> {
        val av = boundValue(a)
        val bv = boundValue(b)
        return if (!av.isNaN() && !bv.isNaN() && av > bv) b to a else a to b
    }

    private fun boundValue(b: Expr): Double = when {
        b == Expr.INF -> Double.POSITIVE_INFINITY
        b == neg(Expr.INF) -> Double.NEGATIVE_INFINITY
        b.symbols().isNotEmpty() -> Double.NaN
        else -> Numeric.value(b) ?: Double.NaN
    }

    /** ∫ from [lo] to [hi] of [e] dx: exact where it can be, else its certified value; fails where it diverges or has none. */
    private fun integral(e: Expr, x: Expr.Sym, lo: Expr, hi: Expr): Expr {
        if (lo == hi) return Expr.ZERO
        val d = Calculus.definite(e, x, lo, hi)
        return d.exact ?: d.value?.let { Expr.Real(it) } ?: throw CalcException(if (d.diverges) Failure.DIVERGES else Failure.UNSOLVED)
    }

    /**
     * Whether [result] is ∫ from [lo] to [hi] of [integrand] dx times [factor] (a number), by quadrature: once where there are no
     * other letters, else at random values of them (positive, as lengths are), three agreeing at least.
     */
    private fun integralAgrees(result: Expr, integrand: Expr, x: Expr.Sym, lo: Expr, hi: Expr, factor: Double = 1.0): Boolean {
        val others = ((result.symbols() + integrand.symbols() + lo.symbols() + hi.symbols()) - x.name).toList()
        val random = java.util.Random(43L * others.hashCode() + 11)
        var agreed = 0
        for (attempt in 0 until 30) {
            Budget.check()
            val env = others.associateWith { 0.5 + random.nextDouble() * 3.0 + 0.0137 }
            fun bound(b: Expr) = when {
                b == Expr.INF -> Double.POSITIVE_INFINITY
                b == neg(Expr.INF) -> Double.NEGATIVE_INFINITY
                else -> Numeric.eval(b, env)
            }
            val l = bound(lo)
            val h = bound(hi)
            val want = if (result.hasInfinity()) Double.NaN else Numeric.eval(result, env)
            val got = Quadrature.integrate({ t -> Numeric.eval(integrand, env + (x.name to t)) }, l, h)?.let { it * factor }
            if (got == null || !want.isFinite() || l.isNaN() || h.isNaN()) {
                if (others.isEmpty()) return false
                continue
            }
            if (abs(got - want) > 1e-7 * max(1.0, abs(want))) return false
            agreed++
            if (others.isEmpty() || agreed >= 3) return true
        }
        return false
    }

    /**
     * f defined between two numbers, with no gap inside (1/x on [−1, 1] is two curves); at an end it may be undefined, where the
     * integral is improper and converges or diverges (1/√x on [0, 1]).
     */
    private fun definedOn(f: Expr, x: Expr.Sym, lo: Expr, hi: Expr) {
        val l = boundValue(lo)
        val h = boundValue(hi)
        if (l.isNaN() || h.isNaN()) return
        val gaps = Analysis.gapsIn(f, x, l, h)
        if (gaps != null && gaps.isNotEmpty()) throw CalcException(Failure.UNDEFINED)
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Arc length.

    /** The arc length's work: f′, 1 + f′², the integrand √(1 + f′²) as simplified, and L. */
    class ArcWork(val derivative: Expr, val radicand: Expr, val integrand: Expr, val lo: Expr, val hi: Expr, val length: Expr)

    fun arcWork(f: Expr, x: Expr.Sym, a: Expr, b: Expr): ArcWork {
        val (lo, hi) = ordered(a, b)
        definedOn(f, x, lo, hi)
        val d = tidy(Calculus.derivative(f, x))
        val radicand = tidy(Algebra.expand(tidy(Expr.ONE + d * d)))
        val integrand = squareRoot(radicand, x, lo, hi) ?: sqrt(radicand)
        val length = integral(integrand, x, lo, hi)
        // Against the integrand as first written, √(1 + f′(x)²).
        if (!integralAgrees(length, sqrt(Expr.ONE + d * d), x, lo, hi)) throw CalcException(Failure.UNSOLVED)
        return ArcWork(d, radicand, integrand, lo, hi, length)
    }

    fun arcLength(f: Expr, x: Expr.Sym, a: Expr, b: Expr): Outcome = Applied.number(arcWork(f, x, a, b).length)

    /**
     * √[s] without the root where [s] is a square on the interval: g with g² = s, as ±g where g keeps its sign between the
     * bounds (numbers); textbook arc lengths are made so: 1 + ((x² − 1/x²)/4)² = ((x² + 1/x²)/4)², 1 + sinh² x = cosh² x.
     */
    private fun squareRoot(s: Expr, x: Expr.Sym, lo: Expr, hi: Expr): Expr? {
        val l = boundValue(lo)
        val h = boundValue(hi)
        if (l.isNaN() || h.isNaN() || !l.isFinite() || !h.isFinite()) return null
        val forms = ArrayList<Expr>()
        try { Algebra.factor(s)?.first?.let { forms += it } } catch (e: CalcException) { if (e.failure == Failure.TOO_LONG) throw e }
        forms += s
        try { forms += TrigForms.best(s, s) { Algebra.simplify(it) } } catch (e: CalcException) { if (e.failure == Failure.TOO_LONG) throw e }
        // 1 + sinh²u = cosh²u: sinh² written as cosh² − 1.
        try { forms += tidy(Algebra.expand(hyperbolic(s))) } catch (e: CalcException) { if (e.failure == Failure.TOO_LONG) throw e }
        for (form in forms.distinct()) {
            Budget.check()
            val g = halved(form) ?: continue
            if (!Verify.same(tidy(g * g), s)) continue
            // The sign of g between the bounds: none of its zeros inside, then its sign in the middle.
            val zeros = try { Solve.equation(g, Expr.ZERO, x) } catch (e: CalcException) {
                if (e.failure == Failure.TOO_LONG) throw e
                continue
            }
            if (zeros.many || zeros.always || zeros.periodic.isNotEmpty()) continue
            if (zeros.values.any { v -> Numeric.value(v)?.let { it > l && it < h } != false }) continue
            val mid = Numeric.eval(g, mapOf(x.name to (l + h) / 2))
            if (!mid.isFinite() || mid == 0.0) continue
            return tidy(if (mid > 0) g else neg(g))
        }
        return null
    }

    /** [e] with every sinh²u as cosh²u − 1. */
    private fun hyperbolic(e: Expr): Expr {
        if (e is Expr.Pow && integer(e.exp)?.toInt() == 2 && (e.base as? Expr.Fn)?.f == Func.SINH) {
            return pow(fn(Func.COSH, (e.base as Expr.Fn).args), Expr.TWO) - Expr.ONE
        }
        return e.mapChildren { hyperbolic(it) }
    }

    /** g with g² = [e] where [e] is written as squares: a power with an even exponent, a product of such, a positive number. */
    private fun halved(e: Expr): Expr? = when (e) {
        is Expr.Num -> if (e.value.signum > 0) tidy(sqrt(e)) else null
        is Expr.Pow -> integer(e.exp)?.let { n -> if (n.testBit(0) || n.signum() == 0) null else pow(e.base, num(Rational.of(n) / Rational.of(2))) }
        is Expr.Mul -> e.factors.map { halved(it) ?: return null }.let { mul(it) }
        else -> null
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Areas, piece by piece.

    /** ∫|h| from [lo] to [hi]: the cuts (zeros and poles of h inside), each piece's signed integral, and the total. */
    class Pieces(val cuts: List<Expr>, val pieces: List<Triple<Expr, Expr, Expr>>, val total: Expr)

    /**
     * ∫ from [lo] to [hi] of |[h]| dx between numbers: cut at h's zeros (exactly found, or numerically where the bounds are
     * given) and poles, each piece's integral with its sign taken off, the sum checked against ∫|h| by quadrature.
     */
    private fun absolute(h: Expr, x: Expr.Sym, lo: Expr, hi: Expr, zeros: List<Expr>): Pieces {
        val l = boundValue(lo)
        val u = boundValue(hi)
        val gaps = if (l.isNaN() || u.isNaN()) null else Analysis.gapsIn(h, x, l, u)
        if (gaps == null && (!l.isFinite() || !u.isFinite())) throw CalcException(Failure.UNSOLVED)
        val inside = (zeros + gaps.orEmpty()).filter { v -> Numeric.value(v)?.let { it > l + 1e-12 * max(1.0, abs(l)) && it < u - 1e-12 * max(1.0, abs(u)) } == true }
            .distinctBy { Math.round(Numeric.value(it)!! * 1e9) }.sortedBy { Numeric.value(it)!! }
        val ends = listOf(lo) + inside + listOf(hi)
        val pieces = ArrayList<Triple<Expr, Expr, Expr>>()
        var total: Expr = Expr.ZERO
        for (k in 0 until ends.size - 1) {
            Budget.check()
            val p = ends[k]
            val q = ends[k + 1]
            val signed = integral(h, x, p, q)
            val v = Numeric.value(signed) ?: throw CalcException(Failure.UNSOLVED)
            // h keeps its sign inside the piece: tried at points across it.
            if (!oneSigned(h, x, boundValue(p), boundValue(q))) throw CalcException(Failure.UNSOLVED)
            pieces += Triple(p, q, signed)
            total = total + (if (v < 0) neg(signed) else signed)
        }
        val sum = tidy(total)
        // Against ∫|h| over the whole interval: a crossing missed would leave a piece's sign wrong and the sums apart.
        val got = Quadrature.integrate({ t -> abs(Numeric.eval(h, mapOf(x.name to t))) }, l, u) ?: throw CalcException(Failure.UNSOLVED)
        val want = Numeric.value(sum) ?: throw CalcException(Failure.UNSOLVED)
        if (abs(got - want) > 1e-8 * max(1.0, abs(want))) throw CalcException(Failure.UNSOLVED)
        return Pieces(inside, pieces, sum)
    }

    /** No change of sign of [h] at 64 points inside (a, b) (each end mapped in from ±∞). */
    private fun oneSigned(h: Expr, x: Expr.Sym, a: Double, b: Double): Boolean {
        var sign = 0
        for (k in 1..64) {
            val s = k / 65.0
            val t = when {
                a.isInfinite() && b.isInfinite() -> Math.tan(Math.PI * (s - 0.5))
                a.isInfinite() -> b - (1 - s) / s
                b.isInfinite() -> a + s / (1 - s)
                else -> a + (b - a) * s
            }
            val v = Numeric.eval(h, mapOf(x.name to t))
            if (!v.isFinite() || v == 0.0) continue
            val here = if (v > 0) 1 else -1
            if (sign != 0 && here != sign) return false
            sign = here
        }
        return true
    }

    /**
     * The real zeros of [h] for the pieces: between [lo] and [hi] where both are numbers (a family's members there), or all of
     * them where no bounds are given, which must then be found exactly (a numeric search may miss one far out).
     */
    private fun zeros(h: Expr, x: Expr.Sym, lo: Double?, hi: Double?): List<Expr> {
        val bounded = lo != null && hi != null
        // Between given bounds a solver that stumbles (1/√x = 0) leaves the zeros to the pieces' check: a missed one shows there.
        val s = try { Solve.equation(h, Expr.ZERO, x) } catch (e: CalcException) {
            if (bounded && (e.failure == Failure.DIVISION_BY_ZERO || e.failure == Failure.UNDEFINED)) return emptyList() else throw e
        }
        if (s.many) throw CalcException(Failure.MANY_SOLUTIONS)
        if (s.always) return emptyList()
        val out = ArrayList<Expr>(s.values)
        if (s.periodic.isNotEmpty()) {
            if (lo == null || hi == null || !lo.isFinite() || !hi.isFinite()) throw CalcException(Failure.MANY_SOLUTIONS)
            for (family in s.periodic) {
                // The members k·p + c between the bounds: p from k = 0 and k = 1.
                val c = Numeric.eval(family, mapOf(K.name to 0.0))
                val p = Numeric.eval(family, mapOf(K.name to 1.0)) - c
                if (!c.isFinite() || !p.isFinite() || p == 0.0) throw CalcException(Failure.UNSOLVED)
                val from = kotlin.math.floor((minOf(lo, hi) - c) / p).toLong() - 1
                val to = kotlin.math.ceil((maxOf(lo, hi) - c) / p).toLong() + 1
                val (k0, k1) = if (from <= to) from to to else to to from
                if (k1 - k0 > 2000) throw CalcException(Failure.MANY_SOLUTIONS)
                for (k in k0..k1) out += tidy(family.substitute(K, num(k)))
            }
        }
        if ((lo == null || hi == null) && s.numeric) throw CalcException(Failure.UNSOLVED)
        // Each zero put back (the solver did; again, as the area rests on them).
        return out.filter { z ->
            val v = Numeric.eval(h, mapOf(x.name to (Numeric.value(z) ?: return@filter false)))
            v.isFinite() && abs(v) <= 1e-7 * (1 + abs(Numeric.value(z)!!))
        }.distinctBy { Math.round(Numeric.value(it)!! * 1e9) }.sortedBy { Numeric.value(it)!! }
    }

    /** The area's work: the curves' difference, the intersections, the bounds used and the pieces. */
    class AreaWork(val difference: Expr, val crossings: List<Expr>, val lo: Expr, val hi: Expr, val pieces: Pieces)

    fun areaWork(f: Expr, g: Expr, x: Expr.Sym, a: Expr?, b: Expr?): AreaWork {
        val h = tidy(f - g)
        val letters = h.symbols() - x.name + (a?.symbols() ?: emptySet()) + (b?.symbols() ?: emptySet())
        if (letters.isNotEmpty()) throw CalcException(Failure.SEVERAL_UNKNOWNS)
        if (a != null && b != null) {
            val (lo, hi) = ordered(a, b)
            if (lo == hi) return AreaWork(h, emptyList(), lo, hi, Pieces(emptyList(), emptyList(), Expr.ZERO))
            val l = boundValue(lo)
            val u = boundValue(hi)
            // The crossings between the bounds (or on them): those are the ones the area is cut at.
            val z = zeros(h, x, l, u).filter { v -> Numeric.value(v)!!.let { it >= l - 1e-12 * max(1.0, abs(l)) && it <= u + 1e-12 * max(1.0, abs(u)) } }
            return AreaWork(h, z, lo, hi, absolute(h, x, lo, hi, z))
        }
        // Between the intersections: from the first to the last.
        val z = zeros(h, x, null, null)
        if (z.size < 2) throw CalcException(Failure.UNSOLVED)
        return AreaWork(h, z, z.first(), z.last(), absolute(h, x, z.first(), z.last(), z))
    }

    fun areaBetween(f: Expr, g: Expr, x: Expr.Sym, a: Expr?, b: Expr?): Outcome = Applied.number(areaWork(f, g, x, a, b).pieces.total)

    // ------------------------------------------------------------------------------------------------------------------
    // Volumes of revolution.

    /** A volume's work: the integrand (f² for discs, x·f for shells), the bounds, the integral and the volume. */
    class VolumeWork(val integrand: Expr, val lo: Expr, val hi: Expr, val integral: Expr, val volume: Expr, val pieces: Pieces?)

    /** About the x-axis by discs: V = π ∫ f² dx. */
    fun volumeXWork(f: Expr, x: Expr.Sym, a: Expr, b: Expr): VolumeWork {
        val (lo, hi) = ordered(a, b)
        definedOn(f, x, lo, hi)
        val square = tidy(Algebra.expand(tidy(f * f)))
        val i = integral(square, x, lo, hi)
        if (!integralAgrees(i, f * f, x, lo, hi)) throw CalcException(Failure.UNSOLVED)
        return VolumeWork(square, lo, hi, i, tidy(Expr.PI * i), null)
    }

    fun volumeX(f: Expr, x: Expr.Sym, a: Expr, b: Expr): Outcome = Applied.number(volumeXWork(f, x, a, b).volume)

    /**
     * About the y-axis by shells: V = 2π ∫ |x|·|f(x)| dx, between numbers on one side of the axis (a region across it would
     * overlap its own mirror image), piece by piece where f changes sign.
     */
    fun volumeYWork(f: Expr, x: Expr.Sym, a: Expr, b: Expr): VolumeWork {
        if (f.symbols().any { it != x.name } || a.symbols().isNotEmpty() || b.symbols().isNotEmpty()) throw CalcException(Failure.SEVERAL_UNKNOWNS)
        val (lo, hi) = ordered(a, b)
        val l = boundValue(lo)
        val u = boundValue(hi)
        if (l.isNaN() || u.isNaN()) throw CalcException(Failure.ARGUMENTS)
        if (l < 0 && u > 0) throw CalcException(Failure.ARGUMENTS)
        definedOn(f, x, lo, hi)
        val shell = tidy(Algebra.expand(tidy(x * f)))
        if (lo == hi) return VolumeWork(shell, lo, hi, Expr.ZERO, Expr.ZERO, null)
        val z = zeros(f, x, l, u)
        val pieces = absolute(shell, x, lo, hi, z)
        return VolumeWork(shell, lo, hi, pieces.total, tidy(mul(Expr.TWO, Expr.PI, pieces.total)), pieces)
    }

    fun volumeY(f: Expr, x: Expr.Sym, a: Expr, b: Expr): Outcome = Applied.number(volumeYWork(f, x, a, b).volume)

    // ------------------------------------------------------------------------------------------------------------------
    // Mean value.

    /** The mean value's work: the integral and 1/(b − a) times it. */
    class MeanWork(val integral: Expr, val length: Expr, val mean: Expr)

    fun meanWork(f: Expr, x: Expr.Sym, a: Expr, b: Expr): MeanWork {
        if (a.hasInfinity() || b.hasInfinity()) throw CalcException(Failure.ARGUMENTS)
        val length = tidy(b - a)
        if (isZero(length)) throw CalcException(Failure.DIVISION_BY_ZERO)
        definedOn(f, x, a, b)
        val i = integral(f, x, a, b)
        if (!integralAgrees(i, f, x, a, b)) throw CalcException(Failure.UNSOLVED)
        return MeanWork(i, length, tidy(i / length))
    }

    fun meanValue(f: Expr, x: Expr.Sym, a: Expr, b: Expr): Outcome = Applied.number(meanWork(f, x, a, b).mean)
}

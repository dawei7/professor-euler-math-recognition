package com.professoreuler.mathcalc

import kotlin.math.abs
import kotlin.math.max

/** f(−x) against f(x). */
enum class Symmetry { EVEN, ODD, NEITHER }

/** A local extremum at ([x], [y]): a maximum where f rises before it and falls after, a minimum the other way round. */
class Extremum internal constructor(val x: Expr, val y: Expr, val maximum: Boolean)

/** A point of the graph: an inflection point. */
class GraphPoint internal constructor(val x: Expr, val y: Expr)

/** An asymptote: x = [at] (vertical), or y = [line] as x → −∞ ([side] −1) or +∞ ([side] 1). */
sealed class Asymptote {
    class Vertical internal constructor(val at: Expr) : Asymptote()
    class Line internal constructor(val side: Int, val line: Expr) : Asymptote()
}

/**
 * The properties of a function of one letter, as a curve sketch (Kurvendiskussion) gives them: its [domain], [zeros],
 * where it meets the y-axis, its [symmetry], local [extrema] and [inflections], where it rises and falls, where it is convex
 * and concave, its [asymptotes] and how it behaves at ±∞ ([limits]). Each comes from the solvers, which check every solution
 * in the equation, or from the signs of f′ and f″ between their zeros; a property that cannot be settled is null and not
 * shown, never guessed.
 */
class FunctionFacts internal constructor(
    val variable: String,
    val function: Expr,
    val domain: IntervalSet,
    /** Families of excluded points (tan x: π/2 + kπ), beside [domain]'s intervals. */
    val domainFamilies: List<Expr>,
    val zeros: Solutions?,
    val yIntercept: Expr?,
    val symmetry: Symmetry?,
    val derivative: Expr?,
    val second: Expr?,
    val extrema: List<Extremum>?,
    val inflections: List<GraphPoint>?,
    val increasing: List<Interval>?,
    val decreasing: List<Interval>?,
    val convex: List<Interval>?,
    val concave: List<Interval>?,
    val asymptotes: List<Asymptote>?,
    /** f at −∞ and at +∞ where the domain reaches them (±∞ as [Expr.INF]), null where it does not or no limit was found. */
    val limits: Pair<Expr?, Expr?>,
    /** The smallest period the solvers see (sin x: 2π), null where f is not periodic or it cannot be settled. */
    val period: Expr? = null,
    /** The values f takes: from its extrema and limits on all of ℝ, or over one period; null where that cannot be settled. */
    val range: IntervalSet? = null,
    /** f⁻¹ in the same letter, where f is strictly monotonic on its whole domain and y = f(x) solves for x in one way. */
    val inverse: Expr? = null,
)

/** The curve sketch of a function: [Analysis.of]. */
object Analysis {
    /** The facts of [f] in [x], or null where its domain cannot be settled (then nothing about it is shown). */
    fun of(f: Expr, x: Expr.Sym): FunctionFacts? {
        if (f.symbols() != setOf(x.name) || f.hasOp() || f.hasI()) return null
        val domain = domainOf(f, x) ?: return null
        val spans = domain.first
        val families = domain.second
        val set = IntervalSet(x.name, spans.map { it.interval() })

        val zeros = part { Solve.equation(f, Expr.ZERO, x) }?.let { s -> inDomain(s, spans, families, x) }
        val yIntercept = if (families.isEmpty() && contains(spans, 0.0)) part { Calculus.tidy(f.substitute(x, Expr.ZERO)) } else null
        val symmetry = part { symmetryOf(f, x, spans) }
        val d1 = part { Calculus.derivative(f, x) }
        val d2 = d1?.let { part { Calculus.derivative(f, x, 2) } }

        // Signs between zeros: only where the domain is a union of intervals (no families of poles) and the zeros are few.
        var extrema: List<Extremum>? = null
        var increasing: List<Interval>? = null
        var decreasing: List<Interval>? = null
        if (d1 != null && families.isEmpty()) {
            part { signs(f, d1, x, spans) }?.let { r ->
                increasing = r.positive
                decreasing = r.negative
                extrema = r.changes.map { (c, rising) -> Extremum(c, value(f, x, c), maximum = rising) }
            }
        }
        var inflections: List<GraphPoint>? = null
        var convex: List<Interval>? = null
        var concave: List<Interval>? = null
        if (d2 != null && families.isEmpty()) {
            part { signs(f, d2, x, spans) }?.let { r ->
                convex = r.positive
                concave = r.negative
                inflections = r.changes.map { (c, _) -> GraphPoint(c, value(f, x, c)) }
            }
        }
        // A periodic function: its extrema and inflection points as families, one period's worth, and the period itself.
        var period: Expr? = null
        if (d1 != null && extrema == null && x != K) {
            part { periodicPoints(f, d1, x) }?.let { (t, points) ->
                period = t
                val second = d2
                extrema = points.mapNotNull { a ->
                    val curve = second?.let { Numeric.value(value(it, x, a)) } ?: return@mapNotNull null
                    // The second derivative's sign settles an extremum only where it is clearly not zero.
                    if (abs(curve) < 1e-9) return@mapNotNull null
                    Extremum(Solve.simplifyValue(add(a, mul(K, t))), value(f, x, a), maximum = curve < 0)
                }.takeIf { it.size == points.size }
            }
            if (d2 != null && period != null) {
                part { periodicPoints(f, d2, x) }?.let { (t, points) ->
                    if (t == period) {
                        val third = part { Calculus.derivative(f, x, 3) }
                        inflections = points.mapNotNull { a ->
                            val v = third?.let { Numeric.value(value(it, x, a)) } ?: return@mapNotNull null
                            if (abs(v) < 1e-9) null else GraphPoint(Solve.simplifyValue(add(a, mul(K, t))), value(f, x, a))
                        }.takeIf { it.size == points.size }
                    }
                }
            }
        }
        val reachesLeft = spans.firstOrNull()?.lo == null
        val reachesRight = spans.lastOrNull()?.hi == null
        val atLeft = if (reachesLeft && families.isEmpty()) part { Calculus.limit(f, x, neg(Expr.INF), null) } else null
        val atRight = if (reachesRight && families.isEmpty()) part { Calculus.limit(f, x, Expr.INF, null) } else null
        val asymptotes = if (families.isEmpty()) part { asymptotesOf(f, x, spans, atLeft, atRight) } else null
        val range = part { rangeOf(spans, families, extrema, asymptotes, atLeft, atRight, period, x) }
        // One-to-one: rising, or falling, on all of its domain (one interval of it). Its inverse, where y = f(x) gives x once.
        val monotonic = spans.size == 1 && families.isEmpty() &&
            (increasing?.size == 1 && decreasing?.isEmpty() == true || decreasing?.size == 1 && increasing?.isEmpty() == true)
        val inverse = if (monotonic && x != Y) part { inverseOf(f, x) } else null
        return FunctionFacts(x.name, f, set, families, zeros, yIntercept, symmetry, d1, d2, extrema, inflections, increasing,
            decreasing, convex, concave, asymptotes, atLeft to atRight, period, range, inverse)
    }

    private val Y = Expr.Sym("y")

    /** y = f(x) solved for x, then written in x: f⁻¹(x). Null where it gives no single formula. */
    private fun inverseOf(f: Expr, x: Expr.Sym): Expr? {
        val s = Solve.forLetter(Y, f, x)
        val value = s.values.singleOrNull() ?: return null
        if (s.periodic.isNotEmpty() || value.has(x)) return null
        val g = Solve.simplifyValue(value.substitute(Y, x))
        // f(f⁻¹(x)) = x wherever both are defined.
        return g.takeIf { Verify.same(f.substitute(x, g), x) }
    }

    /**
     * Where [d] (f′ or f″) is zero along families (sin x: f′ = cos x is zero at π/2 + kπ): f's period, the smallest multiple
     * of the families' step that f repeats after, and the zeros of [d] in one period [0, T). Null where [d]'s zeros are no
     * families or f does not repeat after a few steps.
     */
    private fun periodicPoints(f: Expr, d: Expr, x: Expr.Sym): Pair<Expr, List<Expr>>? {
        val s = Solve.equation(d, Expr.ZERO, x)
        if (s.periodic.isEmpty() || s.many) return null
        val steps = s.periodic.map { fam -> Solve.simplifyValue(Algebra.expand(fam.substitute(K, Expr.ONE) - fam.substitute(K, Expr.ZERO))) }
        val step = steps.first()
        val stepValue = Numeric.value(step)?.takeIf { it > 0 } ?: return null
        if (steps.any { st -> Numeric.value(st)?.let { abs(it - stepValue) < 1e-9 } != true }) return null
        val m = (1..4).firstOrNull { k -> Verify.same(f.substitute(x, add(x, mul(num(k.toLong()), step))), f) } ?: return null
        val t = Solve.simplifyValue(mul(num(m.toLong()), step))
        val tv = Numeric.value(t) ?: return null
        val points = ArrayList<Expr>()
        for (fam in s.periodic) for (k in -m..m) {
            val v = Solve.simplifyValue(Algebra.expand(fam.substitute(K, num(k.toLong()))))
            val dv = Numeric.value(v) ?: continue
            if (dv >= -1e-12 && dv < tv - 1e-12 && points.none { p -> Numeric.value(p)?.let { abs(it - dv) < 1e-9 } == true }) points += v
        }
        return t to points.sortedBy { Numeric.value(it) }
    }

    /**
     * The values f takes, from what is settled: on all of ℝ without poles, the least and greatest of its extrema and its limits
     * at ±∞ (a limit is not attained, an extremum is); for a periodic function, its extrema's values over a period.
     */
    private fun rangeOf(spans: List<Span>, families: List<Expr>, extrema: List<Extremum>?, asymptotes: List<Asymptote>?,
        atLeft: Expr?, atRight: Expr?, period: Expr?, x: Expr.Sym): IntervalSet? {
        val list = extrema ?: return null
        if (families.isNotEmpty()) return null
        val values = list.mapNotNull { e -> Numeric.value(e.y)?.let { it to e.y } }
        if (values.size != list.size) return null
        if (period != null) {
            if (values.isEmpty()) return null
            val lo = values.minBy { it.first }
            val hi = values.maxBy { it.first }
            return IntervalSet("y", listOf(Interval(lo.second, true, hi.second, true)))
        }
        if (spans.size != 1 || spans[0].lo != null || spans[0].hi != null) return null
        if (asymptotes == null || asymptotes.any { it is Asymptote.Vertical }) return null
        val left = atLeft ?: return null
        val right = atRight ?: return null
        // The ends: a number (approached, not attained) or ±∞.
        fun end(e: Expr): Double? = when {
            e == Expr.INF -> Double.POSITIVE_INFINITY
            e.hasInfinity() -> Double.NEGATIVE_INFINITY
            else -> Numeric.value(e)
        }
        val ends = listOf(left to end(left), right to end(right))
        if (ends.any { it.second == null }) return null
        var lo: Pair<Double, Expr?> = Double.POSITIVE_INFINITY to null
        var loClosed = false
        var hi: Pair<Double, Expr?> = Double.NEGATIVE_INFINITY to null
        var hiClosed = false
        for ((d, e) in values) {
            if (d < lo.first - 1e-12) { lo = d to e; loClosed = true }
            if (d > hi.first + 1e-12) { hi = d to e; hiClosed = true }
        }
        for ((e, d) in ends) {
            val v = d!!
            if (v < lo.first - 1e-12) { lo = v to (if (v.isInfinite()) null else e); loClosed = false }
            if (v > hi.first + 1e-12) { hi = v to (if (v.isInfinite()) null else e); hiClosed = false }
        }
        return IntervalSet("y", listOf(Interval(lo.second, loClosed && lo.second != null, hi.second, hiClosed && hi.second != null)))
    }

    /** A part that fails to settle is left out; only the time running out stops the whole. */
    private inline fun <T> part(block: () -> T?): T? = try {
        block()
    } catch (e: CalcException) {
        if (e.failure == Failure.TOO_LONG) throw e
        null
    } catch (e: ArithmeticException) {
        null
    }

    private fun value(f: Expr, x: Expr.Sym, at: Expr): Expr = Calculus.tidy(f.substitute(x, at))

    // --------------------------------------------------------------------------------------------------------------------
    // The domain: the intersection of every condition the formula's functions put on their arguments.

    /** An interval with its bounds as expressions and as numbers (null for ∓∞). */
    private class Span(val lo: Expr?, val loClosed: Boolean, val hi: Expr?, val hiClosed: Boolean) {
        val a: Double get() = lo?.let { num(it) } ?: Double.NEGATIVE_INFINITY
        val b: Double get() = hi?.let { num(it) } ?: Double.POSITIVE_INFINITY
        fun interval() = Interval(lo, loClosed && lo != null, hi, hiClosed && hi != null)
        fun contains(t: Double): Boolean = (t > a || (loClosed && near(t, a))) && (t < b || (hiClosed && near(t, b)))
    }

    private val ALL = listOf(Span(null, false, null, false))

    private sealed class Condition {
        class Positive(val u: Expr) : Condition()
        class NonNegative(val u: Expr) : Condition()
        class NonZero(val u: Expr) : Condition()
    }

    /**
     * The points strictly between [lo] and [hi] (either may be infinite) where [f] is not defined, in order, where those are
     * single points (1/x at 0, tan x at π/2 + kπ); null where its domain cannot be said (Γ, ζ, a calculus sign) or leaves out a
     * whole stretch there (ln x on (−1, 1)). Periodic exclusions are looked for with k from −2000 to 2000.
     */
    internal fun gapsIn(f: Expr, x: Expr.Sym, lo: Double, hi: Double): List<Expr>? {
        val (spans, families) = try { domainOf(f, x) } catch (e: CalcException) {
            if (e.failure == Failure.TOO_LONG) throw e
            null
        } ?: return null
        val a = minOf(lo, hi)
        val b = maxOf(lo, hi)
        // The spans that meet (a, b), in order: they must cover it but for single points between them.
        val meeting = spans.filter { it.b > a && it.a < b && !near(it.b, a) && !near(it.a, b) }.sortedBy { it.a }
        if (meeting.isEmpty()) return null
        if (meeting.first().a > a && !near(meeting.first().a, a)) return null
        if (meeting.last().b < b && !near(meeting.last().b, b)) return null
        val points = ArrayList<Pair<Double, Expr>>()
        for (i in 1 until meeting.size) {
            val p = meeting[i - 1]
            val q = meeting[i]
            if (!near(p.b, q.a)) return null
            points += p.b to (p.hi ?: return null)
        }
        for (family in families) {
            for (k in -2000..2000) {
                val t = Numeric.eval(family, mapOf(K.name to k.toDouble()))
                if (!t.isFinite()) return null
                if (t > a && t < b && !near(t, a) && !near(t, b)) points += t to Calculus.tidy(family.substitute(K, num(k.toLong())))
            }
        }
        if (points.size > 40) return null
        val out = ArrayList<Pair<Double, Expr>>()
        for (p in points.sortedBy { it.first }) if (out.isEmpty() || !near(out.last().first, p.first)) out += p
        return out.map { it.second }
    }

    private fun domainOf(f: Expr, x: Expr.Sym): Pair<List<Span>, List<Expr>>? {
        val conditions = ArrayList<Condition>()
        if (!collect(f, x, conditions)) return null
        var spans = ALL
        val families = ArrayList<Expr>()
        for (c in conditions) {
            Budget.check()
            when (c) {
                is Condition.Positive -> spans = intersect(spans, setOf(c.u, Rel.GT, x) ?: return null)
                is Condition.NonNegative -> spans = intersect(spans, setOf(c.u, Rel.GE, x) ?: return null)
                is Condition.NonZero -> {
                    val s = Solve.equation(c.u, Expr.ZERO, x)
                    if (s.always || s.many) return null
                    families += s.periodic
                    if (s.periodic.isEmpty()) for (p in s.values) spans = without(spans, p)
                }
            }
        }
        return spans to families.distinct()
    }

    /** The conditions [e]'s functions put on [x]; false where one cannot be put in these terms (Γ, ζ, a calculus sign). */
    private fun collect(e: Expr, x: Expr.Sym, out: MutableList<Condition>): Boolean {
        if (!e.has(x)) return true
        return when (e) {
            is Expr.Add -> e.terms.all { collect(it, x, out) }
            is Expr.Mul -> e.factors.all { collect(it, x, out) }
            is Expr.Pow -> {
                if (!collect(e.base, x, out) || !collect(e.exp, x, out)) return false
                if (e.base.has(x)) {
                    val r = rational(e.exp)
                    when {
                        r == null -> if (e.base != Expr.E) out += Condition.Positive(e.base)
                        !r.isInteger && !r.den.testBit(0) -> out += if (r.signum < 0) Condition.Positive(e.base) else Condition.NonNegative(e.base)
                        r.signum < 0 -> out += Condition.NonZero(e.base)
                    }
                }
                true
            }
            is Expr.Fn -> {
                if (!e.args.all { collect(it, x, out) }) return false
                val u = e.args.last()
                when (e.f) {
                    Func.LN -> out += Condition.Positive(u)
                    Func.LOG -> if (e.args[0].has(x)) return false else out += Condition.Positive(u)
                    Func.ASIN, Func.ACOS -> {
                        out += Condition.NonNegative(Expr.ONE - u)
                        out += Condition.NonNegative(Expr.ONE + u)
                    }
                    Func.TAN, Func.SEC -> out += Condition.NonZero(fn(Func.COS, u))
                    Func.COT, Func.CSC -> out += Condition.NonZero(fn(Func.SIN, u))
                    Func.SIN, Func.COS, Func.ATAN, Func.ACOT, Func.SINH, Func.COSH, Func.TANH, Func.ABS, Func.ERF, Func.ERFC -> {}
                    Func.SECH, Func.ASINH, Func.SI -> {}
                    Func.COTH, Func.CSCH, Func.EI -> out += Condition.NonZero(u)
                    Func.ACOSH -> out += Condition.NonNegative(u - Expr.ONE)
                    Func.ATANH -> {
                        out += Condition.Positive(Expr.ONE - u)
                        out += Condition.Positive(Expr.ONE + u)
                    }
                    Func.ACOTH -> out += Condition.Positive(u * u - Expr.ONE)
                    Func.ASEC, Func.ACSC -> out += Condition.NonNegative(u * u - Expr.ONE)
                    Func.CI -> out += Condition.Positive(u)
                    Func.ELLIPTICK -> out += Condition.Positive(Expr.ONE - u * u)
                    Func.ELLIPTICE -> out += Condition.NonNegative(Expr.ONE - u * u)
                    // Steps and special functions: their domains are not put in these terms.
                    else -> return false
                }
                true
            }
            else -> e !is Expr.Op
        }
    }

    private fun setOf(u: Expr, rel: Rel, x: Expr.Sym): List<Span>? {
        val s = Solve.inequality(u, rel, Expr.ZERO, x) ?: return null
        return s.intervals.map { Span(it.lower, it.lowerClosed, it.upper, it.upperClosed) }
    }

    private fun intersect(a: List<Span>, b: List<Span>): List<Span> {
        val out = ArrayList<Span>()
        for (p in a) for (q in b) {
            // The larger lower bound, the smaller upper bound; at a tie a bound is closed only if both are.
            val (lo, loClosed) = when {
                p.lo == null -> q.lo to q.loClosed
                q.lo == null -> p.lo to p.loClosed
                near(p.a, q.a) -> p.lo to (p.loClosed && q.loClosed)
                p.a > q.a -> p.lo to p.loClosed
                else -> q.lo to q.loClosed
            }
            val (hi, hiClosed) = when {
                p.hi == null -> q.hi to q.hiClosed
                q.hi == null -> p.hi to p.hiClosed
                near(p.b, q.b) -> p.hi to (p.hiClosed && q.hiClosed)
                p.b < q.b -> p.hi to p.hiClosed
                else -> q.hi to q.hiClosed
            }
            val span = Span(lo, loClosed, hi, hiClosed)
            if (span.a < span.b - TIE || (near(span.a, span.b) && loClosed && hiClosed)) out += span
        }
        return out.sortedBy { it.a }
    }

    private fun without(spans: List<Span>, p: Expr): List<Span> {
        val t = num(p)
        if (!t.isFinite()) return spans
        val out = ArrayList<Span>()
        for (s in spans) {
            when {
                !s.contains(t) -> out += s
                s.lo != null && near(t, s.a) -> out += Span(s.lo, false, s.hi, s.hiClosed)
                s.hi != null && near(t, s.b) -> out += Span(s.lo, s.loClosed, s.hi, false)
                else -> {
                    out += Span(s.lo, s.loClosed, p, false)
                    out += Span(p, false, s.hi, s.hiClosed)
                }
            }
        }
        return out
    }

    private fun contains(spans: List<Span>, t: Double) = spans.any { it.contains(t) }

    /** The zeros that are in the domain; the solver has put each in the equation already. */
    private fun inDomain(s: Solutions, spans: List<Span>, families: List<Expr>, x: Expr.Sym): Solutions {
        if (s.periodic.isNotEmpty() || s.many || s.always) return s
        val kept = s.values.filter { v -> num(v).let { it.isFinite() && contains(spans, it) } }
        return Solutions(s.variable, kept, emptyList(), s.complex, false, s.rejected + s.values.filter { it !in kept }, s.excluded,
            s.numeric, false, s.conditions, s.degrees)
    }

    // --------------------------------------------------------------------------------------------------------------------
    // Symmetry.

    private fun symmetryOf(f: Expr, x: Expr.Sym, spans: List<Span>): Symmetry? {
        // Only a domain that is its own mirror image can carry an even or odd function.
        val mirrored = spans.all { s -> spans.any { m -> near(m.a, -s.b) && near(m.b, -s.a) } }
        if (!mirrored) return Symmetry.NEITHER
        val reflected = Calculus.tidy(f.substitute(x, neg(x)))
        return when {
            Verify.same(reflected, f) -> Symmetry.EVEN
            Verify.same(reflected, neg(f)) -> Symmetry.ODD
            Verify.differenceAt(reflected, f) != null && Verify.differenceAt(reflected, neg(f)) != null -> Symmetry.NEITHER
            else -> null
        }
    }

    // --------------------------------------------------------------------------------------------------------------------
    // Where a derivative is positive and negative, and where it changes sign.

    private class Signs(val positive: List<Interval>, val negative: List<Interval>, val changes: List<Pair<Expr, Boolean>>)

    /**
     * The signs of [d] between its zeros on each interval of the domain: the intervals where it is positive and negative,
     * and the zeros where it changes sign ([Boolean] true where it goes from + to −). Null where its zeros are not a short
     * finite list, or a sign cannot be read between them.
     */
    private fun signs(f: Expr, d: Expr, x: Expr.Sym, spans: List<Span>): Signs? {
        val zeros = Solve.equation(d, Expr.ZERO, x)
        if (zeros.always) {
            // f′ = 0 everywhere: a constant, neither rising nor falling.
            return Signs(emptyList(), emptyList(), emptyList())
        }
        if (zeros.periodic.isNotEmpty() || zeros.many || zeros.values.size > 20) return null
        val positive = ArrayList<Interval>()
        val negative = ArrayList<Interval>()
        val changes = ArrayList<Pair<Expr, Boolean>>()
        for (span in spans) {
            val inside = zeros.values.filter { v -> num(v).let { it.isFinite() && it > span.a + TIE && it < span.b - TIE } }
                .sortedBy { num(it) }
            val bounds = listOf(span.lo) + inside + listOf(span.hi)
            val pieceSigns = ArrayList<Int>()
            for (k in 0 until bounds.size - 1) {
                val lo = bounds[k]
                val hi = bounds[k + 1]
                val t = probe(lo?.let { num(it) }, hi?.let { num(it) })
                val v = Numeric.eval(d, mapOf(x.name to t))
                if (!v.isFinite()) return null
                pieceSigns += if (abs(v) < 1e-12 * max(1.0, abs(Numeric.eval(f, mapOf(x.name to t))))) 0 else if (v > 0) 1 else -1
            }
            // Neighbouring pieces of the same sign join through the zero between them (x³ rises through 0).
            var start = bounds[0]
            for (k in pieceSigns.indices) {
                val last = k == pieceSigns.size - 1
                val joins = !last && pieceSigns[k] == pieceSigns[k + 1]
                if (!last && !joins && pieceSigns[k] != 0 && pieceSigns[k + 1] != 0) changes += bounds[k + 1]!! to (pieceSigns[k] > 0)
                if (!joins) {
                    val piece = Interval(start, false, bounds[k + 1], false)
                    when (pieceSigns[k]) {
                        1 -> positive += piece
                        -1 -> negative += piece
                    }
                    start = bounds[k + 1]
                }
            }
        }
        return Signs(positive, negative, changes)
    }

    /** A point inside (lo, hi) to read a sign at: the middle, or one unit inside an endless side. */
    private fun probe(lo: Double?, hi: Double?): Double = when {
        lo == null && hi == null -> 0.0
        lo == null -> hi!! - max(1.0, abs(hi))
        hi == null -> lo + max(1.0, abs(lo))
        else -> (lo + hi) / 2
    }

    // --------------------------------------------------------------------------------------------------------------------
    // Asymptotes.

    private fun asymptotesOf(f: Expr, x: Expr.Sym, spans: List<Span>, atLeft: Expr?, atRight: Expr?): List<Asymptote> {
        val out = ArrayList<Asymptote>()
        // A vertical one where the function grows without bound towards a finite end of its domain.
        val ends = LinkedHashMap<Double, Pair<Expr, MutableSet<Int>>>()
        for (s in spans) {
            s.lo?.let { ends.getOrPut(s.a) { it to mutableSetOf() }.second += 1 }
            s.hi?.let { ends.getOrPut(s.b) { it to mutableSetOf() }.second += -1 }
        }
        for ((_, entry) in ends.toSortedMap()) {
            val (p, sides) = entry
            val infinite = sides.any { side -> part { Calculus.limit(f, x, p, side) }?.let { it == Expr.INF || it == neg(Expr.INF) } == true }
            if (infinite) out += Asymptote.Vertical(p)
        }
        // A level or slanting line at each endless side.
        for ((side, at) in listOf(-1 to atLeft, 1 to atRight)) {
            if (at == null) continue
            val point = if (side < 0) neg(Expr.INF) else Expr.INF
            if (at != Expr.INF && at != neg(Expr.INF)) {
                if (at !is Expr.Real) out += Asymptote.Line(side, at)
                continue
            }
            val m = part { Calculus.limit(Calculus.tidy(f / x), x, point, null) } ?: continue
            if (m.hasInfinity() || m is Expr.Real || isZero(m)) continue
            val b = part { Calculus.limit(Calculus.tidy(f - m * x), x, point, null) } ?: continue
            if (b.hasInfinity() || b is Expr.Real) continue
            out += Asymptote.Line(side, Calculus.tidy(m * x + b))
        }
        return out
    }

    // --------------------------------------------------------------------------------------------------------------------

    private const val TIE = 1e-9

    private fun num(e: Expr): Double = Numeric.value(e) ?: Double.NaN

    /** Equal within rounding; an infinite end is near only itself (∞ ≤ ∞ would make 0 near −∞). */
    private fun near(a: Double, b: Double) = a == b || (a.isFinite() && b.isFinite() && abs(a - b) <= TIE * max(1.0, max(abs(a), abs(b))))
}

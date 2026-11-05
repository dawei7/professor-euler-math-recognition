package com.professoreuler.mathcalc

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** A window of a graph: x from [xMin] to [xMax], y from [yMin] to [yMax]. */
data class PlotWindow(val xMin: Double, val xMax: Double, val yMin: Double, val yMax: Double) {
    val width: Double get() = xMax - xMin
    val height: Double get() = yMax - yMin

    fun panned(dx: Double, dy: Double) = PlotWindow(xMin + dx, xMax + dx, yMin + dy, yMax + dy)

    /** Closer by [factor] (more than 1) or farther (less than 1) about the point ([cx], [cy]), within sensible bounds. */
    fun zoomed(factor: Double, cx: Double, cy: Double): PlotWindow {
        val f = factor.coerceIn(0.05, 20.0)
        val w = (width / f).coerceIn(MIN_SIZE, MAX_SIZE)
        val h = (height / f).coerceIn(MIN_SIZE, MAX_SIZE)
        val fx = (cx - xMin) / width
        val fy = (cy - yMin) / height
        return PlotWindow(cx - fx * w, cx + (1 - fx) * w, cy - fy * h, cy + (1 - fy) * h)
    }

    /** The same middle, its height set so a unit is as long on both axes in a view [pixelsWide] by [pixelsHigh]. */
    fun equalScale(pixelsWide: Int, pixelsHigh: Int): PlotWindow {
        if (pixelsWide <= 0 || pixelsHigh <= 0) return this
        val unitsPerPixel = max(width / pixelsWide, height / pixelsHigh)
        val cx = (xMin + xMax) / 2
        val cy = (yMin + yMax) / 2
        val w = unitsPerPixel * pixelsWide / 2
        val h = unitsPerPixel * pixelsHigh / 2
        return PlotWindow(cx - w, cx + w, cy - h, cy + h)
    }

    companion object {
        const val MIN_SIZE = 1e-6
        const val MAX_SIZE = 1e7
    }
}

/**
 * A point a graph marks: a zero, a minimum or maximum, where two curves meet, where a curve crosses the y-axis; on curve
 * [curve] (for an intersection the first of the two). [exactX], [exactY] where Calculate knows them exactly.
 */
class PlotMark(val kind: Kind, val curve: Int, val x: Double, val y: Double, val exactX: Expr?, val exactY: Expr?) {
    enum class Kind { ZERO, MINIMUM, MAXIMUM, INTERSECTION, Y_INTERCEPT }
}

/** One curve in a window: its pieces, each a polyline x₀, y₀, x₁, y₁, …, split where it is undefined, jumps or has a pole. */
class PlotCurve(val curve: Int, val pieces: List<DoubleArray>)

/** The curves of a window and the poles found in it (vertical asymptotes, x values). */
class PlotSample(val curves: List<PlotCurve>, val poles: List<Double>)

/**
 * A graph worked out once (off the main thread: it solves for zeros, extrema and intersections): its curves as fast
 * functions, the window that shows what is interesting, the marks, the vertical asymptotes; then [sample] draws any window.
 */
class PreparedPlot internal constructor(
    /** The letter on the horizontal axis. */
    val variable: String,
    /** The window that shows the interesting part: zeros, extrema, intersections, the y-intercept, poles. */
    val window: PlotWindow,
    val marks: List<PlotMark>,
    /** Vertical asymptotes found exactly (x values). */
    val asymptotes: List<Double>,
    /** The curves are trigonometric: the horizontal axis reads best in multiples of π. */
    val trigonometric: Boolean,
    /** An equation in x and y drawn as a curve (x² + y² = 4): a unit should be as long on both axes. */
    val implicit: Boolean,
    private val functions: List<(Double) -> Double>,
    private val implicitFunction: ((Double, Double) -> Double)?,
    /** An inequality's relation: the graph is the region where the implicit function stands so to 0. */
    val region: Rel? = null,
    /** A parametric or polar curve, sampled once: its pieces, in any window. */
    private val path: List<DoubleArray>? = null,
) {
    /** Whether the region holds at (x, y); false for a graph that is no region. */
    fun holds(x: Double, y: Double): Boolean {
        val r = region ?: return false
        val v = implicitFunction?.invoke(x, y) ?: return false
        if (!v.isFinite()) return false
        return when (r) {
            Rel.LT -> v < 0
            Rel.LE -> v <= 0
            Rel.GT -> v > 0
            Rel.GE -> v >= 0
            else -> false
        }
    }

    /** How many curves there are. */
    val curves: Int get() = if (implicit) 1 else functions.size

    /** Curve [curve]'s value at [x]; NaN where it is not defined (and for an implicit curve). */
    fun value(curve: Int, x: Double): Double = if (implicit) Double.NaN else functions[curve](x)

    /** The curves in [window], about one sample per pixel of [pixels] across. */
    fun sample(window: PlotWindow, pixels: Int): PlotSample {
        path?.let { return PlotSample(listOf(PlotCurve(0, it)), emptyList()) }
        val n = pixels.coerceIn(100, 2000)
        if (implicit) return PlotSample(listOf(PlotCurve(0, Plot.contour(implicitFunction!!, window, n))), emptyList())
        val poles = ArrayList<Double>()
        val curves = functions.mapIndexed { k, f -> PlotCurve(k, Plot.trace(f, window, n, poles)) }
        return PlotSample(curves, poles.distinctBy { Math.round(it / max(window.width, 1e-12) * 1e4) })
    }
}

/**
 * Graphs of what Calculate reads: functions of one letter (both sides of an equation, which meet at its solutions) and
 * curves of an equation in x and y. The functions are compiled to plain arithmetic once; marks are exact where the engine
 * finds them (zeros by Solve, extrema by the derivative's zeros) and filled in numerically where it does not.
 */
object Plot {
    /** How long the exact search for marks may take before the numeric one takes over. */
    private const val EXACT_MS = 1500L
    private const val MOST_MARKS = 40

    /**
     * [graph] worked out for drawing; [exact] false while a slider moves: the marks are then found only numerically, fast.
     * Parameters still in it stand at 1.
     */
    fun prepare(graph0: Outcome.Graph, exact: Boolean = true): PreparedPlot {
        val graph = if (graph0.parameters.isEmpty()) graph0 else at(graph0, graph0.parameters.associateWith { 1.0 }, keep = false)
        val x = Expr.Sym(graph.variable)
        graph.implicit?.let { return prepareImplicit(it, graph.region) }
        graph.curve?.let { return prepareCurve(it, graph.variable) }
        val functions = graph.functions.map { compile(it, graph.variable) }
        val exact = if (exact) exactPoints(graph.functions, x) else Exact().also { it.functions += graph.functions }
        val trig = period(graph.functions, x)
        val window0 = xRange(exact, trig)
        val members = exact.withPeriodicIn(window0, x)
        val numeric = numericPoints(functions, window0)
        val marks = merge(members, numeric).filter { it.x >= window0.first - window0.width() && it.x <= window0.second + window0.width() }
            .sortedBy { it.x }.take(MOST_MARKS * max(1, functions.size))
        val asymptotes = members.poles.map { it.second }.filter { it >= window0.first - window0.width() && it <= window0.second + window0.width() }
            .distinctBy { Math.round(it * 1e9) }
        val window = yRange(functions, window0, marks)
        return PreparedPlot(graph.variable, window, marks, asymptotes, trig != null, false, functions, null)
    }

    private fun Pair<Double, Double>.width() = second - first

    // ---------------------------------------------------------------------------------------------------------------------
    // Compiling: an expression as a function of doubles, with the real odd roots and the domains Numeric has.

    /** [e] as a function of [variable]; another letter makes it NaN. */
    fun compile(e: Expr, variable: String): (Double) -> Double {
        val c = compile2(e, variable, null)
        return { x -> c(x, 0.0) }
    }

    /** [e] as a function of x and y. */
    fun compile2(e: Expr): (Double, Double) -> Double = compile2(e, "x", "y")

    private fun compile2(e: Expr, a: String, b: String?): (Double, Double) -> Double = when (e) {
        is Expr.Num -> e.value.toDouble().let { v -> { _, _ -> v } }
        is Expr.Real -> e.value.let { v -> { _, _ -> v } }
        is Expr.Sym -> when (e.name) {
            a -> { x, _ -> x }
            b -> { _, y -> y }
            else -> { _, _ -> Double.NaN }
        }
        is Expr.Const -> when (e.kind) {
            Constant.PI -> { _, _ -> Math.PI }
            Constant.E -> { _, _ -> Math.E }
            else -> { _, _ -> Double.NaN }
        }
        is Expr.Add -> {
            val parts = e.terms.map { compile2(it, a, b) }.toTypedArray()
            val sum: (Double, Double) -> Double = { x, y -> var s = 0.0; for (p in parts) s += p(x, y); s }
            sum
        }
        is Expr.Mul -> {
            val parts = e.factors.map { compile2(it, a, b) }.toTypedArray()
            val product: (Double, Double) -> Double = { x, y -> var s = 1.0; for (p in parts) s *= p(x, y); s }
            product
        }
        is Expr.Pow -> power(e, a, b)
        is Expr.Fn -> {
            val args = e.args.map { compile2(it, a, b) }
            val f = e.f
            if (args.size == 1) {
                val u = args[0]
                when (f) {
                    Func.SIN -> { x, y -> kotlin.math.sin(u(x, y)) }
                    Func.COS -> { x, y -> kotlin.math.cos(u(x, y)) }
                    Func.TAN -> { x, y -> kotlin.math.tan(u(x, y)) }
                    Func.LN -> { x, y -> u(x, y).let { v -> if (v > 0) ln(v) else Double.NaN } }
                    Func.ABS -> { x, y -> abs(u(x, y)) }
                    else -> { x, y -> Numeric.function(f, listOf(u(x, y))) }
                }
            } else { x, y -> Numeric.function(f, args.map { it(x, y) }) }
        }
        is Expr.Op -> { _, _ -> Double.NaN }
    }

    private fun power(p: Expr.Pow, a: String, b: String?): (Double, Double) -> Double {
        val base = compile2(p.base, a, b)
        val r = rational(p.exp)
        if (r != null && r.isInteger && r.num.bitLength() < 31) {
            val n = r.num.toInt()
            return when (n) {
                2 -> { x, y -> base(x, y).let { v -> v * v } }
                -1 -> { x, y -> base(x, y).let { v -> if (v == 0.0) Double.NaN else 1 / v } }
                else -> { x, y -> base(x, y).let { v -> if (v == 0.0 && n < 0) Double.NaN else v.pow(n) } }
            }
        }
        if (r != null && r.den.testBit(0)) {
            // Odd roots of negatives are real: ∛(−8) = −2.
            val e = r.toDouble()
            val oddNumerator = r.num.testBit(0)
            return { x, y ->
                val v = base(x, y)
                if (v < 0) { val m = (-v).pow(e); if (oddNumerator) -m else m } else v.pow(e)
            }
        }
        val exponent = compile2(p.exp, a, b)
        return { x, y -> base(x, y).pow(exponent(x, y)) }
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Exact points: zeros, extrema, intersections, the y-intercept, poles.

    private class Exact {
        /** (curve, kind, x exact) in families or values. */
        val points = ArrayList<Triple<Int, PlotMark.Kind, Expr>>()
        val families = ArrayList<Triple<Int, PlotMark.Kind, Expr>>()
        /** (curve, x exact) of poles; [poleFamilies] periodic ones. */
        val poles = ArrayList<Pair<Int, Expr>>()
        val poleFamilies = ArrayList<Pair<Int, Expr>>()
        val functions = ArrayList<Expr>()
    }

    /** What the engine finds exactly, within [EXACT_MS]: an empty list of anything it cannot. */
    private fun exactPoints(functions: List<Expr>, x: Expr.Sym): Exact {
        val out = Exact()
        out.functions += functions
        try {
            Budget.within(EXACT_MS) {
                for ((k, f) in functions.withIndex()) {
                    solutions(f, Expr.ZERO, x)?.let { s -> add(out, k, PlotMark.Kind.ZERO, s) }
                    try {
                        val d = Calculus.derivative(f, x)
                        solutions(d, Expr.ZERO, x)?.let { s -> add(out, k, PlotMark.Kind.MINIMUM, s) }
                    } catch (e: CalcException) {
                        if (e.failure == Failure.TOO_LONG) throw e
                    }
                    if (f.has(x)) out.points += Triple(k, PlotMark.Kind.Y_INTERCEPT, Expr.ZERO)
                    for (d in denominators(f, x)) solutions(d, Expr.ZERO, x)?.let { s ->
                        s.values.forEach { out.poles += k to it }
                        s.periodic.forEach { out.poleFamilies += k to it }
                    }
                }
                if (functions.size == 2) solutions(functions[0], functions[1], x)?.let { s -> add(out, 0, PlotMark.Kind.INTERSECTION, s) }
            }
        } catch (e: CalcException) {
            // Too long or too hard: the numeric search finds what there is to see.
        }
        return out
    }

    private fun solutions(a: Expr, b: Expr, x: Expr.Sym): Solutions? = try {
        Solve.equation(a, b, x).takeIf { !it.always && !it.many }
    } catch (e: CalcException) {
        if (e.failure == Failure.TOO_LONG) throw e
        null
    }

    private fun add(out: Exact, curve: Int, kind: PlotMark.Kind, s: Solutions) {
        if (s.periodic.isNotEmpty()) s.periodic.forEach { out.families += Triple(curve, kind, it) }
        else s.values.forEach { out.points += Triple(curve, kind, it) }
    }

    /** What divides by an expression in x (1/(x − 2), tan x = sin x/cos x): those expressions, whose zeros are poles. */
    private fun denominators(f: Expr, x: Expr.Sym): List<Expr> {
        val out = ArrayList<Expr>()
        fun walk(e: Expr) {
            when (e) {
                is Expr.Pow -> {
                    if (e.base.has(x) && rational(e.exp)?.signum == -1) out += e.base
                    walk(e.base); walk(e.exp)
                }
                is Expr.Fn -> {
                    val u = e.args.last()
                    if (u.has(x)) when (e.f) {
                        Func.TAN, Func.SEC -> out += fn(Func.COS, u)
                        Func.COT, Func.CSC -> out += fn(Func.SIN, u)
                        Func.LN, Func.LOG -> out += u
                        else -> Unit
                    }
                    e.args.forEach(::walk)
                }
                is Expr.Add -> e.terms.forEach(::walk)
                is Expr.Mul -> e.factors.forEach(::walk)
                else -> Unit
            }
        }
        walk(f)
        return out.distinct()
    }

    /** The members of each family (and the plain points) as marks within [range] and a width beyond it, with their y. */
    private class Points(val marks: List<PlotMark>, val poles: List<Pair<Int, Double>>)

    private fun Exact.withPeriodicIn(range: Pair<Double, Double>, x: Expr.Sym): Points {
        val lo = range.first - range.width()
        val hi = range.second + range.width()
        val xs = ArrayList<Triple<Int, PlotMark.Kind, Expr>>()
        xs += points
        for ((curve, kind, fam) in families) for (k in -60..60) {
            val v = try { Solve.simplifyValue(fam.substitute(K, num(k.toLong()))) } catch (e: CalcException) { continue }
            val d = Numeric.value(v) ?: continue
            if (d in lo..hi) xs += Triple(curve, kind, v)
        }
        val marks = ArrayList<PlotMark>()
        for ((curve, kind0, xe) in xs) {
            val xv = Numeric.value(xe) ?: continue
            if (xv < lo || xv > hi) continue
            val f = functions[curve]
            val yv = Numeric.eval(f, mapOf(x.name to xv))
            if (!yv.isFinite()) continue
            val kind = when (kind0) {
                PlotMark.Kind.MINIMUM -> extremum(f, x, xv) ?: continue
                else -> kind0
            }
            val ye = when (kind) {
                PlotMark.Kind.ZERO -> Expr.ZERO
                else -> exactValue(f.substitute(x, xe), yv)
            }
            marks += PlotMark(kind, curve, xv, if (kind == PlotMark.Kind.ZERO) 0.0 else yv, xe.takeIf { it !is Expr.Real }, ye)
        }
        val poles = ArrayList<Pair<Int, Double>>()
        for ((c, p) in this.poles) Numeric.value(p)?.let { poles += c to it }
        for ((c, fam) in poleFamilies) for (k in -60..60) {
            val d = try { Numeric.value(fam.substitute(K, num(k.toLong()))) } catch (e: CalcException) { null } ?: continue
            if (d in lo..hi) poles += c to d
        }
        return Points(marks, poles)
    }

    /** f's value as an exact expression where it simplifies to one that agrees with [numeric]; else null. */
    private fun exactValue(e: Expr, numeric: Double): Expr? = try {
        val s = Solve.simplifyValue(e)
        val v = Numeric.value(s)
        if (s !is Expr.Real && v != null && abs(v - numeric) <= 1e-9 * max(1.0, abs(numeric)) && MathCalc.niceExact(s)) s else null
    } catch (x: CalcException) {
        null
    }

    /** Whether f has a minimum or a maximum at [x0] (or neither, as x³ at 0): by its values on both sides. */
    private fun extremum(f: Expr, x: Expr.Sym, x0: Double): PlotMark.Kind? {
        val h = 1e-4 * max(1.0, abs(x0))
        val y0 = Numeric.eval(f, mapOf(x.name to x0))
        val l = Numeric.eval(f, mapOf(x.name to x0 - h))
        val r = Numeric.eval(f, mapOf(x.name to x0 + h))
        if (!y0.isFinite() || !l.isFinite() || !r.isFinite()) return null
        val eps = 1e-12 * max(1.0, abs(y0))
        return when {
            l > y0 + eps && r > y0 + eps -> PlotMark.Kind.MINIMUM
            l < y0 - eps && r < y0 - eps -> PlotMark.Kind.MAXIMUM
            else -> null
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The window.

    /** The period of the curves' trigonometric parts, where every one has a linear argument: the longest. */
    private fun period(functions: List<Expr>, x: Expr.Sym): Double? {
        var longest: Double? = null
        var ok = true
        fun walk(e: Expr) {
            if (e is Expr.Fn && e.args.last().has(x) && e.f in TRIG) {
                val c = Solve.coefficientsIn(Algebra.expand(e.args.last()), x)
                val a = c?.takeIf { it.size == 2 }?.let { Numeric.value(it[1]) }
                if (a == null || a == 0.0) ok = false
                else {
                    val p = (if (e.f == Func.TAN || e.f == Func.COT) Math.PI else 2 * Math.PI) / abs(a)
                    longest = max(longest ?: 0.0, p)
                }
            }
            when (e) {
                is Expr.Add -> e.terms.forEach(::walk)
                is Expr.Mul -> e.factors.forEach(::walk)
                is Expr.Pow -> { walk(e.base); walk(e.exp) }
                is Expr.Fn -> e.args.forEach(::walk)
                else -> Unit
            }
        }
        functions.forEach(::walk)
        return if (ok) longest else null
    }

    private val TRIG = setOf(Func.SIN, Func.COS, Func.TAN, Func.COT, Func.SEC, Func.CSC)

    /** The x range: two periods of a trigonometric curve, otherwise the exact points with room around them, 0 among them. */
    private fun xRange(exact: Exact, period: Double?): Pair<Double, Double> {
        if (period != null) {
            val half = min(period, 1e4)
            return -half * 1.02 to half * 1.02
        }
        val xs = ArrayList<Double>()
        exact.points.forEach { (_, _, e) -> Numeric.value(e)?.let { if (it.isFinite() && abs(it) < 1e6) xs += it } }
        exact.poles.forEach { (_, e) -> Numeric.value(e)?.let { if (it.isFinite() && abs(it) < 1e6) xs += it } }
        if (xs.isEmpty()) return -10.0 to 10.0
        var lo = xs.min()
        var hi = xs.max()
        // The y-axis in view, unless the interesting part is far from it.
        if (lo > 0 && lo < 2 * (hi - lo) + 4) lo = 0.0
        if (hi < 0 && -hi < 2 * (hi - lo) + 4) hi = 0.0
        val span = hi - lo
        val pad = max(span * 0.35, if (span < 1e-9) 5.0 else 1.5)
        return niceBound(lo - pad, floor = true) to niceBound(hi + pad, floor = false)
    }

    /** A bound rounded outward to a nice step of its size. */
    private fun niceBound(v: Double, floor: Boolean): Double {
        val step = 10.0.pow(floor(log10(max(abs(v), 1e-9))) - 1).coerceAtLeast(0.01)
        return if (floor) floor(v / step) * step else ceil(v / step) * step
    }

    /** The y range: what most of the curve's values cover (a pole's run-away cut off), the marks, and y = 0 where near. */
    private fun yRange(functions: List<(Double) -> Double>, xr: Pair<Double, Double>, marks: List<PlotMark>): PlotWindow {
        val values = ArrayList<Double>()
        val n = 600
        for (f in functions) for (k in 0..n) {
            val v = f(xr.first + xr.width() * k / n)
            if (v.isFinite()) values += v
        }
        if (values.isEmpty()) return PlotWindow(xr.first, xr.second, -10.0, 10.0)
        val scale = typicalSize(values.toDoubleArray())
        // The marks in view count, as long as they are not on a pole's run-away.
        val shown = marks.filter { it.x in xr.first..xr.second && abs(it.y) <= 10 * scale }
        shown.forEach { values += it.y }
        values.sort()
        var lo = values[(values.size * 0.04).toInt()]
        var hi = values[((values.size - 1) * 0.96).toInt()]
        shown.forEach { lo = min(lo, it.y); hi = max(hi, it.y) }
        if (hi - lo < 1e-9) { lo -= 1.0; hi += 1.0 }
        val span = hi - lo
        if (lo > 0 && lo < span * 1.5) lo = 0.0
        if (hi < 0 && -hi < span * 1.5) hi = 0.0
        val pad = (hi - lo) * 0.15
        return PlotWindow(xr.first, xr.second, lo - pad, hi + pad)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Numeric points: zeros, extrema, intersections the exact search did not give.

    private fun numericPoints(functions: List<(Double) -> Double>, xr: Pair<Double, Double>): List<PlotMark> {
        val out = ArrayList<PlotMark>()
        val n = 1200
        val xs = DoubleArray(n + 1) { xr.first + xr.width() * it / n }
        for ((c, f) in functions.withIndex()) {
            val ys = DoubleArray(n + 1) { f(xs[it]) }
            val scale = typicalSize(ys)
            for (k in 0 until n) {
                val a = ys[k]
                val b = ys[k + 1]
                if (!a.isFinite() || !b.isFinite()) continue
                if (a == 0.0) out += PlotMark(PlotMark.Kind.ZERO, c, xs[k], 0.0, null, null)
                else if (a * b < 0) root(f, xs[k], xs[k + 1])?.let { out += PlotMark(PlotMark.Kind.ZERO, c, it, 0.0, null, null) }
            }
            for (k in 1 until n) {
                val l = ys[k - 1]
                val m = ys[k]
                val r = ys[k + 1]
                if (!l.isFinite() || !m.isFinite() || !r.isFinite()) continue
                val kind = when {
                    m < l && m <= r -> PlotMark.Kind.MINIMUM
                    m > l && m >= r -> PlotMark.Kind.MAXIMUM
                    else -> continue
                }
                // Beside a pole the values run away: no extremum there.
                if (abs(m) > 10 * scale || abs(l) > 10 * scale || abs(r) > 10 * scale) continue
                val xm = golden(f, xs[k - 1], xs[k + 1], kind == PlotMark.Kind.MINIMUM)
                val ym = f(xm)
                if (!ym.isFinite() || abs(ym) > 10 * scale) continue
                out += PlotMark(kind, c, xm, ym, null, null)
            }
            if (0.0 in xr.first..xr.second) f(0.0).takeIf { it.isFinite() }?.let { out += PlotMark(PlotMark.Kind.Y_INTERCEPT, c, 0.0, it, null, null) }
        }
        if (functions.size == 2) {
            val (f, g) = functions
            val d: (Double) -> Double = { t -> f(t) - g(t) }
            for (k in 0 until n) {
                val a = d(xs[k])
                val b = d(xs[k + 1])
                if (!a.isFinite() || !b.isFinite()) continue
                if (a == 0.0) out += PlotMark(PlotMark.Kind.INTERSECTION, 0, xs[k], f(xs[k]), null, null)
                else if (a * b < 0) root(d, xs[k], xs[k + 1])?.let { out += PlotMark(PlotMark.Kind.INTERSECTION, 0, it, f(it), null, null) }
            }
        }
        return out
    }

    /** How large a curve's values typically are: the 90th percentile of their sizes (a pole's run-away left out), at least 1. */
    private fun typicalSize(ys: DoubleArray): Double {
        val sizes = ys.filter { it.isFinite() }.map { abs(it) }.sorted()
        if (sizes.isEmpty()) return 1.0
        return max(1.0, sizes[((sizes.size - 1) * 0.9).toInt()])
    }

    /** A root between a and b, where f changes sign and is about 0 (not a pole, which changes sign too). */
    private fun root(f: (Double) -> Double, a0: Double, b0: Double): Double? {
        var a = a0
        var b = b0
        var fa = f(a)
        repeat(80) {
            val m = (a + b) / 2
            val fm = f(m)
            if (fm == 0.0) return m
            if (!fm.isFinite()) return null
            if ((fm < 0) == (fa < 0)) { a = m; fa = fm } else b = m
        }
        val m = (a + b) / 2
        val scale = max(1.0, max(abs(f(a0)), abs(f(b0))))
        return if (abs(f(m)) <= 1e-7 * scale) m else null
    }

    /** The x of the minimum (or maximum) of f between a and b, by golden-section search. */
    private fun golden(f: (Double) -> Double, a0: Double, b0: Double, minimum: Boolean): Double {
        val g = (kotlin.math.sqrt(5.0) - 1) / 2
        var a = a0
        var b = b0
        val s = if (minimum) 1.0 else -1.0
        var c = b - g * (b - a)
        var d = a + g * (b - a)
        repeat(60) {
            if (s * f(c) < s * f(d)) b = d else a = c
            c = b - g * (b - a)
            d = a + g * (b - a)
        }
        return (a + b) / 2
    }

    /** Exact points first; a numeric one only where no exact one of its kind lies near. */
    private fun merge(exact: Points, numeric: List<PlotMark>): List<PlotMark> {
        val out = ArrayList(exact.marks)
        fun near(a: PlotMark, b: PlotMark) = a.curve == b.curve && sameKind(a.kind, b.kind) && abs(a.x - b.x) <= 1e-5 * max(1.0, abs(a.x))
        for (m in numeric) if (out.none { near(it, m) }) out += m
        return out.distinctBy { Triple(it.curve, it.kind, Math.round(it.x * 1e7)) }
    }

    private fun sameKind(a: PlotMark.Kind, b: PlotMark.Kind) = a == b ||
        (a == PlotMark.Kind.MINIMUM || a == PlotMark.Kind.MAXIMUM) && (b == PlotMark.Kind.MINIMUM || b == PlotMark.Kind.MAXIMUM)

    // ---------------------------------------------------------------------------------------------------------------------
    // Sampling.

    /**
     * Curve f in [w] as polylines: [n] samples across, a piece ended where f is undefined, and where it jumps or has a pole
     * between two samples (its value halfway does not lie between theirs); near a pole the pieces run on to the edges.
     * Poles found go to [poles].
     */
    internal fun trace(f: (Double) -> Double, w: PlotWindow, n: Int, poles: MutableList<Double>): List<DoubleArray> {
        val pieces = ArrayList<DoubleArray>()
        val bound = 4 * max(abs(w.yMin), abs(w.yMax)) + 4 * w.height
        val xs = DoubleArray(n + 1) { w.xMin + w.width * it / n }
        val ys = DoubleArray(n + 1) { f(xs[it]) }
        var current = DoubleArrayBuilder()
        fun close() {
            if (current.size >= 2) pieces += current.toArray()
            current = DoubleArrayBuilder()
        }
        /** A pole between a and b: each side run up to it until it leaves the window; the piece after it begun. */
        fun splitAt(a: Double, b: Double): Boolean {
            val p = pole(f, a, b) ?: return false
            approach(f, a, p, bound) { ax, ay -> current.add(ax, clamp(ay, bound)) }
            close()
            poles += p
            approach(f, b, p, bound) { ax, ay -> current.add(ax, clamp(ay, bound)) }
            current.reverse()
            return true
        }
        for (k in 0..n) {
            val x = xs[k]
            val y = ys[k]
            if (!y.isFinite()) {
                // A single undefined sample between defined ones may sit right on a pole (1/x at 0).
                val before = k > 0 && ys[k - 1].isFinite()
                val after = k < n && ys[k + 1].isFinite()
                if (before && after && splitAt(xs[k - 1], xs[k + 1])) continue
                close()
                continue
            }
            if (k > 0 && ys[k - 1].isFinite() && breaks(f, xs[k - 1], ys[k - 1], x, y, w)) {
                if (!splitAt(xs[k - 1], x)) close()
            }
            current.add(x, clamp(y, bound))
        }
        close()
        return pieces
    }

    private fun clamp(y: Double, bound: Double) = y.coerceIn(-bound, bound)

    /** Whether the curve may not be drawn straight from (x0, y0) to (x1, y1): a jump, or a pole between. */
    private fun breaks(f: (Double) -> Double, x0: Double, y0: Double, x1: Double, y1: Double, w: PlotWindow): Boolean {
        val jump = abs(y1 - y0)
        if (jump < 0.25 * w.height) return false
        val ym = f((x0 + x1) / 2)
        if (!ym.isFinite()) return true
        // Halfway it is far outside the two, or not between them where they differ in sign: a pole or a jump.
        if (abs(ym) > max(abs(y0), abs(y1)) * 1.5 + w.height * 0.01) return true
        val between = (ym - y0) * (ym - y1) <= 0
        return !between && jump > 0.5 * w.height
    }

    /** The pole between a and b, if f grows without bound there; null for a plain jump. */
    private fun pole(f: (Double) -> Double, a0: Double, b0: Double): Double? {
        var a = a0
        var b = b0
        repeat(60) {
            val m = (a + b) / 2
            val l = f((a + m) / 2)
            val r = f((m + b) / 2)
            // Keep the half where the values are larger: the pole draws them up.
            if (!l.isFinite() || (r.isFinite() && abs(l) >= abs(r))) b = m else a = m
        }
        val p = (a + b) / 2
        val h = max(abs(p), 1.0) * 1e-9
        val near = max(abs(f(p - h)), abs(f(p + h)))
        val far = max(abs(f(a0)), abs(f(b0)))
        return if (!near.isFinite() || near > 1e3 * max(1.0, far)) p else null
    }

    /** Points from [from] toward [pole] while the curve stays within [bound], nearest the pole last. */
    private fun approach(f: (Double) -> Double, from: Double, pole: Double, bound: Double, add: (Double, Double) -> Unit) {
        var t = 0.5
        repeat(40) {
            val x = pole + (from - pole) * t
            val y = f(x)
            if (!y.isFinite()) return
            add(x, y)
            if (abs(y) > bound) return
            t *= 0.5
        }
    }

    /** An implicit curve F(x, y) = 0 in [w] by marching squares: line segments x₀, y₀, x₁, y₁. */
    internal fun contour(f: (Double, Double) -> Double, w: PlotWindow, pixels: Int): List<DoubleArray> {
        val nx = (pixels / 4).coerceIn(40, 220)
        val ny = max(20, (nx * w.height / w.width).toInt().coerceAtMost(220))
        val dx = w.width / nx
        val dy = w.height / ny
        val v = Array(nx + 1) { i -> DoubleArray(ny + 1) { j -> f(w.xMin + i * dx, w.yMin + j * dy) } }
        val out = ArrayList<DoubleArray>()
        for (i in 0 until nx) for (j in 0 until ny) {
            val a = v[i][j]
            val b = v[i + 1][j]
            val c = v[i + 1][j + 1]
            val d = v[i][j + 1]
            if (!a.isFinite() || !b.isFinite() || !c.isFinite() || !d.isFinite()) continue
            val x0 = w.xMin + i * dx
            val y0 = w.yMin + j * dy
            // Where F is 0 on each edge that changes sign: bottom, right, top, left.
            val cuts = ArrayList<Pair<Double, Double>>(4)
            if ((a < 0) != (b < 0)) cuts += (x0 + dx * a / (a - b)) to y0
            if ((b < 0) != (c < 0)) cuts += (x0 + dx) to (y0 + dy * b / (b - c))
            if ((d < 0) != (c < 0)) cuts += (x0 + dx * d / (d - c)) to (y0 + dy)
            if ((a < 0) != (d < 0)) cuts += x0 to (y0 + dy * a / (a - d))
            if (cuts.size < 2) continue
            // A sign change through a pole of F is no curve: F must be small where the cut is.
            val scale = max(max(abs(a), abs(b)), max(abs(c), abs(d)))
            fun real(p: Pair<Double, Double>) = abs(f(p.first, p.second)) <= scale * 0.75 + 1e-12
            if (cuts.size == 2) {
                if (real(cuts[0]) && real(cuts[1])) out += doubleArrayOf(cuts[0].first, cuts[0].second, cuts[1].first, cuts[1].second)
            } else {
                // A saddle: pair the cuts by the value in the middle.
                val mid = f(x0 + dx / 2, y0 + dy / 2)
                val pairs = if ((mid < 0) == (a < 0)) listOf(0 to 1, 2 to 3) else listOf(0 to 3, 1 to 2)
                for ((p, q) in pairs) out += doubleArrayOf(cuts[p].first, cuts[p].second, cuts[q].first, cuts[q].second)
            }
        }
        return out
    }

    /**
     * [graph] with its parameters at [values] (exact numbers, so marks stay exact: a = 1/2, not 0.5000001); with [keep] the
     * list of parameters stays on the graph, for the sliders.
     */
    fun at(graph: Outcome.Graph, values: Map<String, Double>, keep: Boolean = true): Outcome.Graph = with(graph) {
        fun put(e: Expr): Expr {
            var out = e
            for ((name, v) in values) out = out.substitute(Expr.Sym(name), num(Rational.near(v) ?: Rational.of(Math.round(v * 1000)) / Rational.of(1000)))
            return out
        }
        Outcome.Graph(variable, functions.map(::put), implicit?.let(::put), if (keep) parameters else parameters - values.keys, region)
    }

    /**
     * A curve (x(t), y(t)): t over [0, 2π] where its parts are trigonometric (a circle, a rose, a polar curve), over [−10, 10]
     * otherwise; split where it is undefined or jumps; a unit as long on both axes.
     */
    private fun prepareCurve(curve: Pair<Expr, Expr>, t: String): PreparedPlot {
        val fx = compile(curve.first, t)
        val fy = compile(curve.second, t)
        val trig = period(listOf(curve.first, curve.second), Expr.Sym(t)) != null || TrigForms.has(curve.first) || TrigForms.has(curve.second)
        val (a, b) = if (trig) 0.0 to 2 * Math.PI else -10.0 to 10.0
        val n = 2400
        val pieces = ArrayList<DoubleArray>()
        var piece = DoubleArrayBuilder()
        var lastX = Double.NaN
        var lastY = Double.NaN
        var lo = Double.POSITIVE_INFINITY
        var hi = Double.NEGATIVE_INFINITY
        var bottom = Double.POSITIVE_INFINITY
        var top = Double.NEGATIVE_INFINITY
        for (k in 0..n) {
            val tt = a + (b - a) * k / n
            val x = fx(tt)
            val y = fy(tt)
            val jump = lastX.isFinite() && (abs(x - lastX) > 1e3 || abs(y - lastY) > 1e3)
            if (!x.isFinite() || !y.isFinite() || jump) {
                if (piece.size > 1) pieces += piece.toArray()
                piece = DoubleArrayBuilder()
                lastX = Double.NaN
                if (!x.isFinite() || !y.isFinite()) continue
            }
            piece.add(x, y)
            lastX = x
            lastY = y
            lo = min(lo, x); hi = max(hi, x); bottom = min(bottom, y); top = max(top, y)
        }
        if (piece.size > 1) pieces += piece.toArray()
        if (!lo.isFinite()) { lo = -10.0; hi = 10.0; bottom = -10.0; top = 10.0 }
        val padX = max(0.5, (hi - lo) * 0.1)
        val padY = max(0.5, (top - bottom) * 0.1)
        val window = PlotWindow(lo - padX, hi + padX, bottom - padY, top + padY)
        return PreparedPlot(t, window, emptyList(), emptyList(), false, true, emptyList(), null, null, pieces)
    }

    private fun prepareImplicit(e: Expr, region: Rel? = null): PreparedPlot {
        val f = compile2(e)
        // The curve's extent on a coarse grid; ±10 where none shows.
        var lo = Double.POSITIVE_INFINITY
        var hi = Double.NEGATIVE_INFINITY
        var bottom = Double.POSITIVE_INFINITY
        var top = Double.NEGATIVE_INFINITY
        val reach = 20.0
        val n = 160
        val step = 2 * reach / n
        for (i in 0 until n) for (j in 0 until n) {
            val x = -reach + i * step
            val y = -reach + j * step
            val a = f(x, y)
            val b = f(x + step, y)
            val c = f(x, y + step)
            if (!a.isFinite()) continue
            if ((b.isFinite() && (a < 0) != (b < 0)) || (c.isFinite() && (a < 0) != (c < 0))) {
                lo = min(lo, x); hi = max(hi, x + step); bottom = min(bottom, y); top = max(top, y + step)
            }
        }
        val window = if (lo.isFinite()) {
            val cx = (lo + hi) / 2
            val cy = (bottom + top) / 2
            val half = max(max(hi - lo, top - bottom) / 2 * 1.3, 1.0)
            PlotWindow(cx - half, cx + half, cy - half, cy + half)
        } else PlotWindow(-10.0, 10.0, -10.0, 10.0)
        return PreparedPlot("x", window, emptyList(), emptyList(), false, true, emptyList(), f, region)
    }
}

/** A growing list of x, y pairs without boxing. */
private class DoubleArrayBuilder {
    private var data = DoubleArray(64)
    var size = 0
        private set

    fun add(x: Double, y: Double) {
        if (2 * size + 2 > data.size) data = data.copyOf(data.size * 2)
        data[2 * size] = x
        data[2 * size + 1] = y
        size++
    }

    /** The points in the opposite order. */
    fun reverse() {
        for (k in 0 until size / 2) {
            val j = size - 1 - k
            val x = data[2 * k]; val y = data[2 * k + 1]
            data[2 * k] = data[2 * j]; data[2 * k + 1] = data[2 * j + 1]
            data[2 * j] = x; data[2 * j + 1] = y
        }
    }

    fun toArray(): DoubleArray = data.copyOf(2 * size)
}

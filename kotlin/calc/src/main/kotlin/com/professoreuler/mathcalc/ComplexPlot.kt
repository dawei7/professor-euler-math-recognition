package com.professoreuler.mathcalc

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * A complex function's domain colouring, `complexplot(f)`: the function compiled once to plain arithmetic on doubles
 * ([ComplexFunction], for the app to evaluate at every pixel), the square of the complex plane it is drawn on, and its zeros
 * and poles in that square with their orders, each checked by the argument principle: f's argument turns k times around a small
 * circle about a zero of order k and −k times about a pole of order k ([windings]). Exact zeros come from solving f's numerator
 * = 0 (each put back in f, in ball arithmetic) and exact poles from its Laurent series ([ComplexAnalysis.residues]); the others are
 * found by Newton's method from a grid of starting points (on f for zeros, on 1/f for poles) and given to the digits the circle
 * that checked them leaves certain. Powers that are not whole, roots and logarithms take their principal values.
 */
object ComplexPlot {
    /**
     * A zero or a pole: where it is ([at]: exact where [exact], else its parts rounded to [decimals] decimals, the digits that are
     * certain: the point lies within one unit of the last), the same as doubles ([re], [im]) for drawing, and its [order]. Show a
     * numeric one with ≈ and its decimals ([text], [latex]).
     */
    class Point(val at: Expr, val re: Double, val im: Double, val order: Int, val exact: Boolean, val decimals: Int? = null)

    /** A numeric point's value with its certain decimals, `0.18123244 - 1.0839541i` (trailing zeros kept); an exact one's [Point.at]. */
    fun text(p: Point): String = write(p, latex = false)

    fun latex(p: Point): String = write(p, latex = true)

    private fun write(p: Point, latex: Boolean): String {
        val d = p.decimals ?: return Format.expr(p.at, Style.PLAIN, latex)
        val (re, im) = Complex.parts(p.at)?.let { (a, b) -> rational(a) to (b?.let { rational(it) } ?: Rational.ZERO) } ?: return Format.expr(p.at, Style.PLAIN, latex)
        fun plain(r: Rational) = BigDecimal(r.num).divide(BigDecimal(r.den), d, RoundingMode.HALF_EVEN).toPlainString()
        val a = re ?: return Format.expr(p.at, Style.PLAIN, latex)
        val b = im ?: return Format.expr(p.at, Style.PLAIN, latex)
        val i = if (latex) "\\,i" else "i"
        return when {
            b.signum == 0 -> plain(a)
            a.signum == 0 -> plain(b) + i
            b.signum < 0 -> plain(a) + " - " + plain(-b) + i
            else -> plain(a) + " + " + plain(b) + i
        }
    }

    /**
     * The square drawn: centre [center] (a complex number) and half its side [halfWidth], so Re z runs over
     * [Re c − r, Re c + r] and Im z over [Im c − r, Im c + r]; the same as doubles. [given] where the call asked for it; else it is
     * the default: centred at 0 with r = 2, or the first of 5, 10, 20, 50, 100 that holds every exact zero and pole with a fifth
     * to spare.
     */
    class Region(val center: Expr, val halfWidth: Expr, val centerRe: Double, val centerIm: Double, val half: Double, val given: Boolean) {
        fun contains(re: Double, im: Double): Boolean =
            abs(re - centerRe) <= half * (1 + 1e-12) && abs(im - centerIm) <= half * (1 + 1e-12)
    }

    /** f of [variable] drawn on [region], [compiled] for evaluating; its [zeros] and [poles] found in the region (each checked). */
    class Plot(val variable: String, val function: Expr, val region: Region, val compiled: ComplexFunction, val zeros: List<Point>,
        val poles: List<Point>)

    /** The default square's half side, and those it grows to. */
    private val SIZES = listOf(2.0, 5.0, 10.0, 20.0, 50.0, 100.0)

    /** At most this many zeros and poles are listed. */
    const val MOST_POINTS = 60

    /** [f] of [z] on the square [center] ± [halfWidth] (the default where null). */
    fun plot(f0: Expr, z: Expr.Sym, center: Expr?, halfWidth: Expr?): Plot {
        val f = Calculus.tidy(f0)
        if (f.symbols().any { it != z.name }) throw CalcException(Failure.SEVERAL_UNKNOWNS)
        val compiled = ComplexFunction.compile(f, z.name)
        val solved = solveZeros(f, z)
        val exactZeros = solved?.let { s -> (s.values + s.complex).distinct().mapNotNull { checkedZero(f, z, it) } }.orEmpty()
        val exactPoles = exactPoles(f, z)
        // Only a finite set of exact points widens the default square (sin z = 0's values are one period of infinitely many).
        val region = region(center, halfWidth, (if (solved?.periodic?.isNotEmpty() == true) emptyList() else exactZeros) + exactPoles)
        val zeros = ArrayList<Point>()
        val poles = ArrayList<Point>()
        zeros += exactZeros.filter { region.contains(it.re, it.im) }
        solved?.let { zeros += periodicZeros(f, z, it, region).filter { p -> zeros.none { q -> q.at == p.at } } }
        poles += exactPoles.filter { region.contains(it.re, it.im) }
        // The others found numerically; one where an exact point stands is that point.
        val exact = zeros + poles
        val found = search(compiled, ComplexFunction.compileOrNull(Calculus.tidy(Calculus.derivative(f, z)), z.name), region)
            .filter { c -> exact.none { hypot(it.re - c.re, it.im - c.im) <= 1e-6 * max(1.0, hypot(c.re, c.im)) } }
        for (c in found) {
            Budget.check()
            if (zeros.size + poles.size >= MOST_POINTS) break
            val nearest = (found.filter { it !== c }.map { hypot(it.re - c.re, it.im - c.im) } +
                exact.map { hypot(it.re - c.re, it.im - c.im) }).minOrNull() ?: Double.POSITIVE_INFINITY
            val (w, radius) = checkedWinding(compiled, c.re, c.im, nearest, region.half) ?: continue
            if (c.zero && w <= 0 || !c.zero && w >= 0) continue
            val point = decimalPoint(c.re, c.im, radius, abs(w))
            if (!region.contains(point.re, point.im)) continue
            if (c.zero) zeros += point else poles += point
        }
        val order = compareBy<Point>({ it.re }, { it.im })
        return Plot(z.name, f, region, compiled, zeros.take(MOST_POINTS).sortedWith(order), poles.take(MOST_POINTS).sortedWith(order))
    }

    private class Candidate(val re: Double, val im: Double, val exact: Expr?, val zero: Boolean)

    // ---------------------------------------------------------------------------------------------------------------------
    // The square.

    private fun region(center: Expr?, halfWidth: Expr?, exact: List<Point>): Region {
        if (center != null && halfWidth != null) {
            val (cr, ci) = complexValue(center) ?: throw CalcException(Failure.ARGUMENTS)
            val r = Numeric.value(halfWidth)?.takeIf { !halfWidth.hasI() && it > 0 && it.isFinite() } ?: throw CalcException(Failure.ARGUMENTS)
            if (r > 1e6 || r < 1e-6 || abs(cr) > 1e6 || abs(ci) > 1e6) throw CalcException(Failure.ARGUMENTS)
            return Region(center, halfWidth, cr, ci, r, given = true)
        }
        val reach = exact.maxOfOrNull { max(abs(it.re), abs(it.im)) } ?: 0.0
        val half = SIZES.firstOrNull { reach * 1.2 <= it } ?: SIZES.last()
        return Region(Expr.ZERO, num(half.toLong()), 0.0, 0.0, half, given = false)
    }

    private fun complexValue(e: Expr): Pair<Double, Double>? {
        if (!e.isConstant()) return null
        if (!e.hasI()) return Numeric.value(e)?.let { it to 0.0 }
        val normal = Complex.normal(Calculus.tidy(e)) ?: return Numeric.complex(e)
        val re = Numeric.value(Calculus.tidy(normal.first)) ?: return null
        val im = Numeric.value(Calculus.tidy(normal.second)) ?: return null
        return re to im
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Exact zeros and poles.

    /** f's numerator and denominator, its fractions on one bar and cancelled. */
    private fun fraction(f: Expr): Pair<Expr, Expr> {
        val s = try { Algebra.simplify(f) } catch (e: CalcException) { f }
        val factors = if (s is Expr.Mul) s.factors else listOf(s)
        val top = ArrayList<Expr>()
        val bottom = ArrayList<Expr>()
        for (x in factors) {
            val r = (x as? Expr.Pow)?.let { rational(it.exp) }
            if (x is Expr.Pow && r != null && r.signum < 0) bottom += pow(x.base, num(-r)) else top += x
        }
        return mul(top) to mul(bottom)
    }

    /** f's numerator = 0 solved exactly (in the complex numbers); null where that is not done exactly. */
    private fun solveZeros(f: Expr, z: Expr.Sym): Solutions? = try {
        val (top, _) = fraction(f)
        if (!top.has(z)) null else Domain.complex { Solve.equation(top, Expr.ZERO, z) }.takeIf { !it.numeric && !it.many && !it.always }
    } catch (e: CalcException) {
        if (e.failure == Failure.TOO_LONG) throw e
        null
    } catch (e: ArithmeticException) {
        null
    }

    /**
     * a, where f(a) = 0 in ball arithmetic, with its order: the first derivative that is not 0 there. Null where it is no zero
     * (a cancelled factor's) or its order is not found.
     */
    private fun checkedZero(f: Expr, z: Expr.Sym, a0: Expr): Point? = try {
        val a = Calculus.tidy(a0)
        val (re, im) = complexValue(a) ?: return null
        Domain.complex {
            if (Precise.nearZero(Calculus.tidy(f.substitute(z, a))) != true) return@complex null
            var d = f
            var order: Int? = null
            for (m in 1..12) {
                d = Calculus.tidy(Calculus.derivative(d, z))
                val v = Calculus.tidy(d.substitute(z, a))
                if (Precise.nearZero(v) == false) { order = m; break }
            }
            order?.let { Point(a, re, im, it, true) }
        }
    } catch (e: CalcException) {
        if (e.failure == Failure.TOO_LONG) throw e
        null
    } catch (e: ArithmeticException) {
        null
    }

    /** sin z = 0's z = kπ, and other families of zeros: each k that puts one in the square. */
    private fun periodicZeros(f: Expr, z: Expr.Sym, s: Solutions, region: Region): List<Point> {
        val out = ArrayList<Point>()
        for (family in s.periodic) {
            for (k in -200..200) {
                Budget.check()
                val a = try { Calculus.tidy(family.substitute(K, num(k.toLong()))) } catch (e: CalcException) { continue }
                val (re, im) = complexValue(a) ?: continue
                if (!region.contains(re, im)) continue
                checkedZero(f, z, a)?.let { p -> if (out.none { it.at == p.at }) out += p }
                if (out.size >= MOST_POINTS) return out
            }
        }
        return out
    }

    private fun exactPoles(f: Expr, z: Expr.Sym): List<Point> = try {
        val poles = Domain.complex { ComplexAnalysis.residues(f, z) } ?: emptyList()
        poles.mapNotNull { p -> complexValue(p.at)?.let { (re, im) -> Point(Calculus.tidy(p.at), re, im, p.order, true) } }
    } catch (e: CalcException) {
        if (e.failure == Failure.TOO_LONG) throw e
        emptyList()
    } catch (e: ArithmeticException) {
        emptyList()
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Found numerically.

    /** Newton's method from a grid of starting points in the square: z − f/f′ for zeros, z + f/f′ (Newton's on 1/f) for poles. */
    private fun search(f: ComplexFunction, df: ComplexFunction?, region: Region): List<Candidate> {
        val out = ArrayList<Candidate>()
        val ef = f.evaluator()
        val ed = df?.evaluator()
        val n = 11
        for (zero in listOf(true, false)) {
            for (a in 0 until n) for (b in 0 until n) {
                Budget.check()
                var x = region.centerRe + region.half * (2.0 * a / (n - 1) - 1)
                var y = region.centerIm + region.half * (2.0 * b / (n - 1) - 1)
                var converged = false
                var last = Double.POSITIVE_INFINITY
                for (step in 0 until 80) {
                    if (!ef.at(x, y)) break
                    val fr = ef.re
                    val fi = ef.im
                    // f′ from the compiled derivative, else by a central difference.
                    val dr: Double
                    val di: Double
                    if (ed != null && ed.at(x, y)) {
                        dr = ed.re
                        di = ed.im
                    } else {
                        val h = 1e-6 * max(1.0, hypot(x, y))
                        if (!ef.at(x + h, y)) break
                        val pr = ef.re
                        val pi = ef.im
                        if (!ef.at(x - h, y)) break
                        dr = (pr - ef.re) / (2 * h)
                        di = (pi - ef.im) / (2 * h)
                    }
                    val d2 = dr * dr + di * di
                    if (d2 == 0.0 || !d2.isFinite()) break
                    // f / f′
                    val qr = (fr * dr + fi * di) / d2
                    val qi = (fi * dr - fr * di) / d2
                    val sx = if (zero) -qr else qr
                    val sy = if (zero) -qi else qi
                    x += sx
                    y += sy
                    if (!x.isFinite() || !y.isFinite() || hypot(x - region.centerRe, y - region.centerIm) > region.half * 4) break
                    last = hypot(sx, sy)
                    if (last <= 1e-14 * max(1.0, hypot(x, y))) { converged = true; break }
                }
                // Near a pole f is known to fewer digits: an iteration that stands still within 10⁻⁸ is a candidate as well.
                if (!converged && last <= 1e-8 * max(1.0, hypot(x, y))) converged = true
                if (!converged || !region.contains(x, y)) continue
                if (out.none { it.zero == zero && hypot(it.re - x, it.im - y) <= 1e-7 * max(1.0, hypot(x, y)) }) out += Candidate(x, y, null, zero)
                if (out.size > MOST_POINTS * 2) return out
            }
        }
        return out
    }

    /**
     * The winding number of f around circles about (x, y), and the radius it was taken at: the same at radius ρ and 4ρ (each well
     * inside the distance to the [nearest] other point) and not 0; the smallest such ρ of 10⁻⁹, 10⁻⁷, 10⁻⁵ and 10⁻³ times the
     * point's size. Null where none agrees.
     */
    private fun checkedWinding(f: ComplexFunction, x: Double, y: Double, nearest: Double, half: Double): Pair<Int, Double>? {
        val scale = max(1.0, max(hypot(x, y), half * 1e-3))
        for (e in listOf(1e-9, 1e-7, 1e-5, 1e-3)) {
            val rho = e * scale
            if (4 * rho > 0.3 * nearest) break
            val a = windings(f, x, y, rho) ?: continue
            val b = windings(f, x, y, 4 * rho) ?: continue
            if (a == b && a != 0) return a to 4 * rho
        }
        return null
    }

    /**
     * How often f(z) turns around 0 while z goes once around the circle of radius [rho] about (x, y), counterclockwise: the
     * zeros inside less the poles, each by its order. Its steps are taken fine enough that f's argument turns less than half a
     * radian in each; null where f is not finite on the circle or the count is no whole number.
     */
    fun windings(f: ComplexFunction, x: Double, y: Double, rho: Double): Int? {
        val ev = f.evaluator()
        var n = 64
        while (n <= 16384) {
            Budget.check()
            var total = 0.0
            var fine = true
            if (!ev.at(x + rho, y)) return null
            var pr = ev.re
            var pi = ev.im
            if (pr == 0.0 && pi == 0.0) return null
            for (k in 1..n) {
                val t = 2 * PI * k / n
                if (!ev.at(x + rho * cos(t), y + rho * sin(t))) return null
                val cr = ev.re
                val ci = ev.im
                if (cr == 0.0 && ci == 0.0) return null
                // arg(f(zₖ)/f(zₖ₋₁)) = arg(f(zₖ)·conj(f(zₖ₋₁)))
                val d = atan2(ci * pr - cr * pi, cr * pr + ci * pi)
                if (abs(d) > 0.5) { fine = false; break }
                total += d
                pr = cr
                pi = ci
            }
            if (fine) {
                val w = total / (2 * PI)
                val r = w.roundToInt()
                return if (abs(w - r) < 0.05) r else null
            }
            n *= 4
        }
        return null
    }

    /** A point found numerically, its coordinates to the decimals the circle of [radius] about it leaves certain. */
    private fun decimalPoint(x: Double, y: Double, radius: Double, order: Int): Point {
        val decimals = max(0, floor(-log10(2 * radius)).toInt())
        fun part(v: Double): Expr {
            val b = BigDecimal(v).setScale(decimals, RoundingMode.HALF_EVEN)
            return num(Rational.of(b.unscaledValue(), java.math.BigInteger.TEN.pow(decimals)))
        }
        val re = part(x)
        val im = part(y)
        val at = Calculus.tidy(add(re, mul(im, Expr.I)))
        return Point(at, rational(re)!!.toDouble(), rational(im)!!.toDouble(), order, false, decimals)
    }
}

/**
 * A function of one complex variable compiled to plain arithmetic on doubles: a program of a few dozen steps, run by an
 * [Evaluator] (one for each thread: it keeps its own stack) at a point z = x + iy with no allocation, fast enough for every pixel
 * of a picture. On the Riemann sphere: 1/0 is ∞ and 1/∞ is 0, so a pole comes out [Evaluator.infinite]; what has no value (0/0,
 * ∞ − ∞, a function of ∞) comes out as neither finite nor infinite, and [Evaluator.at] is false. Powers that are not whole, √, ln
 * and the inverse functions take their principal values (ln z = ln|z| + i·arg z with arg in (−π, π]).
 */
class ComplexFunction private constructor(private val code: IntArray, private val constants: DoubleArray, private val depth: Int) {
    /** An evaluator of its own: not to be shared between threads. */
    fun evaluator(): Evaluator = Evaluator()

    /**
     * f at every point of an [n] × [n] grid over the square [region] (row by row from the top, Im z falling; in each row Re z
     * rising; the points at the pixels' centres), into [out] as (re, im) pairs: 2·n² doubles. NaN where f has no value, ±∞ at a pole.
     */
    fun grid(region: ComplexPlot.Region, n: Int, out: DoubleArray) {
        require(n > 0 && out.size >= 2 * n * n)
        val ev = Evaluator()
        val step = 2 * region.half / n
        for (row in 0 until n) {
            val y = region.centerIm + region.half - (row + 0.5) * step
            for (col in 0 until n) {
                val x = region.centerRe - region.half + (col + 0.5) * step
                val k = 2 * (row * n + col)
                if (ev.at(x, y)) {
                    out[k] = ev.re
                    out[k + 1] = ev.im
                } else if (ev.infinite) {
                    out[k] = Double.POSITIVE_INFINITY
                    out[k + 1] = Double.POSITIVE_INFINITY
                } else {
                    out[k] = Double.NaN
                    out[k + 1] = Double.NaN
                }
            }
        }
    }

    inner class Evaluator internal constructor() {
        private val sr = DoubleArray(depth + 1)
        private val si = DoubleArray(depth + 1)

        /** f's real and imaginary parts at the last [at]. */
        var re = 0.0
            private set
        var im = 0.0
            private set

        /** Whether f was ∞ there (a pole, or a value too large for a double). */
        var infinite = false
            private set

        /** f at x + iy: true where it is a finite number ([re], [im]); false at a pole ([infinite]) or where it has no value. */
        fun at(x: Double, y: Double): Boolean {
            var sp = 0
            var pc = 0
            while (pc < code.size) {
                val op = code[pc]
                val arg = code[pc + 1]
                pc += 2
                when (op) {
                    CONST -> { sr[sp] = constants[2 * arg]; si[sp] = constants[2 * arg + 1]; sp++ }
                    VAR -> { sr[sp] = x; si[sp] = y; sp++ }
                    ADD -> {
                        var a = sr[sp - arg]
                        var b = si[sp - arg]
                        for (j in sp - arg + 1 until sp) {
                            if (inf(a, b) || inf(sr[j], si[j])) {
                                a = if (inf(a, b) && inf(sr[j], si[j])) Double.NaN else Double.POSITIVE_INFINITY
                                b = if (a.isNaN()) Double.NaN else 0.0
                            } else {
                                a += sr[j]
                                b += si[j]
                            }
                        }
                        sp -= arg
                        sr[sp] = a; si[sp] = b; sp++
                    }
                    MUL -> {
                        var a = sr[sp - arg]
                        var b = si[sp - arg]
                        for (j in sp - arg + 1 until sp) {
                            val c = sr[j]
                            val d = si[j]
                            if (inf(a, b) || inf(c, d)) {
                                val zero = (a == 0.0 && b == 0.0) || (c == 0.0 && d == 0.0)
                                a = if (zero || a.isNaN() || b.isNaN() || c.isNaN() || d.isNaN()) Double.NaN else Double.POSITIVE_INFINITY
                                b = if (a.isNaN()) Double.NaN else 0.0
                            } else {
                                val r = a * c - b * d
                                b = a * d + b * c
                                a = r
                            }
                        }
                        sp -= arg
                        sr[sp] = a; si[sp] = b; sp++
                    }
                    POWI -> { powi(sp - 1, arg) }
                    RECIP -> recip(sp - 1)
                    POW -> {
                        // base^w = e^(w·ln base), base under w.
                        val wr = sr[sp - 1]
                        val wi = si[sp - 1]
                        sp--
                        val k = sp - 1
                        val a = sr[k]
                        val b = si[k]
                        if (a == 0.0 && b == 0.0) {
                            if (wr > 0) { sr[k] = 0.0; si[k] = 0.0 } else if (wr < 0) inf(k) else nan(k)
                        } else if (inf(a, b) || inf(wr, wi)) {
                            nan(k)
                        } else {
                            val lr = ln(hypot(a, b))
                            val li = atan2(b, a)
                            expAt(k, wr * lr - wi * li, wr * li + wi * lr)
                        }
                    }
                    else -> unary(op, sp - 1)
                }
            }
            re = sr[0]
            im = si[0]
            infinite = !re.isNaN() && !im.isNaN() && (re.isInfinite() || im.isInfinite())
            return re.isFinite() && im.isFinite()
        }

        private fun inf(a: Double, b: Double) = a.isInfinite() || b.isInfinite()
        private fun inf(k: Int) { sr[k] = Double.POSITIVE_INFINITY; si[k] = 0.0 }
        private fun nan(k: Int) { sr[k] = Double.NaN; si[k] = Double.NaN }
        private fun bad(k: Int) = sr[k].isNaN() || si[k].isNaN()

        private fun recip(k: Int) {
            val a = sr[k]
            val b = si[k]
            when {
                bad(k) -> nan(k)
                inf(a, b) -> { sr[k] = 0.0; si[k] = 0.0 }
                a == 0.0 && b == 0.0 -> inf(k)
                abs(a) >= abs(b) -> {
                    val r = b / a
                    val d = a + b * r
                    sr[k] = 1 / d
                    si[k] = -r / d
                }
                else -> {
                    val r = a / b
                    val d = a * r + b
                    sr[k] = r / d
                    si[k] = -1 / d
                }
            }
        }

        private fun powi(k: Int, n: Int) {
            if (bad(k)) return
            if (inf(sr[k], si[k])) {
                if (n > 0) inf(k) else if (n < 0) { sr[k] = 0.0; si[k] = 0.0 } else nan(k)
                return
            }
            var e = abs(n)
            var br = sr[k]
            var bi = si[k]
            var rr = 1.0
            var ri = 0.0
            while (e > 0) {
                if (e and 1 == 1) {
                    val t = rr * br - ri * bi
                    ri = rr * bi + ri * br
                    rr = t
                }
                e = e shr 1
                if (e > 0) {
                    val t = br * br - bi * bi
                    bi = 2 * br * bi
                    br = t
                }
            }
            sr[k] = rr
            si[k] = ri
            if (n < 0) recip(k)
        }

        private fun expAt(k: Int, a: Double, b: Double) {
            if (a > 709.0) { inf(k); return }
            val m = exp(a)
            sr[k] = m * cos(b)
            si[k] = m * sin(b)
        }

        private fun lnAt(k: Int) {
            val a = sr[k]
            val b = si[k]
            if (a == 0.0 && b == 0.0) { inf(k); return }
            sr[k] = ln(hypot(a, b))
            si[k] = atan2(b, a)
        }

        private fun sqrtAt(k: Int) {
            val a = sr[k]
            val b = si[k]
            if (a == 0.0 && b == 0.0) return
            val r = hypot(a, b)
            val t = sqrt((abs(a) + r) / 2)
            if (a >= 0) {
                sr[k] = t
                si[k] = b / (2 * t)
            } else {
                sr[k] = abs(b) / (2 * t)
                si[k] = if (b < 0) -t else t
            }
        }

        private fun sinAt(k: Int) {
            val a = sr[k]
            val b = si[k]
            sr[k] = sin(a) * cosh(b)
            si[k] = cos(a) * sinh(b)
        }

        private fun cosAt(k: Int) {
            val a = sr[k]
            val b = si[k]
            sr[k] = cos(a) * cosh(b)
            si[k] = -sin(a) * sinh(b)
        }

        /** tan z = sin z / cos z (accurate near its poles, where cos a is), and ±i far from the real axis. */
        private fun tanAt(k: Int) {
            val a = sr[k]
            val b = si[k]
            if (abs(b) > 20) { sr[k] = 0.0; si[k] = if (b > 0) 1.0 else -1.0; return }
            // sin z = sin a cosh b + i cos a sinh b, cos z = cos a cosh b − i sin a sinh b
            val p = sin(a) * cosh(b)
            val q = cos(a) * sinh(b)
            val c = cos(a) * cosh(b)
            val d = -sin(a) * sinh(b)
            val n = c * c + d * d
            if (n == 0.0) { inf(k); return }
            sr[k] = (p * c + q * d) / n
            si[k] = (q * c - p * d) / n
        }

        private fun mulI(k: Int, sign: Double) {
            // ±i·z
            val a = sr[k]
            sr[k] = -sign * si[k]
            si[k] = sign * a
        }

        /** asin z = −i ln(iz + √(1 − z²)). */
        private fun asinAt(k: Int) {
            val a = sr[k]
            val b = si[k]
            // 1 − z²
            sr[k] = 1 - (a * a - b * b)
            si[k] = -2 * a * b
            sqrtAt(k)
            sr[k] += -b
            si[k] += a
            lnAt(k)
            if (bad(k) || inf(sr[k], si[k])) { nan(k); return }
            mulI(k, -1.0)
        }

        /** atan z = (i/2)(ln(1 − iz) − ln(1 + iz)). */
        private fun atanAt(k: Int) {
            val a = sr[k]
            val b = si[k]
            if (a == 0.0 && abs(b) == 1.0) { inf(k); return }
            // ln(1 − iz) with 1 − iz = (1 + b) − ia; ln(1 + iz) with 1 + iz = (1 − b) + ia.
            val l1r = ln(hypot(1 + b, -a))
            val l1i = atan2(-a, 1 + b)
            val l2r = ln(hypot(1 - b, a))
            val l2i = atan2(a, 1 - b)
            val dr = l1r - l2r
            val di = l1i - l2i
            // (i/2)(dr + i di) = −di/2 + i dr/2
            sr[k] = -di / 2
            si[k] = dr / 2
        }

        private fun unary(op: Int, k: Int) {
            if (bad(k)) return
            if (inf(sr[k], si[k])) {
                // |∞| = ∞; the other functions have no value at ∞.
                if (op == ABS) inf(k) else nan(k)
                return
            }
            when (op) {
                EXP -> expAt(k, sr[k], si[k])
                LN -> lnAt(k)
                SQRT -> sqrtAt(k)
                SIN -> sinAt(k)
                COS -> cosAt(k)
                TAN -> tanAt(k)
                COT -> { tanAt(k); recip(k) }
                SEC -> { cosAt(k); recip(k) }
                CSC -> { sinAt(k); recip(k) }
                // sinh z = −i sin(iz), cosh z = cos(iz), tanh z = −i tan(iz).
                SINH -> { mulI(k, 1.0); sinAt(k); mulI(k, -1.0) }
                COSH -> { mulI(k, 1.0); cosAt(k) }
                TANH -> { mulI(k, 1.0); tanAt(k); if (!bad(k) && !inf(sr[k], si[k])) mulI(k, -1.0) }
                ASIN -> asinAt(k)
                // acos z = π/2 − asin z
                ACOS -> { asinAt(k); sr[k] = PI / 2 - sr[k]; si[k] = -si[k] }
                ATAN -> atanAt(k)
                // acot z = π/2 − atan z, as the real values (in (0, π)) have it.
                ACOT -> { atanAt(k); if (!bad(k) && !inf(sr[k], si[k])) { sr[k] = PI / 2 - sr[k]; si[k] = -si[k] } }
                ABS -> { sr[k] = hypot(sr[k], si[k]); si[k] = 0.0 }
                else -> nan(k)
            }
        }
    }

    companion object {
        private const val CONST = 0
        private const val VAR = 1
        private const val ADD = 2
        private const val MUL = 3
        private const val POWI = 4
        private const val RECIP = 5
        private const val POW = 6
        private const val EXP = 7
        private const val LN = 8
        private const val SQRT = 9
        private const val SIN = 10
        private const val COS = 11
        private const val TAN = 12
        private const val COT = 13
        private const val SEC = 14
        private const val CSC = 15
        private const val SINH = 16
        private const val COSH = 17
        private const val TANH = 18
        private const val ASIN = 19
        private const val ACOS = 20
        private const val ATAN = 21
        private const val ACOT = 22
        private const val ABS = 23

        /**
         * [e] as a function of [variable], or null where [compile] fails.
         */
        fun compileOrNull(e: Expr, variable: String): ComplexFunction? = try {
            compile(e, variable)
        } catch (x: CalcException) {
            if (x.failure == Failure.TOO_LONG) throw x
            null
        }

        /**
         * [e] as a function of [variable]: [Failure.NO_COMPLEX_VALUES] where it holds what has no complex values here (Γ, ⌊x⌋,
         * calculus not worked out), [Failure.SEVERAL_UNKNOWNS] for another letter.
         */
        fun compile(e: Expr, variable: String): ComplexFunction {
            val code = ArrayList<Int>()
            val constants = ArrayList<Double>()
            var depth = 0
            var most = 0
            fun emit(op: Int, arg: Int, change: Int) {
                code += op
                code += arg
                depth += change
                most = max(most, depth)
            }
            fun constant(re: Double, im: Double) {
                constants += re
                constants += im
                emit(CONST, constants.size / 2 - 1, 1)
            }
            fun walk(x: Expr) {
                Budget.check()
                when (x) {
                    is Expr.Num -> constant(x.value.toDouble(), 0.0)
                    is Expr.Real -> constant(x.value, 0.0)
                    is Expr.Sym -> if (x.name == variable) emit(VAR, 0, 1) else throw CalcException(Failure.SEVERAL_UNKNOWNS)
                    is Expr.Const -> when (x.kind) {
                        Constant.PI -> constant(PI, 0.0)
                        Constant.E -> constant(kotlin.math.E, 0.0)
                        Constant.I -> constant(0.0, 1.0)
                        Constant.INF -> throw CalcException(Failure.NO_COMPLEX_VALUES)
                    }
                    is Expr.Add -> { x.terms.forEach { walk(it) }; emit(ADD, x.terms.size, 1 - x.terms.size) }
                    is Expr.Mul -> { x.factors.forEach { walk(it) }; emit(MUL, x.factors.size, 1 - x.factors.size) }
                    is Expr.Pow -> {
                        val r = rational(x.exp)
                        when {
                            x.base == Expr.E -> { walk(x.exp); emit(EXP, 0, 0) }
                            r != null && r.isInteger && r.num.bitLength() < 31 -> { walk(x.base); emit(POWI, r.num.toInt(), 0) }
                            r != null && r == Rational.of(1) / Rational.of(2) -> { walk(x.base); emit(SQRT, 0, 0) }
                            r != null && r == Rational.of(-1) / Rational.of(2) -> { walk(x.base); emit(SQRT, 0, 0); emit(RECIP, 0, 0) }
                            else -> { walk(x.base); walk(x.exp); emit(POW, 0, -1) }
                        }
                    }
                    is Expr.Fn -> {
                        val op = when (x.f) {
                            Func.SIN -> SIN
                            Func.COS -> COS
                            Func.TAN -> TAN
                            Func.COT -> COT
                            Func.SEC -> SEC
                            Func.CSC -> CSC
                            Func.SINH -> SINH
                            Func.COSH -> COSH
                            Func.TANH -> TANH
                            Func.ASIN -> ASIN
                            Func.ACOS -> ACOS
                            Func.ATAN -> ATAN
                            Func.ACOT -> ACOT
                            Func.LN -> LN
                            Func.ABS -> ABS
                            Func.LOG -> -1
                            else -> throw CalcException(Failure.NO_COMPLEX_VALUES)
                        }
                        if (op == -1) {
                            // log_b x = ln x / ln b
                            walk(x.args[1]); emit(LN, 0, 0)
                            walk(x.args[0]); emit(LN, 0, 0); emit(RECIP, 0, 0)
                            emit(MUL, 2, -1)
                        } else {
                            walk(x.args[0])
                            emit(op, 0, 0)
                        }
                    }
                    is Expr.Op, is Expr.Mat -> throw CalcException(Failure.NO_COMPLEX_VALUES)
                }
            }
            walk(e)
            return ComplexFunction(code.toIntArray(), constants.toDoubleArray(), most)
        }
    }
}

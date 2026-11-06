package com.professoreuler.mathcalc

import kotlin.math.abs
import kotlin.math.max

/**
 * The solutions of an equation in one unknown. [values]: the real ones, exact where they can be, sorted. [periodic]: whole
 * families written with the whole number k (x = π/6 + 2kπ), [values] then holds those in one period. [complex]: those that
 * are not real. [always]: every value solves it (where both sides are defined). [rejected]: what squaring or cancelling
 * brought in that does not solve it. [excluded]: values where a side is not defined. [numeric]: some found only numerically.
 * [many]: more than are worth listing.
 */
class Solutions internal constructor(
    val variable: String,
    val values: List<Expr> = emptyList(),
    val periodic: List<Expr> = emptyList(),
    val complex: List<Expr> = emptyList(),
    val always: Boolean = false,
    val rejected: List<Expr> = emptyList(),
    val excluded: List<Expr> = emptyList(),
    val numeric: Boolean = false,
    val many: Boolean = false,
    /** Conditions on the other letters for a formula solved for one: a ≠ 0. */
    val conditions: List<Expr> = emptyList(),
    /** Angles in degrees, as the formula wrote its own: 30 for 30°, the families' step 360. */
    val degrees: Boolean = false,
    /**
     * More values solve it than [values] lists: a whole interval (|x| = x for every x ≥ 0), found where the equation holds at
     * points none of its solutions is near. Such a list is never shown; the solution set is taken as intervals instead.
     */
    internal val incomplete: Boolean = false,
) {
    /** The same solutions, marked as angles in degrees: sin(x°) = 1/2 gives x = 30 + 360k, shown as 30° + k·360°. */
    internal fun markedDegrees(): Solutions =
        Solutions(variable, values, periodic, complex, always, rejected, excluded, numeric, many, conditions, degrees = true)
}

/** An interval of the real line: null bounds are ∓∞. */
class Interval internal constructor(val lower: Expr?, val lowerClosed: Boolean, val upper: Expr?, val upperClosed: Boolean)

/** The solution of an inequality: a union of intervals, all of ℝ, or none. */
class IntervalSet internal constructor(val variable: String, val intervals: List<Interval>) {
    val all: Boolean get() = intervals.size == 1 && intervals[0].lower == null && intervals[0].upper == null
    val empty: Boolean get() = intervals.isEmpty()
}

/** The letter of a family of solutions: k ∈ ℤ. */
internal val K = Expr.Sym("k")

/**
 * Solving: equations in one unknown (exactly where a school method reaches, numerically otherwise), formulas for any of
 * their letters, inequalities as intervals, and systems. Every candidate is put back into the equation as written; one
 * that does not solve it is reported as rejected, never as a solution.
 */
object Solve {
    private const val MOST = 12
    private const val WINDOW = 1000.0

    // ---------------------------------------------------------------------------------------------------------------------
    // Equations in one unknown.

    fun equation(lhs: Expr, rhs: Expr, x: Expr.Sym): Solutions {
        val f = lhs - rhs
        if (!f.has(x)) {
            // A constant difference is 0 only where the balls cannot tell it from 0: x + tanh 20 = x + 1 has no solution.
            val zero = isZero(f) || (f.isConstant() && Numeric.value(f)?.let { abs(it) < 1e-12 } == true &&
                Precise.certainlyNonzero(f) != true)
            return if (zero) Solutions(x.name, always = true) else Solutions(x.name)
        }
        // Every value, where both sides are defined: (x+1)² = x² + 2x + 1, sin²x + cos²x = 1.
        if (f.symbols() == setOf(x.name) && (isZero(Algebra.expand(f)) || Verify.same(f, Expr.ZERO))) {
            return Solutions(x.name, always = true)
        }
        val found = Found()
        roots(f, x, found, depth = 0)
        val solutions = check(lhs, rhs, x, found)
        // Every solution listed is put back in (check); that none is lost is checked too, at points along the line: a case that
        // reduced to 0 = 0 (|x| = x for x ≥ 0, √(x²) = x) leaves no candidates for all the values it holds for.
        if (solutions.many || solutions.periodic.isNotEmpty() || solutions.always || !lostSome(f, x, solutions.values)) return solutions
        return Solutions(x.name, values = solutions.values, rejected = solutions.rejected, numeric = solutions.numeric, incomplete = true)
    }

    /**
     * Whether [f] is 0 (to the balls as well, not only to a double) at two neighbouring points of the line that none of [values]
     * is near: then the equation holds on more than the values found.
     */
    private fun lostSome(f: Expr, x: Expr.Sym, values: List<Expr>): Boolean {
        val known = values.mapNotNull { Numeric.value(it) }
        fun doubleZero(p: Rational): Boolean {
            val d = p.toDouble()
            if (known.any { abs(it - d) < 1e-6 }) return false
            val v = Numeric.eval(f, mapOf(x.name to d))
            return v.isFinite() && abs(v) <= 1e-12 * max(1.0, abs(d))
        }
        fun ballZero(p: Rational): Boolean = try { Precise.certainlyNonzero(f.substitute(x, num(p)), maxBits = 512) == false } catch (e: CalcException) {
            if (e.failure == Failure.TOO_LONG) throw e
            false
        }
        // Nearest 0 first, where a tiny value (erf x − 1 is 0 to a double from x ≈ 6) is still cheap to tell from 0; a few such
        // looks at most: on an interval of solutions every point is one, the first as well.
        var looks = 0
        for (j in (-24..24).sortedBy { kotlin.math.abs(it) }) {
            val p = Rational.of(java.math.BigInteger.valueOf(j * 1000L + 137), java.math.BigInteger.valueOf(2000))
            val q = p + Rational.of(java.math.BigInteger.ONE, java.math.BigInteger.valueOf(97))
            if (!doubleZero(p) || !doubleZero(q)) continue
            if (looks++ >= 4) return false
            if (ballZero(p) && ballZero(q)) return true
        }
        return false
    }

    /**
     * The solution set of lhs = rhs as intervals, where it holds on whole intervals ([Solutions.incomplete]): by the signs
     * between its points ([inequality] with =), each interval checked at points inside it and each gap between them at a point
     * of its own, in ball arithmetic. Null where that cannot be decided.
     */
    fun intervalSolutions(lhs: Expr, rhs: Expr, x: Expr.Sym): IntervalSet? {
        val set = inequality(lhs, Rel.EQ, rhs, x) ?: return null
        if (set.intervals.none { it.lower != it.upper }) return null
        val f = lhs - rhs
        fun zero(p: Rational): Boolean? = try { Precise.certainlyNonzero(f.substitute(x, num(p)), maxBits = 512)?.not() } catch (e: CalcException) {
            if (e.failure == Failure.TOO_LONG) throw e
            null
        }
        fun near(e: Expr?): Rational? = e?.let { Numeric.value(it) }?.let { Rational.near(it, 1_000_000, 1e-3) ?: Rational.of(Math.round(it * 1000)) / Rational.of(1000) }
        for (i in set.intervals) {
            val lo = i.lower?.let { Numeric.value(it) }
            val hi = i.upper?.let { Numeric.value(it) }
            if (lo != null && hi != null && lo == hi) {
                if (!solves(lhs, rhs, x, i.lower!!)) return null
                continue
            }
            // Three points inside, each exactly a solution.
            val a = lo ?: ((hi ?: 0.0) - 10)
            val b = hi ?: ((lo ?: 0.0) + 10)
            for (t in listOf(0.25, 0.5, 0.75)) {
                val p = near(Expr.Real(a + (b - a) * t)) ?: return null
                if (zero(p) != true) return null
            }
        }
        // Between the intervals (and beyond them) a point that is no solution.
        val bounds = set.intervals.flatMap { listOfNotNull(it.lower, it.upper) }.mapNotNull { Numeric.value(it) }.distinct().sorted()
        val gaps = ArrayList<Double>()
        if (bounds.isNotEmpty()) { gaps += bounds.first() - 1.37; gaps += bounds.last() + 1.37 }
        for ((p, q) in bounds.zipWithNext()) gaps += (p + q) / 2
        for (g in gaps) {
            val inside = set.intervals.any { i ->
                val lo = i.lower?.let { Numeric.value(it) }
                val hi = i.upper?.let { Numeric.value(it) }
                (lo == null || g > lo - 1e-12) && (hi == null || g < hi + 1e-12)
            }
            if (inside) continue
            val p = near(Expr.Real(g)) ?: return null
            val v = Numeric.eval(f, mapOf(x.name to p.toDouble()))
            if (v.isFinite() && zero(p) != false) return null
        }
        return set
    }

    /** Candidates while an equation is worked on. */
    private class Found {
        val exact = ArrayList<Expr>()
        val periodic = ArrayList<Expr>()
        val complex = ArrayList<Expr>()
        var numeric = false
        var many = false
    }

    /** Candidates for f = 0 into [out]; what does not solve the original is sorted out by [check]. */
    private fun roots(f0: Expr, x: Expr.Sym, out: Found, depth: Int) {
        if (depth > 6) return
        val f = normalizeExponentials(f0, x)
        // A factor that is never 0 changes nothing about where f is 0: −x·e^(−x) + e^(−x) is 0 where 1 − x is. Without this the
        // numbers alone found "zeros" all along the tail where e^(−x) is tiny.
        // (Looked for in the equation as given: normalizing the exponentials writes e^(−x) as (e^(−x))¹.)
        neverZeroFactor(f0, x)?.let { g ->
            val reduced = Calculus.tidy(f0 / g)
            if (reduced.has(x) && reduced != f0) {
                roots(reduced, x, out, depth + 1)
                return
            }
        }
        // A fraction: its numerator's roots (where the denominator is not 0, which check sees).
        val kernels = Algebra.Kernels()
        val fraction = Algebra.fraction(f, kernels)
        if (fraction != null) {
            val numerator = Algebra.toExpr(fraction.first, kernels)
            val coefficients = coefficientsIn(Algebra.expand(numerator), x)
            if (coefficients != null) {
                polynomialRoots(coefficients, x, out)
                return
            }
            // A polynomial in one kernel of x (eˣ, sin x, √x, ln x …): solved for the kernel as a letter, then each value for x.
            val p = fraction.first
            val xKernels = p.unknowns.filter { it != x.name && kernels.expr(it).has(x) }
            if (xKernels.size == 1 && x.name !in p.unknowns) {
                val k = xKernels[0]
                val coefficients = (0..p.degreeIn(k)).map { d ->
                    Algebra.toExpr(Polynomial(p.terms.filter { it.key.power(k) == d }.mapKeys { (m, _) -> Monomial(m.powers.filter { it.first != k }) }), kernels)
                }
                val values = Found()
                polynomialRoots(coefficients, Expr.Sym(k), values)
                for (v in values.exact) isolate(kernels.expr(k), v, x, out, depth + 1)
                if (values.numeric) out.numeric = true
                return
            }
            val kernel = singleKernel(numerator, x)
            if (kernel != null) {
                val u = Expr.Sym("\u0001u")
                val inU = substituteKernel(numerator, kernel, u)
                val c = coefficientsIn(Algebra.expand(inU), u)
                if (c != null && !inU.has(x)) {
                    val values = Found()
                    polynomialRoots(c, u, values)
                    for (v in values.exact) isolate(kernel, v, x, out, depth + 1)
                    if (values.numeric) out.numeric = true
                    return
                }
            }
        }
        // x once: undo what is done to it.
        if (occurrences(f, x) == 1) {
            isolate(f, Expr.ZERO, x, out, depth + 1)
            return
        }
        // Logarithms on one: ln a + ln b = c as ln(ab) = c.
        val combined = Algebra.combineLogs(f)
        if (combined != f && occurrences(combined, x) == 1) {
            isolate(combined, Expr.ZERO, x, out, depth + 1)
            return
        }
        // One logarithm or power of x against numbers: ln(x(x − 1)/6) = 0 as x(x − 1)/6 = 1, every root of it checked.
        outer(combined, x)?.let { (inner, value) -> roots(inner - value, x, out, depth + 1); return }
        // Equal powers of one base on both sides: aᵘ = aᵛ gives u = v.
        sameBase(f, x)?.let { (u, v) -> roots(u - v, x, out, depth + 1); return }
        // A root: alone on one side, then squared.
        radical(f, x)?.let { (r, rest) ->
            val n = rational((r as Expr.Pow).exp)!!.den.toInt()
            roots(r.base - pow(neg(rest), num(n.toLong())), x, out, depth + 1)
            return
        }
        // |A|: both cases.
        absolute(f)?.let { a ->
            roots(substituteExact(f, a, (a as Expr.Fn).args[0]), x, out, depth + 1)
            roots(substituteExact(f, a, neg(a.args[0])), x, out, depth + 1)
            return
        }
        // a·sin u + b·cos u + c as one sine: R·sin(u + φ) + c, R = √(a² + b²), which has x once.
        harmonic(f, x)?.let { g -> roots(g, x, out, depth + 1); return }
        numericRoots(f, x, out)
    }

    /**
     * [f] = a·sin u + b·cos u + c (a, b, c numbers, u with x in it) as R·sin(u + φ) + c with R = √(a² + b²) and φ the angle
     * of (a, b): cos x − sin x = 0 is √2·sin(x + 3π/4) = 0. Null where [f] is no such sum.
     */
    private fun harmonic(f: Expr, x: Expr.Sym): Expr? {
        val kernels = Algebra.Kernels()
        val p = Algebra.polynomial(Algebra.expand(f), kernels) ?: return null
        val names = p.unknowns.toList()
        if (names.size != 2) return null
        val ks = names.map { kernels.expr(it) }
        val sinAt = ks.indexOfFirst { it is Expr.Fn && it.f == Func.SIN }
        val cosAt = ks.indexOfFirst { it is Expr.Fn && it.f == Func.COS }
        if (sinAt < 0 || cosAt < 0) return null
        val u = (ks[sinAt] as Expr.Fn).args[0]
        if (u != (ks[cosAt] as Expr.Fn).args[0] || !u.has(x)) return null
        var a = Rational.ZERO
        var b = Rational.ZERO
        var c = Rational.ZERO
        for ((m, k) in p.terms) {
            when {
                m == Monomial.ONE -> c = k
                m.powers.size == 1 && m.power(names[sinAt]) == 1 -> a = k
                m.powers.size == 1 && m.power(names[cosAt]) == 1 -> b = k
                else -> return null
            }
        }
        if (a.signum == 0 || b.signum == 0) return null
        val r = sqrt(num(a * a + b * b))
        val angle = fn(Func.ATAN, num(b / a))
        val phi = if (a.signum > 0) angle else add(angle, Expr.PI)
        return add(mul(r, fn(Func.SIN, add(u, phi))), num(c))
    }

    /** An exponential of x (a positive base to a power with x) that is a factor of every term of [f]: it is never 0. */
    private fun neverZeroFactor(f: Expr, x: Expr.Sym): Expr? {
        val terms = if (f is Expr.Add) f.terms else listOf(f)
        fun factors(t: Expr) = if (t is Expr.Mul) t.factors else listOf(t)
        fun exponential(e: Expr) = e is Expr.Pow && e.exp.has(x) && !e.base.has(x) &&
            (e.base == Expr.E || rational(e.base)?.let { it.signum > 0 && it != Rational.ONE } == true)
        val candidates = factors(terms[0]).filter(::exponential)
        return candidates.firstOrNull { c -> terms.all { t -> c in factors(t) } }
    }

    /** a^(2x) as (a^x)², so a polynomial in aˣ shows: e^(2x) − 3eˣ + 2 is u² − 3u + 2. */
    private fun normalizeExponentials(f: Expr, x: Expr.Sym): Expr {
        val powers = ArrayList<Expr.Pow>()
        fun walk(e: Expr) {
            if (e is Expr.Pow && e.exp.has(x) && !e.base.has(x)) powers += e
            when (e) {
                is Expr.Add -> e.terms.forEach(::walk)
                is Expr.Mul -> e.factors.forEach(::walk)
                is Expr.Pow -> { walk(e.base); walk(e.exp) }
                is Expr.Fn -> e.args.forEach(::walk)
                else -> Unit
            }
        }
        walk(f)
        if (powers.size < 2) return f
        var out = f
        for (base in powers.map { it.base }.distinct()) {
            val same = powers.filter { it.base == base }
            // The exponents as rational multiples of the one with the smallest coefficient.
            val unit = same.map { splitCoefficient(it.exp) }.minByOrNull { abs(numValue(it.first)) } ?: continue
            val g = unit.second
            for (p in same) {
                val (c, rest) = splitCoefficient(p.exp)
                if (rest != g) continue
                val ratio = rational(c)?.let { cc -> rational(unit.first)?.let { cc / it } } ?: continue
                if (!ratio.isInteger) continue
                out = substituteExact(out, p, Expr.Pow(pow(base, mul(unit.first, g)), num(ratio)))
            }
        }
        return out
    }

    /** The one subexpression containing x that x occurs only in (sin x in 2sin²x − sin x − 1), if there is exactly one. */
    private fun singleKernel(e: Expr, x: Expr.Sym): Expr? {
        val kernels = LinkedHashSet<Expr>()
        var bare = false
        fun walk(t: Expr) {
            when {
                !t.has(x) -> Unit
                t == x -> bare = true
                t is Expr.Add -> t.terms.forEach(::walk)
                t is Expr.Mul -> t.factors.forEach(::walk)
                t is Expr.Pow && integer(t.exp) != null && t.exp.isConstant() -> walk(t.base)
                else -> kernels += t
            }
        }
        walk(e)
        return if (!bare && kernels.size == 1) kernels.first() else null
    }

    private fun substituteKernel(e: Expr, kernel: Expr, u: Expr.Sym): Expr = substituteExact(e, kernel, u)

    /** [e] with every occurrence of [target] replaced by [value]. */
    internal fun substituteExact(e: Expr, target: Expr, value: Expr): Expr {
        if (e == target) return value
        return e.mapChildren { substituteExact(it, target, value) }
    }

    /** How often x occurs in [e]. */
    private fun occurrences(e: Expr, x: Expr.Sym): Int = when (e) {
        is Expr.Sym -> if (e == x) 1 else 0
        is Expr.Add -> e.terms.sumOf { occurrences(it, x) }
        is Expr.Mul -> e.factors.sumOf { occurrences(it, x) }
        is Expr.Pow -> occurrences(e.base, x) + occurrences(e.exp, x)
        is Expr.Fn -> e.args.sumOf { occurrences(it, x) }
        else -> 0
    }

    /** f as a polynomial in x with coefficients free of x, lowest degree first; null where it is not one. */
    internal fun coefficientsIn(e: Expr, x: Expr.Sym): List<Expr>? {
        val terms = if (e is Expr.Add) e.terms else listOf(e)
        val byDegree = HashMap<Int, MutableList<Expr>>()
        for (t in terms) {
            val factors = if (t is Expr.Mul) t.factors else listOf(t)
            var degree = 0
            val rest = ArrayList<Expr>()
            for (g in factors) {
                when {
                    g == x -> degree += 1
                    g is Expr.Pow && g.base == x && integer(g.exp)?.let { it.signum() > 0 && it.bitLength() < 16 } == true -> degree += integer(g.exp)!!.toInt()
                    g.has(x) -> return null
                    else -> rest += g
                }
            }
            byDegree.getOrPut(degree) { mutableListOf() } += mul(rest)
        }
        val n = byDegree.keys.maxOrNull() ?: return listOf(Expr.ZERO)
        if (n > 200) return null
        return List(n + 1) { k -> byDegree[k]?.let { add(it) } ?: Expr.ZERO }
    }

    /** The roots of Σ cₖ xᵏ: factors over the rationals where the coefficients are numbers, formulas up to degree two. */
    private fun polynomialRoots(c0: List<Expr>, x: Expr.Sym, out: Found) {
        val c = c0.dropLastWhile { isZero(it) }
        if (c.size <= 1) return
        val rationalCoefficients = c.map { rational(it) }
        if (rationalCoefficients.all { it != null }) {
            val p = Polynomial(c.withIndex().filter { !isZero(it.value) }.associate { (k, v) ->
                (if (k == 0) Monomial.ONE else Monomial(listOf(x.name to k))) to rational(v)!!
            })
            val product = Factor.of(p)
            for ((f, _) in product.factors) {
                val fc = coefficientsIn(Algebra.toExpr(f, Algebra.Kernels()), x) ?: continue
                when (fc.size - 1) {
                    1, 2 -> formula(fc, out)
                    // An irreducible cubic or quartic: its roots exactly where they have a form worth reading.
                    3, 4 -> {
                        val exact = fc.map { rational(it) }.takeIf { r -> r.all { it != null } }?.let { r -> Roots.exact(r.map { it!! }) }
                        if (exact != null) {
                            out.exact += exact.first
                            out.complex += exact.second
                        } else {
                            numericPolynomial(fc, out)
                        }
                    }
                    else -> numericPolynomial(fc, out)
                }
            }
            return
        }
        when (c.size - 1) {
            1, 2 -> formula(c, out)
            else -> numericPolynomial(c, out)
        }
    }

    /** a x + b = 0 and a x² + b x + c = 0 by their formulas; non-real roots of a quadratic go to [Found.complex]. */
    private fun formula(c: List<Expr>, out: Found) {
        if (c.size == 2) {
            out.exact += neg(c[0]) / c[1]
            return
        }
        val (k, b, a) = c
        val d = Algebra.expand(b * b - num(4) * a * k)
        val dv = Numeric.value(d)
        val twoA = num(2) * a
        when {
            isZero(d) || (dv != null && d.isConstant() && abs(dv) < 1e-300) -> out.exact += neg(b) / twoA
            dv != null && dv < 0 && d.isConstant() -> {
                val s = sqrt(neg(d))
                out.complex += Algebra.expand((neg(b) - Expr.I * s) / twoA)
                out.complex += Algebra.expand((neg(b) + Expr.I * s) / twoA)
            }
            else -> {
                val s = sqrt(d)
                out.exact += Algebra.expand((neg(b) - s) / twoA)
                out.exact += Algebra.expand((neg(b) + s) / twoA)
            }
        }
    }

    /** All roots of a polynomial with numeric coefficients, by Durand–Kerner: the real ones and the others. */
    private fun numericPolynomial(c: List<Expr>, out: Found) {
        val a = c.map { Numeric.value(it) ?: return }
        val n = a.size - 1
        val lead = a[n]
        val monic = a.map { it / lead }
        var z = List(n) { k -> Cx(0.4, 0.9).pow(Cx(k.toDouble())) }.toMutableList()
        fun value(x: Cx): Cx = monic.asReversed().fold(Cx(0.0)) { acc, ck -> acc * x + Cx(ck) }
        repeat(500) {
            var moved = 0.0
            for (k in 0 until n) {
                var denom = Cx(1.0)
                for (j in 0 until n) if (j != k) denom *= z[k] - z[j]
                val step = value(z[k]) / denom
                z[k] = z[k] - step
                moved = max(moved, step.abs)
            }
            if (moved < 1e-15) return@repeat
        }
        out.numeric = true
        // With rational coefficients the real roots come from Sturm's sequence: certain, none missed however close.
        val exactCoefficients = c.map { rational(it) }
        val certain = if (exactCoefficients.all { it != null }) Sturm.realRoots(exactCoefficients.map { it!! }) else null
        certain?.forEach { out.exact += Expr.Real(it) }
        for (r in z) {
            if (!r.finite) continue
            if (abs(r.im) <= 1e-9 * max(1.0, r.abs)) {
                if (certain == null) out.exact += Expr.Real(polish(monic, r.re))
            } else if (r.im > 0) {
                out.complex += add(Expr.Real(r.re), mul(Expr.Real(r.im), Expr.I))
                out.complex += add(Expr.Real(r.re), mul(Expr.Real(-r.im), Expr.I))
            }
        }
    }

    /** Newton steps on a real root of a monic polynomial. */
    private fun polish(monic: List<Double>, x0: Double): Double {
        var x = x0
        repeat(6) {
            var p = 0.0
            var d = 0.0
            for (ck in monic.asReversed()) { d = d * x + p; p = p * x + ck }
            if (d != 0.0) x -= p / d
        }
        return x
    }

    /** lhs = rhs with x occurring once in lhs (and not in rhs): what is done to x undone, step by step. */
    private fun isolate(lhs: Expr, rhs: Expr, x: Expr.Sym, out: Found, depth: Int) {
        if (depth > 12) return
        when {
            lhs == x -> out.exact += rhs
            lhs is Expr.Add -> {
                val (with, without) = lhs.terms.partition { it.has(x) }
                if (with.size != 1) { roots(lhs - rhs, x, out, depth + 1); return }
                isolate(with[0], rhs - add(without), x, out, depth + 1)
            }
            lhs is Expr.Mul -> {
                val (with, without) = lhs.factors.partition { it.has(x) }
                if (with.size != 1) { roots(lhs - rhs, x, out, depth + 1); return }
                val c = mul(without)
                if (isZero(c)) return
                isolate(with[0], rhs / c, x, out, depth + 1)
            }
            lhs is Expr.Pow && lhs.base.has(x) && !lhs.exp.has(x) -> {
                val r = rational(lhs.exp)
                when {
                    r != null && r.isInteger && r.num.signum() > 0 -> {
                        val n = r.num.toInt()
                        val rv = Numeric.value(rhs)
                        if (n % 2 == 0) {
                            if (rv != null && rv < 0) {
                                // No real root; the complex ones of a square.
                                if (n == 2) {
                                    isolateComplex(lhs.base, Expr.I * sqrt(neg(rhs)), x, out)
                                    isolateComplex(lhs.base, neg(Expr.I * sqrt(neg(rhs))), x, out)
                                }
                                return
                            }
                            val root = pow(rhs, num(Rational.ONE / r))
                            isolate(lhs.base, root, x, out, depth + 1)
                            if (!isZero(root)) isolate(lhs.base, neg(root), x, out, depth + 1)
                        } else isolate(lhs.base, pow(rhs, num(Rational.ONE / r)), x, out, depth + 1)
                    }
                    r != null && r.isInteger && r.num.signum() < 0 -> isolate(pow(lhs.base, num(-r)), Expr.ONE / rhs, x, out, depth + 1)
                    r != null -> {
                        // A root: x^(p/q) = c gives x = c^(q/p), checked afterwards.
                        isolate(lhs.base, pow(rhs, num(Rational.ONE / r)), x, out, depth + 1)
                    }
                    else -> isolate(lhs.base, pow(rhs, Expr.ONE / lhs.exp), x, out, depth + 1)
                }
            }
            lhs is Expr.Pow && !lhs.base.has(x) -> {
                // aᵘ = c: u = log_a c.
                val rv = Numeric.value(rhs)
                if (rv != null && rv <= 0) return
                val u = if (lhs.base == Expr.E) ln(rhs) else fn(Func.LOG, lhs.base, rhs)
                isolate(lhs.exp, u, x, out, depth + 1)
            }
            lhs is Expr.Fn -> inverse(lhs, rhs, x, out, depth)
            else -> numericRoots(lhs - rhs, x, out)
        }
    }

    private fun isolateComplex(lhs: Expr, rhs: Expr, x: Expr.Sym, out: Found) {
        val inner = Found()
        isolate(lhs, rhs, x, inner, 10)
        out.complex += inner.exact.map { Algebra.expand(it) }
    }

    /** f(u) = c undone: u = f⁻¹(c), every solution of a trigonometric one as a family in k. */
    private fun inverse(lhs: Expr.Fn, rhs: Expr, x: Expr.Sym, out: Found, depth: Int) {
        val u = lhs.args.last()
        val rv = Numeric.value(rhs)
        val twoKPi = num(2) * K * Expr.PI
        fun family(base: Expr, period: Expr) {
            val inner = Found()
            isolate(u, base + period, x, inner, depth + 1)
            out.periodic += inner.exact.map { Algebra.expand(it) }
            out.numeric = out.numeric || inner.numeric
        }
        when (lhs.f) {
            Func.SIN -> {
                if (rv != null && abs(rv) > 1 + 1e-12) return
                val a = fn(Func.ASIN, rhs)
                family(a, twoKPi)
                if (Numeric.value(a)?.let { abs(abs(it) - Math.PI / 2) > 1e-12 } != false) family(Expr.PI - a, twoKPi)
            }
            Func.COS -> {
                if (rv != null && abs(rv) > 1 + 1e-12) return
                val a = fn(Func.ACOS, rhs)
                family(a, twoKPi)
                if (Numeric.value(a)?.let { abs(it) > 1e-12 && abs(it - Math.PI) > 1e-12 } != false) family(neg(a), twoKPi)
            }
            Func.TAN -> family(fn(Func.ATAN, rhs), K * Expr.PI)
            Func.COT -> family(fn(Func.ACOT, rhs), K * Expr.PI)
            // sec u = c is cos u = 1/c, csc u = c is sin u = 1/c: their families of solutions (none for c = 0).
            Func.SEC, Func.CSC -> {
                if (rv != null && abs(rv) < 1e-300) return
                if (isZero(rhs)) return
                inverse(Expr.Fn(if (lhs.f == Func.SEC) Func.COS else Func.SIN, lhs.args), Expr.ONE / rhs, x, out, depth)
            }
            // sinh u = c: u = ln(c + √(c² + 1)), the one real solution.
            Func.SINH -> isolate(u, ln(add(rhs, sqrt(add(pow(rhs, Expr.TWO), Expr.ONE)))), x, out, depth + 1)
            // cosh u = c: u = ±ln(c + √(c² − 1)) for c > 1, u = 0 for c = 1, none below 1 (decided in ball arithmetic).
            Func.COSH -> when (constantSign(add(rhs, Expr.MINUS_ONE))) {
                1 -> {
                    val a = ln(add(rhs, sqrt(add(pow(rhs, Expr.TWO), Expr.MINUS_ONE))))
                    isolate(u, a, x, out, depth + 1)
                    isolate(u, neg(a), x, out, depth + 1)
                }
                0 -> isolate(u, Expr.ZERO, x, out, depth + 1)
                else -> {}
            }
            // tanh u = c: u = ln((1 + c)/(1 − c))/2 for |c| < 1; none for |c| ≥ 1, as tanh lies strictly between −1 and 1
            // (tanh x = 1 has no solution, though tanh 20 is 1 as a double).
            Func.TANH -> if (constantSign(add(Expr.ONE, neg(pow(rhs, Expr.TWO)))) == 1) {
                isolate(u, mul(Expr.HALF, ln((Expr.ONE + rhs) / (Expr.ONE - rhs))), x, out, depth + 1)
            }
            // erf and erfc are strictly increasing and decreasing: erf u = 0 at u = 0 alone (erfc u = 1 likewise); another value
            // of their range (−1, 1) and (0, 2) at one point, found numerically; none outside it.
            Func.ERF, Func.ERFC -> {
                val c = if (lhs.f == Func.ERF) rhs else Calculus.tidy(Expr.ONE - rhs)
                if (isZero(c)) { isolate(u, Expr.ZERO, x, out, depth + 1); return }
                if (constantSign(add(Expr.ONE, neg(pow(c, Expr.TWO)))) != 1) return
                val cv = Numeric.value(c) ?: return
                // So close to ±1 that a double cannot find the point: not claimed either way.
                if (abs(cv) > 1 - 1e-12) throw CalcException(Failure.UNSOLVED)
                val root = monotoneRoot({ v -> Numeric.function(Func.ERF, listOf(v)) - cv }, -6.0, 6.0) ?: throw CalcException(Failure.UNSOLVED)
                out.numeric = true
                isolate(u, Expr.Real(root), x, out, depth + 1)
            }
            Func.LN -> isolate(u, exp(rhs), x, out, depth + 1)
            Func.LOG -> isolate(u, pow(lhs.args[0], rhs), x, out, depth + 1)
            Func.ABS -> {
                if (rv != null && rv < 0) return
                isolate(u, rhs, x, out, depth + 1)
                if (!isZero(rhs)) isolate(u, neg(rhs), x, out, depth + 1)
            }
            Func.ASIN -> isolate(u, fn(Func.SIN, rhs), x, out, depth + 1)
            Func.ACOS -> isolate(u, fn(Func.COS, rhs), x, out, depth + 1)
            Func.ATAN -> isolate(u, fn(Func.TAN, rhs), x, out, depth + 1)
            else -> numericRoots(lhs - rhs, x, out)
        }
    }

    /**
     * The sign of a constant, decided in ball arithmetic ([Precise.signOf]); a constant whose sign the balls cannot decide is not
     * guessed: [Failure.UNSOLVED]. Not a constant: [Failure.SEVERAL_UNKNOWNS].
     */
    private fun constantSign(e: Expr): Int {
        if (!e.isConstant()) throw CalcException(Failure.SEVERAL_UNKNOWNS)
        rational(e)?.let { return it.signum }
        return Precise.signOf(e) ?: throw CalcException(Failure.UNSOLVED)
    }

    /** The one root of an increasing function [g] in [a, b] by bisection, to a double's precision; null without a sign change. */
    private fun monotoneRoot(g: (Double) -> Double, a0: Double, b0: Double): Double? {
        var a = a0
        var b = b0
        if (g(a) > 0 || g(b) < 0) return null
        repeat(200) {
            Budget.check()
            val m = (a + b) / 2
            if (m == a || m == b) return m
            if (g(m) < 0) a = m else b = m
        }
        return (a + b) / 2
    }

    /** f = c·F(A) + rest, rest free of x, F a logarithm or a power of a number: (A, F⁻¹(−rest/c)). */
    private fun outer(f: Expr, x: Expr.Sym): Pair<Expr, Expr>? {
        val terms = if (f is Expr.Add) f.terms else listOf(f)
        val (with, without) = terms.partition { it.has(x) }
        if (with.size != 1) return null
        val (c, g) = splitCoefficient(with[0])
        val rhs = neg(add(without)) / c
        return when {
            g is Expr.Fn && g.f == Func.LN -> g.args[0] to exp(rhs)
            g is Expr.Fn && g.f == Func.LOG && !g.args[0].has(x) -> g.args[1] to pow(g.args[0], rhs)
            g is Expr.Pow && !g.base.has(x) -> {
                if ((Numeric.value(rhs) ?: return null) <= 0) return null
                g.exp to (if (g.base == Expr.E) ln(rhs) else fn(Func.LOG, g.base, rhs))
            }
            else -> null
        }
    }

    /** aᵘ = aᵛ (after moving everything): u = v. */
    private fun sameBase(f: Expr, x: Expr.Sym): Pair<Expr, Expr>? {
        if (f !is Expr.Add || f.terms.size != 2) return null
        val (p, q) = f.terms
        val (c, qq) = splitCoefficient(q)
        if (!isOne(neg(c)) || p !is Expr.Pow || qq !is Expr.Pow || p.base != qq.base || p.base.has(x)) return null
        return p.exp to qq.exp
    }

    /** A root of something with x, alone against the rest: f = r + rest. */
    private fun radical(f: Expr, x: Expr.Sym): Pair<Expr, Expr>? {
        val terms = if (f is Expr.Add) f.terms else listOf(f)
        for (t in terms) {
            val (c, rest) = splitCoefficient(t)
            if (rest is Expr.Pow && rest.base.has(x) && rational(rest.exp)?.let { !it.isInteger && it.num.abs() == java.math.BigInteger.ONE } == true) {
                val others = add(terms.filter { it !== t })
                return rest to others / c
            }
        }
        return null
    }

    /** An absolute value with x inside. */
    private fun absolute(f: Expr): Expr? {
        var found: Expr? = null
        fun walk(e: Expr) {
            if (found != null) return
            if (e is Expr.Fn && e.f == Func.ABS && !e.args[0].isConstant()) { found = e; return }
            when (e) {
                is Expr.Add -> e.terms.forEach(::walk)
                is Expr.Mul -> e.factors.forEach(::walk)
                is Expr.Pow -> { walk(e.base); walk(e.exp) }
                is Expr.Fn -> e.args.forEach(::walk)
                else -> Unit
            }
        }
        walk(f)
        return found
    }

    /** Real roots by sign changes on a grid, refined by bisection; finer near 0, where exercises' answers mostly are. */
    private fun numericRoots(f: Expr, x: Expr.Sym, out: Found) {
        fun value(v: Double): Double = Numeric.eval(f, mapOf(x.name to v))
        val grid = ArrayList<Double>()
        for (k in -1000..-200) grid.add(k.toDouble())
        for (k in -1999..-200) grid.add(k / 10.0)
        for (k in -1999..1999) grid.add(k / 100.0)
        for (k in 200..1999) grid.add(k / 10.0)
        for (k in 200..1000) grid.add(k.toDouble())
        grid.sort()
        val found = ArrayList<Double>()
        var a = grid[0]
        var fa = value(a)
        for (b in grid.drop(1)) {
            if (b == a) continue
            Budget.check()
            val fb = value(b)
            if (fa == 0.0) found += a
            else if (fa.isFinite() && fb.isFinite() && fa * fb < 0) {
                var lo = a
                var hi = b
                var flo = fa
                for (step in 0 until 100) {
                    val m = (lo + hi) / 2
                    val fm = value(m)
                    if (fm == 0.0) { lo = m; hi = m; break }
                    if (!fm.isFinite()) break
                    if ((fm < 0) == (flo < 0)) { lo = m; flo = fm } else hi = m
                }
                val m = (lo + hi) / 2
                // A pole changes sign too (1/x at 0): only a point where f is about 0 is a root.
                if (abs(value(m)) <= 1e-7 * (1 + abs(fa) + abs(fb))) found += m
            }
            if (found.size > MOST) {
                out.many = true
                return
            }
            a = b
            fa = fb
        }
        out.numeric = true
        for (r in found.distinctBy { Math.round(it * 1e7) }) {
            val near = Rational.near(r, 1000, 1e-9)
            out.exact += if (near != null && exactlyZero(f, x, near)) num(near) else Expr.Real(r)
        }
    }

    private fun exactlyZero(f: Expr, x: Expr.Sym, r: Rational): Boolean = try {
        val v = f.substitute(x, num(r))
        // A fraction near a numeric root is that root only where f there is 0 to the balls too, not merely to a double.
        isZero(v) || (Numeric.value(v)?.let { abs(it) <= 1e-12 } == true && Precise.certainlyNonzero(v) == false)
    } catch (e: CalcException) {
        false
    }

    /** The candidates sorted out: what solves lhs = rhs as written, what does not (rejected), families, complex ones. */
    private fun check(lhs: Expr, rhs: Expr, x: Expr.Sym, found: Found): Solutions {
        if (found.many) return Solutions(x.name, many = true)
        val good = ArrayList<Expr>()
        val rejected = ArrayList<Expr>()
        for (c in found.exact) {
            val v = simplifyValue(c)
            if (solves(lhs, rhs, x, v)) good += v else rejected += v
        }
        val values = good.distinctBy { Numeric.value(it)?.let { d -> Math.round(d * 1e9) } ?: it.hashCode().toLong() }
            .sortedBy { Numeric.value(it) ?: 0.0 }
        // A family's members in one period [0, 2π): the values a learner looks for.
        val periodic = mergeFamilies(found.periodic.map { simplifyValue(it) }.distinct())
        val inPeriod = ArrayList<Expr>()
        if (periodic.isNotEmpty()) {
            for (fam in periodic) for (k in -3..3) {
                val v = simplifyValue(fam.substitute(K, num(k.toLong())))
                val d = Numeric.value(v) ?: continue
                if (d >= -1e-12 && d < 2 * Math.PI - 1e-12 && solves(lhs, rhs, x, v)) inPeriod += v
            }
        }
        val complex = found.complex.map { simplifyValue(it) }.distinct()
        return Solutions(x.name,
            values = if (periodic.isNotEmpty()) inPeriod.distinctBy { Math.round((Numeric.value(it) ?: 0.0) * 1e9) }.sortedBy { Numeric.value(it) ?: 0.0 } else values,
            periodic = periodic, complex = complex, rejected = rejected.distinct(), numeric = found.numeric)
    }

    /** A value as simply as it can be written. */
    internal fun simplifyValue(v: Expr): Expr = try {
        val s = Algebra.simplify(v)
        Algebra.rationalize(s)
    } catch (e: CalcException) {
        v
    }

    /**
     * Families a + 2kπ and a + π + 2kπ as one, a + kπ: sin x = 0 is x = kπ. Each family's start a (k = 0) and step (from k to
     * k + 1) are compared numerically.
     */
    private fun mergeFamilies(families: List<Expr>): List<Expr> {
        val out = families.toMutableList()
        fun startStep(f: Expr): Pair<Double, Double>? {
            val a = Numeric.value(f.substitute(K, Expr.ZERO)) ?: return null
            val b = Numeric.value(f.substitute(K, Expr.ONE)) ?: return null
            return a to (b - a)
        }
        var merged = true
        while (merged) {
            merged = false
            loop@ for (i in out.indices) for (j in out.indices) {
                if (i == j) continue
                val (ai, si) = startStep(out[i]) ?: continue
                val (aj, sj) = startStep(out[j]) ?: continue
                if (abs(si - sj) > 1e-9 || abs(si) < 1e-12 || ai > aj) continue
                val gap = ((aj - ai) / si).let { it - Math.floor(it) }
                if (abs(gap - 0.5) > 1e-9) continue
                val start = out[i].substitute(K, Expr.ZERO)
                out[i] = Algebra.expand(start + (out[i] - start) / Expr.TWO)
                out.removeAt(j)
                merged = true
                break@loop
            }
        }
        // Each family from its start in [0, step): −π/2 + kπ is π/2 + kπ; then by their starts.
        return out.map { f ->
            val (a, s) = startStep(f) ?: return@map f
            val m = Math.floor(a / s + 1e-12).toLong()
            if (m == 0L) f else simplifyValue(Algebra.expand(f.substitute(K, K - num(m))))
        }.sortedBy { f -> startStep(f)?.first ?: 0.0 }
    }

    /** Whether x = v solves lhs = rhs: both sides defined (real) there and equal. */
    internal fun solves(lhs: Expr, rhs: Expr, x: Expr.Sym, v: Expr): Boolean {
        if (v is Expr.Real || !v.isConstant()) {
            val d = Numeric.value(v) ?: return v.symbols().isNotEmpty()
            val a = Numeric.eval(lhs, mapOf(x.name to d))
            val b = Numeric.eval(rhs, mapOf(x.name to d))
            if (!a.isFinite() || !b.isFinite()) return false
            return abs(a - b) <= 1e-6 * max(1.0, max(abs(a), abs(b)))
        }
        return try {
            val a = lhs.substitute(x, v)
            val b = rhs.substitute(x, v)
            val va = Numeric.value(a)
            val vb = Numeric.value(b)
            if (va == null || vb == null) return false
            if (abs(va - vb) > 1e-9 * max(1.0, max(abs(va), abs(vb)))) return false
            // A root of radicals or cosines (Cardano, a casus irreducibilis): its residual is checked in ball arithmetic too.
            if (v !is Expr.Num && !lhs.hasOp() && !rhs.hasOp()) Precise.nearZero(add(a, neg(b)), complex = true) != false else true
        } catch (e: CalcException) {
            false
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // A formula solved for one of its letters.

    /**
     * lhs = rhs solved for [x], the other letters standing as they are: v = s/t for t is t = s/v; a x + b = c is
     * x = (c − b)/a with a ≠ 0. Linear and quadratic ones in [x], and those where [x] occurs once.
     */
    fun forLetter(lhs: Expr, rhs: Expr, x: Expr.Sym): Solutions {
        val f = lhs - rhs
        if (!f.has(x)) return Solutions(x.name)
        val kernels = Algebra.Kernels()
        val numerator = Algebra.fraction(f, kernels)?.let { Algebra.toExpr(it.first, kernels) } ?: f
        val c = coefficientsIn(Algebra.expand(numerator), x)
        val out = Found()
        // A letter that occurs once is undone step by step (A = πr² gives r = ±√(A/π)); otherwise the formulas of degree one or two.
        if (occurrences(f, x) == 1) {
            isolate(f, Expr.ZERO, x, out, 0)
        } else if (c != null && c.size in 2..3) {
            formula(c, out)
        } else return Solutions(x.name)
        // A formula's value as written (√(A/π), not √(πA)/π), the negative one first (other letters taken as 1).
        val others = f.symbols().minus(x.name).associateWith { 1.0 }
        val values = (out.exact + out.periodic).map { v -> try { Algebra.simplify(v) } catch (e: CalcException) { v } }.distinct()
            .sortedBy { Numeric.eval(it, others).let { d -> if (d.isNaN()) 0.0 else d } }
        // Where it holds: what a value divides by must not be 0 (t = s/v needs v ≠ 0).
        val conditions = values.flatMap { v ->
            (if (v is Expr.Mul) v.factors else listOf(v)).filter { g ->
                g is Expr.Pow && rational(g.exp)?.signum == -1 && g.base.symbols().isNotEmpty()
            }.map { (it as Expr.Pow).base }
        }.distinct()
        return Solutions(x.name, values = values, conditions = conditions)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Inequalities.

    /** lhs rel rhs in one unknown as intervals: by the signs of lhs − rhs between the points where it is 0 or undefined. */
    fun inequality(lhs: Expr, rel: Rel, rhs: Expr, x: Expr.Sym): IntervalSet? {
        val f = lhs - rhs
        fun holds(v: Double): Boolean? {
            val d = Numeric.eval(f, mapOf(x.name to v))
            if (!d.isFinite()) return null
            val zero = abs(d) <= 1e-9 * max(1.0, abs(v))
            return when (rel) {
                Rel.LT -> d < 0 && !zero
                Rel.LE -> d < 0 || zero
                Rel.GT -> d > 0 && !zero
                Rel.GE -> d > 0 || zero
                Rel.NE -> !zero
                Rel.EQ -> zero
            }
        }
        // The points where the sign can change: zeros of f, zeros of its denominators, and where a root or a logarithm begins.
        val points = ArrayList<Expr>()
        val zeros = equation(lhs, rhs, x)
        if (zeros.many || zeros.periodic.isNotEmpty()) return null
        if (zeros.always) {
            return IntervalSet(x.name, if (rel == Rel.LE || rel == Rel.GE) listOf(Interval(null, false, null, false)) else emptyList())
        }
        points += zeros.values
        for (d in boundaries(f, x)) {
            val z = equation(d, Expr.ZERO, x)
            if (z.many || z.periodic.isNotEmpty()) return null
            points += z.values
        }
        val sorted = points.filter { Numeric.value(it) != null }.distinctBy { Math.round(Numeric.value(it)!! * 1e9) }
            .sortedBy { Numeric.value(it)!! }
        val values = sorted.map { Numeric.value(it)!! }
        // Whether it holds in each gap between the points (tested inside it) and at each point; then the gaps and points
        // that hold, joined into intervals: (−∞, p₁), p₁, (p₁, p₂), p₂, …, (pₘ, ∞).
        fun gapHolds(lo: Double?, hi: Double?): Boolean {
            val t = when {
                lo == null && hi == null -> 0.37
                lo == null -> hi!! - 1 - abs(hi) * 0.1
                hi == null -> lo + 1 + abs(lo) * 0.1
                else -> (lo + hi) / 2
            }
            return holds(t) == true
        }
        val pieces = ArrayList<Interval>()
        var inside = gapHolds(null, values.firstOrNull())
        var start: Expr? = null
        var startClosed = false
        for ((k, p) in sorted.withIndex()) {
            val atPoint = holds(values[k]) == true
            if (inside && !atPoint) {
                pieces += Interval(start, startClosed, p, false)
                inside = false
            } else if (!inside && atPoint) {
                inside = true
                start = p
                startClosed = true
            }
            val after = gapHolds(values[k], values.getOrNull(k + 1))
            if (inside && !after) {
                pieces += Interval(start, startClosed, p, true)
                inside = false
            } else if (!inside && after) {
                inside = true
                start = p
                startClosed = false
            }
        }
        if (inside) pieces += Interval(start, startClosed, null, false)
        return IntervalSet(x.name, pieces)
    }

    /** What a denominator, an even root or a logarithm of x makes a boundary of the domain. */
    private fun boundaries(f: Expr, x: Expr.Sym): List<Expr> {
        val out = ArrayList<Expr>()
        fun walk(e: Expr) {
            when (e) {
                is Expr.Pow -> {
                    val r = rational(e.exp)
                    if (e.base.has(x) && r != null && (r.signum < 0 || !r.den.testBit(0))) out += e.base
                    walk(e.base)
                    walk(e.exp)
                }
                is Expr.Fn -> {
                    if ((e.f == Func.LN || e.f == Func.LOG) && e.args.last().has(x)) out += e.args.last()
                    e.args.forEach(::walk)
                }
                is Expr.Add -> e.terms.forEach(::walk)
                is Expr.Mul -> e.factors.forEach(::walk)
                else -> Unit
            }
        }
        walk(f)
        return out
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Systems.

    /**
     * A system of equations (each lhs − rhs = 0) in [letters]: linear ones by elimination with exact numbers, others by
     * solving one equation for a letter it is linear in and putting that into the rest. Each solution maps the letters to
     * values; an empty list where there is none, null where Calculate cannot solve it.
     */
    fun system(equations: List<Expr>, letters: List<String>): List<Map<String, Expr>>? {
        if (equations.isEmpty() || letters.isEmpty()) return null
        // Linear ones by elimination, others by putting one letter into the rest, and polynomial ones by a Gröbner basis.
        val solutions = linear(equations, letters) ?: substitution(equations, letters, 0) ?: Groebner.solve(equations, letters) ?: return null
        // In order of their values, letter by letter.
        return solutions.sortedWith { p, q ->
            letters.asSequence().map { v -> (Numeric.value(p.getValue(v)) ?: 0.0).compareTo(Numeric.value(q.getValue(v)) ?: 0.0) }
                .firstOrNull { it != 0 } ?: 0
        }
    }

    private fun linear(equations: List<Expr>, letters: List<String>): List<Map<String, Expr>>? {
        val n = letters.size
        val rows = equations.map { e ->
            val expanded = Algebra.expand(e)
            val row = ArrayList<Rational>()
            var rest: Expr = expanded
            for (v in letters) {
                val c = coefficientsIn(expanded, Expr.Sym(v)) ?: return null
                if (c.size > 2) return null
                val a = if (c.size == 2) rational(c[1]) ?: return null else Rational.ZERO
                row += a
                rest = rest - mul(num(a), Expr.Sym(v))
            }
            val r = rational(Algebra.expand(rest)) ?: return null
            row += -r
            row
        }.toMutableList()
        // Gauss–Jordan with exact fractions.
        val m = rows.map { it.toMutableList() }.toMutableList()
        var rank = 0
        val pivots = ArrayList<Int>()
        for (col in 0 until n) {
            val p = (rank until m.size).firstOrNull { m[it][col].signum != 0 } ?: continue
            m[p] = m[rank].also { m[rank] = m[p] }
            val lead = m[rank][col]
            for (j in 0..n) m[rank][j] = m[rank][j] / lead
            for (r in m.indices) if (r != rank && m[r][col].signum != 0) {
                val k = m[r][col]
                for (j in 0..n) m[r][j] = m[r][j] - k * m[rank][j]
            }
            pivots += col
            rank++
        }
        // A row 0 = c with c ≠ 0: no solution.
        for (r in rank until m.size) if (m[r][n].signum != 0) return emptyList()
        if (rank < n) return null
        return listOf(letters.withIndex().associate { (k, v) -> v to num(m[pivots.indexOf(k)][n]) })
    }

    /**
     * A linear system with infinitely many solutions (x + y = 1, 2x + 2y = 2): its family, the letters of the free columns
     * as parameters t (t₁, t₂, … for several; another letter where the system has t), each pivot letter in them; the whole
     * family put back into every equation, which must hold for every value of the parameters. Null where the system is not
     * linear, has one solution or none, or the check fails.
     */
    fun linearFamily(equations: List<Expr>, letters: List<String>): Map<String, Expr>? {
        val n = letters.size
        val rows = equations.map { e ->
            val expanded = Algebra.expand(e)
            val row = ArrayList<Rational>()
            var rest: Expr = expanded
            for (v in letters) {
                val c = coefficientsIn(expanded, Expr.Sym(v)) ?: return null
                if (c.size > 2) return null
                val a = if (c.size == 2) rational(c[1]) ?: return null else Rational.ZERO
                row += a
                rest = rest - mul(num(a), Expr.Sym(v))
            }
            row += -(rational(Algebra.expand(rest)) ?: return null)
            row.toMutableList()
        }.toMutableList()
        var rank = 0
        val pivots = ArrayList<Int>()
        for (col in 0 until n) {
            val p = (rank until rows.size).firstOrNull { rows[it][col].signum != 0 } ?: continue
            rows[p] = rows[rank].also { rows[rank] = rows[p] }
            val lead = rows[rank][col]
            for (j in 0..n) rows[rank][j] = rows[rank][j] / lead
            for (r in rows.indices) if (r != rank && rows[r][col].signum != 0) {
                val k = rows[r][col]
                for (j in 0..n) rows[r][j] = rows[r][j] - k * rows[rank][j]
            }
            pivots += col
            rank++
        }
        for (r in rank until rows.size) if (rows[r][n].signum != 0) return null
        if (rank == n) return null
        val free = (0 until n).filter { it !in pivots }
        val base = listOf("t", "s", "r", "u", "v", "w").firstOrNull { b -> letters.none { it == b || it.startsWith(b + "_") } } ?: return null
        val names = if (free.size == 1) listOf(base) else free.indices.map { "${base}_${it + 1}" }
        val parameters = free.zip(names).toMap()
        val solution = LinkedHashMap<String, Expr>()
        for ((k, v) in letters.withIndex()) {
            solution[v] = parameters[k]?.let { Expr.Sym(it) } ?: run {
                val row = rows[pivots.indexOf(k)]
                Algebra.expand(add(listOf(num(row[n])) + free.map { j -> mul(num(-row[j]), Expr.Sym(parameters.getValue(j))) }))
            }
        }
        // The family put back: every equation 0 for every value of the parameters, as polynomials in them.
        for (e in equations) {
            var put: Expr = e
            for ((v, value) in solution) put = put.substitute(Expr.Sym(v), value)
            if (!isZero(Algebra.expand(put))) return null
        }
        return solution
    }

    private fun substitution(equations: List<Expr>, letters: List<String>, depth: Int): List<Map<String, Expr>>? {
        if (depth > 4) return null
        if (letters.size == 1) {
            val x = Expr.Sym(letters[0])
            val sets = equations.map { equation(it, Expr.ZERO, x) }
            if (sets.any { it.many || it.periodic.isNotEmpty() || it.incomplete }) return null
            val first = sets.firstOrNull { !it.always } ?: return null
            return first.values.filter { v -> equations.all { solves(it, Expr.ZERO, x, v) } }.map { mapOf(letters[0] to it) }
        }
        // An equation linear in some letter: that letter solved for, put into the others.
        for (e in equations) for (v in letters) {
            val x = Expr.Sym(v)
            val c = coefficientsIn(Algebra.expand(e), x) ?: continue
            if (c.size != 2 || !c[1].isConstant() || isZero(c[1])) continue
            val value = Algebra.expand(neg(c[0]) / c[1])
            val rest = equations.filter { it !== e }.map { Algebra.expand(it.substitute(x, value)) }
            val others = letters - v
            val inner = (if (rest.isEmpty()) return null else substitution(rest, others, depth + 1)) ?: continue
            return inner.map { sol ->
                var full = value
                for ((k, w) in sol) full = full.substitute(Expr.Sym(k), w)
                sol + (v to simplifyValue(full))
            }
        }
        return null
    }
}

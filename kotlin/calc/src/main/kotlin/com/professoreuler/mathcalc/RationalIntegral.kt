package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.math.abs

/**
 * The antiderivative of every fraction of polynomials in x with rational coefficients whose logarithms need at most square
 * roots, written from the algorithms as M. Bronstein describes them (*Symbolic Integration I*, chapter 2):
 *
 * - Hermite's reduction (Mack's linear version) gives the rational part and leaves A/D with D square-free;
 * - Rothstein and Trager's resultant R(t) = res_x(D, A − t·D′) gives the logarithms: Σ over the roots α of R of
 *   α·ln gcd(D, A − α·D′), the gcd taken in Q(α)[x] for each irreducible factor of R (a factor found reducible on the way,
 *   where an inverse does not exist, is split there and both parts are taken);
 * - the roots α are written with square roots (a quadratic factor by its formula, a quartic one where it splits into two
 *   quadratics over Q(√m) and their roots denest), in a field Q(√p₁, …, √pₖ) with i among the √p, and a pair of complex
 *   conjugate roots α = a ± bi becomes a·ln(P² + Q²) + b·Σ 2·arctan(…) by Rioboo's algorithm (no arctangent of a fraction
 *   that jumps where its denominator is 0), with S(α, x) = P + iQ.
 *
 * So 1/(x⁴ + 1), 1/(x⁶ + 1), 1/(x² + 1)³ and (x² + 1)/(x⁴ + 1) are integrated. Roots that need more than square roots (an
 * irreducible cubic factor of R, as for 1/(x³ − 2)) are declined. Every result is differentiated back by the caller
 * ([Calculus.antiderivative]).
 */
internal object RationalIntegral {
    private class Fail : Exception() {
        override fun fillInStackTrace(): Throwable = this
    }

    private fun fail(): Nothing = throw Fail()

    /** ∫ [e] d[x] where [e] is a fraction of polynomials in [x] alone, or null. */
    fun of(e: Expr, x: Expr.Sym): Expr? = parts(e, x)?.takeIf { it.second.isEmpty() }?.first

    /** The letter of a root sum's roots. */
    val ROOT = Expr.Sym("α")

    /**
     * ∫ [e] d[x] as a root sum where its logarithms need roots no square roots write (1/(x³ − 2), 1/(x⁵ + 1)): what can be
     * written, and for each irreducible factor q of the resultant the sum over its roots α of α·ln S(α, x). Checked here:
     * its derivative, worked out at the roots of each q found numerically, against the integrand at several points.
     */
    fun rootSum(e: Expr, x: Expr.Sym): Outcome.RootSum? {
        val (rest, sums) = parts(e, x) ?: return null
        if (sums.isEmpty()) return null
        val written = sums.map { (q, s) ->
            Q.expr(q, ROOT) to add(s.withIndex().map { (k, c) -> mul(Q.expr(c, ROOT), pow(x, num(k.toLong()))) })
        }
        if (!rootSumHolds(e, rest, sums, x)) return null
        return Outcome.RootSum(Calculus.tidy(rest), ROOT.name, written, x.name)
    }

    private fun rootSumHolds(e: Expr, rest: Expr, sums: List<Pair<List<Rational>, List<List<Rational>>>>, x: Expr.Sym): Boolean {
        val dRest = Calculus.diff(rest, x)
        val roots = sums.map { (q, _) -> Numbers.roots(q.map { it.toDouble() }) ?: return false }
        var compared = 0
        for (p in listOf(0.37, 1.23, -0.71, 2.6, -1.9, 0.83, 3.4)) {
            val f = Numeric.eval(e, mapOf(x.name to p))
            val r = Numeric.eval(dRest, mapOf(x.name to p))
            if (!f.isFinite() || !r.isFinite()) continue
            var total = Cx(r)
            for ((k, pair) in sums.withIndex()) for (alpha in roots[k]) {
                // S(α, p) and ∂S/∂x(α, p): each coefficient a polynomial in α.
                val cs = pair.second.map { c -> c.asReversed().fold(Cx(0.0)) { acc, v -> acc * alpha + Cx(v.toDouble()) } }
                var value = Cx(0.0)
                var slope = Cx(0.0)
                for (j in cs.indices.reversed()) {
                    slope = slope * Cx(p) + value
                    value = value * Cx(p) + cs[j]
                }
                total += alpha * slope / value
            }
            if (!total.finite || kotlin.math.abs(total.im) > 1e-7 * maxOf(1.0, kotlin.math.abs(f)) ||
                kotlin.math.abs(total.re - f) > 1e-7 * maxOf(1.0, kotlin.math.abs(f))) return false
            compared++
        }
        return compared >= 4
    }

    /** What can be written with square roots, and the factors (q, S) whose roots cannot. */
    private fun parts(e: Expr, x: Expr.Sym): Pair<Expr, List<Pair<List<Rational>, List<List<Rational>>>>>? {
        val kernels = Algebra.Kernels()
        val (n0, d0) = try { Algebra.fraction(e, kernels) } catch (z: CalcException) { if (z.failure == Failure.TOO_LONG) throw z; null } ?: return null
        if (!kernels.isEmpty || d0.isConstant) return null
        if ((n0.unknowns + d0.unknowns) != setOf(x.name)) return null
        if (d0.degreeIn(x.name) > 24 || n0.degreeIn(x.name) > 40) return null
        val n = coefficients(n0, x.name)
        val d = coefficients(d0, x.name)
        return try {
            val unwritten = ArrayList<Pair<List<Rational>, List<List<Rational>>>>()
            integrate(n, d, x, unwritten) to unwritten
        } catch (z: Fail) {
            null
        } catch (z: CalcException) {
            if (z.failure == Failure.TOO_LONG) throw z
            null
        } catch (z: ArithmeticException) {
            null
        }
    }

    private fun coefficients(p: Polynomial, x: String): List<Rational> =
        Q.trim(List(p.degreeIn(x) + 1) { k -> p.terms[if (k == 0) Monomial.ONE else Monomial(listOf(x to k))] ?: Rational.ZERO })

    private fun integrate(n: List<Rational>, d: List<Rational>, x: Expr.Sym, unwritten: MutableList<Pair<List<Rational>, List<List<Rational>>>>): Expr {
        val q = Q
        val (quotient, remainder) = q.divide(n, d)
        var result = polyIntegral(quotient, x)
        if (remainder.isEmpty()) return result
        // Hermite: ∫ A/D = g + ∫ h with h's denominator square-free.
        val (rationalPart, a, ds) = hermite(remainder, d, x)
        result = add(result, rationalPart)
        val (polyPart, aa) = q.divide(a, ds)
        result = add(result, polyIntegral(polyPart, x))
        if (aa.isEmpty()) return result
        return add(result, logPart(aa, ds, x, unwritten))
    }

    private fun polyIntegral(p: List<Rational>, x: Expr.Sym): Expr =
        add(p.withIndex().map { (k, c) -> mul(num(c / Rational.of(k + 1L)), pow(x, num(k + 1L))) })

    /** A polynomial over Q as an expression in x. */
    private fun expr(p: List<Rational>, x: Expr.Sym): Expr = add(p.withIndex().map { (k, c) -> mul(num(c), pow(x, num(k.toLong()))) })

    /** Mack's linear Hermite reduction: (g, A, D*) with ∫ a/d = g + ∫ A/D* and D* square-free. */
    private fun hermite(a0: List<Rational>, d: List<Rational>, x: Expr.Sym): Triple<Expr, List<Rational>, List<Rational>> {
        val q = Q
        var a = a0
        var g: Expr = Expr.ZERO
        var dMinus = q.gcd(d, q.derivative(d))
        val dStar = q.divide(d, dMinus).first
        while (q.degree(dMinus) > 0) {
            Budget.check()
            val dMinus2 = q.gcd(dMinus, q.derivative(dMinus))
            val dMinusStar = q.divide(dMinus, dMinus2).first
            // B·(−D*·D⁻′/D⁻) + C·D⁻* = A
            val coefficient = q.neg(q.exactDivide(q.mul(dStar, q.derivative(dMinus)), dMinus))
            val (b, c) = q.solve(coefficient, dMinusStar, a)
            a = q.sub(c, q.exactDivide(q.mul(q.derivative(b), dStar), dMinusStar))
            g = add(g, mul(expr(b, x), pow(expr(dMinus, x), Expr.MINUS_ONE)))
            dMinus = dMinus2
        }
        return Triple(g, a, dStar)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The logarithms.

    /** ∫ a/d for d square-free and deg a < deg d: Σ α·ln S_α(x) over the roots of the Rothstein–Trager resultant, real. */
    private fun logPart(a: List<Rational>, d: List<Rational>, x: Expr.Sym, unwritten: MutableList<Pair<List<Rational>, List<List<Rational>>>>): Expr {
        val q = Q
        val dPrime = q.derivative(d)
        val n = q.degree(d)
        // R(t) = res_x(d, a − t·d′), of degree n in t, by its values at t = 0 … n.
        val points = (0..n).map { k ->
            val t = Rational.of(k.toLong())
            t to q.resultant(d, q.sub(a, q.scale(dPrime, t)))
        }
        val r = q.trim(interpolate(points))
        if (q.degree(r) < 1) fail()
        var result: Expr = Expr.ZERO
        val pending = ArrayDeque<List<Rational>>()
        // R's distinct irreducible factors over Q (the square-free part, factored).
        val squareFree = q.monic(q.divide(r, q.gcd(r, q.derivative(r))).first)
        for (factor in rationalFactors(squareFree)) pending += factor
        var guard = 0
        while (pending.isNotEmpty()) {
            if (++guard > 40) fail()
            Budget.check()
            val factor = pending.removeFirst()
            if (q.degree(factor) < 1) continue
            val field = Algebraic(factor)
            val s = try {
                val poly = PolyOps(field)
                val big = d.map { field.of(it) }
                val other = a.indices.union(dPrime.indices).sorted().map { i ->
                    field.sub(field.of(a.getOrElse(i) { Rational.ZERO }), field.mul(field.t, field.of(dPrime.getOrElse(i) { Rational.ZERO })))
                }
                poly.monic(poly.gcd(big, poly.trim(other)))
            } catch (z: Reducible) {
                // q has a factor of its own: both parts again.
                val g = q.monic(z.factor)
                pending += g
                pending += q.monic(q.divide(factor, g).first)
                continue
            }
            if (s.size < 2) fail()
            val written = Radicals.roots(factor)?.let { sumOverRoots(it, s, x) }
            if (written != null) result = add(result, written) else unwritten += factor to s
        }
        return result
    }

    /** R's factors over Q, each monic and irreducible as far as [Factor] knows (the field arithmetic splits any it missed). */
    private fun rationalFactors(r: List<Rational>): List<List<Rational>> {
        val t = "\u0001t"
        val p = Polynomial(r.withIndex().filter { it.value.signum != 0 }.associate { (k, c) -> (if (k == 0) Monomial.ONE else Monomial(listOf(t to k))) to c })
        val product = Factor.of(p)
        return product.factors.map { (f, _) -> Q.monic(Q.trim(List(f.degreeIn(t) + 1) { k -> f.terms[if (k == 0) Monomial.ONE else Monomial(listOf(t to k))] ?: Rational.ZERO })) }
    }

    /**
     * Σ over the roots α of [factor] of α·ln S(α, x), with S's coefficients polynomials in t (mod the factor), written with
     * square roots: real roots as α·ln|S(α, x)|, a pair of complex ones as a·ln(P² + Q²) + b·(Rioboo's arctangents).
     */
    private fun sumOverRoots(roots: Roots, s: List<List<Rational>>, x: Expr.Sym): Expr {
        val field = roots.field
        val poly = PolyOps(field)
        var out: Expr = Expr.ZERO
        val done = ArrayList<MultiQuad.Element>()
        for (alpha in roots.values) {
            if (done.any { field.equal(it, alpha) }) continue
            // S(α, x): each coefficient, a polynomial in t, at t = α.
            val sAlpha = poly.trim(s.map { c -> c.asReversed().fold(field.zero) { acc, k -> field.add(field.mul(acc, alpha), field.of(k)) } })
            val conjugate = field.conjugate(alpha)
            if (field.equal(conjugate, alpha)) {
                done += alpha
                out = add(out, mul(field.expr(alpha), ln(fn(Func.ABS, poly.expr(sAlpha, x)))))
            } else {
                done += alpha
                done += conjugate
                val a = field.realPart(alpha)
                val b = field.imaginaryPart(alpha)
                val p = poly.trim(sAlpha.map { field.realPart(it) })
                val qq = poly.trim(sAlpha.map { field.imaginaryPart(it) })
                val norm = poly.add(poly.mul(p, p), poly.mul(qq, qq))
                out = add(out, mul(field.expr(a), ln(poly.expr(norm, x))))
                if (qq.isNotEmpty()) out = add(out, mul(field.expr(b), logToAtan(poly, p, qq, x, 0)))
            }
        }
        return out
    }

    /**
     * Rioboo's algorithm: Σ 2·arctan of polynomials whose derivative is that of i·ln((A + iB)/(A − iB)), over the real field of
     * [poly] (A, B real, B ≠ 0).
     */
    private fun logToAtan(poly: PolyOps<MultiQuad.Element>, a: List<MultiQuad.Element>, b: List<MultiQuad.Element>, x: Expr.Sym, depth: Int): Expr {
        if (depth > 20) fail()
        Budget.check()
        val (quotient, remainder) = poly.divide(a, b)
        if (remainder.isEmpty()) return mul(Expr.TWO, fn(Func.ATAN, poly.expr(quotient, x)))
        if (poly.degree(a) < poly.degree(b)) return logToAtan(poly, poly.neg(b), a, x, depth + 1)
        // B·D − A·C = G = gcd(A, B)
        val (d, c, g) = poly.extendedGcd(b, poly.neg(a))
        val top = poly.add(poly.mul(a, d), poly.mul(b, c))
        val ratio = poly.exactDivide(top, g)
        return add(mul(Expr.TWO, fn(Func.ATAN, poly.expr(ratio, x))), logToAtan(poly, d, c, x, depth + 1))
    }

    /** The polynomial through the points (Lagrange), exactly. */
    private fun interpolate(points: List<Pair<Rational, Rational>>): List<Rational> {
        val q = Q
        var out = emptyList<Rational>()
        for ((i, pi) in points.withIndex()) {
            var basis = listOf(Rational.ONE)
            var den = Rational.ONE
            for ((j, pj) in points.withIndex()) if (i != j) {
                basis = q.mul(basis, listOf(-pj.first, Rational.ONE))
                den = den * (pi.first - pj.first)
            }
            out = q.add(out, q.scale(basis, pi.second / den))
        }
        return out
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Fields and polynomials over them.

    /** A field's arithmetic. */
    interface Field<T> {
        val zero: T
        val one: T
        fun of(r: Rational): T
        fun add(a: T, b: T): T
        fun sub(a: T, b: T): T
        fun mul(a: T, b: T): T
        fun inverse(a: T): T
        fun isZero(a: T): Boolean
    }

    /** An element of Q(α) found not invertible: Q[t]/(q) is no field, and [factor] is a factor of q. */
    private class Reducible(val factor: List<Rational>) : Exception() {
        override fun fillInStackTrace(): Throwable = this
    }

    private object RationalField : Field<Rational> {
        override val zero = Rational.ZERO
        override val one = Rational.ONE
        override fun of(r: Rational) = r
        override fun add(a: Rational, b: Rational) = a + b
        override fun sub(a: Rational, b: Rational) = a - b
        override fun mul(a: Rational, b: Rational) = a * b
        override fun inverse(a: Rational) = Rational.ONE / a
        override fun isZero(a: Rational) = a.signum == 0
    }

    /** Polynomials over Q, lowest power first, without trailing zeros. */
    private val Q = PolyOps(RationalField)

    /** Q[t]/(q): polynomials in t of degree below q's, an inverse by Euclid (where none exists, [Reducible]). */
    private class Algebraic(val q: List<Rational>) : Field<List<Rational>> {
        val t: List<Rational> = reduce(listOf(Rational.ZERO, Rational.ONE))
        override val zero: List<Rational> = emptyList()
        override val one: List<Rational> = listOf(Rational.ONE)
        override fun of(r: Rational) = if (r.signum == 0) zero else listOf(r)
        override fun add(a: List<Rational>, b: List<Rational>) = Q.add(a, b)
        override fun sub(a: List<Rational>, b: List<Rational>) = Q.sub(a, b)
        override fun mul(a: List<Rational>, b: List<Rational>) = reduce(Q.mul(a, b))
        override fun isZero(a: List<Rational>) = a.isEmpty()
        override fun inverse(a: List<Rational>): List<Rational> {
            val (s, _, g) = Q.extendedGcd(a, q)
            if (Q.degree(g) > 0) throw Reducible(g)
            return reduce(Q.scale(s, Rational.ONE / g[0]))
        }

        private fun reduce(p: List<Rational>) = if (Q.degree(q) <= 0) p else Q.divide(p, q).second
    }

    /** Polynomials over a field, as lists lowest power first without trailing zeros. */
    class PolyOps<T>(val f: Field<T>) {
        fun trim(p: List<T>): List<T> {
            var n = p.size
            while (n > 0 && f.isZero(p[n - 1])) n--
            return if (n == p.size) p else p.subList(0, n).toList()
        }

        fun degree(p: List<T>) = trim(p).size - 1

        fun add(a: List<T>, b: List<T>): List<T> = trim(List(maxOf(a.size, b.size)) { i ->
            when {
                i >= a.size -> b[i]
                i >= b.size -> a[i]
                else -> f.add(a[i], b[i])
            }
        })

        fun neg(a: List<T>): List<T> = a.map { f.sub(f.zero, it) }

        fun sub(a: List<T>, b: List<T>): List<T> = add(a, neg(b))

        fun scale(a: List<T>, c: T): List<T> = if (f.isZero(c)) emptyList() else trim(a.map { f.mul(it, c) })

        fun mul(a: List<T>, b: List<T>): List<T> {
            if (a.isEmpty() || b.isEmpty()) return emptyList()
            Budget.check()
            val out = MutableList(a.size + b.size - 1) { f.zero }
            for ((i, x) in a.withIndex()) if (!f.isZero(x)) for ((j, y) in b.withIndex()) out[i + j] = f.add(out[i + j], f.mul(x, y))
            return trim(out)
        }

        fun derivative(p: List<T>): List<T> = trim(p.drop(1).mapIndexed { i, c -> f.mul(c, f.of(Rational.of(i + 1L))) })

        fun divide(a0: List<T>, b0: List<T>): Pair<List<T>, List<T>> {
            val b = trim(b0)
            if (b.isEmpty()) throw CalcException(Failure.DIVISION_BY_ZERO)
            var r = trim(a0).toMutableList()
            if (r.size < b.size) return emptyList<T>() to r
            val q = MutableList(r.size - b.size + 1) { f.zero }
            val lead = f.inverse(b.last())
            while (r.size >= b.size) {
                Budget.check()
                val shift = r.size - b.size
                val k = f.mul(r.last(), lead)
                q[shift] = k
                for ((i, c) in b.withIndex()) r[i + shift] = f.sub(r[i + shift], f.mul(c, k))
                r = trim(r).toMutableList()
            }
            return trim(q) to r
        }

        fun exactDivide(a: List<T>, b: List<T>): List<T> {
            val (q, r) = divide(a, b)
            if (r.isNotEmpty()) fail()
            return q
        }

        fun monic(p: List<T>): List<T> = if (p.isEmpty()) p else scale(p, f.inverse(p.last()))

        fun gcd(a: List<T>, b: List<T>): List<T> {
            var x = trim(a)
            var y = trim(b)
            while (y.isNotEmpty()) {
                val r = divide(x, y).second
                x = y
                y = r
            }
            return monic(x)
        }

        /** (s, t, g) with s·a + t·b = g = gcd(a, b), g monic. */
        fun extendedGcd(a: List<T>, b: List<T>): Triple<List<T>, List<T>, List<T>> {
            var r0 = trim(a)
            var r1 = trim(b)
            var s0 = listOf(f.one)
            var s1 = emptyList<T>()
            var t0 = emptyList<T>()
            var t1 = listOf(f.one)
            while (r1.isNotEmpty()) {
                val (q, r) = divide(r0, r1)
                r0 = r1.also { r1 = r }
                s0 = s1.also { s1 = sub(s0, mul(q, s1)) }
                t0 = t1.also { t1 = sub(t0, mul(q, t1)) }
            }
            if (r0.isEmpty()) return Triple(emptyList(), emptyList(), emptyList())
            val c = f.inverse(r0.last())
            return Triple(scale(s0, c), scale(t0, c), scale(r0, c))
        }

        /** (s, t) with s·a + t·b = c and deg s < deg b, for a and b without a common factor. */
        fun solve(a: List<T>, b: List<T>, c: List<T>): Pair<List<T>, List<T>> {
            val (s0, _, g) = extendedGcd(a, b)
            if (degree(g) != 0) fail()
            val s = divide(mul(s0, c), b).second
            val t = exactDivide(sub(c, mul(s, a)), b)
            return s to t
        }

        /** res(a, b) over the field: by remainders, (−1)^(mn)·lc(b)^(m − k)·res(b, a mod b). */
        fun resultant(a0: List<T>, b0: List<T>): T {
            var a = trim(a0)
            var b = trim(b0)
            var factor = f.one
            while (true) {
                Budget.check()
                if (b.isEmpty()) return f.zero
                val m = degree(a)
                val n = degree(b)
                if (n == 0) return f.mul(factor, power(b[0], m))
                val r = divide(a, b).second
                if (r.isEmpty()) return f.zero
                val k = degree(r)
                if ((m.toLong() * n) % 2 == 1L) factor = f.sub(f.zero, factor)
                factor = f.mul(factor, power(b.last(), m - k))
                a = b
                b = r
            }
        }

        private fun power(c: T, n: Int): T {
            var out = f.one
            repeat(n) { out = f.mul(out, c) }
            return out
        }

        /** The polynomial in x, its coefficients written by [Field] where it can ([MultiQuad] and Q). */
        @Suppress("UNCHECKED_CAST")
        fun expr(p: List<T>, x: Expr.Sym): Expr = add(p.withIndex().map { (k, c) ->
            val coefficient = when (f) {
                is MultiQuad -> f.expr(c as MultiQuad.Element)
                RationalField -> num(c as Rational)
                else -> fail()
            }
            mul(coefficient, pow(x, num(k.toLong())))
        })
    }

    /**
     * Q(√p₁, …, √pₖ) for distinct generators, each −1 or a prime: an element is Σ c_S·√(Π_{i∈S} pᵢ) over the subsets S. √−1 is
     * i, the other roots positive reals; conjugation changes the sign of i.
     */
    class MultiQuad(val generators: List<Long>) : Field<MultiQuad.Element> {
        class Element(val c: Map<Int, Rational>)

        override val zero = Element(emptyMap())
        override val one = Element(mapOf(0 to Rational.ONE))
        override fun of(r: Rational) = if (r.signum == 0) zero else Element(mapOf(0 to r))
        override fun isZero(a: Element) = a.c.values.all { it.signum == 0 }

        private fun clean(m: Map<Int, Rational>) = Element(m.filterValues { it.signum != 0 })

        override fun add(a: Element, b: Element): Element {
            val out = HashMap(a.c)
            for ((k, v) in b.c) out[k] = (out[k] ?: Rational.ZERO) + v
            return clean(out)
        }

        override fun sub(a: Element, b: Element) = add(a, Element(b.c.mapValues { -it.value }))

        override fun mul(a: Element, b: Element): Element {
            val out = HashMap<Int, Rational>()
            for ((i, u) in a.c) for ((j, v) in b.c) {
                var k = u * v
                val common = i and j
                for (g in generators.indices) if (common and (1 shl g) != 0) k = k * Rational.of(generators[g])
                val m = i xor j
                out[m] = (out[m] ?: Rational.ZERO) + k
            }
            return clean(out)
        }

        /** a with the sign of √p_g changed. */
        private fun flip(a: Element, g: Int) = Element(a.c.mapValues { (m, v) -> if (m and (1 shl g) != 0) -v else v })

        override fun inverse(a: Element): Element {
            if (isZero(a)) throw CalcException(Failure.DIVISION_BY_ZERO)
            var y = a
            var acc = one
            for (g in generators.indices) {
                val c = flip(y, g)
                acc = mul(acc, c)
                y = mul(y, c)
            }
            val r = y.c[0] ?: throw CalcException(Failure.DIVISION_BY_ZERO)
            return mul(acc, of(Rational.ONE / r))
        }

        fun equal(a: Element, b: Element) = isZero(sub(a, b))

        private val imaginary = generators.indexOf(-1L)

        fun conjugate(a: Element) = if (imaginary < 0) a else flip(a, imaginary)

        fun realPart(a: Element) = if (imaginary < 0) a else clean(a.c.filterKeys { it and (1 shl imaginary) == 0 })

        /** b where a = (real part) + i·b: the terms with i, without it. */
        fun imaginaryPart(a: Element): Element =
            if (imaginary < 0) zero else clean(a.c.filterKeys { it and (1 shl imaginary) != 0 }.mapKeys { it.key xor (1 shl imaginary) })

        /** A real element as an expression: Σ c·√(Π p). */
        fun expr(a: Element): Expr {
            if (imaginary >= 0 && a.c.keys.any { it and (1 shl imaginary) != 0 }) fail()
            return add(a.c.map { (m, v) ->
                var product = BigInteger.ONE
                for (g in generators.indices) if (m and (1 shl g) != 0) product *= BigInteger.valueOf(generators[g])
                mul(num(v), sqrt(num(Rational.of(product))))
            })
        }

        /** The complex value, in doubles. */
        fun value(a: Element): Pair<Double, Double> {
            var re = 0.0
            var im = 0.0
            for ((m, v) in a.c) {
                var size = v.toDouble()
                var isImaginary = false
                for (g in generators.indices) if (m and (1 shl g) != 0) {
                    if (generators[g] == -1L) isImaginary = true else size *= kotlin.math.sqrt(generators[g].toDouble())
                }
                if (isImaginary) im += size else re += size
            }
            return re to im
        }

        /** √r for a rational r, in this field (every prime of r's square-free part a generator), or null. */
        fun sqrtOf(r: Rational): Element? {
            if (r.signum == 0) return zero
            val (outside, inside) = squareFree(r)
            var mask = 0
            for ((g, p) in generators.withIndex()) if (p in inside) mask = mask or (1 shl g)
            if (inside.any { it !in generators }) return null
            return Element(mapOf(mask to outside))
        }
    }

    /** r = c²·Π p over distinct p (−1 among them where r < 0): (c, the p). */
    internal fun squareFree(r: Rational): Pair<Rational, List<Long>> {
        if (r.signum == 0) return Rational.ZERO to emptyList()
        val primes = ArrayList<Long>()
        var c = Rational.ONE
        if (r.signum < 0) primes += -1L
        // r = n/d = n·d / d²
        val m = r.num.abs() * r.den
        val parts = Factor.primes(m) ?: if (m == BigInteger.ONE) emptyList() else fail()
        var outside = BigInteger.ONE
        for ((p, k) in parts) {
            outside *= p.pow(k / 2)
            if (k % 2 == 1) primes += p.toLong()
        }
        c = Rational.of(outside, r.den)
        return c to primes
    }

    /** The roots of an irreducible polynomial over Q written with square roots, in one field. */
    class Roots(val field: MultiQuad, val values: List<MultiQuad.Element>)

    internal object Radicals {
        /** The roots of monic [q] (degree 1, 2 or 4) in a field Q(√…), or null where they need more than square roots. */
        fun roots(q0: List<Rational>): Roots? {
            val q = Q.monic(q0)
            // A number too large to take apart on the way (a discriminant's primes) leaves the roots unwritten, not the integral.
            return try {
                when (Q.degree(q)) {
                    1 -> MultiQuad(emptyList()).let { f -> Roots(f, listOf(f.of(-q[0]))) }
                    2 -> quadratic(q)
                    4 -> quartic(q)
                    else -> null
                }
            } catch (z: Fail) {
                null
            }
        }

        private fun quadratic(q: List<Rational>): Roots? {
            // t² + b t + c: (−b ± √(b² − 4c))/2
            val b = q[1]
            val c = q[0]
            val disc = b * b - Rational.of(4) * c
            val (_, primes) = squareFree(disc)
            val field = MultiQuad(primes)
            val root = field.sqrtOf(disc) ?: return null
            val half = field.of(Rational.ONE / Rational.of(2))
            val minusB = field.of(-b)
            return Roots(field, listOf(field.mul(half, field.add(minusB, root)), field.mul(half, field.sub(minusB, root))))
        }

        /**
         * A quartic as two quadratics over Q(√m) (found from its numeric roots paired, then multiplied out exactly), each
         * quadratic's roots by its formula with the root of its discriminant denested into Q(√m, √u): or null.
         */
        private fun quartic(q: List<Rational>): Roots? {
            val numeric = Numbers.roots(q.map { it.toDouble() }) ?: return null
            val pairings = listOf(listOf(0, 1, 2, 3), listOf(0, 2, 1, 3), listOf(0, 3, 1, 2))
            for (pairing in pairings) {
                val (i, j, k, l) = pairing
                val s1 = numeric[i] + numeric[j]
                val s2 = numeric[k] + numeric[l]
                val p1 = numeric[i] * numeric[j]
                val p2 = numeric[k] * numeric[l]
                // The factors' coefficients s and p are conjugate over Q(√m): their sums and products rational.
                val ss = rationalNear(s1 * s2) ?: continue
                val ps = rationalNear(p1 + p2) ?: continue
                val sSum = -q[3]
                val pProduct = q[0]
                val ds = sSum * sSum - Rational.of(4) * ss
                val dp = ps * ps - Rational.of(4) * pProduct
                val m = when {
                    ds.signum != 0 -> squareFree(ds)
                    dp.signum != 0 -> squareFree(dp)
                    else -> continue
                }
                val field = MultiQuad(m.second)
                val rootDs = field.sqrtOf(ds) ?: continue
                val rootDp = field.sqrtOf(dp) ?: continue
                val half = field.of(Rational.ONE / Rational.of(2))
                for (signS in listOf(1, -1)) for (signP in listOf(1, -1)) {
                    val sa = field.mul(half, field.add(field.of(sSum), if (signS > 0) rootDs else field.sub(field.zero, rootDs)))
                    val sb = field.mul(half, field.sub(field.of(sSum), if (signS > 0) rootDs else field.sub(field.zero, rootDs)))
                    val pa = field.mul(half, field.add(field.of(ps), if (signP > 0) rootDp else field.sub(field.zero, rootDp)))
                    val pb = field.mul(half, field.sub(field.of(ps), if (signP > 0) rootDp else field.sub(field.zero, rootDp)))
                    val poly = PolyOps(field)
                    val f1 = listOf(pa, field.sub(field.zero, sa), field.one)
                    val f2 = listOf(pb, field.sub(field.zero, sb), field.one)
                    val product = poly.mul(f1, f2)
                    if (product.size != 5 || (0..4).any { !field.equal(product[it], field.of(q[it])) }) continue
                    val first = quadraticOver(field, f1) ?: continue
                    val second = quadraticOver(field, f2) ?: continue
                    // One field for all four roots.
                    val generators = (first.field.generators + second.field.generators).distinct().sorted()
                    val big = MultiQuad(generators)
                    return Roots(big, (first.values.map { lift(first.field, big, it) } + second.values.map { lift(second.field, big, it) }))
                }
            }
            return null
        }

        /** The roots of t² − s t + p over [field], with √(s² − 4p) denested where it is no element of the field. */
        private fun quadraticOver(field: MultiQuad, f: List<MultiQuad.Element>): Roots? {
            // c + b t + t²: (−b ± √(b² − 4c))/2
            val b = f[1]
            val c = f[0]
            val disc = field.sub(field.mul(b, b), field.mul(field.of(Rational.of(4)), c))
            val (big, root) = sqrtIn(field, disc) ?: return null
            val half = big.of(Rational.ONE / Rational.of(2))
            val minusB = lift(field, big, field.sub(field.zero, b))
            return Roots(big, listOf(big.mul(half, big.add(minusB, root)), big.mul(half, big.sub(minusB, root))))
        }

        /** √δ for δ ∈ Q(√m), δ = d₀ + d₁√m: a field holding it, and √δ there (checked: its square is δ). */
        private fun sqrtIn(field: MultiQuad, delta: MultiQuad.Element): Pair<MultiQuad, MultiQuad.Element>? {
            // δ = d₀ + d₁·√m with √m one element of the field (m a product of its generators).
            val terms = delta.c.filterValues { it.signum != 0 }
            if (terms.size > 2 || terms.keys.count { it != 0 } > 1) return null
            val d0 = terms[0] ?: Rational.ZERO
            val mask = terms.keys.firstOrNull { it != 0 }
            if (mask == null) {
                // √d₀: a field with the primes of d₀ too.
                val (_, primes) = squareFree(d0)
                val big = MultiQuad((field.generators + primes).distinct().sorted())
                val r = big.sqrtOf(d0) ?: return null
                return big to r
            }
            val d1 = terms.getValue(mask)
            var m = Rational.ONE
            for (g in field.generators.indices) if (mask and (1 shl g) != 0) m = m * Rational.of(field.generators[g])
            // √(d₀ + d₁√m) = √u + sgn(d₁)·√v with u, v = (d₀ ± n)/2 where n² = d₀² − m·d₁².
            val norm = d0 * d0 - m * d1 * d1
            val n = norm.root(2) ?: return null
            val two = Rational.of(2)
            val u = (d0 + n) / two
            val v = (d0 - n) / two
            val primes = (squareFree(u).second + squareFree(v).second)
            val big = MultiQuad((field.generators + primes).distinct().sorted())
            val ru = big.sqrtOf(u) ?: return null
            val rv = big.sqrtOf(v) ?: return null
            val r = if (d1.signum > 0) big.add(ru, rv) else big.sub(ru, rv)
            // Checked: r² = δ.
            if (!big.equal(big.mul(r, r), lift(field, big, delta))) return null
            return big to r
        }

        /** An element of [from] in [to], whose generators include [from]'s. */
        fun lift(from: MultiQuad, to: MultiQuad, a: MultiQuad.Element): MultiQuad.Element {
            val out = HashMap<Int, Rational>()
            for ((m, v) in a.c) {
                var mask = 0
                for (g in from.generators.indices) if (m and (1 shl g) != 0) mask = mask or (1 shl to.generators.indexOf(from.generators[g]))
                out[mask] = (out[mask] ?: Rational.ZERO) + v
            }
            return MultiQuad.Element(out.filterValues { it.signum != 0 })
        }

        private fun rationalNear(z: Cx): Rational? {
            if (abs(z.im) > 1e-7 * maxOf(1.0, abs(z.re))) return null
            return Rational.near(z.re, 100_000, 1e-9 * maxOf(1.0, abs(z.re)))
        }
    }

    /** Complex roots of a polynomial with double coefficients (Durand–Kerner), lowest power first. */
    internal object Numbers {
        fun roots(c: List<Double>): List<Cx>? {
            val n = c.size - 1
            if (n < 1) return null
            val lead = c.last()
            val a = c.map { it / lead }
            var z = List(n) { k -> Cx(0.4, 0.9).let { base -> var p = Cx(1.0); repeat(k) { p *= base }; p } }
            repeat(500) {
                Budget.check()
                var change = 0.0
                z = z.mapIndexed { i, zi ->
                    var value = Cx(1.0)
                    for (k in n - 1 downTo 0) value = value * zi + Cx(a[k])
                    var den = Cx(1.0)
                    for ((j, zj) in z.withIndex()) if (j != i) den *= (zi - zj)
                    val next = zi - value / den
                    change = maxOf(change, (next - zi).abs)
                    next
                }
                if (change < 1e-15) return z
            }
            return z.takeIf { it.all { r -> r.finite } }
        }
    }
}

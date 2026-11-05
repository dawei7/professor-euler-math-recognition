package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.math.sqrt as dsqrt

/**
 * Exact roots of cubics and quartics with rational coefficients that do not factor over the rationals: Cardano's formula with
 * real cube roots where one root is real, the trigonometric form where all three are (casus irreducibilis, which radicals of
 * real numbers cannot write), biquadratics through their square, nested roots taken apart where they come apart
 * (√(5 + 2√6) = √3 + √2), and Ferrari's method where the resolvent cubic has a rational root. Where a quartic has no form worth
 * reading, null: its roots are then found numerically. The caller checks every root in the equation as written.
 */
internal object Roots {
    private val THIRD = num(Rational.ONE / Rational.of(3))

    /** The real and the non-real roots of Σ cₖ xᵏ (degree three or four), or null. */
    fun exact(c: List<Rational>): Pair<List<Expr>, List<Expr>>? = when (c.size - 1) {
        3 -> cubic(c)
        4 -> quartic(c)
        else -> null
    }

    private fun cubic(c: List<Rational>): Pair<List<Expr>, List<Expr>>? {
        val (d, cc, b, a) = c
        val three = Rational.of(3)
        // x = t − b/(3a): t³ + p t + q = 0.
        val p = (three * a * cc - b * b) / (three * a * a)
        val q = (Rational.of(2) * b * b * b - Rational.of(9) * a * b * cc + Rational.of(27) * a * a * d) / (Rational.of(27) * a * a * a)
        val shift = num(-b / (three * a))
        val half = q / Rational.of(2)
        val disc = half * half + (p / three) * (p / three) * (p / three)
        return when {
            disc.signum > 0 -> {
                val s = sqrt(num(disc))
                val u = realCube(add(num(-half), s))
                val v = realCube(add(num(-half), neg(s)))
                val real = add(u, v, shift)
                // The other two: −(u + v)/2 ± i·(√3/2)(u − v).
                val re = add(mul(num(Rational.of(-1) / Rational.of(2)), add(u, v)), shift)
                val im = mul(num(Rational.ONE / Rational.of(2)), sqrt(num(three)), add(u, neg(v)))
                listOf(real) to listOf(add(re, mul(Expr.MINUS_ONE, Expr.I, im)), add(re, mul(Expr.I, im)))
            }
            disc.signum < 0 -> {
                // Three real roots: 2√(−p/3)·cos(⅓·arccos((3q/2p)·√(−3/p)) − 2πk/3) − b/(3a).
                val r = mul(Expr.TWO, sqrt(num(-p / three)))
                val angle = mul(THIRD, fn(Func.ACOS, mul(num(three * q / (Rational.of(2) * p)), sqrt(num(-three / p)))))
                (0..2).map { k ->
                    add(mul(r, fn(Func.COS, add(angle, mul(num(Rational.of(-2L * k) / three), Expr.PI)))), shift)
                } to emptyList()
            }
            else -> null
        }
    }

    /** ∛ of a real number, real: −∛|a| for a negative one. */
    private fun realCube(e: Expr): Expr {
        val v = Numeric.value(e) ?: return pow(e, THIRD)
        if (v == 0.0) return Expr.ZERO
        return if (v < 0) neg(pow(neg(e), THIRD)) else pow(e, THIRD)
    }

    private fun quartic(c0: List<Rational>): Pair<List<Expr>, List<Expr>>? {
        val lead = c0[4]
        val c = c0.map { it / lead }
        val (a0, a1, a2, a3) = c
        // x = y − a3/4: y⁴ + p y² + q y + r = 0.
        val p = a2 - Rational.of(3) * a3 * a3 / Rational.of(8)
        val q = a1 - a3 * a2 / Rational.of(2) + a3 * a3 * a3 / Rational.of(8)
        val r = a0 - a3 * a1 / Rational.of(4) + a3 * a3 * a2 / Rational.of(16) - Rational.of(3) * a3 * a3 * a3 * a3 / Rational.of(256)
        val shift = num(-a3 / Rational.of(4))
        val real = ArrayList<Expr>()
        val complex = ArrayList<Expr>()
        fun keep(y: Expr) {
            val v = add(y, shift)
            if (Numeric.value(v) != null) real += v else complex += v
        }
        if (q.signum == 0) {
            // A biquadratic: y² = (−p ± √(p² − 4r))/2.
            val disc = p * p - Rational.of(4) * r
            if (disc.signum < 0) return null
            for (sign in listOf(-1, 1)) {
                val u = add(num(-p / Rational.of(2)), mul(num(Rational.of(sign.toLong()) / Rational.of(2)), sqrt(num(disc))))
                val uv = Numeric.value(u) ?: return null
                val root = denested(-p / Rational.of(2), Rational.of(sign.toLong()) / Rational.of(2), disc) ?: sqrt(u)
                if (uv >= 0) {
                    keep(root)
                    keep(neg(root))
                } else {
                    val im = denested(p / Rational.of(2), Rational.of(-sign.toLong()) / Rational.of(2), disc) ?: sqrt(neg(u))
                    keep(mul(Expr.I, im))
                    keep(mul(Expr.MINUS_ONE, Expr.I, im))
                }
            }
            return real to complex
        }
        // Ferrari: (y² + m)² = (2m − p)y² − q y + m² − r is a square for m a root of 8m³ − 4p m² − 8r m + 4pr − q² = 0.
        val m = rationalRoot(listOf(Rational.of(4) * p * r - q * q, Rational.of(-8) * r, Rational.of(-4) * p, Rational.of(8)))
            ?.takeIf { (Rational.of(2) * it - p).signum > 0 } ?: return null
        val s = sqrt(num(Rational.of(2) * m - p))
        // y² ∓ s·y + m ± q/(2s) = 0.
        for (sign in listOf(1, -1)) {
            val b = mul(num(Rational.of(-sign.toLong())), s)
            val k = add(num(m), mul(num(Rational.of(sign.toLong()) * q / Rational.of(2)), pow(s, Expr.MINUS_ONE)))
            val disc = Algebra.expand(add(mul(b, b), mul(num(Rational.of(-4)), k)))
            val dv = Numeric.value(disc) ?: return null
            val root = if (dv >= 0) sqrt(disc) else mul(Expr.I, sqrt(neg(disc)))
            keep(mul(num(Rational.ONE / Rational.of(2)), add(neg(b), neg(root))))
            keep(mul(num(Rational.ONE / Rational.of(2)), add(neg(b), root)))
        }
        return real to complex
    }

    /**
     * √(a + b√d) as √x + √y (or √x − √y) where it comes apart: x + y = a and 4xy = b²d, so x and y are the roots of
     * t² − a·t + b²d/4, rational when a² − b²d is a rational square. Null where it does not.
     */
    internal fun denested(a: Rational, b: Rational, d: Rational): Expr? {
        val e = a * a - b * b * d
        if (e.signum < 0) return null
        val w = rationalSqrt(e) ?: return null
        val x = (a + w) / Rational.of(2)
        val y = (a - w) / Rational.of(2)
        if (x.signum < 0 || y.signum < 0) return null
        val first = sqrt(num(x))
        val second = sqrt(num(y))
        return if (b.signum >= 0) add(first, second) else add(first, neg(second))
    }

    private fun rationalSqrt(r: Rational): Rational? {
        if (r.signum < 0) return null
        val n = isqrtExact(r.num) ?: return null
        val d = isqrtExact(r.den) ?: return null
        return Rational.of(n, d)
    }

    private fun isqrtExact(n: BigInteger): BigInteger? {
        if (n.signum() < 0) return null
        if (n.signum() == 0) return BigInteger.ZERO
        var x = BigInteger.valueOf(dsqrt(n.toDouble()).toLong())
        // Newton to the floor of the root, then check.
        repeat(200) {
            if (x.signum() == 0) x = BigInteger.ONE
            val next = (x + n / x).shiftRight(1)
            if ((next - x).abs() <= BigInteger.ONE) {
                for (cand in listOf(next - BigInteger.ONE, next, next + BigInteger.ONE)) if (cand.signum() >= 0 && cand * cand == n) return cand
                return null
            }
            x = next
        }
        return null
    }

    /** A rational root of Σ cₖ xᵏ (rational coefficients), by the rational root test on its integer form, or null. */
    private fun rationalRoot(c: List<Rational>): Rational? {
        val den = c.fold(BigInteger.ONE) { acc, r -> lcm(acc, r.den) }
        val ints = c.map { (it * Rational.of(den, BigInteger.ONE)).num }
        if (ints.last().signum() == 0) return null
        if (ints.first().signum() == 0) return Rational.ZERO
        val p = divisors(ints.first().abs()) ?: return null
        val qs = divisors(ints.last().abs()) ?: return null
        for (num in p) for (d in qs) for (sign in listOf(1, -1)) {
            val r = Rational.of(num * BigInteger.valueOf(sign.toLong()), d)
            if (c.asReversed().fold(Rational.ZERO) { acc, k -> acc * r + k }.signum == 0) return r
        }
        return null
    }

    private fun lcm(a: BigInteger, b: BigInteger): BigInteger = a / a.gcd(b) * b

    /** The divisors of n, or null where n is too large to try them all. */
    private fun divisors(n: BigInteger): List<BigInteger>? {
        if (n.bitLength() > 40) return null
        val v = n.toLong()
        val out = ArrayList<BigInteger>()
        var k = 1L
        while (k * k <= v) {
            if (v % k == 0L) {
                out += BigInteger.valueOf(k)
                if (k != v / k) out += BigInteger.valueOf(v / k)
            }
            k++
            if (k > 1_000_000) return null
        }
        return out
    }
}

/**
 * The real roots of a polynomial with rational coefficients, certain: Sturm's sequence counts the distinct real roots in any
 * interval exactly, bisection with exact rationals isolates each in an interval of its own, and narrows it to the last bit of
 * a double. None is missed and none invented, however close two of them lie.
 */
internal object Sturm {
    /** The distinct real roots of Σ cₖ xᵏ, each to a double's precision, smallest first. */
    fun realRoots(c0: List<Rational>): List<Double> {
        val c = c0.dropLastWhile { it.signum == 0 }
        if (c.size <= 1) return emptyList()
        val p = squareFree(c)
        if (p.size <= 1) return emptyList()
        val chain = chain(p)
        // Every root lies within 1 + max |cₖ/cₙ| (Cauchy).
        val lead = p.last()
        val bound = Rational.ONE + p.dropLast(1).fold(Rational.ZERO) { m, k -> maxOf(m, abs(k / lead)) }
        val out = ArrayList<Double>()
        isolate(chain, -bound, bound, out, 0)
        return out.sorted()
    }

    private fun abs(r: Rational) = if (r.signum < 0) -r else r

    /** The double nearest to the rational: a decimal of 40 digits first, so the last bit is right. */
    private fun Rational.exactDouble(): Double = java.math.BigDecimal(num).divide(java.math.BigDecimal(den), java.math.MathContext(40)).toDouble()
    private fun maxOf(a: Rational, b: Rational) = if (a.compareTo(b) >= 0) a else b

    private fun isolate(chain: List<List<Rational>>, a: Rational, b: Rational, out: MutableList<Double>, depth: Int) {
        Budget.check()
        val n = changes(chain, a) - changes(chain, b)
        if (n == 0 || depth > 200) return
        val p = chain[0]
        if (n == 1) {
            out += refine(p, a, b)
            return
        }
        val mid = (a + b) / Rational.of(2)
        // A root at the midpoint itself: kept, and the halves searched beside it.
        if (value(p, mid).signum == 0) {
            out += mid.exactDouble()
            val eps = (b - a) / Rational.of(1L shl 20)
            isolate(chain, a, mid - eps, out, depth + 1)
            isolate(chain, mid + eps, b, out, depth + 1)
            return
        }
        isolate(chain, a, mid, out, depth + 1)
        isolate(chain, mid, b, out, depth + 1)
    }

    /** The one root in (a, b], by bisection on the sign of p, to a double's last bit. */
    private fun refine(p: List<Rational>, a0: Rational, b0: Rational): Double {
        var a = a0
        var b = b0
        var sa = value(p, a).signum
        if (value(p, b).signum == 0) return b.exactDouble()
        repeat(160) {
            val mid = (a + b) / Rational.of(2)
            val sm = value(p, mid).signum
            if (sm == 0) return mid.exactDouble()
            if (sm == sa) { a = mid; sa = sm } else b = mid
            // The interval well within one double's step: the double nearest its middle is the root's.
            val da = a.exactDouble()
            if ((b - a).exactDouble() < Math.ulp(da) / 16) return ((a + b) / Rational.of(2)).exactDouble()
        }
        return ((a + b) / Rational.of(2)).exactDouble()
    }

    private fun changes(chain: List<List<Rational>>, x: Rational): Int {
        var last = 0
        var count = 0
        for (q in chain) {
            val s = value(q, x).signum
            if (s == 0) continue
            if (last != 0 && s != last) count++
            last = s
        }
        return count
    }

    private fun value(p: List<Rational>, x: Rational): Rational = p.asReversed().fold(Rational.ZERO) { acc, k -> acc * x + k }

    private fun derivative(p: List<Rational>): List<Rational> = (1 until p.size).map { k -> p[k] * Rational.of(k.toLong()) }

    /** p's Sturm sequence: p, p′, then the negated remainders. */
    private fun chain(p: List<Rational>): List<List<Rational>> {
        val out = arrayListOf(p, derivative(p))
        while (out.last().size > 1 || (out.last().size == 1 && out.last()[0].signum != 0)) {
            val r = remainder(out[out.size - 2], out.last()).map { -it }
            if (r.isEmpty() || r.all { it.signum == 0 }) break
            out += r
            if (out.size > 64) break
        }
        return out
    }

    private fun remainder(a: List<Rational>, b: List<Rational>): List<Rational> {
        val d = b.dropLastWhile { it.signum == 0 }
        val r = a.dropLastWhile { it.signum == 0 }.toMutableList()
        while (r.size >= d.size && r.isNotEmpty()) {
            val shift = r.size - d.size
            val k = r.last() / d.last()
            for ((i, c) in d.withIndex()) r[i + shift] = r[i + shift] - c * k
            while (r.isNotEmpty() && r.last().signum == 0) r.removeAt(r.size - 1)
        }
        return r
    }

    /** p over gcd(p, p′): the same roots, each once. */
    private fun squareFree(p: List<Rational>): List<Rational> {
        val g = gcd(p, derivative(p))
        if (g.size <= 1) return p
        return quotient(p, g)
    }

    private fun gcd(a0: List<Rational>, b0: List<Rational>): List<Rational> {
        var a = a0.dropLastWhile { it.signum == 0 }
        var b = b0.dropLastWhile { it.signum == 0 }
        while (b.isNotEmpty()) {
            val r = remainder(a, b)
            a = b
            b = r
        }
        return a
    }

    private fun quotient(a: List<Rational>, b: List<Rational>): List<Rational> {
        val d = b.dropLastWhile { it.signum == 0 }
        val r = a.dropLastWhile { it.signum == 0 }.toMutableList()
        val q = MutableList(maxOf(1, r.size - d.size + 1)) { Rational.ZERO }
        while (r.size >= d.size && r.isNotEmpty()) {
            val shift = r.size - d.size
            val k = r.last() / d.last()
            q[shift] = k
            for ((i, c) in d.withIndex()) r[i + shift] = r[i + shift] - c * k
            while (r.isNotEmpty() && r.last().signum == 0) r.removeAt(r.size - 1)
        }
        return q
    }
}

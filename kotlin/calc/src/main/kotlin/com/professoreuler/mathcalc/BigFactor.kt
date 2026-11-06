package com.professoreuler.mathcalc

import java.math.BigInteger

/**
 * Factors of large whole numbers (up to about 40 digits within a calculation's time) and certificates of their primes.
 *
 * A composite is split by the first method that finds a factor: a perfect power by its root; Shanks' square forms
 * factorization (SQUFOF) below 2⁶²; Pollard's rho in Brent's form for small factors; and Lenstra's elliptic curve method
 * (ECM) for factors of up to about 20 digits, on Montgomery curves with Suyama's parametrization, a first stage by the prime
 * powers up to B1 and a second stage for one prime up to B2 in the form of Crandall and Pomerance's "inversionless ECM"
 * (Prime Numbers, algorithm 7.4.4). Every method is written from its published description.
 *
 * A prime beyond the bound where Miller–Rabin with fixed bases is certain is certified by the Pocklington–Lehmer theorem on
 * a factored part F of p − 1 (F² > p), or, where F³ ≥ p, by Brillhart, Lehmer and Selfridge's test on p written in base F;
 * the primes of F are certified the same way, down to the bound.
 */
internal object BigFactor {
    private val ONE = BigInteger.ONE
    private val TWO = BigInteger.valueOf(2)

    /**
     * A factor d of the composite [m] (odd, without prime factors below 10⁴), 1 < d < m; stops with [Failure.TOO_LONG] when
     * the calculation's time is over. A number function is worked out where the parser reads it, before the calculation's
     * time starts: there a time of its own ([MathCalc.LIMIT_MS], [Budget.bounded]) bounds it, so ECM never runs on without end.
     */
    fun split(m: BigInteger): BigInteger = Budget.bounded(MathCalc.LIMIT_MS) {
        perfectPower(m) ?: (if (m.bitLength() <= 62) squfof(m.toLong())?.let { BigInteger.valueOf(it) } else null)
            ?: rho(m, 1 shl 16) ?: ecm(m)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Perfect powers.

    /** r with r^k = m for some k ≥ 2 (the root of the highest power), or null. */
    fun perfectPower(m: BigInteger): BigInteger? {
        for (k in m.bitLength() downTo 2) {
            val r = root(m, k)
            if (r > ONE && r.pow(k) == m) return r
        }
        return null
    }

    /** ⌊m^(1/k)⌋ for m ≥ 0, by Newton's method in whole numbers. */
    fun root(m: BigInteger, k: Int): BigInteger {
        if (m.signum() == 0) return m
        if (k == 1) return m
        val kk = BigInteger.valueOf(k.toLong())
        // A start above the root: 2^⌈bits/k⌉.
        var x = ONE.shiftLeft((m.bitLength() + k - 1) / k)
        while (true) {
            val y = (x * (kk - ONE) + m / x.pow(k - 1)) / kk
            if (y >= x) break
            x = y
        }
        while (x.pow(k) > m) x -= ONE
        while ((x + ONE).pow(k) <= m) x += ONE
        return x
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Shanks' square forms factorization.

    /** Multipliers k for SQUFOF: square-free products of small odd primes, as Gower and Wagstaff suggest. */
    private val MULTIPLIERS = longArrayOf(1, 3, 5, 7, 11, 15, 21, 33, 35, 55, 77, 105, 165, 231, 385, 1155)

    /** A factor of the odd composite [n] < 2⁶², or null where no multiplier gives one. */
    fun squfof(n: Long): Long? {
        val s = isqrt(n)
        if (s * s == n) return s
        for (k in MULTIPLIERS) {
            if (n > Long.MAX_VALUE / 4 / k) break
            val f = squfofWith(n, k)
            if (f != null) return f
        }
        return null
    }

    private fun squfofWith(n: Long, k: Long): Long? {
        val d = k * n
        val p0 = isqrt(d)
        if (p0 * p0 == d) return gcd(n, p0).takeIf { it != 1L && it != n }
        // The forward cycle: continued fraction of √(kN) until Q at an even step is a square.
        var pPrev = p0
        var p = p0
        var qPrev = 1L
        var q = d - p0 * p0
        val limit = 6 * isqrt(2 * isqrt(d)) + 100
        var i = 1
        var r = 0L
        while (true) {
            if (i > limit) return null
            if (i % 256 == 0) Budget.check()
            val b = (p0 + p) / q
            p = b * q - p
            val qNext = qPrev + b * (pPrev - p)
            qPrev = q
            q = qNext
            pPrev = p
            i++
            if (i % 2 == 0) {
                val t = isqrt(q)
                if (t * t == q) { r = t; break }
            }
        }
        // The reverse cycle from the square form, until P repeats.
        val b0 = (p0 - p) / r
        p = b0 * r + p
        qPrev = r
        q = (d - p * p) / qPrev
        var j = 0
        while (true) {
            if (j > limit) return null
            if (j % 256 == 0) Budget.check()
            val b = (p0 + p) / q
            val pNext = b * q - p
            val qNext = qPrev + b * (p - pNext)
            qPrev = q
            q = qNext
            val repeated = pNext == p
            p = pNext
            j++
            if (repeated) break
        }
        val f = gcd(n, qPrev)
        return f.takeIf { it != 1L && it != n }
    }

    private fun isqrt(n: Long): Long {
        var r = kotlin.math.sqrt(n.toDouble()).toLong()
        while (r * r > n) r--
        while ((r + 1) * (r + 1) <= n) r++
        return r
    }

    private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) kotlin.math.abs(a) else gcd(b, a % b)

    // ---------------------------------------------------------------------------------------------------------------------
    // Pollard's rho, Brent's form.

    /** A factor of [m] by Pollard's rho in Brent's form, within about [most] steps, or null. */
    fun rho(m: BigInteger, most: Int): BigInteger? {
        for (c0 in 1L..3L) {
            val c = BigInteger.valueOf(c0)
            var y = BigInteger.valueOf(2)
            var x = y
            var ys = y
            var q = ONE
            var g = ONE
            var r = 1
            var steps = 0
            fun f(v: BigInteger) = (v * v + c).mod(m)
            while (g == ONE && steps < most) {
                x = y
                repeat(r) { y = f(y) }
                var k = 0
                while (k < r && g == ONE) {
                    Budget.check()
                    ys = y
                    repeat(minOf(64, r - k)) {
                        y = f(y)
                        q = (q * (x - y).abs()).mod(m)
                    }
                    g = q.gcd(m)
                    k += 64
                }
                steps += r
                r *= 2
            }
            if (g == m) {
                var tries = 0
                do { ys = f(ys); g = (x - ys).abs().gcd(m); tries++ } while (g == ONE && tries < 4096)
            }
            if (g != m && g != ONE) return g
        }
        return null
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Lenstra's elliptic curve method.

    /** The stages' bounds and how many curves each gets before the next: factors of 15, 20 and 25 digits. */
    private val SCHEDULE = listOf(Triple(2_000, 200_000, 25), Triple(11_000, 1_100_000, 90), Triple(50_000, 2_500_000, Int.MAX_VALUE))

    /** A factor of the composite [m] by ECM; stops with [Failure.TOO_LONG] when the time is over. */
    fun ecm(m: BigInteger): BigInteger {
        val mont = Montgomery(m)
        var sigma = 6L
        for ((b1, b2, curves) in SCHEDULE) {
            var k = 0
            while (k < curves) {
                Budget.check()
                sigma++
                k++
                val found = curve(mont, sigma, b1, b2)
                if (found != null) return found
            }
        }
        throw CalcException(Failure.TOO_LONG)
    }

    /** One curve with Suyama's σ: a factor of n, or null. */
    private fun curve(mont: Montgomery, sigma: Long, b1: Int, b2: Int): BigInteger? {
        val n = mont.n
        val s = BigInteger.valueOf(sigma)
        val u = (s * s - BigInteger.valueOf(5)).mod(n)
        val v = (s * BigInteger.valueOf(4)).mod(n)
        val x0 = u.modPow(BigInteger.valueOf(3), n)
        val z0 = v.modPow(BigInteger.valueOf(3), n)
        // A24 = (a + 2)/4 = (v − u)³(3u + v) / (16u³v).
        val den = (BigInteger.valueOf(16) * x0 * v).mod(n)
        val g = den.gcd(n)
        if (g != ONE) return g.takeIf { it != n }
        val a24 = ((v - u).modPow(BigInteger.valueOf(3), n) * (BigInteger.valueOf(3) * u + v) * den.modInverse(n)).mod(n)
        val e = Ecm(mont, mont.toMont(a24))
        val p = e.point(mont.toMont(x0), mont.toMont(z0))
        // Stage 1: the point times every prime power up to B1.
        val primes = Primes.upTo(b2)
        for (q in primes) {
            if (q > b1) break
            var power = q.toLong()
            while (power * q <= b1) power *= q
            e.times(p, power)
        }
        val g1 = mont.value(p.z).gcd(n)
        if (g1 != ONE) return g1.takeIf { it != n }
        return e.stageTwo(p, b1, b2, primes)?.takeIf { it != ONE && it != n }
    }

    /** The sieve of Eratosthenes, kept for the largest bound asked. */
    private object Primes {
        @Volatile private var cached: IntArray = IntArray(0)
        @Volatile private var limit = 0

        fun upTo(n: Int): IntArray {
            if (n <= limit) return cached
            synchronized(this) {
                if (n <= limit) return cached
                val composite = BooleanArray(n + 1)
                val out = ArrayList<Int>(n / 10)
                for (i in 2..n) {
                    if (composite[i]) continue
                    out += i
                    var j = i.toLong() * i
                    while (j <= n) { composite[j.toInt()] = true; j += i }
                }
                cached = out.toIntArray()
                limit = n
                return cached
            }
        }
    }

    /** A point (X : Z) of a Montgomery curve, x-only, in Montgomery form. */
    private class Point(val x: LongArray, val z: LongArray) {
        fun set(o: Point) { o.x.copyInto(x); o.z.copyInto(z) }
    }

    /** Arithmetic on the curve B·y² = x³ + a·x² + x through A24 = (a + 2)/4, without inversions. */
    private class Ecm(val m: Montgomery, val a24: LongArray) {
        private val k = m.k
        private val t1 = LongArray(k)
        private val t2 = LongArray(k)
        private val t3 = LongArray(k)
        private val t4 = LongArray(k)

        fun point(x: LongArray, z: LongArray) = Point(x.copyOf(), z.copyOf())
        fun point() = Point(LongArray(k), LongArray(k))

        /** out = 2·p. */
        fun double(p: Point, out: Point) {
            m.add(p.x, p.z, t1)
            m.square(t1, t1)
            m.sub(p.x, p.z, t2)
            m.square(t2, t2)
            m.sub(t1, t2, t3)
            m.mul(t1, t2, out.x)
            m.mul(a24, t3, t4)
            m.add(t4, t2, t4)
            m.mul(t3, t4, out.z)
        }

        /** out = p + q, where p − q = d. */
        fun add(p: Point, q: Point, d: Point, out: Point) {
            m.sub(p.x, p.z, t1)
            m.add(q.x, q.z, t2)
            m.mul(t1, t2, t1)
            m.add(p.x, p.z, t2)
            m.sub(q.x, q.z, t3)
            m.mul(t2, t3, t2)
            m.add(t1, t2, t3)
            m.sub(t1, t2, t4)
            m.square(t3, t3)
            m.square(t4, t4)
            m.mul(d.z, t3, t1)
            m.mul(d.x, t4, out.z)
            t1.copyInto(out.x)
        }

        private val r0 = point()
        private val r1 = point()
        private val tmp = point()
        private val base = point()

        /** p = s·p, by Montgomery's ladder. */
        fun times(p: Point, s: Long) {
            if (s == 1L) return
            base.set(p)
            r0.set(p)
            double(p, r1)
            for (bit in 62 - java.lang.Long.numberOfLeadingZeros(s) downTo 0) {
                if ((s shr bit) and 1L == 1L) {
                    add(r1, r0, base, tmp); r0.set(tmp)
                    double(r1, tmp); r1.set(tmp)
                } else {
                    add(r1, r0, base, tmp); r1.set(tmp)
                    double(r0, tmp); r0.set(tmp)
                }
            }
            p.set(r0)
        }

        /** The second stage for one prime q in (B1, B2]: gcd(∏ (X_R·Z_S − X_S·Z_R), n) over [q]Q = R + S. */
        fun stageTwo(q: Point, b1: Int, b2: Int, primes: IntArray): BigInteger? {
            val d = maxOf(16, kotlin.math.sqrt(b2 / 2.0).toInt())
            // S_δ = [2δ]Q for δ = 1 … D, and β_δ = X(S_δ)·Z(S_δ).
            val s = Array(d + 1) { point() }
            val beta = Array(d + 1) { LongArray(k) }
            double(q, s[1])
            if (d >= 2) double(s[1], s[2])
            for (j in 3..d) add(s[j - 1], s[1], s[j - 2], s[j])
            for (j in 1..d) m.mul(s[j].x, s[j].z, beta[j])
            // R = [B]Q and T = [B − 2D]Q, B odd above 2D.
            var b = (if (b1 % 2 == 0) b1 - 1 else b1).toLong()
            if (b <= 2L * d) b = 2L * d + 1
            val r = point(q.x, q.z)
            times(r, b)
            val t = point(q.x, q.z)
            times(t, b - 2L * d)
            val g = m.one()
            val alpha = LongArray(k)
            val u = LongArray(k)
            val w = LongArray(k)
            val next = point()
            var index = primes.binarySearch((b + 1).toInt()).let { if (it < 0) -it - 1 else it }
            var low = b
            while (low < b2) {
                Budget.check()
                m.mul(r.x, r.z, alpha)
                val high = low + 2L * d
                while (index < primes.size && primes[index] <= high) {
                    val p = primes[index]
                    if (p > b2) break
                    val delta = ((p - low) / 2).toInt()
                    m.sub(r.x, s[delta].x, u)
                    m.add(r.z, s[delta].z, w)
                    m.mul(u, w, u)
                    m.sub(u, alpha, u)
                    m.add(u, beta[delta], u)
                    m.mul(g, u, g)
                    index++
                }
                add(r, s[d], t, next)
                t.set(r)
                r.set(next)
                low = high
            }
            return m.value(g).gcd(m.n)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Certified primes.

    /**
     * Whether the probable prime [p] is prime, proved: true with a certificate (Pocklington–Lehmer, or Brillhart–Lehmer–
     * Selfridge), false where p is composite after all. Stops with [Failure.TOO_LONG] where p − 1 cannot be factored far
     * enough in time (its own [MathCalc.LIMIT_MS] where no calculation's time runs, as in a number function the parser works out).
     */
    fun certify(p: BigInteger): Boolean = Budget.bounded(MathCalc.LIMIT_MS) { proof(p) }

    private fun proof(p: BigInteger): Boolean {
        val nm1 = p - ONE
        // F's primes with their powers in p − 1, and the parts of p − 1 not yet taken apart.
        val primes = HashMap<BigInteger, Int>()
        val parts = ArrayList<BigInteger>()
        var rest = nm1
        var q = 2L
        while (q < 10_000) {
            val bq = BigInteger.valueOf(q)
            while (rest.mod(bq).signum() == 0) { primes[bq] = (primes[bq] ?: 0) + 1; rest /= bq }
            q += if (q == 2L) 1 else 2
        }
        if (rest > ONE) parts += rest
        while (true) {
            Budget.check()
            val f = primes.entries.fold(ONE) { acc, (r, e) -> acc * r.pow(e) }
            when (proved(p, f, primes.keys)) {
                true -> return true
                false -> return false
                null -> {}
            }
            // Take apart the smallest part left: a prime goes into F, a composite is split. With none left F = p − 1,
            // which decides; a prime without a base among the first ones is not certified in time.
            val part = parts.minOrNull() ?: throw CalcException(Failure.TOO_LONG)
            parts.remove(part)
            val root = perfectPower(part)
            if (root != null) {
                var e = 0
                var x = part
                while (x.mod(root).signum() == 0) { x /= root; e++ }
                repeat(e) { parts += root }
                continue
            }
            if (NumberTheory.isPrime(part)) {
                primes[part] = (primes[part] ?: 0) + 1
                // The same prime may stand in other parts.
                for (k in parts.indices) {
                    var x = parts[k]
                    while (x.mod(part).signum() == 0) { x /= part; primes[part] = primes.getValue(part) + 1 }
                    parts[k] = x
                }
                parts.removeAll { it == ONE }
            } else {
                val d = split(part)
                parts += d
                parts += part / d
            }
        }
    }

    /**
     * With F's prime factors [qs] known: true where p is proved prime, false where it is shown composite, null where F is too
     * small to decide.
     */
    private fun proved(p: BigInteger, f: BigInteger, qs: Set<BigInteger>): Boolean? {
        val enough = f * f > p
        val cube = f.pow(3) >= p
        if (!enough && !cube) return null
        val nm1 = p - ONE
        // For each prime q of F a base a with a^(p−1) ≡ 1 and gcd(a^((p−1)/q) − 1, p) = 1 (for a prime p, all but a
        // q-th of the bases have it).
        for (q in qs) {
            var found = false
            for (a in 2L..2_000L) {
                Budget.check()
                val ba = BigInteger.valueOf(a)
                if (ba.modPow(nm1, p) != ONE) return false
                val g = (ba.modPow(nm1 / q, p) - ONE).gcd(p)
                if (g == ONE) { found = true; break }
                if (g != p) return false
            }
            if (!found) return null
        }
        if (enough) return true
        // F³ ≥ p: p = c₂F² + c₁F + 1; a composite p would make c₁² − 4c₂ a square.
        val r = nm1 / f
        val c2 = r / f
        val c1 = r.mod(f)
        val disc = c1 * c1 - BigInteger.valueOf(4) * c2
        if (disc.signum() < 0) return true
        val s = NumberTheory.isqrt(disc)
        return if (s * s == disc) null else true
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Montgomery's arithmetic modulo an odd n.

    /**
     * Numbers modulo the odd [n] in Montgomery's form x·R mod n, R = 2^(30k), as k limbs of 30 bits (lowest first): a product
     * a·b·R⁻¹ mod n without a division (the CIOS method), on 64-bit words, as Android's Java has no 128-bit product.
     */
    class Montgomery(val n: BigInteger) {
        val k = (n.bitLength() + 29) / 30
        private val mod = limbs(n)
        private val inv: Long
        private val r2: LongArray
        private val t = LongArray(k + 2)

        init {
            require(n.testBit(0) && n > ONE)
            // −n⁻¹ mod 2³⁰ by Newton's iteration: each step doubles the bits that are right.
            val n0 = mod[0]
            var x = n0
            repeat(5) { x = (x * ((2 - ((n0 * x) and MASK)) and MASK)) and MASK }
            inv = (-x) and MASK
            r2 = limbs(ONE.shiftLeft(60 * k).mod(n))
        }

        fun limbs(v: BigInteger): LongArray {
            val out = LongArray(k)
            var x = v
            for (i in 0 until k) {
                out[i] = x.toLong() and MASK
                x = x.shiftRight(30)
            }
            return out
        }

        /** The limbs as a number. */
        fun value(a: LongArray): BigInteger {
            var out = BigInteger.ZERO
            for (i in k - 1 downTo 0) out = out.shiftLeft(30).or(BigInteger.valueOf(a[i]))
            return out
        }

        fun toMont(v: BigInteger): LongArray = LongArray(k).also { mul(limbs(v.mod(n)), r2, it) }
        fun fromMont(a: LongArray): BigInteger = value(LongArray(k).also { mul(a, LongArray(k).also { o -> o[0] = 1 }, it) })
        fun one(): LongArray = toMont(ONE)

        /** out = a·b·R⁻¹ mod n (out may be a or b). */
        fun mul(a: LongArray, b: LongArray, out: LongArray) {
            val t = t
            java.util.Arrays.fill(t, 0L)
            for (i in 0 until k) {
                val ai = a[i]
                var c = 0L
                for (j in 0 until k) {
                    val s = t[j] + ai * b[j] + c
                    t[j] = s and MASK
                    c = s ushr 30
                }
                var s = t[k] + c
                t[k] = s and MASK
                t[k + 1] += s ushr 30
                val q = (t[0] * inv) and MASK
                s = t[0] + q * mod[0]
                c = s ushr 30
                for (j in 1 until k) {
                    s = t[j] + q * mod[j] + c
                    t[j - 1] = s and MASK
                    c = s ushr 30
                }
                s = t[k] + c
                t[k - 1] = s and MASK
                c = s ushr 30
                t[k] = t[k + 1] + c
                t[k + 1] = 0
            }
            // t < 2n: one subtraction at most.
            if (t[k] != 0L || !less(t, mod)) {
                var borrow = 0L
                for (j in 0 until k) {
                    val s = t[j] - mod[j] - borrow
                    out[j] = s and MASK
                    borrow = if (s < 0) 1 else 0
                }
            } else {
                for (j in 0 until k) out[j] = t[j]
            }
        }

        fun square(a: LongArray, out: LongArray) = mul(a, a, out)

        /** out = a + b mod n. */
        fun add(a: LongArray, b: LongArray, out: LongArray) {
            var c = 0L
            for (j in 0 until k) {
                val s = a[j] + b[j] + c
                out[j] = s and MASK
                c = s ushr 30
            }
            if (c != 0L || !less(out, mod)) {
                var borrow = 0L
                for (j in 0 until k) {
                    val s = out[j] - mod[j] - borrow
                    out[j] = s and MASK
                    borrow = if (s < 0) 1 else 0
                }
            }
        }

        /** out = a − b mod n. */
        fun sub(a: LongArray, b: LongArray, out: LongArray) {
            var borrow = 0L
            for (j in 0 until k) {
                val s = a[j] - b[j] - borrow
                out[j] = s and MASK
                borrow = if (s < 0) 1 else 0
            }
            if (borrow != 0L) {
                var c = 0L
                for (j in 0 until k) {
                    val s = out[j] + mod[j] + c
                    out[j] = s and MASK
                    c = s ushr 30
                }
            }
        }

        /** Whether the first k limbs of a are below b's. */
        private fun less(a: LongArray, b: LongArray): Boolean {
            for (j in k - 1 downTo 0) {
                if (a[j] != b[j]) return a[j] < b[j]
            }
            return false
        }
    }

    private const val MASK = (1L shl 30) - 1
}

package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.random.Random

/**
 * Interpolation and number functions. The interpolating polynomial against Lagrange's formula in fractions; the natural cubic
 * spline against the test's own (its moments by the tridiagonal system in fractions); multiplicative orders, primitive roots and
 * discrete logarithms by stepping through the powers; factorizations up to 40 digits of products of primes chosen by Java's
 * BigInteger.probablePrime (each factor checked prime by BigInteger's own test).
 */
internal object WrittenResidues {
    private const val AREA = "residues and fits"

    private fun lagrange(pts: List<Pair<Q, Q>>, x: Q): Q = pts.indices.fold(Q.ZERO) { s, i ->
        s + pts[i].second * pts.indices.filter { it != i }.fold(Q.ONE) { p, j -> p * (x - pts[j].first) / (pts[i].first - pts[j].first) }
    }

    /** The natural cubic spline's value at x: moments M (M₀ = Mₙ = 0) from hᵢ₋₁Mᵢ₋₁ + 2(hᵢ₋₁ + hᵢ)Mᵢ + hᵢMᵢ₊₁ = 6(Δᵢ − Δᵢ₋₁). */
    private fun spline(pts: List<Pair<Q, Q>>): (Q) -> Q {
        val n = pts.size - 1
        val h = (0 until n).map { pts[it + 1].first - pts[it].first }
        val slope = (0 until n).map { (pts[it + 1].second - pts[it].second) / h[it] }
        val m = MutableList(n + 1) { Q.ZERO }
        if (n >= 2) {
            val rows = (1 until n).map { i ->
                (1 until n).map { j -> when (j) { i - 1 -> h[i - 1]; i -> q(2) * (h[i - 1] + h[i]); i + 1 -> h[i]; else -> Q.ZERO } } + listOf(q(6) * (slope[i] - slope[i - 1]))
            }
            val sol = QM.rref(rows).first.map { it[n - 1] }
            for (i in 1 until n) m[i] = sol[i - 1]
        }
        return { x ->
            val i = (0 until n).firstOrNull { x <= pts[it + 1].first } ?: (n - 1)
            val a = pts[i].first; val b = pts[i + 1].first; val hi = h[i]
            m[i] * (b - x).pow(3) / (q(6) * hi) + m[i + 1] * (x - a).pow(3) / (q(6) * hi) +
                (pts[i].second / hi - m[i] * hi / q(6)) * (b - x) + (pts[i + 1].second / hi - m[i + 1] * hi / q(6)) * (x - a)
        }
    }

    private fun fits(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(50) {
            val xs = (-6..6).shuffled(r).take(r.int(2, 6)).map { q(it.toLong(), listOf(1L, 1L, 2L).random(r)) }.distinct()
            if (xs.size < 2) return@repeat
            val pts = xs.map { it to q(r.int(-9, 9).toLong(), listOf(1L, 2L, 4L).random(r)) }
            val text = pts.joinToString(", ") { "(${decimalText(it.first)}, ${decimalText(it.second)})" }
            out += Task(AREA, "interpolate($text)", "Lagrange", judge = { c ->
                val p = (c.outcome as? Outcome.Interpolated)?.interpolant?.polynomial
                p != null && (QPOINTS + xs).all { x -> exactAt(p, mapOf("x" to x)) == lagrange(pts, x) }
            })
            val sorted = pts.sortedBy { it.first }
            val s = spline(sorted)
            out += Task(AREA, "spline($text)", "natural cubic", judge = { c ->
                val pieces = (c.outcome as? Outcome.SplineThrough)?.spline?.pieces
                pieces != null && pieces.size == sorted.size - 1 && pieces.all { piece ->
                    val a = rationalOf(piece.from)!!; val b = rationalOf(piece.to)!!
                    listOf(q(1, 3), q(1, 2), q(5, 7)).all { t -> val x = a + (b - a) * t; exactAt(piece.polynomial, mapOf("x" to x)) == s(x) } &&
                        exactAt(piece.polynomial, mapOf("x" to a)) == s(a) && exactAt(piece.polynomial, mapOf("x" to b)) == s(b)
                }
            })
        }
        out += Task(AREA, "interpolate((1, 2), (1, 3))", "the same x twice", fails = setOf(Failure.SAME_X)) { false }
        return out
    }

    private fun residues(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(80) {
            val n = r.int(2, 400).toLong()
            val a = r.int(-50, 500).toLong()
            val g = BigInteger.valueOf(a).gcd(BigInteger.valueOf(n))
            if (g != BigInteger.ONE) out += Task(AREA, "multorder($a, $n)", "no order", fails = setOf(Failure.UNDEFINED)) { false }
            else {
                var k = 1; var p = Math.floorMod(a, n)
                while (p != 1L % n) { p = p * Math.floorMod(a, n) % n; k++ }
                out += Task(AREA, "multorder($a, $n)", "$k", judge = isExact(q(k.toLong())))
            }
        }
        // Primitive roots exist mod 1, 2, 4, pᵏ and 2pᵏ: the smallest g of order φ(n), by stepping through the powers.
        fun order(a: Long, n: Long): Int { var k = 1; var p = a % n; while (p != 1L) { p = p * a % n; k++; if (k > n) return -1 }; return k }
        fun phi(n: Long) = (1..n).count { BigInteger.valueOf(it).gcd(BigInteger.valueOf(n)) == BigInteger.ONE }
        repeat(60) {
            val n = r.int(3, 600).toLong()
            val f = phi(n)
            val g = (1 until n).firstOrNull { BigInteger.valueOf(it).gcd(BigInteger.valueOf(n)) == BigInteger.ONE && order(it, n) == f }
            out += if (g == null) Task(AREA, "primroot($n)", "none", fails = setOf(Failure.NO_PRIMITIVE_ROOT)) { false }
            else Task(AREA, "primroot($n)", "$g", judge = isExact(q(g)))
        }
        // Discrete logarithms: the least x ≥ 0 with gˣ ≡ a (mod n).
        repeat(60) {
            val n = listOf(7L, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 97, 101, 1009, 30, 49, 81).random(r)
            val gg = r.int(2, (n - 1).toInt()).toLong()
            val a = r.int(1, (n - 1).toInt()).toLong()
            var x = 0; var p = 1L % n
            val seen = HashSet<Long>()
            while (p != a % n && seen.add(p)) { p = p * gg % n; x++ }
            // The logarithm is one in the group of units: for a base that is no unit mod n the engine says it is not defined.
            out += if (BigInteger.valueOf(gg).gcd(BigInteger.valueOf(n)) != BigInteger.ONE) Task(AREA, "discretelog($a, $gg, $n)", "no unit", fails = setOf(Failure.UNDEFINED)) { false }
            else if (p != a % n) Task(AREA, "discretelog($a, $gg, $n)", "none", fails = setOf(Failure.NO_DISCRETE_LOG)) { false }
            else Task(AREA, "discretelog($a, $gg, $n)", "$x", judge = isExact(q(x.toLong())))
        }
        return out
    }

    private fun factoring(r: Random): List<Task> {
        val out = ArrayList<Task>()
        val jr = java.util.Random(4040)
        repeat(60) { i ->
            // Primes of 2 to 20 digits, their product below 10⁴⁰.
            val factors = ArrayList<BigInteger>()
            var n = BigInteger.ONE
            val bits = listOf(8, 12, 20, 30, 40, 50, 60).shuffled(r)
            for (b in bits) {
                val p = BigInteger.probablePrime(b, jr)
                if ((n * p).toString().length > 40) continue
                val times = if (b <= 12 && r.nextInt(3) == 0) 2 else 1
                repeat(times) { if ((n * p).toString().length <= 40) { n *= p; factors += p } }
                if (factors.size >= 2 + i % 3) break
            }
            if (factors.size < 2) return@repeat
            val want = factors.groupingBy { it }.eachCount().toSortedMap()
            out += Task(AREA, "factor($n)", want.entries.joinToString(" · ") { (p, k) -> if (k == 1) "$p" else "$p^$k" }, judge = { c ->
                val o = c.outcome as? Outcome.Primes
                o != null && o.n == n && o.factors.map { it.first to it.second }.toMap().toSortedMap() == want
            })
        }
        // Over 40 digits: too large, by the engine's own limit.
        out += Task(AREA, "factor(${BigInteger.TEN.pow(40) + BigInteger.ONE})", "too large", fails = setOf(Failure.TOO_LARGE)) { false }
        return out
    }

    fun all(): List<Task> {
        val r = Random(4041)
        return fits(r) + residues(r) + factoring(r)
    }
}

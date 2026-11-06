package com.professoreuler.mathcalc

import com.professoreuler.mathcalc.Networks.Net
import java.math.BigInteger

/**
 * The chromatic number χ of a small graph without loops, by exhaustive search: colourings with k = ω, ω + 1, … colours tried by
 * backtracking (the vertex with the most colours around it first, as DSATUR does), from a largest clique found (it needs ω
 * colours). χ is shown only where it is certain twice: the colouring is proper (checked edge by edge), and that one colour less
 * is impossible, because a clique of χ vertices was found, or χ = 3 and the graph has an odd cycle, or (up to [EXACT_VERTICES]
 * vertices) by counting the colourings independently with the inclusion–exclusion formula of Björklund, Husfeldt and Koivisto
 * (2009): G has a k-colouring exactly when Σ over the vertex sets S of (−1)^(|V| − |S|) · i(S)^k > 0, where i(S) is the number of
 * independent sets within S. The searches are bounded by a number of steps, not by time, so the same graph always gets the same
 * answer; where they run out nothing is said.
 */
internal object NetworkColouring {
    private const val MOST_VERTICES = 40
    private const val EXACT_VERTICES = 16
    private const val MOST_STEPS = 60_000

    private class Exhausted : RuntimeException()

    /** [bipartite] whether the graph has no odd cycle (as [NetworkFacts] found and checked). */
    fun colouring(net: Net, bipartite: Boolean): Networks.Colouring? {
        val n = net.n
        if (n > MOST_VERTICES || net.arcs.any { it.u == it.v }) return null
        val adjacent = LongArray(n)
        for (a in net.arcs) {
            adjacent[a.u] = adjacent[a.u] or (1L shl a.v)
            adjacent[a.v] = adjacent[a.v] or (1L shl a.u)
        }
        return try {
            val clique = clique(n, adjacent)
            val lower = if (bipartite) clique.size else maxOf(clique.size, 3)
            var k = maxOf(1, lower)
            var found: IntArray? = null
            while (found == null) {
                if (k > n) return null
                found = colourWith(k, n, adjacent)
                if (found == null) k++
            }
            val colours = found
            // Proper, with exactly k colours.
            if (net.arcs.any { colours[it.u] == colours[it.v] } || colours.toSet().size != k || colours.any { it !in 1..k }) return null
            val certain = when {
                k == lower -> true
                n <= EXACT_VERTICES -> !colourable(n, adjacent, k - 1) && colourable(n, adjacent, k)
                else -> false
            }
            if (!certain) return null
            Networks.Colouring(k, colours.toList(), clique.takeIf { it.size == k }?.map { net.names[it] })
        } catch (e: Exhausted) {
            null
        }
    }

    /** A largest clique (its vertices in order), by the Bron–Kerbosch algorithm with a pivot, on bit sets of the vertices. */
    private fun clique(n: Int, adjacent: LongArray): List<Int> {
        var best = 1L
        var steps = 0
        fun extend(r: Long, p0: Long, x0: Long) {
            if (++steps > MOST_STEPS) throw Exhausted()
            if (steps and 255 == 0) Budget.check()
            if (p0 == 0L && x0 == 0L) {
                if (java.lang.Long.bitCount(r) > java.lang.Long.bitCount(best)) best = r
                return
            }
            if (java.lang.Long.bitCount(r) + java.lang.Long.bitCount(p0) <= java.lang.Long.bitCount(best)) return
            // The pivot with the most neighbours among the candidates.
            var pivot = -1
            var most = -1
            var both = p0 or x0
            while (both != 0L) {
                val u = java.lang.Long.numberOfTrailingZeros(both)
                both = both and (both - 1)
                val c = java.lang.Long.bitCount(p0 and adjacent[u])
                if (c > most) { most = c; pivot = u }
            }
            var p = p0
            var x = x0
            var candidates = p0 and adjacent[pivot].inv()
            while (candidates != 0L) {
                val v = java.lang.Long.numberOfTrailingZeros(candidates)
                candidates = candidates and (candidates - 1)
                extend(r or (1L shl v), p and adjacent[v], x and adjacent[v])
                p = p and (1L shl v).inv()
                x = x or (1L shl v)
            }
        }
        extend(0L, if (n == 64) -1L else (1L shl n) - 1, 0L)
        return (0 until n).filter { best and (1L shl it) != 0L }
    }

    /** A colouring with at most [k] colours (1 to k) by backtracking, or null where there is none. */
    private fun colourWith(k: Int, n: Int, adjacent: LongArray): IntArray? {
        val colour = IntArray(n)
        // For each vertex, the colours among its neighbours (bit c for colour c).
        var steps = 0
        fun around(v: Int): Long {
            var m = 0L
            var nb = adjacent[v]
            while (nb != 0L) {
                val u = java.lang.Long.numberOfTrailingZeros(nb)
                nb = nb and (nb - 1)
                if (colour[u] != 0) m = m or (1L shl colour[u])
            }
            return m
        }
        fun place(done: Int, used: Int): Boolean {
            if (done == n) return true
            if (++steps > MOST_STEPS) throw Exhausted()
            if (steps and 255 == 0) Budget.check()
            // The uncoloured vertex with the most different colours among its neighbours, then the most neighbours.
            var best = -1
            var bestSaturation = -1
            var bestAround = 0L
            for (v in 0 until n) {
                if (colour[v] != 0) continue
                val m = around(v)
                val saturation = java.lang.Long.bitCount(m)
                if (best < 0 || saturation > bestSaturation ||
                    (saturation == bestSaturation && java.lang.Long.bitCount(adjacent[v]) > java.lang.Long.bitCount(adjacent[best]))) {
                    best = v
                    bestSaturation = saturation
                    bestAround = m
                }
            }
            // A new colour only as the next one: colourings that differ by renaming colours are tried once.
            for (c in 1..minOf(k, used + 1)) {
                if (bestAround and (1L shl c) != 0L) continue
                colour[best] = c
                if (place(done + 1, maxOf(used, c))) return true
                colour[best] = 0
            }
            return false
        }
        return if (place(0, 0)) colour else null
    }

    /**
     * Whether the graph has a colouring with [k] colours, counted by inclusion–exclusion over the vertex sets: the number of ways
     * to cover the vertices by k independent sets (in order, overlapping allowed) is Σ (−1)^(n − |S|) i(S)^k.
     */
    private fun colourable(n: Int, adjacent: LongArray, k: Int): Boolean {
        if (k <= 0) return n == 0
        val closed = IntArray(n) { v -> (adjacent[v] or (1L shl v)).toInt() }
        val independent = IntArray(1 shl n)
        independent[0] = 1
        for (s in 1 until (1 shl n)) {
            val v = Integer.numberOfTrailingZeros(s)
            independent[s] = independent[s and (1 shl v).inv()] + independent[s and closed[v].inv()]
        }
        var total = BigInteger.ZERO
        for (s in 0 until (1 shl n)) {
            if (s and 1023 == 0) Budget.check()
            val term = BigInteger.valueOf(independent[s].toLong()).pow(k)
            total = if ((n - Integer.bitCount(s)) % 2 == 0) total + term else total - term
        }
        return total.signum() > 0
    }
}

package com.professoreuler.mathcalc

import java.math.BigInteger

/**
 * Huffman coding of symbol frequencies: the two lightest trees merged, again and again, into one; each symbol's code word read off
 * its path from the root (0 to the lighter of two merged trees, 1 to the other). Ties go to the tree made first, the symbols in the
 * order given before every merged tree. Checked before it is shown: the code is prefix-free (no word starts another), Kraft's sum
 * Σ 2^(−lᵢ) is exactly 1, Σ wᵢ·lᵢ equals the sum of the merged weights, the average length L lies between the entropy H and H + 1
 * (in ball arithmetic), and no prefix code is shorter on average: every list of lengths with Kraft's sum 1 is tried.
 */
object Huffman {
    /** A merge: the trees [first] and [second] (0..n − 1 the symbols, n, n + 1, … the merged trees in the order made) into one of [weight]. */
    data class Merge(val first: Int, val second: Int, val weight: Expr)

    /**
     * A code: the [symbols] with their [weights], each one's code word ([codes], of 0 and 1), the [merges] in order, the average
     * length [average] = Σ pᵢ·lᵢ (pᵢ = wᵢ/Σw), the [entropy] H = −Σ pᵢ·log₂ pᵢ (exact; its digits through the calculation's value
     * formatting), and Kraft's sum ([kraft], 1).
     */
    data class Code(val symbols: List<String>, val weights: List<Expr>, val codes: List<String>, val merges: List<Merge>, val average: Expr,
        val entropy: Expr, val kraft: Expr)

    /** More symbols than this are not coded: the optimality check tries every list of lengths. */
    const val MAX_SYMBOLS = 20

    fun code(symbols: List<String>, weights: List<Rational>): Code {
        val n = symbols.size
        if (n < 2 || n != weights.size || symbols.toSet().size != n) throw CalcException(Failure.ARGUMENTS)
        if (n > MAX_SYMBOLS) throw CalcException(Failure.TOO_LARGE)
        if (weights.any { it.signum <= 0 }) throw CalcException(Failure.UNDEFINED)
        // The forest: each tree's weight and its children (−1 for a leaf); taken lightest first, the older one on a tie.
        val weight = ArrayList(weights)
        val left = ArrayList<Int>(List(n) { -1 })
        val right = ArrayList<Int>(List(n) { -1 })
        val open = ArrayList((0 until n).toList())
        val merges = ArrayList<Merge>()
        while (open.size > 1) {
            Budget.check()
            open.sortWith(compareBy<Int>({ weight[it] }, { it }))
            val a = open.removeAt(0)
            val b = open.removeAt(0)
            weight += weight[a] + weight[b]
            left += a
            right += b
            merges += Merge(a, b, num(weight.last()))
            open += weight.size - 1
        }
        val codes = Array(n) { "" }
        fun walk(t: Int, prefix: String) {
            if (left[t] < 0) {
                codes[t] = prefix
                return
            }
            walk(left[t], prefix + "0")
            walk(right[t], prefix + "1")
        }
        walk(open.single(), "")
        val total = weights.fold(Rational.ZERO) { s, w -> s + w }
        val lengths = codes.map { it.length }
        val p = weights.map { it / total }
        val average = p.indices.fold(Rational.ZERO) { s, i -> s + p[i] * Rational.of(lengths[i].toLong()) }
        val kraft = lengths.fold(Rational.ZERO) { s, l -> s + Rational.of(BigInteger.ONE, BigInteger.ONE.shiftLeft(l)) }
        val entropy = Calculus.tidy(add(p.map { pi -> mul(num(pi), fn(Func.LOG, num(2), num(Rational.ONE / pi))) }))
        // The checks.
        val prefixFree = codes.indices.all { i -> codes.indices.all { j -> i == j || !codes[j].startsWith(codes[i]) } }
        val cost = weights.indices.fold(Rational.ZERO) { s, i -> s + weights[i] * Rational.of(lengths[i].toLong()) }
        val merged = merges.fold(Rational.ZERO) { s, m -> s + rational(m.weight)!! }
        val bounds = entropyBounds(entropy, average)
        if (!prefixFree || kraft != Rational.ONE || cost != merged || !bounds || optimum(weights) != cost) throw CalcException(Failure.UNSOLVED)
        return Code(symbols, weights.map { num(it) }, codes.toList(), merges, num(average), entropy, num(kraft))
    }

    /** H ≤ L < H + 1, decided in ball arithmetic (H = L exactly where every pᵢ is a power of ½). */
    private fun entropyBounds(h: Expr, l: Rational): Boolean {
        if (h is Expr.Num) return h.value <= l && l < h.value + Rational.ONE
        val ball = try { Precise.eval(h, 192, false, emptyMap()).re } catch (e: Imprecise) { return false }
        val lo = Ball.of(l, 192)
        val hi = Ball.of(l - Rational.ONE, 192)
        return (ball.below(lo)) && hi.below(ball)
    }

    /**
     * The least Σ wᵢ·lᵢ of any prefix code: every nondecreasing list of lengths l₁ ≤ … ≤ lₙ with Σ 2^(−lᵢ) = 1 (the full binary
     * trees' depths), the longest lengths to the lightest symbols.
     */
    private fun optimum(weights: List<Rational>): Rational {
        val n = weights.size
        val heavy = weights.sortedDescending()
        val top = n - 1
        val unit = BigInteger.ONE.shiftLeft(top)
        var best: Rational? = null
        val lengths = IntArray(n)
        fun search(k: Int, from: Int, left: BigInteger) {
            Budget.check()
            if (k == n) {
                if (left.signum() == 0) {
                    val c = (0 until n).fold(Rational.ZERO) { s, i -> s + heavy[i] * Rational.of(lengths[i].toLong()) }
                    if (best == null || c < best!!) best = c
                }
                return
            }
            for (l in from..top) {
                val piece = unit.shiftRight(l)
                if (piece > left) continue
                // The rest, each at most this piece, must be able to fill what is left, and at least the smallest piece each.
                val rest = left - piece
                val more = BigInteger.valueOf((n - k - 1).toLong())
                if (piece * more < rest) break
                if (rest < more) continue
                if (rest.signum() > 0 && more.signum() == 0) continue
                if (rest.signum() == 0 && more.signum() > 0) continue
                lengths[k] = l
                search(k + 1, l, rest)
            }
        }
        search(0, 1, unit)
        return best ?: throw CalcException(Failure.UNSOLVED)
    }
}

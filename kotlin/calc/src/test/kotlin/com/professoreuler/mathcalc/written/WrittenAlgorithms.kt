package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.random.Random

/**
 * Algorithms, against the test's own: the master theorem's case by comparing c with log_b a (exactly where a is a power of b),
 * Akra–Bazzi's p by bisection, each order compared by the ratio of the engine's Θ to the textbook's at ever larger n (constant
 * where the order is the same); limits of ratios built from functions of known growth; edit distance, LCS, knapsack and coin
 * change by the test's own dynamic programs and brute force; Huffman's average length by the test's own merging; the sorts
 * traced by the test's own instrumented textbook versions (CLRS's insertion sort, Lomuto's partition with the last pivot, merge
 * sort halving with the larger half first, bubble sort stopping after a pass without a swap); binary search's probes.
 */
internal object WrittenAlgorithms {
    private const val AREA = "algorithms"

    private fun Decimal.toDouble(): Double = when (this) {
        is Decimal.Digits -> ((if (negative) "-" else "") + digits[0] + "." + digits.substring(1).ifEmpty { "0" } + "e$exponent").toDouble()
        else -> 0.0
    }

    /** Whether [e] (in n) has the order of [order]: their ratio the same constant at n = 10¹⁰ … 10⁸⁰. */
    private fun sameOrder(e: Expr, order: (Double) -> Double, p: Double? = null): Boolean {
        val ns = listOf(1e10, 1e20, 1e40, 1e80)
        // Θ(n^p) with p the root of its equation, written as the letter p.
        val ratios = ns.map { n -> at(e, mapOf("n" to n) + (if (p != null) mapOf("p" to p) else emptyMap())) / order(n) }
        return ratios.all { it.isFinite() && it > 0 } && ratios.all { abs(it / ratios[0] - 1) < 1e-6 }
    }

    private fun recurrences(r: Random): List<Task> {
        val out = ArrayList<Task>()
        val pairs = listOf(1 to 2, 2 to 2, 4 to 2, 8 to 2, 3 to 3, 9 to 3, 4 to 4, 16 to 4, 3 to 2, 5 to 3, 7 to 2, 2 to 4, 6 to 5)
        val drivers = listOf(q(0), q(1, 2), q(1), q(2), q(3))
        for ((a, b) in pairs) for (c in drivers) for (k in 0..1) {
            if (c.isZero && k == 1 && r.nextBoolean()) continue
            val p = ln(a.toDouble()) / ln(b.toDouble())
            // Exactly equal where bᶜ = a: c = p.
            val equal = c.d == BigInteger.ONE && BigInteger.valueOf(b.toLong()).pow(c.n.toInt()) == BigInteger.valueOf(a.toLong()) ||
                c == q(1, 2) && b == 4 && a == 2
            val cd = c.toDouble()
            val (case, order) = when {
                equal -> 2 to { n: Double -> n.pow(p) * ln(n).pow(k + 1) }
                cd < p -> 1 to { n: Double -> n.pow(p) }
                else -> 3 to { n: Double -> n.pow(cd) * ln(n).pow(k) }
            }
            val base = if (c.isZero) "" else if (c == Q.ONE) "n" else if (c == q(1, 2)) "sqrt(n)" else "n^${c.n}"
            val text = if (k == 0) base.ifEmpty { "1" } else if (base.isEmpty()) "log(n)" else "$base log(n)"
            out += Task(AREA, "mastertheorem(T(n) = ${if (a == 1) "" else "$a"}T(n/$b) + $text)", "case $case", judge = { calc ->
                val an = (calc.outcome as? Outcome.DivideAndConquer)?.analysis
                an != null && an.case == case && an.order?.let { sameOrder(it, order) } == true &&
                    (an.critical?.let { abs(valueOf(it) - p) < 1e-12 } ?: an.criticalDigits?.let { abs(it.toDouble() - p) < 1e-9 } ?: false)
            })
        }
        // Akra–Bazzi: Σ aᵢ·T(n·sᵢ) + nᶜ, p with Σ aᵢsᵢᵖ = 1 by bisection.
        val shares = listOf(listOf(1 to q(1, 3), 1 to q(2, 3)), listOf(1 to q(1, 2), 1 to q(1, 4)), listOf(2 to q(1, 4), 1 to q(1, 2)), listOf(1 to q(1, 5), 1 to q(7, 10)),
            listOf(1 to q(1, 2), 1 to q(1, 3)), listOf(3 to q(1, 3), 1 to q(1, 2)), listOf(1 to q(1, 4), 1 to q(3, 4)), listOf(2 to q(1, 2), 1 to q(1, 3)), listOf(1 to q(1, 3), 2 to q(1, 3), 1 to q(1, 6)))
        for (terms in shares) for (c in listOf(q(0), q(1, 2), q(1), q(2))) {
            fun g(p: Double) = terms.sumOf { (a, s) -> a * s.toDouble().pow(p) } - 1
            var lo = -10.0; var hi = 10.0
            repeat(200) { val m = (lo + hi) / 2; if (g(m) > 0) lo = m else hi = m }
            val p = (lo + hi) / 2
            val cd = c.toDouble()
            val equal = abs(cd - p) < 1e-12
            val order: (Double) -> Double = when {
                equal -> { n -> n.pow(p) * ln(n) }
                cd < p -> { n -> n.pow(p) }
                else -> { n -> n.pow(cd) }
            }
            val f = if (c.isZero) "1" else if (c == Q.ONE) "n" else if (c == q(1, 2)) "sqrt(n)" else "n^${c.n}"
            val sum = terms.joinToString(" + ") { (a, s) -> "${if (a == 1) "" else "$a"}T(${if (s.n == BigInteger.ONE) "" else "${s.n}"}n/${s.d})" }
            out += Task(AREA, "akrabazzi(T(n) = $sum + $f)", "p = $p", judge = { calc ->
                val an = (calc.outcome as? Outcome.DivideAndConquer)?.analysis
                an != null && an.order?.let { sameOrder(it, order, p) } == true &&
                    (an.critical?.let { abs(valueOf(it) - p) < 1e-12 } ?: an.criticalDigits?.let { abs(it.toDouble() - p) < 1e-9 } ?: false)
            })
        }
        out += Task(AREA, "mastertheorem(T(n) = T(n-1) + n)", "no divide and conquer", fails = setOf(Failure.NOT_DIVIDE_AND_CONQUER)) { false }
        return out
    }

    /** Functions of n in increasing order of growth, each strictly below the next. */
    private val LADDER = listOf("1", "log(log(n))", "log(n)", "log(n)^2", "sqrt(n)", "n", "n log(n)", "n^2", "n^2 log(n)", "n^3", "2^n", "3^n", "n!", "n^n")

    private fun growth(r: Random): List<Task> {
        val out = ArrayList<Task>()
        for (i in LADDER.indices) for (j in LADDER.indices) {
            if (i == j || r.nextInt(3) != 0) continue
            val limitZero = i < j
            out += Task(AREA, "asymptotic(${LADDER[i]}, ${LADDER[j]})", if (limitZero) "0" else "∞", judge = { c ->
                val g = (c.outcome as? Outcome.Growth)?.growth
                g != null && !g.sorted && (if (limitZero) isZero(g.limits[0]) else g.limits[0] == Expr.INF)
            })
        }
        // The same growth: a·nᵏ + lower terms against b·nᵏ, the limit a/b.
        repeat(30) {
            val k = r.int(2, 4)
            val a = r.int(1, 9); val b = r.int(1, 9); val lower = r.int(-5, 5)
            val f = "${a}n^$k${if (lower == 0) "" else if (lower > 0) " + ${lower}n" else " - ${-lower}n"}"
            out += Task(AREA, "asymptotic($f, ${b}n^$k)", "${q(a.toLong(), b.toLong())}", judge = { c ->
                val g = (c.outcome as? Outcome.Growth)?.growth
                g != null && rationalOf(g.limits[0]) == q(a.toLong(), b.toLong()) && Asymptotics.Relation.THETA in g.relations()
            })
        }
        out += Task(AREA, "asymptotic(log_2(n), ln(n))", "1/ln 2", judge = { c ->
            (c.outcome as? Outcome.Growth)?.growth?.let { abs(valueOf(it.limits[0]) - 1 / ln(2.0)) < 1e-12 } == true
        })
        // Order a shuffled choice from the ladder.
        repeat(40) {
            val picked = LADDER.indices.shuffled(r).take(r.int(3, 6))
            val text = picked.joinToString(", ") { LADDER[it] }
            val want = picked.indices.sortedBy { picked[it] }
            out += Task(AREA, "growthorder($text)", want.joinToString(" < ") { LADDER[picked[it]] }, judge = { c ->
                val g = (c.outcome as? Outcome.Growth)?.growth
                g != null && g.sorted && g.order == want && g.limits.all { isZero(it) }
            })
        }
        return out
    }

    private fun lev(a: String, b: String): Int {
        val d = Array(a.length + 1) { i -> IntArray(b.length + 1) { j -> if (i == 0) j else if (j == 0) i else 0 } }
        for (i in 1..a.length) for (j in 1..b.length) d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        return d[a.length][b.length]
    }

    private fun lcs(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 1..a.length) for (j in 1..b.length) d[i][j] = if (a[i - 1] == b[j - 1]) d[i - 1][j - 1] + 1 else maxOf(d[i - 1][j], d[i][j - 1])
        return d[a.length][b.length]
    }

    private fun isSubsequence(s: String, of: String): Boolean {
        var k = 0
        for (ch in of) if (k < s.length && s[k] == ch) k++
        return k == s.length
    }

    private fun dynamic(r: Random): List<Task> {
        val out = ArrayList<Task>()
        fun Random.word() = String(CharArray(int(1, 9)) { "abcd"[nextInt(if (nextBoolean()) 2 else 4)] })
        repeat(60) {
            val a = r.word(); val b = r.word()
            val d = lev(a, b)
            out += Task(AREA, "editdistance($a, $b)", "$d", judge = { c ->
                val t = (c.outcome as? Outcome.DynamicTable)?.table
                t != null && t.value?.let { rationalOf(it) } == q(d.toLong()) && t.operations.count { it.edit != Dynamic.Edit.KEEP } == d
            })
            val l = lcs(a, b)
            out += Task(AREA, "lcs($a, $b)", "$l", judge = { c ->
                val t = (c.outcome as? Outcome.DynamicTable)?.table
                t != null && t.value?.let { rationalOf(it) } == q(l.toLong()) && (t.subsequence ?: "").let { s -> s.length == l && isSubsequence(s, a) && isSubsequence(s, b) }
            })
        }
        // 0/1 knapsack by trying every set of items.
        repeat(40) {
            val n = r.int(2, 8)
            val values = List(n) { r.int(1, 30).toLong() }; val weights = List(n) { r.int(1, 12).toLong() }
            val cap = r.int(1, 30).toLong()
            var best = 0L
            for (mask in 0 until (1 shl n)) {
                val w = (0 until n).filter { (mask shr it) and 1 == 1 }.sumOf { weights[it] }
                if (w <= cap) best = maxOf(best, (0 until n).filter { (mask shr it) and 1 == 1 }.sumOf { values[it] })
            }
            out += Task(AREA, "knapsack(${values.joinToString(", ", "[", "]")}, ${weights.joinToString(", ", "[", "]")}, $cap)", "$best", judge = { c ->
                val t = (c.outcome as? Outcome.DynamicTable)?.table
                t != null && t.value?.let { rationalOf(it) } == q(best) && t.items.toSet().size == t.items.size &&
                    t.items.sumOf { values[it - 1] } == best && t.items.sumOf { weights[it - 1] }.let { w -> w <= cap && t.weight?.let { rationalOf(it) } == q(w) }
            })
        }
        // Coin change: the fewest coins and the number of ways, by the test's own tables.
        repeat(40) {
            val coins = (listOf(1, 2, 3, 4, 5, 6, 7, 10, 12, 25).shuffled(r).take(r.int(1, 4))).sorted()
            val amount = r.int(1, 80)
            val fewest = IntArray(amount + 1) { if (it == 0) 0 else Int.MAX_VALUE }
            val ways = Array(amount + 1) { if (it == 0) BigInteger.ONE else BigInteger.ZERO }
            for (x in 1..amount) for (cn in coins) if (cn <= x && fewest[x - cn] != Int.MAX_VALUE) fewest[x] = minOf(fewest[x], fewest[x - cn] + 1)
            for (cn in coins) for (x in cn..amount) ways[x] = ways[x] + ways[x - cn]
            val list = coins.joinToString(", ", "[", "]")
            out += Task(AREA, "coinchange($list, $amount)", if (fewest[amount] == Int.MAX_VALUE) "none" else "${fewest[amount]}", judge = { c ->
                val t = (c.outcome as? Outcome.DynamicTable)?.table
                t != null && if (fewest[amount] == Int.MAX_VALUE) t.value == null else
                    t.value?.let { rationalOf(it) } == q(fewest[amount].toLong()) && t.coins.size == fewest[amount] &&
                        t.coins.fold(Q.ZERO) { s, e -> s + (rationalOf(e) ?: Q.ZERO) } == q(amount.toLong()) && t.coins.all { rationalOf(it)?.let { v -> v.isInteger && v.n.toInt() in coins } == true }
            })
            out += Task(AREA, "coinways($list, $amount)", "${ways[amount]}", judge = { c ->
                (c.outcome as? Outcome.DynamicTable)?.table?.value?.let { rationalOf(it) } == Q.of(ways[amount])
            })
        }
        // Huffman: the optimal total Σ wᵢ·lengthᵢ is the sum of the merged weights; the entropy −Σ pᵢ log₂ pᵢ.
        repeat(40) {
            val w = List(r.int(2, 8)) { r.int(1, 30).toLong() }
            val heap = java.util.PriorityQueue(w)
            var cost = 0L
            while (heap.size > 1) { val s = heap.poll() + heap.poll(); cost += s; heap.add(s) }
            val total = w.sum()
            val entropy = -w.sumOf { val p = it.toDouble() / total; p * ln(p) / ln(2.0) }
            out += Task(AREA, "huffman(${w.joinToString(", ")})", "L = ${q(cost, total)}", judge = { c ->
                val h = (c.outcome as? Outcome.HuffmanCode)?.code
                // A prefix code with those lengths: no code word begins another.
                h != null && rationalOf(h.average) == q(cost, total) && abs(valueOf(h.entropy) - entropy) < 1e-12 * maxOf(1.0, entropy) &&
                    h.codes.indices.all { i -> h.codes.indices.none { j -> i != j && h.codes[j].startsWith(h.codes[i]) } } &&
                    h.codes.indices.sumOf { w[it] * h.codes[it].length } == cost
            })
        }
        return out
    }

    private class Count(var comparisons: Int = 0, var swaps: Int = 0)

    private fun insertion(a: IntArray, k: Count) {
        for (i in 1 until a.size) {
            val key = a[i]; var j = i - 1
            while (j >= 0) { k.comparisons++; if (a[j] > key) { a[j + 1] = a[j]; k.swaps++; j-- } else break }
            a[j + 1] = key
        }
    }

    private fun selection(a: IntArray, k: Count) {
        for (i in 0 until a.size - 1) {
            var m = i
            for (j in i + 1 until a.size) { k.comparisons++; if (a[j] < a[m]) m = j }
            if (m != i) { a[i] = a[m].also { a[m] = a[i] }; k.swaps++ }
        }
    }

    private fun bubble(a: IntArray, k: Count) {
        for (pass in 1 until a.size) {
            var swapped = false
            for (j in 0 until a.size - pass) { k.comparisons++; if (a[j] > a[j + 1]) { a[j] = a[j + 1].also { a[j + 1] = a[j] }; k.swaps++; swapped = true } }
            if (!swapped) break
        }
    }

    private fun merge(a: List<Int>, k: Count): List<Int> {
        if (a.size <= 1) return a
        val left = merge(a.subList(0, (a.size + 1) / 2), k); val right = merge(a.subList((a.size + 1) / 2, a.size), k)
        val out = ArrayList<Int>(); var i = 0; var j = 0
        while (i < left.size && j < right.size) { k.comparisons++; if (left[i] <= right[j]) out += left[i++] else out += right[j++] }
        while (i < left.size) out += left[i++]
        while (j < right.size) out += right[j++]
        return out
    }

    /** CLRS's quicksort: Lomuto's partition around the last element; a swap of an element with itself is no swap. */
    private fun quick(a: IntArray, lo: Int, hi: Int, k: Count) {
        if (lo >= hi) return
        val pivot = a[hi]; var i = lo - 1
        for (j in lo until hi) { k.comparisons++; if (a[j] <= pivot) { i++; if (i != j) { a[i] = a[j].also { a[j] = a[i] }; k.swaps++ } } }
        if (i + 1 != hi) { a[i + 1] = a[hi].also { a[hi] = a[i + 1] }; k.swaps++ }
        quick(a, lo, i, k); quick(a, i + 2, hi, k)
    }

    private fun sorts(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(40) {
            val xs = List(r.int(2, 12)) { r.int(-20, 40) }
            val text = xs.joinToString(", ")
            val sorted = xs.sorted().map { q(it.toLong()) }
            fun trace(name: String, comparisons: Int, swaps: Int?) = Task(AREA, "$name($text)", "comparisons $comparisons", judge = { c ->
                val t = (c.outcome as? Outcome.Trace)?.trace
                t != null && t.result.map { rationalOf(it) } == sorted && t.comparisons == comparisons && (swaps == null || t.swaps == swaps)
            })
            Count().let { k -> insertion(xs.toIntArray(), k); out += trace("insertionsort", k.comparisons, k.swaps) }
            Count().let { k -> selection(xs.toIntArray(), k); out += trace("selectionsort", k.comparisons, k.swaps) }
            Count().let { k -> bubble(xs.toIntArray(), k); out += trace("bubblesort", k.comparisons, k.swaps) }
            Count().let { k -> merge(xs, k); out += trace("mergesort", k.comparisons, null) }
            Count().let { k -> quick(xs.toIntArray(), 0, xs.size - 1, k); out += trace("quicksort", k.comparisons, k.swaps) }
            out += Task(AREA, "quicksort($text, middle)", "sorted", judge = { c -> (c.outcome as? Outcome.Trace)?.trace?.result?.map { rationalOf(it) } == sorted })
        }
        // Binary search: the middle ⌊(lo + hi)/2⌋ of the positions 1 … n, as probed.
        repeat(40) {
            val xs = List(r.int(1, 20)) { r.int(-30, 60) }.distinct().sorted()
            val target = if (r.nextBoolean()) xs.random(r) else r.int(-30, 60)
            val probes = ArrayList<Int>(); var lo = 1; var hi = xs.size; var found: Int? = null
            while (lo <= hi) {
                val mid = (lo + hi) / 2
                probes += mid
                when { xs[mid - 1] == target -> { found = mid; break }; xs[mid - 1] < target -> lo = mid + 1; else -> hi = mid - 1 }
            }
            out += Task(AREA, "binarysearch(${xs.joinToString(", ", "[", "]")}, $target)", "probes $probes", judge = { c ->
                val t = (c.outcome as? Outcome.Trace)?.trace
                t != null && t.steps.map { it.at } == probes && t.found == found
            })
        }
        out += Task(AREA, "binarysearch([3, 1, 2], 2)", "not sorted", fails = setOf(Failure.NOT_SORTED)) { false }
        return out
    }

    fun all(): List<Task> {
        val r = Random(2024)
        return recurrences(r) + growth(r) + dynamic(r) + sorts(r)
    }
}

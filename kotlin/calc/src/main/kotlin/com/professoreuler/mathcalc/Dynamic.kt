package com.professoreuler.mathcalc

import java.math.BigInteger

/**
 * The dynamic programs an algorithms course works by hand, each with its table, its result and the reconstruction of that result:
 * the edit (Levenshtein) distance of two words, a longest common subsequence, the 0/1 knapsack and coin change (the fewest coins,
 * and the number of ways). Every result is checked by a second method before it is shown: the reconstruction's own value (the
 * operations turn the first word into the second, the subsequence is one of both, the items fit and are worth the optimum, the coins
 * make the amount), and the optimum found again another way: a shortest path through the grid of the two words (0-1 breadth-first
 * search) for the distances, every subset for the knapsack, a breadth-first search over the amounts for the fewest coins, and the
 * power series 1/∏(1 − x^c) for the number of ways.
 */
object Dynamic {
    enum class Problem { EDIT_DISTANCE, LCS, KNAPSACK, COIN_CHANGE, COIN_WAYS }

    /** An edit operation: a letter kept, substituted, inserted or deleted. */
    enum class Edit { KEEP, SUBSTITUTE, INSERT, DELETE }

    /**
     * One operation of an alignment, in order from the words' start: [from] the first word's letter (null for an insertion), [to]
     * the second word's (null for a deletion), [first] and [second] the positions (1-based) in the two words it is at.
     */
    data class Operation(val edit: Edit, val from: Char?, val to: Char?, val first: Int, val second: Int)

    /**
     * A table: [rows] and [columns] labelled (ε for the empty prefix and the letters of the words; the items 0..n and the
     * capacities; the coin types and the amounts), [cells] row by row (null where there is no value: an amount the coins cannot
     * make), [path] the cells the reconstruction goes through (row, column, from 0), from the result back to the start, and the
     * [value] (null where the amount cannot be made). The reconstruction: the edit [operations]; the [subsequence]; the knapsack's
     * [items] (1-based) and their [weight]; the [coins] used.
     */
    data class Table(val problem: Problem, val rows: List<String>, val columns: List<String>, val cells: List<List<Expr?>>,
        val path: List<Pair<Int, Int>>, val value: Expr?, val operations: List<Operation> = emptyList(), val subsequence: String? = null,
        val items: List<Int> = emptyList(), val weight: Expr? = null, val coins: List<Expr> = emptyList())

    /** Words longer than this are refused: their table would not be read. */
    const val MAX_WORD = 60
    const val MAX_ITEMS = 20
    const val MAX_CAPACITY = 1000
    const val MAX_AMOUNT = 5000
    const val MAX_COINS = 20

    private fun n(k: Int): Expr = num(k.toLong())
    private fun labels(w: String) = listOf("ε") + w.map { it.toString() }

    // ---------------------------------------------------------------------------------------------------------------------
    // The edit distance.

    /**
     * D[i][j], the fewest insertions, deletions and substitutions (each costing 1) that turn the first i letters of [a] into the first
     * j of [b]; the operations read back from D[m][n], a diagonal step first (keep or substitute), then a deletion, then an insertion.
     */
    fun editDistance(a: String, b: String): Table {
        checkWords(a, b)
        val m = a.length
        val n = b.length
        val d = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) d[i][0] = i
        for (j in 0..n) d[0][j] = j
        for (i in 1..m) {
            Budget.check()
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j - 1] + cost, d[i - 1][j] + 1, d[i][j - 1] + 1)
            }
        }
        val path = ArrayList<Pair<Int, Int>>()
        val ops = ArrayList<Operation>()
        var i = m
        var j = n
        path += i to j
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && d[i][j] == d[i - 1][j - 1] + (if (a[i - 1] == b[j - 1]) 0 else 1)) {
                ops += Operation(if (a[i - 1] == b[j - 1]) Edit.KEEP else Edit.SUBSTITUTE, a[i - 1], b[j - 1], i, j)
                i--; j--
            } else if (i > 0 && d[i][j] == d[i - 1][j] + 1) {
                ops += Operation(Edit.DELETE, a[i - 1], null, i, j)
                i--
            } else {
                ops += Operation(Edit.INSERT, null, b[j - 1], i, j)
                j--
            }
            path += i to j
        }
        ops.reverse()
        // The checks: the operations turn a into b, as many as the distance; the distance found again as a shortest path.
        val distance = d[m][n]
        if (apply(a, ops) != b || ops.count { it.edit != Edit.KEEP } != distance || gridPath(a, b, substitute = true) != distance) {
            throw CalcException(Failure.UNSOLVED)
        }
        return Table(Problem.EDIT_DISTANCE, labels(a), labels(b), d.map { r -> r.map { n(it) } }, path, n(distance), operations = ops)
    }

    private fun checkWords(a: String, b: String) {
        if (a.length > MAX_WORD || b.length > MAX_WORD) throw CalcException(Failure.TOO_LARGE)
    }

    /** The operations applied to [a], one after the other. */
    private fun apply(a: String, ops: List<Operation>): String {
        val out = StringBuilder()
        var k = 0
        for (op in ops) {
            when (op.edit) {
                Edit.KEEP -> { if (a.getOrNull(k) != op.from) return "\u0000"; out.append(a[k]); k++ }
                Edit.SUBSTITUTE -> { if (a.getOrNull(k) != op.from) return "\u0000"; out.append(op.to); k++ }
                Edit.DELETE -> { if (a.getOrNull(k) != op.from) return "\u0000"; k++ }
                Edit.INSERT -> out.append(op.to)
            }
        }
        return if (k == a.length) out.toString() else "\u0000"
    }

    /**
     * The shortest path from (0, 0) to (m, n) in the grid of the two words, by 0-1 breadth-first search: a step right or down
     * costs 1, a diagonal step costs 0 between equal letters and (with [substitute]) 1 between different ones.
     */
    private fun gridPath(a: String, b: String, substitute: Boolean): Int {
        val m = a.length
        val n = b.length
        val dist = Array(m + 1) { IntArray(n + 1) { Int.MAX_VALUE } }
        val deque = ArrayDeque<Pair<Int, Int>>()
        dist[0][0] = 0
        deque.addFirst(0 to 0)
        while (deque.isNotEmpty()) {
            Budget.check()
            val (i, j) = deque.removeFirst()
            val here = dist[i][j]
            fun relax(x: Int, y: Int, w: Int) {
                if (here + w < dist[x][y]) {
                    dist[x][y] = here + w
                    if (w == 0) deque.addFirst(x to y) else deque.addLast(x to y)
                }
            }
            if (i < m) relax(i + 1, j, 1)
            if (j < n) relax(i, j + 1, 1)
            if (i < m && j < n) {
                if (a[i] == b[j]) relax(i + 1, j + 1, 0) else if (substitute) relax(i + 1, j + 1, 1)
            }
        }
        return dist[m][n]
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // A longest common subsequence.

    /**
     * c[i][j], the length of a longest common subsequence of the first i letters of [a] and the first j of [b] (Cormen et al.); one
     * read back from c[m][n]: a diagonal step where the letters are equal, else up where c[i − 1][j] ≥ c[i][j − 1], else left.
     */
    fun lcs(a: String, b: String): Table {
        checkWords(a, b)
        val m = a.length
        val n = b.length
        val c = Array(m + 1) { IntArray(n + 1) }
        for (i in 1..m) {
            Budget.check()
            for (j in 1..n) c[i][j] = if (a[i - 1] == b[j - 1]) c[i - 1][j - 1] + 1 else maxOf(c[i - 1][j], c[i][j - 1])
        }
        val path = ArrayList<Pair<Int, Int>>()
        val out = StringBuilder()
        var i = m
        var j = n
        path += i to j
        while (i > 0 && j > 0) {
            if (a[i - 1] == b[j - 1]) {
                out.append(a[i - 1]); i--; j--
            } else if (c[i - 1][j] >= c[i][j - 1]) i-- else j--
            path += i to j
        }
        val s = out.reverse().toString()
        // The checks: a subsequence of both, as long as the table says; m + n − 2·length is the shortest path of insertions and
        // deletions alone.
        val length = c[m][n]
        if (s.length != length || !isSubsequence(s, a) || !isSubsequence(s, b) || gridPath(a, b, substitute = false) != m + n - 2 * length) {
            throw CalcException(Failure.UNSOLVED)
        }
        return Table(Problem.LCS, labels(a), labels(b), c.map { r -> r.map { n(it) } }, path, n(length), subsequence = s)
    }

    private fun isSubsequence(s: String, w: String): Boolean {
        var k = 0
        for (ch in w) if (k < s.length && s[k] == ch) k++
        return k == s.length
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The 0/1 knapsack.

    /**
     * K[i][w], the most value from the first i items within the capacity w (each item taken once or not at all); the items read back
     * from K[n][W]: item i is taken where K[i][w] ≠ K[i − 1][w].
     */
    fun knapsack(values: List<Rational>, weights: List<Int>, capacity: Int): Table {
        val count = values.size
        if (count == 0 || count != weights.size) throw CalcException(Failure.ARGUMENTS)
        if (count > MAX_ITEMS || capacity > MAX_CAPACITY) throw CalcException(Failure.TOO_LARGE)
        if (values.any { it.signum < 0 } || weights.any { it <= 0 } || capacity < 0) throw CalcException(Failure.UNDEFINED)
        val k = Array(count + 1) { arrayOfNulls<Rational>(capacity + 1).also { r -> r.fill(Rational.ZERO) } }
        for (i in 1..count) {
            Budget.check()
            for (w in 0..capacity) {
                val without = k[i - 1][w]!!
                k[i][w] = if (weights[i - 1] <= w) maxOf(without, k[i - 1][w - weights[i - 1]]!! + values[i - 1]) else without
            }
        }
        val items = ArrayList<Int>()
        val path = ArrayList<Pair<Int, Int>>()
        var w = capacity
        path += count to w
        for (i in count downTo 1) {
            if (k[i][w] != k[i - 1][w]) {
                items += i
                w -= weights[i - 1]
            }
            path += i - 1 to w
        }
        items.reverse()
        val best = k[count][capacity]!!
        val weight = items.sumOf { weights[it - 1] }
        val value = items.fold(Rational.ZERO) { s, i -> s + values[i - 1] }
        // The checks: the items fit and are worth the optimum, and no subset is worth more (every one tried).
        if (weight > capacity || value != best || bruteKnapsack(values, weights, capacity) != best) throw CalcException(Failure.UNSOLVED)
        return Table(Problem.KNAPSACK, (0..count).map { it.toString() }, (0..capacity).map { it.toString() },
            k.map { r -> r.map { num(it!!) } }, path, num(best), items = items, weight = n(weight))
    }

    /** The best value of all 2ⁿ subsets that fit, each tried (a Gray code, one item in or out at a time). */
    private fun bruteKnapsack(values: List<Rational>, weights: List<Int>, capacity: Int): Rational {
        val count = values.size
        var best = Rational.ZERO
        var weight = 0L
        var value = Rational.ZERO
        val taken = BooleanArray(count)
        for (step in 1 until (1 shl count)) {
            if (step and 1023 == 0) Budget.check()
            val i = Integer.numberOfTrailingZeros(step)
            taken[i] = !taken[i]
            if (taken[i]) { weight += weights[i]; value += values[i] } else { weight -= weights[i]; value -= values[i] }
            if (weight <= capacity && value > best) best = value
        }
        return best
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Coin change.

    /**
     * C[a], the fewest coins that make the amount a (each coin as often as wanted), and the last coin of such a way (the largest where
     * several fit); the coins read back from C[A]. The table's two rows: C[a], and that last coin.
     */
    fun coinChange(coins: List<Int>, amount: Int): Table {
        checkCoins(coins, amount)
        val c = arrayOfNulls<Int>(amount + 1)
        val last = arrayOfNulls<Int>(amount + 1)
        c[0] = 0
        val sorted = coins.sortedDescending()
        for (a in 1..amount) {
            if (a and 255 == 0) Budget.check()
            for (coin in sorted) {
                if (coin > a) continue
                val before = c[a - coin] ?: continue
                if (c[a] == null || before + 1 < c[a]!!) {
                    c[a] = before + 1
                    last[a] = coin
                }
            }
        }
        val used = ArrayList<Expr>()
        val path = ArrayList<Pair<Int, Int>>()
        val fewest = c[amount]
        if (fewest != null) {
            var a = amount
            path += 0 to a
            while (a > 0) {
                val coin = last[a]!!
                used += n(coin)
                a -= coin
                path += 0 to a
            }
        }
        // The checks: the coins make the amount, as many as the table says; the fewest found again by a breadth-first search over
        // the amounts (a coin a step), which finds none exactly where the table has none.
        val ok = (fewest == null && used.isEmpty() || used.size == fewest && used.sumOf { integer(it)!!.toInt() } == amount) &&
            fewestBySearch(coins, amount) == fewest
        if (!ok) throw CalcException(Failure.UNSOLVED)
        return Table(Problem.COIN_CHANGE, listOf("C", "last"), (0..amount).map { it.toString() },
            listOf(c.map { it?.let(::n) }, last.map { it?.let(::n) }), path, fewest?.let(::n), coins = used)
    }

    private fun checkCoins(coins: List<Int>, amount: Int) {
        if (coins.isEmpty() || coins.size > MAX_COINS || coins.toSet().size != coins.size) throw CalcException(Failure.ARGUMENTS)
        if (coins.any { it <= 0 } || amount < 0) throw CalcException(Failure.UNDEFINED)
        if (amount > MAX_AMOUNT) throw CalcException(Failure.TOO_LARGE)
    }

    private fun fewestBySearch(coins: List<Int>, amount: Int): Int? {
        val dist = IntArray(amount + 1) { -1 }
        dist[0] = 0
        val queue = ArrayDeque<Int>()
        queue.addLast(0)
        while (queue.isNotEmpty()) {
            Budget.check()
            val a = queue.removeFirst()
            if (a == amount) return dist[a]
            for (coin in coins) {
                val b = a + coin
                if (b <= amount && dist[b] < 0) { dist[b] = dist[a] + 1; queue.addLast(b) }
            }
        }
        return null
    }

    /**
     * W[i][a], the number of ways to make the amount a from the first i kinds of coin, each as often as wanted and the order not
     * counted: W[i][a] = W[i − 1][a] + W[i][a − cᵢ].
     */
    fun coinWays(coins: List<Int>, amount: Int): Table {
        checkCoins(coins, amount)
        val w = Array(coins.size + 1) { Array<BigInteger>(amount + 1) { BigInteger.ZERO } }
        w[0][0] = BigInteger.ONE
        for (i in 1..coins.size) {
            Budget.check()
            val coin = coins[i - 1]
            for (a in 0..amount) w[i][a] = w[i - 1][a] + (if (a >= coin) w[i][a - coin] else BigInteger.ZERO)
        }
        val ways = w[coins.size][amount]
        // The check: the coefficient of x^A in 1/∏(1 − x^c), the power series inverted term by term.
        if (waysBySeries(coins, amount) != ways) throw CalcException(Failure.UNSOLVED)
        val path = (coins.size downTo 0).map { it to amount }
        return Table(Problem.COIN_WAYS, listOf("∅") + coins.map { it.toString() }, (0..amount).map { it.toString() },
            w.map { r -> r.map { num(Rational.of(it)) } }, path, num(Rational.of(ways)))
    }

    /** a_A of 1/P(x), P = ∏(1 − x^c) cut after x^A: a₀ = 1, a_k = −Σ p_j·a_{k−j}. */
    private fun waysBySeries(coins: List<Int>, amount: Int): BigInteger {
        var p = HashMap<Int, BigInteger>().apply { put(0, BigInteger.ONE) }
        for (coin in coins) {
            Budget.check()
            val next = HashMap<Int, BigInteger>(p)
            for ((d, v) in p) if (d + coin <= amount) next[d + coin] = (next[d + coin] ?: BigInteger.ZERO) - v
            p = HashMap(next.filterValues { it.signum() != 0 })
        }
        val terms = p.filterKeys { it > 0 }.toList().sortedBy { it.first }
        val a = Array<BigInteger>(amount + 1) { BigInteger.ZERO }
        a[0] = BigInteger.ONE
        for (k in 1..amount) {
            if (k and 63 == 0) Budget.check()
            var s = BigInteger.ZERO
            for ((j, pj) in terms) {
                if (j > k) break
                s -= pj * a[k - j]
            }
            a[k] = s
        }
        return a[amount]
    }
}

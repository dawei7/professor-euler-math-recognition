package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The algorithms course's functions, each answer against a value known independently: the textbooks' worked examples (Cormen,
 * Leiserson, Rivest and Stein; the classic exercise values), closed formulas, and brute force in the test itself.
 */
class AlgorithmsTest {
    private fun answer(text: String): String {
        val r = Reports.of(TextInput.read(text)).result
        return if (r.outcome is Outcome.Failed) "FAILED ${(r.outcome as Outcome.Failed).failure}" else answerOf(r)
    }

    private fun outcome(text: String): Outcome = Reports.of(TextInput.read(text)).result.outcome

    private fun check(cases: List<Pair<String, String>>) {
        val wrong = cases.mapNotNull { (text, expected) -> answer(text).takeIf { it != expected }?.let { "$text: $it, not $expected" } }
        assertEquals("", wrong.joinToString("\n"))
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Sorting and searching.

    @Test
    fun sortingTraces() = check(listOf(
        // Cormen et al., Figure 2.2: insertion sort of 5, 2, 4, 6, 1, 3; 9 inversions, so 9 moves.
        "insertionsort(5, 2, 4, 6, 1, 3)" to "1, 2, 3, 4, 5, 6 comparisons 12 swaps 9 steps 5",
        // Selection sort always compares n(n − 1)/2 = 15 times.
        "selectionsort(5, 2, 4, 6, 1, 3)" to "1, 2, 3, 4, 5, 6 comparisons 15 swaps 3 steps 5",
        "bubblesort(5, 2, 4, 6, 1, 3)" to "1, 2, 3, 4, 5, 6 comparisons 15 swaps 9 steps 5",
        // Sorted input of length 8: merge sort compares (n/2)·log₂ n = 12 times.
        "mergesort(1, 2, 3, 4, 5, 6, 7, 8)" to "1, 2, 3, 4, 5, 6, 7, 8 comparisons 12 steps 7",
        // Cormen et al., Figure 7.1: Lomuto's partition of 2, 8, 7, 1, 3, 5, 6, 4.
        "quicksort(2, 8, 7, 1, 3, 5, 6, 4)" to "1, 2, 3, 4, 5, 6, 7, 8 comparisons 15 swaps 6 steps 5",
        // Reversed input with the last element as pivot: the worst case, n(n − 1)/2 = 36 comparisons.
        "quicksort(9, 8, 7, 6, 5, 4, 3, 2, 1)" to "1, 2, 3, 4, 5, 6, 7, 8, 9 comparisons 36 swaps 4 steps 8",
        "insertionsort(3, -1, 2.5, 1/2)" to "-1, 0.5, 2.5, 3 comparisons 6 swaps 4 steps 3",
        "binarysearch([2, 5, 8, 12, 16, 23, 38, 56, 72, 91], 23)" to "probes 5,8,6 found at 6",
        "binarysearch([1, 3, 5, 7], 8)" to "probes 2,3,4 absent",
        "binarysearch([3, 1, 2], 2)" to "FAILED NOT_SORTED",
    ))

    @Test
    fun theFirstPartitionIsCormensFigure() {
        val t = (outcome("quicksort(2, 8, 7, 1, 3, 5, 6, 4)") as Outcome.Trace).trace
        val first = t.steps.first()
        assertEquals(listOf(2, 1, 3, 4, 7, 5, 6, 8).map { num(it.toLong()) }, first.list)
        assertEquals(4, first.at)
        assertEquals(num(4), first.value)
    }

    /** Random lists: sorted and a permutation (against the library's sort), and the counts against formulas counted pair by pair. */
    @Test
    fun randomListsAgainstCountsByPairs() {
        val random = Random(7)
        repeat(200) {
            val n = random.nextInt(1, 15)
            val a = List(n) { random.nextInt(-9, 10) }
            val text = a.joinToString(", ")
            val expected = a.sorted().map { num(it.toLong()) }
            val inversions = a.indices.sumOf { j -> (0 until j).count { a[it] > a[j] } }
            // Insertion sort compares once more than it moves each key, except where the key moves to the front.
            val insertion = a.indices.drop(1).sumOf { j -> val moves = (0 until j).count { a[it] > a[j] }; if (moves == j) moves else moves + 1 }
            for (name in listOf("insertionsort", "selectionsort", "bubblesort", "mergesort", "quicksort")) {
                val t = (outcome("$name($text)") as Outcome.Trace).trace
                assertEquals("$name($text)", expected, t.result)
                when (name) {
                    "insertionsort" -> { assertEquals(inversions, t.swaps); assertEquals(insertion, t.comparisons) }
                    "bubblesort" -> assertEquals(inversions, t.swaps)
                    "selectionsort" -> assertEquals(n * (n - 1) / 2, t.comparisons)
                }
            }
            val sorted = a.sorted()
            val v = random.nextInt(-10, 11)
            val s = (outcome("binarysearch([${sorted.joinToString(", ")}], $v)") as Outcome.Trace).trace
            if (v in sorted) assertEquals(v, sorted[s.found!! - 1]) else assertEquals(null, s.found)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Dynamic programming.

    @Test
    fun dynamicProgramsAgainstTextbookValues() = check(listOf(
        "editdistance(kitten, sitting)" to "3 substitute k→s substitute e→i insert g",
        "editdistance(intention, execution)" to "5 substitute i→e substitute n→x substitute t→e substitute e→c substitute n→u",
        "editdistance(sunday, saturday)" to "3 insert a insert t substitute n→r",
        "editdistance(abc, abc)" to "0",
        "editdistance(sin, sing)" to "1 insert g",
        // Cormen et al., Figure 15.8.
        "lcs(ABCBDAB, BDCABA)" to "4 BCBA",
        "lcs(AGGTAB, GXTXAYB)" to "4 GTAB",
        "lcs(abc, def)" to "0",
        "knapsack([60, 100, 120], [10, 20, 30], 50)" to "220 items 2,3 weight 50",
        "knapsack([10, 40, 30, 50], [5, 4, 6, 3], 10)" to "90 items 2,4 weight 7",
        "knapsack([1.5, 2.5], [1, 2], 2)" to "2.5 items 2 weight 2",
        "coinchange([1, 2, 5], 11)" to "3 coins 5,5,1",
        // The greedy choice fails here: 4 + 1 + 1, but 3 + 3 is fewer.
        "coinchange([1, 3, 4], 6)" to "2 coins 3,3",
        "coinchange([2], 3)" to "none coins",
        "coinways([1, 2, 5], 5)" to "4",
        "coinways([2, 5, 3, 6], 10)" to "5",
        // Project Euler 31: 2 pounds in British coins.
        "coinways([1, 2, 5, 10, 20, 50, 100, 200], 200)" to "73682",
        "editdistance(\"kitten\", \"sitting\")" to "FAILED ARGUMENTS",
        "knapsack([1, 2], [3], 5)" to "FAILED ARGUMENTS",
        "coinchange([1, 2, 2], 4)" to "FAILED ARGUMENTS",
        "knapsack([1, 2], [1, 2.5], 5)" to "FAILED UNDEFINED",
    ))

    /** Random words and coins against brute force written here: the recursive definitions, every subsequence, every count of coins. */
    @Test
    fun randomInputsAgainstBruteForce() {
        val random = Random(11)
        fun word() = String(CharArray(random.nextInt(1, 7)) { "abc"[random.nextInt(3)] })
        fun lev(a: String, b: String): Int = when {
            a.isEmpty() -> b.length
            b.isEmpty() -> a.length
            else -> minOf(lev(a.drop(1), b) + 1, lev(a, b.drop(1)) + 1, lev(a.drop(1), b.drop(1)) + if (a[0] == b[0]) 0 else 1)
        }
        fun isSub(s: String, w: String): Boolean { var k = 0; for (c in w) if (k < s.length && s[k] == c) k++; return k == s.length }
        fun lcs(a: String, b: String): Int = (0 until (1 shl a.length)).maxOf { mask ->
            val s = a.filterIndexed { i, _ -> mask shr i and 1 == 1 }
            if (isSub(s, b)) s.length else 0
        }
        repeat(60) {
            val a = word()
            val b = word()
            assertEquals("editdistance($a, $b)", lev(a, b).toString(), answer("editdistance($a, $b)").substringBefore(' '))
            assertEquals("lcs($a, $b)", lcs(a, b).toString(), answer("lcs($a, $b)").substringBefore(' '))
        }
        repeat(40) {
            val coins = (1..9).shuffled(random).take(random.nextInt(1, 4)).sorted()
            val amount = random.nextInt(0, 30)
            var fewest: Int? = null
            var ways = 0
            fun count(i: Int, left: Int, used: Int) {
                if (i == coins.size) { if (left == 0) { ways++; if (fewest == null || used < fewest!!) fewest = used }; return }
                var k = 0
                while (k * coins[i] <= left) { count(i + 1, left - k * coins[i], used + k); k++ }
            }
            count(0, amount, 0)
            val list = coins.joinToString(", ")
            assertEquals("coinways([$list], $amount)", ways.toString(), answer("coinways([$list], $amount)"))
            assertEquals("coinchange([$list], $amount)", fewest?.toString() ?: "none", answer("coinchange([$list], $amount)").substringBefore(' '))
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Huffman codes.

    @Test
    fun huffmanCodes() = check(listOf(
        // Cormen et al., Figure 16.4: 224,000 bits for 100,000 characters, 2.24 on average.
        "huffman(a = 45, b = 13, c = 12, d = 16, e = 9, f = 5)" to "a=0 b=101 c=100 d=111 e=1101 f=1100 L = 56/25 H = ≈ 2.219879978",
        // Probabilities that are powers of ½: the average length is the entropy, 1.75.
        "huffman(0.5, 0.25, 0.125, 0.125)" to "a=0 b=10 c=110 d=111 L = 1.75 H = 1.75",
        "huffman(1, 1)" to "a=0 b=1 L = 1 H = 1",
        "huffman(5)" to "FAILED ARGUMENTS",
    ))

    // ---------------------------------------------------------------------------------------------------------------------
    // Recurrences.

    @Test
    fun masterTheorem() = check(listOf(
        // Merge sort; Cormen et al. § 4.5's examples; Strassen; Karatsuba.
        "mastertheorem(T(n) = 2T(n/2) + n)" to "master case 2 p = 1 Θ(n·log_2(n))",
        "mastertheorem(T(n) = 9T(n/3) + n)" to "master case 1 p = 2 Θ(n^2)",
        "mastertheorem(T(n) = T(2n/3) + 1)" to "master case 2 p = 0 Θ(log_2(n))",
        "mastertheorem(T(n) = 3T(n/4) + n log(n))" to "master case 3 p = log_4(3) Θ(n·log_2(n))",
        "mastertheorem(T(n) = 7T(n/2) + n^2)" to "master case 1 p = log_2(7) Θ(n^(log_2(7)))",
        "mastertheorem(T(n) = 3T(n/2) + n)" to "master case 1 p = log_2(3) Θ(n^(log_2(3)))",
        "mastertheorem(T(n) = 8T(n/2) + n^2)" to "master case 1 p = 3 Θ(n^3)",
        "mastertheorem(T(n) = 2T(n/2) + n log(n))" to "master case 2 p = 1 Θ(n(log_2(n))^2)",
        "mastertheorem(T(n) = 2T(n/4) + sqrt(n))" to "master case 2 p = 1/2 Θ(√n·log_2(n))",
        "mastertheorem(T(n) = 2T(n/2) + n^2)" to "master case 3 p = 1 Θ(n^2)",
        "mastertheorem(T(n) = 2T(floor(n/2)) + n)" to "master case 2 p = 1 Θ(n·log_2(n))",
        // Cormen et al.'s example of the gap between cases 1 and 2.
        "mastertheorem(T(n) = 2T(n/2) + n/log(n))" to "master no case: between_cases_1_and_2 p = 1",
        "mastertheorem(T(n) = T(n/3) + T(2n/3) + n)" to "master no case: several_sizes",
        "mastertheorem(T(n) = T(n-1) + n)" to "FAILED NOT_DIVIDE_AND_CONQUER",
        "mastertheorem(T(n) = 2T(n/2) - n)" to "FAILED NOT_DIVIDE_AND_CONQUER",
        "mastertheorem(T(n) = 2T(n/2) + 2^n)" to "FAILED UNSOLVED",
    ))

    @Test
    fun akraBazzi() = check(listOf(
        // Cormen et al.'s recursion tree: Θ(n log n); the median of medians: Θ(n); the gap's recurrence: Θ(n log log n).
        "akrabazzi(T(n) = T(n/3) + T(2n/3) + n)" to "akra-bazzi p = 1 Θ(n·log_2(n))",
        "akrabazzi(T(n) = T(n/5) + T(7n/10) + n)" to "akra-bazzi p ≈ 0.8397803045 Θ(n)",
        "akrabazzi(T(n) = 2T(n/2) + n/log(n))" to "akra-bazzi p = 1 Θ(n·log_2(log_2(n)))",
        "akrabazzi(T(n) = 2T(n/2) + n/log(n)^2)" to "akra-bazzi p = 1 Θ(n)",
        "akrabazzi(T(n) = T(n/2) + T(n/3) + n)" to "akra-bazzi p ≈ 0.787884911 Θ(n)",
    ))

    /** p of T(n/5) + T(7n/10): (1/5)^p + (7/10)^p = 1, checked with doubles here. */
    @Test
    fun theExponentSolvesItsEquation() {
        val a = (outcome("akrabazzi(T(n) = T(n/5) + T(7n/10) + n)") as Outcome.DivideAndConquer).analysis
        val p = Algorithms.decimal(a.criticalDigits!!, Style.PLAIN, latex = false).toDouble()
        assertEquals(1.0, Math.pow(0.2, p) + Math.pow(0.7, p), 1e-9)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Growth.

    @Test
    fun asymptoticComparisons() = check(listOf(
        "asymptotic(n log(n), n^2)" to "lim = 0 little_o,big_o",
        "asymptotic(3n^2 + n, n^2)" to "lim = 3 big_o,theta,big_omega",
        "asymptotic(2^n, n!)" to "lim = 0 little_o,big_o",
        "asymptotic(log_2(n), ln(n))" to "lim = 1/ln(2) big_o,theta,big_omega",
        // Stirling's formula, and the central binomial coefficient ~ 4ⁿ/√(πn).
        "asymptotic(n!, sqrt(2 pi n) (n/e)^n)" to "lim = 1 big_o,theta,big_omega,equivalent",
        "asymptotic((2n)!/(n!)^2, 4^n/sqrt(n))" to "lim = 1/√π big_o,theta,big_omega",
        "asymptotic(log(n!), n log(n))" to "lim = 1 big_o,theta,big_omega,equivalent",
        "asymptotic((n+1)!, n!)" to "lim = ∞ little_omega,big_omega",
        "asymptotic(n log(log(n)), n)" to "lim = ∞ little_omega,big_omega",
        "asymptotic(n^2, n^3, n)" to "FAILED ARGUMENTS",
        "asymptotic(n, x)" to "FAILED SEVERAL_UNKNOWNS",
        "asymptotic(sin(n), n)" to "FAILED UNSOLVED",
    ))

    @Test
    fun growthOrder() = check(listOf(
        "growthorder(n!, 2^n, n^3, n log(n), sqrt(n), log(n), 1, n^n, log(log(n)))" to
            "1 < log_10(log_10(n)) < log_10(n) < √n < n·log_10(n) < n^3 < 2^n < n! < n^n",
        "growthorder(n^2, 2n^2 + 1, n log(n))" to "n·log_10(n) < n^2 ~ 2n^2 + 1",
        "growthorder(n!, (n+1)!, n^n, 2^(2n), 4^n)" to "2^(2n) ~ 4^n < n! < (n + 1)! < n^n",
        "growthorder(n^(log(n)), n^10, 1.1^n)" to "n^10 < n^(log_10(n)) < (1.1)^n",
    ))

    /** Every call written as the Calculator shows it reads back to the same answer, quickly. */
    @Test
    fun writtenCallsReadBackAndAreQuick() {
        val calls = listOf("mastertheorem(T(n) = 3T(n/4) + n log(n))", "akrabazzi(T(n) = T(n/3) + T(2n/3) + n)",
            "growthorder(n!, 2^n, n^3, n log(n), sqrt(n), log(n))", "editdistance(kitten, sitting)", "knapsack([60, 100, 120], [10, 20, 30], 50)",
            "huffman(a = 45, b = 13, c = 12, d = 16, e = 9, f = 5)", "quicksort(2, 8, 7, 1, 3, 5, 6, 4, middle)")
        for (call in calls) {
            val start = System.nanoTime()
            val a = answer(call)
            val ms = (System.nanoTime() - start) / 1_000_000
            assertTrue("$call took $ms ms", ms < 1500)
            val written = CommandFunctions.latex(TextInput.read(call))!!
            assertEquals(call, a, answer(written))
        }
    }
}

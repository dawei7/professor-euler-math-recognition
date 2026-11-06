package com.professoreuler.mathcalc

/**
 * The sorting algorithms of a first algorithms course, traced step by step on a list of numbers as a learner traces them by hand
 * (owner, 6 November 2026: what an "Algorithmik" course has learners compute), with the comparisons and swaps they make, and binary
 * search with its probes. Positions count from 1, as a textbook's A[1..n]. Each trace is checked before it is shown: its result is
 * the input sorted another way (sorted, and the same numbers), insertion and bubble sort swapped exactly as often as the list has
 * inversions (counted pair by pair), selection sort compared n(n − 1)/2 times, and binary search found the value exactly where it
 * is in the list, within ⌊log₂ n⌋ + 1 probes.
 */
object Sorting {
    enum class Algorithm { INSERTION, SELECTION, BUBBLE, MERGE, QUICK, BINARY_SEARCH }

    /**
     * Quicksort's pivot: the last element of the part (Lomuto's partition, as Cormen et al. write it), or the first or the middle
     * one (position ⌊(p + r)/2⌋), moved to the end of the part first and then partitioned the same way.
     */
    enum class Pivot { LAST, FIRST, MIDDLE }

    /** What a step of a trace did. */
    enum class Action {
        /** Insertion sort: the element at [Step.at] (the [Step.value]) put in at [Step.to], the larger ones before it moved one place right. */
        INSERT,

        /**
         * Selection sort: the smallest of positions [Step.at]..n found at [Step.to] (its [Step.value]) and swapped with position
         * [Step.at]; where it is already there, nothing is swapped ([Step.marks] empty).
         */
        SELECT,

        /** Bubble sort: one pass over positions 1..[Step.to]; [Step.marks] the positions j whose neighbours j and j + 1 were swapped. */
        PASS,

        /** Merge sort: the sorted halves [Step.from]..[Step.at] and [Step.at] + 1..[Step.to] merged into one. */
        MERGE,

        /**
         * Quicksort: positions [Step.from]..[Step.to] partitioned around the pivot [Step.value], which ends at [Step.at]: the smaller
         * or equal ones before it, the larger ones after it.
         */
        PARTITION,

        /**
         * Binary search: the middle [Step.at] of [Step.from]..[Step.to] probed; its element [Step.value] is [Step.outcome] −1 larger than
         * the value sought (go left), 0 the value (found), 1 smaller (go right).
         */
        PROBE,
    }

    /**
     * A step: what it did, the list after it, the positions it worked on ([from]..[to], [at]; 1-based, 0 where unused), [marks]
     * (the positions moved or swapped), the element it is about, and the comparisons and swaps made so far.
     */
    data class Step(val action: Action, val list: List<Expr>, val from: Int, val to: Int, val at: Int, val marks: List<Int>, val value: Expr?,
        val comparisons: Int, val swaps: Int, val outcome: Int = 0)

    /**
     * A trace: the [input], each step, the sorted [result] (for binary search the list searched), [comparisons] and [swaps] in all
     * (null for merge sort and binary search, which swap nothing); quicksort's [pivot] rule; binary search's [target] and the
     * position it was [found] at (null where it is not in the list).
     */
    data class Trace(val algorithm: Algorithm, val input: List<Expr>, val steps: List<Step>, val result: List<Expr>, val comparisons: Int,
        val swaps: Int?, val pivot: Pivot? = null, val target: Expr? = null, val found: Int? = null)

    /** Lists longer than this are not traced: the trace would be longer than anyone reads. */
    const val MAX_LENGTH = 64

    private fun exprs(a: List<Rational>): List<Expr> = a.map { num(it) }

    fun sort(algorithm: Algorithm, input: List<Rational>, pivot: Pivot = Pivot.LAST): Trace {
        if (input.isEmpty() || input.size > MAX_LENGTH) throw CalcException(Failure.ARGUMENTS)
        val t = when (algorithm) {
            Algorithm.INSERTION -> insertion(input)
            Algorithm.SELECTION -> selection(input)
            Algorithm.BUBBLE -> bubble(input)
            Algorithm.MERGE -> merge(input)
            Algorithm.QUICK -> quick(input, pivot)
            Algorithm.BINARY_SEARCH -> throw CalcException(Failure.ARGUMENTS)
        }
        check(t, input)
        return t
    }

    /** The checks: sorted, the same numbers as the input (the input sorted another way), and the counts each algorithm must have. */
    private fun check(t: Trace, input: List<Rational>) {
        val expected = exprs(input.sortedWith(naturalOrder()))
        val n = input.size
        val ok = t.result == expected && when (t.algorithm) {
            Algorithm.INSERTION, Algorithm.BUBBLE -> t.swaps == inversions(input)
            Algorithm.SELECTION -> t.comparisons == n * (n - 1) / 2 && (t.swaps ?: 0) <= n - 1
            // Each merge compares at most once for each element it places but its last.
            Algorithm.MERGE -> t.comparisons <= n * (32 - Integer.numberOfLeadingZeros(maxOf(n - 1, 1)))
            Algorithm.QUICK -> t.comparisons <= n * (n - 1) / 2
            Algorithm.BINARY_SEARCH -> true
        } && t.steps.all { s -> s.list.size == n }
        if (!ok) throw CalcException(Failure.UNSOLVED)
    }

    /** The pairs i < j with a[i] > a[j], counted pair by pair. */
    internal fun inversions(a: List<Rational>): Int {
        var k = 0
        for (i in a.indices) for (j in i + 1 until a.size) if (a[i] > a[j]) k++
        return k
    }

    /** Insertion sort as Cormen et al. write it: each key compared with the sorted ones before it from the right; each move is a swap of neighbours. */
    private fun insertion(input: List<Rational>): Trace {
        val a = input.toMutableList()
        val steps = ArrayList<Step>()
        var comparisons = 0
        var swaps = 0
        for (j in 1 until a.size) {
            Budget.check()
            val key = a[j]
            var i = j - 1
            while (i >= 0) {
                comparisons++
                if (a[i] > key) {
                    a[i + 1] = a[i]
                    swaps++
                    i--
                } else break
            }
            a[i + 1] = key
            steps += Step(Action.INSERT, exprs(a), 1, j + 1, j + 1, ((i + 2)..(j + 1)).toList().takeIf { i + 1 != j }.orEmpty(), num(key),
                comparisons, swaps).copy(to = i + 2)
        }
        return Trace(Algorithm.INSERTION, exprs(input), steps, exprs(a), comparisons, swaps)
    }

    private fun selection(input: List<Rational>): Trace {
        val a = input.toMutableList()
        val steps = ArrayList<Step>()
        var comparisons = 0
        var swaps = 0
        for (i in 0 until a.size - 1) {
            Budget.check()
            var min = i
            for (j in i + 1 until a.size) {
                comparisons++
                if (a[j] < a[min]) min = j
            }
            val value = a[min]
            if (min != i) {
                a[min] = a[i]
                a[i] = value
                swaps++
            }
            steps += Step(Action.SELECT, exprs(a), i + 1, a.size, i + 1, if (min != i) listOf(i + 1, min + 1) else emptyList(), num(value),
                comparisons, swaps).copy(to = min + 1)
        }
        return Trace(Algorithm.SELECTION, exprs(input), steps, exprs(a), comparisons, swaps)
    }

    /** Bubble sort with its early stop: passes until one swaps nothing; each pass goes one place less far. */
    private fun bubble(input: List<Rational>): Trace {
        val a = input.toMutableList()
        val steps = ArrayList<Step>()
        var comparisons = 0
        var swaps = 0
        for (pass in 1 until a.size) {
            Budget.check()
            val marks = ArrayList<Int>()
            for (j in 0 until a.size - pass) {
                comparisons++
                if (a[j] > a[j + 1]) {
                    val x = a[j]
                    a[j] = a[j + 1]
                    a[j + 1] = x
                    swaps++
                    marks += j + 1
                }
            }
            steps += Step(Action.PASS, exprs(a), 1, a.size - pass + 1, pass, marks, null, comparisons, swaps)
            if (marks.isEmpty()) break
        }
        return Trace(Algorithm.BUBBLE, exprs(input), steps, exprs(a), comparisons, swaps)
    }

    /** Merge sort, top down: the halves p..q and q + 1..r with q = ⌊(p + r)/2⌋, each merge comparing the halves' first elements. */
    private fun merge(input: List<Rational>): Trace {
        val a = input.toMutableList()
        val steps = ArrayList<Step>()
        var comparisons = 0
        fun mergeSort(p: Int, r: Int) {
            if (p >= r) return
            Budget.check()
            val q = (p + r) / 2
            mergeSort(p, q)
            mergeSort(q + 1, r)
            val left = a.subList(p, q + 1).toList()
            val right = a.subList(q + 1, r + 1).toList()
            var i = 0
            var j = 0
            var k = p
            while (i < left.size && j < right.size) {
                comparisons++
                if (left[i] <= right[j]) a[k++] = left[i++] else a[k++] = right[j++]
            }
            while (i < left.size) a[k++] = left[i++]
            while (j < right.size) a[k++] = right[j++]
            steps += Step(Action.MERGE, exprs(a), p + 1, r + 1, q + 1, ((p + 1)..(r + 1)).toList(), null, comparisons, 0)
        }
        mergeSort(0, a.size - 1)
        return Trace(Algorithm.MERGE, exprs(input), steps, exprs(a), comparisons, null)
    }

    /**
     * Quicksort with Lomuto's partition: the pivot x at the end, i the last of the smaller ones; each A[j] ≤ x moves next to them.
     * A swap of an element with itself is no swap and is not counted.
     */
    private fun quick(input: List<Rational>, pivot: Pivot): Trace {
        val a = input.toMutableList()
        val steps = ArrayList<Step>()
        var comparisons = 0
        var swaps = 0
        fun swap(i: Int, j: Int) {
            if (i == j) return
            val x = a[i]
            a[i] = a[j]
            a[j] = x
            swaps++
        }
        fun partition(p: Int, r: Int): Int {
            when (pivot) {
                Pivot.LAST -> Unit
                Pivot.FIRST -> swap(p, r)
                Pivot.MIDDLE -> swap((p + r) / 2, r)
            }
            val x = a[r]
            var i = p - 1
            for (j in p until r) {
                comparisons++
                if (a[j] <= x) {
                    i++
                    swap(i, j)
                }
            }
            swap(i + 1, r)
            return i + 1
        }
        fun quickSort(p: Int, r: Int) {
            if (p >= r) return
            Budget.check()
            val x = when (pivot) {
                Pivot.LAST -> a[r]
                Pivot.FIRST -> a[p]
                Pivot.MIDDLE -> a[(p + r) / 2]
            }
            val q = partition(p, r)
            steps += Step(Action.PARTITION, exprs(a), p + 1, r + 1, q + 1, listOf(q + 1), num(x), comparisons, swaps)
            quickSort(p, q - 1)
            quickSort(q + 1, r)
        }
        quickSort(0, a.size - 1)
        return Trace(Algorithm.QUICK, exprs(input), steps, exprs(a), comparisons, swaps, pivot = pivot)
    }

    /**
     * Binary search for [value] in the sorted [list]: the middle ⌊(lo + hi)/2⌋ of lo..hi probed, one three-way comparison each,
     * until the value is found there or lo..hi is empty. Where the value is in the list several times, the one it meets first.
     */
    fun search(list: List<Rational>, value: Rational): Trace {
        if (list.isEmpty() || list.size > 10_000) throw CalcException(Failure.ARGUMENTS)
        if (list.zipWithNext().any { (x, y) -> x > y }) throw CalcException(Failure.NOT_SORTED)
        val steps = ArrayList<Step>()
        val shown = exprs(list)
        var lo = 0
        var hi = list.size - 1
        var found: Int? = null
        var probes = 0
        while (lo <= hi) {
            Budget.check()
            val mid = (lo + hi) / 2
            probes++
            val c = list[mid].compareTo(value)
            val outcome = if (c == 0) 0 else if (c > 0) -1 else 1
            steps += Step(Action.PROBE, shown, lo + 1, hi + 1, mid + 1, listOf(mid + 1), num(list[mid]), probes, 0, outcome)
            if (c == 0) {
                found = mid + 1
                break
            }
            if (c > 0) hi = mid - 1 else lo = mid + 1
        }
        // The check: found exactly where the value is, absent exactly where it is not, within ⌊log₂ n⌋ + 1 probes.
        val bound = 32 - Integer.numberOfLeadingZeros(list.size)
        val ok = (if (found == null) value !in list else list[found - 1] == value) && probes <= bound
        if (!ok) throw CalcException(Failure.UNSOLVED)
        return Trace(Algorithm.BINARY_SEARCH, shown, steps, shown, probes, null, target = num(value), found = found)
    }
}

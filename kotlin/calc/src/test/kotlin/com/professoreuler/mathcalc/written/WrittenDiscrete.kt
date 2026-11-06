package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.random.Random

/**
 * Number theory, counting, logic and sets, by brute force: divisors counted, residues tried, primes sieved, the counting
 * numbers from their recurrences, truth tables evaluated row by row, sets as the test's own sets.
 */
internal object WrittenDiscrete {
    private fun big(n: Long) = BigInteger.valueOf(n)

    private val SIEVE: BooleanArray = BooleanArray(200_000) { true }.also { s ->
        s[0] = false; s[1] = false
        var i = 2
        while (i * i < s.size) { if (s[i]) { var j = i * i; while (j < s.size) { s[j] = false; j += i } }; i++ }
    }
    private val PRIMES = SIEVE.indices.filter { SIEVE[it] }

    private fun isPrime(n: Long): Boolean {
        if (n < 2) return false
        if (n < SIEVE.size) return SIEVE[n.toInt()]
        var d = 2L
        while (d * d <= n) { if (n % d == 0L) return false; d++ }
        return true
    }

    private fun factorize(n0: Long): List<Pair<Long, Int>> {
        var n = n0
        val out = ArrayList<Pair<Long, Int>>()
        var d = 2L
        while (d * d <= n) {
            var k = 0
            while (n % d == 0L) { n /= d; k++ }
            if (k > 0) out += d to k
            d++
        }
        if (n > 1) out += n to 1
        return out
    }

    private fun divisors(n: Long) = (1..n).filter { n % it == 0L }

    /** The engine's whole number. */
    private fun isWhole(want: BigInteger): (Calculation) -> Boolean = isExact(Q.of(want))

    fun numberTheory(): List<Task> {
        val r = Random(1640)
        val out = ArrayList<Task>()
        val area = "number theory"
        repeat(40) {
            val a = r.int(1, 2000).toLong(); val b = r.int(1, 2000).toLong(); val c = r.int(1, 300).toLong()
            out += Task(area, "gcd($a, $b)", "", judge = isWhole(big(a).gcd(big(b))))
            out += Task(area, "lcm($a, $b, $c)", "", judge = isWhole(listOf(a, b, c).map(::big).reduce { x, y -> x * y / x.gcd(y) }))
        }
        repeat(30) {
            val a = r.int(-500, 500).toLong(); val m = r.int(2, 60).toLong()
            out += Task(area, if (it % 2 == 0) "mod($a, $m)" else "${if (a < 0) "($a)" else "$a"} mod $m", "", judge = isWhole(big(Math.floorMod(a, m))))
        }
        repeat(40) {
            val m = r.int(2, 80).toLong(); val a = r.int(1, 200).toLong()
            val inv = (0 until m).firstOrNull { (a * it) % m == 1L }
            out += if (inv == null) Task(area, "modinv($a, $m)", "no inverse", fails = setOf(Failure.UNDEFINED, Failure.UNSOLVED)) { false }
            else Task(area, "modinv($a, $m)", "$inv", judge = isWhole(big(inv)))
            val e = r.int(0, 5000).toLong()
            out += Task(area, "powmod($a, $e, $m)", "", judge = isWhole(big(a).modPow(big(e), big(m))))
        }
        for (n in (1..60) + listOf(97L, 100, 128, 210, 360, 997, 1001, 1024, 2310, 9973).map { it.toInt() }) {
            val nn = n.toLong()
            val ds = divisors(nn)
            out += Task(area, "totient($n)", "", judge = isWhole(big((1..nn).count { big(it).gcd(big(nn)) == BigInteger.ONE }.toLong())))
            out += Task(area, "sigma($n)", "", judge = isWhole(big(ds.sum())))
            out += Task(area, "tau($n)", "", judge = isWhole(big(ds.size.toLong())))
            val f = factorize(nn)
            val mu = if (f.any { it.second > 1 }) 0 else if (f.size % 2 == 0) 1 else -1
            out += Task(area, "mu($n)", "", judge = isWhole(big(mu.toLong())))
        }
        for (k in listOf(1, 2, 3, 10, 25, 100, 168, 500, 1000, 1229, 5000, 10000)) out += Task(area, "prime($k)", "", judge = isWhole(big(PRIMES[k - 1].toLong())))
        repeat(25) {
            val n = r.int(3, 100000).toLong()
            out += Task(area, "nextprime($n)", "", judge = isWhole(big(generateSequence(n + 1) { it + 1 }.first(::isPrime))))
            out += Task(area, "prevprime($n)", "", judge = isWhole(big(generateSequence(n - 1) { it - 1 }.first(::isPrime))))
        }
        repeat(30) {
            val p = PRIMES[r.int(1, 60)].toLong(); val a = r.int(-50, 200).toLong()
            val e = big(Math.floorMod(a, p)).modPow(big((p - 1) / 2), big(p)).toLong()
            val want = if (e == 0L) 0L else if (e == 1L) 1L else -1L
            out += Task(area, "legendre($a, $p)", "$want", judge = isWhole(big(want)))
        }
        repeat(25) {
            val n = (2 * r.int(1, 200) + 1).toLong(); val a = r.int(-100, 300).toLong()
            // Jacobi: the product of the Legendre symbols of n's primes.
            val want = factorize(n).fold(1L) { acc, (p, k) ->
                val e = big(Math.floorMod(a, p)).modPow(big((p - 1) / 2), big(p)).toLong()
                val l = if (e == 0L) 0L else if (e == 1L) 1L else -1L
                acc * (0 until k).fold(1L) { x, _ -> x * l }
            }
            out += Task(area, "jacobi($a, $n)", "$want", judge = isWhole(big(want)))
        }
        // Whole numbers into primes, and their facts.
        repeat(50) {
            val n = r.int(2, 1_000_000).toLong()
            val f = factorize(n)
            out += Task(area, "factor($n)", f.joinToString(" · ") { (p, k) -> if (k == 1) "$p" else "$p^$k" }, judge = { c ->
                when (val o = c.outcome) {
                    is Outcome.Primes -> o.factors.map { (p, k) -> p.toLong() to k } == f
                    // A prime has nothing to factor: Calculate may say so with the number itself.
                    is Outcome.Factored -> f.size == 1 && f[0].second == 1
                    else -> false
                }
            })
        }
        repeat(30) {
            val n = r.int(2, 100000).toLong()
            val f = factorize(n)
            out += Task(area, "facts($n)", "", judge = { c ->
                val k = (c.outcome as? Outcome.NumberFacts)?.facts
                k != null && k.prime == isPrime(n) && k.divisorCount == big(f.fold(1L) { a, (_, e) -> a * (e + 1) }) &&
                    k.sigma == big(f.fold(1L) { a, (p, e) -> a * (0..e).sumOf { j -> (0 until j).fold(1L) { x, _ -> x * p } } }) &&
                    k.phi == big(f.fold(1L) { a, (p, e) -> a * (p - 1) * (1 until e).fold(1L) { x, _ -> x * p } }) &&
                    k.nextPrime == big(generateSequence(n + 1) { it + 1 }.first(::isPrime)) &&
                    k.mu == (if (f.any { it.second > 1 }) 0 else if (f.size % 2 == 0) 1 else -1)
            })
        }
        // Congruences, every residue tried.
        repeat(40) {
            val m = r.int(2, 40); val a = r.int(1, 60); val b = r.int(0, 60)
            val want = (0 until m).filter { Math.floorMod(a * it - b, m) == 0 }
            out += Task(area, "${a}x ≡ $b mod $m", "$want", judge = congruence(want, m))
        }
        repeat(30) {
            val m = r.int(3, 30); val a = r.int(0, m - 1)
            val want = (0 until m).filter { Math.floorMod(it * it - a, m) == 0 }
            out += Task(area, "x^2 ≡ $a mod $m", "$want", judge = congruence(want, m))
        }
        repeat(30) {
            val m1 = r.int(2, 12); val m2 = r.int(2, 12); val a1 = r.int(0, m1 - 1); val a2 = r.int(0, m2 - 1)
            val l = m1 / gcd(m1, m2) * m2
            val want = (0 until l).filter { it % m1 == a1 && it % m2 == a2 }
            out += Task(area, "x ≡ $a1 mod $m1; x ≡ $a2 mod $m2", "$want (mod $l)", judge = congruence(want, l))
        }
        // Linear Diophantine equations: every solution a step apart; and Pell's equation's least solution.
        repeat(30) {
            val a = r.int(-12, 12, nonzero = true).toLong(); val b = r.int(1, 12).toLong(); val c = r.int(-30, 30).toLong()
            val g = big(a).gcd(big(b)).toLong()
            out += Task(area, "diophantine(${a}x + ${b}y = $c)", if (c % g == 0L) "solutions" else "none", judge = { calc ->
                when (val s = (calc.outcome as? Outcome.WholeSolutions)?.solution) {
                    is Discrete.Integers.Linear -> c % g == 0L && (-3..3).all { t ->
                        big(a) * (s.x0 + s.sx * big(t.toLong())) + big(b) * (s.y0 + s.sy * big(t.toLong())) == big(c)
                    } && s.sx.abs() == big(b / g) && s.sy.abs() == big(kotlin.math.abs(a) / g)
                    Discrete.Integers.None -> c % g != 0L
                    else -> false
                }
            })
        }
        for (d in listOf(2, 3, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 17, 18, 19, 20, 21, 22, 23, 24)) {
            val y = (1L..20000L).first { y -> val x2 = 1 + d * y * y; val x = kotlin.math.sqrt(x2.toDouble()).toLong().let { s -> (s - 1..s + 1).firstOrNull { it * it == x2 } }; x != null }
            val x = kotlin.math.sqrt((1 + d * y * y).toDouble()).let { Math.round(it) }
            out += Task(area, "diophantine(x^2 - ${d}y^2 = 1)", "x = $x, y = $y", judge = { c ->
                (c.outcome as? Outcome.WholeSolutions)?.solution?.let { it is Discrete.Integers.Pell && it.x1 == big(x) && it.y1 == big(y) } == true
            })
        }
        return out
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    /** The residues modulo [m] the engine names, taken modulo [m]: exactly [want] (none where [want] is empty). */
    private fun congruence(want: List<Int>, m: Int): (Calculation) -> Boolean = { c ->
        val k = (c.outcome as? Outcome.Congruence)?.solution
        if (k == null) false else {
            val got = (0 until m).filter { t -> k.residues.any { res -> (big(t.toLong()) - res).mod(k.modulus).signum() == 0 } }
            got == want && (want.isEmpty() || big(m.toLong()).mod(k.modulus).signum() == 0)
        }
    }

    // -----------------------------------------------------------------------------------------------------------------------
    // Counting.

    fun combinatorics(): List<Task> {
        val out = ArrayList<Task>()
        val area = "combinatorics"
        val pascal = Array(81) { arrayOfNulls<BigInteger>(81) }
        for (n in 0..80) for (k in 0..n) pascal[n][k] = if (k == 0 || k == n) BigInteger.ONE else pascal[n - 1][k - 1]!! + pascal[n - 1][k]!!
        val r = Random(1654)
        repeat(60) {
            val n = r.int(0, 80); val k = r.int(0, n)
            out += Task(area, "binom($n, $k)", "", judge = isWhole(pascal[n][k]!!))
        }
        repeat(30) {
            val n = r.int(1, 30); val k = r.int(0, n)
            out += Task(area, "npr($n, $k)", "", judge = isWhole((0 until k).fold(BigInteger.ONE) { p, j -> p * big((n - j).toLong()) }))
        }
        for (n in 0..25) out += Task(area, "catalan($n)", "", judge = isWhole(pascal[2 * n][n]!! / big(n + 1L)))
        // Bell numbers by the Bell triangle.
        val bell = ArrayList<BigInteger>()
        var row = listOf(BigInteger.ONE)
        bell += BigInteger.ONE
        for (n in 1..25) {
            val next = ArrayList<BigInteger>()
            next += row.last()
            for (x in row) next += next.last() + x
            row = next
            bell += row.first()
        }
        for (n in 0..25) out += Task(area, "bell($n)", "", judge = isWhole(bell[n]))
        val s2 = Array(31) { arrayOfNulls<BigInteger>(31) }
        val s1 = Array(31) { arrayOfNulls<BigInteger>(31) }
        for (n in 0..30) for (k in 0..30) {
            s2[n][k] = when { n == 0 && k == 0 -> BigInteger.ONE; n == 0 || k == 0 -> BigInteger.ZERO; else -> big(k.toLong()) * s2[n - 1][k]!! + s2[n - 1][k - 1]!! }
            s1[n][k] = when { n == 0 && k == 0 -> BigInteger.ONE; n == 0 || k == 0 -> BigInteger.ZERO; else -> big(n - 1L) * s1[n - 1][k]!! + s1[n - 1][k - 1]!! }
        }
        repeat(40) {
            val n = r.int(1, 30); val k = r.int(1, n)
            out += Task(area, "stirling($n, $k)", "", judge = isWhole(s2[n][k]!!))
            out += Task(area, "stirling1($n, $k)", "", judge = isWhole(s1[n][k]!!))
        }
        val parts = Array(201) { BigInteger.ZERO }.also { p ->
            p[0] = BigInteger.ONE
            for (part in 1..200) for (n in part..200) p[n] = p[n] + p[n - part]
        }
        for (n in listOf(0, 1, 2, 5, 10, 20, 37, 50, 75, 100, 150, 200)) out += Task(area, "partitions($n)", "", judge = isWhole(parts[n]))
        val fib = ArrayList<BigInteger>().also { f -> f += BigInteger.ZERO; f += BigInteger.ONE; while (f.size <= 300) f += f[f.size - 1] + f[f.size - 2] }
        val lucas = ArrayList<BigInteger>().also { f -> f += big(2); f += BigInteger.ONE; while (f.size <= 300) f += f[f.size - 1] + f[f.size - 2] }
        for (n in listOf(0, 1, 2, 3, 10, 20, 50, 71, 100, 150, 200, 300)) {
            out += Task(area, "fibonacci($n)", "", judge = isWhole(fib[n]))
            out += Task(area, "lucas($n)", "", judge = isWhole(lucas[n]))
        }
        return out
    }

    // -----------------------------------------------------------------------------------------------------------------------
    // Logic.

    private sealed class B {
        abstract fun eval(v: Map<String, Boolean>): Boolean
        abstract fun text(): String
        class V(val n: String) : B() { override fun eval(v: Map<String, Boolean>) = v.getValue(n); override fun text() = n }
        class Not(val a: B) : B() { override fun eval(v: Map<String, Boolean>) = !a.eval(v); override fun text(): String = "¬" + a.wrapped() }
        class Bin(val op: String, val a: B, val b: B) : B() {
            override fun eval(v: Map<String, Boolean>): Boolean = when (op) {
                "∧" -> a.eval(v) && b.eval(v); "∨" -> a.eval(v) || b.eval(v); "→" -> !a.eval(v) || b.eval(v)
                "⇔" -> a.eval(v) == b.eval(v); else -> a.eval(v) != b.eval(v)
            }
            override fun text(): String = a.wrapped() + " $op " + b.wrapped()
        }
        fun wrapped(): String = if (this is Bin) "(${text()})" else text()
    }

    private fun Random.formula(names: List<String>, depth: Int): B = when {
        depth == 0 || nextInt(4) == 0 -> B.V(names[nextInt(names.size)]).let { if (nextInt(4) == 0) B.Not(it) else it }
        nextInt(6) == 0 -> B.Not(formula(names, depth - 1))
        else -> B.Bin(listOf("∧", "∨", "→", "⇔", "⊕")[nextInt(5)], formula(names, depth - 1), formula(names, depth - 1))
    }

    /** The engine's own form evaluated by the test. */
    private fun evalBool(b: Logic.Bool, v: Map<String, Boolean>): Boolean = when (b) {
        is Logic.Bool.Var -> v.getValue(b.name)
        is Logic.Bool.Const -> b.value
        is Logic.Bool.Not -> !evalBool(b.a, v)
        is Logic.Bool.And -> b.parts.all { evalBool(it, v) }
        is Logic.Bool.Or -> b.parts.any { evalBool(it, v) }
        is Logic.Bool.Xor -> evalBool(b.a, v) != evalBool(b.b, v)
        is Logic.Bool.Implies -> !evalBool(b.a, v) || evalBool(b.b, v)
        is Logic.Bool.Iff -> evalBool(b.a, v) == evalBool(b.b, v)
    }

    private fun letters(b: B): Set<String> = when (b) {
        is B.V -> setOf(b.n); is B.Not -> letters(b.a); is B.Bin -> letters(b.a) + letters(b.b)
    }

    private fun rows(names: List<String>): List<Map<String, Boolean>> =
        (0 until (1 shl names.size)).map { row -> names.withIndex().associate { (k, n) -> n to ((row shr (names.size - 1 - k)) and 1 == 1) } }

    fun logic(): List<Task> {
        val r = Random(1847)
        val out = ArrayList<Task>()
        repeat(170) { i ->
            val all = listOf("p", "q", "r", "s").take(if (i % 4 == 0) 4 else 3)
            val f = r.formula(all, 3)
            val names = letters(f).sorted()
            if (names.size < 2) return@repeat
            val want = rows(names).map { f.eval(it) }
            out += Task("logic", "truthtable(${f.text()})", want.joinToString("") { if (it) "1" else "0" }, judge = { c ->
                val a = (c.outcome as? Outcome.LogicTable)?.analysis
                a != null && a.names == names && a.values == want && a.tautology == want.all { it } && a.contradiction == want.none { it } &&
                    rows(names).withIndex().all { (k, v) -> evalBool(a.dnf, v) == want[k] && evalBool(a.cnf, v) == want[k] }
            })
        }
        // Equivalences: laws that hold, and look-alikes that do not.
        val laws = listOf("¬(p ∧ q) = ¬p ∨ ¬q", "¬(p ∨ q) = ¬p ∧ ¬q", "p → q = ¬p ∨ q", "p → q = ¬q → ¬p", "p ∧ (q ∨ r) = (p ∧ q) ∨ (p ∧ r)",
            "p ∨ (q ∧ r) = (p ∨ q) ∧ (p ∨ r)", "p ⇔ q = (p → q) ∧ (q → p)", "p ⊕ q = (p ∨ q) ∧ ¬(p ∧ q)", "p ∨ (p ∧ q) = p", "p ∧ (p ∨ q) = p")
        val misses = listOf("p → q = q → p", "¬(p ∧ q) = ¬p ∧ ¬q", "p ⊕ q = p ⇔ q", "p ∨ (q ∧ r) = (p ∨ q) ∧ r", "p → (q → r) = (p → q) → r", "p ∧ q = p ∨ q")
        for (law in laws + misses) {
            val holds = law in laws
            out += Task("logic", law, if (holds) "equivalent" else "not equivalent", judge = { c -> (c.outcome as? Outcome.LogicTable)?.analysis?.equivalent == holds })
        }
        repeat(30) {
            val a = r.formula(listOf("p", "q"), 2); val b = r.formula(listOf("p", "q"), 2)
            val names = (letters(a) + letters(b)).sorted()
            if (names.size < 2) return@repeat
            val holds = rows(names).all { a.eval(it) == b.eval(it) }
            out += Task("logic", "${a.text()} = ${b.text()}", "$holds", judge = { c -> (c.outcome as? Outcome.LogicTable)?.analysis?.equivalent == holds })
        }
        return out
    }

    // -----------------------------------------------------------------------------------------------------------------------
    // Sets written out.

    fun sets(): List<Task> {
        val r = Random(1874)
        val out = ArrayList<Task>()
        fun Random.set(): Set<Int> = List(int(0, 6)) { int(-3, 12) }.toSet()
        fun text(s: Set<Int>) = if (s.isEmpty()) "∅" else s.joinToString(", ", "{", "}")
        val ops = listOf("∪", "∩", "∖")
        fun apply(op: String, a: Set<Int>, b: Set<Int>) = when (op) { "∪" -> a + b; "∩" -> a intersect b; else -> a - b }
        repeat(110) {
            val a = r.set().ifEmpty { setOf(1) }; val b = r.set().ifEmpty { setOf(2) }
            val op = ops[r.nextInt(3)]
            val want = apply(op, a, b)
            out += Task("sets", "${text(a)} $op ${text(b)}", text(want), judge = isSet(want))
        }
        repeat(50) {
            val a = r.set().ifEmpty { setOf(0) }; val b = r.set().ifEmpty { setOf(3) }; val c = r.set().ifEmpty { setOf(5) }
            val o1 = ops[r.nextInt(3)]; val o2 = ops[r.nextInt(3)]
            val want = apply(o2, apply(o1, a, b), c)
            out += Task("sets", "(${text(a)} $o1 ${text(b)}) $o2 ${text(c)}", text(want), judge = isSet(want))
        }
        return out
    }

    private fun isSet(want: Set<Int>): (Calculation) -> Boolean = { c ->
        val els = (c.outcome as? Outcome.SetValue)?.elements
        els != null && els.size == want.size && els.map { rational(it)?.let { q -> if (q.isInteger) q.num.toInt() else null } }.toSet() == want
    }
}

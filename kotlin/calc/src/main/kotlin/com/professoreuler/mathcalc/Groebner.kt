package com.professoreuler.mathcalc

import java.util.TreeMap

/**
 * Systems of polynomial equations by Gröbner bases (Buchberger's algorithm in the lexicographic order, with exact rationals):
 * the reduced basis of a system with finitely many solutions is triangular, its last polynomial in the last unknown alone.
 * That one is solved, its values put into the polynomials in the next unknown, and so on back to the first; every solution is
 * put into the equations as written before it is given. So xy = 2 with x² + y² = 5 has its four solutions, and
 * x + y + z = 6, xy + yz + zx = 11, xyz = 6 its six. Systems whose basis grows too large, or with infinitely many solutions,
 * are left (null).
 */
internal object Groebner {
    private const val MOST_POLYNOMIALS = 60
    private const val MOST_TERMS = 400
    private const val MOST_BITS = 4000

    /** An exponent vector, compared lexicographically: the first unknown counts most. */
    private class Mono(val e: IntArray) : Comparable<Mono> {
        override fun compareTo(other: Mono): Int {
            for (k in e.indices) if (e[k] != other.e[k]) return e[k].compareTo(other.e[k])
            return 0
        }
        override fun equals(other: Any?) = other is Mono && e.contentEquals(other.e)
        override fun hashCode() = e.contentHashCode()
        fun times(o: Mono) = Mono(IntArray(e.size) { e[it] + o.e[it] })
        fun divides(o: Mono) = e.indices.all { e[it] <= o.e[it] }
        fun over(o: Mono) = Mono(IntArray(e.size) { e[it] - o.e[it] })
        fun lcm(o: Mono) = Mono(IntArray(e.size) { maxOf(e[it], o.e[it]) })
        fun coprime(o: Mono) = e.indices.all { e[it] == 0 || o.e[it] == 0 }
        val isOne get() = e.all { it == 0 }
    }

    /** A polynomial: its terms, the leading (largest) first. */
    private class Poly(val terms: TreeMap<Mono, Rational>) {
        val isZero get() = terms.isEmpty()
        val leadMono: Mono get() = terms.lastKey()
        val leadCoef: Rational get() = terms.lastEntry().value

        fun monic(): Poly {
            val c = leadCoef
            return Poly(TreeMap(terms.mapValues { it.value / c }))
        }

        /** this − c·m·other. */
        fun minus(c: Rational, m: Mono, other: Poly): Poly {
            val out = TreeMap(terms)
            for ((k, v) in other.terms) {
                val key = k.times(m)
                val next = (out[key] ?: Rational.ZERO) - c * v
                if (next.signum == 0) out.remove(key) else out[key] = next
            }
            return Poly(out)
        }

        fun bits(): Int = terms.values.maxOfOrNull { it.num.bitLength() + it.den.bitLength() } ?: 0
    }

    /** The real solutions of [equations] (each = 0) in [letters], or null where this way finds none it can trust. */
    fun solve(equations: List<Expr>, letters: List<String>): List<Map<String, Expr>>? {
        if (letters.size !in 2..4) return null
        val polys = equations.map { toPoly(it, letters) ?: return null }.filter { !it.isZero }
        if (polys.isEmpty()) return null
        val basis = reducedBasis(polys) ?: return null
        // 1 in the basis: no solution at all.
        if (basis.any { it.leadMono.isOne }) return emptyList()
        // Finitely many solutions: each unknown has a pure power as some leading monomial.
        for (k in letters.indices) {
            if (basis.none { p -> p.leadMono.e.withIndex().all { (j, d) -> if (j == k) d > 0 else d == 0 } }) return null
        }
        val asExprs = basis.map { toExpr(it, letters) }
        var partial: List<Map<String, Expr>> = listOf(emptyMap())
        for (k in letters.indices.reversed()) {
            val v = letters[k]
            // The polynomials in this unknown and the later ones only.
            val here = basis.indices.filter { i -> basis[i].terms.keys.all { m -> (0 until k).all { m.e[it] == 0 } } }
                .filter { i -> basis[i].terms.keys.any { it.e[k] > 0 } }.map { asExprs[it] }
            if (here.isEmpty()) return null
            val next = ArrayList<Map<String, Expr>>()
            for (known in partial) {
                Budget.check()
                val inV = here.map { e -> var s = e; for ((name, value) in known) s = s.substitute(Expr.Sym(name), value); Algebra.expand(s) }
                    .filter { !isZero(it) }
                if (inV.isEmpty()) return null
                val x = Expr.Sym(v)
                // The one of lowest degree, solved; its values kept where the others hold too.
                val first = inV.minByOrNull { Solve.coefficientsIn(it, x)?.size ?: Int.MAX_VALUE } ?: return null
                val found = Solve.equation(first, Expr.ZERO, x)
                if (found.many || found.always || found.periodic.isNotEmpty()) return null
                for (value in found.values) {
                    if (inV.all { Solve.solves(it, Expr.ZERO, x, value) }) next += known + (v to value)
                }
            }
            partial = next
        }
        // Every solution in the equations as written.
        return partial.filter { sol ->
            equations.all { e ->
                var s = e
                for ((name, value) in sol) s = s.substitute(Expr.Sym(name), value)
                val d = Numeric.value(s)
                d != null && kotlin.math.abs(d) < 1e-8
            }
        }.map { sol -> sol.mapValues { Solve.simplifyValue(it.value) } }
    }

    private fun reducedBasis(input: List<Poly>): List<Poly>? {
        val g = input.map { it.monic() }.toMutableList()
        val pairs = ArrayDeque<Pair<Int, Int>>()
        for (i in g.indices) for (j in i + 1 until g.size) pairs += i to j
        while (pairs.isNotEmpty()) {
            Budget.check()
            val (i, j) = pairs.removeFirst()
            val a = g[i]
            val b = g[j]
            // Buchberger's first criterion: coprime leading monomials reduce to zero.
            if (a.leadMono.coprime(b.leadMono)) continue
            val l = a.leadMono.lcm(b.leadMono)
            val s = sPoly(a, b, l)
            val r = reduce(s, g)
            if (r.isZero) continue
            val m = r.monic()
            if (m.terms.size > MOST_TERMS || m.bits() > MOST_BITS) return null
            g += m
            if (g.size > MOST_POLYNOMIALS) return null
            for (k in 0 until g.size - 1) pairs += k to g.size - 1
        }
        // Minimal: no leading monomial divisible by another's; then each reduced by the rest.
        val minimal = g.filterIndexed { i, p -> g.indices.none { j -> j != i && g[j].leadMono.divides(p.leadMono) && (g[j].leadMono != p.leadMono || j < i) } }
        return minimal.map { p -> reduce(p, minimal.filter { it !== p }).monic() }.filter { !it.isZero }
    }

    private fun sPoly(a: Poly, b: Poly, l: Mono): Poly {
        val fromA = Poly(TreeMap()).minus(-Rational.ONE, l.over(a.leadMono), a)
        return fromA.minus(Rational.ONE, l.over(b.leadMono), b)
    }

    /** [p] reduced by [by]: no term of it divisible by a leading monomial of them. */
    private fun reduce(p: Poly, by: List<Poly>): Poly {
        var rest = p
        val out = TreeMap<Mono, Rational>()
        while (!rest.isZero) {
            Budget.check()
            val m = rest.leadMono
            val c = rest.leadCoef
            val d = by.firstOrNull { !it.isZero && it.leadMono.divides(m) }
            if (d != null) {
                rest = rest.minus(c / d.leadCoef, m.over(d.leadMono), d)
            } else {
                out[m] = c
                val next = TreeMap(rest.terms)
                next.remove(m)
                rest = Poly(next)
            }
            if (rest.terms.size > MOST_TERMS * 4) throw CalcException(Failure.TOO_LARGE)
        }
        return Poly(out)
    }

    private fun toPoly(e: Expr, letters: List<String>): Poly? {
        val kernels = Algebra.Kernels()
        val p = Algebra.polynomial(Algebra.expand(e), kernels) ?: return null
        if (!kernels.isEmpty || !letters.containsAll(p.unknowns)) return null
        val terms = TreeMap<Mono, Rational>()
        for ((m, c) in p.terms) terms[Mono(IntArray(letters.size) { k -> m.power(letters[k]) })] = c
        return Poly(terms)
    }

    private fun toExpr(p: Poly, letters: List<String>): Expr = add(p.terms.map { (m, c) ->
        mul(listOf(num(c)) + m.e.withIndex().filter { it.value > 0 }.map { (k, d) -> pow(Expr.Sym(letters[k]), num(d.toLong())) })
    })
}

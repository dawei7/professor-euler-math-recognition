package com.professoreuler.mathcalc

import kotlin.math.abs

/** Solves f(x) = 0 over the reals: exactly where f is a polynomial of degree two at most, numerically otherwise. */
internal object Solver {
    /** Beyond this many solutions in the searched range, an equation counts as having too many to list (sin x = 0). */
    private const val MOST = 10

    fun solve(f: Node, x: String): Outcome {
        val p = Poly.of(f, x)
        if (p != null && p.degree <= 2) return exact(p, x)
        return numeric(f, x)
    }

    private fun exact(p: Poly, x: String): Outcome {
        val c = p.coefficients
        val roots = when (p.degree) {
            -1 -> return Outcome.Solved(x, emptyList(), always = true)
            0 -> emptyList()
            1 -> listOf(Num.Exact(-c[0] / c[1]))
            else -> {
                val (a, b, k) = Triple(c[2], c[1], c[0])
                val d = b * b - Rational.of(4) * a * k
                when {
                    d.signum < 0 -> emptyList()
                    d.signum == 0 -> listOf(Num.Exact(-b / (Rational.of(2) * a)))
                    else -> {
                        val s = d.root(2)
                        if (s != null) {
                            listOf(-b - s, -b + s).map { Num.Exact(it / (Rational.of(2) * a)) }
                        } else {
                            val sd = Math.sqrt(d.toDouble())
                            listOf(-b.toDouble() - sd, -b.toDouble() + sd).map { Num.Approx(it / (2 * a.toDouble())) }
                        }
                    }
                }
            }
        }
        return Outcome.Solved(x, roots.sortedBy { it.double })
    }

    private fun numeric(f: Node, x: String): Outcome {
        fun value(v: Double): Double = try {
            f.eval(mapOf(x to Num.Approx(v))).double
        } catch (e: CalcException) {
            Double.NaN
        } catch (e: ArithmeticException) {
            Double.NaN
        }

        // Finer near 0, where the answers of exercises mostly are: steps of 0.01 to ±20, 0.1 to ±200, 1 to ±1000.
        val grid = ArrayList<Double>()
        for (k in -1000..-200) grid.add(k.toDouble())
        for (k in -1999..-200) grid.add(k / 10.0)
        for (k in -1999..1999) grid.add(k / 100.0)
        for (k in 200..1999) grid.add(k / 10.0)
        for (k in 200..1000) grid.add(k.toDouble())
        grid.sort()

        val found = mutableListOf<Double>()
        var a = grid[0]
        var fa = value(a)
        for (b in grid.drop(1)) {
            if (b == a) continue
            val fb = value(b)
            if (fa == 0.0) found.add(a)
            else if (fa.isFinite() && fb.isFinite() && fa * fb < 0) {
                var lo = a
                var hi = b
                var flo = fa
                for (step in 0 until 100) {
                    val m = (lo + hi) / 2
                    val fm = value(m)
                    if (fm == 0.0) { lo = m; hi = m; break }
                    if (!fm.isFinite()) break
                    if ((fm < 0) == (flo < 0)) { lo = m; flo = fm } else hi = m
                }
                val m = (lo + hi) / 2
                // A pole changes sign too (1/x at 0): only a point where f is about 0 is a solution.
                if (abs(value(m)) <= 1e-7 * (1 + abs(fa) + abs(fb))) found.add(m)
            }
            if (found.size > MOST) return Outcome.Failed(Failure.MANY_SOLUTIONS)
            a = b
            fa = fb
        }
        val roots = found.distinctBy { Math.round(it * 1e7) }.map { r ->
            val near = Rational.near(r, 1000, 1e-9)
            if (near != null && exactZero(f, x, near)) Num.Exact(near) else Num.Approx(r)
        }
        return Outcome.Solved(x, roots.distinctBy { it.rational ?: it.double })
    }

    /** Whether f is 0 at [r]: exactly, or within rounding where f needs doubles (e^x = 1 at 0). */
    private fun exactZero(f: Node, x: String, r: Rational): Boolean = try {
        when (val v = f.eval(mapOf(x to Num.Exact(r)))) {
            is Num.Exact -> v.r.signum == 0
            is Num.Approx -> abs(v.d) <= 1e-12
        }
    } catch (e: CalcException) {
        false
    }
}

/** A polynomial in one unknown with exact coefficients, lowest degree first; null where a formula is not one. */
internal class Poly(val coefficients: List<Rational>) {
    val degree: Int get() = coefficients.indexOfLast { it.signum != 0 }

    operator fun plus(o: Poly) = Poly(List(maxOf(coefficients.size, o.coefficients.size)) { at(it) + o.at(it) })
    operator fun unaryMinus() = Poly(coefficients.map { -it })
    operator fun times(o: Poly): Poly {
        val out = MutableList(coefficients.size + o.coefficients.size - 1) { Rational.ZERO }
        for ((i, a) in coefficients.withIndex()) for ((j, b) in o.coefficients.withIndex()) out[i + j] = out[i + j] + a * b
        return Poly(out)
    }

    private fun at(k: Int) = coefficients.getOrElse(k) { Rational.ZERO }

    companion object {
        private const val MOST_DEGREE = 12

        fun constant(r: Rational) = Poly(listOf(r))

        fun of(node: Node, x: String): Poly? {
            if (x !in node.unknowns()) {
                val v = try { node.eval(emptyMap()) } catch (e: CalcException) { return null }
                return (v as? Num.Exact)?.let { constant(it.r) }
            }
            return when (node) {
                is Unknown -> Poly(listOf(Rational.ZERO, Rational.ONE))
                is Add -> of(node.a, x)?.let { a -> of(node.b, x)?.let { a + it } }
                is Sub -> of(node.a, x)?.let { a -> of(node.b, x)?.let { a + -it } }
                is Neg -> of(node.a, x)?.let { -it }
                is Mul -> of(node.a, x)?.let { a -> of(node.b, x)?.let { b -> (a * b).takeIf { it.degree <= MOST_DEGREE } } }
                is Div -> {
                    val d = of(node.b, x)?.takeIf { it.degree == 0 } ?: return null
                    of(node.a, x)?.let { it * constant(Rational.ONE / d.coefficients[0]) }
                }
                is Pow -> {
                    if (x in node.b.unknowns()) return null
                    val e = (try { node.b.eval(emptyMap()) } catch (e: CalcException) { return null }) as? Num.Exact ?: return null
                    val n = e.r.takeIf { it.isInteger && it.signum >= 0 && it.num.bitLength() < 5 }?.num?.toInt() ?: return null
                    val base = of(node.a, x) ?: return null
                    if (base.degree * n > MOST_DEGREE) return null
                    var out = constant(Rational.ONE)
                    repeat(n) { out *= base }
                    out
                }
                else -> null
            }
        }
    }
}

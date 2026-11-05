package com.professoreuler.mathcalc

/**
 * Trigonometric simplification, after the idea of Fu, Zhong and Zeng's rule sets: a few rewrites, each simplified, and the
 * shortest form that is equal to the original wherever the original is defined ([Verify.replaces]). The rewrites are the sum
 * and multiple-angle formulas taken apart (sin 2x = 2 sin x cos x, sin(x + y) = sin x cos y + cos x sin y, tan = sin/cos), and
 * powers brought down to double angles (sin²u = (1 − cos 2u)/2, sin u cos u = sin 2u / 2). So sin 2x / sin x is 2 cos x,
 * cos 2x + 2 sin²x is 1, sin⁴x − cos⁴x is −cos 2x and sin(x + y) − sin x cos y is cos x sin y.
 */
internal object TrigForms {
    private val TRIG = setOf(Func.SIN, Func.COS, Func.TAN, Func.COT, Func.SEC, Func.CSC)

    fun has(e: Expr): Boolean = when (e) {
        is Expr.Fn -> e.f in TRIG || e.args.any(::has)
        is Expr.Add -> e.terms.any(::has)
        is Expr.Mul -> e.factors.any(::has)
        is Expr.Pow -> has(e.base) || has(e.exp)
        else -> false
    }

    /** The simplest of [simplified] (the usual simplification of [original]) and the rewrites of [original]. */
    fun best(original: Expr, simplified: Expr, simplify: (Expr) -> Expr): Expr {
        if (!has(original)) return simplified
        val candidates = ArrayList<Expr>()
        candidates += simplified
        fun attempt(f: () -> Expr) {
            try {
                Budget.check()
                candidates += f()
            } catch (e: CalcException) {
                if (e.failure == Failure.TOO_LONG) throw e
            } catch (e: ArithmeticException) {
            } catch (e: StackOverflowError) {
            }
        }
        var expanded: Expr? = null
        attempt { simplify(expand(original)).also { expanded = it } }
        attempt { simplify(linearize(simplified)) }
        expanded?.let { x -> attempt { simplify(linearize(x)) } }
        var best = simplified
        var cost = complexity(simplified)
        for (c in candidates.drop(1)) {
            val k = complexity(c)
            if (k < cost && Verify.replaces(original, c)) {
                best = c
                cost = k
            }
        }
        return best
    }

    /** How hard a form is to read: its size, each trigonometric function counted again. */
    private fun complexity(e: Expr): Int = e.size() + 2 * count(e)

    private fun count(e: Expr): Int = when (e) {
        is Expr.Fn -> (if (e.f in TRIG) 1 else 0) + e.args.sumOf(::count)
        is Expr.Add -> e.terms.sumOf(::count)
        is Expr.Mul -> e.factors.sumOf(::count)
        is Expr.Pow -> count(e.base) + count(e.exp)
        else -> 0
    }

    /** The sum and multiple-angle formulas taken apart, tan, cot, sec and csc as sin and cos. */
    fun expand(e: Expr): Expr {
        val inner = e.mapChildren { expand(it) }
        if (inner !is Expr.Fn || inner.f !in TRIG || inner.args.size != 1) return inner
        val u = inner.args[0]
        return when (inner.f) {
            Func.TAN -> mul(sinOf(u), pow(cosOf(u), Expr.MINUS_ONE))
            Func.COT -> mul(cosOf(u), pow(sinOf(u), Expr.MINUS_ONE))
            Func.SEC -> pow(cosOf(u), Expr.MINUS_ONE)
            Func.CSC -> pow(sinOf(u), Expr.MINUS_ONE)
            Func.SIN -> sinOf(u)
            else -> cosOf(u)
        }
    }

    /** sin u with u's sums and small multiples taken apart. */
    private fun sinOf(u: Expr): Expr {
        sum(u)?.let { (a, b) -> return add(mul(sinOf(a), cosOf(b)), mul(cosOf(a), sinOf(b))) }
        multiple(u)?.let { (n, v) ->
            return when (n) {
                2 -> mul(Expr.TWO, sinOf(v), cosOf(v))
                3 -> add(mul(num(3), sinOf(v)), mul(num(-4), pow(sinOf(v), num(3))))
                else -> fn(Func.SIN, u)
            }
        }
        return fn(Func.SIN, u)
    }

    private fun cosOf(u: Expr): Expr {
        sum(u)?.let { (a, b) -> return add(mul(cosOf(a), cosOf(b)), neg(mul(sinOf(a), sinOf(b)))) }
        multiple(u)?.let { (n, v) ->
            return when (n) {
                2 -> add(pow(cosOf(v), Expr.TWO), neg(pow(sinOf(v), Expr.TWO)))
                3 -> add(mul(num(4), pow(cosOf(v), num(3))), mul(num(-3), cosOf(v)))
                else -> fn(Func.COS, u)
            }
        }
        return fn(Func.COS, u)
    }

    /** u = a + b with a letter in each part (x + y, x + π/2), or null. */
    private fun sum(u: Expr): Pair<Expr, Expr>? {
        if (u !is Expr.Add || u.terms.size < 2) return null
        return u.terms.first() to add(u.terms.drop(1))
    }

    /** u = n·v with n a whole number 2 or 3 and v not a number (2x, 3θ), or null. */
    private fun multiple(u: Expr): Pair<Int, Expr>? {
        if (u !is Expr.Mul) return null
        val n = (u.factors.firstOrNull { it is Expr.Num } as? Expr.Num)?.value ?: return null
        if (!n.isInteger) return null
        val k = n.num.toInt()
        if (k !in 2..3) return null
        val rest = mul(u.factors.filter { it !is Expr.Num })
        if (rest.symbols().isEmpty()) return null
        return k to rest
    }

    /** Powers of sin and cos brought down to double angles: sin²u = (1 − cos 2u)/2, cos²u = (1 + cos 2u)/2, sin u cos u = sin 2u / 2. */
    fun linearize(e: Expr): Expr {
        val inner = e.mapChildren { linearize(it) }
        if (inner is Expr.Pow) {
            val b = inner.base
            val n = (inner.exp as? Expr.Num)?.value
            if (b is Expr.Fn && (b.f == Func.SIN || b.f == Func.COS) && n != null && n.isInteger && n.num.toInt() in 2..6 && n.num.toInt() % 2 == 0) {
                val double = fn(Func.COS, mul(Expr.TWO, b.args[0]))
                val square = mul(Expr.HALF, add(Expr.ONE, if (b.f == Func.SIN) neg(double) else double))
                return Algebra.expand(pow(square, num((n.num.toInt() / 2).toLong())))
            }
        }
        if (inner is Expr.Mul) {
            val sines = inner.factors.filter { it is Expr.Fn && it.f == Func.SIN }
            for (s in sines) {
                val u = (s as Expr.Fn).args[0]
                val c = inner.factors.firstOrNull { it is Expr.Fn && it.f == Func.COS && it.args[0] == u } ?: continue
                val rest = inner.factors.toMutableList().also { it.remove(s); it.remove(c) }
                return mul(rest + listOf(Expr.HALF, fn(Func.SIN, mul(Expr.TWO, u))))
            }
        }
        return inner
    }
}

package com.professoreuler.mathcalc

import java.math.BigInteger

/**
 * Algebra on expressions: multiplied out ([expand]), as polynomials and fractions of polynomials in their letters and
 * "kernels" (what is not a letter or a number, as sin x, √2, eˣ: each a letter of its own while the polynomial is worked
 * on), brought onto one fraction bar and cancelled ([cancel]), factored ([factor]), denominators made rational, logarithms
 * combined or split, and [simplify], which tries the forms that apply and keeps the simplest one that still has the
 * expression's value ([Verify]).
 */
object Algebra {

    // Multiplying out.

    /** Every product of sums and every whole power of a sum multiplied out, inside functions too: (x+2)² is x² + 4x + 4. */
    fun expand(e: Expr): Expr = when (e) {
        is Expr.Add -> add(e.terms.map { expand(it) })
        is Expr.Mul -> e.factors.map { expand(it) }.fold(Expr.ONE as Expr) { acc, f -> distribute(acc, f) }
        is Expr.Pow -> {
            val base = expand(e.base)
            val exp = expand(e.exp)
            val n = integer(exp)
            when {
                base is Expr.Add && n != null && n.signum() > 0 && n.bitLength() < 8 && n.toInt() <= Limits.MAX_EXPAND_POWER -> {
                    var out: Expr = Expr.ONE
                    repeat(n.toInt()) { out = distribute(out, base) }
                    out
                }
                // A product to a negative power: its denominator multiplied out, 1/(x+1)² as 1/(x² + 2x + 1).
                base is Expr.Add && n != null && n.signum() < 0 && n.bitLength() < 8 -> pow(expand(pow(base, num(-n.toLong()))), Expr.MINUS_ONE)
                // e^(a+b) stays one power.
                else -> pow(base, exp)
            }
        }
        is Expr.Fn -> fn(e.f, e.args.map { expand(it) })
        else -> e
    }

    /** a · b with every term of a times every term of b. */
    private fun distribute(a: Expr, b: Expr): Expr {
        val left = if (a is Expr.Add) a.terms else listOf(a)
        val right = if (b is Expr.Add) b.terms else listOf(b)
        if (left.size.toLong() * right.size > Limits.MAX_TERMS) throw CalcException(Failure.TOO_LARGE)
        // Each product may hold a sum or a whole positive power of one (x·(x+1)² after the first step): expand it again. A
        // sum in a denominator stays as it is.
        return add(left.flatMap { x -> right.map { y -> mul(x, y).let { p -> if (p is Expr.Mul && p.factors.any { expandable(it) }) expand(p) else p } } })
    }

    private fun expandable(f: Expr): Boolean = f is Expr.Add ||
        (f is Expr.Pow && f.base is Expr.Add && integer(f.exp)?.let { it.signum() > 0 } == true)

    // Polynomials in letters and kernels.

    /** The kernels of one calculation, each standing as a letter of its own while a polynomial is worked on. */
    class Kernels {
        private val toName = LinkedHashMap<Expr, String>()
        private val toExpr = HashMap<String, Expr>()

        fun name(k: Expr): String = toName.getOrPut(k) { "\u0001k${toName.size}".also { toExpr[it] = k } }

        fun expr(name: String): Expr = toExpr[name] ?: Expr.Sym(name)

        val isEmpty get() = toName.isEmpty()
    }

    /**
     * [e] as a polynomial with rational coefficients in its letters and kernels; null where it has a letter or kernel in a
     * denominator or under a power that is not whole, or a number that is not exact.
     */
    fun polynomial(e: Expr, kernels: Kernels): Polynomial? = when (e) {
        is Expr.Num -> Polynomial.constant(e.value)
        is Expr.Real -> null
        is Expr.Sym -> Polynomial.variable(e.name)
        is Expr.Add -> e.terms.fold(Polynomial.constant(Rational.ZERO) as Polynomial?) { acc, t -> acc?.let { a -> polynomial(t, kernels)?.let { a + it } } }
        is Expr.Mul -> e.factors.fold(Polynomial.ONE as Polynomial?) { acc, f -> acc?.let { a -> polynomial(f, kernels)?.let { a * it } } }
        is Expr.Pow -> {
            val n = integer(e.exp)
            when {
                n != null && n.signum() > 0 && n.bitLength() < 31 -> polynomial(e.base, kernels)?.pow(n.toInt())
                n != null && n.signum() < 0 -> null
                // √2, x^(1/2), eˣ: a kernel.
                else -> Polynomial.variable(kernels.name(e))
            }
        }
        is Expr.Const -> if (e.kind == Constant.INF) null else Polynomial.variable(kernels.name(e))
        is Expr.Fn -> Polynomial.variable(kernels.name(e))
        is Expr.Op, is Expr.Mat -> null
    }

    /** The polynomial as an expression again, its kernels put back. */
    fun toExpr(p: Polynomial, kernels: Kernels): Expr = add(p.terms.map { (m, c) ->
        mul(listOf(num(c)) + m.powers.map { (v, k) -> pow(kernels.expr(v), num(k.toLong())) })
    })

    // Fractions.

    /** [e] as one fraction: numerator over denominator, each a polynomial in letters and kernels; null where it is not. */
    fun fraction(e: Expr, kernels: Kernels): Pair<Polynomial, Polynomial>? {
        fun of(x: Expr): Pair<Polynomial, Polynomial>? = when (x) {
            is Expr.Add -> x.terms.map { of(it) ?: return null }.reduce { (an, ad), (bn, bd) ->
                if (ad == bd) (an + bn) to ad else (an * bd + bn * ad) to (ad * bd)
            }
            is Expr.Mul -> x.factors.map { of(it) ?: return null }.reduce { (an, ad), (bn, bd) -> (an * bn) to (ad * bd) }
            is Expr.Pow -> {
                val n = integer(x.exp)
                if (n != null && n.signum() < 0 && n.bitLength() < 31) {
                    val (bn, bd) = of(x.base) ?: return null
                    if (bn.isZero) throw CalcException(Failure.DIVISION_BY_ZERO)
                    bd.pow(-n.toInt()) to bn.pow(-n.toInt())
                } else polynomial(x, kernels)?.let { it to Polynomial.ONE }
            }
            else -> polynomial(x, kernels)?.let { it to Polynomial.ONE }
        }
        return of(e)
    }

    /**
     * [e] on one fraction bar with what numerator and denominator share cancelled: (x² − 1)/(x − 1) is x + 1, and
     * 1/x + 1/(x+1) is (2x + 1)/(x(x + 1)). The numerator multiplied out, the denominator as factors. Null where [e] is not a
     * fraction of polynomials (in its letters and kernels). [excluded] collects the factors cancelled from the denominator:
     * where they are 0 the original is not defined.
     */
    fun cancel(e: Expr, excluded: MutableList<Expr>? = null): Expr? {
        val kernels = Kernels()
        val (n0, d0) = fraction(e, kernels) ?: return null
        if (d0.isZero) throw CalcException(Failure.DIVISION_BY_ZERO)
        if (n0.isZero) return Expr.ZERO
        val g = gcd(n0, d0)
        val n = n0.divideExactly(g) ?: return null
        val d = d0.divideExactly(g) ?: return null
        if (excluded != null && !g.isConstant) excluded += toExpr(g, kernels)
        // Leading numbers: the denominator's leading coefficient positive and whole numbers inside.
        val dProduct = Factor.of(d)
        val c = dProduct.constant
        val num = toExpr(n * (Rational.ONE / c), kernels)
        if (dProduct.factors.isEmpty()) return num
        val den = mul(dProduct.factors.map { (f, k) -> pow(toExpr(f, kernels), num(k.toLong())) })
        return mul(num, pow(den, Expr.MINUS_ONE))
    }

    /** The greatest common divisor of two polynomials over the rationals: by factoring the smaller, then by Euclid in one letter. */
    fun gcd(a: Polynomial, b: Polynomial): Polynomial {
        if (a.isZero) return b
        if (b.isZero) return a
        if (a.isConstant || b.isConstant) return Polynomial.ONE
        val letters = a.unknowns + b.unknowns
        if (letters.size == 1) return univariateGcd(a, b, letters.first())
        // Several letters: the factors of the one with fewer terms that divide the other.
        val small = if (a.size <= b.size) a else b
        val large = if (small === a) b else a
        var g = Polynomial.ONE
        var rest = large
        for ((f, k) in Factor.of(small).factors) repeat(k) {
            val divided = rest.divideExactly(f) ?: return@repeat
            rest = divided
            g *= f
        }
        return g
    }

    private fun univariateGcd(a: Polynomial, b: Polynomial, x: String): Polynomial {
        fun coefficients(p: Polynomial) = List(p.degreeIn(x) + 1) { k -> p.terms[if (k == 0) Monomial.ONE else Monomial(listOf(x to k))] ?: Rational.ZERO }
        var u = coefficients(a)
        var v = coefficients(b)
        while (v.any { it.signum != 0 }) {
            val r = remainder(u, v)
            u = v
            v = r
        }
        val lead = u.last()
        return Polynomial(u.withIndex().filter { it.value.signum != 0 }.associate { (k, c) ->
            (if (k == 0) Monomial.ONE else Monomial(listOf(x to k))) to c / lead
        })
    }

    private fun remainder(a: List<Rational>, b: List<Rational>): List<Rational> {
        val d = b.dropLastWhile { it.signum == 0 }
        val r = a.dropLastWhile { it.signum == 0 }.toMutableList()
        while (r.size >= d.size && r.isNotEmpty()) {
            val shift = r.size - d.size
            val k = r.last() / d.last()
            for ((i, c) in d.withIndex()) r[i + shift] = r[i + shift] - c * k
            while (r.isNotEmpty() && r.last().signum == 0) r.removeAt(r.size - 1)
        }
        return r
    }

    // Factoring.

    /** [e] as a product: a polynomial's factors, a fraction's numerator and denominator each in factors; null where neither. */
    fun factor(e: Expr): Pair<Expr, Product?>? {
        val kernels = Kernels()
        val (n, d) = fraction(e, kernels) ?: return null
        if (d.isConstant) {
            val p = polynomial(e, kernels) ?: return null
            val product = Factor.of(p)
            return productExpr(product, kernels) to product
        }
        val g = gcd(n, d)
        val num = Factor.of(n.divideExactly(g) ?: n)
        val den = Factor.of(d.divideExactly(g) ?: d)
        return mul(productExpr(num, kernels), pow(productExpr(den, kernels), Expr.MINUS_ONE)) to null
    }

    /** A polynomial's factors over the reals and over the complex numbers ([Split]), or null where [e] is no polynomial. */
    fun factorFurther(e: Expr): Pair<Expr?, Expr?>? {
        val kernels = Kernels()
        val p = polynomial(e, kernels) ?: return null
        if (p.isConstant) return null
        return Split.of(Factor.of(p), kernels)
    }

    private fun productExpr(p: Product, kernels: Kernels): Expr =
        mul(listOf(num(p.constant)) + p.factors.map { (f, k) -> pow(toExpr(f, kernels), num(k.toLong())) })

    // Roots in denominators.

    /** A denominator with a root made rational: 1/√2 = √2/2 (already so), 1/(1 + √2) = √2 − 1, 2/(√3 − 1) = √3 + 1. */
    fun rationalize(e: Expr): Expr {
        if (e !is Expr.Mul && e !is Expr.Pow) return e.mapChildren { rationalize(it) }
        val factors = if (e is Expr.Mul) e.factors else listOf(e)
        val out = ArrayList<Expr>()
        var changed = false
        for (f in factors) {
            // (a + b√c)^-1 = (a − b√c)/(a² − b²c).
            if (f is Expr.Pow && f.exp == Expr.MINUS_ONE && f.base is Expr.Add && f.base.terms.size == 2 && f.base.isConstant()) {
                val (a, b) = f.base.terms
                val radical = listOf(a, b).firstOrNull { hasSquareRoot(it) }
                if (radical != null) {
                    val other = if (radical === a) b else a
                    val conjugate = add(other, neg(radical))
                    val norm = expand(add(mul(other, other), neg(mul(radical, radical))))
                    if (norm.isConstant() && !hasSquareRoot(norm) && !isZero(norm)) {
                        out += conjugate
                        out += pow(norm, Expr.MINUS_ONE)
                        changed = true
                        continue
                    }
                }
            }
            out += f
        }
        return if (changed) expand(mul(out)) else e
    }

    private fun hasSquareRoot(e: Expr): Boolean = when (e) {
        is Expr.Pow -> e.exp == Expr.HALF && e.base is Expr.Num
        is Expr.Mul -> e.factors.any { hasSquareRoot(it) }
        else -> false
    }

    // Logarithms.

    /** ln a + ln b as ln(ab), k·ln a as ln(aᵏ), ln a − ln b as ln(a/b): a sum's logarithms on one. */
    fun combineLogs(e: Expr): Expr {
        val inner = e.mapChildren { combineLogs(it) }
        if (inner !is Expr.Add) return inner
        val logs = ArrayList<Expr>()
        val others = ArrayList<Expr>()
        for (t in inner.terms) {
            val (c, rest) = splitCoefficient(t)
            val k = rational(c)
            if (rest is Expr.Fn && rest.f == Func.LN && k != null && k.isInteger) logs += pow(rest.args[0], c) else others += t
        }
        if (logs.size < 2) return inner
        return add(others + ln(mul(logs)))
    }

    /** ln(ab) as ln a + ln b, ln(aᵏ) as k ln a, ln(a/b) as ln a − ln b: logarithms split into their parts. */
    fun expandLogs(e: Expr): Expr {
        val inner = e.mapChildren { expandLogs(it) }
        if (inner is Expr.Fn && (inner.f == Func.LN || inner.f == Func.LOG)) {
            val arg = inner.args.last()
            fun log(x: Expr): Expr = if (inner.f == Func.LN) ln(x) else fn(Func.LOG, inner.args[0], x)
            return when {
                arg is Expr.Mul -> add(arg.factors.map { expandLogs(log(it)) })
                arg is Expr.Pow && arg.base !is Expr.Num -> mul(arg.exp, expandLogs(log(arg.base)))
                else -> inner
            }
        }
        return inner
    }

    // Trigonometry.

    /** tan, cot, sec, csc written with sin and cos. */
    fun sinCos(e: Expr): Expr {
        val inner = e.mapChildren { sinCos(it) }
        if (inner !is Expr.Fn) return inner
        val x = inner.args[0]
        return when (inner.f) {
            Func.TAN -> fn(Func.SIN, x) / fn(Func.COS, x)
            Func.COT -> fn(Func.COS, x) / fn(Func.SIN, x)
            Func.SEC -> Expr.ONE / fn(Func.COS, x)
            Func.CSC -> Expr.ONE / fn(Func.SIN, x)
            else -> inner
        }
    }

    /**
     * cos² u replaced by 1 − sin² u (or the other way round) where that makes the expression smaller, in a fraction too:
     * (1 − sin² x)/cos x is cos x.
     */
    fun pythagoras(e: Expr): Expr {
        var best = e
        // For each angle with both sin and cos, try both replacements.
        val angles = trigArguments(e)
        for (u in angles) {
            for (toSin in listOf(true, false)) {
                val replaced = replaceSquares(e, u, toSin)
                val expanded = expand(replaced)
                if (expanded.size() < best.size()) best = expanded
            }
        }
        return best
    }

    private fun trigArguments(e: Expr): Set<Expr> {
        val out = LinkedHashSet<Expr>()
        fun walk(x: Expr) {
            if (x is Expr.Fn && (x.f == Func.SIN || x.f == Func.COS)) out += x.args[0]
            when (x) {
                is Expr.Add -> x.terms.forEach(::walk)
                is Expr.Mul -> x.factors.forEach(::walk)
                is Expr.Pow -> { walk(x.base); walk(x.exp) }
                is Expr.Fn -> x.args.forEach(::walk)
                else -> Unit
            }
        }
        walk(e)
        return out
    }

    /** Every even power of cos u as a power of 1 − sin² u ([toSin]), or of sin u as one of 1 − cos² u. */
    private fun replaceSquares(e: Expr, u: Expr, toSin: Boolean): Expr {
        val from = if (toSin) fn(Func.COS, u) else fn(Func.SIN, u)
        val other = if (toSin) fn(Func.SIN, u) else fn(Func.COS, u)
        fun walk(x: Expr): Expr {
            if (x is Expr.Pow && x.base == from) {
                val n = integer(x.exp)
                if (n != null && n.signum() > 0 && !n.testBit(0)) return pow(add(Expr.ONE, neg(pow(other, Expr.TWO))), num(n.toLong() / 2))
            }
            return x.mapChildren(::walk)
        }
        return walk(e)
    }

    // Simplify.

    /**
     * The simplest form of [e] that keeps its value: a polynomial multiplied out, a fraction on one bar with its common
     * factors cancelled, roots in denominators made rational, logarithms combined, sin² + cos² taken as 1. Every candidate is
     * compared with [e] at random values ([Verify]); one that differs is never chosen. [excluded] collects factors cancelled
     * from a denominator.
     */
    fun simplify(e: Expr, excluded: MutableList<Expr>? = null): Expr {
        val candidates = ArrayList<Expr>()
        candidates += e
        fun attempt(f: () -> Expr?) {
            try {
                f()?.let { candidates += it }
            } catch (x: CalcException) {
                if (x.failure == Failure.DIVISION_BY_ZERO) throw x
            } catch (x: ArithmeticException) {
            }
        }
        val inside = e.mapChildren { if (it is Expr.Fn) fn(it.f, it.args.map { a -> simplify(a) }) else it }
        // A polynomial (in its letters and kernels) is multiplied out and its like terms collected: (x+2)² is x² + 4x + 4.
        val polynomialForm = try {
            fraction(e, Kernels())?.second?.isConstant == true
        } catch (x: CalcException) {
            false
        }
        val base = if (polynomialForm) (try { expand(inside) } catch (x: CalcException) { inside }) else inside
        if (polynomialForm) candidates.clear()
        attempt { base }
        // A letter in a denominator: the whole on one fraction bar, its common factors cancelled (1/x + 1/(x+1) is
        // (2x + 1)/(x(x + 1))), as a learner is asked to write it.
        if (!polynomialForm) {
            val cancelled = ArrayList<Expr>()
            val one = try { cancel(inside, cancelled) } catch (x: CalcException) { if (x.failure == Failure.DIVISION_BY_ZERO) throw x; null }
            if (one != null && Verify.replaces(e, one) || one != null && Verify.same(e, one)) {
                // With tan, sec and the like written in sin and cos, more may cancel: 1/cos² x · cos x − tan x · sin x is cos x.
                if (trigArguments(sinCos(e)).isNotEmpty()) {
                    val inTrig = ArrayList<Expr>()
                    val trig = try { cancel(sinCos(inside), inTrig)?.let { pythagoras(it) } } catch (x: CalcException) { if (x.failure == Failure.DIVISION_BY_ZERO) throw x; null }
                    if (trig != null && complexity(trig) < complexity(one) && Verify.replaces(e, trig)) {
                        excluded?.addAll(inTrig)
                        return trig
                    }
                }
                excluded?.addAll(cancelled)
                return one
            }
        }
        if (!polynomialForm) attempt { expand(inside) }
        attempt { rationalize(base) }
        attempt { combineLogs(base) }
        attempt { pythagoras(expand(base)) }
        attempt { cancel(sinCos(base))?.let { pythagoras(it) } }
        val best = candidates.filter { it == e || Verify.replaces(e, it) }.minWithOrNull(compareBy<Expr> { complexity(it) })
        return best ?: e
    }

    /** How complicated an expression looks: its size, with fraction bars and roots a little heavier. */
    fun complexity(e: Expr): Int = e.size() + when (e) {
        is Expr.Mul -> e.factors.count { it is Expr.Pow && rational(it.exp)?.signum == -1 }
        else -> 0
    }

    /** n! of a whole n below [Limits.MAX_FACTORIAL], as a number. */
    internal fun factorial(n: Int): BigInteger {
        var p = BigInteger.ONE
        for (k in 2..n) p *= BigInteger.valueOf(k.toLong())
        return p
    }
}

package com.professoreuler.mathcalc

/**
 * A polynomial's factors taken further than the rationals allow: over the real numbers (x² − 2 = (x − √2)(x + √2)) and over
 * the complex numbers (x² + 1 = (x − i)(x + i)). Quadratic factors split by their formula; an irreducible cubic or quartic
 * splits where its roots have a form worth reading ([Roots]). Each product is multiplied out numerically and compared with the
 * polynomial before it is given.
 */
internal object Split {
    /** The product over the reals and over the complex numbers, each null where it is no further than the rational one. */
    fun of(product: Product, kernels: Algebra.Kernels): Pair<Expr?, Expr?> {
        var realChanged = false
        var complexChanged = false
        val real = ArrayList<Expr>()
        val complex = ArrayList<Expr>()
        real += num(product.constant)
        complex += num(product.constant)
        for ((f, k) in product.factors) {
            val whole = pow(Algebra.toExpr(f, kernels), num(k.toLong()))
            val letter = f.unknowns.singleOrNull()
            val degree = letter?.let { f.degreeIn(it) } ?: 0
            if (letter == null || degree < 2 || kernels.expr(letter) != Expr.Sym(letter)) {
                real += whole
                complex += whole
                continue
            }
            val c = (0..degree).map { d -> f.coefficientsIn(letter)[d]?.constant ?: Rational.ZERO }
            val x = Expr.Sym(letter)
            val roots = rootsOf(c) ?: run {
                real += whole
                complex += whole
                null
            } ?: continue
            val (realRoots, complexRoots) = roots
            val lead = num(c.last())
            // x + √2 + √3, not x − (−√2 − √3).
            fun linear(r: Expr) = pow(Algebra.expand(add(x, neg(r))), num(k.toLong()))
            if (complexRoots.isEmpty()) {
                real += pow(lead, num(k.toLong()))
                complex += pow(lead, num(k.toLong()))
                realRoots.forEach { real += linear(it); complex += linear(it) }
                realChanged = true
                complexChanged = true
            } else {
                real += whole
                // Over the complex numbers every root is a factor.
                complex += pow(lead, num(k.toLong()))
                (realRoots + complexRoots).forEach { complex += linear(it) }
                complexChanged = true
            }
        }
        val expanded = Algebra.toExpr(product.expanded(), kernels)
        val overReals = if (realChanged) mul(real).takeIf { same(it, expanded) } else null
        val overComplex = if (complexChanged) mul(complex).takeIf { same(it, expanded) && it != overReals } else null
        return overReals to overComplex
    }

    /** The roots of Σ cₖ xᵏ (degree 2 to 4), real and not, each a form worth reading; null where they have none. */
    private fun rootsOf(c: List<Rational>): Pair<List<Expr>, List<Expr>>? {
        val roots = if (c.size == 3) {
            val (k, b, a) = c
            val d = b * b - Rational.of(4) * a * k
            val twoA = Rational.of(2) * a
            if (d.signum > 0) {
                val s = sqrt(num(d))
                listOf(add(num(-b / twoA), mul(num(-Rational.ONE / twoA), s)), add(num(-b / twoA), mul(num(Rational.ONE / twoA), s))) to emptyList()
            } else {
                val s = mul(Expr.I, sqrt(num(-d)))
                emptyList<Expr>() to listOf(add(num(-b / twoA), mul(num(-Rational.ONE / twoA), s)), add(num(-b / twoA), mul(num(Rational.ONE / twoA), s)))
            }
        } else {
            Roots.exact(c) ?: return null
        }
        val all = roots.first + roots.second
        if (all.size != c.size - 1) return null
        val simplified = roots.first.map { Solve.simplifyValue(it) } to roots.second.map { Solve.simplifyValue(it) }
        if ((simplified.first + simplified.second).any { !MathCalc.niceExact(it) && !it.hasI() }) return null
        if (simplified.second.any { it.size() > 30 }) return null
        return simplified
    }

    /** The product and the polynomial agree at a few values of its letter (complex arithmetic where i is in it). */
    private fun same(a: Expr, b: Expr): Boolean = Verify.same(a, b)
}

package com.professoreuler.mathcalc

/**
 * Curves through points (xᵢ, yᵢ) with rational coordinates, exact with fractions: the interpolating polynomial of degree at
 * most n − 1 (`interpolate`), worked out by Newton's divided differences and, independently, by Lagrange's basis polynomials,
 * the two compared coefficient by coefficient and the polynomial put back at every point; and the natural cubic spline
 * (`spline`), a cubic on each interval between neighbouring points with S″ = 0 at both ends, its second derivatives from the
 * tridiagonal system, and the result checked at every point and for equal first and second derivatives where two pieces meet.
 */
object Interpolation {
    /** The most points a polynomial is put through (degree 19), and a spline. */
    const val MOST_POINTS = 20
    const val MOST_SPLINE_POINTS = 50

    /**
     * The polynomial through the points [xs], [ys] (in the order given) in [letter]: [polynomial] multiplied out; [newton] the
     * coefficients cₖ = f[x₀, …, xₖ] of its Newton form c₀ + c₁(x − x₀) + c₂(x − x₀)(x − x₁) + … (written out by
     * [newtonLatex]); [lagrange] the basis polynomials Lᵢ(x) = ∏ⱼ≠ᵢ (x − xⱼ)/(xᵢ − xⱼ) as products (so p = Σ yᵢ·Lᵢ); and the
     * [differences]: column k holds f[xᵢ, …, xᵢ₊ₖ] for i = 0 … n − 1 − k, its first entry Newton's cₖ.
     */
    class Interpolant(
        val letter: String, val xs: List<Expr>, val ys: List<Expr>, val polynomial: Expr, val newton: List<Expr>, val lagrange: List<Expr>,
        val differences: List<List<Expr>>,
        /** The degree of the polynomial: at most n − 1, less where the points lie on a lower one (0 for p = 0). */
        val degree: Int,
    )

    /**
     * One piece of a spline: on [[from], [to]] the cubic [a] + [b](x − from) + [c](x − from)² + [d](x − from)³ (written out by
     * [pieceLatex]), multiplied out as [polynomial].
     */
    class Piece(val from: Expr, val to: Expr, val a: Expr, val b: Expr, val c: Expr, val d: Expr, val polynomial: Expr)

    /**
     * The natural cubic spline in [letter] through the points sorted by x ([xs], [ys]): its [pieces], one to an interval, and
     * the [moments] Mᵢ = S″(xᵢ) (the first and the last 0).
     */
    class Spline(val letter: String, val xs: List<Expr>, val ys: List<Expr>, val moments: List<Expr>, val pieces: List<Piece>)

    private fun tidy(e: Expr) = Calculus.tidy(e)

    /** The points of a matrix with two columns, one to a row, as rationals: the function takes no other arguments. */
    private fun points(m: Expr.Mat, most: Int): Pair<List<Rational>, List<Rational>> {
        if (m.columns != 2 || m.size < 2) throw CalcException(Failure.ARGUMENTS)
        if (m.symbols().isNotEmpty()) throw CalcException(Failure.ARGUMENTS)
        if (m.size > most) throw CalcException(Failure.TOO_LARGE)
        val xs = m.rows.map { rational(tidy(it[0])) ?: throw CalcException(Failure.UNSOLVED) }
        val ys = m.rows.map { rational(tidy(it[1])) ?: throw CalcException(Failure.UNSOLVED) }
        if (xs.toSet().size != xs.size) throw CalcException(Failure.SAME_X)
        return xs to ys
    }

    /** The choices that fit points written as a matrix: an interpolation and a spline where their x differ. */
    internal fun offered(m: Expr.Mat): List<Choice> {
        if (m.columns != 2 || m.size < 3 || m.symbols().isNotEmpty()) return emptyList()
        val xs = m.rows.map { rational(it[0]) ?: return emptyList() }
        if (m.rows.any { rational(it[1]) == null } || xs.toSet().size != xs.size) return emptyList()
        return listOfNotNull(Choice(Mode.INTERPOLATION).takeIf { m.size <= MOST_POINTS }, Choice(Mode.SPLINE).takeIf { m.size <= MOST_SPLINE_POINTS })
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Polynomials as rational coefficients, lowest first.

    private fun times(p: List<Rational>, root: Rational): List<Rational> {
        // p·(x − root)
        val out = MutableList(p.size + 1) { Rational.ZERO }
        for ((k, c) in p.withIndex()) {
            out[k + 1] = out[k + 1] + c
            out[k] = out[k] - c * root
        }
        return out
    }

    private fun plus(p: List<Rational>, q: List<Rational>): List<Rational> =
        List(maxOf(p.size, q.size)) { (p.getOrNull(it) ?: Rational.ZERO) + (q.getOrNull(it) ?: Rational.ZERO) }

    private fun scale(p: List<Rational>, s: Rational): List<Rational> = p.map { it * s }

    private fun at(p: List<Rational>, x: Rational): Rational = p.foldRight(Rational.ZERO) { c, acc -> acc * x + c }

    private fun derivative(p: List<Rational>): List<Rational> = p.drop(1).mapIndexed { k, c -> c * Rational.of(k + 1L) }

    private fun trim(p: List<Rational>): List<Rational> = p.dropLastWhile { it.signum == 0 }

    private fun expr(p: List<Rational>, x: Expr): Expr =
        tidy(add(p.mapIndexed { k, c -> mul(num(c), pow(x, num(k.toLong()))) }))

    // ---------------------------------------------------------------------------------------------------------------------
    // The interpolating polynomial.

    /** The polynomial through the rows of [m] (x, y), in x. */
    fun interpolate(m: Expr.Mat, letter: String = "x"): Interpolant {
        val (xs, ys) = points(m, MOST_POINTS)
        val n = xs.size
        val x = Expr.Sym(letter)
        // Newton's divided differences.
        val table = ArrayList<List<Rational>>()
        table += ys
        for (k in 1 until n) {
            Budget.check()
            val before = table[k - 1]
            table += List(n - k) { i -> (before[i + 1] - before[i]) / (xs[i + k] - xs[i]) }
        }
        var newton = listOf(table[n - 1][0])
        for (k in n - 2 downTo 0) newton = plus(times(newton, xs[k]), listOf(table[k][0]))
        // Lagrange's form, worked out on its own: Σ yᵢ ∏ⱼ≠ᵢ (x − xⱼ)/(xᵢ − xⱼ).
        var lagrange = emptyList<Rational>()
        val basis = ArrayList<List<Rational>>()
        for (i in 0 until n) {
            Budget.check()
            var l = listOf(Rational.ONE)
            var den = Rational.ONE
            for (j in 0 until n) if (j != i) {
                l = times(l, xs[j])
                den = den * (xs[i] - xs[j])
            }
            l = scale(l, Rational.ONE / den)
            basis += l
            lagrange = plus(lagrange, scale(l, ys[i]))
        }
        // The two forms agree, and the polynomial passes through every point.
        val p = trim(newton)
        if (p != trim(lagrange)) throw CalcException(Failure.UNSOLVED)
        if (xs.indices.any { at(p, xs[it]) != ys[it] }) throw CalcException(Failure.UNSOLVED)
        if (p.size > n) throw CalcException(Failure.UNSOLVED)

        val polynomial = expr(p, x)
        // Newton's form as written: each coefficient by the product of (x − xⱼ) of the points before it.
        val newtonForm = add((0 until n).map { k ->
            mul(listOf(num(table[k][0])) + (0 until k).map { j -> add(x, num(-xs[j])) })
        }.filter { !isZero(it) }.ifEmpty { listOf(Expr.ZERO) })
        val lagrangeForms = (0 until n).map { i ->
            var den = Rational.ONE
            for (j in 0 until n) if (j != i) den = den * (xs[i] - xs[j])
            mul(listOf(num(Rational.ONE / den)) + (0 until n).filter { it != i }.map { j -> add(x, num(-xs[j])) })
        }
        // The forms shown multiply out to the polynomial.
        if (!Verify.same(Algebra.expand(newtonForm), polynomial)) throw CalcException(Failure.UNSOLVED)
        if (!Verify.same(Algebra.expand(add(lagrangeForms.mapIndexed { i, l -> mul(num(ys[i]), l) })), polynomial)) throw CalcException(Failure.UNSOLVED)
        return Interpolant(letter, xs.map { num(it) }, ys.map { num(it) }, polynomial, (0 until n).map { num(table[it][0]) }, lagrangeForms,
            table.map { col -> col.map { num(it) } }, maxOf(0, p.size - 1))
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The natural cubic spline.

    /** The natural cubic spline through the rows of [m] (x, y), sorted by x, in x. */
    fun spline(m: Expr.Mat, letter: String = "x"): Spline {
        val (xs0, ys0) = points(m, MOST_SPLINE_POINTS)
        val order = xs0.indices.sortedBy { xs0[it] }
        val xs = order.map { xs0[it] }
        val ys = order.map { ys0[it] }
        val n = xs.size
        val x = Expr.Sym(letter)
        val h = (0 until n - 1).map { xs[it + 1] - xs[it] }
        // The second derivatives Mᵢ: M₀ = Mₙ₋₁ = 0, and for each inner point
        // hᵢ₋₁Mᵢ₋₁ + 2(hᵢ₋₁ + hᵢ)Mᵢ + hᵢMᵢ₊₁ = 6((yᵢ₊₁ − yᵢ)/hᵢ − (yᵢ − yᵢ₋₁)/hᵢ₋₁), by Thomas's algorithm (diagonally dominant).
        val moments = MutableList(n) { Rational.ZERO }
        val inner = n - 2
        if (inner > 0) {
            val sub = List(inner) { k -> h[k] }
            val diag = List(inner) { k -> Rational.of(2) * (h[k] + h[k + 1]) }
            val sup = List(inner) { k -> h[k + 1] }
            val rhs = List(inner) { k -> Rational.of(6) * ((ys[k + 2] - ys[k + 1]) / h[k + 1] - (ys[k + 1] - ys[k]) / h[k]) }
            val c = MutableList(inner) { Rational.ZERO }
            val d = MutableList(inner) { Rational.ZERO }
            for (k in 0 until inner) {
                Budget.check()
                val pivot = diag[k] - (if (k > 0) sub[k] * c[k - 1] else Rational.ZERO)
                c[k] = sup[k] / pivot
                d[k] = (rhs[k] - (if (k > 0) sub[k] * d[k - 1] else Rational.ZERO)) / pivot
            }
            for (k in inner - 1 downTo 0) moments[k + 1] = d[k] - (if (k < inner - 1) c[k] * moments[k + 2] else Rational.ZERO)
            // The system's equations hold.
            for (k in 0 until inner) {
                if (sub[k] * moments[k] + diag[k] * moments[k + 1] + sup[k] * moments[k + 2] != rhs[k]) throw CalcException(Failure.UNSOLVED)
            }
        }
        val pieces = ArrayList<Piece>()
        val polys = ArrayList<List<Rational>>()
        for (i in 0 until n - 1) {
            Budget.check()
            val a = ys[i]
            val b = (ys[i + 1] - ys[i]) / h[i] - h[i] * (Rational.of(2) * moments[i] + moments[i + 1]) / Rational.of(6)
            val c = moments[i] / Rational.of(2)
            val d = (moments[i + 1] - moments[i]) / (Rational.of(6) * h[i])
            // a + b·t + c·t² + d·t³ with t = x − xᵢ, multiplied out.
            var poly = listOf(d)
            for (coefficient in listOf(c, b, a)) poly = plus(times(poly, xs[i]), listOf(coefficient))
            polys += poly
            val t = add(x, num(-xs[i]))
            val local = add(listOf(num(a), mul(num(b), t), mul(num(c), pow(t, Expr.TWO)), mul(num(d), pow(t, num(3)))).filter { !isZero(it) }
                .ifEmpty { listOf(Expr.ZERO) })
            pieces += Piece(num(xs[i]), num(xs[i + 1]), num(a), num(b), num(c), num(d), expr(poly, x))
            // The piece as written multiplies out to its polynomial.
            if (!Verify.same(Algebra.expand(local), pieces.last().polynomial)) throw CalcException(Failure.UNSOLVED)
        }
        // Checked from the pieces multiplied out: through every point, S′ and S″ equal where two pieces meet, S″ = 0 at both ends.
        for (i in 0 until n - 1) {
            if (at(polys[i], xs[i]) != ys[i] || at(polys[i], xs[i + 1]) != ys[i + 1]) throw CalcException(Failure.UNSOLVED)
            if (i + 1 < n - 1) {
                val here = derivative(polys[i])
                val next = derivative(polys[i + 1])
                if (at(here, xs[i + 1]) != at(next, xs[i + 1])) throw CalcException(Failure.UNSOLVED)
                if (at(derivative(here), xs[i + 1]) != at(derivative(next), xs[i + 1])) throw CalcException(Failure.UNSOLVED)
            }
        }
        if (at(derivative(derivative(polys.first())), xs.first()).signum != 0 || at(derivative(derivative(polys.last())), xs.last()).signum != 0) {
            throw CalcException(Failure.UNSOLVED)
        }
        return Spline(letter, xs.map { num(it) }, ys.map { num(it) }, moments.map { num(it) }, pieces)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Steps.

    private fun step(out: MutableList<Step>, shown: String, key: StepKey, vararg args: String) {
        out += Step(Steps.tidy(shown), StepText(key, args.map { Steps.tidy(it) }))
    }

    private fun pointsLatex(c: Calculation, xs: List<Expr>, ys: List<Expr>): String =
        xs.indices.joinToString(",\\ ") { "\\left(" + c.latex(xs[it]) + ", " + c.latex(ys[it]) + "\\right)" }

    /** Newton's form as written, in LaTeX: 2 + (x − 1) + \frac{1}{2}(x − 1)(x − 2), its terms of coefficient 0 left out. */
    fun newtonLatex(c: Style, p: Interpolant): String {
        val x = Format.name(p.letter, latex = true)
        fun factor(a: Expr): String {
            val r = rational(a) ?: return "\\left($x - " + Format.expr(a, c, latex = true) + "\\right)"
            return when {
                r.signum == 0 -> x
                r.signum > 0 -> "\\left($x - " + Format.expr(num(r), c, latex = true) + "\\right)"
                else -> "\\left($x + " + Format.expr(num(-r), c, latex = true) + "\\right)"
            }
        }
        val out = StringBuilder()
        for ((k, coefficient) in p.newton.withIndex()) {
            val r = rational(coefficient) ?: continue
            if (r.signum == 0) continue
            val factors = (0 until k).joinToString("") { factor(p.xs[it]) }
            val size = if (k > 0 && r.abs() == Rational.ONE) "" else Format.expr(num(r.abs()), c, latex = true)
            val term = size + factors
            out.append(if (out.isEmpty()) (if (r.signum < 0) "-" else "") else if (r.signum < 0) " - " else " + ").append(term)
        }
        return if (out.isEmpty()) "0" else out.toString()
    }

    /** A spline's piece as written, in LaTeX: 1 + \frac{1}{2}(x − 1) − 3(x − 1)^{2} + \frac{1}{2}(x − 1)^{3}, terms of coefficient 0 left out. */
    fun pieceLatex(c: Style, p: Piece, letter: String = "x"): String {
        val x = Format.name(letter, latex = true)
        val from = rational(p.from) ?: return Format.expr(p.polynomial, c, latex = true)
        val t = when {
            from.signum == 0 -> x
            from.signum > 0 -> "\\left($x - " + Format.expr(num(from), c, latex = true) + "\\right)"
            else -> "\\left($x + " + Format.expr(num(-from), c, latex = true) + "\\right)"
        }
        val out = StringBuilder()
        for ((k, coefficient) in listOf(p.a, p.b, p.c, p.d).withIndex()) {
            val r = rational(coefficient) ?: return Format.expr(p.polynomial, c, latex = true)
            if (r.signum == 0) continue
            val power = when (k) { 0 -> ""; 1 -> t; else -> "$t^{$k}" }
            val size = if (k > 0 && r.abs() == Rational.ONE) "" else Format.expr(num(r.abs()), c, latex = true)
            out.append(if (out.isEmpty()) (if (r.signum < 0) "-" else "") else if (r.signum < 0) " - " else " + ").append(size + power)
        }
        return if (out.isEmpty()) "0" else out.toString()
    }

    /** The divided differences order by order, Newton's form, the check at every point, and the polynomial multiplied out. */
    internal fun steps(c: Calculation, p: Interpolant): List<Step> {
        val out = ArrayList<Step>()
        val x = p.letter
        step(out, pointsLatex(c, p.xs, p.ys), StepKey.START)
        for (k in 1 until p.differences.size) {
            val column = p.differences[k]
            val shown = column.indices.joinToString(",\\ ") { i ->
                "f\\left[" + (i..i + k).joinToString(", ") { "x_{$it}" } + "\\right] = " + c.latex(column[i])
            }
            step(out, shown, StepKey.INTERP_DIFFERENCES, k.toString())
        }
        step(out, "p($x) = " + newtonLatex(c, p), StepKey.INTERP_NEWTON)
        val sx = Expr.Sym(x)
        step(out, p.xs.indices.joinToString(",\\ ") { i ->
            "p\\left(" + c.latex(p.xs[i]) + "\\right) = " + c.latex(tidy(p.polynomial.substitute(sx, p.xs[i])))
        }, StepKey.INTERP_CHECK)
        step(out, "p($x) = " + c.latex(p.polynomial), StepKey.RESULT)
        return out
    }

    /** The widths, the system for the second derivatives, its solution, each piece, and the spline as a whole. */
    internal fun steps(c: Calculation, s: Spline): List<Step> {
        val out = ArrayList<Step>()
        val n = s.xs.size
        val x = s.letter
        step(out, pointsLatex(c, s.xs, s.ys), StepKey.START)
        val h = (0 until n - 1).map { tidy(s.xs[it + 1] - s.xs[it]) }
        step(out, h.indices.joinToString(",\\ ") { "h_{$it} = " + c.latex(h[it]) }, StepKey.SPLINE_WIDTHS)
        if (n > 2) {
            val lines = (1 until n - 1).map { i ->
                val left = Calculus.tidy(add(mul(h[i - 1], Expr.Sym("M_${i - 1}")), mul(num(2), add(h[i - 1], h[i]), Expr.Sym("M_$i")),
                    mul(h[i], Expr.Sym("M_${i + 1}"))).substitute(Expr.Sym("M_0"), Expr.ZERO).substitute(Expr.Sym("M_${n - 1}"), Expr.ZERO))
                val right = tidy(mul(num(6), add(mul(add(s.ys[i + 1], neg(s.ys[i])), pow(h[i], Expr.MINUS_ONE)),
                    neg(mul(add(s.ys[i], neg(s.ys[i - 1])), pow(h[i - 1], Expr.MINUS_ONE))))))
                c.latex(left) + " = " + c.latex(right)
            }
            step(out, if (lines.size == 1) lines[0] else "\\begin{cases}" + lines.joinToString(" \\\\ ") + "\\end{cases}", StepKey.SPLINE_SYSTEM)
        }
        step(out, s.moments.indices.joinToString(",\\ ") { "M_{$it} = " + c.latex(s.moments[it]) }, StepKey.SPLINE_MOMENTS)
        for ((i, p) in s.pieces.withIndex()) {
            step(out, "S_{$i}($x) = " + pieceLatex(c, p, s.letter), StepKey.SPLINE_PIECE, "\\left[" + c.latex(p.from) + ", " + c.latex(p.to) + "\\right]")
        }
        step(out, "S($x) = \\begin{cases}" + s.pieces.joinToString(" \\\\ ") { p ->
            c.latex(p.polynomial) + " & " + c.latex(p.from) + " \\le " + x + " \\le " + c.latex(p.to)
        } + "\\end{cases}", StepKey.RESULT)
        return out
    }
}

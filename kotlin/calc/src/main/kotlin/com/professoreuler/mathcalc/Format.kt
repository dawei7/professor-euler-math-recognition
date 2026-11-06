package com.professoreuler.mathcalc

import java.math.BigDecimal
import java.math.MathContext

/**
 * Numbers as the formula wrote its own: an integer as it is; a fraction where the formula had fractions, with its
 * decimal; a decimal where it had decimals or no fractions; a decimal comma where it used one. Ten significant digits.
 */
internal object Format {
    const val DIGITS = 10

    /** The most digits an exact integer is shown with. */
    const val MAX_DIGITS = 1000

    /** The most digits a fraction is shown with, numerator and denominator together. */
    const val MAX_FRACTION_DIGITS = 40

    /** An expression in LaTeX or in the sheets' math shorthand. */
    fun expr(e: Expr, c: Style, latex: Boolean): String = Write(c, latex).expr(e)

    /** An expression's result: a series term by term in rising powers with + O(…), an antiderivative with + C. */
    fun result(o: Outcome.Expression, c: Style, latex: Boolean): String {
        val w = Write(c, latex)
        val series = o.series
        val body = if (series == null || series.isEmpty()) w.expr(o.result) else {
            val out = StringBuilder()
            val h = o.seriesIn ?: Expr.ZERO
            // (x − a) as one letter while a term is written, then put in with its brackets: (1/2)(x − 1)².
            val stand = Expr.Sym("ϰ")
            val bracketed = if (latex) "\\left(" + w.expr(h) + "\\right)" else "(" + w.expr(h) + ")"
            for ((k, pair) in series.withIndex()) {
                val (coefficient, j) = pair
                val negative = Write.isNegative(coefficient)
                val size = if (negative) neg(coefficient) else coefficient
                val text = if (h is Expr.Sym) w.expr(mul(size, pow(h, num(j.toLong()))))
                else w.expr(mul(size, pow(stand, num(j.toLong())))).replace(name(stand.name, latex), bracketed)
                when {
                    k == 0 -> out.append(if (negative) "-$text" else text)
                    else -> out.append(if (negative) " - " else " + ").append(text)
                }
            }
            out.toString()
        }
        val tail = when {
            o.remainder != null -> (if (latex) " + O\\left(" else " + O(") + w.expr(o.remainder) + (if (latex) "\\right)" else ")")
            o.plusC -> " + C"
            else -> ""
        }
        return body + tail
    }

    /**
     * A value: a rational as numbers are written ([latex] of a [Num]), an exact form with its decimal beside it
     * (`√2 ≈ 1.414213562`), a numeric one as its decimal; a formula as it is.
     */
    fun value(e: Expr, c: Style, latex: Boolean): String {
        if (e is Expr.Num) return write(Num.Exact(e.value), c, latex)
        val approx = if (latex) "\\approx " else "≈ "
        if (!e.isConstant() || e.hasI() || e.hasInfinity()) return expr(e, c, latex)
        val d = Numeric.value(e) ?: return expr(e, c, latex)
        if (e is Expr.Real || !MathCalc.niceExact(e)) return approx + decimal(BigDecimal(d), c, latex)
        return expr(e, c, latex) + " " + approx + decimal(BigDecimal(d), c, latex)
    }

    /**
     * An inequality's solution: one interval as inequalities (`x < 4`, `-2 < x \le 3`), several as a union of intervals
     * (`x \in (-\infty, -1] \cup [1, \infty)`), all of ℝ, or none.
     */
    fun intervals(s: IntervalSet, c: Style, latex: Boolean): String {
        val x = name(s.variable, latex)
        fun v(e: Expr) = expr(e, c, latex)
        val le = if (latex) "\\le" else "≤"
        val inSet = if (latex) "\\in" else "∈"
        if (s.empty) return if (latex) "\\emptyset" else "∅"
        if (s.all) return if (latex) "$x \\in \\mathbb{R}" else "$x ∈ ℝ"
        if (s.intervals.size == 1) {
            val i = s.intervals[0]
            val lo = i.lower?.let { v(it) + (if (i.lowerClosed) " $le " else " < ") }.orEmpty()
            val hi = i.upper?.let { (if (i.upperClosed) " $le " else " < ") + v(it) }.orEmpty()
            if (i.lower != null && i.lower == i.upper) return "$x = ${v(i.lower)}"
            return "$lo$x$hi".trim()
        }
        val inf = if (latex) "\\infty" else "∞"
        val union = if (latex) " \\cup " else " ∪ "
        return "$x $inSet " + s.intervals.joinToString(union) { i ->
            if (i.lower != null && i.lower == i.upper) (if (latex) "\\{${v(i.lower)}\\}" else "{${v(i.lower)}}")
            else (if (i.lowerClosed) "[" else "(") + (i.lower?.let { v(it) } ?: "-$inf") + ", " + (i.upper?.let { v(it) } ?: inf) +
                (if (i.upperClosed) "]" else ")")
        }
    }

    /** An angle in degrees, a family a + k·b written as a° + k·b°. */
    fun angle(e: Expr, c: Style, latex: Boolean): String {
        val deg = if (latex) "^{\\circ}" else "°"
        if (!e.has(K)) return expr(e, c, latex) + deg
        val a = Solve.simplifyValue(e.substitute(K, Expr.ZERO))
        val b = Solve.simplifyValue(e.substitute(K, Expr.ONE) - a)
        val dot = if (latex) " \\cdot " else "·"
        val negative = Write.isNegative(b)
        val step = expr(if (negative) neg(b) else b, c, latex) + deg
        return expr(a, c, latex) + deg + (if (negative) " - k" else " + k") + dot + step
    }

    fun latex(n: Num, c: Style): String = write(n, c, latex = true)

    fun text(n: Num, c: Style): String = write(n, c, latex = false)

    private fun write(n: Num, c: Style, latex: Boolean): String {
        val approx = if (latex) "\\approx " else "≈ "
        val r = n.rational ?: return approx + decimal(BigDecimal(n.double), c, latex)
        if (r.isInteger) {
            // An exact integer is exact: all its digits (2^100 = 1267650600228229401496703205376), with its size beside them
            // where it has more than 15; beyond [MAX_DIGITS] its decimal alone, whose power of ten says how many digits it has.
            val length = r.num.abs().toString().length
            return when {
                length <= 15 -> r.num.toString()
                length <= MAX_DIGITS -> r.num.toString() + " " + approx + decimal(BigDecimal(r.num), c, latex)
                else -> approx + decimal(BigDecimal(r.num), c, latex)
            }
        }
        // A fraction too long to read (binompdf(100, 0.02, 1), 170 digits over 170): its decimal, the exact value kept in the
        // outcome (Num.Exact) for its digits on request.
        if (r.num.abs().toString().length + r.den.toString().length > MAX_FRACTION_DIGITS) {
            return approx + decimal(BigDecimal(r.num).divide(BigDecimal(r.den), MathContext(DIGITS)), c, latex)
        }
        // A fraction nobody reads, its numerator or denominator longer than 15 digits as a whole number is (an interest compounded
        // monthly for ten years): its decimal, as for such a whole number; the exact value stays in the outcome and its digits.
        if (r.num.abs().toString().length > 15 || r.den.toString().length > 15) {
            // Rounded once, from the exact fraction: every digit shown is certain.
            return approx + decimal(BigDecimal(r.num).divide(BigDecimal(r.den), MathContext(DIGITS, java.math.RoundingMode.HALF_UP)), c, latex)
        }
        val exact = if (r.terminates) BigDecimal(r.num).divide(BigDecimal(r.den)) else null
        val shortExact = exact?.takeIf { it.stripTrailingZeros().scale() <= DIGITS && it.abs() >= BigDecimal("1e-6") }
        val dec = shortExact?.let { decimal(it, c, latex, exact = true) }
        val near = decimal(BigDecimal(r.toDouble()), c, latex)
        return when {
            dec != null && (c.decimals || !c.fractions) -> dec
            c.decimals && !c.fractions -> approx + near
            else -> fraction(r, latex) + if (dec != null) " = $dec" else " $approx$near"
        }
    }

    /** Terms highest degree first, with signs between them: `x^{2} - 5x + 6`; in text `x^2 - 5x + 6`, `(5/6)x`. */
    fun polynomial(p: Polynomial, c: Style, latex: Boolean): String {
        if (p.isZero) return "0"
        val out = StringBuilder()
        for ((k, term) in p.sorted.withIndex()) {
            val (m, coefficient) = term
            val negative = coefficient.signum < 0
            if (k == 0) { if (negative) out.append('-') } else out.append(if (negative) " - " else " + ")
            val size = coefficient.abs()
            when {
                m == Monomial.ONE -> out.append(coefficient(size, c, latex, alone = true))
                size == Rational.ONE -> out.append(monomial(m, latex))
                else -> out.append(coefficient(size, c, latex, alone = false)).append(monomial(m, latex))
            }
        }
        return out.toString()
    }

    /** Its number, then its factors: `3x(x - 2)^{2}`; a factor of one term (an unknown) without brackets. */
    fun product(p: Product, c: Style, latex: Boolean): String {
        if (p.factors.isEmpty()) return number(p.constant, c, latex)
        if (p.trivial) return polynomial(p.factors[0].first, c, latex)
        val factors = p.factors.joinToString("") { (f, k) ->
            val base = if (f.size == 1) polynomial(f, c, latex) else "(${polynomial(f, c, latex)})"
            when {
                k == 1 -> base
                latex -> "$base^{$k}"
                else -> "$base^$k"
            }
        }
        val constant = when (p.constant) {
            Rational.ONE -> ""
            -Rational.ONE -> "-"
            else -> (if (p.constant.signum < 0) "-" else "") + coefficient(p.constant.abs(), c, latex, alone = false)
        }
        return constant + factors
    }

    /** `2^{3} \cdot 3^{2} \cdot 5`; in text `2^3 · 3^2 · 5`. */
    fun primes(p: Outcome.Primes, latex: Boolean): String =
        (if (p.n.signum() < 0) "-" else "") + p.factors.joinToString(if (latex) " \\cdot " else " · ") { (prime, k) ->
            when {
                k == 1 -> "$prime"
                latex -> "$prime^{$k}"
                else -> "$prime^$k"
            }
        }

    /** An unknown's name: `x`; `x_{1}` in LaTeX and `x_1` in text (`a_{n+1}` where its index is more than digits). */
    fun name(v: String, latex: Boolean): String {
        val i = v.indexOf('_')
        if (i < 0) return v
        val index = v.substring(i + 1)
        return if (latex || !index.all { it.isDigit() }) "${v.substring(0, i)}_{$index}" else v
    }

    private fun monomial(m: Monomial, latex: Boolean) = m.powers.joinToString("") { (v, k) ->
        name(v, latex) + when {
            k == 1 -> ""
            latex -> "^{$k}"
            else -> "^$k"
        }
    }

    /** A number in a polynomial: a decimal where the formula wrote decimals and no fractions and it ends, else a fraction. */
    fun coefficient(r: Rational, c: Style, latex: Boolean, alone: Boolean): String {
        if (r.isInteger) return r.num.toString()
        if (c.decimals && !c.fractions && r.terminates) {
            val exact = BigDecimal(r.num).divide(BigDecimal(r.den))
            if (exact.stripTrailingZeros().scale() <= DIGITS) return decimal(exact, c, latex, exact = true)
        }
        return when {
            latex -> "\\frac{${r.num}}{${r.den}}"
            alone -> "${r.num}/${r.den}"
            else -> "(${r.num}/${r.den})"
        }
    }

    private fun number(r: Rational, c: Style, latex: Boolean) =
        (if (r.signum < 0) "-" else "") + coefficient(r.abs(), c, latex, alone = true)

    private fun fraction(r: Rational, latex: Boolean): String {
        val sign = if (r.signum < 0) "-" else ""
        return if (latex) "$sign\\frac{${r.num.abs()}}{${r.den}}" else "$sign${r.num.abs()}/${r.den}"
    }

    fun decimal(x: BigDecimal, c: Style, latex: Boolean, exact: Boolean = false): String {
        val rounded = if (exact) x else x.round(MathContext(DIGITS))
        val plain = rounded.abs() >= BigDecimal("1e-6") && rounded.abs() < BigDecimal("1e15") || rounded.signum() == 0
        val s = if (plain) {
            rounded.stripTrailingZeros().toPlainString()
        } else {
            val mantissa = rounded.unscaledValue().toString().trimStart('-')
            val exponent = mantissa.length - 1 - rounded.scale()
            val digits = mantissa.trimEnd('0').ifEmpty { "0" }
            val m = if (digits.length > 1) digits[0] + "." + digits.substring(1) else digits
            val sign = if (rounded.signum() < 0) "-" else ""
            if (latex) "$sign$m \\cdot 10^{$exponent}" else "$sign$m · 10^$exponent"
        }
        val point = if (c.decimalComma) (if (latex) "{,}" else ",") else "."
        return s.replace(".", point).let { if (it == "-0") "0" else it }
    }
}

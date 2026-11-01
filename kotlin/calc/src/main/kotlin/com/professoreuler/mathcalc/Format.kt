package com.professoreuler.mathcalc

import java.math.BigDecimal
import java.math.MathContext

/**
 * Numbers as the formula wrote its own: an integer as it is; a fraction where the formula had fractions, with its
 * decimal; a decimal where it had decimals or no fractions; a decimal comma where it used one. Ten significant digits.
 */
internal object Format {
    private const val DIGITS = 10

    fun latex(n: Num, c: Calculation): String = write(n, c, latex = true)

    fun text(n: Num, c: Calculation): String = write(n, c, latex = false)

    private fun write(n: Num, c: Calculation, latex: Boolean): String {
        val approx = if (latex) "\\approx " else "≈ "
        val r = n.rational ?: return approx + decimal(BigDecimal(n.double), c, latex)
        if (r.isInteger) {
            return if (r.num.abs().toString().length <= 15) r.num.toString() else approx + decimal(BigDecimal(r.num), c, latex)
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

    private fun fraction(r: Rational, latex: Boolean): String {
        val sign = if (r.signum < 0) "-" else ""
        return if (latex) "$sign\\frac{${r.num.abs()}}{${r.den}}" else "$sign${r.num.abs()}/${r.den}"
    }

    private fun decimal(x: BigDecimal, c: Calculation, latex: Boolean, exact: Boolean = false): String {
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

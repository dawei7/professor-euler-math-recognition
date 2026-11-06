package com.professoreuler.mathcalc

import java.math.BigInteger

/**
 * Worked steps for the added functions where a worked solution makes sense: a whole number written in another base (its
 * divisions with remainder, the remainders read back), a numeral read (each digit times its power of the base), and the gcd or
 * lcm of two polynomials (Euclid's divisions, the last remainder made monic, the lcm as the product over the gcd). Null for
 * anything else.
 */
internal object FunctionSteps {
    /** At most this many digits or divisions are written out. */
    private const val MOST = 40

    fun of(c: Calculation, latex: String): List<Step>? = try {
        when (val o = c.outcome) {
            is Outcome.InBase -> toBase(c, latex, o)
            is Outcome.Value -> if (c.mode == Mode.FROM_BASE) fromBase(c, latex) else null
            is Outcome.Expression -> polynomialGcd(c, latex)
            else -> null
        }?.takeIf { it.size >= 2 }
    } catch (e: CalcException) {
        null
    } catch (e: ArithmeticException) {
        null
    }

    private fun step(shown: String, key: StepKey, vararg args: String) = Step(Steps.tidy(shown), StepText(key, args.toList()))

    private fun call(latex: String, c: Calculation): String = CommandFunctions.latex(Typed(latex, c.choice)) ?: latex

    private fun toBase(c: Calculation, latex: String, o: Outcome.InBase): List<Step>? {
        if (o.digits.length > MOST) return null
        val out = ArrayList<Step>()
        val b = BigInteger.valueOf(o.base.toLong())
        out += step(call(latex, c), StepKey.START)
        var m = o.value.abs()
        while (m.signum() > 0) {
            val (q, r) = m.divideAndRemainder(b).let { it[0] to it[1] }
            out += step("$m = $q \\cdot ${o.base} + $r", StepKey.BASE_DIVIDE, "${o.base}")
            m = q
        }
        out += step("${o.value} = ${o.digits}_{${o.base}}", StepKey.BASE_REMAINDERS, "${o.base}")
        return out
    }

    private fun fromBase(c: Calculation, latex: String): List<Step>? {
        val numeral = c.choice?.letter ?: return null
        val base = c.choice.point?.toIntOrNull() ?: return null
        val negative = numeral.startsWith("-")
        val digits = numeral.removePrefix("-")
        if (digits.length > MOST) return null
        val n = (c.outcome as Outcome.Value).value as? Num.Exact ?: return null
        val sum = digits.mapIndexed { k, d -> "${Bases.DIGITS.indexOf(d)} \\cdot $base^{${digits.length - 1 - k}}" }.joinToString(" + ")
        return listOf(
            step(call(latex, c), StepKey.START),
            step("${numeral}_{$base} = " + (if (negative) "-\\left($sum\\right)" else sum), StepKey.BASE_PLACE_VALUES, "$base"),
            step("${numeral}_{$base} = ${n.r}", StepKey.RESULT),
        )
    }

    private val GCD_CALL = Regex("""^\\?(gcd|lcm)\s*(?:\\left)?\((.*?)(?:\\right)?\)$""", RegexOption.DOT_MATCHES_ALL)

    /** gcd(a, b) or lcm(a, b) of two polynomials in one letter: Euclid's divisions. */
    private fun polynomialGcd(c: Calculation, latex: String): List<Step>? {
        val m = GCD_CALL.matchEntire(latex.trim()) ?: return null
        val lcm = m.groupValues[1] == "lcm"
        val parts = split(m.groupValues[2]) ?: return null
        if (parts.size != 2) return null
        val exprs = parts.map { read(it) ?: return null }
        val letters = exprs.flatMap { it.symbols() }.toSet()
        if (letters.size != 1) return null
        val x = Expr.Sym(letters.first())
        val ps = exprs.map { PolynomialGcd.coefficients(it, x) ?: return null }
        if (ps.any { it.isEmpty() }) return null
        val divisions = PolynomialGcd.divisions(ps[0], ps[1])
        if (divisions.isEmpty() || divisions.size > MOST) return null
        fun l(p: List<Rational>) = Format.expr(Poly.expr(p, x), c, latex = true)
        fun b(p: List<Rational>) = l(p).let { if (" + " in it || " - " in it || it.startsWith("-")) "\\left($it\\right)" else it }
        val out = ArrayList<Step>()
        out += step("\\operatorname{${m.groupValues[1]}}\\left(${l(ps[0])}, ${l(ps[1])}\\right)", StepKey.START)
        for (d in divisions) {
            val rest = if (d.remainder.isEmpty()) "" else " + " + b(d.remainder)
            out += step("${l(d.a)} = ${b(d.quotient)} \\cdot ${b(d.b)}$rest", StepKey.GCD_DIVIDE)
        }
        val g = PolynomialGcd.gcd(ps)
        out += step("\\gcd = ${l(g)}", StepKey.GCD_LAST)
        if (lcm) {
            val result = PolynomialGcd.lcm(ps)
            out += step("\\operatorname{lcm} = \\frac{${b(ps[0])} \\cdot ${b(ps[1])}}{${l(g)}} = ${l(result)}", StepKey.LCM_PRODUCT)
        }
        return out
    }

    /** The arguments of a call, split at its commas outside brackets. */
    private fun split(inner: String): List<String>? {
        val out = ArrayList<String>()
        var depth = 0
        val current = StringBuilder()
        for (ch in inner) {
            when (ch) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
            }
            if (ch == ',' && depth == 0) {
                out += current.toString()
                current.setLength(0)
            } else current.append(ch)
        }
        out += current.toString()
        return out.map { it.trim() }.takeIf { p -> p.all { it.isNotEmpty() } }
    }

    private fun read(latex: String): Expr? = try {
        val tokens = MathCalc.TOKEN.findAll(latex).map { it.value }.toList()
        Parser(tokens).statements().singleOrNull()?.sides?.singleOrNull()?.let { Calculus.evaluate(it) }
    } catch (e: CalcException) {
        null
    }
}

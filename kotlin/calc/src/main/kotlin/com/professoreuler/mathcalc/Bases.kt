package com.professoreuler.mathcalc

import java.math.BigInteger

/**
 * Whole numbers in other bases, 2 to 36: tobase(255, 16) is FF₁₆ and frombase(FF, 16) is 255. Digits beyond 9 are the
 * letters A to Z (a lowercase numeral is read the same), a negative number has its minus first. tobase takes a formula whose
 * value is a whole number (tobase(2^10, 2)); frombase takes the numeral itself. Each is checked the other way: the digits found
 * by dividing are read back by Java's own numeral reader, and a numeral read is written again by dividing.
 */
internal object Bases {
    const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val NUMERAL = Regex("^-?[0-9A-Za-z]+$")
    private const val MOST_DIGITS = 4000

    /** A call of tobase or frombase read: null where its arguments are not a formula or a numeral and a base 2 to 36. */
    fun read(mode: Mode, args: List<String>): Typed? {
        if (args.size != 2) return null
        val base = args[1].trim().toIntOrNull()?.takeIf { it in 2..36 } ?: return null
        return when (mode) {
            Mode.TO_BASE -> {
                val latex = TextInput.latex(args[0])
                if (!numberOnly(latex)) return null
                Typed(latex, Choice(Mode.TO_BASE, point = base.toString()))
            }
            Mode.FROM_BASE -> {
                val digits = args[0].trim()
                if (!NUMERAL.matches(digits) || digits.length > MOST_DIGITS) return null
                if (digits.removePrefix("-").any { DIGITS.indexOf(it.uppercaseChar()) !in 0 until base }) return null
                Typed(BigInteger(digits, base).toString(), Choice(Mode.FROM_BASE, letter = digits.uppercase(), point = base.toString()))
            }
            else -> null
        }
    }

    /** Whether [latex] is one formula without letters or relations (a number to be converted). */
    private fun numberOnly(latex: String): Boolean = try {
        val tokens = MathCalc.TOKEN.findAll(latex).map { it.value }.toList()
        val statements = Domain.complex { Parser(tokens).statements() }
        statements.size == 1 && statements[0].sides.size == 1 && statements[0].sides.all { it != null && it.symbols().isEmpty() }
    } catch (e: CalcException) {
        false
    } catch (e: RuntimeException) {
        false
    }

    /** The call's arguments as [CommandFunctions.latex] writes them back. */
    fun arguments(c: Choice, latex: String): List<String>? = when (c.mode) {
        Mode.TO_BASE -> listOf(latex, c.point ?: return null)
        Mode.FROM_BASE -> listOf(c.letter ?: return null, c.point ?: return null)
        else -> null
    }

    /** [n]'s digits in [base] by repeated division, the remainders read from the last. */
    fun digits(n: BigInteger, base: Int): String {
        if (n.signum() == 0) return "0"
        val b = BigInteger.valueOf(base.toLong())
        val out = StringBuilder()
        var m = n.abs()
        while (m.signum() > 0) {
            val (q, r) = m.divideAndRemainder(b).let { it[0] to it[1] }
            out.append(DIGITS[r.toInt()])
            m = q
            if (out.length and 1023 == 0) Budget.check()
        }
        if (n.signum() < 0) out.append('-')
        return out.reverse().toString()
    }

    /** The outcome of a choice of [Mode.TO_BASE] or [Mode.FROM_BASE] on the formula's value [v]. */
    fun outcome(c: Choice, v: Expr): Outcome {
        val base = c.point?.toIntOrNull()?.takeIf { it in 2..36 } ?: throw CalcException(Failure.ARGUMENTS)
        val n = integer(v) ?: throw CalcException(Failure.UNDEFINED)
        if (n.bitLength() > 4 * MOST_DIGITS) throw CalcException(Failure.TOO_LARGE)
        val written = digits(n, base)
        // Checked the other way: the digits read back are the number.
        if (BigInteger(written, base) != n) throw CalcException(Failure.UNSOLVED)
        return when (c.mode) {
            Mode.TO_BASE -> Outcome.InBase(n, base, written)
            else -> {
                val numeral = c.letter ?: throw CalcException(Failure.ARGUMENTS)
                // The numeral, without its leading zeros, is what dividing writes.
                val plain = numeral.removePrefix("-").trimStart('0').ifEmpty { "0" }.let { if (numeral.startsWith("-") && it != "0") "-$it" else it }
                if (plain != written) throw CalcException(Failure.UNSOLVED)
                Outcome.Value(Num.Exact(Rational.of(n)))
            }
        }
    }
}

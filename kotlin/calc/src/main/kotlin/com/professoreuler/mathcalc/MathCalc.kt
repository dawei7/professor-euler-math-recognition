package com.professoreuler.mathcalc

import kotlin.math.abs
import kotlin.math.max

/** Why a formula has no answer. The apps put each in words of their own. */
enum class Failure {
    /** Not a formula this calculator reads: an unknown sign, a missing bracket, nothing to calculate. */
    UNREADABLE,
    DIVISION_BY_ZERO,
    /** Outside a function's domain: the root of a negative, the logarithm of 0, tan at 90°, 2.5! */
    UNDEFINED,
    /** More than one unknown, as in `x + y = 3`. */
    SEVERAL_UNKNOWNS,
    /** An unknown without an equation to find it: `2x + 3 =`. */
    UNKNOWN_WITHOUT_EQUATION,
    /** More solutions than are worth listing, as sin x = 0 has. */
    MANY_SOLUTIONS,
}

class CalcException(val failure: Failure) : Exception(failure.name)

/** A number: exact while every step of its calculation was, otherwise as close as a double gets. */
sealed class Num {
    abstract val double: Double

    data class Exact(val r: Rational) : Num() {
        override val double get() = r.toDouble()
    }

    data class Approx(val d: Double) : Num() {
        override val double get() = d
    }

    /** The exact value, or the fraction a double lies within rounding of (sin 30° is 1/2). */
    val rational: Rational?
        get() = when (this) {
            is Exact -> r
            is Approx -> Rational.near(d)
        }
}

/** What a formula comes to. */
sealed class Outcome {
    /** The formula's value: `37 + 48 =` is 85. [steps] are the earlier sides of a chain, `a = b =`, and whether each held. */
    data class Value(val value: Num, val steps: List<Boolean> = emptyList()) : Outcome()

    /** An equation in one unknown and its real solutions, none where there are none; [always] where every value solves it. */
    data class Solved(val unknown: String, val roots: List<Num>, val always: Boolean = false) : Outcome()

    /** An equation without an unknown, `2 + 2 = 4`, and whether it holds. */
    data class Checked(val holds: Boolean) : Outcome()

    data class Failed(val failure: Failure) : Outcome()
}

/** A formula's outcome, and how it was written: a decimal comma, decimals, fractions, so the answer is written alike. */
class Calculation(val outcome: Outcome, val decimalComma: Boolean, val decimals: Boolean, val fractions: Boolean) {
    /** A number in LaTeX: an integer, a fraction and its decimal, or a decimal, as the formula wrote them. */
    fun latex(n: Num): String = Format.latex(n, this)

    /** The same in plain text: `19/12 ≈ 1.583333333`. */
    fun text(n: Num): String = Format.text(n, this)
}

/**
 * Calculates a formula read from handwriting, as the recognizer writes it in LaTeX: arithmetic with `\cdot`, `\times`,
 * `:`, `\div` and `/`, decimal points or commas, `\frac`, powers, `\sqrt` and `\sqrt[n]`, `!`, `\%`, `|x|`, π and e,
 * sin, cos, tan (in degrees with `^{\circ}`, otherwise radians), ln, lg and log with or without a base; and equations in
 * one unknown, solved exactly where they are linear or quadratic and numerically otherwise.
 */
object MathCalc {

    fun calculate(latex: String): Calculation {
        val tokens = TOKEN.findAll(latex).map { it.value }.toList()
        val parser = Parser(tokens)
        val outcome = try {
            outcome(parser.formula())
        } catch (e: CalcException) {
            Outcome.Failed(e.failure)
        } catch (e: ArithmeticException) {
            Outcome.Failed(Failure.UNDEFINED)
        }
        return Calculation(outcome, parser.decimalComma, parser.decimals, parser.fractions)
    }

    private val TOKEN = Regex("""\\[a-zA-Z]+|\\.|\S""")

    private fun outcome(sides: List<Node?>): Outcome {
        val written = sides.filterNotNull()
        if (written.isEmpty() || sides.dropLast(1).any { it == null }) throw CalcException(Failure.UNREADABLE)
        val unknowns = written.flatMap { it.unknowns() }.toSet()
        if (unknowns.size > 1) throw CalcException(Failure.SEVERAL_UNKNOWNS)
        if (unknowns.size == 1) {
            if (written.size != 2 || sides.last() == null) throw CalcException(Failure.UNKNOWN_WITHOUT_EQUATION)
            return Solver.solve(Sub(written[0], written[1]), unknowns.first())
        }
        val values = written.map { it.eval(emptyMap()) }
        if (sides.last() == null || sides.size == 1) {
            val steps = values.zipWithNext { a, b -> same(a, b) }
            return Outcome.Value(values.last(), steps)
        }
        return Outcome.Checked(values.zipWithNext { a, b -> same(a, b) }.all { it })
    }

    private fun same(a: Num, b: Num): Boolean =
        if (a is Num.Exact && b is Num.Exact) a.r == b.r else abs(a.double - b.double) <= 1e-9 * max(1.0, max(abs(a.double), abs(b.double)))
}

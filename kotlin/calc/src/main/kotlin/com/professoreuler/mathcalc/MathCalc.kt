package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.max

/** Why a formula has no answer. The apps put each in words of their own. */
enum class Failure {
    /** Not a formula this calculator reads: an unknown sign, a missing bracket, nothing to calculate. */
    UNREADABLE,
    DIVISION_BY_ZERO,
    /** Outside a function's domain: the root of a negative, the logarithm of 0, tan at 90°, 2.5! */
    UNDEFINED,
    /** An equation in more than one unknown that is not a polynomial one, as `sin x = y`. */
    SEVERAL_UNKNOWNS,
    /** An unknown without an equation to find it, where a value had to be found. */
    UNKNOWN_WITHOUT_EQUATION,
    /** More solutions than are worth listing, as sin x = 0 has. */
    MANY_SOLUTIONS,
    /** Unknowns, but not a polynomial in them: an unknown in a denominator, under a root, in a function. */
    NOT_POLYNOMIAL,
    /** A polynomial too long to show, as (x + y + z)⁹⁹. */
    TOO_LARGE,
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

/** What Calculate does with a formula. [MathCalc.calculate] lists those that fit it and picks the likeliest. */
enum class Mode {
    /** Works out a number: `37 + 48 =` is 85. */
    CALCULATE,
    /** Multiplies out and collects like terms: (x + 2)² is x² + 4x + 4, 5x + 8x + 7y + 2y is 13x + 9y. */
    SIMPLIFY,
    /** The other way round, into factors: x² + 4x + 4 is (x + 2)²; a whole number into its primes. */
    FACTOR,
    /** Solves an equation in one unknown. */
    SOLVE,
    /** Whether the sides of an equation are equal: for every value of its unknowns where it has some. */
    COMPARE,
}

/** What a formula comes to. */
sealed class Outcome {
    /** The formula's value: `37 + 48 =` is 85. [steps] are the earlier sides of a chain, `a = b =`, and whether each held. */
    data class Value(val value: Num, val steps: List<Boolean> = emptyList()) : Outcome()

    /** An equation in one unknown and its real solutions, none where there are none; [always] where every value solves it. */
    data class Solved(val unknown: String, val roots: List<Num>, val always: Boolean = false) : Outcome()

    /** An equation without an unknown, `2 + 2 = 4`, and whether it holds. */
    data class Checked(val holds: Boolean) : Outcome()

    /** A polynomial multiplied out, its like terms collected. [steps] as for [Value]: a chain's sides, each equal to the next. */
    data class Simplified(val polynomial: Polynomial, val steps: List<Boolean> = emptyList()) : Outcome()

    /** A polynomial as a product of factors. */
    data class Factored(val product: Product) : Outcome()

    /** An equation in unknowns, and whether its sides are equal for every value of them; [sides] each simplified. */
    data class Compared(val holds: Boolean, val sides: List<Polynomial>) : Outcome()

    /** A whole number and its primes with their powers: 360 = 2³ · 3² · 5. */
    data class Primes(val n: BigInteger, val factors: List<Pair<BigInteger, Int>>) : Outcome()

    data class Failed(val failure: Failure) : Outcome()
}

/**
 * A formula's outcome, and how it was written (a decimal comma, decimals, fractions), so the answer is written alike.
 * [modes] are what Calculate can do with the formula, in [Mode]'s order; [mode] is the one [outcome] comes from.
 */
class Calculation(val outcome: Outcome, val decimalComma: Boolean, val decimals: Boolean, val fractions: Boolean,
    val modes: List<Mode> = emptyList(), val mode: Mode? = null) {
    /** A number in LaTeX: an integer, a fraction and its decimal, or a decimal, as the formula wrote them. */
    fun latex(n: Num): String = Format.latex(n, this)

    /** The same in plain text: `19/12 ≈ 1.583333333`. */
    fun text(n: Num): String = Format.text(n, this)

    /** A polynomial in LaTeX, `x^{2} + 4x + 4`, its numbers as the formula wrote them. */
    fun latex(p: Polynomial): String = Format.polynomial(p, this, latex = true)

    /** The same as the sheets' math shorthand: `x^2 + 4x + 4`, `(5/6)x`. */
    fun text(p: Polynomial): String = Format.polynomial(p, this, latex = false)

    /** A product in LaTeX: `3x(x - 2)^{2}`. */
    fun latex(p: Product): String = Format.product(p, this, latex = true)

    fun text(p: Product): String = Format.product(p, this, latex = false)

    /** Primes in LaTeX: `2^{3} \cdot 3^{2} \cdot 5`. */
    fun latex(p: Outcome.Primes): String = Format.primes(p, latex = true)

    fun text(p: Outcome.Primes): String = Format.primes(p, latex = false)

    /** An unknown's name in LaTeX: `x`, `x_{1}`. */
    fun latexName(unknown: String): String = Format.name(unknown, latex = true)
}

/**
 * Calculates a formula read from handwriting, as the recognizer writes it in LaTeX: arithmetic with `\cdot`, `\times`,
 * `:`, `\div` and `/`, decimal points or commas, `\frac`, powers, `\sqrt` and `\sqrt[n]`, `!`, `\%`, `|x|`, π and e,
 * sin, cos, tan (in degrees with `^{\circ}`, otherwise radians), ln, lg and log with or without a base; equations in one
 * unknown, solved exactly where they are linear or quadratic and numerically otherwise; and polynomials in any unknowns,
 * multiplied out, factored and compared.
 */
object MathCalc {

    /** [mode] as the learner chose it; null, or one that does not fit the formula, for the likeliest. */
    fun calculate(latex: String, mode: Mode? = null): Calculation {
        val tokens = TOKEN.findAll(latex).map { it.value }.toList()
        val parser = Parser(tokens)
        var modes = emptyList<Mode>()
        var shown: Mode? = null
        val outcome = try {
            val sides = parser.formula()
            val style = Calculation(Outcome.Failed(Failure.UNREADABLE), parser.decimalComma, parser.decimals, parser.fractions)
            val plan = plan(sides, parser.sideTexts, style)
            modes = plan.modes
            val chosen = mode?.takeIf { it in plan.modes } ?: plan.likeliest
            shown = chosen
            plan.outcome(chosen)
        } catch (e: CalcException) {
            Outcome.Failed(e.failure)
        } catch (e: ArithmeticException) {
            Outcome.Failed(Failure.UNDEFINED)
        }
        return Calculation(outcome, parser.decimalComma, parser.decimals, parser.fractions, modes, shown)
    }

    private val TOKEN = Regex("""\\[a-zA-Z]+|\\.|\S""")

    private val TWO = BigInteger.valueOf(2)

    /** The modes that fit a formula, the likeliest of them, and how each comes to its outcome. */
    private class Plan(val modes: List<Mode>, val likeliest: Mode, val outcome: (Mode) -> Outcome)

    private fun plan(sides: List<Node?>, texts: List<String>, style: Calculation): Plan {
        val written = sides.filterNotNull()
        if (written.isEmpty() || sides.dropLast(1).any { it == null }) throw CalcException(Failure.UNREADABLE)
        val open = sides.last() == null
        val equation = !open && written.size >= 2
        val unknowns = written.flatMap { it.unknowns() }.toSet()

        if (unknowns.isEmpty()) {
            val values = written.map { it.eval(emptyMap()) }
            val steps = values.zipWithNext { a, b -> same(a, b) }
            if (equation) return Plan(listOf(Mode.COMPARE), Mode.COMPARE) { Outcome.Checked(steps.all { it }) }
            val value = values.last()
            // A whole number can be taken apart into its primes.
            val whole = (value as? Num.Exact)?.r?.takeIf { it.isInteger && it.num.abs() >= TWO && it.num.abs() <= Factor.MOST_PRIME }?.num
            val modes = if (whole != null) listOf(Mode.CALCULATE, Mode.FACTOR) else listOf(Mode.CALCULATE)
            return Plan(modes, Mode.CALCULATE) { mode ->
                if (mode == Mode.FACTOR && whole != null) Outcome.Primes(whole, Factor.primes(whole.abs())!!) else Outcome.Value(value, steps)
            }
        }

        var tooLarge = false
        val polynomials: List<Polynomial>? = try {
            written.map { Polynomial.of(it) }.takeIf { list -> list.all { it != null } }?.map { it!! }
        } catch (e: CalcException) {
            if (e.failure != Failure.TOO_LARGE) throw e
            tooLarge = true
            null
        }
        val compared = { p: List<Polynomial> -> Outcome.Compared(p.zipWithNext { a, b -> a == b }.all { it }, p) }
        val notPolynomial = CalcException(if (tooLarge) Failure.TOO_LARGE else Failure.NOT_POLYNOMIAL)

        if (equation && unknowns.size == 1 && written.size == 2) {
            val solve = { Solver.solve(Sub(written[0], written[1]), unknowns.first()) }
            if (polynomials == null) return Plan(listOf(Mode.SOLVE), Mode.SOLVE) { solve() }
            // Where the unknown drops out, the equation says nothing about it: whether its sides are equal is the question.
            val likeliest = if ((polynomials[0] - polynomials[1]).isConstant) Mode.COMPARE else Mode.SOLVE
            return Plan(listOf(Mode.SOLVE, Mode.COMPARE), likeliest) { mode -> if (mode == Mode.COMPARE) compared(polynomials) else solve() }
        }
        if (equation) {
            if (polynomials == null) throw if (!tooLarge && unknowns.size > 1) CalcException(Failure.SEVERAL_UNKNOWNS) else notPolynomial
            return Plan(listOf(Mode.COMPARE), Mode.COMPARE) { compared(polynomials) }
        }

        // An expression, or a chain that ends in "=".
        if (polynomials == null) throw notPolynomial
        val last = polynomials.last()
        val steps = polynomials.zipWithNext { a, b -> a == b }
        if (last.isConstant) return Plan(listOf(Mode.SIMPLIFY), Mode.SIMPLIFY) { Outcome.Simplified(last, steps) }
        // A polynomial already written as Simplify would write it is factored first.
        val asWritten = written.size == 1 && plain(texts[sides.indexOfFirst { it != null }]) == plain(style.latex(last))
        return Plan(listOf(Mode.SIMPLIFY, Mode.FACTOR), if (asWritten) Mode.FACTOR else Mode.SIMPLIFY) { mode ->
            if (mode == Mode.FACTOR) Outcome.Factored(Factor.of(last)) else Outcome.Simplified(last, steps)
        }
    }

    /** LaTeX without what does not change what it says: spaces, braces, `\cdot` between factors. */
    private fun plain(latex: String) = latex.replace("\\cdot", "").filter { it != ' ' && it != '{' && it != '}' }

    private fun same(a: Num, b: Num): Boolean =
        if (a is Num.Exact && b is Num.Exact) a.r == b.r else abs(a.double - b.double) <= 1e-9 * max(1.0, max(abs(a.double), abs(b.double)))
}

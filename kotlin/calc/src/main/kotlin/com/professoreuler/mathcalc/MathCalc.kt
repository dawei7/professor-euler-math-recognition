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
    /** An equation in more than one unknown that Calculate cannot solve for any of them, as `sin x = y`. */
    SEVERAL_UNKNOWNS,
    /** An unknown without an equation to find it, where a value had to be found. */
    UNKNOWN_WITHOUT_EQUATION,
    /** More solutions than are worth listing. */
    MANY_SOLUTIONS,
    /** Unknowns in a form Calculate does not simplify or factor. */
    NOT_POLYNOMIAL,
    /** A result too long to show, as (x + y + z)⁹⁹. */
    TOO_LARGE,
    /** No antiderivative Calculate can write with the usual functions (e^(x²) has none). */
    NO_ANTIDERIVATIVE,
    /** An integral, a sum or a series that grows without bound. */
    DIVERGES,
    /** A limit that does not exist, or that Calculate cannot find. */
    NO_LIMIT,
    /** An equation or a system Calculate cannot solve. */
    UNSOLVED,
    /** Working it out took longer than a calculation may (about three seconds). */
    TOO_LONG,
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
    /** Works out a number, or the calculus written in the formula: `37 + 48 =` is 85, ∫ x dx is x²/2 + C. */
    CALCULATE,
    /** The simplest form: like terms collected, fractions on one bar and cancelled, sin² + cos² as 1. */
    SIMPLIFY,
    /** Multiplied out: (x + 2)² is x² + 4x + 4. */
    EXPAND,
    /** The other way round, into factors: x² + 4x + 4 is (x + 2)²; a whole number into its primes. */
    FACTOR,
    /** Solves an equation or an inequality for one of its letters, or a system. */
    SOLVE,
    /** Whether the sides of an equation are equal for every value of its letters. */
    COMPARE,
    /** The derivative in one of its letters. */
    DERIVATIVE,
    /** An antiderivative in one of its letters. */
    INTEGRAL,
    /** A graph of the function, or of both sides of the equation. */
    GRAPH,
}

/** A mode, and for Solve, Derivative and Integral the letter it is for. */
data class Choice(val mode: Mode, val letter: String? = null)

/** How the formula wrote its numbers, so answers are written alike: a decimal comma, decimals, fractions. */
interface Style {
    val decimalComma: Boolean
    val decimals: Boolean
    val fractions: Boolean

    companion object {
        /** Points, fractions: the style of a formula that says nothing else. */
        val PLAIN: Style = of(decimalComma = false, decimals = false, fractions = true)

        fun of(decimalComma: Boolean, decimals: Boolean, fractions: Boolean): Style = object : Style {
            override val decimalComma = decimalComma
            override val decimals = decimals
            override val fractions = fractions
        }
    }
}

/** What a formula comes to. */
sealed class Outcome {
    /**
     * A number: `37 + 48 =` is 85. [exact] where its exact form is not a plain number (2√2, π/3, ln 5); [steps] are the
     * earlier sides of a chain, `a = b =`, and whether each held; [others] the values of a formula written with ±.
     */
    data class Value(val value: Num, val steps: List<Boolean> = emptyList(), val exact: Expr? = null, val others: List<Expr> = emptyList()) : Outcome()

    /**
     * A formula's result as a formula: simplified or multiplied out, a derivative, an antiderivative ([plusC]), a limit, a
     * sum. [steps] as for [Value]; [excluded] the values a cancelled denominator excludes, letter to value (x ≠ 1); [approx]
     * its value where it is a number.
     */
    data class Expression(val result: Expr, val steps: List<Boolean> = emptyList(), val excluded: List<Pair<String, Expr>> = emptyList(),
        val plusC: Boolean = false, val approx: Double? = null) : Outcome()

    /** A product of factors; [complete] where none can be taken apart further, [trivial] where nothing came out. */
    data class Factored(val result: Expr, val complete: Boolean, val trivial: Boolean) : Outcome()

    /** An equation and whether its sides are equal for every value of its letters; [sides] each simplified, [at] a value where they differ. */
    data class Compared(val holds: Boolean, val sides: List<Expr>, val at: Map<String, Double>? = null) : Outcome()

    /** An equation without letters, `2 + 2 = 4`, and whether it holds. */
    data class Checked(val holds: Boolean) : Outcome()

    /** The solutions of an equation in one letter. */
    data class Solved(val solutions: Solutions) : Outcome()

    /** The solution of an inequality. */
    data class Intervals(val set: IntervalSet) : Outcome()

    /** A system's solutions: each a value for each of [letters]. */
    data class SystemSolved(val letters: List<String>, val solutions: List<Map<String, Expr>>) : Outcome()

    /** A definite integral: [exact] where there is one, [value] numerically. */
    data class DefiniteIntegral(val exact: Expr?, val value: Double?) : Outcome()

    /** A whole number and its primes with their powers: 360 = 2³ · 3² · 5. */
    data class Primes(val n: BigInteger, val factors: List<Pair<BigInteger, Int>>) : Outcome()

    /** A graph: [functions] of [variable], each drawn; for an equation both sides, which meet at its solutions. */
    data class Graph(val variable: String, val functions: List<Expr>, val implicit: Expr? = null) : Outcome()

    /** Several lines of a learner's working, each checked against the line before. */
    data class LinesChecked(val lines: List<LineCheck>) : Outcome()

    data class Failed(val failure: Failure) : Outcome()
}

/**
 * A formula's outcome, and how it was written (a decimal comma, decimals, fractions, degrees), so the answer is written
 * alike. [choices] are what Calculate can do with the formula; [choice] the one [outcome] comes from.
 */
class Calculation(val outcome: Outcome, override val decimalComma: Boolean, override val decimals: Boolean,
    override val fractions: Boolean, val choices: List<Choice> = emptyList(), val choice: Choice? = null,
    /** The formula wrote angles in degrees: solutions of trigonometric equations are given in degrees too. */
    val degrees: Boolean = false) : Style {

    val modes: List<Mode> get() = choices.map { it.mode }.distinct()
    val mode: Mode? get() = choice?.mode

    /** A number in LaTeX: an integer, a fraction and its decimal, or a decimal, as the formula wrote them. */
    fun latex(n: Num): String = Format.latex(n, this)

    /** The same in plain text: `19/12 ≈ 1.583333333`. */
    fun text(n: Num): String = Format.text(n, this)

    /** An expression in LaTeX. */
    fun latex(e: Expr): String = Format.expr(e, this, latex = true)

    /** An expression in the sheets' math shorthand: `x^2 + 4x + 4`, `(5/6)x`, `√2`. */
    fun text(e: Expr): String = Format.expr(e, this, latex = false)

    /** Primes in LaTeX: `2^{3} \cdot 3^{2} \cdot 5`. */
    fun latex(p: Outcome.Primes): String = Format.primes(p, latex = true)

    fun text(p: Outcome.Primes): String = Format.primes(p, latex = false)

    /** An unknown's name in LaTeX: `x`, `x_{1}`. */
    fun latexName(unknown: String): String = Format.name(unknown, latex = true)

    /** A value with its decimal where it is not a plain number: `\sqrt{2} \approx 1.414213562`. */
    fun latexValue(e: Expr): String = Format.value(e, this, latex = true)

    fun textValue(e: Expr): String = Format.value(e, this, latex = false)

    /** An angle in degrees: `30^{\circ}`, a family `30^{\circ} + k \cdot 360^{\circ}`. */
    fun latexAngle(e: Expr): String = Format.angle(e, this, latex = true)

    fun textAngle(e: Expr): String = Format.angle(e, this, latex = false)

    /** An inequality's solution: `x < 4`, `x \in (-\infty, -1] \cup [1, \infty)`. */
    fun latex(s: IntervalSet): String = Format.intervals(s, this, latex = true)

    fun text(s: IntervalSet): String = Format.intervals(s, this, latex = false)
}

/**
 * Calculates a formula read from handwriting, as the recognizer writes it in LaTeX: arithmetic, exact roots and fractions,
 * π, e and i, functions with their exact values, equations and inequalities of every school kind, formulas solved for any
 * letter, systems, derivatives, integrals, limits and sums, and expressions simplified, multiplied out, factored and
 * compared. Every result is checked ([Verify]) before it is shown.
 */
object MathCalc {

    /**
     * [choice] as the learner picked it; null, or one that does not fit the formula, for the likeliest. [millis] is how long
     * it may take (the Calculator's "Try longer" gives more). [fractions] where the answer is to be written with exact
     * fractions although the formula wrote none with a bar: a line typed on a keyboard, whose `/` is its fraction bar.
     */
    fun calculate(latex: String, choice: Choice? = null, millis: Long = LIMIT_MS, fractions: Boolean = false): Calculation {
        val tokens = TOKEN.findAll(latex).map { it.value }.toList()
        val flags = Flags()
        var choices = emptyList<Choice>()
        var shown: Choice? = null
        val outcome = try {
            val statements = Parser(tokens, flags).statements()
            if (fractions) flags.fractions = true
            val others = if (flags.plusMinus) Parser(tokens, Flags().also { it.decimals = flags.decimals }, pmSign = -1).statements() else null
            val style = Style.of(flags.decimalComma, flags.decimals, flags.fractions)
            Budget.within(millis) {
                val plan = plan(statements, others, style, flags.degrees)
                choices = plan.choices
                val chosen = choice?.takeIf { it in plan.choices } ?: plan.likeliest
                shown = chosen
                plan.outcome(chosen)
            }
        } catch (e: CalcException) {
            Outcome.Failed(e.failure)
        } catch (e: ArithmeticException) {
            Outcome.Failed(Failure.UNDEFINED)
        } catch (e: StackOverflowError) {
            Outcome.Failed(Failure.TOO_LARGE)
        }
        return Calculation(outcome, flags.decimalComma, flags.decimals, flags.fractions, choices, shown, flags.degrees)
    }

    /** Several lines of working, each read on its own: checked step by step, or solved as a system ([Lines]). */
    fun lines(readings: List<String>): Calculation = Lines.check(readings)

    /** Calculate with a mode by name: the [choice] for the formula's likeliest letter. */
    fun calculate(latex: String, mode: Mode?): Calculation {
        if (mode == null) return calculate(latex, null as Choice?)
        val first = calculate(latex, null as Choice?)
        val choice = first.choices.firstOrNull { it.mode == mode } ?: return first
        return calculate(latex, choice)
    }

    /** How long one calculation may take. */
    const val LIMIT_MS = 3000L

    internal val TOKEN = Regex("""\\[a-zA-Z]+|\\.|\S""")

    private val TWO = BigInteger.valueOf(2)

    /** The choices that fit a formula, the likeliest of them, and how each comes to its outcome. */
    private class Plan(val choices: List<Choice>, val likeliest: Choice, val outcome: (Choice) -> Outcome)

    private fun plan(statements: List<Statement>, others: List<Statement>?, style: Style, degrees: Boolean): Plan {
        if (statements.size > 1) return systemPlan(statements)
        val s = statements[0]
        if (s.definition != null) return definitionPlan(s)
        val written = s.sides.filterNotNull()
        if (written.isEmpty() || s.sides.dropLast(1).any { it == null }) throw CalcException(Failure.UNREADABLE)
        if (written.any { it.hasInfinity() && !it.hasOp() }) throw CalcException(Failure.UNREADABLE)
        val open = s.sides.last() == null
        val relations = s.relations.take(written.size - 1)
        val equation = !open && written.size >= 2
        // Calculus written in the formula is worked out first.
        val sides = written.map { Calculus.evaluate(it) }
        val wroteCalculus = written.any { it.hasOp() }
        val letters = sides.flatMap { it.symbols() }.toSortedSet().toList()

        if (equation && relations.any { it.isInequality || it == Rel.NE }) return inequalityPlan(sides, relations, letters)

        if (letters.isEmpty()) {
            val steps = sides.zipWithNext { a, b -> same(a, b) }
            if (equation) return Plan(listOf(Choice(Mode.COMPARE)), Choice(Mode.COMPARE)) { Outcome.Checked(steps.all { it }) }
            val v = sides.last()
            val alternatives = others?.let { o -> o[0].sides.filterNotNull().lastOrNull()?.let { Calculus.evaluate(it) } }
            val whole = integer(v)?.takeIf { it.abs() >= TWO && it.abs() <= Factor.MOST_PRIME }
            val choices = listOfNotNull(Choice(Mode.CALCULATE), whole?.let { Choice(Mode.FACTOR) })
            return Plan(choices, Choice(Mode.CALCULATE)) { c ->
                if (c.mode == Mode.FACTOR && whole != null) Outcome.Primes(whole, Factor.primes(whole.abs())!!)
                else value(v, steps, listOfNotNull(alternatives).filter { it != v })
            }
        }

        val variable = mainLetter(letters)
        val x = Expr.Sym(variable)
        if (!equation) {
            // An expression, or a chain that ends in "=".
            val last = sides.last()
            val steps = sides.zipWithNext { a, b -> Verify.same(a, b) }
            if (wroteCalculus) {
                return Plan(listOf(Choice(Mode.CALCULATE)), Choice(Mode.CALCULATE)) {
                    val integral = written.last() is Expr.Op && (written.last() as Expr.Op).let { it.kind == OpKind.INTEGRAL && it.a == null }
                    Outcome.Expression(last, steps, plusC = integral)
                }
            }
            val simplified = safeSimplify(last)
            val expanded = try { Algebra.expand(last) } catch (e: CalcException) { last }
            val factorable = Algebra.factor(last) != null
            val choices = ArrayList<Choice>()
            choices += Choice(Mode.SIMPLIFY)
            if (expanded != simplified && expanded != last) choices += Choice(Mode.EXPAND)
            if (factorable && !last.isConstantInLetters()) choices += Choice(Mode.FACTOR)
            letters.forEach { choices += Choice(Mode.DERIVATIVE, it) }
            letters.forEach { choices += Choice(Mode.INTEGRAL, it) }
            if (letters.size == 1) choices += Choice(Mode.GRAPH, variable)
            // A polynomial already written as Simplify would write it is factored first.
            val asWritten = written.size == 1 && plain(s.texts[s.sides.indexOfFirst { it != null }]) == plain(Format.expr(simplified, style, true))
            val polynomial = Algebra.Kernels().let { k -> Algebra.polynomial(last, k) != null && k.isEmpty }
            val likeliest = when {
                factorable && polynomial && asWritten -> Choice(Mode.FACTOR)
                else -> Choice(Mode.SIMPLIFY)
            }
            return Plan(choices, likeliest) { c ->
                when (c.mode) {
                    Mode.SIMPLIFY -> {
                        val excluded = ArrayList<Expr>()
                        val r = Algebra.simplify(last, excluded)
                        Outcome.Expression(r, steps, excluded = excludedValues(excluded))
                    }
                    Mode.EXPAND -> Outcome.Expression(expanded, steps)
                    Mode.FACTOR -> factored(last)
                    Mode.DERIVATIVE -> Outcome.Expression(Calculus.derivative(last, Expr.Sym(c.letter!!)))
                    Mode.INTEGRAL -> Outcome.Expression(Calculus.antiderivative(last, Expr.Sym(c.letter!!))
                        ?: throw CalcException(Failure.NO_ANTIDERIVATIVE), plusC = true)
                    Mode.GRAPH -> Outcome.Graph(variable, listOf(last))
                    else -> throw CalcException(Failure.UNREADABLE)
                }
            }
        }

        // Equations.
        val compareOutcome = { compare(sides) }
        if (written.size > 2) return Plan(listOf(Choice(Mode.COMPARE)), Choice(Mode.COMPARE)) { compareOutcome() }
        val (lhs, rhs) = sides
        val difference = lhs - rhs
        val identity = Verify.same(lhs, rhs)
        val choices = ArrayList<Choice>()
        for (v in letters) choices += Choice(Mode.SOLVE, v)
        choices += Choice(Mode.COMPARE)
        val graphable = letters.size == 1 || (letters.toSet() == setOf("x", "y"))
        if (graphable) choices += Choice(Mode.GRAPH, variable)
        // y = f(x) is a graph; an equation whose letter drops out, or a learner's answer to calculus, is checked; else solved.
        val explicit = letters.toSet() == setOf("x", "y") && (lhs == Expr.Sym("y") || rhs == Expr.Sym("y"))
        // A formula, a letter on one side (v = s/t), is rearranged; two compound sides in several letters, (a+b)² = a² + b², checked.
        val formula = lhs is Expr.Sym || rhs is Expr.Sym
        val likeliest = when {
            wroteCalculus -> Choice(Mode.COMPARE)
            identity || letters.none { difference.has(Expr.Sym(it)) } -> Choice(Mode.COMPARE)
            letters.size == 1 && isConstantDifference(difference) -> Choice(Mode.COMPARE)
            explicit -> Choice(Mode.GRAPH, variable)
            letters.size > 1 && !formula -> Choice(Mode.COMPARE)
            else -> Choice(Mode.SOLVE, if (letters.size == 1) variable else formulaLetter(lhs, rhs, letters))
        }
        return Plan(choices, likeliest) { c ->
            when (c.mode) {
                Mode.COMPARE -> compareOutcome()
                Mode.GRAPH -> if (explicit) Outcome.Graph("x", listOf(if (lhs == Expr.Sym("y")) rhs else lhs))
                    else if (letters.size == 1) Outcome.Graph(variable, listOf(lhs, rhs)) else Outcome.Graph("x", emptyList(), implicit = difference)
                Mode.SOLVE -> {
                    val v = Expr.Sym(c.letter ?: variable)
                    val found = if (letters.size == 1) Solve.equation(lhs, rhs, v) else Solve.forLetter(lhs, rhs, v)
                    // An unknown in degrees (sin x° = 1/2) has its solutions in degrees: numbers without π, shown with °.
                    val inDegrees = degrees && found.periodic.isNotEmpty() && (found.periodic + found.values).none { it.hasPi() }
                    val solutions = if (inDegrees) found.markedDegrees() else found
                    if (letters.size > 1 && solutions.values.isEmpty()) throw CalcException(Failure.SEVERAL_UNKNOWNS)
                    Outcome.Solved(solutions)
                }
                else -> throw CalcException(Failure.UNREADABLE)
            }
        }
    }

    private fun Expr.isConstantInLetters() = symbols().isEmpty()

    private fun Expr.hasPi(): Boolean = when (this) {
        is Expr.Const -> kind == Constant.PI
        is Expr.Add -> terms.any { it.hasPi() }
        is Expr.Mul -> factors.any { it.hasPi() }
        is Expr.Pow -> base.hasPi() || exp.hasPi()
        is Expr.Fn -> args.any { it.hasPi() }
        else -> false
    }

    /** Factors cancelled from a denominator as the values they exclude: x − 1 as x ≠ 1. */
    private fun excludedValues(factors: List<Expr>): List<Pair<String, Expr>> = factors.distinct().flatMap { f ->
        val letter = f.symbols().singleOrNull() ?: return@flatMap emptyList()
        try {
            Solve.equation(f, Expr.ZERO, Expr.Sym(letter)).values.map { letter to it }
        } catch (e: CalcException) {
            emptyList()
        }
    }.distinct()

    /** The letter a formula is likeliest rearranged for: x where it has one, else the last letter of its compound side (v = s/t: t). */
    private fun formulaLetter(lhs: Expr, rhs: Expr, letters: List<String>): String {
        if ("x" in letters) return "x"
        val compound = if (lhs is Expr.Sym) rhs else lhs
        return compound.symbols().lastOrNull() ?: letters.last()
    }

    /** The letter a formula is about: x where it has one, else the only one, else the last in the alphabet (y before a). */
    private fun mainLetter(letters: List<String>): String = when {
        "x" in letters -> "x"
        letters.size == 1 -> letters[0]
        else -> letters.last()
    }

    /**
     * Whether the sides differ by a number for every value: 2x + 1 = 2x + 3. Not where that needs a factor cancelled from a
     * denominator, x/(x − 2) = 2/(x − 2), which is an equation to solve (here without solutions).
     */
    private fun isConstantDifference(d: Expr): Boolean = try {
        val excluded = ArrayList<Expr>()
        Algebra.simplify(d, excluded).isConstant() && excluded.isEmpty()
    } catch (e: CalcException) {
        false
    }

    private fun safeSimplify(e: Expr): Expr = try {
        Algebra.simplify(e)
    } catch (x: CalcException) {
        if (x.failure == Failure.DIVISION_BY_ZERO) throw x
        e
    }

    /** A number's outcome: exact as a plain number, or its exact form with the decimal beside it; complex numbers as formulas. */
    private fun value(v: Expr, steps: List<Boolean>, others: List<Expr>): Outcome {
        if (v.hasI()) return Outcome.Expression(Algebra.expand(v), steps)
        if (v.hasInfinity()) return Outcome.Expression(v, steps)
        if (v is Expr.Num) return Outcome.Value(Num.Exact(v.value), steps, others = others)
        val d = Numeric.value(v) ?: throw CalcException(Failure.UNDEFINED)
        val shown = Algebra.rationalize(v)
        return Outcome.Value(Num.Approx(d), steps, exact = shown.takeIf { niceExact(it) }, others = others)
    }

    /** An exact form worth showing: roots, π, e, logarithms of numbers; not sin(47°), whose decimal says more. */
    internal fun niceExact(e: Expr): Boolean {
        if (e is Expr.Real || e.size() > 24) return false
        fun ok(t: Expr): Boolean = when (t) {
            is Expr.Num, is Expr.Const -> true
            is Expr.Real -> false
            is Expr.Add -> t.terms.all(::ok)
            is Expr.Mul -> t.factors.all(::ok)
            is Expr.Pow -> ok(t.base) && ok(t.exp)
            is Expr.Fn -> (t.f == Func.LN || t.f == Func.LOG || t.f == Func.ABS) && t.args.all(::ok)
            else -> false
        }
        return ok(e)
    }

    private fun factored(e: Expr): Outcome {
        val (result, product) = Algebra.factor(e) ?: throw CalcException(Failure.NOT_POLYNOMIAL)
        return Outcome.Factored(result, complete = product?.complete ?: true, trivial = product?.trivial ?: (result == e))
    }

    private fun compare(sides: List<Expr>): Outcome {
        val simplified = sides.map { safeSimplify(it) }
        var at: Map<String, Double>? = null
        var holds = true
        for ((a, b) in sides.zipWithNext()) {
            if (Verify.same(a, b)) continue
            holds = false
            at = at ?: Verify.differenceAt(a, b)
        }
        return Outcome.Compared(holds, simplified, at)
    }

    private fun inequalityPlan(sides: List<Expr>, relations: List<Rel>, letters: List<String>): Plan {
        if (letters.isEmpty()) {
            val holds = sides.zipWithNext().zip(relations).all { (pair, rel) ->
                val a = Numeric.value(pair.first) ?: return@all false
                val b = Numeric.value(pair.second) ?: return@all false
                when (rel) {
                    Rel.LT -> a < b
                    Rel.LE -> a <= b + 1e-12
                    Rel.GT -> a > b
                    Rel.GE -> a >= b - 1e-12
                    Rel.NE -> abs(a - b) > 1e-12 * max(1.0, abs(a))
                    Rel.EQ -> abs(a - b) <= 1e-12 * max(1.0, abs(a))
                }
            }
            return Plan(listOf(Choice(Mode.COMPARE)), Choice(Mode.COMPARE)) { Outcome.Checked(holds) }
        }
        if (letters.size > 1 || sides.size > 3) throw CalcException(Failure.SEVERAL_UNKNOWNS)
        val x = Expr.Sym(letters[0])
        val choices = listOf(Choice(Mode.SOLVE, letters[0]), Choice(Mode.GRAPH, letters[0]))
        return Plan(choices, choices[0]) { c ->
            if (c.mode == Mode.GRAPH) return@Plan Outcome.Graph(letters[0], sides.filter { it.has(x) || true })
            // a < b < c as a < b and b < c: both sets, intersected.
            val sets = sides.zipWithNext().zip(relations).map { (pair, rel) ->
                Solve.inequality(pair.first, rel, pair.second, x) ?: throw CalcException(Failure.UNSOLVED)
            }
            Outcome.Intervals(if (sets.size == 1) sets[0] else intersect(sets, x))
        }
    }

    /** The intersection of interval sets: tested between all their bounds. */
    private fun intersect(sets: List<IntervalSet>, x: Expr.Sym): IntervalSet {
        val bounds = sets.flatMap { s -> s.intervals.flatMap { listOfNotNull(it.lower, it.upper) } }
            .distinctBy { Math.round((Numeric.value(it) ?: 0.0) * 1e9) }.sortedBy { Numeric.value(it) ?: 0.0 }
        fun inSet(s: IntervalSet, v: Double) = s.intervals.any { i ->
            val lo = i.lower?.let { Numeric.value(it) }
            val hi = i.upper?.let { Numeric.value(it) }
            (lo == null || v > lo || (i.lowerClosed && abs(v - lo) < 1e-12)) && (hi == null || v < hi || (i.upperClosed && abs(v - hi) < 1e-12))
        }
        fun all(v: Double) = sets.all { inSet(it, v) }
        val values = bounds.map { Numeric.value(it)!! }
        val pieces = ArrayList<Interval>()
        var inside = all(if (values.isEmpty()) 0.0 else values[0] - 1)
        var start: Expr? = null
        var startClosed = false
        for ((k, p) in bounds.withIndex()) {
            val atPoint = all(values[k])
            if (inside && !atPoint) { pieces += Interval(start, startClosed, p, false); inside = false }
            else if (!inside && atPoint) { inside = true; start = p; startClosed = true }
            val next = values.getOrNull(k + 1)
            val after = all(if (next == null) values[k] + 1 else (values[k] + next) / 2)
            if (inside && !after) { pieces += Interval(start, startClosed, p, true); inside = false }
            else if (!inside && after) { inside = true; start = p; startClosed = false }
        }
        if (inside) pieces += Interval(start, startClosed, null, false)
        return IntervalSet(x.name, pieces)
    }

    private fun systemPlan(statements: List<Statement>): Plan {
        val equations = statements.map { s ->
            val sides = s.sides.filterNotNull()
            if (sides.size != 2 || s.relations.size != 1 || s.relations[0] != Rel.EQ) throw CalcException(Failure.UNREADABLE)
            Calculus.evaluate(sides[0]) - Calculus.evaluate(sides[1])
        }
        val letters = equations.flatMap { it.symbols() }.toSortedSet().toList()
        val choice = Choice(Mode.SOLVE)
        return Plan(listOf(choice), choice) {
            val solutions = Solve.system(equations, letters) ?: throw CalcException(Failure.UNSOLVED)
            Outcome.SystemSolved(letters, solutions)
        }
    }

    private fun definitionPlan(s: Statement): Plan {
        val def = s.definition!!
        val body = Calculus.evaluate(s.sides.getOrNull(1) ?: throw CalcException(Failure.UNREADABLE))
        val x = Expr.Sym(def.variable)
        val choices = listOf(Choice(Mode.GRAPH, def.variable), Choice(Mode.DERIVATIVE, def.variable), Choice(Mode.INTEGRAL, def.variable),
            Choice(Mode.SOLVE, def.variable), Choice(Mode.SIMPLIFY))
        return Plan(choices, choices[0]) { c ->
            when (c.mode) {
                Mode.GRAPH -> Outcome.Graph(def.variable, listOf(body))
                Mode.DERIVATIVE -> Outcome.Expression(Calculus.derivative(body, x))
                Mode.INTEGRAL -> Outcome.Expression(Calculus.antiderivative(body, x) ?: throw CalcException(Failure.NO_ANTIDERIVATIVE), plusC = true)
                Mode.SOLVE -> Outcome.Solved(Solve.equation(body, Expr.ZERO, x))
                else -> Outcome.Expression(Algebra.simplify(body))
            }
        }
    }

    /** LaTeX without what does not change what it says: spaces, braces, `\cdot` between factors, `\left`, `\right`. */
    private fun plain(latex: String) = latex.replace("\\cdot", "").replace("\\left", "").replace("\\right", "")
        .filter { it != ' ' && it != '{' && it != '}' }

    private fun same(a: Expr, b: Expr): Boolean {
        if (a == b) return true
        val x = Numeric.value(a) ?: return Verify.same(a, b)
        val y = Numeric.value(b) ?: return false
        if (a is Expr.Num && b is Expr.Num) return a.value == b.value
        return abs(x - y) <= 1e-9 * max(1.0, max(abs(x), abs(y)))
    }
}

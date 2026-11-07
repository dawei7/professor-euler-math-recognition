package com.professoreuler.mathcalc

/** What a panel of the Calculator's report shows. */
enum class PanelKind {
    /** What the input asked for, or else its likeliest choice: Calculate's own answer. */
    RESULT,

    /** Another choice that fits the input (Simplify, Factor, Solve for y, the graph …), worked out on its own. */
    CHOICE,

    /** A number's certified digits, as many as asked for. */
    DIGITS,

    /** A function's curve sketch: domain, zeros, symmetry, extrema, inflection points, monotony, asymptotes ([Analysis]). */
    FUNCTION,
}

/** A panel of the report before it is worked out: its kind and, for a choice, which one. */
data class Panel(val kind: PanelKind, val choice: Choice? = null)

/** A panel worked out. */
sealed class PanelResult {
    data class Calculated(val calculation: Calculation) : PanelResult()

    /** [value]'s digits, [digits] of each part; [style] how the input wrote its numbers (a decimal comma). */
    data class Digits(val value: PreciseValue, val digits: Int, val style: Style) : PanelResult()

    /** A function's [facts], written as the input wrote its numbers ([calculation]'s style). */
    class Function(val facts: FunctionFacts, val calculation: Calculation) : PanelResult()

    data class Failed(val failure: Failure) : PanelResult()
}

/**
 * The Calculator's report on one input, as Wolfram Alpha's pods: [result] is Calculate's answer, worked out at once; [panels]
 * are what else fits the input, each worked out on its own by [Reports.compute] (in parallel, each in its own time), so a
 * slow one never holds the others up. [complex] where complex values are wanted (ln(−1) is iπ); otherwise the real numbers.
 */
class Report internal constructor(
    val latex: String,
    val complex: Boolean,
    val result: Calculation,
    val panels: List<Panel>,
    internal val constant: Expr?,
    internal val fractions: Boolean,
    /** How the input was read where it could be read two ways. */
    val assumptions: Assumptions,
) {
    /** What the input holds that could be read two ways ("Assuming …"): [assumptions] flipped for one reads it the other way. */
    val ambiguities: List<Ambiguity> get() = result.ambiguities.toList()
}

object Reports {
    /** The digits a number shows first; "More digits" asks for more. */
    const val FIRST_DIGITS = 20

    /**
     * The report on [latex]: [wanted] as the learner's words or choice asked (null for the likeliest); [fractions] for a
     * typed line, whose answers are written with exact fractions ([Typed.fractions]).
     */
    fun of(latex: String, wanted: Choice? = null, complex: Boolean = true, millis: Long = MathCalc.LIMIT_MS, fractions: Boolean = false,
        assumptions: Assumptions = Assumptions()): Report {
        val first = inDomain(complex) { MathCalc.calculate(latex, wanted, millis, fractions, assumptions) }
        // Words that name a mode but no letter ("derivative of x² y") take that mode for the formula's likeliest letter.
        val chosen = if (wanted != null && wanted.letter == null && first.choice?.mode != wanted.mode) {
            first.choices.firstOrNull { it.mode == wanted.mode }?.let { c -> inDomain(complex) { MathCalc.calculate(latex, c, millis, fractions, assumptions) } } ?: first
        } else {
            first
        }
        val result = if (wanted == null) chosen else asked(wanted, chosen)
        val constant = if (result.outcome is Outcome.Failed) null else inDomain(complex) { constantOf(latex, assumptions) }
        val panels = ArrayList<Panel>()
        panels += Panel(PanelKind.RESULT, result.choice)
        // The digits of the input's value, only where the answer is that value written another way: never beside an answer that
        // is another number (frombase(11111111, 2) is 255, derivative(5, x) is 0, volumex(2, 0, 1) is 4π) or a method's own
        // approximation (trapezoid, simpson); a value converted between units has its digits in its own answer.
        val mode = result.choice?.mode
        if (constant != null && (mode == null || mode in SAME_VALUE)) panels += Panel(PanelKind.DIGITS)
        val others = result.choices.filter { it != result.choice && kindred(wanted, it) }
        // A graph is worth seeing early, as Wolfram Alpha shows its plots (a surface first); a function's curve sketch beside it.
        others.filter { it.mode == Mode.SURFACE }.forEach { panels += Panel(PanelKind.CHOICE, it) }
        others.filter { it.mode == Mode.GRAPH }.forEach { panels += Panel(PanelKind.CHOICE, it) }
        result.choices.firstOrNull { it.mode == Mode.GRAPH }?.let { graph ->
            // Only of an expression in one letter: an equation's graph has two sides, and no curve sketch.
            val function = try { inDomain(complex) { expressionOf(latex, assumptions) } } catch (e: CalcException) { null }
            if (function != null && function.symbols().size == 1 && !function.hasOp()) panels += Panel(PanelKind.FUNCTION, graph)
        }
        // A function of several letters: its critical points next, then the rest.
        others.filter { it.mode == Mode.EXTREMA }.forEach { panels += Panel(PanelKind.CHOICE, it) }
        others.filter { it.mode != Mode.GRAPH && it.mode != Mode.SURFACE && it.mode != Mode.EXTREMA }.forEach { panels += Panel(PanelKind.CHOICE, it) }
        return Report(latex, complex, result, panels, constant, fractions, assumptions)
    }

    /** What answers with the input's own value written another way: the digits of that value belong beside it. */
    private val SAME_VALUE = setOf(Mode.CALCULATE, Mode.SIMPLIFY, Mode.EXPAND, Mode.FACTOR, Mode.NUMBER_FACTS, Mode.POLAR, Mode.CONSTANT)

    /** A vector field's derivatives: curl(…), divergence(…) and jacobian(…) are written as the field's column. */
    private val FIELDS = setOf(Mode.CURL, Mode.DIVERGENCE, Mode.JACOBIAN)

    /** What works on a matrix as a whole. */
    private val MATRIX = setOf(Mode.DETERMINANT, Mode.INVERSE, Mode.EIGEN, Mode.ROW_REDUCE, Mode.DECOMPOSE, Mode.SVD, Mode.JORDAN,
        Mode.MATRIX_EXP, Mode.TRANSPOSE)

    /**
     * Whether [other], another reading of the input, belongs in the report beside what a command or a choice asked for
     * ([wanted]; every other reading where nothing was asked). A command written as the list of its arguments (irr,
     * amortization, stats, sequence, convert) or as a number in a base (tobase, frombase) has no other: the list's own readings
     * (its column, its transpose, its statistics, a rule for it) and the number's (its factors) are no part of its answer. Points (interpolate, spline, regression) keep the other curves
     * through them, not the matrix they make; a vector field keeps its other derivatives, not its column; a matrix keeps what
     * else works on it, not its rows read as points.
     */
    private fun kindred(wanted: Choice?, other: Choice): Boolean {
        val mode = wanted?.mode ?: return true
        return when (CommandFunctions.shapeOf(mode)) {
            CommandFunctions.Shape.LIST, CommandFunctions.Shape.CONVERT, CommandFunctions.Shape.ARGS, CommandFunctions.Shape.BASE -> false
            CommandFunctions.Shape.POINTS -> CommandFunctions.shapeOf(other.mode) == CommandFunctions.Shape.POINTS
            else -> when (mode) {
                in FIELDS -> other.mode in FIELDS
                in MATRIX -> other.mode in MATRIX
                else -> true
            }
        }
    }

    /** The report on a typed line ([TextInput.read]). */
    fun of(typed: Typed, complex: Boolean = true, millis: Long = MathCalc.LIMIT_MS, assumptions: Assumptions = Assumptions()): Report =
        of(typed.latex, typed.choice, complex, millis, typed.fractions, assumptions)

    /** One panel worked out, within [millis]; [digits] for a [PanelKind.DIGITS] panel. Thread-safe: call them in parallel. */
    fun compute(report: Report, panel: Panel, millis: Long = MathCalc.LIMIT_MS, digits: Int = FIRST_DIGITS): PanelResult = when (panel.kind) {
        PanelKind.RESULT -> PanelResult.Calculated(report.result)
        PanelKind.CHOICE -> PanelResult.Calculated(inDomain(report.complex) { MathCalc.calculate(report.latex, panel.choice, millis, report.fractions, report.assumptions) })
        PanelKind.FUNCTION -> try {
            val letter = panel.choice?.letter
            val f = if (letter == null) null else inDomain(report.complex) { expressionOf(report.latex, report.assumptions) }
            val facts = f?.let { Budget.within(millis) { Analysis.of(it, Expr.Sym(letter!!)) } }
            if (facts == null) PanelResult.Failed(Failure.UNSOLVED) else PanelResult.Function(facts, report.result)
        } catch (x: CalcException) {
            PanelResult.Failed(x.failure)
        } catch (x: ArithmeticException) {
            PanelResult.Failed(Failure.UNDEFINED)
        } catch (x: StackOverflowError) {
            PanelResult.Failed(Failure.TOO_LARGE)
        }
        PanelKind.DIGITS -> {
            val e = report.constant
            if (e == null) {
                PanelResult.Failed(Failure.UNREADABLE)
            } else {
                try {
                    val value = Budget.within(millis) { Precise.digits(e, digits.coerceIn(1, Precise.MAX_DIGITS), report.complex) }
                    PanelResult.Digits(value, digits, report.result)
                } catch (x: CalcException) {
                    PanelResult.Failed(x.failure)
                } catch (x: ArithmeticException) {
                    PanelResult.Failed(Failure.UNDEFINED)
                } catch (x: StackOverflowError) {
                    PanelResult.Failed(Failure.TOO_LARGE)
                }
            }
        }
    }

    /**
     * solve for a letter the equation does not hold is refused, never solved for another letter in its place (solve(x² = 4, y)
     * is no x = ±2). Every other operation asked for by name MathCalc does or refuses itself.
     */
    private fun asked(wanted: Choice, result: Calculation): Calculation {
        if (wanted.mode != Mode.SOLVE || wanted.letter == null || result.outcome is Outcome.Failed) return result
        if (result.choice?.mode != Mode.SOLVE || result.choice?.letter == wanted.letter) return result
        return Calculation(Outcome.Failed(Failure.ARGUMENTS), result.decimalComma, result.decimals, result.fractions, result.choices, wanted,
            result.degrees, result.assumptions, result.ambiguities)
    }

    /** The formula's value as a constant expression (no letters, its calculus worked out), if it is one. */
    private fun constantOf(latex: String, assumptions: Assumptions): Expr? = try {
        val tokens = MathCalc.TOKEN.findAll(latex).map { it.value }.toList()
        val statements = Parser(tokens, Flags(assumptions)).statements()
        if (statements.size != 1 || statements[0].definition != null) {
            null
        } else {
            val sides = statements[0].sides.filterNotNull()
            // An equation's sides are compared, not a number.
            val open = statements[0].sides.last() == null
            if (sides.isEmpty() || (sides.size > 1 && !open)) {
                null
            } else {
                Budget.within(MathCalc.LIMIT_MS) { Calculus.evaluate(sides.last()) }
                    .takeIf { it !is Expr.Mat && it.symbols().isEmpty() && !it.hasOp() && !it.hasInfinity() && !hasReal(it) }
            }
        }
    } catch (e: CalcException) {
        null
    } catch (e: ArithmeticException) {
        null
    } catch (e: StackOverflowError) {
        null
    }

    /** The formula as one expression (no equation), its calculus worked out: what a curve sketch is of. */
    private fun expressionOf(latex: String, assumptions: Assumptions): Expr? {
        val tokens = MathCalc.TOKEN.findAll(latex).map { it.value }.toList()
        val statements = Parser(tokens, Flags(assumptions)).statements()
        if (statements.size != 1 || statements[0].definition != null) return null
        val sides = statements[0].sides
        val written = sides.filterNotNull()
        if (written.isEmpty() || (written.size > 1 && sides.last() != null)) return null
        return Budget.within(MathCalc.LIMIT_MS) { Calculus.evaluate(written.last()) }
    }

    private fun hasReal(e: Expr): Boolean = when (e) {
        is Expr.Real -> true
        is Expr.Add -> e.terms.any(::hasReal)
        is Expr.Mul -> e.factors.any(::hasReal)
        is Expr.Pow -> hasReal(e.base) || hasReal(e.exp)
        is Expr.Fn -> e.args.any(::hasReal)
        else -> false
    }

    private fun <T> inDomain(complex: Boolean, block: () -> T): T = if (complex) Domain.complex(block) else block()
}

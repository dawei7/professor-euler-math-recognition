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
        val result = if (wanted != null && wanted.letter == null && first.choice?.mode != wanted.mode) {
            first.choices.firstOrNull { it.mode == wanted.mode }?.let { c -> inDomain(complex) { MathCalc.calculate(latex, c, millis, fractions, assumptions) } } ?: first
        } else {
            first
        }
        val constant = if (result.outcome is Outcome.Failed) null else inDomain(complex) { constantOf(latex, assumptions) }
        val panels = ArrayList<Panel>()
        panels += Panel(PanelKind.RESULT, result.choice)
        if (constant != null) panels += Panel(PanelKind.DIGITS)
        val others = result.choices.filter { it != result.choice }
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
                    .takeIf { it.symbols().isEmpty() && !it.hasOp() && !it.hasInfinity() && !hasReal(it) }
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

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
    /** A square matrix with determinant 0: it has no inverse. */
    SINGULAR,
    /** Matrices or vectors whose sizes do not fit the operation: a 2 × 3 matrix times a 2 × 2 one. */
    SIZES,
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
    /** A fraction of polynomials as its partial fractions: 1/(x² − 1) = 1/(2(x − 1)) − 1/(2(x + 1)). */
    PARTIAL_FRACTIONS,
    /** The Taylor (or Laurent) series at a point: sin x = x − x³/6 + x⁵/120 + O(x⁷). */
    SERIES,
    /** Where a function of x, y (and z) has gradient 0, and whether each is a minimum, a maximum or a saddle. */
    EXTREMA,
    /** A function of x and y as a surface z = f(x, y), turned by a finger. */
    SURFACE,
    /** A square matrix's determinant. */
    DETERMINANT,
    /** A square matrix's inverse, checked: A·A⁻¹ = I. */
    INVERSE,
    /** A square matrix's eigenvalues and eigenvectors, with its characteristic polynomial. */
    EIGEN,
    /** A matrix in reduced row echelon form, with its row operations, rank and null space. */
    ROW_REDUCE,
    /** A matrix with its rows as columns. */
    TRANSPOSE,
    /** A linear system with a letter in it, solved for each value of that letter: where it has one solution, none, or many. */
    CASES,
    /** A matrix as P·A = L·U, A = Q·R and, where it is symmetric positive definite, A = L·Lᵀ. */
    DECOMPOSE,
    /** e^A of a square matrix. */
    MATRIX_EXP,
    /** ∇f of a function of x, y (and z). */
    GRADIENT,
    /** div F of a vector field in x, y (and z). */
    DIVERGENCE,
    /** curl F of a vector field (in the plane its one component). */
    CURL,
    /** The Jacobian matrix of a vector field. */
    JACOBIAN,
    /** Δf of a function of x, y (and z). */
    LAPLACIAN,
    /** The x that makes |A·x − b| least, for a linear system with more equations than unknowns and no solution. */
    LEAST_SQUARES,
    /** A differential equation solved, with its initial values where they are given. */
    ODE,
    /** The Laplace transform of f(t). */
    LAPLACE,
    /** f(t) whose Laplace transform is F(s). */
    INVERSE_LAPLACE,
    /** The Fourier series on [−L, L] (π where no L is asked for). */
    FOURIER,
    /** Newton's method from x₀ ([Choice.point]). */
    NEWTON,
    /** Bisection on [a, b] ([Choice.point] "a,b"). */
    BISECTION,
    /** Euler's method for y′ = f(x, y) from its initial value. */
    EULER,
    /** The classical Runge–Kutta method. */
    RUNGE_KUTTA,
    /** The trapezoidal rule for a definite integral. */
    TRAPEZOID,
    /** Simpson's rule. */
    SIMPSON,
    /** A complex number's polar form. */
    POLAR,
    /** A function's poles and their residues. */
    RESIDUES,
}

/** A point where a function's gradient is 0: where it is, what kind of point it is, the function's value there. */
data class CriticalPoint(val at: Map<String, Expr>, val kind: CriticalKind, val value: Expr)

/** What the second-derivative test (the Hessian's leading minors) says of a critical point. */
enum class CriticalKind { MINIMUM, MAXIMUM, SADDLE, UNDECIDED }

/**
 * A mode, and for Solve, Derivative, Integral and Series the letter it is for; for Series also the point (in LaTeX, 0 where
 * none is given) and the highest power shown ([order], 6 where none is given).
 */
data class Choice(val mode: Mode, val letter: String? = null, val point: String? = null, val order: Int? = null)

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
        val plusC: Boolean = false, val approx: Double? = null,
        /** A series: its coefficients and powers of [seriesIn] (x, or x − a) in rising powers, and its remainder's power (O(x⁷)). */
        val series: List<Pair<Expr, Int>>? = null, val seriesIn: Expr? = null, val remainder: Expr? = null) : Outcome()

    /**
     * A product of factors; [complete] where none can be taken apart further, [trivial] where nothing came out. [overReals] and
     * [overComplex] take it further where that is possible: x² − 2 = (x − √2)(x + √2), x² + 1 = (x − i)(x + i).
     */
    data class Factored(val result: Expr, val complete: Boolean, val trivial: Boolean, val overReals: Expr? = null,
        val overComplex: Expr? = null) : Outcome()

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

    /**
     * A graph: [functions] of [variable], each drawn; for an equation both sides, which meet at its solutions; an equation in
     * x and y as the curve [implicit] = 0, and an inequality in them as the region where [implicit] [region] 0. [parameters] are
     * its other letters (y = ax² + bx + c: a, b, c), each a slider, 1 until moved ([Plot.at]).
     */
    /**
     * The critical points of a function of [letters]: its [gradient] and [hessian], and each point where the gradient is 0
     * (checked by putting it in), with what the second-derivative test makes of it and the function's value there.
     */
    data class Critical(val letters: List<String>, val gradient: List<Expr>, val hessian: List<List<Expr>>, val points: List<CriticalPoint>) : Outcome()

    /** A matrix in reduced row echelon [form], its [rank], the row [operations] that made it and a basis of its [nullSpace]. */
    data class RowReduced(val form: Expr.Mat, val rank: Int, val operations: List<LinAlg.RowOp>, val nullSpace: List<Expr.Mat>,
        /** The original columns at the pivots: a basis of the column space. */
        val columnSpace: List<Expr.Mat> = emptyList()) : Outcome()

    /** A matrix's decompositions, those that exist: P·A = L·U, A = Q·R, A = L·Lᵀ. */
    data class Decompositions(val lu: Triple<Expr.Mat, Expr.Mat, Expr.Mat>?, val qr: Pair<Expr.Mat, Expr.Mat>?, val cholesky: Expr.Mat?) : Outcome()

    /** f's candidates for an extremum under [constraints] (Lagrange), each with its value; the largest and smallest marked. */
    data class Constrained(val letters: List<String>, val constraints: List<Expr>, val candidates: List<LinAlg.Candidate>) : Outcome()

    /** A system with no solution, solved in the least squares: its [solution] and the [residual] |A·x − b|. */
    data class LeastSquares(val letters: List<String>, val solution: Map<String, Expr>, val residual: Expr) : Outcome()

    /** A square matrix's [characteristic] polynomial det(λI − A) and its eigenvalues, each with its eigenvectors. */
    data class Eigenvalues(val characteristic: Expr, val values: List<LinAlg.Eigen>,
        /** P and D of A = P·D·P⁻¹ where A can be diagonalized. */
        val diagonal: Pair<Expr.Mat, Expr.Mat>? = null) : Outcome()

    /**
     * A differential equation in [y]([x]): its [general] solution, the [particular] one where initial values were given, and
     * y′ = [slope] of a first-order one (for its slope field).
     */
    data class OdeSolved(val y: String, val x: String, val general: Ode.Solution, val particular: Ode.Solution?, val slope: Expr?) : Outcome()

    /** A Fourier series of a function of [x]. */
    data class FourierSeries(val x: String, val series: Transforms.Fourier) : Outcome()

    /** A numerical method's table ([Numerics]). */
    data class NumericTable(val table: Numerics.Table) : Outcome()

    /** A complex number's polar form. */
    data class PolarForm(val z: Expr, val polar: ComplexAnalysis.Polar) : Outcome()

    /** A function's poles in [z] with their orders and residues. */
    data class Residues(val z: String, val poles: List<ComplexAnalysis.Pole>) : Outcome()

    /** A linear system with a parameter, case by case ([LinAlg.cases]). */
    data class SystemCases(val cases: LinAlg.Cases) : Outcome()

    /** z = [function] of [x] and [y], drawn as a surface. */
    data class Surface(val function: Expr, val x: String, val y: String) : Outcome()

    data class Graph(val variable: String, val functions: List<Expr>, val implicit: Expr? = null, val parameters: List<String> = emptyList(),
        val region: Rel? = null,
        /** A curve (x(t), y(t)) in [variable] (x = cos t, y = sin t; a polar r(θ) as (r cos θ, r sin θ)). */
        val curve: Pair<Expr, Expr>? = null) : Outcome()

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
    val degrees: Boolean = false,
    /** How the formula was read where it could be read two ways. */
    val assumptions: Assumptions = Assumptions(),
    /** What the formula holds that could be read two ways: each a chip ("Assuming …") that reads it the other way. */
    val ambiguities: Set<Ambiguity> = emptySet()) : Style {

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

    /** An expression's result as it is shown: a series in rising powers with its O(…), an antiderivative with + C. */
    fun latexResult(o: Outcome.Expression): String = Format.result(o, this, latex = true)

    fun textResult(o: Outcome.Expression): String = Format.result(o, this, latex = false)

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
     * [assumptions] how what can be read two ways is read (the result says what it met: [Calculation.ambiguities]).
     */
    fun calculate(latex: String, choice: Choice? = null, millis: Long = LIMIT_MS, fractions: Boolean = false,
        assumptions: Assumptions = Assumptions()): Calculation {
        val tokens = TOKEN.findAll(latex).map { it.value }.toList()
        val flags = Flags(assumptions)
        var choices = emptyList<Choice>()
        var shown: Choice? = null
        val outcome = try {
            val statements = Parser(tokens, flags).statements()
            if (fractions) flags.fractions = true
            val others = if (flags.plusMinus) Parser(tokens, Flags(assumptions).also { it.decimals = flags.decimals }, pmSign = -1).statements() else null
            val style = Style.of(flags.decimalComma, flags.decimals, flags.fractions)
            Budget.within(millis) {
                val plan = odePlan(statements, flags.odeVariable) ?: plan(statements, others, style, flags.degrees)
                choices = plan.choices
                // A series asked for at a point or to a power of its own: the plan's series in that letter, so taken.
                val chosen = choice?.takeIf { it in plan.choices }
                    ?: choice?.takeIf { it.mode == Mode.SERIES }?.let { want ->
                        plan.choices.firstOrNull { it.mode == Mode.SERIES && (want.letter == null || it.letter == want.letter) }
                            ?.let { it.copy(point = want.point ?: it.point, order = want.order ?: it.order) }
                    }
                    // A mode it does only when asked for (Newton's method, a Fourier series): the choice as asked.
                    ?: choice?.takeIf { it.mode in plan.accepts }
                    ?: plan.likeliest
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
        return Calculation(outcome, flags.decimalComma, flags.decimals, flags.fractions, choices, shown, flags.degrees, assumptions,
            flags.met.toSet())
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
    /** What a formula can be worked out as: its [choices] (chips), the [likeliest], and modes it does only when asked ([accepts]). */
    private class Plan(val choices: List<Choice>, val likeliest: Choice, val accepts: Set<Mode> = emptySet(), val outcome: (Choice) -> Outcome)

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

        // A matrix or a vector, worked out where it was written (a product, a power, a transpose).
        val lastSide = sides.last()
        if (!equation && lastSide is Expr.Mat) {
            val text = s.texts.lastOrNull { it.isNotBlank() }.orEmpty()
            // Written as it is: one matrix and nothing after its \end (no power, no transpose).
            val bare = text.replace("\\left", "").replace("\\right", "").replace(" ", "")
            val literal = (text.split("\\begin").size == 2 && text.trim().startsWith("\\begin") &&
                Regex("""\\end\{[a-zA-Z]+\}\s*$""").containsMatchIn(text)) ||
                // [[1, 2], [3, 4]] as the keys write it: one bracket of brackets, nothing after it.
                (bare.startsWith("[[") && closingAt(bare, 0) == bare.length - 1)
            return matrixPlan(lastSide, literal)
        }

        if (equation && relations.any { it.isInequality || it == Rel.NE }) return inequalityPlan(sides, relations, letters)

        if (letters.isEmpty()) {
            val steps = sides.zipWithNext { a, b -> same(a, b) }
            if (equation) return Plan(listOf(Choice(Mode.COMPARE)), Choice(Mode.COMPARE)) { Outcome.Checked(steps.all { it }) }
            val v = sides.last()
            val alternatives = others?.let { o -> o[0].sides.filterNotNull().lastOrNull()?.let { Calculus.evaluate(it) } }
            val whole = integer(v)?.takeIf { it.abs() >= TWO && it.abs() <= Factor.MOST_PRIME }
            val complexNumber = v.hasI() && Complex.parts(v)?.second != null
            // A definite integral with numbers for bounds: the trapezoidal and Simpson's rules beside its value.
            val integral = (written.last() as? Expr.Op)?.takeIf { it.kind == OpKind.INTEGRAL && it.a != null && it.b != null }
                ?.takeIf { Numeric.value(Calculus.evaluate(it.a!!))?.isFinite() == true && Numeric.value(Calculus.evaluate(it.b!!))?.isFinite() == true }
            val choices = listOfNotNull(Choice(Mode.CALCULATE), whole?.takeIf { integral == null }?.let { Choice(Mode.FACTOR) },
                if (complexNumber) Choice(Mode.POLAR) else null, integral?.let { Choice(Mode.TRAPEZOID) }, integral?.let { Choice(Mode.SIMPSON) })
            return Plan(choices, Choice(Mode.CALCULATE)) { c ->
                if (c.mode == Mode.FACTOR && whole != null) Outcome.Primes(whole, Factor.primes(whole.abs())!!)
                else if (c.mode == Mode.POLAR) Outcome.PolarForm(v, ComplexAnalysis.polar(v) ?: throw CalcException(Failure.UNSOLVED))
                else if (integral != null && (c.mode == Mode.TRAPEZOID || c.mode == Mode.SIMPSON)) {
                    val a = Numeric.value(Calculus.evaluate(integral.a!!))!!
                    val b = Numeric.value(Calculus.evaluate(integral.b!!))!!
                    Outcome.NumericTable(Numerics.quadrature(Calculus.evaluate(integral.body), integral.variable, a, b, c.mode == Mode.SIMPSON,
                        exact = if (v.hasDecimal()) null else Numeric.value(v)))
                }
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
                val op = written.last() as? Expr.Op
                val definite = op?.takeIf { it.kind == OpKind.INTEGRAL && it.a != null && it.b != null }
                    ?.takeIf { Numeric.value(Calculus.evaluate(it.a!!))?.isFinite() == true && Numeric.value(Calculus.evaluate(it.b!!))?.isFinite() == true }
                val choices = listOfNotNull(Choice(Mode.CALCULATE), definite?.let { Choice(Mode.TRAPEZOID) }, definite?.let { Choice(Mode.SIMPSON) })
                return Plan(choices, Choice(Mode.CALCULATE)) { c ->
                    if (definite != null && (c.mode == Mode.TRAPEZOID || c.mode == Mode.SIMPSON)) {
                        val a = Numeric.value(Calculus.evaluate(definite.a!!))!!
                        val b = Numeric.value(Calculus.evaluate(definite.b!!))!!
                        val exact = if (last.hasDecimal()) null else Numeric.value(last)
                        Outcome.NumericTable(Numerics.quadrature(Calculus.evaluate(definite.body), definite.variable, a, b, c.mode == Mode.SIMPSON, exact = exact))
                    } else {
                        val integral = written.last() is Expr.Op && (written.last() as Expr.Op).let { it.kind == OpKind.INTEGRAL && it.a == null }
                        Outcome.Expression(last, steps, plusC = integral)
                    }
                }
            }
            val simplified = safeSimplify(last)
            val expanded = try { Algebra.expand(last) } catch (e: CalcException) { last }
            val factorable = Algebra.factor(last) != null
            val choices = ArrayList<Choice>()
            choices += Choice(Mode.SIMPLIFY)
            if (expanded != simplified && expanded != last) choices += Choice(Mode.EXPAND)
            if (factorable && !last.isConstantInLetters()) choices += Choice(Mode.FACTOR)
            // A fraction of polynomials in one letter, taken apart where that is something else than its simplest form.
            val partial = if (letters.size == 1) {
                try { Calculus.partialFractions(last, x)?.takeIf { it != simplified && it != last } } catch (e: CalcException) { null }
            } else {
                null
            }
            if (partial != null) choices += Choice(Mode.PARTIAL_FRACTIONS)
            // A function that is no polynomial, in one letter: its series at 0, else at 1 (ln x), where it has one.
            val seriesAt = if (letters.size == 1 && !isPolynomialIn(last)) seriesPoint(last, x) else null
            if (seriesAt != null) choices += Choice(Mode.SERIES, variable, point = seriesAt)
            if (letters == listOf("t")) choices += Choice(Mode.LAPLACE)
            if (letters == listOf("s")) choices += Choice(Mode.INVERSE_LAPLACE)
            if (letters == listOf("z") && hasPoles(last)) choices += Choice(Mode.RESIDUES)
            // A function of the coordinates x, y (and z): its critical points, and of x and y its surface.
            val coordinates = letters.size in 2..3 && letters.all { it in COORDINATES }
            if (coordinates) {
                choices += Choice(Mode.EXTREMA)
                choices += Choice(Mode.GRADIENT)
                choices += Choice(Mode.LAPLACIAN)
            }
            if (letters.toSet() == setOf("x", "y")) choices += Choice(Mode.SURFACE)
            letters.forEach { choices += Choice(Mode.DERIVATIVE, it) }
            letters.forEach { choices += Choice(Mode.INTEGRAL, it) }
            // A graph in its main letter, the others as sliders (a x² + b x + c).
            if (letters.size in 1..4) choices += Choice(Mode.GRAPH, variable)
            // A polynomial already written as Simplify would write it is factored first.
            val asWritten = written.size == 1 && plain(s.texts[s.sides.indexOfFirst { it != null }]) == plain(Format.expr(simplified, style, true))
            val polynomial = Algebra.Kernels().let { k -> Algebra.polynomial(last, k) != null && k.isEmpty }
            val likeliest = when {
                factorable && polynomial && asWritten -> Choice(Mode.FACTOR)
                else -> Choice(Mode.SIMPLIFY)
            }
            val onRequest = if (letters.size == 1) setOf(Mode.FOURIER, Mode.NEWTON, Mode.BISECTION) else emptySet()
            return Plan(choices, likeliest, onRequest) { c ->
                when (c.mode) {
                    Mode.LAPLACE -> Outcome.Expression(Transforms.laplace(last, x, Expr.Sym("s")) ?: throw CalcException(Failure.UNSOLVED))
                    Mode.INVERSE_LAPLACE -> Outcome.Expression(Transforms.inverseLaplace(last, x, Expr.Sym("t")) ?: throw CalcException(Failure.UNSOLVED))
                    Mode.RESIDUES -> Outcome.Residues(variable, ComplexAnalysis.residues(last, x) ?: throw CalcException(Failure.UNSOLVED))
                    Mode.FOURIER -> {
                        val half = c.point?.let { pointOf(it) } ?: Expr.PI
                        Outcome.FourierSeries(variable, Transforms.fourier(last, x, half) ?: throw CalcException(Failure.UNSOLVED))
                    }
                    Mode.NEWTON, Mode.BISECTION -> numericRoot(last, x, c)
                    Mode.SIMPLIFY -> {
                        val excluded = ArrayList<Expr>()
                        val usual = Algebra.simplify(last, excluded)
                        // A trigonometric form may come shorter through its formulas (sin 2x / sin x is 2 cos x).
                        val r = TrigForms.best(last, usual) { Algebra.simplify(it) }
                        Outcome.Expression(r, steps, excluded = if (r == usual) excludedValues(excluded) else emptyList())
                    }
                    Mode.EXPAND -> Outcome.Expression(expanded, steps)
                    Mode.FACTOR -> factored(last)
                    Mode.DERIVATIVE -> Outcome.Expression(Calculus.derivative(last, Expr.Sym(c.letter!!)))
                    Mode.INTEGRAL -> Outcome.Expression(Calculus.antiderivative(last, Expr.Sym(c.letter!!))
                        ?: throw CalcException(Failure.NO_ANTIDERIVATIVE), plusC = true)
                    Mode.GRAPH -> Outcome.Graph(variable, listOf(last), parameters = letters - variable)
                    Mode.PARTIAL_FRACTIONS -> Outcome.Expression(partial ?: throw CalcException(Failure.UNREADABLE), steps)
                    Mode.SERIES -> series(last, Expr.Sym(c.letter ?: variable), c.point ?: seriesAt ?: "0", c.order)
                    Mode.EXTREMA -> critical(last, letters)
                    Mode.GRADIENT -> Outcome.Expression(LinAlg.gradient(last, letters))
                    Mode.LAPLACIAN -> Outcome.Expression(LinAlg.laplacian(last, letters))
                    Mode.SURFACE -> Outcome.Surface(last, "x", "y")
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
        // r = f(θ): a polar curve, drawn as (r cos θ, r sin θ).
        val angle = letters.firstOrNull { it in POLAR_ANGLES }
        if (angle != null && letters.toSet() == setOf("r", angle) && (lhs == Expr.Sym("r") && !rhs.has(Expr.Sym("r")) || rhs == Expr.Sym("r") && !lhs.has(Expr.Sym("r")))) {
            val r = if (lhs == Expr.Sym("r")) rhs else lhs
            val th = Expr.Sym(angle)
            val graph = Choice(Mode.GRAPH, angle)
            return Plan(listOf(graph), graph) {
                Outcome.Graph(angle, emptyList(), curve = mul(r, fn(Func.COS, th)) to mul(r, fn(Func.SIN, th)))
            }
        }
        // The letters beside x and y are sliders: y = ax² + bx + c, a·x + b = 0.
        val parameters = letters - setOf("x", "y")
        val inXY = letters.containsAll(listOf("x", "y"))
        val graphable = letters.size == 1 || ((inXY || "x" in letters) && parameters.size <= 3)
        if (graphable) choices += Choice(Mode.GRAPH, if ("x" in letters) "x" else variable)
        // y = f(x) is a graph; an equation whose letter drops out, or a learner's answer to calculus, is checked; else solved.
        val explicit = inXY && (lhs == Expr.Sym("y") && !rhs.has(Expr.Sym("y")) || rhs == Expr.Sym("y") && !lhs.has(Expr.Sym("y")))
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
        val onRequest = if (letters.size == 1) setOf(Mode.NEWTON, Mode.BISECTION) else emptySet()
        return Plan(choices, likeliest, onRequest) { c ->
            when (c.mode) {
                Mode.NEWTON, Mode.BISECTION -> numericRoot(difference, Expr.Sym(variable), c)
                Mode.COMPARE -> compareOutcome()
                Mode.GRAPH -> when {
                    explicit -> Outcome.Graph("x", listOf(if (lhs == Expr.Sym("y")) rhs else lhs), parameters = parameters)
                    letters.size == 1 -> Outcome.Graph(variable, listOf(lhs, rhs))
                    inXY -> Outcome.Graph("x", emptyList(), implicit = difference, parameters = parameters)
                    else -> Outcome.Graph("x", listOf(lhs, rhs), parameters = letters - "x")
                }
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

    /** Whether e has a negative power of something with a letter in it: a denominator, with poles. */
    private fun hasPoles(e: Expr): Boolean = when (e) {
        is Expr.Pow -> e.base.symbols().isNotEmpty() && (rational(e.exp)?.signum ?: 0) < 0
        is Expr.Mul -> e.factors.any { hasPoles(it) }
        is Expr.Add -> e.terms.any { hasPoles(it) }
        else -> false
    }

    /**
     * Newton's method from the asked x₀ (1 where none is asked), or bisection on the asked [a, b] (else the first whole-number
     * interval from −10 to 10 where f changes sign).
     */
    private fun numericRoot(f: Expr, x: Expr.Sym, c: Choice): Outcome {
        val points = c.point?.split(',')?.mapNotNull { pointOf(it.trim())?.let { p -> Numeric.value(p) } }.orEmpty()
        if (c.mode == Mode.NEWTON) return Outcome.NumericTable(Numerics.newton(f, x, points.firstOrNull() ?: 1.0))
        val (a, b) = if (points.size == 2) points[0] to points[1] else {
            (-10 until 10).map { it.toDouble() to it + 1.0 }.firstOrNull { (p, q) ->
                Numeric.eval(f, mapOf(x.name to p)) * Numeric.eval(f, mapOf(x.name to q)) <= 0
            } ?: throw CalcException(Failure.UNSOLVED)
        }
        return Outcome.NumericTable(Numerics.bisection(f, x, a, b) ?: throw CalcException(Failure.UNSOLVED))
    }

    /** The letters a polar angle is written with. */
    private val POLAR_ANGLES = setOf("\\theta", "\\varphi", "\\phi", "θ", "φ")

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

    /** The letters of a function of several variables whose critical points and surface are offered. */
    private val COORDINATES = setOf("x", "y", "z")

    /** Where the bracket opened at [at] closes in [s], or −1. */
    private fun closingAt(s: String, at: Int): Int {
        var depth = 0
        for (k in at until s.length) {
            if (s[k] == '[' || s[k] == '(' || s[k] == '{') depth++
            if (s[k] == ']' || s[k] == ')' || s[k] == '}') { depth--; if (depth == 0) return k }
        }
        return -1
    }

    /**
     * A matrix: for a square one its determinant (the likeliest where it was written as it is), inverse and eigenvalues; for any
     * its row echelon form and its transpose; a matrix worked out from others (A·B, A²) or a vector is shown as it came out.
     */
    private fun matrixPlan(m: Expr.Mat, literal: Boolean): Plan {
        val square = m.size == m.columns
        val vector = LinAlg.isVector(m)
        val choices = ArrayList<Choice>()
        if (!literal || vector) choices += Choice(Mode.CALCULATE)
        if (square) {
            choices += Choice(Mode.DETERMINANT)
            choices += Choice(Mode.INVERSE)
            choices += Choice(Mode.EIGEN)
        }
        if (!vector) choices += Choice(Mode.ROW_REDUCE)
        if (!vector && m.size >= m.columns && m.size <= 6) choices += Choice(Mode.DECOMPOSE)
        if (square && m.size <= 4) choices += Choice(Mode.MATRIX_EXP)
        // A vector field: as many entries as coordinates, in x, y (and z).
        val coordinates = listOf("x", "y", "z").take(m.size)
        val field = vector && m.size in 2..3 && m.symbols().isNotEmpty() && m.symbols().all { it in coordinates }
        if (field) {
            choices += Choice(Mode.DIVERGENCE)
            choices += Choice(Mode.CURL)
            choices += Choice(Mode.JACOBIAN)
        }
        choices += Choice(Mode.TRANSPOSE)
        val likeliest = when {
            !literal || vector -> Choice(Mode.CALCULATE)
            square -> Choice(Mode.DETERMINANT)
            else -> Choice(Mode.ROW_REDUCE)
        }
        return Plan(choices, likeliest) { c ->
            when (c.mode) {
                Mode.CALCULATE -> Outcome.Expression(m)
                Mode.DETERMINANT -> LinAlg.det(m).let { d -> if (d.symbols().isEmpty()) value(d, emptyList(), emptyList()) else Outcome.Expression(d) }
                Mode.INVERSE -> Outcome.Expression(LinAlg.inverse(m))
                Mode.TRANSPOSE -> Outcome.Expression(LinAlg.transpose(m))
                Mode.ROW_REDUCE -> LinAlg.echelon(m).let { e -> Outcome.RowReduced(e.form, e.rank, e.ops, LinAlg.nullSpace(m), LinAlg.columnSpace(m)) }
                Mode.DECOMPOSE -> {
                    val d = Outcome.Decompositions(LinAlg.lu(m), LinAlg.qr(m), LinAlg.cholesky(m))
                    if (d.lu == null && d.qr == null && d.cholesky == null) throw CalcException(Failure.UNSOLVED)
                    d
                }
                Mode.MATRIX_EXP -> Outcome.Expression(LinAlg.exp(m) ?: throw CalcException(Failure.UNSOLVED))
                Mode.DIVERGENCE -> Outcome.Expression(LinAlg.divergence(m, coordinates))
                Mode.CURL -> Outcome.Expression(LinAlg.curl(m, coordinates))
                Mode.JACOBIAN -> Outcome.Expression(LinAlg.jacobian(m, coordinates))
                Mode.EIGEN -> {
                    val eigen = LinAlg.eigen(m) ?: throw CalcException(Failure.UNSOLVED)
                    Outcome.Eigenvalues(LinAlg.characteristic(m), eigen, LinAlg.diagonalization(m, eigen))
                }
                else -> throw CalcException(Failure.UNREADABLE)
            }
        }
    }

    /**
     * The critical points of [f] in [names]: the gradient's zeros by the system solver (real ones, each put back into every
     * component), each classified by the leading minors of the Hessian there; a line or plane of them is not said.
     */
    private fun critical(f: Expr, names: List<String>): Outcome {
        val xs = names.map { Expr.Sym(it) }
        val gradient = xs.map { Calculus.tidy(Calculus.derivative(f, it)) }
        val hessian = xs.map { a -> xs.map { b -> Calculus.tidy(Calculus.derivative(Calculus.derivative(f, a), b)) } }
        // Factors that are never 0 (e^u) taken out, so the solver sees the polynomial part: xy·e^(−x²−y²).
        val equations = gradient.filter { !isZero(it) }.map { withoutExponentials(it) }
        if (equations.isEmpty()) throw CalcException(Failure.UNSOLVED)
        val solutions = Solve.system(equations, names) ?: throw CalcException(Failure.UNSOLVED)
        // Each solution a point: a number for every letter (a solution still in letters is a whole line of them).
        if (solutions.any { s -> names.any { n -> s[n]?.symbols()?.isNotEmpty() != false } }) throw CalcException(Failure.UNSOLVED)
        val points = ArrayList<CriticalPoint>()
        for (s in solutions) {
            Budget.check()
            if (s.values.any { it.hasI() || it.hasInfinity() }) continue
            fun at(e: Expr): Expr = Calculus.tidy(names.fold(e) { acc, n -> acc.substitute(Expr.Sym(n), s.getValue(n)) })
            // The gradient there is 0, exactly or in ball arithmetic.
            if (!gradient.all { g -> at(g).let { v -> isZero(v) || Precise.nearZero(v, complex = false) == true } }) continue
            val h = hessian.map { row -> row.map { at(it) } }
            points += CriticalPoint(names.associateWith { s.getValue(it) }, kindOf(h), at(f))
        }
        return Outcome.Critical(names, gradient, hessian, points)
    }

    /** [g] divided by the powers of e that every one of its terms has: those are never 0. */
    private fun withoutExponentials(g: Expr): Expr {
        val terms = if (g is Expr.Add) g.terms else listOf(g)
        val common = terms.map { t -> (if (t is Expr.Mul) t.factors else listOf(t)).filter { it is Expr.Pow && it.base == Expr.E }.toSet() }
            .reduce { p, q -> p intersect q }
        if (common.isEmpty()) return g
        return Calculus.tidy(Algebra.expand(mul(g, pow(mul(common.toList()), Expr.MINUS_ONE))))
    }

    /** Positive definite: a minimum; negative definite: a maximum; neither and invertible: a saddle; singular: undecided. */
    private fun kindOf(h: List<List<Expr>>): CriticalKind {
        val n = h.size
        fun minor(k: Int): Expr = Calculus.tidy(when (k) {
            1 -> h[0][0]
            2 -> h[0][0] * h[1][1] - h[0][1] * h[1][0]
            else -> h[0][0] * (h[1][1] * h[2][2] - h[1][2] * h[2][1]) - h[0][1] * (h[1][0] * h[2][2] - h[1][2] * h[2][0]) +
                h[0][2] * (h[1][0] * h[2][1] - h[1][1] * h[2][0])
        })
        fun sign(e: Expr): Int? {
            if (isZero(e)) return 0
            val v = Numeric.value(e) ?: return null
            if (abs(v) < 1e-12 && Precise.nearZero(e, complex = false) == true) return 0
            return if (v > 0) 1 else -1
        }
        val signs = (1..n).map { sign(minor(it)) ?: return CriticalKind.UNDECIDED }
        return when {
            signs.last() == 0 -> CriticalKind.UNDECIDED
            signs.all { it > 0 } -> CriticalKind.MINIMUM
            signs.withIndex().all { (k, s) -> if (k % 2 == 0) s < 0 else s > 0 } -> CriticalKind.MAXIMUM
            else -> CriticalKind.SADDLE
        }
    }

    private fun isPolynomialIn(e: Expr): Boolean = Algebra.Kernels().let { k -> Algebra.polynomial(e, k) != null && k.isEmpty }

    /** Where a series is offered: at 0, else at 1 (ln x, √x), as LaTeX; null where it has neither. */
    private fun seriesPoint(e: Expr, x: Expr.Sym): String? = try {
        when {
            Series.taylor(e, x, Expr.ZERO, 4) != null -> "0"
            Series.taylor(e, x, Expr.ONE, 4) != null -> "1"
            else -> null
        }
    } catch (z: CalcException) {
        if (z.failure == Failure.TOO_LONG) throw z
        null
    }

    /**
     * The series of [e] at [point] up to the power [order] (6 where none is asked for), in rising powers with its remainder;
     * shown only where it matches e near the point from both sides, the difference shrinking as that power says.
     */
    private fun series(e: Expr, x: Expr.Sym, point: String, order: Int?): Outcome {
        val a = pointOf(point) ?: throw CalcException(Failure.UNREADABLE)
        val n = (order ?: 6).coerceIn(1, 24)
        val terms = Series.taylorTerms(e, x, a, n) ?: throw CalcException(Failure.UNSOLVED)
        val h = if (isZero(a)) x else add(x, neg(a))
        val t = if (terms.isEmpty()) Expr.ZERO else add(terms.map { (c, j) -> mul(c, pow(h, num(j.toLong()))) })
        if (!Series.matches(e, t, x, a, n)) throw CalcException(Failure.UNSOLVED)
        return Outcome.Expression(t, series = terms, seriesIn = h, remainder = pow(h, num((n + 1).toLong())))
    }

    /** A point written in LaTeX ("0", "1", "\\pi"), read as a number. */
    private fun pointOf(latex: String): Expr? = try {
        val tokens = TOKEN.findAll(latex).map { it.value }.toList()
        val st = Parser(tokens, Flags(Assumptions())).statements()
        st.singleOrNull()?.sides?.filterNotNull()?.singleOrNull()?.let { Calculus.evaluate(it) }?.takeIf { it.symbols().isEmpty() && !it.hasInfinity() }
    } catch (z: CalcException) {
        null
    }

    /** An exact form worth showing: roots, π, e, logarithms of numbers; not sin(47°), whose decimal says more. */
    internal fun niceExact(e: Expr): Boolean {
        // Long enough for Cardano's two cube roots, not for a formula nobody reads.
        if (e is Expr.Real || e.size() > 36) return false
        fun ok(t: Expr): Boolean = when (t) {
            is Expr.Num, is Expr.Const -> true
            is Expr.Real -> false
            is Expr.Add -> t.terms.all(::ok)
            is Expr.Mul -> t.factors.all(::ok)
            is Expr.Pow -> ok(t.base) && ok(t.exp)
            is Expr.Fn -> ((t.f == Func.LN || t.f == Func.LOG || t.f == Func.ABS) && t.args.all(::ok)) ||
                // 2cos(2π/9): a cubic's roots where all three are real, which radicals cannot write; sin(47°) stays a decimal.
                ((t.f == Func.COS || t.f == Func.SIN) && t.args.size == 1 && smallPiMultiple(t.args[0]))
            else -> false
        }
        return ok(e)
    }

    /** A rational multiple of π with a small denominator (2π/9, π/7), not a degree's 47π/180. */
    private fun smallPiMultiple(a: Expr): Boolean {
        if (a !is Expr.Mul || a.factors.size != 2 || a.factors.none { it == Expr.PI }) return false
        val r = (a.factors.firstOrNull { it is Expr.Num } as? Expr.Num)?.value ?: return false
        return r.den <= BigInteger.valueOf(12)
    }

    private fun factored(e: Expr): Outcome {
        val (result, product) = Algebra.factor(e) ?: throw CalcException(Failure.NOT_POLYNOMIAL)
        // Further over the reals, and over the complex numbers where they are wanted.
        val (overReals, overComplex) = try {
            Algebra.factorFurther(e) ?: (null to null)
        } catch (x: CalcException) {
            null to null
        } catch (x: ArithmeticException) {
            null to null
        }
        return Outcome.Factored(result, complete = product?.complete ?: true, trivial = product?.trivial ?: (result == e),
            overReals = overReals, overComplex = overComplex?.takeIf { Domain.isComplex })
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
        // An inequality in x and y: the region where it holds (y > x², x² + y² ≤ 4), its other letters as sliders.
        if (letters.containsAll(listOf("x", "y")) && sides.size == 2 && relations[0] != Rel.NE && letters.size <= 5) {
            val graph = Choice(Mode.GRAPH, "x")
            return Plan(listOf(graph), graph) {
                Outcome.Graph("x", emptyList(), implicit = sides[0] - sides[1], parameters = letters - setOf("x", "y"), region = relations[0])
            }
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
        constrainedPlan(statements)?.let { return it }
        val equations = statements.map { s ->
            val sides = s.sides.filterNotNull()
            if (sides.size != 2 || s.relations.size != 1 || s.relations[0] != Rel.EQ) throw CalcException(Failure.UNREADABLE)
            Calculus.evaluate(sides[0]) - Calculus.evaluate(sides[1])
        }
        val letters = equations.flatMap { it.symbols() }.toSortedSet().toList()
        val choice = Choice(Mode.SOLVE)
        // x = f(t), y = g(t): a curve in the parameter t, drawn first; still solvable.
        val curve = parametric(statements)
        if (curve != null) {
            val graph = Choice(Mode.GRAPH, curve.first)
            return Plan(listOf(graph, choice), graph) { c ->
                if (c.mode == Mode.GRAPH) return@Plan Outcome.Graph(curve.first, emptyList(), curve = curve.second)
                Outcome.SystemSolved(letters, Solve.system(equations, letters) ?: throw CalcException(Failure.UNSOLVED))
            }
        }
        // Unknowns x, y, z (x₁, x₂, …) and one letter beside them: a system with a parameter, case by case.
        val unknowns = letters.filter { it in UNKNOWNS || it.startsWith("x_") }
        val parameters = letters - unknowns.toSet()
        if (unknowns.size == statements.size && parameters.size == 1) {
            val cases = try { LinAlg.cases(equations, unknowns, parameters[0]) } catch (e: CalcException) {
                if (e.failure == Failure.TOO_LONG) throw e
                null
            }
            if (cases != null) {
                val byCase = Choice(Mode.CASES, parameters[0])
                return Plan(listOf(byCase, choice), byCase) { c ->
                    if (c.mode == Mode.CASES) Outcome.SystemCases(cases)
                    else Outcome.SystemSolved(letters, Solve.system(equations, letters) ?: throw CalcException(Failure.UNSOLVED))
                }
            }
        }
        // More linear equations than unknowns and no solution: the least squares.
        if (statements.size > letters.size && letters.isNotEmpty()) {
            val squares = leastSquares(equations, letters)
            if (squares != null) {
                val choice2 = Choice(Mode.LEAST_SQUARES)
                return Plan(listOf(choice2, choice), choice2) { c ->
                    if (c.mode == Mode.LEAST_SQUARES) squares
                    else Outcome.SystemSolved(letters, Solve.system(equations, letters) ?: emptyList())
                }
            }
        }
        return Plan(listOf(choice), choice) {
            val solutions = Solve.system(equations, letters) ?: throw CalcException(Failure.UNSOLVED)
            Outcome.SystemSolved(letters, solutions)
        }
    }

    /**
     * One line a function and the others equations in the same coordinates (x·y with x + y = 10): the extremum under those
     * constraints, by Lagrange's multipliers.
     */
    /**
     * A differential equation in y (or u), y′ … written in it, and lines of initial values y(0) = 1, y′(0) = 0 beside it: its
     * solution in x (the variable of a written dy/dt, else t where the formula has t and no x).
     */
    private fun odePlan(statements: List<Statement>, written: String?): Plan? {
        val unknown = Parser.ODE_UNKNOWNS.firstOrNull { name -> statements.any { s -> s.sides.filterNotNull().any { Ode.order(it, name) > 0 } } }
            ?: return null
        val main = statements.firstOrNull { s -> s.sides.filterNotNull().any { Ode.order(it, unknown) > 0 } && !isCondition(s, unknown) } ?: return null
        val sides = main.sides.filterNotNull()
        if (sides.size != 2 || main.relations != listOf(Rel.EQ)) return null
        val equation = Calculus.evaluate(sides[0]) - Calculus.evaluate(sides[1])
        val letters = equation.symbols()
        val x = written ?: if ("t" in letters && "x" !in letters) "t" else "x"
        val conditions = ArrayList<Ode.Condition>()
        for (s in statements) {
            if (s === main) continue
            val condition = conditionOf(s, unknown) ?: return null
            conditions += condition
        }
        val y1 = Ode.marker(unknown, 1)
        val slope = if (Ode.order(equation, unknown) == 1) Solve.equation(equation, Expr.ZERO, y1).values.singleOrNull()?.let { Calculus.tidy(it) } else null
        val choice = Choice(Mode.ODE)
        val f = slope?.takeIf { !it.has(y1) }
        val start = conditions.singleOrNull()?.takeIf { it.k == 0 }
        val numeric = f != null && start != null && Numeric.value(start.at) != null && Numeric.value(start.value) != null
        val choices = listOfNotNull(choice, if (numeric) Choice(Mode.RUNGE_KUTTA) else null, if (numeric) Choice(Mode.EULER) else null)
        return Plan(choices, choice) { c ->
            if (numeric && (c.mode == Mode.EULER || c.mode == Mode.RUNGE_KUTTA)) {
                // From the initial value, ten steps of h = 0.1; the exact solution beside where it is found.
                val exact = try { Ode.solve(equation, unknown, x)?.let { Ode.withConditions(equation, it, conditions, unknown, x) }?.explicit } catch (e: CalcException) {
                    if (e.failure == Failure.TOO_LONG) throw e
                    null
                }
                val h = c.order?.let { 1.0 / it } ?: 0.1
                return@Plan Outcome.NumericTable(Numerics.ode(f!!, x, unknown, Numeric.value(start!!.at)!!, Numeric.value(start.value)!!, h, 10,
                    c.mode == Mode.RUNGE_KUTTA, exact))
            }
            val general = Ode.solve(equation, unknown, x) ?: throw CalcException(Failure.UNSOLVED)
            val particular = if (conditions.isEmpty()) null else Ode.withConditions(equation, general, conditions, unknown, x) ?: throw CalcException(Failure.UNSOLVED)
            Outcome.OdeSolved(unknown, x, general, particular, f)
        }
    }

    private val CONDITION = Regex("""^\s*([a-z])('*)\s*\((.+)\)\s*$""")

    private fun isCondition(s: Statement, unknown: String): Boolean = conditionOf(s, unknown) != null

    /** y(0) = 1, y'(0) = 0 as the line wrote it: the derivative's order, the point and the value. */
    private fun conditionOf(s: Statement, unknown: String): Ode.Condition? {
        if (s.relations != listOf(Rel.EQ) || s.texts.size < 2) return null
        val m = CONDITION.find(s.texts[0].replace("\\prime", "'").replace("^{'}", "'").replace("\\left", "").replace("\\right", "")) ?: return null
        if (m.groupValues[1] != unknown) return null
        val at = pointOf(m.groupValues[3]) ?: return null
        val value = s.sides.getOrNull(1)?.let { Calculus.evaluate(it) }?.takeIf { it.symbols().isEmpty() } ?: return null
        return Ode.Condition(m.groupValues[2].length, at, value)
    }

    private fun constrainedPlan(statements: List<Statement>): Plan? {
        val expressions = statements.filter { s -> s.relations.isEmpty() && s.sides.filterNotNull().size == 1 }
        if (expressions.size != 1 || statements.size < 2) return null
        val f = Calculus.evaluate(expressions[0].sides.filterNotNull()[0])
        val constraints = (statements - expressions[0]).map { s ->
            val sides = s.sides.filterNotNull()
            if (sides.size != 2 || s.relations != listOf(Rel.EQ)) return null
            Calculus.evaluate(sides[0]) - Calculus.evaluate(sides[1])
        }
        val names = (f.symbols() + constraints.flatMap { it.symbols() }).toSortedSet().toList()
        if (names.isEmpty() || names.size > 3 || !names.all { it in COORDINATES }) return null
        val choice = Choice(Mode.EXTREMA)
        return Plan(listOf(choice), choice) {
            Outcome.Constrained(names, constraints, LinAlg.lagrange(f, constraints, names) ?: throw CalcException(Failure.UNSOLVED))
        }
    }

    /** A linear system with more equations than unknowns and no solution: its least-squares solution, or null. */
    private fun leastSquares(equations: List<Expr>, letters: List<String>): Outcome.LeastSquares? {
        val xs = letters.map { Expr.Sym(it) }
        val rows = ArrayList<List<Expr>>()
        val rhs = ArrayList<Expr>()
        for (e in equations) {
            val row = xs.map { x -> Calculus.tidy(Calculus.derivative(e, x)) }
            if (row.any { c -> c.symbols().isNotEmpty() }) return null
            rows += row
            rhs += Calculus.tidy(neg(xs.fold(e) { acc, x -> acc.substitute(x, Expr.ZERO) }))
        }
        if (rhs.any { it.symbols().isNotEmpty() }) return null
        val a = Expr.Mat(rows)
        val b = LinAlg.column(rhs)
        // Only where the system itself has no solution.
        val augmented = Expr.Mat(rows.mapIndexed { i, r -> r + rhs[i] })
        if (letters.size !in LinAlg.echelon(augmented).pivots) return null
        val x = LinAlg.leastSquares(a, b) ?: return null
        val r = LinAlg.plus(LinAlg.times(a, x), LinAlg.scale(b, Expr.MINUS_ONE)) as Expr.Mat
        return Outcome.LeastSquares(letters, letters.indices.associate { letters[it] to x.rows[it][0] }, LinAlg.norm(r))
    }

    /** The letters a system is solved for; the others in it are its parameters. */
    private val UNKNOWNS = setOf("x", "y", "z", "w")

    /** x = f(t) and y = g(t), both in one letter t: t and the curve, or null. */
    private fun parametric(statements: List<Statement>): Pair<String, Pair<Expr, Expr>>? {
        if (statements.size != 2) return null
        val sides = statements.map { s -> s.sides.filterNotNull().takeIf { it.size == 2 && s.relations == listOf(Rel.EQ) } ?: return null }
        fun of(name: String): Expr? = sides.firstOrNull { it[0] == Expr.Sym(name) }?.get(1) ?: sides.firstOrNull { it[1] == Expr.Sym(name) }?.get(0)
        val x = of("x") ?: return null
        val y = of("y") ?: return null
        val t = (x.symbols() + y.symbols()).singleOrNull() ?: return null
        if (t == "x" || t == "y") return null
        return t to (Calculus.evaluate(x) to Calculus.evaluate(y))
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

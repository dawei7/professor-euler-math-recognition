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
    /** Units that measure different things: metres in seconds. */
    UNITS,
    /** No triangle has these sides and angles. */
    NO_TRIANGLE,
    /**
     * A function given arguments it does not take: too many or too few, a number where its letter belongs, or commas that
     * could be read two ways (`stats(1,5, 2,5)`).
     */
    ARGUMENTS,
    /** A name before a bracket that is no function of the calculator: round(2.5), max(1, 2). Never read as its letters. */
    UNKNOWN_FUNCTION,
    /** A command's function (solve, det …) inside a formula, as 2·det(A): it takes a line of its own. */
    COMMAND_INSIDE,
    /** constant(…) of a name that is no constant of the calculator ([PhysicalConstants.NAMES]). */
    UNKNOWN_CONSTANT,
    /**
     * A measured constant (G, mₑ …) beside letters or in an equation: it is known only to its published digits, so it is worked
     * out only in a number, whose uncertainty follows from them; or a number whose uncertainty cannot be bounded that way.
     */
    MEASURED_CONSTANT,
    /** A function the domain colouring cannot evaluate at complex points (⌊z⌋, Γ, calculus not worked out). */
    NO_COMPLEX_VALUES,
    /**
     * A power series asked for at a singular point of a differential equation, where its leading coefficient is 0 or another
     * has no Taylor series (x²y″ + y = 0 at 0): a series there needs Frobenius' method, which the calculator does not have.
     */
    SINGULAR_POINT,
    /** A number without a primitive root: primroot(n) of an n that is not 2, 4, pᵏ or 2pᵏ (p an odd prime), as 8 or 15. */
    NO_PRIMITIVE_ROOT,
    /** No power of g is a modulo n: discretelog(a, g, n) has no solution, as 2ˣ ≡ 3 (mod 7). */
    NO_DISCRETE_LOG,
    /** Points of an interpolation or a spline with the same x: no function passes through both (or a point given twice). */
    SAME_X,

    /**
     * A boundary-value problem whose conditions do not fix one solution: a partial differential equation without the
     * values at both ends or its initial values (u(0, t) = 0, u(L, t) = 0, u(x, 0) = f(x)), or with one of them twice.
     */
    CONDITIONS,
    /** An optimization whose objective or constraints are not linear: maximize and minimize take linear ones only. */
    NOT_LINEAR,
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

    /**
     * The exact value; null for an approximation. A double is never taken for the fraction it lies near: sinh 10 is no
     * 75242407/6832 and tanh 20 no 1, though each double is within rounding of them.
     */
    val rational: Rational?
        get() = when (this) {
            is Exact -> r
            is Approx -> null
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

    /** A whole number's facts: prime or not, its factors and divisors, φ, σ, μ, other bases. */
    NUMBER_FACTS,

    /** A congruence or a system of them solved: x ≡ 2 (mod 3). */
    CONGRUENCE,

    /** An equation in two letters solved in whole numbers: 3x + 5y = 7, x² − 2y² = 1. */
    DIOPHANTINE,

    /** A linear recurrence solved in closed form: aₙ₊₁ = 2aₙ + 1, a₀ = 1. */
    RECURRENCE,

    /** The rule of a list of numbers, and its next terms. */
    SEQUENCE,

    /** A formula of logic or sets: its truth table and simplest forms. */
    LOGIC,

    /** A list of numbers' statistics: mean, median, deviations, quartiles. */
    STATISTICS,

    /** The least-squares line through points. */
    REGRESSION,

    /** A value in one unit written in another: 5 km in mi (the units in the choice's letter and point). */
    UNITS,

    /** A triangle from three of its sides and angles. */
    TRIANGLE,

    /** A conic from its equation: centre, axes, foci, eccentricity. */
    CONIC,

    /** The line through two points. */
    LINE,

    /** The plane through three points. */
    PLANE,

    /** A physical constant on its own, constant(speedoflight): its value, unit and uncertainty ([PhysicalConstants]). */
    CONSTANT,

    /** A complex function's domain colouring with its zeros and poles ([ComplexPlot]). */
    COMPLEX_PLOT,

    /** A Venn diagram of a formula of sets in one to three sets ([VennDiagram]). */
    VENN,
    /** A matrix's singular value decomposition A = U·Σ·Vᵀ. */
    SVD,

    /** A square matrix's Jordan normal form A = P·J·P⁻¹. */
    JORDAN,

    /** A linear differential equation's power series solution at an ordinary point ([Choice.point], [Choice.order]). */
    ODE_SERIES,

    /** The Dormand–Prince method with adaptive steps for y′ = f(x, y), to the end [Choice.point] (x₀ + 1 where none is asked). */
    DORMAND_PRINCE,

    /** The slope field of y′ = f(x, y). */
    SLOPE_FIELD,

    /** The phase portrait of x′ = f(x, y), y′ = g(x, y), with its equilibria. */
    PHASE_PORTRAIT,
    /** The polynomial through points, by Newton's divided differences and Lagrange's form. */
    INTERPOLATION,

    /** The natural cubic spline through points. */
    SPLINE,

    /** A second-order linear partial differential equation's type (elliptic, parabolic, hyperbolic) and canonical form. */
    PDE_TYPE,

    /** A partial differential equation with its boundary and initial values, solved by separation of variables as a Fourier series. */
    PDE,
    /** A matrix's rank, checked: a minor of that size is not 0 and the null space has the rest ([Applied]). */
    RANK,

    /** A square matrix's trace, the sum of its diagonal. */
    TRACE,

    /** A basis of a matrix's null space, each vector checked: A·v = 0. */
    NULL_SPACE,

    /** A basis of a matrix's column space: its columns at the pivots. */
    COLUMN_SPACE,

    /** A square matrix's characteristic polynomial det(λI − A), checked against its principal minors. */
    CHARACTERISTIC_POLYNOMIAL,

    /** A square matrix's adjugate, checked: A·adj(A) = det(A)·I. */
    ADJUGATE,

    /** The tangent line of y = f(x) at x = a ([Choice.letter] x, [Choice.point] a). */
    TANGENT_LINE,

    /** The normal line of y = f(x) at x = a. */
    NORMAL_LINE,

    /** The arc length of y = f(x) on [a, b] ([Choice.point] "a,b"). */
    ARC_LENGTH,

    /** The area between two curves (the formula's two lines) on [a, b], or between their intersections. */
    AREA_BETWEEN,

    /** The volume of the solid y = f(x), a ≤ x ≤ b, turned about the x-axis (discs). */
    VOLUME_X,

    /** The volume of the solid y = f(x), a ≤ x ≤ b, turned about the y-axis (shells). */
    VOLUME_Y,

    /** The mean value of f on [a, b]: 1/(b − a)·∫ₐᵇ f dx. */
    MEAN_VALUE,

    /** A linear objective maximized under linear constraints (the formula's first line, then the constraints). */
    MAXIMIZE,

    /** A linear objective minimized under linear constraints. */
    MINIMIZE,
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

    /** A whole number's facts ([Discrete.facts]). */
    data class NumberFacts(val facts: Discrete.Facts) : Outcome()

    /** Congruences solved: x ≡ r (mod m) for each residue ([Discrete.congruences]). */
    data class Congruence(val solution: Discrete.Congruences) : Outcome()

    /** An equation solved in whole numbers ([Discrete.integers]). */
    data class WholeSolutions(val solution: Discrete.Integers) : Outcome()

    /** A recurrence in closed form ([Discrete.recurrence]). */
    data class RecurrenceSolved(val recurrence: Discrete.Recurrence) : Outcome()

    /** A list's rule and its next terms ([Discrete.sequence]). */
    data class SequenceRule(val values: List<Expr>, val rule: Discrete.Rule) : Outcome()

    /** A formula of logic or sets: its table and simplest forms ([Logic.analyse]). */
    data class LogicTable(val analysis: Logic.Analysis) : Outcome()

    /** Sets written out, worked out: the elements of the result. */
    data class SetValue(val elements: List<Expr>) : Outcome()

    /** A data list's statistics ([Stats.summary]). */
    data class Statistics(val values: List<Expr>, val summary: Stats.Summary) : Outcome()

    /** The least-squares line y = a·x + b through the points, with Pearson's r ([Stats.regression]). */
    data class Regression(val xs: List<Expr>, val ys: List<Expr>, val line: Stats.Line) : Outcome()

    /** [value] in [from] is [result] in [to] ([Units.convert]). */
    data class Converted(val value: Expr, val from: String, val to: String, val result: Expr) : Outcome()

    /** The triangles with the given sides and angles: two in the ambiguous case ([Geometry.triangles]). */
    data class Triangles(val triangles: List<Geometry.Triangle>) : Outcome()

    /** A conic's centre, axes, foci and the rest ([Geometry.conic]). */
    data class ConicSection(val conic: Geometry.Conic) : Outcome()

    /** The line through two points ([Geometry.line]). */
    data class LineThrough(val line: Geometry.Line) : Outcome()

    /** The plane through three points ([Geometry.plane]). */
    data class PlaneThrough(val plane: Geometry.Plane) : Outcome()

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

    /** A physical constant ([PhysicalConstants.Entry]) in [unit] (its own, or the one asked for) and its [quantity] there. */
    data class PhysicalConstant(val constant: PhysicalConstants.Entry, val unit: String, val quantity: PhysicalConstants.Quantity) : Outcome()

    /**
     * A number worked out from physical constants ([constants], their names): its [quantity], exact where only exact constants
     * are in it, else the value with a bound of its standard uncertainty, both rounded as CODATA rounds (6.6743(15) × 10⁻¹¹).
     */
    data class PhysicalValue(val quantity: PhysicalConstants.Quantity, val constants: List<String>) : Outcome()

    /** A complex function's domain colouring: the function compiled, its square, its zeros and poles ([ComplexPlot.plot]). */
    data class DomainColoring(val plot: ComplexPlot.Plot) : Outcome()

    /** A Venn diagram: its sets and which of its regions belong to the formula ([VennDiagram.of]). */
    data class Venn(val diagram: VennDiagram.Diagram) : Outcome()
    /** A matrix's singular value decomposition A = U·Σ·Vᵀ, exact or in certified decimals ([MatrixForms.svd]). */
    data class SingularValues(val svd: MatrixForms.Svd) : Outcome()

    /** A square matrix's Jordan normal form A = P·J·P⁻¹ ([MatrixForms.jordan]). */
    data class JordanForm(val jordan: MatrixForms.Jordan) : Outcome()

    /** A system of linear differential equations x′ = A·x + b solved, with its initial values where given ([OdeSystems.solve]). */
    data class OdeSystemSolved(val system: OdeSystems.Solution) : Outcome()

    /** A linear differential equation's power series solution at an ordinary point ([SeriesOde.solve]). */
    data class SeriesSolved(val solution: SeriesOde.Solution) : Outcome()

    /** A boundary value problem: one solution, none, or infinitely many ([BoundaryValue.solve]). */
    data class BoundaryValueSolved(val problem: BoundaryValue.Problem) : Outcome()

    /** A slope field or a phase portrait, with its equilibria and their kinds ([Fields]). */
    data class DirectionField(val field: Fields.Field) : Outcome()
    /**
     * The polynomial through points ([Interpolation.interpolate]): multiplied out, in Newton's form and by Lagrange's basis
     * polynomials, with its table of divided differences.
     */
    data class Interpolated(val interpolant: Interpolation.Interpolant) : Outcome()

    /** The natural cubic spline through points ([Interpolation.spline]): a cubic on each interval between two of them. */
    data class SplineThrough(val spline: Interpolation.Spline) : Outcome()

    /** A partial differential equation's type, its discriminant b² − 4ac and, with constant coefficients, its canonical form ([Pde.classify]). */
    data class PdeType(val classification: Pde.Classification) : Outcome()

    /** A partial differential equation's problem solved by separation of variables, as a Fourier series ([Pde.separate]). */
    data class PdeSeries(val solution: Pde.Separated) : Outcome()
    /**
     * A basis of a matrix's [nullSpace] (vectors x with A·x = 0) or else its column space (the span of its columns): [vectors]
     * (empty for the space {0}), the [dimension] and the dimension of the [space] the vectors lie in (A's columns for the null
     * space, its rows for the column space). Shown as ker A = span{v₁, v₂} (or {0}) with dim ker A = 2, or col A = span{…}.
     */
    data class Basis(val vectors: List<Expr.Mat>, val nullSpace: Boolean, val dimension: Int, val space: Int) : Outcome()

    /**
     * The rank of a matrix with one letter [parameter] in it, case by case: [generic] for every real value but those in
     * [special] (each value with its rank there) and [undefined] (where an entry is not defined). Shown as "rank A = 2 for
     * k ≠ ±2; rank A = 1 for k = −2 and for k = 2".
     */
    data class RankCases(val parameter: String, val generic: Int, val special: List<Pair<Expr, Int>>, val undefined: List<Expr>) : Outcome()

    /**
     * The tangent (or, [normal], the normal) of y = [function]([x]) at x = [at]: through ([at], [value]), where f′([at]) is
     * [slope]; [line] its equation, "y" to m·x + c, or "x" to [at] where it is vertical (the normal where f′ = 0). Shown as
     * the equation, with the point and the slope, and drawn: the curve and the line.
     */
    data class TangentLine(val function: Expr, val x: String, val at: Expr, val value: Expr, val slope: Expr, val line: Pair<String, Expr>,
        val normal: Boolean) : Outcome()

    /** A linear program maximized or minimized ([LinearProgram]): its optimum, or that it has none, with the certificate. */
    data class Optimized(val program: LinearProgram.Solution) : Outcome()
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
        // A picture asked for by name, and physical constants, are worked out on their own.
        Additions.calculate(latex, choice, millis, fractions, assumptions)?.let { return it }
        val tokens = TOKEN.findAll(latex).map { it.value }.toList()
        val flags = Flags(assumptions)
        var choices = emptyList<Choice>()
        var shown: Choice? = null
        val outcome = try {
            // Logic and sets are read on their own; everything else is mathematics for the parser.
            val logic = Logic.read(tokens.map { if (it.length > 1 && it[0] == ',') "," else it })
            val statements = if (logic != null) emptyList() else Parser(tokens, flags).statements()
            if (fractions) flags.fractions = true
            val others = if (flags.plusMinus) Parser(tokens, Flags(assumptions).also { it.decimals = flags.decimals }, pmSign = -1).statements() else null
            val style = Style.of(flags.decimalComma, flags.decimals, flags.fractions)
            Budget.within(millis) {
                // The applied functions (rank, tangentline, maximize …), asked for by their call, are worked out on their own.
                if (logic == null) Applied.outcome(statements, choice)?.let { o -> choices = listOf(choice!!); shown = choice; return@within o }
                val plan = if (logic != null) logicPlan(logic)
                    else pdePlan(statements) ?: odePlan(statements, flags.odeVariable) ?: odeSystemPlan(statements, flags.odeVariable)
                        ?: plan(statements, others, style, flags.degrees, flags.moduli, flags.denominators)
                choices = plan.choices
                // A series asked for at a point or to a power of its own: the plan's series in that letter, so taken.
                val chosen = choice?.takeIf { it in plan.choices }
                    ?: choice?.takeIf { it.mode == Mode.SERIES }?.let { want ->
                        plan.choices.firstOrNull { it.mode == Mode.SERIES && (want.letter == null || it.letter == want.letter) }
                            ?.let { it.copy(point = want.point ?: it.point, order = want.order ?: it.order) }
                    }
                    // A mode asked for without its letter: the plan's choice of that mode (derivative(x² y) in x).
                    ?: choice?.takeIf { it.letter == null && it.point == null && it.order == null }?.let { want ->
                        plan.choices.firstOrNull { it.mode == want.mode }
                    }
                    // A mode it does only when asked for (Newton's method, a Fourier series): the choice as asked.
                    ?: choice?.takeIf { it.mode in plan.accepts }
                    ?: plan.likeliest
                // An operation asked for by name (a function's call, a choice kept with a calculation) is done or refused, never
                // another in its place: det of a 2 × 3 matrix is no row echelon form, derivative(5, x) no 5.
                if (choice != null && chosen.mode != choice.mode && choice.mode !in INTERCHANGEABLE) throw CalcException(Failure.ARGUMENTS)
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

    /**
     * The modes another may stand in for where the formula has nothing of their own to do: calculate(x + x) simplifies,
     * simplify(2 + 3) calculates, factor(x + 1) and expand(x + 1) are x + 1, solve(5) and plot(5) show the number.
     */
    private val INTERCHANGEABLE = setOf(Mode.CALCULATE, Mode.SIMPLIFY, Mode.EXPAND, Mode.FACTOR, Mode.PARTIAL_FRACTIONS, Mode.COMPARE,
        Mode.SOLVE, Mode.GRAPH,
        // A system with a solution is solved exactly: that is its least-squares solution, with residual 0.
        Mode.LEAST_SQUARES)

    /**
     * The letters a function of several variables is taken in (its gradient, Laplacian, Jacobian): the coordinates x, y, z it
     * has, the other letters standing as constants (a x + b y in x and y); without any of them every letter it has, in
     * alphabetical order (r cos t in r and t).
     */
    internal fun variablesOf(letters: Collection<String>): List<String> {
        val coordinates = COORDINATE_ORDER.filter { it in letters }
        return coordinates.ifEmpty { letters.sorted() }
    }

    private val COORDINATE_ORDER = listOf("x", "y", "z")

    /**
     * The tokens of LaTeX: a command, an escaped sign, or one sign; a comma with a space after it is a token of its own
     * (`", "`), a separator and never a decimal comma: log(8, 2) is no log of 8.2, (3, 4) no 3.4.
     */
    internal val TOKEN = Regex("""\\[a-zA-Z]+|\\.|,\s+|\S""")

    private val TWO = BigInteger.valueOf(2)

    /** The choices that fit a formula, the likeliest of them, and how each comes to its outcome. */
    /** What a formula can be worked out as: its [choices] (chips), the [likeliest], and modes it does only when asked ([accepts]). */
    private class Plan(val choices: List<Choice>, val likeliest: Choice, val accepts: Set<Mode> = emptySet(), val outcome: (Choice) -> Outcome)

    private fun plan(statements: List<Statement>, others: List<Statement>?, style: Style, degrees: Boolean, moduli: Map<Int, BigInteger>,
        denominators: List<Expr> = emptyList()): Plan {
        if (moduli.isNotEmpty()) return congruencePlan(statements, moduli)
        recurrencePlan(statements)?.let { return it }
        pointsPlan(statements)?.let { return it }
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
            // A complex number as a + b·i, where it is a quotient or a power of one (1/(1 + i) is 1/2 − i/2), checked equal.
            val v = sides.last().let { e ->
                if (e !is Expr.Mat && e.hasI() && Complex.parts(e) == null) {
                    Complex.normal(e)?.let { (a, b) -> Calculus.tidy(add(Calculus.tidy(a), mul(Calculus.tidy(b), Expr.I))) }
                        ?.takeIf { n -> LinAlg.isNull(Calculus.tidy(n - e)) } ?: e
                } else e
            }
            val alternatives = others?.let { o -> o[0].sides.filterNotNull().lastOrNull()?.let { Calculus.evaluate(it) } }
            val whole = integer(v)?.takeIf { it.abs() >= TWO && it.abs() < NumberTheory.MOST_FACTORED }
            val complexNumber = v.hasI() && Complex.parts(v)?.second != null
            // A definite integral with numbers for bounds: the trapezoidal and Simpson's rules beside its value.
            val integral = (written.last() as? Expr.Op)?.takeIf { it.kind == OpKind.INTEGRAL && it.a != null && it.b != null }
                ?.takeIf { Numeric.value(Calculus.evaluate(it.a!!))?.isFinite() == true && Numeric.value(Calculus.evaluate(it.b!!))?.isFinite() == true }
            val facts = integer(v)?.takeIf { it >= TWO && it < NumberTheory.MOST_FACTORED && integral == null }
            val choices = listOfNotNull(Choice(Mode.CALCULATE), whole?.takeIf { integral == null }?.let { Choice(Mode.FACTOR) },
                facts?.let { Choice(Mode.NUMBER_FACTS) },
                if (complexNumber) Choice(Mode.POLAR) else null, integral?.let { Choice(Mode.TRAPEZOID) }, integral?.let { Choice(Mode.SIMPSON) })
            // A whole number of 41 digits or more is too large to take apart: factor(n) and facts(n) say so.
            val unfactored = integer(v)?.let { it.abs() >= NumberTheory.MOST_FACTORED } == true
            // A number is a constant function where a function's calculus is asked for: derivative(5, x) is 0, integral(5, x) 5x,
            // laplace(1) 1/s; no function of t has a number for its transform (invlaplace(2) would be 2δ(t), no function).
            val asFunction = setOf(Mode.DERIVATIVE, Mode.INTEGRAL, Mode.LAPLACE, Mode.INVERSE_LAPLACE)
            return Plan(choices, Choice(Mode.CALCULATE), setOf(Mode.UNITS) + asFunction +
                (if (unfactored) setOf(Mode.FACTOR, Mode.NUMBER_FACTS) else emptySet())) { c ->
                if (unfactored && (c.mode == Mode.FACTOR || c.mode == Mode.NUMBER_FACTS)) throw CalcException(Failure.TOO_LARGE)
                if (c.mode in asFunction && (v is Expr.Mat || v.hasI() || v.hasInfinity())) throw CalcException(Failure.ARGUMENTS)
                if (c.mode == Mode.DERIVATIVE) Outcome.Expression(Expr.ZERO)
                else if (c.mode == Mode.INTEGRAL) Outcome.Expression(Calculus.tidy(mul(v, Expr.Sym(c.letter ?: "x"))), plusC = true)
                else if (c.mode == Mode.LAPLACE) Outcome.Expression(Transforms.laplace(v, Expr.Sym("t"), Expr.Sym("s")) ?: throw CalcException(Failure.UNSOLVED))
                else if (c.mode == Mode.INVERSE_LAPLACE) throw CalcException(Failure.UNSOLVED)
                else if (c.mode == Mode.UNITS) {
                    val from = c.letter ?: throw CalcException(Failure.UNREADABLE)
                    val to = c.point ?: throw CalcException(Failure.UNREADABLE)
                    Outcome.Converted(v, from, to, Units.convert(v, from, to) ?: throw CalcException(Failure.UNREADABLE))
                }
                else if (c.mode == Mode.NUMBER_FACTS && facts != null) Outcome.NumberFacts(Discrete.facts(facts) ?: throw CalcException(Failure.TOO_LARGE))
                else if (c.mode == Mode.FACTOR && whole != null) Outcome.Primes(whole, Factor.primes(whole.abs())
                    ?: NumberTheory.factor(whole.abs()) ?: throw CalcException(Failure.TOO_LARGE))
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
            if (letters == listOf("z")) choices += Choice(Mode.COMPLEX_PLOT, "z")
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
            // Asked for by name, in any letter it has (or none: derivative(c, x) is 0), with the others as constants: a transform
            // in t (s), a function's residues in z, its gradient and Laplacian in its coordinates ([variablesOf]).
            // A series asked for by name as well: its chip is looked for in 400 ms, which a busy device may not have (taylor(tan x)
            // was then refused as a call with arguments it does not take).
            val onRequest = (if (letters.size == 1) setOf(Mode.FOURIER, Mode.NEWTON, Mode.BISECTION, Mode.SERIES) else emptySet()) +
                setOf(Mode.DERIVATIVE, Mode.INTEGRAL, Mode.GRADIENT, Mode.LAPLACIAN) +
                (if ("t" in letters) setOf(Mode.LAPLACE) else emptySet()) + (if ("s" in letters) setOf(Mode.INVERSE_LAPLACE) else emptySet()) +
                (if (letters.size == 1 || "z" in letters) setOf(Mode.RESIDUES) else emptySet())
            val inTransform = Expr.Sym(if (letters == listOf("t") || letters == listOf("s")) variable else "t")
            val inLaplace = Expr.Sym(if (letters == listOf("s")) variable else "s")
            val inResidues = Expr.Sym(if ("z" in letters) "z" else variable)
            val fieldLetters = variablesOf(letters)
            return Plan(choices, likeliest, onRequest) { c ->
                when (c.mode) {
                    Mode.LAPLACE -> Outcome.Expression(Transforms.laplace(last, inTransform, Expr.Sym("s")) ?: throw CalcException(Failure.UNSOLVED))
                    Mode.INVERSE_LAPLACE -> Outcome.Expression(Transforms.inverseLaplace(last, inLaplace, Expr.Sym("t")) ?: throw CalcException(Failure.UNSOLVED))
                    Mode.RESIDUES -> Outcome.Residues(inResidues.name, ComplexAnalysis.residues(last, inResidues) ?: throw CalcException(Failure.UNSOLVED))
                    Mode.FOURIER -> {
                        // On [−L, L] for a number L; a period that is no number (a letter) is refused, never taken for π.
                        val half = c.point?.let { pointOf(it) ?: throw CalcException(Failure.ARGUMENTS) } ?: Expr.PI
                        Outcome.FourierSeries(variable, Transforms.fourier(last, x, half) ?: throw CalcException(Failure.UNSOLVED))
                    }
                    Mode.NEWTON, Mode.BISECTION -> numericRoot(last, x, c)
                    Mode.SIMPLIFY -> {
                        val excluded = ArrayList<Expr>()
                        val usual = Algebra.simplify(last, excluded)
                        // A trigonometric form may come shorter through its formulas (sin 2x / sin x is 2 cos x).
                        val r = TrigForms.best(last, usual) { Algebra.simplify(it) }
                        // The values the formula as written excludes that its simplest form is defined at: a ≠ 0, b ≠ 0 for
                        // (a²b)³/(ab²) = a⁵b, whether simplify cancelled them or the reading did.
                        val cancelled = (if (r == usual) excludedValues(excluded) else emptyList()) +
                            excludedValues(denominators.filter { isPolynomialIn(it) }).filter { (letter, value) -> definedAt(r, letter, value) }
                        Outcome.Expression(r, steps, excluded = cancelled.distinct())
                    }
                    Mode.EXPAND -> Outcome.Expression(expanded, steps)
                    Mode.FACTOR -> factored(last)
                    Mode.DERIVATIVE -> Outcome.Expression(Calculus.derivative(last, Expr.Sym(c.letter ?: variable)))
                    Mode.INTEGRAL -> Outcome.Expression(Calculus.antiderivative(last, Expr.Sym(c.letter ?: variable))
                        ?: throw CalcException(Failure.NO_ANTIDERIVATIVE), plusC = true)
                    Mode.GRAPH -> Outcome.Graph(variable, listOf(last), parameters = letters - variable)
                    Mode.PARTIAL_FRACTIONS -> Outcome.Expression(partial ?: throw CalcException(Failure.UNREADABLE), steps)
                    Mode.SERIES -> series(last, Expr.Sym(c.letter ?: variable), c.point ?: seriesAt ?: "0", c.order)
                    Mode.EXTREMA -> critical(last, letters)
                    Mode.GRADIENT -> Outcome.Expression(LinAlg.gradient(last, fieldLetters))
                    Mode.LAPLACIAN -> Outcome.Expression(LinAlg.laplacian(last, fieldLetters))
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
        // A curve of degree two in x and y: a conic, named first where both squares (or xy) are in it.
        val conic = letters.toSet() == setOf("x", "y") && !wroteCalculus && Geometry.isConic(difference)
        if (conic) choices += Choice(Mode.CONIC)
        val whole = letters.size == 2 && !wroteCalculus && try { Discrete.integers(difference, letters) != null } catch (e: CalcException) { false }
        if (whole) choices += Choice(Mode.DIOPHANTINE)
        val chosen = if (conic && !identity && Geometry.bothSquares(difference)) Choice(Mode.CONIC) else likeliest
        return Plan(choices, chosen, onRequest) { c ->
            when (c.mode) {
                Mode.CONIC -> Outcome.ConicSection(Geometry.conic(difference) ?: throw CalcException(Failure.UNSOLVED))
                Mode.DIOPHANTINE -> Outcome.WholeSolutions(Discrete.integers(difference, letters) ?: throw CalcException(Failure.UNSOLVED))
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
                    // Solutions on whole intervals (|x| = x for x ≥ 0): the set as intervals, never the points found alone.
                    if (found.incomplete) return@Plan Outcome.Intervals(Solve.intervalSolutions(lhs, rhs, v) ?: throw CalcException(Failure.UNSOLVED))
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
    private fun excludedValues(factors: List<Expr>): List<Pair<String, Expr>> = factors.flatMap { pieces(it) }.distinct().flatMap { f ->
        val letters = f.symbols().sorted()
        if (letters.isEmpty()) return@flatMap emptyList()
        try {
            // In one letter its values (x ≠ 1); in several, solved for the first (a ≠ −b for a + b): a ≠ 0 and b ≠ 0 for a·b².
            // Values only where they are all of them: sin x is 0 at every kπ, which no list of x ≠ … holds.
            if (letters.size == 1) Solve.equation(f, Expr.ZERO, Expr.Sym(letters[0])).takeIf { it.periodic.isEmpty() && !it.many && !it.incomplete }
                ?.values.orEmpty().map { letters[0] to it }
            else Solve.forLetter(f, Expr.ZERO, Expr.Sym(letters[0])).values.filter { it.isConstant() || !it.has(Expr.Sym(letters[0])) }.map { letters[0] to it }
        } catch (e: CalcException) {
            emptyList()
        }
    }.distinct()

    /**
     * A value of the letters (two exact ones tried) where [a] and [b] differ in ball arithmetic, though doubles found them
     * equal; null where none is found.
     */
    private fun ballsApart(a: Expr, b: Expr): Map<String, Double>? {
        val letters = (a.symbols() + b.symbols()).sorted()
        for (start in listOf(Rational.of(BigInteger.valueOf(37), BigInteger.valueOf(100)), Rational.of(BigInteger.valueOf(129), BigInteger.valueOf(100)))) {
            val values = letters.withIndex().associate { (k, v) -> v to start + Rational.of(BigInteger.valueOf(k.toLong()), BigInteger.valueOf(7)) }
            try {
                var d: Expr = add(a, neg(b))
                for ((v, r) in values) d = d.substitute(Expr.Sym(v), num(r))
                if (Precise.certainlyNonzero(d, maxBits = 512) == true) return values.mapValues { it.value.toDouble() }
            } catch (x: CalcException) {
                if (x.failure == Failure.TOO_LONG) throw x
            } catch (x: ArithmeticException) {
            }
        }
        return null
    }

    /** Whether [e] has a value where [letter] is [value] (its other letters at a few values): a denominator cancelled from it. */
    private fun definedAt(e: Expr, letter: String, value: Expr): Boolean = try {
        val put = e.substitute(Expr.Sym(letter), value)
        val others = put.symbols()
        listOf(0.37, 1.29).all { v -> Numeric.eval(put, others.associateWith { v + it.length * 0.01 }).isFinite() }
    } catch (x: CalcException) {
        false
    } catch (x: ArithmeticException) {
        false
    }

    /** A factor cancelled from a denominator in its pieces, each of which must not be 0: a·b² as a and b. */
    private fun pieces(f: Expr): List<Expr> = when {
        f is Expr.Mul -> f.factors.flatMap { pieces(it) }
        f is Expr.Pow && (rational(f.exp)?.signum ?: 0) > 0 -> pieces(f.base)
        f.symbols().isEmpty() -> emptyList()
        else -> listOf(f)
    }

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
        val double = Numeric.value(v) ?: throw CalcException(Failure.UNDEFINED)
        // A number in another form (sin² 1 + cos² 1) exactly where it simplifies to one and the balls agree (looked for only
        // where its double lies near a fraction); a double near a fraction is never taken for it.
        if (Rational.near(double) != null) {
            val simple = try { Algebra.simplify(v) } catch (e: CalcException) { v }
            if (simple is Expr.Num && Precise.certainlyNonzero(add(v, neg(simple))) == false) return Outcome.Value(Num.Exact(simple.value), steps, others = others)
        }
        // Its decimal from ball arithmetic, where the balls take it: a double's loses its digits where terms cancel (e^(10⁻¹⁰) − 1).
        val d = Precise.decimal(v)?.toDouble() ?: double
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
        // The singular values of a matrix of numbers, and the Jordan form of a square one.
        val ofNumbers = m.symbols().isEmpty() && !m.hasI()
        if (!vector && ofNumbers) choices += Choice(Mode.SVD)
        if (square && ofNumbers && m.size <= 6) choices += Choice(Mode.JORDAN)
        // A vector field: as many entries as coordinates, in x, y (and z).
        val coordinates = listOf("x", "y", "z").take(m.size)
        val field = vector && m.size in 2..3 && m.symbols().isNotEmpty() && m.symbols().all { it in coordinates }
        if (field) {
            choices += Choice(Mode.DIVERGENCE)
            choices += Choice(Mode.CURL)
            choices += Choice(Mode.JACOBIAN)
        }
        choices += Choice(Mode.TRANSPOSE)
        // Numbers in a list are data; in two columns, points.
        val numeric = m.symbols().isEmpty() && m.rows.all { r -> r.all { Numeric.value(it) != null } }
        val values = if (vector) m.rows.map { it[0] } else emptyList()
        val data = vector && numeric && m.size >= 4
        if (data) choices += Choice(Mode.STATISTICS)
        if (data && values.all { rational(it) != null }) choices += Choice(Mode.SEQUENCE)
        val points = !vector && numeric && m.columns == 2 && m.size >= 3
        if (points) choices += Choice(Mode.REGRESSION)
        if (points) choices += Interpolation.offered(m)
        // Asked for by name, a matrix's operations are done where its size allows (a larger one as long as the time allows) and
        // refused otherwise ([Failure.SIZES]); a vector's calculus is taken in its letters ([variablesOf]).
        val ownOperations = setOf(Mode.DETERMINANT, Mode.INVERSE, Mode.EIGEN, Mode.DECOMPOSE, Mode.MATRIX_EXP, Mode.ROW_REDUCE, Mode.TRANSPOSE,
            Mode.DIVERGENCE, Mode.CURL, Mode.JACOBIAN)
        val accepts = (if (numeric) setOf(Mode.STATISTICS, Mode.SEQUENCE, Mode.LINE, Mode.PLANE, Mode.REGRESSION) else emptySet()) +
            setOf(Mode.SVD, Mode.JORDAN, Mode.INTERPOLATION, Mode.SPLINE) + ownOperations
        val fieldLetters = if (field) coordinates else variablesOf(m.symbols())
        val likeliest = when {
            data && (literal || m.size >= 5) -> Choice(Mode.STATISTICS)
            literal && points && m.size >= 4 -> Choice(Mode.REGRESSION)
            !literal || vector -> Choice(Mode.CALCULATE)
            square -> Choice(Mode.DETERMINANT)
            else -> Choice(Mode.ROW_REDUCE)
        }
        return Plan(choices, likeliest, accepts) { c ->
            when (c.mode) {
                Mode.STATISTICS -> {
                    val list = if (vector) values else m.rows.flatten()
                    Outcome.Statistics(list, Stats.summary(list) ?: throw CalcException(Failure.UNDEFINED))
                }
                Mode.SEQUENCE -> {
                    val list = if (vector) values else m.rows.flatten()
                    Outcome.SequenceRule(list, Discrete.sequence(list) ?: throw CalcException(Failure.UNSOLVED))
                }
                Mode.REGRESSION -> {
                    if (m.columns != 2) throw CalcException(Failure.SIZES)
                    val xs = m.rows.map { it[0] }
                    val ys = m.rows.map { it[1] }
                    Outcome.Regression(xs, ys, Stats.regression(xs, ys) ?: throw CalcException(Failure.UNSOLVED))
                }
                Mode.INTERPOLATION -> Outcome.Interpolated(Interpolation.interpolate(m))
                Mode.SPLINE -> Outcome.SplineThrough(Interpolation.spline(m))
                // The rows as points: (1, 2) and (3, 4) written as [[1, 2], [3, 4]].
                Mode.LINE, Mode.PLANE -> pointsChoices(m.rows.map { LinAlg.column(it) })?.let { plan ->
                    plan.outcome(Choice(c.mode))
                } ?: throw CalcException(Failure.SIZES)
                Mode.CALCULATE -> Outcome.Expression(m)
                Mode.DETERMINANT -> LinAlg.det(m).let { d -> if (d.symbols().isEmpty()) value(d, emptyList(), emptyList()) else Outcome.Expression(d) }
                Mode.INVERSE -> Outcome.Expression(LinAlg.inverse(m))
                Mode.TRANSPOSE -> Outcome.Expression(LinAlg.transpose(m))
                Mode.ROW_REDUCE -> LinAlg.echelon(m).let { e -> Outcome.RowReduced(e.form, e.rank, e.ops, LinAlg.nullSpace(m), LinAlg.columnSpace(m)) }
                Mode.DECOMPOSE -> {
                    if (vector || m.size < m.columns) throw CalcException(Failure.SIZES)
                    val d = Outcome.Decompositions(LinAlg.lu(m), LinAlg.qr(m), LinAlg.cholesky(m))
                    if (d.lu == null && d.qr == null && d.cholesky == null) throw CalcException(Failure.UNSOLVED)
                    d
                }
                Mode.MATRIX_EXP -> {
                    if (!square) throw CalcException(Failure.SIZES)
                    Outcome.Expression(LinAlg.exp(m) ?: throw CalcException(Failure.UNSOLVED))
                }
                Mode.SVD -> Outcome.SingularValues(MatrixForms.svd(m))
                Mode.JORDAN -> Outcome.JordanForm(MatrixForms.jordan(m) ?: throw CalcException(Failure.UNSOLVED))
                // div and curl of a field with as many entries as letters; the Jacobian of any vector in its letters.
                Mode.DIVERGENCE, Mode.CURL, Mode.JACOBIAN -> {
                    if (!vector || fieldLetters.isEmpty()) throw CalcException(Failure.ARGUMENTS)
                    if (c.mode != Mode.JACOBIAN && (fieldLetters.size != m.size || m.size !in 2..3)) throw CalcException(Failure.SIZES)
                    Outcome.Expression(when (c.mode) {
                        Mode.DIVERGENCE -> LinAlg.divergence(m, fieldLetters)
                        Mode.CURL -> LinAlg.curl(m, fieldLetters)
                        else -> LinAlg.jacobian(m, fieldLetters)
                    })
                }
                Mode.EIGEN -> {
                    if (!square) throw CalcException(Failure.SIZES)
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

    /**
     * Where a series is offered: at 0, else at 1 (ln x, √x), as LaTeX; null where it has neither. Only a chip: looked for in a
     * part of the time, so that a function slow to work with (lambertw(x)) still has its own answer at once.
     */
    private fun seriesPoint(e: Expr, x: Expr.Sym): String? = try {
        Budget.within(CHIP_MS) {
            when {
                Series.taylor(e, x, Expr.ZERO, 4) != null -> "0"
                Series.taylor(e, x, Expr.ONE, 4) != null -> "1"
                else -> null
            }
        }
    } catch (z: CalcException) {
        null
    }

    /** How long looking for a chip may take. */
    private const val CHIP_MS = 400L

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
        var domains = false
        for ((a, b) in sides.zipWithNext()) {
            if (Verify.same(a, b)) {
                // Equal to doubles, apart to the balls (x + tanh 20 and x + 1 differ by 8.5·10⁻¹⁸): no identity.
                val tiny = ballsApart(a, b)
                // Equal where both are defined, but one is defined where the other is not (ln(ab) = ln a + ln b holds only
                // for a, b > 0): no identity either, and said where they part.
                val apart = tiny ?: Verify.domainDifferenceAt(a, b)?.also { domains = true } ?: continue
                holds = false
                at = at ?: apart
                continue
            }
            holds = false
            at = at ?: Verify.differenceAt(a, b)
        }
        // The sides as written where their simplest forms would look the same (ln a + ln b simplifies to ln(ab)).
        return Outcome.Compared(holds, if (domains) sides else simplified, at)
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
        trianglePlan(statements)?.let { return it }
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
            // A linear system with infinitely many solutions: its family in parameters t (checked in Solve.linearFamily).
            val solutions = Solve.system(equations, letters) ?: Solve.linearFamily(equations, letters)?.let { listOf(it) }
                ?: throw CalcException(Failure.UNSOLVED)
            Outcome.SystemSolved(letters, solutions)
        }
    }

    /**
     * A partial differential equation in u, its derivatives written u_t, u_xx (or ∂u/∂t), with lines of conditions u(0, t) = 0,
     * u(x, 0) = f(x) beside it: solved by separation of variables where conditions are given, its type in either case ([Pde]).
     */
    private fun pdePlan(statements: List<Statement>): Plan? {
        val reading = Pde.read(statements) ?: return null
        val type = Choice(Mode.PDE_TYPE)
        val solve = Choice(Mode.PDE)
        val choices = if (reading.conditions.isEmpty()) listOf(type) else listOf(solve, type)
        return Plan(choices, choices[0], setOf(Mode.PDE)) { c ->
            if (c.mode == Mode.PDE) {
                if (reading.conditions.isEmpty()) throw CalcException(Failure.CONDITIONS)
                Outcome.PdeSeries(Pde.separate(reading))
            } else Outcome.PdeType(Pde.classify(reading))
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
        val unknown = (Parser.ODE_UNKNOWNS + Parser.SYSTEM_UNKNOWNS).firstOrNull { name -> statements.any { s -> s.sides.filterNotNull().any { Ode.order(it, name) > 0 } } }
            ?: return null
        val main = statements.firstOrNull { s -> s.sides.filterNotNull().any { Ode.order(it, unknown) > 0 } && !isCondition(s, unknown) } ?: return null
        val sides = main.sides.filterNotNull()
        if (sides.size != 2 || main.relations != listOf(Rel.EQ)) return null
        val equation = Calculus.evaluate(sides[0]) - Calculus.evaluate(sides[1])
        val letters = equation.symbols()
        // x(t), z(t): the unknowns of a system, in t also on their own (x'' + 4x = 0).
        val x = written ?: if (unknown in Parser.SYSTEM_UNKNOWNS || ("t" in letters && "x" !in letters)) "t" else "x"
        if (x == unknown) return null
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
        val choices = listOfNotNull(choice, if (numeric) Choice(Mode.RUNGE_KUTTA) else null, if (numeric) Choice(Mode.EULER) else null,
            if (numeric) Choice(Mode.DORMAND_PRINCE) else null, if (f != null) Choice(Mode.SLOPE_FIELD) else null)
        val accepts = setOf(Mode.DORMAND_PRINCE, Mode.ODE_SERIES, Mode.SLOPE_FIELD)
        return Plan(choices, choice, accepts) { c ->
            if (c.mode == Mode.SLOPE_FIELD) return@Plan Outcome.DirectionField(Fields.slope(f ?: throw CalcException(Failure.UNSOLVED), x, unknown))
            if (c.mode == Mode.ODE_SERIES) {
                val at = c.point?.let { pointOf(it) ?: throw CalcException(Failure.ARGUMENTS) }
                return@Plan Outcome.SeriesSolved(SeriesOde.solve(equation, unknown, x, conditions, at, c.order ?: 6))
            }
            if (c.mode == Mode.DORMAND_PRINCE) {
                if (!numeric) throw CalcException(Failure.UNSOLVED)
                val exact = try { Ode.solve(equation, unknown, x)?.let { Ode.withConditions(equation, it, conditions, unknown, x) }?.explicit } catch (e: CalcException) {
                    if (e.failure == Failure.TOO_LONG) throw e
                    null
                }
                val from = Numeric.value(start!!.at)!!
                val to = c.point?.let { p -> pointOf(p)?.let { Numeric.value(it) } ?: throw CalcException(Failure.ARGUMENTS) } ?: (from + 1)
                return@Plan Outcome.NumericTable(Adaptive.dormandPrince(f!!, x, unknown, from, Numeric.value(start.value)!!, to, exact))
            }
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
            // Values at two points: a boundary value problem, with one solution, none or infinitely many.
            if (BoundaryValue.isBoundary(conditions)) return@Plan Outcome.BoundaryValueSolved(BoundaryValue.solve(equation, general, conditions, unknown, x))
            val particular = if (conditions.isEmpty()) null else Ode.withConditions(equation, general, conditions, unknown, x) ?: throw CalcException(Failure.UNSOLVED)
            Outcome.OdeSolved(unknown, x, general, particular, f)
        }
    }

    /**
     * Lines that are a system of first-order differential equations in two or more unknowns (x′ = …, y′ = …): solved where it is
     * linear with constant coefficients, and in the plane with its phase portrait.
     */
    private fun odeSystemPlan(statements: List<Statement>, written: String?): Plan? {
        val r = OdeSystems.read(statements, written) ?: return null
        val linear = OdeSystems.linear(r) != null
        val plane = r.unknowns.size == 2 && r.slopes.none { it.has(Expr.Sym(r.t)) } && r.letters.isEmpty()
        val solve = Choice(Mode.ODE)
        val portrait = Choice(Mode.PHASE_PORTRAIT)
        val choices = listOfNotNull(if (linear) solve else null, if (plane) portrait else null)
        val likeliest = if (linear || !plane) solve else portrait
        return Plan(choices.ifEmpty { listOf(solve) }, likeliest, setOf(Mode.ODE, Mode.PHASE_PORTRAIT)) { c ->
            if (c.mode == Mode.PHASE_PORTRAIT) {
                if (!plane) throw CalcException(Failure.UNSOLVED)
                Outcome.DirectionField(Fields.phase(r.slopes[0], r.slopes[1], r.unknowns[0], r.unknowns[1], r.t))
            } else {
                Outcome.OdeSystemSolved(OdeSystems.solve(r))
            }
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

    /** Logic and sets: a truth table and the simplest forms, or sets written out worked out. */
    private fun logicPlan(r: Logic.Reading): Plan = when (r) {
        is Logic.Reading.Literal -> Plan(listOf(Choice(Mode.CALCULATE)), Choice(Mode.CALCULATE)) { Outcome.SetValue(r.elements) }
        is Logic.Reading.Formula -> Plan(listOfNotNull(Choice(Mode.LOGIC),
            Choice(Mode.VENN).takeIf { r.sets && r.right == null && Logic.variables(r.left).size in 1..VennDiagram.MOST_SETS }),
            Choice(Mode.LOGIC)) { Outcome.LogicTable(Logic.analyse(r)) }
    }

    /** Lines that are congruences (each with its modulus): solved together, or checked where they hold no letter. */
    private fun congruencePlan(statements: List<Statement>, moduli: Map<Int, BigInteger>): Plan {
        val lines = statements.mapIndexed { k, s ->
            val sides = s.sides.filterNotNull()
            if (sides.size != 2 || s.relations.size != 1 || s.relations[0] != Rel.EQ) throw CalcException(Failure.UNREADABLE)
            val m = moduli[k] ?: throw CalcException(Failure.UNREADABLE)
            Calculus.tidy(Calculus.evaluate(sides[0]) - Calculus.evaluate(sides[1])) to m
        }
        val letters = lines.flatMap { it.first.symbols() }.toSortedSet()
        if (letters.isEmpty()) {
            val holds = lines.all { (d, m) -> integer(d)?.mod(m)?.signum() == 0 }
            return Plan(listOf(Choice(Mode.COMPARE)), Choice(Mode.COMPARE)) { Outcome.Checked(holds) }
        }
        val letter = letters.singleOrNull() ?: throw CalcException(Failure.SEVERAL_UNKNOWNS)
        val choice = Choice(Mode.CONGRUENCE, letter)
        return Plan(listOf(choice), choice) { Outcome.Congruence(Discrete.congruences(lines, letter) ?: throw CalcException(Failure.UNSOLVED)) }
    }

    /** aₙ₊₁ = 2aₙ + 1 with its initial values: a recurrence, solved in closed form. */
    private fun recurrencePlan(statements: List<Statement>): Plan? {
        val names = statements.flatMap { s -> s.sides.filterNotNull().flatMap { it.symbols() } }.toSet()
        if (!Discrete.isRecurrence(names)) return null
        val equations = statements.map { s ->
            val sides = s.sides.filterNotNull()
            if (sides.size != 2 || s.relations.size != 1 || s.relations[0] != Rel.EQ) return null
            Calculus.evaluate(sides[0]) - Calculus.evaluate(sides[1])
        }
        val choice = Choice(Mode.RECURRENCE)
        return Plan(listOf(choice), choice) { Outcome.RecurrenceSolved(Discrete.recurrence(equations) ?: throw CalcException(Failure.UNSOLVED)) }
    }

    /** Two or three points, one to a line: the line through two, the plane through three. */
    private fun pointsPlan(statements: List<Statement>): Plan? {
        if (statements.size !in 2..3) return null
        val points = statements.map { s ->
            val sides = s.sides.filterNotNull()
            if (sides.size != 1 || s.relations.isNotEmpty()) return null
            (Calculus.evaluate(sides[0]) as? Expr.Mat)?.takeIf { LinAlg.isVector(it) && it.size in 2..3 && it.symbols().isEmpty() } ?: return null
        }
        if (points.map { it.size }.distinct().size != 1) return null
        return pointsChoices(points)
    }

    private fun pointsChoices(points: List<Expr.Mat>): Plan? {
        if (points.any { !LinAlg.isVector(it) || it.size !in 2..3 } || points.map { it.size }.distinct().size != 1) return null
        val choices = ArrayList<Choice>()
        if (points.size == 2) choices += Choice(Mode.LINE)
        if (points.size == 3 && points[0].size == 3) choices += Choice(Mode.PLANE)
        if (choices.isEmpty()) return null
        return Plan(choices, choices[0]) { c ->
            if (c.mode == Mode.PLANE) Outcome.PlaneThrough(Geometry.plane(points[0], points[1], points[2]))
            else Outcome.LineThrough(Geometry.line(points[0], points[1]))
        }
    }

    /** a = 3, b = 4, γ = 60°: three of a triangle's sides and angles (angles in degrees unless written with π). */
    private fun trianglePlan(statements: List<Statement>): Plan? {
        if (statements.size != 3) return null
        val sides = HashMap<Int, Expr>()
        val angles = HashMap<Int, Expr>()
        for (s in statements) {
            val w = s.sides.filterNotNull()
            if (w.size != 2 || s.relations != listOf(Rel.EQ)) return null
            val name = (w[0] as? Expr.Sym)?.name ?: return null
            val v = Calculus.evaluate(w[1])
            if (v.symbols().isNotEmpty() || v is Expr.Mat || Numeric.value(v) == null) return null
            val side = Geometry.SIDES.indexOf(name)
            val angle = Geometry.ANGLES.indexOfFirst { name in it }
            when {
                side >= 0 -> if (sides.put(side, v) != null) return null
                angle >= 0 -> if (angles.put(angle, if (v.hasPi()) Calculus.tidy(mul(v, num(180), pow(Expr.PI, Expr.MINUS_ONE))) else v) != null) return null
                else -> return null
            }
        }
        if (sides.isEmpty()) return null
        val choice = Choice(Mode.TRIANGLE)
        return Plan(listOf(choice), choice) { Outcome.Triangles(Geometry.triangles(sides, angles)) }
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
        if (abs(x - y) > 1e-9 * max(1.0, max(abs(x), abs(y)))) return false
        // Close as doubles: equal only where the balls cannot tell them apart either (tanh 20 = 1 does not hold).
        return Precise.certainlyNonzero(add(a, neg(b))) != true
    }
}

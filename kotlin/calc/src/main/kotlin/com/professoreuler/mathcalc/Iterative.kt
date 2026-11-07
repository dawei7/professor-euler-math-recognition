package com.professoreuler.mathcalc

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import kotlin.math.abs

/**
 * Iterations to a solution, each as the table it builds and with what decides whether it converges: the secant method and
 * fixed-point iteration x = g(x) for one equation, the Jacobi and Gauss–Seidel methods for a linear system A·x = b. The table
 * is what the method gives (doubles), never an exact answer. A result stands under it only where the method's stopping rule
 * was met and the result was checked another way: a zero (a fixed point) by a change of sign of f (of g(x) − x) in ball
 * arithmetic on an interval on which f is defined and whose ends round to the same ten digits, a linear system's against its
 * exact solution. Whether an iteration converges is decided exactly where it can be: |g′| at the fixed point, strict diagonal
 * dominance, and the spectral radius of the iteration matrix by the Schur–Cohn test of its characteristic polynomial (exact
 * rationals; its digits by bisection with the same test in ball arithmetic).
 */
object Iterative {
    /** The modes of this file and of [Norms] and [Orthogonal]: worked out only where a call asks for them. */
    val MODES = setOf(Mode.SECANT, Mode.FIXED_POINT, Mode.JACOBI, Mode.GAUSS_SEIDEL, Mode.CONDITION, Mode.NORM, Mode.GRAM_SCHMIDT,
        Mode.PROJECTION)

    /** At most this many steps of the secant method. */
    const val SECANT_STEPS = 50

    /** At most this many steps of fixed-point iteration and of the Jacobi and Gauss–Seidel methods. */
    const val STEPS = 100

    /** The stopping rule of every method here: a step that changes x by at most 10⁻¹² (relative to |x|, at least 1). */
    const val TOLERANCE = 1e-12

    /** What a fixed point does to an iteration near it: |g′(x*)| < 1 attracts, > 1 repels, = 1 does not decide. */
    enum class Kind { ATTRACTING, REPELLING, NEUTRAL }

    /** A condition of convergence, as it was checked. */
    sealed class Test {
        /**
         * Strict diagonal dominance by rows, |aᵢᵢ| > Σⱼ≠ᵢ |aᵢⱼ| in every row, which makes both methods converge for every start;
         * [row] the first row (0-based) where it fails, null where it holds.
         */
        data class Dominance(val holds: Boolean, val row: Int?) : Test()

        /**
         * The spectral radius ρ(T) of the iteration [matrix] T (Jacobi T = −D⁻¹(L + U), Gauss–Seidel T = −(D + L)⁻¹U): [exact]
         * where T's eigenvalues are known exactly, [digits] certified; [below] is ρ(T) < 1, decided exactly, which is exactly
         * when the method converges for every start.
         */
        data class Radius(val matrix: Expr.Mat, val exact: Expr?, val digits: Decimal, val below: Boolean) : Test()

        /**
         * |g′(x*)| at a fixed point x* = [at] of g (exact, or certified to ten digits): [exact] where it is known exactly,
         * [digits] certified; its [kind].
         */
        data class Slope(val at: Expr, val exact: Expr?, val digits: Decimal?, val kind: Kind) : Test()
    }

    /**
     * An iteration x ← G(x): its [table] (fixed-point iteration n, xₙ, |xₙ − xₙ₋₁|; Jacobi and Gauss–Seidel k, the unknowns,
     * ‖Δx‖∞), its unknowns [letters], the [result] (the last step, where the stopping rule was met and it was checked; null
     * otherwise), the [exact] solution where the calculator has it and the [error] max |resultᵢ − exactᵢ|, and the [tests] that
     * decide whether the method converges.
     */
    class Iteration(val table: Numerics.Table, val letters: List<String>, val result: List<Double>?, val exact: List<Expr>?,
        val error: Double?, val tests: List<Test>)

    /** What a call of one of [MODES] can be worked out as: its [choices], and how each comes to its outcome. */
    internal class Work(val choices: List<Choice>, val outcome: (Choice) -> Outcome)

    /** The work for [statements] where [choice] asks for one of [MODES]; null otherwise. */
    internal fun plan(statements: List<Statement>, choice: Choice?): Work? {
        val mode = choice?.mode ?: return null
        if (mode !in MODES || statements.isEmpty()) return null
        val work = guarded {
            when (mode) {
                Mode.SECANT, Mode.FIXED_POINT -> scalar(statements, choice)
                Mode.JACOBI, Mode.GAUSS_SEIDEL -> system(statements, choice)
                Mode.CONDITION, Mode.NORM -> Norms.work(statements, choice)
                else -> Orthogonal.work(statements, choice)
            }
        }
        return Work(work.choices) { c -> guarded { work.outcome(c) } }
    }

    /** Ball arithmetic that cannot decide ([Imprecise]) never reaches the learner: what it would have shown is not shown. */
    private fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: Imprecise) {
        throw CalcException(Failure.UNSOLVED)
    }

    // ------------------------------------------------------------------------------------------------------------------
    // One equation: the secant method, fixed-point iteration.

    private fun scalar(statements: List<Statement>, c: Choice): Work {
        val s = statements.singleOrNull() ?: throw CalcException(Failure.ARGUMENTS)
        if (s.definition != null || s.sides.any { it == null }) throw CalcException(Failure.ARGUMENTS)
        val sides = s.sides.map { Calculus.evaluate(it!!) }
        if (sides.size > 2 || (sides.size == 2 && s.relations != listOf(Rel.EQ))) throw CalcException(Failure.ARGUMENTS)
        if (sides.any { it is Expr.Mat || it.hasOp() || it.hasI() }) throw CalcException(Failure.ARGUMENTS)
        val letters = sides.flatMap { it.symbols() }.toSortedSet()
        // A number where the function belongs: secant(π/3, 1, 2) is no secant of π/3.
        if (letters.isEmpty()) throw CalcException(Failure.ARGUMENTS)
        if (letters.size > 1) throw CalcException(Failure.SEVERAL_UNKNOWNS)
        val x = Expr.Sym(letters.first())
        val starts = c.point?.split(',')?.map { numberOf(it) ?: throw CalcException(Failure.ARGUMENTS) } ?: throw CalcException(Failure.ARGUMENTS)
        if (c.mode == Mode.SECANT) {
            if (starts.size != 2 || starts[0] == starts[1]) throw CalcException(Failure.ARGUMENTS)
            val f = if (sides.size == 2) Calculus.tidy(sides[0] - sides[1]) else sides[0]
            return Work(listOf(c, Choice(Mode.GRAPH, x.name))) { Outcome.NumericTable(secant(f, x, starts[0], starts[1])) }
        }
        if (starts.size != 1) throw CalcException(Failure.ARGUMENTS)
        // x = g(x), g(x) = x, or g on its own.
        val g = when {
            sides.size == 1 -> sides[0]
            sides[0] == x -> sides[1]
            sides[1] == x -> sides[0]
            else -> throw CalcException(Failure.ARGUMENTS)
        }
        return Work(listOf(c)) { Outcome.Iterated(fixedPoint(g, x, starts[0])) }
    }

    private fun value(e: Expr, x: String, v: Double) = Numeric.eval(e, mapOf(x to v))

    /**
     * The secant method from [x0] and [x1]: xₙ₊₁ = xₙ − f(xₙ)·(xₙ − xₙ₋₁)/(f(xₙ) − f(xₙ₋₁)), until a step changes x by at most
     * [TOLERANCE] (relative), f(xₙ) = 0, the secant is level (f(xₙ) = f(xₙ₋₁)), a value is not finite, or [SECANT_STEPS] steps.
     * The result is the last xₙ where the rule was met and a zero of f was proved next to it ([certify]).
     */
    fun secant(f: Expr, x: Expr.Sym, x0: Double, x1: Double): Numerics.Table {
        val rows = ArrayList<List<Double>>()
        var a = x0
        var fa = value(f, x.name, a)
        var b = x1
        var fb = value(f, x.name, b)
        rows += listOf(0.0, a, fa)
        rows += listOf(1.0, b, fb)
        var found: Double? = when {
            fa == 0.0 -> a
            fb == 0.0 -> b
            else -> null
        }
        var n = 2
        while (found == null && n <= SECANT_STEPS + 1) {
            Budget.check()
            if (!fa.isFinite() || !fb.isFinite() || fa == fb) break
            val next = b - fb * (b - a) / (fb - fa)
            if (!next.isFinite()) break
            val fn = value(f, x.name, next)
            rows += listOf(n.toDouble(), next, fn)
            if (fn == 0.0 || abs(next - b) <= TOLERANCE * maxOf(1.0, abs(next))) {
                found = next
                break
            }
            a = b; fa = fb; b = next; fb = fn
            n++
        }
        // A zero proved exactly at a point (at 0 where the method came that close) is that point.
        val result = found?.let { r -> certify(f, x.name, r)?.let { b -> if (b.lo == b.hi) b.lo + 0.0 else r } }
        return Numerics.Table(Numerics.Method.SECANT, listOf("n", "x_n", "f(x_n)"), rows, result)
    }

    /**
     * Fixed-point iteration xₙ₊₁ = g(xₙ) from [x0], until a step changes x by at most [TOLERANCE] (relative), a value is not
     * finite or beyond 10¹⁰⁰, or [STEPS] steps. The result is the last xₙ where the rule was met and a fixed point was proved next
     * to it (a change of sign of g(x) − x); the tests are |g′| there, or, where the iteration did not converge, at the fixed
     * points the calculator finds (the nearest to x₀ first).
     */
    fun fixedPoint(g: Expr, x: Expr.Sym, x0: Double): Iteration {
        val rows = ArrayList<List<Double>>()
        var v = x0
        rows += listOf(0.0, v, Double.NaN)
        var converged: Double? = null
        for (n in 1..STEPS) {
            Budget.check()
            val next = value(g, x.name, v)
            if (!next.isFinite()) break
            val step = abs(next - v)
            rows += listOf(n.toDouble(), next, step)
            if (step <= TOLERANCE * maxOf(1.0, abs(next))) {
                converged = next
                break
            }
            if (abs(next) > 1e100) break
            v = next
        }
        val h = Calculus.tidy(g - x)
        // A few more steps (not shown) to a double's precision before the value is certified: a step below 10⁻¹² can leave the
        // value further than that from the fixed point, and its bracket then crossed a rounding of the ten digits shown
        // (fixedpoint(e^(-x)/2, 2) had no result, from 1 it had).
        converged = converged?.let { c ->
            var refined = c
            for (k in 0 until 30) {
                val next = value(g, x.name, refined)
                if (!next.isFinite() || abs(next - refined) > abs(c - v) + TOLERANCE) break
                if (next == refined) break
                refined = next
            }
            refined
        }
        val bracket = converged?.let { certify(h, x.name, it) }
        val result = if (bracket == null) null else if (bracket.lo == bracket.hi) bracket.lo + 0.0 else converged
        val slope = Calculus.tidy(Calculus.derivative(g, x))
        // The fixed points the calculator finds where it solves g(x) = x: exact ones, or numbers proved by a change of sign.
        val solved = try {
            val s = Solve.equation(g, x, x)
            if (s.many || s.periodic.isNotEmpty()) emptyList()
            else s.values.filter { it.symbols().isEmpty() && !it.hasI() && Numeric.value(it)?.isFinite() == true }.map { it to s.numeric }
        } catch (e: CalcException) {
            if (e.failure == Failure.TOO_LONG) throw e
            emptyList()
        }
        val exactPoints = solved.filter { !it.second && it.first !is Expr.Real }.map { it.first }
        val tests = ArrayList<Test>()
        var exact: List<Expr>? = null
        var error: Double? = null
        if (result != null && bracket != null) {
            // The fixed point it converged to: the exact one inside its bracket, else the bracket itself.
            val match = exactPoints.firstOrNull { p -> inside(p, bracket) }
            if (match != null) {
                exact = listOf(match)
                error = abs(result - Numeric.value(match)!!)
                slopeAt(slope, x, match)?.let { tests += it }
            } else {
                slopeOver(slope, x, bracket, result)?.let { tests += it }
            }
        } else {
            // Where it did not converge: what |g′| says at the fixed points nearest the start.
            for ((p, numeric) in solved.sortedBy { abs(Numeric.value(it.first)!! - x0) }.take(4)) {
                if (!numeric && p !is Expr.Real) {
                    slopeAt(slope, x, p)?.let { tests += it }
                } else {
                    val v = Numeric.value(p)!!
                    certify(h, x.name, v)?.let { b -> slopeOver(slope, x, b, v)?.let { tests += it } }
                }
            }
        }
        val table = Numerics.Table(Numerics.Method.FIXED_POINT, listOf("n", "x_n", "|x_n - x_{n-1}|"), rows, result)
        return Iteration(table, listOf(x.name), result?.let { listOf(it) }, exact, error, tests)
    }

    /** |g′| at an exact fixed point [p]: exact, its digits, and what it says. */
    private fun slopeAt(slope: Expr, x: Expr.Sym, p: Expr): Test.Slope? {
        val m = try { Calculus.tidy(fn(Func.ABS, Calculus.tidy(slope.substitute(x, p)))) } catch (e: CalcException) {
            if (e.failure == Failure.TOO_LONG) throw e
            return null
        }
        if (m.symbols().isNotEmpty() || m.hasI()) return null
        val sign = signOf(Calculus.tidy(m - Expr.ONE)) ?: return null
        val kind = when {
            sign < 0 -> Kind.ATTRACTING
            sign > 0 -> Kind.REPELLING
            else -> Kind.NEUTRAL
        }
        val digits = try { Precise.digits(m, 10, complex = false).re } catch (e: CalcException) {
            if (e.failure == Failure.TOO_LONG) throw e
            null
        }
        return Test.Slope(p, m.takeIf { MathCalc.niceExact(it) || it is Expr.Num }, digits, kind)
    }

    /** |g′| over the bracket of a fixed point found numerically, in ball arithmetic: its digits and what it says. */
    private fun slopeOver(slope: Expr, x: Expr.Sym, bracket: Bracket, at: Double): Test.Slope? {
        val ball = try {
            val z = Precise.eval(slope, 192, false, mapOf(x.name to CBall(Ball.span(BigFloat.of(bracket.lo), BigFloat.of(bracket.hi)))))
            z.re
        } catch (e: CalcException) {
            if (e.failure == Failure.TOO_LONG) throw e
            return null
        } catch (e: Imprecise) {
            return null
        } catch (e: ArithmeticException) {
            return null
        }
        val kind = when {
            absUp(ball) < BigFloat.ONE -> Kind.ATTRACTING
            absDown(ball) > BigFloat.ONE -> Kind.REPELLING
            else -> return null
        }
        // |g′| over the bracket: the ball itself where it does not hold 0, its digits; none where it does.
        val digits = if (ball.containsZero() && !ball.isExactZero) null else digitsOf(if (ball.isNegative) -ball else ball)
        return Test.Slope(Expr.Real(at), null, digits, kind)
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Proving a zero.

    /** An interval [lo, hi] in which a zero was proved, every number of which rounds to the same ten digits. */
    internal class Bracket(val lo: Double, val hi: Double)

    /** Whether the exact number [p] lies in [b]: in its ball, or for a point bracket, equal to it. */
    private fun inside(p: Expr, b: Bracket): Boolean {
        val v = Numeric.value(p) ?: return false
        if (v < b.lo - 1e-9 * maxOf(1.0, abs(v)) || v > b.hi + 1e-9 * maxOf(1.0, abs(v))) return false
        if (b.lo == b.hi) return try { isZero(Calculus.tidy(p - num(exactly(b.lo)))) } catch (e: CalcException) {
            if (e.failure == Failure.TOO_LONG) throw e
            false
        }
        return within(p, BigFloat.of(b.lo), BigFloat.of(b.hi))
    }

    /**
     * A zero of [f] next to [r], proved: f is exactly 0 at r (or at 0, where r is that close to it), or f changes sign between
     * the ends of [r − δ, r + δ] in ball arithmetic and is defined on all of it, for a δ (10⁻¹⁵ to 10⁻¹¹ relative) small enough
     * that the ends round to the same ten significant digits. Null where none is proved (a double zero, which does not change
     * sign; a pole; a function with jumps).
     */
    internal fun certify(f: Expr, x: String, r: Double): Bracket? {
        if (!r.isFinite() || hasJumps(f)) return null
        if (exactlyZero(f, x, r)) return Bracket(r, r)
        if (abs(r) < 1e-12 && exactlyZero(f, x, 0.0)) return Bracket(0.0, 0.0)
        val scale = maxOf(1.0, abs(r))
        for (d in doubleArrayOf(1e-15, 1e-14, 1e-13, 1e-12, 1e-11)) {
            val lo = r - d * scale
            val hi = r + d * scale
            // A wider interval cannot round alike either.
            if (!sameDigits(lo, hi)) return null
            val a = signAt(f, x, lo) ?: continue
            val b = signAt(f, x, hi) ?: continue
            if (a == b || !definedOn(f, x, lo, hi)) continue
            return Bracket(lo, hi)
        }
        return null
    }

    /** Functions with jumps or poles a ball does not see: no change of sign proves a zero of them. */
    private fun hasJumps(e: Expr): Boolean = when (e) {
        is Expr.Fn -> e.f in JUMPS || e.args.any(::hasJumps)
        is Expr.Add -> e.terms.any(::hasJumps)
        is Expr.Mul -> e.factors.any(::hasJumps)
        is Expr.Pow -> hasJumps(e.base) || hasJumps(e.exp)
        else -> false
    }

    private val JUMPS = setOf(Func.FLOOR, Func.CEIL, Func.SIGN, Func.FACTORIAL, Func.GAMMA, Func.DIGAMMA, Func.ZETA)

    /** The ten significant digits the app writes a double with ([Format.DIGITS]). */
    private fun tenDigits(v: Double): BigDecimal = BigDecimal(v).round(MathContext(Format.DIGITS))

    private fun sameDigits(a: Double, b: Double) = tenDigits(a).compareTo(tenDigits(b)) == 0

    /** f at the exact number [r] (a double is a fraction) simplified to 0. */
    private fun exactlyZero(f: Expr, x: String, r: Double): Boolean = try {
        isZero(Calculus.tidy(f.substitute(Expr.Sym(x), num(exactly(r)))))
    } catch (e: CalcException) {
        if (e.failure == Failure.TOO_LONG) throw e
        false
    } catch (e: ArithmeticException) {
        false
    }

    /** A double as the fraction it is. */
    internal fun exactly(d: Double): Rational {
        val b = BigDecimal(d)
        return if (b.scale() <= 0) Rational.of(b.toBigIntegerExact()) else Rational.of(b.unscaledValue(), BigInteger.TEN.pow(b.scale()))
    }

    /** The sign of f at the double [v] in ball arithmetic, or null where the ball holds 0 or f is not defined there. */
    private fun signAt(f: Expr, x: String, v: Double): Int? = try {
        val z = Precise.eval(f, 192, false, mapOf(x to CBall(Ball(BigFloat.of(v))))).re
        when {
            z.isPositive -> 1
            z.isNegative -> -1
            else -> null
        }
    } catch (e: CalcException) {
        if (e.failure == Failure.TOO_LONG) throw e
        null
    } catch (e: Imprecise) {
        null
    } catch (e: ArithmeticException) {
        null
    }

    /** Whether f is defined on all of [lo, hi]: its ball over the interval is worked out (no pole, no root of a negative). */
    private fun definedOn(f: Expr, x: String, lo: Double, hi: Double): Boolean = try {
        Precise.eval(f, 192, false, mapOf(x to CBall(Ball.span(BigFloat.of(lo), BigFloat.of(hi)))))
        true
    } catch (e: CalcException) {
        if (e.failure == Failure.TOO_LONG) throw e
        false
    } catch (e: Imprecise) {
        false
    } catch (e: ArithmeticException) {
        false
    }

    /** The sign of a constant, decided in ball arithmetic (0 where it is 0 to 2⁻¹³⁰, as [LinAlg.isNull] decides). */
    internal fun signOf(e: Expr): Int? {
        if (isZero(e)) return 0
        if (LinAlg.isNull(e)) return 0
        for (bits in intArrayOf(128, 512)) {
            try {
                val z = Precise.eval(e, bits, false, emptyMap()).re
                if (z.isPositive) return 1
                if (z.isNegative) return -1
            } catch (x: CalcException) {
                if (x.failure == Failure.TOO_LONG) throw x
                return null
            } catch (x: Imprecise) {
                // more bits
            } catch (x: ArithmeticException) {
                return null
            }
        }
        return null
    }

    /** Bounds of |x| over a ball, exactly (the ball's own [Ball.magUp] and [Ball.magDown] keep 30 bits only). */
    private fun absUp(b: Ball): BigFloat = b.mid.abs() + b.rad

    private fun absDown(b: Ball): BigFloat = b.mid.abs() - b.rad

    /** As many certified digits of a ball as it has, up to ten; null where it has none. */
    internal fun digitsOf(b: Ball): Decimal? {
        if (b.isExactZero) return Decimal.Zero
        for (d in 10 downTo 1) Precise.decimal(b, d)?.let { return it }
        return null
    }

    /** A number written in LaTeX (a start, a bound), or null where it is none. */
    internal fun numberOf(latex: String): Double? = try {
        val s = Parser(MathCalc.TOKEN.findAll(latex).map { it.value }.toList()).statements()
        val side = s.singleOrNull()?.takeIf { it.relations.isEmpty() }?.sides?.singleOrNull()
        val e = side?.let { Calculus.evaluate(it) }
        if (e == null || e is Expr.Mat || e.symbols().isNotEmpty() || e.hasI()) null else Numeric.value(e)?.takeIf { it.isFinite() }
    } catch (e: CalcException) {
        null
    }

    /** A vector written in LaTeX, its entries exact, or null where it is none. */
    internal fun vectorOf(latex: String): Expr.Mat? = try {
        val s = Parser(MathCalc.TOKEN.findAll(latex).map { it.value }.toList()).statements()
        val side = s.singleOrNull()?.takeIf { it.relations.isEmpty() }?.sides?.singleOrNull()
        (side?.let { Calculus.evaluate(it) } as? Expr.Mat)?.takeIf { it.columns == 1 }
    } catch (e: CalcException) {
        null
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Linear systems: the Jacobi and Gauss–Seidel methods.

    /**
     * jacobimethod(A, b), jacobimethod(A, b, x₀) with the matrix as the formula and b, x₀ in the choice's point ("b;x₀"), or
     * jacobimethod(equations, x₀) with the equations as its lines (row i is equation i, column j the j-th letter in alphabetical
     * order).
     */
    private fun system(statements: List<Statement>, c: Choice): Work {
        val params = c.point?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val first = statements[0].sides.singleOrNull()?.let { Calculus.evaluate(it) }
        val a: List<List<Rational>>
        val b: List<Rational>
        val letters: List<String>
        val start: List<Expr.Mat?>
        if (statements.size == 1 && first is Expr.Mat && !LinAlg.isVector(first)) {
            // The matrix and b (and x₀).
            if (params.size !in 1..2) throw CalcException(Failure.ARGUMENTS)
            if (first.size != first.columns) throw CalcException(Failure.SIZES)
            a = first.rows.map { r -> r.map { rational(it) ?: throw CalcException(Failure.UNSOLVED) } }
            val bv = vectorOf(params[0]) ?: throw CalcException(Failure.ARGUMENTS)
            if (bv.size != a.size) throw CalcException(Failure.SIZES)
            b = bv.rows.map { rational(it[0]) ?: throw CalcException(Failure.UNSOLVED) }
            letters = (1..a.size).map { "x_$it" }
            start = listOf(params.getOrNull(1)?.let { vectorOf(it) ?: throw CalcException(Failure.ARGUMENTS) })
        } else {
            // Equations, one to a line, and x₀.
            if (params.size > 1) throw CalcException(Failure.ARGUMENTS)
            val equations = statements.map { s ->
                val sides = s.sides.filterNotNull()
                if (sides.size != 2 || s.sides.size != 2 || s.relations != listOf(Rel.EQ)) throw CalcException(Failure.ARGUMENTS)
                Calculus.tidy(Calculus.evaluate(sides[0]) - Calculus.evaluate(sides[1]))
            }
            letters = equations.flatMap { it.symbols() }.toSortedSet().toList()
            if (letters.size != equations.size || letters.size < 2) throw CalcException(Failure.SIZES)
            val rows = equations.map { e -> linear(e, letters) ?: throw CalcException(Failure.UNSOLVED) }
            a = rows.map { it.first }
            b = rows.map { it.second }
            start = listOf(params.getOrNull(0)?.let { vectorOf(it) ?: throw CalcException(Failure.ARGUMENTS) })
        }
        if (a.size > LinAlg.MOST) throw CalcException(Failure.TOO_LARGE)
        val x0 = start[0]?.let { v ->
            if (v.size != a.size) throw CalcException(Failure.SIZES)
            v.rows.map { Numeric.value(it[0])?.takeIf { d -> d.isFinite() } ?: throw CalcException(Failure.ARGUMENTS) }
        } ?: List(a.size) { 0.0 }
        val other = if (c.mode == Mode.JACOBI) Choice(Mode.GAUSS_SEIDEL, point = c.point) else Choice(Mode.JACOBI, point = c.point)
        return Work(listOf(c, other)) { chosen -> Outcome.Iterated(linearSystem(a, b, x0, chosen.mode == Mode.GAUSS_SEIDEL, letters)) }
    }

    /** The coefficients of a linear equation e = 0 in [letters] and its right side: e = Σ aⱼxⱼ − b; null where it is not linear. */
    private fun linear(e: Expr, letters: List<String>): Pair<List<Rational>, Rational>? {
        val zero = letters.fold(e) { acc, v -> acc.substitute(Expr.Sym(v), Expr.ZERO) }
        val constant = rational(Calculus.tidy(zero)) ?: return null
        val coefficients = letters.map { v -> rational(Calculus.tidy(Calculus.derivative(e, Expr.Sym(v)))) ?: return null }
        // Linear: nothing is left of e beyond its coefficients.
        val rest = Calculus.tidy(Algebra.expand(e - add(letters.mapIndexed { j, v -> mul(num(coefficients[j]), Expr.Sym(v)) }) - num(constant)))
        if (!isZero(rest)) return null
        return coefficients to -constant
    }

    /**
     * The Jacobi method (each xᵢ from the values of the step before) or the Gauss–Seidel method (each xᵢ from the newest values)
     * for A·x = b from [x0], until a step changes x by at most [TOLERANCE] (relative, in the largest entry), a value is not finite
     * or beyond 10¹⁰⁰, or [STEPS] steps. The exact solution A⁻¹b is put back into A·x = b; the result is the last step where the
     * rule was met and it lies within 10⁻⁸ (relative) of the exact solution.
     */
    fun linearSystem(a: List<List<Rational>>, b: List<Rational>, x0: List<Double>, gaussSeidel: Boolean, letters: List<String>): Iteration {
        val n = a.size
        if (a.any { it.size != n } || b.size != n || x0.size != n) throw CalcException(Failure.SIZES)
        if (a.indices.any { a[it][it].signum == 0 }) throw CalcException(Failure.ZERO_DIAGONAL)
        val exact = solveExactly(a, b) ?: throw CalcException(Failure.SINGULAR)
        val ad = a.map { r -> r.map { it.toDouble() } }
        val bd = b.map { it.toDouble() }
        val rows = ArrayList<List<Double>>()
        var x = x0.toDoubleArray()
        rows += listOf(0.0) + x.toList() + Double.NaN
        var converged = false
        for (k in 1..STEPS) {
            Budget.check()
            val next = x.copyOf()
            for (i in 0 until n) {
                var s = bd[i]
                for (j in 0 until n) if (j != i) s -= ad[i][j] * (if (gaussSeidel) next[j] else x[j])
                next[i] = s / ad[i][i]
            }
            val step = (0 until n).maxOf { abs(next[it] - x[it]) }
            rows += listOf(k.toDouble()) + next.toList() + step
            x = next
            if (x.any { !it.isFinite() }) break
            val size = x.maxOf { abs(it) }
            if (step <= TOLERANCE * maxOf(1.0, size)) { converged = true; break }
            if (size > 1e100) break
        }
        // Compared with the exact solution, exactly: a double is a fraction.
        val error = if (converged) (0 until n).maxOf { (exactly(x[it]) - exact[it]).abs().toDouble() } else null
        val scale = maxOf(1.0, exact.maxOf { it.abs().toDouble() })
        val result = if (error != null && error <= 1e-8 * scale) x.toList() else null
        val tests = listOf(dominance(a), radius(iterationMatrix(a, gaussSeidel)))
        val columns = listOf("k") + letters.map { Format.name(it, latex = true) } + "\\lVert\\Delta x\\rVert_{\\infty}"
        val table = Numerics.Table(if (gaussSeidel) Numerics.Method.GAUSS_SEIDEL else Numerics.Method.JACOBI, columns, rows, null)
        return Iteration(table, letters, result, exact.map { num(it) }, error?.takeIf { result != null }, tests)
    }

    /** x with A·x = b by Gaussian elimination in fractions, put back into every equation; null where A is singular. */
    internal fun solveExactly(a: List<List<Rational>>, b: List<Rational>): List<Rational>? {
        val n = a.size
        val m = a.mapIndexed { i, r -> (r + b[i]).toMutableList() }.toMutableList()
        for (c in 0 until n) {
            Budget.check()
            val p = (c until n).firstOrNull { m[it][c].signum != 0 } ?: return null
            if (p != c) { val t = m[p]; m[p] = m[c]; m[c] = t }
            for (r in 0 until n) {
                if (r == c || m[r][c].signum == 0) continue
                val f = m[r][c] / m[c][c]
                for (j in c..n) m[r][j] = m[r][j] - f * m[c][j]
            }
        }
        val x = (0 until n).map { m[it][n] / m[it][it] }
        for (i in 0 until n) {
            var s = Rational.ZERO
            for (j in 0 until n) s += a[i][j] * x[j]
            if (s != b[i]) return null
        }
        return x
    }

    /** Strict diagonal dominance by rows. */
    internal fun dominance(a: List<List<Rational>>): Test.Dominance {
        val row = a.indices.firstOrNull { i ->
            var others = Rational.ZERO
            for (j in a.indices) if (j != i) others += a[i][j].abs()
            a[i][i].abs() <= others
        }
        return Test.Dominance(row == null, row)
    }

    /** Jacobi's T = −D⁻¹(L + U), or Gauss–Seidel's T = −(D + L)⁻¹U (by forward substitution, column by column). */
    internal fun iterationMatrix(a: List<List<Rational>>, gaussSeidel: Boolean): List<List<Rational>> {
        val n = a.size
        if (!gaussSeidel) return List(n) { i -> List(n) { j -> if (i == j) Rational.ZERO else -(a[i][j] / a[i][i]) } }
        val t = MutableList(n) { MutableList(n) { Rational.ZERO } }
        for (j in 0 until n) {
            // (D + L)·t = −U·eⱼ: row i of the right side is −aᵢⱼ where j > i.
            for (i in 0 until n) {
                var s = if (j > i) -a[i][j] else Rational.ZERO
                for (k in 0 until i) s -= a[i][k] * t[k][j]
                t[i][j] = s / a[i][i]
            }
        }
        return t
    }

    /** The spectral radius of [t]: exact where its eigenvalues are, certified digits, and whether it is below 1 (exactly). */
    internal fun radius(t: List<List<Rational>>): Test.Radius {
        val p = characteristic(t)
        val below = allInsideUnitCircle(p)
        val matrix = Expr.Mat(t.map { r -> r.map { num(it) } })
        // ρ = 0: the characteristic polynomial is λⁿ.
        if (p.dropLast(1).all { it.signum == 0 }) return Test.Radius(matrix, Expr.ZERO, Decimal.Zero, true)
        val (lo, hi) = spectralBracket(p.map { Ball.of(it, 256) }) ?: throw CalcException(Failure.UNSOLVED)
        val ball = Ball.span(lo, hi)
        val digits = digitsOf(ball) ?: throw CalcException(Failure.UNSOLVED)
        // The certified bracket agrees with the exact test.
        if (below && lo >= BigFloat.ONE || !below && hi < BigFloat.ONE) throw CalcException(Failure.UNSOLVED)
        val exact = exactRadius(matrix)?.takeIf { e -> within(e, lo, hi) }
        return Test.Radius(matrix, exact, digits, below)
    }

    /** max |λ| over T's eigenvalues where they are known exactly. */
    private fun exactRadius(m: Expr.Mat): Expr? {
        // Within a time of its own: the exact eigenvalues of a 4 × 4 Gauss–Seidel matrix took three seconds and stopped the whole
        // calculation, whose certified radius needs none of them.
        val eigen = try { Budget.within(EXACT_RADIUS_MS) { LinAlg.eigen(m) } } catch (e: CalcException) {
            if (e.failure == Failure.TOO_LONG && Budget.expired()) throw e
            null
        } ?: return null
        val moduli = eigen.map { e -> modulus(e.value) ?: return null }
        return moduli.maxByOrNull { Numeric.value(it) ?: return null }?.takeIf { MathCalc.niceExact(it) || it is Expr.Num }
    }

    /** The time the exact eigenvalues of an iteration matrix may take. */
    private const val EXACT_RADIUS_MS = 400L

    /** |z| of an exact number: √(a² + b²) where it is complex. */
    internal fun modulus(z: Expr): Expr? {
        if (!z.hasI()) return Calculus.tidy(fn(Func.ABS, z))
        val (re, im) = Complex.parts(z) ?: return null
        return Calculus.tidy(sqrt(add(pow(re, Expr.TWO), pow(im ?: Expr.ZERO, Expr.TWO))))
    }

    /** Whether the exact constant [e] lies in [lo, hi] (within rounding), in ball arithmetic. */
    internal fun within(e: Expr, lo: BigFloat, hi: BigFloat): Boolean = try {
        val z = Precise.eval(e, 256, false, emptyMap()).re
        val slack = hi.abs().shl(-200).let { if (it.isZero) BigFloat.ONE.shl(-200) else it }
        z.lower >= lo - slack && z.upper <= hi + slack
    } catch (x: CalcException) {
        if (x.failure == Failure.TOO_LONG) throw x
        false
    } catch (x: Imprecise) {
        false
    } catch (x: ArithmeticException) {
        false
    }

    // ------------------------------------------------------------------------------------------------------------------
    // The characteristic polynomial and the Schur–Cohn test.

    /** det(λI − T) by Faddeev–LeVerrier in fractions: its coefficients, c[k] of λᵏ (c[n] = 1). */
    internal fun characteristic(t: List<List<Rational>>): List<Rational> {
        val n = t.size
        val c = MutableList(n + 1) { Rational.ZERO }
        c[n] = Rational.ONE
        var m = List(n) { List(n) { Rational.ZERO } }
        for (k in 1..n) {
            Budget.check()
            // M_k = T·M_(k−1) + c_(n−k+1)·I, c_(n−k) = −tr(T·M_k)/k.
            val tm = times(t, m)
            m = tm.mapIndexed { i, r -> r.mapIndexed { j, v -> if (i == j) v + c[n - k + 1] else v } }
            val product = times(t, m)
            val tr = product.indices.fold(Rational.ZERO) { acc, i -> acc + product[i][i] }
            c[n - k] = -(tr / Rational.of(k.toLong()))
        }
        return c
    }

    private fun times(a: List<List<Rational>>, b: List<List<Rational>>): List<List<Rational>> = List(a.size) { i ->
        List(b[0].size) { j ->
            var s = Rational.ZERO
            for (k in b.indices) if (a[i][k].signum != 0 && b[k][j].signum != 0) s += a[i][k] * b[k][j]
            s
        }
    }

    /**
     * Whether every root of the polynomial with coefficients [c] (c[k] of zᵏ, the last not 0) lies inside the unit circle,
     * |z| < 1, decided exactly by the Schur–Cohn test: where |c₀| < |cₙ|, p has all its roots inside exactly when
     * (cₙ·p(z) − c₀·p*(z))/z does, p* the reversed polynomial; where |c₀| ≥ |cₙ|, the product of the roots shows one outside
     * or on the circle (Henrici, Applied and Computational Complex Analysis I, §6.8).
     */
    internal fun allInsideUnitCircle(c: List<Rational>): Boolean {
        var p = c.dropLastWhile { it.signum == 0 }
        while (p.size > 1) {
            Budget.check()
            val n = p.size - 1
            val an = p[n]
            val a0 = p[0]
            if (a0.abs() >= an.abs()) return false
            p = primitive((1..n).map { k -> an * p[k] - a0 * p[n - k] })
        }
        return true
    }

    /** Coefficients scaled to whole numbers without a common factor: the same roots, smaller numbers. */
    private fun primitive(c: List<Rational>): List<Rational> {
        var lcm = BigInteger.ONE
        for (r in c) lcm = lcm.multiply(r.den).divide(lcm.gcd(r.den))
        val whole = c.map { it.num.multiply(lcm).divide(it.den) }
        var gcd = BigInteger.ZERO
        for (w in whole) gcd = gcd.gcd(w)
        if (gcd.signum() == 0) return c
        return whole.map { Rational.of(it.divide(gcd)) }
    }

    /** The same test in ball arithmetic for the roots inside |z| < r ([c] scaled by rᵏ): true, false, or null where it cannot decide. */
    private fun insideRadius(c: List<Ball>, r: BigFloat, prec: Int): Boolean? {
        var power = Ball.ONE
        var p = c.map { k -> k.mul(power, prec).also { power = power.mul(Ball(r), prec) } }
        while (p.size > 1) {
            Budget.check()
            val n = p.size - 1
            val an = p[n]
            val a0 = p[0]
            when {
                absUp(a0) < absDown(an) -> {}
                absDown(a0) >= absUp(an) -> return false
                else -> return null
            }
            // Scaled by 1/|cₙ|² so the numbers stay of their size.
            p = (1..n).map { k -> an.mul(p[k], prec).sub(a0.mul(p[n - k], prec), prec) }
            val top = absUp(p.last())
            if (!top.isZero && (top.top > 64 || top.top < -64)) {
                val s = Ball(BigFloat.twoTo(-top.top))
                p = p.map { it.mul(s, prec) }
            }
        }
        return true
    }

    /**
     * Certified bounds lo ≤ ρ < hi of the largest |root| of the monic polynomial with coefficients [c], by bisection with the
     * Schur–Cohn test in balls, to about 2⁻⁵⁰ of ρ; null where the balls cannot tell even the first halves apart.
     */
    internal fun spectralBracket(c: List<Ball>): Pair<BigFloat, BigFloat>? {
        val n = c.size - 1
        // Cauchy's bound: every root has |z| < 1 + max |cₖ| (c monic).
        var bound = BigFloat.ONE
        for (k in 0 until n) bound = maxOf(bound, absUp(c[k]))
        var lo = BigFloat.ZERO
        var hi = (bound + BigFloat.ONE).round(64, Round.CEILING)
        val prec = 256
        if (insideRadius(c, hi, prec) != true) return null
        for (step in 0 until 200) {
            Budget.check()
            if (!lo.isZero && (hi - lo) <= hi.shl(-50)) break
            if (lo.isZero && hi.top < -1100) break
            // A root right at the middle (a dyadic one, 1/4) leaves the test undecided there: a little to either side it is not.
            val half = (lo + hi).shl(-1)
            val nudge = (hi - lo).shl(-6)
            val (mid, inside) = listOf(half, half + nudge, half - nudge).asSequence()
                .map { it to insideRadius(c, it, prec) }.firstOrNull { it.second != null } ?: break
            if (inside == true) hi = mid else lo = mid
        }
        return lo to hi
    }
}

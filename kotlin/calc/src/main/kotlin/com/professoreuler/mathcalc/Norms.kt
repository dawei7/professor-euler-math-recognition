package com.professoreuler.mathcalc

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import kotlin.math.abs

/**
 * Norms of vectors and matrices and the condition number of a square matrix, cond(A) = ‖A‖·‖A⁻¹‖, in a norm the call states:
 * the 1-norm (the largest column sum of |aᵢⱼ|; of a vector Σ|vᵢ|), the 2-norm (the largest singular value √λmax(AᵀA); of a
 * vector its length), the ∞-norm (the largest row sum; of a vector max|vᵢ|) and the Frobenius norm √(Σ aᵢⱼ²); of a vector also
 * the p-norm (Σ|vᵢ|ᵖ)^(1/p) of a whole p ≥ 3. The 1-, ∞- and Frobenius norms are exact for every matrix of real numbers; the
 * 2-norm of a matrix of fractions is exact where the eigenvalues of AᵀA are (always for 2 × 2), and its digits are certified by
 * the Schur–Cohn test of AᵀA's characteristic polynomial in ball arithmetic, which also checks the exact form. Every exact value
 * is compared with the definition worked out in decimals.
 */
object Norms {
    /** The norms of a matrix a call may ask for (as LaTeX): 1, 2, ∞ and F (Frobenius). */
    val MATRIX = setOf("1", "2", "\\infty", "F")

    internal fun work(statements: List<Statement>, c: Choice): Iterative.Work {
        val s = statements.singleOrNull() ?: throw CalcException(Failure.ARGUMENTS)
        if (s.relations.isNotEmpty() || s.sides.size != 1 || s.sides[0] == null) throw CalcException(Failure.ARGUMENTS)
        val m = Calculus.evaluate(s.sides[0]!!) as? Expr.Mat ?: throw CalcException(Failure.ARGUMENTS)
        // The norm: 1, 2, ∞ or F, and for a vector's norm also a whole p ≥ 3.
        val p = c.point ?: "2"
        if (p !in MATRIX && (c.mode == Mode.CONDITION || m.columns != 1 || wholeP(p) == null)) throw CalcException(Failure.ARGUMENTS)
        return Iterative.Work(listOf(c)) { chosen ->
            val q = chosen.point ?: "2"
            if (chosen.mode == Mode.NORM) norm(m, q) else condition(m, q)
        }
    }

    /** A whole p ≥ 3 written as the norm of a vector, or null. */
    private fun wholeP(p: String): Int? = p.toIntOrNull()?.takeIf { it in 3..100 }

    // ------------------------------------------------------------------------------------------------------------------
    // Norms.

    /** ‖[m]‖ in the norm [p]: of a vector (one column) the vector norm, of a matrix the norm the vector norm induces (F: Frobenius). */
    fun norm(m: Expr.Mat, p: String): Outcome {
        if (m.hasI()) throw CalcException(Failure.UNSOLVED)
        if (m.columns == 1) return vectorNorm(m.rows.map { it[0] }, p)
        if (p !in MATRIX) throw CalcException(Failure.ARGUMENTS)
        numbers(m)
        return when (p) {
            "2" -> spectral(m).let { (exact, ball) -> outcome(exact, ball) }
            else -> outcome(checked(matrixNorm(m, p), doubleNorm(m, p)), null)
        }
    }

    /** cond(A) = ‖A‖·‖A⁻¹‖ of a square matrix in the norm [p]; a singular matrix has none (its condition number is ∞). */
    fun condition(m: Expr.Mat, p: String): Outcome {
        if (p !in MATRIX) throw CalcException(Failure.ARGUMENTS)
        if (m.size != m.columns) throw CalcException(Failure.SIZES)
        if (m.hasI()) throw CalcException(Failure.UNSOLVED)
        numbers(m)
        if (LinAlg.isNull(LinAlg.det(m))) throw CalcException(Failure.SINGULAR)
        if (p == "2") {
            val a = fractions(m)
            val ata = gram(a)
            val c = Iterative.characteristic(ata)
            // λmax of AᵀA, and 1/λmin as the largest root of the reversed polynomial (the eigenvalues of (AᵀA)⁻¹).
            val big = bracket(c)
            val n = c.size - 1
            val reversed = (0..n).map { k -> c[n - k] / c[0] }
            val small = bracket(reversed)
            val ball = BallMath.sqrt(big.mul(small, PREC), PREC)
            return outcome(exactCondition(m, a, ata), ball)
        }
        val inverse = LinAlg.inverse(m)
        val exact = Calculus.tidy(mul(matrixNorm(m, p), matrixNorm(inverse, p)))
        val d = doubleNorm(m, p) * doubleNorm(inverse, p)
        return outcome(checked(exact, d), null)
    }

    /** A vector's norm: exact; with letters only its length (the 2-norm). */
    private fun vectorNorm(v: List<Expr>, p: String): Outcome {
        val letters = v.any { it.symbols().isNotEmpty() }
        val whole = wholeP(p)
        if (p !in MATRIX && whole == null) throw CalcException(Failure.ARGUMENTS)
        if (letters) {
            if (p != "2" && p != "F") throw CalcException(Failure.UNSOLVED)
            return Outcome.Expression(LinAlg.norm(LinAlg.column(v)))
        }
        if (v.any { Numeric.value(it)?.isFinite() != true }) throw CalcException(Failure.UNSOLVED)
        val doubles = v.map { abs(Numeric.value(it)!!) }
        val exact = when {
            p == "1" -> Calculus.tidy(add(v.map { absolute(it) }))
            p == "2" || p == "F" -> LinAlg.norm(LinAlg.column(v))
            p == "\\infty" -> largest(v.map { absolute(it) })
            else -> Calculus.tidy(pow(add(v.map { pow(absolute(it), num(whole!!.toLong())) }), num(Rational.of(BigInteger.ONE, BigInteger.valueOf(whole!!.toLong())))))
        }
        val d = when {
            p == "1" -> doubles.sum()
            p == "2" || p == "F" -> kotlin.math.sqrt(doubles.sumOf { it * it })
            p == "\\infty" -> doubles.max()
            else -> Math.pow(doubles.sumOf { Math.pow(it, whole!!.toDouble()) }, 1.0 / whole!!)
        }
        return outcome(checked(exact, d), null)
    }

    /** The 1-, ∞- or Frobenius norm of a matrix of real numbers, exactly, for the steps. */
    internal fun exactNorm(m: Expr.Mat, p: String): Expr = matrixNorm(m, p)

    /** The 1-, ∞- or Frobenius norm of a matrix of real numbers, exactly. */
    private fun matrixNorm(m: Expr.Mat, p: String): Expr = when (p) {
        "1" -> largest((0 until m.columns).map { j -> Calculus.tidy(add(m.rows.map { absolute(it[j]) })) })
        "\\infty" -> largest(m.rows.map { r -> Calculus.tidy(add(r.map { absolute(it) })) })
        else -> Calculus.tidy(sqrt(add(m.rows.flatten().map { pow(it, Expr.TWO) })))
    }

    /** The same norm worked out in decimals, from the definition. */
    private fun doubleNorm(m: Expr.Mat, p: String): Double {
        val d = m.rows.map { r -> r.map { Numeric.value(it) ?: throw CalcException(Failure.UNSOLVED) } }
        return when (p) {
            "1" -> (0 until m.columns).maxOf { j -> d.sumOf { abs(it[j]) } }
            "\\infty" -> d.maxOf { r -> r.sumOf { abs(it) } }
            else -> kotlin.math.sqrt(d.flatten().sumOf { it * it })
        }
    }

    private fun absolute(e: Expr): Expr = Calculus.tidy(fn(Func.ABS, e))

    /** The largest of exact numbers, compared in ball arithmetic. */
    private fun largest(values: List<Expr>): Expr {
        var best = values[0]
        for (v in values.drop(1)) {
            val s = Iterative.signOf(Calculus.tidy(v - best)) ?: throw CalcException(Failure.UNSOLVED)
            if (s > 0) best = v
        }
        return best
    }

    /** An exact value whose decimal agrees with [d], the definition worked out in decimals; otherwise it is not shown. */
    private fun checked(exact: Expr, d: Double): Expr {
        val v = Numeric.value(exact) ?: throw CalcException(Failure.UNSOLVED)
        if (abs(v - d) > 1e-9 * maxOf(1.0, abs(d))) throw CalcException(Failure.UNSOLVED)
        return exact
    }

    /** Every entry a real number. */
    private fun numbers(m: Expr.Mat) {
        if (m.symbols().isNotEmpty()) throw CalcException(Failure.UNSOLVED)
        if (m.rows.any { r -> r.any { Numeric.value(it)?.isFinite() != true } }) throw CalcException(Failure.UNSOLVED)
    }

    /** The entries as fractions: the 2-norm of a matrix is worked out for fractions only. */
    private fun fractions(m: Expr.Mat): List<List<Rational>> = m.rows.map { r -> r.map { rational(it) ?: throw CalcException(Failure.UNSOLVED) } }

    private fun gram(a: List<List<Rational>>): List<List<Rational>> {
        val n = a[0].size
        return List(n) { i -> List(n) { j -> a.indices.fold(Rational.ZERO) { s, k -> s + a[k][i] * a[k][j] } } }
    }

    // ------------------------------------------------------------------------------------------------------------------
    // The 2-norm.

    private const val PREC = 256

    /** The largest root of the monic polynomial [c] (all its roots real and ≥ 0: AᵀA's), as a certified ball. */
    private fun bracket(c: List<Rational>): Ball {
        if (c.dropLast(1).all { it.signum == 0 }) return Ball.ZERO
        val (lo, hi) = Iterative.spectralBracket(c.map { Ball.of(it, PREC) }) ?: throw CalcException(Failure.UNSOLVED)
        return Ball.span(lo, hi)
    }

    /** ‖A‖₂ = σmax = √λmax(AᵀA): exact where it can be (of a symmetric A its largest |λ|), and certified as a ball. */
    internal fun spectral(m: Expr.Mat): Pair<Expr?, Ball> {
        val a = fractions(m)
        val ata = gram(a)
        val ball = BallMath.sqrt(bracket(Iterative.characteristic(ata)), PREC)
        val exact = (if (a.size > 2 && a.size == a[0].size && symmetric(a)) eigenModuli(m)?.let { largest(it) } else null)
            ?: largestEigenvalue(ata)?.let { Calculus.tidy(sqrt(it)) }
        return exact?.takeIf { Iterative.within(it, ball.lower, ball.upper) } to ball
    }

    private fun symmetric(a: List<List<Rational>>) = a.indices.all { i -> a.indices.all { j -> a[i][j] == a[j][i] } }

    /** |λ| of every eigenvalue of [m] (each with its multiplicity's place once), where all are found exactly. */
    private fun eigenModuli(m: Expr.Mat): List<Expr>? {
        val eigen = try { LinAlg.eigen(m) } catch (e: CalcException) {
            if (e.failure == Failure.TOO_LONG) throw e
            null
        } ?: return null
        return eigen.map { Iterative.modulus(it.value) ?: return null }
    }

    /** The largest eigenvalue of the symmetric AᵀA, exactly: by the quadratic formula for 2 × 2, else where all are found. */
    private fun largestEigenvalue(ata: List<List<Rational>>): Expr? {
        if (ata.size == 2) {
            val t = ata[0][0] + ata[1][1]
            val d = ata[0][0] * ata[1][1] - ata[0][1] * ata[1][0]
            return Calculus.tidy(mul(num(Rational.of(BigInteger.ONE, BigInteger.valueOf(2))), add(num(t), sqrt(num(t * t - Rational.of(4) * d)))))
        }
        val eigen = try { LinAlg.eigen(Expr.Mat(ata.map { r -> r.map { num(it) } })) } catch (e: CalcException) {
            if (e.failure == Failure.TOO_LONG) throw e
            null
        } ?: return null
        if (eigen.any { it.value.hasI() }) return null
        return largest(eigen.map { it.value })
    }

    /**
     * cond₂(A) exactly: λ₊/|det A| for 2 × 2 (λ₊λ₋ = det²); for a symmetric A max|λ|/min|λ| of its own eigenvalues; else
     * √(λmax/λmin) where every eigenvalue of AᵀA is found.
     */
    private fun exactCondition(m: Expr.Mat, a: List<List<Rational>>, ata: List<List<Rational>>): Expr? {
        if (a.size > 2 && symmetric(a)) {
            val moduli = eigenModuli(m) ?: return null
            val small = moduli.reduce { s, v -> if ((Iterative.signOf(Calculus.tidy(v - s)) ?: return null) < 0) v else s }
            return Algebra.rationalize(Calculus.tidy(largest(moduli) / small))
        }
        if (a.size == 2) {
            val lambda = largestEigenvalue(ata) ?: return null
            val det = (a[0][0] * a[1][1] - a[0][1] * a[1][0]).abs()
            return Calculus.tidy(lambda / num(det))
        }
        val eigen = try { LinAlg.eigen(Expr.Mat(ata.map { r -> r.map { num(it) } })) } catch (e: CalcException) {
            if (e.failure == Failure.TOO_LONG) throw e
            null
        } ?: return null
        if (eigen.any { it.value.hasI() }) return null
        val values = eigen.map { it.value }
        val big = largest(values)
        val small = values.reduce { s, v -> if ((Iterative.signOf(Calculus.tidy(v - s)) ?: return null) < 0) v else s }
        return Calculus.tidy(sqrt(big / small))
    }

    // ------------------------------------------------------------------------------------------------------------------
    // The outcome.

    /**
     * A norm's value: exact where it is known ([exact], checked inside [ball] where there is one), else the certified decimal
     * of [ball] to ten digits (as Expr.Real, written with ≈); declined where neither is certain.
     */
    private fun outcome(exact: Expr?, ball: Ball?): Outcome {
        if (exact != null && (ball == null || Iterative.within(exact, ball.lower, ball.upper))) {
            if (exact is Expr.Num) return Outcome.Value(Num.Exact(exact.value))
            val d = Precise.double(exact) ?: throw CalcException(Failure.UNSOLVED)
            val shown = Algebra.rationalize(exact)
            return Outcome.Value(Num.Approx(d), exact = if (MathCalc.niceExact(shown)) shown else Expr.Real(d))
        }
        ball ?: throw CalcException(Failure.UNSOLVED)
        val d = certifiedDouble(ball) ?: throw CalcException(Failure.UNSOLVED)
        return Outcome.Value(Num.Approx(d), exact = Expr.Real(d))
    }

    /** A double whose ten digits every number of [ball] rounds to, or null where the ball is too wide for them. */
    private fun certifiedDouble(ball: Ball): Double? {
        val digits = Precise.decimal(ball, Format.DIGITS) as? Decimal.Digits ?: return null
        var shown = BigDecimal(BigInteger(digits.digits), digits.digits.length - 1 - digits.exponent)
        if (digits.negative) shown = shown.negate()
        val d = ball.mid.toDouble()
        return d.takeIf { BigDecimal(it).round(MathContext(Format.DIGITS)).compareTo(shown) == 0 }
    }
}

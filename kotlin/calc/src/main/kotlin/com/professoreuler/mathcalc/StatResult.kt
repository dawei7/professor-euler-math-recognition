package com.professoreuler.mathcalc

/** A test's alternative hypothesis H₁: μ ≠ μ₀, μ < μ₀ or μ > μ₀ (for two samples μ₁ − μ₂ against 0). */
enum class Alternative { TWO_SIDED, LESS, GREATER }

/** A value a statistical procedure finds ([StatRow]). The apps put each name in words of their own. */
enum class Quantity {
    /** The number of values n (of sample [StatRow.group] where there are two). */
    N,
    /** The sample mean x̄. */
    MEAN,
    /** The sample standard deviation s, with n − 1. */
    SD,
    /** The population's standard deviation σ, known: a z test's and a z interval's. */
    SIGMA,
    /** The standard error of the estimate: σ/√n, s/√n, √(s₁²/n₁ + s₂²/n₂), √(p̂(1 − p̂)/n). */
    STANDARD_ERROR,
    /** The test statistic: z, t or χ², as the procedure ([StatResult.mode]) has it. */
    STATISTIC,
    /** The degrees of freedom. */
    DF,
    /** The p-value. */
    P_VALUE,
    /** The significance level α. */
    ALPHA,
    /** The critical value: of a test at α (± it for a two-sided one), of an interval at its level. */
    CRITICAL,
    /** The confidence level. */
    LEVEL,
    /** The margin of error: the critical value times the standard error. */
    MARGIN,
    /** A confidence interval's lower end. */
    LOWER,
    /** A confidence interval's upper end. */
    UPPER,
    /** The number of successes k. */
    SUCCESSES,
    /** The sample proportion p̂ = k/n. */
    PROPORTION,
    /** Wilson's score interval for a proportion: its lower end. */
    WILSON_LOWER,
    /** Wilson's score interval: its upper end. */
    WILSON_UPPER,
    /** The number of categories of a goodness-of-fit test. */
    CATEGORIES,
    /** The rows of a contingency table. */
    ROWS,
    /** The columns of a contingency table. */
    COLUMNS,
    /** A coefficient of a fitted curve; its letter is [StatRow.symbol]. */
    COEFFICIENT,
    /** The coefficient of determination R² = 1 − SS_res/SS_tot of a fit, on the y as given. */
    R_SQUARED,
    /** The sum of the squared residuals SS_res = Σ(yᵢ − ŷᵢ)². */
    RESIDUAL_SUM,
}

/** One value of a [StatResult]: what it is, its value (exact, or a certified decimal), the sample it belongs to (1 or 2, else 0), a coefficient's letter. */
data class StatRow(val quantity: Quantity, val value: Expr, val group: Int = 0, val symbol: String? = null)

/** What the learner should know about a result's validity. */
enum class StatWarning {
    /** An expected count below 5: the χ² distribution of the statistic is a rough approximation. */
    SMALL_EXPECTED,
    /** Fewer than 5 successes or failures: the Wald interval p̂ ± z·√(p̂(1 − p̂)/n) is unreliable, Wilson's is better. */
    FEW_SUCCESSES,
}

/**
 * What a statistical procedure found: a hypothesis test, a confidence interval or a fitted curve ([Inference], [Fits]). The apps show
 * [rows] as a table of named values in their order, and below it what the procedure has: a test's decision, an interval on a number
 * line, a fit's points and curve with its residuals, a χ² test's expected counts.
 */
data class StatResult(
    /** The function it came from (Mode.T_TEST, Mode.FIT_EXPONENTIAL …): which procedure it is. */
    val mode: Mode,
    /** The values found, in the order they are shown. */
    val rows: List<StatRow>,
    /** A test's alternative hypothesis (null for intervals and fits, and for the χ² tests, which are always upper-tailed). */
    val alternative: Alternative? = null,
    /** A test's value under H₀: μ₀, or 0 for the difference μ₁ − μ₂; null for the χ² tests. */
    val nullValue: Expr? = null,
    /** A test's decision at its level α: true where H₀ is rejected (p < α), false where it is not. */
    val reject: Boolean? = null,
    /** A confidence interval: its lower and upper end. */
    val interval: Pair<Expr, Expr>? = null,
    /** A fit: the curve y = model in x. */
    val model: Expr? = null,
    /** A fit: the points, their x and their y. */
    val xs: List<Expr> = emptyList(),
    val ys: List<Expr> = emptyList(),
    /** A fit: the fitted values ŷᵢ and the residuals yᵢ − ŷᵢ, point by point. */
    val fitted: List<Expr> = emptyList(),
    val residuals: List<Expr> = emptyList(),
    /** A χ² test: the expected counts (a column for goodness of fit, a table for independence). */
    val expected: Expr.Mat? = null,
    /** What the learner should know about the result's validity. */
    val warnings: List<StatWarning> = emptyList(),
) {
    /** The value of the first row of [quantity] (of [group]), if there is one. */
    fun value(quantity: Quantity, group: Int = 0): Expr? = rows.firstOrNull { it.quantity == quantity && it.group == group }?.value
}

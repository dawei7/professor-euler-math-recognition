package com.professoreuler.mathcalc

import java.math.BigDecimal
import java.math.BigInteger

/**
 * Financial mathematics as school and first-year business mathematics teach it: simple and compound interest (once, m times a
 * period, continuously), present values, the rate and the time of a growth, effective and nominal rates, annuities paid at the
 * end or at the start of each period, the payment and the term of an annuity loan, the net present value and the internal rate
 * of return of cash flows, and the tables of an annuity loan, an equal-principal loan and of straight-line and declining-balance
 * depreciation.
 *
 * A rate is a decimal fraction per period, 0.05, or the same written with the percent sign, 5% (the parser reads 5% as 1/20):
 * one number, read one way. A rate of 100 % or more per period is refused ([Failure.ARGUMENTS]), because 5 typed for 5 % would
 * otherwise silently be 500 %. The value functions are worked out where they are written ([Discrete.call] hands them here), so
 * they go into formulas, `solve` and `plot`: with a letter for an argument they are their textbook formula. Every number is
 * exact (a fraction, a root, a logarithm, a power of e) and checked another way before it is shown: by compounding period after
 * period, by adding the payments one by one, by putting the rate, the time or the payment back. The tables round every amount to
 * the cent (half away from zero), as a bank does: each row adds up exactly, and the last payment or depreciation takes what
 * rounding left over, so the debt ends at exactly 0.00 and the book value at exactly the salvage value.
 */
object Finance {
    // ---------------------------------------------------------------------------------------------------------------------
    // The value functions.

    private const val MANY = 10_000

    /** The value functions by name, and how many arguments each takes. */
    val NAMES: Map<String, IntRange> = mapOf(
        "simpleinterest" to 3..3,
        "futurevalue" to 3..4, "presentvalue" to 3..4,
        "interestrate" to 3..4, "periods" to 3..4,
        "effectiverate" to 2..2, "nominalrate" to 2..2,
        "annuityfv" to 3..3, "annuitypv" to 3..3, "annuityduefv" to 3..3, "annuityduepv" to 3..3,
        "loanpayment" to 3..3, "loanterm" to 3..3,
        "npv" to 2..MANY,
    )

    /** What an argument is: its numbers are checked by its role before anything is worked out. */
    private enum class Role { AMOUNT, POSITIVE, RATE, TIME, COUNT, TIMES }

    private val ROLES: Map<String, List<Role>> = mapOf(
        "simpleinterest" to listOf(Role.AMOUNT, Role.RATE, Role.TIME),
        "futurevalue" to listOf(Role.AMOUNT, Role.RATE, Role.TIME, Role.TIMES),
        "presentvalue" to listOf(Role.AMOUNT, Role.RATE, Role.TIME, Role.TIMES),
        "interestrate" to listOf(Role.POSITIVE, Role.POSITIVE, Role.TIME, Role.TIMES),
        "periods" to listOf(Role.POSITIVE, Role.POSITIVE, Role.RATE, Role.TIMES),
        "effectiverate" to listOf(Role.RATE, Role.TIMES),
        "nominalrate" to listOf(Role.RATE, Role.TIMES),
        "annuityfv" to listOf(Role.AMOUNT, Role.RATE, Role.COUNT),
        "annuitypv" to listOf(Role.AMOUNT, Role.RATE, Role.COUNT),
        "annuityduefv" to listOf(Role.AMOUNT, Role.RATE, Role.COUNT),
        "annuityduepv" to listOf(Role.AMOUNT, Role.RATE, Role.COUNT),
        "loanpayment" to listOf(Role.AMOUNT, Role.RATE, Role.COUNT),
        "loanterm" to listOf(Role.POSITIVE, Role.RATE, Role.POSITIVE),
    )

    private fun roleOf(name: String, k: Int): Role = if (name == "npv") (if (k == 0) Role.RATE else Role.AMOUNT) else ROLES.getValue(name)[k]

    /** The most payments an annuity, a loan or a table takes: a hundred years of monthly payments. */
    const val MOST_PERIODS = 1200

    /** The most compounding periods worked out exactly one by one in a check. */
    private const val MOST_STEPS = 100_000

    /**
     * [name] at [args]: its value where the arguments are numbers, its formula where some are letters; null where [name] is no
     * function of this file. Numbers outside an argument's range fail: a rate of 100 % or more ([Failure.ARGUMENTS]), a
     * negative time, a count that is no whole number ([Failure.UNDEFINED]). A result that fails its check is not shown
     * ([Failure.UNSOLVED]).
     */
    fun call(name: String, args: List<Expr>): Expr? {
        val arity = NAMES[name] ?: return null
        if (args.size !in arity) throw CalcException(Failure.ARGUMENTS)
        if (args.any { it is Expr.Mat || it.hasI() || it.hasOp() }) throw CalcException(Failure.ARGUMENTS)
        // Worked out while the formula is read, before the calculation's time starts: bounded by a time of its own.
        return Budget.bounded(MathCalc.LIMIT_MS) {
            Budget.check()
            args.forEachIndexed { k, a -> checkRole(roleOf(name, k), a) }
            val result = formula(name, args)
            if (!checked(name, args, result)) throw CalcException(Failure.UNSOLVED)
            result
        }
    }

    /** The number [a] in its [role]'s range; letters pass (the formula is the answer then). */
    private fun checkRole(role: Role, a: Expr) {
        if (a.symbols().isNotEmpty()) return
        if (role == Role.TIMES) {
            if (a == Expr.INF) return
            val m = integer(a) ?: throw CalcException(Failure.UNDEFINED)
            if (m.signum() <= 0) throw CalcException(Failure.UNDEFINED)
            if (m > BigInteger.valueOf(1_000_000)) throw CalcException(Failure.TOO_LARGE)
            return
        }
        if (a.hasInfinity()) throw CalcException(Failure.UNDEFINED)
        val v = Numeric.value(a) ?: throw CalcException(Failure.UNDEFINED)
        when (role) {
            Role.AMOUNT -> {}
            Role.POSITIVE -> if (!(v > 0)) throw CalcException(Failure.UNDEFINED)
            Role.RATE -> if (!(v > -1 && v < 1) || rateOutside(a)) throw CalcException(Failure.ARGUMENTS)
            Role.TIME -> if (v < 0) throw CalcException(Failure.UNDEFINED)
            Role.COUNT -> {
                val n = integer(a) ?: throw CalcException(Failure.UNDEFINED)
                if (n.signum() <= 0) throw CalcException(Failure.UNDEFINED)
                if (n > BigInteger.valueOf(MOST_PERIODS.toLong())) throw CalcException(Failure.TOO_LARGE)
            }
            Role.TIMES -> {}
        }
    }

    /** An exact rate at ±1 exactly (100 %), which a double near it might let through. */
    private fun rateOutside(a: Expr): Boolean {
        val r = rational(a) ?: return false
        return r >= Rational.ONE || r <= -Rational.ONE
    }

    private fun one(r: Expr) = add(Expr.ONE, r)

    /**
     * b^e, kept exact: a whole power of a fraction longer than the canonical form keeps exactly (daily interest for thirty years)
     * is worked out here, as the fraction it is; beyond [MOST_BITS] bits it is too large ([Failure.TOO_LARGE]).
     */
    private fun power(b: Expr, e: Expr): Expr {
        val rb = rational(b)
        val n = integer(e)
        if (rb != null && n != null && n.bitLength() < 31 && rb.signum != 0) {
            val bits = n.abs().toLong() * maxOf(rb.num.bitLength(), rb.den.bitLength())
            if (bits > Rational.MAX_BITS) {
                if (bits > MOST_BITS) throw CalcException(Failure.TOO_LARGE)
                val k = n.abs().toInt()
                val top = rb.num.pow(k)
                val bottom = rb.den.pow(k)
                return Expr.Num(if (n.signum() > 0) Rational.of(top, bottom) else Rational.of(bottom, top))
            }
        }
        return pow(b, e)
    }

    /** The longest whole power of a fraction worked out exactly, in bits of its numerator or denominator. */
    private const val MOST_BITS = 400_000L

    private fun isNumberZero(e: Expr) = isZero(e)

    /** The growth factor of [n] periods at [r], compounded [m] times a period (null: once; ∞: continuously). */
    private fun growth(r: Expr, n: Expr, m: Expr?): Expr = when {
        m == null -> power(one(r), n)
        m == Expr.INF -> exp(mul(r, n))
        else -> power(one(r / m), mul(m, n))
    }

    /** The formula of [name] at [args] (numbers already checked). */
    private fun formula(name: String, a: List<Expr>): Expr {
        val m = a.getOrNull(3)
        return when (name) {
            "simpleinterest" -> mul(a[0], a[1], a[2])
            "futurevalue" -> mul(a[0], growth(a[1], a[2], m))
            "presentvalue" -> mul(a[0], power(growth(a[1], a[2], m), Expr.MINUS_ONE))
            "interestrate" -> {
                val (p, f, n) = a
                if (isNumberZero(n)) throw CalcException(Failure.DIVISION_BY_ZERO)
                val ratio = f / p
                when {
                    m == null -> pow(ratio, pow(n, Expr.MINUS_ONE)) - Expr.ONE
                    m == Expr.INF -> ln(ratio) / n
                    else -> mul(m, pow(ratio, pow(mul(m, n), Expr.MINUS_ONE)) - Expr.ONE)
                }
            }
            "periods" -> {
                val (p, f, r) = a
                if (isNumberZero(r)) throw CalcException(if (rational(f / p) == Rational.ONE) Failure.MANY_SOLUTIONS else Failure.UNDEFINED)
                val n = when {
                    m == null -> ln(f / p) / ln(one(r))
                    m == Expr.INF -> ln(f / p) / r
                    else -> ln(f / p) / mul(m, ln(one(r / m)))
                }
                // The time until: never negative (F below P at a positive rate is never reached).
                if (n.symbols().isEmpty()) {
                    val v = Numeric.value(n) ?: throw CalcException(Failure.UNDEFINED)
                    if (v < 0) throw CalcException(Failure.UNDEFINED)
                }
                n
            }
            "effectiverate" -> growth(a[0], Expr.ONE, a[1]) - Expr.ONE
            "nominalrate" -> {
                val (i, mm) = a
                if (mm == Expr.INF) ln(one(i)) else mul(mm, pow(one(i), pow(mm, Expr.MINUS_ONE)) - Expr.ONE)
            }
            "annuityfv", "annuityduefv" -> {
                val (pay, r, n) = a
                val base = if (isNumberZero(r)) mul(pay, n) else mul(pay, power(one(r), n) - Expr.ONE) / r
                if (name == "annuityduefv") mul(one(r), base) else base
            }
            "annuitypv", "annuityduepv" -> {
                val (pay, r, n) = a
                val base = if (isNumberZero(r)) mul(pay, n) else mul(pay, Expr.ONE - power(one(r), neg(n))) / r
                if (name == "annuityduepv") mul(one(r), base) else base
            }
            "loanpayment" -> {
                val (p, r, n) = a
                if (isNumberZero(r)) p / n else mul(p, r, power(Expr.ONE - power(one(r), neg(n)), Expr.MINUS_ONE))
            }
            "loanterm" -> {
                val (p, r, pay) = a
                if (isNumberZero(r)) {
                    p / pay
                } else {
                    val left = Expr.ONE - mul(r, p) / pay
                    if (left.symbols().isEmpty()) {
                        // The payment must be more than the interest of the first period, or the debt is never repaid.
                        val v = Numeric.value(left) ?: throw CalcException(Failure.UNDEFINED)
                        if (!(v > 0)) throw CalcException(Failure.UNDEFINED)
                    }
                    // n = ln(A/(A − rP))/ln(1 + r): the debt P·qⁿ − A·(qⁿ − 1)/r is 0.
                    ln(pay / (pay - mul(r, p))) / ln(one(r))
                }
            }
            "npv" -> {
                val r = a[0]
                add(a.drop(1).mapIndexed { t, c -> mul(c, power(one(r), num(-t.toLong()))) })
            }
            else -> throw CalcException(Failure.UNREADABLE)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Checks: every value worked out another way.
    /**
     * Whether [result] of [name] at [args] holds, worked out another way: compounded period by period, the payments added one by
     * one, the rate, the time or the payment put back (exactly for fractions, in ball arithmetic otherwise). A formula with
     * letters is compared at random values of its letters with the same quantity built another way, or put back the same way.
     */
    private fun checked(name: String, args: List<Expr>, result: Expr): Boolean = try {
        if (args.any { it.symbols().isNotEmpty() }) symbolicCheck(name, args, result) else numericCheck(name, args, result)
    } catch (e: CalcException) {
        if (e.failure == Failure.TOO_LONG) throw e
        false
    } catch (e: ArithmeticException) {
        false
    }

    /** [a] and [b] are the same number: equal, or within 2⁻¹⁰⁰ of each other relative to their size, in ball arithmetic. */
    internal fun agree(a: Expr, b: Expr): Boolean {
        if (a == b) return true
        val ra = rational(a)
        val rb = rational(b)
        if (ra != null && rb != null) return ra == rb
        val size = maxOf(1.0, kotlin.math.abs(Numeric.value(a) ?: return false))
        // Divided by a power of two near the size, so a large amount is compared relative to itself.
        val scale = Rational.of(BigInteger.ONE.shiftLeft(kotlin.math.ceil(kotlin.math.log2(size)).toInt().coerceIn(0, 4000)))
        val difference = mul(add(a, neg(b)), num(Rational.ONE / scale))
        return isZero(difference) || Precise.nearZero(difference, bits = 100, complex = false) == true
    }

    /** The number [result] checked: worked out another way, or put back into what it solves. */
    private fun numericCheck(name: String, a: List<Expr>, result: Expr): Boolean {
        val m = a.getOrNull(3)
        return when (name) {
            // The rate put back: I/(P·t) is r.
            "simpleinterest" -> mul(a[0], a[2]).let { pt -> if (isZero(pt)) isZero(result) else agree(result / pt, a[1]) }
            "futurevalue" -> agree(result, compounded(a[0], a[1], a[2], m))
            // Grown again over the same time, the present value is the amount.
            "presentvalue" -> agree(compounded(result, a[1], a[2], m), a[0])
            "interestrate" -> agree(compounded(a[0], result, a[2], m), a[1])
            "periods" -> agree(compounded(a[0], a[2], result, m), a[1])
            "effectiverate" -> agree(add(Expr.ONE, result), compounded(Expr.ONE, a[0], Expr.ONE, a[1]))
            // The nominal rate compounded m times gives the effective rate back.
            "nominalrate" -> agree(compounded(Expr.ONE, result, Expr.ONE, a[1]), one(a[0]))
            "annuityfv", "annuityduefv" -> agree(result, payments(a[0], a[1], integer(a[2])!!.toInt(), future = true, due = name == "annuityduefv"))
            "annuitypv", "annuityduepv" -> agree(result, payments(a[0], a[1], integer(a[2])!!.toInt(), future = false, due = name == "annuityduepv"))
            // The payment put back: n payments of it leave no debt.
            "loanpayment" -> debtAfter(a[0], a[1], result, integer(a[2])!!.toInt()).let { rest -> isZero(rest) || agree(add(a[0], rest), a[0]) }
            // The term put back: the present value of its payments is the loan.
            "loanterm" -> agree(if (isZero(a[1])) mul(a[2], result) else mul(a[2], Expr.ONE - power(one(a[1]), neg(result))) / a[1], a[0])
            "npv" -> agree(result, horner(a[0], a.drop(1)))
            else -> false
        }
    }

    /** A formula checked at random values of its letters ([Verify.same]) against the same quantity built or put back another way. */
    private fun symbolicCheck(name: String, a: List<Expr>, result: Expr): Boolean {
        val m = a.getOrNull(3)
        fun grown(p: Expr, r: Expr, n: Expr): Expr =
            if (m == Expr.INF) mul(p, exp(mul(r, n))) else mul(p, exp(mul(periodsOf(n, m), ln(one(rateOf(r, m))))))
        return when (name) {
            "simpleinterest" -> Verify.same(result, mul(a[2], mul(a[1], a[0])))
            "futurevalue" -> Verify.same(result, grown(a[0], a[1], a[2]))
            "presentvalue" -> Verify.same(grown(result, a[1], a[2]), a[0])
            "interestrate" -> Verify.same(grown(a[0], result, a[2]), a[1])
            "periods" -> Verify.same(grown(a[0], a[2], result), a[1])
            "effectiverate" -> Verify.same(add(Expr.ONE, result), if (a[1] == Expr.INF) exp(a[0]) else exp(mul(a[1], ln(one(a[0] / a[1])))))
            "nominalrate" -> Verify.same(if (a[1] == Expr.INF) exp(result) else exp(mul(a[1], ln(one(result / a[1])))), one(a[0]))
            // The present value grown over the n periods is the future value, and the other way round.
            "annuityfv", "annuityduefv" -> Verify.same(result, mul(formula(name.replace("fv", "pv"), a), power(one(a[1]), a[2])))
            "annuitypv", "annuityduepv" -> Verify.same(result, mul(formula(name.replace("pv", "fv"), a), power(one(a[1]), neg(a[2]))))
            "loanpayment" -> Verify.same(result, a[0] / formula("annuitypv", listOf(Expr.ONE, a[1], a[2])))
            "loanterm" -> Verify.same(mul(a[2], Expr.ONE - power(one(a[1]), neg(result))) / a[1], a[0])
            "npv" -> Verify.same(result, horner(a[0], a.drop(1)))
            else -> false
        }
    }

    /** Σ cₜ·(1 + r)⁻ᵗ by Horner's rule on the polynomial in q = 1 + r, divided by qⁿ. */
    private fun horner(r: Expr, flows: List<Expr>): Expr {
        val q = one(r)
        var acc: Expr = Expr.ZERO
        for (c in flows) {
            Budget.check()
            acc = add(mul(acc, q), c)
        }
        return mul(acc, power(q, num(-(flows.size - 1).toLong())))
    }

    private fun rateOf(r: Expr, m: Expr?): Expr = if (m == null || m == Expr.INF) r else r / m
    private fun periodsOf(n: Expr, m: Expr?): Expr = if (m == null || m == Expr.INF) n else mul(m, n)

    /**
     * [p] compounded at [r] for [n] periods, [m] times each ([m] null: once): period by period where that is a whole number of
     * periods of a fraction (each step exact), else e^(periods·ln(1 + rate)), and e^(r·n) for ∞.
     */
    private fun compounded(p: Expr, r: Expr, n: Expr, m: Expr?): Expr {
        if (m == Expr.INF) return mul(p, exp(mul(r, n)))
        val rate = rateOf(r, m)
        val steps = periodsOf(n, m)
        val k = integer(steps)
        val q = rational(one(rate))
        val start = rational(p)
        if (k != null && q != null && start != null && k.signum() >= 0 && k <= BigInteger.valueOf(MOST_STEPS.toLong()) &&
            k.toLong() * maxOf(q.num.bitLength(), q.den.bitLength()) <= 400_000) {
            var num = start.num
            var den = start.den
            for (j in 0 until k.toInt()) {
                if (j % 64 == 0) Budget.check()
                num *= q.num
                den *= q.den
            }
            return Expr.Num(Rational.of(num, den))
        }
        return mul(p, exp(mul(steps, ln(one(rate)))))
    }

    /**
     * [x0] taken [n] times through x ← x·a/b + c, exactly. Kept as N/(L·bᵏ) in whole numbers and reduced once at the end: a
     * fraction reduced at every step (a gcd of long numbers each time) would take seconds for a hundred years of months.
     */
    internal fun affine(x0: Rational, a: BigInteger, b: BigInteger, c: Rational, n: Int): Rational {
        val l = x0.den * c.den
        var top = x0.num * c.den
        val step = c.num * x0.den
        var bk = BigInteger.ONE
        for (k in 1..n) {
            Budget.check()
            bk *= b
            // x_k·L·bᵏ = x_(k−1)·L·bᵏ⁻¹·a + c·L·bᵏ.
            top = top * a + step * bk
        }
        return Rational.of(top, l * bk)
    }

    /** n payments of [pay] added one by one: at the end of each period (or at its start, [due]), to the end or to the start. */
    private fun payments(pay: Expr, r: Expr, n: Int, future: Boolean, due: Boolean): Expr {
        val q = one(r)
        val rq = rational(q)
        val rp = rational(pay)
        if (rq != null && rp != null) {
            // In fractions: S ← S·q + R for the future value, S ← (S + R)/q = S/q + R/q for the present value.
            val s = if (future) affine(Rational.ZERO, rq.num, rq.den, rp, n) else affine(Rational.ZERO, rq.den, rq.num, rp / rq, n)
            return Expr.Num(if (due) s * rq else s)
        }
        val terms = (0 until n).map { j ->
            Budget.check()
            val k = if (future) (if (due) j + 1 else j) else (if (due) -j else -(j + 1))
            mul(pay, power(q, num(k.toLong())))
        }
        return add(terms)
    }

    /** The debt of a loan [p] at [r] after [n] payments of [pay], period by period. */
    private fun debtAfter(p: Expr, r: Expr, pay: Expr, n: Int): Expr {
        val rp = rational(p)
        val rr = rational(r)
        val ra = rational(pay)
        if (rp != null && rr != null && ra != null) {
            val q = Rational.ONE + rr
            val d = affine(rp, q.num, q.den, -ra, n)
            return Expr.Num(d)
        }
        val q = one(r)
        return add(mul(p, power(q, num(n.toLong()))), neg(mul(pay, formula("annuityfv", listOf(Expr.ONE, r, num(n.toLong()))))))
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Tables.

    /** What a table is of. */
    enum class Plan { ANNUITY_LOAN, EQUAL_PRINCIPAL, STRAIGHT_LINE, DECLINING_BALANCE }

    /** A column of a table. */
    enum class Column {
        /** 1, 2, …, n: the period (a loan's) or the year (a depreciation's). */
        PERIOD,
        /** The debt at the start of the period. */
        DEBT_START,
        /** What is paid at the end of the period: interest and principal. */
        PAYMENT,
        INTEREST,
        /** The part of the payment that repays debt. */
        PRINCIPAL,
        /** The debt at the end of the period, after the payment. */
        DEBT_END,
        /** The book value at the start of the year. */
        VALUE_START,
        DEPRECIATION,
        /** The depreciation of all years so far. */
        ACCUMULATED,
        /** The book value at the end of the year. */
        VALUE_END,
    }

    /**
     * A repayment or depreciation table: one row for each period, the period first, then amounts, each a whole number of cents
     * (exact); [totals] the sum of a column where it means something (payments, interest, principal, depreciation), null
     * elsewhere. [regular] is the regular amount as rounded to the cent (the annuity, the principal part, the yearly
     * depreciation; null for declining balance, where there is none), [exact] the same before rounding; [last] the last row's
     * payment (principal, depreciation) where rounding made it differ from [regular]. [rate] is the rate per period.
     */
    class Schedule(
        val plan: Plan,
        val columns: List<Column>,
        val rows: List<List<Rational>>,
        val totals: List<Rational?>,
        val regular: Rational?,
        val exact: Expr?,
        val last: Rational?,
        val start: Rational,
        val rate: Rational,
        val periods: Int,
    )

    /** The table functions' modes. */
    val MODES: Set<Mode> = setOf(Mode.AMORTIZATION, Mode.EQUAL_PRINCIPAL, Mode.STRAIGHT_LINE, Mode.DECLINING_BALANCE, Mode.IRR)

    /** The outcome of a table function or of irr for its list [m] of numbers (a vector, as the call wrote them). */
    internal fun outcome(mode: Mode, m: Expr.Mat): Outcome {
        val values = if (LinAlg.isVector(m)) m.rows.map { it[0] } else if (m.size == 1) m.rows[0] else throw CalcException(Failure.ARGUMENTS)
        if (mode == Mode.IRR) return Outcome.Solved(irr(values))
        if (values.size != 3 || values.any { rational(it) == null }) throw CalcException(Failure.ARGUMENTS)
        val (a, b, c) = values
        val n = integer(c)?.takeIf { it.signum() > 0 } ?: throw CalcException(Failure.UNDEFINED)
        if (n > BigInteger.valueOf(MOST_PERIODS.toLong())) throw CalcException(Failure.TOO_LARGE)
        val schedule = when (mode) {
            Mode.AMORTIZATION -> annuityLoan(cents(a), rateArgument(b), n.toInt())
            Mode.EQUAL_PRINCIPAL -> equalPrincipal(cents(a), rateArgument(b), n.toInt())
            Mode.STRAIGHT_LINE -> straightLine(cents(a), cents(b), n.toInt())
            Mode.DECLINING_BALANCE -> decliningBalance(cents(a), rateArgument(b, positive = true), n.toInt())
            else -> throw CalcException(Failure.UNREADABLE)
        }
        if (!consistent(schedule)) throw CalcException(Failure.UNSOLVED)
        return Outcome.FinanceSchedule(schedule)
    }

    /** An amount of a table: a number of whole cents, exactly (a fraction of a cent is no amount: [Failure.ARGUMENTS]). */
    private fun cents(e: Expr): Rational {
        val r = rational(e) ?: throw CalcException(Failure.ARGUMENTS)
        if (!(r * HUNDRED).isInteger) throw CalcException(Failure.ARGUMENTS)
        return r
    }

    /** A table's rate: a fraction with 0 ≤ r < 1 (0 < r for declining balance); 100 % or more is no rate ([Failure.ARGUMENTS]). */
    private fun rateArgument(e: Expr, positive: Boolean = false): Rational {
        val r = rational(e) ?: throw CalcException(Failure.ARGUMENTS)
        if (r >= Rational.ONE || r <= -Rational.ONE) throw CalcException(Failure.ARGUMENTS)
        if (r.signum < 0 || (positive && r.signum == 0)) throw CalcException(Failure.UNDEFINED)
        return r
    }

    private val HUNDRED = Rational.of(100)

    /** [x] rounded to the cent, half away from zero (commercial rounding). */
    fun roundCents(x: Rational): Rational {
        if (x.signum < 0) return -roundCents(-x)
        val scaled = x.num * BigInteger.valueOf(100)
        // ⌊(2·scaled + den) / (2·den)⌋: the nearest whole number of cents, halves up.
        val c = (scaled * BigInteger.valueOf(2) + x.den) / (x.den * BigInteger.valueOf(2))
        return Rational.of(c, BigInteger.valueOf(100))
    }

    /** An amount with two decimals, as a table shows it: 2820.12, -0.50, 0.00. */
    fun money(x: Rational): String {
        val d = BigDecimal(x.num).divide(BigDecimal(x.den), 2, java.math.RoundingMode.HALF_UP)
        return d.toPlainString()
    }

    private fun rpow(b: Rational, n: Int): Rational {
        if (n.toLong() * maxOf(b.num.bitLength(), b.den.bitLength()) > 400_000) throw CalcException(Failure.TOO_LARGE)
        return Rational.of(b.num.pow(n), b.den.pow(n))
    }

    private val LOAN_COLUMNS = listOf(Column.PERIOD, Column.DEBT_START, Column.PAYMENT, Column.INTEREST, Column.PRINCIPAL, Column.DEBT_END)
    private val ASSET_COLUMNS = listOf(Column.PERIOD, Column.VALUE_START, Column.DEPRECIATION, Column.ACCUMULATED, Column.VALUE_END)

    /**
     * An annuity loan's table: the payment A = P·r/(1 − (1 + r)⁻ⁿ) rounded to the cent; each period the interest on the debt
     * at its start rounded to the cent, the principal A − interest; the last payment the debt left with its interest.
     */
    fun annuityLoan(p: Rational, r: Rational, n: Int): Schedule {
        if (p.signum <= 0) throw CalcException(Failure.UNDEFINED)
        val exact = if (r.signum == 0) p / Rational.of(n.toLong()) else p * r / (Rational.ONE - Rational.ONE / rpow(Rational.ONE + r, n))
        // The exact payment put back: n payments of it leave exactly no debt.
        val q = Rational.ONE + r
        val d = affine(p, q.num, q.den, -exact, n)
        if (d.signum != 0) throw CalcException(Failure.UNSOLVED)
        val a = roundCents(exact)
        val rows = ArrayList<List<Rational>>()
        var debt = p
        for (k in 1..n) {
            if (k % 64 == 0) Budget.check()
            val interest = roundCents(debt * r)
            val principal = if (k < n) a - interest else debt
            val payment = interest + principal
            val end = debt - principal
            if (end.signum < 0 || principal.signum < 0) throw CalcException(Failure.UNDEFINED)
            rows += listOf(Rational.of(k.toLong()), debt, payment, interest, principal, end)
            debt = end
        }
        val last = rows.last()[2].takeIf { it != a }
        return Schedule(Plan.ANNUITY_LOAN, LOAN_COLUMNS, rows, totals(rows, setOf(2, 3, 4)), a, Expr.Num(exact), last, p, r, n)
    }

    /** An equal-principal loan's table: the principal P/n rounded to the cent each period (the last takes the rest), interest on the debt. */
    fun equalPrincipal(p: Rational, r: Rational, n: Int): Schedule {
        if (p.signum <= 0) throw CalcException(Failure.UNDEFINED)
        val exact = p / Rational.of(n.toLong())
        val t = roundCents(exact)
        val rows = ArrayList<List<Rational>>()
        var debt = p
        for (k in 1..n) {
            if (k % 64 == 0) Budget.check()
            val interest = roundCents(debt * r)
            val principal = if (k < n) t else debt
            val end = debt - principal
            if (end.signum < 0 || principal.signum <= 0) throw CalcException(Failure.UNDEFINED)
            rows += listOf(Rational.of(k.toLong()), debt, interest + principal, interest, principal, end)
            debt = end
        }
        val last = rows.last()[4].takeIf { it != t }
        return Schedule(Plan.EQUAL_PRINCIPAL, LOAN_COLUMNS, rows, totals(rows, setOf(2, 3, 4)), t, Expr.Num(exact), last, p, r, n)
    }

    /** Straight-line depreciation: (cost − salvage)/n each year rounded to the cent, the last year to the salvage value exactly. */
    fun straightLine(cost: Rational, salvage: Rational, n: Int): Schedule {
        if (cost.signum <= 0 || salvage.signum < 0 || salvage >= cost) throw CalcException(Failure.UNDEFINED)
        val exact = (cost - salvage) / Rational.of(n.toLong())
        val dep = roundCents(exact)
        val rows = ArrayList<List<Rational>>()
        var value = cost
        var accumulated = Rational.ZERO
        for (k in 1..n) {
            if (k % 64 == 0) Budget.check()
            val d = if (k < n) dep else value - salvage
            if (d.signum < 0) throw CalcException(Failure.UNDEFINED)
            accumulated += d
            rows += listOf(Rational.of(k.toLong()), value, d, accumulated, value - d)
            value -= d
        }
        val last = rows.last()[2].takeIf { it != dep }
        return Schedule(Plan.STRAIGHT_LINE, ASSET_COLUMNS, rows, totals(rows, setOf(2)), dep, Expr.Num(exact), last, cost, Rational.ZERO, n)
    }

    /** Declining-balance depreciation: each year the rate times the book value at its start, rounded to the cent. */
    fun decliningBalance(cost: Rational, d: Rational, n: Int): Schedule {
        if (cost.signum <= 0) throw CalcException(Failure.UNDEFINED)
        val rows = ArrayList<List<Rational>>()
        var value = cost
        var accumulated = Rational.ZERO
        for (k in 1..n) {
            if (k % 64 == 0) Budget.check()
            val dep = roundCents(value * d)
            accumulated += dep
            rows += listOf(Rational.of(k.toLong()), value, dep, accumulated, value - dep)
            value -= dep
        }
        return Schedule(Plan.DECLINING_BALANCE, ASSET_COLUMNS, rows, totals(rows, setOf(2)), null,
            Expr.Num(cost * rpow(Rational.ONE - d, n)), null, cost, d, n)
    }

    private fun totals(rows: List<List<Rational>>, summed: Set<Int>): List<Rational?> =
        rows[0].indices.map { j -> if (j in summed) rows.fold(Rational.ZERO) { s, row -> s + row[j] } else null }

    private fun column(s: Schedule, c: Column): Int = s.columns.indexOf(c)

    /**
     * The table checked as a whole, independently of how it was built: every amount a whole number of cents; each row adds up
     * (payment = interest + principal, end = start − principal, the next row starting where this one ended); each interest the
     * debt times the rate, rounded; the totals the sums of the rows; the debt ending at exactly 0 and the principal adding up
     * to the loan (the book value at the salvage value, the depreciation adding up to cost less salvage); the last payment no
     * further from the regular one than the rounding of all periods can carry it; a declining balance within rounding of
     * C·(1 − d)ⁿ.
     */
    internal fun consistent(s: Schedule): Boolean {
        val cent = Rational.of(BigInteger.ONE, BigInteger.valueOf(100))
        val half = cent / Rational.of(2)
        if (s.rows.size != s.periods) return false
        for (row in s.rows) for (x in row.drop(1)) if (!(x * HUNDRED).isInteger) return false
        val n = s.periods
        for ((j, total) in s.totals.withIndex()) if (total != null && total != s.rows.fold(Rational.ZERO) { acc, r -> acc + r[j] }) return false
        val first = s.rows[0]
        val ord = Rational.of(n.toLong())
        when (s.plan) {
            Plan.ANNUITY_LOAN, Plan.EQUAL_PRINCIPAL -> {
                val start = column(s, Column.DEBT_START)
                val pay = column(s, Column.PAYMENT)
                val interest = column(s, Column.INTEREST)
                val principal = column(s, Column.PRINCIPAL)
                val end = column(s, Column.DEBT_END)
                if (first[start] != s.start) return false
                for ((k, row) in s.rows.withIndex()) {
                    if (row[0] != Rational.of(k + 1L)) return false
                    if (row[pay] != row[interest] + row[principal]) return false
                    if (row[end] != row[start] - row[principal]) return false
                    if (row[interest] != roundCents(row[start] * s.rate)) return false
                    if (k > 0 && row[start] != s.rows[k - 1][end]) return false
                    if (row[end].signum < 0) return false
                }
                if (s.rows.last()[end].signum != 0) return false
                if (s.totals[principal] != s.start) return false
                if (s.totals[pay] != s.totals[interest]!! + s.start) return false
                val q = Rational.ONE + s.rate
                if (s.plan == Plan.ANNUITY_LOAN) {
                    // Every regular payment is the annuity, and the last within the rounding the periods carry: half a cent of
                    // the payment and of each interest, grown to the end (at most a cent times the future value factor).
                    for (row in s.rows.dropLast(1)) if (row[pay] != s.regular) return false
                    val factor = if (s.rate.signum == 0) ord else (rpow(q, n) - Rational.ONE) / s.rate
                    if ((s.rows.last()[pay] - s.regular!!).abs() > cent * factor * q) return false
                    if ((s.regular - (rational(s.exact!!) ?: return false)).abs() > half) return false
                } else {
                    for (row in s.rows.dropLast(1)) if (row[principal] != s.regular) return false
                    // The interest of the exact plan, r·P·(n + 1)/2, within rounding: half a cent each period, and the rate on
                    // the debt that the rounded principal moved, at most (k − 1)·half a cent in period k.
                    val exactInterest = s.rate * s.start * (ord + Rational.ONE) / Rational.of(2)
                    val bound = half * ord + s.rate * half * ord * (ord - Rational.ONE) / Rational.of(2)
                    if ((s.totals[interest]!! - exactInterest).abs() > bound) return false
                }
            }
            Plan.STRAIGHT_LINE, Plan.DECLINING_BALANCE -> {
                val start = column(s, Column.VALUE_START)
                val dep = column(s, Column.DEPRECIATION)
                val acc = column(s, Column.ACCUMULATED)
                val end = column(s, Column.VALUE_END)
                if (first[start] != s.start) return false
                var sum = Rational.ZERO
                for ((k, row) in s.rows.withIndex()) {
                    sum += row[dep]
                    if (row[0] != Rational.of(k + 1L)) return false
                    if (row[end] != row[start] - row[dep]) return false
                    if (row[acc] != sum) return false
                    if (row[end] != s.start - sum) return false
                    if (k > 0 && row[start] != s.rows[k - 1][end]) return false
                    if (row[dep].signum < 0 || row[end].signum < 0) return false
                    if (s.plan == Plan.DECLINING_BALANCE && row[dep] != roundCents(row[start] * s.rate)) return false
                }
                if (s.plan == Plan.STRAIGHT_LINE) {
                    for (row in s.rows.dropLast(1)) if (row[dep] != s.regular) return false
                    val salvage = s.start - (rational(s.exact!!) ?: return false) * ord
                    if (s.rows.last()[end] != salvage) return false
                    if (s.totals[dep] != s.start - salvage) return false
                    if ((s.rows.last()[dep] - s.regular!!).abs() > half * ord) return false
                } else {
                    // Within rounding of C·(1 − d)ⁿ: half a cent each year, shrunk by the later years' factors.
                    val exact = rational(s.exact!!) ?: return false
                    if ((s.rows.last()[end] - exact).abs() > half * ord) return false
                    if (s.totals[dep] != s.start - s.rows.last()[end]) return false
                }
            }
        }
        return true
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The internal rate of return.

    /**
     * The internal rates of return of cash flows CF₀, CF₁, …, CFₙ (CF₀ now, CFₜ after t periods): every r > −1 with
     * NPV(r) = Σ CFₜ·(1 + r)⁻ᵗ = 0. With q = 1 + r that is the polynomial Σ CFₜ·qⁿ⁻ᵗ = 0, whose positive roots q are found
     * exactly where they can be (factors, the quadratic formula) and otherwise certainly by Sturm's sequence, none missed: none,
     * one or several, each put back. Descartes' rule of signs bounds the count independently: no more than the cash flows'
     * changes of sign, and exactly one where they change sign once.
     */
    fun irr(values: List<Expr>): Solutions {
        if (values.size < 2) throw CalcException(Failure.ARGUMENTS)
        val flows = values.map { rational(it) ?: throw CalcException(Failure.ARGUMENTS) }
        if (flows.all { it.signum == 0 }) throw CalcException(Failure.UNDEFINED)
        // Zeros at the end are a factor q (q = 0 is no rate); zeros at the start lower the degree.
        val trimmed = flows.dropLastWhile { it.signum == 0 }
        val n = trimmed.size - 1
        if (n == 0) return Solutions("r")
        // The coefficient of qᵏ is CF₍ₙ₋ₖ₎.
        val coefficients = (0..n).map { k -> trimmed[n - k] }
        val q = Expr.Sym("q")
        val polynomial = add(coefficients.mapIndexed { k, c -> mul(num(c), pow(q, num(k.toLong()))) })
        // Sturm's sequence: every positive root, certainly, none missed however close.
        val certain = Sturm.realRoots(coefficients).filter { it > 0 }
        // Exact forms where the solver finds them (factors, the quadratic formula, Cardano), the same roots; else the decimals.
        val exact = try {
            Solve.equation(polynomial, Expr.ZERO, q).values.filter { v -> Numeric.value(v)?.let { it > 0 } == true }
        } catch (e: CalcException) {
            if (e.failure == Failure.TOO_LONG) throw e
            null
        }
        val matches = exact != null && exact.size == certain.size && exact.map { Numeric.value(it)!! }.sorted().zip(certain.sorted())
            .all { (a, b) -> kotlin.math.abs(a - b) <= 1e-9 * maxOf(1.0, kotlin.math.abs(b)) }
        val positive: List<Expr> = if (matches) exact!! else certain.map { Expr.Real(it) }
        // Descartes: no more positive roots than changes of sign, and one exactly where there is one change.
        val signs = flows.filter { it.signum != 0 }.map { it.signum }
        val changes = signs.zipWithNext().count { (a, b) -> a != b }
        if (positive.size > changes || (changes == 1 && positive.size != 1) || (changes == 0 && positive.isNotEmpty())) {
            throw CalcException(Failure.UNSOLVED)
        }
        val rates = positive.map { v ->
            val rate = if (v is Expr.Real) refinedRate(coefficients, v.value) else Solve.simplifyValue(add(v, Expr.MINUS_ONE))
            if (!rootOf(coefficients, rate)) throw CalcException(Failure.UNSOLVED)
            rate
        }.sortedBy { Numeric.value(it) }
        return Solutions("r", rates, numeric = rates.any { it is Expr.Real })
    }

    /** p(q) = Σ cₖ qᵏ at the fraction [q], exactly. */
    private fun valueAt(c: List<Rational>, q: Rational): Rational = c.asReversed().fold(Rational.ZERO) { acc, k -> acc * q + k }

    /** The rate r = q − 1 of a root q of p known to a double's precision, refined by bisection on p's sign in fractions where p changes sign there. */
    private fun refinedRate(c: List<Rational>, q0: Double): Expr {
        val center = exactOf(q0)
        val width = exactOf(Math.ulp(q0) * 64)
        var a = center - width
        var b = center + width
        var sa = valueAt(c, a).signum
        val sb = valueAt(c, b).signum
        if (sa == 0) return Expr.Real((a - Rational.ONE).toDouble())
        if (sb == 0) return Expr.Real((b - Rational.ONE).toDouble())
        if (sa == sb) return Expr.Real((center - Rational.ONE).toDouble())
        repeat(80) {
            Budget.check()
            val mid = (a + b) / Rational.of(2)
            val sm = valueAt(c, mid).signum
            if (sm == 0) return Expr.Real((mid - Rational.ONE).toDouble())
            if (sm == sa) { a = mid; sa = sm } else b = mid
        }
        return Expr.Real(((a + b) / Rational.of(2) - Rational.ONE).toDouble())
    }

    private fun exactOf(d: Double): Rational {
        val b = BigDecimal(d)
        return if (b.scale() <= 0) Rational.of(b.toBigIntegerExact()) else Rational.of(b.unscaledValue(), BigInteger.TEN.pow(b.scale()))
    }

    /**
     * Whether [rate] solves NPV = 0, put back: exactly (or in ball arithmetic) for an exact rate; for a decimal one, p changes
     * sign across it (by a relative 2⁻⁴⁰), or, at a root where p touches 0, p there is 0 to within 10⁻⁹ of the size of its terms.
     */
    private fun rootOf(c: List<Rational>, rate: Expr): Boolean {
        if (rate is Expr.Real) {
            val q = exactOf(rate.value) + Rational.ONE
            val delta = q * Rational.of(BigInteger.ONE, BigInteger.ONE.shiftLeft(40))
            val left = valueAt(c, q - delta).signum
            val right = valueAt(c, q + delta).signum
            if (left == 0 || right == 0 || left != right) return true
            val size = c.withIndex().fold(Rational.ZERO) { s, (k, ck) -> s + ck.abs() * rpow(q, k) }
            return valueAt(c, q).abs() <= size * Rational.of(BigInteger.ONE, BigInteger.TEN.pow(9))
        }
        val q = add(Expr.ONE, rate)
        val p = add(c.mapIndexed { k, ck -> mul(num(ck), power(q, num(k.toLong()))) })
        val tidy = try { Calculus.tidy(p) } catch (e: CalcException) { p }
        return isZero(tidy) || agree(p, Expr.ZERO)
    }
}

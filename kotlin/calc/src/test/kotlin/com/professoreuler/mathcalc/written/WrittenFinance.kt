package com.professoreuler.mathcalc

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * Financial mathematics: interest and annuities in the test's own fractions (whole-number powers of 1 + r) and doubles, the
 * repayment and depreciation tables built here as a bank builds them (BigDecimal, every amount rounded half up to the cent), and
 * internal rates of return built into the cash flows (their polynomial in 1/(1 + r) made from chosen roots).
 */
internal object WrittenFinance {
    private const val AREA = "finance"

    /** A rate as typed: "5%", "0.05" or "1/20", and its value. */
    private fun Random.rate(): Pair<String, Q> {
        val tenths = int(1, 150)
        val v = q(tenths.toLong(), 1000)
        val text = when (nextInt(3)) {
            0 -> decimalText(q(tenths.toLong(), 10)) + "%"
            1 -> decimalText(v)
            else -> "${v.n}/${v.d}"
        }
        return text to v
    }

    private fun d(v: Q) = v.toDouble()

    fun all(): List<Task> {
        val r = Random(20261107)
        val out = ArrayList<Task>()
        // One amount, compound interest: P(1 + r/m)^(mn) exactly where it is a fraction, its double too.
        repeat(60) {
            val p = r.int(100, 50000).toLong()
            val (rt, rv) = r.rate()
            val n = r.int(1, 30)
            val m = listOf(1, 1, 2, 4, 12).random(r)
            val growth = (Q.ONE + rv / q(m.toLong())).pow(m * n)
            val call = if (m == 1 && r.nextBoolean()) "futurevalue($p, $rt, $n)" else "futurevalue($p, $rt, $n, $m)"
            out += Task(AREA, call, "${q(p) * growth}", judge = if (m * n <= 40) isExact(q(p) * growth) else isNumber(d(q(p)) * d(Q.ONE + rv / q(m.toLong())).pow(m * n)))
            val f = r.int(100, 90000).toLong()
            out += Task(AREA, "presentvalue($f, $rt, $n${if (m == 1) "" else ", $m"})", "", judge = if (m * n <= 40) isExact(q(f) / growth)
                else isNumber(d(q(f)) / d(Q.ONE + rv / q(m.toLong())).pow(m * n)))
            // Continuous: P·e^(rn).
            out += Task(AREA, "futurevalue($p, $rt, $n, inf)", "", judge = isNumber(p * exp(d(rv) * n)))
            out += Task(AREA, "presentvalue($f, $rt, $n, inf)", "", judge = isNumber(f * exp(-d(rv) * n)))
            // Simple interest P·r·t, t a fraction of a year.
            val days = r.int(1, 720)
            out += Task(AREA, "simpleinterest($p, $rt, $days/360)", "", judge = isExact(q(p) * rv * q(days.toLong(), 360)))
        }
        // The rate, the time, effective and nominal rates.
        repeat(40) {
            val p = r.int(100, 9000).toLong()
            val f = p + r.int(1, 20000).toLong()
            val n = r.int(1, 40)
            val m = listOf(1, 2, 4, 12).random(r)
            val ratio = f.toDouble() / p
            out += Task(AREA, if (m == 1) "interestrate($p, $f, $n)" else "interestrate($p, $f, $n, $m)", "",
                judge = isNumber(m * (ratio.pow(1.0 / (m * n)) - 1), 1e-8))
            out += Task(AREA, "interestrate($p, $f, $n, inf)", "", judge = isNumber(ln(ratio) / n, 1e-9))
            val (rt, rv) = r.rate()
            out += Task(AREA, if (m == 1) "periods($p, $f, $rt)" else "periods($p, $f, $rt, $m)", "",
                judge = isNumber(ln(ratio) / (m * ln(1 + d(rv) / m)), 1e-9))
            out += Task(AREA, "periods($p, $f, $rt, inf)", "", judge = isNumber(ln(ratio) / d(rv), 1e-9))
            out += Task(AREA, "effectiverate($rt, $m)", "", judge = isExact((Q.ONE + rv / q(m.toLong())).pow(m) - Q.ONE))
            out += Task(AREA, "effectiverate($rt, inf)", "", judge = isNumber(Math.expm1(d(rv)), 1e-9))
            out += Task(AREA, "nominalrate($rt, $m)", "", judge = isNumber(m * Math.expm1(ln(1 + d(rv)) / m), 1e-9))
            out += Task(AREA, "nominalrate($rt, inf)", "", judge = isNumber(Math.log1p(d(rv)), 1e-9))
            // A round trip: the nominal rate of the effective rate is the rate.
            out += Task(AREA, "nominalrate(effectiverate($rt, $m), $m)", "$rv", judge = isExact(rv))
        }
        // Annuities: the payments added up one by one, in fractions.
        repeat(40) {
            val pay = r.int(10, 2000).toLong()
            val (rt, rv) = r.rate()
            val n = r.int(1, 36)
            val one = Q.ONE + rv
            val fv = (0 until n).fold(Q.ZERO) { s, k -> s + q(pay) * one.pow(k) }
            val pv = (1..n).fold(Q.ZERO) { s, k -> s + q(pay) / one.pow(k) }
            out += Task(AREA, "annuityfv($pay, $rt, $n)", "", judge = isExact(fv))
            out += Task(AREA, "annuityduefv($pay, $rt, $n)", "", judge = isExact(fv * one))
            out += Task(AREA, "annuitypv($pay, $rt, $n)", "", judge = isExact(pv))
            out += Task(AREA, "annuityduepv($pay, $rt, $n)", "", judge = isExact(pv * one))
            // The loan's payment repays it: P = Σ A/(1 + r)ᵏ, so A = P/Σ(1 + r)⁻ᵏ.
            val principal = r.int(1000, 500000).toLong()
            val annuity = q(principal) / (1..n).fold(Q.ZERO) { s, k -> s + Q.ONE / one.pow(k) }
            out += Task(AREA, "loanpayment($principal, $rt, $n)", "$annuity", judge = isExact(annuity))
            // The term of a payment: the n for which it repays, ln(A/(A − Pr))/ln(1 + r); none where A ≤ P·r.
            val a = r.int(50, 20000).toLong()
            val interest = q(principal) * rv
            out += if (q(a) <= interest) Task(AREA, "loanterm($principal, $rt, $a)", "never repaid", fails = setOf(Failure.UNDEFINED)) { false }
            else Task(AREA, "loanterm($principal, $rt, $a)", "", judge = isNumber(ln(a / (a - d(interest))) / ln(1 + d(rv)), 1e-9))
        }
        for (n in 1..6) out += Task(AREA, "annuitypv(100, 0, $n)", "${100 * n}", judge = isExact(q(100L * n)))
        // Net present value: Σ cₖ/(1 + r)ᵏ in fractions.
        repeat(40) {
            val k = r.int(1, 8)
            val flows = listOf(-r.int(100, 10000).toLong()) + List(k) { r.int(-500, 4000).toLong() }
            val (rt, rv) = r.rate()
            val want = flows.indices.fold(Q.ZERO) { s, j -> s + q(flows[j]) / (Q.ONE + rv).pow(j) }
            out += Task(AREA, "npv($rt, ${flows.joinToString(", ")})", "$want", judge = isExact(want))
        }
        out += irr(r)
        out += tables(r)
        // Refused: a rate of 100 % or more, a negative time, too few or too many arguments, a fraction of a cent in a table.
        for ((call, why) in listOf("futurevalue(1000, 5, 10)" to Failure.ARGUMENTS, "futurevalue(1000, 5%)" to Failure.ARGUMENTS,
                "annuitypv(500, 1.5, 20)" to Failure.ARGUMENTS, "amortization(10000.005, 5%, 4)" to Failure.ARGUMENTS,
                "futurevalue(1000, 5%, -1)" to Failure.UNDEFINED, "loanpayment(10000, 5%, 0)" to Failure.UNDEFINED,
                "periods(2000, 1000, 5%)" to Failure.UNDEFINED, "straightline(1000, 2000, 5)" to Failure.UNDEFINED,
                "irr(5)" to Failure.ARGUMENTS, "npv(5%)" to Failure.ARGUMENTS)) {
            out += Task(AREA, call, why.name, fails = setOf(why)) { false }
        }
        return out
    }

    /**
     * Internal rates of return: the cash flows are the coefficients of K·Π(x − xⱼ) in x = 1/(1 + r), the roots xⱼ = q/(p + q)
     * chosen (r = p/q), some negative (no rate). The rates are the 1/xⱼ − 1 of the positive roots, and nothing else.
     */
    private fun irr(r: Random): List<Task> {
        val out = ArrayList<Task>()
        val rates = listOf(q(1, 20), q(1, 10), q(1, 8), q(1, 5), q(1, 4), q(3, 10), q(1, 2), q(-1, 10), q(-1, 5), q(0), q(1), q(3, 20), q(7, 100))
        repeat(60) {
            val degree = r.int(1, 3)
            val chosen = List(degree) { rates.random(r) }
            // Each root x = 1/(1 + r) = q/(p + q); some factors with a negative root instead (x + c), which has no rate.
            val roots = chosen.map { Q.ONE / (Q.ONE + it) }.toMutableList()
            if (degree < 3 && r.nextInt(3) == 0) roots += q(-r.int(1, 4).toLong())
            // Π(x − xⱼ) with fractions, then cleared to whole numbers: c₀ + c₁x + … (lowest power first).
            var poly = listOf(Q.ONE)
            for (x in roots) poly = (0..poly.size).map { k -> (if (k > 0) poly[k - 1] else Q.ZERO) - (if (k < poly.size) poly[k] * x else Q.ZERO) }
            val den = poly.fold(BigInteger.ONE) { a, c -> a * c.d / a.gcd(c.d) }
            var flows = poly.map { (it * Q.of(den)).n }
            if (flows[0].signum() > 0) flows = flows.map { it.negate() }
            val scale = r.int(1, 5).toBigInteger()
            flows = flows.map { it * scale }
            val want = roots.filter { it > Q.ZERO }.map { Q.ONE / it - Q.ONE }.distinct().sorted()
            out += Task(AREA, "irr(${flows.joinToString(", ")})", want.joinToString("; "), judge = { c ->
                val s = (c.outcome as? Outcome.Solved)?.solutions
                if (s == null) false else {
                    val got = s.values.map { read(it).re }.sorted()
                    got.size == want.size && got.zip(want).all { (g, w) -> close(g, w.toDouble(), 1e-10, 1e-12) } &&
                        s.values.all { v -> rationalOf(v)?.let { it in want } ?: true }
                }
            })
        }
        // No change of sign: no rate.
        for (flows in listOf("100, 200", "-100, -50", "-100, -1, -2", "5, 5, 5")) {
            out += Task(AREA, "irr($flows)", "none", judge = { c -> (c.outcome as? Outcome.Solved)?.solutions?.let { it.values.isEmpty() && !it.many } == true })
        }
        return out
    }

    private val CENT = BigDecimal("0.01")

    private fun money(x: Rational) = BigDecimal(x.num).divide(BigDecimal(x.den), 2, RoundingMode.UNNECESSARY)

    /** The engine's table as rows of BigDecimal, column by column as [columns] names them. */
    private fun rows(c: Calculation, columns: List<Finance.Column>): List<List<BigDecimal>>? {
        val s = (c.outcome as? Outcome.FinanceSchedule)?.schedule ?: return null
        val at = columns.map { s.columns.indexOf(it).takeIf { i -> i >= 0 } ?: return null }
        return s.rows.map { row -> at.map { money(row[it]) } }
    }

    /** The tables, as a bank builds them: BigDecimal, every amount rounded half up to the cent, the last period taking the rest. */
    private fun tables(r: Random): List<Task> {
        val out = ArrayList<Task>()
        val loan = listOf(Finance.Column.PERIOD, Finance.Column.DEBT_START, Finance.Column.PAYMENT, Finance.Column.INTEREST,
            Finance.Column.PRINCIPAL, Finance.Column.DEBT_END)
        repeat(60) { i ->
            val p = BigDecimal(r.nextLong(10_000, 20_000_000)).divide(BigDecimal(100))
            val k = r.int(0, 120)
            val n = r.int(1, 60)
            val rate = BigDecimal(k).divide(BigDecimal(1200), 60, RoundingMode.HALF_EVEN)
            val rateText = "$k/1200"
            val equal = i % 2 == 1
            val want = ArrayList<List<BigDecimal>>()
            var debt = p
            if (!equal) {
                val payment = if (k == 0) p.divide(BigDecimal(n), 2, RoundingMode.HALF_UP) else {
                    val qn = (BigDecimal.ONE + rate).pow(n)
                    (p * rate * qn).divide(qn - BigDecimal.ONE, 2, RoundingMode.HALF_UP)
                }
                for (row in 1..n) {
                    val interest = (debt * BigDecimal(k)).divide(BigDecimal(1200), 2, RoundingMode.HALF_UP)
                    val principal = if (row < n) payment - interest else debt
                    want += listOf(BigDecimal(row).setScale(2), debt.setScale(2), (interest + principal).setScale(2), interest, principal.setScale(2), (debt - principal).setScale(2))
                    debt -= principal
                }
            } else {
                val part = p.divide(BigDecimal(n), 2, RoundingMode.HALF_UP)
                for (row in 1..n) {
                    val interest = (debt * BigDecimal(k)).divide(BigDecimal(1200), 2, RoundingMode.HALF_UP)
                    val principal = if (row < n) part else debt
                    want += listOf(BigDecimal(row).setScale(2), debt.setScale(2), (interest + principal).setScale(2), interest, principal.setScale(2), (debt - principal).setScale(2))
                    debt -= principal
                }
            }
            val call = (if (equal) "equalprincipal" else "amortization") + "(${p.toPlainString()}, $rateText, $n)"
            // A payment that never repays the debt (the rounded principal part too small) is a table the engine may decline.
            out += Task(AREA, call, "${want.last()}", judge = { c -> rows(c, loan) == want })
        }
        val dep = listOf(Finance.Column.PERIOD, Finance.Column.VALUE_START, Finance.Column.DEPRECIATION, Finance.Column.ACCUMULATED, Finance.Column.VALUE_END)
        repeat(30) { i ->
            val cost = BigDecimal(r.nextLong(100_000, 10_000_000)).divide(BigDecimal(100))
            val n = r.int(1, 25)
            val want = ArrayList<List<BigDecimal>>()
            var value = cost
            var accumulated = BigDecimal.ZERO.setScale(2)
            if (i % 2 == 0) {
                val salvage = (cost * BigDecimal(r.int(0, 30))).divide(BigDecimal(100), 2, RoundingMode.DOWN)
                val year = (cost - salvage).divide(BigDecimal(n), 2, RoundingMode.HALF_UP)
                for (row in 1..n) {
                    val d = if (row < n) year else value - salvage
                    accumulated += d
                    want += listOf(BigDecimal(row).setScale(2), value.setScale(2), d.setScale(2), accumulated.setScale(2), (value - d).setScale(2))
                    value -= d
                }
                out += Task(AREA, "straightline(${cost.toPlainString()}, ${salvage.toPlainString()}, $n)", "", judge = { c -> rows(c, dep) == want })
            } else {
                val pct = r.int(1, 60)
                for (row in 1..n) {
                    val d = (value * BigDecimal(pct)).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
                    accumulated += d
                    want += listOf(BigDecimal(row).setScale(2), value.setScale(2), d, accumulated.setScale(2), (value - d).setScale(2))
                    value -= d
                }
                out += Task(AREA, "decliningbalance(${cost.toPlainString()}, $pct%, $n)", "", judge = { c -> rows(c, dep) == want })
            }
        }
        return out
    }
}

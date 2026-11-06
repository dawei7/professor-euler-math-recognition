package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * Financial mathematics against values known independently of the engine: textbook values, fractions worked out here with
 * whole numbers, sums of payments added here one by one, a bank's repayment table built here with BigDecimal, internal rates of
 * return found here by bisection or built into the cash flows, and the refusals.
 */
class FinanceTest {
    private fun calc(text: String): Calculation = Reports.of(TextInput.read(text)).result

    private fun answer(text: String): String = calc(text).let { r -> (r.outcome as? Outcome.Failed)?.let { "FAILED ${it.failure}" } ?: answerOf(r) }

    /** The exact value of a value function's call, as a fraction. */
    private fun exact(text: String): Rational {
        val o = calc(text).outcome as? Outcome.Value ?: error("$text: ${answer(text)}")
        return (o.value as? Num.Exact)?.r ?: error("$text: not exact, ${answer(text)}")
    }

    /** The value of a call as a double, from its exact form. */
    private fun value(text: String): Double {
        val o = calc(text).outcome as? Outcome.Value ?: error("$text: ${answer(text)}")
        return o.exact?.let { Numeric.value(it) } ?: o.value.double
    }

    private fun near(a: Double, b: Double, tol: Double = 1e-10) = abs(a - b) <= tol * maxOf(1.0, abs(a), abs(b))

    private fun q(n: Long, d: Long = 1) = Rational.of(BigInteger.valueOf(n), BigInteger.valueOf(d))

    private fun qpow(b: Rational, n: Int) = Rational.of(b.num.pow(n), b.den.pow(n))

    @Test
    fun interestOnOneAmount() {
        // 1000 · 1.05¹⁰ with whole numbers: 1000 · 21¹⁰ / 20¹⁰.
        assertEquals(q(1000) * qpow(q(21, 20), 10), exact("futurevalue(1000, 5%, 10)"))
        assertEquals(exact("futurevalue(1000, 0.05, 10)"), exact("futurevalue(1000, 5%, 10)"))
        assertTrue(near(1628.894626777442, value("futurevalue(1000, 5%, 10)")))
        // Monthly: 1000 · (1 + 0.06/12)¹², the textbook's 1061.68.
        assertEquals(q(1000) * qpow(q(201, 200), 12), exact("futurevalue(1000, 6%, 1, 12)"))
        assertTrue(near(1061.6778118644996, value("futurevalue(1000, 6%, 1, 12)")))
        // Continuously: 1000·e^0.5.
        assertTrue(near(1000 * exp(0.5), value("futurevalue(1000, 5%, 10, inf)")))
        assertEquals("1000√e ≈ 1648.721271", answer("futurevalue(1000, 5%, 10, inf)"))
        // 2.5 years: 1000 · 1.05^2.5 exactly as a root.
        assertTrue(near(1000 * 1.05.pow(2.5), value("futurevalue(1000, 5%, 2.5)")))
        // The present value grown again is the amount.
        assertEquals(q(5000), exact("presentvalue(5000, 4%, 10)") * qpow(q(26, 25), 10))
        assertTrue(near(5000 / 1.04.pow(10), value("presentvalue(5000, 4%, 10)"), 1e-12))
        assertTrue(near(5000 * exp(-0.4), value("presentvalue(5000, 4%, 10, inf)")))
        // Simple interest: I = P·r·t, 90 days of a 360-day year.
        assertEquals(q(30), exact("simpleinterest(2000, 3%, 0.5)"))
        assertEquals(q(15), exact("simpleinterest(1500, 4%, 90/360)"))
        // The rate and the time of a doubling.
        assertTrue(near(2.0.pow(0.1) - 1, value("interestrate(1000, 2000, 10)"), 1e-13))
        assertTrue(near(12 * (2.0.pow(1.0 / 120) - 1), value("interestrate(1000, 2000, 10, 12)"), 1e-13))
        assertTrue(near(ln(2.0) / 10, value("interestrate(1000, 2000, 10, inf)"), 1e-13))
        assertTrue(near(ln(2.0) / ln(1.05), value("periods(1000, 2000, 5%)"), 1e-13))
        assertTrue(near(ln(2.0) / (12 * ln(1.005)), value("periods(1000, 2000, 6%, 12)"), 1e-12))
        assertTrue(near(ln(2.0) / 0.07, value("periods(1000, 2000, 7%, inf)"), 1e-13))
        assertEquals("ln(2)/ln(21/20) ≈ 14.20669908", answer("periods(1000, 2000, 5%)"))
        // Effective and nominal rates, and the one undoing the other exactly.
        assertEquals(qpow(q(201, 200), 12) - Rational.ONE, exact("effectiverate(6%, 12)"))
        assertTrue(near(0.0616778118645, value("effectiverate(6%, 12)"), 1e-11))
        assertTrue(near(exp(0.05) - 1, value("effectiverate(5%, inf)"), 1e-13))
        assertEquals(q(3, 50), exact("nominalrate(effectiverate(6%, 12), 12)"))
        assertTrue(near(12 * (1.05.pow(1.0 / 12) - 1), value("nominalrate(5%, 12)"), 1e-12))
        assertTrue(near(ln(1.05), value("nominalrate(5%, inf)"), 1e-13))
    }

    @Test
    fun annuities() {
        // 120 monthly payments of 100 at 3 % a year: added here one by one.
        var sum = Rational.ZERO
        var factor = Rational.ONE
        for (k in 0 until 120) { sum += q(100) * factor; factor *= q(401, 400) }
        assertEquals(sum, exact("annuityfv(100, 0.25%, 120)"))
        assertTrue(near(13974.14189, value("annuityfv(100, 0.25%, 120)"), 1e-9))
        assertEquals(sum * q(401, 400), exact("annuityduefv(100, 0.25%, 120)"))
        // 20 yearly payments of 500 at 5 %, discounted here one by one: the textbook's 6231.11 and 6542.66.
        var pv = Rational.ZERO
        for (k in 1..20) pv += q(500) / qpow(q(21, 20), k)
        assertEquals(pv, exact("annuitypv(500, 5%, 20)"))
        assertEquals(pv * q(21, 20), exact("annuityduepv(500, 5%, 20)"))
        assertTrue(near(500 * (1 - 1.05.pow(-20)) / 0.05, value("annuitypv(500, 5%, 20)"), 1e-11))
        assertTrue(near(1.05 * 500 * (1 - 1.05.pow(-20)) / 0.05, value("annuityduepv(500, 5%, 20)"), 1e-11))
        // Without interest: n payments are R·n.
        assertEquals(q(1200), exact("annuityfv(100, 0, 12)"))
        assertEquals(q(1200), exact("annuitypv(100, 0, 12)"))
        assertEquals(q(1200), exact("annuityduepv(100, 0, 12)"))
    }

    @Test
    fun loans() {
        // A 30-year mortgage of 200000 at 4 %: 954.83 a month.
        assertTrue(near(954.8305909309, value("loanpayment(200000, 0.04/12, 360)"), 1e-12))
        // 10000 at 5 % in four years: 10000 · 0.05 / (1 − 1.05⁻⁴), with whole numbers.
        val f = qpow(q(21, 20), 4)
        assertEquals(q(10000) * q(1, 20) * f / (f - Rational.ONE), exact("loanpayment(10000, 5%, 4)"))
        assertEquals(q(2500), exact("loanpayment(10000, 0, 4)"))
        // The term: 10000 repaid by 1000 a year at 5 % takes ln 2 / ln 1.05 years; the term of the exact payment is n again.
        assertTrue(near(ln(2.0) / ln(1.05), value("loanterm(10000, 5%, 1000)"), 1e-13))
        assertTrue(near(4.0, value("loanterm(10000, 5%, loanpayment(10000, 5%, 4))"), 1e-12))
        assertEquals(q(10), exact("loanterm(10000, 0, 1000)"))
        // A payment that only covers the interest never repays the loan.
        assertEquals("FAILED UNDEFINED", answer("loanterm(10000, 5%, 500)"))
        assertEquals("FAILED UNDEFINED", answer("loanterm(10000, 5%, 400)"))
    }

    @Test
    fun netPresentValueAndInternalRate() {
        // −1000 + 500/1.1 + 500/1.21 + 500/1.331, with whole numbers.
        val npv = -q(1000) + q(500) / q(11, 10) + q(500) / q(121, 100) + q(500) / q(1331, 1000)
        assertEquals(npv, exact("npv(10%, -1000, 500, 500, 500)"))
        assertTrue(near(243.4259954921, value("npv(10%, -1000, 500, 500, 500)"), 1e-11))
        assertEquals(q(-1000), exact("npv(10%, -1000)"))
        // The rate where it is 0, found here by bisection.
        var lo = 0.0
        var hi = 1.0
        repeat(200) {
            val mid = (lo + hi) / 2
            val v = -1000 + 500 / (1 + mid) + 500 / (1 + mid).pow(2) + 500 / (1 + mid).pow(3)
            if (v > 0) lo = mid else hi = mid
        }
        val irr = calc("irr(-1000, 500, 500, 500)").outcome as Outcome.Solved
        assertEquals(1, irr.solutions.values.size)
        assertTrue(near(lo, Numeric.value(irr.solutions.values[0])!!, 1e-12))
        assertEquals("r = 1/10 = 0.1", answer("irr(-1000, 1100)"))
        assertEquals("r = 0", answer("irr(-100, 100)"))
        // Two changes of sign, two rates: the classic −100, 230, −132 at 10 % and 20 %.
        assertEquals("r = 1/10 = 0.1; 1/5 = 0.2", answer("irr(-100, 230, -132)"))
        assertEquals("r: none", answer("irr(100, 200)"))
        assertEquals("r: none", answer("irr(-100, -50)"))
        // Less back than was put in: a negative rate, NPV(r) = 0 for r ≈ −7 %.
        assertEquals("r = -3/4 + √185/20 ≈ -0.06992647456", answer("irr(-100, 50, 40)"))
        // Zeros at either end change nothing.
        assertEquals("r = 1/10 = 0.1", answer("irr(-1000, 1100, 0)"))
        assertEquals("r = 1/10 = 0.1", answer("irr(0, -1000, 1100)"))
    }

    /** Cash flows built around a known rate r₀: CF₀ = −Σ CFₜ/(1 + r₀)ᵗ, with one change of sign, so r₀ is their only rate. */
    @Test
    fun internalRatesBuiltIntoTheCashFlows() {
        val random = Random(20261106)
        val wrong = ArrayList<String>()
        repeat(150) {
            val r0 = q(random.nextLong(1, 40), 100)
            val n = random.nextInt(1, 7)
            val later = List(n) { q(random.nextLong(1, 2000)) }
            val first = -later.withIndex().fold(Rational.ZERO) { s, (t, c) -> s + c / qpow(Rational.ONE + r0, t + 1) }
            val text = "irr(" + (listOf(first) + later).joinToString(", ") { it.toString() } + ")"
            val o = calc(text).outcome as? Outcome.Solved
            val values = o?.solutions?.values
            if (values == null || values.size != 1) {
                wrong += "$text: ${answer(text)}"
            } else {
                val v = values[0]
                val ok = if (v is Expr.Num) v.value == r0 else near(Numeric.value(v)!!, r0.toDouble(), 1e-12)
                if (!ok) wrong += "$text: ${answer(text)}, not $r0"
            }
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    private fun schedule(text: String): Finance.Schedule =
        (calc(text).outcome as? Outcome.FinanceSchedule)?.schedule ?: error("$text: ${answer(text)}")

    private fun column(s: Finance.Schedule, c: Finance.Column): List<String> = s.rows.map { Finance.money(it[s.columns.indexOf(c)]) }

    @Test
    fun repaymentTables() {
        // The textbook's annuity loan: 10000 at 5 % over four years, 2820.12 a year, the last 2820.11.
        val a = schedule("amortization(10000, 5%, 4)")
        assertEquals(listOf("500.00", "383.99", "262.19", "134.29"), column(a, Finance.Column.INTEREST))
        assertEquals(listOf("2320.12", "2436.13", "2557.93", "2685.82"), column(a, Finance.Column.PRINCIPAL))
        assertEquals(listOf("2820.12", "2820.12", "2820.12", "2820.11"), column(a, Finance.Column.PAYMENT))
        assertEquals(listOf("7679.88", "5243.75", "2685.82", "0.00"), column(a, Finance.Column.DEBT_END))
        assertEquals("1280.47", Finance.money(a.totals[a.columns.indexOf(Finance.Column.INTEREST)]!!))
        // Equal principal: 2500 a year, the interest falling by 125.
        val e = schedule("equalprincipal(10000, 5%, 4)")
        assertEquals(listOf("500.00", "375.00", "250.00", "125.00"), column(e, Finance.Column.INTEREST))
        assertEquals(listOf("3000.00", "2875.00", "2750.00", "2625.00"), column(e, Finance.Column.PAYMENT))
        // The mortgage: 954.83 a month, the debt at exactly 0.00 after 360 months.
        val m = schedule("amortization(200000, 0.04/12, 360)")
        assertEquals("954.83", Finance.money(m.regular!!))
        assertEquals("0.00", Finance.money(m.rows.last().last()))
        assertEquals("666.67", column(m, Finance.Column.INTEREST)[0])
        // Without interest: the loan in equal parts.
        assertEquals(listOf("2500.00", "2500.00", "2500.00", "2500.00"), column(schedule("amortization(10000, 0, 4)"), Finance.Column.PAYMENT))
    }

    /**
     * Random loans against a table built here as a bank builds it, in BigDecimal: the payment rounded half up to the cent,
     * each interest rounded, the last payment the remaining debt with its interest.
     */
    @Test
    fun randomLoansAgainstABanksTable() {
        val random = Random(7)
        val wrong = ArrayList<String>()
        val hundred = BigDecimal(100)
        repeat(120) {
            val p = BigDecimal(random.nextLong(10_000, 50_000_000)).divide(hundred)
            val k = random.nextInt(0, 120)
            val n = random.nextInt(1, 121)
            val text = "amortization(${p.toPlainString()}, $k/1200, $n)"
            val s = try { schedule(text) } catch (x: IllegalStateException) { wrong += x.message!!; return@repeat }
            val r = BigDecimal(k).divide(BigDecimal(1200), 60, RoundingMode.HALF_EVEN)
            val payment = if (k == 0) p.divide(BigDecimal(n), 2, RoundingMode.HALF_UP) else {
                val qn = (BigDecimal.ONE + r).pow(n)
                (p * r * qn).divide(qn - BigDecimal.ONE, 2, RoundingMode.HALF_UP)
            }
            var debt = p
            for (row in 1..n) {
                val interest = (debt * BigDecimal(k)).divide(BigDecimal(1200), 2, RoundingMode.HALF_UP)
                val principal = if (row < n) payment - interest else debt
                val got = s.rows[row - 1].map { Finance.money(it) }
                val want = listOf(row.toString() + ".00", debt.setScale(2).toPlainString(), (interest + principal).toPlainString(),
                    interest.toPlainString(), principal.setScale(2).toPlainString(), (debt - principal).setScale(2).toPlainString())
                if (got != want) { wrong += "$text row $row: $got, not $want"; return@repeat }
                debt -= principal
            }
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test
    fun depreciationTables() {
        val s = schedule("straightline(10000, 1000, 5)")
        assertEquals(List(5) { "1800.00" }, column(s, Finance.Column.DEPRECIATION))
        assertEquals(listOf("8200.00", "6400.00", "4600.00", "2800.00", "1000.00"), column(s, Finance.Column.VALUE_END))
        // A third of 10000 to the cent: the last year takes the cent left over.
        assertEquals(listOf("3333.33", "3333.33", "3333.34"), column(schedule("straightline(10000, 0, 3)"), Finance.Column.DEPRECIATION))
        val d = schedule("decliningbalance(10000, 20%, 5)")
        assertEquals(listOf("2000.00", "1600.00", "1280.00", "1024.00", "819.20"), column(d, Finance.Column.DEPRECIATION))
        assertEquals("3276.80", Finance.money(d.rows.last().last()))
        assertEquals(listOf("2000.00", "3600.00", "4880.00", "5904.00", "6723.20"), column(d, Finance.Column.ACCUMULATED))
    }

    @Test
    fun whatIsRefused() {
        val cases = listOf(
            // A rate of 100 % or more: 5 typed for 5 % would be 500 %.
            "futurevalue(1000, 5, 10)" to "FAILED ARGUMENTS", "futurevalue(1000, 100%, 1)" to "FAILED ARGUMENTS",
            "annuitypv(500, 1.5, 20)" to "FAILED ARGUMENTS", "amortization(10000, 5, 4)" to "FAILED ARGUMENTS",
            // As many arguments as a function takes, no more, no fewer.
            "futurevalue(1000, 5%)" to "FAILED ARGUMENTS", "futurevalue(1000, 5%, 10, 12, 1)" to "FAILED ARGUMENTS",
            "npv(5%)" to "FAILED ARGUMENTS", "amortization(10000, 5%)" to "FAILED ARGUMENTS", "amortization(5)" to "FAILED ARGUMENTS",
            "irr(5)" to "FAILED ARGUMENTS", "irr(a, b)" to "FAILED ARGUMENTS", "amortization(P, r, n)" to "FAILED ARGUMENTS",
            // A fraction of a cent is no amount of a table.
            "amortization(10000.005, 5%, 4)" to "FAILED ARGUMENTS",
            // Outside a function's domain.
            "annuityfv(100, 5%, 2.5)" to "FAILED UNDEFINED", "futurevalue(1000, 5%, -1)" to "FAILED UNDEFINED",
            "futurevalue(1000, 5%, 10, 0)" to "FAILED UNDEFINED", "futurevalue(1000, 5%, 10, 2.5)" to "FAILED UNDEFINED",
            "periods(2000, 1000, 5%)" to "FAILED UNDEFINED", "interestrate(-1000, 2000, 10)" to "FAILED UNDEFINED",
            "loanpayment(10000, 5%, 0)" to "FAILED UNDEFINED", "straightline(1000, 2000, 5)" to "FAILED UNDEFINED",
            "decliningbalance(1000, 0, 5)" to "FAILED UNDEFINED", "irr(0, 0)" to "FAILED UNDEFINED",
            "amortization(10000, 5%, 1201)" to "FAILED TOO_LARGE",
        )
        val wrong = cases.mapNotNull { (text, expected) -> answer(text).takeIf { it != expected }?.let { "$text: $it, not $expected" } }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test
    fun withLettersAFunctionIsItsFormula() {
        assertEquals("P(r + 1)^n", answer("futurevalue(P, r, n)"))
        assertEquals("P(r/m + 1)^(mn)", answer("futurevalue(P, r, n, m)"))
        // So solve() finds a rate and a time: the rate of a doubling in ten years, the time of a doubling at 5 %.
        assertTrue(answer("solve(futurevalue(1000, r, 10) = 2000, r)").contains("0.07177346254"))
        assertTrue(answer("solve(futurevalue(1000, 5%, n) = 2000, n)").contains("14.20669908"))
        assertTrue(answer("solve(npv(r, -1000, 500, 700) = 0, r)").contains("√305/20"))
        assertTrue(answer("solve(annuityfv(100, 0.25%, n) = 20000, n)").contains("≈"))
    }

    @Test
    fun aLongFractionIsWrittenAsItsDecimal() {
        assertEquals("≈ 1647.009498", answer("futurevalue(1000, 5%, 10, 12)"))
        assertEquals("≈ 1647.009498", answer("1000*(1 + 0.05/12)^120"))
        // A short one keeps its fraction.
        assertEquals("97240500/34481 ≈ 2820.118326", answer("loanpayment(10000, 5%, 4)"))
    }

    @Test
    fun theCommonCasesAreQuick() {
        val calls = listOf("amortization(100000, 0.035/12, 1200)", "equalprincipal(100000, 0.035/12, 1200)",
            "annuityfv(100, 0.0425/12, 1200)", "loanpayment(250000, 0.0375/12, 360)", "futurevalue(1000, 5%, 30, 365)",
            "irr(" + (listOf(-10000) + List(40) { 300 + 7 * it }).joinToString(", ") + ")")
        for (c in calls) {
            val t0 = System.nanoTime()
            val a = answer(c)
            val ms = (System.nanoTime() - t0) / 1_000_000
            assertTrue("$c: $a", !a.startsWith("FAILED"))
            assertTrue("$c took $ms ms", ms < 3000)
        }
    }

    @Test
    fun theStepsSayTheFormulaAndTheValuesPutIn() {
        val steps = Steps.of(TextInput.read("futurevalue(1000, 5%, 10)").latex)!!
        assertEquals(listOf(StepKey.FIN_COMPOUND, StepKey.FIN_SUBSTITUTE, StepKey.RESULT), steps.map { it.why.key })
        assertEquals("F = 1000\\left(1 + 5\\%\\right)^{10}", steps[1].latex)
        val table = TextInput.read("amortization(10000, 5%, 4)")
        val rows = Steps.of(table.latex, table.choice)!!
        assertTrue(rows.last().latex, rows.last().latex.contains("2820.11"))
    }
}

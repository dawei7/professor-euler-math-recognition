package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Certified digits of formulas as the learner writes them, against mpmath's correctly rounded digits (generated once):
 * the real part, and the imaginary part where there is one.
 */
class PreciseTest {
    private val token = Regex("""\\[a-zA-Z]+|\\.|\S""")

    private fun expr(latex: String): Expr =
        Domain.complex { Parser(token.findAll(latex).map { it.value }.toList()).statements().single().sides.single()!! }

    private fun shown(d: Decimal?): String = when (d) {
        null -> "-"
        is Decimal.Zero -> "0"
        is Decimal.Digits -> (if (d.negative) "-" else "") + d.digits.trimEnd('0') + "e" + d.exponent
        is Decimal.Small -> "<1e" + d.below
    }

    private fun check(latex: String, digits: Int, re: String, im: String) {
        val v = Budget.within(60_000) { Precise.digits(expr(latex), digits) }
        assertEquals("$latex, real part", re, shown(v.re).let { s -> if (s.startsWith("-") || s == "0") s else s })
        assertEquals("$latex, imaginary part", im, shown(v.im))
    }

    @Test fun formulas() {
        check("\\sqrt{2}", 50, "14142135623730950488016887242096980785696718753769e0", "-")
        check("e^{\\pi}", 100, "2314069263277926900572908636794854738026610624260021199344504640952434235069045278351697199706754922e1", "-")
        check("e^{\\pi\\sqrt{163}}", 40, "2625374126407687439999999999992500725972e17", "-")
        check("\\frac{1}{3}", 30, "333333333333333333333333333333e-1", "-")
        check("e^{1000}", 30, "197007111401704699388887935224e434", "-")
        check("\\Gamma(\\frac{1}{3})", 40, "2678938534707747633655692940974677644129e0", "-")
        check("\\zeta(3)", 60, "120205690315959428539973816151144999076498629234049888179227e0", "-")
        check("\\operatorname{erf}(1)", 40, "8427007929497148693412206350826092592961e-1", "-")
        check("\\operatorname{J}_{0}(2)", 40, "2238907791412356680518274546499486258252e-1", "-")
        check("\\operatorname{W}(1)", 40, "5671432904097838729999686622103555497538e-1", "-")
        check("i^{i}", 40, "2078795763507619085469556198349787700339e-1", "-")
        check("\\ln(-1)", 30, "0", "314159265358979323846264338328e0")
        check("\\sqrt{-4}+1", 20, "1e0", "2e0")
        check("(1+i)^{10}", 20, "0", "32e1")
        check("\\arcsin(2)", 30, "157079632679489661923132169164e0", "-131695789692481670862504634731e0")
        check("\\sin(10^{20})", 30, "-645251285265780844205811711313e-1", "-")
        check("\\log_{2}(10)", 50, "33219280948873623478703194294893901758648313930246e0", "-")
        check("\\cos(1+2i)", 30, "20327230070196655294363434485e0", "-30518977991518000575121156869e0")
        check("\\arctan(3i)", 30, "157079632679489661923132169164e0", "346573590279972654708616060729e-1")
        check("\\frac{\\pi^{4}}{90}", 50, "10823232337111381915160036965411679027747509519187e0", "-")
        check("\\operatorname{arccot}(-1)", 30, "235619449019234492884698253746e0", "-")
    }

    @Test fun exactNumbersEndWhereTheyEnd() {
        val v = Precise.digits(expr("0.125"), 20)
        assertEquals(Decimal.Digits(false, "125", -1, exact = true), v.re)
        val big = Precise.digits(expr("2^{100}"), 50)
        assertEquals(Decimal.Digits(false, "1267650600228229401496703205376", 30, exact = true), big.re)
        assertEquals(Decimal.Zero, Precise.digits(expr("\\sin(\\pi)"), 30).re)
        assertEquals(Decimal.Digits(true, "2", 0, exact = true), Precise.digits(expr("(-8)^{1/3}"), 30).re)
    }

    @Test fun realModeRefusesComplexValues() {
        val failure = try { Precise.digits(expr("\\ln(-1)"), 20, complex = false); null } catch (e: CalcException) { e.failure }
        assertEquals(Failure.UNDEFINED, failure)
    }
}

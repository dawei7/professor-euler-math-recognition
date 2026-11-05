package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What can be read two ways ("Assuming …"): which readings a formula meets, and each read the other way. */
class AssumptionsTest {
    private fun read(latex: String, a: Assumptions = Assumptions()) = MathCalc.calculate(latex, assumptions = a)
    private fun met(latex: String, a: Assumptions = Assumptions()) = read(latex, a).ambiguities
    private fun answer(latex: String, a: Assumptions) = answerOf(read(latex, a))

    private val degrees = Assumptions(degrees = true)

    @Test
    fun anglesWithoutUnits() {
        assertEquals(setOf(Ambiguity.ANGLES), met("\\sin 30"))
        assertEquals("0.5", answer("\\sin 30", degrees))
        assertEquals("1", answer("\\tan(45)", degrees))
        assertEquals("√3/2 ≈ 0.8660254038", answer("\\cos 30", degrees))
        // An inverse function answers in degrees.
        assertEquals("30", answer("\\arcsin \\frac{1}{2}", degrees))
        assertEquals("60", answer("\\cos^{-1}\\left(\\frac{1}{2}\\right)", degrees))
        assertEquals("π/6 ≈ 0.5235987756", answer("\\arcsin \\frac{1}{2}", Assumptions()))
    }

    @Test
    fun anglesThatSayTheirUnit() {
        // ° and π say it: nothing to assume, and degrees assumed change nothing.
        assertEquals(emptySet<Ambiguity>(), met("\\sin 30^{\\circ}"))
        assertEquals(emptySet<Ambiguity>(), met("\\cos\\frac{\\pi}{3}"))
        assertEquals(emptySet<Ambiguity>(), met("\\cos\\frac{\\pi}{3}", degrees))
        assertEquals("1/2 = 0.5", answer("\\cos\\frac{\\pi}{3}", degrees))
        assertEquals("0.5", answer("\\sin 30^{\\circ}", degrees))
    }

    @Test
    fun anglesOfALetter() {
        // sin x in radians is the convention: no chip; assumed degrees, the equation is solved in degrees and says so.
        assertEquals(emptySet<Ambiguity>(), met("\\sin x=\\frac{1}{2}"))
        assertEquals(setOf(Ambiguity.ANGLES), met("\\sin x=\\frac{1}{2}", degrees))
        assertEquals("x = 30° + k·360°; 150° + k·360° (k)", answer("\\sin x=\\frac{1}{2}", degrees))
    }

    @Test
    fun logWithoutABase() {
        assertEquals(setOf(Ambiguity.LOG), met("\\log 100"))
        assertEquals("2", answer("\\log 100", Assumptions()))
        val natural = answer("\\log 100", Assumptions(naturalLog = true))
        assertTrue(natural, natural.contains("ln") && natural.endsWith("≈ 4.605170186"))
        assertEquals(emptySet<Ambiguity>(), met("\\log_{2} 8"))
        assertEquals(emptySet<Ambiguity>(), met("\\ln 5"))
        assertEquals(emptySet<Ambiguity>(), met("lg 100"))
    }

    @Test
    fun aFunctionOfAPower() {
        assertEquals(setOf(Ambiguity.FUNCTION_POWER), met("\\sin x^{2}"))
        val inner = answerOf(MathCalc.calculate("\\sin x^{2}", Choice(Mode.DERIVATIVE, "x")))
        val outer = answerOf(MathCalc.calculate("\\sin x^{2}", Choice(Mode.DERIVATIVE, "x"), assumptions = Assumptions(powerOfFunction = true)))
        assertEquals("2x·cos(x^2)", inner)
        assertTrue(outer, outer == "2·sin(x)cos(x)" || outer == "2cos(x)·sin(x)" || outer == "sin(2x)")
        // Brackets say it.
        assertEquals(emptySet<Ambiguity>(), met("\\sin(x)^{2}"))
        assertEquals(emptySet<Ambiguity>(), met("\\sin(x^{2})"))
        assertEquals(emptySet<Ambiguity>(), met("\\sin 2x"))
    }

    @Test
    fun eAndI() {
        assertEquals(emptySet<Ambiguity>(), met("e^{x}"))
        assertEquals(setOf(Ambiguity.E), met("a+e"))
        assertEquals(setOf(Ambiguity.I), met("3+4i"))
        // e as a letter: a + e = 5 solved for e.
        val asLetter = MathCalc.calculate("a+e=5", Choice(Mode.SOLVE, "e"), assumptions = Assumptions(eLetter = true))
        assertEquals("e = -a + 5", answerOf(asLetter))
        // i as a letter: i² is no longer −1.
        assertEquals("-1", answer("i^{2}", Assumptions()))
        assertEquals("i^2", answer("i^{2}", Assumptions(iLetter = true)))
        // A sum's index is its own letter, never the imaginary unit.
        assertEquals(emptySet<Ambiguity>(), met("\\sum_{i=1}^{3} i"))
    }

    @Test
    fun theReportCarriesThem() {
        val r = Reports.of("\\sin 30", assumptions = degrees)
        assertEquals(listOf(Ambiguity.ANGLES), r.ambiguities)
        assertEquals("0.5", answerOf(r.result))
        assertEquals(degrees, r.assumptions)
        assertEquals(Assumptions(), degrees.flipped(Ambiguity.ANGLES))
        assertTrue(degrees.takes(Ambiguity.ANGLES))
        // The steps read it the same way.
        val steps = Steps.of("\\sin x=\\frac{1}{2}", null, degrees)
        assertTrue(steps == null || steps.last().latex.contains("30"))
    }
}

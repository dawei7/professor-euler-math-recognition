package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Test

/** A learner's own lines checked step by step (owner, 1 November 2026: "self-control or simple check"). */
class LinesTest {

    @Test
    fun anEquationSolvedRightHoldsEveryStep() {
        assertEquals("· | ✓ | ✓ | ✓", lines("3(x-2)=2x+1", "3x-6=2x+1", "x-6=1", "x=7"))
        assertEquals("· | ✓", lines("x^{2}-5x+6=0", "(x-2)(x-3)=0"))
    }

    @Test
    fun theFirstWrongStepIsFoundWithWhatItLostOrGained() {
        assertEquals("· | ✓ | ✗ lost 7 gained 5", lines("3(x-2)=2x+1", "3x-6=2x+1", "x=5"))
        assertEquals("· | ✓ | ✗ lost 3", lines("x^{2}-5x+6=0", "(x-2)(x-3)=0", "x=2"))
    }

    @Test
    fun anExpressionWorkedOutStepByStep() {
        assertEquals("· | ✓ | ✓", lines("(x+2)^{2}", "=x^{2}+2\\cdot2x+4", "=x^{2}+4x+4"))
        assertEquals("· | ✓ | ✗", lines("(x+2)^{2}", "=x^{2}+2\\cdot2x+4", "=x^{2}+2x+4"))
        assertEquals("· | ✗", lines("(a+b)^{2}", "=a^{2}+b^{2}"))
    }

    @Test
    fun inequalitiesKeepTheirDirection() {
        assertEquals("· | ✓ | ✓", lines("-2x+1<5", "-2x<4", "x>-2"))
        assertEquals("· | ✓ | ✗", lines("-2x+1<5", "-2x<4", "x<-2"))
    }

    @Test
    fun aFormulaRearranged() {
        assertEquals("· | ✓ | ✓", lines("v=\\frac{s}{t}", "vt=s", "t=\\frac{s}{v}"))
        assertEquals("· | ✓ | ✗", lines("v=\\frac{s}{t}", "vt=s", "t=sv"))
    }

    @Test
    fun linesThatAreASystemAreSolved() {
        assertEquals("x = 3, y = 2", lines("x+y=5", "x-y=1"))
        assertEquals("x = 2, y = 1, z = 3", lines("x+y+z=6", "x-y=1", "y+2z=7"))
    }

    @Test
    fun unreadableLinesAreSaidSo() {
        assertEquals("· | ? | ✓", lines("2x=4", "(((", "x=2"))
    }
}

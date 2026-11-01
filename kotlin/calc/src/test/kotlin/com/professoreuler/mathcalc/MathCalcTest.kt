package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Test

class MathCalcTest {

    /** An outcome as a line of text, to compare. */
    private fun answer(latex: String): String {
        val c = MathCalc.calculate(latex)
        return when (val o = c.outcome) {
            is Outcome.Value -> c.text(o.value) + if (o.steps.isNotEmpty()) " (steps ${o.steps})" else ""
            is Outcome.Solved -> when {
                o.always -> "${o.unknown}: always"
                o.roots.isEmpty() -> "${o.unknown}: none"
                else -> "${o.unknown} = " + o.roots.joinToString("; ") { c.text(it) }
            }
            is Outcome.Checked -> "${o.holds}"
            is Outcome.Failed -> o.failure.name
        }
    }

    private fun check(cases: List<Pair<String, String>>) {
        val wrong = cases.mapNotNull { (latex, expected) -> answer(latex).takeIf { it != expected }?.let { "$latex: $it, not $expected" } }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test
    fun theFirstTestSheet() = check(listOf(
        "37+48=" to "85", "125-67=" to "58", "12\\cdot13=" to "156", "144:12=" to "12", "6\\div3=" to "2",
        "7\\times6-5=" to "37", "3,75\\cdot4=" to "15", "0.5+2.25=" to "2.75", "-8+3=" to "-5",
        "(3+4)\\cdot(5-2)=" to "21", "2\\cdot(7-3)^{2}=" to "32", "\\frac{3}{4}+\\frac{5}{6}=" to "19/12 ≈ 1.583333333",
        "\\frac{2}{3}\\cdot\\frac{9}{8}=" to "3/4 = 0.75", "\\frac{\\frac{1}{2}}{\\frac{3}{4}}=" to "2/3 ≈ 0.6666666667",
        "1+\\frac{1}{1+\\frac{1}{2}}=" to "5/3 ≈ 1.666666667", "1+\\frac{1}{(1+\\frac{1}{2})}=" to "5/3 ≈ 1.666666667",
        "2^{10}=" to "1024", "3^{2}+4^{2}=" to "25", "10^{-3}=" to "0.001", "\\sqrt{144}=" to "12",
        "\\sqrt[3]{27}=" to "3", "\\sqrt{9+16}=" to "5", "5!=" to "120", "15\\%\\cdot80=" to "12", "|-7|+2=" to "9",
        "\\sin(30^{\\circ})=" to "0.5", "\\cos(\\pi)=" to "-1", "\\tan(\\frac{\\pi}{4})=" to "1", "\\ln(e^{2})=" to "2",
        "\\log_{10}(1000)=" to "3", "2x+3=11" to "x = 4", "3(x-2)=2x+1" to "x = 7", "\\frac{x}{4}+1=3" to "x = 8",
        "4y-7=2y+9" to "y = 8", "x^{2}=49" to "x = -7; 7", "x^{2}-5x+6=0" to "x = 2; 3", "2x^{2}+3x-2=0" to "x = -2; 0.5",
        "(x+1)^{2}=16" to "x = -5; 3", "\\frac{1}{x}=4" to "x = 1/4 = 0.25",
    ))

    @Test
    fun theSecondTestSheet() = check(listOf(
        "47+74=" to "121", "1001-999=" to "2", "14\\cdot17=" to "238", "714:7=" to "102", "81\\div9=" to "9",
        "4\\times7+1=" to "29", "2,5\\cdot1,4=" to "3,5", "7.41-0.7=" to "6.71", "-14-(-7)=" to "-7",
        "(17-4)\\cdot(1+7)=" to "104", "4\\cdot(1+2)^{3}=" to "108", "\\frac{7}{12}-\\frac{1}{4}=" to "1/3 ≈ 0.3333333333",
        "\\frac{4}{7}\\cdot\\frac{7}{4}=" to "1", "\\frac{1}{4}:\\frac{1}{2}=" to "1/2 = 0.5",
        "\\frac{\\frac{1}{2}+\\frac{1}{3}}{5}=" to "1/6 ≈ 0.1666666667", "4^{4}:16=" to "16", "7^{2}-1=" to "48",
        "2^{-1}+4^{-1}=" to "0.75", "1,7\\cdot10^{4}=" to "17000", "\\sqrt{\\frac{1}{4}}=" to "1/2 = 0.5",
        "\\sqrt[4]{81}=" to "3", "\\sqrt{7}\\cdot\\sqrt{7}=" to "7", "4!-7=" to "17", "14\\%\\cdot70=" to "9.8",
        "|1-7|=" to "6", "\\sin(47^{\\circ})+\\cos(43^{\\circ})=" to "≈ 1.462707403", "\\cos(\\pi)-\\cos(2\\pi)=" to "-2",
        "\\tan(\\frac{\\pi}{4})+\\sin(\\frac{\\pi}{6})=" to "3/2 = 1.5", "\\ln(1)+e^{0}=" to "1", "\\log_{2}(64)=" to "6",
        "111\\cdot7=" to "777", "7x-4=17" to "x = 3", "4(x+1)=x+7" to "x = 1", "\\frac{x}{7}=\\frac{1}{14}" to "x = 1/2 = 0.5",
        "1,4y+0,7=4,9" to "y = 3", "x^{2}=1,44" to "x = -1,2; 1,2", "x^{2}+4x-21=0" to "x = -7; 3", "4x^{2}=1" to "x = -0.5; 0.5",
        "(x-7)^{2}-1=0" to "x = 6; 8", "\\frac{14}{x}=7" to "x = 2",
    ))

    @Test
    fun theRecognizersSpelling() = check(listOf(
        "sin(30^{\\circ})=" to "0.5", "log_{10}1000=" to "3", "ln(e^{2})=" to "2", "tan(\\frac{\\pi}{4})=" to "1",
        "6:3=" to "2", "\\sqrt{x}=3" to "x = 9",
    ))

    @Test
    fun whatHasNoAnswer() = check(listOf(
        "5:0=" to "DIVISION_BY_ZERO", "\\sqrt{-4}=" to "UNDEFINED", "\\ln(0)=" to "UNDEFINED", "2,5!=" to "UNDEFINED",
        "\\tan(90^{\\circ})=" to "UNDEFINED", "x+y=3" to "SEVERAL_UNKNOWNS", "2x+3=" to "UNKNOWN_WITHOUT_EQUATION",
        "\\sin(x)=0" to "MANY_SOLUTIONS", "(3+4=" to "UNREADABLE", "=" to "UNREADABLE", "3\\infty=" to "UNREADABLE",
        "x^{2}=-1" to "x: none", "2(x+1)=2x+2" to "x: always", "2x=2x+1" to "x: none",
    ))

    @Test
    fun checksAndChains() = check(listOf(
        "2+2=4" to "true", "2+2=5" to "false", "2\\cdot(7-3)^{2}=2\\cdot16=" to "32 (steps [true])",
        "3+4=8=" to "8 (steps [false])", "\\frac{1}{2}=0.5" to "true",
    ))

    @Test
    fun numbersAsTheFormulaWritesThem() = check(listOf(
        "1,5:0,7=" to "≈ 2,142857143", "1:3=" to "1/3 ≈ 0.3333333333", "2^{100}=" to "≈ 1.2676506 · 10^30",
        "\\sqrt{2}=" to "≈ 1.414213562", "\\sqrt[3]{-8}=" to "-2", "x^{3}=8" to "x = 2", "2^{x}=8" to "x = 3",
        "x^{2}=2" to "x = ≈ -1.414213562; ≈ 1.414213562", "0,1+0,2=" to "0,3", "3\\pi=" to "≈ 9.424777961",
    ))

    @Test
    fun latexOfAnswers() {
        val c = MathCalc.calculate("\\frac{3}{4}+\\frac{5}{6}=")
        assertEquals("\\frac{19}{12} \\approx 1.583333333", c.latex((c.outcome as Outcome.Value).value))
        val d = MathCalc.calculate("1,5:0,7=")
        assertEquals("\\approx 2{,}142857143", d.latex((d.outcome as Outcome.Value).value))
    }
}

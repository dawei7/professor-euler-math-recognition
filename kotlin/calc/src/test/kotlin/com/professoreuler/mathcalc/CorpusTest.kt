package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * School and first-year tasks across everything Calculate does (owner, 1 November 2026: "world class, as far as you can go
 * without AI"), as the recognizer writes them, with the answers a teacher expects. Each list reports all its differences.
 */
class CorpusTest {

    private fun check(cases: List<Pair<String, String>>, mode: Mode? = null) {
        val wrong = cases.mapNotNull { (latex, expected) -> answer(latex, mode).takeIf { it != expected }?.let { "$latex: $it, not $expected" } }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test
    fun exactValues() = check(listOf(
        "\\sqrt{8}=" to "2√2 ≈ 2.828427125", "\\sqrt{12}+\\sqrt{27}=" to "5√3 ≈ 8.660254038", "\\sqrt[3]{54}=" to "3∛2 ≈ 3.77976315",
        "\\frac{1}{\\sqrt{2}}=" to "√2/2 ≈ 0.7071067812", "\\frac{1}{1+\\sqrt{2}}=" to "-1 + √2 ≈ 0.4142135624",
        "\\sin(45^{\\circ})=" to "√2/2 ≈ 0.7071067812", "\\cos(\\frac{2\\pi}{3})=" to "-1/2 = -0.5", "\\tan(60^{\\circ})=" to "√3 ≈ 1.732050808",
        "\\log_{2}(8)=" to "3", "\\ln(e^{2})=" to "2", "\\log_{3}(5)=" to "log_3(5) ≈ 1.464973521", "\\frac{2\\pi}{3}=" to "2π/3 ≈ 2.094395102",
        "\\sqrt{2}\\cdot\\sqrt{8}=" to "4", "\\sqrt{\\frac{9}{16}}=" to "3/4 = 0.75",
    ))

    @Test
    fun complexNumbers() = check(listOf(
        "(2+3i)(1-i)=" to "5 + i", "|3+4i|=" to "5", "i^{2}=" to "-1", "e^{i\\pi}=" to "-1", "\\frac{1}{i}=" to "-i",
        "x^{2}+2x+5=0" to "x: none (complex -1 - 2i; -1 + 2i)",
    ))

    @Test
    fun simplifying() = check(listOf(
        "\\frac{1}{x}+\\frac{1}{x+1}" to "(2x + 1)/(x(x + 1))", "\\frac{x^{2}-4}{x+2}" to "x - 2 (x ≠ -2)",
        "\\frac{2x}{4x^{2}}" to "1/(2x)", "\\sqrt{x}\\cdot\\sqrt{x}" to "x", "x^{2}\\cdot x^{3}" to "x^5",
        "\\ln(a)+\\ln(b)" to "ln(ab)", "\\sin^{2}(x)+\\cos^{2}(x)" to "1", "\\tan(x)\\cos(x)" to "sin(x)",
        "2\\ln(x)-\\ln(x^{2})" to "0", "(x+1)(x-1)(x+2)" to "x^3 + 2x^2 - x - 2",
    ))

    @Test
    fun factoringBeyondPolynomials() = check(listOf(
        "\\sin^{2}(x)-1" to "(sin(x) - 1)(sin(x) + 1)", "\\frac{x^{2}-1}{x^{2}+2x+1}" to "(x - 1)/(x + 1)",
    ), Mode.FACTOR)

    @Test
    fun equations() = check(listOf(
        "x^{3}-6x^{2}+11x-6=0" to "x = 1; 2; 3", "x^{2}-2x-1=0" to "x = 1 - √2 ≈ -0.4142135624; 1 + √2 ≈ 2.414213562",
        "x^{4}-5x^{2}+4=0" to "x = -2; -1; 1; 2", "\\frac{x+1}{x-1}=3" to "x = 2",
        "\\frac{1}{x-1}+\\frac{1}{x+1}=\\frac{2}{x^{2}-1}" to "x: none", "\\sqrt{x+2}=x" to "x = 2 (rejected -1)",
        "|2x-1|=3" to "x = -1; 2", "3^{x}=5" to "x = log_3(5) ≈ 1.464973521", "e^{x}=5" to "x = ln(5) ≈ 1.609437912",
        "e^{2x}-3e^{x}+2=0" to "x = 0; ln(2) ≈ 0.6931471806", "\\ln(x)=2" to "x = e^2 ≈ 7.389056099", "\\log_{2}(x)=5" to "x = 32",
        "\\ln(x)+\\ln(x-1)=\\ln(6)" to "x = 3 (rejected -2)", "x^{5}-x-1=0" to "x = ≈ 1.167303978",
    ))

    @Test
    fun trigonometricEquations() = check(listOf(
        "\\sin(x)=\\frac{1}{2}" to "x = π/6 + 2kπ; 5π/6 + 2kπ (k)", "\\cos(x)=0" to "x = π/2 + kπ (k)", "\\tan(x)=1" to "x = π/4 + kπ (k)",
        "2\\sin^{2}(x)-\\sin(x)-1=0" to "x = π/2 + 2kπ; 7π/6 + 2kπ; 11π/6 + 2kπ (k)",
    ))

    @Test
    fun formulasForALetter() = check(listOf(
        "v=\\frac{s}{t}" to "t = s/v (if v ≠ 0)", "A=\\pi r^{2}" to "r = -√(A/π); √(A/π)",
        "ax+b=c" to "x = (c - b)/a (if a ≠ 0)",
    ))

    @Test
    fun inequalities() = check(listOf(
        "2x+3<11" to "x < 4", "x^{2}-4\\le0" to "-2 ≤ x ≤ 2", "x^{2}>1" to "x ∈ (-∞, -1) ∪ (1, ∞)",
        "\\frac{x-1}{x+2}\\ge0" to "x ∈ (-∞, -2) ∪ [1, ∞)", "|x-1|<3" to "-2 < x < 4", "x^{2}+1>0" to "x ∈ ℝ", "-3<2x+1\\le5" to "-2 < x ≤ 2",
    ))

    @Test
    fun systems() = check(listOf(
        "\\begin{cases}x+y=5\\\\x-y=1\\end{cases}" to "x = 3, y = 2", "\\begin{cases}2x+3y=7\\\\4x-y=7\\end{cases}" to "x = 2, y = 1",
        "\\begin{cases}x+y=5\\\\xy=6\\end{cases}" to "x = 2, y = 3; x = 3, y = 2",
        "\\begin{cases}x+y+z=6\\\\x-y=1\\\\y+2z=7\\end{cases}" to "x = 2, y = 1, z = 3",
        "x+y=5\\\\x-y=1" to "x = 3, y = 2",
    ))

    @Test
    fun derivatives() = check(listOf(
        "\\frac{d}{dx}(x^{3}+2x)=" to "3x^2 + 2", "\\frac{d}{dx}\\sin(x^{2})=" to "2x·cos(x^2)", "(x^{2}e^{x})'=" to "x^2e^x + 2xe^x",
        "\\frac{d}{dx}\\ln(x)=" to "1/x", "\\frac{d}{dx}\\frac{x}{x+1}=" to "1/(x + 1)^2", "\\frac{d^{2}}{dx^{2}}x^{4}=" to "12x^2",
        "\\frac{d}{dx}x^{3}=3x^{2}" to "equal: 3x^2 | 3x^2", "\\frac{d}{dx}x^{3}=3x" to "differ: 3x^2 | 3x",
    ))

    @Test
    fun integrals() = check(listOf(
        "\\int x^{2}dx" to "(1/3)x^3 + C", "\\int\\sin(x)dx" to "-cos(x) + C", "\\int e^{2x}dx" to "(1/2)e^(2x) + C",
        "\\int\\frac{1}{x}dx" to "ln(|x|) + C", "\\int xe^{x}dx" to "xe^x - e^x + C", "\\int x\\cos(x)dx" to "x·sin(x) + cos(x) + C",
        "\\int\\ln(x)dx" to "x·ln(x) - x + C", "\\int\\frac{1}{x^{2}+1}dx" to "arctan(x) + C", "\\int\\frac{2x}{x^{2}+1}dx" to "ln(x^2 + 1) + C",
        "\\int\\sin(x)\\cos(x)dx" to "(1/2)sin^2(x) + C",
    ))

    @Test
    fun definiteIntegrals() = check(listOf(
        "\\int_{0}^{1}x^{2}dx" to "1/3 ≈ 0.3333333333", "\\int_{0}^{\\pi}\\sin(x)dx" to "2", "\\int_{0}^{1}e^{-x^{2}}dx" to "≈ 0.7468241328",
        "\\int_{1}^{\\infty}\\frac{1}{x^{2}}dx" to "1",
    ))

    @Test
    fun limits() = check(listOf(
        "\\lim_{x\\rightarrow0}\\frac{\\sin(x)}{x}" to "1", "\\lim_{x\\rightarrow2}\\frac{x^{2}-4}{x-2}" to "4",
        "\\lim_{x\\rightarrow\\infty}\\frac{2x^{2}+1}{x^{2}-3}" to "2", "\\lim_{x\\rightarrow\\infty}(1+\\frac{1}{x})^{x}" to "e ≈ 2.718281828",
        "\\lim_{x\\rightarrow0^{+}}\\frac{1}{x}" to "∞", "\\lim_{x\\rightarrow0}\\frac{1}{x}" to "NO_LIMIT",
    ))

    @Test
    fun sums() = check(listOf(
        "\\sum_{k=1}^{100}k=" to "5050", "\\sum_{k=1}^{n}k" to "(1/2)n^2 + (1/2)n", "\\sum_{k=0}^{\\infty}(\\frac{1}{2})^{k}" to "2",
        "\\sum_{k=1}^{\\infty}\\frac{1}{k^{2}}" to "π^2/6 ≈ 1.644934067", "\\prod_{k=1}^{5}k=" to "120",
    ))

    @Test
    fun definitions() = check(listOf("f(x)=x^{2}-4" to "graph of x^2 - 4"))
}

package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Milestone 6: differential equations (each solution put back into its equation), Laplace transforms both ways (each checked
 * the other way), Fourier series (checked by quadrature), the numerical methods' tables, and complex analysis.
 */
class DifferentialTest {
    private fun check(vararg cases: Pair<Pair<String, Choice?>, String>) {
        val wrong = cases.mapNotNull { (input, expected) ->
            val (latex, choice) = input
            val got = answerOf(MathCalc.calculate(latex, choice))
            if (got != expected) "$latex [${choice?.mode}]: $got, not $expected" else null
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test fun firstOrder() = check(
        ("y'=2x" to null) to "y = x^2 + C [integration]",
        ("y'=-2y+4" to null) to "y = C/e^(2x) + 2 [linear]",
        ("\\frac{dy}{dx}=x^{2}y" to null) to "y = Ce^((1/3)x^3) [linear]",
        ("y'+y=e^{x}" to null) to "y = C/e^x + (1/2)e^x [linear]",
        ("y'=y^{2}" to null) to "y = -1/(C + x) [separable]",
        ("y'+\\frac{y}{x}=x" to null) to "y = ((1/3)x^3 + C)/x [linear]",
        ("y'=y+xy^{2}" to null) to "y = 1/(C/e^x - x + 1) [bernoulli]",
        // Bernoulli with its terms on one bar ((x²y² − y)/x), and after a separable integral that does not hold.
        ("y'+\\frac{y}{x}=xy^{2}" to null) to "y = 1/(x(C - x)) [bernoulli]",
        ("xy'+y=x^{2}y^{2}" to null) to "y = 1/(x(C - x)) [bernoulli]",
        ("y'=2y-y^{3}" to null) to "y = 1/√(C/e^(4x) + 1/2) [bernoulli]",
        ("2xy+(x^{2}+1)y'=0" to null) to "0 = x^2y + y - C [exact]",
        ("y'=\\frac{x}{y}" to null) to "0 = -(1/2)x^2 + (1/2)y^2 - C [separable]",
    )

    @Test fun higherOrder() = check(
        ("y''+y=0" to null) to "y = C_2·sin(x) + C_1·cos(x) [constant_coefficients]",
        ("y''-3y'+2y=0" to null) to "y = C_1e^x + C_2e^(2x) [constant_coefficients]",
        ("y''+2y'+y=0" to null) to "y = C_2x/e^x + C_1/e^x [constant_coefficients]",
        ("y''+4y=\\sin(x)" to null) to "y = C_2·sin(2x) + C_1·cos(2x) + (1/3)sin(x) [constant_coefficients]",
        ("y''-y=e^{2x}" to null) to "y = C_1/e^x + C_2e^x + (1/3)e^(2x) [constant_coefficients]",
        ("y'''-y'=0" to null) to "y = C_1/e^x + C_3e^x + C_2 [constant_coefficients]",
        ("x^{2}y''+xy'-y=0" to null) to "y = (C_2x^2 + C_1)/x [cauchy_euler]",
        ("x^{2}y''-3xy'+4y=0" to null) to "y = C_2x^2·ln(x) + C_1x^2 [cauchy_euler]",
        ("\\frac{d^{2}y}{dt^{2}}+9y=0" to null) to "y = C_2·sin(3t) + C_1·cos(3t) [constant_coefficients]",
    )

    @Test fun initialValues() = check(
        ("\\begin{cases}y'=y\\\\y(0)=2\\end{cases}" to null) to "y = Ce^x [linear] | y = 2e^x",
        ("\\begin{cases}y''+y=0\\\\y(0)=1\\\\y'(0)=0\\end{cases}" to null) to "y = C_2·sin(x) + C_1·cos(x) [constant_coefficients] | y = cos(x)",
        ("\\begin{cases}\\frac{dy}{dt}=-ky\\\\y(0)=5\\end{cases}" to null) to "y = C/e^(kt) [linear] | y = 5/e^(kt)",
        ("\\begin{cases}y'=y\\\\y(0)=1\\end{cases}" to Choice(Mode.RUNGE_KUTTA)) to "runge_kutta 11 rows → 2.7182797441",
        ("\\begin{cases}y'=y\\\\y(0)=1\\end{cases}" to Choice(Mode.EULER)) to "euler 11 rows → 2.5937424601",
    )

    @Test fun laplace() = check(
        ("t^{2}" to Choice(Mode.LAPLACE)) to "2/s^3",
        ("te^{-t}" to Choice(Mode.LAPLACE)) to "1/(s + 1)^2",
        ("e^{t}\\cos(t)" to Choice(Mode.LAPLACE)) to "(s - 1)/(s^2 - 2s + 2)",
        ("t\\sin(t)" to Choice(Mode.LAPLACE)) to "2s/(s^2 + 1)^2",
        ("\\frac{1}{s(s+1)}" to Choice(Mode.INVERSE_LAPLACE)) to "-1/e^t + 1",
        ("\\frac{1}{(s-2)^{3}}" to Choice(Mode.INVERSE_LAPLACE)) to "(1/2)t^2e^(2t)",
        ("\\frac{s+3}{s^{2}+2s+5}" to Choice(Mode.INVERSE_LAPLACE)) to "sin(2t)/e^t + cos(2t)/e^t",
    )

    @Test fun fourierAndNumerics() = check(
        ("x" to Choice(Mode.FOURIER)) to "a0 = 0, an = 0, bn = -2(-1)^n/n",
        ("x^{2}" to Choice(Mode.FOURIER)) to "a0 = 2π^2/3, an = 4(-1)^n/n^2, bn = 0",
        ("|x|" to Choice(Mode.FOURIER)) to "a0 = π, an = (2(-1)^n - 2)/(πn^2), bn = 0",
        ("x^{2}-2" to Choice(Mode.NEWTON, point = "1")) to "newton 7 rows → 1.4142135624",
        ("x^{3}-x-1" to Choice(Mode.BISECTION, point = "1,2")) to "bisection 34 rows → 1.3247179572",
        ("\\cos(x)=x" to Choice(Mode.NEWTON)) to "newton 6 rows → 0.7390851332",
        ("\\int_{0}^{1}x^{2}dx" to Choice(Mode.SIMPSON)) to "simpson 6 rows → 0.3333333333",
        ("\\int_{0}^{\\pi}\\sin(x)dx" to Choice(Mode.TRAPEZOID)) to "trapezoid 6 rows → 1.9995983886",
    )

    @Test fun complexAnalysis() = check(
        ("1+i" to Choice(Mode.POLAR)) to "r = √2, φ = π/4",
        ("-\\sqrt{3}+i" to Choice(Mode.POLAR)) to "r = 2, φ = 5π/6",
        ("\\frac{1}{z^{2}+1}" to Choice(Mode.RESIDUES)) to "at -i order 1 residue i/2; at i order 1 residue -i/2",
        ("\\frac{e^{z}}{z^{2}}" to Choice(Mode.RESIDUES)) to "at 0 order 2 residue 1",
        ("\\frac{1}{z(z-1)^{2}}" to Choice(Mode.RESIDUES)) to "at 0 order 1 residue 1; at 1 order 2 residue -1",
    )

    /** Typed: the words, a start "from 1", an interval "on [1, 2]" or "from 0 to 1". */
    @Test fun typed() {
        val cases = listOf(
            "laplace(t^2)" to "2/s^3",
            "invlaplace(1/(s^2+1))" to "sin(t)",
            "fourier(x^2)" to "a0 = 2π^2/3, an = 4(-1)^n/n^2, bn = 0",
            "newton(x^2-2, 1)" to "newton 7 rows → 1.4142135624",
            "bisection(x^3-x-1, 1, 2)" to "bisection 34 rows → 1.3247179572",
            "simpson(x^2, x, 0, 1)" to "simpson 6 rows → 0.3333333333",
            "y'=y; y(0)=2" to "y = Ce^x [linear] | y = 2e^x",
            "dsolve(y' = y, y(0) = 2)" to "y = Ce^x [linear] | y = 2e^x",
            "polar(1+i)" to "r = √2, φ = π/4",
        )
        val wrong = cases.mapNotNull { (input, expected) ->
            val got = answerOf(Reports.of(TextInput.read(input)).result)
            if (got != expected) "$input: $got, not $expected" else null
        }
        assertEquals("", wrong.joinToString("\n"))
    }


}

package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Test

/** Simplify, Factor and Compare (owner, 1 November 2026: "(x+2)^2 or the other way round or add equal terms"). */
class AlgebraTest {

    private fun check(cases: List<Pair<String, String>>, mode: Mode? = null) {
        val wrong = cases.mapNotNull { (latex, expected) -> answer(latex, mode).takeIf { it != expected }?.let { "$latex: $it, not $expected" } }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test
    fun simplified() = check(listOf(
        "(x+2)^{2}" to "x^2 + 4x + 4", "(x+2)^{2}=" to "x^2 + 4x + 4", "(a-b)^{3}" to "a^3 - 3a^2b + 3ab^2 - b^3",
        "5x+8x+7y+2y" to "13x + 9y", "5x+8x+7y+2y=" to "13x + 9y", "2(x+3)-x" to "x + 6", "(x+1)(x-1)" to "x^2 - 1",
        "3x\\cdot4x" to "12x^2", "x\\cdot x\\cdot y" to "x^2y", "(x+y+z)^{2}" to "x^2 + 2xy + 2xz + y^2 + 2yz + z^2",
        "6+x^{2}-5x" to "x^2 - 5x + 6", "x-x+3" to "3", "x_{1}+x_1" to "2x_1", "2a_{n}-a_{n}" to "a_{n}",
        // Fractions as the formula wrote its numbers: as fractions, or as decimals with its separator.
        "\\frac{x}{2}+\\frac{x}{3}" to "(5/6)x", "x/2+x/3" to "(5/6)x", "\\frac{x}{2}+1" to "(1/2)x + 1",
        "0,5x+1,25x" to "1,75x", "0.5x^{2}+0.5x^{2}-0.25" to "x^2 - 0.25", "\\frac{x^{2}-1}{x-1}" to "x + 1 (x ≠ 1)",
        "10\\%x+x" to "(11/10)x",
    ))

    @Test
    fun factored() = check(listOf(
        "x^{2}+4x+4" to "(x + 2)^2", "x^{2}-5x+6" to "(x - 2)(x - 3)", "2x^{2}+3x-2" to "(2x - 1)(x + 2)",
        "x^{3}-x" to "x(x - 1)(x + 1)", "6x+9" to "3(2x + 3)", "x^{2}y+xy" to "xy(x + 1)", "x^{3}-3x^{2}+3x-1" to "(x - 1)^3",
        "x^{4}-1" to "(x - 1)(x + 1)(x^2 + 1)", "x^{4}-5x^{2}+4" to "(x - 1)(x + 1)(x - 2)(x + 2)",
        "x^{2}+2xy+y^{2}" to "(x + y)^2", "4x^{2}-9y^{2}" to "(2x - 3y)(2x + 3y)", "x^{2}-5xy+6y^{2}" to "(x - 2y)(x - 3y)",
        "a^{3}-b^{3}" to "(a - b)(a^2 + ab + b^2)", "ax+ay+bx+by" to "(a + b)(x + y)", "x^{2}y^{2}-1" to "(xy - 1)(xy + 1)",
        "x^{2}y^{2}-2xy+1" to "(xy - 1)^2", "-x^{2}+1" to "-(x - 1)(x + 1)", "\\frac{1}{2}x^{2}-\\frac{1}{2}" to "(1/2)(x - 1)(x + 1)",
        "x^{4}+4" to "(x^2 - 2x + 2)(x^2 + 2x + 2)", "x^{4}+x^{2}+1" to "(x^2 - x + 1)(x^2 + x + 1)",
        // Nothing to take out: said, and how sure.
        "x^{2}+1" to "x^2 + 1 (no factors)", "x^{2}-2" to "x^2 - 2 (no factors)", "x^{4}+1" to "x^4 + 1 (no factors)",
        "2x+3" to "2x + 3 (no factors)", "x^{2}+y^{2}" to "x^2 + y^2 (no factors)",
        "x^{2}-2xy+y^{2}-1" to "x^2 - 2xy + y^2 - 1 (perhaps more)",
    ))

    @Test
    fun factoredOnRequest() = check(listOf(
        "(x+2)^{2}" to "(x + 2)^2", "x^{2}+2x+3x+6" to "(x + 2)(x + 3)", "0.5x^{2}-0.25" to "0.25(2x^2 - 1)", "360=" to "2^3 · 3^2 · 5", "17=" to "17",
        "12\\cdot13=" to "2^2 · 3 · 13", "-12=" to "-2^2 · 3",
    ), Mode.FACTOR)

    @Test
    fun compared() = check(listOf(
        "(x+2)^{2}=x^{2}+4x+4" to "equal: x^2 + 4x + 4 | x^2 + 4x + 4", "(x+2)^{2}=x^{2}+4x+2" to "differ: x^2 + 4x + 4 | x^2 + 4x + 2",
        "5x+8x+7y+2y=13x+9y" to "equal: 13x + 9y | 13x + 9y", "(a+b)^{2}=a^{2}+2ab+b^{2}" to "equal: a^2 + 2ab + b^2 | a^2 + 2ab + b^2",
        "(a+b)^{2}=a^{2}+b^{2}" to "differ: a^2 + 2ab + b^2 | a^2 + b^2",
        "(a+b+c)^{2}=a^{2}+b^{2}+c^{2}+2ab+2bc+2ac" to "equal: a^2 + 2ab + 2ac + b^2 + 2bc + c^2 | a^2 + 2ab + 2ac + b^2 + 2bc + c^2",
        "2(x+1)=2x+2" to "equal: 2x + 2 | 2x + 2", "2x=2x+1" to "differ: 2x | 2x + 1",
        "(x+1)^{2}=x^{2}+2x+1=x^{2}+1" to "differ: x^2 + 2x + 1 | x^2 + 2x + 1 | x^2 + 1",
    ))

    @Test
    fun comparedOnRequest() = check(listOf("(x+2)^{2}=x^{2}+2x+4" to "differ: x^2 + 4x + 4 | x^2 + 2x + 4"), Mode.COMPARE)

    @Test
    fun chainsWithUnknowns() = check(listOf(
        "(x+1)^{2}=x^{2}+2x+1=" to "x^2 + 2x + 1 (steps [true])", "(x+1)^{2}=x^{2}+x+1=" to "x^2 + x + 1 (steps [false])",
    ))

    @Test
    fun whatIsTooMuch() {
        val start = System.nanoTime()
        check(listOf("(x+y+z)^{99}" to "TOO_LARGE", "\\frac{x}{0}" to "DIVISION_BY_ZERO"))
        // Whatever it comes to, it ends within the budget (about 3 s) rather than freezing.
        val heavy = listOf("(a+b+c+d+e+f)^{12}", "(x+1)^{60}(x-1)^{60}", "\\int\\frac{x^{9}+1}{x^{10}+x+1}dx",
            "x^{40}+x^{17}-3x^{5}+1=0")
        for (latex in heavy) {
            val t0 = System.nanoTime()
            answer(latex)
            val seconds = (System.nanoTime() - t0) / 1e9
            assertEquals("$latex took $seconds s", true, seconds < 6)
        }
        assertEquals(true, (System.nanoTime() - start) / 1e9 < 20)
    }

    @Test
    fun modesThatFit() {
        fun modes(latex: String) = MathCalc.calculate(latex).let { c ->
            c.choices.joinToString(",") { it.mode.name + (it.letter?.let { l -> " $l" } ?: "") } + " -> " + c.choice?.mode
        }
        assertEquals("SIMPLIFY,FACTOR,DERIVATIVE x,INTEGRAL x,GRAPH x -> SIMPLIFY", modes("(x+2)^{2}"))
        assertEquals("SIMPLIFY,FACTOR,DERIVATIVE x,INTEGRAL x,GRAPH x -> FACTOR", modes("x^{2}+4x+4"))
        assertEquals("SIMPLIFY,FACTOR,DERIVATIVE x,INTEGRAL x,GRAPH x -> FACTOR", modes("x^{2}+4x+4="))
        // Its letters cancel: a number.
        assertEquals("CALCULATE,FACTOR,NUMBER_FACTS -> CALCULATE", modes("x-x+3"))
        assertEquals("SOLVE x,COMPARE,GRAPH x -> SOLVE", modes("2x+3=11"))
        assertEquals("SOLVE x,COMPARE,GRAPH x -> COMPARE", modes("(x+2)^{2}=x^{2}+4x+4"))
        assertEquals("SOLVE x,COMPARE,GRAPH x -> SOLVE", modes("\\sin(x)=0,5"))
        assertEquals("SOLVE x,SOLVE y,COMPARE,GRAPH x -> COMPARE", modes("x+y=y+x"))
        assertEquals("SOLVE s,SOLVE t,SOLVE v,COMPARE -> SOLVE", modes("v=\\frac{s}{t}"))
        assertEquals("COMPARE -> COMPARE", modes("2+2=4"))
        assertEquals("CALCULATE,FACTOR,NUMBER_FACTS -> CALCULATE", modes("37+48="))
        assertEquals("CALCULATE -> CALCULATE", modes("1:3="))
        assertEquals("CALCULATE -> CALCULATE", modes("1="))
        // A mode that does not fit is not taken.
        assertEquals("x = 4", answer("2x+3=11", Mode.FACTOR))
    }

    /** Products of random factors with rational roots, and one without, come apart into exactly those factors. */
    @Test
    fun productsOfRandomFactorsComeApart() {
        val random = java.util.Random(46)
        val x = Polynomial.variable("x")
        val y = Polynomial.variable("y")
        repeat(300) { round ->
            var p = Polynomial.constant(Rational.of(random.nextInt(5) + 1L))
            var linear = 0
            repeat(random.nextInt(4) + 1) {
                val b = (random.nextInt(19) - 9).toLong().let { if (it == 0L) 1L else it }
                val other = if (round % 3 == 0) y else Polynomial.ONE
                p *= x * Rational.of(random.nextInt(4) + 1L) + other * Rational.of(b)
                linear++
            }
            if (round % 2 == 0) p *= x * x + Polynomial.ONE * Rational.of(random.nextInt(9) + 1L)
            val product = Factor.of(p)
            val shown = Format.product(product, Style.PLAIN, latex = false)
            assertEquals("complete: $shown", true, product.complete)
            assertEquals("linear factors: $shown", linear, product.factors.filter { it.first.degree == 1 }.sumOf { it.second })
            assertEquals("the same polynomial: $shown", p, product.expanded())
        }
    }

    /** Large coefficients and degrees take a moment at most, and say where they stopped looking. */
    @Test
    fun hardOnesStayQuick() {
        val start = System.nanoTime()
        check(listOf(
            "x^{12}-1" to "(x - 1)(x + 1)(x^2 + 1)(x^2 - x + 1)(x^2 + x + 1)(x^4 - x^2 + 1)",
            "x^{7}+3x^{3}+5" to "x^7 + 3x^3 + 5 (perhaps more)", "123456789x^{4}+987654321" to "9(13717421x^4 + 109739369) (perhaps more)",
        ), Mode.FACTOR)
        check(listOf("(x+1)^{30}" to "(x + 1)^30"), Mode.FACTOR)
        val seconds = (System.nanoTime() - start) / 1e9
        assertEquals("took $seconds s", true, seconds < 3)
    }

    @Test
    fun latexOfAlgebra() {
        val c = MathCalc.calculate("(x+2)^{2}")
        assertEquals("x^{2} + 4x + 4", c.latex((c.outcome as Outcome.Expression).result))
        val f = MathCalc.calculate("x^{2}+4x+4")
        assertEquals("(x + 2)^{2}", f.latex((f.outcome as Outcome.Factored).result))
        val g = MathCalc.calculate("\\frac{x}{2}+\\frac{x}{3}")
        assertEquals("\\frac{5}{6}x", g.latex((g.outcome as Outcome.Expression).result))
        val d = MathCalc.calculate("0,5x+1,25x")
        assertEquals("1{,}75x", d.latex((d.outcome as Outcome.Expression).result))
        val p = MathCalc.calculate("360=", Mode.FACTOR)
        assertEquals("2^{3} \\cdot 3^{2} \\cdot 5", p.latex(p.outcome as Outcome.Primes))
        val s = MathCalc.calculate("x_{12}^{2}+x_{12}^{2}")
        assertEquals("2x_{12}^{2}", s.latex((s.outcome as Outcome.Expression).result))
        assertEquals("2x_12^2", s.text((s.outcome as Outcome.Expression).result))
    }
}

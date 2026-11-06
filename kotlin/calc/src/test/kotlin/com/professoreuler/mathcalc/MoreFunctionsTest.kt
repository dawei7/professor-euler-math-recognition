package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import kotlin.math.abs

/**
 * The functions added for "every mathematical function which is reasonably used": each answer against a value known
 * independently of the engine (a textbook's, or one computed another way here: an inverse put back, a numeric integral, a
 * finite difference, an identity such as the Wronskian of J and Y or Legendre's relation of K and E).
 */
class MoreFunctionsTest {
    private fun answer(text: String): String {
        val r = Reports.of(TextInput.read(text)).result
        return if (r.outcome is Outcome.Failed) "FAILED ${(r.outcome as Outcome.Failed).failure}" else answerOf(r)
    }

    private fun check(cases: List<Pair<String, String>>) {
        val wrong = cases.mapNotNull { (text, expected) -> answer(text).takeIf { it != expected }?.let { "$text: $it, not $expected" } }
        assertEquals("", wrong.joinToString("\n"))
    }

    /** The certified digits of [text]'s value, [n] of them. */
    private fun digits(text: String, n: Int = 20): String {
        val typed = TextInput.read(text)
        val e = Domain.complex { Parser(MathCalc.TOKEN.findAll(typed.latex).map { it.value }.toList()).statements() }
            .single().sides.single()!!.let { Calculus.evaluate(it) }
        return Precise.digits(e, n, complex = false).re.toString()
    }

    /** [latex] read as a constant (no letters). */
    private fun constant(text: String): Expr {
        val typed = TextInput.read(text)
        return Domain.complex { Parser(MathCalc.TOKEN.findAll(typed.latex).map { it.value }.toList()).statements() }
            .single().sides.single()!!.let { Calculus.evaluate(it) }
    }

    @Test
    fun hyperbolicExactValues() = check(listOf(
        // arsinh(3/4) = ln 2 because sinh(ln 2) = (2 − 1/2)/2 = 3/4; solving sinh x = 3/4 agrees.
        "arsinh(3/4)" to "ln(2) ≈ 0.6931471806",
        "solve(sinh(x) = 3/4, x)" to "x = ln(2) ≈ 0.6931471806",
        "arsinh(-3/4)" to "-ln(2) ≈ -0.6931471806",
        "arsinh(0)" to "0",
        "arcosh(5/4)" to "ln(2) ≈ 0.6931471806",
        "arcosh(1)" to "0",
        "artanh(3/5)" to "ln(2) ≈ 0.6931471806",
        "artanh(1/2)" to "ln(3)/2 ≈ 0.5493061443",
        "arcoth(2)" to "ln(3)/2 ≈ 0.5493061443",
        "coth(ln(2))" to "5/3 ≈ 1.666666667",
        "csch(ln(3))" to "3/4 = 0.75",
        "sech(0)" to "1",
        "sech(ln(2))" to "4/5 = 0.8",
        "artanh(1)" to "FAILED UNDEFINED",
        "arcoth(1/2)" to "FAILED UNDEFINED",
        "coth(0)" to "FAILED UNDEFINED",
        // In the complex numbers: arcosh x = i·arccos x on [−1, 1], arcosh|x| + iπ below −1, artanh beyond ±1 from above the cut.
        "arcosh(1/2)" to "πi/3",
        "arcosh(-2)" to "arcosh(2) + πi",
        "artanh(2)" to "ln(3)/2 + πi/2",
        // At a logarithm exactly, e^(ln a) = a: sinh(ln 2) = 3/4, tanh(ln 3) = 4/5; odd for negative rationals.
        "sinh(ln(2))" to "3/4 = 0.75",
        "cosh(ln(4))" to "17/8 = 2.125",
        "tanh(ln(3))" to "4/5 = 0.8",
        "artanh(-1/3)" to "-ln(2)/2 ≈ -0.3465735903",
        "arcoth(-2)" to "-ln(3)/2 ≈ -0.5493061443",
        "sinh(-x)" to "-sinh(x)",
        "cosh(-x)" to "cosh(x)",
        "sinh^(-1)(0)" to "0",
    ))

    @Test
    fun arcsecAndArccsc() = check(listOf(
        "arcsec(2)" to "π/3 ≈ 1.047197551",
        "arcsec(sqrt(2))" to "π/4 ≈ 0.7853981634",
        "arcsec(-2)" to "2π/3 ≈ 2.094395102",
        "arcsec(1)" to "0",
        "arccsc(2)" to "π/6 ≈ 0.5235987756",
        "arccsc(-1)" to "-π/2 ≈ -1.570796327",
        "arcsec(1/2)" to "FAILED UNDEFINED",
        "solve(arcsec(x) = pi/3, x)" to "x = 2",
    ))

    /** Each inverse put back into its function gives the argument again. */
    @Test
    fun inversesPutBack() {
        val pairs = listOf(
            Func.ASINH to Func.SINH, Func.ACOSH to Func.COSH, Func.ATANH to Func.TANH, Func.ACOTH to Func.COTH,
        )
        val points = mapOf(Func.ASINH to listOf(-3.0, -0.4, 0.2, 5.0), Func.ACOSH to listOf(1.1, 2.0, 7.5),
            Func.ATANH to listOf(-0.9, -0.1, 0.3, 0.95), Func.ACOTH to listOf(-4.0, -1.2, 1.5, 9.0))
        for ((inverse, f) in pairs) for (x in points.getValue(inverse)) {
            val y = Numeric.function(inverse, listOf(x))
            assertEquals("$inverse($x)", x, Numeric.function(f, listOf(y)), 1e-12 * maxOf(1.0, abs(x)))
        }
        for (x in listOf(-5.0, -1.5, 1.0, 2.0, 10.0)) {
            assertEquals(1 / x, kotlin.math.cos(Numeric.function(Func.ASEC, listOf(x))), 1e-14)
            assertEquals(1 / x, kotlin.math.sin(Numeric.function(Func.ACSC, listOf(x))), 1e-14)
        }
    }

    /** Every derivative the engine gives agrees with a central difference of the function itself. */
    @Test
    fun derivativesAgreeWithDifferences() {
        val x = Expr.Sym("x")
        val cases = listOf(
            fn(Func.COTH, x) to listOf(0.5, 2.0), fn(Func.SECH, x) to listOf(-1.0, 0.7), fn(Func.CSCH, x) to listOf(0.4, 3.0),
            fn(Func.ASINH, x) to listOf(-2.0, 0.3), fn(Func.ACOSH, x) to listOf(1.5, 4.0), fn(Func.ATANH, x) to listOf(-0.5, 0.8),
            fn(Func.ACOTH, x) to listOf(-3.0, 1.7), fn(Func.ASEC, x) to listOf(-2.5, 1.3), fn(Func.ACSC, x) to listOf(-1.8, 2.2),
            fn(Func.EI, x) to listOf(-1.5, 0.6, 2.0), fn(Func.SI, x) to listOf(-2.0, 1.0, 6.0), fn(Func.CI, x) to listOf(0.5, 4.0),
            fn(Func.BESSELY, num(0), x) to listOf(0.8, 3.0), fn(Func.BESSELY, num(2), x) to listOf(1.5, 6.0),
            fn(Func.ELLIPTICK, x) to listOf(-0.6, 0.3, 0.9), fn(Func.ELLIPTICE, x) to listOf(-0.4, 0.5, 0.95),
        )
        for ((f, at) in cases) {
            val d = Calculus.derivative(f, x)
            for (p in at) {
                val h = 1e-5
                val numeric = (Numeric.eval(f, mapOf("x" to p + h)) - Numeric.eval(f, mapOf("x" to p - h))) / (2 * h)
                val exact = Numeric.eval(d, mapOf("x" to p))
                assertEquals("d/dx $f at $p: $d", numeric, exact, 1e-6 * maxOf(1.0, abs(exact)))
            }
        }
    }

    @Test
    fun integralsAndSolving() = check(listOf(
        "integral(arsinh(x), x)" to "x·arsinh(x) - √(x^2 + 1) + C",
        "integral(arcosh(x), x)" to "x·arcosh(x) - √(x^2 - 1) + C",
        "integral(artanh(x), x)" to "x·artanh(x) + (1/2)ln(-x^2 + 1) + C",
        "integral(arcsec(x), x)" to "x·arcsec(x) - arcosh(|x|) + C",
        "integral(coth(x), x)" to "ln(|sinh(x)|) + C",
        "integral(sech(x), x)" to "arctan(sinh(x)) + C",
        "integral(tanh(x), x)" to "ln(cosh(x)) + C",
        "integral(sech(x)^2, x)" to "tanh(x) + C",
        "integral(sin(x)/x, x)" to "Si(x) + C",
        "integral(cos(x)/x, x)" to "Ci(x) + C",
        "integral(e^x/x, x)" to "Ei(x) + C",
        "integral(Si(x), x)" to "x·Si(x) + cos(x) + C",
        "solve(tanh(x) = 1/2, x)" to "x = ln(3)/2 ≈ 0.5493061443",
        "solve(cosh(x) = 2, x)" to "x = -ln(2 + √3) ≈ -1.316957897; ln(2 + √3) ≈ 1.316957897",
        "solve(arsinh(x) = 2, x)" to "x = ≈ 3.626860408",
        "derivative(arsinh(x), x)" to "1/√(x^2 + 1)",
        "derivative(bessely(0, x), x)" to "-bessely(1, x)",
        "derivative(elliptice(k), k)" to "(elliptice(k) - elliptick(k))/k",
    ))

    /** Digits against published values (Abramowitz and Stegun, DLMF), each certified by the engine's balls. */
    @Test
    fun specialFunctionDigits() {
        val cases = listOf(
            "Si(1)" to "9.4608307036718301494e-1", "Si(-2)" to "-1.6054129768026948486",
            "Ci(1)" to "3.3740392290096813466e-1", "Ei(1)" to "1.8951178163559367555", "Ei(-1)" to "-2.1938393439552027368e-1",
            "bessely(0, 1)" to "8.8256964215676957983e-2", "bessely(1, 1)" to "-7.8121282130028871655e-1",
            "elliptick(1/2)" to "1.6857503548125960429", "elliptice(1/2)" to "1.4674622093394271555",
            "elliptick(sqrt(2)/2)" to "1.8540746773013719184", "elliptice(sqrt(2)/2)" to "1.3506438810476755025",
            "elliptick(sin(pi/12))" to "1.5981420021125401445",
        )
        val wrong = cases.mapNotNull { (text, expected) -> digits(text).takeIf { it != expected }?.let { "$text: $it, not $expected" } }
        assertEquals("", wrong.joinToString("\n"))
    }

    /** The lemniscatic case is exact: K(1/√2) = Γ(1/4)²/(4√π), and E from Legendre's relation. */
    @Test
    fun lemniscaticCase() {
        val k = constant("elliptick(sqrt(2)/2)")
        assertTrue(k.toString(), k.toString().contains("Γ") || k.toString().contains("gamma"))
        val reference = Calculus.tidy(pow(fn(Func.GAMMA, num(Rational.of(1) / Rational.of(4))), Expr.TWO) / (num(4) * sqrt(Expr.PI)))
        assertEquals(true, Precise.nearZero(k - reference))
    }

    /** J_(n+1)(x)·Y_n(x) − J_n(x)·Y_(n+1)(x) = 2/(πx): Bessel's Y checked against the J the engine had before. */
    @Test
    fun wronskianOfBesselJAndY() {
        for (n in listOf(0L, 1L, 3L)) for (x in listOf(num(1), num(Rational.of(5) / Rational.of(2)), num(7))) {
            val w = fn(Func.BESSELJ, num(n + 1), x) * fn(Func.BESSELY, num(n), x) - fn(Func.BESSELJ, num(n), x) * fn(Func.BESSELY, num(n + 1), x)
            assertEquals("n = $n, x = $x", true, Precise.nearZero(w - Expr.TWO / (Expr.PI * x), 100))
        }
    }

    /** Legendre's relation E·K′ + E′·K − K·K′ = π/2, with K′ = K(√(1 − k²)) and E′ = E(√(1 − k²)). */
    @Test
    fun legendreRelation() {
        for ((k, kp) in listOf(Rational.of(3) / Rational.of(5) to Rational.of(4) / Rational.of(5), Rational.of(5) / Rational.of(13) to Rational.of(12) / Rational.of(13))) {
            val kk = fn(Func.ELLIPTICK, num(k))
            val ee = fn(Func.ELLIPTICE, num(k))
            val kk2 = fn(Func.ELLIPTICK, num(kp))
            val ee2 = fn(Func.ELLIPTICE, num(kp))
            val relation = ee * kk2 + ee2 * kk - kk * kk2 - Expr.PI / Expr.TWO
            assertEquals("k = $k", true, Precise.nearZero(relation, 100))
        }
    }

    /** Si, Ci, Ei and K, E against their integrals, worked out numerically here. */
    @Test
    fun specialFunctionsAgainstTheirIntegrals() {
        val gamma = 0.57721566490153286
        for (x in listOf(0.5, 3.0, 10.0)) {
            val si = Quadrature.integrate({ t -> if (t == 0.0) 1.0 else kotlin.math.sin(t) / t }, 0.0, x)!!
            assertEquals(si, Numeric.function(Func.SI, listOf(x)), 1e-9)
            val ci = gamma + kotlin.math.ln(x) + Quadrature.integrate({ t -> if (t == 0.0) 0.0 else (kotlin.math.cos(t) - 1) / t }, 0.0, x)!!
            assertEquals(ci, Numeric.function(Func.CI, listOf(x)), 1e-9)
        }
        for (x in listOf(-2.0, 0.5, 4.0)) {
            val ei = gamma + kotlin.math.ln(abs(x)) + Quadrature.integrate({ t -> if (t == 0.0) 1.0 else (kotlin.math.exp(t) - 1) / t }, 0.0, x)!!
            assertEquals(ei, Numeric.function(Func.EI, listOf(x)), 1e-9 * maxOf(1.0, abs(ei)))
        }
        for (k in listOf(0.2, 0.7, 0.99)) {
            val kk = Quadrature.integrate({ t -> 1 / kotlin.math.sqrt(1 - k * k * kotlin.math.sin(t) * kotlin.math.sin(t)) }, 0.0, Math.PI / 2)!!
            val ee = Quadrature.integrate({ t -> kotlin.math.sqrt(1 - k * k * kotlin.math.sin(t) * kotlin.math.sin(t)) }, 0.0, Math.PI / 2)!!
            assertEquals(kk, Numeric.function(Func.ELLIPTICK, listOf(k)), 1e-9)
            assertEquals(ee, Numeric.function(Func.ELLIPTICE, listOf(k)), 1e-9)
        }
    }

    @Test
    fun specialFunctionValues() = check(listOf(
        "Si(0)" to "0",
        "Ci(0)" to "FAILED UNDEFINED",
        "Ei(0)" to "FAILED UNDEFINED",
        "elliptick(0)" to "π/2 ≈ 1.570796327",
        "elliptice(0)" to "π/2 ≈ 1.570796327",
        "elliptice(1)" to "1",
        "elliptick(1)" to "FAILED UNDEFINED",
        "elliptick(2)" to "FAILED UNDEFINED",
        "bessely(0, 0)" to "FAILED UNDEFINED",
        "bessely(1, -2)" to "FAILED UNDEFINED",
    ))

    @Test
    fun roundMaxMin() = check(listOf(
        // A half away from zero.
        "round(2.5)" to "3",
        "round(-2.5)" to "-3",
        "round(0.125, 2)" to "0.13",
        "round(pi, 2)" to "3.14",
        "round(2/3, 3)" to "0.667",
        "round(1234.5678, -2)" to "1200",
        "round(sqrt(2), 5)" to "1.41421",
        "round(-e, 3)" to "-2.718",
        "round(x)" to "FAILED ARGUMENTS",
        "round(2.567, 1.5)" to "FAILED ARGUMENTS",
        "max(1, 5, 3)" to "5",
        "max(sqrt(2), 1.4)" to "√2 ≈ 1.414213562",
        "min(pi, 22/7, 3.14)" to "3.14",
        "min(-1, -1/2)" to "-1",
        "max(x, 2)" to "FAILED ARGUMENTS",
        "max(1 + i, 2)" to "FAILED UNDEFINED",
    ))

    /** round against Java's BigDecimal with HALF_UP (away from zero), for numbers of every size and sign. */
    @Test
    fun roundAgreesWithBigDecimal() {
        val random = java.util.Random(7)
        repeat(200) {
            val n = random.nextInt(2_000_000) - 1_000_000
            val places = random.nextInt(4)
            val digits = random.nextInt(3) - 1
            val text = java.math.BigDecimal.valueOf(n.toLong(), places)
            val expected = text.setScale(digits, java.math.RoundingMode.HALF_UP).stripTrailingZeros()
            val got = answer("round(${text.toPlainString()}, $digits)")
            assertEquals("round($text, $digits)", expected.toPlainString(), got)
        }
    }

    @Test
    fun complexParts() = check(listOf(
        "re(3 + 4i)" to "3",
        "im(3 + 4i)" to "4",
        "conj(3 + 4i)" to "3 - 4i",
        "re((1 + 2i)/(3 - i))" to "1/10 = 0.1",
        "im((1 + 2i)/(3 - i))" to "7/10 = 0.7",
        "arg(-1)" to "π ≈ 3.141592654",
        "arg(1 + i)" to "π/4 ≈ 0.7853981634",
        "arg(-1 - i)" to "-3π/4 ≈ -2.35619449",
        "arg(0)" to "FAILED UNDEFINED",
        "re(x + i y)" to "x",
        "im(x + i y)" to "y",
        "conj(x + i y)" to "x - iy",
        "re(e^(i x))" to "cos(x)",
        "im(e^(i x))" to "sin(x)",
        "re((x + i)^2)" to "x^2 - 1",
        "re(sqrt(x) + i)" to "FAILED NOT_POLYNOMIAL",
        "arg(x + i)" to "FAILED ARGUMENTS",
    ))

    /** arg agrees with atan2 of the parts, at many points of every quadrant. */
    @Test
    fun argAgreesWithAtan2() {
        for (a in -3..3) for (b in -3..3) {
            if (a == 0 && b == 0) continue
            val z = "$a + $b i".replace("+ -", "- ")
            val v = Numeric.value(constant("arg($z)"))!!
            assertEquals(z, kotlin.math.atan2(b.toDouble(), a.toDouble()), v, 1e-12)
        }
    }

    @Test
    fun betaFunction() = check(listOf(
        // B(2, 3) = 1!·2!/4! = 1/12; B(½, ½) = π; B(a, 1 − a) = π/sin(πa).
        "beta(2, 3)" to "1/12 ≈ 0.08333333333",
        "beta(1/2, 1/2)" to "π ≈ 3.141592654",
        "beta(1/3, 2/3)" to "2π√3/3 ≈ 3.627598728",
        "beta(2.5, 1.5)" to "π/16 ≈ 0.1963495408",
        "beta(-1/2, 1)" to "-2",
        "beta(x, 2)" to "1/(x(x + 1))",
        "beta(0, 1)" to "FAILED UNDEFINED",
    ))

    /** The orthogonal polynomials against the textbooks' tables (Abramowitz and Stegun 22). */
    @Test
    fun orthogonalPolynomials() = check(listOf(
        "legendrep(3, x)" to "(5/2)x^3 - (3/2)x",
        "legendrep(4, x)" to "(35/8)x^4 - (15/4)x^2 + 3/8",
        "legendrep(2, 1/2)" to "-1/8 = -0.125",
        "legendrep(5, 1)" to "1",
        "chebyshevt(5, x)" to "16x^5 - 20x^3 + 5x",
        "chebyshevt(3, 1/2)" to "-1",
        "hermiteh(4, x)" to "16x^4 - 48x^2 + 12",
        "hermiteh(3, 1)" to "-4",
        "laguerrel(3, x)" to "-(1/6)x^3 + (3/2)x^2 - 3x + 1",
        "laguerrel(4, 0)" to "1",
        "legendrep(n, x)" to "FAILED ARGUMENTS",
        "legendrep(-1, x)" to "FAILED UNDEFINED",
        "hermiteh(2.5, x)" to "FAILED UNDEFINED",
    ))

    /** Orthogonality on [−1, 1] and Tₙ(cos θ) = cos(nθ), computed here by quadrature and cosines. */
    @Test
    fun orthogonalityAndChebyshevCosines() {
        fun at(c: List<Rational>, x: Double) = c.indices.reversed().fold(0.0) { v, k -> v * x + c[k].toDouble() }
        val p2 = Orthogonal.recurrence("legendrep", 2)
        val p3 = Orthogonal.recurrence("legendrep", 3)
        assertEquals(0.0, Quadrature.integrate({ x -> at(p2, x) * at(p3, x) }, -1.0, 1.0)!!, 1e-12)
        assertEquals(2.0 / 7, Quadrature.integrate({ x -> at(p3, x) * at(p3, x) }, -1.0, 1.0)!!, 1e-12)
        val t7 = Orthogonal.recurrence("chebyshevt", 7)
        for (th in listOf(0.1, 0.9, 2.5)) assertEquals(kotlin.math.cos(7 * th), at(t7, kotlin.math.cos(th)), 1e-12)
        // Hermite: ∫ H₂ H₃ e^(−x²) = 0; Laguerre: ∫₀^∞ L₂ L₃ e^(−x) = 0.
        val h2 = Orthogonal.recurrence("hermiteh", 2)
        val h3 = Orthogonal.recurrence("hermiteh", 3)
        assertEquals(0.0, Quadrature.integrate({ x -> at(h2, x) * at(h3, x) * kotlin.math.exp(-x * x) }, -10.0, 10.0)!!, 1e-9)
        val l2 = Orthogonal.recurrence("laguerrel", 2)
        val l3 = Orthogonal.recurrence("laguerrel", 3)
        assertEquals(0.0, Quadrature.integrate({ x -> at(l2, x) * at(l3, x) * kotlin.math.exp(-x) }, 0.0, 60.0)!!, 1e-8)
        // The recurrence and the explicit sum agree up to degree 60.
        for (name in listOf("legendrep", "chebyshevt", "hermiteh", "laguerrel")) for (n in 0..60) {
            assertEquals("$name $n", Orthogonal.explicit(name, n), Orthogonal.recurrence(name, n))
        }
    }

    @Test
    fun polynomialGcdAndLcm() = check(listOf(
        "gcd(x^2 - 1, x^2 + 2x + 1)" to "x + 1",
        "gcd(x^4 - 1, x^6 - 1)" to "x^2 - 1",
        "gcd(x^4 - 1, x^6 - 1, x^3 - 1)" to "x - 1",
        "gcd(2x + 2, 4x + 4)" to "x + 1",
        "gcd(x^2 + 1, x - 1)" to "1",
        "lcm(x^2 - 1, x^2 + 2x + 1)" to "x^3 + x^2 - x - 1",
        "lcm(x - 1, x + 1)" to "x^2 - 1",
        "gcd(x^2 - 1, y)" to "FAILED NOT_POLYNOMIAL",
        "gcd(12, 18)" to "6",
    ))

    /** A random gcd: products of known factors, the gcd being their common part. */
    @Test
    fun polynomialGcdOfKnownFactors() {
        val random = java.util.Random(3)
        repeat(40) {
            val roots = List(5) { random.nextInt(9) - 4 }
            fun factorText(r: Int) = "(x ${if (r >= 0) "- $r" else "+ ${-r}"})"
            val common = roots.take(2)
            val a = (common + roots[2]).joinToString("") { factorText(it) }
            val b = (common + roots[3] + roots[4]).joinToString("") { factorText(it) }
            val x = Expr.Sym("x")
            val pa = PolynomialGcd.coefficients(constantLetters(a), x)!!
            val pb = PolynomialGcd.coefficients(constantLetters(b), x)!!
            val g = PolynomialGcd.gcd(listOf(pa, pb))
            // Every common factor divides the gcd, and the gcd divides both.
            for (r in common) assertTrue(Poly.divide(g, listOf(Rational.of(-r.toLong()), Rational.ONE)).second.isEmpty())
            assertTrue(Poly.divide(pa, g).second.isEmpty() && Poly.divide(pb, g).second.isEmpty())
            // And the cofactors share no root: their gcd is 1.
            val ca = Poly.divide(pa, g).first
            val cb = Poly.divide(pb, g).first
            assertEquals(listOf(Rational.ONE), PolynomialGcd.gcd(listOf(ca, cb)))
        }
    }

    private fun constantLetters(text: String): Expr {
        val typed = TextInput.read(text)
        return Parser(MathCalc.TOKEN.findAll(typed.latex).map { it.value }.toList()).statements().single().sides.single()!!
    }

    @Test
    fun bases() = check(listOf(
        "tobase(255, 2)" to "255 = 11111111_2",
        "tobase(255, 16)" to "255 = FF_16",
        "tobase(-10, 3)" to "-10 = -101_3",
        "tobase(2^10 + 1, 16)" to "1025 = 401_16",
        "tobase(0, 7)" to "0 = 0_7",
        "tobase(2.5, 2)" to "FAILED UNDEFINED",
        "tobase(x, 2)" to "FAILED ARGUMENTS",
        "tobase(255, 37)" to "FAILED ARGUMENTS",
        "frombase(FF, 16)" to "255",
        "frombase(ff, 16)" to "255",
        "frombase(11111111, 2)" to "255",
        "frombase(-101, 2)" to "-5",
        "frombase(zz, 36)" to "1295",
        "frombase(12, 2)" to "FAILED ARGUMENTS",
    ))

    /** tobase against Java's own radix conversion, and frombase back. */
    @Test
    fun basesAgreeWithJava() {
        val random = java.util.Random(11)
        repeat(100) {
            val n = BigInteger(random.nextInt(48) + 1, random).let { if (random.nextBoolean()) it.negate() else it }
            val base = random.nextInt(35) + 2
            assertEquals("$n = ${n.toString(base).uppercase()}_$base", answer("tobase($n, $base)"))
            assertEquals("$n", answer("frombase(${n.toString(base)}, $base)"))
        }
        // Large ones too, their value compared exactly.
        repeat(30) {
            val n = BigInteger(random.nextInt(400) + 64, random)
            val base = random.nextInt(35) + 2
            val o = Reports.of(TextInput.read("frombase(${n.toString(base)}, $base)")).result.outcome as Outcome.Value
            assertEquals(n, (o.value as Num.Exact).r.num)
            assertEquals("$n = ${n.toString(base).uppercase()}_$base", answer("tobase($n, $base)"))
        }
    }

    @Test
    fun stepsOfBasesAndGcd() {
        fun keys(text: String): String {
            val t = TextInput.read(text)
            return Steps.of(t.latex, t.choice)?.joinToString(" ") { it.why.key.name } ?: "none"
        }
        assertEquals("START BASE_DIVIDE BASE_DIVIDE BASE_DIVIDE BASE_DIVIDE BASE_REMAINDERS", keys("tobase(10, 2)"))
        assertEquals("START BASE_PLACE_VALUES RESULT", keys("frombase(FF, 16)"))
        assertEquals("START GCD_DIVIDE GCD_DIVIDE GCD_LAST", keys("gcd(x^3 - 1, x^2 - 1)"))
        assertEquals("START GCD_DIVIDE GCD_DIVIDE GCD_LAST LCM_PRODUCT", keys("lcm(x^2 - 1, x^2 + 2x + 1)"))
        // The divisions shown hold: a = q·b + r.
        val t = TextInput.read("tobase(10, 2)")
        val divisions = Steps.of(t.latex, t.choice)!!.filter { it.why.key == StepKey.BASE_DIVIDE }.map { it.latex }
        assertEquals(listOf("10 = 5 \\cdot 2 + 0", "5 = 2 \\cdot 2 + 1", "2 = 1 \\cdot 2 + 0", "1 = 0 \\cdot 2 + 1"), divisions)
    }

    @Test
    fun namesAreReadOneWay() = check(listOf(
        // One name each: ISO 80000-2's arsinh; asinh, arcsinh and the rest are no names of the calculator.
        "asinh(1)" to "FAILED UNKNOWN_FUNCTION",
        "acosh(2)" to "FAILED UNKNOWN_FUNCTION",
        "arctanh(1/2)" to "FAILED UNKNOWN_FUNCTION",
        // ei and si in small letters are letters: e·i, s·i.
        "si(2)" to "2is",
        // Arguments other than their function takes.
        "beta(1, 2, 3)" to "FAILED ARGUMENTS",
        "legendrep(3)" to "FAILED ARGUMENTS",
        "re(1, 2)" to "FAILED ARGUMENTS",
        "round(1, 2, 3)" to "FAILED ARGUMENTS",
        // \operatorname{…} as the Calculator writes a function reads back as that function.
        "\\operatorname{arsinh}\\left(3/4\\right)" to "ln(2) ≈ 0.6931471806",
        "\\operatorname{erf}\\left(0\\right)" to "0",
    ))

    /** One name for each function (owner, 6 November 2026): every second name is refused, each with a call of its own. */
    @Test
    fun secondNamesAreRefused() {
        val calls = mapOf(
            "asin" to "asin(1/2)", "acos" to "acos(0)", "atan" to "atan(1)", "acot" to "acot(1)", "asec" to "asec(2)",
            "acsc" to "acsc(2)", "asinh" to "asinh(1)", "acosh" to "acosh(2)", "atanh" to "atanh(1/2)", "acoth" to "acoth(2)",
            "asech" to "asech(1/2)", "acsch" to "acsch(1)", "arcsinh" to "arcsinh(1)", "arccosh" to "arccosh(2)",
            "arctanh" to "arctanh(1/2)", "arccoth" to "arccoth(2)", "lg" to "lg(100)", "tg" to "tg(pi/4)", "ctg" to "ctg(pi/4)",
            "arctg" to "arctg(1)", "arcctg" to "arcctg(1)", "productlog" to "productlog(1)", "sign" to "sign(-3)",
            "ncr" to "ncr(5, 2)", "choose" to "choose(5, 2)", "perm" to "perm(5, 2)", "fib" to "fib(10)", "moebius" to "moebius(6)",
            "variance" to "variance(1, 2, 3)", "stdev" to "stdev(1, 2, 3)",
        )
        assertEquals(TextInput.SECOND_NAMES, calls.keys)
        val wrong = calls.values.mapNotNull { text -> answer(text).takeIf { it != "FAILED UNKNOWN_FUNCTION" }?.let { "$text: $it" } }
        assertEquals("", wrong.joinToString("\n"))
        // Without a bracket lg is refused too (the reader would take it for log); the others are letters there.
        assertEquals("FAILED UNKNOWN_FUNCTION", answer("lg 100"))
        assertEquals("FAILED UNKNOWN_FUNCTION", answer("\\operatorname{lg}(100)"))
    }

    /** The one names work, and so does the reader's LaTeX for them: \lg, \sin^{-1}, φ(n). */
    @Test
    fun theOneNamesAndTheReadersLatexWork() {
        check(listOf(
            "arcsin(1/2)" to "π/6 ≈ 0.5235987756",
            "tan(pi/4)" to "1",
            "binom(5, 2)" to "10",
            "npr(5, 2)" to "20",
            "fibonacci(10)" to "55",
            "totient(12)" to "4",
            "mu(6)" to "1",
            "var(1, 2, 3)" to "1",
            "sd(1, 2, 3)" to "1",
            "sgn(-3)" to "-1",
            "lambertw(0)" to "0",
            "log(100)" to "2",
        ))
        assertEquals("2", answerOf(MathCalc.calculate("\\lg(100)")))
        assertEquals("π/6 ≈ 0.5235987756", answerOf(MathCalc.calculate("\\sin^{-1}\\left(\\frac{1}{2}\\right)")))
        assertEquals("4", answerOf(MathCalc.calculate("\\varphi(12)")))
    }

    @Test
    fun theReadingIsShownWithKatexNames() {
        assertEquals("\\operatorname{arsinh}(3/4)", TextInput.display(TextInput.read("arsinh(3/4)")))
        assertEquals("\\operatorname{erf}(1)", TextInput.display(TextInput.read("erf(1)")))
        assertEquals("\\coth(2)", TextInput.display(TextInput.read("coth(2)")))
    }

    /** Common cases stay in milliseconds. */
    @Test
    fun quick() {
        val inputs = listOf("Si(3)", "Ei(-2)", "bessely(2, 3.5)", "elliptick(0.9)", "round(pi, 10)", "arg(3 + 4i)", "legendrep(20, 1/3)",
            "gcd(x^6 - 1, x^4 - 1)", "tobase(2^200, 36)")
        for (text in inputs) {
            answer(text)
            val start = System.nanoTime()
            answer(text)
            val ms = (System.nanoTime() - start) / 1e6
            assertTrue("$text took $ms ms", ms < 300)
        }
    }
}

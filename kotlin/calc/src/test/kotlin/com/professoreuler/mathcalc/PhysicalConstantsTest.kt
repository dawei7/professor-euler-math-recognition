package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext

/**
 * The physical constants against CODATA 2022 as NIST prints it (https://physics.nist.gov/cuu/Constants/Table/allascii.txt, copied
 * here as printed, spaces and all), and against each other: the relations of physics between them (α = e²/(2ε₀hc), a₀ = ħ/(mₑcα) …)
 * must hold within their uncertainties, which a wrong digit would break. Numbers worked out from measured constants are compared
 * with the values CODATA derives itself (mₑc², 1/α, mₚ/mₑ).
 */
class PhysicalConstantsTest {
    private fun answer(text: String): String {
        val r = Reports.of(TextInput.read(text)).result
        return if (r.outcome is Outcome.Failed) "FAILED ${(r.outcome as Outcome.Failed).failure}" else answerOf(r)
    }

    private fun outcome(text: String): Outcome = Reports.of(TextInput.read(text)).result.outcome

    /** NIST's own lines: name in `constant(…)`, value and uncertainty as printed ("(exact)" for an exact one). */
    private val NIST = listOf(
        "speedoflight" to ("299 792 458" to "(exact)"),
        "planckconstant" to ("6.626 070 15 e-34" to "(exact)"),
        "elementarycharge" to ("1.602 176 634 e-19" to "(exact)"),
        "boltzmannconstant" to ("1.380 649 e-23" to "(exact)"),
        "avogadroconstant" to ("6.022 140 76 e23" to "(exact)"),
        "caesiumfrequency" to ("9 192 631 770" to "(exact)"),
        "standardgravity" to ("9.806 65" to "(exact)"),
        "gravitationalconstant" to ("6.674 30 e-11" to "0.000 15 e-11"),
        "electronmass" to ("9.109 383 7139 e-31" to "0.000 000 0028 e-31"),
        "protonmass" to ("1.672 621 925 95 e-27" to "0.000 000 000 52 e-27"),
        "neutronmass" to ("1.674 927 500 56 e-27" to "0.000 000 000 85 e-27"),
        "atomicmassconstant" to ("1.660 539 068 92 e-27" to "0.000 000 000 52 e-27"),
        "finestructureconstant" to ("7.297 352 5643 e-3" to "0.000 000 0011 e-3"),
        "vacuumpermittivity" to ("8.854 187 8188 e-12" to "0.000 000 0014 e-12"),
        "vacuumpermeability" to ("1.256 637 061 27 e-6" to "0.000 000 000 20 e-6"),
        "bohrradius" to ("5.291 772 105 44 e-11" to "0.000 000 000 82 e-11"),
        "rydbergconstant" to ("10 973 731.568 157" to "0.000 012"),
        "bohrmagneton" to ("9.274 010 0657 e-24" to "0.000 000 0029 e-24"),
        "nuclearmagneton" to ("5.050 783 7393 e-27" to "0.000 000 0016 e-27"),
        "comptonwavelength" to ("2.426 310 235 38 e-12" to "0.000 000 000 76 e-12"),
        "hartreeenergy" to ("4.359 744 722 2060 e-18" to "0.000 000 000 0048 e-18"),
        "classicalelectronradius" to ("2.817 940 3205 e-15" to "0.000 000 0013 e-15"),
    )

    /** The exact ones NIST prints with "…": their first digits. */
    private val TRUNCATED = listOf(
        "reducedplanckconstant" to "1.054 571 817 e-34",
        "gasconstant" to "8.314 462 618",
        "faradayconstant" to "96 485.332 12",
        "stefanboltzmannconstant" to "5.670 374 419 e-8",
    )

    private fun nist(s: String): BigDecimal = BigDecimal(s.replace(" e", "e").replace(" ", ""))

    private fun valueOf(q: PhysicalConstants.Quantity): BigDecimal =
        BigDecimal(java.math.BigInteger(q.value.digits), q.value.digits.length - 1 - q.value.exponent).let { if (q.value.negative) it.negate() else it }

    private fun uncertaintyOf(q: PhysicalConstants.Quantity): BigDecimal? =
        q.uncertainty?.let { BigDecimal(java.math.BigInteger(it.digits), it.digits.length - 1 - it.exponent) }

    private fun constant(name: String): PhysicalConstants.Quantity = (outcome("constant($name)") as Outcome.PhysicalConstant).quantity

    @Test
    fun theValuesAreCodata2022sAsNistPrintsThem() {
        val wrong = ArrayList<String>()
        for ((name, printed) in NIST) {
            val q = constant(name)
            val (value, u) = printed
            // The same digits, trailing zeros too (6.674 30 has six).
            val expected = nist(value)
            if (valueOf(q) != expected || valueOf(q).scale() != expected.scale()) wrong += "$name: ${PhysicalConstants.text(q)}, not $value"
            if (u == "(exact)") {
                if (!q.exact || q.uncertainty != null) wrong += "$name is exact"
            } else {
                val got = uncertaintyOf(q)
                if (got == null || got.compareTo(nist(u)) != 0 || q.exact) wrong += "$name: uncertainty $got, not $u"
                // Its last digit stands at the value's last digit.
                if (got != null && got.scale() != valueOf(q).scale()) wrong += "$name: uncertainty at another place"
            }
        }
        for ((name, prefix) in TRUNCATED) {
            val q = constant(name)
            val digits = nist(prefix).unscaledValue().toString()
            if (!q.exact || !q.value.digits.startsWith(digits.dropLast(1))) wrong += "$name: ${PhysicalConstants.text(q)}, not $prefix…"
        }
        // Every constant is in one of the two lists.
        val listed = NIST.map { it.first } + TRUNCATED.map { it.first }
        if (PhysicalConstants.NAMES.toSet() != listed.toSet()) wrong += "listed ${listed.toSet()} vs ${PhysicalConstants.NAMES}"
        assertEquals("", wrong.joinToString("\n"))
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Physics between them: each relation holds within the sum of the relative uncertainties (a bound whatever their
    // correlations), which a digit typed wrong in the first ten would break.

    private val mc = MathContext(40)
    private val PI = BigDecimal("3.141592653589793238462643383279502884197")

    private fun v(name: String) = valueOf(constant(name))
    private fun rel(name: String): BigDecimal = uncertaintyOf(constant(name))?.divide(v(name).abs(), mc) ?: BigDecimal.ZERO

    private fun holds(what: String, lhs: BigDecimal, rhs: BigDecimal, relative: BigDecimal, wrong: MutableList<String>) {
        val diff = (lhs - rhs).abs().divide(rhs.abs(), mc)
        // The constants' rounding to their published digits adds a little: 10⁻¹³ relative.
        if (diff > relative + BigDecimal("1e-13")) wrong += "$what: off by $diff, allowed $relative"
    }

    @Test
    fun theRelationsOfPhysicsHold() {
        val c = v("speedoflight")
        val h = v("planckconstant")
        val e = v("elementarycharge")
        val k = v("boltzmannconstant")
        val na = v("avogadroconstant")
        val hbar = h.divide(PI.multiply(BigDecimal(2)), mc)
        val alpha = v("finestructureconstant")
        val me = v("electronmass")
        val mp = v("protonmass")
        val wrong = ArrayList<String>()
        holds("ħ = h/2π", v("reducedplanckconstant"), hbar, BigDecimal("1e-11"), wrong)
        holds("R = N_A k", v("gasconstant"), na * k, BigDecimal.ZERO, wrong)
        holds("F = N_A e", v("faradayconstant"), na * e, BigDecimal.ZERO, wrong)
        val sigma = BigDecimal(2).multiply(PI.pow(5, mc), mc).multiply(k.pow(4, mc), mc)
            .divide(BigDecimal(15).multiply(h.pow(3, mc), mc).multiply(c.pow(2, mc), mc), mc)
        holds("σ = 2π⁵k⁴/(15h³c²)", v("stefanboltzmannconstant"), sigma, BigDecimal("1e-11"), wrong)
        holds("ε₀ = e²/(2αhc)", v("vacuumpermittivity"), e.pow(2).divide(BigDecimal(2) * alpha * h * c, mc),
            rel("vacuumpermittivity") + rel("finestructureconstant"), wrong)
        holds("μ₀ = 2αh/(e²c)", v("vacuumpermeability"), BigDecimal(2) * alpha * h.divide(e.pow(2) * c, mc),
            rel("vacuumpermeability") + rel("finestructureconstant"), wrong)
        holds("μ₀ε₀c² = 1", v("vacuumpermeability") * v("vacuumpermittivity") * c.pow(2), BigDecimal.ONE,
            rel("vacuumpermeability") + rel("vacuumpermittivity"), wrong)
        holds("a₀ = ħ/(mₑcα)", v("bohrradius"), hbar.divide(me * c * alpha, mc),
            rel("bohrradius") + rel("electronmass") + rel("finestructureconstant"), wrong)
        holds("R∞ = α²mₑc/(2h)", v("rydbergconstant"), alpha.pow(2) * me * c.divide(BigDecimal(2) * h, mc),
            rel("rydbergconstant") + BigDecimal(2) * rel("finestructureconstant") + rel("electronmass"), wrong)
        holds("λ_C = h/(mₑc)", v("comptonwavelength"), h.divide(me * c, mc), rel("comptonwavelength") + rel("electronmass"), wrong)
        holds("E_h = α²mₑc²", v("hartreeenergy"), alpha.pow(2) * me * c.pow(2),
            rel("hartreeenergy") + BigDecimal(2) * rel("finestructureconstant") + rel("electronmass"), wrong)
        holds("r_e = α²a₀", v("classicalelectronradius"), alpha.pow(2) * v("bohrradius"),
            rel("classicalelectronradius") + BigDecimal(2) * rel("finestructureconstant") + rel("bohrradius"), wrong)
        holds("μ_B = eħ/(2mₑ)", v("bohrmagneton"), e * hbar.divide(BigDecimal(2) * me, mc), rel("bohrmagneton") + rel("electronmass"), wrong)
        holds("μ_N = eħ/(2mₚ)", v("nuclearmagneton"), e * hbar.divide(BigDecimal(2) * mp, mc), rel("nuclearmagneton") + rel("protonmass"), wrong)
        // The masses through the molar mass constant and the relative atomic masses (other CODATA 2022 lines).
        val mu = nist("1.000 000 001 05 e-3").divide(na, mc)
        holds("m_u = M_u/N_A", v("atomicmassconstant"), mu, rel("atomicmassconstant") + BigDecimal("3.1e-10"), wrong)
        holds("mₚ = A_r(p) m_u", mp, nist("1.007 276 466 5789") * v("atomicmassconstant"), rel("protonmass") + rel("atomicmassconstant"), wrong)
        holds("mₙ = A_r(n) m_u", v("neutronmass"), nist("1.008 664 916 06") * v("atomicmassconstant"),
            rel("neutronmass") + rel("atomicmassconstant") + BigDecimal("4e-10"), wrong)
        holds("mₑ = A_r(e) m_u", me, nist("5.485 799 090 441 e-4") * v("atomicmassconstant"), rel("electronmass") + rel("atomicmassconstant"), wrong)
        // G against the Earth's GM (IERS: 3.986004418(8) × 10¹⁴ m³/s²) and its mass 5.9722(6) × 10²⁴ kg.
        holds("G = GM/M", v("gravitationalconstant"), nist("3.986004418e14").divide(nist("5.9722e24"), mc), BigDecimal("2e-4"), wrong)
        assertEquals("", wrong.joinToString("\n"))
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Worked out from measured constants: against CODATA's own derived values.

    /** Ours and CODATA's agree within our uncertainty, and ours is not smaller than CODATA's (no digit more than is certain). */
    private fun agrees(text: String, value: String, u: String, wrong: MutableList<String>) {
        val o = outcome(text) as? Outcome.PhysicalValue ?: run { wrong += "$text: ${answer(text)}"; return }
        val ours = valueOf(o.quantity)
        val ourU = uncertaintyOf(o.quantity)!!
        if ((ours - nist(value)).abs() > ourU) wrong += "$text: ${PhysicalConstants.text(o.quantity)} vs $value"
        if (ourU < nist(u)) wrong += "$text: uncertainty $ourU below CODATA's $u"
    }

    @Test
    fun numbersFromMeasuredConstantsAgreeWithCodatasOwn() {
        val wrong = ArrayList<String>()
        // electron mass energy equivalent
        agrees("constant(electronmass)*constant(speedoflight)^2", "8.187 105 7880 e-14", "0.000 000 0026 e-14", wrong)
        // proton mass energy equivalent
        agrees("constant(protonmass)*constant(speedoflight)^2", "1.503 277 618 02 e-10", "0.000 000 000 47 e-10", wrong)
        // inverse fine-structure constant
        agrees("1/constant(finestructureconstant)", "137.035 999 177", "0.000 000 021", wrong)
        // proton-electron mass ratio
        agrees("constant(protonmass)/constant(electronmass)", "1836.152 673 426", "0.000 000 032", wrong)
        // Rydberg constant times hc in J = E_h/2
        agrees("constant(hartreeenergy)/2", "2.179 872 361 1030 e-18", "0.000 000 000 0024 e-18", wrong)
        // Bohr magneton in eV/T
        agrees("constant(bohrmagneton)/constant(elementarycharge)", "5.788 381 7982 e-5", "0.000 000 0018 e-5", wrong)
        assertEquals("", wrong.joinToString("\n"))
        // The electron's rest energy exactly as CODATA gives it, and the bound in its steps.
        assertEquals("8.1871057880(26)e-14 from electronmass, speedoflight", answer("constant(electronmass) * constant(speedoflight)^2"))
        assertEquals("START CONSTANT_VALUES UNCERTAINTY_BOUND RESULT",
            Steps.of(TextInput.read("constant(electronmass)*constant(speedoflight)^2").latex)?.joinToString(" ") { it.why.key.name })
    }

    @Test
    fun anEscapeVelocityHasTheDigitsGLeaves() {
        // v = √(2GM/r) for the Earth: G's relative uncertainty 2.2 × 10⁻⁵, halved by the root: about 0.13 m/s.
        val o = outcome("sqrt(2*constant(gravitationalconstant)*5.9722*10^24/(6.371*10^6))") as Outcome.PhysicalValue
        val v = valueOf(o.quantity)
        val expected = Math.sqrt(2 * 6.6743e-11 * 5.9722e24 / 6.371e6)
        assertTrue(Math.abs(v.toDouble() - expected) < 0.01)
        val u = uncertaintyOf(o.quantity)!!.toDouble()
        // u = v/2 · u(G)/G, rounded up to two digits.
        assertEquals(0.13, u, 1e-12)
        assertTrue(u >= expected / 2 * 0.00015 / 6.6743)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Units, formulas, refusals.

    @Test
    fun inAnotherUnit() {
        // c · 3.6 exactly
        assertEquals("speedoflight = 1079252848.8 km/h exact", answer("constant(speedoflight, km/h)"))
        // 1 m³/(kg s²) = 10⁶ cm³ / (10³ g s²)
        assertEquals("gravitationalconstant = 6.67430(15)e-8 cm^3/g/s^2", answer("constant(gravitationalconstant, cm^3/g/s^2)"))
        assertEquals("electronmass = 9.1093837139(28)e-28 g", answer("constant(electronmass, g)"))
        // h in eV·s: 6.62607015e-34 / 1.602176634e-19, CODATA's 4.135 667 696… e-15
        val q = (outcome("constant(planckconstant, eV*s)") as Outcome.PhysicalConstant).quantity
        assertTrue(q.exact && q.value.digits.startsWith("413566769"))
        assertEquals("UNIT_FACTOR RESULT", Steps.of(TextInput.read("constant(speedoflight, km/h)").latex)?.joinToString(" ") { it.why.key.name })
    }

    @Test
    fun inAFormula() {
        assertEquals("599584916 from speedoflight exact", answer("2*constant(speedoflight)"))
        // R = N_A k, exactly CODATA's 8.314 462 618…; the molar volume R·T/p at 273.15 K and 101.325 kPa, 22.413 969 54… × 10⁻³.
        assertEquals("8.31446261815324 from avogadroconstant, boltzmannconstant exact", answer("constant(avogadroconstant)*constant(boltzmannconstant)"))
        assertEquals("0.0224139695450 from gasconstant exact", answer("constant(gasconstant)*273.15/101325"))
        assertEquals("START CONSTANT_VALUES RESULT", Steps.of(TextInput.read("2*constant(speedoflight)").latex)?.joinToString(" ") { it.why.key.name })
        assertEquals("299792458x", answer("constant(speedoflight)*x"))
        assertEquals("t = 1", answer("solve(constant(speedoflight)*t = 299792458, t)"))
        // h/(2e), the magnetic flux quantum: exact, CODATA's 2.067 833 848… e-15
        val r = Reports.of(TextInput.read("constant(planckconstant)/(2*constant(elementarycharge))"))
        val digits = Reports.compute(r, r.panels.first { it.kind == PanelKind.DIGITS }) as PanelResult.Digits
        assertTrue(digits.value.re.toString(), digits.value.re.toString().startsWith("2.067833848"))
        // A measured constant claims no certified digits.
        assertTrue(Reports.of(TextInput.read("constant(gravitationalconstant)")).panels.none { it.kind == PanelKind.DIGITS })
        assertTrue(Reports.of(TextInput.read("constant(planckconstant)")).panels.any { it.kind == PanelKind.DIGITS })
    }

    @Test
    fun whatIsRefused() {
        assertEquals("FAILED UNKNOWN_CONSTANT", answer("constant(c)"))
        assertEquals("FAILED UNKNOWN_CONSTANT", answer("2*constant(lightspeed)"))
        assertEquals("FAILED UNITS", answer("constant(speedoflight, kg)"))
        assertEquals("FAILED UNITS", answer("constant(finestructureconstant, m)"))
        assertEquals("FAILED ARGUMENTS", answer("constant(speedoflight, km/h, 2)"))
        assertEquals("FAILED ARGUMENTS", answer("constant(2)"))
        assertEquals("FAILED MEASURED_CONSTANT", answer("x*constant(gravitationalconstant)"))
        assertEquals("FAILED MEASURED_CONSTANT", answer("solve(constant(electronmass)*x = 1, x)"))
    }

    @Test
    fun theNamesClashWithNothing() {
        for (n in PhysicalConstants.NAMES) {
            assertTrue(n, n.all { it in 'a'..'z' } && n.length > 4)
            assertTrue("$n is a unit", !Units.known(n))
            assertTrue("$n is a function", n !in Discrete.NAMES && n !in CommandFunctions.NAMES)
            // Every unit a constant has is one the Calculator reads.
            val e = PhysicalConstants.entry(n)!!
            assertTrue("$n's unit ${e.unit}", e.unit.isEmpty() || Units.known(e.unit))
        }
        assertTrue(!Units.known(PhysicalConstants.CALL) && PhysicalConstants.CALL !in CommandFunctions.NAMES)
    }

    @Test
    fun theCommonCasesAreFast() {
        val start = System.nanoTime()
        for (n in PhysicalConstants.NAMES) answer("constant($n)")
        answer("constant(electronmass)*constant(speedoflight)^2")
        assertTrue((System.nanoTime() - start) / 1_000_000 < 3000)
    }
}

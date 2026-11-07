package com.professoreuler.mathcalc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Constants, complex plots and Venn diagrams. The SI's defining constants against their definitions (exact since 2019), the
 * others against the relations of physics among them (R = N_A·k, F = N_A·e, σ = 2π⁵k⁴/(15h³c²), ε₀ = 1/(μ₀c²), R∞ = α²m_ec/(2h),
 * a₀ = ħ/(αm_ec), μ_B = eħ/(2m_e), λ_C = h/(m_ec), E_h = α²m_ec², r_e = αħ/(m_ec), μ₀ = 2αh/(e²c)), which CODATA's values satisfy
 * to within their uncertainties, and conversions by the units' definitions. A complex plot's zeros and poles are those its
 * function was built from, with their orders; a Venn diagram's regions are those the test's own membership table includes.
 */
internal object WrittenPlots {
    private const val AREA = "constants and plots"

    private fun Decimal.Digits.double(): Double = ((if (negative) "-" else "") + digits[0] + "." + digits.substring(1).ifEmpty { "0" } + "e$exponent").toDouble()

    /** A constant's value as the engine gives it, in its SI unit. */
    private fun value(name: String): Double {
        val o = Reports.of(TextInput.read("constant($name)")).result.outcome as? Outcome.PhysicalConstant ?: return Double.NaN
        return o.quantity.value.double()
    }

    private fun constants(): List<Task> {
        val out = ArrayList<Task>()
        val defined = listOf("speedoflight" to 299792458.0, "planckconstant" to 6.62607015e-34, "elementarycharge" to 1.602176634e-19,
            "boltzmannconstant" to 1.380649e-23, "avogadroconstant" to 6.02214076e23, "caesiumfrequency" to 9192631770.0, "standardgravity" to 9.80665)
        for ((name, v) in defined) out += Task(AREA, "constant($name)", "$v exact", judge = { c ->
            (c.outcome as? Outcome.PhysicalConstant)?.quantity?.let { it.exact && close(it.value.double(), v, 1e-15) } == true
        })
        fun relation(name: String, rel: Double = 1e-8, f: () -> Double) {
            out += Task(AREA, "constant($name)", "a relation", judge = { c ->
                (c.outcome as? Outcome.PhysicalConstant)?.quantity?.let { close(it.value.double(), f(), rel) } == true
            })
        }
        val h = { value("planckconstant") }; val e = { value("elementarycharge") }; val k = { value("boltzmannconstant") }
        val na = { value("avogadroconstant") }; val c = { value("speedoflight") }; val me = { value("electronmass") }; val mp = { value("protonmass") }
        val alpha = { value("finestructureconstant") }; val mu0 = { value("vacuumpermeability") }; val hbar = { h() / (2 * PI) }
        relation("reducedplanckconstant", 1e-11) { hbar() }
        relation("gasconstant", 1e-13) { na() * k() }
        relation("faradayconstant", 1e-13) { na() * e() }
        relation("stefanboltzmannconstant", 1e-11) { 2 * Math.pow(PI, 5.0) * Math.pow(k(), 4.0) / (15 * Math.pow(h(), 3.0) * c() * c()) }
        relation("vacuumpermittivity") { 1 / (mu0() * c() * c()) }
        relation("vacuumpermeability") { 2 * alpha() * h() / (e() * e() * c()) }
        relation("rydbergconstant") { alpha() * alpha() * me() * c() / (2 * h()) }
        relation("bohrradius") { hbar() / (alpha() * me() * c()) }
        relation("bohrmagneton") { e() * hbar() / (2 * me()) }
        relation("nuclearmagneton") { e() * hbar() / (2 * mp()) }
        relation("comptonwavelength") { h() / (me() * c()) }
        relation("hartreeenergy") { alpha() * alpha() * me() * c() * c() }
        relation("classicalelectronradius") { alpha() * hbar() / (me() * c()) }
        // Conversions by the units' definitions: 1 ft = 0.3048 m, 1 mi = 1609.344 m, 1 eV = e J, 1 g = 10⁻³ kg, 1 km/h = 1/3.6 m/s.
        fun converted(call: String, f: () -> Double) {
            out += Task(AREA, call, "converted", judge = { cc -> (cc.outcome as? Outcome.PhysicalConstant)?.quantity?.let { close(it.value.double(), f(), 1e-11) } == true })
        }
        converted("constant(speedoflight, km/h)") { c() * 3.6 }
        converted("constant(speedoflight, mi/h)") { c() * 3600 / 1609.344 }
        converted("constant(standardgravity, ft/s^2)") { 9.80665 / 0.3048 }
        converted("constant(electronmass, g)") { me() * 1000 }
        converted("constant(protonmass, g)") { mp() * 1000 }
        converted("constant(boltzmannconstant, eV/K)") { k() / e() }
        converted("constant(planckconstant, eV*s)") { h() / e() }
        converted("constant(bohrradius, cm)") { value("bohrradius") * 100 }
        // Products of constants: their value from the factors', in doubles.
        for ((text, f) in listOf<Pair<String, () -> Double>>("constant(electronmass)*constant(speedoflight)^2" to { me() * c() * c() },
                "constant(protonmass)/constant(electronmass)" to { mp() / me() }, "constant(planckconstant)*constant(speedoflight)" to { h() * c() },
                "constant(avogadroconstant)*constant(boltzmannconstant)" to { na() * k() }, "constant(elementarycharge)/constant(planckconstant)" to { e() / h() })) {
            // Within the uncertainty it states (or a unit of its last digit where it is exact).
            out += Task(AREA, text, "a product", judge = { cc -> (cc.outcome as? Outcome.PhysicalValue)?.quantity?.let { qv ->
                val v = qv.value.double()
                val unit = Math.pow(10.0, (qv.value.exponent - qv.value.digits.length + 1).toDouble())
                abs(v - f()) <= maxOf(qv.uncertainty?.double() ?: 0.0, unit) * 1.01 + 1e-13 * abs(v)
            } == true })
        }
        out += Task(AREA, "constant(c)", "no constant", fails = setOf(Failure.UNKNOWN_CONSTANT)) { false }
        out += Task(AREA, "constant(speedoflight, kg)", "no such unit", fails = setOf(Failure.UNITS)) { false }
        return out
    }

    private fun plots(r: Random): List<Task> {
        val out = ArrayList<Task>()
        val candidates = listOf(0.0 to 0.0, 1.0 to 0.0, -1.0 to 0.0, 2.0 to 0.0, -3.0 to 0.0, 0.0 to 1.0, 0.0 to -1.0, 0.0 to 2.0, 1.0 to 1.0, -1.0 to 1.0,
            2.0 to -1.0, 0.5 to 0.0, -0.5 to 1.5, 3.0 to 2.0, -2.0 to -2.0)
        fun text(p: Pair<Double, Double>): String {
            val (a, b) = p
            return when {
                a == 0.0 && b == 0.0 -> "z"
                b == 0.0 -> "(z - (${fmt(a)}))"
                else -> "(z - (${fmt(a)} + (${fmt(b)})i))"
            }
        }
        repeat(60) {
            val zeros = candidates.shuffled(r).take(r.int(1, 3)).associateWith { r.int(1, 2) }
            val poles = candidates.filter { it !in zeros }.shuffled(r).take(r.int(0, 2)).associateWith { r.int(1, 2) }
            fun product(m: Map<Pair<Double, Double>, Int>) = m.entries.joinToString("") { (p, k) -> text(p) + if (k > 1) "^$k" else "" }
            val f = product(zeros) + if (poles.isEmpty()) "" else "/(${product(poles)})"
            val half = 4
            out += Task(AREA, "complexplot($f, z, 0, $half)", "zeros $zeros poles $poles", judge = { c ->
                val p = (c.outcome as? Outcome.DomainColoring)?.plot
                fun matches(points: List<ComplexPlot.Point>, want: Map<Pair<Double, Double>, Int>) =
                    points.size == want.size && points.all { pt -> want.entries.any { (w, k) -> hypot(pt.re - w.first, pt.im - w.second) < 1e-7 && pt.order == k } }
                p != null && matches(p.zeros, zeros) && matches(p.poles, poles)
            })
        }
        return out
    }

    /** A random formula of sets: its text and its membership for each assignment of A, B, C. */
    private class SetFormula(val text: String, val at: (BooleanArray) -> Boolean)

    private fun Random.formula(letters: Int, depth: Int): SetFormula {
        if (depth == 0 || nextInt(4) == 0) {
            val k = nextInt(letters)
            val name = "ABC"[k].toString()
            return if (nextInt(4) == 0) SetFormula("$name'") { !it[k] } else SetFormula(name) { it[k] }
        }
        val a = formula(letters, depth - 1)
        val b = formula(letters, depth - 1)
        return when (nextInt(5)) {
            0 -> SetFormula("(${a.text} ∪ ${b.text})") { a.at(it) || b.at(it) }
            1 -> SetFormula("(${a.text} ∩ ${b.text})") { a.at(it) && b.at(it) }
            2 -> SetFormula("(${a.text} ∖ ${b.text})") { a.at(it) && !b.at(it) }
            3 -> SetFormula("(${a.text} △ ${b.text})") { a.at(it) != b.at(it) }
            else -> SetFormula("(${a.text} ∪ ${b.text})'") { !(a.at(it) || b.at(it)) }
        }
    }

    private fun venns(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(80) { i ->
            val n = 1 + i % 3
            val f = r.formula(n, r.int(1, 3))
            val text = f.text.removeSurrounding("(", ")").let { if (it.count { ch -> ch == '(' } != it.count { ch -> ch == ')' }) f.text else it }
            out += Task(AREA, "venn($text)", "regions", judge = { c ->
                val d = (c.outcome as? Outcome.Venn)?.diagram
                // The letters the formula holds, in alphabetical order; each region's membership put into the test's formula.
                d != null && d.regions.size == 1 shl d.sets.size && d.regions.all { reg ->
                    val inside = BooleanArray(3)
                    d.sets.forEachIndexed { j, s -> inside["ABC".indexOf(s)] = reg.inside[j] }
                    reg.included == f.at(inside)
                }
            })
        }
        out += Task(AREA, "venn(A ∪ B ∪ C ∪ D)", "too many sets", fails = setOf(Failure.TOO_LARGE)) { false }
        return out
    }

    fun all(): List<Task> {
        val r = Random(77)
        return constants() + plots(r) + venns(r)
    }
}

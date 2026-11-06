package com.professoreuler.mathcalc

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * Physical constants by name, with the values recommended by CODATA 2022 (P. J. Mohr, D. B. Newell, B. N. Taylor, E. Tiesinga,
 * "CODATA recommended values of the fundamental physical constants: 2022", published by NIST at
 * https://physics.nist.gov/constants; the table is a work of the US government, in the public domain).
 *
 * A constant is written `constant(speedoflight)`, or `constant(speedoflight, km/h)` in another unit of the same kind. The name
 * is the constant's English name, lowercase, its words run together (with "constant" where the name has it), so it never clashes
 * with a letter (c, h, G and e are letters and Euler's number), a unit (h is the hour, G a prefix) or a function: it stands only
 * inside `constant(…)`, where nothing else is read. `constant` is a value function, as mean and binom are: on its own it shows the
 * constant ([Outcome.PhysicalConstant]); inside a formula it is its value in SI units (or in the unit asked for).
 *
 * The SI's defining constants (c, h, e, k, N_A, Δν_Cs) and the constants that follow from them alone (ħ, R, F, σ) are exact; g_n
 * is exact by definition. The others are measured: their published value is used as it is, and a number worked out from them is
 * given with a bound of its standard uncertainty ([Outcome.PhysicalValue]), never with more digits than that leaves.
 */
object PhysicalConstants {
    /** The value function's name. */
    const val CALL = "constant"

    /**
     * A constant: its [name] as written in `constant(…)`, its [symbol] (LaTeX), its [unit] as [Units] reads it ("" for a pure
     * number), its [value] in that unit (exact, or the published value as an exact decimal) and its standard [uncertainty]
     * (null where it is exact). [definition] is the exact relation a derived exact constant comes from (ħ = h/(2π)), in LaTeX.
     */
    class Entry internal constructor(
        val name: String,
        val symbol: String,
        val unit: String,
        internal val value: Expr,
        internal val uncertainty: Expr?,
        val definition: String? = null,
        /** The published value's own digits (its last digit is the uncertainty's). */
        internal val published: BigDecimal?,
        internal val publishedUncertainty: BigDecimal?,
    ) {
        val exact: Boolean get() = uncertainty == null

        /** The unit in LaTeX: `\mathrm{m}^{3}\,\mathrm{kg}^{-1}\,\mathrm{s}^{-2}`. */
        val unitLatex: String get() = unitLatex(unit)
    }

    /**
     * A value as it is shown: its [value]'s digits, and the standard [uncertainty]'s two digits (null where it is exact), whose last
     * digit stands where the value's last digit stands (6.674 30(15) × 10⁻¹¹). [exact] where the quantity has no uncertainty; its
     * digits are then all the digits where [Decimal.Digits.exact], else a rounding (ħ, a value in another unit) to [EXACT_DIGITS].
     */
    data class Quantity(val value: Decimal.Digits, val uncertainty: Decimal.Digits?, val exact: Boolean)

    /** Significant digits of an exact value that does not end (ħ, σ, c in mph). */
    const val EXACT_DIGITS = 12

    private fun dec(s: String): BigDecimal = BigDecimal(s)
    private fun exprOf(b: BigDecimal): Expr {
        val unscaled = b.unscaledValue()
        return if (b.scale() >= 0) num(Rational.of(unscaled, BigInteger.TEN.pow(b.scale())))
        else num(Rational.of(unscaled * BigInteger.TEN.pow(-b.scale())))
    }

    private fun exact(name: String, symbol: String, unit: String, value: String) =
        Entry(name, symbol, unit, exprOf(dec(value)), null, published = dec(value), publishedUncertainty = null)

    private fun measured(name: String, symbol: String, unit: String, value: String, uncertainty: String) =
        Entry(name, symbol, unit, exprOf(dec(value)), exprOf(dec(uncertainty)), published = dec(value), publishedUncertainty = dec(uncertainty))

    private fun derived(name: String, symbol: String, unit: String, value: Expr, definition: String) =
        Entry(name, symbol, unit, Calculus.tidy(value), null, definition, published = null, publishedUncertainty = null)

    // The SI's defining constants (exact since 20 May 2019).
    private val C = exact("speedoflight", "c", "m*s^-1", "299792458")
    private val H = exact("planckconstant", "h", "J*s", "6.62607015e-34")
    private val E = exact("elementarycharge", "e", "C", "1.602176634e-19")
    private val K = exact("boltzmannconstant", "k", "J*K^-1", "1.380649e-23")
    private val NA = exact("avogadroconstant", "N_{A}", "mol^-1", "6.02214076e23")

    /** Every constant, in the order of the guide: the defining ones, those that follow from them, then the measured ones. */
    val ALL: List<Entry> = listOf(
        C, H,
        derived("reducedplanckconstant", "\\hbar", "J*s", H.value / mul(Expr.TWO, Expr.PI), "\\frac{h}{2\\pi}"),
        E, K, NA,
        exact("caesiumfrequency", "\\Delta\\nu_{\\mathrm{Cs}}", "Hz", "9192631770"),
        derived("gasconstant", "R", "J*mol^-1*K^-1", NA.value * K.value, "N_{A}\\,k"),
        derived("faradayconstant", "F", "C*mol^-1", NA.value * E.value, "N_{A}\\,e"),
        derived("stefanboltzmannconstant", "\\sigma", "W*m^-2*K^-4",
            mul(Expr.TWO, pow(Expr.PI, num(5)), pow(K.value, num(4))) / mul(num(15), pow(H.value, num(3)), pow(C.value, Expr.TWO)),
            "\\frac{2\\pi^{5} k^{4}}{15 h^{3} c^{2}}"),
        // By definition (3rd CGPM, 1901).
        exact("standardgravity", "g_{n}", "m*s^-2", "9.80665"),
        measured("gravitationalconstant", "G", "m^3*kg^-1*s^-2", "6.67430e-11", "0.00015e-11"),
        measured("electronmass", "m_{e}", "kg", "9.1093837139e-31", "0.0000000028e-31"),
        measured("protonmass", "m_{p}", "kg", "1.67262192595e-27", "0.00000000052e-27"),
        measured("neutronmass", "m_{n}", "kg", "1.67492750056e-27", "0.00000000085e-27"),
        measured("atomicmassconstant", "m_{u}", "kg", "1.66053906892e-27", "0.00000000052e-27"),
        measured("finestructureconstant", "\\alpha", "", "7.2973525643e-3", "0.0000000011e-3"),
        measured("vacuumpermittivity", "\\varepsilon_{0}", "F*m^-1", "8.8541878188e-12", "0.0000000014e-12"),
        measured("vacuumpermeability", "\\mu_{0}", "N*A^-2", "1.25663706127e-6", "0.00000000020e-6"),
        measured("bohrradius", "a_{0}", "m", "5.29177210544e-11", "0.00000000082e-11"),
        measured("rydbergconstant", "R_{\\infty}", "m^-1", "10973731.568157", "0.000012"),
        measured("bohrmagneton", "\\mu_{B}", "J*T^-1", "9.2740100657e-24", "0.0000000029e-24"),
        measured("nuclearmagneton", "\\mu_{N}", "J*T^-1", "5.0507837393e-27", "0.0000000016e-27"),
        measured("comptonwavelength", "\\lambda_{C}", "m", "2.42631023538e-12", "0.00000000076e-12"),
        measured("hartreeenergy", "E_{h}", "J", "4.3597447222060e-18", "0.0000000000048e-18"),
        measured("classicalelectronradius", "r_{e}", "m", "2.8179403205e-15", "0.0000000013e-15"),
    )

    /** The names, as written in `constant(…)`. */
    val NAMES: List<String> = ALL.map { it.name }

    private val BY_NAME = ALL.associateBy { it.name }

    fun entry(name: String): Entry? = BY_NAME[name]

    // ---------------------------------------------------------------------------------------------------------------------
    // Units.

    /** [unit] as [Units] reads it, in LaTeX: factors upright with their powers, a thin space between them, / kept. */
    fun unitLatex(unit: String): String {
        if (unit.isBlank()) return ""
        fun factor(f: String): String {
            val m = Regex("""^(.+?)\^\{?(-?\d+)\}?$""").matchEntire(f.trim())
            val name = (m?.groupValues?.get(1) ?: f.trim()).replace("µ", "\\mu ").replace("μ", "\\mu ").replace("Ω", "\\Omega ").trim()
            return "\\mathrm{$name}" + (m?.let { "^{${it.groupValues[2]}}" } ?: "")
        }
        return unit.split('/').joinToString("/") { part -> part.split('*', '·').filter { it.isNotBlank() }.joinToString("\\,") { factor(it) } }
    }

    /** The factor that writes a value in [from] in [to] (both of one kind), exactly; [Failure.UNITS] for units of another kind. */
    private fun factor(from: String, to: String?): Expr {
        if (to == null || to == from) return Expr.ONE
        val target = Units.unit(to) ?: throw CalcException(Failure.UNREADABLE)
        // A number (α) has no unit to write it in.
        if (from.isEmpty()) throw CalcException(Failure.UNITS)
        val own = Units.unit(from)!!
        if (own.dims != target.dims || target.offset != null) throw CalcException(Failure.UNITS)
        return Calculus.tidy(own.factor / target.factor)
    }

    /** A unit typed in a call, as its LaTeX tokens came (`k m / h`, `m ^ { 3 }`, `\cdot`), as [Units] reads it. */
    internal fun unitText(tokens: List<String>): String = tokens.joinToString("") { t ->
        when (t) {
            "\\cdot", "\\times", "\\ast", "*" -> "*"
            "\\mu" -> "μ"
            "\\Omega" -> "Ω"
            "\\circ" -> "°"
            "\\left", "\\right", "\\,", "\\ ", " ", "{", "}" -> ""
            else -> t
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // A constant on its own.

    /** [e] in [unit] (its own where null): its value, and its uncertainty rounded as CODATA rounds it. */
    fun quantity(e: Entry, unit: String? = null): Quantity {
        val f = factor(e.unit, unit)
        if (f == Expr.ONE && e.published != null) {
            // As published: exactly these digits.
            return Quantity(digitsOf(e.published), e.publishedUncertainty?.let { digitsOf(it) }, e.exact)
        }
        val value = Calculus.tidy(e.value * f)
        val u = e.uncertainty?.let { Calculus.tidy(it * f) } ?: return exactQuantity(value)
        return rounded(value, decimal(u, 6) ?: throw CalcException(Failure.UNDEFINED))
    }

    /** An exact value: all its digits where they end, else [EXACT_DIGITS] of them. */
    private fun exactQuantity(value: Expr): Quantity {
        val r = rational(value)
        if (r != null && r.terminates) {
            val b = BigDecimal(r.num).divide(BigDecimal(r.den)).stripTrailingZeros()
            if (b.precision() <= 40) return Quantity(digitsOf(b), null, true)
        }
        val d = Precise.digits(value, EXACT_DIGITS).re as? Decimal.Digits ?: throw CalcException(Failure.UNDEFINED)
        return Quantity(d, null, true)
    }

    /**
     * A measured [value] with the standard uncertainty [u]: the uncertainty to two significant digits, rounded up (never smaller
     * than it is), the value rounded to the uncertainty's last digit, as CODATA writes its values.
     */
    internal fun rounded(value: Expr, u: BigDecimal): Quantity {
        if (u.signum() <= 0) throw CalcException(Failure.UNDEFINED)
        val two = u.round(MathContext(2, RoundingMode.UP)).let { if (it.precision() < 2) it.setScale(it.scale() + 1) else it }
        // The position of its last digit: 10^-scale.
        val scale = two.scale()
        val v = roundedAt(value, scale) ?: throw CalcException(Failure.UNDEFINED)
        if (v.signum() == 0) throw CalcException(Failure.MEASURED_CONSTANT)
        val unscaled = two.unscaledValue()
        // The uncertainty's two digits (15, 20) at the value's last place.
        val uDigits = Decimal.Digits(false, unscaled.toString(), unscaled.toString().length - 1 - scale, false)
        return Quantity(digitsOf(v), uDigits, false)
    }

    /** [e] rounded to [scale] decimal places (a negative scale rounds to tens, hundreds …). */
    private fun roundedAt(e: Expr, scale: Int): BigDecimal? {
        val r = rational(e)
        if (r != null) return BigDecimal(r.num).divide(BigDecimal(r.den), scale, RoundingMode.HALF_EVEN)
        val magnitude = decimal(e, 4) ?: return null
        val lead = magnitude.precision() - magnitude.scale() - 1
        // Digits enough to reach the scale, and some to round from.
        val digits = (lead + scale + 8).coerceIn(4, Precise.MAX_DIGITS)
        return decimal(e, digits)?.setScale(scale, RoundingMode.HALF_EVEN)
    }

    /** A constant expression's value to [digits] significant digits, certified ([Precise]); null where it is not a real number. */
    internal fun decimal(e: Expr, digits: Int): BigDecimal? {
        val r = rational(e)
        if (r != null) return BigDecimal(r.num).divide(BigDecimal(r.den), MathContext(digits, RoundingMode.HALF_EVEN))
        val p = Precise.digits(e, digits)
        if (p.im != null) return null
        return when (val d = p.re) {
            Decimal.Zero -> BigDecimal.ZERO
            is Decimal.Small -> null
            is Decimal.Digits -> BigDecimal(BigInteger(d.digits), d.digits.length - 1 - d.exponent).let { if (d.negative) it.negate() else it }
        }
    }

    /** A decimal's digits as they are (trailing zeros kept: 6.674 30 has six). */
    private fun digitsOf(b: BigDecimal): Decimal.Digits {
        val unscaled = b.unscaledValue().abs()
        val s = unscaled.toString()
        return Decimal.Digits(b.signum() < 0, s, s.length - 1 - b.scale(), true)
    }

    /**
     * `constant(name)` or `constant(name, unit)` as the whole formula (the editor's `\operatorname{constant}`, `\left(` …
     * taken off): the name and the unit (null for its own); null where the formula is something else.
     */
    private fun lone(latex: String): Pair<String, String?>? {
        val tokens = MathCalc.TOKEN.findAll(latex).map { it.value }.toList()
            .filter { it !in IGNORED }
            .map { if (it.length > 1 && it[0] == ',') "," else it }
        var k = 0
        // \operatorname{constant}, or its letters.
        if (tokens.getOrNull(0) in NAMING && tokens.getOrNull(1) == "{") {
            val close = tokens.indexOf("}")
            if (close < 0 || tokens.subList(2, close).joinToString("") != CALL) return null
            k = close + 1
        } else {
            if (tokens.size < CALL.length || tokens.subList(0, CALL.length).joinToString("") != CALL) return null
            k = CALL.length
        }
        if (tokens.getOrNull(k) != "(") return null
        val inner = ArrayList<String>()
        var depth = 0
        k++
        while (k < tokens.size) {
            val t = tokens[k]
            if (t == "(") depth++
            if (t == ")") {
                if (depth == 0) break
                depth--
            }
            inner += t
            k++
        }
        if (k >= tokens.size) return null
        // Nothing after its bracket but an = (a chain asked for).
        val rest = tokens.subList(k + 1, tokens.size)
        if (rest.isNotEmpty() && rest != listOf("=")) return null
        val comma = inner.indexOf(",")
        val nameTokens = if (comma < 0) inner else inner.subList(0, comma)
        val name = plainName(nameTokens)
        val unit = if (comma < 0) null else unitText(inner.subList(comma + 1, inner.size))
        // One name of letters and at most one unit: constant(), constant(2), constant(c, km, 3) are no calls of it.
        val lettersOnly = nameTokens.none { it.length == 1 && !it[0].isLetter() && it != "{" && it != "}" }
        if (name.isEmpty() || !lettersOnly || inner.count { it == "," } > 1 || unit?.isEmpty() == true) throw CalcException(Failure.ARGUMENTS)
        return name to unit
    }

    /** A name's letters (`\mathrm{…}` and `\text{…}` taken off). */
    private fun plainName(tokens: List<String>): String =
        tokens.filter { it != "{" && it != "}" && it !in NAMING }.joinToString("")

    private val NAMING = setOf("\\operatorname", "\\mathrm", "\\text", "\\textrm")
    private val IGNORED = setOf("\\left", "\\right", "\\,", "\\;", "\\!", "\\:", "\\ ", "\\displaystyle")

    // ---------------------------------------------------------------------------------------------------------------------
    // Inside a formula.

    /** A constant met in a formula: its value and standard uncertainty (null where exact) in the unit asked for. */
    internal class Measure(val entry: Entry, val unit: String?, val value: Expr, val uncertainty: Expr?)

    /** The constants a formula holds, while it is read for its value: each measured one a letter of its own. */
    private class Reading {
        val measured = LinkedHashMap<String, Measure>()
        val all = ArrayList<Measure>()
    }

    private val symbolic = ThreadLocal<Reading?>()

    /**
     * `constant(name)` (or with a unit) where the parser meets it: an exact constant is its exact value; a measured one its
     * published value as a number known only approximately (so no certified digits are claimed for it), or, while the formula is
     * read for its uncertainty, a letter of its own.
     */
    internal fun inFormula(name: String, unit: String?): Expr {
        val e = entry(name) ?: throw CalcException(Failure.UNKNOWN_CONSTANT)
        val f = factor(e.unit, unit)
        val value = Calculus.tidy(e.value * f)
        val u = e.uncertainty?.let { Calculus.tidy(it * f) }
        val reading = symbolic.get()
        val m = Measure(e, unit, value, u)
        if (reading != null && reading.all.none { it.entry === e && it.unit == unit }) reading.all += m
        if (u == null) return value
        if (reading != null) {
            val key = "constant:${e.name}:${unit.orEmpty()}"
            reading.measured[key] = m
            return Expr.Sym(key)
        }
        return Expr.Real(Numeric.value(value) ?: throw CalcException(Failure.UNDEFINED))
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The Calculator's answers.

    /**
     * The calculation of [latex] where it holds a constant: the constant on its own ([Outcome.PhysicalConstant]), or a number
     * worked out from constants ([Outcome.PhysicalValue]: exact, or with a bound of its uncertainty where a measured constant is
     * in it); letters or an equation beside a measured constant fail ([Failure.MEASURED_CONSTANT]). Null where the usual way works
     * it out (no constant; exact ones beside letters or in an equation).
     */
    internal fun calculate(latex: String, millis: Long, assumptions: Assumptions): Calculation? {
        if (!latex.contains(CALL)) return null
        val met = LinkedHashSet<Ambiguity>()
        val outcome = try {
            Budget.within(millis) { outcomeOf(latex, assumptions, met) } ?: return null
        } catch (e: CalcException) {
            Outcome.Failed(e.failure)
        } catch (e: ArithmeticException) {
            Outcome.Failed(Failure.UNDEFINED)
        } catch (e: StackOverflowError) {
            Outcome.Failed(Failure.TOO_LARGE)
        }
        val choice = Choice(if (outcome is Outcome.PhysicalConstant) Mode.CONSTANT else Mode.CALCULATE)
        return Calculation(outcome, decimalComma = false, decimals = true, fractions = false, choices = listOf(choice), choice = choice,
            assumptions = assumptions, ambiguities = met)
    }

    private fun outcomeOf(latex: String, assumptions: Assumptions, met: MutableSet<Ambiguity>): Outcome? {
        lone(latex)?.let { (name, unit) ->
            val e = entry(name) ?: throw CalcException(Failure.UNKNOWN_CONSTANT)
            return Outcome.PhysicalConstant(e, unit ?: e.unit, quantity(e, unit))
        }
        val worked = work(latex, assumptions) ?: return null
        met += worked.met
        return Outcome.PhysicalValue(worked.quantity, worked.measures.map { it.entry.name }.distinct())
    }

    /**
     * A number worked out from constants: the formula, the constants in it, its value, and where measured constants are in it the
     * bound of its uncertainty (null where it is exact); the result as shown.
     */
    internal class Worked(val formula: Expr, val measures: List<Measure>, val value: Expr, val bound: BigDecimal?, val quantity: Quantity,
        val met: Set<Ambiguity>)

    /**
     * [latex] read with its measured constants as letters, worked out: its value with the published values put in exactly, and
     * the bound u ≤ Σ |∂f/∂cᵢ|·u(cᵢ) of its standard uncertainty (first order, whatever the constants' correlations, which CODATA's
     * are). Checked: the formula is worked out again at the values moved by their uncertainties the way that moves it most, and it
     * must move by the bound (to 1 %); else the uncertainty is not that small and nothing is shown. With exact constants only, the
     * exact number. Null where it holds no constant, or exact ones beside letters (the usual way works those out).
     */
    internal fun work(latex: String, assumptions: Assumptions): Worked? {
        val reading = Reading()
        val flags = Flags(assumptions)
        val statements = try {
            symbolic.set(reading)
            Parser(MathCalc.TOKEN.findAll(latex).map { it.value }.toList(), flags).statements()
        } catch (e: CalcException) {
            // Read the usual way, it fails the same (an unknown name, a unit of another kind).
            return null
        } finally {
            symbolic.set(null)
        }
        if (reading.all.isEmpty()) return null
        val registry = reading.measured
        // Not a number: the usual way, unless a measured constant is in it.
        fun notANumber(): Worked? = if (registry.isEmpty()) null else throw CalcException(Failure.MEASURED_CONSTANT)
        val s = statements.singleOrNull() ?: return notANumber()
        val written = s.sides.filterNotNull()
        // A number: one side, or a chain asked for with "=" at its end.
        val open = s.sides.last() == null
        if (s.definition != null || written.size != 1 || (s.sides.size > 1 && !open)) return notANumber()
        val f = Calculus.tidy(Calculus.evaluate(written.single()))
        if (f is Expr.Mat || f.hasOp() || f.hasI() || f.hasInfinity()) return notANumber()
        val letters = f.symbols()
        if (letters.any { it !in registry }) return notANumber()
        if (letters.isEmpty()) {
            // Exact constants only, or measured ones that cancel (G/G): an exact number.
            if (Numeric.value(f) == null || LinAlg.isNull(f)) return null
            return Worked(f, reading.all, f, null, exactQuantity(f), flags.met.toSet())
        }
        val keys = letters.toSortedSet().toList()
        fun at(shift: (Int) -> Expr): Expr = Calculus.tidy(keys.foldIndexed(f) { k, acc, key ->
            acc.substitute(Expr.Sym(key), Calculus.tidy(registry.getValue(key).value + shift(k)))
        })
        val value = at { Expr.ZERO }
        if (value.symbols().isNotEmpty()) throw CalcException(Failure.MEASURED_CONSTANT)
        // |∂f/∂cᵢ| · u(cᵢ), each to a few digits (they only fix the uncertainty's two).
        val slopes = keys.map { key ->
            val d = Calculus.tidy(Calculus.derivative(f, Expr.Sym(key)))
            val dv = keys.fold(d) { acc, k2 -> acc.substitute(Expr.Sym(k2), registry.getValue(k2).value) }.let { Calculus.tidy(it) }
            decimal(dv, 8) ?: throw CalcException(Failure.MEASURED_CONSTANT)
        }
        val terms = keys.mapIndexed { k, key ->
            slopes[k].abs() * (decimal(registry.getValue(key).uncertainty!!, 8) ?: throw CalcException(Failure.UNDEFINED))
        }
        val bound = terms.fold(BigDecimal.ZERO) { a, b -> a + b }
        if (bound.signum() == 0) throw CalcException(Failure.MEASURED_CONSTANT)
        // Checked at the corners that move it most: up by the bound, and down by it.
        val magnitude = decimal(value, 6) ?: throw CalcException(Failure.MEASURED_CONSTANT)
        val lead = if (magnitude.signum() == 0) 0 else magnitude.precision() - magnitude.scale() - 1
        val boundLead = bound.precision() - bound.scale() - 1
        val digits = (lead - boundLead + 8).coerceIn(10, 2000)
        val centre = decimal(value, digits) ?: throw CalcException(Failure.MEASURED_CONSTANT)
        for (sign in listOf(1, -1)) {
            val moved = at { k ->
                val u = registry.getValue(keys[k]).uncertainty!!
                if (slopes[k].signum() * sign >= 0) u else neg(u)
            }
            val change = (decimal(moved, digits) ?: throw CalcException(Failure.MEASURED_CONSTANT)) - centre
            val expected = if (sign > 0) bound else bound.negate()
            if ((change - expected).abs() > bound.multiply(BigDecimal("0.01"))) throw CalcException(Failure.MEASURED_CONSTANT)
        }
        return Worked(f, reading.all, value, bound, rounded(value, bound), flags.met.toSet())
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Written out.

    /** A quantity in text: `6.67430(15)e-11`, `299792458`, `10973731.568157(12)`. */
    fun text(q: Quantity): String = write(q, latex = false)

    /** In LaTeX: `6.67430(15) \times 10^{-11}`. */
    fun latex(q: Quantity): String = write(q, latex = true)

    private fun write(q: Quantity, latex: Boolean): String {
        val d = q.value
        val plain = d.exponent in -3..9
        val digits = d.digits
        val mantissa = if (plain) {
            when {
                d.exponent < 0 -> "0." + "0".repeat(-d.exponent - 1) + digits
                d.exponent + 1 >= digits.length -> digits + "0".repeat(d.exponent + 1 - digits.length)
                else -> digits.substring(0, d.exponent + 1) + "." + digits.substring(d.exponent + 1)
            }
        } else {
            digits[0] + (if (digits.length > 1) "." + digits.substring(1) else "")
        }
        val sign = if (d.negative) "-" else ""
        val u = q.uncertainty?.let { "(" + it.digits + ")" }.orEmpty()
        val power = when {
            plain -> ""
            latex -> " \\times 10^{${d.exponent}}"
            else -> "e${d.exponent}"
        }
        return sign + mantissa + u + power
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Steps.

    /**
     * The steps of a number worked out from constants: the formula, the values put in, the bound of its uncertainty (where a
     * measured constant is in it), the result.
     */
    internal fun steps(latex: String, assumptions: Assumptions): List<Step>? {
        val w = try { Budget.within(MathCalc.LIMIT_MS) { work(latex, assumptions) } } catch (e: CalcException) { null } ?: return null
        val out = ArrayList<Step>()
        fun step(shown: String, key: StepKey) { out += Step(Steps.tidy(shown), StepText(key)) }
        step(latex.trim().removeSuffix("=").trim(), StepKey.START)
        step(w.measures.joinToString(",\\ ") { m ->
            m.entry.symbol + " = " + latex(quantity(m.entry, m.unit)) + unitSpace(m.unit ?: m.entry.unit)
        }, StepKey.CONSTANT_VALUES)
        val measured = w.measures.filter { it.uncertainty != null }
        if (w.bound != null) {
            val parts = measured.joinToString(" + ") { m ->
                "\\left|\\frac{\\partial f}{\\partial ${m.entry.symbol}}\\right| u\\left(${m.entry.symbol}\\right)"
            }
            step("u \\le $parts \\approx " + scientific(w.bound.round(MathContext(3, RoundingMode.UP))), StepKey.UNCERTAINTY_BOUND)
        }
        step(latex(w.quantity), StepKey.RESULT)
        return out
    }

    /** The steps of a constant written in another unit: one unit in the other, then the constant in it. */
    internal fun steps(o: Outcome.PhysicalConstant): List<Step>? {
        if (o.unit == o.constant.unit) return null
        val f = try { factor(o.constant.unit, o.unit) } catch (e: CalcException) { return null }
        val out = ArrayList<Step>()
        val ratio = decimal(f, EXACT_DIGITS)?.stripTrailingZeros() ?: return null
        val exactRatio = rational(f)?.takeIf { it.terminates } != null
        out += Step(Steps.tidy("1" + unitSpace(o.constant.unit) + (if (exactRatio) " = " else " \\approx ") + scientific(ratio) + unitSpace(o.unit)), StepText(StepKey.UNIT_FACTOR))
        out += Step(Steps.tidy(o.constant.symbol + " = " + latex(o.quantity) + unitSpace(o.unit)), StepText(StepKey.RESULT))
        return out
    }

    private fun unitSpace(unit: String) = if (unit.isBlank()) "" else "\\," + unitLatex(unit)

    private fun scientific(b: BigDecimal): String {
        val lead = b.precision() - b.scale() - 1
        if (lead in -3..9) return b.toPlainString()
        val m = b.movePointLeft(lead)
        return m.toPlainString() + " \\times 10^{$lead}"
    }
}

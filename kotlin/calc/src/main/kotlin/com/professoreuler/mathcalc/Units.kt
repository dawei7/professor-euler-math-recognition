package com.professoreuler.mathcalc

import java.math.BigInteger

/**
 * Units: a value in one unit written in another of the same kind ("5 km in mi", "100 km/h in m/s", "20 °C in °F"). SI units
 * with their prefixes, the usual imperial and US units, and units of time, area, volume, speed, force, energy, power, pressure,
 * temperature, angle and data; compound units with ·, / and powers (m/s^2, kW·h, cm³). Factors are exact where the definitions
 * are (an inch is 0.0254 m, a calorie 4.184 J, a degree π/180 rad); units that measure different things are refused.
 */
object Units {
    /** A unit: its value in SI base units, and the powers of m, kg, s, A, K, mol, cd, bit it is made of. */
    class Unit(val factor: Expr, val dims: List<Int>, val offset: Expr? = null)

    private fun dim(vararg p: Pair<Int, Int>) = MutableList(8) { 0 }.also { d -> p.forEach { (k, v) -> d[k] = v } }.toList()

    private const val M = 0
    private const val KG = 1
    private const val S = 2
    private const val A = 3
    private const val K = 4
    private const val MOL = 5
    private const val CD = 6
    private const val BIT = 7

    private fun dec(s: String): Expr = num(s.split('.').let { if (it.size == 1) Rational.of(BigInteger(it[0])) else Rational.decimal(it[0], it[1]) })
    private fun ratio(a: Long, b: Long): Expr = num(Rational.of(BigInteger.valueOf(a), BigInteger.valueOf(b)))

    private val LENGTH = dim(M to 1)
    private val MASS = dim(KG to 1)
    private val TIME = dim(S to 1)
    private val AREA = dim(M to 2)
    private val VOLUME = dim(M to 3)
    private val FORCE = dim(KG to 1, M to 1, S to -2)
    private val ENERGY = dim(KG to 1, M to 2, S to -2)
    private val POWER = dim(KG to 1, M to 2, S to -3)
    private val PRESSURE = dim(KG to 1, M to -1, S to -2)
    private val NONE = dim()

    /** Units that take an SI prefix (km, mg, ms, kWh, hPa, MB). */
    private val PREFIXED: Map<String, Unit> = mapOf(
        "m" to Unit(Expr.ONE, LENGTH),
        "g" to Unit(ratio(1, 1000), MASS),
        "s" to Unit(Expr.ONE, TIME),
        "A" to Unit(Expr.ONE, dim(A to 1)),
        "K" to Unit(Expr.ONE, dim(K to 1)),
        "mol" to Unit(Expr.ONE, dim(MOL to 1)),
        "cd" to Unit(Expr.ONE, dim(CD to 1)),
        "L" to Unit(ratio(1, 1000), VOLUME),
        "l" to Unit(ratio(1, 1000), VOLUME),
        "N" to Unit(Expr.ONE, FORCE),
        "J" to Unit(Expr.ONE, ENERGY),
        "W" to Unit(Expr.ONE, POWER),
        "Wh" to Unit(num(3600), ENERGY),
        "eV" to Unit(dec("0.0000000000000000001602176634"), ENERGY),
        "cal" to Unit(dec("4.184"), ENERGY),
        "Pa" to Unit(Expr.ONE, PRESSURE),
        "bar" to Unit(num(100000), PRESSURE),
        "Hz" to Unit(Expr.ONE, dim(S to -1)),
        "V" to Unit(Expr.ONE, dim(KG to 1, M to 2, S to -3, A to -1)),
        "Ω" to Unit(Expr.ONE, dim(KG to 1, M to 2, S to -3, A to -2)),
        "ohm" to Unit(Expr.ONE, dim(KG to 1, M to 2, S to -3, A to -2)),
        "C" to Unit(Expr.ONE, dim(A to 1, S to 1)),
        "F" to Unit(Expr.ONE, dim(KG to -1, M to -2, S to 4, A to 2)),
        "T" to Unit(Expr.ONE, dim(KG to 1, S to -2, A to -1)),
        "B" to Unit(num(8), dim(BIT to 1)),
        "bit" to Unit(Expr.ONE, dim(BIT to 1)),
        "t" to Unit(num(1000), MASS),
    )

    private val PREFIXES: List<Pair<String, Expr>> = listOf(
        "da" to num(10), "Y" to pow(num(10), num(24)), "Z" to pow(num(10), num(21)), "E" to pow(num(10), num(18)),
        "P" to pow(num(10), num(15)), "T" to pow(num(10), num(12)), "G" to pow(num(10), num(9)), "M" to pow(num(10), num(6)),
        "k" to num(1000), "h" to num(100), "d" to ratio(1, 10), "c" to ratio(1, 100), "m" to ratio(1, 1000),
        "µ" to ratio(1, 1_000_000), "μ" to ratio(1, 1_000_000), "u" to ratio(1, 1_000_000), "n" to ratio(1, 1_000_000_000),
        "p" to pow(num(10), num(-12)), "f" to pow(num(10), num(-15)), "a" to pow(num(10), num(-18)),
        "Ki" to num(1024), "Mi" to pow(num(2), num(20)), "Gi" to pow(num(2), num(30)), "Ti" to pow(num(2), num(40)),
    )

    /** Units written as they are, tried before a prefix: "min" is a minute, not a milli-inch. */
    private val PLAIN: Map<String, Unit> by lazy {
        val inch = dec("0.0254")
        val foot = dec("0.3048")
        val mile = dec("1609.344")
        val pound = dec("0.45359237")
        val lbf = dec("4.4482216152605")
        val gallon = dec("0.003785411784")
        val hour = num(3600)
        val celsius = Unit(Expr.ONE, dim(K to 1), dec("273.15"))
        val fahrenheit = Unit(ratio(5, 9), dim(K to 1), tidy(mul(dec("459.67"), ratio(5, 9))))
        val degree = Unit(tidy(mul(Expr.PI, ratio(1, 180))), NONE)
        mapOf(
            "in" to Unit(inch, LENGTH), "inch" to Unit(inch, LENGTH), "inches" to Unit(inch, LENGTH), "″" to Unit(inch, LENGTH),
            "ft" to Unit(foot, LENGTH), "foot" to Unit(foot, LENGTH), "feet" to Unit(foot, LENGTH), "′" to Unit(foot, LENGTH),
            "yd" to Unit(dec("0.9144"), LENGTH), "yard" to Unit(dec("0.9144"), LENGTH), "yards" to Unit(dec("0.9144"), LENGTH),
            "mi" to Unit(mile, LENGTH), "mile" to Unit(mile, LENGTH), "miles" to Unit(mile, LENGTH),
            "nmi" to Unit(num(1852), LENGTH), "au" to Unit(num(149597870700), LENGTH),
            "ly" to Unit(num(9460730472580800), LENGTH), "Å" to Unit(pow(num(10), num(-10)), LENGTH),
            "meter" to Unit(Expr.ONE, LENGTH), "meters" to Unit(Expr.ONE, LENGTH), "metre" to Unit(Expr.ONE, LENGTH), "metres" to Unit(Expr.ONE, LENGTH),
            "kilometer" to Unit(num(1000), LENGTH), "kilometers" to Unit(num(1000), LENGTH), "kilometre" to Unit(num(1000), LENGTH), "kilometres" to Unit(num(1000), LENGTH),
            "centimeter" to Unit(ratio(1, 100), LENGTH), "centimeters" to Unit(ratio(1, 100), LENGTH),
            "millimeter" to Unit(ratio(1, 1000), LENGTH), "millimeters" to Unit(ratio(1, 1000), LENGTH),
            "lb" to Unit(pound, MASS), "lbs" to Unit(pound, MASS), "pound" to Unit(pound, MASS), "pounds" to Unit(pound, MASS),
            "oz" to Unit(dec("0.028349523125"), MASS), "ounce" to Unit(dec("0.028349523125"), MASS), "ounces" to Unit(dec("0.028349523125"), MASS),
            "st" to Unit(dec("6.35029318"), MASS), "stone" to Unit(dec("6.35029318"), MASS), "stones" to Unit(dec("6.35029318"), MASS), "gram" to Unit(ratio(1, 1000), MASS), "grams" to Unit(ratio(1, 1000), MASS),
            "kilogram" to Unit(Expr.ONE, MASS), "kilograms" to Unit(Expr.ONE, MASS), "tonne" to Unit(num(1000), MASS), "tonnes" to Unit(num(1000), MASS),
            "min" to Unit(num(60), TIME), "minute" to Unit(num(60), TIME), "minutes" to Unit(num(60), TIME),
            "h" to Unit(hour, TIME), "hr" to Unit(hour, TIME), "hour" to Unit(hour, TIME), "hours" to Unit(hour, TIME),
            "d" to Unit(num(86400), TIME), "day" to Unit(num(86400), TIME), "days" to Unit(num(86400), TIME),
            "wk" to Unit(num(604800), TIME), "week" to Unit(num(604800), TIME), "weeks" to Unit(num(604800), TIME),
            "yr" to Unit(num(31557600), TIME), "year" to Unit(num(31557600), TIME), "years" to Unit(num(31557600), TIME),
            "second" to Unit(Expr.ONE, TIME), "seconds" to Unit(Expr.ONE, TIME), "sec" to Unit(Expr.ONE, TIME),
            "ha" to Unit(num(10000), AREA), "hectare" to Unit(num(10000), AREA), "hectares" to Unit(num(10000), AREA),
            "acre" to Unit(dec("4046.8564224"), AREA), "acres" to Unit(dec("4046.8564224"), AREA),
            "gal" to Unit(gallon, VOLUME), "gallon" to Unit(gallon, VOLUME), "gallons" to Unit(gallon, VOLUME),
            "qt" to Unit(dec("0.000946352946"), VOLUME), "pt" to Unit(dec("0.000473176473"), VOLUME),
            "quart" to Unit(dec("0.000946352946"), VOLUME), "quarts" to Unit(dec("0.000946352946"), VOLUME),
            "pint" to Unit(dec("0.000473176473"), VOLUME), "pints" to Unit(dec("0.000473176473"), VOLUME),
            "cup" to Unit(dec("0.0002365882365"), VOLUME), "cups" to Unit(dec("0.0002365882365"), VOLUME), "floz" to Unit(dec("0.0000295735295625"), VOLUME),
            "liter" to Unit(ratio(1, 1000), VOLUME), "liters" to Unit(ratio(1, 1000), VOLUME), "litre" to Unit(ratio(1, 1000), VOLUME), "litres" to Unit(ratio(1, 1000), VOLUME),
            "mph" to Unit(tidy(mile / hour), dim(M to 1, S to -1)), "kn" to Unit(ratio(1852, 3600), dim(M to 1, S to -1)),
            "knot" to Unit(ratio(1852, 3600), dim(M to 1, S to -1)), "knots" to Unit(ratio(1852, 3600), dim(M to 1, S to -1)),
            "lbf" to Unit(lbf, FORCE), "dyn" to Unit(pow(num(10), num(-5)), FORCE),
            "BTU" to Unit(dec("1055.05585262"), ENERGY), "erg" to Unit(pow(num(10), num(-7)), ENERGY),
            "hp" to Unit(tidy(mul(num(550), foot, lbf)), POWER), "PS" to Unit(dec("735.49875"), POWER),
            "atm" to Unit(num(101325), PRESSURE), "psi" to Unit(tidy(lbf / pow(inch, Expr.TWO)), PRESSURE),
            "Torr" to Unit(ratio(101325, 760), PRESSURE), "torr" to Unit(ratio(101325, 760), PRESSURE), "mmHg" to Unit(dec("133.322387415"), PRESSURE),
            "°C" to celsius, "℃" to celsius, "degC" to celsius, "celsius" to celsius, "Celsius" to celsius,
            "°F" to fahrenheit, "℉" to fahrenheit, "degF" to fahrenheit, "fahrenheit" to fahrenheit, "Fahrenheit" to fahrenheit,
            "kelvin" to Unit(Expr.ONE, dim(K to 1)), "Kelvin" to Unit(Expr.ONE, dim(K to 1)),
            "rad" to Unit(Expr.ONE, NONE), "°" to degree, "deg" to degree, "degree" to degree, "degrees" to degree,
            "grad" to Unit(tidy(mul(Expr.PI, ratio(1, 200))), NONE), "gon" to Unit(tidy(mul(Expr.PI, ratio(1, 200))), NONE),
            "rev" to Unit(tidy(mul(Expr.TWO, Expr.PI)), NONE), "turn" to Unit(tidy(mul(Expr.TWO, Expr.PI)), NONE),
            "byte" to Unit(num(8), dim(BIT to 1)), "bytes" to Unit(num(8), dim(BIT to 1)), "bits" to Unit(Expr.ONE, dim(BIT to 1)),
            "kcal" to Unit(dec("4184"), ENERGY), "kWh" to Unit(num(3600000), ENERGY),
        )
    }

    private fun tidy(e: Expr) = Calculus.tidy(e)

    private val SUPERSCRIPTS = mapOf('²' to 2, '³' to 3, '¹' to 1, '⁴' to 4, '⁵' to 5, '⁶' to 6, '⁷' to 7, '⁸' to 8, '⁹' to 9)

    /** One unit, perhaps with a prefix and a power ("km", "cm^3", "m²", "s-1"). */
    private fun factor(text: String): Unit? {
        var name = text
        var power = 1
        val sup = SUPERSCRIPTS[name.lastOrNull() ?: return null]
        if (sup != null) {
            power = sup
            name = name.dropLast(1)
            if (name.endsWith("⁻")) { power = -power; name = name.dropLast(1) }
        } else {
            val m = Regex("""^(.+?)\^\{?(-?\d+)\}?$""").find(name)
            if (m != null) { name = m.groupValues[1]; power = m.groupValues[2].toInt() }
        }
        val base = PLAIN[name] ?: PREFIXED[name] ?: PREFIXES.firstNotNullOfOrNull { (p, f) ->
            if (name.length > p.length && name.startsWith(p)) PREFIXED[name.substring(p.length)]?.let { u -> Unit(tidy(mul(f, u.factor)), u.dims) } else null
        } ?: return null
        if (power != 1 && base.offset != null) return null
        return Unit(tidy(pow(base.factor, num(power.toLong()))), base.dims.map { it * power }, if (power == 1) base.offset else null)
    }

    /** A unit as written: factors with · * or a space between, and / before those it divides by. */
    fun unit(text: String): Unit? {
        val t = text.trim().replace("·", "*").replace("⋅", "*").replace("\\cdot", "*")
        if (t.isEmpty()) return null
        PLAIN[t]?.let { return it }
        var factor: Expr = Expr.ONE
        var dims = List(8) { 0 }
        val parts = t.split('/')
        for ((k, part) in parts.withIndex()) {
            for (f in part.split('*').filter { it.isNotBlank() }) {
                val u = factor(f.trim()) ?: return null
                if (u.offset != null && (parts.size > 1 || part.contains('*'))) return null
                if (u.offset != null) return u
                val sign = if (k == 0) 1 else -1
                factor = mul(factor, if (sign > 0) u.factor else pow(u.factor, Expr.MINUS_ONE))
                dims = dims.zip(u.dims) { a, b -> a + sign * b }
            }
        }
        return Unit(tidy(factor), dims)
    }

    /** [value] in [from] written in [to]: exact where the factors are; null for units it does not know. */
    fun convert(value: Expr, from: String, to: String): Expr? {
        val a = unit(from) ?: return null
        val b = unit(to) ?: return null
        if (a.dims != b.dims) throw CalcException(Failure.UNITS)
        // A temperature by its offset: through kelvin.
        val kelvin = if (a.offset != null) tidy(add(mul(value, a.factor), a.offset)) else tidy(mul(value, a.factor))
        val out = if (b.offset != null) tidy((kelvin - b.offset) / b.factor) else tidy(kelvin / b.factor)
        // Back again, the way it came.
        val back = if (b.offset != null) tidy(add(mul(out, b.factor), b.offset)) else tidy(mul(out, b.factor))
        if (!LinAlg.isNull(tidy(back - kelvin))) return null
        return out
    }

    /** Whether [text] reads as a unit. */
    fun known(text: String): Boolean = try { unit(text) != null } catch (e: CalcException) { false }
}

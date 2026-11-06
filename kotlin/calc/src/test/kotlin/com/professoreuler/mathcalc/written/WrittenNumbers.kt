package com.professoreuler.mathcalc

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random

/** Arithmetic, exact values and units: each answer from the test's own fractions, doubles or the units' definitions. */
internal object WrittenNumbers {

    // -----------------------------------------------------------------------------------------------------------------------
    // Arithmetic: random expressions of fractions, worked out by the test's own fractions.

    private sealed class A {
        abstract fun value(): Q
        abstract val rank: Int
        class Leaf(val q: Q) : A() {
            override fun value() = q
            override val rank = if (q.isInteger && q.n.signum() >= 0) 9 else 8
        }
        class Bin(val op: Char, val l: A, val r: A) : A() {
            override fun value(): Q = when (op) {
                '+' -> l.value() + r.value()
                '-' -> l.value() - r.value()
                '*' -> l.value() * r.value()
                '/' -> r.value().let { if (it.isZero) throw ArithmeticException() else l.value() / it }
                else -> error(op)
            }
            override val rank = if (op == '+' || op == '-') 1 else 2
        }
        class Power(val base: A, val k: Int) : A() {
            override fun value(): Q = base.value().let { if (it.isZero && k < 0) throw ArithmeticException() else it.pow(k) }
            override val rank = 3
        }
    }

    private fun text(a: A): String = when (a) {
        is A.Leaf -> if (a.q.isInteger && a.q.n.signum() >= 0) "${a.q.n}" else "(${a.q})"
        is A.Bin -> {
            val l = text(a.l).let { if (a.l.rank < a.rank) "($it)" else it }
            val tight = a.op == '-' || a.op == '/'
            val r = text(a.r).let { if (a.r.rank < a.rank || (tight && a.r.rank == a.rank)) "($it)" else it }
            "$l ${a.op} $r"
        }
        is A.Power -> {
            val b = text(a.base).let { if (a.base.rank < 9) "($it)" else it }
            if (a.k < 0) "$b^(${a.k})" else "$b^${a.k}"
        }
    }

    private fun Random.leaf(): A {
        val n = int(-12, 15)
        return if (nextInt(3) == 0) A.Leaf(Q.of(n.toLong(), int(2, 9).toLong())) else A.Leaf(Q.of(n.toLong()))
    }

    private fun Random.tree(depth: Int): A {
        if (depth == 0 || nextInt(4) == 0) return leaf()
        return when (nextInt(9)) {
            0 -> A.Power(tree(depth - 1).let { if (it is A.Power) leaf() else it }, int(-2, 3))
            in 1..2 -> A.Bin('+', tree(depth - 1), tree(depth - 1))
            in 3..4 -> A.Bin('-', tree(depth - 1), tree(depth - 1))
            in 5..6 -> A.Bin('*', tree(depth - 1), tree(depth - 1))
            else -> A.Bin('/', tree(depth - 1), tree(depth - 1))
        }
    }

    fun arithmetic(): List<Task> {
        val r = Random(61106)
        val out = ArrayList<Task>()
        repeat(260) {
            val a = r.tree(3)
            val t = text(a)
            val v = try { a.value() } catch (e: ArithmeticException) { null }
            // A power of a fraction too large to be worth asking.
            if (v != null && (v.n.bitLength() > 200 || v.d.bitLength() > 200)) return@repeat
            out += if (v == null) Task("arithmetic", t, "division by 0", fails = setOf(Failure.DIVISION_BY_ZERO, Failure.UNDEFINED)) { false }
            else Task("arithmetic", t, "$v", judge = isExact(v))
        }
        // Times left out before a bracket and between brackets.
        repeat(30) {
            val a = r.int(-9, 9, nonzero = true).toLong(); val b = r.int(1, 9).toLong(); val c = r.int(-9, 9).toLong(); val d = r.int(1, 9).toLong()
            out += Task("arithmetic", "${if (a < 0) "($a)" else "$a"}($b - $c)", "${a * (b - c)}", judge = isExact(q(a * (b - c))))
            out += Task("arithmetic", "($b + $d)($c - $a)", "${(b + d) * (c - a)}", judge = isExact(q((b + d) * (c - a))))
        }
        // Decimals with a point: exact.
        repeat(50) {
            val x = BigDecimal.valueOf(r.int(-9999, 9999).toLong(), r.int(1, 3))
            val y = BigDecimal.valueOf(r.int(1, 9999).toLong(), r.int(1, 3))
            val op = "+-*/"[r.nextInt(4)]
            val v = when (op) {
                '+' -> dq(x) + dq(y); '-' -> dq(x) - dq(y); '*' -> dq(x) * dq(y); else -> dq(x) / dq(y)
            }
            val xt = x.toPlainString().let { if (x.signum() < 0) "($it)" else it }
            out += Task("arithmetic", "$xt $op ${y.toPlainString()}", "$v", judge = isExact(v))
        }
        // Large powers and factorials, exact.
        for (b in listOf(2, 3, 7, 10, 11)) for (k in listOf(17, 40, 63, 64, 100)) {
            out += Task("arithmetic", "$b^$k", "$b^$k", judge = isExact(Q.of(BigInteger.valueOf(b.toLong()).pow(k))))
        }
        for (n in 0..25) out += Task("arithmetic", "$n!", "$n!", judge = isExact(Q.of(fact(n))))
        repeat(15) {
            val n = r.int(5, 40); val m = r.int(1, n - 1)
            out += Task("arithmetic", "$n!/$m!", "", judge = isExact(Q.of(fact(n) / fact(m))))
        }
        // Percent, absolute value, floor, ceiling and sign of fractions.
        repeat(20) {
            val p = r.int(1, 150).toLong(); val b = r.int(-200, 400).toLong()
            out += Task("arithmetic", "$p% * $b", "${q(p * b, 100)}", judge = isExact(q(p * b, 100)))
        }
        repeat(40) {
            val v = Q.of(r.int(-60, 60).toLong(), r.int(1, 7).toLong())
            val fl = floorQ(v)
            when (it % 4) {
                0 -> out += Task("arithmetic", "abs(${v})", "${if (v.n.signum() < 0) -v else v}", judge = isExact(if (v.n.signum() < 0) -v else v))
                1 -> out += Task("arithmetic", "floor($v)", "$fl", judge = isExact(Q.of(fl)))
                2 -> out += Task("arithmetic", "ceil($v)", "", judge = isExact(Q.of(if (v.isInteger) fl else fl + BigInteger.ONE)))
                else -> out += Task("arithmetic", "sgn($v)", "", judge = isExact(q(v.n.signum().toLong())))
            }
        }
        return out
    }

    private fun dq(x: BigDecimal): Q = if (x.scale() >= 0) Q.of(x.unscaledValue(), BigInteger.TEN.pow(x.scale())) else Q.of(x.toBigInteger())

    fun fact(n: Int): BigInteger = (2..n).fold(BigInteger.ONE) { a, k -> a * BigInteger.valueOf(k.toLong()) }

    private fun floorQ(v: Q): BigInteger = v.n.divide(v.d).let { if (v.n.signum() < 0 && it * v.d != v.n) it - BigInteger.ONE else it }

    // -----------------------------------------------------------------------------------------------------------------------
    // Exact values.

    fun exactValues(): List<Task> {
        val out = ArrayList<Task>()
        // Trigonometric functions at multiples of 15°, in degrees and in radians; undefined where the cosine (sine) is 0.
        val funcs = listOf("sin", "cos", "tan", "cot", "sec", "csc")
        for (k in -8..24) for ((fi, f) in funcs.withIndex()) {
            if ((k + fi) % 3 != 0 && k !in listOf(1, 2, 3, 4, 6, 8, 12)) continue
            val deg = 15 * k
            val x = deg * PI / 180
            val sinZero = deg % 180 == 0
            val cosZero = (deg - 90) % 180 == 0
            val undefined = (f in listOf("tan", "sec") && cosZero) || (f in listOf("cot", "csc") && sinZero)
            val ref = when (f) {
                "sin" -> kotlin.math.sin(x); "cos" -> kotlin.math.cos(x); "tan" -> kotlin.math.tan(x)
                "cot" -> 1 / kotlin.math.tan(x); "sec" -> 1 / kotlin.math.cos(x); else -> 1 / kotlin.math.sin(x)
            }.let { if ((f == "sin" && sinZero) || (f == "cos" && cosZero) || (f == "tan" && sinZero) || (f == "cot" && cosZero)) 0.0 else it }
            val radians = when {
                k == 0 -> "0"
                else -> {
                    val g = gcd(k.toLong(), 12)
                    val num = k / g.toInt(); val den = 12 / g.toInt()
                    (if (num == 1) "" else if (num == -1) "-" else "$num") + "pi" + (if (den == 1) "" else "/$den")
                }
            }
            for (input in listOf("$f(${deg}°)", "$f($radians)")) {
                out += if (undefined) Task("exact values", input, "undefined", fails = setOf(Failure.UNDEFINED, Failure.DIVISION_BY_ZERO)) { false }
                else Task("exact values", input, fmt(ref), judge = isExactForm(ref))
            }
        }
        // Angles of the pentagon: exact or not, the value.
        for (deg in listOf(18, 36, 54, 72, 108, 144)) for (f in listOf("sin", "cos")) {
            val ref = if (f == "sin") kotlin.math.sin(deg * PI / 180) else kotlin.math.cos(deg * PI / 180)
            out += Task("exact values", "$f(${deg}°)", fmt(ref), judge = isNumber(ref))
        }
        // Inverse functions at the standard values.
        val args = listOf("0" to 0.0, "1/2" to 0.5, "-1/2" to -0.5, "sqrt(2)/2" to kotlin.math.sqrt(2.0) / 2, "-sqrt(2)/2" to -kotlin.math.sqrt(2.0) / 2,
            "sqrt(3)/2" to kotlin.math.sqrt(3.0) / 2, "-sqrt(3)/2" to -kotlin.math.sqrt(3.0) / 2, "1" to 1.0, "-1" to -1.0)
        for ((t, v) in args) {
            out += Task("exact values", "arcsin($t)", "", judge = isExactForm(kotlin.math.asin(v)))
            out += Task("exact values", "arccos($t)", "", judge = isExactForm(kotlin.math.acos(v)))
        }
        for ((t, v) in listOf("0" to 0.0, "1" to 1.0, "-1" to -1.0, "sqrt(3)" to kotlin.math.sqrt(3.0), "-sqrt(3)" to -kotlin.math.sqrt(3.0),
            "sqrt(3)/3" to kotlin.math.sqrt(3.0) / 3, "-sqrt(3)/3" to -kotlin.math.sqrt(3.0) / 3)) {
            out += Task("exact values", "arctan($t)", "", judge = isExactForm(kotlin.math.atan(v)))
        }
        // Roots: exact, partly taken out.
        for (n in 2..120) out += Task("exact values", "sqrt($n)", fmt(kotlin.math.sqrt(n.toDouble())), judge = isExactForm(kotlin.math.sqrt(n.toDouble())))
        for (n in listOf(-1000, -216, -54, -27, -16, -8, -2, 2, 3, 16, 24, 54, 81, 128, 250, 343, 1000)) {
            out += Task("exact values", "cbrt($n)", "", judge = isExactForm(Math.cbrt(n.toDouble())))
        }
        for ((b, k) in listOf(32 to 5, 81 to 4, 64 to 6, 128 to 7, 1024 to 10, 243 to 5, 625 to 4, 8 to 3, 27 to 3, 100 to 2)) {
            out += Task("exact values", "$b^(1/$k)", "", judge = isExactForm(Math.pow(b.toDouble(), 1.0 / k)))
            out += Task("exact values", "$b^(3/$k)", "", judge = isExactForm(Math.pow(b.toDouble(), 3.0 / k)))
        }
        repeat(12) { i ->
            val a = 2 + i; val b = 3 + 2 * i
            val ref = kotlin.math.sqrt(a.toDouble()) * kotlin.math.sqrt(b.toDouble())
            out += Task("exact values", "sqrt($a) * sqrt($b)", fmt(ref), judge = isExactForm(ref))
            out += Task("exact values", "1/(sqrt($a) + 1)", "", judge = isExactForm(1 / (kotlin.math.sqrt(a.toDouble()) + 1)))
        }
        // Logarithms.
        for (b in 2..10) for (k in listOf(-2, 0, 1, 3, 5)) {
            val x = Q.of(BigInteger.valueOf(b.toLong())).pow(k)
            out += Task("exact values", "log_$b(${x})", "$k", judge = isExact(q(k.toLong())))
        }
        for (k in -3..6) out += Task("exact values", "log(${Q.of(BigInteger.TEN).pow(k)})", "$k", judge = isExact(q(k.toLong())))
        for (k in 1..12) out += Task("exact values", "log2(${BigInteger.TWO.pow(k)})", "$k", judge = isExact(q(k.toLong())))
        for (k in -3..5) out += Task("exact values", "ln(e^$k)", "$k", judge = isExact(q(k.toLong())))
        for ((b, x) in listOf(3 to 5, 2 to 10, 5 to 7, 7 to 2, 10 to 2, 4 to 8, 9 to 27, 8 to 32, 6 to 5)) {
            out += Task("exact values", "log_$b($x)", "", judge = isNumber(kotlin.math.ln(x.toDouble()) / kotlin.math.ln(b.toDouble())))
        }
        for (x in listOf(2, 3, 5, 10, 100)) out += Task("exact values", "ln($x)", "", judge = isNumber(kotlin.math.ln(x.toDouble())))
        for (k in -3..4) out += Task("exact values", "exp($k)", "", judge = isNumber(kotlin.math.exp(k.toDouble())))
        // Hyperbolic functions of logarithms: fractions.
        for (a in 2..9) {
            val qa = q(a.toLong())
            out += Task("exact values", "sinh(ln($a))", "", judge = isExact((qa - Q.ONE / qa) / q(2)))
            out += Task("exact values", "cosh(ln($a))", "", judge = isExact((qa + Q.ONE / qa) / q(2)))
            out += Task("exact values", "tanh(ln($a))", "", judge = isExact((qa * qa - Q.ONE) / (qa * qa + Q.ONE)))
        }
        // Special functions: Γ, ζ, erf, J, W, ψ.
        for (n in 1..20) out += Task("exact values", "gamma($n)", "${n - 1}!", judge = isExact(Q.of(fact(n - 1))))
        for (n in 0..6) {
            val ref = fact(2 * n).toDouble() / (Math.pow(4.0, n.toDouble()) * fact(n).toDouble()) * kotlin.math.sqrt(PI)
            out += Task("exact values", "gamma(${2 * n + 1}/2)", fmt(ref), judge = isExactForm(ref))
        }
        for ((k, d) in listOf(2 to 6.0, 4 to 90.0, 6 to 945.0, 8 to 9450.0, 10 to 93555.0)) {
            out += Task("exact values", "zeta($k)", "π^$k/$d", judge = isExactForm(Math.pow(PI, k.toDouble()) / d))
        }
        for ((k, v) in listOf(3 to 1.2020569031595942, 5 to 1.0369277551433699, 7 to 1.0083492773819228)) out += Task("exact values", "zeta($k)", "$v", judge = isNumber(v))
        for ((k, v) in listOf(0 to q(-1, 2), -1 to q(-1, 12), -3 to q(1, 120), -5 to q(-1, 252))) out += Task("exact values", "zeta($k)", "$v", judge = isExact(v))
        for (x in listOf(0.1, 0.25, 0.5, 0.75, 1.0, 1.5, 2.0, -0.5, -1.0)) {
            out += Task("exact values", "erf(${fmt(x)})", "", judge = isNumber(erf(x), 1e-9))
            out += Task("exact values", "erfc(${fmt(x)})", "", judge = isNumber(1 - erf(x), 1e-9))
        }
        for (n in 0..3) for (x in listOf(0.5, 1.0, 2.0, 3.5)) out += Task("exact values", "besselj($n, ${fmt(x)})", "", judge = isNumber(besselJ(n, x), 1e-9))
        for (x in listOf(0.5, 1.0, 2.0, 5.0, 10.0)) out += Task("exact values", "lambertw(${fmt(x)})", "", judge = isNumber(lambertW(x), 1e-9))
        out += Task("exact values", "lambertw(e)", "1", judge = isNumber(1.0))
        for (n in 1..6) {
            val harmonic = (1 until n).sumOf { 1.0 / it }
            out += Task("exact values", "digamma($n)", "", judge = isNumber(-0.5772156649015329 + harmonic, 1e-9))
        }
        // Complex numbers, with the test's own complex arithmetic.
        val r = Random(4471)
        repeat(60) {
            val a = r.int(-6, 6); val b = r.int(-6, 6); val c = r.int(-6, 6); val d = r.int(-6, 6)
            val z = Cx(a.toDouble(), b.toDouble()); val w = Cx(c.toDouble(), d.toDouble())
            fun zt(re: Int, im: Int) = "($re ${if (im < 0) "-" else "+"} ${abs(im)}i)"
            when (it % 3) {
                0 -> (z * w).let { v -> out += Task("exact values", "${zt(a, b)}${zt(c, d)}", "$v", judge = isComplex(v.re, v.im)) }
                1 -> if (c != 0 || d != 0) (z / w).let { v -> out += Task("exact values", "${zt(a, b)}/${zt(c, d)}", "$v", judge = isComplex(v.re, v.im)) }
                else -> out += Task("exact values", "|${zt(a, b).removeSurrounding("(", ")")}|", "", judge = isNumber(z.abs))
            }
        }
        for (n in 1..12) {
            var p = Cx(1.0)
            repeat(n) { p *= Cx(1.0, 1.0) }
            out += Task("exact values", "(1 + i)^$n", "$p", judge = isComplex(p.re, p.im))
        }
        for (n in listOf(1, 4, 9, 2, 12, 25)) out += Task("exact values", "sqrt(-$n)", "", judge = isComplex(0.0, kotlin.math.sqrt(n.toDouble())))
        out += Task("exact values", "e^(i pi)", "-1", judge = isComplex(-1.0, 0.0))
        out += Task("exact values", "e^(i pi/2)", "i", judge = isComplex(0.0, 1.0))
        return out
    }

    private fun gcd(a: Long, b: Long): Long = if (b == 0L) abs(a) else gcd(b, a % b)

    /** erf by its Taylor series, summed in doubles (|x| ≤ 2). */
    fun erf(x: Double): Double {
        var sum = 0.0
        var term = x
        var n = 0
        while (abs(term) > 1e-18 || n < 5) {
            sum += term / (2 * n + 1)
            n++
            term *= -x * x / n
        }
        return 2 / kotlin.math.sqrt(PI) * sum
    }

    /** Jₙ by its series. */
    fun besselJ(n: Int, x: Double): Double {
        var sum = 0.0
        var term = Math.pow(x / 2, n.toDouble()) / fact(n).toDouble()
        var m = 0
        while (abs(term) > 1e-20 || m < 5) {
            sum += term
            m++
            term *= -(x / 2) * (x / 2) / (m * (m + n))
        }
        return sum
    }

    /** W₀ by Newton's method on w·eʷ = x. */
    fun lambertW(x: Double): Double {
        var w = kotlin.math.ln(1 + x)
        repeat(100) { w -= (w * kotlin.math.exp(w) - x) / (kotlin.math.exp(w) * (w + 1)) }
        return w
    }

    // -----------------------------------------------------------------------------------------------------------------------
    // Certified digits, against the test's own BigDecimal values.

    fun digits(): List<DigitsTask> {
        val mc = MathContext(80)
        val out = ArrayList<DigitsTask>()
        for (n in listOf(2, 3, 5, 6, 7, 10, 11, 13, 50, 99, 1000)) out += DigitsTask("sqrt($n)", BigDecimal(n).sqrt(mc), 45)
        for ((p, d) in listOf(1 to 3, 2 to 7, 22 to 7, 355 to 113, -5 to 17, 1 to 9801)) out += DigitsTask("$p/$d", BigDecimal(p).divide(BigDecimal(d), mc), 40)
        val pi = machinPi(mc)
        out += DigitsTask("pi", pi, 60)
        out += DigitsTask("2pi", pi * BigDecimal(2), 50)
        out += DigitsTask("pi^2", pi * pi, 50)
        out += DigitsTask("pi/7", pi.divide(BigDecimal(7), mc), 50)
        val e = eSeries(mc)
        out += DigitsTask("e", e, 60)
        out += DigitsTask("e^2", e * e, 50)
        out += DigitsTask("1/e", BigDecimal.ONE.divide(e, mc), 50)
        for (n in listOf(2, 3, 10)) out += DigitsTask("ln($n)", lnSeries(n, mc), 45)
        out += DigitsTask("(1 + sqrt(5))/2", (BigDecimal.ONE + BigDecimal(5).sqrt(mc)).divide(BigDecimal(2), mc), 50)
        out += DigitsTask("sqrt(2) + sqrt(3)", BigDecimal(2).sqrt(mc) + BigDecimal(3).sqrt(mc), 45)
        return out
    }

    class DigitsTask(val input: String, val ref: BigDecimal, val digits: Int)

    /** π by Machin's formula, 4·(4 arctan(1/5) − arctan(1/239)). */
    private fun machinPi(mc: MathContext): BigDecimal {
        fun atanInv(n: Int): BigDecimal {
            val x = BigDecimal.ONE.divide(BigDecimal(n), mc)
            val x2 = x * x
            var term = x
            var sum = BigDecimal.ZERO
            var k = 0
            while (term.abs() > BigDecimal("1e-90")) {
                sum += term.divide(BigDecimal(2 * k + 1), mc).let { if (k % 2 == 0) it else -it }
                term = (term * x2).round(mc)
                k++
            }
            return sum
        }
        return (atanInv(5) * BigDecimal(16) - atanInv(239) * BigDecimal(4)).round(mc)
    }

    private fun eSeries(mc: MathContext): BigDecimal {
        var sum = BigDecimal.ONE
        var term = BigDecimal.ONE
        for (k in 1..80) { term = term.divide(BigDecimal(k), mc); sum += term }
        return sum.round(mc)
    }

    /** ln n = 2 atanh((n − 1)/(n + 1)). */
    private fun lnSeries(n: Int, mc: MathContext): BigDecimal {
        val y = BigDecimal(n - 1).divide(BigDecimal(n + 1), mc)
        val y2 = y * y
        var term = y
        var sum = BigDecimal.ZERO
        var k = 0
        while (term.abs() > BigDecimal("1e-90")) {
            sum += term.divide(BigDecimal(2 * k + 1), mc)
            term = (term * y2).round(mc)
            k++
        }
        return (sum * BigDecimal(2)).round(mc)
    }

    /** Whether the certified digits [d] are [ref] rounded: within half a unit of their last digit. */
    fun digitsAgree(d: Decimal, ref: BigDecimal): Boolean = when (d) {
        is Decimal.Zero -> ref.signum() == 0
        is Decimal.Small -> ref.abs() < BigDecimal.ONE.scaleByPowerOfTen(d.below)
        is Decimal.Digits -> {
            val v = BigDecimal(BigInteger(d.digits), d.digits.length - 1 - d.exponent).let { if (d.negative) it.negate() else it }
            val halfUnit = BigDecimal.ONE.scaleByPowerOfTen(d.exponent - d.digits.length + 1).divide(BigDecimal(2))
            (v - ref).abs() <= halfUnit + BigDecimal("1e-75")
        }
    }

    // -----------------------------------------------------------------------------------------------------------------------
    // Units: factors from their definitions (NIST SP 811), exact.

    private val LENGTH = mapOf("m" to "1", "km" to "1000", "cm" to "0.01", "mm" to "0.001", "in" to "0.0254", "ft" to "0.3048",
        "yd" to "0.9144", "mi" to "1609.344", "nmi" to "1852")
    private val MASS = mapOf("kg" to "1", "g" to "0.001", "mg" to "0.000001", "lb" to "0.45359237", "oz" to "0.028349523125", "t" to "1000")
    private val TIME = mapOf("s" to "1", "min" to "60", "h" to "3600", "d" to "86400", "ms" to "0.001", "wk" to "604800")
    private val VOLUME = mapOf("L" to "0.001", "mL" to "0.000001", "gal" to "0.003785411784", "qt" to "0.000946352946", "m^3" to "1", "cm^3" to "0.000001")
    private val ENERGY = mapOf("J" to "1", "kJ" to "1000", "cal" to "4.184", "kcal" to "4184", "kWh" to "3600000", "Wh" to "3600")
    private val PRESSURE = mapOf("Pa" to "1", "kPa" to "1000", "bar" to "100000", "atm" to "101325", "hPa" to "100")
    private val SPEED = mapOf("m/s" to "1", "km/h" to "0.27777777777777777777777777777777777777777778", "mph" to "0.44704")
    private val DATA = mapOf("B" to "8", "bit" to "1", "kB" to "8000", "KiB" to "8192", "MB" to "8000000", "MiB" to "8388608", "GiB" to "8589934592")
    private val AREA = mapOf("m^2" to "1", "km^2" to "1000000", "ha" to "10000", "acre" to "4046.8564224", "cm^2" to "0.0001")

    fun units(): List<Task> {
        val out = ArrayList<Task>()
        val r = Random(1852)
        val kinds = listOf(LENGTH, MASS, TIME, VOLUME, ENERGY, PRESSURE, SPEED, DATA, AREA)
        for (kind in kinds) {
            val names = kind.keys.toList()
            repeat(18) {
                val a = names[r.nextInt(names.size)]
                val b = names[r.nextInt(names.size)]
                if (a == b) return@repeat
                val v = r.int(1, 500).toLong()
                // km/h is 1/3.6 m/s exactly.
                fun f(u: String) = if (u == "km/h") q(5, 18) else dq(BigDecimal(kind.getValue(u)))
                val want = q(v) * f(a) / f(b)
                out += Task("units", "convert($v $a, $b)", "$want $b", judge = convertedTo(want.toDouble()))
            }
        }
        // Temperatures: K = °C + 273.15, °F = 9/5 °C + 32.
        for (c in listOf(-40, -10, 0, 20, 37, 100, 250)) {
            val k = c + 273.15
            val f = c * 9.0 / 5 + 32
            out += Task("units", "convert($c °C, °F)", fmt(f), judge = convertedTo(f))
            out += Task("units", "convert($c °C, K)", fmt(k), judge = convertedTo(k))
            out += Task("units", "convert(${fmt(f)} °F, °C)", "$c", judge = convertedTo(c.toDouble()))
        }
        // Angles.
        for (d in listOf(30, 45, 60, 90, 180, 270, 360)) out += Task("units", "convert($d °, rad)", "", judge = convertedTo(d * PI / 180))
        // Different kinds are refused.
        for ((a, b) in listOf("m" to "s", "kg" to "L", "J" to "W", "km/h" to "km", "Pa" to "N", "h" to "m", "°C" to "m", "B" to "s")) {
            out += Task("units", "convert(5 $a, $b)", "different kinds", fails = setOf(Failure.UNITS)) { false }
        }
        return out
    }

    private fun convertedTo(want: Double): (Calculation) -> Boolean = { c ->
        val o = c.outcome as? Outcome.Converted
        o != null && close(Numeric.value(o.result) ?: Double.NaN, want, 1e-12)
    }
}

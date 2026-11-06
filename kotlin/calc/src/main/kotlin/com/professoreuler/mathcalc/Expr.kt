package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.math.PI
import kotlin.math.abs

/** The functions an expression may hold; `exp` is e to a power and a root is a power. */
enum class Func(val latex: String, val text: String) {
    SIN("\\sin", "sin"), COS("\\cos", "cos"), TAN("\\tan", "tan"), COT("\\cot", "cot"), SEC("\\sec", "sec"), CSC("\\csc", "csc"),
    ASIN("\\arcsin", "arcsin"), ACOS("\\arccos", "arccos"), ATAN("\\arctan", "arctan"), ACOT("\\operatorname{arccot}", "arccot"),
    SINH("\\sinh", "sinh"), COSH("\\cosh", "cosh"), TANH("\\tanh", "tanh"),
    LN("\\ln", "ln"),
    /** log to a base: args are (base, x). */
    LOG("\\log", "log"),
    ABS("", "abs"), FLOOR("", "floor"), CEIL("", "ceil"), FACTORIAL("", "!"), SIGN("\\operatorname{sgn}", "sgn"),
    /** Γ, and its logarithmic derivative ψ (digamma). */
    GAMMA("\\Gamma", "gamma"), DIGAMMA("\\psi", "digamma"),
    ERF("\\operatorname{erf}", "erf"), ERFC("\\operatorname{erfc}", "erfc"),
    /** Riemann's ζ. */
    ZETA("\\zeta", "zeta"),
    /** Bessel's J of a whole order: args are (order, x). */
    BESSELJ("\\operatorname{J}", "besselj"),
    /** Lambert's W, its principal branch W₀. */
    LAMBERTW("\\operatorname{W}", "lambertw"),
    /** The other hyperbolic functions, and the inverse hyperbolic ones under their ISO 80000-2 names ([MoreFunctions]). */
    COTH("\\coth", "coth"), SECH("\\operatorname{sech}", "sech"), CSCH("\\operatorname{csch}", "csch"),
    ASINH("\\operatorname{arsinh}", "arsinh"), ACOSH("\\operatorname{arcosh}", "arcosh"), ATANH("\\operatorname{artanh}", "artanh"),
    ACOTH("\\operatorname{arcoth}", "arcoth"),
    /** arcsec x = arccos(1/x) and arccsc x = arcsin(1/x), for |x| ≥ 1. */
    ASEC("\\operatorname{arcsec}", "arcsec"), ACSC("\\operatorname{arccsc}", "arccsc"),
    /** The exponential, sine and cosine integrals. */
    EI("\\operatorname{Ei}", "Ei"), SI("\\operatorname{Si}", "Si"), CI("\\operatorname{Ci}", "Ci"),
    /** Bessel's Y (the second kind) of a whole order: args are (order, x). */
    BESSELY("\\operatorname{Y}", "bessely"),
    /** The complete elliptic integrals of the first and second kind, of the modulus k: K(k), E(k). */
    ELLIPTICK("\\operatorname{K}", "elliptick"), ELLIPTICE("\\operatorname{E}", "elliptice"),
}

/** π, e, the imaginary unit, and ∞ (only as a bound or a limit's point or value). */
enum class Constant { PI, E, I, INF }

/** Calculus written in a formula, worked out by [Calculus]. */
enum class OpKind { DERIVATIVE, INTEGRAL, LIMIT, SUM, PRODUCT }

/**
 * A mathematical expression, always in one canonical form (after Cohen, *Computer Algebra and Symbolic Computation*): sums
 * and products are flat, their operands sorted, like terms and like powers collected, numbers combined exactly, roots of
 * numbers taken out as far as they go and their denominators made rational, known values of functions put in (sin π/6 is
 * 1/2, log₂ 8 is 3). Subtraction is a sum with −1 times, division a product with a power −1, √x is x^(1/2), eˣ is e^x.
 * Build with [num], [sym], [add], [mul], [pow], [fn] and the operators; never with the constructors.
 */
sealed class Expr {
    /** An exact rational number. */
    data class Num(val value: Rational) : Expr()

    /** A number known only approximately: an answer found numerically. */
    data class Real(val value: Double) : Expr()

    /** A letter: x, a, x_1. */
    data class Sym(val name: String) : Expr()

    data class Const(val kind: Constant) : Expr()

    class Add internal constructor(val terms: List<Expr>) : Expr() {
        override fun equals(other: Any?) = other is Add && terms == other.terms
        override fun hashCode() = terms.hashCode() * 31 + 1
    }

    class Mul internal constructor(val factors: List<Expr>) : Expr() {
        override fun equals(other: Any?) = other is Mul && factors == other.factors
        override fun hashCode() = factors.hashCode() * 31 + 2
    }

    class Pow internal constructor(val base: Expr, val exp: Expr) : Expr() {
        override fun equals(other: Any?) = other is Pow && base == other.base && exp == other.exp
        override fun hashCode() = (base.hashCode() * 31 + exp.hashCode()) * 31 + 3
    }

    class Fn internal constructor(val f: Func, val args: List<Expr>) : Expr() {
        override fun equals(other: Any?) = other is Fn && f == other.f && args == other.args
        override fun hashCode() = (f.hashCode() * 31 + args.hashCode()) * 31 + 4
    }

    /**
     * d/dx (a = the order), ∫ (a, b = the bounds, null for an antiderivative), lim (a = the point, b = −1 or 1 for one side),
     * Σ and Π (a, b = the bounds) of [body] in [variable], as written; [Calculus] works them out.
     */
    class Op internal constructor(val kind: OpKind, val body: Expr, val variable: Sym, val a: Expr?, val b: Expr?) : Expr() {
        override fun equals(other: Any?) = other is Op && kind == other.kind && body == other.body && variable == other.variable &&
            a == other.a && b == other.b
        override fun hashCode() = listOf(kind, body, variable, a, b).hashCode() * 31 + 5
    }

    /**
     * A matrix (a vector is one column), its entries row by row. Worked out as a whole by [LinAlg] where the formula is read,
     * never inside a sum or a product of the usual algebra, whose order of factors does not matter.
     */
    class Mat internal constructor(val rows: List<List<Expr>>) : Expr() {
        /** How many rows. */
        val size: Int get() = rows.size
        val columns: Int get() = rows[0].size
        override fun equals(other: Any?) = other is Mat && rows == other.rows
        override fun hashCode() = rows.hashCode() * 31 + 6
    }

    operator fun plus(o: Expr) = add(this, o)
    operator fun minus(o: Expr) = add(this, neg(o))
    operator fun times(o: Expr) = mul(this, o)
    operator fun div(o: Expr) = mul(this, pow(o, MINUS_ONE))
    operator fun unaryMinus() = neg(this)

    override fun toString(): String = Format.expr(this, Style.PLAIN, latex = false)

    companion object {
        val ZERO: Expr = Num(Rational.ZERO)
        val ONE: Expr = Num(Rational.ONE)
        val TWO: Expr = Num(Rational.of(2))
        val MINUS_ONE: Expr = Num(Rational.of(-1))
        val HALF: Expr = Num(Rational.of(1) / Rational.of(2))
        val PI: Expr = Const(Constant.PI)
        val E: Expr = Const(Constant.E)
        val I: Expr = Const(Constant.I)
        val INF: Expr = Const(Constant.INF)
    }
}

fun num(n: Long): Expr = Expr.Num(Rational.of(n))
fun num(r: Rational): Expr = Expr.Num(r)
fun sym(name: String): Expr = Expr.Sym(name)
fun neg(a: Expr): Expr = mul(Expr.MINUS_ONE, a)
fun sqrt(a: Expr): Expr = pow(a, Expr.HALF)
fun exp(a: Expr): Expr = pow(Expr.E, a)
fun ln(a: Expr): Expr = fn(Func.LN, a)
fun op(kind: OpKind, body: Expr, variable: Expr.Sym, a: Expr? = null, b: Expr? = null): Expr = Expr.Op(kind, body, variable, a, b)

/** Limits that keep exact arithmetic from running away: beyond them a result is too large to keep exactly. */
internal object Limits {
    const val MAX_TERMS = 2000
    const val MAX_EXPAND_POWER = 64
    const val MAX_FACTORIAL = 1000
    /** Integers whose roots are taken apart by trial division (the rest of a larger one stays under its root). */
    val ROOT_TRIAL: BigInteger = BigInteger.valueOf(100_000)
}

// ------------------------------------------------------------------------------------------------------------------------
// Order: Cohen's order relation, which puts like terms and like factors side by side.

internal object Order : Comparator<Expr> {
    override fun compare(u: Expr, v: Expr): Int {
        if (u is Expr.Num || u is Expr.Real) {
            if (v is Expr.Num || v is Expr.Real) return numeric(u).compareTo(numeric(v))
            return -1
        }
        if (v is Expr.Num || v is Expr.Real) return 1
        return when {
            u is Expr.Const && v is Expr.Const -> u.kind.compareTo(v.kind)
            u is Expr.Const -> -1
            v is Expr.Const -> 1
            u is Expr.Sym && v is Expr.Sym -> u.name.compareTo(v.name)
            u is Expr.Add && v is Expr.Add -> lastFirst(u.terms, v.terms)
            u is Expr.Mul && v is Expr.Mul -> lastFirst(u.factors, v.factors)
            u is Expr.Pow && v is Expr.Pow -> compare(u.base, v.base).let { if (it != 0) it else compare(u.exp, v.exp) }
            u is Expr.Fn && v is Expr.Fn -> if (u.f != v.f) u.f.compareTo(v.f) else lastFirst(u.args, v.args, fromEnd = false)
            u is Expr.Mat && v is Expr.Mat -> u.rows.toString().compareTo(v.rows.toString())
            u is Expr.Mat -> 1
            v is Expr.Mat -> -1
            u is Expr.Op && v is Expr.Op -> if (u.kind != v.kind) u.kind.compareTo(v.kind) else compare(u.body, v.body)
            u is Expr.Op -> 1
            v is Expr.Op -> -1
            u is Expr.Mul -> lastFirst(u.factors, listOf(v))
            v is Expr.Mul -> lastFirst(listOf(u), v.factors)
            u is Expr.Pow -> compare(u.base, v).let { if (it != 0) it else compare(u.exp, Expr.ONE) }
            v is Expr.Pow -> compare(u, v.base).let { if (it != 0) it else compare(Expr.ONE, v.exp) }
            u is Expr.Add -> lastFirst(u.terms, listOf(v))
            v is Expr.Add -> lastFirst(listOf(u), v.terms)
            u is Expr.Fn && v is Expr.Sym -> if (u.f.text == v.name) 1 else u.f.text.compareTo(v.name)
            u is Expr.Sym && v is Expr.Fn -> if (u.name == v.f.text) -1 else u.name.compareTo(v.f.text)
            else -> u.javaClass.name.compareTo(v.javaClass.name)
        }
    }

    private fun lastFirst(a: List<Expr>, b: List<Expr>, fromEnd: Boolean = true): Int {
        val m = minOf(a.size, b.size)
        for (k in 0 until m) {
            val c = if (fromEnd) compare(a[a.size - 1 - k], b[b.size - 1 - k]) else compare(a[k], b[k])
            if (c != 0) return c
        }
        return a.size.compareTo(b.size)
    }

    private fun numeric(e: Expr): Double = when (e) {
        is Expr.Num -> e.value.toDouble()
        is Expr.Real -> e.value
        else -> 0.0
    }
}

// ------------------------------------------------------------------------------------------------------------------------
// Numbers.

internal fun isNumber(e: Expr) = e is Expr.Num || e is Expr.Real

internal fun numValue(e: Expr): Double = when (e) {
    is Expr.Num -> e.value.toDouble()
    is Expr.Real -> e.value
    else -> Double.NaN
}

private fun addNumbers(a: Expr, b: Expr): Expr =
    if (a is Expr.Num && b is Expr.Num) Expr.Num(a.value + b.value) else Expr.Real(numValue(a) + numValue(b))

private fun mulNumbers(a: Expr, b: Expr): Expr =
    if (a is Expr.Num && b is Expr.Num) Expr.Num(a.value * b.value) else Expr.Real(numValue(a) * numValue(b))

internal fun isZero(e: Expr) = (e is Expr.Num && e.value.signum == 0) || (e is Expr.Real && e.value == 0.0)
internal fun isOne(e: Expr) = e is Expr.Num && e.value == Rational.ONE
internal fun rational(e: Expr): Rational? = (e as? Expr.Num)?.value
internal fun integer(e: Expr): BigInteger? = rational(e)?.takeIf { it.isInteger }?.num

// ------------------------------------------------------------------------------------------------------------------------
// Sums.

fun add(vararg terms: Expr): Expr = add(terms.asList())

fun add(terms: List<Expr>): Expr {
    Budget.check()
    // Flattened, every number summed, and each term split into its number and the rest, the rest collected.
    var number: Expr = Expr.ZERO
    val collected = LinkedHashMap<Expr, Expr>()
    fun take(t: Expr) {
        when {
            t is Expr.Add -> t.terms.forEach(::take)
            isNumber(t) -> number = addNumbers(number, t)
            else -> {
                val (c, rest) = splitCoefficient(t)
                collected[rest] = collected[rest]?.let { addNumbers(it, c) } ?: c
            }
        }
    }
    terms.forEach(::take)
    if (collected.size > Limits.MAX_TERMS) throw CalcException(Failure.TOO_LARGE)
    val out = ArrayList<Expr>()
    if (!isZero(number)) out += number
    for ((rest, c) in collected) if (!isZero(c)) out += if (isOne(c)) rest else mulOf(c, rest)
    return when (out.size) {
        0 -> Expr.ZERO
        1 -> out[0]
        else -> Expr.Add(out.sortedWith(Order))
    }
}

/** 3x²y as (3, x²y); a term without a number as (1, itself). */
internal fun splitCoefficient(t: Expr): Pair<Expr, Expr> {
    if (t is Expr.Mul && isNumber(t.factors[0])) {
        val rest = t.factors.drop(1)
        return t.factors[0] to (if (rest.size == 1) rest[0] else Expr.Mul(rest))
    }
    return Expr.ONE to t
}

/** A number times a product already in canonical form, without simplifying again. */
private fun mulOf(c: Expr, rest: Expr): Expr = when (rest) {
    is Expr.Mul -> Expr.Mul(listOf(c) + rest.factors)
    else -> Expr.Mul(listOf(c, rest))
}

// ------------------------------------------------------------------------------------------------------------------------
// Products.

fun mul(vararg factors: Expr): Expr = mul(factors.asList())

fun mul(factors: List<Expr>): Expr {
    Budget.check()
    var number: Expr = Expr.ONE
    // Each factor as base to an exponent; the exponents of one base summed.
    val bases = LinkedHashMap<Expr, Expr>()
    fun take(f: Expr) {
        when {
            f is Expr.Mul -> f.factors.forEach(::take)
            isNumber(f) -> number = mulNumbers(number, f)
            f is Expr.Pow -> bases[f.base] = bases[f.base]?.let { add(it, f.exp) } ?: f.exp
            else -> bases[f] = bases[f]?.let { add(it, Expr.ONE) } ?: Expr.ONE
        }
    }
    factors.forEach(::take)
    if (isZero(number)) return Expr.ZERO
    // Powers of the bases; a power that comes out a number, or a product, joins the rest again.
    val powers = ArrayList<Expr>()
    for ((base, e) in bases) {
        val p = pow(base, e)
        when {
            isNumber(p) -> number = mulNumbers(number, p)
            p is Expr.Mul -> for (g in p.factors) if (isNumber(g)) number = mulNumbers(number, g) else powers += g
            else -> powers += p
        }
    }
    // Roots of numbers with the same exponent go under one root: √2·√3 = √6.
    val merged = mergeNumericRoots(powers)
    if (merged.second != null) {
        number = mulNumbers(number, merged.second!!)
    }
    val rest = merged.first
    if (isZero(number)) return Expr.ZERO
    val out = ArrayList<Expr>()
    if (!isOne(number)) out += number
    out += rest.sortedWith(Order)
    return when (out.size) {
        0 -> Expr.ONE
        1 -> out[0]
        else -> {
            // A number times a sum stays a product, but −1·(a + b) and 2·(a + b) are kept as written; Expand distributes.
            Expr.Mul(out)
        }
    }
}

/** n^(p/q) · m^(p/q) as (nm)^(p/q), and whatever number that gives out. */
private fun mergeNumericRoots(powers: List<Expr>): Pair<List<Expr>, Expr?> {
    val byExp = LinkedHashMap<Expr, MutableList<Expr.Pow>>()
    val others = ArrayList<Expr>()
    for (p in powers) {
        if (p is Expr.Pow && p.base is Expr.Num && p.exp is Expr.Num) byExp.getOrPut(p.exp) { mutableListOf() } += p else others += p
    }
    if (byExp.values.none { it.size > 1 }) return powers to null
    var number: Expr = Expr.ONE
    for ((e, list) in byExp) {
        if (list.size == 1) { others += list[0]; continue }
        val base = list.fold(Rational.ONE) { acc, p -> acc * (p.base as Expr.Num).value }
        val p = pow(Expr.Num(base), e)
        when {
            isNumber(p) -> number = mulNumbers(number, p)
            p is Expr.Mul -> for (g in p.factors) if (isNumber(g)) number = mulNumbers(number, g) else others += g
            else -> others += p
        }
    }
    return others to number
}

// ------------------------------------------------------------------------------------------------------------------------
// Powers.

fun pow(base: Expr, exp: Expr): Expr {
    Budget.check()
    if (isZero(exp)) {
        if (isZero(base)) throw CalcException(Failure.UNDEFINED)
        return Expr.ONE
    }
    if (isOne(exp)) return base
    if (isOne(base)) return Expr.ONE
    if (isZero(base)) {
        val e = numValue(exp)
        if (!e.isNaN() && e < 0) throw CalcException(Failure.DIVISION_BY_ZERO)
        if (!e.isNaN() && e > 0) return Expr.ZERO
        return Expr.Pow(base, exp)
    }
    if (base is Expr.Num && exp is Expr.Num) return numberPower(base.value, exp.value)
    if (isNumber(base) && isNumber(exp)) {
        val v = Math.pow(numValue(base), numValue(exp))
        if (!v.isFinite()) throw CalcException(Failure.UNDEFINED)
        return Expr.Real(v)
    }
    val n = integer(exp)
    if (base is Expr.Const && base.kind == Constant.I && n != null) {
        return when (n.mod(BigInteger.valueOf(4)).toInt()) {
            0 -> Expr.ONE
            1 -> Expr.I
            2 -> Expr.MINUS_ONE
            else -> neg(Expr.I)
        }
    }
    // Euler: e^(iθ) = cos θ + i sin θ, exactly for θ a multiple of π/12 (e^(iπ) = −1).
    if (base is Expr.Const && base.kind == Constant.E && exp.hasI()) {
        val theta = mul(exp, neg(Expr.I))
        if (!theta.hasI()) piMultiple(theta)?.let { c ->
            val s = Trig.exact(Func.SIN, c)
            val co = Trig.exact(Func.COS, c)
            if (s != null && co != null) return add(co, mul(Expr.I, s))
        }
    }
    // e^(ln x) = x; e^(k ln x) = x^k.
    if (base is Expr.Const && base.kind == Constant.E) {
        if (exp is Expr.Fn && exp.f == Func.LN) return exp.args[0]
        if (exp is Expr.Mul && exp.factors.size == 2 && isNumber(exp.factors[0]) && exp.factors[1].let { it is Expr.Fn && it.f == Func.LN }) {
            return pow((exp.factors[1] as Expr.Fn).args[0], exp.factors[0])
        }
    }
    if (base is Expr.Pow) {
        // (x^a)^n = x^(an) for a whole n; √(x²) = |x|.
        if (n != null) return pow(base.base, mul(base.exp, exp))
        val inner = integer(base.exp)
        val r = rational(exp)
        if (inner != null && r != null && inner.testBit(0).not() && (Rational.of(inner) * r) == Rational.ONE) return fn(Func.ABS, base.base)
        if (inner != null && r != null) {
            // (x^a)^r with x^a ≥ 0 where it matters: (x^4)^(1/2) = x²; (x³)^(1/3) = x.
            val product = Rational.of(inner) * r
            if (product.isInteger && (!inner.testBit(0) && product.num.testBit(0)).not()) return pow(base.base, Expr.Num(product))
        }
    }
    if (base is Expr.Mul && n != null) return mul(base.factors.map { pow(it, exp) })
    // A number times something, to a root: (4x)^(1/2) = 2√x (the number's root taken out).
    if (base is Expr.Mul && rational(exp) != null && base.factors[0] is Expr.Num && (base.factors[0] as Expr.Num).value.signum > 0) {
        val c = base.factors[0]
        val rest = if (base.factors.size == 2) base.factors[1] else Expr.Mul(base.factors.drop(1))
        return mul(pow(c, exp), Expr.Pow(rest, exp))
    }
    return Expr.Pow(base, exp)
}

/** (a/b)^(p/q) exactly: whole powers, roots taken out as far as they go, the denominator made rational. */
private fun numberPower(b: Rational, e: Rational): Expr {
    if (e.isInteger) {
        if (e.num.bitLength() > 31) throw CalcException(Failure.TOO_LARGE)
        val p = b.pow(e.num.toInt()) ?: return Expr.Real(Math.pow(b.toDouble(), e.toDouble())).also {
            if (!it.value.isFinite()) throw CalcException(Failure.TOO_LARGE)
        }
        return Expr.Num(p)
    }
    if (e.den.bitLength() > 16 || e.num.bitLength() > 31) return Expr.Pow(Expr.Num(b), Expr.Num(e))
    val q = e.den.toInt()
    val p = e.num.toInt()
    if (b.signum < 0) {
        if (q % 2 == 1) return neg(numberPower(-b, e))
        // An even root of a negative number: i times the root of its size, for square roots.
        if (q == 2) return mul(pow(Expr.I, Expr.Num(Rational.of(p.toLong()))), numberPower(-b, e))
        return Expr.Pow(Expr.Num(b), Expr.Num(e))
    }
    // b^(p/q) = (b^p)^(1/q), with b^p = n/d: n = a^q·r gives a·r^(1/q); 1/d becomes d^(q−1)/d under the root.
    val whole = p / q
    val frac = p - whole * q
    val wholePart = b.pow(whole) ?: return Expr.Pow(Expr.Num(b), Expr.Num(e))
    val bp = b.pow(frac) ?: return Expr.Pow(Expr.Num(b), Expr.Num(e))
    val n = bp.num
    val d = bp.den
    val dUp = d.pow(q - 1)
    val (outN, inN) = takeOutPower(n * dUp, q)
    val coefficient = wholePart * Rational.of(outN, d)
    if (inN == BigInteger.ONE) return Expr.Num(coefficient)
    val root = Expr.Pow(Expr.Num(Rational.of(inN)), Expr.Num(Rational.ONE / Rational.of(q.toLong())))
    return if (coefficient == Rational.ONE) root else Expr.Mul(listOf(Expr.Num(coefficient), root))
}

/** n = out^q · rest with rest free of q-th powers (of primes found by trial division up to [Limits.ROOT_TRIAL]). */
private fun takeOutPower(n: BigInteger, q: Int): Pair<BigInteger, BigInteger> {
    var rest = n
    var out = BigInteger.ONE
    rest.let { whole -> rationalRoot(whole, q)?.let { return it to BigInteger.ONE } }
    val two = BigInteger.valueOf(2)
    var p = two
    while (p <= Limits.ROOT_TRIAL && p.pow(q) <= rest) {
        val pq = p.pow(q)
        while (rest.mod(pq).signum() == 0) {
            rest /= pq
            out *= p
        }
        p = if (p == two) BigInteger.valueOf(3) else p + two
    }
    rationalRoot(rest, q)?.let { return out * it to BigInteger.ONE }
    return out to rest
}

private fun rationalRoot(n: BigInteger, q: Int): BigInteger? = Rational.of(n).root(q)?.takeIf { it.isInteger }?.num

// ------------------------------------------------------------------------------------------------------------------------
// Functions and their known values.

fun fn(f: Func, vararg args: Expr): Expr = fn(f, args.asList())

fun fn(f: Func, args: List<Expr>): Expr {
    known(f, args)?.let { return it }
    return Expr.Fn(f, args)
}

/** The rational c with x = c·π, if x is a rational multiple of π (0 included). */
internal fun piMultiple(x: Expr): Rational? = when {
    isZero(x) -> Rational.ZERO
    x == Expr.PI -> Rational.ONE
    x is Expr.Mul && x.factors.size == 2 && x.factors[0] is Expr.Num && x.factors[1] == Expr.PI -> (x.factors[0] as Expr.Num).value
    else -> null
}

private fun known(f: Func, args: List<Expr>): Expr? {
    val x = args[0]
    when (f) {
        Func.SIN, Func.COS, Func.TAN, Func.COT, Func.SEC, Func.CSC -> {
            piMultiple(x)?.let { c -> Trig.exact(f, c)?.let { return it } }
            // Odd and even: sin(−x) = −sin x, cos(−x) = cos x.
            val (k, rest) = splitCoefficient(x)
            if (k is Expr.Num && k.value.signum < 0) {
                val flipped = fn(f, mul(Expr.Num(-k.value), rest))
                return if (f == Func.COS || f == Func.SEC) flipped else neg(flipped)
            }
            if (f == Func.SIN && x is Expr.Fn && x.f == Func.ASIN) return x.args[0]
            if (f == Func.COS && x is Expr.Fn && x.f == Func.ACOS) return x.args[0]
            if (f == Func.TAN && x is Expr.Fn && x.f == Func.ATAN) return x.args[0]
            if (isNumber(x) && x is Expr.Real) return Expr.Real(Numeric.eval(Expr.Fn(f, args), emptyMap()))
        }
        Func.ASIN, Func.ACOS, Func.ATAN, Func.ACOT -> {
            // Beyond ±1 arcsin and arccos are complex: left as they are where complex values are wanted.
            if (Domain.isComplex && (f == Func.ASIN || f == Func.ACOS) && rational(x)?.let { it.abs() > Rational.ONE } == true) return null
            Trig.inverse(f, x)?.let { return it }
            if (x is Expr.Real) return Expr.Real(Numeric.eval(Expr.Fn(f, args), emptyMap()))
        }
        Func.LN -> {
            if (isOne(x)) return Expr.ZERO
            if (x == Expr.E) return Expr.ONE
            if (x is Expr.Pow && x.base == Expr.E) return x.exp
            // Where complex values are wanted, ln(−r) = ln r + iπ.
            if (Domain.isComplex) rational(x)?.let { if (it.signum < 0) return add(fn(Func.LN, num(-it)), mul(Expr.I, Expr.PI)) }
            rational(x)?.let { if (it.signum <= 0) throw CalcException(Failure.UNDEFINED) }
            if (x is Expr.Real) return Expr.Real(kotlin.math.ln(x.value).also { if (!it.isFinite()) throw CalcException(Failure.UNDEFINED) })
            // ln of a whole power of a number: ln 8 = 3 ln 2 (the smallest base).
            rational(x)?.let { r -> perfectPower(r)?.let { (b, k) -> return mul(Expr.Num(Rational.of(k.toLong())), Expr.Fn(Func.LN, listOf(Expr.Num(b)))) } }
            // ln of a power of one number with a root in it: ln √2 = ln(2)/2, ln(√2/2) = −ln(2)/2.
            rootPowerOf(x)?.let { (b, k) -> return mul(Expr.Num(k), Expr.Fn(Func.LN, listOf(Expr.Num(Rational.of(b))))) }
        }
        Func.LOG -> {
            val base = args[0]
            val arg = args[1]
            if (isOne(arg)) return Expr.ZERO
            if (arg == base) return Expr.ONE
            rational(base)?.let { if (it.signum <= 0 || it == Rational.ONE) throw CalcException(Failure.UNDEFINED) }
            if (Domain.isComplex) rational(arg)?.let { if (it.signum < 0) return null }
            rational(arg)?.let { if (it.signum <= 0) throw CalcException(Failure.UNDEFINED) }
            if (arg is Expr.Pow && arg.base == base) return arg.exp
            val b = rational(base)
            val a = rational(arg)
            if (b != null && a != null) exactLog(b, a)?.let { return Expr.Num(it) }
        }
        Func.ABS -> {
            if (x is Expr.Num) return Expr.Num(x.value.abs())
            if (x is Expr.Real) return Expr.Real(abs(x.value))
            if (x is Expr.Const && x.kind != Constant.I) return x
            if (x == Expr.I) return Expr.ONE
            // Never negative for any real value of its letters (eˣ + 1, x² + 1): itself.
            if (!Domain.isComplex && nonNegative(x)) return x
            // A real number written with roots, π or functions: by its sign (|√2| is √2).
            if (x.symbols().isEmpty() && !x.hasI() && !x.hasInfinity()) Numeric.value(x)?.let { v ->
                if (v > 1e-9) return x
                if (v < -1e-9) return neg(x)
            }
            if (x is Expr.Fn && x.f == Func.ABS) return x
            if (x is Expr.Pow && integer(x.exp)?.testBit(0) == false && x.base.isReal()) return x
            val (k, rest) = splitCoefficient(x)
            if (k is Expr.Num && !isOne(k)) return mul(Expr.Num(k.value.abs()), fn(Func.ABS, rest))
            Complex.parts(x)?.let { (re, im) -> if (im != null) return sqrt(add(mul(re, re), mul(im, im))) }
        }
        Func.SIGN -> rational(x)?.let { return Expr.Num(Rational.of(it.signum.toLong())) }
        Func.FLOOR -> rational(x)?.let { return Expr.Num(Rational.of(floorOf(it))) }
        Func.CEIL -> rational(x)?.let { return Expr.Num(Rational.of(floorOf(-it).negate())) }
        Func.FACTORIAL -> rational(x)?.let { r ->
            if (!r.isInteger || r.signum < 0) throw CalcException(Failure.UNDEFINED)
            if (r.num > BigInteger.valueOf(Limits.MAX_FACTORIAL.toLong())) throw CalcException(Failure.TOO_LARGE)
            var p = BigInteger.ONE
            for (k in 2..r.num.toInt()) p *= BigInteger.valueOf(k.toLong())
            return Expr.Num(Rational.of(p))
        }
        Func.SINH, Func.COSH, Func.TANH -> {
            if (isZero(x)) return if (f == Func.COSH) Expr.ONE else Expr.ZERO
            MoreFunctions.oddOrEven(f, x)?.let { return it }
        }
        Func.GAMMA, Func.DIGAMMA, Func.ERF, Func.ERFC, Func.ZETA, Func.BESSELJ, Func.LAMBERTW -> return SpecialValues.known(f, args)
        Func.COTH, Func.SECH, Func.CSCH, Func.ASINH, Func.ACOSH, Func.ATANH, Func.ACOTH, Func.ASEC, Func.ACSC, Func.EI, Func.SI, Func.CI,
        Func.BESSELY, Func.ELLIPTICK, Func.ELLIPTICE -> return MoreFunctions.known(f, args)
    }
    return null
}

/**
 * Whether complex values are wanted while expressions are built on this thread: then ln(−2) is ln 2 + iπ and arcsin 2 stays
 * as it is, where otherwise both are [Failure.UNDEFINED] at once (the school's real numbers, Calculate's default).
 */
object Domain {
    private val complex = ThreadLocal<Boolean>()

    val isComplex: Boolean get() = complex.get() == true

    fun <T> complex(block: () -> T): T {
        val before = complex.get()
        complex.set(true)
        try {
            return block()
        } finally {
            complex.set(before)
        }
    }
}

/** The exact values of the special functions where there are simple ones: Γ(5) = 24, Γ(½) = √π, ζ(2) = π²/6, erf 0 = 0. */
internal object SpecialValues {
    fun known(f: Func, args: List<Expr>): Expr? {
        val x = args.last()
        if (x is Expr.Real && (f != Func.BESSELJ || args[0] is Expr.Num)) {
            val v = Numeric.eval(Expr.Fn(f, args), emptyMap())
            if (!v.isFinite()) throw CalcException(Failure.UNDEFINED)
            return Expr.Real(v)
        }
        val r = rational(x)
        when (f) {
            Func.GAMMA -> if (r != null) {
                if (r.isInteger) {
                    if (r.signum <= 0) throw CalcException(Failure.UNDEFINED)
                    if (r.num > BigInteger.valueOf(Limits.MAX_FACTORIAL.toLong() + 1)) throw CalcException(Failure.TOO_LARGE)
                    return Expr.Num(Rational.of(Special.factorial(r.num.toLong() - 1)))
                }
                if (r.den == BigInteger.valueOf(2) && r.num.abs() < BigInteger.valueOf(2L * Limits.MAX_FACTORIAL)) {
                    // Γ(n + ½) = (2n)! / (4ⁿ n!) · √π, Γ(½ − m) = (−4)^m m! / (2m)! · √π
                    val n = (r - Rational.of(1) / Rational.of(2)).num.toLong()
                    val c = if (n >= 0) {
                        Rational.of(Special.factorial(2 * n), BigInteger.valueOf(4).pow(n.toInt()) * Special.factorial(n))
                    } else {
                        val m = -n
                        Rational.of(BigInteger.valueOf(-4).pow(m.toInt()) * Special.factorial(m), Special.factorial(2 * m))
                    }
                    return mul(num(c), sqrt(Expr.PI))
                }
            }
            Func.ERF -> {
                if (isZero(x)) return Expr.ZERO
                val (k, rest) = splitCoefficient(x)
                if (k is Expr.Num && k.value.signum < 0) return neg(fn(Func.ERF, mul(Expr.Num(-k.value), rest)))
            }
            Func.ERFC -> {
                if (isZero(x)) return Expr.ONE
                val (k, rest) = splitCoefficient(x)
                if (k is Expr.Num && k.value.signum < 0) return add(Expr.TWO, neg(fn(Func.ERFC, mul(Expr.Num(-k.value), rest))))
            }
            Func.ZETA -> if (r != null && r.isInteger && r.num.bitLength() < 16) {
                val n = r.num.toInt()
                when {
                    n == 1 -> throw CalcException(Failure.UNDEFINED)
                    n == 0 -> return num(Rational.of(-1) / Rational.of(2))
                    n < 0 && n % 2 == 0 -> return Expr.ZERO
                    // ζ(−m) = −B_(m+1)/(m + 1)
                    n < 0 -> return num(-Bernoulli.b2k((1 - n) / 2) / Rational.of(1L - n))
                    // ζ(2k) = (−1)^(k+1) B_2k 2^(2k−1) π^(2k) / (2k)!
                    n % 2 == 0 && n <= 200 -> {
                        val k = n / 2
                        val b = Bernoulli.b2k(k)
                        val c = b * Rational.of(BigInteger.ONE.shiftLeft(n - 1), Special.factorial(n.toLong()))
                        return mul(num(if (k % 2 == 1) c else -c), pow(Expr.PI, num(n.toLong())))
                    }
                }
            }
            Func.BESSELJ -> {
                val order = rational(args[0]) ?: return null
                if (!order.isInteger) throw CalcException(Failure.UNDEFINED)
                if (isZero(x)) return if (order.signum == 0) Expr.ONE else Expr.ZERO
                if (order.signum < 0) {
                    val j = fn(Func.BESSELJ, num(-order), x)
                    return if (order.num.testBit(0)) neg(j) else j
                }
            }
            Func.LAMBERTW -> {
                if (isZero(x)) return Expr.ZERO
                if (x == Expr.E) return Expr.ONE
                if (x == neg(pow(Expr.E, Expr.MINUS_ONE))) return Expr.MINUS_ONE
                // W(a·e^a) = a for a ≥ −1
                if (x is Expr.Mul && x.factors.size == 2) {
                    val (a, b) = x.factors
                    if (b is Expr.Pow && b.base == Expr.E && b.exp == a && rational(a)?.let { it >= Rational.of(-1) } == true) return a
                }
            }
            else -> {}
        }
        return null
    }
}

private fun floorOf(r: Rational): BigInteger {
    val q = r.num.divide(r.den)
    return if (r.signum < 0 && q * r.den != r.num) q - BigInteger.ONE else q
}

/** r = b^k with k ≥ 2 and b smallest: 8 = 2³, 1/9 = (1/3)². */
private fun perfectPower(r: Rational): Pair<Rational, Int>? {
    if (r.signum <= 0 || r == Rational.ONE) return null
    for (k in 40 downTo 2) {
        val root = r.root(k) ?: continue
        if (root != Rational.ONE) return root to k
    }
    return null
}

/** log_b(a) where it is rational: log₂ 8 = 3, log₄ 8 = 3/2, log₁₀ 0.001 = −3. */
private fun exactLog(b: Rational, a: Rational): Rational? {
    val (bb, bk) = perfectPower(b) ?: (b to 1)
    val (aa, ak) = perfectPower(a) ?: (a to 1)
    // Same smallest base, or one the other's reciprocal.
    return when {
        aa == bb -> Rational.of(ak.toLong()) / Rational.of(bk.toLong())
        aa == Rational.ONE / bb -> -(Rational.of(ak.toLong()) / Rational.of(bk.toLong()))
        else -> null
    }
}

// ------------------------------------------------------------------------------------------------------------------------
// Looking inside.

/** Whether [e] holds the letter [s] anywhere. */
fun Expr.has(s: Expr.Sym): Boolean = when (this) {
    is Expr.Sym -> this == s
    is Expr.Add -> terms.any { it.has(s) }
    is Expr.Mul -> factors.any { it.has(s) }
    is Expr.Pow -> base.has(s) || exp.has(s)
    is Expr.Fn -> args.any { it.has(s) }
    is Expr.Op -> (body.has(s) && variable != s) || (a?.has(s) ?: false) || (b?.has(s) ?: false)
    is Expr.Mat -> rows.any { r -> r.any { it.has(s) } }
    else -> false
}

/** Whether [e] holds π: an angle in radians. */
internal fun Expr.hasPi(): Boolean = when (this) {
    is Expr.Const -> kind == Constant.PI
    is Expr.Add -> terms.any { it.hasPi() }
    is Expr.Mul -> factors.any { it.hasPi() }
    is Expr.Pow -> base.hasPi() || exp.hasPi()
    is Expr.Fn -> args.any { it.hasPi() }
    else -> false
}

/** The letters in [e], in alphabetical order. */
fun Expr.symbols(): Set<String> {
    val out = sortedSetOf<String>()
    fun walk(e: Expr) {
        when (e) {
            is Expr.Sym -> out += e.name
            is Expr.Add -> e.terms.forEach(::walk)
            is Expr.Mul -> e.factors.forEach(::walk)
            is Expr.Pow -> { walk(e.base); walk(e.exp) }
            is Expr.Fn -> e.args.forEach(::walk)
            is Expr.Mat -> e.rows.forEach { r -> r.forEach(::walk) }
            is Expr.Op -> {
                // The variable of an integral, a sum or a limit is bound: it is not one of the formula's own letters.
                val inner = e.body.symbols() - e.variable.name
                out += if (e.kind == OpKind.DERIVATIVE) inner + e.variable.name else inner
                e.a?.let(::walk)
                e.b?.let(::walk)
            }
            else -> Unit
        }
    }
    walk(this)
    return out
}

/** Whether [e] holds calculus still to be worked out. */
fun Expr.hasOp(): Boolean = when (this) {
    is Expr.Op -> true
    is Expr.Mat -> rows.any { r -> r.any { it.hasOp() } }
    is Expr.Add -> terms.any { it.hasOp() }
    is Expr.Mul -> factors.any { it.hasOp() }
    is Expr.Pow -> base.hasOp() || exp.hasOp()
    is Expr.Fn -> args.any { it.hasOp() }
    else -> false
}

/** Whether [e] holds no letter: a number, possibly with π, e, i, roots and functions of numbers. */
fun Expr.isConstant(): Boolean = symbols().isEmpty()

/** Whether [e] holds the imaginary unit. */
fun Expr.hasI(): Boolean = when (this) {
    is Expr.Const -> kind == Constant.I
    is Expr.Add -> terms.any { it.hasI() }
    is Expr.Mul -> factors.any { it.hasI() }
    is Expr.Pow -> base.hasI() || exp.hasI()
    is Expr.Fn -> args.any { it.hasI() }
    is Expr.Op -> body.hasI()
    is Expr.Mat -> rows.any { r -> r.any { it.hasI() } }
    else -> false
}

/** Whether [e] holds ∞. */
fun Expr.hasInfinity(): Boolean = when (this) {
    is Expr.Const -> kind == Constant.INF
    is Expr.Add -> terms.any { it.hasInfinity() }
    is Expr.Mul -> factors.any { it.hasInfinity() }
    is Expr.Pow -> base.hasInfinity() || exp.hasInfinity()
    is Expr.Fn -> args.any { it.hasInfinity() }
    else -> false
}

/** [e] as b^k for one whole number b > 1, where it holds a root of b: (b, k); else null. */
internal fun rootPowerOf(e: Expr): Pair<java.math.BigInteger, Rational>? {
    val factors = if (e is Expr.Mul) e.factors else listOf(e)
    var base: java.math.BigInteger? = null
    var total = Rational.ZERO
    var coefficient = Rational.ONE
    var root = false
    for (f in factors) {
        when {
            f is Expr.Num -> coefficient *= f.value
            f is Expr.Pow && f.base is Expr.Num && f.exp is Expr.Num -> {
                val b = (f.base as Expr.Num).value
                if (!b.isInteger || b.num <= java.math.BigInteger.ONE) return null
                val (smallest, k) = perfectPower(b)?.let { it.first.num to it.second } ?: (b.num to 1)
                if (base != null && base != smallest) return null
                base = smallest
                total += Rational.of(k.toLong()) * (f.exp as Expr.Num).value
                if (!(f.exp as Expr.Num).value.isInteger) root = true
            }
            else -> return null
        }
    }
    val b = base ?: return null
    if (!root) return null
    // The number in front: a whole power of the same base (1/2 is 2⁻¹), or nothing.
    var c = coefficient
    var k = 0L
    val big = Rational.of(b)
    while (c.num.mod(b).signum() == 0 && c.num.signum() != 0) { c = c / big; k++ }
    while (c.den.mod(b).signum() == 0) { c = c * big; k-- }
    if (c != Rational.ONE) return null
    return b to (total + Rational.of(k))
}

/** Never negative for any real value of its letters, as its form shows: eᵘ, x², |u|, sums and products of such. */
internal fun nonNegative(e: Expr): Boolean = when (e) {
    is Expr.Num -> e.value.signum >= 0
    is Expr.Const -> e.kind == Constant.PI || e.kind == Constant.E
    is Expr.Pow -> when {
        e.base == Expr.E -> !e.exp.hasI()
        e.base is Expr.Num && (e.base as Expr.Num).value.signum > 0 -> !e.exp.hasI()
        integer(e.exp)?.let { !it.testBit(0) } == true -> !e.base.hasI()
        // A square root and the like: never negative where it is defined.
        rational(e.exp)?.let { !it.isInteger && !it.den.testBit(0) } == true -> !e.base.hasI()
        else -> nonNegative(e.base) && !e.exp.hasI()
    }
    is Expr.Add -> e.terms.all { nonNegative(it) }
    is Expr.Mul -> e.factors.all { nonNegative(it) }
    is Expr.Fn -> e.f == Func.ABS || e.f == Func.COSH
    else -> false
}

/** Whether [e] holds a decimal number: a value only approximated. */
internal fun Expr.hasDecimal(): Boolean = when (this) {
    is Expr.Real -> true
    is Expr.Add -> terms.any { it.hasDecimal() }
    is Expr.Mul -> factors.any { it.hasDecimal() }
    is Expr.Pow -> base.hasDecimal() || exp.hasDecimal()
    is Expr.Fn -> args.any { it.hasDecimal() }
    else -> false
}

/** Real for every real value of its letters, as far as its form shows (no i). */
fun Expr.isReal(): Boolean = !hasI()

/** [e] with [s] replaced by [value], simplified again. */
fun Expr.substitute(s: Expr.Sym, value: Expr): Expr = when (this) {
    is Expr.Sym -> if (this == s) value else this
    is Expr.Add -> add(terms.map { it.substitute(s, value) })
    is Expr.Mul -> mul(factors.map { it.substitute(s, value) })
    is Expr.Pow -> pow(base.substitute(s, value), exp.substitute(s, value))
    is Expr.Fn -> fn(f, args.map { it.substitute(s, value) })
    is Expr.Op -> if (variable == s) Expr.Op(kind, body, variable, a?.substitute(s, value), b?.substitute(s, value))
        else Expr.Op(kind, body.substitute(s, value), variable, a?.substitute(s, value), b?.substitute(s, value))
    is Expr.Mat -> Expr.Mat(rows.map { r -> r.map { it.substitute(s, value) } })
    else -> this
}

/** How many nodes [e] has: the measure of which of two equal forms is simpler. */
fun Expr.size(): Int = when (this) {
    is Expr.Add -> 1 + terms.sumOf { it.size() }
    is Expr.Mul -> 1 + factors.sumOf { it.size() }
    is Expr.Pow -> 1 + base.size() + exp.size() + (if (exp == Expr.HALF) -1 else 0)
    is Expr.Fn -> 1 + args.sumOf { it.size() }
    is Expr.Op -> 2 + body.size()
    is Expr.Mat -> 1 + rows.sumOf { r -> r.sumOf { it.size() } }
    is Expr.Num -> if (value.isInteger) 1 else 2
    else -> 1
}

/** [e] rebuilt bottom-up through [f]. */
fun Expr.mapChildren(f: (Expr) -> Expr): Expr = when (this) {
    is Expr.Add -> add(terms.map(f))
    is Expr.Mul -> mul(factors.map(f))
    is Expr.Pow -> pow(f(base), f(exp))
    is Expr.Fn -> fn(this.f, args.map(f))
    is Expr.Mat -> Expr.Mat(rows.map { r -> r.map(f) })
    else -> this
}

// ------------------------------------------------------------------------------------------------------------------------
// Exact values of the trigonometric functions at multiples of π/12, and their inverses.

internal object Trig {
    private val R2 by lazy { sqrt(Expr.TWO) }
    private val R3 by lazy { sqrt(num(3)) }
    private val R6 by lazy { sqrt(num(6)) }

    /** sin at k·π/12 for k = 0..6, the first quarter. */
    private val SIN_TWELFTHS by lazy {
        listOf(
            Expr.ZERO,
            mul(add(R6, neg(R2)), num(Rational.of(1) / Rational.of(4))),
            Expr.HALF,
            mul(R2, Expr.HALF),
            mul(R3, Expr.HALF),
            mul(add(R6, R2), num(Rational.of(1) / Rational.of(4))),
            Expr.ONE,
        )
    }

    /** sin(c·π) where c·12 is whole, else null. */
    private fun sinAt(c: Rational): Expr? {
        val k12 = c * Rational.of(12)
        if (!k12.isInteger) return null
        var k = k12.num.mod(BigInteger.valueOf(24)).toInt() // 0..23 twelfths of π over a whole turn
        var sign = 1
        if (k >= 12) { k -= 12; sign = -1 } // sin(x + π) = −sin x
        if (k > 6) k = 12 - k // sin(π − x) = sin x
        val v = SIN_TWELFTHS[k]
        return if (sign < 0) neg(v) else v
    }

    fun exact(f: Func, c: Rational): Expr? {
        val s = sinAt(c) ?: return null
        val co = sinAt(c + Rational.of(1) / Rational.of(2)) ?: return null
        return when (f) {
            Func.SIN -> s
            Func.COS -> co
            Func.TAN -> if (isZero(co)) throw CalcException(Failure.UNDEFINED) else s / co
            Func.COT -> if (isZero(s)) throw CalcException(Failure.UNDEFINED) else co / s
            Func.SEC -> if (isZero(co)) throw CalcException(Failure.UNDEFINED) else Expr.ONE / co
            Func.CSC -> if (isZero(s)) throw CalcException(Failure.UNDEFINED) else Expr.ONE / s
            else -> null
        }
    }

    /** arcsin, arccos, arctan, arccot of the known values, as multiples of π. */
    fun inverse(f: Func, x: Expr): Expr? {
        val r = rational(x)
        if (f == Func.ASIN || f == Func.ACOS) r?.let { if (it.abs() > Rational.ONE) throw CalcException(Failure.UNDEFINED) }
        // Search the twelfths of the principal range for the value: the tables are tiny.
        val range = when (f) {
            Func.ASIN, Func.ATAN -> (-6..6)
            Func.ACOS, Func.ACOT -> (0..12)
            else -> return null
        }
        val base = when (f) {
            Func.ASIN -> Func.SIN
            Func.ACOS -> Func.COS
            Func.ATAN -> Func.TAN
            else -> Func.COT
        }
        for (k in range) {
            val c = Rational.of(k.toLong()) / Rational.of(12)
            if ((f == Func.ATAN && (k == -6 || k == 6)) || (f == Func.ACOT && (k == 0 || k == 12))) continue
            val v = try { exact(base, c) } catch (e: CalcException) { null } ?: continue
            if (v == x) return mul(num(c), Expr.PI)
        }
        return null
    }
}

// ------------------------------------------------------------------------------------------------------------------------
// Complex numbers written as a + b·i.

internal object Complex {
    /**
     * A constant built from numbers, i, sums, products and whole powers as a + b·i with a and b free of i: 1/(1 + i) is
     * 1/2 − i/2. Null where i stands inside a function or a power that is not whole.
     */
    fun normal(e: Expr): Pair<Expr, Expr>? {
        if (!e.isConstant()) return null
        if (!e.hasI()) return e to Expr.ZERO
        return when (e) {
            Expr.I -> Expr.ZERO to Expr.ONE
            is Expr.Add -> e.terms.map { normal(it) ?: return null }.let { ps -> add(ps.map { it.first }) to add(ps.map { it.second }) }
            is Expr.Mul -> {
                var a: Expr = Expr.ONE
                var b: Expr = Expr.ZERO
                for (f in e.factors) {
                    val (c, d) = normal(f) ?: return null
                    val re = add(mul(a, c), neg(mul(b, d)))
                    b = add(mul(a, d), mul(b, c))
                    a = re
                }
                a to b
            }
            is Expr.Pow -> {
                val n = integer(e.exp)?.takeIf { it.bitLength() < 10 }?.toInt() ?: return null
                val (a, b) = normal(e.base) ?: return null
                // A negative power by the conjugate over a² + b².
                val (p, q) = if (n < 0) {
                    val m = add(pow(a, Expr.TWO), pow(b, Expr.TWO))
                    mul(a, pow(m, Expr.MINUS_ONE)) to neg(mul(b, pow(m, Expr.MINUS_ONE)))
                } else a to b
                var re: Expr = Expr.ONE
                var im: Expr = Expr.ZERO
                repeat(kotlin.math.abs(n)) {
                    val r2 = add(mul(re, p), neg(mul(im, q)))
                    im = add(mul(re, q), mul(im, p))
                    re = r2
                }
                re to im
            }
            else -> null
        }
    }

    /** (real part, imaginary part) of a number written with i; imaginary part null where there is no i. */
    fun parts(e: Expr): Pair<Expr, Expr?>? {
        if (!e.isConstant()) return null
        if (!e.hasI()) return e to null
        val terms = if (e is Expr.Add) e.terms else listOf(e)
        var re: Expr = Expr.ZERO
        var im: Expr = Expr.ZERO
        for (t in terms) {
            val (c, rest) = splitCoefficient(t)
            when {
                rest == Expr.I -> im = add(im, c)
                rest is Expr.Mul && rest.factors.contains(Expr.I) -> im = add(im, mul(listOf(c) + rest.factors.filter { it != Expr.I }))
                rest.hasI() -> return null
                else -> re = add(re, t)
            }
        }
        return re to im
    }
}

// ------------------------------------------------------------------------------------------------------------------------
// Numeric values.

/** Values in doubles: for graphs, checks and approximations. NaN where a value is not real or not defined. */
object Numeric {
    fun eval(e: Expr, env: Map<String, Double>): Double = when (e) {
        is Expr.Num -> e.value.toDouble()
        is Expr.Real -> e.value
        is Expr.Sym -> env[e.name] ?: Double.NaN
        is Expr.Const -> when (e.kind) {
            Constant.PI -> PI
            Constant.E -> kotlin.math.E
            Constant.I -> Double.NaN
            Constant.INF -> Double.POSITIVE_INFINITY
        }
        is Expr.Op, is Expr.Mat -> Double.NaN
        is Expr.Add -> e.terms.sumOf { eval(it, env) }
        is Expr.Mul -> e.factors.fold(1.0) { acc, f -> acc * eval(f, env) }
        is Expr.Pow -> power(eval(e.base, env), e.exp, env)
        is Expr.Fn -> function(e.f, e.args.map { eval(it, env) })
    }

    private fun power(b: Double, exp: Expr, env: Map<String, Double>): Double {
        val r = rational(exp)
        // Odd roots of negatives are real: ∛(−8) = −2.
        if (r != null && b < 0 && !r.isInteger && r.den.testBit(0)) {
            val magnitude = Math.pow(-b, r.toDouble())
            return if (r.num.testBit(0)) -magnitude else magnitude
        }
        return Math.pow(b, eval(exp, env))
    }

    fun function(f: Func, a: List<Double>): Double {
        val x = a[0]
        return when (f) {
            Func.SIN -> kotlin.math.sin(x)
            Func.COS -> kotlin.math.cos(x)
            Func.TAN -> kotlin.math.tan(x)
            Func.COT -> 1 / kotlin.math.tan(x)
            Func.SEC -> 1 / kotlin.math.cos(x)
            Func.CSC -> 1 / kotlin.math.sin(x)
            Func.ASIN -> kotlin.math.asin(x)
            Func.ACOS -> kotlin.math.acos(x)
            Func.ATAN -> kotlin.math.atan(x)
            // In (0, π), as the exact values (Trig.inverse) have it; atan(1/x) jumped below 0 for negative x.
            Func.ACOT -> PI / 2 - kotlin.math.atan(x)
            Func.SINH -> kotlin.math.sinh(x)
            Func.COSH -> kotlin.math.cosh(x)
            Func.TANH -> kotlin.math.tanh(x)
            Func.LN -> if (x > 0) kotlin.math.ln(x) else Double.NaN
            Func.LOG -> if (x > 0 && x != 1.0 && a[1] > 0) kotlin.math.ln(a[1]) / kotlin.math.ln(x) else Double.NaN
            Func.ABS -> abs(x)
            Func.FLOOR -> kotlin.math.floor(x)
            Func.CEIL -> kotlin.math.ceil(x)
            Func.SIGN -> kotlin.math.sign(x)
            Func.FACTORIAL -> if (x >= 0 && x == kotlin.math.floor(x) && x <= 170) (2..x.toInt()).fold(1.0) { p, k -> p * k } else Double.NaN
            Func.GAMMA -> special(x) { Special.gamma(it, SPECIAL_BITS) }
            Func.DIGAMMA -> special(x) { Special.digamma(it, SPECIAL_BITS) }
            Func.ERF -> special(x) { Special.erf(it, SPECIAL_BITS) }
            Func.ERFC -> special(x) { Special.erfc(it, SPECIAL_BITS) }
            Func.ZETA -> special(x) { Special.zeta(it, SPECIAL_BITS) }
            Func.LAMBERTW -> special(x) { Special.lambertW(it, SPECIAL_BITS) }
            Func.BESSELJ -> {
                val v = a[1]
                if (x != kotlin.math.floor(x) || kotlin.math.abs(x) > 1000) Double.NaN else special(v) { Special.besselJ(x.toLong(), it, SPECIAL_BITS) }
            }
            Func.COTH, Func.SECH, Func.CSCH, Func.ASINH, Func.ACOSH, Func.ATANH, Func.ACOTH, Func.ASEC, Func.ACSC, Func.EI, Func.SI, Func.CI,
            Func.BESSELY, Func.ELLIPTICK, Func.ELLIPTICE -> MoreFunctions.double(f, a)
        }
    }

    /** Doubles of the special functions come from their balls at a few bits more than a double has. */
    private const val SPECIAL_BITS = 64

    private inline fun special(x: Double, f: (Ball) -> Ball): Double {
        if (!x.isFinite()) return Double.NaN
        return try {
            f(Ball(BigFloat.of(x))).toDouble()
        } catch (e: CalcException) {
            Double.NaN
        } catch (e: Imprecise) {
            Double.NaN
        }
    }

    /** The value of a constant expression, if real; with i, null. */
    fun value(e: Expr): Double? = if (e.hasI()) null else eval(e, emptyMap()).takeIf { it.isFinite() }

    /** A complex value (re, im) of a constant expression, for numbers written with i. */
    fun complex(e: Expr): Pair<Double, Double>? {
        val (re, im) = Complex.parts(e) ?: return null
        val r = eval(re, emptyMap())
        val i = im?.let { eval(it, emptyMap()) } ?: 0.0
        return if (r.isFinite() && i.isFinite()) r to i else null
    }
}

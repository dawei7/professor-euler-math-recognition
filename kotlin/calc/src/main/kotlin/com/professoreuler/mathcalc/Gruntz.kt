package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.math.abs

/**
 * Gruntz's algorithm for limits of exp-log functions (D. Gruntz, *On Computing Limits in a Symbolic Manipulation System*,
 * ETH Zürich 1996), written from the thesis's description; SymPy's `gruntz.py` (BSD) guided the order of its steps. Every limit
 * becomes one as x → +∞ (x → a⁺ is a + 1/x, x → −∞ is −x). Then, recursively: the most rapidly varying subexpressions Ω of the
 * function (comparable ones in one class, compared by the limit of the ratio of their logarithms; where x itself is among them,
 * x is first replaced by eˣ), one of them taken as ω → 0⁺, every other written as a power of ω times something slower, the
 * function's series in ω (its coefficients functions of x that vary more slowly, ln ω kept as a letter), and its leading term
 * c₀·ω^e₀: 0 where e₀ > 0, ±∞ by the sign of c₀ where e₀ < 0, the limit of c₀ where e₀ = 0. So (1 + 1/x)^x → e,
 * x^(1/x) → 1, eˣ/x¹⁰⁰ → ∞, ln(x)²/x → 0, x(e^(1/x) − 1) → 1 and (e^(1/x − e^(−x)) − e^(1/x))/e^(−x) → −1 exactly.
 *
 * Functions other than exp and ln are taken where their argument has a finite limit and they are analytic there (sin, cos,
 * arctan …; arctan at ±∞ too); sin x as x → ∞ has no limit and is declined. What is found is compared with values near the
 * point by the caller ([Calculus.limit]).
 */
internal object Gruntz {
    /** ω, the most rapidly varying function written as a letter, and λ = ln ω, kept as a letter in the coefficients. */
    private val W = Expr.Sym("\u0001ω")
    private val LAMBDA = Expr.Sym("\u0001λ")

    /** Where the algorithm cannot go on (a function it does not know, an oscillation, a coefficient it cannot decide). */
    private class Fail : Exception() {
        // The trace only while a test asks for it ([trace]): a failure is an everyday outcome, not worth its stack.
        override fun fillInStackTrace(): Throwable = if (trace != null) super.fillInStackTrace() else this
    }

    private fun fail(): Nothing = throw Fail()

    /** For tests: told why a limit was not found. */
    internal var trace: ((Throwable) -> Unit)? = null

    /** lim as [x] → [a] (finite, ∞ or −∞) from [side] (1 above, −1 below, null both) of [e]: exact, ±∞, or null. */
    fun limit(e: Expr, x: Expr.Sym, a: Expr, side: Int?): Expr? {
        val y = Expr.Sym("\u0001y")
        fun at(f: () -> Expr): Expr? = try {
            val run = Run(y)
            reciprocalLogs(Calculus.tidy(run.limitInf(run.normal(f()))))
        } catch (z: Fail) {
            trace?.invoke(z)
            null
        } catch (z: CalcException) {
            if (z.failure == Failure.TOO_LONG) throw z
            trace?.invoke(z)
            null
        } catch (z: ArithmeticException) {
            trace?.invoke(z)
            null
        } catch (z: Imprecise) {
            trace?.invoke(z)
            null
        } catch (z: StackOverflowError) {
            null
        }
        val inverse = pow(y, Expr.MINUS_ONE)
        return when {
            a == Expr.INF -> at { e.substitute(x, y) }
            a == neg(Expr.INF) -> at { e.substitute(x, neg(y)) }
            side == 1 -> at { e.substitute(x, add(a, inverse)) }
            side == -1 -> at { e.substitute(x, add(a, neg(inverse))) }
            else -> {
                val above = at { e.substitute(x, add(a, inverse)) } ?: return null
                val below = at { e.substitute(x, add(a, neg(inverse))) } ?: return null
                if (above == below || (!above.hasInfinity() && !below.hasInfinity() &&
                        Numeric.value(above)?.let { u -> Numeric.value(below)?.let { v -> abs(u - v) <= 1e-12 * maxOf(1.0, abs(u)) } } == true)) above
                else null
            }
        }
    }

    /** ln(1/q) as −ln q: x − ln(cosh x) → ln 2, not −ln(1/2). */
    private fun reciprocalLogs(e: Expr): Expr {
        val inner = e.mapChildren(::reciprocalLogs)
        if (inner is Expr.Fn && inner.f == Func.LN) rational(inner.args[0])?.let { r ->
            if (r.signum > 0 && r.num == BigInteger.ONE && r.den > BigInteger.ONE) return neg(ln(num(Rational.of(r.den))))
        }
        return inner
    }

    /** One limit's work as x → ∞: [x] the variable, results remembered, the depth of the recursion bounded. */
    private class Run(val x: Expr.Sym) {
        private var depth = 0
        private val limits = HashMap<Expr, Expr>()

        /**
         * [e] as an exp-log function: every power with x in its exponent as e^(p·ln b), sinh, cosh, tanh by exponentials, sec,
         * csc, cot by sin and cos, log_b by ln, and |u| by ±u where the sign of u as x → ∞ is known.
         */
        fun normal(e: Expr): Expr {
            if (!e.has(x)) return e
            return when (e) {
                is Expr.Add -> add(e.terms.map(::normal))
                is Expr.Mul -> mul(e.factors.map(::normal))
                is Expr.Pow -> {
                    val b = normal(e.base)
                    val p = normal(e.exp)
                    when {
                        b == Expr.E -> exp(p)
                        // (e^u)^r = e^(u·r): x moved up as eˣ leaves √(eˣ) behind.
                        b is Expr.Pow && b.base == Expr.E && !p.has(x) -> exp(mul(b.exp, p))
                        p.has(x) -> exp(mul(p, ln(b)))
                        else -> pow(b, p)
                    }
                }
                is Expr.Fn -> {
                    val args = e.args.map(::normal)
                    val u = args.last()
                    when (e.f) {
                        Func.SINH -> mul(Expr.HALF, add(exp(u), neg(exp(neg(u)))))
                        Func.COSH -> mul(Expr.HALF, add(exp(u), exp(neg(u))))
                        Func.TANH -> add(Expr.ONE, mul(num(-2), pow(add(exp(mul(Expr.TWO, u)), Expr.ONE), Expr.MINUS_ONE)))
                        Func.SEC -> pow(fn(Func.COS, u), Expr.MINUS_ONE)
                        Func.CSC -> pow(fn(Func.SIN, u), Expr.MINUS_ONE)
                        Func.COT -> mul(fn(Func.COS, u), pow(fn(Func.SIN, u), Expr.MINUS_ONE))
                        Func.LOG -> mul(ln(u), pow(ln(args[0]), Expr.MINUS_ONE))
                        Func.ACOT -> add(mul(Expr.HALF, Expr.PI), neg(fn(Func.ATAN, u)))
                        Func.ABS -> when (sign(u)) {
                            1 -> u
                            -1 -> neg(u)
                            else -> fail()
                        }
                        // tan as sin/cos: its pole (1 − x)·tan(πx/2) at 1 is a zero of cos.
                        Func.TAN -> mul(fn(Func.SIN, u), pow(fn(Func.COS, u), Expr.MINUS_ONE))
                        Func.SIN, Func.COS, Func.ASIN, Func.ACOS, Func.ATAN, Func.LN, Func.ERF, Func.ERFC -> fn(e.f, args)
                        else -> fail()
                    }
                }
                else -> e
            }
        }

        private fun enter() {
            Budget.check()
            if (++depth > 40) fail()
        }

        /** The limit of [e] as x → ∞. */
        fun limitInf(e: Expr): Expr {
            if (!e.has(x)) return e
            // x itself, where every other limit begins (its own expansion would ask for it again).
            if (e == x) return Expr.INF
            limits[e]?.let { return it }
            enter()
            try {
                val (c0, e0) = leadTerm(e)
                val s = signOfNumber(e0)
                val l = when {
                    s > 0 -> Expr.ZERO
                    s < 0 -> when (sign(c0)) {
                        1 -> Expr.INF
                        -1 -> neg(Expr.INF)
                        else -> fail()
                    }
                    else -> limitInf(c0)
                }
                limits[e] = l
                return l
            } finally {
                depth--
            }
        }

        /** The sign of [e] as x → ∞: 1, −1, or 0 where it is 0 there. */
        fun sign(e: Expr): Int {
            if (!e.has(x)) return signOfNumber(e)
            enter()
            try {
                return when {
                    e == x -> 1
                    e is Expr.Mul -> e.factors.fold(1) { s, f -> s * sign(f) }
                    e is Expr.Pow && e.base == Expr.E -> 1
                    e is Expr.Pow && rational(e.exp) != null -> {
                        val r = rational(e.exp)!!
                        val s = sign(e.base)
                        when {
                            s == 0 -> fail()
                            r.isInteger -> if (r.num.testBit(0)) s else 1
                            s == 1 -> 1
                            r.den.testBit(0) -> if (r.num.testBit(0)) s else 1
                            else -> fail()
                        }
                    }
                    else -> sign(leadTerm(e).first)
                }
            } finally {
                depth--
            }
        }

        /** The leading term c₀·ω^e₀ of [e]'s expansion in its most rapidly varying ω: (c₀, e₀), c₀ a function of x. */
        fun leadTerm(e: Expr): Pair<Expr, Expr> {
            if (!e.has(x)) return e to Expr.ZERO
            enter()
            try {
                var omega = mrv(e)
                if (omega.isEmpty()) fail()
                var f = e
                if (omega.any { it == x }) {
                    // x itself among the fastest: the function and Ω one level up, x as eˣ (Ω is not looked for again, which
                    // would compare eˣ with x through the limit of x/ln x, itself moved up to eˣ/x). The leading coefficient
                    // stays up there: its limit and sign as x → ∞ are those of the coefficient moved down.
                    f = normal(e.substitute(x, exp(x)))
                    omega = omega.map { normal(it.substitute(x, exp(x))) }.distinct()
                }
                val (g, logw) = rewrite(f, omega)
                val (c, k) = Expansion(x).leading(g)
                return c.substitute(LAMBDA, logw) to k
            } finally {
                depth--
            }
        }

        /** The most rapidly varying subexpressions of [e]: all of one class, x or exponentials. */
        fun mrv(e: Expr): List<Expr> {
            if (!e.has(x)) return emptyList()
            Budget.check()
            return when (e) {
                x -> listOf(x)
                is Expr.Add -> e.terms.fold(emptyList()) { acc, t -> max(acc, mrv(t)) }
                is Expr.Mul -> e.factors.fold(emptyList()) { acc, t -> max(acc, mrv(t)) }
                is Expr.Pow -> when {
                    // eˣ outgrows x: the class of x moved up.
                    e.base == Expr.E && e.exp == x -> listOf(e)
                    e.base == Expr.E -> {
                        val inner = mrv(e.exp)
                        val l = limitInf(e.exp)
                        if (l.hasInfinity()) max(listOf(e), inner) else inner
                    }
                    e.exp.has(x) -> fail()
                    else -> mrv(e.base)
                }
                is Expr.Fn -> e.args.fold(emptyList()) { acc, t -> max(acc, mrv(t)) }
                else -> fail()
            }
        }

        /** The faster of two classes, or both together where they are comparable. */
        private fun max(f: List<Expr>, g: List<Expr>): List<Expr> {
            if (f.isEmpty()) return g
            if (g.isEmpty()) return f
            if (f.any { it in g }) return (f + g).distinct()
            return when (compare(f[0], g[0])) {
                1 -> f
                -1 -> g
                else -> (f + g).distinct()
            }
        }

        /** 1 where [a] varies faster than [b], −1 slower, 0 in the same class: by the limit of ln a / ln b. */
        private fun compare(a: Expr, b: Expr): Int {
            val ratio = mul(logOf(a), pow(logOf(b), Expr.MINUS_ONE))
            val c = limitInf(ratio)
            return when {
                isZero(c) -> -1
                c.hasInfinity() -> 1
                else -> 0
            }
        }

        private fun logOf(a: Expr): Expr = if (a is Expr.Pow && a.base == Expr.E) a.exp else ln(a)

        /**
         * [e] with every element of [omega] (each e^s) written as e^(s − c·h)·ω^k, where g = e^h is the element with no other
         * inside it, ω = 1/g where h → ∞ (else g), c = lim s/h: the function of ω, x, and ln ω in terms of x.
         */
        private fun rewrite(e: Expr, omega: List<Expr>): Pair<Expr, Expr> {
            val elements = omega.map { it as? Expr.Pow ?: fail() }
            val g = elements.sortedBy { it.size() }.firstOrNull { c -> elements.none { o -> o != c && contains(c, o) } } ?: fail()
            val h = g.exp
            val up = when (limitInf(h)) {
                Expr.INF -> true
                neg(Expr.INF) -> false
                else -> fail()
            }
            val logw = if (up) neg(h) else h
            val replacement = LinkedHashMap<Expr, Pair<Expr, Expr>>()
            /** c = lim s/h for e^s of Ω's class: a constant, rational or not (2ˣ beside 3ˣ: ln 2/ln 3); 0 where e^s is slower. */
            fun ratio(s: Expr): Expr {
                val c = Calculus.tidy(limitInf(mul(s, pow(h, Expr.MINUS_ONE))))
                if (c.hasInfinity() || c.has(x) || c.has(W) || Numeric.value(c) == null) fail()
                return c
            }
            fun expanded(s: Expr) = try { Algebra.expand(s) } catch (z: CalcException) { if (z.failure == Failure.TOO_LONG) throw z; s }
            for (f in elements.sortedByDescending { it.size() }) {
                val c = ratio(f.exp)
                if (isZero(c)) fail()
                replacement[f] = expanded(add(f.exp, neg(mul(c, h)))) to (if (up) neg(c) else c)
            }
            fun sub(t: Expr): Expr {
                replacement[t]?.let { (rest, k) -> return mul(exp(sub(rest)), pow(W, k)) }
                if (!t.has(x)) return t
                // An exponential of Ω's class that Ω does not hold as such: 1/x moved up is e^(−x) beside Ω = {eˣ}.
                if (t is Expr.Pow && t.base == Expr.E) {
                    val c = ratio(t.exp)
                    if (!isZero(c) && Numeric.value(c)?.let { abs(it) > 1e-12 } == true) {
                        val k = if (up) neg(c) else c
                        val rest = expanded(add(t.exp, neg(mul(c, h))))
                        replacement[t] = rest to k
                        return mul(exp(sub(rest)), pow(W, k))
                    }
                }
                return t.mapChildren(::sub)
            }
            val f = sub(e)
            // Whatever of x is left must vary more slowly than ω: no element of Ω may remain.
            if (elements.any { contains(f, it) }) fail()
            return f to logw
        }

        private fun contains(a: Expr, b: Expr): Boolean = a == b || when (a) {
            is Expr.Add -> a.terms.any { contains(it, b) }
            is Expr.Mul -> a.factors.any { contains(it, b) }
            is Expr.Pow -> contains(a.base, b) || contains(a.exp, b)
            is Expr.Fn -> a.args.any { contains(it, b) }
            else -> false
        }

        /** The sign of a constant: exactly 0, or the sign of its value (in balls where a double is too close to 0 to say). */
        fun signOfNumber(c: Expr): Int {
            if (isZero(c)) return 0
            if (c.symbols().isNotEmpty()) fail()
            val v = Numeric.value(c) ?: fail()
            if (abs(v) > 1e-9) return if (v > 0) 1 else -1
            if (Precise.nearZero(c, complex = false) == true) return 0
            return try {
                val z = Precise.eval(c, 256, false, emptyMap()).re
                if (z.isPositive) 1 else if (z.isNegative) -1 else fail()
            } catch (z: Imprecise) {
                fail()
            }
        }
    }

    /**
     * An exponent of ω: a constant, exact (a rational, or ln 2/ln 3 where 2ˣ and 3ˣ meet), with its value to order the terms
     * by. Two are the same where their difference is 0 in balls.
     */
    private class Ex private constructor(val e: Expr, val v: Double) : Comparable<Ex> {
        operator fun plus(o: Ex) = of(add(e, o.e))
        operator fun minus(o: Ex) = of(add(e, neg(o.e)))
        fun times(r: Expr) = of(mul(e, r))
        val signum: Int get() = if (isZero(e)) 0 else if (v > 0) 1 else -1

        fun same(o: Ex): Boolean = e == o.e || (rational(e) == null || rational(o.e) == null) &&
            abs(v - o.v) <= 1e-12 * maxOf(1.0, abs(v)) && Precise.nearZero(add(e, neg(o.e)), complex = false) == true

        override fun compareTo(other: Ex): Int = when {
            same(other) -> 0
            v < other.v -> -1
            else -> 1
        }

        companion object {
            val ZERO = Ex(Expr.ZERO, 0.0)
            val ONE = Ex(Expr.ONE, 1.0)

            fun of(r: Rational) = Ex(num(r), r.toDouble())

            fun of(e0: Expr): Ex {
                val e = if (rational(e0) != null) e0 else Calculus.tidy(e0)
                if (e.symbols().isNotEmpty()) fail()
                val v = Numeric.value(e) ?: fail()
                if (rational(e) == null && abs(v) < 1e-12 && Precise.nearZero(e, complex = false) == true) return ZERO
                return Ex(e, v)
            }
        }
    }

    /**
     * Series in ω → 0⁺ with constant exponents and coefficients that are functions of x (and λ = ln ω): Σ c_j ω^(e_j), in
     * rising powers, with all terms below [order] known (null: exact).
     */
    private class PS(val terms: List<Pair<Ex, Expr>>, val order: Ex?) {
        fun lead(): Pair<Ex, Expr>? = terms.firstOrNull()
    }

    /** Series of a function of ω up to an absolute order, increased until the leading term is known. */
    private class Expansion(val x: Expr.Sym) {
        private var cap = Ex.of(Rational.of(3))

        fun leading(f: Expr): Pair<Expr, Expr> {
            for (c in listOf(3L, 6L, 12L, 24L)) {
                Budget.check()
                cap = Ex.of(Rational.of(c))
                val s = of(f)
                s.lead()?.let { (k, coefficient) -> return coefficient to k.e }
            }
            fail()
        }

        private fun exact(vararg t: Pair<Ex, Expr>) = PS(merged(t.toList()).filter { !zero(it.second) }, null)

        /** Terms in rising powers, those of one power added. */
        private fun merged(terms: List<Pair<Ex, Expr>>): List<Pair<Ex, Expr>> {
            val out = ArrayList<Pair<Ex, Expr>>()
            for ((k, c) in terms.sortedBy { it.first.v }) {
                val i = out.indexOfFirst { it.first.same(k) }
                if (i >= 0) out[i] = out[i].first to add(out[i].second, c) else out += k to c
            }
            return out.sortedBy { it.first.v }
        }

        private fun minOrder(a: Ex?, b: Ex?): Ex? = when {
            a == null -> b
            b == null -> a
            else -> if (a < b) a else b
        }

        /** [s] without the terms at [order] or beyond, and none past the cap. */
        private fun cut(terms: List<Pair<Ex, Expr>>, order: Ex?): PS {
            val o = minOrder(order, cap)
            val inRange = terms.filter { (k, _) -> o == null || k < o }
            // Exact still where nothing was cut off: an exact series below the cap.
            val exactNow = order == null && inRange.size == terms.size
            val kept = merged(inRange).map { (k, c) -> k to simplified(c) }.filter { (_, c) -> !zero(c) }
            return PS(kept, if (exactNow) null else o)
        }

        fun of(e: Expr): PS {
            Budget.check()
            if (!e.has(W)) return exact(Ex.ZERO to e)
            return when (e) {
                W -> exact(Ex.ONE to Expr.ONE)
                is Expr.Add -> e.terms.map(::of).reduce(::plus)
                is Expr.Mul -> e.factors.map(::of).reduce(::times)
                is Expr.Pow -> when {
                    e.base == W -> exact(Ex.of(e.exp) to Expr.ONE)
                    e.base == Expr.E -> exp(of(e.exp))
                    e.exp.has(W) -> exp(of(mul(e.exp, ln(e.base))))
                    else -> power(of(e.base), e.exp)
                }
                is Expr.Fn -> when (e.f) {
                    Func.LN -> ln(of(e.args[0]))
                    Func.ATAN -> {
                        val a = of(e.args[0])
                        val l = a.lead() ?: fail()
                        if (l.first.signum < 0) {
                            // arctan u = ±π/2 − arctan(1/u) where u → ±∞.
                            val s = signOf(l.second)
                            plus(exact(Ex.ZERO to mul(num(s.toLong()), Expr.HALF, Expr.PI)), times(exact(Ex.ZERO to Expr.MINUS_ONE), analytic(Func.ATAN, power(a, Expr.MINUS_ONE))))
                        } else analytic(Func.ATAN, a)
                    }
                    Func.SIN, Func.COS, Func.ASIN, Func.ACOS, Func.ERF, Func.ERFC -> if (e.args.size == 1) analytic(e.f, of(e.args[0])) else fail()
                    else -> fail()
                }
                else -> fail()
            }
        }

        private fun plus(a: PS, b: PS): PS = cut(a.terms + b.terms, minOrder(a.order, b.order))

        private fun times(a: PS, b: PS): PS {
            val la = a.lead()
            val lb = b.lead()
            // A factor known only to vanish to its order: the product is known only to that order times the other's first power.
            if (la == null || lb == null) {
                // An exact 0 times anything is 0; O(ω^p)·(c ω^q + …) is O(ω^(p+q)), O(ω^p)·O(ω^q) is O(ω^(p+q)).
                if (la == null && a.order == null || lb == null && b.order == null) return PS(emptyList(), null)
                return PS(emptyList(), (la?.first ?: a.order!!) + (lb?.first ?: b.order!!))
            }
            val order = minOrder(a.order?.let { it + lb.first }, b.order?.let { it + la.first })
            val o = minOrder(order, cap)
            val out = ArrayList<Pair<Ex, Expr>>()
            for ((i, c) in a.terms) for ((j, d) in b.terms) {
                val k = i + j
                if (o != null && k >= o) continue
                out += k to mul(c, d)
            }
            return cut(out, order)
        }

        private fun scale(a: PS, c: Expr) = PS(a.terms.map { (k, d) -> k to simplified(mul(c, d)) }, a.order)

        /** a as c₀ω^α(1 + g): (α, c₀, g), g with positive exponents only. */
        private fun split(a: PS): Triple<Ex, Expr, PS> {
            val (alpha, c0) = a.lead() ?: fail()
            val inverse = pow(c0, Expr.MINUS_ONE)
            val g = PS(a.terms.drop(1).map { (k, c) -> (k - alpha) to simplified(mul(c, inverse)) }, a.order?.let { it - alpha })
            return Triple(alpha, c0, g)
        }

        /** Σ coefficient(k)·g^k for g with positive exponents, as far as its order (or the cap) reaches. */
        private fun compose(g: PS, constant: Expr, coefficient: (Int) -> Expr): PS {
            val target = minOrder(g.order, cap) ?: cap
            var result = exact(Ex.ZERO to constant)
            if (g.terms.isEmpty()) return PS(result.terms, g.order)
            val step = g.terms.first().first
            if (step.signum <= 0) fail()
            var power = exact(Ex.ZERO to Expr.ONE)
            var k = 1
            while (true) {
                Budget.check()
                power = times(power, g)
                val first = power.lead()?.first ?: break
                if (first >= target || k > 64) break
                result = plus(result, scale(power, coefficient(k)))
                k++
            }
            return PS(result.terms.filter { it.first < target }, minOrder(g.order, target))
        }

        private fun exp(a: PS): PS {
            if (a.terms.any { it.first.signum < 0 }) fail()
            val a0 = a.terms.firstOrNull { it.first.signum == 0 }?.second ?: Expr.ZERO
            val g = PS(a.terms.filter { it.first.signum > 0 }, a.order)
            var factorial = Rational.ONE
            val series = compose(g, Expr.ONE) { k -> factorial = factorial * Rational.of(k.toLong()); num(Rational.ONE / factorial) }
            return scale(series, exp(a0))
        }

        private fun ln(a: PS): PS {
            val (alpha, c0, g) = split(a)
            val constant = add(fn(Func.LN, c0), mul(alpha.e, LAMBDA))
            return compose(g, constant) { k -> num(Rational.of(if (k % 2 == 1) 1L else -1L) / Rational.of(k.toLong())) }
        }

        /** a^r for a constant r (rational or not): c₀^r·ω^(αr)·(1 + g)^r by the binomial series. */
        private fun power(a: PS, r: Expr): PS {
            if (r.symbols().isNotEmpty()) fail()
            val whole = integer(r)
            if (whole != null && whole.signum() >= 0 && whole.bitLength() < 7) {
                var out = exact(Ex.ZERO to Expr.ONE)
                repeat(whole.toInt()) { out = times(out, a) }
                return out
            }
            val (alpha, c0, g) = split(a)
            var binomial: Expr = Expr.ONE
            val series = compose(g, Expr.ONE) { k -> binomial = simplified(mul(binomial, add(r, num(1L - k)), num(Rational.ONE / Rational.of(k.toLong())))); binomial }
            val lead = pow(c0, r)
            val shift = alpha.times(r)
            val shifted = PS(series.terms.map { (k, c) -> (k + shift) to c }, series.order?.let { it + shift })
            return scale(shifted, lead)
        }

        /** f(a) for f analytic at a's constant term y₀: Σ f⁽ᵏ⁾(y₀)/k!·gᵏ. */
        private fun analytic(f: Func, a: PS): PS {
            if (a.terms.any { it.first.signum < 0 }) fail()
            val y0 = a.terms.firstOrNull { it.first.signum == 0 }?.second ?: Expr.ZERO
            if (y0.has(LAMBDA)) fail()
            val g = PS(a.terms.filter { it.first.signum > 0 }, a.order)
            val y = Expr.Sym("\u0001z")
            var d: Expr = fn(f, y)
            var factorial = Rational.ONE
            val value = Calculus.tidy(d.substitute(y, y0))
            if (y0.symbols().isEmpty() && Numeric.value(value)?.isFinite() != true) fail()
            return compose(g, value) { k ->
                d = Calculus.derivative(d, y)
                factorial = factorial * Rational.of(k.toLong())
                val v = Calculus.tidy(d.substitute(y, y0))
                if (y0.symbols().isEmpty() && Numeric.value(v)?.isFinite() != true) fail()
                mul(num(Rational.ONE / factorial), v)
            }
        }

        private fun simplified(c: Expr): Expr = try {
            Algebra.expand(c)
        } catch (z: CalcException) {
            if (z.failure == Failure.TOO_LONG) throw z
            c
        }

        private fun signOf(c: Expr): Int {
            if (c.symbols().isEmpty()) return Numeric.value(c)?.let { if (it > 0) 1 else if (it < 0) -1 else null } ?: fail()
            fail()
        }

        /** Whether a coefficient (a function of x, λ and other letters) is 0: exactly, or at every one of several values. */
        fun zero(c: Expr): Boolean {
            if (isZero(c)) return true
            val e = simplified(c)
            if (isZero(e)) return true
            val letters = e.symbols().toList()
            if (letters.isEmpty()) {
                val v = Numeric.value(e)
                if (v != null && abs(v) > 1e-9) return false
                return Precise.nearZero(e, complex = false) ?: fail()
            }
            var compared = 0
            var small = 0
            for ((k, at) in listOf(1.37, 2.11, 2.93, 3.71, 1.83).withIndex()) {
                val env = letters.withIndex().associate { (j, name) ->
                    name to when (name) {
                        LAMBDA.name -> -3.1 - 2.3 * k - 0.7 * j
                        x.name -> at
                        else -> at * 0.61 + 0.13 * j
                    }
                }
                val v = Numeric.eval(e, env)
                // Relative to the size of its terms: x⁻¹⁰⁰ is tiny at x = 3 and still no 0; sin² + cos² − 1 cancels to rounding.
                val size = if (e is Expr.Add) e.terms.sumOf { abs(Numeric.eval(it, env)) } else abs(v)
                if (!v.isFinite() || !size.isFinite()) continue
                if (size == 0.0) { compared++; small++; continue }
                compared++
                if (abs(v) > 1e-7 * size) return false
                if (abs(v) < 1e-11 * size) small++
            }
            if (compared >= 3 && small == compared) return true
            // Undecided in doubles: in balls at two of the values.
            val checks = listOf(1.37, 2.93).map { at ->
                val env = letters.withIndex().associate { (j, name) ->
                    val value = when (name) {
                        LAMBDA.name -> Rational.of(-31 - 7L * j) / Rational.of(10)
                        x.name -> Rational.of((at * 100).toLong()) / Rational.of(100)
                        else -> Rational.of((at * 61).toLong() + 13L * j) / Rational.of(100)
                    }
                    name to CBall(Ball.of(value, 300))
                }
                try {
                    val z = Precise.eval(e, 300, false, env).re
                    z.magUp() <= BigFloat.of(1).shl(-200L)
                } catch (z: Imprecise) {
                    fail()
                }
            }
            return checks.all { it }
        }
    }
}

/**
 * Whether the values of a function ever nearer a point approach a limit found exactly: a check for where values in doubles do
 * not settle (they overflow at eˣ with x = 1000, or approach too slowly). The function is worked out in balls of many bits
 * at points ever nearer (x = 10, 40, 160 … at ∞; a ± 4⁻ᵏ at a point): toward a finite limit the distances must shrink and the
 * last be small against the last step (a sequence settling elsewhere is not taken); toward ±∞ the values must grow with one
 * sign, the steps not dying away as a convergent sequence's do. Null where too few values could be worked out.
 */
internal object NearValues {
    fun approach(e: Expr, x: Expr.Sym, a: Expr, side: Int?, limit: Expr): Boolean? {
        if ((e.symbols() - x.name).isNotEmpty()) return null
        if (a.hasInfinity()) return along(e, x, a, 0, limit)
        if (side != null) return along(e, x, a, side, limit)
        val up = along(e, x, a, 1, limit) ?: return null
        val down = along(e, x, a, -1, limit) ?: return null
        return up && down
    }

    private fun along(e: Expr, x: Expr.Sym, a: Expr, side: Int, limit: Expr): Boolean? =
        // Steps of 4, else (where e^(eˣ) cannot be worked out beyond x = 20) of 7/5.
        along(e, x, a, side, limit, Rational.of(4)) ?: along(e, x, a, side, limit, Rational.of(7) / Rational.of(5))

    private fun along(e: Expr, x: Expr.Sym, a: Expr, side: Int, limit: Expr, factor: Rational): Boolean? {
        val values = ArrayList<Ball>()
        val started = System.nanoTime()
        val steps = if (factor > Rational.of(2)) 24 else 60
        for (k in 1..steps) {
            if (System.nanoTime() - started > 300_000_000L) break
            val point: Rational
            val ball: Ball
            val prec = 200
            val power = factor.pow(k - 1) ?: break
            if (a.hasInfinity()) {
                val t = power * Rational.of(if (factor > Rational.of(2)) 10 else 2)
                point = if (a == Expr.INF) t else -t
                ball = Ball.of(point, prec)
            } else {
                val h = Rational.ONE / (power * factor)
                val av = rational(a)
                ball = if (av != null) Ball.of(av + (if (side > 0) h else -h), prec) else try {
                    Precise.eval(a, prec + 64, false, emptyMap()).re.add(Ball.of(if (side > 0) h else -h, prec + 64), prec + 64)
                } catch (z: Exception) {
                    if (z is CalcException && z.failure == Failure.TOO_LONG) throw z
                    return null
                }
            }
            val v = valueAt(e, x, ball) ?: if (values.size >= 4) break else continue
            values += v
        }
        if (values.size < 4) return null
        val last = values.takeLast(4)
        // Values too far apart to subtract exactly (e^(e^x) beside a number): decided by their order alone, or not at all.
        return try {
            decide(last, limit)
        } catch (z: CalcException) {
            if (z.failure == Failure.TOO_LONG) throw z
            if (limit.hasInfinity()) null else false
        }
    }

    private fun decide(last: List<Ball>, limit: Expr): Boolean? {
        return when {
            limit == Expr.INF || limit == neg(Expr.INF) -> {
                val s = if (limit == Expr.INF) 1 else -1
                val mids = last.map { if (s > 0) it.mid else -it.mid }
                val growing = mids.zipWithNext().all { (p, q) -> q > p } && mids.last().signum > 0
                // A convergent sequence's steps die away (by a quarter each time, 1/x, or faster); growth's do not.
                growing && (mids.last() > BigFloat.of(1000) ||
                    mids.zipWithNext().map { (p, q) -> (q - p).toDouble() }.zipWithNext().all { (p, q) -> !p.isFinite() || !q.isFinite() || q >= 0.3 * p })
            }
            else -> {
                val l = try {
                    Precise.eval(limit, 200, false, emptyMap()).re
                } catch (z: Exception) {
                    if (z is CalcException && z.failure == Failure.TOO_LONG) throw z
                    return null
                }
                val distances = last.map { (it.mid - l.mid).abs() }
                val tiny = BigFloat.of(1).shl(-80L) * (l.mid.abs() + BigFloat.of(1))
                if (distances.last() <= tiny) return true
                val shrinking = distances.zipWithNext().all { (p, q) -> q < p }
                val step = (last[3].mid - last[2].mid).abs()
                shrinking && distances.last() <= step * 200L + tiny
            }
        }
    }

    /** [e] at x = [at] in balls, precise enough to compare (more bits where cancellation needs them), or null. */
    private fun valueAt(e: Expr, x: Expr.Sym, at: Ball): Ball? {
        for (prec in listOf(200, 800, 3200)) {
            try {
                val z = Precise.eval(e, prec, false, mapOf(x.name to CBall(at.rounded(prec))))
                val v = z.re
                val mag = v.mid.abs()
                if (mag.isZero && v.rad.isZero) return v
                if (!mag.isZero && v.rad <= mag.shl(-40L)) return v
                if (mag.isZero && v.rad <= BigFloat.of(1).shl(-150L)) return v
            } catch (z: CalcException) {
                if (z.failure == Failure.TOO_LONG) throw z
                return null
            } catch (z: Imprecise) {
                // more bits
            } catch (z: ArithmeticException) {
                return null
            }
        }
        return null
    }
}

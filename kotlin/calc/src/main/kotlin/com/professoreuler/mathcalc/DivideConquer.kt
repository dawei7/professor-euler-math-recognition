package com.professoreuler.mathcalc

/**
 * Divide-and-conquer recurrences T(n) = a·T(n/b) + f(n), solved as an algorithms course solves them: by the master theorem in the
 * form of Cormen, Leiserson, Rivest and Stein (4th edition, § 4.5: a > 0, b > 1; case 2 for f(n) = Θ(n^(log_b a)·lg^k n) with
 * k ≥ 0), and T(n) = Σ aᵢ·T(bᵢ·n) + g(n) with several sub-problems by the method of Akra and Bazzi (1998, in Leighton's form:
 * T(n) = Θ(n^p·(1 + ∫₁ⁿ g(u)/u^(p+1) du)) with Σ aᵢ·bᵢ^p = 1). f and g are a constant times n^p·(log n)^q (sums of such terms
 * too: the largest decides). Floors and ceilings around n/b are read as n/b: they do not change the order.
 *
 * Where no case of the master theorem applies the analysis says why: f(n) = Θ(n^(log_b a)·log^k n) with k < 0 lies in the gap
 * between cases 1 and 2, and sub-problems of different sizes are no master recurrence; Akra–Bazzi then gives the order. Every
 * order is checked before it is shown: the recurrence itself is worked out (in ball arithmetic) for n = b^k up to b^1024 (master
 * theorem, exactly at powers of b) or for n = 2^k up to 2^60 with the floors (Akra–Bazzi), and T(n) divided by the order must level
 * off.
 */
object DivideConquer {
    enum class Method { MASTER, AKRA_BAZZI }

    /** Why no case of the master theorem applies. */
    enum class Gap {
        /** f(n) = Θ(n^(log_b a)·log^k n) with k < 0: smaller than case 2 needs, not polynomially smaller as case 1 needs. */
        BETWEEN_CASES_1_AND_2,

        /** Sub-problems of different sizes (T(n/3) + T(2n/3)): the master theorem has one size n/b. */
        SEVERAL_SIZES,
    }

    /** T(n)/order at n = [base]^[k]: certified digits, levelling off where the order is right. */
    data class Sample(val base: Expr, val k: Int, val ratio: Decimal)

    /**
     * An analysis of T([variable]) = Σ aᵢ·T(bᵢ·n) + f(n) ([function] the recurrence's letter, [terms] the pairs (aᵢ, bᵢ), bᵢ the
     * factor: ½ for n/2), [driving] f; [drivingOrder] (p, q) with f(n) = Θ(n^p·log^q n), null for f = 0. Master theorem:
     * [critical] log_b a, [case] 1, 2 or 3 and for case 3 [regularity] lim a·f(n/b)/f(n) = a/b^p < 1. Akra–Bazzi: [critical] the p
     * with Σ aᵢ·bᵢ^p = 1 (where it has no exact form, the letter p and its [criticalDigits]) and [integral] the order of
     * ∫₁ⁿ g(u)/u^(p+1) du. [order]: T(n) = Θ(order), null where no case applies ([gap] says why); [samples] the check.
     */
    data class Analysis(val method: Method, val function: String, val variable: String, val terms: List<Pair<Expr, Expr>>,
        val driving: Expr, val drivingOrder: Pair<Expr, Expr>?, val critical: Expr?, val criticalDigits: Decimal? = null,
        val case: Int? = null, val gap: Gap? = null, val regularity: Expr? = null, val integral: Expr? = null, val order: Expr? = null,
        val samples: List<Sample> = emptyList())

    /** A recurrence read: its letter, variable, terms (aᵢ, bᵢ) with equal bᵢ merged, and f. */
    class Recurrence(val function: String, val variable: String, val terms: List<Pair<Rational, Rational>>, val f: Expr)

    private fun tidy(e: Expr) = Calculus.tidy(e)
    private fun bad(): Nothing = throw CalcException(Failure.NOT_DIVIDE_AND_CONQUER)

    // ---------------------------------------------------------------------------------------------------------------------
    // Reading.

    /** T(n) = 2T(n/2) + n, as LaTeX the calculator reads (T(\frac{n}{2}) and ⌊n/2⌋ too). */
    internal fun read(latex: String, flags: Flags): Recurrence {
        val l = latex.replace("\\left(", "(").replace("\\right)", ")").replace("\\left\\lfloor", "\\lfloor").replace("\\right\\rfloor", "\\rfloor")
            .replace("\\left\\lceil", "\\lceil").replace("\\right\\rceil", "\\rceil").trim()
        val eq = topLevel(l, '=').singleOrNull() ?: throw CalcException(Failure.UNREADABLE)
        val left = l.substring(0, eq).replace(" ", "")
        val m = Regex("""^([A-Za-z])\(([A-Za-z])\)$""").matchEntire(left) ?: bad()
        val name = m.groupValues[1]
        val n = m.groupValues[2]
        if (name == n) bad()
        val right = l.substring(eq + 1)
        // Each T(…) as a letter of its own, T_{901}, T_{902}, …; its argument read on its own.
        val out = StringBuilder()
        val arguments = ArrayList<String>()
        var k = 0
        while (k < right.length) {
            val c = right[k]
            val before = right.getOrNull(k - 1)
            if (c.toString() == name && before?.isLetter() != true && before != '\\') {
                var j = k + 1
                while (j < right.length && right[j] == ' ') j++
                if (j < right.length && right[j] == '(') {
                    val close = closing(right, j) ?: throw CalcException(Failure.UNREADABLE)
                    arguments += right.substring(j + 1, close)
                    out.append(" ${name}_{${900 + arguments.size}} ")
                    k = close + 1
                    continue
                }
            }
            out.append(c)
            k++
        }
        if (arguments.isEmpty()) bad()
        val x = Expr.Sym(n)
        val r = parse(out.toString(), flags)
        val placeholders = arguments.indices.map { Expr.Sym("${name}_${901 + it}") }
        val terms = ArrayList<Pair<Rational, Rational>>()
        for ((i, arg) in arguments.withIndex()) {
            var a = parse(arg, flags)
            if (a is Expr.Fn && (a.f == Func.FLOOR || a.f == Func.CEIL)) a = a.args[0]
            val factor = tidy(mul(a, pow(x, Expr.MINUS_ONE)))
            val b = rational(factor) ?: bad()
            if (!isZero(tidy(add(a, neg(mul(num(b), x))))) || b.signum <= 0 || b >= Rational.ONE) bad()
            val coefficient = tidy(Calculus.derivative(r, placeholders[i]))
            if (coefficient.symbols().isNotEmpty()) bad()
            val c = rational(coefficient) ?: bad()
            if (c.signum <= 0) bad()
            terms += c to b
        }
        val f = tidy(placeholders.fold(r) { acc, p -> acc.substitute(p, Expr.ZERO) })
        if (f.symbols().any { it != n }) bad()
        val merged = terms.groupBy { it.second }.map { (b, ts) -> ts.fold(Rational.ZERO) { s, t -> s + t.first } to b }.sortedByDescending { it.second }
        return Recurrence(name, n, merged, f)
    }

    private fun parse(latex: String, flags: Flags): Expr {
        val tokens = MathCalc.TOKEN.findAll(latex).map { it.value }.toList()
        val st = Parser(tokens, flags).statements().singleOrNull() ?: throw CalcException(Failure.UNREADABLE)
        if (st.relations.isNotEmpty() || st.definition != null) throw CalcException(Failure.UNREADABLE)
        val side = st.sides.filterNotNull().singleOrNull() ?: throw CalcException(Failure.UNREADABLE)
        return tidy(Calculus.evaluate(side))
    }

    /** Where [c] stands outside every bracket and brace (and is no part of \le, \ge, \ne). */
    private fun topLevel(s: String, c: Char): List<Int> {
        val out = ArrayList<Int>()
        var depth = 0
        for ((k, x) in s.withIndex()) {
            when (x) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
            }
            if (x == c && depth == 0) out += k
        }
        if (Regex("""\\(le|ge|ne|leq|geq|neq)\b|[<>≤≥≠]""").containsMatchIn(s)) throw CalcException(Failure.UNREADABLE)
        return out
    }

    private fun closing(s: String, open: Int): Int? {
        var depth = 0
        for (k in open until s.length) {
            if (s[k] == '(') depth++
            if (s[k] == ')' && --depth == 0) return k
        }
        return null
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // f(n) = Θ(n^p·log^q n).

    /** (p, q) with f = Θ(n^p·log^q n), null for f = 0; [Failure.UNSOLVED] for an f of another kind (2ⁿ, n!). */
    private fun orderOf(f: Expr, n: String): Pair<Expr, Rational>? {
        if (isZero(f)) return null
        val m = Asymptotics.leading(f, n)
        if (Asymptotics.signOf(m.c) <= 0) bad()
        var p: Expr = Expr.ZERO
        var q = Rational.ZERO
        for ((s, k) in m.exponent) when (s) {
            Asymptotics.Scale.level(1) -> p = k
            Asymptotics.Scale.level(2) -> q = rational(k) ?: throw CalcException(Failure.UNSOLVED)
            else -> throw CalcException(Failure.UNSOLVED)
        }
        return p to q
    }

    private fun log2(e: Expr): Expr = fn(Func.LOG, num(2), e)

    /** n^p·(log₂ n)^q. */
    private fun power(n: Expr, p: Expr, q: Rational): Expr = tidy(mul(pow(n, p), pow(log2(n), num(q))))

    // ---------------------------------------------------------------------------------------------------------------------
    // The master theorem.

    fun master(r: Recurrence): Analysis {
        val n = Expr.Sym(r.variable)
        val terms = r.terms.map { num(it.first) to num(it.second) }
        val fo = orderOf(r.f, r.variable)
        val shownOrder = fo?.let { it.first to num(it.second) }
        if (r.terms.size > 1) return Analysis(Method.MASTER, r.function, r.variable, terms, r.f, shownOrder, null, gap = Gap.SEVERAL_SIZES)
        val (a, factor) = r.terms.single()
        val b = Rational.ONE / factor
        val critical = tidy(fn(Func.LOG, num(b), num(a)))
        val (case, order, regularity) = if (fo == null) {
            Triple(1, tidy(pow(n, critical)), null)
        } else {
            val (p, q) = fo
            val s = comparePower(p, a, b, critical)
            when {
                s < 0 -> Triple(1, tidy(pow(n, critical)), null)
                s == 0 && q.signum >= 0 -> Triple(2, power(n, critical, q + Rational.ONE), null)
                s == 0 -> return Analysis(Method.MASTER, r.function, r.variable, terms, r.f, shownOrder, critical, gap = Gap.BETWEEN_CASES_1_AND_2)
                else -> {
                    // Regularity: a·f(n/b)/f(n) → a/b^p, which is below 1 exactly where p > log_b a.
                    val limit = tidy(mul(num(a), pow(num(b), neg(p))))
                    if (Asymptotics.signOf(add(Expr.ONE, neg(limit))) <= 0) throw CalcException(Failure.UNSOLVED)
                    Triple(3, power(n, p, q), limit)
                }
            }
        }
        val samples = masterCheck(a, b, r.f, order, r.variable)
        return Analysis(Method.MASTER, r.function, r.variable, terms, r.f, shownOrder, critical, case = case, regularity = regularity,
            order = order, samples = samples)
    }

    /** The sign of p − log_b a: exactly for a rational p = u/v (b^u against a^v), else in ball arithmetic. */
    private fun comparePower(p: Expr, a: Rational, b: Rational, critical: Expr): Int {
        val r = rational(p) ?: return Asymptotics.signOf(add(p, neg(critical)))
        val c = rational(critical)
        if (c != null) return r.compareTo(c)
        val u = r.num.toInt()
        val v = r.den.toInt()
        if (r.num.bitLength() > 30 || r.den.bitLength() > 30) return Asymptotics.signOf(add(p, neg(critical)))
        val left = b.pow(u) ?: return Asymptotics.signOf(add(p, neg(critical)))
        val right = a.pow(v) ?: return Asymptotics.signOf(add(p, neg(critical)))
        return left.compareTo(right)
    }

    /**
     * T(b^k) = a·T(b^(k−1)) + f(b^k) from T = 1 at the first b^k ≥ 16, worked out in ball arithmetic up to about 2^1024, and
     * T(n)/order(n) along it: it must level off (its logarithm change by less than 0.08 from k = K/2 to K and by less than 0.05 from
     * k = 3K/4 to K).
     */
    private fun masterCheck(a: Rational, b: Rational, f: Expr, order: Expr, n: String): List<Sample> {
        val prec = 160
        val bits = kotlin.math.ln(b.toDouble()) / kotlin.math.ln(2.0)
        val last = (1024 / bits).toInt().coerceIn(64, 1024)
        var k0 = 1
        while (b.pow(k0)!! < Rational.of(16)) k0++
        val aBall = Ball.of(a, prec)
        var t = Ball.ONE
        val ratios = HashMap<Int, Ball>()
        for (k in k0 + 1..last) {
            Budget.check()
            val nk = CBall(Ball.of(b.pow(k) ?: throw CalcException(Failure.UNSOLVED), prec))
            val fk = if (isZero(f)) Ball.ZERO else Precise.eval(f, prec, false, mapOf(n to nk)).re
            if (!isZero(f) && !fk.isPositive) throw CalcException(Failure.UNSOLVED)
            t = aBall.mul(t, prec).add(fk, prec)
            if (k == last || k == last / 2 || k == 3 * last / 4 || k in sampleAt(last)) {
                val g = Precise.eval(order, prec, false, mapOf(n to nk)).re
                if (!g.isPositive) throw CalcException(Failure.UNSOLVED)
                ratios[k] = t.div(g, prec)
            }
        }
        levelsOff(ratios, last, 0.08, 0.05)
        return sampleAt(last).map { k -> Sample(num(b), k, digits(ratios.getValue(k))) }
    }

    private fun sampleAt(last: Int) = listOf(last / 16, last / 8, last / 4, last / 2, last).filter { it > 4 }.distinct()

    /** The ratio's logarithm changes little over the last half: it levels off, as T(n) = Θ(order) needs. */
    private fun levelsOff(ratios: Map<Int, Ball>, last: Int, half: Double, quarter: Double) {
        val r = ratios.getValue(last)
        val h = ratios.getValue(last / 2)
        val q = ratios.getValue(3 * last / 4)
        if (!r.isPositive || !h.isPositive || !q.isPositive) throw CalcException(Failure.UNSOLVED)
        val lr = kotlin.math.ln(r.mid.toDouble())
        if (kotlin.math.abs(lr - kotlin.math.ln(h.mid.toDouble())) > half || kotlin.math.abs(lr - kotlin.math.ln(q.mid.toDouble())) > quarter) {
            throw CalcException(Failure.UNSOLVED)
        }
    }

    private fun digits(b: Ball): Decimal {
        for (d in 4 downTo 1) Precise.decimal(b, d)?.let { return it }
        throw CalcException(Failure.UNSOLVED)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Akra–Bazzi.

    fun akraBazzi(r: Recurrence): Analysis {
        val n = Expr.Sym(r.variable)
        val terms = r.terms.map { num(it.first) to num(it.second) }
        val fo = orderOf(r.f, r.variable)
        // p with Σ aᵢ·bᵢ^p = 1: exact where all sizes are one (log_{1/b} Σaᵢ) or a rational p checks exactly, else in digits.
        var digits: Decimal? = null
        var pBall: Ball? = null
        val exact: Expr? = if (r.terms.size == 1 || r.terms.map { it.second }.distinct().size == 1) {
            val a = r.terms.fold(Rational.ZERO) { s, t -> s + t.first }
            tidy(fn(Func.LOG, num(Rational.ONE / r.terms[0].second), num(a)))
        } else {
            rationalP(r.terms) ?: run {
                val (ball, d) = digitsOfP(r.terms)
                pBall = ball
                digits = d
                null
            }
        }
        val p: Expr = exact ?: Expr.Sym("p")
        val (order, integral) = if (fo == null) {
            pow(n, p) to Expr.ONE
        } else {
            val (q, k) = fo
            // Where q is against p: φ(q) = Σ aᵢ·bᵢ^q − 1 falls as q grows, so φ(q) > 0 exactly where q < p.
            val phi = add(r.terms.map { (a, b) -> mul(num(a), pow(num(b), q)) } + Expr.MINUS_ONE)
            when (val s = -Asymptotics.signOf(phi)) {
                -1 -> pow(n, p) to Expr.ONE
                0 -> when {
                    k > -Rational.ONE -> tidy(mul(pow(n, p), pow(log2(n), num(k + Rational.ONE)))) to tidy(pow(log2(n), num(k + Rational.ONE)))
                    k == -Rational.ONE -> tidy(mul(pow(n, p), log2(log2(n)))) to log2(log2(n))
                    else -> pow(n, p) to Expr.ONE
                }
                else -> {
                    check(s == 1)
                    power(n, q, k) to tidy(mul(pow(n, add(q, neg(p))), pow(log2(n), num(k))))
                }
            }
        }
        val shown = tidy(order)
        val samples = floorCheck(r, shown, pBall)
        return Analysis(Method.AKRA_BAZZI, r.function, r.variable, terms, r.f, fo?.let { it.first to num(it.second) }, exact,
            criticalDigits = digits, integral = tidy(integral), order = shown, samples = samples)
    }

    private fun phiBall(terms: List<Pair<Rational, Rational>>, t: Ball, prec: Int): Ball {
        var s = Ball.of(-1)
        for ((a, b) in terms) s = s.add(Ball.of(a, prec).mul(BallMath.exp(t.mul(BallMath.ln(Ball.of(b, prec), prec), prec), prec), prec), prec)
        return s
    }

    /** A rational p near the root of φ that makes Σ aᵢ·bᵢ^p exactly 1 (p = 1 for T(n/3) + T(2n/3)), or null. */
    private fun rationalP(terms: List<Pair<Rational, Rational>>): Expr? {
        var lo = -64.0
        var hi = 64.0
        fun phi(t: Double) = terms.sumOf { (a, b) -> a.toDouble() * Math.pow(b.toDouble(), t) } - 1
        if (phi(lo) <= 0 || phi(hi) >= 0) throw CalcException(Failure.UNSOLVED)
        repeat(200) { val m = (lo + hi) / 2; if (phi(m) > 0) lo = m else hi = m }
        val near = Rational.near((lo + hi) / 2, 24, 1e-9) ?: return null
        val exact = tidy(add(terms.map { (a, b) -> mul(num(a), pow(num(b), num(near))) }))
        return if (isOne(exact)) num(near) else null
    }

    /** p by bisection in ball arithmetic: a ball that holds it, and its certified digits. */
    private fun digitsOfP(terms: List<Pair<Rational, Rational>>): Pair<Ball, Decimal> {
        val prec = 128
        var lo = BigFloat.of(-64)
        var hi = BigFloat.of(64)
        repeat(90) {
            Budget.check()
            val m = (lo + hi).shl(-1)
            val v = phiBall(terms, Ball(m), prec)
            when {
                v.isPositive -> lo = m
                v.isNegative -> hi = m
                else -> return@repeat
            }
        }
        val ball = Ball.span(lo, hi)
        for (d in 10 downTo 3) Precise.decimal(ball, d)?.let { return ball to it }
        throw CalcException(Failure.UNSOLVED)
    }

    /**
     * T(m) = Σ aᵢ·T(⌊bᵢ·m⌋) + g(m) for m ≥ 16, T(m) = 1 below, worked out for m = 2^k up to 2^60 (exactly where g has whole
     * values at whole numbers, else in balls), and T(m)/order(m) along it: it must level off.
     */
    private fun floorCheck(r: Recurrence, order: Expr, p: Ball?): List<Sample> {
        val prec = 128
        val last = 60
        val exact = isPolynomial(r.f, r.variable)
        val memo = HashMap<Long, Ball>()
        val coefficients = r.terms.map { Ball.of(it.first, prec) }
        fun g(m: Long): Ball = if (isZero(r.f)) Ball.ZERO else if (exact) {
            Ball.of(rational(tidy(r.f.substitute(Expr.Sym(r.variable), num(m)))) ?: throw CalcException(Failure.UNSOLVED), prec)
        } else Precise.eval(r.f, prec, false, mapOf(r.variable to CBall(Ball.of(m)))).re
        fun t(m: Long): Ball {
            if (m < 16) return Ball.ONE
            memo[m]?.let { return it }
            Budget.check()
            var s = g(m)
            if (!isZero(r.f) && !s.isPositive) throw CalcException(Failure.UNSOLVED)
            for ((i, term) in r.terms.withIndex()) {
                val down = (Rational.of(m) * term.second).let { it.num / it.den }.toLong()
                s = s.add(coefficients[i].mul(t(down), prec), prec)
            }
            memo[m] = s
            return s
        }
        val env = HashMap<String, CBall>()
        if (p != null) env["p"] = CBall(p)
        val ratios = HashMap<Int, Ball>()
        for (k in listOf(15, 30, 45, 60)) {
            val m = 1L shl k
            env[r.variable] = CBall(Ball.of(m))
            val o = Precise.eval(order, prec, false, env).re
            if (!o.isPositive) throw CalcException(Failure.UNSOLVED)
            ratios[k] = t(m).div(o, prec)
        }
        levelsOff(ratios, last, 0.12, 0.08)
        return listOf(15, 30, 45, 60).map { Sample(Expr.TWO, it, digits(ratios.getValue(it))) }
    }

    /** f a polynomial in n with rational coefficients: its values at whole numbers are worked out exactly. */
    private fun isPolynomial(f: Expr, n: String): Boolean = when (f) {
        is Expr.Num -> true
        is Expr.Sym -> f.name == n
        is Expr.Add -> f.terms.all { isPolynomial(it, n) }
        is Expr.Mul -> f.factors.all { isPolynomial(it, n) }
        is Expr.Pow -> isPolynomial(f.base, n) && rational(f.exp)?.let { it.isInteger && it.signum >= 0 } == true
        else -> false
    }
}

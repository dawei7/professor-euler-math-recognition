package com.professoreuler.mathcalc

import java.math.BigInteger

/**
 * The Risch–Norman (parallel Risch) heuristic, written from the descriptions by A. C. Norman and M. Bronstein ("Structure
 * theorems for parallel integration", J. Symbolic Computation, 2007, and his "Poor Man's Integrator"): the integrand is a
 * fraction P/Q of polynomials in x and kernels θ — eᵘ, ln u, tan u, arctan u, and sin v with cos v as a pair (sin² + cos² = 1)
 * — whose derivatives are fractions in them again. The antiderivative is sought at once in all of them as
 *
 *   A/V + Σ cⱼ·ln wⱼ + Σ eₖ·arctan(rₖ),
 *
 * V from Q's factors (a normal factor w^k gives w^(k−1), a special one, which divides its own derivative as eˣ and 1 + tan²x
 * do, stays whole), the wⱼ Q's factors and the special polynomials, the rₖ from quadratic factors, A a polynomial with unknown
 * coefficients up to a total degree. Differentiated and multiplied by the common denominator, A/V + … = ∫ P/Q is a polynomial
 * identity whose coefficients are linear equations in the unknowns, solved exactly. So x e^x sin x, e^x(1/x − 1/x²), ln(x)²,
 * ln(x)/x², e^(x + eˣ), tan(x)·sec(x)² and x·tanh(x)² are integrated. It is a heuristic: a degree bound too small, or a
 * logarithm not among the candidates, leaves an integral undone that has an elementary antiderivative.
 */
internal object RischNorman {
    private class Fail : Exception() {
        override fun fillInStackTrace(): Throwable = this
    }

    private fun fail(): Nothing = throw Fail()

    /** Most unknown coefficients in one linear system. */
    private const val MOST_UNKNOWNS = 220

    fun of(e: Expr, x: Expr.Sym): Expr? = try {
        Tower(x).integrate(normal(e, x))
    } catch (z: Fail) {
        null
    } catch (z: CalcException) {
        if (z.failure == Failure.TOO_LONG) throw z
        null
    } catch (z: ArithmeticException) {
        null
    }

    /** sec, csc, cot by sin and cos, sinh, cosh, tanh by exponentials, bᵘ as e^(u ln b), log_b by ln. */
    private fun normal(e: Expr, x: Expr.Sym): Expr {
        if (!e.has(x)) return e
        return when (e) {
            is Expr.Add -> add(e.terms.map { normal(it, x) })
            is Expr.Mul -> mul(e.factors.map { normal(it, x) })
            is Expr.Pow -> {
                val b = normal(e.base, x)
                val p = normal(e.exp, x)
                if (p.has(x) && b != Expr.E) exp(mul(p, ln(b))) else pow(b, p)
            }
            is Expr.Fn -> {
                val u = normal(e.args.last(), x)
                when (e.f) {
                    Func.SEC -> pow(fn(Func.COS, u), Expr.MINUS_ONE)
                    Func.CSC -> pow(fn(Func.SIN, u), Expr.MINUS_ONE)
                    Func.COT -> mul(fn(Func.COS, u), pow(fn(Func.SIN, u), Expr.MINUS_ONE))
                    Func.SINH -> mul(Expr.HALF, add(exp(u), neg(exp(neg(u)))))
                    Func.COSH -> mul(Expr.HALF, add(exp(u), exp(neg(u))))
                    Func.TANH -> mul(add(exp(mul(Expr.TWO, u)), Expr.MINUS_ONE), pow(add(exp(mul(Expr.TWO, u)), Expr.ONE), Expr.MINUS_ONE))
                    Func.LOG -> mul(ln(u), pow(ln(e.args[0]), Expr.MINUS_ONE))
                    Func.ACOT -> add(mul(Expr.HALF, Expr.PI), neg(fn(Func.ATAN, u)))
                    else -> fn(e.f, e.args.dropLast(1) + u)
                }
            }
            else -> e
        }
    }

    private enum class Kind { EXP, LN, TAN, ATAN, SIN, COS }

    /** A kernel: its letter, the expression it stands for, and its argument (for eᵘ the exponent of the group's base). */
    private class Kernel(val name: String, val kind: Kind, val arg: Expr) {
        val expr: Expr = when (kind) {
            Kind.EXP -> exp(arg)
            Kind.LN -> ln(arg)
            Kind.TAN -> fn(Func.TAN, arg)
            Kind.ATAN -> fn(Func.ATAN, arg)
            Kind.SIN -> fn(Func.SIN, arg)
            Kind.COS -> fn(Func.COS, arg)
        }
        val sym = Expr.Sym(name)
    }

    private class Tower(val x: Expr.Sym, val halved: Boolean = false) {
        val kernels = ArrayList<Kernel>()
        private var sinName: String? = null
        private var cosName: String? = null

        private fun named(kind: Kind, arg: Expr): Kernel {
            kernels.firstOrNull { it.kind == kind && it.arg == arg }?.let { return it }
            if (kernels.size >= 5) fail()
            val k = Kernel("\u0001r${kernels.size}", kind, arg)
            kernels += k
            if (kind == Kind.SIN) sinName = k.name
            if (kind == Kind.COS) cosName = k.name
            return k
        }

        /** The exponents of the exponentials, grouped by rational ratios, and each group's base: e^(base·r) = θ^(r/…). */
        private val expBases = ArrayList<Expr>()
        private val trigBases = ArrayList<Expr>()

        /** [u]·c = base for one of [bases] with c rational: (base index, u/base); a new base where none fits. */
        private fun groupOf(u: Expr, bases: MutableList<Expr>): Pair<Int, Rational> {
            for ((i, b) in bases.withIndex()) {
                val r = rational(Calculus.tidy(mul(u, pow(b, Expr.MINUS_ONE))))
                if (r != null && r.signum != 0) return i to r
            }
            bases += u
            return bases.size - 1 to Rational.ONE
        }

        /** Every exponential and trigonometric argument in [e], so the groups' bases are fixed before any kernel is named. */
        private fun scan(e: Expr) {
            when {
                !e.has(x) -> return
                // e^(x + eˣ) as eˣ·e^(eˣ): each term of the exponent its own exponential.
                e is Expr.Pow && e.base == Expr.E -> { terms(e.exp).filter { it.has(x) }.forEach { exps += it }; scan(e.exp) }
                e is Expr.Fn && (e.f == Func.SIN || e.f == Func.COS) -> { trigs += e.args[0]; scan(e.args[0]) }
                e is Expr.Add -> e.terms.forEach(::scan)
                e is Expr.Mul -> e.factors.forEach(::scan)
                e is Expr.Pow -> { scan(e.base); scan(e.exp) }
                e is Expr.Fn -> e.args.forEach(::scan)
            }
        }

        private val exps = ArrayList<Expr>()
        private val trigs = ArrayList<Expr>()

        /** Bases: for each group of exponents with rational ratios, the greatest common part (e^x and e^(x/2): base x/2). */
        private fun bases(all: List<Expr>, out: MutableList<Expr>) {
            for (u in all) {
                val (i, r) = groupOf(u, out)
                // A smaller step: the base divided by the denominator of r.
                if (!r.isInteger) out[i] = Calculus.tidy(mul(out[i], num(Rational.ONE / Rational.of(r.den))))
            }
        }

        /** [e] with every kernel a letter; null where something else of x remains (√x, arcsin x). */
        fun letters(e: Expr): Expr {
            Budget.check()
            if (!e.has(x)) return e
            if (e == x) return e
            return when (e) {
                is Expr.Add -> add(e.terms.map(::letters))
                is Expr.Mul -> mul(e.factors.map(::letters))
                is Expr.Pow -> when {
                    e.base == Expr.E -> mul(terms(e.exp).map { u ->
                        if (!u.has(x)) exp(u) else {
                            val (i, r) = groupOf(u, expBases)
                            if (!r.isInteger) fail()
                            pow(named(Kind.EXP, expBases[i]).sym, num(r))
                        }
                    })
                    integer(e.exp) != null -> pow(letters(e.base), e.exp)
                    else -> fail()
                }
                is Expr.Fn -> when (e.f) {
                    Func.LN -> named(Kind.LN, e.args[0]).sym
                    Func.ATAN -> named(Kind.ATAN, e.args[0]).sym
                    Func.TAN -> named(Kind.TAN, e.args[0]).sym
                    Func.SIN, Func.COS -> {
                        val (i, r) = groupOf(e.args[0], trigBases)
                        if (!r.isInteger || r.num.abs() > BigInteger.valueOf(6)) fail()
                        val v = trigBases[i]
                        if (kernels.any { (it.kind == Kind.SIN || it.kind == Kind.COS) && it.arg != v }) fail()
                        val s = named(Kind.SIN, v).sym
                        val c = named(Kind.COS, v).sym
                        multiple(e.f == Func.SIN, r.num.toInt(), s, c)
                    }
                    else -> fail()
                }
                else -> fail()
            }
        }

        private fun terms(u: Expr): List<Expr> = if (u is Expr.Add) u.terms else listOf(u)

        /** sin(m v) or cos(m v) as a polynomial in s = sin v, c = cos v. */
        private fun multiple(sine: Boolean, m: Int, s: Expr, c: Expr): Expr {
            var sn: Expr = Expr.ZERO
            var cs: Expr = Expr.ONE
            repeat(kotlin.math.abs(m)) {
                val next = add(mul(sn, c), mul(cs, s))
                cs = add(mul(cs, c), neg(mul(sn, s)))
                sn = next
            }
            if (m < 0) sn = neg(sn)
            return Algebra.expand(if (sine) sn else cs)
        }

        /** A fraction of polynomials in x and the kernels' letters. */
        fun fraction(e: Expr): Pair<Polynomial, Polynomial> {
            val k = Algebra.Kernels()
            val f = Algebra.fraction(letters(e), k) ?: fail()
            if (!k.isEmpty) fail()
            return f
        }

        lateinit var derivatives: Map<String, Pair<Polynomial, Polynomial>>

        fun integrate(e: Expr): Expr? {
            scan(e)
            bases(exps, expBases)
            bases(trigs, trigBases)
            val (p0, q0) = fraction(e)
            if (kernels.isEmpty()) return null
            // Each kernel's derivative as a fraction (new kernels it needs are named on the way, a few times round).
            val known = HashMap<String, Pair<Polynomial, Polynomial>>()
            known[x.name] = Polynomial.ONE to Polynomial.ONE
            var round = 0
            while (kernels.any { it.name !in known }) {
                if (++round > 8) fail()
                for (k in kernels.toList()) if (k.name !in known) known[k.name] = fraction(derivativeOf(k))
            }
            derivatives = known
            val pair = sinName != null && cosName != null
            // sin or cos in a denominator (x/(1 + cos x)): with T = tan(v/2) instead, sin v = 2T/(1 + T²), cos v = (1 − T²)/(1 + T²),
            // a kernel of its own with T′ = v′(1 + T²)/2.
            if (pair && (q0.degreeIn(sinName!!) > 0 || q0.degreeIn(cosName!!) > 0)) {
                if (halved) return null
                val v = trigBases.single()
                return Tower(x, halved = true).integrate(halfAngle(e, v))
            }
            return solve(p0, q0, pair)
        }

        /** [e] with sin(m v), cos(m v) and tan(m v) as fractions in tan(v/2). */
        private fun halfAngle(e: Expr, v: Expr): Expr {
            val t = fn(Func.TAN, mul(Expr.HALF, v))
            val below = pow(add(Expr.ONE, pow(t, Expr.TWO)), Expr.MINUS_ONE)
            val s = mul(Expr.TWO, t, below)
            val c = mul(add(Expr.ONE, neg(pow(t, Expr.TWO))), below)
            fun walk(u: Expr): Expr {
                if (!u.has(x)) return u
                if (u is Expr.Fn && (u.f == Func.SIN || u.f == Func.COS || u.f == Func.TAN) && u.args.size == 1) {
                    val r = rational(Calculus.tidy(mul(u.args[0], pow(v, Expr.MINUS_ONE))))
                    if (r != null && r.isInteger && r.num.abs() <= BigInteger.valueOf(6)) {
                        val m = r.num.toInt()
                        val sn = multiple(true, m, s, c)
                        val cs = multiple(false, m, s, c)
                        return when (u.f) {
                            Func.SIN -> sn
                            Func.COS -> cs
                            else -> mul(sn, pow(cs, Expr.MINUS_ONE))
                        }
                    }
                }
                return u.mapChildren(::walk)
            }
            return walk(e)
        }

        private fun derivativeOf(k: Kernel): Expr {
            val du = Calculus.diff(k.arg, x)
            return when (k.kind) {
                Kind.EXP -> mul(du, k.expr)
                Kind.LN -> mul(du, pow(k.arg, Expr.MINUS_ONE))
                Kind.TAN -> mul(du, add(Expr.ONE, pow(k.expr, Expr.TWO)))
                Kind.ATAN -> mul(du, pow(add(Expr.ONE, pow(k.arg, Expr.TWO)), Expr.MINUS_ONE))
                Kind.SIN -> mul(du, fn(Func.COS, k.arg))
                Kind.COS -> neg(mul(du, fn(Func.SIN, k.arg)))
            }
        }

        // -----------------------------------------------------------------------------------------------------------------

        private val variables: List<String> get() = listOf(x.name) + kernels.map { it.name }

        /** c² as 1 − s² where sin and cos are a pair: one form for each polynomial. */
        private fun reduce(p: Polynomial): Polynomial {
            val s = sinName ?: return p
            val c = cosName ?: return p
            if (p.degreeIn(c) < 2) return p
            var out = Polynomial(emptyMap())
            val oneMinusS2 = Polynomial(mapOf(Monomial.ONE to Rational.ONE, Monomial(listOf(s to 2)) to -Rational.ONE))
            for ((m, k) in p.terms) {
                val cp = m.power(c)
                val rest = Polynomial(mapOf(Monomial(m.powers.map { (v, e) -> if (v == c) v to cp % 2 else v to e }.filter { it.second > 0 }) to k))
                out += rest * oneMinusS2.pow(cp / 2)
            }
            return out
        }

        /** D̃p = d·Dp, with d the common denominator of the derivatives: a polynomial. */
        private fun derive(p: Polynomial, tilde: Map<String, Polynomial>): Polynomial {
            var out = Polynomial(emptyMap())
            for (v in p.unknowns) {
                val dv = tilde[v] ?: continue
                out += partial(p, v) * dv
            }
            return reduce(out)
        }

        private fun partial(p: Polynomial, v: String): Polynomial = Polynomial(p.terms.mapNotNull { (m, c) ->
            val k = m.power(v)
            if (k == 0) null else Monomial(m.powers.mapNotNull { (u, e) -> if (u == v) (if (e > 1) u to e - 1 else null) else u to e }) to c * Rational.of(k.toLong())
        }.groupBy({ it.first }, { it.second }).mapValues { (_, l) -> l.fold(Rational.ZERO) { a, b -> a + b } })

        private fun exactly(a: Polynomial, b: Polynomial): Polynomial = a.divideExactly(b) ?: fail()

        private fun solve(p0: Polynomial, q0: Polynomial, pair: Boolean): Expr? {
            // The common denominator d of the derivatives and D̃ = d·D.
            var d = Polynomial.ONE
            for ((_, f) in derivatives) d = lcm(d, f.second)
            val tilde = derivatives.mapValues { (_, f) -> reduce(exactly(f.first * d, f.second)) }
            val g = Algebra.gcd(p0, q0)
            val p = reduce(exactly(p0, g))
            val q = exactly(q0, g)
            // Q's factors: special (w | D̃w, w no factor of d) stay whole in V; normal ones lose one power.
            val product = Factor.of(q)
            var v = Polynomial.constant(product.constant)
            val logs = ArrayList<Polynomial>()
            for ((w, k) in product.factors) {
                if (w.isConstant) continue
                val special = derive(w, tilde).let { dw -> !dw.isZero && dw.divideExactly(w) != null } && d.divideExactly(w) == null
                v *= w.pow(if (special) k else k - 1)
                // eᵘ itself is never a logarithm's argument (ln eᵘ = u); every other factor may be.
                val monomialKernel = w.size == 1 && w.terms.keys.single().powers.size == 1 && kernels.any { it.kind == Kind.EXP && it.name == w.terms.keys.single().powers[0].first }
                if (!monomialKernel) logs += w
            }
            // Special polynomials of the kernels: 1 + θ² for tan, the argument u of ln u, and the denominators of derivatives.
            for (k in kernels) if (k.kind == Kind.TAN) logs += Polynomial(mapOf(Monomial.ONE to Rational.ONE, Monomial(listOf(k.name to 2)) to Rational.ONE))
            for ((w, _) in Factor.of(d).factors) if (!w.isConstant) logs += w
            if (pair) {
                // ln|sin v|, ln|cos v|, ln|1 ± sin v| …: their derivatives c/s, −s/c are fractions with sin or cos below.
                logs += Polynomial.variable(sinName!!)
                logs += Polynomial.variable(cosName!!)
            }
            val candidates = logs.distinct().filter { !it.isConstant }
            // Quadratics a z² + b z + c without real roots in one letter z: arctan((2az + b)/√(4ac − b²)), whose derivative is
            // (√(4ac − b²)/2)·D̃z/(d·w).
            val atans = candidates.filter { w -> w.unknowns.size == 1 && w.degree == 2 && discriminant(w)?.let { it.signum < 0 } == true }
            // Q again, with the logarithms' and arctangents' arguments, to clear every denominator: L.
            var l = lcm(v * v, q)
            for (w in candidates) l = lcm(l, w)
            val pd = reduce(p * d)
            val rhs = reduce(pd * exactly(l, q))
            val l1 = exactly(l, v)
            val l2 = exactly(exactly(l, v), v) * derive(v, tilde)
            val bound = maxOf(1, totalDegree(p) - totalDegree(q) + totalDegree(v) + 1)
            for (n in listOf(bound, bound + 1)) {
                Budget.check()
                val monomials = monomials(n, pair)
                if (monomials.size + candidates.size + atans.size > MOST_UNKNOWNS) return null
                val columns = ArrayList<Polynomial>()
                for (m in monomials) {
                    val mp = Polynomial(mapOf(m to Rational.ONE))
                    columns += reduce(l1 * derive(mp, tilde) - l2 * mp)
                }
                for (w in candidates) columns += reduce(exactly(l, w) * derive(w, tilde))
                for (w in atans) {
                    val z = w.unknowns.single()
                    columns += reduce(exactly(l, w) * (tilde[z] ?: fail()))
                }
                val solution = linear(columns, rhs) ?: continue
                // The antiderivative, its letters back as kernels.
                var a = Polynomial(emptyMap())
                for ((i, m) in monomials.withIndex()) if (solution[i].signum != 0) a += Polynomial(mapOf(m to solution[i]))
                var result = mul(back(a), pow(back(v), Expr.MINUS_ONE))
                for ((j, w) in candidates.withIndex()) {
                    val c = solution[monomials.size + j]
                    if (c.signum != 0) result = add(result, mul(num(c), ln(fn(Func.ABS, back(w)))))
                }
                for ((j, w) in atans.withIndex()) {
                    val c = solution[monomials.size + candidates.size + j]
                    if (c.signum == 0) continue
                    val z = w.unknowns.single()
                    val (a2, a1, a0) = quadratic(w, z)
                    val delta = sqrt(num(Rational.of(4) * a2 * a0 - a1 * a1))
                    val argument = mul(add(mul(num(Rational.of(2) * a2), back(Polynomial.variable(z))), num(a1)), pow(delta, Expr.MINUS_ONE))
                    // e·(δ/2) = c
                    result = add(result, mul(num(c * Rational.of(2)), pow(delta, Expr.MINUS_ONE), fn(Func.ATAN, argument)))
                }
                return result
            }
            return null
        }

        private fun discriminant(w: Polynomial): Rational? {
            val (a, b, c) = quadratic(w, w.unknowns.single())
            return b * b - Rational.of(4) * a * c
        }

        private fun quadratic(w: Polynomial, z: String): Triple<Rational, Rational, Rational> {
            val c = w.coefficientsIn(z)
            fun at(k: Int) = c[k]?.let { if (it.isConstant) it.constant else fail() } ?: Rational.ZERO
            return Triple(at(2), at(1), at(0))
        }

        private fun totalDegree(p: Polynomial) = maxOf(0, p.degree)

        /** Every monomial in the variables up to total degree [n] (cos to the first power where it is paired with sin). */
        private fun monomials(n: Int, pair: Boolean): List<Monomial> {
            val vars = variables
            val out = ArrayList<Monomial>()
            fun build(i: Int, left: Int, acc: List<Pair<String, Int>>) {
                if (i == vars.size) { out += Monomial(acc.sortedBy { it.first }); return }
                val top = if (pair && vars[i] == cosName) minOf(1, left) else left
                for (k in 0..top) build(i + 1, left - k, if (k > 0) acc + (vars[i] to k) else acc)
            }
            build(0, n, emptyList())
            return out
        }

        private fun lcm(a: Polynomial, b: Polynomial): Polynomial {
            val g = Algebra.gcd(a, b)
            return a * exactly(b, g)
        }

        /** The polynomial with its letters as the kernels they stand for. */
        fun back(p: Polynomial): Expr = add(p.terms.map { (m, c) ->
            mul(listOf(num(c)) + m.powers.map { (name, k) ->
                val base = if (name == x.name) x else kernels.first { it.name == name }.expr
                pow(base, num(k.toLong()))
            })
        })

        /** Columns · u = target over Q, exactly, free unknowns 0: null where it has no solution. */
        private fun linear(columns: List<Polynomial>, target: Polynomial): List<Rational>? {
            val index = HashMap<Monomial, Int>()
            val rows = ArrayList<HashMap<Int, Rational>>()
            val rhs = ArrayList<Rational>()
            fun row(m: Monomial): Int = index.getOrPut(m) { rows += HashMap(); rhs += Rational.ZERO; rows.size - 1 }
            for ((j, col) in columns.withIndex()) for ((m, c) in col.terms) rows[row(m)][j] = c
            for ((m, c) in target.terms) { val r = row(m); rhs[r] = rhs[r] + c }
            val n = columns.size
            val pivotRow = IntArray(n) { -1 }
            var r = 0
            val order = rows.indices.toMutableList()
            for (col in 0 until n) {
                Budget.check()
                val p = (r until order.size).firstOrNull { rows[order[it]][col]?.signum?.let { s -> s != 0 } == true } ?: continue
                order[r] = order[p].also { order[p] = order[r] }
                val pr = order[r]
                val lead = rows[pr][col]!!
                val inv = Rational.ONE / lead
                for (k in rows[pr].keys.toList()) rows[pr][k] = rows[pr][k]!! * inv
                rhs[pr] = rhs[pr] * inv
                for (i in order.indices) {
                    val ri = order[i]
                    if (ri == pr) continue
                    val f = rows[ri][col] ?: continue
                    if (f.signum == 0) continue
                    for ((k, value) in rows[pr]) {
                        val nv = (rows[ri][k] ?: Rational.ZERO) - f * value
                        if (nv.signum == 0) rows[ri].remove(k) else rows[ri][k] = nv
                    }
                    rhs[ri] = rhs[ri] - f * rhs[pr]
                }
                pivotRow[col] = pr
                r++
            }
            // The rows left without a pivot are 0 in every unknown (reduced row echelon form): they must read 0 = 0.
            for (i in r until order.size) if (rhs[order[i]].signum != 0) return null
            // The free unknowns 0, each pivot unknown its row's right side.
            val out = MutableList(n) { Rational.ZERO }
            for (col in 0 until n) if (pivotRow[col] >= 0) out[col] = rhs[pivotRow[col]]
            return out
        }
    }
}

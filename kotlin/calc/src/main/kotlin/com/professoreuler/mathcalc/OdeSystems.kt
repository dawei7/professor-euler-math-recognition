package com.professoreuler.mathcalc

/**
 * Systems of first-order differential equations, one line for each unknown: x′ = f(t, x, y), y′ = g(t, x, y) (x, y, z or x₁, x₂,
 * … of t), with initial values x(0) = 1 beside them. A linear one, x′ = A·x + b(t) with constant A, is solved through A's
 * eigenvalues: for each eigenvalue λ of multiplicity m and each w of the generalized eigenspace ker (A − λI)ᵐ the solution
 * e^(λt)·Σₖ tᵏ/k!·(A − λI)ᵏw (that is e^(At)w, the series ending), complex pairs α ± βi taken apart into their real and imaginary
 * parts; b(t) by variation of constants, x_p = e^(At)∫e^(−At)b dt. Every solution is put back into every equation and its
 * initial values.
 */
object OdeSystems {
    /** An initial value of one unknown: [unknown]([at]) = [value]. */
    class Condition(val unknown: String, val at: Expr, val value: Expr)

    /** The system as read: [unknowns] of [t], each [unknown]′ = [slopes] (in the same order), and its initial values. */
    class Reading(val t: String, val unknowns: List<String>, val slopes: List<Expr>, val conditions: List<Condition>) {
        /** No letter but the unknowns and t. */
        val letters: Set<String> get() = slopes.flatMap { it.symbols() }.toSet() - unknowns.toSet() - t
    }

    /**
     * A linear system solved: x′ = [matrix]·x + [forcing], its [general] solution component by component in the [constants]
     * C₁ … Cₙ (from the [fundamental] solutions, each a column), the [particular] one where the initial values were given, the
     * [eigen]values of the matrix and, in the plane, its [equilibrium]; [values] of the constants the initial values fix.
     */
    class Solution(val t: String, val unknowns: List<String>, val matrix: Expr.Mat, val forcing: List<Expr>, val fundamental: List<List<Expr>>,
        val general: List<Expr>, val constants: List<String>, val particular: List<Expr>?, val eigen: List<LinAlg.Eigen>,
        val equilibrium: Fields.Equilibrium?, val values: Map<String, Expr>? = null)

    private fun tidy(e: Expr) = Calculus.tidy(e)

    private val CONDITION = Regex("""^\s*([a-z](?:_\{?[0-9]\}?)?)\s*\((.+)\)\s*$""")

    /** Lines that are a system in two or more unknowns of [Parser.SYSTEM_UNKNOWNS] and [Parser.ODE_UNKNOWNS], or null. */
    fun read(statements: List<Statement>, written: String?): Reading? {
        val names = Parser.ODE_UNKNOWNS + Parser.SYSTEM_UNKNOWNS
        val all = statements.flatMap { s -> s.sides.filterNotNull().flatMap { it.symbols() } }.toSet()
        val unknowns = names.filter { n -> "$n'" in all || all.any { it.startsWith(n) && it.length > n.length && it.drop(n.length).all { c -> c == '\'' } } }
            .sortedWith(compareBy({ it.substringBefore('_') }, { it.substringAfter('_', "") }))
        if (unknowns.size < 2) return null
        val t = written ?: "t"
        if (t in unknowns) return null
        val slopes = HashMap<String, Expr>()
        val conditions = ArrayList<Condition>()
        for (s in statements) {
            val sides = s.sides.filterNotNull()
            if (sides.size != 2 || s.relations != listOf(Rel.EQ)) throw CalcException(Failure.UNREADABLE)
            condition(s, unknowns)?.let { conditions += it; continue }
            val e = tidy(Calculus.evaluate(sides[0]) - Calculus.evaluate(sides[1]))
            val markers = e.symbols().filter { it.endsWith("'") }
            if (markers.size != 1) throw CalcException(Failure.UNSOLVED)
            val marker = markers[0]
            val unknown = marker.dropLast(1)
            if (unknown !in unknowns || unknown in slopes) throw CalcException(Failure.UNSOLVED)
            val m = Expr.Sym(marker)
            val coefficient = tidy(Calculus.derivative(e, m))
            if (coefficient.symbols().isNotEmpty() || LinAlg.isNull(coefficient)) throw CalcException(Failure.UNSOLVED)
            val rest = tidy(e.substitute(m, Expr.ZERO))
            if (rest.symbols().any { it.endsWith("'") }) throw CalcException(Failure.UNSOLVED)
            slopes[unknown] = tidy(neg(rest) / coefficient)
        }
        if (slopes.keys != unknowns.toSet()) throw CalcException(Failure.UNSOLVED)
        return Reading(t, unknowns, unknowns.map { slopes.getValue(it) }, conditions)
    }

    /** x(0) = 1 as written: the unknown, the point and the value; null where the line is no initial value. */
    private fun condition(s: Statement, unknowns: List<String>): Condition? {
        if (s.texts.size < 2) return null
        val m = CONDITION.find(s.texts[0].replace("\\left", "").replace("\\right", "")) ?: return null
        val name = m.groupValues[1].replace("{", "").replace("}", "")
        if (name !in unknowns) return null
        val at = pointOf(m.groupValues[2]) ?: return null
        val value = s.sides.getOrNull(1)?.let { Calculus.evaluate(it) }?.takeIf { it.symbols().isEmpty() } ?: return null
        return Condition(name, at, value)
    }

    private fun pointOf(latex: String): Expr? = try {
        val tokens = MathCalc.TOKEN.findAll(latex).map { it.value }.toList()
        Parser(tokens, Flags(Assumptions())).statements().singleOrNull()?.sides?.filterNotNull()?.singleOrNull()
            ?.let { Calculus.evaluate(it) }?.takeIf { it.symbols().isEmpty() && !it.hasInfinity() }
    } catch (e: CalcException) {
        null
    }

    /** The matrix A and the forcing b of a reading that is linear with constant coefficients: x′ = A·x + b(t); null otherwise. */
    fun linear(r: Reading): Pair<Expr.Mat, List<Expr>>? {
        val xs = r.unknowns.map { Expr.Sym(it) }
        val rows = ArrayList<List<Expr>>()
        val b = ArrayList<Expr>()
        for (f in r.slopes) {
            val row = xs.map { x -> tidy(Calculus.derivative(f, x)) }
            if (row.any { it.symbols().isNotEmpty() }) return null
            rows += row
            val rest = tidy(xs.fold(f) { acc, x -> acc.substitute(x, Expr.ZERO) })
            if (rest.symbols().any { it != r.t }) return null
            b += rest
        }
        // Linear: f − A·x − b is 0.
        for ((i, f) in r.slopes.withIndex()) {
            val d = tidy(Algebra.expand(f - add(xs.indices.map { j -> mul(rows[i][j], xs[j]) }) - b[i]))
            if (!LinAlg.isNull(d)) return null
        }
        return Expr.Mat(rows) to b
    }

    /** Zero for every t: exactly once multiplied out, or at random values. */
    internal fun vanishes(e: Expr): Boolean {
        val expanded = tidy(Algebra.expand(e))
        return LinAlg.isNull(expanded) || Verify.same(expanded, Expr.ZERO)
    }

    /** A sum of products of sines and cosines as the shortest of itself and its sum form. */
    private fun short(e: Expr): Expr {
        val t = tidy(Algebra.expand(e))
        if (!TrigForms.has(t)) return t
        val sums = tidy(Algebra.expand(TrigForms.productsToSums(t)))
        return if (sums.size() < t.size()) sums else t
    }

    /** Solves a linear reading; [Failure.UNSOLVED] where its eigenvalues are not found exactly or a check fails. */
    fun solve(r: Reading): Solution {
        val (a, b) = linear(r) ?: throw CalcException(Failure.UNSOLVED)
        if (r.letters.isNotEmpty()) throw CalcException(Failure.UNSOLVED)
        val n = r.unknowns.size
        if (n > 4) throw CalcException(Failure.TOO_LARGE)
        val t = Expr.Sym(r.t)
        val eigen = LinAlg.eigen(a) ?: throw CalcException(Failure.UNSOLVED)
        val fundamental = fundamental(a, eigen, t) ?: throw CalcException(Failure.UNSOLVED)
        if (fundamental.size != n) throw CalcException(Failure.UNSOLVED)
        // Each column solves x′ = A·x, and they are independent: det Φ(0) ≠ 0.
        for (phi in fundamental) if (!solves(a, List(n) { Expr.ZERO }, phi, t)) throw CalcException(Failure.UNSOLVED)
        val atZero = Expr.Mat(List(n) { i -> fundamental.map { tidy(it[i].substitute(t, Expr.ZERO)) } })
        if (LinAlg.isNull(LinAlg.det(atZero))) throw CalcException(Failure.UNSOLVED)
        val forced = b.any { !LinAlg.isNull(it) }
        val particular = if (forced) particular(a, b, fundamental, atZero, t) ?: throw CalcException(Failure.UNSOLVED) else List(n) { Expr.ZERO }
        if (forced && !solves(a, b, particular, t)) throw CalcException(Failure.UNSOLVED)
        val constants = (1..n).map { "C_$it" }
        val general = List(n) { i -> tidy(add(fundamental.mapIndexed { j, phi -> mul(Expr.Sym(constants[j]), phi[i]) }) + particular[i]) }
        if (!solves(a, b, general, t)) throw CalcException(Failure.UNSOLVED)
        val solved = if (r.conditions.isEmpty()) null else withConditions(r, a, b, general, constants, t)
        val equilibrium = if (n == 2 && b.all { it.symbols().isEmpty() }) Fields.linearEquilibrium(a, b) else null
        return Solution(r.t, r.unknowns, a, b, fundamental, general, constants, solved?.first, eigen, equilibrium, solved?.second)
    }

    /** Whether x(t) solves x′ = A·x + b: every component put back. */
    private fun solves(a: Expr.Mat, b: List<Expr>, x: List<Expr>, t: Expr.Sym): Boolean = x.indices.all { i ->
        Budget.check()
        vanishes(Calculus.derivative(x[i], t) - add(x.indices.map { j -> mul(a.rows[i][j], x[j]) }) - b[i])
    }

    /** The real fundamental solutions from the eigenvalues, each a list of components; null where a part cannot be written. */
    private fun fundamental(a: Expr.Mat, eigen: List<LinAlg.Eigen>, t: Expr.Sym): List<List<Expr>>? {
        val n = a.size
        val out = ArrayList<List<Expr>>()
        for (e in eigen) {
            Budget.check()
            val lambda = e.value
            val shifted = Expr.Mat(a.rows.mapIndexed { i, r -> r.mapIndexed { j, x -> if (i == j) tidy(x - lambda) else x } })
            val parts = Complex.parts(lambda) ?: return null
            val beta = parts.second
            val complex = beta != null && !LinAlg.isNull(beta)
            // One of each conjugate pair: the positive imaginary part.
            if (complex && (Numeric.value(beta!!) ?: return null) < 0) continue
            val generalized = LinAlg.nullSpace(LinAlg.power(shifted, e.multiplicity))
            if (generalized.size != e.multiplicity) return null
            for (w in generalized) {
                // Σₖ tᵏ/k!·Nᵏw, k from 0 until Nᵏw is 0.
                val terms = ArrayList<Expr.Mat>()
                var v = w
                var k = 0
                while (k <= e.multiplicity && !v.rows.all { LinAlg.isNull(it[0]) }) {
                    terms += v
                    v = LinAlg.times(shifted, v) as Expr.Mat
                    k++
                }
                fun polynomial(pick: (Expr) -> Expr?): List<Expr>? = List(n) { i ->
                    add(terms.mapIndexed { kk, term -> mul(pick(term.rows[i][0]) ?: return null, pow(t, num(kk.toLong())), num(Rational.ONE / factorial(kk))) })
                }
                if (!complex) {
                    val p = polynomial { it } ?: return null
                    out += p.map { tidy(mul(exp(mul(lambda, t)), it)) }
                } else {
                    // e^(αt)(cos βt + i sin βt)(p + iq): real part e^(αt)(p cos βt − q sin βt), imaginary e^(αt)(p sin βt + q cos βt).
                    val alpha = parts.first
                    val re = polynomial { c -> partsOf(c)?.first } ?: return null
                    val im = polynomial { c -> partsOf(c)?.second } ?: return null
                    val cos = fn(Func.COS, mul(beta!!, t))
                    val sin = fn(Func.SIN, mul(beta, t))
                    val front = exp(mul(alpha, t))
                    out += List(n) { i -> tidy(Algebra.expand(mul(front, re[i] * cos - im[i] * sin))) }
                    out += List(n) { i -> tidy(Algebra.expand(mul(front, re[i] * sin + im[i] * cos))) }
                }
            }
        }
        return out
    }

    private fun factorial(k: Int): Rational {
        var r = Rational.ONE
        for (j in 2..k) r = r * Rational.of(j.toLong())
        return r
    }

    /** A complex number's real and imaginary parts, 0 for a missing one. */
    private fun partsOf(c: Expr): Pair<Expr, Expr>? {
        Complex.parts(c)?.let { (re, im) -> return tidy(re) to tidy(im ?: Expr.ZERO) }
        return Complex.normal(c)?.let { (re, im) -> tidy(re) to tidy(im) }
    }

    /** x_p = e^(At)∫e^(−At)b dt with e^(At) = Φ(t)Φ(0)⁻¹; a constant b with A invertible as −A⁻¹b. */
    private fun particular(a: Expr.Mat, b: List<Expr>, fundamental: List<List<Expr>>, atZero: Expr.Mat, t: Expr.Sym): List<Expr>? {
        val n = a.size
        if (b.all { it.symbols().isEmpty() } && !LinAlg.isNull(LinAlg.det(a))) {
            val x = LinAlg.times(LinAlg.inverse(a), LinAlg.column(b)) as Expr.Mat
            return x.rows.map { tidy(neg(it[0])) }
        }
        val phi = Expr.Mat(List(n) { i -> fundamental.map { it[i] } })
        val e = LinAlg.times(phi, LinAlg.inverse(atZero)) as Expr.Mat
        val minus = Expr.Mat(e.rows.map { r -> r.map { tidy(it.substitute(t, neg(t))) } })
        val w = LinAlg.times(minus, LinAlg.column(b)) as Expr.Mat
        val integrated = w.rows.map { r -> Calculus.antiderivative(short(r[0]), t) ?: return null }
        val x = LinAlg.times(e, LinAlg.column(integrated)) as Expr.Mat
        return x.rows.map { short(it[0]) }
    }

    /** The constants the initial values fix (all at one point, one for each unknown), checked in the system and the values. */
    private fun withConditions(r: Reading, a: Expr.Mat, b: List<Expr>, general: List<Expr>, constants: List<String>, t: Expr.Sym): Pair<List<Expr>, Map<String, Expr>> {
        val n = r.unknowns.size
        if (r.conditions.size != n || r.conditions.map { it.unknown }.toSet().size != n) throw CalcException(Failure.UNSOLVED)
        val equations = r.conditions.map { c -> tidy(general[r.unknowns.indexOf(c.unknown)].substitute(t, c.at) - c.value) }
        val values = Solve.system(equations, constants)?.singleOrNull() ?: throw CalcException(Failure.UNSOLVED)
        if (constants.any { values[it] == null || values.getValue(it).symbols().isNotEmpty() }) throw CalcException(Failure.UNSOLVED)
        val solved = general.map { g -> short(constants.fold(g) { acc, k -> acc.substitute(Expr.Sym(k), values.getValue(k)) }) }
        if (!solves(a, b, solved, t)) throw CalcException(Failure.UNSOLVED)
        for (c in r.conditions) {
            if (!LinAlg.isNull(tidy(solved[r.unknowns.indexOf(c.unknown)].substitute(t, c.at) - c.value))) throw CalcException(Failure.UNSOLVED)
        }
        return solved to constants.associateWith { values.getValue(it) }
    }
}

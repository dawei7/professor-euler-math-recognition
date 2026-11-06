package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Partial differential equations for u of two variables, written with the subscripts of their partial derivatives as a
 * textbook writes them (u_t, u_xx, u_xy; typed `u_xx`, from the editor `u_{xx}`) or as ∂u/∂t, ∂²u/∂x², ∂²u/∂x∂y, which are read as
 * the same markers; u(0, t) = 0, u_x(L, t) = 0, u(x, 0) = f(x), u_t(x, 0) = g(x) are its conditions, written as lines (or as
 * further arguments of `pdsolve`), the way `dsolve` takes y(0) = 1.
 *
 * [classify]: the type of a second-order linear equation a·u_xx + b·u_xy + c·u_yy + d·u_x + e·u_y + f·u = g from its
 * discriminant b² − 4ac: elliptic, parabolic or hyperbolic, with variable coefficients the regions where each holds, and with
 * constant ones its canonical form, checked by the chain rule applied to plane waves e^(px + qy).
 *
 * [separate]: the classic problems of a first course, by separation of variables, as Fourier series: the heat equation
 * u_t = k·u_xx on 0 < x < L with fixed (Dirichlet) or insulated (Neumann) ends, the wave equation u_tt = c²·u_xx with its initial
 * shape and velocity, and Laplace's equation u_xx + u_yy = 0 on a rectangle with given values on its sides. The coefficients are
 * exact integrals (the engine's own, [Calculus.definite], with sin nπ = 0 and cos nπ = (−1)ⁿ for whole n as [Transforms] has
 * them), each checked against quadrature; every term is checked to solve the equation and its boundary conditions exactly, and
 * the conditions met only in the limit (the initial values, a nonzero side) are checked by the partial sums at points.
 */
object Pde {
    /** The unknown function. */
    const val U = "u"

    /** The variables a partial derivative may be taken in, in the order a subscript is written (u_xt, not u_tx). */
    internal val VARIABLES = listOf("x", "y", "t")

    /** The summation letter. */
    val N = Expr.Sym("n")

    private fun tidy(e: Expr) = Calculus.tidy(e)

    // ------------------------------------------------------------------------------------------------------------------
    // Markers: u_x, u_xx, … as letters, as y′ is "y'".

    /** The marker of ∂u/∂[v] (several letters: the derivative in each), its letters in order: u_xt for ∂²u/∂t∂x. */
    fun marker(v: String): Expr.Sym = Expr.Sym("u_" + sorted(v))

    private fun sorted(v: CharSequence): String = v.toList().sortedBy { VARIABLES.indexOf(it.toString()) }.joinToString("")

    /** The variables of a marker's derivative (u_tx: "xt"), or null where the name is no marker. */
    internal fun derivativeOf(name: String): String? {
        if (!name.startsWith("u_")) return null
        val v = name.substring(2).removePrefix("{").removeSuffix("}")
        if (v.isEmpty() || v.length > 4 || v.any { it.toString() !in VARIABLES }) return null
        return sorted(v)
    }

    internal fun hasMarkers(e: Expr): Boolean = e.symbols().any { derivativeOf(it) != null }

    /** The markers with their letters in order: u_yx is u_xy. */
    private fun normalized(e: Expr): Expr {
        var out = e
        for (s in e.symbols()) {
            val d = derivativeOf(s) ?: continue
            if (s != "u_$d") out = out.substitute(Expr.Sym(s), marker(d))
        }
        return out
    }

    /**
     * ∂u/∂t, ∂²u/∂x², ∂^{2}u/∂x∂y (a fraction's tokens above and below) as the markers u_t, u_xx, u_xy; null where the fraction
     * is no such derivative of u.
     */
    internal fun partial(top: List<String>, bottom: List<String>): Expr? {
        val d = "\\partial"
        if (top.isEmpty() || top[0] != d || bottom.isEmpty() || bottom[0] != d) return null
        val (order, rest) = when {
            top.size == 2 -> 1 to top.drop(1)
            top.size == 4 && top[1] == "^" -> (top[2].toIntOrNull() ?: return null) to top.drop(3)
            top.size == 6 && top[1] == "^" && top[2] == "{" && top[4] == "}" -> (top[3].toIntOrNull() ?: return null) to top.drop(5)
            else -> return null
        }
        if (rest != listOf(U) || order !in 1..2) return null
        val letters = StringBuilder()
        var k = 0
        while (k < bottom.size) {
            if (bottom[k] != d) return null
            val v = bottom.getOrNull(k + 1)?.takeIf { it in VARIABLES } ?: return null
            k += 2
            var power = 1
            if (bottom.getOrNull(k) == "^") {
                when {
                    bottom.getOrNull(k + 1)?.toIntOrNull() != null -> { power = bottom[k + 1].toInt(); k += 2 }
                    bottom.getOrNull(k + 1) == "{" && bottom.getOrNull(k + 3) == "}" -> { power = bottom[k + 2].toIntOrNull() ?: return null; k += 4 }
                    else -> return null
                }
            }
            repeat(power) { letters.append(v) }
        }
        if (letters.length != order) return null
        return marker(letters.toString())
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Reading the lines: the equation and its conditions.

    /** A point a condition fixes a variable at, or the variable itself (u(0, t): the point 0, then t). */
    internal sealed class Arg {
        data class Letter(val name: String) : Arg()
        data class Point(val value: Expr) : Arg()
    }

    /** u(a, t) = value, u_x(a, t) = value (the derivative's letter in [derivative], "" for u itself). */
    internal class Condition(val derivative: String, val args: List<Arg>, val value: Expr)

    /** A partial differential equation (left − right, its markers in order) and the conditions written beside it. */
    internal class Reading(val equation: Expr, val conditions: List<Condition>) {
        /** The variables its derivatives are taken in, in the order x, y, t. */
        val variables: List<String> = VARIABLES.filter { v ->
            equation.symbols().any { s -> derivativeOf(s)?.contains(v) == true }
        }
    }

    private val CONDITION = Regex("""^u(?:_\{?([xyt])\}?)?\((.+)\)$""")
    private val PARTIAL_CONDITION = Regex("""^\\frac\{\\partialu\}\{\\partial([xyt])\}""")

    /**
     * The lines as a partial differential equation and its conditions; null where none of them is one (no marker in a line that
     * is no condition), so that the other readings take it.
     */
    internal fun read(statements: List<Statement>): Reading? {
        val conditions = ArrayList<Condition>()
        val others = ArrayList<Statement>()
        for (s in statements) {
            val c = condition(s)
            if (c != null) conditions += c else others += s
        }
        val main = others.filter { s -> s.sides.filterNotNull().any { hasMarkers(it) } }
        if (main.isEmpty()) return null
        val s = main[0]
        val sides = s.sides.filterNotNull()
        // Derivatives in one variable only (u_x = 3, a component's name) are no partial differential equation.
        val names = sides.flatMap { it.symbols() }.toSet()
        if (VARIABLES.count { v -> names.any { derivativeOf(it)?.contains(v) == true } } < 2) return null
        if (main.size > 1 || others.size > 1) throw CalcException(Failure.UNREADABLE)
        val equation = when {
            sides.size == 1 && s.relations.isEmpty() -> Calculus.evaluate(sides[0])
            sides.size == 2 && s.relations == listOf(Rel.EQ) -> Calculus.evaluate(sides[0]) - Calculus.evaluate(sides[1])
            else -> throw CalcException(Failure.UNREADABLE)
        }
        if (equation is Expr.Mat) throw CalcException(Failure.UNREADABLE)
        return Reading(normalized(tidy(equation)), conditions).takeIf { it.variables.size >= 2 } ?: throw CalcException(Failure.UNREADABLE)
    }

    private fun condition(s: Statement): Condition? {
        if (s.relations != listOf(Rel.EQ) || s.texts.size < 2) return null
        val text = s.texts[0].replace("\\left", "").replace("\\right", "").filter { !it.isWhitespace() }
            // ∂u/∂x(0, t) = 0 written with ∂: u_x(0, t).
            .replace(PARTIAL_CONDITION, "u_$1")
        val m = CONDITION.matchEntire(text) ?: return null
        val parts = split(m.groupValues[2]) ?: return null
        if (parts.size != 2) return null
        val args = parts.map { arg(it) ?: return null }
        val value = s.sides.getOrNull(1)?.let { Calculus.evaluate(it) } ?: return null
        if (value is Expr.Mat || hasMarkers(value)) return null
        // As written (x(π − x), not −x² + πx): it is shown so.
        return Condition(m.groupValues[1], args, value)
    }

    /** The text split at its commas outside brackets; null where a bracket does not close. */
    private fun split(s: String): List<String>? {
        val out = ArrayList<String>()
        var depth = 0
        var start = 0
        for ((k, c) in s.withIndex()) {
            if (c == '(' || c == '[' || c == '{') depth++
            if (c == ')' || c == ']' || c == '}') depth--
            if (depth < 0) return null
            if (c == ',' && depth == 0) { out += s.substring(start, k); start = k + 1 }
        }
        if (depth != 0) return null
        out += s.substring(start)
        return out.map { it.trim() }
    }

    private fun arg(text: String): Arg? {
        if (text in VARIABLES) return Arg.Letter(text)
        return try {
            val tokens = MathCalc.TOKEN.findAll(text).map { it.value }.toList()
            val st = Parser(tokens).statements().singleOrNull() ?: return null
            val e = st.sides.filterNotNull().singleOrNull()?.takeIf { st.relations.isEmpty() } ?: return null
            val v = tidy(Calculus.evaluate(e))
            if (v is Expr.Mat || v.hasInfinity() || v.symbols().any { it in VARIABLES }) null else Arg.Point(v)
        } catch (x: CalcException) {
            null
        }
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Classification.

    enum class Type { ELLIPTIC, PARABOLIC, HYPERBOLIC }

    /**
     * a·u_xx + b·u_xy + c·u_yy + d·u_x + e·u_y + f·u = g in the variables [x] and [y] (in the order x, y, t: the heat equation's
     * are x and t).
     */
    class Coefficients(val x: String, val y: String, val a: Expr, val b: Expr, val c: Expr, val d: Expr, val e: Expr, val f: Expr,
        val g: Expr)

    /**
     * Where the equation is of [type]: where b² − 4ac [relation] 0. With one letter in the discriminant, as the values of that
     * [letter]: an interval [set] (> 0, < 0) or the [points] (= 0); with two, the relation itself is the region (a curve for = 0).
     */
    class Region(val type: Type, val relation: Rel, val letter: String?, val set: IntervalSet?, val points: List<Expr>)

    /**
     * The canonical form in new variables ξ = [xi] and η = [eta] (linear in x and y): [equation] = [rhs], the equation's left side
     * in the markers u_ξξ, u_ξη, u_ηη, u_ξ, u_η and u with its leading coefficient 1; u_ξη = … (hyperbolic), u_ηη = … (parabolic),
     * u_ξξ + u_ηη = … (elliptic).
     */
    class Canonical(val xi: Expr, val eta: Expr, val equation: Expr, val rhs: Expr,
        /** The roots λ of the characteristic equation a·λ² − b·λ + c = 0 (complex ones for an elliptic equation), none where a = 0. */
        val characteristic: List<Expr>,
        /** [equation]'s terms in the order they are written: u_ξξ, u_ξη, u_ηη, u_ξ, u_η, u (each with its coefficient). */
        val terms: List<Expr>)

    /**
     * The type of a second-order linear equation: its [coefficients], the [discriminant] b² − 4ac, the [type] where it is one type
     * everywhere (null where that depends on the point or on a letter: then [regions] says where each holds), and with constant
     * a, b, c its [canonical] form.
     */
    class Classification(val coefficients: Coefficients, val discriminant: Expr, val type: Type?, val regions: List<Region>,
        val canonical: Canonical?)

    /** The letters of the new variables and of their derivatives' markers. */
    val XI = Expr.Sym("\\xi")
    val ETA = Expr.Sym("\\eta")

    private fun canonicalMarker(v: String) = Expr.Sym("u_$v")
    private val UXIXI = canonicalMarker("\\xi\\xi")
    private val UXIETA = canonicalMarker("\\xi\\eta")
    private val UETAETA = canonicalMarker("\\eta\\eta")
    private val UXI = canonicalMarker("\\xi")
    private val UETA = canonicalMarker("\\eta")

    /** The type of the reading's equation; [Failure.UNSOLVED] where it is no linear equation of the second order in two variables. */
    internal fun classify(r: Reading): Classification {
        val vars = r.variables
        if (vars.size != 2) throw CalcException(Failure.UNSOLVED)
        val (x, y) = vars
        val eq = r.equation
        val second = listOf(marker(x + x), marker(x + y), marker(y + y))
        val first = listOf(marker(x), marker(y))
        val u = Expr.Sym(U)
        // Only derivatives of the first and second order in these two variables.
        if (eq.symbols().any { s -> derivativeOf(s) != null && marker(derivativeOf(s)!!) !in second + first }) throw CalcException(Failure.UNSOLVED)
        val unknowns = second + first + u
        val coefficients = unknowns.map { m -> tidy(Calculus.derivative(eq, m)) }
        // Linear: no coefficient holds u or a derivative of it.
        if (coefficients.any { k -> unknowns.any { k.has(it) } }) throw CalcException(Failure.UNSOLVED)
        val h = tidy(unknowns.fold(eq) { acc, m -> acc.substitute(m, Expr.ZERO) })
        val back = tidy(add(unknowns.mapIndexed { k, m -> mul(coefficients[k], m) } + h))
        if (!LinAlg.isNull(tidy(Algebra.expand(back - eq)))) throw CalcException(Failure.UNSOLVED)
        val (a, b, c, d, e) = coefficients
        val f = coefficients[5]
        if (listOf(a, b, c).all { LinAlg.isNull(it) }) throw CalcException(Failure.UNSOLVED)
        val k = Coefficients(x, y, a, b, c, d, e, f, tidy(neg(h)))
        val disc = tidy(Algebra.expand(pow(b, Expr.TWO) - mul(num(4), a, c)))
        val shown = Algebra.factor(disc)?.first?.takeIf { it.size() < disc.size() } ?: disc
        val letters = disc.symbols()
        if (letters.isEmpty()) {
            val type = typeOf(disc) ?: throw CalcException(Failure.UNSOLVED)
            val constant = listOf(a, b, c).all { it.symbols().isEmpty() }
            return Classification(k, disc, type, emptyList(), if (constant) canonical(k, type, disc) else null)
        }
        val regions = regions(disc, letters.toList())
        val type = when {
            regions.size == 1 && regions[0].relation != Rel.EQ && (regions[0].letter == null || regions[0].set?.all == true) -> regions[0].type
            regions.isEmpty() -> throw CalcException(Failure.UNSOLVED)
            else -> null
        }
        return Classification(k, shown, type, if (type != null) emptyList() else regions, null)
    }

    /** The type a constant discriminant gives; null where its sign cannot be told. */
    private fun typeOf(disc: Expr): Type? {
        if (LinAlg.isNull(disc)) return Type.PARABOLIC
        val v = Numeric.value(disc) ?: return null
        if (abs(v) < 1e-12) return null
        return if (v > 0) Type.HYPERBOLIC else Type.ELLIPTIC
    }

    private fun typeOf(rel: Rel) = when (rel) {
        Rel.GT -> Type.HYPERBOLIC
        Rel.EQ -> Type.PARABOLIC
        else -> Type.ELLIPTIC
    }

    /** Where b² − 4ac is > 0, = 0 and < 0: in one letter as its values (each checked at a point inside), else as the relations. */
    private fun regions(disc: Expr, letters: List<String>): List<Region> {
        val out = ArrayList<Region>()
        if (letters.size == 1) {
            val z = Expr.Sym(letters[0])
            for (rel in listOf(Rel.GT, Rel.LT)) {
                val set = Solve.inequality(disc, rel, Expr.ZERO, z) ?: throw CalcException(Failure.UNSOLVED)
                if (set.empty) continue
                if (!signInside(disc, z, set, if (rel == Rel.GT) 1 else -1)) throw CalcException(Failure.UNSOLVED)
                out += Region(typeOf(rel), rel, z.name, set, emptyList())
            }
            val zeros = Solve.equation(disc, Expr.ZERO, z)
            if (zeros.always || zeros.many || zeros.periodic.isNotEmpty()) throw CalcException(Failure.UNSOLVED)
            // Each zero put back: exactly 0 there.
            val points = zeros.values.filter { p -> LinAlg.isNull(tidy(disc.substitute(z, p))) }
            if (points.size != zeros.values.size) throw CalcException(Failure.UNSOLVED)
            if (points.isNotEmpty()) out += Region(Type.PARABOLIC, Rel.EQ, z.name, null, points)
            out.sortBy { listOf(Rel.GT, Rel.EQ, Rel.LT).indexOf(it.relation) }
            return out
        }
        // Several letters: each region as its relation, b² − 4ac > 0, = 0, < 0 (true as said, the curve = 0 between the others),
        // left out only where the discriminant's form rules it out: x² + y² is never < 0, 1 + x² + y² never ≤ 0.
        if (strictlyPositive(disc)) return listOf(Region(Type.HYPERBOLIC, Rel.GT, null, null, emptyList()))
        if (strictlyPositive(tidy(neg(disc)))) return listOf(Region(Type.ELLIPTIC, Rel.LT, null, null, emptyList()))
        if (!nonNegative(tidy(neg(disc)))) out += Region(Type.HYPERBOLIC, Rel.GT, null, null, emptyList())
        out += Region(Type.PARABOLIC, Rel.EQ, null, null, emptyList())
        if (!nonNegative(disc)) out += Region(Type.ELLIPTIC, Rel.LT, null, null, emptyList())
        return out
    }

    /** Positive for every value of its letters, as its form shows: a positive number plus what is never negative. */
    private fun strictlyPositive(e: Expr): Boolean {
        val terms = if (e is Expr.Add) e.terms else listOf(e)
        val constant = terms.filter { it.symbols().isEmpty() }
        val c = Numeric.value(add(constant)) ?: return false
        return c > 0 && terms.filter { it.symbols().isNotEmpty() }.all { nonNegative(it) }
    }

    /** At a point inside each interval of [set] (and beyond its ends) [disc] has the [sign]. */
    private fun signInside(disc: Expr, z: Expr.Sym, set: IntervalSet, sign: Int): Boolean = set.intervals.all { i ->
        val lo = i.lower?.let { Numeric.value(it) }
        val hi = i.upper?.let { Numeric.value(it) }
        val at = when {
            lo != null && hi != null -> (lo + hi) / 2
            lo != null -> lo + 1
            hi != null -> hi - 1
            else -> 0.37
        }
        val v = Numeric.eval(disc, mapOf(z.name to at))
        v.isFinite() && v * sign > 0
    }

    /**
     * The canonical form of an equation with constant a, b, c: new variables ξ, η along the characteristics (hyperbolic), along
     * the one characteristic and x (parabolic), or the real and imaginary parts of the complex characteristics (elliptic); checked
     * by the plane waves e^(px + qy), whose symbol a·p² + b·pq + c·q² + d·p + e·q + f the new form must carry over.
     */
    private fun canonical(k: Coefficients, type: Type, disc: Expr): Canonical? {
        val a = k.a
        val b = k.b
        val c = k.c
        val zero = { e: Expr -> LinAlg.isNull(e) }
        // ξ = α₁x + β₁y, η = α₂x + β₂y.
        val alpha1: Expr
        val beta1: Expr
        val alpha2: Expr
        val beta2: Expr
        var roots = emptyList<Expr>()
        when (type) {
            Type.HYPERBOLIC -> if (!zero(a)) {
                val root = tidy(sqrt(disc))
                val l1 = tidy((b + root) / mul(Expr.TWO, a))
                val l2 = tidy((b - root) / mul(Expr.TWO, a))
                roots = listOf(l1, l2)
                alpha1 = tidy(neg(l1)); beta1 = Expr.ONE; alpha2 = tidy(neg(l2)); beta2 = Expr.ONE
            } else if (zero(c)) {
                alpha1 = Expr.ONE; beta1 = Expr.ZERO; alpha2 = Expr.ZERO; beta2 = Expr.ONE
            } else {
                alpha1 = Expr.ONE; beta1 = Expr.ZERO; alpha2 = tidy(neg(c / b)); beta2 = Expr.ONE
            }
            Type.PARABOLIC -> if (!zero(a)) {
                val l = tidy(b / mul(Expr.TWO, a))
                roots = listOf(l)
                alpha1 = tidy(neg(l)); beta1 = Expr.ONE; alpha2 = Expr.ONE; beta2 = Expr.ZERO
            } else {
                alpha1 = Expr.ONE; beta1 = Expr.ZERO; alpha2 = Expr.ZERO; beta2 = Expr.ONE
            }
            Type.ELLIPTIC -> {
                if (zero(a)) return null
                val root = tidy(sqrt(neg(disc)))
                val re = tidy(b / mul(Expr.TWO, a))
                val im = tidy(root / mul(Expr.TWO, fn(Func.ABS, a)))
                roots = listOf(tidy(re + mul(Expr.I, tidy(root / mul(Expr.TWO, a)))), tidy(re - mul(Expr.I, tidy(root / mul(Expr.TWO, a)))))
                // ξ along x, η along the characteristics' real direction: u_xx + u_yy keeps ξ = x, η = y.
                alpha1 = im; beta1 = Expr.ZERO; alpha2 = tidy(neg(re)); beta2 = Expr.ONE
            }
        }
        val jacobian = tidy(alpha1 * beta2 - alpha2 * beta1)
        if (zero(jacobian)) return null
        // The chain rule for a linear change of variables: u_x = α₁u_ξ + α₂u_η, u_y = β₁u_ξ + β₂u_η.
        val bigA = tidy(a * alpha1 * alpha1 + b * alpha1 * beta1 + c * beta1 * beta1)
        val bigB = tidy(mul(Expr.TWO, a, alpha1, alpha2) + b * (alpha1 * beta2 + alpha2 * beta1) + mul(Expr.TWO, c, beta1, beta2))
        val bigC = tidy(a * alpha2 * alpha2 + b * alpha2 * beta2 + c * beta2 * beta2)
        val bigD = tidy(k.d * alpha1 + k.e * beta1)
        val bigE = tidy(k.d * alpha2 + k.e * beta2)
        // x and y in ξ and η, for the coefficients that hold them.
        val xs = Expr.Sym(k.x)
        val ys = Expr.Sym(k.y)
        val xOf = tidy((beta2 * XI - beta1 * ETA) / jacobian)
        val yOf = tidy((alpha1 * ETA - alpha2 * XI) / jacobian)
        fun inNew(e: Expr) = tidy(Algebra.expand(e.substitute(xs, xOf).substitute(ys, yOf)))
        val lead = when (type) {
            Type.HYPERBOLIC -> bigB
            Type.PARABOLIC -> bigC
            Type.ELLIPTIC -> bigA
        }
        if (zero(lead)) return null
        // The form's own shape: u_ξη alone (hyperbolic), u_ηη alone (parabolic), u_ξξ + u_ηη (elliptic).
        val shape = when (type) {
            Type.HYPERBOLIC -> zero(bigA) && zero(bigC)
            Type.PARABOLIC -> zero(bigA) && zero(bigB)
            Type.ELLIPTIC -> zero(bigB) && zero(tidy(bigA - bigC))
        }
        if (!shape) return null
        val u = Expr.Sym(U)
        val ordered = listOf(bigA to UXIXI, bigB to UXIETA, bigC to UETAETA, inNew(bigD) to UXI, inNew(bigE) to UETA, inNew(k.f) to u)
            .map { (co, m) -> tidy(mul(tidy(co / lead), m)) }.filter { !isZero(it) }
        val left = tidy(add(ordered))
        val rhs = tidy(inNew(k.g) / lead)
        // Checked: the symbol of the plane wave e^(px + qy) = e^(Pξ + Qη), with p = α₁P + α₂Q and q = β₁P + β₂Q.
        val p = Expr.Sym("P_1")
        val q = Expr.Sym("Q_1")
        val pp = tidy(alpha1 * p + alpha2 * q)
        val qq = tidy(beta1 * p + beta2 * q)
        val original = add(a * pp * pp, b * pp * qq, c * qq * qq, k.d * pp, k.e * qq, k.f)
        val newSymbol = tidy(lead * add(left.substitute(UXIXI, p * p).substitute(UXIETA, p * q).substitute(UETAETA, q * q)
            .substitute(UXI, p).substitute(UETA, q).substitute(u, Expr.ONE)))
        val back = newSymbol.substitute(XI, tidy(alpha1 * xs + beta1 * ys)).substitute(ETA, tidy(alpha2 * xs + beta2 * ys))
        if (!LinAlg.isNull(tidy(Algebra.expand(back - original)))) return null
        if (!LinAlg.isNull(tidy(Algebra.expand(tidy(lead * rhs).substitute(XI, tidy(alpha1 * xs + beta1 * ys))
                .substitute(ETA, tidy(alpha2 * xs + beta2 * ys)) - k.g)))) return null
        // ξ and η as a learner writes them: y − (2 + √3)x, its coefficient of x together.
        fun linear(alpha: Expr, beta: Expr) = add(mul(alpha, xs), mul(beta, ys))
        val xi = linear(alpha1, beta1)
        val eta = linear(alpha2, beta2)
        if (!LinAlg.isNull(tidy(Algebra.expand(xi - alpha1 * xs - beta1 * ys))) || !LinAlg.isNull(tidy(Algebra.expand(eta - alpha2 * xs - beta2 * ys)))) return null
        return Canonical(xi, eta, left, rhs, roots, ordered)
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Separation of variables.

    enum class Kind { HEAT, WAVE, LAPLACE }

    /** An end of the interval: u = 0 there (fixed) or u_x = 0 (insulated, free). */
    enum class End { DIRICHLET, NEUMANN }

    /**
     * One family of coefficients: [name]ₙ = [weight] · [integral] (the integral as written, an [Expr.Op]: 2/L · ∫₀ᴸ f(x)·Xₙ(x) dx)
     * = [formula] for every n ≥ 1 but the [exceptions], where the formula has no value and the integral was worked out on its own;
     * [zero] the coefficient of n = 0 where there is one (insulated ends: the constant term [zero]/2). In the n-th term it
     * multiplies [factor] (e^(−kλₙt)·Xₙ(x), cos(cμₙt)·Xₙ(x), sinh(μₙy)/sinh(μₙb)·Xₙ(x)).
     */
    class Coefficient(val name: String, val weight: Expr, val integral: Expr, val formula: Expr, val exceptions: List<Pair<Int, Expr>>,
        val zero: Expr?, val factor: Expr)

    /**
     * A condition met only in the limit, checked by the partial sums: [derivative] of u (null: u itself) with [variable] = [point]
     * is [value]; at the [points] of the other variable, the sum of the first [terms] terms differs from it by less than
     * [digit]·10^[exponent]. [exact] where the series is finite and its sum is the value exactly (then no bound). Where the
     * problem has letters ([letters]: L, c, an amplitude), the sums were taken with each of them equal to each of the [samples].
     */
    class Check(val derivative: String?, val variable: String, val point: Expr, val value: Expr, val terms: Int, val points: List<Expr>,
        val digit: Int, val exponent: Int, val exact: Boolean, val letters: List<String> = emptyList(), val samples: List<Expr> = emptyList())

    /**
     * X″ + λX = 0 in [variable] on (0, [length]) with its two ends: the eigenvalues [lambda] = [frequency]² (in n), the
     * eigenfunctions [function] (in n and the variable), for n ≥ 1 ([constant]: and the constant X₀ = 1 for λ₀ = 0, insulated at
     * both ends).
     */
    class Eigen(val variable: String, val length: Expr, val left: End, val right: End, val lambda: Expr, val function: Expr, val constant: Boolean,
        val frequency: Expr)

    /**
     * A problem solved by separation of variables: u([x], [t]) (Laplace: u(x, y), [t] is y) on 0 < x < [length] (and
     * 0 < y < [height]); [constant] is k (heat) or c (wave), assumed positive where it is a letter ([positive]). [eigen] the
     * eigenvalue problems the boundary conditions make (Laplace: one for each side with values). u = [series], written with the
     * coefficients' names (b_n, a_0 …), each worked out in [coefficients]; [terms] the first terms that are not 0, [sum] u itself
     * where only finitely many are; [checks] the conditions met in the limit.
     */
    class Separated(val kind: Kind, val x: String, val t: String, val length: Expr, val height: Expr?, val constant: Expr?,
        val positive: List<Expr>, val eigen: List<Eigen>, val series: Expr, val coefficients: List<Coefficient>, val terms: List<Expr>,
        val sum: Expr?, val checks: List<Check>,
        /** The conditions as the problem states them: the ends' (or sides') values, then the initial ones. */
        val given: List<Given>,
        /** The steady state v(x) where an end is fixed at another value than 0: u = v + the series, whose ends are 0. */
        val steady: Expr? = null)

    /** The series' parts: a coefficient, the factor it multiplies in the n-th term, how the term meets each condition. */
    private class Part(val coefficient: Coefficient, val factor: Expr, val zeroFactor: Expr?)

    /** The problem the reading states, solved; [Failure.CONDITIONS] where its conditions do not fix one solution. */
    internal fun separate(r: Reading): Separated {
        val eq = r.equation
        val markers = eq.symbols().filter { derivativeOf(it) != null }.toSet()
        val u = Expr.Sym(U)
        fun coefficient(m: Expr.Sym) = tidy(Calculus.derivative(eq, m))
        // The equation exactly α·first + β·second, nothing else.
        fun pair(m1: Expr.Sym, m2: Expr.Sym): Pair<Expr, Expr>? {
            if (markers != setOf(m1.name, m2.name) || eq.has(u)) return null
            val alpha = coefficient(m1)
            val beta = coefficient(m2)
            if (alpha.symbols().any { it in VARIABLES || derivativeOf(it) != null } || beta.symbols().any { it in VARIABLES || derivativeOf(it) != null }) return null
            if (!LinAlg.isNull(tidy(Algebra.expand(eq - alpha * m1 - beta * m2)))) return null
            return alpha to beta
        }
        pair(marker("t"), marker("xx"))?.let { (alpha, beta) ->
            val k = tidy(neg(beta) / alpha)
            return evolution(Kind.HEAT, k, r.conditions, positive(k))
        }
        pair(marker("tt"), marker("xx"))?.let { (alpha, beta) ->
            val c2 = tidy(neg(beta) / alpha)
            val c = speed(c2)
            return evolution(Kind.WAVE, c, r.conditions, positive(c))
        }
        pair(marker("xx"), marker("yy"))?.let { (alpha, beta) ->
            if (!LinAlg.isNull(tidy(alpha - beta))) throw CalcException(Failure.UNSOLVED)
            return laplace(r.conditions)
        }
        throw CalcException(Failure.UNSOLVED)
    }

    /** The letters a constant is assumed positive with, or its sign checked where it is a number. */
    private fun positive(k: Expr): List<Expr> {
        if (k.symbols().isEmpty()) {
            val v = Numeric.value(k) ?: throw CalcException(Failure.UNSOLVED)
            if (v <= 0) throw CalcException(Failure.UNSOLVED)
            return emptyList()
        }
        if (k.symbols().any { it in VARIABLES }) throw CalcException(Failure.UNSOLVED)
        return listOf(k)
    }

    /** A length: a positive number, or a letter (L, a, b) assumed positive, returned. */
    private fun length(l: Expr): List<Expr> {
        if (l.symbols().isEmpty()) {
            if ((Numeric.value(l) ?: 0.0) <= 0) throw CalcException(Failure.UNSOLVED)
            return emptyList()
        }
        if (l !is Expr.Sym || l.name in VARIABLES) throw CalcException(Failure.UNSOLVED)
        return listOf(l)
    }

    /** c from c²: c where it is written c², else the square root. */
    private fun speed(c2: Expr): Expr {
        if (c2 is Expr.Pow && integer(c2.exp) == BigInteger.valueOf(2) && c2.base.symbols().isNotEmpty()) return c2.base
        return tidy(sqrt(c2))
    }

    /** The ends' kinds, the length, and the initial values of the heat or the wave equation from its conditions. */
    private fun evolution(kind: Kind, constant: Expr, conditions: List<Condition>, positive: List<Expr>): Separated {
        var left: End? = null
        var right: End? = null
        var length: Expr? = null
        var f: Expr? = null
        var g: Expr? = null
        var leftValue: Expr = Expr.ZERO
        var rightValue: Expr = Expr.ZERO
        val x = Expr.Sym("x")
        val t = Expr.Sym("t")
        for (c in conditions) {
            val (a1, a2) = c.args
            when {
                // A boundary condition: u(a, t) = T, a fixed end (T a constant), or u_x(a, t) = 0, an insulated one.
                a1 is Arg.Point && a2 == Arg.Letter("t") -> {
                    if (c.derivative != "" && c.derivative != "x") throw CalcException(Failure.CONDITIONS)
                    if (c.value.symbols().any { it in VARIABLES }) throw CalcException(Failure.UNSOLVED)
                    if (c.derivative == "x" && !LinAlg.isNull(c.value)) throw CalcException(Failure.UNSOLVED)
                    val end = if (c.derivative == "x") End.NEUMANN else End.DIRICHLET
                    if (LinAlg.isNull(a1.value)) {
                        if (left != null) throw CalcException(Failure.CONDITIONS)
                        left = end
                        leftValue = c.value
                    } else {
                        if (right != null) throw CalcException(Failure.CONDITIONS)
                        right = end
                        rightValue = c.value
                        length = a1.value
                    }
                }
                // An initial condition: u(x, 0) = f(x), u_t(x, 0) = g(x).
                a1 == Arg.Letter("x") && a2 is Arg.Point -> {
                    if (!LinAlg.isNull(a2.value)) throw CalcException(Failure.UNSOLVED)
                    if (c.value.symbols().any { it in VARIABLES && it != "x" }) throw CalcException(Failure.UNSOLVED)
                    when {
                        c.derivative == "" -> { if (f != null) throw CalcException(Failure.CONDITIONS); f = c.value }
                        c.derivative == "t" && kind == Kind.WAVE -> { if (g != null) throw CalcException(Failure.CONDITIONS); g = c.value }
                        else -> throw CalcException(Failure.CONDITIONS)
                    }
                }
                else -> throw CalcException(Failure.CONDITIONS)
            }
        }
        if (left == null || right == null || length == null || f == null || (kind == Kind.WAVE && g == null)) throw CalcException(Failure.CONDITIONS)
        val lengths = length(length)
        // Fixed ends at other values than 0: u = v(x) + w(x, t), the steady state v taking the ends' values (a line between them,
        // or the one value where the other end is insulated), w with ends at 0 and initial values f − v.
        val steady = when {
            LinAlg.isNull(leftValue) && LinAlg.isNull(rightValue) -> null
            left == End.DIRICHLET && right == End.DIRICHLET -> add(leftValue, mul(tidy(rightValue - leftValue), x, pow(length, Expr.MINUS_ONE)))
            left == End.DIRICHLET -> leftValue
            else -> rightValue
        }
        val initial = f
        if (steady != null) f = add(f, neg(steady))
        val eigen = eigen("x", length, left, right)
        val mu = eigen.frequency
        val parts = ArrayList<Part>()
        val weight = tidy(Expr.TWO / length)
        if (kind == Kind.HEAT) {
            // Written as a textbook writes it, e^(−k((2n − 1)π/(2L))²t)·sin((2n − 1)πx/(2L)), not multiplied out.
            val decay = exp(neg(mul(constant, eigen.lambda, t)))
            val name = if (eigen.constant) "a" else "b"
            val factor = mul(decay, eigen.function)
            val co = coefficient(name, weight, f, eigen.function, x, length, if (eigen.constant) weight else null, factor)
            parts += Part(co, factor, if (eigen.constant) Expr.HALF else null)
        } else {
            val omega = mul(constant, mu)
            val cosine = mul(fn(Func.COS, mul(omega, t)), eigen.function)
            val a = coefficient("a", weight, f, eigen.function, x, length, if (eigen.constant) weight else null, cosine)
            parts += Part(a, cosine, if (eigen.constant) Expr.HALF else null)
            val sine = mul(fn(Func.SIN, mul(omega, t)), eigen.function)
            val b = coefficient("b", tidy(weight / omega), g!!, eigen.function, x, length, if (eigen.constant) weight else null, sine)
            parts += Part(b, sine, if (eigen.constant) tidy(mul(Expr.HALF, t)) else null)
        }
        val equation = if (kind == Kind.HEAT) Equation.Heat(constant) else Equation.Wave(constant)
        val checked = listOfNotNull(
            Given("x", Expr.ZERO, leftValue, left == End.NEUMANN),
            Given("x", length, rightValue, right == End.NEUMANN),
            Given("t", Expr.ZERO, initial, false),
            if (kind == Kind.WAVE) Given("t", Expr.ZERO, g!!, true) else null,
        )
        return assemble(kind, "x", "t", length, null, constant, positive + lengths, listOf(eigen), parts, equation, checked, steady)
    }

    /** X″ + λX = 0 on (0, L) with these ends: λₙ and Xₙ for n ≥ 1, and the constant for λ₀ = 0 where both ends are insulated. */
    private fun eigen(variable: String, length: Expr, left: End, right: End): Eigen {
        val v = Expr.Sym(variable)
        val half = (left == End.DIRICHLET) != (right == End.DIRICHLET)
        // nπ/L, or (2n − 1)π/(2L) where one end is fixed and the other free.
        val mu = if (half) mul(add(mul(Expr.TWO, N), Expr.MINUS_ONE), Expr.PI, pow(mul(Expr.TWO, length), Expr.MINUS_ONE))
            else mul(N, Expr.PI, pow(length, Expr.MINUS_ONE))
        // As a textbook writes them: λₙ = ((2n − 1)π/(2L))², sin((2n − 1)πx/(2L)), not multiplied out.
        val function = fn(if (left == End.DIRICHLET) Func.SIN else Func.COS, mul(mu, v))
        return Eigen(variable, length, left, right, pow(mu, Expr.TWO), function, left == End.NEUMANN && right == End.NEUMANN, mu)
    }

    /** Laplace's equation on the rectangle (0, a) × (0, b): its four sides, the series of each side with values, superposed. */
    private fun laplace(conditions: List<Condition>): Separated {
        var bottom: Expr? = null
        var top: Expr? = null
        var leftSide: Expr? = null
        var rightSide: Expr? = null
        var width: Expr? = null
        var height: Expr? = null
        for (c in conditions) {
            if (c.derivative != "") throw CalcException(Failure.UNSOLVED)
            val (a1, a2) = c.args
            when {
                a1 == Arg.Letter("x") && a2 is Arg.Point -> {
                    if (c.value.symbols().any { it in VARIABLES && it != "x" }) throw CalcException(Failure.UNSOLVED)
                    if (LinAlg.isNull(a2.value)) { if (bottom != null) throw CalcException(Failure.CONDITIONS); bottom = c.value }
                    else { if (top != null) throw CalcException(Failure.CONDITIONS); top = c.value; height = a2.value }
                }
                a1 is Arg.Point && a2 == Arg.Letter("y") -> {
                    if (c.value.symbols().any { it in VARIABLES && it != "y" }) throw CalcException(Failure.UNSOLVED)
                    if (LinAlg.isNull(a1.value)) { if (leftSide != null) throw CalcException(Failure.CONDITIONS); leftSide = c.value }
                    else { if (rightSide != null) throw CalcException(Failure.CONDITIONS); rightSide = c.value; width = a1.value }
                }
                else -> throw CalcException(Failure.CONDITIONS)
            }
        }
        if (bottom == null || top == null || leftSide == null || rightSide == null || width == null || height == null) throw CalcException(Failure.CONDITIONS)
        val lengths = (length(width) + length(height)).distinct()
        val x = Expr.Sym("x")
        val y = Expr.Sym("y")
        val parts = ArrayList<Part>()
        val eigens = ArrayList<Eigen>()
        // Each side with values: the sines along it, and sinh across, 0 on the opposite side and 1 on its own; its coefficients
        // named by the side (a bottom, b top, c left, d right).
        for ((side, value) in listOf(bottom, top, leftSide, rightSide).withIndex()) {
            if (LinAlg.isNull(value)) continue
            val along = if (side < 2) "x" else "y"
            val s = if (along == "x") x else y
            val other = if (along == "x") y else x
            val l = if (along == "x") width else height
            val across = if (along == "x") height else width
            val e = eigen(along, l, End.DIRICHLET, End.DIRICHLET)
            val mu = e.frequency
            // Bottom and left: 0 on the far side (y = b, x = a); top and right: 0 at y = 0, x = 0.
            val distance = if (side == 0 || side == 2) tidy(across - other) else other
            val profile = tidy(fn(Func.SINH, tidy(mu * distance)) / fn(Func.SINH, tidy(mu * across)))
            val factor = tidy(mul(profile, e.function))
            val co = coefficient(listOf("a", "b", "c", "d")[side], tidy(Expr.TWO / l), value, e.function, s, l, null, factor)
            parts += Part(co, factor, null)
            eigens += e
        }
        val checked = listOf(
            Given("y", Expr.ZERO, bottom, false), Given("y", height, top, false),
            Given("x", Expr.ZERO, leftSide, false), Given("x", width, rightSide, false),
        )
        return assemble(Kind.LAPLACE, "x", "y", width, height, null, lengths, eigens, parts, Equation.Laplace, checked, null)
    }

    /** The equation each term must solve. */
    private sealed class Equation {
        abstract fun residual(term: Expr): Expr

        class Heat(val k: Expr) : Equation() {
            override fun residual(term: Expr) = Calculus.derivative(term, Expr.Sym("t")) - k * Calculus.derivative(term, Expr.Sym("x"), 2)
        }

        class Wave(val c: Expr) : Equation() {
            override fun residual(term: Expr) = Calculus.derivative(term, Expr.Sym("t"), 2) - c * c * Calculus.derivative(term, Expr.Sym("x"), 2)
        }

        object Laplace : Equation() {
            override fun residual(term: Expr) = Calculus.derivative(term, Expr.Sym("x"), 2) + Calculus.derivative(term, Expr.Sym("y"), 2)
        }
    }

    /**
     * A condition as the problem states it: with [variable] = [point], u (or where [derivative], its derivative in that same
     * variable: u_x(0, t) at an insulated end, u_t(x, 0) the initial velocity) is [value].
     */
    class Given(val variable: String, val point: Expr, val value: Expr, val derivative: Boolean)

    /** n-th coefficient: weight·∫₀ᴸ f·Xₙ, exact; where its formula has no value at a whole n, that n on its own. */
    private fun coefficient(name: String, weight: Expr, f: Expr, function: Expr, v: Expr.Sym, length: Expr, zeroWeight: Expr?, factor: Expr): Coefficient {
        val integrand = tidy(mul(f, function))
        val integral = op(OpKind.INTEGRAL, mul(f, function), v, Expr.ZERO, length)
        val raw = Calculus.definite(integrand, v, Expr.ZERO, length).exact ?: throw CalcException(Failure.NO_ANTIDERIVATIVE)
        val whole = tidy(whole(tidy(mul(weight, raw))))
        val formula = nicest(whole)
        // The whole n ≥ 1 where a denominator of the integral (or of its factor) is 0.
        val poles = java.util.TreeSet<Int>()
        for (base in denominators(tidy(mul(weight, raw)))) {
            val roots = try { Solve.equation(base, Expr.ZERO, N) } catch (x: CalcException) {
                if (x.failure == Failure.TOO_LONG) throw x
                throw CalcException(Failure.UNSOLVED)
            }
            // A pole that depends on a letter (sin x on (0, L): n = L/π) would leave the formula without a value for some L.
            if ((roots.values + roots.complex).any { it.symbols().isNotEmpty() }) throw CalcException(Failure.UNSOLVED)
            for (root in roots.values) {
                val m = integer(tidy(root)) ?: continue
                if (m.signum() > 0 && m.bitLength() < 31) poles += m.toInt()
            }
        }
        val exceptions = poles.map { m ->
            val at = num(m.toLong())
            val value = Calculus.definite(tidy(integrand.substitute(N, at)), v, Expr.ZERO, length).exact ?: throw CalcException(Failure.NO_ANTIDERIVATIVE)
            m to tidy(mul(weight.substitute(N, at), value))
        }
        val zero = zeroWeight?.let { w ->
            val value = Calculus.definite(tidy(f), v, Expr.ZERO, length).exact ?: throw CalcException(Failure.NO_ANTIDERIVATIVE)
            tidy(mul(w, value))
        }
        val co = Coefficient(name, weight, integral, formula, exceptions, zero, factor)
        // Checked against quadrature at n = 1 … 6, at every exception and at n = 0; a letter of the equation (c of the wave
        // equation, in the factor) at two values.
        val parameters = (weight.symbols() + integrand.symbols() + length.symbols() + co.formula.symbols() +
            co.exceptions.flatMap { it.second.symbols() } + zero?.symbols().orEmpty()) - N.name - v.name
        for (sample in SAMPLES) {
            val env = parameters.associateWith { sample }
            val lv = Numeric.eval(length, env).takeIf { it.isFinite() && it > 0 } ?: throw CalcException(Failure.UNSOLVED)
            for (n in (1..6) + poles) {
                Budget.check()
                val expected = valueAt(co, n)?.let { Numeric.eval(it, env) } ?: throw CalcException(Failure.UNSOLVED)
                val w = Numeric.eval(weight, env + (N.name to n.toDouble()))
                val q = Quadrature.integrate({ s -> Numeric.eval(integrand, env + mapOf(v.name to s, N.name to n.toDouble())) }, 0.0, lv)
                    ?: throw CalcException(Failure.UNSOLVED)
                if (!agrees(expected, w * q)) throw CalcException(Failure.UNSOLVED)
            }
            if (zero != null) {
                val q = Quadrature.integrate({ s -> Numeric.eval(f, env + (v.name to s)) }, 0.0, lv) ?: throw CalcException(Failure.UNSOLVED)
                if (!agrees(Numeric.eval(zero, env), Numeric.eval(zeroWeight, env) * q)) throw CalcException(Failure.UNSOLVED)
            }
            if (parameters.isEmpty()) break
        }
        return co
    }

    /** The values the letters of a problem (k, c, L, an amplitude) take in its numeric checks: two, so no identity holds by chance. */
    private val SAMPLES = listOf(1.37, 0.61)
    private val SAMPLE_VALUES = listOf(num(Rational.of(137) / Rational.of(100)), num(Rational.of(61) / Rational.of(100)))

    private fun agrees(a: Double, b: Double) = a.isFinite() && b.isFinite() && abs(a - b) <= 1e-8 * max(1.0, max(abs(a), abs(b)))

    /** The coefficient of the n-th term, exact; null where it has no value. */
    private fun valueAt(co: Coefficient, n: Int): Expr? {
        co.exceptions.firstOrNull { it.first == n }?.let { return it.second }
        return try {
            tidy(whole(tidy(co.formula.substitute(N, num(n.toLong()))))).takeIf { c -> !c.hasInfinity() && Numeric.eval(c, c.symbols().associateWith { 1.37 }).isFinite() }
        } catch (x: CalcException) {
            if (x.failure == Failure.TOO_LONG) throw x
            null
        }
    }

    /** The bases of the negative powers in [e] that hold n: where its formula may have no value. */
    private fun denominators(e: Expr): Set<Expr> {
        val out = LinkedHashSet<Expr>()
        fun walk(x: Expr) {
            when (x) {
                is Expr.Pow -> {
                    if (x.base.has(N) && (rational(x.exp)?.signum ?: 0) < 0) out += x.base
                    walk(x.base); walk(x.exp)
                }
                is Expr.Add -> x.terms.forEach(::walk)
                is Expr.Mul -> x.factors.forEach(::walk)
                is Expr.Fn -> x.args.forEach(::walk)
                else -> {}
            }
        }
        walk(e)
        return out
    }

    /** The shortest of the coefficient's simplified forms that agrees with it at whole n. */
    private fun nicest(e: Expr): Expr {
        val candidates = ArrayList<Expr>()
        candidates += e
        try { candidates += tidy(whole(Algebra.simplify(e))) } catch (x: CalcException) { if (x.failure == Failure.TOO_LONG) throw x }
        try { Algebra.factor(e)?.first?.let { candidates += tidy(whole(it)) } } catch (x: CalcException) { if (x.failure == Failure.TOO_LONG) throw x }
        val ok = candidates.filter { c -> (1..8).all { n -> sameAt(c, e, n) } }
        return ok.minByOrNull { it.size() } ?: e
    }

    private fun sameAt(a: Expr, b: Expr, n: Int): Boolean {
        val va = Numeric.eval(a, mapOf(N.name to n.toDouble()))
        val vb = Numeric.eval(b, mapOf(N.name to n.toDouble()))
        if (!va.isFinite() || !vb.isFinite()) return va.isFinite() == vb.isFinite()
        return abs(va - vb) <= 1e-12 * max(1.0, abs(vb))
    }

    /**
     * sin and cos of (αn + β)π, and (−1) to αn + β, for whole α (and β), as their values for every whole n: sin nπ = 0,
     * cos nπ = (−1)ⁿ, sin((2n − 1)π/2) = (−1)ⁿ⁺¹.
     */
    internal fun whole(e: Expr): Expr {
        val inner = e.mapChildren { whole(it) }
        if (inner is Expr.Fn && (inner.f == Func.SIN || inner.f == Func.COS) && inner.args[0].has(N)) {
            val c = Solve.coefficientsIn(Algebra.expand(tidy(inner.args[0] / Expr.PI)), N)
            if (c != null && c.size == 2) {
                val alpha = rational(tidy(c[1]))
                val beta = tidy(c[0])
                if (alpha != null && alpha.isInteger && beta.symbols().isEmpty()) {
                    val sign = if (alpha.num.testBit(0)) pow(Expr.MINUS_ONE, N) else Expr.ONE
                    return tidy(mul(sign, fn(inner.f, tidy(mul(beta, Expr.PI)))))
                }
            }
        }
        if (inner is Expr.Pow && inner.base == Expr.MINUS_ONE && inner.exp.has(N)) {
            val c = Solve.coefficientsIn(Algebra.expand(inner.exp), N)
            if (c != null && c.size == 2) {
                val alpha = rational(tidy(c[1]))
                val beta = rational(tidy(c[0]))
                if (alpha != null && alpha.isInteger && beta != null && beta.isInteger) {
                    val s = if (alpha.num.testBit(0)) pow(Expr.MINUS_ONE, N) else Expr.ONE
                    return if (beta.num.testBit(0)) tidy(neg(s)) else s
                }
            }
        }
        return inner
    }

    /** The number of terms the partial sums go up to, at most. */
    private const val MOST_TERMS = 6400

    /** How many of the first terms that are not 0 are written out. */
    private const val SHOWN_TERMS = 3

    /**
     * The series from its parts: each term checked to solve the equation and the homogeneous conditions exactly, the others by
     * the partial sums (or exactly where the series is finite).
     */
    private fun assemble(kind: Kind, x: String, t: String, length: Expr, height: Expr?, constant: Expr?, positive: List<Expr>,
        eigen: List<Eigen>, parts0: List<Part>, equation: Equation, stated: List<Given>, steady: Expr?): Separated {
        // A family that is 0 for every n is left out (u_t(x, 0) = 0 has no sine terms).
        val parts = parts0.filter { p ->
            !(isZero(p.coefficient.formula) && p.coefficient.exceptions.all { isZero(it.second) } && (p.coefficient.zero?.let { isZero(it) } ?: true))
        }
        // Every term solves the equation, for every n.
        for (p in parts) {
            if (!LinAlg.isNull(tidy(Algebra.expand(tidy(whole(tidy(equation.residual(p.factor)))))))) {
                if (!Verify.same(tidy(equation.residual(p.factor)), Expr.ZERO)) throw CalcException(Failure.UNSOLVED)
            }
            p.zeroFactor?.let { z -> if (!LinAlg.isNull(tidy(equation.residual(z)))) throw CalcException(Failure.UNSOLVED) }
        }
        if (steady != null && !LinAlg.isNull(tidy(equation.residual(steady)))) throw CalcException(Failure.UNSOLVED)
        // The first terms, n by n.
        val terms = ArrayList<Expr>()
        if (steady != null) terms += steady
        val zeroTerm = add(parts.mapNotNull { p -> p.coefficient.zero?.let { z -> mul(z, p.zeroFactor!!) } })
        // What the series has besides its terms in n: the steady state and the constant terms (a₀/2).
        val base = add(listOfNotNull(steady) + parts.mapNotNull { p -> p.coefficient.zero?.let { z -> mul(z, p.zeroFactor!!) } })
        if (!isZero(tidy(zeroTerm))) terms += zeroTerm
        val finite = parts.all { isZero(it.coefficient.formula) }
        var n = 1
        val before = terms.size
        while (terms.size < (if (finite) Int.MAX_VALUE else SHOWN_TERMS + before) && n <= 60) {
            Budget.check()
            // Each term its coefficient times its factor, not multiplied out: (1 + e^π)/π · e^(−t) sin x.
            val term = add(parts.map { p ->
                val c = valueAt(p.coefficient, n) ?: throw CalcException(Failure.UNSOLVED)
                mul(c, tidy(whole(tidy(p.factor.substitute(N, num(n.toLong()))))))
            })
            if (!isZero(tidy(term))) terms += term
            n++
            if (finite && n > (parts.flatMap { it.coefficient.exceptions.map { e -> e.first } }.maxOrNull() ?: 0)) break
        }
        val sum = if (finite) add(terms) else null
        // The conditions.
        val checks = ArrayList<Check>()
        for (s in stated) {
            val v = Expr.Sym(s.variable)
            fun applied(e: Expr): Expr {
                val d = if (s.derivative) Calculus.derivative(e, v) else e
                return tidy(whole(tidy(d.substitute(v, s.point))))
            }
            // A condition no term in n takes part in (the ends): met exactly, by the steady state and the constant terms alone.
            if (parts.all { p -> LinAlg.isNull(tidy(Algebra.expand(applied(p.factor)))) }) {
                val left = applied(base)
                if (!LinAlg.isNull(tidy(Algebra.expand(left - s.value))) && !Verify.same(left, s.value)) throw CalcException(Failure.UNSOLVED)
                continue
            }
            val other = if (s.variable == x) t else x
            val derivative = if (s.derivative) s.variable else null
            if (sum != null) {
                if (!Verify.same(applied(sum), s.value) && !LinAlg.isNull(tidy(Algebra.expand(applied(sum) - s.value)))) throw CalcException(Failure.UNSOLVED)
                checks += Check(derivative, s.variable, s.point, s.value, terms.size, emptyList(), 0, 0, true)
                continue
            }
            checks += partialSums(parts, applied(base), s, other, ::applied, height, length)
        }
        val u = series(parts, kind).let { if (steady != null) add(steady, it) else it }
        return Separated(kind, x, t, length, height, constant, positive, eigen, u, parts.map { it.coefficient }, terms, sum, checks, stated, steady)
    }

    /** u = Σ (coefficient · factor), the coefficients by their names: b_n, and a_0/2 where there is a constant term. */
    private fun series(parts: List<Part>, kind: Kind): Expr {
        if (parts.isEmpty()) return Expr.ZERO
        val zeroPart = parts.filter { it.coefficient.zero != null }.map { p -> mul(Expr.Sym(p.coefficient.name + "_0"), p.zeroFactor!!) }
        val sums = if (kind == Kind.LAPLACE) parts.map { p -> op(OpKind.SUM, mul(Expr.Sym(p.coefficient.name + "_n"), p.factor), N, Expr.ONE, Expr.INF) }
            else listOf(op(OpKind.SUM, add(parts.map { p -> mul(Expr.Sym(p.coefficient.name + "_n"), p.factor) }), N, Expr.ONE, Expr.INF))
        return add(zeroPart + sums)
    }

    /**
     * The condition met in the limit, by the partial sums at three points of the other variable (a quarter, half and three
     * quarters along): terms doubled from 50 while they come closer, at most [MOST_TERMS]; the bound rounded up to one digit.
     */
    private fun partialSums(parts: List<Part>, base: Expr, s: Given, other: String, applied: (Expr) -> Expr, height: Expr?, length: Expr): Check {
        val span = if (other == "y") height!! else length
        val points = listOf(tidy(span / num(4)), tidy(span / Expr.TWO), tidy(mul(num(3), span) / num(4)))
        // What each part's n-th term gives under the condition, as a function of n and the other variable.
        val shapes = parts.map { applied(it.factor) }
        val zeros = listOf(base)
        // The letters of the problem (c of the wave equation, in a coefficient and its factor alike; L; an amplitude): two values.
        val parameters = (parts.flatMap { it.coefficient.formula.symbols() + it.coefficient.exceptions.flatMap { e -> e.second.symbols() } } +
            shapes.flatMap { it.symbols() } + base.symbols() + s.value.symbols() + span.symbols()).toSet() - N.name - other
        var done = 0
        var worst = 0.0
        var scale = 1.0
        for (sample in if (parameters.isEmpty()) listOf(0.0) else SAMPLES) {
            val env = parameters.associateWith { sample }
            val xs = points.map { Numeric.eval(it, env).takeIf { v -> v.isFinite() } ?: throw CalcException(Failure.UNSOLVED) }
            val targets = xs.map { Numeric.eval(s.value, env + (other to it)) }
            if (targets.any { !it.isFinite() }) throw CalcException(Failure.UNSOLVED)
            // The size of what is summed: the value, and what the series has besides its terms (a steady state of 100°).
            scale = max(scale, max(targets.maxOf { abs(it) }, xs.maxOf { at -> abs(Numeric.eval(base, env + (other to at))) }))
            val exceptions = parts.map { p -> p.coefficient.exceptions.associate { (m, v) -> m to Numeric.eval(v, env) } }
            val sums = xs.map { at -> zeros.sumOf { z -> Numeric.eval(z, env + (other to at)) } }.toMutableList()
            if (sums.any { !it.isFinite() } || exceptions.any { e -> e.values.any { !it.isFinite() } }) throw CalcException(Failure.UNSOLVED)
            done = 0
            var firstError = Double.NaN
            var error: Double
            var level = 50
            while (true) {
                for (n in done + 1..level) {
                    Budget.check()
                    for ((k, p) in parts.withIndex()) {
                        val c = exceptions[k][n] ?: Numeric.eval(p.coefficient.formula, env + (N.name to n.toDouble()))
                        if (!c.isFinite()) throw CalcException(Failure.UNSOLVED)
                        if (c == 0.0) continue
                        for ((i, at) in xs.withIndex()) {
                            val shape = Numeric.eval(shapes[k], env + mapOf(N.name to n.toDouble(), other to at))
                            if (!shape.isFinite()) throw CalcException(Failure.UNSOLVED)
                            sums[i] += c * shape
                        }
                    }
                }
                done = level
                error = xs.indices.maxOf { abs(sums[it] - targets[it]) }
                if (firstError.isNaN()) firstError = error
                if (error <= 1e-10 * scale || level >= MOST_TERMS) break
                level *= 2
            }
            // Close, and closer than with the first terms: the series converges to the value there.
            val converging = error <= 1e-10 * scale || error <= 0.5 * firstError
            if (error > 1e-2 * scale || !converging) throw CalcException(Failure.UNSOLVED)
            worst = max(worst, error)
        }
        val error = worst
        // Rounding in the sums is far below this: 10⁻¹² of the scale for each thousand terms, added before rounding up.
        val bound = error + 1e-12 * scale * (1 + done / 1000.0)
        val exponent = floor(log10(bound)).toInt()
        var digit = ceil(bound / 10.0.pow(exponent) - 1e-9).toInt().coerceAtLeast(1)
        var e = exponent
        if (digit >= 10) { digit = 1; e++ }
        return Check(if (s.derivative) s.variable else null, s.variable, s.point, s.value, done, points, digit, e, false,
            parameters.sorted(), if (parameters.isEmpty()) emptyList() else SAMPLE_VALUES)
    }

}

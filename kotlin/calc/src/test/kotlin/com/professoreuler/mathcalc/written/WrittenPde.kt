package com.professoreuler.mathcalc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.random.Random

/**
 * Partial differential equations. The type from b² − 4ac worked out here; the canonical form carried back by the test's own chain
 * rule (a test function U(ξ, η) of known derivatives, u = U(ξ(x, y), η(x, y)) with ξ and η read from the engine: the original
 * operator on u is a constant times the canonical one on U, and so are the right sides). Separation of variables against the
 * textbook's series with its coefficients computed here by quadrature, 2/L ∫₀ᴸ f(x)·Xₙ(x) dx: the partial sums of the engine's
 * coefficients times its terms equal the textbook's at points, and its steady state and constant terms are the textbook's.
 */
internal object WrittenPde {
    private const val AREA = "pdes"

    // -----------------------------------------------------------------------------------------------------------------------
    // The type and the canonical form.

    /** U(ξ, η) = sin(ξ + 0.3η) + 0.2ξ²η + e^(0.1η) and its derivatives: U, U_ξ, U_η, U_ξξ, U_ξη, U_ηη. */
    private fun test(xi: Double, eta: Double): DoubleArray {
        val s = sin(xi + 0.3 * eta)
        val c = cos(xi + 0.3 * eta)
        val e = exp(0.1 * eta)
        return doubleArrayOf(s + 0.2 * xi * xi * eta + e, c + 0.4 * xi * eta, 0.3 * c + 0.2 * xi * xi + 0.1 * e,
            -s + 0.4 * eta, -0.3 * s + 0.4 * xi, -0.09 * s + 0.01 * e)
    }

    private val MARKERS = listOf("u", "u_\\xi", "u_\\eta", "u_\\xi\\xi", "u_\\xi\\eta", "u_\\eta\\eta")

    /** The canonical form of a·u_xx + b·u_xy + c·u_yy + d·u_x + e·u_y + f·u = g(x, y), carried back by the chain rule. */
    private fun canonicalHolds(k: Pde.Classification, a: Double, b: Double, c: Double, d: Double, e: Double, f: Double,
        g: (Double, Double) -> Double): Boolean {
        val form = k.canonical ?: return true
        val x = k.coefficients.x
        val y = k.coefficients.y
        fun xi(px: Double, py: Double) = at(form.xi, mapOf(x to px, y to py))
        fun eta(px: Double, py: Double) = at(form.eta, mapOf(x to px, y to py))
        // ξ and η are linear: their gradients from three values.
        val x0 = xi(0.0, 0.0); val a1 = xi(1.0, 0.0) - x0; val b1 = xi(0.0, 1.0) - x0
        val e0 = eta(0.0, 0.0); val a2 = eta(1.0, 0.0) - e0; val b2 = eta(0.0, 1.0) - e0
        if (abs(a1 * b2 - a2 * b1) < 1e-9) return false
        // The form's shape: u_ξη alone (hyperbolic), u_ηη alone (parabolic), u_ξξ + u_ηη (elliptic), leading coefficient 1.
        fun coefficient(marker: String) = at(form.equation, MARKERS.associateWith { if (it == marker) 1.0 else 0.0 } + mapOf("\\xi" to 0.3, "\\eta" to 0.7))
        val shape = when (k.type) {
            Pde.Type.HYPERBOLIC -> close(coefficient("u_\\xi\\eta"), 1.0) && abs(coefficient("u_\\xi\\xi")) < 1e-12 && abs(coefficient("u_\\eta\\eta")) < 1e-12
            Pde.Type.PARABOLIC -> close(coefficient("u_\\eta\\eta"), 1.0) && abs(coefficient("u_\\xi\\xi")) < 1e-12 && abs(coefficient("u_\\xi\\eta")) < 1e-12
            Pde.Type.ELLIPTIC -> close(coefficient("u_\\xi\\xi"), 1.0) && close(coefficient("u_\\eta\\eta"), 1.0) && abs(coefficient("u_\\xi\\eta")) < 1e-12
            null -> false
        }
        if (!shape) return false
        var lead: Double? = null
        for ((px, py) in listOf(0.31 to -0.47, -0.82 to 0.29, 0.55 to 0.91, 1.3 to -1.1, -0.2 to 0.05)) {
            val q = xi(px, py)
            val r = eta(px, py)
            val (uu, ux, ue, uxx, uxe, uee) = test(q, r).toList()
            val dx = ux * a1 + ue * a2
            val dy = ux * b1 + ue * b2
            val dxx = uxx * a1 * a1 + 2 * uxe * a1 * a2 + uee * a2 * a2
            val dxy = uxx * a1 * b1 + uxe * (a1 * b2 + a2 * b1) + uee * a2 * b2
            val dyy = uxx * b1 * b1 + 2 * uxe * b1 * b2 + uee * b2 * b2
            val original = a * dxx + b * dxy + c * dyy + d * dx + e * dy + f * uu
            val canonical = at(form.equation, mapOf("u" to uu, "u_\\xi" to ux, "u_\\eta" to ue, "u_\\xi\\xi" to uxx, "u_\\xi\\eta" to uxe, "u_\\eta\\eta" to uee,
                "\\xi" to q, "\\eta" to r))
            val rhs = at(form.rhs, mapOf("\\xi" to q, "\\eta" to r))
            if (lead == null) lead = original / canonical
            if (!close(original, lead * canonical, 1e-8, 1e-9)) return false
            if (!close(g(px, py), lead * rhs, 1e-8, 1e-9)) return false
        }
        return lead != null && abs(lead!!) > 1e-9
    }

    private operator fun <T> List<T>.component6() = this[5]

    private fun types(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(160) { i ->
            var a: Int; var b: Int; var c: Int
            do { a = r.int(-5, 5); b = r.int(-6, 6); c = r.int(-5, 5) } while (a == 0 && c == 0)
            // A share of parabolic ones: b² = 4ac.
            if (i % 4 == 0) { val s = r.int(1, 3); val t = r.int(-3, 3, nonzero = true); a = s * s; c = t * t; b = 2 * s * t * (if (r.nextBoolean()) 1 else -1) }
            var d: Int; var e: Int
            // Both variables in it (an equation in x alone is no partial differential equation).
            do { d = r.int(-3, 3); e = r.int(-3, 3) } while ((a == 0 && b == 0 && d == 0) || (c == 0 && b == 0 && e == 0))
            val f = r.int(-2, 2)
            val g = listOf(0, 0, r.int(-5, 5)).random(r)
            val rhsText = if (i % 5 == 1) "x y" else "$g"
            val gf: (Double, Double) -> Double = if (i % 5 == 1) { px, py -> px * py } else { _, _ -> g.toDouble() }
            val lhs = listOf(a to "u_xx", b to "u_xy", c to "u_yy", d to "u_x", e to "u_y", f to "u").filter { it.first != 0 }
                .mapIndexed { j, (k, body) -> term(k.toLong(), body, first = j == 0) }.joinToString("")
            val disc = b * b - 4 * a * c
            val type = when { disc > 0 -> Pde.Type.HYPERBOLIC; disc == 0 -> Pde.Type.PARABOLIC; else -> Pde.Type.ELLIPTIC }
            out += Task(AREA, "pdetype($lhs = $rhsText)", "${type.name.lowercase()} Δ = $disc", judge = { calc ->
                val k = (calc.outcome as? Outcome.PdeType)?.classification
                k != null && k.type == type && rationalOf(k.discriminant) == q(disc.toLong()) &&
                    canonicalHolds(k, a.toDouble(), b.toDouble(), c.toDouble(), d.toDouble(), e.toDouble(), f.toDouble(), gf)
            })
        }
        // Coefficients that depend on the point: u_xx + k·x·u_yy (Tricomi's for k = 1), y·u_xx + u_yy, u_xx + k·u_yy with a letter.
        for (k in listOf(-3, -2, -1, 1, 2, 3, 5)) {
            out += Task(AREA, "pdetype(u_xx + ${if (k == 1) "" else if (k == -1) "-" else "$k"}x u_yy = 0)".replace("+ -", "- "), "Δ = ${-4 * k}x", judge = regions("x") { x -> -4.0 * k * x })
            out += Task(AREA, "pdetype(${if (k == 1) "" else if (k == -1) "-" else "$k"}y u_xx + u_yy = 0)", "Δ = ${-4 * k}y", judge = regions("y") { y -> -4.0 * k * y })
        }
        // Not linear of the second order: no type.
        for (text in listOf("pdetype(u u_x + u_t = 0)", "pdetype(u_t = u_xxx)", "pdetype(u_x + u_y = 0)", "pdetype(u_xx^2 + u_yy = 0)")) {
            out += Task(AREA, text, "no type", fails = setOf(Failure.UNSOLVED)) { false }
        }
        return out
    }

    /** A type that depends on one letter: the discriminant is [disc] there, and each region's type holds at its points. */
    private fun regions(letter: String, disc: (Double) -> Double): (Calculation) -> Boolean = { calc ->
        val k = (calc.outcome as? Outcome.PdeType)?.classification
        if (k == null || k.type != null) false else {
            val samples = listOf(-2.7, -0.4, 0.0, 0.6, 3.1)
            agrees(k.discriminant, listOf(letter), samples.map { doubleArrayOf(it) }) { (v) -> disc(v) } && samples.all { v ->
                val want = disc(v).let { if (it > 0) Pde.Type.HYPERBOLIC else if (it == 0.0) Pde.Type.PARABOLIC else Pde.Type.ELLIPTIC }
                val holding = k.regions.filter { reg ->
                    if (reg.set != null) reg.set.intervals.any { iv ->
                        val lo = iv.lower?.let { valueOf(it) } ?: Double.NEGATIVE_INFINITY
                        val hi = iv.upper?.let { valueOf(it) } ?: Double.POSITIVE_INFINITY
                        (v > lo || (iv.lowerClosed && v == lo)) && (v < hi || (iv.upperClosed && v == hi))
                    } else reg.points.any { abs(valueOf(it) - v) < 1e-12 }
                }
                holding.size == 1 && holding[0].type == want
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------------
    // Separation of variables.

    private enum class End { FIXED, INSULATED }

    /** A problem: the series' n-th term (n ≥ 1) at (x, t), the steady state, and the constant terms (a₀ of u, b₀ of u_t) where there are. */
    private class Known(val term: (Int, Double, Double) -> Double, val steady: ((Double) -> Double)?, val zeros: Map<String, Double>,
        val constant: (Double, Double) -> Double = { _, _ -> 0.0 })

    /** The engine's partial sum of its n-th terms, n = 1 … [terms], at (x, t). */
    private fun engineSum(s: Pde.Separated, terms: Int, x: Double, t: Double): Double = s.coefficients.sumOf { k ->
        (1..terms).sumOf { n ->
            val c = k.exceptions.firstOrNull { it.first == n }?.let { valueOf(it.second) } ?: at(k.formula, mapOf("n" to n.toDouble()))
            if (c == 0.0) 0.0 else c * at(k.factor, mapOf("n" to n.toDouble(), s.x to x, s.t to t))
        }
    }

    private fun solves(known: Known, points: List<Pair<Double, Double>>): (Calculation) -> Boolean = { calc ->
        val s = (calc.outcome as? Outcome.PdeSeries)?.solution
        if (s == null) false else {
            val n = 12
            val sums = points.all { (x, t) ->
                val mine = (1..n).sumOf { known.term(it, x, t) }
                val scale = (1..n).sumOf { abs(known.term(it, x, t)) } + 1e-3
                abs(engineSum(s, n, x, t) - mine) <= 1e-8 * scale
            }
            val steady = known.steady?.let { v -> s.steady != null && listOf(0.3, 0.77, 1.4).all { x -> close(at(s.steady, mapOf(s.x to x)), v(x), 1e-10, 1e-10) } }
                ?: (s.steady == null || isZero(s.steady))
            // Each constant term (a₀ of u, b₀ of u_t) the textbook's; one that is 0 may be left out.
            val given = s.coefficients.filter { it.zero != null }.associate { it.name to valueOf(it.zero!!) }
            val constants = given.all { (name, v) -> close(v, known.zeros[name] ?: 0.0, 1e-9, 1e-10) } &&
                known.zeros.all { (name, v) -> abs(v) < 1e-12 || name in given }
            // A finite series given as its sum: the textbook's coefficients end too, and its sum is u.
            val exact = s.sum == null || points.all { (x, t) ->
                val tail = (9..n).all { abs(known.term(it, x, t)) < 1e-10 }
                val u = (known.steady?.invoke(x) ?: 0.0) + known.constant(x, t) + (1..n).sumOf { known.term(it, x, t) }
                tail && close(at(s.sum, mapOf(s.x to x, s.t to t)), u, 1e-9, 1e-10)
            }
            sums && steady && constants && exact
        }
    }

    /** f as typed and as a function: a polynomial of degree ≤ 3, or a sum of sines (then the series is finite on (0, π) or (0, 1)). */
    private class Initial(val text: String, val f: (Double) -> Double, val sines: Map<Int, Int> = emptyMap())

    private fun Random.polynomial(): Initial {
        val c = List(int(1, 4)) { int(-3, 3).toLong() }.let { if (it.all { v -> v == 0L }) listOf(1L) else it }
        return Initial(polyText(c), { x -> polyAt(c, x) })
    }

    /** Σ cₖ·sin(k·π·x/L) typed for L = π (sin(kx)) or L = 1 (sin(kπx)). */
    private fun Random.sines(unit: Boolean): Initial {
        val m = (1..int(1, 3)).associate { int(1, 6) to int(-4, 4, nonzero = true) }
        val text = m.entries.joinToString(" + ") { (k, c) ->
            val arg = if (unit) (if (k == 1) "pi x" else "${k}pi x") else (if (k == 1) "x" else "${k}x")
            "${if (c == 1) "" else if (c == -1) "-" else "$c"}sin($arg)"
        }.replace("+ -", "- ")
        return Initial(text, { x -> m.entries.sumOf { (k, c) -> c * sin(k * (if (unit) PI else 1.0) * x) } }, m)
    }

    private fun coefficientOf(l: Double, f: (Double) -> Double, mode: (Double) -> Double) = 2 / l * integrate(0.0, l, 1e-13) { x -> f(x) * mode(x) }

    private fun separation(r: Random): List<Task> {
        val out = ArrayList<Task>()
        val lengths = listOf("pi" to PI, "1" to 1.0, "2" to 2.0, "3" to 3.0)
        val points = listOf(0.37 to 0.05, 0.81 to 0.2, 0.23 to 0.6, 0.66 to 1.3)
        // The heat equation.
        repeat(70) { i ->
            val (lt, l) = lengths[i % 4]
            val k = listOf(1, 2, 3).random(r)
            val unitSines = l == 1.0 && i % 3 == 0
            val f = if ((l == PI || unitSines) && i % 3 == 0) r.sines(unitSines) else r.polynomial()
            val ends = listOf(End.FIXED to End.FIXED, End.FIXED to End.FIXED, End.INSULATED to End.INSULATED, End.FIXED to End.INSULATED,
                End.INSULATED to End.FIXED).random(r)
            val (left, right) = ends
            // Ends fixed at values other than 0 now and then: the steady state v = A + (B − A)x/L.
            val (va, vb) = if (left == End.FIXED && right == End.FIXED && i % 5 == 2) r.int(-9, 9) to r.int(-9, 9) else 0 to 0
            val v = { x: Double -> va + (vb - va) * x / l }
            val pts = points.map { (x, t) -> x * l to t / k }
            val sineAtZero = left == End.FIXED
            val quarter = left != right
            val freq = { n: Int -> if (quarter) (2 * n - 1) * PI / (2 * l) else n * PI / l }
            val mode = { n: Int -> { x: Double -> if (sineAtZero) sin(freq(n) * x) else cos(freq(n) * x) } }
            val rate = freq
            val b = (1..12).map { n -> coefficientOf(l, { x -> f.f(x) - v(x) }, mode(n)) }
            val zeros = if (left == End.INSULATED && right == End.INSULATED) mapOf("a" to 2 / l * integrate(0.0, l, 1e-13) { f.f(it) }) else emptyMap()
            val a0 = zeros["a"] ?: 0.0
            val known = Known({ n, x, t -> b[n - 1] * exp(-k * rate(n) * rate(n) * t) * mode(n)(x) }, if (va != 0 || vb != 0) v else null, zeros,
                { _, _ -> a0 / 2 })
            fun end(at: String, e: End, value: Int) = if (e == End.FIXED) "u($at, t) = $value" else "u_x($at, t) = 0"
            val call = "pdsolve(u_t = ${if (k == 1) "" else "$k"}u_xx, ${end("0", left, va)}, ${end(lt, right, vb)}, u(x, 0) = ${f.text})"
            out += Task(AREA, call, "heat", judge = solves(known, pts))
        }
        // The wave equation: a string fixed at both ends, or free (insulated) ones with the rigid motion.
        repeat(50) { i ->
            val (lt, l) = lengths[i % 4]
            val c = listOf(1, 2, 3).random(r)
            val free = i % 4 == 3
            val f = if (l == PI && i % 3 == 0) r.sines(false) else r.polynomial()
            val g = if (i % 2 == 0) r.polynomial() else Initial("0", { 0.0 })
            val mode = { n: Int -> { x: Double -> if (free) cos(n * PI * x / l) else sin(n * PI * x / l) } }
            val a = (1..12).map { n -> coefficientOf(l, f.f, mode(n)) }
            val bb = (1..12).map { n -> coefficientOf(l, g.f, mode(n)) / (c * n * PI / l) }
            val zeros = if (free) mapOf("a" to 2 / l * integrate(0.0, l, 1e-13) { f.f(it) }, "b" to 2 / l * integrate(0.0, l, 1e-13) { g.f(it) }) else emptyMap()
            val known = Known({ n, x, t -> (a[n - 1] * cos(c * n * PI / l * t) + bb[n - 1] * sin(c * n * PI / l * t)) * mode(n)(x) }, null, zeros,
                { _, t -> (zeros["a"] ?: 0.0) / 2 + (zeros["b"] ?: 0.0) * t / 2 })
            val endText = if (free) "u_x(0, t) = 0, u_x($lt, t) = 0" else "u(0, t) = 0, u($lt, t) = 0"
            val call = "pdsolve(u_tt = ${if (c == 1) "" else "${c * c}"}u_xx, $endText, u(x, 0) = ${f.text}, u_t(x, 0) = ${g.text})"
            out += Task(AREA, call, "wave", judge = solves(known, points.map { (x, t) -> x * l to t }))
        }
        // Laplace's equation on a rectangle (0, a) × (0, b), one side at f and the others at 0.
        repeat(40) { i ->
            val (at, a) = lengths[i % 4]
            val (bt, b) = lengths[(i / 4 + 1) % 4]
            val side = i % 4
            val f = r.polynomial()
            val sideText = listOf("u(x, 0) = 0", "u(x, $bt) = 0", "u(0, y) = 0", "u($at, y) = 0").toMutableList()
            sideText[side] = when (side) { 0 -> "u(x, 0) = ${f.text}"; 1 -> "u(x, $bt) = ${f.text}"; 2 -> "u(0, y) = ${f.text.replace("x", "y")}"; else -> "u($at, y) = ${f.text.replace("x", "y")}" }
            val horizontal = side < 2
            val width = if (horizontal) a else b
            val c = (1..12).map { n -> coefficientOf(width, f.f) { s -> sin(n * PI * s / width) } }
            val term = { n: Int, x: Double, y: Double ->
                val w = n * PI / width
                c[n - 1] * when (side) {
                    0 -> sinh(w * (b - y)) / sinh(w * b) * sin(w * x)
                    1 -> sinh(w * y) / sinh(w * b) * sin(w * x)
                    2 -> sinh(w * (a - x)) / sinh(w * a) * sin(w * y)
                    else -> sinh(w * x) / sinh(w * a) * sin(w * y)
                }
            }
            out += Task(AREA, "pdsolve(u_xx + u_yy = 0, ${sideText.joinToString(", ")})", "laplace",
                judge = solves(Known(term, null, emptyMap()), listOf(0.31 * a to 0.42 * b, 0.7 * a to 0.2 * b, 0.5 * a to 0.85 * b)))
        }
        // Conditions that do not fix one solution.
        for (text in listOf("pdsolve(u_t = u_xx, u(0, t) = 0)", "pdsolve(u_t = u_xx)", "pdsolve(u_tt = u_xx, u(0, t) = 0, u(pi, t) = 0, u(x, 0) = sin(x))",
                "pdsolve(u_xx + u_yy = 0, u(x, 0) = 0, u(x, 1) = 1, u(0, y) = 0)", "pdsolve(u_t = 2u_xx, u(0, t) = 0, u(0, t) = 0, u(x, 0) = x)")) {
            out += Task(AREA, text, "CONDITIONS", fails = setOf(Failure.CONDITIONS)) { false }
        }
        return out
    }

    fun all(): List<Task> {
        val r = Random(1822)
        return types(r) + separation(r)
    }
}

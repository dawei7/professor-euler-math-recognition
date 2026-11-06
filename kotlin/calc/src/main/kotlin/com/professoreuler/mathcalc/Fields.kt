package com.professoreuler.mathcalc

import kotlin.math.hypot
import kotlin.math.max

/**
 * Direction fields: the slope field of y′ = f(x, y) and the phase portrait of x′ = f(x, y), y′ = g(x, y), with the equilibria
 * of a phase portrait (each put back into both equations) and what kind each is, from the eigenvalues of the Jacobian there. For
 * a linear system that kind is exact; for a nonlinear one the linearization decides it only where no eigenvalue has real part 0
 * and the two are not equal (Hartman–Grobman; a centre, a star or a degenerate node of the linearization is not decided).
 * The arrows and the solution curves through a point ([arrows], [trajectory]) are a picture's, drawn by the app, not answers.
 */
object Fields {
    /** What an equilibrium is. */
    enum class Kind {
        /** Two real eigenvalues of one sign: the curves run into it (stable) or out of it. */
        NODE,
        /** One real eigenvalue twice with one eigenvector: a degenerate (improper) node. */
        DEGENERATE_NODE,
        /** One real eigenvalue twice with two eigenvectors (A = λI): every curve a straight ray. */
        STAR,
        /** Real eigenvalues of both signs: unstable. */
        SADDLE,
        /** Complex eigenvalues α ± βi with α ≠ 0: curves wind into it (α < 0) or out of it. */
        SPIRAL,
        /** Imaginary eigenvalues ±βi: closed orbits around it, stable but not attracting. */
        CENTRE,
        /** An eigenvalue 0: a line of equilibria (a linear system with det A = 0). */
        NON_ISOLATED,
        /** A nonlinear system whose linearization does not decide (a centre, a star, a degenerate node or an eigenvalue 0 of it). */
        UNDECIDED,
    }

    /**
     * An equilibrium: where it is ([at], one value for each coordinate), the Jacobian there (A itself for a linear system), its
     * eigenvalues, its [kind] and whether it is asymptotically stable (true), unstable (false) or neither or undecided (null).
     */
    class Equilibrium(val at: List<Expr>, val jacobian: Expr.Mat, val eigenvalues: List<Expr>, val kind: Kind, val stable: Boolean?)

    /** A slope field of y′ = f(x, y) or a phase portrait of a system in the plane. */
    enum class FieldKind { SLOPE, PHASE }

    /**
     * The field (dx, dy) = ([dx], [dy]) at the point ([x], [y]): (1, f) for a slope field, (f, g) for a phase portrait in its
     * variable [t]. [equilibria] of a phase portrait where they are found ([found] false where the system could not be solved for
     * them); [constants], the constant solutions y = c of a slope field (f(x, c) = 0 for every x); [linear] for a linear system.
     */
    class Field(val kind: FieldKind, val x: String, val y: String, val t: String, val dx: Expr, val dy: Expr, val equilibria: List<Equilibrium>,
        val found: Boolean, val constants: List<Expr>, val linear: Boolean)

    private fun tidy(e: Expr) = Calculus.tidy(e)

    /** The sign of a number: exactly 0, or its sign (null where it is not a real number). */
    private fun sign(e: Expr): Int? {
        if (LinAlg.isNull(e)) return 0
        val v = Numeric.value(e) ?: return null
        return if (v > 0) 1 else -1
    }

    /**
     * The kind of the equilibrium of x′ = J·x in the plane, from τ = tr J, Δ = det J and τ² − 4Δ; [linear] false for the
     * linearization of a nonlinear system. Null where a sign cannot be decided.
     */
    fun classify(j: Expr.Mat, linear: Boolean): Pair<Kind, Boolean?>? {
        val tau = LinAlg.trace(j)
        val delta = LinAlg.det(j)
        val disc = tidy(Algebra.expand(tau * tau - num(4) * delta))
        val st = sign(tau) ?: return null
        val sd = sign(delta) ?: return null
        val sq = sign(disc) ?: return null
        val stable: Boolean? = if (st < 0) true else if (st > 0) false else null
        val kind = when {
            sd < 0 -> return Kind.SADDLE to false
            sd == 0 -> if (linear) Kind.NON_ISOLATED else Kind.UNDECIDED
            sq > 0 -> Kind.NODE
            sq == 0 -> {
                val scalar = LinAlg.isNull(j.rows[0][1]) && LinAlg.isNull(j.rows[1][0])
                if (!linear) Kind.UNDECIDED else if (scalar) Kind.STAR else Kind.DEGENERATE_NODE
            }
            st == 0 -> if (linear) Kind.CENTRE else Kind.UNDECIDED
            else -> Kind.SPIRAL
        }
        // A centre is stable but not attracting; an eigenvalue 0 decides nothing.
        return kind to if (sd == 0) null else stable
    }

    private fun eigenvalues(j: Expr.Mat): List<Expr> = try {
        LinAlg.eigen(j)?.flatMap { e -> List(e.multiplicity) { e.value } } ?: emptyList()
    } catch (e: CalcException) {
        if (e.failure == Failure.TOO_LONG) throw e
        emptyList()
    }

    /** The equilibrium of x′ = A·x + b in the plane, A·x* = −b; null where det A = 0 and b ≠ 0 (no equilibrium) or a sign is undecided. */
    fun linearEquilibrium(a: Expr.Mat, b: List<Expr>): Equilibrium? {
        val (kind, stable) = classify(a, linear = true) ?: return null
        val at = if (LinAlg.isNull(LinAlg.det(a))) {
            if (b.any { !LinAlg.isNull(it) }) return null
            listOf(Expr.ZERO, Expr.ZERO)
        } else {
            (LinAlg.times(LinAlg.inverse(a), LinAlg.column(b)) as Expr.Mat).rows.map { tidy(neg(it[0])) }
        }
        return Equilibrium(at, a, eigenvalues(a), kind, stable)
    }

    /** The slope field of y′ = [f]([x], [y]), with its constant solutions where f does not hold x. */
    fun slope(f: Expr, x: String, y: String): Field {
        val ys = Expr.Sym(y)
        val constants = if (f.has(Expr.Sym(x)) || !f.has(ys)) emptyList() else try {
            val s = Solve.equation(f, Expr.ZERO, ys)
            if (s.many || s.always || s.periodic.isNotEmpty()) emptyList()
            else s.values.filter { v -> v.symbols().isEmpty() && LinAlg.isNull(tidy(f.substitute(ys, v))) }
        } catch (e: CalcException) {
            if (e.failure == Failure.TOO_LONG) throw e
            emptyList()
        }
        return Field(FieldKind.SLOPE, x, y, x, Expr.ONE, f, emptyList(), true, constants, false)
    }

    /**
     * The phase portrait of [x]′ = [f], [y]′ = [g] (autonomous: no t in them): its equilibria, solved for and put back, each
     * with the kind of its linearization.
     */
    fun phase(f: Expr, g: Expr, x: String, y: String, t: String): Field {
        val xs = Expr.Sym(x)
        val ys = Expr.Sym(y)
        val linear = listOf(f, g).all { e ->
            listOf(xs, ys).all { v -> tidy(Calculus.derivative(e, v)).symbols().isEmpty() }
        }
        if (linear) {
            val a = Expr.Mat(listOf(f, g).map { e -> listOf(tidy(Calculus.derivative(e, xs)), tidy(Calculus.derivative(e, ys))) })
            val b = listOf(f, g).map { tidy(it.substitute(xs, Expr.ZERO).substitute(ys, Expr.ZERO)) }
            val e = linearEquilibrium(a, b)
            return Field(FieldKind.PHASE, x, y, t, f, g, listOfNotNull(e), true, emptyList(), true)
        }
        val solutions = try {
            Solve.system(listOf(f, g).filter { !isZero(it) }, listOf(x, y))
        } catch (e: CalcException) {
            if (e.failure == Failure.TOO_LONG) throw e
            null
        }
        val out = ArrayList<Equilibrium>()
        val jac = listOf(f, g).map { e -> listOf(Calculus.derivative(e, xs), Calculus.derivative(e, ys)) }
        for (s in solutions.orEmpty()) {
            Budget.check()
            val px = s[x] ?: continue
            val py = s[y] ?: continue
            if (px.symbols().isNotEmpty() || py.symbols().isNotEmpty() || px.hasI() || py.hasI()) continue
            fun at(e: Expr) = tidy(e.substitute(xs, px).substitute(ys, py))
            if (!LinAlg.isNull(at(f)) || !LinAlg.isNull(at(g))) continue
            val j = Expr.Mat(jac.map { r -> r.map { at(it) } })
            val (kind, stable) = classify(j, linear = false) ?: continue
            out += Equilibrium(listOf(px, py), j, eigenvalues(j), kind, stable)
        }
        return Field(FieldKind.PHASE, x, y, t, f, g, out, solutions != null, emptyList(), false)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Drawing: what the app draws in a window.

    /** An arrow of the field at ([x], [y]): its direction ([dx], [dy]), of length 1 in units of the window's width and height. */
    class Arrow(val x: Double, val y: Double, val dx: Double, val dy: Double, val strength: Double)

    private fun compiled(field: Field): Pair<(Double, Double) -> Double, (Double, Double) -> Double> {
        // The field's letters as x and y, which the compiled functions read.
        fun c(e: Expr): (Double, Double) -> Double {
            val h = Expr.Sym("\u0001h")
            val v = Expr.Sym("\u0001v")
            val renamed = e.substitute(Expr.Sym(field.x), h).substitute(Expr.Sym(field.y), v).substitute(h, Expr.Sym("x")).substitute(v, Expr.Sym("y"))
            return Plot.compile2(renamed)
        }
        return c(field.dx) to c(field.dy)
    }

    /** [columns] × [rows] arrows over [window], at the middles of its cells; none where the field is not defined. */
    fun arrows(field: Field, window: PlotWindow, columns: Int, rows: Int): List<Arrow> {
        val (fx, fy) = compiled(field)
        val out = ArrayList<Arrow>()
        for (i in 0 until columns) for (j in 0 until rows) {
            val x = window.xMin + window.width * (i + 0.5) / columns
            val y = window.yMin + window.height * (j + 0.5) / rows
            val u = fx(x, y)
            val v = fy(x, y)
            if (!u.isFinite() || !v.isFinite()) continue
            val su = u / window.width
            val sv = v / window.height
            val l = hypot(su, sv)
            if (l == 0.0) out += Arrow(x, y, 0.0, 0.0, 0.0) else out += Arrow(x, y, su / l, sv / l, hypot(u, v))
        }
        return out
    }

    /**
     * The solution curve through ([x0], [y0]) in [window], forwards and backwards, as a polyline x₀, y₀, x₁, y₁, …: the classical
     * Runge–Kutta method in steps of a fixed length on the screen, until it leaves the window (with a margin), stops at an
     * equilibrium or has [most] points each way.
     */
    fun trajectory(field: Field, x0: Double, y0: Double, window: PlotWindow, most: Int = 600): DoubleArray {
        val (fx, fy) = compiled(field)
        val step = 0.004
        fun direction(x: Double, y: Double, sign: Double): Pair<Double, Double>? {
            val u = fx(x, y)
            val v = fy(x, y)
            if (!u.isFinite() || !v.isFinite()) return null
            // In screen units, normalized: the curve moves at one speed on the screen.
            val su = u / window.width
            val sv = v / window.height
            val l = hypot(su, sv)
            if (l < 1e-12) return null
            return sign * su / l * window.width to sign * sv / l * window.height
        }
        fun run(sign: Double): List<Pair<Double, Double>> {
            val points = ArrayList<Pair<Double, Double>>()
            var x = x0
            var y = y0
            val margin = 0.25
            for (k in 0 until most) {
                val k1 = direction(x, y, sign) ?: break
                val k2 = direction(x + step / 2 * k1.first, y + step / 2 * k1.second, sign) ?: break
                val k3 = direction(x + step / 2 * k2.first, y + step / 2 * k2.second, sign) ?: break
                val k4 = direction(x + step * k3.first, y + step * k3.second, sign) ?: break
                x += step * (k1.first + 2 * k2.first + 2 * k3.first + k4.first) / 6
                y += step * (k1.second + 2 * k2.second + 2 * k3.second + k4.second) / 6
                if (!x.isFinite() || !y.isFinite()) break
                points += x to y
                if (x < window.xMin - margin * window.width || x > window.xMax + margin * window.width ||
                    y < window.yMin - margin * window.height || y > window.yMax + margin * window.height) break
            }
            return points
        }
        val backward = run(-1.0).asReversed()
        val forward = run(1.0)
        val all = backward + listOf(x0 to y0) + forward
        return DoubleArray(all.size * 2) { k -> if (k % 2 == 0) all[k / 2].first else all[k / 2].second }
    }

    /** A window that shows the equilibria with room around them: [−5, 5]² where there are none, centred and widened otherwise. */
    fun window(field: Field): PlotWindow {
        val points = field.equilibria.mapNotNull { e -> e.at.map { Numeric.value(it) ?: return@mapNotNull null }.let { it[0] to it[1] } } +
            field.constants.mapNotNull { c -> Numeric.value(c)?.let { 0.0 to it } }
        if (points.isEmpty()) return PlotWindow(-5.0, 5.0, -5.0, 5.0)
        val x0 = points.minOf { it.first }
        val x1 = points.maxOf { it.first }
        val y0 = points.minOf { it.second }
        val y1 = points.maxOf { it.second }
        val half = max(5.0, max(x1 - x0, y1 - y0) * 0.75)
        val cx = (x0 + x1) / 2
        val cy = (y0 + y1) / 2
        return PlotWindow(cx - half, cx + half, cy - half, cy + half)
    }
}

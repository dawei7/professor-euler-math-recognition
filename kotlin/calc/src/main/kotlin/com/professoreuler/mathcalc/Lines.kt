package com.professoreuler.mathcalc

import kotlin.math.abs
import kotlin.math.max

/**
 * One line of a learner's own working, and whether it follows from the line before ([holds] null for the first line, and
 * for one that could not be read or compared). Where it does not: [at] a value of the letters that shows it, with the two
 * lines' values there ([before], [after]); or the solutions it [lost] or [gained].
 */
class LineCheck internal constructor(
    val reading: String,
    val holds: Boolean?,
    val at: Map<String, Double>? = null,
    val before: Double? = null,
    val after: Double? = null,
    val lost: List<Expr> = emptyList(),
    val gained: List<Expr> = emptyList(),
    val unreadable: Boolean = false,
)

/**
 * Checks several lines of a learner's working, each read on its own (owner, 1 November 2026: "self-control or simple
 * check"): every line of equations has the same solutions as the one before, every expression the same value, every
 * inequality the same intervals; a line starting with "=" continues the line before. Lines in several letters that are not
 * steps of one another but as many as their letters are a system, and solved as one.
 */
object Lines {

    /** What one line is: its relation's two sides, or an expression; null where it could not be read. */
    private class Line(val reading: String, val lhs: Expr?, val rhs: Expr?, val rel: Rel?, val style: Flags)

    fun check(readings: List<String>): Calculation {
        val flags = Flags()
        val lines = readings.map { parse(it) }
        lines.forEach { l ->
            if (l.style.decimalComma) flags.decimalComma = true
            if (l.style.decimals) flags.decimals = true
            if (l.style.fractions) flags.fractions = true
            if (l.style.degrees) flags.degrees = true
        }
        val outcome = try {
            Budget.within(LIMIT_MS) { system(lines) ?: steps(lines) }
        } catch (e: CalcException) {
            Outcome.Failed(e.failure)
        } catch (e: ArithmeticException) {
            Outcome.Failed(Failure.UNDEFINED)
        }
        return Calculation(outcome, flags.decimalComma, flags.decimals, flags.fractions, degrees = flags.degrees)
    }

    private const val LIMIT_MS = 4000L

    private val TOKEN = Regex("""\\[a-zA-Z]+|\\.|\S""")

    private fun parse(reading: String): Line {
        val flags = Flags()
        return try {
            val tokens = TOKEN.findAll(reading).map { it.value }.toList()
            // "= x + 1": the line goes on from the one before.
            if (tokens.firstOrNull() == "=") {
                val e = Parser(tokens.drop(1), flags).statements().single().sides.single() ?: throw CalcException(Failure.UNREADABLE)
                return Line(reading, null, Calculus.evaluate(e), Rel.EQ, flags)
            }
            val s = Parser(tokens, flags).statements().single()
            val sides = s.sides.filterNotNull().map { Calculus.evaluate(it) }
            when {
                sides.size == 1 -> Line(reading, sides[0], null, null, flags)
                sides.size == 2 && s.relations.size == 1 -> Line(reading, sides[0], sides[1], s.relations[0], flags)
                // a = b = c on one line: its last side stands for it.
                sides.size > 2 && s.relations.all { it == Rel.EQ } -> Line(reading, sides.last(), null, null, flags)
                else -> Line(reading, null, null, null, flags)
            }
        } catch (e: CalcException) {
            Line(reading, null, null, null, flags)
        }
    }

    /** Equations in several letters that do not follow from one another, as many as their letters: one system. */
    private fun system(lines: List<Line>): Outcome? {
        if (lines.size < 2 || lines.any { it.rel != Rel.EQ || it.lhs == null || it.rhs == null }) return null
        val equations = lines.map { it.lhs!! - it.rhs!! }
        val letters = equations.flatMap { it.symbols() }.toSortedSet().toList()
        if (letters.size < 2 || letters.size != equations.size) return null
        // Steps of one another are a working, not a system.
        if (equations.zipWithNext().all { (a, b) -> equivalentMultiples(a, b) }) return null
        val solutions = Solve.system(equations, letters) ?: return null
        return Outcome.SystemSolved(letters, solutions)
    }

    private fun steps(lines: List<Line>): Outcome {
        val checks = ArrayList<LineCheck>()
        var previous: Line? = null
        for (line in lines) {
            val prev = previous
            if (line.lhs == null && line.rhs == null) {
                checks += LineCheck(line.reading, null, unreadable = true)
                continue
            }
            // "= …" continues the expression or the right side before it.
            val current = if (line.lhs == null && prev != null) Line(line.reading, prev.rhs ?: prev.lhs, line.rhs, null, line.style)
                .let { Line(it.reading, it.rhs, null, null, line.style) } else line
            if (prev == null) {
                checks += LineCheck(line.reading, null)
                previous = current
                continue
            }
            checks += compare(prev, current, line.lhs == null)
            previous = current
        }
        return Outcome.LinesChecked(checks)
    }

    /** The check of [b] against the line before it, [a]. */
    private fun compare(a: Line, b: Line, continued: Boolean): LineCheck {
        // An expression continued with "=", or two expressions: the same value.
        if (a.rel == null && b.rel == null || continued) {
            val x = if (continued) a.rhs ?: a.lhs!! else a.lhs!!
            val y = b.lhs!!
            if (Verify.same(x, y)) return LineCheck(b.reading, true)
            val at = Verify.differenceAt(x, y)
            return LineCheck(b.reading, false, at, at?.let { Numeric.eval(x, it) }, at?.let { Numeric.eval(y, it) })
        }
        if (a.rel == null || b.rel == null || a.lhs == null || b.lhs == null || a.rhs == null || b.rhs == null) {
            return LineCheck(b.reading, null)
        }
        val fa = a.lhs - a.rhs
        val fb = b.lhs - b.rhs
        val letters = (fa.symbols() + fb.symbols()).toList()
        // Inequalities: the same intervals.
        if (a.rel.isInequality || b.rel.isInequality) {
            if (letters.size != 1) return LineCheck(b.reading, null)
            val x = Expr.Sym(letters[0])
            val sa = Solve.inequality(a.lhs, a.rel, a.rhs, x) ?: return LineCheck(b.reading, null)
            val sb = Solve.inequality(b.lhs, b.rel, b.rhs, x) ?: return LineCheck(b.reading, null)
            val point = differingPoint(sa, sb)
            return if (point == null) LineCheck(b.reading, true) else LineCheck(b.reading, false, mapOf(letters[0] to point))
        }
        // Equations in one letter: the same solutions.
        if (letters.size == 1) {
            val x = Expr.Sym(letters[0])
            val sa = Solve.equation(a.lhs, a.rhs, x)
            val sb = Solve.equation(b.lhs, b.rhs, x)
            if (sa.always && sb.always) return LineCheck(b.reading, true)
            if (sa.periodic.isNotEmpty() || sb.periodic.isNotEmpty() || sa.many || sb.many) {
                return LineCheck(b.reading, equivalentMultiples(fa, fb).takeIf { it })
            }
            val lost = sa.values.filter { v -> sb.values.none { close(it, v) } }
            val gained = sb.values.filter { v -> sa.values.none { close(it, v) } }
            if (lost.isEmpty() && gained.isEmpty() && sa.always == sb.always) return LineCheck(b.reading, true)
            return LineCheck(b.reading, false, lost = lost, gained = gained)
        }
        // Several letters: both lines give the same values when solved for one of their letters (v = s/t and vt = s both
        // give t = s/v), or the second is a nonzero multiple of the first.
        for (v in letters) {
            val x = Expr.Sym(v)
            val sa = try { Solve.forLetter(a.lhs, a.rhs, x) } catch (e: CalcException) { continue }
            val sb = try { Solve.forLetter(b.lhs, b.rhs, x) } catch (e: CalcException) { continue }
            if (sa.values.isEmpty() || sb.values.isEmpty()) continue
            val same = sa.values.all { p -> sb.values.any { Verify.same(p, it) } } && sb.values.all { q -> sa.values.any { Verify.same(q, it) } }
            return LineCheck(b.reading, same)
        }
        if (equivalentMultiples(fa, fb)) return LineCheck(b.reading, true)
        return LineCheck(b.reading, false, Verify.differenceAt(fa, fb))
    }

    private fun close(a: Expr, b: Expr): Boolean {
        val x = Numeric.value(a) ?: return a == b
        val y = Numeric.value(b) ?: return false
        return abs(x - y) <= 1e-9 * max(1.0, abs(x))
    }

    /** Whether f and g are equal up to a nonzero number: f = c·g, as when both sides of an equation are changed alike. */
    private fun equivalentMultiples(f: Expr, g: Expr): Boolean {
        if (Verify.same(f, g)) return true
        val letters = (f.symbols() + g.symbols()).toList()
        val random = java.util.Random(7)
        repeat(20) {
            val env = letters.associateWith { random.nextDouble() * 6 - 3 + 0.013 }
            val gv = Numeric.eval(g, env)
            val fv = Numeric.eval(f, env)
            if (gv.isFinite() && fv.isFinite() && abs(gv) > 1e-6) {
                val c = fv / gv
                if (abs(c) < 1e-12) return false
                return Verify.same(f, mul(Expr.Real(c), g)) || Rational.near(c)?.let { Verify.same(f, mul(num(it), g)) } == true
            }
        }
        return false
    }

    /** A value in one interval set and not the other, if any. */
    private fun differingPoint(a: IntervalSet, b: IntervalSet): Double? {
        val bounds = (a.intervals + b.intervals).flatMap { listOfNotNull(it.lower, it.upper) }.mapNotNull { Numeric.value(it) }.sorted()
        val probes = ArrayList<Double>()
        if (bounds.isEmpty()) probes += 0.37 else {
            probes += bounds.first() - 1
            probes += bounds.last() + 1
            for ((p, q) in bounds.zipWithNext()) probes += (p + q) / 2
            probes += bounds
        }
        fun inSet(s: IntervalSet, v: Double) = s.intervals.any { i ->
            val lo = i.lower?.let { Numeric.value(it) }
            val hi = i.upper?.let { Numeric.value(it) }
            (lo == null || v > lo || (i.lowerClosed && abs(v - lo) < 1e-12)) && (hi == null || v < hi || (i.upperClosed && abs(v - hi) < 1e-12))
        }
        return probes.firstOrNull { inSet(a, it) != inSet(b, it) }
    }
}

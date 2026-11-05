package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Generated tasks, seeded, checked independently of how the engine worked them out: a product factored is multiplied back, a
 * root put into its equation, a derivative compared with a difference quotient, an antiderivative differentiated, A·A⁻¹ = I,
 * a system's solution put back, a congruence's residues tried, a recurrence's closed form against its terms, a triangle by the
 * law of cosines, a unit converted there and back. Every answer shown must hold; one that the engine declines is counted, and
 * the share declined must stay small.
 */
class GeneratedCorpusTest {
    private val failures = ArrayList<String>()
    private var declined = 0
    private val declinedTasks = ArrayList<String>()
    private var total = 0

    private fun Random.small(lo: Int = -6, hi: Int = 6, nonzero: Boolean = false): Int {
        while (true) {
            val v = nextInt(lo, hi + 1)
            if (!nonzero || v != 0) return v
        }
    }

    private fun term(c: Int, body: String) = when (c) {
        0 -> if (body.isEmpty()) "+0" else ""
        1 -> "+$body"
        -1 -> "-$body"
        else -> (if (c > 0) "+$c" else "$c") + body
    }

    private fun near(a: Double, b: Double, tol: Double = 1e-7) = abs(a - b) <= tol * maxOf(1.0, abs(a), abs(b))

    private fun check(task: String, ok: Boolean?) {
        total++
        when (ok) {
            null -> { declined++; declinedTasks += task }
            false -> failures += task
            true -> {}
        }
    }

    @Test
    fun generatedTasksHold() {
        val r = Random(20261106)
        val x = Expr.Sym("x")
        // Products of linear factors, multiplied out, factored back.
        repeat(300) {
            val a = r.small(); val b = r.small(); val c = r.small(1, 4); val d = r.small()
            val latex = "(x${term(-a, "")})(x${term(-b, "")})(${c}x${term(d, "")})".replace("(x)", "(x)").replace("+0", "").replace("-0", "")
            val expanded = Algebra.expand(MathCalc.calculate(latex, Choice(Mode.EXPAND)).let { (it.outcome as? Outcome.Expression)?.result ?: return@repeat })
            val text = Format.expr(expanded, MathCalc.calculate("1"), latex = true)
            val f = MathCalc.calculate(text, Choice(Mode.FACTOR)).outcome
            check("factor $text", (f as? Outcome.Factored)?.let { Verify.same(Algebra.expand(it.result), expanded) })
        }
        // Quadratics with whole roots: both found, each put back.
        repeat(300) {
            val a = r.small(); val b = r.small(); val k = r.small(1, 5)
            val p = Calculus.tidy(Algebra.expand(mul(num(k.toLong()), add(x, num(-a.toLong())), add(x, num(-b.toLong())))))
            val latex = Format.expr(p, MathCalc.calculate("1"), latex = true) + "=0"
            val o = MathCalc.calculate(latex).outcome
            check("solve $latex", (o as? Outcome.Solved)?.solutions?.values?.let { vs ->
                vs.map { Numeric.value(it) ?: Double.NaN }.sorted().let { got -> got.size == setOf(a, b).size && got.zip(setOf(a, b).sorted()).all { (g, e) -> near(g, e.toDouble()) } }
            })
        }
        // Derivatives against a central difference quotient at a point.
        val pieces = listOf("x^{%d}", "\\sin(%dx)", "\\cos(%dx)", "e^{%dx}", "\\ln(x^{2}+%d)", "\\sqrt{x+%d}")
        repeat(400) {
            val f1 = pieces.random(r).replace("%d", r.small(1, 4).toString())
            val f2 = pieces.random(r).replace("%d", r.small(1, 4).toString())
            val latex = listOf("$f1\\cdot $f2", "\\frac{$f1}{$f2}", "$f1+$f2", "${r.small(2, 5)}$f1-$f2").random(r)
            val c = MathCalc.calculate(latex, Choice(Mode.DERIVATIVE, "x"))
            val d = (c.outcome as? Outcome.Expression)?.result
            val g = (MathCalc.calculate(latex, Choice(Mode.SIMPLIFY)).outcome as? Outcome.Expression)?.result
            check("d/dx $latex", if (d == null || g == null) null else {
                val at = 0.7
                val h = 1e-5
                val diff = (Numeric.eval(g, mapOf("x" to at + h)) - Numeric.eval(g, mapOf("x" to at - h))) / (2 * h)
                val got = Numeric.eval(d, mapOf("x" to at))
                if (!diff.isFinite() || !got.isFinite()) null else near(got, diff, 1e-5)
            })
        }
        // Antiderivatives differentiated back.
        val integrands = listOf("x^{%d}e^{x}", "x\\sin(%dx)", "x^{%d}\\ln(x)", "\\frac{1}{x^{2}+%d}", "\\frac{x}{x^{2}+%d}", "\\sin(x)^{%d}",
            "\\frac{1}{(x+1)(x+%d)}", "e^{%dx}\\cos(x)")
        repeat(300) {
            val latex = integrands.random(r).replace("%d", r.small(2, 4).toString())
            val o = MathCalc.calculate(latex, Choice(Mode.INTEGRAL, "x")).outcome
            check("∫ $latex", (o as? Outcome.Expression)?.result?.let { anti ->
                val f = (MathCalc.calculate(latex, Choice(Mode.SIMPLIFY)).outcome as Outcome.Expression).result
                listOf(0.6, 1.3, 2.1).all { at -> near(Numeric.eval(Calculus.derivative(anti, x), mapOf("x" to at)), Numeric.eval(f, mapOf("x" to at)), 1e-6) }
            })
        }
        // Inverses multiplied back.
        repeat(200) {
            val n = r.nextInt(2, 4)
            val rows = List(n) { List(n) { r.small(-4, 4) } }
            val latex = "\\begin{pmatrix}" + rows.joinToString("\\\\") { it.joinToString("&") } + "\\end{pmatrix}"
            val m = LinAlg.matrix(rows.map { row -> row.map { num(it.toLong()) } })
            if (isZero(LinAlg.det(m))) return@repeat
            val o = MathCalc.calculate(latex, Choice(Mode.INVERSE)).outcome
            check("inverse $latex", ((o as? Outcome.Expression)?.result as? Expr.Mat)?.let { inv ->
                val p = LinAlg.times(m, inv) as Expr.Mat
                p.rows.withIndex().all { (i, row) -> row.withIndex().all { (j, e) -> Calculus.tidy(e - if (i == j) Expr.ONE else Expr.ZERO).let(::isZero) } }
            })
        }
        // Linear systems with a whole solution, put back.
        repeat(200) {
            val sol = listOf(r.small(), r.small(), r.small())
            val n = r.nextInt(2, 4)
            val names = listOf("x", "y", "z").take(n)
            val lines = List(n) { List(n) { r.small(-5, 5) } }
            val m = LinAlg.matrix(lines.map { row -> row.map { num(it.toLong()) } })
            if (isZero(LinAlg.det(m))) return@repeat
            val latex = "\\begin{cases}" + lines.joinToString("\\\\") { row ->
                row.mapIndexed { k, c -> term(c, names[k]) }.joinToString("").removePrefix("+").replace(Regex("[+-]0[xyz]"), "").ifEmpty { "0" } + "=" +
                    row.indices.sumOf { row[it] * sol[it] }
            } + "\\end{cases}"
            val o = MathCalc.calculate(latex).outcome
            check("system $latex", (o as? Outcome.SystemSolved)?.solutions?.singleOrNull()?.let { s ->
                names.indices.all { k -> s[names[k]]?.let { v -> Numeric.value(v)?.let { near(it, sol[k].toDouble()) } } == true }
            })
        }
        // Linear congruences: every residue tried against the answer.
        repeat(200) {
            val a = r.nextInt(1, 30); val b = r.nextInt(0, 30); val m = r.nextInt(2, 40)
            val latex = "${a}x\\equiv $b\\pmod{$m}"
            val o = MathCalc.calculate(latex).outcome
            val expected = (0 until m).filter { (a * it - b).mod(m) == 0 }
            check("congruence $latex", (o as? Outcome.Congruence)?.solution?.let { k ->
                val got = (0 until m).filter { t -> k.residues.any { res -> (t.toBigInteger() - res).mod(k.modulus).signum() == 0 } }
                got == expected
            } ?: (if (expected.isEmpty()) true else null))
        }
        // Recurrences aₙ₊₂ = p·aₙ₊₁ + q·aₙ with whole roots, against their terms.
        repeat(150) {
            val r1 = r.small(-3, 3, nonzero = true); val r2 = r.small(-3, 3, nonzero = true)
            val p = r1 + r2; val q = -r1 * r2
            val a0 = r.small(); val a1 = r.small()
            val latex = "\\begin{cases}a_{n+2}=${p}a_{n+1}${term(q, "a_{n}")}\\\\a_{0}=$a0\\\\a_{1}=$a1\\end{cases}".replace("=0a_{n+1}", "=").replace("=+", "=")
            val o = MathCalc.calculate(latex).outcome
            check("recurrence $latex", (o as? Outcome.RecurrenceSolved)?.recurrence?.let { rec ->
                val u = arrayListOf(a0.toLong(), a1.toLong())
                while (u.size < 10) u += p * u[u.size - 1] + q * u[u.size - 2]
                u.indices.all { n -> Numeric.eval(rec.closed, mapOf("n" to n.toDouble())).let { near(it, u[n].toDouble(), 1e-6) } }
            })
        }
        // Triangles from three sides: the law of cosines and the angle sum.
        repeat(150) {
            val s = List(3) { r.nextInt(2, 15) }.sorted()
            if (s[0] + s[1] <= s[2]) return@repeat
            val latex = "\\begin{cases}a=${s[0]}\\\\b=${s[1]}\\\\c=${s[2]}\\end{cases}"
            val o = MathCalc.calculate(latex).outcome
            check("triangle $latex", (o as? Outcome.Triangles)?.triangles?.singleOrNull()?.let { t ->
                val g = t.angles.map { Numeric.value(it)!! }
                near(g.sum(), 180.0, 1e-9) && near(s[2] * s[2].toDouble(), s[0] * s[0] + s[1] * s[1] - 2.0 * s[0] * s[1] * kotlin.math.cos(Math.toRadians(g[2])), 1e-9)
            })
        }
        // Units there and back.
        val pairs = listOf("km" to "mi", "m" to "ft", "kg" to "lb", "L" to "gal", "km/h" to "m/s", "°C" to "°F", "kWh" to "J", "atm" to "psi", "h" to "min", "°" to "rad")
        repeat(150) {
            val (a, b) = pairs.random(r)
            val v = r.nextInt(1, 500)
            val there = MathCalc.calculate("$v", Choice(Mode.UNITS, a, b)).outcome as? Outcome.Converted
            val back = there?.let { Units.convert(it.result, b, a) }
            check("$v $a in $b", back?.let { near(Numeric.value(it)!!, v.toDouble(), 1e-12) })
        }
        val report = "generated $total, declined $declined, wrong ${failures.size}"
        println(report)
        declinedTasks.forEach { println("declined: $it") }
        assertEquals("", failures.take(30).joinToString("\n"))
        // The engine may decline little of what it is asked here.
        assert(declined * 20 <= total) { report }
    }
}

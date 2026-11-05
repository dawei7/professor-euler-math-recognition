package com.professoreuler.mathcalc

/** An outcome as a line of text, to compare in tests. */
internal fun answerOf(c: Calculation): String = when (val o = c.outcome) {
    is Outcome.Value -> (o.exact?.let { c.textValue(it) } ?: c.text(o.value)) + steps(o.steps) +
        (if (o.others.isNotEmpty()) " | " + o.others.joinToString(" | ") { c.textValue(it) } else "")
    is Outcome.Solved -> solutions(c, o.solutions)
    is Outcome.Checked -> "${o.holds}"
    is Outcome.Expression -> c.text(o.result) + (if (o.plusC) " + C" else "") + steps(o.steps) +
        (if (o.excluded.isNotEmpty()) " (" + o.excluded.joinToString(", ") { (x, v) -> "$x ≠ " + c.text(v) } + ")" else "")
    is Outcome.Factored -> c.text(o.result) + when {
        !o.complete -> " (perhaps more)"
        o.trivial -> " (no factors)"
        else -> ""
    }
    is Outcome.Compared -> (if (o.holds) "equal: " else "differ: ") + o.sides.joinToString(" | ") { c.text(it) }
    is Outcome.Primes -> c.text(o)
    is Outcome.Intervals -> c.text(o.set)
    is Outcome.SystemSolved -> if (o.solutions.isEmpty()) "no solution" else o.solutions.joinToString("; ") { s ->
        o.letters.joinToString(", ") { "$it = ${c.textValue(s.getValue(it))}" }
    }
    is Outcome.DefiniteIntegral -> o.exact?.let { c.textValue(it) } ?: "≈ ${o.value}"
    is Outcome.Graph -> "graph of " + o.functions.joinToString(" | ") { c.text(it) } + (o.implicit?.let { "implicit " + c.text(it) } ?: "")
    is Outcome.LinesChecked -> o.lines.joinToString(" | ") { l ->
        when (l.holds) { true -> "✓"; false -> "✗"; null -> if (l.unreadable) "?" else "·" } +
            (if (l.lost.isNotEmpty()) " lost " + l.lost.joinToString(";") { c.text(it) } else "") +
            (if (l.gained.isNotEmpty()) " gained " + l.gained.joinToString(";") { c.text(it) } else "")
    }
    is Outcome.Failed -> o.failure.name
    is Outcome.Critical -> if (o.points.isEmpty()) "no critical points" else o.points.joinToString("; ") { p ->
        "(" + o.letters.joinToString(", ") { c.text(p.at.getValue(it)) } + ") " + p.kind.name.lowercase() + " " + c.textValue(p.value)
    }
    is Outcome.Surface -> "surface " + c.text(o.function)
    is Outcome.FourierSeries -> "a0 = " + c.text(o.series.a0) + ", an = " + c.text(o.series.an) + ", bn = " + c.text(o.series.bn)
    is Outcome.NumericTable -> o.table.method.name.lowercase() + " " + o.table.rows.size + " rows → " + (o.table.result?.let { "%.10f".format(java.util.Locale.ROOT, it) } ?: "none")
    is Outcome.PolarForm -> "r = " + c.text(o.polar.modulus) + ", φ = " + c.text(o.polar.argument)
    is Outcome.Residues -> o.poles.joinToString("; ") { "at " + c.text(it.at) + " order " + it.order + " residue " + c.text(it.residue) }
    is Outcome.OdeSolved -> {
        fun of(s: Ode.Solution) = s.explicit?.let { "${o.y} = " + c.text(it) } ?: ("0 = " + c.text(s.implicit!!))
        of(o.general) + " [" + o.general.method.name.lowercase() + "]" + (o.particular?.let { " | " + of(it) } ?: "")
    }
    is Outcome.Decompositions -> listOfNotNull(
        o.lu?.let { (p, l, u) -> "P=" + c.text(p) + " L=" + c.text(l) + " U=" + c.text(u) },
        o.qr?.let { (q, r) -> "Q=" + c.text(q) + " R=" + c.text(r) },
        o.cholesky?.let { "C=" + c.text(it) },
    ).joinToString(" | ")
    is Outcome.Constrained -> o.candidates.joinToString("; ") { k -> "(" + o.letters.joinToString(", ") { c.text(k.at.getValue(it)) } + ") " + c.textValue(k.value) }
    is Outcome.LeastSquares -> o.letters.joinToString(", ") { "$it = " + c.text(o.solution.getValue(it)) } + " residual " + c.textValue(o.residual)
    is Outcome.SystemCases -> {
        val k = o.cases
        val generic = k.generic?.let { g -> k.unknowns.joinToString(", ") { "$it = " + c.text(g.getValue(it)) } }
        "otherwise: $generic" + k.special.joinToString("") { s ->
            " | ${k.parameter} = ${c.text(s.value)}: " + (s.solution?.let { sol -> k.unknowns.joinToString(", ") { "$it = " + c.text(sol.getValue(it)) } } ?: "none")
        }
    }
    is Outcome.RowReduced -> c.text(o.form) + " rank ${o.rank}" + (if (o.nullSpace.isNotEmpty()) " null " + o.nullSpace.joinToString(" ") { c.text(it) } else "")
    is Outcome.Eigenvalues -> o.values.joinToString("; ") { e ->
        c.text(e.value) + (if (e.multiplicity > 1) " (${e.multiplicity}×)" else "") + ": " + e.vectors.joinToString(" ") { c.text(it) }
    }
}

internal fun lines(vararg readings: String): String = answerOf(MathCalc.lines(readings.toList()))

private fun solutions(c: Calculation, s: Solutions): String = when {
    s.many -> "MANY_SOLUTIONS"
    s.always -> "${s.variable}: always"
    s.periodic.isNotEmpty() -> "${s.variable} = " + s.periodic.joinToString("; ") { if (s.degrees) c.textAngle(it) else c.text(it) } + " (k)"
    s.values.isEmpty() -> "${s.variable}: none" + (if (s.complex.isNotEmpty()) " (complex " + s.complex.joinToString("; ") { c.text(it) } + ")" else "")
    else -> "${s.variable} = " + s.values.joinToString("; ") { c.textValue(it) } +
        (if (s.rejected.isNotEmpty()) " (rejected " + s.rejected.joinToString("; ") { c.textValue(it) } + ")" else "") +
        (if (s.conditions.isNotEmpty()) " (if " + s.conditions.joinToString(", ") { c.text(it) + " ≠ 0" } + ")" else "")
}

private fun steps(steps: List<Boolean>) = if (steps.isNotEmpty()) " (steps $steps)" else ""

internal fun answer(latex: String, mode: Mode? = null): String = answerOf(MathCalc.calculate(latex, mode))

internal fun answer(latex: String, choice: Choice): String = answerOf(MathCalc.calculate(latex, choice))

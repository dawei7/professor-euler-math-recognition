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
    is Outcome.NumberFacts -> o.facts.let { f ->
        (if (f.prime) "prime" else c.text(Outcome.Primes(f.n, f.factors))) + " d=${f.divisorCount} σ=${f.sigma} φ=${f.phi} μ=${f.mu} next ${f.nextPrime} bin ${f.binary}"
    }
    is Outcome.Congruence -> o.solution.let { k -> if (k.residues.isEmpty()) "none" else "${k.letter} ≡ " + k.residues.joinToString(", ") + " (mod ${k.modulus})" }
    is Outcome.WholeSolutions -> when (val k = o.solution) {
        is Discrete.Integers.Linear -> "${k.x} = ${k.x0} + ${k.sx}t, ${k.y} = ${k.y0} + ${k.sy}t"
        is Discrete.Integers.Pell -> "${k.x} = ${k.x1}, ${k.y} = ${k.y1} (d = ${k.d})"
        Discrete.Integers.None -> "none"
    }
    is Outcome.RecurrenceSolved -> o.recurrence.let { r ->
        "${r.name}_n = " + c.text(r.closed) + (if (r.terms.isNotEmpty()) " | " + r.terms.joinToString(", ") { c.text(it) } else "")
    }
    is Outcome.SequenceRule -> (o.rule.formula?.let { "a_n = " + c.text(it) } ?: "no formula") +
        (o.rule.recurrence?.let { r -> " rec " + r.joinToString(", ") { c.text(it) } } ?: "") + " next " + o.rule.next.joinToString(", ") { c.text(it) }
    is Outcome.LogicTable -> o.analysis.let { a ->
        (when { a.tautology -> "tautology "; a.contradiction -> "contradiction "; else -> "" }) + a.values.joinToString("") { if (it) "1" else "0" } +
            " dnf " + Logic.text(a.dnf, a.sets) + " cnf " + Logic.text(a.cnf, a.sets) + (a.equivalent?.let { " equivalent $it" } ?: "")
    }
    is Outcome.SetValue -> "{" + o.elements.joinToString(", ") { c.text(it) } + "}"
    is Outcome.Statistics -> o.summary.let { k ->
        "n=${k.n} mean " + c.text(k.mean) + " median " + c.text(k.median) + " sd " + (k.sampleSd?.let { c.text(it) } ?: "-") + " q1 " + c.text(k.q1) + " q3 " + c.text(k.q3)
    }
    is Outcome.Regression -> "y = " + c.text(o.line.slope) + "x + " + c.text(o.line.intercept) + " r " + (o.line.r?.let { c.text(it) } ?: "undefined")
    is Outcome.Converted -> c.text(o.result) + " ${o.to}"
    is Outcome.Triangles -> o.triangles.joinToString(" | ") { t ->
        "sides " + t.sides.joinToString(", ") { c.text(it) } + " angles " + t.angles.joinToString(", ") { c.text(it) } + " area " + c.text(t.area)
    }
    is Outcome.ConicSection -> o.conic.let { k ->
        fun pt(p: Geometry.Point) = "(" + c.text(p.x) + ", " + c.text(p.y) + ")"
        k.kind.name.lowercase() + (k.centre?.let { " centre " + pt(it) } ?: "") + (k.semiAxes?.let { " axes " + c.text(it.first) + ", " + c.text(it.second) } ?: "") +
            (if (k.foci.isNotEmpty()) " foci " + k.foci.joinToString(" ") { pt(it) } else "") + (k.eccentricity?.let { " e " + c.text(it) } ?: "") +
            (k.vertex?.let { " vertex " + pt(it) } ?: "") + (k.directrix?.let { " directrix ${it.first} = " + c.text(it.second) } ?: "") +
            (if (k.asymptotes.isNotEmpty()) " asymptotes " + k.asymptotes.joinToString(" ") { c.text(it) } else "") +
            (k.angle?.let { " angle " + c.text(it) } ?: "")
    }
    is Outcome.LineThrough -> o.line.let { l -> (l.equation?.let { "${it.first} = " + c.text(it.second) } ?: ("direction " + c.text(l.direction))) + " distance " + c.text(l.distance) }
    is Outcome.PlaneThrough -> c.text(o.plane.equation) + " = 0"
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

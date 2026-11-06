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
    is Outcome.Regression -> "y = " + c.text(o.line.slope) + "x + " + c.text(o.line.intercept) + " r " + c.text(o.line.r)
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
    is Outcome.PhysicalConstant -> o.constant.name + " = " + PhysicalConstants.text(o.quantity) + (if (o.unit.isEmpty()) "" else " " + o.unit) +
        (if (o.quantity.exact) " exact" else "")
    is Outcome.PhysicalValue -> PhysicalConstants.text(o.quantity) + " from " + o.constants.joinToString(", ") +
        (if (o.quantity.exact) " exact" else "")
    is Outcome.DomainColoring -> o.plot.let { p ->
        fun pts(l: List<ComplexPlot.Point>) = l.joinToString("; ") { (if (it.exact) c.text(it.at) else "≈" + ComplexPlot.text(it)) + (if (it.order > 1) " (${it.order}×)" else "") }
        "square " + c.text(p.region.center) + " ± " + c.text(p.region.halfWidth) + " zeros " + pts(p.zeros).ifEmpty { "none" } +
            " poles " + pts(p.poles).ifEmpty { "none" }
    }
    is Outcome.Venn -> o.diagram.let { d ->
        d.sets.joinToString("") + ": " + d.regions.filter { it.included }.joinToString(", ") { it.text }.ifEmpty { "∅" } +
            " simplest " + Logic.text(d.simplest, true)
    }
    is Outcome.SingularValues -> o.svd.let { s ->
        "σ " + s.values.joinToString(", ") { c.text(it) } + " U=" + c.text(s.u) + " S=" + c.text(s.sigma) + " V=" + c.text(s.v) + (if (s.exact) "" else " (decimals)")
    }
    is Outcome.JordanForm -> o.jordan.let { j -> "J=" + c.text(j.j) + " P=" + c.text(j.p) + " blocks " + j.blocks.joinToString(", ") { c.text(it.value) + "×" + it.size } }
    is Outcome.OdeSystemSolved -> o.system.let { s ->
        fun of(v: List<Expr>) = s.unknowns.indices.joinToString(", ") { "${s.unknowns[it]} = " + c.text(v[it]) }
        of(s.general) + (s.particular?.let { " | " + of(it) } ?: "") + (s.equilibrium?.let { " | " + equilibrium(c, it) } ?: "")
    }
    is Outcome.SeriesSolved -> o.solution.let { s ->
        "${s.y} = " + c.textResult(Outcome.Expression(Expr.ZERO, series = s.terms, seriesIn = s.seriesIn, remainder = pow(s.seriesIn, num(s.order + 1L)))) +
            (s.recurrence?.let { (l, r) -> " | " + c.text(l) + " = " + c.text(r) } ?: "")
    }
    is Outcome.BoundaryValueSolved -> o.problem.let { p ->
        when (p.kind) {
            BoundaryValue.Kind.UNIQUE -> "unique ${p.y} = " + c.text(p.solution!!)
            BoundaryValue.Kind.NONE -> "none"
            BoundaryValue.Kind.INFINITE -> "infinitely many ${p.y} = " + c.text(p.solution!!) + " (" + p.free.joinToString(", ") + " free)"
        }
    }
    is Outcome.DirectionField -> o.field.let { f ->
        (if (f.kind == Fields.FieldKind.SLOPE) "slope field ${f.y}' = " + c.text(f.dy) else "phase portrait ${f.x}' = " + c.text(f.dx) + ", ${f.y}' = " + c.text(f.dy)) +
            (if (f.constants.isNotEmpty()) " | constant " + f.constants.joinToString(", ") { "${f.y} = " + c.text(it) } else "") +
            (if (f.equilibria.isNotEmpty()) " | " + f.equilibria.joinToString("; ") { equilibrium(c, it) } else "")
    }
    is Outcome.Interpolated -> o.interpolant.let { p -> "p(${p.letter}) = " + c.text(p.polynomial) + " newton " + p.newton.joinToString(", ") { c.text(it) } }
    is Outcome.SplineThrough -> o.spline.pieces.joinToString(" | ") { p -> "[" + c.text(p.from) + ", " + c.text(p.to) + "] " + c.text(p.polynomial) }
    is Outcome.DivideAndConquer -> o.analysis.let { a ->
        (if (a.method == DivideConquer.Method.MASTER) "master " else "akra-bazzi ") +
            (a.case?.let { "case $it " } ?: "") + (a.gap?.let { "no case: ${it.name.lowercase()} " } ?: "") +
            (a.critical?.let { "p = " + c.text(it) + " " } ?: a.criticalDigits?.let { "p ≈ " + Algorithms.decimal(it, c, latex = false) + " " } ?: "") +
            (a.order?.let { "Θ(" + c.text(it) + ")" } ?: "")
    }.trim()
    is Outcome.Growth -> o.growth.let { g ->
        if (!g.sorted) "lim = " + c.text(g.limits[0]) + " " + g.relations().joinToString(",") { it.name.lowercase() }
        else g.order.indices.joinToString("") { k ->
            (if (k == 0) "" else if (isZero(g.limits[k - 1])) " < " else " ~ ") + c.text(g.functions[g.order[k]])
        }
    }
    is Outcome.DynamicTable -> o.table.let { t ->
        (t.value?.let { c.text(it) } ?: "none") + when (t.problem) {
            Dynamic.Problem.EDIT_DISTANCE -> " " + t.operations.filter { it.edit != Dynamic.Edit.KEEP }.joinToString(" ") { op ->
                op.edit.name.lowercase() + " " + (op.from ?: "") + (if (op.from != null && op.to != null) "→" else "") + (op.to ?: "")
            }
            Dynamic.Problem.LCS -> " " + t.subsequence
            Dynamic.Problem.KNAPSACK -> " items " + t.items.joinToString(",") + " weight " + c.text(t.weight!!)
            Dynamic.Problem.COIN_CHANGE -> " coins " + t.coins.joinToString(",") { c.text(it) }
            Dynamic.Problem.COIN_WAYS -> ""
        }
    }.trim()
    is Outcome.HuffmanCode -> o.code.let { h ->
        h.symbols.indices.joinToString(" ") { h.symbols[it] + "=" + h.codes[it] } + " L = " + c.text(h.average) + " H = " + c.textValue(h.entropy)
    }
    is Outcome.Trace -> o.trace.let { t ->
        if (t.algorithm == Sorting.Algorithm.BINARY_SEARCH) "probes " + t.steps.joinToString(",") { it.at.toString() } + " " + (t.found?.let { "found at $it" } ?: "absent")
        else t.result.joinToString(", ") { c.text(it) } + " comparisons ${t.comparisons}" + (t.swaps?.let { " swaps $it" } ?: "") + " steps ${t.steps.size}"
    }
}

private fun equilibrium(c: Calculation, e: Fields.Equilibrium): String =
    "(" + e.at.joinToString(", ") { c.text(it) } + ") " + e.kind.name.lowercase() + when (e.stable) { true -> " stable"; false -> " unstable"; null -> "" }

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

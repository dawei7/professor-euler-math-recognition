package com.professoreuler.mathcalc

/** An outcome as a line of text, to compare in tests. */
internal fun answerOf(c: Calculation): String = when (val o = c.outcome) {
    is Outcome.Value -> c.text(o.value) + steps(o.steps)
    is Outcome.Solved -> when {
        o.always -> "${o.unknown}: always"
        o.roots.isEmpty() -> "${o.unknown}: none"
        else -> "${o.unknown} = " + o.roots.joinToString("; ") { c.text(it) }
    }
    is Outcome.Checked -> "${o.holds}"
    is Outcome.Simplified -> c.text(o.polynomial) + steps(o.steps)
    is Outcome.Factored -> c.text(o.product) + when {
        !o.product.complete -> " (perhaps more)"
        o.product.trivial -> " (no factors)"
        else -> ""
    }
    is Outcome.Compared -> (if (o.holds) "equal: " else "differ: ") + o.sides.joinToString(" | ") { c.text(it) }
    is Outcome.Primes -> c.text(o)
    is Outcome.Failed -> o.failure.name
}

private fun steps(steps: List<Boolean>) = if (steps.isNotEmpty()) " (steps $steps)" else ""

internal fun answer(latex: String, mode: Mode? = null): String = answerOf(MathCalc.calculate(latex, mode))

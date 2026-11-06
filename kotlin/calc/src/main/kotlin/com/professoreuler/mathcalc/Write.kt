package com.professoreuler.mathcalc

import java.math.BigDecimal

/**
 * An expression written out: LaTeX for the card, or the sheets' math shorthand (`x^2 + 4x + 4`, `√2`, `(5/6)x`, `sin(x)`)
 * that a text box shows with Unicode. Sums highest degree first and numbers last, as a learner writes them; a number's
 * fraction before what it multiplies, other denominators under a fraction bar; roots as roots; numbers in the formula's style.
 */
internal class Write(private val style: Style, private val latex: Boolean) {

    fun expr(e: Expr): String = when (e) {
        is Expr.Add -> sum(e)
        is Expr.Mul -> product(e.factors)
        is Expr.Pow -> if (negativeExponent(e)) product(listOf(e)) else power(e)
        is Expr.Num -> number(e.value)
        is Expr.Real -> real(e.value)
        is Expr.Sym -> Format.name(e.name, latex)
        is Expr.Const -> when (e.kind) {
            Constant.PI -> if (latex) "\\pi" else "π"
            Constant.E -> "e"
            Constant.I -> "i"
            Constant.INF -> if (latex) "\\infty" else "∞"
        }
        is Expr.Fn -> function(e)
        is Expr.Op -> operation(e)
        is Expr.Mat -> matrix(e)
    }

    /** A matrix as LaTeX writes it (pmatrix); in the shorthand a vector as (1, 2, 3) and a matrix as [[1, 2], [3, 4]]. */
    private fun matrix(m: Expr.Mat): String = when {
        latex -> "\\begin{pmatrix}" + m.rows.joinToString(" \\\\ ") { r -> r.joinToString(" & ") { expr(it) } } + "\\end{pmatrix}"
        m.columns == 1 -> "(" + m.rows.joinToString(", ") { expr(it[0]) } + ")"
        else -> "[" + m.rows.joinToString(", ") { r -> "[" + r.joinToString(", ") { expr(it) } + "]" } + "]"
    }

    /** Calculus as written: d/dx (…), ∫ with its bounds and dx, lim with its point, Σ and Π with their bounds. */
    private fun operation(o: Expr.Op): String {
        val v = Format.name(o.variable.name, latex)
        val body = expr(o.body).let { if (o.body is Expr.Add) paren(it) else it }
        return when (o.kind) {
            OpKind.DERIVATIVE -> {
                val n = integer(o.a ?: Expr.ONE)?.toInt() ?: 1
                if (latex) (if (n == 1) "\\frac{d}{d$v}" else "\\frac{d^{$n}}{d$v^{$n}}") + paren(expr(o.body))
                else (if (n == 1) "d/d$v" else "d^$n/d$v^$n") + "(" + expr(o.body) + ")"
            }
            OpKind.INTEGRAL -> {
                val bounds = if (o.a != null && o.b != null) (if (latex) "_{${expr(o.a)}}^{${expr(o.b)}}" else "_(${expr(o.a)})^(${expr(o.b)})") else ""
                if (latex) "\\int$bounds $body\\,d$v" else "∫$bounds $body d$v"
            }
            OpKind.LIMIT -> {
                val side = when (o.b?.let { rational(it)?.signum }) { -1 -> "^{-}"; 1 -> "^{+}"; else -> "" }
                val point = expr(o.a ?: Expr.ZERO)
                if (latex) "\\lim_{$v \\to $point$side} $body" else "lim($v → $point${side.replace("^{", "").replace("}", "")}) $body"
            }
            OpKind.SUM, OpKind.PRODUCT -> {
                val sign = if (o.kind == OpKind.SUM) (if (latex) "\\sum" else "Σ") else (if (latex) "\\prod" else "Π")
                val from = expr(o.a ?: Expr.ONE)
                val to = expr(o.b ?: Expr.INF)
                if (latex) "${sign}_{$v=$from}^{$to} $body" else "$sign($v=$from..$to) $body"
            }
        }
    }

    // Sums.

    private fun sum(e: Expr.Add): String {
        val out = StringBuilder()
        for ((k, t) in displayOrder(e.terms).withIndex()) {
            val negative = isNegative(t)
            val shown = if (negative) neg(t) else t
            val text = expr(shown).let { if (shown is Expr.Add) paren(it) else it }
            when {
                k == 0 -> out.append(if (negative) "-$text" else text)
                else -> out.append(if (negative) " - " else " + ").append(text)
            }
        }
        return out.toString()
    }

    /**
     * Highest degree first (x² + x + 1), the number last; of one degree a positive term before a negative one (c − b),
     * then in dictionary order (x² + xy + y²). A sum of numbers starts with its rational part (1 − √2, 5 + i); a family's
     * term in k comes last (π/6 + 2kπ).
     */
    private fun displayOrder(terms: List<Expr>): List<Expr> {
        val constant = terms.all { it.symbols().isEmpty() }
        return terms.sortedWith(compareBy<Expr> { if (it.has(K)) 1 else 0 }
            // A complex number's real part before its imaginary one, a decimal too (−0.727 + 0.430i, not 0.430i − 0.727).
            .thenByDescending { if (constant) (if (it is Expr.Num) 2.0 else if (!it.hasI()) 1.0 else 0.0) else degree(it) }
            .thenBy { if (isNegative(it) && degree(it) <= 1.0) 1 else 0 }
            .thenComparator { a, b ->
                lexicographic(splitCoefficient(a).second, splitCoefficient(b).second) ?: Order.compare(splitCoefficient(a).second, splitCoefficient(b).second)
            })
    }

    /** Products of letters of one degree in dictionary order: the higher power of the earlier letter first (x², xy, xz, y²). */
    private fun lexicographic(a: Expr, b: Expr): Int? {
        fun powers(t: Expr): Map<String, Int>? {
            val out = HashMap<String, Int>()
            for (f in if (t is Expr.Mul) t.factors else listOf(t)) {
                when {
                    f is Expr.Sym -> out[f.name] = (out[f.name] ?: 0) + 1
                    f is Expr.Pow && f.base is Expr.Sym && integer(f.exp)?.let { it.signum() > 0 && it.bitLength() < 31 } == true ->
                        out[(f.base as Expr.Sym).name] = (out[(f.base as Expr.Sym).name] ?: 0) + integer(f.exp)!!.toInt()
                    else -> return null
                }
            }
            return out
        }
        val pa = powers(a) ?: return null
        val pb = powers(b) ?: return null
        for (v in (pa.keys + pb.keys).sorted()) {
            val d = (pb[v] ?: 0).compareTo(pa[v] ?: 0)
            if (d != 0) return d
        }
        return 0
    }

    private fun degree(t: Expr): Double = when (t) {
        is Expr.Num, is Expr.Real -> -1000.0
        is Expr.Const -> 0.0
        is Expr.Sym -> 1.0
        is Expr.Pow -> if (t.base is Expr.Sym) (rational(t.exp)?.toDouble() ?: 0.5) else 0.5
        is Expr.Mul -> t.factors.sumOf { if (isNumber(it)) 0.0 else degree(it).coerceAtLeast(0.0) }
        else -> 0.5
    }

    // Products and quotients.

    private fun negativeExponent(p: Expr.Pow) = rational(p.exp)?.let { it.signum < 0 } ?: (p.exp is Expr.Mul && isNegative(p.exp))

    private fun product(factors: List<Expr>): String {
        var coefficient = Rational.ONE
        var real: Double? = null
        val top = ArrayList<Expr>()
        val bottom = ArrayList<Expr>()
        for (f in factors) {
            when {
                f is Expr.Num -> coefficient *= f.value
                f is Expr.Real -> real = (real ?: 1.0) * f.value
                f is Expr.Pow && negativeExponent(f) -> bottom += pow(f.base, neg(f.exp))
                else -> top += f
            }
        }
        val sign = if (coefficient.signum < 0 || (real ?: 1.0) < 0) "-" else ""
        coefficient = coefficient.abs()
        val realPart = real?.let { kotlin.math.abs(it) }
        // A number made of constants and roots with a fraction is written as one: √2/2, π/6, 2π/3.
        if (bottom.isEmpty() && realPart == null && !coefficient.isInteger && top.isNotEmpty() && top.all { it.symbols().isEmpty() }) {
            val above = joinFactors(if (coefficient.num == java.math.BigInteger.ONE) "" else coefficient.num.toString(), top)
            val below = coefficient.den.toString()
            return sign + if (latex) "\\frac{$above}{$below}" else "${grouped(above, 1, denominator = false)}/$below"
        }
        if (bottom.isEmpty()) {
            // A number's fraction stands before what it multiplies: (5/6)x, \frac{5}{6}x.
            val number = when {
                realPart != null -> real(realPart * coefficient.toDouble())
                coefficient == Rational.ONE -> ""
                else -> Format.coefficient(coefficient, style, latex, alone = top.isEmpty())
            }
            return sign + joinFactors(number, top)
        }
        // Otherwise one fraction bar: the number's numerator above, its denominator below.
        val above = barPart(if (coefficient.num == java.math.BigInteger.ONE) "" else coefficient.num.toString(), top).ifEmpty { "1" }
        val belowFactors = bottom
        val below = barPart(if (coefficient.den == java.math.BigInteger.ONE) "" else coefficient.den.toString(), belowFactors)
        return sign + if (latex) "\\frac{$above}{$below}" else "${grouped(above, 1, denominator = false)}/${grouped(below, belowFactors.size + (if (coefficient.den == java.math.BigInteger.ONE) 0 else 1))}"
    }

    /**
     * In text, a denominator of more than one factor, and a numerator or denominator with a sign or a sum outside its own
     * brackets, in brackets: 1/(2x), (2x + 1)/(x(x + 1)), but 5π/6 and 1/(x + 1)^2.
     */
    private fun grouped(s: String, count: Int, denominator: Boolean = true): String {
        var depth = 0
        var open = false
        for (ch in s) {
            when (ch) {
                '(' -> depth++
                ')' -> depth--
                ' ' -> if (depth == 0) open = true
            }
        }
        return if (open || s.startsWith("-") || (denominator && count > 1)) "($s)" else s
    }

    /** Above or below a LaTeX fraction bar a lone sum needs no brackets: \frac{2x + 1}{x^{2}}. */
    private fun barPart(number: String, factors: List<Expr>): String =
        if (latex && number.isEmpty() && factors.size == 1 && factors[0] is Expr.Add) expr(factors[0]) else joinFactors(number, factors)

    private fun joinFactors(number: String, factors: List<Expr>): String {
        val out = StringBuilder(number)
        val ordered = factors.sortedWith(compareBy<Expr>({ factorRank(it) }, { bracketKey(it).first }, { bracketKey(it).second },
            { bracketKey(it).third }).thenComparator { a, b -> coefficientOrder(a, b) }.thenComparator { a, b -> Order.compare(a, b) })
        for (f in ordered) {
            val text = factorText(f)
            // A factor that begins with a digit needs a dot: 2·3^x; in text a function after a letter or digit too: 2x·cos(x).
            val dot = out.isNotEmpty() && (text.first().isDigit() || (out.last().isDigit() && text.first() == '.') ||
                (!latex && f is Expr.Fn && f.f != Func.ABS && out.last().isLetterOrDigit()))
            if (dot) out.append(if (latex) " \\cdot " else "·")
            // A command before a letter needs a space, or the two read as one: \pi r^{2}, not \pir^{2}.
            else if (latex && text.first().isLetter() && COMMAND_END.containsMatchIn(out)) out.append(' ')
            out.append(text)
        }
        return out.toString()
    }

    /**
     * Brackets as a learner orders them: lower degree first; of linear ones, the smaller root first and x − r before
     * x + r ((x − 2)(x − 3), (x − 1)(x + 1)); then fewer terms.
     */
    private fun bracketKey(f: Expr): Triple<Int, Double, Int> {
        val sum = (if (f is Expr.Pow) f.base else f) as? Expr.Add ?: return Triple(0, 0.0, 0)
        val letters = sum.symbols()
        if (letters.isEmpty()) return Triple(0, 0.0, sum.terms.size)
        val x = Expr.Sym(if ("x" in letters) "x" else letters.first())
        val c = Solve.coefficientsIn(sum, x)
        val degree = (c?.size ?: 99) - 1
        if (c != null && degree == 1) {
            // x − 3y has its "root" 3y: other letters taken as 1.
            val others = (letters - x.name).associateWith { 1.0 }
            val root = Numeric.eval(neg(c[0]) / c[1], others).takeIf { it.isFinite() }
            if (root != null) return Triple(1, kotlin.math.abs(root), if (root > 0) 0 else 1)
        }
        return Triple(degree, Double.MAX_VALUE, sum.terms.size)
    }

    /** Of two brackets alike so far, the one whose coefficients (highest power first) are smaller: x² − x + 1 before x² + x + 1. */
    private fun coefficientOrder(a: Expr, b: Expr): Int {
        fun coefficients(f: Expr): List<Double>? {
            val sum = (if (f is Expr.Pow) f.base else f) as? Expr.Add ?: return null
            val letters = sum.symbols()
            if (letters.isEmpty()) return null
            val x = Expr.Sym(if ("x" in letters) "x" else letters.first())
            val others = (letters - x.name).associateWith { 1.0 }
            return Solve.coefficientsIn(sum, x)?.map { Numeric.eval(it, others) }?.asReversed()
        }
        val ca = coefficients(a) ?: return 0
        val cb = coefficients(b) ?: return 0
        for ((p, q) in ca.zip(cb)) if (p != q) return p.compareTo(q)
        return ca.size.compareTo(cb.size)
    }

    /** Constants, then letters and their powers, then roots and functions, then brackets. */
    private fun factorRank(f: Expr): Int = when {
        f == K -> -1
        f is Expr.Const -> 0
        f is Expr.Sym -> 1
        f is Expr.Pow && f.base is Expr.Sym -> 1
        f is Expr.Pow && (f.base is Expr.Num || f.base is Expr.Const) -> 2
        f is Expr.Fn -> 3
        f is Expr.Pow -> 3
        else -> 4
    }

    private fun factorText(f: Expr): String = when (f) {
        is Expr.Add -> paren(expr(f))
        else -> expr(f)
    }

    // Powers and roots.

    private fun power(p: Expr.Pow): String {
        val r = rational(p.exp)
        if (r != null && r.num == java.math.BigInteger.ONE && r.den.bitLength() < 31) {
            val n = r.den.toInt()
            val inside = expr(p.base)
            if (latex) return if (n == 2) "\\sqrt{$inside}" else "\\sqrt[$n]{$inside}"
            val radicand = if (simple(p.base)) inside else "($inside)"
            return when (n) {
                2 -> "√$radicand"
                3 -> "∛$radicand"
                4 -> "∜$radicand"
                else -> "$radicand^(1/$n)"
            }
        }
        // Trigonometric functions to a whole positive power: sin²(x).
        if (p.base is Expr.Fn && r != null && r.isInteger && r.signum > 0 && p.base.f in SQUARED_FUNCTIONS) {
            val arg = expr(p.base.args[0])
            return if (latex) "${p.base.f.latex}^{${r.num}}($arg)" else "${p.base.f.text}^${r.num}($arg)"
        }
        val base = expr(p.base).let { if (needsBrackets(p.base)) paren(it) else it }
        val exponent = expr(p.exp)
        return if (latex) "$base^{$exponent}" else if (simpleExponent(p.exp)) "$base^$exponent" else "$base^($exponent)"
    }

    private fun needsBrackets(b: Expr): Boolean = when (b) {
        is Expr.Add, is Expr.Mul, is Expr.Pow -> true
        is Expr.Num -> b.value.signum < 0 || !b.value.isInteger
        is Expr.Real -> b.value < 0
        is Expr.Fn -> b.f != Func.ABS && b.f != Func.FLOOR && b.f != Func.CEIL
        else -> false
    }

    private fun simple(e: Expr) = e is Expr.Sym || e is Expr.Const || (e is Expr.Num && e.value.isInteger && e.value.signum >= 0)

    private fun simpleExponent(e: Expr) = (e is Expr.Num && e.value.isInteger && e.value.signum >= 0) || e is Expr.Sym || e is Expr.Const

    // Functions.

    private fun function(f: Expr.Fn): String {
        val arg = expr(f.args.last())
        return when (f.f) {
            Func.ABS -> if (latex) "\\left|$arg\\right|" else "|$arg|"
            Func.FLOOR -> if (latex) "\\lfloor $arg\\rfloor" else "floor($arg)"
            Func.CEIL -> if (latex) "\\lceil $arg\\rceil" else "ceil($arg)"
            Func.FACTORIAL -> (if (simple(f.args[0])) arg else paren(arg)) + "!"
            Func.LOG -> {
                val base = expr(f.args[0])
                if (latex) "\\log_{$base}\\left($arg\\right)" else "log_${if (simple(f.args[0])) base else "($base)"}($arg)"
            }
            Func.BESSELJ -> {
                val order = expr(f.args[0])
                if (latex) "\\operatorname{J}_{$order}\\left($arg\\right)" else "besselj($order; $arg)"
            }
            Func.BESSELY -> {
                val order = expr(f.args[0])
                if (latex) "\\operatorname{Y}_{$order}\\left($arg\\right)" else "bessely($order, $arg)"
            }
            else -> if (latex) "${f.f.latex}\\left($arg\\right)" else "${f.f.text}($arg)"
        }
    }

    // Numbers.

    fun number(r: Rational): String = (if (r.signum < 0) "-" else "") + Format.coefficient(r.abs(), style, latex, alone = true)

    fun real(d: Double): String = Format.decimal(BigDecimal(d), style, latex)

    /** Brackets; in LaTeX growing with what they hold only where that is tall (a fraction, a root, a sum sign). */
    private fun paren(s: String) = when {
        !latex -> "($s)"
        TALL.any { it in s } -> "\\left($s\\right)"
        else -> "($s)"
    }

    companion object {
        private val TALL = listOf("\\frac", "\\sqrt", "\\sum", "\\int", "\\prod")

        /** LaTeX that ends in a command's name: \pi, \infty. */
        private val COMMAND_END = Regex("\\\\[a-zA-Z]+$")

        private val SQUARED_FUNCTIONS = setOf(Func.SIN, Func.COS, Func.TAN, Func.COT, Func.SEC, Func.CSC, Func.SINH, Func.COSH, Func.TANH) +
            setOf(Func.COTH, Func.SECH, Func.CSCH)

        /** A term that is written with a minus: a negative number, or a negative number times something. */
        fun isNegative(t: Expr): Boolean = when (t) {
            is Expr.Num -> t.value.signum < 0
            is Expr.Real -> t.value < 0
            is Expr.Mul -> isNegative(t.factors[0])
            else -> false
        }
    }
}

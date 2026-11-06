package com.professoreuler.mathcalc

/**
 * The calls of several variables' integrals and derivatives, and of the Fourier transform: read from a typed line
 * ([CommandFunctions]), written back as calls, and worked out. A double or triple integral in Cartesian coordinates is written as
 * the formula ∫∫ f dx dy (`integral(f, x, a, b, y, c, d)`), which the parser reads and [Multiple] works out; the others are kept as
 * their calls, `\operatorname{polarintegral}\left(…\right)`, which [calculate] reads argument by argument.
 */
internal object MultiCalls {
    /** The functions read here, by name: how many arguments each takes. */
    private val COUNTS = mapOf(
        "polarintegral" to setOf(7), "cylindricalintegral" to setOf(10), "sphericalintegral" to setOf(10),
        "lineintegral" to setOf(5), "surfaceintegral" to setOf(8), "flux" to setOf(5, 8),
        "directionalderivative" to setOf(3), "tangentplane" to setOf(2),
        "fouriertransform" to setOf(1, 2), "invfouriertransform" to setOf(1, 2),
    )

    val NAMES: Set<String> = COUNTS.keys

    // ---------------------------------------------------------------------------------------------------------------------
    // Reading a call.

    /** A call of one of [NAMES] with [args] as typed: kept as the call, with its function's mode; null where it cannot be one. */
    fun read(f: CommandFunctions.Function, args: List<String>): Typed? {
        val counts = COUNTS[f.name] ?: return null
        if (args.size !in counts) return null
        val pieces = args.map { TextInput.latex(it).trim() }
        if (pieces.any { it.isEmpty() }) return null
        return Typed(call(f.name, pieces), Choice(f.mode!!))
    }

    /**
     * integral(f, x, a, b, y, c, d) and with a third letter and its bounds: the iterated integral ∫_c^d ∫_a^b f dx dy, the first
     * letter innermost, as the differentials dx dy are read; each letter a single letter, each one once.
     */
    fun iterated(formula: String, params: List<String>): Typed? {
        if (params.size != 6 && params.size != 9) return null
        val groups = params.chunked(3)
        val letters = groups.map { g -> TextInput.letterOf(g[0])?.takeIf { it.length == 1 } ?: return null }
        if (letters.toSet().size != letters.size) return null
        var body = TextInput.latex(formula).trim()
        for ((k, g) in groups.withIndex()) {
            // A bound that is its own letter is no bound.
            if (g.drop(1).any { it.trim() == letters[k] }) return null
            body = "\\int_{${TextInput.latex(g[1]).trim()}}^{${TextInput.latex(g[2]).trim()}} $body \\, d${letters[k]}"
        }
        return Typed(body)
    }

    private fun call(name: String, pieces: List<String>) = "\\operatorname{$name}\\left(" + pieces.joinToString(", ") + "\\right)"

    // ---------------------------------------------------------------------------------------------------------------------
    // Writing a reading as its call.

    /** The call of [typed] where it is one of these: a nested ∫∫ as `integral(f, x, a, b, y, c, d)`, a kept call as itself. */
    fun latex(typed: Typed): String? {
        val l = typed.latex.trim()
        if (typed.choice == null) return nestedCall(l)
        return split(l)?.let { (name, pieces) -> call(name, pieces) }
    }

    /** ∫_c^d ∫_a^b f dx dy (two or three integrals) as the call integral(f, x, a, b, y, c, d). */
    private fun nestedCall(latex: String): String? {
        val groups = ArrayList<List<String>>()
        var s = latex.trim()
        while (s.startsWith("\\int_{")) {
            val lo = group(s, "\\int_".length) ?: return null
            var at = lo.second
            if (s.getOrNull(at) != '^') return null
            val hi = group(s, at + 1) ?: return null
            at = hi.second
            val m = DIFFERENTIAL.find(s) ?: return null
            if (m.range.first < at) return null
            groups += listOf(m.groupValues[1], lo.first, hi.first)
            s = s.substring(at, m.range.first).trim()
        }
        if (groups.size < 2 || groups.size > Multiple.MOST) return null
        return call("integral", listOf(s) + groups.reversed().flatten())
    }

    private val DIFFERENTIAL = Regex("""\s*\\,\s*d([A-Za-z])$""")

    /** The braced group at [at] in [s]: its inside and where it ends. */
    private fun group(s: String, at: Int): Pair<String, Int>? {
        if (s.getOrNull(at) != '{') return null
        var depth = 0
        for (k in at until s.length) {
            if (s[k] == '{') depth++
            if (s[k] == '}' && --depth == 0) return s.substring(at + 1, k) to k + 1
        }
        return null
    }

    /** A kept call's name and its arguments' LaTeX; null where [latex] is no call of these. */
    fun split(latex: String): Pair<String, List<String>>? {
        val m = CALL.matchEntire(latex.trim()) ?: return null
        val name = m.groupValues[1]
        if (name !in NAMES) return null
        val inner = m.groupValues[2]
        val out = ArrayList<String>()
        var depth = 0
        var start = 0
        var k = 0
        while (k < inner.length) {
            val c = inner[k]
            if (c == '(' || c == '[' || c == '{') depth++
            if (c == ')' || c == ']' || c == '}') depth--
            if (c == ',' && depth == 0) {
                out += inner.substring(start, k).trim()
                start = k + 1
            }
            k++
        }
        out += inner.substring(start).trim()
        return name to out
    }

    private val CALL = Regex("""^\\operatorname\{([a-z]+)\}\\left\((.*)\\right\)$""", RegexOption.DOT_MATCHES_ALL)

    // ---------------------------------------------------------------------------------------------------------------------
    // Working a call out.

    /** The calculation of a kept call ([MathCalc.calculate] asks here first); null where [latex] is no call of these. */
    fun calculate(latex: String, choice: Choice?, millis: Long, fractions: Boolean, assumptions: Assumptions): Calculation? {
        val (name, pieces) = split(latex) ?: return null
        val flags = Flags(assumptions)
        if (fractions) flags.fractions = true
        val mode = CommandFunctions.ALL.first { it.name == name }.mode!!
        val shown = Choice(mode)
        val outcome = try {
            if (pieces.size !in COUNTS.getValue(name) || pieces.any { it.isEmpty() }) throw CalcException(Failure.ARGUMENTS)
            Budget.within(millis) { Reader(pieces, flags).work(name) }
        } catch (e: CalcException) {
            Outcome.Failed(e.failure)
        } catch (e: ArithmeticException) {
            Outcome.Failed(Failure.UNDEFINED)
        } catch (e: StackOverflowError) {
            Outcome.Failed(Failure.TOO_LARGE)
        }
        return Calculation(outcome, flags.decimalComma, flags.decimals, flags.fractions, listOf(shown), shown, false, assumptions,
            flags.met.toSet())
    }

    /** The arguments of a call, read one by one. */
    class Reader(val pieces: List<String>, val flags: Flags) {
        /** The piece at [k] as an expression (its calculus worked out); [letters] are letters there, never e or i. */
        fun expr(k: Int, letters: Set<String> = emptySet()): Expr {
            val st = statement(k, letters)
            if (st.sides.size != 1 || st.sides[0] == null) throw CalcException(Failure.ARGUMENTS)
            return canonical(Calculus.evaluate(st.sides[0]!!))
        }

        fun statement(k: Int, letters: Set<String> = emptySet()): Statement {
            val tokens = MathCalc.TOKEN.findAll(pieces[k]).map { it.value }.toList()
            val statements = Parser(tokens, flags, 1, letters).statements()
            if (statements.size != 1 || statements[0].definition != null) throw CalcException(Failure.ARGUMENTS)
            return statements[0]
        }

        /** The piece at [k] as a letter: x, t, r, θ, φ. */
        fun letter(k: Int): Expr.Sym {
            val e = try { expr(k) } catch (x: CalcException) { throw CalcException(Failure.ARGUMENTS) }
            return e as? Expr.Sym ?: throw CalcException(Failure.ARGUMENTS)
        }

        /** The piece at [k] as a vector of [sizes] entries. */
        fun vector(k: Int, sizes: Set<Int>, letters: Set<String> = emptySet()): List<Expr> {
            val m = expr(k, letters) as? Expr.Mat ?: throw CalcException(Failure.ARGUMENTS)
            if (!LinAlg.isVector(m) || m.size !in sizes) throw CalcException(Failure.SIZES)
            return m.rows.map { it[0] }
        }

        fun work(name: String): Outcome = when (name) {
            "polarintegral" -> coordinates(Multiple.System.POLAR)
            "cylindricalintegral" -> coordinates(Multiple.System.CYLINDRICAL)
            "sphericalintegral" -> coordinates(Multiple.System.SPHERICAL)
            "lineintegral" -> VectorCalculus.line(this, flux = false)
            "surfaceintegral" -> VectorCalculus.surface(this, flux = false)
            "flux" -> if (pieces.size == 5) VectorCalculus.line(this, flux = true) else VectorCalculus.surface(this, flux = true)
            "directionalderivative" -> VectorCalculus.directional(this)
            "tangentplane" -> VectorCalculus.tangentPlane(this)
            "fouriertransform" -> FourierTransform.call(this, inverse = false)
            "invfouriertransform" -> FourierTransform.call(this, inverse = true)
            else -> throw CalcException(Failure.ARGUMENTS)
        }

        /** polarintegral(f, r, a, b, θ, c, d) and the others: the letters by their names, their order the order of integration. */
        private fun coordinates(system: Multiple.System): Outcome {
            val groups = (1 until pieces.size step 3).map { k -> letter(k) }
            val names = groups.map { it.name }.toSet()
            if (names.size != groups.size) throw CalcException(Failure.ARGUMENTS)
            val angle = when (system) {
                Multiple.System.SPHERICAL -> Multiple.THETA
                else -> groups.firstOrNull { it == Multiple.THETA || it == Multiple.PHI } ?: throw CalcException(Failure.ARGUMENTS)
            }
            if (names != Multiple.letters(system, angle).map { it.name }.toSet()) throw CalcException(Failure.ARGUMENTS)
            val f = expr(0, names)
            val ranges = groups.mapIndexed { j, v -> Multiple.Range(v, expr(3 * j + 2, names), expr(3 * j + 3, names)) }
            // The bounds are in the system's letters: x and y (and z on the sphere) are what they stand for.
            val cartesian = Multiple.cartesian(system, angle).map { it.first.name }.toSet()
            if (ranges.any { r -> (r.lo.symbols() + r.hi.symbols()).any { it in cartesian } }) throw CalcException(Failure.ARGUMENTS)
            return valueOf(Multiple.answer(Multiple.integrate(f, ranges, system, angle)))
        }
    }

    /** φ written \varphi is the letter φ. */
    fun canonical(e: Expr): Expr = if ("\\varphi" in e.symbols()) e.substitute(Expr.Sym("\\varphi"), Multiple.PHI) else e

    /** A result as the Calculator shows it: a number exactly and with its decimal, a decimal alone, a formula in its letters. */
    fun valueOf(v: Expr): Outcome {
        if (v.symbols().isNotEmpty() || v.hasI() || v.hasInfinity()) return Outcome.Expression(v)
        if (v is Expr.Num) return Outcome.Value(Num.Exact(v.value))
        val d = Numeric.value(v) ?: throw CalcException(Failure.UNDEFINED)
        if (v is Expr.Real) return Outcome.Value(Num.Approx(d))
        val shown = Algebra.rationalize(v)
        return Outcome.Value(Num.Approx(d), exact = shown.takeIf { MathCalc.niceExact(it) })
    }
}

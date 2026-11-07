package com.professoreuler.mathcalc

/**
 * The applied functions a school or first-year course uses daily, each a call of its own ([CommandFunctions]): a matrix's rank,
 * trace, null space, column space, characteristic polynomial and adjugate ([MatrixFunctions]); a curve's tangent and normal
 * line, arc length, the area between two curves, the volumes of revolution about either axis and the mean value
 * ([Applications]); linear programs maximized or minimized ([LinearProgram]). [read] reads the calls whose arguments are more
 * than a formula, [latex] writes them back as calls, and [outcome] works each out when its call asks for it.
 *
 * The names, and why: `rank`, `trace`, `nullspace`, `columnspace`, `charpoly` and `adjugate` as the linear algebra systems name
 * them (`adjugate`, not `adjoint`, which many books use for the conjugate transpose); `tangentline` and `normalline`, since
 * `tangent` is the name of tan and `normal` begins the normal distribution's functions (normalpdf, normalcdf); `arclength`;
 * `areabetween`, which names its two curves (an area alone could be a triangle's); `volumex` and `volumey`, the solid turned
 * about the x-axis and about the y-axis; `meanvalue`, the mean of a function (`mean` is a list's); `maximize` and `minimize`.
 */
object Applied {
    /** The modes worked out here. */
    val MODES: Set<Mode> = setOf(Mode.RANK, Mode.TRACE, Mode.NULL_SPACE, Mode.COLUMN_SPACE, Mode.CHARACTERISTIC_POLYNOMIAL, Mode.ADJUGATE,
        Mode.TANGENT_LINE, Mode.NORMAL_LINE, Mode.ARC_LENGTH, Mode.AREA_BETWEEN, Mode.VOLUME_X, Mode.VOLUME_Y, Mode.MEAN_VALUE,
        Mode.MAXIMIZE, Mode.MINIMIZE)

    private val MATRIX = setOf(Mode.RANK, Mode.TRACE, Mode.NULL_SPACE, Mode.COLUMN_SPACE, Mode.CHARACTERISTIC_POLYNOMIAL, Mode.ADJUGATE)
    private val POINT = setOf(Mode.TANGENT_LINE, Mode.NORMAL_LINE)
    private val BOUNDS = setOf(Mode.ARC_LENGTH, Mode.VOLUME_X, Mode.VOLUME_Y, Mode.MEAN_VALUE)

    // ------------------------------------------------------------------------------------------------------------------
    // Reading a call.

    /**
     * A call of a function of [Applied] (shape APPLIED) with its [args] as typed: null where they are not what it takes, so the
     * call fails ([Failure.ARGUMENTS]). The count tells the forms apart, as integral's does:
     * - charpoly(A), charpoly(A, x);
     * - tangentline(f, a), tangentline(f, x, a), and normalline alike;
     * - arclength(f, a, b), arclength(f, x, a, b), and volumex, volumey, meanvalue alike;
     * - areabetween(f, g), areabetween(f, g, x), areabetween(f, g, a, b), areabetween(f, g, x, a, b).
     */
    internal fun read(f: CommandFunctions.Function, args: List<String>): Typed? {
        val mode = f.mode ?: return null
        // No argument here is an equation or an inequality.
        if (args.any { relation(it) }) return null
        val l = TextInput
        fun letter(k: Int): String? = l.letterOf(args[k])
        return when (mode) {
            Mode.CHARACTERISTIC_POLYNOMIAL -> when (args.size) {
                1 -> Typed(l.latex(args[0]), Choice(mode))
                2 -> Typed(l.latex(args[0]), Choice(mode, letter(1) ?: return null))
                else -> null
            }
            in POINT -> when (args.size) {
                2 -> Typed(l.latex(args[0]), Choice(mode, point = l.latex(args[1])))
                3 -> {
                    val x = letter(1) ?: return null
                    if (args[2].trim() == x) return null
                    Typed(l.latex(args[0]), Choice(mode, x, point = l.latex(args[2])))
                }
                else -> null
            }
            in BOUNDS -> when (args.size) {
                3 -> Typed(l.latex(args[0]), Choice(mode, point = bounds(args[1], args[2])))
                4 -> {
                    val x = letter(1) ?: return null
                    if (args[2].trim() == x || args[3].trim() == x) return null
                    Typed(l.latex(args[0]), Choice(mode, x, point = bounds(args[2], args[3])))
                }
                else -> null
            }
            Mode.AREA_BETWEEN -> {
                val curves = l.latex(args[0] + "; " + args[1])
                when (args.size) {
                    2 -> Typed(curves, Choice(mode))
                    3 -> Typed(curves, Choice(mode, letter(2) ?: return null))
                    4 -> Typed(curves, Choice(mode, point = bounds(args[2], args[3])))
                    5 -> {
                        val x = letter(2) ?: return null
                        if (args[3].trim() == x || args[4].trim() == x) return null
                        Typed(curves, Choice(mode, x, point = bounds(args[3], args[4])))
                    }
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun bounds(a: String, b: String) = TextInput.latex(a) + "," + TextInput.latex(b)

    private fun relation(arg: String): Boolean {
        val a = arg.replace("->", " ").replace("\\to", " ")
        return listOf("=", "<", ">", "≤", "≥", "≠", "≡", "\\le", "\\ge", "\\ne", "\\equiv").any { it in a }
    }

    /** [point] ("a,b") split at its comma outside every bracket. */
    private fun split(point: String): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        val current = StringBuilder()
        for (ch in point) {
            when (ch) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
            }
            if (ch == ',' && depth == 0) {
                out += current.toString().trim()
                current.setLength(0)
            } else current.append(ch)
        }
        out += current.toString().trim()
        return out
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Writing the call.

    /** The call of a reading of one of [Applied]'s functions whose shape is its own; null for every other reading. */
    internal fun latex(typed: Typed): String? {
        val c = typed.choice ?: return null
        val f = CommandFunctions.ALL.firstOrNull { it.mode == c.mode && it.shape == CommandFunctions.Shape.APPLIED } ?: return null
        val l = typed.latex.trim()
        val args: List<String> = when (c.mode) {
            Mode.CHARACTERISTIC_POLYNOMIAL -> listOfNotNull(l, c.letter)
            in POINT -> listOf(l, c.letter ?: TextInput.mainLetterOf(l), c.point ?: return null)
            in BOUNDS -> listOf(l, c.letter ?: TextInput.mainLetterOf(l)) + split(c.point ?: return null)
            Mode.AREA_BETWEEN -> lines(l) + listOf(c.letter ?: TextInput.mainLetterOf(l)) + (c.point?.let { split(it) } ?: emptyList())
            else -> return null
        }
        return "\\operatorname{${f.name}}\\left(" + args.joinToString(", ") + "\\right)"
    }

    private fun lines(l: String): List<String> {
        val m = Regex("""^\\begin\{cases\}(.*)\\end\{cases\}$""", RegexOption.DOT_MATCHES_ALL).matchEntire(l) ?: return listOf(l)
        return m.groupValues[1].split("\\\\").map { it.trim() }.filter { it.isNotEmpty() }
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Working out.

    /** The outcome of [statements] for [choice], where it is one of [MODES]; null for every other choice. */
    internal fun outcome(statements: List<Statement>, choice: Choice?): Outcome? {
        val c = choice ?: return null
        if (c.mode !in MODES) return null
        return when (c.mode) {
            in MATRIX -> MatrixFunctions.of(c.mode, matrixOf(statements), c.letter)
            Mode.MAXIMIZE, Mode.MINIMIZE -> LinearProgram.of(statements, c.mode == Mode.MAXIMIZE)
            Mode.AREA_BETWEEN -> {
                if (statements.size != 2) throw CalcException(Failure.ARGUMENTS)
                val f = formulaOf(listOf(statements[0]))
                val g = formulaOf(listOf(statements[1]))
                val x = Expr.Sym(c.letter ?: mainLetter(f.symbols() + g.symbols()))
                val bounds = c.point?.let { boundsOf(it) }
                Applications.areaBetween(f, g, x, bounds?.first, bounds?.second)
            }
            else -> {
                val f = formulaOf(statements)
                val x = Expr.Sym(c.letter ?: mainLetter(f.symbols()))
                when (c.mode) {
                    in POINT -> Applications.tangent(f, x, pointOf(c.point ?: throw CalcException(Failure.ARGUMENTS)), c.mode == Mode.NORMAL_LINE)
                    else -> {
                        val (a, b) = boundsOf(c.point ?: throw CalcException(Failure.ARGUMENTS))
                        when (c.mode) {
                            Mode.ARC_LENGTH -> Applications.arcLength(f, x, a, b)
                            Mode.VOLUME_X -> Applications.volumeX(f, x, a, b)
                            Mode.VOLUME_Y -> Applications.volumeY(f, x, a, b)
                            else -> Applications.meanValue(f, x, a, b)
                        }
                    }
                }
            }
        }
    }

    /** x where the formula has it, else its only letter, else the first; x for a constant. */
    internal fun mainLetter(letters: Set<String>): String = when {
        "x" in letters -> "x"
        letters.isEmpty() -> "x"
        else -> letters.sorted().first()
    }

    /** The one matrix of a matrix function's call. */
    internal fun matrixOf(statements: List<Statement>): Expr.Mat {
        val s = statements.singleOrNull() ?: throw CalcException(Failure.ARGUMENTS)
        val sides = s.sides.filterNotNull()
        if (sides.size != 1 || s.relations.isNotEmpty()) throw CalcException(Failure.ARGUMENTS)
        return Calculus.evaluate(sides[0]) as? Expr.Mat ?: throw CalcException(Failure.ARGUMENTS)
    }

    /** The one formula (no equation, no matrix) of a call, its calculus worked out. */
    internal fun formulaOf(statements: List<Statement>): Expr {
        val s = statements.singleOrNull() ?: throw CalcException(Failure.ARGUMENTS)
        val sides = s.sides.filterNotNull()
        if (sides.size != 1 || s.relations.isNotEmpty() || s.definition != null) throw CalcException(Failure.ARGUMENTS)
        val e = Calculus.evaluate(sides[0])
        if (e is Expr.Mat || e.hasInfinity()) throw CalcException(Failure.ARGUMENTS)
        return e
    }

    /** A point or bound written in LaTeX ("1", "\\pi", "a", "\\infty"). */
    internal fun pointOf(latex: String): Expr {
        val tokens = MathCalc.TOKEN.findAll(latex).map { it.value }.toList()
        val s = Parser(tokens, Flags(Assumptions())).statements().singleOrNull() ?: throw CalcException(Failure.ARGUMENTS)
        val sides = s.sides.filterNotNull()
        if (sides.size != 1 || s.relations.isNotEmpty()) throw CalcException(Failure.ARGUMENTS)
        val e = Calculus.evaluate(sides[0])
        if (e is Expr.Mat) throw CalcException(Failure.ARGUMENTS)
        return e
    }

    internal fun boundsOf(point: String): Pair<Expr, Expr> {
        val parts = split(point)
        if (parts.size != 2) throw CalcException(Failure.ARGUMENTS)
        return pointOf(parts[0]) to pointOf(parts[1])
    }

    /** A number's outcome: exact as a plain number, or its exact form with the decimal beside it; with letters, a formula. */
    internal fun number(v: Expr): Outcome {
        if (v.symbols().isNotEmpty()) return Outcome.Expression(v)
        if (v.hasInfinity()) return Outcome.Expression(v)
        if (v is Expr.Num) return Outcome.Value(Num.Exact(v.value))
        val d = Precise.double(v) ?: throw CalcException(Failure.UNDEFINED)
        val shown = Algebra.rationalize(v)
        return Outcome.Value(Num.Approx(d), exact = shown.takeIf { MathCalc.niceExact(it) })
    }
}

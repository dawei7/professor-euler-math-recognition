package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.round

/**
 * Show the steps (plan item 6): what each kind of task's steps say, and that every step follows from the one before:
 * the same solutions for equations (more only where a step may bring some in, as squaring does), the same truth for
 * inequalities, the same value for expressions.
 */
class StepsTest {
    /** The applied functions' steps (rank to linear programs), as their calls are read. */
    private val APPLIED = listOf(
        "rank([[1, k], [k, 4]])", "trace([[1, 2], [3, 4]])", "nullspace([[1, 2, 3], [4, 5, 6]])", "columnspace([[1, 2, 3], [4, 5, 6]])",
        "charpoly([[2, 1], [1, 2]])", "adjugate([[1, 2], [3, 4]])", "tangentline(x^2, x, 1)", "normalline(x^3, x, 1)",
        "normalline(x^2, x, 0)", "arclength(x^3/6 + 1/(2x), x, 1, 2)", "arclength(sin(x), x, 0, pi)", "areabetween(x^3, x)",
        "volumex(sqrt(x), x, 0, 4)", "volumey(x^2, x, 0, 2)", "meanvalue(x^2, x, 0, 3)",
        "maximize(3x + 2y, x + y <= 4, x + 3y <= 6, x >= 0, y >= 0)",
        "maximize(2x + 3y + 4z, x + y + z <= 10, 2x + y <= 12, y + 3z <= 15, x >= 0, y >= 0, z >= 0)",
        "maximize(x + y, x >= 0, y >= 0)", "maximize(x + y, x + y <= 2, x + y >= 3)",
    ).map { TextInput.read(it).let { t -> t.latex to t.choice } }

    private val LATER = listOf(
        "\\begin{pmatrix}1&2\\\\3&4\\end{pmatrix}" to null, "\\begin{pmatrix}2&1\\\\1&2\\end{pmatrix}" to Choice(Mode.EIGEN),
        "\\begin{pmatrix}2&0&1\\\\1&3&2\\\\1&1&1\\end{pmatrix}" to null, "\\begin{pmatrix}1&2\\\\3&4\\end{pmatrix}" to Choice(Mode.INVERSE),
        "\\begin{pmatrix}2&0&1\\\\1&3&2\\\\1&1&2\\end{pmatrix}" to Choice(Mode.INVERSE),
        "\\begin{cases}a_{n+2}=a_{n+1}+a_{n}\\\\a_{0}=0\\\\a_{1}=1\\end{cases}" to null,
        "\\begin{cases}a_{n}=3a_{n-1}+2^{n}\\\\a_{0}=1\\end{cases}" to null,
        "3x\\equiv 2\\pmod{7}" to null, "\\begin{cases}x\\equiv 2\\pmod{3}\\\\x\\equiv 3\\pmod{5}\\end{cases}" to null,
        "x^{2}\\equiv 1\\pmod{8}" to null, "3x+5y=7" to Choice(Mode.DIOPHANTINE), "x^{2}-2y^{2}=1" to Choice(Mode.DIOPHANTINE),
        "\\begin{cases}a=3\\\\b=4\\\\c=5\\end{cases}" to null, "\\begin{cases}a=5\\\\b=7\\\\\\gamma=60\\end{cases}" to null,
        "\\begin{cases}a=10\\\\\\alpha=30\\\\\\beta=45\\end{cases}" to null, "\\begin{cases}a=6\\\\b=8\\\\\\alpha=30\\end{cases}" to null,
        "(2,4,4,4,5,5,7,9)" to null, "\\begin{pmatrix}1&2\\\\2&4\\\\3&5\\\\4&4\\\\5&5\\end{pmatrix}" to null,
        "5" to Choice(Mode.UNITS, "km", "mi"), "20" to Choice(Mode.UNITS, "°C", "°F"),
        "x^{2}+y^{2}-4x+6y=12" to null, "y^{2}=8x" to Choice(Mode.CONIC),
        "\\operatorname{constant}(electronmass)\\cdot \\operatorname{constant}(speedoflight)^{2}" to null,
        "\\operatorname{constant}(speedoflight, km/h)" to null,
    ) + APPLIED


    private val NUMBERS = listOf("interpolate((1, 2), (2, 3), (3, 5))", "spline((0, 0), (1, 1), (2, 0))", "multorder(3, 7)",
        "primroot(41)", "discretelog(5, 2, 13)")

    private fun keys(latex: String, choice: Choice? = null): String =
        Steps.of(latex, choice)?.joinToString(",") { it.why.key.name } ?: "none"

    private fun last(latex: String, choice: Choice? = null): String = Steps.of(latex, choice)?.last()?.latex ?: "none"

    @Test
    fun linearEquations() {
        assertEquals("START,SUBTRACT_BOTH,DIVIDE_BOTH", keys("2x+3=11"))
        assertEquals("x = 4", last("2x+3=11"))
        assertEquals("START,SUBTRACT_BOTH,ADD_BOTH,DIVIDE_BOTH", keys("4y-7=2y+9"))
        assertEquals("START,EXPAND_SIDES,SUBTRACT_BOTH,ADD_BOTH", keys("3(x-2)=2x+1"))
        assertEquals("x = 7", last("3(x-2)=2x+1"))
    }

    @Test
    fun quadraticEquations() {
        assertEquals("START,FACTOR_SIDE,ZERO_PRODUCT,SOLUTIONS", keys("x^{2}-5x+6=0"))
        assertEquals("START,QUADRATIC_FORMULA,DISCRIMINANT,D_POSITIVE,SOLUTIONS", keys("x^{2}-2x-1=0"))
        assertEquals("START,SQUARE_ROOT_BOTH,SOLUTIONS", keys("x^{2}=49"))
        assertEquals("START,SQUARE_ROOT_BOTH,SUBTRACT_BOTH,SOLUTIONS", keys("(x+1)^{2}=16"))
        assertEquals("START,QUADRATIC_FORMULA,DISCRIMINANT,D_NEGATIVE,NO_REAL_SOLUTION", keys("x^{2}+2x+5=0"))
    }

    @Test
    fun fractionsRootsAbsolute() {
        assertEquals("START,DOMAIN,MULTIPLY_DENOMINATOR,SUBTRACT_BOTH,SUBTRACT_BOTH,DIVIDE_BOTH", keys("\\frac{x+1}{x-1}=3"))
        assertEquals("x = 2", last("\\frac{x+1}{x-1}=3"))
        val root = keys("\\sqrt{x+2}=x")
        assertTrue(root, root.startsWith("START,SQUARE_BOTH") && root.endsWith("CHECK,FALSE_SOLUTION,SOLUTION"))
        assertEquals("START,ABS_CASES,ADD_BOTH,DIVIDE_BOTH,SOLUTIONS", keys("|2x-1|=3"))
        assertEquals("START,SUBTRACT_BOTH,ABS_NEGATIVE,NO_SOLUTION", keys("|x-1|+2=1"))
        // A value that makes a denominator 0 is named as outside the domain.
        assertEquals("START,DOMAIN,MULTIPLY_DENOMINATOR,DIVIDE_BOTH,EXCLUDED,NO_SOLUTION", keys("\\frac{1}{x-1}+\\frac{1}{x+1}=\\frac{2}{x^{2}-1}"))
    }

    @Test
    fun exponentialsLogarithmsTrigonometry() {
        assertEquals("START,TAKE_LOG", keys("2^{x}=8"))
        assertEquals("x = 3", last("2^{x}=8"))
        assertEquals("START,EXPONENTIATE,SOLUTION", keys("\\ln(x)=2"))
        val logs = keys("\\ln(x)+\\ln(x-1)=\\ln(6)")
        assertTrue(logs, logs.startsWith("START,COMBINE_LOGS,ARGUMENTS_EQUAL") && logs.endsWith("CHECK,FALSE_SOLUTION,SOLUTION"))
        val sub = keys("e^{2x}-3e^{x}+2=0")
        assertTrue(sub, sub.startsWith("START,SUBSTITUTE_U") && sub.endsWith("BACK_SUBSTITUTE,SOLUTIONS"))
        assertEquals("START,INVERSE_TRIG,SOLUTIONS_PERIODIC", keys("\\sin(x)=\\frac{1}{2}"))
    }

    @Test
    fun inequalitiesAndSystems() {
        assertEquals("START,SUBTRACT_BOTH,DIVIDE_BOTH", keys("2x+3<11"))
        assertEquals("START,FLIP", keys("-2x>4"))
        assertEquals("START,STANDARD_FORM,CRITICAL_POINTS,SIGN_CHART,SOLUTION_SET", keys("x^{2}>1"))
        assertEquals("START,ABS_LESS,ADD_BOTH", keys("|x-1|<3"))
        assertEquals("-2 < x < 4", last("|x-1|<3"))
        assertEquals("SYSTEM,SOLVE_FOR_LETTER,SUBSTITUTE_INTO,SUBTRACT_BOTH,DIVIDE_BOTH,BACK_SUBSTITUTE_VALUE,SOLUTION", keys("x+y=5\\\\x-y=1"))
        assertEquals("x = 3,\\; y = 2", last("x+y=5\\\\x-y=1"))
    }

    @Test
    fun algebraAndCalculus() {
        assertEquals("START,BINOMIAL_1", keys("(x+2)^{2}"))
        assertEquals("START,BINOMIAL_3,MULTIPLY_OUT", keys("(x+1)(x-1)(x+2)"))
        assertEquals("START,FACTOR_BINOMIAL_1", keys("x^{2}+4x+4"))
        assertEquals("START,TRINOMIAL", keys("x^{2}-5x+6"))
        assertEquals("START,COMMON_FACTOR", keys("6x+9", Choice(Mode.FACTOR)))
        assertEquals("START,FACTOR_PARTS,CANCEL", keys("\\frac{x^{2}-4}{x+2}"))
        assertEquals("START,PRODUCT_RULE,SIMPLIFY", keys("\\frac{d}{dx}(x^{2}\\sin(x))="))
        assertEquals("START,SUM_RULE,POWER_RULE", keys("\\frac{d}{dx}(x^{3}+2x)="))
        assertEquals("START,CHAIN_RULE,SIMPLIFY", keys("\\frac{d}{dx}\\sin(x^{2})="))
        assertEquals("START,POWER_RULE,DERIVATIVE_AGAIN", keys("\\frac{d^{2}}{dx^{2}}x^{4}="))
        assertEquals("START,INTEGRAL_PARTS,PLUS_C", keys("\\int xe^{x}dx"))
        assertEquals("START,INTEGRAL_SUBSTITUTION,BACK_SUBSTITUTE,PLUS_C", keys("\\int 2xe^{x^{2}}dx"))
        assertEquals("START,LIMIT_INDETERMINATE,LIMIT_CANCEL,RESULT", keys("\\lim_{x\\rightarrow2}\\frac{x^{2}-4}{x-2}"))
        assertEquals("START,COLLECT", keys("5x+8x+7y+2y"))
        assertEquals("START,PRIME_DIVIDE,PRIME_DIVIDE,PRIME_DIVIDE,PRIME_DIVIDE,PRIME_DIVIDE,PRIME_DIVIDE,RESULT", keys("360=", Choice(Mode.FACTOR)))
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Every step follows from the one before.

    private val tasks = listOf(
        // Equations.
        "2x+3=11", "3(x-2)=2x+1", "\\frac{x}{4}+1=3", "4y-7=2y+9", "x^{2}=49", "x^{2}-5x+6=0", "2x^{2}+3x-2=0", "(x+1)^{2}=16",
        "\\frac{1}{x}=4", "x^{3}=8", "2^{x}=8", "7x-4=17", "4(x+1)=x+7", "\\frac{x}{7}=\\frac{1}{14}", "1,4y+0,7=4,9", "x^{2}=1,44",
        "x^{2}+4x-21=0", "4x^{2}=1", "(x-7)^{2}-1=0", "\\frac{14}{x}=7", "x^{3}-6x^{2}+11x-6=0", "x^{2}-2x-1=0", "x^{4}-5x^{2}+4=0",
        "\\frac{x+1}{x-1}=3", "\\frac{1}{x-1}+\\frac{1}{x+1}=\\frac{2}{x^{2}-1}", "\\sqrt{x+2}=x", "|2x-1|=3", "3^{x}=5", "e^{x}=5",
        "e^{2x}-3e^{x}+2=0", "\\ln(x)=2", "\\log_{2}(x)=5", "\\ln(x)+\\ln(x-1)=\\ln(6)", "x^{2}+2x+5=0", "\\sqrt{x}=3",
        "\\sqrt{2x+1}=x-1", "x=3-x", "5=2x+1", "\\frac{2}{x}+1=\\frac{3}{x}", "x^{2}+x=0", "3x^{2}=12",
        "\\frac{x}{x-2}=\\frac{2}{x-2}", "\\cos(x)=0", "\\tan(x)=1", "2\\sin^{2}(x)-\\sin(x)-1=0", "\\sin(x^{\\circ})=\\frac{1}{2}",
        "\\sqrt{x-1}=3", "\\sqrt[3]{x}=2", "e^{x+1}=e^{3}", "2^{x+1}=2^{5}", "v=\\frac{s}{t}", "ax+b=c", "A=\\pi r^{2}",
        "x^{2}-2x+1=0", "|x-1|+2=1", "2|x-1|=6", "\\sqrt{x+1}+1=x", "2^{2x}=2^{x+3}", "\\sin(x)=2", "|x-1|+x=5",
        // Inequalities.
        "2x+3<11", "-2x>4", "x^{2}-4\\le0", "x^{2}>1", "\\frac{x-1}{x+2}\\ge0", "5-3x\\ge2", "x^{2}-x-6>0", "x^{2}+1>0", "|x-1|<3",
        "|2x-1|\\ge3", "|3-x|<2", "x^{2}+2x<x^{2}+4",
        // Systems.
        "x+y=5\\\\x-y=1", "2x+3y=7\\\\4x-y=7", "x+y=5\\\\xy=6", "x+y+z=6\\\\x-y=1\\\\y+2z=7",
        // Expressions.
        "(x+2)^{2}", "(a-b)^{3}", "5x+8x+7y+2y", "(x+1)(x-1)(x+2)", "(x+3)(x-3)", "2(x+3)-x", "(x-1)^{4}", "(x+1)(x+2)(x+3)",
        "-(x-1)(x+1)", "x^{3}+2x^{2}-x-2",
        "x^{2}+4x+4", "x^{2}-5x+6", "6x+9", "x^{3}-x", "4x^{2}-9y^{2}", "ax+ay+bx+by", "x^{3}-3x^{2}+3x-1",
        "\\frac{1}{x}+\\frac{1}{x+1}", "\\frac{x^{2}-4}{x+2}", "\\frac{x^{2}-1}{x^{2}+2x+1}",
        // Comparing sides.
        "(x+2)^{2}=x^{2}+4x+4", "(x+1)^{2}=x^{2}+1",
        // Calculus.
        "\\frac{d}{dx}(x^{3}+2x)=", "\\frac{d}{dx}\\sin(x^{2})=", "(x^{2}e^{x})'=", "\\frac{d}{dx}\\ln(x)=", "\\frac{d}{dx}\\frac{x}{x+1}=",
        "\\frac{d}{dx}(x^{2}\\sin(x))=", "\\frac{d}{dx}e^{3x}=", "\\frac{d}{dx}((2x+1)^{5})=",
        "\\int x^{2}dx", "\\int\\sin(x)dx", "\\int e^{2x}dx", "\\int xe^{x}dx", "\\int x\\cos(x)dx", "\\int\\ln(x)dx",
        "\\int\\frac{2x}{x^{2}+1}dx", "\\int\\frac{1}{x^{2}-1}dx", "\\int(3x^{2}+2x)dx", "\\int_{0}^{1}x^{2}dx",
        "\\int 2xe^{x^{2}}dx", "\\int 6xe^{x^{2}}dx", "\\int 3\\sin(x)dx", "\\int_{0}^{\\pi}\\sin(x)dx", "\\frac{d}{dx}(3x^{2}\\sin(x))=",
        "\\frac{d}{dx}\\frac{1}{x^{2}+1}=", "\\frac{d}{dx}\\sqrt{2x+1}=", "\\frac{d^{2}}{dx^{2}}x^{4}=", "\\int\\frac{1}{x}dx",
        "\\lim_{x\\rightarrow0^{+}}\\frac{1}{x}", "\\lim_{x\\rightarrow2}x^{2}",
        "\\lim_{x\\rightarrow0}\\frac{\\sin(x)}{x}", "\\lim_{x\\rightarrow2}\\frac{x^{2}-4}{x-2}", "\\lim_{x\\rightarrow\\infty}\\frac{2x^{2}+1}{x^{2}-3}",
    )

    /** The real solutions of an equation form, rounded; null where it cannot be told (several letters, families). */
    private fun solutions(form: StepForm, x: String): Set<Long>? = when (form) {
        is StepForm.Equation -> {
            val letters = (form.lhs.symbols() + form.rhs.symbols())
            if (!letters.all { it == x }) null
            else if (letters.isEmpty()) {
                val d = Numeric.value(form.lhs - form.rhs)
                if (d != null && abs(d) < 1e-9) null else emptySet()
            } else {
                val s = Solve.equation(form.lhs, form.rhs, Expr.Sym(x))
                if (s.always || s.many || s.periodic.isNotEmpty()) null
                else s.values.mapNotNull { Numeric.value(it) }.map { round(it * 1e6).toLong() }.toSet()
            }
        }
        is StepForm.AnyOf -> {
            val parts = form.equations.map { solutions(it, x) }
            if (parts.any { it == null }) null else parts.flatMap { it!! }.toSet()
        }
        else -> null
    }

    private fun truth(form: StepForm.Inequality, x: String, v: Double): Boolean? {
        val a = Numeric.eval(form.lhs, mapOf(x to v))
        val b = Numeric.eval(form.rhs, mapOf(x to v))
        if (!a.isFinite() || !b.isFinite()) return null
        val d = a - b
        if (abs(d) < 1e-9) return null
        return when (form.rel) {
            Rel.LT, Rel.LE -> d < 0
            Rel.GT, Rel.GE -> d > 0
            Rel.NE -> true
            Rel.EQ -> false
        }
    }

    @Test
    fun everyStepFollowsFromTheOneBefore() {
        val problems = ArrayList<String>()
        var withSteps = 0
        var checked = 0
        for (task in tasks) {
            val steps = Steps.of(task)
            if (steps == null) {
                problems += "$task: no steps"
                continue
            }
            withSteps++
            val calc = MathCalc.calculate(task)
            val x = when (val o = calc.outcome) {
                is Outcome.Solved -> o.solutions.variable
                is Outcome.Intervals -> o.set.variable
                else -> calc.choice?.letter ?: "x"
            }
            val forms = steps.filter { it.form != null }
            for ((a, b) in forms.zipWithNext()) {
                val fa = a.form!!
                val fb = b.form!!
                when {
                    fa is StepForm.Value && fb is StepForm.Value -> {
                        if (fa.e.hasOp() || fb.e.hasOp()) continue
                        checked++
                        if (!Verify.same(fa.e, fb.e)) problems += "$task: ${a.latex}  →  ${b.latex}"
                    }
                    fa is StepForm.Inequality && fb is StepForm.Inequality -> {
                        checked++
                        for (k in -400..400) {
                            val v = k * 0.0371 + 0.00137
                            val ta = truth(fa, x, v) ?: continue
                            val tb = truth(fb, x, v) ?: continue
                            if (ta != tb) { problems += "$task: ${a.latex}  →  ${b.latex} at $v"; break }
                        }
                    }
                    else -> {
                        val sa = solutions(fa, x) ?: continue
                        val sb = solutions(fb, x) ?: continue
                        checked++
                        val ok = when {
                            b.widens -> sb.containsAll(sa)
                            // An answer that drops values: each dropped one must not solve the equation as written.
                            b.narrows -> {
                                val original = steps.first().form as? StepForm.Equation
                                sa.containsAll(sb) && original != null && (sa - sb).none { d ->
                                    Solve.solves(original.lhs, original.rhs, Expr.Sym(x), Expr.Real(d / 1e6))
                                }
                            }
                            else -> sa == sb
                        }
                        if (!ok) problems += "$task: ${a.latex} $sa  →  ${b.latex} $sb"
                    }
                }
            }
        }
        println("steps for $withSteps of ${tasks.size} tasks, $checked transitions checked")
        assertEquals("", problems.joinToString("\n"))
    }

    private val token = Regex("""\\[a-zA-Z]+|\\.|\S""")

    /** A step's formula read back as Calculate reads handwriting: its sides, or null where it is not one formula. */
    private fun readBack(latex: String): List<Expr>? = try {
        val statements = Parser(token.findAll(latex).map { it.value }.toList()).statements()
        statements.singleOrNull()?.sides?.filterNotNull()?.takeIf { it.isNotEmpty() && statements[0].relations.all { r -> r == Rel.EQ } }
    } catch (e: CalcException) {
        null
    }

    /** What each step shows is what it says: its formula, read back, has the value or the solutions of its form. */
    @Test
    fun eachFormulaSaysWhatItsStepSays() {
        val problems = ArrayList<String>()
        var checked = 0
        for (task in tasks) {
            val x = when (val o = MathCalc.calculate(task).outcome) {
                is Outcome.Solved -> o.solutions.variable
                is Outcome.Intervals -> o.set.variable
                else -> "x"
            }
            for (s in Steps.of(task).orEmpty()) {
                val form = s.form ?: continue
                if (s.why.key == StepKey.START || listOf("\\lor", ":", ",").any { it in s.latex }) continue
                val sides = readBack(s.latex) ?: continue
                when {
                    form is StepForm.Value && sides.size == 1 && !form.e.hasOp() && !sides[0].hasOp() -> {
                        checked++
                        if (!Verify.same(sides[0], form.e)) problems += "$task: ${s.why.key} ${s.latex}"
                    }
                    form is StepForm.Equation && sides.size == 2 -> {
                        val shown = solutions(StepForm.Equation(sides[0], sides[1]), x) ?: continue
                        val meant = solutions(form, x) ?: continue
                        checked++
                        if (shown != meant) problems += "$task: ${s.why.key} ${s.latex} $shown, not $meant"
                    }
                }
            }
        }
        println("$checked formulas read back")
        assertEquals("", problems.joinToString("\n"))
    }

    /** Every sentence the apps carry is said by some task. */
    @Test
    fun everyKindOfStepIsReached() {
        val reached = HashSet<StepKey>()
        for (task in tasks) Steps.of(task)?.forEach { reached += it.why.key }
        // Those that need a mode chosen: every value solves it, a letter taken out, factors, the sides compared.
        val chosen = listOf("2(x+1)=2x+2" to Choice(Mode.SOLVE, "x"), "ax+b=cx+d" to Choice(Mode.SOLVE, "x"),
            "2x^{2}+3x-2" to Choice(Mode.FACTOR), "x^{2}+1" to Choice(Mode.FACTOR), "360=" to Choice(Mode.FACTOR),
            "x+1=x+1" to Choice(Mode.COMPARE), "x+1=x+2" to Choice(Mode.COMPARE),
            // The singular value decomposition, the Jordan form, a linear system, boundary values, a power series.
            "\\begin{pmatrix}3&0\\\\4&5\\end{pmatrix}" to Choice(Mode.SVD), "\\begin{pmatrix}2&2&3\\\\1&3&3\\\\-1&-2&-2\\end{pmatrix}" to Choice(Mode.JORDAN),
            "\\begin{cases}x'=3x+y\\\\y'=x+3y\\\\x(0)=2\\\\y(0)=0\\end{cases}" to null, "\\begin{cases}y''+y=0\\\\y(0)=0\\\\y(\\pi)=0\\end{cases}" to null,
            "\\begin{cases}y''+y=0\\\\y(0)=0\\\\y(\\pi)=1\\end{cases}" to null, "\\begin{cases}y''+y=0\\\\y(0)=0\\\\y(\\frac{\\pi}{2})=1\\end{cases}" to null,
            "\\begin{cases}y''+y=0\\\\y(0)=1\\\\y'(0)=0\\end{cases}" to Choice(Mode.ODE_SERIES))
        for ((latex, choice) in chosen + LATER) Steps.of(latex, choice)?.forEach { reached += it.why.key }
        // Interpolation and splines; orders, primitive roots and discrete logarithms, typed as their functions.
        for (text in NUMBERS) TextInput.read(text).let { t -> Steps.of(t.latex, t.choice)?.forEach { reached += it.why.key } }
        assertEquals("", StepKey.values().filter { it !in reached }.joinToString())
    }

    /** The later areas' steps: matrices, recurrences, congruences, whole numbers, triangles, statistics, units, conics. */
    @Test
    fun laterAreasHaveTheirSteps() {
        val expected = listOf(
            "START DET_2X2 RESULT", "START CHARACTERISTIC EIGENVALUES EIGENVECTOR EIGENVECTOR", "START DET_EXPAND RESULT",
            "START INVERSE_2X2 RESULT INVERSE_CHECK", "START INVERSE_GAUSS_JORDAN RESULT INVERSE_CHECK",
            "REC_CHARACTERISTIC REC_ROOTS REC_GENERAL REC_CONSTANTS RESULT", "REC_CHARACTERISTIC REC_ROOTS REC_GENERAL REC_PARTICULAR REC_CONSTANTS RESULT",
            "START CONG_GCD CONG_LINE RESULT", "START CONG_GCD CONG_LINE START CONG_GCD CONG_LINE CRT", "START CONG_TRIED CONG_LINE RESULT",
            "START DIO_GCD DIO_PARTICULAR DIO_FAMILY", "START PELL PELL_POWERS",
            "START TRI_COSINES TRI_COSINES TRI_ANGLE_SUM TRI_AREA", "START TRI_COSINES TRI_COSINES TRI_ANGLE_SUM TRI_AREA",
            "START TRI_ANGLE_SUM TRI_SINES TRI_SINES TRI_AREA",
            "START TRI_SINES TRI_AMBIGUOUS TRI_ANGLE_SUM TRI_SINES TRI_AREA TRI_SINES TRI_AMBIGUOUS TRI_ANGLE_SUM TRI_SINES TRI_AREA",
            "START STAT_MEAN STAT_SORTED STAT_MEDIAN STAT_VARIANCE STAT_SD", "REG_SUMS REG_SLOPE REG_INTERCEPT RESULT",
            "UNIT_FACTOR RESULT", "UNIT_OFFSET RESULT", "START CONIC_COMPLETE CONIC_STANDARD", "START CONIC_STANDARD",
            "START CONSTANT_VALUES UNCERTAINTY_BOUND RESULT", "UNIT_FACTOR RESULT",
            "START RANK_ECHELON RANK_MINOR RANK_CASE RANK_CASE RESULT", "START TRACE_SUM", "START RANK_ECHELON NULL_FREE RESULT",
            "START RANK_ECHELON COLUMN_PIVOTS RESULT", "START CHARPOLY_DET RESULT", "START ADJ_COFACTORS ADJ_TRANSPOSE ADJ_CHECK",
            "START TANGENT_POINT DERIVATIVE_OF TANGENT_SLOPE TANGENT_FORMULA RESULT",
            "START TANGENT_POINT DERIVATIVE_OF TANGENT_SLOPE NORMAL_SLOPE NORMAL_FORMULA RESULT",
            "START TANGENT_POINT DERIVATIVE_OF TANGENT_SLOPE NORMAL_VERTICAL",
            "START DERIVATIVE_OF ARC_FORMULA ARC_SQUARE INTEGRAL_TABLE DEFINITE_BOUNDS RESULT",
            "START DERIVATIVE_OF ARC_FORMULA INTEGRAL_NUMERIC RESULT", "START AREA_CROSSINGS AREA_PIECE AREA_PIECE AREA_PIECES RESULT",
            "START VOLUME_DISCS INTEGRAL_TABLE DEFINITE_BOUNDS RESULT", "START VOLUME_SHELLS INTEGRAL_TABLE DEFINITE_BOUNDS RESULT",
            "START MEAN_FORMULA INTEGRAL_TABLE DEFINITE_BOUNDS RESULT", "START LP_CORNERS LP_VALUES LP_DUAL RESULT",
            "START LP_SIMPLEX LP_DUAL RESULT", "START LP_CORNERS LP_VALUES LP_UNBOUNDED", "START LP_INFEASIBLE",
        )
        val got = LATER.map { (latex, choice) -> Steps.of(latex, choice)?.joinToString(" ") { it.why.key.name } ?: "none" }
        assertEquals(expected.joinToString("\n"), got.joinToString("\n"))
    }

    /** The apps' sentences have a fixed number of formulas: each kind of step always brings the same number. */
    @Test
    fun eachKindOfStepHasItsNumberOfFormulas() {
        val counts = HashMap<StepKey, MutableSet<Int>>()
        for (task in tasks) for (s in Steps.of(task).orEmpty()) counts.getOrPut(s.why.key) { HashSet() } += s.why.args.size
        assertEquals("", counts.filter { it.value.size > 1 }.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    }

    /** The last step says what the card says: its formula holds the card's answer. */
    @Test
    fun theLastStepIsTheCardsAnswer() {
        val problems = ArrayList<String>()
        for (task in tasks) {
            val steps = Steps.of(task) ?: continue
            val calc = MathCalc.calculate(task)
            val answer = when (val o = calc.outcome) {
                // Families of solutions where there are any (the card gives one period's values only below them).
                is Outcome.Solved -> o.solutions.periodic.lastOrNull()?.let { if (o.solutions.degrees) calc.latexAngle(it) else calc.latex(it) }
                    ?: o.solutions.values.lastOrNull()?.let { calc.latexValue(it) }
                is Outcome.Intervals -> calc.latex(o.set)
                is Outcome.Expression -> calc.latex(o.result)
                is Outcome.Factored -> calc.latex(o.result)
                is Outcome.Value -> o.exact?.let { calc.latexValue(it) } ?: calc.latex(o.value)
                is Outcome.SystemSolved -> o.solutions.lastOrNull()?.values?.lastOrNull()?.let { calc.latexValue(it) }
                is Outcome.Primes -> calc.latex(o)
                else -> null
            } ?: continue
            if (!steps.last().latex.contains(Steps.tidy(answer))) problems += "$task: last step ${steps.last().latex}, answer $answer"
        }
        assertEquals("", problems.joinToString("\n"))
    }
}

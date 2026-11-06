package com.professoreuler.mathcalc

/**
 * What [MathCalc.calculate] hands over before its plan: the pictures asked for by name (a complex function's domain colouring,
 * a Venn diagram) and formulas with physical constants ([PhysicalConstants]). Null where the usual way works the formula out.
 */
internal object Additions {
    fun calculate(latex: String, choice: Choice?, millis: Long, fractions: Boolean, assumptions: Assumptions): Calculation? {
        if (choice?.mode == Mode.VENN || choice?.mode == Mode.COMPLEX_PLOT) {
            val flags = Flags(assumptions)
            val outcome = try {
                Budget.within(millis) {
                    if (choice.mode == Mode.VENN) venn(latex) else Domain.complex { complexPlot(latex, choice, flags) }
                }
            } catch (e: CalcException) {
                Outcome.Failed(e.failure)
            } catch (e: ArithmeticException) {
                Outcome.Failed(Failure.UNDEFINED)
            } catch (e: StackOverflowError) {
                Outcome.Failed(Failure.TOO_LARGE)
            }
            return Calculation(outcome, flags.decimalComma, flags.decimals, flags.fractions || fractions, listOf(choice), choice,
                assumptions = assumptions, ambiguities = flags.met.toSet())
        }
        return PhysicalConstants.calculate(latex, millis, assumptions)
    }

    private fun tokens(latex: String) = MathCalc.TOKEN.findAll(latex).map { it.value }.toList()

    /** venn(A ∪ B): the formula read as one of sets, even a single set (A, A′). */
    private fun venn(latex: String): Outcome {
        val reading = Logic.readSets(tokens(latex).map { if (it.length > 1 && it[0] == ',') "," else it })
            ?: throw CalcException(Failure.UNREADABLE)
        return Outcome.Venn(VennDiagram.of(reading))
    }

    /** complexplot(f, z, c, r): f of the letter on the square c ± r (the default square where none is asked for). */
    private fun complexPlot(latex: String, choice: Choice, flags: Flags): Outcome {
        val statements = Parser(tokens(latex), flags).statements()
        val s = statements.singleOrNull() ?: throw CalcException(Failure.UNREADABLE)
        val written = s.sides.filterNotNull()
        if (s.definition != null || written.size != 1 || (s.sides.size > 1 && s.sides.last() != null)) throw CalcException(Failure.UNREADABLE)
        val f = Calculus.tidy(Calculus.evaluate(written.single()))
        if (f is Expr.Mat) throw CalcException(Failure.SIZES)
        val letters = f.symbols()
        val letter = choice.letter ?: letters.singleOrNull() ?: "z"
        if (letters.any { it != letter }) throw CalcException(Failure.SEVERAL_UNKNOWNS)
        val square = choice.point?.split(',')?.map { it.trim() }
        if (square != null && square.size != 2) throw CalcException(Failure.ARGUMENTS)
        val (center, half) = square?.map { part ->
            val e = Parser(tokens(part), Flags(flags.assumptions)).statements().singleOrNull()?.sides?.singleOrNull()
                ?: throw CalcException(Failure.ARGUMENTS)
            Calculus.tidy(Calculus.evaluate(e)).also { if (it.symbols().isNotEmpty()) throw CalcException(Failure.ARGUMENTS) }
        } ?: listOf(null, null)
        return Outcome.DomainColoring(ComplexPlot.plot(f, Expr.Sym(letter), center, half))
    }
}

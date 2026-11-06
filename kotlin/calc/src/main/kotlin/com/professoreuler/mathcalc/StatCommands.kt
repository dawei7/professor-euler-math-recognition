package com.professoreuler.mathcalc

/**
 * The statistics commands, each a function with its arguments read by itself ([CommandFunctions.Shape.ARGS]): the hypothesis
 * tests (`ztest`, `ttest`, `welchtest`, `chisqgof`, `chisqindep`), the confidence intervals (`zinterval`, `tinterval`,
 * `propinterval`), the fits (`fitpoly`, `fitexp`, `fitpow`, `fitlog`, `fitlogistic`), the Markov chains (`stationary`, `markov`)
 * and a list's modes (`mode`). [CommandFunctions.read] writes a call's arguments as LaTeX, separated by ", "; [calculate] reads
 * them back, one by one: a list in brackets, a matrix, a point, a number, or one of the words `less` and `greater`.
 */
internal object StatCommands {
    val MODES: Set<Mode> = setOf(
        Mode.Z_TEST, Mode.T_TEST, Mode.WELCH_TEST, Mode.CHI_SQUARE_FIT, Mode.CHI_SQUARE_INDEPENDENCE,
        Mode.Z_INTERVAL, Mode.T_INTERVAL, Mode.PROPORTION_INTERVAL,
        Mode.FIT_POLYNOMIAL, Mode.FIT_EXPONENTIAL, Mode.FIT_POWER, Mode.FIT_LOGARITHMIC, Mode.FIT_LOGISTIC,
        Mode.STATIONARY, Mode.MARKOV, Mode.MODES,
    )

    /** An argument: a value (a number, a list, a matrix, a point) or a word. */
    sealed class Arg {
        data class Value(val e: Expr) : Arg()
        data class Word(val word: String) : Arg()
    }

    /** The words an argument may be: a test's alternative hypothesis. */
    private val WORDS = setOf("less", "greater")

    /** The calculation of a statistics command, or null where [choice] is none of them. */
    fun calculate(latex: String, choice: Choice?, millis: Long, fractions: Boolean): Calculation? {
        val mode = choice?.mode ?: return null
        if (mode !in MODES) return null
        val flags = Flags()
        val outcome = try {
            Budget.within(millis) { work(mode, latex, flags) }
        } catch (e: CalcException) {
            Outcome.Failed(e.failure)
        } catch (e: Imprecise) {
            Outcome.Failed(Failure.TOO_LONG)
        } catch (e: ArithmeticException) {
            Outcome.Failed(Failure.UNDEFINED)
        } catch (e: StackOverflowError) {
            Outcome.Failed(Failure.TOO_LARGE)
        }
        return Calculation(outcome, flags.decimalComma, flags.decimals, fractions || flags.fractions, listOf(choice), choice)
    }

    private fun work(mode: Mode, latex: String, flags: Flags): Outcome {
        val args = arguments(latex, flags)
        return when (mode) {
            Mode.Z_TEST, Mode.T_TEST, Mode.WELCH_TEST, Mode.CHI_SQUARE_FIT, Mode.CHI_SQUARE_INDEPENDENCE,
            Mode.Z_INTERVAL, Mode.T_INTERVAL, Mode.PROPORTION_INTERVAL -> Outcome.Statistical(Inference.of(mode, args))
            Mode.FIT_POLYNOMIAL, Mode.FIT_EXPONENTIAL, Mode.FIT_POWER, Mode.FIT_LOGARITHMIC, Mode.FIT_LOGISTIC ->
                Outcome.Statistical(Fits.of(mode, args))
            Mode.STATIONARY -> Outcome.Expression(Markov.stationary(single(args)))
            Mode.MARKOV -> Outcome.Expression(Markov.after(args))
            Mode.MODES -> {
                val list = Describe.entries(single(args)) ?: throw CalcException(Failure.ARGUMENTS)
                Outcome.SetValue(Describe.modes(list))
            }
            else -> throw CalcException(Failure.UNREADABLE)
        }
    }

    private fun single(args: List<Arg>): Expr {
        if (args.size != 1) throw CalcException(Failure.ARGUMENTS)
        return (args[0] as? Arg.Value)?.e ?: throw CalcException(Failure.ARGUMENTS)
    }

    /** The arguments of [latex], split at its commas outside brackets, each read. */
    fun arguments(latex: String, flags: Flags): List<Arg> = pieces(latex).map { piece ->
        val word = piece.trim()
        if (word.lowercase() in WORDS) {
            Arg.Word(word.lowercase())
        } else {
            val tokens = MathCalc.TOKEN.findAll(piece).map { it.value }.toList()
            if (tokens.isEmpty()) throw CalcException(Failure.ARGUMENTS)
            val statements = Parser(tokens, flags).statements()
            val sides = statements.singleOrNull()?.sides ?: throw CalcException(Failure.ARGUMENTS)
            if (sides.size != 1 || sides[0] == null) throw CalcException(Failure.ARGUMENTS)
            val e = Calculus.evaluate(sides[0]!!)
            if (e.symbols().isNotEmpty() || e.hasOp()) throw CalcException(Failure.ARGUMENTS)
            Arg.Value(e)
        }
    }

    /** [latex] split at the commas outside every bracket. */
    fun pieces(latex: String): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        var start = 0
        for ((k, c) in latex.withIndex()) {
            when (c) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
            }
            if (c == ',' && depth == 0) {
                out += latex.substring(start, k).trim()
                start = k + 1
            }
        }
        out += latex.substring(start).trim()
        if (out.any { it.isEmpty() }) throw CalcException(Failure.ARGUMENTS)
        return out
    }
}

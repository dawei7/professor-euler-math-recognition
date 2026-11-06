package com.professoreuler.mathcalc

/**
 * The functions of an algorithms course (owner, 6 November 2026: what an "Algorithmik" course has learners compute by hand):
 * divide-and-conquer recurrences (`mastertheorem`, `akrabazzi`), growth as n → ∞ (`asymptotic`, `growthorder`), dynamic programs
 * (`editdistance`, `lcs`, `knapsack`, `coinchange`, `coinways`), `huffman` and the sorting traces (`insertionsort`, `selectionsort`,
 * `bubblesort`, `mergesort`, `quicksort`, `binarysearch`).
 *
 * Their arguments are not one formula: words, lists, a recurrence, several functions. A call is read here ([read]) into a [Typed]
 * whose LaTeX is the call itself, written as `\operatorname{name}\left(…\right)` (so the Calculator shows it as it was typed and the
 * editor takes it back), and worked out here ([calculate]) before the parser sees it.
 *
 * How words are written: as their letters, without quotation marks (`editdistance(kitten, sitting)`). The keys have letters and no
 * quotation mark, and in these functions an argument is always a word, so its letters cannot be read as anything else (never as a
 * product of letters, never e as Euler's number); a quotation mark, a space or any other sign in a word is refused
 * ([Failure.ARGUMENTS]). Upper and lower case are different letters. Lists are numbers in square brackets (`[1, 2, 5]`), the
 * sorting functions' own list also as bare numbers (`insertionsort(5, 2, 4)`), as `stats` takes its data.
 */
object Algorithms {
    private val NAMES: Map<String, Mode> = mapOf(
        "mastertheorem" to Mode.MASTER_THEOREM, "akrabazzi" to Mode.AKRA_BAZZI, "asymptotic" to Mode.ASYMPTOTIC,
        "growthorder" to Mode.GROWTH_ORDER, "editdistance" to Mode.EDIT_DISTANCE, "lcs" to Mode.LCS, "knapsack" to Mode.KNAPSACK,
        "coinchange" to Mode.COIN_CHANGE, "coinways" to Mode.COIN_WAYS, "huffman" to Mode.HUFFMAN,
        "insertionsort" to Mode.INSERTION_SORT, "selectionsort" to Mode.SELECTION_SORT, "bubblesort" to Mode.BUBBLE_SORT,
        "mergesort" to Mode.MERGE_SORT, "quicksort" to Mode.QUICKSORT, "binarysearch" to Mode.BINARY_SEARCH,
    )
    private val NAME_OF: Map<Mode, String> = NAMES.entries.associate { (k, v) -> v to k }

    /** The modes of these functions. */
    val MODES: Set<Mode> = NAMES.values.toSet()

    private val SORTS = mapOf(Mode.INSERTION_SORT to Sorting.Algorithm.INSERTION, Mode.SELECTION_SORT to Sorting.Algorithm.SELECTION,
        Mode.BUBBLE_SORT to Sorting.Algorithm.BUBBLE, Mode.MERGE_SORT to Sorting.Algorithm.MERGE, Mode.QUICKSORT to Sorting.Algorithm.QUICK)

    /** The most functions growthorder sorts. */
    const val MAX_FUNCTIONS = 12

    // ---------------------------------------------------------------------------------------------------------------------
    // Reading a call.

    /** The call of [mode]'s function with [args] (as typed, trimmed), as its [Typed]; null where they are not its arguments. */
    fun read(mode: Mode, args: List<String>): Typed? = try {
        val written = write(mode, args)
        written?.let { Typed(call(NAME_OF.getValue(mode), it), Choice(mode)) }
    } catch (e: CalcException) {
        null
    } catch (e: RuntimeException) {
        null
    }

    private fun call(name: String, args: List<String>) = "\\operatorname{$name}\\left(" + args.joinToString(", ") + "\\right)"

    /** The arguments as the call writes them, each checked for its kind; null where one is not what the function takes. */
    private fun write(mode: Mode, args: List<String>): List<String>? = when (mode) {
        Mode.MASTER_THEOREM, Mode.AKRA_BAZZI -> args.singleOrNull()?.takeIf { '=' in it }?.let { listOf(TextInput.latex(it)) }
        Mode.ASYMPTOTIC -> if (args.size == 2) formulas(args) else null
        Mode.GROWTH_ORDER -> if (args.size in 2..MAX_FUNCTIONS) formulas(args) else null
        Mode.EDIT_DISTANCE, Mode.LCS -> if (args.size == 2 && args.all { word(it) != null }) args.map { "\\text{${word(it)}}" } else null
        Mode.KNAPSACK -> if (args.size == 3 && list(args[0]) != null && list(args[1]) != null && number(args[2]) != null)
            listOf(listLatex(args[0]), listLatex(args[1]), TextInput.latex(args[2])) else null
        Mode.COIN_CHANGE, Mode.COIN_WAYS, Mode.BINARY_SEARCH -> if (args.size == 2 && list(args[0]) != null && number(args[1]) != null)
            listOf(listLatex(args[0]), TextInput.latex(args[1])) else null
        Mode.HUFFMAN -> huffmanArguments(args)?.let { (symbols, weights) ->
            if (args.any { '=' in it }) symbols.indices.map { "\\text{${symbols[it]}}=" + TextInput.latex(weights[it]) }
            else weights.map { TextInput.latex(it) }
        }
        in SORTS.keys -> sortArguments(mode, args)?.let { (items, pivot) -> items.map { TextInput.latex(it) } + listOfNotNull(pivot?.let { "\\text{$it}" }) }
        else -> null
    }

    private fun formulas(args: List<String>): List<String>? = if (args.any { '=' in it || it.isBlank() }) null else args.map { TextInput.latex(it) }

    /** A word: letters and digits only (the editor's function keys, \sin, counted as their letters). */
    private fun word(s: String): String? {
        val w = s.trim().replace(Regex("""\\(sin|cos|tan|ln|log|exp|lim|arcsin|arccos|arctan)(?![A-Za-z])\s*"""), "$1")
        return w.takeIf { Regex("""[A-Za-z0-9]+""").matches(it) }
    }

    /** A list in square brackets: its elements as typed. */
    private fun items(s: String): List<String>? {
        val t = s.trim()
        if (!t.startsWith("[") || !t.endsWith("]")) return null
        val inner = t.substring(1, t.length - 1)
        if (inner.isBlank()) return null
        return split(inner)?.map { it.trim() }?.takeIf { it.none(String::isEmpty) }
    }

    private fun list(s: String): List<Rational>? = items(s)?.map { number(it) ?: return null }

    private fun listLatex(s: String): String = "[" + items(s)!!.joinToString(", ") { TextInput.latex(it) } + "]"

    /** A number as typed: whole, negative, a decimal with a point, a fraction; no letters. */
    private fun number(s: String): Rational? {
        if (s.isBlank()) return null
        return try {
            val tokens = MathCalc.TOKEN.findAll(TextInput.latex(s)).map { it.value }.toList()
            val st = Parser(tokens, Flags()).statements().singleOrNull() ?: return null
            if (st.relations.isNotEmpty()) return null
            val e = Calculus.tidy(Calculus.evaluate(st.sides.filterNotNull().singleOrNull() ?: return null))
            rational(e)
        } catch (e: CalcException) {
            null
        }
    }

    /** The numbers to sort (bare, or one list in brackets) and quicksort's pivot rule (first, last or middle) after them. */
    private fun sortArguments(mode: Mode, args: List<String>): Pair<List<String>, String?>? {
        var rest = args
        var pivot: String? = null
        if (mode == Mode.QUICKSORT && rest.size >= 2 && word(rest.last())?.lowercase() in PIVOTS.keys) {
            pivot = word(rest.last())!!.lowercase()
            rest = rest.dropLast(1)
        }
        val items = if (rest.size == 1 && rest[0].trim().startsWith("[")) items(rest[0]) ?: return null else rest
        if (items.isEmpty() || items.size > Sorting.MAX_LENGTH || items.any { number(it) == null }) return null
        return items to pivot
    }

    private val PIVOTS = mapOf("last" to Sorting.Pivot.LAST, "first" to Sorting.Pivot.FIRST, "middle" to Sorting.Pivot.MIDDLE)

    /** Huffman's symbols and weights: all written a = 45 (symbols words), or all bare weights (the symbols a, b, c, … in order). */
    private fun huffmanArguments(args: List<String>): Pair<List<String>, List<String>>? {
        if (args.size < 2) return null
        val pairs = args.count { '=' in it }
        return when (pairs) {
            args.size -> {
                val split = args.map { a -> a.split('=').map { it.trim() }.takeIf { it.size == 2 } ?: return null }
                val symbols = split.map { word(it[0]) ?: return null }
                if (split.any { number(it[1]) == null }) return null
                symbols to split.map { it[1] }
            }
            0 -> {
                if (args.size > 26 || args.any { number(it) == null }) return null
                args.indices.map { ('a' + it).toString() } to args
            }
            else -> null
        }
    }

    /** [s] split at its commas outside brackets; null where it holds a semicolon there. */
    private fun split(s: String): List<String>? {
        val out = ArrayList<String>()
        var depth = 0
        val current = StringBuilder()
        for (c in s) {
            when (c) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
            }
            if (c == ';' && depth == 0) return null
            if (c == ',' && depth == 0) {
                out += current.toString()
                current.setLength(0)
            } else current.append(c)
        }
        out += current.toString()
        return out
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Working a call out.

    /** The call [latex] is, as [read] writes it (or typed as such): its mode and arguments; null where it is none of these. */
    private fun callOf(latex: String): Pair<Mode, List<String>>? {
        val t = latex.trim()
            .replace(Regex("""\\operatorname\{([A-Za-z]+)\}"""), "$1")
            .replace(Regex("""\\(?:text|mathrm)\{([^{}]*)\}"""), "$1")
            .replace("\\left(", "(").replace("\\right)", ")")
            .replace("\\ ", " ").replace("\\,", " ")
        val m = Regex("""^([A-Za-z]+)\s*\((.*)\)$""", RegexOption.DOT_MATCHES_ALL).matchEntire(t) ?: return null
        val mode = NAMES[m.groupValues[1].lowercase()] ?: return null
        val inner = m.groupValues[2]
        var depth = 0
        for (c in inner) {
            if (c == '(') depth++
            if (c == ')' && --depth < 0) return null
        }
        if (depth != 0) return null
        val args = split(inner)?.map { it.trim() } ?: return mode to emptyList()
        return mode to args
    }

    /** The modes that fit the call: those that work on the same arguments. */
    private fun choicesOf(mode: Mode, args: List<String>): List<Mode> = when (mode) {
        Mode.MASTER_THEOREM, Mode.AKRA_BAZZI -> listOf(mode, if (mode == Mode.MASTER_THEOREM) Mode.AKRA_BAZZI else Mode.MASTER_THEOREM)
        Mode.ASYMPTOTIC -> listOf(Mode.ASYMPTOTIC, Mode.GROWTH_ORDER)
        Mode.GROWTH_ORDER -> if (args.size == 2) listOf(Mode.GROWTH_ORDER, Mode.ASYMPTOTIC) else listOf(Mode.GROWTH_ORDER)
        Mode.EDIT_DISTANCE -> listOf(Mode.EDIT_DISTANCE, Mode.LCS)
        Mode.LCS -> listOf(Mode.LCS, Mode.EDIT_DISTANCE)
        Mode.COIN_CHANGE -> listOf(Mode.COIN_CHANGE, Mode.COIN_WAYS)
        Mode.COIN_WAYS -> listOf(Mode.COIN_WAYS, Mode.COIN_CHANGE)
        else -> listOf(mode)
    }

    /**
     * [latex] worked out where it is a call of one of these functions: [choice] where it fits the call (Akra–Bazzi for a master
     * recurrence, the LCS of two words given for their edit distance), else the call's own; null where it is no such call.
     */
    fun calculate(latex: String, choice: Choice?, millis: Long, assumptions: Assumptions): Calculation? {
        val (mode, args) = callOf(latex) ?: return null
        val flags = Flags(assumptions)
        val choices = choicesOf(mode, args).map { Choice(it) }
        val chosen = choice?.takeIf { it in choices } ?: choices[0]
        val decimals = args.any { Regex("""\d\.\d""").containsMatchIn(it) }
        val outcome = try {
            if (write(mode, args) == null) throw CalcException(Failure.ARGUMENTS)
            Budget.within(millis) { work(chosen.mode, args, flags) }
        } catch (e: CalcException) {
            Outcome.Failed(e.failure)
        } catch (e: ArithmeticException) {
            Outcome.Failed(Failure.UNDEFINED)
        } catch (e: StackOverflowError) {
            Outcome.Failed(Failure.TOO_LARGE)
        }
        return Calculation(outcome, decimalComma = false, decimals = decimals, fractions = !decimals, choices = choices, choice = chosen,
            assumptions = assumptions, ambiguities = flags.met.toSet())
    }

    private fun work(mode: Mode, args: List<String>, flags: Flags): Outcome = when (mode) {
        Mode.MASTER_THEOREM -> Outcome.DivideAndConquer(DivideConquer.master(DivideConquer.read(args[0], flags)))
        Mode.AKRA_BAZZI -> Outcome.DivideAndConquer(DivideConquer.akraBazzi(DivideConquer.read(args[0], flags)))
        Mode.ASYMPTOTIC, Mode.GROWTH_ORDER -> {
            val fs = args.map { formula(it, flags) }
            val letters = fs.flatMap { it.symbols() }.toSet()
            if (letters.size > 1) throw CalcException(Failure.SEVERAL_UNKNOWNS)
            val x = letters.singleOrNull() ?: "n"
            Outcome.Growth(if (mode == Mode.ASYMPTOTIC) Asymptotics.compare(fs[0], fs[1], x) else Asymptotics.sort(fs, x))
        }
        Mode.EDIT_DISTANCE -> Outcome.DynamicTable(Dynamic.editDistance(word(args[0])!!, word(args[1])!!))
        Mode.LCS -> Outcome.DynamicTable(Dynamic.lcs(word(args[0])!!, word(args[1])!!))
        Mode.KNAPSACK -> {
            val values = list(args[0])!!
            val weights = list(args[1])!!.map { whole(it) }
            Outcome.DynamicTable(Dynamic.knapsack(values, weights, whole(number(args[2])!!)))
        }
        Mode.COIN_CHANGE -> Outcome.DynamicTable(Dynamic.coinChange(list(args[0])!!.map { whole(it) }, whole(number(args[1])!!)))
        Mode.COIN_WAYS -> Outcome.DynamicTable(Dynamic.coinWays(list(args[0])!!.map { whole(it) }, whole(number(args[1])!!)))
        Mode.HUFFMAN -> {
            val (symbols, weights) = huffmanArguments(args)!!
            Outcome.HuffmanCode(Huffman.code(symbols, weights.map { number(it)!! }))
        }
        Mode.BINARY_SEARCH -> Outcome.Trace(Sorting.search(list(args[0])!!, number(args[1])!!))
        else -> {
            val algorithm = SORTS[mode] ?: throw CalcException(Failure.UNREADABLE)
            val (items, pivot) = sortArguments(mode, args)!!
            Outcome.Trace(Sorting.sort(algorithm, items.map { number(it)!! }, PIVOTS[pivot] ?: Sorting.Pivot.LAST))
        }
    }

    /** Certified digits as a decimal, written as [style] writes decimals: 0.6942419136. */
    fun decimal(d: Decimal, style: Style, latex: Boolean): String = when (d) {
        Decimal.Zero -> "0"
        is Decimal.Small -> "0"
        is Decimal.Digits -> {
            val unscaled = java.math.BigInteger(d.digits).let { if (d.negative) it.negate() else it }
            Format.decimal(java.math.BigDecimal(unscaled, d.digits.length - 1 - d.exponent), style, latex, exact = d.exact)
        }
    }

    /** A whole number, or [Failure.UNDEFINED] (a weight, a coin, an amount, a capacity of 2.5). */
    private fun whole(r: Rational): Int {
        if (!r.isInteger) throw CalcException(Failure.UNDEFINED)
        if (r.num.bitLength() > 30) throw CalcException(Failure.TOO_LARGE)
        return r.num.toInt()
    }

    private fun formula(s: String, flags: Flags): Expr {
        val tokens = MathCalc.TOKEN.findAll(TextInput.latex(s)).map { it.value }.toList()
        val st = Parser(tokens, flags).statements().singleOrNull() ?: throw CalcException(Failure.UNREADABLE)
        if (st.relations.isNotEmpty() || st.definition != null) throw CalcException(Failure.UNREADABLE)
        val side = st.sides.filterNotNull().singleOrNull() ?: throw CalcException(Failure.UNREADABLE)
        val e = Calculus.tidy(Calculus.evaluate(side))
        if (e is Expr.Mat || e.hasOp()) throw CalcException(Failure.UNREADABLE)
        return e
    }
}

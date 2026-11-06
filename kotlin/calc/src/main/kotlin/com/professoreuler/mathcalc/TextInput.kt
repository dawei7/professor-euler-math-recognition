package com.professoreuler.mathcalc

import java.util.Locale

/**
 * A line typed on the keyboard, read: the formula in LaTeX, and the choice its words asked for (null where none). Its answers
 * are written with exact fractions ([fractions]): a keyboard's `/` is its fraction bar, and sin 30° is ½, as a calculator's
 * learner expects.
 */
data class Typed(val latex: String, val choice: Choice? = null, val fractions: Boolean = true)

/**
 * Reads what a learner types: math written in a line (`x^2 + 3x - 4 = 0`, `sqrt(2)`, `e^(i pi)`, `integrate x^2 from 0 to 1`)
 * into the LaTeX the calculator reads, and the command words in front of it in the app's language, English always too
 * ("solve", "löse", "Ableitung von … nach y"). A fixed vocabulary per language, no AI: [Vocabulary.ALL].
 */
object TextInput {
    /** [text] as typed; [language] the app's language ("de", "en", "pt-BR"); English words are always understood. */
    fun read(text: String, language: String = "en"): Typed {
        // ¿es 97 primo?, هل 97 أولي؟: the marks of a question are no math.
        val trimmed = westernDigits(text.trim().trimStart('¿', '¡').trimEnd('؟').trim())
        if (trimmed.isEmpty()) return Typed("")
        // A line whose words cannot be made sense of is the math it holds: reading never fails.
        return try {
            readWords(trimmed, language)
        } catch (e: CalcException) {
            Typed(latex(trimmed))
        } catch (e: RuntimeException) {
            Typed(latex(trimmed))
        } catch (e: StackOverflowError) {
            Typed(latex(trimmed))
        }
    }

    /** Arabic-Indic and Persian digits as the digits the calculator reads, and the Arabic decimal separator as a point. */
    internal fun westernDigits(text: String): String {
        if (text.none { it in '\u0660'..'\u0669' || it in '\u06F0'..'\u06F9' || it == '\u066B' }) return text
        return String(CharArray(text.length) { k ->
            val c = text[k]
            when (c) {
                in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                '\u066B' -> '.'
                else -> c
            }
        })
    }

    private fun readWords(text: String, language: String): Typed {
        val vocabularies = listOfNotNull(Vocabulary.of(language), Vocabulary.ENGLISH).distinct()
        conversion(text)?.let { return it }
        // "maximize x*y subject to x + y = 10": the constraint a line of its own, as "x*y; x + y = 10" writes it.
        val trimmed = vocabularies.fold(text) { t, v -> v.constrained(t) }
        derivativeOperator(trimmed)?.let { return it }
        // The words looked for apart from the math, also where a script writes no spaces (求导x^2, x^2を0から1まで積分).
        // Again once spaced, where a phrase meets the math without a space ("x+y=10のもとで").
        val spaced = vocabularies.fold(Vocabulary.spaced(trimmed)) { t, v -> v.constrained(t) }
        for (v in vocabularies) {
            val (command, rest) = v.command(spaced) ?: continue
            return commandOf(command, rest, vocabularies)
        }
        // Languages that put the verb last: "x^2-1を因数分解", "x^2-1 का गुणनखंड करें".
        for (v in vocabularies) {
            if (!v.commandLast) continue
            val (command, rest) = v.commandAtEnd(spaced) ?: continue
            return commandOf(command, rest, vocabularies)
        }
        // A number's predicate after it, in any language: "97 es primo", "97 est-il premier ?", "97 простое?".
        for (v in vocabularies) {
            val (command, rest) = v.commandAtEnd(spaced) ?: continue
            if (command == Command.NUMBER_FACTS) return commandOf(command, rest, vocabularies)
        }
        // A command on one of several lines: "求 x*y 的最大值; x+y=10", the constraint after it.
        val lines = spaced.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size > 1) {
            val particles = vocabularies.flatMap { it.particles }.toSet()
            for ((i, line) in lines.withIndex()) for (v in vocabularies) {
                val (command, own) = v.command(line) ?: (if (v.commandLast) v.commandAtEnd(line) else null) ?: continue
                var words = own.split(' ').filter { it.isNotEmpty() }
                while (words.size > 1 && Vocabulary.lower(words.last()) in particles) words = words.dropLast(1)
                return commandOf(command, lines.mapIndexed { k, l -> if (k == i) words.joinToString(" ") else l }.joinToString("; "), vocabularies)
            }
        }
        return Typed(latex(trimmed))
    }

    /**
     * What a typed line asks for, typeset as a learner would write it: the formula, a derivative as d/dx (…), an
     * antiderivative as ∫ … dx. The Calculator shows it as the line is typed ("Read as"), so a misreading shows at once.
     */
    fun display(typed: Typed): String {
        val choice = typed.choice ?: return typed.latex
        if (choice.mode == Mode.UNITS) return "${typed.latex}\\,\\text{${choice.letter}} \\to \\text{${choice.point}}"
        val letter = choice.letter ?: mainLetterOf(typed.latex)
        val name = Format.name(letter, latex = true)
        return when (choice.mode) {
            Mode.DERIVATIVE -> "\\frac{d}{d$name}\\left(${typed.latex}\\right)"
            Mode.INTEGRAL -> "\\int ${typed.latex} \\, d$name"
            else -> typed.latex
        }
    }

    /** "d/dx x^2" and "d/dy (…)": a derivative in the letter after d/d. */
    private fun derivativeOperator(text: String): Typed? {
        val m = DD.find(text) ?: return null
        val letter = m.groupValues[1]
        val body = text.substring(m.range.last + 1).trim()
        if (body.isEmpty()) return null
        return Typed(latex(body), Choice(Mode.DERIVATIVE, letter))
    }

    private fun commandOf(command: Command, rest: String, vocabularies: List<Vocabulary>): Typed {
        var slots = split(rest, vocabularies)
        // Nothing before the command but a formula where its letter goes ("对 x^3 求导"): that is the formula.
        // A sum's or a limit's "for" holds its index or approach, never the formula.
        if (slots[Slot.MAIN].isNullOrBlank() && command != Command.SUM && command != Command.LIMIT) {
            val where = listOf(Slot.RESPECT, Slot.FOR).firstOrNull { slots[it] != null && letterOf(slots[it]) == null }
            if (where != null) slots = slots - where + (Slot.MAIN to slots.getValue(where))
        }
        var main = slots[Slot.MAIN].orEmpty()
        // "y'=y; y(0)=1 के लिए ऑयलर विधि": a line of the system taken by a postposition belongs to the formula.
        if (main.trimEnd().endsWith(";")) {
            listOf(Slot.FOR, Slot.RESPECT).firstNotNullOfOrNull { s -> slots[s]?.takeIf { '=' in it } }?.let { main = main.trimEnd() + " " + it }
        }
        when (command) {
            Command.SOLVE -> {
                val letter = letterOf(slots[Slot.RESPECT] ?: slots[Slot.FOR])
                return Typed(latex(main), Choice(Mode.SOLVE, letter))
            }
            Command.FACTOR -> return Typed(latex(main), Choice(Mode.FACTOR))
            Command.EXPAND -> return Typed(latex(main), Choice(Mode.EXPAND))
            Command.SIMPLIFY -> return Typed(latex(main), Choice(Mode.SIMPLIFY))
            Command.CALCULATE -> return Typed(latex(main), Choice(Mode.CALCULATE))
            Command.PARTIAL_FRACTIONS -> return Typed(latex(main), Choice(Mode.PARTIAL_FRACTIONS))
            Command.DETERMINANT -> return Typed(latex(main), Choice(Mode.DETERMINANT))
            Command.INVERSE -> return Typed(latex(main), Choice(Mode.INVERSE))
            Command.EIGEN -> return Typed(latex(main), Choice(Mode.EIGEN))
            Command.ROW_REDUCE -> return Typed(latex(main), Choice(Mode.ROW_REDUCE))
            Command.TRANSPOSE -> return Typed(latex(main), Choice(Mode.TRANSPOSE))
            Command.DECOMPOSE -> return Typed(latex(main), Choice(Mode.DECOMPOSE))
            Command.MATRIX_EXP -> return Typed(latex(main), Choice(Mode.MATRIX_EXP))
            Command.GRADIENT -> return Typed(latex(main), Choice(Mode.GRADIENT))
            Command.DIVERGENCE -> return Typed(latex(main), Choice(Mode.DIVERGENCE))
            Command.CURL -> return Typed(latex(main), Choice(Mode.CURL))
            Command.JACOBIAN -> return Typed(latex(main), Choice(Mode.JACOBIAN))
            Command.LAPLACIAN -> return Typed(latex(main), Choice(Mode.LAPLACIAN))
            Command.LEAST_SQUARES -> return Typed(latex(main), Choice(Mode.LEAST_SQUARES))
            Command.EXTREMA -> return Typed(latex(main), Choice(Mode.EXTREMA))
            Command.LAPLACE -> return Typed(latex(main), Choice(Mode.LAPLACE))
            Command.INVERSE_LAPLACE -> return Typed(latex(main), Choice(Mode.INVERSE_LAPLACE))
            Command.EULER -> return Typed(latex(main), Choice(Mode.EULER))
            Command.RUNGE_KUTTA -> return Typed(latex(main), Choice(Mode.RUNGE_KUTTA))
            Command.POLAR -> return Typed(latex(main), Choice(Mode.POLAR))
            Command.RESIDUES -> return Typed(latex(main), Choice(Mode.RESIDUES))
            Command.NUMBER_FACTS -> return Typed(latex(main), Choice(Mode.NUMBER_FACTS))
            Command.STATISTICS -> return Typed(listLatex(main), Choice(Mode.STATISTICS))
            Command.SEQUENCE -> return Typed(listLatex(main), Choice(Mode.SEQUENCE))
            Command.REGRESSION -> return Typed(pointsLatex(main, matrix = true), Choice(Mode.REGRESSION))
            Command.TRUTH_TABLE -> return Typed(latex(logicWords(main, vocabularies.map { it.language.substringBefore('-') })), Choice(Mode.LOGIC))
            Command.TRIANGLE -> return Typed(triangleLatex(main), Choice(Mode.TRIANGLE))
            Command.CONIC -> return Typed(latex(main), Choice(Mode.CONIC))
            Command.LINE -> return Typed(pointsLatex(main, matrix = false), Choice(Mode.LINE))
            Command.PLANE -> return Typed(pointsLatex(main, matrix = false), Choice(Mode.PLANE))
            Command.DIOPHANTINE -> return Typed(latex(main), Choice(Mode.DIOPHANTINE))
            Command.NEWTON -> {
                // "newton x^2 - 2 from 1", "… at 1": the start.
                val start = slots[Slot.FROM] ?: slots[Slot.AS]
                return Typed(latex(main), Choice(Mode.NEWTON, point = start?.let { latex(it) }))
            }
            Command.BISECTION, Command.FOURIER -> {
                // "bisection x^3 - x - 1 from 1 to 2", "… on [1, 2]"; a Fourier series "from -2 to 2" or "on [-2, 2]".
                val (body, interval) = trailingInterval(main)
                val from = slots[Slot.FROM] ?: interval?.first
                val to = slots[Slot.TO] ?: interval?.second
                return if (command == Command.FOURIER) Typed(latex(body), Choice(Mode.FOURIER, point = to?.let { latex(it) }))
                else Typed(latex(body), Choice(Mode.BISECTION, point = if (from != null && to != null) latex(from) + "," + latex(to) else null))
            }
            Command.TRAPEZOID, Command.SIMPSON -> {
                val mode = if (command == Command.SIMPSON) Mode.SIMPSON else Mode.TRAPEZOID
                val from = slots[Slot.FROM]
                val to = slots[Slot.TO]
                if (from != null && to != null) {
                    val (body, dLetter) = stripDifferential(main)
                    val bodyLatex = latex(body)
                    val v = dLetter ?: mainLetterOf(bodyLatex)
                    return Typed("\\int_{${latex(from)}}^{${latex(to)}} $bodyLatex \\, d$v", Choice(mode))
                }
                return Typed(latex(main), Choice(mode))
            }
            Command.SERIES -> {
                // "taylor series of sin x at x = 1", "Taylorreihe von e^x um 0": the point after "at" or "um", 0 where none is.
                val approach = (slots[Slot.AS] ?: slots[Slot.FOR])?.let { a -> vocabularies.fold(a) { t, v -> v.arrowed(t) } }
                var letter: String? = null
                var point: String? = null
                if (approach != null) {
                    val at = ARROW.find(approach) ?: Regex("=").find(approach)
                    if (at != null) {
                        letter = letterOf(approach.substring(0, at.range.first).trim())
                        point = approach.substring(at.range.last + 1).trim()
                    } else if (letterOf(approach) != null) {
                        letter = letterOf(approach)
                    } else {
                        point = approach.trim()
                    }
                }
                val bodyLatex = latex(main)
                return Typed(bodyLatex, Choice(Mode.SERIES, letter ?: mainLetterOf(bodyLatex), point = point?.let { latex(it) } ?: "0"))
            }
            Command.PLOT -> return Typed(latex(main), Choice(Mode.GRAPH, letterOf(slots[Slot.FOR] ?: slots[Slot.RESPECT])))
            Command.DERIVATIVE -> {
                val (body, dLetter) = stripDifferential(main)
                val letter = letterOf(slots[Slot.RESPECT] ?: slots[Slot.FOR]) ?: dLetter
                return Typed(latex(body), Choice(Mode.DERIVATIVE, letter))
            }
            Command.INTEGRAL -> {
                // "intégrale de 0 à 3 de x^2", "integral de x^2 entre 0 y 3": the bounds before the formula or around "and".
                boundsOf(rest, vocabularies)?.let { (from, to, written) ->
                    val (body, dLetter) = stripDifferential(written)
                    val bodyLatex = latex(body)
                    val v = dLetter ?: mainLetterOf(bodyLatex)
                    return Typed("\\int_{${latex(from)}}^{${latex(to)}} $bodyLatex \\, d$v")
                }
                val (body, dLetter) = stripDifferential(main)
                val letter = letterOf(slots[Slot.RESPECT] ?: slots[Slot.FOR]) ?: dLetter
                val from = slots[Slot.FROM]
                val to = slots[Slot.TO]
                if (from != null && to != null) {
                    val bodyLatex = latex(body)
                    val v = letter ?: mainLetterOf(bodyLatex)
                    return Typed("\\int_{${latex(from)}}^{${latex(to)}} $bodyLatex \\, d$v")
                }
                return Typed(latex(body), Choice(Mode.INTEGRAL, letter))
            }
            Command.LIMIT -> {
                // "as x -> 0", "for x gegen 0", "x -> 0" in the main part's tail
                var variable: String? = null
                var point: String? = null
                val approach = (slots[Slot.AS] ?: slots[Slot.FOR])?.let { a -> vocabularies.fold(a) { t, v -> v.arrowed(t) } }
                if (approach != null) {
                    val arrow = ARROW.find(approach)
                    if (arrow != null) {
                        variable = approach.substring(0, arrow.range.first).trim()
                        point = approach.substring(arrow.range.last + 1).trim()
                    } else if (letterOf(approach) != null) {
                        variable = approach.trim()
                    } else {
                        // "limite de x^2 en 2": the point alone.
                        point = approach.trim()
                    }
                }
                // "voor x naar 2": a word for "with respect to" that names the point in a limit.
                if (point == null) point = slots[Slot.TO] ?: slots[Slot.RESPECT]
                var body = main
                if (variable == null) {
                    // "lim x->0 sin(x)/x"
                    val lead = LEADING_APPROACH.find(main)
                    if (lead != null) {
                        variable = lead.groupValues[1]
                        point = lead.groupValues[2]
                        body = main.substring(lead.range.last + 1)
                    }
                }
                val bodyLatex = latex(body)
                val v = letterOf(variable) ?: mainLetterOf(bodyLatex)
                val p = point?.let { latex(it) } ?: "0"
                return Typed("\\lim_{$v \\to $p} $bodyLatex")
            }
            Command.SUM -> {
                val bodyLatex = latex(main)
                // The index after "for", or as the lower bound itself: "k=1から", "从 k=1 到 n".
                val index = slots[Slot.FOR] ?: slots[Slot.RESPECT] ?: slots[Slot.FROM]?.takeIf { '=' in it }
                val eq = index?.indexOf('=') ?: -1
                val v = letterOf(if (eq > 0) index!!.substring(0, eq) else index) ?: "k"
                val lower = if (eq > 0) index!!.substring(eq + 1).trim() else slots[Slot.FROM] ?: "1"
                val upper = slots[Slot.TO] ?: "\\infty"
                return Typed("\\sum_{$v=${latex(lower)}}^{${latex(upper)}} $bodyLatex")
            }
        }
    }

    // --------------------------------------------------------------------------------------------------------------------
    // Slots: the parts of a command between its connecting words.

    private enum class Slot { MAIN, FROM, TO, FOR, RESPECT, AS }

    private fun split(rest: String, vocabularies: List<Vocabulary>): Map<Slot, String> {
        val words = rest.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val slots = LinkedHashMap<Slot, MutableList<String>>()
        var current = Slot.MAIN
        var depth = 0
        var k = 0
        val ignored = vocabularies.flatMap { it.ignored }.toSet()
        val closers = vocabularies.flatMap { it.closers }.toSet()
        // "of" right after the command belongs to it: "derivative of x²", "Ableitung von x²".
        if (words.isNotEmpty() && vocabularies.any { v -> Vocabulary.lower(words[0]) in v.of }) k = 1
        while (k < words.size) {
            if (depth == 0) {
                if (Vocabulary.lower(words[k]) in ignored) { k++; continue }
                // "当 x->0 时": the word that closes a part, after which the formula goes on.
                if (Vocabulary.lower(words[k]) in closers) { current = Slot.MAIN; k++; continue }
                // "limite per x->2 di x^2", "suma para k=1 hasta n de k": "of" after the other parts begins the formula.
                if (current != Slot.MAIN && slots[Slot.MAIN].isNullOrEmpty() && vocabularies.any { Vocabulary.lower(words[k]) in it.of }) {
                    current = Slot.MAIN
                    k++
                    continue
                }
                val match = vocabularies.firstNotNullOfOrNull { v -> v.connective(words, k, current)?.let { v to it } }
                if (match != null) {
                    val (v, found) = match
                    if (v.postpositional) {
                        // "0から", "x에 대해", "0 से": the connective follows its value, the word before it.
                        val before = slots[current]?.removeLastOrNull()
                        if (before != null) slots.getOrPut(found.first) { mutableListOf() } += before
                        current = Slot.MAIN
                    } else {
                        current = found.first
                    }
                    k += found.second
                    continue
                }
            }
            val w = words[k]
            depth += w.count { it == '(' || it == '[' || it == '{' } - w.count { it == ')' || it == ']' || it == '}' }
            slots.getOrPut(current) { mutableListOf() } += w
            k++
        }
        // Particles left at the end of a part: "x^2-1 を", "x^2 का", "x^2-1 'i", "3 的".
        val particles = vocabularies.flatMap { it.particles }.toSet()
        for (part in slots.values) while (part.size > 1 && Vocabulary.lower(part.last()) in particles) part.removeAt(part.size - 1)
        return slots.mapValues { it.value.joinToString(" ") }
    }

    /**
     * An integral's bounds written before its formula ("de 0 à 3 de x^2", "od 0 do 3 z x^2", "dari 0 sampai 3 x^2") or after it
     * with "between … and …" ("x^2 entre 0 y 3"): the bounds and the formula, or null.
     */
    private fun boundsOf(rest: String, vocabularies: List<Vocabulary>): Triple<String, String, String>? {
        val words = rest.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        fun low(k: Int) = words.getOrNull(k)?.let { Vocabulary.lower(it) }
        for (v in vocabularies) {
            if (v.postpositional) continue
            // With its "from" ("de 0 à 3 de x^2"), or without it where the command's own phrase took it ("Integral von" 0 bis 3
            // von x^2), and then only with the "of" before the formula, so "x bis 3" alone is never taken for bounds.
            val withFrom = low(0) in v.from.filter { it.size == 1 }.map { it[0] }
            val start = if (withFrom) 1 else 0
            for (to in v.to.sortedByDescending { it.size }) {
                val end = start + 1 + to.size
                if (words.size <= end + 1) continue
                if (!to.indices.all { low(start + 1 + it) == to[it] }) continue
                var body = words.drop(end + 1)
                val ofFirst = body.size > 1 && Vocabulary.lower(body[0]) in v.of
                if (!withFrom && !ofFirst && !NUMBER_LIKE.matches(words[start])) continue
                if (ofFirst) body = body.drop(1)
                if (body.isNotEmpty()) return Triple(words[start], words[end], body.joinToString(" "))
            }
            for ((between, and) in v.between) {
                val n = words.size
                if (n < 5 || low(n - 4) != between || low(n - 2) != and) continue
                var body = words.take(n - 4)
                if (body.size > 1 && Vocabulary.lower(body[0]) in v.of) body = body.drop(1)
                return Triple(words[n - 3], words[n - 1], body.joinToString(" "))
            }
        }
        return null
    }

    /**
     * The connecting words at [k]: their slot and how many words they are. A word with a particle after it counts as the
     * word ("までの" is "まで"). A connective that is a single Latin letter ("a" in "de 0 a 1") joins only after a lower
     * bound, so a letter of the formula is never taken for it.
     */
    private fun Vocabulary.connective(words: List<String>, k: Int, current: Slot): Pair<Slot, Int>? {
        for ((slot, phrases) in listOf(Slot.RESPECT to respect, Slot.FROM to from, Slot.TO to to, Slot.FOR to forWords, Slot.AS to approach)) {
            for (p in phrases.sortedByDescending { it.size }) {
                if (k + p.size > words.size) continue
                val matches = p.indices.all { j ->
                    val w = Vocabulary.lower(words[k + j])
                    w == p[j] || (j == p.size - 1 && postpositional && particles.any { w == p[j] + it })
                }
                if (!matches) continue
                if (p.size == 1 && p[0].length == 1 && p[0][0] in 'a'..'z' && current != Slot.FROM) continue
                return slot to p.size
            }
        }
        return null
    }

    /** "x^2 dx" → ("x^2", "x"): a trailing differential names the letter. */
    /** "x^3 - x - 1 on [1, 2]": the formula and the interval at its end (one word before it is left out), or the line and null. */
    /** A data list typed without brackets: "1, 2, 3", "1 2 3", or with semicolons where commas are decimal ("1,5; 2,5"). */
    private fun listLatex(main: String): String {
        val t = main.trim()
        if (t.startsWith("(") || t.startsWith("[")) return latex(t)
        if (t.startsWith("{")) return latex("[" + t.removePrefix("{").removeSuffix("}") + "]")
        val items = (if (';' in t) t.split(';').map { it.trim().replace(',', '.') } else t.split(Regex(",\\s*|\\s+"))).filter { it.isNotBlank() }
        if (items.size < 2) return latex(t)
        return latex("[" + items.joinToString(", ") + "]")
    }

    /** Points typed as (1, 2) and (3, 4): each a vector, one to a line, or ([matrix]) the rows of one matrix. */
    private fun pointsLatex(main: String, matrix: Boolean): String {
        val points = Regex("""[(\[]\s*([^()\[\]]+?)\s*[)\]]""").findAll(main).map { it.groupValues[1].trim() }
            // (1|2) as German textbooks write a point: its coordinates with a bar between, commas then decimal.
            .map { if ('|' in it) it.split('|').joinToString("; ") { c -> c.trim() } else it }
            .filter { ',' in it || ';' in it }.toList()
        if (points.size < 2) return latex(main)
        val rows = points.map { p -> (if (';' in p) p.split(';').map { it.trim().replace(',', '.') } else p.split(',').map { it.trim() }).joinToString(", ") }
        return if (matrix) latex("[" + rows.joinToString(", ") { "[$it]" } + "]") else latex(rows.joinToString("; ") { "[$it]" })
    }

    /** "a=3, b=4, gamma=60°": a triangle's values, one to a line. */
    private fun triangleLatex(main: String): String {
        val parts = main.split(Regex("""\s*[,;]\s*(?=\\?[A-Za-zα-ωΑ-Ω]+\s*=)""")).map { it.trim() }.filter { it.isNotEmpty() }
        return latex(parts.joinToString("; "))
    }

    /** and, or, not, implies, iff, xor written as words in a truth table's formula. */
    private fun logicWords(main: String, languages: List<String> = listOf("en")): String =
        (languages.flatMap { LOGIC_WORDS[it].orEmpty() } + LOGIC_WORDS.getValue("en")).sortedByDescending { it.first.length }
            .fold(main) { t, (w, sym) -> t.replace(Regex("(?i)(?<![\\p{L}\\p{M}])" + Regex.escape(w) + "(?![\\p{L}\\p{M}])"), sym) }

    /** "and", "or", "not", "implies", "if and only if" in each language, as the symbols of a truth table's formula. */
    private val LOGIC_WORDS: Map<String, List<Pair<String, String>>> = run {
        fun of(and: List<String>, or: List<String>, not: List<String>, implies: List<String> = emptyList(), iff: List<String> = emptyList()) =
            and.map { it to " ∧ " } + or.map { it to " ∨ " } + not.map { it to " ¬ " } + implies.map { it to " ⇒ " } + iff.map { it to " ⇔ " }
        mapOf(
            "en" to of(listOf("and"), listOf("or"), listOf("not"), listOf("implies"), listOf("iff", "if and only if")) + listOf("xor" to " ⊕ "),
            "de" to of(listOf("und"), listOf("oder"), listOf("nicht"), listOf("impliziert"), listOf("genau dann wenn")),
            "fr" to of(listOf("et"), listOf("ou"), listOf("non"), listOf("implique"), listOf("si et seulement si")),
            "es" to of(emptyList(), emptyList(), listOf("no"), listOf("implica"), listOf("si y solo si")),
            "it" to of(emptyList(), emptyList(), listOf("non"), listOf("implica"), listOf("se e solo se")),
            "pt" to of(emptyList(), listOf("ou"), listOf("não"), listOf("implica"), listOf("se e somente se")),
            "nl" to of(listOf("en"), listOf("of"), listOf("niet"), listOf("impliceert"), listOf("dan en slechts dan als")),
            "pl" to of(emptyList(), listOf("lub"), listOf("nie"), listOf("implikuje"), listOf("wtedy i tylko wtedy gdy")),
            "tr" to of(listOf("ve"), listOf("veya"), listOf("değil")),
            "vi" to of(listOf("và"), listOf("hoặc"), listOf("không")),
            "in" to of(listOf("dan"), listOf("atau"), listOf("bukan", "tidak")),
            "ru" to of(listOf("и"), listOf("или"), listOf("не")),
            "uk" to of(listOf("і", "та"), listOf("або"), listOf("не")),
            "ar" to of(emptyList(), listOf("أو"), listOf("ليس")),
            "iw" to of(listOf("וגם"), listOf("או"), listOf("לא")),
            "hi" to of(listOf("और"), listOf("या"), listOf("नहीं")),
            "ja" to of(listOf("かつ"), listOf("または"), listOf("でない")),
            "ko" to of(listOf("그리고"), listOf("또는"), listOf("아니다")),
            "th" to of(listOf("และ"), listOf("หรือ"), listOf("ไม่")),
            "zh" to of(listOf("并且", "且", "並且"), listOf("或"), listOf("非")),
        )
    }

    /** The words between a value's unit and the unit it is wanted in, in any language (and an arrow or =). */
    private val CONNECTORS = setOf("in", "to", "into", "as", "->", "=", "?", "=?", "en", "a", "em", "para", "naar", "na", "w", "в", "у", "ke",
        "sang", "ra", "kaç", "kac", "nach", "zu", "là", "bằng", "dalam")

    /**
     * "5 km in mi", "100 km/h to m/s", "20 °C in °F", "5km em mi", "5kmをmiに": a number, a unit, a word or an arrow, and the unit
     * wanted (words of a script written without spaces may stand after it).
     */
    private fun conversion(text: String): Typed? {
        val all = Vocabulary.spaced(text).trim().trimEnd('?', '.', '？', '。', '؟').split(Regex("\\s+")).filter { it.isNotEmpty() }
        // Up to two words before the number ("convert", "rechne", "đổi"), none of them a number.
        for (skip in 0..2) {
            if (skip > 0 && all.take(skip).any { w -> w.any { it.isDigit() } }) break
            conversionOf(all.drop(skip).toMutableList())?.let { return it }
        }
        return null
    }

    /** Words after the unit wanted that only end the sentence: a separable verb's particle ("rechne … um"). */
    private val CONVERSION_ENDINGS = setOf("um", "?", "please")

    private fun conversionOf(words: MutableList<String>): Typed? {
        if (words.size < 3) return null
        val number = Regex("""^-?\d+(?:[.,]\d+)?(?:/\d+)?$""")
        val joined = Regex("""^(-?\d+(?:[.,]\d+)?(?:/\d+)?)(\D.*)$""").find(words[0])
        // A unit written against its number (5km, 20°C); a number alone (1.5, 1/2) is left as it is.
        if (joined != null && !number.matches(words[0])) { words[0] = joined.groupValues[1]; words.add(1, joined.groupValues[2]) }
        if (!number.matches(words[0])) return null
        val from = words.getOrNull(1)?.takeIf { Units.known(it) } ?: return null
        for (k in words.size - 1 downTo 3) {
            val to = words[k]
            if (!Units.known(to)) continue
            val between = words.subList(2, k)
            val after = words.subList(k + 1, words.size)
            if (between.size !in 1..2 || !between.all { w -> w.lowercase() in CONNECTORS || w.none { it.code < 128 } }) continue
            if (!after.all { w -> w.lowercase() in CONVERSION_ENDINGS || w.none { it.code < 128 } }) continue
            return Typed(latex(words[0]), Choice(Mode.UNITS, from, to))
        }
        return null
    }

    private fun trailingInterval(main: String): Pair<String, Pair<String, String>?> {
        // Up to three words before it in any script ("на", "im Intervall", "在"), never a lone Latin letter (a variable),
        // and one after it ("上", "で", "에서").
        val m = Regex("""^(.*?)\s*(?:(?:[\p{L}\p{M}]{2,}|[^\x00-\x7F\s\[\]]+)\s+){0,3}\[([^\[\],]+),([^\[\],]+)\]\s*[^\x00-\x7F\s]*\s*$""")
            .find(main.trim()) ?: return main to null
        if (m.groupValues[1].isBlank()) return main to null
        return m.groupValues[1] to (m.groupValues[2].trim() to m.groupValues[3].trim())
    }

    private fun stripDifferential(main: String): Pair<String, String?> {
        val m = TRAILING_D.find(main) ?: return main to null
        return main.substring(0, m.range.first).trim() to m.groupValues[1]
    }

    private fun letterOf(s: String?): String? {
        val t = s?.trim()?.trimEnd('.', ',', '?') ?: return null
        return if (LETTER.matches(t)) t.replace(Regex("^([A-Za-z])_?\\{?([0-9]+)\\}?$"), "$1_$2") else null
    }

    /** x if the formula has it, else its only letter, else the first. */
    private fun mainLetterOf(latex: String): String = try {
        val tokens = MathCalc.TOKEN.findAll(latex).map { it.value }.toList()
        val letters = Domain.complex { Parser(tokens).statements() }.flatMap { st -> st.sides.filterNotNull().flatMap { it.symbols() } }.toSortedSet()
        when {
            "x" in letters -> "x"
            letters.isNotEmpty() -> letters.first()
            else -> "x"
        }
    } catch (e: CalcException) {
        "x"
    } catch (e: RuntimeException) {
        "x"
    } catch (e: StackOverflowError) {
        "x"
    }

    // --------------------------------------------------------------------------------------------------------------------
    // Math typed in a line, to LaTeX.

    /** Math typed in a line as the LaTeX the calculator reads: `x^2` → `x^{2}`, `sqrt(2)` → `\sqrt{2}`, `pi` → `\pi`. */
    /**
     * [[1, 2], [3, 4]] (and Wolfram's {{1, 2}, {3, 4}}) as \\begin{pmatrix}1&2\\\\3&4\\end{pmatrix}, each entry read as typed math;
     * anything else as it is.
     */
    internal fun matrices(text: String): String {
        val out = StringBuilder()
        var k = 0
        while (k < text.length) {
            val open = text[k]
            if ((open == '[' || open == '{') && text.getOrNull(k + 1)?.let { it == open || it.isWhitespace() && text.substring(k + 1).trimStart().startsWith(open) } == true) {
                val close = if (open == '[') ']' else '}'
                val end = closing(text, k, open, close)
                if (end != null) {
                    val rows = parts(text.substring(k + 1, end), ',').map { it.trim() }
                    if (rows.all { it.startsWith(open) && it.endsWith(close) }) {
                        val cells = rows.map { r -> parts(r.substring(1, r.length - 1), ',').map { latex(it.trim()) } }
                        if (cells.isNotEmpty() && cells.all { it.size == cells[0].size && it.all(String::isNotEmpty) }) {
                            out.append("\\begin{pmatrix}").append(cells.joinToString("\\\\") { it.joinToString("&") }).append("\\end{pmatrix}")
                            k = end + 1
                            continue
                        }
                    }
                }
            }
            out.append(open)
            k++
        }
        return out.toString()
    }

    private val SET_OPERATIONS = listOf("∪", "∩", "∖", "\\cup", "\\cap", "\\setminus")

    private fun closing(s: String, at: Int, open: Char, close: Char): Int? {
        var depth = 0
        for (j in at until s.length) {
            if (s[j] == open) depth++
            if (s[j] == close) { depth--; if (depth == 0) return j }
        }
        return null
    }

    /** [s] split at [separator] outside every bracket. */
    private fun parts(s: String, separator: Char): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        var start = 0
        for ((j, c) in s.withIndex()) {
            if (c == '(' || c == '[' || c == '{') depth++
            if (c == ')' || c == ']' || c == '}') depth--
            if (c == separator && depth == 0) { out += s.substring(start, j); start = j + 1 }
        }
        out += s.substring(start)
        return out
    }

    fun latex(math0: String): String {
        // {1, 2, 3} ∪ {3, 4}: braces of sets.
        val math = if ('{' in math0 && SET_OPERATIONS.any { it in math0 } && "{{" !in math0.replace(" ", ""))
            math0.replace("{", " \\{ ").replace("}", " \\} ") else math0
        // Lines of a system, typed with ";" between them: x + y = 3; x - y = 1.
        val lines = parts(math, ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size > 1) return "\\begin{cases}" + lines.joinToString("\\\\") { latex(it) } + "\\end{cases}"
        if ("[[" in math.replace(" ", "") || "{{" in math.replace(" ", "")) {
            val withMatrices = matrices(math)
            if (withMatrices != math) return withMatrices.split("\\begin{pmatrix}").let { pieces ->
                // The pieces between matrices are typed math as well: [[1,2],[3,4]]^2, 2[[1,0],[0,1]].
                pieces.mapIndexed { n, piece ->
                    if (n == 0) latexPlain(piece) else {
                        val endAt = piece.indexOf("\\end{pmatrix}") + "\\end{pmatrix}".length
                        "\\begin{pmatrix}" + piece.substring(0, endAt) + latexPlain(piece.substring(endAt))
                    }
                }.joinToString("")
            }
        }
        return latexPlain(math)
    }

    private fun latexPlain(math: String): String {
        val s = symbols(math)
        val out = StringBuilder()
        var i = 0
        fun emit(str: String) {
            // A command must not run into a letter after it: \pi x, not \pix.
            if (out.isNotEmpty() && COMMAND_END.containsMatchIn(out) && (str.firstOrNull()?.isLetter() == true)) out.append(' ')
            out.append(str)
        }
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> {
                    out.append(' ')
                    i++
                }
                // LaTeX in the line (a reading from the pen pad, or typed by one who knows it) is kept as it is: \frac, \sin.
                c == '\\' -> {
                    var j = i + 1
                    while (j < s.length && (s[j] in 'a'..'z' || s[j] in 'A'..'Z')) j++
                    if (j == i + 1) j = minOf(i + 2, s.length)
                    emit(s.substring(i, j))
                    i = j
                }
                c in 'a'..'z' || c in 'A'..'Z' -> {
                    var j = i
                    while (j < s.length && (s[j] in 'a'..'z' || s[j] in 'A'..'Z')) j++
                    val name = s.substring(i, j)
                    i = wordAt(name, s, j, ::emit)
                }
                c == '^' || (c == '*' && s.getOrNull(i + 1) == '*') -> {
                    i += if (c == '^') 1 else 2
                    val (exponent, next) = exponentAt(s, i)
                    emit("^{${latex(exponent)}}")
                    i = next
                }
                c == '<' && s.getOrNull(i + 1) == '>' -> { emit("\\ne "); i += 2 }
                c == '!' && s.getOrNull(i + 1) == '=' -> { emit("\\ne "); i += 2 }
                c == '-' && s.getOrNull(i + 1) == '>' -> { emit("\\to "); i += 2 }
                c == '°' -> { emit("^{\\circ}"); i++ }
                // A keyboard's * is a times sign: typeset as a dot, read as times.
                c == '*' -> { emit("\\cdot "); i++ }
                c == '%' -> { emit("\\%"); i++ }
                else -> {
                    emit(c.toString())
                    i++
                }
            }
        }
        return out.toString().replace(Regex(" {2,}"), " ").trim()
    }

    /** A word in typed math: a function, a constant, a Greek letter's name, or letters. Returns where reading goes on. */
    private fun wordAt(word: String, s: String, start: Int, emit: (String) -> Unit): Int {
        var i = start
        val lower = word.lowercase(Locale.ROOT)
        val nextNonSpace = s.substring(i).trimStart().firstOrNull()
        val bracketFollows = nextNonSpace == '('
        when {
            lower == "log" && i < s.length && s[i].isDigit() -> {
                // log10(x), log2(x)
                var j = i
                while (j < s.length && s[j].isDigit()) j++
                emit("\\log_{${s.substring(i, j)}}")
                return j
            }
            lower in WRAPPERS -> {
                val (arg, next) = argumentAt(s, i)
                if (arg == null) {
                    emit(word)
                    return i
                }
                val a = latex(arg)
                emit(
                    when (lower) {
                        "sqrt" -> "\\sqrt{$a}"
                        "cbrt" -> "\\sqrt[3]{$a}"
                        "abs" -> "\\left|$a\\right|"
                        "floor" -> "\\lfloor $a\\rfloor "
                        else -> "\\lceil $a\\rceil "
                    },
                )
                return next
            }
            // gamma(5) is Γ(5); Gamma alone the capital letter, gamma alone the small one
            lower == "gamma" && bracketFollows -> emit("\\Gamma")
            word == "Gamma" -> emit("\\Gamma ")
            lower in FUNCTION_WORDS -> emit("\\${FUNCTION_WORDS.getValue(lower)}")
            lower == "pi" -> emit("\\pi ")
            lower == "inf" || lower == "infinity" || lower == "oo" -> emit("\\infty ")
            word in GREEK_WORDS -> emit("\\$word ")
            lower in GREEK_WORDS && word[0].isUpperCase() && "\\${word}" in CAPITAL_GREEK -> emit("\\$word ")
            word.length == 1 && i < s.length && s[i].isDigit() -> {
                // x1 → x_1
                var j = i
                while (j < s.length && s[j].isDigit()) j++
                emit("${word}_{${s.substring(i, j)}}")
                return j
            }
            else -> emit(word)
        }
        return i
    }

    /** What a power raises to: a bracket's inside, a braced group, or a signed number or word (with its own power). */
    private fun exponentAt(s: String, start: Int): Pair<String, Int> {
        var i = start
        while (i < s.length && s[i] == ' ') i++
        if (i >= s.length) return "" to i
        if (s[i] == '(' || s[i] == '{') {
            val close = matching(s, i) ?: return s.substring(i + 1) to s.length
            var text = s.substring(i + 1, close)
            var next = close + 1
            if (next < s.length && s[next] == '^') {
                val (more, after) = exponentAt(s, next + 1)
                text = "($text)^($more)"
                next = after
            }
            return text to next
        }
        val begin = i
        if (s[i] == '-' || s[i] == '+') i++
        when {
            i < s.length && (s[i].isDigit() || s[i] == '.') -> while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            i < s.length && s[i].isLetter() -> while (i < s.length && s[i].isLetter()) i++
            i < s.length -> i++
        }
        var text = s.substring(begin, i)
        if (i < s.length && s[i] == '^') {
            val (more, after) = exponentAt(s, i + 1)
            text = "$text^($more)"
            i = after
        }
        return text to i
    }

    /** A function's argument: a bracket's inside, or else the number or word after it. */
    private fun argumentAt(s: String, start: Int): Pair<String?, Int> {
        var i = start
        while (i < s.length && s[i] == ' ') i++
        if (i >= s.length) return null to start
        if (s[i] == '(') {
            val close = matching(s, i) ?: return s.substring(i + 1) to s.length
            return s.substring(i + 1, close) to close + 1
        }
        val begin = i
        while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '.')) i++
        if (i == begin) return null to start
        return s.substring(begin, i) to i
    }

    private fun matching(s: String, open: Int): Int? {
        var depth = 0
        for (k in open until s.length) {
            if (s[k] == '(' || s[k] == '{' || s[k] == '[') depth++
            if (s[k] == ')' || s[k] == '}' || s[k] == ']') {
                depth--
                if (depth == 0) return k
            }
        }
        return null
    }

    /** Symbols typed with a phone's keyboard, as the words above: × is *, √ is sqrt, π is pi, ² is ^2. */
    private fun symbols(math: String): String {
        val b = StringBuilder()
        for (ch in math) {
            when (ch) {
                '×', '·', '⋅', '∙' -> b.append('*')
                '÷', '∕' -> b.append('/')
                '−', '–', '—' -> b.append('-')
                '√' -> b.append("sqrt")
                '∛' -> b.append("cbrt")
                'π' -> b.append(" pi ")
                '∞' -> b.append(" inf ")
                '≤' -> b.append("<=")
                '≥' -> b.append(">=")
                '≠' -> b.append("!=")
                '→' -> b.append("->")
                '≡' -> b.append(" \\equiv ")
                '∧' -> b.append(" \\land ")
                '∨' -> b.append(" \\lor ")
                '¬' -> b.append(" \\neg ")
                '⇒' -> b.append(" \\Rightarrow ")
                '⇔', '↔' -> b.append(" \\Leftrightarrow ")
                '⊕' -> b.append(" \\oplus ")
                '∪' -> b.append(" \\cup ")
                '∩' -> b.append(" \\cap ")
                '∖' -> b.append(" \\setminus ")
                '∅' -> b.append(" \\emptyset ")
                '⁰' -> b.append("^0")
                '¹' -> b.append("^1")
                '²' -> b.append("^2")
                '³' -> b.append("^3")
                '⁴' -> b.append("^4")
                '⁵' -> b.append("^5")
                '⁶' -> b.append("^6")
                '⁷' -> b.append("^7")
                '⁸' -> b.append("^8")
                '⁹' -> b.append("^9")
                else -> GREEK_LETTERS[ch]?.let { b.append(' ').append(it).append(' ') } ?: b.append(ch)
            }
        }
        return b.toString()
    }

    private val DD = Regex("^d\\s*/\\s*d([A-Za-z])\\b\\s*")
    private val ARROW = Regex("->|→|\\\\to")
    private val LEADING_APPROACH = Regex("^\\s*([A-Za-z])\\s*(?:->|→)\\s*(\\S+)\\s+")
    /** A bound written as a number: 0, -1, 2.5, π, pi, ∞. */
    private val NUMBER_LIKE = Regex("^[-+−]?([0-9]+([.,][0-9]+)?|pi|π|∞|inf|infinity)$", RegexOption.IGNORE_CASE)
    private val TRAILING_D = Regex("\\s*\\bd([A-Za-z])\\s*$")
    private val LETTER = Regex("^[A-Za-z](_?\\{?[0-9]+\\}?)?$")
    private val COMMAND_END = Regex("\\\\[a-zA-Z]+$")

    /** Words wrapped around their argument. */
    private val WRAPPERS = setOf("sqrt", "cbrt", "abs", "floor", "ceil")

    /** Function words as the parser's commands: asin is arcsin, productlog is Lambert's W. */
    private val FUNCTION_WORDS = mapOf(
        "sin" to "sin", "cos" to "cos", "tan" to "tan", "cot" to "cot", "sec" to "sec", "csc" to "csc",
        "sinh" to "sinh", "cosh" to "cosh", "tanh" to "tanh",
        "arcsin" to "arcsin", "arccos" to "arccos", "arctan" to "arctan", "arccot" to "arccot",
        "asin" to "arcsin", "acos" to "arccos", "atan" to "arctan", "acot" to "arccot",
        "ln" to "ln", "log" to "log", "lg" to "lg", "exp" to "exp",
        "erf" to "erf", "erfc" to "erfc", "zeta" to "zeta", "digamma" to "digamma",
        "lambertw" to "lambertw", "productlog" to "lambertw", "besselj" to "besselj",
        "sgn" to "sgn", "sign" to "sgn",
        // Russian and Ukrainian school notation.
        "tg" to "tan", "ctg" to "cot", "arctg" to "arctan", "arcctg" to "arccot",
    )

    private val GREEK_WORDS = setOf(
        "alpha", "beta", "gamma", "delta", "epsilon", "varepsilon", "zeta", "eta", "theta", "vartheta", "iota", "kappa",
        "lambda", "mu", "nu", "xi", "rho", "sigma", "tau", "upsilon", "phi", "varphi", "chi", "psi", "omega",
    )
    private val CAPITAL_GREEK = setOf("\\Delta", "\\Omega", "\\Phi", "\\Psi", "\\Theta", "\\Lambda", "\\Sigma", "\\Gamma")

    private val GREEK_LETTERS = mapOf(
        'α' to "alpha", 'β' to "beta", 'γ' to "gamma", 'δ' to "delta", 'ε' to "epsilon", 'ζ' to "zeta", 'η' to "eta",
        'θ' to "theta", 'ι' to "iota", 'κ' to "kappa", 'λ' to "lambda", 'μ' to "mu", 'ν' to "nu", 'ξ' to "xi", 'ρ' to "rho",
        'σ' to "sigma", 'τ' to "tau", 'υ' to "upsilon", 'φ' to "phi", 'χ' to "chi", 'ψ' to "psi", 'ω' to "omega",
        'Δ' to "Delta", 'Ω' to "Omega", 'Φ' to "Phi", 'Ψ' to "Psi", 'Θ' to "Theta", 'Λ' to "Lambda", 'Σ' to "Sigma",
        'Γ' to "Gamma",
    )
}

/** What the words in front of a formula ask for. */
enum class Command {
    SOLVE, FACTOR, EXPAND, SIMPLIFY, DERIVATIVE, INTEGRAL, LIMIT, SUM, PLOT, CALCULATE, PARTIAL_FRACTIONS, SERIES,
    DETERMINANT, INVERSE, EIGEN, ROW_REDUCE, TRANSPOSE,
    DECOMPOSE, MATRIX_EXP, GRADIENT, DIVERGENCE, CURL, JACOBIAN, LAPLACIAN, LEAST_SQUARES, EXTREMA,
    LAPLACE, INVERSE_LAPLACE, FOURIER, NEWTON, BISECTION, EULER, RUNGE_KUTTA, TRAPEZOID, SIMPSON, POLAR, RESIDUES,
    NUMBER_FACTS, STATISTICS, REGRESSION, SEQUENCE, TRUTH_TABLE, TRIANGLE, CONIC, LINE, PLANE, DIOPHANTINE,
}

/**
 * The command words of one language and the words that join a command's parts ("from … to", "for", "with respect to"),
 * all in lower case. A phrase is matched whole at the start of the line, the longest first; in a language that puts the verb
 * last ([commandLast]) at its end too. In a language whose joining words follow their value ([postpositional]: "0から1まで",
 * "0부터 1까지", "0 से 1 तक"), each takes the word before it. [particles] are left out where they end a part ("x^2-1を",
 * "x^2 का"), [ignored] words wherever they stand ("kadar", Dutch "op" of "los … op"), and [endings] (polite or verbal endings,
 * "してください", "करें") are taken off the end of a line before its last words are matched.
 */
class Vocabulary internal constructor(
    val language: String,
    private val commands: Map<Command, List<String>>,
    internal val of: Set<String>,
    internal val from: List<List<String>>,
    internal val to: List<List<String>>,
    internal val forWords: List<List<String>>,
    internal val respect: List<List<String>>,
    internal val approach: List<List<String>>,
    internal val postpositional: Boolean = false,
    internal val commandLast: Boolean = false,
    internal val particles: Set<String> = emptySet(),
    internal val ignored: Set<String> = emptySet(),
    private val endings: List<String> = emptyList(),
    /** Words that close the part before them ("当 x->0 时"). */
    internal val closers: Set<String> = emptySet(),
    /** Words a line may begin with before its command, left out ("จง" of a Thai exercise). */
    private val prefixes: List<String> = emptyList(),
    /** Articles between a command and its noun or formula ("la", "l'", "the"); an elided one ends in an apostrophe. */
    private val articles: Set<String> = emptySet(),
    /** Nouns that name no command ("la ecuación x^2=4", "x^2=4 denklemini"), left out after or before one. */
    private val nouns: List<List<String>> = emptyList(),
    /** Words for "tends to" in a limit's approach ("x tiende a 2" is x -> 2). */
    internal val arrows: List<String> = emptyList(),
    /** "between … and …" around an integral's bounds after its formula. */
    internal val between: List<Pair<String, String>> = emptyList(),
    /** "subject to" before the constraint of an extremum: the constraint is a line of its own after it. */
    internal val constraints: List<String> = emptyList(),
) {
    /** [line] with each of this language's "subject to" as the ";" that begins the constraint's line. */
    internal fun constrained(line: String): String {
        var out = line
        for (c in constraints.sortedByDescending { it.length }) {
            // A script written without spaces joins the phrase to the words around it ("…的最大值，约束条件为 x+y=10").
            val pattern = if (unspaced(c.first())) "[\\s,，、]*" + Regex.escape(c) + "\\s*" else "\\s+" + Regex.escape(c) + "\\s+"
            out = out.replace(Regex("(?i)$pattern"), "; ")
        }
        // "x*yの最大値、x+y=10のもとで": a phrase after its constraint ends the line; the constraint begins after the last 、 or ，.
        val end = out.trimEnd()
        if (end.endsWith(";") && !line.trimEnd().endsWith(";")) {
            val body = end.dropLast(1).trimEnd()
            val cut = body.lastIndexOfAny(charArrayOf('、', '，'))
            out = if (cut > 0) body.substring(0, cut).trimEnd() + "; " + body.substring(cut + 1).trim() else body
        }
        return out
    }

    private val phrases: List<Pair<String, Command>> =
        commands.flatMap { (c, list) -> list.map { lower(it) to c } }.sortedByDescending { it.first.length }

    /** The command words of [command], as this language writes them (the first is its usual one). */
    fun words(command: Command): List<String> = commands[command].orEmpty()

    /** The command a line starts with, and the rest of the line. */
    internal fun command(line: String): Pair<Command, String>? {
        wrapped(withoutPrefix(line))?.let { return it }
        val (command, rest) = startCommand(withoutPrefix(line)) ?: return null
        val (c, after) = refined(command, rest)
        // "画出 x^2 的图像": a language that puts its verb last may name the command at both ends.
        if (commandLast) endCommand(after)?.let { (inner, before) -> if (inner == c) return c to before }
        return c to after
    }

    /**
     * A command phrase written around its formula, as textbooks put it: "rozłóż 1/(x^2-1) na ułamki proste", "scomponi … in
     * fratti semplici", "פרק את … לשברים חלקיים". The longest phrase whose first words begin the line and whose last words end
     * it; a last part that is only "of" does not count ("derivative … of").
     */
    private fun wrapped(text: String): Pair<Command, String>? {
        val lower = lower(text)
        val end = lower.trimEnd().trimEnd('.', '。', '!', '！', '?', '？').trimEnd().length
        val line = lower.substring(0, end)
        for ((phrase, command) in phrases) {
            val words = phrase.split(' ').filter { it.isNotEmpty() }
            if (words.size < 2) continue
            for (cut in 1 until words.size) {
                val head = words.take(cut).joinToString(" ")
                val tail = words.drop(cut).joinToString(" ")
                if (tail in of) continue
                if (!line.startsWith("$head ") || !line.endsWith(" $tail")) continue
                if (head.length + tail.length + 2 > line.length) continue
                val middle = text.substring(head.length, end - tail.length).trim()
                if (middle.isEmpty()) continue
                val afterArticle = dropArticle(middle)
                return command to dropTrailingNoun(dropNoun(afterArticle) ?: afterArticle)
            }
        }
        return null
    }

    private fun startCommand(text: String): Pair<Command, String>? {
        val lower = lower(text)
        for ((phrase, command) in phrases) {
            if (!lower.startsWith(phrase)) continue
            val after = lower.getOrNull(phrase.length)
            // A script without spaces runs its words together ("หาอนุพันธ์ของ"): the longest phrase is matched first.
            if (after != null && !unspaced(phrase.last()) && joins(phrase.last(), after)) continue
            return command to text.substring(phrase.length).trim().trimStart(':', '：').trim()
        }
        return null
    }

    /**
     * What follows a command, said more exactly: "calcula la derivada de x^3" is the derivative (a calculating verb, an article
     * and a command's noun), "dibuja la gráfica de x^2" a graph, and a noun that names no command goes ("resuelve la ecuación
     * x^2=4", "pochodna funkcji x^3"). An article alone is kept: it may be a letter ("calcule a x + 1").
     */
    private fun refined(command: Command, rest: String): Pair<Command, String> {
        val afterArticle = dropArticle(rest)
        val inner = startCommand(afterArticle)
        if (inner != null && (command == Command.CALCULATE || inner.first == command)) return refined(inner.first, inner.second)
        val afterNoun = dropNoun(afterArticle)
        if (afterNoun != null) return command to dropOf(afterNoun)
        return command to rest
    }

    private fun dropArticle(text: String): String {
        val lower = lower(text)
        for (a in articles.sortedByDescending { it.length }) {
            if (!lower.startsWith(a)) continue
            if (a.endsWith("'") && text.length > a.length) return text.substring(a.length).trimStart()
            if (lower.getOrNull(a.length) == ' ') return text.substring(a.length).trimStart()
        }
        return text
    }

    private fun dropNoun(text: String): String? {
        val words = text.split(' ').filter { it.isNotEmpty() }
        for (noun in nouns.sortedByDescending { it.size }) {
            if (words.size > noun.size && noun.indices.all { lower(words[it]) == noun[it] }) return words.drop(noun.size).joinToString(" ")
        }
        return null
    }

    private fun dropOf(text: String): String {
        val words = text.split(' ').filter { it.isNotEmpty() }
        return if (words.size > 1 && lower(words[0]) in of) words.drop(1).joinToString(" ") else text
    }

    /** A noun that names no command at the end of what is left before a final command ("x^2=4 denklemini çöz"). */
    private fun dropTrailingNoun(text: String): String {
        val words = text.split(' ').filter { it.isNotEmpty() }
        for (noun in nouns.sortedByDescending { it.size }) {
            val n = noun.size
            if (words.size > n && noun.indices.all { lower(words[words.size - n + it]) == noun[it] }) return words.dropLast(n).joinToString(" ")
        }
        return text
    }

    private fun withoutPrefix(text: String): String {
        for (p in prefixes) {
            if (lower(text).startsWith(lower(p)) && text.length > p.length) return text.substring(p.length).trimStart()
        }
        return text
    }

    /** The command a line ends with ("x^2-1を因数分解", "x^2-1 का गुणनखंड करें"), and the rest of the line. */
    internal fun commandAtEnd(text: String): Pair<Command, String>? {
        val (command, rest) = endCommand(text) ?: return null
        val before = dropTrailingNoun(rest)
        // "x^3'ün türevini hesapla", "x^2 fonksiyonunun grafiğini çiz": a command's noun before a calculating verb says it.
        val inner = endCommand(before)
        // "矩阵 A 的行列式": the noun may begin the line, before the formula.
        if (inner != null && (command == Command.CALCULATE || inner.first == command)) {
            val formula = dropTrailingNoun(inner.second)
            return inner.first to (dropNoun(formula) ?: formula)
        }
        return command to (dropNoun(before) ?: before)
    }

    private fun endCommand(text: String): Pair<Command, String>? {
        val line = withoutPrefix(text).trimEnd().trimEnd('.', '。', '!', '！', '?', '？').trimEnd()
        // The line as it is first ("인수분해" ends in "해" itself), then without a verbal ending ("因数分解して").
        val candidates = listOf(line) + endings.sortedByDescending { it.length }.mapNotNull { ending ->
            val e = lower(ending)
            if (lower(line).endsWith(e) && line.length > e.length) line.substring(0, line.length - e.length).trimEnd() else null
        }
        for (candidate in candidates) {
            val lower = lower(candidate)
            for ((phrase, command) in phrases) {
                if (!lower.endsWith(phrase) || lower.length == phrase.length) continue
                val at = lower.length - phrase.length
                val before = lower[at - 1]
                // A script without spaces has no word boundary to see: a particle may stand right before the command.
                if (!unspaced(phrase.first()) && joins(phrase.first(), before)) continue
                val rest = candidate.substring(0, at).trim()
                if (rest.isEmpty()) continue
                return command to rest
            }
        }
        return null
    }

    /** [approach] with this language's "tends to" written as an arrow: "x tiende a 2" is "x -> 2". */
    internal fun arrowed(approach: String): String {
        var out = " $approach "
        for (a in arrows.sortedByDescending { it.length }) out = out.replace(Regex("(?i)(?<=\\s)" + Regex.escape(a) + "(?=\\s)"), "->")
        return out.trim()
    }

    companion object {
        private fun words(vararg phrases: String) = phrases.map { it.split(' ') }

        /** Lower case, one character for one, so positions stay as they were (Turkish İ is i). */
        internal fun lower(s: String): String = String(CharArray(s.length) { Character.toLowerCase(s[it]) })

        /** Two letters of one script, which a word boundary never separates ("solvex" is no command; "解x" is). */
        private fun joins(a: Char, b: Char): Boolean = Character.isLetter(b) && scriptOf(a) == scriptOf(b)

        private fun scriptOf(c: Char): Any = when (val s = Character.UnicodeScript.of(c.code)) {
            Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> "CJK"
            // The long-vowel mark of katakana (テイラー) belongs to no script of its own.
            else -> if (c == 'ー' || c == 'ｰ') "CJK" else s
        }

        /** Scripts written without spaces between words, as far as the math is concerned: Chinese, Japanese, Korean particles, Thai. */
        private fun unspaced(c: Char): Boolean = when (Character.UnicodeScript.of(c.code)) {
            Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.HANGUL, Character.UnicodeScript.THAI -> true
            else -> c == 'ー' || c == 'ｰ'
        }

        private fun rightToLeft(c: Char): Boolean = when (Character.UnicodeScript.of(c.code)) {
            Character.UnicodeScript.HEBREW, Character.UnicodeScript.ARABIC -> true
            else -> false
        }

        /**
         * [text] with spaces where words meet math without one: between a script written without spaces and anything else
         * ("求导x^2", "x^2を0から1まで", "x^2-1을"), between a Hebrew or Arabic word and the math it is joined to ("מ-0", "לx",
         * "لـx"), and before a Turkish suffix ("0'dan", "x^2-1'i"). Apostrophes are made one (’ ʼ as ').
         */
        internal fun spaced(text: String): String {
            // Arabic short vowels and shadda are not letters of the word ("حلّل" is "حلل").
            val s = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
                .replace('’', '\'').replace('ʼ', '\'').replace('‘', '\'')
                .filterNot { it in '\u064B'..'\u0652' || it == '\u0670' }
            val out = StringBuilder(s.length + 8)
            for (i in s.indices) {
                val c = s[i]
                val prev = if (i > 0) s[i - 1] else null
                // A hyphen or a middle dot inside a word of such a script ("ルンゲ・クッタ法", "龙格-库塔法") is part of it.
                val inWord = (joiner(c) && prev != null && unspaced(prev) && s.getOrNull(i + 1)?.let { unspaced(it) } == true) ||
                    (prev != null && joiner(prev) && unspaced(c) && i >= 2 && unspaced(s[i - 2]))
                if (prev != null && !prev.isWhitespace() && !c.isWhitespace() && !inWord) {
                    val boundary = (unspaced(prev) != unspaced(c) && (unspaced(prev) || unspaced(c)) && !isCombining(c)) ||
                        (rightToLeft(prev) && (c.isDigit() || c in 'a'..'z' || c in 'A'..'Z')) ||
                        ((prev.isDigit() || prev in 'a'..'z' || prev in 'A'..'Z') && rightToLeft(c))
                    if (boundary) out.append(' ')
                }
                // A joiner after a Hebrew or Arabic prefix ("מ-0", "לـx") is a space; a minus anywhere else stays.
                if ((c == '-' || c == 'ـ') && prev != null && rightToLeft(prev) && s.getOrNull(i + 1)?.let { !it.isWhitespace() && !rightToLeft(it) } == true &&
                    (i < 2 || !rightToLeft(s[i - 2]))) {
                    out.append(' ')
                    continue
                }
                // A tatweel elsewhere only stretches a word.
                if (c == 'ـ') continue
                // A Turkish suffix after math: "0'dan" is "0 'dan"; after a word it is the word's ("Laplace'a").
                val afterLetter = prev != null && prev.isLetter() && i >= 2 && s[i - 2].isLetter()
                if (c == '\'' && prev != null && (prev.isDigit() || prev in 'a'..'z' || prev in 'A'..'Z' || prev == ')' || prev == ']' || prev == '}') &&
                    !afterLetter && isTurkishSuffix(s, i + 1)) {
                    out.append(' ')
                }
                out.append(c)
            }
            return out.toString().replace(Regex(" {2,}"), " ")
        }

        /** A hyphen or a middle dot, which may join the parts of a word ("ルンゲ・クッタ", "龙格-库塔", "룽게-쿠타"). */
        private fun joiner(c: Char) = c == '-' || c == '‐' || c == '・' || c == '･' || c == '＝' || c == '·'

        private fun isCombining(c: Char) = Character.getType(c).let {
            it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt() || it == Character.ENCLOSING_MARK.toInt()
        }

        /** The suffixes a Turkish learner joins to math with an apostrophe: 0'dan, 1'e, x^2-1'i, x'e göre. */
        private fun isTurkishSuffix(s: String, at: Int): Boolean {
            val word = s.substring(at).takeWhile { it.isLetter() }.lowercase()
            return word in TURKISH_SUFFIXES
        }

        private val TURKISH_SUFFIXES = setOf(
            "dan", "den", "tan", "ten", "a", "e", "ya", "ye", "i", "ı", "u", "ü", "yi", "yı", "yu", "yü",
            "in", "ın", "un", "ün", "nin", "nın", "nun", "nün", "da", "de", "ta", "te",
        )

        val ENGLISH = Vocabulary(
            "en",
            mapOf(
                Command.SOLVE to listOf("solve"),
                Command.FACTOR to listOf("factor", "factorise", "factorize"),
                Command.EXPAND to listOf("expand", "multiply out"),
                Command.SIMPLIFY to listOf("simplify"),
                Command.DERIVATIVE to listOf("derivative of", "derivative", "differentiate", "derive"),
                Command.INTEGRAL to listOf("integral of", "integral", "integrate", "antiderivative of", "antiderivative"),
                Command.LIMIT to listOf("limit of", "limit", "lim"),
                Command.SUM to listOf("sum of", "sum"),
                Command.PLOT to listOf("plot", "graph of", "graph", "draw"),
                Command.CALCULATE to listOf("calculate", "compute", "evaluate", "find", "determine", "what is"),
                Command.PARTIAL_FRACTIONS to listOf("partial fractions of", "partial fractions", "partial fraction decomposition of", "partial fraction decomposition", "apart"),
                Command.SERIES to listOf("taylor series of", "taylor series", "taylor polynomial of", "taylor polynomial", "maclaurin series of", "maclaurin series", "series expansion of", "series expansion", "power series of", "series of", "series", "taylor", "maclaurin", "power series", "laurent series of", "laurent series", "expand in a taylor series", "expand in a power series"),
                Command.DETERMINANT to listOf("determinant of", "determinant", "det"),
                Command.INVERSE to listOf("inverse matrix of", "inverse of", "inverse", "invert", "inverse matrix"),
                Command.EIGEN to listOf("eigenvalues and eigenvectors of", "eigenvalues of", "eigenvalues", "eigenvectors of", "eigenvectors", "eigenvalue", "characteristic polynomial of", "characteristic polynomial"),
                Command.ROW_REDUCE to listOf("reduced row echelon form of", "row echelon form of", "row echelon form", "row reduce", "rref of", "rref", "gaussian elimination", "rank of", "rank", "reduced row echelon form", "null space of", "kernel of", "column space of"),
                Command.TRANSPOSE to listOf("transpose of", "transpose"),
                Command.DECOMPOSE to listOf("lu decomposition of", "lu decomposition", "qr decomposition of", "qr decomposition", "cholesky decomposition of", "cholesky decomposition", "decomposition of", "lu factorization of", "lu factorization"),
                Command.MATRIX_EXP to listOf("matrix exponential of", "matrix exponential"),
                Command.GRADIENT to listOf("gradient of", "gradient", "grad"),
                Command.DIVERGENCE to listOf("divergence of", "divergence", "div"),
                Command.CURL to listOf("curl of", "curl", "rot"),
                Command.JACOBIAN to listOf("jacobian matrix of", "jacobian of", "jacobian", "jacobian matrix"),
                Command.LAPLACIAN to listOf("laplacian of", "laplacian"),
                Command.LEAST_SQUARES to listOf("least squares solution of", "least squares", "least-squares"),
                Command.EXTREMA to listOf("maximize", "minimize", "optimize", "extrema of", "critical points of"),
                Command.LAPLACE to listOf("laplace transform of", "laplace transform", "laplace of"),
                Command.INVERSE_LAPLACE to listOf("inverse laplace transform of", "inverse laplace transform", "inverse laplace of", "inverse laplace"),
                Command.FOURIER to listOf("fourier series of", "fourier series"),
                Command.NEWTON to listOf("newton's method for", "newton's method", "newton method", "newton", "newton-raphson method for", "newton-raphson method", "newton-raphson"),
                Command.BISECTION to listOf("bisection method for", "bisection method", "bisection"),
                Command.EULER to listOf("euler's method for", "euler's method", "euler method"),
                Command.RUNGE_KUTTA to listOf("runge-kutta method for", "runge-kutta method", "runge-kutta", "runge kutta", "rk4"),
                Command.TRAPEZOID to listOf("trapezoidal rule for", "trapezoidal rule", "trapezoid rule"),
                Command.SIMPSON to listOf("simpson's rule for", "simpson's rule", "simpson rule", "simpson"),
                Command.POLAR to listOf("polar form of", "polar form", "exponential form of"),
                Command.RESIDUES to listOf("residues of", "residues", "residue of", "poles of"),
                Command.NUMBER_FACTS to listOf("is prime", "divisors of", "divisors", "properties of the number", "number properties of", "is a prime number", "is a prime", "factors of", "prime factorization of", "prime factors of"),
                Command.STATISTICS to listOf("mean of", "average of", "median of", "statistics of", "statistics", "standard deviation of", "variance of", "summary statistics of", "mode of"),
                Command.REGRESSION to listOf("linear regression of", "linear regression", "regression line of", "regression line", "regression", "line of best fit for", "line of best fit", "best fit line", "least squares line"),
                Command.SEQUENCE to listOf("next terms of", "next term of", "next terms", "next term", "continue the sequence", "sequence", "pattern of", "rule of the sequence"),
                Command.TRUTH_TABLE to listOf("truth table of", "truth table for", "truth table", "tautology"),
                Command.TRIANGLE to listOf("solve the triangle", "triangle with", "triangle"),
                Command.CONIC to listOf("conic section", "conic", "ellipse", "hyperbola", "parabola", "foci of", "eccentricity of", "focus of", "vertex of", "directrix of"),
                Command.LINE to listOf("line through the points", "line through", "equation of the line through", "straight line through"),
                Command.PLANE to listOf("plane through the points", "plane through", "equation of the plane through"),
                Command.DIOPHANTINE to listOf("integer solutions of", "integer solutions", "solve in integers", "solve over the integers", "diophantine", "whole number solutions of"),
            ),
            of = setOf("of"),
            from = words("from"),
            to = words("to"),
            forWords = words("for"),
            respect = words("with respect to", "wrt"),
            approach = words("as", "when", "at", "around", "about"),
            articles = setOf("the"),
            nouns = words("equation", "function", "expression", "matrix", "parabola", "ellipse", "hyperbola"),
            arrows = listOf("approaches", "tends to", "goes to"),
            between = listOf("between" to "and"),
            constraints = listOf("subject to", "with the constraint", "under the constraint"),
        )

        val GERMAN = Vocabulary(
            "de",
            mapOf(
                Command.SOLVE to listOf("löse", "lösen", "löse die gleichung", "auflösen"),
                Command.FACTOR to listOf("faktorisiere", "faktorisieren", "zerlege"),
                Command.EXPAND to listOf("multipliziere aus", "ausmultiplizieren", "expandiere"),
                Command.SIMPLIFY to listOf("vereinfache", "vereinfachen"),
                Command.DERIVATIVE to listOf("ableitung von", "ableitung", "leite ab", "ableiten"),
                Command.INTEGRAL to listOf("integral von", "integral", "integriere", "stammfunktion von", "stammfunktion"),
                Command.LIMIT to listOf("grenzwert von", "grenzwert", "limes"),
                Command.SUM to listOf("summe von", "summe"),
                Command.PLOT to listOf("zeichne", "plotte", "graphen von", "graph von", "graphen", "graph"),
                Command.CALCULATE to listOf("berechne", "rechne", "bestimme", "ermittle", "was ist"),
                Command.PARTIAL_FRACTIONS to listOf("partialbruchzerlegung von", "partialbruchzerlegung", "partialbrüche von", "partialbrüche"),
                Command.SERIES to listOf("taylorreihe von", "taylorreihe", "taylorpolynom von", "taylorpolynom", "taylorentwicklung von", "taylorentwicklung", "taylor-entwicklung von", "reihenentwicklung von", "reihenentwicklung", "potenzreihe von", "potenzreihe", "maclaurin-reihe von", "maclaurinreihe von", "maclaurinreihe", "taylor-reihe von", "taylor-reihe", "taylor-polynom von", "taylor-polynom", "taylor-entwicklung", "maclaurin-reihe", "potenzreihenentwicklung von", "potenzreihenentwicklung", "entwickle in eine taylorreihe", "laurentreihe von", "laurentreihe"),
                Command.DETERMINANT to listOf("determinante von", "determinante"),
                Command.INVERSE to listOf("inverse matrix von", "inverse von", "kehrmatrix von", "inverse", "invertiere", "inverse matrix", "kehrmatrix"),
                Command.EIGEN to listOf("eigenwerte und eigenvektoren von", "eigenwerte von", "eigenwerte", "eigenvektoren von", "eigenvektoren"),
                Command.ROW_REDUCE to listOf("reduzierte zeilenstufenform von", "zeilenstufenform von", "zeilenstufenform", "gauß-verfahren", "gauss-verfahren", "rang von", "rang", "reduzierte zeilenstufenform"),
                Command.TRANSPOSE to listOf("transponierte von", "transponierte", "transponiere"),
                Command.DECOMPOSE to listOf("lr-zerlegung von", "lu-zerlegung von", "lr-zerlegung", "lu-zerlegung", "qr-zerlegung von", "qr-zerlegung", "cholesky-zerlegung von", "cholesky-zerlegung"),
                Command.MATRIX_EXP to listOf("matrixexponential von", "matrixexponentialfunktion von", "matrixexponential"),
                Command.GRADIENT to listOf("gradient von"),
                Command.DIVERGENCE to listOf("divergenz von", "divergenz"),
                Command.CURL to listOf("rotation von"),
                Command.JACOBIAN to listOf("jacobi-matrix von", "jacobimatrix von", "jacobi-matrix", "funktionalmatrix von"),
                Command.LAPLACIAN to listOf("laplace von", "laplace-operator von"),
                Command.LEAST_SQUARES to listOf("methode der kleinsten quadrate", "kleinste quadrate", "ausgleichsrechnung"),
                Command.EXTREMA to listOf("maximiere", "minimiere", "extrema von", "kritische punkte von"),
                Command.LAPLACE to listOf("laplace-transformation von", "laplace-transformierte von", "laplacetransformation von", "laplace-transformation", "laplace-transformierte", "laplacetransformierte von", "laplacetransformierte"),
                Command.INVERSE_LAPLACE to listOf("inverse laplace-transformation von", "rücktransformation von", "inverse laplace-transformation", "inverse laplace-transformierte von", "inverse laplacetransformierte von", "laplace-rücktransformation von", "laplace-rücktransformation"),
                Command.FOURIER to listOf("fourierreihe von", "fourier-reihe von", "fourierreihe", "fourier-reihe"),
                Command.NEWTON to listOf("newton-verfahren für", "newton-verfahren", "newtonverfahren", "newton-raphson-verfahren", "newton-raphson"),
                Command.BISECTION to listOf("bisektionsverfahren für", "bisektionsverfahren", "intervallhalbierung", "bisektion", "intervallhalbierungsverfahren"),
                Command.EULER to listOf("euler-verfahren für", "euler-verfahren", "eulerverfahren"),
                Command.RUNGE_KUTTA to listOf("runge-kutta-verfahren für", "runge-kutta-verfahren", "runge-kutta"),
                Command.TRAPEZOID to listOf("trapezregel für", "trapezregel", "trapezformel"),
                Command.SIMPSON to listOf("simpsonregel für", "simpson-regel für", "simpsonregel", "simpson-regel", "keplersche fassregel"),
                Command.POLAR to listOf("polarform von", "polardarstellung von", "exponentialform von", "polarform", "polardarstellung", "exponentialform", "trigonometrische form von", "trigonometrische form"),
                Command.RESIDUES to listOf("residuen von", "residuen", "polstellen von", "residuum von", "pole von"),
                Command.NUMBER_FACTS to listOf("ist prim", "teiler von", "teiler", "eigenschaften der zahl", "zahleigenschaften von", "ist eine primzahl", "primfaktorzerlegung von", "primfaktoren von", "eine primzahl"),
                Command.STATISTICS to listOf("mittelwert von", "durchschnitt von", "median von", "statistik von", "statistik", "standardabweichung von", "varianz von", "kennzahlen von", "modalwert von", "modus von"),
                Command.REGRESSION to listOf("lineare regression von", "lineare regression", "regressionsgerade von", "regressionsgerade", "regression", "ausgleichsgerade durch", "ausgleichsgerade"),
                Command.SEQUENCE to listOf("nächste glieder von", "nächste glieder", "nächstes glied", "zahlenfolge", "folge", "bildungsgesetz von", "setze die folge fort"),
                Command.TRUTH_TABLE to listOf("wahrheitstafel von", "wahrheitstafel", "wahrheitstabelle von", "wahrheitstabelle", "tautologie"),
                Command.TRIANGLE to listOf("löse das dreieck", "dreieck mit", "dreieck"),
                Command.CONIC to listOf("kegelschnitt", "ellipse", "hyperbel", "parabel", "brennpunkte von", "exzentrizität von", "brennpunkt von", "scheitelpunkt von", "scheitel von", "leitlinie von"),
                Command.LINE to listOf("gerade durch die punkte", "gerade durch", "geradengleichung durch", "gleichung der geraden durch"),
                Command.PLANE to listOf("ebene durch die punkte", "ebene durch", "ebenengleichung durch", "gleichung der ebene durch"),
                Command.DIOPHANTINE to listOf("ganzzahlige lösungen von", "ganzzahlige lösungen", "diophantische gleichung", "löse in den ganzen zahlen", "löse in ganzen zahlen"),
            ),
            of = setOf("von"),
            from = words("von"),
            to = words("bis", "gegen"),
            forWords = words("für"),
            respect = words("nach"),
            approach = words("wenn", "um", "an der stelle", "bei"),
            articles = setOf("die", "der", "das", "den", "dem"),
            nouns = words("gleichung", "funktion", "term", "matrix", "parabel", "ellipse", "hyperbel"),
            between = listOf("zwischen" to "und"),
            constraints = listOf("unter der nebenbedingung", "mit der nebenbedingung", "unter der bedingung"),
        )

        val SPANISH = Vocabulary(
            "es",
            mapOf(
                Command.SOLVE to listOf("resuelve", "resolver", "resuelva"),
                Command.FACTOR to listOf("factoriza", "factorizar", "factorice"),
                Command.EXPAND to listOf("desarrolla", "desarrollar", "expande", "expandir"),
                Command.SIMPLIFY to listOf("simplifica", "simplificar", "simplifique"),
                Command.DERIVATIVE to listOf("derivada de", "derivada", "deriva", "derivar"),
                Command.INTEGRAL to listOf("integral de", "integral", "integra", "integrar", "primitiva de", "primitiva", "antiderivada de"),
                Command.LIMIT to listOf("límite de", "límite", "limite de", "limite"),
                Command.SUM to listOf("suma de", "suma", "sumatoria de", "sumatoria"),
                Command.PLOT to listOf("grafica", "graficar", "gráfica de", "gráfica", "dibuja", "dibujar", "representa gráficamente", "representa"),
                Command.CALCULATE to listOf("calcula", "calcular", "evalúa", "evaluar", "halla", "hallar", "encuentra", "determina", "cuánto es", "cuanto es"),
                Command.PARTIAL_FRACTIONS to listOf("fracciones parciales de", "fracciones parciales", "descompón en fracciones parciales", "descomposición en fracciones simples de", "descomposición en fracciones simples", "descomposición en fracciones parciales de", "descomposición en fracciones parciales", "descompón en fracciones simples", "descomponer en fracciones simples", "descomponer en fracciones parciales", "fracciones simples de", "fracciones simples"),
                Command.SERIES to listOf("serie de taylor de", "serie de taylor", "polinomio de taylor de", "polinomio de taylor", "serie de maclaurin de", "serie de maclaurin", "desarrollo en serie de", "desarrollo de taylor de", "desarrollo en serie de taylor de", "desarrollo en serie de taylor", "desarrollo en serie de maclaurin de", "desarrolla en serie de taylor", "desarrollar en serie de taylor", "desarrolla en serie de potencias", "desarrollo de taylor", "serie de potencias de", "polinomio de maclaurin de", "serie de laurent de", "serie de laurent"),
                Command.DETERMINANT to listOf("determinante de", "determinante"),
                Command.INVERSE to listOf("matriz inversa de", "inversa de", "inversa", "matriz inversa"),
                Command.EIGEN to listOf("valores propios de", "valores propios", "autovalores de", "autovalores", "vectores propios de", "autovectores de", "autovalores y autovectores de", "valores y vectores propios de"),
                Command.ROW_REDUCE to listOf("forma escalonada reducida de", "forma escalonada de", "forma escalonada", "escalona", "rango de", "rango", "forma escalonada reducida"),
                Command.TRANSPOSE to listOf("traspuesta de", "transpuesta de", "traspuesta", "transpuesta"),
                Command.DECOMPOSE to listOf("descomposición lu de", "descomposición lu", "descomposición qr de", "descomposición qr", "descomposición de cholesky de", "factorización lu de", "factorización lu", "descomposición de cholesky"),
                Command.MATRIX_EXP to listOf("exponencial de la matriz", "exponencial matricial de", "exponencial matricial"),
                Command.GRADIENT to listOf("gradiente de", "gradiente"),
                Command.DIVERGENCE to listOf("divergencia de", "divergencia"),
                Command.CURL to listOf("rotacional de", "rotacional", "rotor de"),
                Command.JACOBIAN to listOf("matriz jacobiana de", "jacobiana de", "jacobiano de", "matriz jacobiana"),
                Command.LAPLACIAN to listOf("laplaciano de", "laplaciano"),
                Command.LEAST_SQUARES to listOf("solución por mínimos cuadrados de", "mínimos cuadrados"),
                Command.EXTREMA to listOf("maximiza", "minimiza", "extremos de", "puntos críticos de", "maximizar", "minimizar"),
                Command.LAPLACE to listOf("transformada de laplace de", "transformada de laplace"),
                Command.INVERSE_LAPLACE to listOf("transformada inversa de laplace de", "transformada inversa de laplace", "laplace inversa de", "transformada de laplace inversa de", "transformada de laplace inversa", "antitransformada de laplace de", "antitransformada de laplace"),
                Command.FOURIER to listOf("serie de fourier de", "serie de fourier", "desarrollo en serie de fourier de", "desarrolla en serie de fourier"),
                Command.NEWTON to listOf("método de newton para", "método de newton", "método de newton-raphson"),
                Command.BISECTION to listOf("método de bisección para", "método de bisección", "bisección", "método de la bisección"),
                Command.EULER to listOf("método de euler para", "método de euler"),
                Command.RUNGE_KUTTA to listOf("método de runge-kutta para", "método de runge-kutta", "runge-kutta"),
                Command.TRAPEZOID to listOf("regla del trapecio para", "regla del trapecio", "regla de los trapecios", "método del trapecio"),
                Command.SIMPSON to listOf("regla de simpson para", "regla de simpson", "método de simpson"),
                Command.POLAR to listOf("forma polar de", "forma polar", "forma exponencial de", "forma trigonométrica de", "forma trigonométrica"),
                Command.RESIDUES to listOf("residuos de", "residuos", "polos de", "residuo de"),
                Command.NUMBER_FACTS to listOf("es primo", "divisores de", "propiedades del número", "es un número primo"),
                Command.STATISTICS to listOf("media de", "promedio de", "mediana de", "estadística de", "estadísticas de", "desviación estándar de", "desviación típica de", "varianza de", "moda de"),
                Command.REGRESSION to listOf("regresión lineal de", "regresión lineal", "recta de regresión de", "recta de regresión", "regresión", "recta de mejor ajuste"),
                Command.SEQUENCE to listOf("siguientes términos de", "siguiente término de", "sucesión", "término general de", "patrón de"),
                Command.TRUTH_TABLE to listOf("tabla de verdad de", "tabla de verdad", "tautología"),
                Command.TRIANGLE to listOf("resuelve el triángulo", "triángulo con", "triángulo"),
                Command.CONIC to listOf("cónica", "elipse", "hipérbola", "parábola", "focos de", "excentricidad de", "foco de", "vértice de", "directriz de"),
                Command.LINE to listOf("recta que pasa por", "recta por los puntos", "recta por", "ecuación de la recta que pasa por"),
                Command.PLANE to listOf("plano que pasa por", "plano por los puntos", "plano por", "ecuación del plano que pasa por"),
                Command.DIOPHANTINE to listOf("soluciones enteras de", "soluciones enteras", "ecuación diofántica", "resuelve en los enteros"),
            ),
            of = setOf("de"),
            from = words("de", "desde"),
            to = words("a", "hasta"),
            forWords = words("para"),
            respect = words("respecto a", "respecto de", "con respecto a"),
            approach = words("cuando", "alrededor de", "en torno a", "centrada en"),
            articles = setOf("el", "la", "los", "las", "lo"),
            nouns = words("ecuación", "función", "expresión", "polinomio", "matriz"),
            arrows = listOf("tiende a", "tiende hacia"),
            between = listOf("entre" to "y"),
            constraints = listOf("sujeto a", "con la restricción", "sujeta a", "con la condición", "bajo la restricción"),
        )

        val FRENCH = Vocabulary(
            "fr",
            mapOf(
                Command.SOLVE to listOf("résous", "résoudre", "résolvez", "resous", "resoudre"),
                Command.FACTOR to listOf("factorise", "factoriser", "factorisez"),
                Command.EXPAND to listOf("développe", "développer", "développez", "developpe", "developper"),
                Command.SIMPLIFY to listOf("simplifie", "simplifier", "simplifiez"),
                Command.DERIVATIVE to listOf("dérivée de", "dérivée", "dérive", "dériver", "derivee de", "derivee", "deriver"),
                Command.INTEGRAL to listOf("intégrale de", "intégrale", "intègre", "intégrer", "primitive de", "primitive", "integrale de", "integrale", "integrer"),
                Command.LIMIT to listOf("limite de", "limite"),
                Command.SUM to listOf("somme de", "somme"),
                Command.PLOT to listOf("trace", "tracer", "tracez", "dessine", "graphe de", "courbe de"),
                Command.CALCULATE to listOf("calcule", "calculer", "calculez", "évalue", "détermine", "déterminer", "trouve", "combien fait"),
                Command.PARTIAL_FRACTIONS to listOf("décompose en éléments simples", "décomposition en éléments simples de", "décomposition en éléments simples", "éléments simples de", "éléments simples", "décomposer en éléments simples", "décomposez en éléments simples"),
                Command.SERIES to listOf("série de taylor de", "série de taylor", "serie de taylor de", "développement limité de", "développement limité", "developpement limite de", "développement en série de", "polynôme de taylor de", "polynôme de taylor", "série de maclaurin de", "développement de taylor de", "développement de taylor", "développement en série entière de", "série entière de", "développe en série entière", "développer en série entière", "développe en série de taylor", "série de maclaurin", "série de laurent de", "série de laurent"),
                Command.DETERMINANT to listOf("déterminant de", "déterminant", "determinant de"),
                Command.INVERSE to listOf("matrice inverse de", "inverse de", "inverse"),
                Command.EIGEN to listOf("valeurs propres de", "valeurs propres", "vecteurs propres de", "vecteurs propres", "valeurs propres et vecteurs propres de", "éléments propres de"),
                Command.ROW_REDUCE to listOf("forme échelonnée réduite de", "forme échelonnée de", "forme échelonnée", "échelonne", "rang de", "rang", "forme échelonnée réduite", "pivot de gauss"),
                Command.TRANSPOSE to listOf("transposée de", "transposée"),
                Command.DECOMPOSE to listOf("décomposition lu de", "décomposition lu", "décomposition qr de", "décomposition qr", "décomposition de cholesky de", "factorisation lu de", "factorisation lu", "décomposition de cholesky"),
                Command.MATRIX_EXP to listOf("exponentielle de la matrice", "exponentielle matricielle de", "exponentielle de matrice", "exponentielle matricielle"),
                Command.GRADIENT to listOf("gradient de"),
                Command.DIVERGENCE to listOf("divergence de"),
                Command.CURL to listOf("rotationnel de", "rotationnel"),
                Command.JACOBIAN to listOf("matrice jacobienne de", "jacobienne de", "matrice jacobienne"),
                Command.LAPLACIAN to listOf("laplacien de", "laplacien"),
                Command.LEAST_SQUARES to listOf("solution aux moindres carrés de", "moindres carrés"),
                Command.EXTREMA to listOf("maximiser", "minimiser", "extremums de", "extrema de", "points critiques de", "maximise", "minimise"),
                Command.LAPLACE to listOf("transformée de laplace de", "transformée de laplace"),
                Command.INVERSE_LAPLACE to listOf("transformée de laplace inverse de", "transformée inverse de laplace de", "laplace inverse de", "transformée de laplace inverse"),
                Command.FOURIER to listOf("série de fourier de", "série de fourier", "développement en série de fourier de", "développe en série de fourier"),
                Command.NEWTON to listOf("méthode de newton pour", "méthode de newton", "méthode de newton-raphson"),
                Command.BISECTION to listOf("méthode de dichotomie pour", "dichotomie", "méthode de bissection", "méthode de dichotomie", "méthode de la dichotomie"),
                Command.EULER to listOf("méthode d'euler pour", "méthode d'euler", "méthode d’euler"),
                Command.RUNGE_KUTTA to listOf("méthode de runge-kutta pour", "méthode de runge-kutta", "runge-kutta"),
                Command.TRAPEZOID to listOf("méthode des trapèzes pour", "méthode des trapèzes", "règle des trapèzes", "formule des trapèzes"),
                Command.SIMPSON to listOf("méthode de simpson pour", "méthode de simpson", "règle de simpson", "formule de simpson"),
                Command.POLAR to listOf("forme polaire de", "forme polaire", "forme exponentielle de", "forme trigonométrique de", "forme trigonométrique"),
                Command.RESIDUES to listOf("résidus de", "résidus", "pôles de", "résidu de"),
                Command.NUMBER_FACTS to listOf("est premier", "diviseurs de", "propriétés du nombre", "est-ce que est premier", "est-il premier", "est-il un nombre premier", "est un nombre premier"),
                Command.STATISTICS to listOf("moyenne de", "médiane de", "statistiques de", "écart type de", "écart-type de", "variance de", "mode de"),
                Command.REGRESSION to listOf("régression linéaire de", "régression linéaire", "droite de régression de", "droite de régression", "régression", "droite des moindres carrés"),
                Command.SEQUENCE to listOf("termes suivants de", "terme suivant de", "terme général de", "continue la suite", "suite"),
                Command.TRUTH_TABLE to listOf("table de vérité de", "table de vérité", "tautologie"),
                Command.TRIANGLE to listOf("résous le triangle", "résoudre le triangle", "triangle avec", "triangle"),
                Command.CONIC to listOf("conique", "ellipse", "hyperbole", "parabole", "foyers de", "excentricité de", "foyer de", "sommet de", "directrice de"),
                Command.LINE to listOf("droite passant par", "droite par les points", "droite par", "équation de la droite passant par"),
                Command.PLANE to listOf("plan passant par", "plan par les points", "plan par", "équation du plan passant par"),
                Command.DIOPHANTINE to listOf("solutions entières de", "solutions entières", "équation diophantienne", "résoudre dans les entiers", "résous dans les entiers", "résous dans z", "résoudre dans z"),
            ),
            of = setOf("de", "du"),
            from = words("de"),
            to = words("à", "a", "jusqu'à"),
            forWords = words("pour"),
            respect = words("par rapport à", "par rapport a", "selon"),
            // "limite de x^2 en 2": the lycée names the point after "en".
            approach = words("quand", "lorsque", "en", "au voisinage de", "autour de"),
            articles = setOf("le", "la", "les", "l'"),
            nouns = words("équation", "fonction", "expression", "polynôme", "matrice"),
            arrows = listOf("tend vers"),
            between = listOf("entre" to "et"),
            constraints = listOf("sous la contrainte", "avec la contrainte", "sous les contraintes", "sous la condition", "avec la condition"),
        )

        val INDONESIAN = Vocabulary(
            "id",
            mapOf(
                Command.SOLVE to listOf("selesaikan", "pecahkan", "tentukan penyelesaian"),
                Command.FACTOR to listOf("faktorkan", "faktorisasi"),
                Command.EXPAND to listOf("jabarkan", "uraikan", "ekspansikan"),
                Command.SIMPLIFY to listOf("sederhanakan"),
                Command.DERIVATIVE to listOf("turunan pertama dari", "turunan pertama", "turunan dari", "turunan", "turunkan"),
                Command.INTEGRAL to listOf("integral dari", "integral", "integralkan", "antiturunan dari", "antiturunan"),
                Command.LIMIT to listOf("limit dari", "limit"),
                Command.SUM to listOf("jumlah dari", "jumlah", "jumlahkan"),
                Command.PLOT to listOf("gambarkan grafik", "gambarkan", "grafik dari", "grafik", "gambar"),
                Command.CALCULATE to listOf("hitunglah", "hitung", "tentukan", "carilah", "cari", "berapa"),
                Command.PARTIAL_FRACTIONS to listOf("pecahan parsial dari", "pecahan parsial", "uraikan menjadi pecahan parsial", "dekomposisi pecahan parsial dari", "dekomposisi pecahan parsial"),
                Command.SERIES to listOf("deret taylor dari", "deret taylor", "deret maclaurin dari", "deret maclaurin", "polinomial taylor dari", "polinom taylor dari", "ekspansi deret dari", "ekspansikan dalam deret taylor", "ekspansikan dalam deret maclaurin", "uraikan dalam deret taylor", "deret pangkat dari", "ekspansi taylor dari", "ekspansi taylor"),
                Command.DETERMINANT to listOf("determinan dari", "determinan"),
                Command.INVERSE to listOf("invers matriks dari", "invers dari", "invers", "invers matriks"),
                Command.EIGEN to listOf("nilai eigen dari", "nilai eigen", "vektor eigen dari", "vektor eigen", "nilai eigen dan vektor eigen dari"),
                Command.ROW_REDUCE to listOf("eselon baris tereduksi dari", "eselon baris dari", "eselon baris", "rank dari", "bentuk eselon baris", "eselon baris tereduksi", "eliminasi gauss"),
                Command.TRANSPOSE to listOf("transpos dari", "transpose dari", "transpos"),
                Command.DECOMPOSE to listOf("dekomposisi lu dari", "dekomposisi lu", "dekomposisi qr dari", "dekomposisi qr", "dekomposisi cholesky dari", "faktorisasi lu dari", "faktorisasi lu", "faktorisasi qr"),
                Command.MATRIX_EXP to listOf("eksponensial matriks dari", "eksponensial matriks"),
                Command.GRADIENT to listOf("gradien dari", "gradien"),
                Command.DIVERGENCE to listOf("divergensi dari", "divergensi"),
                Command.CURL to listOf("curl dari", "rotasi dari"),
                Command.JACOBIAN to listOf("matriks jacobian dari", "jacobian dari", "matriks jacobian"),
                Command.LAPLACIAN to listOf("laplacian dari", "laplasian dari"),
                Command.LEAST_SQUARES to listOf("metode kuadrat terkecil", "kuadrat terkecil"),
                Command.EXTREMA to listOf("maksimumkan", "minimumkan", "titik kritis dari", "nilai ekstrem dari", "maksimalkan", "minimalkan"),
                Command.LAPLACE to listOf("transformasi laplace dari", "transformasi laplace"),
                Command.INVERSE_LAPLACE to listOf("invers transformasi laplace dari", "transformasi laplace invers dari", "laplace invers dari", "invers transformasi laplace", "transformasi laplace invers"),
                Command.FOURIER to listOf("deret fourier dari", "deret fourier"),
                Command.NEWTON to listOf("metode newton untuk", "metode newton", "metode newton-raphson"),
                Command.BISECTION to listOf("metode biseksi untuk", "metode biseksi", "biseksi", "metode bagi dua"),
                Command.EULER to listOf("metode euler untuk", "metode euler"),
                Command.RUNGE_KUTTA to listOf("metode runge-kutta untuk", "metode runge-kutta", "runge-kutta"),
                Command.TRAPEZOID to listOf("aturan trapesium untuk", "aturan trapesium", "metode trapesium", "kaidah trapesium"),
                Command.SIMPSON to listOf("aturan simpson untuk", "aturan simpson", "metode simpson", "kaidah simpson"),
                Command.POLAR to listOf("bentuk polar dari", "bentuk polar", "bentuk eksponensial dari"),
                Command.RESIDUES to listOf("residu dari", "residu", "kutub dari"),
                Command.NUMBER_FACTS to listOf("apakah prima", "pembagi dari", "sifat bilangan", "apakah bilangan prima", "faktor dari"),
                Command.STATISTICS to listOf("rata-rata dari", "rata-rata", "median dari", "statistik dari", "simpangan baku dari", "variansi dari", "ragam dari", "modus dari"),
                Command.REGRESSION to listOf("regresi linear dari", "regresi linear", "garis regresi", "regresi"),
                Command.SEQUENCE to listOf("suku berikutnya dari", "suku berikutnya", "barisan", "rumus suku ke-n dari", "pola dari"),
                Command.TRUTH_TABLE to listOf("tabel kebenaran dari", "tabel kebenaran", "tautologi"),
                Command.TRIANGLE to listOf("selesaikan segitiga", "segitiga dengan", "segitiga"),
                Command.CONIC to listOf("irisan kerucut", "elips", "hiperbola", "parabola", "fokus dari", "eksentrisitas dari"),
                Command.LINE to listOf("garis melalui titik", "garis melalui", "persamaan garis melalui"),
                Command.PLANE to listOf("bidang melalui titik", "bidang melalui", "persamaan bidang melalui"),
                Command.DIOPHANTINE to listOf("solusi bilangan bulat dari", "solusi bulat dari", "persamaan diophantine"),
            ),
            of = setOf("dari"),
            from = words("dari"),
            to = words("sampai", "hingga", "ke"),
            forWords = words("untuk"),
            respect = words("terhadap"),
            approach = words("saat", "ketika", "di sekitar", "di titik"),
            nouns = words("persamaan", "fungsi", "bentuk", "matriks"),
            arrows = listOf("mendekati", "menuju"),
            between = listOf("antara" to "dan"),
            constraints = listOf("dengan kendala", "dengan syarat", "terhadap kendala", "dengan batasan"),
        )

        val ITALIAN = Vocabulary(
            "it",
            mapOf(
                Command.SOLVE to listOf("risolvi", "risolvere"),
                Command.FACTOR to listOf("scomponi in fattori", "scomponi", "scomporre", "fattorizza", "fattorizzare"),
                Command.EXPAND to listOf("sviluppa", "sviluppare", "espandi"),
                Command.SIMPLIFY to listOf("semplifica", "semplificare"),
                Command.DERIVATIVE to listOf("derivata di", "derivata", "deriva", "derivare"),
                Command.INTEGRAL to listOf("integrale di", "integrale", "integra", "integrare", "primitiva di", "primitiva"),
                Command.LIMIT to listOf("limite di", "limite"),
                Command.SUM to listOf("somma di", "somma", "sommatoria di", "sommatoria"),
                Command.PLOT to listOf("disegna", "traccia", "grafico di", "grafico"),
                Command.CALCULATE to listOf("calcola", "calcolare", "trova", "determina", "quanto fa"),
                Command.PARTIAL_FRACTIONS to listOf("fratti semplici di", "fratti semplici", "scomponi in fratti semplici", "decomposizione in fratti semplici di", "decomposizione in fratti semplici", "scomporre in fratti semplici"),
                Command.SERIES to listOf("serie di taylor di", "serie di taylor", "sviluppo di taylor di", "sviluppo di taylor", "polinomio di taylor di", "polinomio di taylor", "serie di maclaurin di", "sviluppo in serie di", "sviluppo di maclaurin di", "sviluppa in serie di taylor", "sviluppare in serie di taylor", "sviluppa in serie di maclaurin", "sviluppo in serie di taylor di", "sviluppo in serie di taylor", "serie di maclaurin", "sviluppo di maclaurin", "serie di potenze di", "formula di taylor di", "serie di laurent di"),
                Command.DETERMINANT to listOf("determinante di", "determinante"),
                Command.INVERSE to listOf("matrice inversa di", "inversa di", "inversa"),
                Command.EIGEN to listOf("autovalori di", "autovalori", "autovettori di", "autovettori", "autovalori e autovettori di"),
                Command.ROW_REDUCE to listOf("forma a scala ridotta di", "forma a scala di", "riduci a scala", "rango di", "rango", "forma a scala", "forma a scala ridotta", "eliminazione di gauss"),
                Command.TRANSPOSE to listOf("trasposta di", "trasposta"),
                Command.DECOMPOSE to listOf("decomposizione lu di", "decomposizione lu", "decomposizione qr di", "decomposizione qr", "decomposizione di cholesky di", "fattorizzazione lu di", "fattorizzazione lu", "decomposizione di cholesky"),
                Command.MATRIX_EXP to listOf("esponenziale della matrice", "esponenziale di matrice"),
                Command.GRADIENT to listOf("gradiente di", "gradiente"),
                Command.DIVERGENCE to listOf("divergenza di", "divergenza"),
                Command.CURL to listOf("rotore di", "rotore"),
                Command.JACOBIAN to listOf("matrice jacobiana di", "jacobiana di", "matrice jacobiana"),
                Command.LAPLACIAN to listOf("laplaciano di", "laplaciano"),
                Command.LEAST_SQUARES to listOf("soluzione ai minimi quadrati di", "minimi quadrati"),
                Command.EXTREMA to listOf("massimizza", "minimizza", "estremi di", "punti critici di", "massimizzare", "minimizzare", "estremi vincolati di"),
                Command.LAPLACE to listOf("trasformata di laplace di", "trasformata di laplace"),
                Command.INVERSE_LAPLACE to listOf("antitrasformata di laplace di", "trasformata inversa di laplace di", "antitrasformata di laplace"),
                Command.FOURIER to listOf("serie di fourier di", "serie di fourier", "sviluppo in serie di fourier di", "sviluppa in serie di fourier"),
                Command.NEWTON to listOf("metodo di newton per", "metodo di newton", "metodo delle tangenti", "metodo di newton-raphson"),
                Command.BISECTION to listOf("metodo di bisezione per", "metodo di bisezione", "bisezione", "metodo della bisezione"),
                Command.EULER to listOf("metodo di eulero per", "metodo di eulero", "metodo di euler"),
                Command.RUNGE_KUTTA to listOf("metodo di runge-kutta per", "metodo di runge-kutta", "runge-kutta"),
                Command.TRAPEZOID to listOf("regola dei trapezi per", "regola dei trapezi", "metodo dei trapezi", "formula dei trapezi"),
                Command.SIMPSON to listOf("regola di simpson per", "regola di simpson", "metodo di cavalieri-simpson", "metodo di simpson", "formula di cavalieri-simpson", "formula di simpson"),
                Command.POLAR to listOf("forma polare di", "forma polare", "forma esponenziale di", "forma trigonometrica di"),
                Command.RESIDUES to listOf("residui di", "residui", "poli di", "residuo di"),
                Command.NUMBER_FACTS to listOf("è primo", "divisori di", "proprietà del numero", "è un numero primo"),
                Command.STATISTICS to listOf("media di", "mediana di", "statistiche di", "deviazione standard di", "scarto quadratico medio di", "varianza di", "moda di"),
                Command.REGRESSION to listOf("regressione lineare di", "regressione lineare", "retta di regressione di", "retta di regressione", "regressione"),
                Command.SEQUENCE to listOf("termini successivi di", "termine successivo di", "successione", "termine generale di"),
                Command.TRUTH_TABLE to listOf("tavola di verità di", "tavola di verità", "tabella di verità", "tautologia"),
                Command.TRIANGLE to listOf("risolvi il triangolo", "triangolo con", "triangolo"),
                Command.CONIC to listOf("conica", "ellisse", "iperbole", "parabola", "fuochi di", "eccentricità di", "fuoco di", "vertice di", "direttrice di"),
                Command.LINE to listOf("retta passante per", "retta per i punti", "retta per", "equazione della retta passante per"),
                Command.PLANE to listOf("piano passante per", "piano per i punti", "piano per", "equazione del piano passante per"),
                Command.DIOPHANTINE to listOf("soluzioni intere di", "soluzioni intere", "equazione diofantea", "risolvi negli interi"),
            ),
            of = setOf("di", "del", "della"),
            from = words("da"),
            to = words("a", "fino a"),
            forWords = words("per"),
            respect = words("rispetto a"),
            approach = words("quando", "centrato in", "centrata in", "nel punto", "attorno a"),
            articles = setOf("il", "lo", "la", "i", "gli", "le", "l'"),
            nouns = words("equazione", "funzione", "espressione", "polinomio", "matrice"),
            arrows = listOf("che tende a", "tende a"),
            between = listOf("tra" to "e", "fra" to "e"),
            // "scomponi x^2-1 in fattori".
            ignored = setOf("in", "fattori"),
            constraints = listOf("soggetto a", "con il vincolo", "con vincolo", "sotto il vincolo", "vincolato a"),
        )

        val DUTCH = Vocabulary(
            "nl",
            mapOf(
                Command.SOLVE to listOf("los op", "oplossen", "los"),
                Command.FACTOR to listOf("ontbind in factoren", "ontbinden in factoren", "ontbind", "factoriseer"),
                Command.EXPAND to listOf("werk uit", "werk", "uitwerken", "haakjes wegwerken", "vermenigvuldig uit"),
                Command.SIMPLIFY to listOf("vereenvoudig", "vereenvoudigen"),
                Command.DERIVATIVE to listOf("afgeleide van", "afgeleide", "differentieer", "differentiëren"),
                Command.INTEGRAL to listOf("integraal van", "integraal", "integreer", "primitieve van", "primitieve"),
                Command.LIMIT to listOf("limiet van", "limiet"),
                Command.SUM to listOf("som van", "som"),
                Command.PLOT to listOf("teken", "plot", "grafiek van", "grafiek"),
                Command.CALCULATE to listOf("bereken", "reken uit", "bepaal", "wat is"),
                Command.PARTIAL_FRACTIONS to listOf("breuksplitsen", "splits in breuken", "splits in partiële breuken", "partiële breuken van", "partiële breuken", "breuksplitsing van", "breuksplitsing"),
                Command.SERIES to listOf("taylorreeks van", "taylorreeks", "taylorpolynoom van", "taylorpolynoom", "maclaurinreeks van", "maclaurinreeks", "reeksontwikkeling van", "taylorontwikkeling van", "taylor-reeks van", "taylor-reeks", "machtreeks van", "machtreeks", "taylorbenadering van", "taylorveelterm van", "taylorontwikkeling", "reeksontwikkeling", "ontwikkel in een taylorreeks"),
                Command.DETERMINANT to listOf("determinant van", "determinant"),
                Command.INVERSE to listOf("inverse matrix van", "inverse van", "inverse"),
                Command.EIGEN to listOf("eigenwaarden van", "eigenwaarden", "eigenvectoren van", "eigenvectoren", "eigenwaarden en eigenvectoren van"),
                Command.ROW_REDUCE to listOf("rijgereduceerde vorm van", "trapvorm van", "trapvorm", "rang van", "rang", "rijtrapvorm", "rijtrapvorm van", "gereduceerde rijtrapvorm van", "gereduceerde rijtrapvorm"),
                Command.TRANSPOSE to listOf("getransponeerde van", "getransponeerde", "transponeer"),
                Command.DECOMPOSE to listOf("lu-decompositie van", "lu-decompositie", "qr-decompositie van", "qr-decompositie", "cholesky-decompositie van", "lu-ontbinding van", "lu-ontbinding"),
                Command.MATRIX_EXP to listOf("matrixexponentiaal van", "matrixexponentiaal"),
                Command.GRADIENT to listOf("gradiënt van", "gradiënt", "gradient van"),
                Command.DIVERGENCE to listOf("divergentie van", "divergentie"),
                Command.CURL to listOf("rotatie van", "rotor van"),
                Command.JACOBIAN to listOf("jacobiaan van", "jacobimatrix van", "jacobiaan"),
                Command.LAPLACIAN to listOf("laplaciaan van", "laplaciaan"),
                Command.LEAST_SQUARES to listOf("kleinstekwadratenmethode", "kleinste kwadraten", "methode van de kleinste kwadraten"),
                Command.EXTREMA to listOf("maximaliseer", "minimaliseer", "extrema van", "kritieke punten van"),
                Command.LAPLACE to listOf("laplacegetransformeerde van", "laplacetransformatie van", "laplacetransformatie", "laplace-transformatie van", "laplace-transformatie"),
                Command.INVERSE_LAPLACE to listOf("inverse laplacetransformatie van", "inverse laplacetransformatie", "inverse laplace-transformatie van", "inverse laplace-transformatie"),
                Command.FOURIER to listOf("fourierreeks van", "fourierreeks"),
                Command.NEWTON to listOf("methode van newton voor", "methode van newton", "newton-raphson", "newton-raphson-methode"),
                Command.BISECTION to listOf("bisectiemethode voor", "bisectiemethode", "bisectie", "halveringsmethode"),
                Command.EULER to listOf("methode van euler voor", "methode van euler", "euler-methode"),
                Command.RUNGE_KUTTA to listOf("runge-kuttamethode voor", "runge-kuttamethode", "runge-kutta", "runge-kutta-methode"),
                Command.TRAPEZOID to listOf("trapeziumregel voor", "trapeziumregel"),
                Command.SIMPSON to listOf("regel van simpson voor", "regel van simpson", "simpsonregel", "simpson-regel"),
                Command.POLAR to listOf("poolvorm van", "poolvorm", "exponentiële vorm van", "goniometrische vorm van", "goniometrische vorm"),
                Command.RESIDUES to listOf("residuen van", "residuen", "polen van", "residu van"),
                Command.NUMBER_FACTS to listOf("is priem", "delers van", "eigenschappen van het getal", "is een priemgetal"),
                Command.STATISTICS to listOf("gemiddelde van", "mediaan van", "statistiek van", "standaardafwijking van", "variantie van", "modus van"),
                Command.REGRESSION to listOf("lineaire regressie van", "lineaire regressie", "regressielijn van", "regressielijn", "regressie"),
                Command.SEQUENCE to listOf("volgende termen van", "volgende term van", "rij van", "rij", "voorschrift van"),
                Command.TRUTH_TABLE to listOf("waarheidstabel van", "waarheidstabel", "tautologie"),
                Command.TRIANGLE to listOf("los de driehoek op", "driehoek met", "driehoek"),
                Command.CONIC to listOf("kegelsnede", "ellips", "hyperbool", "parabool", "brandpunten van", "excentriciteit van", "brandpunt van", "top van"),
                Command.LINE to listOf("lijn door de punten", "lijn door", "rechte door", "vergelijking van de lijn door", "vergelijking van de rechte door"),
                Command.PLANE to listOf("vlak door de punten", "vlak door", "vergelijking van het vlak door"),
                Command.DIOPHANTINE to listOf("gehele oplossingen van", "gehele oplossingen", "diofantische vergelijking", "los op in gehele getallen"),
            ),
            of = setOf("van"),
            from = words("van"),
            to = words("tot"),
            forWords = words("voor"),
            respect = words("naar"),
            approach = words("als", "wanneer", "rondom", "rond"),
            articles = setOf("de", "het", "een"),
            nouns = words("vergelijking", "functie", "uitdrukking", "matrix"),
            arrows = listOf("nadert tot", "nadert", "gaat naar"),
            between = listOf("tussen" to "en"),
            // "los x^2 = 4 op", "werk (x+1)^2 uit", "ontbind x^2-1 in factoren": the verb's other half.
            ignored = setOf("op", "uit", "in", "factoren"),
            constraints = listOf("onder de voorwaarde", "met als voorwaarde", "onder de nevenvoorwaarde", "met de nevenvoorwaarde", "onder de randvoorwaarde"),
        )

        val POLISH = Vocabulary(
            "pl",
            mapOf(
                Command.SOLVE to listOf("rozwiąż", "rozwiaz", "rozwiązać"),
                Command.FACTOR to listOf("rozłóż na czynniki", "rozloz na czynniki", "rozłóż", "faktoryzuj"),
                Command.EXPAND to listOf("wymnóż", "wymnoz", "rozwiń", "rozwin"),
                Command.SIMPLIFY to listOf("uprość", "uprosc", "uprościć"),
                Command.DERIVATIVE to listOf("oblicz pochodną", "pochodna z", "pochodna", "pochodną", "różniczkuj", "rozniczkuj"),
                Command.INTEGRAL to listOf("oblicz całkę", "całka oznaczona z", "całka nieoznaczona z", "całka oznaczona", "całka nieoznaczona", "całka z", "całka", "całkę", "calka z", "calka", "scałkuj", "całkuj"),
                Command.LIMIT to listOf("oblicz granicę", "granica", "granica z"),
                Command.SUM to listOf("suma"),
                Command.PLOT to listOf("narysuj wykres", "narysuj", "wykres funkcji", "wykres"),
                Command.CALCULATE to listOf("oblicz", "wyznacz", "znajdź", "ile wynosi", "ile to"),
                Command.PARTIAL_FRACTIONS to listOf("rozłóż na ułamki proste", "ułamki proste", "rozkład na ułamki proste", "rozloz na ulamki proste", "rozklad na ulamki proste", "ulamki proste"),
                Command.SERIES to listOf("rozwiń w szereg taylora", "rozwinięcie w szereg taylora", "szereg taylora funkcji", "szereg taylora", "szereg maclaurina", "wielomian taylora", "rozwin w szereg taylora", "szereg potęgowy", "rozwiń w szereg maclaurina", "rozwin w szereg maclaurina", "rozwiń w szereg potęgowy", "rozwiń w szereg", "rozwinięcie taylora", "wzór taylora", "szereg laurenta"),
                Command.DETERMINANT to listOf("oblicz wyznacznik", "wyznacznik macierzy", "wyznacznik"),
                Command.INVERSE to listOf("macierz odwrotna do", "macierz odwrotna", "odwrotność macierzy", "macierz odwrotną do", "macierz odwrotną"),
                Command.EIGEN to listOf("wartości własne macierzy", "wartości własne", "wektory własne", "wartosci wlasne"),
                Command.ROW_REDUCE to listOf("sprowadź do postaci schodkowej", "postać schodkowa", "rząd macierzy", "rząd", "postać schodkową", "eliminacja gaussa"),
                Command.TRANSPOSE to listOf("macierz transponowana", "transpozycja", "transponuj", "macierz transponowaną", "transpozycję"),
                Command.DECOMPOSE to listOf("rozkład lu macierzy", "rozkład lu", "rozkład qr", "rozkład choleskiego"),
                Command.MATRIX_EXP to listOf("eksponens macierzy", "eksponenta macierzy", "eksponentę macierzy", "funkcja wykładnicza macierzy", "funkcję wykładniczą macierzy"),
                Command.GRADIENT to listOf("gradient funkcji", "gradient"),
                Command.DIVERGENCE to listOf("dywergencja pola", "dywergencja", "dywergencję"),
                Command.CURL to listOf("rotacja pola", "rotacja", "rotację"),
                Command.JACOBIAN to listOf("macierz jacobiego", "jakobian"),
                Command.LAPLACIAN to listOf("laplasjan funkcji", "laplasjan"),
                Command.LEAST_SQUARES to listOf("metoda najmniejszych kwadratów", "najmniejsze kwadraty"),
                Command.EXTREMA to listOf("zmaksymalizuj", "zminimalizuj", "ekstrema funkcji", "punkty krytyczne", "ekstrema warunkowe", "ekstrema"),
                Command.LAPLACE to listOf("transformata laplace'a", "transformata laplace"),
                Command.INVERSE_LAPLACE to listOf("odwrotna transformata laplace'a", "odwrotna transformata laplace", "transformata odwrotna laplace'a"),
                Command.FOURIER to listOf("szereg fouriera funkcji", "szereg fouriera", "rozwiń w szereg fouriera"),
                Command.NEWTON to listOf("metoda newtona", "metoda stycznych", "metoda newtona-raphsona"),
                Command.BISECTION to listOf("metoda połowienia przedziału", "metoda bisekcji", "bisekcja", "metoda połowienia"),
                Command.EULER to listOf("metoda eulera"),
                Command.RUNGE_KUTTA to listOf("metoda rungego-kutty", "runge-kutta", "metoda runge-kutty"),
                Command.TRAPEZOID to listOf("metoda trapezów", "wzór trapezów"),
                Command.SIMPSON to listOf("metoda simpsona", "wzór simpsona", "metoda parabol"),
                Command.POLAR to listOf("postać trygonometryczna", "postać wykładnicza", "postać biegunowa"),
                Command.RESIDUES to listOf("residua", "residuum", "bieguny"),
                Command.NUMBER_FACTS to listOf("czy jest pierwsza", "dzielniki liczby", "dzielniki", "własności liczby", "czy jest liczbą pierwszą"),
                Command.STATISTICS to listOf("średnia z", "średnia", "mediana z", "mediana", "statystyki", "odchylenie standardowe", "wariancja", "dominanta", "moda"),
                Command.REGRESSION to listOf("regresja liniowa", "prosta regresji", "regresja"),
                Command.SEQUENCE to listOf("następne wyrazy ciągu", "następne wyrazy", "następny wyraz", "wzór ogólny ciągu", "ciąg"),
                Command.TRUTH_TABLE to listOf("tabela prawdy", "tabelka prawdy", "tautologia"),
                Command.TRIANGLE to listOf("rozwiąż trójkąt", "trójkąt o", "trójkąt"),
                Command.CONIC to listOf("krzywa stożkowa", "elipsa", "hiperbola", "parabola", "ogniska", "mimośród", "ognisko"),
                Command.LINE to listOf("prosta przechodząca przez punkty", "prosta przechodząca przez", "prosta przez", "równanie prostej przechodzącej przez"),
                Command.PLANE to listOf("płaszczyzna przechodząca przez punkty", "płaszczyzna przechodząca przez", "płaszczyzna przez", "równanie płaszczyzny przechodzącej przez"),
                Command.DIOPHANTINE to listOf("rozwiązania całkowite", "rozwiąż w liczbach całkowitych", "równanie diofantyczne"),
            ),
            of = setOf("z"),
            from = words("od"),
            to = words("do"),
            forWords = words("dla"),
            respect = words("względem", "wzgledem", "po"),
            approach = words("gdy", "przy", "w punkcie", "wokół punktu", "w otoczeniu punktu"),
            nouns = words("równanie", "funkcji", "funkcję", "wyrażenie", "wielomian", "macierzy", "macierz", "liczby", "liczba", "elipsy", "paraboli", "hiperboli"),
            arrows = listOf("dąży do", "zmierza do"),
            // "rozłóż x^2-1 na czynniki".
            ignored = setOf("na", "czynniki"),
            constraints = listOf("przy warunku", "z warunkiem", "pod warunkiem", "przy ograniczeniu"),
        )

        val PORTUGUESE = Vocabulary(
            "pt",
            mapOf(
                Command.SOLVE to listOf("resolva", "resolver", "resolve"),
                Command.FACTOR to listOf("fatore", "fatorar", "fatorize"),
                Command.EXPAND to listOf("expanda", "expandir", "desenvolva"),
                Command.SIMPLIFY to listOf("simplifique", "simplificar"),
                Command.DERIVATIVE to listOf("derivada de", "derivada", "derive", "derivar"),
                Command.INTEGRAL to listOf("integral de", "integral", "integre", "integrar", "primitiva de", "primitiva", "antiderivada de"),
                Command.LIMIT to listOf("limite de", "limite"),
                Command.SUM to listOf("soma de", "soma", "somatório de", "somatório"),
                Command.PLOT to listOf("plote", "desenhe", "esboce", "gráfico de", "gráfico", "grafico de"),
                Command.CALCULATE to listOf("calcule", "calcular", "determine", "encontre", "ache", "quanto é", "quanto e"),
                Command.PARTIAL_FRACTIONS to listOf("frações parciais de", "frações parciais", "decomponha em frações parciais", "decomposição em frações parciais de", "decomposição em frações parciais", "decompor em frações parciais", "decomponha em fracoes parciais", "fracoes parciais de", "fracoes parciais"),
                Command.SERIES to listOf("série de taylor de", "série de taylor", "serie de taylor de", "polinômio de taylor de", "polinômio de taylor", "série de maclaurin de", "série de maclaurin", "expansão em série de", "expansão em série de taylor de", "expanda em série de taylor", "desenvolva em série de taylor", "expanda em série de maclaurin", "expansão de taylor de", "série de potências de", "serie de taylor", "polinomio de taylor de", "série de laurent de"),
                Command.DETERMINANT to listOf("determinante de", "determinante"),
                Command.INVERSE to listOf("matriz inversa de", "inversa de", "inversa"),
                Command.EIGEN to listOf("autovalores de", "autovalores", "valores próprios de", "autovetores de", "autovetores", "autovalores e autovetores de", "vetores próprios de"),
                Command.ROW_REDUCE to listOf("forma escalonada reduzida de", "forma escalonada de", "escalone", "posto de", "posto", "forma escalonada", "escalonamento de", "forma escalonada reduzida"),
                Command.TRANSPOSE to listOf("transposta de", "transposta"),
                Command.DECOMPOSE to listOf("decomposição lu de", "decomposição lu", "decomposição qr de", "decomposição qr", "decomposição de cholesky de", "fatoração lu de"),
                Command.MATRIX_EXP to listOf("exponencial da matriz", "exponencial matricial de"),
                Command.GRADIENT to listOf("gradiente de", "gradiente"),
                Command.DIVERGENCE to listOf("divergente de", "divergência de", "divergente"),
                Command.CURL to listOf("rotacional de", "rotacional"),
                Command.JACOBIAN to listOf("matriz jacobiana de", "jacobiana de", "matriz jacobiana"),
                Command.LAPLACIAN to listOf("laplaciano de", "laplaciano"),
                Command.LEAST_SQUARES to listOf("solução por mínimos quadrados de", "mínimos quadrados"),
                Command.EXTREMA to listOf("maximize", "minimize", "extremos de", "pontos críticos de", "maximizar", "minimizar"),
                Command.LAPLACE to listOf("transformada de laplace de", "transformada de laplace"),
                Command.INVERSE_LAPLACE to listOf("transformada inversa de laplace de", "transformada inversa de laplace", "laplace inversa de", "transformada de laplace inversa de", "transformada de laplace inversa"),
                Command.FOURIER to listOf("série de fourier de", "série de fourier"),
                Command.NEWTON to listOf("método de newton para", "método de newton", "método de newton-raphson"),
                Command.BISECTION to listOf("método da bissecção para", "método da bisseção para", "método da bisseção", "bisseção", "método da bissecção"),
                Command.EULER to listOf("método de euler para", "método de euler"),
                Command.RUNGE_KUTTA to listOf("método de runge-kutta para", "método de runge-kutta", "runge-kutta"),
                Command.TRAPEZOID to listOf("regra do trapézio para", "regra do trapézio", "regra dos trapézios", "método dos trapézios"),
                Command.SIMPSON to listOf("regra de simpson para", "regra de simpson", "método de simpson"),
                Command.POLAR to listOf("forma polar de", "forma polar", "forma trigonométrica de", "forma exponencial de"),
                Command.RESIDUES to listOf("resíduos de", "resíduos", "polos de", "resíduo de"),
                Command.NUMBER_FACTS to listOf("é primo", "divisores de", "propriedades do número", "é um número primo"),
                Command.STATISTICS to listOf("média de", "mediana de", "estatística de", "estatísticas de", "desvio padrão de", "variância de", "moda de"),
                Command.REGRESSION to listOf("regressão linear de", "regressão linear", "reta de regressão de", "reta de regressão", "regressão"),
                Command.SEQUENCE to listOf("próximos termos de", "próximo termo de", "sequência", "termo geral de"),
                Command.TRUTH_TABLE to listOf("tabela verdade de", "tabela-verdade de", "tabela verdade", "tabela-verdade", "tautologia"),
                Command.TRIANGLE to listOf("resolva o triângulo", "triângulo com", "triângulo"),
                Command.CONIC to listOf("cônica", "elipse", "hipérbole", "parábola", "focos de", "excentricidade de", "foco de", "vértice de", "diretriz de"),
                Command.LINE to listOf("reta que passa por", "reta pelos pontos", "reta por", "equação da reta que passa por"),
                Command.PLANE to listOf("plano que passa por", "plano pelos pontos", "plano por", "equação do plano que passa por"),
                Command.DIOPHANTINE to listOf("soluções inteiras de", "soluções inteiras", "equação diofantina", "resolva nos inteiros"),
            ),
            of = setOf("de", "da", "do"),
            from = words("de"),
            to = words("a", "até", "ate"),
            forWords = words("para"),
            respect = words("em relação a"),
            approach = words("quando", "em torno de", "centrada em", "no ponto"),
            articles = setOf("o", "a", "os", "as"),
            nouns = words("equação", "função", "expressão", "polinômio", "matriz"),
            arrows = listOf("tende a", "tende para"),
            between = listOf("entre" to "e"),
            constraints = listOf("sujeito a", "com a restrição", "sujeita a", "com a condição", "sob a restrição"),
        )

        val TURKISH = Vocabulary(
            "tr",
            mapOf(
                Command.SOLVE to listOf("çöz", "coz", "çözün", "çözünüz"),
                Command.FACTOR to listOf("çarpanlarına ayırın", "çarpanlarına ayır", "çarpanlara ayır", "carpanlarina ayir"),
                Command.EXPAND to listOf("parantezleri açın", "parantezleri aç", "genişletin", "genişlet", "aç", "açınız"),
                Command.SIMPLIFY to listOf("sadeleştirin", "sadeleştir", "sadelestir", "basitleştir"),
                Command.DERIVATIVE to listOf("türevini al", "türevini", "türevi", "türev", "turev"),
                Command.INTEGRAL to listOf("integralini al", "integralini", "integrali", "integral"),
                Command.LIMIT to listOf("limitini", "limiti", "limit"),
                Command.SUM to listOf("toplamı", "toplam"),
                Command.PLOT to listOf("grafiğini çiz", "grafiğini", "çizin", "çiz", "ciz", "grafiği", "grafik"),
                Command.CALCULATE to listOf("hesaplayın", "hesapla", "bulun", "bul", "kaç eder", "kac eder"),
                Command.PARTIAL_FRACTIONS to listOf("basit kesirlere ayır", "basit kesirler", "basit kesirlerine ayırınız", "basit kesirlerine ayırın", "basit kesirlerine ayır", "basit kesirlere ayırınız", "basit kesirlere ayırın", "basit kesirlere ayir", "basit kesirlerine ayir"),
                Command.SERIES to listOf("taylor serisine açınız", "taylor serisine açın", "taylor serisine aç", "taylor serisi", "taylor açılımı", "maclaurin serisi", "taylor polinomu", "taylor serisini bulun", "taylor serisini bul", "maclaurin serisine açınız", "maclaurin serisine açın", "maclaurin serisine aç", "taylor serisini bulunuz", "maclaurin serisini bulunuz", "taylor polinomunu bulunuz", "taylor açılımını bulunuz", "kuvvet serisi", "laurent serisi"),
                Command.DETERMINANT to listOf("determinantını bulun", "determinantını bul", "determinantı", "determinant", "determinantını"),
                Command.INVERSE to listOf("tersini bulun", "tersini bul", "ters matrisi", "ters matris", "tersi", "tersini", "ters matrisini"),
                Command.EIGEN to listOf("özdeğerlerini bulun", "özdeğerlerini bul", "özdeğerleri", "özvektörleri", "ozdegerleri", "özdeğerlerini", "özvektörlerini"),
                Command.ROW_REDUCE to listOf("satır eşelon formu", "eşelon formu", "rankı", "rank", "rankını", "satır eşelon formunu"),
                Command.TRANSPOSE to listOf("transpozunu bulun", "transpozu", "devriği", "transpoz", "transpozunu", "devriğini"),
                Command.DECOMPOSE to listOf("lu ayrışımı", "qr ayrışımı", "cholesky ayrışımı", "lu ayrışımını"),
                Command.MATRIX_EXP to listOf("matris üsteli", "matris üstelini"),
                Command.GRADIENT to listOf("gradyanı", "gradyan", "gradyanını"),
                Command.DIVERGENCE to listOf("diverjansı", "diverjans", "diverjansını"),
                Command.CURL to listOf("rotasyoneli", "rotasyonel", "rotasyonelini"),
                Command.JACOBIAN to listOf("jacobi matrisi", "jakobiyen", "jacobi matrisini"),
                Command.LAPLACIAN to listOf("laplasyeni", "laplasyen", "laplasyenini"),
                Command.LEAST_SQUARES to listOf("en küçük kareler", "en küçük kareler çözümü", "en küçük kareler yöntemi"),
                Command.EXTREMA to listOf("ekstremumları", "kritik noktaları", "kritik noktalarını", "ekstremumlarını"),
                Command.LAPLACE to listOf("laplace dönüşümünü bulun", "laplace dönüşümü", "laplace dönüşümünü"),
                Command.INVERSE_LAPLACE to listOf("ters laplace dönüşümü", "ters laplace", "ters laplace dönüşümünü"),
                Command.FOURIER to listOf("fourier serisini bulun", "fourier serisi", "fourier serisini"),
                Command.NEWTON to listOf("newton yöntemi ile", "newton yöntemi", "newton-raphson yöntemi"),
                Command.BISECTION to listOf("ikiye bölme yöntemi", "bisection yöntemi", "ikiye bölme"),
                Command.EULER to listOf("euler yöntemi"),
                Command.RUNGE_KUTTA to listOf("runge-kutta yöntemi", "runge-kutta"),
                Command.TRAPEZOID to listOf("yamuk kuralı", "yamuk yöntemi"),
                Command.SIMPSON to listOf("simpson kuralı", "simpson yöntemi"),
                Command.POLAR to listOf("kutupsal biçimi", "kutupsal form", "üstel biçimi", "kutupsal biçim", "kutupsal biçimini"),
                Command.RESIDUES to listOf("rezidüleri", "kalıntıları", "kutupları", "rezidüler", "rezidü"),
                Command.NUMBER_FACTS to listOf("asal mı", "bölenleri", "sayının özellikleri", "asal sayı mıdır", "asal sayı mı", "asal mıdır"),
                Command.STATISTICS to listOf("ortalaması", "ortalama", "medyanı", "medyan", "istatistikleri", "standart sapması", "standart sapma", "varyansı", "modu", "tepe değeri"),
                Command.REGRESSION to listOf("doğrusal regresyon", "regresyon doğrusu", "regresyon"),
                Command.SEQUENCE to listOf("sonraki terimler", "sonraki terim", "dizinin genel terimi", "dizi", "dizisi"),
                Command.TRUTH_TABLE to listOf("doğruluk tablosu", "totoloji"),
                Command.TRIANGLE to listOf("üçgeni çöz", "üçgen"),
                Command.CONIC to listOf("konik", "elips", "hiperbol", "parabol", "odakları", "dışmerkezlik", "odağı"),
                Command.LINE to listOf("noktalarından geçen doğru", "noktasından geçen doğru", "geçen doğru"),
                Command.PLANE to listOf("noktalarından geçen düzlem", "geçen düzlem"),
                Command.DIOPHANTINE to listOf("tam sayı çözümleri", "diofant denklemi", "tam sayılarda çöz"),
            ),
            of = emptySet(),
            from = words("'dan", "'den", "'tan", "'ten"),
            to = words("'a", "'e", "'ya", "'ye"),
            forWords = words("için"),
            respect = words("'a göre", "'e göre", "'ya göre", "'ye göre"),
            approach = words("iken"),
            postpositional = true,
            commandLast = true,
            particles = setOf("'i", "'ı", "'u", "'ü", "'yi", "'yı", "'yu", "'yü", "'in", "'ın", "'un", "'ün", "'nin", "'nın", "'nun", "'nün"),
            ignored = setOf("kadar"),
            nouns = words("denklemini", "denklemi", "fonksiyonunun", "ifadesini", "matrisinin", "matrisin", "sayısının", "sayılarının", "denkleminin", "parabolünün", "elipsinin", "hiperbolünün"),
            constraints = listOf("kısıtı altında", "koşulu altında", "olmak üzere", "koşuluyla", "şartıyla"),
        )

        val VIETNAMESE = Vocabulary(
            "vi",
            mapOf(
                Command.SOLVE to listOf("giải phương trình", "giải", "giai"),
                Command.FACTOR to listOf("phân tích đa thức thành nhân tử", "phân tích thành nhân tử", "phân tích đa thức", "phân tích", "phan tich"),
                Command.EXPAND to listOf("khai triển", "khai trien"),
                Command.SIMPLIFY to listOf("rút gọn", "rut gon", "đơn giản"),
                Command.DERIVATIVE to listOf("đạo hàm của", "đạo hàm", "dao ham"),
                Command.INTEGRAL to listOf("tích phân của", "tích phân", "nguyên hàm của", "nguyên hàm", "tich phan", "nguyen ham"),
                Command.LIMIT to listOf("giới hạn của", "giới hạn", "gioi han"),
                Command.SUM to listOf("tổng của", "tổng", "tong"),
                Command.PLOT to listOf("vẽ đồ thị", "vẽ", "đồ thị của", "đồ thị", "ve do thi"),
                Command.CALCULATE to listOf("tính giá trị", "tính", "tinh", "tìm"),
                Command.PARTIAL_FRACTIONS to listOf("phân tích thành phân thức đơn giản", "phân thức đơn giản", "phân tích thành tổng các phân thức đơn giản", "phân tích thành các phân thức đơn giản"),
                Command.SERIES to listOf("chuỗi taylor của", "chuỗi taylor", "khai triển taylor của", "khai triển taylor", "chuỗi maclaurin của", "chuỗi maclaurin", "đa thức taylor của", "khai triển thành chuỗi taylor", "khai triển maclaurin của", "khai triển maclaurin", "khai triển thành chuỗi maclaurin", "khai triển thành chuỗi lũy thừa", "khai trien taylor", "chuoi taylor", "đa thức taylor", "chuỗi lũy thừa của"),
                Command.DETERMINANT to listOf("định thức của", "định thức"),
                Command.INVERSE to listOf("ma trận nghịch đảo của", "nghịch đảo của", "nghịch đảo"),
                Command.EIGEN to listOf("giá trị riêng của", "giá trị riêng", "vectơ riêng của", "vectơ riêng", "giá trị riêng và vectơ riêng của", "véctơ riêng", "vector riêng"),
                Command.ROW_REDUCE to listOf("dạng bậc thang rút gọn của", "dạng bậc thang của", "dạng bậc thang", "hạng của", "hạng", "đưa về dạng bậc thang", "dạng bậc thang rút gọn"),
                Command.TRANSPOSE to listOf("ma trận chuyển vị của", "chuyển vị của", "chuyển vị"),
                Command.DECOMPOSE to listOf("phân tích lu của", "phân tích lu", "phân tích qr của", "phân tích qr", "phân tích cholesky của", "phân tích cholesky", "phân tích ma trận"),
                Command.MATRIX_EXP to listOf("hàm mũ ma trận của", "hàm mũ ma trận"),
                Command.GRADIENT to listOf("gradient của", "građiên của"),
                Command.DIVERGENCE to listOf("divergence của", "dive của", "độ phân kỳ"),
                Command.CURL to listOf("rot của", "curl của", "độ xoáy", "rota của"),
                Command.JACOBIAN to listOf("ma trận jacobi của", "jacobian của", "ma trận jacobi"),
                Command.LAPLACIAN to listOf("toán tử laplace của", "laplace của", "toán tử laplace"),
                Command.LEAST_SQUARES to listOf("bình phương tối thiểu"),
                Command.EXTREMA to listOf("cực trị của", "điểm tới hạn của", "cực trị có điều kiện của", "cực trị"),
                Command.LAPLACE to listOf("biến đổi laplace của", "biến đổi laplace"),
                Command.INVERSE_LAPLACE to listOf("biến đổi laplace ngược của", "biến đổi laplace ngược"),
                Command.FOURIER to listOf("chuỗi fourier của", "chuỗi fourier", "khai triển fourier của", "khai triển fourier"),
                Command.NEWTON to listOf("phương pháp newton cho", "phương pháp newton", "phương pháp tiếp tuyến", "phương pháp newton-raphson"),
                Command.BISECTION to listOf("phương pháp chia đôi cho", "phương pháp chia đôi", "chia đôi"),
                Command.EULER to listOf("phương pháp euler cho", "phương pháp euler"),
                Command.RUNGE_KUTTA to listOf("phương pháp runge-kutta cho", "phương pháp runge-kutta", "runge-kutta"),
                Command.TRAPEZOID to listOf("công thức hình thang cho", "công thức hình thang", "phương pháp hình thang"),
                Command.SIMPSON to listOf("công thức simpson cho", "công thức simpson", "phương pháp simpson"),
                Command.POLAR to listOf("dạng lượng giác của", "dạng cực của", "dạng mũ của", "dạng lượng giác"),
                Command.RESIDUES to listOf("thặng dư của", "thặng dư", "cực điểm của"),
                Command.NUMBER_FACTS to listOf("có phải số nguyên tố", "ước của", "tính chất của số", "là số nguyên tố", "có phải là số nguyên tố không", "có phải số nguyên tố không"),
                Command.STATISTICS to listOf("trung bình cộng của", "trung bình của", "trung vị của", "thống kê của", "độ lệch chuẩn của", "phương sai của", "mốt của"),
                Command.REGRESSION to listOf("hồi quy tuyến tính của", "hồi quy tuyến tính", "đường hồi quy", "hồi quy"),
                Command.SEQUENCE to listOf("các số hạng tiếp theo của", "số hạng tiếp theo của", "số hạng tổng quát của", "dãy số"),
                Command.TRUTH_TABLE to listOf("bảng chân trị của", "bảng chân trị", "bảng chân lý", "hằng đúng"),
                Command.TRIANGLE to listOf("giải tam giác", "tam giác có", "tam giác"),
                Command.CONIC to listOf("đường conic", "elip", "hypebol", "parabol", "tiêu điểm của", "tâm sai của", "đỉnh của"),
                Command.LINE to listOf("đường thẳng đi qua", "đường thẳng qua", "phương trình đường thẳng đi qua"),
                Command.PLANE to listOf("mặt phẳng đi qua", "mặt phẳng qua", "phương trình mặt phẳng đi qua"),
                Command.DIOPHANTINE to listOf("nghiệm nguyên của", "nghiệm nguyên", "phương trình diophantine"),
            ),
            of = setOf("của"),
            from = words("từ"),
            to = words("đến", "tới"),
            forWords = words("với"),
            respect = words("theo", "đối với"),
            approach = words("khi", "tại điểm", "quanh"),
            nouns = words("phương trình", "hàm số", "đa thức", "biểu thức", "ma trận"),
            arrows = listOf("tiến tới", "tiến đến", "dần tới"),
            constraints = listOf("với điều kiện", "với ràng buộc"),
        )

        val RUSSIAN = Vocabulary(
            "ru",
            mapOf(
                Command.SOLVE to listOf("реши уравнение", "решите уравнение", "реши", "решить", "решите"),
                Command.FACTOR to listOf("разложи на множители", "разложите на множители", "разложить на множители", "разложи", "разложите", "разложить"),
                Command.EXPAND to listOf("раскрой скобки", "раскройте скобки", "раскрыть скобки", "раскрой", "раскройте"),
                Command.SIMPLIFY to listOf("упрости выражение", "упростите выражение", "упрости", "упростить", "упростите"),
                Command.DERIVATIVE to listOf("найди производную", "найдите производную", "вычисли производную", "вычислите производную", "производная от", "производная", "продифференцируй"),
                Command.INTEGRAL to listOf("найди интеграл", "найдите интеграл", "вычисли интеграл", "вычислите интеграл", "интеграл от", "интеграл", "проинтегрируй", "первообразная от", "первообразная"),
                Command.LIMIT to listOf("найди предел", "найдите предел", "вычисли предел", "вычислите предел", "предел"),
                Command.SUM to listOf("сумма"),
                Command.PLOT to listOf("построй график", "постройте график", "построй", "постройте", "нарисуй", "график"),
                Command.CALCULATE to listOf("вычисли", "вычислите", "вычислить", "посчитай", "найди значение", "найдите значение", "сколько будет", "найдите", "найди"),
                Command.PARTIAL_FRACTIONS to listOf("разложи на простейшие дроби", "разложите на простейшие дроби", "простейшие дроби", "разложить на простейшие дроби", "разложение на простейшие дроби"),
                Command.SERIES to listOf("разложи в ряд тейлора", "разложите в ряд тейлора", "разложить в ряд тейлора", "разложение в ряд тейлора", "ряд тейлора", "ряд маклорена", "многочлен тейлора", "разложи в ряд маклорена", "разложите в ряд маклорена", "разложить в ряд маклорена", "разложи в ряд", "разложите в ряд", "разложить в ряд", "разложение в ряд маклорена", "формула тейлора", "степенной ряд", "найдите ряд тейлора", "найди ряд тейлора", "ряд лорана"),
                Command.DETERMINANT to listOf("найдите определитель", "найди определитель", "определитель", "детерминант"),
                Command.INVERSE to listOf("найдите обратную матрицу", "найди обратную матрицу", "обратная матрица", "обратную матрицу"),
                Command.EIGEN to listOf("найдите собственные значения", "собственные значения", "собственные векторы", "собственные значения и собственные векторы", "собственные числа"),
                Command.ROW_REDUCE to listOf("приведите к ступенчатому виду", "ступенчатый вид", "ранг матрицы", "ранг"),
                Command.TRANSPOSE to listOf("транспонированная матрица", "транспонируйте", "транспонировать", "транспонированную матрицу", "транспонирование"),
                Command.DECOMPOSE to listOf("lu-разложение", "lu разложение", "qr-разложение", "разложение холецкого"),
                Command.MATRIX_EXP to listOf("экспонента матрицы", "матричная экспонента", "экспоненту матрицы", "матричную экспоненту"),
                Command.GRADIENT to listOf("градиент функции", "градиент"),
                Command.DIVERGENCE to listOf("дивергенция поля", "дивергенция", "дивергенцию"),
                Command.CURL to listOf("ротор поля", "ротор"),
                Command.JACOBIAN to listOf("матрица якоби", "якобиан", "матрицу якоби"),
                Command.LAPLACIAN to listOf("оператор лапласа от", "лапласиан", "оператор лапласа"),
                Command.LEAST_SQUARES to listOf("метод наименьших квадратов", "наименьшие квадраты", "методом наименьших квадратов"),
                Command.EXTREMA to listOf("найдите экстремумы", "экстремумы", "критические точки", "условный экстремум"),
                Command.LAPLACE to listOf("преобразование лапласа", "изображение по лапласу"),
                Command.INVERSE_LAPLACE to listOf("обратное преобразование лапласа", "оригинал"),
                Command.FOURIER to listOf("разложите в ряд фурье", "разложи в ряд фурье", "ряд фурье", "разложить в ряд фурье"),
                Command.NEWTON to listOf("метод ньютона", "метод касательных", "метод ньютона для", "метод ньютона-рафсона", "метод касательных для"),
                Command.BISECTION to listOf("метод половинного деления", "метод деления пополам", "метод бисекции", "метод деления отрезка пополам", "метод дихотомии", "деление пополам", "метод половинного деления для", "метод деления пополам для", "метод бисекции для"),
                Command.EULER to listOf("метод эйлера", "метод эйлера для"),
                Command.RUNGE_KUTTA to listOf("метод рунге-кутты", "рунге-кутта", "метод рунге-кутты для"),
                Command.TRAPEZOID to listOf("метод трапеций", "формула трапеций", "метод трапеций для", "формула трапеций для"),
                Command.SIMPSON to listOf("метод симпсона", "формула симпсона", "метод парабол", "метод симпсона для", "формула симпсона для"),
                Command.POLAR to listOf("тригонометрическая форма", "показательная форма", "полярная форма"),
                Command.RESIDUES to listOf("вычеты", "вычет", "полюсы"),
                Command.NUMBER_FACTS to listOf("простое ли число", "является ли простым", "делители числа", "делители", "свойства числа", "является ли простым числом", "простое число", "простое"),
                Command.STATISTICS to listOf("среднее значение", "среднее арифметическое", "среднее", "медиана", "статистика", "стандартное отклонение", "дисперсия", "мода"),
                Command.REGRESSION to listOf("линейная регрессия", "уравнение регрессии", "прямая регрессии", "регрессия"),
                Command.SEQUENCE to listOf("следующие члены последовательности", "следующие члены", "следующий член", "формула общего члена", "последовательность"),
                Command.TRUTH_TABLE to listOf("таблица истинности", "тавтология"),
                Command.TRIANGLE to listOf("решите треугольник", "реши треугольник", "треугольник"),
                Command.CONIC to listOf("кривая второго порядка", "эллипс", "гипербола", "парабола", "фокусы", "эксцентриситет", "фокус", "вершина", "директриса"),
                Command.LINE to listOf("прямая через точки", "прямая, проходящая через", "прямая через", "уравнение прямой, проходящей через", "уравнение прямой через"),
                Command.PLANE to listOf("плоскость через точки", "плоскость, проходящая через", "плоскость через", "уравнение плоскости, проходящей через", "уравнение плоскости через"),
                Command.DIOPHANTINE to listOf("решите в целых числах", "реши в целых числах", "целые решения", "диофантово уравнение", "решить в целых числах", "решите уравнение в целых числах", "решить уравнение в целых числах"),
            ),
            of = setOf("от"),
            from = words("от"),
            to = words("до"),
            forWords = words("для"),
            respect = words("по"),
            approach = words("при", "в окрестности точки", "в окрестности", "в точке"),
            nouns = words("уравнение", "функции", "выражение", "функцию", "функция", "матрицы", "матрицу", "матрица", "числа", "чисел", "эллипса", "параболы", "гиперболы"),
            arrows = listOf("стремится к"),
            constraints = listOf("при условии", "при ограничении", "при условии связи"),
        )

        val UKRAINIAN = Vocabulary(
            "uk",
            mapOf(
                Command.SOLVE to listOf("розв'яжи рівняння", "розв'яжіть рівняння", "розв'яжи", "розв'яжіть", "розв'язати"),
                Command.FACTOR to listOf("розклади на множники", "розкладіть на множники", "розкласти на множники", "розклади", "розкладіть"),
                Command.EXPAND to listOf("розкрий дужки", "розкрийте дужки", "розкрий", "розкрийте"),
                Command.SIMPLIFY to listOf("спрости вираз", "спростіть вираз", "спрости", "спростіть", "спростити"),
                Command.DERIVATIVE to listOf("знайди похідну", "знайдіть похідну", "обчисли похідну", "обчисліть похідну", "похідна від", "похідна", "продиференціюй"),
                Command.INTEGRAL to listOf("знайди інтеграл", "знайдіть інтеграл", "обчисли інтеграл", "обчисліть інтеграл", "інтеграл від", "інтеграл", "проінтегруй", "первісна від", "первісна"),
                Command.LIMIT to listOf("знайди границю", "знайдіть границю", "обчисли границю", "обчисліть границю", "границя", "межа"),
                Command.SUM to listOf("сума"),
                Command.PLOT to listOf("побудуй графік", "побудуйте графік", "побудуй", "побудуйте", "намалюй", "графік"),
                Command.CALCULATE to listOf("обчисли", "обчисліть", "порахуй", "скільки буде", "знайдіть", "знайди"),
                Command.PARTIAL_FRACTIONS to listOf("розклади на найпростіші дроби", "розкладіть на найпростіші дроби", "найпростіші дроби", "розкласти на найпростіші дроби", "розклад на найпростіші дроби"),
                Command.SERIES to listOf("розклади в ряд тейлора", "розкладіть в ряд тейлора", "розкласти в ряд тейлора", "розклад у ряд тейлора", "ряд тейлора", "ряд маклорена", "многочлен тейлора", "розкладіть у ряд тейлора", "розклади у ряд тейлора", "розкласти у ряд тейлора", "розкладіть в ряд маклорена", "розклади в ряд маклорена", "розкласти в ряд маклорена", "розклад в ряд тейлора", "формула тейлора", "степеневий ряд", "знайдіть ряд тейлора"),
                Command.DETERMINANT to listOf("знайдіть визначник", "визначник"),
                Command.INVERSE to listOf("знайдіть обернену матрицю", "обернена матриця", "обернену матрицю"),
                Command.EIGEN to listOf("знайдіть власні значення", "власні значення", "власні вектори", "власні числа"),
                Command.ROW_REDUCE to listOf("зведіть до східчастого вигляду", "східчастий вигляд", "ранг матриці", "ранг", "ступінчастий вигляд"),
                Command.TRANSPOSE to listOf("транспонована матриця", "транспонуйте", "транспоновану матрицю", "транспонування"),
                Command.DECOMPOSE to listOf("lu-розклад", "qr-розклад", "розклад холецького"),
                Command.MATRIX_EXP to listOf("експонента матриці", "матрична експонента", "експоненту матриці"),
                Command.GRADIENT to listOf("градієнт функції", "градієнт"),
                Command.DIVERGENCE to listOf("дивергенція", "дивергенцію", "дивергенція поля"),
                Command.CURL to listOf("ротор", "ротор поля"),
                Command.JACOBIAN to listOf("матриця якобі", "якобіан", "матрицю якобі"),
                Command.LAPLACIAN to listOf("лапласіан", "оператор лапласа"),
                Command.LEAST_SQUARES to listOf("метод найменших квадратів", "найменші квадрати"),
                Command.EXTREMA to listOf("знайдіть екстремуми", "екстремуми", "критичні точки", "умовний екстремум"),
                Command.LAPLACE to listOf("перетворення лапласа"),
                Command.INVERSE_LAPLACE to listOf("обернене перетворення лапласа", "оригінал"),
                Command.FOURIER to listOf("розкладіть в ряд фур'є", "ряд фур'є", "ряд фурье", "розкладіть у ряд фур'є", "розкласти в ряд фур'є", "розкласти у ряд фур'є"),
                Command.NEWTON to listOf("метод ньютона", "метод дотичних", "метод ньютона для", "метод ньютона-рафсона", "метод дотичних для"),
                Command.BISECTION to listOf("метод поділу навпіл", "метод бісекції", "метод ділення навпіл", "поділ навпіл", "метод поділу навпіл для", "метод бісекції для", "метод ділення навпіл для"),
                Command.EULER to listOf("метод ейлера", "метод ейлера для"),
                Command.RUNGE_KUTTA to listOf("метод рунге-кутти", "рунге-кутта", "метод рунге-кутти для"),
                Command.TRAPEZOID to listOf("метод трапецій", "формула трапецій", "метод трапецій для", "формула трапецій для"),
                Command.SIMPSON to listOf("метод сімпсона", "формула сімпсона", "метод парабол", "метод сімпсона для", "формула сімпсона для"),
                Command.POLAR to listOf("тригонометрична форма", "показникова форма", "полярна форма"),
                Command.RESIDUES to listOf("лишки", "лишок", "полюси"),
                Command.NUMBER_FACTS to listOf("чи є простим", "дільники числа", "дільники", "властивості числа", "чи є простим числом", "просте число", "просте"),
                Command.STATISTICS to listOf("середнє значення", "середнє арифметичне", "середнє", "медіана", "статистика", "стандартне відхилення", "дисперсія", "мода"),
                Command.REGRESSION to listOf("лінійна регресія", "рівняння регресії", "пряма регресії", "регресія"),
                Command.SEQUENCE to listOf("наступні члени послідовності", "наступні члени", "наступний член", "формула загального члена", "послідовність"),
                Command.TRUTH_TABLE to listOf("таблиця істинності", "тавтологія"),
                Command.TRIANGLE to listOf("розв'яжіть трикутник", "розв'яжи трикутник", "трикутник"),
                Command.CONIC to listOf("крива другого порядку", "еліпс", "гіпербола", "парабола", "фокуси", "ексцентриситет", "фокус"),
                Command.LINE to listOf("пряма через точки", "пряма, що проходить через", "пряма через", "рівняння прямої, що проходить через", "рівняння прямої через"),
                Command.PLANE to listOf("площина через точки", "площина, що проходить через", "площина через", "рівняння площини, що проходить через"),
                Command.DIOPHANTINE to listOf("розв'яжіть у цілих числах", "цілі розв'язки", "діофантове рівняння", "розв'яжи у цілих числах", "розв'яжіть в цілих числах", "розв'язати в цілих числах", "розв'язати у цілих числах"),
            ),
            of = setOf("від"),
            from = words("від"),
            to = words("до"),
            forWords = words("для"),
            respect = words("по", "за"),
            approach = words("при", "в околі точки", "в точці", "у точці"),
            nouns = words("рівняння", "функції", "вираз", "функцію", "функція", "матриці", "матрицю", "числа", "чисел", "еліпса", "параболи", "гіперболи"),
            arrows = listOf("прямує до"),
            constraints = listOf("за умови", "при умові", "з обмеженням"),
        )

        val ARABIC = Vocabulary(
            "ar",
            mapOf(
                Command.SOLVE to listOf("حل المعادلة", "حل", "أوجد حل", "اوجد حل"),
                Command.FACTOR to listOf("حلل إلى العوامل", "حلل الى العوامل", "حلل إلى عوامل", "حلل الى عوامل", "حلل", "فكك"),
                Command.EXPAND to listOf("فك الأقواس", "فك الاقواس", "انشر", "وسع"),
                Command.SIMPLIFY to listOf("بسط", "اختصر"),
                Command.DERIVATIVE to listOf("أوجد المشتقة", "اوجد المشتقة", "أوجد مشتقة", "اوجد مشتقة", "احسب المشتقة", "احسب مشتقة", "مشتقة", "المشتقة", "اشتق"),
                Command.INTEGRAL to listOf("احسب التكامل", "احسب تكامل", "أوجد التكامل", "اوجد التكامل", "تكامل", "التكامل", "كامل", "الدالة الأصلية"),
                Command.LIMIT to listOf("احسب النهاية", "احسب نهاية", "أوجد النهاية", "اوجد النهاية", "نهاية", "النهاية"),
                Command.SUM to listOf("مجموع", "المجموع"),
                Command.PLOT to listOf("ارسم", "مثل بيانيا", "التمثيل البياني"),
                Command.CALCULATE to listOf("احسب", "كم يساوي", "أوجد", "اوجد"),
                Command.PARTIAL_FRACTIONS to listOf("الكسور الجزئية", "كسور جزئية", "حلل إلى كسور جزئية", "حلل الى كسور جزئية", "حللوا إلى كسور جزئية", "حللوا الى كسور جزئية"),
                Command.SERIES to listOf("متسلسلة تايلور", "متسلسلة ماكلورين", "مفكوك تايلور", "سلسلة تايلور", "منشور تايلور", "متسلسلة تايلور للدالة", "مفكوك تايلور للدالة", "أوجد متسلسلة تايلور", "اوجد متسلسلة تايلور", "مفكوك ماكلورين", "متسلسلة القوى"),
                Command.DETERMINANT to listOf("محدد المصفوفة", "المحدد", "محدد"),
                Command.INVERSE to listOf("معكوس المصفوفة", "المعكوس", "معكوس", "المصفوفة العكسية"),
                Command.EIGEN to listOf("القيم الذاتية للمصفوفة", "القيم الذاتية", "المتجهات الذاتية", "القيم المميزة"),
                Command.ROW_REDUCE to listOf("الصيغة الدرجية", "رتبة المصفوفة", "الرتبة", "الصيغة الدرجية المختزلة", "الشكل الدرجي"),
                Command.TRANSPOSE to listOf("منقول المصفوفة", "المنقول", "منقول"),
                Command.DECOMPOSE to listOf("تحليل lu", "تحليل qr", "تحليل تشولسكي"),
                Command.MATRIX_EXP to listOf("أسية المصفوفة"),
                Command.GRADIENT to listOf("التدرج", "تدرج"),
                Command.DIVERGENCE to listOf("التباعد", "تباعد"),
                Command.CURL to listOf("الدوران", "دوران"),
                Command.JACOBIAN to listOf("مصفوفة جاكوبي", "جاكوبيان"),
                Command.LAPLACIAN to listOf("مؤثر لابلاس", "لابلاسيان"),
                Command.LEAST_SQUARES to listOf("طريقة المربعات الصغرى", "المربعات الصغرى"),
                Command.EXTREMA to listOf("القيم القصوى", "النقاط الحرجة", "القيم العظمى والصغرى"),
                Command.LAPLACE to listOf("تحويل لابلاس"),
                Command.INVERSE_LAPLACE to listOf("تحويل لابلاس العكسي", "معكوس تحويل لابلاس", "تحويل لابلاس المعكوس"),
                Command.FOURIER to listOf("متسلسلة فورييه", "متسلسلة فوريير", "سلسلة فورييه"),
                Command.NEWTON to listOf("طريقة نيوتن"),
                Command.BISECTION to listOf("طريقة التنصيف", "التنصيف"),
                Command.EULER to listOf("طريقة أويلر", "طريقة اويلر"),
                Command.RUNGE_KUTTA to listOf("طريقة رونج كوتا", "رونج-كوتا", "طريقة رونج-كوتا"),
                Command.TRAPEZOID to listOf("قاعدة شبه المنحرف", "طريقة شبه المنحرف"),
                Command.SIMPSON to listOf("قاعدة سمبسون", "طريقة سمبسون", "قاعدة سيمبسون"),
                Command.POLAR to listOf("الصيغة القطبية", "الصورة القطبية", "الصيغة الأسية", "الشكل القطبي", "الصيغة المثلثية"),
                Command.RESIDUES to listOf("الرواسب", "البواقي", "الأقطاب"),
                Command.NUMBER_FACTS to listOf("هل هو أولي", "قواسم", "خصائص العدد", "هل عدد أولي", "هل أولي"),
                Command.STATISTICS to listOf("الوسط الحسابي", "المتوسط", "الوسيط", "الإحصاءات", "الانحراف المعياري", "التباين", "المنوال"),
                Command.REGRESSION to listOf("الانحدار الخطي", "خط الانحدار", "الانحدار"),
                Command.SEQUENCE to listOf("الحدود التالية", "الحد التالي", "الحد العام", "المتتالية"),
                Command.TRUTH_TABLE to listOf("جدول الصواب", "جدول الحقيقة", "تحصيل حاصل"),
                Command.TRIANGLE to listOf("حل المثلث", "مثلث"),
                Command.CONIC to listOf("قطع مخروطي", "القطع الناقص", "القطع الزائد", "القطع المكافئ", "البؤرتان", "الاختلاف المركزي", "البؤرة", "بؤرة"),
                Command.LINE to listOf("المستقيم المار بالنقطتين", "المستقيم المار ب", "مستقيم يمر ب", "معادلة المستقيم المار بالنقطتين", "معادلة المستقيم المار ب"),
                Command.PLANE to listOf("المستوى المار بالنقاط", "المستوى المار ب"),
                Command.DIOPHANTINE to listOf("الحلول الصحيحة", "معادلة ديوفانتية"),
            ),
            of = emptySet(),
            from = words("من"),
            to = words("إلى", "الى"),
            forWords = words("ل"),
            respect = words("بالنسبة إلى", "بالنسبة الى", "بالنسبة ل"),
            approach = words("عندما"),
            nouns = words("للدالة", "الدالة", "المصفوفة", "العدد", "للأعداد", "الأعداد", "القطع المكافئ", "القطع الناقص", "القطع الزائد"),
            constraints = listOf("بشرط", "تحت القيد", "مع القيد"),
        )

        val HEBREW = Vocabulary(
            "he",
            mapOf(
                Command.SOLVE to listOf("פתור את המשוואה", "פתרו את המשוואה", "פתרי את המשוואה", "פתור", "פתרי", "פתרו"),
                Command.FACTOR to listOf("פרק לגורמים", "פרקי לגורמים", "פרקו לגורמים", "פרק"),
                Command.EXPAND to listOf("פתח סוגריים", "פתחו סוגריים", "פתחי סוגריים", "פתח", "פתחו", "פתחי"),
                Command.SIMPLIFY to listOf("פשט", "פשטי", "פשטו"),
                Command.DERIVATIVE to listOf("הנגזרת של", "נגזרת של", "נגזרת", "גזור", "גזרו", "גזרי"),
                Command.INTEGRAL to listOf("האינטגרל של", "אינטגרל של", "אינטגרל", "פונקציה קדומה של", "פונקציה קדומה"),
                Command.LIMIT to listOf("הגבול של", "גבול של", "גבול"),
                Command.SUM to listOf("הסכום של", "סכום של", "סכום"),
                Command.PLOT to listOf("שרטט", "שרטטו", "שרטטי", "צייר", "ציירו", "ציירי", "גרף של", "גרף"),
                Command.CALCULATE to listOf("חשב", "חשבי", "חשבו", "כמה זה", "מצא", "מצאו", "מצאי"),
                Command.PARTIAL_FRACTIONS to listOf("פירוק לשברים חלקיים", "שברים חלקיים", "פרק לשברים חלקיים", "פרקו לשברים חלקיים", "פרקי לשברים חלקיים"),
                Command.SERIES to listOf("טור טיילור של", "טור טיילור", "פולינום טיילור של", "פולינום טיילור", "טור מקלורן של", "טור מקלורן", "פתח לטור טיילור", "פתחו לטור טיילור", "פתחי לטור טיילור", "פיתוח טיילור של", "טור חזקות של", "טור מקלורין", "מצאו את טור טיילור", "טור לורן"),
                Command.DETERMINANT to listOf("דטרמיננטה של", "דטרמיננטה", "הדטרמיננטה של"),
                Command.INVERSE to listOf("מטריצה הופכית של", "ההופכית של", "הופכית"),
                Command.EIGEN to listOf("ערכים עצמיים של", "ערכים עצמיים", "וקטורים עצמיים של", "וקטורים עצמיים", "ערכים עצמיים ווקטורים עצמיים של", "הערכים העצמיים של", "הווקטורים העצמיים של"),
                Command.ROW_REDUCE to listOf("צורה מדורגת של", "צורה מדורגת", "דרגה של", "דרגה", "הצורה המדורגת של", "הדרגה של", "דרגת", "דרג", "דרגו", "דירוג"),
                Command.TRANSPOSE to listOf("מטריצה משוחלפת של", "שחלוף של", "משוחלפת", "המשוחלפת של"),
                Command.DECOMPOSE to listOf("פירוק lu של", "פירוק lu", "פירוק qr של", "פירוק qr", "פירוק כולסקי של"),
                Command.MATRIX_EXP to listOf("אקספוננט של מטריצה"),
                Command.GRADIENT to listOf("גרדיאנט של", "גרדיאנט", "הגרדיאנט של"),
                Command.DIVERGENCE to listOf("דיברגנץ של", "דיברגנץ"),
                Command.CURL to listOf("רוטור של", "רוטור", "הרוטור של"),
                Command.JACOBIAN to listOf("מטריצת יעקובי של", "יעקוביאן של", "מטריצת יעקובי"),
                Command.LAPLACIAN to listOf("לפלסיאן של", "לפלסיאן"),
                Command.LEAST_SQUARES to listOf("שיטת הריבועים הפחותים", "ריבועים פחותים"),
                Command.EXTREMA to listOf("נקודות קיצון של", "נקודות קריטיות של", "נקודות הקיצון של"),
                Command.LAPLACE to listOf("התמרת לפלס של", "התמרת לפלס"),
                Command.INVERSE_LAPLACE to listOf("התמרת לפלס הפוכה של", "התמרת לפלס הפוכה", "התמרת לפלס ההפוכה"),
                Command.FOURIER to listOf("טור פורייה של", "טור פורייה", "טור פוריה"),
                Command.NEWTON to listOf("שיטת ניוטון-רפסון", "שיטת ניוטון"),
                Command.BISECTION to listOf("שיטת החצייה", "שיטת הביסקציה", "שיטת החציה"),
                Command.EULER to listOf("שיטת אוילר"),
                Command.RUNGE_KUTTA to listOf("שיטת רונגה-קוטה", "רונגה-קוטה"),
                Command.TRAPEZOID to listOf("כלל הטרפז", "שיטת הטרפזים", "שיטת הטרפז"),
                Command.SIMPSON to listOf("כלל סימפסון", "שיטת סימפסון"),
                Command.POLAR to listOf("הצגה קוטבית של", "הצגה קוטבית", "הצגה מעריכית של", "הצגה טריגונומטרית", "צורה קוטבית"),
                Command.RESIDUES to listOf("שאריות של", "שאריות", "קטבים של"),
                Command.NUMBER_FACTS to listOf("האם ראשוני", "מחלקים של", "תכונות המספר", "האם מספר ראשוני"),
                Command.STATISTICS to listOf("ממוצע של", "ממוצע", "חציון של", "חציון", "סטטיסטיקה של", "סטיית תקן של", "שונות של", "שכיח של", "שכיח"),
                Command.REGRESSION to listOf("רגרסיה לינארית", "קו רגרסיה", "רגרסיה"),
                Command.SEQUENCE to listOf("האיברים הבאים של", "האיבר הבא של", "האיבר הכללי של", "סדרה"),
                Command.TRUTH_TABLE to listOf("טבלת אמת של", "טבלת אמת", "טאוטולוגיה"),
                Command.TRIANGLE to listOf("פתור את המשולש", "משולש עם", "משולש"),
                Command.CONIC to listOf("חתך חרוט", "אליפסה", "היפרבולה", "פרבולה", "מוקדים של", "אקסצנטריות של", "מוקד של", "קודקוד של"),
                Command.LINE to listOf("ישר העובר דרך", "ישר דרך הנקודות", "ישר דרך", "משוואת הישר העובר דרך"),
                Command.PLANE to listOf("מישור העובר דרך", "מישור דרך הנקודות", "מישור דרך", "משוואת המישור העובר דרך"),
                Command.DIOPHANTINE to listOf("פתרונות שלמים של", "פתרונות שלמים", "משוואה דיופנטית"),
            ),
            of = setOf("של"),
            from = words("מ", "החל מ"),
            to = words("עד"),
            forWords = words("עבור"),
            respect = words("לפי", "ביחס ל"),
            approach = words("כאשר", "כש", "סביב", "בנקודה"),
            // The object marker of "פתרו את …".
            ignored = setOf("את"),
            // "מצא את הנגזרת של": the mark of a definite object before the command's noun.
            articles = setOf("את"),
            nouns = words("הפונקציה", "המטריצה"),
            constraints = listOf("בתנאי", "תחת האילוץ", "תחת התנאי"),
        )

        val HINDI = Vocabulary(
            "hi",
            mapOf(
                Command.SOLVE to listOf("हल"),
                Command.FACTOR to listOf("गुणनखंड", "गुणनखण्ड"),
                Command.EXPAND to listOf("विस्तार", "प्रसार"),
                Command.SIMPLIFY to listOf("सरल", "सरलीकरण"),
                Command.DERIVATIVE to listOf("अवकलज", "अवकलन", "व्युत्पन्न"),
                Command.INTEGRAL to listOf("समाकल", "समाकलन", "प्रतिअवकलज"),
                Command.LIMIT to listOf("सीमा"),
                Command.SUM to listOf("योगफल", "योग"),
                Command.PLOT to listOf("ग्राफ़", "ग्राफ", "आलेख"),
                Command.CALCULATE to listOf("गणना", "परिकलन", "कितना है"),
                Command.PARTIAL_FRACTIONS to listOf("आंशिक भिन्न", "आंशिक भिन्नों में", "आंशिक भिन्नों"),
                Command.SERIES to listOf("टेलर श्रेणी", "मैक्लॉरिन श्रेणी", "टेलर प्रसार", "टेलर श्रेणी ज्ञात कीजिए", "टेलर विस्तार", "मैक्लॉरिन विस्तार", "टेलर श्रेणी में प्रसार", "मैकलॉरिन श्रेणी", "टेलर बहुपद"),
                Command.DETERMINANT to listOf("सारणिक ज्ञात कीजिए", "सारणिक"),
                Command.INVERSE to listOf("व्युत्क्रम आव्यूह", "व्युत्क्रम"),
                Command.EIGEN to listOf("अभिलक्षणिक मान", "आइगेन मान", "अभिलक्षणिक सदिश", "अभिलक्षणिक मूल", "आइगेन सदिश"),
                Command.ROW_REDUCE to listOf("पंक्ति सोपानक रूप", "कोटि", "समानीत पंक्ति सोपानक रूप", "सोपानक रूप"),
                Command.TRANSPOSE to listOf("परिवर्त आव्यूह", "परिवर्त"),
                Command.DECOMPOSE to listOf("lu वियोजन", "qr वियोजन", "चोलेस्की वियोजन"),
                Command.MATRIX_EXP to listOf("आव्यूह घातांक", "आव्यूह चरघातांकी फलन"),
                Command.GRADIENT to listOf("प्रवणता", "ग्रेडिएंट"),
                Command.DIVERGENCE to listOf("अपसरण", "डाइवर्जेंस"),
                Command.CURL to listOf("कर्ल"),
                Command.JACOBIAN to listOf("जैकोबियन आव्यूह", "जैकोबियन"),
                Command.LAPLACIAN to listOf("लाप्लासियन"),
                Command.LEAST_SQUARES to listOf("न्यूनतम वर्ग", "न्यूनतम वर्ग विधि से हल करें", "न्यूनतम वर्ग विधि से हल कीजिए", "न्यूनतम वर्ग विधि"),
                Command.EXTREMA to listOf("क्रांतिक बिंदु", "उच्चिष्ठ और निम्निष्ठ", "उच्चतम और निम्नतम"),
                Command.LAPLACE to listOf("लाप्लास रूपांतरण", "लैप्लास रूपांतरण"),
                Command.INVERSE_LAPLACE to listOf("व्युत्क्रम लाप्लास रूपांतरण", "प्रतिलोम लाप्लास रूपांतरण", "प्रतिलोम लैप्लास रूपांतरण"),
                Command.FOURIER to listOf("फूरियर श्रेणी"),
                Command.NEWTON to listOf("न्यूटन-रैफसन विधि", "न्यूटन विधि", "न्यूटन रैफसन विधि", "न्यूटन-रैफसन"),
                Command.BISECTION to listOf("द्विभाजन विधि", "द्विभाजन"),
                Command.EULER to listOf("ऑयलर विधि", "यूलर विधि"),
                Command.RUNGE_KUTTA to listOf("रंगे-कुट्टा विधि", "रुंगे-कुट्टा विधि", "रुंगे-कुट्टा"),
                Command.TRAPEZOID to listOf("समलंब नियम"),
                Command.SIMPSON to listOf("सिम्पसन नियम", "सिम्पसन का नियम"),
                Command.POLAR to listOf("ध्रुवीय रूप", "ध्रुवीय निरूपण"),
                Command.RESIDUES to listOf("अवशेष", "ध्रुव"),
                Command.NUMBER_FACTS to listOf("क्या अभाज्य है", "भाजक", "संख्या के गुण", "क्या एक अभाज्य संख्या है", "अभाज्य संख्या है", "अभाज्य है"),
                Command.STATISTICS to listOf("माध्यिका", "माध्य", "औसत", "सांख्यिकी", "मानक विचलन", "प्रसरण", "बहुलक"),
                Command.REGRESSION to listOf("रैखिक समाश्रयण", "समाश्रयण रेखा", "समाश्रयण"),
                Command.SEQUENCE to listOf("अगले पद", "अगला पद", "व्यापक पद", "अनुक्रम"),
                Command.TRUTH_TABLE to listOf("सत्यता सारणी", "पुनरुक्ति"),
                Command.TRIANGLE to listOf("त्रिभुज हल करें", "त्रिभुज"),
                Command.CONIC to listOf("शांकव", "दीर्घवृत्त", "अतिपरवलय", "परवलय", "नाभियाँ", "उत्केंद्रता", "नाभि"),
                Command.LINE to listOf("बिंदुओं से होकर जाने वाली रेखा", "से होकर जाने वाली रेखा"),
                Command.PLANE to listOf("बिंदुओं से होकर जाने वाला समतल", "से होकर जाने वाला समतल"),
                Command.DIOPHANTINE to listOf("पूर्णांक हल", "डायोफैंटाइन समीकरण"),
            ),
            of = setOf("का", "की", "के"),
            from = words("से"),
            to = words("तक"),
            forWords = words("के लिए"),
            respect = words("के सापेक्ष"),
            // "जब" stands before its value, which a postpositional language cannot read: x->0 says it.
            approach = emptyList(),
            postpositional = true,
            commandLast = true,
            particles = setOf("का", "की", "के", "को"),
            // "हल करें x^2=4": the verb's helper after a command that comes first.
            ignored = setOf("करें", "करो", "कीजिए", "कीजिये"),
            endings = listOf("ज्ञात कीजिए", "ज्ञात कीजिये", "ज्ञात करें", "ज्ञात करो", "करें", "करो", "कीजिए", "कीजिये", "बनाएं", "बनाएँ", "बनाओ",
                "खींचिए", "खींचें", "खींचो"),
            constraints = listOf("प्रतिबंध के साथ", "के प्रतिबंध के अंतर्गत", "व्यवरोध के अंतर्गत"),
            nouns = words("आव्यूह"),
        )

        val JAPANESE = Vocabulary(
            "ja",
            mapOf(
                Command.SOLVE to listOf("方程式を解く", "解いて", "解く", "解け", "解きなさい", "解"),
                Command.FACTOR to listOf("因数分解"),
                Command.EXPAND to listOf("展開"),
                Command.SIMPLIFY to listOf("簡単にして", "簡単に", "簡単化", "簡約", "約分", "整理"),
                Command.DERIVATIVE to listOf("導関数", "微分"),
                Command.INTEGRAL to listOf("不定積分", "原始関数", "積分"),
                Command.LIMIT to listOf("極限値", "極限"),
                Command.SUM to listOf("総和", "和"),
                Command.PLOT to listOf("グラフを描いて", "グラフを描く", "グラフを描け", "グラフをかいて", "グラフをかく", "グラフをかけ", "グラフ"),
                Command.CALCULATE to listOf("計算", "の値"),
                Command.PARTIAL_FRACTIONS to listOf("部分分数分解", "部分分数に分解"),
                Command.SERIES to listOf("テイラー展開", "マクローリン展開", "テイラー級数", "マクローリン級数", "テイラー級数展開", "べき級数展開", "級数展開", "テーラー展開", "テイラー多項式", "ローラン展開"),
                Command.DETERMINANT to listOf("行列式"),
                Command.INVERSE to listOf("逆行列"),
                Command.EIGEN to listOf("固有値と固有ベクトル", "固有値", "固有ベクトル", "固有多項式"),
                Command.ROW_REDUCE to listOf("階段行列", "簡約化", "階数", "ランク", "簡約階段行列", "行階段形", "掃き出し法"),
                Command.TRANSPOSE to listOf("転置行列", "転置"),
                Command.DECOMPOSE to listOf("lu分解", "qr分解", "コレスキー分解", "lu 分解", "qr 分解", "cholesky 分解"),
                Command.MATRIX_EXP to listOf("行列指数関数", "行列の指数関数"),
                Command.GRADIENT to listOf("勾配"),
                Command.DIVERGENCE to listOf("発散"),
                Command.CURL to listOf("回転"),
                Command.JACOBIAN to listOf("ヤコビ行列", "ヤコビアン", "ヤコビ行列式"),
                Command.LAPLACIAN to listOf("ラプラシアン"),
                Command.LEAST_SQUARES to listOf("最小二乗法", "最小二乗", "最小二乗解"),
                Command.EXTREMA to listOf("極値", "臨界点", "条件付き極値", "最大値", "最小値", "最大化", "最小化"),
                Command.LAPLACE to listOf("ラプラス変換"),
                Command.INVERSE_LAPLACE to listOf("逆ラプラス変換", "ラプラス逆変換"),
                Command.FOURIER to listOf("フーリエ級数展開", "フーリエ級数", "フーリエ級数に展開"),
                Command.NEWTON to listOf("ニュートン法"),
                Command.BISECTION to listOf("二分法"),
                Command.EULER to listOf("オイラー法"),
                Command.RUNGE_KUTTA to listOf("ルンゲ・クッタ法", "ルンゲクッタ法", "ルンゲ＝クッタ法"),
                Command.TRAPEZOID to listOf("台形公式", "台形則", "台形法"),
                Command.SIMPSON to listOf("シンプソン公式", "シンプソン則", "シンプソンの公式"),
                Command.POLAR to listOf("極形式"),
                Command.RESIDUES to listOf("留数"),
                Command.NUMBER_FACTS to listOf("は素数か", "素数判定", "は素数ですか", "素因数分解", "の約数"),
                Command.STATISTICS to listOf("平均値", "平均", "中央値", "統計量", "標準偏差", "分散", "の平均", "最頻値"),
                Command.REGRESSION to listOf("回帰直線", "線形回帰", "回帰分析", "回帰"),
                Command.SEQUENCE to listOf("次の項", "一般項", "数列"),
                Command.TRUTH_TABLE to listOf("真理値表", "恒真式", "トートロジー"),
                Command.TRIANGLE to listOf("三角形を解く", "三角形"),
                Command.CONIC to listOf("二次曲線", "楕円", "双曲線", "放物線", "焦点", "離心率"),
                Command.LINE to listOf("を通る直線", "2点を通る直線", "を通る直線の方程式"),
                Command.PLANE to listOf("を通る平面", "3点を通る平面", "を通る平面の方程式"),
                Command.DIOPHANTINE to listOf("整数解", "不定方程式"),
            ),
            of = setOf("の"),
            from = words("から"),
            to = words("まで"),
            forWords = words("について"),
            respect = words("で"),
            approach = words("のとき"),
            postpositional = true,
            commandLast = true,
            particles = setOf("を", "の", "は"),
            // "…の導関数を求めよ": the textbook's "find" after what is found.
            endings = listOf("を求めてください", "を求めなさい", "を求めよ", "を求めて", "を求める", "してください", "して下さい", "ください",
                "しなさい", "して", "せよ", "する"),
            ignored = setOf("方程式"),
            constraints = listOf("の条件のもとで", "のもとで", "の下で"),
        )

        val KOREAN = Vocabulary(
            "ko",
            mapOf(
                Command.SOLVE to listOf("방정식 풀기", "풀어라", "풀어", "푸시오", "풀기", "풀이", "의 해"),
                Command.FACTOR to listOf("인수분해"),
                Command.EXPAND to listOf("전개"),
                Command.SIMPLIFY to listOf("간단히", "정리"),
                Command.DERIVATIVE to listOf("도함수", "미분"),
                Command.INTEGRAL to listOf("부정적분", "정적분", "원시함수", "적분"),
                Command.LIMIT to listOf("극한값", "극한"),
                Command.SUM to listOf("합"),
                Command.PLOT to listOf("그래프를 그리시오", "그래프를 그려라", "그래프를 그려", "그래프 그리기", "그래프"),
                Command.CALCULATE to listOf("계산", "값 구하기", "의 값"),
                Command.PARTIAL_FRACTIONS to listOf("부분분수로 분해", "부분분수", "부분분수분해", "부분분수 분해"),
                Command.SERIES to listOf("테일러 급수", "테일러 전개", "테일러급수", "테일러전개", "매클로린 급수", "매클로린급수", "테일러 다항식", "매클로린 전개", "매클로린전개", "테일러 급수로 전개", "급수로 전개", "급수 전개", "맥클로린 급수", "멱급수", "로랑 급수"),
                Command.DETERMINANT to listOf("행렬식"),
                Command.INVERSE to listOf("역행렬"),
                Command.EIGEN to listOf("고윳값과 고유벡터", "고윳값", "고유값", "고유벡터", "고유값과 고유벡터"),
                Command.ROW_REDUCE to listOf("기약 행 사다리꼴", "행 사다리꼴", "랭크", "기약행사다리꼴", "행사다리꼴", "가우스 소거법"),
                Command.TRANSPOSE to listOf("전치행렬", "전치 행렬", "전치"),
                Command.DECOMPOSE to listOf("lu 분해", "lu분해", "qr 분해", "qr분해", "숄레스키 분해"),
                Command.MATRIX_EXP to listOf("행렬 지수", "행렬 지수함수"),
                Command.GRADIENT to listOf("그래디언트"),
                Command.DIVERGENCE to listOf("발산"),
                Command.CURL to listOf("회전", "컬"),
                Command.JACOBIAN to listOf("야코비 행렬", "야코비안"),
                Command.LAPLACIAN to listOf("라플라시안"),
                Command.LEAST_SQUARES to listOf("최소제곱법", "최소 제곱", "최소 제곱법", "최소제곱해"),
                Command.EXTREMA to listOf("극값", "임계점", "조건부 극값", "최댓값", "최솟값", "최대화", "최소화"),
                Command.LAPLACE to listOf("라플라스 변환"),
                Command.INVERSE_LAPLACE to listOf("역라플라스 변환", "라플라스 역변환", "역 라플라스 변환"),
                Command.FOURIER to listOf("푸리에 급수", "푸리에 급수로 전개"),
                Command.NEWTON to listOf("뉴턴 방법", "뉴턴법"),
                Command.BISECTION to listOf("이분법"),
                Command.EULER to listOf("오일러 방법", "오일러법"),
                Command.RUNGE_KUTTA to listOf("룽게-쿠타 방법", "룽게쿠타법", "룽게-쿠타"),
                Command.TRAPEZOID to listOf("사다리꼴 공식", "사다리꼴 규칙", "사다리꼴 법칙"),
                Command.SIMPSON to listOf("심프슨 공식", "심프슨 규칙", "심프슨 법칙"),
                Command.POLAR to listOf("극형식", "극 형식"),
                Command.RESIDUES to listOf("유수", "극점"),
                Command.NUMBER_FACTS to listOf("소수인가", "수의 성질", "소수인가요", "소수입니까", "의 약수"),
                Command.STATISTICS to listOf("평균", "중앙값", "통계", "표준편차", "분산", "최빈값"),
                Command.REGRESSION to listOf("선형 회귀", "회귀직선", "회귀"),
                Command.SEQUENCE to listOf("다음 항", "일반항", "수열"),
                Command.TRUTH_TABLE to listOf("진리표", "항진명제"),
                Command.TRIANGLE to listOf("삼각형 풀기", "삼각형"),
                Command.CONIC to listOf("이차곡선", "타원", "쌍곡선", "포물선", "초점", "이심률"),
                Command.LINE to listOf("을 지나는 직선", "를 지나는 직선", "지나는 직선"),
                Command.PLANE to listOf("을 지나는 평면", "를 지나는 평면", "지나는 평면"),
                Command.DIOPHANTINE to listOf("정수해", "디오판토스 방정식"),
            ),
            of = setOf("의"),
            from = words("부터", "에서"),
            to = words("까지"),
            forWords = words("에 대해", "에 대하여"),
            respect = words("에 관하여", "에 관해"),
            approach = words("일 때"),
            postpositional = true,
            commandLast = true,
            particles = setOf("을", "를", "의", "은", "는"),
            endings = listOf("를 구하시오", "을 구하시오", "를 구하세요", "을 구하세요", "를 구하여라", "을 구하여라", "를 구해", "을 구해",
                "하여라", "해 주세요", "해주세요", "하시오", "하세요", "해 줘", "해줘", "해라", "하기", "해"),
            ignored = setOf("방정식"),
            nouns = words("행렬"),
            constraints = listOf("조건 하에서", "조건에서", "하에서"),
        )

        val THAI = Vocabulary(
            "th",
            mapOf(
                Command.SOLVE to listOf("แก้สมการ", "แก้", "หาคำตอบ"),
                Command.FACTOR to listOf("แยกตัวประกอบ"),
                Command.EXPAND to listOf("กระจาย"),
                Command.SIMPLIFY to listOf("ทำให้อยู่ในรูปอย่างง่าย", "ลดรูป", "ทำให้ง่าย"),
                Command.DERIVATIVE to listOf("หาอนุพันธ์", "อนุพันธ์ของ", "อนุพันธ์", "ดิฟ"),
                Command.INTEGRAL to listOf("หาอินทิกรัล", "อินทิกรัลของ", "อินทิกรัล", "ปริพันธ์", "อินทิเกรต"),
                Command.LIMIT to listOf("หาลิมิต", "ลิมิตของ", "ลิมิต"),
                Command.SUM to listOf("ผลรวมของ", "ผลรวม"),
                Command.PLOT to listOf("วาดกราฟ", "เขียนกราฟ", "กราฟของ", "กราฟ", "พล็อต"),
                Command.CALCULATE to listOf("คำนวณ", "หาค่า", "หา"),
                Command.PARTIAL_FRACTIONS to listOf("แยกเศษส่วนย่อย", "เศษส่วนย่อย", "แยกเป็นเศษส่วนย่อย"),
                Command.SERIES to listOf("อนุกรมเทย์เลอร์ของ", "อนุกรมเทย์เลอร์", "อนุกรมแมคลอรินของ", "อนุกรมแมคลอริน", "พหุนามเทย์เลอร์", "หาอนุกรมเทย์เลอร์ของ", "หาอนุกรมเทย์เลอร์", "หาอนุกรมแมคลอรินของ", "หาพหุนามเทย์เลอร์ของ", "กระจายเป็นอนุกรมเทย์เลอร์", "อนุกรมกำลัง"),
                Command.DETERMINANT to listOf("ดีเทอร์มิแนนต์ของ", "ดีเทอร์มิแนนต์"),
                Command.INVERSE to listOf("อินเวอร์สของเมทริกซ์", "เมทริกซ์ผกผันของ", "อินเวอร์สของ", "เมทริกซ์ผกผัน"),
                Command.EIGEN to listOf("ค่าลักษณะเฉพาะของ", "ค่าลักษณะเฉพาะ", "เวกเตอร์ลักษณะเฉพาะ", "หาค่าลักษณะเฉพาะของ", "หาค่าลักษณะเฉพาะ", "หาเวกเตอร์ลักษณะเฉพาะ"),
                Command.ROW_REDUCE to listOf("รูปแบบขั้นบันไดแบบลดรูป", "รูปแบบขั้นบันได", "แรงก์ของ", "แรงก์"),
                Command.TRANSPOSE to listOf("ทรานสโพสของ", "เมทริกซ์สลับเปลี่ยน", "ทรานสโพส"),
                Command.DECOMPOSE to listOf("การแยกตัวประกอบ lu", "การแยก qr", "การแยกโชเลสกี", "แยกตัวประกอบ lu", "การแยก lu", "การแยกตัวประกอบ qr", "แยกตัวประกอบ qr"),
                Command.MATRIX_EXP to listOf("เลขชี้กำลังของเมทริกซ์"),
                Command.GRADIENT to listOf("เกรเดียนต์ของ", "เกรเดียนต์"),
                Command.DIVERGENCE to listOf("ไดเวอร์เจนซ์ของ", "ไดเวอร์เจนซ์"),
                Command.CURL to listOf("เคิร์ลของ", "เคิร์ล"),
                Command.JACOBIAN to listOf("เมทริกซ์จาโคเบียน", "จาโคเบียน"),
                Command.LAPLACIAN to listOf("ลาปลาเซียน"),
                Command.LEAST_SQUARES to listOf("กำลังสองน้อยที่สุด"),
                Command.EXTREMA to listOf("ค่าสุดขีด", "จุดวิกฤต", "ค่าสูงสุด", "ค่าต่ำสุด"),
                Command.LAPLACE to listOf("การแปลงลาปลาซของ", "การแปลงลาปลาซ"),
                Command.INVERSE_LAPLACE to listOf("การแปลงลาปลาซผกผันของ", "การแปลงลาปลาซผกผัน"),
                Command.FOURIER to listOf("อนุกรมฟูริเยร์ของ", "อนุกรมฟูริเยร์", "อนุกรมฟูเรียร์"),
                Command.NEWTON to listOf("วิธีของนิวตัน", "วิธีนิวตัน"),
                Command.BISECTION to listOf("วิธีแบ่งครึ่งช่วง", "วิธีแบ่งครึ่ง", "แบ่งครึ่งช่วง"),
                Command.EULER to listOf("วิธีของออยเลอร์", "วิธีออยเลอร์"),
                Command.RUNGE_KUTTA to listOf("วิธีรุงเง-คุตตา", "รุงเง-คุตตา"),
                Command.TRAPEZOID to listOf("กฎสี่เหลี่ยมคางหมู", "วิธีสี่เหลี่ยมคางหมู"),
                Command.SIMPSON to listOf("กฎของซิมป์สัน", "วิธีซิมป์สัน", "กฎซิมป์สัน", "วิธีของซิมป์สัน"),
                Command.POLAR to listOf("รูปเชิงขั้ว"),
                Command.RESIDUES to listOf("ส่วนตกค้าง", "เรซิดิว"),
                Command.NUMBER_FACTS to listOf("เป็นจำนวนเฉพาะหรือไม่", "ตัวหารของ", "สมบัติของจำนวน"),
                Command.STATISTICS to listOf("ค่าเฉลี่ยของ", "ค่าเฉลี่ย", "มัธยฐานของ", "มัธยฐาน", "สถิติของ", "ส่วนเบี่ยงเบนมาตรฐาน", "ความแปรปรวน", "ฐานนิยมของ", "ฐานนิยม"),
                Command.REGRESSION to listOf("การถดถอยเชิงเส้น", "เส้นถดถอย", "การถดถอย"),
                Command.SEQUENCE to listOf("พจน์ถัดไปของ", "พจน์ถัดไป", "พจน์ทั่วไปของ", "ลำดับ"),
                Command.TRUTH_TABLE to listOf("ตารางค่าความจริงของ", "ตารางค่าความจริง", "สัจนิรันดร์"),
                Command.TRIANGLE to listOf("แก้สามเหลี่ยม", "สามเหลี่ยม"),
                Command.CONIC to listOf("ภาคตัดกรวย", "วงรี", "ไฮเพอร์โบลา", "พาราโบลา", "จุดโฟกัสของ", "ความเยื้องศูนย์กลาง"),
                Command.LINE to listOf("เส้นตรงที่ผ่านจุด", "เส้นตรงผ่าน", "สมการเส้นตรงที่ผ่านจุด"),
                Command.PLANE to listOf("ระนาบที่ผ่านจุด", "ระนาบผ่าน", "สมการระนาบที่ผ่านจุด"),
                Command.DIOPHANTINE to listOf("ผลเฉลยที่เป็นจำนวนเต็ม", "สมการไดโอแฟนไทน์"),
            ),
            of = setOf("ของ"),
            from = words("จาก"),
            to = words("ถึง"),
            forWords = words("สำหรับ"),
            respect = words("เทียบกับ"),
            approach = words("เมื่อ"),
            // "จงแก้สมการ …": the imperative every exercise starts with.
            prefixes = listOf("จง"),
            nouns = words("เมทริกซ์", "ฟังก์ชัน"),
            constraints = listOf("ภายใต้เงื่อนไข", "โดยมีเงื่อนไข"),
        )

        /** Simplified and Traditional characters together: a word is the same command in either. */
        val CHINESE = Vocabulary(
            "zh",
            mapOf(
                Command.SOLVE to listOf("解方程式", "解方程", "求解", "解"),
                Command.FACTOR to listOf("因式分解", "分解因式"),
                Command.EXPAND to listOf("展开", "展開"),
                Command.SIMPLIFY to listOf("化简", "化簡", "简化", "簡化"),
                Command.DERIVATIVE to listOf("求导数", "求導數", "求导", "求導", "导函数", "導函數", "导数", "導數", "微分"),
                Command.INTEGRAL to listOf("求积分", "求積分", "不定积分", "不定積分", "定积分", "定積分", "原函数", "原函數", "积分", "積分"),
                Command.LIMIT to listOf("求极限", "求極限", "极限", "極限"),
                Command.SUM to listOf("求和"),
                Command.PLOT to listOf("画出", "畫出", "画图", "畫圖", "作图", "作圖", "绘制", "繪製", "图像", "圖像", "图形", "圖形"),
                Command.CALCULATE to listOf("计算", "計算", "求值", "等于多少", "等於多少"),
                Command.PARTIAL_FRACTIONS to listOf("部分分式分解", "部分分式", "分解为部分分式", "分解成部分分式", "化为部分分式", "分解為部分分式", "化為部分分式", "化成部分分式"),
                Command.SERIES to listOf("泰勒级数", "泰勒展开式", "泰勒展开", "麦克劳林级数", "麦克劳林展开", "泰勒級數", "泰勒展開式", "泰勒展開", "麥克勞林級數", "泰勒多项式", "泰勒多項式", "展开成泰勒级数", "展开为泰勒级数", "展开成幂级数", "展開成泰勒級數", "展開為泰勒級數", "麥克勞林展開", "级数展开", "級數展開", "泰勒公式", "麦克劳林公式", "幂级数", "冪級數", "馬克勞林級數", "洛朗级数", "洛朗級數"),
                Command.DETERMINANT to listOf("行列式"),
                Command.INVERSE to listOf("求逆矩阵", "逆矩阵", "反矩陣", "逆矩陣"),
                Command.EIGEN to listOf("特征值和特征向量", "特征值", "特征向量", "特徵值", "特徵向量", "特征值与特征向量", "特徵值和特徵向量", "特徵值與特徵向量"),
                Command.ROW_REDUCE to listOf("化为行最简形", "行最简形", "行阶梯形", "列梯形", "秩", "行阶梯形矩阵", "行最简形矩阵", "列梯形矩陣", "简化列梯形", "簡化列梯形", "高斯消元法"),
                Command.TRANSPOSE to listOf("转置矩阵", "转置", "轉置矩陣", "轉置"),
                Command.DECOMPOSE to listOf("lu分解", "qr分解", "楚列斯基分解", "cholesky分解", "lu 分解", "qr 分解", "cholesky 分解"),
                Command.MATRIX_EXP to listOf("矩阵指数", "矩陣指數"),
                Command.GRADIENT to listOf("梯度"),
                Command.DIVERGENCE to listOf("散度"),
                Command.CURL to listOf("旋度"),
                Command.JACOBIAN to listOf("雅可比矩阵", "雅可比矩陣", "雅可比行列式"),
                Command.LAPLACIAN to listOf("拉普拉斯算子", "拉普拉斯"),
                Command.LEAST_SQUARES to listOf("最小二乘法", "最小二乘", "最小平方法", "最小二乘解", "最小平方解"),
                Command.EXTREMA to listOf("极值", "临界点", "極值", "臨界點", "条件极值", "條件極值", "驻点", "駐點", "最大值", "最小值"),
                Command.LAPLACE to listOf("拉普拉斯变换", "拉普拉斯變換", "拉氏变换", "拉普拉斯轉換", "拉氏變換"),
                Command.INVERSE_LAPLACE to listOf("拉普拉斯逆变换", "逆拉普拉斯变换", "拉普拉斯逆變換", "拉氏逆变换", "拉普拉斯反变换", "拉普拉斯逆轉換", "逆拉普拉斯變換", "拉氏逆變換", "拉普拉斯反變換"),
                Command.FOURIER to listOf("傅里叶级数", "傅立叶级数", "傅立葉級數", "傅里葉級數"),
                Command.NEWTON to listOf("牛顿迭代法", "牛顿法", "牛頓法", "牛頓迭代法"),
                Command.BISECTION to listOf("二分法"),
                Command.EULER to listOf("欧拉法", "歐拉法"),
                Command.RUNGE_KUTTA to listOf("龙格-库塔法", "龙格库塔法", "龍格-庫塔法", "龍格庫塔法"),
                Command.TRAPEZOID to listOf("梯形法则", "梯形公式", "梯形法", "梯形法則"),
                Command.SIMPSON to listOf("辛普森法则", "辛普森公式", "辛普森法", "辛普森法則"),
                Command.POLAR to listOf("极坐标形式", "三角形式", "極坐標形式", "極式", "指数形式", "指數形式"),
                Command.RESIDUES to listOf("留数", "留數", "极点", "極點"),
                Command.NUMBER_FACTS to listOf("是否为质数", "是不是质数", "是否為質數", "数的性质", "是质数吗", "是素数吗", "是否为素数", "是不是素数", "是質數嗎", "分解质因数", "分解質因數", "的因数", "的约数", "的因數", "的約數"),
                Command.STATISTICS to listOf("平均值", "平均数", "平均數", "中位数", "中位數", "统计量", "統計量", "标准差", "標準差", "方差", "變異數", "平均", "众数", "眾數"),
                Command.REGRESSION to listOf("线性回归", "線性迴歸", "回归直线", "迴歸直線", "回归", "迴歸"),
                Command.SEQUENCE to listOf("下一项", "下一項", "通项公式", "通項公式", "数列", "數列"),
                Command.TRUTH_TABLE to listOf("真值表", "重言式", "恒真式"),
                Command.TRIANGLE to listOf("解三角形", "三角形"),
                Command.CONIC to listOf("圆锥曲线", "圓錐曲線", "椭圆", "橢圓", "双曲线", "雙曲線", "抛物线", "拋物線", "焦点", "焦點", "离心率", "離心率"),
                Command.LINE to listOf("过点的直线", "经过点的直线", "過點的直線", "直线过", "直線過", "过点 的直线", "经过点 的直线", "過點 的直線", "过点 的直线方程"),
                Command.PLANE to listOf("过点的平面", "经过点的平面", "過點的平面", "平面过", "平面過", "过点 的平面", "過點 的平面"),
                Command.DIOPHANTINE to listOf("整数解", "整數解", "丢番图方程", "丟番圖方程"),
            ),
            of = setOf("的"),
            from = words("从", "從"),
            to = words("到"),
            forWords = words("关于", "關於"),
            respect = words("对", "對"),
            approach = words("当", "當"),
            commandLast = true,
            particles = setOf("的"),
            // "求 x^3 的导数", "把 x^2-1 因式分解": a leading "find" or object marker; "当 x->0 时": 时 closes the approach.
            ignored = setOf("求", "把", "将", "將"),
            closers = setOf("时", "時"),
            nouns = words("矩阵", "矩陣"),
            constraints = listOf("约束条件为", "限制條件為", "约束条件", "限制條件"),
        )

        val ALL: List<Vocabulary> = listOf(
            ENGLISH, GERMAN, SPANISH, FRENCH, INDONESIAN, ITALIAN, DUTCH, POLISH, PORTUGUESE, TURKISH, VIETNAMESE, RUSSIAN,
            UKRAINIAN, ARABIC, HEBREW, HINDI, JAPANESE, KOREAN, THAI, CHINESE,
        )

        /** The vocabulary of an app language ("de", "de-CH", "pt-BR", "zh-Hant-TW", Android's old "iw" and "in"), or null. */
        fun of(language: String): Vocabulary? {
            val base = when (val b = language.lowercase(Locale.ROOT).substringBefore('-').substringBefore('_')) {
                "iw" -> "he"
                "in" -> "id"
                else -> b
            }
            return ALL.firstOrNull { it.language == base }
        }
    }
}

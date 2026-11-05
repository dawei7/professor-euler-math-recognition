package com.professoreuler.mathcalc

/**
 * Propositional logic and the algebra of sets: a formula in ∧ ∨ ¬ → ↔ ⊕ (or ∪ ∩ ∖ and complements of sets named by letters)
 * as its truth table, its simplest disjunctive and conjunctive forms (Quine–McCluskey, the cover chosen by Petrick's method)
 * and whether it always or never holds; two formulas with = between them compared; sets written out ({1, 2, 3} ∪ {3, 4})
 * worked out. Every form is checked against the whole table.
 */
object Logic {
    sealed class Bool {
        data class Var(val name: String) : Bool()
        data class Const(val value: Boolean) : Bool()
        data class Not(val a: Bool) : Bool()
        data class And(val parts: List<Bool>) : Bool()
        data class Or(val parts: List<Bool>) : Bool()
        data class Xor(val a: Bool, val b: Bool) : Bool()
        data class Implies(val a: Bool, val b: Bool) : Bool()
        data class Iff(val a: Bool, val b: Bool) : Bool()
    }

    /** What was read: a formula of letters ([sets] where it was written with set operations), or one of sets written out. */
    sealed class Reading {
        class Formula(val sets: Boolean, val left: Bool, val right: Bool?) : Reading()
        class Literal(val elements: List<Expr>) : Reading()
    }

    private val NOT = setOf("\\neg", "\\lnot", "¬", "\\sim", "~")
    private val AND = setOf("\\land", "\\wedge", "∧", "\\cap", "∩")
    private val OR = setOf("\\lor", "\\vee", "∨", "\\cup", "∪")
    private val XOR = setOf("\\oplus", "\\veebar", "⊕", "\\triangle", "\\vartriangle", "\\bigtriangleup", "△", "Δ")
    private val IMPLIES = setOf("\\Rightarrow", "\\implies", "⇒", "\\to", "\\rightarrow", "→", "\\longrightarrow", "\\Longrightarrow")
    private val IFF = setOf("\\Leftrightarrow", "\\iff", "⇔", "\\leftrightarrow", "↔", "\\Longleftrightarrow", "\\longleftrightarrow")
    private val MINUS = setOf("\\setminus", "\\backslash", "∖")
    private val SET_TOKENS = setOf("\\cup", "∪", "\\cap", "∩", "\\setminus", "∖", "\\complement", "\\emptyset", "\\varnothing", "∅")
    private val STRONG = setOf("\\neg", "\\lnot", "¬", "\\land", "\\wedge", "∧", "\\lor", "\\vee", "∨", "\\oplus", "\\veebar", "⊕",
        "\\Rightarrow", "\\implies", "⇒", "\\Leftrightarrow", "\\iff", "⇔", "\\leftrightarrow", "↔", "\\top", "\\bot") + SET_TOKENS
    private val SPACES = setOf("\\left", "\\right", "\\,", "\\;", "\\!", "\\:", "\\ ", "\\quad", "\\qquad", "\\big", "\\Big")

    /** The formula's reading, or null where [tokens] are no logic and no sets (they are then mathematics). */
    fun read(tokens0: List<String>): Reading? {
        val tokens = tokens0.filter { it !in SPACES }
        val strong = tokens.any { it in STRONG }
        // p → q alone, with no limit or sum about: an implication.
        val arrows = tokens.any { it in IMPLIES } && tokens.none { it == "\\lim" || it == "_" || it == "\\sum" || it.isNumber() } &&
            tokens.all { it in IMPLIES || it in IFF || it.length == 1 && it[0].isLetter() || it == "(" || it == ")" || it in NOT }
        if (!strong && !arrows) return null
        val sets = tokens.any { it in SET_TOKENS }
        return Reader(tokens, sets).reading()
    }

    private fun String.isNumber() = isNotEmpty() && all { it.isDigit() }

    private class Reader(val t: List<String>, val sets: Boolean) {
        var i = 0
        fun peek(k: Int = 0) = t.getOrNull(i + k)
        fun fail(): Nothing = throw CalcException(Failure.UNREADABLE)

        fun reading(): Reading {
            if (t.contains("\\{")) return Reading.Literal(literal())
            val left = iff()
            if (peek() == "=" || peek() == "\\equiv" || peek() == "≡") {
                i++
                val right = iff()
                if (i != t.size) fail()
                return Reading.Formula(sets, left, right)
            }
            if (i != t.size) fail()
            return Reading.Formula(sets, left, null)
        }

        fun iff(): Bool {
            var a = implies()
            while (peek() in IFF) { i++; a = Bool.Iff(a, implies()) }
            return a
        }

        fun implies(): Bool {
            val a = or()
            if (peek() in IMPLIES) { i++; return Bool.Implies(a, implies()) }
            return a
        }

        fun or(): Bool {
            var a = and()
            while (true) {
                a = when (peek()) {
                    in OR -> { i++; Bool.Or(listOf(a, and())) }
                    in XOR -> { i++; Bool.Xor(a, and()) }
                    // A + B for "or" only in sets' sum? No: + stays out of logic.
                    else -> return a
                }
            }
        }

        fun and(): Bool {
            var a = not()
            while (true) {
                a = when (peek()) {
                    in AND -> { i++; Bool.And(listOf(a, not())) }
                    in MINUS -> { i++; Bool.And(listOf(a, Bool.Not(not()))) }
                    else -> return a
                }
            }
        }

        fun not(): Bool {
            if (peek() in NOT || peek() == "\\complement") { i++; return Bool.Not(not()) }
            var a = atom()
            // Complements written after: A^c, A^{c}, A', A^\complement.
            while (true) {
                when {
                    peek() == "'" || peek() == "\\prime" -> { i++; a = Bool.Not(a) }
                    peek() == "^" && (peek(1) == "c" || peek(1) == "C" || peek(1) == "\\complement" || peek(1) == "\\prime") -> { i += 2; a = Bool.Not(a) }
                    peek() == "^" && peek(1) == "{" && peek(3) == "}" && (peek(2) == "c" || peek(2) == "C" || peek(2) == "\\complement" || peek(2) == "\\prime") -> {
                        i += 4
                        a = Bool.Not(a)
                    }
                    else -> return a
                }
            }
        }

        fun atom(): Bool {
            val tok = peek() ?: fail()
            return when {
                tok == "(" || tok == "[" -> {
                    i++
                    val a = iff()
                    if (peek() != ")" && peek() != "]") fail()
                    i++
                    a
                }
                tok == "{" -> { i++; val a = iff(); if (peek() != "}") fail(); i++; a }
                tok == "\\overline" || tok == "\\bar" -> {
                    i++
                    if (peek() != "{") return Bool.Not(atom())
                    i++
                    val a = iff()
                    if (peek() != "}") fail()
                    i++
                    Bool.Not(a)
                }
                tok == "\\top" || tok == "1" -> { i++; Bool.Const(true) }
                tok == "\\bot" || tok == "0" || tok == "\\emptyset" || tok == "\\varnothing" || tok == "∅" -> { i++; Bool.Const(false) }
                tok.length == 1 && tok[0].isLetter() -> {
                    i++
                    if (peek() == "_") {
                        i++
                        val sub = if (peek() == "{") {
                            i++
                            val start = i
                            while (peek() != null && peek() != "}") i++
                            val s = t.subList(start, i).joinToString("")
                            i++
                            s
                        } else t.getOrNull(i++) ?: fail()
                        Bool.Var("${tok}_$sub")
                    } else {
                        Bool.Var(tok)
                    }
                }
                tok == "\\alpha" || tok == "\\beta" || tok == "\\varphi" || tok == "\\psi" || tok == "\\phi" -> { i++; Bool.Var(tok) }
                else -> fail()
            }
        }

        /** Sets written out, with ∪ ∩ ∖ △ between them. */
        fun literal(): List<Expr> {
            fun set(): List<Expr> {
                when (peek()) {
                    "(" -> { i++; val s = expression(); if (peek() != ")") fail(); i++; return s }
                    "\\emptyset", "\\varnothing", "∅" -> { i++; return emptyList() }
                    "\\{" -> {}
                    else -> fail()
                }
                i++
                val out = ArrayList<Expr>()
                var start = i
                var depth = 0
                while (true) {
                    val tok = peek() ?: fail()
                    if (tok == "(" || tok == "{" || tok == "[") depth++
                    if (tok == ")" || tok == "}" || tok == "]") depth--
                    if (depth == 0 && (tok == "," || tok == ";" || tok == "\\}")) {
                        if (i > start) out += element(t.subList(start, i))
                        i++
                        if (tok == "\\}") break
                        start = i
                        continue
                    }
                    i++
                }
                return out.distinct()
            }
            val result = expression(::set)
            if (i != t.size) fail()
            return result.sortedWith(compareBy<Expr>({ Numeric.value(it) ?: Double.MAX_VALUE }, { it.toString() }))
        }

        private var setReader: (() -> List<Expr>)? = null

        fun expression(set: (() -> List<Expr>)? = null): List<Expr> {
            if (set != null) setReader = set
            val read = setReader ?: fail()
            var a = intersection(read)
            while (true) {
                a = when (peek()) {
                    "\\cup", "∪" -> { i++; (a + intersection(read)).distinct() }
                    "\\triangle", "\\vartriangle", "△", "\\bigtriangleup" -> { i++; val b = intersection(read); (a - b.toSet()) + (b - a.toSet()) }
                    else -> return a
                }
            }
        }

        fun intersection(read: () -> List<Expr>): List<Expr> {
            var a = read()
            while (true) {
                a = when (peek()) {
                    "\\cap", "∩" -> { i++; val b = read().toSet(); a.filter { it in b } }
                    "\\setminus", "\\backslash", "∖" -> { i++; val b = read().toSet(); a.filter { it !in b } }
                    else -> return a
                }
            }
        }

        fun element(tokens: List<String>): Expr = try {
            Parser(tokens).statements().single().sides.single()!!.let { Calculus.evaluate(it) }
        } catch (e: CalcException) {
            fail()
        } catch (e: RuntimeException) {
            fail()
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Truth tables and simplest forms.

    fun variables(b: Bool): List<String> {
        val out = sortedSetOf<String>()
        fun walk(x: Bool) {
            when (x) {
                is Bool.Var -> out += x.name
                is Bool.Const -> {}
                is Bool.Not -> walk(x.a)
                is Bool.And -> x.parts.forEach { walk(it) }
                is Bool.Or -> x.parts.forEach { walk(it) }
                is Bool.Xor -> { walk(x.a); walk(x.b) }
                is Bool.Implies -> { walk(x.a); walk(x.b) }
                is Bool.Iff -> { walk(x.a); walk(x.b) }
            }
        }
        walk(b)
        return out.toList()
    }

    fun eval(b: Bool, at: Map<String, Boolean>): Boolean = when (b) {
        is Bool.Var -> at.getValue(b.name)
        is Bool.Const -> b.value
        is Bool.Not -> !eval(b.a, at)
        is Bool.And -> b.parts.all { eval(it, at) }
        is Bool.Or -> b.parts.any { eval(it, at) }
        is Bool.Xor -> eval(b.a, at) != eval(b.b, at)
        is Bool.Implies -> !eval(b.a, at) || eval(b.b, at)
        is Bool.Iff -> eval(b.a, at) == eval(b.b, at)
    }

    /** The rows in order: the first letter is the highest bit, false before true. */
    fun table(b: Bool, names: List<String>): List<Boolean> {
        if (names.size > 12) throw CalcException(Failure.TOO_LARGE)
        return (0 until (1 shl names.size)).map { row -> eval(b, at(names, row)) }
    }

    fun at(names: List<String>, row: Int): Map<String, Boolean> =
        names.withIndex().associate { (k, n) -> n to ((row shr (names.size - 1 - k)) and 1 == 1) }

    /** A product of literals: for each letter true, false, or absent. */
    private data class Implicant(val values: Int, val mask: Int) {
        fun covers(row: Int) = row and mask == values
        fun literals() = Integer.bitCount(mask)
    }

    /** The simplest sum of products that has [values] as its table (fewest products, then fewest letters). */
    fun minimalDnf(values: List<Boolean>, names: List<String>): Bool {
        val n = names.size
        val full = (1 shl n) - 1
        val ones = values.indices.filter { values[it] }
        if (ones.isEmpty()) return Bool.Const(false)
        if (ones.size == values.size) return Bool.Const(true)
        // Prime implicants by combining terms that differ in one letter.
        var current = ones.map { Implicant(it, full) }.toSet()
        val primes = LinkedHashSet<Implicant>()
        while (current.isNotEmpty()) {
            Budget.check()
            val used = HashSet<Implicant>()
            val next = LinkedHashSet<Implicant>()
            val list = current.toList()
            for (a in list.indices) for (b in a + 1 until list.size) {
                val x = list[a]
                val y = list[b]
                if (x.mask != y.mask) continue
                val diff = x.values xor y.values
                if (Integer.bitCount(diff) != 1) continue
                next += Implicant(x.values and diff.inv(), x.mask and diff.inv())
                used += x
                used += y
            }
            primes += current.filter { it !in used }
            current = next
        }
        val cover = cover(primes.toList(), ones)
        val terms = cover.sortedWith(compareBy({ -it.mask }, { it.values })).map { imp ->
            val lits = (0 until n).filter { imp.mask shr (n - 1 - it) and 1 == 1 }.map { k ->
                val v = Bool.Var(names[k])
                if (imp.values shr (n - 1 - k) and 1 == 1) v else Bool.Not(v)
            }
            if (lits.size == 1) lits[0] else Bool.And(lits)
        }
        return if (terms.size == 1) terms[0] else Bool.Or(terms)
    }

    /** The fewest prime implicants covering [ones]: essential ones first, then Petrick's method (a greedy cover beyond its reach). */
    private fun cover(primes: List<Implicant>, ones: List<Int>): List<Implicant> {
        val chosen = LinkedHashSet<Implicant>()
        var left = ones.toSet()
        for (m in ones) {
            val by = primes.filter { it.covers(m) }
            if (by.size == 1) chosen += by[0]
        }
        left = left.filter { m -> chosen.none { it.covers(m) } }.toSet()
        if (left.isEmpty()) return chosen.toList()
        val rest = primes.filter { it !in chosen && left.any { m -> it.covers(m) } }
        if (rest.size <= 20) {
            // Every subset by size, the cheapest that covers.
            var best: List<Implicant>? = null
            var bestCost = Int.MAX_VALUE to Int.MAX_VALUE
            for (mask in 1 until (1 shl rest.size)) {
                if (mask and 0xFF == 0) Budget.check()
                val pick = rest.indices.filter { mask shr it and 1 == 1 }.map { rest[it] }
                if (!left.all { m -> pick.any { it.covers(m) } }) continue
                val cost = pick.size to pick.sumOf { it.literals() }
                if (cost.first < bestCost.first || cost.first == bestCost.first && cost.second < bestCost.second) { best = pick; bestCost = cost }
            }
            return chosen.toList() + best!!
        }
        val out = chosen.toMutableList()
        while (left.isNotEmpty()) {
            val p = rest.maxByOrNull { imp -> left.count { imp.covers(it) } * 100 - imp.literals() }!!
            out += p
            left = left.filter { !p.covers(it) }.toSet()
        }
        return out
    }

    /** The simplest product of sums: the dual of the simplest sum of products of the negation. */
    fun minimalCnf(values: List<Boolean>, names: List<String>): Bool = negate(minimalDnf(values.map { !it }, names))

    private fun negate(b: Bool): Bool = when (b) {
        is Bool.Const -> Bool.Const(!b.value)
        is Bool.Var -> Bool.Not(b)
        is Bool.Not -> b.a
        is Bool.And -> Bool.Or(b.parts.map { negate(it) }).let { if (it.parts.size == 1) it.parts[0] else it }
        is Bool.Or -> Bool.And(b.parts.map { negate(it) }).let { if (it.parts.size == 1) it.parts[0] else it }
        else -> Bool.Not(b)
    }

    /** A formula's table and simplest forms, each checked against the table; with a second formula, whether they agree. */
    class Analysis(val sets: Boolean, val names: List<String>, val formula: Bool, val values: List<Boolean>, val dnf: Bool, val cnf: Bool,
        val other: Bool?, val otherValues: List<Boolean>?) {
        val tautology get() = values.all { it }
        val contradiction get() = values.none { it }
        val equivalent: Boolean? get() = otherValues?.let { it == values }
    }

    fun analyse(r: Reading.Formula): Analysis {
        val names = (variables(r.left) + (r.right?.let { variables(it) } ?: emptyList())).distinct().sorted()
        val values = table(r.left, names)
        val otherValues = r.right?.let { table(it, names) }
        val dnf = minimalDnf(values, names)
        val cnf = minimalCnf(values, names)
        if (table(dnf, names) != values || table(cnf, names) != values) throw CalcException(Failure.UNSOLVED)
        return Analysis(r.sets, names, r.left, values, dnf, cnf, r.right, otherValues)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Written out.

    private fun precedence(b: Bool): Int = when (b) {
        is Bool.Iff -> 1
        is Bool.Implies -> 2
        is Bool.Or, is Bool.Xor -> 3
        is Bool.And -> 4
        else -> 5
    }

    fun latex(b: Bool, sets: Boolean): String = write(b, sets, true)

    fun text(b: Bool, sets: Boolean): String = write(b, sets, false)

    private fun write(b: Bool, sets: Boolean, latex: Boolean): String {
        // Brackets around every part that is another operation ((p ∧ q) ∨ r), and where a part binds less tightly.
        fun inner(x: Bool, outer: Int): String = write(x, sets, latex).let {
            val p = precedence(x)
            if (p < outer || (p < 5 && x::class != b::class && !(b is Bool.Implies && x is Bool.Implies))) (if (latex) "\\left($it\\right)" else "($it)") else it
        }
        fun op(l: String, t: String) = if (latex) " $l " else " $t "
        return when (b) {
            is Bool.Var -> b.name
            is Bool.Const -> if (sets) {
                if (b.value) (if (latex) "U" else "U") else (if (latex) "\\emptyset" else "∅")
            } else if (b.value) (if (latex) "1" else "1") else "0"
            is Bool.Not -> if (sets) {
                val a = write(b.a, sets, latex)
                if (latex) "\\overline{$a}" else (if (b.a is Bool.Var) "$a'" else "($a)'")
            } else (if (latex) "\\neg " else "¬") + inner(b.a, 5)
            is Bool.And -> b.parts.joinToString(if (sets) op("\\cap", "∩") else op("\\land", "∧")) { inner(it, 4) }
            is Bool.Or -> b.parts.joinToString(if (sets) op("\\cup", "∪") else op("\\lor", "∨")) { inner(it, 3) }
            is Bool.Xor -> inner(b.a, 3) + (if (sets) op("\\triangle", "△") else op("\\oplus", "⊕")) + inner(b.b, 3)
            is Bool.Implies -> inner(b.a, 3) + op("\\Rightarrow", "⇒") + inner(b.b, 2)
            is Bool.Iff -> inner(b.a, 2) + op("\\Leftrightarrow", "⇔") + inner(b.b, 2)
        }
    }
}

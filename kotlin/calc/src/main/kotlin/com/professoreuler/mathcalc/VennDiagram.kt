package com.professoreuler.mathcalc

/**
 * A Venn diagram of a formula of sets in one, two or three sets (∪ ∩ ∖ △ and complements, as [Logic] reads them; a formula of
 * logic draws the same way, a letter as the set where it holds): the 2, 4 or 8 regions of the diagram, each named by the sets it
 * lies in and those it lies outside, and whether it belongs to the formula's set. Worked out twice: once row by row from the
 * formula's membership table ([Logic.table]), once by doing the formula's operations on the regions themselves (each set the
 * regions it covers, ∪ their union, ∩ their common ones, a complement the others); the two must agree.
 */
object VennDiagram {
    /**
     * A region: [inside] tells for each of the diagram's sets (in [Diagram.sets]' order) whether the region lies in it, [included]
     * whether it belongs to the formula's set; [latex] and [text] name it as the intersection of each set or its complement
     * (`A \cap \overline{B}`, `A ∩ B'`).
     */
    class Region(val inside: List<Boolean>, val included: Boolean, val latex: String, val text: String)

    /**
     * The diagram: its [sets] (one circle each, in alphabetical order), the [formula] as read, its [regions] in the order of the
     * membership table (the first set's membership the highest bit, outside before inside: for A, B the regions A′∩B′, A′∩B, A∩B′,
     * A∩B), and the formula's simplest form as a union of intersections ([simplest]).
     */
    class Diagram(val sets: List<String>, val formula: Logic.Bool, val regions: List<Region>, val simplest: Logic.Bool, val isSets: Boolean)

    /** At most this many sets are drawn. */
    const val MOST_SETS = 3

    /** The diagram of [reading]: a formula of one to three letters with nothing on the other side of an =. */
    fun of(reading: Logic.Reading.Formula): Diagram {
        if (reading.right != null) throw CalcException(Failure.UNREADABLE)
        val formula = reading.left
        val names = Logic.variables(formula)
        if (names.isEmpty()) throw CalcException(Failure.UNREADABLE)
        if (names.size > MOST_SETS) throw CalcException(Failure.TOO_LARGE)
        val table = Logic.table(formula, names)
        // The same by the operations of sets on the regions: region r is bit r of a mask.
        val count = 1 shl names.size
        val universe = (1 shl count) - 1
        fun covered(k: Int): Int = (0 until count).filter { r -> (r shr (names.size - 1 - k)) and 1 == 1 }.fold(0) { m, r -> m or (1 shl r) }
        val masks = names.indices.associate { names[it] to covered(it) }
        fun set(b: Logic.Bool): Int = when (b) {
            is Logic.Bool.Var -> masks.getValue(b.name)
            is Logic.Bool.Const -> if (b.value) universe else 0
            is Logic.Bool.Not -> universe xor set(b.a)
            is Logic.Bool.And -> b.parts.fold(universe) { m, p -> m and set(p) }
            is Logic.Bool.Or -> b.parts.fold(0) { m, p -> m or set(p) }
            is Logic.Bool.Xor -> set(b.a) xor set(b.b)
            is Logic.Bool.Implies -> (universe xor set(b.a)) or set(b.b)
            is Logic.Bool.Iff -> universe xor (set(b.a) xor set(b.b))
        }
        val mask = set(formula)
        if ((0 until count).any { r -> table[r] != ((mask shr r) and 1 == 1) }) throw CalcException(Failure.UNSOLVED)
        val regions = (0 until count).map { r ->
            val inside = names.indices.map { k -> (r shr (names.size - 1 - k)) and 1 == 1 }
            Region(inside, table[r], label(names, inside, latex = true), label(names, inside, latex = false))
        }
        val simplest = Logic.minimalDnf(table, names)
        if (Logic.table(simplest, names) != table) throw CalcException(Failure.UNSOLVED)
        return Diagram(names, formula, regions, simplest, reading.sets)
    }

    private fun label(names: List<String>, inside: List<Boolean>, latex: Boolean): String = names.indices.joinToString(if (latex) " \\cap " else " ∩ ") { k ->
        val n = names[k]
        when {
            inside[k] -> n
            latex -> "\\overline{$n}"
            else -> "$n'"
        }
    }
}

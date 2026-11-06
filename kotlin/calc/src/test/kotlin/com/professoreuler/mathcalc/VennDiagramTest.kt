package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * venn(…): the regions of each diagram against sets of numbers worked out with Kotlin's own set operations (an element lies in
 * the region of the sets it belongs to, so the region is in the formula's set exactly where its elements are), textbook counts,
 * De Morgan's laws, and the region labels.
 */
class VennDiagramTest {
    private fun diagram(text: String): VennDiagram.Diagram {
        val o = Reports.of(TextInput.read(text)).result.outcome
        return (o as? Outcome.Venn)?.diagram ?: throw AssertionError("$text: $o")
    }

    private fun answer(text: String): String {
        val r = Reports.of(TextInput.read(text)).result
        return if (r.outcome is Outcome.Failed) "FAILED ${(r.outcome as Outcome.Failed).failure}" else answerOf(r)
    }

    // U = 1 … 8, each number in another combination of A, B and C.
    private val universe = (1..8).toSet()
    private val a = setOf(2, 4, 6, 8)
    private val b = setOf(3, 4, 7, 8)
    private val c = setOf(5, 6, 7, 8)

    /** The region of n in a diagram of [sets]: in each set or not. */
    private fun regionOf(n: Int, sets: List<String>) = sets.map { name -> n in mapOf("A" to a, "B" to b, "C" to c).getValue(name) }

    /** Each formula with the same formula worked out on the sets of numbers. */
    private val cases: List<Pair<String, Set<Int>>> = listOf(
        "venn(A ∪ B ∪ C)" to (a union b union c),
        "venn(A ∩ B ∩ C)" to (a intersect b intersect c),
        "venn((A ∪ B) ∖ C)" to ((a union b) - c),
        "venn(A ∩ (B ∪ C))" to (a intersect (b union c)),
        "venn((A ∩ B)')" to (universe - (a intersect b)),
        "venn(A' ∩ B' ∩ C)" to ((universe - a) intersect (universe - b) intersect c),
        "venn(A △ B △ C)" to (a.symmetric(b).symmetric(c)),
        "venn((A ∖ B) ∪ (B ∖ C) ∪ (C ∖ A))" to ((a - b) union (b - c) union (c - a)),
    )

    private fun Set<Int>.symmetric(o: Set<Int>) = (this - o) union (o - this)

    @Test
    fun theRegionsAreThoseOfTheSetsOfNumbers() {
        val wrong = ArrayList<String>()
        for ((text, set) in cases) {
            val d = diagram(text)
            // Every combination of three sets has a number in it, so every region is tested.
            for (n in universe) {
                val inside = regionOf(n, d.sets)
                val region = d.regions.single { it.inside == inside }
                if (region.included != (n in set)) wrong += "$text: region ${region.text} (of $n) ${region.included}"
            }
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test
    fun countsAndLabels() {
        val ab = diagram("venn(A ∪ B)")
        assertEquals(listOf("A", "B"), ab.sets)
        assertEquals(4, ab.regions.size)
        // In the order of the membership table: A′∩B′, A′∩B, A∩B′, A∩B.
        assertEquals(listOf("A' ∩ B'", "A' ∩ B", "A ∩ B'", "A ∩ B"), ab.regions.map { it.text })
        assertEquals(listOf(false, true, true, true), ab.regions.map { it.included })
        assertEquals("\\overline{A} \\cap B", ab.regions[1].latex)
        assertEquals(7, diagram("venn(A ∪ B ∪ C)").regions.count { it.included })
        assertEquals(1, diagram("venn(A ∩ B ∩ C)").regions.count { it.included })
        // A single set and its complement.
        assertEquals("A: A' simplest A'", answer("venn(A')"))
        assertEquals("AB: A ∩ B' simplest A ∩ B'", answer("venn(A ∖ B)"))
        assertEquals("AB: A' ∩ B, A ∩ B'", answer("venn(A △ B)").substringBefore(" simplest"))
    }

    @Test
    fun deMorgan() {
        assertEquals(diagram("venn((A ∪ B)')").regions.map { it.included }, diagram("venn(A' ∩ B')").regions.map { it.included })
        assertEquals(diagram("venn((A ∩ B ∩ C)')").regions.map { it.included }, diagram("venn(A' ∪ B' ∪ C')").regions.map { it.included })
    }

    @Test
    fun whatIsRefusedAndOffered() {
        assertEquals("FAILED TOO_LARGE", answer("venn(A ∪ B ∪ C ∪ D)"))
        assertEquals("FAILED UNREADABLE", answer("venn(A ∪ B = B ∪ A)"))
        assertEquals("FAILED UNREADABLE", answer("venn(x^2 + 1)"))
        // A formula of sets typed alone offers its diagram beside its table.
        val r = Reports.of(TextInput.read("(A ∪ B) ∩ C"))
        assertTrue(r.panels.any { it.choice?.mode == Mode.VENN })
        val venn = Reports.compute(r, r.panels.first { it.choice?.mode == Mode.VENN }) as PanelResult.Calculated
        assertEquals("ABC: A' ∩ B ∩ C, A ∩ B' ∩ C, A ∩ B ∩ C simplest (A ∩ C) ∪ (B ∩ C)", answerOf(venn.calculation))
        assertTrue(r.panels.none { it.kind == PanelKind.DIGITS })
    }
}

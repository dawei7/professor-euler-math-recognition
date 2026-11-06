package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Calls whose steps reach every step of the graph functions ([StepsTest.everyKindOfStepIsReached]). */
internal val NETWORK_STEPS: List<Pair<String, Choice?>> = listOf(
    "dijkstra((1, 2, 4), (1, 3, 1), (3, 2, 2), (2, 4, 5), 1, 4)",
    "bellmanford((1 -> 2, 4), (1 -> 3, 5), (3 -> 2, -3), (2 -> 4, 2), 1, 4)",
    "bellmanford((1 -> 2, 1), (2 -> 3, -2), (3 -> 1, -1), 1)",
    "kruskal((1, 2, 4), (1, 3, 1), (2, 3, 2), (2, 4, 5))",
    "prim((1, 2, 4), (1, 3, 1), (2, 3, 2), (2, 4, 5))",
    "toposort((1 -> 2), (1 -> 3), (3 -> 2))",
    "toposort((1 -> 2), (2 -> 3), (3 -> 2))",
    "maxflow((s -> a, 3), (a -> t, 2), (s -> t, 1), s, t)",
    "walks((1, 2), (2, 3), 3, 1, 3)",
).map { TextInput.read(it).let { t -> t.latex to t.choice } }

/**
 * The graph functions, each answer against a value known independently: textbook examples (Cormen, Leiserson, Rivest and
 * Stein), and seeded random graphs checked against what the test computes another way (Floyd–Warshall distances, every spanning
 * tree, every cut, every colouring, the walks counted one by one, triangulations that are planar by construction).
 */
class NetworksTest {
    private fun outcome(text: String): Outcome = Reports.of(TextInput.read(text)).result.outcome
    private fun answer(text: String): String = answerOf(Reports.of(TextInput.read(text)).result)

    private class E(val u: Int, val v: Int, val w: Int)

    private fun call(name: String, edges: List<E>, directed: Boolean, weighted: Boolean, vararg rest: Any): String =
        "$name(" + (edges.map { e -> "(" + e.u + (if (directed) " -> " else ", ") + e.v + (if (weighted) ", " + e.w else "") + ")" } +
            rest.map { it.toString() }).joinToString(", ") + ")"

    private fun random(r: Random, n: Int, m: Int, low: Int, high: Int, loops: Boolean = false): List<E> =
        List(m) { E(r.nextInt(1, n + 1), r.nextInt(1, n + 1), r.nextInt(low, high + 1)) }.filter { loops || it.u != it.v }

    private fun rational(e: Expr?): Rational? = e?.let { com.professoreuler.mathcalc.rational(it) }

    // -----------------------------------------------------------------------------------------------------------------
    // Shortest paths.

    @Test
    fun textbookShortestPaths() {
        // CLRS figure 24.6 (Dijkstra) and figure 24.4 (Bellman–Ford).
        val dijkstra = "dijkstra((s -> t, 10), (s -> y, 5), (t -> x, 1), (t -> y, 2), (y -> t, 3), (y -> x, 9), (y -> z, 2), (x -> z, 4), " +
            "(z -> x, 6), (z -> s, 7), s, x)"
        assertEquals("d s: 0, t: 8, x: 9, y: 5, z: 7 | s → y → t → x length 9", answer(dijkstra))
        val bellman = "bellmanford((s -> t, 6), (s -> y, 7), (t -> x, 5), (t -> y, 8), (t -> z, -4), (x -> t, -2), (y -> x, -3), " +
            "(y -> z, 9), (z -> s, 2), (z -> x, 7), s, z)"
        assertEquals("d s: 0, t: 2, x: 4, y: 7, z: -2 | s → y → x → t → z length -2", answer(bellman))
        // Undirected, a path's length the number of its edges where no weights are given.
        assertEquals("d 1: 0, 2: 1, 3: 2, 4: 1 | 1 → 4 length 1", answer("dijkstra((1, 2), (2, 3), (3, 4), (4, 1), 1, 4)"))
        assertEquals("d 1: 0, 2: 2.5, 3: 3.75 | 1 → 2 → 3 length 3.75", answer("dijkstra((1, 2, 2.5), (2, 3, 1.25), 1, 3)"))
        assertEquals("d 1: 0, 2: 1/2, 3: 5/6 | 1 → 2 → 3 length 5/6", answer("dijkstra((1, 2, 1/2), (2, 3, 1/3), 1, 3)"))
        assertEquals("d 1: 0, 2: 4, 3: ∞ | no path", answer("dijkstra((1 -> 2, 4), (3 -> 2, 1), 1, 3)"))
    }

    /** Floyd–Warshall distances, with null for unreachable; and whether a vertex lies on a negative cycle reachable from 1. */
    private fun floyd(n: Int, edges: List<E>, directed: Boolean): Array<Array<Long?>> {
        val d = Array(n + 1) { i -> Array<Long?>(n + 1) { j -> if (i == j) 0L else null } }
        for (e in edges) {
            for ((a, b) in if (directed) listOf(e.u to e.v) else listOf(e.u to e.v, e.v to e.u)) {
                if (d[a][b] == null || e.w < d[a][b]!!) d[a][b] = e.w.toLong()
            }
        }
        for (k in 1..n) for (i in 1..n) for (j in 1..n) {
            val x = d[i][k] ?: continue
            val y = d[k][j] ?: continue
            if (d[i][j] == null || x + y < d[i][j]!!) d[i][j] = x + y
        }
        return d
    }

    @Test
    fun distancesAgreeWithFloydWarshall() {
        val r = Random(2026)
        var checked = 0
        var negativeCycles = 0
        repeat(150) { trial ->
            val n = r.nextInt(2, 9)
            val directed = trial % 3 != 0
            val negative = directed && trial % 2 == 0
            val edges = random(r, n, r.nextInt(1, 3 * n), if (negative) -3 else 0, 9)
            if (edges.isEmpty() || edges.none { it.u == 1 || it.v == 1 }) return@repeat
            val d = floyd(n, edges, directed)
            val onNegative = (1..n).any { v -> d[v][v]!! < 0 && d[1][v] != null }
            for (method in listOf("dijkstra", "bellmanford")) {
                if (method == "dijkstra" && negative && edges.any { it.w < 0 }) {
                    assertEquals("NEGATIVE_WEIGHT", answer(call(method, edges, directed, true, 1)))
                    continue
                }
                val o = outcome(call(method, edges, directed, true, 1)) as Outcome.ShortestPaths
                val p = o.paths
                if (onNegative) {
                    negativeCycles++
                    val cycle = p.negativeCycle!!.map { it.toInt() }
                    // The cycle's edges are the graph's, and its weight is negative: the lightest edges along it weigh at most the
                    // weight it says, which is below 0.
                    var lightest = 0L
                    for (k in cycle.indices) {
                        val (a, b) = cycle[k] to cycle[(k + 1) % cycle.size]
                        lightest += edges.filter { it.u == a && it.v == b }.minOf { it.w }.toLong()
                    }
                    val said = rational(p.cycleWeight)!!
                    assertTrue(said.signum < 0 && Rational.of(lightest) <= said)
                } else {
                    for ((k, name) in p.vertices.withIndex()) {
                        val expected = d[1][name.toInt()]
                        assertEquals("$method ${call(method, edges, directed, true, 1)} at $name", expected, rational(p.distances[k])?.num?.toLong())
                        checked++
                    }
                }
            }
        }
        println("$checked distances, $negativeCycles negative cycles")
        assertTrue(checked > 500 && negativeCycles > 5)
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Spanning trees.

    @Test
    fun textbookSpanningTree() {
        // CLRS figure 23.1: a minimum spanning tree of weight 37.
        val edges = "(a, b, 4), (a, h, 8), (b, c, 8), (b, h, 11), (c, d, 7), (c, f, 4), (c, i, 2), (d, e, 9), (d, f, 14), (e, f, 10), " +
            "(f, g, 2), (g, h, 1), (g, i, 6), (h, i, 7)"
        for (f in listOf("kruskal($edges)", "prim($edges)", "prim($edges, e)")) {
            val t = (outcome(f) as Outcome.SpanningTree).tree
            assertEquals(f, Rational.of(37), rational(t.weight))
            assertEquals(8, t.edges.size)
        }
        assertEquals("NOT_CONNECTED", answer("kruskal((1, 2, 1), (3, 4, 1))"))
        assertEquals("ARGUMENTS", answer("prim((1 -> 2, 1), (2 -> 3, 1))"))
    }

    @Test
    fun spanningTreesAgreeWithEveryTree() {
        val r = Random(37)
        var checked = 0
        repeat(120) {
            val n = r.nextInt(2, 7)
            val edges = random(r, n, r.nextInt(n - 1, 10), -5, 9)
            if (edges.isEmpty()) return@repeat
            // The lightest spanning tree among all sets of n − 1 edges.
            var best: Long? = null
            val m = edges.size
            fun choose(start: Int, picked: List<Int>) {
                if (picked.size == n - 1) {
                    val parent = IntArray(n + 1) { it }
                    fun find(x: Int): Int { var y = x; while (parent[y] != y) y = parent[y]; return y }
                    var joined = 0
                    for (k in picked) { val (a, b) = find(edges[k].u) to find(edges[k].v); if (a != b) { parent[a] = b; joined++ } }
                    if (joined == n - 1) {
                        val w = picked.sumOf { edges[it].w.toLong() }
                        if (best == null || w < best!!) best = w
                    }
                    return
                }
                for (k in start until m) choose(k + 1, picked + k)
            }
            choose(0, emptyList())
            val vertices = edges.flatMap { listOf(it.u, it.v) }.toSet().size
            for (f in listOf("kruskal", "prim")) {
                // A number 1..n on no edge is no vertex of the graph: only graphs on all n are compared.
                if (vertices < n) continue
                val text = call(f, edges, false, true)
                val o = outcome(text)
                if (best == null) {
                    assertEquals(text, "NOT_CONNECTED", (o as Outcome.Failed).failure.name)
                } else {
                    assertEquals(text, Rational.of(best!!), rational((o as Outcome.SpanningTree).tree.weight))
                    checked++
                }
            }
        }
        println("$checked trees")
        assertTrue(checked > 80)
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Flows.

    @Test
    fun textbookFlow() {
        // CLRS figure 26.1: a maximum flow of value 23.
        val f = (outcome("maxflow((s -> a, 16), (s -> b, 13), (a -> c, 12), (b -> a, 4), (b -> d, 14), (c -> b, 9), (c -> t, 20), " +
            "(d -> c, 7), (d -> t, 4), s, t)") as Outcome.MaxFlow).flow
        assertEquals(Rational.of(23), rational(f.value))
        assertEquals(listOf("a", "b", "d", "s"), f.cut)
        assertEquals("ARGUMENTS", answer("maxflow((s, a, 1), (a, t, 1), s, t)"))
        assertEquals("NEGATIVE_WEIGHT", answer("maxflow((s -> a, -1), (a -> t, 1), s, t)"))
    }

    @Test
    fun flowsAgreeWithEveryCut() {
        val r = Random(26)
        var checked = 0
        repeat(150) {
            val n = r.nextInt(2, 8)
            val edges = random(r, n, r.nextInt(1, 14), 0, 9)
            val vertices = edges.flatMap { listOf(it.u, it.v) }.toSet()
            if (1 !in vertices || n !in vertices || n == 1) return@repeat
            // The least capacity of a cut with 1 on one side and n on the other.
            var least = Long.MAX_VALUE
            for (mask in 0 until (1 shl n)) {
                if (mask and 1 == 0 || mask and (1 shl (n - 1)) != 0) continue
                val cut = edges.filter { mask and (1 shl (it.u - 1)) != 0 && mask and (1 shl (it.v - 1)) == 0 }.sumOf { it.w.toLong() }
                least = minOf(least, cut)
            }
            val f = (outcome(call("maxflow", edges, true, true, 1, n)) as Outcome.MaxFlow).flow
            assertEquals(Rational.of(least), rational(f.value))
            checked++
        }
        assertTrue(checked > 100)
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Topological orders.

    @Test
    fun topologicalOrdersAreOrdersAndCyclesAreCycles() {
        val r = Random(10)
        var orders = 0
        var cycles = 0
        repeat(200) { trial ->
            val n = r.nextInt(2, 9)
            // Half of them acyclic: every edge from a smaller vertex to a larger one.
            val edges = random(r, n, r.nextInt(1, 12), 1, 1).map { if (trial % 2 == 0 && it.u > it.v) E(it.v, it.u, 1) else it }
            if (edges.isEmpty()) return@repeat
            val o = (outcome(call("toposort", edges, true, false)) as Outcome.TopologicalOrder).order
            // Whether the graph has a cycle, by depth-first search.
            val state = IntArray(n + 1)
            fun cyclic(u: Int): Boolean {
                state[u] = 1
                for (e in edges.filter { it.u == u }) {
                    if (state[e.v] == 1) return true
                    if (state[e.v] == 0 && cyclic(e.v)) return true
                }
                state[u] = 2
                return false
            }
            val hasCycle = (1..n).any { state[it] == 0 && cyclic(it) }
            if (hasCycle) {
                val c = o.cycle!!.map { it.toInt() }
                for (k in c.indices) assertTrue(edges.any { it.u == c[k] && it.v == c[(k + 1) % c.size] })
                cycles++
            } else {
                val position = o.order!!.withIndex().associate { (k, v) -> v.toInt() to k }
                for (e in edges) assertTrue(position.getValue(e.u) < position.getValue(e.v))
                orders++
            }
        }
        println("$orders orders, $cycles cycles")
        assertTrue(orders > 50 && cycles > 20)
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Walks.

    @Test
    fun walksCountedByFormulaAndOneByOne() {
        // In K₄: closed walks of length k from a vertex ((n − 1)^k + (n − 1)(−1)^k)/n, walks to another ((n − 1)^k − (−1)^k)/n.
        val k4 = "(1, 2), (1, 3), (1, 4), (2, 3), (2, 4), (3, 4)"
        assertEquals("6", answer("walks($k4, 3, 1, 1)"))
        assertEquals("7", answer("walks($k4, 3, 1, 2)"))
        assertEquals("21", answer("walks($k4, 4, 2, 2)"))
        assertEquals("[[0, 1], [1, 0]]", answer("walks([[0, 1], [1, 0]], 5)"))
        assertEquals("TOO_LARGE", answer("walks((1, 2), 101)"))
        val r = Random(4)
        repeat(60) { trial ->
            val n = r.nextInt(1, 6)
            val directed = trial % 2 == 0
            val edges = random(r, n, r.nextInt(1, 8), 1, 1, loops = true)
            if (edges.isEmpty()) return@repeat
            val names = edges.flatMap { listOf(it.u, it.v) }.toSortedSet().toList()
            val k = r.nextInt(0, 6)
            val (a, b) = names.random(r) to names.random(r)
            // The walks of length k from a to b, one by one: each step along an edge (an undirected one either way, a loop once).
            val steps = edges.flatMap { e -> if (directed || e.u == e.v) listOf(e.u to e.v) else listOf(e.u to e.v, e.v to e.u) }
            fun count(at: Int, left: Int): Long = if (left == 0) (if (at == b) 1L else 0L) else steps.filter { it.first == at }.sumOf { count(it.second, left - 1) }
            assertEquals(call("walks", edges, directed, false, k, a, b), "${count(a, k)}", answer(call("walks", edges, directed, false, k, a, b)))
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Facts.

    private fun facts(text: String): Networks.Facts = (outcome(text) as Outcome.GraphFacts).facts

    private val petersen = "graph((1, 2), (2, 3), (3, 4), (4, 5), (5, 1), (1, 6), (2, 7), (3, 8), (4, 9), (5, 10), (6, 8), (8, 10), (10, 7), (7, 9), (9, 6))"

    @Test
    fun knownGraphs() {
        val p = facts(petersen)
        assertEquals(List(10) { 3 }, p.degrees)
        assertEquals(3, p.colouring!!.number)
        assertEquals(false, p.sides!!.bipartite)
        assertEquals(5, p.sides!!.oddCycle.size)
        assertEquals(false, p.planarity!!.planar)
        assertEquals(Networks.EulerKind.NONE, p.euler.kind)
        val k5 = facts("graph((1, 2), (1, 3), (1, 4), (1, 5), (2, 3), (2, 4), (2, 5), (3, 4), (3, 5), (4, 5))")
        assertEquals(Networks.Kuratowski.K5, k5.planarity!!.obstruction)
        assertEquals(5, k5.colouring!!.number)
        assertEquals(Networks.EulerKind.CIRCUIT, k5.euler.kind)
        val k33 = facts("graph((1, 4), (1, 5), (1, 6), (2, 4), (2, 5), (2, 6), (3, 4), (3, 5), (3, 6))")
        assertEquals(Networks.Kuratowski.K33, k33.planarity!!.obstruction)
        assertEquals(listOf("1", "2", "3"), k33.sides!!.left)
        // The cube: bipartite, planar with 6 faces, 3-regular so no Euler trail.
        val cube = facts("graph((1, 2), (2, 3), (3, 4), (4, 1), (5, 6), (6, 7), (7, 8), (8, 5), (1, 5), (2, 6), (3, 7), (4, 8))")
        assertEquals(true, cube.sides!!.bipartite)
        assertEquals(6, cube.planarity!!.faces)
        assertEquals(2, cube.colouring!!.number)
        // The octahedron: 4-regular, an Euler circuit, χ = 3, 8 faces.
        val octahedron = facts("graph((1, 2), (1, 3), (1, 4), (1, 5), (6, 2), (6, 3), (6, 4), (6, 5), (2, 3), (3, 4), (4, 5), (5, 2))")
        assertEquals(Networks.EulerKind.CIRCUIT, octahedron.euler.kind)
        assertEquals(3, octahedron.colouring!!.number)
        assertEquals(8, octahedron.planarity!!.faces)
        // Königsberg: four vertices of odd degree, no Euler trail.
        val konigsberg = facts("graph((A, B), (A, B), (A, C), (A, C), (A, D), (B, D), (C, D))")
        assertEquals(listOf(5, 3, 3, 3), konigsberg.degrees)
        assertEquals(4, konigsberg.euler.odd.size)
        assertEquals(Networks.EulerKind.NONE, konigsberg.euler.kind)
        assertEquals(false, konigsberg.simple)
        // The Grötzsch graph: no triangle, χ = 4.
        val grotzsch = facts("graph((1, 2), (2, 3), (3, 4), (4, 5), (5, 1), (1, 7), (2, 6), (2, 8), (3, 7), (3, 9), (4, 8), (4, 10), (5, 9), " +
            "(5, 6), (1, 10), (6, 11), (7, 11), (8, 11), (9, 11), (10, 11))")
        assertEquals(4, grotzsch.colouring!!.number)
        assertEquals(null, grotzsch.colouring!!.clique)
        // A tree and a forest with an isolated vertex.
        assertEquals(true, facts("graph((1, 2), (1, 3), (3, 4), (3, 5))").tree)
        val forest = facts("graph((1, 2), (3, 4), 5)")
        assertEquals(false, forest.tree)
        assertEquals(true, forest.forest)
        assertEquals(3, forest.components.size)
        // An odd cycle of 19 vertices: χ = 3 from the odd cycle, though the exact count stops at 16 vertices.
        assertEquals(3, facts("graph(" + (1..19).joinToString(", ") { "($it, ${it % 19 + 1})" } + ")").colouring!!.number)
        // A directed graph: two strong components, an Euler path, a cycle.
        val d = facts("graph((1 -> 2), (2 -> 3), (3 -> 1), (3 -> 4), (4 -> 5), (5 -> 4))")
        assertEquals(listOf(listOf("1", "2", "3"), listOf("4", "5")), d.strong)
        assertEquals(listOf("3", "1", "2", "3", "4", "5", "4"), d.euler.trail)
        assertEquals(listOf("1", "2", "3"), d.order!!.cycle)
        assertEquals("[[0, 1, 0, 0, 0], [0, 0, 1, 0, 0], [1, 0, 0, 1, 0], [0, 0, 0, 0, 1], [0, 0, 0, 1, 0]]",
            Format.expr(d.adjacency, Style.PLAIN, latex = false))
    }

    @Test
    fun chromaticNumbersAgreeWithEveryColouring() {
        val r = Random(99)
        var checked = 0
        repeat(150) {
            val n = r.nextInt(1, 8)
            val edges = random(r, n, r.nextInt(0, 14), 1, 1)
            if (edges.isEmpty()) return@repeat
            val names = edges.flatMap { listOf(it.u, it.v) }.toSortedSet().toList()
            val index = names.withIndex().associate { (k, v) -> v to k }
            // The fewest colours of all assignments of colours to the vertices.
            fun colourable(k: Int): Boolean {
                val colour = IntArray(names.size)
                fun go(at: Int): Boolean {
                    if (at == names.size) return edges.all { colour[index.getValue(it.u)] != colour[index.getValue(it.v)] }
                    for (c in 0 until k) { colour[at] = c; if (go(at + 1)) return true }
                    return false
                }
                return go(0)
            }
            val chi = (1..names.size).first { colourable(it) }
            val f = facts(call("graph", edges, false, false))
            val c = f.colouring!!
            assertEquals(chi, c.number)
            for (e in edges) assertTrue(c.colours[index.getValue(e.u)] != c.colours[index.getValue(e.v)])
            checked++
        }
        assertTrue(checked > 100)
    }

    /** Triangulations, planar by how they are made (each new vertex in a triangle, joined to its corners), and one edge more. */
    @Test
    fun planarityOfTriangulations() {
        val r = Random(5)
        repeat(40) {
            val n = r.nextInt(4, 25)
            val edges = arrayListOf(1 to 2, 2 to 3, 3 to 1)
            val triangles = arrayListOf(Triple(1, 2, 3), Triple(1, 2, 3))
            for (v in 4..n) {
                val t = triangles.removeAt(r.nextInt(triangles.size))
                edges += t.first to v
                edges += t.second to v
                edges += t.third to v
                triangles += Triple(t.first, t.second, v)
                triangles += Triple(t.second, t.third, v)
                triangles += Triple(t.first, t.third, v)
            }
            val text = "graph(" + edges.joinToString(", ") { "(${it.first}, ${it.second})" } + ")"
            val p = facts(text).planarity!!
            assertTrue(text, p.planar)
            assertEquals(2 * n - 4, p.faces)
            // A maximal planar graph has 3n − 6 edges: one more between two vertices not yet joined makes it non-planar.
            val joined = edges.map { setOf(it.first, it.second) }.toSet()
            val missing = (1..n).flatMap { a -> (a + 1..n).map { b -> a to b } }.filter { setOf(it.first, it.second) !in joined }
            if (missing.isNotEmpty()) {
                val extra = missing[r.nextInt(missing.size)]
                val q = facts("graph(" + (edges + extra).joinToString(", ") { "(${it.first}, ${it.second})" } + ")").planarity!!
                assertTrue(!q.planar && q.obstruction != null && q.subgraph.isNotEmpty())
            }
        }
        // Small families: wheels and K₂,ₙ are planar, K₃,₃ less an edge too.
        for (k in 3..8) assertTrue(facts("graph(" + ((1..k).map { "($it, ${it % k + 1})" } + (1..k).map { "($it, 0)" }).joinToString(", ") + ")").planarity!!.planar)
        assertTrue(facts("graph(" + (3..9).flatMap { listOf("(1, $it)", "(2, $it)") }.joinToString(", ") + ")").planarity!!.planar)
        assertTrue(facts("graph((1, 4), (1, 5), (1, 6), (2, 4), (2, 5), (2, 6), (3, 4), (3, 5))").planarity!!.planar)
    }

    @Test
    fun eulerTrailsAndSidesFollowedEdgeByEdge() {
        val r = Random(8)
        var circuits = 0
        repeat(100) { trial ->
            val n = r.nextInt(3, 9)
            // A union of random closed walks: every degree even.
            val edges = ArrayList<E>()
            repeat(r.nextInt(1, 4)) {
                val walk = List(r.nextInt(2, 6)) { r.nextInt(1, n + 1) }
                for (k in walk.indices) if (walk[k] != walk[(k + 1) % walk.size]) edges += E(walk[k], walk[(k + 1) % walk.size], 1)
            }
            if (edges.isEmpty()) return@repeat
            val directed = trial % 2 == 0
            val f = facts(call("graph", edges, directed, false))
            if (f.components.count { c -> edges.any { it.u.toString() in c } } != 1) return@repeat
            assertEquals(Networks.EulerKind.CIRCUIT, f.euler.kind)
            // Every edge once, along the trail.
            val left = edges.map { it.u to it.v }.toMutableList()
            val t = f.euler.trail.map { it.toInt() }
            for (k in 1 until t.size) {
                val i = left.indexOfFirst { (a, b) -> (a == t[k - 1] && b == t[k]) || (!directed && a == t[k] && b == t[k - 1]) }
                assertTrue(i >= 0)
                left.removeAt(i)
            }
            assertTrue(left.isEmpty() && t.first() == t.last())
            circuits++
        }
        assertTrue(circuits > 40)
        // Bipartite graphs and odd cycles.
        repeat(100) {
            val n = r.nextInt(2, 10)
            val edges = random(r, n, r.nextInt(1, 15), 1, 1)
            if (edges.isEmpty()) return@repeat
            val s = facts(call("graph", edges, false, false)).sides!!
            if (s.bipartite) {
                for (e in edges) assertTrue((e.u.toString() in s.left) != (e.v.toString() in s.left))
            } else {
                val c = s.oddCycle.map { it.toInt() }
                assertTrue(c.size % 2 == 1 && c.toSet().size == c.size)
                for (k in c.indices) assertTrue(edges.any { setOf(it.u, it.v) == setOf(c[k], c[(k + 1) % c.size]) })
            }
        }
    }

    @Test
    fun picturesHoldEveryVertexAndEdge() {
        val f = facts(petersen)
        assertEquals(10, f.picture.vertices.size)
        assertEquals(15, f.picture.edges.size)
        assertTrue(f.picture.vertices.all { it.x in 0.0..1.0 && it.y in 0.0..1.0 })
        // The colours of the colouring are the picture's groups.
        assertEquals(f.colouring!!.colours.map { it - 1 }, f.picture.vertices.map { it.group })
        val p = (outcome("dijkstra((1, 2, 4), (1, 3, 1), (3, 2, 2), 1, 2)") as Outcome.ShortestPaths).paths
        assertEquals(listOf(false, true, true), p.picture.edges.map { it.marked })
        assertEquals(setOf("1", "2"), p.picture.vertices.filter { it.marked }.map { it.name }.toSet())
        val t = (outcome("kruskal((1, 2, 4), (1, 3, 1), (2, 3, 2))") as Outcome.SpanningTree).tree
        assertEquals(listOf(null, 1, 2), t.picture.edges.map { it.order })
        val m = (outcome("maxflow((s -> a, 3), (a -> t, 2), (s -> t, 1), s, t)") as Outcome.MaxFlow).flow
        assertEquals(listOf("2", "2", "1"), m.picture.edges.map { Format.expr(it.flow!!, Style.PLAIN, false) })
    }

    @Test
    fun callsThatDoNotFit() {
        val refused = listOf(
            "dijkstra((1, 2, 4), 5)", "dijkstra((1, 2, 4))", "dijkstra((1, 2, 4), 1, 2, 3)", "kruskal((1 -> 2, 3))", "kruskal((1, 2), 1)",
            "toposort((1, 2))", "graph((1, 2), (2 -> 3))", "graph((1, 2, 3), (2, 3))", "dijkstra((1, 2, 4); 1)", "walks((1, 2), 2.5)",
            "walks((1, 2))", "walks((1, 2), 2, 1)", "walks([[0, 1], [1, 0]], 2, 1, 3)", "maxflow((s -> t, 1), s, s)", "graph((1, 2, x))",
            "graph((1.5, 2))", "graph((ab, c))", "dijkstra(1, (1, 2, 4))", "graph((1 -> 2 -> 3))", "prim((1, 2, 1), 1, 2)",
        )
        for (r in refused) assertEquals(r, "ARGUMENTS", answer(r))
        assertEquals("NEGATIVE_WEIGHT", answer("dijkstra((1 -> 2, -1), 1)"))
        assertEquals("NEGATIVE_WEIGHT", answer("bellmanford((1, 2, -1), 1)"))
    }

    @Test
    fun aCallIsWrittenBackAsItself() {
        val typed = TextInput.read("dijkstra((v1 -> v2, 1/2), (v_2 -> v_3, 2.5), v1, v3)")
        assertEquals("\\operatorname{dijkstra}\\left(\\left(v_{1} \\to v_{2}, 1/2\\right), \\left(v_{2} \\to v_{3}, 2.5\\right), v_{1}, v_{3}\\right)",
            CommandFunctions.latex(typed))
        assertEquals(Choice(Mode.DIJKSTRA), typed.choice)
        // The editor's LaTeX reads the same.
        assertEquals(answer("dijkstra((1, 2, 4), (2, 3, 1), 1, 3)"),
            answer("dijkstra\\left(\\left(1,2,4\\right),\\left(2,3,1\\right),1,3\\right)"))
        assertEquals(answer("toposort((1 -> 2), (2 -> 3))"), answer("toposort\\left(\\left(1\\to 2\\right),\\left(2\\to 3\\right)\\right)"))
        assertEquals(answer("toposort((1 -> 2), (2 -> 3))"), answer("toposort((1 → 2), (2 → 3))"))
    }

    @Test
    fun largeGraphsInTime() {
        val r = Random(1)
        val edges = List(1000) { E(r.nextInt(1, 201), r.nextInt(1, 201), r.nextInt(1, 50)) }.filter { it.u != it.v }
        for (f in listOf("dijkstra" to true, "kruskal" to false, "graph" to false)) {
            val start = System.nanoTime()
            val text = if (f.first == "dijkstra") call("dijkstra", edges, true, true, edges[0].u) else call(f.first, edges, false, f.first != "graph")
            val o = outcome(text)
            val ms = (System.nanoTime() - start) / 1_000_000
            assertTrue("${f.first}: $o", o !is Outcome.Failed || (o.failure == Failure.NOT_CONNECTED && f.first == "kruskal"))
            assertTrue("${f.first} took $ms ms", ms < 2500)
        }
        assertEquals("TOO_LARGE", answer("graph(" + (1..1001).joinToString(", ") { "(1, 2)" } + ")"))
    }
}

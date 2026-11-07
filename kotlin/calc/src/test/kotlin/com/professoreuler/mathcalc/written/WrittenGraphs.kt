package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.random.Random

/**
 * Graphs, against the test's own brute force: degrees and components counted, a 2-colouring by search (or the odd cycle checked
 * to be one), Euler trails by the degree rule (and the trail walked edge by edge), chromatic numbers by trying every colouring,
 * shortest paths by Floyd–Warshall in fractions, minimum spanning trees by trying every set of n − 1 edges, maximum flows as the
 * smallest cut over every set of vertices (max-flow min-cut), walks by the test's own powers of the adjacency matrix.
 */
internal object WrittenGraphs {
    private const val AREA = "graphs"

    private class E(val u: Int, val v: Int, val w: Long = 1)

    private fun name(v: Int) = "${v + 1}"

    private fun typed(fn: String, edges: List<E>, directed: Boolean, weighted: Boolean, extra: String = "") =
        "$fn(" + edges.joinToString(", ") { e -> "(" + name(e.u) + (if (directed) " -> " else ", ") + name(e.v) + (if (weighted) ", ${e.w}" else "") + ")" } + extra + ")"

    private fun Random.edges(n: Int, m: Int, directed: Boolean, lo: Long = 1, hi: Long = 9, loops: Boolean = false): List<E> {
        val out = ArrayList<E>()
        val seen = HashSet<Pair<Int, Int>>()
        var tries = 0
        while (out.size < m && tries++ < 500) {
            val u = nextInt(n); val v = nextInt(n)
            if (u == v && !loops) continue
            val key = if (directed) u to v else minOf(u, v) to maxOf(u, v)
            if (!seen.add(key)) continue
            out += E(u, v, nextLong(lo, hi + 1))
        }
        return out
    }

    /** The components of an undirected graph on [n] vertices (those in an edge, or listed), as sets of vertices. */
    private fun components(n: Int, edges: List<E>): List<Set<Int>> {
        val parent = IntArray(n) { it }
        fun find(x: Int): Int = if (parent[x] == x) x else find(parent[x]).also { parent[x] = it }
        for (e in edges) parent[find(e.u)] = find(e.v)
        return (0 until n).groupBy { find(it) }.values.map { it.toSet() }
    }

    private fun facts(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(70) { i ->
            val n = r.int(3, 7)
            val m = r.int(n - 1, minOf(n * (n - 1) / 2, n + 4))
            val edges = r.edges(n, m, directed = false)
            // Every vertex in the graph: those in no edge listed on their own.
            val used = edges.flatMap { listOf(it.u, it.v) }.toSet()
            val lonely = (0 until n).filter { it !in used }
            val call = "graph(" + (edges.map { "(${name(it.u)}, ${name(it.v)})" } + lonely.map { name(it) }).joinToString(", ") + ")"
            val degree = IntArray(n).also { d -> edges.forEach { d[it.u]++; d[it.v]++ } }
            val comps = components(n, edges)
            // A 2-colouring by search.
            val colour = IntArray(n) { -1 }
            var bipartite = true
            for (s in 0 until n) if (colour[s] < 0) {
                colour[s] = 0
                val stack = ArrayDeque(listOf(s))
                while (stack.isNotEmpty()) {
                    val x = stack.removeLast()
                    for (e in edges) {
                        val y = if (e.u == x) e.v else if (e.v == x) e.u else continue
                        if (colour[y] < 0) { colour[y] = 1 - colour[x]; stack += y } else if (colour[y] == colour[x]) bipartite = false
                    }
                }
            }
            // χ by trying every colouring with 1, 2, … colours.
            fun colourable(k: Int): Boolean {
                val c = IntArray(n)
                fun go(v: Int): Boolean {
                    if (v == n) return true
                    for (x in 0 until k) {
                        if (edges.any { (it.u == v && it.v < v && c[it.v] == x) || (it.v == v && it.u < v && c[it.u] == x) }) continue
                        c[v] = x
                        if (go(v + 1)) return true
                    }
                    return false
                }
                return go(0)
            }
            val chi = (1..n).first { colourable(it) }
            val odd = (0 until n).count { degree[it] % 2 == 1 }
            val edgeComps = comps.count { c -> c.any { degree[it] > 0 } }
            val euler = when {
                edges.isEmpty() -> null
                edgeComps > 1 -> Networks.EulerKind.NONE
                odd == 0 -> Networks.EulerKind.CIRCUIT
                odd == 2 -> Networks.EulerKind.PATH
                else -> Networks.EulerKind.NONE
            }
            val tree = comps.size == 1 && edges.size == n - 1
            out += Task(AREA, call, "χ = $chi, ${comps.size} components", judge = { c ->
                val f = (c.outcome as? Outcome.GraphFacts)?.facts
                if (f == null || f.directed) false else {
                    val index = f.vertices.map { it.toInt() - 1 }
                    f.vertices.size == n && f.edges == edges.size && index.indices.all { k -> f.degrees[k] == degree[index[k]] } &&
                        f.components.map { cc -> cc.map { it.toInt() - 1 }.toSet() }.toSet() == comps.toSet() &&
                        f.tree == tree && f.sides?.let { s ->
                            s.bipartite == bipartite && (if (s.bipartite) {
                                val left = s.left.map { it.toInt() - 1 }.toSet(); val right = s.right.map { it.toInt() - 1 }.toSet()
                                left.intersect(right).isEmpty() && (left + right).size == n && edges.all { (it.u in left) != (it.v in left) }
                            } else {
                                // The odd cycle is one: odd, and each step an edge.
                                val cyc = s.oddCycle.map { it.toInt() - 1 }
                                cyc.size % 2 == 1 && cyc.size >= 3 && cyc.indices.all { k -> val a = cyc[k]; val b = cyc[(k + 1) % cyc.size]
                                    edges.any { (it.u == a && it.v == b) || (it.u == b && it.v == a) } }
                            })
                        } == true && (f.colouring == null || f.colouring!!.number == chi) &&
                        (euler == null || f.euler.kind == euler) && (f.euler.trail.isEmpty() || walked(f.euler.trail.map { it.toInt() - 1 }, edges, directed = false)) &&
                        f.planarity?.let { p ->
                            // Planar where it has at most four vertices, not where E > 3V − 6; a planar one's faces by Euler's formula.
                            !(n <= 4 && !p.planar) && !(n >= 3 && edges.size > 3 * n - 6 && p.planar) &&
                                (!p.planar || p.faces == edges.size - n + 1 + comps.size)
                        } != false
                }
            })
        }
        // Families with known facts: cycles (χ 2 or 3), complete graphs (χ = n, planar up to 4), stars (trees).
        for (k in 3..9) {
            val cycle = (0 until k).map { E(it, (it + 1) % k) }
            out += Task(AREA, "graph(" + cycle.joinToString(", ") { "(${name(it.u)}, ${name(it.v)})" } + ")", "C$k", judge = { c ->
                (c.outcome as? Outcome.GraphFacts)?.facts?.let { f -> f.colouring?.number == (if (k % 2 == 0) 2 else 3) && f.euler.kind == Networks.EulerKind.CIRCUIT && f.planarity?.planar == true } == true
            })
            if (k <= 7) {
                val complete = (0 until k).flatMap { a -> (a + 1 until k).map { b -> E(a, b) } }
                out += Task(AREA, "graph(" + complete.joinToString(", ") { "(${name(it.u)}, ${name(it.v)})" } + ")", "K$k", judge = { c ->
                    (c.outcome as? Outcome.GraphFacts)?.facts?.let { f -> f.colouring?.number == k && f.planarity?.planar == (k <= 4) &&
                        f.euler.kind == (if (k % 2 == 1) Networks.EulerKind.CIRCUIT else if (k == 2) Networks.EulerKind.PATH else Networks.EulerKind.NONE) } == true
                })
            }
        }
        // Directed: in- and out-degrees, strong components by reachability, the Euler rule for directed graphs.
        repeat(40) {
            val n = r.int(3, 6)
            val edges = r.edges(n, r.int(n - 1, n + 4), directed = true)
            val used = edges.flatMap { listOf(it.u, it.v) }.toSet()
            if (used.size < n) return@repeat
            val reach = Array(n) { a -> BooleanArray(n) { b -> a == b } }
            for (e in edges) reach[e.u][e.v] = true
            for (k in 0 until n) for (a in 0 until n) for (b in 0 until n) if (reach[a][k] && reach[k][b]) reach[a][b] = true
            val strong = (0 until n).groupBy { a -> (0 until n).filter { b -> reach[a][b] && reach[b][a] }.toSet() }.keys
            val ins = IntArray(n).also { d -> edges.forEach { d[it.v]++ } }
            val outs = IntArray(n).also { d -> edges.forEach { d[it.u]++ } }
            out += Task(AREA, typed("graph", edges, directed = true, weighted = false), "${strong.size} strong components", judge = { c ->
                val f = (c.outcome as? Outcome.GraphFacts)?.facts
                f != null && f.directed && f.vertices.map { it.toInt() - 1 }.let { idx ->
                    idx.indices.all { k -> f.inDegrees!![k] == ins[idx[k]] && f.outDegrees!![k] == outs[idx[k]] }
                } && f.strong?.map { s -> s.map { it.toInt() - 1 }.toSet() }?.toSet() == strong &&
                    (f.euler.trail.isEmpty() || walked(f.euler.trail.map { it.toInt() - 1 }, edges, directed = true))
            })
        }
        return out
    }

    /** Whether [trail] walks every edge exactly once, each step along an edge. */
    private fun walked(trail: List<Int>, edges: List<E>, directed: Boolean): Boolean {
        if (trail.size != edges.size + 1) return false
        val left = edges.toMutableList()
        for (k in 0 until trail.size - 1) {
            val a = trail[k]; val b = trail[k + 1]
            val e = left.firstOrNull { (it.u == a && it.v == b) || (!directed && it.u == b && it.v == a) } ?: return false
            left.remove(e)
        }
        return left.isEmpty()
    }

    /** All distances by Floyd–Warshall (null: unreachable); a negative diagonal where a negative cycle runs through a vertex. */
    private fun floyd(n: Int, edges: List<E>, directed: Boolean): Array<Array<Long?>> {
        val d = Array(n) { a -> Array<Long?>(n) { b -> if (a == b) 0L else null } }
        for (e in edges) {
            d[e.u][e.v] = minOf(d[e.u][e.v] ?: Long.MAX_VALUE, e.w)
            if (!directed) d[e.v][e.u] = minOf(d[e.v][e.u] ?: Long.MAX_VALUE, e.w)
        }
        for (k in 0 until n) for (a in 0 until n) for (b in 0 until n) {
            val x = d[a][k] ?: continue
            val y = d[k][b] ?: continue
            if (d[a][b] == null || x + y < d[a][b]!!) d[a][b] = x + y
        }
        return d
    }

    private fun paths(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(80) { i ->
            val n = r.int(3, 7)
            val directed = i % 2 == 0
            val bellman = i % 3 == 0
            val edges = r.edges(n, r.int(n, n + 6), directed = directed || bellman, lo = if (bellman) -3 else 1, hi = 9)
            val dir = directed || bellman
            val used = edges.flatMap { listOf(it.u, it.v) }.toSet()
            if (0 !in used) return@repeat
            val d = floyd(n, edges, dir)
            val s = 0
            val t = (1 until n).filter { it in used }.randomOrNull(r) ?: return@repeat
            // A negative cycle the source reaches: a vertex on a negative cycle (d[v][v] < 0) reachable from s.
            val negative = (0 until n).any { v -> d[v][v]!! < 0 && d[s][v] != null }
            if (!bellman && edges.any { it.w < 0 }) return@repeat
            val call = typed(if (bellman) "bellmanford" else "dijkstra", edges, dir, weighted = true, extra = ", ${name(s)}, ${name(t)}")
            out += Task(AREA, call, if (negative) "negative cycle" else "${d[s][t]}", judge = { c ->
                val p = (c.outcome as? Outcome.ShortestPaths)?.paths
                if (p == null) false else if (negative) {
                    val cyc = p.negativeCycle?.map { it.toInt() - 1 }
                    // The cycle is one, of negative weight.
                    cyc != null && cyc.size >= 1 && p.cycleWeight?.let { rationalOf(it) }?.let { w ->
                        w < Q.ZERO && cyc.indices.fold(Q.ZERO as Q?) { acc, k ->
                            val a = cyc[k]; val b = cyc[(k + 1) % cyc.size]
                            acc?.let { x -> edges.filter { it.u == a && it.v == b }.minOfOrNull { it.w }?.let { x + q(it) } }
                        } == w
                    } == true
                } else {
                    val idx = p.vertices.map { it.toInt() - 1 }
                    p.negativeCycle == null && idx.indices.all { k -> p.distances[k]?.let { rationalOf(it) } == d[s][idx[k]]?.let { q(it) } } &&
                        (d[s][t] == null && p.path == null || p.path != null && p.length?.let { rationalOf(it) } == q(d[s][t]!!) &&
                            p.path.map { it.toInt() - 1 }.let { path ->
                                path.first() == s && path.last() == t && path.zipWithNext().fold(0L as Long?) { acc, (a, b) ->
                                    acc?.let { x -> edges.filter { (it.u == a && it.v == b) || (!dir && it.u == b && it.v == a) }.minOfOrNull { it.w }?.let { x + it } }
                                } == d[s][t]
                            })
                }
            })
        }
        out += Task(AREA, "dijkstra((1 -> 2, -1), 1)", "negative", fails = setOf(Failure.NEGATIVE_WEIGHT)) { false }
        return out
    }

    private fun trees(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(50) { i ->
            val n = r.int(3, 6)
            // Connected: a random spanning path first, then more edges.
            val order = (0 until n).shuffled(r)
            val base = order.zipWithNext().map { (a, b) -> E(a, b, r.nextLong(1, 10)) }
            val extra = r.edges(n, r.int(1, 5), directed = false).filter { e -> base.none { (it.u == e.u && it.v == e.v) || (it.u == e.v && it.v == e.u) } }
            val edges = base + extra
            // Every set of n − 1 edges that connects the graph: the least weight.
            var best = Long.MAX_VALUE
            fun choose(start: Int, picked: List<E>) {
                if (picked.size == n - 1) {
                    if (components(n, picked).size == 1) best = minOf(best, picked.sumOf { it.w })
                    return
                }
                for (k in start until edges.size) choose(k + 1, picked + edges[k])
            }
            choose(0, emptyList())
            val prim = i % 2 == 1
            val call = typed(if (prim) "prim" else "kruskal", edges, directed = false, weighted = true, extra = if (prim && i % 4 == 1) ", ${name(r.nextInt(n))}" else "")
            out += Task(AREA, call, "weight $best", judge = { c ->
                val t = (c.outcome as? Outcome.SpanningTree)?.tree
                t != null && rationalOf(t.weight) == q(best) && t.edges.size == n - 1 &&
                    components(n, t.edges.map { E(it.from.toInt() - 1, it.to.toInt() - 1) }).size == 1 &&
                    t.edges.all { l -> edges.any { e -> ((e.u == l.from.toInt() - 1 && e.v == l.to.toInt() - 1) || (e.v == l.from.toInt() - 1 && e.u == l.to.toInt() - 1)) && l.weight?.let { rationalOf(it) } == q(e.w) } }
            })
        }
        out += Task(AREA, "kruskal((1, 2, 1), (3, 4, 1))", "not connected", fails = setOf(Failure.NOT_CONNECTED)) { false }
        return out
    }

    private fun orders(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(50) { i ->
            val n = r.int(3, 7)
            val labels = (0 until n).shuffled(r)
            // A DAG: edges from earlier to later labels; now and then one edge back, which may close a cycle.
            val dag = r.edges(n, r.int(n - 1, n + 3), directed = true).map { e -> if (labels[e.u] < labels[e.v]) e else E(e.v, e.u) }.distinctBy { it.u to it.v }
            val edges = if (i % 3 == 0 && dag.isNotEmpty()) dag + E(dag[0].v, dag[0].u) else dag
            val used = edges.flatMap { listOf(it.u, it.v) }.toSet()
            val reach = Array(n) { BooleanArray(n) }
            for (e in edges) reach[e.u][e.v] = true
            for (k in 0 until n) for (a in 0 until n) for (b in 0 until n) if (reach[a][k] && reach[k][b]) reach[a][b] = true
            val cyclic = (0 until n).any { reach[it][it] }
            out += Task(AREA, typed("toposort", edges, directed = true, weighted = false), if (cyclic) "cycle" else "order", judge = { c ->
                val o = (c.outcome as? Outcome.TopologicalOrder)?.order
                if (o == null) false else if (cyclic) {
                    val cyc = o.cycle?.map { it.toInt() - 1 }
                    o.order == null && cyc != null && cyc.indices.all { k -> edges.any { it.u == cyc[k] && it.v == cyc[(k + 1) % cyc.size] } }
                } else {
                    val pos = o.order?.mapIndexed { k, v -> v.toInt() - 1 to k }?.toMap()
                    pos != null && pos.keys == used && edges.all { pos.getValue(it.u) < pos.getValue(it.v) }
                }
            })
        }
        return out
    }

    private fun flows(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(50) {
            val n = r.int(3, 7)
            val edges = r.edges(n, r.int(n, n + 6), directed = true, lo = 1, hi = 9)
            val s = 0; val t = n - 1
            val used = edges.flatMap { listOf(it.u, it.v) }.toSet()
            if (s !in used || t !in used) return@repeat
            // The smallest cut: every set S with s ∈ S, t ∉ S; its capacity the edges leaving S.
            var cut = Long.MAX_VALUE
            for (mask in 0 until (1 shl n)) {
                if (mask and 1 == 0 || mask and (1 shl t) != 0) continue
                cut = minOf(cut, edges.filter { (mask shr it.u) and 1 == 1 && (mask shr it.v) and 1 == 0 }.sumOf { it.w })
            }
            out += Task(AREA, typed("maxflow", edges, directed = true, weighted = true, extra = ", ${name(s)}, ${name(t)}"), "flow $cut", judge = { c ->
                val f = (c.outcome as? Outcome.MaxFlow)?.flow
                f != null && rationalOf(f.value) == q(cut) && f.cut.map { it.toInt() - 1 }.toSet().let { side ->
                    s in side && t !in side && edges.filter { it.u in side && it.v !in side }.sumOf { it.w } == cut
                }
            })
        }
        return out
    }

    private fun walks(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(40) {
            val n = r.int(2, 5)
            val edges = r.edges(n, r.int(n - 1, n + 3), directed = false)
            val used = edges.flatMap { listOf(it.u, it.v) }.toSet()
            if (used.size < n) return@repeat
            val k = r.int(1, 12)
            val a = Array(n) { Array(n) { BigInteger.ZERO } }
            for (e in edges) { a[e.u][e.v] = a[e.u][e.v] + BigInteger.ONE; a[e.v][e.u] = a[e.v][e.u] + BigInteger.ONE }
            var p = Array(n) { x -> Array(n) { y -> if (x == y) BigInteger.ONE else BigInteger.ZERO } }
            repeat(k) { p = Array(n) { x -> Array(n) { y -> (0 until n).fold(BigInteger.ZERO) { s, z -> s + p[x][z] * a[z][y] } } } }
            val u = r.nextInt(n); val v = r.nextInt(n)
            out += Task(AREA, typed("walks", edges, directed = false, weighted = false, extra = ", $k, ${name(u)}, ${name(v)}"), "${p[u][v]}",
                judge = isExact(Q.of(p[u][v])))
        }
        return out
    }

    fun all(): List<Task> {
        val r = Random(1736)
        return facts(r) + paths(r) + trees(r) + orders(r) + flows(r) + walks(r)
    }
}

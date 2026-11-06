package com.professoreuler.mathcalc

import com.professoreuler.mathcalc.Networks.Arc
import com.professoreuler.mathcalc.Networks.Net
import java.util.TreeSet

/**
 * A graph's facts, each checked: the degrees (their sum twice the edges, or the in- and out-degrees each the edges), the
 * components (by search and again by union–find), a tree or a forest (|E| = |V| − components), bipartite with its two sides
 * (every edge between them) or an odd cycle (edge by edge), an Euler trail (by Hierholzer's algorithm, followed edge by edge),
 * the strong components of a directed graph (Kosaraju's algorithm, against mutual reachability), a topological order (Kahn's
 * algorithm, every edge forward) or a cycle, the chromatic number ([NetworkColouring]) and planarity ([NetworkPlanarity]).
 */
internal object NetworkFacts {
    fun facts(net: Net): Networks.Facts {
        val n = net.n
        val m = net.arcs.size
        val loops = net.arcs.count { it.u == it.v }
        val pairs = net.arcs.map { if (net.directed || it.u <= it.v) it.u to it.v else it.v to it.u }
        val simple = loops == 0 && pairs.toSet().size == pairs.size
        val inDegree = IntArray(n)
        val outDegree = IntArray(n)
        val degree = IntArray(n)
        for (a in net.arcs) {
            outDegree[a.u]++
            inDegree[a.v]++
            degree[a.u]++
            degree[a.v]++
        }
        // The handshake lemma: every edge has two ends (a loop both at one vertex).
        if (degree.sum() != 2 * m || (net.directed && (inDegree.sum() != m || outDegree.sum() != m))) throw CalcException(Failure.UNSOLVED)
        val components = components(net)
        val names = net.names
        fun named(list: List<Int>) = list.map { names[it] }
        val forest = if (net.directed) null else m == n - components.size
        val tree = forest?.let { it && components.size == 1 }
        val sides = if (net.directed) null else sides(net)
        val euler = euler(net, degree, inDegree, outDegree)
        val strong = if (net.directed) strong(net) else null
        val order = if (net.directed) order(net) else null
        val adjacency = Expr.Mat(NetworkWalks.adjacency(net).map { r -> r.map { num(Rational.of(it)) } })
        val colouring = if (net.directed || loops > 0) null else NetworkColouring.colouring(net, sides?.bipartite == true)
        val planarity = if (net.directed) null else NetworkPlanarity.planarity(net)
        // The picture: a tree by its levels, a directed acyclic graph by its order, a bipartite graph in its two rows.
        val layers = when {
            tree == true && n > 1 -> Networks.levels(net, 0)
            order?.order != null -> topologicalLevels(net, order.order.map { net.index(it) })
            sides?.bipartite == true && m > 0 -> IntArray(n) { if (names[it] in sides.left) 0 else 1 }
            else -> null
        }
        val groups: List<Int?>? = colouring?.colours?.map { it - 1 } ?: sides?.takeIf { it.bipartite && m > 0 }?.let { s -> names.map { if (it in s.left) 0 else 1 } }
        val trailOrder = HashMap<Int, Int>()
        euler.second.forEachIndexed { k, a -> trailOrder[a.id] = k + 1 }
        val links = net.arcs.map { a -> net.link(a, order = trailOrder[a.id]) }
        return Networks.Facts(net.directed, names, m, loops, simple, degree.toList(),
            if (net.directed) inDegree.toList() else null, if (net.directed) outDegree.toList() else null,
            degree.sortedDescending(), components.map(::named), strong?.map(::named), tree, forest, sides, euler.first, colouring,
            planarity, order, adjacency, Networks.picture(net, layers, groups, links = links))
    }

    /** The components (of a directed graph the weak ones), each in order, by breadth-first search; checked by union–find. */
    fun components(net: Net): List<List<Int>> {
        val both = undirected(net)
        val label = IntArray(net.n) { -1 }
        val out = ArrayList<List<Int>>()
        for (start in 0 until net.n) {
            if (label[start] >= 0) continue
            val members = ArrayList<Int>()
            val queue = ArrayDeque<Int>()
            queue += start
            label[start] = out.size
            while (queue.isNotEmpty()) {
                val u = queue.removeFirst()
                members += u
                for (v in both[u]) if (label[v] < 0) { label[v] = out.size; queue += v }
            }
            out += members.sorted()
        }
        val parent = IntArray(net.n) { it }
        fun find(x: Int): Int { var y = x; while (parent[y] != y) y = parent[y]; return y }
        for (a in net.arcs) { val (x, y) = find(a.u) to find(a.v); if (x != y) parent[x] = y }
        for (u in 0 until net.n) for (v in 0 until net.n) if ((label[u] == label[v]) != (find(u) == find(v))) throw CalcException(Failure.UNSOLVED)
        return out
    }

    private fun undirected(net: Net): List<List<Int>> {
        val both = List(net.n) { ArrayList<Int>() }
        for (a in net.arcs) { both[a.u] += a.v; both[a.v] += a.u }
        return both
    }

    /** Two sides with every edge between them, by breadth-first search; or an odd cycle where an edge joins two of one side. */
    private fun sides(net: Net): Networks.Sides {
        val n = net.n
        val colour = IntArray(n) { -1 }
        val parent = IntArray(n) { -1 }
        val depth = IntArray(n)
        for (start in 0 until n) {
            if (colour[start] >= 0) continue
            colour[start] = 0
            val queue = ArrayDeque<Int>()
            queue += start
            while (queue.isNotEmpty()) {
                val u = queue.removeFirst()
                for ((_, v) in net.out[u]) {
                    if (colour[v] < 0) {
                        colour[v] = 1 - colour[u]
                        parent[v] = u
                        depth[v] = depth[u] + 1
                        queue += v
                    } else if (colour[v] == colour[u]) {
                        return Networks.Sides(false, emptyList(), emptyList(), oddCycle(net, u, v, parent).map { net.names[it] })
                    }
                }
            }
        }
        val left = (0 until n).filter { colour[it] == 0 }
        val right = (0 until n).filter { colour[it] == 1 }
        // Checked: every edge between the sides.
        if (net.arcs.any { colour[it.u] == colour[it.v] }) throw CalcException(Failure.UNSOLVED)
        return Networks.Sides(true, left.map { net.names[it] }, right.map { net.names[it] }, emptyList())
    }

    /** The cycle an edge (u, v) between two vertices at the same depth closes with the search tree's paths to their meeting point. */
    private fun oddCycle(net: Net, u: Int, v: Int, parent: IntArray): List<Int> {
        if (u == v) return listOf(u)
        val up = arrayListOf(u)
        val down = arrayListOf(v)
        var a = u
        var b = v
        while (a != b) {
            a = parent[a]
            b = parent[b]
            if (a < 0 || b < 0) throw CalcException(Failure.UNSOLVED)
            up += a
            down += b
        }
        val cycle = Networks.fromSmallest(up + down.dropLast(1).reversed())
        // Checked: distinct vertices, each joined to the next by an edge, an odd number of them.
        if (cycle.toSet().size != cycle.size || cycle.size % 2 == 0) throw CalcException(Failure.UNSOLVED)
        val edges = net.arcs.map { setOf(it.u, it.v) }.toSet()
        for (k in cycle.indices) if (setOf(cycle[k], cycle[(k + 1) % cycle.size]) !in edges) throw CalcException(Failure.UNSOLVED)
        return cycle
    }

    /**
     * An Euler circuit where every degree is even (in a directed graph every in-degree its out-degree), an Euler path where
     * exactly two are odd (one vertex with one more edge out, one with one more in), and the edges all in one component; found by
     * Hierholzer's algorithm and followed edge by edge: every edge exactly once, each from the vertex before it.
     */
    private fun euler(net: Net, degree: IntArray, inDegree: IntArray, outDegree: IntArray): Pair<Networks.Euler, List<Arc>> {
        val n = net.n
        val odd = (0 until n).filter { if (net.directed) inDegree[it] != outDegree[it] else degree[it] % 2 == 1 }
        val withEdges = (0 until n).filter { degree[it] > 0 }
        val component = components(net).firstOrNull { c -> withEdges.isNotEmpty() && withEdges[0] in c }
        val connected = withEdges.isNotEmpty() && withEdges.all { it in component!! }
        val names = net.names
        fun none() = Networks.Euler(Networks.EulerKind.NONE, emptyList(), odd.map { names[it] }, connected) to emptyList<Arc>()
        if (!connected) return none()
        val start = when {
            odd.isEmpty() -> withEdges[0]
            !net.directed && odd.size == 2 -> odd[0]
            net.directed && odd.size == 2 -> {
                val (x, y) = odd
                when {
                    outDegree[x] == inDegree[x] + 1 && inDegree[y] == outDegree[y] + 1 -> x
                    outDegree[y] == inDegree[y] + 1 && inDegree[x] == outDegree[x] + 1 -> y
                    else -> return none()
                }
            }
            else -> return none()
        }
        val used = BooleanArray(net.arcs.size)
        val next = IntArray(n)
        val outs = net.out
        val stack = arrayListOf(start)
        val arcStack = ArrayList<Arc>()
        val trail = ArrayList<Int>()
        val trailArcs = ArrayList<Arc>()
        while (stack.isNotEmpty()) {
            Budget.check()
            val u = stack.last()
            while (next[u] < outs[u].size && used[outs[u][next[u]].first.id]) next[u]++
            if (next[u] < outs[u].size) {
                val (a, v) = outs[u][next[u]]
                used[a.id] = true
                stack += v
                arcStack += a
            } else {
                trail += stack.removeAt(stack.size - 1)
                if (arcStack.isNotEmpty()) trailArcs += arcStack.removeAt(arcStack.size - 1)
            }
        }
        trail.reverse()
        trailArcs.reverse()
        // Followed edge by edge: every edge once, each from the vertex before it to the next, closed for a circuit.
        val circuit = odd.isEmpty()
        if (trailArcs.size != net.arcs.size || trailArcs.map { it.id }.toSet().size != net.arcs.size || trail.size != trailArcs.size + 1)
            throw CalcException(Failure.UNSOLVED)
        for ((k, a) in trailArcs.withIndex()) {
            val (x, y) = trail[k] to trail[k + 1]
            val fits = (a.u == x && a.v == y) || (!net.directed && a.u == y && a.v == x)
            if (!fits) throw CalcException(Failure.UNSOLVED)
        }
        if (circuit != (trail.first() == trail.last())) throw CalcException(Failure.UNSOLVED)
        return Networks.Euler(if (circuit) Networks.EulerKind.CIRCUIT else Networks.EulerKind.PATH, trail.map { names[it] },
            odd.map { names[it] }, true) to trailArcs
    }

    /** A directed graph's strong components by Kosaraju's algorithm, checked against mutual reachability. */
    private fun strong(net: Net): List<List<Int>> {
        val n = net.n
        val forward = List(n) { ArrayList<Int>() }
        val backward = List(n) { ArrayList<Int>() }
        for (a in net.arcs) { forward[a.u] += a.v; backward[a.v] += a.u }
        // First pass: the vertices in the order their search finishes (iteratively).
        val seen = BooleanArray(n)
        val finished = ArrayList<Int>()
        for (s in 0 until n) {
            if (seen[s]) continue
            val stack = ArrayList<Pair<Int, Int>>()
            stack += s to 0
            seen[s] = true
            while (stack.isNotEmpty()) {
                Budget.check()
                val (u, k) = stack.last()
                if (k < forward[u].size) {
                    stack[stack.size - 1] = u to k + 1
                    val v = forward[u][k]
                    if (!seen[v]) { seen[v] = true; stack += v to 0 }
                } else {
                    finished += u
                    stack.removeAt(stack.size - 1)
                }
            }
        }
        // Second pass on the reversed graph, latest finished first.
        val label = IntArray(n) { -1 }
        var count = 0
        for (s in finished.reversed()) {
            if (label[s] >= 0) continue
            val queue = ArrayDeque<Int>()
            queue += s
            label[s] = count
            while (queue.isNotEmpty()) {
                val u = queue.removeFirst()
                for (v in backward[u]) if (label[v] < 0) { label[v] = count; queue += v }
            }
            count++
        }
        // Checked: two vertices share a component exactly when each reaches the other.
        val reach = List(n) { s -> reachable(forward, s) }
        for (u in 0 until n) for (v in 0 until n) if ((label[u] == label[v]) != (reach[u][v] && reach[v][u])) throw CalcException(Failure.UNSOLVED)
        return (0 until count).map { c -> (0 until n).filter { label[it] == c } }.sortedBy { it.first() }
    }

    private fun reachable(adjacent: List<List<Int>>, s: Int): BooleanArray {
        val seen = BooleanArray(adjacent.size)
        seen[s] = true
        val queue = ArrayDeque<Int>()
        queue += s
        while (queue.isNotEmpty()) {
            Budget.check()
            val u = queue.removeFirst()
            for (v in adjacent[u]) if (!seen[v]) { seen[v] = true; queue += v }
        }
        return seen
    }

    /**
     * A topological order by Kahn's algorithm (always the first vertex in the graph's order with no edge in from those left), or
     * else a cycle among those left; checked: every edge from an earlier vertex to a later one, or the cycle edge by edge.
     */
    fun order(net: Net): Networks.Order {
        if (!net.directed) throw CalcException(Failure.ARGUMENTS)
        val n = net.n
        val incoming = IntArray(n)
        for (a in net.arcs) incoming[a.v]++
        val ready = TreeSet<Int>()
        for (v in 0 until n) if (incoming[v] == 0) ready += v
        val order = ArrayList<Int>()
        while (ready.isNotEmpty()) {
            Budget.check()
            val u = ready.pollFirst()!!
            order += u
            for ((_, v) in net.out[u]) {
                incoming[v]--
                if (incoming[v] == 0) ready += v
            }
        }
        val names = net.names
        if (order.size == n) {
            val position = IntArray(n)
            order.forEachIndexed { k, v -> position[v] = k }
            if (net.arcs.any { position[it.u] >= position[it.v] }) throw CalcException(Failure.UNSOLVED)
            val links = net.arcs.map { net.link(it) }
            return Networks.Order(order.map { names[it] }, null, order.map { names[it] },
                Networks.picture(net, topologicalLevels(net, order), links = links))
        }
        // Every vertex left has an edge in from another one left: going back along such edges must come round again.
        val left = BooleanArray(n) { it !in order }
        val into = List(n) { ArrayList<Arc>() }
        for (a in net.arcs) if (left[a.u] && left[a.v]) into[a.v] += a
        var x = (0 until n).first { left[it] }
        val walk = ArrayList<Int>()
        val at = HashMap<Int, Int>()
        while (x !in at) {
            at[x] = walk.size
            walk += x
            x = into[x].firstOrNull()?.u ?: throw CalcException(Failure.UNSOLVED)
        }
        val cycle = Networks.fromSmallest(walk.subList(at.getValue(x), walk.size).reversed())
        // Checked: each vertex of the cycle has an edge to the next, the last to the first.
        val cycleArcs = cycle.indices.map { k ->
            val (u, v) = cycle[k] to cycle[(k + 1) % cycle.size]
            net.arcs.firstOrNull { it.u == u && it.v == v } ?: throw CalcException(Failure.UNSOLVED)
        }
        val ids = cycleArcs.map { it.id }.toSet()
        val links = net.arcs.map { net.link(it, marked = it.id in ids) }
        return Networks.Order(null, cycle.map { names[it] }, order.map { names[it] }, Networks.picture(net, null, links = links))
    }

    /** Each vertex's level in a topological [order]: one more than the highest of those with an edge to it. */
    private fun topologicalLevels(net: Net, order: List<Int>): IntArray {
        val level = IntArray(net.n)
        val into = List(net.n) { ArrayList<Int>() }
        for (a in net.arcs) into[a.v] += a.u
        for (v in order) level[v] = into[v].maxOfOrNull { level[it] + 1 } ?: 0
        return level
    }
}

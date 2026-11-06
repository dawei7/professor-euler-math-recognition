package com.professoreuler.mathcalc

import com.professoreuler.mathcalc.Networks.Arc
import com.professoreuler.mathcalc.Networks.Net

/**
 * Minimum spanning trees of a connected undirected graph: Kruskal's algorithm (the edges by weight, lightest first, each taken
 * unless its ends are already joined) and Prim's (from one vertex, always the lightest edge from the tree to a vertex outside it),
 * both from their textbook descriptions; ties go to the edge written first. Every tree is checked: |V| − 1 edges of the graph
 * joining every vertex, minimal by the cycle property (no edge outside it is lighter than a tree edge on the cycle it closes),
 * and its weight is that of the other algorithm's tree.
 */
internal object NetworkTrees {
    fun tree(net: Net, kruskal: Boolean, start: Int?): Networks.Tree {
        if (net.directed) throw CalcException(Failure.ARGUMENTS)
        val first = if (kruskal) kruskal(net) else prim(net, start ?: 0)
        val other = if (kruskal) prim(net, 0) else kruskal(net)
        val taken = first.first
        val total = taken.fold(Rational.ZERO) { s, a -> s + a.w }
        check(net, taken)
        // The other algorithm's tree weighs the same: a minimum spanning tree's weight is one number.
        if (other.first.fold(Rational.ZERO) { s, a -> s + a.w } != total) throw CalcException(Failure.UNSOLVED)
        val order = taken.withIndex().associate { (k, a) -> a.id to k + 1 }
        val links = net.arcs.map { net.link(it, marked = it.id in order, order = order[it.id]) }
        val levels = if (net.n > 1) treeLevels(net, taken, start ?: 0) else null
        return Networks.Tree(if (kruskal) Networks.TreeMethod.KRUSKAL else Networks.TreeMethod.PRIM, net.names,
            taken.map { net.link(it) }, num(total), first.second.map { (a, t) -> Networks.TreeStep(net.link(a), t) },
            if (kruskal) null else net.names[start ?: 0],
            Networks.picture(net, levels, marked = if (kruskal) emptySet() else setOf(start ?: 0), links = links))
    }

    /** Kruskal's tree and every edge it looked at (taken or skipped), until the tree had |V| − 1 edges. */
    private fun kruskal(net: Net): Pair<List<Arc>, List<Pair<Arc, Boolean>>> {
        val parent = IntArray(net.n) { it }
        fun find(x: Int): Int { var y = x; while (parent[y] != y) { parent[y] = parent[parent[y]]; y = parent[y] }; return y }
        val taken = ArrayList<Arc>()
        val steps = ArrayList<Pair<Arc, Boolean>>()
        for (a in net.arcs.sortedWith(compareBy<Arc> { it.w }.thenBy { it.id })) {
            if (taken.size == net.n - 1) break
            Budget.check()
            val (x, y) = find(a.u) to find(a.v)
            if (x != y) {
                parent[x] = y
                taken += a
                steps += a to true
            } else {
                steps += a to false
            }
        }
        if (taken.size != net.n - 1) throw CalcException(Failure.NOT_CONNECTED)
        return taken to steps
    }

    /** Prim's tree from [start]: the edges in the order taken. */
    private fun prim(net: Net, start: Int): Pair<List<Arc>, List<Pair<Arc, Boolean>>> {
        val inTree = BooleanArray(net.n)
        inTree[start] = true
        val taken = ArrayList<Arc>()
        while (taken.size < net.n - 1) {
            Budget.check()
            var best: Arc? = null
            for (a in net.arcs) {
                if (inTree[a.u] == inTree[a.v]) continue
                if (best == null || a.w < best.w || (a.w == best.w && a.id < best.id)) best = a
            }
            if (best == null) throw CalcException(Failure.NOT_CONNECTED)
            inTree[best.u] = true
            inTree[best.v] = true
            taken += best
        }
        return taken to taken.map { it to true }
    }

    /**
     * [taken] is a minimum spanning tree: |V| − 1 distinct edges of the graph that join every vertex (so a tree), and for every
     * other edge (u, v) each tree edge on the tree's path from u to v weighs at most as much as it (the cycle property, which a
     * spanning tree has exactly when it is minimal).
     */
    private fun check(net: Net, taken: List<Arc>) {
        if (taken.size != net.n - 1 || taken.map { it.id }.toSet().size != taken.size) throw CalcException(Failure.UNSOLVED)
        val adjacent = List(net.n) { ArrayList<Pair<Int, Arc>>() }
        for (a in taken) {
            if (net.arcs[a.id] !== a || a.u == a.v) throw CalcException(Failure.UNSOLVED)
            adjacent[a.u] += a.v to a
            adjacent[a.v] += a.u to a
        }
        val ids = taken.map { it.id }.toSet()
        for (u in 0 until net.n) {
            // The heaviest tree edge on the path from u to each vertex.
            val heaviest = arrayOfNulls<Rational>(net.n)
            val seen = BooleanArray(net.n)
            seen[u] = true
            val stack = ArrayDeque<Int>()
            stack += u
            var reached = 1
            while (stack.isNotEmpty()) {
                Budget.check()
                val x = stack.removeLast()
                for ((y, a) in adjacent[x]) {
                    if (seen[y]) continue
                    seen[y] = true
                    reached++
                    heaviest[y] = heaviest[x]?.let { if (it > a.w) it else a.w } ?: a.w
                    stack += y
                }
            }
            if (reached != net.n) throw CalcException(Failure.UNSOLVED)
            for (a in net.arcs) {
                if (a.id in ids || a.u != u || a.v == u) continue
                if (heaviest[a.v]!! > a.w) throw CalcException(Failure.UNSOLVED)
            }
        }
    }

    /** The tree's levels from [root], for its picture. */
    private fun treeLevels(net: Net, taken: List<Arc>, root: Int): IntArray {
        val depth = IntArray(net.n) { -1 }
        val adjacent = List(net.n) { ArrayList<Int>() }
        for (a in taken) { adjacent[a.u] += a.v; adjacent[a.v] += a.u }
        depth[root] = 0
        val queue = ArrayDeque<Int>()
        queue += root
        while (queue.isNotEmpty()) {
            val x = queue.removeFirst()
            for (y in adjacent[x]) if (depth[y] < 0) { depth[y] = depth[x] + 1; queue += y }
        }
        return depth
    }
}

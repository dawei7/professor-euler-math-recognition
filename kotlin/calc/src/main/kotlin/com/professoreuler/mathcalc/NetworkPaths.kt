package com.professoreuler.mathcalc

import com.professoreuler.mathcalc.Networks.Arc
import com.professoreuler.mathcalc.Networks.Net

/**
 * Shortest paths from one vertex: Dijkstra's algorithm (weights ≥ 0; each time the unvisited vertex nearest the source is
 * visited and the edges out of it relaxed) and the Bellman–Ford algorithm (any weights in a directed graph: |V| − 1 rounds of
 * relaxing every edge in the order given; a round |V| that still lowers a distance shows a cycle of negative weight). Both from
 * their textbook descriptions (Cormen, Leiserson, Rivest and Stein, chapter 24). The distances are checked against every edge,
 * and every path against the edges it uses.
 */
internal object NetworkPaths {
    fun paths(net: Net, source: Int, target: Int?, bellmanFord: Boolean): Networks.Paths {
        val negative = net.arcs.any { it.w.signum < 0 }
        // Dijkstra's algorithm takes a visited vertex's distance as final, which a negative edge can undo; an undirected edge of
        // negative weight is a walk back and forth of any negative length.
        if (negative && (!bellmanFord || !net.directed)) throw CalcException(Failure.NEGATIVE_WEIGHT)
        val n = net.n
        val dist = arrayOfNulls<Rational>(n)
        val prev = IntArray(n) { -1 }
        val prevArc = arrayOfNulls<Arc>(n)
        dist[source] = Rational.ZERO
        val table = ArrayList<List<Expr?>>()
        fun snapshot() { table += dist.map { it?.let(::num) } }
        snapshot()
        val visited = ArrayList<Int>()
        var cycle: List<Int>? = null
        var cycleArcs: List<Arc>? = null
        if (!bellmanFord) {
            val done = BooleanArray(n)
            while (true) {
                Budget.check()
                var u = -1
                for (k in 0 until n) if (!done[k] && dist[k] != null && (u < 0 || dist[k]!! < dist[u]!!)) u = k
                if (u < 0) break
                done[u] = true
                visited += u
                for ((a, v) in net.out[u]) {
                    if (done[v]) continue
                    val d = dist[u]!! + a.w
                    if (dist[v] == null || d < dist[v]!!) {
                        dist[v] = d
                        prev[v] = u
                        prevArc[v] = a
                    }
                }
                snapshot()
            }
        } else {
            // Each round relaxes every edge in the order given (an undirected one both ways).
            fun round(): Int {
                var last = -1
                for (a in net.arcs) {
                    Budget.check()
                    val ways = if (net.directed || a.u == a.v) listOf(a.u to a.v) else listOf(a.u to a.v, a.v to a.u)
                    for ((u, v) in ways) {
                        val du = dist[u] ?: continue
                        val d = du + a.w
                        if (dist[v] == null || d < dist[v]!!) {
                            dist[v] = d
                            prev[v] = u
                            prevArc[v] = a
                            last = v
                        }
                    }
                }
                return last
            }
            var stable = false
            for (r in 1 until n) {
                val changed = round() >= 0
                snapshot()
                if (!changed) { stable = true; break }
            }
            if (!stable) {
                // Round |V|: a distance that still falls lies on a negative cycle or behind one; |V| steps back along the
                // predecessors lead onto the cycle.
                val last = round()
                if (last >= 0) {
                    var x = last
                    repeat(n) { x = prev[x].also { if (it < 0) throw CalcException(Failure.UNSOLVED) } }
                    val found = ArrayList<Int>()
                    var y = x
                    do {
                        found += y
                        y = prev[y]
                        if (y < 0 || found.size > n) throw CalcException(Failure.UNSOLVED)
                    } while (y != x)
                    found.reverse()
                    cycle = Networks.fromSmallest(found)
                    // The edge into each vertex of the cycle is its predecessor's.
                    cycleArcs = cycle.map { prevArc[it]!! }
                }
            }
        }
        val names = net.names
        if (cycle != null) {
            // Checked: a cycle of edges of the graph, each from the vertex before it, of negative total weight, reached from the
            // source.
            val arcs = cycleArcs!!
            val weight = arcs.fold(Rational.ZERO) { s, a -> s + a.w }
            val closes = cycle.indices.all { k -> arcs[k].u == cycle[(k - 1 + cycle.size) % cycle.size] && arcs[k].v == cycle[k] }
            if (!closes || weight.signum >= 0 || cycle.toSet().size != cycle.size || dist[cycle[0]] == null) throw CalcException(Failure.UNSOLVED)
            val onCycle = arcs.map { it.id }.toSet()
            val links = net.arcs.map { net.link(it, marked = it.id in onCycle) }
            return Networks.Paths(Networks.PathMethod.BELLMAN_FORD, net.directed, names, names[source], target?.let { names[it] },
                emptyList(), emptyList(), null, null, table, emptyList(), cycle.map { names[it] }, num(weight),
                Networks.picture(net, Networks.levels(net, source), marked = setOfNotNull(source, target), links = links))
        }
        check(net, source, dist, prev, prevArc)
        val path = target?.let { t -> if (dist[t] == null) null else pathTo(t, prev) }
        val pathArcs = path?.let { p -> (1 until p.size).map { prevArc[p[it]]!! } }
        if (path != null) {
            // The path's edges are edges of the graph, one after the other, and add up to its length.
            val sum = pathArcs!!.fold(Rational.ZERO) { s, a -> s + a.w }
            if (path.first() != source || sum != dist[target!!]) throw CalcException(Failure.UNSOLVED)
            for (k in 1 until path.size) if (!joins(net, pathArcs[k - 1], path[k - 1], path[k])) throw CalcException(Failure.UNSOLVED)
        }
        // Marked: the path to the target, or else every vertex's last edge on its shortest path (a shortest-path tree).
        val marks = pathArcs?.map { it.id }?.toSet() ?: if (target == null) prevArc.filterNotNull().map { it.id }.toSet() else emptySet()
        val links = net.arcs.map { a ->
            // An undirected edge is drawn the way the path goes along it.
            val forward = !net.directed && prevArc.withIndex().any { (v, b) -> b === a && v == a.u && prev[v] == a.v }
            if (forward) net.link(a, a.v, a.u, marked = a.id in marks) else net.link(a, marked = a.id in marks)
        }
        return Networks.Paths(if (bellmanFord) Networks.PathMethod.BELLMAN_FORD else Networks.PathMethod.DIJKSTRA, net.directed, names,
            names[source], target?.let { names[it] }, dist.map { it?.let(::num) }, prev.map { if (it < 0) null else names[it] },
            path?.map { names[it] }, target?.let { t -> dist[t]?.let(::num) }, table, visited.map { names[it] }, null, null,
            Networks.picture(net, Networks.levels(net, source), marked = setOfNotNull(source, target), links = links))
    }

    /** Whether [a] leads from [from] to [to] (an undirected edge either way). */
    private fun joins(net: Net, a: Arc, from: Int, to: Int): Boolean =
        (a.u == from && a.v == to) || (!net.directed && a.u == to && a.v == from)

    private fun pathTo(t: Int, prev: IntArray): List<Int> {
        val out = ArrayList<Int>()
        var x = t
        while (x >= 0) {
            out += x
            if (out.size > prev.size) throw CalcException(Failure.UNSOLVED)
            x = prev[x]
        }
        return out.reversed()
    }

    /**
     * The distances are the shortest, checked independently of how they were found: the source's is 0; no edge leads to a
     * shorter distance (d(v) ≤ d(u) + w(u, v) for every edge, and nothing unreached is reached by one); and every other reached
     * vertex is reached from its predecessor by an edge with d(v) = d(u) + w(u, v), back to the source. A path's weight is then at
     * least the distance of its end (adding d(v) − d(u) ≤ w along it), and the predecessors give one that equals it.
     */
    private fun check(net: Net, source: Int, dist: Array<Rational?>, prev: IntArray, prevArc: Array<Arc?>) {
        if (dist[source] != Rational.ZERO) throw CalcException(Failure.UNSOLVED)
        for (u in 0 until net.n) {
            val du = dist[u] ?: continue
            for ((a, v) in net.out[u]) {
                val dv = dist[v] ?: throw CalcException(Failure.UNSOLVED)
                if (dv > du + a.w) throw CalcException(Failure.UNSOLVED)
            }
        }
        for (v in 0 until net.n) {
            if (v == source || dist[v] == null) continue
            val u = prev[v]
            val a = prevArc[v] ?: throw CalcException(Failure.UNSOLVED)
            if (u < 0 || dist[u] == null || !joins(net, a, u, v) || dist[v] != dist[u]!! + a.w) throw CalcException(Failure.UNSOLVED)
            pathTo(v, prev).let { if (it.first() != source) throw CalcException(Failure.UNSOLVED) }
        }
    }
}

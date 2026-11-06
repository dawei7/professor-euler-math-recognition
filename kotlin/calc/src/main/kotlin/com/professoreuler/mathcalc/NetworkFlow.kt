package com.professoreuler.mathcalc

import com.professoreuler.mathcalc.Networks.Net

/**
 * A maximum flow in a directed network, by the Edmonds–Karp algorithm (the Ford–Fulkerson method, each time along a shortest
 * path with room left in the residual network, found by breadth-first search), from its textbook description. The edges' weights
 * are their capacities (1 where none is given). The flow is checked by a cut of the same capacity: every edge within its capacity,
 * what flows into each other vertex flows out of it, and the edges from the vertices still reachable from the source to the rest
 * carry exactly the flow's value in capacity, which no flow can exceed (the max-flow min-cut theorem).
 */
internal object NetworkFlow {
    fun flow(net: Net, s: Int, t: Int): Networks.Flow {
        if (!net.directed || s == t) throw CalcException(Failure.ARGUMENTS)
        if (net.arcs.any { it.w.signum < 0 }) throw CalcException(Failure.NEGATIVE_WEIGHT)
        val m = net.arcs.size
        val f = Array(m) { Rational.ZERO }
        val cap = net.arcs.map { it.w }
        // The residual network: each edge forward while it has room, backward while it carries flow.
        val forward = List(net.n) { ArrayList<Int>() }
        val backward = List(net.n) { ArrayList<Int>() }
        for (a in net.arcs) {
            forward[a.u] += a.id
            backward[a.v] += a.id
        }
        val augmentations = ArrayList<Networks.Augmentation>()
        fun search(): Pair<IntArray, IntArray> {
            // For each vertex reached, the edge it was reached by (−1 for none) and whether forward (1) or backward (−1).
            val by = IntArray(net.n) { -1 }
            val way = IntArray(net.n)
            val seen = BooleanArray(net.n)
            seen[s] = true
            val queue = ArrayDeque<Int>()
            queue += s
            while (queue.isNotEmpty()) {
                Budget.check()
                val u = queue.removeFirst()
                for (id in forward[u]) {
                    val v = net.arcs[id].v
                    if (!seen[v] && f[id] < cap[id]) { seen[v] = true; by[v] = id; way[v] = 1; queue += v }
                }
                for (id in backward[u]) {
                    val v = net.arcs[id].u
                    if (!seen[v] && f[id].signum > 0) { seen[v] = true; by[v] = id; way[v] = -1; queue += v }
                }
            }
            return by to way
        }
        while (true) {
            val (by, way) = search()
            if (by[t] < 0) break
            // Back from the sink: the path, and the least room along it.
            val path = ArrayList<Int>()
            var x = t
            var room: Rational? = null
            while (x != s) {
                path += x
                val id = by[x]
                val r = if (way[x] == 1) cap[id] - f[id] else f[id]
                if (room == null || r < room) room = r
                x = if (way[x] == 1) net.arcs[id].u else net.arcs[id].v
                if (path.size > net.n) throw CalcException(Failure.UNSOLVED)
            }
            path += s
            x = t
            while (x != s) {
                val id = by[x]
                if (way[x] == 1) { f[id] = f[id] + room!!; x = net.arcs[id].u } else { f[id] = f[id] - room!!; x = net.arcs[id].v }
            }
            augmentations += Networks.Augmentation(path.reversed().map { net.names[it] }, num(room!!))
        }
        // The cut: the vertices the residual network still reaches from the source.
        val (by, _) = search()
        val side = BooleanArray(net.n) { it == s || by[it] >= 0 }
        val value = net.arcs.fold(Rational.ZERO) { sum, a -> sum + (if (a.u == s) f[a.id] else Rational.ZERO) - (if (a.v == s) f[a.id] else Rational.ZERO) }
        val cutCapacity = net.arcs.filter { side[it.u] && !side[it.v] }.fold(Rational.ZERO) { sum, a -> sum + a.w }
        check(net, s, t, f, side, value, cutCapacity)
        val links = net.arcs.map { a -> net.link(a, marked = side[a.u] && !side[a.v], flow = num(f[a.id])) }
        return Networks.Flow(net.names[s], net.names[t], net.names, num(value), links, augmentations,
            net.names.filterIndexed { k, _ -> side[k] }, links.filter { it.marked },
            Networks.picture(net, Networks.levels(net, s), groups = side.map { if (it) 0 else 1 }, marked = setOf(s, t), links = links))
    }

    /**
     * A flow of [value] that a cut of the same capacity bounds is maximal: each edge's flow within 0 and its capacity; at every
     * vertex but the source and the sink as much in as out; the sink receiving the value; the source on one side of the cut and
     * the sink on the other; and the cut's capacity equal to the value.
     */
    private fun check(net: Net, s: Int, t: Int, f: Array<Rational>, side: BooleanArray, value: Rational, cutCapacity: Rational) {
        val balance = Array(net.n) { Rational.ZERO }
        for (a in net.arcs) {
            if (f[a.id].signum < 0 || f[a.id] > a.w) throw CalcException(Failure.UNSOLVED)
            balance[a.u] = balance[a.u] - f[a.id]
            balance[a.v] = balance[a.v] + f[a.id]
        }
        for (v in 0 until net.n) if (v != s && v != t && balance[v].signum != 0) throw CalcException(Failure.UNSOLVED)
        if (balance[t] != value || -balance[s] != value) throw CalcException(Failure.UNSOLVED)
        if (!side[s] || side[t] || cutCapacity != value) throw CalcException(Failure.UNSOLVED)
    }
}

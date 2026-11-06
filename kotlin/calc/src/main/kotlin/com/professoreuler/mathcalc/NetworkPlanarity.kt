package com.professoreuler.mathcalc

import com.professoreuler.mathcalc.Networks.Net

/**
 * Whether a small graph is planar, with a proof either way. Planarity is decided on each 2-connected block of the graph's simple
 * form (loops and repeated edges change nothing) by the algorithm of Demoucron, Malgrange and Pertuiset (1964), from its
 * textbook description (Gibbons, Algorithmic Graph Theory, 1985): a cycle is drawn, then, again and again, a path through a
 * fragment of what is not drawn yet is drawn in a face that holds all of the fragment's attachments (one with only one such
 * face first); where a fragment fits in no face the graph is not planar.
 *
 * The answer is proved independently of the algorithm. A planar one by its faces: each a cycle, each edge on two of them, the
 * faces around each vertex one cycle, and V − E + F = 2, which makes the faces a sphere, a drawing without crossings. A non-planar
 * one by Kuratowski's theorem: edges are taken away while the rest stays non-planar, and what remains must be a subdivision of
 * K₅ or K₃,₃, checked by smoothing away its vertices of degree 2. Where neither proof is found, nothing is said.
 */
internal object NetworkPlanarity {
    private const val MOST_VERTICES = 40
    private const val MOST_EDGES = 150

    private class NotProved : RuntimeException()

    fun planarity(net: Net): Networks.Planarity? {
        val n = net.n
        val edges = LinkedHashSet<Pair<Int, Int>>()
        for (a in net.arcs) if (a.u != a.v) edges += if (a.u < a.v) a.u to a.v else a.v to a.u
        if (n > MOST_VERTICES || edges.size > MOST_EDGES) return null
        return try {
            if (planar(n, edges.toList())) {
                // Euler's formula for a plane drawing with c components: V − E + F = 1 + c (loops and repeated edges each one face more).
                val components = NetworkFacts.components(net).size
                Networks.Planarity(true, net.arcs.size - n + 1 + components, null, emptyList())
            } else {
                var keep = edges.toList()
                for (e in edges) {
                    val without = keep - e
                    if (!planar(n, without)) keep = without
                }
                val kind = kuratowski(keep) ?: return null
                Networks.Planarity(false, null, kind, keep.map { net.names[it.first] to net.names[it.second] })
            }
        } catch (e: NotProved) {
            null
        }
    }

    /** Whether every block of the simple graph [edges] has a drawing without crossings, each drawing checked. */
    private fun planar(n: Int, edges: List<Pair<Int, Int>>): Boolean {
        val adjacent = List(n) { ArrayList<Int>() }
        for ((u, v) in edges) { adjacent[u] += v; adjacent[v] += u }
        for (block in blocks(n, adjacent)) {
            if (block.size < 3) continue
            val faces = dmp(block) ?: return false
            if (!sphere(block, faces)) throw NotProved()
        }
        return true
    }

    /** The 2-connected blocks of a simple graph, as their edges (Tarjan's algorithm). */
    private fun blocks(n: Int, adjacent: List<List<Int>>): List<List<Pair<Int, Int>>> {
        val discovered = IntArray(n) { -1 }
        val low = IntArray(n)
        var time = 0
        val stack = ArrayList<Pair<Int, Int>>()
        val out = ArrayList<List<Pair<Int, Int>>>()
        fun search(u: Int, parent: Int) {
            Budget.check()
            discovered[u] = time
            low[u] = time
            time++
            for (v in adjacent[u]) {
                if (discovered[v] < 0) {
                    stack += u to v
                    search(v, u)
                    low[u] = minOf(low[u], low[v])
                    if (low[v] >= discovered[u]) {
                        val block = ArrayList<Pair<Int, Int>>()
                        while (true) {
                            val e = stack.removeAt(stack.size - 1)
                            block += e
                            if (e == (u to v)) break
                        }
                        out += block
                    }
                } else if (v != parent && discovered[v] < discovered[u]) {
                    stack += u to v
                    low[u] = minOf(low[u], discovered[v])
                }
            }
        }
        for (s in 0 until n) if (discovered[s] < 0) search(s, -1)
        return out
    }

    private fun key(u: Int, v: Int): Long = if (u < v) u.toLong() * 100_000 + v else v.toLong() * 100_000 + u

    /** What is not drawn yet, in one piece: its vertices inside, where it meets the drawing, or a single edge (a chord). */
    private class Fragment(val attachments: List<Int>, val inner: Set<Int>, val chord: Pair<Int, Int>?)

    /** The faces of a drawing of a 2-connected simple graph (each a cycle of its vertices), or null where it has none. */
    private fun dmp(block: List<Pair<Int, Int>>): List<List<Int>>? {
        val adjacent = HashMap<Int, MutableSet<Int>>()
        for ((u, v) in block) {
            adjacent.getOrPut(u) { LinkedHashSet() } += v
            adjacent.getOrPut(v) { LinkedHashSet() } += u
        }
        val vertices = adjacent.keys.sorted()
        val cycle = cycle(vertices[0], adjacent) ?: throw NotProved()
        val drawnVertices = HashSet<Int>(cycle)
        val drawnEdges = HashSet<Long>()
        for (k in cycle.indices) drawnEdges += key(cycle[k], cycle[(k + 1) % cycle.size])
        val faces = arrayListOf(cycle, cycle.toList())
        while (drawnEdges.size < block.size) {
            Budget.check()
            val fragments = ArrayList<Fragment>()
            for ((u, v) in block) if (key(u, v) !in drawnEdges && u in drawnVertices && v in drawnVertices) {
                fragments += Fragment(listOf(minOf(u, v), maxOf(u, v)), emptySet(), u to v)
            }
            val seen = HashSet<Int>()
            for (s in vertices) {
                if (s in drawnVertices || s in seen) continue
                val inner = LinkedHashSet<Int>()
                val attachments = sortedSetOf<Int>()
                val queue = ArrayDeque<Int>()
                queue += s
                seen += s
                while (queue.isNotEmpty()) {
                    val x = queue.removeFirst()
                    inner += x
                    for (y in adjacent.getValue(x)) {
                        if (y in drawnVertices) attachments += y
                        else if (y !in seen) { seen += y; queue += y }
                    }
                }
                if (attachments.size < 2) throw NotProved()
                fragments += Fragment(attachments.toList(), inner, null)
            }
            if (fragments.isEmpty()) throw NotProved()
            val fitting = fragments.map { f -> faces.indices.filter { k -> faces[k].containsAll(f.attachments) } }
            if (fitting.any { it.isEmpty() }) return null
            val chosen = fitting.indexOfFirst { it.size == 1 }.let { if (it < 0) 0 else it }
            val fragment = fragments[chosen]
            val face = fitting[chosen][0]
            val path = fragment.chord?.toList() ?: pathThrough(fragment, adjacent)
            // The face is cut in two along the path.
            val f = faces[face]
            val (a, b) = path.first() to path.last()
            val i = f.indexOf(a)
            val j = f.indexOf(b)
            val inside = path.subList(1, path.size - 1)
            val one = around(f, i, j) + inside.reversed()
            val two = around(f, j, i) + inside
            faces[face] = one
            faces += two
            drawnVertices += path
            for (k in 1 until path.size) drawnEdges += key(path[k - 1], path[k])
        }
        return faces
    }

    /** The vertices of the cycle [f] from position [i] on to position [j]. */
    private fun around(f: List<Int>, i: Int, j: Int): List<Int> {
        val out = ArrayList<Int>()
        var k = i
        while (true) {
            out += f[k]
            if (k == j) return out
            k = (k + 1) % f.size
        }
    }

    /** A path from one attachment through the fragment's inside to another. */
    private fun pathThrough(fragment: Fragment, adjacent: Map<Int, Set<Int>>): List<Int> {
        val a = fragment.attachments[0]
        val b = fragment.attachments[1]
        val from = HashMap<Int, Int>()
        val queue = ArrayDeque<Int>()
        for (x in adjacent.getValue(a)) if (x in fragment.inner && x !in from) { from[x] = a; queue += x }
        while (queue.isNotEmpty()) {
            val x = queue.removeFirst()
            if (b in adjacent.getValue(x)) {
                val path = arrayListOf(b, x)
                var y = x
                while (from.getValue(y) != a) { y = from.getValue(y); path += y }
                path += a
                return path.reversed()
            }
            for (y in adjacent.getValue(x)) if (y in fragment.inner && y !in from) { from[y] = x; queue += y }
        }
        throw NotProved()
    }

    /** A cycle through [start] (a 2-connected graph has one), by depth-first search: the first edge back to an earlier vertex. */
    private fun cycle(start: Int, adjacent: Map<Int, Set<Int>>): List<Int>? {
        val parent = HashMap<Int, Int>()
        val depth = HashMap<Int, Int>()
        val stack = ArrayList<Int>()
        depth[start] = 0
        parent[start] = -1
        stack += start
        val next = HashMap<Int, Iterator<Int>>()
        while (stack.isNotEmpty()) {
            val u = stack.last()
            val it = next.getOrPut(u) { adjacent.getValue(u).iterator() }
            if (!it.hasNext()) { stack.removeAt(stack.size - 1); continue }
            val v = it.next()
            if (v == parent[u]) continue
            if (v in depth) {
                // The tree path from v down to u, closed by the edge u–v.
                val out = arrayListOf(u)
                var x = u
                while (x != v) { x = parent.getValue(x); if (x < 0) return null; out += x }
                return out.reversed()
            }
            depth[v] = depth.getValue(u) + 1
            parent[v] = u
            stack += v
        }
        return null
    }

    /**
     * The faces are a drawing on the sphere: each a cycle of edges of the block, every edge on exactly two faces, the faces
     * around every vertex one cycle (so the faces form a closed surface), and V − E + F = 2 (so the surface is the sphere).
     */
    private fun sphere(block: List<Pair<Int, Int>>, faces: List<List<Int>>): Boolean {
        val edges = block.map { key(it.first, it.second) }.toSet()
        val vertices = block.flatMap { listOf(it.first, it.second) }.toSet()
        val count = HashMap<Long, Int>()
        // At each vertex, its corners: the two edges that meet there in a face.
        val corners = HashMap<Int, ArrayList<Pair<Int, Int>>>()
        for (f in faces) {
            if (f.size < 3 || f.toSet().size != f.size) return false
            for (k in f.indices) {
                val (x, v, y) = Triple(f[(k - 1 + f.size) % f.size], f[k], f[(k + 1) % f.size])
                val e = key(v, y)
                if (e !in edges) return false
                count[e] = (count[e] ?: 0) + 1
                corners.getOrPut(v) { ArrayList() } += x to y
            }
        }
        if (edges.any { count[it] != 2 }) return false
        for (v in vertices) {
            val around = corners[v] ?: return false
            // The corners link v's neighbours in one cycle through all of them.
            val links = HashMap<Int, MutableList<Int>>()
            for ((x, y) in around) { links.getOrPut(x) { ArrayList() } += y; links.getOrPut(y) { ArrayList() } += x }
            if (links.values.any { it.size != 2 }) return false
            val start = links.keys.first()
            val seen = hashSetOf(start)
            var previous = start
            var at = links.getValue(start)[0]
            while (at != start) {
                if (!seen.add(at)) return false
                val nextOnes = links.getValue(at)
                val next = if (nextOnes[0] != previous) nextOnes[0] else nextOnes[1]
                previous = at
                at = next
            }
            if (seen.size != links.size) return false
        }
        return vertices.size - edges.size + faces.size == 2
    }

    /** What [edges] subdivide, K₅ or K₃,₃, once their vertices of degree 2 are smoothed away; null where they are neither. */
    private fun kuratowski(edges: List<Pair<Int, Int>>): Networks.Kuratowski? {
        val adjacent = HashMap<Int, MutableSet<Int>>()
        for ((u, v) in edges) {
            adjacent.getOrPut(u) { HashSet() } += v
            adjacent.getOrPut(v) { HashSet() } += u
        }
        while (true) {
            val v = adjacent.keys.firstOrNull { adjacent.getValue(it).size == 2 } ?: break
            val (a, b) = adjacent.getValue(v).toList()
            // Smoothing must not join two vertices already joined: then this is no subdivision of a simple graph.
            if (b in adjacent.getValue(a)) return null
            adjacent.remove(v)
            adjacent.getValue(a).remove(v)
            adjacent.getValue(b).remove(v)
            adjacent.getValue(a) += b
            adjacent.getValue(b) += a
        }
        val vertices = adjacent.keys.toList()
        val degrees = vertices.map { adjacent.getValue(it).size }
        if (vertices.size == 5 && degrees.all { it == 4 }) return Networks.Kuratowski.K5
        if (vertices.size == 6 && degrees.all { it == 3 }) {
            // Two sides of three, every vertex joined to the three of the other side.
            val first = vertices[0]
            val other = adjacent.getValue(first)
            val same = vertices.filter { it !in other }
            if (same.size == 3 && same.all { adjacent.getValue(it) == other } && other.all { o -> adjacent.getValue(o) == same.toSet() }) {
                return Networks.Kuratowski.K33
            }
        }
        return null
    }
}

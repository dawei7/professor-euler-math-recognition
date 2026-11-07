package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Graphs as a course in discrete mathematics has them (Lehman, Leighton and Meyer, Mathematics for Computer Science): a graph is
 * written as its edges, each an argument in round brackets, `(u, v)` undirected and `(u → v)` directed (typed `->` or with the →
 * key), with a weight after a further comma, `(u, v, 4)`, `(u → v, 4)`. Vertices are whole numbers (0 to 9999) or letters, with
 * an index where wanted (`v_1`, typed `v1`); the arguments after the edges are the vertices the function asks for (a source, a
 * target) or, for [Mode.WALKS], the length. Round brackets because the Calculator reads points and vectors so (`line((1, 2), (3,
 * 4))`), and an arrow for a direction because that is how a digraph's edge is drawn; a square matrix in double square brackets is
 * the adjacency matrix, read only by `walks`, where its entries count edges whatever the graph is.
 *
 * The functions: `graph` (its facts: degrees, components, tree, bipartite, Euler, chromatic number, planarity, the adjacency
 * matrix), `dijkstra` and `bellmanford` (shortest paths), `kruskal` and `prim` (minimum spanning trees), `toposort` (a topological
 * order or the cycle that prevents one), `maxflow` (Edmonds–Karp, with a minimum cut) and `walks` (the powers of the adjacency
 * matrix). Weights are exact fractions. Every answer is checked a second way before it is shown: distances against every edge,
 * a tree against the cycle property and the other algorithm, a flow against a cut of the same capacity, a trail edge by edge, a
 * colouring edge by edge and its number against an independent count; what fails is not shown.
 */
object Networks {
    /** The functions, by name, and the mode of each. */
    internal val MODES: Map<String, Mode> = linkedMapOf(
        "graph" to Mode.GRAPH_FACTS, "dijkstra" to Mode.DIJKSTRA, "bellmanford" to Mode.BELLMAN_FORD, "kruskal" to Mode.KRUSKAL,
        "prim" to Mode.PRIM, "toposort" to Mode.TOPOLOGICAL_ORDER, "maxflow" to Mode.MAX_FLOW, "walks" to Mode.WALKS,
    )

    /** At most this many vertices and edges: graphs typed on the keys are small. */
    internal const val MOST_VERTICES = 200
    internal const val MOST_EDGES = 1000

    // ------------------------------------------------------------------------------------------------------------------
    // What the outcomes hold.

    /**
     * A vertex of a [Picture]: its [name] (3, a, v_1), where it is drawn ([x] and [y] from 0 to 1, y downwards), its [group] (a
     * colour of a colouring, a side of a bipartite graph or of a cut, from 0; null where none) and whether it is [marked] (the
     * source and the target of a path, the source and the sink of a flow, the start of a tree).
     */
    data class Spot(val name: String, val x: Double, val y: Double, val group: Int? = null, val marked: Boolean = false)

    /**
     * An edge of a [Picture], from [from] to [to] (an arrow where the picture is directed): its [weight] (a capacity in a flow;
     * null where the graph has none), the [flow] on it (a flow's), whether it is [marked] (on the path, in the tree, in the cut,
     * on the cycle) and its [order] (its place in an Euler trail or in the order a tree took it, from 1; null where none).
     */
    data class Link(val from: String, val to: String, val weight: Expr? = null, val marked: Boolean = false, val order: Int? = null,
        val flow: Expr? = null)

    /** What the app draws of a graph: its vertices where they stand, its edges, and what the answer marks on them. */
    data class Picture(val directed: Boolean, val vertices: List<Spot>, val edges: List<Link>)

    enum class EulerKind { CIRCUIT, PATH, NONE }

    /**
     * An Euler trail: a [CIRCUIT] or a [PATH] through every edge exactly once, as its vertices in order ([trail], a circuit's
     * first vertex again at its end), or [NONE]. [odd] are the vertices of odd degree (in a directed graph those whose in- and
     * out-degree differ); [connected] whether the edges are all in one component.
     */
    data class Euler(val kind: EulerKind, val trail: List<String>, val odd: List<String>, val connected: Boolean)

    /**
     * Whether an undirected graph is bipartite: its two sides [left] and [right] (every edge between them), or else an [oddCycle]
     * (its vertices in order, the first not repeated), which no 2-colouring can have.
     */
    data class Sides(val bipartite: Boolean, val left: List<String>, val right: List<String>, val oddCycle: List<String>)

    /**
     * The chromatic number [number] and a colouring with as many colours ([colours], one per vertex in the graph's order, from
     * 1); [clique] a clique of [number] vertices where one was found (it needs as many colours: χ ≥ ω).
     */
    data class Colouring(val number: Int, val colours: List<Int>, val clique: List<String>?)

    enum class Kuratowski { K5, K33 }

    /**
     * Whether the graph can be drawn in the plane without crossings: where it can, its number of [faces] (Euler's formula
     * V − E + F = 1 + components, checked on an embedding); where it cannot, the [obstruction] its edges [subgraph] subdivide.
     */
    data class Planarity(val planar: Boolean, val faces: Int?, val obstruction: Kuratowski?, val subgraph: List<Pair<String, String>>)

    /**
     * A topological [order] of a directed graph, or else a [cycle] (its vertices in order, the first not repeated); [taken] the
     * vertices Kahn's algorithm took in turn (the order, or those before it found no vertex free of incoming edges).
     */
    data class Order(val order: List<String>?, val cycle: List<String>?, val taken: List<String>, val picture: Picture)

    /**
     * A graph's facts. [degrees] per vertex in the order of [vertices] (in a directed graph in- plus out-degree, which are
     * [inDegrees] and [outDegrees]); [sequence] the degrees from the largest; [components] (weak ones of a directed graph) and of a
     * directed graph its [strong] components; [tree] and [forest] of an undirected graph; [sides] whether it is bipartite; [euler]
     * its Euler trail; [colouring] its chromatic number (null where it has a loop, is directed, or is too large to decide);
     * [planarity] (null where it is directed or too large); [order] a directed graph's topological order or cycle; [adjacency]
     * its adjacency matrix (rows and columns in the order of [vertices]).
     */
    data class Facts(
        val directed: Boolean,
        val vertices: List<String>,
        val edges: Int,
        val loops: Int,
        val simple: Boolean,
        val degrees: List<Int>,
        val inDegrees: List<Int>?,
        val outDegrees: List<Int>?,
        val sequence: List<Int>,
        val components: List<List<String>>,
        val strong: List<List<String>>?,
        val tree: Boolean?,
        val forest: Boolean?,
        val sides: Sides?,
        val euler: Euler,
        val colouring: Colouring?,
        val planarity: Planarity?,
        val order: Order?,
        val adjacency: Expr.Mat,
        val picture: Picture,
    )

    enum class PathMethod { DIJKSTRA, BELLMAN_FORD }

    /**
     * Shortest paths from [source]: the [distances] to every vertex in the order of [vertices] (null where it cannot be reached),
     * each vertex's [previous] one on a shortest path to it, and to the [target] where one was asked for its [path] (null where it
     * cannot be reached) and [length]. [table] are the distances as the method went: first at the start (the source at 0, the
     * rest unknown), then Dijkstra's after each vertex it visited (in [visited], one fewer than the rows: row k follows the k-th
     * visit), Bellman–Ford's after each round (row k after round k). Where Bellman–Ford finds a [negativeCycle] reachable from the source (its
     * vertices in order, the first not repeated, of total [cycleWeight] < 0) there are no shortest paths: the distances are empty.
     */
    data class Paths(
        val method: PathMethod,
        val directed: Boolean,
        val vertices: List<String>,
        val source: String,
        val target: String?,
        val distances: List<Expr?>,
        val previous: List<String?>,
        val path: List<String>?,
        val length: Expr?,
        val table: List<List<Expr?>>,
        val visited: List<String>,
        val negativeCycle: List<String>?,
        val cycleWeight: Expr?,
        val picture: Picture,
    )

    enum class TreeMethod { KRUSKAL, PRIM }

    /** An edge a spanning tree method looked at: [taken] into the tree, or skipped because it would close a cycle. */
    data class TreeStep(val edge: Link, val taken: Boolean)

    /**
     * A minimum spanning tree: its [edges] in the order the method took them, their total [weight], and the [steps] (Kruskal's every
     * edge looked at in order of weight, taken or skipped; Prim's the edges taken, from [start]).
     */
    data class Tree(val method: TreeMethod, val vertices: List<String>, val edges: List<Link>, val weight: Expr, val steps: List<TreeStep>,
        val start: String?, val picture: Picture)

    /** A path along which a flow was raised, and by how much ([amount], its bottleneck). */
    data class Augmentation(val path: List<String>, val amount: Expr)

    /**
     * A maximum flow from [source] to [sink]: its [value], the flow on every edge ([edges], with their capacities as weights, in
     * the order given), the [augmentations] that made it, and a minimum cut: [cut] the side of the source, [cutEdges] the edges
     * from it to the other side, whose capacities add up to the value.
     */
    data class Flow(val source: String, val sink: String, val vertices: List<String>, val value: Expr, val edges: List<Link>,
        val augmentations: List<Augmentation>, val cut: List<String>, val cutEdges: List<Link>, val picture: Picture)

    // ------------------------------------------------------------------------------------------------------------------
    // The graph inside.

    /** An edge: from [u] to [v] (indices into the vertices), of weight [w] (1 where none was given), the [id]th as written. */
    internal class Arc(val u: Int, val v: Int, val w: Rational, val id: Int)

    /** A graph: its vertices' [names] in their order, its [arcs] in the order given, [weighted] where they had weights. */
    internal class Net(val names: List<String>, val arcs: List<Arc>, val directed: Boolean, val weighted: Boolean, val weightTexts: List<String?>) {
        val n: Int get() = names.size

        /** Each vertex's edges out: the edge and the vertex at its other end (an undirected edge from both of its ends). */
        val out: List<List<Pair<Arc, Int>>> by lazy {
            val lists = List(n) { ArrayList<Pair<Arc, Int>>() }
            for (a in arcs) {
                lists[a.u] += a to a.v
                if (!directed && a.u != a.v) lists[a.v] += a to a.u
            }
            lists
        }

        fun index(name: String): Int = names.indexOf(name)

        fun weightOf(a: Arc): Expr? = if (weighted) num(a.w) else null

        fun link(a: Arc, from: Int = a.u, to: Int = a.v, marked: Boolean = false, order: Int? = null, flow: Expr? = null): Link =
            Link(names[from], names[to], weightOf(a), marked, order, flow)
    }

    /** A call read: its function, its graph (or adjacency [matrix]), and its other arguments as written ([rest]). */
    internal class Call(val name: String, val mode: Mode, val net: Net?, val matrix: List<List<BigInteger>>?, val rest: List<String>,
        val decimals: Boolean, val fractions: Boolean, val latex: String)

    // ------------------------------------------------------------------------------------------------------------------
    // Reading and writing a call.

    /** A call of one of [MODES] typed (its arguments as [CommandFunctions] split them), as its canonical LaTeX; null where it is none. */
    internal fun typed(name: String, args: List<String>): Typed? {
        // A call too large to read is read as itself, which fails when it is worked out (TOO_LARGE).
        val call = try { readCall(name, args) } catch (e: CalcException) { null } ?: return null
        return Typed(call.latex, Choice(call.mode))
    }

    private val CALL = Regex("""^\\operatorname\{([A-Za-z]+)\}\s*\\left\((.*)\\right\)$""", RegexOption.DOT_MATCHES_ALL)

    /** The call [latex] holds (as [typed] writes it, or as the editor does), or null where it is no call of these functions. */
    internal fun callOf(latex: String): Pair<String, List<String>?>? {
        val m = CALL.matchEntire(latex.trim()) ?: return null
        val name = m.groupValues[1].lowercase()
        if (name !in MODES) return null
        return name to split(normal(m.groupValues[2]))
    }

    /**
     * [latex] worked out where it is a call of these functions; null where it is none. [choice] is ignored: each call has its one
     * mode.
     */
    internal fun calculation(latex: String, millis: Long, fractions: Boolean): Calculation? {
        val (name, args) = callOf(latex) ?: return null
        val mode = MODES.getValue(name)
        val choice = Choice(mode)
        var decimals = false
        var bar = fractions
        val outcome = try {
            val call = args?.let { readCall(name, it) } ?: throw CalcException(Failure.ARGUMENTS)
            // Weights written as decimals, and none as a fraction: the answers in decimals too (2.5 + 1.25 = 3.75).
            decimals = call.decimals
            if (decimals && !call.fractions) bar = false
            Budget.within(millis) { outcome(call) }
        } catch (e: CalcException) {
            Outcome.Failed(e.failure)
        } catch (e: ArithmeticException) {
            Outcome.Failed(Failure.UNDEFINED)
        } catch (e: StackOverflowError) {
            Outcome.Failed(Failure.TOO_LARGE)
        }
        return Calculation(outcome, false, decimals, bar, listOf(choice), choice)
    }

    private fun outcome(call: Call): Outcome {
        val net = call.net
        return when (call.mode) {
            Mode.GRAPH_FACTS -> Outcome.GraphFacts(NetworkFacts.facts(net!!))
            Mode.DIJKSTRA, Mode.BELLMAN_FORD -> {
                val source = net!!.index(call.rest[0])
                val target = call.rest.getOrNull(1)?.let { net.index(it) }
                Outcome.ShortestPaths(NetworkPaths.paths(net, source, target, call.mode == Mode.BELLMAN_FORD))
            }
            Mode.KRUSKAL -> Outcome.SpanningTree(NetworkTrees.tree(net!!, kruskal = true, start = null))
            Mode.PRIM -> Outcome.SpanningTree(NetworkTrees.tree(net!!, kruskal = false, start = call.rest.firstOrNull()?.let { net.index(it) }))
            Mode.TOPOLOGICAL_ORDER -> Outcome.TopologicalOrder(NetworkFacts.order(net!!))
            Mode.MAX_FLOW -> Outcome.MaxFlow(NetworkFlow.flow(net!!, net.index(call.rest[0]), net.index(call.rest[1])))
            Mode.WALKS -> NetworkWalks.walks(call)
            else -> throw CalcException(Failure.UNREADABLE)
        }
    }

    /** An edge as written: its ends' names, its weight's text (null where none) and whether it has a direction. */
    private class Written(val u: String, val v: String, val weight: String?, val directed: Boolean)

    /** A call's arguments read, checked against what its function takes; null where they do not fit ([Failure.ARGUMENTS]). */
    internal fun readCall(name: String, args: List<String>): Call? {
        val mode = MODES[name] ?: return null
        val edges = ArrayList<Written>()
        var matrix: List<List<BigInteger>>? = null
        val rest = ArrayList<String>()
        for (a0 in args) {
            val a = normal(a0)
            when {
                a.isEmpty() -> return null
                isMatrix(a) -> {
                    if (mode != Mode.WALKS || edges.isNotEmpty() || rest.isNotEmpty() || matrix != null) return null
                    matrix = matrixOf(a) ?: return null
                }
                a.startsWith("(") && closing(a, 0) == a.length - 1 -> {
                    if (rest.isNotEmpty() || matrix != null) return null
                    edges += edge(a.substring(1, a.length - 1)) ?: return null
                }
                else -> rest += vertexName(a) ?: return null
            }
        }
        if (edges.size > MOST_EDGES) throw CalcException(Failure.TOO_LARGE)
        val directed = edges.firstOrNull()?.directed ?: false
        val weighted = edges.firstOrNull()?.weight != null
        if (edges.any { it.directed != directed || (it.weight != null) != weighted }) return null
        // The arguments after the edges each function takes.
        val restFits = when (mode) {
            Mode.GRAPH_FACTS -> edges.isNotEmpty() || rest.isNotEmpty()
            Mode.DIJKSTRA, Mode.BELLMAN_FORD -> edges.isNotEmpty() && rest.size in 1..2
            Mode.KRUSKAL -> edges.isNotEmpty() && rest.isEmpty() && !directed
            Mode.PRIM -> edges.isNotEmpty() && rest.size <= 1 && !directed
            Mode.TOPOLOGICAL_ORDER -> edges.isNotEmpty() && rest.isEmpty() && directed
            Mode.MAX_FLOW -> edges.isNotEmpty() && rest.size == 2 && directed && rest[0] != rest[1]
            Mode.WALKS -> (edges.isNotEmpty() || matrix != null) && rest.size in setOf(1, 3) && rest[0].all { it.isDigit() }
            else -> false
        }
        if (!restFits) return null
        var decimals = false
        var bars = false
        val texts = ArrayList<String?>()
        val weights = ArrayList<Rational>()
        for (e in edges) {
            if (e.weight == null) {
                texts += null
                weights += Rational.ONE
            } else {
                val (w, decimal) = weightOf(e.weight) ?: return null
                if (decimal) decimals = true
                if ('/' in e.weight || "\\frac" in e.weight) bars = true
                texts += TextInput.latex(e.weight)
                weights += w
            }
        }
        // The vertices: those of the edges, and for graph(…) those named after them (isolated ones among them).
        val named = LinkedHashSet<String>()
        for (e in edges) { named += e.u; named += e.v }
        val extra = if (mode == Mode.GRAPH_FACTS) rest else emptyList()
        named += extra
        val names = named.sortedWith(VERTEX_ORDER)
        if (names.size > MOST_VERTICES) throw CalcException(Failure.TOO_LARGE)
        val net = if (matrix != null) null else {
            val index = names.withIndex().associate { (k, s) -> s to k }
            Net(names, edges.mapIndexed { k, e -> Arc(index.getValue(e.u), index.getValue(e.v), weights[k], k) }, directed, weighted, texts)
        }
        // The vertices the function names are the graph's: a source, a target, a start (for walks after its length).
        val asked = when (mode) {
            Mode.GRAPH_FACTS -> emptyList()
            Mode.WALKS -> rest.drop(1)
            else -> rest
        }
        if (matrix != null) {
            if (asked.any { a -> a.toIntOrNull()?.let { it in 1..matrix.size } != true }) return null
        } else if (asked.any { it !in names }) return null
        if (mode == Mode.WALKS && rest[0].length > 4) return null
        val latex = write(name, edges, texts, matrix, rest)
        return Call(name, mode, net, matrix, rest, decimals, bars, latex)
    }

    /** The canonical LaTeX of a call: `\operatorname{dijkstra}\left(\left(1, 2, 4\right), 1, 3\right)`. */
    private fun write(name: String, edges: List<Written>, texts: List<String?>, matrix: List<List<BigInteger>>?, rest: List<String>): String {
        val args = ArrayList<String>()
        for ((k, e) in edges.withIndex()) {
            val ends = if (e.directed) Format.name(e.u, latex = true) + " \\to " + Format.name(e.v, latex = true)
                else Format.name(e.u, latex = true) + ", " + Format.name(e.v, latex = true)
            args += "\\left(" + ends + (texts[k]?.let { ", $it" } ?: "") + "\\right)"
        }
        if (matrix != null) args += "\\begin{pmatrix}" + matrix.joinToString("\\\\") { it.joinToString("&") } + "\\end{pmatrix}"
        args += rest.map { Format.name(it, latex = true) }
        return "\\operatorname{$name}\\left(" + args.joinToString(", ") + "\\right)"
    }

    /** The LaTeX dressing taken off: \left( is (, \to is →. */
    private fun normal(s: String): String = s
        .replace("\\rightarrow", "→").replace("\\longrightarrow", "→").replace("\\to", "→").replace("->", "→")
        .replace("\\left(", "(").replace("\\right)", ")").replace("\\left[", "[").replace("\\right]", "]")
        .replace("\\,", " ").replace("\\ ", " ").replace("\\;", " ")
        .replace(Regex("""\\operatorname\{([A-Za-z]+)\}"""), "$1")
        .trim()

    /** [s] split at its commas outside every bracket (null where a bracket does not close). */
    private fun split(s: String): List<String>? {
        val out = ArrayList<String>()
        var depth = 0
        val current = StringBuilder()
        for (c in s) {
            when (c) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
            }
            if (depth < 0) return null
            if (c == ',' && depth == 0) {
                out += current.toString().trim()
                current.setLength(0)
            } else {
                current.append(c)
            }
        }
        if (depth != 0) return null
        out += current.toString().trim()
        return out
    }

    private fun closing(s: String, at: Int): Int {
        var depth = 0
        for (k in at until s.length) {
            when (s[k]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> { depth--; if (depth == 0) return k }
            }
        }
        return -1
    }

    /** The inside of an edge's brackets: u, v or u, v, w undirected, u → v or u → v, w directed. */
    private fun edge(inside: String): Written? {
        val parts = split(inside) ?: return null
        val first = parts[0]
        return if ('→' in first) {
            val ends = first.split('→')
            if (ends.size != 2 || parts.size > 2) return null
            val w = parts.getOrNull(1)
            if (w != null && (w.isBlank() || '→' in w)) return null
            Written(vertexName(ends[0]) ?: return null, vertexName(ends[1]) ?: return null, w, true)
        } else {
            if (parts.size !in 2..3 || parts.any { '→' in it || it.isBlank() }) return null
            Written(vertexName(parts[0]) ?: return null, vertexName(parts[1]) ?: return null, parts.getOrNull(2), false)
        }
    }

    private val NUMBER_NAME = Regex("""^[0-9]{1,4}$""")
    private val LETTER_NAME = Regex("""^([A-Za-z])(?:_?\{?([0-9]{1,3})\}?)?$""")

    /** A vertex's name: a whole number (3, from 0 to 9999), a letter (a, B) or a letter with an index (v_1, typed v1 or v_1). */
    internal fun vertexName(raw: String): String? {
        val t = raw.trim().replace(" ", "")
        if (NUMBER_NAME.matches(t)) return t.toInt().toString()
        val m = LETTER_NAME.matchEntire(t) ?: return null
        if (m.value.count { it == '{' } != m.value.count { it == '}' }) return null
        return if (m.groupValues[2].isEmpty()) m.groupValues[1] else m.groupValues[1] + "_" + m.groupValues[2].toInt()
    }

    /** Numbers first, by value; then letters, a capital before its small letter's run (A, B, a, b), each by its index. */
    internal val VERTEX_ORDER: Comparator<String> = Comparator { a, b ->
        fun key(s: String): Triple<Int, Int, Int> = if (s[0].isDigit()) Triple(0, s.toInt(), 0)
            else Triple(1, s[0].code, s.substringAfter('_', "").toIntOrNull()?.plus(1) ?: 0)
        val (x, y) = key(a) to key(b)
        compareValuesBy(x, y, { it.first }, { it.second }, { it.third })
    }

    /** A weight: an exact number (whole, a fraction, a decimal with a point), and whether it was written as a decimal. */
    private fun weightOf(text: String): Pair<Rational, Boolean>? = try {
        val latex = TextInput.latex(text)
        val tokens = MathCalc.TOKEN.findAll(latex).map { it.value }.toList()
        val flags = Flags()
        val statements = Parser(tokens, flags).statements()
        val sides = statements.singleOrNull()?.sides
        val side = sides?.singleOrNull()
        val r = side?.let { rational(Calculus.evaluate(it)) }
        r?.let { it to (flags.decimals || '.' in text) }
    } catch (e: CalcException) {
        null
    } catch (e: RuntimeException) {
        null
    }

    private fun isMatrix(a: String): Boolean = a.startsWith("\\begin{pmatrix}") || a.replace(" ", "").startsWith("[[")

    /** An adjacency matrix: square, of whole numbers from 0 (the edges from the row's vertex to the column's). */
    private fun matrixOf(a: String): List<List<BigInteger>>? {
        val rows: List<List<String>> = if (a.startsWith("\\begin{pmatrix}")) {
            if (!a.endsWith("\\end{pmatrix}")) return null
            a.removePrefix("\\begin{pmatrix}").removeSuffix("\\end{pmatrix}").split("\\\\").map { r -> r.split('&').map { it.trim() } }
        } else {
            val t = a.replace(" ", "")
            if (!t.startsWith("[[") || !t.endsWith("]]") || closing(t, 0) != t.length - 1) return null
            val inner = split(t.substring(1, t.length - 1)) ?: return null
            inner.map { r ->
                if (!r.startsWith("[") || !r.endsWith("]")) return null
                split(r.substring(1, r.length - 1)) ?: return null
            }
        }
        val n = rows.size
        if (n == 0 || n > MOST_VERTICES || rows.any { it.size != n }) return null
        return rows.map { r -> r.map { c -> if (c.isNotEmpty() && c.length <= 6 && c.all { it.isDigit() }) BigInteger(c) else return null } }
    }

    /** A cycle's vertices from its first in the graph's order on, in the same direction. */
    internal fun fromSmallest(cycle: List<Int>): List<Int> {
        val k = cycle.indices.minByOrNull { cycle[it] } ?: return cycle
        return cycle.drop(k) + cycle.take(k)
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Pictures.

    /**
     * Where each vertex is drawn: in [layers] where given (a tree's levels, a DAG's, a flow's distances from its source), each layer
     * a row from the top and its vertices spread across in their order; else on a circle, from the top clockwise.
     */
    internal fun positions(n: Int, layers: IntArray? = null): List<Pair<Double, Double>> {
        if (n == 1) return listOf(0.5 to 0.5)
        if (layers != null) {
            val most = layers.maxOrNull() ?: 0
            val byLayer = (0 until n).groupBy { layers[it] }
            val out = Array(n) { 0.5 to 0.5 }
            for ((layer, members) in byLayer) {
                val y = if (most == 0) 0.5 else 0.05 + 0.9 * layer / most
                for ((k, v) in members.withIndex()) out[v] = (if (members.size == 1) 0.5 else 0.05 + 0.9 * k / (members.size - 1)) to y
            }
            return out.toList()
        }
        return (0 until n).map { k ->
            val angle = -PI / 2 + 2 * PI * k / n
            (0.5 + 0.45 * cos(angle)) to (0.5 + 0.45 * sin(angle))
        }
    }

    internal fun picture(net: Net, layers: IntArray? = null, groups: List<Int?>? = null, marked: Set<Int> = emptySet(),
        links: List<Link> = net.arcs.map { net.link(it) }): Picture {
        val at = positions(net.n, layers)
        return Picture(net.directed, net.names.indices.map { k -> Spot(net.names[k], at[k].first, at[k].second, groups?.get(k), k in marked) }, links)
    }

    /** The distance of each vertex from [from] along edges (taken without their direction), for a picture's rows; others below. */
    internal fun levels(net: Net, from: Int): IntArray {
        val depth = IntArray(net.n) { -1 }
        depth[from] = 0
        val queue = ArrayDeque<Int>()
        queue += from
        val both = List(net.n) { ArrayList<Int>() }
        for (a in net.arcs) { both[a.u] += a.v; both[a.v] += a.u }
        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            for (v in both[u]) if (depth[v] < 0) { depth[v] = depth[u] + 1; queue += v }
        }
        val most = depth.maxOrNull() ?: 0
        for (k in depth.indices) if (depth[k] < 0) depth[k] = most + 1
        return depth
    }
}

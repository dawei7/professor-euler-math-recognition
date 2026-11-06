package com.professoreuler.mathcalc

import java.math.BigInteger

/**
 * Worked steps for the graph functions, from what their outcomes hold: Dijkstra's visits and the distances they lower,
 * Bellman–Ford's rounds, the edges Kruskal and Prim take or skip, the vertices of a topological order, the augmenting paths and
 * the cut of a maximum flow, and the powers of the adjacency matrix. Each ends with the check that proves the answer.
 */
internal object NetworkSteps {
    fun of(c: Calculation, latex: String): List<Step>? = try {
        Builder(c, latex).build()?.takeIf { it.size >= 2 }
    } catch (e: CalcException) {
        null
    } catch (e: ArithmeticException) {
        null
    }

    private class Builder(val c: Calculation, val latex: String) {
        val out = ArrayList<Step>()

        fun step(shown: String, key: StepKey, vararg args: String) {
            out += Step(Steps.tidy(shown), StepText(key, args.toList()))
        }

        fun n(name: String) = Format.name(name, latex = true)
        fun x(e: Expr?) = if (e == null) "\\infty" else Format.expr(e, c, latex = true)
        fun edge(l: Networks.Link, directed: Boolean) =
            (if (directed) "(${n(l.from)} \\to ${n(l.to)})" else "\\{${n(l.from)}, ${n(l.to)}\\}") + (l.weight?.let { ":\\ w = " + x(it) } ?: "")
        fun walk(vertices: List<String>) = vertices.joinToString(" \\to ") { n(it) }
        fun distances(names: List<String>, row: List<Expr?>) = names.indices.joinToString(",\\ ") { "d(${n(names[it])}) = " + x(row[it]) }

        fun build(): List<Step>? {
            when (val o = c.outcome) {
                is Outcome.ShortestPaths -> paths(o.paths)
                is Outcome.SpanningTree -> tree(o.tree)
                is Outcome.TopologicalOrder -> order(o.order)
                is Outcome.MaxFlow -> flow(o.flow)
                is Outcome.Value, is Outcome.Expression -> if (c.mode == Mode.WALKS) walks(o) else return null
                else -> return null
            }
            return out
        }

        fun paths(p: Networks.Paths) {
            val names = p.vertices
            step("d(${n(p.source)}) = 0", StepKey.GRAPH_SOURCE, n(p.source))
            if (p.method == Networks.PathMethod.DIJKSTRA) {
                for ((k, u) in p.visited.withIndex()) {
                    val before = p.table[k]
                    val after = p.table[k + 1]
                    val at = names.indexOf(u)
                    step("d(${n(u)}) = " + x(after[at]), StepKey.GRAPH_VISIT, n(u), x(after[at]))
                    val du = rational(after[at]!!)!!
                    val lowered = names.indices.filter { it != at && after[it] != before[it] }
                    if (lowered.isNotEmpty()) {
                        step(lowered.joinToString(",\\ ") { v ->
                            val dv = rational(after[v]!!)!!
                            "d(${n(names[v])}) = " + x(num(du)) + " + " + x(num(dv - du)) + " = " + x(after[v])
                        }, StepKey.GRAPH_LOWER, n(u))
                    }
                }
            } else {
                for (r in 1 until p.table.size) {
                    val row = p.table[r]
                    if (row == p.table[r - 1]) step(distances(names, row), StepKey.GRAPH_ROUND_STABLE, "$r")
                    else step(distances(names, row), StepKey.GRAPH_ROUND, "$r")
                }
            }
            if (p.negativeCycle != null) {
                step(walk(p.negativeCycle + p.negativeCycle.first()), StepKey.GRAPH_NEGATIVE_CYCLE, x(p.cycleWeight))
                return
            }
            step("d(v) \\le d(u) + w(u, v)", StepKey.GRAPH_DISTANCES_CHECK)
            if (p.target != null) {
                if (p.path != null) step(walk(p.path), StepKey.GRAPH_PATH, n(p.target), n(p.source))
                step("d(${n(p.source)}, ${n(p.target)}) = " + x(p.length), StepKey.RESULT)
            } else {
                step(distances(names, p.distances), StepKey.RESULT)
            }
        }

        fun tree(t: Networks.Tree) {
            if (t.method == Networks.TreeMethod.KRUSKAL) {
                // Every edge by weight, the one written first first where two weigh the same (the picture keeps their order).
                val sorted = t.picture.edges.sortedWith(compareBy { rational(it.weight ?: Expr.ONE)!! })
                step(sorted.joinToString(",\\ ") { edge(it, false) }, StepKey.GRAPH_SORT_EDGES)
                for (s in t.steps) step(edge(s.edge, false), if (s.taken) StepKey.GRAPH_TAKE_EDGE else StepKey.GRAPH_SKIP_EDGE, edge(s.edge, false))
            } else {
                step("T = \\{${n(t.start!!)}\\}", StepKey.GRAPH_PRIM_START, n(t.start))
                for (s in t.steps) step(edge(s.edge, false), StepKey.GRAPH_PRIM_EDGE, edge(s.edge, false))
            }
            step("|T| = |V| - 1 = ${t.vertices.size - 1}", StepKey.GRAPH_TREE_CHECK)
            step("w(T) = " + x(t.weight), StepKey.RESULT)
        }

        fun order(o: Networks.Order) {
            for (k in o.taken.indices) step(o.taken.subList(0, k + 1).joinToString(",\\ ") { n(it) }, StepKey.GRAPH_TOPO_TAKE, n(o.taken[k]))
            if (o.cycle != null) {
                step(walk(o.cycle + o.cycle.first()), StepKey.GRAPH_TOPO_CYCLE)
                return
            }
            step(o.order!!.joinToString(",\\ ") { n(it) }, StepKey.GRAPH_TOPO_CHECK)
        }

        fun flow(f: Networks.Flow) {
            var total = Rational.ZERO
            for (a in f.augmentations) {
                total += rational(a.amount)!!
                step(walk(a.path) + ":\\ +" + x(a.amount) + ",\\ |f| = " + x(num(total)), StepKey.GRAPH_AUGMENT, x(a.amount))
            }
            step("S = \\{" + f.cut.joinToString(", ") { n(it) } + "\\},\\ c(S, \\bar{S}) = " + x(f.value), StepKey.GRAPH_MIN_CUT, x(f.value))
            step("|f| = " + x(f.value), StepKey.RESULT)
        }

        fun walks(o: Outcome) {
            val (name, args) = Networks.callOf(latex) ?: return
            val call = Networks.readCall(name, args ?: return) ?: return
            val a = call.matrix ?: NetworkWalks.adjacency(call.net!!)
            val k = call.rest[0].toInt()
            fun matrix(m: List<List<BigInteger>>) = "\\begin{pmatrix}" + m.joinToString("\\\\") { it.joinToString("&") } + "\\end{pmatrix}"
            step("A = " + matrix(a), StepKey.GRAPH_ADJACENCY)
            step("A^{$k} = " + matrix(NetworkWalks.power(a, k)), StepKey.GRAPH_MATRIX_POWER, "$k")
            if (o is Outcome.Value) {
                val (u, v) = call.rest[1] to call.rest[2]
                step("\\left(A^{$k}\\right)_{${n(u)}, ${n(v)}} = " + c.latex(o.value), StepKey.RESULT)
            }
        }
    }
}

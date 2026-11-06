package com.professoreuler.mathcalc

import java.math.BigInteger

/**
 * The number of walks of length k: entry (u, v) of A^k, where A is the adjacency matrix (A_uv the number of edges from u to v; an
 * undirected edge counts both ways, a loop once). A^k by repeated squaring, checked by counting the walks a second way, one step
 * at a time along the edges (the walks of length j + 1 ending at v are those of length j ending at a vertex with an edge to v).
 */
internal object NetworkWalks {
    /** The largest graph and length: A^k of a larger one is a matrix of numbers too long to read. */
    private const val MOST_VERTICES = 30
    private const val MOST_LENGTH = 100

    fun walks(call: Networks.Call): Outcome {
        val a: List<List<BigInteger>> = call.matrix ?: adjacency(call.net!!)
        val n = a.size
        val k = call.rest[0].toInt()
        if (n > MOST_VERTICES || k > MOST_LENGTH) throw CalcException(Failure.TOO_LARGE)
        val power = power(a, k)
        // The walks counted step by step from each vertex.
        val rows = if (call.rest.size == 3) listOf(index(call, call.rest[1])) else (0 until n).toList()
        for (u in rows) {
            var w = List(n) { if (it == u) BigInteger.ONE else BigInteger.ZERO }
            repeat(k) {
                val next = MutableList(n) { BigInteger.ZERO }
                for (x in 0 until n) {
                    if (w[x].signum() == 0) continue
                    for (y in 0 until n) if (a[x][y].signum() != 0) { Budget.check(); next[y] = next[y] + w[x] * a[x][y] }
                }
                w = next
            }
            if (w != power[u]) throw CalcException(Failure.UNSOLVED)
        }
        if (call.rest.size == 3) {
            val count = power[index(call, call.rest[1])][index(call, call.rest[2])]
            return Outcome.Value(Num.Exact(Rational.of(count)))
        }
        return Outcome.Expression(Expr.Mat(power.map { r -> r.map { num(Rational.of(it)) } }))
    }

    private fun index(call: Networks.Call, name: String): Int = if (call.matrix != null) name.toInt() - 1 else call.net!!.index(name)

    /** The adjacency matrix: A_uv the number of edges from u to v (an undirected edge both ways, a loop once). */
    fun adjacency(net: Networks.Net): List<List<BigInteger>> {
        val a = Array(net.n) { Array(net.n) { 0L } }
        for (e in net.arcs) {
            a[e.u][e.v]++
            if (!net.directed && e.u != e.v) a[e.v][e.u]++
        }
        return a.map { r -> r.map { BigInteger.valueOf(it) } }
    }

    private fun times(x: List<List<BigInteger>>, y: List<List<BigInteger>>): List<List<BigInteger>> {
        val n = x.size
        return List(n) { i ->
            List(n) { j ->
                var s = BigInteger.ZERO
                for (k in 0 until n) if (x[i][k].signum() != 0 && y[k][j].signum() != 0) { Budget.check(); s += x[i][k] * y[k][j] }
                s
            }
        }
    }

    fun power(a: List<List<BigInteger>>, k: Int): List<List<BigInteger>> {
        var result = List(a.size) { i -> List(a.size) { j -> if (i == j) BigInteger.ONE else BigInteger.ZERO } }
        var base = a
        var e = k
        while (e > 0) {
            if (e and 1 == 1) result = times(result, base)
            e = e shr 1
            if (e > 0) base = times(base, base)
        }
        return result
    }
}

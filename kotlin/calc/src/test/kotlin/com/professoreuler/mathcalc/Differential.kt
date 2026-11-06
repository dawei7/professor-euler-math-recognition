package com.professoreuler.mathcalc

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The engine's answers to a file of tasks, for the developer's comparison with SymPy (`tools/calc_differential.py`; never
 * shipped, run by `gradlew :calc:differential --args="<tasks> <answers>"`). Each line of the tasks is `id<TAB>typed line`,
 * worked out as the Calculator works it out ([TextInput.read], [Reports.of]); each line of the answers is a JSON object with
 * the outcome's kind, its line of text ([answerOf]), its expressions in SymPy's syntax ([Sympy.of]) and the time it took.
 */
fun main(args: Array<String>) {
    require(args.size >= 2) { "usage: differential <tasks> <answers> [threads]" }
    val tasks = File(args[0]).readLines().filter { it.isNotBlank() }.map { line ->
        val tab = line.indexOf('\t')
        line.substring(0, tab) to line.substring(tab + 1)
    }
    val threads = args.getOrNull(2)?.toIntOrNull() ?: 1
    val pool = Executors.newFixedThreadPool(threads)
    val answers = tasks.map { (id, text) -> pool.submit<String> { Differential.answer(id, text) } }
    File(args[1]).bufferedWriter().use { out -> answers.forEach { out.write(it.get()); out.write("\n") } }
    pool.shutdown()
    pool.awaitTermination(1, TimeUnit.MINUTES)
}

internal object Differential {
    fun answer(id: String, text: String): String {
        val start = System.nanoTime()
        val c = try {
            Reports.of(TextInput.read(text)).result
        } catch (e: Throwable) {
            return json(mapOf("id" to id, "kind" to "Crash", "answer" to (e::class.simpleName + ": " + e.message), "exprs" to emptyList<String>(),
                "ms" to (System.nanoTime() - start) / 1e6))
        }
        val ms = (System.nanoTime() - start) / 1e6
        val o = c.outcome
        val exprs: List<String?> = when (o) {
            is Outcome.Value -> listOf(Sympy.of(o.exact ?: (o.value.rational?.let { num(it) } ?: Expr.Real(o.value.double))))
            is Outcome.Expression -> listOf(Sympy.of(o.result))
            is Outcome.Factored -> listOf(Sympy.of(o.result))
            is Outcome.Solved -> o.solutions.values.map { Sympy.of(it) }
            is Outcome.DefiniteIntegral -> listOf(o.exact?.let { Sympy.of(it) } ?: o.value?.let { Sympy.of(Expr.Real(it)) })
            is Outcome.Intervals -> o.set.intervals.flatMap { listOf(it.lower?.let(Sympy::of) ?: "-oo", it.upper?.let(Sympy::of) ?: "oo") }
            else -> emptyList()
        }
        val extra = HashMap<String, Any?>()
        if (o is Outcome.Solved) {
            extra["complex"] = o.solutions.complex.map { Sympy.of(it) }
            extra["periodic"] = o.solutions.periodic.map { Sympy.of(it) }
            extra["always"] = o.solutions.always
        }
        if (o is Outcome.Factored) extra["complete"] = o.complete
        if (o is Outcome.Expression) extra["plusC"] = o.plusC
        if (o is Outcome.Intervals) extra["closed"] = o.set.intervals.flatMap { listOf(it.lowerClosed, it.upperClosed) }
        if (o is Outcome.Expression && o.series != null) {
            extra["series"] = o.series!!.map { (k, j) -> listOf(Sympy.of(k), j) }
            extra["seriesIn"] = o.seriesIn?.let { Sympy.of(it) }
        }
        return json(mapOf("id" to id, "kind" to o::class.simpleName, "answer" to answerOf(c), "exprs" to exprs, "ms" to ms) + extra)
    }

    private fun json(v: Any?): String = when (v) {
        null -> "null"
        is String -> "\"" + v.flatMap { ch ->
            when (ch) {
                '"' -> listOf('\\', '"'); '\\' -> listOf('\\', '\\'); '\n' -> listOf('\\', 'n'); '\t' -> listOf('\\', 't')
                else -> if (ch < ' ') "\\u%04x".format(ch.code).toList() else listOf(ch)
            }
        }.joinToString("") + "\""
        is Boolean, is Int, is Long -> v.toString()
        is Double -> if (v.isFinite()) v.toString() else "null"
        is Map<*, *> -> v.entries.joinToString(", ", "{", "}") { json(it.key.toString()) + ": " + json(it.value) }
        is List<*> -> v.joinToString(", ", "[", "]") { json(it) }
        else -> json(v.toString())
    }
}

/** An expression in SymPy's syntax, to be read there with `sympify` (or evaluated in its namespace); null where it holds calculus. */
internal object Sympy {
    fun of(e: Expr): String? = try { write(e) } catch (x: IllegalStateException) { null }

    private fun write(e: Expr): String = when (e) {
        is Expr.Num -> if (e.value.isInteger) "Integer(${e.value.num})" else "Rational(${e.value.num}, ${e.value.den})"
        is Expr.Real -> "Float('${e.value}', 17)"
        is Expr.Sym -> "Symbol('${e.name}')"
        is Expr.Const -> when (e.kind) {
            Constant.PI -> "pi"
            Constant.E -> "E"
            Constant.I -> "I"
            Constant.INF -> "oo"
        }
        is Expr.Add -> e.terms.joinToString(" + ", "(", ")") { write(it) }
        is Expr.Mul -> e.factors.joinToString("*", "(", ")") { write(it) }
        is Expr.Pow -> "Pow(${write(e.base)}, ${write(e.exp)})"
        is Expr.Fn -> {
            val a = e.args.map { write(it) }
            when (e.f) {
                Func.SIN -> "sin(${a[0]})"; Func.COS -> "cos(${a[0]})"; Func.TAN -> "tan(${a[0]})"
                Func.COT -> "cot(${a[0]})"; Func.SEC -> "sec(${a[0]})"; Func.CSC -> "csc(${a[0]})"
                Func.ASIN -> "asin(${a[0]})"; Func.ACOS -> "acos(${a[0]})"; Func.ATAN -> "atan(${a[0]})"
                // The engine's arccot is in (0, π).
                Func.ACOT -> "(pi/2 - atan(${a[0]}))"
                Func.SINH -> "sinh(${a[0]})"; Func.COSH -> "cosh(${a[0]})"; Func.TANH -> "tanh(${a[0]})"
                Func.LN -> "log(${a[0]})"
                Func.LOG -> "log(${a[1]}, ${a[0]})"
                Func.ABS -> "Abs(${a[0]})"; Func.FLOOR -> "floor(${a[0]})"; Func.CEIL -> "ceiling(${a[0]})"
                Func.FACTORIAL -> "factorial(${a[0]})"; Func.SIGN -> "sign(${a[0]})"
                Func.GAMMA -> "gamma(${a[0]})"; Func.DIGAMMA -> "polygamma(0, ${a[0]})"
                Func.ERF -> "erf(${a[0]})"; Func.ERFC -> "erfc(${a[0]})"; Func.ZETA -> "zeta(${a[0]})"
                Func.BESSELJ -> "besselj(${a[0]}, ${a[1]})"; Func.LAMBERTW -> "LambertW(${a[0]})"
            }
        }
        is Expr.Mat -> e.rows.joinToString(", ", "Matrix([", "])") { row -> row.joinToString(", ", "[", "]") { write(it) } }
        is Expr.Op -> error("calculus left in the result")
    }
}

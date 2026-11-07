package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The written corpus (the plan's quality measure, 6 November 2026): over 5,000 tasks across every area the engine has, each with
 * its answer known without the engine (a textbook value, or the test's own computation: exact fractions, a numeric value at
 * points, a quadrature, brute force for discrete tasks). Reports for each area the share right, declined and wrong, and the
 * time per task (median, 95th percentile, the slowest ten). An answer that is not the answer fails the test: the engine may
 * decline, never be wrong.
 *
 * `CALC_CORPUS_THREADS` sets the threads (1 for the timing the report gives); `CALC_CORPUS_REPORT` a file for the report.
 */
class WrittenCorpusTest {
    @Test
    fun writtenCorpus() {
        val digits = WrittenNumbers.digits()
        val (wrong, ran) = WrittenCorpusRun.run(WrittenCorpus.all(), "Written corpus", digits, System.getenv("CALC_CORPUS_REPORT"), ::runDigits)
        assertEquals("", wrong)
        assert(ran >= 5000) { "only $ran tasks" }
    }

    /**
     * The areas added after the first corpus (7 November 2026): partial differential equations, the applied functions, financial
     * mathematics, machine learning, graphs, the statistics functions, several variables, the new functions, iterations,
     * algorithms, constants and plots, the matrix forms and further differential equations, interpolation and number functions.
     * `CALC_CORPUS_AREAS` (a comma-separated list) runs only those areas; `CALC_CORPUS_LATER_REPORT` a file for the report.
     */
    @Test
    fun laterAreas() {
        val only = System.getenv("CALC_CORPUS_AREAS")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()?.takeIf { it.isNotEmpty() }
        val tasks = WrittenCorpus.later().filter { only == null || it.area in only }
        val (wrong, ran) = WrittenCorpusRun.run(tasks, "Written corpus, later areas", emptyList(), System.getenv("CALC_CORPUS_LATER_REPORT"), ::runDigits)
        assertEquals("", wrong)
        if (only == null) assert(ran >= 3000) { "only $ran tasks" }
    }

    /** The certified digits of a constant, as the Calculator's Digits panel works them out; null where they are right. */
    private fun runDigits(d: WrittenNumbers.DigitsTask): String? {
        val report = Reports.of(TextInput.read(d.input))
        val r = Reports.compute(report, Panel(PanelKind.DIGITS), digits = d.digits)
        val v = (r as? PanelResult.Digits)?.value ?: return "no digits: $r"
        if (!v.isReal) return "not real: $v"
        return if (WrittenNumbers.digitsAgree(v.re, d.ref)) null else "${v.re}"
    }
}

/** Runs a corpus and reports it: for each area the share right, declined and wrong, the time per task, the slowest ten. */
internal object WrittenCorpusRun {
    /** The wrong answers (the test's message, empty where none) and the number of tasks run. */
    fun run(tasks: List<Task>, title: String, digits: List<WrittenNumbers.DigitsTask>, file: String?,
        runDigits: (WrittenNumbers.DigitsTask) -> String?): Pair<String, Int> {
        val threads = System.getenv("CALC_CORPUS_THREADS")?.toIntOrNull() ?: maxOf(1, Runtime.getRuntime().availableProcessors() / 2)
        val pool = Executors.newFixedThreadPool(threads)
        val ran = tasks.map { t -> pool.submit<Ran> { runTask(t) } }.map { it.get() }
        val checked = digits.map { d -> pool.submit<Pair<WrittenNumbers.DigitsTask, String?>> { d to runDigits(d) } }.map { it.get() }
        pool.shutdown()
        pool.awaitTermination(1, TimeUnit.MINUTES)

        val report = StringBuilder()
        report.appendLine("$title: ${ran.size} tasks" + (if (checked.isNotEmpty()) " and ${checked.size} certified-digit tasks" else "") + ", $threads threads")
        report.appendLine(String.format("%-18s %6s %7s %9s %7s %9s %9s", "area", "tasks", "right", "declined", "WRONG", "median", "p95"))
        val areas = ran.groupBy { it.task.area }
        for ((area, list) in areas) {
            val n = list.size
            fun share(v: Verdict) = list.count { it.verdict == v }.let { "%d (%.1f%%)".format(it, 100.0 * it / n) }
            val ms = list.map { it.nanos / 1e6 }.sorted()
            report.appendLine(String.format("%-18s %6d %7s %9s %7s %8.1fms %8.1fms", area, n, list.count { it.verdict == Verdict.RIGHT },
                share(Verdict.DECLINED), list.count { it.verdict == Verdict.WRONG }, ms[ms.size / 2], ms[(ms.size * 95 / 100).coerceAtMost(ms.size - 1)]))
        }
        val wrong = ran.filter { it.verdict == Verdict.WRONG }
        if (ran.isNotEmpty()) {
            val all = ran.map { it.nanos / 1e6 }.sorted()
            val right = ran.count { it.verdict == Verdict.RIGHT }
            val declined = ran.count { it.verdict == Verdict.DECLINED }
            report.appendLine(String.format("%-18s %6d %7d %9s %7d %8.1fms %8.1fms", "all", ran.size, right,
                "%d (%.1f%%)".format(declined, 100.0 * declined / ran.size), wrong.size, all[all.size / 2], all[(all.size * 95 / 100).coerceAtMost(all.size - 1)]))
        }
        if (checked.isNotEmpty()) report.appendLine("certified digits: ${checked.count { it.second == null }} of ${checked.size} right")
        report.appendLine("slowest:")
        ran.sortedByDescending { it.nanos }.take(10).forEach { report.appendLine("  %8.1f ms  %s → %s".format(it.nanos / 1e6, it.task.input, it.answer)) }
        report.appendLine("wrong:")
        wrong.forEach { report.appendLine("  [${it.task.area}] ${it.task.input} → ${it.answer}   (expected ${it.task.expected})") }
        checked.filter { it.second != null }.forEach { report.appendLine("  [digits] ${it.first.input} → ${it.second}   (expected ${it.first.ref})") }
        report.appendLine("declined:")
        ran.filter { it.verdict == Verdict.DECLINED }.forEach { report.appendLine("  [${it.task.area}] ${it.task.input} → ${it.answer}") }
        println(report)
        file?.let { File(it).writeText(report.toString()) }
        return (wrong.map { "${it.task.input} → ${it.answer}" } + checked.filter { it.second != null }.map { "${it.first.input} → ${it.second}" })
            .take(40).joinToString("\n") to ran.size
    }
}

/** Every area's tasks. */
internal object WrittenCorpus {
    fun all(): List<Task> = WrittenNumbers.arithmetic() + WrittenNumbers.exactValues() + WrittenAlgebra.algebra() + WrittenAlgebra.solving() +
        WrittenAlgebra.inequalities() + WrittenAlgebra.systems() + WrittenCalculus.calculus() + WrittenCalculus.series() + WrittenCalculus.limits() +
        WrittenCalculus.sums() + WrittenLinear.linear() + WrittenDynamics.odes() + WrittenDynamics.transforms() + WrittenDynamics.numerics() +
        WrittenDiscrete.numberTheory() + WrittenDiscrete.combinatorics() + WrittenDiscrete.logic() + WrittenDiscrete.sets() +
        WrittenData.statistics() + WrittenNumbers.units() + WrittenData.geometry() + WrittenEdges.all()

    /** The areas added after the first corpus. */
    fun later(): List<Task> = WrittenLater.all()
}

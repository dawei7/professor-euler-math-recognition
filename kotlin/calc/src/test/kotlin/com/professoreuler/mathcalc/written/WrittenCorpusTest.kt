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
        val tasks = WrittenCorpus.all()
        val threads = System.getenv("CALC_CORPUS_THREADS")?.toIntOrNull() ?: maxOf(1, Runtime.getRuntime().availableProcessors() / 2)
        val pool = Executors.newFixedThreadPool(threads)
        val ran = tasks.map { t -> pool.submit<Ran> { runTask(t) } }.map { it.get() }
        val digits = WrittenNumbers.digits().map { d -> pool.submit<Pair<WrittenNumbers.DigitsTask, String?>> { d to runDigits(d) } }.map { it.get() }
        pool.shutdown()
        pool.awaitTermination(1, TimeUnit.MINUTES)

        val report = StringBuilder()
        report.appendLine("Written corpus: ${ran.size} tasks and ${digits.size} certified-digit tasks, $threads threads")
        report.appendLine(String.format("%-18s %6s %7s %9s %7s %9s %9s", "area", "tasks", "right", "declined", "WRONG", "median", "p95"))
        val areas = ran.groupBy { it.task.area }
        for ((area, list) in areas) {
            val n = list.size
            fun share(v: Verdict) = list.count { it.verdict == v }.let { "%d (%.1f%%)".format(it, 100.0 * it / n) }
            val ms = list.map { it.nanos / 1e6 }.sorted()
            report.appendLine(String.format("%-18s %6d %7s %9s %7s %8.1fms %8.1fms", area, n, list.count { it.verdict == Verdict.RIGHT },
                share(Verdict.DECLINED), list.count { it.verdict == Verdict.WRONG }, ms[ms.size / 2], ms[(ms.size * 95 / 100).coerceAtMost(ms.size - 1)]))
        }
        val all = ran.map { it.nanos / 1e6 }.sorted()
        val right = ran.count { it.verdict == Verdict.RIGHT }
        val declined = ran.count { it.verdict == Verdict.DECLINED }
        val wrong = ran.filter { it.verdict == Verdict.WRONG }
        report.appendLine(String.format("%-18s %6d %7d %9s %7d %8.1fms %8.1fms", "all", ran.size, right,
            "%d (%.1f%%)".format(declined, 100.0 * declined / ran.size), wrong.size, all[all.size / 2], all[all.size * 95 / 100]))
        report.appendLine("certified digits: ${digits.count { it.second == null }} of ${digits.size} right")
        report.appendLine("slowest:")
        ran.sortedByDescending { it.nanos }.take(10).forEach { report.appendLine("  %8.1f ms  %s → %s".format(it.nanos / 1e6, it.task.input, it.answer)) }
        report.appendLine("wrong:")
        wrong.forEach { report.appendLine("  [${it.task.area}] ${it.task.input} → ${it.answer}   (expected ${it.task.expected})") }
        digits.filter { it.second != null }.forEach { report.appendLine("  [digits] ${it.first.input} → ${it.second}   (expected ${it.first.ref})") }
        report.appendLine("declined:")
        ran.filter { it.verdict == Verdict.DECLINED }.forEach { report.appendLine("  [${it.task.area}] ${it.task.input} → ${it.answer}") }
        println(report)
        System.getenv("CALC_CORPUS_REPORT")?.let { File(it).writeText(report.toString()) }

        assertEquals("", (wrong.map { "${it.task.input} → ${it.answer}" } + digits.filter { it.second != null }.map { "${it.first.input} → ${it.second}" })
            .take(40).joinToString("\n"))
        assert(ran.size >= 5000) { "only ${ran.size} tasks" }
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

/** Every area's tasks. */
internal object WrittenCorpus {
    fun all(): List<Task> = WrittenNumbers.arithmetic() + WrittenNumbers.exactValues() + WrittenAlgebra.algebra() + WrittenAlgebra.solving() +
        WrittenAlgebra.inequalities() + WrittenAlgebra.systems() + WrittenCalculus.calculus() + WrittenCalculus.series() + WrittenCalculus.limits() +
        WrittenCalculus.sums() + WrittenLinear.linear() + WrittenDynamics.odes() + WrittenDynamics.transforms() + WrittenDynamics.numerics() +
        WrittenDiscrete.numberTheory() + WrittenDiscrete.combinatorics() + WrittenDiscrete.logic() + WrittenDiscrete.sets() +
        WrittenData.statistics() + WrittenNumbers.units() + WrittenData.geometry() + WrittenEdges.all()
}

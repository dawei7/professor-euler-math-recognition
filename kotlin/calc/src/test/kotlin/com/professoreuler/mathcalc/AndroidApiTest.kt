package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.math.BigInteger

/**
 * The calculator runs on Android from version 10 (API 29), whose Java lacks what Java 9 added to `BigInteger`: `TWO`, `sqrt()`
 * and `sqrtAndRemainder()`. Its sources must not use them (lint in the app finds them only once they are copied there).
 */
class AndroidApiTest {
    @Test
    fun noJava9BigIntegerInTheSources() {
        // Here in the engine's repository, or as copied into the app.
        val folder = listOf("src/main/kotlin/com/professoreuler/mathcalc", "src/main/java/com/professoreuler/mathcalc").map(::File).first { it.isDirectory }
        val sources = folder.listFiles { f -> f.name.endsWith(".kt") }.orEmpty()
        assert(sources.size > 20) { "no sources in $folder" }
        val banned = listOf(Regex("""BigInteger\.TWO\b"""), Regex("""\.sqrtAndRemainder\("""), Regex("""\b[a-z][A-Za-z0-9]*\.sqrt\(\)"""))
        val found = sources.flatMap { f ->
            f.readLines().withIndex().filter { (_, line) -> !line.trimStart().startsWith("/") && !line.trimStart().startsWith("*") && banned.any { it.containsMatchIn(line) } }
                .map { (k, line) -> "${f.name}:${k + 1}: ${line.trim()}" }
        }
        assertEquals("", found.joinToString("\n"))
    }

    @Test
    fun theWholeSquareRootIsExact() {
        for (n in listOf(0L, 1L, 2L, 3L, 4L, 15L, 16L, 17L, 99L, 100L, 1L shl 52, (1L shl 52) + 1, Long.MAX_VALUE)) {
            val b = BigInteger.valueOf(n)
            val r = NumberTheory.isqrt(b)
            assert(r * r <= b && (r + BigInteger.ONE) * (r + BigInteger.ONE) > b) { "isqrt($n) = $r" }
        }
        val big = BigInteger.TEN.pow(60).add(BigInteger.valueOf(12345))
        val r = NumberTheory.isqrt(big)
        assert(r * r <= big && (r + BigInteger.ONE) * (r + BigInteger.ONE) > big)
    }
}

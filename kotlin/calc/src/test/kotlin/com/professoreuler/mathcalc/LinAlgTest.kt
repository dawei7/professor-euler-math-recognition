package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Test

/** Matrices and vectors as LaTeX writes them: arithmetic in the order written, and each result checked. */
class LinAlgTest {
    private val a = "\\begin{pmatrix}1&2\\\\3&4\\end{pmatrix}"
    private val b = "\\begin{pmatrix}0&1\\\\1&0\\end{pmatrix}"
    private val singular = "\\begin{pmatrix}1&2&3\\\\4&5&6\\\\7&8&9\\end{pmatrix}"

    private fun check(vararg cases: Pair<Pair<String, Mode?>, String>) {
        val wrong = cases.mapNotNull { (input, expected) ->
            val (latex, mode) = input
            val got = answerOf(MathCalc.calculate(latex, mode?.let { Choice(it) }))
            if (got != expected) "$latex [$mode]: $got, not $expected" else null
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test fun aMatrixWrittenAsItIs() = check(
        (a to null) to "-2",
        (a to Mode.INVERSE) to "[[-2, 1], [3/2, -1/2]]",
        (a to Mode.TRANSPOSE) to "[[1, 3], [2, 4]]",
        (a to Mode.ROW_REDUCE) to "[[1, 0], [0, 1]] rank 2",
        (singular to null) to "0",
        (singular to Mode.ROW_REDUCE) to "[[1, 0, -1], [0, 1, 2], [0, 0, 0]] rank 2 null (1, -2, 1)",
        (singular to Mode.INVERSE) to "SINGULAR",
        ("\\begin{pmatrix}1&2&3\\\\4&5&6\\end{pmatrix}" to null) to "[[1, 0, -1], [0, 1, 2]] rank 2 null (1, -2, 1)",
        ("\\begin{pmatrix}a&b\\\\c&d\\end{pmatrix}" to null) to "ad - bc",
        ("\\begin{pmatrix}a&b\\\\c&d\\end{pmatrix}" to Mode.INVERSE) to "[[d/(ad - bc), -b/(ad - bc)], [-c/(ad - bc), a/(ad - bc)]]",
        ("\\begin{vmatrix}1&2\\\\3&4\\end{vmatrix}" to null) to "-2",
        ("\\det$a" to null) to "-2",
    )

    /** Products keep their order (A·B is not B·A); powers, the inverse and the transpose come out as matrices. */
    @Test fun arithmeticInTheOrderWritten() = check(
        ("$a\\cdot$b" to null) to "[[2, 1], [4, 3]]",
        ("$b\\cdot$a" to null) to "[[3, 4], [1, 2]]",
        ("$a^{2}" to null) to "[[7, 10], [15, 22]]",
        ("$a^{-1}" to null) to "[[-2, 1], [3/2, -1/2]]",
        ("$a^{T}" to null) to "[[1, 3], [2, 4]]",
        ("2$a" to null) to "[[2, 4], [6, 8]]",
        ("$a+$b" to null) to "[[1, 3], [4, 4]]",
        ("$a\\begin{pmatrix}1\\\\1\\end{pmatrix}" to null) to "(3, 7)",
        ("$a\\cdot\\begin{pmatrix}1&2&3\\end{pmatrix}" to null) to "SIZES",
    )

    /** Eigenvalues with their multiplicities and eigenvectors, real, repeated, irrational and complex; each A·v = λ·v. */
    @Test fun eigenvalues() = check(
        ("\\begin{pmatrix}2&1\\\\1&2\\end{pmatrix}" to Mode.EIGEN) to "1: (-1, 1); 3: (1, 1)",
        ("\\begin{pmatrix}2&0&0\\\\0&3&4\\\\0&4&9\\end{pmatrix}" to Mode.EIGEN) to "1: (0, -2, 1); 2: (1, 0, 0); 11: (0, 1, 2)",
        ("\\begin{pmatrix}1&1\\\\0&1\\end{pmatrix}" to Mode.EIGEN) to "1 (2×): (1, 0)",
        ("\\begin{pmatrix}0&-1\\\\1&0\\end{pmatrix}" to Mode.EIGEN) to "-i: (-i, 1); i: (i, 1)",
        ("\\begin{pmatrix}1&1\\\\1&0\\end{pmatrix}" to Mode.EIGEN) to "1/2 - √5/2: (1/2 - √5/2, 1); 1/2 + √5/2: (1/2 + √5/2, 1)",
    )

    /** A diagonalizable matrix comes with P and D; a Jordan block with none. */
    @Test fun diagonalization() {
        val o = MathCalc.calculate("\\begin{pmatrix}2&1\\\\1&2\\end{pmatrix}", Choice(Mode.EIGEN)).outcome as Outcome.Eigenvalues
        val (p, d) = o.diagonal!!
        assertEquals("[[-1, 1], [1, 1]]", p.toString())
        assertEquals("[[1, 0], [0, 3]]", d.toString())
        val jordan = MathCalc.calculate("\\begin{pmatrix}1&1\\\\0&1\\end{pmatrix}", Choice(Mode.EIGEN)).outcome as Outcome.Eigenvalues
        assertEquals(null, jordan.diagonal)
    }

    /** Matrices typed as [[1, 2], [3, 4]] or {{1, 2}, {3, 4}}, and the words for what to do with them in several languages. */
    @Test fun typedMatrices() {
        val cases = listOf(
            ("det [[1,2],[3,4]]" to "en") to "-2",
            ("eigenvalues of [[2,1],[1,2]]" to "en") to "1: (-1, 1); 3: (1, 1)",
            ("Eigenwerte von [[2,1],[1,2]]" to "de") to "1: (-1, 1); 3: (1, 1)",
            ("valeurs propres de [[2,1],[1,2]]" to "fr") to "1: (-1, 1); 3: (1, 1)",
            ("inverse of [[1,2],[3,4]]" to "en") to "[[-2, 1], [3/2, -1/2]]",
            ("[[1,2],[3,4]]の逆行列" to "ja") to "[[-2, 1], [3/2, -1/2]]",
            ("[[1,2],[3,4]]^2" to "en") to "[[7, 10], [15, 22]]",
            ("{{1,2},{3,4}}" to "en") to "-2",
            ("rref [[1,2,3],[4,5,6]]" to "en") to "[[1, 0, -1], [0, 1, 2]] rank 2 null (1, -2, 1)",
            ("行列式 [[1,2],[3,4]]" to "zh-Hans") to "-2",
            ("найдите определитель [[1,2],[3,4]]" to "ru") to "-2",
            ("[[1, 2], [3, 4]] * [[0, 1], [1, 0]]" to "en") to "[[2, 1], [4, 3]]",
            ("transpose [[1,2,3],[4,5,6]]" to "en") to "[[1, 4], [2, 5], [3, 6]]",
            ("[1,2,3]*[4,5,6]" to "en") to "32",
        )
        val wrong = cases.mapNotNull { (input, expected) ->
            val got = answerOf(Reports.of(TextInput.read(input.first, input.second)).result)
            if (got != expected) "${input.first}: $got, not $expected" else null
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    /** Vectors: (1, 2, 3) with two commas, [3, 4] with one; × the cross product, · the dot product, |v| the length. */
    @Test fun vectors() = check(
        ("(1,2,3)\\times(4,5,6)" to null) to "(-3, 6, -3)",
        ("(1,2,3)\\cdot(4,5,6)" to null) to "32",
        ("|[3,4]|" to null) to "5",
        ("[1,2]+[3,4]" to null) to "(4, 6)",
        // One comma between digits in round brackets is still a decimal comma.
        ("(1,5)\\cdot2" to null) to "3",
    )
}

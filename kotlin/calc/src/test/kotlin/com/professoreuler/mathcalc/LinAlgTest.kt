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
        // Vectors of one length in brackets are a matrix's rows, as the keys write it.
        ("\\left[\\left[1,2\\right],\\left[3,4\\right]\\right]" to null) to "-2",
        ("[[1,2],[3,4,5]]" to null) to "SIZES",
    )

    /** A linear system with a parameter, case by case: one solution for most values, none or a family where its determinant is 0. */
    @Test fun systemsCaseByCase() = check(
        ("\\begin{cases}ax+y=1\\\\x+ay=1\\end{cases}" to null) to
            "otherwise: x = 1/(a + 1), y = 1/(a + 1) | a = -1: none | a = 1: x = -t + 1, y = t",
        ("\\begin{cases}x+y+z=1\\\\x+ay+z=1\\\\x+y+az=1\\end{cases}" to null) to
            "otherwise: x = 1, y = 0, z = 0 | a = 1: x = -s - t + 1, y = t, z = s",
        ("\\begin{cases}x+2y=3\\\\2x+ky=6\\end{cases}" to null) to "otherwise: x = 3, y = 0 | k = 4: x = -2t + 3, y = t",
        ("\\begin{cases}x+2y=3\\\\2x+ky=5\\end{cases}" to null) to "otherwise: x = (-10 + 3k)/(-4 + k), y = -1/(-4 + k) | k = 4: none",
        // Without a parameter it is solved as before.
        ("\\begin{cases}x+y=3\\\\x-y=1\\end{cases}" to null) to "x = 2, y = 1",
    )

    /** P·A = L·U, A = Q·R and A = L·Lᵀ, each checked by multiplying back; Cholesky only for a symmetric positive definite matrix. */
    @Test fun decompositions() = check(
        ("\\begin{pmatrix}4&3\\\\6&3\\end{pmatrix}" to Mode.DECOMPOSE) to
            "P=[[1, 0], [0, 1]] L=[[1, 0], [3/2, 1]] U=[[4, 3], [0, -3/2]] | Q=[[2√13/13, 3√13/13], [3√13/13, -2√13/13]] R=[[2√13, 15√13/13], [0, 3√13/13]]",
        ("\\begin{pmatrix}0&1\\\\1&1\\end{pmatrix}" to Mode.DECOMPOSE) to "P=[[0, 1], [1, 0]] L=[[1, 0], [0, 1]] U=[[1, 1], [0, 1]] | Q=[[0, 1], [1, 0]] R=[[1, 1], [0, 1]]",
        ("\\begin{pmatrix}4&2\\\\2&3\\end{pmatrix}" to Mode.DECOMPOSE) to
            "P=[[1, 0], [0, 1]] L=[[1, 0], [1/2, 1]] U=[[4, 2], [0, 2]] | Q=[[2√5/5, -√5/5], [√5/5, 2√5/5]] R=[[2√5, 7√5/5], [0, 4√5/5]] | C=[[2, 0], [1, √2]]",
        ("\\begin{pmatrix}1&1\\\\1&0\\\\0&1\\end{pmatrix}" to Mode.DECOMPOSE) to "Q=[[√2/2, √6/6], [√2/2, -√6/6], [0, √6/3]] R=[[√2, √2/2], [0, √6/2]]",
    )

    /** e^A, real where the eigenvalues are complex, checked against the series. */
    @Test fun matrixExponential() = check(
        ("\\begin{pmatrix}0&1\\\\-1&0\\end{pmatrix}" to Mode.MATRIX_EXP) to "[[cos(1), sin(1)], [-sin(1), cos(1)]]",
        ("\\begin{pmatrix}2&1\\\\1&2\\end{pmatrix}" to Mode.MATRIX_EXP) to "[[e/2 + e^3/2, e^3/2 - e/2], [e^3/2 - e/2, e/2 + e^3/2]]",
        ("\\begin{pmatrix}1&1\\\\0&1\\end{pmatrix}" to Mode.MATRIX_EXP) to "[[e, e], [0, e]]",
        ("e^{\\begin{pmatrix}0&0\\\\0&1\\end{pmatrix}}" to null) to "[[1, 0], [0, e]]",
    )

    /** Gradient, divergence, curl, Jacobian and Laplacian of fields in x, y and z. */
    @Test fun vectorCalculus() = check(
        ("(x^{2}y,yz,xz)" to Mode.DIVERGENCE) to "2xy + x + z",
        ("(x^{2}y,yz,xz)" to Mode.CURL) to "(-y, -z, -x^2)",
        ("(x^{2}y,yz,xz)" to Mode.JACOBIAN) to "[[2xy, x^2, 0], [0, z, y], [z, 0, x]]",
        ("(-y,x)" to Mode.CURL) to "2",
        ("x^{2}y+y^{3}" to Mode.GRADIENT) to "(2xy, x^2 + 3y^2)",
        ("x^{2}+y^{2}+z^{2}" to Mode.LAPLACIAN) to "6",
    )

    /** Extrema under constraints by Lagrange's multipliers, and least squares for a system with no solution. */
    @Test fun lagrangeAndLeastSquares() = check(
        ("\\begin{cases}xy\\\\x+y=10\\end{cases}" to null) to "(5, 5) 25",
        ("\\begin{cases}x+y\\\\x^{2}+y^{2}=1\\end{cases}" to null) to "(-√2/2, -√2/2) -√2 ≈ -1.414213562; (√2/2, √2/2) √2 ≈ 1.414213562",
        ("\\begin{cases}x^{2}+y^{2}+z^{2}\\\\x+y+z=1\\end{cases}" to null) to "(1/3, 1/3, 1/3) 1/3 ≈ 0.3333333333",
        ("\\begin{cases}x+y=1\\\\x-y=1\\\\x+2y=3\\end{cases}" to null) to "x = 9/7, y = 4/7 residual 2√14/7 ≈ 1.069044968",
    )

    /** Typed: a system with ";", "subject to", and the words for the new modes. */
    @Test fun typedFields() {
        val cases = listOf(
            ("maximize x*y subject to x+y=10" to "en") to "(5, 5) 25",
            ("x*y; x+y=10" to "en") to "(5, 5) 25",
            ("Extrema von x*y unter der Nebenbedingung x+y=10" to "de") to "(5, 5) 25",
            ("divergence of (x^2 y, y z, x z)" to "en") to "2xy + x + z",
            ("Rotation von (x^2 y, y z, x z)" to "de") to "(-y, -z, -x^2)",
            ("gradient of x^2 y + y^3" to "en") to "(2xy, x^2 + 3y^2)",
            ("x+y=1; x-y=1; x+2y=3" to "en") to "x = 9/7, y = 4/7 residual 2√14/7 ≈ 1.069044968",
            ("lu decomposition of [[4,3],[6,3]]" to "en") to
                "P=[[1, 0], [0, 1]] L=[[1, 0], [3/2, 1]] U=[[4, 3], [0, -3/2]] | Q=[[2√13/13, 3√13/13], [3√13/13, -2√13/13]] R=[[2√13, 15√13/13], [0, 3√13/13]]",
        )
        val wrong = cases.mapNotNull { (input, expected) ->
            val got = answerOf(Reports.of(TextInput.read(input.first, input.second)).result)
            if (got != expected) "${input.first}: $got, not $expected" else null
        }
        assertEquals("", wrong.joinToString("\n"))
    }
}

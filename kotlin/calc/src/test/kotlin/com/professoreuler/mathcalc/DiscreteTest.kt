package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Milestone 7: number theory and combinatorics, congruences, whole-number equations, recurrences and sequences, logic and sets,
 * statistics, units and geometry; and the same typed as their functions.
 */
class DiscreteTest {
    private fun check(vararg cases: Pair<Pair<String, Choice?>, String>) {
        val wrong = cases.mapNotNull { (input, expected) ->
            val (latex, choice) = input
            val got = answerOf(MathCalc.calculate(latex, choice))
            if (got != expected) "$latex [${choice?.mode}]: $got, not $expected" else null
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    /** Number functions worked out where they are written, statistics of a list, distributions, and a whole number's facts. */
    @Test fun numberFunctions() = check(
        ("\\gcd(12,18)" to null) to "6",
        ("lcm(4,6,10)" to null) to "60",
        ("gcd(84,36)" to null) to "12",
        ("binom(10,3)" to null) to "120",
        ("\\binom{52}{5}" to null) to "2598960",
        ("catalan(10)" to null) to "16796",
        ("bell(10)" to null) to "115975",
        ("stirling(10,3)" to null) to "9330",
        ("stirling1(5,2)" to null) to "50",
        ("partitions(100)" to null) to "190569292",
        ("\\varphi(36)" to null) to "12",
        ("sigma(28)" to null) to "56",
        ("mu(30)" to null) to "-1",
        ("prime(1000)" to null) to "7919",
        ("nextprime(100)" to null) to "101",
        ("jacobi(2,15)" to null) to "1",
        ("mod(-7,3)" to null) to "2",
        ("17 \\bmod 5" to null) to "2",
        ("2^{100}\\bmod 7" to null) to "2",
        ("powmod(3,1000,7)" to null) to "4",
        ("modinv(3,7)" to null) to "5",
        ("npr(5,2)" to null) to "20",
        ("\\mu(x+1)" to null) to "\\mux + \\mu",
        ("mean(1,2,3,4)" to null) to "2.5",
        ("median(3,1,2)" to null) to "2",
        ("sd(2,4,4,4,5,5,7,9)" to null) to "4√14/7 ≈ 2.138089935",
        ("binompdf(10, 1/2, 3)" to null) to "0.1171875",
        ("normalcdf(-1,1)" to null) to "≈ 0.6826894921",
        ("invnorm(0.975)" to null) to "≈ 1.959963985",
        ("modinv(2,4)" to null) to "UNDEFINED",
        ("360" to Choice(Mode.NUMBER_FACTS)) to "2^3 · 3^2 · 5 d=24 σ=1170 φ=96 μ=0 next 367 bin 101101000",
        ("97" to Choice(Mode.NUMBER_FACTS)) to "prime d=2 σ=98 φ=96 μ=-1 next 101 bin 1100001",
        ("28" to Choice(Mode.NUMBER_FACTS)) to "2^2 · 7 d=6 σ=56 φ=12 μ=0 next 29 bin 11100",
    )

    /** Congruences and their systems (each solution put back), and equations solved in whole numbers. */
    @Test fun congruencesAndWholeNumbers() = check(
        ("3x\\equiv 2\\pmod{7}" to null) to "x ≡ 3 (mod 7)",
        ("x^{2}\\equiv 1\\pmod{8}" to null) to "x ≡ 1, 3, 5, 7 (mod 8)",
        ("\\begin{cases}x\\equiv 2\\pmod{3}\\\\x\\equiv 3\\pmod{5}\\\\x\\equiv 2\\pmod{7}\\end{cases}" to null) to "x ≡ 23 (mod 105)",
        ("2x\\equiv 1\\pmod{4}" to null) to "none",
        ("17\\equiv 2\\pmod{5}" to null) to "true",
        ("x \\bmod 3 = 2" to null) to "x ≡ 2 (mod 3)",
        ("3x+5y=7" to Choice(Mode.DIOPHANTINE)) to "x = 4 + 5t, y = -1 + -3t",
        ("6x+9y=4" to Choice(Mode.DIOPHANTINE)) to "none",
        ("x^{2}-2y^{2}=1" to Choice(Mode.DIOPHANTINE)) to "x = 3, y = 2 (d = 2)",
        ("x^{2}-61y^{2}=1" to Choice(Mode.DIOPHANTINE)) to "x = 1766319049, y = 226153980 (d = 61)",
    )

    /** Linear recurrences in closed form (their terms recomputed), and the rules of lists of numbers. */
    @Test fun recurrencesAndSequences() = check(
        ("\\begin{cases}a_{n+1}=2a_{n}+1\\\\a_{0}=1\\end{cases}" to null) to "a_n = 2·2^n - 1 | 1, 3, 7, 15, 31, 63, 127, 255",
        ("\\begin{cases}a_{n+2}=a_{n+1}+a_{n}\\\\a_{0}=0\\\\a_{1}=1\\end{cases}" to null) to "a_n = (1/5)√5(1/2 + √5/2)^n - (1/5)√5(1/2 - √5/2)^n | 0, 1, 1, 2, 3, 5, 8, 13",
        ("a_{n+2}=5a_{n+1}-6a_{n}" to null) to "a_n = C_1·2^n + C_2·3^n",
        ("\\begin{cases}a_{n}=3a_{n-1}+2^{n}\\\\a_{0}=1\\end{cases}" to null) to "a_n = 3·3^n - 2·2^n | 1, 5, 19, 65, 211, 665, 2059, 6305",
        ("\\begin{cases}a_{n+2}=-a_{n}\\\\a_{0}=1\\\\a_{1}=0\\end{cases}" to null) to "a_n = cos((1/2)πn) | 1, 0, -1, 0, 1, 0, -1, 0",
        ("\\begin{cases}a_{n+2}=4a_{n+1}-4a_{n}\\\\a_{0}=1\\\\a_{1}=4\\end{cases}" to null) to "a_n = n·2^n + 2^n | 1, 4, 12, 32, 80, 192, 448, 1024",
        ("\\begin{cases}a_{n+1}=a_{n}+n\\\\a_{1}=1\\end{cases}" to null) to "a_n = (1/2)n^2 - (1/2)n + 1 | 1, 2, 4, 7, 11, 16, 22, 29",
        ("(1,4,9,16,25)" to Choice(Mode.SEQUENCE)) to "a_n = n^2 next 36, 49, 64",
        ("(2,6,18,54)" to Choice(Mode.SEQUENCE)) to "a_n = (2/3)·3^n rec 3 next 162, 486, 1458",
        ("(1,1,2,3,5,8,13)" to Choice(Mode.SEQUENCE)) to "a_n = (1/5)√5(1/2 + √5/2)^n - (1/5)√5(1/2 - √5/2)^n rec 1, 1 next 21, 34, 55",
        ("(1,3,6,10,15)" to Choice(Mode.SEQUENCE)) to "a_n = (1/2)n^2 + (1/2)n next 21, 28, 36",
    )

    /** Truth tables, the simplest forms (checked against the table), equivalences, and sets written out. */
    @Test fun logicAndSets() = check(
        ("p\\land q\\to r" to null) to "11111101 dnf ¬p ∨ ¬q ∨ r cnf ¬p ∨ ¬q ∨ r",
        ("\\neg(p\\land q)=\\neg p\\lor\\neg q" to null) to "1110 dnf ¬p ∨ ¬q cnf ¬p ∨ ¬q equivalent true",
        ("p\\lor\\neg p" to null) to "tautology 11 dnf 1 cnf 1",
        ("p\\land\\neg p" to null) to "contradiction 00 dnf 0 cnf 0",
        ("(p\\to q)\\land(q\\to p)" to null) to "1001 dnf (¬p ∧ ¬q) ∨ (p ∧ q) cnf (p ∨ ¬q) ∧ (¬p ∨ q)",
        ("A\\cup(B\\cap C)" to null) to "00011111 dnf A ∪ (B ∩ C) cnf (A ∪ B) ∩ (A ∪ C)",
        ("\\overline{A\\cup B}=\\overline{A}\\cap\\overline{B}" to null) to "1000 dnf A' ∩ B' cnf A' ∩ B' equivalent true",
        ("A\\cup B=A\\cap B" to null) to "0111 dnf A ∪ B cnf A ∪ B equivalent false",
        ("\\{1,2,3\\}\\cup\\{3,4\\}" to null) to "{1, 2, 3, 4}",
        ("\\{1,2,3,4\\}\\setminus\\{2,4\\}" to null) to "{1, 3}",
        ("\\{1,2,3\\}\\cap\\{2,3,5\\}" to null) to "{2, 3}",
        ("p \\to q" to null) to "1101 dnf ¬p ∨ q cnf ¬p ∨ q",
        ("a\\oplus b\\oplus c" to null) to "01101001 dnf (¬a ∧ ¬b ∧ c) ∨ (¬a ∧ b ∧ ¬c) ∨ (a ∧ ¬b ∧ ¬c) ∨ (a ∧ b ∧ c) cnf (a ∨ b ∨ c) ∧ (a ∨ ¬b ∨ ¬c) ∧ (¬a ∨ b ∨ ¬c) ∧ (¬a ∨ ¬b ∨ c)",
    )

    /** A data list's statistics, the regression line, and values in other units (exact where the units are). */
    @Test fun statisticsAndUnits() = check(
        ("(2,4,4,4,5,5,7,9)" to null) to "n=8 mean 5 median 9/2 sd 4√14/7 q1 4 q3 6",
        ("(1,2,3,4)" to Choice(Mode.STATISTICS)) to "n=4 mean 5/2 median 5/2 sd √15/3 q1 3/2 q3 7/2",
        ("\\begin{pmatrix}1&2\\\\2&4\\\\3&5\\\\4&4\\\\5&5\\end{pmatrix}" to null) to "y = 3/5x + 11/5 r √15/5",
        ("5" to Choice(Mode.UNITS, "km", "mi")) to "78125/25146 mi",
        ("100" to Choice(Mode.UNITS, "km/h", "m/s")) to "250/9 m/s",
        ("20" to Choice(Mode.UNITS, "°C", "°F")) to "68 °F",
        ("90" to Choice(Mode.UNITS, "°", "rad")) to "π/2 rad",
        ("1" to Choice(Mode.UNITS, "kWh", "J")) to "3600000 J",
        ("3" to Choice(Mode.UNITS, "m", "s")) to "UNITS",
        ("1" to Choice(Mode.UNITS, "GiB", "MB")) to "16777216/15625 MB",
        ("300" to Choice(Mode.UNITS, "K", "°C")) to "537/20 °C",
        ("1" to Choice(Mode.UNITS, "atm", "psi")) to "18677382000000/1270920461503 psi",
    )

    /** Triangles (the ambiguous case too), conics, the line through two points and the plane through three. */
    @Test fun geometry() = check(
        ("\\begin{cases}a=3\\\\b=4\\\\c=5\\end{cases}" to null) to "sides 3, 4, 5 angles 36.86989765, 53.13010235, 90 area 6",
        ("\\begin{cases}a=5\\\\b=7\\\\\\gamma=60\\end{cases}" to null) to "sides 5, 7, √39 angles 43.89788625, 76.10211375, 60 area 15.15544457",
        ("\\begin{cases}a=10\\\\\\alpha=30\\\\\\beta=45\\end{cases}" to null) to "sides 10, 10√2, 5√2 + 5√6 angles 30, 45, 105 area 25 + 25√3",
        ("\\begin{cases}a=6\\\\b=8\\\\\\alpha=30\\end{cases}" to null) to "sides 6, 8, 11.40033919 angles 30, 41.8103149, 108.1896851 area 22.80067837 | sides 6, 8, 2.456067275 angles 30, 138.1896851, 11.8103149 area 4.912134551",
        ("\\begin{cases}a=1\\\\b=2\\\\c=4\\end{cases}" to null) to "NO_TRIANGLE",
        ("\\begin{cases}a=2\\\\b=3\\\\\\gamma=90\\end{cases}" to null) to "sides 2, 3, √13 angles 33.69006753, 56.30993247, 90 area 3",
        ("\\begin{cases}a=8\\\\b=6\\\\\\alpha=30\\end{cases}" to null) to "sides 8, 6, 12.61235091 angles 30, 22.02431284, 127.9756872 area 18.91852636",
        ("\\begin{cases}a=2\\\\b=8\\\\\\alpha=30\\end{cases}" to null) to "NO_TRIANGLE",
        ("\\frac{x^{2}}{9}+\\frac{y^{2}}{4}=1" to null) to "ellipse centre (0, 0) axes 3, 2 foci (-√5, 0) (√5, 0) e √5/3",
        ("x^{2}+y^{2}-4x+6y=12" to null) to "circle centre (2, -3) axes 5, 5 e 0",
        ("\\frac{x^{2}}{16}-\\frac{y^{2}}{9}=1" to null) to "hyperbola centre (0, 0) axes 4, 3 foci (-5, 0) (5, 0) e 5/4 asymptotes (3/4)x -(3/4)x",
        ("y^{2}=8x" to Choice(Mode.CONIC)) to "parabola foci (2, 0) e 1 vertex (0, 0) directrix x = -2",
        ("y=x^{2}-4x+3" to Choice(Mode.CONIC)) to "parabola foci (2, -3/4) e 1 vertex (2, -1) directrix y = -5/4",
        ("xy=1" to null) to "hyperbola centre (0, 0) axes √2, √2 e √2 angle π/4",
        ("x^{2}+xy+y^{2}=3" to null) to "ellipse centre (0, 0) axes √6, √2 e √6/3 angle π/4",
        ("\\frac{y^{2}}{4}-x^{2}=1" to null) to "hyperbola centre (0, 0) axes 1, 2 foci (0, -√5) (0, √5) e √5/2 asymptotes 2x -2x",
        ("x^{2}+y^{2}=-1" to null) to "UNSOLVED",
        ("\\begin{cases}(1,0,0)\\\\(0,1,0)\\\\(0,0,1)\\end{cases}" to null) to "x + y + z - 1 = 0",
        ("\\begin{cases}(1,2,3)\\\\(4,5,6)\\end{cases}" to null) to "direction (3, 3, 3) distance 3√3",
        ("\\begin{cases}[1,2]\\\\[3,6]\\end{cases}" to null) to "y = 2x distance 2√5",
        ("\\begin{cases}[1,1]\\\\[1,5]\\end{cases}" to null) to "x = 1 distance 4",
        ("\\begin{cases}(1,2,3)\\\\(2,4,6)\\\\(3,6,9)\\end{cases}" to null) to "UNSOLVED",
    )

    /** Typed: units converted, number functions, congruences, data lists, points, a triangle's values, each as its function. */
    @Test fun typed() {
        val cases = listOf(
            "convert(5 km, mi)" to "78125/25146 mi",
            "convert(100 km/h, m/s)" to "250/9 m/s",
            "convert(20 °C, °F)" to "68 °F",
            "convert(20°C, °F)" to "68 °F",
            "convert(3 m, s)" to "UNITS",
            "convert(1.5 km, m)" to "1500 m",
            "convert(1/2 mi, m)" to "100584/125 m",
            "convert(5 in, cm)" to "127/10 cm",
            "convert(60 mph, km/h)" to "301752/3125 km/h",
            "gcd(12, 18)" to "6",
            "17 mod 5" to "2",
            "3x ≡ 2 mod 7" to "x ≡ 3 (mod 7)",
            "x ≡ 2 mod 3; x ≡ 3 mod 5; x ≡ 2 mod 7" to "x ≡ 23 (mod 105)",
            "facts(97)" to "prime d=2 σ=98 φ=96 μ=-1 next 101 bin 1100001",
            "facts(360)" to "2^3 · 3^2 · 5 d=24 σ=1170 φ=96 μ=0 next 367 bin 101101000",
            "stats(1, 2, 3, 4)" to "n=4 mean 5/2 median 5/2 sd √15/3 q1 3/2 q3 7/2",
            "stats(1.5, 2.5, 3)" to "n=3 mean 7/3 median 5/2 sd √21/6 q1 3/2 q3 3",
            "stats(2, 4, 4, 4, 5, 5, 7, 9)" to "n=8 mean 5 median 9/2 sd 4√14/7 q1 4 q3 6",
            "regression((1,2), (2,4), (3,5), (4,4), (5,5))" to "y = 3/5x + 11/5 r √15/5",
            "sequence(1, 4, 9, 16, 25)" to "a_n = n^2 next 36, 49, 64",
            "sequence(2, 6, 18, 54)" to "a_n = (2/3)·3^n rec 3 next 162, 486, 1458",
            "truthtable(p ∧ q → r)" to "11111101 dnf ¬p ∨ ¬q ∨ r cnf ¬p ∨ ¬q ∨ r",
            "p ∧ q → r" to "11111101 dnf ¬p ∨ ¬q ∨ r cnf ¬p ∨ ¬q ∨ r",
            "¬(p ∧ q) = ¬p ∨ ¬q" to "1110 dnf ¬p ∨ ¬q cnf ¬p ∨ ¬q equivalent true",
            "{1, 2, 3} ∪ {3, 4}" to "{1, 2, 3, 4}",
            "triangle(a=3, b=4, c=5)" to "sides 3, 4, 5 angles 36.86989765, 53.13010235, 90 area 6",
            "triangle(a=5, b=7, gamma=60°)" to "sides 5, 7, √39 angles 43.89788625, 76.10211375, 60 area 15.15544457",
            "conic(x^2/9 + y^2/4 = 1)" to "ellipse centre (0, 0) axes 3, 2 foci (-√5, 0) (√5, 0) e √5/3",
            "conic(y^2 = 8x)" to "parabola foci (2, 0) e 1 vertex (0, 0) directrix x = -2",
            "line((1,2), (3,6))" to "y = 2x distance 2√5",
            "plane((1,0,0), (0,1,0), (0,0,1))" to "x + y + z - 1 = 0",
            "diophantine(3x + 5y = 7)" to "x = 4 + 5t, y = -1 + -3t",
            "a_{n+1} = 2a_n + 1; a_0 = 1" to "a_n = 2·2^n - 1 | 1, 3, 7, 15, 31, 63, 127, 255",
            "binompdf(10, 0.5, 3)" to "0.1171875",
            "stats(1, 2, 3)" to "n=3 mean 2 median 2 sd 1 q1 1 q3 3",
            "truthtable(p ∧ q)" to "0001 dnf p ∧ q cnf p ∧ q",
        )
        val wrong = cases.mapNotNull { (input, expected) ->
            val got = answerOf(Reports.of(TextInput.read(input)).result)
            if (got != expected) "$input: $got, not $expected" else null
        }
        assertEquals("", wrong.joinToString("\n"))
    }

}

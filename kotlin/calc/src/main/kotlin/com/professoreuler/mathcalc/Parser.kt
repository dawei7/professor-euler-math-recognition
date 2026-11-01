package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.math.E
import kotlin.math.PI

/**
 * Reads the recognizer's LaTeX tokens into [Node]s. Times binds tighter than plus, an implicit product (`2x`, `3(x-2)`)
 * as tight as `\cdot`, powers tighter still; `!` and `\%` apply to what stands before them. Function names come as
 * letters (`s i n`) or as commands (`\sin`); a function takes a bracket, or else the factor after it (`log_{10}1000`).
 */
internal class Parser(private val t: List<String>) {
    private var i = 0
    private var absDepth = 0

    /** Whether the formula wrote a decimal with a comma, any decimal, any `\frac`. */
    var decimalComma = false
    var decimals = false
    var fractions = false

    /** The sides between `=` signs; null where a side is empty, as after a closing `=`. */
    fun formula(): List<Node?> {
        val sides = mutableListOf<Node?>()
        while (true) {
            sides.add(if (i == t.size || t[i] == "=") null else expression())
            if (i == t.size) return sides
            expect("=")
        }
    }

    private fun peek(k: Int = 0) = t.getOrNull(i + k)

    private fun expect(s: String) {
        if (peek() != s) throw unreadable()
        i++
    }

    private fun unreadable() = CalcException(Failure.UNREADABLE)

    private fun isDigit(s: String?) = s != null && s.length == 1 && s[0] in '0'..'9'
    private fun isLetter(s: String?) = s != null && s.length == 1 && (s[0] in 'a'..'z' || s[0] in 'A'..'Z')

    private fun expression(): Node {
        var left = when (peek()) {
            "-" -> { i++; Neg(term()) }
            "+" -> { i++; term() }
            else -> term()
        }
        while (true) {
            left = when (peek()) {
                "+" -> { i++; Add(left, term()) }
                "-" -> { i++; Sub(left, term()) }
                else -> return left
            }
        }
    }

    private fun term(): Node {
        var left = factor()
        while (true) {
            val op = peek() ?: return left
            left = when {
                op in TIMES -> { i++; Mul(left, factor()) }
                op in DIVIDE -> { i++; Div(left, factor()) }
                startsFactor(op) -> Mul(left, factor())
                else -> return left
            }
        }
    }

    private fun startsFactor(op: String) = isDigit(op) || isLetter(op) || op == "(" || op == "[" || op == "\\frac" ||
        op == "\\sqrt" || op == "\\pi" || op in FUNCTION_COMMANDS || (op == "|" && absDepth == 0)

    private fun factor(): Node {
        if (peek() == "-") {
            i++
            return Neg(factor())
        }
        var node = power()
        while (true) {
            node = when (peek()) {
                "!" -> { i++; Factorial(node) }
                "\\%" -> { i++; Percent(node) }
                else -> return node
            }
        }
    }

    private fun power(): Node {
        val base = atom()
        if (peek() != "^") return base
        i++
        if (peek() == "\\circ") {
            i++
            return Degrees(base)
        }
        if (peek() == "{" && peek(1) == "\\circ" && peek(2) == "}") {
            i += 3
            return Degrees(base)
        }
        return Pow(base, group())
    }

    /** A braced expression, or the single digit, letter or constant LaTeX takes without braces (`x^2`). */
    private fun group(): Node {
        val tok = peek() ?: throw unreadable()
        return when {
            tok == "{" -> braced()
            tok == "-" -> { i++; Neg(group()) }
            isDigit(tok) -> { i++; Literal(Rational.of(tok.toLong())) }
            isLetter(tok) -> { i++; letter(tok) }
            tok == "\\pi" -> { i++; Constant(PI) }
            else -> throw unreadable()
        }
    }

    private fun braced(): Node {
        expect("{")
        val e = expression()
        expect("}")
        return e
    }

    private fun bracket(open: String, close: String): Node {
        expect(open)
        val e = expression()
        expect(close)
        return e
    }

    private fun atom(): Node {
        val tok = peek() ?: throw unreadable()
        return when {
            isDigit(tok) -> number()
            (tok == "." || tok == ",") && isDigit(peek(1)) -> number()
            tok == "(" -> bracket("(", ")")
            tok == "[" -> bracket("[", "]")
            tok == "|" -> {
                i++
                absDepth++
                val e = expression()
                expect("|")
                absDepth--
                Abs(e)
            }
            tok == "\\frac" -> {
                i++
                fractions = true
                val n = braced()
                Div(n, braced())
            }
            tok == "\\sqrt" -> {
                i++
                val index = if (peek() == "[") bracket("[", "]") else Literal(Rational.of(2))
                Root(group(), index)
            }
            tok == "\\pi" -> { i++; Constant(PI) }
            tok in FUNCTION_COMMANDS -> { i++; call(tok.drop(1)) }
            isLetter(tok) -> letters()
            else -> throw unreadable()
        }
    }

    private fun number(): Node {
        val whole = StringBuilder()
        while (isDigit(peek())) whole.append(t[i++])
        val separator = peek()
        if ((separator == "." || separator == ",") && isDigit(peek(1))) {
            i++
            val fraction = StringBuilder()
            while (isDigit(peek())) fraction.append(t[i++])
            decimals = true
            if (separator == ",") decimalComma = true
            return Literal(Rational.decimal(whole.ifEmpty { "0" }.toString(), fraction.toString()))
        }
        return Literal(Rational.of(BigInteger(whole.toString())))
    }

    /** A function written in letters, or else one letter: e, or an unknown (with its subscript, as `x_{1}`). */
    private fun letters(): Node {
        val run = StringBuilder()
        while (run.length < 6 && isLetter(peek(run.length))) run.append(peek(run.length))
        val name = FUNCTIONS.firstOrNull { run.startsWith(it) }
        if (name != null) {
            i += name.length
            return call(name)
        }
        return letter(t[i++])
    }

    private fun letter(tok: String): Node {
        if (tok == "e") return Constant(E)
        if (peek() != "_") return Unknown(tok)
        i++
        val sub = StringBuilder()
        if (peek() == "{") {
            var depth = 0
            do {
                val s = peek() ?: throw unreadable()
                if (s == "{") depth++
                if (s == "}") depth--
                sub.append(s)
                i++
            } while (depth > 0)
        } else {
            sub.append(peek() ?: throw unreadable())
            i++
        }
        return Unknown("${tok}_$sub")
    }

    private fun call(name: String): Node {
        val base = if (name == "log" && peek() == "_") { i++; group() } else null
        val power = if (peek() == "^") { i++; group() } else null
        val arg = if (peek() == "(") bracket("(", ")") else factor()
        val call = Call(name, arg, base)
        return if (power != null) Pow(call, power) else call
    }

    companion object {
        private val TIMES = setOf("\\cdot", "\\times", "*", "\\ast", "\\bullet")
        private val DIVIDE = setOf(":", "\\div", "/")
        /** Longest first, so `arcsin` is not read as `a r c sin`. */
        private val FUNCTIONS = listOf("arcsin", "arccos", "arctan", "sin", "cos", "tan", "cot", "exp", "log", "ln", "lg")
        private val FUNCTION_COMMANDS = FUNCTIONS.map { "\\$it" }.toSet()
    }
}

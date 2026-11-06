package com.professoreuler.mathcalc

/**
 * Worked steps for multiple integrals (the inner integrals one after the other, the coordinates put in), line and surface
 * integrals (the curve's or the surface's derivatives, the integrand they make, the integral), directional derivatives and
 * tangent planes, from the same work that gave the answer.
 */
internal object MultiSteps {
    fun of(c: Calculation, latex: String, assumptions: Assumptions): List<Step>? = try {
        Budget.within(MathCalc.LIMIT_MS) { Builder(c).build(latex, assumptions) }?.takeIf { it.size >= 2 }
    } catch (e: CalcException) {
        null
    } catch (e: ArithmeticException) {
        null
    }

    private class Builder(val c: Calculation) {
        val out = ArrayList<Step>()

        fun l(e: Expr) = Format.expr(e, c, latex = true)
        fun v(e: Expr) = c.latexValue(e)
        fun eqv(e: Expr) = v(e).let { if (it.startsWith("\\approx")) it else "= $it" }
        fun p(e: Expr) = l(e).let { if (it.startsWith("-") || it.contains(" + ") || it.contains(" - ")) "\\left($it\\right)" else it }
        fun vec(es: List<Expr>) = "\\left(" + es.joinToString(", ") { l(it) } + "\\right)"
        fun name(s: Expr.Sym) = Format.name(s.name, latex = true)

        fun step(shown: String, key: StepKey, vararg args: String) {
            out += Step(Steps.tidy(shown), StepText(key, args.map { Steps.tidy(it) }))
        }

        fun build(latex: String, assumptions: Assumptions): List<Step>? {
            val call = MultiCalls.split(latex)
            if (call == null) {
                // ∫∫ f dx dy as the formula wrote it.
                val tokens = MathCalc.TOKEN.findAll(latex).map { it.value }.toList()
                val st = Parser(tokens, Flags(assumptions)).statements().singleOrNull() ?: return null
                val side = st.sides.filterNotNull().singleOrNull() ?: return null
                val (body, ranges) = Multiple.unroll(side) ?: return null
                val f = Calculus.evaluate(body)
                val rs = ranges.map { Multiple.Range(it.v, Calculus.evaluate(it.lo), Calculus.evaluate(it.hi)) }
                integral(f, rs, Multiple.System.CARTESIAN, Multiple.THETA)
                return out
            }
            val (name, pieces) = call
            val r = MultiCalls.Reader(pieces, Flags(assumptions))
            when (name) {
                "polarintegral" -> coordinates(r, Multiple.System.POLAR)
                "cylindricalintegral" -> coordinates(r, Multiple.System.CYLINDRICAL)
                "sphericalintegral" -> coordinates(r, Multiple.System.SPHERICAL)
                "lineintegral" -> line(VectorCalculus.lineWork(r, flux = false), r.expr(0), flux = false)
                "surfaceintegral" -> surface(VectorCalculus.surfaceWork(r, flux = false), r.expr(0), flux = false)
                "flux" -> if (pieces.size == 5) line(VectorCalculus.lineWork(r, flux = true), r.expr(0), flux = true)
                    else surface(VectorCalculus.surfaceWork(r, flux = true), r.expr(0), flux = true)
                "directionalderivative" -> directional(r)
                "tangentplane" -> tangent(r)
                else -> return null
            }
            return out
        }

        /** ∫_c^d ∫_a^b [f] d… d… written out, innermost first. */
        fun written(f: String, ranges: List<Multiple.Range>): String {
            var s = f
            for (r in ranges) s = "\\int_{${l(r.lo)}}^{${l(r.hi)}} $s \\, d${name(r.v)}"
            return s
        }

        fun coordinates(r: MultiCalls.Reader, system: Multiple.System) {
            val letters = (1 until r.pieces.size step 3).map { r.letter(it) }
            val names = letters.map { it.name }.toSet()
            val angle = if (system == Multiple.System.SPHERICAL) Multiple.THETA
                else letters.firstOrNull { it == Multiple.THETA || it == Multiple.PHI } ?: return
            val f = r.expr(0, names)
            val ranges = letters.mapIndexed { j, s -> Multiple.Range(s, r.expr(3 * j + 2, names), r.expr(3 * j + 3, names)) }
            integral(f, ranges, system, angle)
        }

        fun integral(f: Expr, ranges: List<Multiple.Range>, system: Multiple.System, angle: Expr.Sym) {
            val result = Multiple.integrate(f, ranges, system, angle)
            step(written(p(f), ranges), StepKey.START)
            if (system != Multiple.System.CARTESIAN) {
                val substitution = Multiple.cartesian(system, angle).joinToString(",\\ ") { (x, e) -> "${name(x)} = ${l(e)}" }
                val element = (if (system == Multiple.System.POLAR) "dA = " else "dV = ") + l(Multiple.jacobian(system)) +
                    ranges.sortedBy { Multiple.letters(system, angle).indexOf(it.v) }.joinToString("") { "\\, d${name(it.v)}" }
                step(written(p(result.integrand), ranges), StepKey.MULTI_COORDINATES, substitution, element)
            }
            val value = Multiple.answer(result)
            if (result.exact == null || result.stages.size != ranges.size) {
                step(eqv(value), StepKey.MULTI_NUMERIC)
                return
            }
            for ((k, stage) in result.stages.withIndex()) {
                val rest = ranges.subList(k + 1, ranges.size)
                val r = ranges[k]
                val shown = if (rest.isEmpty()) eqv(stage) else "= " + written(p(stage), rest)
                val key = if (rest.isEmpty()) StepKey.INTEGRATE_FROM_TO else StepKey.MULTI_INNER
                step(shown, key, name(r.v), l(r.lo), l(r.hi))
            }
            step(v(value), StepKey.RESULT)
        }

        fun line(w: VectorCalculus.LineWork, first: Expr, flux: Boolean) {
            val t = name(w.t)
            val integral = when {
                flux -> "\\int_C \\vec{F} \\cdot \\vec{n} \\, ds"
                first is Expr.Mat -> "\\int_C \\vec{F} \\cdot d\\vec{r}"
                else -> "\\int_C ${p(first)} \\, ds"
            }
            step("$integral,\\ \\vec{r}($t) = ${vec(w.curve)},\\ ${l(w.a)} \\le $t \\le ${l(w.b)}", StepKey.START)
            step("\\vec{r}\\,'($t) = ${vec(w.velocity)}", StepKey.LINE_VELOCITY)
            when {
                flux -> step("P\\,y'($t) - Q\\,x'($t) = ${l(w.integrand)}", StepKey.LINE_NORMAL)
                first is Expr.Mat -> step("\\vec{F}(\\vec{r}($t)) \\cdot \\vec{r}\\,'($t) = ${l(w.integrand)}", StepKey.LINE_DOT)
                else -> step("\\left|\\vec{r}\\,'($t)\\right| = ${l(w.speed ?: return)}", StepKey.LINE_SPEED)
            }
            step("\\int_{${l(w.a)}}^{${l(w.b)}} ${p(w.integrand)} \\, d$t ${eqv(w.value)}", StepKey.INTEGRATE_FROM_TO, t, l(w.a), l(w.b))
            w.potential?.let { phi ->
                step("\\varphi(\\vec{r}(${l(w.b)})) - \\varphi(\\vec{r}(${l(w.a)})) ${eqv(w.value)}", StepKey.LINE_POTENTIAL, l(phi))
            }
            step(v(w.value), StepKey.RESULT)
        }

        fun surface(w: VectorCalculus.SurfaceWork, first: Expr, flux: Boolean) {
            val u = name(w.u)
            val vv = name(w.v)
            val integral = if (flux) "\\iint_S \\vec{F} \\cdot d\\vec{S}" else "\\iint_S ${p(first)} \\, dS"
            step("$integral,\\ \\vec{r}($u, $vv) = ${vec(w.surface)}", StepKey.START)
            step("\\vec{r}_{$u} = ${vec(w.ru)},\\ \\vec{r}_{$vv} = ${vec(w.rv)}", StepKey.SURF_PARTIALS)
            step("\\vec{r}_{$u} \\times \\vec{r}_{$vv} = ${vec(w.normal)}", StepKey.SURF_NORMAL)
            if (flux) step("\\vec{F}(\\vec{r}) \\cdot (\\vec{r}_{$u} \\times \\vec{r}_{$vv}) = ${l(w.integrand)}", StepKey.SURF_DOT)
            else step("\\left|\\vec{r}_{$u} \\times \\vec{r}_{$vv}\\right| = ${l(w.area ?: return)}", StepKey.SURF_AREA)
            val result = Multiple.integrate(w.integrand, w.ranges, Multiple.System.CARTESIAN)
            step(written(p(w.integrand), w.ranges), StepKey.START)
            if (result.exact == null || result.stages.size != w.ranges.size) {
                step(eqv(w.value), StepKey.MULTI_NUMERIC)
                return
            }
            for ((k, stage) in result.stages.withIndex()) {
                val rest = w.ranges.subList(k + 1, w.ranges.size)
                val r = w.ranges[k]
                if (rest.isEmpty()) step(eqv(stage), StepKey.INTEGRATE_FROM_TO, name(r.v), l(r.lo), l(r.hi))
                else step("= " + written(p(stage), rest), StepKey.MULTI_INNER, name(r.v), l(r.lo), l(r.hi))
            }
            step(v(w.value), StepKey.RESULT)
        }

        fun directional(r: MultiCalls.Reader) {
            val w = VectorCalculus.directionalWork(r)
            val f = r.expr(0)
            val point = r.vector(1, setOf(2, 3))
            val direction = r.vector(2, setOf(2, 3))
            step("f = ${l(f)},\\ P = ${vec(point)},\\ \\vec{u} = ${vec(direction)}", StepKey.START)
            step("\\nabla f = ${vec(w.gradient)}", StepKey.DIR_GRADIENT)
            step("\\nabla f(P) = ${vec(w.atPoint)}", StepKey.DIR_AT, vec(point))
            step("\\frac{\\vec{u}}{|\\vec{u}|} = ${vec(w.unit)}", StepKey.DIR_UNIT, l(w.length))
            step("D_{\\vec{u}} f(P) = \\nabla f(P) \\cdot \\frac{\\vec{u}}{|\\vec{u}|} ${eqv(w.value)}", StepKey.DIR_DOT)
            step(v(w.value), StepKey.RESULT)
        }

        fun tangent(r: MultiCalls.Reader) {
            val plane = VectorCalculus.tangentPlaneOf(r)
            val st = r.statement(0)
            val point = plane.points[0].rows.map { it[0] }
            val pp = vec(point)
            if (r.vector(1, setOf(2, 3)).size == 2) {
                step("z = ${l(Calculus.evaluate(st.sides.last { it != null && it != Expr.Sym("z") }!!))},\\ P = $pp", StepKey.START)
                step("F(x, y, z) = f(x, y) - z = 0", StepKey.TP_GRAPH)
            } else {
                step(st.sides.joinToString(" = ") { l(Calculus.evaluate(it!!)) } + ",\\ P = $pp", StepKey.START)
            }
            step("\\vec{n} = ${vec(plane.normal.rows.map { it[0] })}", StepKey.TP_NORMAL, pp)
            step(l(plane.equation) + " = 0", StepKey.TP_EQUATION)
            step(l(plane.equation) + " = 0", StepKey.RESULT)
        }
    }
}

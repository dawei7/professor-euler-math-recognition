package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/** A decimal written as text, as a ball at [p] bits. */
private fun dec(s: String, p: Int): Ball {
    val neg = s.startsWith("-")
    val body = s.removePrefix("-")
    val r = Rational.decimal(body.substringBefore('.'), body.substringAfter('.', ""))
    return Ball.of(if (neg) -r else r, p)
}

/**
 * The special functions against reference digits (mpmath, generated once): the true value inside every ball, and the
 * ball narrow enough for the digits asked for.
 */
class SpecialTest {
    private class Case(val name: String, val digits: String, val compute: (Int) -> Ball)

    private fun rational(s: String): Rational {
        val neg = s.startsWith("-")
        val body = s.removePrefix("-")
        val r = Rational.decimal(body.substringBefore('.'), body.substringAfter('.', ""))
        return if (neg) -r else r
    }

    private fun check(c: Case, digits: Int) {
        val p = (digits * 3.33).toInt() + 24
        val ball = Budget.within(60_000) { c.compute(p) }
        val reference = rational(c.digits)
        val significant = c.digits.removePrefix("-").replace(".", "").trimStart('0').length
        val magnitude = reference.abs()
        val refSlack = magnitude * Rational.of(BigInteger.ONE, BigInteger.TEN.pow(significant - 1))
        val distance = (ball.mid.toRational() - reference).abs()
        val radius = ball.rad.toRational()
        assertTrue("${c.name}: the true value lies outside the ball $ball", distance <= radius + refSlack)
        val wanted = magnitude * Rational.of(BigInteger.ONE, BigInteger.TEN.pow(digits))
        assertTrue("${c.name}: radius ${ball.rad.toDouble()} too wide for $digits digits", radius <= wanted)
    }

    @Test fun toFiftyDigits() {
        for (c in CASES) check(c, 50)
    }

    @Test fun toFifteenDigits() {
        for (c in CASES) check(c, 15)
    }

    @Test fun bernoulliNumbers() {
        assertEquals(Rational.of(1) / Rational.of(6), Bernoulli.b2k(1))
        assertEquals(Rational.of(-1) / Rational.of(30), Bernoulli.b2k(2))
        assertEquals(Rational.of(BigInteger.valueOf(-174611), BigInteger.valueOf(330)), Bernoulli.b2k(10))
    }

    companion object {
        private val CASES = listOf(
            Case("Î“(Â½)", "1.7724538509055160272981674833411451827975494561223871282138077898529112845910322") { p -> Special.gamma(dec("0.5", p), p) },
            Case("Î“(7.3)", "1271.4236336639092730579936266784583378541953782300118871067631489377942097141327") { p -> Special.gamma(dec("7.3", p), p) },
            Case("Î“(âˆ’2.5)", "-0.94530872048294188122568932444861076415869304326527313504736415458821935178188383") { p -> Special.gamma(dec("-2.5", p), p) },
            Case("Î“(0.001)", "999.42377248459546611498220129964400046521761014561223246954217169139602381182840") { p -> Special.gamma(dec("0.001", p), p) },
            Case("Î“(150.5)", "4661072627097377918444637279984648786920938820856505230787802832815587969705137700000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000.") { p -> Special.gamma(dec("150.5", p), p) },
            Case("Ïˆ(1)", "-0.57721566490153286060651209008240243104215933593992359880576723488486772677766467") { p -> Special.digamma(Ball.ONE, p) },
            Case("Ïˆ(2.5)", "0.70315664064524318722569033366791109947350706200623255961953941279501169594961256") { p -> Special.digamma(dec("2.5", p), p) },
            Case("Ïˆ(âˆ’0.5)", "0.036489973978576520559023667001244432806840395339565892952872746128345029282945898") { p -> Special.digamma(dec("-0.5", p), p) },
            Case("erf Â½", "0.52049987781304653768274665389196452873645157575796370005880572564719352171685357") { p -> Special.erf(dec("0.5", p), p) },
            Case("erf 3", "0.99997790950300141455862722387041767962015229291260075034276104515705754331637987") { p -> Special.erf(Ball.of(3), p) },
            Case("erf(âˆ’1.2)", "-0.91031397822963538023840577571537367722789705596902714524350265592775754704528908") { p -> Special.erf(dec("-1.2", p), p) },
            Case("erfc 6", "0.000000000000000021519736712498913116593350399187384630477514061688542100527892051056337238484928") { p -> Special.erfc(Ball.of(6), p) },
            Case("erfc 25", "0.00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000083001725711965227520440127695137227687142231918866995960544485820012808385846651") { p -> Special.erfc(Ball.of(25), p) },
            Case("erfc(âˆ’2)", "1.9953222650189527341620692563672529286108917970400600767383523262004372807199952") { p -> Special.erfc(Ball.of(-2), p) },
            Case("Î¶(3)", "1.2020569031595942853997381615114499907649862923404988817922715553418382057863131") { p -> Special.zeta(Ball.of(3), p) },
            Case("Î¶(Â½)", "-1.4603545088095868128894991525152980124672293310125814905428860878255305294745006") { p -> Special.zeta(dec("0.5", p), p) },
            Case("Î¶(1.5)", "2.6123753486854883433485675679240716305708006524000634075733282488149277676882729") { p -> Special.zeta(dec("1.5", p), p) },
            Case("Î¶(10.25)", "1.0008348121745023500874120656108707850728817301174279512211611262907636837769139") { p -> Special.zeta(dec("10.25", p), p) },
            Case("Î¶(âˆ’2.5)", "0.0085169287778503305423585670283444869362759902200744777658888549519145775559918049") { p -> Special.zeta(dec("-2.5", p), p) },
            Case("Î¶(âˆ’3)", "0.0083333333333333333333333333333333333333333333333333333333333333333333333333333333") { p -> Special.zeta(Ball.of(-3), p) },
            Case("Jâ‚€(1)", "0.76519768655796655144971752610266322090927428975532524186154754911927891221527244") { p -> Special.besselJ(0, Ball.ONE, p) },
            Case("Jâ‚ƒ(10)", "0.058379379305186812342935478410340956290068991381519956394622180663564325173166014") { p -> Special.besselJ(3, Ball.of(10), p) },
            Case("Jâ‚(âˆ’7.5)", "-0.13524842757970550518224049355526462894239620898884829421703229720115151326076361") { p -> Special.besselJ(1, dec("-7.5", p), p) },
            Case("Jâ‚€(50)", "0.055812327669251815004750478529433968176592671045578136196613253154920230156544265") { p -> Special.besselJ(0, Ball.of(50), p) },
            Case("Jâ‚‹â‚‚(3)", "0.48609126058589107690783109411498403480166226564329872737703407014600769445794201") { p -> Special.besselJ(-2, Ball.of(3), p) },
            Case("W(1)", "0.56714329040978387299996866221035554975381578718651250813513107922304579308668457") { p -> Special.lambertW(Ball.ONE, p) },
            Case("W(10)", "1.7455280027406993830743012648753899115352881290809413313222060485555572599415517") { p -> Special.lambertW(Ball.of(10), p) },
            Case("W(âˆ’0.3)", "-0.48940222718021496903623125199629336892341000601635903451146596797368140838162062") { p -> Special.lambertW(dec("-0.3", p), p) },
            Case("W(10â¶)", "11.383358086140052622000156781585004289033774706018865121432386106268986107680189") { p -> Special.lambertW(Ball.of(1000000), p) },
            Case("P(2.5, 3)", "0.69378108158672159912060970890335968975924981984473716932516821139439729199787952") { p -> Special.gammaP(dec("2.5", p), Ball.of(3), p) },
            Case("Q(2.5, 3)", "0.30621891841327840087939029109664031024075018015526283067483178860560270800212048") { p -> Special.gammaQ(dec("2.5", p), Ball.of(3), p) },
            Case("P(10, 1)", "0.00000011142547833872067735305068724025236288094949815466028632175266480925450117110471") { p -> Special.gammaP(Ball.of(10), Ball.ONE, p) },
            Case("I_0.3(2, 5)", "0.57982500000000000000000000000000000000000000000000000000000000000000000000000000") { p -> Special.betaI(dec("0.3", p), Ball.of(2), Ball.of(5), p) },
            Case("I_0.9(Â½, Â½)", "0.79516723530086654835080215244948105190226904777383578919984462572542295357205267") { p -> Special.betaI(dec("0.9", p), dec("0.5", p), dec("0.5", p), p) },
            Case("I_0.5(30, 40)", "0.88579988732335151449450577620936542189156170934438705444335937500000000000000000") { p -> Special.betaI(dec("0.5", p), Ball.of(30), Ball.of(40), p) },
        )
    }
}

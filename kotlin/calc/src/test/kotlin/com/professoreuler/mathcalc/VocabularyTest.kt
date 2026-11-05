package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The command words of the app's other languages, each as a learner of it types them: the verb first or last, the joining
 * words before or after their value, without spaces where the script writes none. Each list reports all its differences.
 */
class VocabularyTest {
    private fun typed(text: String, language: String): String = answerOf(Reports.of(TextInput.read(text, language)).result)

    private fun check(language: String, vararg cases: Pair<String, String>) {
        val wrong = cases.mapNotNull { (text, expected) ->
            val got = typed(text, language)
            if (got != expected) "$text → ${TextInput.read(text, language)}: $got, not $expected" else null
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    private val solved = "x = -2; 2"
    private val factored = "(x - 1)(x + 1)"
    private val derived = "3x^2"

    @Test fun spanish() = check("es",
        "resuelve x^2=4" to solved, "factoriza x^2-1" to factored, "derivada de x^3" to derived,
        "integral de x^2 de 0 a 3" to "9", "límite de sin(x)/x cuando x->0" to "1",
        // A letter "a" of the formula is never "to".
        "resuelve a*x = 6 para x" to "x = 6/a (if a ≠ 0)")

    @Test fun french() = check("fr",
        "résous x^2=4" to solved, "factorise x^2-1" to factored, "dérivée de x^3" to derived,
        "intégrale de x^2 de 0 à 3" to "9", "limite de sin(x)/x quand x->0" to "1")

    @Test fun indonesian() = check("id",
        "selesaikan x^2=4" to solved, "faktorkan x^2-1" to factored, "turunan dari x^3" to derived,
        "integral dari x^2 dari 0 sampai 3" to "9")

    @Test fun italian() = check("it",
        "risolvi x^2=4" to solved, "scomponi x^2-1" to factored, "derivata di x^3" to derived,
        "integrale di x^2 da 0 a 3" to "9")

    @Test fun dutch() = check("nl",
        "los x^2=4 op" to solved, "los op x^2=4" to solved, "ontbind x^2-1 in factoren" to factored,
        "afgeleide van x^3" to derived, "integraal van x^2 van 0 tot 3" to "9")

    @Test fun polish() = check("pl",
        "rozwiąż x^2=4" to solved, "rozłóż na czynniki x^2-1" to factored, "pochodna z x^3" to derived,
        "całka z x^2 od 0 do 3" to "9")

    @Test fun portuguese() = check("pt",
        "resolva x^2=4" to solved, "fatore x^2-1" to factored, "derivada de x^3" to derived,
        "integral de x^2 de 0 até 3" to "9")

    @Test fun turkish() = check("tr",
        "çöz x^2=4" to solved, "x^2=4'ü çöz" to solved, "x^2-1'i çarpanlarına ayır" to factored,
        "x^3'ün türevi" to derived, "x^2'nin 0'dan 3'e kadar integrali" to "9")

    @Test fun vietnamese() = check("vi",
        "giải x^2=4" to solved, "phân tích x^2-1" to factored, "đạo hàm của x^3" to derived,
        "tích phân của x^2 từ 0 đến 3" to "9")

    @Test fun russian() = check("ru",
        "реши x^2=4" to solved, "разложи на множители x^2-1" to factored, "производная x^3" to derived,
        "интеграл от x^2 от 0 до 3" to "9", "производная x^2 y по y" to "x^2")

    @Test fun ukrainian() = check("uk",
        "розв'яжи x^2=4" to solved, "розв’яжи x^2=4" to solved, "розклади на множники x^2-1" to factored,
        "похідна від x^3" to derived, "інтеграл від x^2 від 0 до 3" to "9")

    @Test fun arabic() = check("ar",
        "حل x^2=4" to solved, "حلل x^2-1" to factored, "مشتقة x^3" to derived,
        "تكامل x^2 من 0 إلى 3" to "9")

    @Test fun hebrew() = check("iw",
        "פתור x^2=4" to solved, "פרק לגורמים x^2-1" to factored, "נגזרת של x^3" to derived,
        "אינטגרל של x^2 מ-0 עד 3" to "9")

    @Test fun hindi() = check("hi",
        "हल करें x^2=4" to solved, "x^2=4 को हल करें" to solved, "x^2-1 का गुणनखंड करें" to factored,
        "x^3 का अवकलज" to derived, "x^2 का 0 से 3 तक समाकलन करें" to "9")

    @Test fun japanese() = check("ja",
        "x^2=4を解いて" to solved, "因数分解 x^2-1" to factored, "x^2-1を因数分解して" to factored,
        "x^3を微分" to derived, "x^2を0から3まで積分" to "9", "x^2y を y で微分" to "x^2")

    @Test fun korean() = check("ko",
        "x^2=4를 풀어" to solved, "x^2-1을 인수분해" to factored, "x^2-1을 인수분해하시오" to factored,
        "x^3을 미분" to derived, "x^2을 0부터 3까지 적분" to "9")

    @Test fun thai() = check("th",
        "แก้สมการ x^2=4" to solved, "แยกตัวประกอบx^2-1" to factored, "อนุพันธ์ของ x^3" to derived,
        "อินทิกรัล x^2 จาก 0 ถึง 3" to "9")

    @Test fun chinese() = check("zh-Hans",
        "解方程x^2=4" to solved, "因式分解 x^2-1" to factored, "x^2-1因式分解" to factored, "求导 x^3" to derived,
        "x^3的导数" to derived, "积分 x^2 从0到3" to "9", "x^2从0到3的积分" to "9")

    @Test fun traditionalChinese() = check("zh-Hant-TW",
        "求解 x^2=4" to solved, "x^3的導數" to derived, "積分 x^2 從 0 到 3" to "9")

    private val expanded = "x^2 + 2x + 1"
    private val simplified = "x + 1 (x ≠ 1)"
    private val graph = "graph of x^2"

    /**
     * What the Calculator's hints (calc_empty, calc_input_hint) name in each language, worked out as written there: a hint
     * never shows words the Calculator does not read.
     */
    @Test fun theHintsSayWhatWorks() {
        check("es", "desarrolla (x+1)^2" to expanded, "simplifica (x^2-1)/(x-1)" to simplified, "grafica x^2" to graph,
            "suma de k para k=1 hasta 100" to "5050", "límite de sin(x)/x cuando x->0" to "1", "integral de x^2 de 0 a 3" to "9")
        check("fr", "développe (x+1)^2" to expanded, "simplifie (x^2-1)/(x-1)" to simplified, "trace x^2" to graph,
            "somme de k pour k=1 à 100" to "5050", "limite de sin(x)/x quand x->0" to "1", "intégrale de x^2 de 0 à 3" to "9")
        check("id", "jabarkan (x+1)^2" to expanded, "sederhanakan (x^2-1)/(x-1)" to simplified, "gambarkan x^2" to graph,
            "jumlah dari k untuk k=1 sampai 100" to "5050", "limit dari sin(x)/x saat x->0" to "1", "integral dari x^2 dari 0 sampai 3" to "9")
        check("it", "sviluppa (x+1)^2" to expanded, "semplifica (x^2-1)/(x-1)" to simplified, "disegna x^2" to graph,
            "somma di k per k=1 fino a 100" to "5050", "limite di sin(x)/x quando x->0" to "1", "integrale di x^2 da 0 a 3" to "9")
        check("nl", "werk (x+1)^2 uit" to expanded, "vereenvoudig (x^2-1)/(x-1)" to simplified, "teken x^2" to graph,
            "som van k voor k=1 tot 100" to "5050", "limiet van sin(x)/x als x->0" to "1", "integraal van x^2 van 0 tot 3" to "9")
        check("pl", "wymnóż (x+1)^2" to expanded, "uprość (x^2-1)/(x-1)" to simplified, "narysuj x^2" to graph,
            "suma k dla k=1 do 100" to "5050", "granica sin(x)/x gdy x->0" to "1", "całka z x^2 od 0 do 3" to "9")
        check("pt", "expanda (x+1)^2" to expanded, "simplifique (x^2-1)/(x-1)" to simplified, "plote x^2" to graph,
            "soma de k para k=1 até 100" to "5050", "limite de sin(x)/x quando x->0" to "1", "integral de x^2 de 0 até 3" to "9")
        check("tr", "genişlet (x+1)^2" to expanded, "sadeleştir (x^2-1)/(x-1)" to simplified, "çiz x^2" to graph,
            "lim x->0 sin(x)/x" to "1", "x^2'nin 0'dan 3'e kadar integrali" to "9")
        check("vi", "khai triển (x+1)^2" to expanded, "rút gọn (x^2-1)/(x-1)" to simplified, "vẽ đồ thị x^2" to graph,
            "tổng của k với k=1 đến 100" to "5050", "giới hạn của sin(x)/x khi x->0" to "1", "tích phân của x^2 từ 0 đến 3" to "9")
        check("ru", "раскрой скобки (x+1)^2" to expanded, "упрости (x^2-1)/(x-1)" to simplified, "построй график x^2" to graph,
            "сумма k для k=1 до 100" to "5050", "предел sin(x)/x при x->0" to "1", "интеграл от x^2 от 0 до 3" to "9")
        check("uk", "розкрий дужки (x+1)^2" to expanded, "спрости (x^2-1)/(x-1)" to simplified, "побудуй графік x^2" to graph,
            "сума k для k=1 до 100" to "5050", "границя sin(x)/x при x->0" to "1", "інтеграл від x^2 від 0 до 3" to "9")
        check("ar", "انشر (x+1)^2" to expanded, "بسط (x^2-1)/(x-1)" to simplified, "ارسم x^2" to graph,
            "مجموع k لـk=1 إلى 100" to "5050", "نهاية sin(x)/x عندما x->0" to "1", "تكامل x^2 من 0 إلى 3" to "9")
        check("iw", "פתח סוגריים (x+1)^2" to expanded, "פשט (x^2-1)/(x-1)" to simplified, "שרטט x^2" to graph,
            "סכום של k עבור k=1 עד 100" to "5050", "גבול של sin(x)/x כאשר x->0" to "1", "אינטגרל של x^2 מ-0 עד 3" to "9")
        check("hi", "(x+1)^2 का विस्तार करें" to expanded, "(x^2-1)/(x-1) को सरल करें" to simplified, "x^2 का ग्राफ़" to graph,
            "सीमा x->0 sin(x)/x" to "1", "x^2 का 0 से 3 तक समाकलन करें" to "9")
        check("ja", "(x+1)^2を展開" to expanded, "(x^2-1)/(x-1)を簡単にして" to simplified, "x^2のグラフ" to graph,
            "k=1から100までkの和" to "5050", "x->0のときsin(x)/xの極限" to "1", "x^2を0から3まで積分" to "9")
        check("ko", "(x+1)^2을 전개" to expanded, "(x^2-1)/(x-1)을 간단히" to simplified, "x^2의 그래프" to graph,
            "k=1부터 100까지 k의 합" to "5050", "x->0 일 때 sin(x)/x의 극한" to "1", "x^2을 0부터 3까지 적분" to "9")
        check("th", "กระจาย (x+1)^2" to expanded, "ลดรูป (x^2-1)/(x-1)" to simplified, "วาดกราฟ x^2" to graph,
            "ผลรวมของ k สำหรับ k=1 ถึง 100" to "5050", "ลิมิตของ sin(x)/x เมื่อ x->0" to "1", "อินทิกรัล x^2 จาก 0 ถึง 3" to "9")
        check("zh-Hans", "展开 (x+1)^2" to expanded, "化简 (x^2-1)/(x-1)" to simplified, "画图 x^2" to graph,
            "求和 k 从 k=1 到 100" to "5050", "极限 sin(x)/x 当 x->0" to "1", "积分 x^2 从 0 到 3" to "9")
        check("zh-Hant", "展開 (x+1)^2" to expanded, "化簡 (x^2-1)/(x-1)" to simplified, "畫圖 x^2" to graph,
            "求和 k 從 k=1 到 100" to "5050", "極限 sin(x)/x 當 x->0" to "1", "積分 x^2 從 0 到 3" to "9")
    }

    @Test fun everyAppLanguageHasItsWords() {
        for (language in listOf("en", "de", "es", "fr", "in", "it", "nl", "pl", "pt-BR", "tr", "vi", "ru", "uk", "ar", "iw", "he", "hi",
            "ja", "ko", "th", "zh-Hans", "zh-Hant", "zh-TW")) {
            assertNotNull(language, Vocabulary.of(language))
        }
        // English is understood in every language; math alone is never taken for words.
        check("ja", "solve x^2=4" to solved, "x^2-5x+6=0" to "x = 2; 3")
        for (math in listOf("f(x)=x^2", "f'(x)", "x-1=0", "e^x")) {
            for (language in listOf("tr", "iw", "ar", "ja", "zh", "th", "ko")) assertEquals("$math in $language", TextInput.read(math), TextInput.read(math, language))
        }
    }
}

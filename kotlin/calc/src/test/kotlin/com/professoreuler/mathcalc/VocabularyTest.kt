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
            "сумма k для k от 1 до 100" to "5050", "предел sin(x)/x при x->0" to "1", "интеграл от x^2 от 0 до 3" to "9")
        check("uk", "розкрий дужки (x+1)^2" to expanded, "спрости (x^2-1)/(x-1)" to simplified, "побудуй графік x^2" to graph,
            "сума k для k від 1 до 100" to "5050", "границя sin(x)/x при x->0" to "1", "інтеграл від x^2 від 0 до 3" to "9")
        check("ar", "انشر (x+1)^2" to expanded, "بسط (x^2-1)/(x-1)" to simplified, "ارسم x^2" to graph,
            "مجموع k لـk=1 إلى 100" to "5050", "نهاية sin(x)/x عندما x->0" to "1", "تكامل x^2 من 0 إلى 3" to "9")
        check("iw", "פתח סוגריים (x+1)^2" to expanded, "פשט (x^2-1)/(x-1)" to simplified, "שרטט x^2" to graph,
            "סכום של k עבור k=1 עד 100" to "5050", "גבול של sin(x)/x כאשר x->0" to "1", "אינטגרל של x^2 מ-0 עד 3" to "9")
        check("hi", "(x+1)^2 का विस्तार करें" to expanded, "(x^2-1)/(x-1) को सरल करें" to simplified, "x^2 का ग्राफ़" to graph,
            "सीमा x->0 sin(x)/x" to "1", "x^2 का 0 से 3 तक समाकलन करें" to "9", "k=1 से 100 तक k का योग" to "5050",
            "हल करें x^2=4" to solved, "x^2-1 का गुणनखंड करें" to factored, "x^3 का अवकलज" to derived)
        check("ja", "(x+1)^2を展開" to expanded, "(x^2-1)/(x-1)を簡単にして" to simplified, "x^2のグラフ" to graph,
            "k=1から100までkの和" to "5050", "x->0のときsin(x)/xの極限" to "1", "x^2を0から3まで積分" to "9")
        check("ko", "(x+1)^2을 전개" to expanded, "(x^2-1)/(x-1)을 간단히" to simplified, "x^2의 그래프" to graph,
            "k=1부터 100까지 k의 합" to "5050", "x->0 일 때 sin(x)/x의 극한" to "1", "x^2을 0부터 3까지 적분" to "9")
        check("th", "กระจาย (x+1)^2" to expanded, "ลดรูป (x^2-1)/(x-1)" to simplified, "วาดกราฟ x^2" to graph,
            "ผลรวมของ k สำหรับ k=1 ถึง 100" to "5050", "ลิมิตของ sin(x)/x เมื่อ x->0" to "1", "อินทิกรัล x^2 จาก 0 ถึง 3" to "9")
        check("zh-Hans", "展开 (x+1)^2" to expanded, "化简 (x^2-1)/(x-1)" to simplified, "画图 x^2" to graph,
            "求和 k 从 k=1 到 100" to "5050", "极限 sin(x)/x 当 x->0" to "1", "积分 x^2 从 0 到 3" to "9")
        check("zh-Hant", "展開 (x+1)^2" to expanded, "化簡 (x^2-1)/(x-1)" to simplified, "畫圖 x^2" to graph,
            "求和 k 從 k=1 到 100" to "5050", "極限 sin(x)/x 當 x->0" to "1", "積分 x^2 從 0 到 3" to "9",
            "解方程式 x^2=4" to solved, "因式分解 x^2-1" to factored, "微分 x^3" to derived)
    }

    private val limit2 = "4"
    private val power10 = "1024"

    /**
     * How textbooks and learners of each language put it, as the translation review of 5 November 2026 found them failing:
     * polite and plural imperatives, a verb with an article and a noun, the point of a limit in words, bounds before the
     * formula, "between … and …", a "find" after the noun, articles, particles and school notation.
     */
    @Test fun textbookPhrasings() {
        check("en", "find the derivative of x^3" to derived, "limit of x^2 as x approaches 2" to limit2,
            "integral of x^2 between 0 and 3" to "9", "integrate from 0 to 3 x^2" to "9", "solve the equation x^2=4" to solved)
        check("de", "berechne die Ableitung von x^3" to derived, "Integral von 0 bis 3 von x^2" to "9", "löse die Gleichung x^2=4" to solved,
            "Integral von x^2 zwischen 0 und 3" to "9")
        check("es", "calcula la derivada de x^3" to derived, "halla la derivada de x^3" to derived, "resuelve la ecuación x^2=4" to solved,
            "dibuja la gráfica de x^2" to graph, "representa x^2" to graph, "límite de x^2 cuando x tiende a 2" to limit2,
            "integral de 0 a 3 de x^2" to "9", "integral de x^2 entre 0 y 3" to "9")
        check("fr", "calcule la dérivée de x^3" to derived, "résous l’équation x^2=4" to solved, "trace la courbe de x^2" to graph,
            "limite de x^2 quand x tend vers 2" to limit2, "limite de x^2 en 2" to limit2, "intégrale de 0 à 3 de x^2" to "9",
            "intégrale de x^2 entre 0 et 3" to "9")
        check("it", "calcola la derivata di x^3" to derived, "risolvi l'equazione x^2=4" to solved, "traccia il grafico di x^2" to graph,
            "disegna il grafico di x^2" to graph, "scomponi in fattori x^2-1" to factored, "scomponi x^2-1 in fattori" to factored,
            "limite di x^2 per x che tende a 2" to limit2, "limite per x->2 di x^2" to limit2, "integrale da 0 a 3 di x^2" to "9",
            "integrale di x^2 tra 0 e 3" to "9")
        check("pt", "calcule a derivada de x^3" to derived, "resolva a equação x^2=4" to solved, "desenhe o gráfico de x^2" to graph,
            "esboce o gráfico de x^2" to graph, "limite de x^2 quando x tende a 2" to limit2, "integral de 0 a 3 de x^2" to "9")
        check("nl", "bepaal de afgeleide van x^3" to derived, "los de vergelijking x^2=4 op" to solved, "teken de grafiek van x^2" to graph,
            "limiet van x^2 voor x naar 2" to limit2, "integraal van 0 tot 3 van x^2" to "9")
        check("pl", "wyznacz pochodną x^3" to derived, "pochodna funkcji x^3" to derived, "rozwiąż równanie x^2=4" to solved,
            "narysuj wykres funkcji x^2" to graph, "rozłóż x^2-1 na czynniki" to factored, "całka oznaczona z x^2 od 0 do 3" to "9",
            "całka od 0 do 3 z x^2" to "9")
        check("in", "tentukan turunan dari x^3" to derived, "hitung turunan dari x^3" to derived, "turunan pertama dari x^3" to derived,
            "selesaikan persamaan x^2=4" to solved, "gambarkan grafik fungsi x^2" to graph, "limit x^2 saat x mendekati 2" to limit2,
            "integral dari 0 sampai 3 x^2" to "9")
        check("vi", "tính đạo hàm của x^3" to derived, "tìm đạo hàm của x^3" to derived, "vẽ đồ thị hàm số x^2" to graph,
            "phân tích đa thức thành nhân tử x^2-1" to factored, "tích phân từ 0 đến 3 của x^2" to "9")
        check("tr", "x^2=4'ü çözün" to solved, "x^2-1'i çarpanlarına ayırın" to factored, "x^3'ün türevini bul" to derived,
            "x^3'ün türevini hesapla" to derived, "x^2=4 denklemini çöz" to solved, "x^2 fonksiyonunun grafiğini çiz" to graph,
            "parantezleri aç (x+1)^2" to expanded)
        check("ru", "решите уравнение x^2=4" to solved, "разложите на множители x^2-1" to factored, "раскройте скобки (x+1)^2" to expanded,
            "найдите производную x^3" to derived, "вычислите 2^10" to power10, "постройте график x^2" to graph,
            "вычисли интеграл от x^2 от 0 до 3" to "9", "упростите выражение (x^2-1)/(x-1)" to simplified, "tg(pi/4)" to "1",
            "ctg(pi/4)" to "1", "предел x^2 при x стремится к 2" to limit2)
        check("uk", "розв'яжіть рівняння x^2=4" to solved, "розкладіть на множники x^2-1" to factored, "знайдіть похідну x^3" to derived,
            "обчисли інтеграл від x^2 від 0 до 3" to "9", "спрости вираз (x^2-1)/(x-1)" to simplified, "побудуйте графік x^2" to graph)
        check("ar", "أوجد مشتقة x^3" to derived, "حلل إلى عوامل x^2-1" to factored, "حلّل x^2-1" to factored, "بسّط (x^2-1)/(x-1)" to simplified,
            "احسب التكامل x^2 من 0 إلى 3" to "9", "تكامل x^2 من ٠ إلى ٣" to "9", "٢^١٠" to power10)
        check("iw", "פתרו את המשוואה x^2=4" to solved, "פרקו לגורמים x^2-1" to factored, "הנגזרת של x^3" to derived, "שרטטו x^2" to graph,
            "פתחו סוגריים (x+1)^2" to expanded)
        check("hi", "x^3 का अवकलज ज्ञात कीजिए" to derived, "x^2-1 के गुणनखंड ज्ञात कीजिए" to factored, "x^2 का आलेख खींचिए" to graph)
        check("ja", "x^3の導関数を求めよ" to derived, "x^2=4の解を求めよ" to solved, "x^3の導関数を求めてください" to derived,
            "(x^2-1)/(x-1)を簡単にせよ" to simplified, "y=x^2のグラフをかけ" to "graph of x^2", "方程式x^2=4を解け" to solved,
            "k=1から100までkの和を求めよ" to "5050")
        check("ko", "x^3의 도함수를 구하시오" to derived, "x^2=4의 해를 구하시오" to solved, "x^2-1을 인수분해하여라" to factored,
            "방정식 x^2=4를 푸시오" to solved, "x^2을 0부터 3까지 정적분" to "9")
        check("zh-Hans", "求 x^3 的导数" to derived, "求 x^2 从 0 到 3 的积分" to "9", "把x^2-1因式分解" to factored,
            "对 x^3 求导" to derived, "当x->2时x^2的极限" to limit2, "x^2从0到3的定积分" to "9", "画出 x^2 的图像" to graph)
        check("zh-Hant", "解方程式 x^2=4" to solved, "x^3的導函數" to derived, "微分 x^3" to derived)
        check("th", "จงแก้สมการ x^2=4" to solved, "จงแยกตัวประกอบ x^2-1" to factored, "จงหาอนุพันธ์ของ x^3" to derived,
            "แยกตัวประกอบของ x^2-1" to factored, "วาดกราฟของ x^2" to graph, "หาอินทิกรัลของ x^2 จาก 0 ถึง 3" to "9",
            "จงเขียนกราฟของ x^2" to graph)
    }

    /**
     * Partial fractions as textbooks ask for them, the formula often between the two halves of the command ("rozłóż … na
     * ułamki proste", "פרק את … לשברים חלקיים") and the verb often the factor command's own.
     */
    @Test fun partialFractionsAsTextbooksWriteThem() {
        val expected = TextInput.read("partial fractions of 1/(x^2-1)")
        val cases = mapOf(
            "es" to listOf("descompón 1/(x^2-1) en fracciones simples", "descomposición en fracciones parciales de 1/(x^2-1)", "fracciones simples de 1/(x^2-1)"),
            "fr" to listOf("décomposer en éléments simples 1/(x^2-1)", "décomposez 1/(x^2-1) en éléments simples"),
            "it" to listOf("scomponi 1/(x^2-1) in fratti semplici", "decomposizione in fratti semplici di 1/(x^2-1)"),
            "pt" to listOf("decomponha 1/(x^2-1) em frações parciais", "decomposição em frações parciais de 1/(x^2-1)", "fracoes parciais de 1/(x^2-1)"),
            "nl" to listOf("splits 1/(x^2-1) in partiële breuken", "breuksplitsing van 1/(x^2-1)"),
            "pl" to listOf("rozłóż 1/(x^2-1) na ułamki proste", "rozkład na ułamki proste 1/(x^2-1)", "rozloz 1/(x^2-1) na ulamki proste"),
            "in" to listOf("uraikan 1/(x^2-1) menjadi pecahan parsial", "dekomposisi pecahan parsial dari 1/(x^2-1)"),
            "vi" to listOf("phân tích 1/(x^2-1) thành tổng các phân thức đơn giản", "phân tích 1/(x^2-1) thành các phân thức đơn giản"),
            "tr" to listOf("1/(x^2-1) ifadesini basit kesirlerine ayırınız", "1/(x^2-1)'i basit kesirlere ayırın"),
            "ru" to listOf("разложи 1/(x^2-1) на простейшие дроби", "разложение на простейшие дроби 1/(x^2-1)"),
            "uk" to listOf("розкладіть 1/(x^2-1) на найпростіші дроби", "розклад на найпростіші дроби 1/(x^2-1)"),
            "ar" to listOf("حلل 1/(x^2-1) إلى كسور جزئية", "حلل الى كسور جزئية 1/(x^2-1)"),
            "iw" to listOf("פרק את 1/(x^2-1) לשברים חלקיים", "פרקו לשברים חלקיים 1/(x^2-1)"),
            "hi" to listOf("1/(x^2-1) को आंशिक भिन्नों में"),
            "ko" to listOf("1/(x^2-1)을 부분분수분해", "1/(x^2-1) 부분분수 분해"),
            "th" to listOf("จงแยกเป็นเศษส่วนย่อย 1/(x^2-1)"),
            "zh-Hans" to listOf("把1/(x^2-1)分解为部分分式", "将 1/(x^2-1) 化为部分分式"),
            "zh-Hant" to listOf("把1/(x^2-1)分解為部分分式"),
        )
        val wrong = cases.flatMap { (language, lines) ->
            lines.mapNotNull { line -> TextInput.read(line, language).takeIf { it != expected }?.let { "$language: $line → $it" } }
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    /** A line whose words cannot be made sense of is never a failure of reading: it is the math it holds. */
    @Test fun readingNeverFails() {
        for (line in listOf("integrate x^2+ from 0 to 1", "求 x^2 从 0 到 3 的积", "x^2을 0부터 3까지 적분 +", "derivative of )(", "lim x->")) {
            for (language in listOf("en", "zh", "ko", "es")) {
                val typed = TextInput.read(line, language)
                TextInput.display(typed)
                Reports.of(typed)
            }
        }
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

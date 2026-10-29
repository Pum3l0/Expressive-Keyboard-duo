package com.expressive.keyboard

import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Проверка качества автоисправления на «напечатанных» словах: касания разбрасываются вокруг центров клавиш,
 * как у живого пальца, плюс ошибки на слух, пропуски и перестановки. Итог — в build/eval/autocorrect.txt.
 * Запуск: ./gradlew :app:testDebugUnitTest --tests '*AutocorrectEval*'
 */
class AutocorrectEval {
    private fun words(code: String): List<String> =
        File("src/main/assets/dict/$code.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }

    /** Редкие слова в порядке частоты (в файле они по алфавиту, с местом). */
    private fun rare(code: String): List<String> =
        RareWords.parse(File("src/main/assets/dict/${code}_more.txt").readLines()).sortedBy { it.second }.map { it.first }

    private fun rareEntries(words: List<String>) = words.mapIndexed { i, w -> w to i }

    private fun typos(code: String): Map<String, String> =
        File("src/main/assets/dict/${code}_typos.txt").readLines().mapNotNull { l -> l.split(' ').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()

    /** Палец: разброс вокруг центра клавиши в долях её ширины/высоты. */
    private class Finger(val sx: Float, val sy: Float)

    private data class Typed(val text: String, val touches: List<KeyTouch?>)

    private fun gauss(r: Random): Float {
        var u = 0.0
        var v = 0.0
        while (u == 0.0) u = r.nextDouble()
        while (v == 0.0) v = r.nextDouble()
        return (kotlin.math.sqrt(-2.0 * kotlin.math.ln(u)) * kotlin.math.cos(2 * Math.PI * v)).toFloat()
    }

    private fun tap(lang: KeyboardLanguage, ch: Char, f: Finger, r: Random): Pair<Char, KeyTouch?> {
        val p = lang.keyPositions[ch] ?: return ch to null
        val x = p.second + gauss(r) * f.sx
        val y = p.first + gauss(r) * f.sy
        // клавиша, в которую попал палец: ближайший центр (ряды целые, клавиши шириной 1)
        var best = ch
        var bd = Float.MAX_VALUE
        for ((c, q) in lang.keyPositions) {
            if (!c.isLetter()) continue
            val dy = y - q.first
            val dx = x - q.second
            if (kotlin.math.abs(dy) > 0.5f && kotlin.math.abs(dy) > kotlin.math.abs(y - p.first)) continue
            // сначала ряд (по вертикали), потом ближайшая в ряду
            val rowD = kotlin.math.abs(dy)
            val d = (if (rowD <= 0.5f) 0f else rowD * 10f) + kotlin.math.abs(dx)
            if (d < bd) {
                bd = d
                best = c
            }
        }
        val q = lang.keyPositions[best]!!
        return best to KeyTouch((x - q.second).coerceIn(-0.5f, 0.5f), (y - q.first).coerceIn(-0.5f, 0.5f))
    }

    private val SOUND = mapOf('о' to 'а', 'а' to 'о', 'е' to 'и', 'и' to 'е', 'я' to 'е', 'ш' to 'щ', 'ь' to 'ъ', 'ы' to 'и', 'э' to 'е')

    private fun type(lang: KeyboardLanguage, word: String, f: Finger, r: Random, slips: Boolean): Typed {
        var w = word
        if (slips) {
            when (r.nextInt(100)) {
                in 0..3 -> { // ошибка на слух
                    val idx = w.indices.filter { SOUND.containsKey(w[it]) }
                    if (idx.isNotEmpty()) { val i = idx.random(r); w = w.substring(0, i) + SOUND[w[i]] + w.substring(i + 1) }
                }
                in 4..7 -> if (w.length >= 4) { // пропущенная буква
                    val i = 1 + r.nextInt(w.length - 1); w = w.removeRange(i, i + 1)
                }
                in 8..9 -> if (w.length >= 3) { // перестановка соседних
                    val i = r.nextInt(w.length - 1); w = w.substring(0, i) + w[i + 1] + w[i] + w.substring(i + 2)
                }
                10 -> { // лишняя буква: дважды по одной клавише
                    val i = r.nextInt(w.length); w = w.substring(0, i + 1) + w[i] + w.substring(i + 1)
                }
                in 11..12 -> { // лишняя буква: задели соседнюю клавишу вместе с нужной
                    val i = r.nextInt(w.length)
                    val p = lang.keyPositions[w[i]]
                    val near = lang.keyPositions.filter { (c, q) -> c.isLetter() && c != w[i] && p != null &&
                        kotlin.math.abs(q.first - p.first) < 0.5f && kotlin.math.abs(q.second - p.second) <= 1f }.keys.toList()
                    if (near.isNotEmpty()) { val c = near.random(r); w = if (r.nextBoolean()) w.substring(0, i + 1) + c + w.substring(i + 1) else w.substring(0, i) + c + w.substring(i) }
                }
                13 -> { // пропущен мягкий знак или «й»
                    val i = w.indexOfFirst { it == 'ь' || it == 'й' }
                    if (i >= 0) w = w.removeRange(i, i + 1)
                }
            }
        }
        val out = StringBuilder()
        val t = ArrayList<KeyTouch?>()
        for (c in w) {
            val (k, touch) = tap(lang, c, f, r)
            out.append(k)
            t += touch
        }
        return Typed(out.toString(), t)
    }

    private class Stats {
        var exact = 0; var keptExact = 0; var brokeExact = 0
        var typos = 0; var fixed = 0; var missed = 0; var wrong = 0
        val wrongSamples = ArrayList<String>()
        val brokeSamples = ArrayList<String>()
        val missedSamples = ArrayList<String>()
        override fun toString(): String {
            fun pct(a: Int, b: Int) = if (b == 0) "-" else String.format("%.1f%%", 100.0 * a / b)
            return "верно набрано: $exact, испорчено исправлением: $brokeExact (${pct(brokeExact, exact)})\n" +
                "опечаток: $typos, исправлено: $fixed (${pct(fixed, typos)}), не тронуто: $missed (${pct(missed, typos)}), " +
                "исправлено не так: $wrong (${pct(wrong, typos)})\n" +
                "  испорчено: ${brokeSamples.take(40)}\n  не так: ${wrongSamples.take(40)}\n  пропущено: ${missedSamples.take(40)}"
        }
    }

    private fun run(lang: KeyboardLanguage, dict: List<String>, strength: String, f: Finger, n: Int, seed: Int, touches: Boolean): Stats {
        val r = Random(seed)
        val st = Stats()
        val pool = dict.take(30_000)
        val weights = DoubleArray(pool.size) { 1.0 / (it + 30.0) }
        val total = weights.sum()
        repeat(n) {
            var x = r.nextDouble() * total
            var i = 0
            while (i < pool.size - 1 && x > weights[i]) { x -= weights[i]; i++ }
            val word = pool[i]
            if (word.length < 2 || !word.all { lang.keyPositions.containsKey(it) }) return@repeat
            val typed = type(lang, word, f, r, slips = true)
            val fix = WordPredictorEngine.autoCorrectionFor(typed.text, lang, strength, "", if (touches) typed.touches else null)
            val result = fix ?: typed.text
            if (typed.text == word) {
                st.exact++
                if (result != word) { st.brokeExact++; st.brokeSamples += "$word→$result" }
            } else {
                st.typos++
                when {
                    result == word -> st.fixed++
                    fix == null -> { st.missed++; st.missedSamples += "${typed.text}(${word})" }
                    else -> { st.wrong++; st.wrongSamples += "${typed.text}→$result(${word})" }
                }
            }
        }
        return st
    }

    /** Слова не из словаря (имена, сленг, редкие формы), набранные верно: сколько из них испортит исправление. */
    private fun oovBroken(lang: KeyboardLanguage, oov: List<String>, strength: String, n: Int, seed: Int): Pair<Int, List<String>> {
        val r = Random(seed)
        var broken = 0
        val samples = ArrayList<String>()
        val f = Finger(0.2f, 0.15f)
        repeat(n) {
            val word = oov[r.nextInt(oov.size)]
            if (word.length < 2 || !word.all { lang.keyPositions.containsKey(it) }) return@repeat
            // касания — как у живого пальца, но буквы верные
            val touches = word.map { c -> tap(lang, c, f, r).let { (k, t) -> if (k == c) t else KeyTouch(0f, 0f) } }
            val fix = WordPredictorEngine.autoCorrectionFor(word, lang, strength, "", touches)
            if (fix != null && fix != word) { broken++; samples += "$word→$fix" }
        }
        return broken to samples
    }

    @Test
    fun tune() {
        if (System.getenv("TUNE") == null) return
        val ru = Languages.RU
        val dict = words("ru")
        val more = rare("ru")
        val oov = more.drop(150_000)
        WordPredictorEngine.installDictionary(ru, dict, rareEntries(more.take(150_000)), typos("ru"))
        val fingers = listOf(Finger(0.2f, 0.15f), Finger(0.3f, 0.22f), Finger(0.38f, 0.28f))
        fun score(): Double {
            var fixed = 0; var wrong = 0; var typos = 0; var broke = 0; var exact = 0
            for ((i, f) in fingers.withIndex()) {
                val s = run(ru, dict, "NORMAL", f, 1500, 100 + i, true)
                fixed += s.fixed; wrong += s.wrong; typos += s.typos; broke += s.brokeExact; exact += s.exact
            }
            val (ob, _) = oovBroken(ru, oov, "NORMAL", 800, 5)
            return 100.0 * fixed / typos - 1.5 * 100.0 * wrong / typos - 1.0 * 100.0 * ob / 800 - 20.0 * 100.0 * broke / exact
        }
        val t = WordPredictorEngine.Typo
        t.sigmaX = 0.33f
        t.sigmaY = 0.25f
        val params = listOf(
            Param("subSound", 2.5f, 4.5f, { t.subSound }, { t.subSound = it }),
            Param("subNear", 2f, 4f, { t.subNear }, { t.subNear = it }),
            Param("subFar", 6f, 10f, { t.subFar }, { t.subFar = it }),
            Param("del", 3.5f, 6f, { t.del }, { t.del = it }),
            Param("delNeighbor", 2.5f, 5f, { t.delNeighbor }, { t.delNeighbor = it }),
            Param("delDouble", 1.5f, 3.5f, { t.delDouble }, { t.delDouble = it }),
            Param("ins", 2.5f, 5.5f, { t.ins }, { t.ins = it }),
            Param("insDouble", 1.5f, 3.5f, { t.insDouble }, { t.insDouble = it }),
            Param("insSoft", 1.5f, 4f, { t.insSoft }, { t.insSoft = it }),
            Param("swap", 2.5f, 4.5f, { t.swap }, { t.swap = it }),
            Param("lmScale", 0.9f, 1.3f, { t.lmScale }, { t.lmScale = it }),
            Param("lmK", 10f, 60f, { t.lmK }, { t.lmK = it }),
            Param("keepNormal", 7f, 13f, { t.keepNormal }, { t.keepNormal = it }),
            Param("keepPerChar", 0.3f, 1.5f, { t.keepPerChar }, { t.keepPerChar = it }),
            Param("split", 3f, 7f, { t.split }, { t.split = it }),
            Param("knownMargin", 3f, 6f, { t.knownMargin }, { t.knownMargin = it }),
        )
        val log = StringBuilder()
        var best = score()
        log.appendLine("start $best")
        for (round in 0 until 3) {
            for (p in params) {
                val base = p.get()
                var bestV = base
                for (k in listOf(0.8f, 0.9f, 1.1f, 1.25f)) {
                    val v = (base * k).coerceIn(p.lo, p.hi)
                    if (v == base) continue
                    p.set(v)
                    val sc = score()
                    if (sc > best + 0.1) {
                        best = sc
                        bestV = v
                        log.appendLine("r$round ${p.name}=$bestV → $best")
                    }
                }
                p.set(bestV)
            }
        }
        log.appendLine("итог: " + params.joinToString { "${it.name}=${it.get()}" })
        val out = File("build/eval/tune.txt")
        out.parentFile.mkdirs()
        out.writeText(log.toString())
    }

    class Param(val name: String, val lo: Float, val hi: Float, val get: () -> Float, val set: (Float) -> Unit)

    @Test
    fun evaluate() {
        val ru = Languages.RU
        val en = Languages.EN
        val ruWords = words("ru")
        val enWords = words("en")
        val ruMore = rare("ru")
        WordPredictorEngine.installDictionary(ru, ruWords, rareEntries(ruMore), typos("ru"))
        WordPredictorEngine.installDictionary(en, enWords, rareEntries(rare("en")), typos("en"))
        val report = StringBuilder()
        val fingers = listOf("аккуратно" to Finger(0.2f, 0.15f), "быстро" to Finger(0.3f, 0.22f), "небрежно" to Finger(0.38f, 0.28f))
        for ((fname, f) in fingers) for (strength in listOf("NORMAL", "SAFE")) {
            val t0 = System.nanoTime()
            val s = run(ru, ruWords, strength, f, 4000, 7, touches = true)
            val ms = (System.nanoTime() - t0) / 1_000_000
            report.append("RU $fname $strength (${ms} мс)\n$s\n\n")
        }
        val s = run(en, enWords, "NORMAL", fingers[1].second, 3000, 11, touches = true)
        report.append("EN быстро NORMAL\n$s\n\n")
        // слова не из словаря: самые редкие из дополнительного списка убраны и набираются как «незнакомые»
        WordPredictorEngine.installDictionary(ru, ruWords, rareEntries(ruMore.take(150_000)), typos("ru"))
        for (strength in listOf("NORMAL", "SAFE")) {
            val (b, samples) = oovBroken(ru, ruMore.drop(150_000), strength, 1000, 3)
            report.append("RU незнакомые слова $strength: испорчено $b из 1000: ${samples.take(30)}\n")
        }
        val out = File("build/eval/autocorrect.txt")
        out.parentFile.mkdirs()
        out.writeText(report.toString())
        println(report)
    }
}

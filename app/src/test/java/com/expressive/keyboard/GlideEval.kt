package com.expressive.keyboard

import androidx.compose.ui.geometry.Offset
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Проверка печати свайпом: по словам строятся росчерки, похожие на живые (палец срезает углы, не попадает
 * в центры клавиш, ускоряется на прямых), и декодер должен узнать слово. Итог — в build/eval/glide.txt.
 * Запуск: ./gradlew :app:testDebugUnitTest --tests '*GlideEval*'
 */
class GlideEval {
    private val keyW = 100f
    private val keyH = 190f

    private fun keys(lang: KeyboardLanguage): Map<Char, Offset> =
        lang.keyPositions.filterKeys { it.isLetter() }.mapValues { (_, p) -> Offset((p.second + 0.5f) * keyW, (p.first + 0.5f) * keyH) }

    private fun gauss(r: Random): Float {
        var u = 0.0
        while (u == 0.0) u = r.nextDouble()
        return (kotlin.math.sqrt(-2.0 * kotlin.math.ln(u)) * kotlin.math.cos(2 * Math.PI * r.nextDouble())).toFloat()
    }

    /** Росчерк: точки у букв с разбросом, сглаженные (срезанные углы), с неравномерной скоростью. */
    private fun gesture(word: String, keys: Map<Char, Offset>, r: Random, noise: Float, smooth: Int): List<Offset> {
        val anchors = ArrayList<Offset>()
        for (c in word) {
            val k = keys[c] ?: return emptyList()
            val p = Offset(k.x + gauss(r) * noise * keyW, k.y + gauss(r) * noise * 0.8f * keyH)
            if (anchors.isEmpty() || (anchors.last() - p).getDistance() > keyW * 0.2f) anchors += p
            else anchors += anchors.last() // двойная буква — палец стоит на месте
        }
        if (anchors.size == 1) anchors += anchors[0] + Offset(keyW * 0.05f, 0f)
        // ломаная с плотными точками; на отрезках скорость выше в середине — точек там меньше
        val dense = ArrayList<Offset>()
        for (i in 1 until anchors.size) {
            val a = anchors[i - 1]
            val b = anchors[i]
            val n = 4 + ((b - a).getDistance() / keyW * 6).toInt()
            for (j in 0 until n) {
                val t = j.toFloat() / n
                val eased = t * t * (3 - 2 * t)
                dense += a + (b - a) * eased
            }
        }
        dense += anchors.last()
        // сглаживание скользящим средним: углы срезаются, как у живого пальца
        val out = ArrayList<Offset>(dense.size)
        for (i in dense.indices) {
            var sx = 0f
            var sy = 0f
            var n = 0
            for (j in maxOf(0, i - smooth)..minOf(dense.lastIndex, i + smooth)) {
                sx += dense[j].x; sy += dense[j].y; n++
            }
            out += Offset(sx / n, sy / n)
        }
        // начало и конец — точно у первой и последней точки касания (сглаживание их не сдвигает)
        out[0] = dense.first()
        out[out.lastIndex] = dense.last()
        return out
    }

    private fun accuracy(lexicon: List<Pair<String, Float>>, keys: Map<Char, Offset>, words: List<String>, noise: Float, smooth: Int, n: Int, seed: Int): Double {
        val r = Random(seed)
        val pool = words.take(20_000).filter { w -> w.length >= 2 && w.all { keys.containsKey(it) } }
        val weights = DoubleArray(pool.size) { 1.0 / (it + 30.0) }
        val total = weights.sum()
        var ok = 0
        var all = 0
        repeat(n) {
            var x = r.nextDouble() * total
            var i = 0
            while (i < pool.size - 1 && x > weights[i]) { x -= weights[i]; i++ }
            val word = pool[i]
            val path = gesture(word, keys, r, noise, smooth)
            if (path.size < 2) return@repeat
            all++
            if (GlideDecoder.decode(path, keys, keyW, lexicon, 1).firstOrNull() == word) ok++
        }
        return 100.0 * ok / all
    }

    @Test
    fun tune() {
        if (System.getenv("TUNE") == null) return
        val lang = Languages.RU
        val words = File("src/main/assets/dict/ru.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }
        WordPredictorEngine.installDictionary(lang, words)
        val lexicon = WordPredictorEngine.glideLexicon(lang)
        val keys = keys(lang)
        fun score() = accuracy(lexicon, keys, words, 0.25f, 2, 500, 3) + accuracy(lexicon, keys, words, 0.35f, 3, 500, 4)
        val p = GlideDecoder.P
        val params = listOf<Triple<String, () -> Float, (Float) -> Unit>>(
            Triple("sigma", { p.sigma }, { p.sigma = it }),
            Triple("endSigma", { p.endSigma }, { p.endSigma = it }),
            Triple("lm", { p.lm }, { p.lm = it }),
            Triple("shape", { p.shape }, { p.shape = it }),
            Triple("corner", { p.corner }, { p.corner = it }),
            Triple("length", { p.length }, { p.length = it }),
        )
        val log = StringBuilder()
        var best = score()
        log.appendLine("start $best")
        for (round in 0 until 2) for ((name, get, set) in params) {
            val base = get()
            var bestV = base
            for (k in listOf(0.7f, 0.85f, 1.2f, 1.45f)) {
                set(base * k)
                val sc = score()
                if (sc > best + 0.1) { best = sc; bestV = base * k; log.appendLine("r$round $name=$bestV → $best") }
            }
            set(bestV)
        }
        log.appendLine("итог: " + params.joinToString { "${it.first}=${it.second()}" })
        File("build/eval/glide_tune.txt").writeText(log.toString())
    }

    @Test
    fun evaluate() {
        val lang = Languages.RU
        val words = File("src/main/assets/dict/ru.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }
        WordPredictorEngine.installDictionary(lang, words)
        val lexicon = WordPredictorEngine.glideLexicon(lang)
        val keys = keys(lang)
        val report = StringBuilder()
        for ((name, noise, smooth) in listOf(Triple("аккуратно", 0.15f, 1), Triple("обычно", 0.25f, 2), Triple("небрежно", 0.35f, 3))) {
            val r = Random(17)
            val pool = words.take(20_000).filter { w -> w.length >= 2 && w.all { keys.containsKey(it) } }
            val weights = DoubleArray(pool.size) { 1.0 / (it + 30.0) }
            val total = weights.sum()
            var n = 0
            var top1 = 0
            var top3 = 0
            val misses = ArrayList<String>()
            val t0 = System.nanoTime()
            repeat(1500) {
                var x = r.nextDouble() * total
                var i = 0
                while (i < pool.size - 1 && x > weights[i]) { x -= weights[i]; i++ }
                val word = pool[i]
                val path = gesture(word, keys, r, noise, smooth)
                if (path.size < 2) return@repeat
                val res = GlideDecoder.decode(path, keys, keyW, lexicon, 3)
                n++
                if (res.firstOrNull() == word) top1++
                if (word in res) top3++ else if (misses.size < 60) misses += "$word→${res.firstOrNull()}"
            }
            val ms = (System.nanoTime() - t0) / 1_000_000.0 / n
            report.append(String.format("%s: слов %d, первым %.1f%%, в тройке %.1f%%, %.2f мс на слово\n  мимо: %s\n\n",
                name, n, 100.0 * top1 / n, 100.0 * top3 / n, ms, misses))
        }
        val out = File("build/eval/glide.txt")
        out.parentFile.mkdirs()
        out.writeText(report.toString())
        println(report)
    }
}

package com.expressive.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Опечатка частого слова не становится «своим словом» от одной отмены или пары наборов. */
class LearnedTypoTest {
    @Test
    fun typoOfFrequentWordStaysCorrected() {
        val ru = Languages.RU
        val dict = File("src/main/assets/dict/ru.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }
        WordPredictorEngine.installDictionary(ru, dict, emptyList(), emptyMap())
        WordPredictorEngine.clearUserData()
        assertEquals("привет", WordPredictorEngine.autoCorrectionFor("привит", ru, "NORMAL"))
        // набрали «привит» трижды и один раз отменили исправление — раньше этого хватало, чтобы слово выучилось
        repeat(3) { WordPredictorEngine.learn(ru, "", "привит") }
        WordPredictorEngine.rejectCorrection(ru, "привит")
        assertNull(WordPredictorEngine.autoCorrectionFor("привит", ru, "NORMAL"))
        WordPredictorEngine.clearRejections()
        assertEquals("привет", WordPredictorEngine.autoCorrectionFor("привит", ru, "NORMAL"))
        // а набранное много раз — уже своё слово
        repeat(WordPredictorEngine.LEARNED_TYPO) { WordPredictorEngine.learn(ru, "", "привит") }
        assertNull(WordPredictorEngine.autoCorrectionFor("привит", ru, "NORMAL"))
        WordPredictorEngine.clearUserData()
    }
}

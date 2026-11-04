package af.hazaragi.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test
    fun versionCompare() {
        assertTrue(Versions.compare("v1.0.1", "1.0.0") > 0)
        assertTrue(Versions.compare("1.1.0", "1.1") == 0)
        assertTrue(Versions.compare("v1.10.0", "1.9.9") > 0)
        assertTrue(Versions.compare("1.0", "1.0.1") < 0)
        assertEquals(listOf(1, 2, 3), Versions.parse("v1.2.3-beta"))
    }

    @Test
    fun longTextIsNeverLostWhenChunked() {
        val s = "سلام\n".repeat(5000) + "😀".repeat(3000)
        val parts = chunkText(s, 4000)
        assertEquals(s, parts.joinToString(""))
        assertTrue(parts.all { it.length <= 4000 })
    }

    @Test
    fun emojiPairsAreNotSplit() {
        val s = "a" + "😀".repeat(3000)
        val parts = chunkText(s, 4000)
        assertEquals(s, parts.joinToString(""))
        parts.forEach { assertFalse(Character.isHighSurrogate(it.last())) }
    }

    @Test
    fun shortTextStaysOneChunk() {
        assertEquals(listOf("abc"), chunkText("abc"))
    }
}

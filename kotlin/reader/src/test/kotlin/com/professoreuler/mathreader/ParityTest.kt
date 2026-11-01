package com.professoreuler.mathreader

import java.io.DataInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Holds the Kotlin reader to the Python one on the fixture of `python -m tools.kotlin_fixture`: the same pixels, and
 * the same readings from the same export. Run with `./gradlew :reader:test -Pfixture=<run>/onnx`.
 */
class ParityTest {
    private val dir = System.getProperty("mathreader.fixture").orEmpty().let(::File)

    private class Ink(val label: String, val strokes: List<DoubleArray>, val reading: List<Int>)

    private fun inks(): List<Ink> {
        assumeTrue("no fixture: pass -Pfixture=<run>/onnx", File(dir, "fixture.json").isFile)
        val all = JSONObject(File(dir, "fixture.json").readText()).getJSONArray("inks")
        return (0 until all.length()).map { n ->
            val ink = all.getJSONObject(n)
            val strokes = ink.getJSONArray("strokes")
            val reading = ink.getJSONArray("reading")
            Ink(ink.getString("label"),
                (0 until strokes.length()).map { k -> strokes.getJSONArray(k).let { s -> DoubleArray(s.length()) { s.getDouble(it) } } },
                (0 until reading.length()).map { reading.getInt(it) })
        }
    }

    private fun images(count: Int): List<InkImage> = DataInputStream(File(dir, "fixture.bin").inputStream().buffered()).use { input ->
        List(count) {
            val head = ByteArray(8).also { input.readFully(it) }.let { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN) }
            val h = head.getInt()
            val w = head.getInt()
            val bytes = ByteArray(4 * h * w).also { input.readFully(it) }
            val pixels = FloatArray(h * w)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(pixels)
            InkImage(w, h, pixels)
        }
    }

    @Test
    fun drawsThePixelsPythonDraws() {
        val inks = inks()
        var worst = 0f
        for ((ink, expected) in inks.zip(images(inks.size))) {
            val image = InkRenderer.render(ink.strokes)
            assertEquals(expected.height, image.height)
            assertEquals(expected.width, image.width)
            for (k in image.pixels.indices) worst = maxOf(worst, kotlin.math.abs(image.pixels[k] - expected.pixels[k]))
        }
        println("${inks.size} inks, largest pixel difference $worst")
        assert(worst < 1e-5f) { "pixels differ by up to $worst" }
    }

    @Test
    fun readsWhatPythonReads() {
        val inks = inks()
        val vocab = Vocab.parse(File(dir, "vocab.json").readText())
        var same = 0
        var encoder = 0.0
        var decoder = 0.0
        MathReader(File(dir, "encoder.onnx").readBytes(), File(dir, "step.int8mm.onnx").readBytes(), vocab).use { reader ->
            for (ink in inks) {
                val reading = reader.read(InkRenderer.render(ink.strokes))
                if (reading.ids == ink.reading) same++ else println("differs: ${ink.label} | python ${vocab.decode(ink.reading)} | kotlin ${reading.latex}")
                encoder += reading.encoderMs
                decoder += reading.decoderMs
            }
        }
        println("$same of ${inks.size} read as in Python; on average ${"%.1f".format(encoder / inks.size)} ms encoder, ${"%.1f".format(decoder / inks.size)} ms decoder")
        assertEquals(inks.size, same)
    }

    @Test
    fun decodesLatex() {
        val vocab = Vocab(listOf("<pad>", "<s>", "</s>", "<unk>", "\\pi", "x", "2", "\\frac", "{", "}"))
        assertEquals("\\pi x2", vocab.decode(listOf(1, 4, 5, 6, 2, 5)))
        assertEquals("\\frac{x}{2}", vocab.decode(listOf(7, 8, 5, 9, 8, 6, 9)))
        assertEquals(listOf("a\"b", "\\%", "é"), Vocab.parse("[\n\"a\\\"b\",\n\"\\\\%\",\n\"\\u00e9\"\n]").tokens)
    }
}

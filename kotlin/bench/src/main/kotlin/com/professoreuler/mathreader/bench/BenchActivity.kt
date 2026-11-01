package com.professoreuler.mathreader.bench

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import com.professoreuler.mathreader.InkRenderer
import com.professoreuler.mathreader.MathReader
import com.professoreuler.mathreader.Vocab
import kotlin.concurrent.thread
import org.json.JSONObject

/**
 * Times the reader on this device: the model's loading, the first reading, then every ink of the fixture with 1, 2 and
 * 4 threads, and whether each reading is the desktop's. Shown on screen and logged under the tag MathBench.
 */
class BenchActivity : Activity() {
    private lateinit var text: TextView

    private class Ink(val set: String, val strokes: List<DoubleArray>, val reading: List<Int>)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        text = TextView(this).apply { setPadding(40, 80, 40, 80); textSize = 14f; setTextIsSelectable(true) }
        setContentView(ScrollView(this).apply { addView(text) })
        thread(name = "bench") { runCatching(::bench).onFailure { say("failed: ${Log.getStackTraceString(it)}") } }
    }

    private fun say(line: String) {
        Log.i("MathBench", line)
        runOnUiThread { text.append(line + "\n") }
    }

    private fun asset(name: String) = assets.open(name).use { it.readBytes() }

    private fun bench() {
        val soc = if (Build.VERSION.SDK_INT >= 31) " ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else ""
        say("${Build.MANUFACTURER} ${Build.MODEL}$soc, Android ${Build.VERSION.RELEASE}, ${Runtime.getRuntime().availableProcessors()} cores")
        val all = JSONObject(String(asset("fixture.json"))).getJSONArray("inks")
        val inks = (0 until all.length()).map { n ->
            val ink = all.getJSONObject(n)
            val strokes = ink.getJSONArray("strokes")
            val reading = ink.getJSONArray("reading")
            Ink(ink.getString("set"),
                (0 until strokes.length()).map { k -> strokes.getJSONArray(k).let { s -> DoubleArray(s.length()) { s.getDouble(it) } } },
                (0 until reading.length()).map { reading.getInt(it) })
        }
        val vocab = Vocab.parse(String(asset("vocab.json")))
        val encoder = asset("encoder.onnx")
        val step = asset("step.int8mm.onnx")
        for (threads in listOf(1, 2, 4)) {
            val loading = System.nanoTime()
            MathReader(encoder, step, vocab, threads).use { reader ->
                val loaded = System.nanoTime()
                reader.read(InkRenderer.render(inks[0].strokes))
                say("\n$threads thread(s): model loaded in ${ms(loaded - loading)} ms, first reading ${ms(System.nanoTime() - loaded)} ms")
                repeat(3) { reader.read(InkRenderer.render(inks[it + 1].strokes)) }
                for (set in listOf("sheet", "mathwriting")) {
                    val chosen = inks.filter { it.set == set }
                    if (chosen.isEmpty()) continue
                    val totals = mutableListOf<Double>()
                    var same = 0
                    var render = 0.0
                    var encoding = 0.0
                    var decoding = 0.0
                    for (ink in chosen) {
                        val started = System.nanoTime()
                        val image = InkRenderer.render(ink.strokes)
                        val drawn = System.nanoTime()
                        val reading = reader.read(image)
                        totals.add((System.nanoTime() - started) / 1e6)
                        render += (drawn - started) / 1e6
                        encoding += reading.encoderMs
                        decoding += reading.decoderMs
                        if (reading.ids == ink.reading) same++
                    }
                    totals.sort()
                    val n = chosen.size
                    say("$set, $n inks: median ${"%.0f".format(totals[n / 2])} ms, 90th percentile ${"%.0f".format(totals[(n * 9) / 10])} ms, " +
                        "slowest ${"%.0f".format(totals.last())} ms; on average drawing ${"%.1f".format(render / n)}, encoder " +
                        "${"%.1f".format(encoding / n)}, decoder ${"%.1f".format(decoding / n)} ms; read as on the desktop: $same of $n")
                }
            }
        }
        say("\ndone")
    }

    private fun ms(nanos: Long) = "%.0f".format(nanos / 1e6)
}

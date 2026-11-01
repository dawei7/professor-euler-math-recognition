package com.professoreuler.mathreader

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.FloatBuffer
import java.nio.LongBuffer

/** What was read, and how long the encoder and the decoder took, in milliseconds. */
class Reading(val ids: List<Int>, val latex: String, val encoderMs: Double, val decoderMs: Double)

/**
 * Reads an [InkImage] with the two ONNX graphs of `recognizer/export.py`: the encoder once, then the decoder one token
 * at a time with each layer's keys and values kept, by the same beam search as `export.OnnxReader.read`, so a reading
 * here is the reading on the desktop. Not thread-safe; [close] it when done.
 */
class MathReader(encoder: ByteArray, step: ByteArray, val vocab: Vocab, threads: Int = 1) : AutoCloseable {
    // ONNX Runtime's own telemetry stays off: nothing about the learner leaves the device. Its Android library starts
    // Microsoft's telemetry client from a provider of its own as well, which an app removes from its manifest (README).
    private val env = OrtEnvironment.getEnvironment().also { it.setTelemetry(false) }
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(threads)
        setInterOpNumThreads(1)
    }
    private val encoder = env.createSession(encoder, options)
    private val step = env.createSession(step, options)
    private val layers: Int
    private val heads: Int
    private val dh: Int

    init {
        val shape = (this.step.inputInfo.getValue("past").info as TensorInfo).shape
        layers = shape[0].toInt(); heads = shape[3].toInt(); dh = shape[5].toInt()
    }

    fun read(image: InkImage, beam: Int = 5, maxLen: Int = 200): Reading {
        val started = System.nanoTime()
        val pixels = OnnxTensor.createTensor(env, FloatBuffer.wrap(image.pixels), longArrayOf(1, 1, image.height.toLong(), image.width.toLong()))
        val encoded = pixels.use { encoder.run(mapOf("pixels" to it)) }
        val encoderDone = System.nanoTime()
        try {
            val cross = encoded.get(0) as OnnxTensor
            var past = FloatArray(0)
            var alive = listOf(Beam(listOf(Vocab.BOS), 0.0))
            val done = mutableListOf<Pair<List<Int>, Double>>()
            for (t in 0 until maxLen) {
                val batch = alive.size
                val (logp, next) = step(alive.map { it.seq.last().toLong() }, t, cross, past, batch)
                val vocabSize = logp.size / batch
                val candidates = mutableListOf<Candidate>()
                for ((n, b) in alive.withIndex()) {
                    val row = n * vocabSize
                    val top = (0 until vocabSize).sortedWith(compareByDescending<Int> { logp[row + it] }.thenBy { it }).take(beam)
                    for (token in top) candidates.add(Candidate(b.seq + token, b.score + logp[row + token].toDouble(), n))
                }
                val ranked = candidates.sortedByDescending { it.score }
                val kept = mutableListOf<Beam>()
                val parents = mutableListOf<Int>()
                for (c in ranked) {
                    if (c.seq.last() == Vocab.EOS) {
                        done.add(c.seq.subList(1, c.seq.size - 1) to c.score / (c.seq.size - 1))
                    } else {
                        kept.add(Beam(c.seq, c.score))
                        parents.add(c.parent)
                    }
                    if (kept.size == beam) break
                }
                alive = kept
                if (alive.isEmpty() || (done.size >= beam && done.maxOf { it.second } > alive[0].score / alive[0].seq.size)) break
                past = select(next, parents, batch, t + 1)
            }
            for (b in alive) done.add(b.seq.subList(1, b.seq.size) to b.score / b.seq.size)
            val best = done.maxBy { it.second }.first
            val finished = System.nanoTime()
            return Reading(best, vocab.decode(best), (encoderDone - started) / 1e6, (finished - encoderDone) / 1e6)
        } finally {
            encoded.close()
        }
    }

    private class Beam(val seq: List<Int>, val score: Double)
    private class Candidate(val seq: List<Int>, val score: Double, val parent: Int)

    /** One decoder step for [batch] beams at position [t]: each beam's log probabilities, and the keys and values after it. */
    private fun step(tokens: List<Long>, t: Int, cross: OnnxTensor, past: FloatArray, batch: Int): Pair<FloatArray, FloatArray> {
        val shape = longArrayOf(layers.toLong(), 2, batch.toLong(), heads.toLong(), t.toLong(), dh.toLong())
        OnnxTensor.createTensor(env, LongBuffer.wrap(tokens.toLongArray()), longArrayOf(batch.toLong(), 1)).use { tokenTensor ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(t.toLong())), longArrayOf()).use { position ->
                OnnxTensor.createTensor(env, FloatBuffer.wrap(past), shape).use { pastTensor ->
                    step.run(mapOf("tokens" to tokenTensor, "position" to position, "cross" to cross, "past" to pastTensor)).use { out ->
                        return floats(out.get(0) as OnnxTensor) to floats(out.get(1) as OnnxTensor)
                    }
                }
            }
        }
    }

    private fun floats(tensor: OnnxTensor): FloatArray {
        val buffer = tensor.floatBuffer
        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }

    /** The keys and values of the beams that go on, (layers, 2, beams, heads, [length], dh), from those of all [batch]. */
    private fun select(past: FloatArray, parents: List<Int>, batch: Int, length: Int): FloatArray {
        val block = heads * length * dh
        val out = FloatArray(layers * 2 * parents.size * block)
        var o = 0
        for (lk in 0 until layers * 2) {
            for (p in parents) {
                System.arraycopy(past, (lk * batch + p) * block, out, o, block)
                o += block
            }
        }
        return out
    }

    override fun close() {
        step.close()
        encoder.close()
        options.close()
    }
}

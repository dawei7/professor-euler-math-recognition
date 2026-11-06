package com.professoreuler.mathcalc

/**
 * A time limit for one calculation, on the thread that works it out: [check] is called where the work grows (every sum,
 * product and power built, the long loops), and past the deadline it stops the calculation with [Failure.TOO_LONG]. Nested
 * limits keep the earlier deadline.
 */
internal object Budget {
    private val deadline = ThreadLocal<Long?>()
    private val counter = ThreadLocal<IntArray>()

    fun <T> within(millis: Long, block: () -> T): T {
        val previous = deadline.get()
        val mine = System.nanoTime() + millis * 1_000_000
        deadline.set(if (previous != null && previous < mine) previous else mine)
        try {
            return block()
        } finally {
            deadline.set(previous)
        }
    }

    /**
     * [block] within the calculation's time where one runs, else within [millis] of its own: a number function the parser
     * works out before the calculation's time starts is bounded all the same, and "Try longer" still gives it more.
     */
    fun <T> bounded(millis: Long, block: () -> T): T = if (deadline.get() != null) block() else within(millis, block)

    fun check() {
        val d = deadline.get() ?: return
        val c = counter.get() ?: IntArray(1).also { counter.set(it) }
        if (++c[0] and 255 == 0 && System.nanoTime() > d) throw CalcException(Failure.TOO_LONG)
    }
}

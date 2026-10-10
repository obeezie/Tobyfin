package org.jellyfin.androidtv.ui.home

/**
 * Starts Home rows in screen order with at most [limit] loading at once. Starting every row at
 * once let below-the-fold genre rows take the client's few connections to the server before the
 * rows on the first screen had sent their requests.
 */
class HomeRowLoadQueue<T>(
    private val limit: Int,
    private val start: (row: T, done: () -> Unit) -> Unit,
) {
    private val waiting = ArrayDeque<T>()
    private var running = 0

    fun enqueue(row: T) {
        waiting.addLast(row)
        startNext()
    }

    /** Starts every waiting row regardless of the limit, for example when one row never answers. */
    fun drain() {
        while (waiting.isNotEmpty()) {
            running++
            start(waiting.removeFirst()) {}
        }
    }

    private fun startNext() {
        while (running < limit && waiting.isNotEmpty()) {
            val row = waiting.removeFirst()
            running++
            var finished = false
            start(row) {
                if (!finished) {
                    finished = true
                    running--
                    startNext()
                }
            }
        }
    }

    companion object {
        /**
         * The queue rows should start through while Home adds them. Set only around Home's
         * synchronous row setup on the main thread; rows added elsewhere load immediately.
         */
        var current: HomeRowLoadQueue<org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter>? = null

        fun retrieve(adapter: org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter) {
            current?.enqueue(adapter) ?: adapter.Retrieve()
        }
    }
}

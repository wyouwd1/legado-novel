package io.legado.app.model.cache

class CacheDownloadAdmissionQueue(
    maxActiveBooks: Int
) {

    /** 0/负数会让 pollReady 永远无法准入任何书；按至少 1 本处理，保持下载服务可推进。 */
    private val maxActiveBooks = maxActiveBooks.coerceAtLeast(1)

    private val requests = ArrayDeque<CacheDownloadRequest>()

    val size: Int
        get() = requests.size

    fun isEmpty(): Boolean = requests.isEmpty()

    fun shouldQueue(request: CacheDownloadRequest, activeBookUrls: Set<String>): Boolean {
        return request.bookUrl !in activeBookUrls && activeBookUrls.size >= maxActiveBooks
    }

    fun add(request: CacheDownloadRequest) {
        requests.addLast(request)
    }

    fun pollReady(activeBookUrls: Set<String>): CacheDownloadRequest? {
        if (requests.isEmpty()) return null

        val activeBookRequestIndex = requests.indexOfFirst {
            it.bookUrl in activeBookUrls
        }
        if (activeBookRequestIndex >= 0) {
            return requests.removeAt(activeBookRequestIndex)
        }

        if (activeBookUrls.size >= maxActiveBooks) return null
        return requests.removeFirst()
    }

    fun removeBook(bookUrl: String): Boolean {
        var removed = false
        val iterator = requests.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().bookUrl == bookUrl) {
                iterator.remove()
                removed = true
            }
        }
        return removed
    }

    /** 取出并清空全部待准入请求（用于全局暂停前落盘为 model）。 */
    fun drainAll(): List<CacheDownloadRequest> {
        if (requests.isEmpty()) return emptyList()
        val drained = requests.toList()
        requests.clear()
        return drained
    }

    fun clear() {
        requests.clear()
    }
}

package org.eclipse.kuksa.testapp.domain

class PathHistory(private val maxSize: Int = 20) {
    private val history = mutableListOf<String>()

    @Synchronized
    fun record(path: String) {
        if (path.isBlank()) return
        history.remove(path)
        history.add(0, path)
        while (history.size > maxSize) {
            history.removeAt(history.size - 1)
        }
    }

    @Synchronized
    fun toList(): List<String> {
        return history.toList()
    }
}

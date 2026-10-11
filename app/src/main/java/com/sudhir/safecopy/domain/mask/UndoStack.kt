package com.sudhir.safecopy.domain.mask

/** Snapshot undo/redo history (spec 7.3), keeping at most [capacity] undo steps. */
class UndoStack<T>(initial: T, private val capacity: Int = 50) {
    private val past = ArrayDeque<T>()
    private val future = ArrayDeque<T>()

    var current: T = initial
        private set

    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    fun push(next: T) {
        if (next == current) return
        past.addLast(current)
        if (past.size > capacity) past.removeFirst()
        current = next
        future.clear()
    }

    fun undo(): Boolean {
        val prev = past.removeLastOrNull() ?: return false
        future.addLast(current)
        current = prev
        return true
    }

    fun redo(): Boolean {
        val next = future.removeLastOrNull() ?: return false
        past.addLast(current)
        current = next
        return true
    }

    /** Applies [f] to every snapshot, e.g. to keep history valid after the image rotates. */
    fun mapAll(f: (T) -> T) {
        current = f(current)
        past.replaceAll(f)
        future.replaceAll(f)
    }
}

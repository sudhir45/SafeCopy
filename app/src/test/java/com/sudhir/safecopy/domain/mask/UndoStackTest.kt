package com.sudhir.safecopy.domain.mask

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UndoStackTest {
    @Test
    fun undoAndRedoWalkHistory() {
        val s = UndoStack(0)
        s.push(1)
        s.push(2)
        assertThat(s.undo()).isTrue()
        assertThat(s.current).isEqualTo(1)
        assertThat(s.undo()).isTrue()
        assertThat(s.current).isEqualTo(0)
        assertThat(s.undo()).isFalse()
        assertThat(s.redo()).isTrue()
        assertThat(s.current).isEqualTo(1)
    }

    @Test
    fun pushClearsRedo() {
        val s = UndoStack(0)
        s.push(1)
        s.undo()
        s.push(5)
        assertThat(s.canRedo).isFalse()
        assertThat(s.current).isEqualTo(5)
    }

    @Test
    fun pushingSameValueIsNoOp() {
        val s = UndoStack(3)
        s.push(3)
        assertThat(s.canUndo).isFalse()
    }

    @Test
    fun capacityDropsOldestSteps() {
        val s = UndoStack(0, capacity = 50)
        for (i in 1..60) s.push(i)
        var undos = 0
        while (s.undo()) undos++
        assertThat(undos).isEqualTo(50)
        assertThat(s.current).isEqualTo(10)
    }

    @Test
    fun mapAllRewritesEverySnapshot() {
        val s = UndoStack(1)
        s.push(2)
        s.push(3)
        s.undo()
        s.mapAll { it * 10 }
        assertThat(s.current).isEqualTo(20)
        s.undo()
        assertThat(s.current).isEqualTo(10)
        s.redo()
        s.redo()
        assertThat(s.current).isEqualTo(30)
    }
}

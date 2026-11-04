package com.senzaproject.createapp.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionTileTest {

    @Test
    fun `ponsel memakai 2 kolom`() {
        assertEquals(2, tileColumnsFor(360.dp))
        assertEquals(2, tileColumnsFor(559.dp))
    }

    @Test
    fun `layar lebar memakai 3 kolom`() {
        assertEquals(3, tileColumnsFor(560.dp))
        assertEquals(3, tileColumnsFor(840.dp))
    }
}

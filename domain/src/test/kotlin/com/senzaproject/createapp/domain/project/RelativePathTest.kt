package com.senzaproject.createapp.domain.project

import com.senzaproject.createapp.domain.result.OperationException
import com.senzaproject.createapp.domain.result.OperationFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RelativePathTest {

    private fun assertRejected(path: String) {
        try {
            splitRelativePath(path)
            fail("seharusnya ditolak: \"$path\"")
        } catch (e: OperationException) {
            assertEquals(OperationFailure.OUTSIDE_PROJECT, e.failure)
        }
    }

    @Test
    fun `path kosong adalah akar`() {
        assertTrue(splitRelativePath("").isEmpty())
    }

    @Test
    fun `path bersarang dipecah per segmen`() {
        assertEquals(listOf("app", "src", "Main.kt"), splitRelativePath("app/src/Main.kt"))
    }

    @Test
    fun `path berbahaya ditolak`() {
        assertRejected("..")
        assertRejected("../rahasia")
        assertRejected("a/../b")
        assertRejected("a/./b")
        assertRejected("/etc/passwd")
        assertRejected("a//b")
        assertRejected("a/b/")
        assertRejected("a\\b")
        assertRejected("a\u0000b")
    }

    @Test
    fun `fungsi path pembantu`() {
        assertEquals("a/b", parentOf("a/b/c.kt"))
        assertEquals("", parentOf("c.kt"))
        assertEquals("c.kt", nameOf("a/b/c.kt"))
        assertEquals("c.kt", nameOf("c.kt"))
        assertEquals("c.kt", joinPath("", "c.kt"))
        assertEquals("a/c.kt", joinPath("a", "c.kt"))
    }
}

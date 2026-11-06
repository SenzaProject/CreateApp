package com.senzaproject.createapp.core.project

import com.senzaproject.createapp.domain.result.OperationException
import com.senzaproject.createapp.domain.result.OperationFailure
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun storage() = ProjectStorage(File(tmp.root, "projects").also { it.mkdirs() })

    @Test
    fun `id valid dan tidak valid`() {
        val s = storage()
        assertTrue(s.isValidId("a"))
        assertTrue(s.isValidId("_a"))
        assertTrue(s.isValidId("Aplikasi-Saya-2"))
        assertTrue(s.isValidId("a".repeat(64)))
        assertFalse(s.isValidId("a".repeat(65)))
        assertFalse(s.isValidId("-a"))
        assertFalse(s.isValidId(".a"))
        assertFalse(s.isValidId(".."))
        assertFalse(s.isValidId("a/b"))
        assertFalse(s.isValidId(""))
    }

    @Test
    fun `projectDir menolak id tidak valid dan folder yang tidak ada`() {
        val s = storage()
        for (id in listOf("..", "a/b", "tidak-ada")) {
            try {
                s.projectDir(id)
                fail("seharusnya ditolak: $id")
            } catch (e: OperationException) {
                assertEquals(OperationFailure.NOT_FOUND, e.failure)
            }
        }
    }

    @Test
    fun `uniqueId tidak membedakan huruf besar kecil`() {
        val s = storage()
        File(s.root, "Abc").mkdirs()
        assertEquals("abc-2", s.uniqueId("abc"))
        File(s.root, "abc-2").mkdirs()
        assertEquals("abc-3", s.uniqueId("abc"))
        assertEquals("xyz", s.uniqueId("xyz"))
    }

    @Test
    fun `resolve menerima path bersarang dan menolak keluar dari proyek`() {
        val s = storage()
        val project = File(s.root, "p").also { it.mkdirs() }
        assertEquals(File(project, "a/b.kt"), s.resolve(project, "a/b.kt"))
        assertEquals(project, s.resolve(project, ""))
        for (bad in listOf("..", "../x", "a/../../x", "/etc/passwd")) {
            try {
                s.resolve(project, bad)
                fail("seharusnya ditolak: $bad")
            } catch (e: OperationException) {
                assertEquals(OperationFailure.OUTSIDE_PROJECT, e.failure)
            }
        }
    }

    @Test
    fun `resolve menolak folder metadata`() {
        val s = storage()
        val project = File(s.root, "p").also { it.mkdirs() }
        try {
            s.resolve(project, ".createapp/project.properties")
            fail("seharusnya ditolak")
        } catch (e: OperationException) {
            assertEquals(OperationFailure.RESERVED_NAME, e.failure)
        }
    }
}

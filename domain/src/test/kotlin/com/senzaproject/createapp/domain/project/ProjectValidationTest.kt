package com.senzaproject.createapp.domain.project

import com.senzaproject.createapp.domain.result.OperationException
import com.senzaproject.createapp.domain.result.OperationFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProjectValidationTest {

    @Test
    fun `nama biasa valid`() {
        assertNull(checkEntryName("MainActivity.kt"))
        assertNull(checkEntryName(".gitignore"))
        assertNull(checkEntryName("build.gradle.kts"))
        assertNull(checkProjectName("Aplikasi Saya"))
    }

    @Test
    fun `nama kosong atau spasi tepi ditolak`() {
        assertEquals(OperationFailure.INVALID_NAME, checkEntryName(""))
        assertEquals(OperationFailure.INVALID_NAME, checkEntryName("   "))
        assertEquals(OperationFailure.INVALID_NAME, checkEntryName(" a"))
        assertEquals(OperationFailure.INVALID_NAME, checkEntryName("a "))
    }

    @Test
    fun `nama dengan pemisah path atau karakter terlarang ditolak`() {
        for (bad in listOf("a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b", "a\u0000b", "a\nb")) {
            assertEquals("seharusnya ditolak: $bad", OperationFailure.INVALID_NAME, checkEntryName(bad))
        }
    }

    @Test
    fun `titik dan dua titik ditolak`() {
        assertEquals(OperationFailure.INVALID_NAME, checkEntryName("."))
        assertEquals(OperationFailure.INVALID_NAME, checkEntryName(".."))
        assertEquals(OperationFailure.INVALID_NAME, checkEntryName("nama."))
    }

    @Test
    fun `nama terlalu panjang ditolak`() {
        assertNull(checkEntryName("a".repeat(MAX_ENTRY_NAME_LENGTH)))
        assertEquals(OperationFailure.INVALID_NAME, checkEntryName("a".repeat(MAX_ENTRY_NAME_LENGTH + 1)))
        assertNull(checkProjectName("a".repeat(MAX_PROJECT_NAME_LENGTH)))
        assertEquals(OperationFailure.INVALID_NAME, checkProjectName("a".repeat(MAX_PROJECT_NAME_LENGTH + 1)))
    }

    @Test
    fun `folder metadata dicadangkan`() {
        assertEquals(OperationFailure.RESERVED_NAME, checkEntryName(METADATA_DIR_NAME))
        assertEquals(OperationFailure.RESERVED_NAME, checkEntryName(".CreateApp"))
    }

    @Test
    fun `requireValidEntryName melempar OperationException`() {
        try {
            requireValidEntryName("a/b")
            fail("seharusnya melempar")
        } catch (e: OperationException) {
            assertEquals(OperationFailure.INVALID_NAME, e.failure)
        }
        assertEquals("ok.kt", requireValidEntryName("ok.kt"))
    }

    @Test
    fun `package valid`() {
        assertNull(checkPackageName("com.contoh.app"))
        assertNull(checkPackageName("com.senzaproject.createapp"))
        assertNull(checkPackageName("id.my_app.v2"))
    }

    @Test
    fun `package tidak valid`() {
        for (bad in listOf("", "app", "Com.contoh", "com..contoh", "com.contoh.", ".com.contoh", "com.1contoh", "com.contoh app", "com.class.app", "com.contoh-app")) {
            assertEquals("seharusnya ditolak: $bad", OperationFailure.INVALID_PACKAGE, checkPackageName(bad))
        }
    }

    @Test
    fun `slugify menghasilkan id folder yang aman`() {
        assertEquals("Aplikasi-Saya", slugify("Aplikasi Saya"))
        assertEquals("Tom-Jerry", slugify("Tom & Jerry"))
        assertEquals("a-b", slugify("a --- b"))
        assertEquals("proyek", slugify("   "))
        assertEquals("proyek", slugify("!!!"))
        assertEquals("_x", slugify("_x"))
    }

    @Test
    fun `slugify membatasi panjang dan tidak berakhir dengan strip`() {
        val slug = slugify("a".repeat(47) + " b")
        assertTrue(slug.length <= 48)
        assertFalse(slug.endsWith("-"))
    }

    @Test
    fun `identifier`() {
        assertTrue(isValidIdentifier("MainActivity"))
        assertTrue(isValidIdentifier("_x1"))
        assertFalse(isValidIdentifier("1x"))
        assertFalse(isValidIdentifier("class"))
        assertFalse(isValidIdentifier("my-file"))
    }
}

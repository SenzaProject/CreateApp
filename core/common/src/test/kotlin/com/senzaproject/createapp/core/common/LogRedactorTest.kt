package com.senzaproject.createapp.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LogRedactorTest {

    @Test
    fun `token GitHub disamarkan`() {
        val fake = "ghp_" + "a".repeat(30)
        val result = LogRedactor.redact("push dengan $fake selesai")
        assertEquals("push dengan [REDACTED_TOKEN] selesai", result)
    }

    @Test
    fun `token fine-grained GitHub disamarkan`() {
        val fake = "github_pat_" + "B".repeat(30)
        assertFalse(LogRedactor.redact("auth $fake").contains("BBBB"))
    }

    @Test
    fun `password dan secret disamarkan`() {
        assertEquals("password=***", LogRedactor.redact("password=abc123"))
        assertEquals("storePassword: ***", LogRedactor.redact("storePassword: rahasia"))
        assertEquals("token = ***", LogRedactor.redact("token = xyz"))
    }

    @Test
    fun `teks biasa tidak berubah`() {
        val text = "Build selesai dalam 24 detik"
        assertEquals(text, LogRedactor.redact(text))
    }
}

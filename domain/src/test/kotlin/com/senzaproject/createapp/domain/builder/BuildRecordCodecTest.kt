package com.senzaproject.createapp.domain.builder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BuildRecordCodecTest {

    private fun record(
        name: String = "Proyek Saya",
        failureCode: String? = null,
        failureDetail: String? = null,
        artifacts: List<String> = listOf("app-debug.apk"),
    ) = BuildRecord(
        id = "id-1",
        projectId = "proyek_saya",
        projectName = name,
        type = BuildType.DEBUG,
        status = BuildStatus.SUCCESS,
        startedAtMillis = 1_700_000_000_000L,
        durationMillis = 65_000L,
        tasksSeen = 42,
        errorCount = 0,
        warningCount = 3,
        artifactNames = artifacts,
        failureCode = failureCode,
        failureDetail = failureDetail,
    )

    @Test
    fun `encode lalu decode menghasilkan catatan yang sama`() {
        val original = record()
        assertEquals(original, BuildRecordCodec.decode(BuildRecordCodec.encode(original)))
    }

    @Test
    fun `tab baris baru dan backslash di dalam kolom tidak merusak format`() {
        val original = record(
            name = "a\tb\nc\\d\re",
            failureCode = "SYNC",
            failureDetail = "baris1\nbaris2\t\\x",
        )
        val line = BuildRecordCodec.encode(original)
        assertFalse(line.contains('\n'))
        assertFalse(line.contains('\r'))
        assertEquals(original, BuildRecordCodec.decode(line))
    }

    @Test
    fun `unicode dan daftar artefak ganda dan kosong`() {
        val many = record(name = "Aplikasi \u00e9\u4e2d", artifacts = listOf("a.apk", "b.apk"))
        assertEquals(many, BuildRecordCodec.decode(BuildRecordCodec.encode(many)))
        val none = record(artifacts = emptyList())
        assertEquals(none, BuildRecordCodec.decode(BuildRecordCodec.encode(none)))
    }

    @Test
    fun `baris rusak menghasilkan null`() {
        assertNull(BuildRecordCodec.decode(""))
        assertNull(BuildRecordCodec.decode("bukan catatan"))
        assertNull(BuildRecordCodec.decode("a\tb\tc"))
        val good = BuildRecordCodec.encode(record())
        assertNull(BuildRecordCodec.decode(good.replace("DEBUG", "TIDAK_ADA")))
        assertNull(BuildRecordCodec.decode(good.replace("SUCCESS", "TIDAK_ADA")))
        assertNull(BuildRecordCodec.decode(good.replace("1700000000000", "xx")))
        assertNull(BuildRecordCodec.decode(good + "\\q"))
    }
}

package com.senzaproject.createapp.domain.builder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactCatalogTest {

    private fun file(
        project: String = "p1",
        name: String = "app-debug.apk",
        size: Long = 1000L,
        modified: Long = 5_000L,
    ) = StoredArtifact(
        projectId = project,
        name = name,
        kind = ArtifactRules.kindOf(name)!!,
        sizeBytes = size,
        modifiedAtMillis = modified,
    )

    private fun record(
        id: String,
        project: String = "p1",
        names: List<String> = listOf("app-debug.apk"),
        status: BuildStatus = BuildStatus.SUCCESS,
        type: BuildType = BuildType.DEBUG,
        started: Long = 1_000L,
        duration: Long = 500L,
    ) = BuildRecord(
        id = id,
        projectId = project,
        projectName = "Nama $project",
        type = type,
        status = status,
        startedAtMillis = started,
        durationMillis = duration,
        tasksSeen = 3,
        errorCount = 0,
        warningCount = 0,
        artifactNames = names,
        failureCode = null,
        failureDetail = null,
    )

    private fun entry(
        name: String = "app-debug.apk",
        validation: ArtifactValidation = ArtifactValidation.VALID,
    ) = ArtifactEntry(file(name = name), "Proyek", null, validation)

    // --- merge ---

    @Test
    fun `hanya file nyata yang menjadi entri walau riwayat mencatat artefak lain`() {
        val entries = ArtifactCatalog.merge(
            stored = listOf(file(name = "app-debug.apk")),
            records = listOf(record("r1", names = listOf("app-debug.apk", "hantu.apk"))),
            projectNames = mapOf("p1" to "Proyek Satu"),
            validations = emptyMap(),
        )
        assertEquals(1, entries.size)
        assertEquals("app-debug.apk", entries[0].name)
    }

    @Test
    fun `tanpa riwayat entri tetap tampil dengan keterangan kosong`() {
        val entries = ArtifactCatalog.merge(listOf(file()), emptyList(), emptyMap(), emptyMap())
        assertEquals(1, entries.size)
        assertNull(entries[0].build)
        assertNull(entries[0].projectName)
        assertEquals(ArtifactValidation.NOT_CHECKED, entries[0].validation)
    }

    @Test
    fun `nama proyek memakai peta proyek lebih dulu lalu nama di riwayat`() {
        val withMap = ArtifactCatalog.merge(listOf(file()), listOf(record("r1")), mapOf("p1" to "Nama Terbaru"), emptyMap())
        assertEquals("Nama Terbaru", withMap[0].projectName)
        val withoutMap = ArtifactCatalog.merge(listOf(file()), listOf(record("r1")), emptyMap(), emptyMap())
        assertEquals("Nama p1", withoutMap[0].projectName)
    }

    @Test
    fun `pemilik file adalah build berhasil terbaru yang mencatat nama itu`() {
        val records = listOf(
            record("lama", started = 1_000L, type = BuildType.DEBUG),
            record("baru", started = 9_000L, type = BuildType.DEBUG),
            record("gagal", started = 20_000L, status = BuildStatus.FAILED),
            record("lain", project = "p2", started = 30_000L),
        )
        val entries = ArtifactCatalog.merge(listOf(file()), records, emptyMap(), emptyMap())
        assertEquals("baru", entries[0].build?.recordId)
    }

    @Test
    fun `urutan terbaru lebih dulu lalu proyek lalu nama`() {
        val entries = ArtifactCatalog.merge(
            listOf(
                file(project = "b", name = "x.apk", modified = 100L),
                file(project = "a", name = "x.apk", modified = 100L),
                file(project = "a", name = "y.apk", modified = 300L),
                file(project = "a", name = "a.aab", modified = 100L),
            ),
            emptyList(),
            emptyMap(),
            emptyMap(),
        )
        assertEquals(listOf("a/y.apk", "a/a.aab", "a/x.apk", "b/x.apk"), entries.map { it.key })
    }

    @Test
    fun `waktu build jatuh ke waktu selesai riwayat bila waktu ubah tidak dilaporkan`() {
        val entries = ArtifactCatalog.merge(
            listOf(file(modified = 0L)),
            listOf(record("r1", started = 1_000L, duration = 250L)),
            emptyMap(),
            emptyMap(),
        )
        assertEquals(1_250L, entries[0].builtAtMillis)
        assertEquals(0L, ArtifactCatalog.merge(listOf(file(modified = 0L)), emptyList(), emptyMap(), emptyMap())[0].builtAtMillis)
    }

    @Test
    fun `hasil pemeriksaan hanya dipakai bila file tidak berubah`() {
        val original = file(size = 1000L, modified = 5_000L)
        val cache = mapOf(original.fingerprint to ArtifactValidation.VALID)
        assertEquals(ArtifactValidation.VALID, ArtifactCatalog.merge(listOf(original), emptyList(), emptyMap(), cache)[0].validation)
        val changedSize = original.copy(sizeBytes = 1001L)
        assertEquals(ArtifactValidation.NOT_CHECKED, ArtifactCatalog.merge(listOf(changedSize), emptyList(), emptyMap(), cache)[0].validation)
        val changedTime = original.copy(modifiedAtMillis = 6_000L)
        assertEquals(ArtifactValidation.NOT_CHECKED, ArtifactCatalog.merge(listOf(changedTime), emptyList(), emptyMap(), cache)[0].validation)
    }

    // --- riwayat ---

    @Test
    fun `riwayat hanya memuat build berhasil yang menghasilkan artefak dan terbaru dulu`() {
        val records = listOf(
            record("a", started = 1_000L),
            record("b", started = 3_000L),
            record("gagal", started = 5_000L, status = BuildStatus.FAILED, names = emptyList()),
            record("kosong", started = 4_000L, names = emptyList()),
        )
        val history = ArtifactCatalog.history(listOf(file()), records)
        assertEquals(listOf("b", "a"), history.map { it.record.id })
    }

    @Test
    fun `artefak di riwayat tersedia hanya bila file ada dan build itu pemiliknya`() {
        val records = listOf(
            record("lama", started = 1_000L, names = listOf("app-debug.apk")),
            record("baru", started = 9_000L, names = listOf("app-debug.apk")),
        )
        val history = ArtifactCatalog.history(listOf(file()), records)
        val byId = history.associateBy { it.record.id }
        assertTrue(byId.getValue("baru").artifacts.single().available)
        assertFalse("build lama sudah digantikan", byId.getValue("lama").artifacts.single().available)
    }

    @Test
    fun `artefak di riwayat tidak tersedia bila file sudah dihapus`() {
        val history = ArtifactCatalog.history(emptyList(), listOf(record("r1")))
        assertFalse(history.single().artifacts.single().available)
    }

    // --- aturan pasang / bagikan ---

    @Test
    fun `APK valid dan bertanda tangan boleh dipasang`() {
        val e = entry("app-debug.apk")
        assertNull(e.installBlock)
        assertTrue(e.canInstall)
    }

    @Test
    fun `alasan pemasangan diblokir`() {
        assertEquals(InstallBlock.NOT_APK, entry("app-release.aab").installBlock)
        assertEquals(InstallBlock.UNSIGNED, entry("app-release-unsigned.apk").installBlock)
        assertEquals(InstallBlock.INVALID_FILE, entry(validation = ArtifactValidation.INVALID).installBlock)
        assertEquals(InstallBlock.NOT_CHECKED, entry(validation = ArtifactValidation.NOT_CHECKED).installBlock)
        assertEquals(InstallBlock.MISSING, entry(validation = ArtifactValidation.MISSING).installBlock)
        assertFalse(entry("app-release-unsigned.apk").canInstall)
    }

    @Test
    fun `file hilang diblokir walau berupa AAB`() {
        assertEquals(InstallBlock.MISSING, entry("x.aab", ArtifactValidation.MISSING).installBlock)
    }

    @Test
    fun `bagikan boleh untuk AAB dan APK belum bertanda tangan tetapi tidak untuk file rusak atau hilang`() {
        assertTrue(entry("app-release.aab").canShare)
        assertTrue(entry("app-release-unsigned.apk").canShare)
        assertTrue(entry(validation = ArtifactValidation.NOT_CHECKED).canShare)
        assertFalse(entry(validation = ArtifactValidation.INVALID).canShare)
        assertFalse(entry(validation = ArtifactValidation.MISSING).canShare)
    }

    @Test
    fun `ringkasan menjumlahkan ukuran nyata`() {
        val snapshot = ArtifactSnapshot(
            entries = listOf(
                ArtifactEntry(file(size = 100L), null, null, ArtifactValidation.VALID),
                ArtifactEntry(file(name = "b.apk", size = 250L), null, null, ArtifactValidation.VALID),
            ),
            history = emptyList(),
        )
        assertEquals(ArtifactSummary(2, 350L), snapshot.summary)
    }

    // --- aturan nama ---

    @Test
    fun `jenis dari ekstensi`() {
        assertEquals(ArtifactKind.APK, ArtifactRules.kindOf("a.apk"))
        assertEquals(ArtifactKind.APK, ArtifactRules.kindOf("A.APK"))
        assertEquals(ArtifactKind.AAB, ArtifactRules.kindOf("a.aab"))
        assertNull(ArtifactRules.kindOf("a.zip"))
        assertNull(ArtifactRules.kindOf("apk"))
    }

    @Test
    fun `mime tiap jenis`() {
        assertEquals("application/vnd.android.package-archive", ArtifactRules.mimeType(ArtifactKind.APK))
        assertEquals("application/octet-stream", ArtifactRules.mimeType(ArtifactKind.AAB))
    }

    @Test
    fun `id proyek aman`() {
        assertTrue(ArtifactRules.isSafeProjectId("proyek_1-a"))
        assertFalse(ArtifactRules.isSafeProjectId(""))
        assertFalse(ArtifactRules.isSafeProjectId("../x"))
        assertFalse(ArtifactRules.isSafeProjectId("a b"))
        assertFalse(ArtifactRules.isSafeProjectId("a/b"))
        assertFalse(ArtifactRules.isSafeProjectId("é"))
        assertFalse(ArtifactRules.isSafeProjectId("a".repeat(81)))
    }

    @Test
    fun `nama file ekspor aman dan tetap berekstensi asli`() {
        assertEquals("Proyek_Satu-app-debug.apk", ArtifactRules.exportFileName("Proyek Satu", "app-debug.apk"))
        assertEquals("app-debug.apk", ArtifactRules.exportFileName(null, "app-debug.apk"))
        assertEquals("app-debug.apk", ArtifactRules.exportFileName("   ", "app-debug.apk"))
        // Tidak menggandakan nama proyek bila nama artefak sudah diawali nama itu.
        assertEquals("MyApp-release.aab", ArtifactRules.exportFileName("MyApp", "MyApp-release.aab"))
    }

    @Test
    fun `nama file ekspor tidak membawa path atau karakter berbahaya`() {
        val name = ArtifactRules.exportFileName("../../etc/passwd; rm -rf", "app.apk")
        assertTrue(name.endsWith(".apk"))
        assertTrue(name.none { it == '/' || it == '\\' || it == ' ' || it == ';' })
        assertFalse(name.startsWith("."))
    }

    @Test
    fun `nama file ekspor dipotong tetapi ekstensi tetap ada`() {
        val name = ArtifactRules.exportFileName("P".repeat(300), "app-debug.apk")
        assertTrue(name.length <= 120)
        assertTrue(name.endsWith(".apk"))
    }

    @Test
    fun `nama proyek unicode menjadi garis bawah dan nama kosong diberi cadangan`() {
        assertEquals("app.apk", ArtifactRules.exportFileName("日本語", "app.apk"))
        assertEquals("artefak.apk", ArtifactRules.exportFileName(null, ".apk"))
    }
}

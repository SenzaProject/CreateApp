package com.senzaproject.createapp.domain.apk

import com.senzaproject.createapp.domain.builder.ArtifactKind
import com.senzaproject.createapp.domain.builder.ArtifactValidation
import com.senzaproject.createapp.domain.builder.BuildRecord
import com.senzaproject.createapp.domain.builder.BuildStatus
import com.senzaproject.createapp.domain.builder.BuildType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkDomainTest {

    private fun artifact(
        name: String,
        project: String = "p1",
        modified: Long = 1_000L,
        kind: ArtifactKind = ArtifactNames.kindOf(name)!!,
        size: Long = 10L,
    ) = ManagedArtifact(project, name, kind, size, modified, "content://x/$project/$name")

    private fun metadata(signers: Int?) = ApkMetadata("com.x", "X", "1.0", 1L, 26, 35, signers)

    private fun details(
        validation: ArtifactValidation = ArtifactValidation.VALID,
        signers: Int? = 1,
        withApk: Boolean = true,
    ) = ArtifactDetails(validation, "abc", if (withApk) metadata(signers) else null)

    // --- ArtifactNames / ManagedArtifact ---

    @Test
    fun `jenis artefak dari ekstensi`() {
        assertEquals(ArtifactKind.APK, ArtifactNames.kindOf("app-debug.apk"))
        assertEquals(ArtifactKind.APK, ArtifactNames.kindOf("APP.APK"))
        assertEquals(ArtifactKind.AAB, ArtifactNames.kindOf("app-release.aab"))
        assertNull(ArtifactNames.kindOf("app-debug.apk.part"))
        assertNull(ArtifactNames.kindOf("catatan.txt"))
        assertNull(ArtifactNames.kindOf("apk"))
    }

    @Test
    fun `kunci dan path artefak`() {
        val a = artifact("app-debug.apk")
        assertEquals("p1/app-debug.apk", a.key)
        assertEquals("/storage/emulated/0/B/out/p1/app-debug.apk", a.path("/storage/emulated/0/B"))
    }

    @Test
    fun `petunjuk belum ditandatangani hanya untuk APK`() {
        assertTrue(artifact("app-release-unsigned.apk").looksUnsigned)
        assertFalse(artifact("app-debug.apk").looksUnsigned)
        assertFalse(artifact("app-release-unsigned.aab").looksUnsigned)
    }

    // --- InstallPolicy ---

    @Test
    fun `APK debug valid dan bertanda tangan boleh dipasang`() {
        assertNull(InstallPolicy.check(artifact("app-debug.apk"), details(), canRequestInstalls = true))
    }

    @Test
    fun `AAB tidak bisa dipasang`() {
        assertEquals(InstallBlock.NOT_APK, InstallPolicy.check(artifact("app-release.aab"), null, true))
    }

    @Test
    fun `file tidak valid diblokir dan tidak bisa dipasang`() {
        val d = details(validation = ArtifactValidation.INVALID)
        assertEquals(InstallBlock.INVALID_FILE, InstallPolicy.check(artifact("app-debug.apk"), d, true))
    }

    @Test
    fun `tanpa penanda tangan diblokir walau namanya tidak mengandung unsigned`() {
        val d = details(signers = 0)
        assertEquals(InstallBlock.UNSIGNED, InstallPolicy.check(artifact("app-release.apk"), d, true))
    }

    @Test
    fun `penanda tangan tidak dapat ditentukan maka nama file jadi petunjuk satu-satunya`() {
        val unknown = details(signers = null)
        assertNull("jangan memblokir hanya karena tebakan", InstallPolicy.check(artifact("app-debug.apk"), unknown, true))
        assertEquals(
            InstallBlock.UNSIGNED,
            InstallPolicy.check(artifact("app-release-unsigned.apk"), unknown, true),
        )
        val noApk = details(withApk = false)
        assertEquals(
            InstallBlock.UNSIGNED,
            InstallPolicy.check(artifact("app-release-unsigned.apk"), noApk, true),
        )
    }

    @Test
    fun `penanda tangan terbaca mengalahkan nama file`() {
        val signed = details(signers = 1)
        assertNull(InstallPolicy.check(artifact("app-release-unsigned.apk"), signed, true))
    }

    @Test
    fun `izin pemasangan diperiksa paling akhir`() {
        assertEquals(
            InstallBlock.NEED_INSTALL_PERMISSION,
            InstallPolicy.check(artifact("app-debug.apk"), details(), canRequestInstalls = false),
        )
        // Alasan yang lebih mendasar didahulukan daripada izin.
        assertEquals(
            InstallBlock.UNSIGNED,
            InstallPolicy.check(artifact("app-release.apk"), details(signers = 0), canRequestInstalls = false),
        )
    }

    @Test
    fun `belum diperiksa maka hanya jenis dan nama yang dipakai`() {
        assertNull(InstallPolicy.check(artifact("app-debug.apk"), null, true))
    }

    // --- ArtifactListing ---

    @Test
    fun `pengelompokan per proyek terbaru dulu`() {
        val list = listOf(
            artifact("a-debug.apk", project = "p1", modified = 100L),
            artifact("a-release.apk", project = "p1", modified = 300L),
            artifact("b.apk", project = "p2", modified = 200L),
            artifact("c.aab", project = "p3", modified = 500L),
        )
        val groups = ArtifactListing.group(list)
        assertEquals(listOf("p3", "p1", "p2"), groups.map { it.projectId })
        assertEquals(listOf("a-release.apk", "a-debug.apk"), groups[1].artifacts.map { it.name })
    }

    @Test
    fun `waktu sama diurutkan menurut nama dan total ukuran dijumlahkan`() {
        val list = listOf(
            artifact("b.apk", modified = 100L, size = 5L),
            artifact("a.apk", modified = 100L, size = 7L),
        )
        val group = ArtifactListing.group(list).single()
        assertEquals(listOf("a.apk", "b.apk"), group.artifacts.map { it.name })
        assertEquals(12L, group.totalBytes)
    }

    @Test
    fun `daftar kosong tetap kosong`() {
        assertTrue(ArtifactListing.group(emptyList()).isEmpty())
    }

    // --- BuildHistoryLinker ---

    private fun record(
        names: List<String>,
        project: String = "p1",
        start: Long = 10_000L,
        duration: Long = 60_000L,
    ) = BuildRecord(
        id = "r",
        projectId = project,
        projectName = "P",
        type = BuildType.DEBUG,
        status = BuildStatus.SUCCESS,
        startedAtMillis = start,
        durationMillis = duration,
        tasksSeen = 1,
        errorCount = 0,
        warningCount = 0,
        artifactNames = names,
        failureCode = null,
        failureDetail = null,
    )

    @Test
    fun `file yang masih ada dari build itu tersedia`() {
        val entry = BuildHistoryLinker.link(
            listOf(record(listOf("app-debug.apk"))),
            listOf(artifact("app-debug.apk", modified = 50_000L)),
        ).single()
        assertEquals(ArtifactAvailability.AVAILABLE, entry.artifacts.single().availability)
    }

    @Test
    fun `file yang hilang ditandai dihapus`() {
        val entry = BuildHistoryLinker.link(listOf(record(listOf("app-debug.apk"))), emptyList()).single()
        assertEquals(ArtifactAvailability.DELETED, entry.artifacts.single().availability)
    }

    @Test
    fun `file ditulis ulang build yang lebih baru ditandai diganti`() {
        val entry = BuildHistoryLinker.link(
            listOf(record(listOf("app-debug.apk"))),
            listOf(artifact("app-debug.apk", modified = 500_000L)),
        ).single()
        assertEquals(ArtifactAvailability.REPLACED, entry.artifacts.single().availability)
    }

    @Test
    fun `toleransi jam dan waktu ubah tidak diketahui tidak dianggap diganti`() {
        val rec = record(listOf("a.apk"), start = 10_000L, duration = 60_000L)
        val atEdge = artifact("a.apk", modified = 70_000L + BuildHistoryLinker.TOLERANCE_MILLIS)
        val unknown = artifact("a.apk", modified = 0L)
        assertEquals(
            ArtifactAvailability.AVAILABLE,
            BuildHistoryLinker.link(listOf(rec), listOf(atEdge)).single().artifacts.single().availability,
        )
        assertEquals(
            ArtifactAvailability.AVAILABLE,
            BuildHistoryLinker.link(listOf(rec), listOf(unknown)).single().artifacts.single().availability,
        )
    }

    @Test
    fun `nama sama di proyek lain tidak tertukar dan build gagal tanpa artefak`() {
        val entries = BuildHistoryLinker.link(
            listOf(record(listOf("app-debug.apk"), project = "p1"), record(emptyList(), project = "p1")),
            listOf(artifact("app-debug.apk", project = "p2", modified = 50_000L)),
        )
        assertEquals(ArtifactAvailability.DELETED, entries[0].artifacts.single().availability)
        assertTrue(entries[1].artifacts.isEmpty())
    }
}

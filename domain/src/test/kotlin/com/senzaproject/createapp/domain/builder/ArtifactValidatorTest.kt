package com.senzaproject.createapp.domain.builder

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtifactValidatorTest {

    private fun zip(vararg names: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for (name in names) {
                zip.putNextEntry(ZipEntry(name))
                zip.write("isi $name".toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun validate(kind: ArtifactKind, bytes: ByteArray): ArtifactValidation =
        ArtifactValidator.validate(kind, ByteArrayInputStream(bytes))

    @Test
    fun `APK dengan manifest valid`() {
        assertEquals(
            ArtifactValidation.VALID,
            validate(ArtifactKind.APK, zip("AndroidManifest.xml", "classes.dex", "resources.arsc")),
        )
    }

    @Test
    fun `APK tanpa manifest tidak valid`() {
        assertEquals(ArtifactValidation.INVALID, validate(ArtifactKind.APK, zip("classes.dex")))
    }

    @Test
    fun `manifest di folder lain tidak dihitung untuk APK`() {
        assertEquals(ArtifactValidation.INVALID, validate(ArtifactKind.APK, zip("base/manifest/AndroidManifest.xml")))
    }

    @Test
    fun `bukan ZIP tidak valid`() {
        assertEquals(ArtifactValidation.INVALID, validate(ArtifactKind.APK, "ini bukan zip".toByteArray()))
        assertEquals(ArtifactValidation.INVALID, validate(ArtifactKind.APK, ByteArray(0)))
        assertEquals(ArtifactValidation.INVALID, validate(ArtifactKind.AAB, "DEBUGAPK".toByteArray()))
    }

    @Test
    fun `ZIP terpotong tidak valid walau manifest ada di awal file`() {
        val full = zip("AndroidManifest.xml", "classes.dex")
        for (keep in listOf(full.size / 3, full.size / 2, full.size - 30, full.size - 1)) {
            assertEquals("dipotong di $keep", ArtifactValidation.INVALID, validate(ArtifactKind.APK, full.copyOf(keep)))
        }
    }

    @Test
    fun `sisa data di belakang catatan akhir ZIP ditolak`() {
        val full = zip("AndroidManifest.xml", "classes.dex")
        assertEquals(ArtifactValidation.INVALID, validate(ArtifactKind.APK, full + byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `ZIP kosong tanpa isi tidak valid`() {
        val empty = zip()
        assertEquals(ArtifactValidation.INVALID, validate(ArtifactKind.APK, empty))
    }

    @Test
    fun `APK berukuran besar dengan banyak entri tetap valid`() {
        val names = (1..300).map { "res/raw/file_$it.bin" } + listOf("AndroidManifest.xml", "classes.dex")
        assertEquals(ArtifactValidation.VALID, validate(ArtifactKind.APK, zip(*names.toTypedArray())))
    }

    @Test
    fun `AAB butuh manifest base dan BundleConfig`() {
        assertEquals(
            ArtifactValidation.VALID,
            validate(ArtifactKind.AAB, zip("base/manifest/AndroidManifest.xml", "BundleConfig.pb")),
        )
        assertEquals(
            ArtifactValidation.INVALID,
            validate(ArtifactKind.AAB, zip("base/manifest/AndroidManifest.xml")),
        )
        assertEquals(ArtifactValidation.INVALID, validate(ArtifactKind.AAB, zip("BundleConfig.pb")))
    }

    @Test
    fun `APK release tanpa tanda tangan dikenali dari namanya`() {
        assertEquals(true, BuildArtifact(ArtifactKind.APK, "app-release-unsigned.apk", 1L).isUnsigned)
        assertEquals(false, BuildArtifact(ArtifactKind.APK, "app-release.apk", 1L).isUnsigned)
        assertEquals(false, BuildArtifact(ArtifactKind.AAB, "app-release-unsigned.aab", 1L).isUnsigned)
    }
}

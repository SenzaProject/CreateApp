package com.senzaproject.createapp.domain.builder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HelperProtocolTest {

    @Test
    fun `baris log biasa bukan penanda`() {
        assertNull(HelperProtocol.parse("> Task :app:compileDebugKotlin"))
        assertNull(HelperProtocol.parse(""))
        assertNull(HelperProtocol.parse(" @@CA@done=1"))
    }

    @Test
    fun `penanda dasar dikenali`() {
        assertEquals(HelperMessage.Version(1), HelperProtocol.parse("@@CA@helper=1"))
        assertEquals(HelperMessage.Phase(BuildPhase.BUILD), HelperProtocol.parse("@@CA@phase=build"))
        assertEquals(HelperMessage.Phase(BuildPhase.COLLECT), HelperProtocol.parse("@@CA@phase=collect"))
        assertEquals(HelperMessage.Exit(7), HelperProtocol.parse("@@CA@exit=7"))
        assertEquals(HelperMessage.Done, HelperProtocol.parse("@@CA@done=1"))
        assertEquals(HelperMessage.Cancelled, HelperProtocol.parse("@@CA@cancelled=1"))
        assertEquals(HelperMessage.CleanOk, HelperProtocol.parse("@@CA@clean=ok"))
        assertEquals(HelperMessage.CancelResult(true), HelperProtocol.parse("@@CA@cancel=killed"))
        assertEquals(HelperMessage.CancelResult(false), HelperProtocol.parse("@@CA@cancel=none"))
        assertEquals(HelperMessage.LogFile("/x/y.log"), HelperProtocol.parse("@@CA@log=/x/y.log"))
    }

    @Test
    fun `penanda dengan nilai rusak diabaikan`() {
        assertNull(HelperProtocol.parse("@@CA@exit=abc"))
        assertNull(HelperProtocol.parse("@@CA@phase=ngawur"))
        assertNull(HelperProtocol.parse("@@CA@done=0"))
        assertNull(HelperProtocol.parse("@@CA@tidakada=1"))
        assertNull(HelperProtocol.parse("@@CA@"))
        assertNull(HelperProtocol.parse("@@CA@done"))
    }

    @Test
    fun `artefak dikenali beserta ukuran dan nama`() {
        assertEquals(
            HelperMessage.Artifact(ArtifactKind.APK, 8L, "app-debug.apk"),
            HelperProtocol.parse("@@CA@artifact=APK|8|app-debug.apk"),
        )
        assertEquals(
            HelperMessage.Artifact(ArtifactKind.AAB, 10L, "app-release.aab"),
            HelperProtocol.parse("@@CA@artifact=AAB|10|app-release.aab"),
        )
    }

    @Test
    fun `nama artefak boleh berisi pemisah`() {
        assertEquals(
            HelperMessage.Artifact(ArtifactKind.APK, 5L, "a|b.apk"),
            HelperProtocol.parse("@@CA@artifact=APK|5|a|b.apk"),
        )
    }

    @Test
    fun `artefak dengan nama tidak aman atau format rusak ditolak`() {
        assertNull(HelperProtocol.parse("@@CA@artifact=APK|8|../x.apk"))
        assertNull(HelperProtocol.parse("@@CA@artifact=APK|8|a/b.apk"))
        assertNull(HelperProtocol.parse("@@CA@artifact=APK|8|a\\b.apk"))
        assertNull(HelperProtocol.parse("@@CA@artifact=APK|8|.."))
        assertNull(HelperProtocol.parse("@@CA@artifact=APK|8|"))
        assertNull(HelperProtocol.parse("@@CA@artifact=EXE|8|a.apk"))
        assertNull(HelperProtocol.parse("@@CA@artifact=APK|x|a.apk"))
        assertNull(HelperProtocol.parse("@@CA@artifact=APK|-1|a.apk"))
        assertNull(HelperProtocol.parse("@@CA@artifact=APK8a.apk"))
    }

    @Test
    fun `kode error dikenali dan yang asing menjadi UNKNOWN`() {
        assertEquals(HelperMessage.Error(HelperError.NO_JAVA), HelperProtocol.parse("@@CA@error=NO_JAVA"))
        assertEquals(HelperMessage.Error(HelperError.ALREADY_RUNNING), HelperProtocol.parse("@@CA@error=ALREADY_RUNNING"))
        assertEquals(HelperMessage.Error(HelperError.UNKNOWN), HelperProtocol.parse("@@CA@error=SESUATU"))
        assertEquals(HelperMessage.Error(HelperError.UNKNOWN), HelperProtocol.parse("@@CA@error=UNKNOWN"))
    }

    @Test
    fun `butir pemeriksaan lingkungan dikenali`() {
        assertEquals(
            HelperMessage.Check("java", CheckLevel.OK, "openjdk version \"17\""),
            HelperProtocol.parse("@@CA@check=java|OK|openjdk version \"17\""),
        )
        assertEquals(
            HelperMessage.Check("aapt2", CheckLevel.WARN, "a|b"),
            HelperProtocol.parse("@@CA@check=aapt2|WARN|a|b"),
        )
        assertNull(HelperProtocol.parse("@@CA@check=java|BAGUS|x"))
        assertNull(HelperProtocol.parse("@@CA@check=java"))
    }

    @Test
    fun `laporan lingkungan siap hanya bila komponen wajib tidak MISSING`() {
        val ok = ToolchainReport(
            listOf(
                ToolchainItem("java", CheckLevel.OK, ""),
                ToolchainItem("aapt2", CheckLevel.WARN, "arm"),
            ),
        )
        assertEquals(true, ok.isReady)
        assertEquals(true, ok.hasWarnings)

        val missing = ToolchainReport(listOf(ToolchainItem("java", CheckLevel.MISSING, "")))
        assertEquals(false, missing.isReady)

        // aapt2 bukan komponen wajib; MISSING pada butir yang tidak wajib tidak memblokir.
        val optionalMissing = ToolchainReport(listOf(ToolchainItem("arch", CheckLevel.MISSING, "")))
        assertEquals(true, optionalMissing.isReady)
        assertEquals(false, ToolchainReport(emptyList()).isReady)
    }
}

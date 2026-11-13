package com.senzaproject.createapp.domain.builder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildCommandFactoryTest {

    private val factory = BuildCommandFactory("QUJDREVG")

    @Test
    fun `perintah memasang helper secara atomik lalu menjalankannya`() {
        val command = factory.cancel()
        assertTrue(command.startsWith("mkdir -p ~/.createapp && printf '%s' 'QUJDREVG' | base64 -d > ~/.createapp/build.sh.new"))
        assertTrue(command.contains("mv -f ~/.createapp/build.sh.new ~/.createapp/build.sh"))
        assertTrue(command.endsWith("sh ~/.createapp/build.sh cancel"))
    }

    @Test
    fun `argumen build dikutip dan task berasal dari daftar tetap`() {
        val command = factory.build(
            sourcePath = "/storage/emulated/0/My Build/src/p1",
            projectId = "p1",
            type = BuildType.RELEASE,
            outputRootPath = "/storage/emulated/0/My Build/out",
        )
        assertTrue(
            command.endsWith(
                "sh ~/.createapp/build.sh build --source '/storage/emulated/0/My Build/src/p1' " +
                    "--name 'p1' --task assembleRelease --out '/storage/emulated/0/My Build/out'",
            ),
        )
    }

    @Test
    fun `tanda kutip di path tidak bisa keluar dari argumen`() {
        val command = factory.check("/sdcard/it's \$(x)")
        assertTrue(command.endsWith("check --source '/sdcard/it'\\''s \$(x)'"))
    }

    @Test
    fun `task tiap jenis build`() {
        assertEquals("assembleDebug", BuildType.DEBUG.gradleTask)
        assertEquals("assembleRelease", BuildType.RELEASE.gradleTask)
        assertEquals("bundleRelease", BuildType.BUNDLE.gradleTask)
        assertEquals(ArtifactKind.AAB, BuildType.BUNDLE.artifactKind)
    }

    @Test
    fun `check tanpa sumber dan clean`() {
        assertTrue(factory.check(null).endsWith("sh ~/.createapp/build.sh check"))
        assertTrue(factory.clean().endsWith("sh ~/.createapp/build.sh clean"))
    }

    @Test
    fun `helper kosong atau bukan Base64 ditolak`() {
        assertThrows(IllegalArgumentException::class.java) { BuildCommandFactory("") }
        assertThrows(IllegalArgumentException::class.java) { BuildCommandFactory("abc'; rm -rf / #") }
        assertFalse(factory.check(null).contains("\n"))
    }
}

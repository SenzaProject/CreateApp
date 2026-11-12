package com.senzaproject.createapp.domain.builder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedStoragePathTest {

    private val base = "content://com.android.externalstorage.documents/tree/"

    @Test
    fun `folder di penyimpanan internal utama diubah menjadi path`() {
        assertEquals(
            "/storage/emulated/0/Download/CreateAppBuild",
            SharedStoragePath.fromTreeUri(base + "primary%3ADownload%2FCreateAppBuild"),
        )
    }

    @Test
    fun `bagian document di belakang URI diabaikan`() {
        assertEquals(
            "/storage/emulated/0/Download/CreateAppBuild",
            SharedStoragePath.fromTreeUri(
                base + "primary%3ADownload%2FCreateAppBuild/document/primary%3ADownload%2FCreateAppBuild",
            ),
        )
    }

    @Test
    fun `akar penyimpanan internal`() {
        assertEquals("/storage/emulated/0", SharedStoragePath.fromTreeUri(base + "primary%3A"))
    }

    @Test
    fun `kartu SD dan volume lain ditolak`() {
        assertNull(SharedStoragePath.fromTreeUri(base + "1234-ABCD%3AFoo"))
    }

    @Test
    fun `provider lain ditolak`() {
        assertNull(SharedStoragePath.fromTreeUri("content://com.android.providers.downloads.documents/tree/downloads"))
        assertNull(SharedStoragePath.fromTreeUri("file:///storage/emulated/0/Download"))
    }

    @Test
    fun `path traversal ditolak`() {
        assertNull(SharedStoragePath.fromTreeUri(base + "primary%3A..%2Fsecret"))
        assertNull(SharedStoragePath.fromTreeUri(base + "primary%3AA%2F..%2FB"))
        assertNull(SharedStoragePath.fromTreeUri(base + "primary%3AA%2F%2FB"))
    }

    @Test
    fun `spasi dan unicode didekode dengan benar`() {
        assertEquals(
            "/storage/emulated/0/Download/My Build/caf\u00e9",
            SharedStoragePath.fromTreeUri(base + "primary%3ADownload%2FMy%20Build%2Fcaf%C3%A9"),
        )
    }

    @Test
    fun `tanda plus tidak diubah menjadi spasi`() {
        assertEquals("/storage/emulated/0/C++", SharedStoragePath.fromTreeUri(base + "primary%3AC%2B%2B"))
        assertEquals("/storage/emulated/0/C+", SharedStoragePath.fromTreeUri(base + "primary%3AC+"))
    }

    @Test
    fun `persen tidak valid ditolak`() {
        assertNull(SharedStoragePath.fromTreeUri(base + "primary%3AA%G1"))
        assertNull(SharedStoragePath.fromTreeUri(base + "primary%3AA%3"))
    }

    @Test
    fun `tata letak folder bersama`() {
        assertEquals("/r/src/p1", WorkspaceLayout.sourcePath("/r", "p1"))
        assertEquals("/r/out", WorkspaceLayout.outputRootPath("/r"))
        assertEquals("/r/out/p1", WorkspaceLayout.outputPath("/r", "p1"))
    }

    @Test
    fun `path log langsung`() {
        assertEquals("/r/logs/p1.log", WorkspaceLayout.liveLogPath("/r", "p1"))
        assertEquals("p1.log", WorkspaceLayout.liveLogName("p1"))
    }

    @Test
    fun `id dokumen dari path penyimpanan internal`() {
        assertEquals("primary:", SharedStoragePath.documentIdFor("/storage/emulated/0"))
        assertEquals("primary:Download/B/out/p1", SharedStoragePath.documentIdFor("/storage/emulated/0/Download/B/out/p1"))
        assertEquals("primary:A B/C+", SharedStoragePath.documentIdFor("/storage/emulated/0/A B/C+"))
    }

    @Test
    fun `id dokumen ditolak untuk path di luar penyimpanan internal atau berbahaya`() {
        assertNull(SharedStoragePath.documentIdFor("/sdcard/Download"))
        assertNull(SharedStoragePath.documentIdFor("/storage/emulated/01/x"))
        assertNull(SharedStoragePath.documentIdFor("/storage/emulated/0/../etc"))
        assertNull(SharedStoragePath.documentIdFor("/storage/emulated/0/a//b"))
    }

    @Test
    fun `id dokumen adalah kebalikan dari path pohon`() {
        val path = SharedStoragePath.fromTreeUri(base + "primary%3ADownload%2FCreateAppBuild")
        assertEquals("primary:Download/CreateAppBuild", SharedStoragePath.documentIdFor(path!!))
    }
}

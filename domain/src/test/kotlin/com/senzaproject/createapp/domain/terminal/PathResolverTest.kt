package com.senzaproject.createapp.domain.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class PathResolverTest {

    private val home = "/data/home"

    @Test
    fun `path absolut dipertahankan dan dinormalisasi`() {
        assertEquals("/b/c", PathResolver.resolve("/a", "/b/c", home))
        assertEquals("/b/c", PathResolver.resolve("/a", "/b//c/", home))
    }

    @Test
    fun `path relatif digabung dengan direktori kerja`() {
        assertEquals("/a/b/c", PathResolver.resolve("/a/b", "c", home))
        assertEquals("/a/b/c/d", PathResolver.resolve("/a/b", "c/d", home))
    }

    @Test
    fun `titik dan dua titik ditangani`() {
        assertEquals("/a", PathResolver.resolve("/a/b", "..", home))
        assertEquals("/", PathResolver.resolve("/a/b", "../..", home))
        assertEquals("/a/b", PathResolver.resolve("/a/b", ".", home))
        assertEquals("/a/b/c", PathResolver.resolve("/a", "./b//c/.", home))
        assertEquals("/x", PathResolver.resolve("/a/b", "../../x", home))
    }

    @Test
    fun `dua titik tidak bisa naik melewati akar`() {
        assertEquals("/", PathResolver.resolve("/", "..", home))
        assertEquals("/x", PathResolver.resolve("/a", "../../../x", home))
    }

    @Test
    fun `tilde menjadi direktori awal`() {
        assertEquals("/data/home", PathResolver.resolve("/a", "~", home))
        assertEquals("/data/home/src", PathResolver.resolve("/a", "~/src", home))
        assertEquals("/data/home/src", PathResolver.resolve("/a", "~/src", "/data/home/"))
    }

    @Test
    fun `tilde dengan nama pengguna diperlakukan sebagai nama relatif biasa`() {
        assertEquals("/a/~user", PathResolver.resolve("/a", "~user", home))
    }

    @Test
    fun `direktori kerja akar digabung dengan benar`() {
        assertEquals("/x", PathResolver.resolve("/", "x", home))
    }
}

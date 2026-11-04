package com.senzaproject.createapp.domain.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectFilterTest {

    private val beta = Project("1", "Beta", "com.x.beta", lastModifiedMillis = 100L, sizeBytes = 10L)
    private val alpha = Project("2", "alpha", "com.x.alpha", lastModifiedMillis = 300L, sizeBytes = 20L, isFavorite = true)
    private val gamma = Project("3", "Gamma", "org.y.gamma", lastModifiedMillis = 200L, sizeBytes = 30L)
    private val all = listOf(beta, alpha, gamma)

    private fun names(list: List<Project>) = list.map { it.name }

    @Test
    fun `ALL diurutkan berdasarkan nama tanpa membedakan huruf besar kecil`() {
        assertEquals(listOf("alpha", "Beta", "Gamma"), names(filterProjects(all, "", ProjectFilter.ALL)))
    }

    @Test
    fun `RECENT mengurutkan dari perubahan terbaru`() {
        assertEquals(listOf("alpha", "Gamma", "Beta"), names(filterProjects(all, "", ProjectFilter.RECENT)))
    }

    @Test
    fun `FAVORITES hanya memuat proyek favorit`() {
        assertEquals(listOf("alpha"), names(filterProjects(all, "", ProjectFilter.FAVORITES)))
    }

    @Test
    fun `query mencocokkan nama tanpa membedakan huruf besar kecil`() {
        assertEquals(listOf("Gamma"), names(filterProjects(all, "gAm", ProjectFilter.ALL)))
    }

    @Test
    fun `query juga mencocokkan package`() {
        assertEquals(listOf("alpha", "Beta"), names(filterProjects(all, "com.x", ProjectFilter.ALL)))
    }

    @Test
    fun `query hanya spasi dianggap kosong`() {
        assertEquals(3, filterProjects(all, "   ", ProjectFilter.ALL).size)
    }

    @Test
    fun `query tanpa hasil mengembalikan daftar kosong`() {
        assertTrue(filterProjects(all, "tidak-ada", ProjectFilter.ALL).isEmpty())
    }

    @Test
    fun `query dan filter digabung`() {
        assertTrue(filterProjects(all, "beta", ProjectFilter.FAVORITES).isEmpty())
    }
}

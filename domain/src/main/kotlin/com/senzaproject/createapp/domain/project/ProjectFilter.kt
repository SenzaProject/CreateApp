package com.senzaproject.createapp.domain.project

/** Tab filter pada Project Manager. */
enum class ProjectFilter { ALL, RECENT, FAVORITES }

/**
 * Menyaring dan mengurutkan proyek.
 *
 * - [query]: cocok (tanpa membedakan huruf besar/kecil) dengan nama atau package; spasi tepi diabaikan.
 * - ALL: urut nama; RECENT: terbaru diubah lebih dulu; FAVORITES: hanya favorit, urut nama.
 */
fun filterProjects(
    projects: List<Project>,
    query: String,
    filter: ProjectFilter,
): List<Project> {
    val normalized = query.trim()
    val matched = if (normalized.isEmpty()) {
        projects
    } else {
        projects.filter {
            it.name.contains(normalized, ignoreCase = true) ||
                it.packageName.contains(normalized, ignoreCase = true)
        }
    }
    return when (filter) {
        ProjectFilter.ALL -> matched.sortedBy { it.name.lowercase() }
        ProjectFilter.RECENT -> matched.sortedByDescending { it.lastModifiedMillis }
        ProjectFilter.FAVORITES -> matched.filter { it.isFavorite }.sortedBy { it.name.lowercase() }
    }
}

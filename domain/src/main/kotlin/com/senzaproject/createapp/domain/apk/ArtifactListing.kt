package com.senzaproject.createapp.domain.apk

import com.senzaproject.createapp.domain.builder.BuildRecord

/** Artefak satu proyek, terbaru lebih dulu. */
data class ProjectArtifacts(val projectId: String, val artifacts: List<ManagedArtifact>) {
    val totalBytes: Long get() = artifacts.sumOf { it.sizeBytes }
}

object ArtifactListing {

    /**
     * Mengelompokkan per proyek. Di dalam proyek: terbaru dulu, lalu nama. Antarproyek: proyek yang
     * artefak terbarunya paling baru lebih dulu, lalu id.
     */
    fun group(artifacts: List<ManagedArtifact>): List<ProjectArtifacts> {
        val sortedWithin = Comparator<ManagedArtifact> { a, b ->
            val byTime = b.modifiedMillis.compareTo(a.modifiedMillis)
            if (byTime != 0) byTime else a.name.compareTo(b.name)
        }
        val groups = artifacts.groupBy { it.projectId }.map { (id, list) ->
            ProjectArtifacts(id, list.sortedWith(sortedWithin))
        }
        return groups.sortedWith { a, b ->
            val newestA = a.artifacts.first().modifiedMillis
            val newestB = b.artifacts.first().modifiedMillis
            val byTime = newestB.compareTo(newestA)
            if (byTime != 0) byTime else a.projectId.compareTo(b.projectId)
        }
    }
}

/** Keadaan sebuah file hasil build tertentu saat ini. */
enum class ArtifactAvailability {
    /** File masih ada dan berasal dari build itu. */
    AVAILABLE,

    /** File dengan nama sama ada, tetapi ditulis oleh build yang lebih baru. */
    REPLACED,

    /** File tidak ada lagi di folder keluaran. */
    DELETED,
}

data class HistoryArtifact(val name: String, val availability: ArtifactAvailability)

/** Satu catatan riwayat build beserta keadaan terkini file hasilnya. */
data class HistoryEntry(val record: BuildRecord, val artifacts: List<HistoryArtifact>)

/** Menghubungkan riwayat build (apa yang pernah dihasilkan) dengan isi folder (apa yang masih ada). */
object BuildHistoryLinker {

    /** Selisih jam yang ditoleransi; file disalin helper di dalam jendela waktu build. */
    const val TOLERANCE_MILLIS = 5_000L

    fun link(records: List<BuildRecord>, artifacts: List<ManagedArtifact>): List<HistoryEntry> {
        val byKey = artifacts.associateBy { it.key }
        return records.map { record ->
            val finished = record.startedAtMillis + record.durationMillis
            HistoryEntry(
                record = record,
                artifacts = record.artifactNames.map { name ->
                    val current = byKey["${record.projectId}/$name"]
                    val availability = when {
                        current == null -> ArtifactAvailability.DELETED
                        current.modifiedMillis > 0L && current.modifiedMillis > finished + TOLERANCE_MILLIS ->
                            ArtifactAvailability.REPLACED
                        else -> ArtifactAvailability.AVAILABLE
                    }
                    HistoryArtifact(name, availability)
                },
            )
        }
    }
}

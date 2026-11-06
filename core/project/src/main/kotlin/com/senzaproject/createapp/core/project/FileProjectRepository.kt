package com.senzaproject.createapp.core.project

import com.senzaproject.createapp.domain.logging.Logger
import com.senzaproject.createapp.domain.logging.NoOpLogger
import com.senzaproject.createapp.domain.project.NewProjectRequest
import com.senzaproject.createapp.domain.project.Project
import com.senzaproject.createapp.domain.project.ProjectRepository
import com.senzaproject.createapp.domain.project.ProjectTemplate
import com.senzaproject.createapp.domain.project.checkPackageName
import com.senzaproject.createapp.domain.project.checkProjectName
import com.senzaproject.createapp.domain.project.slugify
import com.senzaproject.createapp.domain.result.AppResult
import com.senzaproject.createapp.domain.result.OperationException
import com.senzaproject.createapp.domain.result.OperationFailure
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Repository proyek berbasis folder (lihat [ProjectStorage]). */
class FileProjectRepository(
    private val storage: ProjectStorage,
    ioDispatcher: CoroutineDispatcher,
    private val logger: Logger = NoOpLogger,
) : ProjectRepository {

    private val dispatcher = ioDispatcher
    private val executor = StorageExecutor(storage, ioDispatcher)

    override fun observeProjects(): Flow<List<Project>> = storage.changes
        .map { scanSafely() }
        .flowOn(dispatcher)

    /**
     * Gagal membaca penyimpanan tidak boleh mematikan alur daftar: dicatat di log dan daftar
     * dikosongkan. Batasan ini tercatat di docs/TAHAP-3.md.
     */
    private fun scanSafely(): List<Project> = try {
        storage.scanProjects()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.e(TAG, "Gagal memuat daftar proyek", e)
        emptyList()
    }

    override suspend fun getProject(id: String): AppResult<Project> = executor.unlocked {
        storage.readProject(storage.projectDir(id))
    }

    override suspend fun createProject(request: NewProjectRequest): AppResult<Project> = executor.locked {
        val name = request.name.trim()
        checkProjectName(name).orThrow("Nama proyek tidak valid: \"$name\"")
        val packageName = request.packageName.trim()
        if (request.template == ProjectTemplate.ANDROID_KOTLIN || packageName.isNotEmpty()) {
            checkPackageName(packageName).orThrow("Package tidak valid: \"$packageName\"")
        }
        storage.requireFreeSpace(ProjectStorage.SPACE_MARGIN_BYTES)
        if (storage.projectNames().any { it.second.equals(name, ignoreCase = true) }) {
            throw OperationException(OperationFailure.NAME_TAKEN, "Nama proyek sudah dipakai: \"$name\"")
        }

        val dir = File(storage.root, storage.uniqueId(slugify(name)))
        try {
            if (!dir.mkdir()) throw IOException("Gagal membuat folder proyek: ${dir.path}")
            storage.writeMeta(dir, ProjectMeta(name, packageName, favorite = false, template = request.template))
            ProjectTemplates.generate(dir, name, packageName, request.template)
        } catch (e: Throwable) {
            // Jangan tinggalkan proyek setengah jadi.
            dir.deleteRecursively()
            throw e
        }
        storage.notifyChanged()
        storage.readProject(dir)
    }

    override suspend fun renameProject(id: String, newName: String): AppResult<Project> = executor.locked {
        val name = newName.trim()
        checkProjectName(name).orThrow("Nama proyek tidak valid: \"$name\"")
        val dir = storage.projectDir(id)
        val taken = storage.projectNames().any { (otherId, otherName) ->
            otherId != id && otherName.equals(name, ignoreCase = true)
        }
        if (taken) throw OperationException(OperationFailure.NAME_TAKEN, "Nama proyek sudah dipakai: \"$name\"")
        storage.writeMeta(dir, storage.readMeta(dir).copy(name = name))
        storage.notifyChanged()
        storage.readProject(dir)
    }

    override suspend fun setFavorite(id: String, favorite: Boolean): AppResult<Unit> = executor.locked {
        val dir = storage.projectDir(id)
        storage.writeMeta(dir, storage.readMeta(dir).copy(favorite = favorite))
        storage.notifyChanged()
    }

    override suspend fun deleteProject(id: String): AppResult<Unit> = executor.locked {
        val dir = storage.projectDir(id)
        if (!dir.deleteRecursively()) {
            storage.notifyChanged()
            throw IOException("Sebagian isi proyek gagal dihapus: ${dir.path}")
        }
        storage.notifyChanged()
    }

    private companion object {
        const val TAG = "FileProjectRepository"
    }
}

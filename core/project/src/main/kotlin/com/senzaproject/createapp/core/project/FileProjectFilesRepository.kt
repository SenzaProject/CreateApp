package com.senzaproject.createapp.core.project

import com.senzaproject.createapp.domain.project.FileNode
import com.senzaproject.createapp.domain.project.MAX_TEXT_FILE_BYTES
import com.senzaproject.createapp.domain.project.METADATA_DIR_NAME
import com.senzaproject.createapp.domain.project.ProjectFilesRepository
import com.senzaproject.createapp.domain.project.initialContentFor
import com.senzaproject.createapp.domain.project.joinPath
import com.senzaproject.createapp.domain.project.parentOf
import com.senzaproject.createapp.domain.project.requireValidEntryName
import com.senzaproject.createapp.domain.result.AppResult
import com.senzaproject.createapp.domain.result.OperationException
import com.senzaproject.createapp.domain.result.OperationFailure
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.coroutines.CoroutineDispatcher

/** Operasi file proyek berbasis `java.io`. Semua path divalidasi lewat [ProjectStorage.resolve]. */
class FileProjectFilesRepository(
    private val storage: ProjectStorage,
    ioDispatcher: CoroutineDispatcher,
) : ProjectFilesRepository {

    private val executor = StorageExecutor(storage, ioDispatcher)

    override suspend fun list(projectId: String, dir: String): AppResult<List<FileNode>> = executor.unlocked {
        val projectDir = storage.projectDir(projectId)
        val folder = storage.resolve(projectDir, dir)
        if (!folder.isDirectory) {
            throw OperationException(OperationFailure.NOT_FOUND, "Folder tidak ditemukan: \"$dir\"")
        }
        val children = folder.listFiles() ?: throw IOException("Tidak dapat membaca folder: \"$dir\"")
        children
            .filter { !(dir.isEmpty() && it.name == METADATA_DIR_NAME) }
            .map { nodeOf(it, joinPath(dir, it.name)) }
            .sortedWith(compareByDescending<FileNode> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override suspend fun createFile(projectId: String, dir: String, name: String): AppResult<FileNode> =
        executor.locked {
            requireValidEntryName(name)
            val projectDir = storage.projectDir(projectId)
            val parent = storage.resolve(projectDir, dir)
            if (!parent.isDirectory) {
                throw OperationException(OperationFailure.NOT_FOUND, "Folder tidak ditemukan: \"$dir\"")
            }
            val target = File(parent, name)
            if (exists(target)) {
                throw OperationException(OperationFailure.ALREADY_EXISTS, "Sudah ada: \"${joinPath(dir, name)}\"")
            }
            val bytes = initialContentFor(name, dir).toByteArray(Charsets.UTF_8)
            storage.requireFreeSpace(bytes.size + ProjectStorage.SPACE_MARGIN_BYTES)
            atomicWrite(target, bytes)
            storage.touch(projectDir)
            storage.notifyChanged()
            nodeOf(target, joinPath(dir, name))
        }

    override suspend fun createFolder(projectId: String, dir: String, name: String): AppResult<FileNode> =
        executor.locked {
            requireValidEntryName(name)
            val projectDir = storage.projectDir(projectId)
            val parent = storage.resolve(projectDir, dir)
            if (!parent.isDirectory) {
                throw OperationException(OperationFailure.NOT_FOUND, "Folder tidak ditemukan: \"$dir\"")
            }
            val target = File(parent, name)
            if (exists(target)) {
                throw OperationException(OperationFailure.ALREADY_EXISTS, "Sudah ada: \"${joinPath(dir, name)}\"")
            }
            storage.requireFreeSpace(ProjectStorage.SPACE_MARGIN_BYTES)
            if (!target.mkdir()) throw IOException("Gagal membuat folder: \"${joinPath(dir, name)}\"")
            storage.touch(projectDir)
            storage.notifyChanged()
            nodeOf(target, joinPath(dir, name))
        }

    override suspend fun rename(projectId: String, path: String, newName: String): AppResult<FileNode> =
        executor.locked {
            requireValidEntryName(newName)
            requireNotRoot(path, "Folder akar proyek tidak dapat diganti nama")
            val projectDir = storage.projectDir(projectId)
            val source = storage.resolve(projectDir, path)
            if (!exists(source)) {
                throw OperationException(OperationFailure.NOT_FOUND, "Tidak ditemukan: \"$path\"")
            }
            val newPath = joinPath(parentOf(path), newName)
            if (newName == source.name) {
                nodeOf(source, path)
            } else {
                val target = File(checkNotNull(source.parentFile), newName)
                if (exists(target)) {
                    throw OperationException(OperationFailure.ALREADY_EXISTS, "Sudah ada: \"$newPath\"")
                }
                Files.move(source.toPath(), target.toPath())
                storage.touch(projectDir)
                storage.notifyChanged()
                nodeOf(target, newPath)
            }
        }

    override suspend fun delete(projectId: String, path: String): AppResult<Unit> = executor.locked {
        requireNotRoot(path, "Folder akar proyek tidak dapat dihapus dari sini")
        val projectDir = storage.projectDir(projectId)
        val target = storage.resolve(projectDir, path)
        if (!exists(target)) {
            throw OperationException(OperationFailure.NOT_FOUND, "Tidak ditemukan: \"$path\"")
        }
        val deleted = if (Files.isSymbolicLink(target.toPath())) {
            // Hapus tautannya saja, jangan menelusuri isi yang ditunjuk.
            target.delete()
        } else {
            target.deleteRecursively()
        }
        storage.touch(projectDir)
        storage.notifyChanged()
        if (!deleted) throw IOException("Gagal menghapus: \"$path\"")
    }

    override suspend fun readText(projectId: String, path: String): AppResult<String> = executor.unlocked {
        requireNotRoot(path, "Path file diperlukan")
        val file = storage.resolve(storage.projectDir(projectId), path)
        if (!file.isFile) {
            throw OperationException(OperationFailure.NOT_FOUND, "File tidak ditemukan: \"$path\"")
        }
        if (file.length() > MAX_TEXT_FILE_BYTES) {
            throw OperationException(OperationFailure.TOO_LARGE, "File lebih besar dari batas: \"$path\"")
        }
        val bytes = file.readBytes()
        if (bytes.size > MAX_TEXT_FILE_BYTES) {
            throw OperationException(OperationFailure.TOO_LARGE, "File lebih besar dari batas: \"$path\"")
        }
        decodeUtf8Strict(bytes, path)
    }

    override suspend fun writeText(projectId: String, path: String, content: String): AppResult<Unit> =
        executor.locked {
            requireNotRoot(path, "Path file diperlukan")
            val bytes = content.toByteArray(Charsets.UTF_8)
            if (bytes.size > MAX_TEXT_FILE_BYTES) {
                throw OperationException(OperationFailure.TOO_LARGE, "Isi lebih besar dari batas file teks")
            }
            val projectDir = storage.projectDir(projectId)
            val file = storage.resolve(projectDir, path)
            val parent = file.parentFile
            if (parent == null || !parent.isDirectory) {
                throw OperationException(OperationFailure.NOT_FOUND, "Folder tujuan tidak ada: \"${parentOf(path)}\"")
            }
            if (file.exists() && !file.isFile) {
                throw OperationException(OperationFailure.ALREADY_EXISTS, "Sudah ada sebagai folder: \"$path\"")
            }
            storage.requireFreeSpace(bytes.size + ProjectStorage.SPACE_MARGIN_BYTES)
            atomicWrite(file, bytes)
            storage.touch(projectDir)
            storage.notifyChanged()
        }

    private fun requireNotRoot(path: String, message: String) {
        if (path.isEmpty()) throw OperationException(OperationFailure.OUTSIDE_PROJECT, message)
    }

    /** `exists` yang juga menghitung tautan simbolik rusak. */
    private fun exists(file: File): Boolean = Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun nodeOf(file: File, path: String) = FileNode(
        name = file.name,
        path = path,
        isDirectory = file.isDirectory,
        sizeBytes = if (file.isFile) file.length() else 0L,
        lastModifiedMillis = file.lastModified(),
    )

    /** Dekode UTF-8 yang ketat: byte tidak valid atau karakter NUL berarti bukan teks (jangan merusak file saat disimpan). */
    private fun decodeUtf8Strict(bytes: ByteArray, path: String): String {
        if (bytes.any { it == 0.toByte() }) {
            throw OperationException(OperationFailure.NOT_TEXT, "File biner tidak dapat dibuka sebagai teks: \"$path\"")
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            throw OperationException(OperationFailure.NOT_TEXT, "File bukan teks UTF-8: \"$path\"", e)
        }
    }
}

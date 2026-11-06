package com.senzaproject.createapp.core.project

import com.senzaproject.createapp.domain.project.NewProjectRequest
import com.senzaproject.createapp.domain.project.Project
import com.senzaproject.createapp.domain.project.ProjectTemplate
import com.senzaproject.createapp.domain.result.AppError
import com.senzaproject.createapp.domain.result.AppResult
import com.senzaproject.createapp.domain.result.OperationFailure
import java.io.File
import kotlinx.coroutines.Dispatchers

/** Mengambil nilai sukses; gagal dengan pesan jelas bila hasilnya Failure. */
internal fun <T> AppResult<T>.ok(): T = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> throw AssertionError("Seharusnya sukses, tetapi gagal: $error")
}

/** Alasan kegagalan dari hasil Failure bertipe Operation; gagal bila hasilnya bukan itu. */
internal fun AppResult<*>.failure(): OperationFailure = when (this) {
    is AppResult.Success -> throw AssertionError("Seharusnya gagal, tetapi sukses: $value")
    is AppResult.Failure -> {
        val e = error
        if (e is AppError.Operation) e.reason else throw AssertionError("Bukan AppError.Operation: $e")
    }
}

/** Penyimpanan + repository di atas folder uji; dibuat ulang pada folder yang sama untuk meniru restart aplikasi. */
internal class Fixture(val root: File) {
    val storage = ProjectStorage(root)
    val projects = FileProjectRepository(storage, Dispatchers.Unconfined)
    val files = FileProjectFilesRepository(storage, Dispatchers.Unconfined)

    suspend fun newProject(name: String = "Contoh", template: ProjectTemplate = ProjectTemplate.EMPTY): Project =
        projects.createProject(NewProjectRequest(name, if (template == ProjectTemplate.EMPTY) "" else "com.contoh.app", template)).ok()

    fun dirOf(project: Project): File = storage.projectDir(project.id)
}

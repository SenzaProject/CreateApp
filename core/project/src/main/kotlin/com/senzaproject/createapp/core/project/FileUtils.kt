package com.senzaproject.createapp.core.project

import com.senzaproject.createapp.domain.result.OperationException
import com.senzaproject.createapp.domain.result.OperationFailure
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Melempar [OperationException] dengan [message] bila receiver bukan null. */
internal fun OperationFailure?.orThrow(message: String) {
    if (this != null) throw OperationException(this, message)
}

/**
 * Menulis [bytes] ke [target] secara atomik: data ditulis dan di-sync ke berkas sementara
 * (`<nama>.tmp`) lalu dipindahkan menimpa [target]. Bila proses terhenti di tengah jalan,
 * [target] tetap berisi data lama yang utuh.
 */
internal fun atomicWrite(target: File, bytes: ByteArray) {
    val parent = target.parentFile ?: throw IOException("Folder tujuan tidak valid: ${target.path}")
    if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
        throw IOException("Gagal membuat folder: ${parent.path}")
    }
    val temp = File(parent, target.name + ".tmp")
    try {
        FileOutputStream(temp).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        if (temp.exists()) temp.delete()
    }
}

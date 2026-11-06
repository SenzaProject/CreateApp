package com.senzaproject.createapp.ui.common

import androidx.annotation.StringRes
import com.senzaproject.createapp.R
import com.senzaproject.createapp.domain.result.AppError
import com.senzaproject.createapp.domain.result.OperationFailure

/** Teks yang dapat dilokalisasi untuk setiap kategori error; [AppError.message] hanya untuk log. */
@StringRes
fun AppError.messageRes(): Int = when (this) {
    is AppError.Operation -> reason.messageRes()
    is AppError.Permission -> R.string.error_permission
    is AppError.Storage -> R.string.error_storage
    is AppError.Validation -> R.string.error_validation
    is AppError.Unsupported -> R.string.error_unsupported
    is AppError.Unknown -> R.string.error_unknown
}

@StringRes
fun OperationFailure.messageRes(): Int = when (this) {
    OperationFailure.INVALID_NAME -> R.string.error_invalid_name
    OperationFailure.RESERVED_NAME -> R.string.error_reserved_name
    OperationFailure.INVALID_PACKAGE -> R.string.error_invalid_package
    OperationFailure.NAME_TAKEN -> R.string.error_name_taken
    OperationFailure.NOT_FOUND -> R.string.error_not_found
    OperationFailure.ALREADY_EXISTS -> R.string.error_already_exists
    OperationFailure.OUTSIDE_PROJECT -> R.string.error_outside_project
    OperationFailure.TOO_LARGE -> R.string.error_too_large
    OperationFailure.NOT_TEXT -> R.string.error_not_text
    OperationFailure.LOW_STORAGE -> R.string.error_low_storage
    OperationFailure.INVALID_SOURCE -> R.string.error_invalid_source
}

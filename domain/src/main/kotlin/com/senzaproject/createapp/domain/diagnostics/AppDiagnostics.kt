package com.senzaproject.createapp.domain.diagnostics

import com.senzaproject.createapp.domain.module.ModuleInfo

data class AppMetadata(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isDebug: Boolean,
)

data class DeviceInfo(
    val androidRelease: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val supportedAbis: List<String>,
)

data class AppDiagnostics(
    val app: AppMetadata,
    val device: DeviceInfo,
    val modules: List<ModuleInfo>,
)

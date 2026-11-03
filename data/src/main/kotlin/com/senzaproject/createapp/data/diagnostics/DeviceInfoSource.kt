package com.senzaproject.createapp.data.diagnostics

import android.os.Build
import com.senzaproject.createapp.domain.diagnostics.DeviceInfo

/** Sumber informasi perangkat. Dipisah dari repository agar mudah diganti saat pengujian. */
interface DeviceInfoSource {
    fun read(): DeviceInfo
}

class AndroidDeviceInfoSource : DeviceInfoSource {
    override fun read(): DeviceInfo = DeviceInfo(
        androidRelease = Build.VERSION.RELEASE ?: "?",
        sdkInt = Build.VERSION.SDK_INT,
        manufacturer = Build.MANUFACTURER ?: "?",
        model = Build.MODEL ?: "?",
        supportedAbis = Build.SUPPORTED_ABIS?.toList() ?: emptyList(),
    )
}

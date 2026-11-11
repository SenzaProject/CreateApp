package com.senzaproject.createapp.domain.builder

data class ToolchainItem(val id: String, val level: CheckLevel, val detail: String)

/** Hasil pemeriksaan lingkungan build di Termux (Java, Gradle/wrapper, Android SDK, folder bersama). */
data class ToolchainReport(val items: List<ToolchainItem>) {

    /** True bila tidak ada komponen wajib yang kurang. WARN (mis. aapt2 di arsitektur ARM) tidak memblokir. */
    val isReady: Boolean get() = items.isNotEmpty() && items.none { it.level == CheckLevel.MISSING && it.id in REQUIRED }

    val hasWarnings: Boolean get() = items.any { it.level == CheckLevel.WARN }

    companion object {
        val REQUIRED: Set<String> = setOf("java", "wrapper", "sdk", "platform", "buildtools", "source")

        fun fromMessages(messages: List<HelperMessage>): ToolchainReport = ToolchainReport(
            messages.filterIsInstance<HelperMessage.Check>().map { ToolchainItem(it.id, it.level, it.detail) },
        )
    }
}

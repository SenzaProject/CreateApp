package com.senzaproject.createapp.core.terminal

/** Konstanta Termux (paket, path, dan nama intent `RUN_COMMAND` resmi). */
object TermuxPaths {
    const val PACKAGE = "com.termux"
    const val HOME = "/data/data/com.termux/files/home"
    const val PREFIX = "/data/data/com.termux/files/usr"

    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"

    const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"

    const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

    /** Baris yang harus ada di `~/.termux/termux.properties` agar aplikasi lain boleh menjalankan perintah. */
    const val ALLOW_EXTERNAL_APPS_LINE = "allow-external-apps=true"

    /**
     * Perintah yang dijalankan DI DALAM Termux untuk mengaktifkan `allow-external-apps`. Aman dijalankan
     * berulang: mengganti baris lama bila ada, menambahkan bila belum ada (dan memastikan baris terakhir
     * file lama berakhir newline agar tidak menempel), lalu memuat ulang pengaturan bila perintahnya ada.
     * Sengaja tanpa karakter `$` dan tanda kutip ganda agar aman sebagai string Kotlin dan saat disalin.
     */
    const val ENABLE_EXTERNAL_APPS_COMMAND =
        "mkdir -p ~/.termux && touch ~/.termux/termux.properties && " +
            "{ if grep -q '^allow-external-apps' ~/.termux/termux.properties; then " +
            "sed -i 's/^allow-external-apps.*/allow-external-apps=true/' ~/.termux/termux.properties; " +
            "else { tail -c1 ~/.termux/termux.properties | grep -q . && echo >> ~/.termux/termux.properties; " +
            "echo 'allow-external-apps=true' >> ~/.termux/termux.properties; }; fi; } && " +
            "{ command -v termux-reload-settings >/dev/null 2>&1 && termux-reload-settings; true; }"
}

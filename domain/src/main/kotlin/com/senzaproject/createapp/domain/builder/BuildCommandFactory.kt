package com.senzaproject.createapp.domain.builder

/**
 * Membuat perintah `bash -c` untuk Termux. Setiap perintah MEMASANG helper lebih dulu lalu
 * menjalankannya, sehingga tidak ada langkah "pasang helper" terpisah yang bisa terlewat atau usang.
 *
 * Helper ditulis ke file sementara lalu dipindah (`mv` = rename atomik). Ini penting: perintah
 * `cancel` atau `check` bisa berjalan saat build sedang memakai helper yang sama, dan menimpa file
 * yang sedang dibaca shell akan merusak build itu.
 *
 * @param helperBase64 isi `createapp-build.sh` dalam Base64 tanpa pemisah baris.
 */
class BuildCommandFactory(private val helperBase64: String) {

    init {
        require(helperBase64.isNotEmpty()) { "Helper kosong" }
        require(helperBase64.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }) {
            "Helper Base64 tidak valid"
        }
    }

    fun check(sourcePath: String?): String {
        val args = if (sourcePath == null) "check" else "check --source " + ShellQuote.quote(sourcePath)
        return install() + run(args)
    }

    /**
     * @param runToken bila diberikan, helper menulis log langsung ke `live/<id>.log` di folder bersama
     *   dengan header berisi token ini (lihat [LiveLog]). Token harus lolos [LiveLog.isValidToken].
     */
    fun build(
        sourcePath: String,
        projectId: String,
        type: BuildType,
        outputRootPath: String,
        runToken: String? = null,
    ): String {
        if (runToken != null) require(LiveLog.isValidToken(runToken)) { "Token run tidak valid" }
        val args = "build" +
            " --source " + ShellQuote.quote(sourcePath) +
            " --name " + ShellQuote.quote(projectId) +
            " --task " + type.gradleTask +
            " --out " + ShellQuote.quote(outputRootPath) +
            (if (runToken != null) " --run " + ShellQuote.quote(runToken) else "")
        return install() + run(args)
    }

    fun cancel(): String = install() + run("cancel")

    fun clean(): String = install() + run("clean")

    private fun install(): String =
        "mkdir -p ~/.createapp && " +
            "printf '%s' '" + helperBase64 + "' | base64 -d > ~/.createapp/build.sh.new && " +
            "mv -f ~/.createapp/build.sh.new ~/.createapp/build.sh && "

    private fun run(arguments: String): String = "sh ~/.createapp/build.sh $arguments"
}

package com.senzaproject.createapp.domain.terminal

/** Hasil penguraian satu baris masukan terminal. */
sealed interface ParsedCommand {
    data object Clear : ParsedCommand
    data object Help : ParsedCommand
    data object History : ParsedCommand

    /** `cd` bawaan; [target] null berarti pindah ke direktori awal. */
    data class ChangeDirectory(val target: String?) : ParsedCommand

    /** `cd` dengan argumen yang tidak valid (kutip tidak tertutup atau lebih dari satu argumen). */
    data object InvalidCd : ParsedCommand

    /** Perintah yang diserahkan ke shell backend apa adanya. */
    data class External(val command: String) : ParsedCommand
}

private const val SHELL_OPERATORS = ";&|<>()`"

/**
 * Perintah bawaan: `clear`/`cls`, `help`, `history`, dan `cd`. Setiap perintah lain dijalankan di shell.
 *
 * `cd` harus bawaan karena setiap perintah berjalan di proses shell baru, sehingga `cd` di dalam
 * shell tidak akan bertahan. Bila argumen `cd` mengandung operator shell (mis. `cd x && ls`), seluruh
 * baris diserahkan ke shell sehingga `cd` di dalamnya hanya berlaku untuk baris itu.
 */
fun parseCommand(input: String): ParsedCommand {
    val text = input.trim()
    return when {
        text == "clear" || text == "cls" -> ParsedCommand.Clear
        text == "help" -> ParsedCommand.Help
        text == "history" -> ParsedCommand.History
        text == "cd" -> ParsedCommand.ChangeDirectory(null)
        text.length > 2 && text.startsWith("cd") && text[2].isWhitespace() -> parseCd(text)
        else -> ParsedCommand.External(text)
    }
}

private fun parseCd(text: String): ParsedCommand {
    val rest = text.substring(2).trim()
    if (hasShellOperator(rest)) return ParsedCommand.External(text)
    val words = ShellWords.split(rest) ?: return ParsedCommand.InvalidCd
    return when (words.size) {
        0 -> ParsedCommand.ChangeDirectory(null)
        1 -> ParsedCommand.ChangeDirectory(words[0])
        else -> ParsedCommand.InvalidCd
    }
}

private fun hasShellOperator(text: String): Boolean {
    var quote: Char? = null
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (quote != null) {
            if (c == quote) quote = null
        } else if (c == '\'' || c == '"') {
            quote = c
        } else if (c == '\\') {
            i++
        } else if (SHELL_OPERATORS.indexOf(c) >= 0) {
            return true
        }
        i++
    }
    return false
}

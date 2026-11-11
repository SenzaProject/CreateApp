package com.senzaproject.createapp.core.builder

import android.content.Context
import com.senzaproject.createapp.domain.builder.BuildSettingsStore

/** Menyimpan folder bersama pilihan pengguna (URI SAF). Tidak menyimpan rahasia apa pun. */
class SharedPrefsBuildSettingsStore(context: Context) : BuildSettingsStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var sharedTreeUri: String?
        get() = prefs.getString(KEY_TREE_URI, null)
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove(KEY_TREE_URI) else editor.putString(KEY_TREE_URI, value)
            editor.apply()
        }

    private companion object {
        const val PREFS_NAME = "build_settings"
        const val KEY_TREE_URI = "shared_tree_uri"
    }
}

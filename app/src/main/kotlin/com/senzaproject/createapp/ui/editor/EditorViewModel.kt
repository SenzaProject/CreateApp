package com.senzaproject.createapp.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel

/**
 * Menyimpan buffer teks sementara editor awal selama proses aplikasi hidup (bertahan saat
 * pindah layar dan rotasi). Buffer BELUM disimpan ke file; penyimpanan file dikerjakan pada Tahap 3.
 */
class EditorViewModel : ViewModel() {

    // State Compose (sinkron) agar kursor dan pengetikan tidak terganggu pembaruan asinkron.
    var value by mutableStateOf(TextFieldValue(""))
        private set

    fun onValueChange(newValue: TextFieldValue) {
        value = newValue
    }
}

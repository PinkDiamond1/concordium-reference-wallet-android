package com.concordium.wallet.ui.passphrase.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import cash.z.ecc.android.bip39.Mnemonics
import com.concordium.wallet.BuildConfig
import com.concordium.wallet.ui.passphrase.common.WordsPickedBaseListAdapter
import java.io.Serializable

class PassPhraseViewModel(application: Application) : AndroidViewModel(application), Serializable {
    var mnemonicCodeToConfirm = listOf<CharArray>()
    var wordsPicked = arrayOfNulls<String>(WORD_COUNT + (WordsPickedBaseListAdapter.OFFSET * 2) + 1)

    companion object {
        val WORD_COUNT: Int = Mnemonics.WordCount.COUNT_24.count
        const val PASS_PHRASE_DATA = "PASS_PHRASE_DATA"
    }

    private val _validateLiveData = MutableLiveData<Boolean>()
    val validate: LiveData<Boolean>
        get() = _validateLiveData

    val continueEnabled: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>() }

    val reveal = MutableLiveData<Boolean>()

    fun generateMnemonicCode() {
        val mnemonicCode: Mnemonics.MnemonicCode = Mnemonics.MnemonicCode(Mnemonics.WordCount.COUNT_24)
        if (BuildConfig.DEBUG)
            println("LC -> ${mnemonicCode.words.joinToString(" ") { String(it) }}")
        mnemonicCodeToConfirm = mnemonicCode.words.toList()
    }

    fun hack() {
        if (BuildConfig.DEBUG)
            _validateLiveData.value = true
    }

    fun validateInputCode() {
        var success = false
        val entered = wordsPicked.filterNotNull().filter { it != WordsPickedBaseListAdapter.BLANK }
        if (entered.size < WORD_COUNT)
            return
        if (entered.size == WORD_COUNT) {
            val enteredPhrase: String = entered.joinToString(" ") { it }
            success = enteredPhrase == generatedPhrase()
        }
        _validateLiveData.value = success
    }

    fun generatedPhrase(): String {
        return mnemonicCodeToConfirm.joinToString(" ") { String(it) }
    }
}

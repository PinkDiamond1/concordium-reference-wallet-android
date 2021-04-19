package com.concordium.wallet.ui.transaction.sendfunds

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.text.method.TextKeyListener
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.concordium.wallet.R
import com.concordium.wallet.core.arch.EventObserver
import com.concordium.wallet.core.backend.BackendError
import com.concordium.wallet.data.model.ShieldedAccountEncryptionStatus
import com.concordium.wallet.data.room.Account
import com.concordium.wallet.data.room.Recipient
import com.concordium.wallet.data.util.CurrencyUtil
import com.concordium.wallet.ui.base.BaseActivity
import com.concordium.wallet.ui.common.failed.FailedActivity
import com.concordium.wallet.ui.common.failed.FailedViewModel
import com.concordium.wallet.ui.recipient.recipientlist.RecipientListActivity
import com.concordium.wallet.ui.transaction.sendfundsconfirmed.SendFundsConfirmedActivity
import com.concordium.wallet.uicore.DecimalTextWatcher
import com.concordium.wallet.uicore.afterTextChanged
import com.concordium.wallet.util.Log
import kotlinx.android.synthetic.main.activity_send_funds.*
import kotlinx.android.synthetic.main.item_account.view.*
import kotlinx.android.synthetic.main.progress.*
import java.text.DecimalFormatSymbols
import javax.crypto.Cipher

class SendFundsActivity :
    BaseActivity(R.layout.activity_send_funds, R.string.send_funds_title) {

    companion object {
        const val EXTRA_ACCOUNT = "EXTRA_ACCOUNT"
        const val EXTRA_SHIELDED = "EXTRA_SHIELDED"
        const val EXTRA_RECIPIENT = "EXTRA_RECIPIENT"
    }

    private lateinit var viewModel: SendFundsViewModel


    //region Lifecycle
    //************************************************************

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val account = intent.extras!!.getSerializable(EXTRA_ACCOUNT) as Account
        val isShielded = intent.extras!!.getBoolean(EXTRA_SHIELDED)
        initializeViewModel()
        viewModel.initialize(account, isShielded)
        initViews()

        scrollview_container.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    scrollview_container.post(Runnable {
                        scrollview_container.fullScroll(View.FOCUS_DOWN)
                    })
                }
            });
        handleRecipient(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleRecipient(intent)
    }

    fun handleRecipient(intent: Intent?){
        val recipient = intent?.getSerializableExtra(EXTRA_RECIPIENT) as? Recipient
        recipient?.let {
            viewModel.selectedRecipient = recipient
            updateConfirmButton()
            if(viewModel.account.id == recipient.id){
                select_recipient_layout.visibility = View.GONE
                setActionBarTitle(if(viewModel.isShielded) R.string.send_funds_unshield_title else R.string.send_funds_shield_title)
            }
        }
    }

    //endregion

    //region Initialize
    //************************************************************

    private fun initializeViewModel() {
        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        ).get(SendFundsViewModel::class.java)

        viewModel.waitingLiveData.observe(this, Observer<Boolean> { waiting ->
            waiting?.let {
                showWaiting(isWaiting())
            }
        })
        viewModel.waitingReceiverAccountPublicKeyLiveData.observe(this, Observer<Boolean> { waiting ->
            waiting?.let {
                showWaiting(isWaiting())
            }
        })
        viewModel.errorLiveData.observe(this, object : EventObserver<Int>() {
            override fun onUnhandledEvent(value: Int) {
                showError(value)
            }
        })
        viewModel.showAuthenticationLiveData.observe(this, object : EventObserver<Boolean>() {
            override fun onUnhandledEvent(value: Boolean) {
                if (value) {
                    showAuthentication(createConfirmString(), viewModel.shouldUseBiometrics(), viewModel.usePasscode(), object : AuthenticationCallback{
                        override fun getCipherForBiometrics() : Cipher?{
                            return viewModel.getCipherForBiometrics()
                        }
                        override fun onCorrectPassword(password: String) {
                            viewModel.continueWithPassword(password)
                        }
                        override fun onCipher(cipher: Cipher) {
                            viewModel.checkLogin(cipher)
                        }
                        override fun onCancelled() {
                        }
                    })
                }
            }
        })
        viewModel.gotoSendFundsConfirmLiveData.observe(this, object : EventObserver<Boolean>() {
            override fun onUnhandledEvent(value: Boolean) {
                if (value) {
                    gotoSendFundsConfirm()
                }
            }
        })
        viewModel.gotoFailedLiveData.observe(
            this,
            object : EventObserver<Pair<Boolean, BackendError?>>() {
                override fun onUnhandledEvent(value: Pair<Boolean, BackendError?>) {
                    if (value.first) {
                        gotoFailed(value.second)
                    }
                }
            })
        viewModel.transactionFeeLiveData.observe(this, object : Observer<Long> {
            override fun onChanged(value: Long?) {
                value?.let {
                    fee_info_textview.visibility = View.VISIBLE
                    fee_info_textview.text = getString(
                        R.string.send_funds_fee_info, CurrencyUtil.formatGTU(value)
                    )
                    updateConfirmButton()
                }
            }
        })
        viewModel.recipientLiveData.observe(this, object : Observer<Recipient> {
            override fun onChanged(value: Recipient?) {
                showRecipient(value)
            }
        })
    }

    private fun initViews() {
        progress_layout.visibility = View.GONE
        error_textview.visibility = View.INVISIBLE
        amount_edittext.afterTextChanged { _ ->
            updateConfirmButton()
            updateAmountEditText()
        }
        updateAmountEditText()
        amount_edittext.setOnEditorActionListener { textView, actionId, _ ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_DONE -> {
                    if (textView.text.isNotEmpty())
                        viewModel.sendFunds(amount_edittext.text.toString())
                    true
                }
                else -> false
            }
        }
        // Allow both . and , but change them in text watcher to decimal separator based on language
        // We allow them both to avoid problems with some devices (eg. Samsung that have their own keyboard restrictions)
        amount_edittext.keyListener = DigitsKeyListener.getInstance("0123456789.,")
        amount_edittext.addTextChangedListener(DecimalTextWatcher(6))
        amount_edittext.addTextChangedListener(object : TextWatcher {

            private var previousText: String = ""
            override fun afterTextChanged(editable: Editable) {
                var change = false
                var str = editable.toString()

                try {
                    val intVal = CurrencyUtil.getWholePart(str)
                    if(intVal != null){
                        if( intVal > (Long.MAX_VALUE - 999999L)/1000000L) {
                            change = true
                        }
                    }
                }
                catch(e: Exception){
                    change = true
                }

                if (change) {
                    editable.replace(0, editable.length, previousText);
                }
                else{
                    previousText = editable.toString()
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
        })
        amount_edittext.requestFocus()

        select_recipient_textview.setText(if(viewModel.isShielded) R.string.send_funds_select_recipient_or_unshield_amount else R.string.send_funds_select_recipient_or_shield_amount)

        select_recipient_layout.setOnClickListener {
            gotoSelectRecipient()
        }

        confirm_button.setOnClickListener {
            viewModel.sendFunds(amount_edittext.text.toString())
        }

        balance_total_textview.text = CurrencyUtil.formatGTU(viewModel.account.totalUnshieldedBalance - viewModel.account.getAtDisposalSubstraction(), withGStroke = true)
        shielded_total_textview.text = CurrencyUtil.formatGTU(viewModel.account.totalShieldedBalance, withGStroke = true)
        shielded_total_textview.visibility = if(viewModel.account.encryptedBalanceStatus == ShieldedAccountEncryptionStatus.DECRYPTED || viewModel.account.encryptedBalanceStatus == ShieldedAccountEncryptionStatus.PARTIALLYDECRYPTED) View.VISIBLE else View.GONE
        shielded_lock_plus.visibility = shielded_total_textview.visibility
        shielded_lock_container.visibility = if(viewModel.account.encryptedBalanceStatus != ShieldedAccountEncryptionStatus.DECRYPTED) View.VISIBLE else View.GONE

    }

    //endregion

    //region Control/UI
    //************************************************************

    private fun showWaiting(waiting: Boolean) {
        progress_layout.visibility = if(waiting) View.VISIBLE else View.GONE
        // Update button enabled state, because it is dependant on waiting state
        updateConfirmButton()
    }

    private fun showError(stringRes: Int) {
        popup.showSnackbar(root_layout, stringRes)
    }


    private fun gotoSelectRecipient() {
        val intent = Intent(this, RecipientListActivity::class.java)
        intent.putExtra(RecipientListActivity.EXTRA_SELECT_RECIPIENT_MODE, true)
        intent.putExtra(RecipientListActivity.EXTRA_SHIELDED, viewModel.isShielded)
        intent.putExtra(RecipientListActivity.EXTRA_ACCOUNT, viewModel.account)
        startActivity(intent)
    }

    private fun gotoSendFundsConfirm() {
        val transfer = viewModel.newTransfer
        val recipient = viewModel.selectedRecipient
        if (transfer != null && recipient != null) {
            val intent = Intent(this, SendFundsConfirmedActivity::class.java)
            intent.putExtra(SendFundsConfirmedActivity.EXTRA_TRANSFER, transfer)
            intent.putExtra(SendFundsConfirmedActivity.EXTRA_RECIPIENT, recipient)
            startActivity(intent)
        }
    }

    private fun gotoFailed(error: BackendError?) {
        val intent = Intent(this, FailedActivity::class.java)
        intent.putExtra(FailedActivity.EXTRA_SOURCE, FailedViewModel.Source.Transfer)
        error?.let {
            intent.putExtra(FailedActivity.EXTRA_ERROR, it)
        }
        startActivity(intent)
    }

    private fun showRecipient(recipient: Recipient?) {
        if (recipient == null) {
            select_recipient_textview.text = ""
            select_recipient_image.setImageResource(R.drawable.background_q_r)
            confirm_button.setText(R.string.send_funds_confirm)
        } else {
            if(viewModel.isShielded){
                if(viewModel.isTransferToSameAccount()){
                    select_recipient_textview.setText(R.string.send_funds_recipient_unshield)
                    select_recipient_image.setImageDrawable(null)
                    confirm_button.setText(R.string.send_funds_confirm_unshield)
                } else {
                    select_recipient_textview.text = recipient.name
                    select_recipient_image.setImageResource(R.drawable.ic_shielded_icon)
                    confirm_button.setText(R.string.send_funds_confirm_send_shielded)
                }
            } else {
                if(viewModel.isTransferToSameAccount()){
                    select_recipient_textview.setText(R.string.send_funds_recipient_shield)
                    select_recipient_image.setImageResource(R.drawable.ic_shielded_icon)
                    confirm_button.setText(R.string.send_funds_confirm_shield)
                } else {
                    select_recipient_textview.text = recipient.name
                    select_recipient_image.setImageResource(R.drawable.background_q_r)
                    confirm_button.setText(R.string.send_funds_confirm)
                }
            }
        }
    }

    fun isWaiting(): Boolean {
        var waiting = false
        viewModel.waitingLiveData.value?.let {
            if(it){
                waiting = true
            }
        }
        viewModel.waitingReceiverAccountPublicKeyLiveData.value?.let {
            if(it){
                waiting = true
            }
        }
        return waiting
    }

    private fun updateConfirmButton(): Boolean {
        val hasSufficientFunds = viewModel.hasSufficientFunds(amount_edittext.text.toString())
        error_textview.visibility = if (hasSufficientFunds) View.INVISIBLE else View.VISIBLE
        val enabled = if(isWaiting()) false else {
            amount_edittext.text.isNotEmpty()
                    && viewModel.selectedRecipient != null
                    && viewModel.transactionFeeLiveData.value != null
                    && hasSufficientFunds
        }
        confirm_button.isEnabled = enabled
        return enabled
    }

    private fun updateAmountEditText() {
        if (amount_edittext.text.isNotEmpty()) {
            // Only setting this (to one char) to have the width being smaller
            // Width is WRAP_CONTENT and hint text count towards this
            amount_edittext.setHint("0")
            amount_edittext.gravity = Gravity.CENTER
        } else {
            amount_edittext.setHint("0${DecimalFormatSymbols.getInstance().decimalSeparator}00")
            amount_edittext.gravity = Gravity.NO_GRAVITY
        }
    }

    private fun createConfirmString(): String? {
        val amount = viewModel.getAmount()
        val cost = viewModel.transactionFeeLiveData.value
        val recipient = viewModel.selectedRecipient
        if (amount == null || cost == null || recipient == null) {
            showError(R.string.app_error_general)
            return null
        }
        val amountString = CurrencyUtil.formatGTU(amount, withGStroke = true)
        val costString = CurrencyUtil.formatGTU(cost, withGStroke = true)

        return if(viewModel.isShielded){
            if(viewModel.isTransferToSameAccount()){
                getString(R.string.send_funds_confirmation_unshield, amountString, recipient.name, costString)
            } else {
                getString(R.string.send_funds_confirmation_send_shielded, amountString, recipient.name, costString)
            }
        } else {
            if(viewModel.isTransferToSameAccount()){
                getString(R.string.send_funds_confirmation_shield, amountString, recipient.name, costString)
            } else {
                getString(R.string.send_funds_confirmation, amountString, recipient.name, costString)
            }
        }
    }



    //endregion
}

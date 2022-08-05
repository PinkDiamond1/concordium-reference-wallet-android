package com.concordium.wallet.ui.identity.identityconfirmed

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import com.concordium.wallet.R
import com.concordium.wallet.data.IdentityRepository
import com.concordium.wallet.data.model.IdentityStatus
import com.concordium.wallet.data.room.Account
import com.concordium.wallet.data.room.Identity
import com.concordium.wallet.data.room.WalletDatabase
import com.concordium.wallet.databinding.ActivityIdentityConfirmedBinding
import com.concordium.wallet.ui.MainActivity
import com.concordium.wallet.ui.RequestCodes
import com.concordium.wallet.ui.common.account.BaseAccountActivity
import com.concordium.wallet.ui.identity.identityproviderlist.IdentityProviderListActivity
import com.concordium.wallet.uicore.dialog.Dialogs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class IdentityConfirmedActivity : BaseAccountActivity(), Dialogs.DialogFragmentListener {
    companion object {
        const val EXTRA_IDENTITY = "EXTRA_IDENTITY"
    }

    private lateinit var binding: ActivityIdentityConfirmedBinding
    private lateinit var viewModel: IdentityConfirmedViewModel

    private var identity: Identity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIdentityConfirmedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupActionBar(binding.toolbarLayout.toolbar, binding.toolbarLayout.toolbarTitle, R.string.identity_confirmed_title)
        identity = intent.extras!!.getSerializable(EXTRA_IDENTITY) as Identity
        initializeNewAccountViewModel()
        initializeAuthenticationObservers()
        initializeViewModel()
        initializeViews()
        // If we're being restored from a previous state
        if (savedInstanceState != null) {
            return
        }
        viewModel.startIdentityUpdate()
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateState()
    }

    override fun onBackPressed() {
        // Ignore back press
    }

    override fun onDialogResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == RequestCodes.REQUEST_IDENTITY_ERROR_DIALOG) {
            if (resultCode == Dialogs.POSITIVE) {
                // Just go back to the identityProvider list to try again
                finish()
            }
        }
    }

    private fun initializeViewModel() {
        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[IdentityConfirmedViewModel::class.java]

        viewModel.waitingLiveData.observe(this) { waiting ->
            waiting?.let {
                showWaiting(waiting)
            }
        }
        viewModel.isFirstIdentityLiveData.observe(this) { isFirst ->
            isFirst?.let {
                updateInfoText(isFirst)
            }
        }
        viewModel.identityErrorLiveData.observe(this) { data ->
            data?.let {
                showCreateIdentityError(data.identity.name)
            }
        }
        viewModel.identityDoneLiveData.observe(this) {
            showSubmitAccount()
        }
    }

    private fun initializeViews() {
        hideActionBarBack(this)
        showWaiting(true)

        binding.confirmButton.setOnClickListener {
            continueClicked()
        }

        identity?.let {
            binding.identityView.setIdentityData(it)
        }

        binding.rlAccount.visibility = View.GONE

        binding.btnSubmitAccount.setOnClickListener {
            viewModelNewAccount.initialize("Account 9", identity!!)  // skal være identity fra viewmodel
            viewModelNewAccount.confirmWithoutAttributes()
        }
    }

    private fun showCreateIdentityError(errorFromIdentityProvider: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.dialog_identity_create_error_title)
        builder.setMessage(getString(R.string.dialog_identity_create_error_text, errorFromIdentityProvider))
        builder.setPositiveButton(getString(R.string.dialog_identity_create_error_retry)) { _, _ -> gotoIdentityList() }
        builder.create().show()
    }

    private fun gotoIdentityList() {
        finish()
        startActivity(Intent(this, IdentityProviderListActivity::class.java))
    }

    private fun continueClicked() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.identity_confirmed_alert_dialog_title))
        builder.setMessage(getString(R.string.identity_confirmed_alert_dialog_text))
        builder.setPositiveButton(getString(R.string.identity_confirmed_alert_dialog_ok)) { _, _ -> showSubmitAccount() }
        builder.setCancelable(true)
        builder.create().show()
    }

     override fun showWaiting(waiting: Boolean) {
        if (waiting) {
            binding.includeProgress.progressLayout.visibility = View.VISIBLE
        } else {
            binding.includeProgress.progressLayout.visibility = View.GONE
        }
    }

    override fun showError(stringRes: Int) {
        // error will be shown on account overview page later
    }

    override fun accountCreated(account: Account) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    private fun showSubmitAccount() {
        CoroutineScope(Dispatchers.IO).launch {
            val identityDao = WalletDatabase.getDatabase(application).identityDao()
            val identityRepository = IdentityRepository(identityDao)
            identity?.let {
                identity = identityRepository.findById(it.id)
            }
            identity?.let {
                runOnUiThread {
                    binding.identityView.setIdentityData(it)
                    binding.accountView.setDefault()
                    binding.accountView.visibility = View.VISIBLE
                    binding.btnSubmitAccount.isEnabled = it.status == IdentityStatus.DONE
                    binding.rlAccount.visibility = View.VISIBLE
                    binding.confirmButton.visibility = View.INVISIBLE
                    binding.progressLine.setFilledDots(4)
                    binding.progressLine.invalidate()
                    setActionBarTitle(R.string.identity_confirmed_confirm_account_submission_toolbar)
                    binding.tvHeader.text = getString(R.string.identity_confirmed_confirm_account_submission_title)
                    binding.infoTextview.text = getString(R.string.identity_confirmed_confirm_account_submission_text)
                }
            }
        }
    }

    private fun updateInfoText(isFirstIdentity: Boolean) {
        if (isFirstIdentity) {
            binding.infoTextview.setText(R.string.identity_confirmed_info_first)
        } else {
            binding.infoTextview.setText(R.string.identity_confirmed_info)
        }
    }
}

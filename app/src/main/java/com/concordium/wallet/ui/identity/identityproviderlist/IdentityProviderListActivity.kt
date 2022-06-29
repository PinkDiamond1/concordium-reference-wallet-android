package com.concordium.wallet.ui.identity.identityproviderlist

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.concordium.wallet.R
import com.concordium.wallet.core.arch.EventObserver
import com.concordium.wallet.core.security.BiometricPromptCallback
import com.concordium.wallet.data.model.IdentityProvider
import com.concordium.wallet.databinding.ActivityIdentityProviderListBinding
import com.concordium.wallet.ui.base.BaseActivity
import com.concordium.wallet.ui.identity.identityproviderlist.adapter.AdapterItem
import com.concordium.wallet.ui.identity.identityproviderlist.adapter.HeaderItem
import com.concordium.wallet.ui.identity.identityproviderlist.adapter.IdentityProviderAdapter
import com.concordium.wallet.ui.identity.identityproviderlist.adapter.IdentityProviderItem
import com.concordium.wallet.ui.identity.identityproviderpolicywebview.IdentityProviderPolicyWebviewActivity
import com.concordium.wallet.ui.identity.identityproviderwebview.IdentityProviderWebviewActivity
import com.concordium.wallet.uicore.dialog.AuthenticationDialogFragment
import javax.crypto.Cipher

class IdentityProviderListActivity : BaseActivity() {
    private lateinit var binding: ActivityIdentityProviderListBinding
    private lateinit var viewModel: IdentityProviderListViewModel
    private lateinit var identityProviderAdapter: IdentityProviderAdapter
    private lateinit var biometricPrompt: BiometricPrompt

    private var longTimeWaitingCountDownTimer =
        object : CountDownTimer(2 * 1000.toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {}

            override fun onFinish() {
                popup.showSnackbar(binding.rootLayout, R.string.new_account_please_wait)
            }
        }

    companion object {
        const val EXTRA_IDENTITY_CUSTOM_NAME = "EXTRA_IDENTITY_CUSTOM_NAME"
        const val EXTRA_ACCOUNT_CUSTOM_NAME = "EXTRA_ACCOUNT_CUSTOM_NAME"
    }
    //region Lifecycle
    //************************************************************

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIdentityProviderListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupActionBar(binding.toolbarLayout.toolbar, binding.toolbarLayout.toolbarTitle, R.string.identity_provider_list_title)

        val identityCustomName = intent.getStringExtra(EXTRA_IDENTITY_CUSTOM_NAME) as String
        val accountCustomName = intent.getStringExtra(EXTRA_ACCOUNT_CUSTOM_NAME) as String

        initializeViewModel()
        viewModel.initialize(identityCustomName, accountCustomName)
        initializeViews()
        viewModel.getIdentityProviders()
        viewModel.getGlobalInfo()
    }

    override fun onResume() {
        super.onResume()
        longTimeWaitingCountDownTimer.cancel()
    }

    //endregion

    //region Initialize
    //************************************************************

    private fun initializeViewModel() {
        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[IdentityProviderListViewModel::class.java]
        viewModel.waitingLiveData.observe(this, Observer<Boolean> { waiting ->
            waiting?.let {
                showWaiting(waiting || viewModel.waitingGlobalData.value!!)
            }
        })
        viewModel.waitingGlobalData.observe(this, Observer<Boolean> { waiting ->
            waiting?.let {
                showWaiting(waiting || viewModel.waitingLiveData.value!!)
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
                    showAuthentication()
                }
            }
        })
        viewModel.gotoIdentityProviderWebview.observe(this, object : EventObserver<Boolean>() {
            override fun onUnhandledEvent(value: Boolean) {
                if (value) {
                    gotoIdentityProviderWebview()
                    showWaiting(false)
                }
            }
        })
    }

    private fun initializeViews() {
        binding.includeProgress.progressLayout.visibility = View.GONE
        initializeList()
    }

    private fun initializeList() {
        val linearLayoutManager = LinearLayoutManager(this)
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = linearLayoutManager

        viewModel.identityProviderList.observe(this, Observer { list ->
            val items: MutableList<AdapterItem> = list.map { IdentityProviderItem(it) }.toMutableList()
            items.add(0, HeaderItem())
            val newlineSeperatedLinks = list.joinToString (separator = "<br><br>") { it -> "<a href=\"${it.ipInfo.ipDescription.url}\">${it.ipInfo.ipDescription.name}</a>" }
            identityProviderAdapter = IdentityProviderAdapter(this, newlineSeperatedLinks, items)
            binding.recyclerview.adapter = identityProviderAdapter
            setItemClickAdapter()
        })
    }

    private fun setItemClickAdapter() {
        identityProviderAdapter.setOnItemClickListener(object : IdentityProviderAdapter.OnItemClickListener {
            override fun onItemClicked(item: IdentityProvider) {
                viewModel.selectedIdentityVerificationItem(item)
            }

            override fun onItemActionClicked(item: IdentityProvider) {
                gotoIdentityProviderPolicyWebview(item)
            }
        })
    }

    //endregion

    //region Control/UI
    //************************************************************

    private fun gotoIdentityProviderWebview() {
        viewModel.getIdentityCreationData()?.let { identityCreationData ->
            val intent = Intent(this, IdentityProviderWebviewActivity::class.java)
            intent.putExtra(
                IdentityProviderWebviewActivity.EXTRA_IDENTITY_CREATION_DATA,
                identityCreationData
            )
            startActivity(intent)
        }
    }

    private fun gotoIdentityProviderPolicyWebview(identityProvider: IdentityProvider) {
        identityProvider.metadata
        val intent = Intent(this, IdentityProviderPolicyWebviewActivity::class.java)
        intent.putExtra(IdentityProviderPolicyWebviewActivity.EXTRA_URL, "https://google.com")
        startActivity(intent)
    }

    private fun showWaiting(waiting: Boolean) {
        if (waiting) {
            binding.includeProgress.progressLayout.visibility = View.VISIBLE
        } else {
            binding.includeProgress.progressLayout.visibility = View.GONE
        }
    }

    private fun showError(stringRes: Int) {
        popup.showSnackbar(binding.rootLayout, stringRes)
    }

    private fun showAuthentication() {
        if (viewModel.shouldUseBiometrics()) {
            showBiometrics()
        } else {
            showPasswordDialog()
        }
    }

    private fun showPasswordDialog() {
        val dialogFragment = AuthenticationDialogFragment()
        dialogFragment.setCallback(object : AuthenticationDialogFragment.Callback {
            override fun onCorrectPassword(password: String) {
                longTimeWaitingCountDownTimer.start()
                viewModel.continueWithPassword(password)
            }

            override fun onCancelled() {
            }
        })
        dialogFragment.show(
            supportFragmentManager,
            AuthenticationDialogFragment.AUTH_DIALOG_TAG
        )
    }

    //endregion

    //region Biometrics
    //************************************************************

    private fun showBiometrics() {
        biometricPrompt = createBiometricPrompt()

        val promptInfo = createPromptInfo()

        val cipher = viewModel.getCipherForBiometrics()
        if (cipher != null) {
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        }
    }

    private fun createBiometricPrompt(): BiometricPrompt {
        val executor = ContextCompat.getMainExecutor(this)

        val callback = object : BiometricPromptCallback() {
            override fun onNegativeButtonClicked() {
                showPasswordDialog()
            }

            override fun onAuthenticationSucceeded(cipher: Cipher) {
                longTimeWaitingCountDownTimer.start()
                viewModel.checkLogin(cipher)
            }
        }

        val biometricPrompt = BiometricPrompt(this, executor, callback)
        return biometricPrompt
    }

    private fun createPromptInfo(): BiometricPrompt.PromptInfo {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.auth_login_biometrics_dialog_title))
            .setConfirmationRequired(true)
            .setNegativeButtonText(getString(if (viewModel.usePasscode()) R.string.auth_login_biometrics_dialog_cancel_passcode else R.string.auth_login_biometrics_dialog_cancel_password))
            .build()
        return promptInfo
    }

    //endregion
}

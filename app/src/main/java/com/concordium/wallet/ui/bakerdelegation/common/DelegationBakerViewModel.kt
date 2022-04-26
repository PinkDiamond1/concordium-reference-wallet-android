package com.concordium.wallet.ui.bakerdelegation.common

import android.app.Application
import android.net.Uri
import android.text.TextUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.concordium.wallet.App
import com.concordium.wallet.DataFileProvider
import com.concordium.wallet.R
import com.concordium.wallet.core.arch.Event
import com.concordium.wallet.core.backend.BackendRequest
import com.concordium.wallet.core.crypto.CryptoLibrary
import com.concordium.wallet.core.security.KeystoreEncryptionException
import com.concordium.wallet.data.TransferRepository
import com.concordium.wallet.data.backend.repository.ProxyRepository
import com.concordium.wallet.data.cryptolib.CreateTransferInput
import com.concordium.wallet.data.cryptolib.CreateTransferOutput
import com.concordium.wallet.data.cryptolib.StorageAccountData
import com.concordium.wallet.data.model.*
import com.concordium.wallet.data.room.Transfer
import com.concordium.wallet.data.room.WalletDatabase
import com.concordium.wallet.data.util.FileUtil
import com.concordium.wallet.ui.common.BackendErrorHandler
import com.concordium.wallet.util.DateTimeUtil
import com.concordium.wallet.util.Log
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import javax.crypto.Cipher

class DelegationBakerViewModel(application: Application) : AndroidViewModel(application) {

    lateinit var bakerDelegationData: DelegationData
    private val proxyRepository = ProxyRepository()
    private val transferRepository: TransferRepository

    private var bakerPoolRequest: BackendRequest<BakerPoolStatus>? = null
    private var accountNonceRequest: BackendRequest<AccountNonce>? = null
    private var submitCredentialRequest: BackendRequest<SubmissionData>? = null
    private var transferSubmissionStatusRequest: BackendRequest<TransferSubmissionStatus>? = null

    companion object {
        const val FILE_NAME_BAKER_KEYS = "baker-credentials.json"
        const val EXTRA_DELEGATION_BAKER_DATA = "EXTRA_DELEGATION_BAKER_DATA"
    }

    private val _transactionSuccessLiveData = MutableLiveData<Boolean>()
    val transactionSuccessLiveData: LiveData<Boolean>
        get() = _transactionSuccessLiveData

    private val _waitingLiveData = MutableLiveData<Boolean>()
    val waitingLiveData: LiveData<Boolean>
        get() = _waitingLiveData

    private val _errorLiveData = MutableLiveData<Event<Int>>()
    val errorLiveData: LiveData<Event<Int>>
        get() = _errorLiveData

    private val _chainParametersLoadedLiveData = MutableLiveData<Boolean>()
    val chainParametersLoadedLiveData: LiveData<Boolean>
        get() = _chainParametersLoadedLiveData

    private val _showDetailedLiveData = MutableLiveData<Event<Boolean>>()
    val showDetailedLiveData: LiveData<Event<Boolean>>
        get() = _showDetailedLiveData

    private val _transactionFeeLiveData = MutableLiveData<Long>()
    val transactionFeeLiveData: LiveData<Long>
        get() = _transactionFeeLiveData

    private val _showAuthenticationLiveData = MutableLiveData<Event<Boolean>>()
    val showAuthenticationLiveData: LiveData<Event<Boolean>>
        get() = _showAuthenticationLiveData

    private val _bakerKeysLiveData = MutableLiveData<BakerKeys>()
    val bakerKeysLiveData: LiveData<BakerKeys>
        get() = _bakerKeysLiveData

    private val _fileSavedLiveData = MutableLiveData<Event<Int>>()
    val fileSavedLiveData: LiveData<Event<Int>>
        get() = _fileSavedLiveData

    init {
        val transferDao = WalletDatabase.getDatabase(application).transferDao()
        transferRepository = TransferRepository(transferDao)
    }

    fun initialize(delegationData: DelegationData) {
        this.bakerDelegationData = delegationData
    }

    fun restakeHasChanged(): Boolean {
        return bakerDelegationData.restake != bakerDelegationData.oldRestake
    }

    fun stakedAmountHasChanged(): Boolean {
        return bakerDelegationData.amount != bakerDelegationData.oldStakedAmount
    }

    fun poolHasChanged(): Boolean {
        if (bakerDelegationData.isLPool && bakerDelegationData.oldDelegationIsBaker != null && bakerDelegationData.oldDelegationIsBaker!!)
            return true
        if (bakerDelegationData.isBakerPool && bakerDelegationData.oldDelegationIsBaker != null && bakerDelegationData.oldDelegationIsBaker == false)
            return true
        if (bakerDelegationData.isBakerPool && bakerDelegationData.oldDelegationIsBaker == true && bakerDelegationData.poolId != bakerDelegationData.oldDelegationTargetPoolId?.toString() ?: "")
            return true
        return false
    }

    fun isBakerPool(): Boolean {
        return bakerDelegationData.account?.accountDelegation?.delegationTarget?.delegateType == DelegationTarget.TYPE_DELEGATE_TO_BAKER
    }

    fun isLPool(): Boolean {
        return bakerDelegationData.account?.accountDelegation?.delegationTarget?.delegateType == DelegationTarget.TYPE_DELEGATE_TO_L_POOL
    }

    fun isOpenBaker(): Boolean {
        return bakerDelegationData.isOpenBaker // bakerData.account?.accountBaker?.bakerId == null
    }

    fun isClosedBaker(): Boolean {
        return bakerDelegationData.isClosedBaker // bakerData.account?.accountBaker?.bakerId != null
    }

    fun isUpdatingDelegation(): Boolean {
        bakerDelegationData.account?.accountDelegation?.let { return it.stakedAmount.isNotBlank() }
        return false
    }

    fun isUpdatingBaker(): Boolean {
        return false
    }

    fun isInCoolDown(): Boolean {
        return bakerDelegationData.account?.accountDelegation?.pendingChange != null
    }

    fun atDisposal(): Long {
        return (bakerDelegationData.account?.finalizedBalance ?: 0) - (bakerDelegationData.account?.accountDelegation?.stakedAmount?.toLong() ?: 0)
    }

    fun selectBakerPool() {
        this.bakerDelegationData.isLPool = false
        this.bakerDelegationData.isBakerPool = true
    }

    fun selectLPool() {
        this.bakerDelegationData.isLPool = true
        this.bakerDelegationData.isBakerPool = false
        this.bakerDelegationData.poolId = ""
    }

    fun selectOpenBaker() {
        bakerDelegationData.isOpenBaker = true
        bakerDelegationData.isClosedBaker = false
    }

    fun selectClosedBaker() {
        bakerDelegationData.isOpenBaker = false
        bakerDelegationData.isClosedBaker = true
    }

    fun markRestake(restake: Boolean) {
        this.bakerDelegationData.restake = restake
        loadTransactionFee(true)
    }

    fun setPoolID(id: String) {
        bakerDelegationData.poolId = id
    }

    fun getPoolId(): String {
        return bakerDelegationData.poolId
    }

    fun validatePoolId() {
        if (bakerDelegationData.isLPool) {
            _showDetailedLiveData.value = Event(true)
        } else {
            _waitingLiveData.value = true
            bakerPoolRequest?.dispose()
            bakerPoolRequest = proxyRepository.getBakerPool(getPoolId(),
                {
                    bakerDelegationData.bakerPoolStatus = it
                    _waitingLiveData.value = false
                    if (bakerDelegationData.bakerPoolStatus?.poolInfo?.openStatus == BakerPoolInfo.OPEN_STATUS_CLOSED_FOR_NEW) {
                        _errorLiveData.value = Event(R.string.delegation_register_delegation_pool_id_closed)
                    } else {
                        _showDetailedLiveData.value = Event(true)
                    }
                },
                {
                    _waitingLiveData.value = false
                    _errorLiveData.value = Event(R.string.delegation_register_delegation_pool_id_error)
                }
            )
        }
    }

    fun loadTransactionFee(notifyObservers: Boolean) {

        val type = when(bakerDelegationData.type) {
            DelegationData.TYPE_REGISTER_DELEGATION -> ProxyRepository.REGISTER_DELEGATION
            DelegationData.TYPE_UPDATE_DELEGATION -> ProxyRepository.UPDATE_DELEGATION
            DelegationData.TYPE_REMOVE_DELEGATION -> ProxyRepository.REMOVE_DELEGATION
            else -> ProxyRepository.REGISTER_DELEGATION
        }

        val amount = when(bakerDelegationData.type) {
            DelegationData.TYPE_UPDATE_DELEGATION -> bakerDelegationData.amount
            else -> null
        }

        val restake = when(bakerDelegationData.type) {
            DelegationData.TYPE_UPDATE_DELEGATION -> bakerDelegationData.restake
            else -> null
        }

        val targetChange: Boolean? = if (poolHasChanged()) true else null

        proxyRepository.getTransferCost(type,
            null,
            amount,
            restake,
            bakerDelegationData.isLPool,
            targetChange,
            {
                bakerDelegationData.energy = it.energy
                bakerDelegationData.cost = it.cost.toLong()
                if (notifyObservers)
                    _transactionFeeLiveData.value = bakerDelegationData.cost
            },
            {
                handleBackendError(it)
            }
        )
    }

    fun loadChainParameters() {
        proxyRepository.getChainParameters(
            {
                bakerDelegationData.chainParameters = it
                _chainParametersLoadedLiveData.value = true
            },
            {
                _chainParametersLoadedLiveData.value = false
                handleBackendError(it)
            }
        )
    }

    private fun handleBackendError(throwable: Throwable) {
        Log.e("Backend request failed", throwable)
        _errorLiveData.value = Event(BackendErrorHandler.getExceptionStringRes(throwable))
    }

    fun usePasscode(): Boolean {
        return App.appCore.getCurrentAuthenticationManager().usePasscode()
    }

    fun delegateAmount() {
        if (bakerDelegationData.amount == null) {
            _errorLiveData.value = Event(R.string.app_error_general)
            return
        }
        getAccountNonce()
    }

    private fun getAccountNonce() {
        _waitingLiveData.value = true
        accountNonceRequest?.dispose()
        accountNonceRequest = bakerDelegationData.account?.let { account ->
            proxyRepository.getAccountNonce(account.address,
                { accountNonce ->
                    bakerDelegationData.accountNonce = accountNonce
                    _showAuthenticationLiveData.value = Event(true)
                    _waitingLiveData.value = false
                },
                {
                    _waitingLiveData.value = false
                    handleBackendError(it)
                }
            )
        }
    }

    fun shouldUseBiometrics(): Boolean {
        return App.appCore.getCurrentAuthenticationManager().useBiometrics()
    }

    fun getCipherForBiometrics(): Cipher? {
        try {
            val cipher =
                App.appCore.getCurrentAuthenticationManager().initBiometricsCipherForDecryption()
            if (cipher == null) {
                _errorLiveData.value = Event(R.string.app_error_keystore_key_invalidated)
            }
            return cipher
        } catch (e: KeystoreEncryptionException) {
            _errorLiveData.value = Event(R.string.app_error_keystore)
            return null
        }
    }

    fun continueWithPassword(password: String) = viewModelScope.launch {
        _waitingLiveData.value = true
        decryptAndContinue(password)
    }

    fun checkLogin(cipher: Cipher) = viewModelScope.launch {
        _waitingLiveData.value = true
        val password =
            App.appCore.getCurrentAuthenticationManager().checkPasswordInBackground(cipher)
        if (password != null) {
            decryptAndContinue(password)
        } else {
            _errorLiveData.value = Event(R.string.app_error_encryption)
            _waitingLiveData.value = false
        }
    }

    private suspend fun decryptAndContinue(password: String) {
        // Decrypt the private data
        Log.d("decryptAndContinue")
        bakerDelegationData.account?.let { account ->
            val storageAccountDataEncrypted = account.encryptedAccountData
            if (TextUtils.isEmpty(storageAccountDataEncrypted)) {
                _errorLiveData.value = Event(R.string.app_error_general)
                _waitingLiveData.value = false
                return
            }
            val decryptedJson = App.appCore.getCurrentAuthenticationManager().decryptInBackground(password, storageAccountDataEncrypted)

            if (decryptedJson != null) {
                val credentialsOutput = App.appCore.gson.fromJson(decryptedJson, StorageAccountData::class.java)
                if (bakerDelegationData.isBakerFlow())
                    createBaking(credentialsOutput.accountKeys, credentialsOutput.encryptionSecretKey)
                else
                    createDelegation(credentialsOutput.accountKeys, credentialsOutput.encryptionSecretKey)
            } else {
                _errorLiveData.value = Event(R.string.app_error_encryption)
                _waitingLiveData.value = false
            }
        }
    }

    private suspend fun createBaking(keys: AccountData, encryptionSecretKey: String?) {

    }

    private suspend fun createDelegation(keys: AccountData, encryptionSecretKey: String?) {
        val from = bakerDelegationData.account?.address
        val expiry = (DateTimeUtil.nowPlusMinutes(10).time) / 1000 // Expiry should me now + 10 minutes (in seconds)
        val energy = bakerDelegationData.energy
        val nonce = bakerDelegationData.accountNonce

        var encryptionSK: String? = null
        if (bakerDelegationData.type != DelegationData.TYPE_REMOVE_DELEGATION)
            encryptionSK = encryptionSecretKey

        var capital: String? = null
        if (stakedAmountHasChanged())
            capital = bakerDelegationData.amount.toString()

        var restakeEarnings: Boolean? = null
        if (bakerDelegationData.type != DelegationData.TYPE_REMOVE_DELEGATION && restakeHasChanged())
            restakeEarnings = bakerDelegationData.restake

        var delegationTarget: DelegationTarget? = null
        if (bakerDelegationData.type == DelegationData.TYPE_REGISTER_DELEGATION) {
            delegationTarget = if (bakerDelegationData.isBakerPool)
                DelegationTarget(DelegationTarget.TYPE_DELEGATE_TO_BAKER, bakerDelegationData.poolId.toLong())
            else
                DelegationTarget(DelegationTarget.TYPE_DELEGATE_TO_L_POOL, null)
        } else if (bakerDelegationData.type == DelegationData.TYPE_UPDATE_DELEGATION) {
            if (poolHasChanged()) {
                delegationTarget = if (bakerDelegationData.isBakerPool)
                    DelegationTarget(DelegationTarget.TYPE_DELEGATE_TO_BAKER, bakerDelegationData.poolId.toLong())
                else
                    DelegationTarget(DelegationTarget.TYPE_DELEGATE_TO_L_POOL, null)
            }
        }

        if (from == null || nonce == null || energy == null) {
            _errorLiveData.value = Event(R.string.app_error_general)
            _waitingLiveData.value = false
            return
        }

        val transferInput = CreateTransferInput(
            from,
            keys,
            null,
            expiry,
            null,
            energy,
            nonce.nonce,
            null,
            null,
            null,
            encryptionSK,
            null,
            capital,
            restakeEarnings,
            delegationTarget
        )

        val output = App.appCore.cryptoLibrary.createTransfer(transferInput, CryptoLibrary.CONFIGURE_DELEGATION_TRANSACTION)

        if (output == null) {
            _errorLiveData.value = Event(R.string.app_error_lib)
            _waitingLiveData.value = false
        } else {
            viewModelScope.launch {
                submitTransfer(output)
            }
        }
    }

    private fun submitTransfer(transfer: CreateTransferOutput) {
        _waitingLiveData.value = true
        submitCredentialRequest?.dispose()
        submitCredentialRequest = proxyRepository.submitTransfer(transfer,
            {
                Log.d("Success:"+it)
                bakerDelegationData.submissionId = it.submissionId
                submissionStatus()
                // Do not disable waiting state yet
            },
            {
                _waitingLiveData.value = false
                it.printStackTrace()
                handleBackendError(it)
            }
        )
    }

    private fun submissionStatus() {
        _waitingLiveData.value = true
        transferSubmissionStatusRequest?.dispose()
        transferSubmissionStatusRequest = bakerDelegationData.submissionId?.let { submissionId ->
            proxyRepository.getTransferSubmissionStatus(submissionId,
                { transferSubmissionStatus ->
                    bakerDelegationData.transferSubmissionStatus = transferSubmissionStatus
                    finishTransferCreation()
                    // Do not disable waiting state yet
                },
                {
                    _waitingLiveData.value = false
                    _errorLiveData.value = Event(BackendErrorHandler.getExceptionStringRes(it))
                }
            )
        }
    }

    private fun finishTransferCreation() {
        val createdAt = Date().time

        val accountId = bakerDelegationData.account?.id
        val amount = bakerDelegationData.amount
        val fromAddress = bakerDelegationData.account?.address
        val submissionId = bakerDelegationData.submissionId
        val transferSubmissionStatus = bakerDelegationData.transferSubmissionStatus
        val cost = bakerDelegationData.cost
        val expiry = (DateTimeUtil.nowPlusMinutes(10).time) / 1000

        if (transferSubmissionStatus == null || expiry == null || cost == null || accountId == null || amount == null || fromAddress == null || submissionId == null) {
            _errorLiveData.value = Event(R.string.app_error_general)
            _waitingLiveData.value = false
            return
        }

        val transfer = Transfer(
            0,
            accountId,
            cost,
            0,
            fromAddress,
            fromAddress,
            expiry,
            "",
            createdAt,
            submissionId,
            TransactionStatus.UNKNOWN,
            TransactionOutcome.UNKNOWN,
            TransactionType.LOCAL_DELEGATIONORBAKER,
            //but amount is negative so it is listed as incoming positive
            null,
            0,
            null
        )
        saveNewTransfer(transfer)
    }

    private fun saveNewTransfer(transfer: Transfer) = viewModelScope.launch {
        transferRepository.insert(transfer)
        _waitingLiveData.value = false
        _transactionSuccessLiveData.value = true
    }

    fun generateKeys() {
        viewModelScope.launch {
            val bakerKeys = App.appCore.cryptoLibrary.generateBakerKeys()
            if (bakerKeys == null) {
                _errorLiveData.value = Event(R.string.app_error_lib)
            } else {
                bakerDelegationData.electionVerifyKey = bakerKeys.electionVerifyKey
                bakerDelegationData.signatureVerifyKey = bakerKeys.signatureVerifyKey
                bakerDelegationData.aggregationVerifyKey = bakerKeys.aggregationVerifyKey
                _bakerKeysLiveData.value = bakerKeys
            }
        }
    }

    fun saveFileToLocalFolder(destinationUri: Uri) {
        bakerKeysJson()?.let { bakerKeysJson ->
            viewModelScope.launch {
                FileUtil.writeFile(destinationUri, FILE_NAME_BAKER_KEYS, bakerKeysJson)
                _fileSavedLiveData.value = Event(R.string.export_backup_saved_local)
            }
        }
    }

    fun bakerKeysJson(): String? {
        _bakerKeysLiveData.value?.let { bakerKeys ->
            return if (bakerKeys.toString().isNotEmpty()) App.appCore.gson.toJson(bakerKeys) else null
        }
        return null
    }

    fun getTempFileWithPath(): Uri = Uri.parse("content://" + DataFileProvider.AUTHORITY + File.separator.toString() + FILE_NAME_BAKER_KEYS)
}

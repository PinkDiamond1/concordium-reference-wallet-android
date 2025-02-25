package com.concordium.wallet.data.room

import androidx.room.*
import com.concordium.wallet.App
import com.concordium.wallet.data.model.*
import com.concordium.wallet.data.room.typeconverter.AccountTypeConverters
import com.google.gson.JsonObject
import java.io.Serializable
import kotlin.math.max

@Entity(tableName = "account_table", indices = [Index(value = ["address"], unique = true)])
@TypeConverters(AccountTypeConverters::class)
data class Account(
    @PrimaryKey(autoGenerate = true)
    var id: Int,

    @ColumnInfo(name = "identity_id")
    val identityId: Int,

    var name: String,

    var address: String,

    @ColumnInfo(name = "submission_id")
    var submissionId: String,

    @ColumnInfo(name = "transaction_status")
    var transactionStatus: TransactionStatus,

    @ColumnInfo(name = "encrypted_account_data")
    var encryptedAccountData: String,

    @ColumnInfo(name = "revealed_attributes")
    var revealedAttributes: List<IdentityAttribute>,

    var credential: CredentialWrapper?,

    @ColumnInfo(name = "finalized_balance")
    var finalizedBalance: Long = 0,

    @ColumnInfo(name = "current_balance")
    var currentBalance: Long = 0,

    @ColumnInfo(name = "total_balance")
    var totalBalance: Long = 0,

    @ColumnInfo(name = "total_unshielded_balance")
    var totalUnshieldedBalance: Long = 0,

    @ColumnInfo(name = "total_shielded_balance")
    var totalShieldedBalance: Long = 0,

    @ColumnInfo(name = "finalized_encrypted_balance")
    var finalizedEncryptedBalance: AccountEncryptedAmount?,

    @ColumnInfo(name = "current_encrypted_balance")
    var currentEncryptedBalance: AccountEncryptedAmount?,

    @ColumnInfo(name = "current_balance_status")
    var encryptedBalanceStatus: ShieldedAccountEncryptionStatus = ShieldedAccountEncryptionStatus.ENCRYPTED,

    @ColumnInfo(name = "total_staked")
    var totalStaked: Long = 0,

    @ColumnInfo(name = "total_at_disposal")
    var totalAtDisposal: Long = 0,

    @ColumnInfo(name = "read_only")
    var readOnly: Boolean = false,

    @ColumnInfo(name = "finalized_account_release_schedule")
    var finalizedAccountReleaseSchedule: AccountReleaseSchedule?,

    @ColumnInfo(name = "baker_id")
    var bakerId: Long? = null,

    @ColumnInfo(name = "account_delegation")
    var accountDelegation: AccountDelegation? = null,

    @ColumnInfo(name = "account_baker")
    var accountBaker: AccountBaker? = null,

    @ColumnInfo(name = "accountIndex")
    var accountIndex: Int? = null,

    @ColumnInfo(name = "cred_number")
    var credNumber: Int) : Serializable {

    companion object {
        fun getDefaultName(address: String): String {
            if (address.length >= 8) {
                return "${address.subSequence(0, 4)}...${address.substring(address.length - 4)}"
            }
            return ""
        }
    }

    fun isInitial(): Boolean {
        if (readOnly || isBaking() || isDelegating()) {
            return false
        }
        val credential = this.credential ?: return true
        val gson = App.appCore.gson
        val credentialValueJsonObject = gson.fromJson(credential.value.json, JsonObject::class.java)
        if (credentialValueJsonObject["type"]?.asString == "initial") {
            return true
        }
        if (credentialValueJsonObject.getAsJsonObject("credential")?.get("type")?.asString == "initial")
            return true
        return false
    }

    fun getAccountName(): String {
        return if (readOnly) {
            address.substring(0, 8)
        } else {
            name
        }
    }

    fun isBaking(): Boolean {
        return accountBaker != null
    }

    fun isDelegating(): Boolean {
        return accountDelegation != null
    }

    fun getAtDisposalWithoutStakedOrScheduled(totalBalance: Long): Long {
        val stakedAmount: Long = accountDelegation?.stakedAmount?.toLong() ?: accountBaker?.stakedAmount?.toLong() ?: 0
        val scheduledTotal: Long = finalizedAccountReleaseSchedule?.total?.toLong() ?: 0
        val subtract = if (stakedAmount in 1..scheduledTotal)
            scheduledTotal
        else if (stakedAmount > 0 && stakedAmount > scheduledTotal)
            stakedAmount
        else if (stakedAmount == 0L && scheduledTotal > 0)
            scheduledTotal
        else
            0
        return max(totalBalance - subtract, 0)
    }

    fun getAtDisposal(): Long {
        return finalizedBalance - (finalizedAccountReleaseSchedule?.total?.toLong() ?: 0)
    }
}

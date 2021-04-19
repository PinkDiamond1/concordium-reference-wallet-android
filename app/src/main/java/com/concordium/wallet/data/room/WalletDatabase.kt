package com.concordium.wallet.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.concordium.wallet.data.room.WalletDatabase.Companion.VERSION_NUMBER
import com.concordium.wallet.data.room.typeconverter.GlobalTypeConverters

@Database(
    entities = arrayOf(Identity::class, Account::class, Transfer::class, Recipient::class, EncryptedAmount::class),
    version = VERSION_NUMBER,
    exportSchema = true
)
@TypeConverters(GlobalTypeConverters::class)
public abstract class WalletDatabase : RoomDatabase() {

    abstract fun identityDao(): IdentityDao
    abstract fun accountDao(): AccountDao
    abstract fun transferDao(): TransferDao
    abstract fun recipientDao(): RecipientDao
    abstract fun encryptedAmountDao(): EncryptedAmountDao

    companion object {

        const val VERSION_NUMBER = 3

        // Singleton prevents multiple instances of database opening at the same time.
        @Volatile
        private var INSTANCE: WalletDatabase? = null

        fun getDatabase(context: Context): WalletDatabase {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WalletDatabase::class.java,
                    "wallet_database"
                ).build()
                INSTANCE = instance
                return instance
            }
        }


    }
}
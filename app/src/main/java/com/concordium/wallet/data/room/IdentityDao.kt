package com.concordium.wallet.data.room

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface IdentityDao {

    @Query("SELECT COUNT(id) FROM identity_table")
    suspend fun getCount(): Int

    @Query("SELECT * FROM identity_table ORDER BY id ASC")
    fun getAllAsLiveData(): LiveData<List<Identity>>

    @Query("SELECT * FROM identity_table ORDER BY id ASC")
    suspend fun getAll(): List<Identity>

    @Query("SELECT * FROM identity_table where status='done' ORDER BY id ASC")
    suspend fun getAllDone(): List<Identity>

    @Query("SELECT * FROM identity_table where status='done' ORDER BY id ASC")
    fun getAllDoneAsLiveData(): LiveData<List<Identity>>

    @Query("SELECT * FROM identity_table WHERE id = :id")
    suspend fun findById(id: Int): Identity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg identity: Identity): List<Long>

    @Update
    suspend fun update(vararg identity: Identity)

    @Delete
    suspend fun delete(identity: Identity)

    @Query("DELETE FROM identity_table")
    suspend fun deleteAll()
}
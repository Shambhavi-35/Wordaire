package com.example.wordaire.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RealmDao {
    @Query("SELECT * FROM realms")
    fun getAllRealms(): Flow<List<Realm>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRealms(realms: List<Realm>)

    @Query("UPDATE realms SET isCompleted = 1 WHERE id = :realmId")
    suspend fun completeRealm(realmId: String)

    @Query("UPDATE realms SET isUnlocked = 1 WHERE id = :realmId")
    suspend fun unlockRealm(realmId: String)
}

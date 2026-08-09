package com.example.wordaire.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LevelDao {
    @Query("SELECT * FROM levels WHERE categoryId = :categoryId ORDER BY levelNumber ASC")
    fun getLevelsByCategory(categoryId: String): Flow<List<Level>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLevels(levels: List<Level>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLevel(level: Level)

    @Query("UPDATE levels SET stars = :stars WHERE id = :levelId")
    suspend fun updateStars(levelId: String, stars: Int)

    @Query("UPDATE levels SET isLocked = :isLocked WHERE id = :levelId")
    suspend fun updateLockStatus(levelId: String, isLocked: Boolean)

    // Used to avoid downgrading a level's stars on a worse retry.
    @Query("SELECT stars FROM levels WHERE id = :levelId")
    suspend fun getStars(levelId: String): Int?

    // Source of truth for a category's "completed" count — recalculated
    // from real level data instead of manually incremented, so it can
    // never drift out of sync.
    @Query("SELECT COUNT(*) FROM levels WHERE categoryId = :categoryId AND stars > 0")
    suspend fun getCompletedCount(categoryId: String): Int

    @Query("SELECT COUNT(*) FROM levels WHERE categoryId = :categoryId")
    suspend fun getTotalCount(categoryId: String): Int

    // Used to confirm whether a "next level" row actually exists before
    // trying to unlock it (mainly useful for logging/debugging).
    @Query("SELECT EXISTS(SELECT 1 FROM levels WHERE id = :levelId)")
    suspend fun levelExists(levelId: String): Boolean

    @Query("SELECT SUM(stars) FROM levels")
    suspend fun getTotalStars(): Int

    @Query("SELECT COUNT(*) FROM levels WHERE stars > 0")
    suspend fun getTotalLevelsCompleted(): Int
}
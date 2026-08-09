package com.example.wordaire.data

import android.util.Log

/**
 * Central place for all progress/unlock logic. Called once a level is
 * completed. Keeps GameplayFragment from having to know about category
 * ordering, unlock cascades, or star-downgrade rules.
 */
class GameProgressManager(private val db: WordaireDatabase) {

    companion object {
        private const val TAG = "GameProgress"

        // Ordering of categories for unlocking
        val CATEGORY_ORDER = listOf(
            "living_world",
            "infinite_mode",
            "arts_culture",
            "around_globe",
            "active_science"
        )
    }

    /**
     * Call this right after a level's win condition is met.
     *
     * @return the number of stars actually saved (may differ from [stars]
     * if the player already had a better result and this was a worse retry).
     */
    suspend fun completeLevel(categoryId: String, levelId: String, levelNumber: Int, stars: Int): Int {
        val levelDao = db.levelDao()
        val categoryDao = db.categoryDao()

        // 1. Never let a worse retry overwrite a better previous result.
        val previousStars = levelDao.getStars(levelId) ?: 0
        val starsToSave = maxOf(previousStars, stars)
        levelDao.updateStars(levelId, starsToSave)

        // 2. Unlock the next level within the same category, if it exists.
        val nextLevelNumber = levelNumber + 1
        val nextLevelId = "${categoryId}_$nextLevelNumber"
        
        if (levelDao.levelExists(nextLevelId)) {
            levelDao.updateLockStatus(nextLevelId, false)
        } else {
            Log.d(TAG, "No next level ($nextLevelId) — this was the last level in $categoryId")
        }

        // 3. Recalculate this category's completed count from real level
        //    data, and push it to the Category row so the "X/20" badge
        //    and progress bar update automatically via the existing Flow.
        val completedCount = levelDao.getCompletedCount(categoryId)
        categoryDao.updateProgress(categoryId, completedCount)

        // 4. Decide if this category counts as "done" for the purpose of
        //    unlocking the next category in CATEGORY_ORDER.
        val totalCount = levelDao.getTotalCount(categoryId)
        val categoryDone = totalCount > 0 && completedCount >= totalCount

        if (categoryDone) {
            unlockNextCategory(categoryId)
        }

        return starsToSave
    }

    private suspend fun unlockNextCategory(finishedCategoryId: String) {
        val currentIndex = CATEGORY_ORDER.indexOf(finishedCategoryId)
        if (currentIndex == -1 || currentIndex >= CATEGORY_ORDER.lastIndex) {
            return // unknown category, or already the last one — nothing to unlock
        }
        val nextCategoryId = CATEGORY_ORDER[currentIndex + 1]
        db.categoryDao().updateLockStatus(nextCategoryId, false)

        // Also unlock the first level of the next category
        val firstLevelId = "${nextCategoryId}_1"
        db.levelDao().updateLockStatus(firstLevelId, false)
        
        Log.d(TAG, "Unlocked next category: $nextCategoryId and its first level")
    }
}

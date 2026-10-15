package com.example.wordaire.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Category::class, Level::class], version = 7, exportSchema = false)
abstract class WordaireDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun levelDao(): LevelDao

    companion object {
        @Volatile
        private var INSTANCE: WordaireDatabase? = null

        fun getDatabase(context: Context): WordaireDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WordaireDatabase::class.java,
                    "wordaire_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Using the database parameter to get a DAO if needed, 
                        // or just letting the first access handle it.
                    }
                })
                .build()
                INSTANCE = instance
                
                // Ensure population on first run or if missing
                CoroutineScope(Dispatchers.IO).launch {
                    val count = instance.categoryDao().getCategoryCount()
                    if (count == 0) {
                        populateDatabase(instance)
                    } else if (instance.categoryDao().getCategory("mathematics") == null) {
                        val mathCategory = Category("mathematics", "Mathematics", "Addition, subtraction, division, multiplication & roots", 20, 0, false)
                        instance.categoryDao().insertCategory(mathCategory)
                        val levels = mutableListOf<Level>()
                        for (i in 1..20) {
                            levels.add(Level("mathematics_$i", "mathematics", i, i > 1, 0))
                        }
                        instance.levelDao().insertLevels(levels)
                    }
                }

                instance
            }
        }

        private suspend fun populateDatabase(db: WordaireDatabase) {
            val categories = listOf(
                Category("living_world", "Living World", "Animals, food & wild places", 20, 0, false),
                Category("mathematics", "Mathematics", "Addition, subtraction, division, multiplication & roots", 20, 0, false),
                Category("infinite_mode", "Infinite Mode", "Endless word matching fun!", 999, 0, false),
                Category("arts_culture", "Arts & Culture", "Music, art & creative minds", 20, 0, true),
                Category("around_globe", "Around the Globe", "Countries, nature & geography", 20, 0, true),
                Category("active_science", "Active & Science", "Sports, science & discovery", 20, 0, true)
            )
            db.categoryDao().insertCategories(categories)

            val levels = mutableListOf<Level>()
            categories.forEach { category ->
                val catId = category.id
                val maxLevels = category.totalLevels
                for (i in 1..maxLevels) {
                    // Unlock only the first level of unlocked categories
                    val isLocked = category.isLocked || i > 1
                    levels.add(Level("${catId}_$i", catId, i, isLocked, 0))
                }
            }
            db.levelDao().insertLevels(levels)
        }
    }
}

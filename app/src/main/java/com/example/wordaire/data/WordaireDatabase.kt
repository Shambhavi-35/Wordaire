package com.example.wordaire.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Category::class, Level::class], version = 6, exportSchema = false)
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
                
                // Ensure population on first run or if empty
                CoroutineScope(Dispatchers.IO).launch {
                    val count = instance.categoryDao().getCategoryCount()
                    if (count == 0) {
                        populateDatabase(instance)
                    }
                }

                instance
            }
        }

        private suspend fun populateDatabase(db: WordaireDatabase) {
            val categories = listOf(
                Category("living_world", "Wild Kingdom", "Animals, food & wild places", 20, 0, false),
                Category("infinite_mode", "Endless Odyssey", "Endless word matching fun!", 99, 0, true),
                Category("arts_culture", "Creative Canvas", "Music, art & creative minds", 20, 0, true),
                Category("around_globe", "Globe Trekker", "Countries, nature & geography", 20, 0, true),
                Category("active_science", "Brain Lab", "Sports, science & discovery", 20, 0, true)
            )
            db.categoryDao().insertCategories(categories)

            val levels = mutableListOf<Level>()
            categories.forEach { category ->
                val catId = category.id
                val maxLevels = category.totalLevels
                for (i in 1..maxLevels) {
                    // Unlock only the first level of the first category
                    val isLocked = !(catId == "living_world" && i == 1)
                    levels.add(Level("${catId}_$i", catId, i, isLocked, 0))
                }
            }
            db.levelDao().insertLevels(levels)
        }
    }
}

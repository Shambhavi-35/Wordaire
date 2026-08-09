package com.example.wordaire.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val totalLevels: Int,
    val completedLevels: Int,
    val isLocked: Boolean
)

@Entity(tableName = "levels")
data class Level(
    @PrimaryKey val id: String,
    val categoryId: String,
    val levelNumber: Int,
    val isLocked: Boolean,
    val stars: Int
)

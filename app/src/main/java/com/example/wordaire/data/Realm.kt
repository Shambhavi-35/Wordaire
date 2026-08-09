package com.example.wordaire.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "realms")
data class Realm(
    @PrimaryKey val id: String,
    val name: String,
    val isUnlocked: Boolean,
    val isCompleted: Boolean,
    val colorHex: String
)

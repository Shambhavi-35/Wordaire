package com.example.wordaire.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Destination {
    @Serializable
    data object Gameplay : Destination
}

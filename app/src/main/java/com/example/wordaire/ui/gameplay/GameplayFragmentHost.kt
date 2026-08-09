package com.example.wordaire.ui.gameplay

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.navigation.fragment.NavHostFragment
import com.example.wordaire.databinding.ActivityXmlHostBinding

@Composable
fun GameplayFragmentHost(onNavigateBack: () -> Unit) {
    AndroidViewBinding(
        factory = ActivityXmlHostBinding::inflate,
        modifier = Modifier.fillMaxSize()
    ) {
        // No additional configuration needed here as NavHostFragment handles the graph
    }
}

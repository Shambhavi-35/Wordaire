package com.example.wordaire.ui.levels

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wordaire.R
import com.example.wordaire.data.WordaireDatabase
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LevelSelectionFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LevelAdapter
    private lateinit var categoryTitle: TextView
    private lateinit var backButton: ImageButton

    private var categoryId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_level_selection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        categoryId = arguments?.getString("categoryId") ?: ""
        val categoryName = arguments?.getString("categoryName") ?: "Levels"

        if (categoryId.isEmpty()) {
            Log.e("LevelSelection", "No categoryId passed in arguments")
            findNavController().popBackStack()
            return
        }

        categoryTitle = view.findViewById(R.id.categoryTitle)
        categoryTitle.text = categoryName

        backButton = view.findViewById(R.id.backButton)
        backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        recyclerView = view.findViewById(R.id.levelRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 4)
        recyclerView.setHasFixedSize(true)

        adapter = LevelAdapter { level ->
            if (level.isLocked) return@LevelAdapter // safety net, UI already blocks this
            val bundle = Bundle().apply {
                putString("levelId", level.id)
                putInt("levelNumber", level.levelNumber)
            }
            findNavController().navigate(R.id.action_levelSelectionFragment_to_gameplayFragment, bundle)
        }
        recyclerView.adapter = adapter

        // viewLifecycleOwner.lifecycleScope: the Flow collection is cancelled
        // as soon as this fragment's VIEW is destroyed (e.g. navigating away
        // or to gameplay and back), instead of only when the fragment
        // instance itself is destroyed. This is what lets the level grid
        // reliably refresh with the newly unlocked level every time you
        // come back from gameplay — the old collector doesn't linger and
        // the new one starts collecting fresh, current DB state right away.
        val db = WordaireDatabase.getDatabase(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            db.levelDao().getLevelsByCategory(categoryId)
                .catch { e ->
                    Log.e("LevelSelection", "Failed to load levels for $categoryId", e)
                }
                .collectLatest { levels ->
                    Log.d("LevelSelection", "Loaded ${levels.size} levels for $categoryId")
                    adapter.submitList(levels)
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recyclerView.adapter = null
    }
}
package com.example.wordaire.ui.categories

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wordaire.R
import com.example.wordaire.data.WordaireDatabase
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CategorySelectionFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CategoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_category_selection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.categoryRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.setHasFixedSize(true)

        adapter = CategoryAdapter { category ->
            if (category.isLocked) return@CategoryAdapter // safety net, UI already blocks this
            val bundle = Bundle().apply {
                putString("categoryId", category.id)
                putString("categoryName", category.name)
            }
            findNavController().navigate(
                R.id.action_categorySelectionFragment_to_levelSelectionFragment,
                bundle
            )
        }
        recyclerView.adapter = adapter

        // Use viewLifecycleOwner so the Flow collection is cancelled when the
        // view is destroyed, not just when the fragment instance dies. This
        // avoids leaking a collector that tries to touch a dead RecyclerView.
        val db = WordaireDatabase.getDatabase(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            db.categoryDao().getAllCategories()
                .catch { e ->
                    Log.e("CategorySelection", "Failed to load categories", e)
                }
                .collectLatest { categories ->
                    Log.d("CategorySelection", "Received ${categories.size} categories")
                    adapter.submitList(categories)
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recyclerView.adapter = null // avoid holding a stale adapter reference
    }
}
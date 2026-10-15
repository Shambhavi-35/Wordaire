package com.example.wordaire.ui.categories

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wordaire.R
import com.example.wordaire.data.Category
import com.google.android.material.card.MaterialCardView

class CategoryAdapter(private val onCategoryClick: (Category) -> Unit) :
    ListAdapter<Category, CategoryAdapter.CategoryViewHolder>(CategoryDiffCallback()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view, onCategoryClick)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CategoryViewHolder(itemView: View, private val onCategoryClick: (Category) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.categoryCard)
        private val name: TextView = itemView.findViewById(R.id.categoryName)
        private val icon: ImageView = itemView.findViewById(R.id.categoryIcon)
        private val description: TextView = itemView.findViewById(R.id.categoryDescription)
        private val progressText: TextView = itemView.findViewById(R.id.progressText)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.categoryProgress)
        private val lockContainer: LinearLayout = itemView.findViewById(R.id.lockContainer)

        fun bind(category: Category) {
            name.text = category.name
            description.text = category.description
            progressText.text = "${category.completedLevels}/${category.totalLevels}"
            progressBar.max = category.totalLevels
            progressBar.progress = category.completedLevels

            val iconRes = when (category.id) {
                "living_world" -> R.drawable.world
                "mathematics" -> if (category.isLocked) R.drawable.science else R.drawable.science1
                "infinite_mode" -> R.drawable.infinite
                "arts_culture" -> if (category.isLocked) R.drawable.art else R.drawable.art1
                "around_globe" -> if (category.isLocked) R.drawable.globe else R.drawable.globe1
                "active_science" -> if (category.isLocked) R.drawable.science else R.drawable.science1
                else -> R.drawable.ic_menu_gallery
            }
            icon.setImageResource(iconRes)

            // Always clear the previous listener first so a recycled view
            // from a now-locked category can't still fire the old click.
            card.setOnClickListener(null)

            if (category.isLocked) {
                lockContainer.visibility = View.VISIBLE
                progressText.visibility = View.INVISIBLE
                progressBar.visibility = View.INVISIBLE
                card.isEnabled = false
                card.alpha = 0.6f
            } else {
                lockContainer.visibility = View.GONE
                progressText.visibility = View.VISIBLE
                progressBar.visibility = View.VISIBLE
                card.isEnabled = true
                card.alpha = 1.0f
                card.setOnClickListener { onCategoryClick(category) }
            }
        }
    }

    class CategoryDiffCallback : DiffUtil.ItemCallback<Category>() {
        override fun areItemsTheSame(oldItem: Category, newItem: Category): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Category, newItem: Category): Boolean {
            return oldItem == newItem
        }
    }
}
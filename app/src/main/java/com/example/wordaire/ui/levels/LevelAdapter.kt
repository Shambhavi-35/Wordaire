package com.example.wordaire.ui.levels

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wordaire.R
import com.example.wordaire.data.Level
import com.google.android.material.card.MaterialCardView

class LevelAdapter(private val onLevelClick: (Level) -> Unit) :
    ListAdapter<Level, LevelAdapter.LevelViewHolder>(LevelDiffCallback()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LevelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_level, parent, false)
        return LevelViewHolder(view, onLevelClick)
    }

    override fun onBindViewHolder(holder: LevelViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class LevelViewHolder(itemView: View, private val onLevelClick: (Level) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.levelCard)
        private val number: TextView = itemView.findViewById(R.id.levelNumber)
        private val lockIcon: ImageView = itemView.findViewById(R.id.lockIcon)
        private val starsLayout: View = itemView.findViewById(R.id.starsLayout)
        private val star1: ImageView = itemView.findViewById(R.id.star1)
        private val star2: ImageView = itemView.findViewById(R.id.star2)
        private val star3: ImageView = itemView.findViewById(R.id.star3)

        fun bind(level: Level) {
            number.text = level.levelNumber.toString()

            // Clear any previous listener first — RecyclerView reuses views,
            // so a recycled "unlocked" card could still fire an old click
            // if we don't reset it before checking isLocked again.
            card.setOnClickListener(null)

            if (level.isLocked) {
                lockIcon.visibility = View.VISIBLE
                number.visibility = View.INVISIBLE
                starsLayout.visibility = View.GONE
                card.isEnabled = false
                card.setCardBackgroundColor(0xFFF5F5F5.toInt())
            } else {
                lockIcon.visibility = View.GONE
                number.visibility = View.VISIBLE
                starsLayout.visibility = View.VISIBLE
                card.isEnabled = true
                card.setCardBackgroundColor(0xFFFFFFFF.toInt())
                card.setOnClickListener { onLevelClick(level) }

                // Update stars: Filled yellow for earned, hollow/dim for unearned.
                val starViews = listOf(star1, star2, star3)
                starViews.forEachIndexed { index, starView ->
                    val isFilled = level.stars > index
                    if (isFilled) {
                        starView.setImageResource(android.R.drawable.btn_star_big_on)
                        starView.setColorFilter(android.graphics.Color.parseColor("#FFC107"))
                        starView.alpha = 1.0f
                    } else {
                        // Exactly like the reference: hollow gray stars for unplayed
                        starView.setImageResource(android.R.drawable.btn_star_big_off)
                        starView.setColorFilter(android.graphics.Color.parseColor("#BDBDBD"))
                        starView.alpha = 0.4f
                    }
                }
            }
        }
    }

    class LevelDiffCallback : DiffUtil.ItemCallback<Level>() {
        override fun areItemsTheSame(oldItem: Level, newItem: Level): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Level, newItem: Level): Boolean {
            return oldItem == newItem
        }
    }
}
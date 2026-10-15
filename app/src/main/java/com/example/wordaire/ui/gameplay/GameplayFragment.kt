package com.example.wordaire.ui.gameplay

import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.wordaire.R
import com.example.wordaire.ads.BannerAdManager
import com.example.wordaire.ads.InterstitialAdManager
import com.example.wordaire.ads.RewardedAdManager
import com.example.wordaire.data.GameProgressManager
import com.example.wordaire.data.WordaireDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Stack

class GameplayFragment : Fragment() {

    private lateinit var foundationRow: LinearLayout
    private lateinit var foundationScroll: android.widget.HorizontalScrollView
    private lateinit var boardLayout: LinearLayout
    private lateinit var levelTitle: TextView
    private lateinit var movesCount: TextView
    private lateinit var stockCountText: TextView
    private lateinit var stockContainer: View
    private lateinit var drawnCardsContainer: FrameLayout
    private lateinit var undoButton: ImageButton
    private lateinit var hintButton: ImageButton
    private lateinit var magnetButton: ImageButton
    private lateinit var undoCountText: TextView
    private lateinit var hintCountText: TextView
    private lateinit var magnetCountText: TextView
    private lateinit var profileButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var overlayContainer: FrameLayout

    private var hintCount = 3
    private var undoCount = 3
    private var magnetCount = 3

    private var moves = 64
    private var startingMoves = 64
    private var levelId: String? = null
    private var categoryId: String? = null
    private var levelNum: Int = 1

    private val categories = mutableListOf<String>()

    private var cardsPerFoundation = 5
    private var boardColumnsCount = 3
    private var cardsPerColumn = 2

    data class CardInfo(
        val name: String,
        val category: String,
        val isCategoryCard: Boolean = false
    )

    private enum class SourceType { BOARD, STOCK_HAND }

    private data class Move(
        val cardInfo: CardInfo,
        val sourceType: SourceType,
        val sourceColumnIndex: Int = -1,
        val targetFoundation: View?
    )

    private data class Difficulty(
        val cardsPerFoundation: Int,
        val boardColumns: Int,
        val cardsPerColumn: Int,
        val startingMoves: Int
    )

    // boardColumnsData[col] is a stack of cards for that peak. Index 0 is
    // the bottom/most-covered card; the LAST index is the exposed card,
    // rendered in front, and the only one that can be dragged. Removing
    // it reveals the card underneath — the peaks cascade from the video.
    private val boardColumnsData = mutableListOf<MutableList<CardInfo>>()
    private val boardColumnViews = mutableListOf<FrameLayout>()
    // How many cards at the TOP of each column should render face-up.
    // Normally 1 (just the literal top/peak card, standard Solitaire rule).
    // It grows past 1 only when the player explicitly stacks a matching
    // character onto another (e.g. Bee onto Ant) — that whole matched
    // cluster stays visible until it's sent to its foundation together.
    // Cards below this count are genuinely hidden tableau cards and render
    // as a face-down back, same as before.
    private val exposedCounts = mutableListOf<Int>()

    private val stockDeck = mutableListOf<CardInfo>()
    private val foundationProgress = mutableMapOf<String, Int>()
    private var totalSorted = 0
    private var totalToSort = 0
    private val moveHistory = Stack<Move>()
    private var levelFinished = false
    // A card remains in its source container until its landing animation ends.
    // Without this guard the same exposed card can be selected again (notably
    // by repeatedly tapping the magnet) and be counted more than once.
    // This same flag is used to lock the board while the opening "deal"
    // animation is playing, so nothing can be tapped mid-cascade.
    private var isResolvingMove = false
    private var isDrawingFromStock = false
    private var isDealing = false
    private var isDragging = false
    private var mistakeCount = 0
    private lateinit var rewardedAdManager: RewardedAdManager
    private lateinit var interstitialAdManager: InterstitialAdManager
    private lateinit var bannerAdManager: BannerAdManager
    private lateinit var soundEffects: GameSoundEffects

    private companion object {
        const val FLIGHT_DURATION_MS = 260L
        const val REWARDED_MOVES = 10

        // --- Deal / cascade animation tuning ---
        // DEAL_FLIGHT_MS: how long a single dealt card takes to fly from the
        // Stock into its column slot.
        // DEAL_STAGGER_MS: delay between launching each successive card —
        // this is what produces the "one by one" cascading motion seen in
        // the video, instead of every card just appearing at once.
        const val DEAL_FLIGHT_MS = 230L
        const val DEAL_STAGGER_MS = 85L
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_gameplay, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        foundationRow = view.findViewById(R.id.foundationRow)
        foundationScroll = view.findViewById(R.id.foundationScroll)
        boardLayout = view.findViewById(R.id.boardLayout)
        levelTitle = view.findViewById(R.id.levelTitle)
        movesCount = view.findViewById(R.id.movesCount)
        stockCountText = view.findViewById(R.id.stockCount)
        stockContainer = view.findViewById(R.id.stockContainer)
        drawnCardsContainer = view.findViewById(R.id.drawnCardsContainer)
        undoButton = view.findViewById(R.id.undoButton)
        hintButton = view.findViewById(R.id.hintButton)
        magnetButton = view.findViewById(R.id.magnetButton)
        undoCountText = view.findViewById(R.id.undoCount)
        hintCountText = view.findViewById(R.id.hintCount)
        magnetCountText = view.findViewById(R.id.magnetCount)
        profileButton = view.findViewById(R.id.profileButton)
        settingsButton = view.findViewById(R.id.settingsButton)

        overlayContainer = view.findViewById(R.id.overlayContainer)
        overlayContainer.setOnClickListener { /* Consume clicks while visible to prevent background interaction */ }
        rewardedAdManager = RewardedAdManager(requireActivity())
        rewardedAdManager.preload()

        interstitialAdManager = InterstitialAdManager(requireActivity())
        interstitialAdManager.load()

        val bannerContainer = view.findViewById<ViewGroup>(R.id.bannerContainer)
        bannerAdManager = BannerAdManager(bannerContainer)
        bannerAdManager.load()

        soundEffects = GameSoundEffects()

        val settingsPrefs = requireContext().getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        soundEffects.setSoundEnabled(settingsPrefs.getBoolean("sound_enabled", true))

        levelId = arguments?.getString("levelId")
        levelNum = arguments?.getInt("levelNumber") ?: 1
        categoryId = levelId?.substringBeforeLast("_")

        if (levelId == null || categoryId.isNullOrEmpty()) {
            Log.e("Gameplay", "Missing levelId/categoryId in arguments")
            Toast.makeText(context, "Could not load level", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        val difficulty = difficultyForLevel(levelNum)
        cardsPerFoundation = difficulty.cardsPerFoundation
        boardColumnsCount = difficulty.boardColumns
        cardsPerColumn = difficulty.cardsPerColumn
        startingMoves = difficulty.startingMoves

        moves = startingMoves
        levelTitle.text = "Level $levelNum"
        movesCount.text = moves.toString()

        setupLevelData()

        stockContainer.setOnClickListener { drawFromStock() }
        stockCountText.setOnClickListener { drawFromStock() }
        drawnCardsContainer.setOnClickListener { returnToStock() }

        hintButton.setOnClickListener {
            if (hintCount > 0) {
                hintCount--
                hintCountText.text = hintCount.toString()
                showHint()
            } else {
                rewardedAdManager.show(
                    onRewardEarned = {
                        hintCount++
                        hintCountText.text = hintCount.toString()
                        Toast.makeText(context, "Hint Refilled!", Toast.LENGTH_SHORT).show()
                    },
                    onUnavailable = {
                        Toast.makeText(context, "Ad is still loading or not available. Please wait or try again.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        undoButton.setOnClickListener {
            if (undoCount > 0) {
                if (moveHistory.isNotEmpty()) {
                    undoCount--
                    undoCountText.text = undoCount.toString()
                    undoLastMove()
                } else {
                    Toast.makeText(context, "No moves to undo!", Toast.LENGTH_SHORT).show()
                }
            } else {
                rewardedAdManager.show(
                    onRewardEarned = {
                        undoCount++
                        undoCountText.text = undoCount.toString()
                        Toast.makeText(context, "Undo Refilled!", Toast.LENGTH_SHORT).show()
                    },
                    onUnavailable = {
                        Toast.makeText(context, "Ad is still loading or not available. Please wait or try again.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        magnetButton.setOnClickListener {
            if (magnetCount > 0) {
                magnetCount--
                magnetCountText.text = magnetCount.toString()
                useMagnet()
            } else {
                rewardedAdManager.show(
                    onRewardEarned = {
                        magnetCount++
                        magnetCountText.text = magnetCount.toString()
                        Toast.makeText(context, "Magnet Refilled!", Toast.LENGTH_SHORT).show()
                    },
                    onUnavailable = {
                        Toast.makeText(context, "Ad is still loading or not available. Please wait or try again.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        profileButton.setOnClickListener {
            if (isResolvingMove || isDrawingFromStock || isDealing || levelFinished || isDragging) return@setOnClickListener
            showProfileDialog()
        }
        settingsButton.setOnClickListener {
            if (isResolvingMove || isDrawingFromStock || isDealing || levelFinished || isDragging) return@setOnClickListener
            showSettingsDialog()
        }
    }

    private fun difficultyForLevel(levelNumber: Int): Difficulty = when {
        levelNumber <= 3 -> Difficulty(cardsPerFoundation = 4, boardColumns = 3, cardsPerColumn = 2, startingMoves = 40)
        levelNumber <= 7 -> Difficulty(cardsPerFoundation = 6, boardColumns = 3, cardsPerColumn = 3, startingMoves = 55)
        levelNumber <= 12 -> Difficulty(cardsPerFoundation = 8, boardColumns = 4, cardsPerColumn = 3, startingMoves = 75)
        levelNumber <= 16 -> Difficulty(cardsPerFoundation = 10, boardColumns = 4, cardsPerColumn = 4, startingMoves = 90)
        else -> Difficulty(cardsPerFoundation = 12, boardColumns = 4, cardsPerColumn = 4, startingMoves = 110)
    }

    private fun setupLevelData() {
        categories.clear()
        categories.addAll(categoryNamesFor(categoryId).distinct())
        totalToSort = categories.size * cardsPerFoundation

        // Dynamically change background based on world
        val bgRes = when (categoryId) {
            "around_globe" -> R.drawable.bg_ocean
            "active_science", "mathematics" -> R.drawable.bg_space
            "infinite_mode" -> R.drawable.bg_splash_gradient
            else -> R.drawable.bg_savanna // living world & others
        }
        view?.findViewById<View>(R.id.gameplayRoot)?.setBackgroundResource(bgRes)

        setupGame()
    }

    private fun categoryNamesFor(id: String?): List<String> = when (id) {
        "living_world" -> {
            val pool = listOf("Animals", "Foods", "Insects", "Plants", "Birds", "Sea Life", "Body Parts", "Family", "Clothing", "Vegetables")
            val start = (levelNum - 1) % pool.size
            pool.subList(start, minOf(start + 4, pool.size)) + pool.subList(0, maxOf(0, 4 - (pool.size - start)))
        }
        "mathematics" -> {
            val targetsPool = listOf("Target = 2", "Target = 3", "Target = 4", "Target = 5", "Target = 6", "Target = 7", "Target = 8", "Target = 9", "Target = 10", "Target = 12", "Target = 15", "Target = 20")
            val start = ((levelNum - 1) * 2) % targetsPool.size
            targetsPool.subList(start, minOf(start + 4, targetsPool.size)) + targetsPool.subList(0, maxOf(0, 4 - (targetsPool.size - start)))
        }
        "infinite_mode" -> {
            val pool = listOf("Animals", "Foods", "Insects", "Plants", "Birds", "Sea Life", "Colors", "Shapes", "Numbers", "Music", "Art", "Theater", "Countries", "Landmarks", "Nature", "Cities", "Travel", "Sports", "Space", "Science", "Computers", "Invention", "Body Parts", "Weather", "Family", "Clothing", "Vehicles", "Tools", "Kitchenware", "Furniture", "Electronics", "School", "Occupations", "Hobbies", "Emotions", "Time", "Buildings", "Natural Disasters", "Materials", "Fantasy", "Holidays", "Jewelry", "Pet Supplies", "Bathroom", "Garden", "Camping", "Gym", "Cosmetics", "Board Games", "Mythology", "Superheroes", "Pirates", "Wild West", "Medieval", "Prehistoric", "Beverages", "Addition", "Subtraction", "Multiplication", "Division", "Root")
            val start = (levelNum - 1) % pool.size
            pool.subList(start, minOf(start + 5, pool.size)) + pool.subList(0, maxOf(0, 5 - (pool.size - start)))
        }
        "arts_culture" -> listOf("Colors", "Shapes", "Music", "Art", "Theater")
        "around_globe" -> {
            val pool = listOf("Countries", "Landmarks", "Nature", "Cities", "Travel")
            val start = (levelNum - 1) % pool.size
            pool.subList(start, pool.size) + pool.subList(0, start)
        }
        "active_science" -> {
            val pool = listOf("Sports", "Space", "Science", "Computers", "Invention")
            val start = (levelNum - 1) % pool.size
            pool.subList(start, pool.size) + pool.subList(0, start)
        }
        else -> listOf("Colors", "Shapes", "Numbers")
    }

    private fun setupGame() {
        levelFinished = false
        isResolvingMove = false
        isDrawingFromStock = false
        isDealing = false
        mistakeCount = 0

        foundationRow.removeAllViews()
        boardLayout.removeAllViews()
        boardColumnsData.clear()
        boardColumnViews.clear()
        stockDeck.clear()
        drawnCardsContainer.removeAllViews()
        moveHistory.clear()
        totalSorted = 0

        // Create empty, unassigned foundations
        categories.forEachIndexed { index, category ->
            foundationProgress[category] = 0
            val foundationView = layoutInflater.inflate(R.layout.item_foundation_card, foundationRow, false)
            // Initially tag with index to indicate it's unassigned
            foundationView.tag = "unassigned_$index"
            updateFoundationUI(foundationView, category, 0)
            foundationView.setOnDragListener(FoundationDragListener())
            foundationRow.addView(foundationView)
        }

        val allCards = mutableListOf<CardInfo>()
        categories.forEach { cat ->
            // 1. Add the Category Card itself to the deck
            allCards.add(CardInfo(cat, cat, isCategoryCard = true))

            // 2. Add the Character Cards
            getCategoryItems(cat).take(cardsPerFoundation).forEach { name ->
                allCards.add(CardInfo(name, cat, isCategoryCard = false))
            }
        }
        allCards.shuffle()

        // Create the empty column containers
        for (i in 0 until boardColumnsCount) {
            val column = FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            }
            boardLayout.addView(column)
            boardColumnViews.add(column)
            boardColumnsData.add(mutableListOf())
        }

        // Build the deal order round-robin across columns (one card to
        // column 0, one to column 1, one to column 2... then back to
        // column 0 for the next row) — this is the classic tableau deal
        // that produces the left-to-right cascading wave seen in the
        // video, instead of filling one whole column before moving to
        // the next.
        val dealQueue = mutableListOf<Pair<Int, CardInfo>>()
        repeat(cardsPerColumn) {
            for (col in 0 until boardColumnsCount) {
                if (allCards.isNotEmpty()) {
                    dealQueue.add(col to allCards.removeAt(0))
                }
            }
        }

        stockDeck.addAll(allCards)
        stockCountText.text = stockDeck.size.toString()

        dealBoardWithAnimation(dealQueue)
    }

    /**
     * Plays the opening "deal" moment: one card at a time flies out of the
     * Stock pile and lands in its column, staggered so the whole board
     * fills in a cascading wave — matching the reference video instead of
     * every card just appearing instantly.
     *
     * The board stays locked (isDealing / isResolvingMove) until the last
     * card has landed, so a fast tap can't interrupt mid-cascade.
     */
    private fun dealBoardWithAnimation(dealQueue: List<Pair<Int, CardInfo>>) {
        if (dealQueue.isEmpty()) return

        isDealing = true
        isResolvingMove = true
        overlayContainer.visibility = View.VISIBLE

        // Wait for the just-added (empty) column views to actually be
        // measured and positioned before reading their on-screen location —
        // otherwise getLocationOnScreen() would return stale/zero values.
        boardLayout.post {
            if (!isAdded) return@post

            lifecycleScope.launch {
                val cardWidth = resources.getDimensionPixelSize(R.dimen.card_width)
                val cardHeight = resources.getDimensionPixelSize(R.dimen.card_height)

                for ((colIndex, cardInfo) in dealQueue) {
                    if (!isAdded) return@launch
                    dealSingleCard(colIndex, cardInfo, cardWidth, cardHeight)
                    soundEffects.cardDrawn()
                    delay(DEAL_STAGGER_MS)
                }

                // Let the very last card's flight actually finish landing
                // before unlocking interaction.
                delay(DEAL_FLIGHT_MS)
                if (isAdded) {
                    overlayContainer.visibility = View.GONE
                    isDealing = false
                    isResolvingMove = false
                }
            }
        }
    }

    /** Flies a single card from the Stock into column [colIndex], then commits it to boardColumnsData. */
    private fun dealSingleCard(colIndex: Int, cardInfo: CardInfo, cardWidth: Int, cardHeight: Int) {
        val column = boardColumnViews.getOrNull(colIndex) ?: return

        val stockLoc = IntArray(2).also { stockContainer.getLocationOnScreen(it) }
        val overlayLoc = IntArray(2).also { overlayContainer.getLocationOnScreen(it) }
        val columnLoc = IntArray(2).also { column.getLocationOnScreen(it) }

        // idxInColumn mirrors the topMargin math in renderColumn(): the
        // card about to be added will sit at index = current size (0-based),
        // stacked slightly lower than the one before it.
        val idxInColumn = boardColumnsData[colIndex].size
        val targetX = columnLoc[0] + (column.width - cardWidth) / 2f
        val targetY = (columnLoc[1] + idxInColumn * 30).toFloat()

        val flyingCard = createCardView(cardInfo, interactive = false)
        overlayContainer.addView(flyingCard, FrameLayout.LayoutParams(cardWidth, cardHeight))
        flyingCard.x = (stockLoc[0] - overlayLoc[0]).toFloat()
        flyingCard.y = (stockLoc[1] - overlayLoc[1]).toFloat()
        flyingCard.scaleX = 0.55f
        flyingCard.scaleY = 0.55f
        flyingCard.rotation = -10f

        flyingCard.animate()
            .x(targetX - overlayLoc[0])
            .y(targetY - overlayLoc[1])
            .scaleX(1f)
            .scaleY(1f)
            .rotation(0f)
            .setDuration(DEAL_FLIGHT_MS)
            .withEndAction {
                if (!isAdded) return@withEndAction
                overlayContainer.removeView(flyingCard)
                // Commit to the real data model and redraw the column now
                // that the card has visually landed. animatePopIn = false
                // because the flight itself already sold the "arrival".
                boardColumnsData[colIndex].add(cardInfo)
                renderColumn(colIndex, animatePopIn = false)
            }
            .start()
    }

    /**
     * Redraws one peak from boardColumnsData. Covered cards are dimmed and
     * get no drag listener at all — that's what makes them un-draggable.
     * The last card in the stack is the exposed one: full opacity, higher
     * elevation, and the only one that responds to long-press-drag.
     *
     * @param animatePopIn When true, plays a small scale pop-in on the
     * newly-exposed card. Pass false while the opening deal animation is
     * running since the fly-in itself already provides that motion.
     */
    private fun renderColumn(colIndex: Int, animatePopIn: Boolean = true) {
        val column = boardColumnViews.getOrNull(colIndex) ?: return
        column.removeAllViews()
        val stack = boardColumnsData.getOrNull(colIndex) ?: return

        stack.forEachIndexed { idx, cardInfo ->
            val isLiteralTop = idx == stack.lastIndex

            val cardView = createCardView(cardInfo, interactive = isLiteralTop)
            val params = cardView.layoutParams as FrameLayout.LayoutParams

            // Solitaire-style fanned arrangement (increased overlap)
            params.topMargin = idx * 45
            params.gravity = android.view.Gravity.CENTER_HORIZONTAL
            cardView.layoutParams = params

            val cardBack = cardView.findViewById<View>(R.id.cardBack)
            val cardText = cardView.findViewById<TextView>(R.id.cardText)

            if (isLiteralTop) {
                cardBack.visibility = View.GONE
                cardText.visibility = View.VISIBLE
                cardView.alpha = 1f
                cardView.elevation = (idx + 1) * 2f

                if (animatePopIn) {
                    // Dramatic "Flip" reveal animation
                    cardView.rotationY = -180f
                    cardView.animate().rotationY(0f).setDuration(400).start()

                    // Subtle pop-in scale
                    cardView.scaleX = 0.85f
                    cardView.scaleY = 0.85f
                    cardView.animate().scaleX(1f).scaleY(1f).setDuration(250).start()

                    // Add a creative breathing/floating animation to the top card
                    startBreathingAnimation(cardView)
                }
            } else {
                // Hidden tableau cards or cards covered by a stack
                cardBack.visibility = View.VISIBLE
                cardText.visibility = View.INVISIBLE
                cardView.alpha = 0.85f
                cardView.elevation = (idx + 1) * 2f
            }
            column.addView(cardView)
        }
    }

    private fun startBreathingAnimation(view: View) {
        if (!isAdded) return
        view.animate()
            .scaleX(1.03f)
            .scaleY(1.03f)
            .setDuration(1200)
            .withEndAction {
                if (!isAdded) return@withEndAction
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(1200)
                    .withEndAction { startBreathingAnimation(view) }
                    .start()
            }
            .start()
    }

    private fun popExposedCardFromColumn(colIndex: Int): CardInfo? {
        val stack = boardColumnsData.getOrNull(colIndex) ?: return null
        if (stack.isEmpty()) return null
        val card = stack.removeAt(stack.lastIndex)
        renderColumn(colIndex)
        return card
    }

    private fun pushCardToColumn(colIndex: Int, info: CardInfo) {
        boardColumnsData.getOrNull(colIndex)?.add(info)
        renderColumn(colIndex)
    }

    private fun getCategoryItems(cat: String): List<String> = when (cat) {
        // --- Mathematics (Target Result Matching Operations) ---
        "Target = 2", "2" -> listOf("1 + 1", "3 - 1", "1 × 2", "4 ÷ 2", "√4", "5 - 3", "6 ÷ 3", "∛8", "2 + 0", "2 × 1", "10 - 8", "8 ÷ 4", "12 - 10", "14 ÷ 7", "4 - 2", "16 ÷ 8")
        "Target = 3", "3" -> listOf("1 + 2", "5 - 2", "1 × 3", "9 ÷ 3", "√9", "2 + 1", "10 - 7", "12 ÷ 4", "∛27", "3 × 1", "15 - 12", "6 ÷ 2", "4 - 1", "18 ÷ 6", "11 - 8", "21 ÷ 7")
        "Target = 4", "4" -> listOf("2 + 2", "6 - 2", "2 × 2", "16 ÷ 4", "√16", "1 + 3", "10 - 6", "1 × 4", "20 ÷ 5", "∛64", "12 ÷ 3", "8 - 4", "5 - 1", "24 ÷ 6", "14 - 10", "28 ÷ 7")
        "Target = 5", "5" -> listOf("2 + 3", "10 - 5", "1 × 5", "25 ÷ 5", "√25", "1 + 4", "15 - 10", "5 × 1", "30 ÷ 6", "∛125", "20 ÷ 4", "7 - 2", "8 - 3", "35 ÷ 7", "11 - 6", "40 ÷ 8")
        "Target = 6", "6" -> listOf("3 + 3", "10 - 4", "2 × 3", "36 ÷ 6", "√36", "1 + 5", "12 - 6", "1 × 6", "24 ÷ 4", "∛216", "18 ÷ 3", "8 - 2", "9 - 3", "42 ÷ 7", "15 - 9", "48 ÷ 8")
        "Target = 7", "7" -> listOf("3 + 4", "12 - 5", "1 × 7", "49 ÷ 7", "√49", "2 + 5", "10 - 3", "7 × 1", "21 ÷ 3", "∛343", "28 ÷ 4", "15 - 8", "9 - 2", "56 ÷ 8", "16 - 9", "63 ÷ 9")
        "Target = 8", "8" -> listOf("4 + 4", "15 - 7", "2 × 4", "64 ÷ 8", "√64", "3 + 5", "12 - 4", "1 × 8", "32 ÷ 4", "∛512", "16 ÷ 2", "20 - 12", "10 - 2", "72 ÷ 9", "18 - 10", "80 ÷ 10")
        "Target = 9", "9" -> listOf("4 + 5", "18 - 9", "3 × 3", "81 ÷ 9", "√81", "2 + 7", "15 - 6", "1 × 9", "36 ÷ 4", "∛729", "27 ÷ 3", "20 - 11", "10 - 1", "45 ÷ 5", "12 - 3", "54 ÷ 6")
        "Target = 10", "10" -> listOf("5 + 5", "15 - 5", "2 × 5", "100 ÷ 10", "√100", "3 + 7", "20 - 10", "1 × 10", "50 ÷ 5", "∛1000", "30 ÷ 3", "25 - 15", "4 + 6", "60 ÷ 6", "12 - 2", "70 ÷ 7")
        "Target = 12", "12" -> listOf("6 + 6", "20 - 8", "3 × 4", "144 ÷ 12", "√144", "5 + 7", "15 - 3", "2 × 6", "36 ÷ 3", "∛1728", "48 ÷ 4", "24 ÷ 2", "4 + 8", "60 ÷ 5", "18 - 6", "72 ÷ 6")
        "Target = 15", "15" -> listOf("10 + 5", "20 - 5", "3 × 5", "225 ÷ 15", "√225", "7 + 8", "30 - 15", "1 × 15", "45 ÷ 3", "∛3375", "60 ÷ 4", "25 - 10", "6 + 9", "75 ÷ 5", "18 - 3", "90 ÷ 6")
        "Target = 20", "20" -> listOf("10 + 10", "25 - 5", "4 × 5", "400 ÷ 20", "√400", "12 + 8", "30 - 10", "2 × 10", "60 ÷ 3", "∛8000", "80 ÷ 4", "50 - 30", "15 + 5", "100 ÷ 5", "24 - 4", "120 ÷ 6")

        // --- Operations Fallback ---
        "Addition" -> listOf("1 + 1", "2 + 3", "4 + 5", "10 + 10", "6 + 6", "3 + 4", "5 + 5", "7 + 8")
        "Subtraction" -> listOf("3 - 1", "10 - 5", "20 - 8", "15 - 7", "6 - 2", "12 - 5", "18 - 9", "25 - 5")
        "Multiplication" -> listOf("1 × 2", "2 × 3", "3 × 4", "4 × 5", "2 × 5", "3 × 3", "2 × 6", "3 × 5")
        "Division" -> listOf("4 ÷ 2", "9 ÷ 3", "16 ÷ 4", "25 ÷ 5", "36 ÷ 6", "49 ÷ 7", "64 ÷ 8", "100 ÷ 10")
        "Root" -> listOf("√4", "√9", "√16", "√25", "√36", "√49", "√64", "√100")

        // --- Living World ---
        "Animals" -> listOf("Lion", "Tiger", "Bear", "Panda", "Zebra", "Wolf", "Elephant", "Giraffe", "Kangaroo", "Koala", "Fox", "Deer", "Monkey", "Rabbit", "Owl", "Eagle", "Cheetah", "Leopard", "Hippo", "Rhino", "Camel", "Donkey", "Goat", "Sheep", "Cow", "Pig", "Horse", "Cat", "Dog", "Hamster", "Squirrel")
        "Foods" -> listOf("Apple", "Pizza", "Sushi", "Burger", "Donut", "Pasta", "Taco", "Salad", "Soup", "Cake", "Bread", "Rice", "Cookie", "Steak", "Fruit", "Juice", "Waffle", "Pancake", "Muffin", "Bacon", "Cheese", "Butter", "Yogurt", "Honey", "Jam", "Curry", "Kebab", "Omelet", "Sandwich", "Chips", "Pie")
        "Insects" -> listOf("Ant", "Bee", "Butterfly", "Beetle", "Grasshopper", "Ladybug", "Dragonfly", "Moth", "Cricket", "Firefly", "Wasp", "Mantis", "Spider", "Fly", "Snail", "Worm", "Cicada", "Termite", "Roach", "Flea", "Tick", "Louse", "Gnat", "Centipede", "Scorpion", "Hornet", "Bumblebee", "Slug")
        "Plants" -> listOf("Rose", "Tulip", "Cactus", "Maple", "Oak", "Fern", "Lily", "Daisy", "Sunflower", "Bamboo", "Palm", "Willow", "Grass", "Mushroom", "Vine", "Moss", "Ivy", "Pine", "Cedar", "Birch", "Orchid", "Lavender", "Poppy", "Jasmine", "Lotus", "Aloe", "Basil", "Mint", "Sage", "Thyme", "Dill")
        "Birds" -> listOf("Eagle", "Owl", "Parrot", "Penguin", "Swan", "Hawk", "Falcon", "Raven", "Crow", "Pigeon", "Robin", "Sparrow", "Woodpecker", "Peacock", "Flamingo", "Ostrich", "Hummingbird", "Kingfisher", "Canary", "Stork", "Pelican", "Heron", "Crane", "Gull", "Tern", "Swallow", "Swift", "Lark", "Jay", "Magpie", "Finch", "Bunting")
        "Sea Life" -> listOf("Shark", "Whale", "Dolphin", "Octopus", "Turtle", "Crab", "Lobster", "Shrimp", "Seahorse", "Starfish", "Jellyfish", "Seal", "Walrus", "Penguin", "Otter", "Coral", "Clam", "Ray", "Squid", "Eel", "Salmon", "Tuna", "Cod", "Bass", "Trout", "Perch", "Carp", "Manta", "Orca", "Narwhal", "Dugong", "Manatee")
        "Reptiles" -> listOf("Snake", "Lizard", "Crocodile", "Alligator", "Turtle", "Tortoise", "Chameleon", "Iguana", "Gecko", "Cobra", "Python", "Viper", "Dragon", "Skink", "Monitor", "Newt", "Asp", "Boa", "Mamba", "Adder", "Gharial", "Caiman", "Tuatara", "Anole", "Terrapin", "Slider", "Racer", "Krait", "Taipan", "Moccasin")
        "Fruits" -> listOf("Mango", "Banana", "Orange", "Grape", "Strawberry", "Blueberry", "Watermelon", "Pineapple", "Peach", "Pear", "Cherry", "Kiwi", "Plum", "Lemon", "Lime", "Papaya", "Guava", "Fig", "Date", "Melon", "Berry", "Apple", "Apricot", "Nectarine", "Lychee", "Durian", "Passion", "Dragon", "Pomelo", "Quince", "Olive", "Coconut")
        "Vegetables" -> listOf("Carrot", "Potato", "Tomato", "Onion", "Garlic", "Broccoli", "Spinach", "Corn", "Peas", "Pepper", "Cucumber", "Lettuce", "Cabbage", "Eggplant", "Radish", "Celery", "Asparagus", "Zucchini", "Kale", "Leek", "Beet", "Yam", "Chard", "Okra", "Gourd", "Squash", "Pumpkin", "Turnip", "Parsnip", "Ginger", "Radicchio")
        "Trees" -> listOf("Oak", "Pine", "Maple", "Birch", "Cedar", "Willow", "Palm", "Redwood", "Banyan", "Cherry", "Apple", "Olive", "Teak", "Ebony", "Mahogany", "Poplar", "Spruce", "Fir", "Ash", "Elm", "Beech", "Yew", "Larch", "Cypress", "Acacia", "Baobab", "Bamboo", "Hickory", "Walnut", "Chestnut", "Alder", "Rowan")

        // --- Arts & Culture ---
        "Colors" -> listOf("Red", "Blue", "Green", "Yellow", "Purple", "Orange", "Pink", "Brown", "Black", "White", "Silver", "Gold", "Cyan", "Magenta", "Beige", "Gray", "Teal", "Indigo", "Violet", "Maroon", "Navy", "Azure", "Lime", "Olive", "Coral", "Peach", "Amber", "Ivory", "Crimson", "Scarlet", "Turquoise", "Emerald", "Ruby", "Sapphire", "Topaz", "Onyx", "Pearl", "Quartz", "Jade", "Opal")
        "Shapes" -> listOf("Circle", "Square", "Triangle", "Rectangle", "Oval", "Star", "Heart", "Diamond", "Cube", "Cone", "Sphere", "Cylinder", "Hexagon", "Octagon", "Cross", "Pyramid", "Pentagon", "Rhombus", "Trapezoid", "Torus", "Crescent", "Arrow", "Prism", "Helix", "Spiral", "Zigzag", "Parallelogram", "Ellipse", "Decagon", "Nonagon", "Heptagon", "Sector", "Kite", "Oblong", "Wedge")
        "Music" -> listOf("Piano", "Guitar", "Violin", "Drums", "Flute", "Trumpet", "Melody", "Rhythm", "Song", "Choir", "Opera", "Jazz", "Harp", "Cello", "Banjo", "Tempo", "Lyric", "Verse", "Chorus", "Bridge", "Harmony", "Solo", "Duet", "Trio", "Quartet", "Band", "Orchestra", "Symphony", "Concert", "Sonata", "Ballad", "Anthem", "Carol", "Hymn", "Lullaby", "Psalm", "Chant", "Jingle", "Tune")
        "Art" -> listOf("Canvas", "Paint", "Brush", "Sketch", "Portrait", "Mural", "Sculpture", "Gallery", "Color", "Frame", "Ink", "Clay", "Statue", "Design", "Stencil", "Craft", "Mosaic", "Pottery", "Weaving", "Easel", "Palette", "Crayon", "Pencil", "Marker", "Etching", "Carving", "Print", "Doodle", "Pastel", "Enamel", "Gilding", "Glaze", "Layer", "Shadow", "Highlight", "Perspective")
        "Theater" -> listOf("Actor", "Stage", "Script", "Drama", "Comedy", "Mask", "Scene", "Applause", "Director", "Play", "Costume", "Curtain", "Ballet", "Musical", "Opera", "Ticket", "Props", "Wings", "Spotlight", "Audience", "Critique", "Review", "Premiere", "Debut", "Encore", "Act", "Draft", "Theme", "Plot", "Hero", "Villain", "Extra", "Role", "Genre", "Style", "Cast")

        // --- Around the Globe ---
        "Countries" -> listOf("India", "Japan", "Brazil", "Canada", "Egypt", "Italy", "Kenya", "Mexico", "Nepal", "Spain", "Thailand", "Turkey", "France", "Germany", "China", "Peru", "Greece", "Russia", "Norway", "Sweden", "Finland", "Iceland", "Ireland", "Poland", "Austria", "Swiss", "Belgium", "Holland", "Vietnam", "Korea", "Iran", "Iraq", "Chile", "Cuba", "Mali", "Chad", "Laos", "Fiji", "Oman")
        "Landmarks" -> listOf("Taj Mahal", "Eiffel Tower", "Pyramids", "Colosseum", "Big Ben", "Petra", "Machu Picchu", "Acropolis", "Sphinx", "Angkor Wat", "Moai", "Alhambra", "Tower", "Bridge", "Castle", "Wall", "Statue", "Liberty", "Opera", "House", "Burj", "Khalifa", "Great", "Canyon", "Niagara", "Falls", "Temple", "Shrine", "Mosque", "Church", "Palace", "Arena", "Spire", "Arch", "Gate", "Dome")
        "Nature" -> listOf("River", "Forest", "Mountain", "Ocean", "Desert", "Valley", "Island", "Glacier", "Volcano", "Waterfall", "Canyon", "Reef", "Cave", "Beach", "Plain", "Swamp", "Marsh", "Tundra", "Jungle", "Steppe", "Savanna", "Delta", "Lagoon", "Geyser", "Crater", "Peak", "Ridge", "Coast", "Bay", "Sea", "Lake", "Pond", "Creek", "Brook", "Spring", "Fjord", "Atoll", "Butte")
        "Cities" -> listOf("London", "Paris", "Tokyo", "New York", "Mumbai", "Dubai", "Sydney", "Cairo", "Rome", "Berlin", "Moscow", "Seoul", "Toronto", "Madrid", "Vienna", "Prague", "Athens", "Dublin", "Oslo", "Stockholm", "Bangkok", "Beijing", "Seoul", "Hanoi", "Lima", "Bogota", "Warsaw", "Lisbon", "Manila", "Taipei", "Riyadh", "Tehran", "Amman", "Kuwait", "Doha", "Muscat")
        "Travel" -> listOf("Plane", "Train", "Map", "Passport", "Camera", "Suitcase", "Hotel", "Guide", "Ticket", "Island", "Flight", "Road", "Beach", "Camp", "Hike", "Tour", "Bus", "Taxi", "Bike", "Boat", "Ship", "Hostel", "Resort", "Motel", "Voyage", "Cruise", "Safari", "Expedition", "Journey", "Trip", "Visit", "Trek", "Ride", "Drive", "Sail", "Walk", "Run", "Fly", "Swim")

        // --- Active & Science ---
        "Sports" -> listOf("Cricket", "Football", "Tennis", "Hockey", "Boxing", "Swimming", "Cycling", "Running", "Archery", "Badminton", "Golf", "Wrestling", "Rugby", "Skiing", "Surfing", "Karate", "Judo", "Fencing", "Sailing", "Rowing", "Diving", "Squash", "Billiards", "Bowling", "Darts", "Chess", "Yoga", "Gym", "Race", "Dive", "Hike", "Kick", "Goal", "Punt", "Pass", "Base", "Ball")
        "Space" -> listOf("Sun", "Moon", "Earth", "Mars", "Venus", "Jupiter", "Saturn", "Comet", "Galaxy", "Nebula", "Orbit", "Rocket", "Star", "Planet", "Cosmos", "Void", "Meteor", "Asteroid", "Black", "Hole", "Pulsar", "Quasar", "Probe", "Lander", "Rover", "Satellite", "Astronaut", "Alien", "Space", "Time", "Light", "Year", "Day", "Mass", "Core", "Gas", "Dust", "Wave", "Ray")
        "Science" -> listOf("Atom", "Energy", "Gravity", "Matter", "Force", "Light", "Cell", "DNA", "Molecule", "Fossil", "Theory", "Lab", "React", "Static", "Logic", "Nerve", "Brain", "Heart", "Lung", "Bone", "Skin", "Blood", "Virus", "Bacteria", "Fungus", "Algae", "Prism", "Lens", "Math", "Stat", "Heat", "Cold", "Dry", "Wet", "Hard", "Soft", "Slow", "Fast", "Up", "Down")
        "Computers" -> listOf("Code", "Logic", "Screen", "Mouse", "Byte", "Server", "Web", "Cloud", "Data", "Binary", "Chip", "Wifi", "Robot", "App", "Link", "Pixel", "Ram", "Disk", "Port", "Wire", "Key", "Font", "Icon", "User", "Bot", "Bug", "Hack", "Spam", "Firewall", "Host", "Node", "Core", "Disk", "Tape", "Card", "Scan", "Plot", "Read", "Save", "Load")
        "Invention" -> listOf("Wheel", "Bulb", "Radio", "Steam", "Engine", "Phone", "Laser", "Battery", "Lens", "Motor", "Clock", "Paper", "Compass", "Tool", "Gear", "Lever", "Screw", "Axle", "Pulley", "Plough", "Print", "Press", "Fridge", "Oven", "Tv", "Scale", "Meter", "Pump", "Drill", "Saw", "File", "Bit", "Axe", "Hoe", "Pick", "Rod")

        "Numbers" -> listOf("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety", "Hundred", "Thousand", "Million", "Billion")
        "Body Parts" -> listOf("Head", "Hair", "Face", "Eye", "Ear", "Nose", "Mouth", "Tooth", "Tongue", "Neck", "Shoulder", "Arm", "Elbow", "Wrist", "Hand", "Finger", "Thumb", "Chest", "Back", "Stomach", "Waist", "Hip", "Leg", "Knee", "Ankle", "Foot", "Toe", "Heart", "Lung", "Brain", "Bone", "Muscle")
        "Weather" -> listOf("Sun", "Rain", "Cloud", "Wind", "Snow", "Storm", "Thunder", "Lightning", "Fog", "Mist", "Hail", "Frost", "Ice", "Heat", "Cold", "Dry", "Wet", "Humid", "Breeze", "Gale", "Hurricane", "Tornado", "Rainbow", "Dew", "Sleet", "Shower", "Clear", "Overcast", "Drizzle", "Tempest", "Cyclone", "Blizzard")
        "Family" -> listOf("Mother", "Father", "Brother", "Sister", "Grandma", "Grandpa", "Aunt", "Uncle", "Cousin", "Nephew", "Niece", "Son", "Daughter", "Parent", "Sibling", "Grandchild", "Husband", "Wife", "Baby", "Toddler", "Child", "Teen", "Adult", "Elder", "Ancestor", "Relative", "In-law", "Twin", "Nanny", "Guardian", "Friend", "Neighbor")
        "Clothing" -> listOf("Shirt", "Pants", "Dress", "Skirt", "Socks", "Shoes", "Boots", "Hat", "Cap", "Gloves", "Scarf", "Coat", "Jacket", "Sweater", "Tie", "Belt", "Jeans", "Shorts", "Vest", "Gown", "Suit", "Uniform", "T-shirt", "Blouse", "Cardigan", "Parka", "Hoodie", "Sandal", "Sneaker", "Slipper", "Wallet", "Watch")

        "Vehicles" -> listOf("Car", "Bike", "Truck", "Ship", "Plane", "Bus", "Train", "Boat", "Van", "Taxi", "Jeep", "Tank", "Sled", "Cart", "Tram", "Ferry", "Yacht", "Glider", "Rocket", "Scooter", "Moped", "Subway", "Tractor", "Lorry", "Wagon", "Canoe", "Raft", "Jet", "Balloon", "Cycle", "Rover", "Skates")
        "Tools" -> listOf("Hammer", "Saw", "Drill", "Wrench", "Pliers", "Screwdriver", "Chisel", "Axe", "File", "Level", "Ruler", "Tape", "Plane", "Clamp", "Vice", "Anvil", "Mallet", "Spade", "Rake", "Hoe", "Fork", "Shears", "Trowel", "Scraper", "Sander", "Grinder", "Lathe", "Torch", "Solder", "Bolt", "Nut", "Screw")
        "Kitchenware" -> listOf("Fork", "Spoon", "Knife", "Plate", "Bowl", "Cup", "Glass", "Mug", "Pot", "Pan", "Jug", "Tray", "Oven", "Stove", "Grater", "Whisk", "Peeler", "Ladle", "Sieve", "Toaster", "Kettle", "Blender", "Mixer", "Fridge", "Freezer", "Scale", "Timer", "Apron", "Glove", "Cloth", "Brush", "Sponge")
        "Furniture" -> listOf("Chair", "Table", "Bed", "Sofa", "Desk", "Bench", "Stool", "Shelf", "Rack", "Stand", "Chest", "Drawer", "Closet", "Wardrobe", "Mirror", "Lamp", "Rug", "Mat", "Curtain", "Blister", "Cradle", "Couch", "Buffet", "Cabinet", "Counter", "Ottoman", "Settee", "Vanity", "Screen", "Frame", "Hanger", "Pillar")
        "Electronics" -> listOf("TV", "Phone", "Laptop", "Camera", "Radio", "Tablet", "Speaker", "Watch", "Clock", "Printer", "Router", "Modem", "Mouse", "Keyboard", "Screen", "Battery", "Charger", "Cable", "Plug", "Socket", "Light", "Bulb", "Sensor", "Remote", "Player", "Disc", "Drive", "Chip", "Board", "Wire", "Switch", "Button")
        "School" -> listOf("Pen", "Pencil", "Eraser", "Ruler", "Paper", "Book", "Page", "Ink", "Glue", "Tape", "Clip", "Folder", "File", "Case", "Bag", "Desk", "Chair", "Board", "Chalk", "Marker", "Sharpener", "Compass", "Protractor", "Calculator", "Map", "Globe", "Paint", "Brush", "Palette", "Scissors", "Stapler", "Punch")
        "Occupations" -> listOf("Doctor", "Nurse", "Teacher", "Pilot", "Farmer", "Baker", "Cook", "Chef", "Artist", "Singer", "Dancer", "Actor", "Writer", "Judge", "Lawyer", "Police", "Guard", "Soldier", "Sailor", "Driver", "Smith", "Mason", "Tailor", "Barber", "Clerk", "Banker", "Mayor", "Priest", "Monk", "Nun", "King", "Queen")
        "Hobbies" -> listOf("Reading", "Writing", "Singing", "Dancing", "Painting", "Drawing", "Cooking", "Baking", "Hiking", "Camping", "Fishing", "Hunting", "Running", "Swimming", "Cycling", "Skating", "Skiing", "Surfing", "Sailing", "Rowing", "Chess", "Cards", "Games", "Sewing", "Knitting", "Gardening", "Pottery", "Photography", "Acting", "Bowling", "Golf", "Yoga")
        "Emotions" -> listOf("Happy", "Sad", "Angry", "Fear", "Love", "Hate", "Joy", "Grief", "Hope", "Dread", "Calm", "Proud", "Shy", "Bold", "Kind", "Mean", "Grumpy", "Silly", "Bored", "Keen", "Weak", "Strong", "Soft", "Hard", "Cold", "Warm", "Hot", "Cool", "Fair", "Just", "Wise", "Fool")
        "Time" -> listOf("Second", "Minute", "Hour", "Day", "Week", "Month", "Year", "Decade", "Century", "Morning", "Noon", "Evening", "Night", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday", "Spring", "Summer", "Autumn", "Winter", "Past", "Now", "Soon", "Late", "Early", "Dawn", "Dusk", "Today")
        "Buildings" -> listOf("House", "Flat", "Cabin", "Hut", "Barn", "Shed", "Church", "Temple", "Mosque", "Shrine", "Palace", "Castle", "Tower", "Spire", "Bridge", "Gate", "Wall", "Roof", "Floor", "Room", "Hall", "Stair", "Window", "Door", "Shop", "Mall", "Bank", "School", "Office", "Clinic", "Hotel", "Cinema")
        "Natural Disasters" -> listOf("Flood", "Fire", "Storm", "Gale", "Rain", "Hail", "Snow", "Frost", "Ice", "Heat", "Drought", "Quake", "Slide", "Wave", "Blast", "Crash", "Plague", "Blight", "Famine", "War", "Riot", "Panic", "Fear", "Loss", "Ruin", "Help", "Hope", "Save", "Mend", "Build", "Plan", "Care")
        "Materials" -> listOf("Gold", "Silver", "Iron", "Steel", "Copper", "Brass", "Tin", "Lead", "Zinc", "Nickel", "Glass", "Clay", "Wood", "Stone", "Brick", "Sand", "Soil", "Mud", "Dust", "Ash", "Coal", "Oil", "Gas", "Salt", "Sugar", "Silk", "Wool", "Wax", "Glue", "Ink", "Dye", "Soap")
        "Fantasy" -> listOf("Dragon", "Giant", "Dwarf", "Elf", "Fairy", "Troll", "Ghost", "Witch", "Wizard", "Mage", "Monk", "Hero", "King", "Queen", "Prince", "Knight", "Steed", "Sword", "Shield", "Spear", "Bow", "Arrow", "Wand", "Staff", "Spell", "Charm", "Myth", "Tale", "Lore", "Fate", "Doom", "Luck")
        "Holidays" -> listOf("Easter", "Diwali", "Eid", "Puja", "Holi", "Feast", "Fair", "Show", "Party", "Gift", "Card", "Cake", "Song", "Dance", "Game", "Fun", "Joy", "Peace", "Love", "Home", "Kin", "Friend", "Guest", "Visit", "Trip", "Stay", "Rest", "Play", "Work", "Learn", "Grow", "Live")

        "Jewelry" -> listOf("Diamond", "Ruby", "Emerald", "Ring", "Bracelet", "Necklace", "Earring", "Gold", "Silver", "Platinum", "Pearl", "Opal", "Sapphire", "Jade", "Amber", "Topaz", "Bangle", "Choker", "Pendant", "Locket", "Cufflink", "Brooch", "Tiara", "Crown", "Amulet", "Bead", "Crystal", "Gem", "Jewel", "Chain", "Clasp", "Link")
        "Pet Supplies" -> listOf("Leash", "Collar", "Bowl", "Kennel", "Crate", "Toy", "Grooming", "Brush", "Shampoo", "Food", "Treat", "Bed", "Mat", "Litter", "Scoop", "Filter", "Tank", "Cage", "Perch", "Swing", "Bone", "Nip", "Whistle", "Clicker", "Harness", "Lead", "Muzzle", "Scratcher", "Post", "Tower", "Hide", "Run")
        "Bathroom" -> listOf("Soap", "Towel", "Brush", "Mirror", "Shampoo", "Lotion", "Razor", "Paste", "Sink", "Bath", "Shower", "Mat", "Tile", "Drain", "Tap", "Plug", "Floss", "Comb", "Dryer", "Scale", "Curtain", "Robes", "Slipper", "Cabinet", "Shelf", "Wall", "Floor", "Light", "Vent", "Fan", "Tray", "Rack")
        "Garden" -> listOf("Shovel", "Hose", "Seeds", "Pot", "Mower", "Rake", "Shears", "Fork", "Gloves", "Soil", "Mulch", "Peat", "Fence", "Gate", "Path", "Bench", "Statue", "Pool", "Pond", "Shed", "Barn", "Arbor", "Trellis", "Vine", "Root", "Stem", "Leaf", "Bloom", "Bud", "Seed", "Crop", "Dirt")
        "Camping" -> listOf("Tent", "Bag", "Fire", "Lamp", "Stove", "Pack", "Boot", "Map", "Compass", "Knife", "Tool", "Rope", "Cord", "Hook", "Pole", "Stake", "Tarp", "Mat", "Cooler", "Jug", "Flask", "Mug", "Plate", "Fork", "Spoon", "Snack", "Log", "Wood", "Ash", "Coal", "Camp", "Hike")
        "Gym" -> listOf("Weights", "Bench", "Mat", "Rope", "Treadmill", "Bike", "Cycle", "Rower", "Rack", "Bar", "Bell", "Ball", "Band", "Step", "Box", "Bag", "Glove", "Wrap", "Belt", "Vest", "Shoe", "Short", "Shirt", "Towel", "Water", "Drink", "Shake", "Time", "Clock", "Pulse", "Heart", "Lung")
        "Cosmetics" -> listOf("Lipstick", "Liner", "Cream", "Powder", "Blush", "Gloss", "Balm", "Serum", "Mask", "Oil", "Gel", "Spray", "Mist", "Shadow", "Palette", "Brush", "Sponge", "Wipe", "Pad", "Cotton", "Polish", "File", "Nail", "Hair", "Wax", "Dye", "Tint", "Tone", "Wash", "Scrub", "Peel", "Puff")
        "Board Games" -> listOf("Chess", "Cards", "Dice", "Board", "Tokens", "Pieces", "Player", "Turn", "Win", "Loss", "Draw", "Move", "Rule", "Check", "Mate", "King", "Queen", "Knight", "Rook", "Pawn", "Bishop", "Dealer", "Shuffle", "Deck", "Hand", "Suit", "Trump", "Trick", "Score", "Point", "Tile", "Grid")
        "Mythology" -> listOf("Zeus", "Odin", "Thor", "Dragon", "Phoenix", "Giant", "Troll", "Elf", "Fairy", "Ghost", "Spirit", "God", "Hero", "Villain", "Myth", "Tale", "Lore", "Fate", "Doom", "Luck", "Charm", "Spell", "Magic", "Wand", "Staff", "Sword", "Shield", "Spear", "Bow", "Arrow", "Relic", "Idol")
        "Superheroes" -> listOf("Cape", "Mask", "Hero", "Villain", "Power", "Force", "Fly", "Fast", "Strong", "Web", "Suit", "Identity", "Secret", "Base", "Lair", "Gadget", "Weapon", "Shield", "Justice", "Peace", "War", "Battle", "Fight", "Save", "Help", "Team", "Squad", "Group", "Union", "League", "City", "World")
        "Pirates" -> listOf("Ship", "Map", "Gold", "Sword", "Parrot", "Flag", "Sail", "Mast", "Deck", "Port", "Island", "Sea", "Ocean", "Wave", "Storm", "Crew", "Mate", "Cap", "Hook", "Leg", "Patch", "Eye", "Rum", "Keg", "Barrel", "Chest", "Box", "Key", "Lock", "Trove", "Prize", "Loot")
        "Wild West" -> listOf("Boot", "Hat", "Horse", "Badge", "Lasso", "Saddle", "Spur", "Gun", "Holster", "Belt", "Vest", "Chaps", "Town", "Saloon", "Bank", "Jail", "Sheriff", "Deputy", "Outlaw", "Rancher", "Cowboy", "Cattle", "Herd", "Dust", "Sand", "Desert", "Cactus", "Rock", "Mesa", "Trail", "Stage", "Coach")
        "Medieval" -> listOf("Knight", "Shield", "Castle", "Armor", "King", "Queen", "Prince", "Lance", "Sword", "Mace", "Axe", "Bow", "Arrow", "Tower", "Wall", "Moat", "Gate", "Bridge", "Keep", "Hall", "Feast", "Joust", "Tournament", "Banner", "Sigil", "Seal", "Steed", "Squire", "Page", "Serf", "Lord", "Lady")
        "Prehistoric" -> listOf("Cave", "Fire", "Stone", "Fossil", "Mammoth", "Tiger", "Beast", "Hunt", "Gather", "Tool", "Bone", "Club", "Spear", "Rock", "Wall", "Paint", "Art", "Skin", "Fur", "Ice", "Glacier", "Tundra", "Tree", "Plant", "Berry", "Nut", "Herd", "Pack", "Clan", "Tribe", "Chief", "Land")
        "Beverages" -> listOf("Water", "Juice", "Milk", "Tea", "Coffee", "Soda", "Pop", "Cola", "Wine", "Beer", "Cider", "Syrup", "Shake", "Smoothie", "Drink", "Thirst", "Glass", "Cup", "Mug", "Bottle", "Can", "Straw", "Ice", "Cold", "Hot", "Warm", "Sweet", "Sour", "Bitter", "Fresh", "Pure", "Brew")

        else -> (1..16).map { "$cat $it" }
    }

    private fun drawFromStock() {
        if (levelFinished || isResolvingMove || isDrawingFromStock || isDealing) return

        if (stockDeck.isEmpty()) {
            // Reshuffle logic: if waste has cards, move them back to stock
            if (drawnCardsContainer.childCount > 0) {
                returnToStock()
                Toast.makeText(context, "Stock reshuffled!", Toast.LENGTH_SHORT).show()
            }
            return
        }

        isDrawingFromStock = true
        val cardInfo = stockDeck.removeAt(0)
        stockCountText.text = stockDeck.size.toString()

        val cardView = createCardView(cardInfo, interactive = true)

        // Add to overlay first for a dramatic "Showcase" animation
        overlayContainer.visibility = View.VISIBLE

        val cardWidth = resources.getDimensionPixelSize(R.dimen.card_width)
        val cardHeight = resources.getDimensionPixelSize(R.dimen.card_height)
        overlayContainer.addView(cardView, FrameLayout.LayoutParams(cardWidth, cardHeight))

        val stockLoc = IntArray(2).also { stockContainer.getLocationOnScreen(it) }
        val wasteLoc = IntArray(2).also { drawnCardsContainer.getLocationOnScreen(it) }
        val overlayLoc = IntArray(2).also { overlayContainer.getLocationOnScreen(it) }

        cardView.x = (stockLoc[0] - overlayLoc[0]).toFloat()
        cardView.y = (stockLoc[1] - overlayLoc[1]).toFloat()
        cardView.scaleX = 0.5f
        cardView.scaleY = 0.5f

        cardView.animate()
            .x((wasteLoc[0] - overlayLoc[0]).toFloat())
            .y((wasteLoc[1] - overlayLoc[1]).toFloat())
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(FLIGHT_DURATION_MS)
            .withEndAction {
                if (!isAdded) return@withEndAction
                overlayContainer.removeView(cardView)
                if (overlayContainer.childCount == 0) {
                    overlayContainer.visibility = View.GONE
                }

                if (drawnCardsContainer.childCount > 0) {
                    val oldView = drawnCardsContainer.getChildAt(0)
                    val oldInfo = oldView.tag as CardInfo
                    stockDeck.add(oldInfo)
                    stockCountText.text = stockDeck.size.toString()
                }
                drawnCardsContainer.removeAllViews()
                drawnCardsContainer.addView(cardView)

                cardView.translationX = 0f
                cardView.translationY = 0f
                cardView.scaleX = 1f
                cardView.scaleY = 1f

                moves--
                updateMoves()
                soundEffects.cardDrawn()
                isDrawingFromStock = false
            }
            .start()
    }

    private fun returnToStock() {
        if (drawnCardsContainer.childCount == 0 || levelFinished || isResolvingMove || isDrawingFromStock || isDealing) return

        val cardView = drawnCardsContainer.getChildAt(0)
        val info = cardView.tag as CardInfo

        // Return to bottom of stock for cycling
        stockDeck.add(info)
        stockCountText.text = stockDeck.size.toString()

        drawnCardsContainer.removeAllViews()
    }

    private fun createCardView(info: CardInfo, interactive: Boolean): View {
        val view = layoutInflater.inflate(R.layout.item_game_card, null)

        // Force the card to its standard dimensions
        val width = resources.getDimensionPixelSize(R.dimen.card_width)
        val height = resources.getDimensionPixelSize(R.dimen.card_height)
        view.layoutParams = FrameLayout.LayoutParams(width, height)

        view.findViewById<TextView>(R.id.cardText).text = info.name

        // If it's a Category Card, show the special decoration (0/4, star)
        if (info.isCategoryCard) {
            view.findViewById<View>(R.id.categoryDecoration).visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.categoryProgress).text = "0/$cardsPerFoundation"
        }

        view.tag = info
        if (interactive) {
            view.setOnLongClickListener { v ->
                if (levelFinished || isResolvingMove || isDrawingFromStock || isDealing) {
                    return@setOnLongClickListener false
                }
                isDragging = true
                val dragData = ClipData(info.name, arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), ClipData.Item(info.name))
                v.startDragAndDrop(dragData, View.DragShadowBuilder(v), v, 0)
                v.visibility = View.INVISIBLE
                true
            }
            // Add Drag Listener to allow stacking matching characters
            view.setOnDragListener(CharacterDragListener())
        }
        return view
    }

    /** Allows dragging one matching character onto another to form a stack. */
    inner class CharacterDragListener : View.OnDragListener {
        override fun onDrag(v: View, event: DragEvent): Boolean {
            val targetInfo = v.tag as? CardInfo ?: return false
            if (targetInfo.isCategoryCard) return false

            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> return true
                DragEvent.ACTION_DROP -> {
                    if (levelFinished || isResolvingMove || isDrawingFromStock || isDealing) return true
                    val draggedView = event.localState as View
                    val draggedInfo = draggedView.tag as CardInfo

                    if (draggedInfo.isCategoryCard) return true

                    if (draggedInfo.category == targetInfo.category) {
                        // Check if the foundation is already assigned
                        val foundation = findFoundationFor(draggedInfo.category)
                        val isAssigned = (foundation?.tag as? String) == draggedInfo.category

                        if (!isAssigned) {
                            // STACKING: Move dragged card to the target's column
                            val sourceCol = sourceColumnIndexOf(draggedView)
                            val targetCol = boardColumnViews.indexOf(v.parent as? FrameLayout)

                            if (targetCol >= 0) {
                                isResolvingMove = true
                                if (sourceCol >= 0) {
                                    popExposedCardFromColumn(sourceCol)
                                } else {
                                    drawnCardsContainer.removeAllViews()
                                }
                                pushCardToColumn(targetCol, draggedInfo)
                                soundEffects.correctDrop()
                                isResolvingMove = false
                                // MOVES ARE NOT DECREMENTED FOR STACKING
                                return true
                            }
                        }
                    }
                    draggedView.visibility = View.VISIBLE
                    return true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    isDragging = false
                    if (!event.result) (event.localState as View).visibility = View.VISIBLE
                    return true
                }
            }
            return false
        }
    }

    private fun updateFoundationUI(view: View, category: String, progress: Int, lastItemName: String? = null) {
        val isUnassigned = (view.tag as? String)?.startsWith("unassigned_") == true

        val emptySlot = view.findViewById<View>(R.id.emptySlotIcon)
        val matchedContent = view.findViewById<View>(R.id.matchedContent)
        val categoryTab = view.findViewById<TextView>(R.id.categoryTab)

        if (isUnassigned) {
            emptySlot.visibility = View.VISIBLE
            matchedContent.visibility = View.GONE
            categoryTab.visibility = View.INVISIBLE
        } else {
            emptySlot.visibility = View.GONE
            matchedContent.visibility = View.VISIBLE
            categoryTab.visibility = View.VISIBLE
            categoryTab.text = category

            view.findViewById<TextView>(R.id.foundationProgress).text = "$progress/$cardsPerFoundation"
            view.findViewById<TextView>(R.id.foundationName).text = lastItemName ?: ""
        }
    }

    private fun updateMoves() {
        movesCount.text = moves.toString()
        if (moves <= 0 && totalSorted < totalToSort && !levelFinished) showFailDialog()
    }

    private fun showHint() {
        // 1. Check drawn card
        if (drawnCardsContainer.childCount > 0) {
            val cardView = drawnCardsContainer.getChildAt(0)
            val info = cardView.tag as CardInfo

            // Match with unassigned foundation (Category Card)
            if (info.isCategoryCard) {
                for (i in 0 until foundationRow.childCount) {
                    val f = foundationRow.getChildAt(i)
                    if ((f.tag as? String)?.startsWith("unassigned_") == true) {
                        pulseMatch(cardView, f)
                        return
                    }
                }
            }

            // Match with assigned foundation
            findFoundationFor(info.category)?.let { f ->
                if (!info.isCategoryCard && (foundationProgress[info.category] ?: 0) < cardsPerFoundation) {
                    pulseMatch(cardView, f)
                    return
                }
            }

            // Match with board cards
            for (col in boardColumnViews) {
                if (col.childCount > 0) {
                    val boardView = col.getChildAt(col.childCount - 1)
                    val boardInfo = boardView.tag as CardInfo
                    if (info.category == boardInfo.category && !info.isCategoryCard && !boardInfo.isCategoryCard) {
                        pulseMatch(cardView, boardView)
                        return
                    }
                }
            }
        }

        // 2. Check board columns
        for (i in boardColumnViews.indices) {
            val colA = boardColumnViews[i]
            if (colA.childCount == 0) continue
            val viewA = colA.getChildAt(colA.childCount - 1)
            val infoA = viewA.tag as CardInfo

            // Match with unassigned foundation
            if (infoA.isCategoryCard) {
                for (k in 0 until foundationRow.childCount) {
                    val f = foundationRow.getChildAt(k)
                    if ((f.tag as? String)?.startsWith("unassigned_") == true) {
                        pulseMatch(viewA, f)
                        return
                    }
                }
            }

            // Match with assigned foundation
            findFoundationFor(infoA.category)?.let { f ->
                if (!infoA.isCategoryCard && (foundationProgress[infoA.category] ?: 0) < cardsPerFoundation) {
                    pulseMatch(viewA, f)
                    return
                }
            }

            // Match with other board columns
            for (j in i + 1 until boardColumnViews.size) {
                val colB = boardColumnViews[j]
                if (colB.childCount == 0) continue
                val viewB = colB.getChildAt(colB.childCount - 1)
                val infoB = viewB.tag as CardInfo
                if (infoA.category == infoB.category && !infoA.isCategoryCard && !infoB.isCategoryCard) {
                    pulseMatch(viewA, viewB)
                    return
                }
            }
        }

        Toast.makeText(context, "No obvious matches found!", Toast.LENGTH_SHORT).show()
    }

    private fun findFoundationFor(category: String): View? {
        for (i in 0 until foundationRow.childCount) {
            val foundation = foundationRow.getChildAt(i)
            if (foundation.tag == category) return foundation
        }
        return null
    }

    private fun pulseMatch(card: View, foundation: View) {
        val duration = 400L
        card.animate().scaleX(1.25f).scaleY(1.25f).setDuration(duration).withEndAction {
            card.animate().scaleX(1f).scaleY(1f).setDuration(duration).start()
        }.start()

        foundation.animate().scaleX(1.15f).scaleY(1.15f).setDuration(duration).withEndAction {
            foundation.animate().scaleX(1f).scaleY(1f).setDuration(duration).start()
        }.start()

        Toast.makeText(context, "Hint: Match found!", Toast.LENGTH_SHORT).show()
    }

    private fun sourceColumnIndexOf(view: View): Int {
        val parent = view.parent as? ViewGroup ?: return -1
        return boardColumnViews.indexOf(parent)
    }

    private fun useMagnet() {
        if (levelFinished || isResolvingMove || isDrawingFromStock || isDealing) return
        if (drawnCardsContainer.childCount > 0) {
            val view = drawnCardsContainer.getChildAt(0)
            if (attemptPlaceCard(view, view.tag as CardInfo)) return
        }
        for (colIndex in boardColumnViews.indices) {
            val col = boardColumnViews[colIndex]
            if (col.childCount > 0) {
                val view = col.getChildAt(col.childCount - 1)
                if (attemptPlaceCard(view, view.tag as CardInfo)) return
            }
        }
    }

    /** Used by the magnet button — finds the matching foundation itself. Returns true if match found. */
    private fun attemptPlaceCard(view: View, info: CardInfo): Boolean {
        for (i in 0 until foundationRow.childCount) {
            val foundation = foundationRow.getChildAt(i)
            val tag = foundation.tag as String

            if (info.isCategoryCard) {
                if (tag.startsWith("unassigned_")) {
                    completeMatch(view, info, foundation)
                    return true
                }
            } else if (tag == info.category) {
                completeMatch(view, info, foundation)
                return true
            }
        }
        return false
    }

    /**
     * Handles both assigning a category to an empty slot AND matching
     * characters to an assigned slot.
     */
    private fun completeMatch(cardView: View, info: CardInfo, foundation: View) {
        if (levelFinished || isResolvingMove) return

        val foundationTag = foundation.tag as String
        val isUnassigned = foundationTag.startsWith("unassigned_")

        // Validation logic
        if (isUnassigned && !info.isCategoryCard) {
            // Cannot drop a character into an empty slot
            cardView.visibility = View.VISIBLE
            return
        }
        if (!isUnassigned && info.isCategoryCard) {
            // Cannot drop a category card into a slot already assigned
            cardView.visibility = View.VISIBLE
            return
        }
        if (!isUnassigned && info.category != foundationTag) {
            // Wrong category for this slot
            cardView.visibility = View.VISIBLE
            return
        }

        isResolvingMove = true
        val colIndex = sourceColumnIndexOf(cardView)
        val sourceType = if (colIndex >= 0) SourceType.BOARD else SourceType.STOCK_HAND

        val cardLoc = IntArray(2).also { cardView.getLocationOnScreen(it) }
        val targetLoc = IntArray(2).also { foundation.getLocationOnScreen(it) }
        val overlayLoc = IntArray(2).also { overlayContainer.getLocationOnScreen(it) }
        val flyingCard = createCardView(info, interactive = false)
        val cardWidth = if (cardView.width > 0) cardView.width else resources.getDimensionPixelSize(R.dimen.card_width)
        val cardHeight = if (cardView.height > 0) cardView.height else resources.getDimensionPixelSize(R.dimen.card_height)
        overlayContainer.visibility = View.VISIBLE
        overlayContainer.addView(flyingCard, FrameLayout.LayoutParams(cardWidth, cardHeight))
        flyingCard.x = (cardLoc[0] - overlayLoc[0]).toFloat()
        flyingCard.y = (cardLoc[1] - overlayLoc[1]).toFloat()
        cardView.visibility = View.INVISIBLE

        flyingCard.animate()
            .x((targetLoc[0] - overlayLoc[0]).toFloat())
            .y((targetLoc[1] - overlayLoc[1]).toFloat())
            .scaleX(1.4f)
            .scaleY(1.4f)
            .setDuration(FLIGHT_DURATION_MS)
            .withEndAction {
                flyingCard.animate()
                    .scaleX(0.2f)
                    .scaleY(0.2f)
                    .alpha(0f)
                    .setDuration(100)
                    .withEndAction {
                        overlayContainer.removeView(flyingCard)
                        if (overlayContainer.childCount == 0) overlayContainer.visibility = View.GONE
                        if (!isAdded) {
                            isResolvingMove = false
                            return@withEndAction
                        }

                        moveHistory.push(Move(info, sourceType, colIndex, foundation))

                        if (info.isCategoryCard) {
                            // PHASE 1: Assign Category
                            foundation.tag = info.category
                            updateFoundationUI(foundation, info.category, 0)

                            // REMOVE FROM SOURCE
                            if (colIndex >= 0) {
                                popExposedCardFromColumn(colIndex)
                            } else {
                                drawnCardsContainer.removeAllViews()
                            }
                        } else {
                            // PHASE 2: Match Character(s)
                            if (colIndex >= 0) {
                                // Pull the top card
                                popExposedCardFromColumn(colIndex)
                                foundationProgress[info.category] = (foundationProgress[info.category] ?: 0) + 1
                                updateFoundationUI(foundation, info.category, foundationProgress[info.category]!!, info.name)
                                totalSorted++

                                // Pull any matching characters immediately below it (The Stack).
                                var nextCardInCol = boardColumnsData.getOrNull(colIndex)?.lastOrNull()
                                while (nextCardInCol != null && nextCardInCol.category == info.category && !nextCardInCol.isCategoryCard) {
                                    val matchedCard = popExposedCardFromColumn(colIndex)!!
                                    moveHistory.push(Move(matchedCard, SourceType.BOARD, colIndex, foundation))
                                    foundationProgress[matchedCard.category] = (foundationProgress[matchedCard.category] ?: 0) + 1
                                    updateFoundationUI(foundation, matchedCard.category, foundationProgress[matchedCard.category]!!, matchedCard.name)
                                    totalSorted++
                                    nextCardInCol = boardColumnsData.getOrNull(colIndex)?.lastOrNull()
                                }
                            } else {
                                // From Hand
                                foundationProgress[info.category] = (foundationProgress[info.category] ?: 0) + 1
                                updateFoundationUI(foundation, info.category, foundationProgress[info.category]!!, info.name)
                                totalSorted++
                                drawnCardsContainer.removeAllViews()
                            }
                        }

                        moves--
                        soundEffects.correctDrop()
                        updateMoves()
                        view?.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                        spawnSparkles(targetLoc[0] + cardWidth / 2f, targetLoc[1] + cardHeight / 2f)

                        foundation.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150)
                            .withEndAction { foundation.animate().scaleX(1f).scaleY(1f).setDuration(150).start() }
                            .start()

                        if (totalSorted >= totalToSort) {
                            soundEffects.levelComplete()
                            celebrateVictory()
                            showClearDialog()
                        }
                        isResolvingMove = false
                    }
                    .start()
            }
            .start()
    }

    private fun celebrateVictory() {
        if (!isAdded) return
        val count = 40
        val colors = listOf("#FFD700", "#FF4081", "#00E5FF", "#76FF03", "#D500F9")
        val random = java.util.Random()

        repeat(count) {
            val particle = View(requireContext()).apply {
                val size = 20 + random.nextInt(30)
                layoutParams = FrameLayout.LayoutParams(size, size)
                setBackgroundColor(Color.parseColor(colors.random()))
                alpha = 0.9f
            }
            overlayContainer.addView(particle)
            overlayContainer.visibility = View.VISIBLE

            val startX = random.nextFloat() * overlayContainer.width
            val startY = -100f
            particle.x = startX
            particle.y = startY

            val duration = 1500L + random.nextInt(1500)
            particle.animate()
                .translationYBy(overlayContainer.height + 200f)
                .rotationBy(720f)
                .alpha(0.2f)
                .setDuration(duration)
                .withEndAction {
                    overlayContainer.removeView(particle)
                    if (overlayContainer.childCount == 0) overlayContainer.visibility = View.GONE
                }
                .start()
        }
    }

    private fun undoLastMove() {
        if (moveHistory.isEmpty() || levelFinished || isResolvingMove || isDrawingFromStock || isDealing) return
        val lastMove = moveHistory.pop()

        lastMove.targetFoundation?.let { f ->
            val info = lastMove.cardInfo
            if (info.isCategoryCard) {
                // Return foundation to unassigned state
                val originalIndex = foundationRow.indexOfChild(f)
                f.tag = "unassigned_$originalIndex"
                updateFoundationUI(f, info.category, 0)
            } else {
                val cat = info.category
                foundationProgress[cat] = (foundationProgress[cat] ?: 1) - 1
                updateFoundationUI(f, cat, foundationProgress[cat]!!, if (foundationProgress[cat] == 0) null else "...")
                totalSorted--
            }
        }

        when (lastMove.sourceType) {
            SourceType.BOARD -> pushCardToColumn(lastMove.sourceColumnIndex, lastMove.cardInfo)
            SourceType.STOCK_HAND -> {
                val cardView = createCardView(lastMove.cardInfo, interactive = true)
                drawnCardsContainer.removeAllViews()
                drawnCardsContainer.addView(cardView)
            }
        }

        moves++
        movesCount.text = moves.toString()
    }

    private fun calculateStars(): Int {
        // Updated logic to ensure 2 stars appear properly
        // 3 stars: Perfect game (0 mistakes)
        // 2 stars: Minor errors (1 or 2 mistakes)
        // 1 star: Many errors (3+ mistakes)
        return when {
            mistakeCount == 0 -> 3
            mistakeCount <= 2 -> 2
            else -> 1
        }
    }

    private fun showClearDialog() {
        if (levelFinished) return
        levelFinished = true

        lifecycleScope.launch {
            delay(800)
            if (!isAdded) return@launch

            overlayContainer.removeAllViews()
            overlayContainer.visibility = View.VISIBLE
            val dialogView = layoutInflater.inflate(R.layout.dialog_level_complete, overlayContainer, false)
            overlayContainer.addView(dialogView)

            val stars = calculateStars()
            val earnedXp = 100 + (stars * 50)

            // Set star icons based on performance with a staggered "filling" animation
            val starIds = listOf(R.id.clearStar1, R.id.clearStar2, R.id.clearStar3)
            starIds.forEachIndexed { index, id ->
                val starView = dialogView.findViewById<ImageView>(id)
                val isFilled = stars > index

                // CRUCIAL: Always start as unfilled/off
                starView.setImageResource(android.R.drawable.btn_star_big_off)
                starView.setColorFilter(android.graphics.Color.parseColor("#BDBDBD"))
                starView.alpha = 0.4f
                starView.scaleX = 1.0f
                starView.scaleY = 1.0f

                if (isFilled) {
                    // Staggered fill animation
                    starView.postDelayed({
                        if (!isAdded) return@postDelayed

                        // Change to filled yellow state
                        starView.setImageResource(android.R.drawable.btn_star_big_on)
                        starView.setColorFilter(android.graphics.Color.parseColor("#FFC107"))
                        starView.alpha = 1.0f

                        // Dramatic "Pop" animation
                        starView.scaleX = 0.4f
                        starView.scaleY = 0.4f
                        starView.animate()
                            .scaleX(1.3f)
                            .scaleY(1.3f)
                            .setDuration(250)
                            .withEndAction {
                                if (isAdded) {
                                    starView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                                }
                            }
                            .start()

                        soundEffects.cardDrawn() // Feedback for each star
                    }, 600L + (index * 400L)) // slightly longer delay and stagger
                }
            }

            dialogView.findViewById<TextView>(R.id.xpEarnedDisplay).text = "+$earnedXp XP Earned"

            val doubleXpButton = dialogView.findViewById<View>(R.id.doubleXpButton)
            doubleXpButton.setOnClickListener { button ->
                button.isEnabled = false
                rewardedAdManager.show(
                    onRewardEarned = {
                        if (!isAdded) return@show
                        addXp(earnedXp)
                        Toast.makeText(context, "Bonus +$earnedXp XP earned!", Toast.LENGTH_SHORT).show()
                    },
                    onUnavailable = {
                        if (isAdded) {
                            button.isEnabled = true
                            Toast.makeText(context, "Ad is loading. Please try again shortly.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            val nextButton = dialogView.findViewById<View>(R.id.nextLevelButton)
            nextButton.setOnClickListener {
                nextButton.isEnabled = false
                lifecycleScope.launch {
                    val success = saveProgressAndUnlock(calculateStars())
                    if (!isAdded) return@launch
                    if (!success) {
                        Toast.makeText(context, "Could not save progress, try again", Toast.LENGTH_SHORT).show()
                        nextButton.isEnabled = true
                        return@launch
                    }
                    addXp(earnedXp)

                    // Logic to show interstitial ad after every 2 level completions
                    val prefs = requireContext().getSharedPreferences("ad_tracking", android.content.Context.MODE_PRIVATE)
                    val completedCount = prefs.getInt("completed_levels_count", 0) + 1

                    if (completedCount >= 2) {
                        prefs.edit().putInt("completed_levels_count", 0).apply()
                        interstitialAdManager.show {
                            if (isAdded) {
                                dismissOverlay()
                                findNavController().popBackStack()
                            }
                        }
                    } else {
                        prefs.edit().putInt("completed_levels_count", completedCount).apply()
                        dismissOverlay()
                        findNavController().popBackStack()
                    }
                }
            }
            dialogView.findViewById<View>(R.id.retryButton).setOnClickListener {
                dismissOverlay()
                resetLevel()
            }
        }
    }

    private fun showFailDialog() {
        if (levelFinished) return
        levelFinished = true

        overlayContainer.removeAllViews()
        overlayContainer.visibility = View.VISIBLE
        val dialogView = layoutInflater.inflate(R.layout.dialog_out_of_moves, overlayContainer, false)
        overlayContainer.addView(dialogView)
        dialogView.findViewById<View>(R.id.retryLevelButton).setOnClickListener {
            dismissOverlay()
            resetLevel()
        }
        dialogView.findViewById<View>(R.id.addMovesButton).setOnClickListener { button ->
            button.isEnabled = false
            rewardedAdManager.show(
                onRewardEarned = {
                    if (!isAdded) return@show
                    moves = maxOf(0, moves) + REWARDED_MOVES
                    movesCount.text = moves.toString()
                    levelFinished = false
                    dismissOverlay()
                    Toast.makeText(context, "+$REWARDED_MOVES moves added!", Toast.LENGTH_SHORT).show()
                },
                onUnavailable = {
                    if (isAdded) {
                        button.isEnabled = true
                        Toast.makeText(context, "Ad is loading. Please try again shortly.", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        dialogView.findViewById<View>(R.id.quitButton).setOnClickListener {
            dismissOverlay()
            findNavController().popBackStack()
        }
    }

    private fun dismissOverlay() {
        overlayContainer.removeAllViews()
        overlayContainer.visibility = View.GONE
    }

    private fun resetLevel() {
        moves = startingMoves
        movesCount.text = moves.toString()
        hintCount = 3
        undoCount = 3
        magnetCount = 3
        hintCountText.text = "3"
        undoCountText.text = "3"
        magnetCountText.text = "3"
        setupLevelData()
    }

    private fun addXp(amount: Int) {
        val preferences = requireContext().getSharedPreferences("player_rewards", android.content.Context.MODE_PRIVATE)
        val currentXp = preferences.getInt("total_xp", 0)
        preferences.edit().putInt("total_xp", currentXp + amount).apply()
    }

    private fun showProfileDialog() {
        lifecycleScope.launch {
            val db = WordaireDatabase.getDatabase(requireContext())
            val totalStars = db.levelDao().getTotalStars()
            val levelsCleared = db.levelDao().getTotalLevelsCompleted()
            val preferences = requireContext().getSharedPreferences("player_rewards", android.content.Context.MODE_PRIVATE)
            val totalXp = preferences.getInt("total_xp", 0)

            if (!isAdded) return@launch

            overlayContainer.removeAllViews()
            overlayContainer.visibility = View.VISIBLE
            val dialogView = layoutInflater.inflate(R.layout.dialog_profile, overlayContainer, false)
            overlayContainer.addView(dialogView)

            dialogView.findViewById<TextView>(R.id.profileXpText).text = "Total XP: $totalXp"
            dialogView.findViewById<TextView>(R.id.profileStarsText).text = "Total Stars: $totalStars"
            dialogView.findViewById<TextView>(R.id.profileLevelsText).text = "Levels Cleared: $levelsCleared"

            dialogView.findViewById<View>(R.id.closeProfileButton).setOnClickListener {
                dismissOverlay()
            }
        }
    }

    private fun showSettingsDialog() {
        overlayContainer.removeAllViews()
        overlayContainer.visibility = View.VISIBLE
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, overlayContainer, false)
        overlayContainer.addView(dialogView)

        val settingsPrefs = requireContext().getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)

        val soundToggle = dialogView.findViewById<android.widget.CompoundButton>(R.id.soundToggle)
        val musicToggle = dialogView.findViewById<android.widget.CompoundButton>(R.id.musicToggle)

        soundToggle.isChecked = settingsPrefs.getBoolean("sound_enabled", true)
        musicToggle.isChecked = settingsPrefs.getBoolean("music_enabled", true)

        soundToggle.setOnCheckedChangeListener { _, isChecked ->
            settingsPrefs.edit().putBoolean("sound_enabled", isChecked).apply()
            soundEffects.setSoundEnabled(isChecked)
        }

        musicToggle.setOnCheckedChangeListener { _, isChecked ->
            settingsPrefs.edit().putBoolean("music_enabled", isChecked).apply()
        }

        dialogView.findViewById<View>(R.id.quitLevelButton).setOnClickListener {
            dismissOverlay()
            findNavController().popBackStack()
        }

        dialogView.findViewById<View>(R.id.closeSettingsButton).setOnClickListener {
            dismissOverlay()
        }
    }

    private suspend fun saveProgressAndUnlock(stars: Int): Boolean {
        val id = levelId ?: return false
        val cat = categoryId ?: return false
        return try {
            val db = WordaireDatabase.getDatabase(requireContext())
            val manager = GameProgressManager(db)
            manager.completeLevel(cat, id, levelNum, stars)
            true
        } catch (e: Exception) {
            Log.e("Gameplay", "Failed to save progress", e)
            false
        }
    }

    inner class FoundationDragListener : View.OnDragListener {
        override fun onDrag(v: View, event: DragEvent): Boolean {
            val tag = v.tag as String
            val isUnassigned = tag.startsWith("unassigned_")

            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> return true
                DragEvent.ACTION_DROP -> {
                    if (levelFinished || isResolvingMove || isDrawingFromStock || isDealing) return true
                    val draggedView = event.localState as View
                    val info = draggedView.tag as CardInfo

                    if (isUnassigned) {
                        if (info.isCategoryCard) {
                            completeMatch(draggedView, info, v)
                        } else {
                            // Incorrect attempt to place character in empty box
                            draggedView.visibility = View.VISIBLE
                            shakeView(v)
                            Toast.makeText(context, "Place a Category card here first!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Already assigned
                        if (info.category == tag && !info.isCategoryCard) {
                            completeMatch(draggedView, info, v)
                        } else {
                            draggedView.visibility = View.VISIBLE
                            v.setBackgroundResource(R.drawable.bg_wrong_move)
                            v.postDelayed({ v.background = null }, 600)
                            shakeView(v)
                            mistakeCount++
                            moves--
                            soundEffects.wrongDrop()
                            updateMoves()
                            Toast.makeText(context, "Wrong category!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    isDragging = false
                    if (!event.result) (event.localState as View).visibility = View.VISIBLE
                    return true
                }
            }
            return false
        }
    }

    private fun shakeView(view: View) {
        val originalX = view.translationX
        view.animate()
            .translationX(originalX + 20f)
            .setDuration(50)
            .withEndAction {
                view.animate()
                    .translationX(originalX - 20f)
                    .setDuration(50)
                    .withEndAction {
                        view.animate()
                            .translationX(originalX + 20f)
                            .setDuration(50)
                            .withEndAction {
                                view.animate()
                                    .translationX(originalX)
                                    .setDuration(50)
                                    .start()
                            }.start()
                    }.start()
            }.start()
    }

    private fun spawnSparkles(x: Float, y: Float) {
        val count = 10
        val colors = listOf("#FFD700", "#FFF700", "#FFDF00", "#FFFFFF")
        val overlayLoc = IntArray(2).also { overlayContainer.getLocationOnScreen(it) }

        repeat(count) {
            val sparkle = View(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(16, 16)
                setBackgroundColor(Color.parseColor(colors.random()))
                alpha = 0.9f
                rotation = (Math.random() * 360).toFloat()
            }
            overlayContainer.addView(sparkle)
            overlayContainer.visibility = View.VISIBLE

            sparkle.x = x - overlayLoc[0]
            sparkle.y = y - overlayLoc[1]

            val angle = (Math.random() * 2 * Math.PI).toFloat()
            val dist = 120f + (Math.random() * 150f).toFloat()
            val tx = Math.cos(angle.toDouble()).toFloat() * dist
            val ty = Math.sin(angle.toDouble()).toFloat() * dist

            sparkle.animate()
                .translationXBy(tx)
                .translationYBy(ty)
                .alpha(0f)
                .scaleX(0.2f)
                .scaleY(0.2f)
                .rotationBy(360f)
                .setDuration(700 + (Math.random() * 500).toLong())
                .withEndAction {
                    overlayContainer.removeView(sparkle)
                    if (overlayContainer.childCount == 0) overlayContainer.visibility = View.GONE
                }
                .start()
        }
    }

    override fun onDestroyView() {
        if (::soundEffects.isInitialized) soundEffects.release()
        if (::bannerAdManager.isInitialized) bannerAdManager.destroy()
        super.onDestroyView()
    }
}

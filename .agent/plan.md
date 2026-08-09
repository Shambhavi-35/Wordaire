# Project Plan

Develop Wordaire, a word-sorting game. 
- Implement a Splash Screen.
- Screen 1: Category Selection (as per image_3.png). Remove any 'Enter Realm' screen.
- Screen 2: Level Selection Grid (as per image_4.png).
- Screen 3: Gameplay (as per image_5.png) with drag-and-drop, category slots at top, moves counter, and card stock.
- Use XML, Material 3, and Kotlin. Ensure it's fully functional with progress persistence.

## Project Brief

# Wordaire - Project Brief

Wordaire is a vibrant educational word-sorting game designed for children. The app focuses on vocabulary building through interactive drag-and-drop gameplay, where players organize words into categories to progress through themed learning realms.

## Features

* **Themed Category Selection:** A main menu featuring educational realms such as 'Living World', 'Arts & Culture', and 'Around the Globe' with visual progress tracking.
* **Progressive Level Grid:** A stage selector for each category that displays a grid of locked and unlocked levels, encouraging sequential learning.
* **Drag-and-Drop Sorting Gameplay:** Core interactive mechanic where players sort word cards into specific category buckets (e.g., Body, Family, Weather).
* **Gameplay HUD & Hints:** Real-time tracking of moves and card stock, supplemented by a hint system to guide players through difficult matches.

## High-Level Technical Stack

* **Language:** Kotlin
* **UI Framework:** XML Layouts (View System) using **Material 3 (M3)** for a chunky, rounded, and child-friendly aesthetic.
* **Navigation:** **Jetpack Navigation** for handling transitions between Splash, Category, and Gameplay screens.
* **Asynchronous Logic:** Kotlin Coroutines for managing game state updates and card animations.
* **Storage:** Room Database for persisting level progress and category unlock status.

## Implementation Steps

### Task_1_Setup_Foundation: Configure the project foundation including a vibrant Material 3 XML theme, Edge-to-Edge display, and Jetpack Navigation setup.
- **Status:** COMPLETED
- **Updates:** Task 1 Setup Foundation:
- **Acceptance Criteria:**
  - Material 3 XML theme is configured with a vibrant, energetic color scheme
  - MainActivity implements Edge-to-Edge display
  - Jetpack Navigation NavHost is set up in XML with an initial navigation graph
  - App builds and launches without crashing

### Task_2_Implement_Gameplay: Develop the core Wordaire gameplay screen for a themed realm (e.g., Savanna) using XML layouts. Implement word-sorting logic, object spawning, and the glitch/repair mechanic.
- **Status:** COMPLETED
- **Updates:** Task 2 Implement Gameplay:
- **Acceptance Criteria:**
  - Gameplay UI allows card selection and category zone placement
  - Correct matches trigger object spawning animations (view-based)
  - Incorrect matches trigger 'glitch energy' and repair interaction
  - Core gameplay loop is functional and follows the low-poly aesthetic

### Task_3_Progress_and_Map: Implement the World Progress Map screen and integrate Room database or DataStore for persisting player progress across different vocabulary realms.
- **Status:** COMPLETED
- **Updates:** Task 3 Progress and Map:
- **Acceptance Criteria:**
  - World Progress Map UI displays available and locked realms
  - Room/DataStore correctly persists realm completion status
  - Navigation between the map and gameplay realms is seamless
  - Progress is visually updated on the map upon realm completion

### Task_4_App_Structure_and_Selection: Implement the Splash screen, the Category Selection screen (as per image_3.png), and the Level Selection Grid (as per image_4.png). Integrate Room Database for level progress persistence.
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - Splash screen is functional and navigates to Category Selection
  - Category Selection UI matches image_3.png design
  - Level Selection Grid UI matches image_4.png design
  - Progress and level unlock status are persisted in Room database
- **StartTime:** 2026-07-13 14:51:11 IST

### Task_5_Drag_and_Drop_Gameplay_and_Verification: Refine the Gameplay screen with drag-and-drop mechanics, category slots at top, moves counter, and card stock (as per image_5.png). Perform final verification and stability checks.
- **Status:** PENDING
- **Acceptance Criteria:**
  - Gameplay UI matches image_5.png design
  - Drag-and-drop mechanic for word cards is fully functional
  - Moves counter and card stock correctly update during gameplay
  - Adaptive app icon is implemented
  - Build pass, app does not crash, all tests pass
  - Final verification confirms alignment with all user requirements


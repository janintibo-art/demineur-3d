package com.demineur3d.game

import kotlin.random.Random

/** Difficultés du jeu — identiques à la version web. */
enum class Difficulty(val rows: Int, val cols: Int, val mines: Int, val label: String) {
    EASY(8, 8, 10, "Facile"),
    MEDIUM(12, 12, 24, "Moyen"),
    HARD(16, 16, 40, "Difficile"),
    GIANT(24, 24, 99, "Géant")
}

/** Mode de jeu : classique (1 couche) ou 3D (3 couches empilées). */
enum class GameMode(val layers: Int, val label: String) {
    CLASSIC(1, "Classique"),
    LAYERS(3, "3D Couches")
}

/** État visuel d'une case. */
enum class CellState { HIDDEN, REVEALED, FLAGGED, QUESTIONED }

/** Position d'une case dans la grille 3D (couche, ligne, colonne). */
data class Position(val layer: Int, val row: Int, val col: Int)

/** Résultat d'une action du joueur. */
sealed class ActionResult {
    /** Rien ne s'est passé (case déjà révélée, jeu terminé…). */
    data object None : ActionResult()

    /** Des cases ont été révélées sans toucher de mine. */
    data class Revealed(val count: Int) : ActionResult()

    /** Une mine a explosé à cette position. Partie perdue. */
    data class Exploded(val at: Position) : ActionResult()

    /** Toutes les cases sûres sont révélées. Partie gagnée. */
    data object Won : ActionResult()

    /** Le drapeau / point d'interrogation a changé. */
    data object FlagChanged : ActionResult()
}

/**
 * Moteur du Démineur 3D. Logique pure, sans dépendance Android,
 * portage fidèle de la version web (index.html).
 *
 * - Premier clic toujours sûr (les mines sont placées après).
 * - Révélation en cascade des cases à 0 (voisinage 3D : 26 voisins).
 * - Chord-click : re-cliquer un chiffre dont les drapeaux voisins
 *   correspondent révèle les autres voisins.
 * - Cycle drapeau → ? → vide sur appui long.
 */
class GameEngine(
    val difficulty: Difficulty = Difficulty.EASY,
    val mode: GameMode = GameMode.CLASSIC,
    private val random: Random = Random.Default
) {
    val rows = difficulty.rows
    val cols = difficulty.cols
    val layers = mode.layers
    val totalMines = difficulty.mines * layers

    private val mine = Array(layers) { Array(rows) { BooleanArray(cols) } }
    private val number = Array(layers) { Array(rows) { IntArray(cols) } }
    private val state = Array(layers) { Array(rows) { Array(cols) { CellState.HIDDEN } } }

    var gameActive = true; private set
    var gameWon = false; private set
    var firstClickDone = false; private set
    var remainingFlags = totalMines; private set

    fun stateAt(p: Position): CellState = state[p.layer][p.row][p.col]
    fun numberAt(p: Position): Int = number[p.layer][p.row][p.col]
    fun isMine(p: Position): Boolean = mine[p.layer][p.row][p.col]

    /** Révèle une case (tap). Gère premier clic, chord, explosion et victoire. */
    fun reveal(p: Position): ActionResult {
        if (!gameActive) return ActionResult.None

        if (stateAt(p) == CellState.REVEALED) return chord(p)
        if (stateAt(p) == CellState.FLAGGED || stateAt(p) == CellState.QUESTIONED) return ActionResult.None

        if (!firstClickDone) {
            placeMines(exclude = p)
            firstClickDone = true
        }

        if (isMine(p)) return explode(p)

        val before = revealedSafeCount()
        floodReveal(p)
        val revealed = revealedSafeCount() - before

        if (isWin()) return win()
        return ActionResult.Revealed(revealed)
    }

    /** Appui long : vide → drapeau → ? → vide. */
    fun toggleFlag(p: Position): ActionResult {
        if (!gameActive || stateAt(p) == CellState.REVEALED) return ActionResult.None
        when (stateAt(p)) {
            CellState.HIDDEN -> {
                if (remainingFlags <= 0) return ActionResult.None
                state[p.layer][p.row][p.col] = CellState.FLAGGED
                remainingFlags--
            }
            CellState.FLAGGED -> {
                state[p.layer][p.row][p.col] = CellState.QUESTIONED
                remainingFlags++
            }
            CellState.QUESTIONED -> state[p.layer][p.row][p.col] = CellState.HIDDEN
            else -> return ActionResult.None
        }
        if (isWin()) return win()
        return ActionResult.FlagChanged
    }

    /** Chord-click : révèle les voisins si le nombre de drapeaux correspond. */
    private fun chord(p: Position): ActionResult {
        val value = numberAt(p)
        if (value <= 0) return ActionResult.None

        val neighbors = neighborsOf(p)
        if (neighbors.count { stateAt(it) == CellState.FLAGGED } != value) return ActionResult.None

        var revealed = 0
        for (n in neighbors) {
            if (stateAt(n) != CellState.HIDDEN && stateAt(n) != CellState.QUESTIONED) continue
            if (stateAt(n) == CellState.QUESTIONED) continue
            if (isMine(n)) return explode(n)
            val before = revealedSafeCount()
            floodReveal(n)
            revealed += revealedSafeCount() - before
        }

        if (revealed == 0) return ActionResult.None
        if (isWin()) return win()
        return ActionResult.Revealed(revealed)
    }

    /** Voisinage 3D : jusqu'à 26 voisins (couches adjacentes comprises). */
    fun neighborsOf(p: Position): List<Position> = buildList {
        for (dl in -1..1) for (dr in -1..1) for (dc in -1..1) {
            if (dl == 0 && dr == 0 && dc == 0) continue
            val l = p.layer + dl; val r = p.row + dr; val c = p.col + dc
            if (l in 0 until layers && r in 0 until rows && c in 0 until cols) {
                add(Position(l, r, c))
            }
        }
    }

    private fun placeMines(exclude: Position) {
        var placed = 0
        while (placed < totalMines) {
            val l = random.nextInt(layers)
            val r = random.nextInt(rows)
            val c = random.nextInt(cols)
            if (Position(l, r, c) == exclude || mine[l][r][c]) continue
            mine[l][r][c] = true
            placed++
        }
        computeNumbers()
    }

    private fun computeNumbers() {
        for (l in 0 until layers) for (r in 0 until rows) for (c in 0 until cols) {
            val p = Position(l, r, c)
            number[l][r][c] = if (mine[l][r][c]) -1 else neighborsOf(p).count { isMine(it) }
        }
    }

    /** Révélation en cascade (itérative pour éviter les débordements de pile en mode Géant). */
    private fun floodReveal(start: Position) {
        val stack = ArrayDeque<Position>()
        stack.addLast(start)
        while (stack.isNotEmpty()) {
            val p = stack.removeLast()
            val s = stateAt(p)
            if (s == CellState.REVEALED || s == CellState.FLAGGED) continue
            state[p.layer][p.row][p.col] = CellState.REVEALED
            if (!isMine(p) && numberAt(p) == 0) {
                for (n in neighborsOf(p)) if (stateAt(n) != CellState.REVEALED) stack.addLast(n)
            }
        }
    }

    private fun explode(at: Position): ActionResult {
        gameActive = false
        gameWon = false
        // Révéler toutes les mines
        for (l in 0 until layers) for (r in 0 until rows) for (c in 0 until cols) {
            if (mine[l][r][c]) state[l][r][c] = CellState.REVEALED
        }
        return ActionResult.Exploded(at)
    }

    private fun win(): ActionResult {
        gameWon = true
        gameActive = false
        // Poser un drapeau sur toutes les mines restantes
        for (l in 0 until layers) for (r in 0 until rows) for (c in 0 until cols) {
            if (mine[l][r][c] && state[l][r][c] != CellState.FLAGGED) state[l][r][c] = CellState.FLAGGED
        }
        remainingFlags = 0
        return ActionResult.Won
    }

    private fun isWin(): Boolean {
        for (l in 0 until layers) for (r in 0 until rows) for (c in 0 until cols) {
            if (!mine[l][r][c] && state[l][r][c] != CellState.REVEALED) return false
        }
        return true
    }

    private fun revealedSafeCount(): Int {
        var n = 0
        for (l in 0 until layers) for (r in 0 until rows) for (c in 0 until cols) {
            if (state[l][r][c] == CellState.REVEALED && !mine[l][r][c]) n++
        }
        return n
    }
}

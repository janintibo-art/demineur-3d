package com.demineur3d.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {

    @Test
    fun `le premier clic n'est jamais une mine`() {
        repeat(50) { seed ->
            val engine = GameEngine(Difficulty.EASY, GameMode.CLASSIC, Random(seed))
            val result = engine.reveal(Position(0, 0, 0))
            assertFalse("Le premier clic ne doit pas exploser (seed=$seed)", result is ActionResult.Exploded)
        }
    }

    @Test
    fun `le nombre de mines correspond a la difficulte`() {
        val engine = GameEngine(Difficulty.MEDIUM, GameMode.CLASSIC, Random(1))
        engine.reveal(Position(0, 0, 0))
        var mines = 0
        for (r in 0 until engine.rows) for (c in 0 until engine.cols) {
            if (engine.isMine(Position(0, r, c))) mines++
        }
        assertEquals(Difficulty.MEDIUM.mines, mines)
    }

    @Test
    fun `le mode 3D multiplie les mines par le nombre de couches`() {
        val engine = GameEngine(Difficulty.EASY, GameMode.LAYERS, Random(1))
        assertEquals(3, engine.layers)
        assertEquals(Difficulty.EASY.mines * 3, engine.totalMines)
    }

    @Test
    fun `le voisinage 3D compte jusqu'a 26 voisins`() {
        val engine = GameEngine(Difficulty.EASY, GameMode.LAYERS, Random(1))
        val center = Position(1, 4, 4)
        assertEquals(26, engine.neighborsOf(center).size)
        val corner = Position(0, 0, 0)
        assertEquals(7, engine.neighborsOf(corner).size)
    }

    @Test
    fun `poser un drapeau decremente le compteur puis le point d'interrogation le rend`() {
        val engine = GameEngine(Difficulty.EASY, GameMode.CLASSIC, Random(1))
        val p = Position(0, 3, 3)
        val flagsBefore = engine.remainingFlags

        engine.toggleFlag(p)
        assertEquals(CellState.FLAGGED, engine.stateAt(p))
        assertEquals(flagsBefore - 1, engine.remainingFlags)

        engine.toggleFlag(p)
        assertEquals(CellState.QUESTIONED, engine.stateAt(p))
        assertEquals(flagsBefore, engine.remainingFlags)

        engine.toggleFlag(p)
        assertEquals(CellState.HIDDEN, engine.stateAt(p))
    }

    @Test
    fun `reveler une mine termine la partie et revele toutes les mines`() {
        val engine = GameEngine(Difficulty.EASY, GameMode.CLASSIC, Random(1))
        engine.reveal(Position(0, 0, 0)) // place les mines

        var minePos: Position? = null
        outer@ for (r in 0 until engine.rows) for (c in 0 until engine.cols) {
            val p = Position(0, r, c)
            if (engine.isMine(p) && engine.stateAt(p) != CellState.REVEALED) { minePos = p; break@outer }
        }
        val result = engine.reveal(minePos!!)

        assertTrue(result is ActionResult.Exploded)
        assertFalse(engine.gameActive)
        for (r in 0 until engine.rows) for (c in 0 until engine.cols) {
            val p = Position(0, r, c)
            if (engine.isMine(p)) assertEquals(CellState.REVEALED, engine.stateAt(p))
        }
    }

    @Test
    fun `reveler toutes les cases sures gagne la partie`() {
        val engine = GameEngine(Difficulty.EASY, GameMode.CLASSIC, Random(42))
        engine.reveal(Position(0, 0, 0))

        var lastResult: ActionResult = ActionResult.None
        for (r in 0 until engine.rows) for (c in 0 until engine.cols) {
            val p = Position(0, r, c)
            if (!engine.isMine(p) && engine.stateAt(p) != CellState.REVEALED) {
                lastResult = engine.reveal(p)
            }
        }
        assertTrue(engine.gameWon)
        assertTrue(lastResult is ActionResult.Won)
        assertEquals(0, engine.remainingFlags)
    }

    @Test
    fun `la cascade revele les zones vides`() {
        // Sur un plateau facile, le premier clic révèle généralement plus d'une case
        var cascadeSeen = false
        repeat(20) { seed ->
            val engine = GameEngine(Difficulty.EASY, GameMode.CLASSIC, Random(seed))
            val result = engine.reveal(Position(0, 4, 4))
            if (result is ActionResult.Revealed && result.count > 1) cascadeSeen = true
        }
        assertTrue("Au moins une cascade attendue sur 20 graines", cascadeSeen)
    }
}

package com.demineur3d.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.demineur3d.data.Stats
import com.demineur3d.data.StatsRepository
import com.demineur3d.game.ActionResult
import com.demineur3d.game.Difficulty
import com.demineur3d.game.GameEngine
import com.demineur3d.game.GameMode
import com.demineur3d.game.Position
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État observable de l'interface. */
data class GameUiState(
    val mode: GameMode = GameMode.CLASSIC,
    val difficulty: Difficulty = Difficulty.EASY,
    val currentLayer: Int = 0,
    val remainingFlags: Int = 10,
    val totalMines: Int = 10,
    val timerSeconds: Int = 0,
    val gameActive: Boolean = true,
    val gameWon: Boolean = false,
    val gameOver: Boolean = false,
    val explodedAt: Position? = null,
    val comboStreak: Int = 0,
    val maxStreak: Int = 0,
    val isNewRecord: Boolean = false,
    val stats: Stats = Stats(),
    /** Incrémenté à chaque changement de grille pour forcer la recomposition. */
    val boardVersion: Int = 0
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val statsRepo = StatsRepository(application)

    var engine: GameEngine = GameEngine()
        private set

    private val _uiState = MutableStateFlow(GameUiState(stats = statsRepo.load()))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var comboResetJob: Job? = null

    // ---------- Actions du joueur ----------

    fun onCellTap(p: Position) {
        if (!engine.firstClickDone) startTimer()
        handleResult(engine.reveal(p), tappedLayer = p.layer)
    }

    fun onCellLongPress(p: Position) {
        handleResult(engine.toggleFlag(p), tappedLayer = p.layer)
    }

    fun changeMode(mode: GameMode) {
        _uiState.update { it.copy(mode = mode) }
        resetGame()
    }

    fun changeDifficulty(difficulty: Difficulty) {
        _uiState.update { it.copy(difficulty = difficulty) }
        resetGame()
    }

    fun changeLayer(delta: Int) {
        _uiState.update {
            val newLayer = (it.currentLayer + delta).coerceIn(0, engine.layers - 1)
            it.copy(currentLayer = newLayer)
        }
    }

    fun resetGame() {
        stopTimer()
        val s = _uiState.value
        engine = GameEngine(s.difficulty, s.mode)
        _uiState.update {
            it.copy(
                currentLayer = 0,
                remainingFlags = engine.totalMines,
                totalMines = engine.totalMines,
                timerSeconds = 0,
                gameActive = true,
                gameWon = false,
                gameOver = false,
                explodedAt = null,
                comboStreak = 0,
                maxStreak = 0,
                isNewRecord = false,
                boardVersion = it.boardVersion + 1
            )
        }
    }

    // ---------- Interne ----------

    private fun handleResult(result: ActionResult, tappedLayer: Int) {
        when (result) {
            is ActionResult.Revealed -> {
                addToStreak(result.count)
                refreshBoard()
            }
            is ActionResult.Exploded -> {
                stopTimer()
                statsRepo.recordGame(false, _uiState.value.timerSeconds, _uiState.value.difficulty)
                _uiState.update {
                    it.copy(
                        gameActive = false, gameOver = true, gameWon = false,
                        explodedAt = result.at, stats = statsRepo.load(),
                        boardVersion = it.boardVersion + 1
                    )
                }
            }
            is ActionResult.Won -> {
                stopTimer()
                val time = _uiState.value.timerSeconds
                val record = statsRepo.recordGame(true, time, _uiState.value.difficulty)
                statsRepo.recordStreak(_uiState.value.maxStreak)
                _uiState.update {
                    it.copy(
                        gameActive = false, gameOver = true, gameWon = true,
                        isNewRecord = record, remainingFlags = 0,
                        stats = statsRepo.load(), boardVersion = it.boardVersion + 1
                    )
                }
            }
            is ActionResult.FlagChanged -> refreshBoard()
            ActionResult.None -> Unit
        }
    }

    private fun refreshBoard() {
        _uiState.update {
            it.copy(remainingFlags = engine.remainingFlags, boardVersion = it.boardVersion + 1)
        }
    }

    /** Combo : les révélations rapprochées (< 3 s) s'additionnent, comme sur le web. */
    private fun addToStreak(count: Int) {
        if (count <= 0) return
        comboResetJob?.cancel()
        _uiState.update {
            val streak = it.comboStreak + count
            it.copy(comboStreak = streak, maxStreak = maxOf(it.maxStreak, streak))
        }
        comboResetJob = viewModelScope.launch {
            delay(3000)
            _uiState.update { it.copy(comboStreak = 0) }
        }
    }

    private fun startTimer() {
        if (timerJob != null) return
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(timerSeconds = it.timerSeconds + 1) }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }
}

fun formatTime(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

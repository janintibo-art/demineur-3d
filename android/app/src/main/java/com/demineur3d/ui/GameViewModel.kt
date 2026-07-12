package com.demineur3d.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.demineur3d.audio.AudioEngine
import com.demineur3d.audio.MusicTrack
import com.demineur3d.audio.Sfx
import com.demineur3d.data.Stats
import com.demineur3d.data.StatsRepository
import com.demineur3d.game.ActionResult
import com.demineur3d.game.CellState
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

data class GameUiState(
    val mode: GameMode = GameMode.CLASSIC,
    val difficulty: Difficulty = Difficulty.EASY,
    val music: MusicTrack = MusicTrack.CHOPIN,
    val currentLayer: Int = 0,
    val totalLayers: Int = 1,
    val remainingFlags: Int = 10,
    val totalMines: Int = 10,
    val timerSeconds: Int = 0,
    val gameActive: Boolean = true,
    val gameWon: Boolean = false,
    val gameOver: Boolean = false,
    val explodedAt: Position? = null,
    val comboStreak: Int = 0,
    val maxStreak: Int = 0,
    val comboPopup: Int = 0,
    val isNewRecord: Boolean = false,
    val showStats: Boolean = false,
    val stats: Stats = Stats(),
    val boardVersion: Int = 0
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val statsRepo = StatsRepository(application)
    private val audio = AudioEngine()

    var engine: GameEngine = GameEngine()
        private set

    private val _uiState = MutableStateFlow(GameUiState(stats = statsRepo.load()))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var comboResetJob: Job? = null
    private var popupJob: Job? = null
    private var audioStarted = false

    // ---------- Actions du joueur ----------

    fun onCellTap(p: Position) {
        val st = _uiState.value
        if (!st.gameActive) return
        if (!engine.firstClickDone) {
            startTimer()
            startAudioIfNeeded()
        }
        val wasRevealed = engine.stateAt(p) == CellState.REVEALED
        handleResult(engine.reveal(p), wasChord = wasRevealed)
    }

    fun onCellLongPress(p: Position) {
        val before = engine.stateAt(p)
        val result = engine.toggleFlag(p)
        if (result != ActionResult.None) {
            when (before) {
                CellState.HIDDEN -> audio.playSfx(Sfx.FLAG)
                CellState.FLAGGED -> audio.playSfx(Sfx.QUESTION)
                else -> audio.playSfx(Sfx.REVEAL)
            }
        }
        handleResult(result, wasChord = false)
    }

    fun changeMode(mode: GameMode) {
        _uiState.update { it.copy(mode = mode) }
        resetGame()
    }

    fun changeDifficulty(difficulty: Difficulty) {
        _uiState.update { it.copy(difficulty = difficulty) }
        resetGame()
    }

    fun changeMusic(track: MusicTrack) {
        _uiState.update { it.copy(music = track) }
        if (track == MusicTrack.OFF) audio.stopMusic()
        else if (audioStarted) audio.playMusic(track)
    }

    fun changeLayer(delta: Int) {
        _uiState.update {
            it.copy(currentLayer = (it.currentLayer + delta).coerceIn(0, engine.layers - 1))
        }
    }

    fun toggleStats(show: Boolean) {
        _uiState.update { it.copy(showStats = show, stats = statsRepo.load()) }
    }

    fun resetGame() {
        stopTimer()
        comboResetJob?.cancel()
        popupJob?.cancel()
        val s = _uiState.value
        engine = GameEngine(s.difficulty, s.mode)
        _uiState.update {
            it.copy(
                currentLayer = 0,
                totalLayers = engine.layers,
                remainingFlags = engine.totalMines,
                totalMines = engine.totalMines,
                timerSeconds = 0,
                gameActive = true,
                gameWon = false,
                gameOver = false,
                explodedAt = null,
                comboStreak = 0,
                maxStreak = 0,
                comboPopup = 0,
                isNewRecord = false,
                boardVersion = it.boardVersion + 1
            )
        }
    }

    // ---------- Interne ----------

    private fun handleResult(result: ActionResult, wasChord: Boolean) {
        when (result) {
            is ActionResult.Revealed -> {
                audio.playSfx(if (wasChord) Sfx.CHORD else Sfx.REVEAL)
                addToStreak(result.count)
                refreshBoard()
            }
            is ActionResult.Exploded -> {
                stopTimer()
                audio.playSfx(Sfx.EXPLOSION)
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
                audio.playSfx(Sfx.WIN)
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

    private fun addToStreak(count: Int) {
        if (count <= 0) return
        comboResetJob?.cancel()
        _uiState.update {
            val streak = it.comboStreak + count
            it.copy(comboStreak = streak, maxStreak = maxOf(it.maxStreak, streak))
        }
        val streak = _uiState.value.comboStreak
        if (streak >= 4) {
            popupJob?.cancel()
            _uiState.update { it.copy(comboPopup = streak) }
            popupJob = viewModelScope.launch {
                delay(1000)
                _uiState.update { it.copy(comboPopup = 0) }
            }
        }
        comboResetJob = viewModelScope.launch {
            delay(3000)
            _uiState.update { it.copy(comboStreak = 0) }
        }
    }

    private fun startAudioIfNeeded() {
        if (audioStarted) return
        audioStarted = true
        val track = _uiState.value.music
        if (track != MusicTrack.OFF) audio.playMusic(track)
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

    override fun onCleared() {
        super.onCleared()
        audio.release()
    }
}

fun formatTime(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

package com.demineur3d.data

import android.content.Context
import android.content.SharedPreferences
import com.demineur3d.game.Difficulty

/** Statistiques du joueur, persistées via SharedPreferences (équivalent du localStorage web). */
data class Stats(
    val played: Int = 0,
    val wins: Int = 0,
    val bestStreak: Int = 0,
    val bestTimes: Map<Difficulty, Int?> = Difficulty.entries.associateWith { null }
) {
    val winRate: Int get() = if (played > 0) (wins * 100) / played else 0
}

class StatsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("demineur3d_stats", Context.MODE_PRIVATE)

    fun load(): Stats = Stats(
        played = prefs.getInt(KEY_PLAYED, 0),
        wins = prefs.getInt(KEY_WINS, 0),
        bestStreak = prefs.getInt(KEY_BEST_STREAK, 0),
        bestTimes = Difficulty.entries.associateWith { d ->
            prefs.getInt(keyBestTime(d), -1).takeIf { it >= 0 }
        }
    )

    /** Enregistre une partie terminée. Retourne true si nouveau record de temps. */
    fun recordGame(won: Boolean, timeSec: Int, difficulty: Difficulty): Boolean {
        val stats = load()
        var isRecord = false
        prefs.edit().apply {
            putInt(KEY_PLAYED, stats.played + 1)
            if (won) {
                putInt(KEY_WINS, stats.wins + 1)
                val best = stats.bestTimes[difficulty]
                if (best == null || timeSec < best) {
                    putInt(keyBestTime(difficulty), timeSec)
                    isRecord = true
                }
            }
        }.apply()
        return isRecord
    }

    fun recordStreak(streak: Int) {
        val best = prefs.getInt(KEY_BEST_STREAK, 0)
        if (streak > best) prefs.edit().putInt(KEY_BEST_STREAK, streak).apply()
    }

    private fun keyBestTime(d: Difficulty) = "best_time_${d.name}"

    private companion object {
        const val KEY_PLAYED = "played"
        const val KEY_WINS = "wins"
        const val KEY_BEST_STREAK = "best_streak"
    }
}

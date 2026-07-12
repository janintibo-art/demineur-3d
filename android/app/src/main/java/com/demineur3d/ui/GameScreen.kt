@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.demineur3d.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.demineur3d.game.CellState
import com.demineur3d.game.Difficulty
import com.demineur3d.game.GameMode
import com.demineur3d.game.Position

private val BgDark = Color(0xFF0D1117)
private val Panel = Color(0xFF1A1F2B)
private val Accent = Color(0xFFFF4444)
private val CellHidden = Color(0xFF2A3140)
private val CellRevealed = Color(0xFF141922)
private val NumberColors = listOf(
    Color.Transparent,
    Color(0xFF4FC3F7), Color(0xFF81C784), Color(0xFFFF8A65), Color(0xFFBA68C8),
    Color(0xFFFFD54F), Color(0xFF4DD0E1), Color(0xFFF06292), Color(0xFFFFFFFF)
)

@Composable
fun GameScreen(viewModel: GameViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        Header(state, viewModel)
        Box(modifier = Modifier.weight(1f)) {
            ZoomableBoard(state, viewModel)
            if (state.comboStreak >= 4) ComboPopup(state.comboStreak)
        }
        if (state.mode == GameMode.LAYERS) LayerBar(state, viewModel)
    }

    if (state.gameOver) EndGameDialog(state, viewModel)
}

// ---------- En-tête ----------

@Composable
private fun Header(state: GameUiState, vm: GameViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GameMode.entries.forEach { mode ->
                SelectorChip(mode.label, state.mode == mode) { vm.changeMode(mode) }
            }
            Text("|", color = Color.Gray)
            Difficulty.entries.forEach { diff ->
                SelectorChip(diff.label, state.difficulty == diff) { vm.changeDifficulty(diff) }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfoBadge("💣 ${state.remainingFlags}/${state.totalMines}")
            InfoBadge("⏱ ${formatTime(state.timerSeconds)}")
            state.stats.bestTimes[state.difficulty]?.let { InfoBadge("🏆 ${formatTime(it)}") }
            Button(
                onClick = vm::resetGame,
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(20.dp)
            ) { Text("↻ Rejouer") }
        }
    }
}

@Composable
private fun SelectorChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) Accent else Panel,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Accent else Color(0xFF3A4150)),
        onClick = onClick
    ) {
        Text(
            label,
            color = if (selected) Color.White else Color(0xFF8892A0),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun InfoBadge(text: String) {
    Surface(color = BgDark, shape = RoundedCornerShape(20.dp)) {
        Text(
            text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ---------- Grille zoomable ----------

@Composable
private fun ZoomableBoard(state: GameUiState, vm: GameViewModel) {
    var scale by remember(state.boardVersion == 0) { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.2f, 3f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.graphicsLayer(
                scaleX = scale, scaleY = scale,
                translationX = offsetX, translationY = offsetY
            )
        ) {
            BoardGrid(state, vm)
        }
    }
}

@Composable
private fun BoardGrid(state: GameUiState, vm: GameViewModel) {
    val engine = vm.engine
    // boardVersion force la relecture de l'état du moteur
    @Suppress("UNUSED_EXPRESSION") state.boardVersion

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (r in 0 until engine.rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (c in 0 until engine.cols) {
                    val p = Position(state.currentLayer, r, c)
                    CellView(
                        cellState = engine.stateAt(p),
                        number = engine.numberAt(p),
                        isMine = engine.isMine(p),
                        isExploded = state.explodedAt == p,
                        onTap = { vm.onCellTap(p) },
                        onLongPress = { vm.onCellLongPress(p) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CellView(
    cellState: CellState,
    number: Int,
    isMine: Boolean,
    isExploded: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val bg = when {
        isExploded -> Accent
        cellState == CellState.REVEALED -> CellRevealed
        else -> CellHidden
    }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .border(1.dp, Color(0xFF0A0E14), RoundedCornerShape(5.dp))
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        contentAlignment = Alignment.Center
    ) {
        when {
            cellState == CellState.FLAGGED -> Text("🚩", fontSize = 16.sp)
            cellState == CellState.QUESTIONED -> Text("❓", fontSize = 16.sp)
            cellState == CellState.REVEALED && isMine -> Text("💣", fontSize = 16.sp)
            cellState == CellState.REVEALED && number > 0 -> Text(
                number.toString(),
                color = NumberColors.getOrElse(number) { Color.White },
                fontWeight = FontWeight.Black,
                fontSize = 16.sp
            )
        }
    }
}

// ---------- Barre de couches (mode 3D) ----------

@Composable
private fun LayerBar(state: GameUiState, vm: GameViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel)
            .padding(8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { vm.changeLayer(-1) }, enabled = state.currentLayer > 0) {
            Text("◀ Couche", color = Color.White)
        }
        Text(
            "  ${state.currentLayer + 1} / ${vm.engine.layers}  ",
            color = Accent, fontWeight = FontWeight.Bold, fontSize = 16.sp
        )
        TextButton(onClick = { vm.changeLayer(1) }, enabled = state.currentLayer < vm.engine.layers - 1) {
            Text("Couche ▶", color = Color.White)
        }
    }
}

// ---------- Popup combo ----------

@Composable
private fun ComboPopup(streak: Int) {
    val label = when {
        streak >= 9 -> "GODLIKE !"
        streak >= 8 -> "INSANE !"
        streak >= 7 -> "AMAZING !"
        streak >= 6 -> "GREAT !"
        streak >= 5 -> "COOL !"
        else -> "NICE !"
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Text(
            "$streak× $label",
            color = Color(0xFFFFD54F),
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}

// ---------- Dialogue de fin ----------

@Composable
private fun EndGameDialog(state: GameUiState, vm: GameViewModel) {
    Dialog(onDismissRequest = {}) {
        Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (state.gameWon) "🎉 VICTOIRE ! 🎉" else "💥 GAME OVER 💥",
                    color = if (state.gameWon) Color(0xFF44FF88) else Accent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                if (state.isNewRecord) {
                    Text("⭐ NOUVEAU RECORD ! ⭐", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                }
                Text("Temps : ${formatTime(state.timerSeconds)}", color = Color.White)
                Text("Combo max : ${state.maxStreak} cases", color = Color.White)
                state.stats.bestTimes[state.difficulty]?.let {
                    Text("Record : ${formatTime(it)}", color = Color(0xFF8892A0))
                }
                Text(
                    "Parties : ${state.stats.played} • Victoires : ${state.stats.wins} (${state.stats.winRate}%)",
                    color = Color(0xFF8892A0), fontSize = 12.sp
                )
                Button(
                    onClick = vm::resetGame,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(24.dp)
                ) { Text("Rejouer", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

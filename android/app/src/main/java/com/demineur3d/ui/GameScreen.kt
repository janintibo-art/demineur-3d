@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.demineur3d.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.demineur3d.game.CellState
import com.demineur3d.game.Difficulty
import com.demineur3d.game.GameMode
import com.demineur3d.game.Position
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

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

/** Taille d'une case + espacement (pas de la grille). */
private val CELL_SIZE = 34.dp
private val CELL_PITCH = 36.dp

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
            ) { Text("↻") }
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

// ---------- Grille zoomable + joystick + walker ----------

@Composable
private fun ZoomableBoard(state: GameUiState, vm: GameViewModel) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val pitchPx = with(density) { CELL_PITCH.toPx() }
        val boardWpx = vm.engine.cols * pitchPx
        val boardHpx = vm.engine.rows * pitchPx
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()

        // Zoom initial : la grille entière tient à l'écran (les grilles Géant
        // apparaissent donc entières, dézoomées). Réinitialisé quand la
        // difficulté ou le mode change → plus de zoom "collé" en repassant en Facile.
        val fitScale = (min(maxW / boardWpx, maxH / boardHpx) * 0.95f).coerceAtMost(1.5f)

        var scale by remember(state.difficulty, state.mode) { mutableFloatStateOf(fitScale) }
        var offsetX by remember(state.difficulty, state.mode) { mutableFloatStateOf(0f) }
        var offsetY by remember(state.difficulty, state.mode) { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.difficulty, state.mode) {
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
                Walker(state, vm)
            }
        }

        // Indicateur de zoom + bouton recentrer (comme la version web)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfoBadge("${(scale * 100).roundToInt()}%")
            Surface(
                color = Panel,
                shape = RoundedCornerShape(20.dp),
                onClick = { scale = fitScale; offsetX = 0f; offsetY = 0f }
            ) {
                Text(
                    "⌖ Recentrer",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        // Joystick : déplace la vue (comme sur le web)
        Joystick(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            onMove = { vx, vy ->
                offsetX += vx * 3.5f
                offsetY += vy * 3.5f
            }
        )
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
            .size(CELL_SIZE)
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

// ---------- Le petit bonhomme promeneur ----------

@Composable
private fun Walker(state: GameUiState, vm: GameViewModel) {
    // Position en unités de cases (x = colonne, y = ligne), comme sur le web
    var pos by remember { mutableStateOf(Offset.Zero) }
    var target by remember { mutableStateOf<Offset?>(null) }
    var jumpPhase by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(false) }

    // Boucle d'animation ~60 fps, fidèle à updateWalkerPosition() du web
    LaunchedEffect(state.currentLayer, state.difficulty, state.mode) {
        pos = Offset.Zero
        target = null
        while (true) {
            withFrameNanos { }
            val ui = vm.uiState.value
            val engine = vm.engine
            if (!ui.gameActive && !ui.gameWon) { visible = false; continue }

            val revealedCells = buildList {
                for (r in 0 until engine.rows) for (c in 0 until engine.cols) {
                    val p = Position(ui.currentLayer, r, c)
                    if (engine.stateAt(p) == CellState.REVEALED && !engine.isMine(p)) {
                        add(Offset(c.toFloat(), r.toFloat()))
                    }
                }
            }
            if (revealedCells.isEmpty()) { visible = false; continue }
            visible = true

            if (target == null || Random.nextFloat() < 0.02f) {
                target = revealedCells.random()
            }
            target?.let { t ->
                val dx = t.x - pos.x
                val dy = t.y - pos.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > 0.05f) {
                    pos = Offset(pos.x + dx * 0.03f, pos.y + dy * 0.03f)
                } else {
                    pos = t
                    target = null
                    jumpPhase = 1f // déclenche un petit saut
                }
            }
            if (jumpPhase > 0f) jumpPhase = (jumpPhase - 0.06f).coerceAtLeast(0f)
        }
    }

    if (!visible) return

    val density = LocalDensity.current
    val pitchPx = with(density) { CELL_PITCH.toPx() }
    val jumpOffset = -12f * (1f - (2f * jumpPhase - 1f) * (2f * jumpPhase - 1f)) // parabole

    WalkerBody(
        modifier = Modifier.offset {
            IntOffset(
                x = ((pos.x + 0.5f) * pitchPx - with(density) { 10.dp.toPx() }).roundToInt(),
                y = ((pos.y + 0.5f) * pitchPx - with(density) { 22.dp.toPx() } + jumpOffset).roundToInt()
            )
        }
    )
}

/** Dessin du personnage : tête, yeux, sourire, corps, bras et jambes qui se balancent. */
@Composable
private fun WalkerBody(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "walk")
    val swing by transition.animateFloat(
        initialValue = -25f, targetValue = 25f,
        animationSpec = infiniteRepeatable(tween(320, easing = LinearEasing), RepeatMode.Reverse),
        label = "swing"
    )

    Canvas(modifier = modifier.size(20.dp, 28.dp)) {
        val w = size.width
        val skin = Color(0xFFFFCC80)
        val outfit = Color(0xFF4FC3F7)

        // Jambes (balancement opposé)
        val hipY = size.height * 0.62f
        val legLen = size.height * 0.32f
        val legAngle = Math.toRadians(swing.toDouble())
        drawLine(
            outfit,
            Offset(w * 0.38f, hipY),
            Offset(w * 0.38f + (legLen * kotlin.math.sin(legAngle)).toFloat(), hipY + (legLen * kotlin.math.cos(legAngle)).toFloat()),
            strokeWidth = 4f, cap = StrokeCap.Round
        )
        drawLine(
            outfit,
            Offset(w * 0.62f, hipY),
            Offset(w * 0.62f - (legLen * kotlin.math.sin(legAngle)).toFloat(), hipY + (legLen * kotlin.math.cos(legAngle)).toFloat()),
            strokeWidth = 4f, cap = StrokeCap.Round
        )

        // Bras (balancement opposé aux jambes)
        val shoulderY = size.height * 0.42f
        val armLen = size.height * 0.26f
        drawLine(
            skin,
            Offset(w * 0.25f, shoulderY),
            Offset(w * 0.25f - (armLen * kotlin.math.sin(legAngle)).toFloat(), shoulderY + (armLen * kotlin.math.cos(legAngle)).toFloat()),
            strokeWidth = 3.5f, cap = StrokeCap.Round
        )
        drawLine(
            skin,
            Offset(w * 0.75f, shoulderY),
            Offset(w * 0.75f + (armLen * kotlin.math.sin(legAngle)).toFloat(), shoulderY + (armLen * kotlin.math.cos(legAngle)).toFloat()),
            strokeWidth = 3.5f, cap = StrokeCap.Round
        )

        // Torse
        drawRoundRect(
            outfit,
            topLeft = Offset(w * 0.28f, size.height * 0.36f),
            size = androidx.compose.ui.geometry.Size(w * 0.44f, size.height * 0.3f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )

        // Tête
        val headR = w * 0.32f
        val headC = Offset(w * 0.5f, size.height * 0.2f)
        drawCircle(skin, headR, headC)
        // Yeux
        drawCircle(Color(0xFF222222), 1.6f, Offset(headC.x - headR * 0.38f, headC.y - headR * 0.1f))
        drawCircle(Color(0xFF222222), 1.6f, Offset(headC.x + headR * 0.38f, headC.y - headR * 0.1f))
        // Sourire
        drawArc(
            Color(0xFF222222),
            startAngle = 20f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(headC.x - headR * 0.45f, headC.y - headR * 0.25f),
            size = androidx.compose.ui.geometry.Size(headR * 0.9f, headR * 0.8f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8f, cap = StrokeCap.Round)
        )
    }
}

// ---------- Joystick (déplace la vue, comme sur le web) ----------

@Composable
private fun Joystick(modifier: Modifier = Modifier, onMove: (Float, Float) -> Unit) {
    val density = LocalDensity.current
    val maxDist = with(density) { 35.dp.toPx() }
    var stick by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }

    // Tant que le joystick est tenu, on déplace la vue à chaque frame
    LaunchedEffect(active) {
        while (active) {
            withFrameNanos { }
            if (stick != Offset.Zero) onMove(stick.x / maxDist, stick.y / maxDist)
        }
    }

    Box(
        modifier = modifier
            .size(90.dp)
            .clip(CircleShape)
            .background(Panel.copy(alpha = if (active) 0.95f else 0.7f))
            .border(2.dp, if (active) Accent else Color(0xFF3A4150), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { active = true },
                    onDrag = { change, drag ->
                        change.consume()
                        var next = stick + drag
                        val d = sqrt(next.x * next.x + next.y * next.y)
                        if (d > maxDist) next = Offset(next.x / d * maxDist, next.y / d * maxDist)
                        stick = next
                    },
                    onDragEnd = { active = false; stick = Offset.Zero },
                    onDragCancel = { active = false; stick = Offset.Zero }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(stick.x.roundToInt(), stick.y.roundToInt()) }
                .size(40.dp)
                .clip(CircleShape)
                .background(if (active) Accent else Color(0xFF4A5160))
        )
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

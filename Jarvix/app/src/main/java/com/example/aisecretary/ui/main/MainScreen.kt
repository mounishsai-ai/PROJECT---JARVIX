package com.example.aisecretary.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aisecretary.audio.AmbientAudioPlayer
import com.example.aisecretary.data.ActiveTask
import com.example.aisecretary.theme.AquaAccent
import com.example.aisecretary.theme.ArcBlue
import com.example.aisecretary.theme.ArcBlueDim
import com.example.aisecretary.theme.CardNavy
import com.example.aisecretary.theme.DeepNavy
import com.example.aisecretary.theme.ElectricViolet
import com.example.aisecretary.theme.ElectricVioletLight
import com.example.aisecretary.theme.ErrorRed
import com.example.aisecretary.theme.IronGold
import com.example.aisecretary.theme.SurfaceNavy
import com.example.aisecretary.theme.TextMuted
import com.example.aisecretary.theme.TextPrimary
import com.example.aisecretary.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val DEG2RAD = (PI / 180.0).toFloat()

/** Set once per process - the boot sequence plays on cold start only, not on every screen visit. */
private object BootFlag {
    var played = false
}

// ── Entry point ──────────────────────────────────────────────────────────────

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel,
    isAssistantLaunch: Boolean = false,
    onNavigateToRoutines: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val micLevel by viewModel.micLevel.collectAsStateWithLifecycle()
    val lastResponse by viewModel.lastResponse.collectAsStateWithLifecycle()
    val ambientOn by viewModel.ambientSoundEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current

    viewModel.initAudioRecorder(context)

    // Ambient HUD bed. Tied to the real audio state, not a timer: it is silenced whenever the
    // mic is live (otherwise it bleeds into the recording Gemini receives) or Jarvix is talking.
    // The on/off preference itself is persisted (see Settings), not just an in-memory toggle.
    val ambient = remember { AmbientAudioPlayer(context.applicationContext) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> ambient.setActive(false)
                Lifecycle.Event.ON_RESUME -> Unit  // recomputed by the effect below
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            ambient.release()
        }
    }

    val resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(ambientOn, isRecording, isProcessing, isSpeaking, resumed) {
        ambient.setEnabled(ambientOn)
        ambient.setActive(!isRecording && !isSpeaking && resumed)
    }

    LaunchedEffect(isAssistantLaunch) {
        if (isAssistantLaunch && !isRecording) {
            viewModel.startRecording()
        }
    }

    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.processTimetableImage(context, uri)
        }
    }

    Dashboard(
        state = state,
        isRecording = isRecording,
        isProcessing = isProcessing,
        isSpeaking = isSpeaking,
        micLevel = micLevel,
        lastResponse = lastResponse,
        onMarkDone = { viewModel.markDone(it) },
        onDeleteTask = { viewModel.deleteTask(it) },
        onStartRecord = { viewModel.startRecording() },
        onStopRecord = { viewModel.stopRecording(context) },
        onPickImage = { photoPickerLauncher.launch("image/*") },
        onNavigateToRoutines = onNavigateToRoutines,
        onNavigateToSettings = onNavigateToSettings,
        ambientOn = ambientOn,
        onToggleAmbient = { viewModel.setAmbientSoundEnabled(!ambientOn) }
    )
}

// ── Dashboard shell ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(
    state: DashboardUiState,
    isRecording: Boolean,
    isProcessing: Boolean,
    isSpeaking: Boolean,
    micLevel: Float,
    lastResponse: String?,
    onMarkDone: (Int) -> Unit,
    onDeleteTask: (Int) -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onPickImage: () -> Unit,
    onNavigateToRoutines: () -> Unit,
    onNavigateToSettings: () -> Unit,
    ambientOn: Boolean,
    onToggleAmbient: () -> Unit
) {
    var showBoot by remember { mutableStateOf(!BootFlag.played) }
    var menuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val heroTasks = (state as? DashboardUiState.Success)?.tasks.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("JARVIX", color = TextPrimary, fontWeight = FontWeight.Black, letterSpacing = 3.sp) },
                actions = {
                    IconButton(onClick = onToggleAmbient) {
                        Icon(
                            imageVector = if (ambientOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = stringResource(
                                if (ambientOn) com.example.aisecretary.R.string.ambient_on
                                else com.example.aisecretary.R.string.ambient_off
                            ),
                            tint = if (ambientOn) AquaAccent else TextMuted,
                        )
                    }
                    IconButton(onClick = onPickImage) {
                        Icon(Icons.Default.Add, contentDescription = "Upload Timetable", tint = AquaAccent)
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = ArcBlue)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            containerColor = SurfaceNavy,
                        ) {
                            DropdownMenuItem(
                                text = { Text("Daily Routines", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = ArcBlue) },
                                onClick = { menuExpanded = false; onNavigateToRoutines() },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = ArcBlue) },
                                onClick = { menuExpanded = false; onNavigateToSettings() },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceNavy.copy(alpha = 0.95f))
            )
        },
        containerColor = DeepNavy,
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            HudChrome(Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                StatusStrip(lastResponse)

                OrbitHero(
                    tasks = heroTasks,
                    isRecording = isRecording,
                    isProcessing = isProcessing,
                    isSpeaking = isSpeaking,
                    micLevel = micLevel,
                    onStartRecord = onStartRecord,
                    onStopRecord = onStopRecord,
                    onNodeTap = { taskId ->
                        val index = heroTasks.indexOfFirst { it.id == taskId }
                        if (index >= 0) {
                            // +1: the SectionLabel occupies the first slot in the LazyColumn.
                            coroutineScope.launch { listState.animateScrollToItem(index + 1) }
                        }
                    }
                )

                when (state) {
                    DashboardUiState.Loading -> LoadingPane()
                    is DashboardUiState.Error -> ErrorPane(state.throwable)
                    is DashboardUiState.Success ->
                        TaskList(state.tasks, onMarkDone, onDeleteTask, listState)
                }
            }

            AnimatedVisibility(
                visible = showBoot,
                exit = fadeOut(tween(500)),
                modifier = Modifier.fillMaxSize()
            ) {
                BootOverlay(onFinished = {
                    BootFlag.played = true
                    showBoot = false
                })
            }
        }
    }
}

// ── HUD chrome: scanlines, sweep, corner reticle ─────────────────────────────

@Composable
private fun HudChrome(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "hud")
    val sweep by infinite.animateFloat(
        initialValue = -0.25f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing)),
        label = "sweep"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        var y = 0f
        val step = 3.dp.toPx()
        while (y < h) {
            drawLine(TextMuted.copy(alpha = 0.05f), Offset(0f, y), Offset(w, y), 1f)
            y += step
        }

        val bandCenterY = h * sweep
        val bandHalf = 90.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, AquaAccent.copy(alpha = 0.05f), Color.Transparent),
                startY = bandCenterY - bandHalf,
                endY = bandCenterY + bandHalf,
            ),
            topLeft = Offset(0f, bandCenterY - bandHalf),
            size = Size(w, bandHalf * 2),
        )

        val bracket = 26.dp.toPx()
        val inset = 14.dp.toPx()
        val strokeW = 2.dp.toPx()
        val bracketColor = ArcBlue.copy(alpha = 0.45f)

        drawLine(bracketColor, Offset(inset, inset), Offset(inset + bracket, inset), strokeW)
        drawLine(bracketColor, Offset(inset, inset), Offset(inset, inset + bracket), strokeW)

        drawLine(bracketColor, Offset(w - inset, inset), Offset(w - inset - bracket, inset), strokeW)
        drawLine(bracketColor, Offset(w - inset, inset), Offset(w - inset, inset + bracket), strokeW)

        drawLine(bracketColor, Offset(inset, h - inset), Offset(inset + bracket, h - inset), strokeW)
        drawLine(bracketColor, Offset(inset, h - inset), Offset(inset, h - inset - bracket), strokeW)

        drawLine(bracketColor, Offset(w - inset, h - inset), Offset(w - inset - bracket, h - inset), strokeW)
        drawLine(bracketColor, Offset(w - inset, h - inset), Offset(w - inset, h - inset - bracket), strokeW)
    }
}

// ── Boot sequence ─────────────────────────────────────────────────────────────

@Composable
private fun BootOverlay(onFinished: () -> Unit) {
    val lines = remember {
        listOf(
            "JARVIX MK-I",
            "ARC REACTOR ................ ONLINE",
            "VOICE UPLINK ................ ONLINE",
            "CHRONO SYNC ................. OK",
            "STANDING BY",
        )
    }
    var shown by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        for (i in lines.indices) {
            delay(260)
            shown = i + 1
        }
        delay(650)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ReactorCore(
                size = 96.dp,
                isRecording = false,
                isProcessing = true,
                isSpeaking = false,
                micLevel = 0f,
                interactive = false,
                onPress = {},
                onRelease = {},
            )
            Spacer(Modifier.height(28.dp))
            Column(horizontalAlignment = Alignment.Start) {
                lines.take(shown).forEach { line ->
                    Text(
                        text = line,
                        color = if (line == "STANDING BY") IronGold else AquaAccent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

// ── Status strip (replaces the old duplicate hero title) ────────────────────

@Composable
private fun StatusStrip(lastResponse: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(ArcBlueDim.copy(alpha = 0.35f), Color.Transparent))
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = lastResponse ?: stringResource(com.example.aisecretary.R.string.subtitle),
            color = if (lastResponse != null) AquaAccent else TextSecondary,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp,
        )
    }
}

// ── The reactor + task orbit (the signature element) ─────────────────────────

@Composable
private fun OrbitHero(
    tasks: List<ActiveTask>,
    isRecording: Boolean,
    isProcessing: Boolean,
    isSpeaking: Boolean,
    micLevel: Float,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onNodeTap: (Int) -> Unit,
) {
    val heroHeight = 280.dp
    val minRadius = 84.dp
    val maxRadius = 108.dp
    val shown = remember(tasks) { tasks.take(6) }
    val n = shown.size

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (n > 0) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                shown.forEachIndexed { i, _ ->
                    val (xPx, yPx) = orbitPositionPx(i, n, minRadius, maxRadius, this)
                    drawLine(
                        ArcBlueDim.copy(alpha = 0.55f),
                        c,
                        Offset(c.x + xPx, c.y + yPx),
                        1.dp.toPx(),
                    )
                }
            }

            shown.forEachIndexed { i, task ->
                val (xDp, yDp) = orbitOffsetDp(i, n, minRadius, maxRadius)
                TaskNode(
                    task = task,
                    urgent = i == 0,
                    modifier = Modifier.offset(x = xDp, y = yDp),
                    onClick = { onNodeTap(task.id) },
                )
            }
        }

        ReactorCore(
            size = 124.dp,
            isRecording = isRecording,
            isProcessing = isProcessing,
            isSpeaking = isSpeaking,
            micLevel = micLevel,
            interactive = !isProcessing,
            onPress = onStartRecord,
            onRelease = onStopRecord,
        )

        Text(
            text = when {
                isRecording -> stringResource(com.example.aisecretary.R.string.status_listening)
                isProcessing -> stringResource(com.example.aisecretary.R.string.status_analyzing)
                isSpeaking -> stringResource(com.example.aisecretary.R.string.status_speaking)
                else -> stringResource(com.example.aisecretary.R.string.status_hold_to_speak)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
            color = TextSecondary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
        )
    }
}

/** Half-segment offset keeps nodes off the exact top/bottom/left/right, away from the caption. */
private fun orbitAngleDeg(index: Int, count: Int): Float =
    -90f + (360f / count) * index + (180f / count)

private fun orbitRadiusDp(index: Int, count: Int, minRadius: Dp, maxRadius: Dp): Dp =
    if (count <= 1) minRadius else lerp(minRadius, maxRadius, index / (count - 1).toFloat())

private fun orbitOffsetDp(index: Int, count: Int, minRadius: Dp, maxRadius: Dp): Pair<Dp, Dp> {
    val angleRad = orbitAngleDeg(index, count) * DEG2RAD
    val radius = orbitRadiusDp(index, count, minRadius, maxRadius)
    return radius * cos(angleRad) to radius * sin(angleRad)
}

private fun orbitPositionPx(
    index: Int,
    count: Int,
    minRadius: Dp,
    maxRadius: Dp,
    density: androidx.compose.ui.unit.Density,
): Pair<Float, Float> {
    val angleRad = orbitAngleDeg(index, count) * DEG2RAD
    val radiusPx = with(density) { orbitRadiusDp(index, count, minRadius, maxRadius).toPx() }
    return radiusPx * cos(angleRad) to radiusPx * sin(angleRad)
}

@Composable
private fun TaskNode(
    task: ActiveTask,
    urgent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val label = remember(task.targetTimeIso8601) { formatShortTime(task.targetTimeIso8601) }
    val color = if (urgent) IronGold else AquaAccent

    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(if (urgent) 16.dp else 12.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, color.copy(alpha = 0.5f), CircleShape)
        )
        Spacer(Modifier.height(2.dp))
        Text(label, color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
    }
}

private fun isOverdue(iso: String): Boolean =
    try {
        LocalDateTime.parse(iso).isBefore(LocalDateTime.now())
    } catch (e: Exception) {
        false
    }

private fun formatShortTime(iso: String): String =
    try {
        LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        "--:--"
    }

// ── Reactor core (Canvas-drawn, reacts to real mic amplitude) ────────────────

@Composable
private fun ReactorCore(
    size: Dp,
    isRecording: Boolean,
    isProcessing: Boolean,
    isSpeaking: Boolean,
    micLevel: Float,
    interactive: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    val infinite = rememberInfiniteTransition(label = "reactor")
    val breathe by infinite.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val processingRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "spin",
    )
    val smoothedMic by animateFloatAsState(
        targetValue = micLevel,
        animationSpec = tween(90),
        label = "mic",
    )

    val coreScale = when {
        isRecording -> 1f + smoothedMic * 0.22f
        isSpeaking -> 1.04f
        else -> breathe
    }
    val ringColor = when {
        isSpeaking -> AquaAccent
        isProcessing -> IronGold
        isRecording -> ArcBlue
        else -> ArcBlueDim
    }
    val glowAlpha = when {
        isRecording -> 0.30f + smoothedMic * 0.45f
        isSpeaking -> 0.55f
        isProcessing -> 0.4f
        else -> 0.18f
    }

    val pressModifier = if (interactive) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(onPress = {
                onPress()
                try {
                    awaitRelease()
                } finally {
                    onRelease()
                }
            })
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .size(size)
            .scale(coreScale)
            .then(pressModifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = this.size.minDimension / 2f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ringColor.copy(alpha = glowAlpha), Color.Transparent),
                    center = c,
                    radius = r * 1.3f,
                ),
                radius = r * 1.3f,
                center = c,
            )

            val segments = 12
            val gapDeg = 6f
            val sweepDeg = 360f / segments - gapDeg
            val ringDiameter = r * 1.7f
            val ringTopLeft = Offset(c.x - ringDiameter / 2, c.y - ringDiameter / 2)
            for (i in 0 until segments) {
                val start = i * (360f / segments) + (if (isProcessing) processingRotation else 0f)
                drawArc(
                    color = ringColor.copy(alpha = 0.55f),
                    startAngle = start,
                    sweepAngle = sweepDeg,
                    useCenter = false,
                    topLeft = ringTopLeft,
                    size = Size(ringDiameter, ringDiameter),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            drawCircle(color = DeepNavy, radius = r * 0.68f, center = c)
            drawCircle(
                color = ringColor.copy(alpha = 0.9f),
                radius = r * 0.68f,
                center = c,
                style = Stroke(width = 2.dp.toPx()),
            )

            val triRadius = r * 0.42f
            val trianglePath = Path().apply {
                for (i in 0..2) {
                    val angle = (-90f + i * 120f) * DEG2RAD
                    val x = c.x + triRadius * cos(angle)
                    val y = c.y + triRadius * sin(angle)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(
                path = trianglePath,
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.95f), ringColor, ringColor.copy(alpha = 0.5f)),
                    center = c,
                    radius = triRadius,
                ),
            )

            drawCircle(color = Color.White.copy(alpha = 0.85f), radius = r * 0.08f, center = c)
        }
    }
}

// ── Task list (interaction lives here; the orbit above is the visualization) ─

@Composable
private fun TaskList(
    tasks: List<ActiveTask>,
    onMarkDone: (Int) -> Unit,
    onDeleteTask: (Int) -> Unit,
    listState: LazyListState,
) {
    if (tasks.isEmpty()) {
        EmptyState()
    } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionLabel("${stringResource(com.example.aisecretary.R.string.pending_reminders)}  •  ${tasks.size}")
            }
            itemsIndexed(tasks, key = { _, t -> t.id }) { index, task ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(300, delayMillis = index * 60)) +
                        slideInVertically(tween(300, delayMillis = index * 60)) { it / 4 },
                ) {
                    TaskCard(task = task, onMarkDone = onMarkDone, onDeleteTask = onDeleteTask)
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: ActiveTask,
    onMarkDone: (Int) -> Unit,
    onDeleteTask: (Int) -> Unit,
) {
    val overdue = remember(task.targetTimeIso8601) { isOverdue(task.targetTimeIso8601) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(ElectricViolet.copy(alpha = 0.4f), AquaAccent.copy(alpha = 0.2f))),
                shape = RoundedCornerShape(16.dp),
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(colors = listOf(ArcBlue, ArcBlueDim), radius = 16f)
                    ),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (overdue) "MISSED  •  ${task.targetTimeIso8601}" else task.targetTimeIso8601,
                    color = if (overdue) IronGold else AquaAccent,
                    fontSize = 12.sp,
                    fontFamily = if (overdue) FontFamily.Monospace else FontFamily.Default,
                )
                if (task.reasoning.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = task.reasoning,
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { onMarkDone(task.id) }) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(com.example.aisecretary.R.string.mark_done),
                    tint = ElectricVioletLight,
                )
            }
            IconButton(onClick = { onDeleteTask(task.id) }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(com.example.aisecretary.R.string.delete_task),
                    tint = TextMuted,
                )
            }
        }
    }
}

// ── Empty / loading / error panes ────────────────────────────────────────────

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎙️", fontSize = 52.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(com.example.aisecretary.R.string.empty_state_title),
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(com.example.aisecretary.R.string.empty_state_subtitle),
                color = TextSecondary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp),
    )
}

@Composable
private fun LoadingPane() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ReactorCore(
            size = 72.dp,
            isRecording = false,
            isProcessing = true,
            isSpeaking = false,
            micLevel = 0f,
            interactive = false,
            onPress = {},
            onRelease = {},
        )
    }
}

@Composable
private fun ErrorPane(throwable: Throwable) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Error: ${throwable.message}", color = MaterialTheme.colorScheme.error)
    }
}

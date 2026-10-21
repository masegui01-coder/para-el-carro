package com.paraelcarro.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.key
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.zIndex
import com.paraelcarro.ui.components.Avatar
import com.paraelcarro.ui.components.AvatarAssets
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paraelcarro.domain.format
import com.paraelcarro.domain.model.GameState
import com.paraelcarro.domain.model.RoundResult
import com.paraelcarro.ui.Feedback
import com.paraelcarro.ui.components.QuestionCard
import com.paraelcarro.ui.components.RoundHeader
import com.paraelcarro.ui.components.WoodButton
import com.paraelcarro.ui.components.Tone
import com.paraelcarro.ui.theme.Rustic
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Estados visibles de la animación. */
enum class BridgeAnimState { Walking, Suspense, CrossedSuccessfully, FellIntoRiver }

@Composable
fun BridgeCrossingScreen(state: GameState, result: RoundResult, feedback: Feedback, onFinished: () -> Unit) {
    val q = result.question
    val driver = state.player(result.driverId)!!
    var animState by remember { mutableStateOf(BridgeAnimState.Walking) }
    var skip by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RoundHeader(state.roundNumber, state.config.rounds, q)
        QuestionCard(q, small = true)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Rustic.MaderaClara.copy(alpha = .45f), RoundedCornerShape(14.dp)),
        ) {
            BridgeScene(
                driver = driver.avatar,
                passengers = result.passengers.mapNotNull { state.player(it)?.avatar },
                success = result.success,
                fastForward = skip,
                onStateChange = { s ->
                    animState = s
                    when (s) {
                        BridgeAnimState.Suspense -> feedback.creak()
                        BridgeAnimState.CrossedSuccessfully -> feedback.crossed()
                        BridgeAnimState.FellIntoRiver -> feedback.fell()
                        BridgeAnimState.Walking -> Unit
                    }
                },
                onDone = onFinished,
            )
            val label = if (animState == BridgeAnimState.Walking) "${driver.name} apuesta por ${q.format(result.bid)}"
            else "Respuesta real: ${q.format(q.answer)}"
            Text(
                label,
                color = when (animState) {
                    BridgeAnimState.Walking -> Rustic.AmbarClaro
                    else -> if (result.success) Rustic.Verde else Rustic.Rojo
                },
                fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .35f)).padding(6.dp),
            )
        }
        WoodButton("Saltar ⏩", { skip = true }, Modifier.fillMaxWidth(), tone = Tone.FANTASMA, small = true)
    }
}

// --------------------------------------------------------------------------- Escena

// Coordenadas lógicas (igual que js/bridge.js); el Canvas se escala a su ancho.
private const val W = 400f
private const val H = 260f
private const val CLIFF_Y = 140f
private const val LEFT = 80f
private const val RIGHT = 320f
private const val MID = 200f
private const val HALF = 120f
private const val SAG = 18f
private const val RIVER_Y = 226f
private const val GS = 1.35f // escala del grupo burra + carro

// Mini escena del turno (igual que BridgeScene.rig en js/bridge.js)
private const val RIG_W = 400f
private const val RIG_H = 140f
private const val RIG_S = 2f
private const val RIG_GX = 250f
private const val RIG_GY = 118f

private const val T_WALK = 2800f
private const val T_SUSPENSE = 2200f
private const val T_CROSS = 2400f
private const val T_PARTY = 2200f
private const val T_FALL = 4000f

private fun bridgeY(x: Float) = if (x <= LEFT || x >= RIGHT) CLIFF_Y else CLIFF_Y + SAG * (1 - ((x - MID) / HALF).pow(2))
private fun bridgeSlope(x: Float) = if (x <= LEFT || x >= RIGHT) 0f else SAG * (-2 * (x - MID) / (HALF * HALF))
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun clamp01(t: Float) = t.coerceIn(0f, 1f)
private fun easeInOut(t: Float) = if (t < .5f) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2
private fun deg(rad: Float) = rad * 180f / PI.toFloat()

private fun seatOf(i: Int, n: Int): Offset {
    val span = min(44f, 10f + n * 7f)
    val x = if (n == 1) -29f else -29f - span / 2 + span * i / (n - 1)
    return Offset(x, -46f - (i % 2) * 5f)
}

private class Segment(val a: Offset, val b: Offset, val width: Float, val color: Color)
private class Plank(val x: Float, val w: Float, val tilt: Float, val shade: Float)
private class Rider(val ch: String, var x: Float, var y: Float, var vx: Float, var vy: Float, var rot: Float, val vr: Float, val size: Float) {
    var sunk = false
    var sinkT = 0f
}
private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Color, val drop: Boolean, var rot: Float = 0f, val vr: Float = 0f, val r: Float = 1.5f)

private fun branches(out: MutableList<Segment>, x: Float, y: Float, len: Float, angle: Float, depth: Int, rnd: Random, color: Color) {
    if (depth == 0 || len < 2) return
    val x2 = x + cos(angle) * len
    val y2 = y + sin(angle) * len
    out += Segment(Offset(x, y), Offset(x2, y2), max(.6f, depth * .9f), color)
    val n = 2 + if (rnd.nextFloat() < .35f) 1 else 0
    repeat(n) { branches(out, x2, y2, len * (.62f + rnd.nextFloat() * .18f), angle + (rnd.nextFloat() - .5f) * 1.4f, depth - 1, rnd, color) }
}

/** Simulación independiente de Compose: avanza con step(dt) y se dibuja con draw(). */
private class BridgeSimulation(val driver: String, val passengers: List<String>, val success: Boolean) {
    var t = 0f
    var state = BridgeAnimState.Walking
    var done = false
    private val rnd = Random(11)
    private val trees = mutableListOf<Segment>().apply {
        val r = Random(7)
        repeat(9) { i -> branches(this, 20f + i * 45f + r.nextFloat() * 20f, 150f, 18f + r.nextFloat() * 12f, -PI.toFloat() / 2, 5, r, Color(0xE6283040)) }
        branches(this, 14f, CLIFF_Y, 34f, -PI.toFloat() / 2 + .1f, 7, r, Color(0xFF0D0F13))
        branches(this, 388f, CLIFF_Y, 38f, -PI.toFloat() / 2 - .15f, 7, r, Color(0xFF0D0F13))
    }
    private val stars = List(40) { Offset(rnd.nextFloat() * W, rnd.nextFloat() * 90f) to (.2f + rnd.nextFloat() * .5f) }
    private val planks = generateSequence(LEFT + 3f) { it + 6.5f }.takeWhile { it < RIGHT - 2f }
        .mapNotNull { x -> if (rnd.nextFloat() < .07f) null else Plank(x, 4.5f + rnd.nextFloat(), (rnd.nextFloat() - .5f) * .15f, 80f + rnd.nextFloat() * 40f) }
        .toList()
    private val particles = mutableListOf<Particle>()

    // Caída
    private var falling = false
    private var fx = MID
    private var fy = 0f
    private var fvy = -40f
    private var frot = 0f
    private var fSunk = false
    private var fSinkT = 0f
    private var fallT0 = 0f
    private var riders = emptyList<Rider>()
    private var splashT = -1f

    private val end = if (success) T_WALK + T_SUSPENSE + T_CROSS + T_PARTY else T_WALK + T_SUSPENSE + T_FALL

    /** Avanza dtMs milisegundos; devuelve el nuevo estado si ha cambiado. */
    fun step(dtMs: Float): BridgeAnimState? {
        val before = state
        t += dtMs
        val dt = dtMs / 1000f
        val after = T_WALK + T_SUSPENSE
        if (t >= T_WALK && state == BridgeAnimState.Walking) state = BridgeAnimState.Suspense

        if (success) {
            if (t >= after + T_CROSS && state != BridgeAnimState.CrossedSuccessfully) {
                state = BridgeAnimState.CrossedSuccessfully
                confetti()
            }
        } else if (t >= after + 400f) {
            if (!falling) startFall()
            if (!fSunk) {
                fvy += 520f * dt; fy += fvy * dt; frot += .9f * dt
                if (fy >= RIVER_Y + 6) {
                    fSunk = true; fSinkT = t; splash(fx, true); splashT = t
                    state = BridgeAnimState.FellIntoRiver
                }
            }
            riders.forEach { r ->
                if (r.sunk) return@forEach
                r.vy += 520f * dt; r.x += r.vx * dt; r.y += r.vy * dt; r.rot += r.vr * dt
                r.x = r.x.coerceIn(LEFT + 8, RIGHT - 8)
                if (r.y >= RIVER_Y + 2) { r.sunk = true; r.sinkT = t; r.y = RIVER_Y + 2; splash(r.x, false) }
            }
        }

        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0) { it.remove(); continue }
            if (p.drop) p.vy += 300f * dt
            p.x += p.vx * dt; p.y += p.vy * dt; p.rot += p.vr * dt
        }

        if (t >= end) {
            if (success && state != BridgeAnimState.CrossedSuccessfully) state = BridgeAnimState.CrossedSuccessfully
            if (!success && state != BridgeAnimState.FellIntoRiver) state = BridgeAnimState.FellIntoRiver
            done = true
        }
        return if (state != before) state else null
    }

    private fun startFall() {
        falling = true
        fallT0 = t
        fy = bridgeY(MID)
        riders = buildList {
            add(Rider(driver, fx + 26 * GS, fy - 36 * GS, 70f, -170f, 0f, 6f, 15 * GS))
            passengers.forEachIndexed { i, p ->
                val s = seatOf(i, passengers.size)
                add(Rider(p, fx + s.x * GS, fy + s.y * GS, (rnd.nextFloat() - .5f) * 120f, -120f - rnd.nextFloat() * 90f, 0f, (rnd.nextFloat() - .5f) * 12f, 13 * GS))
            }
        }
    }

    private fun confetti() {
        val colors = listOf(Rustic.Ambar, Rustic.AmbarClaro, Rustic.Verde, Color(0xFFE8DCFF), Rustic.Rojo)
        repeat(70) { i ->
            particles += Particle(
                250f + rnd.nextFloat() * 140f, -10f - rnd.nextFloat() * 60f, (rnd.nextFloat() - .5f) * 30f, 40f + rnd.nextFloat() * 60f,
                3f, colors[i % colors.size], drop = false, rot = rnd.nextFloat() * 6f, vr = (rnd.nextFloat() - .5f) * 10f,
            )
        }
    }

    private fun splash(x: Float, big: Boolean) {
        repeat(if (big) 34 else 12) {
            val a = -PI.toFloat() / 2 + (rnd.nextFloat() - .5f) * 1.6f
            val v = (if (big) 90f else 60f) + rnd.nextFloat() * 80f
            particles += Particle(x, RIVER_Y, cos(a) * v, sin(a) * v, 1.4f, Color(0xFFAAD7E6), drop = true, r = 1f + rnd.nextFloat() * 1.8f)
        }
    }

    // ----------------------------------------------------------------------- Dibujo

    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val plafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); textSize = 26f
    }

    /** Dibujos de los avatares ilustrados ("av:gato" -> Painter); los emoji se dibujan como texto. */
    var painters: Map<String, Painter> = emptyMap()

    private fun DrawScope.emoji(ch: String, x: Float, y: Float, size: Float, angle: Float = 0f) {
        val painter = painters[ch]
        if (painter != null) {
            val s = size * 1.25f
            withTransform({ translate(x, y); rotate(deg(angle), Offset.Zero); translate(-s / 2, -s / 2) }) {
                with(painter) { draw(Size(s, s)) }
            }
            return
        }
        withTransform({ translate(x, y); rotate(deg(angle), Offset.Zero) }) {
            drawIntoCanvas { c ->
                emojiPaint.textSize = size
                c.nativeCanvas.drawText(ch, 0f, size * .35f, emojiPaint)
            }
        }
    }

    private fun DrawScope.oval(color: Color, cx: Float, cy: Float, rx: Float, ry: Float) =
        drawOval(color, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))

    fun DrawScope.draw() {
        val k = size.width / W
        withTransform({ scale(k, k, Offset.Zero) }) {
            background()
            river()
            val after = T_WALK + T_SUSPENSE
            val wobble = when (state) {
                BridgeAnimState.Suspense -> if (success || t < after) sin(t / 30f) * 1.2f * clamp01((t - T_WALK) / 600f) else 0f
                BridgeAnimState.Walking -> 1.5f * clamp01((t - 1800f) / 1000f)
                else -> 0f
            }
            val breakT = if (success) 0f else clamp01((t - after) / 400f)
            bridge(breakT, wobble)

            if (success || t < after) {
                val x = when {
                    t < T_WALK -> lerp(28f, MID, easeInOut(t / T_WALK))
                    t < after -> MID
                    else -> lerp(MID, 330f, easeInOut(clamp01((t - after) / T_CROSS)))
                }
                val moving = t < T_WALK || (t >= after && t < after + T_CROSS)
                val y = bridgeY(x) + if (state == BridgeAnimState.Suspense) wobble * .8f else 0f
                group(x, y, atan(bridgeSlope(x)) * .6f, if (moving) t / 110f else 0f, riders = true, party = state == BridgeAnimState.CrossedSuccessfully, lantern = 1f, sad = false)
            } else {
                val gy: Float
                val rot: Float
                if (fSunk) {
                    gy = RIVER_Y + 6 + clamp01((t - fSinkT) / 1500f) * 16 + sin(t / 300f) * 1.5f
                    rot = frot + sin(t / 400f) * .05f
                } else {
                    gy = if (falling) fy else bridgeY(MID)
                    rot = frot
                }
                if (fSunk) {
                    clipRect(0f, 0f, W, RIVER_Y + 4) { group(fx, gy, rot, t / 60f, riders = false, party = false, lantern = .3f, sad = true) }
                } else {
                    group(fx, gy, rot, t / 60f, riders = !falling, party = false, lantern = 1f, sad = true)
                }
                riders.forEachIndexed { i, r ->
                    val y = if (r.sunk) RIVER_Y + 1 + sin(t / 250f + i) * 1.5f else r.y
                    emoji(r.ch, r.x, y, r.size, if (r.sunk) sin(t / 300f + i) * .3f else r.rot)
                    if (r.sunk && t - r.sinkT > 700) emoji("💦", r.x + 9, y - 10, 9f)
                }
            }

            particles.forEach { p ->
                if (p.drop) drawCircle(p.color.copy(alpha = min(1f, p.life)), p.r, Offset(p.x, p.y))
                else withTransform({ translate(p.x, p.y); rotate(deg(p.rot), Offset.Zero) }) {
                    drawRect(p.color, Offset(-2f, -1f), Size(4f, 2.5f))
                }
            }

            fog()

            if (splashT >= 0 && t - splashT < 1600) {
                val s = clamp01((t - splashT) / 250f)
                withTransform({ translate(MID, 196f); scale(s, s, Offset.Zero) }) {
                    drawIntoCanvas { c ->
                        plafPaint.style = Paint.Style.STROKE; plafPaint.strokeWidth = 4f; plafPaint.color = 0xFF061015.toInt()
                        c.nativeCanvas.drawText("¡PLAF!", 0f, 0f, plafPaint)
                        plafPaint.style = Paint.Style.FILL; plafPaint.color = Color(0xFFBFE6F0).toArgb()
                        c.nativeCanvas.drawText("¡PLAF!", 0f, 0f, plafPaint)
                    }
                }
            }
        }
    }

    /** Mini escena del turno: suelo, carro y burra parados, sin jinetes (van encima como avatares animados). */
    fun DrawScope.drawRig() {
        val k = size.width / RIG_W
        withTransform({ scale(k, k, Offset.Zero) }) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1D2430), Color(0xFF141820)), 0f, RIG_H), size = Size(RIG_W, RIG_H))
            drawRect(Color(0xFF3A3129), Offset(0f, RIG_GY), Size(RIG_W, RIG_H - RIG_GY))
            drawRect(Rustic.Musgo, Offset(0f, RIG_GY - 2), Size(RIG_W, 5f))
            withTransform({ translate(RIG_GX, RIG_GY); scale(RIG_S, RIG_S, Offset.Zero) }) {
                cart(0f, 1f)
                donkey(0f, false)
            }
        }
    }

    private fun DrawScope.background() {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF26303F), Color(0xFF151A23), Color(0xFF0A0C10)), 0f, H), size = Size(W, H))
        stars.forEach { (p, a) -> drawRect(Color.White.copy(alpha = a), p, Size(1f, 1f)) }
        drawCircle(Brush.radialGradient(listOf(Color(0x8CE6EBFF), Color(0x00E6EBFF)), Offset(330f, 42f), 60f), 60f, Offset(330f, 42f))
        drawCircle(Color(0xFFE9EDF7), 17f, Offset(330f, 42f))
        drawCircle(Color(0x59A0AABE), 4f, Offset(325f, 38f))
        drawCircle(Color(0x59A0AABE), 3f, Offset(336f, 48f))
        trees.forEach { s -> drawLine(s.color, s.a, s.b, s.width, StrokeCap.Round) }
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1A1F27), Color(0xFF07090C)), CLIFF_Y, H), Offset(LEFT - 10, CLIFF_Y), Size(RIGHT - LEFT + 20, H - CLIFF_Y))
        val rock = Brush.verticalGradient(listOf(Color(0xFF3A3129), Color(0xFF14110E)), CLIFF_Y, H)
        fun cliff(vararg pts: Pair<Float, Float>) {
            val p = Path().apply {
                moveTo(pts[0].first, pts[0].second)
                pts.drop(1).forEach { (x, y) -> lineTo(x, y) }
                close()
            }
            drawPath(p, rock)
        }
        cliff(0f to CLIFF_Y, LEFT to CLIFF_Y, LEFT + 8 to 168f, LEFT - 4 to 196f, LEFT + 6 to 230f, LEFT - 2 to H, 0f to H)
        cliff(W to CLIFF_Y, RIGHT to CLIFF_Y, RIGHT - 7 to 165f, RIGHT + 5 to 198f, RIGHT - 6 to 232f, RIGHT + 2 to H, W to H)
        drawRect(Rustic.Musgo, Offset(0f, CLIFF_Y - 3), Size(LEFT + 2, 5f))
        drawRect(Rustic.Musgo, Offset(RIGHT - 2, CLIFF_Y - 3), Size(W - RIGHT + 2, 5f))
    }

    private fun DrawScope.river() {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF13303A), Color(0xFF061015)), RIVER_Y, H), Offset(LEFT - 6, RIVER_Y), Size(RIGHT - LEFT + 12, H - RIVER_Y))
        repeat(3) { row ->
            val p = Path()
            var x = LEFT - 6
            while (x <= RIGHT + 6) {
                val y = RIVER_Y + 6 + row * 10 + sin(x / 12f + t / 400f + row) * 1.6f
                if (x == LEFT - 6) p.moveTo(x, y) else p.lineTo(x, y)
                x += 4f
            }
            drawPath(p, Color(0x2E96C8D2), style = Stroke(1f))
        }
    }

    private fun DrawScope.fog() {
        repeat(4) { i ->
            val x = ((t / (60f + i * 25f)) + i * 120f) % (W + 200f) - 100f
            val y = 175f + i * 14f
            drawOval(
                Brush.radialGradient(listOf(Color(0x1AA0AFBE), Color(0x00A0AFBE)), Offset(x, y), 90f),
                Offset(x - 90f, y - 40f), Size(180f, 80f),
            )
        }
    }

    /** Puente colgante; breakT (0..1) lo parte por el centro y cada mitad cuelga de su anclaje. */
    private fun DrawScope.bridge(breakT: Float, wobble: Float) {
        val swing = if (breakT >= 1f && falling) sin(t / 260f) * .06f * exp(-(t - fallT0) / 1800f) else 0f
        val ang = breakT * 1.25f + swing
        fun tf(x: Float, y: Float): Triple<Float, Float, Float> {
            if (breakT <= 0f) return Triple(x, y + wobble * sin((x - LEFT) / (RIGHT - LEFT) * PI.toFloat()), 0f)
            val (ax, dir) = if (x < MID) LEFT to 1f else RIGHT to -1f
            val a = ang * dir
            val dx = x - ax
            val dy = y - CLIFF_Y
            return Triple(ax + dx * cos(a) - dy * sin(a), CLIFF_Y + dx * sin(a) + dy * cos(a), a)
        }
        drawRect(Rustic.MaderaOscura, Offset(LEFT - 3, CLIFF_Y - 24), Size(4f, 26f))
        drawRect(Rustic.MaderaOscura, Offset(RIGHT - 1, CLIFF_Y - 24), Size(4f, 26f))
        val rope = Color(0xFF8A6A43)
        for (side in listOf(LEFT, RIGHT)) {
            val xs = buildList {
                var x = side
                while (if (side == LEFT) x <= MID else x >= MID) { add(x); x += if (side == LEFT) 8f else -8f }
            }
            val path = Path()
            xs.forEachIndexed { i, x ->
                if (i == 0) path.moveTo(if (side == LEFT) LEFT - 1 else RIGHT + 1, CLIFF_Y - 22)
                else tf(x, bridgeY(x) - 20).let { (px, py) -> path.lineTo(px, py) }
            }
            drawPath(path, rope, style = Stroke(1.2f))
            xs.forEach { x ->
                val (ax, ay) = tf(x, bridgeY(x) - 20)
                val (bx, by) = tf(x, bridgeY(x))
                drawLine(rope, Offset(ax, ay), Offset(bx, by), .6f)
            }
        }
        planks.forEach { p ->
            val (qx, qy, qa) = tf(p.x, bridgeY(p.x))
            withTransform({ translate(qx, qy); rotate(deg(atan(bridgeSlope(p.x)) + p.tilt + qa), Offset.Zero) }) {
                drawRect(Color((p.shade + 30) / 255f, p.shade / 255f, p.shade * .6f / 255f), Offset(-p.w / 2, -1f), Size(p.w, 3.2f))
            }
        }
    }

    private fun DrawScope.group(x: Float, y: Float, angle: Float, step: Float, riders: Boolean, party: Boolean, lantern: Float, sad: Boolean) {
        withTransform({ translate(x, y); rotate(deg(angle), Offset.Zero); scale(GS, GS, Offset.Zero) }) {
            cart(x / 9f, lantern)
            donkey(step, sad)
            if (riders) {
                passengers.forEachIndexed { i, p ->
                    val s = seatOf(i, passengers.size)
                    val hop = if (party) -abs(sin(t / 140f + i)) * 7f else 0f
                    emoji(p, s.x, s.y + hop, 13f)
                }
                emoji(driver, 26f, -36f + if (party) -abs(sin(t / 120f)) * 6f else 0f, 15f)
            }
        }
    }

    private fun DrawScope.cart(wheelRot: Float, lantern: Float) {
        val dark = Color(0xFF2B1A0C)
        drawCircle(dark, 9f, Offset(-28f, -9f), style = Stroke(2.4f))
        repeat(6) { i ->
            val a = wheelRot + i * PI.toFloat() / 3
            drawLine(dark, Offset(-28f, -9f), Offset(-28f + cos(a) * 9f, -9f + sin(a) * 9f), 1.2f)
        }
        drawRect(Rustic.Madera, Offset(-54f, -31f), Size(48f, 19f))
        var py = -25f
        while (py < -12f) { drawLine(Rustic.MaderaOscura, Offset(-54f, py), Offset(-6f, py), 1f); py += 6f }
        drawRect(Rustic.MaderaOscura, Offset(-54f, -31f), Size(48f, 19f), style = Stroke(1f))
        oval(Color(0xFFB08A3E), -42f, -35f, 11f, 7f)
        oval(Color(0xFFB08A3E), -20f, -35f, 12f, 7f)
        oval(Color(0xFF8C6A2C), -31f, -40f, 13f, 6f)
        drawLine(Color(0xFF5A3E17), Offset(-50f, -38f), Offset(-12f, -33f), .8f)
        drawLine(Color(0xFF5A3E17), Offset(-48f, -32f), Offset(-10f, -40f), .8f)
        drawLine(Color(0xFF4A2F17), Offset(-6f, -17f), Offset(14f, -19f), 2f)
        drawLine(dark, Offset(-6f, -31f), Offset(-2f, -38f), 1f)
        drawLine(dark, Offset(-2f, -38f), Offset(1f, -38f), 1f)
        val flicker = .75f + sin(t / 90f) * .1f + sin(t / 37f) * .05f
        val r = 22f * lantern
        drawCircle(Brush.radialGradient(listOf(Color(1f, .75f, .35f, .55f * flicker), Color(1f, .75f, .35f, 0f)), Offset(1f, -31f), r), r, Offset(1f, -31f))
        drawRect(Color(0xFFFFCF6E), Offset(-1.5f, -34f), Size(5f, 6f))
    }

    private fun DrawScope.donkey(step: Float, sad: Boolean) {
        val leg = sin(step) * 4f
        val legC = Color(0xFF4E4339)
        listOf(16f to leg, 20f to -leg, 33f to -leg, 37f to leg).forEach { (lx, d) ->
            drawLine(legC, Offset(lx, -17f), Offset(lx + d, 0f), 2.6f, StrokeCap.Round)
        }
        drawPath(Path().apply { moveTo(12f, -24f); quadraticTo(7f, -20f, 8f + sin(step * .7f) * 2f, -13f) }, legC, style = Stroke(1.4f, cap = StrokeCap.Round))
        val body = Color(0xFF7D6E60)
        oval(body, 26f, -22f, 15f, 8f)
        oval(Color(0xFF9A8B7C), 26f, -18f, 11f, 3.5f)
        drawLine(Rustic.MaderaOscura, Offset(14f, -19f), Offset(36f, -24f), 1.4f)
        drawPath(Path().apply { moveTo(35f, -27f); lineTo(44f, -37f); lineTo(48f, -33f); lineTo(40f, -20f); close() }, body)
        withTransform({ translate(49f, -34f); rotate(deg(if (sad) .5f else .25f), Offset.Zero) }) {
            oval(body, 0f, 0f, 8f, 4.5f)
            oval(Color(0xFFB5A797), 6f, .5f, 3f, 3f)
            drawCircle(Color(0xFF111111), 1f, Offset(-1f, -1.5f))
        }
        val ear = if (sad) .9f else 0f
        val earC = Color(0xFF6A5C4F)
        withTransform({ translate(45f, -37f); rotate(deg(-.35f + ear), Offset.Zero) }) { oval(earC, 0f, -6f, 1.8f, 6f) }
        withTransform({ translate(47f, -37f); rotate(deg(.05f + ear), Offset.Zero) }) { oval(earC, 0f, -6f, 1.8f, 6f) }
    }
}

/**
 * Canvas de la escena. Avanza la simulación en cada frame; [fastForward] la acelera x5.
 * Llama a [onStateChange] en cada cambio (Suspense, CrossedSuccessfully / FellIntoRiver) y a [onDone] al terminar.
 */
@Composable
fun BridgeScene(
    driver: String,
    passengers: List<String>,
    success: Boolean,
    fastForward: Boolean,
    onStateChange: (BridgeAnimState) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sim = remember(driver, passengers, success) { BridgeSimulation(driver, passengers.take(7), success) }
    sim.painters = (passengers + driver).distinct().mapNotNull { a -> AvatarAssets.drawables[a]?.let { a to painterResource(it) } }.toMap()
    var frame by remember { mutableIntStateOf(0) }
    val speed by rememberUpdatedState(if (fastForward) 5f else 1f)
    val stateCb by rememberUpdatedState(onStateChange)
    val doneCb by rememberUpdatedState(onDone)

    LaunchedEffect(sim) {
        var last = -1L
        while (!sim.done) {
            withFrameNanos { now ->
                val dt = if (last < 0) 0f else min(50f, (now - last) / 1_000_000f)
                last = now
                sim.step(dt * speed)?.let(stateCb)
                frame++
            }
        }
        doneCb()
    }

    Canvas(modifier.fillMaxWidth().aspectRatio(W / H).background(Color(0xFF0C0E13))) {
        frame // leer el estado fuerza el redibujado en cada frame
        with(sim) { draw() }
    }
}

// --------------------------------------------------------------------------- Mini escena del turno

/** Un jugador en la mini escena: id (como texto) y avatar. */
data class RigPlayer(val id: String, val avatar: String)

private data class RigSpot(val x: Float, val y: Float, val z: Float, val gone: Boolean)

/** Dónde va cada jugador según su estado ("conductor", "carro" o "bajado"). */
private fun rigLayout(players: List<RigPlayer>, status: Map<String, String>): Map<String, RigSpot> {
    val inCart = players.filter { status[it.id] == "carro" }
    return players.associate { p ->
        p.id to when (status[p.id]) {
            "conductor" -> RigSpot(RIG_GX + 26 * RIG_S, RIG_GY - 38 * RIG_S, 3f, false)
            "carro" -> {
                val s = seatOf(inCart.indexOf(p), inCart.size)
                RigSpot(RIG_GX + s.x * RIG_S, RIG_GY + s.y * RIG_S, 2f, false)
            }
            else -> RigSpot(40f, RIG_GY - 12, 1f, true)
        }
    }
}

/**
 * La burra y el carro con los jugadores: el conductor montado y el resto en el carro.
 * Cuando cambia [status], cada avatar que cambia de sitio salta hasta el nuevo (se sube a la burra,
 * baja del carro…). [cheerId] + [cheerKey] hacen botar a un jugador ("me quedo en el carro").
 */
@Composable
fun TurnRig(
    players: List<RigPlayer>,
    status: Map<String, String>,
    highlightId: String?,
    cheerId: String? = null,
    cheerKey: Any? = null,
    modifier: Modifier = Modifier,
) {
    val sim = remember { BridgeSimulation("", emptyList(), true) }
    val layout = rigLayout(players, status)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(RIG_W / RIG_H)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Rustic.MaderaClara.copy(alpha = .45f), RoundedCornerShape(14.dp)),
    ) {
        val w = maxWidth
        val h = maxHeight
        val avatarSize = w * 0.1f
        Canvas(Modifier.fillMaxSize()) { with(sim) { drawRig() } }
        players.forEach { p ->
            key(p.id) {
                val spot = layout.getValue(p.id)
                val x by animateFloatAsState(spot.x, tween(700), label = "x")
                val y by animateFloatAsState(spot.y, tween(700), label = "y")
                val alpha by animateFloatAsState(if (spot.gone) 0f else 1f, tween(400, delayMillis = if (spot.gone) 450 else 0), label = "alpha")
                val hop = remember { Animatable(1f) }
                var first by remember { mutableStateOf(true) }
                LaunchedEffect(spot.x, spot.y) {
                    if (first) { first = false; return@LaunchedEffect }
                    hop.snapTo(0f)
                    hop.animateTo(1f, tween(700))
                }
                val bounce = remember { Animatable(0f) }
                LaunchedEffect(cheerKey) {
                    if (cheerKey != null && cheerId == p.id) repeat(3) {
                        bounce.animateTo(1f, tween(150))
                        bounce.animateTo(0f, tween(150))
                    }
                }
                val lift = avatarSize * (0.9f * sin(hop.value * PI.toFloat()) + 0.3f * bounce.value)
                Box(
                    Modifier
                        .zIndex(spot.z)
                        .offset(x = w * (x / RIG_W) - avatarSize / 2, y = h * (y / RIG_H) - avatarSize / 2 - lift)
                        .alpha(alpha),
                ) {
                    Avatar(p.avatar, avatarSize, highlight = p.id == highlightId, background = false)
                }
            }
        }
    }
}

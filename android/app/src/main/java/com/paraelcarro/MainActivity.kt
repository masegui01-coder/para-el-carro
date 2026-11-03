package com.paraelcarro

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paraelcarro.ads.AdsManager
import com.paraelcarro.domain.model.GamePhase
import com.paraelcarro.ui.Feedback
import com.paraelcarro.ui.GameEvent
import com.paraelcarro.ui.GameViewModel
import com.paraelcarro.ui.components.AdBanner
import com.paraelcarro.ui.components.ConfirmDialog
import com.paraelcarro.ui.components.ScreenEnter
import com.paraelcarro.ui.components.SettingsBar
import com.paraelcarro.ui.components.SettingsDialog
import com.paraelcarro.ui.online.OnlineBridgeScreen
import com.paraelcarro.ui.online.OnlineEvent
import com.paraelcarro.ui.online.OnlineFormScreen
import com.paraelcarro.ui.online.OnlineGameScreen
import com.paraelcarro.ui.online.OnlineLobbyScreen
import com.paraelcarro.ui.online.OnlineScreen
import com.paraelcarro.ui.online.OnlineUi
import com.paraelcarro.ui.online.OnlineViewModel
import com.paraelcarro.ui.screens.BridgeCrossingScreen
import com.paraelcarro.ui.screens.FinalScreen
import com.paraelcarro.ui.screens.HandoffScreen
import com.paraelcarro.ui.screens.HomeScreen
import com.paraelcarro.ui.screens.LobbyScreen
import com.paraelcarro.ui.screens.OpeningBidScreen
import com.paraelcarro.ui.screens.RoundScoreScreen
import com.paraelcarro.ui.screens.RulesScreen
import com.paraelcarro.ui.screens.TurnScreen
import com.paraelcarro.ui.theme.ParaElCarroTheme
import com.paraelcarro.ui.theme.Rustic
import com.paraelcarro.ui.theme.ThemeState

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()
    private val online: OnlineViewModel by viewModels()
    private lateinit var ads: AdsManager
    private lateinit var feedback: Feedback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeState.systemLight = isSystemLight(resources.configuration)
        applySystemBars(light = true)
        ads = AdsManager(applicationContext).also { it.init(this) }
        feedback = Feedback(this)
        if (savedInstanceState == null) handleInvite(intent)
        setContent { ParaElCarroTheme { App() } }
        if (savedInstanceState == null && CrashGuard.consumeRestarted(this)) {
            Toast.makeText(this, "Ups, algo ha fallado y hemos vuelto a abrir el juego.", Toast.LENGTH_LONG).show()
        }
    }

    private fun isSystemLight(config: android.content.res.Configuration) =
        (config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) != android.content.res.Configuration.UI_MODE_NIGHT_YES

    /** Iconos de las barras del sistema que se vean sobre el fondo (oscuros en el tema claro). */
    private fun applySystemBars(light: Boolean) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (light) SystemBarStyle.light(transparent, transparent) else SystemBarStyle.dark(transparent)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleInvite(intent)
    }

    /** Enlace de invitación https://paraelcarroyomebajo.com/?sala=CODIGO (o los antiguos de Firebase y GitHub) */
    private fun handleInvite(intent: Intent?) {
        val code = intent?.data?.getQueryParameter("sala") ?: return
        online.openJoin(code)
    }

    override fun onDestroy() {
        feedback.release()
        super.onDestroy()
    }

    private fun shareInvite(ui: OnlineUi) {
        val text = "¡Súbete al carro! Únete a mi sala de \"Para el Carro\" con el código ${ui.code}: ${ui.inviteLink}"
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        startActivity(Intent.createChooser(send, "Invitar amigos"))
    }

    private fun copyInvite(ui: OnlineUi) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Para el Carro", ui.inviteLink))
        Toast.makeText(this, "Enlace copiado", Toast.LENGTH_SHORT).show()
    }

    @Composable
    private fun App() {
        val state by vm.state.collectAsStateWithLifecycle()
        val ou by online.ui.collectAsStateWithLifecycle()
        val adsReady by ads.ready.collectAsStateWithLifecycle()
        var confirmExit by rememberSaveable { mutableStateOf(false) }
        var confirmLeaveRoom by rememberSaveable { mutableStateOf(false) }
        val phase = state.phase
        var showSettings by rememberSaveable { mutableStateOf(false) }
        val light = ThemeState.light
        LaunchedEffect(light) { applySystemBars(light) }

        LaunchedEffect(Unit) {
            vm.events.collect {
                when (it) {
                    GameEvent.TICK -> feedback.tick()
                    GameEvent.BID -> feedback.bid()
                    GameEvent.RAISE -> feedback.raise()
                    GameEvent.STAY -> feedback.stay()
                    GameEvent.GET_OFF -> feedback.getOff()
                }
            }
        }
        LaunchedEffect(Unit) {
            online.events.collect {
                when (it) {
                    OnlineEvent.Tick -> feedback.tick()
                    OnlineEvent.Bid -> feedback.bid()
                    OnlineEvent.Raise -> feedback.raise()
                    OnlineEvent.Stay -> feedback.stay()
                    OnlineEvent.GetOff -> feedback.getOff()
                    OnlineEvent.YourTurn -> feedback.creak()
                    OnlineEvent.ShowAd -> ads.showInterstitial(this@MainActivity) {}
                    is OnlineEvent.Closed -> Toast.makeText(this@MainActivity, it.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        val inOnline = ou.screen != OnlineScreen.NONE
        val inRoom = ou.screen == OnlineScreen.ROOM
        val inGame = phase !is GamePhase.Home && phase !is GamePhase.Rules && phase !is GamePhase.Lobby && phase !is GamePhase.Final
        BackHandler(enabled = inOnline || phase !is GamePhase.Home) {
            when {
                inRoom -> confirmLeaveRoom = true
                inOnline -> online.close()
                inGame -> confirmExit = true
                else -> vm.goHome()
            }
        }

        // El banner solo aparece donde no hay temporizador ni animación.
        val onlinePhase = ou.state?.phase
        val showBanner = adsReady && if (inOnline) {
            !ou.inGame || onlinePhase == "score" || onlinePhase == "final"
        } else {
            phase is GamePhase.Home || phase is GamePhase.Rules || phase is GamePhase.Lobby ||
                phase is GamePhase.RoundScore || phase is GamePhase.Final
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(Rustic.Noche),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                SettingsBar(onOpen = { showSettings = true }, modifier = Modifier.align(Alignment.TopEnd).zIndex(5f))
                if (!inOnline && phase is GamePhase.Bridge) {
                    BridgeCrossingScreen(state, phase.result, feedback, vm::onBridgeFinished)
                } else {
                    // Las pantallas de puja usan dos columnas en tablet o apaisado ([AdaptivePanes]): más ancho
                    val wide = if (inOnline) ou.inGame else phase is GamePhase.Turn || phase is GamePhase.OpeningBid
                    Column(
                        Modifier
                            .widthIn(max = if (wide) 1040.dp else 560.dp)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .statusBarsPadding()
                            .then(if (showBanner) Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)) else Modifier.navigationBarsPadding())
                            .displayCutoutPadding()
                            .imePadding()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 60.dp),
                    ) {
                        // Cada pantalla nueva entra con un fundido corto (nada si el sistema pide quitar animaciones)
                        val screenKey = if (inOnline) "online-${ou.screen}-${ou.inGame}-${ou.state?.phase}" else phase.javaClass.simpleName
                        ScreenEnter(screenKey) {
                            if (inOnline) OnlineContent(ou) { confirmLeaveRoom = true } else OfflineContent(phase, state)
                        }
                    }
                }
            }
            if (showBanner) AdBanner()
        }

        if (showSettings) SettingsDialog(onTest = feedback::click, onDismiss = { showSettings = false })
        if (confirmExit) {
            ConfirmDialog(
                "¿Abandonar la partida?", "Se perderán los puntos de esta partida.", "Abandonar", "Seguir jugando",
                onConfirm = { confirmExit = false; vm.openLobby() }, onDismiss = { confirmExit = false },
            )
        }
        if (confirmLeaveRoom) {
            ConfirmDialog(
                if (ou.isHost) "¿Cerrar la sala?" else "¿Salir de la sala?",
                if (ou.isHost) "Saldrán todos los jugadores." else "Podrás volver a entrar con el código si la partida no ha empezado.",
                if (ou.isHost) "Cerrar sala" else "Salir", "Quedarme",
                onConfirm = { confirmLeaveRoom = false; online.exitRoom() }, onDismiss = { confirmLeaveRoom = false },
            )
        }
    }

    @Composable
    private fun OnlineContent(ou: OnlineUi, onExit: () -> Unit) {
        when {
            ou.screen != OnlineScreen.ROOM -> OnlineFormScreen(ou, online)
            ou.meta == null -> Text("Conectando con la sala…", color = Rustic.TextoSuave)
            !ou.inGame -> OnlineLobbyScreen(ou, online, onShare = { shareInvite(ou) }, onCopy = { copyInvite(ou) }, onExit = onExit)
            ou.state?.phase == "bridge" -> OnlineBridgeScreen(ou, ou.state, feedback)
            else -> OnlineGameScreen(ou, online, onExit)
        }
    }

    @Composable
    private fun OfflineContent(phase: GamePhase, state: com.paraelcarro.domain.model.GameState) {
        when (phase) {
            GamePhase.Home -> HomeScreen(
                onCreateRoom = online::openCreate,
                onJoinRoom = { online.openJoin(it) },
                onPlay = vm::openLobby,
                onRules = vm::showRules,
            )
            GamePhase.Rules -> RulesScreen(vm::goHome)
            GamePhase.Lobby -> LobbyScreen(state, vm)
            is GamePhase.Handoff -> HandoffScreen(state, phase, vm)
            is GamePhase.OpeningBid -> OpeningBidScreen(state, phase, vm)
            is GamePhase.Turn -> TurnScreen(state, phase, vm)
            is GamePhase.RoundScore -> RoundScoreScreen(
                state, phase.result,
                alreadyRated = vm.isRated(phase.result.question.id),
                onRate = { vm.rate(phase.result.question.id, it) },
            ) {
                if (vm.shouldShowAdBeforeNext()) ads.showInterstitial(this@MainActivity, vm::nextAfterScore)
                else vm.nextAfterScore()
            }
            GamePhase.Final -> FinalScreen(state, vm::rematch, vm::openLobby)
            is GamePhase.Bridge -> Unit
        }
    }
}

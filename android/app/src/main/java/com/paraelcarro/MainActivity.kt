package com.paraelcarro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paraelcarro.ads.AdsManager
import com.paraelcarro.domain.model.GamePhase
import com.paraelcarro.ui.Feedback
import com.paraelcarro.ui.GameEvent
import com.paraelcarro.ui.GameViewModel
import com.paraelcarro.ui.components.AdBanner
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

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()
    private lateinit var ads: AdsManager
    private lateinit var feedback: Feedback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Iconos claros en las barras del sistema: el fondo de la app siempre es de noche
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        ads = AdsManager(applicationContext).also { it.init(this) }
        feedback = Feedback(this)
        setContent { ParaElCarroTheme { App() } }
    }

    override fun onDestroy() {
        feedback.release()
        super.onDestroy()
    }

    @Composable
    private fun App() {
        val state by vm.state.collectAsStateWithLifecycle()
        val adsReady by ads.ready.collectAsStateWithLifecycle()
        var confirmExit by remember { mutableStateOf(false) }
        val phase = state.phase

        LaunchedEffect(Unit) {
            vm.events.collect {
                when (it) {
                    GameEvent.TICK -> feedback.tick()
                    GameEvent.BID -> feedback.bid()
                    GameEvent.RAISE -> feedback.raise()
                }
            }
        }

        val inGame = phase !is GamePhase.Home && phase !is GamePhase.Rules && phase !is GamePhase.Lobby && phase !is GamePhase.Final
        BackHandler(enabled = phase !is GamePhase.Home) {
            if (inGame) confirmExit = true else vm.goHome()
        }

        // El banner solo aparece donde no hay temporizador ni animación.
        val showBanner = adsReady && (phase is GamePhase.Home || phase is GamePhase.Rules || phase is GamePhase.Lobby ||
            phase is GamePhase.RoundScore || phase is GamePhase.Final)

        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(Color(0xFF2B3342), Rustic.Noche), radius = 1600f)),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                if (phase is GamePhase.Bridge) {
                    BridgeCrossingScreen(state, phase.result, feedback, vm::onBridgeFinished)
                } else {
                    Column(
                        Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .statusBarsPadding()
                            .imePadding()
                            .padding(16.dp),
                    ) {
                        when (phase) {
                            GamePhase.Home -> HomeScreen(vm::openLobby, vm::showRules)
                            GamePhase.Rules -> RulesScreen(vm::goHome)
                            GamePhase.Lobby -> LobbyScreen(state, vm)
                            is GamePhase.Handoff -> HandoffScreen(state, phase, vm)
                            is GamePhase.OpeningBid -> OpeningBidScreen(state, phase, vm)
                            is GamePhase.Turn -> TurnScreen(state, phase, vm)
                            is GamePhase.RoundScore -> RoundScoreScreen(state, phase.result) {
                                if (vm.shouldShowAdBeforeNext()) ads.showInterstitial(this@MainActivity, vm::nextAfterScore)
                                else vm.nextAfterScore()
                            }
                            GamePhase.Final -> FinalScreen(state, vm::rematch, vm::openLobby)
                            is GamePhase.Bridge -> Unit
                        }
                    }
                }
            }
            if (showBanner) AdBanner()
        }

        if (confirmExit) {
            AlertDialog(
                onDismissRequest = { confirmExit = false },
                title = { Text("¿Abandonar la partida?") },
                text = { Text("Se perderán los puntos de esta partida.") },
                confirmButton = { TextButton({ confirmExit = false; vm.openLobby() }) { Text("Abandonar") } },
                dismissButton = { TextButton({ confirmExit = false }) { Text("Seguir jugando") } },
            )
        }
    }
}

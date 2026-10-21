package com.paraelcarro.domain.model

/**
 * Avatares ilustrados (los SVG de shared/avatares, convertidos con tools/build-avatares.js).
 * Los jugadores guardados con un emoji de versiones anteriores lo siguen viendo como texto.
 */
val AVATARS = listOf(
    "av:campesino", "av:campesina", "av:abuela", "av:abuelo", "av:raton", "av:gato", "av:loro", "av:perro",
    "av:cerdo", "av:gallina", "av:oveja", "av:zorro", "av:rana", "av:pirata", "av:bruja", "av:bandolero",
)

const val MIN_PLAYERS = 3
const val MAX_PLAYERS = 8
const val OPEN_SECONDS = 50
const val TURN_SECONDS = 15

data class Player(
    val id: Int,
    val name: String,
    val avatar: String,
    val score: Int = 0,
)

enum class QuestionType { FECHA, NUMERO }

/** Modo elegido por el jefe de sala. */
enum class QuestionMode { FECHAS, NUMEROS, MEZCLA }

/** Una pregunta cuya respuesta es un año (negativo = a. C.) o un número. */
data class Question(
    val id: String,
    val type: QuestionType,
    val text: String,
    val answer: Double,
    val unit: String = "",
    val note: String = "",
)

/** También sirve como "categoría" de la ronda: Fechas o Números. */
val Question.category: String get() = if (type == QuestionType.FECHA) "📅 Fecha" else "🔢 Número"

enum class PlayerStatus { CONDUCTOR, EN_CARRO, BAJADO }

sealed interface TurnAction {
    data class Raise(val value: Double) : TurnAction
    data object Stay : TurnAction
    data object GetOff : TurnAction
}

enum class LogKind { ABRE, SUBE, QUEDA, BAJA, TIEMPO }

data class LogEntry(val playerId: Int, val kind: LogKind, val value: Double? = null)

/** Estado de una ronda en curso. */
data class RoundState(
    val question: Question,
    val openerIndex: Int,
    val bid: Double? = null,
    val driverId: Int? = null,
    val status: Map<Int, PlayerStatus> = emptyMap(),
    /** Jugadores que aún tienen que decidir, en orden. Vacía tras abrir = ronda resuelta. */
    val queue: List<Int> = emptyList(),
    val log: List<LogEntry> = emptyList(),
)

data class RoundResult(
    val question: Question,
    val bid: Double,
    val driverId: Int,
    val success: Boolean,
    val bullseye: Boolean,
    val passengers: List<Int>,
    val gotOff: List<Int>,
    val delta: Map<Int, Int>,
)

data class GameConfig(
    val rounds: Int = 6,
    val mode: QuestionMode = QuestionMode.MEZCLA,
)

/** Máquina de estados: Lobby -> Puja (apertura + turnos) -> Animación/Resolución -> Puntuaciones -> … -> Final. */
sealed interface GamePhase {
    data object Home : GamePhase
    data object Rules : GamePhase
    data object Lobby : GamePhase
    /** "Pasa el móvil a…": el temporizador empieza cuando el jugador pulsa "¡Soy yo!". */
    data class Handoff(val playerId: Int, val opening: Boolean, val message: String) : GamePhase
    data class OpeningBid(val playerId: Int) : GamePhase
    data class Turn(val playerId: Int) : GamePhase
    data class Bridge(val result: RoundResult) : GamePhase
    data class RoundScore(val result: RoundResult) : GamePhase
    data object Final : GamePhase
}

/** Lo que se está escribiendo en el campo de cifra (lo lee el ViewModel si se acaba el tiempo). */
data class Draft(
    val text: String = "",
    val beforeChrist: Boolean = false,
    val raiseOpen: Boolean = false,
)

data class GameState(
    val phase: GamePhase = GamePhase.Home,
    val players: List<Player> = emptyList(),
    val config: GameConfig = GameConfig(),
    val roundNumber: Int = 0,
    val round: RoundState? = null,
    val secondsLeft: Int = 0,
    val timerFraction: Float = 1f,
    val draft: Draft = Draft(),
    val openAttempts: Int = 0,
    /** true mientras el muñeco salta en la mini escena tras pulsar un botón (no se aceptan más jugadas). */
    val animating: Boolean = false,
    /** Jugador que bota de alegría en el carro ("me quedo"). */
    val cheerId: Int? = null,
) {
    fun player(id: Int?): Player? = players.firstOrNull { it.id == id }
    val isLastRound: Boolean get() = roundNumber >= config.rounds
}

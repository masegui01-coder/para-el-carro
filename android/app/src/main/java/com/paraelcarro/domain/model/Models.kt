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

/**
 * Un disparate de la ronda: quién, qué cifra y si se pasó ([high]) o se quedó muy corto.
 * [from] es el conductor de la cifra si el jugador no la dijo pero se quedó en el carro con ella.
 */
data class Barbaridad(val playerId: Int, val value: Double, val high: Boolean, val from: Int? = null) {
    companion object {
        const val ALTO = "alto"
        const val BAJO = "bajo"
    }
}

/**
 * Las grabaciones de la abuela y lo que dicen, palabra a palabra con el segundo en que suena cada una
 * (medido sobre el audio). Mismos datos que VOCES_ABUELA en js/game.js.
 */
enum class VozAbuela(val ms: Long, val tramos: List<Pair<Double, String>>) {
    CORTO(2450, listOf(0.6 to "¿No te", 0.9 to "habrás", 1.1 to "quedado", 1.35 to "corto,", 1.7 to "no?")),
    SANTO(2800, listOf(0.4 to "¡Esto no lo", 0.9 to "arregla", 1.3 to "ni el", 1.55 to "Espíritu", 2.0 to "Santo!")),
    TRILLONES(11000, listOf(1.0 to "¿Cuatrocientos", 1.8 to "trillones?", 7.4 to "¡¡Cuatrocientos", 8.1 to "trillones!!"));

    val texto: String get() = tramos.joinToString(" ") { it.second }

    /** Lo que dura la escena: la grabación y un respiro (mínimo 4,5 s para leer los culpables). */
    val escenaMs: Long get() = maxOf(4500L, ms + 1500L)

    companion object {
        /**
         * Si alguien se ha pasado, "¿No te habrás quedado corto?" o la de los cuatrocientos trillones,
         * alternando según la ronda y la pregunta (todos los móviles de la sala oyen la misma; igual que
         * vozBarbaridad en js/game.js); si no, la del Espíritu Santo.
         */
        fun para(questionId: String, round: Int, anyHigh: Boolean): VozAbuela {
            if (!anyHigh) return SANTO
            val numero = questionId.filter { it.isDigit() }.toIntOrNull() ?: 0
            return if ((round + numero) % 2 == 0) TRILLONES else CORTO
        }
    }
}

data class RoundResult(
    val question: Question,
    val bid: Double,
    val driverId: Int,
    val success: Boolean,
    val bullseye: Boolean,
    val passengers: List<Int>,
    val gotOff: List<Int>,
    val delta: Map<Int, Int>,
    val barbaridades: List<Barbaridad> = emptyList(),
)

data class GameConfig(
    val rounds: Int = 6,
    val mode: QuestionMode = QuestionMode.MEZCLA,
    /** Modo barbaridades: −1 a quien diga un disparate. */
    val barbaridades: Boolean = true,
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

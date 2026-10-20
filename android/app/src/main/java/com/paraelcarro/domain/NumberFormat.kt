package com.paraelcarro.domain

import com.paraelcarro.domain.model.Question
import com.paraelcarro.domain.model.QuestionType
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val spanish = DecimalFormatSymbols(Locale.forLanguageTag("es-ES")).apply {
    groupingSeparator = '.'
    decimalSeparator = ','
}

/** 7400000000000 -> "7.400.000.000.000"; los años sin separador y los negativos como "a. C.". */
fun formatCifra(value: Double, type: QuestionType): String =
    if (type == QuestionType.FECHA) {
        val y = Math.round(value)
        if (y < 0) "${-y} a. C." else y.toString()
    } else {
        DecimalFormat("#,##0.##", spanish).format(value)
    }

fun Question.format(value: Double): String =
    formatCifra(value, type) + if (type == QuestionType.NUMERO && unit.isNotEmpty()) " $unit" else ""

/** "7.400.000" -> 7400000.0 ; "13,7" -> 13.7 (formato español). Null si no es una cifra válida. */
fun parseCifra(text: String, type: QuestionType, beforeChrist: Boolean): Double? {
    val s = text.trim().replace(" ", "").replace(".", "").replace(',', '.')
    if (!Regex("""^\d+(\.\d+)?$""").matches(s)) return null
    val v = s.toDoubleOrNull() ?: return null
    return if (type == QuestionType.FECHA) Math.round(v).toDouble().let { if (beforeChrist) -it else it } else v
}

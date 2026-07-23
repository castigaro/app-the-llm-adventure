package com.appsonar.thellmadventure.data

/**
 * Toleranter Enum-Parser (Muster aus Kap-Krakenzahn): unbekannte Namen —
 * etwa aus älteren/neueren Spielständen oder ungültigen KI-Antworten —
 * ergeben null statt einer Exception.
 */
inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    name?.let { n -> runCatching { enumValueOf<T>(n.trim().uppercase()) }.getOrNull() }

package app.brokoli5191.zeitig.data.ogd

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Lenient JSON for OGD responses: unknown fields are ignored, nulls fall back to defaults. */
internal val OgdJson: Json =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

/** Numbers sometimes arrive as strings (`"rbl": "1450"`) and vice versa. */
internal fun JsonElement?.asIntOrNull(): Int? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.intOrNull ?: primitive.content.trim().toIntOrNull()
}

internal fun JsonElement?.asStringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.content.takeUnless { it.isBlank() || it == "null" }
}

package app.brokoli5191.zeitig.data.map

import app.brokoli5191.zeitig.data.net.await
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * basemap.at's vector style uses relative URLs for sprites and glyphs and points its source at an
 * Esri VectorTileServer root instead of TileJSON. MapLibre Native resolves neither, so the style is
 * rewritten once: absolute sprite/glyph URLs and an explicit tile template.
 */
public object BasemapStyle {
    public const val STYLE_URL: String = "https://mapsneu.wien.gv.at/basemapv/bmapv/3857/resources/styles/root.json"
    public const val TILE_TEMPLATE: String = "https://mapsneu.wien.gv.at/basemapv/bmapv/3857/tile/{z}/{y}/{x}.pbf"
    public const val ATTRIBUTION: String = "© basemap.at"

    private val json = Json { ignoreUnknownKeys = true }

    /** Returns the rewritten style, or throws [IllegalArgumentException] if it is not a style. */
    public fun rewrite(
        style: String,
        styleUrl: String = STYLE_URL,
    ): String {
        val root =
            try {
                json.parseToJsonElement(style).jsonObject
            } catch (e: SerializationException) {
                throw IllegalArgumentException("not a JSON style", e)
            }
        require(root["layers"] is JsonArray) { "style without layers" }
        val base = URI(styleUrl)
        val rewritten = root.toMutableMap()
        (root["sprite"] as? JsonPrimitive)?.content?.let { rewritten["sprite"] = JsonPrimitive(base.resolve(it).toString()) }
        (root["glyphs"] as? JsonPrimitive)?.content?.let { rewritten["glyphs"] = JsonPrimitive(resolveTemplate(base, it)) }
        (root["sources"] as? JsonObject)?.let { sources ->
            rewritten["sources"] =
                JsonObject(
                    sources.mapValues { (_, source) ->
                        val obj = source as? JsonObject ?: return@mapValues source
                        if (obj["type"] != JsonPrimitive("vector")) return@mapValues obj
                        val map = obj.toMutableMap()
                        map.remove("url")
                        map["tiles"] = JsonArray(listOf(JsonPrimitive(TILE_TEMPLATE)))
                        map["attribution"] = JsonPrimitive(ATTRIBUTION)
                        JsonObject(map)
                    },
                )
        }
        return JsonObject(rewritten).toString()
    }

    /** URI.resolve chokes on `{fontstack}`; resolve the static part and keep the placeholders. */
    private fun resolveTemplate(
        base: URI,
        template: String,
    ): String {
        val marker = template.indexOf('{')
        if (marker < 0) return base.resolve(template).toString()
        val prefix = template.substring(0, marker)
        val resolved = base.resolve(prefix).toString()
        return resolved + template.substring(marker)
    }
}

/** Fetches and caches the rewritten basemap style (refreshed after [maxAge]). */
public class MapStyleRepository(
    private val client: OkHttpClient,
    private val cacheDirectory: File,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
    private val styleUrl: String = BasemapStyle.STYLE_URL,
    private val maxAge: Duration = Duration.ofDays(30),
) {
    private val cacheFile get() = File(cacheDirectory, "basemap-style.json")

    public suspend fun style(): String? =
        withContext(ioDispatcher) {
            val cached = cacheFile.takeIf { it.isFile }
            val fresh = cached != null && Duration.between(Instant.ofEpochMilli(cached.lastModified()), clock.instant()) < maxAge
            if (fresh) return@withContext cached.readText()
            val downloaded =
                try {
                    client.newCall(Request.Builder().url(styleUrl).build()).await().use { response ->
                        if (response.isSuccessful) BasemapStyle.rewrite(response.body.string(), styleUrl) else null
                    }
                } catch (_: IOException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            if (downloaded != null) {
                cacheDirectory.mkdirs()
                cacheFile.writeText(downloaded)
                downloaded
            } else {
                cached?.readText()
            }
        }
}

package app.brokoli5191.zeitig.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

@Serializable
data class RouteKey(
    val id: Long,
) : NavKey

@Serializable
data class EditorKey(
    val id: Long = 0,
) : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object AppearanceKey : NavKey

@Serializable
data object AboutKey : NavKey

@Serializable
data object LicencesKey : NavKey

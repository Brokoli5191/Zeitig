@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package app.brokoli5191.zeitig.ui.expressive

// Experimental adaptive navigation APIs, kept next to the other experimental wrappers.

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.navigation3.scene.SceneStrategy

/** Single pane on phones, list and detail side by side on tablets and unfolded foldables. */
@Composable
fun <T : Any> rememberListDetailStrategy(): SceneStrategy<T> = rememberListDetailSceneStrategy()

fun listPaneMetadata(): Map<String, Any> = ListDetailSceneStrategy.listPane()

fun detailPaneMetadata(): Map<String, Any> = ListDetailSceneStrategy.detailPane()

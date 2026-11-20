package app.brokoli5191.zeitig

import android.Manifest
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.brokoli5191.zeitig.data.reference.RefreshResult
import app.brokoli5191.zeitig.locale.AppLanguages
import app.brokoli5191.zeitig.ui.navigation.ZeitigNavHost
import app.brokoli5191.zeitig.ui.theme.LocalDarkTheme
import app.brokoli5191.zeitig.ui.theme.ZeitigTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var pendingLocationResult: ((Boolean) -> Unit)? = null

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            pendingLocationResult?.invoke(result.values.any { it })
            pendingLocationResult = null
        }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguages.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as ZeitigApplication).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val appearance = settings?.appearance ?: return@setContent
            ZeitigTheme(appearance) {
                val dark = LocalDarkTheme.current
                DisposableEffect(dark) {
                    // System bar icons follow the app's theme, not only the system's.
                    val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose {}
                }
                ZeitigNavHost(container, requestLocationPermission = ::requestLocation)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // At most weekly and only on unmetered networks: refresh the bundled stop data.
        val container = (application as ZeitigApplication).container
        val connectivity = getSystemService(android.net.ConnectivityManager::class.java)
        if (connectivity?.isActiveNetworkMetered == false) {
            container.applicationScope.launch {
                if (container.referenceRefresher.refreshIfDue() is RefreshResult.Updated) container.referenceData.invalidate()
            }
        }
    }

    private fun requestLocation(onResult: (Boolean) -> Unit) {
        pendingLocationResult = onResult
        locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
}

package app.brokoli5191.zeitig

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.brokoli5191.zeitig.ui.home.HomeScreen
import app.brokoli5191.zeitig.ui.theme.ZeitigTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ZeitigTheme {
                HomeScreen()
            }
        }
    }
}

package app.brokoli5191.zeitig

import android.app.Application

class ZeitigApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

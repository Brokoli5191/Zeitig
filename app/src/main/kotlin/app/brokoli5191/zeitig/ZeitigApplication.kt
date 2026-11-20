package app.brokoli5191.zeitig

import android.app.Application
import app.brokoli5191.zeitig.alarms.WindowScheduler
import app.brokoli5191.zeitig.notify.Channels
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class ZeitigApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Channels.create(this)
        // Every change to the routes – saved, deleted, schedule or reminders toggled – re-arms the
        // window alarms, so no screen has to remember to do it.
        container.applicationScope.launch {
            val scheduler = WindowScheduler(this@ZeitigApplication)
            container.routes.observeRoutes().distinctUntilChanged().collect { routes ->
                scheduler.rescheduleAll(routes, container.clock.instant())
            }
        }
    }
}

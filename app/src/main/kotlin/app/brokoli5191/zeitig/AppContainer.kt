package app.brokoli5191.zeitig

import android.content.Context

/**
 * Manual dependency graph. Everything long-lived is created here exactly once; screens,
 * services and widgets receive what they need from this container instead of a DI framework.
 */
class AppContainer(
    context: Context,
) {
    val appContext: Context = context.applicationContext
}

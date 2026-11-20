package app.brokoli5191.zeitig.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

internal const val PACKAGE = "app.brokoli5191.zeitig"
private const val TIMEOUT_MS = 5_000L

/**
 * The paths every user takes right after installing: start, look at the empty home screen, open
 * the route editor and the settings. Selectors use the English UI (the emulator's default locale).
 */
internal fun MacrobenchmarkScope.firstRun() {
    pressHome()
    startActivityAndWait()
    device.wait(Until.hasObject(By.text("Zeitig")), TIMEOUT_MS)

    openAndReturn(By.text("New route"))
    openAndReturn(By.desc("Settings")) {
        openAndReturn(By.text("Appearance"))
        device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
        openAndReturn(By.text("About"))
    }
}

private fun MacrobenchmarkScope.openAndReturn(
    selector: BySelector,
    inside: MacrobenchmarkScope.() -> Unit = {},
) {
    val target = device.wait(Until.findObject(selector), TIMEOUT_MS) ?: return
    target.click()
    device.waitForIdle()
    inside()
    device.pressBack()
    device.waitForIdle()
}

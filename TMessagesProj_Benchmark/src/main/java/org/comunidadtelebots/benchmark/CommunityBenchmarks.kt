package org.comunidadtelebots.benchmark

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun target() = requireNotNull(InstrumentationRegistry.getArguments().getString("targetPackage"))
private fun MacrobenchmarkScope.settingsJourney() {
    startActivityAndWait(Intent(Intent.ACTION_VIEW, Uri.parse("tg://settings")).setPackage(target()))
    val entry = device.wait(Until.findObject(By.textContains("Comunidad Telebots")), 10_000)
    assertNotNull("Unlock Moonbot and sign in to the test account before profiling", entry)
    entry.click()
    device.waitForIdle()
    val scroll = device.findObject(By.scrollable(true))
    assertNotNull("Community settings did not open", scroll)
    scroll.setGestureMargin(device.displayWidth / 5)
    scroll.fling(Direction.DOWN)
    device.waitForIdle()
    scroll.fling(Direction.UP)
    device.waitForIdle()
}

@RunWith(AndroidJUnit4::class)
class CommunityBaselineGenerator {
    @get:Rule val rule = BaselineProfileRule()
    @Test fun startup() = rule.collect(packageName = target(), includeInStartupProfile = true) {
        pressHome(); startActivityAndWait()
    }
    @Test fun settingsScroll() = rule.collect(packageName = target(), includeInStartupProfile = false) {
        pressHome(); settingsJourney()
    }
}

@RunWith(AndroidJUnit4::class)
class CommunityBenchmarks {
    @get:Rule val rule = MacrobenchmarkRule()
    private fun run(mode: CompilationMode) = rule.measureRepeated(
        packageName = target(),
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() }
    ) { settingsJourney() }
    @Test fun withoutProfile() = run(CompilationMode.None())
    @Test fun withProfile() = run(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))
}

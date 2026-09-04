package io.legado.app.data.repository

import io.legado.app.constant.PreferKey
import io.legado.app.domain.model.settings.AppShellSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class AppShellSettingsMappingTest {

    @Test
    fun `AppShell 键写读映射逐字段对应`() {
        appShellMappingSamples().forEach { expected ->
            assertEquals(expected.expectedPrefMap(), expected.toPrefMap())
            assertEquals(
                expected,
                expected.expectedPrefMap().toTestPreferences().toAppShellSettings(),
            )
        }
    }

    @Test
    fun `默认首页通过真实原子路径单批写入`() {
        val values = captureAtomicUpdateValues(
            current = AppShellSettings(defaultHomePage = "bookshelf"),
            read = { it.toAppShellSettings() },
            toPrefMap = AppShellSettings::toPrefMap,
            transform = {
                it.copy(
                    defaultHomePage = "my",
                )
            },
        )

        assertEquals(
            mapOf(
                PreferKey.defaultHomePage to "my",
            ),
            values,
        )
    }
}

private fun appShellMappingSamples(): List<AppShellSettings> {
    val base = AppShellSettings(
        themeMode = "theme-mode",
        fontScale = 13,
        composeEngine = "compose-engine",
        tabletInterface = "tablet-interface",
        labelVisibilityMode = "label-mode",
        defaultHomePage = "default-page",
        mainNavigationOrder = "navigation-order",
        navIconBookshelf = "icon-bookshelf",
        navIconMy = "icon-my",
        navIconBookshelfSelected = "icon-bookshelf-selected",
        navIconMySelected = "icon-my-selected",
        launcherIcon = "launcher-icon",
    )
    return listOf(
        base,
        base.copy(showStatusBar = false),
        base.copy(swipeAnimation = false),
        base.copy(predictiveBackEnabled = false),
        base.copy(showBottomView = false),
        base.copy(useFloatingBottomBar = true),
        base.copy(useFloatingBottomBarLiquidGlass = true),
        base.copy(navExtended = true),
    )
}

private fun AppShellSettings.expectedPrefMap(): Map<String, Any?> = mapOf(
    PreferKey.themeMode to themeMode,
    PreferKey.fontScale to fontScale,
    PreferKey.composeEngine to composeEngine,
    PreferKey.showStatusBar to showStatusBar,
    PreferKey.swipeAnimation to swipeAnimation,
    PreferKey.isPredictiveBackEnabled to predictiveBackEnabled,
    PreferKey.showBottomView to showBottomView,
    PreferKey.useFloatingBottomBar to useFloatingBottomBar,
    PreferKey.useFloatingBottomBarLiquidGlass to useFloatingBottomBarLiquidGlass,
    PreferKey.tabletInterface to tabletInterface,
    PreferKey.labelVisibilityMode to labelVisibilityMode,
    PreferKey.defaultHomePage to defaultHomePage,
    PreferKey.mainNavigationOrder to mainNavigationOrder,
    PreferKey.navExtended to navExtended,
    PreferKey.navIconBookshelf to navIconBookshelf,
    PreferKey.navIconMy to navIconMy,
    PreferKey.navIconBookshelfSelected to navIconBookshelfSelected,
    PreferKey.navIconMySelected to navIconMySelected,
    PreferKey.launcherIcon to launcherIcon,
)

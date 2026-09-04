package io.legado.app.domain.model.settings

data class AppShellSettings(
    val themeMode: String = "0",
    val fontScale: Int = 10,
    val composeEngine: String = "material",
    val showStatusBar: Boolean = true,
    val swipeAnimation: Boolean = true,
    val predictiveBackEnabled: Boolean = true,
    val showBottomView: Boolean = true,
    val useFloatingBottomBar: Boolean = false,
    val useFloatingBottomBarLiquidGlass: Boolean = false,
    val tabletInterface: String = "auto",
    val labelVisibilityMode: String = "auto",
    val defaultHomePage: String = "bookshelf",
    val mainNavigationOrder: String = "bookshelf,my",
    val navExtended: Boolean = false,
    val navIconBookshelf: String = "",
    val navIconMy: String = "",
    val navIconBookshelfSelected: String = "",
    val navIconMySelected: String = "",
    val launcherIcon: String = "ic_launcher",
)

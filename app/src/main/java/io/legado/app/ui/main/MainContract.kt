package io.legado.app.ui.main

import androidx.compose.runtime.Stable
import io.legado.app.ui.main.my.PrefClickEvent
import kotlinx.collections.immutable.ImmutableList

@Stable
data class MainUiState(
    val destinations: ImmutableList<MainDestination> = MainDestination.mainDestinations,
    val defaultHomePage: String = "bookshelf",
    val showBottomView: Boolean = true,
    val useFloatingBottomBar: Boolean = false,
    val useFloatingBottomBarLiquidGlass: Boolean = false,
    val labelVisibilityMode: String = "auto",
    val navExtended: Boolean = false,
    val navIconBookshelf: String = "",
    val navIconExplore: String = "",
    val navIconMy: String = "",
    val navIconBookshelfSelected: String = "",
    val navIconExploreSelected: String = "",
    val navIconMySelected: String = "",
    val deepPersonalizationActive: Boolean = false,
    val secondaryThemeColor: Int = 0,
    val secondaryThemeColorNight: Int = 0,
) {
    fun customIconPath(destination: MainDestination): String = when (destination) {
        MainDestination.Bookshelf -> navIconBookshelf
        MainDestination.Explore -> navIconExplore
        MainDestination.My -> navIconMy
    }

    fun selectedCustomIconPath(destination: MainDestination): String = when (destination) {
        MainDestination.Bookshelf -> navIconBookshelfSelected
        MainDestination.Explore -> navIconExploreSelected
        MainDestination.My -> navIconMySelected
    }
}

sealed interface MainUiIntent {
    data class SetNavigationRailExpanded(val expanded: Boolean) : MainUiIntent
    data class HandlePreferenceClick(val event: PrefClickEvent) : MainUiIntent
}

sealed interface MainEffect {
    data class StartActivity(val destination: Class<*>, val configTag: String? = null) : MainEffect
    data object ExitApp : MainEffect
    data object NavigateToReadRecord : MainEffect
    data object NavigateToHighlightTagRule : MainEffect
    data object NavigateToAbout : MainEffect
}

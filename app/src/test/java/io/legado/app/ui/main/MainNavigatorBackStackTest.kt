package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class MainNavigatorBackStackTest {

    @Test
    fun `opening book info from bookshelf manage keeps manage in back stack`() {
        val manage = MainRouteCache(-1L)
        val bookInfo = MainRouteBookInfo("Book", "Author", "book-url")
        val backStack = mutableListOf<NavKey>(MainRouteHome, manage)

        MainNavigator.navigateToRoute(backStack, bookInfo)

        assertEquals(listOf(MainRouteHome, manage, bookInfo), backStack)
    }

    @Test
    fun `opening search from book source manage keeps manage in back stack`() {
        val manage = MainRouteBookSourceManage()
        val search = MainRouteSearch(key = null, scopeRaw = "scope")
        val backStack = mutableListOf<NavKey>(MainRouteHome, manage)

        MainNavigator.navigateToRoute(backStack, search)

        assertEquals(listOf(MainRouteHome, manage, search), backStack)
    }

    @Test
    fun `media reader replaces stale route with home parent`() {
        val bookInfo = MainRouteBookInfo("Book", "Author", "book-url")
        val reader = MainRouteReadBook(bookUrl = "book-url")
        val backStack = mutableListOf<NavKey>(MainRouteHome, bookInfo)

        MainNavigator.navigateToRoute(backStack, reader, resetToHome = true)

        assertEquals(listOf(MainRouteHome, reader), backStack)
    }

    @Test
    fun `regular reader keeps book info as parent`() {
        val bookInfo = MainRouteBookInfo("Book", "Author", "book-url")
        val reader = MainRouteReadBook(bookUrl = "book-url", chapterChanged = true)
        val backStack = mutableListOf<NavKey>(MainRouteHome, bookInfo)

        MainNavigator.navigateToRoute(backStack, reader)

        assertEquals(listOf(MainRouteHome, bookInfo, reader), backStack)
    }
}

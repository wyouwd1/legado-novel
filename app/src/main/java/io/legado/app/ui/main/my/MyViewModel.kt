package io.legado.app.ui.main.my

sealed class PrefClickEvent {
    data class StartActivity(val destination: Class<*>, val configTag: String? = null) : PrefClickEvent()
    object OpenReadRecord : PrefClickEvent()
    object OpenBookCacheManage : PrefClickEvent()
    object OpenBookSourceManage : PrefClickEvent()
    object OpenReplaceRules : PrefClickEvent()
    object OpenHighlightTagRule : PrefClickEvent()
    object OpenAbout : PrefClickEvent()
    object ExitApp : PrefClickEvent()
}

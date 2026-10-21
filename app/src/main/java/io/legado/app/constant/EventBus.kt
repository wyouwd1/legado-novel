package io.legado.app.constant

object EventBus {
    const val UP_BOOKSHELF = "upBookToc"
    const val BOOKSHELF_REFRESH = "bookshelfRefresh"
    const val BATTERY_CHANGED = "batteryChanged"
    const val TIME_CHANGED = "timeChanged"
    const val NOTIFY_MAIN = "notifyMain"
    const val UP_DOWNLOAD = "upDownload"
    const val UP_DOWNLOAD_STATE = "upDownloadState"
    const val SAVE_CONTENT = "saveContent"
    const val CHECK_SOURCE = "checkSource"
    const val CHECK_SOURCE_DONE = "checkSourceDone"
    const val TIP_COLOR = "tipColor"
    const val SOURCE_CHANGED = "sourceChanged"
    const val SEARCH_RESULT = "searchResult"
    const val UPDATE_READ_ACTION_BAR = "updateReadActionBar"
    const val UP_SEEK_BAR = "upSeekBar"
    const val READ_ALOUD_PLAY = "readAloudPlay"
    const val EXPORT_BOOK = "exportBook"
    const val PLAY_MODE_CHANGED = "playModeChanged"
    const val REFRESH_BOOK_INFO = "refreshBookInfo"
    const val REFRESH_BOOK_TOC = "refreshBookToc"
    const val REFRESH_BOOK_CONTENT = "refreshBookContent"
    const val UP_TOC = "upToc"

    const val UP_ALL_BOOK_TOC = "upAllBookToc"

    /** 局域网请求被本地网络保护拦下，宿主应申请本地网络权限。 */
    const val LOCAL_NETWORK_PERMISSION_REQUIRED = "localNetworkPermissionRequired"
}

package io.legado.app.ui.rss.read

import android.webkit.JavascriptInterface
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssReadRecord
import io.legado.app.data.entities.RssSource
import io.legado.app.help.webView.BaseJsExtensions
import io.legado.app.ui.login.SourceLoginType
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * RSS 订阅源 WebView 的 JS 扩展。
 *
 * 通用能力（put/get/searchBook/addBook/showPhoto/getStringList/getString、search/explore）
 * 已上移到 [BaseJsExtensions]；本类只保留 RSS 专属分支：login（含订阅源登录）、sort、rss。
 */
@Suppress("unused")
open class RssJsExtensions(
    activity: AppCompatActivity?,
    source: BaseSource?
) : BaseJsExtensions(activity, source) {

    @JavascriptInterface
    @JvmOverloads
    override fun open(name: String, url: String?, title: String?, origin: String?) {
        if (name !in rssActions) {
            super.open(name, url, title, origin)
            return
        }
        val activity = activityRef.get() ?: return
        activity.lifecycleScope.launch(IO) {
            val source = getSource() ?: return@launch
            when (name) {
                "login" -> {
                    val toSource = origin?.let { o ->
                        appDb.bookSourceDao.getBookSource(o)
                    } ?: source
                    if (toSource.loginUrl.isNullOrBlank()) {
                        activity.toastOnUi("源未配置登录")
                        return@launch
                    }
                    when (toSource) {
                        is BookSource -> {
                            withContext(Main) {
                                activity.startActivity(
                                    MainActivity.createSourceLoginIntent(
                                        activity,
                                        SourceLoginType.BookSource,
                                        toSource.bookSourceUrl
                                    )
                                )
                            }
                        }

                        is RssSource -> {
                            withContext(Main) {
                                activity.startActivity(
                                    MainActivity.createSourceLoginIntent(
                                        activity,
                                        SourceLoginType.RssSource,
                                        toSource.sourceUrl
                                    )
                                )
                            }
                        }
                    }
                }

                "sort" -> {
                    val toSource = origin?.let { o ->
                        appDb.rssSourceDao.getByKey(o)
                    } ?: (source as? RssSource) ?: return@launch
                    val sortUrl = if (url.isJsonObject()) {
                        url
                    } else {
                        title?.let {
                            JSONObject().put(title, url).toString()
                        } ?: url
                    }
                    val sourceUrl = toSource.sourceUrl
                    withContext(Main) {
                        activity.startActivity(
                            MainActivity.createRssSortIntent(
                                context = activity,
                                sourceUrl = sourceUrl,
                                sortUrl = sortUrl
                            )
                        )
                    }
                }

                "rss" -> {
                    val toSource = origin?.let { o ->
                        appDb.rssSourceDao.getByKey(o)
                    } ?: (source as? RssSource) ?: return@launch
                    val title = title ?: toSource.sourceName
                    val sourceUrl = toSource.sourceUrl
                    val link = url ?: return@launch
                    val rss = appDb.rssStarDao.get(sourceUrl, link)?.toRecord()
                        ?: appDb.rssArticleDao.getByLink(sourceUrl, link)?.toRecord()
                    val rssReadRecord = rss ?: RssReadRecord(
                        record = link,
                        title = title,
                        origin = sourceUrl,
                        readTime = System.currentTimeMillis()
                    )
                    appDb.rssReadRecordDao.insertRecord(rssReadRecord)
                    withContext(Main) {
                        activity.startActivity(
                            MainActivity.createRssReadIntent(
                                context = activity,
                                title = title,
                                origin = sourceUrl,
                                openUrl = link
                            )
                        )
                    }
                }
            }
        }
    }

    private companion object {
        val rssActions = setOf("login", "sort", "rss")
    }

}

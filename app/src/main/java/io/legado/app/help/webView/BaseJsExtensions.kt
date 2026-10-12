package io.legado.app.help.webView

import android.webkit.JavascriptInterface
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.help.JsExtensions
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.ui.association.AddToBookshelfDialog
import io.legado.app.ui.login.SourceLoginType
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * WebView JS 通用扩展基类。
 *
 * 承接原 `ui.rss.read.RssJsExtensions` 中与 RSS 无关的通用能力（书源 JS、书源登录、探索等共用）。
 * RSS 专属分支保留在 `io.legado.app.ui.rss.read.RssJsExtensions`（该类现继承本类）。
 */
@Suppress("unused")
open class BaseJsExtensions(activity: AppCompatActivity?, source: BaseSource?) : JsExtensions {

    val activityRef: WeakReference<AppCompatActivity> = WeakReference(activity)
    val sourceRef: WeakReference<BaseSource?> = WeakReference(source)

    override fun getSource(): BaseSource? {
        return sourceRef.get()
    }

    override fun getTag(): String? {
        return getSource()?.getTag()
    }

    @JavascriptInterface
    fun put(key: String, value: String): String {
        getSource()?.put(key, value)
        return value
    }

    @JavascriptInterface
    fun get(key: String): String {
        return getSource()?.get(key) ?: ""
    }

    @JavascriptInterface
    fun searchBook(key: String) {
        searchBook(key, null)
    }

    @JavascriptInterface
    fun searchBook(key: String, searchScope: String?) {
        activityRef.get()?.let {
            it.startActivity(MainActivity.createSearchIntent(it, key, searchScope))
        }
    }

    @JavascriptInterface
    fun addBook(bookUrl: String) {
        activityRef.get()?.showDialogFragment(AddToBookshelfDialog(bookUrl))
    }

    fun showPhoto(src: String) {
        activityRef.get()?.showDialogFragment(PhotoDialog(src, getSource()?.getKey()))
    }

    @JvmOverloads
    fun getStringList(rule: String?, mContent: Any? = null, isUrl: Boolean = false): List<String>? {
        return AnalyzeRule(null, source = getSource()).getStringList(rule, mContent, isUrl)
    }

    @JvmOverloads
    fun getString(ruleStr: String?, mContent: Any? = null, isUrl: Boolean = false): String {
        return AnalyzeRule(null, source = getSource()).getString(ruleStr, mContent, isUrl)
    }

    @JavascriptInterface
    @JvmOverloads
    open fun open(name: String, url: String? = null, title: String? = null, origin: String? = null) {
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
                    if (toSource is BookSource) {
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
                }

                "search" -> {
                    title?.let {
                        val searchScope = origin?.let { o ->
                            appDb.bookSourceDao.getBookSource(o)?.let { s ->
                                "${s.bookSourceName.replace(":", "")}::${o}"
                            }
                        }
                        withContext(Main) {
                            searchBook(it, searchScope)
                        }
                    }
                }

                "explore" -> {
                    val toSource = origin?.let { o ->
                        appDb.bookSourceDao.getBookSource(o)
                    } ?: (source as? BookSource) ?: return@launch
                    val sourceUrl = toSource.bookSourceUrl
                    withContext(Main) {
                        activity.startActivity(
                            MainActivity.createExploreShowIntent(
                                context = activity,
                                exploreName = title,
                                sourceUrl = sourceUrl,
                                exploreUrl = url
                            )
                        )
                    }
                }
            }
        }
    }

}

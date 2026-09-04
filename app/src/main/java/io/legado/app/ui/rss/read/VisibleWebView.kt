package io.legado.app.ui.rss.read

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
class VisibleWebView(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    init {
        setBackgroundColor(0)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}

@Composable
fun VisibleWebViewCompose(
    modifier: Modifier = Modifier,
    onCreated: (VisibleWebView) -> Unit,
    onDestroyed: (() -> Unit)? = null
) {
    val webViewHolder = remember { WebViewHolder() }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).apply {
                clipChildren = false
                clipToPadding = false
                val webView = VisibleWebView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                addView(webView)
                webViewHolder.webView = webView
                onCreated(webView)
            }
        },
        update = { container ->
            webViewHolder.webView = container.getChildAt(0) as? VisibleWebView
        }
    )
    DisposableEffect(Unit) {
        onDispose {
            onDestroyed?.invoke()
            webViewHolder.webView?.let { webView ->
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
            }
            webViewHolder.webView = null
        }
    }
}

private class WebViewHolder {
    var webView: VisibleWebView? = null
}

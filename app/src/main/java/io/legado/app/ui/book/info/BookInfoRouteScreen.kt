package io.legado.app.ui.book.info

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.BookGroup
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.book.info.edit.BookInfoEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginJsExtensions
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.openFileUri
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BookInfoRouteScreen(
    bookUrl: String,
    name: String? = null,
    author: String? = null,
    origin: String? = null,
    coverPath: String? = null,
    viewModel: BookInfoViewModel,
    onBack: () -> Unit,
    onFinish: (resultCode: Int?, afterTransition: Boolean) -> Unit,
    onOpenSearch: (String) -> Unit,
    onOpenBookSourceEdit: (String) -> Unit,
    onOpenSourceLogin: (String) -> Unit,
    onOpenReader: (bookUrl: String, inBookshelf: Boolean, chapterChanged: Boolean) -> Unit = { _, _, _ -> },
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val tocActivityResult = rememberLauncherForActivityResult(TocActivityResult()) {
        viewModel.onTocResult(it)
    }
    val localBookTreeSelect = rememberLauncherForActivityResult(HandleFileContract()) {
        it.uri?.let { treeUri ->
            viewModel.onIntent(BookInfoIntent.SetDefaultBookTreeUri(treeUri.toString()))
        }
    }
    val infoEditResult = rememberLauncherForActivityResult(
        StartActivityContract(BookInfoEditActivity::class.java)
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            viewModel.onInfoEdited()
        }
    }

    LaunchedEffect(bookUrl, name, author, origin, coverPath, viewModel) {
        viewModel.initData(
            bookUrl = bookUrl,
            name = name,
            author = author,
            origin = origin,
            coverPath = coverPath
        )
    }


    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshShelfState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(viewModel, activity) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is BookInfoEffect.ShowMessage -> context.toastOnUi(effect.message)
                is BookInfoEffect.Finish -> {
                    onFinish(effect.resultCode, effect.afterTransition)
                }

                is BookInfoEffect.OpenBookInfoEdit -> {
                    infoEditResult.launch {
                        putExtra("bookUrl", effect.bookUrl)
                    }
                }

                is BookInfoEffect.OpenReader -> {
                    onOpenReader(
                        effect.book.bookUrl,
                        effect.inBookshelf,
                        effect.chapterChanged,
                    )
                }

                is BookInfoEffect.OpenToc -> tocActivityResult.launch(effect.bookUrl)
                is BookInfoEffect.OpenBookSourceEdit -> {
                    onOpenBookSourceEdit(effect.sourceUrl)
                }

                is BookInfoEffect.OpenSourceLogin -> {
                    onOpenSourceLogin(effect.sourceUrl)
                }

                BookInfoEffect.OpenSelectBooksDir -> localBookTreeSelect.launch {
                    title = activity.getString(R.string.select_book_folder)
                }

                is BookInfoEffect.OpenFile -> activity.openFileUri(effect.uri, effect.mimeType)
                is BookInfoEffect.RunSourceCallback -> {
                    runSourceCallback(activity, effect, viewModel, onOpenSearch)
                }

                is BookInfoEffect.RunIntroJs -> {
                    runIntroJs(activity, effect)
                }

            }
        }
    }

    BookInfoScreen(
        state = uiState,
        groups = viewModel.allGroups
            .collectAsStateWithLifecycle(persistentListOf<BookGroup>()).value,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedCoverKey = sharedCoverKey,
    )
}

private fun runSourceCallback(
    activity: AppCompatActivity,
    effect: BookInfoEffect.RunSourceCallback,
    viewModel: BookInfoViewModel,
    onOpenSearch: (String) -> Unit,
) {
    SourceCallBack.callBackBtn(
        activity,
        effect.event,
        effect.source,
        effect.book,
        null,
    ) {
        when (val action = effect.action) {
            is BookInfoCallbackAction.Search -> {
                onOpenSearch(action.keyword)
            }

            is BookInfoCallbackAction.ShareText -> {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, action.text)
                    type = "text/plain"
                }
                activity.startActivity(Intent.createChooser(intent, action.chooserTitle))
            }

            is BookInfoCallbackAction.CopyText -> {
                activity.sendToClip(action.text)
            }

            BookInfoCallbackAction.ClearCache -> {
                viewModel.clearCache()
            }

            BookInfoCallbackAction.None -> Unit
        }
    }
}

private fun runIntroJs(activity: AppCompatActivity, effect: BookInfoEffect.RunIntroJs) {
    val source = effect.source ?: return
    activity.lifecycleScope.launch(IO) {
        try {
            val java = SourceLoginJsExtensions(activity, source)
            runScriptWithContext {
                source.evalJS(effect.click) {
                    put("result", null)
                    put("java", java)
                    put("book", effect.book)
                }
            }
        } catch (e: Throwable) {
            AppLog.put("${source.bookSourceName}: ${e.localizedMessage}", e)
            activity.toastOnUi("${effect.name} click error\n${e.localizedMessage}")
        }
    }
}

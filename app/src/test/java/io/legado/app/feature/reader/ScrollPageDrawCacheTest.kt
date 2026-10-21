package io.legado.app.feature.reader

import android.app.Application
import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageId
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScrollPageDrawCacheTest {

    @Test
    fun `visual page refresh sharing elements reuses draw data`() {
        val elements = listOf(text())
        val original = page(elements)
        val refreshed = original.copy(revision = 2L)
        val cache = ScrollPageDrawCache()

        val originalData = cache.ensure(original)

        assertSame(originalData, cache.ensure(refreshed))
    }

    @Test
    fun `search refresh reuses immutable layout draw data`() {
        val original = page(listOf(text()))
        val highlighted = original.copy(
            searchStart = 0,
            searchEndInclusive = 3,
        )
        val cache = ScrollPageDrawCache()

        val originalData = cache.ensure(original)

        assertSame(originalData, cache.ensure(highlighted))
    }

    @Test
    fun `repaginated page with a new elements list rebuilds draw data`() {
        val original = page(listOf(text()))
        val repaginated = original.copy(elements = listOf(text()))
        val cache = ScrollPageDrawCache()

        val originalData = cache.ensure(original)

        assertNotSame(originalData, cache.ensure(repaginated))
    }

    private fun page(elements: List<ReaderElement>) = ReaderPage(
        id = ReaderPageId(0, 0),
        chapterTitle = "",
        text = "text",
        widthPx = 100,
        heightPx = 200,
        contentTopPx = 0f,
        contentBottomPx = 200f,
        elements = elements,
        revision = 1L,
    )

    private fun text() = ReaderElement.Text(
        bounds = ReaderRect(0f, 0f, 20f, 20f),
        baselinePx = 16f,
        value = "text",
        style = ReaderTextStyle(colorArgb = 0xff000000.toInt(), fontSizePx = 16f),
        selected = false,
        emphasized = false,
        chapterPosition = 0,
    )
}

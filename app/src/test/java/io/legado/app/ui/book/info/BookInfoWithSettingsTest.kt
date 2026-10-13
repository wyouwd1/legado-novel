package io.legado.app.ui.book.info

import io.legado.app.domain.model.settings.CoverSettings
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookInfoWithSettingsTest {

    /**
     * 回归：打开详情页时屏幕状态会被整体重置为默认值（背景 "on"、showMangaUi true）。
     * 只要设置由 withSettings 从 gateway 派生叠加，重置后的默认屏幕状态就无法覆盖用户设置。
     */
    @Test
    fun resetScreenStateStillReflectsSavedOffSettings() {
        val screen = BookInfoUiState() // 相当于 initData 的重置结果，设置字段全为默认

        val result = screen.withSettings(
            theme = ThemeSettings(
                bookInfoNetworkCoverBackground = "off",
                bookInfoDefaultCoverBackground = "off_for_default",
                bookInfoFollowCoverColor = false,
            ),
            cover = CoverSettings(loadOnlyOnWifi = true),
            other = OtherSettings(),
        )

        assertEquals("off", result.bookInfoNetworkCoverBackground)
        assertEquals("off_for_default", result.bookInfoDefaultCoverBackground)
        assertFalse(result.bookInfoFollowCoverColor)
        assertTrue(result.loadCoverOnlyOnWifi)
    }

    @Test
    fun propagatesCoverDefaultsFromCoverSettings() {
        val result = BookInfoUiState().withSettings(
            theme = ThemeSettings(),
            cover = CoverSettings(defaultCover = "light.png", defaultCoverDark = "dark.png"),
            other = OtherSettings(),
        )

        assertEquals("light.png", result.defaultCover)
        assertEquals("dark.png", result.defaultCoverDark)
    }

    @Test
    fun preservesNonSettingScreenFields() {
        val screen = BookInfoUiState(
            inBookshelf = true,
            isBusy = true,
            groupNames = "分组A",
        )

        val result = screen.withSettings(ThemeSettings(), CoverSettings(), OtherSettings())

        assertTrue(result.inBookshelf)
        assertTrue(result.isBusy)
        assertEquals("分组A", result.groupNames)
    }
}

package io.legado.app.api

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.legado.app.R
import io.legado.app.ui.main.MainActivity

object ShortCuts {

    private fun buildBookShelfShortCutInfo(context: Context): ShortcutInfoCompat {
        val bookShelfIntent = MainActivity.createHomeIntent(context).apply {
            action = Intent.ACTION_VIEW
        }
        return ShortcutInfoCompat.Builder(context, "bookshelf")
            .setShortLabel(context.getString(R.string.bookshelf))
            .setLongLabel(context.getString(R.string.bookshelf))
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_st_book))
            .setIntent(bookShelfIntent)
            .build()
    }

    private fun buildReadBookShortCutInfo(context: Context): ShortcutInfoCompat {
        val bookShelfIntent = MainActivity.createHomeIntent(context).apply {
            action = Intent.ACTION_VIEW
        }
        val readBookIntent = MainActivity.createReadBookIntent(context).apply {
            action = Intent.ACTION_VIEW
        }
        return ShortcutInfoCompat.Builder(context, "lastRead")
            .setShortLabel(context.getString(R.string.last_read))
            .setLongLabel(context.getString(R.string.last_read))
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_st_rec))
            .setIntents(arrayOf(bookShelfIntent, readBookIntent))
            .build()
    }

    fun buildShortCuts(context: Context) {
        ShortcutManagerCompat.setDynamicShortcuts(
            context, listOf(
                buildReadBookShortCutInfo(context),
                buildBookShelfShortCutInfo(context)
            )
        )
    }

}

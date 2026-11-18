package dev.lite.launcher

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Process
import java.util.concurrent.ConcurrentHashMap

/**
 * Загрузка списка приложений. Иконки один раз рисуются в Bitmap нужного размера
 * и кэшируются, так что при прокрутке никакой тяжёлой работы нет.
 * Вызывать loadApps() нужно из фонового потока.
 */
class AppRepo(private val ctx: Context) {
    private val launcherApps = ctx.getSystemService(LauncherApps::class.java)
    private val me = Process.myUserHandle()
    private val size = ctx.dp(56)
    private val cache = ConcurrentHashMap<String, Bitmap>()

    fun invalidate(pkg: String) {
        val prefix = "$pkg/"
        cache.keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
    }

    fun loadApps(): List<AppEntry> {
        val self = ctx.packageName
        return launcherApps.getActivityList(null, me)
            .filter { it.componentName.packageName != self }
            .map { info ->
                val key = info.componentName.flattenToString()
                val icon = cache.getOrPut(key) { render(info.getBadgedIcon(0)) }
                AppEntry(key, info.label.toString(), info.componentName, icon)
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private fun render(d: Drawable): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        return bmp
    }
}

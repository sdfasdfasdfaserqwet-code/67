package dev.lite.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Папки хранятся в SharedPreferences как маленький JSON. */
class FolderStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("home", Context.MODE_PRIVATE)
    val folders = mutableListOf<Folder>()

    init {
        runCatching {
            val arr = JSONArray(sp.getString("folders", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val keys = mutableListOf<String>()
                val ka = o.getJSONArray("keys")
                for (j in 0 until ka.length()) keys += ka.getString(j)
                folders += Folder(o.getString("id"), o.getString("name"), keys)
            }
        }
    }

    fun save() {
        val arr = JSONArray()
        folders.forEach { f ->
            arr.put(
                JSONObject()
                    .put("id", f.id)
                    .put("name", f.name)
                    .put("keys", JSONArray(f.keys))
            )
        }
        sp.edit().putString("folders", arr.toString()).apply()
    }

    fun folderOf(key: String): Folder? = folders.firstOrNull { key in it.keys }

    fun create(name: String): Folder {
        val f = Folder(UUID.randomUUID().toString(), name, mutableListOf())
        folders += f
        return f
    }

    /** Переносит приложение в папку [target] (null = достать из папки на экран). */
    fun move(key: String, target: Folder?) {
        folders.forEach { it.keys.remove(key) }
        target?.keys?.add(key)
        folders.removeAll { it.keys.isEmpty() }
        save()
    }

    fun delete(folder: Folder) {
        folders.remove(folder)
        save()
    }
}

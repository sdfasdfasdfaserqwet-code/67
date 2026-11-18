package dev.lite.launcher

import android.content.ComponentName
import android.graphics.Bitmap

class AppEntry(
    val key: String,
    val label: String,
    val component: ComponentName,
    val icon: Bitmap,
)

class Folder(
    val id: String,
    var name: String,
    val keys: MutableList<String>,
)

sealed interface Item

class AppItem(val app: AppEntry) : Item

class FolderItem(val folder: Folder, val apps: List<AppEntry>) : Item

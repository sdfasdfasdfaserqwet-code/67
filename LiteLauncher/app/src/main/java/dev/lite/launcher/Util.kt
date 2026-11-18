package dev.lite.launcher

import android.content.Context

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Context.dpf(v: Int): Float = v * resources.displayMetrics.density

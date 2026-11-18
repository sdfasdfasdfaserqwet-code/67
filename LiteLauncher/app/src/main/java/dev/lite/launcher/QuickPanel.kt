package dev.lite.launcher

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

/** Содержимое шторки: часы, дата и плитки быстрых настроек. */
class QuickPanel(ctx: Context, private val onNavigate: () -> Unit) : LinearLayout(ctx) {

    private val cam = ctx.getSystemService(CameraManager::class.java)
    private val conn = ctx.getSystemService(ConnectivityManager::class.java)
    private val audio = ctx.getSystemService(AudioManager::class.java)

    private val torchId: String? = runCatching {
        cam.cameraIdList.firstOrNull { id ->
            val ch = cam.getCameraCharacteristics(id)
            ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrNull()
    private var torchOn = false

    private val torchCb = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) {
                torchOn = enabled
                updateTorchTile()
            }
        }
    }

    private val tNet = Tile(R.drawable.ic_wifi, "Интернет", { openInternet() }, { go(Intent(Settings.ACTION_WIFI_SETTINGS)) })
    private val tBt = Tile(R.drawable.ic_bluetooth, "Bluetooth", { go(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }, null)
    private val tTorch = Tile(R.drawable.ic_flashlight, "Фонарик", { toggleTorch() }, null)
    private val tSound = Tile(R.drawable.ic_volume, "Звук", { showVolume() }, null)
    private val tAir = Tile(R.drawable.ic_airplane, "Авиарежим", { go(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) }, null)
    private val tSet = Tile(R.drawable.ic_settings, "Настройки", { go(Intent(Settings.ACTION_SETTINGS)) }, null)

    init {
        orientation = VERTICAL
        background = GradientDrawable().apply {
            setColor(0xF01B1C20.toInt())
            val r = ctx.dpf(28)
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        }
        setPadding(ctx.dp(16), ctx.dp(40), ctx.dp(16), ctx.dp(12))

        addView(TextClock(ctx).apply {
            format12Hour = "HH:mm"
            format24Hour = "HH:mm"
            textSize = 44f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        addView(TextClock(ctx).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            textSize = 14f
            setTextColor(0xB3FFFFFF.toInt())
        })

        addView(row(tNet, tBt, tTorch), LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ctx.dp(20)
        })
        addView(row(tSound, tAir, tSet), LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = ctx.dp(8)
        })

        // «ручка» внизу шторки
        addView(View(ctx).apply {
            background = GradientDrawable().apply {
                setColor(0x55FFFFFF)
                cornerRadius = ctx.dpf(2)
            }
        }, LayoutParams(ctx.dp(36), ctx.dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = ctx.dp(14)
        })
    }

    private fun row(a: Tile, b: Tile, c: Tile): LinearLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL
        listOf(a, b, c).forEach {
            addView(it.view, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    fun start() {
        runCatching { cam.registerTorchCallback(torchCb, null) }
    }

    fun stop() {
        runCatching { cam.unregisterTorchCallback(torchCb) }
    }

    /** Обновляет состояние плиток. Вызывается при каждом открытии шторки. */
    fun refresh() {
        val caps = conn.activeNetwork?.let { conn.getNetworkCapabilities(it) }
        when {
            caps == null -> tNet.set(false, "Нет сети")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> tNet.set(true, "Wi‑Fi")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> tNet.set(true, "Моб. сеть")
            else -> tNet.set(true, "Сеть")
        }

        val bt = runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled
        }.getOrNull() == true
        tBt.set(bt, if (bt) "Вкл" else "Выкл")

        updateTorchTile()

        val ringer = audio.ringerMode
        tSound.set(
            ringer == AudioManager.RINGER_MODE_NORMAL,
            when (ringer) {
                AudioManager.RINGER_MODE_VIBRATE -> "Вибро"
                AudioManager.RINGER_MODE_SILENT -> "Тихо"
                else -> "Звук"
            }
        )

        val air = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        tAir.set(air, if (air) "Вкл" else "Выкл")

        tSet.set(false, "")
    }

    private fun updateTorchTile() {
        tTorch.set(torchOn, if (torchOn) "Вкл" else "Выкл")
    }

    private fun toggleTorch() {
        val id = torchId ?: return
        runCatching { cam.setTorchMode(id, !torchOn) }
    }

    private fun showVolume() {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
        onNavigate()
    }

    private fun openInternet() {
        val action = if (Build.VERSION.SDK_INT >= 29) {
            Settings.Panel.ACTION_INTERNET_CONNECTIVITY
        } else {
            Settings.ACTION_WIRELESS_SETTINGS
        }
        go(Intent(action))
    }

    private fun go(i: Intent) {
        runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        onNavigate()
    }

    private inner class Tile(
        iconRes: Int,
        label: String,
        click: () -> Unit,
        long: (() -> Unit)?,
    ) {
        val view = LinearLayout(context)
        private val circle = GradientDrawable().apply { shape = GradientDrawable.OVAL }
        private val sub = TextView(context)

        init {
            view.orientation = VERTICAL
            view.gravity = Gravity.CENTER_HORIZONTAL
            view.setPadding(0, context.dp(6), 0, context.dp(6))

            val holder = FrameLayout(context)
            holder.background = circle
            holder.addView(
                ImageView(context).apply { setImageResource(iconRes) },
                FrameLayout.LayoutParams(context.dp(26), context.dp(26), Gravity.CENTER)
            )
            view.addView(holder, LayoutParams(context.dp(56), context.dp(56)))

            view.addView(TextView(context).apply {
                text = label
                textSize = 12f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dp(6)
            })

            sub.textSize = 10f
            sub.setTextColor(0x99FFFFFF.toInt())
            sub.gravity = Gravity.CENTER
            view.addView(sub, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            view.setOnClickListener { click() }
            if (long != null) view.setOnLongClickListener { long(); true }
            set(false, "")
        }

        fun set(active: Boolean, subtitle: String) {
            circle.setColor(if (active) 0xFF4C8DFF.toInt() else 0x33FFFFFF)
            sub.text = subtitle
        }
    }
}

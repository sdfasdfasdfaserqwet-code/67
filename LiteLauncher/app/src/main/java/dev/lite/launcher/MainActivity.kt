package dev.lite.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var repo: AppRepo
    private lateinit var store: FolderStore
    private lateinit var launcherApps: LauncherApps
    private lateinit var list: RecyclerView
    private lateinit var homeAdapter: HomeAdapter
    private lateinit var host: ShadeHost
    private lateinit var quick: QuickPanel

    private val exec = Executors.newSingleThreadExecutor()
    private var apps: List<AppEntry> = emptyList()
    private var gen = 0
    private var folderDialog: Dialog? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = changed(packageName)
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = changed(packageName)
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = changed(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
            packageNames?.forEach { repo.invalidate(it) }
            reload()
        }

        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
            reload()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        repo = AppRepo(this)
        store = FolderStore(this)
        launcherApps = getSystemService(LauncherApps::class.java)

        homeAdapter = HomeAdapter(::onItemClick, ::onItemLong)
        list = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(context, 4)
            adapter = homeAdapter
            setHasFixedSize(true)
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setItemViewCacheSize(24)
        }

        host = ShadeHost(this)
        quick = QuickPanel(this) { host.close() }
        val scrim = View(this).apply {
            setBackgroundColor(0x66000000)
            setOnClickListener { host.close() }
        }
        host.addView(list, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        host.addView(scrim, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        host.addView(quick, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))
        host.bind(list, quick, scrim)
        host.onShow = { quick.refresh() }
        setContentView(host)

        ViewCompat.setOnApplyWindowInsetsListener(host) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            list.setPadding(dp(8), bars.top + dp(12), dp(8), bars.bottom + dp(12))
            quick.setPadding(dp(16), bars.top + dp(16), dp(16), dp(12))
            insets
        }
        WindowInsetsControllerCompat(window, host).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        reload()
    }

    override fun onStart() {
        super.onStart()
        launcherApps.registerCallback(callback)
        quick.start()
    }

    override fun onStop() {
        super.onStop()
        launcherApps.unregisterCallback(callback)
        quick.stop()
        host.close(false)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            host.close(false)
            folderDialog?.dismiss()
            list.scrollToPosition(0)
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Лаунчер не должен закрываться по «Назад»
        when {
            host.isOpen -> host.close()
            list.canScrollVertically(-1) -> list.smoothScrollToPosition(0)
        }
    }

    // ---------- данные ----------

    private fun changed(pkg: String?) {
        if (pkg != null) repo.invalidate(pkg)
        reload()
    }

    private fun reload() {
        val g = ++gen
        exec.execute {
            val loaded = repo.loadApps()
            runOnUiThread {
                if (g == gen) {
                    apps = loaded
                    rebuild()
                }
            }
        }
    }

    private fun rebuild() {
        val byKey = apps.associateBy { it.key }
        val inFolder = HashSet<String>()
        val items = ArrayList<Item>()
        store.folders.sortedBy { it.name.lowercase() }.forEach { f ->
            val fa = f.keys.mapNotNull { byKey[it] }
            if (fa.isNotEmpty()) {
                items += FolderItem(f, fa)
                inFolder.addAll(f.keys)
            }
        }
        apps.forEach { if (it.key !in inFolder) items += AppItem(it) }
        homeAdapter.submit(items)
    }

    // ---------- клики ----------

    private fun onItemClick(item: Item, @Suppress("UNUSED_PARAMETER") v: View) {
        when (item) {
            is AppItem -> launch(item.app)
            is FolderItem -> openFolder(item)
        }
    }

    private fun onItemLong(item: Item, v: View) {
        when (item) {
            is AppItem -> showAppMenu(item.app, v)
            is FolderItem -> showFolderMenu(item.folder, v)
        }
    }

    private fun launch(app: AppEntry) {
        runCatching {
            launcherApps.startMainActivity(app.component, Process.myUserHandle(), null, null)
        }.onFailure {
            Toast.makeText(this, "Не удалось открыть ${app.label}", Toast.LENGTH_SHORT).show()
        }
        folderDialog?.dismiss()
    }

    // ---------- меню ----------

    private fun showAppMenu(app: AppEntry, anchor: View) {
        val inFolder = store.folderOf(app.key) != null
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, "В папку…")
        if (inFolder) pm.menu.add(0, 2, 0, "Убрать из папки")
        pm.menu.add(0, 3, 0, "О приложении")
        pm.menu.add(0, 4, 0, "Удалить")
        pm.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                1 -> chooseFolder(app)
                2 -> {
                    store.move(app.key, null)
                    refreshAfterEdit()
                }
                3 -> runCatching {
                    launcherApps.startAppDetailsActivity(app.component, Process.myUserHandle(), null, null)
                }
                4 -> runCatching {
                    startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.component.packageName}")))
                }
            }
            true
        }
        pm.show()
    }

    private fun showFolderMenu(folder: Folder, anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, "Переименовать")
        pm.menu.add(0, 2, 0, "Расформировать")
        pm.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                1 -> askName("Название папки", folder.name) {
                    folder.name = it
                    store.save()
                    refreshAfterEdit()
                }
                2 -> {
                    store.delete(folder)
                    refreshAfterEdit()
                }
            }
            true
        }
        pm.show()
    }

    private fun chooseFolder(app: AppEntry) {
        val names = store.folders.map { it.name } + "Новая папка…"
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("В папку")
            .setItems(names.toTypedArray()) { _, i ->
                if (i < store.folders.size) {
                    store.move(app.key, store.folders[i])
                    refreshAfterEdit()
                } else {
                    askName("Новая папка", "") { n ->
                        val f = store.create(n)
                        store.move(app.key, f)
                        refreshAfterEdit()
                    }
                }
            }
            .show()
    }

    private fun askName(title: String, initial: String, ok: (String) -> Unit) {
        val et = EditText(this).apply {
            setText(initial)
            setSelection(text.length)
            isSingleLine = true
            hint = "Название"
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setView(et)
            .setPositiveButton("OK") { _, _ ->
                val n = et.text.toString().trim()
                if (n.isNotEmpty()) ok(n)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun refreshAfterEdit() {
        rebuild()
        folderDialog?.dismiss()
    }

    // ---------- открытая папка ----------

    private fun openFolder(item: FolderItem) {
        folderDialog?.dismiss()
        val d = Dialog(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xE6202124.toInt())
                cornerRadius = dpf(28)
            }
            setPadding(dp(12), dp(16), dp(12), dp(12))
        }
        box.addView(TextView(this).apply {
            text = item.folder.name
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val inner = HomeAdapter(
            { it, _ -> if (it is AppItem) launch(it.app) },
            { it, v -> if (it is AppItem) showAppMenu(it.app, v) }
        )
        inner.submit(item.apps.map { AppItem(it) })
        val rv = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(context, 4)
            adapter = inner
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val maxH = (resources.displayMetrics.heightPixels * 0.6f).toInt()
        box.addView(rv, LinearLayout.LayoutParams(MATCH_PARENT, if (item.apps.size > 16) maxH else WRAP_CONTENT))

        d.setContentView(box)
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), WRAP_CONTENT)
        d.window?.setDimAmount(0.35f)
        d.show()
        folderDialog = d
    }
}

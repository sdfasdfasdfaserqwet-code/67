package dev.lite.launcher

import android.graphics.Color
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HomeAdapter(
    private val onClick: (Item, View) -> Unit,
    private val onLong: (Item, View) -> Unit,
) : RecyclerView.Adapter<HomeAdapter.VH>() {

    private var items: List<Item> = emptyList()

    fun submit(list: List<Item>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(val glyph: GlyphView, val label: TextView, cell: View) : RecyclerView.ViewHolder(cell)

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val c = parent.context
        val cell = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(c.dp(2), c.dp(10), c.dp(2), c.dp(6))
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isClickable = true
            val tv = TypedValue()
            if (c.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)) {
                background = c.getDrawable(tv.resourceId)
            }
        }
        val glyph = GlyphView(c)
        cell.addView(glyph, LinearLayout.LayoutParams(c.dp(56), c.dp(56)))

        val label = TextView(c).apply {
            textSize = 11f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setShadowLayer(3f, 0f, 1f, 0xAA000000.toInt())
        }
        cell.addView(
            label,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = c.dp(4) }
        )

        val vh = VH(glyph, label, cell)
        cell.setOnClickListener {
            val p = vh.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(items[p], cell)
        }
        cell.setOnLongClickListener {
            val p = vh.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onLong(items[p], cell)
            true
        }
        return vh
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        when (val item = items[position]) {
            is AppItem -> {
                h.glyph.bindApp(item.app.icon)
                h.label.text = item.app.label
            }
            is FolderItem -> {
                h.glyph.bindFolder(item.apps.map { it.icon })
                h.label.text = item.folder.name
            }
        }
    }
}

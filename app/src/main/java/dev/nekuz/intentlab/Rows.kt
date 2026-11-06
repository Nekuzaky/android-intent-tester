package dev.nekuz.intentlab

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

sealed class Row {
    data class Header(val text: String) : Row()

    data class HandlerRow(
        val handler: ResolvedHandler,
        val subtitle: String,
        val tag: String?,
        val tagColor: Int,
    ) : Row()

    data class EntryRow(
        val entry: HistoryEntry,
        val favourite: Boolean,
        val title: String,
        val subtitle: String,
        val tag: String?,
        val tagColor: Int,
    ) : Row()

    data class InfoRow(val title: String, val subtitle: String) : Row()
}

class RowAdapter(private val context: Context) : BaseAdapter() {
    var rows: List<Row> = emptyList()
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    private val inflater = LayoutInflater.from(context)

    override fun getCount() = rows.size
    override fun getItem(position: Int) = rows[position]
    override fun getItemId(position: Int) = position.toLong()
    override fun getViewTypeCount() = 2
    override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1
    override fun isEnabled(position: Int) = rows[position] is Row.HandlerRow || rows[position] is Row.EntryRow

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = rows[position]
        if (row is Row.Header) {
            val view = convertView ?: inflater.inflate(R.layout.row_header, parent, false)
            (view as TextView).text = row.text
            return view
        }
        val view = convertView ?: inflater.inflate(R.layout.row_item, parent, false)
        val icon = view.findViewById<ImageView>(R.id.icon)
        val title = view.findViewById<TextView>(R.id.title)
        val subtitle = view.findViewById<TextView>(R.id.subtitle)
        val tag = view.findViewById<TextView>(R.id.tag)
        val chevron = view.findViewById<View>(R.id.chevron)
        icon.tag = null
        title.maxLines = 1
        subtitle.maxLines = 3
        when (row) {
            is Row.HandlerRow -> {
                icon.visibility = View.VISIBLE
                Icons.bind(context, icon, row.handler.packageName)
                title.text = row.handler.appLabel
                subtitle.text = row.subtitle
                bindTag(tag, row.tag, row.tagColor)
                chevron.visibility = View.VISIBLE
            }
            is Row.EntryRow -> {
                icon.visibility = View.GONE
                title.text = row.title
                subtitle.text = row.subtitle
                bindTag(tag, row.tag, row.tagColor)
                chevron.visibility = View.VISIBLE
            }
            is Row.InfoRow -> {
                icon.visibility = View.GONE
                title.maxLines = 4
                subtitle.maxLines = 10
                title.text = row.title
                subtitle.text = row.subtitle
                bindTag(tag, null, 0)
                chevron.visibility = View.GONE
            }
            is Row.Header -> Unit
        }
        subtitle.visibility = if (subtitle.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        return view
    }

    private fun bindTag(view: TextView, text: String?, color: Int) {
        if (text == null) {
            view.visibility = View.GONE
            return
        }
        view.visibility = View.VISIBLE
        view.text = text
        view.setTextColor(context.getColor(color))
    }
}

object HandlerRows {
    fun row(context: Context, handler: ResolvedHandler, extraTag: String? = null, extraColor: Int = 0): Row.HandlerRow {
        val lines = ArrayList<String>()
        lines.add(handler.shownComponent)
        handler.permission?.let { lines.add(context.getString(R.string.permission_required, TextCleaner.line(it, 120))) }
        val (tag, color) = when {
            !handler.exported -> context.getString(R.string.tag_not_exported) to R.color.danger
            !handler.enabled -> context.getString(R.string.tag_disabled) to R.color.danger
            extraTag != null -> extraTag to extraColor
            handler.isDefault -> context.getString(R.string.tag_default) to R.color.mint
            else -> null to 0
        }
        return Row.HandlerRow(handler, lines.joinToString("\n"), tag, color)
    }
}

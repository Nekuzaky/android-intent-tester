package dev.nekuz.intentlab

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView

class SuggestionAdapter(context: Context, private val all: List<String>) : BaseAdapter(), Filterable {
    private val inflater = LayoutInflater.from(context)
    private var shown: List<String> = all

    override fun getCount() = shown.size
    override fun getItem(position: Int) = shown[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_text, parent, false)
        (view as TextView).text = shown[position]
        return view
    }

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = constraint?.toString().orEmpty().trim()
            val matches = if (query.isEmpty()) all else all.filter { it.contains(query, ignoreCase = true) }
            return FilterResults().apply {
                values = matches
                count = matches.size
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            shown = results?.values as? List<String> ?: all
            notifyDataSetChanged()
        }
    }
}

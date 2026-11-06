package dev.nekuz.intentlab

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

class HistoryPanel(
    private val activity: MainActivity,
    root: View,
    private val store: HistoryStore,
) {
    private val adapter = RowAdapter(activity)
    private val empty: TextView = root.findViewById(R.id.historyEmpty)
    private val clearButton: View = root.findViewById(R.id.clearHistoryButton)
    private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    private val listener: () -> Unit = { render() }

    init {
        val list = root.findViewById<ListView>(R.id.historyList)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            (adapter.rows[position] as? Row.EntryRow)?.let { activity.editInBuilder(it.entry.spec) }
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            val row = adapter.rows[position] as? Row.EntryRow ?: return@setOnItemLongClickListener false
            showActions(row)
            true
        }
        clearButton.setOnClickListener { confirmClear() }
    }

    fun start() {
        store.addListener(listener)
        render()
    }

    fun stop() {
        store.removeListener(listener)
    }

    private fun render() {
        val rows = buildList {
            if (store.favourites.isNotEmpty()) {
                add(Row.Header(activity.getString(R.string.section_favourites)))
                store.favourites.forEach { add(entryRow(it, favourite = true)) }
            }
            if (store.history.isNotEmpty()) {
                add(Row.Header(activity.getString(R.string.section_recent)))
                store.history.forEach { add(entryRow(it, favourite = false)) }
            }
        }
        adapter.rows = rows
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        clearButton.isEnabled = store.history.isNotEmpty()
    }

    private fun entryRow(entry: HistoryEntry, favourite: Boolean): Row.EntryRow {
        val spec = entry.spec
        val action = TextCleaner.line(spec.action, 120).ifEmpty { activity.getString(R.string.entry_no_action) }
        val target = if (spec.className.isNotEmpty()) "${spec.packageName}/${spec.fullClassName}" else spec.packageName
        val details = listOf(spec.data, target).filter { it.isNotEmpty() }.map { TextCleaner.line(it, 160) }
        val time = dateFormat.format(Date(entry.timeMillis))
        val outcome = TextCleaner.line(entry.outcome, 20)
        val footer = if (outcome.isEmpty()) time else "$time · $outcome"
        return Row.EntryRow(
            entry = entry,
            favourite = favourite,
            title = if (favourite) TextCleaner.label(entry.name, action) else action,
            subtitle = (details + footer).joinToString("\n"),
            tag = spec.mode.shortLabel,
            tagColor = if (entry.outcome == activity.getString(R.string.outcome_failed)) R.color.danger else R.color.brand_soft,
        )
    }

    private fun showActions(row: Row.EntryRow) {
        val labels = if (row.favourite) {
            arrayOf(activity.getString(R.string.entry_rename), activity.getString(R.string.delete))
        } else {
            arrayOf(activity.getString(R.string.entry_save_favourite), activity.getString(R.string.delete))
        }
        AlertDialog.Builder(activity)
            .setTitle(row.title)
            .setItems(labels) { _, which ->
                when {
                    which == 1 && row.favourite -> store.deleteFavourite(row.entry.id)
                    which == 1 -> store.deleteRecent(row.entry.id)
                    row.favourite -> askName(row.entry.name) { store.renameFavourite(row.entry.id, it) }
                    else -> askName("") { name ->
                        if (!store.addFavourite(name, row.entry.spec)) {
                            Toast.makeText(activity, R.string.not_kept_in_history, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .show()
    }

    private fun askName(current: String, onName: (String) -> Unit) {
        val input = LayoutInflater.from(activity).inflate(R.layout.dialog_name, null) as EditText
        input.setText(current)
        AlertDialog.Builder(activity)
            .setTitle(R.string.favourite_title)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                onName(TextCleaner.label(input.text, activity.getString(R.string.favourite_default)))
            }
            .show()
    }

    private fun confirmClear() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.clear_history)
            .setMessage(R.string.clear_history_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> store.clearHistory() }
            .show()
    }
}

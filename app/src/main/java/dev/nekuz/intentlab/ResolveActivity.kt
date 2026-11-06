package dev.nekuz.intentlab

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView

class ResolveActivity : Activity() {

    private lateinit var adapter: RowAdapter
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val spec = intent.getStringExtra(EXTRA_SPEC)?.let(HistoryCodec::decodeSpec)
        if (spec == null) {
            finish()
            return
        }
        setContentView(R.layout.activity_resolve)
        progress = findViewById(R.id.progress)
        adapter = RowAdapter(this)
        val list = findViewById<ListView>(R.id.list)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            (adapter.rows[position] as? Row.HandlerRow)?.let { confirmTarget(it.handler) }
        }
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<TextView>(R.id.subtitle).text = TextCleaner.line(
            listOf(spec.action, spec.data, spec.packageName).firstOrNull { it.isNotEmpty() }
        )
        load(spec)
    }

    private fun load(spec: IntentSpec) {
        progress.visibility = View.VISIBLE
        val appContext = applicationContext
        Background.query.execute {
            val intent = try {
                IntentFactory.build(spec)
            } catch (error: Throwable) {
                null
            }
            val results = HandlerKind.entries.associateWith { kind ->
                if (intent == null) {
                    Resolution(emptyList(), getString(R.string.load_failed))
                } else {
                    Resolver.query(appContext, intent, kind)
                }
            }
            Background.onMain {
                if (isDestroyed) return@onMain
                progress.visibility = View.GONE
                adapter.rows = buildRows(spec, results)
            }
        }
    }

    private fun buildRows(spec: IntentSpec, results: Map<HandlerKind, Resolution>): List<Row> = buildList {
        val used = when (spec.mode) {
            SendMode.ACTIVITY, SendMode.ACTIVITY_FOR_RESULT -> HandlerKind.ACTIVITY
            SendMode.BROADCAST -> HandlerKind.RECEIVER
            SendMode.SERVICE -> HandlerKind.SERVICE
        }
        val order = listOf(used) + HandlerKind.entries.filter { it != used }
        for (kind in order) {
            val name = getString(sectionName(kind))
            add(Row.Header(if (kind == used) getString(R.string.section_used, name, spec.mode.label) else name))
            val result = results[kind]
            when {
                result?.failure != null -> add(Row.InfoRow(getString(R.string.resolve_failed, result.failure), ""))
                result == null || result.handlers.isEmpty() -> add(Row.InfoRow(getString(R.string.no_matches), ""))
                else -> result.handlers.forEach { add(HandlerRows.row(this@ResolveActivity, it)) }
            }
        }
        add(Row.InfoRow(getString(R.string.resolve_uri_note), ""))
    }

    private fun sectionName(kind: HandlerKind): Int = when (kind) {
        HandlerKind.ACTIVITY -> R.string.section_activities
        HandlerKind.SERVICE -> R.string.section_services
        HandlerKind.RECEIVER -> R.string.section_receivers
    }

    private fun confirmTarget(handler: ResolvedHandler) {
        val requirement = handler.permission?.let {
            getString(R.string.permission_required, TextCleaner.line(it, 120))
        }.orEmpty()
        AlertDialog.Builder(this)
            .setTitle(handler.appLabel)
            .setMessage(
                getString(
                    R.string.handler_details,
                    handler.shownComponent,
                    getString(if (handler.exported) R.string.yes else R.string.no),
                    requirement,
                ).trimEnd()
            )
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(R.string.use_as_target) { _, _ ->
                setResult(
                    RESULT_OK,
                    Intent()
                        .putExtra(RESULT_PACKAGE, handler.packageName)
                        .putExtra(RESULT_CLASS, handler.className),
                )
                finish()
            }
            .show()
    }

    companion object {
        const val EXTRA_SPEC = "spec"
        const val RESULT_PACKAGE = "package"
        const val RESULT_CLASS = "class"
    }
}

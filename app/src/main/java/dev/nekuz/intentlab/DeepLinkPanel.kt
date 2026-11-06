package dev.nekuz.intentlab

import android.app.AlertDialog
import android.view.View
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView

class DeepLinkPanel(private val activity: MainActivity, root: View) {
    private val input: EditText = root.findViewById(R.id.linkInput)
    private val status: TextView = root.findViewById(R.id.linkStatus)
    private val progress: ProgressBar = root.findViewById(R.id.handlersProgress)
    private val adapter = RowAdapter(activity)
    private var generation = 0
    private var destroyed = false
    private var lookups: Map<String, DomainLookup> = emptyMap()
    private var currentLink: DeepLinkParse.Ok? = null

    init {
        val list = root.findViewById<ListView>(R.id.handlersList)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            (adapter.rows[position] as? Row.HandlerRow)?.let { showHandler(it.handler) }
        }
        root.findViewById<View>(R.id.listHandlersButton).setOnClickListener { listHandlers() }
        root.findViewById<View>(R.id.openLinkButton).setOnClickListener { open(null) }
        root.findViewById<View>(R.id.linkToBuilderButton).setOnClickListener {
            parsed()?.let { activity.editInBuilder(specFor(it, null)) }
        }
    }

    fun destroy() {
        destroyed = true
        generation++
    }

    private fun parsed(): DeepLinkParse.Ok? = when (val result = DeepLinks.parse(input.text.toString())) {
        is DeepLinkParse.Invalid -> {
            showStatus(result.message, R.color.danger)
            null
        }
        is DeepLinkParse.Ok -> {
            if (result.warnings.isEmpty()) {
                status.visibility = View.GONE
            } else {
                showStatus(result.warnings.joinToString("\n") { "• $it" }, R.color.amber)
            }
            result
        }
    }

    private fun showStatus(text: String, color: Int) {
        status.visibility = View.VISIBLE
        status.setTextColor(activity.getColor(color))
        status.text = text
    }

    private fun specFor(link: DeepLinkParse.Ok, handler: ResolvedHandler?): IntentSpec = IntentSpec(
        action = ACTION_VIEW,
        data = link.uri,
        categories = listOf(CATEGORY_BROWSABLE),
        packageName = handler?.packageName.orEmpty(),
        className = handler?.className.orEmpty(),
        mode = SendMode.ACTIVITY,
    )

    private fun open(handler: ResolvedHandler?) {
        val link = parsed() ?: return
        activity.requestSend(specFor(link, handler)) { outcome ->
            when (outcome) {
                is SendOutcome.Sent -> showStatus(activity.getString(R.string.deep_link_opened), R.color.mint)
                is SendOutcome.Failed -> showStatus(outcome.message, R.color.danger)
            }
        }
    }

    private fun listHandlers() {
        val link = parsed() ?: return
        currentLink = link
        val request = ++generation
        progress.visibility = View.VISIBLE
        adapter.rows = emptyList()
        val appContext = activity.applicationContext
        Background.query.execute {
            val resolution = try {
                Resolver.query(appContext, IntentFactory.build(specFor(link, null)), HandlerKind.ACTIVITY)
            } catch (error: Throwable) {
                Resolution(emptyList(), FailureText.describe(error))
            }
            val found = HashMap<String, DomainLookup>()
            if (link.isWeb) {
                resolution.handlers.map { it.packageName }.distinct().forEach { name ->
                    found[name] = DomainStates.lookup(appContext, name)
                }
            }
            Background.onMain {
                if (destroyed || request != generation) return@onMain
                progress.visibility = View.GONE
                lookups = found
                adapter.rows = buildRows(link, resolution, found)
            }
        }
    }

    private fun buildRows(
        link: DeepLinkParse.Ok,
        resolution: Resolution,
        found: Map<String, DomainLookup>,
    ): List<Row> = buildList {
        add(Row.Header(activity.getString(R.string.section_link)))
        val host = TextCleaner.line(link.host).ifEmpty { activity.getString(R.string.link_no_host) }
        add(Row.InfoRow(activity.getString(R.string.link_summary, TextCleaner.line(link.scheme), host), ""))
        add(Row.Header(activity.getString(R.string.section_handlers)))
        if (resolution.failure != null) {
            add(Row.InfoRow(activity.getString(R.string.resolve_failed, resolution.failure), ""))
            return@buildList
        }
        if (resolution.handlers.isEmpty()) {
            add(Row.InfoRow(activity.getString(R.string.no_handlers), ""))
            return@buildList
        }
        val default = resolution.handlers.firstOrNull { it.isDefault }
        add(
            Row.InfoRow(
                if (default != null) {
                    activity.getString(R.string.default_handler_is, default.appLabel)
                } else {
                    activity.getString(R.string.default_handler_none)
                },
                "",
            )
        )
        resolution.handlers.forEach { handler ->
            val (tag, color) = verdictTag(link, found[handler.packageName])
            add(HandlerRows.row(activity, handler, tag, color))
        }
    }

    private fun verdictTag(link: DeepLinkParse.Ok, lookup: DomainLookup?): Pair<String?, Int> {
        if (!link.isWeb) return null to 0
        return when (lookup) {
            is DomainLookup.Available -> {
                val verdict = DeepLinks.verdictFor(link.host, lookup.hosts)
                verdict.label to when (verdict) {
                    DomainVerdict.VERIFIED -> R.color.mint
                    DomainVerdict.SELECTED -> R.color.brand_soft
                    DomainVerdict.NONE -> R.color.amber
                    DomainVerdict.NOT_DECLARED -> R.color.text_secondary
                }
            }
            is DomainLookup.Refused -> activity.getString(R.string.tag_unknown) to R.color.text_secondary
            else -> null to 0
        }
    }

    private fun showHandler(handler: ResolvedHandler) {
        val link = currentLink ?: return
        val requirement = handler.permission?.let {
            activity.getString(R.string.permission_required, TextCleaner.line(it, 120))
        }.orEmpty()
        val base = activity.getString(
            R.string.handler_details,
            handler.shownComponent,
            activity.getString(if (handler.exported) R.string.yes else R.string.no),
            requirement,
        ).trimEnd()
        val message = base + "\n\n" + verificationText(link, lookups[handler.packageName])
        val dialog = AlertDialog.Builder(activity)
            .setTitle(handler.appLabel)
            .setMessage(message)
            .setNegativeButton(R.string.close, null)
            .setNeutralButton(R.string.use_in_builder) { _, _ ->
                activity.editInBuilder(specFor(link, handler))
            }
            .setPositiveButton(R.string.open_in_app) { _, _ -> open(handler) }
            .show()
        activity.protect(dialog)
    }

    private fun verificationText(link: DeepLinkParse.Ok, lookup: DomainLookup?): String {
        if (!link.isWeb) return activity.getString(R.string.verify_not_web, TextCleaner.line(link.scheme, 40))
        return when (lookup) {
            null, DomainLookup.NeedsNewerAndroid -> activity.getString(R.string.verify_needs_api)
            DomainLookup.NoDomains -> activity.getString(R.string.verify_no_domains)
            is DomainLookup.Refused -> activity.getString(R.string.verify_failed, lookup.reason)
            is DomainLookup.Available -> {
                val hosts = lookup.hosts.entries.sortedBy { it.key }.take(MAX_HOSTS_SHOWN)
                    .joinToString("\n") { "${TextCleaner.line(it.key, 120)}: ${it.value.label}" }
                    .ifEmpty { activity.getString(R.string.verify_none_listed) }
                activity.getString(
                    R.string.verify_summary,
                    DeepLinks.verdictFor(link.host, lookup.hosts).label,
                    activity.getString(if (lookup.linkHandlingAllowed) R.string.yes else R.string.no),
                    hosts,
                )
            }
        }
    }

    private companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val CATEGORY_BROWSABLE = "android.intent.category.BROWSABLE"
        const val MAX_HOSTS_SHOWN = 40
    }
}

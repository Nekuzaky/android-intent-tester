package dev.nekuz.intentlab

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button

class MainActivity : Activity() {

    private enum class Tab { BUILDER, DEEP_LINK, HISTORY }

    private lateinit var tabBuilder: Button
    private lateinit var tabDeepLink: Button
    private lateinit var tabHistory: Button
    private lateinit var panelBuilder: View
    private lateinit var panelDeepLink: View
    private lateinit var panelHistory: View
    private lateinit var builder: BuilderPanel
    private lateinit var deepLink: DeepLinkPanel
    private lateinit var historyPanel: HistoryPanel
    private lateinit var store: HistoryStore
    private var tab = Tab.BUILDER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = HistoryStore.get(this)
        tabBuilder = findViewById(R.id.tabBuilder)
        tabDeepLink = findViewById(R.id.tabDeepLink)
        tabHistory = findViewById(R.id.tabHistory)
        panelBuilder = findViewById(R.id.panelBuilder)
        panelDeepLink = findViewById(R.id.panelDeepLink)
        panelHistory = findViewById(R.id.panelHistory)
        builder = BuilderPanel(this, panelBuilder, store)
        deepLink = DeepLinkPanel(this, panelDeepLink)
        historyPanel = HistoryPanel(this, panelHistory, store)
        tabBuilder.setOnClickListener { selectTab(Tab.BUILDER) }
        tabDeepLink.setOnClickListener { selectTab(Tab.DEEP_LINK) }
        tabHistory.setOnClickListener { selectTab(Tab.HISTORY) }
        findViewById<View>(R.id.infoButton).setOnClickListener { showInfo() }
        if (savedInstanceState != null) {
            builder.restore(savedInstanceState)
            tab = savedInstanceState.getString(STATE_TAB)?.let { name ->
                Tab.entries.firstOrNull { it.name == name }
            } ?: Tab.BUILDER
        }
        selectTab(tab)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TAB, tab.name)
        builder.save(outState)
    }

    override fun onStart() {
        super.onStart()
        historyPanel.start()
    }

    override fun onStop() {
        super.onStop()
        historyPanel.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        deepLink.destroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_SEND -> showHostileResult(resultCode, data)
            REQUEST_RESOLVE -> if (resultCode == RESULT_OK && data != null) {
                val packageName = data.getStringExtra(ResolveActivity.RESULT_PACKAGE).orEmpty()
                val className = data.getStringExtra(ResolveActivity.RESULT_CLASS).orEmpty()
                builder.setTarget(packageName, className)
            }
        }
    }

    fun requestSend(spec: IntentSpec, onOutcome: (SendOutcome) -> Unit) {
        val review = UriGrants.review(spec)
        if (review == null) {
            send(spec, onOutcome)
            return
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.review_title)
            .setMessage(UriGrants.describe(review))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.review_send) { _, _ -> send(spec, onOutcome) }
            .show()
        protect(dialog)
    }

    fun protect(dialog: AlertDialog) {
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.filterTouchesWhenObscured = true
    }

    fun openResolve(spec: IntentSpec) {
        try {
            val preview = spec.copy(extras = emptyList())
            startActivityForResult(
                Intent(this, ResolveActivity::class.java)
                    .putExtra(ResolveActivity.EXTRA_SPEC, HistoryCodec.encodeSpec(preview)),
                REQUEST_RESOLVE,
            )
        } catch (error: Throwable) {
            builder.showFailure(FailureText.describe(error))
        }
    }

    fun editInBuilder(spec: IntentSpec) {
        builder.load(spec)
        selectTab(Tab.BUILDER)
        builder.scrollToTop()
    }

    private fun send(spec: IntentSpec, onOutcome: (SendOutcome) -> Unit) {
        val outcome = Sender.send(this, spec, REQUEST_SEND)
        val label = getString(if (outcome is SendOutcome.Sent) R.string.outcome_sent else R.string.outcome_failed)
        val kept = store.addRecent(spec, label)
        if (kept) {
            onOutcome(outcome)
            return
        }
        val note = getString(R.string.not_kept_in_history)
        onOutcome(
            when (outcome) {
                is SendOutcome.Sent -> SendOutcome.Sent("${outcome.message}\n$note")
                is SendOutcome.Failed -> SendOutcome.Failed("${outcome.message}\n$note")
            }
        )
    }

    private fun showHostileResult(resultCode: Int, data: Intent?) {
        Background.query.execute {
            val text = try {
                ResultReader.read(resultCode, data)
            } catch (error: Throwable) {
                "Result could not be read (${ResultFormatter.errorName(error)})."
            }
            Background.onMain {
                if (!isDestroyed) builder.showResult(text)
            }
        }
    }

    private fun selectTab(selected: Tab) {
        tab = selected
        panelBuilder.visibility = if (selected == Tab.BUILDER) View.VISIBLE else View.GONE
        panelDeepLink.visibility = if (selected == Tab.DEEP_LINK) View.VISIBLE else View.GONE
        panelHistory.visibility = if (selected == Tab.HISTORY) View.VISIBLE else View.GONE
        styleTab(tabBuilder, selected == Tab.BUILDER)
        styleTab(tabDeepLink, selected == Tab.DEEP_LINK)
        styleTab(tabHistory, selected == Tab.HISTORY)
    }

    private fun styleTab(button: Button, selected: Boolean) {
        button.setBackgroundResource(if (selected) R.drawable.bg_button_primary else R.drawable.bg_button_outline)
        button.setTextColor(getColor(if (selected) R.color.on_brand else R.color.text_primary))
    }

    private fun showInfo() {
        AlertDialog.Builder(this)
            .setTitle(R.string.info)
            .setMessage(R.string.info_message)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private companion object {
        const val STATE_TAB = "tab"
        const val REQUEST_SEND = 1
        const val REQUEST_RESOLVE = 2
    }
}

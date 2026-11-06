package dev.nekuz.intentlab

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class BuilderPanel(
    private val activity: MainActivity,
    private val root: View,
    private val store: HistoryStore,
) {
    private class ExtraRow(val view: View, val key: EditText, val type: Spinner, val value: EditText)

    private val inflater = LayoutInflater.from(activity)
    private val scroll = root as ScrollView
    private val actionInput: AutoCompleteTextView = root.findViewById(R.id.actionInput)
    private val dataInput: EditText = root.findViewById(R.id.dataInput)
    private val mimeInput: AutoCompleteTextView = root.findViewById(R.id.mimeInput)
    private val otherCategoriesInput: EditText = root.findViewById(R.id.otherCategoriesInput)
    private val packageInput: EditText = root.findViewById(R.id.packageInput)
    private val classInput: EditText = root.findViewById(R.id.classInput)
    private val categoriesToggle: Button = root.findViewById(R.id.categoriesToggle)
    private val categoriesBox: View = root.findViewById(R.id.categoriesBox)
    private val flagsToggle: Button = root.findViewById(R.id.flagsToggle)
    private val flagsBox: View = root.findViewById(R.id.flagsBox)
    private val extrasList: LinearLayout = root.findViewById(R.id.extrasList)
    private val modeSpinner: Spinner = root.findViewById(R.id.modeSpinner)
    private val chooserCheck: CheckBox = root.findViewById(R.id.chooserCheck)
    private val warningsText: TextView = root.findViewById(R.id.warningsText)
    private val resultCard: View = root.findViewById(R.id.resultCard)
    private val resultText: TextView = root.findViewById(R.id.resultText)
    private val adbCommandText: TextView = root.findViewById(R.id.adbCommandText)
    private val deviceCommandText: TextView = root.findViewById(R.id.deviceCommandText)
    private val commandWarningsText: TextView = root.findViewById(R.id.commandWarningsText)
    private val copyAdbButton: Button = root.findViewById(R.id.copyAdbButton)
    private val copyDeviceButton: Button = root.findViewById(R.id.copyDeviceButton)
    private val categoryChecks = LinkedHashMap<String, CheckBox>()
    private val flagChecks = LinkedHashMap<String, CheckBox>()
    private val extraRows = ArrayList<ExtraRow>()
    private var loading = false
    private var adbCommand = ""
    private var deviceCommand = ""

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = refresh()
    }

    init {
        root.findViewById<View>(R.id.heroCard).clipToOutline = true
        setupSuggestions(actionInput, IntentCatalog.actions)
        setupSuggestions(mimeInput, IntentCatalog.mimeTypes)
        listOf(actionInput, dataInput, mimeInput, otherCategoriesInput, packageInput, classInput)
            .forEach { it.addTextChangedListener(watcher) }
        setupCategories()
        setupFlags()
        setupMode()
        categoriesToggle.setOnClickListener { toggle(categoriesBox) }
        flagsToggle.setOnClickListener { toggle(flagsBox) }
        root.findViewById<View>(R.id.addExtraButton).setOnClickListener {
            addExtraRow(ExtraSpec("", ExtraType.STRING, ""))
            refresh()
        }
        root.findViewById<View>(R.id.sendButton).setOnClickListener { onSend() }
        root.findViewById<View>(R.id.previewButton).setOnClickListener { onPreview() }
        root.findViewById<View>(R.id.saveFavouriteButton).setOnClickListener { onSaveFavourite() }
        root.findViewById<View>(R.id.clearFormButton).setOnClickListener {
            load(IntentSpec())
            resultCard.visibility = View.GONE
        }
        copyAdbButton.setOnClickListener { copy(adbCommand) }
        copyDeviceButton.setOnClickListener { copy(deviceCommand) }
        chooserCheck.setOnCheckedChangeListener { _, _ -> refresh() }
        refresh()
    }

    fun load(spec: IntentSpec) {
        loading = true
        actionInput.setText(spec.action, false)
        dataInput.setText(spec.data)
        mimeInput.setText(spec.mimeType, false)
        packageInput.setText(spec.packageName)
        classInput.setText(spec.className)
        categoryChecks.forEach { (name, check) -> check.isChecked = name in spec.categories }
        otherCategoriesInput.setText(spec.categories.filter { it !in categoryChecks }.joinToString(", "))
        flagChecks.forEach { (name, check) -> check.isChecked = name in spec.flags }
        extraRows.forEach { extrasList.removeView(it.view) }
        extraRows.clear()
        spec.extras.forEach { addExtraRow(it) }
        modeSpinner.setSelection(spec.mode.ordinal, false)
        chooserCheck.isChecked = spec.chooser
        loading = false
        refresh()
    }

    fun setTarget(packageName: String, className: String) {
        loading = true
        packageInput.setText(packageName)
        classInput.setText(className)
        loading = false
        refresh()
    }

    fun scrollToTop() {
        scroll.scrollTo(0, 0)
    }

    fun save(state: Bundle) {
        val encoded = HistoryCodec.encodeSpec(currentSpec())
        if (encoded.length <= MAX_SAVED_CHARS) state.putString(STATE_SPEC, encoded)
        if (resultCard.visibility == View.VISIBLE) {
            state.putString(STATE_RESULT, resultText.text.toString())
        }
    }

    fun restore(state: Bundle) {
        state.getString(STATE_SPEC)?.let(HistoryCodec::decodeSpec)?.let { load(it) }
        state.getString(STATE_RESULT)?.let { showResult(it) }
    }

    fun showResult(text: String) {
        resultCard.visibility = View.VISIBLE
        resultText.setTextColor(activity.getColor(R.color.text_primary))
        resultText.text = text
    }

    fun showFailure(text: String) {
        resultCard.visibility = View.VISIBLE
        resultText.setTextColor(activity.getColor(R.color.danger))
        resultText.text = text
        scroll.post { scroll.smoothScrollTo(0, resultCard.top) }
    }

    private fun setupSuggestions(view: AutoCompleteTextView, items: List<String>) {
        val adapter = SuggestionAdapter(activity, items)
        view.setAdapter(adapter)
        view.threshold = 1
        val showAll = {
            if (view.text.isEmpty()) {
                adapter.filter.filter("") {
                    if (view.hasFocus() && view.text.isEmpty()) {
                        runCatching { view.showDropDown() }
                    }
                }
            }
        }
        view.setOnClickListener { showAll() }
        view.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showAll() }
    }

    private fun setupCategories() {
        val list = root.findViewById<LinearLayout>(R.id.categoriesList)
        IntentCatalog.categories.forEach { name ->
            val check = inflater.inflate(R.layout.row_check, list, false) as CheckBox
            check.text = name.removePrefix("android.intent.category.")
            check.setOnCheckedChangeListener { _, _ -> refresh() }
            categoryChecks[name] = check
            list.addView(check)
        }
    }

    private fun setupFlags() {
        val list = root.findViewById<LinearLayout>(R.id.flagsList)
        FlagGroup.entries.forEach { group ->
            val header = inflater.inflate(R.layout.row_header, list, false) as TextView
            header.setText(
                when (group) {
                    FlagGroup.URI_GRANT -> R.string.group_uri_grant
                    FlagGroup.GENERAL -> R.string.group_general
                    FlagGroup.ACTIVITY -> R.string.group_activity
                    FlagGroup.BROADCAST -> R.string.group_broadcast
                }
            )
            list.addView(header)
            IntentFlags.all.filter { it.group == group }.forEach { flag ->
                val check = inflater.inflate(R.layout.row_check, list, false) as CheckBox
                check.text = "${flag.name}\n${IntentFlags.hex(flag.value)}"
                if (group == FlagGroup.URI_GRANT) check.setTextColor(activity.getColor(R.color.amber))
                check.setOnCheckedChangeListener { _, _ -> refresh() }
                flagChecks[flag.name] = check
                list.addView(check)
            }
        }
    }

    private fun setupMode() {
        modeSpinner.adapter = textAdapter(SendMode.entries.map { it.label })
        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = refresh()
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun textAdapter(items: List<String>): ArrayAdapter<String> =
        ArrayAdapter(activity, R.layout.item_text, items)

    private fun toggle(view: View) {
        view.visibility = if (view.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun addExtraRow(extra: ExtraSpec) {
        val view = inflater.inflate(R.layout.row_extra, extrasList, false)
        val row = ExtraRow(view, view.findViewById(R.id.extraKey), view.findViewById(R.id.extraType), view.findViewById(R.id.extraValue))
        row.type.adapter = textAdapter(ExtraType.entries.map { it.label })
        row.type.setSelection(extra.type.ordinal, false)
        row.key.setText(extra.key)
        row.value.setText(extra.value)
        row.value.hint = activity.getString(hintFor(extra.type))
        row.key.addTextChangedListener(watcher)
        row.value.addTextChangedListener(watcher)
        row.type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                row.value.hint = activity.getString(hintFor(ExtraType.entries[position]))
                refresh()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        view.findViewById<View>(R.id.extraRemove).setOnClickListener {
            extrasList.removeView(view)
            extraRows.remove(row)
            refresh()
        }
        extraRows.add(row)
        extrasList.addView(view)
    }

    private fun hintFor(type: ExtraType): Int = when (type) {
        ExtraType.STRING -> R.string.extra_hint_string
        ExtraType.INT, ExtraType.LONG -> R.string.extra_hint_integer
        ExtraType.FLOAT, ExtraType.DOUBLE -> R.string.extra_hint_decimal
        ExtraType.BOOLEAN -> R.string.extra_hint_boolean
        ExtraType.STRING_ARRAY -> R.string.extra_hint_array
    }

    private fun currentSpec(): IntentSpec {
        val checked = categoryChecks.filterValues { it.isChecked }.keys.toList()
        val other = otherCategoriesInput.text.toString()
            .split(',', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it !in checked }
        return IntentSpec(
            action = actionInput.text.toString().trim(),
            data = dataInput.text.toString().trim(),
            mimeType = mimeInput.text.toString().trim(),
            categories = checked + other,
            packageName = packageInput.text.toString().trim(),
            className = classInput.text.toString().trim(),
            flags = flagChecks.filterValues { it.isChecked }.keys.toList(),
            extras = extraRows.map {
                ExtraSpec(it.key.text.toString(), ExtraType.entries[it.type.selectedItemPosition], it.value.text.toString())
            },
            mode = SendMode.entries[modeSpinner.selectedItemPosition.coerceAtLeast(0)],
            chooser = chooserCheck.isChecked && chooserCheck.isEnabled,
        )
    }

    private fun refresh() {
        if (loading) return
        val mode = SendMode.entries[modeSpinner.selectedItemPosition.coerceAtLeast(0)]
        chooserCheck.isEnabled = mode.usesActivity
        val spec = currentSpec()
        val selectedCategories = spec.categories.size
        categoriesToggle.text = activity.getString(R.string.toggle_categories, selectedCategories)
        flagsToggle.text = activity.getString(R.string.toggle_flags, spec.flags.size, IntentFlags.hex(spec.flagsValue))
        val validation = SpecValidator.validate(spec)
        val problems = validation.errors + validation.warnings
        warningsText.visibility = if (problems.isEmpty()) View.GONE else View.VISIBLE
        warningsText.setTextColor(activity.getColor(if (validation.isValid) R.color.amber else R.color.danger))
        warningsText.text = problems.joinToString("\n") { "• $it" }
        renderCommand(spec, validation)
    }

    private fun renderCommand(spec: IntentSpec, validation: Validation) {
        if (!validation.isValid) {
            adbCommand = ""
            deviceCommand = ""
            adbCommandText.setText(R.string.command_fix_errors)
            deviceCommandText.text = ""
            commandWarningsText.visibility = View.GONE
            copyAdbButton.isEnabled = false
            copyDeviceButton.isEnabled = false
            return
        }
        val command = CommandGenerator.generate(spec)
        adbCommand = command.adbCommand
        deviceCommand = command.deviceCommand
        adbCommandText.text = TextCleaner.block(command.adbCommand, MAX_COMMAND_SHOWN)
        deviceCommandText.text = TextCleaner.block(command.deviceCommand, MAX_COMMAND_SHOWN)
        commandWarningsText.visibility = if (command.warnings.isEmpty()) View.GONE else View.VISIBLE
        commandWarningsText.text = command.warnings.distinct().joinToString("\n") { "• $it" }
        copyAdbButton.isEnabled = true
        copyDeviceButton.isEnabled = true
    }

    private fun onSend() {
        val spec = currentSpec()
        val validation = SpecValidator.validate(spec)
        if (!validation.isValid) {
            showFailure(validation.errors.joinToString("\n") { "• $it" })
            return
        }
        activity.requestSend(spec) { outcome ->
            when (outcome) {
                is SendOutcome.Sent -> {
                    showResult(outcome.message)
                    resultCard.post { scroll.smoothScrollTo(0, resultCard.top) }
                }
                is SendOutcome.Failed -> showFailure(outcome.message)
            }
        }
    }

    private fun onPreview() {
        val spec = currentSpec()
        val validation = SpecValidator.validate(spec.copy(extras = emptyList()))
        if (!validation.isValid) {
            showFailure(validation.errors.joinToString("\n") { "• $it" })
            return
        }
        activity.openResolve(spec)
    }

    private fun onSaveFavourite() {
        val spec = currentSpec()
        val input = inflater.inflate(R.layout.dialog_name, null) as EditText
        AlertDialog.Builder(activity)
            .setTitle(R.string.favourite_title)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val fallback = activity.getString(R.string.favourite_default)
                val name = TextCleaner.label(input.text, fallback)
                if (!store.addFavourite(name, spec)) {
                    Toast.makeText(activity, R.string.not_kept_in_history, Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun copy(text: String) {
        if (text.isEmpty()) return
        val copied = try {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.section_command), text))
            true
        } catch (error: Throwable) {
            false
        }
        Toast.makeText(activity, if (copied) R.string.copied else R.string.copy_failed, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val STATE_SPEC = "builder_spec"
        const val STATE_RESULT = "builder_result"
        const val MAX_SAVED_CHARS = 100 * 1024
        const val MAX_COMMAND_SHOWN = 6000
    }
}

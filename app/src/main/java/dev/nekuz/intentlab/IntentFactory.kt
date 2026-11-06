package dev.nekuz.intentlab

import android.content.ComponentName
import android.content.Intent
import android.net.Uri

object IntentFactory {

    fun build(spec: IntentSpec): Intent {
        val intent = Intent()
        if (spec.action.isNotEmpty()) intent.action = spec.action
        val uri = if (spec.data.isNotEmpty()) Uri.parse(spec.data) else null
        val type = spec.mimeType.ifEmpty { null }
        intent.setDataAndType(uri, type)
        spec.categories.forEach { intent.addCategory(it) }
        if (spec.className.isNotEmpty()) {
            intent.component = ComponentName(spec.packageName, spec.fullClassName)
        } else if (spec.packageName.isNotEmpty()) {
            intent.setPackage(spec.packageName)
        }
        intent.flags = spec.flagsValue
        spec.extras.forEach { addExtra(intent, it) }
        return intent
    }

    private fun addExtra(intent: Intent, extra: ExtraSpec) {
        val parsed = ExtrasParser.parse(extra)
        if (parsed !is ParsedExtra.Ok) throw IllegalArgumentException("Invalid extra")
        when (val value = parsed.value) {
            is ParsedValue.Text -> intent.putExtra(parsed.key, value.value)
            is ParsedValue.IntValue -> intent.putExtra(parsed.key, value.value)
            is ParsedValue.LongValue -> intent.putExtra(parsed.key, value.value)
            is ParsedValue.BooleanValue -> intent.putExtra(parsed.key, value.value)
            is ParsedValue.FloatValue -> intent.putExtra(parsed.key, value.value)
            is ParsedValue.DoubleValue -> intent.putExtra(parsed.key, value.value)
            is ParsedValue.TextArray -> intent.putExtra(parsed.key, value.value.toTypedArray())
        }
    }
}

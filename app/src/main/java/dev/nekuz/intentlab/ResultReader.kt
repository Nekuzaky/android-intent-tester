package dev.nekuz.intentlab

import android.content.Intent
import android.os.Bundle

object ResultReader {

    fun read(resultCode: Int, data: Intent?): String {
        val text = StringBuilder()
        text.append("Result code: ").append(ResultFormatter.resultCodeName(resultCode))
        if (data == null) {
            text.append("\nNo result data.")
            return text.toString()
        }
        appendField(text, "Action") { data.action }
        appendField(text, "Data") { data.dataString }
        appendField(text, "Type") { data.type }
        appendField(text, "Package") { data.`package` }
        appendField(text, "Component") { data.component?.flattenToShortString() }
        safely { data.flags }?.takeIf { it != 0 }?.let {
            text.append("\nFlags: ").append(IntentFlags.hex(it))
        }
        safely { data.clipData?.itemCount }?.let { text.append("\nClip items: ").append(it) }
        appendExtras(text, data)
        return text.toString()
    }

    private fun appendExtras(text: StringBuilder, data: Intent) {
        val extras = try {
            data.extras
        } catch (error: Throwable) {
            text.append("\nExtras could not be read (").append(ResultFormatter.errorName(error)).append(").")
            return
        }
        if (extras == null) {
            text.append("\nExtras: none")
            return
        }
        val entries = try {
            readBundle(extras)
        } catch (error: Throwable) {
            text.append("\nExtras could not be read (").append(ResultFormatter.errorName(error)).append(").")
            return
        }
        if (entries.isEmpty()) {
            text.append("\nExtras: none")
            return
        }
        val total = safely { extras.keySet().size } ?: entries.size
        text.append("\nExtras:\n")
        text.append(ResultFormatter.formatEntries(entries, total, ::expand))
    }

    private fun expand(value: Any): Map<String, Any?>? =
        if (value is Bundle) readBundle(value) else null

    @Suppress("DEPRECATION")
    private fun readBundle(bundle: Bundle): Map<String, Any?> {
        val keys = bundle.keySet().toList().filterNotNull().sorted().take(ResultFormatter.MAX_KEYS)
        val entries = LinkedHashMap<String, Any?>()
        for (key in keys) {
            entries[key] = try {
                bundle.get(key)
            } catch (error: Throwable) {
                Unreadable(ResultFormatter.errorName(error))
            }
        }
        return entries
    }

    private fun appendField(text: StringBuilder, name: String, read: () -> String?) {
        val value = safely(read) ?: return
        text.append('\n').append(name).append(": ").append(TextCleaner.line(value, ResultFormatter.MAX_VALUE_CHARS))
    }

    private fun <T> safely(read: () -> T?): T? = try {
        read()
    } catch (error: Throwable) {
        null
    }
}

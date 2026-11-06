package dev.nekuz.intentlab

import android.app.Activity
import android.content.Intent

sealed class SendOutcome {
    abstract val message: String

    data class Sent(override val message: String) : SendOutcome()
    data class Failed(override val message: String) : SendOutcome()
}

object Sender {

    fun send(activity: Activity, spec: IntentSpec, requestCode: Int): SendOutcome = try {
        val intent = IntentFactory.build(spec)
        when (spec.mode) {
            SendMode.ACTIVITY -> {
                activity.startActivity(wrap(activity, intent, spec))
                SendOutcome.Sent(activity.getString(R.string.sent_activity))
            }
            SendMode.ACTIVITY_FOR_RESULT -> {
                activity.startActivityForResult(wrap(activity, intent, spec), requestCode)
                SendOutcome.Sent(activity.getString(R.string.sent_waiting))
            }
            SendMode.BROADCAST -> {
                activity.sendBroadcast(intent)
                SendOutcome.Sent(activity.getString(R.string.sent_broadcast))
            }
            SendMode.SERVICE -> {
                val started = activity.startService(intent)
                if (started == null) {
                    SendOutcome.Failed(activity.getString(R.string.service_not_found))
                } else {
                    val name = TextCleaner.line(started.flattenToShortString())
                    SendOutcome.Sent(activity.getString(R.string.sent_service, name))
                }
            }
        }
    } catch (error: Throwable) {
        SendOutcome.Failed(FailureText.describe(error))
    }

    private fun wrap(activity: Activity, intent: Intent, spec: IntentSpec): Intent =
        if (spec.chooser) Intent.createChooser(intent, activity.getString(R.string.chooser_title)) else intent
}

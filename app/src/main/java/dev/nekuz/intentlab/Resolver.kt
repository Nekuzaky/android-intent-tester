package dev.nekuz.intentlab

import android.content.Context
import android.content.Intent
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

enum class HandlerKind { ACTIVITY, SERVICE, RECEIVER }

data class ResolvedHandler(
    val kind: HandlerKind,
    val packageName: String,
    val className: String,
    val appLabel: String,
    val exported: Boolean,
    val enabled: Boolean,
    val permission: String?,
    val isDefault: Boolean,
) {
    val shownComponent: String
        get() = TextCleaner.line("$packageName/$className")
}

data class Resolution(val handlers: List<ResolvedHandler>, val failure: String?)

object Resolver {
    private const val MAX_HANDLERS = 200

    fun query(context: Context, intent: Intent, kind: HandlerKind): Resolution = try {
        val manager = context.packageManager
        val infos = when (kind) {
            HandlerKind.ACTIVITY -> manager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            HandlerKind.SERVICE -> manager.queryIntentServices(intent, PackageManager.MATCH_ALL)
            HandlerKind.RECEIVER -> manager.queryBroadcastReceivers(intent, PackageManager.MATCH_ALL)
        }
        val defaultComponent = if (kind == HandlerKind.ACTIVITY) defaultOf(manager, intent) else null
        val handlers = infos.take(MAX_HANDLERS).mapNotNull { toHandler(manager, it, kind, defaultComponent) }
        Resolution(handlers, null)
    } catch (error: Throwable) {
        Resolution(emptyList(), FailureText.describe(error))
    }

    private fun defaultOf(manager: PackageManager, intent: Intent): Pair<String, String>? = try {
        manager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.let {
            it.packageName to it.name
        }
    } catch (error: Throwable) {
        null
    }

    private fun toHandler(
        manager: PackageManager,
        info: ResolveInfo,
        kind: HandlerKind,
        defaultComponent: Pair<String, String>?,
    ): ResolvedHandler? = try {
        val component: ComponentInfo? = when (kind) {
            HandlerKind.ACTIVITY -> info.activityInfo
            HandlerKind.SERVICE -> info.serviceInfo
            HandlerKind.RECEIVER -> info.activityInfo
        }
        val permission = when (kind) {
            HandlerKind.ACTIVITY, HandlerKind.RECEIVER -> info.activityInfo?.permission
            HandlerKind.SERVICE -> info.serviceInfo?.permission
        }
        component?.let {
            ResolvedHandler(
                kind = kind,
                packageName = it.packageName,
                className = it.name,
                appLabel = appLabel(manager, it),
                exported = it.exported,
                enabled = it.isEnabled,
                permission = permission,
                isDefault = defaultComponent == (it.packageName to it.name),
            )
        }
    } catch (error: Throwable) {
        null
    }

    private fun appLabel(manager: PackageManager, component: ComponentInfo): String {
        val fallback = TextCleaner.line(component.packageName, TextCleaner.MAX_LABEL)
        return try {
            TextCleaner.label(component.applicationInfo?.loadLabel(manager), fallback)
        } catch (error: Throwable) {
            fallback
        }
    }
}

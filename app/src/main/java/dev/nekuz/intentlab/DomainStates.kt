package dev.nekuz.intentlab

import android.content.Context
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build

sealed class DomainLookup {
    data class Available(val linkHandlingAllowed: Boolean, val hosts: Map<String, DomainVerdict>) : DomainLookup()
    data object NoDomains : DomainLookup()
    data object NeedsNewerAndroid : DomainLookup()
    data class Refused(val reason: String) : DomainLookup()
}

object DomainStates {
    private const val MAX_HOSTS = 500

    fun lookup(context: Context, packageName: String): DomainLookup {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return DomainLookup.NeedsNewerAndroid
        return try {
            val manager = context.getSystemService(DomainVerificationManager::class.java)
                ?: return DomainLookup.Refused("no service")
            val state = manager.getDomainVerificationUserState(packageName)
                ?: return DomainLookup.NoDomains
            val hosts = state.hostToStateMap.entries.take(MAX_HOSTS).associate { (host, value) ->
                host to when (value) {
                    DomainVerificationUserState.DOMAIN_STATE_VERIFIED -> DomainVerdict.VERIFIED
                    DomainVerificationUserState.DOMAIN_STATE_SELECTED -> DomainVerdict.SELECTED
                    else -> DomainVerdict.NONE
                }
            }
            DomainLookup.Available(state.isLinkHandlingAllowed, hosts)
        } catch (error: Throwable) {
            DomainLookup.Refused(ResultFormatter.errorName(error))
        }
    }
}

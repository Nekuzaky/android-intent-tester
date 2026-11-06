package dev.nekuz.intentlab

enum class FlagGroup { URI_GRANT, GENERAL, ACTIVITY, BROADCAST }

data class IntentFlag(val name: String, val value: Int, val group: FlagGroup)

object IntentFlags {
    val all: List<IntentFlag> = listOf(
        IntentFlag("FLAG_GRANT_READ_URI_PERMISSION", 0x00000001, FlagGroup.URI_GRANT),
        IntentFlag("FLAG_GRANT_WRITE_URI_PERMISSION", 0x00000002, FlagGroup.URI_GRANT),
        IntentFlag("FLAG_GRANT_PERSISTABLE_URI_PERMISSION", 0x00000040, FlagGroup.URI_GRANT),
        IntentFlag("FLAG_GRANT_PREFIX_URI_PERMISSION", 0x00000080, FlagGroup.URI_GRANT),
        IntentFlag("FLAG_EXCLUDE_STOPPED_PACKAGES", 0x00000010, FlagGroup.GENERAL),
        IntentFlag("FLAG_INCLUDE_STOPPED_PACKAGES", 0x00000020, FlagGroup.GENERAL),
        IntentFlag("FLAG_ACTIVITY_NEW_TASK", 0x10000000, FlagGroup.ACTIVITY),
        IntentFlag("FLAG_ACTIVITY_CLEAR_TOP", 0x04000000, FlagGroup.ACTIVITY),
        IntentFlag("FLAG_ACTIVITY_CLEAR_TASK", 0x00008000, FlagGroup.ACTIVITY),
        IntentFlag("FLAG_ACTIVITY_SINGLE_TOP", 0x20000000, FlagGroup.ACTIVITY),
        IntentFlag("FLAG_ACTIVITY_NO_HISTORY", 0x40000000, FlagGroup.ACTIVITY),
        IntentFlag("FLAG_ACTIVITY_MULTIPLE_TASK", 0x08000000, FlagGroup.ACTIVITY),
        IntentFlag("FLAG_ACTIVITY_NEW_DOCUMENT", 0x00080000, FlagGroup.ACTIVITY),
        IntentFlag("FLAG_ACTIVITY_REORDER_TO_FRONT", 0x00020000, FlagGroup.ACTIVITY),
        IntentFlag("FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS", 0x00800000, FlagGroup.ACTIVITY),
        IntentFlag("FLAG_ACTIVITY_NO_ANIMATION", 0x00010000, FlagGroup.ACTIVITY),
        IntentFlag("FLAG_ACTIVITY_NO_USER_ACTION", 0x00040000, FlagGroup.ACTIVITY),
        IntentFlag("FLAG_ACTIVITY_FORWARD_RESULT", 0x02000000, FlagGroup.ACTIVITY),
        IntentFlag("FLAG_ACTIVITY_PREVIOUS_IS_TOP", 0x01000000, FlagGroup.ACTIVITY),
        IntentFlag("FLAG_ACTIVITY_RESET_TASK_IF_NEEDED", 0x00200000, FlagGroup.ACTIVITY),
        IntentFlag("FLAG_ACTIVITY_TASK_ON_HOME", 0x00004000, FlagGroup.ACTIVITY),
        IntentFlag("FLAG_ACTIVITY_REQUIRE_NON_BROWSER", 0x00000400, FlagGroup.ACTIVITY),
        IntentFlag("FLAG_ACTIVITY_REQUIRE_DEFAULT", 0x00000200, FlagGroup.ACTIVITY),
        IntentFlag("FLAG_RECEIVER_FOREGROUND", 0x10000000, FlagGroup.BROADCAST),
        IntentFlag("FLAG_RECEIVER_REGISTERED_ONLY", 0x40000000, FlagGroup.BROADCAST),
        IntentFlag("FLAG_RECEIVER_REPLACE_PENDING", 0x20000000, FlagGroup.BROADCAST),
        IntentFlag("FLAG_RECEIVER_NO_ABORT", 0x08000000, FlagGroup.BROADCAST),
    )

    private val byName = all.associateBy { it.name }

    fun isKnown(name: String): Boolean = name in byName

    fun isGrant(name: String): Boolean = byName[name]?.group == FlagGroup.URI_GRANT

    fun combine(names: Collection<String>): Int =
        names.fold(0) { value, name -> value or (byName[name]?.value ?: 0) }

    fun hex(value: Int): String = "0x%08x".format(value)
}

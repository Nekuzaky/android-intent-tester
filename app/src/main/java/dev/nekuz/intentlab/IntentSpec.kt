package dev.nekuz.intentlab

enum class ExtraType(val label: String, val amOption: String) {
    STRING("String", "--es"),
    INT("Int", "--ei"),
    LONG("Long", "--el"),
    BOOLEAN("Boolean", "--ez"),
    FLOAT("Float", "--ef"),
    DOUBLE("Double", "--ed"),
    STRING_ARRAY("String array", "--esa"),
}

enum class SendMode(val label: String, val shortLabel: String) {
    ACTIVITY("startActivity", "Activity"),
    ACTIVITY_FOR_RESULT("startActivityForResult", "For result"),
    BROADCAST("sendBroadcast", "Broadcast"),
    SERVICE("startService", "Service"),
    ;

    val usesActivity: Boolean get() = this == ACTIVITY || this == ACTIVITY_FOR_RESULT
}

data class ExtraSpec(val key: String, val type: ExtraType, val value: String)

data class IntentSpec(
    val action: String = "",
    val data: String = "",
    val mimeType: String = "",
    val categories: List<String> = emptyList(),
    val packageName: String = "",
    val className: String = "",
    val flags: List<String> = emptyList(),
    val extras: List<ExtraSpec> = emptyList(),
    val mode: SendMode = SendMode.ACTIVITY,
    val chooser: Boolean = false,
) {
    val fullClassName: String
        get() = if (className.startsWith(".")) packageName + className else className

    val flagsValue: Int
        get() = IntentFlags.combine(flags)
}

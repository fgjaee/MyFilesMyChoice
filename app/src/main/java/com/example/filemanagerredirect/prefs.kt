package com.example.filemanagerredirect

/** Preference identifiers; unchanged legacy name preserves upgrade settings. */
object Prefs {
    const val PREFS_NAME = "fmr_prefs"
    const val PKG_AUTO = "auto"
    const val KEY_ENABLED = "enabled"
    const val KEY_GLOBAL_ENABLED = "global_enabled"
    const val KEY_PACKAGE = "primary_manager"
    const val KEY_PRIMARY_MANAGER = KEY_PACKAGE
    const val KEY_STEALTH = "stealth"
    const val KEY_FALLBACK_SAF = "fallback_saf"
    const val KEY_FALLBACK_MANAGERS = "fallback_managers"
    const val KEY_RESOLVED_TARGET = "resolved_target"
    fun normalizeSelected(value: String?): String =
        if (value.isNullOrBlank()) PKG_AUTO else value
}

/** Backward-compatibility aliases for old classes. */
object Keys {
    const val PREFS = Prefs.PREFS_NAME
    const val KEY_ENABLED = Prefs.KEY_ENABLED
    const val KEY_GLOBAL_ENABLED = Prefs.KEY_GLOBAL_ENABLED
    const val KEY_PACKAGE = Prefs.KEY_PACKAGE
    const val KEY_PRIMARY_MANAGER = Prefs.KEY_PRIMARY_MANAGER
    const val KEY_STEALTH = Prefs.KEY_STEALTH
    const val KEY_FALLBACK_SAF = Prefs.KEY_FALLBACK_SAF
    const val KEY_FALLBACK_MANAGERS = Prefs.KEY_FALLBACK_MANAGERS
    const val KEY_RESOLVED_TARGET = Prefs.KEY_RESOLVED_TARGET
}

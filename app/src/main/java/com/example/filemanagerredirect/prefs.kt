package com.example.filemanagerredirect

/**
 * Central place for SharedPreferences keys used by the app.
 * Includes a small "Keys" shim so older references still compile.
 */
object Prefs {
    // Prefs filename
    const val PREFS_NAME = "fmr_prefs"

    // Values
    /** "Auto" means: decide at runtime based on installed file managers. */
    const val PKG_AUTO = ""

    // Keys
    const val KEY_ENABLED = "enabled"
    const val KEY_PACKAGE = "primary_manager"        // single, primary choice
    const val KEY_PRIMARY_MANAGER = KEY_PACKAGE      // alias (keeps older code compiling)
    const val KEY_STEALTH = "stealth"
    const val KEY_FALLBACK_SAF = "fallback_saf"      // allow falling back to system picker
    const val KEY_FALLBACK_MANAGERS = "fallback_managers" // optional multiselect of alternates
}

/**
 * Back-compat shim so code that used "Keys.*" still compiles.
 * Maps directly to Prefs.* constants.
 */
object Keys {
    const val PREFS = Prefs.PREFS_NAME
    const val KEY_ENABLED = Prefs.KEY_ENABLED
    const val KEY_PACKAGE = Prefs.KEY_PACKAGE
    const val KEY_STEALTH = Prefs.KEY_STEALTH
    const val KEY_FALLBACK_SAF = Prefs.KEY_FALLBACK_SAF
}
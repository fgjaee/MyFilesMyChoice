package com.example.filemanagerredirect

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat

/**
 * Keys must match what your hook class reads.
 */
object Prefs {
    const val PREFS_NAME = "file_redirect_prefs"

    const val KEY_ENABLED = "enabled"
    const val KEY_SCOPE_SYSTEM_FRAMEWORK = "scope_system_framework"  // hook only system server/framework
    const val KEY_PRIMARY_MANAGER = "primary_pkg"
    const val KEY_FALLBACK_MANAGERS = "fallback_pkgs"
    const val KEY_LAST_TEST_ACTION = "last_test_action"              // UX only
}

/**
 * Activity shells a single PreferenceFragment; no layout XML required.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Ensure Material3 DayNight style from your app theme is applied
        super.onCreate(savedInstanceState)

        // Show the fragment directly in the content view (no layout file needed)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
        }
    }

    /**
     * Preference fragment that builds the screen entirely in code.
     */
    class SettingsFragment : PreferenceFragmentCompat() {

        private lateinit var sp: SharedPreferences

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // Use Device Protected Storage for early availability to hooks
            val appCtx = requireContext().applicationContext
            val deCtx = try {
                appCtx.createDeviceProtectedStorageContext()
            } catch (_: Throwable) {
                appCtx
            }

            // Migrate from default prefs if needed (first run)
            val def = PreferenceManager.getDefaultSharedPreferences(appCtx)
            sp = deCtx.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE)
            if (!sp.contains(Prefs.KEY_ENABLED)
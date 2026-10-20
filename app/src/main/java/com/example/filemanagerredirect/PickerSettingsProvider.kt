package com.example.filemanagerredirect

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * Read-only settings handshake for the DocumentsUI process.
 * No file data, root access, or mutating provider operations are exposed.
 */
class PickerSettingsProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != "read") return null
        val ctx = context ?: return null
        val prefs = ctx.getSharedPreferences(Prefs.PREFS_NAME, 0)
        return Bundle().apply {
            putBoolean("enabled", prefs.getBoolean(Prefs.KEY_ENABLED, true))
            putBoolean("global_enabled", prefs.getBoolean(Prefs.KEY_GLOBAL_ENABLED, false))
        }
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Read-only provider")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Read-only provider")
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ): Int = throw UnsupportedOperationException("Read-only provider")
}

package com.example.filemanagerredirect

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/**
 * An ACTION_GET_CONTENT bridge. Keep this Activity alive until the selected
 * manager returns a result, and forward the selected URI/grants to the caller.
 *
 * This is not an OPEN_DOCUMENT, OPEN_DOCUMENT_TREE or Photo Picker replacement.
 */
class RedirectActivity : AppCompatActivity() {
    private val tag = "MyFilesMyChoice"
    private val requestManager = 42
    private val requestSaf = 43
    private var launched = false
    private var usedSaf = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            launched = savedInstanceState.getBoolean("launched", false)
            usedSaf = savedInstanceState.getBoolean("usedSaf", false)
        }
        if (!launched) startPicker()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("launched", launched)
        outState.putBoolean("usedSaf", usedSaf)
        super.onSaveInstanceState(outState)
    }

    private fun startPicker() {
        launched = true
        val target = runCatching { FileManagerRedirectHook.resolveTargetPackage(this) }.getOrNull()
        if (!target.isNullOrBlank() && target != packageName &&
            !target.endsWith("documentsui", ignoreCase = true)) {
            val candidate = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = intent.type ?: "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                setPackage(target)
                intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.let {
                    putExtra(Intent.EXTRA_MIME_TYPES, it)
                }
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                    intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
            }
            try {
                startActivityForResult(candidate, requestManager)
                return
            } catch (e: ActivityNotFoundException) {
                Log.w(tag, "Manager does not support GET_CONTENT: $target", e)
            } catch (e: SecurityException) {
                Log.w(tag, "Manager denied request: $target", e)
            }
        }
        startSaf()
    }

    private fun startSaf() {
        val fallback = getSharedPreferences(Prefs.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(Prefs.KEY_FALLBACK_SAF, true)
        if (!fallback || usedSaf) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        usedSaf = true
        val system = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = intent.type ?: "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.let {
                putExtra(Intent.EXTRA_MIME_TYPES, it)
            }
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
        }
        try {
            startActivityForResult(system, requestSaf)
        } catch (e: Exception) {
            Log.e(tag, "No compatible system picker", e)
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    @Deprecated("Using the Activity result callback for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != requestManager && requestCode != requestSaf) return
        if (resultCode != Activity.RESULT_OK) {
            setResult(resultCode, data)
            finish()
            return
        }
        if (data == null) {
            if (requestCode == requestManager) startSaf()
            else { setResult(Activity.RESULT_CANCELED); finish() }
            return
        }
        val uris = mutableListOf<Uri>()
        data.data?.let(uris::add)
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).uri?.let(uris::add)
            }
        }
        if (uris.isEmpty() || uris.any { it.scheme != "content" }) {
            Log.w(tag, "Manager did not return grantable content URIs")
            if (requestCode == requestManager) startSaf()
            else { setResult(Activity.RESULT_CANCELED); finish() }
            return
        }
        val result = Intent(data).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (clipData == null && this.data != null) {
                clipData = ClipData.newUri(contentResolver, "Selected file", this.data!!)
            }
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}

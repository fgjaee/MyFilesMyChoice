package com.example.filemanagerredirect

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Thin shim: figures out the target file manager and forwards the original intent.
 * Hardened with try/catch so it never crashes the app.
 */
class RedirectActivity : AppCompatActivity() {

    private val tag = "RedirectActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val source = intent ?: Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }

        // Determine target package (may be null if redirect disabled)
        val targetPkg = try {
            FileManagerRedirectHook.resolveTargetPackage(this)
        } catch (t: Throwable) {
            Log.e(tag, "resolveTargetPackage failed", t)
            null
        }

        val forward = Intent(source).apply {
            flags = source.flags
            // IMPORTANT: don’t crash if targetPkg is null — fall back to chooser/system
            if (!targetPkg.isNullOrBlank()) setPackage(targetPkg)
        }

        try {
            // If a package is set and present, go direct. Otherwise present a chooser.
            if (!forward.`package`.isNullOrBlank()) {
                startActivity(forward)
            } else {
                startActivity(Intent.createChooser(forward, getString(R.string.chooser_title)))
            }
            finish()
        } catch (nf: ActivityNotFoundException) {
            Log.w(tag, "Target activity not found, falling back to system picker", nf)
            fallbackSystemPicker()
        } catch (t: Throwable) {
            Log.e(tag, "Unexpected failure launching target", t)
            fallbackSystemPicker()
        }
    }

    private fun fallbackSystemPicker() {
        try {
            val saf = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse("content://com.android.externalstorage.documents/document/primary:"))
            }
            startActivity(Intent.createChooser(saf, getString(R.string.chooser_title)))
        } catch (t: Throwable) {
            Log.e(tag, "Even SAF failed", t)
            Toast.makeText(this, "No compatible file manager found", Toast.LENGTH_LONG).show()
        } finally {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }
}
package com.example.filemanagerredirect

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Deliberately disable the old picker-process hook.
 *
 * It finished DocumentsUI before the original caller received a URI, so it
 * broke uploads. Automatic redirection needs a separate, verified design.
 */
class XposedEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) = Unit
}

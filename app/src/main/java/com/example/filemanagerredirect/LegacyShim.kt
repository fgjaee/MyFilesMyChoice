package com.example.filemanagerredirect

// These top-level constants match what your existing hook file expects.
const val PREF_ENABLED = "enabled"              // on/off toggle
const val PREF_SELECTED_PKG = "package"         // chosen file manager package
const val PKG_AUTO = ""                          // empty string means "auto choose"

// Big static list used by the hook code. (Keep as many as you want here.)
val PACKAGES: Array<String> = arrayOf(
    // Power users / root-capable
    "bin.mt.plus", "bin.mt.plus.canary",
    "com.mixplorer", "com.mixplorer.silver",
    "pl.solidexplorer2",
    "nextapp.fx", "nextapp.fx.rk", "nextapp.fx.plus",
    "com.speedsoftware.rootexplorer",
    "com.lonelycatgames.Xplore",
    "ru.zdevs.zarchiver",
    "com.estrongs.android.pop",
    "com.ghisler.android.TotalCommander",

    // AOSP & Google
    "com.android.documentsui", "com.google.android.documentsui",
    "com.google.android.apps.nbu.files",

    // OEMs
    "com.sec.android.app.myfiles",
    "com.coloros.filemanager", "com.oppo.filemanager",
    "com.oneplus.filemanager",
    "com.huawei.hidisk", "com.hihonor.filemanager",
    "com.mi.android.globalFileexplorer", "com.android.fileexplorer",
    "com.motorola.filemanager",
    "com.lge.filemanager",
    "com.asus.filemanager",
    "com.vivo.filemanager",
    "com.lenovo.FileBrowser",
    "com.transsion.filemanager",

    // Popular third-party
    "com.alphainventor.filemanager",   // File Manager +
    "com.amaze.filemanager",
    "com.rs.explorer.filemanager",
    "com.cxinventor.file.explorer",    // Cx File Explorer
    "com.mobisystems.fileman",         // File Commander
    "me.zhanghai.android.files",       // Material Files
    "com.ghostsq.commander",           // Ghost Commander
    "org.openintents.filemanager",     // OI
    "pl.mkexplorer.k",                 // MK Explorer
    "com.simplemobiletools.filemanager",
    "com.simplemobiletools.filemanager.pro",
    "dev.dworks.apps.anexplorer",
)
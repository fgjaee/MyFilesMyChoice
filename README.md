# MyFilesMyChoice

**My files. My choice.**

An Android LSPosed module with a global DocumentsUI file-picker interceptor and a preferred-manager bridge. This is a **device-testing alpha**, not a verified universal file picker replacement.

## Global mode (experimental)

1. Install the debug APK.
2. LSPosed Manager → Modules → MyFilesMyChoice → enable and scope **DocumentsUI** (often `com.google.android.documentsui` or `com.android.documentsui`). Do not scope every installed app.
3. Restart the DocumentsUI process or reboot.
4. Open MyFilesMyChoice → **Settings** → choose a manager; enable **System-wide interception (LSPosed)**.
5. Tap **Test SYSTEM-WIDE DocumentsUI hook**. The configured manager should appear automatically. Select a file; the test must say **PASS: Returned and readable**.

### Fallback

If you cancel the preferred manager, the original Android DocumentsUI picker stays open. For a full bypass, turn off the global switch in Settings. If no hook is installed or its config cannot be read, Android's normal picker works unchanged.

### What this covers

The hook runs in the **DocumentsUI process** and applies to all requesting apps that actually invoke that picker with `ACTION_GET_CONTENT`. It isn't a separate per-app list and does not require each calling app to be scoped in LSPosed.

The hook deliberately does **not** override `ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`, `ACTION_OPEN_DOCUMENT_TREE`, Google's separate Photo Picker, or OEM-specific picker implementations. Those operations have distinct permission/result contracts; covering them will require compatible implementations rather than changing the action string.

### Result handling

Unlike the original FilePickerReDirekt hook, DocumentsUI stays alive. It starts MyFilesMyChoice for result and forwards valid `content://` URIs and read-grant flags to the original caller. When canceled or invalid, DocumentsUI remains available for selecting the file normally.

## Known limitations

- **Not yet proven on a real device** for Android 16/17; request a field test.
- Requires LSPosed and a compatible root environment for automatic interception.
- The external manager must return a grantable `content://` result. No `file://` URI conversion is implemented yet.
- Root-only files are not exposed by this build.
- Some apps open Android Photo Picker directly or bypass DocumentsUI, and won't be intercepted.

## Build

GitHub Actions assembles a debug APK on commits to this branch. Download it from the workflow's **Artifacts**, install as an update, then run the tests listed in [docs/REVIVAL.md](docs/REVIVAL.md).

The Android application ID remains `com.example.filemanagerredirect` so existing app data and selection settings survive upgrading.

# MyFilesMyChoice

**My files. My choice.**

An Android file-picker bridge for choosing files with a preferred file manager.

## Current status: Android alpha, not a full system-wide replacement

The original FilePickerReDirekt repository has been renamed and revived while preserving its history. This development branch corrects a key flaw: the old picker-process Xposed hook finished the picker before forwarding a valid result to the requesting app.

This build offers a standard **ACTION_GET_CONTENT** picker activity. A compatible app can select it as a file picker, it launches a configured file manager, and forwards a returned **content URI** (with read permission flags). If no compatible manager can launch, it falls back to Android's standard **OPEN_DOCUMENT** picker.

**The legacy LSPosed hook is disabled** until real testing demonstrates reliable results. This build does not force apps to use the custom picker. It does not replace **OPEN_DOCUMENT**, **CREATE_DOCUMENT**, **OPEN_DOCUMENT_TREE**, or the Photo Picker. It also does not yet expose root-only files.

## Build

Use Android Studio with JDK 17 or run:

    gradle :app:assembleDebug

A GitHub Actions workflow builds an APK on pushes. Install the APK and select **Test MyFilesMyChoice bridge** to choose a file and verify whether the returned URI is readable. Compare against **Test Android system picker**.

## Development priorities

1. Verify that real calling applications can successfully upload a selected file.
2. Validate MIME types, cancellation, multiple selection, URI permissions and fallback.
3. Add a safe DocumentsProvider for broader SAF integration.
4. Add optional, user-approved root-file access through a carefully scoped content provider.
5. Explore caller-side LSPosed interoperability only after end-to-end tests.

See docs/REVIVAL.md for the alpha's scope and outstanding work.

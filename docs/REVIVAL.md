# MyFilesMyChoice — global hook alpha checklist

**My files. My choice.**

## Implemented

- Optional, centrally scoped LSPosed hook for `com.google.android.documentsui` and `com.android.documentsui` processes.
- `ACTION_GET_CONTENT` only. Original DocumentsUI remains alive.
- Bridge launched using `startActivityForResult`; result handled via Android's Activity.dispatchActivityResult before DocumentsUI's own callback, with a distinct request code.
- Valid `content://` URI results passed to original caller with read permissions.
- On cancel / invalid result / exception: do not finish stock picker; user can continue normally.
- Read-only settings ContentProvider permits DocumentsUI to check the global enable switch without private-file hacks.
- In-app test explicitly targets DocumentsUI, distinct from the preferred manager bridge test.

## Test it on your device

1. Install GitHub Actions debug APK as an update.
2. Enable MyFilesMyChoice for the *DocumentsUI* package(s) in LSPosed, not each calling app; reboot.
3. In MyFilesMyChoice > Settings, choose a preferred file manager and enable global interception.
4. Tap Test SYSTEM-WIDE DocumentsUI hook, select a TXT/PDF and confirm PASS.
5. Repeat and cancel your preferred manager. Original picker should be visible and remain usable.
6. Test a real external app that invokes DocumentsUI GET_CONTENT, check it accepts and opens the file.
7. Confirm OPEN_DOCUMENT test still uses the original Android picker.
8. Disable the global switch and confirm stock behavior returns.

## Known limitations

The separate Android Photo Picker, OPEN_DOCUMENT, TREE, CREATE_DOCUMENT, and OEM picker UIs are not intercepted. The hook has not yet been validated on Android 16/17 hardware or real uploads. Capturing all of those workflows is a later milestone and may need more than DocumentsUI interception.

## Recovery

If the picker crashes, disable MyFilesMyChoice in LSPosed or turn its global toggle off, then restart the picker process/reboot. Reinstalling without clearing data retains preferred-manager preferences.

## Remaining tests

- Third-party caller result grants (single/multi-select).
- Unexpected resultCode and requestCode collisions.
- MIME mismatch, invalid URI, file manager cancellation.
- Real-world browser upload uses GET_CONTENT vs Photo Picker.
- Samsung and non-Google DocumentsUI variants.

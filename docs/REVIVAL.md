# MyFilesMyChoice — revival checkpoint

**Slogan:** My files. My choice.

This is a development branch built from the renamed FilePickerReDirekt repository. The existing package name is retained to preserve upgrade identity.

## What is included
- Rebranded UI and app label.
- Standard ACTION_GET_CONTENT picker target, with a selected manager and system picker fallback.
- The bridge remains alive for the file manager's result and forwards grantable content URIs.
- An in-app readable-URI smoke test.
- The previous unsafe picker-process LSPosed hook is disabled.

## What is not yet implemented
- Automatic system-wide replacement of all Android pickers.
- SAF OPEN_DOCUMENT, CREATE_DOCUMENT, OPEN_DOCUMENT_TREE and Photo Picker overrides.
- Root access or permission escalation.
- Instrumented tests for third-party calling applications.
- Verified device support for Android 16/17.

## Release criteria
Build successfully, test URI grant propagation in a separate calling app and verify a real file upload. Check cancel, single and multiple selection, mismatched MIME types, fallback behavior and at least one Android 16/17 device. Do not claim universal Android 17 compatibility.

## Safety
A returned file:// URI must not be handed to a modern third-party app as an upload result. The app should not expose unrestricted root files or attempt unsafe picker-process interruption.

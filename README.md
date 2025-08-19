# FilePickerReDirekt

Redirect Android “file picker” intents (e.g., **GET_CONTENT**, **OPEN_DOCUMENT**, **OPEN_DOCUMENT_TREE**, **CREATE_DOCUMENT**, **PICK**) to your preferred file manager. Built as an **LSPosed / Xposed** module.

> **Status:** Early but functional. Requires Magisk + LSPosed (or another Riru/Zygisk Xposed implementation).

---

## ✨ What it does

- Intercepts common file‑picking intents system‑wide.
- Opens your chosen file manager instead of the stock picker.
- “Auto” mode picks a good installed manager from a curated list (MiXplorer, Solid Explorer, etc.).
- Optional fallback to the system picker if your choice isn’t available.
- Lightweight: no background services, no network, no telemetry.

---

## 📦 Module metadata

- **Package:** `com.example.filemanagerredirect`
- **Xposed entry:** `assets/xposed_init` must contain a single line with the fully‑qualified entry class. In this repo it is:
  ```
  com.example.filemanagerredirect.XposedEntry
  ```
  > If you rename or move the entry class, update this file accordingly.

- **Manifest:** includes standard Xposed metadata:
  ```xml
  <meta-data android:name="xposedmodule" android:value="true" />
  <meta-data android:name="xposeddescription" android:value="Redirects Android file picker intents to your chosen file manager." />
  <meta-data android:name="xposedminversion" android:value="93" />
  ```

---

## 🛠️ Build

### Requirements
- **JDK 17**
- **Android Gradle Plugin / Gradle** (project already configured)
- **Android SDK 34** (compile) and minSdk 24
- **LSPosed / Xposed API** (added as _compileOnly_ so it’s not bundled)

### Gradle (local)
```bash
# From project root
./gradlew clean :app:assembleDebug
# APK output:
# app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions
A workflow is included to build on each push. The job effectively runs:
```bash
gradle clean :app:assembleDebug --stacktrace --warning-mode=all --refresh-dependencies
```

> If the build complains about `de.robv.android.xposed:api`, make sure the project has the **Xposed repo**:
> - Project `build.gradle` (Groovy):
>   ```gradle
>   allprojects {
>     repositories {
>       google()
>       mavenCentral()
>       maven { url 'https://api.xposed.info/' }
>     }
>   }
>   ```

---

## 📁 Project layout (high‑level)

```
app/
  src/main/
    AndroidManifest.xml
    assets/
      xposed_init                      # -> com.example.filemanagerredirect.XposedEntry
    java|kotlin/com/example/filemanagerredirect/
      XposedEntry.kt                   # IXposedHookLoadPackage entry (LSPosed)
      FileManagerRedirectHook.kt       # Chooses target package (auto/selected)
      Prefs.kt                         # Preference keys & shims
      MainActivity.kt                  # Simple UI
      SettingsActivity.kt              # Options UI (if enabled)
      RedirectActivity.kt              # Plain intent redirector (optional)
    res/                               # Strings, themes, layouts
```

---

## ⚙️ Configuration

Open the app and set:
- **Enabled** – master switch.
- **Primary manager** – pick a package or choose **Auto**.
- **Fallback to system picker** – optional.

Internally we persist preferences in `SharedPreferences` (name: `fmr_prefs`). Key highlights:
- `enabled` – master toggle
- `primary_manager` – your chosen package or `"auto"`
- `fallback_saf` – allow fallback to AOSP picker
- `fallback_managers` – optional multi‑select of alternates

---

## 🔍 How the target app is chosen

1. If disabled → **no redirect**.
2. If a **specific package** is set → use it.
3. If **Auto** → check an ordered list of popular managers and pick the first that’s installed.
4. If none found → fallback to **com.android.documentsui**.

Installed checks are API‑level safe via a small `getPackageInfo` compat wrapper.

---

## 🚀 Install & enable (end‑user)

1. Install the APK (`app-debug.apk` from CI Releases or your local build).
2. Open **LSPosed Manager** → **Modules** → enable **FilePickerReDirekt** for the **system framework** (recommended) or select scopes as desired.
3. Reboot.
4. Trigger any file‑picking action (share sheet “Files”, “Open”, attach file, etc.) and confirm it opens your selected file manager.

> **Tip:** Enabling only the **system framework** scope usually catches most picker flows while minimizing side effects.

---

## ❗ Troubleshooting

- **App opens stock picker:**  
  - Ensure the module is **enabled in LSPosed** and scoped to **system framework**.  
  - Verify your chosen file manager is **installed** and not disabled by a Work Profile/owner.
  - Try **Auto** mode to let the module pick a compatible manager.
- **Boot loops / crashes:**  
  - Disable the module from LSPosed safe mode and report the stacktrace.
- **Build error: `package="..." in manifest is ignored`:**  
  - Remove the `package` attribute from `AndroidManifest.xml` (namespace is set in `build.gradle`).
- **Missing Xposed API during build:**  
  - Add `maven { url 'https://api.xposed.info/' }` to repositories and use `compileOnly "de.robv.android.xposed:api:82"` (82 is the latest published artifact).

---

## 🛡️ Permissions

We don’t request network privileges. Optional storage permissions are declared primarily for legacy interop and visibility; the module itself doesn’t read your files.

---

## 🧩 Limitations

- Some OEMs heavily customize the intent resolver; redirection might not catch 100% of cases.
- Work profile / Device Owner policies may block cross‑profile intents or package visibility.
- You still need a capable file manager installed (MiXplorer, Solid Explorer, etc.).

---

## 🤝 Contributing

PRs and issues are welcome. Please include:
- Device model, Android version, ROM/Kernel.
- LSPosed version + module scope (framework vs. app‑specific).
- Logcat or LSPosed logs if relevant.

---

## 📜 License

MIT (see `LICENSE`).

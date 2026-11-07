# Android APK releases and updates

This repository builds **debug APKs** on the main branch in `.github/workflows/android.yml`. They can be downloaded from successful workflow runs under **Artifacts**, but are NOT published as stable releases.

## Production releases

`.github/workflows/android-release.yml` publishes an installable **signed** release APK only for a version tag, such as `android-v2.0.0`. It requires the following GitHub Actions repository secrets:

| Secret | Content |
|---|---|
| `ANDROID_RELEASE_KEYSTORE_BASE64` | Base64-encoded binary JKS/PKCS12 signing keystore, one line |
| `ANDROID_RELEASE_STORE_PASSWORD` | Keystore password |
| `ANDROID_RELEASE_KEY_ALIAS` | Private signing key alias |
| `ANDROID_RELEASE_KEY_PASSWORD` | Private signing key password |

Create the keystore OFFLINE, back it up securely, and never commit it to GitHub. To generate an initial keystore with JDK 17:

```powershell
keytool -genkeypair -v -keystore arcanevault-release.jks -alias arcanevault -keyalg RSA -keysize 3072 -validity 10000
```

On Windows PowerShell, generate the base64 value without wrapping lines:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("arcanevault-release.jks")) | Set-Clipboard
```

Paste this into the Base64 GitHub Actions secret. Never share the keystore or passwords in chat, screenshots, issue trackers, or source code. Configure the four secrets at **GitHub → Settings → Secrets and variables → Actions → New repository secret**.

**Critical upgrade warning:** If your currently installed Android APK was signed with an Android Studio/debug signing key, a release APK signed with a *different key* will NOT install in place. Export an Arcane Vault backup before changing signing identities. A reliable long-term update process must always use the same release signing certificate. Do not uninstall to fix a signature mismatch until the backup has been verified.

## Publishing a release

1. Bump both `versionName` (e.g., `2.0.1`) and the monotonic integer `versionCode` (e.g., `21`) in `app/build.gradle`.
2. Create `releases/android-v2.0.1.md` with the required `## Added`, `## Changed`, `## Fixed`, `## Removed` headings.
3. Commit those changes to `main`, ensure the Android Debug APK CI checks pass, and tag that commit:
   ```powershell
   git tag android-v2.0.1
   git push origin android-v2.0.1
   ```
4. The signed-release workflow checks that the tag matches the Gradle `versionName`, runs JavaScript tests, validates signing credentials, builds `:app:assembleRelease`, verifies the APK signature, creates a SHA-256 checksum, and publishes a stable GitHub Release. If secrets are missing, the workflow fails **without publishing a release**.

For the first signed `android-v2.0.0` release, the repository already includes `releases/android-v2.0.0.md`. If existing test users have debug builds installed, consider moving to version 2.0.1 and clearly communicating the signing-key transition instead of reusing 2.0.0.

## In-app update checker

Open **Options, Collection & Metadata → Application updates** and enter `thejohnfavaro/arcanevaultmobile`. The app checks public, stable GitHub Releases for tags such as `android-v2.0.1` with an APK attachment. A prerelease, test APK or source archive does not qualify. The updater only links to the download; installation is a separate user action. Android installer verifies the signing identity and versionCode.

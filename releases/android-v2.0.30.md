# Arcane Vault Android v2.0.30 — Native in-app updates

## Added
- Native Android Home **Check for app updates** control independent of the WebView.
- GitHub signed release selection using numeric version comparison and matching APK plus SHA-256 checksum asset.
- Opt-in APK download with size bound, checksum verification, installed package ID and signing-certificate match, and higher version check.
- Scoped read-only Android content provider for handing the verified APK to Android's normal installer.

## Changed
- The native Android UI can check for updates and request installation without navigating a browser to GitHub Releases.
- Android always obtains explicit confirmation before downloading and again before invoking the platform installer. Unknown-source permission is requested only on demand.

## Fixed
- Avoid downloading a differently signed debug build as an automatic update to a release-signed installation.

## Removed
- No existing WebView functionality removed.

## Safety
- No app uninstall, automatic installation, collection writes or schema migration. If verification fails, the update is not installed and local data stays in place.
- A correct release signing key is required to update an existing signed installation. Never install a debug APK over a release-signed package; use a backed-up installation and its matching release channel.

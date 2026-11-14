import assert from 'node:assert/strict';
import fs from 'node:fs';
const updater = fs.readFileSync('app/src/main/java/com/arcanevault/mobile/NativeAppUpdater.java','utf8');
const provider = fs.readFileSync('app/src/main/java/com/arcanevault/mobile/VerifiedUpdateProvider.java','utf8');
const screen = fs.readFileSync('app/src/main/java/com/arcanevault/mobile/NativeCompanionScreen.java','utf8');
const manifest = fs.readFileSync('app/src/main/AndroidManifest.xml','utf8');
const gradle = fs.readFileSync('app/build.gradle','utf8');
for (const section of [
  /android-v\[0-9\]/, /releases\?per_page=30/, /optBoolean\("draft"\)/,
  /optBoolean\("prerelease"\)/, /browser_download_url/,
  /\.sha256/, /MessageDigest\.getInstance\("SHA-256"\)/,
  /getPackageArchiveInfo/, /getPackageInfo/, /getApkContentsSigners/,
  /candidate\.getLongVersionCode\(\) <= installed\.getLongVersionCode\(\)/,
  /MAX_APK/, /Intent\.ACTION_VIEW/, /FLAG_GRANT_READ_URI_PERMISSION/,
  /canRequestPackageInstalls/, /ACTION_MANAGE_UNKNOWN_APP_SOURCES/
]) assert.match(updater,section,'Native updater must enforce release, integrity, and installer checks');
assert.match(provider,/MODE_READ_ONLY/);
assert.match(provider,/\/verified\/update\.apk/);
assert.match(provider,/UnsupportedOperationException/);
assert.match(manifest,/android\.permission\.REQUEST_INSTALL_PACKAGES/);
assert.match(manifest,/android:exported="false" android:grantUriPermissions="true"/);
assert.match(screen,/Check for app updates/);
assert.match(screen,/new NativeAppUpdater\(activity\)\.check\(\)/);
assert.match(gradle,/versionCode 50/);
assert.match(gradle,/versionName '2\.0\.30'/);
console.log('PASS: native Android update flow exposes an opt-in signed, checksummed, scoped installer without collection writes');

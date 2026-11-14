package com.arcanevault.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native, opt-in, signature-safe Android GitHub APK updater. */
final class NativeAppUpdater {
    private static final String RELEASES = "https://api.github.com/repos/thejohnfavaro/arcanevaultmobile/releases?per_page=30";
    private static final String REPO = "thejohnfavaro/arcanevaultmobile";
    private static final long MAX_APK = 200L * 1024 * 1024;
    private static final ExecutorService JOBS = Executors.newSingleThreadExecutor();
    private final Activity activity;
    private final Handler ui = new Handler(Looper.getMainLooper());

    NativeAppUpdater(Activity activity) { this.activity = activity; }

    private static final class Candidate {
        final String version, apkUrl, hashUrl;
        final int[] parts;
        Candidate(String version, String apkUrl, String hashUrl, int[] parts) {
            this.version = version; this.apkUrl = apkUrl; this.hashUrl = hashUrl; this.parts = parts;
        }
    }

    void check() {
        Toast.makeText(activity, "Checking signed Android releases…", Toast.LENGTH_SHORT).show();
        JOBS.execute(() -> {
            try {
                Candidate release = findLatest();
                ui.post(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    if (release == null) {
                        new AlertDialog.Builder(activity).setTitle("Arcane Vault is up to date")
                            .setMessage("No newer signed Android release is available.")
                            .setPositiveButton("OK", null).show();
                        return;
                    }
                    new AlertDialog.Builder(activity).setTitle("Update available: v" + release.version)
                        .setMessage("Download and verify the signed Android APK before asking Android to install it? Your collection remains in the existing app storage. We will not uninstall the app.")
                        .setNegativeButton("Not now", null)
                        .setPositiveButton("Download", (dialog, which) -> download(release)).show();
                });
            } catch (Exception failure) {
                notifyFailure("Unable to check updates: " + failure.getMessage());
            }
        });
    }

    private Candidate findLatest() throws Exception {
        JSONArray releases = new JSONArray(readText(RELEASES, 2_000_000));
        int[] current = parseVersion(activity.getPackageManager()
            .getPackageInfo(activity.getPackageName(), 0).versionName);
        if (current == null) throw new IllegalStateException("Installed Android version is invalid");
        Candidate best = null;
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.optJSONObject(i);
            if (release == null || release.optBoolean("draft") ||
                release.optBoolean("prerelease")) continue;
            String tag = release.optString("tag_name", "");
            if (!tag.matches("android-v[0-9]+\\.[0-9]+\\.[0-9]+")) continue;
            String version = tag.substring("android-v".length());
            int[] parts = parseVersion(version);
            if (parts == null || compare(parts, current) <= 0 ||
                (best != null && compare(parts, best.parts) <= 0)) continue;
            String apkName = "ArcaneVault-Android-v" + version + ".apk";
            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) continue;
            String apk = null, sha = null;
            for (int j = 0; j < assets.length(); j++) {
                JSONObject asset = assets.optJSONObject(j);
                if (asset == null) continue;
                String name = asset.optString("name", "");
                String url = asset.optString("browser_download_url", "");
                String expected = "https://github.com/" + REPO + "/releases/download/" + tag + "/" + name;
                if (!url.equals(expected)) continue;
                if (name.equals(apkName)) apk = url;
                if (name.equals(apkName + ".sha256")) sha = url;
            }
            if (apk != null && sha != null) best = new Candidate(version, apk, sha, parts);
        }
        return best;
    }

    private static int[] parseVersion(String value) {
        if (value == null || !value.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) return null;
        String[] numbers = value.split("\\.");
        try {
            return new int[]{Integer.parseInt(numbers[0]), Integer.parseInt(numbers[1]), Integer.parseInt(numbers[2])};
        } catch (NumberFormatException ex) { return null; }
    }

    private static int compare(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
        return 0;
    }

    private static HttpURLConnection connection(String address) throws Exception {
        URL url = new URL(address);
        if (!"https".equals(url.getProtocol()) ||
            !("github.com".equals(url.getHost()) || "api.github.com".equals(url.getHost()) ||
              "objects.githubusercontent.com".equals(url.getHost()) ||
              "release-assets.githubusercontent.com".equals(url.getHost())))
            throw new SecurityException("Unexpected download host");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "ArcaneVault-Native-Android-Updater/1");
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setInstanceFollowRedirects(false);
        return conn;
    }

    /** Follow only HTTPS redirects to explicit GitHub asset infrastructure. */
    private static HttpURLConnection open(String path) throws Exception {
        String next = path;
        for (int i = 0; i < 6; i++) {
            HttpURLConnection conn = connection(next);
            int status = conn.getResponseCode();
            if (status == 200) return conn;
            if (status != 301 && status != 302 && status != 303 && status != 307 && status != 308) {
                conn.disconnect();
                throw new IllegalStateException("Download returned HTTP " + status);
            }
            String redirect = conn.getHeaderField("Location");
            conn.disconnect();
            if (redirect == null || !redirect.startsWith("https://"))
                throw new SecurityException("Insecure release redirect");
            next = redirect;
        }
        throw new SecurityException("Excessive release redirects");
    }

    private static String readText(String url, int limit) throws Exception {
        HttpURLConnection conn = open(url);
        try (InputStream in = conn.getInputStream()) {
            byte[] data = readBytes(in, limit);
            return new String(data, StandardCharsets.UTF_8);
        } finally { conn.disconnect(); }
    }

    private static byte[] readBytes(InputStream in, long max) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int read;
        while ((read = in.read(buf)) != -1) {
            if ((long) output.size() + read > max) throw new SecurityException("Download too large");
            output.write(buf, 0, read);
        }
        return output.toByteArray();
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }

    private void download(Candidate release) {
        Toast.makeText(activity, "Downloading verified v" + release.version + "…", Toast.LENGTH_LONG).show();
        JOBS.execute(() -> {
            File file = null;
            try {
                String expectedName = "ArcaneVault-Android-v" + release.version + ".apk";
                String checksum = readText(release.hashUrl, 8192).trim();
                String[] tokens = checksum.split("\\s+");
                if (tokens.length != 2 || !tokens[0].matches("[a-fA-F0-9]{64}") ||
                    !tokens[1].replaceFirst("^\\*", "").equals(expectedName))
                    throw new SecurityException("Invalid release checksum file");
                File directory = new File(activity.getCacheDir(), "verified-updates");
                if (!directory.exists() && !directory.mkdirs())
                    throw new IllegalStateException("Cannot create private update cache");
                file = new File(directory, "update.apk");
                File temporary = new File(directory, "update.partial");
                if (temporary.exists()) temporary.delete();
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                HttpURLConnection conn = open(release.apkUrl);
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(temporary)) {
                    byte[] buffer = new byte[8192]; int length; long size = 0;
                    while ((length = in.read(buffer)) != -1) {
                        size += length;
                        if (size > MAX_APK) throw new SecurityException("APK exceeds size limit");
                        digest.update(buffer, 0, length);
                        out.write(buffer, 0, length);
                    }
                    out.getFD().sync();
                } finally { conn.disconnect(); }
                if (!hex(digest.digest()).equalsIgnoreCase(tokens[0]))
                    throw new SecurityException("Downloaded APK checksum mismatch");
                verifyPackage(temporary);
                if (file.exists() && !file.delete()) throw new IllegalStateException("Stale update cannot be replaced");
                if (!temporary.renameTo(file)) throw new IllegalStateException("Verified update could not be staged");
                final File verified = file;
                ui.post(() -> confirmInstall(verified));
            } catch (Exception failure) {
                if (file != null) file.delete();
                new File(new File(activity.getCacheDir(), "verified-updates"), "update.partial").delete();
                notifyFailure("Update was not installed: " + failure.getMessage());
            }
        });
    }

    private void verifyPackage(File archive) throws Exception {
        PackageManager pm = activity.getPackageManager();
        int flags = PackageManager.GET_SIGNING_CERTIFICATES;
        PackageInfo candidate = pm.getPackageArchiveInfo(archive.getAbsolutePath(), flags);
        PackageInfo installed = pm.getPackageInfo(activity.getPackageName(), flags);
        if (candidate == null || candidate.signingInfo == null || installed.signingInfo == null ||
            !activity.getPackageName().equals(candidate.packageName) ||
            candidate.getLongVersionCode() <= installed.getLongVersionCode())
            throw new SecurityException("APK package identity or version does not match");
        // Signing history is handled by Android's package installer. Require the
        // same current signer here too, never a different debug/release certificate.
        android.content.pm.Signature[] a = candidate.signingInfo.getApkContentsSigners();
        android.content.pm.Signature[] b = installed.signingInfo.getApkContentsSigners();
        if (a == null || b == null || a.length != b.length || a.length == 0)
            throw new SecurityException("APK signing certificate missing");
        for (int i = 0; i < a.length; i++) {
            boolean found = false;
            for (android.content.pm.Signature signer : b)
                if (a[i].equals(signer)) { found = true; break; }
            if (!found) throw new SecurityException("APK signed with a different certificate");
        }
    }

    private void confirmInstall(File verified) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        new AlertDialog.Builder(activity).setTitle("Verified APK ready")
            .setMessage("The download's SHA-256, package ID, signing certificate, and higher version were checked. Android will request your confirmation to install this update. Your collection is not cleared.")
            .setNegativeButton("Later", null)
            .setPositiveButton("Install", (dialog, which) -> {
                if (Build.VERSION.SDK_INT >= 26 &&
                    !activity.getPackageManager().canRequestPackageInstalls()) {
                    new AlertDialog.Builder(activity).setTitle("Allow app updates")
                        .setMessage("Android requires permission to install updates from Arcane Vault. Enable 'Allow from this source' for this app, then tap Check for updates again.")
                        .setPositiveButton("Open settings", (d, w) -> {
                            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName()));
                            activity.startActivity(settings);
                        }).setNegativeButton("Cancel", null).show();
                    return;
                }
                Uri uri = Uri.parse("content://" + activity.getPackageName() +
                    ".updates/verified/update.apk");
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try { activity.startActivity(intent); }
                catch (Exception ex) { notifyFailure("Android installer unavailable: " + ex.getMessage()); }
            }).show();
    }

    private void notifyFailure(String message) {
        ui.post(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed())
                new AlertDialog.Builder(activity).setTitle("Android update").setMessage(message)
                    .setPositiveButton("OK", null).show();
        });
    }
}

package com.arcanevault.mobile;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

/** Single read-only APK URI, not a general file-sharing provider. */
public final class VerifiedUpdateProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) {
        return valid(uri) ? "application/vnd.android.package-archive" : null;
    }
    private boolean valid(Uri uri) {
        return uri != null && getContext() != null &&
            (getContext().getPackageName() + ".updates").equals(uri.getAuthority()) &&
            "/verified/update.apk".equals(uri.getPath());
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!valid(uri) || !"r".equals(mode))
            throw new FileNotFoundException("Invalid update URI or mode");
        File base = new File(getContext().getCacheDir(), "verified-updates");
        File apk = new File(base, "update.apk");
        if (!apk.isFile()) throw new FileNotFoundException("No verified update staged");
        return ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        if (!valid(uri)) return null;
        String[] cols = projection == null ? new String[]{"_display_name","_size"} : projection;
        android.database.MatrixCursor rows = new android.database.MatrixCursor(cols);
        Object[] values = new Object[cols.length];
        File apk = new File(new File(getContext().getCacheDir(), "verified-updates"), "update.apk");
        for (int i = 0; i < cols.length; i++) {
            if ("_display_name".equals(cols[i])) values[i] = "ArcaneVault-Update.apk";
            else if ("_size".equals(cols[i])) values[i] = apk.isFile() ? apk.length() : 0L;
        }
        rows.addRow(values);
        return rows;
    }
    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only update provider");
    }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only update provider");
    }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only update provider");
    }
}

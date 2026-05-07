package com.flipphoneguy.f21bands;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Tiny zero-dependency content provider that exposes one downloaded APK so
 * {@link android.content.Intent#ACTION_VIEW} with mimetype
 * {@code application/vnd.android.package-archive} can launch the system
 * package installer on Android 7+ (where {@code file://} URIs throw
 * {@code FileUriExposedException}).
 *
 * Holds a single staged path; subsequent {@link #stage(File)} calls replace
 * it. Authority is {@code <appId>.apkprovider}.
 */
public final class ApkProvider extends ContentProvider {

    public static final String AUTHORITY = "com.flipphoneguy.f21bands.apkprovider";

    private static volatile File staged;

    public static Uri stage(File apk) {
        staged = apk;
        return Uri.parse("content://" + AUTHORITY + "/apk");
    }

    @Override
    public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = staged;
        if (f == null || !f.exists()) throw new FileNotFoundException("no APK staged");
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File f = staged;
        String[] cols = projection != null
                ? projection
                : new String[]{ OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };
        MatrixCursor cur = new MatrixCursor(cols);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) {
                row[i] = f != null ? f.getName() : "update.apk";
            } else if (OpenableColumns.SIZE.equals(cols[i])) {
                row[i] = f != null ? f.length() : 0L;
            } else {
                row[i] = null;
            }
        }
        cur.addRow(row);
        return cur;
    }

    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}

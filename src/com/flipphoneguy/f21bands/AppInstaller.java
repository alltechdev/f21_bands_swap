package com.flipphoneguy.f21bands;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.io.File;
import java.io.IOException;

/**
 * Two install paths for an already-downloaded .apk:
 *
 * <ul>
 *   <li>{@link #installSilently(File, Callback)} — invokes
 *       {@code pm install -r -d} via {@link RootRunner}. No UI prompt.
 *       Requires root.</li>
 *   <li>{@link #installViaSystem(Context, File)} — fires an
 *       {@code ACTION_VIEW} intent at the system package installer using a
 *       content URI from {@link ApkProvider}. User taps Install.</li>
 * </ul>
 */
public final class AppInstaller {

    public interface Callback {
        void onResult(boolean success, String message);
    }

    private AppInstaller() {}

    public static void installSilently(File apk, Callback cb) {
        if (apk == null || !apk.exists()) {
            cb.onResult(false, "APK file missing: " + apk);
            return;
        }
        try {
            String cmd = "pm install -r -d " + shellQuote(apk.getAbsolutePath());
            RootRunner.Result r = RootRunner.run(cmd);
            String out = (r.stdout + "\n" + r.stderr).trim();
            if (r.exit == 0 && out.contains("Success")) {
                cb.onResult(true, out);
            } else {
                cb.onResult(false, out.isEmpty() ? ("pm install exit " + r.exit) : out);
            }
        } catch (IOException | InterruptedException e) {
            cb.onResult(false, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    public static void installViaSystem(Context ctx, File apk) {
        Uri uri = ApkProvider.stage(apk);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }

    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}

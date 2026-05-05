package com.flipphoneguy.f21bands;

import android.content.Context;
import android.net.Uri;

import org.tukaani.xz.XZInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public final class BlobLoader {

    public interface ProgressListener {
        void onProgress(long bytesSoFar, long total);
    }

    private BlobLoader() {}

    public static File blobFile(Context ctx, String region) {
        return new File(ctx.getFilesDir(), Constants.blobFileNameForRegion(region));
    }

    public static File download(Context ctx, String region, ProgressListener listener) throws IOException {
        File outFile = blobFile(ctx, region);
        File tmp = new File(ctx.getFilesDir(), outFile.getName() + ".part");
        URL url = new URL(Constants.urlForRegion(region));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if (conn instanceof HttpsURLConnection) {
            // default trust manager — GitHub raw uses public CAs.
        }
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(60_000);
        conn.setRequestProperty("User-Agent", "F21Bands/1.0");
        conn.setInstanceFollowRedirects(true);
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            long total = conn.getContentLengthLong();
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp))) {
                byte[] buf = new byte[64 * 1024];
                long sofar = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    sofar += n;
                    if (listener != null) listener.onProgress(sofar, total);
                }
            }
        } finally {
            conn.disconnect();
        }
        return finalize(tmp, outFile);
    }

    public static File importFromUri(Context ctx, Uri src, String region) throws IOException {
        File outFile = blobFile(ctx, region);
        File tmp = new File(ctx.getFilesDir(), outFile.getName() + ".part");
        try (InputStream in = new BufferedInputStream(ctx.getContentResolver().openInputStream(src));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp))) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return finalize(tmp, outFile);
    }

    /**
     * Streams the entire blob through xz+tar to confirm it parses and contains
     * the 4 expected files at the expected sizes. Fast (a few seconds for 20 MB).
     */
    public static boolean validate(File blob) {
        if (!blob.isFile() || blob.length() == 0) return false;
        boolean[] seen = new boolean[Constants.PARTITION_FILES.length];
        try (InputStream raw = new BufferedInputStream(new FileInputStream(blob));
             InputStream xz = new XZInputStream(raw)) {
            while (true) {
                TarStream.Entry e = TarStream.readHeader(xz);
                if (e == null) break;
                int idx = indexOf(e.name);
                if (idx < 0) {
                    TarStream.copyBody(xz, null, e.size);
                    continue;
                }
                if (e.size != Constants.PARTITION_SIZES[idx]) return false;
                TarStream.copyBody(xz, null, e.size);
                seen[idx] = true;
            }
        } catch (IOException e) {
            return false;
        }
        for (boolean b : seen) {
            if (!b) return false;
        }
        return true;
    }

    private static File finalize(File tmp, File outFile) throws IOException {
        if (outFile.exists() && !outFile.delete()) {
            throw new IOException("Cannot replace existing blob");
        }
        if (!tmp.renameTo(outFile)) {
            throw new IOException("Cannot rename .part to final");
        }
        return outFile;
    }

    private static int indexOf(String name) {
        for (int i = 0; i < Constants.PARTITION_FILES.length; i++) {
            if (Constants.PARTITION_FILES[i].equals(name)) return i;
        }
        return -1;
    }
}

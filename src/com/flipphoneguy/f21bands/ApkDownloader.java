package com.flipphoneguy.f21bands;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Streams an HTTP(S) GET into a local file with progress callbacks. Blocking;
 * call from a worker thread.
 */
public final class ApkDownloader {

    public interface ProgressCallback {
        void onProgress(long downloaded, long total);
    }

    private ApkDownloader() {}

    public static File download(String url, File outFile, ProgressCallback cb) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "f21bandsswap-updater");
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new RuntimeException("HTTP " + code);

            long total = c.getContentLengthLong();
            long downloaded = 0;

            File parent = outFile.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new RuntimeException("cannot create " + parent);
            }

            byte[] buf = new byte[16 * 1024];
            try (InputStream in = c.getInputStream();
                 FileOutputStream out = new FileOutputStream(outFile)) {
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    downloaded += n;
                    if (cb != null) cb.onProgress(downloaded, total);
                }
            }
            return outFile;
        } finally {
            c.disconnect();
        }
    }
}

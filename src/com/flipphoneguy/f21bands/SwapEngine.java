package com.flipphoneguy.f21bands;

import android.content.Context;

import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Backup-then-flash. Both halves are streamed end-to-end:
 *   - Backup pipes `dd if=<part>` straight into a single xz/tar output stream,
 *     so the 270 MB of raw partition data never lands on disk.
 *   - Flash pipes the xz/tar input stream straight into `dd of=<part>` for each
 *     entry in turn — no extraction step, no temp files.
 *
 * Storage invariant: at any time, app private storage holds exactly ONE blob —
 * the bands for the region the user is NOT currently on. After a swap, the
 * previously-loaded blob is deleted (those bands are now live on the device,
 * trivially re-dumpable on the next swap), and the freshly-created backup
 * takes its place.
 */
public final class SwapEngine {

    public interface ProgressListener {
        void step(String message);
    }

    private SwapEngine() {}

    public static void swap(Context ctx, String currentRegion, String targetRegion,
                            ProgressListener listener) throws IOException, InterruptedException {

        File loadedBlob = BlobLoader.blobFile(ctx, targetRegion);
        if (!loadedBlob.isFile()) {
            throw new IOException("Loaded blob missing: " + loadedBlob.getName());
        }

        File backupBlob = BlobLoader.blobFile(ctx, currentRegion);
        File backupTmp = new File(ctx.getFilesDir(), backupBlob.getName() + ".part");

        int total = Constants.PARTITION_FILES.length;

        // ── 1. Stream live partitions → xz/tar → backup blob.
        try (OutputStream raw = new BufferedOutputStream(new FileOutputStream(backupTmp));
             XZOutputStream xz = new XZOutputStream(raw, new LZMA2Options(LZMA2Options.PRESET_DEFAULT))) {
            for (int i = 0; i < total; i++) {
                if (listener != null) listener.step(ctx.getString(
                    R.string.step_backup_part, Constants.PARTITION_FILES[i], i + 1, total));
                TarStream.writeHeader(xz, Constants.PARTITION_FILES[i], Constants.PARTITION_SIZES[i]);
                RootRunner.streamPartitionToOut(
                    Constants.PARTITION_DEVICES[i],
                    Constants.PARTITION_SIZES[i],
                    xz);
                TarStream.writePad(xz, Constants.PARTITION_SIZES[i]);
            }
            TarStream.writeEnd(xz);
            xz.finish();
        }

        // Atomically replace the previous backup of this region (if any).
        if (backupBlob.exists() && !backupBlob.delete()) {
            //noinspection ResultOfMethodCallIgnored
            backupTmp.delete();
            throw new IOException("Cannot replace existing backup");
        }
        if (!backupTmp.renameTo(backupBlob)) {
            throw new IOException("Cannot rename backup");
        }

        // ── 2. Stream loaded blob → dd of=<partition> for each tar entry.
        boolean[] flashed = new boolean[total];
        int flashedCount = 0;
        try (InputStream rawIn = new BufferedInputStream(new FileInputStream(loadedBlob));
             InputStream xzIn = new XZInputStream(rawIn)) {
            while (true) {
                TarStream.Entry e = TarStream.readHeader(xzIn);
                if (e == null) break;
                int idx = indexOf(e.name);
                if (idx < 0) {
                    TarStream.copyBody(xzIn, null, e.size);
                    continue;
                }
                if (e.size != Constants.PARTITION_SIZES[idx]) {
                    throw new IOException("Blob entry size mismatch for " + e.name);
                }
                flashedCount++;
                if (listener != null) listener.step(ctx.getString(
                    R.string.step_flash_part, e.name, flashedCount, total));
                RootRunner.streamFlashFromIn(xzIn, e.size, Constants.PARTITION_DEVICES[idx]);
                TarStream.skipFully(xzIn, TarStream.padAfter(e.size));
                flashed[idx] = true;
            }
        }
        for (int i = 0; i < flashed.length; i++) {
            if (!flashed[i]) throw new IOException("Blob missing: " + Constants.PARTITION_FILES[i]);
        }

        // ── 3. Loaded blob is now live on the device — drop it.
        //noinspection ResultOfMethodCallIgnored
        loadedBlob.delete();

        if (listener != null) listener.step(ctx.getString(R.string.step_done));
    }

    private static int indexOf(String name) {
        for (int i = 0; i < Constants.PARTITION_FILES.length; i++) {
            if (Constants.PARTITION_FILES[i].equals(name)) return i;
        }
        return -1;
    }
}

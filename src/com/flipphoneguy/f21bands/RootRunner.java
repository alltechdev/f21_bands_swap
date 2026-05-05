package com.flipphoneguy.f21bands;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Thin wrapper around `su -c "..."`. All partition I/O is streamed: dumps
 * pipe `dd` stdout into a Java OutputStream; flashes pipe a Java InputStream
 * into `dd` stdin. We never stage raw partition images on disk, which
 * sidesteps both the SELinux app/shell label mismatch in /data/local/tmp and
 * the 270-ish MB of temp space that staging would otherwise need.
 */
public final class RootRunner {

    private RootRunner() {}

    public static boolean hasRoot() {
        try {
            Result r = run("id");
            return r.exit == 0 && r.stdout.contains("uid=0");
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    public static Result run(String cmd) throws IOException, InterruptedException {
        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
        p.getOutputStream().close();
        String stdout = drain(p.getInputStream());
        String stderr = drain(p.getErrorStream());
        int exit = p.waitFor();
        return new Result(exit, stdout, stderr);
    }

    /** SHA-256 of the first `bytes` bytes of a partition. Used for region detection. */
    public static String sha256Partition(String device, long bytes) throws IOException, InterruptedException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        long blocks = bytes / Constants.DD_BS;
        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c",
            "dd if=" + device + " bs=4M count=" + blocks + " 2>/dev/null"});
        p.getOutputStream().close();
        try (InputStream in = p.getInputStream()) {
            byte[] buf = new byte[1024 * 1024];
            long remaining = bytes;
            int n;
            while (remaining > 0 && (n = in.read(buf, 0, (int) Math.min(buf.length, remaining))) > 0) {
                md.update(buf, 0, n);
                remaining -= n;
            }
        }
        int exit = p.waitFor();
        if (exit != 0) throw new IOException("dd hash exited " + exit);
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** Streams `bytes` bytes from a partition through `dd` into the provided OutputStream. */
    public static void streamPartitionToOut(String device, long bytes, OutputStream out)
            throws IOException, InterruptedException {
        long blocks = bytes / Constants.DD_BS;
        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c",
            "dd if=" + device + " bs=4M count=" + blocks + " 2>/dev/null"});
        p.getOutputStream().close();
        try (InputStream in = p.getInputStream()) {
            byte[] buf = new byte[1024 * 1024];
            long remaining = bytes;
            int n;
            while (remaining > 0 && (n = in.read(buf, 0, (int) Math.min(buf.length, remaining))) > 0) {
                out.write(buf, 0, n);
                remaining -= n;
            }
            if (remaining != 0) throw new IOException("dd dump short read: " + remaining + " left");
        }
        int exit = p.waitFor();
        if (exit != 0) throw new IOException("dd dump exited " + exit);
    }

    /** Streams exactly `bytes` bytes from `in` into `dd of=device` via stdin. */
    public static void streamFlashFromIn(InputStream in, long bytes, String device)
            throws IOException, InterruptedException {
        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c",
            "dd of=" + device + " bs=4M 2>/dev/null"});
        long remaining = bytes;
        IOException pipeErr = null;
        try (OutputStream stdin = p.getOutputStream()) {
            byte[] buf = new byte[1024 * 1024];
            int n;
            while (remaining > 0 && (n = in.read(buf, 0, (int) Math.min(buf.length, remaining))) > 0) {
                stdin.write(buf, 0, n);
                remaining -= n;
            }
        } catch (IOException e) {
            pipeErr = e;
        }
        String stderr = drain(p.getErrorStream());
        int exit = p.waitFor();
        if (pipeErr != null) throw pipeErr;
        if (exit != 0) throw new IOException("dd flash exited " + exit + ": " + stderr);
        if (remaining != 0) throw new IOException("dd flash short input: " + remaining + " left");
    }

    public static void reboot() {
        try { run("reboot"); } catch (Exception ignored) {}
    }

    private static String drain(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString();
    }

    public static final class Result {
        public final int exit;
        public final String stdout;
        public final String stderr;
        Result(int exit, String stdout, String stderr) {
            this.exit = exit;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}

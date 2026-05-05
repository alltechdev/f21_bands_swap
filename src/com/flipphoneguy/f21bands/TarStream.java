package com.flipphoneguy.f21bands;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Minimal USTAR tar reader/writer. Just enough for our 4 known regular files.
 *
 * Spec ref: https://www.gnu.org/software/tar/manual/html_node/Standard.html
 * Each record is 512 bytes. Header + body + zero-pad to next 512. Two trailing
 * 512-byte zero blocks signal EOF.
 */
public final class TarStream {

    private static final int BLOCK = 512;

    private TarStream() {}

    public static final class Entry {
        public final String name;
        public final long size;
        Entry(String name, long size) { this.name = name; this.size = size; }
    }

    /** Reads a header block. Returns null at archive end. */
    public static Entry readHeader(InputStream in) throws IOException {
        byte[] hdr = new byte[BLOCK];
        if (!readFully(in, hdr)) return null;

        boolean allZero = true;
        for (byte b : hdr) {
            if (b != 0) { allZero = false; break; }
        }
        if (allZero) return null;

        int nameLen = 0;
        while (nameLen < 100 && hdr[nameLen] != 0) nameLen++;
        String name = new String(hdr, 0, nameLen, "UTF-8");
        long size = parseOctal(hdr, 124, 12);
        return new Entry(name, size);
    }

    /** Reads `size` bytes of body, optionally writing to `out`, then skips the pad. */
    public static void copyBody(InputStream in, OutputStream out, long size) throws IOException {
        byte[] buf = new byte[64 * 1024];
        long remaining = size;
        while (remaining > 0) {
            int want = (int) Math.min(buf.length, remaining);
            int n = in.read(buf, 0, want);
            if (n < 0) throw new IOException("Unexpected EOF inside tar entry");
            if (out != null) out.write(buf, 0, n);
            remaining -= n;
        }
        skipFully(in, padAfter(size));
    }

    /** Bytes of zero-padding that follow a body of the given size. */
    public static long padAfter(long size) {
        return (BLOCK - (size % BLOCK)) % BLOCK;
    }

    /** Writes a USTAR header for a regular file. */
    public static void writeHeader(OutputStream out, String name, long size) throws IOException {
        if (name.length() >= 100) throw new IOException("name too long: " + name);
        byte[] hdr = new byte[BLOCK];
        byte[] nameBytes = name.getBytes("UTF-8");
        System.arraycopy(nameBytes, 0, hdr, 0, nameBytes.length);

        writeOctal(hdr, 100, 8, 0644L);   // mode
        writeOctal(hdr, 108, 8, 0L);      // uid
        writeOctal(hdr, 116, 8, 0L);      // gid
        writeOctal(hdr, 124, 12, size);   // size
        writeOctal(hdr, 136, 12, 0L);     // mtime

        // chksum field is filled with spaces during sum, then overwritten.
        Arrays.fill(hdr, 148, 156, (byte) ' ');

        hdr[156] = '0';                   // typeflag: regular file
        // magic 257-262 = "ustar\0"
        hdr[257] = 'u'; hdr[258] = 's'; hdr[259] = 't'; hdr[260] = 'a'; hdr[261] = 'r';
        hdr[262] = 0;
        // version 263-264 = "00"
        hdr[263] = '0'; hdr[264] = '0';

        int sum = 0;
        for (byte b : hdr) sum += (b & 0xff);
        String s = String.format("%06o", sum);
        byte[] sb = s.getBytes("UTF-8");
        System.arraycopy(sb, 0, hdr, 148, sb.length);
        hdr[154] = 0;
        hdr[155] = ' ';

        out.write(hdr);
    }

    /** Pads `out` to the next BLOCK boundary after a body of `size` bytes. */
    public static void writePad(OutputStream out, long size) throws IOException {
        long pad = padAfter(size);
        if (pad > 0) out.write(new byte[(int) pad]);
    }

    /** Writes the two trailing zero blocks. */
    public static void writeEnd(OutputStream out) throws IOException {
        out.write(new byte[BLOCK * 2]);
    }

    public static void skipFully(InputStream in, long count) throws IOException {
        long remaining = count;
        byte[] junk = new byte[BLOCK];
        while (remaining > 0) {
            int want = (int) Math.min(junk.length, remaining);
            int n = in.read(junk, 0, want);
            if (n < 0) throw new IOException("Unexpected EOF skipping tar pad");
            remaining -= n;
        }
    }

    private static boolean readFully(InputStream in, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int n = in.read(buf, total, buf.length - total);
            if (n < 0) {
                if (total == 0) return false;
                throw new IOException("Unexpected EOF mid-header");
            }
            total += n;
        }
        return true;
    }

    private static long parseOctal(byte[] buf, int off, int len) {
        long val = 0;
        int end = off + len;
        for (int i = off; i < end; i++) {
            int c = buf[i] & 0xff;
            if (c == 0 || c == ' ') continue;
            if (c < '0' || c > '7') break;
            val = (val * 8) + (c - '0');
        }
        return val;
    }

    private static void writeOctal(byte[] buf, int off, int len, long val) {
        String s = String.format("%0" + (len - 1) + "o", val);
        if (s.length() > len - 1) {
            s = s.substring(s.length() - (len - 1));
        }
        byte[] b = s.getBytes();
        System.arraycopy(b, 0, buf, off, b.length);
        buf[off + len - 1] = 0;
    }
}

package com.flipphoneguy.f21bands;

import android.os.Build;
import java.io.File;
import java.io.IOException;

public final class M5SupportTest {
    public static void main(String[] args) throws Exception {
        check(DeviceDetector.detect() == DeviceDetector.Model.M5, "physical M5 identity");
        check(Constants.REGION_US.equals(BlobLoader.validate(new File("bands/m5/us.tar.xz"))), "M5 bundle");
        rejected("bands/us.tar.xz");
        malformed("nvcfg.bin", 33554432L); // Noncanonical first entry.
        malformed("md1img_a.bin", 41252016L); // Unpadded modem must fail exact-size validation.
        malformed(null, 0); // Missing all partitions.
        rejected("bands/stock.tar.xz");
        check(Constants.supportsTarget("us") && !Constants.supportsTarget("stock"), "one-way targets");
        check(RegionDetector.otherRegion("us") == null, "no reverse target");
        check(RegionDetector.otherRegion("unknown") == null, "unknown requires override");
        check(Constants.blobFileNameForRegion("unknown").equals("bands_m5_unknown.tar.xz"), "separate backup");
        check(Constants.urlForRegion("us").contains("/m5-support/bands/m5/"), "M5 download");
        check(Constants.urlForRegion("stock").isEmpty(), "no reverse download");
        Build.MANUFACTURER = "Qin"; Build.BRAND = "Qin"; Build.MODEL = "F21 Pro";
        Build.DEVICE = "f21pro"; Build.PRODUCT = "f21pro";
        check(Constants.REGION_US.equals(BlobLoader.validate(new File("bands/us.tar.xz"))), "F21 USA unchanged");
        check(Constants.REGION_STOCK.equals(BlobLoader.validate(new File("bands/stock.tar.xz"))), "F21 stock unchanged");
        rejected("bands/m5/us.tar.xz");
        check(Constants.supportsTarget("stock"), "F21 reverse retained");
        check("stock".equals(RegionDetector.otherRegion("us")), "F21 reverse target");
        check(Constants.blobFileNameForRegion("unknown").equals("bands_unknown.tar.xz"), "F21 backup unchanged");
        check(Constants.urlForRegion("us").equals(Constants.DOWNLOAD_BASE + "us.tar.xz"), "F21 URL unchanged");
        Build.MODEL = "unknown"; Build.DEVICE = "unknown"; Build.PRODUCT = "unknown";
        check(!Constants.supportsTarget("us"), "unknown device blocked");
        rejected("bands/us.tar.xz");
        System.out.println("M5/F21 bundle isolation, targets, backups and URL tests passed");
    }

    private static void malformed(String firstName, long size) throws Exception {
        File file = File.createTempFile("m5-invalid-", ".tar.xz");
        try {
            try (org.tukaani.xz.XZOutputStream out = new org.tukaani.xz.XZOutputStream(
                    new java.io.FileOutputStream(file), new org.tukaani.xz.LZMA2Options())) {
                if (firstName != null) TarStream.writeHeader(out, firstName, size);
                TarStream.writeEnd(out);
            }
            rejected(file.getAbsolutePath());
        } finally { file.delete(); }
    }

    private static void rejected(String path) throws Exception {
        try { BlobLoader.validate(new File(path)); }
        catch (IOException expected) { return; }
        throw new AssertionError("Accepted incompatible bundle: " + path);
    }
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}

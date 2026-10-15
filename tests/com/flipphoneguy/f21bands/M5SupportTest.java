package com.flipphoneguy.f21bands;

import android.os.Build;
import java.io.File;
import java.io.IOException;

public final class M5SupportTest {
    public static void main(String[] args) throws Exception {
        check(DeviceDetector.detect() == DeviceDetector.Model.M5, "physical M5 identity");
        check(Constants.REGION_US.equals(BlobLoader.validate(new File("bands/m5/us.tar.xz"))), "M5 bundle");
        rejected("bands/us.tar.xz");
        malformed("nvcfg.bin", 33554432L); // Truncated entry body.
        malformed("md1img_a.bin", 41252016L); // Unpadded modem must fail exact-size validation.
        malformed(null, 0); // Missing all partitions.
        rejected("bands/stock.tar.xz");
        check("stock".equals(BlobLoader.validate(new File("bands/m5/stock.tar.xz"))), "M5 EU bundle");
        check("stock".equals(RegionDetector.otherRegion("us")), "USA offers EU");
        check("stock".equals(Constants.regionForHash(Constants.M5_EU_MD1IMG_SHA256)), "EU detection");
        check("us".equals(RegionDetector.otherRegion("stock")), "EU offers USA");
        check("EU".equals(Constants.prettyRegion("stock")), "EU label");
        check(Constants.blobFileNameForRegion("stock").equals("bands_m5_stock.tar.xz"), "EU backup name");
        check(RegionDetector.otherRegion("unknown") == null, "unknown requires override");
        check(Constants.blobFileNameForRegion("unknown").equals("bands_m5_unknown.tar.xz"), "separate backup");
        check(Constants.urlForRegion("us").equals(Constants.DOWNLOAD_BASE + "m5/us.tar.xz"), "M5 upstream USA download");
        check(Constants.urlForRegion("stock").equals(Constants.DOWNLOAD_BASE + "m5/stock.tar.xz"), "M5 upstream EU download");
        Build.MANUFACTURER = "Qin"; Build.BRAND = "Qin"; Build.MODEL = "F21 Pro";
        Build.DEVICE = "f21pro"; Build.PRODUCT = "f21pro";
        check(Constants.REGION_US.equals(BlobLoader.validate(new File("bands/us.tar.xz"))), "F21 USA unchanged");
        check(Constants.REGION_STOCK.equals(BlobLoader.validate(new File("bands/stock.tar.xz"))), "F21 stock unchanged");
        legacyArchiveOrder();
        rejected("bands/m5/us.tar.xz");
        rejected("bands/m5/stock.tar.xz");
        check("stock".equals(RegionDetector.otherRegion("us")), "F21 reverse target");
        check(Constants.blobFileNameForRegion("unknown").equals("bands_unknown.tar.xz"), "F21 backup unchanged");
        check(Constants.urlForRegion("us").equals(Constants.DOWNLOAD_BASE + "us.tar.xz"), "F21 URL unchanged");
        // Generic/custom ROMs keep the original F21 hash and unknown-band flow.
        Build.MODEL = "unknown"; Build.DEVICE = "unknown"; Build.PRODUCT = "unknown";
        check(DeviceDetector.detect() == DeviceDetector.Model.UNKNOWN, "generic identity");
        check("us".equals(BlobLoader.validate(new File("bands/us.tar.xz"))), "generic-ROM F21 USA bundle");
        check("stock".equals(Constants.regionForHash(Constants.STOCK_MD1IMG_SHA256)), "generic-ROM F21 stock hash");
        check("unknown".equals(Constants.regionForHash("unrecognized")), "original unknown-band result");
        check(RegionDetector.otherRegion("unknown") == null, "unknown-band override remains necessary");
        check("stock".equals(Constants.prettyRegion("stock")), "F21 stock label unchanged");
        Build.MANUFACTURER = "M5"; Build.BRAND = "alps"; Build.MODEL = "M5";
        check("unknown".equals(Constants.regionForHash(Constants.US_MD1IMG_SHA256)), "M5 rejects F21 hash");
        Build.DEVICE = "F21 Pro";
        check(DeviceDetector.detect() == DeviceDetector.Model.UNKNOWN, "conflicting identity");
        check("us".equals(Constants.regionForHash(Constants.US_MD1IMG_SHA256)), "non-M5 retains original F21 hash handling");
        System.out.println("M5/F21 bundle isolation, bidirectional selection and legacy F21 detection tests passed");
    }

    // Upstream validates complete archives independently of entry order.
    // The existing flash-time order check remains in SwapEngine.
    private static void legacyArchiveOrder() throws Exception {
        File file = File.createTempFile("f21-reordered-", ".tar.xz");
        try {
            try (org.tukaani.xz.XZOutputStream out = new org.tukaani.xz.XZOutputStream(
                    new java.io.FileOutputStream(file), new org.tukaani.xz.LZMA2Options(0))) {
                for (int index : new int[] {1, 0, 2, 3}) {
                    boolean found = false;
                    try (org.tukaani.xz.XZInputStream in = new org.tukaani.xz.XZInputStream(
                            new java.io.FileInputStream("bands/us.tar.xz"))) {
                        TarStream.Entry entry;
                        while ((entry = TarStream.readHeader(in)) != null) {
                            if (entry.name.equals(Constants.PARTITION_FILES[index])) {
                                TarStream.writeHeader(out, entry.name, entry.size);
                                TarStream.copyBody(in, out, entry.size);
                                TarStream.writePad(out, entry.size);
                                found = true;
                                break;
                            }
                            TarStream.copyBody(in, null, entry.size);
                        }
                    }
                    check(found, "source partition exists");
                }
                TarStream.writeEnd(out);
            }
            check("us".equals(BlobLoader.validate(file)), "original order-independent F21 validation");
        } finally { file.delete(); }
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

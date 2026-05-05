package com.flipphoneguy.f21bands;

import java.io.IOException;

/**
 * Hash the md1img_a partition and match against embedded SHA-256s. NV
 * partitions get rewritten at runtime (calibration, IMEI cache, time-of-day),
 * so they're useless for region detection — only the modem firmware blob is
 * stable.
 */
public final class RegionDetector {

    private RegionDetector() {}

    public static String detect() throws IOException, InterruptedException {
        long size = Constants.PARTITION_SIZES[0];
        String device = Constants.PARTITION_DEVICES[0];
        String h = RootRunner.sha256Partition(device, size);
        if (h.equalsIgnoreCase(Constants.US_MD1IMG_SHA256)) return Constants.REGION_US;
        if (h.equalsIgnoreCase(Constants.STOCK_MD1IMG_SHA256)) return Constants.REGION_STOCK;
        return Constants.REGION_UNKNOWN;
    }

    public static String otherRegion(String current) {
        if (Constants.REGION_US.equals(current)) return Constants.REGION_STOCK;
        if (Constants.REGION_STOCK.equals(current)) return Constants.REGION_US;
        return null;
    }
}

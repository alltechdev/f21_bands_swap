package com.flipphoneguy.f21bands;

public final class Constants {

    private Constants() {}

    public static final String REGION_US = "us";
    public static final String REGION_STOCK = "stock";
    public static final String REGION_UNKNOWN = "unknown";

    public static final String[] PARTITION_FILES = {
        "md1img_a.bin",
        "nvcfg.bin",
        "nvdata.bin",
        "nvram.bin",
    };

    public static final String[] PARTITION_DEVICES = {
        "/dev/block/by-name/md1img_a",
        "/dev/block/by-name/nvcfg",
        "/dev/block/by-name/nvdata",
        "/dev/block/by-name/nvram",
    };

    public static final long[] PARTITION_SIZES = {
        104857600L,   // md1img_a — 100 MB
        33554432L,    // nvcfg    — 32 MB
        67108864L,    // nvdata   — 64 MB
        67108864L,    // nvram    — 64 MB
    };

    public static final long DD_BS = 4L * 1024 * 1024;

    /** SHA-256 of the first PARTITION_SIZES[0] bytes of /dev/block/by-name/md1img_a for US bands. */
    public static final String US_MD1IMG_SHA256 =
        "784c350304597f71c5cc34a237422bae4cd417495afa7316e05557b7605077b8";

    /** SHA-256 of the first PARTITION_SIZES[0] bytes of /dev/block/by-name/md1img_a for stock bands. */
    public static final String STOCK_MD1IMG_SHA256 =
        "68ec5dcb9f38a5f59f5a700841d76fe479175285512f0c76b64f7834d5d9b2ae";

    public static final String DOWNLOAD_BASE =
        "https://raw.githubusercontent.com/flipphoneguy/f21_bands_swap/main/bands/";

    public static String urlForRegion(String region) {
        return DOWNLOAD_BASE + region + ".tar.xz";
    }

    public static String blobFileNameForRegion(String region) {
        return "bands_" + region + ".tar.xz";
    }

    public static String prettyRegion(String region) {
        if (REGION_US.equals(region)) return "US";
        if (REGION_STOCK.equals(region)) return "stock";
        return "unknown";
    }
}

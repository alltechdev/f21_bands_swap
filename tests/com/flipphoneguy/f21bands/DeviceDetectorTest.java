package com.flipphoneguy.f21bands;

public final class DeviceDetectorTest {
    public static void main(String[] args) {
        check(DeviceDetector.Model.F21, "Qin", "Qin", "F21 Pro");
        check(DeviceDetector.Model.F21, "unknown", "unknown", "generic", "f21_pro");
        check(DeviceDetector.Model.F21, "DuoQin", "Qin", "DuoQin F21 Pro");
        check(DeviceDetector.Model.M5, "TIQ", "TIQ", "M5");
        check(DeviceDetector.Model.M5, "unknown", "unknown", "TIQ-M5");
        check(DeviceDetector.Model.M5, "unknown", "Tiq", "generic", "m5");
        check(DeviceDetector.Model.UNKNOWN, "Other", "Other", "M5");
        check(DeviceDetector.Model.UNKNOWN, "Qin", "Qin", "F21 Pro Max");
        check(DeviceDetector.Model.UNKNOWN, "TIQ", "TIQ", "M50");
        check(DeviceDetector.Model.UNKNOWN, "TIQ", "TIQ", "M5", "F21 Pro");
        check(DeviceDetector.Model.UNKNOWN, null, null, (String) null);
        check(DeviceDetector.Model.UNKNOWN, "", "", "");
        check(DeviceDetector.Model.M5, "M5", "alps", "M5", "k61v1_64_bsp", "vnd_k61v1_64_bsp");
        check(DeviceDetector.Model.UNKNOWN, "Other", "alps", "M5", "k61v1_64_bsp");
        check(DeviceDetector.Model.UNKNOWN, "M5", "alps", "M50");
        check(DeviceDetector.Model.UNKNOWN, "M5", "alps", "M5", "f21pro");
        System.out.println("16 device detection cases passed");
    }

    private static void check(DeviceDetector.Model expected, String manufacturer,
                              String brand, String... identifiers) {
        DeviceDetector.Model actual = DeviceDetector.classify(manufacturer, brand, identifiers);
        if (actual != expected) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}

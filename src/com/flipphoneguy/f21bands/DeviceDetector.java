package com.flipphoneguy.f21bands;

import android.os.Build;
import java.util.Locale;

/** Model identification only; this does not establish partition compatibility. */
public final class DeviceDetector {
    public enum Model { F21, M5, UNKNOWN }

    private DeviceDetector() {}

    // Session-only evidence from the live modem, never from an imported bundle.
    private static volatile String verifiedF21Identity;

    private static String identity() {
        return Build.MANUFACTURER + "|" + Build.BRAND + "|" + Build.MODEL
            + "|" + Build.DEVICE + "|" + Build.PRODUCT;
    }

    static void observeLiveModemHash(String hash) {
        if (Constants.US_MD1IMG_SHA256.equalsIgnoreCase(hash)
                || Constants.STOCK_MD1IMG_SHA256.equalsIgnoreCase(hash)) {
            verifiedF21Identity = identity();
        }
    }

    public static Model detect() {
        int flags = signals(Build.MANUFACTURER, Build.BRAND, Build.MODEL,
                            Build.DEVICE, Build.PRODUCT);
        if (flags == 0 && identity().equals(verifiedF21Identity)) return Model.F21;
        return modelForSignals(flags);
    }

    // Package-private so identification can be tested without Android hardware.
    static Model classify(String manufacturer, String brand, String... identifiers) {
        return modelForSignals(signals(manufacturer, brand, identifiers));
    }

    private static Model modelForSignals(int flags) {
        if (flags == 1) return Model.F21;
        if (flags == 2) return Model.M5;
        return Model.UNKNOWN; // No evidence, or contradictory model identifiers.
    }

    private static int signals(String manufacturer, String brand, String... identifiers) {
        boolean tiq = normalize(manufacturer).equals("tiq")
            || normalize(brand).equals("tiq");
        // Observed on the physical M5: manufacturer M5, brand alps, model M5.
        boolean m5Vendor = normalize(manufacturer).equals("m5")
            && normalize(brand).equals("alps");
        boolean f21 = false;
        boolean m5 = false;
        for (String value : identifiers) {
            String id = normalize(value);
            f21 |= id.equals("f21pro") || id.equals("qinf21pro")
                || id.equals("duoqinf21pro");
            m5 |= id.equals("tiqm5") || ((tiq || m5Vendor) && id.equals("m5"));
        }
        return (f21 ? 1 : 0) | (m5 ? 2 : 0);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[ _-]", "");
    }

    public static String label(Model model) {
        switch (model) {
            case F21: return "DuoQin F21 Pro";
            case M5: return "Tiq M5";
            default: return "Unrecognized (" + Build.MANUFACTURER + " / " + Build.MODEL + ")";
        }
    }

}

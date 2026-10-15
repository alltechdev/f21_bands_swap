package com.flipphoneguy.f21bands;

import android.os.Build;
import java.util.Locale;

/** Model identification only; this does not establish partition compatibility. */
public final class DeviceDetector {
    public enum Model { F21, M5, UNKNOWN }

    private DeviceDetector() {}

    public static Model detect() {
        return classify(Build.MANUFACTURER, Build.BRAND, Build.MODEL,
                        Build.DEVICE, Build.PRODUCT);
    }

    // Package-private so identification can be tested without Android hardware.
    static Model classify(String manufacturer, String brand, String... identifiers) {
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
        if (f21 == m5) return Model.UNKNOWN;
        return f21 ? Model.F21 : Model.M5;
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

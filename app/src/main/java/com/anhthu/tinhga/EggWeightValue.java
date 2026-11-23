package com.anhthu.tinhga;

import java.text.NumberFormat;
import java.util.Locale;

/** Three-position egg-tray weight readings, in grams. */
public final class EggWeightValue {
    public static final double MIN_GRAMS = 5000.0;
    public static final double MAX_GRAMS = 18000.0;
    private EggWeightValue() {}

    public static double parse(String raw) {
        if (raw == null) return Double.NaN;
        String text = raw.trim().replaceAll("[\\s\\u00a0]", "");
        if (text.isEmpty()) return Double.NaN;
        try {
            String normalized=text;
            // Accept grouped weights such as 11.200 / 11,200.
            if (text.matches("[0-9]{1,3}([.,][0-9]{3})+")) {
                normalized=text.replace(".", "").replace(",", "");
            } else if (text.indexOf('.')>=0 && text.indexOf(',')>=0) {
                // Whichever separator appears last is the decimal separator.
                int dot=text.lastIndexOf('.');
                int comma=text.lastIndexOf(',');
                normalized=dot>comma ? text.replace(",", "") : text.replace(".", "").replace(',', '.');
            } else {
                normalized=text.replace(',', '.');
            }
            double number=Double.parseDouble(normalized);
            return Double.isFinite(number) && number>=MIN_GRAMS && number<=MAX_GRAMS
                ? number : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    public static String display(double number) {
        NumberFormat formatter=NumberFormat.getNumberInstance(new Locale("vi","VN"));
        formatter.setGroupingUsed(true);
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(number);
    }
}

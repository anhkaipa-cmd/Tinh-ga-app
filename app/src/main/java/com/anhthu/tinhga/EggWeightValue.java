package com.anhthu.tinhga;

import java.text.NumberFormat;
import java.util.Locale;

/** Parsing and formatting egg-weight samples; do not impose an arbitrary 500g limit. */
public final class EggWeightValue {
    private EggWeightValue() {}

    public static double parse(String raw) {
        if (raw == null) return Double.NaN;
        String text = raw.trim().replace(',', '.');
        if (text.isEmpty()) return Double.NaN;
        try {
            double number = Double.parseDouble(text);
            return number > 0 && Double.isFinite(number) ? number : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    public static String display(double number) {
        NumberFormat formatter = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        formatter.setGroupingUsed(true);
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(number);
    }
}

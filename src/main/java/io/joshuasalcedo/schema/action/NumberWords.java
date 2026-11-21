package io.joshuasalcedo.schema.action;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A number in English words, as written on cheques and invoices: {@code 1250.5} is
 * {@code "one thousand two hundred fifty and 50/100"}. Whole numbers have no fraction part; negatives start with
 * "minus". Up to 999 trillion.
 */
final class NumberWords {

    private static final String[] ONES = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
    private static final String[] TENS = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};
    private static final String[] SCALES = {"", " thousand", " million", " billion", " trillion"};

    private NumberWords() {
    }

    static String of(BigDecimal value) {
        BigDecimal v = value.setScale(2, RoundingMode.HALF_UP);
        boolean negative = v.signum() < 0;
        v = v.abs();
        long whole = v.setScale(0, RoundingMode.DOWN).longValueExact();
        int cents = v.subtract(BigDecimal.valueOf(whole)).movePointRight(2).intValue();
        if (whole >= 1_000_000_000_000_000L) {
            throw new IllegalArgumentException(value + " is too large to write in words");
        }
        String words = whole == 0 ? "zero" : groups(whole);
        return (negative ? "minus " : "") + words + (cents == 0 ? "" : " and " + String.format("%02d", cents) + "/100");
    }

    private static String groups(long n) {
        StringBuilder out = new StringBuilder();
        int scale = 0;
        while (n > 0) {
            int group = (int) (n % 1000);
            if (group > 0) {
                String words = hundreds(group) + SCALES[scale];
                out.insert(0, out.isEmpty() ? words : words + " ");
            }
            n /= 1000;
            scale++;
        }
        return out.toString();
    }

    private static String hundreds(int n) {
        StringBuilder out = new StringBuilder();
        if (n >= 100) {
            out.append(ONES[n / 100]).append(" hundred");
            n %= 100;
            if (n > 0) {
                out.append(' ');
            }
        }
        if (n >= 20) {
            out.append(TENS[n / 10]);
            if (n % 10 > 0) {
                out.append('-').append(ONES[n % 10]);
            }
        } else if (n > 0 || out.isEmpty()) {
            out.append(ONES[n]);
        }
        return out.toString();
    }
}

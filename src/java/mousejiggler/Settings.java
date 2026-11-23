package mousejiggler;

import java.math.BigDecimal;

/**
 * Immutable application settings shared between the UI and the worker thread.
 */
public record Settings(int moveIntervalMs, int returnIntervalMs, int distance, boolean returnCursor) {

    public static final int MIN_VALUE = 1;
    public static final int MAX_VALUE = 999999;

    public static Settings defaults() {
        return new Settings(60000, 100, 2, true);
    }

    /**
     * Normalizes raw user input to an integer in {@code 1..999999}:
     * negative values become positive, the fractional part is truncated,
     * zero and values below 1 become 1, values above 999999 are clamped,
     * and empty or unfinished input falls back to the last valid value.
     */
    public static int normalize(String rawValue, int fallback) {
        int safeFallback = clamp(fallback);
        if (rawValue == null) {
            return safeFallback;
        }
        String text = rawValue.strip().replace(',', '.');
        if (text.isEmpty()) {
            return safeFallback;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(text);
        } catch (NumberFormatException e) {
            return safeFallback;
        }
        if (value.signum() < 0) {
            value = value.negate();
        }
        // intValue() truncates toward zero, dropping the fractional part.
        return clamp(value.intValue());
    }

    private static int clamp(int value) {
        return Math.min(MAX_VALUE, Math.max(MIN_VALUE, value));
    }
}

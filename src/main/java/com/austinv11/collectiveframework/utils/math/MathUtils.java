package com.austinv11.collectiveframework.utils.math;

/**
 * Class to simplify manipulation of numbers
 */
public class MathUtils {

    /**
     * Formerly the iteration cap used by {@link #findLowestCommonDenominator(double...)}.
     *
     * @deprecated The cap was the reason a ratio such as 4096:2048 never reduced; the divisor search is now
     *             Euclidean and unbounded. Kept only so existing readers still compile.
     */
    @Deprecated
    public static int MAX_PASSES = 1024;

    /**
     * Checks if the specified number is a whole number
     * 
     * @param number Number to check
     * @return If the number is a whole number
     */
    public static boolean isWholeNumber(Number number) {
        if (number == null) return false;
        double value = number.doubleValue();
        // Compared intValue() with longValue(), which is true for every value that fits in an int - including
        // fractions (CODE_REVIEW §3.14).
        return !Double.isNaN(value) && !Double.isInfinite(value) && value == Math.rint(value);
    }

    /**
     * Finds the smallest number in a set of numbers
     * 
     * @param numbers The numbers to search through
     * @return The smallest number
     */
    public static double getMin(double... numbers) {
        if (numbers == null || numbers.length == 0)
            throw new IllegalArgumentException("getMin needs at least one value");
        double min = numbers[0];
        for (double n : numbers) if (n < min) min = n;
        return min;
    }

    /**
     * Finds the largest number in a set of numbers
     * 
     * @param numbers The numbers to search through
     * @return The largest number
     */
    public static double getMax(double... numbers) {
        if (numbers == null || numbers.length == 0)
            throw new IllegalArgumentException("getMax needs at least one value");
        double max = numbers[0];
        for (double n : numbers) if (n > max) max = n;
        return max;
    }

    /**
     * Checks if the specified value is between the provided range
     * 
     * @param min       The minimum of the range
     * @param max       The maximum of the range
     * @param value     The value to test
     * @param inclusive Whether to include the min and max in calculations
     * @return If the value is within the range
     */
    public static boolean isBetween(double min, double max, double value, boolean inclusive) {
        if (inclusive) {
            return value <= max && value >= min;
        }
        return value < max && value > min;
    }

    /**
     * Checks if the given string represents a number
     * 
     * @param string The string
     * @return True if the string is a number
     */
    public static boolean isStringNumber(String string) {
        try {
            Double.parseDouble(string);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if the given number is even
     * 
     * @param number The number
     * @return True if the number is an even number
     */
    public static boolean isEvenNumber(int number) {
        return number % 2 == 0;
    }

    /**
     * Finds the greatest common divisor of the given values.
     *
     * @param values The values
     * @return The greatest common divisor, or {@link Double#NaN} if any value is not a positive whole number
     * @deprecated Misnamed: this never returned a lowest common denominator, and now delegates to
     *             {@link #findGreatestCommonDivisor(double...)}. Use that instead.
     */
    @Deprecated
    public static double findLowestCommonDenominator(double... values) {
        return findGreatestCommonDivisor(values);
    }

    /**
     * Finds the greatest common divisor of the given values, which is what reducing a ratio needs.
     * <p>
     * {@link #findLowestCommonDenominator(double...)} used to return the <em>smallest</em> common divisor at or
     * above 2, so {@code Ratio(1920, 1080)} reduced to 960:540 instead of 16:9, and it gave up after
     * {@code MAX_PASSES} iterations, so a 4096x2048 texture never reduced at all (CODE_REVIEW §3.14).
     *
     * @param values The values; each must be a positive whole number
     * @return The greatest common divisor, or {@link Double#NaN} if any value is not a positive whole number
     */
    public static double findGreatestCommonDivisor(double... values) {
        if (values == null || values.length == 0) return Double.NaN;
        long divisor = 0;
        for (double value : values) {
            if (!isWholeNumber(value) || value <= 0 || value > Long.MAX_VALUE) return Double.NaN;
            divisor = greatestCommonDivisor(divisor, (long) value);
        }
        return divisor <= 0 ? Double.NaN : divisor;
    }

    private static long greatestCommonDivisor(long a, long b) {
        while (b != 0) {
            long remainder = a % b;
            a = b;
            b = remainder;
        }
        return Math.abs(a);
    }
}

package com.austinv11.collectiveframework.utils.math;

/**
 * This is a helper object to handle ratios (i.e. 3:1, 2:5, etc)
 */
public class Ratio {

    private double[] originalValues;
    private double[] reducedValues;
    private double lcd;

    /**
     * Constructs a ratio
     * 
     * @param values The ratio values
     */
    public Ratio(double... values) {
        originalValues = values.clone();
        reducedValues = new double[values.length];
        // The greatest common divisor, not the smallest common divisor: the old helper made a 1920x1080 ratio
        // reduce to 960:540 (CODE_REVIEW §3.14).
        double divisor = MathUtils.findGreatestCommonDivisor(values);
        this.lcd = divisor;
        if (Double.isNaN(divisor)) System.arraycopy(values, 0, reducedValues, 0, values.length);
        else {
            for (int i = 0; i < values.length; i++) reducedValues[i] = values[i] / divisor;
        }
    }

    /**
     * Gets the provided ratio
     * 
     * @return The provided ratio
     */
    public double[] getOriginalRatio() {
        // Returns a copy: handing out the internal array let a caller mutate the ratio (CODE_REVIEW §3.14).
        return originalValues.clone();
    }

    /**
     * Gets the ratio reduced by the lcd (if it exists)
     * 
     * @return The reduced ratio
     */
    public double[] getReducedRatio() {
        return reducedValues.clone();
    }

    /**
     * Retrieves the lcd for the ratio
     * 
     * @return The lcd
     */
    public double getLCD() {
        return lcd;
    }
}

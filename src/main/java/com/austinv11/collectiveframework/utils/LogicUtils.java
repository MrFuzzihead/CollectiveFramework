package com.austinv11.collectiveframework.utils;

/**
 * A few helper functions for logic. Has all the basic logic gates.
 * Some redundancies for the sake of completion.
 */
public class LogicUtils {

    /**
     * A buffer gate
     * 
     * @param bool Input
     * @return Output
     */
    public static boolean buffer(boolean bool) {
        return bool;
    }

    /**
     * A not gate
     * 
     * @param bool Input
     * @return Output
     */
    public static boolean not(boolean bool) {
        return !bool;
    }

    /**
     * An or gate
     * 
     * @param bools Input
     * @return Output
     */
    public static boolean or(boolean... bools) {
        boolean total = false;
        for (boolean bool : bools) total = total || bool;
        return total;
    }

    /**
     * A nor gate
     * 
     * @param bools Input
     * @return Output
     */
    public static boolean nor(boolean... bools) {
        return !or(bools);
    }

    /**
     * An and gate
     * 
     * @param bools Input
     * @return Output
     */
    public static boolean and(boolean... bools) {
        boolean total = true;
        for (boolean bool : bools) total = total && bool;
        return total;
    }

    /**
     * A nand gate
     * 
     * @param bools Input
     * @return Output
     */
    public static boolean nand(boolean... bools) {
        return !and(bools);
    }

    /**
     * An xor gate
     * 
     * @param bools Input
     * @return Output
     */
    public static boolean xor(boolean... bools) {
        // True when an odd number of inputs are true. The previous two-input short circuit returned false for
        // xor(true, true, true) (CODE_REVIEW §2.16).
        boolean odd = false;
        for (boolean bool : bools) if (bool) odd = !odd;
        return odd;
    }

    /**
     * An xnor gate
     * 
     * @param bools Input
     * @return Output
     */
    public static boolean xnor(boolean... bools) {
        return !xor(bools);
    }
}

package com.austinv11.collectiveframework.utils;

import java.util.Arrays;

/**
 * A class with helpful methods for handling arrays
 */
public class ArrayUtils {

    /**
     * Finds the index of the specified key in an array
     * 
     * @param array The array to search
     * @param key   The key to search for
     * @return The index, or -1 if the key was not found
     */
    public static int indexOf(Object[] array, Object key) {
        for (int i = 0; i < array.length; i++) {
            // Used to NPE on a null element (CODE_REVIEW §3.13).
            if (java.util.Objects.equals(array[i], key)) return i;
        }
        return -1;
    }

    /**
     * Finds the index of the specified key in an array
     * 
     * @param array The array to search
     * @param key   The key to search for
     * @return The index, or -1 if the key was not found
     */
    public static int indexOf(byte[] array, byte key) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == (key)) return i;
        }
        return -1;
    }

    /**
     * Finds the index of the specified key in an array
     * 
     * @param array The array to search
     * @param key   The key to search for
     * @return The index, or -1 if the key was not found
     */
    public static int indexOf(short[] array, short key) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == (key)) return i;
        }
        return -1;
    }

    /**
     * Finds the index of the specified key in an array
     * 
     * @param array The array to search
     * @param key   The key to search for
     * @return The index, or -1 if the key was not found
     */
    public static int indexOf(int[] array, int key) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == (key)) return i;
        }
        return -1;
    }

    /**
     * Finds the index of the specified key in an array
     * 
     * @param array The array to search
     * @param key   The key to search for
     * @return The index, or -1 if the key was not found
     */
    public static int indexOf(long[] array, long key) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == (key)) return i;
        }
        return -1;
    }

    /**
     * Finds the index of the specified key in an array
     * 
     * @param array The array to search
     * @param key   The key to search for
     * @return The index, or -1 if the key was not found
     */
    public static int indexOf(float[] array, float key) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == (key)) return i;
        }
        return -1;
    }

    /**
     * Finds the index of the specified key in an array
     * 
     * @param array The array to search
     * @param key   The key to search for
     * @return The index, or -1 if the key was not found
     */
    public static int indexOf(double[] array, double key) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == (key)) return i;
        }
        return -1;
    }

    /**
     * Finds the index of the specified key in an array
     * 
     * @param array The array to search
     * @param key   The key to search for
     * @return The index, or -1 if the key was not found
     */
    public static int indexOf(boolean[] array, boolean key) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == (key)) return i;
        }
        return -1;
    }

    /**
     * Finds the index of the specified key in an array
     * 
     * @param array The array to search
     * @param key   The key to search for
     * @return The index, or -1 if the key was not found
     */
    public static int indexOf(char[] array, char key) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == (key)) return i;
        }
        return -1;
    }

    /**
     * Finds the index of the specified key in an array
     * 
     * @param array      The array to search
     * @param key        The key to search for
     * @param startIndex The index to start searching from
     * @return The index, or -1 if the key was not found
     */
    public static int indexOf(Object[] array, Object key, int startIndex) {
        for (int i = startIndex; i < array.length; i++) {
            if (java.util.Objects.equals(array[i], key)) return i;
        }
        return -1;
    }

    /**
     * Finds the index of the specified key in an array
     * 
     * @param array      The array to search
     * @param key        The key to search for
     * @param startIndex The index to start searching from
     * @return The index, or -1 if the key was not found
     */
    public static int indexOf(byte[] array, byte key, int startIndex) {
        for (int i = startIndex; i < array.length; i++) {
            if (array[i] == (key)) return i;
        }
        return -1;
    }

    /**
     * Finds the index of the specified key in an array
     * 
     * @param array      The array to search
     * @param key        The key to search for
     * @param startIndex The index to start searching from
     * @return The index, or -1 if the key was not found
     */
    public static int indexOf(short[] array, short key, int startIndex) {
        for (int i = startIndex; i < array.length; i++) {
            if (array[i] == (key)) return i;
        }
        return -1;
    }

    /**
     * Finds the index of the specified key in an array
     * 
     * @param array      The array to search
     * @param key        The key to search for
     * @param startIndex The index to start searching from
     * @return The index, or -1 if the key was not found
     */
    public static int indexOf(int[] array, int key, int startIndex) {
        for (int i = startIndex; i < array.length; i++) {
            if (array[i] == (key)) return i;
        }
        return -1;
    }

    /**
     * Finds the index of the specified key in an array
     * 
     * @param array      The array to search
     * @param key        The key to search for
     * @param startIndex The index to start searching from
     * @return The index, or -1 if the key was not found
     */
    public static int indexOf(long[] array, long key, int startIndex) {
        for (int i = startIndex; i < array.length; i++) {
            if (array[i] == (key)) return i;
        }
        return -1;
    }

    /**
     * Finds the index of the specified key in an array
     * 
     * @param array      The array to search
     * @param key        The key to search for
     * @param startIndex The index to start searching from
     * @return The index, or -1 if the key was not found
     */
    public static int indexOf(float[] array, float key, int startIndex) {
        for (int i = startIndex; i < array.length; i++) {
            if (array[i] == (key)) return i;
        }
        return -1;
    }

    /**
     * Finds the index of the specified key in an array
     * 
     * @param array      The array to search
     * @param key        The key to search for
     * @param startIndex The index to start searching from
     * @return The index, or -1 if the key was not found
     */
    public static int indexOf(double[] array, double key, int startIndex) {
        for (int i = startIndex; i < array.length; i++) {
            if (array[i] == (key)) return i;
        }
        return -1;
    }

    /**
     * Finds the index of the specified key in an array
     * 
     * @param array      The array to search
     * @param key        The key to search for
     * @param startIndex The index to start searching from
     * @return The index, or -1 if the key was not found
     */
    public static int indexOf(boolean[] array, boolean key, int startIndex) {
        for (int i = startIndex; i < array.length; i++) {
            if (array[i] == (key)) return i;
        }
        return -1;
    }

    /**
     * Finds the index of the specified key in an array
     * 
     * @param array      The array to search
     * @param key        The key to search for
     * @param startIndex The index to start searching from
     * @return The index, or -1 if the key was not found
     */
    public static int indexOf(char[] array, char key, int startIndex) {
        for (int i = startIndex; i < array.length; i++) {
            if (array[i] == (key)) return i;
        }
        return -1;
    }

    /**
     * Gets the value from an array, prevents {@link java.lang.ArrayIndexOutOfBoundsException} by wrapping
     * 
     * @param array The array to use
     * @param index The index to get the value from
     * @return The value
     */
    public static <T> T wrappedRetrieve(T[] array, int index) {
        // A negative index used to fall straight through to array[index] and throw, and a zero-length array
        // made the wrapping loop spin forever (CODE_REVIEW §3.13).
        if (array.length == 0) throw new IllegalArgumentException("Cannot wrap around an empty array");
        int wrapped = index % array.length;
        if (wrapped < 0) wrapped += array.length;
        return array[wrapped];
    }

    /**
     * Gets the value from an array, prevents {@link java.lang.ArrayIndexOutOfBoundsException} by wrapping
     * 
     * @param array The array to use
     * @param index The index to get the value from
     * @return The value
     */
    public static byte wrappedRetrieve(byte[] array, int index) {
        // A negative index used to fall straight through to array[index] and throw, and a zero-length array
        // made the wrapping loop spin forever (CODE_REVIEW §3.13).
        if (array.length == 0) throw new IllegalArgumentException("Cannot wrap around an empty array");
        int wrapped = index % array.length;
        if (wrapped < 0) wrapped += array.length;
        return array[wrapped];
    }

    /**
     * Gets the value from an array, prevents {@link java.lang.ArrayIndexOutOfBoundsException} by wrapping
     * 
     * @param array The array to use
     * @param index The index to get the value from
     * @return The value
     */
    public static short wrappedRetrieve(short[] array, int index) {
        // A negative index used to fall straight through to array[index] and throw, and a zero-length array
        // made the wrapping loop spin forever (CODE_REVIEW §3.13).
        if (array.length == 0) throw new IllegalArgumentException("Cannot wrap around an empty array");
        int wrapped = index % array.length;
        if (wrapped < 0) wrapped += array.length;
        return array[wrapped];
    }

    /**
     * Gets the value from an array, prevents {@link java.lang.ArrayIndexOutOfBoundsException} by wrapping
     * 
     * @param array The array to use
     * @param index The index to get the value from
     * @return The value
     */
    public static int wrappedRetrieve(int[] array, int index) {
        // A negative index used to fall straight through to array[index] and throw, and a zero-length array
        // made the wrapping loop spin forever (CODE_REVIEW §3.13).
        if (array.length == 0) throw new IllegalArgumentException("Cannot wrap around an empty array");
        int wrapped = index % array.length;
        if (wrapped < 0) wrapped += array.length;
        return array[wrapped];
    }

    /**
     * Gets the value from an array, prevents {@link java.lang.ArrayIndexOutOfBoundsException} by wrapping
     * 
     * @param array The array to use
     * @param index The index to get the value from
     * @return The value
     */
    public static long wrappedRetrieve(long[] array, int index) {
        // A negative index used to fall straight through to array[index] and throw, and a zero-length array
        // made the wrapping loop spin forever (CODE_REVIEW §3.13).
        if (array.length == 0) throw new IllegalArgumentException("Cannot wrap around an empty array");
        int wrapped = index % array.length;
        if (wrapped < 0) wrapped += array.length;
        return array[wrapped];
    }

    /**
     * Gets the value from an array, prevents {@link java.lang.ArrayIndexOutOfBoundsException} by wrapping
     * 
     * @param array The array to use
     * @param index The index to get the value from
     * @return The value
     */
    public static float wrappedRetrieve(float[] array, int index) {
        // A negative index used to fall straight through to array[index] and throw, and a zero-length array
        // made the wrapping loop spin forever (CODE_REVIEW §3.13).
        if (array.length == 0) throw new IllegalArgumentException("Cannot wrap around an empty array");
        int wrapped = index % array.length;
        if (wrapped < 0) wrapped += array.length;
        return array[wrapped];
    }

    /**
     * Gets the value from an array, prevents {@link java.lang.ArrayIndexOutOfBoundsException} by wrapping
     * 
     * @param array The array to use
     * @param index The index to get the value from
     * @return The value
     */
    public static double wrappedRetrieve(double[] array, int index) {
        // A negative index used to fall straight through to array[index] and throw, and a zero-length array
        // made the wrapping loop spin forever (CODE_REVIEW §3.13).
        if (array.length == 0) throw new IllegalArgumentException("Cannot wrap around an empty array");
        int wrapped = index % array.length;
        if (wrapped < 0) wrapped += array.length;
        return array[wrapped];
    }

    /**
     * Gets the value from an array, prevents {@link java.lang.ArrayIndexOutOfBoundsException} by wrapping
     * 
     * @param array The array to use
     * @param index The index to get the value from
     * @return The value
     */
    public static boolean wrappedRetrieve(boolean[] array, int index) {
        // A negative index used to fall straight through to array[index] and throw, and a zero-length array
        // made the wrapping loop spin forever (CODE_REVIEW §3.13).
        if (array.length == 0) throw new IllegalArgumentException("Cannot wrap around an empty array");
        int wrapped = index % array.length;
        if (wrapped < 0) wrapped += array.length;
        return array[wrapped];
    }

    /**
     * Gets the value from an array, prevents {@link java.lang.ArrayIndexOutOfBoundsException} by wrapping
     * 
     * @param array The array to use
     * @param index The index to get the value from
     * @return The value
     */
    public static char wrappedRetrieve(char[] array, int index) {
        // A negative index used to fall straight through to array[index] and throw, and a zero-length array
        // made the wrapping loop spin forever (CODE_REVIEW §3.13).
        if (array.length == 0) throw new IllegalArgumentException("Cannot wrap around an empty array");
        int wrapped = index % array.length;
        if (wrapped < 0) wrapped += array.length;
        return array[wrapped];
    }

    /**
     * Removes any repeating data from the array
     * 
     * @param array The array to remove repeats from
     * @return The new array
     */
    public static <T> T[] removeRepeats(T[] array) {
        // List.contains in a loop was O(n^2) (CODE_REVIEW §3.13); a LinkedHashSet keeps first-seen order in O(n).
        java.util.Set<T> unique = new java.util.LinkedHashSet<T>(Arrays.asList(array));
        return unique.toArray(Arrays.copyOf(array, unique.size()));
    }

    /**
     * Removes any repeating data from the array
     * 
     * @param array The array to remove repeats from
     * @return The new array
     */
    public static byte[] removeRepeats(byte[] array) {
        java.util.Set<Byte> unique = new java.util.LinkedHashSet<Byte>(array.length);
        for (byte value : array) unique.add(value);
        byte[] newArray = new byte[unique.size()];
        int i = 0;
        for (Byte value : unique) newArray[i++] = value;
        return newArray;
    }

    /**
     * Removes any repeating data from the array
     * 
     * @param array The array to remove repeats from
     * @return The new array
     */
    public static short[] removeRepeats(short[] array) {
        java.util.Set<Short> unique = new java.util.LinkedHashSet<Short>(array.length);
        for (short value : array) unique.add(value);
        short[] newArray = new short[unique.size()];
        int i = 0;
        for (Short value : unique) newArray[i++] = value;
        return newArray;
    }

    /**
     * Removes any repeating data from the array
     * 
     * @param array The array to remove repeats from
     * @return The new array
     */
    public static int[] removeRepeats(int[] array) {
        java.util.Set<Integer> unique = new java.util.LinkedHashSet<Integer>(array.length);
        for (int value : array) unique.add(value);
        int[] newArray = new int[unique.size()];
        int i = 0;
        for (Integer value : unique) newArray[i++] = value;
        return newArray;
    }

    /**
     * Removes any repeating data from the array
     * 
     * @param array The array to remove repeats from
     * @return The new array
     */
    public static long[] removeRepeats(long[] array) {
        java.util.Set<Long> unique = new java.util.LinkedHashSet<Long>(array.length);
        for (long value : array) unique.add(value);
        long[] newArray = new long[unique.size()];
        int i = 0;
        for (Long value : unique) newArray[i++] = value;
        return newArray;
    }

    /**
     * Removes any repeating data from the array
     * 
     * @param array The array to remove repeats from
     * @return The new array
     */
    public static float[] removeRepeats(float[] array) {
        java.util.Set<Float> unique = new java.util.LinkedHashSet<Float>(array.length);
        for (float value : array) unique.add(value);
        float[] newArray = new float[unique.size()];
        int i = 0;
        for (Float value : unique) newArray[i++] = value;
        return newArray;
    }

    /**
     * Removes any repeating data from the array
     * 
     * @param array The array to remove repeats from
     * @return The new array
     */
    public static double[] removeRepeats(double[] array) {
        java.util.Set<Double> unique = new java.util.LinkedHashSet<Double>(array.length);
        for (double value : array) unique.add(value);
        double[] newArray = new double[unique.size()];
        int i = 0;
        for (Double value : unique) newArray[i++] = value;
        return newArray;
    }

    /**
     * Removes any repeating data from the array
     * 
     * @param array The array to remove repeats from
     * @return The new array
     */
    public static boolean[] removeRepeats(boolean[] array) {
        java.util.Set<Boolean> unique = new java.util.LinkedHashSet<Boolean>(array.length);
        for (boolean value : array) unique.add(value);
        boolean[] newArray = new boolean[unique.size()];
        int i = 0;
        for (Boolean value : unique) newArray[i++] = value;
        return newArray;
    }

    /**
     * Removes any repeating data from the array
     * 
     * @param array The array to remove repeats from
     * @return The new array
     */
    public static char[] removeRepeats(char[] array) {
        java.util.Set<Character> unique = new java.util.LinkedHashSet<Character>(array.length);
        for (char value : array) unique.add(value);
        char[] newArray = new char[unique.size()];
        int i = 0;
        for (Character value : unique) newArray[i++] = value;
        return newArray;
    }

    /**
     * Performs the {@link java.lang.String#trim()} method on all strings in an array
     * 
     * @param array The array to trim
     * @return The trimmed array
     */
    public static String[] trimAll(String[] array) {
        String[] trimmed = new String[array.length];
        for (int i = 0; i < array.length; i++) {
            trimmed[i] = array[i].trim();
        }
        return trimmed;
    }

    /**
     * Converts an object array to the primitive type
     * 
     * @param array The object array
     * @return The primitive array
     */
    public static byte[] convertObjectArrayToByte(Object[] array) {
        byte[] primitives = new byte[array.length];
        for (int i = 0; i < array.length; i++) primitives[i] = (byte) (Byte) array[i];
        return primitives;
    }

    /**
     * Converts an object array to the primitive type
     * 
     * @param array The object array
     * @return The primitive array
     */
    public static short[] convertObjectArrayToShort(Object[] array) {
        short[] primitives = new short[array.length];
        for (int i = 0; i < array.length; i++) primitives[i] = (short) (Short) array[i];
        return primitives;
    }

    /**
     * Converts an object array to the primitive type
     * 
     * @param array The object array
     * @return The primitive array
     */
    public static int[] convertObjectArrayToInt(Object[] array) {
        int[] primitives = new int[array.length];
        for (int i = 0; i < array.length; i++) primitives[i] = (int) (Integer) array[i];
        return primitives;
    }

    /**
     * Converts an object array to the primitive type
     * 
     * @param array The object array
     * @return The primitive array
     */
    public static long[] convertObjectArrayToLong(Object[] array) {
        long[] primitives = new long[array.length];
        for (int i = 0; i < array.length; i++) primitives[i] = (long) (Long) array[i];
        return primitives;
    }

    /**
     * Converts an object array to the primitive type
     * 
     * @param array The object array
     * @return The primitive array
     */
    public static float[] convertObjectArrayToFloat(Object[] array) {
        float[] primitives = new float[array.length];
        for (int i = 0; i < array.length; i++) primitives[i] = (float) (Float) array[i];
        return primitives;
    }

    /**
     * Converts an object array to the primitive type
     * 
     * @param array The object array
     * @return The primitive array
     */
    public static double[] convertObjectArrayToDouble(Object[] array) {
        double[] primitives = new double[array.length];
        for (int i = 0; i < array.length; i++) primitives[i] = (double) (Double) array[i];
        return primitives;
    }

    /**
     * Converts an object array to the primitive type
     * 
     * @param array The object array
     * @return The primitive array
     */
    public static boolean[] convertObjectArrayToBoolean(Object[] array) {
        boolean[] primitives = new boolean[array.length];
        for (int i = 0; i < array.length; i++) primitives[i] = (boolean) (Boolean) array[i];
        return primitives;
    }

    /**
     * Converts an object array to the primitive type
     * 
     * @param array The object array
     * @return The primitive array
     */
    public static char[] convertObjectArrayToChar(Object[] array) {
        char[] primitives = new char[array.length];
        for (int i = 0; i < array.length; i++) primitives[i] = (char) (Character) array[i];
        return primitives;
    }
}

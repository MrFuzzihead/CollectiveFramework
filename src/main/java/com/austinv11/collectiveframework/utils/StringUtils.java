package com.austinv11.collectiveframework.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * A class for manipulating Strings
 */
public class StringUtils {

    /**
     * Converts (any) list into a string, useful with the {@link WebUtils#readURL(String)} method
     * 
     * @param list The list
     * @return The String
     */
    public static String stringFromList(List list) {
        StringBuilder val = new StringBuilder();
        for (Object o : list) val.append(o)
            .append('\n');
        return val.toString();
    }

    /**
     * Converts (any) array into a string, useful with the {@link WebUtils#readURL(String)} method
     * 
     * @param array The list
     * @return The String
     */
    public static <T> String stringFromArray(T[] array) {
        StringBuilder val = new StringBuilder();
        for (T o : array) val.append(o)
            .append('\n');
        return val.toString();
    }

    /**
     * Gets a string with the provided amount of the provided character
     * 
     * @param character Character for the string
     * @param amount    Amount for the character to be repeated
     * @return The String
     */
    public static String repeatChar(char character, int amount) {
        StringBuilder val = new StringBuilder(Math.max(0, amount));
        for (int i = 0; i < amount; i++) val.append(character);
        return val.toString();
    }

    /**
     * Gets a string with the provided amount of the provided string
     * 
     * @param string String to repeat
     * @param amount Amount for the character to be repeated
     * @return The String
     */
    public static String repeatString(String string, int amount) {
        if (string == null || amount <= 0) return "";
        StringBuilder val = new StringBuilder(string.length() * amount);
        for (int i = 0; i < amount; i++) val.append(string);
        return val.toString();
    }

    /**
     * Creates a list from an input stream where each entry is due to a new line character
     * 
     * @param stream The input stream
     * @return The list of strings
     * @throws IOException
     */
    public static List<String> getStringsFromStream(InputStream stream) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(stream));
        String temp;
        List<String> read = new ArrayList<String>();
        while ((temp = in.readLine()) != null) read.add(temp);
        in.close();
        return read;
    }

    /**
     * Counts the number of occurrences of a string within another
     * 
     * @param string     The string to look in
     * @param toFind     The string to look for
     * @param ignoreCase Whether to ignore case
     * @return The amount of occurrences
     */
    public static int countFor(String string, String toFind, boolean ignoreCase) {
        // Used String.replaceFirst with the search term as a regex, so any metacharacter was interpreted as a
        // pattern and an empty search term looped forever (CODE_REVIEW §3.14).
        if (string == null || toFind == null || toFind.isEmpty()) return 0;
        String haystack = ignoreCase ? string.toLowerCase(java.util.Locale.ROOT) : string;
        String needle = ignoreCase ? toFind.toLowerCase(java.util.Locale.ROOT) : toFind;
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }

    /**
     * Counts the number of occurrences of a string within another,
     * same as {@link #countFor(String,String,boolean)} with ignoreCase as true
     * 
     * @param string The string to look in
     * @param toFind The string to look for
     * @return The amount of occurrences
     */
    public static int countFor(String string, String toFind) {
        return countFor(string, toFind, false);
    }

    /**
     * Replaces the first instance of a string with another, preserving the case for each character
     * 
     * @param string        The string to replace in
     * @param toReplace     The portion of the string to replace
     * @param toReplaceWith The string to replace with
     * @return The new string
     */
    public static String replaceFirstPreservingCase(String string, String toReplace, String toReplaceWith) {
        if (string == null || toReplace == null || toReplaceWith == null) return string;
        if (toReplace.isEmpty()) return string;

        int index = string.toLowerCase(java.util.Locale.ROOT)
            .indexOf(toReplace.toLowerCase(java.util.Locale.ROOT));
        if (index == -1) return string;

        // substring(index, toReplace.length()) threw StringIndexOutOfBoundsException for any match past the
        // start of the string (CODE_REVIEW §1.10).
        String found = string.substring(index, index + toReplace.length());
        char[] foundChars = found.toCharArray();
        char[] replacementChars = toReplaceWith.toCharArray();
        for (int i = 0; i < foundChars.length && i < replacementChars.length; i++) {
            replacementChars[i] = Character.isLowerCase(foundChars[i]) ? Character.toLowerCase(replacementChars[i])
                : Character.toUpperCase(replacementChars[i]);
        }

        // Built by hand: String.replaceFirst treated both the search term and the replacement as regex syntax.
        return string.substring(0, index) + new String(replacementChars) + string.substring(index + found.length());
    }

    /**
     * Replaces all instances of a string with another, preserving the case for each character
     * 
     * @param string        The string to replace in
     * @param toReplace     The portion of the string to replace
     * @param toReplaceWith The string to replace with
     * @return The new string
     */
    public static String replaceAllPreservingCase(String string, String toReplace, String toReplaceWith) {
        if (string == null || toReplace == null || toReplaceWith == null) return string;
        if (toReplace.isEmpty()) return string;
        // Bounded by the original occurrence count, so a replacement that itself contains the search term
        // cannot loop forever.
        int remaining = countFor(string, toReplace, true);
        for (int i = 0; i < remaining; i++) {
            string = replaceFirstPreservingCase(string, toReplace, toReplaceWith);
        }
        return string;
    }
}

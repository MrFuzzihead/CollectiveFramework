package com.austinv11.collectiveframework.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * Regression tests for {@link StringUtils}. See CODE_REVIEW.md for the defects behind the ignored tests.
 */
public class StringUtilsTest {

    @Test
    public void stringFromListAppendsNewlinePerElement() {
        Assert.assertEquals("a\nb\n", StringUtils.stringFromList(Arrays.asList("a", "b")));
    }

    @Test
    public void stringFromArrayAppendsNewlinePerElement() {
        Assert.assertEquals("a\nb\n", StringUtils.stringFromArray(new String[] { "a", "b" }));
    }

    @Test
    public void repeatCharRepeats() {
        Assert.assertEquals("aaa", StringUtils.repeatChar('a', 3));
        Assert.assertEquals("", StringUtils.repeatChar('a', 0));
    }

    @Test
    public void repeatStringRepeats() {
        Assert.assertEquals("abab", StringUtils.repeatString("ab", 2));
        Assert.assertEquals("", StringUtils.repeatString("ab", 0));
    }

    @Test
    public void getStringsFromStreamSplitsOnNewlines() throws IOException {
        ByteArrayInputStream stream = new ByteArrayInputStream("one\ntwo\nthree".getBytes(StandardCharsets.UTF_8));
        List<String> read = StringUtils.getStringsFromStream(stream);
        Assert.assertEquals(Arrays.asList("one", "two", "three"), read);
    }

    @Test
    public void countForCountsLiteralOccurrences() {
        Assert.assertEquals(2, StringUtils.countFor("aXbXc", "X"));
        Assert.assertEquals(3, StringUtils.countFor("aaa", "a"));
        Assert.assertEquals(0, StringUtils.countFor("abc", "z"));
    }

    @Test
    public void countForCanIgnoreCase() {
        Assert.assertEquals(2, StringUtils.countFor("aXbxc", "x", true));
        Assert.assertEquals(1, StringUtils.countFor("aXbxc", "x", false));
    }

    /**
     * CODE_REVIEW §3.14 / StringUtils: {@code countFor} passes its search term straight to
     * {@code String.replaceFirst}, so any regex metacharacter is interpreted as a pattern.
     */
    @Test
    public void countForShouldTreatSearchTermLiterally() {
        // "." appears twice, but replaceFirst(".") matches *any* character.
        Assert.assertEquals(2, StringUtils.countFor("a.b.c", "."));
    }

    @Test
    public void replaceFirstPreservingCaseWorksAtTheStartOfTheString() {
        // index == 0, so the (buggy) substring call happens to be in range.
        Assert.assertEquals("hi there", StringUtils.replaceFirstPreservingCase("hello there", "hello", "hi"));
    }

    @Test
    public void replaceFirstPreservingCaseKeepsUpperAndLowerPatterns() {
        Assert.assertEquals("Hi", StringUtils.replaceFirstPreservingCase("Hello", "hello", "hi"));
    }

    @Test
    public void replaceFirstPreservingCaseReturnsInputWhenAbsent() {
        Assert.assertEquals("hello", StringUtils.replaceFirstPreservingCase("hello", "zzz", "yyy"));
    }

    /**
     * CODE_REVIEW §1.10 - {@code string.substring(index, toReplace.length())} should be
     * {@code substring(index, index + toReplace.length())}. Any match at index &gt; 0 throws
     * {@link StringIndexOutOfBoundsException}.
     */
    @Test
    public void replaceFirstPreservingCaseShouldWorkPastIndexZero() {
        Assert.assertEquals("Hello Earth", StringUtils.replaceFirstPreservingCase("Hello World", "world", "earth"));
    }

    /**
     * CODE_REVIEW §1.10 - {@code replaceAllPreservingCase} loops over {@code countFor} and calls the
     * broken {@code replaceFirstPreservingCase}, so it throws for any non-leading match.
     */
    @Test
    public void replaceAllPreservingCaseShouldReplaceEveryOccurrence() {
        // Case is taken from each occurrence: "Hello" -> "Hi", "hello" -> "hi".
        Assert.assertEquals("Hi hi", StringUtils.replaceAllPreservingCase("Hello hello", "hello", "hi"));
    }

    /** An empty search term used to loop forever in {@code countFor}. */
    @Test
    public void countForWithAnEmptySearchTermReturnsZero() {
        Assert.assertEquals(0, StringUtils.countFor("abc", ""));
        Assert.assertEquals(0, StringUtils.countFor("abc", "", true));
    }

    @Test
    public void countForTreatsRegexMetacharactersLiterally() {
        Assert.assertEquals(2, StringUtils.countFor("a.b.c", "."));
        Assert.assertEquals(1, StringUtils.countFor("a+b", "+"));
        Assert.assertEquals(1, StringUtils.countFor("a$b", "$"));
        Assert.assertEquals(1, StringUtils.countFor("a(b", "("));
    }

    @Test
    public void countForIsNonOverlapping() {
        Assert.assertEquals(1, StringUtils.countFor("aaa", "aa"));
    }

    @Test
    public void replaceFirstPreservingCaseTreatsTheSearchTermLiterally() {
        Assert.assertEquals("a-b", StringUtils.replaceFirstPreservingCase("a.b", ".", "-"));
        Assert.assertEquals("a$b", StringUtils.replaceFirstPreservingCase("a+b", "+", "$"));
    }

    @Test
    public void replaceAllPreservingCaseWithAnEmptySearchTermIsANoOp() {
        Assert.assertEquals("abc", StringUtils.replaceAllPreservingCase("abc", "", "x"));
    }

    @Test
    public void repeatStringHandlesNullAndNonPositiveAmounts() {
        Assert.assertEquals("", StringUtils.repeatString(null, 3));
        Assert.assertEquals("", StringUtils.repeatString("ab", 0));
        Assert.assertEquals("", StringUtils.repeatString("ab", -1));
    }
}

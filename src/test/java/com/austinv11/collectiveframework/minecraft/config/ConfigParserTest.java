package com.austinv11.collectiveframework.minecraft.config;

import java.util.Collections;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * CODE_REVIEW §1.6 - {@code DefaultConfigurationHandler#readFromReader} was a three-state line counter that
 * assumed exactly one comment line, one entry line and one blank line per field, inside a {@code Category { }
 * } block. Anything else - a hand edit, a multi-line comment, a value containing {@code =}, a field skipped
 * while writing - silently dropped every following value, with no logging and no validation.
 * <p>
 * These tests drive the parser through {@link IConfigurationHandler#loadFromString}, which is the public
 * entry point the network layer uses for a synced config.
 */
public class ConfigParserTest {

    /** A config with one field of every type the default proxy handles. */
    @com.austinv11.collectiveframework.minecraft.config.Config
    public static class SampleConfig {

        @Description(comment = "an int", category = "Numbers")
        public static int counter = 1;

        @Description(comment = "a long", category = "Numbers")
        public static long big = 2L;

        @Description(comment = "a double", category = "Numbers")
        public static double ratio = 3.5d;

        @Description(comment = "a boolean", category = "Flags")
        public static boolean enabled = false;

        @Description(comment = "a string", category = "Flags")
        public static String label = "default";

        @Description(comment = "a char", category = "Flags")
        public static char letter = 'x';

        @Description(comment = "an int array", category = "Arrays")
        public static int[] numbers = new int[] { 9 };

        @Description(comment = "a string array", category = "Arrays")
        public static String[] names = new String[] { "default" };
    }

    private final ConfigRegistry.DefaultConfigurationHandler handler = new ConfigRegistry.DefaultConfigurationHandler();

    @Before
    public void resetValues() {
        SampleConfig.counter = 1;
        SampleConfig.big = 2L;
        SampleConfig.ratio = 3.5d;
        SampleConfig.enabled = false;
        SampleConfig.label = "default";
        SampleConfig.letter = 'x';
        SampleConfig.numbers = new int[] { 9 };
        SampleConfig.names = new String[] { "default" };
    }

    private void parse(String text) {
        handler.loadFromString(
            text,
            new SampleConfig(),
            Collections.<String, java.util.Map<String, java.lang.reflect.Field>>emptyMap());
    }

    // ------------------------------------------------------------------------------ well-formed input

    @Test
    public void parsesAWellFormedFile() {
        parse(
            "Numbers {\n" + "\t# an int\n"
                + "\tInteger:counter=42\n"
                + "\n"
                + "\t# a long\n"
                + "\tLong:big=123456789012\n"
                + "\n"
                + "}\n"
                + "Flags {\n"
                + "\tBoolean:enabled=true\n"
                + "\tString:label=hello\n"
                + "\tCharacter:letter=z\n"
                + "}\n"
                + "Arrays {\n"
                + "\tInteger Array:numbers=[1,2,3]\n"
                + "\tString Array:names=[a,b]\n"
                + "}\n");

        Assert.assertEquals(42, SampleConfig.counter);
        Assert.assertEquals(123456789012L, SampleConfig.big);
        Assert.assertTrue(SampleConfig.enabled);
        Assert.assertEquals("hello", SampleConfig.label);
        Assert.assertEquals('z', SampleConfig.letter);
        Assert.assertArrayEquals(new int[] { 1, 2, 3 }, SampleConfig.numbers);
        Assert.assertArrayEquals(new String[] { "a", "b" }, SampleConfig.names);
    }

    @Test
    public void acceptsWindowsLineEndings() {
        parse("Numbers {\r\n\tInteger:counter=7\r\n}\r\n");
        Assert.assertEquals(7, SampleConfig.counter);
    }

    @Test
    public void acceptsIndentationOfAnyKind() {
        parse("Numbers {\nInteger:counter=5\n}\n");
        Assert.assertEquals(5, SampleConfig.counter);

        resetValues();
        parse("Numbers {\n\t\t\tInteger:counter=6\n}\n");
        Assert.assertEquals(6, SampleConfig.counter);
    }

    @Test
    public void acceptsACategoryHeaderWithExtraSpaces() {
        parse("Numbers   {\n\tInteger:counter=8\n}\n");
        Assert.assertEquals(8, SampleConfig.counter);
    }

    // ------------------------------------------------------------------------------ §1.6 resilience

    /** The old parser required a comment line before every entry. */
    @Test
    public void parsesEntriesWithNoComments() {
        parse("Numbers {\n\tInteger:counter=11\n\tLong:big=12\n}\n");
        Assert.assertEquals(11, SampleConfig.counter);
        Assert.assertEquals(12L, SampleConfig.big);
    }

    /** The old parser required exactly one comment line per entry. */
    @Test
    public void parsesEntriesWithMultipleCommentLines() {
        parse("Numbers {\n" + "\t# first comment\n" + "\t# second comment\n" + "\tInteger:counter=13\n" + "}\n");
        Assert.assertEquals(13, SampleConfig.counter);
    }

    /** The old parser required a blank line after every entry. */
    @Test
    public void parsesEntriesWithNoBlankLines() {
        parse("Numbers {\n\tInteger:counter=14\n\tLong:big=15\n\tDouble:ratio=16.5\n}\n");
        Assert.assertEquals(14, SampleConfig.counter);
        Assert.assertEquals(15L, SampleConfig.big);
        Assert.assertEquals(16.5d, SampleConfig.ratio, 0d);
    }

    /** A value containing '=' used to be truncated at the first one. */
    @Test
    public void parsesAValueContainingEquals() {
        parse("Flags {\n\tString:label=a=b=c\n}\n");
        Assert.assertEquals("a=b=c", SampleConfig.label);
    }

    /** A value containing ':' used to be fine, but only by luck; pin it. */
    @Test
    public void parsesAValueContainingAColon() {
        parse("Flags {\n\tString:label=host:port\n}\n");
        Assert.assertEquals("host:port", SampleConfig.label);
    }

    @Test
    public void parsesAValueStartingWithAHash() {
        parse("Flags {\n\tString:label=#notacomment\n}\n");
        Assert.assertEquals("#notacomment", SampleConfig.label);
    }

    @Test
    public void parsesAnEmptyStringValue() {
        parse("Flags {\n\tString:label=\n}\n");
        Assert.assertEquals("", SampleConfig.label);
    }

    @Test
    public void parsesAnEmptyArray() {
        parse("Arrays {\n\tInteger Array:numbers=[]\n}\n");
        Assert.assertArrayEquals(new int[0], SampleConfig.numbers);
    }

    /** The old parser's state machine could not recover from a stray header. */
    @Test
    public void recoversFromAStrayHeader() {
        parse("this line is nonsense\nNumbers {\n\tInteger:counter=17\n}\n");
        Assert.assertEquals(17, SampleConfig.counter);
    }

    @Test
    public void ignoresTrailingGarbageAfterTheLastBlock() {
        parse("Numbers {\n\tInteger:counter=18\n}\n}\n}\n");
        Assert.assertEquals(18, SampleConfig.counter);
    }

    // --------------------------------------------------------------------------- error handling

    @Test
    public void unknownFieldsAreSkippedAndTheRestStillParse() {
        parse("Numbers {\n" + "\tInteger:doesNotExist=1\n" + "\tInteger:counter=19\n" + "}\n");
        Assert.assertEquals(19, SampleConfig.counter);
    }

    @Test
    public void malformedEntriesAreSkippedAndTheRestStillParse() {
        parse("Numbers {\n" + "\tthis is not an entry\n" + "\tInteger:counter=20\n" + "}\n");
        Assert.assertEquals(20, SampleConfig.counter);
    }

    /** A bad value must keep the default instead of aborting the parse. */
    @Test
    public void aTypeMismatchKeepsTheDefault() {
        parse("Numbers {\n\tInteger:counter=not-a-number\n}\n");
        Assert.assertEquals(1, SampleConfig.counter);
    }

    @Test
    public void aTypeMismatchDoesNotStopLaterEntries() {
        parse("Numbers {\n" + "\tInteger:counter=oops\n" + "\tLong:big=21\n" + "}\n");
        Assert.assertEquals(1, SampleConfig.counter);
        Assert.assertEquals(21L, SampleConfig.big);
    }

    @Test
    public void anUnterminatedBlockStillParsesItsEntries() {
        parse("Numbers {\n\tInteger:counter=22\n");
        Assert.assertEquals(22, SampleConfig.counter);
    }

    @Test
    public void anEmptyDocumentLeavesEverythingAtItsDefault() {
        parse("");
        Assert.assertEquals(1, SampleConfig.counter);
        Assert.assertEquals("default", SampleConfig.label);
    }

    @Test
    public void commentsOutsideABlockAreIgnored() {
        parse("# a leading comment\nNumbers {\n\tInteger:counter=23\n}\n# a trailing comment\n");
        Assert.assertEquals(23, SampleConfig.counter);
    }

    // ------------------------------------------------------------------------------- round trip

    /** Write then read must be a fixed point, including values with delimiters. */
    @Test
    public void writeThenReadIsAFixedPoint() throws Exception {
        SampleConfig.counter = -7;
        SampleConfig.big = Long.MIN_VALUE;
        SampleConfig.ratio = -1.25d;
        SampleConfig.enabled = true;
        SampleConfig.label = "a=b, c#d";
        SampleConfig.letter = 'q';
        SampleConfig.numbers = new int[] { -1, 0, 1 };
        SampleConfig.names = new String[] { "x,y", "z]" };

        ConfigRegistry.ConfigProxy proxy = new ConfigRegistry.ConfigProxy(
            SampleConfig.class.getAnnotation(com.austinv11.collectiveframework.minecraft.config.Config.class),
            new SampleConfig());
        // convertToString reads the current side to decide which fields to skip, and must work before
        // loadFile has populated the handler's field map (CODE_REVIEW §1.6).
        com.austinv11.collectiveframework.minecraft.proxy.CommonProxy previousProxy = com.austinv11.collectiveframework.minecraft.CollectiveFramework.proxy;
        com.austinv11.collectiveframework.minecraft.CollectiveFramework.proxy = new com.austinv11.collectiveframework.minecraft.proxy.CommonProxy();
        String written;
        try {
            written = proxy.handler.convertToString(proxy.config);
        } finally {
            com.austinv11.collectiveframework.minecraft.CollectiveFramework.proxy = previousProxy;
        }
        Assert.assertFalse(
            "convertToString produced nothing",
            written.trim()
                .isEmpty());

        resetValues();
        parse(written);

        Assert.assertEquals(-7, SampleConfig.counter);
        Assert.assertEquals(Long.MIN_VALUE, SampleConfig.big);
        Assert.assertEquals(-1.25d, SampleConfig.ratio, 0d);
        Assert.assertTrue(SampleConfig.enabled);
        Assert.assertEquals("a=b, c#d", SampleConfig.label);
        Assert.assertEquals('q', SampleConfig.letter);
        Assert.assertArrayEquals(new int[] { -1, 0, 1 }, SampleConfig.numbers);
        Assert.assertArrayEquals(new String[] { "x,y", "z]" }, SampleConfig.names);
    }
}

package com.austinv11.collectiveframework.minecraft.config;

import java.util.Arrays;

import net.minecraftforge.common.config.Property;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link DefaultProxy} is the only {@link IConfigProxy} that ships with the framework, so its round trip is
 * what every config value goes through. Requires Forge on the test classpath (for {@link Property.Type}),
 * but no running game.
 * <p>
 * CODE_REVIEW §2.8 - {@code deserialize} used to swallow {@link IllegalArgumentException} and hand back the raw
 * string, so a caller that assigned the result to a typed field blew up later with an unchecked
 * {@link IllegalArgumentException} instead of a {@link ConfigException}.
 * <p>
 * CODE_REVIEW §2.9 - array elements were joined with unescaped commas (so {@code ["a,b", "c"]} came back as
 * three elements) and an empty array came back as a single empty element.
 */
public class DefaultProxyTest {

    private final DefaultProxy proxy = new DefaultProxy();

    // --------------------------------------------------------- canSerializeObject

    @Test
    public void canSerializePrimitiveWrappersAndStrings() {
        Assert.assertTrue(proxy.canSerializeObject(Integer.valueOf(1)));
        Assert.assertTrue(proxy.canSerializeObject(Boolean.TRUE));
        Assert.assertTrue(proxy.canSerializeObject(Character.valueOf('a')));
        Assert.assertTrue(proxy.canSerializeObject("string"));
        Assert.assertTrue(proxy.canSerializeObject(Property.Type.BOOLEAN));
    }

    @Test
    public void canSerializeArrays() {
        Assert.assertTrue(proxy.canSerializeObject(new int[0]));
        Assert.assertTrue(proxy.canSerializeObject(new String[0]));
    }

    @Test
    public void cannotSerializeArbitraryObjectsOrNull() {
        Assert.assertFalse(proxy.canSerializeObject(new Object()));
        Assert.assertFalse(proxy.canSerializeObject(Arrays.asList("a")));
        Assert.assertFalse(proxy.canSerializeObject(null));
    }

    // ------------------------------------------------------------------ isKeyUsable

    @Test
    public void recognisesItsOwnKeys() {
        Assert.assertTrue(proxy.isKeyUsable("Integer"));
        Assert.assertTrue(proxy.isKeyUsable("Boolean"));
        Assert.assertTrue(proxy.isKeyUsable("String"));
        Assert.assertTrue(proxy.isKeyUsable("Integer Array"));
        Assert.assertFalse(proxy.isKeyUsable("SomethingElse"));
        Assert.assertFalse(proxy.isKeyUsable(null));
    }

    // ---------------------------------------------------------------------- getKey

    @Test
    public void getKeyUsesFrameworkTypeNames() {
        Assert.assertEquals("Integer", proxy.getKey(Integer.valueOf(1)));
        Assert.assertEquals("Long", proxy.getKey(Long.valueOf(1)));
        Assert.assertEquals("Boolean", proxy.getKey(Boolean.TRUE));
        Assert.assertEquals("Character", proxy.getKey(Character.valueOf('a')));
        Assert.assertEquals("String", proxy.getKey("s"));
    }

    @Test
    public void getKeyForArraysAppendsArray() {
        Assert.assertEquals("Integer Array", proxy.getKey(new int[0]));
        Assert.assertEquals("String Array", proxy.getKey(new String[0]));
    }

    @Test
    public void getKeyForForgePropertyTypesIsTitleCased() {
        Assert.assertEquals("Boolean", proxy.getKey(Property.Type.BOOLEAN));
        Assert.assertEquals("Integer", proxy.getKey(Property.Type.INTEGER));
        Assert.assertEquals("String", proxy.getKey(Property.Type.STRING));
    }

    // ------------------------------------------------------------ scalar round trip

    @Test
    public void scalarRoundTrip() throws Exception {
        assertRoundTrip(Integer.valueOf(42), "Integer");
        assertRoundTrip(Long.valueOf(42L), "Long");
        assertRoundTrip(Double.valueOf(4.5d), "Double");
        assertRoundTrip(Float.valueOf(4.5f), "Float");
        assertRoundTrip(Boolean.TRUE, "Boolean");
        assertRoundTrip(Byte.valueOf((byte) 4), "Byte");
        assertRoundTrip(Short.valueOf((short) 4), "Short");
        assertRoundTrip(Character.valueOf('a'), "Character");
        assertRoundTrip("hello", "String");
    }

    private void assertRoundTrip(Object value, String key) throws ConfigException {
        String serialised = proxy.serialize(value);
        Object restored = proxy.deserialize(key, serialised);
        Assert.assertEquals(value, restored);
        Assert.assertEquals(value.getClass(), restored.getClass());
    }

    // ------------------------------------------------------------- array round trip

    @Test
    public void intArrayRoundTrip() throws Exception {
        String serialised = proxy.serialize(new int[] { 1, 2, 3 });
        Assert.assertEquals("[1,2,3]", serialised);
        Assert.assertArrayEquals(new int[] { 1, 2, 3 }, (int[]) proxy.deserialize("Integer Array", serialised));
    }

    @Test
    public void stringArrayRoundTrip() throws Exception {
        String serialised = proxy.serialize(new String[] { "a", "b" });
        Assert.assertEquals("[a,b]", serialised);
        Assert.assertArrayEquals(new String[] { "a", "b" }, (String[]) proxy.deserialize("String Array", serialised));
    }

    @Test
    public void booleanArrayRoundTrip() throws Exception {
        String serialised = proxy.serialize(new boolean[] { true, false });
        Assert.assertArrayEquals(
            new boolean[] { true, false },
            (boolean[]) proxy.deserialize("Boolean Array", serialised));
    }

    @Test
    public void doubleArrayRoundTrip() throws Exception {
        String serialised = proxy.serialize(new double[] { 1.5d, 2.5d });
        Assert.assertArrayEquals(
            new double[] { 1.5d, 2.5d },
            (double[]) proxy.deserialize("Double Array", serialised),
            0d);
    }

    /** CODE_REVIEW §2.9 (fixed) - an empty array used to come back as one element holding the empty string. */
    @Test
    public void emptyArrayRoundTrip() throws Exception {
        String serialised = proxy.serialize(new int[0]);
        Assert.assertEquals("[]", serialised);
        Assert.assertArrayEquals(new int[0], (int[]) proxy.deserialize("Integer Array", serialised));
    }

    /** CODE_REVIEW §2.9 (fixed) - elements were joined with unescaped commas. */
    @Test
    public void stringArrayRoundTripWithCommas() throws Exception {
        String serialised = proxy.serialize(new String[] { "a,b", "c" });
        Assert.assertArrayEquals(new String[] { "a,b", "c" }, (String[]) proxy.deserialize("String Array", serialised));
    }

    @Test
    public void arrayElementEndingWithABracketRoundTrips() throws Exception {
        String[] value = { "a]", "b" };
        String serialised = proxy.serialize(value);
        Assert.assertArrayEquals(value, (String[]) proxy.deserialize("String Array", serialised));
    }

    /**
     * Known limitation of the bracketed-comma format: an array holding exactly one empty string and an empty
     * array both serialise to {@code []}. Pinned so a change is a deliberate decision.
     */
    @Test
    public void anArrayOfOneEmptyStringIsIndistinguishableFromAnEmptyArray() throws Exception {
        Assert.assertEquals("[]", proxy.serialize(new String[] { "" }));
        Assert.assertEquals(0, ((String[]) proxy.deserialize("String Array", "[]")).length);
    }

    // ---------------------------------------------------------------------- §2.9 escaping

    @Test
    public void scalarRoundTripWithSpecialCharacters() throws Exception {
        String value = "a,b]c\\d\ne\tf";
        String serialised = proxy.serialize(value);
        Assert.assertEquals(value, proxy.deserialize("String", serialised));
    }

    @Test
    public void scalarWithANewlineStaysOnOneLine() throws Exception {
        String serialised = proxy.serialize("line one\nline two");
        Assert.assertFalse("a serialised value must not contain a real newline", serialised.contains("\n"));
        Assert.assertEquals("line one\nline two", proxy.deserialize("String", serialised));
    }

    /**
     * A value written by an older version - a Windows path, say - must still read back unchanged: an
     * unrecognised escape sequence is kept verbatim rather than mangled.
     */
    @Test
    public void legacyBackslashesArePreserved() throws Exception {
        Assert.assertEquals("C:\\data\\file", proxy.deserialize("String", "C:\\data\\file"));
    }

    // --------------------------------------------------------------------- §2.8 errors

    @Test(expected = ConfigException.class)
    public void deserializeRejectsMalformedValues() throws Exception {
        proxy.deserialize("Integer", "not-a-number");
    }

    @Test(expected = ConfigException.class)
    public void deserializeRejectsAnUnusableKey() throws Exception {
        proxy.deserialize("NotAType", "whatever");
    }

    @Test(expected = ConfigException.class)
    public void deserializeRejectsNull() throws Exception {
        proxy.deserialize("Integer", null);
    }

    @Test(expected = ConfigException.class)
    public void deserializeRejectsAnUnbracketedArray() throws Exception {
        proxy.deserialize("Integer Array", "1,2,3");
    }

    @Test(expected = ConfigException.class)
    public void deserializeRejectsAMismatchedArrayElement() throws Exception {
        proxy.deserialize("Integer Array", "[1,two,3]");
    }

    /** CODE_REVIEW §2.9 (fixed) - an empty character used to throw AIOOBE from {@code toCharArray()[0]}. */
    @Test(expected = ConfigException.class)
    public void emptyCharacterValueReportsACleanError() throws Exception {
        proxy.deserialize("Character", "");
    }

    @Test
    public void serializingAnUnsupportedObjectReportsACleanError() {
        try {
            proxy.serialize(new Object());
            Assert.fail("expected ConfigException");
        } catch (ConfigException expected) {
            Assert.assertTrue(
                expected.getMessage(),
                expected.getMessage()
                    .contains("Cannot serialize"));
        }
    }
}

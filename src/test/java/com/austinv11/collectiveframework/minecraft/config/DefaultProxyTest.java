package com.austinv11.collectiveframework.minecraft.config;

import java.util.Arrays;

import net.minecraftforge.common.config.Property;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * {@link DefaultProxy} is the only {@link IConfigProxy} that ships with the framework, so its round trip is
 * what every config value goes through. Requires Forge on the test classpath (for {@link Property.Type}),
 * but no running game.
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
    public void cannotSerializeArbitraryObjects() {
        Assert.assertFalse(proxy.canSerializeObject(new Object()));
        Assert.assertFalse(proxy.canSerializeObject(Arrays.asList("a")));
    }

    // ------------------------------------------------------------------ isKeyUsable

    @Test
    public void recognisesItsOwnKeys() {
        Assert.assertTrue(proxy.isKeyUsable("Integer"));
        Assert.assertTrue(proxy.isKeyUsable("Boolean"));
        Assert.assertTrue(proxy.isKeyUsable("String"));
        Assert.assertTrue(proxy.isKeyUsable("Integer Array"));
        Assert.assertFalse(proxy.isKeyUsable("SomethingElse"));
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

    // ---------------------------------------------------------------- known defects

    /**
     * CODE_REVIEW §2.9 - an empty array serialises to {@code []}, which deserialises back as a one-element
     * array containing the empty string.
     */
    @Ignore("CODE_REVIEW §2.9: empty arrays round-trip as a single empty element")
    @Test
    public void emptyArrayRoundTrip() throws Exception {
        String serialised = proxy.serialize(new int[0]);
        Assert.assertArrayEquals(new int[0], (int[]) proxy.deserialize("Integer Array", serialised));
    }

    /**
     * CODE_REVIEW §2.9 - array elements are joined with commas and never escaped, so any element containing a
     * comma is split in two.
     */
    @Ignore("CODE_REVIEW §2.9: array serialisation does not escape commas")
    @Test
    public void stringArrayRoundTripWithCommas() throws Exception {
        String serialised = proxy.serialize(new String[] { "a,b", "c" });
        Assert.assertArrayEquals(new String[] { "a,b", "c" }, (String[]) proxy.deserialize("String Array", serialised));
    }

    /**
     * CODE_REVIEW §2.8 - {@code deserialize} swallows {@link IllegalArgumentException} and returns the raw
     * string, so a caller that assigns the result to a typed field blows up later with an unchecked
     * {@link IllegalArgumentException} instead of a {@link ConfigException} here.
     */
    @Ignore("CODE_REVIEW §2.8: type errors are swallowed and returned as a String")
    @Test(expected = ConfigException.class)
    public void deserializeShouldRejectMalformedValues() throws Exception {
        proxy.deserialize("Integer", "not-a-number");
    }

    /**
     * CODE_REVIEW §2.8 - locked-in description of today's behaviour: malformed input comes back as the
     * untouched string rather than as an error.
     */
    @Test
    public void deserializeCurrentlyReturnsTheRawStringOnTypeErrors() throws Exception {
        Assert.assertEquals("not-a-number", proxy.deserialize("Integer", "not-a-number"));
    }

    /**
     * CODE_REVIEW §2.9 - {@code deserializeUnSafe} indexes {@code toCharArray()[0]} for characters, so an
     * empty value throws.
     */
    @Ignore("CODE_REVIEW §2.9: empty character values throw AIOOBE")
    @Test
    public void emptyCharacterValueShouldNotThrow() throws Exception {
        Assert.assertNull(proxy.deserialize("Character", ""));
    }
}

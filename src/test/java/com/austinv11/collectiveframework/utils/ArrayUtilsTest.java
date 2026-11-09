package com.austinv11.collectiveframework.utils;

import org.junit.Assert;
import org.junit.Test;

/**
 * Regression tests for {@link ArrayUtils}.
 * <p>
 * Tests annotated with {@code @Ignore} document behaviour that is currently wrong; see the referenced
 * section of CODE_REVIEW.md. When a defect is fixed, remove the {@code @Ignore} and the test becomes the
 * regression guard for the fix.
 */
public class ArrayUtilsTest {

    // ---------------------------------------------------------------- indexOf

    @Test
    public void indexOfObjectArrayFindsFirstMatch() {
        Assert.assertEquals(1, ArrayUtils.indexOf(new String[] { "a", "b", "c" }, "b"));
    }

    @Test
    public void indexOfObjectArrayReturnsMinusOneWhenAbsent() {
        Assert.assertEquals(-1, ArrayUtils.indexOf(new String[] { "a" }, "z"));
    }

    @Test
    public void indexOfObjectArrayHonoursStartIndex() {
        Assert.assertEquals(2, ArrayUtils.indexOf(new String[] { "a", "b", "a" }, "a", 1));
    }

    @Test
    public void indexOfPrimitiveOverloadsWork() {
        Assert.assertEquals(1, ArrayUtils.indexOf(new byte[] { 1, 2, 3 }, (byte) 2));
        Assert.assertEquals(1, ArrayUtils.indexOf(new short[] { 1, 2, 3 }, (short) 2));
        Assert.assertEquals(1, ArrayUtils.indexOf(new int[] { 1, 2, 3 }, 2));
        Assert.assertEquals(1, ArrayUtils.indexOf(new long[] { 1L, 2L, 3L }, 2L));
        Assert.assertEquals(1, ArrayUtils.indexOf(new float[] { 1f, 2f, 3f }, 2f));
        Assert.assertEquals(1, ArrayUtils.indexOf(new double[] { 1d, 2d, 3d }, 2d));
        Assert.assertEquals(1, ArrayUtils.indexOf(new boolean[] { false, true }, true));
        Assert.assertEquals(1, ArrayUtils.indexOf(new char[] { 'a', 'b' }, 'b'));
    }

    @Test
    public void indexOfPrimitiveOverloadsHonourStartIndex() {
        Assert.assertEquals(2, ArrayUtils.indexOf(new int[] { 1, 2, 1 }, 1, 1));
        Assert.assertEquals(-1, ArrayUtils.indexOf(new int[] { 1, 2, 1 }, 1, 3));
    }

    // -------------------------------------------------------- wrappedRetrieve

    @Test
    public void wrappedRetrieveWrapsForward() {
        // Index 4 on a 3-element array wraps to index 1.
        Assert.assertEquals(Integer.valueOf(2), ArrayUtils.wrappedRetrieve(new Integer[] { 1, 2, 3 }, 4));
        Assert.assertEquals(1, ArrayUtils.wrappedRetrieve(new int[] { 1, 2, 3 }, 3));
        Assert.assertEquals(3, ArrayUtils.wrappedRetrieve(new int[] { 1, 2, 3 }, 5));
    }

    @Test
    public void wrappedRetrieveReturnsInBoundsValues() {
        Assert.assertEquals(2, ArrayUtils.wrappedRetrieve(new int[] { 1, 2, 3 }, 1));
    }

    /**
     * CODE_REVIEW §3.13 - a negative index falls through to {@code array[index]} and throws, and a
     * zero-length array makes the wrapping loop spin forever.
     */
    @Test
    public void wrappedRetrieveShouldHandleNegativeIndices() {
        Assert.assertEquals(3, ArrayUtils.wrappedRetrieve(new int[] { 1, 2, 3 }, -1));
    }

    // --------------------------------------------------------- removeRepeats

    @Test
    public void removeRepeatsKeepsFirstOccurrenceOrder() {
        Assert.assertArrayEquals(
            new String[] { "a", "b", "c" },
            ArrayUtils.removeRepeats(new String[] { "a", "b", "a", "c", "b" }));
    }

    @Test
    public void removeRepeatsOnPrimitives() {
        Assert.assertArrayEquals(new int[] { 1, 2, 3 }, ArrayUtils.removeRepeats(new int[] { 1, 1, 2, 3, 2 }));
        Assert.assertArrayEquals(new byte[] { 1, 2 }, ArrayUtils.removeRepeats(new byte[] { 1, 2, 1 }));
        Assert.assertArrayEquals(new short[] { 1, 2 }, ArrayUtils.removeRepeats(new short[] { 1, 2, 2 }));
        Assert.assertArrayEquals(new long[] { 1L, 2L }, ArrayUtils.removeRepeats(new long[] { 1L, 2L, 1L }));
        Assert.assertArrayEquals(new float[] { 1f, 2f }, ArrayUtils.removeRepeats(new float[] { 1f, 2f, 2f }), 0f);
        Assert.assertArrayEquals(new double[] { 1d, 2d }, ArrayUtils.removeRepeats(new double[] { 1d, 2d, 2d }), 0d);
        Assert.assertArrayEquals(
            new boolean[] { true, false },
            ArrayUtils.removeRepeats(new boolean[] { true, false, true }));
        Assert.assertArrayEquals(new char[] { 'a', 'b' }, ArrayUtils.removeRepeats(new char[] { 'a', 'b', 'a' }));
    }

    @Test
    public void removeRepeatsOnEmptyArray() {
        Assert.assertEquals(0, ArrayUtils.removeRepeats(new int[0]).length);
        Assert.assertEquals(0, ArrayUtils.removeRepeats(new String[0]).length);
    }

    // -------------------------------------------------------------- trimAll

    @Test
    public void trimAllTrimsEveryElement() {
        Assert.assertArrayEquals(new String[] { "a", "b" }, ArrayUtils.trimAll(new String[] { " a ", "\tb\n" }));
    }

    // ------------------------------------------------- object -> primitive

    @Test
    public void convertObjectArrayToPrimitives() {
        Assert.assertArrayEquals(
            new int[] { 1, 2 },
            ArrayUtils.convertObjectArrayToInt(new Object[] { Integer.valueOf(1), Integer.valueOf(2) }));
        Assert.assertArrayEquals(
            new long[] { 1L, 2L },
            ArrayUtils.convertObjectArrayToLong(new Object[] { Long.valueOf(1), Long.valueOf(2) }));
        Assert.assertArrayEquals(
            new double[] { 1d, 2d },
            ArrayUtils.convertObjectArrayToDouble(new Object[] { Double.valueOf(1), Double.valueOf(2) }),
            0d);
        Assert.assertArrayEquals(
            new float[] { 1f, 2f },
            ArrayUtils.convertObjectArrayToFloat(new Object[] { Float.valueOf(1), Float.valueOf(2) }),
            0f);
        Assert.assertArrayEquals(
            new boolean[] { true, false },
            ArrayUtils.convertObjectArrayToBoolean(new Object[] { Boolean.TRUE, Boolean.FALSE }));
        Assert.assertArrayEquals(
            new char[] { 'a', 'b' },
            ArrayUtils.convertObjectArrayToChar(new Object[] { Character.valueOf('a'), Character.valueOf('b') }));
        Assert.assertArrayEquals(
            new byte[] { 1, 2 },
            ArrayUtils.convertObjectArrayToByte(new Object[] { Byte.valueOf((byte) 1), Byte.valueOf((byte) 2) }));
        Assert.assertArrayEquals(
            new short[] { 1, 2 },
            ArrayUtils.convertObjectArrayToShort(new Object[] { Short.valueOf((short) 1), Short.valueOf((short) 2) }));
    }
}

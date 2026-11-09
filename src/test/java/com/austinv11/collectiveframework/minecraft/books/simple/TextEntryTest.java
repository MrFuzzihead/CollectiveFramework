package com.austinv11.collectiveframework.minecraft.books.simple;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.austinv11.collectiveframework.utils.math.TwoDimensionalVector;

/**
 * CODE_REVIEW §2.25 - {@code TextEntry} initialised {@code private FontRenderer renderer =
 * Minecraft.getMinecraft().fontRenderer} as a field initialiser, and {@code Entry}'s constructor did the same, so
 * constructing an entry anywhere {@code Minecraft.getMinecraft()} is null threw a
 * {@link NullPointerException}. Both also captured the renderer once, while Minecraft replaces the instance on an
 * F3+T resource reload.
 * <p>
 * The guard and the degradation path are directly testable here; the live-renderer path needs a client and is
 * covered by the client game test.
 */
public class TextEntryTest {

    private static TextEntry entry(String text, int width, int height) {
        return new TextEntry(new TwoDimensionalVector(0, 0), width, height, text);
    }

    /** §2.25 - this used to throw a {@link NullPointerException} from the field initialiser. */
    @Test
    public void constructingAnEntryOffAClientDoesNotThrow() {
        TextEntry entry = entry("hello", 100, 20);
        Assert.assertNotNull(entry);
        Assert.assertEquals(new TwoDimensionalVector(0, 0), entry.getCoords());
        Assert.assertEquals(100, entry.width);
        Assert.assertEquals(20, entry.height);
    }

    @Test
    public void fitStringDegradesToTheRawLinesWithoutARenderer() {
        Assert.assertEquals(Arrays.asList("one", "two"), entry("one\ntwo", 100, 40).fitString());
    }

    @Test
    public void fitStringKeepsALongLineIntactWithoutARenderer() {
        String longLine = "a line far longer than the entry is wide";
        List<String> lines = entry(longLine, 10, 20).fitString();
        Assert.assertEquals(1, lines.size());
        Assert.assertEquals(longLine, lines.get(0));
    }

    @Test
    public void fitStringHandlesNoText() {
        // "".split("\n") is [""], so one empty line is produced. Pinned so a change is deliberate.
        List<String> lines = entry("", 100, 20).fitString();
        Assert.assertEquals(1, lines.size());
        Assert.assertEquals("", lines.get(0));
    }

    @Test
    public void fitStringHandlesANullText() {
        TextEntry entry = new TextEntry(new TwoDimensionalVector(0, 0), 100, 20, null);
        Assert.assertTrue(
            entry.fitString()
                .isEmpty());
    }

    @Test
    public void renderingOffAClientIsANoOp() {
        // font() returns null, so onRender must return rather than NPE.
        entry("hello", 100, 20).onRender(16);
    }

    @Test
    public void alignmentAndFlagsDefaultToLeftAndVisible() {
        TextEntry entry = entry("hello", 100, 20);
        Assert.assertEquals(TextEntry.Alignment.LEFT, entry.alignment);
        Assert.assertTrue(entry.useUnicodeFont);
        Assert.assertTrue(entry.useFontShadow);
    }

    @Test
    public void coordsAndRotationAreSettable() {
        TextEntry entry = entry("hello", 100, 20);
        entry.setCoords(new TwoDimensionalVector(5, 6));
        entry.setRotation(90);
        entry.setPitch(10);
        entry.setYaw(20);

        Assert.assertEquals(new TwoDimensionalVector(5, 6), entry.getCoords());
        Assert.assertEquals(90f, entry.getRotation(), 1.0E-6f);
        Assert.assertEquals(10f, entry.getPitch(), 1.0E-6f);
        Assert.assertEquals(20f, entry.getYaw(), 1.0E-6f);
    }

    @Test
    public void rotationIsClampedToAFullTurn() {
        TextEntry entry = entry("hello", 100, 20);
        entry.setRotation(-30);
        Assert.assertEquals(0f, entry.getRotation(), 1.0E-6f);
        entry.setRotation(400);
        Assert.assertEquals(360f, entry.getRotation(), 1.0E-6f);
    }
}

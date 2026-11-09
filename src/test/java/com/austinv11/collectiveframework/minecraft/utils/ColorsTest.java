package com.austinv11.collectiveframework.minecraft.utils;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link Colors} has no Minecraft dependencies, so it is unit-testable without a game.
 */
public class ColorsTest {

    @Test
    public void toStringPrefixesTheSectionSign() {
        Assert.assertEquals("\u00a70", Colors.BLACK.toString());
        Assert.assertEquals("\u00a7c", Colors.RED.toString());
        Assert.assertEquals("\u00a7f", Colors.WHITE.toString());
        Assert.assertEquals("\u00a7r", Colors.RESET.toString());
    }

    @Test
    public void colorCodeCharIsTheSectionSign() {
        Assert.assertEquals('\u00a7', Colors.COLOR_CODE_CHAR);
    }

    @Test
    public void everyColorCharIsDeclared() {
        Assert.assertEquals(Colors.values().length, Colors.COLOR_CHARS.length);
    }

    @Test
    public void replacesAlternateCharBeforeAValidCode() {
        Assert.assertEquals("\u00a7aHello", Colors.replaceAlternateColorChar("&aHello"));
        Assert.assertEquals("\u00a7a\u00a7b\u00a7c", Colors.replaceAlternateColorChar("&a&b&c"));
    }

    @Test
    public void leavesUnknownCodesAlone() {
        Assert.assertEquals("&z", Colors.replaceAlternateColorChar("&z"));
    }

    @Test
    public void leavesATrailingAlternateCharAlone() {
        Assert.assertEquals("hello&", Colors.replaceAlternateColorChar("hello&"));
    }

    @Test
    public void supportsACustomAlternateChar() {
        Assert.assertEquals("\u00a7a", Colors.replaceAlternateColorChar('%', "%a"));
    }

    /**
     * Known limitation (not a CODE_REVIEW defect): {@code COLOR_CHARS} only lists lowercase codes, so
     * uppercase essentials-style codes such as {@code &A} are left untouched. Locked in so a change is a
     * deliberate decision.
     */
    @Test
    public void uppercaseCodesAreLeftAlone() {
        Assert.assertEquals("&A", Colors.replaceAlternateColorChar("&A"));
    }
}

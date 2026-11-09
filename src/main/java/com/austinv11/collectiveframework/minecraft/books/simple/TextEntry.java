package com.austinv11.collectiveframework.minecraft.books.simple;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;

import com.austinv11.collectiveframework.minecraft.books.api.Entry;
import com.austinv11.collectiveframework.utils.math.TwoDimensionalVector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * This is an easy-to-use entry implementation for text
 */
public class TextEntry extends Entry {

    /**
     * Using unicode font makes everything a little less minecrafty
     */
    public boolean useUnicodeFont = true;
    /**
     * Adds a shadow to the text, makes it more visible in most cases
     */
    public boolean useFontShadow = true;
    /**
     * The text alignment
     */
    public Alignment alignment = Alignment.LEFT;
    private String toRender;

    public TextEntry(TwoDimensionalVector coords, int width, int height, String toRender) {
        this(coords, width, height);
        this.toRender = toRender;
    }

    private TextEntry(TwoDimensionalVector coords, int width, int height) {
        super(coords, width, height);
    }

    @SideOnly(Side.CLIENT)
    public List<String> fitString() {
        List<String> strings = new ArrayList<String>();
        if (toRender == null) return strings;
        String[] lines = toRender.split("\n");
        // The field initialiser used to call Minecraft.getMinecraft().fontRenderer, which NPE'd outside a client
        // and went stale after a resource reload (CODE_REVIEW §2.25).
        FontRenderer renderer = font();
        if (renderer == null) {
            for (String line : lines) strings.add(line);
            return strings;
        }
        for (String line : lines) {
            String currentString = "";
            String[] split = line.split(" ");
            for (String s : split) {
                if (currentString.isEmpty()) {
                    currentString = s;
                } else if (renderer.getStringWidth(currentString + " " + s) > width) {
                    strings.add(currentString);
                    currentString = s;
                } else {
                    currentString = currentString + " " + s;
                }
            }
            strings.add(currentString);
        }
        return strings;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void onRender(int dt) {
        FontRenderer renderer = font();
        if (renderer == null) return;
        List<String> lines = fitString();
        boolean unicode = renderer.getUnicodeFlag();
        renderer.setUnicodeFlag(useUnicodeFont);
        int currentY = getCoords().getRoundedY();
        if (alignment == Alignment.LEFT) {
            for (String s : lines) {
                renderer.drawString(s, getCoords().getRoundedX(), currentY, Color.WHITE.getRGB(), useFontShadow);
                currentY += renderer.FONT_HEIGHT;
                if (currentY >= getCoords().getRoundedY() + height) break;
            }
        } else if (alignment == Alignment.CENTER) {
            for (String s : lines) {
                renderer.drawString(
                    s,
                    getCoords().getRoundedX() + (width - renderer.getStringWidth(s)) / 2,
                    currentY,
                    Color.WHITE.getRGB(),
                    useFontShadow);
                currentY += renderer.FONT_HEIGHT;
                if (currentY >= getCoords().getRoundedY() + height) break;
            }
        } else if (alignment == Alignment.RIGHT) {
            for (String s : lines) {
                renderer.drawString(
                    s,
                    getCoords().getRoundedX() + (width - renderer.getStringWidth(s)),
                    currentY,
                    Color.WHITE.getRGB(),
                    useFontShadow);
                currentY += renderer.FONT_HEIGHT;
                if (currentY >= getCoords().getRoundedY() + height) break;
            }
        } else {
            for (String s : lines) {
                int spacesToAdd = (width - renderer.getStringWidth(s)) / renderer.getCharWidth(' ');
                String[] words = s.split(" ");
                spacesToAdd += words.length;
                String justified = "";
                inner: while (spacesToAdd > 0) {
                    for (int i = 0; i < words.length; i++) {
                        if (spacesToAdd == 0) break inner;
                        words[i] = words[i] + " ";
                        spacesToAdd--;
                    }
                }
                for (int i = 0; i < words.length; i++) justified = justified + words[i];
                renderer
                    .drawString(justified, getCoords().getRoundedX(), currentY, Color.WHITE.getRGB(), useFontShadow);
                currentY += renderer.FONT_HEIGHT;
                if (currentY >= getCoords().getRoundedY() + height) break;
            }
        }
        renderer.setUnicodeFlag(unicode);
    }

    /**
     * This represents the possible text alignments
     */
    public static enum Alignment {
        LEFT,
        CENTER,
        RIGHT,
        JUSTIFY
    }
}

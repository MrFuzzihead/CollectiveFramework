package com.austinv11.collectiveframework.minecraft.books.simple;

import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.austinv11.collectiveframework.minecraft.books.api.Entry;
import com.austinv11.collectiveframework.minecraft.utils.client.GuiUtils;
import com.austinv11.collectiveframework.utils.math.TwoDimensionalVector;

/**
 * This is an easy-to-use entry implementation for images
 */
public class ImageEntry extends Entry {

    /**
     * Set this to true to scale the image to fit the width and height provided
     */
    public boolean scaleToSize = true;
    /**
     * Set this to true to retain the aspect ratio for the image when scaling to size
     */
    public boolean retainRatio = true;
    private final ResourceLocation image;
    private final int sourceWidth;
    private final int sourceHeight;

    public ImageEntry(TwoDimensionalVector coords, int width, int height, ResourceLocation image) throws IOException {
        super(coords, width, height);
        this.image = image;
        BufferedImage backgroundImage = ImageIO.read(GuiUtils.getResourceAsStream(image));
        if (backgroundImage == null) throw new IOException("Unable to decode " + image);
        this.sourceWidth = backgroundImage.getWidth();
        this.sourceHeight = backgroundImage.getHeight();
    }

    /**
     * The scale a source image is drawn at inside a target box.
     * <p>
     * Pure so the arithmetic can be tested without a GL context or a running game.
     *
     * @param sourceWidth  The image's natural width
     * @param sourceHeight The image's natural height
     * @param targetWidth  The width to fit into
     * @param targetHeight The height to fit into
     * @param retainRatio  Whether to use a single uniform scale, so the image is not distorted
     * @return {@code {scaleX, scaleY}}
     */
    public static double[] computeScale(int sourceWidth, int sourceHeight, int targetWidth, int targetHeight,
        boolean retainRatio) {
        if (sourceWidth <= 0 || sourceHeight <= 0) return new double[] { 1, 1 };
        double scaleX = (double) targetWidth / (double) sourceWidth;
        double scaleY = (double) targetHeight / (double) sourceHeight;
        if (retainRatio) {
            double uniform = Math.min(scaleX, scaleY);
            return new double[] { uniform, uniform };
        }
        return new double[] { scaleX, scaleY };
    }

    /**
     * The scale this entry draws with.
     *
     * @return {@code {scaleX, scaleY}}
     */
    public double[] getDrawScale() {
        if (!scaleToSize) return new double[] { 1, 1 };
        return computeScale(sourceWidth, sourceHeight, width, height, retainRatio);
    }

    /**
     * The image's natural size, in pixels.
     *
     * @return {@code {width, height}}
     */
    public int[] getSourceSize() {
        return new int[] { sourceWidth, sourceHeight };
    }

    @Override
    public void onRender(int dt) {
        if (image == null || sourceWidth <= 0 || sourceHeight <= 0) return;

        double[] scale = getDrawScale();

        GL11.glPushMatrix();
        // Translate to the entry first, then scale, then draw at the origin. Scaling before translating scaled
        // the entry's own position as well, so a scaled image landed somewhere else entirely
        // (CODE_REVIEW §2.23).
        GL11.glTranslated(getCoords().getRoundedX(), getCoords().getRoundedY(), 0);
        GL11.glScaled(scale[0], scale[1], 1);

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(image);
        drawTexturedModalRect(0, 0, 0, 0, sourceWidth, sourceHeight);

        GL11.glPopMatrix();
    }
}

package com.austinv11.collectiveframework.minecraft.books.api;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.MathHelper;

import com.austinv11.collectiveframework.utils.math.TwoDimensionalVector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * This is the base class for any entry. Think of these like Microsoft Word text boxes.
 */
public abstract class Entry extends GuiScreen {

    /**
     * This determines whether the placement is relative to the book when false or absolute when true
     */
    public boolean useAbsoluteCoords = false;
    /**
     * If this is true, debug lines will be drawn atop the entry
     */
    public boolean drawDebugLines = false;
    private TwoDimensionalVector coords;
    public int width, height;
    private float pitch = 0.0F, yaw = 0.0F, rotation = 0.0F; // Yup, this api supports complete rotation, albeit
                                                             // somewhat buggy

    /**
     * Default constructor, feel free to use a different one
     *
     * @param coords The coords of the entry
     * @param width  The width of the entry
     * @param height The height of the entry
     */
    public Entry(TwoDimensionalVector coords, int width, int height) {
        this.coords = coords;
        this.width = width;
        this.height = height;
        // Minecraft.getMinecraft() is null outside a client, so the old pair of calls threw an NPE rather than
        // leaving the entry merely unusable (CODE_REVIEW §2.25).
        Minecraft minecraft = Minecraft.getMinecraft();
        this.mc = minecraft;
        this.fontRendererObj = minecraft == null ? null : minecraft.fontRenderer;
    }

    /**
     * The font renderer to draw with.
     * <p>
     * {@code GuiScreen#fontRendererObj} is captured once at construction, and Minecraft replaces the instance on
     * an F3+T resource reload, so drawing with the captured one after a reload used a stale renderer
     * (CODE_REVIEW §2.25).
     *
     * @return The live renderer, the captured one as a fallback, or null outside a client
     */
    protected FontRenderer font() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.fontRenderer != null) return minecraft.fontRenderer;
        return fontRendererObj;
    }

    /**
     * Retrieves the current coords of the entry
     *
     * @return The coords
     */
    public TwoDimensionalVector getCoords() {
        return coords;
    }

    /**
     * Changes the coords of the entry
     *
     * @param newCoords The new coords for the entry
     */
    public void setCoords(TwoDimensionalVector newCoords) {
        this.coords = newCoords;
    }

    /**
     * Gets the pitch of the entry
     *
     * @return The pitch
     */
    public float getPitch() {
        return pitch;
    }

    /**
     * Gets the yaw of the entry
     *
     * @return The yaw
     */
    public float getYaw() {
        return yaw;
    }

    /**
     * Gets the rotation of the entry
     *
     * @return The rotation
     */
    public float getRotation() {
        return rotation;
    }

    /**
     * Sets the pitch of the entry
     *
     * @param pitch The pitch
     */
    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    /**
     * Sets the yaw of the entry
     *
     * @param yaw The yaw
     */
    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    /**
     * Sets the rotation of the entry
     *
     * @param rotation The rotation
     */
    public void setRotation(float rotation) {
        this.rotation = MathHelper.clamp_float(rotation, 0, 360);
    }

    /**
     * Called when the entry is rendered
     *
     * @param dt The time since the last render of the current pass in milliseconds
     */
    @SideOnly(Side.CLIENT)
    public abstract void onRender(int dt);
}

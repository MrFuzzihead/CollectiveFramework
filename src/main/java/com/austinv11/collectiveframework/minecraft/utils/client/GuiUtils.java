package com.austinv11.collectiveframework.minecraft.utils.client;

import java.io.IOException;
import java.io.InputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A class for holding helper methods for guis
 */
@SideOnly(Side.CLIENT)
public class GuiUtils {

    /**
     * Plays the button click sound
     */
    public static void playButtonSound() {
        Minecraft.getMinecraft()
            .getSoundHandler()
            .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
    }

    /**
     * Closes the currently opened gui for the player
     */
    public static void closeCurrentGui() {
        closeCurrentGui(null);
    }

    /**
     * Closes the currently opened gui for the player if it matches the filter
     *
     * @param filter The gui filter
     */
    public static void closeCurrentGui(Class<? extends GuiScreen> filter) {
        Minecraft minecraft = Minecraft.getMinecraft();
        // No game running (unit tests, early class loading): nothing to close.
        if (minecraft == null) return;
        GuiScreen current = minecraft.currentScreen;
        if (current == null) return;
        if (filter != null && !filter.isAssignableFrom(current.getClass())) return;
        // Assigning currentScreen = null directly skipped displayGuiScreen's cleanup, leaving the mouse cursor
        // grabbed and the world paused (CODE_REVIEW §2.20). displayGuiScreen(null) calls onGuiClosed itself.
        minecraft.displayGuiScreen(null);
    }

    /**
     * Retrieves an input stream for a resource location
     *
     * @param resourceLocation The resource location
     * @return The input stream
     * @throws IOException
     */
    public static InputStream getResourceAsStream(ResourceLocation resourceLocation) throws IOException {
        return Minecraft.getMinecraft()
            .getResourceManager()
            .getResource(resourceLocation)
            .getInputStream();
    }

    /**
     * Generates a scaled resolution object
     *
     * @return The resolution object
     */
    public static ScaledResolution getCurrentResolution() {
        return new ScaledResolution(
            Minecraft.getMinecraft(),
            Minecraft.getMinecraft().displayWidth,
            Minecraft.getMinecraft().displayHeight);
    }
}

package com.austinv11.collectiveframework.minecraft.utils.client;

import net.minecraft.client.gui.GuiScreen;

import org.junit.Assert;
import org.junit.Test;

/**
 * CODE_REVIEW §2.20 - {@code closeCurrentGui} assigned {@code Minecraft.currentScreen = null} directly, which
 * skipped {@code displayGuiScreen}'s cleanup and left the mouse cursor grabbed. It now delegates to
 * {@code displayGuiScreen(null)}.
 * <p>
 * Only the guard path is unit-testable: {@code Minecraft.getMinecraft()} is null outside a client, so the method
 * must return without touching anything. The real close is covered by the client game test.
 */
public class GuiUtilsTest {

    @Test
    public void closeCurrentGuiIsSafeWithoutAGame() {
        GuiUtils.closeCurrentGui();
    }

    @Test
    public void closeCurrentGuiWithAFilterIsSafeWithoutAGame() {
        GuiUtils.closeCurrentGui(GuiScreen.class);
    }

    @Test
    public void theMethodsExistWithTheExpectedSignatures() throws Exception {
        Assert.assertNotNull(GuiUtils.class.getMethod("closeCurrentGui"));
        Assert.assertNotNull(GuiUtils.class.getMethod("closeCurrentGui", Class.class));
        Assert.assertNotNull(GuiUtils.class.getMethod("playButtonSound"));
        Assert
            .assertNotNull(GuiUtils.class.getMethod("getResourceAsStream", net.minecraft.util.ResourceLocation.class));
        Assert.assertNotNull(GuiUtils.class.getMethod("getCurrentResolution"));
    }
}

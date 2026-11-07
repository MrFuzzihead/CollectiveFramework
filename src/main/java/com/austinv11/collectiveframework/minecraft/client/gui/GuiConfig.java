package com.austinv11.collectiveframework.minecraft.client.gui;

import net.minecraft.client.gui.GuiScreen;

import com.austinv11.collectiveframework.minecraft.reference.Config;
import com.austinv11.collectiveframework.minecraft.reference.Reference;

public class GuiConfig extends CfGuiFactory.GuiConfig {

    public GuiConfig(GuiScreen parentScreen) {
        super(parentScreen, Reference.MOD_ID, Reference.MOD_NAME, Config.INSTANCE);
    }
}

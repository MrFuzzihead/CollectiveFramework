package com.austinv11.collectiveframework.minecraft.proxy;

import net.minecraftforge.common.MinecraftForge;

import com.austinv11.collectiveframework.minecraft.client.gui.KeyOverlay;
import com.austinv11.collectiveframework.minecraft.event.handler.ClientTickHandler;
import com.austinv11.collectiveframework.minecraft.event.handler.KeyHandler;
import com.austinv11.collectiveframework.minecraft.init.Keybindings;
import com.austinv11.collectiveframework.minecraft.utils.IconManager;
import com.austinv11.collectiveframework.minecraft.utils.MinecraftTranslator;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventBus;
import cpw.mods.fml.relauncher.Side;

public class ClientProxy extends CommonProxy {

    @Override
    public void prepareClient() {
        Keybindings.init();
    }

    @Override
    public void registerEvents() {
        super.registerEvents();

        // MinecraftForge bus: Forge client events (ClientChatReceivedEvent, TextureStitchEvent,
        // RenderGameOverlayEvent, ...)
        MinecraftForge.EVENT_BUS.register(new MinecraftTranslator());
        MinecraftForge.EVENT_BUS.register(new IconManager());
        MinecraftForge.EVENT_BUS.register(new KeyOverlay());

        // FML bus: InputEvent and TickEvent are FML game events and are never posted to the Forge bus.
        // Registering them there left the key overlay and the time-change buttons dead. See CODE_REVIEW §1.1.
        EventBus fmlBus = FMLCommonHandler.instance()
            .bus();
        fmlBus.register(new KeyHandler());
        fmlBus.register(new ClientTickHandler());
    }

    @Override
    public Side getSide() {
        return Side.CLIENT;
    }
}

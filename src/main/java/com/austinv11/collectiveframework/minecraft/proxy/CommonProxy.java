package com.austinv11.collectiveframework.minecraft.proxy;

import net.minecraftforge.common.MinecraftForge;

import com.austinv11.collectiveframework.minecraft.CollectiveFramework;
import com.austinv11.collectiveframework.minecraft.event.handler.CommandBroadcastHandler;
import com.austinv11.collectiveframework.minecraft.event.handler.TooltipHandler;
import com.austinv11.collectiveframework.minecraft.threading.MainThreadTickHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

public class CommonProxy {

    public void prepareClient() {}

    public void registerEvents() {
        // MinecraftForge bus: Forge events (ItemTooltipEvent, CommandEvent, ServerChatEvent, ...)
        MinecraftForge.EVENT_BUS.register(new TooltipHandler());
        MinecraftForge.EVENT_BUS.register(new CommandBroadcastHandler());
        MinecraftForge.EVENT_BUS.register(CollectiveFramework.instance);

        // FML bus: FML game events (PlayerEvent, TickEvent, InputEvent). These are posted to a *different*
        // EventBus instance than MinecraftForge.EVENT_BUS, so registering only on the Forge bus means the
        // handler never fires. See CODE_REVIEW §1.1.
        FMLCommonHandler.instance()
            .bus()
            .register(CollectiveFramework.instance);

        // Drains MainThread's queues; network handlers hand their world access to it (CODE_REVIEW §3.1).
        FMLCommonHandler.instance()
            .bus()
            .register(new MainThreadTickHandler());
    }

    public Side getSide() {
        return Side.SERVER;
    }
}

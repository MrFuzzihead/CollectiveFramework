package com.austinv11.collectiveframework.minecraft.threading;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Drains the {@link MainThread} queues once per tick.
 * <p>
 * Registered on {@code FMLCommonHandler.instance().bus()}: {@link TickEvent} is an FML game event and is never
 * posted to {@code MinecraftForge.EVENT_BUS} (CODE_REVIEW §1.1).
 */
public class MainThreadTickHandler {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        // START and END both fire each tick; draining on END means everything scheduled during the tick runs
        // before the world is saved.
        if (event.phase == TickEvent.Phase.END) MainThread.drainServer();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) MainThread.drainClient();
    }
}

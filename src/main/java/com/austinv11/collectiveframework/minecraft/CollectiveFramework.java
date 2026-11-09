package com.austinv11.collectiveframework.minecraft;

import net.minecraft.entity.player.EntityPlayerMP;

import com.austinv11.collectiveframework.SmokeTestHook;
import com.austinv11.collectiveframework.minecraft.client.gui.GuiHandler;
import com.austinv11.collectiveframework.minecraft.compat.modules.Modules;
import com.austinv11.collectiveframework.minecraft.config.ConfigException;
import com.austinv11.collectiveframework.minecraft.config.ConfigRegistry;
import com.austinv11.collectiveframework.minecraft.logging.Logger;
import com.austinv11.collectiveframework.minecraft.network.ConfigPacket;
import com.austinv11.collectiveframework.minecraft.network.TileEntityClientUpdatePacket;
import com.austinv11.collectiveframework.minecraft.network.TileEntityServerUpdatePacket;
import com.austinv11.collectiveframework.minecraft.network.TimeUpdatePacket;
import com.austinv11.collectiveframework.minecraft.proxy.CommonProxy;
import com.austinv11.collectiveframework.minecraft.reference.Config;
import com.austinv11.collectiveframework.minecraft.reference.Reference;
import com.austinv11.collectiveframework.multithreading.SimpleRunnable;
import com.austinv11.collectiveframework.utils.TimeProfiler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

@Mod(
    modid = Reference.MOD_ID,
    name = Reference.MOD_NAME,
    version = Reference.VERSION,
    acceptableRemoteVersions = "*",
    guiFactory = Reference.GUI_FACTORY_CLASS)
public class CollectiveFramework {

    public static SimpleNetworkWrapper NETWORK;

    public static Logger LOGGER = new Logger(Reference.MOD_NAME);

    @Mod.Instance(Reference.MOD_ID)
    public static CollectiveFramework instance;

    @SidedProxy(clientSide = Reference.CLIENT_PROXY_CLASS, serverSide = Reference.SERVER_PROXY_CLASS)
    public static CommonProxy proxy;

    @Mod.EventHandler
    @SuppressWarnings("deprecation") // RESTRICT_THREAD_USAGE is kept for source compatibility only
    public void preInit(FMLPreInitializationEvent event) {
        TimeProfiler profiler = new TimeProfiler();
        try {
            ConfigRegistry.registerConfig(Config.INSTANCE);
        } catch (ConfigException e) {
            e.printStackTrace();
        }
        ConfigRegistry.init();
        proxy.registerEvents();
        Modules.init();
        SimpleRunnable.RESTRICT_THREAD_USAGE = Config.restrictThreadUsage;
        NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(Reference.NETWORK_NAME);
        Modules.propagate(event);
        LOGGER.info("Pre-Init took " + profiler.getTime() + "ms");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        TimeProfiler profiler = new TimeProfiler();
        ConfigRegistry.init();
        proxy.prepareClient();
        NetworkRegistry.INSTANCE.registerGuiHandler(instance, new GuiHandler());
        Modules.propagate(event);
        LOGGER.info("Init took " + profiler.getTime() + "ms");
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        TimeProfiler profiler = new TimeProfiler();
        ConfigRegistry.init();
        NETWORK.registerMessage(
            TileEntityServerUpdatePacket.TileEntityServerUpdatePacketHandler.class,
            TileEntityServerUpdatePacket.class,
            0,
            Side.SERVER);
        NETWORK.registerMessage(
            TileEntityClientUpdatePacket.TileEntityClientUpdatePacketHandler.class,
            TileEntityClientUpdatePacket.class,
            1,
            Side.CLIENT);
        NETWORK.registerMessage(ConfigPacket.ConfigPacketHandler.class, ConfigPacket.class, 2, Side.CLIENT);
        NETWORK.registerMessage(TimeUpdatePacket.TimeUpdatePacketHandler.class, TimeUpdatePacket.class, 3, Side.SERVER);
        Modules.propagate(event);
        LOGGER.info("Post-Init took " + profiler.getTime() + "ms");
    }

    /**
     * CI hook: no-op unless {@code -PsmokeTest} set the {@code collectiveframework.smokeTest} system property.
     * See {@link SmokeTestHook}.
     */
    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        SmokeTestHook.scheduleExitIfRequested();
    }

    /**
     * CI hook: lets {@link SmokeTestHook} wait for a fully started world before exiting.
     */
    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        SmokeTestHook.notifyServerStarted();
    }

    /**
     * Pushes every syncable config to a player that just joined.
     * <p>
     * This is a {@link PlayerEvent} handler, which is an FML game event: it must be registered on
     * {@code FMLCommonHandler.instance().bus()}, not on {@code MinecraftForge.EVENT_BUS} (CODE_REVIEW §1.1).
     */
    @SubscribeEvent
    public void onServerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (FMLCommonHandler.instance()
            .getSide() != Side.SERVER
            && FMLCommonHandler.instance()
                .getEffectiveSide() != Side.SERVER)
            return;
        for (ConfigRegistry.ConfigProxy proxy : ConfigRegistry.configs) {
            if (proxy.doesSync) NETWORK.sendTo(
                new ConfigPacket(proxy.fileName, proxy.handler.convertToString(proxy.config), false),
                (EntityPlayerMP) event.player);
        }
    }

    /**
     * Restores the local config files when the client disconnects from a server.
     * <p>
     * The previous implementation sent a "revert" {@link ConfigPacket} to the player on logout. That cannot
     * work: {@code PlayerLoggedOutEvent} is a server-side event, and the packet was addressed to a player whose
     * connection is already closing, while the config that needs reverting lives on the <em>client</em>. The
     * client-side event is {@link FMLNetworkEvent.ClientDisconnectionFromServerEvent}, and the revert is a local
     * file reload rather than a round trip.
     */
    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ConfigRegistry.revertSyncedConfigs();
    }
}

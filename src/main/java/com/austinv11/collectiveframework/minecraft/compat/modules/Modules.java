package com.austinv11.collectiveframework.minecraft.compat.modules;

import java.util.ArrayList;
import java.util.List;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLStateEvent;

/**
 * A registry for {@link IModule}
 */
public class Modules {

    private static List<IModule> modules = new ArrayList<IModule>();

    /**
     * Registers a module
     *
     * @param module The module to register
     */
    public static void registerModCompat(IModule module) {
        modules.add(module);
    }

    private static boolean canModuleLoad(IModule module) {
        String[] required = module.modsCompatible();
        // A module with no requirements used to never load, contradicting the documented "not strict" contract
        // (CODE_REVIEW §3.17).
        if (required == null || required.length == 0) return true;
        for (String modId : required) {
            if (modId != null && Loader.isModLoaded(modId)) return true;
        }
        return false;
    }

    private static void cleanModules() {
        List<IModule> uncleanedModules = new ArrayList<IModule>(modules);
        modules.clear();
        for (IModule m : uncleanedModules) if (canModuleLoad(m)) modules.add(m);
    }

    /**
     * Intended for internal use only
     */
    public static void init() {
        cleanModules();
    }

    /**
     * Propagates an FMLStateEvent to all modules
     *
     * @param event The event
     */
    public static void propagate(FMLStateEvent event) {
        for (IModule module : modules) {
            if (event instanceof FMLPreInitializationEvent) module.preInit((FMLPreInitializationEvent) event);
            else if (event instanceof FMLInitializationEvent) module.init((FMLInitializationEvent) event);
            else if (event instanceof FMLPostInitializationEvent) module.postInit((FMLPostInitializationEvent) event);
            // Any other state event is ignored rather than being cast to a post-init event, which threw
            // ClassCastException (CODE_REVIEW §3.17).
        }
    }
}

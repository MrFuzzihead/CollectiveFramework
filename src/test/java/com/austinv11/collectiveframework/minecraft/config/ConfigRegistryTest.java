package com.austinv11.collectiveframework.minecraft.config;

import java.io.File;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.austinv11.collectiveframework.minecraft.CollectiveFramework;
import com.austinv11.collectiveframework.minecraft.proxy.CommonProxy;

/**
 * Registry-level behaviour: config identity matching (CODE_REVIEW §1.2), the serialisation proxy lookup, and
 * the default handler's value accessor (CODE_REVIEW §1.4).
 * <p>
 * {@code DefaultConfigurationHandler} reads the current side from {@code CollectiveFramework.proxy} when it
 * writes, so a server-side proxy is installed for the tests that touch the file.
 */
public class ConfigRegistryTest {

    private static final String FILE = "cf-test-identity.cfg";

    @com.austinv11.collectiveframework.minecraft.config.Config(fileName = FILE)
    public static class IdentityConfig {

        @Description(comment = "a value", category = "Test")
        public static int value = 11;
    }

    private static final String NO_SYNC_FILE = "cf-test-nosync.cfg";

    @com.austinv11.collectiveframework.minecraft.config.Config(fileName = NO_SYNC_FILE, doesSync = false)
    public static class NoSyncConfig {

        @Description(comment = "a value", category = "Test")
        public static int value = 11;
    }

    private CommonProxy previousProxy;

    @Before
    public void installServerProxy() {
        previousProxy = CollectiveFramework.proxy;
        CollectiveFramework.proxy = new CommonProxy();
        IdentityConfig.value = 11;
        // noinspection ResultOfMethodCallIgnored
        new File("./config").mkdirs();
    }

    @After
    public void restoreProxyAndCleanUp() {
        CollectiveFramework.proxy = previousProxy;
        // noinspection ResultOfMethodCallIgnored
        new File("./config", FILE).delete();
        // noinspection ResultOfMethodCallIgnored
        new File("./config", NO_SYNC_FILE).delete();
    }

    private static ConfigRegistry.ConfigProxy proxyFor(Class<?> type, Object instance) throws Exception {
        return new ConfigRegistry.ConfigProxy(
            type.getAnnotation(com.austinv11.collectiveframework.minecraft.config.Config.class),
            instance);
    }

    /** Creates the config file and loads the handler's field map. */
    private static ConfigRegistry.ConfigProxy loadedProxy() throws Exception {
        ConfigRegistry.ConfigProxy proxy = proxyFor(IdentityConfig.class, new IdentityConfig());
        proxy.handler.loadFile(proxy.fileName, proxy.config, proxy.fields);
        return proxy;
    }

    // --------------------------------------------------------------------------- proxy delegation

    @Test
    public void getKeyDelegatesToTheRegisteredProxy() {
        Assert.assertEquals("Integer", ConfigRegistry.getKey(Integer.valueOf(1)));
        Assert.assertEquals("String", ConfigRegistry.getKey("s"));
        Assert.assertEquals("Boolean Array", ConfigRegistry.getKey(new boolean[0]));
    }

    @Test
    public void serializeAndDeserializeDelegateToTheRegisteredProxy() throws Exception {
        Assert.assertEquals("42", ConfigRegistry.serialize(Integer.valueOf(42)));
        Assert.assertEquals(Integer.valueOf(42), ConfigRegistry.deserialize("Integer", "42"));
    }

    @Test
    public void deserializeReturnsNullForAnUnknownKey() throws Exception {
        Assert.assertNull(ConfigRegistry.deserialize("NotAType", "whatever"));
    }

    // --------------------------------------------------------------------------------- registration

    @Test(expected = ConfigException.class)
    public void rejectsAnUnannotatedConfig() throws Exception {
        ConfigRegistry.registerConfig(new Object());
    }

    @Test(expected = ConfigException.class)
    public void rejectsANullConfig() throws Exception {
        ConfigRegistry.registerConfig(null);
    }

    // --------------------------------------------------------------------------- identity matching

    /**
     * CODE_REVIEW §1.2 (fixed) - {@code ConfigRegistry} used to match a config object with
     * {@code Object.equals}, i.e. by identity. {@code CollectiveFramework} registered one instance while the
     * config GUI handed over {@code Config.INSTANCE}, so the GUI always came up empty. Matching is now done by
     * class.
     */
    @Test
    public void proxyMatchesAnyInstanceOfItsConfigClass() throws Exception {
        ConfigRegistry.ConfigProxy proxy = proxyFor(IdentityConfig.class, new IdentityConfig());
        Assert.assertTrue(proxy.isFor(new IdentityConfig()));
        Assert.assertTrue(proxy.isFor(proxy.config));
    }

    @Test
    public void proxyDoesNotMatchAnotherConfigClass() throws Exception {
        ConfigRegistry.ConfigProxy proxy = proxyFor(IdentityConfig.class, new IdentityConfig());
        Assert.assertFalse(proxy.isFor(new Object()));
        Assert.assertFalse(proxy.isFor(null));
        Assert.assertFalse(proxy.isFor("not a config"));
    }

    // ------------------------------------------------------------- default handler value accessor

    /**
     * CODE_REVIEW §1.4 (fixed) - {@code getValue} used to return the {@link java.lang.reflect.Field} that
     * holds the value instead of the value itself, so every caller got a {@code Field} back.
     */
    @Test
    public void getValueReturnsTheValueNotTheField() throws Exception {
        ConfigRegistry.ConfigProxy proxy = loadedProxy();

        Object value = proxy.handler.getValue("value", "Test", proxy.config);

        Assert.assertNotNull("getValue returned null for a present key", value);
        Assert.assertFalse("getValue returned a java.lang.reflect.Field", value instanceof java.lang.reflect.Field);
        Assert.assertEquals(Integer.valueOf(11), value);
    }

    @Test
    public void getValueReflectsLaterChanges() throws Exception {
        ConfigRegistry.ConfigProxy proxy = loadedProxy();

        IdentityConfig.value = 99;

        Assert.assertEquals(Integer.valueOf(99), proxy.handler.getValue("value", "Test", proxy.config));
    }

    @Test
    public void getValueReturnsNullForAMissingKey() throws Exception {
        ConfigRegistry.ConfigProxy proxy = loadedProxy();
        Assert.assertNull(proxy.handler.getValue("absent", "Test", proxy.config));
        Assert.assertNull(proxy.handler.getValue("value", "NoSuchCategory", proxy.config));
    }

    @Test
    public void hasValueReflectsTheLoadedFields() throws Exception {
        ConfigRegistry.ConfigProxy proxy = loadedProxy();
        Assert.assertTrue(proxy.handler.hasValue("value", "Test"));
        Assert.assertFalse(proxy.handler.hasValue("absent", "Test"));
    }

    // -------------------------------------------------------------------------- GUI categories

    /**
     * CODE_REVIEW §1.2 - {@code ConfigRegistry#getCategories} is what the config GUI builds its list from. It
     * refuses to run on a server, so a client-side proxy is installed for the test. This is the same path the
     * client game test exercises, but at unit speed.
     */
    @Test
    public void getCategoriesBuildsTheGuiList() throws Exception {
        ConfigRegistry.ConfigProxy proxy = loadedProxy();
        ConfigRegistry.configs.add(proxy);
        CommonProxy previous = CollectiveFramework.proxy;
        CollectiveFramework.proxy = new CommonProxy() {

            @Override
            public cpw.mods.fml.relauncher.Side getSide() {
                return cpw.mods.fml.relauncher.Side.CLIENT;
            }
        };
        try {
            java.util.ArrayList<cpw.mods.fml.client.config.IConfigElement> categories = ConfigRegistry
                .getCategories(proxy.config, "collectiveframework");

            Assert.assertFalse("the config GUI would be empty", categories.isEmpty());
            boolean sawTestCategory = false;
            for (cpw.mods.fml.client.config.IConfigElement element : categories) {
                Assert.assertNotNull(element.getName());
                if (element.getName()
                    .contains("Test")) sawTestCategory = true;
            }
            Assert.assertTrue("the 'Test' category is missing from " + categories, sawTestCategory);
        } finally {
            CollectiveFramework.proxy = previous;
            ConfigRegistry.configs.remove(proxy);
        }
    }

    @Test
    public void getCategoriesIsEmptyForAnUnregisteredConfig() throws Exception {
        CommonProxy previous = CollectiveFramework.proxy;
        CollectiveFramework.proxy = new CommonProxy() {

            @Override
            public cpw.mods.fml.relauncher.Side getSide() {
                return cpw.mods.fml.relauncher.Side.CLIENT;
            }
        };
        try {
            Assert.assertTrue(
                ConfigRegistry.getCategories(new Object(), "collectiveframework")
                    .isEmpty());
        } finally {
            CollectiveFramework.proxy = previous;
        }
    }

    // ------------------------------------------------------------------------ server sync revert

    /**
     * CODE_REVIEW §1.1 - the client used to be told to revert via a {@code ConfigPacket} sent to a player who
     * was already logging out, which can never be delivered. The revert is now a local file reload triggered by
     * {@code FMLNetworkEvent.ClientDisconnectionFromServerEvent}.
     */
    @Test
    public void revertSyncedConfigsRestoresTheFileValues() throws Exception {
        ConfigRegistry.ConfigProxy proxy = loadedProxy();
        ConfigRegistry.configs.add(proxy);
        try {
            IdentityConfig.value = 99; // pretend a server pushed this

            Assert.assertEquals(1, ConfigRegistry.revertSyncedConfigs());
            Assert.assertEquals(11, IdentityConfig.value);
        } finally {
            ConfigRegistry.configs.remove(proxy);
        }
    }

    @Test
    public void revertSyncedConfigsSkipsConfigsThatDoNotSync() throws Exception {
        ConfigRegistry.ConfigProxy proxy = new ConfigRegistry.ConfigProxy(
            NoSyncConfig.class.getAnnotation(com.austinv11.collectiveframework.minecraft.config.Config.class),
            new NoSyncConfig());
        proxy.handler.loadFile(proxy.fileName, proxy.config, proxy.fields);
        ConfigRegistry.configs.add(proxy);
        try {
            NoSyncConfig.value = 99;

            Assert.assertEquals(0, ConfigRegistry.revertSyncedConfigs());
            Assert.assertEquals("a non-syncing config must not be touched", 99, NoSyncConfig.value);
        } finally {
            ConfigRegistry.configs.remove(proxy);
        }
    }
}

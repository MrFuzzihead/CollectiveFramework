package com.austinv11.collectiveframework.minecraft.config;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * Field discovery and annotation handling for {@link ConfigRegistry.ConfigProxy}. Pure reflection, no game
 * required.
 */
public class ConfigProxyTest {

    @com.austinv11.collectiveframework.minecraft.config.Config(
        fileName = "explicit.cfg",
        exclude = { "INSTANCE", "EXCLUDED" })
    public static class ExplicitlyNamedConfig {

        public static final ExplicitlyNamedConfig INSTANCE = new ExplicitlyNamedConfig();
        public static final String EXCLUDED = "not-a-config-value";

        @Description(comment = "a counter", category = "Numbers")
        public static int counter = 3;

        @Description(comment = "a label")
        public static String label = "hi";

        public static boolean plain = true;
    }

    @com.austinv11.collectiveframework.minecraft.config.Config
    public static class UnnamedConfig {

        public int instanceField = 1;
    }

    @com.austinv11.collectiveframework.minecraft.config.Config(doesSync = false)
    public static class NoSyncConfig {

        public int value = 1;
    }

    private static ConfigRegistry.ConfigProxy proxyFor(Class<?> type, Object instance) throws Exception {
        return new ConfigRegistry.ConfigProxy(
            type.getAnnotation(com.austinv11.collectiveframework.minecraft.config.Config.class),
            instance);
    }

    @Test
    public void readsTheExplicitFileName() throws Exception {
        Assert
            .assertEquals("explicit.cfg", proxyFor(ExplicitlyNamedConfig.class, new ExplicitlyNamedConfig()).fileName);
    }

    @Test
    public void fallsBackToTheClassNameAsFileName() throws Exception {
        Assert.assertEquals("UnnamedConfig.cfg", proxyFor(UnnamedConfig.class, new UnnamedConfig()).fileName);
    }

    @Test
    public void readsDoesSync() throws Exception {
        Assert.assertTrue(proxyFor(ExplicitlyNamedConfig.class, new ExplicitlyNamedConfig()).doesSync);
        Assert.assertFalse(proxyFor(NoSyncConfig.class, new NoSyncConfig()).doesSync);
    }

    @Test
    public void groupsFieldsByTheirDeclaredCategory() throws Exception {
        ConfigRegistry.ConfigProxy proxy = proxyFor(ExplicitlyNamedConfig.class, new ExplicitlyNamedConfig());

        Assert.assertEquals(2, proxy.fields.size());
        Assert.assertTrue(proxy.fields.containsKey("Numbers"));
        Assert.assertTrue(proxy.fields.containsKey("General"));

        Assert.assertEquals(
            1,
            proxy.fields.get("Numbers")
                .size());
        Assert.assertTrue(
            proxy.fields.get("Numbers")
                .containsKey("counter"));
    }

    @Test
    public void fieldsWithoutADescriptionGoToGeneral() throws Exception {
        Map<String, Field> general = proxyFor(ExplicitlyNamedConfig.class, new ExplicitlyNamedConfig()).fields
            .get("General");
        Assert.assertTrue(general.containsKey("label"));
        Assert.assertTrue(general.containsKey("plain"));
    }

    @Test
    public void excludedFieldsAreIgnored() throws Exception {
        ConfigRegistry.ConfigProxy proxy = proxyFor(ExplicitlyNamedConfig.class, new ExplicitlyNamedConfig());
        for (Map<String, Field> category : proxy.fields.values()) {
            Assert.assertFalse(category.containsKey("INSTANCE"));
            Assert.assertFalse(category.containsKey("EXCLUDED"));
        }
    }

    @Test
    public void categoriesAreSortedAlphabetically() throws Exception {
        java.util.Iterator<String> categories = proxyFor(
            ExplicitlyNamedConfig.class,
            new ExplicitlyNamedConfig()).fields.keySet()
                .iterator();
        Assert.assertEquals("General", categories.next());
        Assert.assertEquals("Numbers", categories.next());
    }

    @Test
    public void fieldsWithinACategoryAreSorted() throws Exception {
        Assert.assertEquals(
            "label",
            proxyFor(ExplicitlyNamedConfig.class, new ExplicitlyNamedConfig()).fields.get("General")
                .keySet()
                .iterator()
                .next());
    }

    /**
     * CODE_REVIEW §3.18 (fixed) - {@code ConfigProxy} used to walk {@code getDeclaredFields()} with no filter,
     * so compiler-generated fields and {@code static final} constants became config entries.
     */
    @Test
    public void syntheticFieldsShouldBeIgnored() throws Exception {
        ConfigRegistry.ConfigProxy proxy = proxyFor(ExplicitlyNamedConfig.class, new ExplicitlyNamedConfig());
        for (Map<String, Field> category : proxy.fields.values()) {
            for (String name : category.keySet()) {
                Assert.assertFalse("synthetic field leaked: " + name, name.startsWith("$"));
            }
        }
    }
}

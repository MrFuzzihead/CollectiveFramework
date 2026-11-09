package com.austinv11.collectiveframework.minecraft.config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.austinv11.collectiveframework.minecraft.CollectiveFramework;
import com.austinv11.collectiveframework.minecraft.proxy.CommonProxy;

/**
 * End-to-end tests for the default config file format: write a config, read it back, and verify that the file
 * the framework produces can actually be parsed again.
 * <p>
 * {@code DefaultConfigurationHandler.writeToStream} reads the current side from
 * {@code CollectiveFramework.proxy}, so these tests install a server-side {@link CommonProxy} (which is what
 * skips {@code clientSideOnly} fields) and write into {@code ./config}, the same directory the mod uses.
 */
public class ConfigFileRoundTripTest {

    private static final String ROUND_TRIP_FILE = "cf-test-roundtrip.cfg";
    private static final String BRACE_FILE = "cf-test-brace.cfg";

    @com.austinv11.collectiveframework.minecraft.config.Config(fileName = ROUND_TRIP_FILE, exclude = { "INSTANCE" })
    public static class RoundTripConfig {

        public static final RoundTripConfig INSTANCE = new RoundTripConfig();

        @Description(comment = "a counter", category = "Numbers")
        public static int counter = 7;

        @Description(comment = "a label", category = "Numbers")
        public static String label = "seven";
    }

    /**
     * Both fields live in one category, and the {@code clientSideOnly} one sorts last. On a dedicated server
     * it is skipped while writing, which is what exposes the brace bug.
     */
    @com.austinv11.collectiveframework.minecraft.config.Config(fileName = BRACE_FILE, exclude = { "INSTANCE" })
    public static class TrailingClientOnlyConfig {

        public static final TrailingClientOnlyConfig INSTANCE = new TrailingClientOnlyConfig();

        @Description(comment = "a normal field", category = "Mixed")
        public static boolean normalField = true;

        @Description(comment = "client only", category = "Mixed", clientSideOnly = true)
        public static String zzClientOnly = "client";
    }

    private CommonProxy previousProxy;

    @Before
    public void installServerProxy() {
        previousProxy = CollectiveFramework.proxy;
        CollectiveFramework.proxy = new CommonProxy();
        // The config values are static, so reset them so test order cannot leak state.
        RoundTripConfig.counter = 7;
        RoundTripConfig.label = "seven";
        TrailingClientOnlyConfig.normalField = true;
        TrailingClientOnlyConfig.zzClientOnly = "client";
        // The handler writes into ./config, which normally already exists in a Forge game directory.
        // noinspection ResultOfMethodCallIgnored
        new File("./config").mkdirs();
    }

    @After
    public void restoreProxyAndCleanUp() {
        CollectiveFramework.proxy = previousProxy;
        // noinspection ResultOfMethodCallIgnored
        new File("./config", ROUND_TRIP_FILE).delete();
        // noinspection ResultOfMethodCallIgnored
        new File("./config", BRACE_FILE).delete();
    }

    private static ConfigRegistry.ConfigProxy proxyFor(Class<?> type, Object instance) throws Exception {
        return new ConfigRegistry.ConfigProxy(
            type.getAnnotation(com.austinv11.collectiveframework.minecraft.config.Config.class),
            instance);
    }

    @Test
    public void writesAFileAndReadsTheValuesBack() throws Exception {
        ConfigRegistry.ConfigProxy proxy = proxyFor(RoundTripConfig.class, RoundTripConfig.INSTANCE);
        proxy.handler.loadFile(proxy.fileName, proxy.config, proxy.fields);

        File written = new File("./config", ROUND_TRIP_FILE);
        Assert.assertTrue("config file was not created", written.isFile());

        // Change the in-memory values, then reload from disk.
        RoundTripConfig.counter = 99;
        RoundTripConfig.label = "changed";
        proxy.handler.loadFile(proxy.fileName, proxy.config, proxy.fields);

        Assert.assertEquals(7, RoundTripConfig.counter);
        Assert.assertEquals("seven", RoundTripConfig.label);
    }

    @Test
    public void writtenFileIsWellFormed() throws Exception {
        ConfigRegistry.ConfigProxy proxy = proxyFor(RoundTripConfig.class, RoundTripConfig.INSTANCE);
        proxy.handler.loadFile(proxy.fileName, proxy.config, proxy.fields);

        List<String> lines = Files.readAllLines(new File("./config", ROUND_TRIP_FILE).toPath(), StandardCharsets.UTF_8);
        Assert.assertEquals("unbalanced braces in:\n" + String.join("\n", lines), 1, count(lines, "{"));
        Assert.assertEquals("unbalanced braces in:\n" + String.join("\n", lines), 1, count(lines, "}"));
    }

    @Test
    public void writtenFileContainsTypeTaggedEntries() throws Exception {
        ConfigRegistry.ConfigProxy proxy = proxyFor(RoundTripConfig.class, RoundTripConfig.INSTANCE);
        proxy.handler.loadFile(proxy.fileName, proxy.config, proxy.fields);

        List<String> lines = Files.readAllLines(new File("./config", ROUND_TRIP_FILE).toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(lines.toString(), lines.contains("\tInteger:counter=7"));
        Assert.assertTrue(lines.toString(), lines.contains("\tString:label=seven"));
    }

    @Test
    public void convertToStringSkipsClientSideOnlyFieldsOnTheServer() throws Exception {
        ConfigRegistry.ConfigProxy proxy = proxyFor(TrailingClientOnlyConfig.class, TrailingClientOnlyConfig.INSTANCE);
        proxy.handler.loadFile(proxy.fileName, proxy.config, proxy.fields);
        String serialised = proxy.handler.convertToString(proxy.config);

        Assert.assertTrue(serialised, serialised.contains("normalField=true"));
        Assert.assertFalse("clientSideOnly field leaked to the server config", serialised.contains("zzClientOnly"));
    }

    private static int count(List<String> lines, String needle) {
        int total = 0;
        for (String line : lines) {
            int index = line.indexOf(needle);
            while (index >= 0) {
                total++;
                index = line.indexOf(needle, index + 1);
            }
        }
        return total;
    }

    /**
     * CODE_REVIEW §1.5 (fixed) - {@code writeToStream} used to emit the closing brace only when the loop
     * reached {@code i == size}, so a category whose last field was skipped produced an unterminated block
     * and an unparseable file. The block is now opened on the first written field and always closed.
     */
    @Test
    public void trailingClientOnlyFieldShouldNotBreakTheFile() throws Exception {
        ConfigRegistry.ConfigProxy proxy = proxyFor(TrailingClientOnlyConfig.class, TrailingClientOnlyConfig.INSTANCE);
        proxy.handler.loadFile(proxy.fileName, proxy.config, proxy.fields);

        List<String> lines = Files.readAllLines(new File("./config", BRACE_FILE).toPath(), StandardCharsets.UTF_8);
        Assert.assertEquals("unbalanced braces in:\n" + String.join("\n", lines), 1, count(lines, "{"));
        Assert.assertEquals("unbalanced braces in:\n" + String.join("\n", lines), 1, count(lines, "}"));
    }

    /**
     * CODE_REVIEW §1.5 (fixed) - the malformed file also made the *next* load throw, because
     * {@code readFromReader} kept its "inside a block" flag set and then tried to parse a category header as a
     * {@code key:field=value} line. The file must survive a write/read/write cycle.
     */
    @Test
    public void trailingClientOnlyFieldSurvivesAReload() throws Exception {
        ConfigRegistry.ConfigProxy proxy = proxyFor(TrailingClientOnlyConfig.class, TrailingClientOnlyConfig.INSTANCE);
        proxy.handler.loadFile(proxy.fileName, proxy.config, proxy.fields);

        TrailingClientOnlyConfig.normalField = false;
        proxy.handler.loadFile(proxy.fileName, proxy.config, proxy.fields);

        Assert.assertTrue("the written value was not read back", TrailingClientOnlyConfig.normalField);
    }
}

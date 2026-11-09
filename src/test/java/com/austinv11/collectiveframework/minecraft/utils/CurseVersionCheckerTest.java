package com.austinv11.collectiveframework.minecraft.utils;

import org.junit.Assert;
import org.junit.Test;

import com.austinv11.collectiveframework.testsupport.LocalHttpServer;

/**
 * CODE_REVIEW §3.3 - every accessor dereferenced the parsed response unconditionally, so any network failure
 * turned into a {@link NullPointerException} in the caller.
 * <p>
 * The API base url is injectable for exactly this reason, so both the success and failure paths are exercised
 * against a local server rather than the (long dead) Curse widget endpoint.
 */
public class CurseVersionCheckerTest {

    private static final String DEAD_URL = "http://127.0.0.1:1/@MOD@.json";

    private static String widgetJson(String fileName, String version) {
        return "{\"title\":\"Test\",\"project_url\":\"https://example.invalid/project\","
            + "\"download\":{\"id\":42,\"name\":\""
            + fileName
            + "\",\"version\":\""
            + version
            + "\"}}";
    }

    // ------------------------------------------------------------------------------------ failure

    @Test
    public void anUnreachableEndpointDoesNotThrow() {
        CurseVersionChecker checker = new CurseVersionChecker("slug", "file.jar", DEAD_URL);

        Assert.assertFalse(checker.isUpdateAvailable());
        Assert.assertNull(checker.getDownloadUrl());
        Assert.assertNull(checker.getUpdateFileName());
        Assert.assertNull(checker.getProjectUrl());
        Assert.assertTrue(checker.isUnknown());
    }

    @Test
    public void anErrorStatusDoesNotThrow() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(500, "boom")) {
            CurseVersionChecker checker = new CurseVersionChecker("slug", "file.jar", server.url("/@MOD@.json"));

            Assert.assertFalse(checker.isUpdateAvailable());
            Assert.assertTrue(checker.isUnknown());
        }
    }

    @Test
    public void anEmptyResponseDoesNotThrow() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, "")) {
            CurseVersionChecker checker = new CurseVersionChecker("slug", "file.jar", server.url("/@MOD@.json"));

            Assert.assertFalse(checker.isUpdateAvailable());
            Assert.assertTrue(checker.isUnknown());
        }
    }

    @Test
    public void aResponseWithoutADownloadDoesNotThrow() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, "{\"title\":\"Test\"}")) {
            CurseVersionChecker checker = new CurseVersionChecker("slug", "file.jar", server.url("/@MOD@.json"));

            Assert.assertFalse(checker.isUpdateAvailable());
            Assert.assertTrue(checker.isUnknown());
        }
    }

    // ------------------------------------------------------------------------------------ success

    @Test
    public void aNewerFileForTheSameMinecraftVersionIsAnUpdate() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, widgetJson("newer.jar", mcVersion()))) {
            CurseVersionChecker checker = new CurseVersionChecker("slug", "current.jar", server.url("/@MOD@.json"));

            Assert.assertFalse(checker.isUnknown());
            Assert.assertTrue(checker.isUpdateAvailable());
            Assert.assertEquals("newer.jar", checker.getUpdateFileName());
            Assert.assertEquals("https://example.invalid/project", checker.getProjectUrl());
            Assert.assertTrue(
                checker.getDownloadUrl(),
                checker.getDownloadUrl()
                    .contains("/files/42/download"));
        }
    }

    @Test
    public void theSameFileIsNotAnUpdate() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, widgetJson("current.jar", mcVersion()))) {
            CurseVersionChecker checker = new CurseVersionChecker("slug", "current.jar", server.url("/@MOD@.json"));

            Assert.assertFalse(checker.isUpdateAvailable());
            Assert.assertEquals("current.jar", checker.getUpdateFileName());
        }
    }

    @Test
    public void aFileForAnotherMinecraftVersionIsNotAnUpdate() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, widgetJson("newer.jar", "0.0.0"))) {
            CurseVersionChecker checker = new CurseVersionChecker("slug", "current.jar", server.url("/@MOD@.json"));

            Assert.assertFalse(checker.isUpdateAvailable());
        }
    }

    @Test
    public void theResultIsCached() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, widgetJson("newer.jar", mcVersion()))) {
            CurseVersionChecker checker = new CurseVersionChecker("slug", "current.jar", server.url("/@MOD@.json"));

            checker.isUpdateAvailable();
            checker.getUpdateFileName();
            checker.getProjectUrl();

            Assert.assertEquals(
                "the endpoint must be queried once",
                1,
                server.getRequests()
                    .size());
        }
    }

    @Test
    public void theCurseIdIsSubstitutedIntoTheUrl() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, widgetJson("f.jar", mcVersion()))) {
            new CurseVersionChecker("my-mod", "f.jar", server.url("/@MOD@.json")).isUpdateAvailable();

            Assert.assertEquals("/my-mod.json", server.getLastRequest().path);
        }
    }

    private static String mcVersion() {
        return net.minecraftforge.common.MinecraftForge.MC_VERSION;
    }
}

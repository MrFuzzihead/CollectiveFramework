package com.austinv11.collectiveframework.dependencies.download;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.austinv11.collectiveframework.testsupport.LocalHttpServer;

/**
 * CODE_REVIEW §3.4 - {@code BinaryProvider} dereferenced {@code header.get(null)} (NPE when the status line was
 * absent), had no timeouts, leaked its streams on failure, ignored the status code, and followed redirects with a
 * hand-rolled loop that read the headers before the request was made and only handled absolute
 * {@code Location}s. {@code PlainTextProvider} wrote with a bare {@code FileWriter} (platform charset) and did
 * not create the parent directory.
 * <p>
 * Both are exercised against a real loopback HTTP server.
 */
public class DownloadProviderTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    // ------------------------------------------------------------------------- BinaryProvider

    @Test
    public void binaryProviderWritesTheBody() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, "binary payload")) {
            File target = new File(folder.getRoot(), "out.bin");

            Assert.assertTrue(new BinaryProvider().downloadFile(server.url("/file"), target.getAbsolutePath()));
            Assert.assertEquals(
                "binary payload",
                new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void binaryProviderCreatesTheParentDirectory() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, "x")) {
            File target = new File(new File(folder.getRoot(), "a/b"), "out.bin");

            Assert.assertTrue(new BinaryProvider().downloadFile(server.url("/"), target.getAbsolutePath()));
            Assert.assertTrue(target.isFile());
        }
    }

    /** §3.4 - the status code was ignored, so a 404 was written to disk as if it were the file. */
    @Test
    public void binaryProviderRejectsANonSuccessStatus() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(404, "not found")) {
            File target = new File(folder.getRoot(), "out.bin");

            Assert.assertFalse(new BinaryProvider().downloadFile(server.url("/missing"), target.getAbsolutePath()));
            Assert.assertFalse("a failed download must not create the file", target.exists());
        }
    }

    /** §3.4 - the hand-rolled redirect loop did not work; the JDK's own handling does. */
    @Test
    public void binaryProviderFollowsARedirect() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.start(exchange -> {
            if (exchange.getRequestURI()
                .getPath()
                .equals("/start")) {
                exchange.getResponseHeaders()
                    .add("Location", "/final");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
                return;
            }
            LocalHttpServer.textResponse(200, "redirected")
                .handle(exchange);
        })) {
            File target = new File(folder.getRoot(), "out.bin");

            Assert.assertTrue(new BinaryProvider().downloadFile(server.url("/start"), target.getAbsolutePath()));
            Assert.assertEquals("redirected", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void binaryProviderReturnsFalseOnAConnectionFailure() throws Exception {
        File target = new File(folder.getRoot(), "out.bin");
        Assert.assertFalse(new BinaryProvider().downloadFile("http://127.0.0.1:1/file", target.getAbsolutePath()));
        Assert.assertFalse(target.exists());
    }

    @Test
    public void binaryProviderRejectsNullArguments() {
        try {
            new BinaryProvider().downloadFile(null, "x");
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // documented contract
        }
    }

    @Test
    public void binaryProviderReportsItsCapability() {
        Assert.assertEquals(java.util.EnumSet.of(FileType.BINARY), new BinaryProvider().getCapabilities());
    }

    // ---------------------------------------------------------------------- PlainTextProvider

    @Test
    public void plainTextProviderWritesUtf8() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, "h\u00e9llo\nthere")) {
            File target = new File(folder.getRoot(), "out.txt");

            Assert.assertTrue(new PlainTextProvider().downloadFile(server.url("/"), target.getAbsolutePath()));
            Assert.assertEquals(
                "h\u00e9llo\nthere\n",
                new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
        }
    }

    /** §3.4 - a bare {@code FileWriter} failed when the directory did not exist. */
    @Test
    public void plainTextProviderCreatesTheParentDirectory() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, "x")) {
            File target = new File(new File(folder.getRoot(), "a/b"), "out.txt");

            Assert.assertTrue(new PlainTextProvider().downloadFile(server.url("/"), target.getAbsolutePath()));
            Assert.assertTrue(target.isFile());
        }
    }

    @Test
    public void plainTextProviderReturnsFalseOnFailure() throws Exception {
        File target = new File(folder.getRoot(), "out.txt");
        Assert.assertFalse(new PlainTextProvider().downloadFile("http://127.0.0.1:1/x", target.getAbsolutePath()));
        Assert.assertFalse(target.exists());
    }

    @Test
    public void plainTextProviderReportsItsCapability() {
        Assert.assertEquals(java.util.EnumSet.of(FileType.PLAIN_TEXT), new PlainTextProvider().getCapabilities());
    }
}

package com.austinv11.collectiveframework.utils;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.austinv11.collectiveframework.testsupport.LocalHttpServer;

/**
 * CODE_REVIEW §3.4 - {@code WebUtils} set only a connect timeout, so a server that accepted the connection and
 * then stalled hung the calling thread forever; it decoded with the platform charset; it called
 * {@code connect()} *after* opening the input stream, which already connects; and it leaked the stream when the
 * read threw.
 * <p>
 * Every test here talks to a real loopback HTTP server, so the timeouts, headers and stream handling are
 * exercised rather than mocked.
 */
public class WebUtilsTest {

    // ------------------------------------------------------------------------------ happy path

    @Test
    public void readsAPageLineByLine() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, "one\ntwo\nthree")) {
            List<String> lines = WebUtils.readURL(server.url("/page"), 5000);
            Assert.assertEquals(Arrays.asList("one", "two", "three"), lines);
        }
    }

    @Test
    public void decodesAsUtf8() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, "h\u00e9llo w\u00f6rld")) {
            Assert.assertEquals(
                "h\u00e9llo w\u00f6rld",
                WebUtils.readURL(server.url("/"), 5000)
                    .get(0));
        }
    }

    @Test
    public void addsAMissingScheme() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, "ok")) {
            String withoutScheme = server.url("/")
                .replace("http://", "");
            Assert.assertEquals(
                "ok",
                WebUtils.readURL(withoutScheme, 5000)
                    .get(0));
        }
    }

    // ------------------------------------------------------------------------------- timeouts

    /** §3.4 - only a connect timeout was set, so a stalled response blocked forever. */
    @Test(expected = SocketTimeoutException.class)
    public void aStalledResponseHitsTheReadTimeout() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.stalling(3000, "too late")) {
            WebUtils.readURL(server.url("/"), 3000, 150);
        }
    }

    @Test(expected = SocketTimeoutException.class)
    public void theSingleTimeoutOverloadAppliesToBothPhases() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.stalling(3000, "too late")) {
            WebUtils.readURL(server.url("/"), 150);
        }
    }

    @Test
    public void anUnreachableHostFailsFast() {
        // Port 1 on loopback refuses immediately, so this is a connect failure rather than a hang.
        try {
            WebUtils.readURL("http://127.0.0.1:1/", 500);
            Assert.fail("expected IOException");
        } catch (IOException expected) {
            // documented contract
        }
    }

    // ----------------------------------------------------------------------------------- errors

    /** A non-2xx response must be reported, not parsed as if it were content. */
    @Test
    public void getReportsANonSuccessStatus() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(404, "nope")) {
            try {
                WebUtils.get(server.url("/missing"), 5000, null);
                Assert.fail("expected IOException");
            } catch (IOException expected) {
                Assert.assertTrue(
                    expected.getMessage(),
                    expected.getMessage()
                        .contains("404"));
            }
        }
    }

    @Test
    public void getSucceedsOn200() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, "ok")) {
            Assert.assertEquals(
                "ok",
                WebUtils.get(server.url("/"), 5000, null)
                    .get(0));
        }
    }

    // ---------------------------------------------------------------------------------- requests

    @Test
    public void getSendsAGetRequest() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, "ok")) {
            WebUtils.get(server.url("/path"), 5000, null);
            Assert.assertEquals("GET", server.getLastRequest().method);
            Assert.assertEquals("/path", server.getLastRequest().path);
        }
    }

    @Test
    public void postSendsAPostRequest() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, "ok")) {
            WebUtils.post(server.url("/"), 5000, null);
            Assert.assertEquals("POST", server.getLastRequest().method);
        }
    }

    @Test
    public void getSendsTheProvidedHeaders() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, "ok")) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("User-Agent", "CollectiveFramework-Test");
            headers.put("X-Custom", "value");

            WebUtils.get(server.url("/"), 5000, headers);

            LocalHttpServer.Recorded request = server.getLastRequest();
            Assert.assertNotNull(request);
            Assert.assertEquals("CollectiveFramework-Test", request.headers.get("user-agent"));
            Assert.assertEquals("value", request.headers.get("x-custom"));
        }
    }

    @Test
    public void rejectsANullUrl() throws Exception {
        try {
            WebUtils.readURL(null, 100);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // documented contract
        }
    }
}

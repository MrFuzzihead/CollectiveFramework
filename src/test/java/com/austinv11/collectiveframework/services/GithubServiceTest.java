package com.austinv11.collectiveframework.services;

import org.junit.Assert;
import org.junit.Test;

import com.austinv11.collectiveframework.testsupport.LocalHttpServer;

/**
 * CODE_REVIEW §3.4 - {@code GithubService} sent no {@code User-Agent} (GitHub answers 403 without one), set no
 * read timeout, ignored its {@code start} parameter entirely, sent the ref as a {@code sha} *header* rather than
 * the documented query parameter, and returned {@code null} on every failure so callers NPE'd.
 * <p>
 * The API base url is injectable for exactly this reason, so the whole class is exercised against a local server.
 */
public class GithubServiceTest {

    private static final String COMMITS_JSON = "[{\"sha\":\"abc123\",\"commit\":{\"message\":\"first line\\nsecond\","
        + "\"author\":{\"name\":\"Tester\"}}}]";

    private static final String RELEASES_JSON = "[{\"name\":\"v1\",\"tag_name\":\"1.0\"}]";

    // ------------------------------------------------------------------------------------ headers

    @Test
    public void getReleasesSendsAUserAgent() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, RELEASES_JSON)) {
            GithubService service = new GithubService("owner", "repo", "main", server.url(""));

            Assert.assertEquals(1, service.getReleases().length);

            LocalHttpServer.Recorded request = server.getLastRequest();
            Assert.assertNotNull(request);
            Assert.assertEquals(GithubService.USER_AGENT, request.headers.get("user-agent"));
            Assert.assertEquals("/repos/owner/repo/releases", request.path);
        }
    }

    // ----------------------------------------------------------------------------- commits + ref

    /** §3.4 - {@code start} was ignored and the ref went out as a header instead of {@code ?sha=}. */
    @Test
    public void getCommitsUsesTheRefAsAQueryParameter() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, COMMITS_JSON)) {
            GithubService service = new GithubService("owner", "repo", "main", server.url(""));

            GithubService.Commit[] commits = service.getCommitsToRepo("develop");

            Assert.assertEquals(1, commits.length);
            Assert.assertEquals("abc123", commits[0].sha);
            LocalHttpServer.Recorded request = server.getLastRequest();
            Assert.assertEquals("/repos/owner/repo/commits", request.path);
            Assert.assertEquals("sha=develop", request.query);
            Assert.assertNull("the ref must not be sent as a header", request.headers.get("sha"));
        }
    }

    @Test
    public void getCommitsFallsBackToTheBranch() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, COMMITS_JSON)) {
            GithubService service = new GithubService("owner", "repo", "main", server.url(""));

            service.getCommitsToRepo(null);

            Assert.assertEquals("sha=main", server.getLastRequest().query);
        }
    }

    @Test
    public void getCommitsEncodesTheRef() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, COMMITS_JSON)) {
            GithubService service = new GithubService("owner", "repo", "main", server.url(""));

            service.getCommitsToRepo("feature/x y");

            Assert.assertEquals("sha=feature%2Fx+y", server.getLastRequest().query);
        }
    }

    @Test
    public void getCommitRequestsTheSha() throws Exception {
        try (LocalHttpServer server = LocalHttpServer
            .responding(200, "{\"sha\":\"abc123\",\"commit\":{\"message\":\"only\"}}")) {
            GithubService service = new GithubService("owner", "repo", "main", server.url(""));

            Assert.assertEquals("abc123", service.getCommitToRepo("abc123").sha);
            Assert.assertEquals("/repos/owner/repo/commits/abc123", server.getLastRequest().path);
        }
    }

    // ------------------------------------------------------------------------------------ failure

    /** §3.4 - every failure path returned {@code null}, so callers NPE'd. */
    @Test
    public void getReleasesReturnsAnEmptyArrayOnFailure() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(500, "boom")) {
            GithubService service = new GithubService("owner", "repo", "main", server.url(""));
            Assert.assertEquals(0, service.getReleases().length);
        }
    }

    @Test
    public void getCommitsReturnsAnEmptyArrayOnFailure() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(500, "boom")) {
            GithubService service = new GithubService("owner", "repo", "main", server.url(""));
            Assert.assertEquals(0, service.getCommitsToRepo("main").length);
        }
    }

    @Test
    public void getReleasesReturnsAnEmptyArrayWhenTheHostIsUnreachable() {
        GithubService service = new GithubService("owner", "repo", "main", "http://127.0.0.1:1");
        Assert.assertEquals(0, service.getReleases().length);
    }

    @Test
    public void getCommitReturnsNullForAnEmptySha() {
        GithubService service = new GithubService("owner", "repo", "main", "http://127.0.0.1:1");
        Assert.assertNull(service.getCommitToRepo(""));
        Assert.assertNull(service.getCommitToRepo(null));
    }

    // ---------------------------------------------------------------------------------- changelog

    @Test
    public void generateChangelogClassifiesCommits() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, COMMITS_JSON)) {
            GithubService service = new GithubService("owner", "repo", "main", server.url(""));

            String changelog = service.generateChangelog("main");

            Assert.assertTrue(changelog, changelog.contains("abc123"));
            Assert.assertTrue(changelog, changelog.contains("Tester"));
        }
    }

    @Test
    public void generateChangelogSurvivesAMalformedResponse() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.responding(200, "[]")) {
            GithubService service = new GithubService("owner", "repo", "main", server.url(""));
            Assert.assertEquals("", service.generateChangelog("main"));
        }
    }

    // --------------------------------------------------------------------------------------- url

    @Test
    public void constructUrlBuildsRawAndWebUrls() {
        GithubService service = new GithubService("owner", "repo", "main");
        Assert.assertEquals("https://raw.github.com/owner/repo/main/", service.constructUrl(true));
        Assert.assertEquals("https://github.com/owner/repo/main/", service.constructUrl(false));
    }

    @Test
    public void theDefaultConstructorUsesMaster() {
        Assert.assertEquals(
            "https://raw.github.com/owner/repo/master/",
            new GithubService("owner", "repo").constructUrl(true));
    }
}

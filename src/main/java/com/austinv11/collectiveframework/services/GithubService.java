package com.austinv11.collectiveframework.services;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.austinv11.collectiveframework.utils.LogicUtils;
import com.austinv11.collectiveframework.utils.StringUtils;
import com.austinv11.collectiveframework.utils.WebUtils;
import com.google.gson.Gson;

/**
 * This contains helpful APIs with Github.
 */
public class GithubService {

    /** GitHub rejects requests without a User-Agent. */
    public static final String USER_AGENT = "CollectiveFramework";

    /** Connect and read timeout for API calls, in milliseconds. */
    public static final int TIMEOUT_MS = 10_000;

    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager
        .getLogger("CollectiveFramework");

    /** The public GitHub API root. */
    public static final String DEFAULT_API_BASE = "https://api.github.com";

    private final String user;
    private final String repo;
    private final String branch;
    private final String apiBase;

    public GithubService(String user, String repo, String branch) {
        this(user, repo, branch, DEFAULT_API_BASE);
    }

    /**
     * @param user    The repository owner
     * @param repo    The repository name
     * @param branch  The branch to read from
     * @param apiBase The API root; injectable so the class can be exercised against a local server
     */
    GithubService(String user, String repo, String branch, String apiBase) {
        this.user = user;
        this.repo = repo;
        this.branch = branch;
        this.apiBase = apiBase;
    }

    /**
     * Instantiates with the branch as "master"
     */
    public GithubService(String user, String repo) {
        this(user, repo, "master");
    }

    public Release[] getReleases() {
        try {
            Release[] releases = new Gson().fromJson(fetch(apiUrl("/releases")), Release[].class);
            return releases == null ? new Release[0] : releases;
        } catch (Exception e) {
            LOG.warn("Unable to read releases for " + user + "/" + repo, e);
            return new Release[0];
        }
    }

    /**
     * Generates a changelog from the given sha
     *
     * @param sha The sha to start the changelog from
     * @return The changelog
     */
    public String generateChangelog(String sha) {
        Commit[] commits = getCommitsToRepo(sha);
        StringBuilder log = new StringBuilder();
        for (Commit commit : commits) {
            if (commit == null || commit.commit == null || commit.commit.message == null) continue;
            String list;
            String message = commit.commit.message.toLowerCase(java.util.Locale.ROOT);
            if (LogicUtils.xor(message.contains("add"), message.contains("remov"))) {
                if (message.contains("add")) list = "+";
                else list = "-";
            } else list = "*";
            log.append(list)
                .append('\t')
                .append(commit.commit.message.replaceFirst("\n", ""))
                .append('(')
                .append(commit.commit.author == null ? "unknown" : commit.commit.author.name)
                .append(" SHA:")
                .append(commit.sha)
                .append(")\n");
        }
        return log.toString();
    }

    /**
     * Generates a changelog
     *
     * @return The changelog
     */
    public String generateChangelog() {
        return generateChangelog("master");
    }

    /**
     * Retrieves all the commits to the repo
     *
     * @param start The sha representing where to start listing commits from
     * @return The commits
     */
    public Commit[] getCommitsToRepo(String start) {
        // `start` was ignored entirely: the request always asked for the branch, sent as a "sha" *header*
        // rather than the query parameter the API documents (CODE_REVIEW §3.4).
        String ref = start == null || start.isEmpty() ? branch : start;
        try {
            Commit[] commits = new Gson().fromJson(fetch(apiUrl("/commits?sha=" + encode(ref))), Commit[].class);
            return commits == null ? new Commit[0] : commits;
        } catch (Exception e) {
            LOG.warn("Unable to read commits for " + user + "/" + repo, e);
            return new Commit[0];
        }
    }

    /**
     * Retrieves all the commits to the repo
     *
     * @return The commits
     */
    public Commit[] getCommitsToRepo() {
        return getCommitsToRepo("master");
    }

    /**
     * Gets the commit with the corresponding sha
     *
     * @param sha The sha
     * @return The commit
     */
    public Commit getCommitToRepo(String sha) {
        if (sha == null || sha.isEmpty()) return null;
        try {
            return new Gson().fromJson(fetch(apiUrl("/commits/" + encode(sha))), Commit.class);
        } catch (Exception e) {
            LOG.warn("Unable to read commit " + sha + " of " + user + "/" + repo, e);
            return null;
        }
    }

    private String apiUrl(String path) {
        return apiBase + "/repos/" + user + "/" + repo + path;
    }

    private static String encode(String value) throws java.io.UnsupportedEncodingException {
        return java.net.URLEncoder.encode(value, "UTF-8");
    }

    /**
     * Performs a GET against the GitHub API.
     * <p>
     * GitHub rejects any request without a {@code User-Agent} with HTTP 403, and the previous implementation set
     * none. {@link WebUtils#get} also applies connect and read timeouts and closes the stream on failure
     * (CODE_REVIEW §3.4).
     *
     * @param url The absolute url
     * @return The response body
     * @throws IOException if the request fails
     */
    private String fetch(String url) throws IOException {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Accept", "application/vnd.github+json");
        return StringUtils.stringFromList(WebUtils.get(url, TIMEOUT_MS, headers));
    }

    /**
     * Reads the contents of the file at the given path relative to the repo
     *
     * @param path The path
     * @return The contents
     * @throws IOException
     */
    public List<String> readPath(String path) throws IOException {
        return WebUtils.readURL(constructUrl(true) + path);
    }

    /**
     * Constructs the url this service is directed at
     *
     * @param raw Whether to use the raw version of the url
     * @return The url
     */
    public String constructUrl(boolean raw) {
        return "https://" + (raw ? "raw." : "") + "github.com/" + user + "/" + repo + "/" + branch + "/";
    }

    public static class Release {

        public String url;
        public String html_url;
        public String assets_url;
        public String upload_url;
        public String tarball_url;
        public String zipball_url;
        public int id;
        public String tag_name;
        public String target_commitish;
        public String name;
        public String body;
        public boolean draft;
        public boolean prerelease;
        public String created_at;
        public String published_at;
        public AuthorExtended author;
        public Asset[] assets;

        @Override
        public String toString() {
            return name + " -" + tag_name;
        }
    }

    public static class Asset {

        public String url;
        public String browser_download_url;
        public int id;
        public String name;
        public String label;
        public String state;
        public String content_type;
        public int size;
        public int download_count;
        public String created_at;
        public String updated_at;
        public AuthorExtended uploader;
    }

    public static class Commit {

        public String url;
        public String sha;
        public String html_url;
        public String comments_url;
        public CommitInfo commit;
        public int comment_count;
        public AuthorExtended author;
        public AuthorExtended committer;
        public SHAInfo[] parents;

        @Override
        public String toString() {
            return commit.message.split("\n")[0] + "@" + sha;
        }
    }

    public static class CommitInfo {

        public String url;
        public Author author;
        public Author committer;
        public String message;
    }

    public static class Author {

        public String name;
        public String email;
        public String date;
    }

    public static class SHAInfo {

        public String url;
        public String sha;
    }

    public static class AuthorExtended {

        public String login;
        public int id;
        public String avatar_url;
        public String gravatar_id;
        public String url;
        public String html_url;
        public String followers_url;
        public String following_url;
        public String gists_url;
        public String starred_url;
        public String subscriptions_url;
        public String organizations_url;
        public String repos_url;
        public String events_url;
        public String received_events_url;
        public String type;
        public boolean site_admin;
    }
}

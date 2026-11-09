package com.austinv11.collectiveframework.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Class for interacting with teh interwebz
 * <p>
 * Every method sets both a connect and a read timeout, decodes as UTF-8, and closes the stream on every path
 * including failure. The previous version set only a connect timeout (so a server that accepted the connection
 * and then stalled hung the calling thread forever), decoded with the platform charset, connected *after*
 * opening the input stream, and leaked the stream when the read threw (CODE_REVIEW §3.4).
 */
public class WebUtils {

    /** Default connect and read timeout, in milliseconds. */
    public static final int DEFAULT_TIMEOUT_MS = 5000;

    /**
     * Method for reading the contents of a web page
     *
     * @param url The url of the web page
     * @return Contents, each list entry represents a new line
     * @throws IOException if the request fails
     */
    public static List<String> readURL(String url) throws IOException {
        return readURL(url, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Method for reading the contents of a web page
     *
     * @param url     The url of the web page
     * @param timeout The connect and read timeout, in milliseconds
     * @return Contents, each list entry represents a new line
     * @throws IOException if the request fails
     */
    public static List<String> readURL(String url, int timeout) throws IOException {
        return readURL(url, timeout, timeout);
    }

    /**
     * Method for reading the contents of a web page
     *
     * @param url            The url of the web page
     * @param connectTimeout The connect timeout, in milliseconds
     * @param readTimeout    The read timeout, in milliseconds
     * @return Contents, each list entry represents a new line
     * @throws IOException if the request fails
     */
    public static List<String> readURL(String url, int connectTimeout, int readTimeout) throws IOException {
        URLConnection connection = openConnection(url, connectTimeout, readTimeout);
        try (BufferedReader in = new BufferedReader(
            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            return readLines(in);
        }
    }

    /**
     * Method to make a POST request to the specified url
     *
     * @param url     URL to make the request on
     * @param timeout The connect and read timeout, in milliseconds
     * @param header  The request header (can be null)
     * @return Contents, each list entry represents a new line
     * @throws IOException if the request fails or returns a non-2xx status
     */
    public static List<String> post(String url, int timeout, Map header) throws IOException {
        return request(url, timeout, "POST", header);
    }

    /**
     * Method to make a GET request to the specified url
     *
     * @param url     URL to make the request on
     * @param timeout The connect and read timeout, in milliseconds
     * @param header  The request header (can be null)
     * @return Contents, each list entry represents a new line
     * @throws IOException if the request fails or returns a non-2xx status
     */
    public static List<String> get(String url, int timeout, Map header) throws IOException {
        return request(url, timeout, "GET", header);
    }

    private static List<String> request(String url, int timeout, String method, Map header) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) openConnection(url, timeout, timeout);
        connection.setRequestMethod(method);
        if (header != null) {
            for (Object key : header.keySet()) {
                connection.setRequestProperty(String.valueOf(key), String.valueOf(header.get(key)));
            }
        }
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + " from " + url);
            }
            try (BufferedReader in = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                return readLines(in);
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Opens a connection with both timeouts set.
     *
     * @param url            The url, with or without a scheme
     * @param connectTimeout The connect timeout, in milliseconds
     * @param readTimeout    The read timeout, in milliseconds
     * @return The connection
     * @throws IOException if the url is unusable
     */
    static URLConnection openConnection(String url, int connectTimeout, int readTimeout) throws IOException {
        if (url == null) throw new IllegalArgumentException("url must not be null");
        String normalised = url.contains("://") ? url : "http://" + url;
        URLConnection connection = new URL(normalised).openConnection();
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        return connection;
    }

    private static List<String> readLines(BufferedReader reader) throws IOException {
        List<String> read = new ArrayList<String>();
        String line;
        while ((line = reader.readLine()) != null) read.add(line);
        return read;
    }

    /**
     * Simple helper method to read a github source file
     *
     * @param repo Repo name
     * @param path Path in the repo
     * @return The contents
     * @throws IOException
     * @deprecated See {@link com.austinv11.collectiveframework.services.GithubService}
     */
    @Deprecated
    public static List<String> readGithub(String repo, String path) throws IOException {
        return readGithub(repo, "master", path);
    }

    /**
     * Simple helper method to read a github source file
     *
     * @param repo   Repo name
     * @param branch The branch of the repo
     * @param path   Path in the repo
     * @return The contents
     * @throws IOException
     * @deprecated See {@link com.austinv11.collectiveframework.services.GithubService}
     */
    @Deprecated
    public static List<String> readGithub(String repo, String branch, String path) throws IOException {
        return readURL("https://raw.github.com/austinv11/" + repo + "/" + branch + "/" + path);
    }
}

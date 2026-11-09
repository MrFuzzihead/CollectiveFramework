package com.austinv11.collectiveframework.dependencies.download;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.EnumSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BinaryProvider implements IDownloadProvider {

    /** Connect and read timeout, in milliseconds. */
    public static final int TIMEOUT_MS = 30_000;

    private static final Logger LOG = LogManager.getLogger("CollectiveFramework");

    @Override
    public boolean downloadFile(String link, String downloadPath) {
        if (link == null || downloadPath == null) throw new IllegalArgumentException("link and path required");
        HttpURLConnection http = null;
        try {
            http = (HttpURLConnection) new URL(link).openConnection();
            http.setConnectTimeout(TIMEOUT_MS);
            http.setReadTimeout(TIMEOUT_MS);
            // Redirects are followed by the JDK. The hand-rolled loop read getHeaderFields() before the request
            // was made, NPE'd when the status line was absent, had no limit, and only followed absolute
            // Locations (CODE_REVIEW §3.4).
            http.setInstanceFollowRedirects(true);

            int status = http.getResponseCode();
            if (status < 200 || status >= 300) {
                LOG.warn("Download of " + link + " failed with HTTP " + status);
                return false;
            }

            File target = new File(downloadPath);
            File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
                LOG.warn("Unable to create directory " + parent + " for " + downloadPath);
                return false;
            }

            try (InputStream input = http.getInputStream(); OutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
            }
            return true;
        } catch (Exception e) {
            LOG.warn("Download of " + link + " failed", e);
            return false;
        } finally {
            if (http != null) http.disconnect();
        }
    }

    @Override
    public EnumSet<FileType> getCapabilities() {
        return EnumSet.of(FileType.BINARY);
    }
}

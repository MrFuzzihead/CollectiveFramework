package com.austinv11.collectiveframework.dependencies.download;

import java.util.EnumSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.austinv11.collectiveframework.utils.StringUtils;
import com.austinv11.collectiveframework.utils.WebUtils;

/**
 * General plain text download provider
 */
public class PlainTextProvider implements IDownloadProvider {

    private static final Logger LOG = LogManager.getLogger("CollectiveFramework");

    @Override
    public boolean downloadFile(String url, String downloadPath) {
        if (url == null || downloadPath == null) throw new IllegalArgumentException("url and path required");
        try {
            String download = StringUtils.stringFromList(WebUtils.readURL(url));
            // FileUtils.safeWrite uses UTF-8, creates the parent directory and closes on every path; a bare
            // FileWriter used the platform charset and failed when the directory was missing (CODE_REVIEW §3.4).
            com.austinv11.collectiveframework.utils.FileUtils.safeWrite(new java.io.File(downloadPath), download);
            return true;
        } catch (Exception e) {
            LOG.warn("Text download of " + url + " failed", e);
            return false;
        }
    }

    @Override
    public EnumSet<FileType> getCapabilities() {
        return EnumSet.of(FileType.PLAIN_TEXT);
    }
}

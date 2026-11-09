package com.austinv11.collectiveframework.minecraft.utils;

import net.minecraftforge.common.MinecraftForge;

import com.austinv11.collectiveframework.minecraft.CollectiveFramework;
import com.austinv11.collectiveframework.utils.StringUtils;
import com.austinv11.collectiveframework.utils.WebUtils;
import com.google.gson.Gson;

/**
 * An easy to use version checker which utilizes curseforge for versioning
 */
public class CurseVersionChecker {

    public static final String BASE_API_URL = "http://widget.mcf.li/mc-mods/minecraft/@MOD@.json";

    private final String curseId;
    private final String name;
    private final String url;

    private CurseWidgetResponse check;

    private boolean didCheck = false;

    private boolean failed = false;

    /**
     * @param curseId The project slug/id used in curseforge urls
     * @param name    The name of the file of the currently running version of the mod
     */
    public CurseVersionChecker(String curseId, String name) {
        this(curseId, name, BASE_API_URL);
    }

    /**
     * @param curseId The project slug/id used in curseforge urls
     * @param name    The name of the file of the currently running version of the mod
     * @param baseUrl The API url template, with {@code @MOD@} as the placeholder
     */
    CurseVersionChecker(String curseId, String name, String baseUrl) {
        this.curseId = curseId;
        this.name = name;
        this.url = baseUrl.replace("@MOD@", curseId);
    }

    /**
     * Fetches the update metadata once.
     * <p>
     * Every accessor used to dereference {@link #check} unconditionally, so any network failure turned into a
     * {@link NullPointerException} in the caller (CODE_REVIEW §3.3). A failed or empty response now leaves
     * {@link #check} null and {@link #failed} set, and the accessors report "unknown".
     */
    private void doCheckIfNescessary() {
        if (didCheck) return;
        didCheck = true;
        try {
            String response = StringUtils.stringFromList(WebUtils.readURL(url));
            CurseWidgetResponse parsed = new Gson().fromJson(response, CurseWidgetResponse.class);
            if (parsed == null || parsed.download == null) {
                failed = true;
                CollectiveFramework.LOGGER.warn("Update metadata for " + curseId + " was empty or malformed");
                return;
            }
            check = parsed;
        } catch (Exception e) {
            failed = true;
            CollectiveFramework.LOGGER.warn("A problem has occurred attempting to retrieve update info for " + curseId);
            CollectiveFramework.LOGGER.debug(e);
        }
    }

    /**
     * @return True if the metadata could not be fetched
     */
    public boolean isUnknown() {
        doCheckIfNescessary();
        return check == null;
    }

    /**
     * This checks if an update is available
     *
     * @return True if an update is available
     */
    public boolean isUpdateAvailable() {
        doCheckIfNescessary();
        if (check == null) return false;
        return !name.equals(check.download.name) && MinecraftForge.MC_VERSION.equals(check.download.version);
    }

    /**
     * Gets the direct url to the latest file
     *
     * @return The url
     */
    public String getDownloadUrl() {
        doCheckIfNescessary();
        if (check == null) return null;
        return "http://minecraft.curseforge.com/mc-mods/" + curseId + "/files/" + check.download.id + "/download";
    }

    /**
     * Gets the file name of the latest file
     *
     * @return The file name
     */
    public String getUpdateFileName() {
        doCheckIfNescessary();
        return check == null ? null : check.download.name;
    }

    /**
     * Gets the curseforge project url
     *
     * @return The url
     */
    public String getProjectUrl() {
        doCheckIfNescessary();
        return check == null ? null : check.project_url;
    }

    private static class CurseWidgetResponse {

        public String title;
        public String game;
        public String category;
        public String url;
        public String thumbnail;
        public String[] authors;
        public Downloads downloads;
        public int favorites;
        public int likes;
        public String updated_at;
        public String created_at;
        public String project_url;
        public String release_type;
        public String license;
        public Download download;
        // No versions
    }

    private static class Downloads {

        public int monthly;
        public int total;
    }

    private static class Download {

        public int id;
        public String url;
        public String name;
        public String type;
        public String version;
        public int downloads;
        public String created_at;
    }
}

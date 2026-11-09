package com.austinv11.collectiveframework.minecraft.books.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.util.Constants;

/**
 * Per-save storage for {@link com.austinv11.collectiveframework.minecraft.books.api.Book.DataStorageType#LOCAL}
 * book data.
 * <p>
 * The store lives in the overworld's {@link net.minecraft.world.storage.MapStorage}, so it is shared by every
 * book in one save and persisted with it.
 * <p>
 * It is server-side only: a multiplayer client has no {@code MinecraftServer}, so reads return null and writes
 * are dropped rather than throwing.
 */
public class LocalBookData extends WorldSavedData {

    public static final String IDENTIFIER = "CF_LOCAL_BOOK_DATA";

    /**
     * The in-memory copy of the store. Replaced wholesale by {@link #readFromNBT(NBTTagCompound)} so that
     * loading a different save cannot merge with the previous one.
     */
    private static final Map<String, NBTTagCompound> DATA = new ConcurrentHashMap<String, NBTTagCompound>();

    public LocalBookData() {
        this(IDENTIFIER);
    }

    public LocalBookData(String identifier) {
        super(identifier);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        DATA.clear();
        NBTTagList list = tag.getTagList("data", Constants.NBT.TAG_STRING);
        for (int i = 0; i < list.tagCount(); i++) {
            String key = list.getStringTagAt(i);
            DATA.put(key, tag.getCompoundTag(key));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, NBTTagCompound> entry : DATA.entrySet()) {
            list.appendTag(new NBTTagString(entry.getKey()));
            tag.setTag(entry.getKey(), entry.getValue());
        }
        tag.setTag("data", list);
    }

    /**
     * Returns the store for the current save, creating it on first use.
     * <p>
     * The previous implementation created and registered a store unconditionally on the first access, which
     * either discarded data that had already been loaded from disk or threw
     * {@code RuntimeException("Duplicate id: ...")} from {@code MapStorage.setData} depending on ordering
     * (CODE_REVIEW §2.2).
     *
     * @return The store, or null when there is no server (a multiplayer client) or no overworld yet
     */
    public static LocalBookData getInstance() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return null;
        World overworld = server.worldServerForDimension(0);
        if (overworld == null) return null;

        WorldSavedData existing = overworld.loadItemData(LocalBookData.class, IDENTIFIER);
        if (existing != null) return (LocalBookData) existing;

        // Nothing on disk for this save: start empty rather than inheriting the previous world's entries.
        DATA.clear();
        LocalBookData created = new LocalBookData();
        overworld.setItemData(IDENTIFIER, created);
        return created;
    }

    /**
     * @param key The book key
     * @return The stored data, or null if absent (or if this is not the server side)
     */
    public static NBTTagCompound retrieveLocalData(String key) {
        if (key == null) return null;
        return getInstance() == null ? null : DATA.get(key);
    }

    /**
     * Stores data for a book and marks the save dirty.
     *
     * @param key The book key
     * @param tag The data
     */
    public static void putLocalData(String key, NBTTagCompound tag) {
        if (key == null || tag == null) return;
        LocalBookData local = getInstance();
        if (local == null) return; // no server: nowhere to persist
        DATA.put(key, tag);
        // Without this the data was never written to disk (CODE_REVIEW §2.2).
        local.markDirty();
    }

    /**
     * @return The number of stored book entries
     */
    public static int size() {
        return DATA.size();
    }

    /**
     * Discards the in-memory store. Intended for tests and for world shutdown.
     */
    public static void clear() {
        DATA.clear();
    }
}

package com.austinv11.collectiveframework.minecraft.books.api;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.austinv11.collectiveframework.minecraft.books.core.GuiBook;
import com.austinv11.collectiveframework.minecraft.books.core.ItemBook;
import com.austinv11.collectiveframework.minecraft.books.core.LocalBookData;
import com.austinv11.collectiveframework.minecraft.utils.NBTHelper;
import com.austinv11.collectiveframework.minecraft.utils.client.GuiUtils;
import com.austinv11.collectiveframework.multithreading.SimpleRunnable;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * This class is holds all required information to render and handle books
 */
public abstract class Book {

    private static final Logger LOGGER = LogManager.getLogger("CollectiveFramework");

    /**
     * If this is true, a debug grid will be drawn atop the book
     */
    public boolean drawDebugLines = false;
    /** Directory holding {@link DataStorageType#GLOBAL} book data. */
    public static final String GLOBAL_DIR = "./CFBookData/";

    /**
     * Cached GLOBAL data, keyed by storage file path.
     * <p>
     * This used to be a single static {@code NBTTagCompound}, so every GLOBAL book shared one buffer: opening
     * two different books made them overwrite each other, and the file for the second book was never read at all
     * (CODE_REVIEW §2.1). Keying by path fixes that while still avoiding a file read on every page turn.
     */
    private static final Map<String, NBTTagCompound> GLOBAL_CACHE = new ConcurrentHashMap<String, NBTTagCompound>();

    private int currentPage = 0;
    protected ItemBook bookItem;
    protected ItemStack bookStack;
    public EntityPlayer player;

    /**
     * The gui rendering the book
     */
    @SideOnly(Side.CLIENT)
    private GuiBook bookGui;

    /**
     * Default Book constructor, this MUST be public
     *
     * @param bookItem  The book item
     * @param bookStack The book stack
     */
    public Book(ItemBook bookItem, ItemStack bookStack) {
        this.bookItem = bookItem;
        this.bookStack = bookStack;
    }

    /**
     * Used to determine how data will be stored in the book
     *
     * @return The storage type
     */
    public abstract DataStorageType getDataStorageType();

    /**
     * Gets the book's data
     *
     * @param stack The book stack (only necessary when {@link #getDataStorageType()} returns
     *              {@link DataStorageType#LOCAL}
     * @return The data
     */
    public NBTTagCompound getData(ItemStack stack) {
        if (getDataStorageType() == DataStorageType.GLOBAL) {
            File storage = getGlobalStorageFile();
            NBTTagCompound cached = GLOBAL_CACHE.get(storage.getPath());
            if (cached == null) {
                cached = readGlobalData(storage);
                GLOBAL_CACHE.put(storage.getPath(), cached);
            }
            return (NBTTagCompound) cached.copy();
        } else if (getDataStorageType() == DataStorageType.LOCAL) {
            String key = bookItem.getModId() + ":" + bookItem.getUnlocalizedName();
            NBTBase retrieved = LocalBookData.retrieveLocalData(key);
            if (retrieved == null) return new NBTTagCompound();
            return (NBTTagCompound) retrieved.copy();
        } else {
            return NBTHelper.getCompoundTag(stack, "CFBookData");
        }
    }

    /**
     * Sets the book's data
     *
     * @param data  The new data
     * @param stack The book stack (only necessary when {@link #getDataStorageType()} returns
     *              {@link DataStorageType#LOCAL}
     */
    public void setData(final NBTTagCompound data, final ItemStack stack) {
        if (getDataStorageType() == DataStorageType.GLOBAL) {
            final File storage = getGlobalStorageFile();
            final NBTTagCompound merged;
            synchronized (GLOBAL_CACHE) {
                NBTTagCompound cached = GLOBAL_CACHE.get(storage.getPath());
                if (cached == null) cached = readGlobalData(storage);
                merged = (NBTTagCompound) cached.copy();
                merged.setTag(getOwnerKey(), data);
                GLOBAL_CACHE.put(storage.getPath(), merged);
            }
            new SimpleRunnable() { // Done to prevent speed bottlenecks from constant file modification

                @Override
                public void run() {
                    try {
                        writeGlobalData(storage, merged);
                    } catch (IOException e) {
                        LOGGER.warn("Unable to save GLOBAL book data to " + storage, e);
                    }
                }

                @Override
                public String getName() {
                    return "Book Data Serializer";
                }
            }.startOnce();
        } else if (getDataStorageType() == DataStorageType.LOCAL) {
            NBTTagCompound tag = getData(stack);
            tag.setTag(getOwnerKey(), data);
            LocalBookData.putLocalData(getBookStorageKey(), tag);
        } else {
            NBTTagCompound tag = getData(stack);
            tag.setTag(getOwnerKey(), data);
            NBTHelper.setTag(stack, "CFBookData", tag);
        }
    }

    /**
     * The file a {@link DataStorageType#GLOBAL} book stores its data in.
     *
     * @return The storage file
     */
    protected File getGlobalStorageFile() {
        return new File(GLOBAL_DIR + bookItem.getModId() + "/" + bookItem.getUnlocalizedName() + ".dat");
    }

    /**
     * Key identifying this book type in {@link LocalBookData}.
     *
     * @return The storage key
     */
    protected String getBookStorageKey() {
        return bookItem.getModId() + ":" + bookItem.getUnlocalizedName();
    }

    /**
     * Key identifying the player whose data is being read or written. Falls back to a shared key when there is
     * no player yet, instead of throwing an NPE from {@code player.getGameProfile()} (CODE_REVIEW §2.1).
     *
     * @return The owner key
     */
    protected String getOwnerKey() {
        if (player == null) return "unowned";
        if (player.getGameProfile() == null || player.getGameProfile()
            .getId() == null) return "unowned";
        return player.getGameProfile()
            .getId()
            .toString();
    }

    /**
     * Reads a GLOBAL data file.
     *
     * @param storage The file
     * @return The contents, or an empty compound if the file is missing or unreadable
     */
    protected NBTTagCompound readGlobalData(File storage) {
        if (!storage.isFile()) return new NBTTagCompound();
        try {
            NBTTagCompound read = CompressedStreamTools.read(storage);
            return read == null ? new NBTTagCompound() : read;
        } catch (IOException e) {
            LOGGER.warn("Unable to read GLOBAL book data from " + storage, e);
            return new NBTTagCompound();
        }
    }

    /**
     * Writes a GLOBAL data file, creating the directory if needed. The directory was never created before, so
     * every write failed with a swallowed {@code FileNotFoundException} (CODE_REVIEW §2.1).
     *
     * @param storage The file
     * @param data    The data
     * @throws IOException if the write fails
     */
    protected void writeGlobalData(File storage, NBTTagCompound data) throws IOException {
        File parent = storage.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Unable to create directory " + parent);
        }
        CompressedStreamTools.write(data, storage);
    }

    /**
     * Discards the GLOBAL data cache. Intended for tests.
     */
    public static void clearGlobalDataCache() {
        GLOBAL_CACHE.clear();
    }

    /**
     * Retrieves the current page of the book
     *
     * @return The current page
     */
    public int getCurrentPage() {
        return currentPage;
    }

    /**
     * Attempts to set the page of the book
     *
     * @param newPage The new page
     */
    public void setPage(int newPage) {
        Page[] pages = getPages();
        if (pages.length == 0) {
            // Nothing to page through; do not index an empty array.
            return;
        }
        if (newPage >= pages.length) newPage = 0;
        // This used to clamp to `pages.length`, which is one past the end, so paging backwards from the first
        // page made GuiBook throw ArrayIndexOutOfBoundsException (CODE_REVIEW §1.20).
        else if (newPage < 0) newPage = pages.length - 1;
        if (onPageFlip(currentPage, newPage)) {
            currentPage = newPage;
            NBTTagCompound data = getData(bookStack);
            data.setInteger("page", currentPage);
            setData(data, bookStack);
        }
    }

    /**
     * Do NOT touch this, it is required for communications between the actual gui and book
     *
     * @param gui The actual gui
     */
    @SideOnly(Side.CLIENT)
    public final void handoffGui(GuiBook gui) {
        this.bookGui = gui;
    }

    /**
     * Called when the book is opened
     *
     * @param player The player who opened the book
     */
    public void onOpen(EntityPlayer player) {
        this.player = player;
        NBTTagCompound data = getData(bookStack);
        if (data.hasKey("page")) setPage(data.getInteger("page"));
    }

    /**
     * Called when the book is closed
     */
    public void onClose() {}

    /**
     * Closes the book's gui
     */
    @SideOnly(Side.CLIENT)
    public void closeBook() {
        GuiUtils.closeCurrentGui(GuiBook.class);
    }

    /**
     * Same as {@link GuiScreen#doesGuiPauseGame()}
     */
    public abstract boolean doesPauseGame();

    /**
     * Used to retrieve the pages in the book
     *
     * @return All the pages in the book, THE ORDER MATTERS! Page at index 0 is the page displayed on the first page of
     *         the book
     */
    public abstract Page[] getPages();

    /**
     * Used to get the background image, basically the generic background
     *
     * @return The resource location of the background, or null if there aren't any
     */
    public abstract ResourceLocation getBackground();

    /**
     * Called when the page is rendered
     *
     * @param dt The time since the last render of the current pass in milliseconds
     */
    @SideOnly(Side.CLIENT)
    public abstract void onRender(int dt);

    /**
     * Called when the page is attempted to be changed
     *
     * @param oldPage The current page of the book
     * @param newPage The new page of the book
     * @return True to allow the page flip, false if otherwise
     */
    public abstract boolean onPageFlip(int oldPage, int newPage);

    /**
     * Represents where the persistence data is stored for this book:
     * GLOBAL = All worlds share the same data
     * LOCAL = All books in one world share the same data
     * INSTANCE = The data stored is per book
     */
    public static enum DataStorageType {
        GLOBAL,
        LOCAL,
        INSTANCE
    }
}

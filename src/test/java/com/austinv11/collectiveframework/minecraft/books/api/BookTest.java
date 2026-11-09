package com.austinv11.collectiveframework.minecraft.books.api;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.austinv11.collectiveframework.minecraft.books.core.ItemBook;
import com.mojang.authlib.GameProfile;

/**
 * CODE_REVIEW §1.20 and §2.1.
 * <ul>
 * <li>§1.20 - {@code setPage} clamped a negative page to {@code pages.length}, one past the end, so paging
 * backwards from the first page made {@code GuiBook} throw {@code ArrayIndexOutOfBoundsException}.</li>
 * <li>§2.1 - all GLOBAL books shared one static {@code NBTTagCompound} buffer, so two books overwrote each
 * other and the second book's file was never read; the storage directory was never created, so every write
 * failed; and {@code player.getGameProfile()} NPE'd when there was no player.</li>
 * </ul>
 */
public class BookTest {

    // ------------------------------------------------------------------------------- fixtures

    private static class TestPage extends Page {

        @Override
        public ResourceLocation getBackground() {
            return null;
        }

        @Override
        public Entry[] getEntries() {
            return new Entry[0];
        }

        @Override
        public void onRender(int dt) {}
    }

    /**
     * A book with in-memory storage, so the paging logic can be tested without an {@link ItemStack}.
     */
    private static class MemoryBook extends Book {

        private final Page[] pages;
        private NBTTagCompound store = new NBTTagCompound();
        boolean allowFlip = true;
        int flips;

        MemoryBook(int pageCount) {
            super(null, null);
            this.pages = new Page[pageCount];
            for (int i = 0; i < pageCount; i++) pages[i] = new TestPage();
        }

        @Override
        public DataStorageType getDataStorageType() {
            return DataStorageType.INSTANCE;
        }

        @Override
        public NBTTagCompound getData(ItemStack stack) {
            return (NBTTagCompound) store.copy();
        }

        @Override
        public void setData(NBTTagCompound data, ItemStack stack) {
            store = (NBTTagCompound) data.copy();
        }

        NBTTagCompound lastSaved() {
            return store;
        }

        @Override
        public Page[] getPages() {
            return pages;
        }

        @Override
        public ResourceLocation getBackground() {
            return null;
        }

        @Override
        public void onRender(int dt) {}

        @Override
        public boolean onPageFlip(int oldPage, int newPage) {
            flips++;
            return allowFlip;
        }

        @Override
        public boolean doesPauseGame() {
            return false;
        }
    }

    /**
     * A book that really uses GLOBAL storage, with the file redirected into a temporary directory.
     */
    private static class GlobalBook extends Book {

        private final File storage;
        private final Page[] pages = new Page[] { new TestPage(), new TestPage() };

        GlobalBook(ItemBook item, File storage) {
            super(item, null);
            this.storage = storage;
        }

        @Override
        protected File getGlobalStorageFile() {
            return storage;
        }

        @Override
        public DataStorageType getDataStorageType() {
            return DataStorageType.GLOBAL;
        }

        @Override
        public Page[] getPages() {
            return pages;
        }

        @Override
        public ResourceLocation getBackground() {
            return null;
        }

        @Override
        public void onRender(int dt) {}

        @Override
        public boolean onPageFlip(int oldPage, int newPage) {
            return true;
        }

        @Override
        public boolean doesPauseGame() {
            return false;
        }
    }

    private File tempDir;

    @Before
    public void resetCache() throws Exception {
        Book.clearGlobalDataCache();
        tempDir = Files.createTempDirectory("cf-book-test")
            .toFile();
    }

    @After
    public void cleanUp() {
        Book.clearGlobalDataCache();
        if (tempDir != null) deleteRecursively(tempDir);
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        // noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static ItemBook mockBookItem(String modId, String name) {
        ItemBook item = Mockito.mock(ItemBook.class);
        Mockito.when(item.getModId())
            .thenReturn(modId);
        Mockito.when(item.getUnlocalizedName())
            .thenReturn(name);
        return item;
    }

    private static EntityPlayer mockPlayer(UUID id) {
        EntityPlayer player = Mockito.mock(EntityPlayer.class);
        Mockito.when(player.getGameProfile())
            .thenReturn(new GameProfile(id, "tester"));
        return player;
    }

    // ------------------------------------------------------------------------------- §1.20 paging

    @Test
    public void setPageWrapsForwardPastTheEnd() {
        MemoryBook book = new MemoryBook(3);
        book.setPage(3);
        Assert.assertEquals(0, book.getCurrentPage());
    }

    /** §1.20 - this used to set the page to {@code pages.length} and crash the renderer. */
    @Test
    public void setPageClampsBackwardsToTheLastPage() {
        MemoryBook book = new MemoryBook(3);
        book.setPage(-1);
        Assert.assertEquals("paging back from page 0 must land on the last page", 2, book.getCurrentPage());
    }

    @Test
    public void setPageAcceptsAValidIndex() {
        MemoryBook book = new MemoryBook(3);
        book.setPage(1);
        Assert.assertEquals(1, book.getCurrentPage());
    }

    @Test
    public void setPageIsANoOpWhenTheBookHasNoPages() {
        MemoryBook book = new MemoryBook(0);
        book.setPage(5);
        book.setPage(-5);
        Assert.assertEquals(0, book.getCurrentPage());
    }

    @Test
    public void setPageHonoursOnPageFlip() {
        MemoryBook book = new MemoryBook(3);
        book.allowFlip = false;
        book.setPage(1);
        Assert.assertEquals("onPageFlip vetoed the change", 0, book.getCurrentPage());
        Assert.assertEquals(1, book.flips);
    }

    @Test
    public void setPagePersistsTheNewPage() {
        MemoryBook book = new MemoryBook(3);
        book.setPage(2);

        Assert.assertEquals(2, book.getCurrentPage());
        Assert.assertNotNull("setData was not called", book.lastSaved());
        Assert.assertEquals(
            2,
            book.lastSaved()
                .getInteger("page"));
    }

    @Test
    public void onOpenRestoresTheStoredPage() {
        MemoryBook book = new MemoryBook(3);
        book.setPage(2);

        MemoryBook reopened = new MemoryBook(3);
        // Carry the stored data across, as a real INSTANCE book would through its ItemStack.
        reopened.setData(book.getData(null), null);
        reopened.onOpen(null);

        Assert.assertEquals(2, reopened.getCurrentPage());
    }

    /** A page index stored by an older, buggier version must not crash the renderer. */
    @Test
    public void onOpenClampsAnOutOfRangeStoredPage() {
        MemoryBook book = new MemoryBook(3);
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("page", 99);
        book.setData(data, null);

        book.onOpen(null);

        Assert.assertEquals("an out-of-range stored page must wrap", 0, book.getCurrentPage());
    }

    // ------------------------------------------------------------------------ §2.1 GLOBAL storage

    /** §2.1 - two GLOBAL books must not share one buffer. */
    @Test
    public void globalBooksDoNotShareABuffer() {
        File first = new File(tempDir, "first.dat");
        File second = new File(tempDir, "second.dat");
        GlobalBook firstBook = new GlobalBook(mockBookItem("mod", "first"), first);
        GlobalBook secondBook = new GlobalBook(mockBookItem("mod", "second"), second);

        firstBook.setData(named("from-first"), null);
        secondBook.setData(named("from-second"), null);

        Assert.assertEquals(
            "from-first",
            firstBook.getData(null)
                .getCompoundTag(firstBook.getOwnerKey())
                .getString("name"));
        Assert.assertEquals(
            "from-second",
            secondBook.getData(null)
                .getCompoundTag(secondBook.getOwnerKey())
                .getString("name"));
    }

    @Test
    public void globalDataIsReadBackFromDisk() throws Exception {
        File storage = new File(tempDir, "from-disk.dat");
        NBTTagCompound stored = new NBTTagCompound();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("name", "on-disk");
        stored.setTag("owner", entry);
        CompressedStreamTools.write(stored, storage);

        GlobalBook book = new GlobalBook(mockBookItem("mod", "disk"), storage);

        Assert.assertEquals(
            "on-disk",
            book.getData(null)
                .getCompoundTag("owner")
                .getString("name"));
    }

    @Test
    public void globalDataReturnsACopy() {
        GlobalBook book = new GlobalBook(mockBookItem("mod", "copy"), new File(tempDir, "copy.dat"));
        book.setData(named("original"), null);

        NBTTagCompound first = book.getData(null);
        first.getCompoundTag(book.getOwnerKey())
            .setString("name", "mutated");

        Assert.assertEquals(
            "mutating the returned compound must not affect the cache",
            "original",
            book.getData(null)
                .getCompoundTag(book.getOwnerKey())
                .getString("name"));
    }

    @Test
    public void globalDataIsCachedBetweenReads() {
        File storage = new File(tempDir, "cached.dat");
        GlobalBook book = new GlobalBook(mockBookItem("mod", "cached"), storage);

        book.getData(null);
        Assert.assertFalse("getData must not create the file", storage.exists());

        book.setData(named("cached"), null);
        // The cache is updated synchronously, before the background write.
        Assert.assertEquals(
            "cached",
            book.getData(null)
                .getCompoundTag(book.getOwnerKey())
                .getString("name"));
    }

    /** §2.1 - the directory was never created, so every write failed. */
    @Test
    public void writeGlobalDataCreatesTheDirectory() throws Exception {
        File nested = new File(new File(tempDir, "a/b/c"), "book.dat");
        GlobalBook book = new GlobalBook(mockBookItem("mod", "nested"), nested);

        book.writeGlobalData(nested, named("nested"));

        Assert.assertTrue(nested.isFile());
        Assert.assertEquals(
            "nested",
            CompressedStreamTools.read(nested)
                .getString("name"));
    }

    @Test
    public void readingAMissingFileYieldsAnEmptyCompound() {
        GlobalBook book = new GlobalBook(mockBookItem("mod", "missing"), new File(tempDir, "missing.dat"));
        Assert.assertTrue(
            book.getData(null)
                .hasNoTags());
    }

    @Test
    public void readingACorruptFileYieldsAnEmptyCompound() throws Exception {
        File storage = new File(tempDir, "corrupt.dat");
        Files.write(storage.toPath(), "not an nbt file".getBytes("UTF-8"));
        GlobalBook book = new GlobalBook(mockBookItem("mod", "corrupt"), storage);

        Assert.assertTrue(
            book.getData(null)
                .hasNoTags());
    }

    // ---------------------------------------------------------------------------- owner key

    @Test
    public void ownerKeyUsesThePlayerUuid() {
        GlobalBook book = new GlobalBook(mockBookItem("mod", "owner"), new File(tempDir, "owner.dat"));
        UUID id = UUID.randomUUID();
        book.player = mockPlayer(id);

        Assert.assertEquals(id.toString(), book.getOwnerKey());
    }

    /** §2.1 - this used to NPE. */
    @Test
    public void ownerKeyFallsBackWithoutAPlayer() {
        GlobalBook book = new GlobalBook(mockBookItem("mod", "noplayer"), new File(tempDir, "noplayer.dat"));
        Assert.assertEquals("unowned", book.getOwnerKey());
    }

    @Test
    public void ownerKeyFallsBackWithoutAGameProfile() {
        GlobalBook book = new GlobalBook(mockBookItem("mod", "noprofile"), new File(tempDir, "noprofile.dat"));
        book.player = Mockito.mock(EntityPlayer.class); // getGameProfile() returns null
        Assert.assertEquals("unowned", book.getOwnerKey());
    }

    @Test
    public void settingDataWithoutAPlayerDoesNotThrow() {
        GlobalBook book = new GlobalBook(mockBookItem("mod", "anonymous"), new File(tempDir, "anonymous.dat"));
        book.setData(named("anonymous"), null);

        Assert.assertEquals(
            "anonymous",
            book.getData(null)
                .getCompoundTag("unowned")
                .getString("name"));
    }

    // -------------------------------------------------------------------------------- storage key

    @Test
    public void storageKeyCombinesModIdAndName() {
        GlobalBook book = new GlobalBook(mockBookItem("mymod", "mybook"), new File(tempDir, "key.dat"));
        Assert.assertEquals("mymod:mybook", book.getBookStorageKey());
    }

    private static NBTTagCompound named(String name) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("name", name);
        return tag;
    }
}

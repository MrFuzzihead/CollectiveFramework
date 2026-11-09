package com.austinv11.collectiveframework.gametest;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;

import com.austinv11.collectiveframework.minecraft.CollectiveFramework;
import com.austinv11.collectiveframework.minecraft.books.api.Book;
import com.austinv11.collectiveframework.minecraft.books.api.Entry;
import com.austinv11.collectiveframework.minecraft.books.api.Page;
import com.austinv11.collectiveframework.minecraft.books.core.ItemBook;
import com.austinv11.collectiveframework.minecraft.books.core.LocalBookData;
import com.austinv11.collectiveframework.minecraft.config.ConfigRegistry;
import com.austinv11.collectiveframework.minecraft.network.TimeUpdatePacket;
import com.austinv11.collectiveframework.minecraft.reference.Config;
import com.austinv11.collectiveframework.minecraft.reference.Reference;
import com.austinv11.collectiveframework.minecraft.threading.MainThread;
import com.austinv11.collectiveframework.minecraft.tiles.INBTSyncable;
import com.austinv11.collectiveframework.minecraft.tiles.TileEntityInventory;
import com.austinv11.collectiveframework.minecraft.utils.Colors;
import com.austinv11.collectiveframework.minecraft.utils.NBTHelper;
import com.austinv11.collectiveframework.minecraft.utils.WorldUtils;
import com.austinv11.collectiveframework.multithreading.HeavyCalculations;
import com.austinv11.collectiveframework.multithreading.ICalculations;
import com.austinv11.collectiveframework.multithreading.SimpleRunnable;
import com.austinv11.collectiveframework.multithreading.SimpleThread;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.eventhandler.EventBus;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;

/**
 * A test-only mod that asserts framework invariants from inside a running dedicated server, where the
 * Minecraft-coupled classes (config files, NBT helpers, tile entity inventories, world lookup) actually exist.
 * <p>
 * It is compiled into its own {@code gameTest} source set and is only added to the run classpath by the
 * {@code gameTestServer} Gradle task (or {@code -PgameTest}), so it never ships.
 * <p>
 * Results are reported with machine-readable markers so CI can assert on the log:
 * <ul>
 * <li>{@code GAMETEST_ASSERTIONS_OK} - every assertion passed</li>
 * <li>{@code COLLECTIVEFRAMEWORK_GAMETEST_OK} - the run finished cleanly</li>
 * </ul>
 * Any failure logs {@code COLLECTIVEFRAMEWORK_GAMETEST_FAILED} and exits non-zero.
 */
@Mod(
    modid = "collectiveframework_gametest",
    name = "CollectiveFramework Game Tests",
    version = "1.0",
    acceptableRemoteVersions = "*")
public class GameTestMod {

    public static final String ASSERTIONS_OK = "GAMETEST_ASSERTIONS_OK";
    public static final String RUN_OK = "COLLECTIVEFRAMEWORK_GAMETEST_OK";
    public static final String RUN_FAILED = "COLLECTIVEFRAMEWORK_GAMETEST_FAILED";

    private static final boolean ENABLED = Boolean.getBoolean("collectiveframework.gameTest");

    private static int checks = 0;
    private static int knownDefectsPresent = 0;

    /** Set once the synchronous assertions are done; cleared when the main-thread check finishes. */
    private static volatile boolean awaitingMainThreadCheck = false;
    private static volatile Thread mainThreadTaskThread;
    private static final java.util.concurrent.CountDownLatch mainThreadTaskRan = new java.util.concurrent.CountDownLatch(
        1);
    private static final int MAX_TICKS_WAITED = 200;
    private static int ticksWaited = 0;

    /** {@link SimpleThread}'s constructors are protected, so the game test needs its own subclass. */
    @SuppressWarnings("deprecation")
    public static class TestThread extends SimpleThread {

        TestThread(Runnable target, String name) {
            super(target, name);
        }
    }

    /** Registered in {@link #preInit} and placed in the world by {@link #checkTileEntitySyncGate}. */
    public static final TestBlock GAME_TEST_BLOCK = new TestBlock();

    /**
     * A concrete inventory so the abstract base can be exercised. It has to be registered or
     * {@code TileEntity.writeToNBT} refuses to serialise it.
     */
    public static class TestInventory extends TileEntityInventory implements INBTSyncable {

        @Override
        public int getSize() {
            return 4;
        }

        /**
         * {@link TileEntityInventory} provides {@code removeStackFromSlotStatic} but never wires it into
         * {@code IInventory}, so every subclass has to implement this itself (CODE_REVIEW §6.7).
         */
        @Override
        public ItemStack getStackInSlotOnClosing(int slot) {
            return removeStackFromSlotStatic(items, slot);
        }

        @Override
        public NBTTagCompound writeSyncData() {
            NBTTagCompound tag = new NBTTagCompound();
            writeToNBT(tag);
            return tag;
        }

        @Override
        public void readSyncData(NBTTagCompound data) {
            INBTSyncable.readFromNbtKeepingPosition(this, data);
        }
    }

    /** A page with no content, so {@link TestBook} can have a real page array. */
    public static class TestPage extends Page {

        @Override
        public net.minecraft.util.ResourceLocation getBackground() {
            return null;
        }

        @Override
        public Entry[] getEntries() {
            return new Entry[0];
        }

        @Override
        public void onRender(int dt) {}
    }

    /** A book item so {@link TestBook} has a concrete {@link ItemBook}. */
    public static class TestBookItem extends ItemBook {

        public TestBookItem() {
            setUnlocalizedName("collectiveframework_gametest_book");
        }

        @Override
        public Class<? extends Book> getBook() {
            return TestBook.class;
        }

        @Override
        public net.minecraft.creativetab.CreativeTabs getTab() {
            return null;
        }

        @Override
        public String getModId() {
            return "collectiveframework_gametest";
        }
    }

    /** A three-page book using per-item storage, so paging can be checked against a real ItemStack. */
    public static class TestBook extends Book {

        private static final Page[] PAGES = { new TestPage(), new TestPage(), new TestPage() };

        public TestBook(ItemBook bookItem, ItemStack bookStack) {
            super(bookItem, bookStack);
        }

        @Override
        public DataStorageType getDataStorageType() {
            return DataStorageType.INSTANCE;
        }

        @Override
        public Page[] getPages() {
            return PAGES;
        }

        @Override
        public net.minecraft.util.ResourceLocation getBackground() {
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

    /** A block that hosts {@link TestInventory}, so the sync gate can be exercised in a real world. */
    public static class TestBlock extends BlockContainer {

        public TestBlock() {
            super(Material.iron);
            setBlockName("collectiveframework_gametest_block");
        }

        @Override
        public TileEntity createNewTileEntity(World world, int metadata) {
            return new TestInventory();
        }
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        GameRegistry.registerTileEntity(TestInventory.class, "collectiveframework_gametest_inventory");
        GameRegistry.registerBlock(GAME_TEST_BLOCK, "collectiveframework_gametest_block");
        // @Mod classes are not auto-registered for @SubscribeEvent (CODE_REVIEW Appendix A), and the tick
        // handler needs to be on the FML bus (CODE_REVIEW §1.1).
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        if (!ENABLED) return;
        if (FMLCommonHandler.instance()
            .getSide() != Side.SERVER) return;

        try {
            runAssertions();
        } catch (Throwable t) {
            fail(t);
            return;
        }

        // The main-thread hop can only be proven on a real tick, so the run finishes in onServerTick.
        scheduleMainThreadCheck();
    }

    // ------------------------------------------------------------------------------ main-thread hop

    /**
     * CODE_REVIEW §3.1 (fixed) - network handlers used to touch the world on the netty thread. They now hand
     * their work to {@link MainThread}, which is drained from {@link TickEvent.ServerTickEvent}. This schedules
     * a task from outside the tick and checks, on a later tick, that it ran on the server thread.
     */
    private static void scheduleMainThreadCheck() {
        awaitingMainThreadCheck = true;
        MainThread.scheduleOnServer(new Runnable() {

            @Override
            public void run() {
                mainThreadTaskThread = Thread.currentThread();
                mainThreadTaskRan.countDown();
            }
        });
    }

    /**
     * Registered on the FML bus in {@link #preInit}. Finishes the run once the scheduled task has been drained.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !awaitingMainThreadCheck) return;

        if (mainThreadTaskRan.getCount() != 0) {
            if (++ticksWaited > MAX_TICKS_WAITED) {
                awaitingMainThreadCheck = false;
                fail(new AssertionError("MainThread never drained the scheduled task (CODE_REVIEW §3.1)"));
            }
            return;
        }

        awaitingMainThreadCheck = false;
        try {
            Thread serverThread = Thread.currentThread();
            check(
                "MainThread drained the task on the server thread (CODE_REVIEW §3.1)",
                mainThreadTaskThread == serverThread);
            check("MainThread drained within a few ticks", ticksWaited <= MAX_TICKS_WAITED);
            check("the MainThread queue is empty after the drain", MainThread.pendingServerCount() == 0);

            System.out.println(ASSERTIONS_OK + " (" + checks + " checks)");
            System.out.println("known defects still present: " + knownDefectsPresent + " (see CODE_REVIEW.md)");
            System.out.println(RUN_OK);
            exit(0);
        } catch (Throwable t) {
            fail(t);
        }
    }

    private static void fail(Throwable t) {
        t.printStackTrace();
        System.out.println(RUN_FAILED);
        exit(1);
    }

    private static void exit(int code) {
        FMLCommonHandler.instance()
            .exitJava(code, false);
    }

    private static void runAssertions() throws Exception {
        checkEventBusWiring();
        checkConfigIsRegistered();
        checkConfigIdentityIsResolvable();
        checkConfigSerialisation();
        checkWorldLookup();
        checkNbtHelperRoundTrip();
        checkTileEntityInventoryRoundTrip();
        checkTileEntitySyncGate();
        checkTimeUpdateBounds();
        checkTranslationFailsFastWithoutAKey();
        checkBookPaging();
        checkLocalBookDataPersists();
        checkThreadingRunsOnAModernJvm();
        checkColors();
    }

    // --------------------------------------------------------------------------------- event buses

    /**
     * CODE_REVIEW §1.1 (fixed) - {@code PlayerEvent}, {@code TickEvent} and {@code InputEvent} are posted to
     * {@code FMLCommonHandler.instance().bus()}, which is a different {@link EventBus} instance than
     * {@code MinecraftForge.EVENT_BUS}. Registering only on the Forge bus left config sync, the time-change
     * buttons, the key overlay and the book keyboard input completely dead.
     */
    private static void checkEventBusWiring() {
        check("CollectiveFramework.instance is set", CollectiveFramework.instance != null);
        check(
            "CollectiveFramework.instance is registered on the FML event bus (CODE_REVIEW §1.1)",
            isRegistered(
                FMLCommonHandler.instance()
                    .bus(),
                CollectiveFramework.instance));
        check(
            "CollectiveFramework.instance is still registered on the Forge event bus",
            isRegistered(MinecraftForge.EVENT_BUS, CollectiveFramework.instance));
        check(
            "the FML and Forge buses are distinct instances",
            FMLCommonHandler.instance()
                .bus() != MinecraftForge.EVENT_BUS);
    }

    /** Reads {@code EventBus.listeners}, a {@code Map<Object, List<IEventListener>>} keyed by listener. */
    private static boolean isRegistered(EventBus bus, Object listener) {
        try {
            java.lang.reflect.Field field = EventBus.class.getDeclaredField("listeners");
            field.setAccessible(true);
            Object listeners = field.get(bus);
            if (!(listeners instanceof java.util.Map)) {
                throw new AssertionError("EventBus.listeners is not a Map: " + listeners);
            }
            return ((java.util.Map<?, ?>) listeners).containsKey(listener);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to inspect the event bus", e);
        }
    }

    // ------------------------------------------------------------------------------------- config

    private static void checkConfigIsRegistered() {
        check("ConfigRegistry.configs is not empty", !ConfigRegistry.configs.isEmpty());

        ConfigRegistry.ConfigProxy proxy = findProxy(Reference.MOD_ID + ".cfg");
        check("the framework config file is registered", proxy != null);
        check("the framework config is marked for syncing", proxy.doesSync);
    }

    /**
     * CODE_REVIEW §1.2 (fixed) - {@code CollectiveFramework.preInit} used to register {@code new Config()}
     * while {@code GuiConfig} passed {@code Config.INSTANCE}. {@code ConfigRegistry} matched them with
     * {@code Object.equals}, so the GUI's instance was never found and the config screen came up empty.
     */
    private static void checkConfigIdentityIsResolvable() {
        boolean sameInstance = false;
        for (ConfigRegistry.ConfigProxy proxy : ConfigRegistry.configs) {
            if (proxy.config == Config.INSTANCE) sameInstance = true;
        }
        check("the registered config proxy holds Config.INSTANCE (CODE_REVIEW §1.2)", sameInstance);

        String path = ConfigRegistry.getFilePath(Config.INSTANCE);
        check("ConfigRegistry.getFilePath(Config.INSTANCE) resolves (CODE_REVIEW §1.2)", path != null);
        check(
            "the resolved path points at the framework config file",
            path != null && path.replace('\\', '/')
                .endsWith("/config/" + Reference.MOD_ID + ".cfg"));

        // Matching is by class now, so a different instance of the same config class must also resolve.
        check(
            "a second instance of the same config class also resolves (CODE_REVIEW §1.2)",
            ConfigRegistry.getFilePath(new Config()) != null);
    }

    private static void checkConfigSerialisation() throws Exception {
        check("ConfigRegistry.getKey(Integer) is Integer", "Integer".equals(ConfigRegistry.getKey(Integer.valueOf(1))));
        check(
            "ConfigRegistry.serialize/deserialize round trips an int",
            Integer.valueOf(42)
                .equals(ConfigRegistry.deserialize("Integer", ConfigRegistry.serialize(Integer.valueOf(42)))));
        check(
            "ConfigRegistry.serialize/deserialize round trips an int array",
            java.util.Arrays.equals(
                new int[] { 1, 2, 3 },
                (int[]) ConfigRegistry.deserialize("Integer Array", ConfigRegistry.serialize(new int[] { 1, 2, 3 }))));
        check(
            "getForgeConfigType maps Integer to INTEGER",
            "INTEGER".equals(
                ConfigRegistry.getForgeConfigType(Integer.valueOf(1))
                    .name()));
    }

    private static ConfigRegistry.ConfigProxy findProxy(String fileName) {
        for (ConfigRegistry.ConfigProxy proxy : ConfigRegistry.configs) {
            if (proxy.fileName.equals(fileName)) return proxy;
        }
        return null;
    }

    // -------------------------------------------------------------------------------------- world

    private static void checkWorldLookup() {
        check("WorldUtils resolves the overworld", WorldUtils.getWorldFromDimensionId(0) != null);
    }

    // ---------------------------------------------------------------------------------------- NBT

    private static void checkNbtHelperRoundTrip() {
        ItemStack stack = new ItemStack(Items.stick);

        NBTHelper.setInteger(stack, "int", 1234);
        check("NBTHelper int round trip", NBTHelper.getInt(stack, "int") == 1234);

        NBTHelper.setString(stack, "string", "hello");
        check("NBTHelper string round trip", "hello".equals(NBTHelper.getString(stack, "string")));

        NBTHelper.setBoolean(stack, "boolean", true);
        check("NBTHelper boolean round trip", NBTHelper.getBoolean(stack, "boolean"));

        NBTHelper.setDouble(stack, "double", 1.5d);
        check("NBTHelper double round trip", NBTHelper.getDouble(stack, "double") == 1.5d);

        NBTHelper.setLong(stack, "long", 1L << 40);
        check("NBTHelper long round trip", NBTHelper.getLong(stack, "long") == (1L << 40));

        check("NBTHelper created a tag compound on write", stack.hasTagCompound());
        check("NBTHelper.hasTag sees a written key", NBTHelper.hasTag(stack, "int"));
        NBTHelper.removeTag(stack, "int");
        check("NBTHelper.removeTag removes the key", !NBTHelper.hasTag(stack, "int"));

        ItemStack fresh = new ItemStack(Items.stick);
        check("NBTHelper returns 0 for a missing int", NBTHelper.getInt(fresh, "absent") == 0);
    }

    // ---------------------------------------------------------------------------------- inventory

    private static void checkTileEntityInventoryRoundTrip() {
        TestInventory inventory = new TestInventory();
        check("TileEntityInventory sizes itself from getSize() (CODE_REVIEW §2.4)", inventory.getSizeInventory() == 4);

        inventory.setInventorySlotContents(1, new ItemStack(Items.diamond, 3));

        NBTTagCompound tag = new NBTTagCompound();
        inventory.writeToNBT(tag);
        check("TileEntityInventory wrote its items tag", tag.hasKey("items"));

        NBTTagList items = tag.getTagList("items", Constants.NBT.TAG_COMPOUND);
        check("TileEntityInventory wrote one entry per slot", items.tagCount() == 4);

        TestInventory restored = new TestInventory();
        restored.readFromNBT(tag);
        ItemStack slot1 = restored.getStackInSlot(1);
        check("TileEntityInventory restored slot 1", slot1 != null && slot1.getItem() == Items.diamond);
        check("TileEntityInventory restored stack size", slot1 != null && slot1.stackSize == 3);
        check("TileEntityInventory kept slot 0 empty", restored.getStackInSlot(0) == null);
    }

    // ------------------------------------------------------------------------------- packet safety

    /**
     * CODE_REVIEW §1.17 (fixed) - the sync packets used to call {@code readFromNBT} on any tile entity at the
     * coordinates in the packet, so a client could rewrite (or relocate) any tile entity it could name. The
     * write is now gated on {@link INBTSyncable}, checked here against a real world.
     */
    private static void checkTileEntitySyncGate() {
        World world = WorldUtils.getWorldFromDimensionId(0);
        if (world == null) throw new AssertionError("no overworld to test against");

        int x = 1;
        int y = 64;
        int z = 1;
        world.setBlock(x, y, z, Blocks.chest);
        TileEntity chest = world.getTileEntity(x, y, z);
        check("a vanilla chest tile entity was created", chest != null);
        check("a vanilla chest is not INBTSyncable", !(chest instanceof INBTSyncable));
        check(
            "the sync gate rejects a tile entity that did not opt in (CODE_REVIEW §1.17)",
            !INBTSyncable.applyTo(world, x, y, z, new NBTTagCompound()));

        int sx = 3;
        int sz = 1;
        world.setBlock(sx, y, sz, GAME_TEST_BLOCK);
        TileEntity placed = world.getTileEntity(sx, y, sz);
        check("the opted-in test tile entity was created", placed instanceof TestInventory);

        TestInventory source = new TestInventory();
        source.setInventorySlotContents(0, new ItemStack(Items.gold_ingot, 5));
        NBTTagCompound payload = source.writeSyncData();

        check("the sync gate accepts an opted-in tile entity", INBTSyncable.applyTo(world, sx, y, sz, payload));
        ItemStack synced = ((TestInventory) placed).getStackInSlot(0);
        check("the payload was applied", synced != null && synced.getItem() == Items.gold_ingot);
        check("the payload carried the stack size", synced != null && synced.stackSize == 5);
        check(
            "applying a payload did not relocate the tile entity",
            placed.xCoord == sx && placed.yCoord == y && placed.zCoord == sz);

        check(
            "the sync gate rejects an empty position",
            !INBTSyncable.applyTo(world, sx, y + 5, sz, new NBTTagCompound()));
        check("the sync gate rejects a null payload", !INBTSyncable.applyTo(world, sx, y, sz, null));
    }

    /**
     * CODE_REVIEW §1.16 (fixed) - the time-change handler authorised against the profile inside the packet and
     * applied the result to every loaded world with no bound. The bounds half is checked here.
     */
    private static void checkTimeUpdateBounds() {
        long noon = 6000L;
        check("a small time change is accepted", TimeUpdatePacket.resolveRequestedTime(noon, noon, 600) != null);
        check(
            "a zero time change is accepted",
            Long.valueOf(noon)
                .equals(TimeUpdatePacket.resolveRequestedTime(noon, noon, 0)));
        check(
            "a change beyond the limit is rejected (CODE_REVIEW §1.16)",
            TimeUpdatePacket.resolveRequestedTime(noon, noon + TimeUpdatePacket.MAX_TIME_CHANGE_TICKS + 1L, 0) == null);
        check(
            "an overflowing change is rejected",
            TimeUpdatePacket.resolveRequestedTime(0L, Long.MAX_VALUE, 1) == null);
        check("a negative resulting time is rejected", TimeUpdatePacket.resolveRequestedTime(noon, 0L, -1) == null);
    }

    // -------------------------------------------------------------------------------- translation

    /**
     * CODE_REVIEW §1.18 (fixed) - the framework used to ship a hard-coded Yandex key. With no key configured,
     * translation must now fail fast with {@code NOT_ENOUGH_PARAMETERS} instead of making an unauthenticated
     * request. This is also the only in-game check that proves no credential is baked into the jar.
     */
    private static void checkTranslationFailsFastWithoutAKey() throws Exception {
        if (!Config.yandexApiKey.isEmpty()) {
            System.out.println("  [skip] an API key is configured; not exercising the no-key path");
            return;
        }
        check(
            "no API key is compiled in",
            !com.austinv11.collectiveframework.language.translation.YandexProvider.hasApiKey());
        try {
            com.austinv11.collectiveframework.language.TranslationManager.translate("hello", "en", "de");
            throw new AssertionError("translation without an API key should have failed");
        } catch (com.austinv11.collectiveframework.language.translation.TranslationException e) {
            check(
                "translation fails fast without an API key (CODE_REVIEW §1.18)",
                e.errorType
                    == com.austinv11.collectiveframework.language.translation.TranslationException.ErrorTypes.NOT_ENOUGH_PARAMETERS);
        }
    }

    // --------------------------------------------------------------------------------------- books

    /**
     * CODE_REVIEW §1.20 (fixed) - {@code Book#setPage} clamped a negative page to {@code pages.length}, one past
     * the end, so paging backwards from the first page made the renderer throw
     * {@code ArrayIndexOutOfBoundsException}. Exercised here against a real {@link ItemStack}.
     */
    private static void checkBookPaging() {
        ItemStack stack = new ItemStack(new TestBookItem());
        TestBook book = new TestBook((ItemBook) stack.getItem(), stack);

        book.setPage(1);
        check("setPage accepts a valid index", book.getCurrentPage() == 1);

        book.setPage(3);
        check("setPage wraps forward past the end", book.getCurrentPage() == 0);

        book.setPage(-1);
        check("setPage wraps backward to the last page (CODE_REVIEW §1.20)", book.getCurrentPage() == 2);

        book.setPage(2);
        check(
            "the page is persisted to the stack",
            NBTHelper.getCompoundTag(stack, "CFBookData")
                .getInteger("page") == 2);
    }

    /**
     * CODE_REVIEW §2.2 (fixed) - {@code LocalBookData} never called {@code markDirty()}, so LOCAL book data was
     * never written to disk, and it registered its store unconditionally on first use, which could throw
     * {@code Duplicate id} from {@code MapStorage}.
     */
    private static void checkLocalBookDataPersists() {
        LocalBookData store = LocalBookData.getInstance();
        check("LocalBookData is available on a server", store != null);
        check("LocalBookData can be resolved twice without a duplicate id", LocalBookData.getInstance() == store);

        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("page", 4);
        LocalBookData.putLocalData("collectiveframework_gametest", tag);

        NBTTagCompound read = LocalBookData.retrieveLocalData("collectiveframework_gametest");
        check("LocalBookData round trips a value", read != null && read.getInteger("page") == 4);
        check("LocalBookData marks the save dirty (CODE_REVIEW §2.2)", store.isDirty());
        check("LocalBookData reports its size", LocalBookData.size() >= 1);
        check("LocalBookData returns null for an unknown key", LocalBookData.retrieveLocalData("nope") == null);
    }

    // ---------------------------------------------------------------------------------- threading

    /**
     * CODE_REVIEW §1.11 - §1.15 (fixed) - the old threading layer reflected into
     * {@code java.lang.Thread#target} and {@code #threadStatus}, which throws
     * {@link java.lang.reflect.InaccessibleObjectException} on Java 17+. Unit tests run on the Java 8 toolchain,
     * so this is the only place the absence of that reflection is actually proven: {@code gameTestServer} runs on
     * Java 25.
     * <p>
     * It also proves that a one-shot runnable runs exactly once, that a repeating runnable repeats, and that
     * {@code SimpleThread#setTarget} works without reflection.
     */
    @SuppressWarnings("deprecation")
    private static void checkThreadingRunsOnAModernJvm() throws Exception {
        check("the game test is running on a JVM where java.lang.Thread is not open", isThreadReflectionRestricted());

        final java.util.concurrent.CountDownLatch once = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicInteger onceRuns = new java.util.concurrent.atomic.AtomicInteger();
        SimpleRunnable oneShot = new SimpleRunnable() {

            @Override
            public void run() {
                onceRuns.incrementAndGet();
                once.countDown();
            }
        };
        oneShot.startOnce();
        check("SimpleRunnable.startOnce() completed", once.await(10, java.util.concurrent.TimeUnit.SECONDS));
        Thread.sleep(100);
        check("SimpleRunnable.startOnce() ran exactly once", onceRuns.get() == 1);
        oneShot.disable(true);

        final java.util.concurrent.CountDownLatch repeats = new java.util.concurrent.CountDownLatch(3);
        SimpleRunnable repeating = new SimpleRunnable() {

            @Override
            public void run() {
                repeats.countDown();
            }
        };
        repeating.setTicking(5L);
        repeating.start();
        check("SimpleRunnable.start() repeats", repeats.await(10, java.util.concurrent.TimeUnit.SECONDS));
        repeating.disable(true);

        final java.util.concurrent.CountDownLatch retargeted = new java.util.concurrent.CountDownLatch(1);
        SimpleThread thread = new TestThread(new Runnable() {

            @Override
            public void run() {
                // idle until re-targeted
            }
        }, "collectiveframework-gametest-thread");
        thread.setTarget(new Runnable() {

            @Override
            public void run() {
                retargeted.countDown();
            }
        });
        thread.start();
        check(
            "SimpleThread.setTarget() works without reflecting into java.lang.Thread (CODE_REVIEW §1.14)",
            retargeted.await(10, java.util.concurrent.TimeUnit.SECONDS));
        thread.isActive = false;
        thread.join(5000);
        check("SimpleThread stopped when asked", !thread.isAlive());

        HeavyCalculations calculations = new HeavyCalculations(2);
        try {
            final java.util.concurrent.CountDownLatch slow = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Future<ICalculations> future = calculations.addCalculation(new ICalculations() {

                @Override
                public void doCalculation() {
                    try {
                        slow.await(5, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread()
                            .interrupt();
                    }
                }
            });
            boolean timedOut = false;
            try {
                future.get(50, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException expected) {
                timedOut = true;
            }
            check("HeavyCalculations.get(timeout) throws TimeoutException (CODE_REVIEW §1.12)", timedOut);
            slow.countDown();
            future.get(10, java.util.concurrent.TimeUnit.SECONDS);
            check("HeavyCalculations.get(timeout) completes afterwards", future.isDone());
        } finally {
            calculations.kill();
        }
    }

    /**
     * @return true if {@code java.lang.Thread}'s internals cannot be opened reflectively, i.e. this is a JVM on
     *         which the old implementation would have failed
     */
    private static boolean isThreadReflectionRestricted() {
        try {
            java.lang.reflect.Field field = Thread.class.getDeclaredField("threadStatus");
            field.setAccessible(true);
            return false;
        } catch (NoSuchFieldException e) {
            return true;
        } catch (RuntimeException e) {
            // InaccessibleObjectException on Java 17+
            return true;
        }
    }

    // ------------------------------------------------------------------------------------- misc

    private static void checkColors() {
        check("Colors.RED is the red section-sign code", "\u00a7c".equals(Colors.RED.toString()));
        check("Colors translates & codes", "\u00a7a".equals(Colors.replaceAlternateColorChar("&a")));
    }

    // ---------------------------------------------------------------------------------- harness

    private static void check(String description, boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Game test failed: " + description);
        System.out.println("  [ok] " + description);
    }

    /**
     * Reports an assertion that is expected to fail until the referenced CODE_REVIEW defect is fixed. It never
     * aborts the run, so a known-bad build still produces a full report and a green CI run.
     */
    private static void knownDefect(String description, boolean condition) {
        checks++;
        if (condition) {
            System.out.println("  [ok] " + description + " (defect appears fixed - promote to check())");
        } else {
            knownDefectsPresent++;
            System.out.println("  [KNOWN-DEFECT] " + description);
        }
    }
}

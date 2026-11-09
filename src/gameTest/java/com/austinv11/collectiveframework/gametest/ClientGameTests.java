package com.austinv11.collectiveframework.gametest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.austinv11.collectiveframework.minecraft.books.api.Entry;
import com.austinv11.collectiveframework.minecraft.books.simple.ImageEntry;
import com.austinv11.collectiveframework.minecraft.books.simple.TextEntry;
import com.austinv11.collectiveframework.minecraft.client.gui.GuiConfig;
import com.austinv11.collectiveframework.utils.math.TwoDimensionalVector;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side half of the game test. It opens the real config screen and renders real book entries, both of which
 * need a GL context and are therefore invisible to the server-side checks and the unit suite.
 * <p>
 * What this covers that nothing else can:
 * <ul>
 * <li>{@code ConfigRegistry#getCategories} refuses to run on a server, so the config GUI's category list is only
 * ever exercised here (CODE_REVIEW §1.2, §1.3).</li>
 * <li>The book entry render path - {@code TextEntry} and {@code ImageEntry} - which is where the scaling pivot
 * (§2.23) and the stale {@code FontRenderer} (§2.25) live. The assertions are "it renders without throwing", not
 * pixel comparison.</li>
 * </ul>
 * Run by {@code ./gradlew runClient25 -PgameTest} under Xvfb; the CI job is non-blocking because software OpenGL
 * is environment dependent.
 * <p>
 * The run is phased rather than immediate: {@code FMLLoadCompleteEvent} fires while Minecraft is still closing its
 * splash screen, and the main menu it shows afterwards replaces whatever screen is current. Each phase therefore
 * waits a number of client ticks before acting.
 */
@SideOnly(Side.CLIENT)
public final class ClientGameTests {

    public static final String RUN_OK = "COLLECTIVEFRAMEWORK_CLIENT_GAMETEST_OK";
    public static final String RUN_FAILED = "COLLECTIVEFRAMEWORK_CLIENT_GAMETEST_FAILED";
    public static final String ASSERTIONS_OK = "CLIENT_GAMETEST_ASSERTIONS_OK";

    private static final Logger LOG = LogManager.getLogger("CollectiveFramework");

    /** Ticks to wait for the main menu before opening anything. */
    private static final int SETTLE_TICKS = 40;
    /** Ticks to let the client render each screen before asserting on it. */
    private static final int RENDER_TICKS = 5;

    private enum Phase {
        IDLE,
        SETTLE,
        SHOW_CONFIG,
        ASSERT_CONFIG,
        SHOW_ENTRIES,
        ASSERT_ENTRIES
    }

    private static Phase phase = Phase.IDLE;
    private static int ticksInPhase;
    private static int checks;
    private static volatile Throwable failure;
    private static GuiConfig configScreen;

    private ClientGameTests() {}

    /**
     * Begins the client-side run. Called from {@code FMLLoadCompleteEvent} on a client.
     */
    public static void start() {
        if (phase != Phase.IDLE) return;
        phase = Phase.SETTLE;
        ticksInPhase = 0;
    }

    /**
     * Called once per client tick by {@code GameTestMod#onClientTick}.
     */
    public static void tick() {
        if (phase == Phase.IDLE) return;
        ticksInPhase++;
        try {
            switch (phase) {
                case SETTLE:
                    if (ticksInPhase >= SETTLE_TICKS) advance(Phase.SHOW_CONFIG);
                    break;
                case SHOW_CONFIG:
                    showConfigScreen();
                    advance(Phase.ASSERT_CONFIG);
                    break;
                case ASSERT_CONFIG:
                    if (ticksInPhase < RENDER_TICKS) return;
                    assertConfigScreen();
                    showEntryScreen();
                    advance(Phase.SHOW_ENTRIES);
                    break;
                case SHOW_ENTRIES:
                    advance(Phase.ASSERT_ENTRIES);
                    break;
                case ASSERT_ENTRIES:
                    if (ticksInPhase < RENDER_TICKS) return;
                    assertEntryScreen();
                    finish();
                    break;
                default:
                    break;
            }
        } catch (Throwable t) {
            LOG.error("Client game test failed", t);
            t.printStackTrace();
            System.out.println(RUN_FAILED);
            FMLCommonHandler.instance()
                .exitJava(1, false);
        }
    }

    private static void advance(Phase next) {
        phase = next;
        ticksInPhase = 0;
    }

    private static void showConfigScreen() {
        configScreen = new GuiConfig(null);
        Minecraft.getMinecraft()
            .displayGuiScreen(configScreen);
    }

    private static void assertConfigScreen() {
        Minecraft minecraft = Minecraft.getMinecraft();
        check("the config screen is displayed", minecraft.currentScreen == configScreen);
        // ConfigRegistry#getCategories refuses to run on a server, so this list is only ever non-empty here.
        check(
            "the config screen has at least one category (CODE_REVIEW §1.2)",
            !configScreen.entryList.listEntries.isEmpty());
    }

    private static void showEntryScreen() {
        Minecraft.getMinecraft()
            .displayGuiScreen(new EntryRenderScreen());
    }

    private static void assertEntryScreen() {
        Minecraft minecraft = Minecraft.getMinecraft();
        check(
            "the entry-render screen is displayed, got " + (minecraft.currentScreen == null ? "null"
                : minecraft.currentScreen.getClass()
                    .getName()),
            minecraft.currentScreen instanceof EntryRenderScreen);
        check("no entry threw while rendering (CODE_REVIEW §2.23, §2.25)", failure == null);
    }

    private static void finish() {
        phase = Phase.IDLE;
        System.out.println(ASSERTIONS_OK + " (" + checks + " checks)");
        System.out.println(RUN_OK);
        FMLCommonHandler.instance()
            .exitJava(0, false);
    }

    private static void check(String description, boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Client game test failed: " + description);
        System.out.println("  [ok] " + description);
    }

    /**
     * Draws one {@link TextEntry} and one {@link ImageEntry} every frame, recording the first failure.
     * <p>
     * Using a real screen means the entries are rendered through Minecraft's own render pass, so a GL error or an
     * NPE in {@code onRender} shows up exactly as it would in a book.
     */
    private static class EntryRenderScreen extends GuiScreen {

        private Entry[] entries;

        @Override
        public void initGui() {
            try {
                TextEntry text = new TextEntry(
                    new TwoDimensionalVector(10, 10),
                    160,
                    40,
                    "The quick brown fox jumps over the lazy dog, and then keeps going for long enough to wrap.");
                text.useUnicodeFont = false;

                // A vanilla texture that is guaranteed to exist, so ImageEntry's ImageIO read succeeds.
                ImageEntry image = new ImageEntry(
                    new TwoDimensionalVector(10, 60),
                    64,
                    64,
                    new ResourceLocation("textures/gui/widgets.png"));

                entries = new Entry[] { text, image };
            } catch (Throwable t) {
                failure = t;
                entries = new Entry[0];
            }
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            super.drawScreen(mouseX, mouseY, partialTicks);
            if (entries == null) return;
            for (Entry entry : entries) {
                try {
                    entry.onRender(16);
                } catch (Throwable t) {
                    failure = t;
                }
            }
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }
}

package com.austinv11.collectiveframework.minecraft.books.core;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.austinv11.collectiveframework.minecraft.CollectiveFramework;
import com.austinv11.collectiveframework.minecraft.books.api.Book;
import com.austinv11.collectiveframework.minecraft.books.api.Entry;
import com.austinv11.collectiveframework.minecraft.books.api.InteractiveEntry;
import com.austinv11.collectiveframework.minecraft.books.api.Page;
import com.austinv11.collectiveframework.minecraft.utils.client.GuiUtils;
import com.austinv11.collectiveframework.utils.TimeProfiler;
import com.austinv11.collectiveframework.utils.math.TwoDimensionalVector;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiBook extends GuiScreen {

    /**
     * Decoded image sizes, keyed by resource location.
     * <p>
     * Every entry used to re-read and re-decode the background PNG on every frame - six decodes per frame for a
     * five-entry book, roughly 360 per second at 60 fps (CODE_REVIEW §5.1). The size never changes, so decode
     * once.
     */
    private static final Map<ResourceLocation, int[]> IMAGE_SIZES = new ConcurrentHashMap<ResourceLocation, int[]>();

    private ItemBook bookItem;
    private ItemStack stack;
    private Book book;
    private TimeProfiler profiler;
    private EntityPlayer player;
    /** Refreshed once per frame so the draw path and the mouse path do not each allocate one. */
    private ScaledResolution resolution;
    /** False when the book could not be instantiated; every render/input method then no-ops. */
    private boolean initialised;

    public GuiBook(EntityPlayer player) {
        // GuiHandler already refuses to open this screen for a non-book, but the constructor must not assume it:
        // it is called while Minecraft is still inside displayGuiScreen, so calling displayGuiScreen(null) from
        // here would re-enter the GUI lifecycle (CODE_REVIEW §2.20).
        ItemStack heldItem = player == null ? null : player.getHeldItem();
        if (heldItem == null || !(heldItem.getItem() instanceof ItemBook)) {
            CollectiveFramework.LOGGER.warn("Refusing to open a book gui for a player not holding a book");
            return;
        }
        stack = heldItem;
        bookItem = (ItemBook) heldItem.getItem();
        try {
            book = bookItem.getBook()
                .getConstructor(ItemBook.class, ItemStack.class)
                .newInstance(bookItem, stack);
        } catch (Exception e) {
            CollectiveFramework.LOGGER.error(
                "Exception opening book gui for " + bookItem.getClass()
                    .getName());
            e.printStackTrace();
            return;
        }
        book.handoffGui(this);
        this.player = player;
        this.initialised = true;

        // Registered only once the book exists, so a failed construction cannot leave a half-built, still-
        // subscribed listener behind (CODE_REVIEW §2.18).
        // MouseEvent is a Forge event, InputEvent is an FML event: they live on different buses (CODE_REVIEW
        // §1.1).
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (!initialised) return;
        resolution = GuiUtils.getCurrentResolution();
        if (profiler == null) profiler = new TimeProfiler();
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        // TODO: Maybe auto scale the gui?
        drawBackground();
        drawCurrentPage();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
        // CollectiveFramework.LOGGER.info(profiler.getTime());
        profiler = new TimeProfiler();
    }

    @Override
    public void initGui() {
        if (!initialised) return;
        book.onOpen(player);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
    }

    private void drawEntries(Page page) {
        int[] background = entryOrigin();
        for (Entry entry : page.getEntries()) {
            GL11.glPushMatrix();
            if (!entry.useAbsoluteCoords && background != null) GL11.glTranslated(background[0], background[1], 0);
            GL11.glRotatef(entry.getPitch(), 1, 0, 0);
            GL11.glRotatef(entry.getYaw(), 0, 1, 0);
            GL11.glRotatef(entry.getRotation(), 0, 0, 1);
            if (entry.drawDebugLines) drawRect(
                entry.getCoords()
                    .getRoundedX(),
                entry.getCoords()
                    .getRoundedY(),
                entry.getCoords()
                    .getRoundedX() + entry.width,
                entry.getCoords()
                    .getRoundedY() + entry.height,
                Color.LIGHT_GRAY.getRGB());
            entry.onRender((int) profiler.getTime());
            if (entry.drawDebugLines) {
                drawHorizontalLine(
                    entry.getCoords()
                        .getRoundedX(),
                    entry.getCoords()
                        .getRoundedX() + entry.width,
                    entry.getCoords()
                        .getRoundedY(),
                    Color.BLACK.getRGB());
                drawHorizontalLine(
                    entry.getCoords()
                        .getRoundedX(),
                    entry.getCoords()
                        .getRoundedX() + entry.width,
                    entry.getCoords()
                        .getRoundedY() + entry.height,
                    Color.BLACK.getRGB());
                drawVerticalLine(
                    entry.getCoords()
                        .getRoundedX(),
                    entry.getCoords()
                        .getRoundedY(),
                    entry.getCoords()
                        .getRoundedY() + entry.height,
                    Color.BLACK.getRGB());
                drawVerticalLine(
                    entry.getCoords()
                        .getRoundedX() + entry.width,
                    entry.getCoords()
                        .getRoundedY(),
                    entry.getCoords()
                        .getRoundedY() + entry.height,
                    Color.BLACK.getRGB());
            }
            GL11.glPopMatrix();
        }
    }

    private void drawCurrentPage() {
        Page[] pages = book.getPages();
        if (pages.length == 0) return;
        // setPage clamps to a valid index, but never trust a stored page from an older save (CODE_REVIEW §1.20).
        int currentPage = Math.max(0, Math.min(book.getCurrentPage(), pages.length - 1));
        Page page = pages[currentPage];
        ResourceLocation background = page.getBackground();
        ScaledResolution screen = currentResolution();
        if (background != null) {
            int[] size = imageSize(background);
            if (size != null) {
                int backX = (screen.getScaledWidth() - size[0]) / 2;
                int backY = (screen.getScaledHeight() - size[1]) / 2;
                Minecraft.getMinecraft()
                    .getTextureManager()
                    .bindTexture(background);
                drawTexturedModalRect(backX, backY, 0, 0, screen.getScaledWidth(), screen.getScaledHeight());
            }
        }
        page.onRender((int) profiler.getTime());
        drawEntries(page);
    }

    private void drawBackground() {
        ResourceLocation background = book.getBackground();
        ScaledResolution screen = currentResolution();
        if (background != null) {
            int[] size = imageSize(background);
            if (size != null) {
                int backX = (screen.getScaledWidth() - size[0]) / 2;
                int backY = (screen.getScaledHeight() - size[1]) / 2;
                Minecraft.getMinecraft()
                    .getTextureManager()
                    .bindTexture(background);
                drawTexturedModalRect(backX, backY, 0, 0, size[0], size[1]);
            }
        }
        book.onRender((int) profiler.getTime());
        if (book.drawDebugLines) {
            for (int x = 0; x < resolution.getScaledWidth(); x++) {
                if (x % 2 == 0 && x % 4 != 0) {
                    drawVerticalLine(x, 0, resolution.getScaledHeight(), Color.LIGHT_GRAY.getRGB());
                } else if (x % 4 == 0) {
                    drawHorizontalLine(x, 0, resolution.getScaledHeight(), Color.GRAY.getRGB());
                }
            }
            for (int y = 0; y < resolution.getScaledHeight(); y++) {
                if (y % 2 == 0 && y % 4 != 0) {
                    drawHorizontalLine(0, resolution.getScaledWidth(), y, Color.LIGHT_GRAY.getRGB());
                } else if (y % 4 == 0) {
                    drawHorizontalLine(0, resolution.getScaledWidth(), y, Color.GRAY.getRGB());
                }
            }
        }
    }

    @SubscribeEvent
    public void onKeyboardInput(InputEvent.KeyInputEvent event) {
        if (!initialised || Minecraft.getMinecraft().currentScreen != this) return;
        char eventChar = Keyboard.getEventCharacter();
        boolean isDown = Keyboard.getEventKeyState();
        Page page = currentPage();
        if (page == null) return;
        for (Entry entry : page.getEntries()) if (entry instanceof InteractiveEntry) {
            ((InteractiveEntry) entry).onKeyboardEvent(eventChar, isDown);
        }
    }

    @SubscribeEvent
    public void onMouseInput(MouseEvent event) {
        if (!initialised || Minecraft.getMinecraft().currentScreen != this) return;
        Page page = currentPage();
        if (page == null) return;
        int[] origin = entryOrigin();
        for (Entry entry : page.getEntries()) if (entry instanceof InteractiveEntry) {
            int offsetX = 0, offsetY = 0;
            TwoDimensionalVector startCoords = entry.getCoords();
            if (!entry.useAbsoluteCoords && origin != null) {
                offsetX = origin[0];
                offsetY = origin[1];
                startCoords = startCoords.add(new TwoDimensionalVector(offsetX, offsetY));
            }
            // The lower bound was compared against entry.height instead of the entry's own bottom edge, and the
            // wheel/button arguments were swapped relative to InteractiveEntry#onMouseEvent
            // (CODE_REVIEW §2.19).
            if (event.x >= startCoords.getRoundedX() && event.x <= startCoords.getRoundedX() + entry.width
                && event.y >= startCoords.getRoundedY()
                && event.y <= startCoords.getRoundedY() + entry.height)
                ((InteractiveEntry) entry).onMouseEvent(
                    event.x - offsetX,
                    event.y - offsetY,
                    event.dx,
                    event.dy,
                    event.dwheel,
                    event.button,
                    event.buttonstate);
        }
    }

    @Override
    public void onGuiClosed() {
        if (book != null) book.onClose();
        MinecraftForge.EVENT_BUS.unregister(this);
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        initialised = false;
    }

    // ------------------------------------------------------------------------------- helpers

    /**
     * @return The page currently shown, or null if the book has no pages
     */
    private Page currentPage() {
        Page[] pages = book.getPages();
        if (pages.length == 0) return null;
        return pages[Math.max(0, Math.min(book.getCurrentPage(), pages.length - 1))];
    }

    /**
     * @return The top-left corner of the book background, or null if there is no background
     */
    private int[] entryOrigin() {
        ResourceLocation background = book.getBackground();
        if (background == null) return null;
        int[] size = imageSize(background);
        if (size == null) return null;
        ScaledResolution screen = currentResolution();
        return new int[] { (screen.getScaledWidth() - size[0]) / 2, (screen.getScaledHeight() - size[1]) / 2 };
    }

    private ScaledResolution currentResolution() {
        if (resolution == null) resolution = GuiUtils.getCurrentResolution();
        return resolution;
    }

    /**
     * Decodes an image's dimensions, once per resource location.
     *
     * @param location The image
     * @return {width, height}, or null if it could not be read
     */
    private static int[] imageSize(ResourceLocation location) {
        int[] cached = IMAGE_SIZES.get(location);
        if (cached != null) return cached;
        try {
            BufferedImage image = ImageIO.read(GuiUtils.getResourceAsStream(location));
            if (image == null) {
                CollectiveFramework.LOGGER.warn("Unable to decode book image " + location);
                return null;
            }
            int[] size = new int[] { image.getWidth(), image.getHeight() };
            IMAGE_SIZES.put(location, size);
            return size;
        } catch (IOException e) {
            CollectiveFramework.LOGGER.warn("Unable to read book image " + location);
            CollectiveFramework.LOGGER.debug(e);
            return null;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return book.doesPauseGame();
    }
}

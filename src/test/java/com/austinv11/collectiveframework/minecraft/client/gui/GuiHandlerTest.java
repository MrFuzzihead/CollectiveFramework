package com.austinv11.collectiveframework.minecraft.client.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.world.World;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.austinv11.collectiveframework.minecraft.books.core.ItemBook;
import com.austinv11.collectiveframework.minecraft.reference.Reference;

/**
 * CODE_REVIEW §2.20 - {@code GuiBook}'s constructor used to call {@code GuiUtils.closeCurrentGui()} when the
 * held item was not a book. That ran while {@code Minecraft} was still inside {@code displayGuiScreen}, and the
 * half-built screen was then assigned as the current screen anyway. Validation now happens here, before the
 * screen is constructed.
 */
public class GuiHandlerTest {

    private final GuiHandler handler = new GuiHandler();

    private static EntityPlayer playerHolding(ItemStack stack) {
        EntityPlayer player = Mockito.mock(EntityPlayer.class);
        Mockito.when(player.getHeldItem())
            .thenReturn(stack);
        return player;
    }

    @Test
    public void refusesANullPlayer() {
        Assert.assertNull(handler.getClientGuiElement(Reference.Guis.BOOK.ordinal(), null, null, 0, 0, 0));
    }

    @Test
    public void refusesAPlayerWithAnEmptyHand() {
        Assert.assertNull(
            handler.getClientGuiElement(
                Reference.Guis.BOOK.ordinal(),
                playerHolding(null),
                Mockito.mock(World.class),
                0,
                0,
                0));
    }

    @Test
    public void refusesAPlayerHoldingSomethingElse() {
        Item sword = Mockito.mock(ItemSword.class);
        Assert.assertNull(
            handler.getClientGuiElement(
                Reference.Guis.BOOK.ordinal(),
                playerHolding(new ItemStack(sword)),
                Mockito.mock(World.class),
                0,
                0,
                0));
    }

    @Test
    public void refusesAnUnknownGuiId() {
        Item book = Mockito.mock(ItemBook.class);
        Assert.assertNull(
            handler.getClientGuiElement(
                Reference.Guis.BOOK.ordinal() + 99,
                playerHolding(new ItemStack(book)),
                Mockito.mock(World.class),
                0,
                0,
                0));
    }

    @Test
    public void hasNoServerGuiElement() {
        Assert.assertNull(
            handler.getServerGuiElement(
                Reference.Guis.BOOK.ordinal(),
                playerHolding(null),
                Mockito.mock(World.class),
                0,
                0,
                0));
    }
}

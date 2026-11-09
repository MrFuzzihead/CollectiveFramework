package com.austinv11.collectiveframework.minecraft.client.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.austinv11.collectiveframework.minecraft.books.core.GuiBook;
import com.austinv11.collectiveframework.minecraft.books.core.ItemBook;
import com.austinv11.collectiveframework.minecraft.reference.Reference;

import cpw.mods.fml.common.network.IGuiHandler;

public class GuiHandler implements IGuiHandler {

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID != Reference.Guis.BOOK.ordinal()) return null;
        // Validate before constructing the screen. GuiBook's constructor used to call displayGuiScreen(null) on
        // failure, from inside Minecraft's own displayGuiScreen call, and the broken screen was then assigned as
        // the current screen anyway (CODE_REVIEW §2.20).
        if (player == null) return null;
        ItemStack heldItem = player.getHeldItem();
        if (heldItem == null || !(heldItem.getItem() instanceof ItemBook)) return null;
        return new GuiBook(player);
    }
}

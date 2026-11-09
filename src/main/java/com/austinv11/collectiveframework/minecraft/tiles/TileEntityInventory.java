package com.austinv11.collectiveframework.minecraft.tiles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.Constants;

/**
 * Simple implementation for a Tile Entity to contain an inventory
 */
public abstract class TileEntityInventory extends TileEntity implements IInventory {

    public List<ItemStack> items;
    public String invName = "null";
    private int numUsingPlayers = 0;

    public TileEntityInventory() {
        // Deliberately not sized here: getSize() is abstract, so a subclass's fields are still uninitialised and
        // the inventory would be created with zero slots (CODE_REVIEW §2.4). Sizing happens on first use.
        items = new ArrayList<ItemStack>();
    }

    /**
     * Sizes {@link #items} to {@link #getSize()}, preserving anything already stored.
     */
    protected final void ensureCapacity() {
        int size = getSize();
        if (items.size() == size) return;
        List<ItemStack> resized = new ArrayList<ItemStack>(size);
        for (int i = 0; i < size; i++) resized.add(i < items.size() ? items.get(i) : null);
        items = resized;
    }

    /**
     * This represents how many slots are in the inventory
     *
     * @return The number of slots
     */
    public abstract int getSize();

    @Override
    public void updateEntity() {
        super.updateEntity();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        NBTTagList itemsNbt = nbttagcompound.getTagList("items", Constants.NBT.TAG_COMPOUND);
        items = Arrays.asList(new ItemStack[getSize()]);
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            NBTTagCompound itemNbt = itemsNbt.getCompoundTagAt(itemIndex);
            items.set(itemIndex, ItemStack.loadItemStackFromNBT(itemNbt));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        ensureCapacity();
        NBTTagList itemsNbt = new NBTTagList();
        for (ItemStack item : items) {
            NBTTagCompound itemNbt = new NBTTagCompound();
            if (item != null) item.writeToNBT(itemNbt);
            itemsNbt.appendTag(itemNbt);
        }
        nbttagcompound.setTag("items", itemsNbt);
    }

    @Override
    public int getSizeInventory() {
        ensureCapacity();
        return items.size();
    }

    @Override
    @Nullable
    public ItemStack getStackInSlot(int slot) {
        ensureCapacity();
        if (slot >= 0 && items.size() > slot) return items.get(slot);
        return null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        ensureCapacity();
        return decrStackSizeStatic(this, items, slot, amount);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Implemented here rather than left abstract: the class already shipped
     * {@link #removeStackFromSlotStatic(List, int)} but never wired it in, so every subclass had to reimplement
     * it (CODE_REVIEW §6.7).
     */
    @Override
    @Nullable
    public ItemStack getStackInSlotOnClosing(int slot) {
        ensureCapacity();
        return removeStackFromSlotStatic(items, slot);
    }

    @Nullable
    public static ItemStack decrStackSizeStatic(IInventory inventory, List<ItemStack> items, int slot, int amount) {
        if (items.size() > slot) {
            if (items.get(slot) != null) {
                if (items.get(slot).stackSize <= amount) {
                    ItemStack item = items.get(slot);
                    items.set(slot, null);
                    inventory.markDirty();
                    return item;
                }
                ItemStack item = items.get(slot)
                    .splitStack(amount);
                if (items.get(slot).stackSize == 0) {
                    items.set(slot, null);
                }
                inventory.markDirty();
                return item;
            }
        }
        return null;
    }

    @Nullable
    public static ItemStack removeStackFromSlotStatic(List<ItemStack> items, int slot) {
        if (items.size() > slot) {
            if (items.get(slot) != null) {
                ItemStack item = items.get(slot);
                items.set(slot, null);
                return item;
            }
        }
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        ensureCapacity();
        setInventorySlotContentsStatic(this, items, slot, stack);
    }

    public static void setInventorySlotContentsStatic(IInventory inventory, List<ItemStack> items, int slot,
        ItemStack stack) {
        if (items.size() > slot) {
            items.set(slot, stack);
            if (stack != null && stack.stackSize > inventory.getInventoryStackLimit()) {
                stack.stackSize = inventory.getInventoryStackLimit();
            }
            inventory.markDirty();
        }
    }

    @Override
    public String getInventoryName() {
        return invName;
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        if (worldObj == null) {
            return true;
        }
        if (worldObj.getTileEntity(xCoord, yCoord, zCoord) != this) {
            return false;
        }
        return player.getDistanceSq((double) xCoord + 0.5D, (double) yCoord + 0.5D, (double) zCoord + 0.5D) <= 64D;
    }

    @Override
    public void openInventory() {
        numUsingPlayers++;
        worldObj.addBlockEvent(xCoord, yCoord, zCoord, this.getBlockType(), 1, numUsingPlayers);
    }

    @Override
    public void closeInventory() {
        numUsingPlayers--;
        worldObj.addBlockEvent(xCoord, yCoord, zCoord, this.getBlockType(), 1, numUsingPlayers);
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return true;
    }

    @Override
    public void markDirty() {
        ensureCapacity();
        for (int i = 0; i < items.size(); i++)
            if (items.get(i) != null && items.get(i).stackSize < 1) items.set(i, null);
        super.markDirty();
    }
}

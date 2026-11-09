package com.austinv11.collectiveframework.minecraft.tiles;

import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * {@link TileEntityInventory} had no test; its coverage came only from the in-game suite, which JaCoCo does not
 * measure.
 * <p>
 * The NBT paths are not exercised here: {@code TileEntity#writeToNBT} refuses to serialise a class that was never
 * registered with {@code GameRegistry}, and {@code openInventory}/{@code closeInventory} need a world. What is
 * covered is the whole {@code IInventory} surface, which is where the actual logic lives.
 */
public class TileEntityInventoryTest {

    private static class TestInventory extends TileEntityInventory {

        private final int size;

        TestInventory(int size) {
            this.size = size;
        }

        @Override
        public int getSize() {
            return size;
        }

    }

    private static ItemStack stack(int size) {
        return new ItemStack(new Item(), size);
    }

    private TestInventory inventory;

    @Before
    public void createInventory() {
        inventory = new TestInventory(4);
    }

    // -------------------------------------------------------------------------------------- size

    @Test
    public void theInventoryIsSizedFromGetSize() {
        Assert.assertEquals(4, inventory.getSizeInventory());
        Assert.assertEquals(4, inventory.items.size());
    }

    @Test
    public void everySlotStartsEmpty() {
        for (int i = 0; i < 4; i++) Assert.assertNull(inventory.getStackInSlot(i));
    }

    @Test
    public void getStackInSlotIsNullPastTheEnd() {
        Assert.assertNull(inventory.getStackInSlot(4));
        Assert.assertNull(inventory.getStackInSlot(99));
    }

    @Test
    public void theStackLimitIsSixtyFour() {
        Assert.assertEquals(64, inventory.getInventoryStackLimit());
    }

    @Test
    public void everySlotAcceptsAnyItemByDefault() {
        Assert.assertTrue(inventory.isItemValidForSlot(0, stack(1)));
    }

    @Test
    public void theInventoryNameDefaultsToNull() {
        Assert.assertEquals("null", inventory.getInventoryName());
        Assert.assertFalse(inventory.hasCustomInventoryName());
    }

    // --------------------------------------------------------------------------- setting a slot

    @Test
    public void settingASlotStoresTheStack() {
        ItemStack stack = stack(5);
        inventory.setInventorySlotContents(1, stack);
        Assert.assertSame(stack, inventory.getStackInSlot(1));
    }

    @Test
    public void settingASlotClampsToTheStackLimit() {
        inventory.setInventorySlotContents(0, stack(200));
        Assert.assertEquals(64, inventory.getStackInSlot(0).stackSize);
    }

    @Test
    public void settingASlotPastTheEndIsIgnored() {
        inventory.setInventorySlotContents(9, stack(1));
        for (int i = 0; i < 4; i++) Assert.assertNull(inventory.getStackInSlot(i));
    }

    @Test
    public void settingASlotToNullClearsIt() {
        inventory.setInventorySlotContents(0, stack(1));
        inventory.setInventorySlotContents(0, null);
        Assert.assertNull(inventory.getStackInSlot(0));
    }

    // ------------------------------------------------------------------------ decrementing a slot

    @Test
    public void decrementingBelowTheStackSizeReturnsAPartialStack() {
        inventory.setInventorySlotContents(0, stack(10));

        ItemStack taken = inventory.decrStackSize(0, 4);

        Assert.assertEquals(4, taken.stackSize);
        Assert.assertEquals(6, inventory.getStackInSlot(0).stackSize);
    }

    @Test
    public void decrementingTheWholeStackClearsTheSlot() {
        inventory.setInventorySlotContents(0, stack(10));

        ItemStack taken = inventory.decrStackSize(0, 10);

        Assert.assertEquals(10, taken.stackSize);
        Assert.assertNull(inventory.getStackInSlot(0));
    }

    @Test
    public void decrementingMoreThanPresentReturnsTheWholeStack() {
        inventory.setInventorySlotContents(0, stack(3));

        ItemStack taken = inventory.decrStackSize(0, 99);

        Assert.assertEquals(3, taken.stackSize);
        Assert.assertNull(inventory.getStackInSlot(0));
    }

    @Test
    public void decrementingAnEmptySlotReturnsNull() {
        Assert.assertNull(inventory.decrStackSize(0, 1));
    }

    @Test
    public void decrementingPastTheEndReturnsNull() {
        Assert.assertNull(inventory.decrStackSize(99, 1));
    }

    @Test
    public void removingAStackFromASlotClearsIt() {
        inventory.setInventorySlotContents(2, stack(7));

        ItemStack removed = inventory.getStackInSlotOnClosing(2);

        Assert.assertEquals(7, removed.stackSize);
        Assert.assertNull(inventory.getStackInSlot(2));
    }

    @Test
    public void removingAnEmptySlotReturnsNull() {
        Assert.assertNull(inventory.getStackInSlotOnClosing(0));
    }

    // ------------------------------------------------------------------------------- usability

    @Test
    public void aWorldLessInventoryIsAlwaysUsable() {
        Assert.assertTrue(inventory.isUseableByPlayer(Mockito.mock(EntityPlayer.class)));
    }

    // --------------------------------------------------------------------------- static helpers

    @Test
    public void theStaticHelpersWorkOnAPlainList() {
        List<ItemStack> items = Arrays.asList(new ItemStack[3]);
        TestInventory host = new TestInventory(3);

        TestInventory.setInventorySlotContentsStatic(host, items, 1, stack(2));
        Assert.assertEquals(2, items.get(1).stackSize);

        ItemStack taken = TestInventory.decrStackSizeStatic(host, items, 1, 1);
        Assert.assertEquals(1, taken.stackSize);
        Assert.assertEquals(1, items.get(1).stackSize);

        ItemStack removed = TestInventory.removeStackFromSlotStatic(items, 1);
        Assert.assertEquals(1, removed.stackSize);
        Assert.assertNull(items.get(1));
    }

    @Test
    public void markDirtyPrunesEmptyStacks() {
        inventory.setInventorySlotContents(0, stack(1));
        inventory.getStackInSlot(0).stackSize = 0;

        inventory.markDirty();

        Assert.assertNull("a zero-sized stack should be pruned", inventory.getStackInSlot(0));
    }

    @Test
    public void markDirtyLeavesNormalStacksAlone() {
        inventory.setInventorySlotContents(0, stack(2));
        inventory.markDirty();
        Assert.assertEquals(2, inventory.getStackInSlot(0).stackSize);
    }

    @Test
    public void updateEntityIsANoOpWithoutAWorld() {
        inventory.updateEntity();
    }
}

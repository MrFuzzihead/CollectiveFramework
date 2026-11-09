package com.austinv11.collectiveframework.minecraft.inventory;

import java.util.Set;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * {@link ExclusiveSlot} had no test. It is pure slot logic over an {@link ItemStack}, so it needs no game - a
 * plain {@code new Item()} stands in for a vanilla item, whose registry lookups are null outside a game.
 */
public class ExclusiveSlotTest {

    private static ExclusiveSlot slot() {
        return new ExclusiveSlot(Mockito.mock(IInventory.class), 0, 0, 0);
    }

    private static ItemStack stackOf(Item item) {
        return new ItemStack(item);
    }

    @Test
    public void withNoFilterEveryItemIsValid() {
        Assert.assertTrue(slot().isItemValid(stackOf(new Item())));
    }

    @Test
    public void aWhitelistAcceptsOnlyListedItems() {
        Item allowed = new Item();
        Item other = new Item();
        Set<Item> items = new java.util.HashSet<Item>();
        items.add(allowed);

        ExclusiveSlot slot = slot().setExclusive(items);

        Assert.assertTrue(slot.isItemValid(stackOf(allowed)));
        Assert.assertFalse(slot.isItemValid(stackOf(other)));
    }

    @Test
    public void aBlacklistRejectsListedItems() {
        Item blocked = new Item();
        Item other = new Item();
        Set<Item> items = new java.util.HashSet<Item>();
        items.add(blocked);

        ExclusiveSlot slot = slot().setExclusive(items)
            .setWhitelist(false);

        Assert.assertFalse(slot.isItemValid(stackOf(blocked)));
        Assert.assertTrue(slot.isItemValid(stackOf(other)));
    }

    @Test
    public void theSettersAreChainable() {
        ExclusiveSlot slot = slot();
        Assert.assertSame(slot, slot.setExclusive(new java.util.HashSet<Item>()));
        Assert.assertSame(slot, slot.setWhitelist(false));
    }

    @Test
    public void anEmptyWhitelistAcceptsNothing() {
        ExclusiveSlot slot = slot().setExclusive(new java.util.HashSet<Item>());
        Assert.assertFalse(slot.isItemValid(stackOf(new Item())));
    }

    @Test
    public void anEmptyBlacklistAcceptsEverything() {
        ExclusiveSlot slot = slot().setExclusive(new java.util.HashSet<Item>())
            .setWhitelist(false);
        Assert.assertTrue(slot.isItemValid(stackOf(new Item())));
    }
}

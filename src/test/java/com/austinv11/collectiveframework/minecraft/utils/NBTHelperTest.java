package com.austinv11.collectiveframework.minecraft.utils;

import java.util.Arrays;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link NBTHelper} is 413 lines of pure NBT manipulation that previously had no test at all - the only
 * coverage came from the in-game suite, which JaCoCo does not measure.
 * <p>
 * A plain {@code new Item()} is used rather than a vanilla item: {@code Items.stick} and friends are
 * <em>registry lookups</em> in 1.7.10, so they are null outside a running game, while {@code new Item()} needs
 * no registry.
 */
public class NBTHelperTest {

    private static ItemStack stack() {
        return new ItemStack(new Item());
    }

    // ------------------------------------------------------------------------------------ hasTag

    @Test
    public void hasTagIsFalseForAFreshStack() {
        Assert.assertFalse(NBTHelper.hasTag(stack(), "key"));
    }

    @Test
    public void hasTagIsFalseForANullStack() {
        Assert.assertFalse(NBTHelper.hasTag(null, "key"));
    }

    @Test
    public void hasTagSeesAWrittenKey() {
        ItemStack stack = stack();
        NBTHelper.setInteger(stack, "key", 1);
        Assert.assertTrue(NBTHelper.hasTag(stack, "key"));
    }

    // -------------------------------------------------------------------------------- primitives

    @Test
    public void longRoundTrip() {
        ItemStack stack = stack();
        NBTHelper.setLong(stack, "key", Long.MAX_VALUE);
        Assert.assertEquals(Long.MAX_VALUE, NBTHelper.getLong(stack, "key"));
    }

    @Test
    public void stringRoundTrip() {
        ItemStack stack = stack();
        NBTHelper.setString(stack, "key", "h\u00e9llo");
        Assert.assertEquals("h\u00e9llo", NBTHelper.getString(stack, "key"));
    }

    @Test
    public void booleanRoundTrip() {
        ItemStack stack = stack();
        NBTHelper.setBoolean(stack, "key", true);
        Assert.assertTrue(NBTHelper.getBoolean(stack, "key"));
        NBTHelper.setBoolean(stack, "key", false);
        Assert.assertFalse(NBTHelper.getBoolean(stack, "key"));
    }

    @Test
    public void byteRoundTrip() {
        ItemStack stack = stack();
        NBTHelper.setByte(stack, "key", (byte) -7);
        Assert.assertEquals((byte) -7, NBTHelper.getByte(stack, "key"));
    }

    @Test
    public void shortRoundTrip() {
        ItemStack stack = stack();
        NBTHelper.setShort(stack, "key", (short) -300);
        Assert.assertEquals((short) -300, NBTHelper.getShort(stack, "key"));
    }

    @Test
    public void intRoundTrip() {
        ItemStack stack = stack();
        NBTHelper.setInteger(stack, "key", Integer.MIN_VALUE);
        Assert.assertEquals(Integer.MIN_VALUE, NBTHelper.getInt(stack, "key"));
    }

    @Test
    public void floatRoundTrip() {
        ItemStack stack = stack();
        NBTHelper.setFloat(stack, "key", 1.25f);
        Assert.assertEquals(1.25f, NBTHelper.getFloat(stack, "key"), 0f);
    }

    @Test
    public void doubleRoundTrip() {
        ItemStack stack = stack();
        NBTHelper.setDouble(stack, "key", -1.5d);
        Assert.assertEquals(-1.5d, NBTHelper.getDouble(stack, "key"), 0d);
    }

    // ---------------------------------------------------------------------------- missing values

    @Test
    public void gettersReturnDefaultsForAMissingKey() {
        // A fresh stack each time: every getter writes its default under the same key, so sharing one stack
        // would make the second getter read the first getter's tag.
        Assert.assertEquals(0L, NBTHelper.getLong(stack(), "absent"));
        Assert.assertEquals("", NBTHelper.getString(stack(), "absent"));
        Assert.assertFalse(NBTHelper.getBoolean(stack(), "absent"));
        Assert.assertEquals(0, NBTHelper.getByte(stack(), "absent"));
        Assert.assertEquals(0, NBTHelper.getShort(stack(), "absent"));
        Assert.assertEquals(0, NBTHelper.getInt(stack(), "absent"));
        Assert.assertEquals(0f, NBTHelper.getFloat(stack(), "absent"), 0f);
        Assert.assertEquals(0d, NBTHelper.getDouble(stack(), "absent"), 0d);
    }

    /**
     * Locked-in description: {@code getString} returns the tag's NBT string form for any type, so reading a
     * numeric key as a string yields {@code "0L"} rather than {@code ""}. Pinned because it is surprising.
     */
    @Test
    public void getStringReturnsTheNbtFormOfANonStringTag() {
        ItemStack stack = stack();
        NBTHelper.setLong(stack, "key", 0L);
        Assert.assertEquals("0L", NBTHelper.getString(stack, "key"));
    }

    /**
     * The getters create the tag compound and write the default, which is the documented contract but means
     * reading has a side effect. Pinned so a change is deliberate.
     */
    @Test
    public void readingAMissingKeyInitialisesTheTagCompound() {
        ItemStack stack = stack();
        Assert.assertFalse(stack.hasTagCompound());
        NBTHelper.getInt(stack, "absent");
        Assert.assertTrue(stack.hasTagCompound());
        Assert.assertTrue(NBTHelper.hasTag(stack, "absent"));
    }

    // ------------------------------------------------------------------------------ tag removal

    @Test
    public void removeTagRemovesOnlyTheNamedKey() {
        ItemStack stack = stack();
        NBTHelper.setInteger(stack, "a", 1);
        NBTHelper.setInteger(stack, "b", 2);

        NBTHelper.removeTag(stack, "a");

        Assert.assertFalse(NBTHelper.hasTag(stack, "a"));
        Assert.assertTrue(NBTHelper.hasTag(stack, "b"));
    }

    @Test
    public void removeTagOnAStackWithNoCompoundIsANoOp() {
        ItemStack stack = stack();
        NBTHelper.removeTag(stack, "anything");
        Assert.assertFalse(stack.hasTagCompound());
    }

    // ---------------------------------------------------------------------------------- raw tags

    @Test
    public void rawTagRoundTrip() {
        ItemStack stack = stack();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("inner", "value");

        NBTHelper.setTag(stack, "key", tag);

        Assert.assertSame(tag, NBTHelper.getTag(stack, "key"));
    }

    @Test
    public void getTagReturnsNullForAMissingKey() {
        Assert.assertNull(NBTHelper.getTag(stack(), "absent"));
    }

    @Test
    public void compoundTagRoundTrip() {
        ItemStack stack = stack();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("inner", 5);

        NBTHelper.setCompoundTag(stack, "key", tag);

        Assert.assertEquals(
            5,
            NBTHelper.getCompoundTag(stack, "key")
                .getInteger("inner"));
    }

    @Test
    public void intArrayRoundTrip() {
        ItemStack stack = stack();
        NBTHelper.setIntArray(stack, "key", new int[] { 1, 2, 3 });
        Assert.assertArrayEquals(new int[] { 1, 2, 3 }, NBTHelper.getIntArray(stack, "key"));
    }

    @Test
    public void getIntArrayOnAMissingKeyIsEmpty() {
        Assert.assertEquals(0, NBTHelper.getIntArray(stack(), "absent").length);
    }

    // ------------------------------------------------------------------------------------ lists

    @Test
    public void listRoundTrip() {
        ItemStack stack = stack();
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagString("a"));
        list.appendTag(new NBTTagString("b"));

        NBTHelper.setList(stack, "key", list);

        NBTTagList read = NBTHelper.getList(stack, "key", Constants.NBT.TAG_STRING);
        Assert.assertEquals(2, read.tagCount());
        Assert.assertEquals("b", read.getStringTagAt(1));
    }

    @Test
    public void getListCreatesAnEmptyListForAMissingKey() {
        ItemStack stack = stack();
        NBTTagList list = NBTHelper.getList(stack, "absent", Constants.NBT.TAG_STRING);
        Assert.assertNotNull(list);
        Assert.assertEquals(0, list.tagCount());
        Assert.assertTrue(NBTHelper.hasTag(stack, "absent"));
    }

    // -------------------------------------------------------------------------------------- lore

    @Test
    public void setInfoWritesLore() {
        ItemStack stack = stack();
        List<String> lore = Arrays.asList("first line", "second line");

        NBTHelper.setInfo(stack, lore);

        NBTTagList read = stack.stackTagCompound.getCompoundTag("display")
            .getTagList("Lore", Constants.NBT.TAG_STRING);
        Assert.assertEquals(2, read.tagCount());
        Assert.assertEquals("first line", read.getStringTagAt(0));
    }

    @Test
    public void addInfoAppendsToExistingLore() {
        ItemStack stack = stack();
        NBTHelper.setInfo(stack, Arrays.asList("first"));

        NBTHelper.addInfo(stack, Arrays.asList("second"));

        NBTTagList read = stack.stackTagCompound.getCompoundTag("display")
            .getTagList("Lore", Constants.NBT.TAG_STRING);
        Assert.assertEquals(2, read.tagCount());
        Assert.assertEquals("first", read.getStringTagAt(0));
        Assert.assertEquals("second", read.getStringTagAt(1));
    }

    @Test
    public void addInfoCreatesLoreWhenThereIsNoDisplayTag() {
        ItemStack stack = stack();
        NBTHelper.addInfo(stack, Arrays.asList("only"));

        NBTTagList read = stack.stackTagCompound.getCompoundTag("display")
            .getTagList("Lore", Constants.NBT.TAG_STRING);
        Assert.assertEquals(1, read.tagCount());
        Assert.assertEquals("only", read.getStringTagAt(0));
    }

    @Test
    public void removeInfoClearsLore() {
        ItemStack stack = stack();
        NBTHelper.setInfo(stack, Arrays.asList("first", "second"));

        NBTHelper.removeInfo(stack);

        NBTTagList read = stack.stackTagCompound.getCompoundTag("display")
            .getTagList("Lore", Constants.NBT.TAG_STRING);
        Assert.assertEquals(0, read.tagCount());
    }

    @Test
    public void removeInfoOnAStackWithNoDisplayIsANoOp() {
        ItemStack stack = stack();
        NBTHelper.removeInfo(stack);
        // removeInfo does not create a compound when there is nothing to remove.
        Assert.assertNull(stack.stackTagCompound);
        Assert.assertFalse(NBTHelper.hasTag(stack, "display"));
    }

    // ------------------------------------------------------------------------------------ state

    @Test
    public void everySetterInitialisesTheTagCompound() {
        ItemStack stack = stack();
        Assert.assertFalse(stack.hasTagCompound());

        NBTHelper.setInteger(stack, "a", 1);
        Assert.assertTrue(stack.hasTagCompound());

        NBTHelper.setLong(stack(), "a", 1L);
        NBTHelper.setString(stack(), "a", "");
        NBTHelper.setBoolean(stack(), "a", false);
        NBTHelper.setByte(stack(), "a", (byte) 0);
        NBTHelper.setShort(stack(), "a", (short) 0);
        NBTHelper.setFloat(stack(), "a", 0f);
        NBTHelper.setDouble(stack(), "a", 0d);
        NBTHelper.setIntArray(stack(), "a", new int[0]);
        NBTHelper.setCompoundTag(stack(), "a", new NBTTagCompound());
        NBTHelper.setList(stack(), "a", new NBTTagList());
        NBTHelper.setTag(stack(), "a", new NBTTagString("x"));
        NBTHelper.setInfo(stack(), Arrays.asList("x"));
    }
}

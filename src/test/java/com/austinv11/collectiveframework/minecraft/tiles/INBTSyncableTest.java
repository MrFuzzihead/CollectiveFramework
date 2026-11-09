package com.austinv11.collectiveframework.minecraft.tiles;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * CODE_REVIEW §1.17 - the sync packets used to call {@code TileEntity#readFromNBT} on whatever tile entity sat
 * at the coordinates in the packet, so any client could overwrite the complete NBT (including the coordinates)
 * of any tile entity. {@link INBTSyncable#applyTo} is the single gate both packets now go through.
 */
public class INBTSyncableTest {

    /** A tile entity that has not opted in - the exploit target. */
    private static class PlainTileEntity extends TileEntity {
    }

    /** A tile entity that opted in. */
    private static class SyncedTileEntity extends TileEntity implements INBTSyncable {

        NBTTagCompound received;
        int writes;

        @Override
        public NBTTagCompound writeSyncData() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("value", 7);
            return tag;
        }

        @Override
        public void readSyncData(NBTTagCompound data) {
            received = data;
            writes++;
        }
    }

    private static World worldWith(int x, int y, int z, TileEntity tileEntity) {
        World world = Mockito.mock(World.class);
        Mockito.when(world.getTileEntity(x, y, z))
            .thenReturn(tileEntity);
        return world;
    }

    @Test
    public void rejectsANullWorld() {
        Assert.assertFalse(INBTSyncable.applyTo(null, 1, 2, 3, new NBTTagCompound()));
    }

    @Test
    public void rejectsANullPayload() {
        World world = worldWith(1, 2, 3, new SyncedTileEntity());
        Assert.assertFalse(INBTSyncable.applyTo(world, 1, 2, 3, null));
    }

    @Test
    public void rejectsAnEmptyPosition() {
        World world = Mockito.mock(World.class);
        Assert.assertFalse(INBTSyncable.applyTo(world, 1, 2, 3, new NBTTagCompound()));
    }

    /** The exploit: a tile entity that did not opt in must never be written to. */
    @Test
    public void rejectsATileEntityThatDidNotOptIn() {
        PlainTileEntity plain = new PlainTileEntity();
        World world = worldWith(1, 2, 3, plain);

        Assert.assertFalse(INBTSyncable.applyTo(world, 1, 2, 3, new NBTTagCompound()));
    }

    @Test
    public void appliesToAnOptedInTileEntity() {
        SyncedTileEntity synced = new SyncedTileEntity();
        World world = worldWith(1, 2, 3, synced);
        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger("value", 42);

        Assert.assertTrue(INBTSyncable.applyTo(world, 1, 2, 3, payload));
        Assert.assertEquals(1, synced.writes);
        Assert.assertSame(payload, synced.received);
    }

    @Test
    public void applyToOnlyTouchesTheRequestedPosition() {
        SyncedTileEntity synced = new SyncedTileEntity();
        World world = worldWith(1, 2, 3, synced);

        Assert.assertFalse(INBTSyncable.applyTo(world, 9, 9, 9, new NBTTagCompound()));
        Assert.assertEquals(0, synced.writes);
    }
}

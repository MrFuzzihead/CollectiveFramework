package com.austinv11.collectiveframework.minecraft.tiles;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Opt-in contract for tile entities that can be updated by
 * {@link com.austinv11.collectiveframework.minecraft.network.TileEntityServerUpdatePacket} and
 * {@link com.austinv11.collectiveframework.minecraft.network.TileEntityClientUpdatePacket}.
 * <p>
 * The sync packets used to call {@link TileEntity#readFromNBT(NBTTagCompound)} on whatever tile entity
 * happened to sit at the coordinates in the packet. That let any client overwrite the complete NBT of any tile
 * entity - including its {@code x}/{@code y}/{@code z}, which desynchronises the tile entity from its chunk and
 * duplicates or destroys its contents - in any dimension. Requiring this interface means a tile entity only
 * accepts sync data it explicitly opted into (CODE_REVIEW §1.17).
 * <p>
 * Implementations must not read or write position or identity data: {@code xCoord}, {@code yCoord},
 * {@code zCoord} and the registry {@code id} are owned by the world, not by the payload.
 */
public interface INBTSyncable {

    /**
     * Builds the payload to send to the other side. Called on the side that owns the authoritative state.
     *
     * @return The payload; must not be null and must not contain position or identity data
     */
    NBTTagCompound writeSyncData();

    /**
     * Applies a payload produced by {@link #writeSyncData()} on the other side.
     *
     * @param data The payload, never null
     */
    void readSyncData(NBTTagCompound data);

    /**
     * Validates and applies a sync payload to the tile entity at the given position.
     * <p>
     * Deliberately a single shared entry point so both sync packets apply identical validation, and so the
     * behaviour can be tested without a network connection.
     *
     * @param world The world to look in
     * @param x     The x coordinate of the tile entity
     * @param y     The y coordinate of the tile entity
     * @param z     The z coordinate of the tile entity
     * @param data  The payload to apply
     * @return True if the payload was delivered, false if the position holds no opted-in tile entity
     */
    static boolean applyTo(World world, int x, int y, int z, NBTTagCompound data) {
        if (world == null || data == null) return false;
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (!(tileEntity instanceof INBTSyncable)) return false;
        ((INBTSyncable) tileEntity).readSyncData(data);
        return true;
    }

    /**
     * Convenience implementation of {@link #readSyncData(NBTTagCompound)} for tile entities that simply want to
     * round-trip their NBT.
     * <p>
     * {@link TileEntity#readFromNBT(NBTTagCompound)} overwrites {@code xCoord}/{@code yCoord}/{@code zCoord}
     * with whatever the tag says (0 when the keys are absent), which is how a forged payload could relocate a
     * tile entity. This helper restores the position the world gave us.
     *
     * @param tileEntity The tile entity being updated
     * @param data       The payload
     */
    static void readFromNbtKeepingPosition(TileEntity tileEntity, NBTTagCompound data) {
        int x = tileEntity.xCoord;
        int y = tileEntity.yCoord;
        int z = tileEntity.zCoord;
        tileEntity.readFromNBT(data);
        tileEntity.xCoord = x;
        tileEntity.yCoord = y;
        tileEntity.zCoord = z;
    }
}

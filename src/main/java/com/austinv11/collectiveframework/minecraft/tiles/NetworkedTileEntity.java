package com.austinv11.collectiveframework.minecraft.tiles;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Simple Tile Entity implementation which is networked.
 * <p>
 * It also implements {@link INBTSyncable}, so subclasses can be used with
 * {@link com.austinv11.collectiveframework.minecraft.network.TileEntityServerUpdatePacket} and
 * {@link com.austinv11.collectiveframework.minecraft.network.TileEntityClientUpdatePacket} without extra
 * work.
 */
public abstract class NetworkedTileEntity extends TileEntity implements INBTSyncable {

    public NetworkedTileEntity() {
        super();
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 3, tag);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Position and registry id are stripped: they belong to the world, and sending them would let a forged
     * payload relocate this tile entity (CODE_REVIEW §1.17).
     */
    @Override
    public NBTTagCompound writeSyncData() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        tag.removeTag("x");
        tag.removeTag("y");
        tag.removeTag("z");
        tag.removeTag("id");
        return tag;
    }

    @Override
    public void readSyncData(NBTTagCompound data) {
        INBTSyncable.readFromNbtKeepingPosition(this, data);
    }
}

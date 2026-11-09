package com.austinv11.collectiveframework.minecraft.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.austinv11.collectiveframework.minecraft.CollectiveFramework;
import com.austinv11.collectiveframework.minecraft.threading.MainThread;
import com.austinv11.collectiveframework.minecraft.tiles.INBTSyncable;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Use this packet to sync tile entities (client to server).
 * <p>
 * The receiving tile entity must implement {@link INBTSyncable}.
 */
public class TileEntityServerUpdatePacket implements IMessage {

    /**
     * Squared distance (8 blocks) within which a player may update a tile entity. Matches the radius vanilla
     * uses for {@code IInventory#isUseableByPlayer}.
     */
    public static final double MAX_SYNC_DISTANCE_SQ = 64.0D;

    /**
     * The world the sender claims to be in. Advisory only: the server resolves the world from the connection so
     * that a client cannot address tile entities in a world it is not in (CODE_REVIEW §1.17). Null on the
     * receiving side.
     */
    public World world;
    /** Dimension id used on the wire. */
    public int dimensionId;
    public int x, y, z;
    public NBTTagCompound updateData;

    public TileEntityServerUpdatePacket() {}

    public TileEntityServerUpdatePacket(World world, int x, int y, int z, NBTTagCompound updateData) {
        this.world = world;
        this.dimensionId = world == null ? 0 : world.provider.dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.updateData = updateData;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        // ByteBufUtils.readTag reads a length prefix before it can return null, so a truncated payload would
        // throw IndexOutOfBoundsException on the netty thread (CODE_REVIEW §3.2).
        if (buf.readableBytes() < 2) return;
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        if (tag == null) return;
        dimensionId = tag.getInteger("dim");
        x = tag.getInteger("x");
        y = tag.getInteger("y");
        z = tag.getInteger("z");
        updateData = tag.getCompoundTag("tag");
        // The world is resolved by the handler from the sender's connection, not from this id.
        world = null;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("dim", world != null ? world.provider.dimensionId : dimensionId);
        tag.setInteger("x", x);
        tag.setInteger("y", y);
        tag.setInteger("z", z);
        tag.setTag("tag", updateData == null ? new NBTTagCompound() : updateData);
        ByteBufUtils.writeTag(buf, tag);
    }

    /**
     * Whether a player at the given position is close enough to update the tile entity at {@code (x, y, z)}.
     * Kept separate from the handler so it can be tested without a connection.
     *
     * @param player The player
     * @param x      The tile entity x coordinate
     * @param y      The tile entity y coordinate
     * @param z      The tile entity z coordinate
     * @return True if the update is within interaction range
     */
    public static boolean isWithinInteractionRange(EntityPlayer player, int x, int y, int z) {
        if (player == null) return false;
        return player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D) <= MAX_SYNC_DISTANCE_SQ;
    }

    public static class TileEntityServerUpdatePacketHandler
        implements IMessageHandler<TileEntityServerUpdatePacket, IMessage> {

        @Override
        public IMessage onMessage(TileEntityServerUpdatePacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler() == null ? null : ctx.getServerHandler().playerEntity;
            if (player == null || player.worldObj == null) return null;

            // Everything that touches the world runs on the server thread (CODE_REVIEW §3.1): resolving a tile
            // entity can load a chunk, which is not thread safe.
            final int x = message.x;
            final int y = message.y;
            final int z = message.z;
            final NBTTagCompound data = message.updateData;
            MainThread.scheduleOnServer(new Runnable() {

                @Override
                public void run() {
                    // CODE_REVIEW §1.17: the world comes from the connection. Previously the packet supplied the
                    // dimension id, so a client could write NBT into any tile entity in any dimension.
                    if (!isWithinInteractionRange(player, x, y, z)) {
                        CollectiveFramework.LOGGER.warn(
                            "Rejecting tile entity sync from " + player
                                .getCommandSenderName() + ": " + x + "," + y + "," + z + " is out of range");
                        return;
                    }

                    if (!INBTSyncable.applyTo(player.worldObj, x, y, z, data)) {
                        CollectiveFramework.LOGGER.warn(
                            "Rejecting tile entity sync from " + player
                                .getCommandSenderName() + ": no INBTSyncable tile entity at " + x + "," + y + "," + z);
                    }
                }
            });
            return null;
        }
    }
}

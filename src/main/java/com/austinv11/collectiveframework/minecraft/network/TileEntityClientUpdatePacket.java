package com.austinv11.collectiveframework.minecraft.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.austinv11.collectiveframework.minecraft.CollectiveFramework;
import com.austinv11.collectiveframework.minecraft.threading.MainThread;
import com.austinv11.collectiveframework.minecraft.tiles.INBTSyncable;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Use this packet to sync tile entities (server to client).
 * <p>
 * The receiving tile entity must implement {@link INBTSyncable}.
 */
public class TileEntityClientUpdatePacket implements IMessage {

    /**
     * The world to update. Null until {@link TileEntityClientUpdatePacketHandler} resolves it, because the
     * receiving side has no {@code MinecraftServer} to look a dimension id up in.
     */
    public World world;
    /** Dimension id used on the wire. */
    public int dimensionId;
    public int x, y, z;
    public NBTTagCompound updateData;

    public TileEntityClientUpdatePacket() {}

    public TileEntityClientUpdatePacket(World world, int x, int y, int z, NBTTagCompound updateData) {
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
        // CODE_REVIEW §1.7: this used to call WorldUtils.getWorldFromDimensionId(...), which dereferences
        // MinecraftServer.getServer(). That is always null on a physical client, so decoding this packet threw
        // on every client. The world is resolved by the handler instead.
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
     * Whether a server-pushed update produced in {@code packetDimensionId} applies to a client that is
     * currently in {@code clientDimensionId}.
     *
     * @param clientDimensionId The dimension the client has loaded
     * @param packetDimensionId The dimension the update was produced in
     * @return True if the update applies to the client's world
     */
    public static boolean isDimensionMatch(int clientDimensionId, int packetDimensionId) {
        return clientDimensionId == packetDimensionId;
    }

    /**
     * Resolves the world a server-pushed update applies to, on the client.
     *
     * @param clientWorld The world the client currently has loaded
     * @param dimensionId The dimension the update was produced in
     * @return The world to apply the update to, or null if it does not apply here
     */
    @SideOnly(Side.CLIENT)
    public static World resolveClientWorld(World clientWorld, int dimensionId) {
        if (clientWorld == null || clientWorld.provider == null) return null;
        return isDimensionMatch(clientWorld.provider.dimensionId, dimensionId) ? clientWorld : null;
    }

    public static class TileEntityClientUpdatePacketHandler
        implements IMessageHandler<TileEntityClientUpdatePacket, IMessage> {

        @SideOnly(Side.CLIENT)
        @Override
        public IMessage onMessage(TileEntityClientUpdatePacket message, MessageContext ctx) {
            // Chunk and tile entity access belongs on the client thread (CODE_REVIEW §3.1).
            final int dimensionId = message.dimensionId;
            final int x = message.x;
            final int y = message.y;
            final int z = message.z;
            final NBTTagCompound data = message.updateData;
            MainThread.scheduleOnClient(new Runnable() {

                @Override
                public void run() {
                    World world = resolveClientWorld(Minecraft.getMinecraft().theWorld, dimensionId);
                    if (world == null) return;

                    if (!INBTSyncable.applyTo(world, x, y, z, data)) {
                        CollectiveFramework.LOGGER
                            .warn("Ignoring tile entity sync: no INBTSyncable tile entity at " + x + "," + y + "," + z);
                    }
                }
            });
            return null;
        }
    }
}

package com.austinv11.collectiveframework.minecraft.network;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * CODE_REVIEW §1.7 - {@code fromBytes} used to call {@code WorldUtils.getWorldFromDimensionId(...)}, which
 * dereferences {@code MinecraftServer.getServer()}. That is always null on a physical client, so decoding this
 * packet threw on every client. The dimension is now carried on the wire and resolved by the handler against
 * the client's loaded world.
 */
public class TileEntityClientUpdatePacketTest {

    @Test
    public void roundTripsThroughTheBuffer() {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("name", "hello");

        TileEntityClientUpdatePacket out = new TileEntityClientUpdatePacket(null, 4, 5, 6, payload);
        out.dimensionId = 7;

        ByteBuf buf = Unpooled.buffer();
        out.toBytes(buf);

        TileEntityClientUpdatePacket in = new TileEntityClientUpdatePacket();
        in.fromBytes(buf);

        Assert.assertEquals(4, in.x);
        Assert.assertEquals(5, in.y);
        Assert.assertEquals(6, in.z);
        Assert.assertEquals(7, in.dimensionId);
        Assert.assertEquals("hello", in.updateData.getString("name"));
        // Decoding must not touch MinecraftServer.getServer().
        Assert.assertNull(in.world);
    }

    @Test
    public void fromBytesToleratesAnEmptyBuffer() {
        TileEntityClientUpdatePacket in = new TileEntityClientUpdatePacket();
        in.fromBytes(Unpooled.buffer());
        Assert.assertNull(in.updateData);
    }

    @Test
    public void fromBytesToleratesANullTag() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeShort(-1);

        TileEntityClientUpdatePacket in = new TileEntityClientUpdatePacket();
        in.fromBytes(buf);
        Assert.assertNull(in.updateData);
    }

    // ------------------------------------------------------------------------------ world resolution

    @Test
    public void dimensionMatchAcceptsTheSameDimension() {
        Assert.assertTrue(TileEntityClientUpdatePacket.isDimensionMatch(0, 0));
        Assert.assertTrue(TileEntityClientUpdatePacket.isDimensionMatch(-1, -1));
    }

    @Test
    public void dimensionMatchRejectsADifferentDimension() {
        Assert.assertFalse(TileEntityClientUpdatePacket.isDimensionMatch(0, -1));
    }

    @Test
    public void rejectsAMissingWorld() {
        Assert.assertNull(TileEntityClientUpdatePacket.resolveClientWorld(null, 0));
    }

    /**
     * A mock {@link World} has a null {@code provider} field, which is exactly the "client world is not ready
     * yet" case: the update must be dropped rather than NPE.
     */
    @Test
    public void rejectsAWorldWithoutAProvider() {
        World world = Mockito.mock(World.class);
        Assert.assertNull(TileEntityClientUpdatePacket.resolveClientWorld(world, 0));
    }
}

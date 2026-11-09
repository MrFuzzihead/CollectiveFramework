package com.austinv11.collectiveframework.minecraft.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * CODE_REVIEW §1.17 - the server used to take the world from the packet's dimension id and write arbitrary NBT
 * into whatever tile entity was at the given coordinates. The world now comes from the connection and the write
 * is gated on {@link com.austinv11.collectiveframework.minecraft.tiles.INBTSyncable}; this covers the range
 * check and the wire format.
 */
public class TileEntityServerUpdatePacketTest {

    // --------------------------------------------------------------------------------- range check

    @Test
    public void acceptsAPlayerStandingOnTheTileEntity() {
        EntityPlayer player = playerAt(10.5d, 64d, 10.5d);
        Assert.assertTrue(TileEntityServerUpdatePacket.isWithinInteractionRange(player, 10, 64, 10));
    }

    @Test
    public void acceptsAPlayerWithinEightBlocks() {
        EntityPlayer player = playerAt(10.5d, 64d, 10.5d);
        Assert.assertTrue(TileEntityServerUpdatePacket.isWithinInteractionRange(player, 17, 64, 10));
    }

    @Test
    public void rejectsAPlayerJustOutsideEightBlocks() {
        EntityPlayer player = playerAt(10.5d, 64d, 10.5d);
        Assert.assertFalse(TileEntityServerUpdatePacket.isWithinInteractionRange(player, 19, 64, 10));
    }

    @Test
    public void rejectsAPlayerInAnotherChunkFarAway() {
        EntityPlayer player = playerAt(0d, 64d, 0d);
        Assert.assertFalse(TileEntityServerUpdatePacket.isWithinInteractionRange(player, 1000, 64, 1000));
    }

    @Test
    public void rejectsANullPlayer() {
        Assert.assertFalse(TileEntityServerUpdatePacket.isWithinInteractionRange(null, 0, 0, 0));
    }

    private static EntityPlayer playerAt(double x, double y, double z) {
        EntityPlayer player = Mockito.mock(EntityPlayer.class);
        Mockito.when(player.getDistanceSq(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
            .thenAnswer(invocation -> {
                double dx = x - (Double) invocation.getArguments()[0];
                double dy = y - (Double) invocation.getArguments()[1];
                double dz = z - (Double) invocation.getArguments()[2];
                return dx * dx + dy * dy + dz * dz;
            });
        return player;
    }

    // ----------------------------------------------------------------------------------- wire format

    @Test
    public void roundTripsThroughTheBuffer() {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger("value", 1234);

        TileEntityServerUpdatePacket out = new TileEntityServerUpdatePacket(null, 1, 2, 3, payload);
        out.dimensionId = -1;

        ByteBuf buf = Unpooled.buffer();
        out.toBytes(buf);

        TileEntityServerUpdatePacket in = new TileEntityServerUpdatePacket();
        in.fromBytes(buf);

        Assert.assertEquals(1, in.x);
        Assert.assertEquals(2, in.y);
        Assert.assertEquals(3, in.z);
        Assert.assertEquals(-1, in.dimensionId);
        Assert.assertEquals(1234, in.updateData.getInteger("value"));
        // The receiving side must not trust a world resolved from the wire.
        Assert.assertNull(in.world);
    }

    @Test
    public void toBytesToleratesAMissingPayload() {
        TileEntityServerUpdatePacket out = new TileEntityServerUpdatePacket(null, 0, 0, 0, null);
        ByteBuf buf = Unpooled.buffer();
        out.toBytes(buf);

        TileEntityServerUpdatePacket in = new TileEntityServerUpdatePacket();
        in.fromBytes(buf);
        Assert.assertNotNull(in.updateData);
        Assert.assertEquals(0, in.updateData.getInteger("value"));
    }

    /**
     * A truncated payload must be dropped, not thrown. {@code ByteBufUtils.readTag} reads a length prefix before
     * it can return null, so the guard has to check the readable byte count first (CODE_REVIEW §3.2).
     */
    @Test
    public void fromBytesToleratesAnEmptyBuffer() {
        TileEntityServerUpdatePacket in = new TileEntityServerUpdatePacket();
        in.fromBytes(Unpooled.buffer());
        Assert.assertNull(in.updateData);
    }

    @Test
    public void fromBytesToleratesANullTag() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeShort(-1);

        TileEntityServerUpdatePacket in = new TileEntityServerUpdatePacket();
        in.fromBytes(buf);
        Assert.assertNull(in.updateData);
    }
}

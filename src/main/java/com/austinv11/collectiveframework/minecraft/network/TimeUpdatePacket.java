package com.austinv11.collectiveframework.minecraft.network;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import com.austinv11.collectiveframework.minecraft.CollectiveFramework;
import com.austinv11.collectiveframework.minecraft.reference.Config;
import com.austinv11.collectiveframework.minecraft.threading.MainThread;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client to server request to scrub the world time, sent when the player releases the time-change keys.
 */
public class TimeUpdatePacket implements IMessage {

    /**
     * Largest jump the server will accept, in ticks. One in-game day is 24,000 ticks; this allows roughly 48
     * minutes of holding the keys at the default {@code timeChangeRate} of 30 before the request is rejected.
     * The bound is a sanity check, not a privilege check - authorisation comes from the op list.
     */
    public static final long MAX_TIME_CHANGE_TICKS = 20L * 60L * 60L * 24L;

    public long startTime;
    public int time;
    /**
     * The sender's profile, as claimed by the client.
     * <p>
     * Informational only. The server must never authorise a request from this field: it is attacker-controlled,
     * so any client could claim to be an operator (CODE_REVIEW §1.16). The handler authorises against
     * {@link MessageContext#getServerHandler()} instead.
     */
    public GameProfile profile;

    public TimeUpdatePacket() {}

    public TimeUpdatePacket(long startTime, int time, GameProfile profile) {
        this.startTime = startTime;
        this.time = time;
        this.profile = profile;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        // ByteBufUtils.readTag reads a length prefix before it can return null, so a truncated payload would
        // throw IndexOutOfBoundsException on the netty thread (CODE_REVIEW §3.2).
        if (buf.readableBytes() < 2) return;
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        if (tag == null) return;
        startTime = tag.getLong("startTime");
        time = tag.getInteger("time");
        // A malformed UUID used to throw IllegalArgumentException on the netty thread.
        String uuid = tag.getString("uuid");
        if (!uuid.isEmpty()) {
            try {
                profile = new GameProfile(UUID.fromString(uuid), tag.getString("name"));
            } catch (IllegalArgumentException e) {
                CollectiveFramework.LOGGER.warn("Ignoring time update with a malformed profile: " + uuid);
                profile = null;
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("startTime", startTime);
        tag.setInteger("time", time);
        tag.setString(
            "uuid",
            profile == null || profile.getId() == null ? ""
                : profile.getId()
                    .toString());
        tag.setString("name", profile == null ? "" : String.valueOf(profile.getName()));
        ByteBufUtils.writeTag(buf, tag);
    }

    /**
     * Works out the absolute world time a request is asking for.
     * <p>
     * Kept separate from the handler so the validation can be tested without a connection.
     *
     * @param currentTime The world's current time
     * @param startTime   The world time when the player started holding the key
     * @param timeChange  The accumulated change requested
     * @return The requested absolute time, or {@code null} if the request must be rejected
     */
    public static Long resolveRequestedTime(long currentTime, long startTime, int timeChange) {
        long requested;
        try {
            requested = Math.addExact(startTime, (long) timeChange);
        } catch (ArithmeticException e) {
            return null;
        }
        if (requested < 0) return null;
        // Both operands are non-negative here, so the difference cannot overflow.
        if (Math.abs(requested - currentTime) > MAX_TIME_CHANGE_TICKS) return null;
        return requested;
    }

    public static class TimeUpdatePacketHandler implements IMessageHandler<TimeUpdatePacket, IMessage> {

        @Override
        public IMessage onMessage(TimeUpdatePacket message, MessageContext ctx) {
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null || !Config.enableButtonTimeChanging) return null;

            // Authorise against the connection, never against message.profile (CODE_REVIEW §1.16).
            EntityPlayerMP player = ctx.getServerHandler() == null ? null : ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            if (!server.getConfigurationManager()
                .func_152596_g(player.getGameProfile())) {
                CollectiveFramework.LOGGER
                    .warn("Rejecting time update from " + player.getCommandSenderName() + ": not an operator");
                return null;
            }

            // World access happens on the server thread (CODE_REVIEW §3.1). Only the sender's own world is
            // touched; this used to iterate every loaded world.
            final long startTime = message.startTime;
            final int timeChange = message.time;
            MainThread.scheduleOnServer(new Runnable() {

                @Override
                public void run() {
                    if (!(player.worldObj instanceof WorldServer)) return;
                    WorldServer world = (WorldServer) player.worldObj;

                    Long requested = resolveRequestedTime(world.getWorldTime(), startTime, timeChange);
                    if (requested == null) {
                        CollectiveFramework.LOGGER.warn(
                            "Rejecting time update from " + player.getCommandSenderName() + ": request out of range");
                        return;
                    }
                    world.setWorldTime(requested);
                }
            });
            return null;
        }
    }
}

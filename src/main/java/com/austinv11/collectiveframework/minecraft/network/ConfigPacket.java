package com.austinv11.collectiveframework.minecraft.network;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.MinecraftForge;

import com.austinv11.collectiveframework.minecraft.config.ConfigLoadEvent;
import com.austinv11.collectiveframework.minecraft.config.ConfigRegistry;
import com.austinv11.collectiveframework.minecraft.threading.MainThread;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class ConfigPacket implements IMessage {

    public String configName;
    public String config;
    public boolean isRevert;

    public ConfigPacket() {

    }

    public ConfigPacket(String configName, String config, boolean isRevert) {
        this.configName = configName;
        this.config = config;
        this.isRevert = isRevert;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        configName = tag.getString("configName");
        config = tag.getString("config");
        isRevert = tag.getBoolean("isRevert");
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("configName", configName);
        tag.setString("config", config);
        tag.setBoolean("isRevert", isRevert);
        ByteBufUtils.writeTag(buf, tag);
    }

    public static class ConfigPacketHandler implements IMessageHandler<ConfigPacket, IMessage> {

        @Override
        public IMessage onMessage(ConfigPacket message, MessageContext ctx) {
            // Config reloads mutate static fields and touch the config files, so they belong on the client
            // thread rather than the netty event loop (CODE_REVIEW §3.1).
            final String configName = message.configName;
            final String config = message.config;
            final boolean isRevert = message.isRevert;
            MainThread.scheduleOnClient(new Runnable() {

                @Override
                public void run() {
                    ConfigLoadEvent.Pre event = new ConfigLoadEvent.Pre();
                    event.configName = configName;
                    event.config = config;
                    event.isRevert = isRevert;
                    if (!MinecraftForge.EVENT_BUS.post(event)) {
                        ConfigRegistry.onConfigReload(event);
                        ConfigLoadEvent.Post newEvent = new ConfigLoadEvent.Post();
                        newEvent.configName = event.configName;
                        newEvent.config = event.config;
                        newEvent.isRevert = event.isRevert;
                        MinecraftForge.EVENT_BUS.post(newEvent);
                    }
                }
            });
            return null;
        }
    }
}

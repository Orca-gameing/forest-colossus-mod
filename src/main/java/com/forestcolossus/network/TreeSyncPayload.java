package com.forestcolossus.network;

import com.forestcolossus.ForestColossusMod;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TreeSyncPayload(NbtCompound data) implements CustomPayload {
    public static final Id<TreeSyncPayload> ID = new Id<>(Identifier.of(ForestColossusMod.MOD_ID, "tree_sync"));
    
    public static final PacketCodec<RegistryByteBuf, TreeSyncPayload> CODEC = PacketCodec.tuple(
        net.minecraft.network.codec.PacketCodecs.NBT_COMPOUND,
        TreeSyncPayload::data,
        TreeSyncPayload::new
    );
    
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
    
    public static void registerServer() {
        // Register the payload type for S2C (server to client)
        // This must only be called once, on the server/common side
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(ID, CODEC);
        ForestColossusMod.LOGGER.info("TreeSyncPayload registered!");
    }
}

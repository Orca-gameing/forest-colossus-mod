package com.forestcolossus.network;

import com.forestcolossus.ForestColossusMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Manages tree holding state for players (for testing the tree pickup mechanic)
 */
public class PlayerTreeHolder {
    
    // Server-side storage of held trees per player
    private static final Map<UUID, TreePickupPayload.TreeStructure> heldTrees = new HashMap<>();
    private static final Map<UUID, BlockState> heldTreeTypes = new HashMap<>();
    
    public static boolean isHoldingTree(PlayerEntity player) {
        return heldTrees.containsKey(player.getUuid());
    }
    
    public static void setHeldTree(ServerPlayerEntity player, TreePickupPayload.TreeStructure structure, BlockState logType) {
        heldTrees.put(player.getUuid(), structure);
        heldTreeTypes.put(player.getUuid(), logType);
        
        // Sync to client
        syncToClient(player);
    }
    
    public static TreePickupPayload.TreeStructure getHeldTree(PlayerEntity player) {
        return heldTrees.get(player.getUuid());
    }
    
    public static BlockState getHeldTreeType(PlayerEntity player) {
        return heldTreeTypes.getOrDefault(player.getUuid(), Blocks.OAK_LOG.getDefaultState());
    }
    
    public static void dropTree(ServerPlayerEntity player, ServerWorld world) {
        TreePickupPayload.TreeStructure structure = heldTrees.remove(player.getUuid());
        BlockState logType = heldTreeTypes.remove(player.getUuid());
        
        if (structure == null) return;
        
        // Place tree at player's position
        BlockPos basePos = player.getBlockPos().add(2, 0, 0); // Offset so it doesn't spawn inside player
        
        BlockState logState = logType != null ? logType : Blocks.OAK_LOG.getDefaultState();
        BlockState leafState = getMatchingLeaves(logState);
        
        // Place logs
        for (int[] pos : structure.logPositions) {
            BlockPos blockPos = basePos.add(pos[0], pos[1], pos[2]);
            if (world.getBlockState(blockPos).isAir() || world.getBlockState(blockPos).isReplaceable()) {
                world.setBlockState(blockPos, logState);
            }
        }
        
        // Place leaves
        for (int[] pos : structure.leafPositions) {
            BlockPos blockPos = basePos.add(pos[0], pos[1], pos[2]);
            if (world.getBlockState(blockPos).isAir() || world.getBlockState(blockPos).isReplaceable()) {
                world.setBlockState(blockPos, leafState);
            }
        }
        
        ForestColossusMod.LOGGER.info("Dropped tree at " + basePos);
        
        // Sync to client
        syncToClient(player);
    }
    
    private static BlockState getMatchingLeaves(BlockState logBlock) {
        if (logBlock == null) return Blocks.OAK_LEAVES.getDefaultState();
        
        String blockName = logBlock.getBlock().getTranslationKey().toLowerCase();
        
        if (blockName.contains("dark_oak")) {
            return Blocks.DARK_OAK_LEAVES.getDefaultState();
        } else if (blockName.contains("oak")) {
            return Blocks.OAK_LEAVES.getDefaultState();
        } else if (blockName.contains("spruce")) {
            return Blocks.SPRUCE_LEAVES.getDefaultState();
        } else if (blockName.contains("birch")) {
            return Blocks.BIRCH_LEAVES.getDefaultState();
        } else if (blockName.contains("jungle")) {
            return Blocks.JUNGLE_LEAVES.getDefaultState();
        } else if (blockName.contains("acacia")) {
            return Blocks.ACACIA_LEAVES.getDefaultState();
        } else if (blockName.contains("mangrove")) {
            return Blocks.MANGROVE_LEAVES.getDefaultState();
        } else if (blockName.contains("cherry")) {
            return Blocks.CHERRY_LEAVES.getDefaultState();
        }
        
        return Blocks.OAK_LEAVES.getDefaultState();
    }
    
    private static void syncToClient(ServerPlayerEntity player) {
        // Send sync packet to client
        TreePickupPayload.TreeStructure structure = heldTrees.get(player.getUuid());
        BlockState logType = heldTreeTypes.get(player.getUuid());
        
        NbtCompound nbt = new NbtCompound();
        
        if (structure != null) {
            nbt.putBoolean("hasTree", true);
            nbt.putString("woodType", Registries.BLOCK.getId(logType.getBlock()).toString());
            
            NbtList logsList = new NbtList();
            for (int[] pos : structure.logPositions) {
                NbtCompound posNbt = new NbtCompound();
                posNbt.putInt("x", pos[0]);
                posNbt.putInt("y", pos[1]);
                posNbt.putInt("z", pos[2]);
                logsList.add(posNbt);
            }
            nbt.put("logs", logsList);
            
            NbtList leavesList = new NbtList();
            for (int[] pos : structure.leafPositions) {
                NbtCompound posNbt = new NbtCompound();
                posNbt.putInt("x", pos[0]);
                posNbt.putInt("y", pos[1]);
                posNbt.putInt("z", pos[2]);
                leavesList.add(posNbt);
            }
            nbt.put("leaves", leavesList);
        } else {
            nbt.putBoolean("hasTree", false);
        }
        
        // Send via custom payload
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new TreeSyncPayload(nbt));
    }
}

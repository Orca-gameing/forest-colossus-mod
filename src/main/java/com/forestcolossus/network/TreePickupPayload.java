package com.forestcolossus.network;

import com.forestcolossus.ForestColossusMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class TreePickupPayload implements CustomPayload {
    public static final Id<TreePickupPayload> ID = new Id<>(Identifier.of(ForestColossusMod.MOD_ID, "tree_pickup"));
    
    // Proper codec that can encode/decode multiple instances
    public static final PacketCodec<RegistryByteBuf, TreePickupPayload> CODEC = new PacketCodec<>() {
        @Override
        public TreePickupPayload decode(RegistryByteBuf buf) {
            return new TreePickupPayload();
        }

        @Override
        public void encode(RegistryByteBuf buf, TreePickupPayload value) {
            // No data to encode - this is just a signal packet
        }
    };
    
    public TreePickupPayload() {}
    
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
    
    public static void register() {
        // Register the payload type
        PayloadTypeRegistry.playC2S().register(ID, CODEC);
        
        // Register the server receiver
        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            ServerWorld world = player.getServerWorld();
            
            context.server().execute(() -> {
                handleTreePickup(player, world);
            });
        });
        
        ForestColossusMod.LOGGER.info("TreePickupPayload registered!");
    }
    
    private static void handleTreePickup(ServerPlayerEntity player, ServerWorld world) {
        // Check if player is already holding a tree
        if (PlayerTreeHolder.isHoldingTree(player)) {
            // Drop the tree at current position
            PlayerTreeHolder.dropTree(player, world);
            return;
        }
        
        // Raycast to find what block player is looking at
        HitResult hitResult = player.raycast(5.0, 0, false);
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            ForestColossusMod.LOGGER.info("Player not looking at a block");
            return;
        }
        
        BlockHitResult blockHit = (BlockHitResult) hitResult;
        BlockPos hitPos = blockHit.getBlockPos();
        BlockState hitState = world.getBlockState(hitPos);
        
        if (!hitState.isIn(BlockTags.LOGS)) {
            ForestColossusMod.LOGGER.info("Not a log block: " + hitState.getBlock().getTranslationKey());
            return;
        }
        
        // Find tree base
        BlockPos treeBase = findTreeBase(world, hitPos);
        if (treeBase == null) {
            ForestColossusMod.LOGGER.info("Could not find tree base");
            return;
        }
        
        // Capture tree structure
        TreeStructure structure = captureTreeStructure(world, treeBase);
        if (structure.logPositions.isEmpty()) {
            ForestColossusMod.LOGGER.info("No logs found in tree");
            return;
        }
        
        ForestColossusMod.LOGGER.info("Captured tree with " + structure.logPositions.size() + " logs and " + structure.leafPositions.size() + " leaves");
        
        // Remove tree from world
        removeTreeFromWorld(world, treeBase, structure);
        
        // Store in player data
        PlayerTreeHolder.setHeldTree(player, structure, hitState);
        
        ForestColossusMod.LOGGER.info("Player picked up tree!");
    }
    
    private static BlockPos findTreeBase(ServerWorld world, BlockPos logPos) {
        BlockPos current = logPos;
        
        while (current.getY() > world.getBottomY()) {
            BlockPos below = current.down();
            BlockState belowState = world.getBlockState(below);
            
            if (belowState.isIn(BlockTags.LOGS)) {
                current = below;
            } else {
                break;
            }
        }
        
        return current;
    }
    
    private static TreeStructure captureTreeStructure(ServerWorld world, BlockPos basePos) {
        TreeStructure structure = new TreeStructure();
        
        Set<BlockPos> visitedLogs = new HashSet<>();
        Set<BlockPos> visitedLeaves = new HashSet<>();
        
        // Flood fill logs
        floodFillLogs(world, basePos, basePos, visitedLogs, 0);
        
        // Find leaves connected to logs
        for (BlockPos logPos : visitedLogs) {
            for (BlockPos neighbor : getNeighbors26(logPos)) {
                if (!visitedLogs.contains(neighbor) && !visitedLeaves.contains(neighbor)) {
                    BlockState state = world.getBlockState(neighbor);
                    if (state.isIn(BlockTags.LEAVES)) {
                        floodFillLeaves(world, neighbor, basePos, visitedLogs, visitedLeaves, 0);
                    }
                }
            }
        }
        
        // Convert to relative positions
        for (BlockPos pos : visitedLogs) {
            structure.logPositions.add(new int[]{
                pos.getX() - basePos.getX(),
                pos.getY() - basePos.getY(),
                pos.getZ() - basePos.getZ()
            });
        }
        
        for (BlockPos pos : visitedLeaves) {
            structure.leafPositions.add(new int[]{
                pos.getX() - basePos.getX(),
                pos.getY() - basePos.getY(),
                pos.getZ() - basePos.getZ()
            });
        }
        
        return structure;
    }
    
    private static void floodFillLogs(ServerWorld world, BlockPos current, BlockPos basePos, Set<BlockPos> visited, int depth) {
        if (depth > 100) return;
        if (visited.contains(current)) return;
        
        int dx = Math.abs(current.getX() - basePos.getX());
        int dz = Math.abs(current.getZ() - basePos.getZ());
        if (dx > 3 || dz > 3) return;
        
        int dy = current.getY() - basePos.getY();
        if (dy < -1 || dy > 25) return;
        
        BlockState state = world.getBlockState(current);
        if (!state.isIn(BlockTags.LOGS)) return;
        
        visited.add(current);
        
        for (BlockPos neighbor : getNeighbors6(current)) {
            floodFillLogs(world, neighbor, basePos, visited, depth + 1);
        }
    }
    
    private static void floodFillLeaves(ServerWorld world, BlockPos current, BlockPos basePos,
                                        Set<BlockPos> logs, Set<BlockPos> visitedLeaves, int depth) {
        // Limit leaf spread to only 4 blocks from any log (prevents grabbing neighboring trees)
        if (depth > 4) return;
        if (visitedLeaves.contains(current)) return;
        if (logs.contains(current)) return;
        
        // Tighter horizontal bounds - leaves shouldn't spread too far from trunk
        int dx = Math.abs(current.getX() - basePos.getX());
        int dz = Math.abs(current.getZ() - basePos.getZ());
        if (dx > 4 || dz > 4) return;
        
        int dy = current.getY() - basePos.getY();
        if (dy < 0 || dy > 20) return;
        
        BlockState state = world.getBlockState(current);
        if (!state.isIn(BlockTags.LEAVES)) return;
        
        visitedLeaves.add(current);
        
        for (BlockPos neighbor : getNeighbors6(current)) {
            floodFillLeaves(world, neighbor, basePos, logs, visitedLeaves, depth + 1);
        }
    }
    
    private static List<BlockPos> getNeighbors6(BlockPos pos) {
        return Arrays.asList(
            pos.up(), pos.down(),
            pos.north(), pos.south(),
            pos.east(), pos.west()
        );
    }
    
    private static List<BlockPos> getNeighbors26(BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    neighbors.add(pos.add(dx, dy, dz));
                }
            }
        }
        return neighbors;
    }
    
    private static void removeTreeFromWorld(ServerWorld world, BlockPos basePos, TreeStructure structure) {
        // Remove logs
        for (int[] pos : structure.logPositions) {
            BlockPos blockPos = basePos.add(pos[0], pos[1], pos[2]);
            world.setBlockState(blockPos, Blocks.AIR.getDefaultState());
        }
        
        // Remove leaves
        for (int[] pos : structure.leafPositions) {
            BlockPos blockPos = basePos.add(pos[0], pos[1], pos[2]);
            world.setBlockState(blockPos, Blocks.AIR.getDefaultState());
        }
    }
    
    public static class TreeStructure {
        public List<int[]> logPositions = new ArrayList<>();
        public List<int[]> leafPositions = new ArrayList<>();
        public String woodType = "oak";
    }
}

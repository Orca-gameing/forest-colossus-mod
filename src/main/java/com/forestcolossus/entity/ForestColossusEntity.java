package com.forestcolossus.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.*;

public class ForestColossusEntity extends HostileEntity implements GeoEntity {
    
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    
    // Synced data - these sync between server and client
    private static final TrackedData<Integer> ANIMATION_STATE = DataTracker.registerData(ForestColossusEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> HAS_TREE = DataTracker.registerData(ForestColossusEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> IS_MOVING = DataTracker.registerData(ForestColossusEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<String> HELD_TREE_TYPE = DataTracker.registerData(ForestColossusEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> HELD_LEAF_TYPE = DataTracker.registerData(ForestColossusEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<NbtCompound> TREE_STRUCTURE = DataTracker.registerData(ForestColossusEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    
    // Client-side cached tree structure
    private List<int[]> cachedLogPositions = new ArrayList<>();
    private List<int[]> cachedLeafPositions = new ArrayList<>();
    
    public static final int ANIM_IDLE = 0;
    public static final int ANIM_WALK = 1;
    public static final int ANIM_SLAM = 2;
    public static final int ANIM_GRAB = 3;
    public static final int ANIM_THROW = 4;
    
    private ServerBossBar bossBar;
    
    public static final float BASE_HEALTH = 500f;
    public static final float SLAM_DAMAGE = 8f;  // Base damage, will also do % health
    public static final float THROW_DAMAGE = 4f; // Base damage, will also do % health (20% of max health)
    
    // Server-side only
    private int attackCooldown = 0;
    private int animationTicks = 0;
    private boolean slamExecuted = false;
    private boolean throwExecuted = false;
    private LivingEntity throwTarget = null;
    
    public ForestColossusEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        if (!world.isClient) {
            this.bossBar = new ServerBossBar(
                Text.literal("Forest Colossus"),
                BossBar.Color.GREEN,
                BossBar.Style.NOTCHED_10
            );
        }
        this.experiencePoints = 500;
    }
    
    public static DefaultAttributeContainer.Builder createColossusAttributes() {
        return MobEntity.createMobAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH, BASE_HEALTH)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35)
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 25.0)
            .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
            .add(EntityAttributes.GENERIC_ARMOR, 8.0)
            .add(EntityAttributes.GENERIC_STEP_HEIGHT, 2.0);
    }
    
    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(ANIMATION_STATE, ANIM_IDLE);
        builder.add(HAS_TREE, false);
        builder.add(IS_MOVING, false);
        builder.add(HELD_TREE_TYPE, "minecraft:oak_log");
        builder.add(HELD_LEAF_TYPE, "minecraft:oak_leaves");
        builder.add(TREE_STRUCTURE, new NbtCompound());
    }
    
    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new ColossusAttackGoal(this));
        this.goalSelector.add(2, new WanderAroundFarGoal(this, 0.5));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 48.0f));
        this.goalSelector.add(4, new LookAroundGoal(this));
        
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        this.targetSelector.add(2, new RevengeGoal(this));
    }
    
    // GeckoLib animation controller
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Single controller that handles all animations
        controllers.add(new AnimationController<>(this, "controller", 5, this::animationPredicate));
    }
    
    private PlayState animationPredicate(AnimationState<ForestColossusEntity> state) {
        // Check attack animations first (highest priority)
        int animState = this.dataTracker.get(ANIMATION_STATE);
        
        if (animState == ANIM_SLAM) {
            return state.setAndContinue(RawAnimation.begin().thenPlay("animation.forest_colossus.slam"));
        } else if (animState == ANIM_GRAB) {
            return state.setAndContinue(RawAnimation.begin().thenPlay("animation.forest_colossus.grab"));
        } else if (animState == ANIM_THROW) {
            return state.setAndContinue(RawAnimation.begin().thenPlay("animation.forest_colossus.throw"));
        }
        
        // Check movement - use synced data tracker value
        boolean moving = this.dataTracker.get(IS_MOVING);
        if (moving) {
            return state.setAndContinue(RawAnimation.begin().thenLoop("animation.forest_colossus.walk"));
        }
        
        // Default to idle
        return state.setAndContinue(RawAnimation.begin().thenLoop("animation.forest_colossus.idle"));
    }
    
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
    
    @Override
    public void tick() {
        super.tick();
        
        // Both client and server: check if moving
        double dx = this.getX() - this.prevX;
        double dz = this.getZ() - this.prevZ;
        boolean isCurrentlyMoving = (dx * dx + dz * dz) > 0.0001;
        
        // Server-side logic
        if (!this.getWorld().isClient) {
            // Update movement state (syncs to client)
            this.dataTracker.set(IS_MOVING, isCurrentlyMoving);
            
            if (attackCooldown > 0) attackCooldown--;
            if (animationTicks > 0) animationTicks--;
            
            int animState = getAnimationState();
            
            // Execute slam at the right moment
            if (animState == ANIM_SLAM && !slamExecuted && animationTicks <= 30 && animationTicks > 25) {
                executeSlam();
                slamExecuted = true;
            }
            
            // Execute throw at the right moment
            if (animState == ANIM_THROW && !throwExecuted && animationTicks <= 20 && animationTicks > 15) {
                executeThrow();
                throwExecuted = true;
            }
            
            // Reset to idle when animation ends
            if (animationTicks <= 0 && animState != ANIM_IDLE && animState != ANIM_WALK) {
                setAnimationState(ANIM_IDLE);
                slamExecuted = false;
                throwExecuted = false;
                throwTarget = null;
            }
            
            // Boss bar
            if (bossBar != null) {
                bossBar.setPercent(this.getHealth() / this.getMaxHealth());
            }
            
            // Footstep effects
            if (this.age % 15 == 0 && isCurrentlyMoving) {
                playFootstepEffects();
            }
        }
    }
    
    public void setAnimationState(int state) {
        this.dataTracker.set(ANIMATION_STATE, state);
    }
    
    public int getAnimationState() {
        return this.dataTracker.get(ANIMATION_STATE);
    }
    
    public boolean hasTree() {
        return this.dataTracker.get(HAS_TREE);
    }
    
    public void setHasTree(boolean hasTree) {
        this.dataTracker.set(HAS_TREE, hasTree);
        if (!hasTree) {
            // Clear tree data when dropping tree
            this.dataTracker.set(TREE_STRUCTURE, new NbtCompound());
            this.dataTracker.set(HELD_TREE_TYPE, "minecraft:oak_log");
            this.dataTracker.set(HELD_LEAF_TYPE, "minecraft:oak_leaves");
            this.cachedLogPositions.clear();
            this.cachedLeafPositions.clear();
        }
    }
    
    public BlockState getHeldTreeBlock() {
        // Get from synced data tracker for client-side rendering
        String blockId = this.dataTracker.get(HELD_TREE_TYPE);
        try {
            net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(blockId);
            if (id != null) {
                net.minecraft.block.Block block = net.minecraft.registry.Registries.BLOCK.get(id);
                if (block != Blocks.AIR) {
                    return block.getDefaultState();
                }
            }
        } catch (Exception e) {
            // Fallback
        }
        return Blocks.OAK_LOG.getDefaultState();
    }
    
    public void setHeldTreeBlock(BlockState state) {
        String blockId = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString();
        this.dataTracker.set(HELD_TREE_TYPE, blockId);
    }
    
    public BlockState getHeldLeafBlock() {
        String blockId = this.dataTracker.get(HELD_LEAF_TYPE);
        try {
            net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(blockId);
            if (id != null) {
                net.minecraft.block.Block block = net.minecraft.registry.Registries.BLOCK.get(id);
                if (block != Blocks.AIR) {
                    return block.getDefaultState();
                }
            }
        } catch (Exception e) {
            // Fallback
        }
        return Blocks.OAK_LEAVES.getDefaultState();
    }
    
    public void setHeldLeafBlock(BlockState state) {
        String blockId = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString();
        this.dataTracker.set(HELD_LEAF_TYPE, blockId);
    }
    
    public boolean canAttack() {
        return attackCooldown <= 0 && animationTicks <= 0;
    }
    
    // SLAM ATTACK
    public void performGroundSlam() {
        if (!canAttack()) return;
        
        com.forestcolossus.ForestColossusMod.LOGGER.info("ForestColossus: Performing SLAM!");
        
        setAnimationState(ANIM_SLAM);
        animationTicks = 50;
        attackCooldown = 80;
        slamExecuted = false;
        
        this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 2.0f, 0.6f);
        
        // Trigger animation refresh
        this.triggerAnim("controller", "animation.forest_colossus.slam");
    }
    
    private void executeSlam() {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) return;
        
        serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 2.5f, 0.7f);
        
        // Damage entities
        Box damageBox = this.getBoundingBox().expand(10, 5, 10);
        for (Entity entity : serverWorld.getOtherEntities(this, damageBox)) {
            if (entity instanceof LivingEntity living && !(entity instanceof ForestColossusEntity)) {
                double dist = entity.distanceTo(this);
                if (dist < 12) {
                    float damage = (float) (SLAM_DAMAGE * Math.max(0.2, 1.0 - dist / 12.0));
                    living.damage(this.getDamageSources().mobAttack(this), damage);
                    
                    Vec3d knockback = entity.getPos().subtract(this.getPos()).normalize();
                    double strength = 1.5 * (1.0 - dist / 12.0);
                    entity.addVelocity(knockback.x * strength, 0.6 + strength * 0.3, knockback.z * strength);
                    entity.velocityModified = true;
                }
            }
        }
        
        // Terrain destruction
        int radius = 5;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) continue;
                for (int y = -1; y < 2; y++) {
                    BlockPos pos = this.getBlockPos().add(x, y, z);
                    BlockState state = serverWorld.getBlockState(pos);
                    float hardness = state.getHardness(serverWorld, pos);
                    if (hardness >= 0 && hardness < 2.0f && !state.isAir() && random.nextFloat() < 0.3f) {
                        serverWorld.breakBlock(pos, true);
                    }
                }
            }
        }
        
        // Particles
        for (int i = 0; i < 25; i++) {
            double ox = (random.nextDouble() - 0.5) * 10;
            double oz = (random.nextDouble() - 0.5) * 10;
            serverWorld.spawnParticles(ParticleTypes.EXPLOSION,
                this.getX() + ox, this.getY() + 0.5, this.getZ() + oz, 1, 0, 0, 0, 0);
        }
    }
    
    // TREE GRAB
    public void performTreeGrab() {
        if (!canAttack() || hasTree()) return;
        
        com.forestcolossus.ForestColossusMod.LOGGER.info("ForestColossus: Performing GRAB!");
        
        BlockPos treePos = findNearbyTree();
        
        setAnimationState(ANIM_GRAB);
        animationTicks = 40;
        attackCooldown = 30;
        
        // Trigger animation
        this.triggerAnim("controller", "animation.forest_colossus.grab");
        
        if (treePos != null && this.getWorld() instanceof ServerWorld serverWorld) {
            grabTree(serverWorld, treePos);
        }
    }
    
    private BlockPos findNearbyTree() {
        World world = this.getWorld();
        BlockPos entityPos = this.getBlockPos();
        
        for (int radius = 2; radius <= 12; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    for (int y = 0; y < 8; y++) {
                        BlockPos checkPos = entityPos.add(x, y, z);
                        if (world.getBlockState(checkPos).isIn(BlockTags.LOGS)) {
                            return checkPos;
                        }
                    }
                }
            }
        }
        return null;
    }
    
    private void grabTree(ServerWorld world, BlockPos logPos) {
        // Find tree base first
        BlockPos basePos = findTreeBase(world, logPos);
        BlockState treeState = world.getBlockState(basePos);
        setHeldTreeBlock(treeState);
        
        com.forestcolossus.ForestColossusMod.LOGGER.info("ForestColossus: Grabbing tree at base " + basePos + " with block " + treeState.getBlock().getName().getString());
        
        // Capture full tree structure using flood-fill
        Set<BlockPos> visitedLogs = new HashSet<>();
        Set<BlockPos> visitedLeaves = new HashSet<>();
        
        floodFillLogs(world, basePos, basePos, visitedLogs, 0);
        
        com.forestcolossus.ForestColossusMod.LOGGER.info("ForestColossus: Found " + visitedLogs.size() + " logs");
        
        // Find leaves connected to logs and capture the first leaf type
        BlockState firstLeafState = null;
        for (BlockPos lp : new HashSet<>(visitedLogs)) {
            for (BlockPos neighbor : getNeighbors26(lp)) {
                if (!visitedLogs.contains(neighbor) && !visitedLeaves.contains(neighbor)) {
                    BlockState state = world.getBlockState(neighbor);
                    if (state.isIn(BlockTags.LEAVES)) {
                        if (firstLeafState == null) {
                            firstLeafState = state;
                            com.forestcolossus.ForestColossusMod.LOGGER.info("ForestColossus: Found leaf type " + state.getBlock().getName().getString());
                        }
                        floodFillLeaves(world, neighbor, basePos, visitedLogs, visitedLeaves, 0);
                    }
                }
            }
        }
        
        com.forestcolossus.ForestColossusMod.LOGGER.info("ForestColossus: Found " + visitedLeaves.size() + " leaves");
        
        // Set the actual leaf type
        if (firstLeafState != null) {
            setHeldLeafBlock(firstLeafState);
        }
        
        // Convert to relative positions and store
        List<int[]> logPositions = new ArrayList<>();
        List<int[]> leafPositions = new ArrayList<>();
        
        for (BlockPos pos : visitedLogs) {
            logPositions.add(new int[]{
                pos.getX() - basePos.getX(),
                pos.getY() - basePos.getY(),
                pos.getZ() - basePos.getZ()
            });
        }
        
        for (BlockPos pos : visitedLeaves) {
            leafPositions.add(new int[]{
                pos.getX() - basePos.getX(),
                pos.getY() - basePos.getY(),
                pos.getZ() - basePos.getZ()
            });
        }
        
        // Save to NBT and sync
        saveTreeStructure(logPositions, leafPositions);
        
        // Remove tree from world
        for (BlockPos pos : visitedLogs) {
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, world.getBlockState(pos)),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.02);
            world.setBlockState(pos, Blocks.AIR.getDefaultState());
        }
        for (BlockPos pos : visitedLeaves) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState());
        }
        
        setHasTree(true);
        com.forestcolossus.ForestColossusMod.LOGGER.info("ForestColossus: Grabbed tree with " + logPositions.size() + " logs and " + leafPositions.size() + " leaves");
        world.playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.HOSTILE, 2.0f, 0.6f);
    }
    
    private BlockPos findTreeBase(ServerWorld world, BlockPos logPos) {
        BlockPos current = logPos;
        while (current.getY() > world.getBottomY()) {
            BlockPos below = current.down();
            if (world.getBlockState(below).isIn(BlockTags.LOGS)) {
                current = below;
            } else {
                break;
            }
        }
        return current;
    }
    
    private void floodFillLogs(ServerWorld world, BlockPos current, BlockPos basePos, Set<BlockPos> visited, int depth) {
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
        
        floodFillLogs(world, current.up(), basePos, visited, depth + 1);
        floodFillLogs(world, current.down(), basePos, visited, depth + 1);
        floodFillLogs(world, current.north(), basePos, visited, depth + 1);
        floodFillLogs(world, current.south(), basePos, visited, depth + 1);
        floodFillLogs(world, current.east(), basePos, visited, depth + 1);
        floodFillLogs(world, current.west(), basePos, visited, depth + 1);
    }
    
    private void floodFillLeaves(ServerWorld world, BlockPos current, BlockPos basePos,
                                 Set<BlockPos> logs, Set<BlockPos> visitedLeaves, int depth) {
        if (depth > 4) return;
        if (visitedLeaves.contains(current)) return;
        if (logs.contains(current)) return;
        
        int dx = Math.abs(current.getX() - basePos.getX());
        int dz = Math.abs(current.getZ() - basePos.getZ());
        if (dx > 4 || dz > 4) return;
        
        int dy = current.getY() - basePos.getY();
        if (dy < 0 || dy > 20) return;
        
        BlockState state = world.getBlockState(current);
        if (!state.isIn(BlockTags.LEAVES)) return;
        
        visitedLeaves.add(current);
        
        floodFillLeaves(world, current.up(), basePos, logs, visitedLeaves, depth + 1);
        floodFillLeaves(world, current.down(), basePos, logs, visitedLeaves, depth + 1);
        floodFillLeaves(world, current.north(), basePos, logs, visitedLeaves, depth + 1);
        floodFillLeaves(world, current.south(), basePos, logs, visitedLeaves, depth + 1);
        floodFillLeaves(world, current.east(), basePos, logs, visitedLeaves, depth + 1);
        floodFillLeaves(world, current.west(), basePos, logs, visitedLeaves, depth + 1);
    }
    
    private List<BlockPos> getNeighbors26(BlockPos pos) {
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
    
    private void saveTreeStructure(List<int[]> logs, List<int[]> leaves) {
        NbtCompound structure = new NbtCompound();
        
        NbtList logsList = new NbtList();
        for (int[] pos : logs) {
            NbtCompound posNbt = new NbtCompound();
            posNbt.putInt("x", pos[0]);
            posNbt.putInt("y", pos[1]);
            posNbt.putInt("z", pos[2]);
            logsList.add(posNbt);
        }
        structure.put("logs", logsList);
        
        NbtList leavesList = new NbtList();
        for (int[] pos : leaves) {
            NbtCompound posNbt = new NbtCompound();
            posNbt.putInt("x", pos[0]);
            posNbt.putInt("y", pos[1]);
            posNbt.putInt("z", pos[2]);
            leavesList.add(posNbt);
        }
        structure.put("leaves", leavesList);
        
        // Update local cache immediately (server-side)
        this.cachedLogPositions.clear();
        this.cachedLogPositions.addAll(logs);
        this.cachedLeafPositions.clear();
        this.cachedLeafPositions.addAll(leaves);
        
        this.dataTracker.set(TREE_STRUCTURE, structure);
    }
    
    public List<int[]> getLogPositions() {
        // Always reload from NBT to ensure we have latest data
        loadTreeStructureFromNbt();
        return cachedLogPositions;
    }
    
    public List<int[]> getLeafPositions() {
        // Always reload from NBT to ensure we have latest data
        loadTreeStructureFromNbt();
        return cachedLeafPositions;
    }
    
    private void loadTreeStructureFromNbt() {
        NbtCompound structure = this.dataTracker.get(TREE_STRUCTURE);
        if (structure == null || structure.isEmpty()) return;
        
        cachedLogPositions.clear();
        cachedLeafPositions.clear();
        
        if (structure.contains("logs")) {
            NbtList logsList = structure.getList("logs", 10);
            for (int i = 0; i < logsList.size(); i++) {
                NbtCompound posNbt = logsList.getCompound(i);
                cachedLogPositions.add(new int[]{posNbt.getInt("x"), posNbt.getInt("y"), posNbt.getInt("z")});
            }
        }
        
        if (structure.contains("leaves")) {
            NbtList leavesList = structure.getList("leaves", 10);
            for (int i = 0; i < leavesList.size(); i++) {
                NbtCompound posNbt = leavesList.getCompound(i);
                cachedLeafPositions.add(new int[]{posNbt.getInt("x"), posNbt.getInt("y"), posNbt.getInt("z")});
            }
        }
    }
    
    // THROW ATTACK
    public void performThrow(LivingEntity target) {
        if (!canAttack() || !hasTree() || target == null) return;
        
        this.throwTarget = target;
        setAnimationState(ANIM_THROW);
        animationTicks = 40;
        attackCooldown = 60;
        throwExecuted = false;
        
        this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 1.5f, 0.8f);
        
        // Trigger animation
        this.triggerAnim("controller", "animation.forest_colossus.throw");
    }
    
    private void executeThrow() {
        if (!(this.getWorld() instanceof ServerWorld world) || throwTarget == null || !hasTree()) return;
        
        ThrownTreeEntity thrownTree = new ThrownTreeEntity(ModEntities.THROWN_TREE, world);
        
        Vec3d handPos = this.getPos().add(
            Math.sin(Math.toRadians(-this.bodyYaw)) * 3,
            8,
            Math.cos(Math.toRadians(-this.bodyYaw)) * 3
        );
        thrownTree.setPosition(handPos);
        thrownTree.setTreeBlock(getHeldTreeBlock());
        thrownTree.setLeafBlock(getHeldLeafBlock());
        
        // Pass the ACTUAL tree structure to the thrown tree
        thrownTree.setTreeStructure(getLogPositions(), getLeafPositions());
        
        thrownTree.setOwner(this);
        
        Vec3d toTarget = throwTarget.getPos().add(0, 1, 0).subtract(handPos);
        double dist = toTarget.horizontalLength();
        double speed = Math.min(dist * 0.06, 2.0);
        double arc = Math.min(dist * 0.03, 0.5);
        
        Vec3d velocity = toTarget.normalize().multiply(speed).add(0, arc, 0);
        thrownTree.setVelocity(velocity);
        
        world.spawnEntity(thrownTree);
        com.forestcolossus.ForestColossusMod.LOGGER.info("ForestColossus: Threw tree with " + getLogPositions().size() + " logs!");
        world.playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.HOSTILE, 2.0f, 0.6f);
        
        setHasTree(false);
        
        // Clear the cached tree structure
        cachedLogPositions.clear();
        cachedLeafPositions.clear();
    }
    
    private void playFootstepEffects() {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) return;
        
        serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.ENTITY_IRON_GOLEM_STEP, SoundCategory.HOSTILE, 1.5f, 0.5f);
        
        for (int i = 0; i < 4; i++) {
            double ox = (random.nextDouble() - 0.5) * 3;
            double oz = (random.nextDouble() - 0.5) * 3;
            serverWorld.spawnParticles(ParticleTypes.CLOUD,
                this.getX() + ox, this.getY(), this.getZ() + oz, 1, 0.1, 0.05, 0.1, 0.01);
        }
    }
    
    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        if (bossBar != null) bossBar.addPlayer(player);
    }
    
    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        if (bossBar != null) bossBar.removePlayer(player);
    }
    
    @Override
    public void onDeath(DamageSource damageSource) {
        super.onDeath(damageSource);
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ENTITY_IRON_GOLEM_DEATH, SoundCategory.HOSTILE, 3.0f, 0.4f);
            for (int i = 0; i < 30; i++) {
                double ox = (random.nextDouble() - 0.5) * 6;
                double oy = random.nextDouble() * 8;
                double oz = (random.nextDouble() - 0.5) * 6;
                serverWorld.spawnParticles(ParticleTypes.EXPLOSION,
                    this.getX() + ox, this.getY() + oy, this.getZ() + oz, 1, 0, 0, 0, 0);
            }
        }
    }
    
    @Override
    protected net.minecraft.registry.RegistryKey<net.minecraft.loot.LootTable> getLootTableId() {
        return net.minecraft.registry.RegistryKey.of(
            net.minecraft.registry.RegistryKeys.LOOT_TABLE,
            net.minecraft.util.Identifier.of("forestcolossus", "entities/forest_colossus")
        );
    }
    
    @Override
    public boolean cannotDespawn() {
        return true;
    }
    
    @Override
    public boolean isPushable() {
        return false;
    }
    
    // Check if eyes should glow (at night)
    public boolean shouldEyesGlow() {
        World world = this.getWorld();
        long timeOfDay = world.getTimeOfDay() % 24000;
        // Night time is roughly 13000-23000
        return timeOfDay >= 13000 && timeOfDay <= 23000;
    }
    
    // Spawn check for jungle biomes
    public static boolean canSpawn(EntityType<ForestColossusEntity> type, net.minecraft.world.WorldAccess world, 
                                   net.minecraft.entity.SpawnReason spawnReason, BlockPos pos, net.minecraft.util.math.random.Random random) {
        // Very rare spawn - 1 in 100 chance even when conditions are met
        if (random.nextInt(100) != 0) {
            return false;
        }
        
        // Must be on solid ground
        BlockState below = world.getBlockState(pos.down());
        if (!below.isSolidBlock(world, pos.down())) {
            return false;
        }
        
        // Check for trees nearby (needs forest environment)
        int treeCount = 0;
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                for (int y = 0; y < 10; y++) {
                    if (world.getBlockState(pos.add(x, y, z)).isIn(BlockTags.LOGS)) {
                        treeCount++;
                        if (treeCount >= 5) {
                            // Announce spawn to nearby players
                            if (world instanceof ServerWorld serverWorld) {
                                Box announceBox = new Box(pos).expand(64);
                                for (PlayerEntity player : serverWorld.getPlayers()) {
                                    if (announceBox.contains(player.getPos())) {
                                        player.sendMessage(Text.literal("\u00A72\u00A7oA monster roams these woods..."), false);
                                    }
                                }
                            }
                            return true;
                        }
                    }
                }
            }
        }
        
        return false;
    }
}

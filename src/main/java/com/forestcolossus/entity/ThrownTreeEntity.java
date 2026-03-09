package com.forestcolossus.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class ThrownTreeEntity extends ProjectileEntity {
    
    private static final TrackedData<NbtCompound> TREE_BLOCK = DataTracker.registerData(ThrownTreeEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    private static final TrackedData<NbtCompound> LEAF_BLOCK = DataTracker.registerData(ThrownTreeEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    private static final TrackedData<NbtCompound> TREE_STRUCTURE = DataTracker.registerData(ThrownTreeEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    
    private BlockState treeBlock = Blocks.OAK_LOG.getDefaultState();
    private BlockState leafBlock = Blocks.OAK_LEAVES.getDefaultState();
    public int treeAge = 0;
    
    // Cached tree structure for rendering
    private List<int[]> cachedLogPositions = new ArrayList<>();
    private List<int[]> cachedLeafPositions = new ArrayList<>();
    
    public ThrownTreeEntity(EntityType<? extends ProjectileEntity> entityType, World world) {
        super(entityType, world);
    }
    
    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(TREE_BLOCK, new NbtCompound());
        builder.add(LEAF_BLOCK, new NbtCompound());
        builder.add(TREE_STRUCTURE, new NbtCompound());
    }
    
    public void setTreeBlock(BlockState state) {
        this.treeBlock = state;
        NbtCompound nbt = NbtHelper.fromBlockState(state);
        this.dataTracker.set(TREE_BLOCK, nbt);
    }
    
    public BlockState getTreeBlock() {
        NbtCompound nbt = this.dataTracker.get(TREE_BLOCK);
        if (!nbt.isEmpty()) {
            return NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), nbt);
        }
        return treeBlock;
    }
    
    public void setLeafBlock(BlockState state) {
        this.leafBlock = state;
        NbtCompound nbt = NbtHelper.fromBlockState(state);
        this.dataTracker.set(LEAF_BLOCK, nbt);
    }
    
    public BlockState getLeafBlock() {
        NbtCompound nbt = this.dataTracker.get(LEAF_BLOCK);
        if (!nbt.isEmpty()) {
            return NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), nbt);
        }
        return leafBlock;
    }
    
    public void setTreeStructure(List<int[]> logs, List<int[]> leaves) {
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
        // Always reload to get latest synced data
        loadTreeStructure();
        return cachedLogPositions;
    }
    
    public List<int[]> getLeafPositions() {
        // Always reload to get latest synced data
        loadTreeStructure();
        return cachedLeafPositions;
    }
    
    private void loadTreeStructure() {
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
    
    @Override
    public void tick() {
        super.tick();
        treeAge++;
        
        if (treeAge > 200) {
            this.discard();
            return;
        }
        
        Vec3d velocity = this.getVelocity();
        
        // Apply gravity
        this.setVelocity(velocity.x, velocity.y - 0.05, velocity.z);
        velocity = this.getVelocity();
        
        // Calculate next position
        Vec3d nextPos = this.getPos().add(velocity);
        
        if (this.getWorld().isClient) {
            for (int i = 0; i < 2; i++) {
                this.getWorld().addParticle(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, getTreeBlock()),
                    this.getX() + (random.nextDouble() - 0.5) * 2,
                    this.getY() + (random.nextDouble() - 0.5) * 2,
                    this.getZ() + (random.nextDouble() - 0.5) * 2,
                    0, 0, 0
                );
            }
        }
        
        if (!this.getWorld().isClient) {
            // Check for block collision along the path
            if (checkBlockCollision(this.getPos(), nextPos)) {
                return; // Already handled impact
            }
            
            // Check for entity collision
            if (checkEntityCollision()) {
                return; // Already handled impact
            }
        }
        
        // Move to next position
        this.setPosition(nextPos);
    }
    
    private boolean checkBlockCollision(Vec3d from, Vec3d to) {
        World world = this.getWorld();
        
        // Raycast from current position to next position
        net.minecraft.util.hit.BlockHitResult hitResult = world.raycast(new net.minecraft.world.RaycastContext(
            from, to,
            net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
            net.minecraft.world.RaycastContext.FluidHandling.NONE,
            this
        ));
        
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            // Hit a block - impact at the hit location
            Vec3d hitPos = hitResult.getPos();
            this.setPosition(hitPos);
            onBlockHit();
            return true;
        }
        
        return false;
    }
    
    private boolean checkEntityCollision() {
        // Use a larger hitbox based on tree size
        Box hitBox = this.getBoundingBox().expand(1.5);
        
        List<Entity> entities = this.getWorld().getOtherEntities(this, hitBox, e -> e instanceof LivingEntity && e != this.getOwner());
        
        for (Entity entity : entities) {
            if (entity instanceof LivingEntity living) {
                onEntityHit(living);
                return true;
            }
        }
        
        return false;
    }
    
    private void onEntityHit(LivingEntity target) {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) return;
        
        // Deal 3 hearts (6 damage) - fixed damage
        float damage = 6.0f;
        
        target.damage(this.getDamageSources().thrown(this, this.getOwner()), damage);
        
        // Small knockback - just a little push
        Vec3d knockback = this.getVelocity().normalize().multiply(0.5);
        target.addVelocity(knockback.x, 0.25, knockback.z);
        target.velocityModified = true;
        
        serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 2.0f, 0.6f);
        serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.HOSTILE, 2.0f, 0.8f);
        
        spawnBreakParticles(serverWorld);
        createCrater(serverWorld);
        
        this.discard();
    }
    
    private void onBlockHit() {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) return;
        
        serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.HOSTILE, 2.5f, 0.6f);
        serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 1.5f, 0.7f);
        
        spawnBreakParticles(serverWorld);
        createCrater(serverWorld);
        
        this.discard();
    }
    
    private void createCrater(ServerWorld world) {
        net.minecraft.util.math.BlockPos center = this.getBlockPos();
        int craterRadius = 3;
        int craterDepth = 2;
        
        // Create a bowl-shaped crater
        for (int x = -craterRadius; x <= craterRadius; x++) {
            for (int z = -craterRadius; z <= craterRadius; z++) {
                double distSq = x * x + z * z;
                double maxDistSq = craterRadius * craterRadius;
                
                if (distSq > maxDistSq) continue;
                
                // Depth decreases towards edges (bowl shape)
                int depthHere = (int) Math.ceil(craterDepth * (1.0 - distSq / maxDistSq));
                
                for (int y = depthHere; y >= -depthHere; y--) {
                    net.minecraft.util.math.BlockPos pos = center.add(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    
                    // Don't break bedrock or very hard blocks
                    float hardness = state.getHardness(world, pos);
                    if (hardness >= 0 && hardness < 50.0f && !state.isAir()) {
                        // Higher chance to break blocks closer to center
                        float breakChance = (float) (0.9 - 0.5 * (distSq / maxDistSq));
                        if (random.nextFloat() < breakChance) {
                            world.breakBlock(pos, true);
                            
                            // Spawn dirt particles
                            world.spawnParticles(
                                new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                3, 0.3, 0.3, 0.3, 0.05
                            );
                        }
                    }
                }
            }
        }
        
        // Damage nearby entities from the impact (small splash damage)
        Box damageBox = new Box(center).expand(craterRadius + 1);
        for (Entity entity : world.getOtherEntities(this, damageBox)) {
            if (entity instanceof LivingEntity living && entity != this.getOwner()) {
                double dist = entity.getPos().distanceTo(this.getPos());
                if (dist < craterRadius + 1) {
                    // Small splash damage: 2 damage max, reduced by distance
                    float damage = (float) (2.0 * (1.0 - dist / (craterRadius + 1)));
                    living.damage(this.getDamageSources().thrown(this, this.getOwner()), damage);
                    
                    // Small knockback away from center
                    Vec3d knockback = entity.getPos().subtract(this.getPos()).normalize().multiply(0.3);
                    entity.addVelocity(knockback.x, 0.15, knockback.z);
                    entity.velocityModified = true;
                }
            }
        }
    }
    
    private void spawnBreakParticles(ServerWorld world) {
        for (int i = 0; i < 30; i++) {
            world.spawnParticles(
                new BlockStateParticleEffect(ParticleTypes.BLOCK, getTreeBlock()),
                this.getX() + (random.nextDouble() - 0.5) * 3,
                this.getY() + (random.nextDouble() - 0.5) * 3,
                this.getZ() + (random.nextDouble() - 0.5) * 3,
                1, 0.5, 0.5, 0.5, 0.1
            );
        }
        
        for (int i = 0; i < 10; i++) {
            world.spawnParticles(ParticleTypes.EXPLOSION,
                this.getX() + (random.nextDouble() - 0.5) * 2,
                this.getY() + (random.nextDouble() - 0.5) * 2,
                this.getZ() + (random.nextDouble() - 0.5) * 2,
                1, 0, 0, 0, 0
            );
        }
    }
    
    @Override
    protected void onCollision(HitResult hitResult) {
    }
    
    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
    }
    
    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
    }
    
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.put("TreeBlock", NbtHelper.fromBlockState(treeBlock));
    }
    
    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("TreeBlock")) {
            treeBlock = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), nbt.getCompound("TreeBlock"));
        }
    }
}

package com.forestcolossus.item;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

public class LivingBranchItem extends SwordItem {
    
    private static final int SLAM_COOLDOWN = 40; // 2 seconds
    
    public LivingBranchItem(ToolMaterial material, Settings settings) {
        super(material, settings.attributeModifiers(SwordItem.createAttributeModifiers(material, 7, -3.0f)));
    }
    
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        
        if (!world.isClient && player.getItemCooldownManager().getCooldownProgress(this, 0) == 0) {
            // Ground slam attack!
            performGroundSlam(world, player);
            player.getItemCooldownManager().set(this, SLAM_COOLDOWN);
            stack.damage(5, player, LivingEntity.getSlotForHand(hand));
            return TypedActionResult.success(stack);
        }
        
        return TypedActionResult.pass(stack);
    }
    
    private void performGroundSlam(World world, PlayerEntity player) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        
        Vec3d pos = player.getPos();
        double radius = 5.0;
        float damage = 12.0f;
        
        // Spawn particles in a ring
        for (int i = 0; i < 36; i++) {
            double angle = Math.toRadians(i * 10);
            for (double r = 1; r <= radius; r += 0.5) {
                double x = pos.x + Math.cos(angle) * r;
                double z = pos.z + Math.sin(angle) * r;
                serverWorld.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    x, pos.y + 0.1, z, 1, 0, 0.1, 0, 0.02);
            }
        }
        
        // Spawn leaf particles
        for (int i = 0; i < 20; i++) {
            double x = pos.x + (world.random.nextDouble() - 0.5) * radius * 2;
            double z = pos.z + (world.random.nextDouble() - 0.5) * radius * 2;
            serverWorld.spawnParticles(ParticleTypes.COMPOSTER,
                x, pos.y + 0.5, z, 3, 0.3, 0.3, 0.3, 0.05);
        }
        
        // Damage and knockback nearby entities
        Box damageBox = new Box(pos.x - radius, pos.y - 1, pos.z - radius,
                                pos.x + radius, pos.y + 3, pos.z + radius);
        
        List<Entity> entities = world.getOtherEntities(player, damageBox);
        for (Entity entity : entities) {
            if (entity instanceof LivingEntity living) {
                double dist = entity.getPos().distanceTo(pos);
                if (dist < radius) {
                    // Damage falls off with distance
                    float actualDamage = (float) (damage * (1.0 - dist / radius));
                    living.damage(player.getDamageSources().playerAttack(player), actualDamage);
                    
                    // Knockback away from player
                    Vec3d knockback = entity.getPos().subtract(pos).normalize().multiply(1.5);
                    entity.addVelocity(knockback.x, 0.5, knockback.z);
                    entity.velocityModified = true;
                }
            }
        }
        
        // Sound
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ENTITY_RAVAGER_STUNNED, SoundCategory.PLAYERS, 1.5f, 0.7f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.BLOCK_GRASS_BREAK, SoundCategory.PLAYERS, 2.0f, 0.5f);
    }
    
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // Spawn leaf particles on hit
        if (attacker.getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.COMPOSTER,
                target.getX(), target.getY() + target.getHeight() / 2, target.getZ(),
                5, 0.3, 0.3, 0.3, 0.05);
        }
        return super.postHit(stack, target, attacker);
    }
}

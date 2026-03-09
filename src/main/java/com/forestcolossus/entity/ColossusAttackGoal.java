package com.forestcolossus.entity;

import com.forestcolossus.ForestColossusMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;

import java.util.EnumSet;

public class ColossusAttackGoal extends Goal {
    
    private final ForestColossusEntity colossus;
    private LivingEntity target;
    private int attackTimer = 0;
    private int pathUpdateTimer = 0;
    private int treeThrowCount = 0; // Track how many trees thrown before walking
    private int walkPhaseTimer = 0; // Force walking phase
    private boolean inWalkPhase = false;
    
    public ColossusAttackGoal(ForestColossusEntity colossus) {
        this.colossus = colossus;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }
    
    @Override
    public boolean canStart() {
        LivingEntity target = this.colossus.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        this.target = target;
        return true;
    }
    
    @Override
    public boolean shouldContinue() {
        if (target == null || !target.isAlive()) {
            return false;
        }
        return this.colossus.getTarget() == target;
    }
    
    @Override
    public void stop() {
        this.target = null;
        this.colossus.getNavigation().stop();
        this.treeThrowCount = 0;
        this.inWalkPhase = false;
    }
    
    @Override
    public void tick() {
        if (target == null) return;
        
        double dist = this.colossus.distanceTo(target);
        
        // Always look at target
        this.colossus.getLookControl().lookAt(target, 30.0f, 30.0f);
        
        // Handle walk phase timer
        if (walkPhaseTimer > 0) {
            walkPhaseTimer--;
            if (walkPhaseTimer <= 0) {
                inWalkPhase = false;
            }
        }
        
        // After 2-3 tree throws, enter walk phase to chase player
        if (treeThrowCount >= 2 + colossus.getRandom().nextInt(2)) {
            inWalkPhase = true;
            walkPhaseTimer = 100; // Walk for ~5 seconds
            treeThrowCount = 0;
        }
        
        // ALWAYS try to move towards target if not very close
        if (--pathUpdateTimer <= 0) {
            pathUpdateTimer = 10; // More frequent path updates
            
            // Move towards target if far enough away
            // During walk phase, be more aggressive about closing distance
            double stopDistance = inWalkPhase ? 5.0 : 8.0;
            
            if (dist > stopDistance) {
                Path path = this.colossus.getNavigation().findPathTo(target, 0);
                if (path != null) {
                    this.colossus.getNavigation().startMovingAlong(path, 1.0);
                } else {
                    // Direct movement fallback - always try to move towards target
                    double dx = target.getX() - colossus.getX();
                    double dz = target.getZ() - colossus.getZ();
                    double len = Math.sqrt(dx * dx + dz * dz);
                    if (len > 0) {
                        dx /= len;
                        dz /= len;
                        double speed = 0.3;
                        colossus.setVelocity(dx * speed, colossus.getVelocity().y, dz * speed);
                        colossus.velocityModified = true;
                    }
                }
            } else {
                this.colossus.getNavigation().stop();
            }
        }
        
        // Attack logic
        if (!colossus.canAttack()) {
            return;
        }
        
        attackTimer--;
        
        if (attackTimer <= 0) {
            // If we have a tree, throw it (unless in walk phase and close)
            if (colossus.hasTree()) {
                if (inWalkPhase && dist < 8) {
                    // Drop tree mentality during walk phase if close - slam instead
                    ForestColossusMod.LOGGER.info("ColossusAttackGoal: Walk phase slam!");
                    colossus.performGroundSlam();
                    attackTimer = 80;
                } else {
                    ForestColossusMod.LOGGER.info("ColossusAttackGoal: Throwing tree at target!");
                    colossus.performThrow(target);
                    treeThrowCount++;
                    attackTimer = 60;
                }
                return;
            }
            
            // During walk phase, prioritize slamming when close
            if (inWalkPhase) {
                if (dist <= 12) {
                    ForestColossusMod.LOGGER.info("ColossusAttackGoal: Walk phase - SLAM!");
                    colossus.performGroundSlam();
                    attackTimer = 70;
                }
                // Don't grab trees during walk phase, just chase
                return;
            }
            
            // Normal attack pattern
            // Close range (< 10 blocks) - high chance of slam
            if (dist <= 10) {
                float roll = colossus.getRandom().nextFloat();
                if (roll < 0.6f) {
                    // 60% slam when close
                    ForestColossusMod.LOGGER.info("ColossusAttackGoal: Close range SLAM!");
                    colossus.performGroundSlam();
                    attackTimer = 80;
                } else {
                    // 40% grab tree
                    ForestColossusMod.LOGGER.info("ColossusAttackGoal: Close range tree grab!");
                    colossus.performTreeGrab();
                    attackTimer = 50;
                }
            }
            // Medium range (10-25 blocks) - grab trees to throw
            else if (dist <= 25) {
                ForestColossusMod.LOGGER.info("ColossusAttackGoal: Medium range - tree grab!");
                colossus.performTreeGrab();
                attackTimer = 40;
            }
            // Far range (> 25 blocks) - grab trees
            else {
                ForestColossusMod.LOGGER.info("ColossusAttackGoal: Far range - tree grab!");
                colossus.performTreeGrab();
                attackTimer = 30;
            }
        }
    }
}

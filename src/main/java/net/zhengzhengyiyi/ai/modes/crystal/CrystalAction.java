package net.zhengzhengyiyi.ai.modes.crystal;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.List;
import java.util.ArrayList;
import java.util.AbstractMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Crystal bot combat action - handles crystal bombing, anchor usage, and attacks
 */
public class CrystalAction {
    private static final Logger LOGGER = LoggerFactory.getLogger("CrystalAction");
    
    private EntityPlayerMPFake bot;
    private int pearlCooldown = 0;
    private int anchorCooldown = 0;
    private int attackCooldown = 0;
    private int crystalCooldown = 0;
    private int totemCooldown = 0;
    
    // Anchor state machine
    private int anchorStep = 0; // 0 = not started, 1 = anchor placed, 2 = charged, 3 = ready to explode
    private BlockPos anchorPos = null;
    private boolean anchorReachable = false;
    
    // Crystal spamming state
    private BlockPos lastCrystalObsidianPos = null;
    private boolean alternateCrystal = false;

    public CrystalAction(EntityPlayerMPFake bot) {
        this.bot = bot;
    }
    
    public void setBot(EntityPlayerMPFake bot) {
        this.bot = bot;
    }
    
    /**
     * Main tick method for combat actions - called every tick
     */
    public void performCombat(ServerPlayerEntity target, double distance) {
        if (target == null || !target.isAlive()) {
            return;
        }
        
        // Decrease cooldowns
        if (pearlCooldown > 0) pearlCooldown--;
        if (anchorCooldown > 0) anchorCooldown--;
        if (attackCooldown > 0) attackCooldown--;
        if (crystalCooldown > 0) crystalCooldown--;
        if (totemCooldown > 0) totemCooldown--;
        
        // Ensure bot has a totem in offhand
        ensureTotemInOffhand();
        
        double botY = bot.getY();
        double targetY = target.getY();
        double verticalDistance = Math.abs(targetY - botY);
        double horizontalDistance = Math.sqrt(Math.pow(target.getX() - bot.getX(), 2) + Math.pow(target.getZ() - bot.getZ(), 2));
        
        // Use pearl to get close to player if far away
        handlePearlUsage(target, horizontalDistance);
        
        // Continue executing anchor sequence if in progress (must complete once started)
        if (anchorStep > 0 && anchorCooldown == 0) {
            executeAnchorSequence();
            anchorCooldown = 1;
        }
        
        // Crystal spamming - TOP PRIORITY (when not in anchor sequence)
        if (distance <= 6.0 && crystalCooldown == 0 && targetY >= botY + 0.0) {
            handleCrystalSpamming(target, distance);
        }
        
        // Same height sprint knockback hit
        handleSprintAttack(target, distance, botY, targetY);
        
        // Player under bot - Place anchor
        if (targetY <= botY - 0.6 && distance <= 5.0 && verticalDistance <= 4.0) {
            handleAnchorPlacement(target);
        }
    }
    
    private void ensureTotemInOffhand() {
        ItemStack offhand = bot.getEquippedStack(EquipmentSlot.OFFHAND);
        if (offhand.getItem() != Items.TOTEM_OF_UNDYING && totemCooldown == 0) {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = bot.getInventory().getStack(i);
                if (stack != null && stack.getItem() == Items.TOTEM_OF_UNDYING && stack.getCount() > 0) {
                    ItemStack toMove = new ItemStack(Items.TOTEM_OF_UNDYING, 1);
                    bot.equipStack(EquipmentSlot.OFFHAND, toMove);
                    if (stack.getCount() > 1) {
                        bot.getInventory().setStack(i, new ItemStack(stack.getItem(), stack.getCount() - 1));
                    } else {
                        bot.getInventory().setStack(i, ItemStack.EMPTY);
                    }
                    LOGGER.info("Moved totem from inventory slot {} to offhand for bot {}", i, bot.getName().getString());
                    totemCooldown = 2;
                    break;
                }
            }
        }
    }
    
    private void handlePearlUsage(ServerPlayerEntity target, double horizontalDistance) {
        ItemStack pearls = bot.getInventory().getStack(8);
        boolean hasPearls = pearls.getItem() == Items.ENDER_PEARL && pearls.getCount() > 0;
        
        if (horizontalDistance > 6 && hasPearls && pearlCooldown == 0) {
            bot.getInventory().setSelectedSlot(8);
            bot.setCurrentHand(Hand.MAIN_HAND);
            
            double dx = target.getX() - bot.getX();
            double dz = target.getZ() - bot.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
            bot.setYaw(yaw);
            bot.setPitch(0.0f);
            
            LOGGER.info("Crystal bot throwing ender pearl to get close (horizontal distance: {})", horizontalDistance);
            bot.interactionManager.interactItem(bot, bot.getEntityWorld(), pearls, Hand.MAIN_HAND);
            pearlCooldown = 2;
        }
    }
    
    private void handleCrystalSpamming(ServerPlayerEntity target, double distance) {
        BlockPos targetPos = target.getBlockPos();
        BlockPos existingObsidian = findClosestObsidian(targetPos);
        
        BlockPos placePos;
        if (existingObsidian != null) {
            double obsidianDistance = calculate3DDistance(existingObsidian, targetPos);
            if (obsidianDistance <= 4.0) {
                placePos = existingObsidian;
                LOGGER.info("Crystal - using existing obsidian at: {} (distance: {})", placePos, obsidianDistance);
            } else {
                placePos = findCrystalPlacementPosition(targetPos);
                LOGGER.info("Crystal - existing obsidian too far ({}), placing new at: {}", obsidianDistance, placePos);
            }
        } else {
            placePos = findCrystalPlacementPosition(targetPos);
            LOGGER.info("Crystal - no existing obsidian, placing new at: {}", placePos);
        }

        if (placePos != null) {
            if (lastCrystalObsidianPos != null && !lastCrystalObsidianPos.equals(placePos)) {
                alternateCrystal = !alternateCrystal;
            }
            
            if (alternateCrystal && lastCrystalObsidianPos != null && 
                bot.getEntityWorld().getBlockState(lastCrystalObsidianPos).getBlock() == Blocks.OBSIDIAN) {
                placePos = lastCrystalObsidianPos;
                LOGGER.info("Crystal - alternating to previous obsidian at: {}", placePos);
            }
            
            if (bot.getEntityWorld().getBlockState(placePos).getBlock() == Blocks.OBSIDIAN) {
                LOGGER.info("Crystal - spamming crystal on existing obsidian at: {}", placePos);
                placeCrystalOnExistingObsidian(placePos, target);
                lastCrystalObsidianPos = placePos;
                crystalCooldown = 1;
            } else {
                LOGGER.info("Crystal - placing new obsidian at: {}", placePos);
                placeObsidianAndCrystal(placePos);
                lastCrystalObsidianPos = placePos;
                crystalCooldown = 1;
            }
        } else {
            LOGGER.info("Crystal - no valid position found, skipping");
        }
    }
    
    private void handleSprintAttack(ServerPlayerEntity target, double distance, double botY, double targetY) {
        int targetBlockY = target.getBlockPos().getY();
        int botBlockY = bot.getBlockPos().getY();
        boolean sameHeight = targetBlockY == botBlockY || Math.abs(targetY - botY) < 0.3;
        
        if (distance <= 3.0 && attackCooldown == 0 && sameHeight) {
            ItemStack sword = bot.getInventory().getStack(0);
            if (sword.getItem() == Items.NETHERITE_SWORD) {
                if (bot.getInventory().getSelectedSlot() != 0) {
                    bot.getInventory().setSelectedSlot(0);
                }
                
                bot.setSprinting(true);
                bot.setPitch(-30.0f);
                
                LOGGER.info("Crystal - sprint knockback hit (targetY: {}, botY: {}, targetBlockY: {}, botBlockY: {})", targetY, botY, targetBlockY, botBlockY);
                
                bot.attack(target);
                attackCooldown = 1;
                bot.setSprinting(true);
            }
        }
    }
    
    private void handleAnchorPlacement(ServerPlayerEntity target) {
        ItemStack anchors = bot.getInventory().getStack(3);
        if (anchors.getItem() == Items.RESPAWN_ANCHOR && anchors.getCount() > 0) {
            BlockPos targetPos = target.getBlockPos();
            BlockPos placePos = targetPos.down();
            
            if (bot.getEntityWorld().isAir(placePos)) {
                if (anchorStep == 0 && anchorCooldown == 0) {
                    anchorPos = placePos;
                    anchorReachable = isLayerReachable(placePos);
                    anchorStep = 1;
                    LOGGER.info("Crystal - starting anchor sequence at {}, reachable: {}", placePos, anchorReachable);
                    executeAnchorSequence();
                    anchorCooldown = 1;
                }
            } else {
                LOGGER.info("Crystal - cannot place anchor, position not air: {}", placePos);
            }
        }
    }
    
    private void executeAnchorSequence() {
        switch (anchorStep) {
            case 1:
                placeAnchor();
                anchorStep = 2;
                LOGGER.info("Anchor sequence: Step 1 - Anchor placed at {}", anchorPos);
                break;
                
            case 2:
                chargeAnchor();
                anchorStep = 3;
                LOGGER.info("Anchor sequence: Step 2 - Anchor charged with glowstone");
                break;
                
            case 3:
                explodeAnchor();
                anchorStep = 0;
                anchorPos = null;
                LOGGER.info("Anchor sequence: Step 3 - Anchor exploded, sequence complete");
                break;
        }
    }
    
    private void placeAnchor() {
        bot.getInventory().setSelectedSlot(3);
        bot.setCurrentHand(Hand.MAIN_HAND);
        
        double dx = anchorPos.getX() + 0.5 - bot.getX();
        double dy = anchorPos.getY() + 0.5 - bot.getY();
        double dz = anchorPos.getZ() + 0.5 - bot.getZ();
        float aimYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float aimPitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        bot.setYaw(aimYaw);
        bot.setPitch(aimPitch);
        
        ItemStack anchorItem = bot.getInventory().getStack(3);
        bot.interactionManager.interactItem(bot, bot.getEntityWorld(), anchorItem, Hand.MAIN_HAND);
        LOGGER.info("Crystal bot placed respawn anchor at {}", anchorPos);
    }
    
    private void chargeAnchor() {
        bot.getInventory().setSelectedSlot(4);
        bot.setCurrentHand(Hand.MAIN_HAND);
        
        double dx = anchorPos.getX() + 0.5 - bot.getX();
        double dy = anchorPos.getY() + 0.5 - bot.getY();
        double dz = anchorPos.getZ() + 0.5 - bot.getZ();
        float aimYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float aimPitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        bot.setYaw(aimYaw);
        bot.setPitch(aimPitch);
        
        if (anchorReachable) {
            ItemStack glowstoneItem = bot.getInventory().getStack(4);
            bot.interactionManager.interactItem(bot, bot.getEntityWorld(), glowstoneItem, Hand.MAIN_HAND);
            LOGGER.info("Crystal bot charged anchor directly with glowstone");
        } else {
            BlockPos glowstonePos = anchorPos.add(0, 1, 0);
            double gdx = glowstonePos.getX() + 0.5 - bot.getX();
            double gdy = glowstonePos.getY() + 0.5 - bot.getY();
            double gdz = glowstonePos.getZ() + 0.5 - bot.getZ();
            float gYaw = (float) Math.toDegrees(Math.atan2(gdz, gdx)) - 90.0f;
            float gPitch = (float) Math.toDegrees(Math.atan2(-gdy, Math.sqrt(gdx * gdx + gdz * gdz)));
            bot.setYaw(gYaw);
            bot.setPitch(gPitch);
            
            if (bot.getEntityWorld().isAir(glowstonePos)) {
                ItemStack glowstoneItem = bot.getInventory().getStack(4);
                bot.interactionManager.interactItem(bot, bot.getEntityWorld(), glowstoneItem, Hand.MAIN_HAND);
                LOGGER.info("Crystal bot placed glowstone between anchor and bot at {}", glowstonePos);
            }
            
            ItemStack glowstoneItem = bot.getInventory().getStack(4);
            bot.interactionManager.interactItem(bot, bot.getEntityWorld(), glowstoneItem, Hand.MAIN_HAND);
            LOGGER.info("Crystal bot charged anchor with glowstone after placing glowstone");
        }
    }
    
    private void explodeAnchor() {
        bot.getInventory().setSelectedSlot(3);
        bot.setCurrentHand(Hand.MAIN_HAND);
        
        double dx = anchorPos.getX() + 0.5 - bot.getX();
        double dy = anchorPos.getY() + 0.5 - bot.getY();
        double dz = anchorPos.getZ() + 0.5 - bot.getZ();
        float aimYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float aimPitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        bot.setYaw(aimYaw);
        bot.setPitch(aimPitch);
        
        ItemStack anchorItem = bot.getInventory().getStack(3);
        bot.interactionManager.interactItem(bot, bot.getEntityWorld(), anchorItem, Hand.MAIN_HAND);
        LOGGER.info("Crystal bot exploded anchor at {}", anchorPos);
    }
    
    private void placeCrystalOnExistingObsidian(BlockPos obsidianPos, ServerPlayerEntity target) {
        bot.getInventory().setSelectedSlot(1);
        bot.setCurrentHand(Hand.MAIN_HAND);
        
        BlockPos crystalPos = obsidianPos.add(0, 1, 0);
        double dx = crystalPos.getX() + 0.5 - bot.getX();
        double dy = crystalPos.getY() + 0.5 - bot.getY();
        double dz = crystalPos.getZ() + 0.5 - bot.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        bot.setYaw(yaw);
        bot.setPitch(pitch);
        
        bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), Items.END_CRYSTAL.getDefaultStack(), Hand.MAIN_HAND, new BlockHitResult(crystalPos.toCenterPos(), Direction.UP, obsidianPos, false));
        attackEntity(crystalPos, bot);
    }
    
    private void placeObsidianAndCrystal(BlockPos placePos) {
        bot.getInventory().setSelectedSlot(2);
        bot.setCurrentHand(Hand.MAIN_HAND);
        
        double dx = placePos.getX() + 0.5 - bot.getX();
        double dy = placePos.getY() + 0.5 - bot.getY();
        double dz = placePos.getZ() + 0.5 - bot.getZ();
        float aimYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float aimPitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        bot.setYaw(aimYaw);
        bot.setPitch(aimPitch);
        
        if (bot.getEntityWorld().isAir(placePos) || bot.getEntityWorld().getBlockState(placePos).getBlock() == Blocks.SHORT_GRASS || bot.getEntityWorld().getBlockState(placePos).getBlock() == Blocks.TALL_DRY_GRASS || bot.getEntityWorld().getBlockState(placePos).getBlock() == Blocks.SHORT_DRY_GRASS || bot.getEntityWorld().getBlockState(placePos).getBlock() == Blocks.TALL_GRASS) {
            bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), Items.OBSIDIAN.getDefaultStack(), Hand.MAIN_HAND, new BlockHitResult(placePos.toCenterPos(), Direction.UP, placePos.down(), false));
            LOGGER.info("Crystal bot placed obsidian at {}", placePos);
        }
        
        bot.getInventory().setSelectedSlot(1);
        bot.setCurrentHand(Hand.MAIN_HAND);
        
        BlockPos crystalPos = placePos.add(0, 1, 0);
        double cdx = crystalPos.getX() + 0.5 - bot.getX();
        double cdy = crystalPos.getY() + 0.5 - bot.getY();
        double cdz = crystalPos.getZ() + 0.5 - bot.getZ();
        float cYaw = (float) Math.toDegrees(Math.atan2(cdz, cdx)) - 90.0f;
        float cPitch = (float) Math.toDegrees(Math.atan2(-cdy, Math.sqrt(cdx * cdx + cdz * cdz)));
        bot.setYaw(cYaw);
        bot.setPitch(cPitch);

        bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), Items.END_CRYSTAL.getDefaultStack(), Hand.MAIN_HAND, new BlockHitResult(crystalPos.toCenterPos(), Direction.UP, placePos, false));
        attackEntity(crystalPos, bot);
    }
    
    private void attackEntity(BlockPos pos, ServerPlayerEntity bot) {
        if (bot == null || bot.getEntityWorld() == null) {
            return;
        }

        World world = bot.getEntityWorld();
        
        List<EndCrystalEntity> crystals = world.getEntitiesByClass(
            EndCrystalEntity.class,
            new net.minecraft.util.math.Box(pos).expand(1.5),
            entity -> true
        );

        if (!crystals.isEmpty()) {
            EndCrystalEntity targetCrystal = crystals.get(0);
            bot.attack(targetCrystal);
        }
    }
    
    private BlockPos findClosestObsidian(BlockPos targetPos) {
        BlockPos[] searchArea = {
            targetPos.add(1, 0, 0), targetPos.add(-1, 0, 0), targetPos.add(0, 0, 1), targetPos.add(0, 0, -1),
            targetPos.add(1, 0, 1), targetPos.add(-1, 0, -1), targetPos.add(1, 0, -1), targetPos.add(-1, 0, 1),
            targetPos.add(1, 1, 0), targetPos.add(-1, 1, 0), targetPos.add(0, 1, 1), targetPos.add(0, 1, -1),
            targetPos.add(1, -1, 0), targetPos.add(-1, -1, 0), targetPos.add(0, -1, 1), targetPos.add(0, -1, -1),
            targetPos.add(2, 0, 0), targetPos.add(-2, 0, 0), targetPos.add(0, 0, 2), targetPos.add(0, 0, -2),
            targetPos.add(2, 1, 0), targetPos.add(-2, 1, 0), targetPos.add(0, 1, 2), targetPos.add(0, 1, -2),
            targetPos.add(2, -1, 0), targetPos.add(-2, -1, 0), targetPos.add(0, -1, 2), targetPos.add(0, -1, -2)
        };
        
        BlockPos closest = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (BlockPos pos : searchArea) {
            if (bot.getEntityWorld().getBlockState(pos).getBlock() == Blocks.OBSIDIAN) {
                if (bot.getEntityWorld().isAir(pos.up()) && !isEntityInBox(getBlockBox(pos.up()))) {
                    double distance = calculate3DDistance(pos, targetPos);
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closest = pos;
                    }
                }
            }
        }
        
        return closest;
    }
    
    private double calculate3DDistance(BlockPos pos1, BlockPos pos2) {
        double dx = pos1.getX() - pos2.getX();
        double dy = pos1.getY() - pos2.getY();
        double dz = pos1.getZ() - pos2.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    private BlockPos findCrystalPlacementPosition(BlockPos targetPos) {
        BlockPos[] candidates = {
            targetPos.add(1, 0, 0), targetPos.add(-1, 0, 0), targetPos.add(0, 0, 1), targetPos.add(0, 0, -1),
            targetPos.add(1, 0, 1), targetPos.add(-1, 0, -1), targetPos.add(1, 0, -1), targetPos.add(-1, 0, 1),
            targetPos.add(1, 1, 0), targetPos.add(-1, 1, 0), targetPos.add(0, 1, 1), targetPos.add(0, 1, -1),
            targetPos.add(1, 1, 1), targetPos.add(-1, 1, -1), targetPos.add(1, 1, -1), targetPos.add(-1, 1, 1),
            targetPos.add(1, -1, 0), targetPos.add(-1, -1, 0), targetPos.add(0, -1, 1), targetPos.add(0, -1, -1),
            targetPos.add(1, -1, 1), targetPos.add(-1, -1, -1), targetPos.add(1, -1, -1), targetPos.add(-1, -1, 1)
        };

        java.util.List<AbstractMap.SimpleEntry<BlockPos, Integer>> scoredCandidates = new ArrayList<>();
        
        for (BlockPos placePos : candidates) {
            int score = scoreCrystalPosition(placePos, targetPos);
            scoredCandidates.add(new AbstractMap.SimpleEntry<>(placePos, score));
        }
        
        scoredCandidates.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        for (AbstractMap.SimpleEntry<BlockPos, Integer> entry : scoredCandidates) {
            BlockPos placePos = entry.getKey();
            if (bot.getEntityWorld().getBlockState(placePos).getBlock() == Blocks.OBSIDIAN
                && bot.getEntityWorld().isAir(placePos.up())
                && !isEntityInBox(getBlockBox(placePos.up()))) {
                return placePos;
            }
        }

        for (AbstractMap.SimpleEntry<BlockPos, Integer> entry : scoredCandidates) {
            BlockPos placePos = entry.getKey();
            if (bot.getEntityWorld().isAir(placePos)
                && !bot.getEntityWorld().isAir(placePos.down())
                && bot.getEntityWorld().isAir(placePos.up())
                && !isEntityInBox(getBlockBox(placePos.up()))) {
                return placePos;
            }
        }

        return null;
    }
    
    private int scoreCrystalPosition(BlockPos placePos, BlockPos targetPos) {
        int targetY = targetPos.getY();
        int botY = bot.getBlockPos().getY();
        int placeY = placePos.getY();
        
        double horizontalDistance = Math.sqrt(
            Math.pow(placePos.getX() - targetPos.getX(), 2) + 
            Math.pow(placePos.getZ() - targetPos.getZ(), 2)
        );
        
        int baseScore;
        
        if (placeY < targetY && placeY > botY) {
            baseScore = 40;
        } else if (placeY == targetY) {
            baseScore = 30;
        } else if (placeY > targetY) {
            baseScore = 20;
        } else {
            baseScore = 10;
        }
        
        return baseScore - (int)(horizontalDistance * 10);
    }
    
    private boolean isLayerReachable(BlockPos pos) {
        double verticalDistance = Math.abs(pos.getY() - bot.getY());
        return verticalDistance <= 2.0;
    }

    private boolean isEntityInBox(net.minecraft.util.math.Box box) {
        return !bot.getEntityWorld()
            .getEntitiesByClass(Entity.class, box, entity -> true)
            .isEmpty();
    }

    private net.minecraft.util.math.Box getBlockBox(BlockPos pos) {
        return new net.minecraft.util.math.Box(
            pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0
        );
    }
}
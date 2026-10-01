// TODO: if the anchor position is not avinable, throw the pearl in. FINISHED
// TODO: if the height difference is higher than 1 block, the bot pearl's in. FINISHED
// TODO: the bot sometimes place the glowstone in the wrong position

package net.zhengzhengyiyi.ai.modes.crystal;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
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
    private int totemCooldown = 2;
    
    // Anchor state machine
    private int anchorStep = 0; // 0 = not started, 1 = anchor placed, 2 = charged, 3 = ready to explode
    private BlockPos anchorPos = null;
    
    // Crystal spamming state
    private BlockPos lastCrystalObsidianPos = null;
    private boolean alternateCrystal = false;
    
    // D-tab state machine (for blast protection armor)
    private int dTabStep = 0; // 0 = not started, 1 = first anchor placed, 2 = first anchor charged, 3 = first anchor exploded, 4 = second anchor placed, 5 = second anchor charged, 6 = second anchor exploded
    private BlockPos dTabPos1 = null;
    private BlockPos dTabPos2 = null;
    private boolean dTabActive = false;
    private int dTabCooldown = 0;

    public CrystalAction(EntityPlayerMPFake bot) {
        this.bot = bot;
    }
    
    public void setBot(EntityPlayerMPFake bot) {
        this.bot = bot;
    }
    
    /**
     * Finds an item in inventory and switches it to the specified hotbar slot
     * @param item The item to find and move
     * @param hotbar The hotbar slot (0-8) to move the item to
     */
    private void moveItem(Item item, int hotbar) {
        // Check if item is already in the target slot
        ItemStack currentStack = bot.getInventory().getStack(hotbar);
        if (currentStack.getItem() == item && currentStack.getCount() > 0) {
            return; // Already has the item
        }
        
        // Search for the item in inventory
        for (int i = 0; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (stack.getItem() == item && stack.getCount() > 0) {
                // Move one item to the target slot
                ItemStack toMove = new ItemStack(item, 1);
                bot.getInventory().setStack(hotbar, toMove);
                
                // Decrease count or clear the source slot
                if (stack.getCount() > 1) {
                    bot.getInventory().setStack(i, new ItemStack(item, stack.getCount() - 1));
                } else {
                    bot.getInventory().setStack(i, ItemStack.EMPTY);
                }
                
                LOGGER.info("Moved {} from inventory slot {} to hotbar slot {}", item, i, hotbar);
                return;
            }
        }
        
        LOGGER.warn("Could not find {} in inventory to move to slot {}", item, hotbar);
    }
    
    /**
     * Restores an item to a slot if the current item runs out
     * @param hotbar The hotbar slot to restore to
     * @param item The item to restore
     */
    private void restoreItemToSlot(int hotbar, Item item) {
        ItemStack currentStack = bot.getInventory().getStack(hotbar);
        if (currentStack.getItem() == item && currentStack.getCount() > 0) {
            return; // Still has the item
        }
        
        moveItem(item, hotbar);
    }
    
    /**
     * Checks if the bot is currently restoring totem (should not move)
     */
    public boolean isRestoringTotem() {
        return totemCooldown > 0;
    }
    
    /**
     * Main tick method for combat actions - called every tick
     */
    public void performCombat(ServerPlayerEntity target, double distance) {
        if (target == null || !target.isAlive() || target.isSpectator()) {
            return;
        }
        
        // Decrease cooldowns
        if (pearlCooldown > 0) pearlCooldown--;
        if (anchorCooldown > 0) anchorCooldown--;
        if (attackCooldown > 0) attackCooldown--;
        if (crystalCooldown > 0) crystalCooldown--;
        if (totemCooldown > 0) totemCooldown--;
        if (dTabCooldown > 0) dTabCooldown--;
        
        // If bot is in totem restoration cooldown, do nothing (including movement)
        if (totemCooldown > 0) {
            return;
        }
        
        // Ensure bot has a totem in offhand
        ensureTotemInOffhand();
        
        double botY = bot.getY();
        double targetY = target.getY();
        double verticalDistance = Math.abs(targetY - botY);
        double horizontalDistance = Math.sqrt(Math.pow(target.getX() - bot.getX(), 2) + Math.pow(target.getZ() - bot.getZ(), 2));
        
        // Check if target has 2+ pieces of blast protection 4 armor for d-tab
        boolean hasBlastProtection = countBlastProtectionArmor(target) >= 2;
        boolean targetAtSameLevel = Math.abs(targetY - botY) < 0.3;
        
        // Activate d-tab if target has blast protection and fell to same level after crystal spamming
        if (hasBlastProtection && targetAtSameLevel && !dTabActive && dTabStep == 0) {
            dTabActive = true;
            LOGGER.info("Crystal - target has blast protection armor, activating d-tab sequence");
        }
        
        // Use pearl to get close to player if far away
        handlePearlUsage(bot, horizontalDistance);
        
        // Continue executing anchor sequence if in progress (must complete once started)
        if (anchorStep > 0 && anchorCooldown == 0 && verticalDistance <= 2.2) {
            executeAnchorSequence();
            anchorCooldown = 1;
        }
        
        // Crystal spamming - TOP PRIORITY (when not in anchor sequence)
        if (distance <= 6.0 && crystalCooldown == 0 && targetY >= botY + 0.7) {
            // TODO: higher than the bot 0.7
            handleCrystalSpamming(target, distance);
        }
        
        // Same height sprint knockback hit
        handleSprintAttack(target, distance, botY, targetY);
        
        // Player under bot - Use priority system: anchor on player level > safe anchor with glowstone > pearl > walking
        if (targetY <= botY - 0.6 && distance <= 5.0 && verticalDistance <= 4.0) {
            handleAnchorPriority(target);
        }
        
        // Execute d-tab sequence if active
        if (dTabActive && dTabStep > 0 && dTabCooldown == 0) {
            executeDTabSequence(target);
        } else if (dTabActive && dTabStep == 0) {
            // Reset if step is 0 but still marked as active
            dTabActive = false;
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
                    totemCooldown = 4;
                    break;
                }
            }
        }
    }
    
    private void handlePearlUsage(EntityPlayerMPFake bot, double horizontalDistance) {
        ItemStack pearls = bot.getInventory().getStack(8);
        boolean hasPearls = pearls.getItem() == Items.ENDER_PEARL && pearls.getCount() > 0;
        
        // Use pearl if horizontal distance is between 3 and 10 blocks
        if (horizontalDistance > 3.0 && horizontalDistance < 10.0 && hasPearls && pearlCooldown == 0) {
            throwPearlTowardsTarget();
        }
    }
    
    /**
     * Throws an ender pearl towards the target without distance restrictions
     * Used for anchor fallbacks when the anchor position is unreachable
     */
    private void throwPearlTowardsTarget() {
        ItemStack pearls = bot.getInventory().getStack(8);
        boolean hasPearls = pearls.getItem() == Items.ENDER_PEARL && pearls.getCount() > 0;
        
        if (!hasPearls || pearlCooldown > 0) {
            return;
        }
        
        // Restore pearls if slot is empty
        restoreItemToSlot(8, Items.ENDER_PEARL);
        
        bot.getInventory().setSelectedSlot(8);
        bot.setCurrentHand(Hand.MAIN_HAND);
        
        // Aim towards target or current facing direction
        float yaw = bot.getYaw();
        bot.setYaw(yaw);
        bot.setPitch(0.0f);
        
        LOGGER.info("Crystal bot throwing ender pearl (anchor fallback)");
        bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), pearls, Hand.MAIN_HAND, new BlockHitResult(bot.getEntityPos(), Direction.UP, bot.getBlockPos(), false));
        pearlCooldown = 1;
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
            // Restore obsidian if slot is empty
            restoreItemToSlot(2, Items.OBSIDIAN);
            // Restore end crystals if slot is empty
            restoreItemToSlot(1, Items.END_CRYSTAL);
            
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
                crystalCooldown = 2;
            } else {
                LOGGER.info("Crystal - placing new obsidian at: {}", placePos);
                placeObsidianAndCrystal(placePos);
                lastCrystalObsidianPos = placePos;
                crystalCooldown = 2;
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
    
    private void handleAnchorPriority(ServerPlayerEntity target) {
        ItemStack anchors = bot.getInventory().getStack(3);
        if (anchors.getItem() == Items.RESPAWN_ANCHOR && anchors.getCount() > 0) {
            // Priority 1: Anchor on player level (directly below target's feet)
            // Player is 2 blocks tall (feet at Y, head at Y+1), so place anchor at Y-1
            BlockPos targetPos = target.getBlockPos();
            BlockPos playerLevelAnchor = targetPos.down();
            
            // Check if position is air, reachable, and not occupied by player
            if (bot.getEntityWorld().isAir(playerLevelAnchor) && 
                isLayerReachable(playerLevelAnchor) &&
                !isPositionOccupiedByPlayer(playerLevelAnchor, target)) {
                if (anchorStep == 0 && anchorCooldown == 0) {
                    anchorPos = playerLevelAnchor;
                    // anchorReachable = true;
                    anchorStep = 1;
                    LOGGER.info("Crystal - Priority 1: anchor on player level at {}", playerLevelAnchor);
                    executeAnchorSequence();
                    anchorCooldown = 1;
                }
                return;
            }
            
            // Priority 2: Safe anchor with glowstone (same y level as bot)
            BlockPos sameLevelAnchor = findSameLevelAnchorPosition(target);
            
            if (sameLevelAnchor != null) {
                if (anchorStep == 0 && anchorCooldown == 0) {
                    anchorPos = sameLevelAnchor;
                    // anchorReachable = false; // Will use glowstone between
                    anchorStep = 1;
                    LOGGER.info("Crystal - Priority 2: safe anchor with glowstone at {}", sameLevelAnchor);
                    executeAnchorSequence();
                    anchorCooldown = 1;
                }
                return;
            }
            
            // Priority 3: Pearl
            LOGGER.info("Crystal - Priority 3: no valid anchor position, using pearl");
            throwPearlTowardsTarget();
            
            // Priority 4: Walking (default movement, handled by BotMovement)
            // No action needed, just continue normal movement
        } else {
            // No anchors in slot, try to restore from inventory
            restoreItemToSlot(3, Items.RESPAWN_ANCHOR);
        }
    }
    
    /**
     * Finds an anchor position at the same y level as the bot
     * Returns null if no valid position found
     */
    private BlockPos findSameLevelAnchorPosition(ServerPlayerEntity target) {
        BlockPos botPos = bot.getBlockPos();
        BlockPos targetPos = target.getBlockPos();
        int y = botPos.getY();
        
        // Candidate positions around the target at bot's y level
        BlockPos[] candidates = {
            targetPos.withY(y),
            targetPos.add(1, 0, 0).withY(y),
            targetPos.add(-1, 0, 0).withY(y),
            targetPos.add(0, 0, 1).withY(y),
            targetPos.add(0, 0, -1).withY(y),
            targetPos.add(1, 0, 1).withY(y),
            targetPos.add(-1, 0, -1).withY(y),
            targetPos.add(1, 0, -1).withY(y),
            targetPos.add(-1, 0, 1).withY(y)
        };
        
        for (BlockPos pos : candidates) {
            // Check if position is air and not occupied by player
            if (bot.getEntityWorld().isAir(pos) && !isPositionOccupiedByPlayer(pos, target)) {
                return pos;
            }
        }
        
        return null;
    }
    
    /**
     * Checks if a block position is occupied by the player
     * Player is 2 blocks tall (feet at Y, head at Y+1)
     */
    private boolean isPositionOccupiedByPlayer(BlockPos pos, ServerPlayerEntity target) {
        BlockPos targetPos = target.getBlockPos();
        
        // Check if the position is at the same x,z as the player
        if (pos.getX() == targetPos.getX() && pos.getZ() == targetPos.getZ()) {
            // Check if the position overlaps with player's feet (Y) or head (Y+1)
            int playerFeetY = targetPos.getY();
            int playerHeadY = targetPos.getY() + 1;
            
            if (pos.getY() == playerFeetY || pos.getY() == playerHeadY) {
                return true;
            }
        }
        
        return false;
    }
    
    private void executeAnchorSequence() {
        // Check if anchor position is still reachable at each step
        // if (anchorPos != null && !isLayerReachable(anchorPos)) {
        //     LOGGER.info("Crystal - anchor position became unreachable during sequence, using pearl instead");
        //     throwPearlTowardsTarget();
        //     // Reset anchor sequence
        //     anchorStep = 0;
        //     anchorPos = null;
        //     return;
        // }

        if (explodeNearbyAnchors()) return;
        if (chargeNearbyAnchors()) return;
        
        // Restore glowstone if slot is empty before charging
        if (anchorStep == 2) {
            restoreItemToSlot(4, Items.GLOWSTONE);
        }
        
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
        
        // Aim at the block face where the anchor will be placed (block below anchor position)
        // This is the block that the anchor will be placed on top of
        BlockPos aimPos = anchorPos.down();
        double dx = aimPos.getX() + 0.5 - bot.getX();
        double dy = aimPos.getY() + 0.5 - bot.getY();
        double dz = aimPos.getZ() + 0.5 - bot.getZ();
        float aimYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float aimPitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        bot.setYaw(aimYaw);
        bot.setPitch(aimPitch);
        
        ItemStack anchorItem = bot.getInventory().getStack(3);
        bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), anchorItem, Hand.MAIN_HAND,
            new BlockHitResult(aimPos.toCenterPos(), Direction.UP, aimPos, false));
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
        
        if (bot.getEntityWorld().getBlockState(anchorPos).getBlock().equals(Blocks.RESPAWN_ANCHOR)) {
            ItemStack glowstoneItem = bot.getInventory().getStack(4);
            bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), glowstoneItem, Hand.MAIN_HAND, 
                new BlockHitResult(anchorPos.toCenterPos(), Direction.UP, anchorPos, false));
            LOGGER.info("Crystal bot charged anchor directly with glowstone");
        }
    }
    
    private void explodeAnchor() {
        bot.getInventory().setSelectedSlot(3);
        bot.setCurrentHand(Hand.MAIN_HAND);
        
        // Aim directly at the anchor to explode it
        double dx = anchorPos.getX() + 0.5 - bot.getX();
        double dy = anchorPos.getY() + 0.5 - bot.getY();
        double dz = anchorPos.getZ() + 0.5 - bot.getZ();
        float aimYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float aimPitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        bot.setYaw(aimYaw);
        bot.setPitch(aimPitch);
        
        ItemStack anchorItem = bot.getInventory().getStack(3);
        bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), anchorItem, Hand.MAIN_HAND, 
            new BlockHitResult(anchorPos.toCenterPos(), Direction.UP, anchorPos, false));
        LOGGER.info("Crystal bot exploded anchor at {}", anchorPos);
    }
    
    /**
     * Explodes existing anchors nearby the bot (within 3 blocks)
     */
    private boolean explodeNearbyAnchors() {
        BlockPos botPos = bot.getBlockPos();
        int searchRadius = 3;
        
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    BlockPos checkPos = botPos.add(x, y, z);
                    if (bot.getEntityWorld().getBlockState(checkPos).getBlock().equals(Blocks.RESPAWN_ANCHOR)) {
                        // Check if anchor is charged (has glowstone charge level > 0)
                        int chargeLevel = bot.getEntityWorld().getBlockState(checkPos).get(RespawnAnchorBlock.CHARGES);
                        if (chargeLevel > 0) {
                            // Explode this anchor
                            bot.getInventory().setSelectedSlot(3);
                            bot.setCurrentHand(Hand.MAIN_HAND);
                            
                            double dx = checkPos.getX() + 0.5 - bot.getX();
                            double dy = checkPos.getY() + 0.5 - bot.getY();
                            double dz = checkPos.getZ() + 0.5 - bot.getZ();
                            float aimYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
                            float aimPitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
                            bot.setYaw(aimYaw);
                            bot.setPitch(aimPitch);
                            
                            ItemStack anchorItem = bot.getInventory().getStack(3);
                            bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), anchorItem, Hand.MAIN_HAND, 
                                new BlockHitResult(checkPos.toCenterPos(), Direction.UP, checkPos, false));
                            LOGGER.info("Crystal bot exploded nearby anchor at {}", checkPos);
                            anchorCooldown = 1;
                            return true; // Only explode one anchor per tick
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean chargeNearbyAnchors() {
        BlockPos botPos = bot.getBlockPos();
        int searchRadius = 3;
        
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    BlockPos checkPos = botPos.add(x, y, z);
                    if (bot.getEntityWorld().getBlockState(checkPos).getBlock().equals(Blocks.RESPAWN_ANCHOR)) {
                        // Check if anchor is charged (has glowstone charge level > 0)
                        int chargeLevel = bot.getEntityWorld().getBlockState(checkPos).get(RespawnAnchorBlock.CHARGES);
                        if (chargeLevel == 0) {
                            // Charge this anchor
                            bot.getInventory().setSelectedSlot(4);
                            bot.setCurrentHand(Hand.MAIN_HAND);
                            
                            double dx = checkPos.getX() + 0.5 - bot.getX();
                            double dy = checkPos.getY() + 0.5 - bot.getY();
                            double dz = checkPos.getZ() + 0.5 - bot.getZ();
                            float aimYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
                            float aimPitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
                            bot.setYaw(aimYaw);
                            bot.setPitch(aimPitch);
                            
                            ItemStack anchorItem = bot.getInventory().getStack(4);
                            bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), anchorItem, Hand.MAIN_HAND, new BlockHitResult(checkPos.toCenterPos(), Direction.UP, checkPos, false));
                            LOGGER.info("Crystal bot charged nearby anchor at {}", checkPos);
                            anchorCooldown = 1;
                            return true; // Only charge one anchor per tick
                        }
                    }
                }
            }
        }
        return false;
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
    
    // private boolean isLayerReachable(BlockPos pos) {
    //     double verticalDistance = Math.abs(pos.getY() - bot.getY());
    //     return verticalDistance <= 2.0;
    // }

    private boolean isLayerReachable(BlockPos pos) {
        World world = bot.getEntityWorld();
        
        // In Yarn 1.21.11, get eye position and target center using Vec3d
        Vec3d eyePos = bot.getEyePos();
        Vec3d targetCenter = Vec3d.ofCenter(pos);
        
        // 1. Distance check: within 3 blocks (3.0 squared = 9.0)
        if (eyePos.squaredDistanceTo(targetCenter) > 9.0) {
            return false;
        }
        
        // 2. Raycast check ("laser" from eyes to target center)
        RaycastContext context = new RaycastContext(
            eyePos,
            targetCenter,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            bot
        );
        
        BlockHitResult hitResult = world.raycast(context);
        
        // If the ray missed everything, the path is completely open
        if (hitResult.getType() == HitResult.Type.MISS) {
            return true;
        }
        
        // If it hit a block, ensure it hits the exact target block
        return hitResult.getBlockPos().equals(pos);
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
    
    /**
     * Counts how many armor pieces have blast protection 4 enchantment
     */
    private int countBlastProtectionArmor(ServerPlayerEntity target) {
        int count = 0;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armor = target.getEquippedStack(slot);
            if (armor != null && !armor.isEmpty()) {
                var enchantments = armor.getEnchantments();
                // Check for blast protection level 4
                // Note: This is a simplified check - actual implementation may need to check enchantment registry
                for (var entry : enchantments.getEnchantments()) {
                    if (entry.toString().contains("blast_protection") || entry.toString().contains("BLAST_PROTECTION")) {
                        count++;
                        break;
                    }
                }
            }
        }
        return count;
    }
    
    /**
     * Executes the d-tab sequence: place 2 anchors after crystal spamming when target has blast protection
     */
    private void executeDTabSequence(ServerPlayerEntity target) {
        switch (dTabStep) {
            case 0:
                // Find first anchor position
                dTabPos1 = findDTabAnchorPosition(target);
                if (dTabPos1 != null) {
                    dTabStep = 1;
                    dTabCooldown = 1;
                    LOGGER.info("D-tab - found first anchor position at {}", dTabPos1);
                } else {
                    LOGGER.info("D-tab - no valid anchor position found, aborting");
                    dTabActive = false;
                }
                break;
                
            case 1:
                // Place first anchor
                if (dTabPos1 != null && dTabCooldown == 0) {
                    placeAnchorAt(dTabPos1);
                    dTabStep = 2;
                    dTabCooldown = 1;
                    LOGGER.info("D-tab - placed first anchor at {}", dTabPos1);
                }
                break;
                
            case 2:
                // Charge first anchor
                if (dTabPos1 != null && dTabCooldown == 0) {
                    anchorPos = dTabPos1;
                    chargeAnchor();
                    dTabStep = 3;
                    dTabCooldown = 1;
                    LOGGER.info("D-tab - charged first anchor at {}", dTabPos1);
                }
                break;
                
            case 3:
                // Explode first anchor
                if (dTabPos1 != null && dTabCooldown == 0) {
                    explodeAnchorAt(dTabPos1);
                    dTabStep = 4;
                    dTabCooldown = 1;
                    LOGGER.info("D-tab - exploded first anchor at {}", dTabPos1);
                }
                break;
                
            case 4:
                // Find second anchor position
                dTabPos2 = findDTabAnchorPosition(target);
                if (dTabPos2 != null && !dTabPos2.equals(dTabPos1)) {
                    dTabStep = 5;
                    dTabCooldown = 1;
                    LOGGER.info("D-tab - found second anchor position at {}", dTabPos2);
                } else {
                    // Can't find second position, end sequence
                    dTabStep = 0;
                    dTabActive = false;
                    dTabPos1 = null;
                    LOGGER.info("D-tab - no second anchor position found, ending sequence");
                }
                break;
                
            case 5:
                // Place second anchor
                if (dTabPos2 != null && dTabCooldown == 0) {
                    placeAnchorAt(dTabPos2);
                    dTabStep = 6;
                    dTabCooldown = 1;
                    LOGGER.info("D-tab - placed second anchor at {}", dTabPos2);
                }
                break;
                
            case 6:
                // Charge second anchor
                if (dTabPos2 != null && dTabCooldown == 0) {
                    anchorPos = dTabPos2;
                    chargeAnchor();
                    dTabStep = 7;
                    dTabCooldown = 1;
                    LOGGER.info("D-tab - charged second anchor at {}", dTabPos2);
                }
                break;
                
            case 7:
                // Explode second anchor
                if (dTabPos2 != null && dTabCooldown == 0) {
                    explodeAnchorAt(dTabPos2);
                    dTabStep = 0;
                    dTabActive = false;
                    dTabPos1 = null;
                    dTabPos2 = null;
                    dTabCooldown = 1;
                    LOGGER.info("D-tab - exploded second anchor at {}, sequence complete", dTabPos2);
                }
                break;
        }
    }
    
    /**
     * Finds an anchor position within 2.5 blocks of the target
     * Includes positions behind the bot for d-tab
     */
    private BlockPos findDTabAnchorPosition(ServerPlayerEntity target) {
        BlockPos targetPos = target.getBlockPos();
        BlockPos botPos = bot.getBlockPos();
        
        // Calculate direction from bot to target
        int dx = targetPos.getX() - botPos.getX();
        int dz = targetPos.getZ() - botPos.getZ();
        
        // Normalize direction
        int directionX = dx != 0 ? dx / Math.abs(dx) : 0;
        int directionZ = dz != 0 ? dz / Math.abs(dz) : 0;
        
        // Candidates: positions around target, including behind bot
        BlockPos[] candidates = {
            targetPos.add(1, 0, 0), targetPos.add(-1, 0, 0), targetPos.add(0, 0, 1), targetPos.add(0, 0, -1),
            targetPos.add(1, 0, 1), targetPos.add(-1, 0, -1), targetPos.add(1, 0, -1), targetPos.add(-1, 0, 1),
            targetPos.add(2, 0, 0), targetPos.add(-2, 0, 0), targetPos.add(0, 0, 2), targetPos.add(0, 0, -2),
            // Add positions behind the bot relative to target
            targetPos.add(-directionX * 2, 0, -directionZ * 2),
            targetPos.add(-directionX * 3, 0, -directionZ * 3),
            targetPos.add(-directionX * 2, 0, 0),
            targetPos.add(0, 0, -directionZ * 2),
            targetPos.add(-directionX * 2, 0, directionZ * 2)
        };
        
        for (BlockPos placePos : candidates) {
            double distance = calculate3DDistance(placePos, targetPos);
            if (distance <= 2.5 && 
                bot.getEntityWorld().isAir(placePos) && 
                isLayerReachable(placePos) &&
                !isPositionOccupiedByPlayer(placePos, target)) {
                return placePos;
            }
        }
        
        return null;
    }
    
    /**
     * Places an anchor at the specified position
     */
    private void placeAnchorAt(BlockPos pos) {
        bot.getInventory().setSelectedSlot(3);
        bot.setCurrentHand(Hand.MAIN_HAND);
        
        // Aim at the block face where the anchor will be placed (block below anchor position)
        BlockPos aimPos = pos.down();
        double dx = aimPos.getX() + 0.5 - bot.getX();
        double dy = aimPos.getY() + 0.5 - bot.getY();
        double dz = aimPos.getZ() + 0.5 - bot.getZ();
        float aimYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float aimPitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        bot.setYaw(aimYaw);
        bot.setPitch(aimPitch);
        
        ItemStack anchorItem = bot.getInventory().getStack(3);
        bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), anchorItem, Hand.MAIN_HAND,
            new BlockHitResult(aimPos.toCenterPos(), Direction.UP, aimPos, false));
    }
    
    /**
     * Explodes an anchor at the specified position
     */
    private void explodeAnchorAt(BlockPos pos) {
        bot.getInventory().setSelectedSlot(3);
        bot.setCurrentHand(Hand.MAIN_HAND);
        
        // Aim directly at the anchor to explode it
        double dx = pos.getX() + 0.5 - bot.getX();
        double dy = pos.getY() + 0.5 - bot.getY();
        double dz = pos.getZ() + 0.5 - bot.getZ();
        float aimYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float aimPitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        bot.setYaw(aimYaw);
        bot.setPitch(aimPitch);
        
        ItemStack anchorItem = bot.getInventory().getStack(3);
        bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), anchorItem, Hand.MAIN_HAND, new BlockHitResult(pos.toCenterPos(), Direction.UP, pos, false));
    }
}

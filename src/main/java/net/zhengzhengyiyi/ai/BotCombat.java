package net.zhengzhengyiyi.ai;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO sometimes the found obsidian position is not placed (entity is already on it)

public class BotCombat {
    private static final Logger LOGGER = LoggerFactory.getLogger("BotCombat");
    
    private EntityPlayerMPFake bot;
    private boolean usingAxe = false;
    private int shieldCooldown = 0;
    private boolean shouldBlock = false;
    private String botType = "smp";
    private String difficulty = "middle";
    private int pearlCooldown = 0;
    private int anchorCooldown = 0;
    private int attackCooldown = 0;
    private int crystalCooldown = 0;

    /**
     * Finds an End Crystal at the given position and makes the fake bot attack it.
     *
     * @param pos The block position where the crystal was placed (or is located).
     * @param bot The fake player entity (ServerPlayerEntity) that will perform the attack.
     */
    @SuppressWarnings("null")
    public void attackEntity(BlockPos pos, ServerPlayerEntity bot) {
        if (bot == null || bot.getEntityWorld() == null) {
            return;
        }

        World world = bot.getEntityWorld();
        
        // End Crystals are entities, not block entities, so we search for entities near the block position.
        // We create a small bounding box around the block to find the crystal.
        // The crystal usually sits on top of the block, so we check a slightly larger area.
        List<EndCrystalEntity> crystals = world.getEntitiesByClass(
            EndCrystalEntity.class,
            new net.minecraft.util.math.Box(pos).expand(1.5), // Expand to catch entities slightly outside the exact block
            entity -> true
        );

        if (!crystals.isEmpty()) {
            // Get the closest crystal or the first one found
            EndCrystalEntity targetCrystal = crystals.get(0);
            
            // Perform the attack
            // Note: In newer versions, attack() might require a hand or specific parameters.
            // Standard interaction often uses interactHand or direct damage. 
            // If bot#attack(Entity) is a custom method in your FakePlayer implementation:
            bot.attack(targetCrystal);
            
            // If using standard Minecraft mechanics, you might need to simulate a hit:
            // targetCrystal.damage(world.getDamageSources().playerAttack(bot), 10.0F);
        }
    }
    
    public BotCombat(EntityPlayerMPFake bot) {
        this.bot = bot;
    }
    
    public void setBotType(String type) {
        this.botType = type;
    }
    
    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }
    
    public void attack(ServerPlayerEntity target, double distance) {
        if (target == null || !target.isAlive()) {
            return;
        }
        
        // Handle mace bot specific combat
        if (botType.equals("mace")) {
            handleMaceCombat(target, distance);
            return;
        }
        
        // Handle crystal bot specific combat
        if (botType.equals("crystal")) {
            handleCrystalCombat(target, distance);
            return;
        }
        
        // Decrease shield cooldown
        if (shieldCooldown > 0) {
            shieldCooldown--;
        }
        
        // Decrease crystal cooldown
        if (crystalCooldown > 0) {
            crystalCooldown--;
        }
        
        // Decrease anchor cooldown
        if (anchorCooldown > 0) {
            anchorCooldown--;
        }
        
        // Decrease attack cooldown
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        
        // Decrease pearl cooldown
        if (pearlCooldown > 0) {
            pearlCooldown--;
        }
        
        // Check if target has shield
        boolean targetHasShield = targetIsUsingShield(target);
        
        // Switch to axe if target has shield
        if (targetHasShield && !usingAxe) {
            switchToAxe();
            usingAxe = true;
        } else if (!targetHasShield && usingAxe) {
            switchToSword();
            usingAxe = false;
        }
        
        // Ensure correct weapon is equipped before attacking
        if (!usingAxe) {
            // Ensure sword is equipped (slot 0)
            if (bot.getInventory().getSelectedSlot() != 0) {
                bot.getInventory().setSelectedSlot(0);
            }
        }
        
        // Check for crit hit (2 blocks away and falling)
        boolean isCrit = distance <= 1.8 && !bot.isOnGround() && bot.fallDistance > 0;
        
        // Only attack if conditions are met (normal attack range)
        if (isCrit || distance <= 2.6) {
            // 5% chance to miss
            if (Math.random() < 0.08) {
                LOGGER.debug("Bot missed attack on {}", target.getName().getString());
                return;
            }
            
            bot.attack(target);
            
            if (isCrit) {
                // LOGGER.info("Bot performed crit hit on {}", target.getName().getString());
                // Raise shield after crit hit with cooldown
                shouldBlock = true;
                shieldCooldown = 40; // 2 seconds cooldown
            }
        }
        
        // Handle shield blocking
        if (shouldBlock && shieldCooldown == 0) {
            // Shield is already in offhand, start using it to block
            ItemStack offhandItem = bot.getEquippedStack(EquipmentSlot.OFFHAND);
            if (offhandItem.getItem() == Items.SHIELD) {
                bot.setCurrentHand(net.minecraft.util.Hand.OFF_HAND);
                shouldBlock = false;
                bot.interactionManager.interactItem(bot, bot.getEntityWorld(), offhandItem, Hand.OFF_HAND);
                LOGGER.info("Bot raised shield");
            }
        }
    }
    
    private void handleMaceCombat(ServerPlayerEntity target, double distance) {
        // Decrease pearl cooldown
        if (pearlCooldown > 0) {
            pearlCooldown--;
        }
        
        // Check if bot should do vertical pearl catch
        double botY = bot.getY();
        double targetY = target.getY();
        boolean isHighAbove = botY > targetY + 15;
        
        // Check if bot has pearls in slot 1
        ItemStack pearls = bot.getInventory().getStack(1);
        boolean hasPearls = pearls.getItem() == Items.ENDER_PEARL && pearls.getCount() > 0;
        
        // If high above target and has pearls, throw pearl vertically
        if (isHighAbove && hasPearls && pearlCooldown == 0) {
            bot.getInventory().setSelectedSlot(1);
            bot.setCurrentHand(Hand.MAIN_HAND);
            
            // Look straight up
            bot.setPitch(-90.0f);
            
            // Use interaction manager to throw pearl
            LOGGER.info("Mace bot attempting to throw ender pearl vertically");
            bot.interactionManager.interactItem(bot, bot.getEntityWorld(), pearls, Hand.MAIN_HAND);
            pearlCooldown = 3;
        }
        
        // Switch to mace when close to target for smash
        if (distance <= 5.0) {
            ItemStack mace = bot.getInventory().getStack(0);
            if (mace.getItem() == Items.MACE) {
                bot.getInventory().setSelectedSlot(0);
                
                // Check for mace smash (falling from height)
                if (bot.fallDistance > 3.0) {
                    bot.attack(target);
                    LOGGER.info("Mace bot performed smash attack with fall distance: {}", bot.fallDistance);
                }
            }
        }
    }
    
    private void handleCrystalCombat(ServerPlayerEntity target, double distance) {
        // Ensure bot is always sprinting
        bot.setSprinting(true);
        
        // Decrease cooldowns
        if (pearlCooldown > 0) {
            pearlCooldown--;
        }
        if (anchorCooldown > 0) {
            anchorCooldown--;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (crystalCooldown > 0) {
            crystalCooldown--;
        }

        // Ensure bot has a totem in offhand for crystal combat survivability.
        // If not present, scan main inventory and move one to offhand.
        ItemStack offhand = bot.getEquippedStack(EquipmentSlot.OFFHAND);
        if (offhand.getItem() != Items.TOTEM_OF_UNDYING) {
            // Search through main inventory slots (0-35)
            for (int i = 0; i < 36; i++) {
                ItemStack stack = bot.getInventory().getStack(i);
                if (stack != null && stack.getItem() == Items.TOTEM_OF_UNDYING && stack.getCount() > 0) {
                    // Move one to offhand
                    ItemStack toMove = new ItemStack(Items.TOTEM_OF_UNDYING, 1);
                    bot.equipStack(EquipmentSlot.OFFHAND, toMove);

                    // Decrease inventory count or clear slot
                    if (stack.getCount() > 1) {
                        bot.getInventory().setStack(i, new ItemStack(stack.getItem(), stack.getCount() - 1));
                    } else {
                        bot.getInventory().setStack(i, ItemStack.EMPTY);
                    }

                    // LOGGER.info("Moved totem from inventory slot {} to offhand for bot {}", i, bot.getName().getString());
                    break;
                }
            }
        }
        
        double botY = bot.getY();
        double targetY = target.getY();
        double verticalDistance = Math.abs(targetY - botY);
        
        // Calculate horizontal distance (2D, not including Y axis)
        double horizontalDistance = Math.sqrt(Math.pow(target.getX() - bot.getX(), 2) + Math.pow(target.getZ() - bot.getZ(), 2));
        
        // Check if bot has pearls in slot 8
        ItemStack pearls = bot.getInventory().getStack(8);
        boolean hasPearls = pearls.getItem() == Items.ENDER_PEARL && pearls.getCount() > 0;
        
        // Use pearl to get close to player if far away (2D horizontal distance > 6 blocks)
        if (horizontalDistance > 6 && hasPearls && pearlCooldown == 0) {
            bot.getInventory().setSelectedSlot(8);
            bot.setCurrentHand(Hand.MAIN_HAND);
            
            // Look at target
            double dx = target.getX() - bot.getX();
            double dz = target.getZ() - bot.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
            bot.setYaw(yaw);
            bot.setPitch(0.0f);
            
            // Use interaction manager to throw pearl
            LOGGER.info("Crystal bot throwing ender pearl to get close (horizontal distance: {})", horizontalDistance);
            bot.interactionManager.interactItem(bot, bot.getEntityWorld(), pearls, Hand.MAIN_HAND);
            pearlCooldown = 2;
        }
        
        // Case 1: Player higher than bot (>= 0.8 blocks) - Place obsidian and crystal
        if (distance <= 5.0 && verticalDistance <= 8.0 && crystalCooldown == 0 && targetY >= botY + 0.8 - 0.5) {
            BlockPos targetPos = target.getBlockPos();
            BlockPos placePos = findCrystalPlacementPosition(targetPos);

            if (placePos != null) {
                if (bot.getEntityWorld().getBlockState(placePos).getBlock() == Blocks.OBSIDIAN) {
                    LOGGER.info("Case 1: Crystal - found existing obsidian at: {}", placePos);
                    placeCrystalOnExistingObsidian(placePos, target);
                } else {
                    LOGGER.info("Case 1: Crystal - placing new obsidian at: {}", placePos);
                    placeObsidianAndCrystal(placePos);
                }
            } else {
                LOGGER.info("Case 1: Crystal - no valid position found (obsidian unavailable or no supported air), skipping");
            }
        }
        
        // Case 2: Same height - Sprint knockback hit (using BlockPos integer comparison with tolerance)
        int targetBlockY = target.getBlockPos().getY();
        int botBlockY = bot.getBlockPos().getY();
        boolean sameHeight = targetBlockY == botBlockY || Math.abs(targetY - botY) < 0.3;
        
        if (distance <= 3.0 && attackCooldown == 0 && sameHeight) {
            ItemStack sword = bot.getInventory().getStack(0);
            if (sword.getItem() == Items.NETHERITE_SWORD) {
                // Only switch to sword if not already selected
                if (bot.getInventory().getSelectedSlot() != 0) {
                    bot.getInventory().setSelectedSlot(0);
                }
                
                // Ensure bot is sprinting for knockback
                bot.setSprinting(true);
                
                // Aim upward for knockback
                bot.setPitch(-30.0f);
                
                LOGGER.info("Case 2: Same height sprint knockback hit (targetY: {}, botY: {}, targetBlockY: {}, botBlockY: {})", targetY, botY, targetBlockY, botBlockY);
                
                bot.attack(target);
                attackCooldown = 2;
                bot.setSprinting(true);
                return;
            }
        }
        
        // Case 3: Player under bot (<= 0.6 blocks below) - Place anchor
        if (targetY <= botY - 0.6 && distance <= 5.0 && verticalDistance <= 4.0 && anchorCooldown == 0) {
            ItemStack anchors = bot.getInventory().getStack(3);
            if (anchors.getItem() == Items.RESPAWN_ANCHOR && anchors.getCount() > 0) {
                BlockPos targetPos = target.getBlockPos();
                BlockPos placePos = targetPos; // Place anchor at same level as target (1 block higher than before)
                
                // Check if placement position is air (cannot place into blocks)
                if (bot.getEntityWorld().isAir(placePos)) {
                    if (isLayerReachable(placePos)) {
                        placeAnchorWithGlowstone(placePos, true);
                    } else {
                        placeAnchorWithGlowstone(placePos, false);
                    }
                    
                    LOGGER.info("Case 3: Anchor - player below by {}", botY - targetY);
                    anchorCooldown = 3;
                } else {
                    LOGGER.info("Case 3: Anchor - cannot place, position not air: {}", placePos);
                }
                return;
            }
        }
    }
    
    private boolean targetIsUsingShield(ServerPlayerEntity target) {
        ItemStack offHandItem = target.getEquippedStack(EquipmentSlot.OFFHAND);
        ItemStack mainHandItem = target.getEquippedStack(EquipmentSlot.MAINHAND);
        
        boolean hasShield = offHandItem.getItem() == Items.SHIELD || mainHandItem.getItem() == Items.SHIELD;
        
        // Check if player is actively blocking (using the shield)
        boolean isBlocking = target.isUsingItem() && 
                           (target.getActiveItem().getItem() == Items.SHIELD);
        
        return hasShield && isBlocking;
    }
    
    private void switchToAxe() {
        ItemStack axe = bot.getInventory().getStack(1);
        if (axe.getItem() == Items.NETHERITE_AXE) {
            bot.getInventory().setSelectedSlot(1);
            LOGGER.info("Bot switched to axe to break shield");
        }
    }
    
    private void placeObsidianAndCrystal(BlockPos placePos) {
        // Switch to obsidian slot (slot 2)
        bot.getInventory().setSelectedSlot(2);
        bot.setCurrentHand(Hand.MAIN_HAND);
        
        // Look at the placement position (center of the block)
        double dx = placePos.getX() + 0.5 - bot.getX();
        double dy = placePos.getY() + 0.5 - bot.getY();
        double dz = placePos.getZ() + 0.5 - bot.getZ();
        float aimYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float aimPitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        bot.setYaw(aimYaw);
        bot.setPitch(aimPitch);
        
        // Place obsidian using interactBlock
        if (bot.getEntityWorld().isAir(placePos) || bot.getEntityWorld().getBlockState(placePos).getBlock() == Blocks.SHORT_GRASS || bot.getEntityWorld().getBlockState(placePos).getBlock() == Blocks.TALL_DRY_GRASS || bot.getEntityWorld().getBlockState(placePos).getBlock() == Blocks.SHORT_DRY_GRASS || bot.getEntityWorld().getBlockState(placePos).getBlock() == Blocks.TALL_GRASS) {
            bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), Items.OBSIDIAN.getDefaultStack(), Hand.MAIN_HAND, new BlockHitResult(placePos.toCenterPos(), Direction.UP, placePos.down(), false));
            LOGGER.info("Crystal bot placed obsidian at {}", placePos);
        }
        
        // Switch to crystal slot (slot 1)
        bot.getInventory().setSelectedSlot(1);
        bot.setCurrentHand(Hand.MAIN_HAND);
        
        // Re-aim at the top of the obsidian for crystal placement
        BlockPos crystalPos = placePos.add(0, 1, 0);
        double cdx = crystalPos.getX() + 0.5 - bot.getX();
        double cdy = crystalPos.getY() + 0.5 - bot.getY();
        double cdz = crystalPos.getZ() + 0.5 - bot.getZ();
        float cYaw = (float) Math.toDegrees(Math.atan2(cdz, cdx)) - 90.0f;
        float cPitch = (float) Math.toDegrees(Math.atan2(-cdy, Math.sqrt(cdx * cdx + cdz * cdz)));
        bot.setYaw(cYaw);
        bot.setPitch(cPitch);

        // Place crystal on top of obsidian (aiming at the top face)
        bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), Items.END_CRYSTAL.getDefaultStack(), Hand.MAIN_HAND, new BlockHitResult(crystalPos.toCenterPos(), Direction.UP, placePos, false));
        // bot.getEntityWorld().createExplosion(bot, crystalPos.getX(), crystalPos.getY(), crystalPos.getZ(), 6, ExplosionSourceType.TNT);
        attackEntity(crystalPos, bot);
    }
    
    private void placeCrystalOnExistingObsidian(BlockPos obsidianPos, ServerPlayerEntity target) {
        if (!isValidObsidianCrystalSupport(obsidianPos)) {
            BlockPos fallbackPos = findCrystalPlacementPosition(target.getBlockPos());
            if (fallbackPos == null) {
                LOGGER.info("Case 1: Crystal - existing obsidian {} invalid and no fallback found", obsidianPos);
                return;
            }

            if (bot.getEntityWorld().getBlockState(fallbackPos).getBlock() == Blocks.OBSIDIAN) {
                LOGGER.info("Case 1: Crystal - fallback existing obsidian found at {}", fallbackPos);
                placeCrystalOnExistingObsidian(fallbackPos, target);
            } else {
                LOGGER.info("Case 1: Crystal - fallback placing new obsidian at {}", fallbackPos);
                placeObsidianAndCrystal(fallbackPos);
            }
            return;
        }

        // Switch to crystal slot (slot 1)
        bot.getInventory().setSelectedSlot(1);
        bot.setCurrentHand(Hand.MAIN_HAND);
        
        // Look at the top of the obsidian position (where crystal will be placed)
        BlockPos crystalPos = obsidianPos.add(0, 1, 0);
        double dx = crystalPos.getX() + 0.5 - bot.getX();
        double dy = crystalPos.getY() + 0.5 - bot.getY();
        double dz = crystalPos.getZ() + 0.5 - bot.getZ();
        float aimYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float aimPitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        bot.setYaw(aimYaw);
        bot.setPitch(aimPitch);
        
        // Place crystal on top of existing obsidian (aiming at the top face)
        bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), Items.END_CRYSTAL.getDefaultStack(), Hand.MAIN_HAND, new BlockHitResult(crystalPos.toCenterPos(), Direction.UP, obsidianPos, false));
        // bot.getEntityWorld().createExplosion(bot, crystalPos.getX(), crystalPos.getY(), crystalPos.getZ(), 6, ExplosionSourceType.TNT);
        attackEntity(crystalPos, bot);
    }
    
    private boolean isLayerReachable(BlockPos pos) {
        // Check if the layer is reachable from bot
        // Simple implementation: check if vertical distance is <= 2 blocks
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

    private boolean isValidObsidianCrystalSupport(BlockPos obsidianPos) {
        return bot.getEntityWorld().getBlockState(obsidianPos).getBlock() == Blocks.OBSIDIAN
            && bot.getEntityWorld().isAir(obsidianPos.up())
            && !isEntityInBox(getBlockBox(obsidianPos.up()));
    }

    private BlockPos findCrystalPlacementPosition(BlockPos targetPos) {
        BlockPos[] candidates = {
            // Same Y level - adjacent
            targetPos.add(1, 0, 0),
            targetPos.add(-1, 0, 0),
            targetPos.add(0, 0, 1),
            targetPos.add(0, 0, -1),
            // Same Y level - diagonal
            targetPos.add(1, 0, 1),
            targetPos.add(-1, 0, -1),
            targetPos.add(1, 0, -1),
            targetPos.add(-1, 0, 1),
            // One block above target
            targetPos.add(1, 1, 0),
            targetPos.add(-1, 1, 0),
            targetPos.add(0, 1, 1),
            targetPos.add(0, 1, -1),
            targetPos.add(1, 1, 1),
            targetPos.add(-1, 1, -1),
            targetPos.add(1, 1, -1),
            targetPos.add(-1, 1, 1),
            // One block below target
            targetPos.add(1, -1, 0),
            targetPos.add(-1, -1, 0),
            targetPos.add(0, -1, 1),
            targetPos.add(0, -1, -1),
            targetPos.add(1, -1, 1),
            targetPos.add(-1, -1, -1),
            targetPos.add(1, -1, -1),
            targetPos.add(-1, -1, 1)
        };

        // Prefer existing valid obsidian first.
        for (BlockPos placePos : candidates) {
            if (isValidObsidianCrystalSupport(placePos)) {
                return placePos;
            }
        }

        // Fallback to placing new obsidian at a supported air spot.
        for (BlockPos placePos : candidates) {
            if (bot.getEntityWorld().isAir(placePos)
                && !bot.getEntityWorld().isAir(placePos.down())
                && bot.getEntityWorld().isAir(placePos.up())
                && !isEntityInBox(getBlockBox(placePos.up()))) {
                return placePos;
            }
        }

        return null;
    }
    
    private void placeAnchorWithGlowstone(BlockPos placePos, boolean reachable) {
        // Look at the placement position
        double dx = placePos.getX() + 1.5 - bot.getX();
        double dy = placePos.getY() + 1.5 - bot.getY();
        double dz = placePos.getZ() + 1.5 - bot.getZ();
        float aimYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float aimPitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        bot.setYaw(aimYaw);
        bot.setPitch(aimPitch);
        
        if (reachable) {
            // Reachable - place anchor, then use anchor
            bot.getInventory().setSelectedSlot(3);
            bot.setCurrentHand(Hand.MAIN_HAND);
            
            // Place anchor using interactBlock
            bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), Items.RESPAWN_ANCHOR.getDefaultStack(), Hand.MAIN_HAND, new BlockHitResult(placePos.toCenterPos(), Direction.UP, placePos.down(), false));
            LOGGER.info("Crystal bot placed respawn anchor at {}", placePos);
            
            // Charge anchor with glowstone
            bot.getInventory().setSelectedSlot(4);
            bot.setCurrentHand(Hand.MAIN_HAND);
            bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), Items.GLOWSTONE.getDefaultStack(), Hand.MAIN_HAND, new BlockHitResult(placePos.toCenterPos(), Direction.UP, placePos, false));
            LOGGER.info("Crystal bot charged anchor with glowstone");
            
            // Switch to first slot and use anchor
            bot.getInventory().setSelectedSlot(0);
            bot.setCurrentHand(Hand.MAIN_HAND);
            LOGGER.info("Crystal bot using anchor");
        } else {
            // Not reachable - place anchor, place glowstone between anchor and bot, use anchor
            bot.getInventory().setSelectedSlot(3);
            bot.setCurrentHand(Hand.MAIN_HAND);
            
            // Place anchor using interactBlock
            bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), Items.RESPAWN_ANCHOR.getDefaultStack(), Hand.MAIN_HAND, new BlockHitResult(placePos.toCenterPos(), Direction.UP, placePos.down(), false));
            LOGGER.info("Crystal bot placed respawn anchor at {}", placePos);
            
            // Place glowstone between anchor and bot
            BlockPos glowstonePos = placePos.add(0, 1, 0); // Place above anchor
            double gdx = glowstonePos.getX() + 0.5 - bot.getX();
            double gdy = glowstonePos.getY() + 0.5 - bot.getY();
            double gdz = glowstonePos.getZ() + 0.5 - bot.getZ();
            float gYaw = (float) Math.toDegrees(Math.atan2(gdz, gdx)) - 90.0f;
            float gPitch = (float) Math.toDegrees(Math.atan2(-gdy, Math.sqrt(gdx * gdx + gdz * gdz)));
            bot.setYaw(gYaw);
            bot.setPitch(gPitch);
            
            bot.getInventory().setSelectedSlot(4);
            bot.setCurrentHand(Hand.MAIN_HAND);
            if (bot.getEntityWorld().isAir(glowstonePos)) {
                bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), Items.GLOWSTONE.getDefaultStack(), Hand.MAIN_HAND, new BlockHitResult(glowstonePos.toCenterPos(), Direction.UP, placePos, false));
                LOGGER.info("Crystal bot placed glowstone between anchor and bot at {}", glowstonePos);
            }
            
            // Charge anchor with glowstone after placing it or when using direct charge.
            chargeRespawnAnchor(placePos);
            LOGGER.info("Crystal bot charged anchor with glowstone");
            
            // Use anchor
            bot.getInventory().setSelectedSlot(0);
            bot.setCurrentHand(Hand.MAIN_HAND);
            bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), Items.AIR.getDefaultStack(), Hand.MAIN_HAND, new BlockHitResult(placePos.toCenterPos(), Direction.UP, placePos, false));
            LOGGER.info("Crystal bot using anchor");
        }
    }
    
    private void switchToSword() {
        ItemStack sword = bot.getInventory().getStack(0);
        if (sword.getItem() == Items.NETHERITE_SWORD) {
            bot.getInventory().setSelectedSlot(0);
            LOGGER.info("Bot switched to sword");
        }
    }

    private void chargeRespawnAnchor(BlockPos anchorPos) {
        bot.getInventory().setSelectedSlot(4);
        bot.setCurrentHand(Hand.MAIN_HAND);

        double dx = anchorPos.getX() + 0.5 - bot.getX();
        double dy = anchorPos.getY() + 0.5 - bot.getY();
        double dz = anchorPos.getZ() + 0.5 - bot.getZ();
        float aimYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float aimPitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        bot.setYaw(aimYaw);
        bot.setPitch(aimPitch);

        bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), Items.GLOWSTONE.getDefaultStack(), Hand.MAIN_HAND,
                new BlockHitResult(anchorPos.toCenterPos(), Direction.UP, anchorPos, false));
    }
    
    public void setBot(EntityPlayerMPFake bot) {
        this.bot = bot;
    }
}

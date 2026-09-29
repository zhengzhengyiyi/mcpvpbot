package net.zhengzhengyiyi.ai;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

public class BotMovement {
//    private static final Logger LOGGER = LoggerFactory.getLogger("BotMovement");
    
    private EntityPlayerMPFake bot;
    private int strafeDirection = 1;
    private int strafeTimer = 0;
    private String botType = "smp";
    private String difficulty = "middle";
    private int waterBucketCooldown = 0;
    // pending placement state: used to delay placing a block until the bot is falling
    private boolean pendingPlaceAfterJump = false;
    private BlockPos pendingPlacePos = null;
    private int pendingPlaceTimeout = 0;
    
    public BotMovement(EntityPlayerMPFake bot) {
        this.bot = bot;
    }
    
    public void setBotType(String type) {
        this.botType = type;
    }
    
    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }
    
    public void moveTowards(ServerPlayerEntity target) {
        if (target == null || !target.isAlive()) {
            return;
        }
        
        // Handle mace bot elytra flight
        if (botType.equals("mace")) {
            handleMaceMovement(target);
            return;
        }
        
        // Handle crystal bot movement (pearl to get close)
        if (botType.equals("crystal")) {
            handleCrystalMovement(target);
            return;
        }
        
        // Calculate bot position
        double botX = bot.getX();
        double botY = bot.getY();
        double botZ = bot.getZ();
        
        // Calculate target position
        double targetX = target.getX();
        double targetY = target.getY();
        double targetZ = target.getZ();
        
        // Calculate distance (using all 3 axes: x, y, z)
        double dx = targetX - botX;
        double dy = targetY - botY;
        double dz = targetZ - botZ;
        double fullDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz); // Keep horizontal for movement calculations

        // If a pending placement was scheduled (from detecting a tall obstacle),
        // only place when the bot is falling (velocity Y < 0). Timeout after a short window.
        if (pendingPlaceAfterJump) {
            pendingPlaceTimeout--;
            if (pendingPlaceTimeout <= 0) {
                pendingPlaceAfterJump = false;
                pendingPlacePos = null;
            } else {
                Vec3d vel = bot.getVelocity();
                double velY = vel != null ? vel.y : 0.0;
                if (!bot.isOnGround() && velY < 0) {
                    if (pendingPlacePos != null && bot.getEntityWorld().isAir(pendingPlacePos)) {
                        bot.getInventory().setSelectedSlot(2);
                        bot.setCurrentHand(Hand.MAIN_HAND);
                        bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), Items.OBSIDIAN.getDefaultStack(), Hand.MAIN_HAND,
                                new net.minecraft.util.hit.BlockHitResult(pendingPlacePos.toCenterPos(), net.minecraft.util.math.Direction.UP, pendingPlacePos.down(), false));
//                        LOGGER.info("Crystal bot placed pending obsidian at {}", pendingPlacePos);
                    }

                    pendingPlaceAfterJump = false;
                    pendingPlacePos = null;
                }
            }
        }
        
        // Handle web detection for SMP bot
        if (botType.equals("smp")) {
            handleWebDetection();
        }
        
        // Look at target
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) Math.toDegrees(Math.atan2(-(targetY - botY), horizontalDistance));
        bot.setYaw(yaw);
        bot.setPitch(pitch);
        
        // Always sprint
        bot.setSprinting(true);
        
        // Update strafe timer
        strafeTimer++;
        if (strafeTimer > 20) { // Change direction every 20 ticks (1 second)
            strafeDirection *= -1;
            strafeTimer = 0;
        }
        
        // Move forward by directly modifying position with sprint speed and strafe
        if (horizontalDistance > 1.5) {
            double speed = 0.28; // Sprint speed
            double strafeAmount = 0.1; // Default strafing
            
            // Adjust strafing based on difficulty (only affects smp and netherite)
            if ((botType.equals("smp") || botType.equals("netherite")) && difficulty.equals("easy")) {
                strafeAmount = 0.0; // No strafing in easy mode
            }
            
            // Hard mode behavior for smp and netherite
            double optimalDistance = 2.7;
            double attackRange = 2.6;
            if ((botType.equals("smp") || botType.equals("netherite")) && difficulty.equals("hard")) {
                if (fullDistance > attackRange + 1.0) {
                    // Player is far away, chase with no strafe
                    strafeAmount = 0.0;
                    speed = 0.32; // Move faster when chasing
                } else if (fullDistance < optimalDistance) {
                    // Too close, stop moving forward (maintain distance for crit spam)
                    speed = 0.0;
                    // Strafe faster when at optimal distance (not critting)
                    strafeAmount = 0.15;
                } else {
                    // At optimal distance range, strafe faster when not critting
                    strafeAmount = 0.15;
                }
            }
            
            // Forward movement
            double moveX = Math.cos(Math.toRadians(yaw + 90)) * speed;
            double moveZ = Math.sin(Math.toRadians(yaw + 90)) * speed;
            
            // Strafe movement (perpendicular to forward)
            double strafeX = Math.cos(Math.toRadians(yaw)) * strafeAmount * strafeDirection;
            double strafeZ = Math.sin(Math.toRadians(yaw)) * strafeAmount * strafeDirection;
            
            Vec3d newPos = new Vec3d(botX + moveX + strafeX, botY, botZ + moveZ + strafeZ);
            bot.setPosition(newPos);
            
//            LOGGER.debug("Moving bot from ({}, {}, {}) to ({}, {}, {})", botX, botY, botZ, newPos.x, newPos.y, newPos.z);
        }
        
        // Jump only when 2 blocks away or less from player
        // In hard mode, jump at optimal distance for crit spam (only for smp and netherite)
        if (botType.equals("smp") || botType.equals("netherite")) {
            double jumpDistance = 1.1;
            if (difficulty.equals("hard")) {
                // In hard mode, jump when at optimal distance for crit spam
                // Jump when between 2.5 and 2.8 blocks to maintain crit range
                if (fullDistance <= 1.4 && bot.isOnGround()) {
                    bot.jump();
                }
            } else {
                // Normal/easy mode: jump when close
                if (fullDistance <= jumpDistance && bot.isOnGround()) {
                    bot.jump();
                }
            }
//            LOGGER.debug("Bot jumping at distance: {}", fullDistance);
        }
    }
    
    private void handleMaceMovement(ServerPlayerEntity target) {
        double botX = bot.getX();
        double botY = bot.getY();
        double botZ = bot.getZ();
        double targetX = target.getX();
        double targetY = target.getY();
        double targetZ = target.getZ();
        
        // Calculate full distance
        double dx = targetX - botX;
        double dy = targetY - botY;
        double dz = targetZ - botZ;
        double fullDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        
        // Check if bot should use elytra (if in air or target is above)
        ItemStack chestItem = bot.getEquippedStack(EquipmentSlot.CHEST);
        boolean hasElytra = chestItem.getItem() == Items.ELYTRA;
        
        if (hasElytra && (!bot.isOnGround() || targetY > botY + 5)) {
            // Start elytra flight by jumping first
            if (bot.isOnGround()) {
                bot.jump();
            }
            
            // Elytra is in chest slot, need to activate it differently
            // For now, just set velocity to simulate flight
            
            // Calculate direction
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
            float pitch = (float) Math.toDegrees(Math.atan2(-dy, horizontalDistance));
            
            bot.setYaw(yaw);
            bot.setPitch(pitch);
            
            // Set velocity for elytra flight
            double speed = 1.5;
            double velX = Math.cos(Math.toRadians(yaw + 90)) * speed;
            double velZ = Math.sin(Math.toRadians(yaw + 90)) * speed;
            double velY = Math.sin(Math.toRadians(-pitch)) * speed * 0.5;
            
            bot.setVelocity(velX, velY, velZ);
            
//            LOGGER.debug("Mace bot attempting elytra flight with velocity ({}, {}, {})", velX, velY, velZ);
        } else {
            // Regular movement when not flying (inline to avoid recursion)
            // Look at target
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
            float pitch = (float) Math.toDegrees(Math.atan2(-(targetY - botY), horizontalDistance));
            bot.setYaw(yaw);
            bot.setPitch(pitch);
            
            // Always sprint
            bot.setSprinting(true);
            
            // Update strafe timer
            strafeTimer++;
            if (strafeTimer > 20) {
                strafeDirection *= -1;
                strafeTimer = 0;
            }
            
            // Move forward by directly modifying position with sprint speed and strafe
            if (horizontalDistance > 1.5) {
                double speed = 0.28;
                double strafeAmount = 0.13;
                
                // Adjust strafing based on difficulty (only affects smp and netherite)
                if ((botType.equals("smp") || botType.equals("netherite")) && difficulty.equals("easy")) {
                    strafeAmount = 0.0; // No strafing in easy mode
                }
                
                // Hard mode behavior for smp and netherite
                double optimalDistance = 2.7;
                double attackRange = 2.6;
                if ((botType.equals("smp") || botType.equals("netherite")) && difficulty.equals("hard")) {
                    if (fullDistance > attackRange + 1.0) {
                        // Player is far away, chase with no strafe
                        strafeAmount = 0.0;
                        speed = 0.32; // Move faster when chasing
                    } else if (fullDistance < optimalDistance) {
                        // Too close, stop moving forward (maintain distance for crit spam)
                        speed = 0.0;
                        // Strafe faster when at optimal distance (not critting)
                        strafeAmount = 0.15;
                    } else {
                        // At optimal distance range, strafe faster when not critting
                        strafeAmount = 0.15;
                    }
                }
                
                double moveX = Math.cos(Math.toRadians(yaw + 90)) * speed;
                double moveZ = Math.sin(Math.toRadians(yaw + 90)) * speed;
                
                double strafeX = Math.cos(Math.toRadians(yaw)) * strafeAmount * strafeDirection;
                double strafeZ = Math.sin(Math.toRadians(yaw)) * strafeAmount * strafeDirection;
                
                Vec3d newPos = new Vec3d(botX + moveX + strafeX, botY, botZ + moveZ + strafeZ);
                bot.setPosition(newPos);
                
//                LOGGER.debug("Mace bot moving from ({}, {}, {}) to ({}, {}, {})", botX, botY, botZ, newPos.x, newPos.y, newPos.z);
            }
            
            // Jump only when 2 blocks away or less from player
            // Mace bot is not affected by difficulty settings
            if (fullDistance <= 2.0 && bot.isOnGround()) {
                bot.jump();
//                LOGGER.debug("Mace bot jumping at distance: {}", fullDistance);
            }
        }
    }
    
    private void handleCrystalMovement(ServerPlayerEntity target) {
        double botX = bot.getX();
        double botY = bot.getY();
        double botZ = bot.getZ();
        double targetX = target.getX();
        double targetY = target.getY();
        double targetZ = target.getZ();
        
        double dx = targetX - botX;
        double dy = targetY - botY;
        double dz = targetZ - botZ;
        double fullDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        // Check for tall obstacles between bot and target.
        // If any sampled column between bot and target has a top surface higher than bot by > 1.2,
        // make the bot jump and place obsidian under itself (if air).
        int steps = Math.max(1, (int)Math.ceil(horizontalDistance));
        for (int i = 1; i <= steps; i++) {
            double frac = (double)i / (double)steps;
            double sx = botX + dx * frac;
            double sz = botZ + dz * frac;
            int sampleX = (int)Math.floor(sx);
            int sampleZ = (int)Math.floor(sz);

            int botBlockY = bot.getBlockPos().getY();
            int upperY = botBlockY + 10;
            int lowerY = botBlockY - 5;
            int highestY = Integer.MIN_VALUE;
            for (int y = upperY; y >= lowerY; y--) {
                BlockPos check = new BlockPos(sampleX, y, sampleZ);
                if (!bot.getEntityWorld().isAir(check)) {
                    highestY = y;
                    break;
                }
            }

            if (highestY == Integer.MIN_VALUE) continue;
            double topY = highestY + 1.0; // top surface
            double heightDiff = topY - botY;
            if (heightDiff > 1.2) {
                // Obstacle too tall: jump and schedule obsidian placement only when falling.
                if (bot.isOnGround()) {
                    bot.jump();
                }

                // Determine the block position under the bot to place into when falling
                BlockPos placePos = bot.getBlockPos().down();

                // If we're already in the air and falling, place immediately
                Vec3d vel = bot.getVelocity();
                double velY = vel != null ? vel.y : 0.0;
                if (!bot.isOnGround() && velY < 0 && bot.getEntityWorld().isAir(placePos)) {
                    bot.getInventory().setSelectedSlot(2);
                    bot.setCurrentHand(Hand.MAIN_HAND);
                    bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), Items.OBSIDIAN.getDefaultStack(), Hand.MAIN_HAND,
                            new net.minecraft.util.hit.BlockHitResult(placePos.toCenterPos(), net.minecraft.util.math.Direction.UP, placePos.down(), false));
//                    LOGGER.info("Crystal bot detected tall obstacle and placed obsidian at {} while falling", placePos);
                } else {
                    // Otherwise schedule placing once the bot starts falling
                    pendingPlaceAfterJump = true;
                    pendingPlacePos = placePos;
                    pendingPlaceTimeout = 20; // ~1 second window to place while falling
//                    LOGGER.debug("Crystal bot scheduled pending obsidian at {}", placePos);
                }

                break; // only perform once per move tick
            }
        }
        
        // Look at target
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) Math.toDegrees(Math.atan2(-(targetY - botY), horizontalDistance));
        bot.setYaw(yaw);
        bot.setPitch(pitch);
        
        // Always sprint
        bot.setSprinting(true);
        
        // Update strafe timer
        strafeTimer++;
        if (strafeTimer > 20) {
            strafeDirection *= -1;
            strafeTimer = 0;
        }
        
        // Move forward by directly modifying position with sprint speed and strafe
        if (horizontalDistance > 1.5) {
            double speed = 0.28;
            double strafeAmount = 0.13;
            
            // Adjust strafing based on difficulty (only affects smp and netherite)
            if ((botType.equals("smp") || botType.equals("netherite")) && difficulty.equals("easy")) {
                strafeAmount = 0.0; // No strafing in easy mode
            }
            
            // Hard mode behavior for smp and netherite
            double optimalDistance = 2.7;
            double attackRange = 2.6;
            if ((botType.equals("smp") || botType.equals("netherite")) && difficulty.equals("hard")) {
                if (fullDistance > attackRange + 1.0) {
                    // Player is far away, chase with no strafe
                    strafeAmount = 0.0;
                    speed = 0.32; // Move faster when chasing
                } else if (fullDistance < optimalDistance) {
                    // Too close, stop moving forward (maintain distance for crit spam)
                    speed = 0.0;
                    // Strafe faster when at optimal distance (not critting)
                    strafeAmount = 0.15;
                } else {
                    // At optimal distance range, strafe faster when not critting
                    strafeAmount = 0.15;
                }
            }
            
            double moveX = Math.cos(Math.toRadians(yaw + 90)) * speed;
            double moveZ = Math.sin(Math.toRadians(yaw + 90)) * speed;
            
            double strafeX = Math.cos(Math.toRadians(yaw)) * strafeAmount * strafeDirection;
            double strafeZ = Math.sin(Math.toRadians(yaw)) * strafeAmount * strafeDirection;
            
            Vec3d newPos = new Vec3d(botX + moveX + strafeX, botY, botZ + moveZ + strafeZ);
            bot.setPosition(newPos);
            
//            LOGGER.debug("Crystal bot moving from ({}, {}, {}) to ({}, {}, {})", botX, botY, botZ, newPos.x, newPos.y, newPos.z);
        }
        
        // Crystal bot does not jump
    }
    
    public void setBot(EntityPlayerMPFake bot) {
        this.bot = bot;
    }
    
    private void handleWebDetection() {
        // Decrease water bucket cooldown
        if (waterBucketCooldown > 0) {
            waterBucketCooldown--;
        }
        
        // Check if bot is in a web
        BlockPos botPos = new BlockPos((int) bot.getX(), (int) bot.getY(), (int) bot.getZ());
        BlockPos feetPos = botPos.down();
        BlockPos headPos = botPos.up();
        
        boolean inWebAtFeet = bot.getEntityWorld().getBlockState(feetPos).getBlock() == Blocks.COBWEB;
        boolean inWebAtBody = bot.getEntityWorld().getBlockState(botPos).getBlock() == Blocks.COBWEB;
        boolean inWebAtHead = bot.getEntityWorld().getBlockState(headPos).getBlock() == Blocks.COBWEB;
        
        int webCount = (inWebAtFeet ? 1 : 0) + (inWebAtBody ? 1 : 0) + (inWebAtHead ? 1 : 0);
        
        if (webCount > 0) {
//            LOGGER.info("Bot detected web at {} positions: feet={}, body={}, head={}", webCount, inWebAtFeet, inWebAtBody, inWebAtHead);
            
            // If mostly in web (2 or more positions), use water bucket
            if (webCount >= 2 && waterBucketCooldown == 0) {
                ItemStack waterBucket = bot.getInventory().getStack(2);
                if (waterBucket.getItem() == Items.WATER_BUCKET && waterBucket.getCount() > 0) {
                    // Place water bucket
                    bot.getInventory().setSelectedSlot(2);
                    bot.setCurrentHand(Hand.MAIN_HAND);
                    
                    // Place water at feet position
                    BlockPos waterPos = feetPos;
                    bot.getEntityWorld().setBlockState(waterPos, Blocks.WATER.getDefaultState());
                    
//                    LOGGER.info("Bot placed water bucket at {}", waterPos);
                    
                    // Set cooldown and mark for pickup
                    waterBucketCooldown = 5; // Short cooldown before pickup
                }
            } else if (webCount == 1) {
                // Only slightly stuck - try to move out by jumping
                if (bot.isOnGround()) {
                    bot.jump();
//                    LOGGER.info("Bot jumping to escape web");
                }
            }
        }
        
        // Pick up water after cooldown expires
        if (waterBucketCooldown == 1) {
            BlockPos pickupPos = new BlockPos((int) bot.getX(), (int) bot.getY() - 1, (int) bot.getZ());
            if (bot.getEntityWorld().getBlockState(pickupPos).getBlock() == Blocks.WATER) {
                // Pick up water (replace with empty bucket)
                ItemStack emptyBucket = new ItemStack(Items.BUCKET, 1);
                bot.getInventory().setStack(2, emptyBucket);
                bot.getEntityWorld().setBlockState(pickupPos, Blocks.AIR.getDefaultState());
                
//                LOGGER.info("Bot picked up water at {}", pickupPos);
            }
        }
    }
}

package net.zhengzhengyiyi.ai.modes.crystal;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Crystal bot movement behavior
 */
public class CrystalMovement {
    private EntityPlayerMPFake bot;
    private int strafeDirection = 1;
    private int strafeTimer = 0;
    
    // pending placement state: used to delay placing a block until the bot is falling
    private boolean pendingPlaceAfterJump = false;
    private BlockPos pendingPlacePos = null;
    private int pendingPlaceTimeout = 0;

    public CrystalMovement(EntityPlayerMPFake bot) {
        this.bot = bot;
    }
    
    public void setBot(EntityPlayerMPFake bot) {
        this.bot = bot;
    }
    
    public void moveTowards(ServerPlayerEntity target) {
        if (target == null || !target.isAlive()) {
            return;
        }
        
        handleCrystalMovement(target);
        
        // Handle pending obsidian placement
        if (pendingPlaceAfterJump && pendingPlaceTimeout > 0) {
            pendingPlaceTimeout--;
            
            Vec3d vel = bot.getVelocity();
            double velY = vel != null ? vel.y : 0.0;
            
            if (!bot.isOnGround() && velY < 0 && pendingPlacePos != null && bot.getEntityWorld().isAir(pendingPlacePos)) {
                bot.getInventory().setSelectedSlot(2);
                bot.setCurrentHand(Hand.MAIN_HAND);
                bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), Items.OBSIDIAN.getDefaultStack(), Hand.MAIN_HAND,
                        new net.minecraft.util.hit.BlockHitResult(pendingPlacePos.toCenterPos(), net.minecraft.util.math.Direction.UP, pendingPlacePos.down(), false));
                pendingPlaceAfterJump = false;
                pendingPlacePos = null;
            }
            
            if (pendingPlaceTimeout == 0) {
                pendingPlaceAfterJump = false;
                pendingPlacePos = null;
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
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        // Check for tall obstacles between bot and target.
        // Only climb if player is more than 30 blocks away in x, y, or z
        double distX = Math.abs(dx);
        double distY = Math.abs(dy);
        double distZ = Math.abs(dz);
        boolean shouldClimb = (distX > 30 || distY > 30 || distZ > 30);
        
        if (shouldClimb) {
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
                    } else {
                        // Otherwise schedule placing once the bot starts falling
                        pendingPlaceAfterJump = true;
                        pendingPlacePos = placePos;
                        pendingPlaceTimeout = 20; // ~1 second window to place while falling
                    }

                    break; // only perform once per move tick
                }
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
            
            double moveX = Math.cos(Math.toRadians(yaw + 90)) * speed;
            double moveZ = Math.sin(Math.toRadians(yaw + 90)) * speed;
            
            double strafeX = Math.cos(Math.toRadians(yaw)) * strafeAmount * strafeDirection;
            double strafeZ = Math.sin(Math.toRadians(yaw)) * strafeAmount * strafeDirection;
            
            Vec3d newPos = new Vec3d(botX + moveX + strafeX, botY, botZ + moveZ + strafeZ);
            bot.setPosition(newPos);
        }
        
        // Crystal bot does not jump
    }
}
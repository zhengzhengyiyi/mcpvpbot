package net.zhengzhengyiyi.ai.modes.smp;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * SMP bot movement behavior
 */
public class SmpMovement {
    private EntityPlayerMPFake bot;
    private int strafeDirection = 1;
    private int strafeTimer = 0;
    private String difficulty = "middle";
    private int waterBucketCooldown = 0;
    
    // pending placement state
    private boolean pendingPlaceAfterJump = false;
    private BlockPos pendingPlacePos = null;
    private int pendingPlaceTimeout = 0;

    public SmpMovement(EntityPlayerMPFake bot) {
        this.bot = bot;
    }
    
    public void setBot(EntityPlayerMPFake bot) {
        this.bot = bot;
    }
    
    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }
    
    public void moveTowards(ServerPlayerEntity target) {
        if (target == null || !target.isAlive()) {
            return;
        }
        
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

        // Handle pending obsidian placement
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
                        pendingPlaceAfterJump = false;
                        pendingPlacePos = null;
                    }
                }
            }
        }
        
        // Handle web detection
        handleWebDetection();
        
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
        
        // Move forward with sprint speed and strafe
        if (horizontalDistance > 1.5) {
            double speed = 0.28;
            double strafeAmount = 0.1;
            
            // Adjust strafing based on difficulty
            if (difficulty.equals("easy")) {
                strafeAmount = 0.0;
            }
            
            // Hard mode behavior
            double optimalDistance = 2.7;
            double attackRange = 2.6;
            if (difficulty.equals("hard")) {
                if (fullDistance > attackRange + 1.0) {
                    strafeAmount = 0.0;
                    speed = 0.32;
                } else if (fullDistance < optimalDistance) {
                    speed = 0.0;
                    strafeAmount = 0.15;
                } else {
                    strafeAmount = 0.15;
                }
            }
            
            double moveX = Math.cos(Math.toRadians(yaw + 90)) * speed;
            double moveZ = Math.sin(Math.toRadians(yaw + 90)) * speed;
            
            double strafeX = Math.cos(Math.toRadians(yaw)) * strafeAmount * strafeDirection;
            double strafeZ = Math.sin(Math.toRadians(yaw)) * strafeAmount * strafeDirection;
            
            Vec3d newPos = new Vec3d(botX + moveX + strafeX, botY, botZ + moveZ + strafeZ);
            bot.setPosition(newPos);
        }
        
        // Jump behavior
        double jumpDistance = 1.1;
        if (difficulty.equals("hard")) {
            if (fullDistance <= 1.4 && bot.isOnGround()) {
                bot.jump();
            }
        } else {
            if (fullDistance <= jumpDistance && bot.isOnGround()) {
                bot.jump();
            }
        }
    }
    
    private void handleWebDetection() {
        if (waterBucketCooldown > 0) {
            waterBucketCooldown--;
        }
        
        BlockPos botPos = new BlockPos((int) bot.getX(), (int) bot.getY(), (int) bot.getZ());
        BlockPos feetPos = botPos.down();
        BlockPos headPos = botPos.up();
        
        boolean inWebAtFeet = bot.getEntityWorld().getBlockState(feetPos).getBlock() == Blocks.COBWEB;
        boolean inWebAtBody = bot.getEntityWorld().getBlockState(botPos).getBlock() == Blocks.COBWEB;
        boolean inWebAtHead = bot.getEntityWorld().getBlockState(headPos).getBlock() == Blocks.COBWEB;
        
        int webCount = (inWebAtFeet ? 1 : 0) + (inWebAtBody ? 1 : 0) + (inWebAtHead ? 1 : 0);
        
        if (webCount > 0) {
            if (webCount >= 2 && waterBucketCooldown == 0) {
                ItemStack waterBucket = bot.getInventory().getStack(2);
                if (waterBucket.getItem() == Items.WATER_BUCKET && waterBucket.getCount() > 0) {
                    bot.getInventory().setSelectedSlot(2);
                    bot.setCurrentHand(Hand.MAIN_HAND);
                    
                    BlockPos waterPos = feetPos;
                    bot.getEntityWorld().setBlockState(waterPos, Blocks.WATER.getDefaultState());
                    
                    waterBucketCooldown = 5;
                }
            }
        }
    }
}
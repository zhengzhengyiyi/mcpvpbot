package net.zhengzhengyiyi.ai.modes.mace;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Mace bot movement behavior (elytra flight + mace attacks)
 */
public class MaceMovement {
    private EntityPlayerMPFake bot;

    public MaceMovement(EntityPlayerMPFake bot) {
        this.bot = bot;
    }
    
    public void setBot(EntityPlayerMPFake bot) {
        this.bot = bot;
    }
    
    public void setDifficulty(String difficulty) {
        // Mace bot is not affected by difficulty
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

        // Look at target
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) Math.toDegrees(Math.atan2(-(targetY - botY), horizontalDistance));
        bot.setYaw(yaw);
        bot.setPitch(pitch);
        
        // Elytra flight logic - move towards target
        if (horizontalDistance > 1.5) {
            double speed = 0.4; // Faster with elytra
            double moveX = Math.cos(Math.toRadians(yaw + 90)) * speed;
            double moveZ = Math.sin(Math.toRadians(yaw + 90)) * speed;
            
            // Vertical movement if needed
            double moveY = 0;
            if (dy > 1.0) {
                moveY = 0.3; // Fly up
            } else if (dy < -1.0) {
                moveY = -0.3; // Fly down
            }
            
            Vec3d newPos = new Vec3d(botX + moveX, botY + moveY, botZ + moveZ);
            bot.setPosition(newPos);
        }
        
        // Jump only when 2 blocks away or less from player
        if (fullDistance <= 2.0 && bot.isOnGround()) {
            bot.jump();
        }
    }
}
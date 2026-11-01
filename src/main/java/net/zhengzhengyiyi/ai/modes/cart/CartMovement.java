package net.zhengzhengyiyi.ai.modes.cart;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class CartMovement {
    public enum MovementState { APPROACH, STRIKING_RANGE, HEIGHT_ADJUSTMENT, BACKPEDAL_ESCAPE }
    private static final double STRAFE_SCALE = 0.65;

    private EntityPlayerMPFake bot;
    private int strafeDirection = 1;
    private int strafeTicks;

    public CartMovement(EntityPlayerMPFake bot) {
        this.bot = bot;
    }

    public void setBot(EntityPlayerMPFake bot) {
        this.bot = bot;
    }

    public void moveTowards(ServerPlayerEntity target) {
        if (bot == null || target == null || !target.isAlive()) return;

        Vec3d delta = new Vec3d(target.getX() - bot.getX(), target.getY() - bot.getY(), target.getZ() - bot.getZ());
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        MovementState state = evaluateMovementState(target, horizontalDistance);
        float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0f;
        double lookDistance = Math.max(0.001, horizontalDistance);
        float pitch = (float) Math.toDegrees(Math.atan2(-(target.getY() - bot.getY()), lookDistance));
        bot.setYaw(yaw);
        bot.setPitch(pitch);

        strafeTicks++;
        if (strafeTicks >= 16) {
            strafeDirection *= -1;
            strafeTicks = 0;
        }

        double forward = 0.0;
        double strafe = 0.0;
        double vertical = 0.0;
        switch (state) {
            case APPROACH -> {
                forward = 0.30;
                strafe = 0.06 * STRAFE_SCALE * strafeDirection;
                bot.setSprinting(true);
            }
            case STRIKING_RANGE -> {
                forward = horizontalDistance > 3.2 ? 0.12 : 0.0;
                strafe = 0.15 * STRAFE_SCALE * strafeDirection;
                bot.setSprinting(false);
            }
            case HEIGHT_ADJUSTMENT -> {
                forward = horizontalDistance > 2.8 ? 0.24 : 0.08;
                strafe = 0.08 * STRAFE_SCALE * strafeDirection;
                vertical = delta.y > 0.8 ? 0.22 : -0.10;
                bot.setSprinting(true);
                if (delta.y > 0.8 && bot.isOnGround()) bot.jump();
            }
            case BACKPEDAL_ESCAPE -> {
                forward = -0.20;
                strafe = 0.12 * STRAFE_SCALE * strafeDirection;
                bot.setSprinting(false);
            }
        }

        Vec3d forwardVector = new Vec3d(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
        Vec3d strafeVector = new Vec3d(Math.cos(Math.toRadians(yaw)), 0, Math.sin(Math.toRadians(yaw)));
        Vec3d movement = forwardVector.multiply(forward).add(strafeVector.multiply(strafe));
        Vec3d next = new Vec3d(bot.getX() + movement.x, bot.getY() + vertical, bot.getZ() + movement.z);
        if (isPositionClear(next)) bot.setPosition(next);
    }

    private MovementState evaluateMovementState(ServerPlayerEntity target, double horizontalDistance) {
        double verticalDistance = target.getY() - bot.getY();
        if (Math.abs(verticalDistance) > 0.8) return MovementState.HEIGHT_ADJUSTMENT;
        if (horizontalDistance <= 2.2 && (target.isSprinting() || bot.getHealth() < 8.0f)) return MovementState.BACKPEDAL_ESCAPE;
        if (horizontalDistance > 5.0) return MovementState.APPROACH;
        return MovementState.STRIKING_RANGE;
    }

    private boolean isPositionClear(Vec3d position) {
        BlockPos feet = BlockPos.ofFloored(position);
        BlockPos head = feet.up();
        BlockState feetState = bot.getEntityWorld().getBlockState(feet);
        BlockState headState = bot.getEntityWorld().getBlockState(head);
        return feetState.getCollisionShape(bot.getEntityWorld(), feet).isEmpty()
                && headState.getCollisionShape(bot.getEntityWorld(), head).isEmpty()
                && !bot.getEntityWorld().isAir(feet.down());
    }
}
package net.zhengzhengyiyi.ai.modes.cart;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.block.BlockState;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.HashSet;
import java.util.Set;

public final class CartAction {
    private enum Decision { COUNTER_RAIL, HEIGHT_ADVANTAGE, HEIGHT_DISADVANTAGE, INSTA_CART, SWORD_PRESSURE, NONE }
    private enum ComboStep { IDLE, BOW_FIRST, PLACE_RAIL, PLACE_CART, DETONATE, SHIELD }

    private EntityPlayerMPFake bot;
    private ComboStep comboStep = ComboStep.IDLE;
    private BlockPos comboRailPos;
    private boolean useBowForCombo;
    private int comboTimeout;
    private int attackCooldown;
    private boolean bowUseStarted;
    private int bowChargeTicks;
    private final Set<BlockPos> ownRailPositions = new HashSet<>();

    public CartAction(EntityPlayerMPFake bot) {
        this.bot = bot;
    }

    public void setBot(EntityPlayerMPFake bot) {
        this.bot = bot;
        resetCombo();
    }

    public void performCombat(ServerPlayerEntity target, double distance) {
        if (bot == null || target == null || !target.isAlive() || target.isSpectator()) return;
        if (attackCooldown > 0) attackCooldown--;
        replenishSupplies();

        if (comboStep != ComboStep.IDLE) {
            executeCombo(target);
            return;
        }

        Decision decision = evaluateState(target);
        if (decision == Decision.COUNTER_RAIL) {
            cancelNearbyRail();
            return;
        }

        if (comboStep == ComboStep.IDLE) {
            if (decision == Decision.HEIGHT_ADVANTAGE || decision == Decision.HEIGHT_DISADVANTAGE || decision == Decision.INSTA_CART) {
                beginCombo(target, decision);
            } else if (decision == Decision.SWORD_PRESSURE) {
                pressureWithSword(target, distance);
                return;
            } else {
                return;
            }
        }

        executeCombo(target);
    }

    private Decision evaluateState(ServerPlayerEntity target) {
        if (findNearbyEnemyRail() != null) return Decision.COUNTER_RAIL;

        double height = target.getY() - bot.getY();
        if (height >= 0.8 && height <= 2.2) return Decision.HEIGHT_ADVANTAGE;
        if (height <= -0.8 && isInDropOrHole(target)) return Decision.HEIGHT_DISADVANTAGE;
        if (isInstaCartOpportunity(target)) return Decision.INSTA_CART;
        return Decision.SWORD_PRESSURE;
    }

    private boolean isInstaCartOpportunity(ServerPlayerEntity target) {
        Vec3d velocity = target.getVelocity();
        boolean lowMotion = velocity.horizontalLengthSquared() < 0.012;
        boolean eatingOrUsing = target.isUsingItem();
        boolean stopped = !target.isSprinting() && lowMotion;
        int blockedSides = 0;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos side = target.getBlockPos().offset(direction);
            if (!target.getEntityWorld().getBlockState(side).getCollisionShape(target.getEntityWorld(), side).isEmpty()) blockedSides++;
        }
        return eatingOrUsing || stopped || blockedSides >= 2;
    }

    private boolean isInDropOrHole(ServerPlayerEntity target) {
        BlockPos feet = target.getBlockPos();
        BlockState below = target.getEntityWorld().getBlockState(feet.down());
        return !below.getCollisionShape(target.getEntityWorld(), feet.down()).isEmpty() || target.getY() < bot.getY() - 1.0;
    }

    private void beginCombo(ServerPlayerEntity target, Decision decision) {
        comboRailPos = chooseRailPosition(target);
        useBowForCombo = decision == Decision.HEIGHT_DISADVANTAGE;
        comboTimeout = 40;
        comboStep = decision == Decision.INSTA_CART ? ComboStep.BOW_FIRST : ComboStep.PLACE_RAIL;
    }

    private void executeCombo(ServerPlayerEntity target) {
        if (--comboTimeout < 0) {
            resetCombo();
            return;
        }
        switch (comboStep) {
            case BOW_FIRST -> {
                Vec3d cartAim = comboRailPos.toCenterPos().add(0, 0.3, 0);
                aimBowAt(cartAim);
                if (!bowUseStarted) {
                    beginBowUse();
                    return;
                }
                bowChargeTicks++;
                    if (bowChargeTicks >= 5) {
                    bot.stopUsingItem();
                    bowUseStarted = false;
                    bowChargeTicks = 0;
                    comboStep = ComboStep.PLACE_RAIL;
                }
            }
            case PLACE_RAIL -> {
                if (comboRailPos == null || !bot.getEntityWorld().isAir(comboRailPos)) {
                    resetCombo();
                    return;
                }
                useItemOnSupport(2, comboRailPos);
                ownRailPositions.add(comboRailPos.toImmutable());
                comboStep = ComboStep.PLACE_CART;
            }
            case PLACE_CART -> {
                if (comboRailPos == null || bot.getEntityWorld().isAir(comboRailPos)) return;
                selectSlot(3);
                bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), bot.getInventory().getStack(3), Hand.MAIN_HAND,
                        new BlockHitResult(comboRailPos.toCenterPos(), Direction.UP, comboRailPos, false));
                comboStep = ComboStep.DETONATE;
            }
            case DETONATE -> {
                TntMinecartEntity cart = findNearbyCart();
                if (cart == null) return;
                if (useBowForCombo) {
                    aimBowAt(new Vec3d(cart.getX(), cart.getY(), cart.getZ()));
                    if (!bowUseStarted) {
                        beginBowUse();
                        return;
                    }
                    bowChargeTicks++;
                        if (bowChargeTicks >= 5) {
                        bot.stopUsingItem();
                        comboStep = ComboStep.SHIELD;
                    }
                } else if (attackCooldown == 0) {
                    selectSlot(0);
                    bot.attack(cart);
                    attackCooldown = 4;
                    comboStep = ComboStep.SHIELD;
                }
            }
            case SHIELD -> {
                placeShield(target);
                resetCombo();
            }
            case IDLE -> { }
        }
    }

    private void pressureWithSword(ServerPlayerEntity target, double distance) {
        selectSlot(0);
        if (distance <= 3.2 && attackCooldown == 0 && bot.getAttackCooldownProgress(0.5f) >= 0.85f) {
            bot.attack(target);
            attackCooldown = 8;
        }
    }

    private void beginBowUse() {
        selectSlot(1);
        ItemStack bow = bot.getInventory().getStack(1);
        bot.interactionManager.interactItem(bot, bot.getEntityWorld(), bow, Hand.MAIN_HAND);
        bowUseStarted = true;
    }

    private void aimBowAt(Vec3d destination) {
        Vec3d eye = bot.getEyePos();
        Vec3d aim = destination.add(0, 0.3, 0).subtract(eye).normalize();
        bot.setYaw((float) Math.toDegrees(Math.atan2(aim.z, aim.x)) - 90.0f);
        bot.setPitch((float) -Math.toDegrees(Math.asin(aim.y)));
    }

    private void placeShield(ServerPlayerEntity target) {
        Vec3d towardTarget = new Vec3d(target.getX() - bot.getX(), 0, target.getZ() - bot.getZ()).normalize();
        BlockPos base = bot.getBlockPos().offset(Math.abs(towardTarget.x) > Math.abs(towardTarget.z)
            ? (towardTarget.x > 0 ? Direction.EAST : Direction.WEST)
            : (towardTarget.z > 0 ? Direction.SOUTH : Direction.NORTH));
        placeBlockAt(4, base);
    }

    private void placeBlockAt(int slot, BlockPos position) {
        if (bot.getEntityWorld().isAir(position) && !bot.getEntityWorld().isAir(position.down())) {
            useItemOnSupport(slot, position);
        }
    }

    private void useItemOnSupport(int slot, BlockPos position) {
        selectSlot(slot);
        BlockPos support = position.down();
        bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), bot.getInventory().getStack(slot), Hand.MAIN_HAND,
                new BlockHitResult(support.toCenterPos(), Direction.UP, support, false));
    }

    private BlockPos chooseRailPosition(ServerPlayerEntity target) {
        Vec3d towardBot = new Vec3d(bot.getX() - target.getX(), bot.getY() - target.getY(), bot.getZ() - target.getZ()).normalize();
        Direction side = Math.abs(towardBot.x) > Math.abs(towardBot.z)
                ? (towardBot.x > 0 ? Direction.EAST : Direction.WEST)
                : (towardBot.z > 0 ? Direction.SOUTH : Direction.NORTH);
        return target.getBlockPos().offset(side);
    }

    private void cancelNearbyRail() {
        BlockPos rail = findNearbyEnemyRail();
        if (rail == null) return;
        BlockPos cap = rail.up();
        if (!bot.getEntityWorld().isAir(cap)) return;
        selectSlot(4);
        bot.interactionManager.interactBlock(bot, bot.getEntityWorld(), bot.getInventory().getStack(4), Hand.MAIN_HAND,
                new BlockHitResult(rail.toCenterPos(), Direction.UP, rail, false));
    }

    private BlockPos findNearbyEnemyRail() {
        BlockPos origin = bot.getBlockPos();
        for (int x = -3; x <= 3; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -3; z <= 3; z++) {
                    BlockPos pos = origin.add(x, y, z);
                        if (bot.getEntityWorld().getBlockState(pos).getBlock() instanceof AbstractRailBlock
                            && !ownRailPositions.contains(pos.toImmutable())
                            && (comboRailPos == null || !comboRailPos.equals(pos))) return pos;
                }
            }
        }
        return null;
    }

    private TntMinecartEntity findNearbyCart() {
        Box search = bot.getBoundingBox().expand(5.0);
        TntMinecartEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (TntMinecartEntity cart : bot.getEntityWorld().getEntitiesByClass(TntMinecartEntity.class, search,
                candidate -> candidate.isAlive()
                        && (comboRailPos == null || candidate.getBlockPos().getSquaredDistance(comboRailPos) <= 16.0)
                        && isCartInSight(candidate))) {
            double distance = cart.squaredDistanceTo(bot);
            if (distance < nearestDistance) {
                nearest = cart;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

        private boolean isCartInSight(TntMinecartEntity cart) {
        Vec3d start = bot.getEyePos();
        Vec3d end = new Vec3d(cart.getX(), cart.getY() + 0.5, cart.getZ());
        if (cart.getBoundingBox().expand(0.25).raycast(start, end).isEmpty()) return false;
        BlockHitResult obstruction = bot.getEntityWorld().raycast(new RaycastContext(start, end,
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, bot));
        return obstruction.getType() == HitResult.Type.MISS || obstruction.getBlockPos().equals(comboRailPos);
        }

    private void replenishSupplies() {
        ItemStack offhand = bot.getEquippedStack(EquipmentSlot.OFFHAND);
        if (offhand.getItem() != Items.TOTEM_OF_UNDYING) {
            for (int slot = 7; slot < 36; slot++) {
                ItemStack reserve = bot.getInventory().getStack(slot);
                if (reserve.getItem() == Items.TOTEM_OF_UNDYING) {
                    bot.equipStack(EquipmentSlot.OFFHAND, reserve.copyWithCount(1));
                    bot.getInventory().setStack(slot, ItemStack.EMPTY);
                    break;
                }
            }
        }
        refill(2, Items.RAIL);
        refill(3, Items.TNT_MINECART);
        refill(4, Items.OAK_PLANKS);
        refill(5, Items.GOLDEN_APPLE);
        if (bot.getInventory().getStack(6).isEmpty()) bot.getInventory().setStack(6, new ItemStack(Items.ARROW, 1));
    }

    private void refill(int slot, net.minecraft.item.Item item) {
        ItemStack stack = bot.getInventory().getStack(slot);
        if (stack.getItem() != item || stack.getCount() < 64) bot.getInventory().setStack(slot, new ItemStack(item, 64));
    }

    private void selectSlot(int slot) {
        if (bot.getInventory().getSelectedSlot() != slot) bot.getInventory().setSelectedSlot(slot);
    }

    private void resetCombo() {
        comboStep = ComboStep.IDLE;
        comboRailPos = null;
        comboTimeout = 0;
        bowUseStarted = false;
        bowChargeTicks = 0;
    }
}
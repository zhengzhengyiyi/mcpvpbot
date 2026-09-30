package net.zhengzhengyiyi.ai.modes.netherite;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

/**
 * Netherite bot combat action - melee combat with shield/axe switching (better stats than SMP)
 */
public class NetheriteAction {
    private EntityPlayerMPFake bot;
    @SuppressWarnings("unused")
    private String difficulty = "middle"; // Reserved for future difficulty-based behavior
    private boolean usingAxe = false;
    private int shieldCooldown = 0;
    private boolean shouldBlock = false;
    private int attackCooldown = 0;

    public NetheriteAction(EntityPlayerMPFake bot) {
        this.bot = bot;
    }
    
    public void setBot(EntityPlayerMPFake bot) {
        this.bot = bot;
    }
    
    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }
    
    public void performCombat(ServerPlayerEntity target, double distance) {
        if (target == null || !target.isAlive()) {
            return;
        }
        
        // Decrease cooldowns
        if (shieldCooldown > 0) shieldCooldown--;
        if (attackCooldown > 0) attackCooldown--;
        
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
            if (bot.getInventory().getSelectedSlot() != 0) {
                bot.getInventory().setSelectedSlot(0);
            }
        }
        
        // Check for crit hit (2 blocks away and falling)
        boolean isCrit = distance <= 1.8 && !bot.isOnGround() && bot.fallDistance > 0;
        
        // Attack if conditions are met
        if (isCrit || distance <= 2.6) {
            // 5% chance to miss
            if (Math.random() < 0.08) {
                return;
            }
            
            bot.attack(target);
            
            if (isCrit) {
                shouldBlock = true;
                shieldCooldown = 40; // 2 seconds cooldown
            }
            
            attackCooldown = 8;
        }
        
        // Handle shield blocking
        if (shouldBlock && shieldCooldown == 0) {
            ItemStack offhandItem = bot.getEquippedStack(EquipmentSlot.OFFHAND);
            if (offhandItem.getItem() == Items.SHIELD) {
                bot.setCurrentHand(Hand.OFF_HAND);
                shouldBlock = false;
                bot.interactionManager.interactItem(bot, bot.getEntityWorld(), offhandItem, Hand.OFF_HAND);
            }
        }
    }
    
    private boolean targetIsUsingShield(ServerPlayerEntity target) {
        ItemStack offhand = target.getEquippedStack(EquipmentSlot.OFFHAND);
        return offhand.getItem() == Items.SHIELD;
    }
    
    private void switchToAxe() {
        ItemStack axe = bot.getInventory().getStack(1);
        if (axe.getItem() == Items.NETHERITE_AXE) {
            bot.getInventory().setSelectedSlot(1);
        }
    }
    
    private void switchToSword() {
        ItemStack sword = bot.getInventory().getStack(0);
        if (sword.getItem() == Items.NETHERITE_SWORD) {
            bot.getInventory().setSelectedSlot(0);
        }
    }
}
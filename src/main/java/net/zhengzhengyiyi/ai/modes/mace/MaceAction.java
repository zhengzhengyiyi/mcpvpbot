package net.zhengzhengyiyi.ai.modes.mace;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

/**
 * Mace bot combat action - mace attacks with elytra flight and vertical pearl catches
 */
public class MaceAction {
    private EntityPlayerMPFake bot;
    private int pearlCooldown = 0;
    private int attackCooldown = 0;

    public MaceAction(EntityPlayerMPFake bot) {
        this.bot = bot;
    }
    
    public void setBot(EntityPlayerMPFake bot) {
        this.bot = bot;
    }
    
    public void performCombat(ServerPlayerEntity target, double distance) {
        if (target == null || !target.isAlive()) {
            return;
        }
        
        // Decrease cooldowns
        if (pearlCooldown > 0) pearlCooldown--;
        if (attackCooldown > 0) attackCooldown--;
        
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
                    attackCooldown = 8;
                } else if (distance <= 3.0 && attackCooldown == 0) {
                    // Regular mace attack when close
                    bot.attack(target);
                    attackCooldown = 8;
                }
            }
        }
    }
}
package net.zhengzhengyiyi.ai;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Interface for bot combat behavior strategies
 */
public interface CombatBehavior {
    /**
     * Initialize the combat behavior with a bot
     * @param bot The fake player entity
     */
    void initialize(EntityPlayerMPFake bot);
    
    /**
     * Execute combat logic against a target
     * @param target The target player to attack
     * @param distance Distance to the target
     */
    void attack(ServerPlayerEntity target, double distance);
    
    /**
     * Set the bot type for this combat behavior
     * @param type The bot type (smp, crystal, mace, netherite)
     */
    void setBotType(String type);
    
    /**
     * Set the difficulty level for this combat behavior
     * @param difficulty The difficulty level (easy, middle, hard)
     */
    void setDifficulty(String difficulty);
    
    /**
     * Update the bot entity reference
     * @param bot The new bot entity
     */
    void setBot(EntityPlayerMPFake bot);
}
package net.zhengzhengyiyi.ai;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Interface for bot movement behavior strategies
 */
public interface MovementBehavior {
    /**
     * Initialize the movement behavior with a bot
     * @param bot The fake player entity
     */
    void initialize(EntityPlayerMPFake bot);
    
    /**
     * Move the bot towards a target
     * @param target The target player to move towards
     */
    void moveTowards(ServerPlayerEntity target);
    
    /**
     * Set the bot type for this movement behavior
     * @param type The bot type (smp, crystal, mace, netherite)
     */
    void setBotType(String type);
    
    /**
     * Set the difficulty level for this movement behavior
     * @param difficulty The difficulty level (easy, middle, hard)
     */
    void setDifficulty(String difficulty);
    
    /**
     * Update the bot entity reference
     * @param bot The new bot entity
     */
    void setBot(EntityPlayerMPFake bot);
}
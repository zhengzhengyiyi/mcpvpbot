package net.zhengzhengyiyi.ai;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.zhengzhengyiyi.PvpBot;

@SuppressWarnings("unused")
public class BotAI {
    // private static final Logger LOGGER = LoggerFactory.getLogger("BotAI");
    
    private EntityPlayerMPFake bot;
    private ServerPlayerEntity target;
    private int attackCooldown = 0;
    private String botType = "smp";
    private String difficulty = "middle";
    
    private MovementBehavior movement;
    private CombatBehavior combat;
    
    public BotAI(EntityPlayerMPFake bot) {
        this.bot = bot;
        // Default to old classes for backward compatibility
        this.movement = new BotMovement(bot);
        this.combat = new BotCombat(bot);
    }
    
    public void setBotType(String type) {
        this.botType = type;
        
        // BotMovement and BotCombat now handle all bot types as delegators
        // Just need to update their botType field
        if (movement instanceof BotMovement) {
            ((BotMovement) movement).setBotType(type);
        }
        if (combat instanceof BotCombat) {
            ((BotCombat) combat).setBotType(type);
        }
        
        // Link movement to combat for totem cooldown check
        if (movement instanceof BotMovement && combat instanceof BotCombat) {
            ((BotMovement) movement).setCombat((BotCombat) combat);
        }
    }
    
    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
        
        // Propagate difficulty to movement and combat
        if (movement instanceof BotMovement) {
            ((BotMovement) movement).setDifficulty(difficulty);
        }
        if (combat instanceof BotCombat) {
            ((BotCombat) combat).setDifficulty(difficulty);
        }
    }
    
    public void tick(MinecraftServer server) {
        if (bot == null || !bot.isAlive()) {
            return;
        }
        
        // Decrease attack cooldown
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        
        // Find nearest player
        ServerPlayerEntity nearestPlayer = findNearestPlayer(server);
        if (nearestPlayer == null) {
            target = null;
            return;
        }
        
        ServerPlayerEntity preferredTarget;
        if (PvpBot.playerTarget == null) {
            preferredTarget = nearestPlayer;
        } else {
            preferredTarget = PvpBot.playerTarget;
        }

        target = resolveTarget(server, preferredTarget);
        
        // Skip if no valid target
        if (target == null) {
            return;
        }
        
        // Calculate distance
        double distance = calculateDistance(bot, target);
        double hitRange = 3.0;
        
        // Move towards target first
        movement.moveTowards(target);
        
        // Crystal bot has its own attack logic with distance checks
        if (botType.equals("crystal")) {
            combat.attack(target, distance);
        } else {
            // Other bots use the old attack logic
            if (distance <= hitRange && attackCooldown == 0) {
                combat.attack(target, distance);
                attackCooldown = 10;
            }
        }
    }
    
    private ServerPlayerEntity findNearestPlayer(MinecraftServer server) {
        if (bot == null || server == null) {
            return null;
        }
        
        ServerPlayerEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player == bot || !player.isAlive()) {
                continue;
            }
            
            double distance = calculateDistance(bot, player);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }
        
        return nearest;
    }

    private ServerPlayerEntity resolveTarget(MinecraftServer server, ServerPlayerEntity preferredTarget) {
        if (preferredTarget == null) {
            return findNearestPvpBot(server, getBot());
        }

        String preferredName = preferredTarget.getName().getString();
        String botName = getBot().getName().getString();

        if (preferredName.equals(botName)) {
            ServerPlayerEntity nearestBotTarget = findNearestPvpBot(server, getBot());
            
            return nearestBotTarget;
        }

        return preferredTarget;
    }

    
    // private ServerPlayerEntity resolveTarget(MinecraftServer server, ServerPlayerEntity preferredTarget) {
    //     if (preferredTarget.getName().getString().equals(getBot().getName().getString())) {
    //         ServerPlayerEntity nearestBotTarget = findNearestPvpBot(server, preferredTarget);
    //         return nearestBotTarget != null ? nearestBotTarget : preferredTarget;
    //     }

    //     return preferredTarget;
    // }

    private boolean isPvpBot(ServerPlayerEntity player) {
        if (player == null || player == bot) {
            return false;
        }

        if (player instanceof EntityPlayerMPFake) {
            return true;
        }

        String name = player.getName().getString();
        return name != null && name.startsWith("pvp-bot");
    }

    private ServerPlayerEntity findNearestPvpBot(MinecraftServer server, ServerPlayerEntity preferredTarget) {
        if (bot == null || server == null) {
            return null;
        }

        ServerPlayerEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player == bot || player == preferredTarget || !player.isAlive() || !isPvpBot(player)) {
                continue;
            }

            double distance = calculateDistance(bot, player);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }

        return nearest;
    }

    private double calculateDistance(EntityPlayerMPFake bot, ServerPlayerEntity target) {
        double dx = bot.getX() - target.getX();
        double dy = bot.getY() - target.getY();
        double dz = bot.getZ() - target.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
        // return Math.sqrt(dx * dx + dz * dz);
    }
    
    public void setBot(EntityPlayerMPFake bot) {
        this.bot = bot;
        if (movement != null) {
            movement.setBot(bot);
        }
        if (combat != null) {
            combat.setBot(bot);
        }
    }
    
    public EntityPlayerMPFake getBot() {
        return bot;
    }
}

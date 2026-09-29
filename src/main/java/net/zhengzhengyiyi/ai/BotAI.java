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
    
    private final BotMovement movement;
    private final BotCombat combat;
    
    public BotAI(EntityPlayerMPFake bot) {
        this.bot = bot;
        this.movement = new BotMovement(bot);
        this.combat = new BotCombat(bot);
    }
    
    public void setBotType(String type) {
        this.botType = type;
        movement.setBotType(type);
        combat.setBotType(type);
    }
    
    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
        movement.setDifficulty(difficulty);
        combat.setDifficulty(difficulty);
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
        
        // Calculate distance
        double distance = calculateDistance(bot, target);
        double hitRange = 3.0;
        
        // Move towards target first
        movement.moveTowards(target);
        
        // If in hit range, attempt to attack
        if (distance <= hitRange && attackCooldown == 0) {
            combat.attack(target, distance);
            attackCooldown = 10;
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
        movement.setBot(bot);
        combat.setBot(bot);
    }
    
    public EntityPlayerMPFake getBot() {
        return bot;
    }
}

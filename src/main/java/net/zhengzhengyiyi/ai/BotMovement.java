package net.zhengzhengyiyi.ai;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.server.network.ServerPlayerEntity;
import net.zhengzhengyiyi.ai.modes.crystal.CrystalMovement;
import net.zhengzhengyiyi.ai.modes.smp.SmpMovement;
import net.zhengzhengyiyi.ai.modes.netherite.NetheriteMovement;
import net.zhengzhengyiyi.ai.modes.mace.MaceMovement;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

/**
 * Main movement delegator - routes movement to mode-specific implementations
 */
public class BotMovement implements MovementBehavior {
//    private static final Logger LOGGER = LoggerFactory.getLogger("BotMovement");
    
    private EntityPlayerMPFake bot;
    private String botType = "smp";
    private BotCombat combat;
    
    // Mode-specific movement implementations
    private CrystalMovement crystalMovement;
    private SmpMovement smpMovement;
    private NetheriteMovement netheriteMovement;
    private MaceMovement maceMovement;
    
    public BotMovement(EntityPlayerMPFake bot) {
        this.bot = bot;
        this.crystalMovement = new CrystalMovement(bot);
        this.smpMovement = new SmpMovement(bot);
        this.netheriteMovement = new NetheriteMovement(bot);
        this.maceMovement = new MaceMovement(bot);
    }
    
    @Override
    public void initialize(EntityPlayerMPFake bot) {
        this.bot = bot;
        this.crystalMovement.setBot(bot);
        this.smpMovement.setBot(bot);
        this.netheriteMovement.setBot(bot);
        this.maceMovement.setBot(bot);
    }
    
    public void setBotType(String type) {
        this.botType = type;
    }
    
    public void setDifficulty(String difficulty) {
        // Propagate difficulty to mode-specific movements
        if (smpMovement != null) smpMovement.setDifficulty(difficulty);
        if (netheriteMovement != null) netheriteMovement.setDifficulty(difficulty);
    }
    
    public void setCombat(BotCombat combat) {
        this.combat = combat;
        // Link crystal movement to crystal action for totem cooldown check
        if (crystalMovement != null && combat != null) {
            crystalMovement.setCombat(combat.getCrystalAction());
        }
    }
    
    @Override
    public void setBot(EntityPlayerMPFake bot) {
        this.bot = bot;
        if (crystalMovement != null) crystalMovement.setBot(bot);
        if (smpMovement != null) smpMovement.setBot(bot);
        if (netheriteMovement != null) netheriteMovement.setBot(bot);
        if (maceMovement != null) maceMovement.setBot(bot);
    }
    
    public void moveTowards(ServerPlayerEntity target) {
        if (target == null || !target.isAlive()) {
            return;
        }
        
        // Delegate to mode-specific movement
        if (botType.equals("crystal")) {
            crystalMovement.moveTowards(target);
        } else if (botType.equals("smp")) {
            smpMovement.moveTowards(target);
        } else if (botType.equals("netherite")) {
            netheriteMovement.moveTowards(target);
        } else if (botType.equals("mace")) {
            maceMovement.moveTowards(target);
        } else {
            // Default movement - just look at target and sprint
            handleDefaultMovement(target);
        }
    }
    
    private void handleDefaultMovement(ServerPlayerEntity target) {
        double botX = bot.getX();
        double botY = bot.getY();
        double botZ = bot.getZ();
        double targetX = target.getX();
        double targetY = target.getY();
        double targetZ = target.getZ();
        
        double dx = targetX - botX;
        double dz = targetZ - botZ;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) Math.toDegrees(Math.atan2(-(targetY - botY), Math.sqrt(dx * dx + dz * dz)));
        bot.setYaw(yaw);
        bot.setPitch(pitch);
        bot.setSprinting(true);
    }
}
package net.zhengzhengyiyi.ai;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.server.network.ServerPlayerEntity;
import net.zhengzhengyiyi.ai.modes.crystal.CrystalAction;
import net.zhengzhengyiyi.ai.modes.smp.SmpAction;
import net.zhengzhengyiyi.ai.modes.netherite.NetheriteAction;
import net.zhengzhengyiyi.ai.modes.mace.MaceAction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main combat delegator - routes combat actions to mode-specific implementations
 */
public class BotCombat implements CombatBehavior {
    private static final Logger LOGGER = LoggerFactory.getLogger("BotCombat");
    
    private EntityPlayerMPFake bot;
    private String botType = "smp";
    private String difficulty = "middle";
    
    // Mode-specific action implementations
    private CrystalAction crystalAction;
    private SmpAction smpAction;
    private NetheriteAction netheriteAction;
    private MaceAction maceAction;

    public BotCombat(EntityPlayerMPFake bot) {
        this.bot = bot;
        this.crystalAction = new CrystalAction(bot);
        this.smpAction = new SmpAction(bot);
        this.netheriteAction = new NetheriteAction(bot);
        this.maceAction = new MaceAction(bot);
    }
    
    @Override
    public void initialize(EntityPlayerMPFake bot) {
        this.bot = bot;
        this.crystalAction.setBot(bot);
        this.smpAction.setBot(bot);
        this.netheriteAction.setBot(bot);
        this.maceAction.setBot(bot);
        
        // Link crystal movement to crystal action for totem cooldown check
        // This will be set when movement is initialized
    }
    
    public void setBotType(String type) {
        this.botType = type;
    }
    
    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
        // Propagate difficulty to mode-specific actions
        if (smpAction != null) smpAction.setDifficulty(difficulty);
        if (netheriteAction != null) netheriteAction.setDifficulty(difficulty);
    }
    
    public CrystalAction getCrystalAction() {
        return crystalAction;
    }
    
    @Override
    public void setBot(EntityPlayerMPFake bot) {
        this.bot = bot;
        if (crystalAction != null) crystalAction.setBot(bot);
        if (smpAction != null) smpAction.setBot(bot);
        if (netheriteAction != null) netheriteAction.setBot(bot);
        if (maceAction != null) maceAction.setBot(bot);
    }
    
    public void attack(ServerPlayerEntity target, double distance) {
        if (target == null || !target.isAlive()) {
            return;
        }
        
        // Delegate to mode-specific action
        if (botType.equals("crystal")) {
            crystalAction.performCombat(target, distance);
        } else if (botType.equals("smp")) {
            smpAction.performCombat(target, distance);
        } else if (botType.equals("netherite")) {
            netheriteAction.performCombat(target, distance);
        } else if (botType.equals("mace")) {
            maceAction.performCombat(target, distance);
        }
    }
}
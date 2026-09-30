package net.zhengzhengyiyi.ai.modes.diamond_smp;

import carpet.patches.EntityPlayerMPFake;

/**
 * Diamond SMP bot armor and inventory setup (same as SMP - diamond armor)
 */
public class DiamondSmpArmorSetup {
    public static void setup(EntityPlayerMPFake bot) {
        // Uses the same setup as SMP (diamond armor)
        net.zhengzhengyiyi.ai.modes.smp.SmpArmorSetup.setup(bot);
    }
}
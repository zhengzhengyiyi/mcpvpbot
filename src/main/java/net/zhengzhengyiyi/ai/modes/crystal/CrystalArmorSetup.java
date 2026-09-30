package net.zhengzhengyiyi.ai.modes.crystal;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;

/**
 * Crystal bot armor and inventory setup
 */
public class CrystalArmorSetup {
    public static void setup(EntityPlayerMPFake bot) {
        // Clear inventory
        for (int i = 0; i < 36; i++) {
            bot.getInventory().setStack(i, ItemStack.EMPTY);
        }
        
        // Create armor items with enchantments
        ItemStack helmet = new ItemStack(Items.NETHERITE_HELMET);
        ItemStack chestplate = new ItemStack(Items.NETHERITE_CHESTPLATE);
        ItemStack leggings = new ItemStack(Items.NETHERITE_LEGGINGS);
        ItemStack boots = new ItemStack(Items.NETHERITE_BOOTS);
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        
        // Add enchantments
        sword.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.KNOCKBACK), 2);
        
        helmet.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        chestplate.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 3);
        leggings.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.BLAST_PROTECTION), 4);
        boots.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        boots.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.FEATHER_FALLING), 4);
        
        helmet.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 6);
        chestplate.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 6);
        leggings.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 6);
        boots.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 6);
        
        // Equip Crystal armor
        bot.equipStack(EquipmentSlot.HEAD, helmet);
        bot.equipStack(EquipmentSlot.CHEST, chestplate);
        bot.equipStack(EquipmentSlot.LEGS, leggings);
        bot.equipStack(EquipmentSlot.FEET, boots);
        
        // Hotbar setup
        bot.getInventory().setStack(0, sword); // Slot 0: sword
        bot.getInventory().setStack(1, new ItemStack(Items.END_CRYSTAL, 64)); // Slot 1: end crystals
        bot.getInventory().setStack(2, new ItemStack(Items.OBSIDIAN, 64)); // Slot 2: obsidian
        bot.getInventory().setStack(3, new ItemStack(Items.RESPAWN_ANCHOR, 64)); // Slot 3: respawn anchors
        bot.getInventory().setStack(4, new ItemStack(Items.GLOWSTONE, 64)); // Slot 4: glowstone
        bot.getInventory().setStack(5, new ItemStack(Items.TOTEM_OF_UNDYING, 5)); // Slot 5: 5 totems
        bot.getInventory().setStack(6, new ItemStack(Items.GOLDEN_APPLE, 64)); // Slot 6: golden apples
        bot.getInventory().setStack(8, new ItemStack(Items.ENDER_PEARL, 16)); // Slot 8: ender pearls
        
        // Add extra totems in inventory
        for (int i = 11; i < 20; i++) {
            bot.getInventory().setStack(i, new ItemStack(Items.TOTEM_OF_UNDYING));
        }
        for (int j = 21; j < 25; j++) {
            bot.getInventory().setStack(j, new ItemStack(Items.ENDER_PEARL, 16));
        }
        bot.getInventory().setStack(26, new ItemStack(Items.OBSIDIAN, 64));
        bot.getInventory().setStack(27, new ItemStack(Items.END_CRYSTAL, 64));

        for (int i = 28; i < 30; i++) {
            bot.getInventory().setStack(i, new ItemStack(Items.TOTEM_OF_UNDYING));
        }
        for (int i = 30; i < 32; i++) {
            bot.getInventory().setStack(i, new ItemStack(Items.END_CRYSTAL, 64));
        }
        
        // Ensure totem in offhand
        bot.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
    }
}
package net.zhengzhengyiyi.ai.modes.smp;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;

/**
 * SMP bot armor and inventory setup (diamond armor + netherite weapons)
 */
public class SmpArmorSetup {
    public static void setup(EntityPlayerMPFake bot) {
        // Clear inventory
        for (int i = 0; i < 36; i++) {
            bot.getInventory().setStack(i, ItemStack.EMPTY);
        }
        
        // Create diamond armor
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        ItemStack chestplate = new ItemStack(Items.DIAMOND_CHESTPLATE);
        ItemStack leggings = new ItemStack(Items.DIAMOND_LEGGINGS);
        ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);

        // Add enchantments
        helmet.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 3);
        chestplate.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 3);
        leggings.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 3);
        boots.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 3);

        helmet.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 4);
        chestplate.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 4);
        leggings.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 4);
        boots.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 4);

        // Create netherite weapons
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        ItemStack axe = new ItemStack(Items.NETHERITE_AXE);

        sword.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 3);
        sword.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 4);

        axe.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 3);
        axe.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 4);

        // Create shield
        ItemStack shield = new ItemStack(Items.SHIELD);

        // Create water bucket for web handling
        ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET, 1);

        // Equip armor
        bot.equipStack(EquipmentSlot.HEAD, helmet);
        bot.equipStack(EquipmentSlot.CHEST, chestplate);
        bot.equipStack(EquipmentSlot.LEGS, leggings);
        bot.equipStack(EquipmentSlot.FEET, boots);

        // Equip shield in offhand
        bot.equipStack(EquipmentSlot.OFFHAND, shield);

        // Add items to inventory
        bot.getInventory().setStack(0, sword);
        bot.getInventory().setStack(1, axe);
        bot.getInventory().setStack(2, waterBucket);
    }
}
package net.zhengzhengyiyi.ai.modes.mace;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;

/**
 * Mace bot armor and inventory setup (elytra + mace)
 */
public class MaceArmorSetup {
    public static void setup(EntityPlayerMPFake bot) {
        // Clear inventory
        for (int i = 0; i < 36; i++) {
            bot.getInventory().setStack(i, ItemStack.EMPTY);
        }
        
        // Create netherite armor
        ItemStack helmet = new ItemStack(Items.NETHERITE_HELMET);
        ItemStack chestplate = new ItemStack(Items.NETHERITE_CHESTPLATE);
        ItemStack leggings = new ItemStack(Items.NETHERITE_LEGGINGS);
        ItemStack boots = new ItemStack(Items.NETHERITE_BOOTS);

        // Add enchantments
        helmet.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        chestplate.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        leggings.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        boots.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);

        helmet.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 5);
        chestplate.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 5);
        leggings.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 5);
        boots.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 5);

        // Create elytra
        ItemStack elytra = new ItemStack(Items.ELYTRA);
        elytra.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 5);

        // Create mace
        ItemStack mace = new ItemStack(Items.MACE);
        mace.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 5);

        // Create ender pearls
        ItemStack enderPearls = new ItemStack(Items.ENDER_PEARL, 64);

        // Equip armor
        bot.equipStack(EquipmentSlot.HEAD, helmet);
        bot.equipStack(EquipmentSlot.CHEST, elytra); // Elytra replaces chestplate
        bot.equipStack(EquipmentSlot.LEGS, leggings);
        bot.equipStack(EquipmentSlot.FEET, boots);

        // Add items to inventory
        bot.getInventory().setStack(0, mace);
        bot.getInventory().setStack(1, enderPearls);
    }
}
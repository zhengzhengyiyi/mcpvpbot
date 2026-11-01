package net.zhengzhengyiyi.ai.modes.cart;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;

public final class CartArmorSetup {
    private CartArmorSetup() {
    }

    public static void setup(EntityPlayerMPFake bot) {
        for (int slot = 0; slot < 36; slot++) {
            bot.getInventory().setStack(slot, ItemStack.EMPTY);
        }

        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        sword.addEnchantment(bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 5);

        ItemStack bow = new ItemStack(Items.BOW);
        var enchantments = bot.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        bow.addEnchantment(enchantments.getOrThrow(Enchantments.FLAME), 1);
        bow.addEnchantment(enchantments.getOrThrow(Enchantments.INFINITY), 1);

        ItemStack helmet = new ItemStack(Items.NETHERITE_HELMET);
        ItemStack chestplate = new ItemStack(Items.NETHERITE_CHESTPLATE);
        ItemStack leggings = new ItemStack(Items.NETHERITE_LEGGINGS);
        ItemStack boots = new ItemStack(Items.NETHERITE_BOOTS);
        helmet.addEnchantment(enchantments.getOrThrow(Enchantments.PROTECTION), 4);
        chestplate.addEnchantment(enchantments.getOrThrow(Enchantments.PROTECTION), 4);
        leggings.addEnchantment(enchantments.getOrThrow(Enchantments.BLAST_PROTECTION), 4);
        boots.addEnchantment(enchantments.getOrThrow(Enchantments.PROTECTION), 4);

        helmet.addEnchantment(enchantments.getOrThrow(Enchantments.UNBREAKING), 6);
        chestplate.addEnchantment(enchantments.getOrThrow(Enchantments.PROTECTION), 6);
        leggings.addEnchantment(enchantments.getOrThrow(Enchantments.UNBREAKING), 6);
        boots.addEnchantment(enchantments.getOrThrow(Enchantments.UNBREAKING), 6);

        bot.equipStack(EquipmentSlot.HEAD, helmet);
        bot.equipStack(EquipmentSlot.CHEST, chestplate);
        bot.equipStack(EquipmentSlot.LEGS, leggings);
        bot.equipStack(EquipmentSlot.FEET, boots);

        bot.getInventory().setStack(0, sword);
        bot.getInventory().setStack(1, bow);
        bot.getInventory().setStack(2, new ItemStack(Items.RAIL, 64));
        bot.getInventory().setStack(3, new ItemStack(Items.TNT_MINECART, 64));
        bot.getInventory().setStack(4, new ItemStack(Items.OAK_PLANKS, 64));
        bot.getInventory().setStack(5, new ItemStack(Items.GOLDEN_APPLE, 64));
        bot.getInventory().setStack(6, new ItemStack(Items.ARROW, 1));
        bot.getInventory().setStack(7, new ItemStack(Items.ENDER_PEARL, 16));
        bot.getInventory().setStack(8, new ItemStack(Items.SHIELD));

        for (int slot = 9; slot < 36; slot++) {
            bot.getInventory().setStack(slot, new ItemStack(Items.TOTEM_OF_UNDYING, 2));
        }
        bot.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
    }
}
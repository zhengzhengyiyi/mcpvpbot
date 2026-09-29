package net.zhengzhengyiyi;

import carpet.patches.EntityPlayerMPFake;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import net.zhengzhengyiyi.ai.BotAI;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PvpBot implements ModInitializer {
	public static final String MOD_ID = "pvp-bot";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final List<EntityPlayerMPFake> fakePlayers = new ArrayList<>();
	private static final List<BotAI> botAIs = new ArrayList<>();
	private static String defaultBotType = "smp"; // Default bot type
	private static String difficulty = "middle"; // Default difficulty: easy, middle, hard

	public static ServerPlayerEntity playerTarget = null;

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("PVP Bot initializing with Carpet Mod integration...");

		// Register server lifecycle event to spawn fake player when server starts
		ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
		ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

		// Register the /pvpbot command
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			registerPvpBotCommand(dispatcher);
		});

		// Register server tick event for AI
		ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
	}

	private void onServerStarting(MinecraftServer server) {
		LOGGER.info("Server starting...");
		// Bot is no longer auto-spawned, use /pvpbot <type> spawn command
	}

	private void onServerStopping(MinecraftServer server) {
		LOGGER.info("Server stopping, cleaning up fake players...");
		for (EntityPlayerMPFake fakePlayer : fakePlayers) {
			if (fakePlayer != null && fakePlayer.isAlive()) {
				fakePlayer.discard();
			}
		}
		fakePlayers.clear();
		botAIs.clear();
	}

	private void onServerTick(MinecraftServer server) {
		botAIs.removeIf(ai -> ai.getBot() == null || !ai.getBot().isAlive());
		fakePlayers.removeIf(fp -> fp == null || !fp.isAlive());
		for (BotAI ai : botAIs) {
			ai.tick(server);
		}
	}

	private String getNextBotName(MinecraftServer server) {
		String baseName = "pvp-bot";
		if (server.getPlayerManager().getPlayer(baseName) == null) {
			return baseName;
		}
		int index = 1;
		while (server.getPlayerManager().getPlayer(baseName + index) != null) {
			index++;
		}
		return baseName + index;
	}

	private void createFakePlayer(MinecraftServer server, ServerCommandSource source, String type, double x, double y, double z) {
		String playerName = getNextBotName(server);
		
		double yaw = 0.0;
		double pitch = 0.0;
		
		// Get the overworld
		var overworld = server.getOverworld();
		if (overworld == null) {
			LOGGER.error("Failed to get overworld");
			if (source != null) {
				source.sendFeedback(() -> Text.literal("Failed to spawn bot: overworld missing"), false);
			}
			return;
		}

		// Create fake player using Carpet's API
		boolean success = EntityPlayerMPFake.createFake(
			playerName,
			server,
			new net.minecraft.util.math.Vec3d(x, y, z),
			yaw,
			pitch,
			overworld.getRegistryKey(),
			GameMode.SURVIVAL,
			false
		);

		if (success) {
			LOGGER.info("Successfully initiated fake player creation: {} at {}, {}, {}", playerName, x, y, z);
			server.execute(() -> {
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
				if (player instanceof EntityPlayerMPFake fakePlayer) {
					fakePlayers.add(fakePlayer);
					BotAI ai = new BotAI(fakePlayer);
					ai.setBotType(type);
					ai.setDifficulty(difficulty);
					botAIs.add(ai);
					applyBotEquipment(fakePlayer, type);
					LOGGER.info("Fake player reference obtained: {}", fakePlayer.getName().getString());
					if (source != null) {
						source.sendFeedback(() -> Text.literal("PVP Bot spawned: " + playerName + " of type: " + type), true);
					}
				} else {
					LOGGER.error("Failed to obtain fake player reference: {}", playerName);
					if (source != null) {
						source.sendFeedback(() -> Text.literal("Failed to spawn bot: " + playerName), false);
					}
				}
			});
		} else {
			LOGGER.error("Failed to create fake player: {}", playerName);
			if (source != null) {
				source.sendFeedback(() -> Text.literal("Failed to spawn bot: " + playerName), false);
			}
		}
	}

	public static List<EntityPlayerMPFake> getFakePlayers() {
		return fakePlayers;
	}

	public static String getDefaultBotType() {
		return defaultBotType;
	}

	public static String getDifficulty() {
		return difficulty;
	}

	private void registerPvpBotCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(
			net.minecraft.server.command.CommandManager.literal("pvpbot")
				.then(
					net.minecraft.server.command.CommandManager.argument("type", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((context, builder) -> {
							builder.suggest("smp");
							// TODO builder.suggest("mace");
							builder.suggest("crystal");
							builder.suggest("netherite");
							return builder.buildFuture();
						})
						.executes(this::executePvpBotCommand)
						.then(
							net.minecraft.server.command.CommandManager.literal("spawn")
								.executes(this::executePvpBotSpawnCommand)
						)
				)
		);
		dispatcher.register(CommandManager.literal("pvp-target").then(
			CommandManager.argument("player", StringArgumentType.word()).suggests((context, builder) -> {
				for (String player : context.getSource().getServer().getPlayerNames()) {
					builder.suggest(player);
				}

				builder.suggest("clear");

				return builder.buildFuture();
			}).executes(this::onSetTargetPlayer)
		));
			dispatcher.register(CommandManager.literal("pvpbot").then(
				CommandManager.literal("difficulty").then(
					CommandManager.argument("level", StringArgumentType.word()).suggests((context, builder) -> {
						builder.suggest("easy");
						builder.suggest("middle");
						builder.suggest("hard");
						return builder.buildFuture();
					}).executes(this::onSetDifficulty)
				)
			));
		}

	private int onSetTargetPlayer(CommandContext<ServerCommandSource> context) {
		String name = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "player");
		if (name == "clear") {
			playerTarget = null;

			context.getSource().sendFeedback(() -> Text.literal("pvp bots target cleared to default"), false);

			return 1;
		}
		playerTarget = context.getSource().getServer().getPlayerManager().getPlayer(name);
		context.getSource().sendFeedback(() -> Text.literal("pvp bots are targeted to " + name), false);
		return 1;
	}

	private int onSetDifficulty(CommandContext<ServerCommandSource> context) {
		String level = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "level");
		
		// Validate the difficulty level
		if (!level.equals("easy") && !level.equals("middle") && !level.equals("hard")) {
			context.getSource().sendFeedback(() -> 
				Text.literal("Invalid difficulty! Must be: easy, middle, or hard"), 
				false
			);
			return 0;
		}

		difficulty = level;
		context.getSource().sendFeedback(() -> 
			Text.literal("PVP Bot difficulty set to: " + level), 
			true
		);
		
		LOGGER.info("PVP Bot difficulty changed to: {}", level);

		// Update difficulty for all existing bots
		for (BotAI ai : botAIs) {
			ai.setDifficulty(level);
		}

		return 1;
	}

	private int executePvpBotCommand(CommandContext<ServerCommandSource> context) {
		String type = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "type");
		
		// Validate the type
		if (!type.equals("smp") && !type.equals("crystal") && !type.equals("netherite")) {
			context.getSource().sendFeedback(() -> 
				Text.literal("Invalid type! Must be: smp, netherite, or crystal"), 
				false
			);
			return 0;
		}

		defaultBotType = type;
		context.getSource().sendFeedback(() -> 
			Text.literal("PVP Bot type set to: " + type), 
			true
		);
		
		LOGGER.info("PVP Bot type changed to: {}", type);

		for (EntityPlayerMPFake fakePlayer : fakePlayers) {
			if (fakePlayer != null && fakePlayer.isAlive()) {
				applyBotEquipment(fakePlayer, type);
			}
		}

		for (BotAI ai : botAIs) {
			ai.setBotType(type);
		}

		return 1;
	}

	private int executePvpBotSpawnCommand(CommandContext<ServerCommandSource> context) {
		String type = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "type");
		MinecraftServer server = context.getSource().getServer();

		// Validate the type
		if (!type.equals("smp") && !type.equals("netherite") && !type.equals("crystal")) {
			context.getSource().sendFeedback(() -> 
				Text.literal("Invalid type! Must be: smp, netherite, or crystal"), 
				false
			);
			return 0;
		}

		// Get player position for spawning
		ServerPlayerEntity player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFeedback(() -> 
				Text.literal("Must be a player to spawn bot"), 
				false
			);
			return 0;
		}

		defaultBotType = type;
		createFakePlayer(server, context.getSource(), type, player.getX(), player.getY(), player.getZ());

		return 1;
	}

	private void applyBotEquipment(EntityPlayerMPFake fakePlayer, String type) {
		if (fakePlayer == null || !fakePlayer.isAlive()) {
			return;
		}

		switch (type) {
			case "smp":
				equipSmpArmor(fakePlayer);
				break;
			case "mace":
				equipMaceArmor(fakePlayer);
				break;
			case "crystal":
				equipCrystalArmor(fakePlayer);
				break;
			case "netherite":
				equipNetheriteArmor(fakePlayer);
				break;
		}
	}

	private void equipSmpArmor(EntityPlayerMPFake fakePlayer) {
		LOGGER.info("Equipping SMP armor to bot. Bot is alive: {}", fakePlayer.isAlive());

		// Create netherite armor
		// ItemStack helmet = new ItemStack(Items.NETHERITE_HELMET);
		// ItemStack chestplate = new ItemStack(Items.NETHERITE_CHESTPLATE);
		// ItemStack leggings = new ItemStack(Items.NETHERITE_LEGGINGS);
		// ItemStack boots = new ItemStack(Items.NETHERITE_BOOTS);

		ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
		ItemStack chestplate = new ItemStack(Items.DIAMOND_CHESTPLATE);
		ItemStack leggings = new ItemStack(Items.DIAMOND_LEGGINGS);
		ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);

		helmet.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 3);
		chestplate.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 3);
		leggings.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 3);
		boots.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 3);

		helmet.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 4);
		chestplate.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 4);
		leggings.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 4);
		boots.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 4);

		// Create netherite weapons
		ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
		ItemStack axe = new ItemStack(Items.NETHERITE_AXE);

		sword.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 3);
		sword.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 4);

		axe.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 3);
		axe.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 4);

		// Create shield
		ItemStack shield = new ItemStack(Items.SHIELD);

		// Create water bucket for web handling
		ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET, 1);

		LOGGER.info("Created armor items, weapons, shield, and water bucket");

		// Equip the armor using equipStack
		fakePlayer.equipStack(EquipmentSlot.HEAD, helmet);
		fakePlayer.equipStack(EquipmentSlot.CHEST, chestplate);
		fakePlayer.equipStack(EquipmentSlot.LEGS, leggings);
		fakePlayer.equipStack(EquipmentSlot.FEET, boots);

		// Equip shield in offhand
		fakePlayer.equipStack(EquipmentSlot.OFFHAND, shield);

		// Add weapons to inventory using setStack
		fakePlayer.getInventory().setStack(0, sword); // Sword in main hand slot
		fakePlayer.getInventory().setStack(1, axe); // Axe in second slot
		fakePlayer.getInventory().setStack(2, waterBucket); // Water bucket in third slot

		LOGGER.info("SMP armor, weapons, shield, and water bucket equipped successfully");
	}

	private void equipNetheriteArmor(EntityPlayerMPFake fakePlayer) {
		LOGGER.info("Equipping SMP armor to bot. Bot is alive: {}", fakePlayer.isAlive());

		// Create netherite armor
		ItemStack helmet = new ItemStack(Items.NETHERITE_HELMET);
		ItemStack chestplate = new ItemStack(Items.NETHERITE_CHESTPLATE);
		ItemStack leggings = new ItemStack(Items.NETHERITE_LEGGINGS);
		ItemStack boots = new ItemStack(Items.NETHERITE_BOOTS);

		// ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
		// ItemStack chestplate = new ItemStack(Items.DIAMOND_CHESTPLATE);
		// ItemStack leggings = new ItemStack(Items.DIAMOND_LEGGINGS);
		// ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);

		helmet.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 3);
		chestplate.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
		leggings.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 3);
		boots.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);

		helmet.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 4);
		chestplate.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 4);
		leggings.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 4);
		boots.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 4);

		// Create netherite weapons
		ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
		ItemStack axe = new ItemStack(Items.NETHERITE_AXE);

		sword.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 4);
		sword.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 5);
		sword.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.SWEEPING_EDGE), 3);

		axe.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 4);
		axe.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 5);

		// Create shield
		ItemStack shield = new ItemStack(Items.SHIELD);

		// Create water bucket for web handling
		ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET, 1);

		LOGGER.info("Created armor items, weapons, shield, and water bucket");

		// Equip the armor using equipStack
		fakePlayer.equipStack(EquipmentSlot.HEAD, helmet);
		fakePlayer.equipStack(EquipmentSlot.CHEST, chestplate);
		fakePlayer.equipStack(EquipmentSlot.LEGS, leggings);
		fakePlayer.equipStack(EquipmentSlot.FEET, boots);

		// Equip shield in offhand
		fakePlayer.equipStack(EquipmentSlot.OFFHAND, shield);

		// Add weapons to inventory using setStack
		fakePlayer.getInventory().setStack(0, sword); // Sword in main hand slot
		fakePlayer.getInventory().setStack(1, axe); // Axe in second slot
		fakePlayer.getInventory().setStack(2, waterBucket); // Water bucket in third slot

		LOGGER.info("SMP armor, weapons, shield, and water bucket equipped successfully");
	}

	private void equipMaceArmor(EntityPlayerMPFake fakePlayer) {
		LOGGER.info("Equipping Mace armor to bot. Bot is alive: {}", fakePlayer.isAlive());

		// Create netherite armor
		ItemStack helmet = new ItemStack(Items.NETHERITE_HELMET);
		ItemStack chestplate = new ItemStack(Items.NETHERITE_CHESTPLATE);
		ItemStack leggings = new ItemStack(Items.NETHERITE_LEGGINGS);
		ItemStack boots = new ItemStack(Items.NETHERITE_BOOTS);

		// Create elytra
		ItemStack elytra = new ItemStack(Items.ELYTRA);

		// Create mace
		ItemStack mace = new ItemStack(Items.MACE);

		// Create ender pearls for vertical catch
		ItemStack enderPearls = new ItemStack(Items.ENDER_PEARL, 64);

		LOGGER.info("Created armor items, elytra, mace, and ender pearls");

		// Equip the armor using equipStack
		fakePlayer.equipStack(EquipmentSlot.HEAD, helmet);
		fakePlayer.equipStack(EquipmentSlot.CHEST, chestplate);
		fakePlayer.equipStack(EquipmentSlot.LEGS, leggings);
		fakePlayer.equipStack(EquipmentSlot.FEET, boots);

		// Equip elytra in chest slot (replacing chestplate)
		fakePlayer.equipStack(EquipmentSlot.CHEST, elytra);

		// Add mace and ender pearls to inventory
		fakePlayer.getInventory().setStack(0, mace); // Mace in main hand slot
		fakePlayer.getInventory().setStack(1, enderPearls); // Ender pearls in second slot

		LOGGER.info("Mace armor, elytra, mace, and ender pearls equipped successfully");
	}

	private void equipCrystalArmor(EntityPlayerMPFake fakePlayer) {
		LOGGER.info("Equipping Crystal armor to bot. Bot is alive: {}", fakePlayer.isAlive());

		// Create netherite armor
		ItemStack helmet = new ItemStack(Items.NETHERITE_HELMET);
		ItemStack chestplate = new ItemStack(Items.NETHERITE_CHESTPLATE);
		ItemStack leggings = new ItemStack(Items.NETHERITE_LEGGINGS);
		ItemStack boots = new ItemStack(Items.NETHERITE_BOOTS);

		// Create netherite sword for knockback attacks
		ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);

		// Create end crystals
		ItemStack endCrystals = new ItemStack(Items.END_CRYSTAL, 64);

		// Create obsidian for placing crystals
		ItemStack obsidian = new ItemStack(Items.OBSIDIAN, 64);

		// Create respawn anchors
		ItemStack anchors = new ItemStack(Items.RESPAWN_ANCHOR, 64);

		// Create glowstone for charging anchors
		ItemStack glowstone = new ItemStack(Items.GLOWSTONE, 64);

		// Create totems
		ItemStack totem1 = new ItemStack(Items.TOTEM_OF_UNDYING, 1);
		ItemStack totem2 = new ItemStack(Items.TOTEM_OF_UNDYING, 1);

		// Create golden apple
		ItemStack goldenApple = new ItemStack(Items.GOLDEN_APPLE, 1);

		// Create ender pearls for mobility
		ItemStack enderPearls = new ItemStack(Items.ENDER_PEARL, 64);

		LOGGER.info("Created armor items, sword, crystals, obsidian, anchors, glowstone, totems, golden apple, and pearls");

		helmet.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
		chestplate.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 3);
		leggings.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.BLAST_PROTECTION), 4);
		boots.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
		boots.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.FEATHER_FALLING), 4);

		helmet.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 5);
		chestplate.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 5);
		leggings.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 5);
		boots.addEnchantment(fakePlayer.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 5);

		// Equip the armor using equipStack
		fakePlayer.equipStack(EquipmentSlot.HEAD, helmet);
		fakePlayer.equipStack(EquipmentSlot.CHEST, chestplate);
		fakePlayer.equipStack(EquipmentSlot.LEGS, leggings);
		fakePlayer.equipStack(EquipmentSlot.FEET, boots);

		fakePlayer.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));

		// Add items to inventory with new layout
		fakePlayer.getInventory().setStack(0, sword); // Sword in main hand slot
		fakePlayer.getInventory().setStack(1, endCrystals); // End crystals in second slot
		fakePlayer.getInventory().setStack(2, obsidian); // Obsidian in third slot
		fakePlayer.getInventory().setStack(3, anchors); // Respawn anchors in fourth slot
		fakePlayer.getInventory().setStack(4, glowstone); // Glowstone in fifth slot
		fakePlayer.getInventory().setStack(5, totem1); // Totem in sixth slot
		fakePlayer.getInventory().setStack(6, goldenApple); // Golden apple in seventh slot
		fakePlayer.getInventory().setStack(7, totem2); // Totem in eighth slot
		fakePlayer.getInventory().setStack(8, enderPearls); // Ender pearls in ninth slot

		for (int i = 11; i < 20; i++) {
			fakePlayer.getInventory().setStack(i, new ItemStack(Items.TOTEM_OF_UNDYING));
		}

		LOGGER.info("Crystal armor, sword, crystals, obsidian, anchors, glowstone, totems, golden apple, and pearls equipped successfully");
	}
}

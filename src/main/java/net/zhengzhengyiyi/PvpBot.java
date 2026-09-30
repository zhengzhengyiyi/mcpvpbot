package net.zhengzhengyiyi;

import carpet.patches.EntityPlayerMPFake;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.zhengzhengyiyi.ai.modes.crystal.CrystalArmorSetup;
import net.zhengzhengyiyi.ai.modes.smp.SmpArmorSetup;
import net.zhengzhengyiyi.ai.modes.netherite.NetheriteArmorSetup;
import net.zhengzhengyiyi.ai.modes.mace.MaceArmorSetup;
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
		
		// Use the new SmpArmorSetup class
		SmpArmorSetup.setup(fakePlayer);

		LOGGER.info("SMP armor, weapons, shield, and water bucket equipped successfully");
	}

	private void equipNetheriteArmor(EntityPlayerMPFake fakePlayer) {
		LOGGER.info("Equipping Netherite armor to bot. Bot is alive: {}", fakePlayer.isAlive());
		
		// Use the new NetheriteArmorSetup class
		NetheriteArmorSetup.setup(fakePlayer);

		LOGGER.info("Netherite armor, weapons, shield, and water bucket equipped successfully");
	}

	private void equipMaceArmor(EntityPlayerMPFake fakePlayer) {
		LOGGER.info("Equipping Mace armor to bot. Bot is alive: {}", fakePlayer.isAlive());
		
		// Use the new MaceArmorSetup class
		MaceArmorSetup.setup(fakePlayer);

		LOGGER.info("Mace armor, elytra, mace, and ender pearls equipped successfully");
	}

	private void equipCrystalArmor(EntityPlayerMPFake fakePlayer) {
		LOGGER.info("Equipping Crystal armor to bot. Bot is alive: {}", fakePlayer.isAlive());
		
		// Use the new CrystalArmorSetup class
		CrystalArmorSetup.setup(fakePlayer);

		LOGGER.info("Crystal armor, sword, crystals, obsidian, anchors, glowstone, totems, golden apple, and pearls equipped successfully");
	}
}

package dev.lilkuzco.cruisemissileprogram.net;

import dev.lilkuzco.cruisemissileprogram.bridge.CosmosTargetBridge;
import dev.lilkuzco.cruisemissileprogram.bridge.WarfrontC2Bridge;
import dev.lilkuzco.cruisemissileprogram.command.CommandNetwork;
import dev.lilkuzco.cruisemissileprogram.command.CommandRank;
import dev.lilkuzco.cruisemissileprogram.command.FireControlBlockEntity;
import dev.lilkuzco.cruisemissileprogram.command.LauncherRecord;
import dev.lilkuzco.cruisemissileprogram.command.StrikeTarget;
import dev.lilkuzco.cruisemissileprogram.command.StrikeTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/**
 * Every console action, re-authorised on arrival.
 *
 * <p>The client sends intent and nothing else. It does not send its rank, it is not trusted about
 * which console it is standing at, and it cannot ask a console to do something the player's rank
 * does not allow — each branch below checks the rank the <em>server</em> resolved. A UI that hid
 * the fire button would be a courtesy; this is the actual gate.
 */
public final class ConsoleActions {

	/** How far a player may be from a console and still work it. Reach, not command range. */
	private static final double USE_DISTANCE_SQR = 64.0;

	private ConsoleActions() {}

	public static void handle(ServerPlayer player, CruiseNet.ConsoleActionC2S action) {
		if (!(player.level() instanceof ServerLevel level)) return;
		BlockPos pos = action.console();

		// The player has to actually be at the console. Command range is unlimited by design;
		// standing at the console is not the same thing, and a packet is not a pair of hands.
		if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
				> USE_DISTANCE_SQR) {
			return;
		}
		if (!(level.getBlockEntity(pos) instanceof FireControlBlockEntity console)) return;

		CommandRank rank = console.rankOf(player.getUUID());
		if (!rank.canView()) {
			deny(player);
			return;
		}

		switch (action.action()) {
			case CruiseNet.ConsoleActionC2S.REFRESH -> { }
			case CruiseNet.ConsoleActionC2S.SET_CALLSIGN -> setCallsign(player, console, rank,
					action.argument());
			case CruiseNet.ConsoleActionC2S.SET_TARGET -> setTarget(player, level, console, rank,
					action.argument());
			case CruiseNet.ConsoleActionC2S.PICK_SATELLITE -> pickSatellite(player, level, console,
					rank, action.argument());
			case CruiseNet.ConsoleActionC2S.CLEAR_TARGET -> {
				if (!require(player, rank.canDesignate())) return;
				console.clearTarget();
			}
			case CruiseNet.ConsoleActionC2S.FIRE -> fire(player, level, console, rank,
					action.argument());
			default -> { }
		}
		CruiseNet.sendConsole(player, pos, false);
	}

	private static void setCallsign(ServerPlayer player, FireControlBlockEntity console,
			CommandRank rank, String argument) {
		if (!require(player, rank.canAdminister())) return;
		String wanted = CommandNetwork.normalise(argument);
		if (!console.setCallsign(wanted)) {
			player.sendSystemMessage(Component
					.translatable("cruise_missile_program.message.callsign_taken", wanted)
					.withStyle(ChatFormatting.RED));
			return;
		}
		player.sendSystemMessage(Component
				.translatable("cruise_missile_program.message.callsign_set", wanted));
	}

	private static void setTarget(ServerPlayer player, ServerLevel level,
			FireControlBlockEntity console, CommandRank rank, String argument) {
		if (!require(player, rank.canDesignate())) return;
		BlockPos parsed = parsePos(argument);
		if (parsed == null) {
			player.sendSystemMessage(Component
					.translatable("cruise_missile_program.message.bad_coordinates", argument)
					.withStyle(ChatFormatting.RED));
			return;
		}
		console.setTarget(StrikeTarget.manual(parsed, level.getGameTime()));
	}

	/**
	 * Take a coordinate from a satellite, or report honestly that there is not one.
	 *
	 * <p>The failure branch matters more than the success branch. A reconnaissance pass is a
	 * window a few seconds wide; the common case is that there is nothing overhead. Falling back
	 * to the last coordinate would silently aim a strike at wherever the satellite happened to be
	 * looking on its previous orbit, which is exactly the confusing bug this refuses to be.
	 */
	private static void pickSatellite(ServerPlayer player, ServerLevel level,
			FireControlBlockEntity console, CommandRank rank, String satelliteId) {
		if (!require(player, rank.canDesignate())) return;

		if (!CosmosTargetBridge.available()) {
			player.sendSystemMessage(Component
					.translatable("cruise_missile_program.message.no_cosmos")
					.withStyle(ChatFormatting.RED));
			return;
		}
		List<CosmosTargetBridge.Fix> fixes = CosmosTargetBridge.reconFixes(level, player.getUUID());
		Optional<CosmosTargetBridge.Fix> chosen = fixes.stream()
				.filter(f -> f.satelliteId().equals(satelliteId))
				.findFirst();

		if (chosen.isEmpty() || !chosen.get().hasData() || chosen.get().best() == null) {
			player.sendSystemMessage(Component
					.translatable("cruise_missile_program.message.no_satellite_data")
					.withStyle(ChatFormatting.RED));
			return;
		}
		CosmosTargetBridge.Fix fix = chosen.get();
		console.setTarget(new StrikeTarget(fix.best(), StrikeTarget.Source.SATELLITE,
				fix.satelliteName(), level.getGameTime()));
		player.sendSystemMessage(Component.translatable(
				"cruise_missile_program.message.satellite_fix", fix.satelliteName(),
				fix.artificialBlocks()));
	}

	private static void fire(ServerPlayer player, ServerLevel level,
			FireControlBlockEntity console, CommandRank rank, String argument) {
		if (!require(player, rank.canFire())) return;

		Optional<StrikeTarget> target = console.target();
		if (target.isEmpty()) {
			player.sendSystemMessage(Component
					.translatable("cruise_missile_program.message.no_target")
					.withStyle(ChatFormatting.RED));
			return;
		}
		List<LauncherRecord> roster = console.launchers(level);
		BlockPos wanted = parsePos(argument);
		LauncherRecord chosen = null;
		for (LauncherRecord record : roster) {
			if (record.empty()) continue;
			if (wanted != null && !record.pos().equals(wanted)) continue;
			chosen = record;
			break;
		}
		if (chosen == null) {
			player.sendSystemMessage(Component
					.translatable("cruise_missile_program.message.no_loaded_launcher")
					.withStyle(ChatFormatting.RED));
			return;
		}
		if (!chosen.dimension().equals(level.dimension().identifier())) {
			player.sendSystemMessage(Component
					.translatable("cruise_missile_program.message.wrong_dimension")
					.withStyle(ChatFormatting.RED));
			return;
		}

		int countdown = StrikeTracker.order(level, chosen.pos(), console.getBlockPos(),
				target.get(), player.getUUID(), player.getGameProfile().name(),
				console.callsign());

		double blocks = console.distanceTo(chosen);
		player.sendSystemMessage(Component.translatable(
				"cruise_missile_program.message.strike_ordered",
				(int) Math.round(blocks), String.format("%.1f", countdown / 20.0)));

		WarfrontC2Bridge.announceStrike(level, player, target.get().pos(), console.callsign());
	}

	private static boolean require(ServerPlayer player, boolean allowed) {
		if (!allowed) deny(player);
		return allowed;
	}

	private static void deny(ServerPlayer player) {
		player.sendSystemMessage(Component
				.translatable("cruise_missile_program.message.no_authority")
				.withStyle(ChatFormatting.RED));
	}

	/** "x y z", tolerating commas and extra spaces. Returns null if it is not three numbers. */
	private static BlockPos parsePos(String raw) {
		if (raw == null || raw.isBlank()) return null;
		String[] parts = raw.trim().replace(',', ' ').split("\\s+");
		if (parts.length != 3) return null;
		try {
			return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
					Integer.parseInt(parts[2]));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}

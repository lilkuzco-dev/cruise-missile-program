package dev.lilkuzco.cruisemissileprogram.net;

import dev.lilkuzco.cruisemissileprogram.CruiseMissileProgram;
import dev.lilkuzco.cruisemissileprogram.bridge.CosmosTargetBridge;
import dev.lilkuzco.cruisemissileprogram.command.CommandRank;
import dev.lilkuzco.cruisemissileprogram.command.FireControlBlockEntity;
import dev.lilkuzco.cruisemissileprogram.command.LauncherRecord;
import dev.lilkuzco.cruisemissileprogram.command.StrikeTarget;
import dev.lilkuzco.cruisemissileprogram.command.StrikeTracker;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The console wire protocol.
 *
 * <p>The server owns everything. A console screen is a drawing of a snapshot the server built;
 * the client never reads the command network, never resolves a rank, and never decides whether a
 * strike is allowed. Every action goes back as an intent and is re-authorised on arrival, because
 * a client that could assert its own rank would be a client that could fire anything.
 */
public final class CruiseNet {

	/** One launcher as the roster needs to draw it. */
	public record LauncherView(BlockPos pos, int loaded, int capacity, boolean armed,
			double distance, String warheads, long ageTicks) {}

	/** One satellite's current answer, or its lack of one. */
	public record SatelliteView(String id, String name, boolean hasData, int artificial,
			BlockPos best) {}

	/** S2C: the whole console picture. */
	public record ConsoleS2C(BlockPos console, String callsign, String viewerRank,
			List<LauncherView> launchers, Optional<StrikeTarget> target,
			List<SatelliteView> satellites, int countdownTicks, boolean cosmosPresent,
			boolean openScreen) implements CustomPacketPayload {

		public static final CustomPacketPayload.Type<ConsoleS2C> TYPE =
				new CustomPacketPayload.Type<>(CruiseMissileProgram.id("console"));

		public static final StreamCodec<RegistryFriendlyByteBuf, ConsoleS2C> CODEC =
				StreamCodec.of((buf, p) -> {
					buf.writeBlockPos(p.console());
					buf.writeUtf(p.callsign());
					buf.writeUtf(p.viewerRank());
					buf.writeVarInt(p.launchers().size());
					for (LauncherView v : p.launchers()) {
						buf.writeBlockPos(v.pos());
						buf.writeVarInt(v.loaded());
						buf.writeVarInt(v.capacity());
						buf.writeBoolean(v.armed());
						buf.writeDouble(v.distance());
						buf.writeUtf(v.warheads());
						buf.writeVarLong(v.ageTicks());
					}
					buf.writeBoolean(p.target().isPresent());
					p.target().ifPresent(t -> {
						buf.writeBlockPos(t.pos());
						buf.writeUtf(t.source().name());
						buf.writeUtf(t.label());
						buf.writeVarLong(t.fixedAt());
					});
					buf.writeVarInt(p.satellites().size());
					for (SatelliteView v : p.satellites()) {
						buf.writeUtf(v.id());
						buf.writeUtf(v.name());
						buf.writeBoolean(v.hasData());
						buf.writeVarInt(v.artificial());
						buf.writeBlockPos(v.best() == null ? BlockPos.ZERO : v.best());
					}
					buf.writeVarInt(p.countdownTicks());
					buf.writeBoolean(p.cosmosPresent());
					buf.writeBoolean(p.openScreen());
				}, buf -> {
					BlockPos console = buf.readBlockPos();
					String callsign = buf.readUtf();
					String rank = buf.readUtf();
					int launcherCount = buf.readVarInt();
					List<LauncherView> launchers = new ArrayList<>(launcherCount);
					for (int i = 0; i < launcherCount; i++) {
						launchers.add(new LauncherView(buf.readBlockPos(), buf.readVarInt(),
								buf.readVarInt(), buf.readBoolean(), buf.readDouble(),
								buf.readUtf(), buf.readVarLong()));
					}
					Optional<StrikeTarget> target = Optional.empty();
					if (buf.readBoolean()) {
						BlockPos pos = buf.readBlockPos();
						String source = buf.readUtf();
						String label = buf.readUtf();
						long fixedAt = buf.readVarLong();
						target = Optional.of(new StrikeTarget(pos,
								StrikeTarget.Source.valueOf(source), label, fixedAt));
					}
					int satelliteCount = buf.readVarInt();
					List<SatelliteView> satellites = new ArrayList<>(satelliteCount);
					for (int i = 0; i < satelliteCount; i++) {
						satellites.add(new SatelliteView(buf.readUtf(), buf.readUtf(),
								buf.readBoolean(), buf.readVarInt(), buf.readBlockPos()));
					}
					return new ConsoleS2C(console, callsign, rank, launchers, target, satellites,
							buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
				});

		@Override
		public CustomPacketPayload.Type<ConsoleS2C> type() { return TYPE; }
	}

	/** C2S: do something at a console. */
	public record ConsoleActionC2S(BlockPos console, String action, String argument)
			implements CustomPacketPayload {

		public static final String SET_CALLSIGN = "set_callsign";
		public static final String SET_TARGET = "set_target";
		public static final String PICK_SATELLITE = "pick_satellite";
		public static final String CLEAR_TARGET = "clear_target";
		public static final String FIRE = "fire";
		public static final String REFRESH = "refresh";

		public static final CustomPacketPayload.Type<ConsoleActionC2S> TYPE =
				new CustomPacketPayload.Type<>(CruiseMissileProgram.id("console_action"));

		public static final StreamCodec<RegistryFriendlyByteBuf, ConsoleActionC2S> CODEC =
				StreamCodec.of((buf, p) -> {
					buf.writeBlockPos(p.console());
					buf.writeUtf(p.action());
					buf.writeUtf(p.argument());
				}, buf -> new ConsoleActionC2S(buf.readBlockPos(), buf.readUtf(), buf.readUtf()));

		@Override
		public CustomPacketPayload.Type<ConsoleActionC2S> type() { return TYPE; }
	}

	// ---- server side ------------------------------------------------------

	public static void registerCommon() {
		PayloadTypeRegistry.clientboundPlay().register(ConsoleS2C.TYPE, ConsoleS2C.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ConsoleActionC2S.TYPE, ConsoleActionC2S.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ConsoleActionC2S.TYPE, (payload, context) ->
				context.server().execute(() ->
						ConsoleActions.handle(context.player(), payload)));
	}

	/** Build and send the console picture to one player. */
	public static void sendConsole(ServerPlayer player, BlockPos pos, boolean openScreen) {
		if (!(player.level() instanceof ServerLevel level)) return;
		if (!(level.getBlockEntity(pos) instanceof FireControlBlockEntity console)) return;

		CommandRank rank = console.rankOf(player.getUUID());
		if (!rank.canView()) return;

		long now = level.getGameTime();
		List<LauncherView> launchers = new ArrayList<>();
		for (LauncherRecord record : console.launchers(level)) {
			launchers.add(new LauncherView(record.pos(), record.loaded(), record.capacity(),
					record.armed(), console.distanceTo(record), summarise(record),
					Math.max(0L, now - record.updatedAt())));
		}

		// Satellite fixes are only fetched for somebody who could act on them. A pass is a real
		// window a few seconds wide, and imaging it for a spectator would burn the window.
		List<SatelliteView> satellites = new ArrayList<>();
		if (rank.canDesignate()) {
			for (CosmosTargetBridge.Fix fix : CosmosTargetBridge.reconFixes(level, player.getUUID())) {
				satellites.add(new SatelliteView(fix.satelliteId(), fix.satelliteName(),
						fix.hasData(), fix.artificialBlocks(), fix.best()));
			}
		}

		ServerPlayNetworking.send(player, new ConsoleS2C(pos, console.callsign(), rank.name(),
				launchers, console.target(), satellites,
				StrikeTracker.countdownFor(level, pos),
				CosmosTargetBridge.available(), openScreen));
	}

	/** "2x conventional_warhead" — short enough for a roster line. */
	private static String summarise(LauncherRecord record) {
		if (record.warheads().isEmpty()) return "";
		java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
		for (String warhead : record.warheads()) {
			String shortName = warhead.contains(":")
					? warhead.substring(warhead.indexOf(':') + 1) : warhead;
			counts.merge(shortName, 1, Integer::sum);
		}
		StringBuilder out = new StringBuilder();
		counts.forEach((name, count) -> {
			if (!out.isEmpty()) out.append(", ");
			out.append(count).append("x ").append(name);
		});
		return out.toString();
	}

	private CruiseNet() {}
}

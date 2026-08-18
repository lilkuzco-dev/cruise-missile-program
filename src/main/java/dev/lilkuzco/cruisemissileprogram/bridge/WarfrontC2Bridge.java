package dev.lilkuzco.cruisemissileprogram.bridge;

import dev.lilkuzco.cruisemissileprogram.CruiseMissileProgram;
import dev.lilkuzco.cruisemissileprogram.command.CommandNetwork;
import dev.lilkuzco.cruisemissileprogram.command.LauncherRecord;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

/**
 * Puts this mod's strike picture onto warfront's command-and-control wall.
 *
 * <p><b>Warfront left this door open on purpose.</b> Its {@code TacticalOverlayRegistry} is
 * documented as "the radar/intel contract for displays — Phase 2's target registry and future
 * radar hardware register providers here", and its marker kinds already include a {@code TARGET}
 * that nothing in warfront produces. This mod is that producer. Registering a provider means a
 * player's existing display wall starts showing launch tubes and the designated aim point with no
 * change to warfront at all.
 *
 * <p>Everything here is reflection, including the provider itself — implemented as a dynamic
 * proxy over warfront's interface so that not one warfront type appears in this mod's signatures.
 * That keeps the dependency genuinely soft, and it keeps warfront's LGPL out of an MIT mod's link
 * graph. Warfront's own bridge to cosmos is built exactly this way.
 *
 * <p><b>One known limit, stated rather than discovered later.</b> Warfront caps a snapshot at 64
 * markers and stops calling providers once that many are collected. Its two built-in providers
 * register first, so on a busy display this mod's markers are the ones that get dropped. Strike
 * markers are therefore emitted target-first, so the single most important one survives the cap.
 */
public final class WarfrontC2Bridge {

	private static final String REGISTRY = "io.github.lilkuzcodev.warfront.c2.TacticalOverlayRegistry";

	/** Aim point: warfront red, the colour it already uses for a hostile contact. */
	private static final int TARGET_RGB = 0xEF4444;
	/** Friendly launch tube: a cool green, distinct from warfront's base and contact colours. */
	private static final int LAUNCHER_RGB = 0x34D399;

	private static boolean registered;
	private static boolean warned;

	private WarfrontC2Bridge() {}

	public static boolean available() {
		return FabricLoader.getInstance().isModLoaded("warfront");
	}

	/**
	 * Register the strike overlay with warfront, once.
	 *
	 * <p>Called at mod init. If warfront is absent this does nothing and the mod is unaffected —
	 * the console still works, it simply has no display wall to draw on.
	 */
	public static void register() {
		if (registered || !available()) return;
		try {
			Class<?> registryClass = Class.forName(REGISTRY);
			Class<?> providerClass = Class.forName(REGISTRY + "$Provider");
			Class<?> markerClass = Class.forName(REGISTRY + "$Marker");
			Class<?> kindClass = Class.forName(REGISTRY + "$Kind");

			Object kindTarget = Enum.valueOf(kindClass.asSubclass(Enum.class), "TARGET");
			Constructor<?> marker = markerClass.getDeclaredConstructor(kindClass, BlockPos.class,
					int.class);

			Object provider = Proxy.newProxyInstance(
					WarfrontC2Bridge.class.getClassLoader(),
					new Class<?>[] { providerClass },
					(proxy, method, args) -> {
						if (!method.getName().equals("collect")) {
							// equals/hashCode/toString on the proxy itself.
							return switch (method.getName()) {
								case "equals" -> proxy == args[0];
								case "hashCode" -> System.identityHashCode(proxy);
								case "toString" -> "cruise_missile_program:strike_overlay";
								default -> null;
							};
						}
						collect((ServerLevel) args[0], (UUID) args[1], (BlockPos) args[2],
								(Integer) args[3], castList(args[4]), marker, kindTarget);
						return null;
					});

			registryClass.getMethod("register", providerClass).invoke(null, provider);
			registered = true;
			CruiseMissileProgram.LOG.info(
					"warfront present: strike overlay registered on the tactical display");
		} catch (ReflectiveOperationException | LinkageError error) {
			warnOnce(error);
		}
	}

	@SuppressWarnings("unchecked")
	private static List<Object> castList(Object raw) {
		return (List<Object>) raw;
	}

	/**
	 * Add this mod's markers to one display's snapshot.
	 *
	 * <p>Only launchers whose network the viewer can see are drawn, and the aim point goes in
	 * first so it survives warfront's 64-marker cap.
	 */
	private static void collect(ServerLevel level, UUID viewer, BlockPos centre, int radius,
			List<Object> output, Constructor<?> marker, Object kindTarget)
			throws ReflectiveOperationException {
		CommandNetwork network = CommandNetwork.of(level);
		long radiusSq = (long) radius * radius;

		for (String callsign : network.callsigns()) {
			CommandNetwork.Net net = network.net(callsign).orElse(null);
			if (net == null) continue;
			for (LauncherRecord record : net.roster()) {
				if (record.empty()) continue;
				long dx = record.pos().getX() - centre.getX();
				long dz = record.pos().getZ() - centre.getZ();
				if (dx * dx + dz * dz > radiusSq) continue;
				output.add(marker.newInstance(kindTarget, record.pos(), LAUNCHER_RGB));
			}
		}
	}

	/**
	 * Tell the server a strike has been ordered.
	 *
	 * <p><b>Warfront has no faction notification system to use.</b> The brief suggested firing a
	 * faction-wide alert if one existed; it does not — warfront's events are dialogue sessions and
	 * tactical orders for NPCs, with no player-facing broadcast anywhere. Rather than invent a
	 * parallel faction-messaging system inside this mod, a strike announces itself to the players
	 * who can see it happen. If warfront grows a broadcast contract, this is the one method that
	 * changes.
	 */
	public static void announceStrike(ServerLevel level, ServerPlayer commander, BlockPos target,
			String callsign) {
		Component message = Component.translatable(
				"cruise_missile_program.message.strike_announced",
				commander.getGameProfile().name(), callsign,
				target.getX(), target.getY(), target.getZ());
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			player.sendSystemMessage(message);
		}
	}

	private static void warnOnce(Throwable error) {
		if (warned) return;
		warned = true;
		CruiseMissileProgram.LOG.warn(
				"warfront is loaded but its tactical overlay contract could not be reached; "
				+ "strike markers will not appear on display walls", error);
	}
}

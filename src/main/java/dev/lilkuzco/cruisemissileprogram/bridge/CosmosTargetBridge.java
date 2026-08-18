package dev.lilkuzco.cruisemissileprogram.bridge;

import dev.lilkuzco.cruisemissileprogram.CruiseMissileProgram;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Target coordinates from orbit, read out of cosmos by reflection only.
 *
 * <p><b>Reflection rather than a compile dependency, deliberately.</b> Keeping every cosmos type
 * out of this mod's signatures is what makes the soft dependency real: without cosmos installed,
 * nothing here fails to link and the console simply reports no data. Warfront set this precedent
 * with its own {@code CosmosReconBridge} and it has held up in production.
 *
 * <p><b>This is a one-way feed and stays one.</b> A satellite hands down a coordinate; it does
 * not track the missile, correct it, or know that a strike happened. Closing that loop would put
 * targeting and terminal guidance in two mods at once, and the missile's own terminal phase
 * already owns the final approach.
 *
 * <p><b>Coverage is a window, not a state.</b> Cosmos's own documentation is emphatic about this:
 * at the reference orbit a reconnaissance footprint crosses a point in about three seconds, and
 * the next pass is a Minecraft day away. A consumer that cached the answer would be lying. So
 * this asks every time and reports honestly when the answer is "nothing overhead right now" —
 * which is precisely the "no data" case the console must never paper over with a stale fix.
 */
public final class CosmosTargetBridge {

	/** One satellite's current answer. {@code hasData} false means: overhead, but nothing to see. */
	public record Fix(String satelliteId, String satelliteName, boolean inPass, boolean commsLink,
			boolean hasData, int artificialBlocks, List<BlockPos> signals) {

		public Fix {
			signals = List.copyOf(signals);
		}

		/** The best aim point this pass found, or empty if the pass saw nothing built. */
		public BlockPos best() {
			return signals.isEmpty() ? null : signals.get(0);
		}
	}

	private static boolean warned;

	private CosmosTargetBridge() {}

	public static boolean available() {
		return FabricLoader.getInstance().isModLoaded("cosmos");
	}

	/**
	 * Every reconnaissance satellite this player owns, and what it can see right now.
	 *
	 * <p>Returns an empty list when cosmos is absent, when the player owns no recon payload, or
	 * when the query fails. An empty list is the console's "NO DATA", and that is the correct
	 * reading in all three cases.
	 */
	public static List<Fix> reconFixes(ServerLevel level, UUID owner) {
		List<Fix> fixes = new ArrayList<>();
		if (!available() || !level.dimension().equals(Level.OVERWORLD)) return fixes;
		try {
			Class<?> constellationClass =
					Class.forName("dev.lilkuzco.cosmos.satellite.SatelliteConstellation");
			Object constellation = constellationClass.getMethod("of", ServerLevel.class)
					.invoke(null, level.getServer().overworld());
			@SuppressWarnings("unchecked")
			List<Object> entries = (List<Object>) constellationClass
					.getMethod("ownedBy", UUID.class).invoke(constellation, owner);
			if (entries.isEmpty()) return fixes;

			Object service = Class.forName("dev.lilkuzco.kinetics.fabric.KineticsMod")
					.getMethod("service").invoke(null);
			if (service == null) return fixes;
			Object orbits = service.getClass().getMethod("orbits").invoke(service);
			double now = (double) service.getClass().getMethod("worldTimeSeconds").invoke(service);

			Class<?> imagerClass = Class.forName("dev.lilkuzco.cosmos.satellite.ReconImager");
			Method image = imageMethod(imagerClass);

			for (Object entry : entries) {
				Object record = entry.getClass().getMethod("record").invoke(entry);
				Object payload = record.getClass().getMethod("payload").invoke(record);
				if (!"RECON".equals(payload.toString())) continue;

				String id = (String) record.getClass().getMethod("id").invoke(record);
				String name = (String) record.getClass().getMethod("name").invoke(record);

				Object state = orbits.getClass().getMethod("stateAt", String.class, double.class)
						.invoke(orbits, id, now);
				if (state == null) continue;
				Object groundTrack = state.getClass().getMethod("groundTrack").invoke(state);
				double halfAngle = (double) payload.getClass()
						.getMethod("sensorHalfAngleDeg").invoke(payload);

				Object report = image.invoke(null, level, id, groundTrack, halfAngle);
				int artificial = (int) report.getClass().getMethod("artificialBlocks").invoke(report);
				@SuppressWarnings("unchecked")
				List<BlockPos> signals = (List<BlockPos>) report.getClass()
						.getMethod("strongestSignals").invoke(report);

				boolean comms = hasComms(level, signals.isEmpty()
						? BlockPos.ZERO : signals.get(0));
				fixes.add(new Fix(id, name, true, comms, !signals.isEmpty(), artificial,
						new ArrayList<>(signals)));
			}
		} catch (ReflectiveOperationException | LinkageError error) {
			warnOnce(error);
		}
		return fixes;
	}

	private static boolean hasComms(ServerLevel level, BlockPos at) {
		try {
			return (boolean) Class.forName("dev.lilkuzco.cosmos.satellite.CommsCoverage")
					.getMethod("hasCoverage", ServerLevel.class, BlockPos.class)
					.invoke(null, level, at);
		} catch (ReflectiveOperationException | LinkageError error) {
			return false;
		}
	}

	private static Method imageMethod(Class<?> imagerClass) throws NoSuchMethodException {
		for (Method method : imagerClass.getMethods()) {
			if (method.getName().equals("image") && method.getParameterCount() == 4) return method;
		}
		throw new NoSuchMethodException("ReconImager.image(ServerLevel,String,GroundTrack,double)");
	}

	private static void warnOnce(Throwable error) {
		if (warned) return;
		warned = true;
		CruiseMissileProgram.LOG.warn(
				"cosmos is loaded but its recon API could not be queried; "
				+ "satellite targeting is disabled and the console will report NO DATA", error);
	}
}

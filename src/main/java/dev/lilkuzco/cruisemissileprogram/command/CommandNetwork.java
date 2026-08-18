package dev.lilkuzco.cruisemissileprogram.command;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.lilkuzco.cruisemissileprogram.CruiseMissileProgram;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The callsign registry: which launchers answer to which console, and what they are holding.
 *
 * <p>Linking is by <b>callsign</b> — a short string set on both ends — and deliberately has
 * <b>no distance limit at all</b>. That is the promise of this mod: a console commands launchers
 * a thousand blocks away, or ten thousand. Distance is expressed as {@linkplain #signalDelaySeconds
 * signal delay} on the firing countdown, which is a texture rather than a wall.
 *
 * <p><b>Why this is saved data and not a scan.</b> Everything here must be true while the far end
 * is in an unloaded chunk, which for a launcher a kilometre away is almost always. A registry that
 * walked block entities would report an empty network the moment the player stopped standing next
 * to their own missiles. So launchers register on placement, deregister on removal, and mirror
 * their inventory in when it changes — the three moments that are guaranteed to run.
 *
 * <p>The rule this repo learned three times over: registration hangs off placement and removal,
 * <b>never</b> off {@code setRemoved}, which also fires on chunk unload and would quietly empty
 * the roster of every launcher nobody happened to be standing near.
 */
public class CommandNetwork extends SavedData {

	/** One callsign's network: at most one console, any number of launchers. */
	public record Net(String callsign, Optional<BlockPos> console, Identifier consoleDimension,
			Map<BlockPos, LauncherRecord> launchers) {

		public static final Codec<Net> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("callsign").forGetter(Net::callsign),
				BlockPos.CODEC.optionalFieldOf("console").forGetter(Net::console),
				Identifier.CODEC.optionalFieldOf("console_dimension",
						Identifier.withDefaultNamespace("overworld"))
						.forGetter(Net::consoleDimension),
				LauncherRecord.CODEC.listOf().optionalFieldOf("launchers", List.of())
						.forGetter(n -> List.copyOf(n.launchers().values())))
				.apply(i, (callsign, console, dim, launchers) -> {
					Map<BlockPos, LauncherRecord> map = new LinkedHashMap<>();
					for (LauncherRecord r : launchers) map.put(r.pos(), r);
					return new Net(callsign, console, dim, map);
				}));

		Net withConsole(Optional<BlockPos> pos, Identifier dimension) {
			return new Net(callsign, pos, dimension, launchers);
		}

		/** Launchers in a stable order — the roster must not reshuffle between UI refreshes. */
		public List<LauncherRecord> roster() {
			List<LauncherRecord> out = new ArrayList<>(launchers.values());
			out.sort((a, b) -> {
				int byX = Integer.compare(a.pos().getX(), b.pos().getX());
				if (byX != 0) return byX;
				int byZ = Integer.compare(a.pos().getZ(), b.pos().getZ());
				if (byZ != 0) return byZ;
				return Integer.compare(a.pos().getY(), b.pos().getY());
			});
			return List.copyOf(out);
		}

		public int totalMissiles() {
			int total = 0;
			for (LauncherRecord r : launchers.values()) total += r.loaded();
			return total;
		}
	}

	private static final Codec<CommandNetwork> CODEC = RecordCodecBuilder.create(i -> i.group(
			Net.CODEC.listOf().fieldOf("nets").forGetter(n -> List.copyOf(n.nets.values())))
			.apply(i, CommandNetwork::new));

	public static final SavedDataType<CommandNetwork> TYPE = new SavedDataType<>(
			CruiseMissileProgram.id("command_network"),
			CommandNetwork::new,
			CODEC,
			DataFixTypes.LEVEL);

	/** Longest callsign accepted. Long enough for "BATTERY-ELEVEN", short enough to draw. */
	public static final int MAX_CALLSIGN = 16;

	// Insertion-ordered so the console lists networks in the order they were created rather than
	// at the whim of a hash — the same determinism argument cosmos makes about its constellation.
	private final Map<String, Net> nets = new LinkedHashMap<>();

	public CommandNetwork() {}

	private CommandNetwork(List<Net> loaded) {
		for (Net net : loaded) nets.put(net.callsign(), net);
	}

	/** The network lives on the overworld's storage regardless of who is asking. */
	public static CommandNetwork of(ServerLevel level) {
		return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/**
	 * Callsigns are normalised on the way in, so "alpha 1" and "ALPHA-1" are the same network.
	 * Two people setting what they believe is the same callsign and getting two networks is a
	 * bug report that would be very hard to read.
	 */
	public static String normalise(String raw) {
		String trimmed = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9-]+", "-");
		trimmed = trimmed.replaceAll("^-+|-+$", "");
		return trimmed.length() > MAX_CALLSIGN ? trimmed.substring(0, MAX_CALLSIGN) : trimmed;
	}

	public Optional<Net> net(String callsign) {
		return Optional.ofNullable(nets.get(normalise(callsign)));
	}

	public List<String> callsigns() { return List.copyOf(nets.keySet()); }

	private Net getOrCreate(String callsign) {
		return nets.computeIfAbsent(callsign, cs -> new Net(cs, Optional.empty(),
				Identifier.withDefaultNamespace("overworld"), new LinkedHashMap<>()));
	}

	// ---- consoles ---------------------------------------------------------

	/**
	 * Claim a callsign for a console.
	 *
	 * <p>One console per callsign, and the claim is refused rather than stolen: a second console
	 * quietly taking over would silently redirect somebody else's launchers, which is precisely
	 * the kind of failure that shows up later as "my missiles fired from the wrong place".
	 *
	 * @return true if this console now owns the callsign
	 */
	public boolean claim(String callsign, BlockPos console, Identifier dimension) {
		String cs = normalise(callsign);
		if (cs.isEmpty()) return false;
		Net net = getOrCreate(cs);
		if (net.console().isPresent() && !net.console().get().equals(console)) return false;
		nets.put(cs, net.withConsole(Optional.of(console.immutable()), dimension));
		setDirty();
		return true;
	}

	/** Release a callsign when its console is broken. Launchers stay registered and go orphaned. */
	public void releaseConsole(String callsign, BlockPos console) {
		String cs = normalise(callsign);
		Net net = nets.get(cs);
		if (net == null || net.console().isEmpty() || !net.console().get().equals(console)) return;
		nets.put(cs, net.withConsole(Optional.empty(), net.consoleDimension()));
		prune(cs);
		setDirty();
	}

	// ---- launchers --------------------------------------------------------

	/** Register or refresh a launcher on a callsign. Called on placement and on inventory change. */
	public void put(String callsign, LauncherRecord record) {
		String cs = normalise(callsign);
		if (cs.isEmpty()) return;
		Net net = getOrCreate(cs);
		net.launchers().put(record.pos().immutable(), record);
		setDirty();
	}

	/** Deregister a launcher. Called on removal — never on chunk unload. */
	public void remove(String callsign, BlockPos pos) {
		String cs = normalise(callsign);
		Net net = nets.get(cs);
		if (net == null) return;
		if (net.launchers().remove(pos) != null) {
			prune(cs);
			setDirty();
		}
	}

	/** Drop a network that has neither a console nor a launcher left. */
	private void prune(String callsign) {
		Net net = nets.get(callsign);
		if (net != null && net.console().isEmpty() && net.launchers().isEmpty()) {
			nets.remove(callsign);
		}
	}

	// ---- distance ---------------------------------------------------------

	/**
	 * Extra countdown seconds for commanding a launcher this far away.
	 *
	 * <p>Deliberately mild and deliberately capped: distance should be something you <em>feel</em>
	 * rather than something that stops you. A launcher across the world adds a handful of seconds
	 * to the count, and a hard range limit does not exist anywhere in this mod.
	 *
	 * <p>Logarithmic rather than linear so the first kilometre reads as a real change and the
	 * tenth costs almost nothing more — a linear delay would make long range a punishment, which
	 * is the opposite of the point.
	 */
	public static double signalDelaySeconds(double blocks) {
		if (blocks <= 128.0) return 0.0;
		double delay = 1.6 * Math.log10(blocks / 128.0);
		return Math.min(6.0, delay);
	}

	public int size() { return nets.size(); }
}

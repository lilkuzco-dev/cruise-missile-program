package dev.lilkuzco.cruisemissileprogram.command;

import dev.lilkuzco.cruisemissileprogram.CruiseMissileProgram;
import dev.lilkuzco.cruisemissileprogram.CruiseSounds;
import dev.lilkuzco.cruisemissileprogram.launcher.LaunchTubeBlockEntity;
import dev.lilkuzco.cruisemissileprogram.missile.CruiseLaunch;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Countdowns, on the server tick.
 *
 * <p><b>This is the one place the mod could most easily have gone wrong, so it is worth being
 * explicit.</b> A fire order is issued at a console and resolves at a launcher that may be a
 * thousand blocks away, in a chunk nobody has loaded. Hanging the countdown off the launcher's
 * block entity would mean the strike simply never happened whenever the player was not standing
 * next to their own missiles — no error, no log line, no missile, and a console still cheerfully
 * counting down. That is precisely the failure this repo has already paid for three times.
 *
 * <p>So the countdown lives here, driven by {@code END_SERVER_TICK}, and the launcher's chunk is
 * loaded for exactly one tick at the moment of release. A launch is a discrete event, not a
 * simulation, so a single deliberate chunk load is the honest cost — and it happens whether or
 * not anybody is watching.
 */
public final class StrikeTracker {

	/**
	 * One fire order in flight.
	 *
	 * @param dimension   which level both the launcher and the target are in
	 * @param launcher    where the round comes out of
	 * @param console     which console ordered it, so a broken console can cancel its own strikes
	 * @param target      where it is going
	 * @param releaseTick game time at which the round leaves the tube
	 * @param commander   who authorised it, for the flight log and any notification
	 */
	public record Pending(ResourceKey<Level> dimension, BlockPos launcher, BlockPos console,
			StrikeTarget target, long releaseTick, UUID commander, String commanderName,
			String callsign) {}

	private static final List<Pending> PENDING = new ArrayList<>();

	/** Base countdown before signal delay is added. Long enough to be an event, short enough to sit through. */
	public static final int BASE_COUNTDOWN_TICKS = 100;

	private StrikeTracker() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(StrikeTracker::tick);
	}

	/** Queue a fire order. Returns the countdown in ticks so the console can report it. */
	public static int order(ServerLevel level, BlockPos launcher, BlockPos console,
			StrikeTarget target, UUID commander, String commanderName, String callsign) {
		double blocks = Math.sqrt(console.distSqr(launcher));
		int delay = (int) Math.round(CommandNetwork.signalDelaySeconds(blocks) * 20.0);
		int countdown = BASE_COUNTDOWN_TICKS + delay;
		PENDING.add(new Pending(level.dimension(), launcher.immutable(), console.immutable(),
				target, level.getGameTime() + countdown, commander, commanderName, callsign));
		return countdown;
	}

	/** Drop every strike ordered by a console that no longer exists. */
	public static void cancelFrom(BlockPos console) {
		PENDING.removeIf(p -> p.console().equals(console));
	}

	/** Ticks remaining on the soonest strike from this console, or -1. */
	public static int countdownFor(ServerLevel level, BlockPos console) {
		long now = level.getGameTime();
		int best = -1;
		for (Pending p : PENDING) {
			if (!p.console().equals(console)) continue;
			int remaining = (int) Math.max(0L, p.releaseTick() - now);
			if (best < 0 || remaining < best) best = remaining;
		}
		return best;
	}

	public static int pendingCount() { return PENDING.size(); }

	private static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) return;
		Iterator<Pending> it = PENDING.iterator();
		while (it.hasNext()) {
			Pending pending = it.next();
			ServerLevel level = server.getLevel(pending.dimension());
			if (level == null) {
				it.remove();
				continue;
			}
			if (level.getGameTime() < pending.releaseTick()) continue;
			it.remove();
			release(level, pending);
		}
	}

	private static void release(ServerLevel level, Pending pending) {
		// One deliberate chunk load, for one tick, at the moment of release. Everything else in
		// this mod avoids touching the launcher's chunk; a launch is the exception because a
		// round genuinely has to leave a tube that exists.
		BlockPos tubePos = pending.launcher();
		level.getChunk(tubePos.getX() >> 4, tubePos.getZ() >> 4);

		if (!(level.getBlockEntity(pending.launcher()) instanceof LaunchTubeBlockEntity tube)) {
			CruiseMissileProgram.LOG.warn("strike on {} aborted: no launcher at {}",
					pending.target().pos(), pending.launcher());
			return;
		}
		ItemStack round = tube.consumeRound();
		if (round.isEmpty()) {
			CruiseMissileProgram.LOG.warn("strike from {} aborted: tube empty at release",
					pending.launcher());
			return;
		}
		tube.setArmed(false);

		// Loud, and deliberately so: a launch is a server-wide event, and the 6.0 volume carries
		// it well past the tube's own chunk so neighbours know a strike left from nearby.
		level.playSound(null, pending.launcher(), CruiseSounds.LAUNCH,
				SoundSource.BLOCKS, 6.0F, 1.0F);
		CruiseLaunch.launch(level, pending, round);
	}
}

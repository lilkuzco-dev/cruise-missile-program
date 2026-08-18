package dev.lilkuzco.cruisemissileprogram.missile;

import dev.lilkuzco.cruisemissileprogram.CruiseSounds;
import dev.lilkuzco.cruisemissileprogram.warhead.WarheadSpec;
import net.minecraft.sounds.SoundSource;
import dev.lilkuzco.kinetics.math.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * What a warhead does when it arrives.
 *
 * <p>This is the one place in the whole flight where damage is applied, and it is deliberately
 * outside kinetics. Rule I10 says a physics library never applies damage — kinetics reports an
 * impact as a position, a velocity and a mass, and there is no handle in its world probe through
 * which damage could be applied even by accident. The consequence belongs to the mod that owns
 * consequences, which is this one.
 */
public final class Detonation {

	/** The last resolved blast, retained for the operator depth test and client integration test. */
	public record Result(Vec3 impact, Vec3 centre, int verticalDepth) {}

	private static volatile Result lastResult;

	private Detonation() {}

	public static void at(ServerLevel level, Vec3 position, WarheadSpec warhead, String commander) {
		explode(level, position, position, warhead);
	}

	/**
	 * Detonate a terrain warhead after a physical collision.
	 *
	 * <p>Kinetics reports the point just outside the face it hit. Centring a vanilla explosion at
	 * that point leaves roughly half of its spherical block-destruction volume in open air, which
	 * makes a low horizontal strike look like a flat scrape. Embed the centre along the incoming
	 * velocity so the same vanilla TNT algorithm has material above, below and downrange to cut.
	 */
	public static void atImpact(ServerLevel level, Vec3 position, Vec3 velocity,
			WarheadSpec warhead, String commander) {
		Vec3 centre = position;
		if (warhead.terrain() && velocity.lengthSq() > 1.0e-9) {
			double penetration = Math.clamp(0.6 + warhead.blast() * 0.2, 1.0, 2.5);
			centre = position.add(velocity.normalized().scale(penetration));
		}
		explode(level, position, centre, warhead);
	}

	private static void explode(ServerLevel level, Vec3 impact, Vec3 centre,
			WarheadSpec warhead) {
		Level.ExplosionInteraction interaction = warhead.terrain()
				? Level.ExplosionInteraction.TNT
				: Level.ExplosionInteraction.NONE;

		level.explode(null, centre.x(), centre.y(), centre.z(),
				warhead.blast(), warhead.fire(), interaction);
		lastResult = new Result(impact, centre,
				warhead.terrain() ? verticalDepth(level, centre, warhead.blast()) : 0);

		// Played on top of the explosion rather than instead of it. The vanilla blast carries the
		// crack; this carries the weight, and a warhead should not sound like a creeper.
		level.playSound(null, centre.x(), centre.y(), centre.z(),
				CruiseSounds.IMPACT, SoundSource.BLOCKS, 6.0F, 1.0F);
	}

	private static int verticalDepth(ServerLevel level, Vec3 centre, float blast) {
		BlockPos origin = BlockPos.containing(centre.x(), centre.y(), centre.z());
		int floor = Math.max(level.getMinY(), origin.getY() - (int) Math.ceil(blast * 2.0));
		int depth = 0;
		for (int y = origin.getY(); y >= floor; y--) {
			if (!level.getBlockState(new BlockPos(origin.getX(), y, origin.getZ())).isAir()) break;
			depth++;
		}
		return depth;
	}

	public static Result lastResult() {
		return lastResult;
	}

	public static void clearLastResult() {
		lastResult = null;
	}
}

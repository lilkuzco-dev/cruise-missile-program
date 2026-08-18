package dev.lilkuzco.cruisemissileprogram.missile;

import dev.lilkuzco.cruisemissileprogram.warhead.WarheadSpec;
import dev.lilkuzco.kinetics.math.Vec3;
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

	private Detonation() {}

	public static void at(ServerLevel level, Vec3 position, WarheadSpec warhead, String commander) {
		Level.ExplosionInteraction interaction = warhead.terrain()
				? Level.ExplosionInteraction.TNT
				: Level.ExplosionInteraction.NONE;

		level.explode(null, position.x(), position.y(), position.z(),
				warhead.blast(), warhead.fire(), interaction);
	}
}

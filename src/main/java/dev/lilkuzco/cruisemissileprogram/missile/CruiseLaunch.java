package dev.lilkuzco.cruisemissileprogram.missile;

import dev.lilkuzco.cruisemissileprogram.CruiseItems;
import dev.lilkuzco.cruisemissileprogram.command.StrikeTracker;
import dev.lilkuzco.cruisemissileprogram.warhead.WarheadSpec;
import dev.lilkuzco.kinetics.math.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Turns a released round into a body in the air. The seam between command and flight. */
public final class CruiseLaunch {

	private CruiseLaunch() {}

	public static void launch(ServerLevel level, StrikeTracker.Pending pending, ItemStack round) {
		WarheadSpec warhead = CruiseItems.specOf(round);
		BlockPos tube = pending.launcher();
		BlockPos target = pending.target().pos();

		// Leave from just above the tube so the first integration step is not started inside the
		// launcher block, which the swept-collision check would read as an immediate impact.
		Vec3 origin = new Vec3(tube.getX() + 0.5, tube.getY() + 1.6, tube.getZ() + 0.5);
		Vec3 aim = new Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

		CruiseFlight.spawn(level, CruiseFlight.nextBodyId(), origin, aim, warhead, tube,
				pending.commander(), pending.commanderName());
	}
}

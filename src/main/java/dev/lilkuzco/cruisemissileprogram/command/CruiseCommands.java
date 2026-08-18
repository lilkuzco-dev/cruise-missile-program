package dev.lilkuzco.cruisemissileprogram.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.lilkuzco.cruisemissileprogram.CruiseMissileProgram;
import dev.lilkuzco.cruisemissileprogram.missile.CruiseFlight;
import dev.lilkuzco.cruisemissileprogram.missile.Detonation;
import dev.lilkuzco.cruisemissileprogram.warhead.WarheadSpec;
import dev.lilkuzco.kinetics.math.Vec3;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.UUID;

/**
 * Operator and verification commands.
 *
 * <p>{@code /cruise selftest} exists because this repo cannot take screenshots of a running game —
 * screen-recording permission is not granted to the terminal, so a screen capture returns desktop
 * wallpaper and could never prove anything. The empire's standing answer is to prove things with
 * logs. This flies a real missile over real generated terrain and prints its altitude, its
 * clearance above the ground beneath it, and its speed every second, so the flight profile can be
 * checked as arithmetic rather than guessed at from a description.
 */
public final class CruiseCommands {

	private CruiseCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
				dispatcher.register(Commands.literal("cruise")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("selftest")
								.executes(ctx -> selftest(ctx, 1200))
								.then(Commands.argument("range", IntegerArgumentType.integer(64, 20000))
										.executes(ctx -> selftest(ctx,
												IntegerArgumentType.getInteger(ctx, "range")))))
						.then(Commands.literal("status")
								.executes(CruiseCommands::status))
						.then(Commands.literal("depthtest")
								.executes(CruiseCommands::depthtest))));
	}

	/**
	 * Fly one missile a stated distance due east and report what happened.
	 *
	 * <p>The target is put on the actual surface at that range rather than at the launcher's own
	 * height, because a target floating in the air would let a missile that ignored terrain
	 * entirely still look successful.
	 */
	private static int selftest(CommandContext<CommandSourceStack> ctx, int range) {
		CommandSourceStack source = ctx.getSource();
		ServerLevel level = source.getLevel();
		net.minecraft.world.phys.Vec3 at = source.getPosition();

		int originX = (int) at.x;
		int originZ = (int) at.z;

		// Generate the columns before measuring them. An ungenerated chunk reports its minimum
		// build height as the surface, which put the first version of this test's launcher two
		// blocks underground: the missile spawned inside rock, the swept-collision check called
		// it an impact on the first substep, and the "flight" ended 0.1 s after it began at
		// zero metres per second. The reading was wrong, not the physics.
		int originY = surfaceAt(level, originX, originZ) + 3;

		int targetX = originX + range;
		int targetZ = originZ;
		int targetY = surfaceAt(level, targetX, targetZ);

		WarheadSpec warhead = new WarheadSpec(
				Identifier.fromNamespaceAndPath("cruise_missile_program", "conventional_warhead"),
				1, 4.5F, false, true);

		String bodyId = CruiseFlight.nextBodyId();
		CruiseFlight.spawn(level, bodyId,
				new Vec3(originX + 0.5, originY, originZ + 0.5),
				new Vec3(targetX + 0.5, targetY + 0.5, targetZ + 0.5),
				warhead, new BlockPos(originX, originY, originZ),
				UUID.nameUUIDFromBytes("selftest".getBytes()), "selftest");

		CruiseFlight.trace(bodyId, true);

		source.sendSuccess(() -> Component.literal(String.format(
				"cruise selftest: %d,%d,%d -> %d,%d,%d (%d blocks). Trace is on the server log.",
				originX, originY, originZ, targetX, targetY, targetZ, range)), false);
		CruiseMissileProgram.LOG.info(
				"SELFTEST launch {} range {} blocks: origin {},{},{} target {},{},{}",
				bodyId, range, originX, originY, originZ, targetX, targetY, targetZ);
		return 1;
	}

	/** Surface height of a column, generating its chunk first so the answer is real. */
	private static int surfaceAt(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(
				"cruise: %d missile(s) in the air, %d strike(s) counting down",
				CruiseFlight.liveCount(), StrikeTracker.pendingCount())), false);
		return 1;
	}

	/** Detonate a conventional test warhead travelling east from the command source. */
	private static int depthtest(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		WarheadSpec warhead = new WarheadSpec(
				Identifier.fromNamespaceAndPath("cruise_missile_program", "conventional_warhead"),
				1, 4.5F, false, true);
		net.minecraft.world.phys.Vec3 at = source.getPosition();
		Detonation.atImpact(source.getLevel(), new Vec3(at.x, at.y, at.z),
				new Vec3(1.0, 0.0, 0.0), warhead, "depthtest");
		Detonation.Result result = Detonation.lastResult();
		source.sendSuccess(() -> Component.literal(String.format(
				"cruise depthtest: vanilla blast cleared %d vertical blocks below centre %s",
				result.verticalDepth(), result.centre())), false);
		return result.verticalDepth() >= 2 ? 1 : 0;
	}
}

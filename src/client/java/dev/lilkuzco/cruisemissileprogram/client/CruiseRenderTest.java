package dev.lilkuzco.cruisemissileprogram.client;

import dev.lilkuzco.cruisemissileprogram.missile.Detonation;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * The client render battery.
 *
 * <p><b>This exists because headless verification cannot see this class of bug.</b> An entity type
 * with no registered renderer returns null from the render dispatcher and hard-crashes the render
 * thread the moment one is spawned — while the server logs a flawless flight. Cosmos shipped
 * exactly that, then fixed the crash with a renderer that drew nothing and shipped a whole release
 * of invisible rocket. Both are invisible to every server-side check this mod has.
 *
 * <p>So this boots a real client, stands the missile in front of the camera, flies one, and takes
 * screenshots. <b>The screenshots are the evidence and they are meant to be looked at</b> — they
 * land in {@code build/run-gametest/screenshots/}.
 *
 * <p>Runs only under {@code ./gradlew runGametest} ({@code -Dfabric.client.gametest}).
 */
public class CruiseRenderTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(80);
			var server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("gamemode creative @p");
			server.runCommand("difficulty peaceful");
			server.runCommand("kill @e[type=!minecraft:player]");

			// Absolute coordinates throughout. Building the stage with `~` resolves against a
			// player who may still be falling, and the shots then come out at a different height
			// every run; a fixed stage at a fixed origin is reproducible.
			server.runCommand("forceload add -3 -3 3 3");
			server.runCommand("fill -40 99 -40 40 99 40 minecraft:stone");
			server.runCommand("tp @p 0 101 0");
			context.waitTicks(40);

			// ---- 1. the blocks, side by side ------------------------------------
			server.runCommand("setblock -2 100 6 cruise_missile_program:fire_control_console");
			server.runCommand("setblock 2 100 6 cruise_missile_program:launch_tube");
			server.runCommand("tp @p 0 101 0 0 10");
			context.waitTicks(40);
			context.takeScreenshot("cruise_blocks");

			// ---- 2. the items, held ---------------------------------------------
			for (String item : new String[] { "cruise_missile_body", "conventional_warhead" }) {
				server.runCommand("clear @p");
				server.runCommand("give @p cruise_missile_program:" + item);
				context.waitTicks(10);
				context.takeScreenshot("cruise_item_" + item);
			}

			// ---- 3. THE MODEL BOARD ---------------------------------------------
			//
			// A missile in flight is a small fast object a long way off, and cosmos learned the
			// hard way that verifying a model from a real flight takes many runs and never
			// produces a legible frame. So one is stood up next to the camera, stationary, and
			// photographed from three sides. This answers "is it drawn, and is it the right
			// shape" in seconds — build the fast loop before spending hours on the slow one.
			server.runCommand("clear @p");
			server.runCommand("summon cruise_missile_program:cruise_missile 0 103 8");
			context.waitTicks(6);
			server.runCommand("tp @p 0 103 0 0 0");
			context.waitTicks(10);
			context.takeScreenshot("cruise_model_front");

			// Yaw 90 faces -X. The first version used -90, which faces +X — away from the missile
			// — and produced a photograph of empty stone that could easily have been read as
			// "the model does not draw". A camera angle is part of the evidence.
			server.runCommand("tp @p 8 103 8 90 0");
			context.waitTicks(10);
			context.takeScreenshot("cruise_model_side");

			server.runCommand("tp @p 0 110 8 0 90");
			context.waitTicks(10);
			context.takeScreenshot("cruise_model_top");

			server.runCommand("kill @e[type=cruise_missile_program:cruise_missile]");
			context.waitTicks(5);

			// ---- 4. a real flight, seen from beside the launcher ------------------
			server.runCommand("tp @p 0 104 -14 0 0");
			context.waitTicks(10);
			server.runCommand("cruise selftest 300");
			context.waitTicks(6);
			context.takeScreenshot("cruise_flight_launch");
			context.waitTicks(14);
			context.takeScreenshot("cruise_flight_cruise");
			context.waitTicks(20);
			context.takeScreenshot("cruise_flight_downrange");

			context.waitTicks(20);

			// ---- 5. a controlled three-dimensional terrain blast -----------------
			server.runCommand("fill 20 88 -6 40 112 6 minecraft:stone");
			server.runCommand("gamemode spectator @p");
			server.runCommand("tp @p 10 108 -10 -45 25");
			Detonation.clearLastResult();
			server.runCommand("execute positioned 19.999 100 0 run cruise depthtest");
			context.waitTicks(30);
			Detonation.Result result = Detonation.lastResult();
			if (result == null) {
				throw new AssertionError("cruise depth test did not detonate");
			}
			if (result.verticalDepth() < 2) {
				throw new AssertionError("cruise blast cleared only " + result.verticalDepth()
						+ " vertical blocks; expected TNT-like depth");
			}
			// The assertion above measures the untouched blast. Remove the outermost west-face
			// slice only afterwards, making the internal vertical cavity legible as a cutaway.
			server.runCommand("fill 20 88 -6 20 112 6 minecraft:air");
			server.runCommand("tp @p 10 104 0 -90 15");
			context.waitTicks(20);
			context.takeScreenshot("cruise_blast_depth");
		}
	}
}

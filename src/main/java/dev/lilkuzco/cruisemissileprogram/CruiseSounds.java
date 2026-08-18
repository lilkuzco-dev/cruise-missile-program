package dev.lilkuzco.cruisemissileprogram;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * This mod's sounds.
 *
 * <p>All of them are Kenney CC0 material, sliced to length by {@code tools/prepare-sounds.py} —
 * which pins each upstream archive by SHA-256 and reads the licence out of the archive itself
 * rather than trusting a claim on a web page. See ASSETS-ORIGIN.md.
 *
 * <p><b>Every one is mono.</b> Minecraft plays a stereo file flat and non-directional, and for a
 * missile that is exactly the wrong behaviour: hearing which way it is coming from is most of the
 * value of it making a noise at all.
 */
public final class CruiseSounds {

	/** The tube firing. Heard a long way off — a launch is an event on a server. */
	public static final SoundEvent LAUNCH = register("block.launch_tube.launch");

	/** The sustainer overhead. Played from the missile as it passes. */
	public static final SoundEvent FLYBY = register("entity.cruise_missile.flyby");

	/** Arrival. Deep, and deliberately distinct from a vanilla TNT crack. */
	public static final SoundEvent IMPACT = register("entity.cruise_missile.impact");

	/** Opening the fire control console. */
	public static final SoundEvent CONSOLE = register("block.fire_control_console.open");

	/** A target has been designated. */
	public static final SoundEvent TARGET_SET = register("block.fire_control_console.target_set");

	/** A strike is authorised and counting down. */
	public static final SoundEvent ARMED = register("block.fire_control_console.armed");

	/** Refused: wrong rank, no target, no loaded launcher, no satellite data. */
	public static final SoundEvent DENIED = register("block.fire_control_console.denied");

	private static SoundEvent register(String name) {
		Identifier id = CruiseMissileProgram.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id,
				SoundEvent.createVariableRangeEvent(id));
	}

	public static void register() {}

	private CruiseSounds() {}
}

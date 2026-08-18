package dev.lilkuzco.cruisemissileprogram.missile;

import dev.lilkuzco.kinetics.constants.Constants;
import dev.lilkuzco.kinetics.profile.Airframe;
import dev.lilkuzco.kinetics.profile.Profile;
import dev.lilkuzco.kinetics.profile.Recovery;
import dev.lilkuzco.kinetics.profile.SeekerSpec;
import dev.lilkuzco.kinetics.profile.Stage;

import java.util.List;

/**
 * The flyable description of a cruise missile, handed to kinetics and never interpreted here.
 *
 * <p>Everything about how this thing moves is a consequence of these numbers plus kinetics'
 * integrator. There is no speed field and no place to put one: the missile settles at whatever
 * speed its wing, its thrust and its drag agree on, which is the only arrangement in which the
 * flight profile can be said to be simulated rather than animated.
 *
 * <h2>The numbers were solved, then measured</h2>
 *
 * <p>The first draft gave this a 1.3 m^2 wing on a 680 kg body — a wing loading of 520 kg/m^2,
 * near a real Tomahawk's. A real Tomahawk carries it at 240 m/s. This one was asked to cruise at
 * 40, where the same wing produced about a ninth of the lift needed, so it flew a ballistic arc,
 * porpoised 55 blocks above its commanded height and dived into the ground seven seconds after
 * launch. The trace showed it plainly; no amount of gain tuning would have fixed it, because the
 * airframe could not hold itself up.
 *
 * <p>Wing loading is what picks cruise speed, so it is the number this design starts from:
 *
 * <pre>
 *   level flight:  L = W          =>  q * S * Cl = m * g
 *   at ground+22:  rho ~ 0.72 kg/m^3   (sea-level 1.225 with kinetics' 55 m scale height)
 *   want v ~ 43 m/s, Cl ~ 1.0     =>  q = 0.5 * 0.72 * 43^2 = 654 Pa
 *                                 =>  S = m*g / (q*Cl) = 3922 / 654 = 6.0 m^2
 * </pre>
 *
 * <p>Six square metres of wing on a 400 kg missile is a wing loading of 67 kg/m^2 — light-aircraft
 * territory, not missile territory. That is the honest consequence of asking something to cruise
 * at Minecraft speeds in an atmosphere whose density is real: it has to be a glider with a motor.
 * Cosmos states the same kind of abstraction about its parachutes, which are necessarily enormous
 * for the same reason.
 *
 * <h2>Thrust, and why it is far larger than cruise drag</h2>
 *
 * <p>Level cruise needs about 230 N. This carries 1,500 N, and the excess is not waste: it is
 * climb authority. Minecraft terrain rises 40 blocks in the distance this missile covers in four
 * seconds, which is a far steeper relief than any real cruise missile ever meets. Climb rate comes
 * from excess power, so hugging this landscape needs roughly six times cruise thrust. The
 * autopilot throttles back in level flight rather than letting it accelerate away.
 */
public final class CruiseProfile {

	/** Airframe mass that never stages away: warhead, structure, wings, guidance. kg. */
	public static final double DRY_MASS = 240.0;

	private static final double FUEL_MASS = 100.0;
	private static final double STAGE_DRY_MASS = 60.0;

	/** Sustainer thrust, N. Six times cruise drag, because climbing over Minecraft is the job. */
	private static final double THRUST_N = 1500.0;

	/**
	 * High Isp because this stands in for an air-breathing engine, which is what a cruise missile
	 * actually uses. A rocket sustainer at a realistic Isp would burn out in seconds and turn the
	 * whole flight into a glide.
	 */
	private static final double ISP = 1200.0;

	/** Wing area, m^2. Solved from the wing-loading identity above, not chosen. */
	private static final double WING_AREA = 8.5;

	/** The speed the autopilot governs to. Fast enough to read as a missile, slow enough to watch. */
	public static final double CRUISE_SPEED = 44.0;

	/**
	 * Speed the tube's booster charge throws the round at.
	 *
	 * <p>Ejecting at cruise speed rather than accelerating up to it is deliberate: with 1,500 N on
	 * 400 kg the missile would need most of a minute to reach flying speed from rest, and would
	 * spend it sinking. A box launcher has a booster charge for exactly this reason.
	 */
	public static final double EJECTION_SPEED = 46.0;

	/**
	 * Terminal dive range: inside this, terrain following stops and it goes for the point.
	 *
	 * <p><b>Adaptive, because a fixed range cannot be right twice.</b> At a fixed 110 m the missile
	 * abandoned terrain following while a ridge was still between it and the target and flew into
	 * that ridge; at a fixed 75 m it arrived over a target sitting 28 blocks below it with 1.3
	 * seconds to lose the height, and sailed over the top. What actually matters is the height it
	 * has to shed: a dive of about 35 degrees is comfortable, so the handover happens roughly
	 * 1.4 heights out, with a floor for a target at its own altitude.
	 */
	public static double terminalRange(double heightAboveTarget) {
		return Math.max(70.0, Math.abs(heightAboveTarget) * 1.4 + 40.0);
	}

	/** Height above the target the let-down aims for before the terminal dive takes over. */
	public static final double ARRIVAL_CLEARANCE = 12.0;

	/** Floor on the commanded height during the let-down. The terrain always wins over the plan. */
	public static final double TERRAIN_SAFETY = 9.0;

	/** Lift coefficient the autopilot plans around. Below stall, so a climb keeps its margin. */
	public static final double CRUISE_LIFT_COEFFICIENT = 0.95;

	/** Ceiling on the speed governor. Past this it stops being a cruise missile. */
	public static final double MAX_SPEED = 120.0;

	/** Height held over the ground during cruise, in blocks. Low enough to read as terrain-hugging. */
	public static final double CRUISE_CLEARANCE = 26.0;

	/**
	 * How far ahead the terrain is sampled, in seconds of flight.
	 *
	 * <p>The single most important number in the flight profile. Sample directly underneath and
	 * the missile notices a ridge at the moment it hits it; sample too far ahead and it climbs
	 * over hills it was going to pass beside. Kinetics' own RC5 note makes the same point.
	 *
	 * <p><b>Fourteen seconds, and every step of that was measured rather than picked.</b> At 4.5 s
	 * the missile saw a ridge 270 blocks out and could not climb it in time; two of four test
	 * flights flew into rising ground. At 9 s it cleared ridges and still hit mountains, because
	 * Minecraft mountains are 60 blocks of relief and the wing buys about 7 m/s of climb — which
	 * is twelve seconds of warning at cruise speed, or some 700 blocks. The look-ahead has to be
	 * longer than the climb takes, and the climb is set by the wing, so this number is downstream
	 * of the wing area rather than independent of it.
	 */
	public static final double LOOK_AHEAD_SECONDS = 14.0;

	/**
	 * Ceiling on the vertical acceleration the autopilot may ask for, m/s^2.
	 *
	 * <p>Set to what the wing can actually deliver at cruise — {@code q*S*Clmax/m} works out at
	 * about 13.7 — rather than to a number that sounds authoritative. Commanding more than the
	 * airframe can produce does not make it climb faster; it just means the guidance is lying to
	 * itself about what happened.
	 */
	public static final double MAX_VERTICAL_ACCEL = 18.0;

	private CruiseProfile() {}

	public static Profile build(String bodyId, Constants k) {
		Airframe airframe = new Airframe(
				// Frontal reference area: a slim body, roughly half a metre across.
				0.22,
				WING_AREA,
				// Parasite drag on the FRONTAL area, so this is a body coefficient, not a
				// clean-wing one. At 0.028 the missile met no meaningful drag at all and
				// accelerated to several hundred metres per second before anything balanced it.
				0.30,
				6.5,
				k.d("aerodynamics.oswald_efficiency_default"),
				0.075,
				k.d("aerodynamics.default_stall_aoa_deg"),
				k.d("aerodynamics.post_stall_aoa_deg"),
				k.d("aerodynamics.post_stall_cl_fraction"),
				// g limit: a winged cruise vehicle, not an interceptor. The wing tops out near
				// 1.4 g at cruise anyway; this is the structural ceiling above that.
				4.0,
				60_000.0,
				0.12,
				k.d("reentry.overheat_threshold_default"),
				// Small radar cross-section. Flying low is what actually hides it (kinetics RF2),
				// but a slim winged body is a small return in its own right.
				0.35);

		Stage sustainer = new Stage("sustainer", FUEL_MASS, STAGE_DRY_MASS, THRUST_N,
				ISP * 0.92, ISP);

		return new Profile(bodyId, DRY_MASS, List.of(sustainer), airframe,
				Recovery.none(), SeekerSpec.none(),
				0,
				// Slew rate: a cruise missile turns with its wings, and briskly.
				90.0,
				0.0);
	}
}

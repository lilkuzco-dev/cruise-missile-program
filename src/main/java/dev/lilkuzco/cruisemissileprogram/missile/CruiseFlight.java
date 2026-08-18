package dev.lilkuzco.cruisemissileprogram.missile;

import dev.lilkuzco.cruisemissileprogram.CruiseMissileProgram;
import dev.lilkuzco.cruisemissileprogram.CruiseSounds;
import dev.lilkuzco.cruisemissileprogram.warhead.WarheadSpec;
import dev.lilkuzco.kinetics.body.KineticBody;
import dev.lilkuzco.kinetics.constants.Constants;
import dev.lilkuzco.kinetics.env.Environment;
import dev.lilkuzco.kinetics.event.KineticEvent;
import dev.lilkuzco.kinetics.fabric.KineticsMod;
import dev.lilkuzco.kinetics.fabric.KineticsService;
import dev.lilkuzco.kinetics.guidance.GuidanceLaws;
import dev.lilkuzco.kinetics.integrate.ControlCommand;
import dev.lilkuzco.kinetics.integrate.Integrator;
import dev.lilkuzco.kinetics.math.Vec3;
import dev.lilkuzco.kinetics.phase.FlightPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every cruise missile in the air, flown on the server tick.
 *
 * <p><b>Why the flight is here and not on the entity.</b> A missile crossing a thousand blocks
 * spends nearly all of its flight over chunks nobody has loaded, and an entity in an unloaded
 * chunk is not ticked. A flight driven from {@code Entity.tick()} would therefore stop somewhere
 * over open country and simply never arrive — with no crash, no log line, and a console still
 * reporting a successful launch. This repo has paid for that lesson three times; the missile is
 * integrated here, and the entity is a window.
 *
 * <h2>Why this drives the integrator directly</h2>
 *
 * <p>Kinetics' {@code FlightDirector} offers a guided mission, but its guidance is a
 * proportional-navigation seeker: it needs a lock, and it drops that lock whenever terrain
 * occludes the target. That is correct behaviour for an interceptor and exactly wrong for a
 * missile whose entire purpose is to fly <em>behind</em> terrain. A cruise missile does not
 * chase what it can see; it follows the ground to a coordinate it was given.
 *
 * <p>So this class supplies the steering and lets kinetics do the physics, which is precisely
 * what {@code GuidanceLaws} exists for — its own note calls these "the cheaper guidance laws,
 * for bodies that do not warrant a PN seeker", and its {@code waypoint} law is documented as the
 * thing that "keeps a cruise missile from cornering like an interceptor". Every metre of motion
 * is still integrated by kinetics: drag, lift, thrust, gravity and collision are none of this
 * mod's business. All that is decided here is which way to point.
 */
public final class CruiseFlight {

	/** One missile in the air. Mutable, because a flight is a process. */
	private static final class InFlight {
		final String bodyId;
		final KineticBody body;
		final ResourceKey<Level> dimension;
		final Vec3 target;
		final WarheadSpec warhead;
		final BlockPos launcher;
		final UUID commander;
		final String commanderName;
		int entityId = -1;
		double age;
		boolean trace;
		double lastTraceAt = -1.0;
		double lastCeiling = Double.NaN;
		double lastWanted = Double.NaN;
		double lastThrottle;
		double previousRange = Double.MAX_VALUE;
		double lastFlybyAt = -99.0;

		InFlight(String bodyId, KineticBody body, ResourceKey<Level> dimension, Vec3 target,
				WarheadSpec warhead, BlockPos launcher, UUID commander, String commanderName) {
			this.bodyId = bodyId;
			this.body = body;
			this.dimension = dimension;
			this.target = target;
			this.warhead = warhead;
			this.launcher = launcher;
			this.commander = commander;
			this.commanderName = commanderName;
		}
	}

	// ---- tuning -----------------------------------------------------------

	/** Bank limit during cruise. 60 degrees is 1.73 g — a cruise constraint, not a dogfight. */
	private static final double MAX_BANK_DEG = 60.0;

	/** Pursuit gain on the horizontal aim point. */
	private static final double LATERAL_GAIN = 1.8;

	/**
	 * Altitude-hold gains for terrain following.
	 *
	 * <p>Near-critically damped: {@code kd ≈ 2*sqrt(kp)}. The first draft used kp 0.55 with kd
	 * 1.6, which is heavily over-damped, and the consequence showed up immediately in a real
	 * flight rather than in reasoning. A missile ejected climbing at 14 m/s with 17 m still to
	 * gain produced {@code 0.55*17 - 1.6*14 = -13} — the damping term outvoted the error term and
	 * the guidance commanded the missile <em>downwards while it was still below its cruise
	 * height</em>. It clipped the first tree it came to.
	 */
	private static final double ALT_KP = 0.9;
	private static final double ALT_KD = 1.9;

	/** Speed governor gain: how hard the throttle chases {@code CRUISE_SPEED}. */
	private static final double SPEED_GAIN = 0.045;

	/** Idle throttle in level flight. Roughly cruise drag over full thrust. */
	private static final double BASE_THROTTLE = 0.16;

	/**
	 * Columns sampled across the look-ahead corridor.
	 *
	 * <p>Twenty-four at a 14 s look-ahead and 40 m/s is a sample every 23 blocks or so — fine enough
	 * to catch a hill or a building, coarse enough that a flight costs a few hundred block reads
	 * a tick rather than thousands. Kinetics caches block lookups within a tick, so the repeated
	 * columns a slow-turning missile re-scans are nearly free.
	 */
	private static final int TERRAIN_SAMPLES = 24;

	/** How often the flyby is re-emitted. Slightly under the clip length so it reads as continuous. */
	private static final double FLYBY_INTERVAL_SECONDS = 1.7;

	/** Over how many blocks of run-in the missile lets down from cruise height to arrival height. */
	private static final double LETDOWN_DISTANCE = 300.0;

	/** Give up on a missile that has been flying this long. A safety net, not a range limit. */
	private static final double MAX_FLIGHT_SECONDS = 600.0;

	/** A direct hit: close enough that there is nothing to decide. */
	private static final double ARM_RADIUS = 3.0;

	/**
	 * Inside this range the fuse is armed and watching for closest approach.
	 *
	 * <p>A missile arriving at 60 m/s covers three metres in a tick, so a test that only fires
	 * inside a small radius is a test that a fast missile steps straight over. The first complete
	 * flight passed within two blocks of its target, did not fire, and flew on for another fifty
	 * blocks before hitting a hillside — a perfect intercept scored as a miss. Real fuses solve
	 * for closest approach for exactly this reason, and kinetics' own proximity fuse says so.
	 */
	private static final double FUSE_ARM_RADIUS = 48.0;

	private static final Map<String, InFlight> LIVE = new LinkedHashMap<>();
	private static Integrator integrator;
	private static long serial;

	private CruiseFlight() {}

	public static void register() {
		// Registered after kinetics' own handler, because this mod declares a hard dependency on
		// kinetics and Fabric initialises it first. By the time this runs, the service exists.
		ServerTickEvents.END_SERVER_TICK.register(CruiseFlight::tick);
	}

	public static int liveCount() { return LIVE.size(); }

	/**
	 * Log this flight's telemetry once a second.
	 *
	 * <p>The trace prints clearance above the ground <em>beneath the missile</em>, not altitude
	 * above sea level. That distinction is the entire test: a missile holding 22 m of altitude
	 * over a valley and 22 m over a mountain is not following terrain, and only the clearance
	 * number can tell the two apart.
	 */
	public static void trace(String bodyId, boolean on) {
		InFlight flight = LIVE.get(bodyId);
		if (flight != null) flight.trace = on;
	}

	/** A fresh body id. Serials rather than UUIDs so a flight log stays readable. */
	public static String nextBodyId() {
		return "cruise_missile_program:cm-" + (++serial);
	}

	/**
	 * Put a missile in the air.
	 *
	 * @param origin where the round leaves the tube
	 * @param target where it is going
	 */
	public static void spawn(ServerLevel level, String bodyId, Vec3 origin, Vec3 target,
			WarheadSpec warhead, BlockPos launcher, UUID commander, String commanderName) {
		KineticsService kinetics = KineticsMod.service();
		if (kinetics == null) {
			CruiseMissileProgram.LOG.error("cannot launch {}: kinetics service is not up", bodyId);
			return;
		}
		Constants k = kinetics.constants();
		if (integrator == null) integrator = new Integrator(k);

		// The tube's booster charge throws the round out at flying speed on a shallow climb.
		//
		// Both halves matter. A wing makes no lift without airspeed, so a round ejected slowly
		// is a round that falls while its guidance commands a climb it cannot perform. And the
		// climb must be shallow rather than steep: an earlier version fired the round almost
		// straight up at 30 m/s, which bought clearance but arrived at the top with no speed,
		// no lift, and nothing to do but come down again.
		Vec3 downrange = new Vec3(target.x() - origin.x(), 0.0, target.z() - origin.z());
		Vec3 heading = downrange.lengthSq() < 1e-6 ? new Vec3(1, 0, 0) : downrange.normalized();
		double climb = Math.toRadians(40.0);
		Vec3 velocity = heading.scale(CruiseProfile.EJECTION_SPEED * Math.cos(climb))
				.add(new Vec3(0.0, CruiseProfile.EJECTION_SPEED * Math.sin(climb), 0.0));

		KineticBody body = new KineticBody(bodyId, CruiseProfile.build(bodyId, k), k,
				origin, velocity,
				dev.lilkuzco.kinetics.math.Quat.between(new Vec3(0, 0, 1), velocity.normalized()),
				// BOOST, not RAIL: the round is already clear of the tube and already moving, and
				// the liftoff thrust-to-weight gate a rail applies is meaningless for something
				// that was thrown rather than lifted.
				FlightPhase.BOOST);

		InFlight flight = new InFlight(bodyId, body, level.dimension(), target, warhead,
				launcher, commander, commanderName);

		CruiseMissileEntity entity = new CruiseMissileEntity(
				dev.lilkuzco.cruisemissileprogram.CruiseEntities.CRUISE_MISSILE, level);
		entity.setBodyId(bodyId);
		entity.setPos(origin.x(), origin.y(), origin.z());
		if (level.addFreshEntity(entity)) {
			flight.entityId = entity.getId();
		}
		LIVE.put(bodyId, flight);
		CruiseMissileProgram.LOG.info("launch {}: {} -> {} ({} blocks)", bodyId,
				fmt(origin), fmt(target), Math.round(horizontalRange(origin, target)));
	}

	// ---- the tick ---------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (LIVE.isEmpty()) return;
		KineticsService kinetics = KineticsMod.service();
		if (kinetics == null) {
			LIVE.clear();
			return;
		}
		double dt = kinetics.constants().d("world.tick_seconds");
		double worldTime = kinetics.worldTimeSeconds();

		List<String> finished = null;
		for (InFlight flight : List.copyOf(LIVE.values())) {
			ServerLevel level = server.getLevel(flight.dimension);
			if (level == null) {
				if (finished == null) finished = new ArrayList<>();
				finished.add(flight.bodyId);
				continue;
			}
			if (step(level, kinetics, flight, worldTime, dt)) {
				if (finished == null) finished = new ArrayList<>();
				finished.add(flight.bodyId);
			}
		}
		if (finished != null) finished.forEach(LIVE::remove);
	}

	/** @return true when this flight is over */
	private static boolean step(ServerLevel level, KineticsService kinetics, InFlight flight,
			double worldTime, double dt) {
		Environment env = kinetics.environmentOf(flight.dimension);
		if (env == null) {
			CruiseMissileProgram.LOG.warn("no kinetics environment for {}; {} is grounded",
					flight.dimension.identifier(), flight.bodyId);
			return true;
		}
		KineticBody body = flight.body;
		flight.age += dt;

		Vec3 position = body.position();
		Vec3 velocity = body.velocity();
		double range = horizontalRange(position, flight.target);

		double terminalRange = CruiseProfile.terminalRange(position.y() - flight.target.y());
		ControlCommand control = range <= terminalRange
				? terminalCommand(flight, body, position, velocity, env)
				: cruiseCommand(flight, body, position, velocity, flight.target, env);

		List<KineticEvent.Impact> impacts = new ArrayList<>(1);
		Integrator.StepResult result = integrator.step(body, env, control, worldTime, dt, event -> {
			if (event instanceof KineticEvent.Impact impact) impacts.add(impact);
		});

		moveEntity(level, flight, body);

		double traceEvery = flight.age < 6.0 ? 0.5 : 1.0;
		if (flight.trace && flight.age - flight.lastTraceAt >= traceEvery) {
			flight.lastTraceAt = flight.age;
			Vec3 now = body.position();
			double groundY = env.groundYBelow(now.x(), now.z(), now.y());
			CruiseMissileProgram.LOG.info(String.format(
					"TRACE %s t=%5.1fs pos=%7.0f,%6.1f,%7.0f ground=%6.1f clearance=%6.1f "
					+ "speed=%5.1f thr=%.2f ceil=%6.1f want=%6.1f phase=%s range=%6.0f",
					flight.bodyId, flight.age, now.x(), now.y(), now.z(), groundY,
					now.y() - groundY, body.velocity().length(), flight.lastThrottle,
					flight.lastCeiling,
					flight.lastWanted,
					body.phase(), horizontalRange(now, flight.target)));
		}

		// Arrival, by any of the three ways a flight can end.
		if (!impacts.isEmpty()) {
			return detonate(level, flight, impacts.get(0).position());
		}
		if (result != null && result.collided()) {
			if (flight.trace) {
				Vec3 hit = body.position();
				CruiseMissileProgram.LOG.info("TRACE {} COLLIDED at {} with {} (clearance {})",
						flight.bodyId, fmt(hit),
						level.getBlockState(net.minecraft.core.BlockPos.containing(
								hit.x(), hit.y(), hit.z())),
						String.format("%.1f", hit.y()
								- env.groundYBelow(hit.x(), hit.z(), hit.y())));
			}
			return detonate(level, flight, body.position());
		}
		// The fuse. A direct hit fires immediately; otherwise, once armed, the moment the range
		// stops falling IS the closest approach, and that is where the warhead goes off.
		double toTarget = body.position().sub(flight.target).length();
		if (toTarget <= ARM_RADIUS) {
			return detonate(level, flight, body.position());
		}
		if (toTarget <= FUSE_ARM_RADIUS && toTarget > flight.previousRange) {
			if (flight.trace) {
				CruiseMissileProgram.LOG.info("TRACE {} fuse: closest approach {} blocks",
						flight.bodyId, String.format("%.1f", flight.previousRange));
			}
			return detonate(level, flight, body.position());
		}
		flight.previousRange = toTarget;
		if (flight.age > MAX_FLIGHT_SECONDS) {
			CruiseMissileProgram.LOG.warn("{} exceeded {}s of flight and was scrubbed",
					flight.bodyId, MAX_FLIGHT_SECONDS);
			removeEntity(level, flight);
			return true;
		}
		if (!body.phase().isInWorld()) {
			return detonate(level, flight, body.position());
		}
		return false;
	}

	/**
	 * Cruise: hold a low clearance over whatever is underneath, steering at the target.
	 *
	 * <p>The two halves are deliberately independent. Horizontal steering aims at the target and
	 * knows nothing about hills; vertical steering follows the ground and knows nothing about
	 * where it is going. Composing them is what produces the shape — a missile that holds its
	 * heading and rides the landscape up and over, rather than one that arcs.
	 */
	private static ControlCommand cruiseCommand(InFlight flight, KineticBody body, Vec3 position,
			Vec3 velocity, Vec3 target, Environment env) {
		// Aim at the target's column, at our own altitude: a purely horizontal steering demand.
		Vec3 horizontalAim = new Vec3(target.x(), position.y(), target.z());
		Vec3 lateral = GuidanceLaws.waypoint(position, velocity, horizontalAim,
				MAX_BANK_DEG, env.gravity(), LATERAL_GAIN);

		double ceiling = terrainCeilingAhead(env, position, velocity);
		flight.lastCeiling = ceiling;

		// Let down toward the target over the last few hundred blocks instead of arriving on top
		// of it with everything still to lose. Coming over a mountain the missile can be sixty
		// blocks above its target with two seconds of flight left, and no dive fixes that — the
		// descent has to have started already. The blend never goes below the terrain corridor
		// plus a safety margin, so letting down cannot fly it into the ground.
		double approach = Math.max(0.0, Math.min(1.0,
				(horizontalRange(position, target) - CruiseProfile.terminalRange(
						position.y() - target.y())) / LETDOWN_DISTANCE));
		double cruiseY = ceiling + CruiseProfile.CRUISE_CLEARANCE;
		double arrivalY = target.y() + CruiseProfile.ARRIVAL_CLEARANCE;
		double wantedY = Math.max(ceiling + CruiseProfile.TERRAIN_SAFETY,
				arrivalY + (cruiseY - arrivalY) * approach);
		Vec3 vertical = GuidanceLaws.altitudeHold(position, velocity, wantedY,
				ALT_KP, ALT_KD, CruiseProfile.MAX_VERTICAL_ACCEL);

		flight.lastWanted = wantedY;
		double throttle = throttleFor(env, body, velocity, vertical.y(), wantedY);
		flight.lastThrottle = throttle;
		return ControlCommand.accelerate(
				new Vec3(lateral.x(), vertical.y(), lateral.z()), throttle);
	}

	/**
	 * How fast this missile has to be going to hold itself up where it is going.
	 *
	 * <p><b>A winged vehicle in this world has a service ceiling, and mountains are above it.</b>
	 * Kinetics compresses the atmosphere's scale height to 55 m, so air thins 154 times faster
	 * with altitude than it really does: at 70 m above sea level the density is already a quarter
	 * of what it is at the surface. A missile governed to a fixed 40 m/s can hold 26 blocks over a
	 * valley and physically cannot hold level flight at y=135 — the wing runs out of air. Two test
	 * flights climbed toward a mountain, ran out of lift on the way up, and flew into it while
	 * still climbing. Nothing was wrong with the guidance; the vehicle could not get there.
	 *
	 * <p>Lift goes as v^2, so the answer is speed: solve {@code L = W} at the density of the
	 * altitude being commanded and govern to that instead of to a constant. Climbing therefore
	 * makes the missile accelerate, which is both correct and the right thing to watch.
	 */
	private static double targetSpeed(Environment env, KineticBody body, double wantedY) {
		double rho = env.densityAt(Math.max(wantedY, body.position().y()));
		double wingArea = body.profile().airframe().wingArea();
		if (rho <= 1e-6 || wingArea <= 0.0) return CruiseProfile.CRUISE_SPEED;

		// Cl held below the stall so there is manoeuvre margin left at the top of a climb.
		double needed = Math.sqrt(2.0 * body.mass() * env.gravity()
				/ (rho * wingArea * CruiseProfile.CRUISE_LIFT_COEFFICIENT));
		return Math.max(CruiseProfile.CRUISE_SPEED,
				Math.min(CruiseProfile.MAX_SPEED, needed * 1.12));
	}

	/**
	 * The highest ground anywhere along the next few seconds of flight.
	 *
	 * <p><b>This is a denser scan than kinetics' own {@code terrainFollow}, and it has to be.</b>
	 * That law samples exactly two columns — underneath, and one look-ahead point — which is the
	 * right shape for real terrain, where relief is gentle and a single point ahead represents
	 * everything between. Minecraft is not gentle: the first flight into a forest hit a tree
	 * eleven blocks off the launcher, in the gap between the two samples, having been told by
	 * both of them that the way ahead was clear. Sampling the whole corridor is what turns
	 * "follow the terrain" into "clear everything between here and there".
	 *
	 * <p>The scan takes the maximum rather than an average, because the thing that stops a missile
	 * is the tallest object in its path, not the typical one.
	 */
	private static double terrainCeilingAhead(Environment env, Vec3 position, Vec3 velocity) {
		Vec3 horizontal = new Vec3(velocity.x(), 0.0, velocity.z());
		double speed = horizontal.length();
		double highest = env.groundYBelow(position.x(), position.z(), position.y());
		if (speed < 1e-3) return highest;

		// horizontal already has magnitude = ground speed, so scaling it by (seconds / samples)
		// gives one sample every (speed * seconds / samples) blocks. Dividing by speed as well —
		// which the first version did — collapses the whole 225-block corridor to four blocks,
		// and the missile then "looks ahead" no further than its own nose. It flew into the first
		// hill it met while reporting a perfectly clear corridor.
		Vec3 step = horizontal.scale(CruiseProfile.LOOK_AHEAD_SECONDS / TERRAIN_SAMPLES);
		// Search from well above the missile: a ridge ahead may stand higher than the missile is
		// flying, and a column scanned downwards from the missile's own altitude would report the
		// ground on the far side of it as if the ridge were not there.
		double from = position.y() + CruiseProfile.CRUISE_CLEARANCE * 4.0;
		for (int i = 1; i <= TERRAIN_SAMPLES; i++) {
			Vec3 at = position.add(step.scale(i));
			double ground = env.groundYBelow(at.x(), at.z(), from);
			if (ground > highest) highest = ground;
		}
		return highest;
	}

	/**
	 * The autopilot's throttle: hold cruise speed, and open up to climb.
	 *
	 * <p>Without this the missile does not cruise, it accelerates. Level drag at cruise is about
	 * 230 N against 1,500 N of thrust, so a sustainer left at full throttle settles somewhere past
	 * 250 m/s — which crosses a thousand blocks before anybody can look at it, and turns terrain
	 * following into a formality because nothing can turn that fast. Climb authority still needs
	 * the full 1,500 N, so the throttle opens with the vertical demand rather than being capped.
	 */
	private static double throttleFor(Environment env, KineticBody body, Vec3 velocity,
			double verticalCommand, double wantedY) {
		double climbDemand = Math.max(0.0, verticalCommand) / CruiseProfile.MAX_VERTICAL_ACCEL;
		double speedError = targetSpeed(env, body, wantedY) - velocity.length();
		double throttle = BASE_THROTTLE + 0.9 * climbDemand + SPEED_GAIN * speedError;
		// Overspeed: give back everything except what the climb is asking for. Capping at a fixed
		// idle instead let the missile settle 13 m/s fast, because a quarter throttle on this
		// sustainer is almost exactly cruise drag — it stopped accelerating and never slowed down.
		if (speedError < -4.0) throttle = Math.min(throttle, 0.9 * climbDemand);
		return Math.max(0.0, Math.min(1.0, throttle));
	}

	/**
	 * Terminal: stop following the ground and go for the point.
	 *
	 * <p>The phase change is real, not cosmetic — TERMINAL is a powered phase in kinetics, so the
	 * sustainer keeps running into the dive rather than the missile arriving as a glider.
	 */
	private static ControlCommand terminalCommand(InFlight flight, KineticBody body, Vec3 position,
			Vec3 velocity, Environment env) {
		if (body.phase() == FlightPhase.BOOST) {
			body.phases().transition(FlightPhase.TERMINAL, body.age(),
					"terminal dive", event -> {});
		}
		Vec3 accel = GuidanceLaws.purePursuit(position, velocity, flight.target, 4.2);
		// Full throttle into the dive: a missile that arrives as a glider arrives slowly.
		return ControlCommand.accelerate(accel, 1.0);
	}

	// ---- the view ---------------------------------------------------------

	private static void moveEntity(ServerLevel level, InFlight flight, KineticBody body) {
		if (flight.entityId < 0) return;
		Entity entity = level.getEntity(flight.entityId);
		if (!(entity instanceof CruiseMissileEntity missile)) {
			flight.entityId = -1;
			return;
		}
		Vec3 p = body.position();
		missile.setPos(p.x(), p.y(), p.z());
		Vec3 v = body.velocity();
		if (v.lengthSq() > 1e-6) {
			double horizontal = Math.sqrt(v.x() * v.x() + v.z() * v.z());
			missile.setYRot((float) (Math.toDegrees(Math.atan2(-v.x(), v.z()))));
			missile.setXRot((float) (-Math.toDegrees(Math.atan2(v.y(), horizontal))));
		}
	}

	private static void removeEntity(ServerLevel level, InFlight flight) {
		if (flight.entityId < 0) return;
		Entity entity = level.getEntity(flight.entityId);
		if (entity != null) entity.discard();
		flight.entityId = -1;
	}

	private static boolean detonate(ServerLevel level, InFlight flight, Vec3 at) {
		removeEntity(level, flight);
		Detonation.at(level, at, flight.warhead, flight.commanderName);
		CruiseMissileProgram.LOG.info("{} impact at {} after {}s of flight",
				flight.bodyId, fmt(at), Math.round(flight.age));
		return true;
	}

	private static double horizontalRange(Vec3 from, Vec3 to) {
		double dx = to.x() - from.x();
		double dz = to.z() - from.z();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static String fmt(Vec3 v) {
		return String.format("%.0f,%.0f,%.0f", v.x(), v.y(), v.z());
	}
}

package dev.lilkuzco.cruisemissileprogram.missile;

import dev.lilkuzco.cruisemissileprogram.CruiseEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A cruise missile in flight. <b>A view of a kinetics body, not a physics object.</b>
 *
 * <p>It owns nothing about motion: {@link CruiseFlight} integrates the body on the server tick
 * and moves this entity to wherever the body ended up. There is deliberately no velocity field,
 * no gravity and no drag here, because the entity has no say in where it goes.
 *
 * <p><b>If this entity never ticks, nothing is lost but the picture.</b> That is the whole point
 * of the arrangement. A missile crossing a thousand blocks spends most of its flight over chunks
 * nobody has loaded, and an entity in such a chunk is not ticked at all — so a missile whose
 * flight depended on {@code tick()} would simply stop over open country and never arrive, with
 * no error anywhere. The flight is on the server tick; this is scenery.
 */
public class CruiseMissileEntity extends Entity {

	/** Synced so the renderer can bank the model into its turns without recomputing anything. */
	private static final EntityDataAccessor<Float> ROLL =
			SynchedEntityData.defineId(CruiseMissileEntity.class, EntityDataSerializers.FLOAT);

	private String bodyId = "";

	public CruiseMissileEntity(EntityType<? extends CruiseMissileEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public void setBodyId(String id) { this.bodyId = id; }

	public String bodyId() { return bodyId; }

	public float roll() { return entityData.get(ROLL); }

	public void setRoll(float roll) { entityData.set(ROLL, roll); }

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(ROLL, 0.0F);
	}

	/**
	 * Client-side only: the exhaust trail.
	 *
	 * <p>Server-side this does nothing at all, because the server has no business deciding
	 * anything here — the position it would read has already been written by the flight tracker
	 * earlier in the same tick.
	 */
	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) return;
		if (tickCount % 2 == 0) {
			level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
		}
	}

	/**
	 * Nothing can shoot a cruise missile down by hitting the view of it.
	 *
	 * <p>The entity is scenery — the flight lives in {@link CruiseFlight} — so damage applied
	 * here would be applied to the wrong object entirely, and a missile that vanished when a
	 * skeleton shot at it while the body kept flying invisibly to its target would be a genuinely
	 * baffling report. Interception is a phase-2 idea and belongs on the body, not here.
	 */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 24_000.0;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		bodyId = input.getStringOr("body_id", "");
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putString("body_id", bodyId);
	}
}

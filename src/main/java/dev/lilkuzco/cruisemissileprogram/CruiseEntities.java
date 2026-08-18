package dev.lilkuzco.cruisemissileprogram;

import dev.lilkuzco.cruisemissileprogram.missile.CruiseMissileEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** One entity: the window onto a cruise missile's kinetics body. */
public final class CruiseEntities {

	public static final EntityType<CruiseMissileEntity> CRUISE_MISSILE = register("cruise_missile",
			EntityType.Builder.<CruiseMissileEntity>of(CruiseMissileEntity::new, MobCategory.MISC)
					.noLootTable()
					// Long and slim, and the box has to contain the wings or they get culled.
					.sized(1.4F, 0.8F)
					.clientTrackingRange(16)
					.updateInterval(1));

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE,
				CruiseMissileProgram.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void register() {}

	private CruiseEntities() {}
}

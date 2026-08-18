package dev.lilkuzco.cruisemissileprogram;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

/**
 * Item data this mod attaches. One component: the warhead socketed into a cruise body.
 *
 * <p>Storing the warhead on the body stack rather than in a second launcher slot is what makes an
 * assembled round a single item — it can be crafted, carried, stacked with its like, and read at
 * a glance in a roster a thousand blocks away, all without the launcher having to remember which
 * warhead went with which airframe.
 */
public final class CruiseComponents {

	/** The warhead item id socketed into this body, absent if the body is bare. */
	public static final DataComponentType<Identifier> WARHEAD = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			CruiseMissileProgram.id("warhead"),
			DataComponentType.<Identifier>builder()
					.persistent(Identifier.CODEC)
					.networkSynchronized(ByteBufCodecs.fromCodec(Identifier.CODEC))
					.build());

	public static void register() {}

	private CruiseComponents() {}
}

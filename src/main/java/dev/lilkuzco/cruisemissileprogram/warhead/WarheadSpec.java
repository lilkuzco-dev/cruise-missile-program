package dev.lilkuzco.cruisemissileprogram.warhead;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * What one warhead does when it arrives.
 *
 * <p>Kinetics reports an impact — a position, a velocity, a mass — and stops there; rule I10 says
 * a physics library never applies damage, and there is deliberately no handle in its world probe
 * through which it could. So the consequence is defined here, in the mod that owns consequences.
 *
 * @param item     the item that is this warhead
 * @param tier     1..4, coarse power band; the cruise body refuses anything above its cap
 * @param blast    explosion strength passed to the level, in TNT-equivalent units
 * @param fire     whether the burst leaves fires
 * @param terrain  whether the burst breaks blocks
 */
public record WarheadSpec(Identifier item, int tier, float blast, boolean fire, boolean terrain) {

	public static final Codec<WarheadSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("item").forGetter(WarheadSpec::item),
			Codec.intRange(1, 4).optionalFieldOf("tier", 1).forGetter(WarheadSpec::tier),
			Codec.FLOAT.optionalFieldOf("blast", 4.0F).forGetter(WarheadSpec::blast),
			Codec.BOOL.optionalFieldOf("fire", false).forGetter(WarheadSpec::fire),
			Codec.BOOL.optionalFieldOf("terrain", true).forGetter(WarheadSpec::terrain))
			.apply(i, WarheadSpec::new));

	/**
	 * What an item in the warhead tag gets if nothing declared stats for it.
	 *
	 * <p>Deliberately the weakest thing in the table rather than an average. An unknown warhead
	 * is far more likely to be another mod's item that landed in the tag than a deliberate
	 * omission, and the failure mode of "quietly weaker than expected" is a great deal kinder
	 * than "quietly levelled the base".
	 */
	public static WarheadSpec fallback(Identifier item) {
		return new WarheadSpec(item, 1, 3.0F, false, true);
	}
}

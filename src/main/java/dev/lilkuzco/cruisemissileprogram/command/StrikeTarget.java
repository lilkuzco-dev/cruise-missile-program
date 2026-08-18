package dev.lilkuzco.cruisemissileprogram.command;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.Locale;

/**
 * Where a strike is aimed, and where that coordinate came from.
 *
 * <p>Provenance is carried alongside the numbers on purpose. A coordinate typed in by hand and a
 * coordinate read off a reconnaissance pass are the same three integers, and the console should
 * not present them as though they were equally trustworthy — a satellite fix has an age, and an
 * old one is worth knowing about before committing a missile to it.
 */
public record StrikeTarget(BlockPos pos, Source source, String label, long fixedAt) {

	public enum Source {
		/** Typed into the console. Always current, never verified. */
		MANUAL,
		/** Read from a reconnaissance satellite's imaging pass. Has an age. */
		SATELLITE;

		public String translationKey() {
			return "cruise_missile_program.target_source." + name().toLowerCase(Locale.ROOT);
		}
	}

	public static final Codec<StrikeTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(StrikeTarget::pos),
			Codec.STRING.optionalFieldOf("source", Source.MANUAL.name())
					.forGetter(t -> t.source().name()),
			Codec.STRING.optionalFieldOf("label", "").forGetter(StrikeTarget::label),
			Codec.LONG.optionalFieldOf("fixed_at", 0L).forGetter(StrikeTarget::fixedAt))
			.apply(i, (pos, source, label, fixedAt) ->
					new StrikeTarget(pos, sourceOf(source), label, fixedAt)));

	private static Source sourceOf(String name) {
		for (Source source : Source.values()) {
			if (source.name().equalsIgnoreCase(name)) return source;
		}
		return Source.MANUAL;
	}

	public static StrikeTarget manual(BlockPos pos, long now) {
		return new StrikeTarget(pos, Source.MANUAL, "", now);
	}

	/** How many seconds old this fix is. Manual fixes are reported as age zero. */
	public double ageSeconds(long now) {
		if (source == Source.MANUAL) return 0.0;
		return Math.max(0L, now - fixedAt) / 20.0;
	}
}

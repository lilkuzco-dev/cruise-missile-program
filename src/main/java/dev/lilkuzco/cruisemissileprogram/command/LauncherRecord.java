package dev.lilkuzco.cruisemissileprogram.command;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * What the command network knows about one launcher, <b>without loading its chunk</b>.
 *
 * <p>This is the mod's central trick and the reason the roster can be honest at a thousand
 * blocks. A launcher a kilometre away is in an unloaded chunk essentially always, so asking the
 * block entity what it holds would either return nothing or force a chunk load on every UI tick.
 * Instead the launcher <em>mirrors</em> its inventory into the saved network the moment that
 * inventory changes, and the console reads the mirror.
 *
 * <p>The consequence worth stating plainly: a mirror can go stale if something edits the tube's
 * contents without going through the tube (a datapack, a rogue hopper, an operator command).
 * {@link #updatedAt} is carried so the console can say how old the picture is rather than
 * implying it is live.
 *
 * @param pos        where the launcher is
 * @param dimension  which level it is in — a console cannot fire into another dimension
 * @param loaded     how many missiles are in the tube
 * @param capacity   how many it holds
 * @param warheads   the warhead item id in each occupied slot, in slot order, for the roster
 * @param armed      whether the launcher has been armed and is holding for a fire order
 * @param updatedAt  game time of the last mirror write
 */
public record LauncherRecord(
		BlockPos pos,
		Identifier dimension,
		int loaded,
		int capacity,
		List<String> warheads,
		boolean armed,
		long updatedAt) {

	public LauncherRecord {
		warheads = List.copyOf(warheads);
	}

	public static final Codec<LauncherRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(LauncherRecord::pos),
			Identifier.CODEC.fieldOf("dimension").forGetter(LauncherRecord::dimension),
			Codec.INT.fieldOf("loaded").forGetter(LauncherRecord::loaded),
			Codec.INT.fieldOf("capacity").forGetter(LauncherRecord::capacity),
			Codec.STRING.listOf().optionalFieldOf("warheads", List.of())
					.forGetter(LauncherRecord::warheads),
			Codec.BOOL.optionalFieldOf("armed", false).forGetter(LauncherRecord::armed),
			Codec.LONG.optionalFieldOf("updated_at", 0L).forGetter(LauncherRecord::updatedAt))
			.apply(i, LauncherRecord::new));

	public boolean empty() { return loaded <= 0; }

	/** An empty tube at a known position — what a freshly placed launcher registers as. */
	public static LauncherRecord vacant(BlockPos pos, Identifier dimension, int capacity, long now) {
		return new LauncherRecord(pos, dimension, 0, capacity, List.of(), false, now);
	}
}

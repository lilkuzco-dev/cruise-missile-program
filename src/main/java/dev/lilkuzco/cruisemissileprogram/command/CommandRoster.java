package dev.lilkuzco.cruisemissileprogram.command;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Who holds what rank at one console.
 *
 * <p>This is the mod's entire authority model, and it is deliberately <b>player-side and local to
 * the console</b> rather than borrowed from warfront. Warfront's factions are NPC organisations:
 * its "general" is an AI planner that scores tactical templates, and the only player-facing
 * quantity it keeps is standing — reputation earned with an AI faction. None of that can express
 * "these four players may fire and that one may not", so this mod keeps its own ladder, and a
 * player's rank here is never overridden by anything an NPC faction thinks of them.
 */
public record CommandRoster(Optional<UUID> owner, Map<UUID, CommandRank> ranks,
		CommandRank defaultRank) {

	public CommandRoster {
		ranks = Map.copyOf(ranks);
	}

	/** One rostered player. A list rather than a map codec so the save stays readable. */
	private record Seat(UUID player, String rank) {
		static final Codec<Seat> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("player").forGetter(Seat::player),
				Codec.STRING.fieldOf("rank").forGetter(Seat::rank))
				.apply(i, Seat::new));
	}

	public static final Codec<CommandRoster> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(CommandRoster::owner),
			Seat.CODEC.listOf().optionalFieldOf("seats", List.of()).forGetter(roster -> {
				List<Seat> seats = new java.util.ArrayList<>();
				roster.ranks().forEach((player, rank) -> seats.add(new Seat(player, rank.name())));
				return List.copyOf(seats);
			}),
			Codec.STRING.optionalFieldOf("default_rank", CommandRank.OBSERVER.name())
					.forGetter(roster -> roster.defaultRank().name()))
			.apply(i, (owner, seats, defaultRank) -> {
				Map<UUID, CommandRank> ranks = new LinkedHashMap<>();
				for (Seat seat : seats) ranks.put(seat.player(), CommandRank.byName(seat.rank()));
				return new CommandRoster(owner, ranks, CommandRank.byName(defaultRank));
			}));

	/** A console nobody has claimed yet. */
	public static CommandRoster unclaimed() {
		return new CommandRoster(Optional.empty(), Map.of(), CommandRank.OBSERVER);
	}

	/**
	 * This player's rank.
	 *
	 * <p>The owner is always COMMANDER — the rank is not stored for them, so it cannot be
	 * demoted by an edit to the seat list and leave a console nobody can administer.
	 */
	public CommandRank rankOf(UUID player) {
		if (owner.isPresent() && owner.get().equals(player)) return CommandRank.COMMANDER;
		return ranks.getOrDefault(player, defaultRank);
	}

	public CommandRoster claimedBy(UUID player) {
		return owner.isPresent() ? this : new CommandRoster(Optional.of(player), ranks, defaultRank);
	}

	/** Set a rank. COMMANDER is not assignable — there is exactly one, and it is the owner. */
	public CommandRoster with(UUID player, CommandRank rank) {
		if (owner.isPresent() && owner.get().equals(player)) return this;
		Map<UUID, CommandRank> next = new LinkedHashMap<>(ranks);
		if (rank == CommandRank.NONE || rank == CommandRank.COMMANDER) {
			next.remove(player);
			if (rank == CommandRank.NONE) next.put(player, CommandRank.NONE);
		} else {
			next.put(player, rank);
		}
		return new CommandRoster(owner, next, defaultRank);
	}

	public CommandRoster withDefault(CommandRank rank) {
		return new CommandRoster(owner, ranks, rank == CommandRank.COMMANDER
				? CommandRank.OBSERVER : rank);
	}

	public int seats() { return ranks.size(); }
}

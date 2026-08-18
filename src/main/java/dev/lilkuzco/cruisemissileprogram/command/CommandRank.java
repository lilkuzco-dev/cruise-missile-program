package dev.lilkuzco.cruisemissileprogram.command;

import java.util.Locale;

/**
 * Who may do what at a fire control console.
 *
 * <p><b>A player rank always outranks any NPC rank.</b> Warfront models faction authority as
 * NPC doctrine and per-player <em>standing</em> — reputation earned with an AI faction. None of
 * that gates a player here. A console's ladder is its own, players occupy it, and a warfront
 * general has no authority over a strike a player commands. Standing may colour the flavour text;
 * it may never move somebody up or down this enum.
 *
 * <p>Ordered least to most authority so {@link #outranks} is a plain ordinal comparison and a new
 * rank cannot be inserted without someone thinking about where it sits.
 */
public enum CommandRank {

	/** No authority. Cannot open the console; the block simply refuses. */
	NONE(false, false, false, false),

	/** May look. The roster, the target and the launcher inventory are readable, nothing else. */
	OBSERVER(true, false, false, false),

	/**
	 * The basic rank. May work the launchers — load missiles, set a target — but may not fire.
	 *
	 * <p>This is the rank that makes a command network a staffed thing rather than a one-player
	 * machine: a soldier can keep tubes loaded and a target designated so that the moment an
	 * officer arrives the strike is already set up, and can do none of it irreversibly.
	 */
	SOLDIER(true, true, false, false),

	/** May arm and fire on a target somebody set. Cannot re-wire the network or promote anyone. */
	OFFICER(true, true, true, false),

	/** The console's owner. Everything, including the callsign and the roster itself. */
	COMMANDER(true, true, true, true);

	private final boolean view;
	private final boolean designate;
	private final boolean fire;
	private final boolean administer;

	CommandRank(boolean view, boolean designate, boolean fire, boolean administer) {
		this.view = view;
		this.designate = designate;
		this.fire = fire;
		this.administer = administer;
	}

	/** Open the console and read the roster, target and remote inventory. */
	public boolean canView() { return view; }

	/** Set the target, and load or unload linked launchers. */
	public boolean canDesignate() { return designate; }

	/** Arm and launch. */
	public boolean canFire() { return fire; }

	/** Change the callsign, link or unlink launchers, and set other players' ranks. */
	public boolean canAdminister() { return administer; }

	public boolean outranks(CommandRank other) { return ordinal() > other.ordinal(); }

	public boolean atLeast(CommandRank other) { return ordinal() >= other.ordinal(); }

	public String translationKey() {
		return "cruise_missile_program.rank." + name().toLowerCase(Locale.ROOT);
	}

	public static CommandRank byName(String name) {
		for (CommandRank rank : values()) {
			if (rank.name().equalsIgnoreCase(name)) return rank;
		}
		return NONE;
	}
}

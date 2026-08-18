package dev.lilkuzco.cruisemissileprogram.command;

import dev.lilkuzco.cruisemissileprogram.CruiseBlockEntities;
import dev.lilkuzco.cruisemissileprogram.CruiseMissileProgram;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The fire control console: the one place a strike is decided.
 *
 * <p>It holds four things and no inventory at all — a callsign, a roster of who may do what, a
 * target, and nothing else. The missiles are somewhere else by design; that is the mod.
 *
 * <p><b>It does not tick.</b> Everything a console needs to show is either stored here or read
 * out of {@link CommandNetwork}'s saved data at the moment somebody looks, and a countdown in
 * progress lives in {@link StrikeTracker} on the server tick rather than here. A console is a
 * view onto state that other things own, which is what lets it be honest about launchers whose
 * chunks are not loaded.
 */
public class FireControlBlockEntity extends BlockEntity {

	private String callsign = "";
	private CommandRoster roster = CommandRoster.unclaimed();
	private Optional<StrikeTarget> target = Optional.empty();

	public FireControlBlockEntity(BlockPos pos, BlockState state) {
		super(CruiseBlockEntities.FIRE_CONTROL, pos, state);
	}

	// ---- identity ---------------------------------------------------------

	public String callsign() { return callsign; }

	public CommandRoster roster() { return roster; }

	public Optional<StrikeTarget> target() { return target; }

	public CommandRank rankOf(UUID player) { return roster.rankOf(player); }

	/**
	 * The first player to open an unclaimed console becomes its commander.
	 *
	 * <p>Same shape as warfront's display wall, which binds its owner on first interaction. It
	 * makes a freshly placed console usable immediately without a setup ritual, and every later
	 * rank flows from that one.
	 */
	public void claimIfUnowned(ServerPlayer player) {
		if (roster.owner().isEmpty()) {
			roster = roster.claimedBy(player.getUUID());
			setChanged();
		}
	}

	/**
	 * Take a callsign for this console, if no other console holds it.
	 *
	 * @return true if the console now owns the callsign
	 */
	public boolean setCallsign(String raw) {
		if (!(level instanceof ServerLevel server)) return false;
		String next = CommandNetwork.normalise(raw);
		CommandNetwork network = CommandNetwork.of(server);
		if (!callsign.isEmpty() && !callsign.equals(next)) {
			network.releaseConsole(callsign, worldPosition);
		}
		if (next.isEmpty()) {
			callsign = "";
			setChanged();
			return true;
		}
		if (!network.claim(next, worldPosition, server.dimension().identifier())) {
			// Somebody else's console already answers to this callsign. Refusing is the whole
			// point: silently taking it over would redirect their launchers to this console.
			return false;
		}
		callsign = next;
		setChanged();
		return true;
	}

	public void setRank(UUID player, CommandRank rank) {
		roster = roster.with(player, rank);
		setChanged();
	}

	public void setDefaultRank(CommandRank rank) {
		roster = roster.withDefault(rank);
		setChanged();
	}

	public void setTarget(StrikeTarget next) {
		target = Optional.of(next);
		setChanged();
	}

	public void clearTarget() {
		target = Optional.empty();
		setChanged();
	}

	// ---- the roster this console commands ---------------------------------

	/** Every launcher answering to this console's callsign, read from saved data. */
	public List<LauncherRecord> launchers(ServerLevel server) {
		return CommandNetwork.of(server).net(callsign)
				.map(CommandNetwork.Net::roster)
				.orElse(List.of());
	}

	/** Straight-line distance from this console to a launcher, in blocks. */
	public double distanceTo(LauncherRecord record) {
		return Math.sqrt(worldPosition.distSqr(record.pos()));
	}

	// ---- lifecycle --------------------------------------------------------

	public void onRemoved(ServerLevel server) {
		if (!callsign.isEmpty()) {
			CommandNetwork.of(server).releaseConsole(callsign, worldPosition);
		}
		StrikeTracker.cancelFrom(worldPosition);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.callsign = input.getStringOr("callsign", "");
		this.roster = input.read("roster", CommandRoster.CODEC).orElseGet(CommandRoster::unclaimed);
		this.target = input.read("target", StrikeTarget.CODEC);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("callsign", callsign);
		output.store("roster", CommandRoster.CODEC, roster);
		target.ifPresent(t -> output.store("target", StrikeTarget.CODEC, t));
	}
}

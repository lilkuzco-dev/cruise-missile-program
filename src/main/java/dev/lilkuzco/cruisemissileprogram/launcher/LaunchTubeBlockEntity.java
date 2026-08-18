package dev.lilkuzco.cruisemissileprogram.launcher;

import dev.lilkuzco.cruisemissileprogram.CruiseBlockEntities;
import dev.lilkuzco.cruisemissileprogram.CruiseItems;
import dev.lilkuzco.cruisemissileprogram.CruiseMissileProgram;
import dev.lilkuzco.cruisemissileprogram.command.CommandNetwork;
import dev.lilkuzco.cruisemissileprogram.command.LauncherRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * A box launcher: four tubes, a callsign, and no targeting of its own.
 *
 * <p>The tube is deliberately the dumb end of the system. It holds rounds, it answers to a
 * callsign, and it fires when told — by a console that may be a thousand blocks away, or by a
 * player standing in front of it who does not want a command network at all. It has no target
 * panel, because the whole premise of this mod is that targeting happens somewhere else.
 *
 * <p><b>Four tubes, not forty.</b> A launcher that never runs dry removes the only logistics
 * decision in the system. Four means a strike is a thing you plan and restock.
 */
public class LaunchTubeBlockEntity extends BaseContainerBlockEntity {

	public static final int CAPACITY = 4;

	private NonNullList<ItemStack> items = NonNullList.withSize(CAPACITY, ItemStack.EMPTY);
	private String callsign = "";
	private boolean armed;

	public LaunchTubeBlockEntity(BlockPos pos, BlockState state) {
		super(CruiseBlockEntities.LAUNCH_TUBE, pos, state);
	}

	// ---- callsign ---------------------------------------------------------

	public String callsign() { return callsign; }

	public boolean armed() { return armed; }

	public void setArmed(boolean value) {
		if (armed == value) return;
		armed = value;
		setChanged();
		mirror();
	}

	/**
	 * Join a callsign, leaving whatever network this tube was on.
	 *
	 * <p>A tube belongs to exactly one network at a time and re-linking is allowed — the rule is
	 * "one console per tube", not "one console ever". Leaving the old net explicitly is what stops
	 * a re-linked tube from haunting its previous roster as a launcher that can never be fired.
	 */
	public void setCallsign(String raw) {
		String next = CommandNetwork.normalise(raw);
		if (next.equals(callsign)) return;
		if (level instanceof ServerLevel server) {
			if (!callsign.isEmpty()) {
				CommandNetwork.of(server).remove(callsign, worldPosition);
			}
			callsign = next;
			if (!callsign.isEmpty()) {
				CommandNetwork.of(server).put(callsign, snapshot(server));
			}
		} else {
			callsign = next;
		}
		setChanged();
	}

	// ---- the mirror -------------------------------------------------------

	/**
	 * Push this tube's state into the saved command network.
	 *
	 * <p>Called on every change that a console could care about. This is what lets a roster be
	 * accurate at a thousand blocks: the console reads saved data, never a block entity, so it
	 * never needs the tube's chunk to be loaded and never forces it to load.
	 */
	public void mirror() {
		if (level instanceof ServerLevel server && !callsign.isEmpty()) {
			CommandNetwork.of(server).put(callsign, snapshot(server));
		}
	}

	private LauncherRecord snapshot(ServerLevel server) {
		List<String> warheads = new ArrayList<>();
		int loaded = 0;
		for (ItemStack stack : items) {
			if (stack.isEmpty()) continue;
			loaded++;
			warheads.add(CruiseItems.socketedWarhead(stack)
					.map(Identifier::toString)
					.orElse("cruise_missile_program:none"));
		}
		return new LauncherRecord(worldPosition.immutable(), server.dimension().identifier(),
				loaded, CAPACITY, warheads, armed, server.getGameTime());
	}

	/** Register a freshly placed tube. Called from the block's {@code onPlace}, never on load. */
	public void onPlaced(ServerLevel server) {
		if (!callsign.isEmpty()) {
			CommandNetwork.of(server).put(callsign, snapshot(server));
		}
	}

	/** Deregister a broken tube. Called from the block's removal hook, never from setRemoved. */
	public void onRemoved(ServerLevel server) {
		if (!callsign.isEmpty()) {
			CommandNetwork.of(server).remove(callsign, worldPosition);
		}
	}

	// ---- firing -----------------------------------------------------------

	/** The first loaded round, or empty. Does not consume it. */
	public ItemStack peekRound() {
		for (ItemStack stack : items) {
			if (CruiseItems.isLoadedRound(stack)) return stack;
		}
		return ItemStack.EMPTY;
	}

	/** Take one round out of the tube for launch. */
	public ItemStack consumeRound() {
		for (int slot = 0; slot < items.size(); slot++) {
			ItemStack stack = items.get(slot);
			if (!CruiseItems.isLoadedRound(stack)) continue;
			ItemStack round = stack.copyWithCount(1);
			stack.shrink(1);
			if (stack.isEmpty()) items.set(slot, ItemStack.EMPTY);
			setChanged();
			mirror();
			return round;
		}
		return ItemStack.EMPTY;
	}

	public int loadedRounds() {
		int count = 0;
		for (ItemStack stack : items) {
			if (CruiseItems.isLoadedRound(stack)) count++;
		}
		return count;
	}

	// ---- container --------------------------------------------------------

	@Override
	public int getContainerSize() { return CAPACITY; }

	@Override
	protected NonNullList<ItemStack> getItems() { return items; }

	@Override
	protected void setItems(NonNullList<ItemStack> replacement) {
		this.items = replacement;
		mirror();
	}

	/**
	 * Only assembled rounds go in the tube.
	 *
	 * <p>A bare airframe is refused here rather than at launch. A missile that flew a perfect
	 * profile and did nothing on arrival would be a genuinely baffling thing to debug, so the
	 * refusal happens at the slot the player is looking at.
	 */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return CruiseItems.isLoadedRound(stack);
	}

	@Override
	public void setChanged() {
		super.setChanged();
		mirror();
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.cruise_missile_program.launch_tube");
	}

	@Override
	protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
		return new LaunchTubeMenu(id, inventory, this);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(CAPACITY, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, this.items);
		this.callsign = input.getStringOr("callsign", "");
		this.armed = input.getBooleanOr("armed", false);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
		output.putString("callsign", callsign);
		output.putBoolean("armed", armed);
	}
}

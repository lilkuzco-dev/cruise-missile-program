package dev.lilkuzco.cruisemissileprogram.launcher;

import dev.lilkuzco.cruisemissileprogram.CruiseItems;
import dev.lilkuzco.cruisemissileprogram.CruiseMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Four tube slots and the player's inventory. Loading a launcher is the whole interaction. */
public class LaunchTubeMenu extends AbstractContainerMenu {

	private final Container tube;

	public LaunchTubeMenu(int id, Inventory inventory) {
		this(id, inventory, new SimpleContainer(LaunchTubeBlockEntity.CAPACITY));
	}

	public LaunchTubeMenu(int id, Inventory inventory, Container tube) {
		super(CruiseMenus.LAUNCH_TUBE, id);
		checkContainerSize(tube, LaunchTubeBlockEntity.CAPACITY);
		this.tube = tube;
		tube.startOpen(inventory.player);

		for (int slot = 0; slot < LaunchTubeBlockEntity.CAPACITY; slot++) {
			addSlot(new Slot(tube, slot, 44 + slot * 24, 35) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return CruiseItems.isLoadedRound(stack);
				}

				@Override
				public int getMaxStackSize() {
					return 1;
				}
			});
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(inventory, col, 8 + col * 18, 142));
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) return ItemStack.EMPTY;

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		int tubeSlots = LaunchTubeBlockEntity.CAPACITY;

		if (index < tubeSlots) {
			if (!moveItemStackTo(stack, tubeSlots, slots.size(), true)) return ItemStack.EMPTY;
		} else if (!moveItemStackTo(stack, 0, tubeSlots, false)) {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}

	@Override
	public boolean stillValid(Player player) {
		return tube.stillValid(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		tube.stopOpen(player);
	}
}

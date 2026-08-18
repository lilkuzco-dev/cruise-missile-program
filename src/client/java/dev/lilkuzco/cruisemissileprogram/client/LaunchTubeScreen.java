package dev.lilkuzco.cruisemissileprogram.client;

import dev.lilkuzco.cruisemissileprogram.launcher.LaunchTubeMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Four tubes and your pockets. Loading a launcher is the whole interaction.
 *
 * <p>Drawn rather than textured, like the rest of this mod family's screens: a background PNG
 * would be one more asset to keep in step with a slot layout that lives in the menu class.
 */
public class LaunchTubeScreen extends AbstractContainerScreen<LaunchTubeMenu> {

	private static final int PANEL = 0xFF11161F;
	private static final int WELL = 0xFF1F2937;
	private static final int FRAME = 0xFF374151;

	public LaunchTubeScreen(LaunchTubeMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 166);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY,
			float partialTick) {
		int x = leftPos;
		int y = topPos;
		g.fill(x, y, x + imageWidth, y + imageHeight, 0xF00A0E14);
		g.fill(x + 4, y + 14, x + imageWidth - 4, y + 60, PANEL);

		// Four tube mouths, drawn where the menu put the slots.
		for (int slot = 0; slot < 4; slot++) {
			slotFrame(g, x + 44 + slot * 24 - 1, y + 35 - 1);
		}
		// The player inventory well.
		g.fill(x + 7, y + 83, x + 169, y + 141, WELL);
		g.fill(x + 7, y + 141, x + 169, y + 159, WELL);
	}

	private static void slotFrame(GuiGraphicsExtractor g, int x, int y) {
		g.fill(x, y, x + 18, y + 18, WELL);
		g.fill(x, y, x + 18, y + 1, FRAME);
		g.fill(x, y + 17, x + 18, y + 18, FRAME);
		g.fill(x, y, x + 1, y + 18, FRAME);
		g.fill(x + 17, y, x + 18, y + 18, FRAME);
	}
}

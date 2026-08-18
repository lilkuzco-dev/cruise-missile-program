package dev.lilkuzco.cruisemissileprogram.client;

import dev.lilkuzco.cruisemissileprogram.command.CommandRank;
import dev.lilkuzco.cruisemissileprogram.command.StrikeTarget;
import dev.lilkuzco.cruisemissileprogram.net.CruiseNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * The command centre screen: roster, target, and the fire order.
 *
 * <p>Everything drawn here arrived in one server-built snapshot. The client resolves no ranks and
 * decides no permissions — the buttons a player cannot use are disabled as a courtesy, and the
 * server refuses the action regardless, because a UI is not a security boundary.
 *
 * <p>The roster is the reason this mod exists, so it gets the space: every linked launcher, how
 * far away it is, what is in it, and how old that picture is. A player should be able to plan a
 * strike from this screen without ever visiting the launcher.
 */
public class FireControlScreen extends Screen {

	private static final int WIDTH = 300;
	private static final int HEIGHT = 210;

	private static FireControlScreen open;

	/** The console screen currently on screen, or null. Lets a refresh update it in place. */
	public static FireControlScreen open() { return open; }

	private CruiseNet.ConsoleS2C data;
	private EditBox callsignBox;
	private EditBox targetBox;
	private int selectedLauncher;
	private int selectedSatellite;

	public FireControlScreen(CruiseNet.ConsoleS2C data) {
		super(Component.translatable("screen.cruise_missile_program.fire_control"));
		this.data = data;
	}

	/** A refreshed snapshot for a screen that is already open. */
	public void accept(CruiseNet.ConsoleS2C fresh) {
		this.data = fresh;
		if (callsignBox != null && !callsignBox.isFocused()) {
			callsignBox.setValue(fresh.callsign());
		}
		rebuildWidgets();
	}

	private int left() { return (width - WIDTH) / 2; }

	private int top() { return (height - HEIGHT) / 2; }

	private CommandRank rank() { return CommandRank.byName(data.viewerRank()); }

	@Override
	protected void init() {
		open = this;
		int x = left();
		int y = top();
		CommandRank rank = rank();

		callsignBox = new EditBox(font, x + 74, y + 22, 96, 16,
				Component.translatable("screen.cruise_missile_program.callsign"));
		callsignBox.setMaxLength(16);
		callsignBox.setValue(data.callsign());
		callsignBox.setEditable(rank.canAdminister());
		addRenderableWidget(callsignBox);

		addRenderableWidget(Button.builder(
				Component.translatable("screen.cruise_missile_program.link"),
				b -> send(CruiseNet.ConsoleActionC2S.SET_CALLSIGN, callsignBox.getValue()))
				.bounds(x + 174, y + 22, 46, 16)
				.build()).active = rank.canAdminister();

		targetBox = new EditBox(font, x + 74, y + 44, 96, 16,
				Component.translatable("screen.cruise_missile_program.target"));
		targetBox.setMaxLength(40);
		data.target().ifPresent(t -> targetBox.setValue(
				t.pos().getX() + " " + t.pos().getY() + " " + t.pos().getZ()));
		targetBox.setEditable(rank.canDesignate());
		addRenderableWidget(targetBox);

		addRenderableWidget(Button.builder(
				Component.translatable("screen.cruise_missile_program.designate"),
				b -> send(CruiseNet.ConsoleActionC2S.SET_TARGET, targetBox.getValue()))
				.bounds(x + 174, y + 44, 46, 16)
				.build()).active = rank.canDesignate();

		// Satellite targeting. Disabled with an explicit reason rather than hidden — "why is
		// there no satellite button" is a worse question than "why is this one greyed out".
		Button satellite = addRenderableWidget(Button.builder(
				satelliteLabel(),
				b -> {
					if (!data.satellites().isEmpty()) {
						send(CruiseNet.ConsoleActionC2S.PICK_SATELLITE,
								data.satellites().get(selectedSatellite).id());
					}
				})
				.bounds(x + 224, y + 44, 66, 16)
				.build());
		satellite.active = rank.canDesignate() && !data.satellites().isEmpty()
				&& data.satellites().get(Math.min(selectedSatellite,
						Math.max(0, data.satellites().size() - 1))).hasData();

		if (data.satellites().size() > 1) {
			addRenderableWidget(Button.builder(Component.literal(">"), b -> {
				selectedSatellite = (selectedSatellite + 1) % data.satellites().size();
				rebuildWidgets();
			}).bounds(x + 224, y + 62, 16, 14).build());
		}

		int launcherCount = data.launchers().size();
		if (launcherCount > 1) {
			addRenderableWidget(Button.builder(Component.literal("<"), b -> {
				selectedLauncher = (selectedLauncher - 1 + launcherCount) % launcherCount;
				rebuildWidgets();
			}).bounds(x + 10, y + HEIGHT - 28, 16, 16).build());
			addRenderableWidget(Button.builder(Component.literal(">"), b -> {
				selectedLauncher = (selectedLauncher + 1) % launcherCount;
				rebuildWidgets();
			}).bounds(x + 28, y + HEIGHT - 28, 16, 16).build());
		}

		Button fire = addRenderableWidget(Button.builder(
				Component.translatable("screen.cruise_missile_program.fire")
						.withStyle(ChatFormatting.RED),
				b -> send(CruiseNet.ConsoleActionC2S.FIRE, selectedLauncherPos()))
				.bounds(x + WIDTH - 90, y + HEIGHT - 28, 80, 16)
				.build());
		fire.active = rank.canFire() && data.target().isPresent() && hasLoadedLauncher()
				&& data.countdownTicks() < 0;
	}

	private String selectedLauncherPos() {
		if (data.launchers().isEmpty()) return "";
		CruiseNet.LauncherView view = data.launchers()
				.get(Math.min(selectedLauncher, data.launchers().size() - 1));
		BlockPos pos = view.pos();
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	private boolean hasLoadedLauncher() {
		return data.launchers().stream().anyMatch(v -> v.loaded() > 0);
	}

	private Component satelliteLabel() {
		if (!data.cosmosPresent()) {
			return Component.translatable("screen.cruise_missile_program.no_cosmos");
		}
		if (data.satellites().isEmpty()) {
			return Component.translatable("screen.cruise_missile_program.no_satellite");
		}
		CruiseNet.SatelliteView view = data.satellites()
				.get(Math.min(selectedSatellite, data.satellites().size() - 1));
		return view.hasData()
				? Component.translatable("screen.cruise_missile_program.satellite_fix")
				: Component.translatable("screen.cruise_missile_program.no_data");
	}

	private void send(String action, String argument) {
		ClientPlayNetworking.send(
				new CruiseNet.ConsoleActionC2S(data.console(), action, argument));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY,
			float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		int x = left();
		int y = top();

		g.fill(x, y, x + WIDTH, y + HEIGHT, 0xE0101418);
		outline(g, x, y, WIDTH, HEIGHT, 0xFF2F855A);
		g.fill(x, y, x + WIDTH, y + 20, 0xFF16202B);

		g.text(font, title, x + 10, y + 6, 0xFF7CFCB0);
		g.text(font, Component.translatable("screen.cruise_missile_program.rank",
				Component.translatable(rank().translationKey())),
				x + WIDTH - 116, y + 6, 0xFF9CA3AF);

		g.text(font, Component.translatable("screen.cruise_missile_program.callsign"),
				x + 10, y + 26, 0xFFD1D5DB);
		g.text(font, Component.translatable("screen.cruise_missile_program.target"),
				x + 10, y + 48, 0xFFD1D5DB);

		// Target provenance and age. A satellite fix two minutes old is still a fix, but the
		// player should be told rather than left to assume it is live.
		data.target().ifPresent(target -> {
			String provenance = Component.translatable(target.source().translationKey()).getString();
			String detail = target.source() == StrikeTarget.Source.SATELLITE
					&& !target.label().isEmpty()
					? provenance + " \u2014 " + target.label()
					: provenance;
			g.text(font, Component.literal(detail), x + 74, y + 64, 0xFF9CA3AF);
		});

		int rosterTop = y + 88;
		g.text(font, Component.translatable("screen.cruise_missile_program.roster",
				data.launchers().size()), x + 10, rosterTop - 12, 0xFF7CFCB0);

		if (data.launchers().isEmpty()) {
			g.text(font, Component.translatable("screen.cruise_missile_program.no_launchers"),
					x + 14, rosterTop + 4, 0xFF6B7280);
		} else {
			int row = 0;
			int selected = Math.min(selectedLauncher, data.launchers().size() - 1);
			for (CruiseNet.LauncherView view : data.launchers()) {
				if (row >= 5) break;
				int rowY = rosterTop + row * 14;
				if (row == selected) {
					g.fill(x + 8, rowY - 2, x + WIDTH - 8, rowY + 11, 0x402F855A);
				}
				int colour = view.loaded() > 0 ? 0xFFE5E7EB : 0xFF6B7280;
				g.text(font, Component.literal(String.format("%d,%d,%d", view.pos().getX(),
						view.pos().getY(), view.pos().getZ())), x + 14, rowY, colour);
				g.text(font, Component.literal(String.format("%.0fm", view.distance())),
						x + 110, rowY, 0xFF9CA3AF);
				g.text(font, Component.literal(view.loaded() + "/" + view.capacity()),
						x + 158, rowY, colour);
				g.text(font, Component.literal(view.warheads()), x + 192, rowY, 0xFF9CA3AF);
				row++;
			}
			if (data.launchers().size() > 5) {
				g.text(font, Component.translatable("screen.cruise_missile_program.more",
						data.launchers().size() - 5), x + 14, rosterTop + 5 * 14, 0xFF6B7280);
			}
		}

		if (data.countdownTicks() >= 0) {
			g.text(font, Component.translatable("screen.cruise_missile_program.countdown",
					String.format("%.1f", data.countdownTicks() / 20.0)),
					x + 10, y + HEIGHT - 24, 0xFFFF6B6B);
		}
	}

	@Override
	public void removed() {
		super.removed();
		if (open == this) open = null;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** A one-pixel frame, since the extractor draws rectangles and not outlines. */
	private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int colour) {
		g.fill(x, y, x + w, y + 1, colour);
		g.fill(x, y + h - 1, x + w, y + h, colour);
		g.fill(x, y, x + 1, y + h, colour);
		g.fill(x + w - 1, y, x + w, y + h, colour);
	}
}

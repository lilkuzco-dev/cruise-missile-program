package dev.lilkuzco.cruisemissileprogram.client;

import dev.lilkuzco.cruisemissileprogram.CruiseEntities;
import dev.lilkuzco.cruisemissileprogram.CruiseMenus;
import dev.lilkuzco.cruisemissileprogram.net.CruiseNet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * Client wiring: one screen, one renderer, one packet.
 *
 * <p>The renderer registration is not boilerplate. An unregistered entity type returns null from
 * the render dispatcher and hard-crashes the render thread the first time one is spawned, while
 * the server logs a perfectly successful flight — so this line is the difference between a mod
 * that works and a mod that appears to work everywhere except on the client that launched it.
 */
public class CruiseMissileProgramClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		MenuScreens.register(CruiseMenus.LAUNCH_TUBE, LaunchTubeScreen::new);

		ModelLayerRegistry.registerModelLayer(CruiseMissileModel.LAYER,
				CruiseMissileModel::createLayer);
		EntityRendererRegistry.register(CruiseEntities.CRUISE_MISSILE, CruiseMissileRenderer::new);

		ClientPlayNetworking.registerGlobalReceiver(CruiseNet.ConsoleS2C.TYPE,
				(payload, context) -> context.client().execute(() -> {
					Minecraft client = context.client();
					FireControlScreen open = FireControlScreen.open();
					if (open != null) {
						open.accept(payload);
					} else if (payload.openScreen()) {
						client.gui.setScreen(new FireControlScreen(payload));
					}
				}));
	}
}

package dev.lilkuzco.cruisemissileprogram;

import dev.lilkuzco.cruisemissileprogram.bridge.CosmosTargetBridge;
import dev.lilkuzco.cruisemissileprogram.bridge.WarfrontC2Bridge;
import dev.lilkuzco.cruisemissileprogram.command.CruiseCommands;
import dev.lilkuzco.cruisemissileprogram.command.StrikeTracker;
import dev.lilkuzco.cruisemissileprogram.missile.CruiseFlight;
import dev.lilkuzco.cruisemissileprogram.net.CruiseNet;
import dev.lilkuzco.cruisemissileprogram.warhead.WarheadRegistry;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cruise Missile Program — the command layer.
 *
 * <p>The headline of this mod is not the missile, it is the network: a fire control console
 * commands launchers that may be a thousand blocks away, over a callsign link, using target
 * coordinates fed down from orbit. The missile is the thing that happens at the end of a
 * decision made somewhere else entirely.
 *
 * <p><b>What this mod does not own.</b> Motion belongs to lilkuzco_kinetics — every metre of
 * cruise flight is integrated there, including the terrain-following law (RC5) that gives this
 * mod its flight profile. Orbital reconnaissance belongs to cosmos. The tactical display wall
 * belongs to warfront. This mod owns the command network, the launcher, the missile body, and
 * the consequence at the far end — because kinetics rule I10 says a physics library never
 * applies damage, and it is right.
 */
public class CruiseMissileProgram implements ModInitializer {

	public static final String MOD_ID = "cruise_missile_program";
	public static final Logger LOG = LoggerFactory.getLogger("CruiseMissileProgram");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		CruiseComponents.register();
		CruiseItems.register();
		CruiseBlocks.register();
		CruiseBlockEntities.register();
		CruiseMenus.register();
		CruiseEntities.register();
		WarheadRegistry.register();

		CruiseNet.registerCommon();
		CruiseCommands.register();

		// Both of these subscribe to END_SERVER_TICK. Nothing in this mod's simulation hangs off
		// an entity or block-entity tick, because both of them stop in unloaded chunks and every
		// interesting thing this mod does happens a long way from anybody standing anywhere.
		StrikeTracker.register();
		CruiseFlight.register();

		// Soft integration, registered once and inert when warfront is absent.
		WarfrontC2Bridge.register();

		LOG.info("Cruise Missile Program: command layer online (warfront={}, cosmos={})",
				WarfrontC2Bridge.available(), CosmosTargetBridge.available());
	}
}

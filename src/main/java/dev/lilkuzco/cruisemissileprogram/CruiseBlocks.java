package dev.lilkuzco.cruisemissileprogram;

import dev.lilkuzco.cruisemissileprogram.command.FireControlBlock;
import dev.lilkuzco.cruisemissileprogram.launcher.LaunchTubeBlock;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Function;

/** Two blocks: the place decisions are made, and the place missiles are kept. */
public final class CruiseBlocks {

	/** The command centre. Roster, target, authority and the fire order all live here. */
	public static final Block FIRE_CONTROL_CONSOLE = register(
			"fire_control_console", FireControlBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GREEN)
					.instrument(NoteBlockInstrument.BASEDRUM)
					.requiresCorrectToolForDrops()
					.strength(4.0F, 8.0F)
					.lightLevel(state -> 7)
					.sound(SoundType.METAL));

	/** The box launcher. Four tubes and a callsign; no target panel by design. */
	public static final Block LAUNCH_TUBE = register(
			"launch_tube", LaunchTubeBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GRAY)
					.instrument(NoteBlockInstrument.BASEDRUM)
					.requiresCorrectToolForDrops()
					.strength(4.5F, 9.0F)
					.sound(SoundType.METAL));

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
			BlockBehaviour.Properties properties) {
		Identifier id = CruiseMissileProgram.id(name);
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey,
				factory.apply(properties.setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		Registry.register(BuiltInRegistries.ITEM, itemKey,
				new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey)));
		return block;
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(out -> {
			out.accept(FIRE_CONTROL_CONSOLE);
			out.accept(LAUNCH_TUBE);
		});
	}

	private CruiseBlocks() {}
}

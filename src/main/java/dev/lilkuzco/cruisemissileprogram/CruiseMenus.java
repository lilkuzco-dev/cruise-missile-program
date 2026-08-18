package dev.lilkuzco.cruisemissileprogram;

import dev.lilkuzco.cruisemissileprogram.launcher.LaunchTubeMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;

public final class CruiseMenus {

	public static final MenuType<LaunchTubeMenu> LAUNCH_TUBE = Registry.register(
			BuiltInRegistries.MENU, CruiseMissileProgram.id("launch_tube"),
			new MenuType<>(LaunchTubeMenu::new, net.minecraft.world.flag.FeatureFlags.VANILLA_SET));

	public static void register() {}

	private CruiseMenus() {}
}

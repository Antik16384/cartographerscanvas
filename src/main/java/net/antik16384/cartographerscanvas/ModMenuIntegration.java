package net.antik16384.cartographerscanvas;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.antik16384.cartographerscanvas.gui.CartographersCanvasConfigScreen;

public class ModMenuIntegration implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return CartographersCanvasConfigScreen::new;
	}
}

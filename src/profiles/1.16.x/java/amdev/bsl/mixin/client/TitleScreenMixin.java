package amdev.bsl.mixin.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	protected TitleScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void bsl$enableMultiplayerInDev(CallbackInfo ci) {
		if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
			return;
		}

		String multiplayerLabel = new TranslatableComponent("menu.multiplayer").getString();
		for (AbstractWidget widget : this.buttons) {
			if (!(widget instanceof Button button)) {
				continue;
			}
			if (!multiplayerLabel.equals(button.getMessage().getString())) {
				continue;
			}
			button.active = true;
			break;
		}
	}
}

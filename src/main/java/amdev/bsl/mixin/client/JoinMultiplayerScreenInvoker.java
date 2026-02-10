package amdev.bsl.mixin.client;

import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(JoinMultiplayerScreen.class)
public interface JoinMultiplayerScreenInvoker {
	@Invoker("refreshServerList")
	void bsl$refreshServerList();
}

package amdev.bsl.mixin.client;

import java.util.List;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerSelectionList.class)
public interface ServerSelectionListAccessor {
	@Accessor("onlineServers")
	List<ServerSelectionList.OnlineServerEntry> bsl$getOnlineServers();

	@Invoker("refreshEntries")
	void bsl$refreshEntries();
}

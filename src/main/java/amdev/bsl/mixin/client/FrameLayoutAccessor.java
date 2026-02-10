package amdev.bsl.mixin.client;

import java.util.List;
import net.minecraft.client.gui.layouts.FrameLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FrameLayout.class)
public interface FrameLayoutAccessor {
	@Accessor("children")
	List<?> bsl$getChildren();
}

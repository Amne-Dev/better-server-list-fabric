package amdev.bsl.mixin.client;

import amdev.bsl.client.ServerMetadataStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class OnlineServerEntryMixin {
	@Shadow
	private ServerData serverData;

	@Shadow
	private Minecraft minecraft;

	@Inject(method = "extractContent", at = @At("TAIL"))
	private void bsl$renderServerTags(GuiGraphicsExtractor guiGraphics, int x, int y, boolean hovered, float partialTick, CallbackInfo ci) {
		if (this.serverData == null || this.serverData.ip == null) {
			return;
		}

		boolean favorite = ServerMetadataStore.isFavorite(this.serverData.ip);
		String category = ServerMetadataStore.getCategory(this.serverData.ip);
		if (!favorite && (category == null || category.isBlank())) {
			return;
		}

		ObjectSelectionList.Entry<?> entry = (ObjectSelectionList.Entry<?>) (Object) this;
		int rowX = entry.getContentX();
		int rowY = entry.getContentY();
		int rowRight = entry.getContentRight();

		if (favorite) {
			guiGraphics.text(this.minecraft.font, "★", rowX + 3, rowY + 10, 0xFFFFDD55, false);
		}

		if (category != null && !category.isBlank()) {
			String categoryLabel = '[' + this.bsl$abbreviate(category, 14) + ']';
			int labelWidth = this.minecraft.font.width(categoryLabel);
			int nameStartX = rowX + 35;
			int nameWidth = this.minecraft.font.width(this.serverData.name == null ? "" : this.serverData.name);
			int preferredX = nameStartX + Math.min(nameWidth + 6, 170);
			int maxX = rowRight - 40 - labelWidth;
			if (maxX >= nameStartX + 8) {
				int labelX = Math.max(nameStartX + 8, Math.min(preferredX, maxX));
				guiGraphics.text(this.minecraft.font, categoryLabel, labelX, rowY + 1, 0xFFFFD070, false);
			}
		}
	}

	@Unique
	private String bsl$abbreviate(String value, int maxLength) {
		if (value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength - 3) + "...";
	}
}

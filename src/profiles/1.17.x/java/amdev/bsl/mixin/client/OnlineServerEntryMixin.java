package amdev.bsl.mixin.client;

import amdev.bsl.client.ServerMetadataStore;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
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

	@Inject(method = "render", at = @At("TAIL"))
	private void bsl$renderServerTags(PoseStack poseStack, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float partialTick, CallbackInfo ci) {
		if (this.serverData == null || this.serverData.ip == null) {
			return;
		}

		boolean favorite = ServerMetadataStore.isFavorite(this.serverData.ip);
		String category = ServerMetadataStore.getCategory(this.serverData.ip);
		if (!favorite && category.isEmpty()) {
			return;
		}

		if (favorite) {
			this.minecraft.font.draw(poseStack, "★", x + 3.0f, y + 10.0f, 0xFFFFDD55);
		}

		if (!category.isEmpty()) {
			String categoryLabel = '[' + this.bsl$abbreviate(category, 14) + ']';
			int labelWidth = this.minecraft.font.width(categoryLabel);
			int nameStartX = x + 35;
			int nameWidth = this.minecraft.font.width(this.serverData.name == null ? "" : this.serverData.name);
			int preferredX = nameStartX + Math.min(nameWidth + 6, 170);
			int maxX = x + entryWidth - 40 - labelWidth;
			if (maxX >= nameStartX + 8) {
				int labelX = Math.max(nameStartX + 8, Math.min(preferredX, maxX));
				this.minecraft.font.draw(poseStack, categoryLabel, labelX, y + 1.0f, 0xFFFFD070);
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

package amdev.bsl.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;

public final class CategoryEditScreen extends Screen {
	private final Screen parent;
	private final Consumer<String> onSave;
	private final String initialValue;
	private EditBox categoryField;

	public CategoryEditScreen(Screen parent, String initialValue, Consumer<String> onSave) {
		super(new TranslatableComponent("bsl.screen.category_edit.title"));
		this.parent = parent;
		this.initialValue = initialValue == null ? "" : initialValue;
		this.onSave = onSave;
	}

	@Override
	protected void init() {
		int fieldWidth = Math.min(320, this.width - 40);
		int centerX = this.width / 2;
		int centerY = this.height / 2;

		this.categoryField = this.addRenderableWidget(new EditBox(this.font, centerX - fieldWidth / 2, centerY - 10, fieldWidth, 20, new TranslatableComponent("bsl.gui.category")));
		this.categoryField.setMaxLength(32);
		this.categoryField.setValue(this.initialValue);
		this.categoryField.setFocus(true);

		this.addRenderableWidget(new Button(centerX - 155, centerY + 22, 100, 20, new TranslatableComponent("bsl.button.save"), button -> this.bsl$saveAndClose()));
		this.addRenderableWidget(new Button(centerX - 50, centerY + 22, 100, 20, new TranslatableComponent("bsl.button.clear"), button -> this.bsl$clearAndClose()));
		this.addRenderableWidget(new Button(centerX + 55, centerY + 22, 100, 20, new TranslatableComponent("gui.cancel"), button -> this.minecraft.setScreen(this.parent)));
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		this.fill(poseStack, 0, 0, this.width, this.height, 0xB0101010);
		drawCenteredString(poseStack, this.font, this.title, this.width / 2, this.height / 2 - 34, 0xFFFFFFFF);
		drawCenteredString(poseStack, this.font, new TranslatableComponent("bsl.screen.category_edit.hint"), this.width / 2, this.height / 2 - 22, 0xFFA0A0A0);
		super.render(poseStack, mouseX, mouseY, partialTick);
	}

	private void bsl$saveAndClose() {
		this.onSave.accept(this.categoryField.getValue().trim());
		this.minecraft.setScreen(this.parent);
	}

	private void bsl$clearAndClose() {
		this.onSave.accept("");
		this.minecraft.setScreen(this.parent);
	}
}

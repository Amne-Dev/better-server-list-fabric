package amdev.bsl.client.gui;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CategoryEditScreen extends Screen {
	private final Screen parent;
	private final Consumer<String> onSave;
	private final String initialValue;
	private EditBox categoryField;

	public CategoryEditScreen(Screen parent, String initialValue, Consumer<String> onSave) {
		super(Component.literal("Edit Category"));
		this.parent = parent;
		this.initialValue = initialValue == null ? "" : initialValue;
		this.onSave = onSave;
	}

	@Override
	protected void init() {
		int fieldWidth = Math.min(320, this.width - 40);
		int centerX = this.width / 2;
		int centerY = this.height / 2;

		this.categoryField = this.addRenderableWidget(
			new EditBox(this.font, centerX - fieldWidth / 2, centerY - 10, fieldWidth, 20, Component.literal("Category"))
		);
		this.categoryField.setMaxLength(32);
		this.categoryField.setValue(this.initialValue);
		this.setInitialFocus(this.categoryField);

		this.addRenderableWidget(
			Button.builder(Component.literal("Save"), button -> this.bsl$saveAndClose()).bounds(centerX - 155, centerY + 22, 100, 20).build()
		);
		this.addRenderableWidget(
			Button.builder(Component.literal("Clear"), button -> this.bsl$clearAndClose()).bounds(centerX - 50, centerY + 22, 100, 20).build()
		);
		this.addRenderableWidget(
			Button.builder(Component.literal("Cancel"), button -> this.minecraft.setScreen(this.parent)).bounds(centerX + 55, centerY + 22, 100, 20).build()
		);
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
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		guiGraphics.fill(0, 0, this.width, this.height, 0xB0101010);
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 34, 0xFFFFFFFF);
		guiGraphics.drawCenteredString(this.font, "Type any custom category name", this.width / 2, this.height / 2 - 22, 0xFFA0A0A0);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
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

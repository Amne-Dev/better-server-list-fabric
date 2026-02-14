package amdev.bsl.client.gui;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;

public final class CategoryEditScreen extends Screen {
	private final Screen parent;
	private final Consumer<String> onSave;
	private final String initialValue;
	private EditBox categoryField;

	public CategoryEditScreen(Screen parent, String initialValue, Consumer<String> onSave) {
		super(new TextComponent("Edit Category"));
		this.parent = parent;
		this.initialValue = initialValue == null ? "" : initialValue;
		this.onSave = onSave;
	}

	@Override
	protected void init() {
		int fieldWidth = Math.min(320, this.width - 40);
		int centerX = this.width / 2;
		int centerY = this.height / 2;

		this.categoryField = this.addButton(new EditBox(this.font, centerX - fieldWidth / 2, centerY - 10, fieldWidth, 20, "Category"));
		this.categoryField.setMaxLength(32);
		this.categoryField.setValue(this.initialValue);
		this.categoryField.setFocus(true);

		this.addButton(new Button(centerX - 155, centerY + 22, 100, 20, "Save", button -> this.bsl$saveAndClose()));
		this.addButton(new Button(centerX - 50, centerY + 22, 100, 20, "Clear", button -> this.bsl$clearAndClose()));
		this.addButton(new Button(centerX + 55, centerY + 22, 100, 20, "Cancel", button -> this.minecraft.setScreen(this.parent)));
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
	public void render(int mouseX, int mouseY, float partialTick) {
		this.fill(0, 0, this.width, this.height, 0xB0101010);
		drawCenteredString(this.font, this.title.getString(), this.width / 2, this.height / 2 - 34, 0xFFFFFFFF);
		drawCenteredString(this.font, "Type any custom category name", this.width / 2, this.height / 2 - 22, 0xFFA0A0A0);
		super.render(mouseX, mouseY, partialTick);
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

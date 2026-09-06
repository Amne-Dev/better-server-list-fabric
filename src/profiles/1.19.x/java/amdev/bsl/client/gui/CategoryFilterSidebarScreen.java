package amdev.bsl.client.gui;

import amdev.bsl.client.ServerMetadataStore;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CategoryFilterSidebarScreen extends Screen {
	private static final int PANEL_MARGIN = 8;
	private static final int PANEL_WIDTH = 220;
	private static final int PANEL_PADDING = 8;
	private static final int HEADER_HEIGHT = 30;
	private static final int FOOTER_HEIGHT = 56;
	private static final int ROW_HEIGHT = 20;
	private static final int ROW_GAP = 2;

	private final Screen parent;
	private final Consumer<String> onSelect;
	private final String activeCategory;
	private final int page;

	public CategoryFilterSidebarScreen(Screen parent, String activeCategory, Consumer<String> onSelect) {
		this(parent, activeCategory, onSelect, 0);
	}

	private CategoryFilterSidebarScreen(Screen parent, String activeCategory, Consumer<String> onSelect, int page) {
		super(Component.translatable("bsl.category.sidebar.title"));
		this.parent = parent;
		this.onSelect = onSelect;
		this.activeCategory = activeCategory == null ? "" : activeCategory;
		this.page = Math.max(0, page);
	}

	@Override
	protected void init() {
		List<String> options = this.bsl$buildOptions();
		int pageSize = this.bsl$getPageSize();
		int totalPages = Math.max(1, (options.size() + pageSize - 1) / pageSize);
		int currentPage = Math.max(0, Math.min(this.page, totalPages - 1));

		int panelLeft = this.width - PANEL_WIDTH - PANEL_MARGIN;
		int panelTop = PANEL_MARGIN;
		int panelHeight = this.height - PANEL_MARGIN * 2;
		int buttonX = panelLeft + PANEL_PADDING;
		int buttonWidth = PANEL_WIDTH - PANEL_PADDING * 2;
		int from = currentPage * pageSize;
		int to = Math.min(options.size(), from + pageSize);
		int y = panelTop + HEADER_HEIGHT;

		for (int i = from; i < to; i++) {
			String option = options.get(i);
			Component buttonTitle = this.bsl$formatOptionComponent(option);
			this.addRenderableWidget(
				Button.builder(buttonTitle, button -> this.bsl$selectAndClose(option))
					.bounds(buttonX, y, buttonWidth, ROW_HEIGHT)
					.build()
			);
			y += ROW_HEIGHT + ROW_GAP;
		}

		int navY = panelTop + panelHeight - FOOTER_HEIGHT;
		int navWidth = (buttonWidth - PANEL_PADDING) / 2;
		Button prevButton = this.addRenderableWidget(
			Button.builder(Component.translatable("bsl.button.prev"), button -> this.bsl$openPage(currentPage - 1))
				.bounds(buttonX, navY, navWidth, ROW_HEIGHT)
				.build()
		);
		prevButton.active = currentPage > 0;

		Button nextButton = this.addRenderableWidget(
			Button.builder(Component.translatable("bsl.button.next"), button -> this.bsl$openPage(currentPage + 1))
				.bounds(buttonX + navWidth + PANEL_PADDING, navY, navWidth, ROW_HEIGHT)
				.build()
		);
		nextButton.active = currentPage < totalPages - 1;

		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.done"), button -> this.onClose())
				.bounds(buttonX, navY + ROW_HEIGHT + 4, buttonWidth, ROW_HEIGHT)
				.build()
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
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		int panelLeft = this.width - PANEL_WIDTH - PANEL_MARGIN;
		int panelTop = PANEL_MARGIN;
		int panelHeight = this.height - PANEL_MARGIN * 2;

		this.parent.render(poseStack, mouseX, mouseY, partialTick);
		this.fill(poseStack, 0, 0, this.width, this.height, 0x6A000000);
		this.fill(poseStack, panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + panelHeight, 0xD0101010);
		int listTop = panelTop + HEADER_HEIGHT - 2;
		int listBottom = panelTop + panelHeight - FOOTER_HEIGHT + 2;
		this.fill(poseStack, panelLeft + 2, listTop, panelLeft + PANEL_WIDTH - 2, listBottom, 0xFF101010);
		drawCenteredString(poseStack, this.font, this.title, panelLeft + PANEL_WIDTH / 2, panelTop + 8, 0xFFFFFFFF);
		drawString(poseStack, this.font, Component.translatable("bsl.category.sidebar.subtitle"), panelLeft + PANEL_PADDING, panelTop + 20, 0xFFA0A0A0);
		super.render(poseStack, mouseX, mouseY, partialTick);
	}

	private void bsl$openPage(int targetPage) {
		this.minecraft.setScreen(new CategoryFilterSidebarScreen(this.parent, this.activeCategory, this.onSelect, targetPage));
	}

	private void bsl$selectAndClose(String category) {
		this.onSelect.accept(category == null ? "" : category);
		this.minecraft.setScreen(this.parent);
	}

	private List<String> bsl$buildOptions() {
		List<String> options = new ArrayList<>();
		options.add("");
		options.addAll(ServerMetadataStore.getKnownCategories());
		return options;
	}

	private int bsl$getPageSize() {
		int panelHeight = this.height - PANEL_MARGIN * 2;
		int listHeight = panelHeight - HEADER_HEIGHT - FOOTER_HEIGHT - 4;
		return Math.max(1, listHeight / (ROW_HEIGHT + ROW_GAP));
	}

	private Component bsl$formatOptionComponent(String option) {
		String safeOption = option == null ? "" : option;
		boolean isSelected = safeOption.equalsIgnoreCase(this.activeCategory) 
			|| (safeOption.isBlank() && this.activeCategory.isBlank());

		Component baseLabel = safeOption.isBlank() 
			? Component.translatable("bsl.category.all") 
			: Component.literal(this.bsl$abbreviate(safeOption, 19));

		return isSelected 
			? Component.translatable("bsl.category.selected_format", baseLabel) 
			: baseLabel;
	}

	private String bsl$abbreviate(String value, int maxLength) {
		if (value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength - 3) + "...";
	}
}

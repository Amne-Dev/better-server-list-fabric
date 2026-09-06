package amdev.bsl.mixin.client;

import amdev.bsl.client.ServerMetadataStore;
import amdev.bsl.client.ServerOrdering;
import amdev.bsl.client.gui.CategoryFilterSidebarScreen;
import amdev.bsl.client.gui.CategoryEditScreen;
import amdev.bsl.client.gui.ServerBrowserScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {
	protected JoinMultiplayerScreenMixin(Component title) {
		super(title);
	}

	@Shadow
	protected ServerSelectionList serverSelectionList;

	@Shadow
	private ServerList servers;

	@Shadow
	protected abstract void onSelectedChange();

	@Shadow
	private HeaderAndFooterLayout layout;

	@Unique
	private Button bslFavoriteButton;

	@Unique
	private Button bslCategoryButton;

	@Unique
	private Button bslFindServersButton;

	@Unique
	private EditBox bslSearchBox;

	@Unique
	private Button bslCategoryFilterButton;

	@Unique
	private String bslActiveCategoryFilter = "";

	@Unique
	private final List<Button> bslCategoryDropdownButtons = new ArrayList<>();

	@Unique
	private boolean bslCategoryDropdownOpen;

	@Unique
	private int bslCategoryFilterX;

	@Unique
	private int bslCategoryFilterY;

	@Unique
	private int bslCategoryFilterWidth = 120;

	@Unique
	private int bslLastLayoutWidth = -1;

	@Unique
	private int bslLastLayoutHeight = -1;

	@Unique
	private boolean bslPendingFilterApply;

	@Unique
	private static final int BSL_CONTROL_HEIGHT = 20;

	@Unique
	private static final int BSL_CONTROL_GAP = 4;

	@Unique
	private static final int BSL_TOP_Y = 36;

	@Unique
	private static final int BSL_HEADER_HEIGHT = 118;

	@Unique
	private static final int BSL_LIST_TOP_GAP = 8;

	@Unique
	private static final int BSL_LIST_BOTTOM_PADDING = 64;

	@Inject(method = "init", at = @At("TAIL"))
	private void bsl$addCategoryButtons(CallbackInfo ci) {
		this.bsl$removeVanillaTitleFromHeader();

		if (this.layout != null && this.layout.getHeaderHeight() < BSL_HEADER_HEIGHT) {
			this.layout.setHeaderHeight(BSL_HEADER_HEIGHT);
			this.repositionElements();
		}

		this.bslSearchBox = this.addRenderableWidget(new EditBox(this.minecraft.font, 8, 8, 180, BSL_CONTROL_HEIGHT, Component.translatable("bsl.gui.search")));
		this.bslSearchBox.setHint(Component.translatable("bsl.gui.search_hint"));
		this.bslSearchBox.setResponder(value -> this.bsl$applyServerView(null));

		this.bslFavoriteButton = this.addRenderableWidget(
			Button.builder(Component.translatable("bsl.button.favorite"), button -> this.bsl$toggleFavorite()).bounds(8, 8, 100, BSL_CONTROL_HEIGHT).build()
		);
		this.bslCategoryButton = this.addRenderableWidget(
			Button.builder(Component.translatable("bsl.button.category"), button -> this.bsl$openCategoryEditor()).bounds(8, 8, 120, BSL_CONTROL_HEIGHT).build()
		);
		this.bslFindServersButton = this.addRenderableWidget(
			Button.builder(Component.translatable("bsl.button.find"), button -> this.bsl$openServerBrowser()).bounds(8, 8, 110, BSL_CONTROL_HEIGHT).build()
		);
		this.bslCategoryFilterButton = this.addRenderableWidget(
			Button.builder(Component.translatable("bsl.button.category_filter", Component.translatable("bsl.category.all").getString()), button -> this.bsl$toggleCategoryDropdown()).bounds(8, 8, 120, BSL_CONTROL_HEIGHT).build()
		);
		this.bsl$layoutWidgets();
		this.bsl$applyServerView(null);
	}

	@Inject(method = "repositionElements", at = @At("TAIL"))
	private void bsl$repositionCustomWidgets(CallbackInfo ci) {
		this.bsl$layoutWidgets();
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void bsl$ensureLayoutOnResize(CallbackInfo ci) {
		if (this.width != this.bslLastLayoutWidth || this.height != this.bslLastLayoutHeight) {
			this.bsl$layoutWidgets();
		}
		if (this.bslPendingFilterApply && this.minecraft != null && this.minecraft.screen == (Object) this) {
			this.bslPendingFilterApply = false;
			this.bsl$applyServerView(null);
		}
	}

	@Inject(method = "refreshServerList", at = @At("TAIL"))
	private void bsl$sortOnRefresh(CallbackInfo ci) {
		this.bsl$applyServerView(null);
	}

	@Inject(method = "onSelectedChange", at = @At("TAIL"))
	private void bsl$updateButtonsOnSelection(CallbackInfo ci) {
		this.bsl$updateButtons();
	}

	@Inject(method = "removed", at = @At("TAIL"))
	private void bsl$saveStore(CallbackInfo ci) {
		this.bsl$closeCategoryDropdown();
		ServerMetadataStore.save();
	}
	@Unique
	private void bsl$toggleFavorite() {
		ServerData selected = this.bsl$getSelectedServerData();
		if (selected == null) {
			return;
		}

		String key = selected.ip;
		boolean nextValue = !ServerMetadataStore.isFavorite(key);
		ServerMetadataStore.setFavorite(key, nextValue);
		this.bsl$applyServerView(key);
	}

	@Unique
	private void bsl$openCategoryEditor() {
		ServerData selected = this.bsl$getSelectedServerData();
		if (selected == null) {
			return;
		}

		String key = selected.ip;
		String current = ServerMetadataStore.getCategory(key);
		this.minecraft.setScreen(new CategoryEditScreen((JoinMultiplayerScreen) (Object) this, current, next -> {
			ServerMetadataStore.setCategory(key, next);
			if (!this.bslActiveCategoryFilter.isBlank() && !ServerMetadataStore.getKnownCategories().contains(this.bslActiveCategoryFilter)) {
				this.bslActiveCategoryFilter = "";
			}
			this.bsl$applyServerView(key);
		}));
	}

	@Unique
	private void bsl$openServerBrowser() {
		this.bsl$closeCategoryDropdown();
		this.minecraft.setScreen(new ServerBrowserScreen((JoinMultiplayerScreen) (Object) this));
	}

	@Unique
	public void bsl$applyServerView(String preferredAddress) {
		if (this.serverSelectionList == null || this.servers == null) {
			return;
		}

		String selectedAddress = preferredAddress;
		if (selectedAddress == null) {
			ServerData selected = this.bsl$getSelectedServerData();
			if (selected != null) {
				selectedAddress = selected.ip;
			}
		}

		ServerOrdering.reorder(this.servers);
		this.serverSelectionList.updateOnlineServers(this.servers);
		this.bsl$filterEntries();

		this.bsl$restoreSelection(selectedAddress);
		this.onSelectedChange();
		this.bsl$updateButtons();
	}

	@Unique
	private void bsl$filterEntries() {
		String query = this.bsl$getSearchQuery();
		String categoryFilter = this.bslActiveCategoryFilter;

		ServerSelectionListAccessor accessor = (ServerSelectionListAccessor) this.serverSelectionList;
		List<ServerSelectionList.OnlineServerEntry> onlineServers = accessor.bsl$getOnlineServers();
		onlineServers.removeIf(entry -> {
			ServerData data = entry.getServerData();
			return !this.bsl$matchesSearch(data, query) || !this.bsl$matchesCategory(data, categoryFilter);
		});
		accessor.bsl$refreshEntries();
	}

	@Unique
	private void bsl$restoreSelection(String serverAddress) {
		if (serverAddress != null) {
			for (ServerSelectionList.Entry entry : this.serverSelectionList.children()) {
				if (!(entry instanceof ServerSelectionList.OnlineServerEntry onlineServerEntry)) {
					continue;
				}
				ServerData data = onlineServerEntry.getServerData();
				if (data != null && serverAddress.equalsIgnoreCase(data.ip)) {
					this.serverSelectionList.setSelected(entry);
					break;
				}
			}
		}
	}

	@Unique
	private void bsl$updateButtons() {
		if (this.bslFavoriteButton == null || this.bslCategoryButton == null || this.bslFindServersButton == null || this.bslCategoryFilterButton == null) {
			return;
		}

		ServerData selected = this.bsl$getSelectedServerData();
		boolean hasSelection = selected != null;
		this.bslFavoriteButton.active = hasSelection;
		this.bslCategoryButton.active = hasSelection;

		if (!hasSelection) {
			this.bslFavoriteButton.setMessage(Component.translatable("bsl.button.favorite"));
			this.bslCategoryButton.setMessage(Component.translatable("bsl.button.category"));
			this.bslFindServersButton.active = true;
			this.bsl$updateCategoryFilterButton();
			return;
		}

		boolean favorite = ServerMetadataStore.isFavorite(selected.ip);
		this.bslFavoriteButton.setMessage(Component.translatable(favorite ? "bsl.button.unfavorite" : "bsl.button.favorite"));
		this.bslCategoryButton.setMessage(Component.translatable("bsl.button.category"));
		this.bslFindServersButton.active = true;
		this.bsl$updateCategoryFilterButton();
	}

	@Unique
	private String bsl$getSearchQuery() {
		if (this.bslSearchBox == null) {
			return "";
		}
		return this.bslSearchBox.getValue().trim().toLowerCase(Locale.ROOT);
	}

	@Unique
	private boolean bsl$matchesSearch(ServerData serverData, String query) {
		if (serverData == null) {
			return false;
		}
		if (query.isBlank()) {
			return true;
		}

		String category = ServerMetadataStore.getCategory(serverData.ip);
		boolean favorite = ServerMetadataStore.isFavorite(serverData.ip);
		String haystack = (
			(serverData.name == null ? "" : serverData.name) + " " +
			(serverData.ip == null ? "" : serverData.ip) + " " +
			category + " " +
			(favorite ? "favorite fav starred" : "")
		).toLowerCase(Locale.ROOT);

		String[] terms = query.split("\\s+");
		for (String term : terms) {
			if (!term.isEmpty() && !haystack.contains(term)) {
				return false;
			}
		}
		return true;
	}

	@Unique
	private boolean bsl$matchesCategory(ServerData serverData, String categoryFilter) {
		if (serverData == null) {
			return false;
		}
		if (categoryFilter == null || categoryFilter.isBlank()) {
			return true;
		}
		return categoryFilter.equalsIgnoreCase(ServerMetadataStore.getCategory(serverData.ip));
	}

	@Unique
	private void bsl$toggleCategoryDropdown() {
		this.bsl$closeCategoryDropdown();
		this.minecraft.setScreen(
			new CategoryFilterSidebarScreen((JoinMultiplayerScreen) (Object) this, this.bslActiveCategoryFilter, category -> {
				this.bslActiveCategoryFilter = category == null ? "" : category;
				this.bslPendingFilterApply = true;
			})
		);
	}

	@Unique
	private void bsl$updateCategoryFilterButton() {
		if (this.bslCategoryFilterButton == null) {
			return;
		}
		String label = this.bslActiveCategoryFilter.isBlank() 
			? Component.translatable("bsl.category.all").getString() 
			: this.bsl$abbreviate(this.bslActiveCategoryFilter, 8);
		this.bslCategoryFilterButton.setMessage(Component.translatable("bsl.button.category_filter", label));
	}

	@Unique
	private void bsl$openCategoryDropdown() {
		this.bsl$closeCategoryDropdown();

		List<String> options = new ArrayList<>();
		options.add("");
		options.addAll(ServerMetadataStore.getKnownCategories());

		int y = Math.max(8, this.bslCategoryFilterY - options.size() * 20);
		for (String option : options) {
			String label = option.isBlank() 
				? Component.translatable("bsl.category.all").getString() 
				: this.bsl$abbreviate(option, 16);
			Button optionButton = this.addRenderableWidget(
				Button.builder(Component.literal(label), button -> this.bsl$setCategoryFilter(option))
					.bounds(this.bslCategoryFilterX, y, this.bslCategoryFilterWidth, 20)
					.build()
			);
			this.bslCategoryDropdownButtons.add(optionButton);
			y += 20;
		}

		this.bslCategoryDropdownOpen = true;
	}

	@Unique
	private void bsl$closeCategoryDropdown() {
		if (this.bslCategoryDropdownButtons.isEmpty()) {
			this.bslCategoryDropdownOpen = false;
			return;
		}

		for (Button button : this.bslCategoryDropdownButtons) {
			this.removeWidget(button);
		}
		this.bslCategoryDropdownButtons.clear();
		this.bslCategoryDropdownOpen = false;
	}

	@Unique
	private void bsl$setCategoryFilter(String category) {
		this.bslActiveCategoryFilter = category == null ? "" : category;
		this.bsl$closeCategoryDropdown();
		this.bsl$applyServerView(null);
	}

	@Unique
	private void bsl$layoutWidgets() {
		if (this.bslSearchBox == null || this.bslFavoriteButton == null || this.bslCategoryButton == null || this.bslFindServersButton == null || this.bslCategoryFilterButton == null) {
			return;
		}

		int margin = 8;
		int availableWidth = Math.max(220, this.width - margin * 2);
		int y = BSL_TOP_Y;
		int favoriteWidth = 84;
		int categoryWidth = 104;
		int findWidth = 84;
		int filterWidth = 104;
		int buttonsTotalWidth = favoriteWidth + categoryWidth + findWidth + filterWidth + BSL_CONTROL_GAP * 3;
		int minOneRowSearch = 180;

		if (availableWidth >= buttonsTotalWidth + BSL_CONTROL_GAP + minOneRowSearch) {
			int searchWidth = availableWidth - buttonsTotalWidth - BSL_CONTROL_GAP;
			int buttonX = margin + searchWidth + BSL_CONTROL_GAP;

			this.bsl$setBounds(this.bslSearchBox, margin, y, searchWidth, BSL_CONTROL_HEIGHT);
			this.bsl$setBounds(this.bslFavoriteButton, buttonX, y, favoriteWidth, BSL_CONTROL_HEIGHT);
			this.bsl$setBounds(this.bslCategoryButton, buttonX + favoriteWidth + BSL_CONTROL_GAP, y, categoryWidth, BSL_CONTROL_HEIGHT);
			this.bsl$setBounds(this.bslFindServersButton, buttonX + favoriteWidth + categoryWidth + BSL_CONTROL_GAP * 2, y, findWidth, BSL_CONTROL_HEIGHT);
			this.bsl$setBounds(this.bslCategoryFilterButton, buttonX + favoriteWidth + categoryWidth + findWidth + BSL_CONTROL_GAP * 3, y, filterWidth, BSL_CONTROL_HEIGHT);
		} else if (availableWidth >= 320) {
			int secondRowY = y + BSL_CONTROL_HEIGHT + BSL_CONTROL_GAP;
			int compactButtonWidth = Math.max(72, (availableWidth - BSL_CONTROL_GAP * 3) / 4);

			this.bsl$setBounds(this.bslSearchBox, margin, y, availableWidth, BSL_CONTROL_HEIGHT);
			this.bsl$setBounds(this.bslFavoriteButton, margin, secondRowY, compactButtonWidth, BSL_CONTROL_HEIGHT);
			this.bsl$setBounds(this.bslCategoryButton, margin + compactButtonWidth + BSL_CONTROL_GAP, secondRowY, compactButtonWidth, BSL_CONTROL_HEIGHT);
			this.bsl$setBounds(this.bslFindServersButton, margin + compactButtonWidth * 2 + BSL_CONTROL_GAP * 2, secondRowY, compactButtonWidth, BSL_CONTROL_HEIGHT);
			this.bsl$setBounds(this.bslCategoryFilterButton, margin + compactButtonWidth * 3 + BSL_CONTROL_GAP * 3, secondRowY, compactButtonWidth, BSL_CONTROL_HEIGHT);
		} else {
			int secondRowY = y + BSL_CONTROL_HEIGHT + BSL_CONTROL_GAP;
			int thirdRowY = secondRowY + BSL_CONTROL_HEIGHT + BSL_CONTROL_GAP;
			int twoColWidth = Math.max(104, (availableWidth - BSL_CONTROL_GAP) / 2);

			this.bsl$setBounds(this.bslSearchBox, margin, y, availableWidth, BSL_CONTROL_HEIGHT);
			this.bsl$setBounds(this.bslFavoriteButton, margin, secondRowY, twoColWidth, BSL_CONTROL_HEIGHT);
			this.bsl$setBounds(this.bslCategoryButton, margin + twoColWidth + BSL_CONTROL_GAP, secondRowY, twoColWidth, BSL_CONTROL_HEIGHT);
			this.bsl$setBounds(this.bslFindServersButton, margin, thirdRowY, twoColWidth, BSL_CONTROL_HEIGHT);
			this.bsl$setBounds(this.bslCategoryFilterButton, margin + twoColWidth + BSL_CONTROL_GAP, thirdRowY, twoColWidth, BSL_CONTROL_HEIGHT);
		}

		this.bslCategoryFilterX = this.bslCategoryFilterButton.getX();
		this.bslCategoryFilterY = this.bslCategoryFilterButton.getY() + BSL_CONTROL_HEIGHT;
		this.bslCategoryFilterWidth = this.bslCategoryFilterButton.getWidth();
		this.bslLastLayoutWidth = this.width;
		this.bslLastLayoutHeight = this.height;
		this.bsl$closeCategoryDropdown();
		this.bsl$layoutServerListArea();
	}

	@Unique
	private void bsl$setBounds(AbstractWidget widget, int x, int y, int width, int height) {
		widget.setSize(width, height);
		widget.setX(x);
		widget.setY(y);
	}

	@Unique
	private void bsl$layoutServerListArea() {
		if (this.serverSelectionList == null) {
			return;
		}

		int controlsBottom = Math.max(
			Math.max(this.bslSearchBox.getY() + this.bslSearchBox.getHeight(), this.bslFavoriteButton.getY() + this.bslFavoriteButton.getHeight()),
			Math.max(
				this.bslCategoryButton.getY() + this.bslCategoryButton.getHeight(),
				Math.max(this.bslFindServersButton.getY() + this.bslFindServersButton.getHeight(), this.bslCategoryFilterButton.getY() + this.bslCategoryFilterButton.getHeight())
			)
		);

		int top = Math.max(48, controlsBottom + BSL_LIST_TOP_GAP);
		int contentHeight = Math.max(80, this.height - BSL_LIST_BOTTOM_PADDING - top);
		this.serverSelectionList.updateSizeAndPosition(this.width, contentHeight, top);
	}

	@Unique
	private void bsl$removeVanillaTitleFromHeader() {
		if (this.layout == null) {
			return;
		}

		FrameLayout headerFrame = ((HeaderAndFooterLayoutAccessor) (Object) this.layout).bsl$getHeaderFrame();
		List<?> children = ((FrameLayoutAccessor) (Object) headerFrame).bsl$getChildren();
		children.removeIf(container -> {
			try {
				Object child = container.getClass().getField("child").get(container);
				return child instanceof StringWidget;
			} catch (Exception ignored) {
				return false;
			}
		});
	}

	@Unique
	private String bsl$abbreviate(String value, int maxLength) {
		if (value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength - 3) + "...";
	}

	@Unique
	private ServerData bsl$getSelectedServerData() {
		ServerSelectionList.Entry selectedEntry = this.serverSelectionList.getSelected();
		if (selectedEntry instanceof ServerSelectionList.OnlineServerEntry onlineServerEntry) {
			return onlineServerEntry.getServerData();
		}
		return null;
	}
}

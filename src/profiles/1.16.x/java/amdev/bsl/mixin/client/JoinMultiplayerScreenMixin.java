package amdev.bsl.mixin.client;

import amdev.bsl.client.ServerMetadataStore;
import amdev.bsl.client.ServerOrdering;
import amdev.bsl.client.gui.CategoryFilterSidebarScreen;
import amdev.bsl.client.gui.CategoryEditScreen;
import amdev.bsl.client.gui.ServerBrowserScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
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
	private final List<Button> bslCategoryDropdownButtons = new ArrayList<>();

	@Unique
	private String bslActiveCategoryFilter = "";

	@Unique
	private boolean bslCategoryDropdownOpen;

	@Unique
	private int bslCategoryFilterX;

	@Unique
	private int bslCategoryFilterY;

	@Unique
	private int bslCategoryFilterWidth = 120;

	@Unique
	private static final int BSL_CONTROL_HEIGHT = 20;

	@Unique
	private static final int BSL_CONTROL_GAP = 4;

	@Unique
	private static final int BSL_TOP_Y = 36;

	@Unique
	private static final int BSL_MARGIN = 8;

	@Unique
	private static final int BSL_LIST_TOP_GAP = 8;

	@Unique
	private static final int BSL_LIST_BOTTOM_PADDING = 64;

	@Inject(method = "init", at = @At("TAIL"))
	private void bsl$addCategoryButtons(CallbackInfo ci) {
		int favoriteWidth = 84;
		int categoryWidth = 104;
		int findWidth = 84;
		int filterWidth = 104;
		int buttonsTotalWidth = favoriteWidth + categoryWidth + findWidth + filterWidth + BSL_CONTROL_GAP * 3;
		int availableWidth = Math.max(240, this.width - BSL_MARGIN * 2);
		int searchWidth = Math.max(140, availableWidth - buttonsTotalWidth - BSL_CONTROL_GAP);
		int buttonX = BSL_MARGIN + searchWidth + BSL_CONTROL_GAP;

		this.bslSearchBox = this.addButton(new EditBox(this.minecraft.font, BSL_MARGIN, BSL_TOP_Y, searchWidth, BSL_CONTROL_HEIGHT, new TextComponent("Search")));
		this.bslSearchBox.setSuggestion("Search servers");
		this.bslSearchBox.setResponder(value -> this.bsl$applyServerView(null));

		this.bslFavoriteButton = this.addButton(new Button(buttonX, BSL_TOP_Y, favoriteWidth, BSL_CONTROL_HEIGHT, new TextComponent("Fav"), button -> this.bsl$toggleFavorite()));
		this.bslCategoryButton = this.addButton(new Button(buttonX + favoriteWidth + BSL_CONTROL_GAP, BSL_TOP_Y, categoryWidth, BSL_CONTROL_HEIGHT, new TextComponent("Category"), button -> this.bsl$openCategoryEditor()));
		this.bslFindServersButton = this.addButton(new Button(buttonX + favoriteWidth + categoryWidth + BSL_CONTROL_GAP * 2, BSL_TOP_Y, findWidth, BSL_CONTROL_HEIGHT, new TextComponent("Find"), button -> this.bsl$openServerBrowser()));
		this.bslCategoryFilterButton = this.addButton(new Button(buttonX + favoriteWidth + categoryWidth + findWidth + BSL_CONTROL_GAP * 3, BSL_TOP_Y, filterWidth, BSL_CONTROL_HEIGHT, new TextComponent("Show: All v"), button -> this.bsl$toggleCategoryDropdown()));

		this.bslCategoryFilterX = this.bslCategoryFilterButton.x;
		this.bslCategoryFilterY = this.bslCategoryFilterButton.y + BSL_CONTROL_HEIGHT;
		this.bslCategoryFilterWidth = this.bslCategoryFilterButton.getWidth();

		this.bsl$layoutServerListArea();
		this.bsl$applyServerView(null);
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
			if (!this.bslActiveCategoryFilter.isEmpty() && !ServerMetadataStore.getKnownCategories().contains(this.bslActiveCategoryFilter)) {
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
		if (serverAddress == null) {
			return;
		}

		for (ServerSelectionList.Entry entry : this.serverSelectionList.children()) {
			if (!(entry instanceof ServerSelectionList.OnlineServerEntry)) {
				continue;
			}
			ServerData data = ((ServerSelectionList.OnlineServerEntry) entry).getServerData();
			if (data != null && serverAddress.equalsIgnoreCase(data.ip)) {
				this.serverSelectionList.setSelected(entry);
				break;
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
		this.bslFindServersButton.active = true;

		if (!hasSelection) {
			this.bslFavoriteButton.setMessage(new TextComponent("Fav"));
			this.bslCategoryButton.setMessage(new TextComponent("Category"));
			this.bsl$updateCategoryFilterButton();
			return;
		}

		boolean favorite = ServerMetadataStore.isFavorite(selected.ip);
		this.bslFavoriteButton.setMessage(new TextComponent(favorite ? "Unfav" : "Fav"));
		this.bslCategoryButton.setMessage(new TextComponent("Category"));
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
		if (query.isEmpty()) {
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
		if (categoryFilter == null || categoryFilter.isEmpty()) {
			return true;
		}
		return categoryFilter.equalsIgnoreCase(ServerMetadataStore.getCategory(serverData.ip));
	}

	@Unique
	private void bsl$toggleCategoryDropdown() {
		this.bsl$closeCategoryDropdown();
		this.minecraft.setScreen(new CategoryFilterSidebarScreen((JoinMultiplayerScreen) (Object) this, this.bslActiveCategoryFilter, category -> {
			this.bslActiveCategoryFilter = category == null ? "" : category;
			
		}));
	}

	@Unique
	private void bsl$updateCategoryFilterButton() {
		if (this.bslCategoryFilterButton == null) {
			return;
		}
		String label = this.bslActiveCategoryFilter.isEmpty() ? "All" : this.bsl$abbreviate(this.bslActiveCategoryFilter, 8);
		this.bslCategoryFilterButton.setMessage(new TextComponent("Show: " + label + " v"));
	}

	@Unique
	private void bsl$cycleCategoryFilter() {
		List<String> options = new ArrayList<>();
		options.add("");
		options.addAll(ServerMetadataStore.getKnownCategories());

		if (options.isEmpty()) {
			return;
		}

		int currentIndex = 0;
		for (int i = 0; i < options.size(); i++) {
			if (options.get(i).equalsIgnoreCase(this.bslActiveCategoryFilter)) {
				currentIndex = i;
				break;
			}
		}

		int nextIndex = (currentIndex + 1) % options.size();
		this.bslActiveCategoryFilter = options.get(nextIndex);
		
	}

	@Unique
	private void bsl$openCategoryDropdown() {
		this.bsl$closeCategoryDropdown();

		List<String> options = new ArrayList<>();
		options.add("");
		options.addAll(ServerMetadataStore.getKnownCategories());

		int y = Math.max(8, this.bslCategoryFilterY - options.size() * 20);
		for (String option : options) {
			String label = option.isEmpty() ? "All" : this.bsl$abbreviate(option, 16);
			Button optionButton = this.addButton(new Button(this.bslCategoryFilterX, y, this.bslCategoryFilterWidth, 20, new TextComponent(label), button -> this.bsl$setCategoryFilter(option)));
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
			this.buttons.remove(button);
			this.children.remove(button);
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
	private void bsl$layoutServerListArea() {
		if (this.serverSelectionList == null) {
			return;
		}

		int controlsBottom = this.bslCategoryFilterButton == null
			? BSL_TOP_Y + BSL_CONTROL_HEIGHT
			: this.bslCategoryFilterButton.y + this.bslCategoryFilterButton.getHeight();
		int top = Math.max(48, controlsBottom + BSL_LIST_TOP_GAP);
		this.serverSelectionList.updateSize(this.width, this.height, top, this.height - BSL_LIST_BOTTOM_PADDING);
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
		if (selectedEntry instanceof ServerSelectionList.OnlineServerEntry) {
			return ((ServerSelectionList.OnlineServerEntry) selectedEntry).getServerData();
		}
		return null;
	}
}

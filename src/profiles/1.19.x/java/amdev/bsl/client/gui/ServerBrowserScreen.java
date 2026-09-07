package amdev.bsl.client.gui;

import amdev.bsl.client.PublicServerCatalog;
import amdev.bsl.client.ServerMetadataStore;
import amdev.bsl.client.ServerOrdering;
import amdev.bsl.mixin.client.JoinMultiplayerScreenInvoker;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ServerBrowserScreen extends Screen {
	private static final int ROW_HEIGHT = 36;

	private final JoinMultiplayerScreen parent;
	private final List<PublicServerCatalog.Entry> allEntries = new ArrayList<>();
	private final List<PublicServerCatalog.Entry> filteredEntries = new ArrayList<>();
	private final List<Button> resultButtons = new ArrayList<>();
	private EditBox searchField;
	private Button previousPageButton;
	private Button nextPageButton;
	private Button addButton;
	private Button openWebsiteButton;
	private Button refreshApiButton;
	private PublicServerCatalog.Entry selected;
	private int currentPage;
	private int pageSize = 6;
	private Component statusMessage = null;
	private int buttonX;
	private int buttonWidth;
	private int listTop;
	private ItemStack loadingIcon;
	private boolean loadingLive;
	private CompletableFuture<PublicServerCatalog.LoadResult> liveLoadFuture;

	public ServerBrowserScreen(JoinMultiplayerScreen parent) {
		super(Component.translatable("bsl.browser.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.bsl$cancelLiveLoad();
		this.allEntries.clear();
		this.filteredEntries.clear();
		this.selected = null;
		this.currentPage = 0;
		this.loadingIcon = ItemStack.EMPTY;

		this.listTop = 44;
		this.buttonWidth = Math.min(this.width - 20, Math.max(575, this.width - 40));
		this.buttonX = (this.width - this.buttonWidth) / 2;

		int availableHeight = this.height - this.listTop - 56;
		this.pageSize = Math.max(1, availableHeight / ROW_HEIGHT);

		this.searchField = this.addRenderableWidget(new EditBox(this.font, this.buttonX, 16, this.buttonWidth, 20, Component.translatable("bsl.browser.search_field")));
		this.searchField.setSuggestion(Component.translatable("bsl.browser.search_hint").getString());
		this.searchField.setFocused(true);
		this.searchField.setResponder(value -> {
			this.currentPage = 0;
			this.bsl$refreshFilteredEntries();
		});

		this.resultButtons.clear();
		for (int i = 0; i < this.pageSize; i++) {
			final int slot = i;
			Button button = this.addRenderableWidget(
				this.bsl$makeButton(
					this.buttonX,
					this.listTop + i * ROW_HEIGHT,
					this.buttonWidth,
					ROW_HEIGHT - 2,
					Component.empty(),
					b -> this.bsl$selectFromSlot(slot)
				)
			);
			this.resultButtons.add(button);
		}

		int controlsY = this.height - 30;
		int prevX = this.buttonX;
		int nextX = prevX + 80;
		int addX = nextX + 80;
		int websiteX = addX + 115;
		int reloadX = websiteX + 115;
		int doneX = this.buttonX + this.buttonWidth - 75;

		this.previousPageButton = this.addRenderableWidget(this.bsl$makeButton(prevX, controlsY, 75, 20, Component.translatable("bsl.button.prev"), b -> this.bsl$changePage(-1)));
		this.nextPageButton = this.addRenderableWidget(this.bsl$makeButton(nextX, controlsY, 75, 20, Component.translatable("bsl.button.next"), b -> this.bsl$changePage(1)));
		this.addButton = this.addRenderableWidget(this.bsl$makeButton(addX, controlsY, 110, 20, Component.translatable("bsl.browser.add_selected"), b -> this.bsl$addSelectedServer()));
		this.openWebsiteButton = this.addRenderableWidget(this.bsl$makeButton(websiteX, controlsY, 110, 20, Component.translatable("bsl.browser.open_website"), b -> this.bsl$openSelectedWebsite()));
		this.refreshApiButton = this.addRenderableWidget(this.bsl$makeButton(reloadX, controlsY, 100, 20, Component.translatable("bsl.browser.reload_api"), b -> this.bsl$loadLiveCatalog(true)));
		this.addRenderableWidget(this.bsl$makeButton(doneX, controlsY, 75, 20, Component.translatable("gui.done"), b -> this.minecraft.setScreen(this.parent)));

		this.loadingLive = true;
		this.statusMessage = Component.translatable("bsl.browser.status.loading_live");
		this.bsl$refreshFilteredEntries();
		this.bsl$loadLiveCatalog(false);
	}

	@Override
	public void onClose() {
		this.bsl$cancelLiveLoad();
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		this.fill(poseStack, 0, 0, this.width, this.height, 0xB0101010);
		super.render(poseStack, mouseX, mouseY, partialTick);

		drawCenteredString(poseStack, this.font, this.title, this.width / 2, 4, 0xFFFFFFFF);
		
		int infoY = this.height - 42;
		Component resultsComp = Component.translatable("bsl.browser.results", this.filteredEntries.size());
		drawString(poseStack, this.font, resultsComp, this.buttonX, infoY, 0xFFA0A0A0);
		if (this.statusMessage != null) {
			int statusX = this.buttonX + this.font.width(resultsComp) + 12;
			drawString(poseStack, this.font, this.statusMessage, statusX, infoY, 0xFF80FF80);
		}

		if (this.loadingLive) {
			this.bsl$renderLoadingIndicator(poseStack);
		} else {
			this.bsl$renderRows(poseStack);
		}
	}

	private void bsl$renderRows(PoseStack poseStack) {
		int pageStart = this.currentPage * this.pageSize;
		for (int i = 0; i < this.pageSize; i++) {
			int index = pageStart + i;
			if (index < 0 || index >= this.filteredEntries.size()) {
				continue;
			}

			PublicServerCatalog.Entry entry = this.filteredEntries.get(index);
			int rowX = this.buttonX;
			int rowY = this.listTop + i * ROW_HEIGHT;

			if (entry.equals(this.selected)) {
				this.fill(poseStack, rowX - 1, rowY - 1, rowX + this.buttonWidth + 1, rowY + ROW_HEIGHT - 1, 0x40FFD67A);
				this.fill(poseStack, rowX - 1, rowY - 1, rowX + this.buttonWidth + 1, rowY, 0xFFFFD67A);
				this.fill(poseStack, rowX - 1, rowY + ROW_HEIGHT - 2, rowX + this.buttonWidth + 1, rowY + ROW_HEIGHT - 1, 0xFFFFD67A);
			}

			this.bsl$drawLogo(poseStack, rowX + 6, rowY + 8);
			drawString(poseStack, this.font, this.bsl$abbreviate(entry.name(), 38), rowX + 28, rowY + 6, 0xFFFFFFFF);
			drawString(poseStack, this.font, this.bsl$abbreviate(entry.address(), 45), rowX + 28, rowY + 18, 0xFF9FA7B0);

			Component playersText = this.bsl$playersComponent(entry);
			int playersColor = entry.players() > 0 ? 0xFF9DE27A : 0xFFA0A0A0;
			drawString(poseStack, this.font, playersText, rowX + this.buttonWidth - 120, rowY + 12, playersColor);
		}
	}

	private void bsl$renderLoadingIndicator(PoseStack poseStack) {
		float wave = (float) Math.sin((Util.getMillis() % 1600L) / 1600.0 * Math.PI * 2.0);
		float pulse = (wave + 1.0f) * 0.5f;
		int iconX = this.width / 2 - 8;
		int iconY = this.listTop + this.pageSize * ROW_HEIGHT / 2 - 8;

		ItemStack icon = this.bsl$getLoadingIcon();
		if (!icon.isEmpty()) {
			this.itemRenderer.renderAndDecorateItem(poseStack, icon, iconX, iconY);
		} else {
			this.bsl$renderFallbackIcon(poseStack, iconX, iconY, true);
		}

		int darkAlpha = ((int) ((1.0f - pulse) * 120.0f)) << 24;
		int lightAlpha = ((int) (pulse * 70.0f)) << 24;
		this.fill(poseStack, iconX, iconY, iconX + 16, iconY + 16, darkAlpha);
		this.fill(poseStack, iconX, iconY, iconX + 16, iconY + 16, lightAlpha | 0x00FFFFFF);

		int textAlpha = ((int) (140 + pulse * 115.0f)) << 24;
		drawCenteredString(poseStack, this.font, Component.translatable("bsl.browser.loading"), this.width / 2, iconY + 24, textAlpha | 0xD8C8FF);
	}

	private void bsl$drawLogo(PoseStack poseStack, int x, int y) {
		ItemStack icon = this.bsl$getLoadingIcon();
		if (!icon.isEmpty()) {
			this.itemRenderer.renderAndDecorateItem(poseStack, icon, x, y);
		} else {
			this.bsl$renderFallbackIcon(poseStack, x, y, false);
		}
	}

	private ItemStack bsl$getLoadingIcon() {
		if (this.loadingIcon != null && !this.loadingIcon.isEmpty()) {
			return this.loadingIcon;
		}
		try {
			this.loadingIcon = new ItemStack(Items.AMETHYST_SHARD);
			return this.loadingIcon;
		} catch (Exception ignored) {
			return ItemStack.EMPTY;
		}
	}

	private Button bsl$makeButton(int x, int y, int width, int height, Component label, Button.OnPress onPress) {
		return Button.builder(label, onPress).bounds(x, y, width, height).build();
	}

	private void bsl$renderFallbackIcon(PoseStack poseStack, int x, int y, boolean loading) {
		int fillColor = loading ? 0xFF6C5D92 : 0xFF4B4F57;
		int borderColor = loading ? 0xFFD8C8FF : 0xFF9AA0AA;
		this.fill(poseStack, x, y, x + 16, y + 16, fillColor);
		this.fill(poseStack, x, y, x + 16, y + 1, borderColor);
		this.fill(poseStack, x, y + 15, x + 16, y + 16, borderColor);
		this.fill(poseStack, x, y + 1, y + 16, borderColor);
		this.fill(poseStack, x + 15, y, x + 16, y + 16, borderColor);
	}

	private void bsl$refreshFilteredEntries() {
		String query = this.searchField == null ? "" : this.searchField.getValue().trim().toLowerCase(Locale.ROOT);

		this.filteredEntries.clear();
		for (PublicServerCatalog.Entry entry : this.allEntries) {
			if (this.bsl$matchesQuery(entry, query)) {
				this.filteredEntries.add(entry);
			}
		}

		int maxPage = Math.max(0, (this.filteredEntries.size() - 1) / this.pageSize);
		if (this.currentPage > maxPage) {
			this.currentPage = maxPage;
		}
		if (this.selected != null && !this.filteredEntries.contains(this.selected)) {
			this.selected = null;
		}

		this.bsl$refreshResultButtons();
		this.bsl$refreshActionButtons();
	}

	private void bsl$loadLiveCatalog(boolean forceRefresh) {
		this.bsl$cancelLiveLoad();

		if (!forceRefresh) {
			PublicServerCatalog.LoadResult cached = PublicServerCatalog.getSessionCachedResult();
			if (cached != null) {
				this.loadingLive = false;
				this.statusMessage = cached.fromLiveApi()
					? Component.translatable("bsl.browser.status.cache", cached.entries().size())
					: Component.translatable("bsl.browser.status.bundled_cached");
				this.allEntries.clear();
				this.allEntries.addAll(cached.entries());
				this.filteredEntries.clear();
				this.selected = null;
				this.currentPage = 0;
				this.bsl$refreshFilteredEntries();
				return;
			}
		}

		if (this.refreshApiButton != null) {
			this.refreshApiButton.active = false;
		}
		this.loadingLive = true;
		this.statusMessage = forceRefresh 
			? Component.translatable("bsl.browser.status.reloading_live") 
			: Component.translatable("bsl.browser.status.loading_live");
		this.allEntries.clear();
		this.filteredEntries.clear();
		this.selected = null;
		this.currentPage = 0;
		this.bsl$refreshFilteredEntries();

		this.liveLoadFuture = forceRefresh
			? PublicServerCatalog.reloadPreferredAsync(this.minecraft.getResourceManager())
			: PublicServerCatalog.loadPreferredAsync(this.minecraft.getResourceManager());
		this.liveLoadFuture.whenComplete((result, throwable) -> this.minecraft.execute(() -> {
			if (this.minecraft == null || this.minecraft.screen != this) {
				return;
			}

			this.loadingLive = false;
			if (this.refreshApiButton != null) {
				this.refreshApiButton.active = true;
			}

			PublicServerCatalog.LoadResult safeResult;
			if (throwable != null || result == null || result.entries() == null) {
				safeResult = new PublicServerCatalog.LoadResult(PublicServerCatalog.loadBundled(this.minecraft.getResourceManager()), false);
				this.statusMessage = Component.translatable("bsl.browser.status.api_fallback");
			} else {
				safeResult = result;
				if (safeResult.fromLiveApi()) {
					this.statusMessage = Component.translatable("bsl.browser.status.live_loaded", safeResult.entries().size());
				} else {
					this.statusMessage = Component.translatable("bsl.browser.status.api_fallback");
				}
			}

			this.allEntries.clear();
			this.allEntries.addAll(safeResult.entries());
			this.currentPage = 0;
			this.selected = null;
			this.bsl$refreshFilteredEntries();
		}));
	}

	private void bsl$cancelLiveLoad() {
		if (this.liveLoadFuture != null) {
			this.liveLoadFuture.cancel(true);
			this.liveLoadFuture = null;
		}
	}

	private void bsl$refreshResultButtons() {
		if (this.loadingLive) {
			for (Button button : this.resultButtons) {
				button.visible = false;
				button.active = false;
			}
			return;
		}

		int pageStart = this.currentPage * this.pageSize;
		for (int i = 0; i < this.resultButtons.size(); i++) {
			Button button = this.resultButtons.get(i);
			int index = pageStart + i;
			if (index < this.filteredEntries.size()) {
				button.visible = true;
				button.active = true;
				button.setMessage(Component.empty());
			} else {
				button.visible = false;
				button.active = false;
			}
		}
	}

	private void bsl$refreshActionButtons() {
		if (this.loadingLive) {
			this.previousPageButton.active = false;
			this.nextPageButton.active = false;
			this.addButton.active = false;
			this.openWebsiteButton.active = false;
			return;
		}

		int maxPage = Math.max(0, (this.filteredEntries.size() - 1) / this.pageSize);
		this.previousPageButton.active = this.currentPage > 0;
		this.nextPageButton.active = this.currentPage < maxPage;

		if (this.selected == null) {
			this.addButton.active = false;
			this.openWebsiteButton.active = false;
			return;
		}

		this.addButton.active = true;
		this.openWebsiteButton.active = !this.selected.website().isEmpty();
	}

	private void bsl$selectFromSlot(int slot) {
		if (this.loadingLive) {
			return;
		}

		int index = this.currentPage * this.pageSize + slot;
		if (index < 0 || index >= this.filteredEntries.size()) {
			return;
		}
		this.selected = this.filteredEntries.get(index);
		this.statusMessage = null;
		this.bsl$refreshActionButtons();
	}

	private void bsl$changePage(int delta) {
		if (this.loadingLive) {
			return;
		}

		int maxPage = Math.max(0, (this.filteredEntries.size() - 1) / this.pageSize);
		this.currentPage = Math.max(0, Math.min(maxPage, this.currentPage + delta));
		this.bsl$refreshResultButtons();
		this.bsl$refreshActionButtons();
	}

	private void bsl$addSelectedServer() {
		if (this.selected == null) {
			return;
		}

		ServerList serverList = this.parent.getServers();
		if (this.bsl$containsServer(serverList, this.selected.address())) {
			this.statusMessage = Component.translatable("bsl.browser.status.already_exists");
			return;
		}

		ServerData serverData = new ServerData(this.selected.name(), this.selected.address(), false);
		serverList.add(serverData, false);
		if (ServerMetadataStore.getCategory(serverData.ip).isEmpty() && !this.selected.category().isEmpty()) {
			ServerMetadataStore.setCategory(serverData.ip, this.selected.category());
		}
		ServerOrdering.reorder(serverList);
		serverList.save();
		((JoinMultiplayerScreenInvoker) this.parent).bsl$refreshServerList();

		this.statusMessage = Component.translatable("bsl.browser.status.added", this.selected.name());
	}

	private void bsl$openSelectedWebsite() {
		if (this.selected == null || this.selected.website().isEmpty()) {
			return;
		}

		this.minecraft.setScreen(new ConfirmLinkScreen(open -> {
			if (open) {
				Util.getPlatform().openUri(this.selected.website());
			}
			this.minecraft.setScreen(this);
		}, this.selected.website(), true));
	}

	private boolean bsl$containsServer(ServerList serverList, String address) {
		if (serverList == null || address == null || address.trim().isEmpty()) {
			return false;
		}
		String normalized = address.trim().toLowerCase(Locale.ROOT);
		for (int i = 0; i < serverList.size(); i++) {
			ServerData existing = serverList.get(i);
			if (existing != null && existing.ip != null && normalized.equals(existing.ip.trim().toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	private boolean bsl$matchesQuery(PublicServerCatalog.Entry entry, String query) {
		if (query.isEmpty()) {
			return true;
		}

		String searchable = entry.searchableText().toLowerCase(Locale.ROOT);
		String[] terms = query.split("\\s+");
		for (String term : terms) {
			if (!term.isEmpty() && !searchable.contains(term)) {
				return false;
			}
		}
		return true;
	}

	private Component bsl$playersComponent(PublicServerCatalog.Entry entry) {
		if (entry.players() >= 0 && entry.maxPlayers() > 0) {
			return Component.translatable("bsl.browser.players", entry.players(), entry.maxPlayers());
		}
		if (entry.players() >= 0) {
			return Component.translatable("bsl.browser.players_single", entry.players());
		}
		return Component.translatable("bsl.browser.players_unknown");
	}

	private String bsl$abbreviate(String value, int maxLength) {
		if (value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength - 3) + "...";
	}
}

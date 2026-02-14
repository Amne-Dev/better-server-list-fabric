package amdev.bsl.client.gui;

import amdev.bsl.client.PublicServerCatalog;
import amdev.bsl.client.ServerMetadataStore;
import amdev.bsl.client.ServerOrdering;
import amdev.bsl.mixin.client.JoinMultiplayerScreenInvoker;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ServerBrowserScreen extends Screen {
	private static final int PAGE_SIZE = 8;
	private static final int ROW_HEIGHT = 36;
	private static final String MCSRSTAT_ICON_PREFIX = "https://api.mcsrvstat.us/icon/";
	private static final String MCSRSTAT_ICON_PREFIX_HTTP = "http://api.mcsrvstat.us/icon/";
	private static final String MINE_SPARK_STATUS_TEMPLATE = "https://srvstat.minespark.org/general/%s";
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(8))
		.build();

	private final JoinMultiplayerScreen parent;
	private final List<PublicServerCatalog.Entry> allEntries = new ArrayList<>();
	private final List<PublicServerCatalog.Entry> filteredEntries = new ArrayList<>();
	private final List<Button> resultButtons = new ArrayList<>();
	private final Map<String, Identifier> logoTextures = new HashMap<>();
	private final Set<String> logoLoading = new HashSet<>();
	private final List<Identifier> registeredTextures = new ArrayList<>();
	private EditBox searchField;
	private Button previousPageButton;
	private Button nextPageButton;
	private Button addButton;
	private Button openWebsiteButton;
	private Button refreshApiButton;
	private PublicServerCatalog.Entry selected;
	private int currentPage;
	private String statusMessage = "";
	private int buttonX;
	private int buttonWidth;
	private int listTop;
	private ItemStack loadingIcon;
	private long nextLoadingIconRetryAt;
	private boolean loadingLive;
	private CompletableFuture<PublicServerCatalog.LoadResult> liveLoadFuture;

	public ServerBrowserScreen(JoinMultiplayerScreen parent) {
		super(Component.literal("Find Servers"));
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
		this.nextLoadingIconRetryAt = 0L;

		this.listTop = 46;
		this.buttonWidth = Math.min(this.width - 20, Math.max(575, this.width - 40));
		this.buttonX = (this.width - this.buttonWidth) / 2;

		this.searchField = this.addRenderableWidget(
			new EditBox(this.font, this.buttonX, 18, this.buttonWidth, 20, Component.literal("Search public servers"))
		);
		this.searchField.setHint(Component.literal("Search name, address, category, tags"));
		this.searchField.setResponder(value -> {
			this.currentPage = 0;
			this.bsl$refreshFilteredEntries();
		});
		this.setInitialFocus(this.searchField);

		this.resultButtons.clear();
		for (int i = 0; i < PAGE_SIZE; i++) {
			final int slot = i;
			Button button = this.addRenderableWidget(
				Button.builder(Component.empty(), b -> this.bsl$selectFromSlot(slot)).bounds(
					this.buttonX,
					this.listTop + i * ROW_HEIGHT,
					this.buttonWidth,
					ROW_HEIGHT - 2
				).build()
			);
			this.resultButtons.add(button);
		}

		int controlsY = this.height - 54;
		int prevX = this.buttonX;
		int nextX = prevX + 80;
		int addX = nextX + 80;
		int websiteX = addX + 115;
		int reloadX = websiteX + 115;
		int doneX = this.buttonX + this.buttonWidth - 75;

		this.previousPageButton = this.addRenderableWidget(
			Button.builder(Component.literal("< Prev"), b -> this.bsl$changePage(-1)).bounds(prevX, controlsY, 75, 20).build()
		);
		this.nextPageButton = this.addRenderableWidget(
			Button.builder(Component.literal("Next >"), b -> this.bsl$changePage(1)).bounds(nextX, controlsY, 75, 20).build()
		);
		this.addButton = this.addRenderableWidget(
			Button.builder(Component.literal("Add Selected"), b -> this.bsl$addSelectedServer()).bounds(addX, controlsY, 110, 20).build()
		);
		this.openWebsiteButton = this.addRenderableWidget(
			Button.builder(Component.literal("Open Website"), b -> this.bsl$openSelectedWebsite()).bounds(websiteX, controlsY, 110, 20).build()
		);
		this.refreshApiButton = this.addRenderableWidget(
			Button.builder(Component.literal("Reload API"), b -> this.bsl$loadLiveCatalog(true)).bounds(reloadX, controlsY, 100, 20).build()
		);
		this.addRenderableWidget(
			Button.builder(Component.literal("Done"), b -> this.minecraft.setScreen(this.parent)).bounds(doneX, controlsY, 75, 20).build()
		);

		this.loadingLive = true;
		this.statusMessage = "Loading live server catalog...";
		this.bsl$refreshFilteredEntries();
		this.bsl$loadLiveCatalog(false);
	}

	@Override
	public void onClose() {
		this.bsl$cancelLiveLoad();
		this.bsl$releaseLogoTextures();
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		guiGraphics.fill(0, 0, this.width, this.height, 0xB0101010);
		super.render(guiGraphics, mouseX, mouseY, partialTick);

		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFFFF);
		guiGraphics.drawString(this.font, "Results: " + this.filteredEntries.size(), 10, this.height - 76, 0xFFA0A0A0);
		if (!this.statusMessage.isBlank()) {
			guiGraphics.drawString(this.font, this.statusMessage, 10, this.height - 66, 0xFF80FF80);
		}

		if (this.loadingLive) {
			this.bsl$renderLoadingIndicator(guiGraphics);
		} else {
			this.bsl$renderRows(guiGraphics);
		}
	}

	private void bsl$renderRows(GuiGraphics guiGraphics) {
		int pageStart = this.currentPage * PAGE_SIZE;
		for (int i = 0; i < PAGE_SIZE; i++) {
			int index = pageStart + i;
			if (index < 0 || index >= this.filteredEntries.size()) {
				continue;
			}

			PublicServerCatalog.Entry entry = this.filteredEntries.get(index);
			int rowX = this.buttonX;
			int rowY = this.listTop + i * ROW_HEIGHT;

			if (entry.equals(this.selected)) {
				guiGraphics.renderOutline(rowX - 1, rowY - 1, this.buttonWidth + 2, ROW_HEIGHT, 0xFFFFD67A);
			}

			this.bsl$ensureLogoTexture(entry);
			this.bsl$drawLogo(guiGraphics, entry, rowX + 6, rowY + 8);

			guiGraphics.drawString(this.font, this.bsl$abbreviate(entry.name(), 38), rowX + 28, rowY + 6, 0xFFFFFFFF, false);
			guiGraphics.drawString(this.font, this.bsl$abbreviate(entry.address(), 45), rowX + 28, rowY + 18, 0xFF9FA7B0, false);

			String playersText = this.bsl$playersText(entry);
			int playersColor = entry.players() > 0 ? 0xFF9DE27A : 0xFFA0A0A0;
			guiGraphics.drawString(this.font, playersText, rowX + this.buttonWidth - 120, rowY + 12, playersColor, false);
		}
	}

	private void bsl$renderLoadingIndicator(GuiGraphics guiGraphics) {
		float wave = (float) Math.sin((Util.getMillis() % 1600L) / 1600.0 * Math.PI * 2.0);
		float pulse = (wave + 1.0f) * 0.5f;
		int iconX = this.width / 2 - 8;
		int iconY = this.listTop + PAGE_SIZE * ROW_HEIGHT / 2 - 8;

		ItemStack icon = this.bsl$getLoadingIcon();
		if (!icon.isEmpty()) {
			guiGraphics.renderItem(icon, iconX, iconY);
		} else {
			this.bsl$renderFallbackIcon(guiGraphics, iconX, iconY, true);
		}
		int darkAlpha = ((int) ((1.0f - pulse) * 120.0f)) << 24;
		int lightAlpha = ((int) (pulse * 70.0f)) << 24;
		guiGraphics.fill(iconX, iconY, iconX + 16, iconY + 16, darkAlpha);
		guiGraphics.fill(iconX, iconY, iconX + 16, iconY + 16, lightAlpha | 0x00FFFFFF);

		int textAlpha = ((int) (140 + pulse * 115.0f)) << 24;
		guiGraphics.drawCenteredString(this.font, "Loading...", this.width / 2, iconY + 24, textAlpha | 0xD8C8FF);
	}

	private void bsl$drawLogo(GuiGraphics guiGraphics, PublicServerCatalog.Entry entry, int x, int y) {
		String key = this.bsl$logoKey(entry.address());
		Identifier identifier = this.logoTextures.get(key);
		if (identifier != null) {
			guiGraphics.blit(RenderPipelines.GUI_TEXTURED, identifier, x, y, 0.0f, 0.0f, 16, 16, 16, 16);
			return;
		}

		ItemStack icon = this.bsl$getLoadingIcon();
		if (!icon.isEmpty()) {
			guiGraphics.renderItem(icon, x, y);
		} else {
			this.bsl$renderFallbackIcon(guiGraphics, x, y, false);
		}
	}

	private ItemStack bsl$getLoadingIcon() {
		if (this.loadingIcon != null && !this.loadingIcon.isEmpty()) {
			return this.loadingIcon;
		}

		long now = Util.getMillis();
		if (now < this.nextLoadingIconRetryAt) {
			return ItemStack.EMPTY;
		}

		try {
			this.loadingIcon = new ItemStack(Items.AMETHYST_SHARD);
			return this.loadingIcon;
		} catch (Exception ignored) {
			this.nextLoadingIconRetryAt = now + 1000L;
			return ItemStack.EMPTY;
		}
	}

	private void bsl$renderFallbackIcon(GuiGraphics guiGraphics, int x, int y, boolean loading) {
		int fillColor = loading ? 0xFF6C5D92 : 0xFF4B4F57;
		int borderColor = loading ? 0xFFD8C8FF : 0xFF9AA0AA;
		guiGraphics.fill(x, y, x + 16, y + 16, fillColor);
		guiGraphics.renderOutline(x, y, 16, 16, borderColor);
	}

	private void bsl$refreshFilteredEntries() {
		String query = this.searchField == null ? "" : this.searchField.getValue().trim().toLowerCase(Locale.ROOT);

		this.filteredEntries.clear();
		for (PublicServerCatalog.Entry entry : this.allEntries) {
			if (this.bsl$matchesQuery(entry, query)) {
				this.filteredEntries.add(entry);
			}
		}

		int maxPage = Math.max(0, (this.filteredEntries.size() - 1) / PAGE_SIZE);
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
					? "Loaded " + cached.entries().size() + " servers from session cache."
					: "Using cached bundled catalog from this session.";
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
		this.statusMessage = forceRefresh ? "Reloading live server catalog..." : "Loading live server catalog...";
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
				this.statusMessage = "Live API unavailable. Showing bundled catalog.";
			} else {
				safeResult = result;
				if (safeResult.fromLiveApi()) {
					this.statusMessage = "Loaded " + safeResult.entries().size() + " servers from live API.";
				} else {
					this.statusMessage = "Live API unavailable. Showing bundled catalog.";
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

		int pageStart = this.currentPage * PAGE_SIZE;
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

		int maxPage = Math.max(0, (this.filteredEntries.size() - 1) / PAGE_SIZE);
		this.previousPageButton.active = this.currentPage > 0;
		this.nextPageButton.active = this.currentPage < maxPage;

		if (this.selected == null) {
			this.addButton.active = false;
			this.openWebsiteButton.active = false;
			return;
		}

		this.addButton.active = true;
		this.openWebsiteButton.active = !this.selected.website().isBlank();
	}

	private void bsl$selectFromSlot(int slot) {
		if (this.loadingLive) {
			return;
		}

		int index = this.currentPage * PAGE_SIZE + slot;
		if (index < 0 || index >= this.filteredEntries.size()) {
			return;
		}
		this.selected = this.filteredEntries.get(index);
		this.statusMessage = "";
		this.bsl$refreshActionButtons();
	}

	private void bsl$changePage(int delta) {
		if (this.loadingLive) {
			return;
		}

		int maxPage = Math.max(0, (this.filteredEntries.size() - 1) / PAGE_SIZE);
		this.currentPage = Math.max(0, Math.min(maxPage, this.currentPage + delta));
		this.bsl$refreshResultButtons();
		this.bsl$refreshActionButtons();
	}

	private void bsl$addSelectedServer() {
		if (this.selected == null) {
			return;
		}

		ServerList serverList = this.parent.getServers();
		if (serverList.get(this.selected.address()) != null) {
			this.statusMessage = "Server already exists in your list.";
			return;
		}

		ServerData serverData = new ServerData(this.selected.name(), this.selected.address(), ServerData.Type.OTHER);
		serverList.add(serverData, false);
		if (ServerMetadataStore.getCategory(serverData.ip).isBlank() && !this.selected.category().isBlank()) {
			ServerMetadataStore.setCategory(serverData.ip, this.selected.category());
		}
		ServerOrdering.reorder(serverList);
		serverList.save();
		((JoinMultiplayerScreenInvoker) this.parent).bsl$refreshServerList();

		this.statusMessage = "Added " + this.selected.name() + ".";
	}

	private void bsl$openSelectedWebsite() {
		if (this.selected == null || this.selected.website().isBlank()) {
			return;
		}
		ConfirmLinkScreen.confirmLinkNow(this, this.selected.website(), true);
	}

	private void bsl$ensureLogoTexture(PublicServerCatalog.Entry entry) {
		if (entry.logoUrl().isBlank()) {
			return;
		}

		String key = this.bsl$logoKey(entry.address());
		if (this.logoTextures.containsKey(key) || this.logoLoading.contains(key)) {
			return;
		}

		this.logoLoading.add(key);
		CompletableFuture
			.supplyAsync(() -> this.bsl$downloadLogo(entry.logoUrl()))
			.whenComplete((image, throwable) -> this.minecraft.execute(() -> {
				this.logoLoading.remove(key);
				if (throwable != null || image == null) {
					return;
				}

				try {
					Identifier textureId = Identifier.fromNamespaceAndPath("better-server-list", "server_logo/" + Integer.toUnsignedString(key.hashCode()));
					DynamicTexture texture = new DynamicTexture(() -> "bsl_logo_" + key, image);
					texture.upload();
					this.minecraft.getTextureManager().register(textureId, texture);
					this.logoTextures.put(key, textureId);
					this.registeredTextures.add(textureId);
				} catch (Exception exception) {
					image.close();
				}
			}));
	}

	private NativeImage bsl$downloadLogo(String logoUrl) {
		try {
			NativeImage directDataImage = this.bsl$decodeDataImage(logoUrl);
			if (directDataImage != null) {
				return directDataImage;
			}

			String mcsAddress = this.bsl$extractMcsrvstatAddress(logoUrl);
			if (!mcsAddress.isEmpty()) {
				NativeImage fallbackStatusIcon = this.bsl$downloadMinesparkStatusIcon(mcsAddress);
				if (fallbackStatusIcon != null) {
					return fallbackStatusIcon;
				}
			}

			HttpRequest request = HttpRequest.newBuilder()
				.GET()
				.uri(URI.create(logoUrl))
				.timeout(Duration.ofSeconds(8))
				.header("Accept", "image/png,image/*")
				.header("User-Agent", "better-server-list-fabric/1.1")
				.build();
			HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return null;
			}
			return NativeImage.read(new ByteArrayInputStream(response.body()));
		} catch (Exception exception) {
			return null;
		}
	}

	private NativeImage bsl$downloadMinesparkStatusIcon(String address) {
		try {
			String encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8);
			String endpoint = String.format(MINE_SPARK_STATUS_TEMPLATE, encodedAddress);
			HttpRequest request = HttpRequest.newBuilder()
				.GET()
				.uri(URI.create(endpoint))
				.timeout(Duration.ofSeconds(8))
				.header("Accept", "application/json")
				.header("User-Agent", "better-server-list-fabric/1.1")
				.build();
			HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return null;
			}

			JsonElement parsed = new JsonParser().parse(response.body());
			if (!parsed.isJsonObject()) {
				return null;
			}

			JsonObject object = parsed.getAsJsonObject();
			JsonElement iconElement = object.get("icon");
			if (iconElement == null || !iconElement.isJsonPrimitive()) {
				return null;
			}

			return this.bsl$decodeDataImage(iconElement.getAsString());
		} catch (Exception ignored) {
			return null;
		}
	}

	private NativeImage bsl$decodeDataImage(String dataUrl) {
		if (dataUrl == null || !dataUrl.startsWith("data:image/")) {
			return null;
		}

		int commaIndex = dataUrl.indexOf(',');
		if (commaIndex <= 0 || commaIndex >= dataUrl.length() - 1) {
			return null;
		}

		String payload = dataUrl.substring(commaIndex + 1).trim();
		if (payload.contains("%")) {
			try {
				payload = java.net.URLDecoder.decode(payload, StandardCharsets.UTF_8);
			} catch (Exception ignored) {
				// Keep original payload and try decode paths below.
			}
		}

		byte[] decoded = this.bsl$tryBase64Decode(payload);
		if (decoded == null || decoded.length == 0) {
			return null;
		}

		try {
			return NativeImage.read(new ByteArrayInputStream(decoded));
		} catch (Exception ignored) {
			return null;
		}
	}

	private byte[] bsl$tryBase64Decode(String payload) {
		try {
			return Base64.getDecoder().decode(payload);
		} catch (Exception ignored) {
		}
		try {
			return Base64.getMimeDecoder().decode(payload);
		} catch (Exception ignored) {
		}
		try {
			return Base64.getUrlDecoder().decode(payload);
		} catch (Exception ignored) {
		}
		return null;
	}

	private String bsl$extractMcsrvstatAddress(String logoUrl) {
		if (logoUrl == null || logoUrl.isEmpty()) {
			return "";
		}
		String normalized = logoUrl;
		if (normalized.startsWith(MCSRSTAT_ICON_PREFIX)) {
			normalized = normalized.substring(MCSRSTAT_ICON_PREFIX.length());
		} else if (normalized.startsWith(MCSRSTAT_ICON_PREFIX_HTTP)) {
			normalized = normalized.substring(MCSRSTAT_ICON_PREFIX_HTTP.length());
		} else {
			return "";
		}

		int queryIndex = normalized.indexOf('?');
		if (queryIndex >= 0) {
			normalized = normalized.substring(0, queryIndex);
		}
		return normalized.trim();
	}

	private void bsl$releaseLogoTextures() {
		if (this.minecraft == null) {
			return;
		}
		for (Identifier identifier : this.registeredTextures) {
			this.minecraft.getTextureManager().release(identifier);
		}
		this.registeredTextures.clear();
		this.logoTextures.clear();
		this.logoLoading.clear();
	}

	private boolean bsl$matchesQuery(PublicServerCatalog.Entry entry, String query) {
		if (query.isBlank()) {
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

	private String bsl$playersText(PublicServerCatalog.Entry entry) {
		if (entry.players() >= 0 && entry.maxPlayers() > 0) {
			return "Players: " + entry.players() + "/" + entry.maxPlayers();
		}
		if (entry.players() >= 0) {
			return "Players: " + entry.players();
		}
		return "Players: --";
	}

	private String bsl$logoKey(String address) {
		return address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
	}

	private String bsl$abbreviate(String value, int maxLength) {
		if (value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength - 3) + "...";
	}
}

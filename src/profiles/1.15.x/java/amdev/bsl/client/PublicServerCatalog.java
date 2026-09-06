package amdev.bsl.client;

import amdev.bsl.BetterServerList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class PublicServerCatalog {
	private static final ResourceLocation RESOURCE_ID = new ResourceLocation(BetterServerList.MOD_ID, "public_servers.json");
	private static final String LIVE_JAVA_ENDPOINT_TEMPLATE = "https://minecraft-list.info/api/v1/servers?game.slug=minecraft&page=%d";
	private static final String LIVE_SERVER_PAGE_BASE_URL = "https://minecraft-list.info/server/";
	private static final String LIVE_SERVER_ASSET_BASE_URL = "https://minecraft-list.info";
	private static final int LIVE_JAVA_PAGE_COUNT = 5;
	private static final int LIVE_REQUEST_TIMEOUT_SECONDS = 8;
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(LIVE_REQUEST_TIMEOUT_SECONDS))
		.build();
	private static volatile LoadResult sessionCachedLoadResult;

	private PublicServerCatalog() {
	}

	public static List<Entry> getFallbackEntries() {
		return List.of(
			new Entry("Hypixel", "mc.hypixel.net", new TranslatableComponent("bsl.server.hypixel.desc").getString(), "Minigames", "https://hypixel.net", List.of("pvp", "skyblock", "bedwars"), -1, -1, "https://api.mcsrvstat.us/icon/mc.hypixel.net"),
			new Entry("CubeCraft", "play.cubecraft.net", new TranslatableComponent("bsl.server.cubecraft.desc").getString(), "Minigames", "https://www.cubecraft.net", List.of("minigames", "eggwars"), -1, -1, "https://api.mcsrvstat.us/icon/play.cubecraft.net"),
			new Entry("Mineplex", "us.mineplex.com", new TranslatableComponent("bsl.server.mineplex.desc").getString(), "Arcade", "https://www.mineplex.com", List.of("arcade", "party"), -1, -1, "https://api.mcsrvstat.us/icon/us.mineplex.com"),
			new Entry("The Hive", "play.hivemc.com", new TranslatableComponent("bsl.server.hive.desc").getString(), "Minigames", "https://playhive.com", List.of("bedrock", "minigames"), -1, -1, "https://api.mcsrvstat.us/icon/play.hivemc.com")
		);
	}

	public static CompletableFuture<LoadResult> loadPreferredAsync(ResourceManager resourceManager) {
		LoadResult cached = sessionCachedLoadResult;
		if (cached != null) {
			return CompletableFuture.completedFuture(cached);
		}

		return CompletableFuture.supplyAsync(() -> loadPreferred(resourceManager));
	}

	public static CompletableFuture<LoadResult> reloadPreferredAsync(ResourceManager resourceManager) {
		sessionCachedLoadResult = null;
		return CompletableFuture.supplyAsync(() -> loadPreferred(resourceManager));
	}

	public static LoadResult getSessionCachedResult() {
		return sessionCachedLoadResult;
	}

	public static LoadResult loadPreferred(ResourceManager resourceManager) {
		LiveFetchResult online = fetchLiveServers();
		if (online.reachedLiveApi()) {
			LoadResult live = new LoadResult(online.entries(), true);
			sessionCachedLoadResult = live;
			return live;
		}
		LoadResult bundled = new LoadResult(loadBundled(resourceManager), false);
		sessionCachedLoadResult = bundled;
		return bundled;
	}

	public static List<Entry> loadBundled(ResourceManager resourceManager) {
		if (resourceManager == null) {
			return getFallbackEntries();
		}

		try (Resource resource = resourceManager.getResource(RESOURCE_ID); Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
			JsonElement root = bsl$parseJson(reader);
			if (root == null || !root.isJsonArray()) {
				return getFallbackEntries();
			}

			List<Entry> entries = new ArrayList<>();
			JsonArray array = root.getAsJsonArray();
			for (JsonElement element : array) {
				if (!element.isJsonObject()) {
					continue;
				}
				Entry entry = parseEntry(element.getAsJsonObject());
				if (entry != null) {
					entries.add(entry);
				}
			}

			return entries.isEmpty() ? getFallbackEntries() : entries;
		} catch (Exception exception) {
			BetterServerList.LOGGER.warn("Failed to load bundled public server catalog, using fallback list.", exception);
			return getFallbackEntries();
		}
	}

	private static LiveFetchResult fetchLiveServers() {
		Map<String, Entry> byAddress = new LinkedHashMap<>();
		List<CompletableFuture<List<Entry>>> pageRequests = new ArrayList<>(LIVE_JAVA_PAGE_COUNT);
		for (int page = 1; page <= LIVE_JAVA_PAGE_COUNT; page++) {
			pageRequests.add(fetchLivePage(page));
		}

		try {
			CompletableFuture
				.allOf(pageRequests.toArray(CompletableFuture[]::new))
				.get(LIVE_REQUEST_TIMEOUT_SECONDS + 2L, TimeUnit.SECONDS);
		} catch (Exception exception) {
			BetterServerList.LOGGER.warn("Timed out while loading part of the live server catalog; using any completed pages.");
		}

		for (CompletableFuture<List<Entry>> pageRequest : pageRequests) {
			for (Entry entry : pageRequest.getNow(List.of())) {
				byAddress.putIfAbsent(entry.address().toLowerCase(Locale.ROOT), entry);
			}
		}

		List<Entry> entries = new ArrayList<>(byAddress.values());
		entries.sort(
			Comparator
				.comparingInt(PublicServerCatalog::bsl$sortablePlayers)
				.reversed()
				.thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
		);
		return new LiveFetchResult(entries, !entries.isEmpty());
	}

	private static CompletableFuture<List<Entry>> fetchLivePage(int page) {
		String endpoint = String.format(Locale.ROOT, LIVE_JAVA_ENDPOINT_TEMPLATE, page);
		HttpRequest request = HttpRequest.newBuilder()
			.GET()
			.uri(URI.create(endpoint))
			.timeout(Duration.ofSeconds(LIVE_REQUEST_TIMEOUT_SECONDS))
			.header("Accept", "application/json")
			.header("User-Agent", "better-server-list-fabric/1.2")
			.build();

		return HTTP_CLIENT
			.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.handle((response, throwable) -> {
				if (throwable != null) {
					BetterServerList.LOGGER.warn("Failed to fetch live server catalog page {} from minecraft-list.info", page, throwable);
					return List.of();
				}
				if (response.statusCode() < 200 || response.statusCode() >= 300) {
					BetterServerList.LOGGER.warn("Live server catalog page {} failed with status {}", page, response.statusCode());
					return List.of();
				}

				try {
					JsonArray array = extractArray(new JsonParser().parse(response.body()));
					if (array == null) {
						BetterServerList.LOGGER.warn("Live server catalog page {} returned an unexpected response.", page);
						return List.of();
					}

					List<Entry> entries = new ArrayList<>(array.size());
					for (JsonElement element : array) {
						if (!element.isJsonObject()) {
							continue;
						}
						Entry entry = parseLiveEntry(element.getAsJsonObject());
						if (entry != null) {
							entries.add(entry);
						}
					}
					return entries;
				} catch (Exception exception) {
					BetterServerList.LOGGER.warn("Failed to parse live server catalog page {} from minecraft-list.info", page, exception);
					return List.of();
				}
			});
	}

	private static JsonArray extractArray(JsonElement root) {
		if (root == null || root.isJsonNull()) {
			return null;
		}
		if (root.isJsonArray()) {
			return root.getAsJsonArray();
		}
		if (!root.isJsonObject()) {
			return null;
		}

		JsonObject object = root.getAsJsonObject();
		for (String key : List.of("servers", "data", "results", "items")) {
			JsonElement element = object.get(key);
			if (element != null && element.isJsonArray()) {
				return element.getAsJsonArray();
			}
		}
		return null;
	}

	private static Entry parseLiveEntry(JsonObject object) {
		String name = sanitizeServerName(readString(object, "name"));
		String host = readString(object, "host");
		if (name.length() == 0 || host.length() == 0) {
			return null;
		}

		int port = readInt(object, "port");
		String address = formatAddress(host, port);
		boolean online = readBoolean(object, "online");
		int players = online ? Math.max(readInt(object, "players"), 0) : 0;
		int maxPlayers = readInt(object, "maxPlayers");
		String description = readString(object, "description");
		if (description.length() == 0) {
			description = buildDescription(online ? "online" : "offline", players, maxPlayers);
		}

		String uuid = readString(object, "uuid");
		String website = uuid.length() == 0 ? "" : LIVE_SERVER_PAGE_BASE_URL + uuid;
		String logo = normalizeCatalogAssetUrl(readString(object, "faviconPath"));
		if (logo.length() == 0) {
			logo = "https://api.mcsrvstat.us/icon/" + address;
		}

		List<String> tags = new ArrayList<>();
		String version = readString(object, "serverVersionRaw");
		if (version.length() > 0) {
			tags.add(version);
		}
		String country = readString(object, "country");
		if (country.length() > 0) {
			tags.add(country.toUpperCase(Locale.ROOT));
		}
		tags.add(online ? "online" : "offline");
		if (players > 0) {
			tags.add("active");
		}

		return new Entry(name, address, description, "Java", website, tags, players, maxPlayers, logo);
	}

	private static String formatAddress(String host, int port) {
		if (port <= 0 || port == 25565) {
			return host;
		}
		if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
			return '[' + host + "]:" + port;
		}
		return host + ':' + port;
	}

	private static String normalizeCatalogAssetUrl(String value) {
		if (value == null || value.trim().length() == 0) {
			return "";
		}
		String trimmed = value.trim();
		if (trimmed.startsWith("/")) {
			return LIVE_SERVER_ASSET_BASE_URL + trimmed;
		}
		if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
			return trimmed;
		}
		return "";
	}

	private static Entry parseEntry(JsonObject object) {
		String name = sanitizeServerName(readString(object, "name"));
		String address = readString(object, "address");
		if (name.isEmpty() || address.isEmpty()) {
			return null;
		}

		String description = readString(object, "description");
		String category = readString(object, "category");
		String website = readString(object, "website");
		int players = readInt(object, "players");
		int maxPlayers = readInt(object, "maxPlayers");
		String logo = readString(object, "logo");
		if (logo.isEmpty()) {
			logo = "https://api.mcsrvstat.us/icon/" + address;
		}

		List<String> tags = new ArrayList<>();
		JsonArray tagArray = object.getAsJsonArray("tags");
		if (tagArray != null) {
			for (JsonElement tag : tagArray) {
				if (tag.isJsonPrimitive()) {
					String value = tag.getAsString().trim();
					if (!value.isEmpty()) {
						tags.add(value);
					}
				}
			}
		}

		return new Entry(name, address, description, category, website, tags, players, maxPlayers, logo);
	}

	private static String sanitizeServerName(String rawName) {
		String name = rawName == null ? "" : rawName.trim();
		if (name.isEmpty()) {
			return "";
		}

		name = name.replaceAll("(?i)[&§][0-9A-FK-ORX]", "");
		name = name.replaceAll("(?i);t+$", "");
		name = name.replaceAll("\\s{2,}", " ").trim();
		return name;
	}

	private static String readString(JsonObject object, String key) {
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive()) {
			return "";
		}
		return element.getAsString().trim();
	}

	private static int readInt(JsonObject object, String key) {
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive()) {
			return -1;
		}
		try {
			return element.getAsInt();
		} catch (Exception ignored) {
			try {
				String text = element.getAsString().replace(",", "").trim();
				return Integer.parseInt(text);
			} catch (Exception ignoredAgain) {
				return -1;
			}
		}
	}

	private static boolean readBoolean(JsonObject object, String key) {
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive()) {
			return false;
		}
		try {
			return element.getAsBoolean();
		} catch (Exception ignored) {
			String text = element.getAsString().trim();
			return "true".equalsIgnoreCase(text) || "1".equals(text);
		}
	}

	private static String buildDescription(String statusKey, int players, int maxPlayers) {
		String localizedStatus = "online".equalsIgnoreCase(statusKey)
			? new TranslatableComponent("bsl.status.online").getString()
			: ("offline".equalsIgnoreCase(statusKey)
				? new TranslatableComponent("bsl.status.offline").getString()
				: new TranslatableComponent("bsl.status.unknown").getString());

		if (players > 0 && maxPlayers > 0) {
			return new TranslatableComponent("bsl.description.status_players_max", localizedStatus, players, maxPlayers).getString();
		}
		if (players > 0) {
			return new TranslatableComponent("bsl.description.status_players", localizedStatus, players).getString();
		}
		if (!statusKey.isEmpty()) {
			return new TranslatableComponent("bsl.description.status_only", localizedStatus).getString();
		}
		return "";
	}

	private static int bsl$sortablePlayers(Entry entry) {
		return Math.max(entry.players(), 0);
	}

	private static JsonElement bsl$parseJson(Reader reader) {
		try {
			return new JsonParser().parse(reader);
		} catch (Exception ignored) {
			return null;
		}
	}

	public static final class LoadResult {
		private final List<Entry> entries;
		private final boolean fromLiveApi;

		public LoadResult(List<Entry> entries, boolean fromLiveApi) {
			this.entries = entries;
			this.fromLiveApi = fromLiveApi;
		}

		public List<Entry> entries() {
			return this.entries;
		}

		public boolean fromLiveApi() {
			return this.fromLiveApi;
		}
	}

	private static final class LiveFetchResult {
		private final List<Entry> entries;
		private final boolean reachedLiveApi;

		public LiveFetchResult(List<Entry> entries, boolean reachedLiveApi) {
			this.entries = entries;
			this.reachedLiveApi = reachedLiveApi;
		}

		public List<Entry> entries() {
			return this.entries;
		}

		public boolean reachedLiveApi() {
			return this.reachedLiveApi;
		}
	}

	public static final class Entry {
		private final String name;
		private final String address;
		private final String description;
		private final String category;
		private final String website;
		private final List<String> tags;
		private final int players;
		private final int maxPlayers;
		private final String logoUrl;

		public Entry(String name, String address, String description, String category, String website, List<String> tags, int players, int maxPlayers, String logoUrl) {
			this.name = name;
			this.address = address;
			this.description = description;
			this.category = category;
			this.website = website;
			this.tags = tags;
			this.players = players;
			this.maxPlayers = maxPlayers;
			this.logoUrl = logoUrl;
		}

		public String name() {
			return this.name;
		}

		public String address() {
			return this.address;
		}

		public String description() {
			return this.description;
		}

		public String category() {
			return this.category;
		}

		public String website() {
			return this.website;
		}

		public List<String> tags() {
			return this.tags;
		}

		public int players() {
			return this.players;
		}

		public int maxPlayers() {
			return this.maxPlayers;
		}

		public String logoUrl() {
			return this.logoUrl;
		}

		public String searchableText() {
			StringBuilder builder = new StringBuilder();
			builder.append(this.name).append(' ');
			builder.append(this.address).append(' ');
			builder.append(this.description).append(' ');
			builder.append(this.category).append(' ');
			if (this.players > 0) {
				builder.append(this.players).append(' ');
			}
			for (String tag : this.tags) {
				builder.append(tag).append(' ');
			}
			return builder.toString();
		}
	}
}

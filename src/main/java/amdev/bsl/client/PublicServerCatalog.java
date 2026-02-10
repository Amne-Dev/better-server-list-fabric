package amdev.bsl.client;

import amdev.bsl.BetterServerList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

public final class PublicServerCatalog {
	private static final Identifier RESOURCE_ID = Identifier.fromNamespaceAndPath(BetterServerList.MOD_ID, "public_servers.json");
	private static final List<String> LIVE_JAVA_ENDPOINTS = List.of(
		"https://servers.minespark.org/servers/java?status=online",
		"https://servers.minespark.org/servers/java?limit=1500",
		"https://servers.minespark.org/servers/java",
		"https://servers.minespark.org/servers/java/"
	);
	private static final List<String> LIVE_STATUS_ENDPOINT_TEMPLATES = List.of(
		"https://srvstat.minespark.org/general/%s",
		"https://api.mcsrvstat.us/3/%s"
	);
	private static final int LIVE_STATUS_CHECK_LIMIT = 180;
	private static final int LIVE_STATUS_CHECK_THREADS = 24;
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(15))
		.build();
	private static final List<Entry> FALLBACK = List.of(
		new Entry("Hypixel", "mc.hypixel.net", "Popular minigames and PvP modes.", "Minigames", "https://hypixel.net", List.of("pvp", "skyblock", "bedwars"), -1, -1, "https://api.mcsrvstat.us/icon/mc.hypixel.net"),
		new Entry("CubeCraft", "play.cubecraft.net", "Fast-paced competitive minigames.", "Minigames", "https://www.cubecraft.net", List.of("minigames", "eggwars"), -1, -1, "https://api.mcsrvstat.us/icon/play.cubecraft.net"),
		new Entry("Mineplex", "us.mineplex.com", "Classic arcade server with mixed game modes.", "Arcade", "https://www.mineplex.com", List.of("arcade", "party"), -1, -1, "https://api.mcsrvstat.us/icon/us.mineplex.com"),
		new Entry("The Hive", "play.hivemc.com", "Social and competitive minigames.", "Minigames", "https://playhive.com", List.of("bedrock", "minigames"), -1, -1, "https://api.mcsrvstat.us/icon/play.hivemc.com")
	);

	private PublicServerCatalog() {
	}

	public static CompletableFuture<LoadResult> loadPreferredAsync(ResourceManager resourceManager) {
		return CompletableFuture.supplyAsync(() -> loadPreferred(resourceManager));
	}

	public static LoadResult loadPreferred(ResourceManager resourceManager) {
		LiveFetchResult online = fetchLiveServers();
		if (online.reachedLiveApi()) {
			return new LoadResult(online.entries(), true);
		}
		return new LoadResult(loadBundled(resourceManager), false);
	}

	public static List<Entry> loadBundled(ResourceManager resourceManager) {
		if (resourceManager == null) {
			return FALLBACK;
		}

		try (Reader reader = resourceManager.openAsReader(RESOURCE_ID)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonArray()) {
				return FALLBACK;
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

			return entries.isEmpty() ? FALLBACK : entries;
		} catch (Exception exception) {
			BetterServerList.LOGGER.warn("Failed to load bundled public server catalog, using fallback list.", exception);
			return FALLBACK;
		}
	}

	private static LiveFetchResult fetchLiveServers() {
		Map<String, Entry> byAddress = new LinkedHashMap<>();
		boolean reachedLiveApi = false;

		for (String endpoint : LIVE_JAVA_ENDPOINTS) {
			try {
				HttpRequest request = HttpRequest.newBuilder()
					.GET()
					.uri(URI.create(endpoint))
					.timeout(Duration.ofSeconds(20))
					.header("Accept", "application/json")
					.header("User-Agent", "better-server-list-fabric/1.0")
					.build();

				HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() < 200 || response.statusCode() >= 300) {
					BetterServerList.LOGGER.warn("Live server catalog request to {} failed with status {}", endpoint, response.statusCode());
					continue;
				}
				reachedLiveApi = true;

				JsonElement root = JsonParser.parseString(response.body());
				JsonArray array = extractArray(root);
				if (array == null) {
					continue;
				}

				for (JsonElement element : array) {
					if (!element.isJsonObject()) {
						continue;
					}
					Entry entry = parseLiveEntry(element.getAsJsonObject());
					if (entry != null) {
						byAddress.putIfAbsent(entry.address().toLowerCase(Locale.ROOT), entry);
					}
				}
			} catch (Exception exception) {
				BetterServerList.LOGGER.warn("Failed to fetch live server catalog from {}", endpoint, exception);
			}
		}

		List<Entry> baseEntries = new ArrayList<>(byAddress.values());
		List<Entry> hydratedEntries = hydrateLiveEntries(baseEntries);
		if (!hydratedEntries.isEmpty()) {
			return new LiveFetchResult(hydratedEntries, reachedLiveApi);
		}

		// If the list endpoint is reachable but stale, still return discovered entries
		// instead of dropping all the way to the tiny bundled fallback.
		if (reachedLiveApi && !baseEntries.isEmpty()) {
			baseEntries.sort(
				Comparator
					.comparingInt(PublicServerCatalog::bsl$sortablePlayers)
					.reversed()
					.thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
			);
			return new LiveFetchResult(baseEntries, true);
		}

		return new LiveFetchResult(List.of(), reachedLiveApi);
	}

	private static List<Entry> hydrateLiveEntries(List<Entry> baseEntries) {
		if (baseEntries.isEmpty()) {
			return List.of();
		}

		int limit = Math.min(LIVE_STATUS_CHECK_LIMIT, baseEntries.size());
		ExecutorService executor = Executors.newFixedThreadPool(LIVE_STATUS_CHECK_THREADS);
		List<CompletableFuture<Entry>> futures = new ArrayList<>(limit);
		for (int i = 0; i < limit; i++) {
			Entry seed = baseEntries.get(i);
			futures.add(CompletableFuture.supplyAsync(() -> fetchStatusEntry(seed), executor));
		}

		try {
			CompletableFuture
				.allOf(futures.toArray(CompletableFuture[]::new))
				.get(25, TimeUnit.SECONDS);
		} catch (Exception ignored) {
			// Partial results are still useful if some probes timed out.
		} finally {
			executor.shutdownNow();
		}

		Map<String, Entry> byAddress = new LinkedHashMap<>();
		for (Entry entry : baseEntries) {
			byAddress.put(entry.address().toLowerCase(Locale.ROOT), entry);
		}

		for (CompletableFuture<Entry> future : futures) {
			Entry entry = future.getNow(null);
			if (entry != null) {
				byAddress.put(entry.address().toLowerCase(Locale.ROOT), entry);
			}
		}

		List<Entry> hydrated = new ArrayList<>(byAddress.values());
		hydrated.sort(
			Comparator
				.comparingInt(PublicServerCatalog::bsl$sortablePlayers)
				.reversed()
				.thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
		);
		return hydrated;
	}

	private static Entry fetchStatusEntry(Entry seed) {
		String encodedAddress = URLEncoder.encode(seed.address(), StandardCharsets.UTF_8);
		for (String template : LIVE_STATUS_ENDPOINT_TEMPLATES) {
			try {
				String endpoint = template.formatted(encodedAddress);
				HttpRequest request = HttpRequest.newBuilder()
					.GET()
					.uri(URI.create(endpoint))
					.timeout(Duration.ofSeconds(6))
					.header("Accept", "application/json")
					.header("User-Agent", "better-server-list-fabric/1.0")
					.build();

				HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() < 200 || response.statusCode() >= 300) {
					continue;
				}

				JsonElement parsed = JsonParser.parseString(response.body());
				if (!parsed.isJsonObject()) {
					continue;
				}

				JsonObject status = parsed.getAsJsonObject();
				boolean online = readBoolean(status, "online");

				int players = readNestedInt(status, "players", "online");
				int maxPlayers = readNestedInt(status, "players", "max");
				if (players < 0) {
					players = Math.max(seed.players(), 0);
				}
				if (maxPlayers < 0) {
					maxPlayers = seed.maxPlayers();
				}

				String logo = seed.logoUrl();
				String icon = readString(status, "icon");
				if (logo.isBlank() && !icon.isBlank()) {
					logo = icon;
				}

				String description = buildDescription(online ? "online" : "offline", players, maxPlayers);
				return new Entry(seed.name(), seed.address(), description, seed.category(), seed.website(), seed.tags(), players, maxPlayers, logo);
			} catch (Exception ignored) {
				// Try next status provider.
			}
		}
		return null;
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
		if (name.isBlank()) {
			name = sanitizeServerName(readString(object, "serverName"));
		}
		String address = readString(object, "ipAddress");
		if (address.isBlank()) {
			address = readString(object, "address");
		}
		if (address.isBlank()) {
			address = readString(object, "ip");
		}
		if (name.isBlank() || address.isBlank()) {
			return null;
		}

		String platform = readString(object, "platform");
		String status = readString(object, "status");
		int players = readLivePlayers(object);
		int maxPlayers = readLiveMaxPlayers(object);
		String logo = readString(object, "logo");

		String description = buildDescription(status, players, maxPlayers);
		String category = platform.isBlank() ? "Online" : platform;

		List<String> tags = new ArrayList<>();
		if (!platform.isBlank()) {
			tags.add(platform);
		}
		if (!status.isBlank()) {
			tags.add(status.toLowerCase(Locale.ROOT));
		}
		if (players > 0) {
			tags.add("active");
		}
		if (!logo.isBlank()) {
			tags.add("icon");
		}

		return new Entry(name, address, description, category, "", tags, players, maxPlayers, logo);
	}

	private static Entry parseEntry(JsonObject object) {
		String name = sanitizeServerName(readString(object, "name"));
		String address = readString(object, "address");
		if (name.isBlank() || address.isBlank()) {
			return null;
		}

		String description = readString(object, "description");
		String category = readString(object, "category");
		String website = readString(object, "website");
		int players = readInt(object, "players");
		int maxPlayers = readInt(object, "maxPlayers");
		String logo = readString(object, "logo");
		if (logo.isBlank()) {
			logo = "https://api.mcsrvstat.us/icon/" + address;
		}

		List<String> tags = new ArrayList<>();
		JsonArray tagArray = object.getAsJsonArray("tags");
		if (tagArray != null) {
			for (JsonElement tag : tagArray) {
				if (tag.isJsonPrimitive()) {
					String value = tag.getAsString().trim();
					if (!value.isBlank()) {
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

		// Strip common color/format codes from remote server lists.
		name = name.replaceAll("(?i)[&§][0-9A-FK-ORX]", "");
		// Some lists append ';t' artifacts; remove trailing instances.
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

	private static int readNestedInt(JsonObject object, String parentKey, String childKey) {
		JsonElement parent = object.get(parentKey);
		if (parent == null || !parent.isJsonObject()) {
			return -1;
		}
		return readInt(parent.getAsJsonObject(), childKey);
	}

	private static int readLivePlayers(JsonObject object) {
		int direct = firstKnownInt(object, "players", "onlinePlayers", "playerCount");
		if (direct >= 0) {
			return direct;
		}

		JsonElement playersElement = object.get("players");
		if (playersElement != null && playersElement.isJsonObject()) {
			JsonObject players = playersElement.getAsJsonObject();
			int nested = firstKnownInt(players, "online", "current", "count", "value");
			if (nested >= 0) {
				return nested;
			}
		}
		return -1;
	}

	private static int readLiveMaxPlayers(JsonObject object) {
		int direct = firstKnownInt(object, "maxPlayers", "playerLimit", "max");
		if (direct >= 0) {
			return direct;
		}

		JsonElement playersElement = object.get("players");
		if (playersElement != null && playersElement.isJsonObject()) {
			JsonObject players = playersElement.getAsJsonObject();
			int nested = firstKnownInt(players, "max", "maximum", "limit");
			if (nested >= 0) {
				return nested;
			}
		}
		return -1;
	}

	private static int firstKnownInt(JsonObject object, String... keys) {
		for (String key : keys) {
			int value = readInt(object, key);
			if (value >= 0) {
				return value;
			}
		}
		return -1;
	}

	private static String buildDescription(String status, int players, int maxPlayers) {
		if (players > 0 && maxPlayers > 0) {
			return "Status: " + (status.isBlank() ? "unknown" : status) + " | Players: " + players + "/" + maxPlayers;
		}
		if (players > 0) {
			return "Status: " + (status.isBlank() ? "unknown" : status) + " | Players: " + players;
		}
		if (!status.isBlank()) {
			return "Status: " + status;
		}
		return "";
	}

	private static int bsl$sortablePlayers(Entry entry) {
		return Math.max(entry.players(), 0);
	}

	public record LoadResult(List<Entry> entries, boolean fromLiveApi) {
	}

	private record LiveFetchResult(List<Entry> entries, boolean reachedLiveApi) {
	}

	public record Entry(String name, String address, String description, String category, String website, List<String> tags, int players, int maxPlayers, String logoUrl) {
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

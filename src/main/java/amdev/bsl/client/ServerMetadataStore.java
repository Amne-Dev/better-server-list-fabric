package amdev.bsl.client;

import amdev.bsl.BetterServerList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.loader.api.FabricLoader;

public final class ServerMetadataStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("better-server-list.json");
	private static final Set<String> FAVORITES = new HashSet<>();
	private static final Map<String, String> CATEGORIES = new HashMap<>();
	private static boolean loaded;

	private ServerMetadataStore() {
	}

	public static synchronized boolean isFavorite(String serverAddress) {
		ensureLoaded();
		return FAVORITES.contains(normalize(serverAddress));
	}

	public static synchronized void setFavorite(String serverAddress, boolean favorite) {
		ensureLoaded();
		String key = normalize(serverAddress);
		if (key.isEmpty()) {
			return;
		}
		if (favorite) {
			FAVORITES.add(key);
		} else {
			FAVORITES.remove(key);
		}
		save();
	}

	public static synchronized String getCategory(String serverAddress) {
		ensureLoaded();
		return CATEGORIES.getOrDefault(normalize(serverAddress), "");
	}

	public static synchronized void setCategory(String serverAddress, String category) {
		ensureLoaded();
		String key = normalize(serverAddress);
		if (key.isEmpty()) {
			return;
		}

		String value = category == null ? "" : category.trim();
		if (value.isEmpty()) {
			CATEGORIES.remove(key);
		} else {
			CATEGORIES.put(key, value);
		}
		save();
	}

	public static synchronized List<String> getKnownCategories() {
		ensureLoaded();
		return CATEGORIES.values()
			.stream()
			.map(String::trim)
			.filter(value -> !value.isEmpty())
			.distinct()
			.sorted(String.CASE_INSENSITIVE_ORDER)
			.collect(Collectors.toList());
	}

	public static synchronized void save() {
		ensureLoaded();
		JsonObject root = new JsonObject();

		JsonArray favoriteArray = new JsonArray();
		for (String favorite : FAVORITES) {
			favoriteArray.add(favorite);
		}
		root.add("favorites", favoriteArray);

		JsonObject categories = new JsonObject();
		for (Map.Entry<String, String> entry : CATEGORIES.entrySet()) {
			categories.addProperty(entry.getKey(), entry.getValue());
		}
		root.add("categories", categories);

		try {
			Files.createDirectories(FILE.getParent());
			byte[] bytes = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
			Files.write(FILE, bytes);
		} catch (IOException exception) {
			BetterServerList.LOGGER.error("Failed to save server metadata to {}", FILE, exception);
		}
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;

		FAVORITES.clear();
		CATEGORIES.clear();

		if (!Files.exists(FILE)) {
			return;
		}

		try {
			String content = new String(Files.readAllBytes(FILE), StandardCharsets.UTF_8);
			JsonElement parsed = bsl$parseJson(content);
			if (parsed == null || !parsed.isJsonObject()) {
				return;
			}
			JsonObject root = parsed.getAsJsonObject();

			JsonArray favorites = root.getAsJsonArray("favorites");
			if (favorites != null) {
				for (JsonElement element : favorites) {
					if (element.isJsonPrimitive()) {
						String value = normalize(element.getAsString());
						if (!value.isEmpty()) {
							FAVORITES.add(value);
						}
					}
				}
			}

			JsonObject categories = root.getAsJsonObject("categories");
			if (categories != null) {
				for (Map.Entry<String, JsonElement> entry : categories.entrySet()) {
					if (entry.getValue().isJsonPrimitive()) {
						String key = normalize(entry.getKey());
						String value = entry.getValue().getAsString().trim();
						if (!key.isEmpty() && !value.isEmpty()) {
							CATEGORIES.put(key, value);
						}
					}
				}
			}
		} catch (Exception exception) {
			BetterServerList.LOGGER.error("Failed to load server metadata from {}", FILE, exception);
		}
	}

	private static JsonElement bsl$parseJson(String json) {
		try {
			return new JsonParser().parse(json);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String normalize(String serverAddress) {
		if (serverAddress == null) {
			return "";
		}
		return serverAddress.trim().toLowerCase(Locale.ROOT);
	}
}

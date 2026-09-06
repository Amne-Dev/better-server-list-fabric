package amdev.bsl.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

public final class ServerOrdering {
	private static final Comparator<ServerData> ORDER = Comparator
		.comparing((ServerData serverData) -> !ServerMetadataStore.isFavorite(serverData.ip))
		.thenComparing((ServerData serverData) -> isCategoryEmpty(ServerMetadataStore.getCategory(serverData.ip)))
		.thenComparing(
			(ServerData serverData) -> normalize(ServerMetadataStore.getCategory(serverData.ip)),
			String::compareTo
		)
		.thenComparing(ServerOrdering::name, String.CASE_INSENSITIVE_ORDER)
		.thenComparing(ServerOrdering::address, String.CASE_INSENSITIVE_ORDER);

	private ServerOrdering() {
	}

	public static void reorder(ServerList serverList) {
		if (serverList == null || serverList.size() < 2) {
			return;
		}

		List<ServerData> original = new ArrayList<>(serverList.size());
		for (int i = 0; i < serverList.size(); i++) {
			original.add(serverList.get(i));
		}

		List<ServerData> ordered = new ArrayList<>(original);
		ordered.sort(ORDER);

		boolean changed = false;
		for (int i = 0; i < ordered.size(); i++) {
			if (ordered.get(i) != original.get(i)) {
				changed = true;
				serverList.replace(i, ordered.get(i));
			}
		}

		if (changed) {
			serverList.save();
		}
	}

	private static boolean isCategoryEmpty(String category) {
		return category == null || category.trim().isEmpty();
	}

	private static String name(ServerData serverData) {
		return serverData.name == null ? "" : serverData.name;
	}

	private static String address(ServerData serverData) {
		return serverData.ip == null ? "" : serverData.ip;
	}

	private static String normalize(String value) {
		if (value == null || value.trim().isEmpty()) {
			return "~";
		}
		return value.toLowerCase(Locale.ROOT);
	}
}

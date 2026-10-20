package discord.webhooks;

import discord.comp.impl.Thumbnail;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import properties.ServerProperties;

/**
 * Item thumbnails for webhook embeds (2026-10-20). The public RuneLite icon server knows only real OSRS items by id, so
 * a custom item showed as nothing or as whichever real item shares its id. Icons rendered from our own cache
 * (.dev/cache-restore-tool ExportItemIcons) live in {@code <data_path>/item_icons/<id>.png}: an item with one gets an
 * {@code attachment://} thumbnail, and {@link WebhookSender} uploads the file with the message. Anything else keeps the
 * RuneLite URL.
 */
public final class ItemIcons {

	static final String PREFIX = "attachment://", FILE_PREFIX = "item_";
	private static final String RUNELITE = "https://static.runelite.net/cache/item/icon/";

	private ItemIcons() {
	}

	public static Thumbnail thumbnail(int itemId) {
		var thumbnail = new Thumbnail();
		thumbnail.setUrl(Files.isRegularFile(file(itemId)) ? PREFIX + FILE_PREFIX + itemId + ".png" : RUNELITE + itemId + ".png");
		return thumbnail;
	}

	/** The PNG behind an {@code item_<id>.png} attachment name, or null if it can't be read. */
	static byte[] read(String fileName) {
		try {
			var id = Integer.parseInt(fileName.substring(FILE_PREFIX.length(), fileName.length() - ".png".length()));
			return Files.readAllBytes(file(id));
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	private static Path file(int itemId) {
		return Path.of(ServerProperties.dataPath(), "item_icons", itemId + ".png");
	}
}

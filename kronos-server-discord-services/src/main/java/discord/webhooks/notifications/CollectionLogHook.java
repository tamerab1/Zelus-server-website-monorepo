package discord.webhooks.notifications;

import discord.comp.impl.*;
import discord.webhooks.Webhook;
import java.util.function.Supplier;
import org.json.JSONObject;
import properties.ServerProperties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CollectionLogHook {

	public static void sendDiscordMessage(Supplier<JSONObject> objectSupplier) {
		try {
			var object = objectSupplier.get();

			var thumbnail = new Thumbnail();
			thumbnail.setUrl("https://static.runelite.net/cache/item/icon/" + object.get("item_id") + ".png");

			var embedMessage = new Embed();
			embedMessage.setTitle("New collection log item!");
			embedMessage.setDescription("`%s` just received a new collection log item: `%s`!"
					.formatted(object.get("player"), object.get("item_name")));
			embedMessage.setColor(8917522);
			embedMessage.setThumbnail(thumbnail);

			var message = new Message();
			message.setEmbeds(embedMessage);

			Webhook.send(ServerProperties.get("discord_hook_collection_log", ""), message);
		} catch (Exception e) {
			log.error("Webhook error: ", e);
		}
	}
}

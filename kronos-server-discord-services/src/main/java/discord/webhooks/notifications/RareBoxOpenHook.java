package discord.webhooks.notifications;

import discord.webhooks.ItemIcons;
import discord.comp.impl.Embed;
import discord.comp.impl.Field;
import discord.comp.impl.Message;
import discord.webhooks.Webhook;
import org.json.JSONObject;
import properties.ServerProperties;

/**
 * @author Glabay | Glabay-Studios
 * @project reason-server
 * @social Discord: Glabay
 * @since 2025-08-21
 */
public interface RareBoxOpenHook {

	static void sendBoxDiscordMessage(JSONObject object) {
		var thumbnail = ItemIcons.thumbnail(object.getInt("item_id"));

		var embedMessage = new Embed();
			embedMessage.setTitle("Rare pull received!");
			embedMessage.setDescription("`%s` has just received a rare item!"
				.formatted(object.getString("player")));
			embedMessage.setColor(8917522);
			embedMessage.setFields(
				new Field("Item", object.getString("item"), false),
				new Field("Source", object.getString("source"), false)
			);
			embedMessage.setThumbnail(thumbnail);

		var message = new Message();
			message.setEmbeds(embedMessage);

		Webhook.send(ServerProperties.get("discord_hook_rare_box", ""), message);
	}
}

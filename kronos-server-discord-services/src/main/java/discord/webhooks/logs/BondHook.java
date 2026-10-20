package discord.webhooks.logs;

import discord.webhooks.ItemIcons;
import discord.comp.impl.Embed;
import discord.comp.impl.Field;
import discord.comp.impl.Message;
import discord.webhooks.Webhook;
import org.json.JSONObject;
import properties.ServerProperties;

import java.awt.*;

/**
 * @author Glabay | Glabay-Studios
 * @project reason-server
 * @social Discord: Glabay
 * @since 2025-08-21
 */
public interface BondHook {

	static void sendBondGiftToDiscord(JSONObject object) {
		var thumbnail = ItemIcons.thumbnail(object.getInt("item_id"));

		var embedMessage = new Embed();
			embedMessage.setTitle("Bond Gift Event");
			embedMessage.setDescription("`%s` has gifted a bond to `%s`"
				.formatted(object.get("player"), object.get("player_receiving")));
			embedMessage.setColor(new Color(32, 96, 255));
			embedMessage.setFields(new Field("Item", object.get("item_name").toString(),true));
			embedMessage.setThumbnail(thumbnail);

		var message = new Message();
			message.setEmbeds(embedMessage);

		Webhook.send(ServerProperties.get("discord_hook_bond_events", ""), message);
	}
}

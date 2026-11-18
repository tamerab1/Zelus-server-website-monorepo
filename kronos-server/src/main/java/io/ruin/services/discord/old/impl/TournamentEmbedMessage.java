// Beta audit 2026-11-18: the hard-coded Discord webhook URLs (with tokens) were removed from this legacy class.
package io.ruin.services.discord.old.impl;

import io.ruin.api.utils.ServerWrapper;
import io.ruin.model.World;
import io.ruin.services.discord.old.Webhook;
import io.ruin.services.discord.old.util.Embed;
import io.ruin.services.discord.old.util.Footer;
import io.ruin.services.discord.old.util.Message;

public class TournamentEmbedMessage {

	public static void sendDiscordMessage(String presetName, String minutes) {
        /*
        if (!World.isLive()){
            return;
        }
        try {
            Webhook webhook = new Webhook("");
            Message message = new Message();

            Embed embedMessage = new Embed();
            embedMessage.setTitle("Tournament System");
            embedMessage.setDescription("The " + presetName + " tournament will begin in **" + minutes + " minutes**. Login and enter the tournament entrance at home to join!");
            embedMessage.setColor(8917522);


            Footer footer = new Footer();
            footer.setText(World.type.getWorldName() + " - The Final Challenge!");
            embedMessage.setFooter(footer);


            message.setEmbeds(embedMessage);
            webhook.sendMessage(message.toJson());
        } catch (Exception e) {
            ServerWrapper.logError("Failed to send discord embed", e);
        }

         */
	}

}

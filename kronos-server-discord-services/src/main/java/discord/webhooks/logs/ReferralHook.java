package discord.webhooks.logs;

import discord.comp.impl.Embed;
import discord.comp.impl.Field;
import discord.comp.impl.Message;
import discord.webhooks.Webhook;
import properties.ServerProperties;

import java.awt.*;

/**
 * @author Glabay | Glabay-Studios
 * @project reason-server
 * @social Discord: Glabay
 * @since 2025-08-21
 */
public interface ReferralHook {

	static void sendRefClaimLogToDiscord(String player, String code) {
		var message = new Message();
		var embedMessage = new Embed();
			embedMessage.setTitle("Referral Event");
			embedMessage.setDescription("`%s` has claimed a referral code.".formatted(player));
			embedMessage.setColor(new Color(32, 96, 255));
			embedMessage.setFields(new Field("Code", code,true));
		message.setEmbeds(embedMessage);

		Webhook.send(ServerProperties.get("discord_hook_referral", ""), message);
	}

	static void sendMilestoneCompletedToDiscord(String referrer, String referred) {
		var message = new Message();
		var embedMessage = new Embed();
			embedMessage.setTitle("Referral Milestone Reached");
			embedMessage.setDescription("`%s` reached the referral milestone! Both `%s` and `%s` have received their referral rewards.".formatted(referred, referred, referrer));
			embedMessage.setColor(new Color(46, 204, 113));
			embedMessage.setFields(new Field("Referrer", referrer, true), new Field("Referred Player", referred, true));
		message.setEmbeds(embedMessage);

		Webhook.send(ServerProperties.get("discord_hook_referral", ""), message);
	}
}

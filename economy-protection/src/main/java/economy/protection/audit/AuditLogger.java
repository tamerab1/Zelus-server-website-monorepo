package economy.protection.audit;

import discord.comp.impl.Embed;
import discord.comp.impl.Field;
import discord.comp.impl.Message;
import discord.webhooks.Webhook;
import io.ruin.model.combat.Killer;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.Item;
import properties.ServerProperties;

/**
 * Structured admin-review logging for economy-protection events, reusing the
 * existing discord-webhook pattern (see {@code discord.webhooks.logs.WildernessPvpKillHook}).
 */
public final class AuditLogger {

	private AuditLogger() {
	}

	public static void logRejectedKill(Killer killer, Player pKilled, String reason) {
		var embed = new Embed();
		embed.setTitle("[Economy] Kill reward rejected");
		embed.setDescription(reason);
		embed.setColor(0xBA0000);
		embed.setFields(
			new Field("Killer", killer.player != null ? killer.player.getName() : "unknown", true),
			new Field("Victim", pKilled.getName(), true),
			new Field("Reason", reason, false)
		);

		var message = new Message();
		message.setEmbeds(embed);
		Webhook.send(ServerProperties.get("discord_hook_economy_audit", ""), message);
	}

	public static void logRejectedBounty(Player killer, String bossName, int pkpAmount, String reason) {
		var embed = new Embed();
		embed.setTitle("[Economy] Bounty contract reward rejected");
		embed.setDescription(reason);
		embed.setColor(0xBA0000);
		embed.setFields(
			new Field("Killer", killer.getName(), true),
			new Field("Boss", bossName, true),
			new Field("Pooled PKP", String.valueOf(pkpAmount), true),
			new Field("Reason", reason, false)
		);

		var message = new Message();
		message.setEmbeds(embed);
		Webhook.send(ServerProperties.get("discord_hook_economy_audit", ""), message);
	}

	public static void logSink(Player killer, Player victim, Item item) {
		var embed = new Embed();
		embed.setTitle("[Economy] High-value drop sunk");
		embed.setDescription(item.getAmount() + "x " + item.getDef().getName() + " was destroyed instead of dropped.");
		embed.setColor(0xBA0000);
		embed.setFields(
			new Field("Killer", killer.getName(), true),
			new Field("Victim", victim.getName(), true)
		);

		var message = new Message();
		message.setEmbeds(embed);
		Webhook.send(ServerProperties.get("discord_hook_economy_audit", ""), message);
	}
}

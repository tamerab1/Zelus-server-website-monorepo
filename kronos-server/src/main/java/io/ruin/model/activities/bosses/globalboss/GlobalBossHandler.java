package io.ruin.model.activities.bosses.globalboss;

import discord.webhooks.notifications.GlobalBroadcastHook;
import io.ruin.model.World;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GlobalBossHandler {
	static NPC currentBoss;
	static int currentIndex = 0;

	public static void spawnBoss() {
		// a global boss still being fought keeps its arena: skip this cycle rather than removing it mid-fight and
		// putting the next one on its tile
		if (currentBoss != null && !currentBoss.isRemoved() && currentBoss.getHp() > 0) {
			log.info("Global boss {} still alive; skipping this spawn cycle", currentBoss.getName());
			return;
		}
		if (currentBoss != null)
			currentBoss.remove();
		currentBoss = null;
		if (currentIndex >= GlobalBosses.values().length - 1) {
			currentIndex = 0;
		} else
			currentIndex++;
		int commandType = currentIndex + 1;
		broadcastEvent("The " + GlobalBosses.values()[currentIndex].boss.getDef().name
				+ " will spawn in 60 seconds, type ::gb to teleport to it!");

		World.startEvent(e -> {

			var nextBoss = new NPC(GlobalBosses.values()[currentIndex].boss.getId());
			onBossPrepared(nextBoss);
			e.delay(100);
			currentBoss = nextBoss.spawn(GlobalBosses.values()[currentIndex].spawnPosition);
			onBossSpawned(currentBoss);
		});
	}

	private static void onBossPrepared(NPC npc) {
		GlobalBroadcastHook.sendEventSpawnWarning(npc.getName(), null);
	}

	private static void onBossSpawned(NPC npc) {
		GlobalBroadcastHook.sendEventSpawnAnnouncement(npc.getName(), null);
	}

	/* Damage on a global boss (2026-10-29): every hit is capped at 50 (a crowd can't melt it), then scaled up while few
	 * players are fighting it, so a lone grinder isn't stuck on 22k HP for half an hour. Players fighting = distinct
	 * attackers in the last 25 ticks, counted per hit, so people arriving or leaving can't skew it. */
	public static final int HIT_CAP = 50;
	private static final int RECENT_TICKS = 25;
	private static final java.util.Map<Integer, Long> recentAttackers = new java.util.HashMap<>();
	private static NPC scaledFor;

	/** For each global boss's preDefend: the 50 cap, then the few-players multiplier. */
	public static void scaleHit(NPC boss, io.ruin.model.combat.Hit hit) {
		if (hit.damage > HIT_CAP)
			hit.damage = HIT_CAP;
		if (hit.attacker == null || hit.attacker.player == null || hit.damage <= 0)
			return;
		long now = io.ruin.Server.currentTick();
		synchronized (recentAttackers) {
			if (scaledFor != boss) { // a new boss: forget the last one's fighters
				recentAttackers.clear();
				scaledFor = boss;
			}
			recentAttackers.put(hit.attacker.player.getUserId(), now);
			recentAttackers.values().removeIf(t -> now - t > RECENT_TICKS);
			int fighters = recentAttackers.size();
			double multiplier = fighters <= 1 ? 4 : fighters == 2 ? 2.5 : fighters <= 4 ? 1.5 : 1;
			hit.damage = (int) Math.round(hit.damage * multiplier);
		}
	}

	/** Ticks between spawn cycles; the tick the next cycle runs on (the journal's World bosses row). */
	private static final int SPAWN_EVERY = 4900;
	private static long nextCycleTick;

	public static void register() {
		World.startEvent(e -> {
			while (true) {
				nextCycleTick = io.ruin.Server.currentTick() + SPAWN_EVERY;
				e.delay(SPAWN_EVERY);
				spawnBoss();
			}
		});
	}

	/** "Alive" while a global boss stands, else the time until the next spawn cycle (it spawns 60 seconds after). */
	public static String status() {
		if (currentBoss != null && !currentBoss.isRemoved() && currentBoss.getHp() > 0)
			return "<col=50e050>Alive</col> (::gb)";
		long ticks = Math.max(0, nextCycleTick - io.ruin.Server.currentTick()) + 100;
		long secs = ticks * 600 / 1000;
		return (secs / 60) + ":" + String.format("%02d", secs % 60) + " left";
	}

	private static void broadcastEvent(String eventMessage) {
		for (Player p : World.players()) {
			p.getPacketSender().sendBroadcast(eventMessage);
		}
	}
}


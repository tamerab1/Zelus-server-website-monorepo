package io.ruin.model.activities.bosses;

import io.ruin.api.utils.Random;
import io.ruin.cache.Color;
import io.ruin.cache.Icon;
import io.ruin.model.VoteHandler;
import io.ruin.model.World;
import io.ruin.model.activities.VoteBossHandler;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.item.Item;
import io.ruin.model.map.Bounds;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.model.map.ground.GroundItem;
import io.ruin.model.skills.prayer.Prayer;
import io.ruin.utility.Broadcast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Volcanus the Earthshaker as a ::vb vote boss (npc 16216, 2026-10-19; the custom boss 16205's model, bigger: 4x4).
 * Vote spawns alternate between it and the old vote boss (VoteBossHandler). Like the vote boss it fights everyone at
 * once and doesn't chase:
 * <ul>
 *     <li>Magma Bolt (magic, Vorkath's dragonflame 393 bursting 157) and Boulder Volley (ranged, the Gorilla boulder 856
 *     bursting 305) in turns of five at everyone in range; the matching protection prayer cuts it to a third.</li>
 *     <li>Earthshatter: it leaps and slams the ground -- everyone within three tiles is hit, the floor cracks around it.</li>
 *     <li>Magma Pools: the tiles under everyone glow; three ticks later anyone still on one burns.</li>
 *     <li>Eruption (under half health): ten volleys of falling fireballs across the lair, the first at the target.</li>
 * </ul>
 * Rewards: the vote boss's table and perk points (VoteBoss.table) to everyone who damaged it and is still in the lair.
 * Stats in npcs/combat/Custom_event_bosses.json; npc in .dev/cache-restore-tool/custom_bosses_events.txt.
 */
public class VoteVolcanus extends NPCCombat {

	private static final Projectile MAGMA_BOLT = new Projectile(393, 110, 31, 25, 56, 10, 16, 64);
	private static final Projectile BOULDER = new Projectile(856, 110, 31, 25, 60, 8, 28, 64);
	private static final Projectile FIREBALL_DROP = new Projectile(1481, 150, 0, 0, 120, 0, 0, 0);
	private static final int CAST_ANIM = 1167, HURL_ANIM = 1665, SLAM_ANIM = 7644, QUAKE = 1028, BURST = 157,
		ROCK_HIT = 305, RANGE = 10;

	private final Set<Integer> damagingPlayers = new HashSet<>();
	private int attacks;
	private boolean magic = true, erupting;

	@Override
	public void init() {
		npc.deathEndListener = (entity, killer, killHit) -> {
			VoteBossHandler.boss = null;
			try {
				for (Player p : io.ruin.model.activities.bosses.EventBossArea.players(npc)) {
					if (!damagingPlayers.contains(p.getUserId()))
						continue;
					try {
						rewardPlayer(p);
					} catch (Exception e) {
						System.err.println("[Volcanus vote boss] reward failed for " + p.getName() + ": " + e);
						e.printStackTrace();
					}
				}
			} finally {
				npc.remove();
			}
		};
		npc.hitListener = new HitListener().preDefend(hit -> {
			if (hit.damage > 50)
				hit.damage = 50;
		}).postDamage(hit -> {
			if (hit.damage > 0 && hit.attacker != null && hit.attacker.player != null)
				damagingPlayers.add(hit.attacker.player.getUserId());
		});
	}

	private void rewardPlayer(Player player) {
		player.voteBossKills.increment(player);
		io.ruin.model.content.codex.ZelusTask.EARTHSHAKERS_DOWNFALL.increment(player, 1);
		int perkPoints = VoteHandler.playerVotedRecently(player) ? 2 : 1;
		player.perkPoints += perkPoints;
		player.sendMessage(Color.RED.wrap("Your reward has been dropped to your feet! (+" + perkPoints + " perk points from Volcanus)"));
		Item reward = VoteBoss.table.rollItem();
		new GroundItem(reward).owner(player).position(player.getPosition()).loot().spawn();
		if (reward.lootBroadcast != null)
			Broadcast.WORLD.sendNewsDropMessage(player, Icon.RARE_DROP, "<col=000000>" + player.getName(),
				" just received " + reward.getDef().descriptiveName + " from Volcanus the Earthshaker!");
	}

	@Override
	public void follow() {
		// holds its ground like the vote boss
	}

	@Override
	public boolean attack() {
		List<Player> players = inRange();
		if (players.isEmpty())
			return false;
		if (!erupting && npc.getHp() * 2 < npc.getMaxHp() && Random.get(7) == 0) {
			eruption();
		} else if (Random.get(5) == 0 && anyClose()) {
			earthshatter();
		} else if (Random.get(6) == 0) {
			magmaPools(players);
		} else {
			volley(players);
		}
		return true;
	}

	private List<Player> inRange() {
		List<Player> list = new ArrayList<>();
		for (Player p : npc.localPlayers())
			if (!p.getCombat().isDead() && p.getPosition().isWithinDistance(npc.getPosition(), RANGE))
				list.add(p);
		return list;
	}

	private boolean anyClose() {
		for (Player p : inRange())
			if (p.getPosition().distance(npc.getCentrePosition()) <= 3)
				return true;
		return false;
	}

	private void volley(List<Player> players) {
		npc.animate(magic ? CAST_ANIM : HURL_ANIM);
		for (Player p : players) {
			int delay = (magic ? MAGMA_BOLT : BOULDER).send(npc, p);
			int max = info.max_damage;
			if (p.getPrayer().isActive(magic ? Prayer.PROTECT_FROM_MAGIC : Prayer.PROTECT_FROM_MISSILES))
				max /= 3;
			Hit hit = new Hit(npc, magic ? AttackStyle.MAGIC : AttackStyle.RANGED).randDamage(max).ignorePrayer().clientDelay(delay);
			int splash = magic ? BURST : ROCK_HIT;
			hit.afterPostDamage(t -> t.graphics(splash, 0, 0));
			p.hit(hit);
		}
		if (++attacks >= 5) {
			attacks = 0;
			magic = !magic;
		}
	}

	private void earthshatter() {
		npc.animate(SLAM_ANIM);
		npc.forceText("The earth breaks!");
		npc.addEvent(e -> {
			e.delay(2);
			Position c = npc.getCentrePosition();
			for (int[] d : new int[][]{{0, 0}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}})
				World.sendGraphics(QUAKE, 0, 0, c.relative(d[0], d[1]));
			for (Player p : inRange())
				if (p.getPosition().distance(c) <= 3)
					p.hit(new Hit(npc, AttackStyle.CRUSH).randDamage(30, 50).ignorePrayer());
		});
	}

	private void magmaPools(List<Player> players) {
		npc.animate(CAST_ANIM);
		npc.forceText("Burn!");
		List<Position> marked = new ArrayList<>();
		for (Player p : players) {
			Position at = p.getPosition().copy();
			marked.add(at);
			World.sendGraphics(QUAKE, 0, 0, at);
		}
		npc.addEvent(e -> {
			e.delay(3);
			for (Position at : marked)
				World.sendGraphics(BURST, 0, 0, at);
			for (Player p : inRange())
				for (Position at : marked)
					if (p.getPosition().equals(at))
						p.hit(new Hit(npc, AttackStyle.MAGIC).randDamage(20, 35).ignorePrayer().ignoreDefence());
		});
	}

	private void eruption() {
		erupting = true;
		npc.animate(CAST_ANIM);
		npc.forceText("ERUPT!");
		Bounds lair = new Bounds(npc.getAbsX() - 8, npc.getAbsY() - 8, npc.getAbsX() + 8, npc.getAbsY() + 8, npc.getHeight());
		npc.addEvent(e -> {
			for (int wave = 0; wave < 10 && !isDead(); wave++) {
				List<Position> impacts = new ArrayList<>();
				int flight = 0;
				for (int i = 0; i < 8; i++) {
					Position pos = i == 0 && target != null ? target.getPosition().copy() : lair.randomPosition();
					flight = FIREBALL_DROP.send(pos.relative(1, 0), pos);
					impacts.add(pos);
				}
				e.delay(Math.max(1, flight / 25));
				for (Position pos : impacts) {
					World.sendGraphics(BURST, 0, 0, pos);
					for (Player p : inRange()) {
						int d = p.getPosition().distance(pos);
						if (d <= 1)
							p.hit(new Hit(npc).randDamage(d == 0 ? 22 : 11));
					}
				}
				e.delay(3);
			}
			erupting = false;
		});
	}

	@Override
	public void process() {
	}
}

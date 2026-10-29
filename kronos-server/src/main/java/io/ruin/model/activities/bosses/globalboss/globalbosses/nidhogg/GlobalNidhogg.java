package io.ruin.model.activities.bosses.globalboss.globalbosses.nidhogg;

import io.ruin.model.activities.bosses.globalboss.GlobalBossRewards;
import io.ruin.api.utils.Random;
import io.ruin.cache.Color;
import io.ruin.cache.Icon;
import io.ruin.cache.ItemID;
import io.ruin.model.VoteHandler;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.item.Item;
import io.ruin.model.item.loot.LootItem;
import io.ruin.model.item.loot.LootTable;
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
 * Nidhogg the Abyssal Serpent as a ::gb global boss (npc 16215, 2026-10-19; the custom boss 16214's model and rig,
 * bigger). It never moves: it rises in the middle of the arena and fights everyone around it at once, so players have
 * to work together. Every attack hits every player in range:
 * <ul>
 *     <li>Infernal Fire Blast (magic, Fire Surge 1465 / 1466) and Venom Spit (ranged, Zulrah's 1044) in turns of five;
 *     the matching protection prayer cuts it to a third.</li>
 *     <li>Venom Rain: the tiles under everyone sour (warning splash), three ticks later anyone still standing there is
 *     hit and envenomed -- keep moving.</li>
 *     <li>Coil Crush: it coils, and two ticks later everyone within three tiles of it is crushed -- melee must step out.</li>
 *     <li>Abyssal Brood: snakelings rise around the players -- once under half health, once more under a quarter.</li>
 * </ul>
 * Rewards: every player who damaged it and is still in view gets 4-7 rolls on an upgraded global-boss table plus perk
 * points. Stats in npcs/combat/Custom_event_bosses.json; npc in .dev/cache-restore-tool/custom_bosses_events.txt.
 */
public class GlobalNidhogg extends NPCCombat {

	/** Public for the drop viewer (LootsTables.NIDHOGG, shown on examine). */
	public static final LootTable table = new LootTable().addTable(1,
		new LootItem(ItemID.RANARR_SEED, 20, 150),
		new LootItem(ItemID.SNAPDRAGON_SEED, 15, 150),
		new LootItem(ItemID.TORSTOL_SEED, 12, 150),
		new LootItem(ItemID.RUNITE_BAR + 1, 150, 150),
		new LootItem(ItemID.MANTA_RAY + 1, 100, 150, 150),
		new LootItem(ItemID.GRIMY_TORSTOL + 1, 25, 40, 150),
		new LootItem(ItemID.MAGIC_LOGS + 1, 100, 160, 150),
		new LootItem(ItemID.DRAGON_BONES + 1, 70, 110, 90),
		new LootItem(ItemID.RUNITE_ORE + 1, 50, 80, 90),
		new LootItem(ItemID.ANGLERFISH + 1, 80, 140, 90),
		new LootItem(ItemID.SUPER_RESTORE4 + 1, 30, 50, 90),
		new LootItem(ItemID.SARADOMIN_BREW4 + 1, 30, 40, 90),
		new LootItem(ItemID.INSTANCE_TOKEN, 4, 7, 90),
		new LootItem(ItemID.PERK_TASK_SKIP_SCROLL, 2, 3, 90),
		new LootItem(ItemID.SLAYER_TASK_PICK_SCROLL, 2, 3, 90),
		new LootItem(ItemID.HIGH_TIER_MBOX, 1, 3, 90),
		new LootItem(ItemID.ROBINS_DIARY, 1, 1, 8).broadcast(Broadcast.GLOBAL),
		new LootItem(ItemID.UNCUT_ONYX, 1, 1, 5).broadcast(Broadcast.GLOBAL),
		new LootItem(ItemID.DONATOR_MYSTERY_BOX, 1, 1, 4).broadcast(Broadcast.GLOBAL),
		new LootItem(30462, 1, 1, 1).broadcast(Broadcast.GLOBAL), // Advanced donator mystery box
		new LootItem(ItemID.FIVE_BOND, 1, 1, 3).broadcast(Broadcast.GLOBAL)
	);

	private static final Projectile FIRE_BLAST = new Projectile(1465, 88, 31, 20, 56, 10, 16, 64);
	private static final Projectile VENOM_SPIT = new Projectile(1044, 88, 31, 20, 56, 10, 16, 64);
	private static final int ATTACK_ANIM = 16232, FIRE_HIT = 1466, VENOM_WARNING = 1045, SNAKELING = 2045, RANGE = 12;

	/** Account ids that damaged it (a relog keeps the credit). */
	private final Set<Integer> damagingPlayers = new HashSet<>();
	private final List<NPC> brood = new ArrayList<>();
	private int attacks;
	private boolean magic = true;
	private int broodWaves;

	@Override
	public void init() {
		npc.deathEndListener = (entity, killer, killHit) -> {
			try {
				GlobalBossRewards.payDamagers("Nidhogg the Abyssal Serpent", damagingPlayers, table, 4, 7, p -> {
					p.globalBossKills.increment(p);
					io.ruin.model.content.codex.ZelusTask.ABYSSAL_CONQUEROR.increment(p, 1);
					int perkPoints = VoteHandler.playerVotedRecently(p) ? 3 : 2;
					p.perkPoints += perkPoints;
					p.sendMessage(Color.RED.wrap("+" + perkPoints + " perk points from Nidhogg"));
				});
			} finally {
				brood.forEach(n -> { if (!n.isRemoved()) n.remove(); });
				npc.remove();
			}
		};
		npc.hitListener = new HitListener().preDefend(hit -> {
			io.ruin.model.activities.bosses.globalboss.GlobalBossHandler.scaleHit(npc, hit); // cap 50 + few-players scaling
		}).postDamage(hit -> {
			if (hit.damage > 0 && hit.attacker != null && hit.attacker.player != null)
				damagingPlayers.add(hit.attacker.player.getUserId());
		});
	}

	@Override
	public void follow() {
		// rooted in the middle of the arena
	}

	@Override
	public boolean attack() {
		List<Player> players = inRange();
		if (players.isEmpty())
			return false;
		boolean broodDue = brood.isEmpty() && (broodWaves == 0 && npc.getHp() * 2 < npc.getMaxHp()
			|| broodWaves == 1 && npc.getHp() * 4 < npc.getMaxHp());
		if (broodDue) {
			abyssalBrood(players);
		} else if (Random.get(6) == 0) {
			venomRain(players);
		} else if (Random.get(5) == 0 && anyClose()) {
			coilCrush();
		} else {
			blast(players);
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

	/** The normal attack: five fire blasts, then five venom spits, at everyone in range. */
	private void blast(List<Player> players) {
		npc.animate(ATTACK_ANIM);
		for (Player p : players) {
			int delay = (magic ? FIRE_BLAST : VENOM_SPIT).send(npc, p);
			int max = info.max_damage;
			if (p.getPrayer().isActive(magic ? Prayer.PROTECT_FROM_MAGIC : Prayer.PROTECT_FROM_MISSILES))
				max /= 3;
			Hit hit = new Hit(npc, magic ? AttackStyle.MAGIC : AttackStyle.RANGED).randDamage(max).ignorePrayer().clientDelay(delay);
			if (magic)
				hit.afterPostDamage(t -> t.graphics(FIRE_HIT, 124, 0));
			p.hit(hit);
		}
		if (++attacks >= 5) {
			attacks = 0;
			magic = !magic;
		}
	}

	private void venomRain(List<Player> players) {
		npc.animate(ATTACK_ANIM);
		npc.forceText("Drown in venom!");
		List<Position> marked = new ArrayList<>();
		for (Player p : players) {
			Position at = p.getPosition().copy();
			marked.add(at);
			World.sendGraphics(VENOM_WARNING, 0, 0, at);
		}
		npc.addEvent(e -> {
			e.delay(3);
			for (Player p : inRange())
				for (Position at : marked)
					if (p.getPosition().equals(at)) {
						p.hit(new Hit(npc, AttackStyle.MAGIC).randDamage(12, 20).ignorePrayer().ignoreDefence());
						p.envenom(6);
					}
		});
	}

	private void coilCrush() {
		npc.animate(ATTACK_ANIM);
		npc.forceText("Coil and crush!");
		npc.addEvent(e -> {
			e.delay(2);
			for (Player p : inRange())
				if (p.getPosition().distance(npc.getCentrePosition()) <= 3)
					p.hit(new Hit(npc, AttackStyle.CRUSH).randDamage(15, 22).ignorePrayer());
		});
	}

	private void abyssalBrood(List<Player> players) {
		broodWaves++;
		npc.animate(ATTACK_ANIM);
		npc.forceText("My brood, rise!");
		int count = Math.min(4, Math.max(2, players.size() / 2));
		for (int i = 0; i < count; i++) {
			Player p = players.get(i % players.size());
			NPC snakeling = new NPC(SNAKELING).spawn(p.getAbsX() + Random.get(-2, 2), p.getAbsY() + Random.get(-2, 2), p.getHeight(), 3);
			snakeling.getCombat().setAllowRespawn(false);
			snakeling.getCombat().setTarget(p);
			brood.add(snakeling);
		}
	}

	@Override
	public void process() {
		brood.removeIf(NPC::isRemoved);
	}
}

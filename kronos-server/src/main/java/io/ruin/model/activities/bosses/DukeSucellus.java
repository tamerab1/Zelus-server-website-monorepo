package io.ruin.model.activities.bosses;

import io.ruin.api.utils.Random;
import io.ruin.cache.Color;
import io.ruin.model.World;
import io.ruin.model.activities.ActivityTimer;
import io.ruin.model.activities.bosses.instancetoken.maphandlers.DukeMapHandler;
import io.ruin.model.activities.perktree.Perks;
import io.ruin.model.activities.perktree.perks.ThePetHunter;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.HitType;
import io.ruin.model.content.combatachievements.CombatAchievements;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCAction;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.actions.ItemItemAction;
import io.ruin.model.item.actions.ItemNPCAction;
import io.ruin.model.item.actions.impl.pet.Pet;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.model.map.Tile;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.map.object.actions.ObjectAction;
import io.ruin.model.skills.mining.Pickaxe;
import io.ruin.model.skills.prayer.Prayer;
import io.ruin.model.skills.slayer.Slayer;
import io.ruin.model.skills.slayer.master.BossMaster;
import io.ruin.model.stat.StatType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Duke Sucellus (Ghorrock Prison asylum), OSRS 1:1 mechanics with Karn's HP pool (2000 instead of 485, so every
 * HP threshold below is a percentage of max HP rather than the OSRS absolute value).
 * <p>
 * Preparation: he sleeps (12167) until fed two arder-musca poisons. Players pick arder/musca mushrooms, grind them
 * with the asylum's pestle and mortar, mine salax salt with its pickaxe, and ferment 6 of each in a fermentation vat.
 * The hall fights back while he sleeps: three gas vents cycle through the centre and the wall extremities gaze down
 * the side corridors.
 * <p>
 * Fight (12191): melee cycle (icicles + slam) in his front row, a magic eye attack otherwise, a freezing gaze every
 * fifth standard attack (hide directly behind a pillar), gas flares at the vent nearest you, and an enrage below 25%.
 * Dying puts him back to sleep (12192 -> 12167), so every kill needs a fresh preparation.
 * <p>
 * All coordinates are written in the static map's terms (Duke's SW tile 3036,6452) and shifted into the instance.
 */
public class DukeSucellus extends NPCCombat {

	public static final int ASLEEP = 12167, AWAKE = 12191, DEFEATED = 12192;

	// items
	public static final int MUSCA_MUSHROOM = 28341, MUSCA_POWDER = 28342, ARDER_MUSHROOM = 28345, ARDER_POWDER = 28346,
		SALAX_SALT = 28349, ARDER_MUSCA_POISON = 28351, PESTLE_AND_MORTAR = 233, IRON_PICKAXE = 1267, GAS_MASK = 1506, FACE_MASK = 4164;
	private static final int[] PREP_ITEMS = { MUSCA_MUSHROOM, MUSCA_POWDER, ARDER_MUSHROOM, ARDER_POWDER, SALAX_SALT, ARDER_MUSCA_POISON };

	// objects: the map holds inert versions, the instance swaps in the usable ones
	private static final int SALT_INERT = 47523, SALT_MINE = 47522;
	private static final int ARDER_INERT = 47534, ARDER_PICK = 47528, MUSCA_INERT = 47532, MUSCA_PICK = 47524;
	private static final int VAT_INERT = 47537, VAT_FILL = 47536, VAT_COLLECT = 47539;
	private static final int PESTLE_INERT = 47567, PESTLE_TAKE = 47560, PICKAXE_INERT = 47568, PICKAXE_TAKE = 47561;
	private static final int[] EXTREMITY_IDLE = { 47544, 47545, 47546 }, EXTREMITY_WAKING = { 47547, 47548, 47549 },
		EXTREMITY_GAZING = { 47550, 47551, 47552 };

	private static final int POISONS_TO_WAKE = 2, POWDER_PER_POISON = 6, MUSHROOMS_PER_CLUSTER = 8;

	// animations / graphics
	/* gameval npc_duke_sucellus01_*: attack_melee 10176, magic_attack 10177, magic_attack_faster 10178 (the flare spit),
	 * sight_attack 10180; vents spotanim_duke_vent_01_spawn 2431 / idle 2432 / despawn 2433 */
	private static final int MELEE_ANIM = 10176, MAGIC_ANIM = 10177, FLARE_ANIM = 10178, GAZE_ANIM = 10180;
	private static final int MELEE_GFX = 2439, ICICLE_GFX = 2440, MAGIC_IMPACT_GFX = 2435, GAS_GFX = 2432, GAS_SPAWN_GFX = 2431;
	private static final Projectile MAGIC_PROJECTILE = new Projectile(2434, 65, 0, 20, 72, 0, 18, 10);
	private static final Projectile FLARE_PROJECTILE = new Projectile(2436, 65, 0, 20, 72, 14, 18, 10);

	// OSRS max hits (balance rule: Karn HP, OSRS hits)
	private static final int ICICLE_MAX = 11, ICICLE_MAX_PRAYED = 5, SLAM_MAX = 56, SLAM_MAX_PRAYED = 42,
		MAGIC_MAX = 48, MAGIC_MAX_PRAYED = 36, GAZE_MAX = 101, GAS_MAX = 12, GAS_MAX_MASKED = 4,
		EXTREMITY_MAX = 70, PREP_VENT_MAX = 12, ICE_FALL_MAX = 18, ICE_FALL_GFX = 1014;

	/* ------------------------------------------------------------------ state */

	private int poisonsFed = 0;
	private int arderLeft = MUSHROOMS_PER_CLUSTER, muscaLeft = MUSHROOMS_PER_CLUSTER;
	private boolean prepObjectsReady = false;

	private int standardAttacks = 0;
	private int attacksSinceFlare = 0;
	private boolean gazedOnce = false, lastWasGaze = false;
	private int nextAttackDelay = 5;

	/** active gas clouds: centre tile + tick it ends */
	private final List<long[]> gasClouds = new ArrayList<>();
	private int lastGasHitTick = -10;

	private int prepTick = 0;

	/** OSRS's boss health bar for everyone in the asylum while he's awake */
	private final io.ruin.model.entity.BossHudTracker hud = new io.ruin.model.entity.BossHudTracker();

	boolean timerStarted = false;
	boolean playerRan = false;
	boolean damagedPlayer = false;

	/* ------------------------------------------------------------------ coordinates */

	private int dx() { return npc.spawnPosition.getX() - 3036; }
	private int dy() { return npc.spawnPosition.getY() - 6452; }
	private Position at(int x, int y, int z) { return new Position(x + dx(), y + dy(), z); }
	private int mapX(Position p) { return p.getX() - dx(); }
	private int mapY(Position p) { return p.getY() - dy(); }

	private static final int[][] PILLAR_SPOTS = { { 3035, 6451 }, { 3035, 6447 }, { 3035, 6443 }, { 3043, 6451 }, { 3043, 6447 }, { 3043, 6443 } };
	private static final int[] VENT_X = { 3036, 3039, 3042 }, VENT_Y = { 6442, 6446, 6450 };
	/** extremities: {x, y, index into EXTREMITY_* arrays}; wake order per side is middle, south, north */
	private static final int[][] EXTREMITIES_WEST = { { 3028, 6445, 0 }, { 3028, 6441, 1 }, { 3028, 6449, 2 } };
	private static final int[][] EXTREMITIES_EAST = { { 3049, 6445, 2 }, { 3049, 6441, 0 }, { 3049, 6449, 1 } };

	private boolean onPillarSpot(Player p) {
		int x = mapX(p.getPosition()), y = mapY(p.getPosition());
		for (int[] s : PILLAR_SPOTS)
			if (s[0] == x && s[1] == y)
				return true;
		return false;
	}

	/** his front row, diagonals included */
	private boolean inMeleeZone(Player p) {
		int x = mapX(p.getPosition()), y = mapY(p.getPosition());
		return p.getHeight() == npc.getHeight() && y == 6451 && x >= 3035 && x <= 3043;
	}

	private List<Player> players() {
		List<Player> list = new ArrayList<>();
		for (Player p : npc.localPlayers())
			if (p != null && !p.getCombat().isDead() && p.getHeight() == npc.getHeight() && p.getPosition().isWithinDistance(npc.getPosition(), 32))
				list.add(p);
		return list;
	}

	private static boolean faceCovered(Player p) {
		int hat = p.getEquipment().getId(Equipment.SLOT_HAT);
		return hat == GAS_MASK || hat == FACE_MASK || Slayer.hasSlayerHelmEquipped(p);
	}

	private double hpRatio() {
		return npc.getMaxHp() <= 0 ? 0 : (double) npc.getHp() / npc.getMaxHp();
	}

	/* ------------------------------------------------------------------ lifecycle */

	@Override
	public void init() {
		npc.set("DUKE_COMBAT", this);
	}

	@Override
	public void follow() {
		// he never moves
	}

	private void readyPrepObjects() {
		if (prepObjectsReady)
			return;
		prepObjectsReady = true;
		swap(SALT_INERT, SALT_MINE, 3033, 6448, 0);
		swap(SALT_INERT, SALT_MINE, 3043, 6448, 0);
		swap(ARDER_INERT, ARDER_PICK, 3030, 6453, 0);
		swap(MUSCA_INERT, MUSCA_PICK, 3048, 6453, 0);
		swap(VAT_INERT, VAT_FILL, 3034, 6438, 0);
		swap(VAT_INERT, VAT_FILL, 3043, 6438, 0);
		swap(PESTLE_INERT, PESTLE_TAKE, 3034, 6434, 0);
		swap(PICKAXE_INERT, PICKAXE_TAKE, 3044, 6434, 0);
	}

	private void swap(int from, int to, int x, int y, int z) {
		Position p = at(x, y, z);
		GameObject obj = Tile.getObject(from, p.getX(), p.getY(), p.getZ());
		if (obj != null)
			obj.setId(to);
	}

	/** Back to sleep after a kill: a fresh preparation is needed for the next one. */
	private void resetForNextKill() {
		poisonsFed = 0;
		arderLeft = MUSHROOMS_PER_CLUSTER;
		muscaLeft = MUSHROOMS_PER_CLUSTER;
		standardAttacks = 0;
		attacksSinceFlare = 0;
		gazedOnce = false;
		lastWasGaze = false;
		gasClouds.clear();
		timerStarted = false;
		playerRan = false;
		damagedPlayer = false;
	}

	@Override
	public void process() {
		if (npc.getId() == ASLEEP) {
			hud.closeAll();
			readyPrepObjects();
			processPrepHazards();
			return;
		}
		if (npc.getId() != AWAKE) {
			hud.closeAll();
			return;
		}
		hud.update(players(), AWAKE, npc.getHp(), npc.getMaxHp());
		for (Player p : players()) {
			if (io.ruin.model.var.VarPlayerRepository.RUNNING.get(p) == 1)
				playerRan = true;
		}
		processGas();
	}

	/* ------------------------------------------------------------------ preparation hazards */

	private void processPrepHazards() {
		List<Player> players = players();
		if (players.isEmpty())
			return;
		prepTick++;

		// Vents: one live vent per row, handing off to the one east of it (looping) every 8 ticks.
		if (prepTick % 8 == 1) {
			gasClouds.clear();
			int step = prepTick / 8;
			for (int row = 0; row < 3; row++) {
				int col = (step + row) % 3;
				Position vent = at(VENT_X[col], VENT_Y[row], 0);
				gasClouds.add(new long[] { vent.getX(), vent.getY(), io.ruin.Server.currentTick() + 8 });
				World.sendGraphics(GAS_SPAWN_GFX, 0, 0, vent);
			}
		}
		for (Player p : players) {
			for (long[] c : gasClouds) {
				if (Math.abs(p.getPosition().getX() - c[0]) <= 1 && Math.abs(p.getPosition().getY() - c[1]) <= 1 && prepTick % 2 == 0) {
					p.getPrayer().drain(4);
					p.hit(new Hit(HitType.POISON).randDamage(faceCovered(p) ? PREP_VENT_MAX / 3 : PREP_VENT_MAX));
					break;
				}
			}
		}

		// Ice falls (wiki): in the mushroom corridors past the extremities, a shadow looms over a tile and an icicle
		// drops on it - frozen 2 ticks and up to 18 damage if you're still there. (Graphic: the cache has no Duke-
		// specific ice fall, so the stock falling icicle 1014 is used.)
		if (prepTick % 3 == 0) {
			for (Player p : players) {
				if (p.getHeight() != 1 || Random.get(1) == 0)
					continue;
				int x = mapX(p.getPosition());
				if (x > 3026 && x < 3051)
					continue;
				Position tile = p.getPosition().copy();
				World.sendGraphics(ICE_FALL_GFX, 0, 0, tile);
				npc.addEvent(e -> {
					e.delay(2);
					if (p.getPosition().equals(tile) && !p.getCombat().isDead()) {
						p.freeze(2, npc);
						p.hit(new Hit(HitType.DAMAGE).randDamage(ICE_FALL_MAX));
					}
				});
			}
		}

		// Extremities: every 6 ticks the next one on each side wakes (middle, south, north), gazing 2 ticks later.
		if (prepTick % 6 == 0) {
			int idx = (prepTick / 6) % 3;
			gazeExtremity(EXTREMITIES_WEST[idx], true);
			gazeExtremity(EXTREMITIES_EAST[idx], false);
		}
	}

	private void gazeExtremity(int[] e, boolean west) {
		Position pos = at(e[0], e[1], 1);
		GameObject obj = Tile.getObject(EXTREMITY_IDLE[e[2]], pos.getX(), pos.getY(), 1);
		if (obj == null)
			return;
		obj.setId(EXTREMITY_WAKING[e[2]]);
		npc.addEvent(event -> {
			event.delay(2);
			if (obj.isRemoved())
				return;
			obj.setId(EXTREMITY_GAZING[e[2]]);
			for (Player p : players()) {
				int x = mapX(p.getPosition()), y = mapY(p.getPosition());
				boolean inCorridor = west ? x <= 3034 : x >= 3043;
				if (inCorridor && Math.abs(y - e[1]) <= 1) {
					p.freeze(5, npc); // wiki: frozen for 5 ticks (3 seconds)
					p.getPrayer().drain(p.getStats().get(StatType.Prayer).currentLevel / 4);
					p.hit(new Hit(HitType.DAMAGE).randDamage(EXTREMITY_MAX));
					p.sendMessage(Color.PURPLE.wrap("The extremity's gaze freezes you in place!"));
				}
			}
			event.delay(2);
			if (!obj.isRemoved())
				obj.setId(EXTREMITY_IDLE[e[2]]);
		});
	}

	/* ------------------------------------------------------------------ waking */

	private void feed(Player player) {
		if (npc.getId() != ASLEEP)
			return;
		if (!player.getInventory().contains(ARDER_MUSCA_POISON, 1)) {
			player.sendMessage("You need an arder-musca poison to feed Duke Sucellus.");
			return;
		}
		player.getInventory().remove(ARDER_MUSCA_POISON, 1);
		player.animate(832);
		poisonsFed++;
		if (poisonsFed < POISONS_TO_WAKE) {
			player.sendMessage("You feed Duke Sucellus the poison. He stirs... (" + poisonsFed + "/" + POISONS_TO_WAKE + ")");
			return;
		}
		for (Player p : players()) // OSRS: "Duke Sucellus awakens..." (red game message)
			p.sendMessage(Color.RED.wrap("Duke Sucellus awakens..."));
		gasClouds.clear();
		npc.transform(AWAKE);
		npc.getCombat().restore();
		standardAttacks = 0;
		attacksSinceFlare = 0;
		gazedOnce = false;
		lastWasGaze = false;
		timerStarted = false;
		playerRan = false;
		damagedPlayer = false;
		updateLastAttack(4);
		setTarget(player);
	}

	/* ------------------------------------------------------------------ fight */

	@Override
	public boolean attack() {
		if (npc.getId() != AWAKE || target == null || target.player == null)
			return false;
		if (!timerStarted) {
			timerStarted = true;
			target.player.dukeSucellusTimer = new ActivityTimer();
		}
		int speed = hpRatio() < 0.25 ? 4 : 5; // enrage below 25%

		// freezing gaze: every fifth standard attack
		if (standardAttacks > 0 && standardAttacks % 5 == 0 && !lastWasGaze) {
			if (!gazedOnce) {
				gazedOnce = true;
				attacksSinceFlare = flareInterval() - 3; // first flare always 3 standard attacks after the first gaze
			}
			lastWasGaze = true;
			gaze();
			nextAttackDelay = 6;
			return true;
		}
		// gas flare: never straight after a gaze, only once the first gaze has happened
		if (gazedOnce && !lastWasGaze && attacksSinceFlare >= flareInterval()) {
			attacksSinceFlare = 0;
			gasFlare();
			nextAttackDelay = speed;
			return true;
		}
		lastWasGaze = false;
		standardAttacks++;
		attacksSinceFlare++;
		if (inMeleeZone(target.player))
			meleeCycle(target.player);
		else
			magicAttack(target.player);
		nextAttackDelay = speed;
		return true;
	}

	@Override
	protected void afterAttack() {
		updateLastAttack(nextAttackDelay);
	}

	/** every 5 standard attacks above 75%, every 4 down to 25%, every 3 below */
	private int flareInterval() {
		double r = hpRatio();
		return r > 0.75 ? 5 : r > 0.25 ? 4 : 3;
	}

	private void meleeCycle(Player target) {
		npc.graphics(MELEE_GFX);
		npc.animate(MELEE_ANIM);
		Position slamTile = target.getPosition().copy();
		for (int x = 3035; x <= 3043; x++)
			World.sendGraphics(ICICLE_GFX, 0, 0, at(x, 6451, npc.getHeight()));
		npc.addEvent(event -> {
			event.delay(2);
			if (npc.getHp() <= 0 || npc.getId() != AWAKE)
				return;
			for (Player p : players()) {
				// icicles: 3x3 around the slam tile, chip damage that doesn't spoil a perfect kill
				if (Math.abs(p.getPosition().getX() - slamTile.getX()) <= 1 && Math.abs(p.getPosition().getY() - slamTile.getY()) <= 1) {
					boolean prayed = p.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE);
					p.hit(new Hit(npc, AttackStyle.CRUSH).randDamage(prayed ? ICICLE_MAX_PRAYED : ICICLE_MAX).ignoreDefence().ignorePrayer());
				}
				// slam: the exact tile, always hits; it can't reach a pillar spot (diagonal)
				if (p.getPosition().equals(slamTile) && !onPillarSpot(p)) {
					boolean prayed = p.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE);
					p.hit(new Hit(npc, AttackStyle.CRUSH).randDamage(prayed ? SLAM_MAX_PRAYED : SLAM_MAX).ignoreDefence().ignorePrayer());
					damagedPlayer = true;
				}
			}
		});
	}

	private void magicAttack(Player target) {
		npc.animate(MAGIC_ANIM);
		npc.addEvent(event -> {
			event.delay(1);
			int delay = MAGIC_PROJECTILE.send(npc, target);
			event.delay(World.getTicks(delay) + 1);
			if (npc.getHp() <= 0 || npc.getId() != AWAKE || target.getCombat().isDead())
				return;
			boolean prayed = target.getPrayer().isActive(Prayer.PROTECT_FROM_MAGIC);
			Hit hit = new Hit(npc, AttackStyle.MAGIC).randDamage(prayed ? MAGIC_MAX_PRAYED : MAGIC_MAX).ignorePrayer();
			target.hit(hit);
			target.graphics(MAGIC_IMPACT_GFX, 22, 0);
			damagedPlayer = true; // any landed magic attack disqualifies a perfect kill
		});
	}

	private void gaze() {
		npc.animate(GAZE_ANIM);
		for (Player p : players())
			p.sendMessage(Color.PURPLE.wrap("Duke Sucellus turns his gaze upon you..."));
		npc.addEvent(event -> {
			event.delay(5);
			if (npc.getHp() <= 0 || npc.getId() != AWAKE)
				return;
			for (Player p : players()) {
				if (onPillarSpot(p))
					continue;
				p.hit(new Hit(npc, null).randDamage(GAZE_MAX / 2, GAZE_MAX).ignoreDefence().ignorePrayer());
				damagedPlayer = true;
			}
		});
	}

	private void gasFlare() {
		npc.animate(FLARE_ANIM);
		Player p = target.player;
		List<Position> vents = new ArrayList<>();
		for (int vx : VENT_X)
			for (int vy : VENT_Y)
				vents.add(at(vx, vy, npc.getHeight()));
		vents.sort((a, b) -> Integer.compare(a.distance(p.getPosition()), b.distance(p.getPosition())));
		int count = hpRatio() > 0.5 ? 1 : 2;
		npc.addEvent(event -> {
			for (int i = 0; i < count && i < vents.size(); i++) {
				Position vent = vents.get(i);
				int delay = FLARE_PROJECTILE.send(npc, vent);
				event.delay(World.getTicks(delay) + 1);
				if (npc.getId() != AWAKE)
					return;
				World.sendGraphics(GAS_SPAWN_GFX, 0, 0, vent);
				gasClouds.add(new long[] { vent.getX(), vent.getY(), io.ruin.Server.currentTick() + 8 });
			}
		});
	}

	private void processGas() {
		long now = io.ruin.Server.currentTick();
		gasClouds.removeIf(c -> c[2] <= now);
		if (gasClouds.isEmpty())
			return;
		if (now % 3 == 0)
			for (long[] c : gasClouds)
				World.sendGraphics(GAS_GFX, 0, 0, new Position((int) c[0], (int) c[1], npc.getHeight()));
		if (now - lastGasHitTick < 2)
			return;
		boolean hitSomeone = false;
		for (Player p : players()) {
			for (long[] c : gasClouds) {
				if (Math.abs(p.getPosition().getX() - c[0]) <= 1 && Math.abs(p.getPosition().getY() - c[1]) <= 1) {
					p.hit(new Hit(HitType.POISON).randDamage(1, faceCovered(p) ? GAS_MAX_MASKED : GAS_MAX));
					damagedPlayer = true;
					hitSomeone = true;
					break;
				}
			}
		}
		if (hitSomeone)
			lastGasHitTick = (int) now;
	}

	/* ------------------------------------------------------------------ death */

	@Override
	public void startDeath(Hit killHit) {
		if (npc.getId() != AWAKE)
			return;
		Player player = getKiller() == null ? null : getKiller().player;
		for (Player p : players())
			p.getCombat().reset();
		npc.transform(DEFEATED);
		hud.closeAll();
		npc.clearHits();
		setTarget(null);
		gasClouds.clear();
		if (player != null) {
			BossMaster.handleBossSlayerKill(player, npc);
			handleNewDrop(player, npc.getId(), player.getPosition());
			if (npc.getDef().killCounter != null)
				npc.getDef().killCounter.apply(player).increment(player);
			rewardKiller(player);
		}
		npc.addEvent(e -> {
			e.delay(20); // OSRS respawn timer
			npc.transform(ASLEEP);
			npc.getCombat().restore();
			resetForNextKill();
		});
	}

	private void rewardKiller(Player player) {
		float petDropAverage = 950;
		if (player.getPlayerPerkHandler().getActivePerks(player).contains(Perks.THE_PET_HUNTER)) {
			int perkIndex = player.getPlayerPerkHandler().getActivePerkIndex(player, Perks.THE_PET_HUNTER);
			ThePetHunter c = (ThePetHunter) player.getPlayerPerkHandler().getActivePerks(player).get(perkIndex).getPerk(player);
			petDropAverage *= (float) c.getPetChanceBoost();
		}
		if (player.petDropBonus.isDelayed())
			petDropAverage *= 0.8f;
		petDropAverage *= io.ruin.model.content.fortune.StatueOfFortune.petChanceMultiplier();
		petDropAverage *= getPetDonatorBoost(player);

		if (player.dukeSucellusTimer != null)
			player.dukeSucellusBestTime = player.dukeSucellusTimer.stop(player, player.dukeSucellusBestTime);

		checkAchievement(player, CombatAchievements.DUKE_SUCELLUS_ADEPT);
		checkAchievement(player, CombatAchievements.DUKE_SUCELLUS_MASTER);
		boolean slayerTask = player.bossSlayerName != null && player.bossSlayerName.equalsIgnoreCase("Duke Sucellus");
		if (!slayerTask) {
			long best = ActivityTimer.timeInSeconds(player.dukeSucellusBestTime);
			if (best < 45)
				checkAchievement(player, CombatAchievements.DUKE_SUCELLUS_SPEED_TRIALIST);
			if (best < 40)
				checkAchievement(player, CombatAchievements.DUKE_SUCELLUS_SPEED_CHASER);
			if (best < 30)
				checkAchievement(player, CombatAchievements.DUKE_SUCELLUS_SPEED_RUNNER);
		}
		if (Random.get(0, (int) petDropAverage) == 0)
			Pet.DUKE.unlock(player, AWAKE);
		if (!playerRan && !damagedPlayer)
			checkAchievement(player, CombatAchievements.COLD_FEET);
		if (!damagedPlayer) {
			player.perfectDukeKills++;
			if (player.perfectDukeKills >= 5)
				checkAchievement(player, CombatAchievements.PERFECT_DUKE_SUCELLUS);
		} else {
			player.perfectDukeKills = 0;
		}
	}

	private static void checkAchievement(Player player, CombatAchievements ca) {
		Objects.requireNonNull(player.combatAchievementsList.get(player.getCombatAchievementIndexByOrdinal(ca.ordinal()))
			.getCombatAchievement()).check(player);
	}

	/* ------------------------------------------------------------------ preparation actions */

	private static DukeSucellus duke(Player player) {
		if (!(player.currentMapHandler instanceof DukeMapHandler handler))
			return null;
		for (NPC n : handler.getNpcs()) {
			DukeSucellus d = n.get("DUKE_COMBAT");
			if (d != null)
				return d;
		}
		return null;
	}

	/** Called when a player leaves the asylum: the preparation supplies don't leave with them. */
	public static void removePrepItems(Player player) {
		for (int id : PREP_ITEMS) {
			int n = player.getInventory().count(id);
			if (n > 0)
				player.getInventory().remove(id, n);
		}
	}

	private static int powderPerMushroom(Player player) {
		int lvl = player.getStats().get(StatType.Herblore).currentLevel;
		return lvl >= 80 ? 6 : lvl >= 70 ? 5 : 4;
	}

	private static int saltPerHarvest(Player player) {
		int lvl = player.getStats().get(StatType.Mining).currentLevel;
		return lvl >= 80 ? 6 : lvl >= 75 ? 5 : lvl >= 70 ? 4 : 3;
	}

	private static void pickMushroom(Player player, boolean arder) {
		DukeSucellus d = duke(player);
		if (d == null)
			return;
		player.startEvent(event -> {
			while (true) {
				int left = arder ? d.arderLeft : d.muscaLeft;
				if (left <= 0) {
					player.sendMessage("The cluster has been picked clean. It will regrow once Duke Sucellus has woken.");
					return;
				}
				if (player.getInventory().isFull()) {
					player.sendMessage("Your inventory is too full to pick any more mushrooms.");
					return;
				}
				player.animate(827);
				event.delay(2);
				player.getInventory().add(arder ? ARDER_MUSHROOM : MUSCA_MUSHROOM, 1);
				if (arder)
					d.arderLeft--;
				else
					d.muscaLeft--;
			}
		});
	}

	private static void grind(Player player, int mushroom, int powder) {
		player.startEvent(event -> {
			while (player.getInventory().contains(mushroom, 1) && player.getInventory().contains(PESTLE_AND_MORTAR, 1)) {
				player.animate(364);
				event.delay(2);
				player.getInventory().remove(mushroom, 1);
				player.getInventory().add(powder, powderPerMushroom(player));
			}
		});
	}

	private static void mineSalt(Player player) {
		Pickaxe pickaxe = Pickaxe.find(player);
		if (pickaxe == null) {
			player.sendMessage("You need a pickaxe to mine the salt. There's one on the wall by the entrance.");
			return;
		}
		player.startEvent(event -> {
			for (int harvests = 0; harvests < 75; harvests++) { // about five minutes
				if (player.getInventory().isFull() && !player.getInventory().contains(SALAX_SALT, 1)) {
					player.sendMessage("Your inventory is too full to hold any salt.");
					return;
				}
				player.animate(pickaxe.regularAnimationID);
				event.delay(4);
				player.getInventory().add(SALAX_SALT, saltPerHarvest(player));
			}
		});
	}

	private static void fillVat(Player player, GameObject vat) {
		if (!player.getInventory().contains(ARDER_POWDER, POWDER_PER_POISON) || !player.getInventory().contains(MUSCA_POWDER, POWDER_PER_POISON)
			|| !player.getInventory().contains(SALAX_SALT, POWDER_PER_POISON)) {
			player.sendMessage("You need " + POWDER_PER_POISON + " arder powder, " + POWDER_PER_POISON + " musca powder and "
				+ POWDER_PER_POISON + " salax salt to make an arder-musca poison.");
			return;
		}
		player.getInventory().remove(ARDER_POWDER, POWDER_PER_POISON);
		player.getInventory().remove(MUSCA_POWDER, POWDER_PER_POISON);
		player.getInventory().remove(SALAX_SALT, POWDER_PER_POISON);
		player.animate(832);
		player.sendMessage("The mixture in the vat begins to ferment.");
		vat.setId(VAT_INERT);
		World.startEvent(event -> {
			event.delay(10);
			if (!vat.isRemoved()) {
				vat.setId(VAT_COLLECT);
				if (player.isOnline())
					player.sendMessage(Color.DARK_GREEN.wrap("A fermentation vat is ready to be emptied."));
			}
		});
	}

	private static void collectVat(Player player, GameObject vat) {
		if (player.getInventory().isFull()) {
			player.sendMessage("You need a free inventory slot to collect the poison.");
			return;
		}
		player.animate(832);
		vat.setId(VAT_FILL);
		player.getInventory().add(ARDER_MUSCA_POISON, 1);
		player.sendMessage("You collect some poison from the vat.");
	}

	public static void register() {
		ObjectAction.register(ARDER_PICK, "Pick", (player, obj) -> pickMushroom(player, true));
		ObjectAction.register(MUSCA_PICK, "Pick", (player, obj) -> pickMushroom(player, false));
		ObjectAction.register(SALT_MINE, "Mine", (player, obj) -> mineSalt(player));
		ObjectAction.register(VAT_FILL, "Fill", DukeSucellus::fillVat);
		ObjectAction.register(VAT_FILL, "Check", (player, obj) -> player.sendMessage("The vat is empty. It needs "
			+ POWDER_PER_POISON + " arder powder, " + POWDER_PER_POISON + " musca powder and " + POWDER_PER_POISON + " salax salt."));
		ObjectAction.register(VAT_FILL, "Empty", (player, obj) -> player.sendMessage("The vat is already empty."));
		ObjectAction.register(VAT_COLLECT, "Collect-from", DukeSucellus::collectVat);
		ObjectAction.register(PESTLE_TAKE, "Take", (player, obj) -> takeTool(player, PESTLE_AND_MORTAR, "pestle and mortar"));
		ObjectAction.register(PICKAXE_TAKE, "Take", (player, obj) -> takeTool(player, IRON_PICKAXE, "pickaxe"));
		ItemItemAction.register(PESTLE_AND_MORTAR, ARDER_MUSHROOM, (player, a, b) -> grind(player, ARDER_MUSHROOM, ARDER_POWDER));
		ItemItemAction.register(PESTLE_AND_MORTAR, MUSCA_MUSHROOM, (player, a, b) -> grind(player, MUSCA_MUSHROOM, MUSCA_POWDER));
		NPCAction.register(ASLEEP, "Feed", (player, npc) -> {
			DukeSucellus d = npc.get("DUKE_COMBAT");
			if (d != null)
				d.feed(player);
		});
		ItemNPCAction.register(ARDER_MUSCA_POISON, ASLEEP, (player, item, npc) -> {
			DukeSucellus d = npc.get("DUKE_COMBAT");
			if (d != null)
				d.feed(player);
		});
	}

	private static void takeTool(Player player, int item, String name) {
		if (player.getInventory().contains(item, 1)) {
			player.sendMessage("You already have a " + name + ".");
			return;
		}
		if (player.getInventory().isFull()) {
			player.sendMessage("You need a free inventory slot.");
			return;
		}
		player.getInventory().add(item, 1);
		player.sendMessage("You take a " + name + " from the wall.");
	}
}

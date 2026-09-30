package io.ruin.model.activities.minigame;

import io.ruin.api.utils.Random;
import io.ruin.model.World;
import lombok.extern.slf4j.Slf4j;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.map.Direction;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.model.map.Tile;
import io.ruin.model.map.object.GameObject;
import io.ruin.model.skills.prayer.Prayer;
import io.ruin.utility.Misc;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// Standalone version of Tekton (Chambers of Xeric), forked for the David npc mining minigame --
// see io.ruin.model.map.object.actions.impl.DracoRock. Full mechanic preserved (intro sequence,
// melee attack, <50% hp anvil-retreat + lava-splash smithing phase, isAggressive gating) with only
// the raid-room coupling removed:
//   - ChambersOfXeric.getPartySize(npc) HP-scaling and drop-count scaling are gone entirely --
//     solo/standalone, so hitpoints are just whatever Draco.json authors directly.
//   - The anvil-bubble object scan (id 29890, used by shootLava() as projectile source points)
//     is unchanged in mechanism, but since this isn't the real CoX room, actual 29890 objects had
//     to be placed near Draco's arena spawn point for it to find any -- see
//     data/objects/spawns/david_minigame.json.
@Slf4j
public class Draco extends NPCCombat {

	// NOTE: kept under 65536 deliberately -- npc.transform()'s wire encoding (rsprot's
	// NpcTransformationEncoder, `Transformation.id: UShort`) silently wraps mod 65536, and the
	// very first spawn's low-res-change packet clamps to 14 bits (min(16383, id)) besides. The
	// original 100012-100017 ids (this fork's first custom npc ids ever to exceed 65535) were
	// completely invisible in-game because of this -- see reference_npc_invisible_after_recolor_unresolved.
	private static final int DRACO_IDLE = 30560;
	private static final int DRACO_WALK = 30561;
	private static final int DRACO_ATTACK = 30562;
	private static final int DRACO_ENRAGED_1 = 30563;
	private static final int DRACO_ENRAGED_2 = 30564;
	private static final int DRACO_SMITH = 30565;

	private List<Position> bubbles = new ArrayList<>(20);
	private static final Projectile LAVA_PROJECTILE = new Projectile(660, 0, 0, 0, 100, 0, 45, 0);
	private boolean forcedSmith = false;
	public boolean damagedPlayer = false;
	public boolean returnedToAnvil = false;

	@Override
	public void init() {
		int baseX = ((npc.getSpawnPosition().getX() >> 3) & (~3)) << 3; // base X of the arena
		int baseY = ((npc.getSpawnPosition().getY() >> 3) & (~3)) << 3;
		for (int x = 0; x < 32; x++) {
			for (int y = 0; y < 32; y++) {
				GameObject obj = Tile.getObject(29890, baseX + x, baseY + y, npc.getHeight(), 10, -1);
				if (obj != null) {
					bubbles.add(new Position(baseX + x, baseY + y, npc.getHeight()));
				}
			}
		}
		// NOTE: setHeadIcon() is NOT called here -- confirmed project history (NPCCombat init() vs
		// avatar timing) that npc.avatar is still null when init() runs, so setHeadIcon() silently
		// no-ops (just logs a warning) at this point. Deferred to process() instead, see below.
		// Neither this nor MovrethCombat.java (the only other user of this boss-bar HUD system)
		// ever closed it on death -- confirmed via source: no getHealthHud().close() call existed
		// anywhere for either boss, so the bar just stayed on screen forever after a kill.
		npc.deathStartListener = (entity, killer, killHit) -> {
			npc.localPlayers().forEach(p -> p.getHealthHud().close());
			removeWhelps();
		};
		npc.attackNpcListener = (player, npc1, message) -> false;
		npc.addEvent(event -> {
			npc.lock();
			while (npc.localPlayers().size() == 0) {
				event.delay(5);
			}
			npc.animate(7474);
			event.delay(2);
			npc.transform(DRACO_WALK);
			while (npc.localPlayers().size() < 1) event.delay(2);
			Player p = Random.get(npc.localPlayers());
			npc.face(p);
			npc.getRouteFinder().routeEntity(p);
			// Capped -- waitForMovement() has no timeout, so an unreachable/moving target could hang this
			// event forever and leave Draco locked (and, in smith(), stuck on the 0-damage listener).
			event.waitForCondition(() -> npc.getMovement().isAtDestination(), 15);
			event.delay(1);
			npc.animate(7478);
			npc.transform(DRACO_ATTACK);
			event.delay(1);
			target = p;
			npc.attackNpcListener = null;
			npc.faceNone(false);
			npc.unlock();
		});
		npc.hitListener = new HitListener().preDamage(this::preDamage).postDamage(this::postDamage);
	}

	// Draco carries a permanent Protect from Missiles overhead (see the setHeadIcon call in init())
	// -- fully negate ranged damage to match, the same way real overhead-prayer blocking works.
	private void preDamage(Hit hit) {
		if (hit.attackStyle != null && hit.attackStyle.isRanged()) {
			hit.block();
		}
	}

	// Same "show/refresh the boss HP bar" idiom as MovrethCombat.java, plus the pre-existing
	// <50% hp anvil-retreat roll.
	private void postDamage(Hit hit) {
		if (hit.attacker != null && hit.attacker.isPlayer()) {
			if (!hit.attacker.player.getHealthHud().isOpened())
				hit.attacker.player.getHealthHud().open(true, npc.getId(), npc.getMaxHp());
			hit.attacker.player.getHealthHud().updateValue(npc.getHp());
		}
		if (npc.getHp() < npc.getMaxHp() / 2 && !forcedSmith && Random.rollDie(15, 1)) {
			forcedSmith = true;
			returnedToAnvil = true;
			smith();
		}
	}

	@Override
	public void follow() {
		// Unlike the real Chambers of Xeric Tekton (which never leaves its forge), this standalone
		// version needs to actually chase players who kite/range it -- real Tekton's "no follow" was
		// fine there since players choose to melee him at his own anvil, but it left this fork
		// standing still and never attacking anyone who didn't walk into melee range themselves.
		follow(1);
	}

	@Override
	public boolean attack() {
		List<Player> targets = npc.localPlayers().stream().filter(p -> {
			Position pos = Misc.getClosestPosition(npc, p);
			int xDist = Math.abs(pos.getX() - p.getAbsX());
			int yDist = Math.abs(pos.getY() - p.getAbsY());
			int dist = Math.max(xDist, yDist);
			return dist <= 1;
		}).collect(Collectors.toList());
		if (targets.size() == 0) return false;
		Player p = Random.get(targets);
		Direction side;
		Position pos = Misc.getClosestPosition(npc, p);
		int xDiff = pos.getX() - p.getAbsX();
		int yDiff = pos.getY() - p.getAbsY();
		if (xDiff > 0)
			side = Direction.WEST;
		else if (xDiff < 0)
			side = Direction.EAST;
		else if (yDiff > 0)
			side = Direction.SOUTH;
		else
			side = Direction.NORTH;
		npc.face(side);
		npc.animate(7483);
		World.startEvent(event -> {
			event.delay(1);
			if (isDead())
				return;
			for (Player player : npc.localPlayers()) {
				Position src = Misc.getClosestPosition(npc, player);
				if ((side.deltaX != 0 && player.getAbsX() - src.getX() == side.deltaX) ||
					(side.deltaY != 0 && player.getAbsY() - src.getY() == side.deltaY)) {
					// Toned down from real CoX Tekton's 15-50 (no defence roll, prayer only capping it at
					// 24): now rolls accuracy against the player's defence (armour matters, can miss),
					// 0-30, and Protect from Melee caps it at 8. ignorePrayer stays so the generic
					// protect-prayer rule doesn't zero it entirely -- the cap below is the prayer effect.
					int maxDamage = 30;
					if (player.getPrayer().isActive(Prayer.PROTECT_FROM_MELEE))
						maxDamage = 8;
					damagedPlayer = true;
					player.hit(new Hit(npc, AttackStyle.CRUSH).randDamage(maxDamage).ignorePrayer().delay(0));
				}
			}
		});
		return true;
	}

	private void smith() {
		npc.addEvent(event -> {
			event.setCancelCondition(this::isDead);
			npc.lock();
			npc.localPlayers().forEach(plr -> plr.getCombat().reset());
			npc.attackNpcListener = (player, npc1, message) -> false;
			npc.hitListener = new HitListener().preDamage(hit -> hit.damage = 0);
			npc.animate(7479);
			npc.transform(DRACO_WALK);
			npc.faceNone(false);
			npc.forceText("Whelps, defend your master!");
			spawnWhelps();
			event.delay(2);
			npc.getRouteFinder().routeAbsolute(npc.getSpawnPosition().getX(), npc.getSpawnPosition().getY());
			// Capped -- waitForMovement() has no timeout, so an unreachable/moving target could hang this
			// event forever and leave Draco locked (and, in smith(), stuck on the 0-damage listener).
			event.waitForCondition(() -> npc.getMovement().isAtDestination(), 15);
			npc.face(npc.spawnDirection);
			event.delay(1);
			npc.transform(DRACO_SMITH);
			npc.animate(7475);
			for (int i = 0; i < 6; i++) {
				shootLava();
				event.delay(3);
				npc.incrementHp(Math.max((int) (npc.getMaxHp() * 0.005), 1));
			}
			npc.animate(7474);
			event.delay(2);
			npc.transform(DRACO_WALK);
			while (npc.localPlayers().size() < 1) event.delay(2);
			Player p = Random.get(npc.localPlayers());
			npc.face(p);
			npc.getRouteFinder().routeEntity(p);
			// Capped -- waitForMovement() has no timeout, so an unreachable/moving target could hang this
			// event forever and leave Draco locked (and, in smith(), stuck on the 0-damage listener).
			event.waitForCondition(() -> npc.getMovement().isAtDestination(), 15);
			event.delay(1);
			npc.animate(7478);
			npc.transform(DRACO_ATTACK);
			event.delay(1);
			target = p;
			npc.attackNpcListener = null;
			// Restore the normal (ranged-immune + boss-bar + anvil-retreat-roll) listener -- the
			// smithing phase above swaps in a full-immunity listener, which must not become the
			// permanent state once combat resumes.
			npc.hitListener = new HitListener().preDamage(this::preDamage).postDamage(this::postDamage);
			npc.faceNone(false);
			npc.unlock();
		});
	}

	// 2-3 Draco Whelps (npc 30567) join the fight while Draco is off smithing and immune. Same
	// targetPlayer + attackTargetPlayer pattern as DracoRock.spawnWhelp (the generic aggro scan skips
	// idle players and only looks 4 tiles out). They outlive the smithing phase until killed, and
	// are cleared when Draco dies or the player leaves.
	private static final int DRACO_WHELP_ID = 30567;
	private final List<NPC> whelps = new ArrayList<>();

	private void spawnWhelps() {
		List<Player> players = new ArrayList<>(npc.localPlayers());
		if (players.isEmpty())
			return;
		int count = Random.get(2, 3);
		for (int i = 0; i < count; i++) {
			Player player = Random.get(players);
			int x = player.getAbsX() + Random.get(-2, 2);
			int y = player.getAbsY() + Random.get(-2, 2);
			NPC whelp = new NPC(DRACO_WHELP_ID).spawn(x, y, player.getHeight(), 5).targetPlayer(player, false);
			whelp.attackTargetPlayer(() -> !player.isOnline()
					|| !player.getPosition().isWithinDistance(whelp.getPosition()));
			whelps.add(whelp);
		}
	}

	private void removeWhelps() {
		for (NPC whelp : whelps)
			if (!whelp.isRemoved())
				whelp.remove();
		whelps.clear();
	}

	@Override
	public boolean isAggressive() {
		return npc.getId() != DRACO_IDLE && npc.getId() != DRACO_SMITH && !npc.isLocked();
	}

	private void shootLava() {
		for (Player p : npc.localPlayers()) {
			for (int i = 0; i < 2; i++) {
				if (bubbles.isEmpty()) {
					continue;
				}
				Position source = Random.get(bubbles);
				World.startEvent(event -> {
					Position pos = p.getPosition().copy();
					int delay = LAVA_PROJECTILE.send(source.getX(), source.getY(), pos.getX(), pos.getY());
					World.sendGraphics(659, 0, delay, pos);
					event.delay((delay * 25) / 600);
					if (isDead() || npc.isRemoved() || target == null)
						return;
					int distance = Misc.getDistance(p.getPosition(), pos);
					if (distance <= 1) {
						damagedPlayer = true;
						p.hit(new Hit().randDamage(1, distance == 1 ? 12 : 20).delay(0));
					}
				});
			}
		}
	}

	@Override
	public int getRandomDropCount() {
		return 2;
	}

	// "Dragonfall" special: 3 waves of meteors, each wave marking the player's current tile plus a
	// few random tiles around them with a shadow, then a meteor lands on each ~4 ticks later. Anyone
	// still standing on a marked tile takes a heavy unblockable hit -- the counter is to keep moving.
	// Visuals are existing, in-use ids: the meteor + fall timing are VoteBoss's METEOR_DROP_PROJECTILE
	// and impact (157), the tile shadow is Great Olm's ceilingCrystals marker (1447).
	private static final Projectile METEOR_DROP_PROJECTILE = new Projectile(1227, 150, 0, 0, 135, 0, 0, 0);
	private static final int TILE_MARKER_GFX = 1447;
	private static final int METEOR_IMPACT_GFX = 157;
	private static final int DRAGONFALL_WAVES = 3;
	private static final int EXTRA_TILES_PER_PLAYER = 3;
	// Dragonfall fires exactly once at each hp threshold (75%, then 25%), the first tick Draco is
	// at/below it -- not on a repeating timer.
	private static final double[] DRAGONFALL_HP_THRESHOLDS = {0.75, 0.25};
	private int dragonfallsUsed = 0;
	private boolean dragonfallActive = false;

	private void dragonfall() {
		dragonfallActive = true;
		npc.forceText("The sky burns for Draco!");
		npc.addEvent(event -> {
			event.setCancelCondition(this::isDead);
			for (int wave = 0; wave < DRAGONFALL_WAVES; wave++) {
				List<Position> tiles = new ArrayList<>();
				for (Player p : npc.localPlayers()) {
					Position base = p.getPosition();
					addTile(tiles, new Position(base.getX(), base.getY(), base.getZ()));
					for (int i = 0; i < EXTRA_TILES_PER_PLAYER; i++)
						addTile(tiles, new Position(base.getX() + Random.get(-2, 2), base.getY() + Random.get(-2, 2), base.getZ()));
				}
				for (Position tile : tiles) {
					Position src = Random.get() < 0.5 ? tile.relative(1, 0) : tile.relative(0, 1);
					int projDelay = METEOR_DROP_PROJECTILE.send(src, tile);
					World.sendGraphics(TILE_MARKER_GFX, 0, 30, tile);
					World.sendGraphics(METEOR_IMPACT_GFX, 0, projDelay, tile);
					World.startEvent(e -> {
						e.delay(World.getTicks(projDelay) + 1);
						if (isDead() || npc.isRemoved())
							return;
						for (Player p : npc.localPlayers()) {
							Position pos = p.getPosition();
							if (pos.getX() == tile.getX() && pos.getY() == tile.getY() && pos.getZ() == tile.getZ()) {
								damagedPlayer = true;
								p.hit(new Hit(npc).randDamage(30, 50).ignorePrayer().ignoreDefence());
							}
						}
					});
				}
				event.delay(3);
			}
			dragonfallActive = false;
		});
	}

	private static void addTile(List<Position> tiles, Position tile) {
		for (Position t : tiles)
			if (t.getX() == tile.getX() && t.getY() == tile.getY())
				return;
		tiles.add(tile);
	}

	private boolean headIconSet = false;

	@Override
	public void process() {
		if (!headIconSet && npc.avatar() != null) {
			npc.setHeadIcon(NPC.DefaultHeadIconIndex.ProtectFromRanged);
			headIconSet = true;
		}
		// Only while actually fighting -- not during the intro, the anvil-smithing phase (both lock
		// the npc) or while a previous Dragonfall is still landing. If a threshold is crossed while
		// locked, it simply fires as soon as combat resumes.
		if (target != null && !npc.isLocked() && !isDead() && !dragonfallActive
				&& dragonfallsUsed < DRAGONFALL_HP_THRESHOLDS.length
				&& npc.getHp() <= npc.getMaxHp() * DRAGONFALL_HP_THRESHOLDS[dragonfallsUsed]) {
			dragonfallsUsed++;
			dragonfall();
		}
	}
}

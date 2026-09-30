package io.ruin.model.activities.bosses.perkers;

import io.ruin.api.utils.Random;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.map.Graphic;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.model.map.route.routes.DumbRoute;
import io.ruin.utility.Misc;

import java.util.ArrayList;
import java.util.List;

// Shared handler for the Chamber of Ascension: the three Perkers (Azurion, Verdanox, Crimsar --
// 2x-scaled clones of the Arzinian Avatars 1233/1231/1228), Aurelius the Gilded (3x gold ToA
// Baboon Thrower 11713) and his Golden Relics (gold ToA Obelisk 11698). Each boss uses a single
// attack style, picked by npc id. Stats/animations live in Perkers.json.
//
// Fight mechanics (all four bosses):
//  - Style adaptation: after 4-5 landed hits from one combat style the boss switches its overhead
//    prayer to that style. Hits of the protected style deal 0 and reflect 10% back to the player.
//  - Telegraphed floor attack: every 18-24s in combat the player's tile is marked, and 3 ticks later
//    it detonates for 30-40 unless they've stepped off it.
// Aurelius only -- Golden Aegis at 50% hp: invulnerable until both Golden Relics are destroyed,
// then the shield shatters and he's stunned for 3 seconds.
public class Perker extends NPCCombat {

	private static final int MAGE_PERKER = 30568;
	private static final int RANGE_PERKER = 30569;
	private static final int MELEE_PERKER = 30570;
	private static final int AURELIUS = 30571;
	private static final int GOLDEN_RELIC = 30572;

	// Ahrim's fire wave + its impact splash.
	private static final Projectile FIRE_WAVE = new Projectile(156, 70, 31, 51, 56, 10, 16, 64);
	private static final Graphic FIRE_WAVE_HIT = Graphic.builder().id(157).height(100).build();
	// Karil's bolt, raised to leave from a size-2 npc's hands.
	private static final Projectile RANGE_BOLT = new Projectile(27, 70, 36, 41, 51, 5, 5, 11);
	// The real ToA Baboon Thrower projectile (TOA_BABOON_RANGED_TRAVEL_1).
	private static final Projectile BABOON_THROW = new Projectile(2242, 70, 31, 41, 56, 10, 16, 64);

	// Floor attack visuals are the same in-use ids as Draco's Dragonfall: Great Olm's ceiling-crystal
	// tile marker (1447) and the meteor impact splash (157).
	private static final int TILE_MARKER_GFX = 1447;
	private static final int TILE_IMPACT_GFX = 157;
	private static final int FLOOR_DETONATE_TICKS = 3;

	// Combat style indices for the adaptation counters / overhead prayer.
	private static final int MELEE = 0, RANGED = 1, MAGIC = 2;
	private static final NPC.DefaultHeadIconIndex[] PRAYER_ICONS = {
			NPC.DefaultHeadIconIndex.ProtectFromMelee,
			NPC.DefaultHeadIconIndex.ProtectFromRanged,
			NPC.DefaultHeadIconIndex.ProtectFromMagic,
	};
	private static final String[] STYLE_NAMES = {"melee", "ranged", "magic"};

	private final int[] styleHits = new int[3];
	private int protectedStyle = -1;
	private int switchThreshold = Random.get(4, 5);
	private int floorTicks = nextFloorDelay();

	// Aurelius' Golden Aegis.
	private boolean aegisUsed;
	private boolean aegisActive;
	private final List<NPC> relics = new ArrayList<>();

	private boolean isRelic() {
		return npc.getId() == GOLDEN_RELIC;
	}

	@Override
	public void init() {
		if (isRelic())
			return; // relics are passive targets
		npc.hitListener = new HitListener().preDamage(this::preDamage).postDamage(this::postDamage);
		npc.deathStartListener = (entity, killer, killHit) -> resetFight();
	}

	@Override
	public void follow() {
		// Aurelius and his relics never move -- Aurelius only throws at players within range.
		if (npc.getId() == AURELIUS || isRelic())
			return;
		// Perkers only chase a short way from where they spawned (melee 4 tiles, mage/range 2):
		// the chase destination is clamped into that box, so every step stays inside it.
		boolean melee = npc.getId() == MELEE_PERKER;
		int leash = melee ? 4 : 2;
		if (melee ? inMeleeRange() : withinDistance(8))
			return;
		Position spawn = npc.getSpawnPosition();
		int destX = Math.max(spawn.getX() - leash, Math.min(spawn.getX() + leash, target.getAbsX()));
		int destY = Math.max(spawn.getY() - leash, Math.min(spawn.getY() + leash, target.getAbsY()));
		if (npc.getAbsX() != destX || npc.getAbsY() != destY)
			DumbRoute.step(npc, destX, destY);
	}

	@Override
	public boolean attack() {
		switch (npc.getId()) {
			case GOLDEN_RELIC:
				return false;
			case MAGE_PERKER:
				if (!withinDistance(8))
					return false;
				projectileAttack(FIRE_WAVE, info.attack_animation, FIRE_WAVE_HIT, AttackStyle.MAGIC, info.max_damage);
				return true;
			case RANGE_PERKER:
				if (!withinDistance(8))
					return false;
				projectileAttack(RANGE_BOLT, info.attack_animation, AttackStyle.RANGED, info.max_damage);
				return true;
			case AURELIUS:
				if (!withinDistance(8))
					return false;
				projectileAttack(BABOON_THROW, info.attack_animation, AttackStyle.RANGED, info.max_damage);
				return true;
			default:
				if (!inMeleeRange())
					return false;
				basicAttack();
				return true;
		}
	}

	// withinDistance(1) can't be used here: its distance-1 check only verifies adjacency through
	// the route finder's routeEntity, which the leashed DumbRoute.step(x, y) above never sets, so it
	// passed from any range. Instead: the npc tile closest to the target must touch it (diagonals
	// included -- a size-2 npc stepping diagonally at a corner-adjacent player would overlap them).
	private boolean inMeleeRange() {
		if (target == null)
			return false;
		Position closest = Misc.getClosestPosition(npc, target);
		int dx = Math.abs(closest.getX() - target.getAbsX());
		int dy = Math.abs(closest.getY() - target.getAbsY());
		return Math.max(dx, dy) == 1 && npc.getHeight() == target.getHeight();
	}

	/* ------------------------------------------------------------------ */
	/* Style adaptation (overhead prayers)                                */
	/* ------------------------------------------------------------------ */

	private static int styleOf(Hit hit) {
		if (hit.attackStyle == null)
			return -1;
		if (hit.attackStyle.isMelee())
			return MELEE;
		if (hit.attackStyle.isRanged())
			return RANGED;
		if (hit.attackStyle.isMagic())
			return MAGIC;
		return -1;
	}

	private void preDamage(Hit hit) {
		if (aegisActive) {
			hit.block(); // Golden Aegis: fully invulnerable while the relics stand
			return;
		}
		if (protectedStyle < 0 || hit.attacker == null || !hit.attacker.isPlayer() || styleOf(hit) != protectedStyle)
			return;
		// Protected style: no damage, and 10% of what the hit would have done comes back at the player.
		int reflected = hit.damage / 10;
		hit.block();
		if (reflected > 0)
			hit.attacker.hit(new Hit(npc).fixedDamage(reflected));
	}

	private void postDamage(Hit hit) {
		if (hit.attacker == null || !hit.attacker.isPlayer())
			return;
		if (!aegisActive && hit.damage > 0) {
			int style = styleOf(hit);
			if (style >= 0 && style != protectedStyle && ++styleHits[style] >= switchThreshold)
				switchPrayer(style, hit.attacker.player);
		}
		if (npc.getId() == AURELIUS && !aegisUsed && !isDead() && npc.getHp() <= npc.getMaxHp() / 2)
			startAegis();
	}

	private void switchPrayer(int style, Player attacker) {
		protectedStyle = style;
		java.util.Arrays.fill(styleHits, 0);
		switchThreshold = Random.get(4, 5);
		npc.setHeadIcon(PRAYER_ICONS[style]);
		attacker.sendMessage("<col=800000>" + npc.getDef().name + " adapts, and now protects from " + STYLE_NAMES[style] + "!");
	}

	/* ------------------------------------------------------------------ */
	/* Telegraphed floor attack                                           */
	/* ------------------------------------------------------------------ */

	private static int nextFloorDelay() {
		return Random.get(30, 40); // 18-24 seconds
	}

	private void floorAttack(Player player) {
		Position tile = player.getPosition().copy();
		World.sendGraphics(TILE_MARKER_GFX, 0, 0, tile);
		npc.addEvent(event -> {
			event.delay(FLOOR_DETONATE_TICKS);
			if (isDead() || npc.isRemoved())
				return;
			World.sendGraphics(TILE_IMPACT_GFX, 0, 0, tile);
			Position now = player.getPosition();
			if (player.isOnline() && !player.getCombat().isDead() && now.getX() == tile.getX()
					&& now.getY() == tile.getY() && now.getZ() == tile.getZ()) {
				player.hit(new Hit(npc).randDamage(30, 40).ignorePrayer().ignoreDefence());
			}
		});
	}

	/* ------------------------------------------------------------------ */
	/* Aurelius -- Golden Aegis                                           */
	/* ------------------------------------------------------------------ */

	private void startAegis() {
		aegisUsed = true;
		aegisActive = true;
		npc.forceText("My gilded shield cannot be broken while the relics stand!");
		// One relic on each side of Aurelius (size 3), on his own plane.
		Position base = npc.getSpawnPosition();
		int size = npc.getSize();
		spawnRelic(base.getX() - 2, base.getY() + size / 2, base.getZ());
		spawnRelic(base.getX() + size + 1, base.getY() + size / 2, base.getZ());
	}

	private void spawnRelic(int x, int y, int z) {
		relics.add(new NPC(GOLDEN_RELIC).spawn(x, y, z, 0));
	}

	private void checkAegis() {
		if (!aegisActive)
			return;
		for (NPC relic : relics) {
			if (!relic.isRemoved() && !relic.getCombat().isDead())
				return;
		}
		// Both relics down: the shield shatters and Aurelius is stunned for 3 seconds (5 ticks).
		aegisActive = false;
		relics.clear();
		npc.forceText("Impossible... my shield!");
		npc.lock();
		npc.addEvent(event -> {
			event.delay(5);
			npc.unlock();
		});
	}

	private void removeRelics() {
		for (NPC relic : relics) {
			if (!relic.isRemoved())
				relic.remove();
		}
		relics.clear();
	}

	/** Back to a fresh fight: on death, and when the boss is left alone at full health. */
	private void resetFight() {
		java.util.Arrays.fill(styleHits, 0);
		protectedStyle = -1;
		switchThreshold = Random.get(4, 5);
		floorTicks = nextFloorDelay();
		aegisUsed = false;
		aegisActive = false;
		removeRelics();
		if (npc.avatar() != null)
			npc.removeHeadIcon();
	}

	@Override
	public void process() {
		if (isRelic())
			return;
		checkAegis();
		if (target == null || isDead()) {
			// Walked away and the boss healed back up: the next fight starts clean.
			if (!isDead() && (aegisUsed || protectedStyle >= 0) && npc.getHp() >= npc.getMaxHp())
				resetFight();
			return;
		}
		if (npc.isLocked() || target.player == null)
			return;
		if (--floorTicks <= 0) {
			floorTicks = nextFloorDelay();
			if (withinDistance(12))
				floorAttack(target.player);
		}
	}
}

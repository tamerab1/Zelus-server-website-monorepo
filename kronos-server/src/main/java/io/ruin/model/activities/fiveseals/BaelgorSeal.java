package io.ruin.model.activities.fiveseals;

import io.ruin.cache.Color;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.model.map.Tile;
import io.ruin.model.stat.StatType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Seal 2's boss, Baelgor the Felreaver (npc 20001, the customs27 winged arch-demon). Rebuilt 2026-11-17 (user: "the
 * baelgor boss attacks are broken, it should have a clean specials and attack styles"; reported: hits felt unfair /
 * random, he stood idle, the projectiles / gfx looked wrong). Every attack now has one clear tell and one clear answer:
 * <ul>
 * <li><b>Claws</b> (melee, slash) on a player he is really next to (edge-adjacent, never diagonal or from range): his claw
 * swing. Protect from Melee.</li>
 * <li><b>Fel-fire</b> (magic) from any distance: his cast and a green fel fireball (Vorkath's venom breath 1470, impact
 * 1472). Protect from Magic; the prayer is judged when the fireball lands.</li>
 * <li>Every {@link #SPECIAL_EVERY}th attack is a special, taking turns:
 * <ul>
 * <li><b>Fel Brand</b>: "Burn in the fel-fire!" - a fel bomb (Hydra's poison lob 1644) is lobbed at the tile every player
 * in the room is standing on; ~3 ticks later it bursts (1645 on the 3x3) and burns anyone within a tile of it. Prayer
 * doesn't help: step off.</li>
 * <li><b>Hellfire Gust</b> (only when someone is within {@link #GUST_REACH} tiles of him, else a Fel Brand): "Back,
 * mortals!" - he rears up for {@link #GUST_WINDUP} ticks, then a ring of fel flame rolls out a tile a tick to
 * {@link #GUST_REACH} tiles, burning and throwing back anyone it reaches. Walk away when he shouts.</li>
 * </ul></li>
 * <li><b>Fel guard</b>: his overhead shows the one combat style he shrugs off, and it changes on fixed health marks (no
 * hidden counters): Melee above 66%, Ranged from 66%, Magic from 33%. He shouts each change and the party is told.</li>
 * <li><b>Fel Ward</b>, once at 50%: immune while 2-4 Crimson Ankous stand (they spawn on open floor in the room) - or
 * for {@link #WARD_MAX_TICKS} ticks at most. He keeps attacking through it (fel-fire, every 3rd a Fel Brand).</li>
 * </ul>
 * He never stops attacking while a member stands in the room: a target that left the room or died is swapped for the
 * nearest member, and fel-fire has no range limit (a 4x4 body can't be kited out of a 13x12 room).
 * Hitpoints: {@link #BASE_HP} for a solo run, x(1 + 0.85 per extra member).
 */
public class BaelgorSeal extends SealCombat {

	static final int NPC_ID = 20001, BASE_HP = 650, ROOM = 2;
	static final int SEQ_CLAW = 19012, SEQ_CAST = 19013, SEQ_GUST = 19014;
	static final int GFX_FEL_HIT = 1472, GFX_BURST = 1645, GFX_WARD = 1472;
	/**
	 * Claw marks on the player as the claws land (the dragon claws slash). His re-rigged claw swing is small (arm swings
	 * capped at 16 degrees so the mesh doesn't tear), and the user saw melee hits "not showing anything" (2026-11-17).
	 */
	static final int GFX_CLAW = 1171;
	static final int CLAW_MAX = 28, FEL_MAX = 25, BRAND_MAX = 30, GUST_MAX = 25;
	static final int SPECIAL_EVERY = 5, GUST_REACH = 3, GUST_WINDUP = 2, PUSH = 2, WARD_MAX_TICKS = 60, ATTACK_TICKS = 5;
	/** the fel fireball: his chest height to the player's, 1 tick + a cycle-6 step per tile */
	private static final Projectile FEL = new Projectile(1470, 80, 31, 20, 46, 6, 12, 64);
	/** the fel bomb: a high lob onto a tile, ~3 ticks in the air at any range (its shadow is the warning) */
	private static final Projectile BRAND = new Projectile(1644, 90, 0, 15, 95, 0, 40, 64);

	final FiveSealsRun run;
	private AttackStyle guarding;
	private int attacks, specials;
	private boolean wardUsed, warded;
	private final Set<NPC> wardens = new HashSet<>();

	BaelgorSeal(FiveSealsRun run) {
		this.run = run;
	}

	/** the run's hitpoints: {@link #BASE_HP} x the party scaling */
	void scale(int partySize) {
		var hp = getStat(StatType.Hitpoints);
		int max = (int) Math.round(BASE_HP * (1 + 0.85 * Math.max(0, partySize - 1)));
		hp.fixedLevel = max;
		hp.currentLevel = max;
	}

	@Override
	public void init() {
		npc.hitListener = new HitListener().preDamage(this::preDamage);
		guard(AttackStyle.SLASH, null);
	}

	/* ------------------------------------------------------------------ the fel guard and ward */

	private static int bucket(AttackStyle style) {
		return style.isMelee() ? 0 : style.isRanged() ? 1 : 2;
	}

	private void guard(AttackStyle style, String shout) {
		guarding = style;
		npc.setHeadIcon(style.isMelee() ? NPC.DefaultHeadIconIndex.ProtectFromMelee
			: style.isRanged() ? NPC.DefaultHeadIconIndex.ProtectFromRanged : NPC.DefaultHeadIconIndex.ProtectFromMagic);
		if (shout != null) {
			npc.forceText(shout);
			run.broadcast(Color.RED.wrap("Baelgor now guards against " + (style.isMelee() ? "Melee" : style.isRanged() ? "Ranged" : "Magic")
				+ " - switch your attack style."));
		}
	}

	private void preDamage(Hit hit) {
		if (warded || (hit.attackStyle != null && bucket(hit.attackStyle) == bucket(guarding))) {
			hit.block();
			return;
		}
		int max = npc.getMaxHp(), after = npc.getHp() - hit.damage;
		if (after <= max / 3 && !guarding.isMagic())
			guard(AttackStyle.MAGIC, "Your magic feeds the fel!");
		else if (after <= max * 2 / 3 && guarding.isMelee())
			guard(AttackStyle.RANGED, "Arrows? Pathetic!");
		if (!wardUsed && after <= max / 2 && after > 0) {
			wardUsed = true;
			World.startEvent(e -> {
				e.delay(1);
				felWard();
			});
		}
	}

	/** immune; Crimson Ankous at his side; the ward breaks when they fall (or after WARD_MAX_TICKS) */
	private void felWard() {
		if (npc.isRemoved() || isDead())
			return;
		warded = true;
		npc.forceText("Fel guardians, to me!");
		run.broadcast(Color.RED.wrap("Baelgor wraps himself in fel fire! Slay the Crimson Ankous to break his ward."));
		int count = Math.min(4, 2 + run.size / 2);
		List<Position> spots = wardSpots(count);
		for (Position at : spots) {
			NPC guard = SealCombat.spawn(run, FiveSealsRun.ANKOU, at, null);
			wardens.add(guard);
			((SealCombat) guard.getCombat()).onDeath = () -> {
				wardens.removeIf(n -> n.isRemoved() || n.getCombat().isDead());
				if (wardens.isEmpty())
					breakWard("No! My ward!");
			};
			Player t = run.anyIn(ROOM);
			if (t != null)
				((SealCombat) guard.getCombat()).hunt(t);
		}
		if (spots.isEmpty())
			breakWard(null);
		World.startEvent(e -> {
			for (int t = 0; warded && t < WARD_MAX_TICKS; t += 3) {
				if (npc.isRemoved() || isDead())
					return;
				npc.graphics(GFX_WARD, 120, 0);
				e.delay(3);
			}
			breakWard("The fel fire fades...");
		});
	}

	private void breakWard(String shout) {
		if (!warded)
			return;
		warded = false;
		if (shout != null && !npc.isRemoved() && !isDead())
			npc.forceText(shout);
		run.broadcast(Color.PURPLE.wrap("Baelgor's fel ward shatters!"));
	}

	/** open floor tiles in the room, 2 tiles out from his body (each side first), up to {@code count} */
	private List<Position> wardSpots(int count) {
		Position sw = npc.getPosition();
		int size = npc.getSize();
		int[][] offsets = { { -2, 1 }, { size + 1, 1 }, { 1, -2 }, { 1, size + 1 }, { -2, size }, { size + 1, -1 }, { -1, -2 }, { size, size + 1 } };
		List<Position> out = new ArrayList<>();
		for (int[] o : offsets) {
			if (out.size() >= count)
				break;
			Position p = sw.relative(o[0], o[1]);
			if (open(p))
				out.add(p);
		}
		for (Player m : run.membersInRoom(ROOM)) { // a cramped corner: next to the players instead
			if (out.size() >= count)
				break;
			for (int[] o : new int[][] { { 2, 0 }, { -2, 0 }, { 0, 2 }, { 0, -2 } }) {
				Position p = m.getPosition().relative(o[0], o[1]);
				if (open(p) && !out.contains(p)) {
					out.add(p);
					break;
				}
			}
		}
		return out;
	}

	private boolean open(Position p) {
		Tile tile = Tile.get(p.getX(), p.getY(), p.getZ(), false);
		return run.inRoom(p, ROOM) && tile != null && tile.clipping == 0;
	}

	/* ------------------------------------------------------------------ attacks */

	@Override
	public void follow() {
		follow(1);
	}

	@Override
	public boolean attack() {
		Player p = target();
		if (p == null)
			return false;
		attacks++;
		npc.face(p);
		int every = warded ? 3 : SPECIAL_EVERY;
		if (attacks % every == 0) {
			if (!warded && specials++ % 2 == 1 && anyoneWithin(GUST_REACH))
				hellfireGust();
			else
				felBrand();
			return true;
		}
		if (!warded && adjacent(p))
			claw(p);
		else
			felFire(p);
		return true;
	}

	/** the current target if it's a member alive in the room, else the nearest member in the room (re-targeted) */
	private Player target() {
		Player p = target == null ? null : target.player;
		if (p != null && p.isOnline() && !p.getCombat().isDead() && run.inRoom(p, ROOM))
			return p;
		Player next = run.nearest(npc, ROOM);
		if (next != null)
			hunt(next);
		return next;
	}

	private void claw(Player p) {
		npc.animate(SEQ_CLAW);
		p.graphics(GFX_CLAW, 0, 20); // lands with the hit, a tick after the swing starts
		p.hit(new Hit(npc, AttackStyle.SLASH).randDamage(CLAW_MAX).delay(1));
	}

	private void felFire(Player p) {
		npc.animate(SEQ_CAST);
		int duration = FEL.send(npc, p);
		p.hit(new Hit(npc, AttackStyle.MAGIC).randDamage(FEL_MAX).clientDelay(duration));
		p.graphics(GFX_FEL_HIT, 92, duration);
	}

	/** a fel bomb onto every member's tile; bursts on the 3x3 */
	private void felBrand() {
		npc.animate(SEQ_CAST);
		npc.forceText("Burn in the fel-fire!");
		for (Player m : run.membersInRoom(ROOM)) {
			Position spot = m.getPosition().copy();
			int duration = BRAND.send(npc, spot.getX(), spot.getY());
			int ticks = Math.max(2, (duration * 16 + 599) / 600);
			for (int dx = -1; dx <= 1; dx++)
				for (int dy = -1; dy <= 1; dy++)
					if ((dx == 0 && dy == 0) || (dx + dy) % 2 != 0)
						World.sendGraphics(GFX_BURST, 0, duration, spot.relative(dx, dy));
			World.startEvent(e -> {
				e.delay(ticks);
				if (npc.isRemoved() || isDead())
					return;
				for (Player v : run.membersInRoom(ROOM))
					if (Math.abs(v.getAbsX() - spot.getX()) <= 1 && Math.abs(v.getAbsY() - spot.getY()) <= 1)
						v.hit(new Hit(npc).randDamage(BRAND_MAX / 2, BRAND_MAX).ignoreDefence().ignorePrayer());
			});
		}
	}

	/** the close-range ring: wind-up, then a tile a tick out to GUST_REACH */
	private void hellfireGust() {
		npc.animate(SEQ_GUST);
		npc.forceText("Back, mortals!");
		int x0 = npc.getPosition().getX(), y0 = npc.getPosition().getY(), size = npc.getSize(), z = npc.getPosition().getZ();
		Position centre = npc.getPosition().relative(size / 2, size / 2);
		Set<Player> burned = new HashSet<>();
		World.startEvent(e -> {
			e.delay(GUST_WINDUP);
			for (int r = 1; r <= GUST_REACH; r++) {
				if (npc.isRemoved() || isDead())
					return;
				for (int x = x0 - r; x < x0 + size + r; x++)
					for (int y = y0 - r; y < y0 + size + r; y++)
						if (edgeDistance(x, y, x0, y0, size) == r && (x + y + r) % 2 == 0)
							World.sendGraphics(GFX_BURST, 0, 0, new Position(x, y, z));
				for (Player m : run.membersInRoom(ROOM)) {
					int d = edgeDistance(m.getAbsX(), m.getAbsY(), x0, y0, size);
					if (d <= r && burned.add(m)) {
						m.hit(new Hit(npc).randDamage(GUST_MAX / 2, GUST_MAX).ignoreDefence().ignorePrayer());
						push(m, centre);
					}
				}
				e.delay(1);
			}
		});
	}

	private boolean anyoneWithin(int tiles) {
		Position sw = npc.getPosition();
		for (Player m : run.membersInRoom(ROOM))
			if (edgeDistance(m.getAbsX(), m.getAbsY(), sw.getX(), sw.getY(), npc.getSize()) <= tiles)
				return true;
		return false;
	}

	/** really next to his body: one tile out on one axis, inside his span on the other (no diagonals, no reach) */
	private boolean adjacent(Player p) {
		Position sw = npc.getPosition();
		int size = npc.getSize();
		if (p.getHeight() != sw.getZ())
			return false;
		int x = p.getAbsX(), y = p.getAbsY();
		int dx = x < sw.getX() ? sw.getX() - x : Math.max(0, x - (sw.getX() + size - 1));
		int dy = y < sw.getY() ? sw.getY() - y : Math.max(0, y - (sw.getY() + size - 1));
		return dx + dy == 1;
	}

	/** tiles from a size x size body at x0,y0 (1 = adjacent) */
	private static int edgeDistance(int x, int y, int x0, int y0, int size) {
		int dx = x < x0 ? x0 - x : Math.max(0, x - (x0 + size - 1));
		int dy = y < y0 ? y0 - y : Math.max(0, y - (y0 + size - 1));
		return Math.max(dx, dy);
	}

	/** thrown back up to {@link #PUSH} tiles, stopping at the first blocked tile */
	private static void push(Player p, Position from) {
		int sx = Integer.signum(p.getAbsX() - from.getX()), sy = Integer.signum(p.getAbsY() - from.getY());
		Position to = p.getPosition().copy();
		for (int i = 0; i < PUSH; i++) {
			Position next = to.relative(sx, sy);
			Tile tile = Tile.get(next.getX(), next.getY(), next.getZ(), false);
			if (tile == null || tile.clipping != 0)
				break;
			to = next;
		}
		if (!to.equals(p.getPosition()))
			p.getMovement().teleport(to);
	}

	@Override
	protected void afterAttack() {
		updateLastAttack(ATTACK_TICKS);
	}
}

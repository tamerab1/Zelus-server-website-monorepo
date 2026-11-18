package io.ruin.model.activities.moonsofperil.bosses;

import io.ruin.api.utils.Random;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.map.Position;
import io.ruin.model.map.dynamic.DynamicMap;
import io.ruin.model.map.object.GameObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared engine for the three Moons of Peril (Blood / Blue / Eclipse). OSRS (wiki 2026-11-13):
 * <ul>
 * <li>The Moon never moves. It always faces Eyatlalli's glyph, a 2x2 sun symbol that hops clockwise around it every two
 * standard attacks. Standing outside the symbol causes increasing damage over time.</li>
 * <li>Standard attack every 6 ticks: a typeless slash stack of three hits (4 + 8 + 20). Each hit rolls accuracy, and
 * once one misses the rest of the stack misses too.</li>
 * <li>After six standard attacks it alternates between its two special attacks.</li>
 * </ul>
 * Every fight is a solo instance (a copy of the arena region), so the Moon's own hitpoints are the player's progress.
 * Static-map coordinates are passed through {@link #mx}/{@link #my} into the instance.
 */
public abstract class MoonBoss extends NPCCombat {

	/* the OSRS Moons client state (rev 241 varbits, all transmitted): the HUD 869 and the boss/curse meters read these */
	public static final int HUD = 869; // pmoon_hud
	static final io.ruin.model.var.VarPlayerRepository V_BOSS_HP = io.ruin.model.var.VarPlayerRepository.varpbit(9846, false), // pmoon_boss_hp
		V_IN_COMBAT = io.ruin.model.var.VarPlayerRepository.varpbit(9847, false), // pmoon_boss_in_combat
		V_CONDITION = io.ruin.model.var.VarPlayerRepository.varpbit(9853, false), // pmoon_boss_condition (the curse, 0..127)
		V_HIDE_NPC = io.ruin.model.var.VarPlayerRepository.varpbit(9862, false); // pmoon_boss_hide_npc
	private int shownHp = -1, shownCondition = -1;

	public static final int GLYPH = 13015;
	private static final int GLYPH_ANIM = 10981;
	private static final int ATTACK_RATE = 6, COMBOS_PER_ROTATION = 2, ROTATION_DELAY = 3, COMBOS_PER_SPECIAL = 6;
	private static final int POST_SPECIAL_DELAY = 4;
	private static final int[] COMBO_MAX = { 4, 8, 20 };

	protected Player player;
	protected DynamicMap map;

	private final List<NPC> adds = new ArrayList<>();
	private final List<GameObject> objects = new ArrayList<>();

	private NPC glyph;
	private int sigil = 0;
	private int combos = 0, combosSinceSpecial = 0;
	private int attackDelay = -1, rotateIn = -1;
	private int offSigilTicks = 0;
	private boolean nextSpecialA = true;
	protected boolean inSpecial = false;
	private boolean engaged = false;
	private int idleTicks = 0;

	/* ------------------------------------------------------------------ per-moon definition */

	/** SW tiles of the 2x2 glyph positions, clockwise (static map coordinates). */
	protected abstract int[][] sigils();

	protected abstract int comboAnim();

	/** Flat armour: subtracted from every hitsplat the player lands (negative = added: Blue -5, Blood -2). */
	protected abstract int flatArmour();

	/** Starts special A (true) or B (false). */
	protected abstract void startSpecial(boolean a);

	/** Runs one tick of the active special; return false once it has finished. */
	protected abstract boolean specialTick();

	/** Called for every landed combo hit (index 0..2) with its damage. */
	protected void onComboHit(int index, int damage) {
	}

	/** Lets a moon adjust a player's hit (curse effects etc.) before flat armour applies. */
	protected void modifyPlayerHit(Hit hit) {
	}

	/** One curse stack for a tick spent off the glyph (Blue: cold, Eclipse: blindness). */
	protected void offGlyphCurse() {
	}

	/** Per-tick hook while the standard phase runs (curse decay etc.). */
	protected void standardTick() {
	}

	/** The curse stacks shown on the HUD (Blue: cold, Eclipse: blindness, Blood: 0). */
	protected int condition() {
		return 0;
	}

	/** Hides / shows the Moon the OSRS way: the npc and its vis multiloc flag (pmoon_boss_hide_npc). */
	protected void hideMoon(boolean hide) {
		npc.setHidden(hide);
		if (player != null)
			V_HIDE_NPC.set(player, hide ? 1 : 0);
	}

	/** Pushes the boss hp and curse to the client when they change. */
	private void syncHud() {
		int hp = Math.min(1023, Math.max(0, npc.getHp())), cond = Math.min(127, Math.max(0, condition()));
		if (hp != shownHp) {
			shownHp = hp;
			V_BOSS_HP.set(player, hp);
		}
		if (cond != shownCondition) {
			shownCondition = cond;
			V_CONDITION.set(player, cond);
		}
	}

	/** Removes anything the moon spawned and returns borrowed items. Always safe to call twice. */
	protected void cleanupSpecial() {
	}

	/* ------------------------------------------------------------------ wiring */

	public void bind(Player player, DynamicMap map) {
		this.player = player;
		this.map = map;
		npc.ownerId = player.getUserId();
		V_IN_COMBAT.set(player, 1);
		V_HIDE_NPC.set(player, 0);
		syncHud();
	}

	protected int mx(int x) {
		return map.convertX(x);
	}

	protected int my(int y) {
		return map.convertY(y);
	}

	protected Position pos(int x, int y) {
		return new Position(mx(x), my(y), 0);
	}

	protected NPC spawnAdd(int id, int x, int y) {
		NPC add = new NPC(id).spawn(mx(x), my(y), 0, 0);
		add.ownerId = player.getUserId();
		adds.add(add);
		return add;
	}

	protected void removeAdd(NPC add) {
		if (add != null) {
			adds.remove(add);
			add.remove();
		}
	}

	protected GameObject spawnObject(int id, int x, int y, int type, int rot) {
		GameObject obj = GameObject.spawn(id, mx(x), my(y), 0, type, rot);
		objects.add(obj);
		return obj;
	}

	/** Spawns an object at an already-converted (instance) position. */
	protected GameObject spawnObjectAbs(int id, Position p, int type, int rot) {
		GameObject obj = GameObject.spawn(id, p.getX(), p.getY(), p.getZ(), type, rot);
		objects.add(obj);
		return obj;
	}

	protected void removeObject(GameObject obj) {
		if (obj != null) {
			objects.remove(obj);
			obj.remove();
		}
	}

	protected boolean fighting() {
		return player != null && player.isOnline() && !player.getCombat().isDead() && !npc.isRemoved() && !npc.getCombat().isDead()
			&& player.getPosition().getRegion() == npc.getPosition().getRegion() && player.getHeight() == npc.getHeight();
	}

	protected Position centre() {
		return new Position(npc.getAbsX() + npc.getSize() / 2, npc.getAbsY() + npc.getSize() / 2, npc.getHeight());
	}

	/* ------------------------------------------------------------------ setup */

	@Override
	public void init() {
		npc.hitListener = new HitListener().preDamage(this::playerHit);
	}

	private void playerHit(Hit hit) {
		if (hit.attacker == null || hit.attacker.player == null)
			return;
		engaged = true;
		if (inSpecial && !allowHitsDuringSpecial()) {
			hit.block();
			return;
		}
		modifyPlayerHit(hit);
		if (hit.damage > 0)
			hit.damage = Math.max(0, hit.damage - flatArmour());
	}

	/** Moons are intangible during most specials; Eclipse lets parries/rays through. */
	protected boolean allowHitsDuringSpecial() {
		return false;
	}

	@Override
	public void follow() {
		// the Moons never move
	}

	@Override
	public boolean attack() {
		return false; // everything is timer-driven from process()
	}

	/* ------------------------------------------------------------------ tick */

	@Override
	public void process() {
		if (player == null || map == null || npc.getCombat().isDead())
			return;
		if (!fighting()) {
			cleanup();
			return;
		}
		if (!engaged) {
			if (++idleTicks >= 8)
				engaged = true;
			else
				return;
		}
		syncHud();
		if (inSpecial) {
			if (!specialTick()) {
				inSpecial = false;
				attackDelay = POST_SPECIAL_DELAY;
				placeGlyph();
			}
			return;
		}
		if (glyph == null)
			placeGlyph();
		npc.face(glyphCentre());
		standardTick();

		if (onSigil(player)) {
			offSigilTicks = 0;
		} else {
			// wiki: off the glyph, 1 damage a tick doubling up to 16 a tick, and a curse stack each tick
			int dmg = Math.min(16, 1 << Math.min(4, offSigilTicks++));
			player.hit(new Hit(npc).fixedDamage(dmg).ignorePrayer().ignoreDefence());
			offGlyphCurse();
		}

		if (rotateIn > 0 && --rotateIn == 0)
			rotateGlyph();

		if (attackDelay < 0)
			attackDelay = ATTACK_RATE;
		if (--attackDelay <= 0) {
			attackDelay = ATTACK_RATE;
			combo();
			if (++combos % COMBOS_PER_ROTATION == 0)
				rotateIn = ROTATION_DELAY;
			if (++combosSinceSpecial >= COMBOS_PER_SPECIAL) {
				combosSinceSpecial = 0;
				rotateIn = -1;
				sigil = (sigil + 1) % sigils().length; // resume on the next symbol afterwards
				removeGlyph();
				inSpecial = true;
				startSpecial(nextSpecialA);
				nextSpecialA = !nextSpecialA;
			}
		}
	}

	private void combo() {
		npc.animate(comboAnim());
		Hit[] hits = new Hit[3];
		for (int i = 0; i < 3; i++) {
			final int idx = i;
			Hit h = new Hit(npc, AttackStyle.SLASH).randDamage(COMBO_MAX[i]).ignorePrayer().delay(i + 1);
			hits[i] = h;
			if (i > 0) {
				final Hit prev = hits[i - 1];
				h.preDefend(t -> {
					if (prev.isBlocked())
						h.block(); // one miss forces every later hit in the stack to miss
				});
			}
			h.postDamage(t -> {
				if (!h.isBlocked())
					onComboHit(idx, h.damage);
			});
			player.hit(h);
		}
	}

	/* ------------------------------------------------------------------ glyph */

	private Position glyphCentre() {
		int[] s = sigils()[sigil];
		return new Position(mx(s[0]), my(s[1]), 0);
	}

	protected boolean onSigil(Player p) {
		if (glyph == null)
			return true;
		int[] s = sigils()[sigil];
		int x = p.getAbsX() - mx(s[0]), y = p.getAbsY() - my(s[1]);
		return x >= 0 && x < 2 && y >= 0 && y < 2;
	}

	private void placeGlyph() {
		int[] s = sigils()[sigil];
		glyph = spawnAdd(GLYPH, s[0], s[1]);
		glyph.animate(GLYPH_ANIM);
		npc.face(glyphCentre());
	}

	private void removeGlyph() {
		removeAdd(glyph);
		glyph = null;
	}

	private void rotateGlyph() {
		removeGlyph();
		sigil = (sigil + 1) % sigils().length;
		placeGlyph();
	}

	/* ------------------------------------------------------------------ teardown */

	public void cleanup() {
		cleanupSpecial();
		if (player != null) {
			V_IN_COMBAT.set(player, 0);
			V_BOSS_HP.set(player, 0);
			V_CONDITION.set(player, 0);
			V_HIDE_NPC.set(player, 0);
			shownHp = shownCondition = -1;
		}
		inSpecial = false;
		removeGlyph();
		for (NPC add : new ArrayList<>(adds))
			add.remove();
		adds.clear();
		for (GameObject obj : new ArrayList<>(objects))
			obj.remove();
		objects.clear();
	}

	/** Shared helper: a typeless hit on the player that ignores prayer and defence. */
	protected void damagePlayer(int min, int max) {
		player.hit(new Hit(npc).randDamage(min, max).ignorePrayer().ignoreDefence());
	}

	protected void healMoon(int amount) {
		if (amount <= 0)
			return;
		npc.incrementHp(amount);
	}

	protected static int rand(int min, int max) {
		return Random.get(min, max);
	}
}

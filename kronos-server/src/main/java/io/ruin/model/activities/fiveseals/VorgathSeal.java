package io.ruin.model.activities.fiveseals;

import io.ruin.api.utils.Random;
import io.ruin.cache.Color;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.model.map.Tile;
import io.ruin.model.skills.prayer.Prayer;
import io.ruin.model.stat.StatType;

import java.util.ArrayList;
import java.util.List;

/**
 * The Sovereign Vault's boss (user 2026-11-16: "vorgath and baelgor with unique custom special attacks ... vorgath is the
 * ranger one"): Vorgath the Shadowrider (npc 20000, the customs27 rider on a winged shadow-wolf).
 * <ul>
 * <li>A ranger with two shots, each telegraphed ~3 ticks before it lands so players can switch prayers on the read:
 * the Burning Arrow ("Loose!", a fiery arrow 1115 - Protect from Missiles) and the Shadow Bolt ("Shadows, take them!",
 * a dark orb 380 - Protect from Magic). Judged on IMPACT.</li>
 * <li>The shadow-wolf bites anyone standing beside it (Protect from Melee).</li>
 * <li>Volley Barrage, every 5th attack: "Darkness, fall!" - an arrow arcs high and splits into shadow marks around its
 * target; they burst 2 ticks after they appear. Step off them.</li>
 * <li>Beast Pounce, every 8th attack: the wolf leaps onto open ground beside its target and tramples everyone next to the
 * landing (anyone it would land on is thrown clear).</li>
 * <li>The Hunt, below 30% health: "The hunt ends here!" - he spurs the wolf and attacks a tick faster.</li>
 * </ul>
 * Hitpoints: {@link #BASE_HP} for a solo run, x(1 + 0.85 per extra member).
 */
public class VorgathSeal extends SealCombat {

	static final int NPC_ID = 20000, BASE_HP = 1200;
	static final int SEQ_RANGED = 19002, SEQ_BITE = 19003;
	static final int GFX_ARROW = 1115, GFX_ARROW_LAUNCH = 1116, GFX_BOLT = 380, GFX_BOLT_HIT = 381;
	static final int GFX_SHADOW_MARK = 4340, GFX_SHADOW_BURST = 4339;
	static final int ARROW_MAX = 30, BOLT_MAX = 30, BITE_MAX = 28, VOLLEY_MAX = 32, TRAMPLE_MAX = 26;
	static final int VOLLEY_SHADOWS = 7;
	private static final Projectile ARROW = new Projectile(GFX_ARROW, 120, 36, 30, 60, 6, 12, 64);
	private static final Projectile BOLT = new Projectile(GFX_BOLT, 110, 31, 30, 60, 10, 16, 64);

	final FiveSealsRun run;
	private int attacks;
	private boolean hunting;

	VorgathSeal(FiveSealsRun run) {
		this.run = run;
	}

	void scale(int partySize) {
		var hp = getStat(StatType.Hitpoints);
		int max = (int) Math.round(BASE_HP * (1 + 0.85 * Math.max(0, partySize - 1)));
		hp.fixedLevel = max;
		hp.currentLevel = max;
	}

	@Override
	public void init() {
		npc.hitListener = new HitListener().postDamage(hit -> {
			if (!hunting && npc.getHp() > 0 && npc.getHp() * 10 < npc.getMaxHp() * 3) {
				hunting = true;
				npc.forceText("The hunt ends here!");
				run.broadcast(Color.RED.wrap("Vorgath spurs his shadow-wolf - he attacks faster!"));
			}
		});
	}

	@Override
	public void follow() {
		follow(7);
	}

	@Override
	public boolean attack() {
		if (target == null || target.player == null)
			return false;
		Player p = target.player;
		attacks++;
		if (attacks % 5 == 0) {
			volley(p);
			return true;
		}
		if (attacks % 8 == 0 && pounce(p))
			return true;
		npc.face(p);
		if (withinDistance(1) && Random.get(2) != 0) {
			npc.animate(SEQ_BITE);
			FiveSealsBossFx.strike(npc, p, 1, AttackStyle.STAB, Prayer.PROTECT_FROM_MELEE, BITE_MAX, -1);
			return true;
		}
		if (!withinDistance(10))
			return false;
		boolean magic = FiveSealsBossFx.coin();
		npc.forceText(magic ? "Shadows, take them!" : "Loose!");
		World.startEvent(e -> {
			e.delay(1); // the read: the shout and the draw come a tick before the shot leaves
			if (npc.isRemoved() || isDead())
				return;
			npc.animate(SEQ_RANGED);
			if (!magic)
				npc.graphics(GFX_ARROW_LAUNCH, 120, 30);
			int ticks = Math.max(2, (magic ? BOLT : ARROW).send(npc, p) / 30);
			FiveSealsBossFx.strike(npc, p, ticks, magic ? AttackStyle.MAGIC : AttackStyle.RANGED,
				magic ? Prayer.PROTECT_FROM_MAGIC : Prayer.PROTECT_FROM_MISSILES, magic ? BOLT_MAX : ARROW_MAX, magic ? GFX_BOLT_HIT : -1);
		});
		return true;
	}

	/** one arrow high into the air that splits into shadow marks around the target; step off them */
	private void volley(Player player) {
		npc.face(player);
		npc.animate(SEQ_RANGED);
		npc.forceText("Darkness, fall!");
		Position centre = player.getPosition().copy();
		new Projectile(GFX_ARROW, 120, 0, 30, 90, 0, 80, 64).send(npc, centre);
		List<Position> marks = new ArrayList<>();
		marks.add(centre);
		while (marks.size() < VOLLEY_SHADOWS) {
			Position p = centre.relative(Random.get(-2, 2), Random.get(-2, 2));
			if (!marks.contains(p))
				marks.add(p);
		}
		World.startEvent(e -> {
			e.delay(2);
			for (Position m : marks)
				World.sendGraphics(GFX_SHADOW_MARK, 0, 0, m);
			e.delay(2);
			for (Position m : marks)
				World.sendGraphics(GFX_SHADOW_BURST, 0, 0, m);
			if (npc.isRemoved() || isDead())
				return;
			for (Player p : run.membersInRoom(5))
				for (Position m : marks)
					if (p.getAbsX() == m.getX() && p.getAbsY() == m.getY()) {
						p.hit(new Hit(npc).randDamage(VOLLEY_MAX).ignoreDefence().ignorePrayer());
						break;
					}
		});
	}

	/** the wolf leaps onto open ground beside its target; false when there is none (a normal attack instead) */
	private boolean pounce(Player player) {
		Position land = landingBeside(player);
		if (land == null)
			return false;
		npc.face(player);
		npc.animate(SEQ_BITE);
		npc.forceText("*the shadow-wolf lunges*");
		Position from = npc.getPosition().copy();
		io.ruin.model.activities.raids.tfa.Raid4Fx.slide(npc, land, 30,
			io.ruin.model.activities.raids.tfa.Raid4Fx.angle(from.getX(), from.getY(), land.getX(), land.getY()));
		World.startEvent(e -> {
			e.delay(1);
			if (npc.isRemoved() || isDead())
				return;
			int size = npc.getSize();
			for (Player p : run.membersInRoom(5)) {
				int dx = p.getAbsX() - land.getX(), dy = p.getAbsY() - land.getY();
				boolean under = dx >= 0 && dx < size && dy >= 0 && dy < size;
				boolean beside = dx >= -1 && dx <= size && dy >= -1 && dy <= size;
				if (!beside)
					continue;
				if (under)
					knockBack(p, land, size);
				p.hit(new Hit(npc, AttackStyle.CRUSH).randDamage(TRAMPLE_MAX).delay(1));
			}
		});
		return true;
	}

	private Position landingBeside(Player player) {
		int size = npc.getSize();
		Position t = player.getPosition();
		Position best = null;
		int bestDist = Integer.MAX_VALUE;
		for (int x = t.getX() - size; x <= t.getX() + 1; x++)
			for (int y = t.getY() - size; y <= t.getY() + 1; y++) {
				boolean covers = t.getX() >= x && t.getX() < x + size && t.getY() >= y && t.getY() < y + size;
				if (covers || !open(x, y, t.getZ(), size))
					continue;
				int d = Math.abs(x - npc.getAbsX()) + Math.abs(y - npc.getAbsY());
				if (d < bestDist) {
					bestDist = d;
					best = new Position(x, y, t.getZ());
				}
			}
		return best;
	}

	private static boolean open(int x, int y, int z, int size) {
		for (int dx = 0; dx < size; dx++)
			for (int dy = 0; dy < size; dy++) {
				Tile tile = Tile.get(x + dx, y + dy, z, false);
				if (tile == null || tile.clipping != 0)
					return false;
			}
		return true;
	}

	private void knockBack(Player p, Position land, int size) {
		int cx = land.getX() * 2 + size - 1, cy = land.getY() * 2 + size - 1;
		int sx = Integer.signum(p.getAbsX() * 2 - cx), sy = Integer.signum(p.getAbsY() * 2 - cy);
		if (sx == 0 && sy == 0)
			sx = -1;
		for (int dist = size; dist >= 1; dist--) {
			Position to = p.getPosition().relative(sx * dist, sy * dist);
			if (open(to.getX(), to.getY(), to.getZ(), 1)) {
				p.getMovement().reset();
				io.ruin.model.activities.raids.tfa.Raid4Fx.slide(p, to, 30,
					io.ruin.model.activities.raids.tfa.Raid4Fx.angle(to.getX(), to.getY(), p.getAbsX(), p.getAbsY()));
				p.animate(424);
				return;
			}
		}
	}

	@Override
	protected void afterAttack() {
		updateLastAttack(hunting ? 4 : 5);
	}
}

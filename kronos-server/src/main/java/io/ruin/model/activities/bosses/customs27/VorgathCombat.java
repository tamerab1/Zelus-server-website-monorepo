package io.ruin.model.activities.bosses.customs27;

import io.ruin.api.utils.Random;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;

import java.util.ArrayList;
import java.util.List;

/**
 * Vorgath the Shadowrider (npc 20000, customs27 2026-10-27): a horned rider on a winged shadow-wolf (model 62776, rigged by
 * hand, .dev rig_vorgath.txt -> seqs 19000-19004). A ranged boss:
 * <ul>
 * <li>Auto attack: the rider looses a burning dragon arrow (1115) from 8 tiles: accurate ranged, max {@link #ARROW_MAX}.</li>
 * <li>Beast Pounce: a player next to the wolf is bitten instead (melee, max {@link #BITE_MAX}); every
 * {@link #POUNCE_EVERY}th attack the wolf leaps at its target, trampling anyone on the landing tiles.</li>
 * <li>Volley Barrage: every {@link #VOLLEY_EVERY}th attack one arrow arcs high and splits over the target into
 * {@link #VOLLEY_SHADOWS} shadow marks (4340) that explode two ticks later (4339) for up to {@link #VOLLEY_MAX} each.</li>
 * </ul>
 */
public class VorgathCombat extends NPCCombat {

	static final int SEQ_RANGED = 19002, SEQ_BITE = 19003;
	static final int GFX_ARROW = 1115, GFX_ARROW_LAUNCH = 1116, GFX_SHADOW_MARK = 4340, GFX_SHADOW_BURST = 4339;
	static final int ARROW_MAX = 32, BITE_MAX = 30, VOLLEY_MAX = 35, TRAMPLE_MAX = 28;
	static final int VOLLEY_EVERY = 5, POUNCE_EVERY = 8, VOLLEY_SHADOWS = 7, VOLLEY_FUSE = 2;

	private int attacks;

	@Override
	public void init() {
	}

	@Override
	public void process() {
	}

	@Override
	public void follow() {
		follow(7);
	}

	@Override
	public boolean attack() {
		if (!(target instanceof Player player))
			return false;
		attacks++;
		if (attacks % VOLLEY_EVERY == 0) {
			volley(player);
			return true;
		}
		if (attacks % POUNCE_EVERY == 0) {
			pounce(player);
			return true;
		}
		if (withinDistance(1)) {
			npc.face(player);
			npc.animate(SEQ_BITE);
			player.hit(new Hit(npc, AttackStyle.STAB).randDamage(BITE_MAX).delay(1));
			return true;
		}
		npc.face(player);
		npc.animate(SEQ_RANGED);
		npc.graphics(GFX_ARROW_LAUNCH, 120, 30);
		int duration = new Projectile(GFX_ARROW, 120, 36, 30, 40, 6, 12, 64).send(npc, player);
		player.hit(new Hit(npc, AttackStyle.RANGED).randDamage(ARROW_MAX).boostAttack(0.25).clientDelay(duration));
		return true;
	}

	/** One arrow high into the air that splits into shadow marks around the target; step off them. */
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
			boolean taken = false;
			for (Position m : marks)
				taken |= m.equals(p);
			if (!taken)
				marks.add(p);
		}
		World.startEvent(e -> {
			e.delay(2);
			for (Position m : marks)
				World.sendGraphics(GFX_SHADOW_MARK, 0, 0, m);
			e.delay(VOLLEY_FUSE);
			for (Position m : marks)
				World.sendGraphics(GFX_SHADOW_BURST, 0, 0, m);
			if (npc.isRemoved() || npc.getCombat().isDead())
				return;
			for (Player p : npc.localPlayers()) {
				for (Position m : marks) {
					if (p.getPosition().equals(m)) {
						p.hit(new Hit(npc).randDamage(VOLLEY_MAX).ignoreDefence().ignorePrayer());
						break;
					}
				}
			}
		});
	}

	/**
	 * The wolf leaps beside its target: a 30-cycle exact move (no teleport) onto open ground next to them, never on top
	 * of them. Its landing tramples the target and anyone next to the landing; anyone it would land on is thrown two
	 * tiles clear with a stagger, so nobody is left stuck under its body.
	 */
	private void pounce(Player player) {
		Position land = landingBeside(player);
		if (land == null) // nowhere open beside them: bite instead
			return;
		npc.face(player);
		npc.animate(SEQ_BITE);
		npc.forceText("*the shadow-wolf lunges*");
		Position from = npc.getPosition().copy();
		io.ruin.model.activities.raids.tfa.Raid4Fx.slide(npc, land, 30,
				io.ruin.model.activities.raids.tfa.Raid4Fx.angle(from.getX(), from.getY(), land.getX(), land.getY()));
		World.startEvent(e -> {
			e.delay(1);
			if (npc.isRemoved() || npc.getCombat().isDead())
				return;
			int size = npc.getSize();
			for (Player p : npc.localPlayers()) {
				int dx = p.getPosition().getX() - land.getX(), dy = p.getPosition().getY() - land.getY();
				boolean under = dx >= 0 && dx < size && dy >= 0 && dy < size;
				boolean beside = dx >= -1 && dx <= size && dy >= -1 && dy <= size;
				if (!beside)
					continue;
				if (under)
					knockBack(p, land, size);
				p.hit(new Hit(npc, AttackStyle.CRUSH).randDamage(TRAMPLE_MAX).delay(1));
			}
		});
	}

	/** Open ground for the wolf's whole body touching the target's tile, nearest the wolf; null if there is none. */
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
				io.ruin.model.map.Tile tile = io.ruin.model.map.Tile.get(x + dx, y + dy, z, false);
				if (tile == null || tile.clipping != 0)
					return false;
			}
		return true;
	}

	/** Throws a raider two tiles out of the wolf's landing, away from its middle, as a stagger (not a teleport). */
	private void knockBack(Player p, Position land, int size) {
		int cx = land.getX() * 2 + size - 1, cy = land.getY() * 2 + size - 1; // doubled centre
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
}

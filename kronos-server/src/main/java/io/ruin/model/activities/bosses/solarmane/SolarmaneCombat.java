package io.ruin.model.activities.bosses.solarmane;

import io.ruin.api.utils.Random;
import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;
import io.ruin.model.map.Tile;

import java.util.ArrayList;
import java.util.List;

/**
 * Solarmane (npc 30611, 2026-10-31): the sun-lion, a re-textured Sabre-toothed kyatt that keeps the kyatt's animations
 * (5225-5231). An aggressive melee boss:
 * <ul>
 * <li>Auto attack: a fast bite (5228) every 4 ticks, slash, max {@link #BITE_MAX}.</li>
 * <li>Solar Pounce: every {@link #POUNCE_EVERY}th attack it leaps (5231) onto open ground beside its target, bursting
 * into a lava splash; the target and anyone next to the landing take up to {@link #POUNCE_MAX}, and anyone it lands on
 * is thrown clear.</li>
 * <li>Sunfire Roar: every {@link #ROAR_EVERY}th attack it roars (5229) and a beam of sunfire (2698, Sol Heredit's)
 * comes down on every nearby player's tile; it strikes {@link #ROAR_FUSE} ticks later for up to {@link #ROAR_MAX} to
 * anyone still there, through prayer. Step off.</li>
 * </ul>
 * Stats: npcs/combat/solarmane.json. Arena, pet and command: {@link Solarmane}.
 */
public class SolarmaneCombat extends NPCCombat {

	static final int SEQ_BITE = 5228, SEQ_ROAR = 5229, SEQ_LEAP = 5231;
	static final int GFX_LAVA_SPLASH = 69, GFX_SUNFIRE_BEAM = 2698;
	static final int BITE_MAX = 34, POUNCE_MAX = 42, ROAR_MAX = 45;
	static final int POUNCE_EVERY = 6, ROAR_EVERY = 9, ROAR_FUSE = 3, ROAR_RANGE = 10;

	private int attacks;

	@Override
	public void init() {
	}

	@Override
	public void process() {
	}

	@Override
	public void follow() {
		follow(1);
	}

	@Override
	public boolean attack() {
		if (!(target instanceof Player player))
			return false;
		attacks++;
		if (attacks % ROAR_EVERY == 0) {
			roar();
			return true;
		}
		if (attacks % POUNCE_EVERY == 0 && pounce(player))
			return true;
		if (!withinDistance(1))
			return false;
		npc.face(player);
		npc.animate(SEQ_BITE);
		player.hit(new Hit(npc, AttackStyle.SLASH).randDamage(BITE_MAX).delay(1));
		return true;
	}

	/** A sunfire beam onto every player's tile in range, striking {@link #ROAR_FUSE} ticks later. */
	private void roar() {
		npc.animate(SEQ_ROAR);
		npc.forceText("RAAAWRRR!");
		List<Position> marks = new ArrayList<>();
		for (Player p : npc.localPlayers())
			if (p.getPosition().isWithinDistance(npc.getPosition(), ROAR_RANGE))
				marks.add(p.getPosition().copy());
		for (Position m : marks)
			World.sendGraphics(GFX_SUNFIRE_BEAM, 0, 0, m); // one effect: the beam comes down and strikes on its own
		World.startEvent(e -> {
			e.delay(ROAR_FUSE);
			if (npc.isRemoved() || npc.getCombat().isDead())
				return;
			for (Player p : npc.localPlayers())
				for (Position m : marks)
					if (p.getPosition().equals(m)) {
						p.hit(new Hit(npc).randDamage(ROAR_MAX).ignoreDefence().ignorePrayer());
						break;
					}
		});
	}

	/**
	 * Leaps beside its target with a 30-cycle exact move onto open ground (never on top of them); the landing tramples
	 * the target and anyone next to it, and throws clear anyone it would land on. False if there's nowhere to land.
	 */
	private boolean pounce(Player player) {
		Position land = landingBeside(player);
		if (land == null)
			return false;
		npc.face(player);
		npc.animate(SEQ_LEAP);
		npc.forceText("*Solarmane pounces!*");
		Position from = npc.getPosition().copy();
		io.ruin.model.activities.raids.tfa.Raid4Fx.slide(npc, land, 30,
				io.ruin.model.activities.raids.tfa.Raid4Fx.angle(from.getX(), from.getY(), land.getX(), land.getY()));
		World.startEvent(e -> {
			e.delay(1);
			if (npc.isRemoved() || npc.getCombat().isDead())
				return;
			int size = npc.getSize();
			World.sendGraphics(GFX_LAVA_SPLASH, 0, 0, land.relative(size / 2, size / 2));
			for (Player p : npc.localPlayers()) {
				int dx = p.getAbsX() - land.getX(), dy = p.getAbsY() - land.getY();
				boolean under = dx >= 0 && dx < size && dy >= 0 && dy < size;
				boolean beside = dx >= -1 && dx <= size && dy >= -1 && dy <= size;
				if (!beside)
					continue;
				if (under)
					knockBack(p, land, size);
				p.hit(new Hit(npc, AttackStyle.CRUSH).randDamage(POUNCE_MAX).delay(1));
			}
		});
		return true;
	}

	/** Open ground for the whole body touching the target's tile, nearest the lion; null if there is none. */
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

	/** Throws a player out from under the landing, away from its middle, as a stagger (not a teleport). */
	private void knockBack(Player p, Position land, int size) {
		int cx = land.getX() * 2 + size - 1, cy = land.getY() * 2 + size - 1; // doubled centre
		int sx = Integer.signum(p.getAbsX() * 2 - cx), sy = Integer.signum(p.getAbsY() * 2 - cy);
		if (sx == 0 && sy == 0)
			sx = Random.get(1) == 0 ? -1 : 1;
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

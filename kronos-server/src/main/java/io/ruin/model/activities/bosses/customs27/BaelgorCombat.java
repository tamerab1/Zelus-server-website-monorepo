package io.ruin.model.activities.bosses.customs27;

import io.ruin.model.World;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.npc.NPCCombat;
import io.ruin.model.entity.player.Player;
import io.ruin.model.map.Position;
import io.ruin.model.map.Projectile;
import io.ruin.model.map.Tile;

import java.util.HashSet;
import java.util.Set;

/**
 * Baelgor the Felreaver (npc 20001, customs27 2026-10-27): a winged arch-demon (model 62777: the player skeleton plus two
 * wing bones layered over player frames, .dev rig_baelgor.txt -> seqs 19010-19015: a continuous wing beat in every pose).
 * <ul>
 * <li>Melee: claw swipes on a player next to him (19012), max {@link #CLAW_MAX}.</li>
 * <li>Magic: fel-fire from range (19013, the green acid-fire 1598 / impact 1600), max {@link #FEL_MAX}.</li>
 * <li>Hellfire Gust, every {@link #GUST_EVERY}th attack (19014): he rises and beats his wings down; a ring of green flame
 * rolls out a tile a tick to {@link #GUST_RADIUS} tiles, burning and throwing back anyone it passes over.</li>
 * </ul>
 */
public class BaelgorCombat extends NPCCombat {

	/** ::baelgorgust (test only). */
	static boolean forceGust;
	static final int SEQ_CLAW = 19012, SEQ_CAST = 19013, SEQ_GUST = 19014;
	static final int GFX_FEL = 1598, GFX_FEL_HIT = 1600, GFX_FLAME = 1599;
	static final int CLAW_MAX = 38, FEL_MAX = 34, GUST_MAX = 30, GUST_RADIUS = 6, GUST_EVERY = 6, PUSH = 2;

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
		if (forceGust || attacks % GUST_EVERY == 0) {
			hellfireGust();
			return true;
		}
		npc.face(player);
		if (withinDistance(1)) {
			npc.animate(SEQ_CLAW);
			player.hit(new Hit(npc, AttackStyle.SLASH).randDamage(CLAW_MAX).delay(1));
			return true;
		}
		npc.animate(SEQ_CAST);
		int duration = new Projectile(GFX_FEL, 90, 31, 25, 45, 6, 16, 64).send(npc, player);
		Hit hit = new Hit(npc, AttackStyle.MAGIC).randDamage(FEL_MAX).clientDelay(duration);
		hit.postDamage(e -> player.graphics(GFX_FEL_HIT));
		player.hit(hit);
		return true;
	}

	/** The Wing Buffet: a ring of fel flame rolling outward; anyone it passes is burned and thrown back. */
	private void hellfireGust() {
		// 2026-10-28: rebuilt without the root lift that tore the mesh in the rev 241 client (rig_baelgor.txt)
		npc.animate(SEQ_GUST);
		npc.forceText("Burn in the fel-fire!");
		// rings measured from his edge, so they stay square around an even-sized body
		int x0 = npc.getPosition().getX(), y0 = npc.getPosition().getY(), size = npc.getSize(), z = npc.getPosition().getZ();
		Position centre = npc.getPosition().relative(size / 2, size / 2);
		Set<Player> hit = new HashSet<>();
		World.startEvent(e -> {
			e.delay(3); // the downbeat
			for (int r = 1; r <= GUST_RADIUS; r++) {
				if (npc.isRemoved() || npc.getCombat().isDead())
					return;
				for (int x = x0 - r; x < x0 + size + r; x++) {
					for (int y = y0 - r; y < y0 + size + r; y++) {
						if (edgeDistance(x, y, x0, y0, size) == r && (x + y) % 2 == 0)
							World.sendGraphics(GFX_FLAME, 0, 0, new Position(x, y, z));
					}
				}
				for (Player p : npc.localPlayers()) {
					int d = edgeDistance(p.getPosition().getX(), p.getPosition().getY(), x0, y0, size);
					if (d == r && hit.add(p)) {
						p.hit(new Hit(npc).randDamage(GUST_MAX).ignoreDefence().ignorePrayer());
						push(p, centre);
					}
				}
				e.delay(1);
			}
		});
	}

	/** Tiles from a size x size body at x0,y0 (1 = adjacent). */
	private static int edgeDistance(int x, int y, int x0, int y0, int size) {
		int dx = x < x0 ? x0 - x : Math.max(0, x - (x0 + size - 1));
		int dy = y < y0 ? y0 - y : Math.max(0, y - (y0 + size - 1));
		return Math.max(dx, dy);
	}

	/** Thrown back up to {@link #PUSH} tiles, stopping at the first blocked tile. */
	private static void push(Player p, Position from) {
		int sx = Integer.signum(p.getPosition().getX() - from.getX()), sy = Integer.signum(p.getPosition().getY() - from.getY());
		Position to = p.getPosition().copy();
		for (int i = 0; i < PUSH; i++) {
			Position next = to.relative(sx, sy);
			Tile tile = Tile.get(next.getX(), next.getY(), next.getZ(), false);
			if (tile != null && tile.clipping != 0)
				break;
			to = next;
		}
		if (!to.equals(p.getPosition()))
			p.getMovement().teleport(to);
	}
}

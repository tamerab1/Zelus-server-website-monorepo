package io.ruin.model.activities.bosses.custom;

import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.Hit;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.entity.shared.listeners.HitListener;
import io.ruin.model.map.Projectile;
import io.ruin.model.stat.StatType;

/**
 * Morvath the Shadowblade (npc 21940), a Colosseum of Trials archer (2026-11-08):
 * <ul>
 * <li><b>Nightfall Barrage</b> (every {@link #BARRAGE_EVERY}th attack): three smoke arrows a tick apart at everyone in
 * the arena, each sapping {@link #PRAYER_SAP} Prayer on a hit; the last one goes through protection prayers.</li>
 * <li><b>Shadow ward</b>: he protects from either melee or ranged, and every barrage switches which. Hits of the
 * warded style are blocked; magic always gets through.</li>
 * </ul>
 */
public class MorvathCombat extends CustomBossCombat {

	static final int BARRAGE_EVERY = 6, BARRAGE_ARROWS = 3, PRAYER_SAP = 8, RANGE = 8;
	static final int GFX_ARROW = 1101, GFX_SMOKE = 1103;

	private int attacks;
	private boolean wardMelee;
	private boolean overheadPending = true; // the avatar doesn't exist yet in init(): shown on the first tick

	@Override
	public void init() {
		super.init();
		npc.hitListener = new HitListener().preDamage(this::preDamage);
	}

	private void preDamage(Hit hit) {
		if (hit.attackStyle == null || hit.attacker == null || hit.attacker.player == null)
			return;
		if (wardMelee ? hit.attackStyle.isMelee() : hit.attackStyle.isRanged())
			hit.block();
	}

	@Override
	public void process() {
		if (overheadPending && npc.avatar() != null) {
			overheadPending = false;
			showWard();
		}
	}

	private void showWard() {
		npc.setHeadIcon(wardMelee ? NPC.DefaultHeadIconIndex.ProtectFromMelee : NPC.DefaultHeadIconIndex.ProtectFromRanged);
	}

	@Override
	public boolean attack() {
		if (target == null || !withinDistance(RANGE))
			return false;
		if (++attacks % BARRAGE_EVERY == 0) {
			nightfallBarrage();
			return true;
		}
		return super.attack();
	}

	private void nightfallBarrage() {
		npc.animate(info.attack_animation);
		faceTarget();
		npc.forceText("Nightfall!");
		npc.graphics(GFX_SMOKE, 96, 0);
		wardMelee = !wardMelee;
		showWard();
		npc.addEvent(e -> {
			for (int arrow = 0; arrow < BARRAGE_ARROWS; arrow++) {
				if (arrow > 0)
					e.delay(1);
				if (isDead())
					return;
				for (Player p : players())
					shoot(p, arrow == BARRAGE_ARROWS - 1);
			}
		});
	}

	private void shoot(Player victim, boolean piercing) {
		Projectile arrow = new Projectile(GFX_ARROW, 55, 31, 18, 34, 8, 12, 64);
		arrow.tileOffset(npc.getSize() / 2);
		int arrives = arrow.send(npc, (Entity) victim);
		Hit hit = new Hit(npc, AttackStyle.RANGED, null).randDamage(info.max_damage * 3 / 4).delay(Math.max(1, (arrives + 15) / 30));
		if (piercing)
			hit.ignorePrayer();
		hit.afterPostDamage(h -> {
			victim.graphics(GFX_SMOKE, 96, 0);
			if (hit.damage > 0)
				victim.getStats().get(StatType.Prayer).drain(PRAYER_SAP);
		});
		victim.hit(hit);
	}
}

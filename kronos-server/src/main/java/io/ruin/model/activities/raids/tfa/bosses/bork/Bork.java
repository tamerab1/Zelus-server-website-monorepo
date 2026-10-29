package io.ruin.model.activities.raids.tfa.bosses.bork;

import io.ruin.model.activities.raids.tfa.bosses.Raid4Puppet;
import io.ruin.model.combat.Hit;
import lombok.Getter;
import lombok.Setter;

/**
 * Bork (16640, 4x4). Every move is scripted by {@link io.ruin.model.activities.raids.tfa.rooms.BorkRoom} from the
 * RSProx captures (bork_plan.md). His body: flat armour 10 off every melee and ranged hit, none while dazed, never
 * below 0 (§10, identified 98:1 on the wire), and while dazed every melee attack on him lands (1 zero in 84 swings).
 */
public class Bork extends Raid4Puppet {

	static final int FLAT_ARMOUR = 10;

	@Getter
	@Setter
	private boolean dazed;

	@Override
	public void init() {
		super.init();
		setPreDefend(hit -> {
			if (dazed && hit.attackStyle != null && hit.attackStyle.isMelee())
				hit.ignoreDefence();
		});
		setPreDamage(this::armour);
	}

	private void armour(Hit hit) {
		if (dazed || hit.attacker == null || hit.attacker.player == null || hit.attackStyle == null)
			return;
		if (hit.attackStyle.isMelee() || hit.attackStyle.isRanged())
			hit.damage = Math.max(0, hit.damage - FLAT_ARMOUR);
	}
}

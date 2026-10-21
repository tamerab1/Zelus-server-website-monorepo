package io.ruin.model.combat.special.melee;

import io.ruin.cache.ObjType;
import io.ruin.model.activities.perktree.Perks;
import io.ruin.model.activities.perktree.perks.AccurateBlows;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.special.Special;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;
import io.ruin.model.stat.StatType;

//Pulverize: A crushing blow with 25% bonus accuracy that will reduce your target's defence by 35%. (50%)
// The cache already had the spec bar and this description for 21003 / 27100 (enums 906 / 1739), but no server-side
// Special existed, so the bar showed and nothing happened (2026-10-21 bug report). The defence drain lands on a hit.
public class ElderMaul implements Special {

	@Override
	public boolean accept(ObjType def, String name) {
		return name.equals("elder maul") || name.equals("elder maul (or)");
	}

	@Override
	public boolean handle(Player player, Entity target, AttackStyle attackStyle, AttackType attackType, int maxDamage) {
		player.animate(7516); // the maul's own crush swing
		player.publicSound(2541);
		target.graphics(1292, 0, 0);
		double boostAttack = 0.25;
		if (target.isNpc() && player.getPlayerPerkHandler().getActivePerks(player).contains(Perks.ACCURATE_BLOWS)) {
			int perkIndex = player.getPlayerPerkHandler().getActivePerkIndex(player, Perks.ACCURATE_BLOWS);
			AccurateBlows c = (AccurateBlows) player.getPlayerPerkHandler().getActivePerks(player).get(perkIndex).getPerk(player);
			boostAttack += c.getAccuracyBoost();
		}
		Hit hit = new Hit(player, AttackStyle.CRUSH, attackType).randDamage(maxDamage).boostAttack(boostAttack);
		int damage = target.hit(hit);
		if (damage > 0) {
			if (target.player != null)
				target.player.getStats().get(StatType.Defence).drain(0.35);
			else
				target.npc.getCombat().getStat(StatType.Defence).drain(0.35);
		}
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 50;
	}
}

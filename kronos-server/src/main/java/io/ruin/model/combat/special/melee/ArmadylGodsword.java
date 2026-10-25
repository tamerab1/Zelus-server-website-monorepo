package io.ruin.model.combat.special.melee;

import io.ruin.model.combat.special.ForgeSpecGfx;
import io.ruin.cache.ObjType;
import io.ruin.model.activities.perktree.Perks;
import io.ruin.model.activities.perktree.perks.AccurateBlows;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.special.Special;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.containers.Equipment;

//The Judgement: Deal an attack that inflicts
//37.5% more damage with double accuracy. (50%)
public class ArmadylGodsword implements Special {

	// Wilderness Forge upgrade. Its spec uses seq 14457: a copy of 7644 with the forced
	// right-hand item (the regular AGS) stripped, so the wielded model shows.
	private static final int TEMPEST_GODSWORD = 42324;
	private static final int TEMPEST_SPEC_ANIM = 20890;

	@Override
	public boolean accept(ObjType def, String name) {
		return name.equalsIgnoreCase("armadyl godsword") || def.id == TEMPEST_GODSWORD || def.id == 42584; // + AGS (celestial)
	}

	@Override
	public boolean handle(Player player, Entity target, AttackStyle attackStyle, AttackType attackType, int maxDamage) {
		int weaponId = player.getEquipment().getId(Equipment.SLOT_WEAPON);
		player.animate(weaponId == 11802 ? 7644 : weaponId == TEMPEST_GODSWORD ? TEMPEST_SPEC_ANIM : 7645);
		if (player.goldenArmadylGodsword) {
			player.graphics(1747);
		} else {
			player.graphics(ForgeSpecGfx.of(player, 1211));
		}
		player.publicSound(3869);
		double boostAttack = 1.25;
		if (player.getPlayerPerkHandler().getActivePerks(player).contains(Perks.ACCURATE_BLOWS)) {
			if (target.isNpc()) {
				int perkIndex = player.getPlayerPerkHandler().getActivePerkIndex(player, Perks.ACCURATE_BLOWS);
				AccurateBlows c = (AccurateBlows) player.getPlayerPerkHandler().
					getActivePerks(player).get(perkIndex).getPerk(player);
				boostAttack += c.getAccuracyBoost();
			}
		}
		target.hit(new Hit(player, attackStyle, attackType)
			.randDamage(maxDamage)
			.boostDamage(0.375)
			.boostAttack(boostAttack) //100% aka double
			.capDamagePvP(87, target)
		);
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 50;
	}

}
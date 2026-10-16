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
import io.ruin.model.entity.npc.NPC;
import io.ruin.model.entity.player.Player;
import io.ruin.model.item.containers.Equipment;
import io.ruin.model.stat.Stat;
import io.ruin.model.stat.StatType;

//Warstrike: Deal an attack that inflicts 21% more damage and
//drains one of your target's Combat stats by the damage dealt. (65%)
public class BandosGodsword implements Special {

	// Wilderness Forge upgrade. Its spec uses seq 14458: a copy of 7642 with the forced
	// right-hand item (the regular BGS) stripped, so the wielded model shows.
	private static final int WARBRINGER_GODSWORD = 31576;
	private static final int WARBRINGER_SPEC_ANIM = 14458;

	private static StatType[] DRAIN_ORDER = {StatType.Defence, StatType.Strength, StatType.Attack, StatType.Prayer, StatType.Magic, StatType.Ranged};

	@Override
	public boolean accept(ObjType def, String name) {
		return name.equalsIgnoreCase("bandos godsword") || def.id == WARBRINGER_GODSWORD;
	}

	@Override
	public boolean handle(Player player, Entity target, AttackStyle attackStyle, AttackType attackType, int maxDamage) {
		int weaponId = player.getEquipment().getId(Equipment.SLOT_WEAPON);
		player.animate(weaponId == 11804 ? 7642 : weaponId == WARBRINGER_GODSWORD ? WARBRINGER_SPEC_ANIM : 7643);
		if (player.goldenBandosGodsword == true) {
			player.graphics(1748);
		} else {
			player.graphics(ForgeSpecGfx.of(player, 1212));
		}
		player.publicSound(3869);
		double boostAttack = 0.5;
		if (player.getPlayerPerkHandler().getActivePerks(player).contains(Perks.ACCURATE_BLOWS)) {
			if (target.isNpc()) {
				int perkIndex = player.getPlayerPerkHandler().getActivePerkIndex(player, Perks.ACCURATE_BLOWS);
				AccurateBlows c = (AccurateBlows) player.getPlayerPerkHandler().
					getActivePerks(player).get(perkIndex).getPerk(player);
				boostAttack += c.getAccuracyBoost();
			}
		}
		int damage = target.hit(new Hit(player, attackStyle, attackType)
			.randDamage(maxDamage)
			.boostDamage(0.21)
			.boostAttack(boostAttack)
		);
		if (damage > 0) {
			if (target instanceof NPC) {
				target.npc.addWeakenedDamage(player, damage);
			}
			if (target.player != null) {
				for (StatType t : DRAIN_ORDER) {
					if (t == StatType.Prayer)
						damage -= target.player.getPrayer().drain(damage);
					else
						damage -= target.player.getStats().get(t).drain(damage);
					if (damage <= 0)
						break;
				}
				target.player.sendMessage("You feel drained!");
			} else {
				for (StatType t : DRAIN_ORDER) {
					Stat stat = target.npc.getCombat().getStat(t);
					if (stat == null)
						continue;
					damage -= stat.drain(damage);
					if (damage <= 0)
						break;
				}
			}
		}
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 50;
	}

}
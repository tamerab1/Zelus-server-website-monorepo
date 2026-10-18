package io.ruin.model.combat.special.melee;

import io.ruin.cache.ObjType;
import io.ruin.model.combat.AttackStyle;
import io.ruin.model.combat.AttackType;
import io.ruin.model.combat.Hit;
import io.ruin.model.combat.special.Special;
import io.ruin.model.entity.Entity;
import io.ruin.model.entity.player.Player;

//Soul Rend: a glacial cleave with 50% more accuracy and 35% more damage that heals you for a quarter of the damage dealt
//(50%) -- the Ascended soulbound blade 31828 (the Azure Fang mesh, SoulWeapons). A physical sword strike, not a spell:
//the dragon longsword's spinning cleave (1058) trailing a cyan copy of its slash arc (4424), cyan phantom slash marks on
//the target (4425, a recoloured 1171; .dev/cache-restore-tool azure_slash_gfx.txt), the cleave's swoosh (2529) and the
//claws' slash impact (2537). Two ticks later a small cold-burn hit (15% of the damage, the Fang's frost sparkle 4423).
//Melee range like every melee special (the combat path brings the player in first); damage is the normal melee max hit.
//PvM only, like every Soulbound power; spec bar: soulbound_spec_enums.txt.
public class SoulRend implements Special {

	private static final int ANIM_CLEAVE = 1058, GFX_AZURE_ARC = 4424, GFX_PHANTOM_SLASH = 4425, GFX_FROST = 4423,
			SOUND_SWOOSH = 2529, SOUND_IMPACT = 2537, COLD_BURN_DELAY = 2;
	private static final double COLD_BURN = 0.15;

	@Override
	public boolean accept(ObjType def, String name) {
		return def.id == 31828;
	}

	@Override
	public boolean handle(Player player, Entity target, AttackStyle attackStyle, AttackType attackType, int maxDamage) {
		if (target.player != null) {
			player.sendMessage("Soul Rend can only be used against monsters.");
			return false; // no energy spent
		}
		player.animate(ANIM_CLEAVE);
		player.graphics(GFX_AZURE_ARC, 96, 0);
		player.publicSound(SOUND_SWOOSH);
		Hit hit = new Hit(player, attackStyle, attackType).randDamage(maxDamage).boostAttack(0.50).boostDamage(0.35);
		hit.postDamage(t -> {
			t.graphics(GFX_PHANTOM_SLASH, 80, 0);
			t.publicSound(SOUND_IMPACT);
			if (hit.damage > 0) {
				player.incrementHp(Math.max(1, hit.damage / 4));
				Hit burn = new Hit(player, attackStyle, attackType).fixedDamage(Math.max(1, (int) (hit.damage * COLD_BURN)))
						.ignoreDefence().delay(COLD_BURN_DELAY);
				burn.soulEcho = true; // an echo of the cleave: it never triggers talents itself
				burn.postDamage(b -> b.graphics(GFX_FROST, 60, 0));
				t.hit(burn);
			}
		});
		target.hit(hit);
		return true;
	}

	@Override
	public int getDrainAmount() {
		return 50;
	}
}
